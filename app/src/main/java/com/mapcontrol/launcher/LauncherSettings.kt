package com.mapcontrol.launcher

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mapcontrol.R
import com.mapcontrol.util.AppLaunchHelper

/** Launcher görünümü: sistemi izle ya da sabit gündüz/gece. */
enum class ThemeMode(val key: String, @StringRes val label: Int) {
    AUTO("auto", R.string.launcher_theme_auto),
    DAY("day", R.string.launcher_theme_day),
    NIGHT("night", R.string.launcher_theme_night);

    companion object {
        private const val PREFS_NAME = "launcher_settings"
        private const val KEY = "theme_mode"

        fun read(context: Context): ThemeMode {
            val key = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(Profiles.key(context, KEY), null)
            return values().firstOrNull { it.key == key } ?: AUTO
        }

        /** Kayıtlı moddan sonraki mod (tıklama anında okunur, hızlı ikinci dokunuş da ilerletir); yazar ve döndürür. */
        fun next(context: Context): ThemeMode {
            val all = values()
            val n = all[(read(context).ordinal + 1) % all.size]
            write(context, n)
            return n
        }

        fun write(context: Context, mode: ThemeMode) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(Profiles.key(context, KEY), mode.key).apply()
        }
    }
}

/**
 * "Ayarlar" paneli: launcher kartlarıyla aynı dünyada, sistem diyaloğu değil. Üstte tema seçimi
 * (Otomatik | Gündüz | Gece), altında ana ekranı düzenleme, aracın kendi ayarları ve (tam sürümde)
 * MapControl ayarları. Satırlar en az 80dp: sürücü yanlış satıra basmasın.
 * Tema değişince etkinlik yeniden kurulur; renkler yalnızca kurulumda okunur.
 */
class LauncherSettings(private val activity: Activity, private val onEditHome: () -> Unit) {

    private val launcher = LauncherIo.serial()
    private var dialog: Dialog? = null
    private var dataCheck: DataCheckSheet? = null

    /**
     * [appearanceOnly]: "Görünüm" paneli (düzenleme modundaki düğme ve MapControl'daki bağlantı); yalnızca tema, sürüş rengi,
     * alt çubuk, otomatik sürüş düzeni ve araç rengi. Tam panel yalnızca [LauncherEdition.HAS_SETTINGS] false iken başlık düğmesinde açılır.
     */
    fun show(appearanceOnly: Boolean = false) {
        if (dialog?.isShowing == true) return
        val title = if (appearanceOnly) R.string.launcher_appearance else R.string.launcher_settings
        dialog = showLauncherSheet(activity, title) { d -> addScrollable(d, appearanceOnly) }.apply {
            setOnDismissListener { dialog = null }
        }
    }

    fun release() {
        dialog?.dismiss()
        dataCheck?.release()
    }

