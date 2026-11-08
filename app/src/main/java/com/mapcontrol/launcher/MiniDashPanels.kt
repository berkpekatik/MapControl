package com.mapcontrol.launcher

import android.content.Context
import android.content.res.ColorStateList
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
