package com.mapcontrol.launcher

import android.content.Context
import android.content.Intent

/**
 * FrostNav'ın "Ana ekran" sekmesi için Java'dan çağrılan ince katman: launcher ayarlarını aynı tercih
 * anahtarlarıyla (ThemeMode, DriveAccent, LauncherBottomBar, DriveLayoutPrefs, SahneStore, VehicleProfile) okur ve yazar.
 * Launcher bu değerlerin [signature]'ı değişmişse öne gelirken kendini yeniden kurar (bkz. HomeLauncherActivity.onResume).
 */
object HomeScreenPrefs {

    /** FrostNav "Ana ekran" sekmesinden gelir: launcher açılışta düzenleme moduna girer (bir kez tüketilir). */
    const val EXTRA_OPEN_EDIT = "launcher_open_edit"
    private const val PREFS_NAME = "launcher_settings"
    private const val KEY_LAYOUT_RESET_GEN = "layout_reset_gen"
    private const val KEY_VEHICLE_PROFILE = "vehicle_profile"

    /** "Araç ekranı": 0 = Otomatik (ekrandan), 1 = Tiggo 7, 2 = Tiggo 8. Launcher bir sonraki öne gelişinde yeniden kurulur. */
    @JvmStatic fun vehicleProfileIndex(context: Context): Int =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_VEHICLE_PROFILE, VehicleProfile.OVERRIDE_AUTO).coerceIn(0, 2)

    @JvmStatic fun setVehicleProfileIndex(context: Context, index: Int) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_VEHICLE_PROFILE, index.coerceIn(0, 2)).apply()
    }

    @JvmStatic fun themeIndex(context: Context): Int = ThemeMode.read(context).ordinal

    @JvmStatic fun setThemeIndex(context: Context, index: Int) {
        ThemeMode.values().getOrNull(index)?.let { ThemeMode.write(context, it) }
    }

    @JvmStatic fun driveAccent(context: Context): Boolean = DriveAccent.enabled(context)

    @JvmStatic fun setDriveAccent(context: Context, on: Boolean) = DriveAccent.setEnabled(context, on)

    @JvmStatic fun sportMode(context: Context): Boolean = SportModePrefs.enabled(context)

    @JvmStatic fun setSportMode(context: Context, on: Boolean) = SportModePrefs.setEnabled(context, on)

    @JvmStatic fun bottomBarIndex(context: Context): Int = LauncherBottomBar.mode(context).ordinal

    @JvmStatic fun setBottomBarIndex(context: Context, index: Int) {
        BottomBarMode.values().getOrNull(index)?.let { LauncherBottomBar.setMode(context, it) }
    }

    /**
     * "Araç rengi" (sıra [CarColor] sırası: Grafit, Siyah, Beyaz, Gümüş, Kırmızı, Mavi). İmzaya girmez: açık launcher
     * tercih dinleyicisiyle görselleri canlı değiştirir, yeniden kurulmaz.
     */
    @JvmStatic fun carColorIndex(context: Context): Int = CarColorPrefs.get(context).ordinal

    @JvmStatic fun autoDriveLayout(context: Context): Boolean = DriveLayoutPrefs.get(context)

    @JvmStatic fun setAutoDriveLayout(context: Context, on: Boolean) = DriveLayoutPrefs.set(context, on)

    /** Sahne'nin Park ya da Sürüş düzenini varsayılana döndürür (düzenleme modundaki "Varsayılan" ile aynı depo). */
    @JvmStatic fun resetLayout(context: Context, drive: Boolean) {
        val mode = if (drive) LayoutMode.DRIVE else LayoutMode.PARK
        SahneStore(context, mode).reset()
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_LAYOUT_RESET_GEN, prefs.getInt(KEY_LAYOUT_RESET_GEN, 0) + 1).apply()
    }

    /** Launcher'ın kurulumda okuduğu tercihlerin özeti; farklıysa açık launcher yeniden kurulmalıdır. */
    @JvmStatic fun signature(context: Context): String {
        val gen = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_LAYOUT_RESET_GEN, 0)
        return "${themeIndex(context)}|${driveAccent(context)}|${bottomBarIndex(context)}|${autoDriveLayout(context)}|$gen|${VehicleProfile.compute(context).key}"
    }

    /** Ana ekranı doğrudan düzenleme modunda açar. */
    @JvmStatic fun openEditor(context: Context) {
        context.startActivity(
            Intent(context, HomeLauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_OPEN_EDIT, true),
        )
    }
}
