package com.mapcontrol.launcher

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.mapcontrol.R

/**
 * Mini göstergenin yolculuk paneli ([MiniSide]): öncü sayı yolculuk mesafesi, altında süre, ortalama hız ve ortalama
 * tüketim (sürüşte de aynı; anlık tüketim yok, 7 Kasım kararı). Veri [MiniDashActivity]'den gelir (tek [TripSource]
 * dinleyicisi); veri yokken etkinlik paneli gizler.
 */
internal class MiniTripPanel(private val context: Context) {

    private val headIcon = ImageView(context).apply {
        setImageResource(R.drawable.ic_sahne_route)
        imageTintList = ColorStateList.valueOf(Sahne.INK_3)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val head = sahneText(context, HEAD_SP, Sahne.INK_3, 500).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val big = sahneText(context, 42f, Sahne.NUMERAL, 300, -0.03f).apply {
        maxLines = 1
        includeFontPadding = false
        fontFeatureSettings = "tnum"
    }
    // Yazı tipi payı kapalı: satır yüksekliği MiniSide.tripMinDp'deki ölçüyle (TRIP_LINE) aynı.
    private val bigUnit = sahneText(context, 20f, Sahne.INK_2, 400).apply { maxLines = 1; includeFontPadding = false }
    private val heroRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = true
        addView(big, LinearLayout.LayoutParams(WRAP, WRAP))
        addView(bigUnit, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(8) })
    }
    private val statViews = Array(3) {
        sahneText(context, 20f, Sahne.INK, 500).apply { maxLines = 1; includeFontPadding = false; fontFeatureSettings = "tnum" }
    }
    private val statDividers = Array(2) { View(context).apply { setBackgroundColor(Sahne.HAIR_2) } }
    private val statsRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        statViews.forEachIndexed { i, v ->
            if (i > 0) addView(statDividers[i - 1], LinearLayout.LayoutParams(context.dp(1), context.dp(20)).apply {
                marginStart = context.dp(MiniSide.STAT_GAP_DP); marginEnd = context.dp(MiniSide.STAT_GAP_DP)
            })
            addView(v, LinearLayout.LayoutParams(WRAP, WRAP))
        }
    }
    /** Geniş biçim: değerler etiketli satırlar hâlinde alt alta (büyük panel boş kalmaz). */
    private val listLabels = Array(3) { sahneText(context, 20f, Sahne.INK_3, 400).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END } }
    private val listValues = Array(3) { sahneText(context, 20f, Sahne.INK, 500).apply { maxLines = 1; fontFeatureSettings = "tnum" } }
    private val listRows = Array(3) { i ->
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(listLabels[i], LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(listValues[i], LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(12) })
        }
    }
    private val listHairs = Array(3) { View(context).apply { setBackgroundColor(Sahne.HAIR_2) } }
    private val statsList = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        listRows.forEachIndexed { i, row ->
            addView(listHairs[i], LinearLayout.LayoutParams(MATCH, context.dp(1)))
            addView(row, LinearLayout.LayoutParams(MATCH, context.dp(MiniSide.LIST_ROW_DP)))
        }
    }
    /** Başlık ve öncü satır. */
    private val heroBox = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(headIcon, LinearLayout.LayoutParams(context.dp(20), context.dp(20)).apply { marginEnd = context.dp(8) })
            addView(head, LinearLayout.LayoutParams(0, WRAP, 1f))
        }, LinearLayout.LayoutParams(MATCH, context.dp(MiniSide.TRIP_HEAD_DP)))
        addView(heroRow, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = context.dp(MiniSide.TRIP_GAP_DP) })
    }

    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        background = GlassDrawable(context, Sahne.PANEL_RADIUS_DP)
        clipChildren = false
        addView(heroBox, LinearLayout.LayoutParams(MATCH, WRAP))
        addView(statsRow, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(MiniSide.TRIP_GAP_DP) })
        addView(statsList, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(MiniSide.LIST_GAP_DP) })
        // Güvenlik ağı: değer satırı panele sığmıyorsa yarım kesik görünmesin, düşsün (yükseklik büyüyünce geri gelir).
        addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            if (!statsWanted) return@addOnLayoutChangeListener
            val inner = (bottom - top) - paddingTop - paddingBottom
            val need = heroBox.height + if (wide) context.dp(MiniSide.LIST_GAP_DP) + statsListNeedPx()
                else context.dp(MiniSide.TRIP_GAP_DP) + statsRowNeedPx()
            val fit = need <= inner
            if (fit != statsFit) post { statsFit = fit; applyStatsVisibility() }
        }
    }

    private var wide = false
    private var statsWanted = false
    private var statsFit = true

    private fun statsListNeedPx(): Int = listRows.count { it.visibility == View.VISIBLE } * context.dp(MiniSide.LIST_ROW_DP + 1)

    private fun statsRowNeedPx(): Int = if (statsRow.height > 0) statsRow.height else (statViews[0].textSize * MiniSide.TRIP_LINE).toInt()

    private fun applyStatsVisibility() {
        statsRow.visibility = if (statsWanted && !wide && statsFit) View.VISIBLE else View.GONE
        statsList.visibility = if (statsWanted && wide && statsFit) View.VISIBLE else View.GONE
    }

    private var heroSp = 42f
    private var shown: Any? = null

    fun setSizes(m: MiniLayout) {
        val p = context.dp(m.panelPadDp + 6)
        view.setPadding(p, context.dp(m.panelPadDp), p, context.dp(m.panelPadDp))
        heroSp = m.tripHeroSp
        bigUnit.textSize = (m.tripHeroSp * 0.45f).coerceAtLeast(18f)
        statViews.forEach { it.textSize = m.tripStatSp }
        statDividers.forEach { (it.layoutParams as LinearLayout.LayoutParams).height = context.dp((m.tripStatSp * 1.1f).toInt()) }
        head.textSize = if (m.tripHeroSp >= 56f) 20f else HEAD_SP
        wide = m.tripList
        // Liste biçiminde içerik üstten başlar (ortada yüzen tek satır yerine etiketli liste); bölünmüşte panel kendi boyunda, ortalı.
        view.gravity = if (wide) Gravity.TOP else Gravity.CENTER_VERTICAL
        listLabels.forEach { it.textSize = m.tripStatSp * 0.8f }
        listValues.forEach { it.textSize = m.tripStatSp }
        statsFit = true
        applyStatsVisibility()
        shown = null // ölçü değişti: yeniden yaz
    }

    /** [content] NO_DATA iken çağrılmaz (panel gizli). [minutes] sürüş süresi ([TripTotals.drivingMinutes]). */
    fun bind(content: TripHero.Content, data: TripData, minutes: Int?) {
        val km = data.tripKm
        val hero: TripHeroLine? = km?.let {
            TripHeroLine(tripOne(it), context.getString(R.string.launcher_sahne_km), context.getString(R.string.launcher_widget_trip), Sahne.NUMERAL)
        }
        val title = context.getString(R.string.launcher_widget_trip)
        val kinds = MiniSide.stats(content, km, minutes, data.avgSpeedKmh, data.avgL100)
        val stats = kinds.map { s ->
            when (s) {
                MiniSide.Stat.DISTANCE -> context.getString(R.string.launcher_km_value, tripOne(km!!))
                MiniSide.Stat.DURATION -> tripMinutesText(context, minutes!!)
                MiniSide.Stat.AVG_SPEED -> context.getString(R.string.launcher_kmh_value, data.avgSpeedKmh!!)
                MiniSide.Stat.AVG_CONS -> context.getString(R.string.launcher_l100_value, tripOne(data.avgL100!!))
            }
        }
        val labels = kinds.map { s ->
            context.getString(when (s) {
                MiniSide.Stat.DISTANCE -> R.string.launcher_totals_short_km
                MiniSide.Stat.DURATION -> R.string.launcher_totals_short_time
                MiniSide.Stat.AVG_SPEED -> R.string.launcher_trip_avg_speed_label
                MiniSide.Stat.AVG_CONS -> R.string.launcher_trip_avg_cons_label
            })
        }
        val key = Triple(title to hero, stats, heroSp)
        if (key == shown) return
        shown = key
        applyHero(title, hero)
        val unitSp = (statViews[0].textSize / context.resources.displayMetrics.scaledDensity * 0.72f).toInt()
        statViews.forEachIndexed { i, v ->
            val text = stats.getOrNull(i)
            v.visibility = if (text == null) View.GONE else View.VISIBLE
            if (text != null) v.text = withUnit(context, text, unitSp)
            if (i > 0) statDividers[i - 1].visibility = v.visibility
            listRows[i].visibility = v.visibility
            listHairs[i].visibility = v.visibility
            if (text != null) {
                listLabels[i].text = labels[i]
                listValues[i].text = withUnit(context, text, unitSp)
            }
        }
        statsWanted = stats.isNotEmpty()
        applyStatsVisibility()
        view.contentDescription = (listOf(title) + listOfNotNull(hero?.let { listOfNotNull(it.value, it.unit).joinToString(" ") }) +
            kinds.indices.map { "${labels[it]} ${stats[it]}" }).joinToString(", ")
    }

    private fun applyHero(title: String, h: TripHeroLine?) {
        head.text = title
        heroRow.visibility = if (h == null) View.GONE else View.VISIBLE
        if (h == null) return
        big.textSize = heroSp
        big.setTextColor(h.color)
        big.text = h.value
        bigUnit.visibility = if (h.unit == null) View.GONE else View.VISIBLE
        if (h.unit != null) bigUnit.text = h.unit
    }

    private companion object {
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val HEAD_SP = 17f
    }
}

