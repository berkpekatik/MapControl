package com.mapcontrol.launcher

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Outline
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mapcontrol.R

/**
 * Tam ekran arama sayfası (karar §15): ana ekranın üstüne eklenen bir örtü (yeni Activity değil), Sahne zemini ve ortam
 * ışığıyla. Ortada 168dp yuvarlak fotoğraf (yoksa cam disk üstünde baş harfler), büyük isim, isim varsa küçük numara ve
 * durum satırı ("Gelen arama" / "Aranıyor…" / süre). Altta denetimler ([PhoneUiLogic.callControls]): Sessiz, Telefona aktar,
 * Tuş takımı (yolu yoksa gizli), Küçült ve Kapat; gelen aramada büyük Reddet + Cevapla.
 *
 * Seçili hâl halkasız: ikon ve etiket AMBER yanar ("parça yanar"). Tuş takımı fotoğrafın yerine 3×4 tuş açar; her tuş
 * [PhoneSource.sendDtmf] çağırır, girilen tuşlar yalnızca bu ekranda (bellekte) görünür, günlüğe yazılmaz.
 *
 * Çizim: zemin ve sabit parçalar bir kez kurulur; saniyede yalnızca süre yazısı değişir (konuşma sürerken ve sayfa
 * açıkken). Durum NONE olunca sayfa kendiliğinden kapanır. Fotoğraf yalnızca [PhonePhotos]'tan (bellek).
 */
