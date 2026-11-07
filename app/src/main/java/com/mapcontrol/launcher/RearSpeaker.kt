package com.mapcontrol.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.desaysv.ivi.vdb.client.VDBus
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.id.dsp.VDEventDsp
import com.mapcontrol.R

/**
 * Ses alanı: sol-sağ dengesi ve ön-arka dengesi (fader); okunamayan alan null. [assumed] true: araçtan okunmadı, bizim son
 * yazdığımız değerden tahmin (kutucuk düşük güvenle gösterir).
 */
internal data class SoundFieldReading(val balance: Int? = null, val fade: Int? = null, val assumed: Boolean = false)

/**
 * Arka hoparlör kapatmanın saf kuralları (JVM'de sınanır). Arka hoparlör doğrudan kapatılamaz: ön-arka dengesi (fader) ön uca
 * çekilir, eski değer saklanır ve ikinci dokunuşta geri verilir.
 *
 * Kanıt: aracın kendi Ayarlar uygulaması (OEM ayarlar): `setSoundFiled(balance, fade)` düz bir Bundle'a
 * `"type"` = balance, `"value"` = fade yazar ve `VDBus.set(VDEvent(VDEventDsp.SOUND_FIELD, bundle))` gönderir; `getBalance()` /
 * `getFade()` düz `getOnce(SOUND_FIELD)` payload'ından `getInt("type")` / `getInt("value")` okur (yedek: sistem özellikleri,
 * varsayılan 10). Orta 10/10, OEM sıfırla (10,10), sürücü ön ayarı (7,7). Her yazma İKİ değeri birlikte taşır: dengeyi korumak için
 * okunan balance aynen geri yazılır. Fader aralığı 0..20, orta 10; ARAÇTA ÖLÇÜLDÜ (2026-11-06): OEM ayarlarda sesi "tamamen ÖNE"
 * almak fader'ı 10 → 20 yaptı, yani ön uç 20 ([FRONT_EXTREME]), 0 arka uçtur.
 * Gelecekte denenecek: `VDEventDsp.REAR_MUTE` (983045) doğrudan arka sessize alma olabilir; anlamı bilinmiyor, kullanılmıyor.
 *
 * TODO (OEM yolu, araştırılacak): OEM `SoundSettingModel` DSP HAL'e `Dsp setSoundField(balance, fader)` çağırıyor (araç günlüğü:
 * `dsp_hal|Dsp setSoundField: balance index B, fader index F`). Normal uygulama (untrusted_app) DSP servisine SELinux yüzünden
 * erişemiyor ([SoundFieldIo]); tam FrostNav derlemesi bu yola başka biçimde ulaşabilir.
 */
internal object SoundFieldMath {

    /** Beyaz liste kimliği: SOUND_FIELD tek olaydır; kimlik yalnızca "fader yazması" kaydı için sabit 0'dır. */
    const val WRITE_ID = 0

    /** Orta konum (HAL günlüğü ve araç: 10) ve üst sınır (araçta 0..20). */
    const val CENTER = 10
    const val MAX = 20

    /** Arka hoparlörleri susturan uç değer: ön uç = 20 (araçta ölçüldü: "tamamen öne" 10 → 20; 0 arka uçtur). */
    const val FRONT_EXTREME = 20

    /** DSP servisine erişim reddedildi mi (SELinux `avc: denied { find }` / SecurityException); zincirdeki nedenlere de bakılır. */
    fun isDenied(e: Throwable?): Boolean {
        var c = e
        var depth = 0
        while (c != null && depth < 5) {
            if (c is SecurityException) return true
            val m = c.message?.lowercase()
            if (m != null && ("denied" in m || "permission" in m)) return true
            c = c.cause
            depth++
        }
        return false
    }

    /** Erişim reddedildiyse yeniden deneme aralığı (her 3 sn'de deneyip uyandırmasın). */
    const val DENIED_RETRY_MS = 5 * 60_000L

    /** Aralık dışı ve geçersiz değerler null. */
    fun valid(v: Int?): Int? = v?.takeIf { it in 0..MAX }

    /** true: arka hoparlör kapalı (fader ön uçta); okunamadıysa null. */
    fun rearMuted(fade: Int?): Boolean? = valid(fade)?.let { it == FRONT_EXTREME }

    /**
     * Dokunuşun yazacağı fader: açıksa ön uç, kapalıysa [remembered] (geçersiz ya da uçla aynıysa [CENTER]). Okunamadıysa null (yazılmaz).
     */
    fun targetFade(currentFade: Int?, remembered: Int?): Int? {
        val muted = rearMuted(currentFade) ?: return null
        if (!muted) return FRONT_EXTREME
        return valid(remembered)?.takeIf { it != FRONT_EXTREME } ?: CENTER
    }