/**
 * Mini göstergenin telefon paneli ([MiniSide.phone]): son isimli arama için tek dokunuşla "Geri ara" ve en çok üç favori
 * avatar düğmesi. Veri ve arama yolu ana ekranın telefon paneliyle aynıdır ([PhoneSource], [phoneDial], [PhonePhotos]);
 * [PhoneSource] yalnızca panel görünürken dinlenir. Bağlı telefon yokken (ya da rehber yüklenirken / boşken) kutu çizilmez,
 * tek sessiz satır kalır; izin eksikse satırda "İzin ver". Gelen ve süren arama ana ekranın arama şeridinde kalır.
 * İsim ve numara yalnızca ekrana gider; günlüğe yazılmaz.
 */
internal class MiniPhonePanel(private val activity: Activity, private val onShape: (MiniSide.PhoneView) -> Unit) : PhoneListener {

    private val context: Context get() = activity
    private val main = Handler(Looper.getMainLooper())
    private var visible = false
    private var snap: PhoneSnapshot? = null
    private var viewNow: MiniSide.PhoneView? = null
    private var layout: MiniLayout? = null
    private var lastNumber: String? = null
    private val minuteTick = object : Runnable {
        override fun run() {
            if (!visible) return
            refreshTime()
            main.postDelayed(this, MINUTE_MS)
        }
    }

