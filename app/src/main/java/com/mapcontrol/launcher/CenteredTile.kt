package com.mapcontrol.launcher

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mapcontrol.R

/**
 * Ortalanmış kompozisyonlu durum kutucuğu (sürüş modu, bakım, hava kalitesi).
 * Üç kademe ([tier]):
 *  - dar (< 3 sütun, 2x2): üstte ortada ikon + başlık, ortada büyük değer, altında ortalı küçük detay;
 *  - orta (3 sütun): üstte başlık, altında iki dengeli sütun (değer | detay) ve aradaki ince ayraç;
 *  - geniş (>= 4 sütun): solda başlık + değer, sağda detay ve (varsa) segmentli gösterge.
 * Yalnızca eldeki veriyi gösterir, yeni veri uydurmaz.
 */
internal abstract class CenteredTile(
    protected val context: Context,
    @DrawableRes iconRes: Int,
    @StringRes private val labelRes: Int,
) : SizedWidget, Themeable, StripSource {

    // --- Şerit (Sahne) özeti: değer, detay, ikon ve dilimler bağlanırken burada da tutulur ---
    override var onStripChanged: (() -> Unit)? = null
    private var stripIcon = iconRes
    private var stripValue = ""
    private var stripTone = StripTone.MUTED
    private var stripDetail: String? = null
    private var stripLevel = 0
    private var stripLevels = 0

    override fun stripState(): StripState = StripState(
        title = context.getString(labelRes),
        value = stripValue,
        icon = stripIcon,
        tone = stripTone,
        level = stripLevel,
        levels = stripLevels,
        detail = stripDetail,
    )

    /** Kutucukta dokunma işi varsa (kök tıklanabilirse) aynısı. */
    override fun stripTap(part: Int) { root.performClick() }

    private fun notifyStrip() { onStripChanged?.invoke() }

    /** Tema bağlı atamalar (kurucuda bir kez, [applyTheme]'de yeniden); bkz. [ThemeBindings]. */
    protected val theme = ThemeBindings()

    protected val headerIcon: ImageView = icon(context, iconRes, HEADER_ICON_DP).also { v -> theme.bind { v.setTint(LauncherTheme.TEXT_SECONDARY) } }
    private val labelText: TextView = text(context, LauncherTheme.LABEL_SP).apply {
        setText(labelRes)
        theme.bind { setTextColor(LauncherTheme.TEXT_SECONDARY) }
    }
    private val heroIcon: ImageView = icon(context, iconRes, HERO_ICON_DP).apply {
        visibility = View.GONE
        theme.bind { setTint(LauncherTheme.TEXT_SECONDARY) }
    }
    protected val valueText: TextView = text(context, VALUE_SP, medium = true).apply {
        theme.bind { setTextColor(LauncherTheme.TEXT) }
    }
    protected val detailText: TextView = text(context, LauncherTheme.BODY_SP, maxLines = 2).apply {
        visibility = View.GONE
        theme.bind { setTextColor(LauncherTheme.TEXT_SECONDARY) }
    }
    private val segmentRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        visibility = View.GONE
    }
    private var segmentCount = 0
    private val divider = View(context).apply {
        theme.bind { setBackgroundColor(LauncherTheme.TRACK) }
        visibility = View.GONE
    }

    private val headerGap = gap(context, 10)

    private val headerRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        addView(headerIcon)
        addView(headerGap)
        addView(labelText)
    }

    private val root: LinearLayout = card(context, LinearLayout.VERTICAL, paddingDp = PADDING_DP).apply {
        minimumHeight = context.dp(LauncherTheme.TOUCH_MIN_DP)
        theme.bind { background = cardSurface(context) }
    }

    override val view: View get() = root

    /**
     * Tema değişti: sabit renkler varsayılana döner ([theme]), alt sınıf durumu sıfırlanır ([resetThemeState]) ve
     * değer, ikon, detay, dilimler yeni renklerle [bindDetail] ile yeniden atanır; yeni kutucukla birebir aynı sonuç.
     */
    override fun applyTheme() {
        theme.reapply()
        resetThemeState()
        bindDetail()
    }

    /** [applyTheme]'de, yeniden bağlamadan önce çağrılır; verinin yeniden kuramadığı renk durumunu (ör. uyarı zemini) sıfırlar. */
    protected open fun resetThemeState() {}

    /** 0 = dar, 1 = orta (3 sütun), 2 = geniş (>= 4 sütun). */
    protected var tier = 0
        private set

    /** Orta ve geniş kademede ikinci bilgi gösterilir. */
    protected val wide: Boolean get() = tier >= 1

    private var heroShown = false
    private var detailSet: CharSequence? = null
    private var missingNote = false

    override fun applySpan(span: Span) {
        tier = when {
            span.w >= WIDE_MIN_COLS_ -> 2
            span.w >= MEDIUM_MIN_COLS -> 1
            else -> 0
        }
        rebuild()
        bindDetail()
    }

    /** Kademeye göre görünümleri yeniden dizer; yalnızca boyut değişince çalışır. */
    private fun rebuild() {
        root.removeAllViews()
        headerRow.removeAllViews()
        listOf(headerRow, headerIcon, headerGap, heroIcon, valueText, detailText, segmentRow, divider).forEach { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        headerRow.addView(headerIcon)
        headerRow.addView(headerGap)
        headerRow.addView(labelText)
        val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
        val match = LinearLayout.LayoutParams.MATCH_PARENT
        when (tier) {
            0 -> {
                root.orientation = LinearLayout.VERTICAL
                root.gravity = Gravity.CENTER_HORIZONTAL
                centre(valueText, VALUE_SP)
                centre(detailText, LauncherTheme.LABEL_SP)
                // 2x2 kutusu geniş ama alçak (~300x150px): büyük ikon ve değer yan yana, ortalı bir satır.
                val valueRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    addView(heroIcon, LinearLayout.LayoutParams(context.dp(HERO_ICON_DP), context.dp(HERO_ICON_DP)).apply {
                        marginEnd = context.dp(10)
                    })
                    addView(valueText, LinearLayout.LayoutParams(wrap, wrap))
                }
                val middle = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    addView(valueRow, LinearLayout.LayoutParams(wrap, wrap))
                    addView(detailText, LinearLayout.LayoutParams(match, wrap).apply { topMargin = context.dp(2) })
                }
                root.addView(headerRow, LinearLayout.LayoutParams(wrap, wrap))
                root.addView(middle, LinearLayout.LayoutParams(match, 0, 1f))
            }
            1 -> {
                root.orientation = LinearLayout.VERTICAL
                root.gravity = Gravity.CENTER_HORIZONTAL
                centre(valueText, VALUE_SP)
                centre(detailText, VALUE_SP)
                val middle = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(valueText, LinearLayout.LayoutParams(0, wrap, 1f))
                    addView(divider, LinearLayout.LayoutParams(context.dp(1), context.dp(DIVIDER_H_DP)))
                    addView(detailText, LinearLayout.LayoutParams(0, wrap, 1f))
                }
                root.addView(headerRow, LinearLayout.LayoutParams(wrap, wrap))
                root.addView(middle, LinearLayout.LayoutParams(match, 0, 1f))
            }
            else -> {
                root.orientation = LinearLayout.HORIZONTAL
                root.gravity = Gravity.CENTER_VERTICAL
                start(valueText, VALUE_SP)
                detailText.textSize = LauncherTheme.BODY_SP
                detailText.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                detailText.textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                val left = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(headerRow, LinearLayout.LayoutParams(wrap, wrap))
                    addView(gap(context, 4, vertical = true))
                    addView(valueText)
                }
                val right = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    addView(detailText, LinearLayout.LayoutParams(match, wrap))
                    addView(segmentRow, LinearLayout.LayoutParams(match, context.dp(SEGMENT_H_DP)).apply { topMargin = context.dp(8) })
                }
                headerRow.gravity = Gravity.CENTER_VERTICAL
                root.addView(left, LinearLayout.LayoutParams(0, wrap, 1f))
                root.addView(right, LinearLayout.LayoutParams(0, wrap, 1.3f).apply { marginStart = context.dp(16) })
            }
        }
        if (tier != 2) headerRow.gravity = Gravity.CENTER
        applyDetail()
    }

    private fun centre(tv: TextView, sp: Float) {
        tv.textSize = sp
        tv.gravity = Gravity.CENTER
        tv.textAlignment = View.TEXT_ALIGNMENT_CENTER
    }

    private fun start(tv: TextView, sp: Float) {
        tv.textSize = sp
        tv.gravity = Gravity.START
        tv.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    }

    /** Detay ve ayraç görünürlüğü; boşsa gizlenir, dar kademede küçük satır olarak kalır. */
    private fun applyDetail() {
        val has = !detailSet.isNullOrEmpty()
        detailText.text = detailSet
        // "Araç verisi bekleniyor" her kademede aynı boyutta; aksi halde kademenin kendi detay boyutu.
        detailText.textSize = if (missingNote) LauncherTheme.WAITING_SP else when (tier) {
            0 -> LauncherTheme.LABEL_SP
            1 -> VALUE_SP
            else -> LauncherTheme.BODY_SP
        }
        // Dar kademede detay ("Bakıma" gibi) da gösterilir; orta/geniş kademede yalnızca doluysa.
        detailText.visibility = if (has) View.VISIBLE else View.GONE
        divider.visibility = if (tier == 1 && has) View.VISIBLE else View.GONE
        segmentRow.visibility = if (tier == 2 && segmentCount > 0) View.VISIBLE else View.GONE
        (detailText.parent as? View)?.visibility =
            if (tier == 2 && !has && segmentRow.visibility != View.VISIBLE) View.GONE else View.VISIBLE
    }

    /** İkinci satırı günceller; dar kademede değerin altında, orta kademede ikinci sütunda. */
    protected fun showDetail(detail: CharSequence?, color: Int = LauncherTheme.TEXT_SECONDARY) {
        missingNote = false
        detailSet = detail
        detailText.setTextColor(color)
        applyDetail()
        stripDetail = detail?.toString()
        notifyStrip()
    }

    /**
     * Geniş kademede detayın altına [total] dilimli gösterge çizer; ilk [filled] dilim [color], gerisi iz rengi.
     * Dar/orta kademede ya da [total] 0 ise gizlenir.
     */
    protected fun showSegments(filled: Int, total: Int, color: Int) {
        if (total <= 0) {
            segmentCount = 0
            segmentRow.removeAllViews()
            applyDetail()
            if (stripLevels != 0) { stripLevel = 0; stripLevels = 0; notifyStrip() }
            return
        }
        if (segmentRow.childCount != total) {
            segmentRow.removeAllViews()
            repeat(total) { i ->
                segmentRow.addView(View(context), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (i > 0) marginStart = context.dp(SEGMENT_GAP_DP)
                })
            }
        }
        segmentCount = total
        for (i in 0 until total) {
            segmentRow.getChildAt(i).background = rounded(context, if (i < filled) color else LauncherTheme.TRACK, SEGMENT_H_DP / 2f)
        }
        applyDetail()
        stripLevel = filled
        stripLevels = total
        notifyStrip()
    }

    protected fun setIcon(@DrawableRes res: Int, tint: Int) {
        headerIcon.setImageResource(res)
        headerIcon.imageTintList = ColorStateList.valueOf(tint)
        stripIcon = res
    }

    /** Dar kademede büyük, ortalı ikon (sürüş modu gibi); verilince küçük başlık ikonu gizlenir. [res] null ise kaldırılır. */
    protected fun setHeroIcon(@DrawableRes res: Int?, tint: Int = LauncherTheme.TEXT_SECONDARY) {
        heroShown = res != null
        if (res != null) {
            heroIcon.setImageResource(res)
            heroIcon.imageTintList = ColorStateList.valueOf(tint)
        }
        heroIcon.visibility = if (heroShown && tier == 0) View.VISIBLE else View.GONE
        headerIcon.visibility = if (heroShown && tier == 0) View.GONE else View.VISIBLE
        headerGap.visibility = headerIcon.visibility
    }

    protected fun setValue(value: CharSequence, color: Int, animate: Boolean = true) {
        valueText.setTextColor(color)
        valueText.setTextSmooth(unitStyled(value), animate)
        root.contentDescription = value
        stripValue = value.toString()
        stripTone = stripToneFor(color)
        notifyStrip()
    }

    /** Veri yok: değer yerine soluk bir çizgi, nedeni küçük satırda. */
    protected fun showMissing(@StringRes reason: Int = R.string.launcher_waiting_data) {
        setHeroIcon(null)
        setValue(context.getString(R.string.launcher_unavailable), LauncherTheme.TEXT_SECONDARY)
        showDetail(context.getString(reason))
        missingNote = true
        applyDetail()
        showSegments(0, 0, 0)
        root.contentDescription = context.getString(reason)
    }

    /** Boyut değişince ikinci satırı mevcut duruma göre yeniden kurar. */
    protected abstract fun bindDetail()

    private companion object {
        const val VALUE_SP = 40f
        const val HEADER_ICON_DP = 28
        const val HERO_ICON_DP = 40
        const val PADDING_DP = 16
        const val MEDIUM_MIN_COLS = 3
        const val WIDE_MIN_COLS_ = 4
        const val SEGMENT_H_DP = 8
        const val SEGMENT_GAP_DP = 6
        const val DIVIDER_H_DP = 56
    }
}

/** [CenteredTile] + [TripSource] dinleyicisi (bakım, hava kalitesi). */
internal abstract class CenteredTripTile(
    context: Context,
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
) : CenteredTile(context, iconRes, labelRes) {

    protected var data = TripData()
        private set
    private val listener = TripSource.Listener {
        data = it
        bind()
    }

    override fun onVisible() = TripSource.add(context, listener)

    override fun onHidden() = TripSource.remove(listener)

    override fun release() = TripSource.remove(listener)

    override fun bindDetail() = bind()

    protected abstract fun bind()
}