    /**
     * DSP okunamadığında (araçta `getOnce(SOUND_FIELD)` boş geliyor; üçüncü taraf erişimi SELinux ile kapalı olabilir) kutucuğun
     * göstereceği tahmin: bizim son yazdığımız değer ([SoundFieldReading.assumed] = true). Geçerli bir son yazım yoksa null
     * (kutucuk "Kullanılamıyor" der; emülatör / DSP'siz araçta yalan durum göstermez).
     */
    fun assumed(lastBalance: Int?, lastFade: Int?): SoundFieldReading? {
        val fade = valid(lastFade) ?: return null
        return SoundFieldReading(valid(lastBalance) ?: CENTER, fade, assumed = true)
    }
}

/**
 * DSP servisinden ses alanı okuma/yazma (VDBus, düz Bundle: "type" = balance, "value" = fade). Hatalarda null; çökmez. Araçta normal
 * uygulama (untrusted_app) DSP servisini SELinux yüzünden bulamıyor (`avc: denied { find } ... DspService`): ilk hata bir kez
 * günlüğe yazılır ("RearSpeaker"), erişim reddiyse [denied] açılır ve okuma [SoundFieldMath.DENIED_RETRY_MS]'de bir denenir.
 */
internal object SoundFieldIo {

    private var lastLogged: Pair<Int?, Int?>? = null
    private var lastLoggedKeys: String? = null
    private const val NULL_PAYLOAD = "null-payload"
    @Volatile private var loggedError = false
    @Volatile private var retryAt = 0L

    /** DSP servisine erişim reddedildi (sistem izni gerekiyor); kutucuk "Kullanılamıyor · Sistem izni gerekiyor" der. */
    @Volatile
    var denied = false
        private set

    /** Güncel (balance, fade); DSP yoksa (emülatör), erişim reddedildiyse ya da payload boşsa null. */
    fun read(): SoundFieldReading? {
        if (denied && android.os.SystemClock.elapsedRealtime() < retryAt) return null
        return try {
            val payload = VDBus.getDefault().getOnce(VDEventDsp.SOUND_FIELD)?.payload
            denied = false
            val balance = SoundFieldMath.valid(payload?.takeIf { it.containsKey(KEY_BALANCE) }?.getInt(KEY_BALANCE))
            val fade = SoundFieldMath.valid(payload?.takeIf { it.containsKey(KEY_FADE) }?.getInt(KEY_FADE))
            if (lastLogged != balance to fade) {
                lastLogged = balance to fade
                Log.i("RearSpeaker", "SOUND_FIELD read balance=$balance fade=$fade")
            }
            if (balance == null && fade == null && lastLoggedKeys != (payload?.keySet()?.toString() ?: NULL_PAYLOAD)) {
                // Teşhis: boş okumada payload anahtarları (anahtar adı farklıysa araç günlüğünden görülür).
                lastLoggedKeys = payload?.keySet()?.toString() ?: NULL_PAYLOAD
                Log.i("RearSpeaker", "SOUND_FIELD read empty, payload keys=$lastLoggedKeys")
            }
            if (balance == null && fade == null) null else SoundFieldReading(balance, fade)
        } catch (e: RuntimeException) {
            if (!loggedError) {
                loggedError = true
                Log.w("RearSpeaker", "DSP servisi erişilemiyor: $e")
            }
            if (SoundFieldMath.isDenied(e)) {
                denied = true
                retryAt = android.os.SystemClock.elapsedRealtime() + SoundFieldMath.DENIED_RETRY_MS
            }
            null
        }
    }

    fun write(balance: Int, fade: Int) {
        Log.i("RearSpeaker", "SOUND_FIELD write balance=$balance fade=$fade")
        val bundle = android.os.Bundle().apply {
            putInt(KEY_BALANCE, balance)
            putInt(KEY_FADE, fade)
        }
        VDBus.getDefault().set(VDEvent(VDEventDsp.SOUND_FIELD, bundle))
    }

    private const val KEY_BALANCE = "type"
    private const val KEY_FADE = "value"
}

