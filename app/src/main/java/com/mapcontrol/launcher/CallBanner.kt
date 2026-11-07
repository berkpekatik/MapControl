package com.mapcontrol.launcher

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.drawable.InsetDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mapcontrol.R

/**
 * Arama şeridi (karar §15). Yalnızca bir arama varken görünür ([PhoneUiLogic.bannerMode]).
 *
 *  - Aranıyor / konuşma / bitti: başlık bandı yüksekliğinde (76dp) kompakt hap, başlıktaki saatin SAĞINDA (saatin sağ
 *    kenarı + 16dp'den sağ grubun önüne dek, en çok [COMPACT_MAX_W_DP]; [PhoneUiLogic.compactPillSlot]); saati örtmez.
 *    Soldan sağa: 44dp yuvarlak fotoğraf (yoksa baş harf), ad (yoksa numara, tek satır), yalnızca sessizdeyken küçük
 *    mikrofon-kapalı işareti (parça AMBER yanar, rozet yok) ve sağda 76×76 **Kapat**. Süre / "Aranıyor…" yalnız OEM
 *    küçük hapı yokken ad yanında yazılır ([PhoneUiLogic.oemPillShowsDuration]); "Arama bitti" hep yazılır.
 *  - Gelen arama: büyük düzen, üst ortada; arayanın adı, "Gelen arama"; solda kırmızı **Reddet**, sağda yeşil
 *    **Cevapla** (96dp).
 *
 * Yerleşim OEM arama pencerelerine göre (TYPE_PHONE, bizim ekranın
 * üstüne çizilir; 160 dpi'de dp = px): gelen aramada OEM şeridi 1200×234 üst ortada (y 0-234), aranıyor/konuşmada küçük
 * yeşil hap (mShowType 2) x 421-661, y 16-70 ve süreyi kendisi sayar. Bu yüzden gelen arama kartı OEM şeridinin altından
 * başlar ([INCOMING_TOP_DP]; üst omuzdaki "Kilitli" hapını da örtmez); DesaySV yığınında kompakt hap süre yazmaz
 * (araçta süre iki kez görünüyordu) ve saatin sağında durduğu için OEM hapına (saatin solu) değmez.
 *
 * Şeridin gövdesine (düğmeler dışı) dokunmak tam ekran arama sayfasını açar ([onOpen]). Tek zamanlayıcı konuşma
 * süresidir: yalnızca konuşma sürerken, süre bizde yazılıyorken, şerit görünürken ve arama sayfası onu örtmüyorken
 * saniyede bir, yazı değişince.
 *
 * Yalnızca ana ekran görünürken dinler ([onVisible]/[onHidden]). İsim/numara günlüğe yazılmaz.
 */
