package com.mapcontrol.ui.menu

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.mapcontrol.R
import com.mapcontrol.ui.theme.UiStyles

/**
 * Kurulum anketi: "Neleri kullanmak istersin?" → (launcher seçildiyse) launcher sorusu → özet.
 * Yalnız arayüzü kurar; sonuç [Listener]'a gider (kaydetmek ve launcher modunu açmak çağıranın işi).
 */
class MenuSetupWizard private constructor(
    private val context: Context,
    initial: Set<MenuCard>?,
    private val launcherOn: Boolean,
    private val firstRun: Boolean,
    private val listener: Listener,
) {

    interface Listener {
        /** [cards] null: hepsi. [launcher] null: launcher sorusu sorulmadı (kart seçilmedi). */
        fun onFinished(cards: Set<@JvmSuppressWildcards MenuCard>?, launcher: Boolean?)

        /** İlk kurulumda "Atla", sonradan açılışta "Vazgeç". */
        fun onSkipped()
    }

    private val selected: MutableSet<MenuCard> = LinkedHashSet(initial ?: MenuCard.values().toList())
    private var launcherAnswer: Boolean = launcherOn
    private val root = FrameLayout(context)
    private val stage = FrameLayout(context)
    private val cardViews = LinkedHashMap<MenuCard, View>()
    private var allToggle: TextView? = null
    private val accent = UiStyles.color(context, R.color.buttonPrimary)

    private val portrait: Boolean
        get() = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    private fun build(): View {
        UiStyles.setRootBackground(root)
        root.isClickable = true
        root.addView(stage, FrameLayout.LayoutParams(-1, -1))
        showStep(chooseStep(), forward = true)
        return root
    }

    // ---- Adımlar ----

    private fun chooseStep(): View {
        cardViews.clear()
        val column = column()
        column.addView(title(R.string.menu_setup_title), lp(top = 0))
        column.addView(subtitle(R.string.menu_setup_subtitle), lp(top = 8))

        val toggle = pillText(context.getString(R.string.menu_setup_select_all), filled = false)
        toggle.setOnClickListener {
            if (selected.size == MenuCard.values().size) selected.clear() else selected.addAll(MenuCard.values())
            refreshCards()
        }
        allToggle = toggle
        column.addView(toggle, lp(top = 16))

        val columns = if (portrait) 2 else 4
        val grid = GridLayout(context).apply { columnCount = columns }
        MenuCard.values().forEachIndexed { i, card ->
            val view = cardView(card)
            cardViews[card] = view
            val params = GridLayout.LayoutParams(
                GridLayout.spec(i / columns), GridLayout.spec(i % columns, 1f),
            ).apply {
                width = 0
                setMargins(dp(8), dp(8), dp(8), dp(8))
            }
            grid.addView(view, params)
        }
        column.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        val next = pillText(context.getString(R.string.menu_setup_continue), filled = true)
        next.setOnClickListener {
            if (MenuCard.LAUNCHER in selected) showStep(launcherStep(), forward = true) else showStep(doneStep(), forward = true)
        }
        column.addView(buttonRow(next, context.getString(if (firstRun) R.string.menu_setup_skip else R.string.menu_setup_cancel)) {
            listener.onSkipped()
        }, lp(top = 24))
        refreshCards()
        return scroll(column)
    }

    private fun launcherStep(): View {
        val column = column()
        column.addView(icon(R.drawable.ic_mdi_view_grid, 56), lp(top = 0))
        column.addView(title(R.string.menu_setup_launcher_title), lp(top = 16))
        column.addView(subtitle(R.string.menu_setup_launcher_body), lp(top = 8))

        val row = LinearLayout(context).apply { orientation = if (portrait) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        lateinit var yes: View
        lateinit var no: View
        fun refresh() {
            styleCard(yes, launcherAnswer)
            styleCard(no, !launcherAnswer)
        }
        yes = optionCard(R.string.menu_setup_launcher_yes, R.string.menu_setup_launcher_yes_desc) { launcherAnswer = true; refresh() }
        no = optionCard(R.string.menu_setup_launcher_no, R.string.menu_setup_launcher_no_desc) { launcherAnswer = false; refresh() }
        for (v in listOf(yes, no)) {
            row.addView(v, LinearLayout.LayoutParams(if (portrait) -1 else dp(320), -2).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) })
        }
        refresh()
        column.addView(row, lp(top = 20))

        val next = pillText(context.getString(R.string.menu_setup_continue), filled = true)
        next.setOnClickListener { showStep(doneStep(), forward = true) }
        column.addView(buttonRow(next, context.getString(R.string.menu_setup_back)) {
            showStep(chooseStep(), forward = false)
        }, lp(top = 24))
        return scroll(column)
    }

    private fun doneStep(): View {
        val column = column()
        val check = icon(R.drawable.ic_mdi_check, 40).apply {
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accent) }
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        column.addView(check, LinearLayout.LayoutParams(dp(68), dp(68)))
        column.addView(title(R.string.menu_setup_done_title), lp(top = 20))

        val names = if (selected.isEmpty()) {
            context.getString(R.string.menu_setup_done_none)
        } else {
            selected.joinToString(" · ") { context.getString(titleRes(it)) }
        }
        column.addView(text(names, 16f, R.color.textPrimary).apply { UiStyles.setFontStyle(this, Typeface.BOLD) }, lp(top = 12))
        column.addView(subtitle(R.string.menu_setup_done_body), lp(top = 8))

        val start = pillText(context.getString(R.string.menu_setup_start), filled = true)
        start.setOnClickListener {
            val launcher = if (MenuCard.LAUNCHER in selected) launcherAnswer else null
            listener.onFinished(MenuProfile.normalize(LinkedHashSet(selected)), launcher)
        }
        column.addView(buttonRow(start, context.getString(R.string.menu_setup_back)) {
            showStep(if (MenuCard.LAUNCHER in selected) launcherStep() else chooseStep(), forward = false)
        }, lp(top = 28))
        return scroll(column)
    }

    /** Eski adım yana kayıp söner, yenisi karşıdan gelir; içindekiler sırayla belirir. */
    private fun showStep(next: View, forward: Boolean) {
        val shift = dp(48).toFloat() * if (forward) 1 else -1
        val old = if (stage.childCount > 0) stage.getChildAt(0) else null
        if (old != null) {
            old.isEnabled = false
            old.animate().alpha(0f).translationX(-shift).setDuration(180).withEndAction { stage.removeView(old) }.start()
        }
        stage.addView(next, FrameLayout.LayoutParams(-1, -1))
        next.alpha = 0f
        next.translationX = shift
        next.animate().alpha(1f).translationX(0f).setStartDelay(if (old != null) 120 else 0)
            .setDuration(360).setInterpolator(DecelerateInterpolator()).start()
    }

    // ---- Kartlar ----

    private fun cardView(card: MenuCard): View {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            minimumHeight = dp(132)
            isClickable = true
            isFocusable = true
        }
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(icon(iconRes(card), 30), LinearLayout.LayoutParams(dp(30), dp(30)))
        top.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        if (card.beta) {
            top.addView(text(context.getString(R.string.menu_setup_beta), 11f, R.color.statusWarning).apply {
                UiStyles.setFontStyle(this, Typeface.BOLD)
                setPadding(dp(8), dp(2), dp(8), dp(2))
                background = rounded(ColorUtils.setAlphaComponent(UiStyles.color(context, R.color.statusWarning), 36), 0, 0, 100f)
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
        }
        val mark = ImageView(context).apply { setImageResource(R.drawable.ic_mdi_check); setPadding(dp(4), dp(4), dp(4), dp(4)) }
        top.addView(mark, LinearLayout.LayoutParams(dp(26), dp(26)))
        box.addView(top)
        box.addView(text(context.getString(titleRes(card)), 17f, R.color.textPrimary).apply {
            gravity = Gravity.START
            UiStyles.setFontStyle(this, Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        box.addView(text(context.getString(descRes(card)), 13f, R.color.textSecondaryCool).apply { gravity = Gravity.START },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        box.tag = mark
        box.setOnClickListener {
            if (!selected.remove(card)) selected.add(card)
            refreshCards()
            box.animate().scaleX(0.97f).scaleY(0.97f).setDuration(70).withEndAction {
                box.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }.start()
        }
        return box
    }

    private fun optionCard(titleRes: Int, descRes: Int, onClick: () -> Unit): View {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            isClickable = true
            setOnClickListener { onClick() }
        }
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(text(context.getString(titleRes), 17f, R.color.textPrimary).apply {
            gravity = Gravity.START
            UiStyles.setFontStyle(this, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val mark = ImageView(context).apply { setImageResource(R.drawable.ic_mdi_check); setPadding(dp(4), dp(4), dp(4), dp(4)) }
        top.addView(mark, LinearLayout.LayoutParams(dp(26), dp(26)))
        box.addView(top)
        box.addView(text(context.getString(descRes), 13f, R.color.textSecondaryCool).apply { gravity = Gravity.START },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        box.tag = mark
        return box
    }

    /** Seçili kart: vurgu çerçevesi, hafif renkli zemin ve dolu onay işareti. */
    private fun styleCard(card: View, on: Boolean) {
        val inner = UiStyles.color(context, R.color.surfaceCardInner)
        card.background = if (on) {
            rounded(ColorUtils.blendARGB(inner, accent, 0.12f), accent, dp(2), dp(18).toFloat())
        } else {
            rounded(inner, UiStyles.color(context, R.color.outlineSubtle), dp(1), dp(18).toFloat())
        }
        (card.tag as? ImageView)?.apply {
            imageTintList = ColorStateList.valueOf(if (on) Color.WHITE else Color.TRANSPARENT)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (on) setColor(accent) else setStroke(dp(2), UiStyles.color(context, R.color.textSecondaryCool))
            }
        }
    }

    private fun refreshCards() {
        cardViews.forEach { (card, view) -> styleCard(view, card in selected) }
        allToggle?.text = context.getString(
            if (selected.size == MenuCard.values().size) R.string.menu_setup_select_none else R.string.menu_setup_select_all,
        )
    }

    // ---- Küçük yardımcılar ----

    private fun column() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(32), dp(32), dp(32), dp(32))
    }

    private fun scroll(content: View): View = ScrollView(context).apply {
        isFillViewport = true
        val holder = FrameLayout(context)
        holder.addView(content, FrameLayout.LayoutParams(
            if (portrait) -1 else minOf(dp(1180), context.resources.displayMetrics.widthPixels), -2, Gravity.CENTER,
        ))
        addView(holder, ViewGroup.LayoutParams(-1, -2))
    }

    private fun buttonRow(primary: TextView, secondaryLabel: String, onSecondary: () -> Unit): View {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val secondary = text(secondaryLabel, 16f, R.color.textSecondaryCool).apply {
            UiStyles.setFontStyle(this, Typeface.BOLD)
            setPadding(dp(28), dp(14), dp(28), dp(14))
            isClickable = true
            setOnClickListener { onSecondary() }
        }
        row.addView(secondary)
        row.addView(primary, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(16) })
        return row
    }

    private fun pillText(label: String, filled: Boolean) = text(label, if (filled) 17f else 14f, R.color.textPrimary).apply {
        UiStyles.setFontStyle(this, Typeface.BOLD)
        isClickable = true
        if (filled) {
            setTextColor(Color.WHITE)
            setPadding(dp(56), dp(14), dp(56), dp(14))
            background = rounded(accent, 0, 0, 1000f)
        } else {
            setTextColor(accent)
            setPadding(dp(20), dp(8), dp(20), dp(8))
            background = rounded(Color.TRANSPARENT, accent, dp(1), 1000f)
        }
    }

    private fun title(res: Int) = text(context.getString(res), 28f, R.color.textPrimary).apply {
        UiStyles.setFontStyle(this, Typeface.BOLD)
    }

    private fun subtitle(res: Int) = text(context.getString(res), 15f, R.color.textSecondaryCool).apply {
        maxWidth = dp(720)
        setLineSpacing(0f, 1.15f)
    }

    private fun text(value: String, sp: Float, colorRes: Int) = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(UiStyles.color(context, colorRes))
        gravity = Gravity.CENTER
    }

    private fun icon(res: Int, sizeDp: Int) = ImageView(context).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(accent)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    private fun rounded(fill: Int, stroke: Int, strokeWidth: Int, radius: Float) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius
        if (strokeWidth > 0) setStroke(strokeWidth, stroke)
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(-2, -2).apply {
        topMargin = dp(top)
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun dp(v: Int): Int = Math.round(v * context.resources.displayMetrics.density)

    companion object {
        /**
         * @param initial şu anki seçim (null: hepsi)
         * @param launcherOn launcher sorusunun ön seçimi
         * @param firstRun ilk kurulum: ikincil düğme "Atla", sonradan açılış: "Vazgeç"
         */
        @JvmStatic
        fun create(context: Context, initial: Set<MenuCard>?, launcherOn: Boolean, firstRun: Boolean, listener: Listener): View =
            MenuSetupWizard(context, initial, launcherOn, firstRun, listener).build()

        @JvmStatic
        fun titleRes(card: MenuCard): Int = when (card) {
            MenuCard.LAUNCHER -> R.string.menu_card_launcher
            MenuCard.MAP -> R.string.menu_card_map
            MenuCard.SOUND -> R.string.menu_card_sound
            MenuCard.WELCOME -> R.string.menu_card_welcome
            MenuCard.AUTOMATION -> R.string.menu_card_automation
            MenuCard.MEMORY -> R.string.menu_card_memory
            MenuCard.CAMERA -> R.string.menu_card_camera
            MenuCard.TECH -> R.string.menu_card_tech
        }

        private fun descRes(card: MenuCard): Int = when (card) {
            MenuCard.LAUNCHER -> R.string.menu_card_launcher_desc
            MenuCard.MAP -> R.string.menu_card_map_desc
            MenuCard.SOUND -> R.string.menu_card_sound_desc
            MenuCard.WELCOME -> R.string.menu_card_welcome_desc
            MenuCard.AUTOMATION -> R.string.menu_card_automation_desc
            MenuCard.MEMORY -> R.string.menu_card_memory_desc
            MenuCard.CAMERA -> R.string.menu_card_camera_desc
            MenuCard.TECH -> R.string.menu_card_tech_desc
        }

        private fun iconRes(card: MenuCard): Int = when (card) {
            MenuCard.LAUNCHER -> R.drawable.ic_mdi_view_grid
            MenuCard.MAP -> R.drawable.ic_mdi_map
            MenuCard.SOUND -> R.drawable.ic_mdi_equalizer
            MenuCard.WELCOME -> R.drawable.ic_mdi_volume_high
            MenuCard.AUTOMATION -> R.drawable.ic_mdi_thermometer
            MenuCard.MEMORY -> R.drawable.ic_mdi_car
            MenuCard.CAMERA -> R.drawable.ic_mdi_camera
            MenuCard.TECH -> R.drawable.ic_mdi_speedometer
        }
    }
}
