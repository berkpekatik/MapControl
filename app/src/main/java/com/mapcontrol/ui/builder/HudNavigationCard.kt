package com.mapcontrol.ui.builder

import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.mapcontrol.R
import com.mapcontrol.media.LauncherMediaController
import com.mapcontrol.nav.HudNavigationManager
import com.mapcontrol.nav.YandexClusterNavCoordinator
import com.mapcontrol.ui.theme.UiStyles

/** Yansıtma sekmesindeki üretici HUD yönlendirme ayarları. */
object HudNavigationCard {
    @JvmStatic
    fun create(context: Context): LinearLayout {
        val hud = HudNavigationManager.get(context)
        val card = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun text(resource: Int, title: Boolean = false): TextView = TextView(context).apply {
            setText(resource)
            UiStyles.setTextSize(this, if (title) R.dimen.text_size_medium else R.dimen.text_size_small)
            setTextColor(UiStyles.color(context, if (title) R.color.textPrimary else R.color.textHint))
            setPadding(0, 8, 0, 8)
            card.addView(this)
        }
        text(R.string.hud_nav_title, title = true)
        text(R.string.hud_nav_description)
        val enabled = Switch(context).apply {
            setText(R.string.hud_nav_enable)
            setTextColor(UiStyles.color(context, R.color.textPrimary))
            isChecked = hud.isEnabled()
            card.addView(this)
        }
        text(R.string.hud_nav_provider)
        val source = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                listOf(context.getString(R.string.hud_nav_auto_provider), "Google Maps", "Yandex Maps / Navigator"))
            card.addView(this)
        }
        val providers = listOf("auto", "google", "yandex")
        val status = text(R.string.hud_nav_off)
        var binding = false
        val watcher = Runnable {
            binding = true
            enabled.isChecked = hud.isEnabled()
            source.setSelection(providers.indexOf(hud.provider()).coerceAtLeast(0))
            status.text = hud.status()
            binding = false
        }
        fun button(resource: Int, action: () -> Unit) = Button(context).apply {
            setText(resource)
            UiStyles.styleOemButton(this, UiStyles.color(context, R.color.buttonPrimary))
            setTextColor(UiStyles.color(context, R.color.textPrimary))
            setOnClickListener { action() }
            card.addView(this, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        enabled.setOnCheckedChangeListener { _, value ->
            if (!binding) {
                hud.setEnabled(value)
                if (value && !LauncherMediaController.isNotificationAccessEnabled(context)) {
                    LauncherMediaController(context).openNotificationAccessSettings()
                }
            }
        }
        source.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!binding && providers[position] != hud.provider()) hud.setProvider(providers[position])
            }
        }
        button(R.string.hud_nav_permissions) { LauncherMediaController(context).openNotificationAccessSettings() }
        text(R.string.hud_nav_yandex_access)
        button(R.string.hud_nav_accessibility) { YandexClusterNavCoordinator.openAccessibilitySettings(context) }
        button(R.string.hud_nav_test) {
            hud.showTest()
            if (!hud.isEnabled()) Toast.makeText(context, R.string.hud_nav_enable_first, Toast.LENGTH_SHORT).show()
        }
        card.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                hud.refresh()
                hud.addWatcher(watcher)
            }
            override fun onViewDetachedFromWindow(view: View) { hud.removeWatcher(watcher) }
        })
        watcher.run()
        return card
    }
}