internal class CallBanner(
    private val activity: Activity,
    /** Gelen arama şeridi göründüğünde (ana iş parçacığı): etkinlik üstteki sayfaları kapatır ki düğmeler örtülmesin. */
    private val onIncoming: (() -> Unit)? = null,
    /** Şerit gövdesine dokunuş: tam ekran arama sayfası. */
    private val onOpen: (() -> Unit)? = null,
    /** Kompakt hap saatin sağına yerleşir; konumlar başlıktan okunur (null: üst ortada). */
    private val header: HeaderBar? = null,
) : LauncherWidget, PhoneListener {

    private val context: Context = activity
    private val main = Handler(Looper.getMainLooper())
    private var visible = false
    /** Tam ekran arama sayfası açık: şerit gizli, süre burada sayılmaz. */
    private var covered = false
    private var call: CallState = CallState.IDLE
    /** Aramayı bildiren arka uç ([PhoneBackendName]); OEM hapı yalnız DesaySV yığınında. */
    private var backend: String = PhoneBackendName.NONE
    /** OEM küçük hapı süreyi/durumu gösteriyor: bizim hap yalnız ad + sessiz + Kapat ([PhoneUiLogic.oemPillShowsDuration]). */
    private var oemPill = false
    private var mode = PhoneUiLogic.BannerMode.HIDDEN
    /** Kurulu düğme dizisinin kipi (kip değişince yeniden kurulur). */
    private var builtFor: PhoneUiLogic.BannerMode? = null
    private var compact = false
    /** Kip düzeni (pay, yazı boyu, avatar) en az bir kez uygulandı. */
    private var layoutApplied = false
    private var shownPhotoKey: String? = "\u0000"
    private val actionButtons = ArrayList<View>()

    private val avatar = PhoneAvatarView(context, AVATAR_DP, AVATAR_SP)
    private val title = sahneText(context, TITLE_SP, Sahne.INK, 600).apply {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    /** Kompakt hapta başlığın yanında " · süre" (ya da durum); büyük düzende başlığın altında durum. */
    private val status = sahneText(context, COMPACT_STATUS_SP, Sahne.INK_2, 500).apply {
        maxLines = 1
    }
    private val sub = sahneText(context, SUB_SP, Sahne.INK_3, 400).apply {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val mutedMark = ImageView(context).apply {
        setImageResource(R.drawable.ic_sahne_mic_off)
        imageTintList = ColorStateList.valueOf(Sahne.AMBER)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = View.GONE
    }
    private val buttons = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    /** Ad + (kompaktta) durum tek satırda; büyük düzende altta [sub]. */
    private val titleRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        // Satır kendi içeriği kadar geniş: ad sığmazsa yalnız ad kısalır (ağırlık), süre hep adın hemen yanında görünür.
        addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(mutedMark, LinearLayout.LayoutParams(context.dp(MUTED_DP), context.dp(MUTED_DP)).apply { marginStart = context.dp(12) })
    }
    private val texts = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        addView(titleRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(sub, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(2)
        })
    }
    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(avatar, LinearLayout.LayoutParams(context.dp(AVATAR_DP), context.dp(AVATAR_DP)))
        addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(14) })
        addView(buttons, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = context.dp(12)
        })
        // Gövdeye dokunuş arama sayfasını açar; alttaki ekrana geçmez.
        isClickable = true
        setOnClickListener { if (mode != PhoneUiLogic.BannerMode.HIDDEN) onOpen?.invoke() }
    }

    /**
     * Çerçeve ekran genişliğinde (dokunulmaz; boş yeri alttakine geçer), kart içinde konumlanır: kompakt hap saatin
     * sağında ([compactSlot]); gelen arama kartı (ya da saat henüz ölçülmediyse hap) üst ortada, en çok [MAX_W_DP]
     * (kompaktta [COMPACT_MAX_W_DP]), her yandan 24dp pay.
     */
    private val frame = object : FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val parentW = MeasureSpec.getSize(widthMeasureSpec)
            val lp = card.layoutParams as FrameLayout.LayoutParams
            val slot = if (compact) compactSlot(this, parentW) else null
            if (slot != null) {
                lp.gravity = Gravity.TOP or Gravity.START
                lp.leftMargin = slot.leftPx
                lp.width = slot.widthPx
            } else {
                lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                lp.leftMargin = 0
                lp.width = minOf(context.dp(if (compact) COMPACT_MAX_W_DP else MAX_W_DP), parentW - context.dp(2 * SIDE_DP)).coerceAtLeast(0)
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(parentW, MeasureSpec.EXACTLY), heightMeasureSpec)
        }
    }.apply {
        visibility = View.GONE
        clipChildren = false
        clipToPadding = false
        addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
    }

    override val view: View get() = frame

    init {
        applyTheme()
        // Saat yazısı / spor modu / tarih değişince hap yeniden yerleşir (düzen geçişinin dışında, bir sonraki karede).
        header?.setOnClockSlotChanged { if (compact && frame.visibility == View.VISIBLE) frame.post { frame.requestLayout() } }
    }

    /** Kompakt hapın yeri: saatin sağ kenarı + [CLOCK_GAP_DP] ile sağ grup arası ([PhoneUiLogic.compactPillSlot]). */
    private fun compactSlot(frame: View, parentW: Int): PhoneUiLogic.PillSlot? {
        val h = header ?: return null
        return PhoneUiLogic.compactPillSlot(
            clockRightPx = h.clockRightPx(frame),
            rightGroupLeftPx = h.rightGroupLeftPx(frame),
            rightLimitPx = parentW - context.dp(SIDE_DP),
            gapPx = context.dp(CLOCK_GAP_DP),
            maxWidthPx = context.dp(COMPACT_MAX_W_DP),
            minWidthPx = context.dp(COMPACT_MIN_W_DP),
        )
    }

    /** Tema değişti: cam ve düğmeler yeni jetonlarla yeniden kurulur. */
    fun applyTheme() {
        applyGlass()
        title.setTextColor(Sahne.INK)
        status.setTextColor(Sahne.INK_2)
        sub.setTextColor(Sahne.INK_3)
        mutedMark.imageTintList = ColorStateList.valueOf(Sahne.AMBER)
        avatar.background = GlassDrawable(context, AVATAR_DP / 2f, shadow = 0f)
        shownPhotoKey = "\u0000"
        builtFor = null
        if (mode != PhoneUiLogic.BannerMode.HIDDEN) bindCall()
    }

    /** Hap (PILL yarıçap) ya da gelen arama kartı (panel yarıçapı); cam, ince kenar. */
    private fun applyGlass() {
        val radius = if (compact) Sahne.PILL_RADIUS_DP else Sahne.PANEL_RADIUS_DP
        card.background = GlassDrawable(context, radius, control = true)
        card.elevation = context.dpF(ELEVATION_DP)
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) =
                outline.setRoundRect(0, 0, v.width, v.height, minOf(context.dpF(radius), v.height / 2f))
        }
    }

    /** Tam ekran arama sayfası açılınca şerit gizlenir ve süresi durur; kapanınca geri gelir. */
    fun setCovered(on: Boolean) {
        if (covered == on) return
        covered = on
        if (on) {
            main.removeCallbacks(timer)
            frame.animate().cancel()
            frame.visibility = View.GONE
        } else if (visible && mode != PhoneUiLogic.BannerMode.HIDDEN) {
            frame.visibility = View.VISIBLE
            frame.alpha = 1f
            frame.translationY = 0f
            if (mode == PhoneUiLogic.BannerMode.ACTIVE) tickTimer()
        }
    }

    // --- Yaşam döngüsü ---

    override fun onVisible() {
        if (visible) return
        visible = true
        // Şerit yalnızca arama durumunu ister: rehber/geçmiş okunmaz.
        PhoneSource.start(activity, this, wantsData = false)
        PhoneSource.addListener(this)
    }

    override fun onHidden() {
        if (!visible) return
        visible = false
        PhoneSource.removeListener(this)
        PhoneSource.stop(this)
        main.removeCallbacks(timer)
        main.removeCallbacks(unlock)
        // Kilit kalkar ve düğmeler yeniden kurulur: dönüşte aynı kipteki Cevapla/Reddet/Kapat ölü kalmasın.
        setLocked(false)
        builtFor = null
        frame.animate().cancel()
        frame.visibility = View.GONE
        mode = PhoneUiLogic.BannerMode.HIDDEN
        call = CallState.IDLE
        backend = PhoneBackendName.NONE
        oemPill = false
    }

    override fun release() = onHidden()

    override fun onPhoneChanged(snapshot: PhoneSnapshot) {
        if (!visible) return
        val next = snapshot.call
        if (next == call && snapshot.backend == backend) return
        call = next
        backend = snapshot.backend
        bindCall()
    }

    override fun onPhoneError(error: PhoneError) {
        if (!visible || covered || mode == PhoneUiLogic.BannerMode.HIDDEN || !frame.isAttachedToWindow) return
        PhoneToast.show(context, PhoneUiLogic.errorRes(error))
    }

    // --- Bağlama ---

    private fun bindCall() {
        val next = PhoneUiLogic.bannerMode(call)
        val was = mode
        mode = next
        oemPill = PhoneUiLogic.oemPillShowsDuration(call.kind, backend)
        if (next == PhoneUiLogic.BannerMode.HIDDEN) {
            main.removeCallbacks(timer)
            if (was != PhoneUiLogic.BannerMode.HIDDEN) hide()
            return
        }
        val label = PhoneUiLogic.callLabel(call)
        setTextIfChanged(title, label.title)
        if (call.photoKey != shownPhotoKey) {
            shownPhotoKey = call.photoKey
            avatar.bind(call.photoKey, PhoneText.initials(call.name))
        }
        if (next != builtFor) {
            builtFor = next
            applyCompact(next != PhoneUiLogic.BannerMode.INCOMING)
            buildButtons(next)
        }
        // OEM hapı süreyi/durumu yazıyorsa bizde ikinci kez yazılmaz (yalnız ad + sessiz + Kapat).
        status.visibility = if (compact && !oemPill) View.VISIBLE else View.GONE
        when (next) {
            PhoneUiLogic.BannerMode.INCOMING -> setTextIfChanged(sub,
                listOfNotNull(context.getString(R.string.launcher_call_incoming), label.number).joinToString(" · "))
            PhoneUiLogic.BannerMode.DIALING -> setStatus(context.getString(R.string.launcher_call_dialing))
            PhoneUiLogic.BannerMode.ACTIVE -> tickTimer()
            PhoneUiLogic.BannerMode.ENDED -> setStatus(context.getString(R.string.launcher_call_ended))
            PhoneUiLogic.BannerMode.HIDDEN -> Unit
        }
        if (next != PhoneUiLogic.BannerMode.ACTIVE) main.removeCallbacks(timer)
        // Sessiz: yalnızca işaret yanar (rozet/halka yok); gelen aramada gösterilmez.
        mutedMark.visibility = if (call.muted && compact && call.inCall) View.VISIBLE else View.GONE
        val detail = if (!compact) sub.text else if (status.visibility == View.VISIBLE) status.text else ""
        card.contentDescription = listOf(title.text, detail, context.getString(R.string.call_screen_open_cd))
            .filter { it.isNotEmpty() }.joinToString(", ")
        if (was == PhoneUiLogic.BannerMode.HIDDEN) show()
        if (next == PhoneUiLogic.BannerMode.INCOMING && was != PhoneUiLogic.BannerMode.INCOMING) onIncoming?.invoke()
    }

    /** Kompakt hapta durum başlığın yanında " · " ile. */
    private fun setStatus(text: String) = setTextIfChanged(status, "$SEP$text")

    /** Kompakt hap (aranıyor/konuşma/bitti) ya da büyük gelen arama düzeni. */
    private fun applyCompact(on: Boolean) {
        if (on == compact && layoutApplied) return
        layoutApplied = true
        compact = on
        if (on) {
            // 76dp: avatar 44 + 2×16 pay; Kapat düğmesi 76×76 hapın sağ ucunu doldurur (dokunma alanı tam 76dp).
            card.setPadding(context.dp(16), 0, 0, 0)
            card.minimumHeight = context.dp(STRIP_DP)
            (card.layoutParams as FrameLayout.LayoutParams).topMargin = 0
            title.textSize = COMPACT_TITLE_SP
            status.visibility = View.VISIBLE
            sub.visibility = View.GONE
        } else {
            card.setPadding(context.dp(24), context.dp(14), context.dp(14), context.dp(14))
            card.minimumHeight = 0
            // OEM gelen arama şeridinin (y 0-234) altında: görünür kalır, üst omuzdaki "Kilitli" hapını örtmez.
            (card.layoutParams as FrameLayout.LayoutParams).topMargin = context.dp(INCOMING_TOP_DP)
            title.textSize = TITLE_SP
            status.visibility = View.GONE
            sub.visibility = View.VISIBLE
        }
        val a = context.dp(if (on) AVATAR_DP else BIG_AVATAR_DP)
        avatar.layoutParams = (avatar.layoutParams as LinearLayout.LayoutParams).apply { width = a; height = a }
        applyGlass()
        frame.requestLayout()
    }

    private fun buildButtons(m: PhoneUiLogic.BannerMode) {
        buttons.removeAllViews()
        actionButtons.clear()
        main.removeCallbacks(unlock)
        when (m) {
            PhoneUiLogic.BannerMode.INCOMING -> {
                // Reddet solda (kırmızı), Cevapla sağda (yeşil): telefonlardaki alışılmış yerleşim.
                val s = context.dp(BIG_DP)
                buttons.addView(bigButton(R.drawable.ic_sahne_call_end, R.string.launcher_call_decline, Sahne.CALL_REJECT) { act { PhoneSource.reject() } },
                    LinearLayout.LayoutParams(s, s))
                buttons.addView(bigButton(R.drawable.ic_sahne_call, R.string.launcher_call_answer, Sahne.CALL_ACCEPT) { act { PhoneSource.answer() } },
                    LinearLayout.LayoutParams(s, s).apply { marginStart = context.dp(14) })
            }
            PhoneUiLogic.BannerMode.DIALING, PhoneUiLogic.BannerMode.ACTIVE -> {
                val s = context.dp(STRIP_DP)
                buttons.addView(endButton { act { PhoneSource.hangUp() } }, LinearLayout.LayoutParams(s, s))
            }
            PhoneUiLogic.BannerMode.ENDED, PhoneUiLogic.BannerMode.HIDDEN -> Unit
        }
        buttons.visibility = if (buttons.childCount == 0) View.GONE else View.VISIBLE
        // Bitti: sağda düğme yok, metin hapın içinde nefes alsın.
        (texts.layoutParams as LinearLayout.LayoutParams).marginEnd = context.dp(if (buttons.childCount == 0 && compact) 24 else 0)
    }

    /** Cevapla/Reddet/Kapat: istek gönderilince düğmeler [LOCK_MS] kilitlenir (çift dokunuş iki istek olmasın). */
    private fun act(action: () -> Boolean) {
        if (actionButtons.any { !it.isEnabled }) return
        setLocked(true)
        main.postDelayed(unlock, LOCK_MS)
        action()
    }

    private val unlock = Runnable { setLocked(false) }

    private fun setLocked(locked: Boolean) = actionButtons.forEach {
        it.isEnabled = !locked
        it.alpha = if (locked) LOCKED_ALPHA else 1f
    }

    /** Gelen arama düğmesi: dolgulu yuvarlak kare, ikon + etiket. */
    private fun bigButton(@DrawableRes icon: Int, @StringRes label: Int, fill: Int, onClick: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(context, fill, BUTTON_RADIUS_DP)
            addView(ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(Sahne.CALL_ON_INK)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(context.dp(ICON_DP), context.dp(ICON_DP)))
            addView(sahneText(context, LABEL_SP, Sahne.CALL_ON_INK, 600).apply {
                setText(label)
                maxLines = 1
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dp(4)
            })
            describe(this, label)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            pressFeedback()
            actionButtons += this
        }

    /** Hapın sağ ucundaki Kapat: 76×76 dokunma alanı, içinde 6dp içeride kırmızı daire ve ikon. */
    private fun endButton(onClick: () -> Unit): FrameLayout = FrameLayout(context).apply {
        val inset = context.dp(END_INSET_DP)
        background = InsetDrawable(oval(Sahne.CALL_REJECT), inset)
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_sahne_call_end)
            imageTintList = ColorStateList.valueOf(Sahne.CALL_ON_INK)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(context.dp(ICON_DP), context.dp(ICON_DP), Gravity.CENTER))
        describe(this, R.string.call_hangup)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        pressFeedback()
        actionButtons += this
    }

    private fun describe(v: View, @StringRes label: Int) {
        val who = title.text?.toString().orEmpty()
        v.contentDescription = if (who.isEmpty()) context.getString(label) else context.getString(label) + ", " + who
    }

    // --- Süre ---

    private val timer = Runnable { tickTimer() }

    /**
     * Süreyi yazar ve bir sonraki saniye sınırına kurar (konuşma sürerken, şerit görünür ve örtülü değilken). OEM hapı
     * süreyi sayıyorsa hiç kurulmaz.
     */
    private fun tickTimer() {
        main.removeCallbacks(timer)
        if (!visible || covered || oemPill || mode != PhoneUiLogic.BannerMode.ACTIVE) return
        val started = call.startedAtMs
        val elapsed = if (started > 0L) SystemClock.elapsedRealtime() - started else 0L
        setStatus(PhoneUiLogic.formatDuration(elapsed))
        main.postDelayed(timer, 1_000L - (elapsed.coerceAtLeast(0L) % 1_000L))
    }

    // --- Gösterme / gizleme (180 ms kaydırma + saydamlık; başka kare işi yok) ---

    private fun show() {
        frame.animate().cancel()
        if (covered) return
        frame.visibility = View.VISIBLE
        frame.alpha = 0f
        frame.translationY = -context.dpF(SLIDE_DP)
        frame.animate().alpha(1f).translationY(0f).setDuration(ANIM_MS).start()
    }

    private fun hide() {
        frame.animate().cancel()
        frame.animate().alpha(0f).translationY(-context.dpF(SLIDE_DP)).setDuration(ANIM_MS).withEndAction {
            if (mode == PhoneUiLogic.BannerMode.HIDDEN) {
                frame.visibility = View.GONE
                frame.translationY = 0f
                builtFor = null
            }
        }.start()
    }

    private companion object {
        const val SEP = "  ·  "
        const val MAX_W_DP = 760
        const val SIDE_DP = 24
        const val ELEVATION_DP = 12f
        const val TITLE_SP = 24f
        const val SUB_SP = 18f
        const val LABEL_SP = 15f
        const val ICON_DP = 30
        /** Kompakt hap yüksekliği (başlık bandı) ve Kapat düğmesinin dokunma alanı. */
        const val STRIP_DP = 76
        const val END_INSET_DP = 6
        /** Kompakt hapın en büyük genişliği; asıl sınır saat ile sağ grup arası boşluk ([compactSlot]). */
        const val COMPACT_MAX_W_DP = 580
        /**
         * Saat ile sağ grup arası en az bu kadarsa hap o aralıkta kalır (ad kısalır, "…"): avatar 76 + ad en az ~68 +
         * Kapat 76. Daha darsa (yalnız çok dar başlıkta) sağ grubun üstüne uzanır ki Kapat'a dokunulabilsin.
         */
        const val COMPACT_MIN_W_DP = 220
        /** Saatin sağ kenarı ile hap arası. */
        const val CLOCK_GAP_DP = 16
        /**
         * Gelen arama kartının şerit çerçevesi içindeki üst payı: etkinlik 12dp aşağıda başlatır, kart 12 + 234 = 246'da
         * başlar (OEM gelen arama şeridi 234 yüksek, y=0).
         */
        const val INCOMING_TOP_DP = 234
        const val COMPACT_TITLE_SP = 20f
        const val COMPACT_STATUS_SP = 20f
        const val AVATAR_DP = 44
        const val BIG_AVATAR_DP = 64
        const val AVATAR_SP = 17f
        const val MUTED_DP = 22
        /** Gelen aramada Reddet/Cevapla kare düğmeleri (96dp). */
        const val BIG_DP = 96
        const val BUTTON_RADIUS_DP = 28f
        const val LOCK_MS = 1_500L
        const val LOCKED_ALPHA = 0.6f
        const val ANIM_MS = 180L
        const val SLIDE_DP = 24f
    }
}