/** Arka hoparlör kutucuğunun saklanan "eski fader" değeri (araç yeniden başlasa da kalır). */
internal class RearSpeakerStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("launcher_rear_speaker", Context.MODE_PRIVATE)

    var restoreFade: Int?
        get() = if (prefs.contains(KEY)) prefs.getInt(KEY, SoundFieldMath.CENTER) else null
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY) else putInt(KEY, value) }.apply()
        }

    /** Bizim son yazdığımız ses alanı (okuma boşken kutucuk bundan durum gösterir). */
    var lastBalance: Int?
        get() = if (prefs.contains(KEY_LAST_BALANCE)) prefs.getInt(KEY_LAST_BALANCE, SoundFieldMath.CENTER) else null
        set(value) { prefs.edit().apply { if (value == null) remove(KEY_LAST_BALANCE) else putInt(KEY_LAST_BALANCE, value) }.apply() }

    var lastFade: Int?
        get() = if (prefs.contains(KEY_LAST_FADE)) prefs.getInt(KEY_LAST_FADE, SoundFieldMath.CENTER) else null
        set(value) { prefs.edit().apply { if (value == null) remove(KEY_LAST_FADE) else putInt(KEY_LAST_FADE, value) }.apply() }

    private companion object {
        const val KEY = "restore_fade"
        const val KEY_LAST_BALANCE = "last_balance"
        const val KEY_LAST_FADE = "last_fade"
    }
}

/** Ses alanını okur (yalnızca okuma; arka plan thread'inde). */
internal fun ComfortReader.readSound(): ComfortReading {
    if (LauncherDemo.enabled) return LauncherDemo.comfort()
    val sound = SoundFieldIo.read()?.takeIf { it.fade != null } ?: return ComfortReading()
    return ComfortReading(connected = true, sound = sound)
}

/**
 * Arka hoparlör: arkada uyuyan çocuk uyanmasın diye sürücü sürerken de dokunur (sürüş kilidi yok, onay penceresi yok). Değer
 * "Açık" (tüm hoparlörler) ya da "Kapalı" (arka sessiz, fader ön uçta; yanında küçük ay). Tek dokunuşla arka hoparlörleri kapatır,
 * tekrar dokununca eski değeri geri verir. Yazma yalnızca [ComfortWriter.rearSpeakerEnabled] ise gider (doğrulanana dek bilgi
 * mesajı). Şema solda, yazılar sağda.
 */
internal class RearSpeakerTile(context: Context) : ComfortTile(context) {

    private val store = RearSpeakerStore(context)

    /** İyimser durum: true arka kapalı. */
    private var pendingMuted: Boolean? = null
    private var compact = false

