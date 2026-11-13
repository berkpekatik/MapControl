package com.mapcontrol.launcher

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.mapcontrol.R
import com.mapcontrol.media.LauncherMediaController
import com.mapcontrol.media.LauncherMediaController.MediaState

/**
 * Medya kartı: solda kare kapak, sağda kaynak, başlık, sanatçı, ilerleme ve kontroller.
 * Denetleyici çok sık haber verir; dinleyici yalnızca değişeni bağlar, ilerleme kendi 1 Hz sayacıyla akar.
 *
 * İki yerleşimi vardır ve ikisi de bir kez kurulur: yükseklik 2 satır ve üstüyse tam yerleşim,
 * 1 satırsa (2×1) kapaksız, ilerlemesiz kompakt yatay yerleşim. [applySpan] ikisi arasında
 * kartı yeniden yaratmadan görünürlük değiştirir; kontrol satırı tek örnektir ve taşınır.
 */
class MediaCard(
    private val context: Context,
    /** Sahne sol paneli: kapak + bilgi üstte, ilerleme ve ortalı kontroller altta (taslak düzeni); veri mantığı aynı. */
    private val sahne: Boolean = false,
) : SizedWidget, Themeable, StripSource {

    // --- Şerit (Sahne): başlık, sanatçı; dokunuş oynat/duraklat (boştayken son kaynağı açar) ---
    override var onStripChanged: (() -> Unit)? = null

    /** Çalma durumu değişince (yalnız değişimde; [MediaPhoneFlip] çevirme simgesinin ışıması için). */
    var onPlayingChanged: ((Boolean) -> Unit)? = null

    /** Şu an çalıyor mu (ekranda gösterilen durum). */
    val isPlaying: Boolean get() = shownPlaying == true

    /** Yatay sürüklemenin kartı çevirmemesi gereken parçalar (ilerleme çubuğu ve süreleri). */
    fun horizontalDragViews(): List<View> = listOf(progressBox)
    private var stripNow: StripState? = null
    override fun stripState(): StripState? = stripNow
    override fun stripTap(part: Int) {
        when (mode) {
            Mode.IDLE -> playFromIdle()
            Mode.SESSION -> controller.playPause()
            else -> controller.openNotificationAccessSettings()
        }
    }

    private enum class Mode { NO_ACCESS, IDLE, SESSION }

    /** Tema bağlı atamalar (kurucuda bir kez, [applyTheme]'de yeniden); bkz. [ThemeBindings]. */
    private val theme = ThemeBindings()

    private val controller = LauncherMediaController(context)
    private val handler = Handler(Looper.getMainLooper())

    // --- Kapak ---
    private val artworkImage = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        visibility = View.GONE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val placeholderIcon = icon(context, R.drawable.ic_mdi_music_note, 40).also { ic -> theme.bind { ic.setTint(LauncherTheme.TEXT_SECONDARY) } }
    private val artworkBox = FrameLayout(context).apply {
        theme.bind { background = rounded(context, LauncherTheme.CONTROL) }
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, context.dpF(LauncherTheme.CARD_RADIUS_DP))
            }
        }
        clipToOutline = true
        addView(artworkImage, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(placeholderIcon, FrameLayout.LayoutParams(context.dp(40), context.dp(40), Gravity.CENTER))
        setOnClickListener { openCardApp() }
        isClickable = false
    }
    private val artworkGap = gap(context, 32)

    // --- Metinler ---
    /** Kaynak adı; dokununca kaynak seçici ([openSourcePicker]). Dokunma alanı kart üzerinden büyütülür ([updateSourceTouch]). */
    private val sourceLabel = text(context, LauncherTheme.LABEL_SP).also { v ->
        theme.bind { v.setTextColor(LauncherTheme.TEXT_SECONDARY) }
        v.isClickable = true
        v.contentDescription = context.getString(R.string.launcher_media_source_change)
        v.setOnClickListener { if (mode != Mode.NO_ACCESS) openSourcePicker() }
    }
    private val titleView = text(context, LauncherTheme.TITLE_SP, bold = true).also { v ->
        theme.bind { v.setTextColor(LauncherTheme.TEXT) }
        v.setOnClickListener { openCardApp() }
        v.isClickable = false
    }
    private val artistView = text(context, 28f).also { v -> theme.bind { v.setTextColor(LauncherTheme.TEXT_SECONDARY) } }

    // --- İlerleme ---
    private val progressFill = View(context).apply {
        theme.bind { background = rounded(context, LauncherTheme.ACCENT, 4f) }
        // Genişlik sabit, ilerleme scaleX ile çizilir: yerleşim turu gerektirmez.
        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        pivotX = 0f
        scaleX = 0f
    }
    private val progressTrack = FrameLayout(context).apply {
        theme.bind { background = rounded(context, LauncherTheme.TRACK, 4f) }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(8))
        addView(progressFill)
    }
    private val elapsedView = text(context, LauncherTheme.LABEL_SP).also { v -> theme.bind { v.setTextColor(LauncherTheme.TEXT_SECONDARY) } }
    private val totalView = text(context, LauncherTheme.LABEL_SP).also { v -> theme.bind { v.setTextColor(LauncherTheme.TEXT_SECONDARY) } }
    private val timesGap = gap(context, 8, vertical = true)
    private val timesRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(elapsedView)
        addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        addView(totalView)
    }
    private val progressBox = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(progressTrack)
        addView(timesGap)
        addView(timesRow)
    }

    // --- Kontroller ---
    private val prevButton = iconButton(
        context, R.drawable.ic_mdi_skip_previous, context.getString(R.string.launcher_media_prev),
        sizeDp = LauncherTheme.MEDIA_BUTTON_DP, circle = true,
    ) { controller.skipToPrevious() }
    private val playButton = iconButton(
        context, R.drawable.ic_mdi_play, context.getString(R.string.launcher_media_play),
        sizeDp = LauncherTheme.PLAY_BUTTON_DP, background = LauncherTheme.ACCENT, tint = LauncherTheme.ON_ACCENT, circle = true,
    ) { if (mode == Mode.IDLE) playFromIdle() else controller.playPause() }
    private val nextButton = iconButton(
        context, R.drawable.ic_mdi_skip_next, context.getString(R.string.launcher_media_next),
        sizeDp = LauncherTheme.MEDIA_BUTTON_DP, circle = true,
    ) { controller.skipToNext() }
    private val playIcon = playButton.getChildAt(0) as ImageView

    init {
        // iconButton zemini ve ikon tonunu kurulumda bir kez verir; tema değişince aynı değerler yeniden atanır.
        theme.bind {
            prevButton.background = oval(LauncherTheme.CONTROL)
            nextButton.background = oval(LauncherTheme.CONTROL)
            playButton.background = oval(LauncherTheme.ACCENT)
            (prevButton.getChildAt(0) as ImageView).setTint(LauncherTheme.TEXT)
            (nextButton.getChildAt(0) as ImageView).setTint(LauncherTheme.TEXT)
            playIcon.setTint(LauncherTheme.ON_ACCENT)
        }
    }
    private val controlsGap1 = gap(context, 24)
    private val controlsGap2 = gap(context, 24)
    // --- İzin yok durumu ---
    // Kart iskeleti korunur; kontrol satırının yerine tek, net bir tonal düğme gelir (vurgu rengi değil, sakin).
    private val grantButton = text(context, LauncherTheme.BODY_SP, medium = true).apply {
        setText(R.string.launcher_media_grant_access)
        gravity = Gravity.CENTER
        theme.bind {
            setTextColor(LauncherTheme.TEXT)
            background = rounded(context, LauncherTheme.CONTROL, 20f)
        }
        val h = context.dp(40)
        setPadding(h, 0, h, 0)
        minWidth = context.dp(180)
        isClickable = true
        isFocusable = true
        visibility = View.GONE
        setOnClickListener { controller.openNotificationAccessSettings() }
        pressFeedback()
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, context.dp(GRANT_BUTTON_DP))
    }

    private val controlsRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(prevButton)
        addView(controlsGap1)
        addView(playButton)
        addView(controlsGap2)
        addView(nextButton)
        addView(grantButton)
    }


    // --- Kompakt (2×1) yerleşim: solda başlık + sanatçı, sağda kontroller ---
    private val compactTitle = text(context, COMPACT_TITLE_SP, bold = true).also { v -> theme.bind { v.setTextColor(LauncherTheme.TEXT) } }
    private val compactArtist = text(context, LauncherTheme.BODY_SP).also { v -> theme.bind { v.setTextColor(LauncherTheme.TEXT_SECONDARY) } }
    private val compactBox = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(compactTitle)
            addView(gap(context, 4, vertical = true))
            addView(compactArtist)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(gap(context, 24))
    }

    private val sourceGap = gap(context, 4, vertical = true)
    private val artistGap = gap(context, 4, vertical = true)
    private val mainBox = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(sourceLabel)
        addView(sourceGap)
        addView(titleView)
        addView(artistGap)
        addView(artistView)
        // İlerleme çubuğu sanatçı satırına yapışmasın: alçak kartta (1920x720, 4x2) bile en az bu kadar boşluk.
        addView(gap(context, PROGRESS_GAP_DP, vertical = true))
        addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
        addView(progressBox)
        addView(gap(context, 16, vertical = true))
        addView(controlsRow)
    }

    // Sahne paneli parçaları (görünüm kurulmadan önce hazır olmalı).
    private val sourceIcon = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = View.GONE
    }
    private val knob = View(context)
    private var shownSourceIcon: String? = null

    override val view: View = if (sahne) buildSahneView() else card(context, LinearLayout.HORIZONTAL).apply {
        theme.bind { background = cardSurface(context) }
        addView(artworkBox, LinearLayout.LayoutParams(0, 0))
        addView(artworkGap)
        addView(FrameLayout(context).apply {
            addView(mainBox, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(compactBox, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }, weightLp(1f))
        addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
            applySizes(r - l, b - t - v.paddingTop - v.paddingBottom)
        }
    }

    // --- Son bağlanan durum (gereksiz yeniden bağlamayı önlemek için) ---
    private var listening = false
    private var mode: Mode? = null
    private var lastSource: String? = null
    private var lastTitle: String? = null
    private var lastArtist: String? = null
    private val artworkIo = LauncherIo.serial()
    private var shownArtwork: Bitmap? = null
    private var shownGeneration = -1
    private var shownPlaying: Boolean? = null
    private var lastArtworkSize = -1
    private var lastCompact: Boolean? = null
    private var lastTall: Boolean? = null
    private var lastPlayDp = -1
    private var lastTiny: Boolean? = null
    private var lastShort: Boolean? = null
    private var lastSession: Boolean? = null
    private var lastContentHeight = 0
    private var layoutCompact = false

    // --- Radyo görünümü ve kaynak seçici ---
    private var lastRadio: RadioDisplay.Band? = null
    private var lastShowProgress: Boolean? = null
    private val sourceTouch = Rect()
    private var sourcePicker: Dialog? = null

    init {
        // Kaynak satırı küçük yazı: dokunma alanı kartın kendisinde, yazının çevresinde büyütülür.
        view.viewTreeObserver.addOnGlobalLayoutListener { updateSourceTouch() }
    }

    // --- İlerleme durumu ---
    private var basePositionMs = 0L
    private var baseRealtimeMs = 0L
    private var durationMs = 0L
    private var playing = false
    private var lastElapsedSec = -1L
    private var lastTotalSec = -1L
    private var lastFillPermille = -1

    private val tick = object : Runnable {
        override fun run() {
            updateProgress()
            if (playing && mode == Mode.SESSION && !layoutCompact) handler.postDelayed(this, 1000L)
        }
    }

    private val listener = LauncherMediaController.Listener { state -> if (!LauncherDemo.enabled) bind(state) }

    /** Tema değişti: yüzeyler, metin/ikon tonları ve düğme zeminleri yeniden atanır; kapak, ilerleme ve oturum korunur. */
    override fun applyTheme() {
        theme.reapply()
        if (sahne) styleSahne()
        view.invalidate()
    }

    override fun onVisible() {
        if (!listening) {
            controller.addListener(listener)
            listening = true
        }
        controller.start()
        bind(if (LauncherDemo.enabled) LauncherDemo.media() else controller.currentState())
    }

    override fun onHidden() {
        if (listening) {
            controller.removeListener(listener)
            listening = false
        }
        controller.stop()
        handler.removeCallbacks(tick)
    }

    override fun release() = onHidden()

    /**
     * Izgara boyutu değişince çağrılır (onVisible'dan önce ve sonra tekrar). Yükseklik 1 satırsa kompakt
     * yerleşime, aksi halde tam yerleşime geçer; yalnızca yerleşim türü değişince çalışır.
     */
    override fun applySpan(span: Span) {
        if (sahne) return
        val compact = span.h < 4
        if (compact == layoutCompact) return
        layoutCompact = compact

        // Kontrol satırı tek örnek: bulunduğu kapsayıcıdan alınıp yenisine eklenir.
        (controlsRow.parent as? ViewGroup)?.removeView(controlsRow)
        if (compact) {
            compactBox.addView(controlsRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        } else {
            mainBox.addView(controlsRow)
        }
        setGapWidth(controlsGap1, if (compact) 12 else 24)
        setGapWidth(controlsGap2, if (compact) 12 else 24)
        val pad = context.dp(if (compact) COMPACT_PADDING_DP else LauncherTheme.PADDING_DP)
        view.setPadding(pad, pad, pad, pad)

        updateContainers()

        lastFillPermille = -1
        if (compact) {
            // Kapak, ilerleme ve sayaç yok; düğmeler sabit 72dp.
            handler.removeCallbacks(tick)
            artworkBox.visibility = View.GONE
            artworkGap.visibility = View.GONE
            lastArtworkSize = -1
            lastPlayDp = -1
            for (b in arrayOf(prevButton, playButton, nextButton)) resize(b, LauncherTheme.TOUCH_MIN_DP)
        } else {
            lastArtworkSize = -1
            lastCompact = null
            lastPlayDp = -1
            if (view.width > 0) applySizes(view.width, view.height - view.paddingTop - view.paddingBottom)
            if (listening && mode == Mode.SESSION) {
                updateProgress()
                handler.removeCallbacks(tick)
                if (playing) handler.postDelayed(tick, 1000L)
            }
        }
    }

    private fun setGapWidth(gap: View, dp: Int) {
        val lp = gap.layoutParams
        lp.width = context.dp(dp)
        gap.layoutParams = lp
    }

    /** Hangi kapsayıcının görüneceğini belirler: tam ya da kompakt. */
    private fun updateContainers() {
        mainBox.visibility = if (layoutCompact) View.GONE else View.VISIBLE
        compactBox.visibility = if (layoutCompact) View.VISIBLE else View.GONE
    }

    /** Kart yüksekliğine göre kapak boyutu, genişliğe göre başlık boyutu. Yalnızca değişince dokunur. */
    private fun applySizes(width: Int, contentHeight: Int) {
        lastContentHeight = contentHeight
        if (layoutCompact || sahne) return
        val density = context.resources.displayMetrics.density
        val showArtwork = contentHeight >= context.dp(180)
        // Uzun kartta (ör. 12×8 ızgarada 6×6) kapak ve yazılar büyür; boş orta bant kalmaz.
        val tall = contentHeight >= context.dp(TALL_HEIGHT_DP)
        val size = if (showArtwork) minOf(contentHeight, context.dp(if (tall) 360 else 300), if (tall) (width * 0.4f).toInt() else Int.MAX_VALUE) else 0
        if (size != lastArtworkSize) {
            lastArtworkSize = size
            val lp = artworkBox.layoutParams as LinearLayout.LayoutParams
            lp.width = size
            lp.height = size
            artworkBox.layoutParams = lp
            val vis = if (showArtwork) View.VISIBLE else View.GONE
            artworkBox.visibility = vis
            artworkGap.visibility = vis
            val iconSize = (size * 0.4f).toInt()
            val ilp = placeholderIcon.layoutParams
            ilp.width = iconSize
            ilp.height = iconSize
            placeholderIcon.layoutParams = ilp
        }
        val compact = width / density < 700f
        if (compact != lastCompact || tall != lastTall) {
            lastTall = tall
            lastCompact = compact
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 40f else if (tall) TALL_TITLE_SP else LauncherTheme.TITLE_SP)
            titleView.maxLines = if (tall && !compact) 2 else 1
            artistView.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (tall && !compact) TALL_ARTIST_SP else 28f)
        }
        // Çok kısa kartta (ör. 3×2) kaynak ve sanatçı satırları, daha kısada süre satırı gizlenir.
        // İzin yok / boşta durumunda ikincil satır asıl mesajdır; çok kısa değilse kalır.
        val short = contentHeight < context.dp(SHORT_HEIGHT_DP)
        val tiny = contentHeight < context.dp(TINY_HEIGHT_DP)
        val session = mode == Mode.SESSION
        val showSource = !short && (session || sourceLabel.text.isNotEmpty())
        val showArtist = if (session) !short else !tiny
        if (short != lastShort || tiny != lastTiny || session != lastSession) {
            lastShort = short
            lastTiny = tiny
            lastSession = session
            sourceLabel.visibility = if (showSource) View.VISIBLE else View.GONE
            sourceGap.visibility = if (showSource) View.VISIBLE else View.GONE
            artistView.visibility = if (showArtist) View.VISIBLE else View.GONE
            artistGap.visibility = if (showArtist) View.VISIBLE else View.GONE
            val timesVis = if (tiny) View.GONE else View.VISIBLE
            timesRow.visibility = timesVis
            timesGap.visibility = timesVis
        }
        // Düğmeler kalan yüksekliğe sığacak kadar küçülür (en çok tam boy, en az 56dp); kontroller kesilmez.
        var reserved = titleView.lineHeight * (if (tall) 2 else 1) + context.dp(16) + context.dp(8) + context.dp(PROGRESS_GAP_DP)
        if (!tiny) reserved += context.dp(8) + elapsedView.lineHeight
        if (showSource) reserved += sourceLabel.lineHeight + context.dp(4)
        if (showArtist) reserved += artistView.lineHeight + context.dp(4)
        val playPx = (contentHeight - reserved).coerceIn(context.dp(MIN_PLAY_DP), context.dp(LauncherTheme.PLAY_BUTTON_DP))
        val playDp = (playPx / density).toInt()
        val sideDp = minOf((playDp * 0.85f).toInt(), LauncherTheme.MEDIA_BUTTON_DP)
        if (playDp != lastPlayDp) {
            lastPlayDp = playDp
            resize(playButton, playDp)
            resize(prevButton, sideDp)
            resize(nextButton, sideDp)
        }
    }

    private fun resize(button: View, sizeDp: Int) {
        val lp = button.layoutParams
        lp.width = context.dp(sizeDp)
        lp.height = context.dp(sizeDp)
        button.layoutParams = lp
    }

    /** Denetleyiciden gelen durum: yalnızca değişen kısımlar bağlanır. */
    private fun bind(state: MediaState) {
        val newMode = when {
            !state.notificationAccessGranted -> Mode.NO_ACCESS
            !state.sessionAvailable -> Mode.IDLE
            else -> Mode.SESSION
        }
        val modeChanged = newMode != mode
        // Boştayken son kaynak uygulama (varsa) oynat düğmesiyle açılır.
        val resumePkg = if (newMode == Mode.IDLE) controller.lastSourcePackage() else null
        if (modeChanged) {
            mode = newMode
            updateContainers()
            // Mod değişince ikincil satırların görünürlüğü yeniden hesaplanır.
            lastSession = null
        }
        // Radyo (OEM ses uygulaması FM/AM/DAB oturumu): frekans başlıkta, kaynak "Radyo", ilerleme ve süreler yok.
        val radio = if (newMode == Mode.SESSION) RadioDisplay.of(state.source, state.title, state.artist, oemSource()) else null
        val showProgress = newMode == Mode.SESSION && radio == null
        if (showProgress != lastShowProgress) {
            lastShowProgress = showProgress
            progressBox.visibility = if (showProgress) View.VISIBLE else View.INVISIBLE
            if (sahne) placeKnob()
        }
        val noAccess = newMode == Mode.NO_ACCESS
        val showPlayOnly = newMode == Mode.IDLE && resumePkg != null
        prevButton.visibility = if (newMode == Mode.SESSION) View.VISIBLE else View.GONE
        nextButton.visibility = prevButton.visibility
        controlsGap1.visibility = prevButton.visibility
        controlsGap2.visibility = prevButton.visibility
        playButton.visibility = if (newMode == Mode.SESSION || showPlayOnly) View.VISIBLE else View.GONE
        grantButton.visibility = if (noAccess) View.VISIBLE else View.GONE
        setControlsEnabled(newMode == Mode.SESSION || showPlayOnly)

        val source = when (newMode) {
            Mode.SESSION -> state.source
            Mode.IDLE -> resumePkg
            Mode.NO_ACCESS -> null
        }
        val rawTitle = if (radio != null) radio.title else when (newMode) {
            Mode.SESSION -> state.title
            Mode.IDLE -> context.getString(R.string.launcher_media_idle_title)
            Mode.NO_ACCESS -> context.getString(R.string.launcher_media_title_neutral)
        }
        val rawArtist = if (radio != null) radio.subtitle else when (newMode) {
            Mode.SESSION -> state.artist
            Mode.IDLE -> context.getString(
                if (resumePkg != null) R.string.launcher_media_idle_resume else R.string.launcher_media_idle_hint,
            )
            Mode.NO_ACCESS -> context.getString(R.string.launcher_media_access_hint)
        }
        // Bluetooth üstverisi sonda boşluk / satır sonu taşıyabiliyor: boş ikinci satır sanatçıyı aşağı itmesin.
        val title = oneLine(rawTitle)
        val artist = oneLine(rawArtist)
        val radioBand = radio?.band
        val textChanged = modeChanged || source != lastSource || title != lastTitle || artist != lastArtist || radioBand != lastRadio
        updateOpenTarget(MediaSourcePick.appToOpen(newMode != Mode.NO_ACCESS, source, LauncherDemo.enabled))
        if (textChanged) {
            val radioChanged = radioBand != lastRadio
            lastSource = source
            lastTitle = title
            lastArtist = artist
            lastRadio = radioBand
            val sourceName = when {
                radio != null -> context.getString(R.string.launcher_media_radio)
                source.isNullOrEmpty() -> ""
                else -> appLabel(source)
            }
            // Seçici açılabilir (izin varken): kaynak adının yanında küçük ok.
            sourceLabel.text = when {
                newMode == Mode.NO_ACCESS -> sourceName
                // Oturum ve son kaynak yokken de seçici açılabilsin.
                sourceName.isEmpty() -> context.getString(R.string.launcher_media_source_choose) + "  ▾"
                else -> "$sourceName  ▾"
            }
            if (radioChanged) {
                placeholderIcon.setImageResource(
                    if (radio != null) R.drawable.ic_mdi_radio else if (sahne) R.drawable.ic_sahne_note else R.drawable.ic_mdi_music_note,
                )
            }
            titleView.text = title ?: ""
            artistView.text = artist ?: ""
            compactTitle.text = title ?: ""
            compactArtist.text = artist ?: ""
            if (sahne) bindSourceIcon(source)
            // Sahne: izin açıklaması iki satıra kadar (kesilmesin); şarkı sanatçısı tek satır.
            if (sahne) artistView.maxLines = if (newMode == Mode.NO_ACCESS) 2 else 1
            fitSahneTitle()
            stripNow = StripState(
                if (sourceName.isEmpty()) context.getString(R.string.launcher_widget_media) else sourceName,
                title ?: "", R.drawable.ic_mdi_music_note,
                if (newMode == Mode.SESSION) StripTone.NORMAL else StripTone.MUTED, detail = artist,
            )
            onStripChanged?.invoke()
        }
        if (modeChanged) {
            // Görünürlük ve düğme boyutu yeni moda göre yeniden hesaplanır.
            lastPlayDp = -1
            if (!layoutCompact && lastContentHeight > 0) applySizes(view.width, lastContentHeight)
        }
        bindArtwork(if (newMode == Mode.SESSION) state.artwork else null)

        if (newMode != Mode.SESSION) {
            playing = false
            setPlaying(false)
            handler.removeCallbacks(tick)
            return
        }

        // İlerleme: yalnızca konumu sakla; çizimi sayaç yapar.
        val playingChanged = state.playing != playing
        playing = state.playing
        basePositionMs = state.positionMs
        baseRealtimeMs = SystemClock.elapsedRealtime()
        if (state.durationMs != durationMs) {
            durationMs = state.durationMs
            lastTotalSec = -1L
            lastFillPermille = -1
        }
        setPlaying(state.playing)
        if (textChanged || playingChanged) {
            if (!layoutCompact) updateProgress()
            handler.removeCallbacks(tick)
            if (playing && !layoutCompact) handler.postDelayed(tick, 1000L)
        } else if (!playing && !layoutCompact) {
            updateProgress()
        }
    }

    /** Kapak değişmediyse (referans ya da generationId) hiçbir şey yapma. */
    private fun bindArtwork(bitmap: Bitmap?) {
        // Sahne ortam ışığı kapak rengini alır (yalnızca kapak değişince bildirilir).
        SahneCover.publish(bitmap)
        if (bitmap === shownArtwork) return
        if (bitmap != null && bitmap.generationId == shownGeneration) {
            shownArtwork = bitmap
            return
        }
        shownArtwork = bitmap
        if (bitmap == null) {
            shownGeneration = -1
            artworkImage.visibility = View.GONE
            artworkImage.setImageDrawable(null)
            placeholderIcon.visibility = View.VISIBLE
        } else {
            shownGeneration = bitmap.generationId
            val limit = maxOf(artworkImage.width, artworkImage.height).takeIf { it > 0 } ?: ARTWORK_MAX_PX
            if (maxOf(bitmap.width, bitmap.height) <= limit) {
                artworkImage.setImageBitmap(bitmap)
            } else {
                // Büyük kapak: görüntü boyutuna arka planda küçült, ana iş parçacığı GPU'ya dev doku yüklemesin.
                artworkImage.setImageDrawable(null)
                artworkIo.runSafely {
                    val scaled = downscale(bitmap, limit)
                    handler.post { if (shownArtwork === bitmap) artworkImage.setImageBitmap(scaled) }
                }
            }
            artworkImage.visibility = View.VISIBLE
            placeholderIcon.visibility = View.GONE
        }
    }

    private fun downscale(src: Bitmap, limit: Int): Bitmap {
        val scale = limit.toFloat() / maxOf(src.width, src.height)
        return Bitmap.createScaledBitmap(src, maxOf(1, (src.width * scale).toInt()), maxOf(1, (src.height * scale).toInt()), true)
    }

    /** Oynat/duraklat ikonu yalnızca durum gerçekten değişince çapraz geçişle değişir. */
    private fun setPlaying(isPlaying: Boolean) {
        val previous = shownPlaying
        if (previous == isPlaying) return
        shownPlaying = isPlaying
        SahneCover.publishPlaying(isPlaying)
        onPlayingChanged?.invoke(isPlaying)
        val res = if (sahne) (if (isPlaying) R.drawable.ic_sahne_pause else R.drawable.ic_sahne_play)
        else if (isPlaying) R.drawable.ic_mdi_pause else R.drawable.ic_mdi_play
        playButton.contentDescription = context.getString(
            if (isPlaying) R.string.launcher_media_pause else R.string.launcher_media_play,
        )
        if (previous == null) {
            playIcon.setImageResource(res)
            return
        }
        val half = LauncherTheme.CROSSFADE_MS / 2
        playIcon.animate().cancel()
        playIcon.animate().alpha(0f).setDuration(half).withEndAction {
            playIcon.setImageResource(res)
            playIcon.animate().alpha(1f).setDuration(half).start()
        }.start()
    }

    private fun setControlsEnabled(enabled: Boolean) {
        val alpha = if (enabled) 1f else 0.4f
        for (b in arrayOf(prevButton, playButton, nextButton)) {
            b.isEnabled = enabled
            b.alpha = alpha
        }
    }

    /** Son bilinen konumdan ekstrapolasyonla çubuğu ve geçen süreyi günceller; yalnızca değişince yazar. */
    private fun updateProgress() {
        var pos = basePositionMs
        if (playing) pos += SystemClock.elapsedRealtime() - baseRealtimeMs
        if (pos < 0L) pos = 0L
        if (durationMs > 0L && pos > durationMs) pos = durationMs

        val sec = pos / 1000L
        if (sec != lastElapsedSec) {
            lastElapsedSec = sec
            elapsedView.text = formatTime(sec)
        }
        val totalSec = durationMs / 1000L
        if (totalSec != lastTotalSec) {
            lastTotalSec = totalSec
            totalView.text = if (durationMs > 0L) formatTime(totalSec) else ""
        }
        val permille = if (durationMs > 0L) (1000L * pos / durationMs).toInt() else 0
        if (permille != lastFillPermille) {
            lastFillPermille = permille
            progressFill.scaleX = permille / 1000f
            if (sahne) placeKnob()
        }
    }

    private fun formatTime(totalSec: Long): String {
        val m = totalSec / 60
        val s = totalSec % 60
        return if (s < 10) "$m:0$s" else "$m:$s"
    }

    /** Paket adını uygulama etiketine çevirir; bulunamazsa paket adı kalır. */
    /** Başlık / kapak dokunuşunun açacağı uygulama ([MediaSourcePick.appToOpen]); yoksa dokunma alanı yok. */
    private var openTarget: String? = null

    private fun updateOpenTarget(pkg: String?) {
        if (pkg == openTarget) return
        openTarget = pkg
        val clickable = pkg != null
        titleView.isClickable = clickable
        artworkBox.isClickable = clickable
        artworkBox.contentDescription = pkg?.let { context.getString(R.string.launcher_media_open_app, appLabel(it)) }
        artworkBox.importantForAccessibility =
            if (clickable) View.IMPORTANT_FOR_ACCESSIBILITY_YES else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    }

    /** Kartın uygulamasını açar (oturumun "şu an çalıyor" ekranı ya da başlatıcı). */
    private fun openCardApp() {
        val pkg = openTarget ?: return
        if (!controller.openApp(pkg)) {
            android.widget.Toast.makeText(context, R.string.launcher_media_open_failed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun appLabel(pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    // --- Kaynak seçici ---

    /** OEM ses kaynağı (`CURRENT_SOURCE_TYPE`; demoda `launcher_demo_oem_source`). */
    private fun oemSource(): Int? = if (LauncherDemo.enabled) LauncherDemo.oemSource else OemAudioSource.currentSource

    /** Kaynak yazısının dokunma alanı: yazının çevresinde [SOURCE_TOUCH_PAD_DP], en az [SOURCE_TOUCH_MIN_W_DP] genişlik. */
    private fun updateSourceTouch() {
        val root = view as? ViewGroup ?: return
        if (sourceLabel.visibility != View.VISIBLE || sourceLabel.width == 0 || !sourceLabel.isAttachedToWindow ||
            sourceLabel.text.isNullOrEmpty() || mode == Mode.NO_ACCESS) {
            if (root.touchDelegate != null) root.touchDelegate = null
            sourceTouch.setEmpty()
            return
        }
        val r = Rect(0, 0, sourceLabel.width, sourceLabel.height)
        try {
            root.offsetDescendantRectToMyCoords(sourceLabel, r)
        } catch (e: IllegalArgumentException) {
            return // yazı bu kartın altında değil (yerleşim değişiyor)
        }
        val pad = context.dp(SOURCE_TOUCH_PAD_DP)
        r.inset(-pad, -pad)
        r.right = maxOf(r.right, r.left + context.dp(SOURCE_TOUCH_MIN_W_DP))
        if (r == sourceTouch && root.touchDelegate != null) return
        sourceTouch.set(r)
        root.touchDelegate = TouchDelegate(Rect(r), sourceLabel)
    }

    private fun activityOf(c: Context): Activity? {
        var x: Context? = c
        while (x is ContextWrapper) {
            if (x is Activity) return x
            x = x.baseContext
        }
        return null
    }

    /**
     * Kaynak seçici: Bluetooth (bağlı telefonun adı), Radyo FM, Radyo AM, USB (OEM bağlı derse; sonradan eklenir); geçerli
     * kaynak işaretli. Seçim [OemAudioSource] üzerinden OEM'e gider (Bluetooth: kartın Oynat yolu; diğerleri OPEN_SOURCE).
     * Satırlar 96dp. Demoda araca bir şey gitmez, yalnızca demo kaynağı değişir.
     */
    private fun openSourcePicker() {
        val activity = activityOf(context) ?: return
        if (sourcePicker?.isShowing == true) return
        val current = oemSource()
        val demo = LauncherDemo.enabled
        // Telefon adı yalnızca ekranda gösterilir (günlüğe yazılmaz).
        val phone = if (demo) PhoneDemo.DEVICE_NAME else HfpLink.deviceName()
        sourcePicker = showLauncherSheet(activity, R.string.launcher_media_source_title, widthDp = PICKER_WIDTH_DP) { d ->
            // Açık uygulamalarla satırlar ekrana sığmayabilir: liste kayar.
            val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            addView(android.widget.ScrollView(activity).apply {
                isVerticalScrollBarEnabled = false
                addView(list)
            }, sheetFullWidth())
            fun addRow(row: View) {
                if (list.childCount > 0) list.addView(gap(activity, 12, vertical = true))
                list.addView(row, sheetFullWidth())
            }
            val cardOnApp = !demo && mode == Mode.SESSION && MediaSourcePick.isThirdParty(lastSource)
            fun add(type: Int) {
                addRow(sourceRow(activity, type, SourceChoice.isCurrent(type, current, cardOnApp), if (type == OemAudioSource.BT_MUSIC) phone else null) {
                    d.dismiss()
                    pickSource(type)
                })
            }
            SourceChoice.entries(usbAvailable = false).forEach { add(it) }
            if (!demo) {
                controller.appSessionPackages().forEach { pkg ->
                    addRow(appRow(activity, pkg, current = mode == Mode.SESSION && pkg == lastSource) {
                        d.dismiss()
                        controller.selectApp(pkg)
                    })
                }
            }
            if (demo) {
                if (LauncherDemo.usb) add(SourceChoice.USB0)
            } else {
                OemAudioSource.checkUsb(context) { ok -> if (ok && d.isShowing) add(SourceChoice.USB0) }
            }
        }
    }

    private fun pickSource(type: Int) {
        if (LauncherDemo.enabled) {
            LauncherDemo.oemSource = type
            bind(LauncherDemo.media())
            return
        }
        if (type == OemAudioSource.BT_MUSIC) {
            controller.selectBluetooth()
        } else {
            OemAudioSource.openSource(context, type) {
                android.widget.Toast.makeText(context, R.string.launcher_media_source_failed, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Boşta Oynat: bilinen OEM kaynağı ya da son uygulama açılır; ikisi de olmazsa kullanıcı kaynağı seçer. */
    private fun playFromIdle() {
        val source = oemSource()
        if (source != null && MediaSourcePick.idlePlayOpensOem(controller.lastSourcePackage(), source)) {
            pickSource(source)
        } else if (!controller.launchLastSource()) {
            openSourcePicker()
        }
    }

    private fun sourceRow(activity: Activity, type: Int, current: Boolean, hint: String?, onClick: () -> Unit): View {
        val (iconRes, labelRes) = when (type) {
            OemAudioSource.BT_MUSIC -> R.drawable.ic_mdi_bluetooth to R.string.launcher_media_source_bt
            SourceChoice.FM -> R.drawable.ic_mdi_radio to R.string.launcher_media_source_fm
            SourceChoice.AM -> R.drawable.ic_mdi_radio to R.string.launcher_media_source_am
            else -> R.drawable.ic_mdi_usb to R.string.launcher_media_source_usb
        }
        return pickerRow(activity, icon(activity, iconRes, 36, if (current) LauncherTheme.ACCENT else LauncherTheme.TEXT_SECONDARY),
            activity.getString(labelRes), hint, current, onClick)
    }

    private fun appRow(activity: Activity, pkg: String, current: Boolean, onClick: () -> Unit): View {
        val image = ImageView(activity).apply {
            try {
                setImageDrawable(activity.packageManager.getApplicationIcon(pkg))
            } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                // uygulama bu arada kaldırılmış olabilir; satır simgesiz kalır
            }
            layoutParams = LinearLayout.LayoutParams(activity.dp(36), activity.dp(36))
        }
        return pickerRow(activity, image, appLabel(pkg), null, current, onClick)
    }

    private fun pickerRow(activity: Activity, leading: View, label: String, hint: String?, current: Boolean, onClick: () -> Unit): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(activity, LauncherTheme.CONTROL, PICKER_RADIUS_DP, if (current) LauncherTheme.ACCENT else 0)
            minimumHeight = activity.dp(PICKER_ROW_DP)
            val p = activity.dp(24)
            setPadding(p, 0, p, 0)
            addView(leading)
            addView(gap(activity, 20))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(activity, LauncherTheme.BODY_SP, medium = true).apply { text = label })
                if (!hint.isNullOrBlank()) {
                    addView(text(activity, LauncherTheme.LABEL_SP, LauncherTheme.TEXT_SECONDARY).apply { text = hint })
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (current) addView(icon(activity, R.drawable.ic_mdi_check, 32, LauncherTheme.ACCENT))
            isClickable = true
            isFocusable = true
            isSelected = current
            contentDescription = if (current) label + ", " + activity.getString(R.string.launcher_media_source_current) else label
            setOnClickListener { onClick() }
            pressFeedback()
        }
    }

    // --- Sahne yerleşimi ---

    /**
     * Sahne başlığı: uzun şarkı adı iki satıra kelime bölmeden sığana kadar [SAHNE_TITLE_SP]'den
     * [SAHNE_TITLE_MIN_SP]'ye küçülür (araçta "İstanbul Benden Büy…" kesiliyordu); yine sığmazsa "…" kalır.
     * Kaynak + başlık + sanatçı bloğu kapak yüksekliğini ([ART_DP]) geçmez: geçecekse başlık küçülür, en küçükte de
     * sığmazsa tek satıra iner (araçta sanatçı satırı altta kesiliyordu).
     */
    private fun fitSahneTitle() {
        if (!sahne) return
        val width = titleView.width - titleView.paddingLeft - titleView.paddingRight
        val text = titleView.text
        if (width <= 0 || text.isNullOrEmpty()) return
        val paint = TextPaint(titleView.paint)
        val metrics = context.resources.displayMetrics
        // Başlığa kalan yükseklik: kapak − (üst boşluk + kaynak satırı + başlık üstü + sanatçı üstü + sanatçı satırı).
        val sourceH = maxOf(sourceLabel.paint.getFontMetricsInt(null), context.dp(18))
        val artistH = if (artistView.visibility == View.GONE || artistView.text.isNullOrEmpty()) 0
            else artistView.paint.getFontMetricsInt(null) + artistView.paddingTop + artistView.paddingBottom + context.dp(1)
        val room = context.dp(ART_DP) - context.dp(4) - sourceH - context.dp(5) - artistH
        var sp = SAHNE_TITLE_SP
        var lines = 2
        while (true) {
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setBreakStrategy(titleView.breakStrategy)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setIncludePad(titleView.includeFontPadding)
                .build()
            val n = minOf(layout.lineCount, 2)
            val fitsLines = layout.lineCount <= 2 && (0 until layout.lineCount - 1).none { splitsWord(text, layout.getLineEnd(it)) }
            val fitsHeight = layout.getLineBottom(n - 1) <= room
            if (fitsLines && fitsHeight) break
            if (sp - 2f >= SAHNE_TITLE_MIN_SP) { sp -= 2f; continue }
            // En küçük boyda: iki satır yüksekliğe sığmıyorsa tek satır ("…").
            if (!fitsHeight || layout.lineCount > 2) lines = if (n >= 2 && layout.getLineBottom(1) > room) 1 else 2
            break
        }
        if (titleView.maxLines != lines) titleView.maxLines = lines
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)
        if (titleView.textSize != px) titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    }

    /** Satır sonları ve art arda boşluklar tek boşluk, baş/son boşluk atılır; boş metin null. */
    private fun oneLine(s: String?): String? = s?.replace(WHITESPACE, " ")?.trim()?.ifEmpty { null }

    private fun splitsWord(text: CharSequence, end: Int): Boolean =
        end in 1 until text.length && !text[end - 1].isWhitespace() && !text[end].isWhitespace() && text[end - 1] != '-'

    /**
     * Taslaktaki medya paneli: üstte 112dp kapak ve yanında kaynak (uygulama ikonu + adı), başlık, sanatçı; altında ince
     * ilerleme çizgisi, topuzu ve süreler; en altta ortalı önceki / oynat / sonraki. Görünümler tam yerleşimdekilerle
     * aynı örneklerdir; yalnızca yeniden dizilir.
     */
    private fun buildSahneView(): View {
        listOf(sourceLabel, titleView, artistView, progressBox, controlsRow, artworkBox).forEach { (it.parent as? ViewGroup)?.removeView(it) }
        mainBox.removeAllViews()
        sourceLabel.textSize = 20f
        SahneFonts.apply(sourceLabel, 500)
        titleView.textSize = SAHNE_TITLE_SP
        titleView.maxLines = 2
        SahneFonts.apply(titleView, 600, -0.01f)
        titleView.addOnLayoutChangeListener { _, l, _, r, _, ol, _, or, _ -> if (r - l != or - ol) titleView.post { fitSahneTitle() } }
        artistView.textSize = 20f
        SahneFonts.apply(artistView, 400)
        elapsedView.textSize = 20f
        totalView.textSize = 20f
        SahneFonts.apply(elapsedView, 400)
        SahneFonts.apply(totalView, 400)
        // Süreler sabit genişlikte: saniyede bir değişen metin yalnızca kendini çizdirir, kart ve ekran yeniden yerleşmez.
        val timeW = elapsedView.paint.measureText(TIME_WIDEST).toInt() + 2
        elapsedView.layoutParams = LinearLayout.LayoutParams(timeW, LinearLayout.LayoutParams.WRAP_CONTENT)
        totalView.layoutParams = LinearLayout.LayoutParams(timeW, LinearLayout.LayoutParams.WRAP_CONTENT)
        totalView.gravity = Gravity.END
        val meta = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, context.dp(4), 0, 0)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(sourceIcon, LinearLayout.LayoutParams(context.dp(18), context.dp(18)).apply { marginEnd = context.dp(8) })
                addView(sourceLabel)
            })
            // Taslakta başlık satır yüksekliği 1.1: doğal satırdan kısa, bu yüzden aralıklar biraz daraltıldı.
            addView(titleView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(5) })
            addView(artistView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(1) })
        }
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(artworkBox, LinearLayout.LayoutParams(context.dp(ART_DP), context.dp(ART_DP)))
            addView(meta, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(22) })
        }
        // İlerleme: 4dp iz, dolgu ve üzerinde 14dp topuz (topuz izin dışına taşar).
        progressTrack.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(4))
        progressTrack.clipChildren = false
        progressTrack.clipToPadding = false
        progressTrack.addView(knob, FrameLayout.LayoutParams(context.dp(KNOB_DP), context.dp(KNOB_DP), Gravity.CENTER_VERTICAL or Gravity.START))
        (timesGap.layoutParams as LinearLayout.LayoutParams).height = context.dp(12)
        progressBox.clipChildren = false
        // Kontroller: 84 / 100 / 84, aralarında 28dp.
        resize(prevButton, 76)
        resize(nextButton, 76)
        resize(playButton, 100)
        setGapWidth(controlsGap1, 28)
        setGapWidth(controlsGap2, 28)
        (playIcon.layoutParams as FrameLayout.LayoutParams).apply { width = context.dp(34); height = context.dp(34) }
        (prevButton.getChildAt(0).layoutParams as FrameLayout.LayoutParams).apply { width = context.dp(30); height = context.dp(30) }
        (nextButton.getChildAt(0).layoutParams as FrameLayout.LayoutParams).apply { width = context.dp(30); height = context.dp(30) }
        controlsRow.gravity = Gravity.CENTER
        playButton.elevation = context.dpF(10f)
        (prevButton.getChildAt(0) as ImageView).setImageResource(R.drawable.ic_sahne_prev)
        (nextButton.getChildAt(0) as ImageView).setImageResource(R.drawable.ic_sahne_next)
        playIcon.setImageResource(R.drawable.ic_sahne_play)
        placeholderIcon.setImageResource(R.drawable.ic_sahne_note)
        (placeholderIcon.layoutParams as FrameLayout.LayoutParams).apply { width = context.dp(44); height = context.dp(44) }
        styleSahne()
        return FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            val p = context.dp(28)
            setPadding(p, context.dp(20), p, context.dp(20))
            // İçerik panelde dikey ortalı: kapak + başlık, ilerleme, kontroller (boş boşluk yok).
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = false
                addView(top)
                addView(progressBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(22) })
                addView(controlsRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(14) })
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL))
            addView(grantButton.also { (it.parent as? ViewGroup)?.removeView(it) },
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, context.dp(SAHNE_GRANT_DP), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = context.dp(12) })
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> placeKnob() }
        }
    }

    /** Sahne renkleri: kapak yer tutucusu gradyan, düğmeler (oynat birincil), ilerleme dolgusu ve topuz. */
    private fun styleSahne() {
        artworkBox.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF3A1D4F.toInt(), 0xFF7A2F57.toInt(), 0xFFD0654A.toInt()),
        ).apply { cornerRadius = context.dpF(18f) }
        artworkBox.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) = outline.setRoundRect(0, 0, v.width, v.height, context.dpF(18f))
        }
        artworkBox.elevation = context.dpF(8f)
        placeholderIcon.setTint(0xD1FFFFFF.toInt())
        sourceLabel.setTextColor(Sahne.INK_3)
        titleView.setTextColor(Sahne.INK)
        artistView.setTextColor(Sahne.INK_2)
        elapsedView.setTextColor(Sahne.INK_3)
        totalView.setTextColor(Sahne.INK_3)
        progressTrack.background = rounded(context, Sahne.TRACK, 2f)
        progressFill.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Sahne.INK and 0x00FFFFFF or 0x99000000.toInt(), Sahne.INK),
        ).apply { cornerRadius = context.dpF(2f) }
        knob.background = oval(Sahne.KNOB)
        knob.elevation = context.dpF(3f)
        for (b in arrayOf(prevButton, nextButton)) {
            b.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Sahne.SOFT_FILL)
                setStroke(context.dp(1), Sahne.HAIR)
            }
            (b.getChildAt(0) as ImageView).setTint(Sahne.INK)
        }
        playButton.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Sahne.CTA_TOP, Sahne.CTA_BOTTOM),
        ).apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            if (Sahne.CTA_RING != 0) setStroke(context.dp(1), Sahne.CTA_RING)
        }
        playIcon.setTint(Sahne.CTA_INK)
        grantButton.setTextColor(Sahne.INK)
        grantButton.background = rounded(context, Sahne.SOFT_FILL, Sahne.CONTROL_RADIUS_DP, Sahne.HAIR_2)
    }

    /** Topuz dolgunun ucunda: iz genişliği × ilerleme. */
    private fun placeKnob() {
        val w = progressTrack.width
        if (w <= 0) return
        val half = context.dp(KNOB_DP) / 2f
        knob.translationX = w * (lastFillPermille.coerceAtLeast(0) / 1000f) - half
        knob.visibility = if (lastShowProgress == true) View.VISIBLE else View.INVISIBLE
    }

    /** Kaynağın küçük uygulama ikonu (kapak bilgisinin yanında); arka planda çözülür, değişince yenilenir. */
    private fun bindSourceIcon(pkg: String?) {
        if (pkg == shownSourceIcon) return
        shownSourceIcon = pkg
        if (pkg.isNullOrEmpty()) {
            sourceIcon.visibility = View.GONE
            sourceIcon.setImageDrawable(null)
            return
        }
        artworkIo.runSafely {
            val d = try { com.mapcontrol.util.AppIconHelper.load(context, pkg) } catch (e: Exception) { null }
            handler.post {
                if (shownSourceIcon != pkg) return@post
                sourceIcon.setImageDrawable(d)
                sourceIcon.visibility = if (d != null) View.VISIBLE else View.GONE
            }
        }
    }

    private companion object {
        /** Sahne: kapak kenarı ve ilerleme topuzu (dp). */
        const val ART_DP = 156
        const val KNOB_DP = 14
        /** Kaynak yazısının dokunma alanı (yazının çevresine) ve en az genişliği. */
        const val SOURCE_TOUCH_PAD_DP = 20
        const val SOURCE_TOUCH_MIN_W_DP = 220
        /** Kaynak seçici: pencere genişliği, satır yüksekliği (büyük dokunma hedefi) ve köşe. */
        const val PICKER_WIDTH_DP = 640
        const val PICKER_ROW_DP = 96
        const val PICKER_RADIUS_DP = 16f
        /** Sürelerin en geniş biçimi (dakika 3 haneye kadar): sabit genişlik bundan ölçülür. */
        const val TIME_WIDEST = "888:88"
        val WHITESPACE = Regex("\\s+")
        /** Görüntü boyutu henüz bilinmiyorsa kapak kenarı üst sınırı (px). */
        const val ARTWORK_MAX_PX = 480
        /** Bu yüksekliğin altında kaynak ve sanatçı satırları gizlenir. */
        const val SHORT_HEIGHT_DP = 250
        /** Bu yüksekliğin üstünde kapak ve başlık büyür. */
        const val TALL_HEIGHT_DP = 380
        const val TALL_TITLE_SP = 56f
        private const val SAHNE_TITLE_SP = 32f
        private const val SAHNE_TITLE_MIN_SP = 24f
        const val TALL_ARTIST_SP = 36f
        /** Bu yüksekliğin altında süre satırı da gizlenir; düğmeler en az MIN_PLAY_DP olur. */
        const val TINY_HEIGHT_DP = 190
        const val PROGRESS_GAP_DP = 12
        const val MIN_PLAY_DP = 56
        /** "Erişim ver" düğmesi yüksekliği (en az 76dp). */
        const val GRANT_BUTTON_DP = 76
        /** Sahne izin düğmesi: 72dp dokunma yüksekliği. */
        const val SAHNE_GRANT_DP = 72

        /** Kompakt (2×1) yerleşimde başlık boyutu ve kart iç boşluğu. */
        const val COMPACT_TITLE_SP = 28f
        const val COMPACT_PADDING_DP = 24
    }
}