    // --- Sessiz satır (kutusuz) ---
    private val quietIcon = ImageView(activity).apply {
        setImageResource(R.drawable.ic_sahne_phone)
        imageTintList = ColorStateList.valueOf(Sahne.INK_3)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val quietText = sahneText(activity, 18f, Sahne.INK_3, 500).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val grant = sahneText(activity, 18f, Sahne.CTA_INK, 600).apply {
        setText(R.string.launcher_phone_grant)
        gravity = Gravity.CENTER
        background = phoneCtaBackground(activity)
        setPadding(activity.dp(22), 0, activity.dp(22), 0)
        minWidth = activity.dp(MiniLayout.MIN_TOUCH_DP)
        isClickable = true
        isFocusable = true
        setOnClickListener { PhoneSource.requestPermissions(activity) }
        pressFeedback()
    }
    private val quiet = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(activity.dp(8), 0, 0, 0)
        addView(quietIcon, LinearLayout.LayoutParams(activity.dp(22), activity.dp(22)).apply { marginEnd = activity.dp(12) })
        addView(quietText, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(grant, LinearLayout.LayoutParams(WRAP, activity.dp(MiniLayout.MIN_TOUCH_DP)))
    }

    // --- Geri ara ---
    private var lastAvatar = PhoneAvatarView(activity, 48, 20f)
    private val lastName = sahneText(activity, 20f, Sahne.INK, 600).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val lastGlyph = ImageView(activity).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    private val lastTime = sahneText(activity, 15f, Sahne.INK_3, 400).apply { maxLines = 1 }
    private val callBackText = sahneText(activity, 17f, Sahne.CTA_INK, 600).apply {
        setText(R.string.phone_call_back)
        maxLines = 1
    }
    private val callBack = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        background = phoneCtaBackground(activity)
        setPadding(activity.dp(14), 0, activity.dp(18), 0)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(ImageView(activity).apply {
            setImageResource(R.drawable.ic_sahne_call)
            imageTintList = ColorStateList.valueOf(Sahne.CALL_ACCEPT)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(activity.dp(22), activity.dp(22)).apply { marginEnd = activity.dp(8) })
        addView(callBackText)
    }
    private val lastMeta = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(lastName, LinearLayout.LayoutParams(MATCH, WRAP))
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(lastGlyph, LinearLayout.LayoutParams(activity.dp(16), activity.dp(16)))
            addView(lastTime, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = activity.dp(6) })
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = activity.dp(1) })
    }
    /** Satırın tamamı tek dokunuşla geri arar (düğme yalnızca görsel işaret). */
    private val callRow = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(activity, Sahne.SOFT_FILL, Sahne.SQUARE_RADIUS_DP, Sahne.HAIR_2)
        clipChildren = false
        isClickable = true
        isFocusable = true
        setOnClickListener { lastNumber?.let { dial(it) } }
        pressFeedback()
    }

    // --- Favoriler ---
    private val favRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
    private var shownFavs: List<PhoneContact>? = null
    private var shownFavDp = 0

    private val box = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = GlassDrawable(activity, Sahne.PANEL_RADIUS_DP)
        clipChildren = false
        addView(callRow, LinearLayout.LayoutParams(MATCH, activity.dp(MiniLayout.MIN_TOUCH_DP)))
        addView(favRow, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    val view: android.widget.FrameLayout = android.widget.FrameLayout(activity).apply {
        clipChildren = false
        addView(quiet, android.widget.FrameLayout.LayoutParams(MATCH, activity.dp(MiniLayout.MIN_TOUCH_DP), Gravity.CENTER_VERTICAL))
        addView(box, android.widget.FrameLayout.LayoutParams(MATCH, MATCH))
    }

    init {
        rebuildCallRow(48)
        bind(PhoneSnapshot.EMPTY)
    }

    fun setSizes(m: MiniLayout) {
        layout = m
        val p = context.dp(m.panelPadDp)
        box.setPadding(p, p, p, p)
        (callRow.layoutParams as LinearLayout.LayoutParams).height = context.dp(m.callRowDp)
        rebuildCallRow(m.callRowDp - 16)
        (callBack.layoutParams as LinearLayout.LayoutParams).height = context.dp(m.callRowDp - 16)
        (favRow.layoutParams as LinearLayout.LayoutParams).height = context.dp(MiniSide.favoriteRowDp(m))
        shownFavs = null
        snap?.let { s -> snap = null; bind(s) }
    }

    /** Avatar boyu değişince "Geri ara" satırı yeniden kurulur (avatar kendi boyunu yapıcıda alır). */
    private fun rebuildCallRow(avatarDp: Int) {
        callRow.removeAllViews()
        lastAvatar = PhoneAvatarView(activity, avatarDp, (avatarDp * 0.4f))
        callRow.setPadding(context.dp(8), 0, context.dp(8), 0)
        callRow.addView(lastAvatar, LinearLayout.LayoutParams(context.dp(avatarDp), context.dp(avatarDp)))
        callRow.addView(lastMeta, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = context.dp(12); marginEnd = context.dp(10) })
        callRow.addView(callBack, LinearLayout.LayoutParams(WRAP, context.dp(avatarDp)))
    }

    fun onVisible() {
        if (visible) return
        visible = true
        PhoneSource.start(activity, this)
        PhoneSource.addListener(this)
        PhoneSource.refreshData(RefreshReason.PANEL_OPENED)
        bind(PhoneSource.snapshot())
        main.removeCallbacks(minuteTick)
        main.postDelayed(minuteTick, MINUTE_MS)
    }

    fun onHidden() {
        if (!visible) return
        visible = false
        main.removeCallbacks(minuteTick)
        PhoneSource.removeListener(this)
        PhoneSource.stop(this)
    }

    override fun onPhoneChanged(snapshot: PhoneSnapshot) {
        if (snapshot != snap) bind(snapshot)
    }

    override fun onPhoneError(error: PhoneError) {
        if (visible && view.isAttachedToWindow) PhoneToast.show(context, PhoneUiLogic.errorRes(error))
    }

    private fun bind(s: PhoneSnapshot) {
        snap = s
        val v = MiniSide.phone(s)
        val changed = v != viewNow
        viewNow = v
        val content = v.state == MiniSide.Phone.CONTENT
        box.visibility = if (content) View.VISIBLE else View.GONE
        quiet.visibility = if (content) View.GONE else View.VISIBLE
        if (!content) {
            quietText.setText(when (v.state) {
                MiniSide.Phone.OFF -> R.string.launcher_mini_phone_off
                MiniSide.Phone.PERMISSION -> R.string.launcher_phone_permission
                MiniSide.Phone.LOADING -> R.string.launcher_phone_loading
                else -> R.string.launcher_phone_empty
            })
            grant.visibility = if (v.state == MiniSide.Phone.PERMISSION) View.VISIBLE else View.GONE
            quiet.contentDescription = quietText.text
        } else {
            bindLast(v.last, s)
            bindFavorites(v.favorites)
        }
        if (changed) onShape(v)
    }

    private fun bindLast(r: PhoneRecent?, s: PhoneSnapshot) {
        callRow.visibility = if (r == null) View.GONE else View.VISIBLE
        (favRow.layoutParams as LinearLayout.LayoutParams).topMargin = if (r == null) 0 else context.dp(MiniSide.ROW_GAP_DP)
        if (r == null) { lastNumber = null; return }
        val name = r.name.orEmpty().trim()
        lastNumber = r.number
        setTextIfChanged(lastName, name)
        val photo = PhoneUiLogic.recentPhotoKey(r, s.favorites) ?: directoryPhoto(r)
        lastAvatar.bind(photo, PhoneText.initials(name))
        lastGlyph.setImageResource(recentGlyph(r.type))
        // Cevapsız: yalnız işaret yanar (rozet/halka yok).
        lastGlyph.imageTintList = ColorStateList.valueOf(if (r.type == RecentType.MISSED) Sahne.ALERT else Sahne.INK_3)
        refreshTime()
        callRow.contentDescription = context.getString(R.string.phone_call_back) + ", " + name
    }

    /** Rehberde fotoğraf araması aynı arama kaydı ve aynı rehber için bir kez yapılır (her anlık görüntüde değil). */
    private var photoFor: Triple<PhoneRecent, List<PhoneContact>, String?>? = null

    private fun directoryPhoto(r: PhoneRecent): String? {
        val dir = PhoneSource.directory().contacts
        photoFor?.let { (cr, cd, key) -> if (cr == r && cd === dir) return key }
        return PhoneUiLogic.recentPhotoKey(r, dir).also { photoFor = Triple(r, dir, it) }
    }

    private fun refreshTime() {
        val r = viewNow?.last ?: return
        setTextIfChanged(lastTime, phoneTimeText(context, r.timeMs, ago = true))
    }

    private fun bindFavorites(list: List<PhoneContact>) {
        val m = layout
        val avatarDp = m?.favAvatarDp ?: 52
        favRow.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        if (list == shownFavs && avatarDp == shownFavDp) return
        shownFavs = list
        shownFavDp = avatarDp
        favRow.removeAllViews()
        list.forEach { c -> favRow.addView(favoriteCell(c, avatarDp), LinearLayout.LayoutParams(0, MATCH, 1f)) }
        // Eksik hücreler boş kalır: hücre genişliği kişi sayısıyla değişmez.
        repeat(MiniSide.FAVORITES - list.size) { favRow.addView(View(context), LinearLayout.LayoutParams(0, MATCH, 1f)) }
    }

    private fun favoriteCell(c: PhoneContact, avatarDp: Int): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false
        addView(PhoneAvatarView(context, avatarDp, avatarDp * 0.36f).apply { bind(c.photoKey, c.initials) },
            LinearLayout.LayoutParams(context.dp(avatarDp), context.dp(avatarDp)))
        addView(sahneText(context, NAME_SP, Sahne.INK_2, 500).apply {
            text = c.name
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(context.dp(4), 0, context.dp(4), 0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(MATCH, context.dp(MiniSide.NAME_ROW_DP)).apply { topMargin = context.dp(MiniSide.NAME_GAP_DP) })
        contentDescription = context.getString(R.string.launcher_phone_call_cd, c.name)
        isClickable = true
        isFocusable = true
        setOnClickListener { dial(c.number) }
        pressFeedback()
    }

    private fun dial(number: String) {
        phoneDial(context, number)
    }

    private companion object {
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val NAME_SP = 16f
        const val MINUTE_MS = 60_000L
    }
}
