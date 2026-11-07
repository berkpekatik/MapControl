package com.mapcontrol.launcher

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.Display
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import com.mapcontrol.R
import com.mapcontrol.util.AppLaunchHelper
import java.text.DateFormatSymbols

/**
 * Sahne Telefon paneli (4×4, 378dp; satır ölçüleri [PhoneUiLogic.panelContentDp] ile sınanır):
 *  1. Başlık: "TELEFON" + bağlı telefonun adı (28dp).
 *  2. "Son arama" kartı (96dp): ismi olan en yeni kayıt (isimsizler atlanır), 56dp fotoğraf ya da baş harf, göreli zaman
 *     ve tür işareti (cevapsızda işaret ALERT yanar), sağda 76dp **Geri ara**.
 *  3. Favoriler (112dp): yalnız isimli 3 kişi (76dp fotoğraf ya da baş harf + ad; dokun: ara, basılı tut: sabitle) ve
 *     dördüncü hücrede **Tümü** (Son aramalar / Kişiler sayfası, [PhoneAllSheet]).
 *  4. "Sesli asistan" şeridi (76dp, tam genişlik; yalnız "Sesli asistan düğmesi: Açık" iken).
 * "Sesli asistan düğmesi: Kapalı" (varsayılan; [PhonePrefs.showAssistant]; adb: `--ez launcher_phone_assistant false`): şerit kalkar,
 * "Son arama" kartı büyür (büyük avatar/ad, geniş **Geri ara**) ve favoriler 3 sütunlu ızgara olur (en çok 5 kişi + Tümü,
 * [PhoneUiLogic.compactGrid]); yükseklik [PhoneUiLogic.compactSizes] ile boşluksuz paylaşılır. Açıkken düzen değişmez.
 * Son aramalar listesi panelde yok; "Tümü"de. Veri yalnızca [PhoneSource]'tan, olay güdümlü; görünmezken dinleme yok.
 * Bölümler yalnız verisi değişince yeniden kurulur; tek zamanlayıcı "Son arama" zamanının dakikalık tazelemesidir.
 *
 * Medya kartının arka yüzü olarak da kullanılır ([MediaPhoneFlip]): aynı görünüm, yalnız başlık sağında çevirme simgesine pay.
 *
 * [sheet]: alt çubuktaki telefon düğmesinin açtığı sayfadaki aynı görünüm (sayfanın kendi başlığı olduğu için "TELEFON"
 * etiketi gizli); [onDialed] arama başlayınca sayfayı kapatır. [onOpenAll]: "Tümü" (null ise etkinlik açar).
 *
 * GİZLİLİK: isim ve numaralar yalnızca ekrana yazılır; günlüğe hiçbir şey yazılmaz. Fotoğraflar yalnız [PhonePhotos]'tan.
 */