internal class CallScreen(
    private val activity: Activity,
    /** Örtünün ekleneceği kök (etkinliğin içerik FrameLayout'u). */
    private val host: FrameLayout,
    /** Açılış/kapanış (ana iş parçacığı): etkinlik arama şeridini gizler/gösterir (süre iki yerde birden saymasın). */
    private val onOpenChanged: (Boolean) -> Unit = {},
) : PhoneListener {

    private val context: Context = activity
    private val main = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var call: CallState = CallState.IDLE
    private var snap: PhoneSnapshot = PhoneSnapshot.EMPTY
    private var keypadOpen = false
    /** Bu aramada girilen tuşlar (yalnız ekranda; günlüğe/diske yazılmaz). */
    private var digits = ""
    private var builtControls: List<PhoneUiLogic.CallControl>? = null
    private var shownPhotoKey: String? = "\u0000"

    // Görünümler (açılışta kurulur).
    private lateinit var avatar: PhoneAvatarView
    private lateinit var nameText: TextView
    private lateinit var numberText: TextView
    private lateinit var stateText: TextView
    private var oemPill = false
    private lateinit var digitsText: TextView
    private lateinit var keypad: LinearLayout
    private lateinit var controls: LinearLayout
    private val controlViews = HashMap<PhoneUiLogic.CallControl, LinearLayout>()
    private val keyViews = ArrayList<View>()

    val isOpen: Boolean get() = root != null

    /** Sayfayı açar (zaten açıksa bir şey yapmaz). Arama yoksa açılmaz. */
    fun open() {
        if (root != null || activity.isFinishing || activity.isDestroyed) return
        val s = PhoneSource.snapshot()
        if (!s.call.inCall && s.call.kind != CallKind.ENDED) return
        val r = build()
        root = r
        host.addView(r, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        r.alpha = 0f
        r.animate().alpha(1f).setDuration(ANIM_MS).start()
        onOpenChanged(true)
        // Yalnız arama durumu istenir: rehber/geçmiş okunmaz. Eklenince güncel durum hemen gelir.
        PhoneSource.start(activity, this, wantsData = false)
        PhoneSource.addListener(this)
        bind(s)
    }

    /** Sayfayı kapatır (Küçült, arama bitti ya da etkinlik durdu). Şerit yerinde kalır. */
    fun close() = detach(notify = true)

    /** Örtüyü kaldırır; [notify] false ise (tema yenilemesi) etkinliğe kapanış bildirilmez (küçültme sayılmasın). */
    private fun detach(notify: Boolean) {
        val r = root ?: return
        root = null
        main.removeCallbacks(timer)
        PhoneSource.removeListener(this)
        PhoneSource.stop(this)
        r.animate().cancel()
        host.removeView(r)
        keypadOpen = false
        digits = ""
        builtControls = null
        shownPhotoKey = "\u0000"
        controlViews.clear()
        keyViews.clear()
        if (notify) onOpenChanged(false)
    }

    /** Tema değişti: açıksa yeni jetonlarla yeniden kurulur. */
    fun applyTheme() {
        if (root == null) return
        val keepKeypad = keypadOpen
        val keepDigits = digits
        detach(notify = false)
        open()
        if (root != null && keepKeypad && PhoneUiLogic.keypadEnabled(call.kind)) {
            digits = keepDigits
            setKeypad(true)
        }
    }

    fun release() = close()

    override fun onPhoneChanged(snapshot: PhoneSnapshot) {
        if (root == null) return
        bind(snapshot)
    }

    override fun onPhoneError(error: PhoneError) {
        if (root == null) return
        PhoneToast.show(context, PhoneUiLogic.errorRes(error))
    }

    // --- Bağlama ---

    private fun bind(s: PhoneSnapshot) {
        val prev = call
        snap = s
        call = s.call
        if (PhoneUiLogic.callScreenShouldClose(call.kind)) {
            close()
            return
        }
        // Yeni arama (ör. bekleyen arama ya da bitip yenisi): tuşlar sıfırlanır.
        if (prev.kind == CallKind.ENDED && call.inCall) {
            digits = ""
            setKeypad(false)
        }
        val label = PhoneUiLogic.callLabel(call)
        setTextIfChanged(nameText, label.title)
        setTextIfChanged(numberText, label.number.orEmpty())
        numberText.visibility = if (label.number == null) View.GONE else View.VISIBLE
        if (call.photoKey != shownPhotoKey) {
            shownPhotoKey = call.photoKey
            avatar.bind(call.photoKey, PhoneText.initials(call.name))
        }
        val wanted = PhoneUiLogic.callControls(call.kind, s.canMute, s.canAudioTransfer, s.canDtmf)
        if (wanted != builtControls) buildControls(wanted)
        bindToggles()
        if (keypadOpen && PhoneUiLogic.CallControl.KEYPAD !in wanted) setKeypad(false)
        val keysOn = PhoneUiLogic.keypadEnabled(call.kind)
        keyViews.forEach { k -> if (k.isEnabled != keysOn) { k.isEnabled = keysOn; k.alpha = if (keysOn) 1f else DISABLED_ALPHA } }
        // Aranıyorken Tuş takımı soluk (yalnız konuşmada çalışır).
        controlViews[PhoneUiLogic.CallControl.KEYPAD]?.alpha = if (keysOn) 1f else DISABLED_ALPHA
        // OEM hapı süreyi/durumu yazıyorsa bizde ikinci kez yazılmaz (CallBanner ile aynı kural); sayaç da durur.
        oemPill = PhoneUiLogic.oemPillShowsDuration(call.kind, s.backend)
        stateText.visibility = if (oemPill) View.GONE else View.VISIBLE
        when (call.kind) {
            CallKind.INCOMING -> setTextIfChanged(stateText, context.getString(R.string.launcher_call_incoming))
            CallKind.DIALING -> if (!oemPill) setTextIfChanged(stateText, context.getString(R.string.launcher_call_dialing))
            CallKind.ACTIVE -> if (!oemPill) tickTimer()
            CallKind.ENDED -> setTextIfChanged(stateText, context.getString(R.string.launcher_call_ended))
            CallKind.NONE -> Unit
        }
        if (call.kind != CallKind.ACTIVE || oemPill) main.removeCallbacks(timer)
    }

    /** Sessiz ve ses yönü: seçiliyken ikon ve etiket AMBER yanar (halka yok); ses telefondayken etiket "Araca aktar". */
    private fun bindToggles() {
        controlViews[PhoneUiLogic.CallControl.MUTE]?.let { setLit(it, call.muted) }
        controlViews[PhoneUiLogic.CallControl.AUDIO]?.let {
            setLit(it, call.audioOnPhone)
            val label = it.getChildAt(1) as TextView
            setTextIfChanged(label, context.getString(if (call.audioOnPhone) R.string.call_audio_car else R.string.call_audio_phone))
            it.contentDescription = label.text
        }
        controlViews[PhoneUiLogic.CallControl.KEYPAD]?.let { setLit(it, keypadOpen) }
    }

    private fun setLit(cell: LinearLayout, on: Boolean) {
        if (cell.isSelected == on && cell.tag == LIT_TAG_SET) return
        cell.tag = LIT_TAG_SET
        cell.isSelected = on
        val ink = if (on) Sahne.AMBER else Sahne.INK
        ((cell.getChildAt(0) as FrameLayout).getChildAt(0) as ImageView).imageTintList = ColorStateList.valueOf(ink)
        (cell.getChildAt(1) as TextView).setTextColor(if (on) Sahne.AMBER else Sahne.INK_2)
    }

    // --- Kurulum ---

    private fun build(): FrameLayout {
        avatar = PhoneAvatarView(context, AVATAR_DP, INITIALS_SP)
        nameText = sahneText(context, NAME_SP, Sahne.INK, 500).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }
        numberText = sahneText(context, NUMBER_SP, Sahne.INK_3, 400).apply {
            maxLines = 1
            gravity = Gravity.CENTER
        }
        stateText = sahneText(context, STATE_SP, Sahne.NUMERAL, 500).apply {
            maxLines = 1
            gravity = Gravity.CENTER
        }
        digitsText = sahneText(context, DIGITS_SP, Sahne.NUMERAL, 400, 0.08f).apply {
            maxLines = 1
            gravity = Gravity.CENTER
            ellipsize = TextUtils.TruncateAt.START
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        keypad = buildKeypad()
        controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        // Fotoğraf alanı: avatar ya da (aynı yerde) tuş takımı.
        val stage = FrameLayout(context).apply {
            // Avatar camının gölgesi kendi sınırından taşar (GlassDrawable): kırpılmasın.
            clipChildren = false
            clipToPadding = false
            addView(avatar, FrameLayout.LayoutParams(context.dp(AVATAR_DP), context.dp(AVATAR_DP), Gravity.CENTER))
            addView(keypad, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        keypad.visibility = View.GONE
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
            addView(stage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(nameText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dp(22)
                marginStart = context.dp(120)
                marginEnd = context.dp(120)
            })
            addView(numberText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dp(2)
            })
            addView(stateText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dp(10)
            })
            addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
            addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return FrameLayout(context).apply {
            background = SahneGround()
            // Dokunuşlar alttaki ana ekrana geçmez.
            isClickable = true
            isFocusable = true
            setPadding(context.dp(24), context.dp(28), context.dp(24), context.dp(32))
            addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun buildControls(list: List<PhoneUiLogic.CallControl>) {
        builtControls = list
        controls.removeAllViews()
        controlViews.clear()
        val incoming = PhoneUiLogic.CallControl.ANSWER in list
        val size = if (incoming) BIG_DP else BUTTON_DP
        list.forEachIndexed { i, c ->
            val cell = when (c) {
                PhoneUiLogic.CallControl.MUTE -> control(R.drawable.ic_sahne_mic_off, R.string.call_screen_mute, size) { PhoneSource.toggleMute() }
                PhoneUiLogic.CallControl.AUDIO -> control(R.drawable.ic_sahne_mobile, R.string.call_audio_phone, size) { PhoneSource.toggleAudioTarget() }
                PhoneUiLogic.CallControl.KEYPAD -> control(R.drawable.ic_sahne_keypad, R.string.call_keypad, size) {
                    if (PhoneUiLogic.keypadEnabled(call.kind)) setKeypad(!keypadOpen)
                }
                PhoneUiLogic.CallControl.MINIMIZE -> control(R.drawable.ic_sahne_minimize, R.string.call_minimize, size) { close() }
                PhoneUiLogic.CallControl.HANGUP -> control(R.drawable.ic_sahne_call_end, R.string.call_hangup, size, Sahne.CALL_REJECT) {
                    PhoneSource.hangUp()
                }
                PhoneUiLogic.CallControl.REJECT -> control(R.drawable.ic_sahne_call_end, R.string.launcher_call_decline, size, Sahne.CALL_REJECT) {
                    PhoneSource.reject()
                }
                PhoneUiLogic.CallControl.ANSWER -> control(R.drawable.ic_sahne_call, R.string.launcher_call_answer, size, Sahne.CALL_ACCEPT) {
                    PhoneSource.answer()
                }
            }
            controlViews[c] = cell
            controls.addView(cell, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (i > 0) marginStart = context.dp(if (incoming) INCOMING_GAP_DP else CONTROL_GAP_DP)
            })
        }
    }

    /**
     * Bir denetim: [sizeDp] yuvarlak düğme (cam ya da [fill] dolgulu) ve altında etiket. Dokunma alanı düğmenin kendisi
     * (≥ 96dp; en az 76dp kuralını aşar).
     */
    private fun control(@DrawableRes icon: Int, @StringRes label: Int, sizeDp: Int, fill: Int = 0, onClick: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val ink = if (fill != 0) Sahne.CALL_ON_INK else Sahne.INK
            addView(FrameLayout(context).apply {
                background = if (fill != 0) oval(fill) else GlassDrawable(context, sizeDp / 2f, shadow = 0.5f, control = true)
                addView(ImageView(context).apply {
                    setImageResource(icon)
                    imageTintList = ColorStateList.valueOf(ink)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, FrameLayout.LayoutParams(context.dp(ICON_DP), context.dp(ICON_DP), Gravity.CENTER))
                isClickable = true
                isFocusable = true
                contentDescription = context.getString(label)
                setOnClickListener { onClick() }
                pressFeedback()
            }, LinearLayout.LayoutParams(context.dp(sizeDp), context.dp(sizeDp)))
            addView(sahneText(context, LABEL_SP, Sahne.INK_2, 500).apply {
                setText(label)
                maxLines = 1
                gravity = Gravity.CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(context.dp(sizeDp + 28), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(10) })
            clipChildren = false
            clipToPadding = false
        }

    // --- Tuş takımı ---

    private fun buildKeypad(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(digitsText, LinearLayout.LayoutParams(context.dp(3 * KEY_W_DP + 2 * KEY_GAP_DP), context.dp(DIGITS_H_DP)))
        listOf("123", "456", "789", "*0#").forEachIndexed { r, row ->
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                row.forEachIndexed { i, c ->
                    addView(key(c), LinearLayout.LayoutParams(context.dp(KEY_W_DP), context.dp(KEY_H_DP)).apply {
                        if (i > 0) marginStart = context.dp(KEY_GAP_DP)
                    })
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dp(if (r == 0) 6 else KEY_GAP_DP)
            })
        }
    }

    private fun key(c: Char): View = TextView(context).apply {
        text = c.toString()
        textSize = KEY_SP
        setTextColor(Sahne.INK)
        SahneFonts.apply(this, 400)
        gravity = Gravity.CENTER
        background = GlassDrawable(context, Sahne.CONTROL_RADIUS_DP, shadow = 0f, control = true)
        isClickable = true
        isFocusable = true
        setOnClickListener {
            // Tuş günlüğe yazılmaz; yalnız kabul edilirse ekrandaki satıra eklenir.
            if (PhoneSource.sendDtmf(c)) {
                digits = PhoneUiLogic.keypadAppend(digits, c)
                setTextIfChanged(digitsText, digits)
            }
        }
        pressFeedback()
        keyViews += this
    }

    private fun setKeypad(on: Boolean) {
        if (root == null) return
        keypadOpen = on
        keypad.visibility = if (on) View.VISIBLE else View.GONE
        avatar.visibility = if (on) View.GONE else View.VISIBLE
        setTextIfChanged(digitsText, digits)
        bindToggles()
    }

    // --- Süre ---

    private val timer = Runnable { tickTimer() }

    /** Süreyi yazar ve bir sonraki saniye sınırına kurar (yalnız konuşma sürerken ve sayfa açıkken). */
    private fun tickTimer() {
        main.removeCallbacks(timer)
        if (root == null || call.kind != CallKind.ACTIVE || oemPill) return
        val started = call.startedAtMs
        val elapsed = if (started > 0L) SystemClock.elapsedRealtime() - started else 0L
        setTextIfChanged(stateText, PhoneUiLogic.formatDuration(elapsed))
        main.postDelayed(timer, 1_000L - (elapsed.coerceAtLeast(0L) % 1_000L))
    }

    private companion object {
        const val AVATAR_DP = 168
        const val INITIALS_SP = 56f
        const val NAME_SP = 40f
        const val NUMBER_SP = 20f
        const val STATE_SP = 24f
        const val DIGITS_SP = 30f
        const val DIGITS_H_DP = 48
        const val LABEL_SP = 16f
        const val ICON_DP = 34
        const val BUTTON_DP = 96
        const val BIG_DP = 120
        const val CONTROL_GAP_DP = 40
        const val INCOMING_GAP_DP = 120
        const val KEY_W_DP = 116
        const val KEY_H_DP = 76
        const val KEY_GAP_DP = 8
        const val KEY_SP = 30f
        const val DISABLED_ALPHA = 0.4f
        const val ANIM_MS = 160L
        const val LIT_TAG_SET = "lit"
    }
}

/**
 * Kişi görseli: [PhonePhotos]'taki fotoğraf yuvarlak kırpılarak, yoksa cam disk üstünde baş harfler (INK_3). Fotoğraf
 * yalnızca bellekten okunur; anahtar önbellekte yoksa (atılmış olabilir) baş harfe düşer.
 */
internal class PhoneAvatarView(context: Context, sizeDp: Int, initialsSp: Float) : FrameLayout(context) {
    private val photo = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = View.GONE
    }
    private val initials = sahneText(context, initialsSp, Sahne.INK_3, 500).apply {
        gravity = Gravity.CENTER
        maxLines = 1
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    init {
        background = GlassDrawable(context, sizeDp / 2f, shadow = 0.4f)
        addView(initials, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(photo, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) = outline.setOval(0, 0, v.width, v.height)
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Fotoğraf ya da baş harf. Yalnız bağlanınca çağrılır (her karede değil). */
    fun bind(photoKey: String?, initialsText: String) {
        val bmp = PhonePhotos.get(photoKey)
        if (bmp != null) {
            photo.setImageBitmap(bmp)
            photo.visibility = View.VISIBLE
            initials.visibility = View.GONE
            clipToOutline = true
        } else {
            photo.setImageDrawable(null)
            photo.visibility = View.GONE
            initials.text = initialsText
            initials.visibility = View.VISIBLE
            // Baş harfte camın gölgesi taşabilsin.
            clipToOutline = false
        }
    }
}

/** "Arama ekranı: Launcher / Fabrika" (profil başına; varsayılan Launcher). Yalnız açık/kapalı yazılır. */
internal object CallScreenPrefs {
    private const val PREFS_NAME = "launcher_settings"
    private const val KEY = "phone_call_screen_launcher"

    fun launcher(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(Profiles.key(context, KEY), true)

    fun setLauncher(context: Context, on: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(Profiles.key(context, KEY), on).apply()
    }
}