    private val diagram = SpeakerDiagramView(context)
    private val iconView = icon(context, R.drawable.ic_mdi_volume_high, ICON_DP, LauncherTheme.TEXT_SECONDARY)
    private val title = text(context, TITLE_SP, LauncherTheme.TEXT_SECONDARY).apply {
        setText(R.string.launcher_widget_rear_speaker)
        theme.bind { setTextColor(LauncherTheme.TEXT_SECONDARY) }
    }
    private val titleRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(iconView)
        addView(gap(context, 8))
        addView(title)
    }
    /** Açık/Kapalı: anahtar görünümlü hap; arka sessizken sakin gece vurgusu ve ay, açıkken nötr. */
    private val stateText = text(context, STATE_SP, LauncherTheme.TEXT, medium = true).apply {
        gravity = Gravity.CENTER
        setPadding(context.dp(20), context.dp(4), context.dp(20), context.dp(4))
    }
    private val detail = text(context, DETAIL_SP, LauncherTheme.TEXT_SECONDARY, maxLines = 2).apply {
        theme.bind { setTextColor(LauncherTheme.TEXT_SECONDARY) }
    }

    /** Gece vurgusu (alarm kırmızısı değil): sakin çivit. */
    private fun sleepAccent(): Int = if (LauncherTheme.night) 0xFF7C83F5.toInt() else 0xFF4F46E5.toInt()

    private val root: LinearLayout = card(context, LinearLayout.VERTICAL, PADDING_DP).apply {
        gravity = Gravity.CENTER
        theme.bind { background = cardSurface(context) }
        isClickable = true
        isFocusable = true
        setOnClickListener { onTap() }
        pressFeedback()
    }

    init {
        theme.bind { diagram.invalidate() }
        arrange(false)
    }

    override val view: View get() = root

    /**
     * DSP okunamazsa (araçta fade=null) son yazımızdan tahmin eder ve "tahmin" diye işaretler; hiç yazımız yoksa eski davranış
     * ("Kullanılamıyor").
     */
    override fun readNow(): ComfortReading {
        val read = ComfortReader.readSound()
        if (read.sound != null || LauncherDemo.enabled) return read
        // Erişim reddi: yazma da gidemez, son yazımızdan tahmin gösterilmez.
        if (SoundFieldIo.denied) return read
        val guess = SoundFieldMath.assumed(store.lastBalance, store.lastFade) ?: return read
        return ComfortReading(connected = true, sound = guess)
    }

    /** Erişim reddedildiyse seyrek dene (her 3 sn'de uyanmasın). */
    override fun pollIntervalMs(): Long = if (SoundFieldIo.denied) SoundFieldMath.DENIED_RETRY_MS else super.pollIntervalMs()

    override fun applySpan(span: Span) {
        val c = span.h < 2
        if (c != compact) arrange(c)
    }

    /** Yatay düzen: şema solda; sağda (2x2) başlık, büyük durum hapı ve açıklama, (2x1) yalnızca durum hapı. */
    private fun arrange(c: Boolean) {
        compact = c
        listOf(titleRow, diagram, stateText, detail).forEach { (it.parent as? ViewGroup)?.removeView(it) }
        root.removeAllViews()
        stateText.textSize = STATE_SP
        root.orientation = LinearLayout.HORIZONTAL
        root.addView(diagram, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.7f))
        root.addView(gap(context, 8))
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        if (!c) {
            column.addView(titleRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            column.addView(gap(context, 4, vertical = true))
        }
        column.addView(stateText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        if (!c) {
            column.addView(gap(context, 4, vertical = true))
            column.addView(detail, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        root.addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.3f))
    }

    override fun clearPending() { pendingMuted = null }

    private fun muted(): Boolean? =
        pendingMuted ?: SoundFieldMath.rearMuted(reading.sound?.fade).takeIf { reading.connected }

    override fun render() {
        // Kapalı = arka hoparlör sessiz (fader ön uçta).
        val muted = muted()
        val state = context.getString(
            when (muted) {
                null -> R.string.launcher_unavailable
                true -> R.string.launcher_rear_off
                false -> R.string.launcher_rear_on
            },
        )
        stateText.setTextSmooth(state)
        stateText.setTextColor(if (muted == true) 0xFFFFFFFF.toInt() else LauncherTheme.TEXT_SECONDARY)
        stateText.background = rounded(context, if (muted == true) sleepAccent() else LauncherTheme.CONTROL, 999f)
        stateText.setCompoundDrawablesRelative(if (muted == true) moon() else null, null, null, null)
        stateText.compoundDrawablePadding = context.dp(6)
        val assumed = reading.sound?.assumed == true && pendingMuted == null
        detail.text = when {
            muted == null && SoundFieldIo.denied && !LauncherDemo.enabled -> context.getString(R.string.launcher_rear_denied_detail)
            muted == null -> null
            assumed -> context.getString(R.string.launcher_rear_assumed_detail)
            muted -> context.getString(R.string.launcher_rear_muted_detail)
            else -> context.getString(R.string.launcher_rear_on_detail)
        }
        iconView.setImageResource(if (muted == true) R.drawable.ic_mdi_weather_night else R.drawable.ic_mdi_volume_high)
        iconView.imageTintList = android.content.res.ColorStateList.valueOf(if (muted == true) sleepAccent() else LauncherTheme.TEXT_SECONDARY)
        diagram.setRearOn(muted?.not())
        root.contentDescription = context.getString(R.string.launcher_widget_rear_speaker) + ": " + state
        root.isEnabled = muted != null || LauncherEdition.READ_ONLY
        notifyStrip(StripState(context.getString(R.string.launcher_widget_rear_speaker), state, R.drawable.ic_mdi_volume_high,
            if (muted == null || assumed) StripTone.MUTED else StripTone.NORMAL,
            detail = detail.text?.toString(), active = muted?.not(), badge = if (muted == true) R.drawable.ic_mdi_weather_night else 0))
    }

    /** "Kapalı" hapındaki küçük ay (beyaz, hap rengine uyar). */
    private fun moon(): android.graphics.drawable.Drawable? = context.getDrawable(R.drawable.ic_mdi_weather_night)?.mutate()?.apply {
        val s = context.dp(MOON_DP)
        setBounds(0, 0, s, s)
        setTint(0xFFFFFFFF.toInt())
    }

    override fun stripTap(part: Int) = onTap()

    private fun onTap() {
        val fade = SoundFieldMath.valid(reading.sound?.fade)
        val target = SoundFieldMath.targetFade(fade, store.restoreFade)
        // Okunamıyorsa (test sürümü dışında) tahmin etme. Okuma "tahmin" ise (son yazımız) yine de ondan ilerlenir.
        if (target == null && !LauncherEdition.READ_ONLY) return
        val enabled = ComfortWriter.rearSpeakerEnabled
        if (enabled && target != null) {
            val wasMuted = muted() == true
            val balance = SoundFieldMath.valid(reading.sound?.balance) ?: SoundFieldMath.CENTER
            pendingMuted = !wasMuted
            render()
            io.runSafely {
                // Yazım atarsa (VDBus.set, DSP bağlı değil) runSafely yutar: başarısız sayılır, bekleyen hal temizlenir.
                val sent = try {
                    ComfortWriter.setFader(balance, target)
                } catch (e: RuntimeException) {
                    false
                }
                main.post {
                    if (sent) {
                        // Yalnızca gönderim başarılıysa hatırla (eski fader, son yazım).
                        if (!wasMuted && fade != null) store.restoreFade = fade
                        store.lastBalance = balance
                        store.lastFade = target
                        afterCommit(true)
                    } else {
                        pendingMuted = null
                        afterCommit(false)
                    }
                }
            }
            return
        }
        afterCommit(enabled)
    }

    private companion object {
        const val PADDING_DP = 8
        const val ICON_DP = 24
        const val TITLE_SP = 20f
        /** Bir bakışta okunur: durum en az 32 sp. */
        const val STATE_SP = 32f
        const val DETAIL_SP = 17f
        const val MOON_DP = 22
    }
}