    /** Satırlar araç ekranının yüksekliğini aşabilir (tema + alt çubuk + 4-5 satır): panel kaydırılır. */
    private fun LinearLayout.addScrollable(d: Dialog, appearanceOnly: Boolean) {
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, activity.dp(16)) }
        content.buildPanel(d, appearanceOnly)
        val maxH = activity.resources.displayMetrics.heightPixels - activity.dp(SCROLL_RESERVED_DP)
        // Yükseklik = min(içerik, kullanılabilir): kısa panel kaydırmadan sığar, uzun panel kaydırılır.
        val scroll = object : ScrollView(activity) {
            override fun onMeasure(widthSpec: Int, heightSpec: Int) {
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST))
            }
        }.apply { addView(content) }
        addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun LinearLayout.buildPanel(d: Dialog, appearanceOnly: Boolean) {
        mapcontrolPackage()?.takeIf { !appearanceOnly }?.let { pkg ->
            addView(row(R.drawable.ic_mdi_open_in_new, R.string.launcher_open_mapcontrol, R.string.launcher_open_mapcontrol_hint) {
                d.dismiss()
                openMapControl(pkg)
            }, fullWidth())
            addView(gap(activity, 24, vertical = true))
        }
        addView(text(activity, LauncherTheme.LABEL_SP, LauncherTheme.TEXT_SECONDARY).apply { setText(R.string.launcher_theme) }, fullWidth())
        addView(gap(activity, 12, vertical = true))
        addView(themeSegments(d), fullWidth())

        addView(gap(activity, 12, vertical = true))
        addView(driveModeToggle(d), fullWidth())

        addView(gap(activity, 12, vertical = true))
        addView(sportModeToggle(d), fullWidth())

        // Yalnız spor modu açıkken anlamlı; kapalıyken satır gösterilmez (değişimde pencere zaten kapanıp yeniden kurulur).
        if (SportModePrefs.enabled(activity)) {
            addView(gap(activity, 12, vertical = true))
            addView(sportCloseOemDialogToggle(d), fullWidth())
        }

        addView(gap(activity, 12, vertical = true))
        addView(text(activity, LauncherTheme.LABEL_SP, LauncherTheme.TEXT_SECONDARY).apply { setText(R.string.launcher_bottom_bar) }, fullWidth())
        addView(gap(activity, 12, vertical = true))
        addView(bottomBarSegments(d), fullWidth())

        addView(gap(activity, 12, vertical = true))
        addView(autoDriveLayoutToggle(d), fullWidth())
        addView(gap(activity, 4, vertical = true))
        addView(text(activity, LauncherTheme.LABEL_SP - 2f, LauncherTheme.TEXT_SECONDARY, maxLines = 2).apply {
            setText(R.string.launcher_auto_drive_layout_hint)
        }, fullWidth())

        // Araç rengi (Sahne'nin ortasındaki araç): seçim hemen uygulanır, ekran yeniden kurulmaz.
        addView(gap(activity, 12, vertical = true))
        addView(text(activity, LauncherTheme.LABEL_SP, LauncherTheme.TEXT_SECONDARY).apply { setText(R.string.launcher_car_color) }, fullWidth())
        addView(gap(activity, 8, vertical = true))
        addView(CarColorSwatches.build(activity, LauncherTheme.ACCENT, LauncherTheme.TEXT, if (LauncherTheme.night) 0x40FFFFFF else 0x30000000,
            LauncherTheme.LABEL_SP, SWATCH_DP) { d.dismiss() }, fullWidth())

        if (appearanceOnly) return

        addView(gap(activity, 12, vertical = true))
        addView(sportAmbientToggle(d), fullWidth())

        // Alt çubuktan klima değişince OEM paneli (varsayılan Gizle). Yukarı kaydırma paneli her durumda açar.
        addView(gap(activity, 12, vertical = true))
        addView(onOffControl(R.string.launcher_climate_oem_panel, ClimatePanelPrefs.showOnChange(activity),
            R.string.launcher_toggle_show, R.string.launcher_toggle_hide) { value ->
            d.dismiss()
            ClimatePanelPrefs.setShowOnChange(activity, value)
        }, fullWidth())
        addView(gap(activity, 4, vertical = true))
        addView(text(activity, LauncherTheme.LABEL_SP - 2f, LauncherTheme.TEXT_SECONDARY, maxLines = 2).apply {
            setText(R.string.launcher_climate_oem_panel_hint)
        }, fullWidth())

        addView(gap(activity, 12, vertical = true))
        addView(text(activity, LauncherTheme.LABEL_SP, LauncherTheme.TEXT_SECONDARY).apply { setText(R.string.launcher_favorite_key) }, fullWidth())
        addView(gap(activity, 12, vertical = true))
        addView(favoriteKeySegments(d), fullWidth())

        // Ses (VR) tuşu: uzun basış bağlı telefonun asistanını açar; kısa basış aracın kendi sesli komutunda kalır.
        addView(gap(activity, 12, vertical = true))
        addView(onOffControl(R.string.launcher_phone_voice_key, PhonePrefs.assistantOnVoiceKey(activity)) { value ->
            d.dismiss()
            PhonePrefs.setAssistantOnVoiceKey(activity, value)
            PhoneSource.syncVoiceKey(activity)
        }, fullWidth())
        addView(gap(activity, 4, vertical = true))
        addView(text(activity, LauncherTheme.LABEL_SP - 2f, LauncherTheme.TEXT_SECONDARY, maxLines = 2).apply {
            setText(R.string.launcher_phone_voice_key_hint)
        }, fullWidth())

        // Telefon kartında "Sesli asistan" düğmesi: kapalıyken son arama ve favoriler büyür (açık paneller hemen değişir).
        addView(gap(activity, 12, vertical = true))
        addView(onOffControl(R.string.launcher_phone_assistant_button, PhonePrefs.showAssistant(activity)) { value ->
            d.dismiss()
            PhonePrefs.setShowAssistant(activity, value)
        }, fullWidth())
        addView(gap(activity, 4, vertical = true))
        addView(text(activity, LauncherTheme.LABEL_SP - 2f, LauncherTheme.TEXT_SECONDARY, maxLines = 2).apply {
            setText(R.string.launcher_phone_assistant_button_hint)
        }, fullWidth())

        // Arama ekranı: Launcher (arama başlayınca ana ekran öne gelir, bizim arama sayfası) / Fabrika. Varsayılan Launcher.
        addView(gap(activity, 12, vertical = true))
        addView(onOffControl(R.string.pref_call_screen, CallScreenPrefs.launcher(activity),
            R.string.pref_call_screen_launcher, R.string.pref_call_screen_oem) { value ->
            d.dismiss()
            CallScreenPrefs.setLauncher(activity, value)
            (activity as? HomeLauncherActivity)?.syncCallWatcher()
        }, fullWidth())
        addView(gap(activity, 4, vertical = true))
        addView(text(activity, LauncherTheme.LABEL_SP - 2f, LauncherTheme.TEXT_SECONDARY, maxLines = 2).apply {
            setText(R.string.pref_call_screen_hint)
        }, fullWidth())

        // Telefon önceliği (varsayılan açık): telefonda Oynat'a basılınca araç kaynağı Bluetooth'a geçer ([PhonePriority]).
        addView(gap(activity, 12, vertical = true))
        addView(onOffControl(R.string.launcher_phone_priority, MediaPrefs.phonePriority(activity)) { value ->
            d.dismiss()
            MediaPrefs.setPhonePriority(activity, value)
        }, fullWidth())
        addView(gap(activity, 4, vertical = true))
        addView(text(activity, LauncherTheme.LABEL_SP - 2f, LauncherTheme.TEXT_SECONDARY, maxLines = 2).apply {
            setText(R.string.launcher_phone_priority_hint)
        }, fullWidth())

        addView(gap(activity, 24, vertical = true))
        addView(row(R.drawable.ic_mdi_view_grid, R.string.launcher_edit_title) {
            d.dismiss()
            onEditHome()
        }, fullWidth())
        if (isInstalled(VEHICLE_SETTINGS_PACKAGE)) {
            addView(gap(activity, 12, vertical = true))
            addView(row(R.drawable.ic_mdi_car, R.string.launcher_vehicle_settings) {
                d.dismiss()
                openApp(VEHICLE_SETTINGS_PACKAGE)
            }, fullWidth())
        }
        addView(gap(activity, 12, vertical = true))
        addView(row(R.drawable.ic_mdi_magnify, R.string.launcher_data_check, R.string.launcher_data_check_hint) {
            d.dismiss()
            dataCheck = (dataCheck ?: DataCheckSheet(activity)).also { it.show() }
        }, fullWidth())
        if (LauncherEdition.HAS_SETTINGS) {
            addView(gap(activity, 12, vertical = true))
            addView(row(R.drawable.ic_mdi_cog, R.string.launcher_mapcontrol_settings) {
                d.dismiss()
                LauncherEdition.openSettings(activity)
            }, fullWidth())
        }
    }

    /** Üç eşit parça; seçili olan vurgu renginde. Seçim kaydedilir ve ekran yeni temayla kurulur. */
    private fun themeSegments(d: Dialog): View {
        val current = ThemeMode.read(activity)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(activity, LauncherTheme.CONTROL, SEGMENT_RADIUS_DP)
            val p = activity.dp(6)
            setPadding(p, p, p, p)
            ThemeMode.values().forEachIndexed { i, mode ->
                if (i > 0) addView(gap(activity, 6))
                val selected = mode == current
                addView(text(activity, LauncherTheme.BODY_SP,
                    if (selected) LauncherTheme.ON_ACCENT else LauncherTheme.TEXT, medium = true).apply {
                    setText(mode.label)
                    gravity = Gravity.CENTER
                    if (selected) background = rounded(activity, LauncherTheme.ACCENT, SEGMENT_RADIUS_DP - 4)
                    isClickable = true
                    isFocusable = true
                    isSelected = selected
                    contentDescription = activity.getString(R.string.launcher_theme) + ": " + activity.getString(mode.label)
                    setOnClickListener {
                        d.dismiss()
                        if (mode != current) {
                            ThemeMode.write(activity, mode)
                            (activity as? HomeLauncherActivity)?.rethemeKeepingEdit() ?: ThemeTransition.retheme(activity)
                        }
                    }
                    pressFeedback()
                }, LinearLayout.LayoutParams(0, activity.dp(LauncherTheme.TOUCH_MIN_DP), 1f))
            }
        }
    }

    /** "Sürüş moduna göre renk": etiket + Açık | Kapalı; değişince ekran yeni vurguyla kurulur. */
    private fun driveModeToggle(d: Dialog): View {
        val on = DriveAccent.enabled(activity)
        return onOffControl(R.string.launcher_drive_theme, on) { value ->
            d.dismiss()
            DriveAccent.setEnabled(activity, value)
            (activity as? HomeLauncherActivity)?.refreshDrivePrefs()
            if (!value) LauncherTheme.driveMode = DriveAccent.NORMAL
            // Kapatınca vurgu hemen varsayılana döner; açınca mod araçtan okunup ~2 sn sonra uygulanır.
            if (!value) ThemeTransition.retheme(activity)
        }
    }

    /** "Spor modu (deneysel)": Açık | Kapalı. Kapatınca açık spor ekranı sakin geçişle orijinal temaya döner. */
    private fun sportModeToggle(d: Dialog): View =
        onOffControl(R.string.launcher_sport_mode, SportModePrefs.enabled(activity)) { value ->
            d.dismiss()
            SportModePrefs.setEnabled(activity, value)
            (activity as? HomeLauncherActivity)?.refreshDrivePrefs()
        }

    /**
     * "Sürüş modu penceresini kapat" (varsayılan açık): spor geçişi başlarken aracın sürüş modu penceresi kapatılır
     * ([OemDriveDialogCloser]); yalnız "Spor modu (deneysel)" açıkken etkisi var.
     */
    private fun sportCloseOemDialogToggle(d: Dialog): View =
        onOffControl(R.string.launcher_sport_close_oem_dialog, SportModePrefs.closeOemDialog(activity)) { value ->
            d.dismiss()
            SportModePrefs.setCloseOemDialog(activity, value)
            (activity as? HomeLauncherActivity)?.refreshDrivePrefs()
        }

    /** Etiket (küçük, ikincil) + iki parçalı Açık | Kapalı; seçili olan vurgu renginde. Değişmeyen seçime dokunmak bir şey yapmaz. */
    private fun onOffControl(
        @StringRes labelRes: Int,
        on: Boolean,
        @StringRes onLabel: Int = R.string.launcher_toggle_on,
        @StringRes offLabel: Int = R.string.launcher_toggle_off,
        onChange: (Boolean) -> Unit,
    ): View =
        LinearLayout(activity).apply {
            // Etiket solda, Açık | Kapalı sağda: iki satırlık sürüş ayarı panele kaydırmadan sığsın.
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(text(activity, LauncherTheme.LABEL_SP, LauncherTheme.TEXT_SECONDARY).apply { setText(labelRes) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                background = rounded(activity, LauncherTheme.CONTROL, SEGMENT_RADIUS_DP)
                val p = activity.dp(6)
                setPadding(p, p, p, p)
                listOf(true to onLabel, false to offLabel).forEachIndexed { i, (value, label) ->
                    if (i > 0) addView(gap(activity, 6))
                    val selected = value == on
                    addView(text(activity, LauncherTheme.BODY_SP,
                        if (selected) LauncherTheme.ON_ACCENT else LauncherTheme.TEXT, medium = true).apply {
                        setText(label)
                        gravity = Gravity.CENTER
                        if (selected) background = rounded(activity, LauncherTheme.ACCENT, SEGMENT_RADIUS_DP - 4)
                        isClickable = true
                        isFocusable = true
                        isSelected = selected
                        contentDescription = activity.getString(labelRes) + ": " + activity.getString(label)
                        setOnClickListener { if (value != on) onChange(value) }
                        pressFeedback()
                    }, LinearLayout.LayoutParams(0, activity.dp(LauncherTheme.TOUCH_MIN_DP), 1f))
                }
            }, LinearLayout.LayoutParams(activity.dp(TOGGLE_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT))
        }

    /**
     * "Spor modunda ambiyans rengi" (varsayılan kapalı): spor başlarken ambiyans OEM kırmızı-turuncusuna geçer, bitince eski
     * renk döner ([SportAmbient]). Yazma doğrulanmadığı için kapı kapalıyken seçim saklanır ama araca bir şey gitmez.
     */
    private fun sportAmbientToggle(d: Dialog): View {
        val on = SportAmbient.isOn(activity)
        val label = if (SportAmbient.enabled) R.string.launcher_sport_ambient else R.string.launcher_sport_ambient_unverified
        return onOffControl(label, on) { value ->
            d.dismiss()
            SportAmbient.setOn(activity, value)
        }
    }

    /** "Sürüşte ayrı düzen" (varsayılan kapalı): etiket + Açık | Kapalı; değişince ekran yeni seçimle kurulur. */
    private fun autoDriveLayoutToggle(d: Dialog): View {
        val on = DriveLayoutPrefs.get(activity)
        return onOffControl(R.string.launcher_auto_drive_layout, on) { value ->
            d.dismiss()
            DriveLayoutPrefs.set(activity, value)
            (activity as? HomeLauncherActivity)?.refreshDrivePrefs()
            ThemeTransition.recreate(activity)
        }
    }

    /**
     * "Alt çubuk": Orijinal | Bizimki | Gizli. Seçim kaydedilir ve ekran yeniden kurulur (OEM çubuğu gizlenir ya da
     * görünür, kendi çubuğumuz eklenir ya da kalkar). Tema seçimiyle aynı üç parçalı görünüm.
     */
    private fun bottomBarSegments(d: Dialog): View {
        val current = LauncherBottomBar.mode(activity)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(activity, LauncherTheme.CONTROL, SEGMENT_RADIUS_DP)
            val p = activity.dp(6)
            setPadding(p, p, p, p)
            BottomBarMode.values().forEachIndexed { i, mode ->
                if (i > 0) addView(gap(activity, 6))
                val selected = mode == current
                addView(text(activity, LauncherTheme.BODY_SP,
                    if (selected) LauncherTheme.ON_ACCENT else LauncherTheme.TEXT, medium = true).apply {
                    setText(mode.label)
                    gravity = Gravity.CENTER
                    if (selected) background = rounded(activity, LauncherTheme.ACCENT, SEGMENT_RADIUS_DP - 4)
                    isClickable = true
                    isFocusable = true
                    isSelected = selected
                    contentDescription = activity.getString(R.string.launcher_bottom_bar) + ": " + activity.getString(mode.label)
                    setOnClickListener {
                        d.dismiss()
                        if (mode != current) {
                            LauncherBottomBar.setMode(activity, mode)
                            ThemeTransition.recreate(activity)
                        }
                    }
                    pressFeedback()
                }, LinearLayout.LayoutParams(0, activity.dp(LauncherTheme.TOUCH_MIN_DP), 1f))
            }
        }
    }

    /**
     * "Favori tuşu": direksiyondaki favori tuşa kısa basınca ne açılacağı (Kapalı | Yandex Navi | ...). Ekran yeniden
     * kurulmaz; dinleyici hemen yeni seçime geçer (Kapalı'da VDBus aboneliği bırakılır).
     */
    private fun favoriteKeySegments(d: Dialog): View {
        val current = FavoriteKeyPrefs.get(activity)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(activity, LauncherTheme.CONTROL, SEGMENT_RADIUS_DP)
            val p = activity.dp(6)
            setPadding(p, p, p, p)
            FavoriteAction.values().forEachIndexed { i, action ->
                if (i > 0) addView(gap(activity, 6))
                val selected = action == current
                addView(text(activity, LauncherTheme.BODY_SP,
                    if (selected) LauncherTheme.ON_ACCENT else LauncherTheme.TEXT, medium = true).apply {
                    setText(action.label)
                    gravity = Gravity.CENTER
                    if (selected) background = rounded(activity, LauncherTheme.ACCENT, SEGMENT_RADIUS_DP - 4)
                    isClickable = true
                    isFocusable = true
                    isSelected = selected
                    contentDescription = activity.getString(R.string.launcher_favorite_key) + ": " + activity.getString(action.label)
                    setOnClickListener {
                        d.dismiss()
                        if (action != current) {
                            FavoriteKeyPrefs.set(activity, action)
                            FavoriteKey.sync(activity)
                        }
                    }
                    pressFeedback()
                }, LinearLayout.LayoutParams(0, activity.dp(LauncherTheme.TOUCH_MIN_DP), 1f))
            }
        }
    }

    private fun row(
        @DrawableRes iconRes: Int,
        @StringRes labelRes: Int,
        @StringRes hintRes: Int = 0,
        onClick: () -> Unit,
    ): View = rowText(iconRes, activity.getString(labelRes), if (hintRes == 0) null else activity.getString(hintRes), onClick)

    private fun rowText(
        @DrawableRes iconRes: Int,
        label: String,
        hint: String?,
        onClick: () -> Unit,
    ): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(activity, LauncherTheme.CONTROL, SEGMENT_RADIUS_DP)
            minimumHeight = activity.dp(ROW_HEIGHT_DP)
            val p = activity.dp(24)
            setPadding(p, 0, p, 0)
            addView(icon(activity, iconRes, 32, LauncherTheme.TEXT_SECONDARY))
            addView(gap(activity, 20))
            if (hint == null) {
                addView(text(activity, LauncherTheme.BODY_SP, medium = true).apply { text = label })
            } else {
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(text(activity, LauncherTheme.BODY_SP, medium = true).apply { text = label })
                    addView(text(activity, LauncherTheme.LABEL_SP, LauncherTheme.TEXT_SECONDARY).apply {
                        text = hint
                    })
                })
            }
            contentDescription = label
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            pressFeedback()
        }

    private fun fullWidth() = sheetFullWidth()

    /** Java yardımcısı senkron çalıştığı için arka planda açılır. */
    private fun openApp(packageName: String) {
        launcher.runSafely {
            if (!DriveScreen.openLeftover(activity, packageName)) AppLaunchHelper.launchAppOnDisplay(activity, packageName, Display.DEFAULT_DISPLAY, 0)
        }
    }

    /** Launcher MapControl'ın dışında çalışıyorsa ve MapControl kuruluysa açılacak paket; yoksa null. */
    private fun mapcontrolPackage(): String? = MapControlLink.choosePackage(
        activity.packageName,
        listOf(MapControlLink.PRIMARY_PACKAGE, MapControlLink.FALLBACK_PACKAGE).filter { isInstalled(it) }.toSet(),
    )

    private fun openMapControl(packageName: String) {
        val intent = activity.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (intent == null) throw ActivityNotFoundException(packageName)
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, R.string.launcher_open_mapcontrol_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isInstalled(packageName: String): Boolean = try {
        activity.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        /** Aracın kendi araç ayarları uygulaması (Desay SV). */
        const val VEHICLE_SETTINGS_PACKAGE = "com.desaysv.vehiclesetting"
        const val ROW_HEIGHT_DP = 80
        /** Açık | Kapalı parçasının genişliği (etiket kalan alanı alır). */
        const val TOGGLE_WIDTH_DP = 300
        /** Araç rengi örneğinin çapı (halka dahil); adıyla birlikte dokunma hedefi 72dp'yi aşar. */
        const val SWATCH_DP = 56
        /** Başlık, iç boşluk ve ekran kenarı için ayrılan yükseklik. */
        /** Başlık + kart dolgusu + sistem çubuğu (1920x720 ekranda 48 px) düşülür; aksi halde son satır pencere altında kırpılır. */
        const val SCROLL_RESERVED_DP = 212
        const val SEGMENT_RADIUS_DP = 16f
    }
}