internal class PhonePanel(
    private val activity: Activity,
    private val sheet: Boolean = false,
    private val onDialed: (() -> Unit)? = null,
    private val onOpenAll: (() -> Unit)? = null,
    /** Başlık sağında boş bırakılan pay (dp): medya kartının arka yüzünde köşedeki çevirme simgesine yer açar. */
    private val headerEndInsetDp: Int = 0,
) : SizedWidget, PhoneListener {

    private val context: Context = activity
    private val main = Handler(Looper.getMainLooper())
    private var visible = false
    private var snap: PhoneSnapshot? = null
    private var state: PhoneUiLogic.PanelState? = null
    private var permBlocked = false
    private var shownFavorites: List<PhoneContact>? = null
    private var shownLast: PhoneRecent? = null
    private var shownLastPhoto: String? = null
    private var shownDirVersion = -1
    private var lastNumber: String? = null

    /** "Sesli asistan düğmesi" ayarı ([PhonePrefs.showAssistant]); kapalıyken asistansız düzen ([compactCol]). */
    private var showAssistant = PhonePrefs.showAssistant(context)
    /**
     * Asistansız düzende gövdenin yüksekliği ve genişliği (dp). Yalnız ayar kapalıyken ölçülür: açıkken şerit yer
     * tuttuğu için gövde kısadır, o ölçüyle kurulan düzen bir kare ezik görünürdü.
     */
    private var compactBodyDp = 0
    private var compactBodyWDp = 0
    /** Asistansız düzenin son kurulduğu veri + ölçü (değişmedikçe yeniden kurulmaz). */
    private var compactKey: Any? = null
    private var cLast: CompactLast? = null
    private val showAssistantListener: () -> Unit = { applyMode() }

    // --- Başlık ---
    private val headIcon = ImageView(context).apply {
        setImageResource(R.drawable.ic_sahne_phone)
        imageTintList = ColorStateList.valueOf(Sahne.INK_3)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val headLabel = sahneText(context, LABEL_SP, Sahne.INK_3, 600, 0.12f).apply { setText(R.string.launcher_phone_label) }
    private val deviceText = sahneText(context, DEVICE_SP, Sahne.INK_2, 500).apply {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = if (sheet) Gravity.START else Gravity.END
    }
    private val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        if (!sheet) {
            addView(headIcon, LinearLayout.LayoutParams(context.dp(20), context.dp(20)))
            addView(headLabel, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(10) })
        }
        addView(deviceText, LinearLayout.LayoutParams(0, WRAP, 1f).apply {
            if (!sheet) marginStart = context.dp(16)
            marginEnd = context.dp(headerEndInsetDp)
        })
    }

    // --- Son arama kartı ---
    private val lastAvatar = PhoneAvatarView(context, LAST_AVATAR_DP, LAST_INITIALS_SP)
    private val lastLabel = sahneText(context, CAPTION_SP, Sahne.INK_3, 500).apply {
        setText(R.string.phone_last_call)
        maxLines = 1
    }
    /** Uzun ad iki satıra kayar, taşarsa ikinci satırın sonunda "…" ([twoLineName]). */
    private val lastName = sahneText(context, LAST_NAME_SP, Sahne.INK, 600).apply { twoLineName(this, lastLabel) }
    private val lastGlyph = ImageView(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    private val lastTime = sahneText(context, CAPTION_SP, Sahne.INK_3, 400).apply { maxLines = 1 }
    private val callBack = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = ctaBackground()
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_sahne_call)
            imageTintList = ColorStateList.valueOf(Sahne.CALL_ACCEPT)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(context.dp(28), context.dp(28)))
        addView(sahneText(context, CALLBACK_SP, Sahne.CTA_INK, 600).apply {
            setText(R.string.phone_call_back)
            maxLines = 1
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = context.dp(3) })
        isClickable = true
        isFocusable = true
        setOnClickListener { lastNumber?.let { dial(it) } }
        pressFeedback()
    }
    private val lastCard = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(context, Sahne.SOFT_FILL, Sahne.SQUARE_RADIUS_DP, Sahne.HAIR_2)
        val pad = context.dp(PhoneUiLogic.FACE_CARD_PAD_DP)
        setPadding(pad, pad, pad, pad)
        // Avatar gölgesi kendi sınırından taşar: kartın iç payına çizilebilsin.
        clipChildren = false
        addView(lastAvatar, LinearLayout.LayoutParams(context.dp(LAST_AVATAR_DP), context.dp(LAST_AVATAR_DP)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(lastLabel)
            addView(lastName, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(PhoneUiLogic.LAST_CAPTION_GAP_DP) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(lastGlyph, LinearLayout.LayoutParams(context.dp(18), context.dp(18)))
                addView(lastTime, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(6) })
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(PhoneUiLogic.LAST_TIME_GAP_DP) })
        }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = context.dp(14); marginEnd = context.dp(10) })
        addView(callBack, LinearLayout.LayoutParams(context.dp(TOUCH_DP), context.dp(TOUCH_DP)))
    }

    // --- Favoriler + Tümü ---
    private val favRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val allCell: View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false
        addView(FrameLayout(context).apply {
            background = GlassDrawable(context, FAV_AVATAR_DP / 2f, shadow = 0.4f, control = true)
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_sahne_grid)
                imageTintList = ColorStateList.valueOf(Sahne.INK_2)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(context.dp(28), context.dp(28), Gravity.CENTER))
        }, LinearLayout.LayoutParams(context.dp(FAV_AVATAR_DP), context.dp(FAV_AVATAR_DP)))
        addView(nameText(context.getString(R.string.phone_all)), LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(4) })
        contentDescription = context.getString(R.string.phone_all)
        isClickable = true
        isFocusable = true
        setOnClickListener { openAll() }
        pressFeedback()
    }

    private val contentCol = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(lastCard, LinearLayout.LayoutParams(MATCH, context.dp(PhoneUiLogic.PANEL_LAST_CALL_DP)))
        addView(favRow, LinearLayout.LayoutParams(MATCH, context.dp(PhoneUiLogic.PANEL_FAVORITES_DP)).apply {
            topMargin = context.dp(PhoneUiLogic.PANEL_GAP_DP)
        })
    }

    // --- Boş / bağlı değil / izin / yükleniyor ---
    private val msgTitle = sahneText(context, MSG_SP, Sahne.INK_2, 500).apply { gravity = Gravity.CENTER; maxLines = 2 }
    private val msgSub = sahneText(context, MSG_SUB_SP, Sahne.INK_3, 400).apply { gravity = Gravity.CENTER; maxLines = 2 }
    private val msgButtonText = sahneText(context, BUTTON_SP, Sahne.CTA_INK, 600)
    private var msgAction: (() -> Unit)? = null
    private val msgButton = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        background = ctaBackground()
        minimumWidth = context.dp(MSG_BUTTON_MIN_W_DP)
        setPadding(context.dp(28), 0, context.dp(28), 0)
        addView(msgButtonText)
        isClickable = true
        isFocusable = true
        setOnClickListener { msgAction?.invoke() }
        pressFeedback()
    }
    private val message = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
        addView(msgTitle, LinearLayout.LayoutParams(MATCH, WRAP))
        addView(msgSub, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(6) })
        addView(msgButton, LinearLayout.LayoutParams(WRAP, context.dp(TOUCH_DP)).apply { topMargin = context.dp(14) })
        addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
    }

    // --- Asistansız düzen: büyük "Son arama" kartı + 2×3 favori ızgarası, gövdeyi boşluksuz doldurur ---
    private val compactCol = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        visibility = View.GONE
    }

    private val body = object : FrameLayout(context) {
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (!showAssistant) measureCompactBody()
        }
    }.apply {
        addView(contentCol, FrameLayout.LayoutParams(MATCH, WRAP))
        addView(compactCol, FrameLayout.LayoutParams(MATCH, MATCH))
        addView(message, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    // --- Sesli asistan şeridi ---
    private val assistant = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = ctaBackground()
        setPadding(context.dp(24), 0, context.dp(20), 0)
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_sahne_assistant)
            imageTintList = ColorStateList.valueOf(Sahne.CTA_INK)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(context.dp(30), context.dp(30)))
        addView(sahneText(context, ASSIST_SP, Sahne.CTA_INK, 600).apply {
            setText(R.string.phone_assistant_strip)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = context.dp(16) })
        contentDescription = context.getString(R.string.phone_assistant_strip)
        isClickable = true
        isFocusable = true
        setOnClickListener { startAssistant() }
        pressFeedback()
    }

    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        if (!sheet) setPadding(context.dp(24), context.dp(PhoneUiLogic.PANEL_PAD_TOP_DP), context.dp(24), context.dp(PhoneUiLogic.PANEL_PAD_BOTTOM_DP))
        addView(header, LinearLayout.LayoutParams(MATCH, context.dp(PhoneUiLogic.PANEL_HEADER_DP)))
        addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = context.dp(PhoneUiLogic.PANEL_GAP_DP) })
        addView(assistant, LinearLayout.LayoutParams(MATCH, context.dp(PhoneUiLogic.PANEL_ASSIST_DP)).apply {
            topMargin = context.dp(PhoneUiLogic.PANEL_GAP_DP)
        })
    }

    /** "Son arama" zamanı dakika sınırında (yalnız görünürken ve kart doluyken); yazı değişmedikçe çizim olmaz. */
    private val minuteTick = Runnable {
        refreshTime()
        scheduleMinuteTick()
    }

    private fun scheduleMinuteTick() {
        main.removeCallbacks(minuteTick)
        val t = shownLast?.timeMs ?: 0L
        if (!visible || t <= 0L || state != PhoneUiLogic.PanelState.CONTENT) return
        main.postDelayed(minuteTick, MINUTE_MS - (System.currentTimeMillis() % MINUTE_MS))
    }

    init {
        bind(PhoneSnapshot.EMPTY)
        applyClip()
    }

    override val view: View get() = root
    override fun applySpan(span: Span) = Unit

    override fun onVisible() {
        if (visible) return
        visible = true
        PhonePrefs.addShowAssistantListener(showAssistantListener)
        applyMode()
        PhoneSource.start(activity, this)
        PhoneSource.addListener(this)
        // Sayfa (alt çubuk telefon düğmesi) kullanıcının açık eylemi; panel ekranda durması değil.
        PhoneSource.refreshData(if (sheet) RefreshReason.USER_OPENED else RefreshReason.PANEL_OPENED)
        refreshTime()
        scheduleMinuteTick()
    }

    override fun onHidden() {
        if (!visible) return
        visible = false
        PhonePrefs.removeShowAssistantListener(showAssistantListener)
        main.removeCallbacks(minuteTick)
        PhoneSource.removeListener(this)
        PhoneSource.stop(this)
    }

    override fun release() = onHidden()

    override fun onPhoneChanged(snapshot: PhoneSnapshot) {
        if (snapshot == snap) return
        bind(snapshot)
    }

    override fun onPhoneError(error: PhoneError) {
        if (!visible || !root.isAttachedToWindow) return
        PhoneToast.show(context, PhoneUiLogic.errorRes(error))
    }

    // --- Bağlama ---

    private fun bind(s: PhoneSnapshot) {
        snap = s
        val device = s.deviceName?.trim()?.takeIf { s.connected && it.isNotEmpty() }
        // Bağlı değilken başlık sağı boş: aynı bilgi gövdede ("Bağlı telefon yok") yazılı, iki kez yazılmaz.
        setTextIfChanged(deviceText, device.orEmpty())
        // Sayfada başlık satırı yalnız telefon adını taşır: ad yoksa satır kalkar (sayfanın kendi "Telefon" başlığı var).
        if (sheet) header.visibility = if (device == null) View.INVISIBLE else View.VISIBLE
        // Bağlı telefon yokken asistan şeridi kalkar: gövdedeki tek Bluetooth düğmesi panelin tek eylemi olur.
        // Bağlı ama asistan yolu yoksa soluk ve dokunulamaz.
        val canAssist = s.connected && s.canAssistant
        assistant.visibility = assistantVisibility(s)
        assistant.alpha = if (canAssist) 1f else DISABLED_ALPHA
        assistant.isEnabled = canAssist
        assistant.contentDescription = listOfNotNull(context.getString(R.string.phone_assistant_strip), device).joinToString(", ")

        val st = PhoneUiLogic.panelState(s)
        if (st != state || (st == PhoneUiLogic.PanelState.PERMISSION && s.permissionBlocked != permBlocked)) {
            state = st
            permBlocked = s.permissionBlocked
            applyState(st)
        }
        if (st == PhoneUiLogic.PanelState.CONTENT) {
            // "Son arama" kartındaki kişi favorilerde tekrar edilmez; yerini sıradaki favori alır.
            val favs = PhoneUiLogic.favoriteCells(s.favorites, exclude = PhoneUiLogic.lastNamedRecent(s.recents))
            if (favs != shownFavorites) bindFavorites(favs)
            bindLast(s)
            rebuildCompact()
        }
    }

    /** Asistansız düzende şerit hiç yer tutmaz; asistanlı düzende bugünkü gibi (bağlı değilken yeri boş kalır). */
    private fun assistantVisibility(s: PhoneSnapshot): Int =
        if (!showAssistant) View.GONE else if (s.connected) View.VISIBLE else View.INVISIBLE

    /** Asistansız düzende avatar gölgeleri gövde ve panel sınırında kesilmesin; asistanlı düzen bugünkü gibi. */
    private fun applyClip() {
        body.clipChildren = showAssistant
        root.clipChildren = showAssistant
        root.clipToPadding = showAssistant
    }

    /** Gövde ölçüsünü alır; değiştiyse asistansız düzen bir sonraki karede kurulur (yerleşim sırasında ağaç değişmez). */
    private fun measureCompactBody() {
        val d = context.resources.displayMetrics.density
        val h = (body.height / d + 0.5f).toInt()
        val w = (body.width / d + 0.5f).toInt()
        if (h <= 0 || (h == compactBodyDp && w == compactBodyWDp)) return
        compactBodyDp = h
        compactBodyWDp = w
        body.post { rebuildCompact() }
    }

    /** Ayar değişti (ya da panel yeniden göründü): düzen seçilir; asistanlı düzen bugünküyle aynıdır. */
    private fun applyMode() {
        val show = PhonePrefs.showAssistant(context)
        if (show == showAssistant) return
        showAssistant = show
        applyClip()
        compactKey = null
        compactCol.removeAllViews()
        cLast = null
        state?.let { applyContentVisibility(it) }
        snap?.let { assistant.visibility = assistantVisibility(it) }
        if (show) return
        // Kapanınca hemen kurulmaz: şerit kalktıktan sonraki yerleşimde ölçülen gövdeyle kurulur (onSizeChanged; boyut
        // değişmezse bu tek seferlik dinleyici).
        compactBodyDp = 0
        compactBodyWDp = 0
        body.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                body.viewTreeObserver.removeOnGlobalLayoutListener(this)
                if (!showAssistant) measureCompactBody()
            }
        })
        body.requestLayout()
    }

    private fun applyContentVisibility(st: PhoneUiLogic.PanelState) {
        val content = st == PhoneUiLogic.PanelState.CONTENT
        contentCol.visibility = if (content && showAssistant) View.VISIBLE else View.GONE
        compactCol.visibility = if (content && !showAssistant) View.VISIBLE else View.GONE
        message.visibility = if (content) View.GONE else View.VISIBLE
    }

    private fun applyState(st: PhoneUiLogic.PanelState) {
        val content = st == PhoneUiLogic.PanelState.CONTENT
        applyContentVisibility(st)
        if (content) return
        // Gövde gizlenince bir sonraki içerik hâlinde baştan kurulur.
        shownFavorites = null
        shownLast = null
        shownLastPhoto = null
        lastCard.tag = null
        compactKey = null
        compactCol.removeAllViews()
        cLast = null
        main.removeCallbacks(minuteTick)
        when (st) {
            PhoneUiLogic.PanelState.NO_PHONE -> showMessage(R.string.launcher_phone_not_connected, R.string.launcher_phone_not_connected_sub,
                R.string.launcher_phone_bluetooth) { openOemBluetooth(context) }
            // İzin reddedildi ve sistem yeniden sormuyor: istek düğmesi boşa döner, uygulama ayarları açılır.
            PhoneUiLogic.PanelState.PERMISSION -> if (permBlocked) {
                showMessage(R.string.launcher_phone_permission, R.string.launcher_phone_permission_settings_sub, R.string.launcher_phone_open_settings) {
                    openAppSettings()
                }
            } else {
                showMessage(R.string.launcher_phone_permission, 0, R.string.launcher_phone_grant) { PhoneSource.requestPermissions(activity) }
            }
            PhoneUiLogic.PanelState.LOADING -> showMessage(R.string.launcher_phone_loading, 0, 0, null)
            // Boşken de "Tümü" açılabilir: açılış bir kez indirme isteyebilir (kullanıcı eylemi).
            PhoneUiLogic.PanelState.EMPTY -> showMessage(R.string.launcher_phone_empty, R.string.launcher_phone_empty_sub, R.string.phone_all) { openAll() }
            PhoneUiLogic.PanelState.CONTENT -> Unit
        }
    }

    private fun showMessage(@StringRes title: Int, @StringRes sub: Int, @StringRes button: Int, action: (() -> Unit)?) {
        msgTitle.setText(title)
        msgSub.visibility = if (sub == 0) View.GONE else View.VISIBLE
        if (sub != 0) msgSub.setText(sub)
        msgButton.visibility = if (button == 0) View.GONE else View.VISIBLE
        if (button != 0) {
            msgButtonText.setText(button)
            msgButton.contentDescription = context.getString(button)
        }
        msgAction = action
        message.contentDescription = listOfNotNull(context.getString(title), if (sub != 0) context.getString(sub) else null).joinToString(", ")
    }

    /** "Son arama": ismi olan en yeni kayıt; yoksa kart kalkar. Fotoğraf kaydın ya da eşleşen kişinin anahtarından. */
    private fun bindLast(s: PhoneSnapshot) {
        val r = PhoneUiLogic.lastNamedRecent(s.recents)
        // Yalnız kayıt ya da rehber sürümü değişince aranır (her arama durumu değişiminde tam rehber taranmasın).
        if (r == shownLast && s.directoryVersion == shownDirVersion && lastCard.tag != null) return
        val photo = r?.let { PhoneUiLogic.recentPhotoKey(it, s.favorites) ?: PhoneUiLogic.recentPhotoKey(it, PhoneSource.directory().contacts) }
        lastCard.tag = true
        shownLast = r
        shownLastPhoto = photo
        shownDirVersion = s.directoryVersion
        if (r == null) {
            lastCard.visibility = View.GONE
            lastNumber = null
            (favRow.layoutParams as LinearLayout.LayoutParams).topMargin = 0
            main.removeCallbacks(minuteTick)
            return
        }
        lastCard.visibility = View.VISIBLE
        (favRow.layoutParams as LinearLayout.LayoutParams).topMargin = context.dp(PhoneUiLogic.PANEL_GAP_DP)
        val name = r.name.orEmpty().trim()
        lastNumber = r.number
        setTextIfChanged(lastName, name)
        lastAvatar.bind(photo, PhoneText.initials(name))
        lastGlyph.setImageResource(recentGlyph(r.type))
        // Cevapsız: yalnız işaret yanar (rozet/halka yok); ad sakin kalır.
        lastGlyph.imageTintList = ColorStateList.valueOf(if (r.type == RecentType.MISSED) Sahne.ALERT else Sahne.INK_3)
        refreshTime()
        callBack.contentDescription = context.getString(R.string.phone_call_back) + ", " + name
        scheduleMinuteTick()
    }

    private fun refreshTime() {
        val t = shownLast?.timeMs ?: 0L
        setTextIfChanged(lastTime, phoneTimeText(context, t, ago = true))
        lastTime.visibility = if (t > 0L) View.VISIBLE else View.GONE
        cLast?.let {
            setTextIfChanged(it.time, lastTime.text)
            it.time.visibility = lastTime.visibility
        }
    }

    // --- Asistansız düzen ---

    /** Büyük "Son arama" kartı ve dakikalık tazelenen zaman yazısı (her yeniden kurulumda yenilenir). */
    private class CompactLast(val card: View, val time: TextView)

    /**
     * Asistansız düzeni kurar: kart ve ızgara gövdenin yüksekliğini [PhoneUiLogic.compactSizes] ile boşluksuz paylaşır,
     * hücreler [PhoneUiLogic.compactGrid]'den. Veri ya da ölçü değişmedikçe hiçbir şey yapmaz.
     */
    private fun rebuildCompact() {
        if (showAssistant || state != PhoneUiLogic.PanelState.CONTENT || compactBodyDp <= 0) return
        val s = snap ?: return
        val last = shownLast
        val grid = PhoneUiLogic.compactGrid(s.favorites, compactBodyDp, last != null, exclude = last)
        val slots = PhoneUiLogic.compactSlots(grid)
        val cellW = if (compactBodyWDp > 0) compactBodyWDp / slots else Int.MAX_VALUE
        val sizes = PhoneUiLogic.compactSizes(compactBodyDp, grid.size, last != null, cellW)
        val key = listOf(grid, sizes, slots, last, shownLastPhoto, shownDirVersion)
        if (key == compactKey) return
        compactKey = key
        compactCol.removeAllViews()
        cLast = null
        if (last != null) {
            val c = compactLastCard(last, sizes)
            cLast = c
            compactCol.addView(c.card, LinearLayout.LayoutParams(MATCH, context.dp(sizes.lastDp)))
            refreshTime()
        }
        grid.forEachIndexed { i, row ->
            val rowView = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                // Hücre genişliği tüm satırlarda aynı; kısa satır (KKK/KT) ortalanır, boş hücre yok.
                weightSum = slots.toFloat()
                gravity = Gravity.CENTER_HORIZONTAL
                row.forEach { cell -> addView(compactCell(cell, sizes.favAvatarDp), LinearLayout.LayoutParams(0, MATCH, 1f)) }
            }
            compactCol.addView(rowView, LinearLayout.LayoutParams(MATCH, context.dp(sizes.rowDp)).apply {
                if (last != null || i > 0) topMargin = context.dp(PhoneUiLogic.PANEL_GAP_DP)
            })
        }
    }

    /** Büyük kart: büyük avatar ve ad, sağda geniş **Geri ara** (tek dokunuşluk ana eylem). */
    private fun compactLastCard(r: PhoneRecent, sizes: PhoneUiLogic.CompactSizes): CompactLast {
        val name = r.name.orEmpty().trim()
        val pad = context.dp(PhoneUiLogic.COMPACT_CARD_PAD_DP)
        val time = sahneText(context, C_CAPTION_SP, Sahne.INK_3, 400).apply { maxLines = 1 }
        val callH = (sizes.lastDp - 2 * PhoneUiLogic.COMPACT_CARD_PAD_DP).coerceAtLeast(PhoneUiLogic.TOUCH_MIN_DP)
        // Sayfada geniş yan yana düğme; kart yüzünde (dar) simge yazının üstünde, ada yer kalsın.
        val call = LinearLayout(context).apply {
            orientation = if (sheet) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = ctaBackground()
            if (sheet) setPadding(context.dp(16), 0, context.dp(20), 0) else setPadding(context.dp(12), 0, context.dp(12), 0)
            val icon = if (sheet) 32 else 28
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_sahne_call)
                imageTintList = ColorStateList.valueOf(Sahne.CALL_ACCEPT)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(context.dp(icon), context.dp(icon)))
            addView(sahneText(context, if (sheet) C_CALLBACK_SP else C_CALLBACK_FACE_SP, Sahne.CTA_INK, 600).apply {
                setText(R.string.phone_call_back)
                maxLines = 1
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { if (sheet) marginStart = context.dp(10) else topMargin = context.dp(2) })
            contentDescription = context.getString(R.string.phone_call_back) + ", " + name
            isClickable = true
            isFocusable = true
            setOnClickListener { lastNumber?.let { dial(it) } }
            pressFeedback()
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(context, Sahne.SOFT_FILL, Sahne.SQUARE_RADIUS_DP, Sahne.HAIR_2)
            setPadding(pad, pad, pad, pad)
            // Avatar gölgesi kendi sınırından taşar: kartın iç payına çizilebilsin (iç pay da kırpmasın).
            clipChildren = false
            clipToPadding = false
            addView(PhoneAvatarView(context, sizes.lastAvatarDp, C_LAST_INITIALS_SP).apply { bind(shownLastPhoto, PhoneText.initials(name)) },
                LinearLayout.LayoutParams(context.dp(sizes.lastAvatarDp), context.dp(sizes.lastAvatarDp)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val caption = sahneText(context, C_CAPTION_SP, Sahne.INK_3, 500).apply {
                    setText(R.string.phone_last_call)
                    maxLines = 1
                }
                addView(caption)
                addView(sahneText(context, C_LAST_NAME_SP, Sahne.INK, 600).apply {
                    text = name
                    twoLineName(this, caption)
                }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(PhoneUiLogic.LAST_CAPTION_GAP_DP) })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(ImageView(context).apply {
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        setImageResource(recentGlyph(r.type))
                        // Cevapsız: yalnız işaret yanar (rozet/halka yok); ad sakin kalır.
                        imageTintList = ColorStateList.valueOf(if (r.type == RecentType.MISSED) Sahne.ALERT else Sahne.INK_3)
                    }, LinearLayout.LayoutParams(context.dp(20), context.dp(20)))
                    addView(time, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(6) })
                }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(PhoneUiLogic.LAST_TIME_GAP_DP) })
            }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = context.dp(16); marginEnd = context.dp(12) })
            addView(call, LinearLayout.LayoutParams(WRAP, context.dp(callH)))
        }
        return CompactLast(card, time)
    }

    /** Izgara hücresi: kişi (dokun: ara, basılı tut: sabitle) · "Tümü". İçerik hücrede ortalı. */
    private fun compactCell(cell: PhoneUiLogic.FavCell, avatarDp: Int): View = when (cell) {
        PhoneUiLogic.FavCell.All -> compactCellFrame(context.getString(R.string.phone_all), FrameLayout(context).apply {
            background = GlassDrawable(context, avatarDp / 2f, shadow = 0.4f, control = true)
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_sahne_grid)
                imageTintList = ColorStateList.valueOf(Sahne.INK_2)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(context.dp(30), context.dp(30), Gravity.CENTER))
        }, avatarDp).apply {
            contentDescription = context.getString(R.string.phone_all)
            setOnClickListener { openAll() }
        }
        is PhoneUiLogic.FavCell.Contact -> {
            val c = cell.contact
            compactCellFrame(c.name, PhoneAvatarView(context, avatarDp, C_FAV_INITIALS_SP).apply { bind(c.photoKey, c.initials) }, avatarDp).apply {
                contentDescription = context.getString(R.string.launcher_phone_call_cd, c.name)
                setOnClickListener { dial(c.number) }
                if (PhoneUiLogic.canPin(c.id)) setOnLongClickListener { togglePin(c); true }
            }
        }
    }

    private fun compactCellFrame(label: String, avatar: View, avatarDp: Int): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        // Avatar gölgesi kırpılmasın.
        clipChildren = false
        addView(avatar, LinearLayout.LayoutParams(context.dp(avatarDp), context.dp(avatarDp)))
        addView(nameText(label, C_NAME_SP), LinearLayout.LayoutParams(MATCH, WRAP).apply {
            topMargin = context.dp(PhoneUiLogic.COMPACT_NAME_GAP_DP)
        })
        isClickable = true
        isFocusable = true
        pressFeedback()
    }

    /** Favori hücreleri: en çok 3 isimli kişi; eksik hücreler boş kalır, dördüncü hücre hep "Tümü". */
    private fun bindFavorites(list: List<PhoneContact>) {
        shownFavorites = list
        favRow.removeAllViews()
        list.forEach { c -> favRow.addView(favoriteCell(c), LinearLayout.LayoutParams(0, MATCH, 1f)) }
        repeat(PhoneUiLogic.PANEL_FAVORITES - list.size) { favRow.addView(View(context), LinearLayout.LayoutParams(0, MATCH, 1f)) }
        (allCell.parent as? LinearLayout)?.removeView(allCell)
        favRow.addView(allCell, LinearLayout.LayoutParams(0, MATCH, 1f))
    }

    private fun favoriteCell(c: PhoneContact): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        // Avatar gölgesi kırpılmasın.
        clipChildren = false
        addView(PhoneAvatarView(context, FAV_AVATAR_DP, FAV_INITIALS_SP).apply { bind(c.photoKey, c.initials) },
            LinearLayout.LayoutParams(context.dp(FAV_AVATAR_DP), context.dp(FAV_AVATAR_DP)))
        addView(nameText(c.name), LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(4) })
        contentDescription = context.getString(R.string.launcher_phone_call_cd, c.name)
        isClickable = true
        isFocusable = true
        setOnClickListener { dial(c.number) }
        if (PhoneUiLogic.canPin(c.id)) setOnLongClickListener { togglePin(c); true }
        pressFeedback()
    }

    /**
     * "Son arama" adı: en çok [PhoneUiLogic.LAST_NAME_MAX_LINES] satır, taşarsa ikinci satırın sonunda "…" (yazı payı
     * kapalı). İki satıra kaydığında küçük "Son arama" başlığı ([caption]) kalkar ki ad + zaman kart yüksekliğine sığsın
     * ([PhoneUiLogic.lastCardCaptionVisible]); tek satıra dönünce geri gelir.
     */
    private fun twoLineName(name: TextView, caption: View) {
        name.maxLines = PhoneUiLogic.LAST_NAME_MAX_LINES
        name.ellipsize = TextUtils.TruncateAt.END
        name.includeFontPadding = false
        name.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val lines = (v as TextView).layout?.lineCount ?: return@addOnLayoutChangeListener
            val want = if (PhoneUiLogic.lastCardCaptionVisible(lines)) View.VISIBLE else View.GONE
            // Başlığın görünürlüğü adın genişliğini değiştirmez: döngü olmaz; düzen geçişinin dışında uygulanır.
            if (caption.visibility != want) v.post { caption.visibility = want }
        }
    }

    private fun nameText(value: String, sizeSp: Float = NAME_SP): TextView = sahneText(context, sizeSp, Sahne.INK_2, 500).apply {
        text = value
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_HORIZONTAL
        // Favori satırı sabit yükseklikte: yazının üst/alt yazı tipi payı ş/ç çengelini satır sınırına itiyordu
        // ("Ayşe" "Avse" görünüyordu). Pay kaldırılır, çengel için altta 3 dp bırakılır.
        includeFontPadding = false
        setPadding(context.dp(4), 0, context.dp(4), context.dp(3))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    // --- Eylemler (yalnız kullanıcı dokunuşuyla) ---

    /** Başarısızlık nedeni [onPhoneError] ile tek uyarı olarak gelir; doğrulanamayan asistan uyarı vermez (arka uç). */
    private fun startAssistant() {
        PhoneSource.startVoiceAssistant()
    }

    private fun openAll() {
        onOpenAll?.let { it(); return }
        (activity as? HomeLauncherActivity)?.openPhoneAll() ?: PhoneAllSheet.show(activity)
    }

    /**
     * Önce karar verilir: bağlı telefon var ama arama yolu yoksa doğrudan OEM telefon uygulaması (hata uyarısı yok).
     * Aksi halde arama denenir; başarısızlık nedeni [onPhoneError] ile tek uyarı olarak gelir.
     */
    private fun dial(number: String) {
        if (phoneDial(context, number)) onDialed?.invoke()
    }

    /** Uygulama ayrıntıları (izinler); ayar ekranı yoksa kısa uyarı. */
    private fun openAppSettings() {
        try {
            activity.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (t: Throwable) {
            PhoneToast.show(context, R.string.launcher_call_not_supported)
        }
    }

    /** Uzun basış: sabitle / sabitlemeyi kaldır (yalnız kimlik yazılır); bellekteki veri yeniden sıralanır. */
    private fun togglePin(c: PhoneContact) {
        val pinned = PhonePrefs.pinnedIds(context)
        val wasPinned = c.id in pinned
        PhonePrefs.setPinnedIds(context, PhoneUiLogic.togglePin(pinned, c.id))
        root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        PhoneToast.show(context, if (wasPinned) R.string.launcher_phone_unpinned else R.string.launcher_phone_pinned)
        PhoneSource.reloadFavorites()
    }

    // --- Parçalar ---

    private fun ctaBackground(): GradientDrawable = phoneCtaBackground(context)

    private companion object {
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val TOUCH_DP = 76
        const val LAST_AVATAR_DP = 56
        const val LAST_INITIALS_SP = 22f
        const val FAV_AVATAR_DP = 76
        const val FAV_INITIALS_SP = 26f
        const val MSG_BUTTON_MIN_W_DP = 220
        const val LABEL_SP = 16f
        const val DEVICE_SP = 18f
        const val ASSIST_SP = 22f
        const val CAPTION_SP = PhoneUiLogic.LAST_CAPTION_SP
        const val LAST_NAME_SP = PhoneUiLogic.LAST_NAME_SP
        const val CALLBACK_SP = 14f
        const val NAME_SP = 16f
        const val MSG_SP = 22f
        const val MSG_SUB_SP = 18f
        const val BUTTON_SP = 20f
        const val DISABLED_ALPHA = 0.4f
        // Asistansız düzen.
        const val C_CAPTION_SP = PhoneUiLogic.C_LAST_CAPTION_SP
        const val C_LAST_NAME_SP = PhoneUiLogic.C_LAST_NAME_SP
        const val C_LAST_INITIALS_SP = 28f
        const val C_CALLBACK_SP = 19f
        const val C_CALLBACK_FACE_SP = 16f
        const val C_FAV_INITIALS_SP = 24f
        const val C_NAME_SP = 17f
        const val MINUTE_MS = 60_000L
    }
}

/** Telefon CTA zemini (koyu dolgu, gece ince halka): asistan şeridi, Geri ara, mesaj düğmesi. */
internal fun phoneCtaBackground(context: Context): GradientDrawable = GradientDrawable(
    GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Sahne.CTA_TOP, Sahne.CTA_BOTTOM),
).apply {
    cornerRadius = context.dpF(Sahne.SQUARE_RADIUS_DP)
    if (Sahne.CTA_RING != 0) setStroke(context.dp(1), Sahne.CTA_RING)
}