/**
 * Üstten görünüş: gövde, ön ve arka cam, dört hoparlör. Ön iki hoparlör her zaman yanar. Arka iki [rearOn] true iken yanar;
 * false iken sönük ve çizgili (kapalı); null (okunamadı) iken sönük ama çizgisiz.
 */
internal class SpeakerDiagramView(context: Context) : View(context) {
    private var rearOn: Boolean? = null
    private val body = RectF()
    private val cell = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpF(1.6f)
        strokeCap = Paint.Cap.ROUND
    }

    fun setRearOn(v: Boolean?) {
        if (v == rearOn) return
        rearOn = v
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        // Araç gövdesi 1:2 oranında, kutuya sığacak en büyük boyutta ortalanır.
        var bh = h * 0.92f
        var bw = bh / 2f
        if (bw > w * 0.56f) {
            bw = w * 0.56f
            bh = bw * 2f
        }
        val left = (w - bw) / 2f
        val top = (h - bh) / 2f
        body.set(left, top, left + bw, top + bh)
        val radius = bw * 0.30f
        fill.color = LauncherTheme.CONTROL
        canvas.drawRoundRect(body, radius, radius, fill)
        line.color = LauncherTheme.TEXT_SECONDARY
        line.alpha = 150
        canvas.drawRoundRect(body, radius, radius, line)
        // Ön ve arka cam.
        fill.color = LauncherTheme.TRACK
        cell.set(left + bw * 0.20f, top + bh * 0.11f, left + bw * 0.80f, top + bh * 0.22f)
        canvas.drawRoundRect(cell, bw * 0.10f, bw * 0.10f, fill)
        cell.set(left + bw * 0.20f, top + bh * 0.84f, left + bw * 0.80f, top + bh * 0.91f)
        canvas.drawRoundRect(cell, bw * 0.10f, bw * 0.10f, fill)

        val rad = bw * 0.12f
        spot(canvas, left + bw * 0.24f, top + bh * 0.36f, rad, lit = true, slashed = false)
        spot(canvas, left + bw * 0.76f, top + bh * 0.36f, rad, lit = true, slashed = false)
        val rear = rearOn
        spot(canvas, left + bw * 0.24f, top + bh * 0.68f, rad, lit = rear == true, slashed = rear == false)
        spot(canvas, left + bw * 0.76f, top + bh * 0.68f, rad, lit = rear == true, slashed = rear == false)
    }

    private fun spot(canvas: Canvas, cx: Float, cy: Float, r: Float, lit: Boolean, slashed: Boolean) {
        if (lit) {
            fill.color = LauncherTheme.ACCENT
            fill.alpha = 70
            canvas.drawCircle(cx, cy, r * 1.9f, fill)
            fill.alpha = 255
            canvas.drawCircle(cx, cy, r, fill)
            return
        }
        line.color = LauncherTheme.TEXT_SECONDARY
        line.alpha = 120
        canvas.drawCircle(cx, cy, r, line)
        if (slashed) {
            line.color = LauncherTheme.TEXT_SECONDARY
            line.alpha = 220
            val d = r * 0.7f
            canvas.drawLine(cx - d, cy - d, cx + d, cy + d, line)
        }
    }
}
