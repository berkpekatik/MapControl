package com.mapcontrol.ui.activity

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.mapcontrol.R
import com.mapcontrol.launcher.DriveMapPrefs
import com.mapcontrol.launcher.DrivePolicy
import com.mapcontrol.launcher.DriveScreen
import com.mapcontrol.launcher.FreeformCaption
import com.mapcontrol.launcher.GlassDrawable
import com.mapcontrol.launcher.LauncherTheme
import com.mapcontrol.launcher.MapApp
import com.mapcontrol.launcher.MapAppPrefs
import com.mapcontrol.launcher.Sahne
import com.mapcontrol.launcher.SahneGround
import com.mapcontrol.launcher.ThemeMode
import com.mapcontrol.launcher.dp
import com.mapcontrol.launcher.pressFeedback
import com.mapcontrol.launcher.sahneText
import com.mapcontrol.launcher.setTint
import com.mapcontrol.util.SplitScreenLauncher

/**
 * Sürüş ekranının harita yarısı, harita uygulaması yokken ([DriveScreen]): "+ Harita ekle". Kurulu harita seçenekleri ([MapApp];
 * Google / Yandex) ve `geo:` açabilen diğer navigasyon uygulamaları büyük kartlar olarak listelenir. Seçim kaydedilir (kartın seçimi ya
 * da "diğer" [DriveMapPrefs]) ve seçilen uygulama bu pencerenin yerinde (aynı sınırlar) açılır, seçici kapanır.
 *
 * Yalnız FrostNav derlemesinde (bölünmüş ekran başlatıcısı burada); serbest pencere başlığı her zaman gizlenir.
 */
class DriveMapPickerActivity : Activity() {

    private lateinit var root: FrameLayout
    private var launching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LauncherTheme.applyFrom(resources.configuration, ThemeMode.read(this))
        window.setBackgroundDrawable(ColorDrawable(Sahne.GROUND))
        root = FrameLayout(this).apply { background = SahneGround() }
        setContentView(root)
        window.decorView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> v.post { if (!isFinishing) FreeformCaption.apply(this, force = true) } }
        build()
    }

    override fun onResume() {
        super.onResume()
        // Uygulama kurulup geri dönülünce liste tazelenir.
        if (!launching) build()
    }

    private fun build() {
        root.removeAllViews()
        val installed = MapAppPrefs.installedSet(this)
        val choices = DrivePolicy.pickerChoices(
            MapApp.ORDER.map { it.packageName }.filter { it in installed },
            DriveMapPrefs.geoApps(this),
            packageName,
        )
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // Cam gölgeleri kırpılmasın.
            clipChildren = false
            clipToPadding = false
            val p = dp(32)
            setPadding(p, p, p, p)
        }
        // Düz simge (cam düğme gibi görünüp dokunulamayan disk değil): dokunulacak olan aşağıdaki uygulama kartları.
        column.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_sahne_plus)
            setTint(Sahne.INK_3)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        column.addView(sahneText(this, 34f, Sahne.INK, 500).apply {
            setText(R.string.launcher_drive_picker_title)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(20) })
        column.addView(sahneText(this, 20f, Sahne.INK_3, 500).apply {
            setText(if (choices.isEmpty()) R.string.launcher_drive_picker_none else R.string.launcher_drive_picker_sub)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(8) })
        if (choices.isNotEmpty()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                // Kart gölgeleri kırpılmasın (GlassDrawable: ebeveyn clipChildren=false; yoksa altta gri köşeler).
                clipChildren = false
                clipToPadding = false
            }
            choices.forEachIndexed { i, pkg ->
                row.addView(card(pkg), LinearLayout.LayoutParams(dp(CARD_DP), dp(CARD_DP)).apply { if (i > 0) marginStart = dp(16) })
            }
            column.addView(android.widget.HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                clipChildren = false
                clipToPadding = false
                setPadding(dp(16), dp(12), dp(16), dp(20))
                addView(row)
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(16) })
        }
        root.clipChildren = false
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(FrameLayout(this@DriveMapPickerActivity).apply {
                clipChildren = false
                clipToPadding = false
                addView(column, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
            })
        }, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    /** Uygulama kartı: simge + ad, en az 64 dp dokunma alanının çok üstünde (sürüşte de rahat). */
    private fun card(pkg: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = GlassDrawable(this@DriveMapPickerActivity, 24f, shadow = 0.6f)
        val label = MapApp.fromPackage(pkg)?.fullName ?: appLabel(pkg)
        addView(ImageView(this@DriveMapPickerActivity).apply {
            setImageDrawable(try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null })
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(72), dp(72)))
        addView(sahneText(this@DriveMapPickerActivity, 18f, Sahne.INK, 500).apply {
            text = label
            gravity = Gravity.CENTER
            maxLines = 2
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12); marginStart = dp(8); marginEnd = dp(8) })
        contentDescription = label
        isClickable = true
        isFocusable = true
        setOnClickListener { choose(pkg) }
        pressFeedback()
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    /** Seçim kaydedilir; uygulama bu pencerenin sınırlarında açılır, seçici kapanır. */
    private fun choose(pkg: String) {
        if (launching) return
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return
        launching = true
        DriveMapPrefs.choose(this, pkg)
        DriveScreen.onMapChosen(pkg)
        SplitScreenLauncher.launchInto(this, pkg, intent, Rect(windowManager.currentWindowMetrics.bounds)) { result ->
            Log.i(TAG, "picked -> $result")
            launching = false
            if (result == SplitScreenLauncher.Result.STARTED) finishAndRemoveTask()
        }
    }

    private companion object {
        const val TAG = "DriveMapPicker"
        const val CARD_DP = 200
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