/** Son arama türünün işareti. */
internal fun recentGlyph(type: RecentType): Int = when (type) {
    RecentType.MISSED -> R.drawable.ic_sahne_call_missed
    RecentType.INCOMING -> R.drawable.ic_sahne_call_in
    RecentType.OUTGOING -> R.drawable.ic_sahne_call_out
}

/**
 * Bağlı telefon var ama arama yolu yoksa OEM telefon uygulaması (uyarı yok); yoksa arama denenir (başarısızlık
 * dinleyicilere [PhoneListener.onPhoneError] ile gelir). Arama isteği kabul edildiyse true.
 */
internal fun phoneDial(context: Context, number: String): Boolean {
    val s = PhoneSource.snapshot()
    if (s.connected && !s.canDial) {
        openOemPhoneApp(context)
        return false
    }
    return PhoneSource.dial(number)
}

/**
 * Arama zamanının yazısı ([PhoneUiLogic.relativeTime]): şimdi · n dk (önce) · n sa (önce) · dün · gün + kısa ay.
 * Zamanı bilinmeyen kayıt (0) için boş.
 */
internal fun phoneTimeText(context: Context, timeMs: Long, ago: Boolean): String = if (timeMs <= 0L) "" else
    when (val rel = PhoneUiLogic.relativeTime(System.currentTimeMillis(), timeMs)) {
        PhoneUiLogic.RelTime.Now -> context.getString(R.string.launcher_time_now)
        is PhoneUiLogic.RelTime.Minutes -> context.getString(if (ago) R.string.phone_ago_minutes else R.string.launcher_time_minutes, rel.n)
        is PhoneUiLogic.RelTime.Hours -> context.getString(if (ago) R.string.phone_ago_hours else R.string.launcher_time_hours, rel.n)
        PhoneUiLogic.RelTime.Yesterday -> context.getString(R.string.launcher_time_yesterday)
        is PhoneUiLogic.RelTime.Date -> {
            val locale = context.resources.configuration.locales[0]
            val month = DateFormatSymbols.getInstance(locale).shortMonths.getOrNull(rel.monthIndex).orEmpty().trimEnd('.')
            "${rel.day} $month"
        }
    }

/** Yalnızca metin gerçekten değişince yazar (gereksiz yerleşim/çizim olmasın). */
internal fun setTextIfChanged(tv: TextView, value: CharSequence) {
    if (tv.text?.toString() != value.toString()) tv.text = value
}

/** OEM Bluetooth telefon uygulamasını varsayılan ekranda açar (arka planda; kurulu değilse bir şey yapmaz). */
internal fun openOemPhoneApp(context: Context) {
    LauncherIo.serial().runSafely { AppLaunchHelper.launchAppOnDisplay(context, OEM_PHONE_PACKAGE, Display.DEFAULT_DISPLAY, 0) }
}

/**
 * Telefon uyarıları tek bir Toast'tan: panel, sayfa ve arama şeridi aynı hatayı aynı anda dinlediği için aynı metin 1,5 sn
 * içinde bir kez gösterilir; yenisi eskisinin yerini alır.
 */
internal object PhoneToast {
    private var toast: Toast? = null
    private var lastRes = 0
    private var lastAt = 0L

    fun show(context: Context, @StringRes res: Int) {
        val now = SystemClock.uptimeMillis()
        if (res == lastRes && now - lastAt < DEDUP_MS) return
        lastRes = res
        lastAt = now
        toast?.cancel()
        toast = Toast.makeText(context.applicationContext, res, Toast.LENGTH_SHORT).also { it.show() }
    }

    /** Son [ms] içinde (herhangi bir) telefon uyarısı gösterildi mi. */
    fun shownWithin(ms: Long): Boolean = lastAt != 0L && SystemClock.uptimeMillis() - lastAt < ms

    private const val DEDUP_MS = 1_500L
}
