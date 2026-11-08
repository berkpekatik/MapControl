package com.mapcontrol.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import android.view.WindowInsets
import androidx.annotation.StringRes
import com.mapcontrol.R

/** Alt çubuk seçeneği: OEM çubuğu olduğu gibi · gizlenip yerine bizimki · hiç çubuk yok (tam ekran). */
enum class BottomBarMode(val key: String, @StringRes val label: Int) {
    ORIGINAL("original", R.string.launcher_bottom_bar_original),
    OURS("ours", R.string.launcher_bottom_bar_ours),
    HIDDEN("hidden", R.string.launcher_bottom_bar_hidden);

    /** OEM gezinme çubuğu gizlenir mi (Bizimki ve Gizli). */
    val hidesOemBar: Boolean get() = this != ORIGINAL

    companion object {
        /** Anahtar metni (tercih ya da debug ekstrası); bilinmeyen/boşsa null. Büyük-küçük harf önemsiz. */
        fun parse(value: String?): BottomBarMode? =
            value?.trim()?.lowercase()?.let { v -> values().firstOrNull { it.key == v } }

        /** Eski "Tam ekran" mantıksal tercihi: true Gizli, false Orijinal. */
        fun fromLegacyFullscreen(on: Boolean): BottomBarMode = if (on) HIDDEN else ORIGINAL

        /**
         * Kayıtlı seçimi çözer. Yeni anahtar varsa o; yoksa eski tam ekran tercihi (true → Gizli, false → Orijinal);
         * ikisi de yoksa varsayılan Orijinal.
         */
        fun resolve(stored: String?, legacyFullscreen: Boolean?): BottomBarMode =
            parse(stored) ?: legacyFullscreen?.let(::fromLegacyFullscreen) ?: ORIGINAL

        /**
         * Launcher'ın kendi çubuğu çizilir mi: Bizimki'nde her zaman; "Orijinal" seçiliyken OEM çubuğu yoksa da
         * (emülatör `launcher_oem_inset` olmadan, Desay olmayan ünite). Gizli'de çubuk yok.
         */
        fun drawsOurBar(mode: BottomBarMode, oemBarPresent: Boolean): Boolean =
            mode == OURS || (mode == ORIGINAL && !oemBarPresent)

        /**
         * Sistem OEM çubuğunu zorla mı gösteriyor: Bizimki/Gizli'de gezinme çubuğunu gizlemiştik ama pencere inset'i onu görünür
         * bildiriyor. Android 11 serbest pencere (FrostNav bölünmüş ekranı, Mini gösterge) görünürken sistem çubuklarını zorla açar;
         * o sürece alt kenar OEM çubuğunundur, bizimki çizilmez. Alttan kaydırmayla geçici gösterim uygulamaya görünmez
         * bildirildiği için burada sayılmaz. Orijinal'de OEM çubuğu zaten görünür; bu kural yalnızca gizleyen seçeneklerde işler.
         */
        fun oemBarForced(mode: BottomBarMode, navBarVisible: Boolean, navBarBottomPx: Int): Boolean =
            mode.hidesOemBar && navBarVisible && navBarBottomPx > 0
    }
}

/**
 * Alt çubuk seçeneği (profil başına): Orijinal (varsayılan; OEM çubuğu görünür, launcher onun üstünde biter) ·
 * Bizimki (OEM çubuğu gizlenir, yerine launcher'ın kendi çubuğu [OurBottomBar] gelir) · Gizli (çubuk yok, ana ekran
 * 720 px'in tamamını kullanır). Gizli iken ve Bizimki'nde alttan yukarı kaydırınca OEM çubuğu geçici görünür.
 *
 * Hata ayıklama (yalnızca debug yapı): `--es launcher_bottom_bar original|ours|hidden` tercihi yazar;
 * eski `--ez launcher_fullscreen true|false` takma ad olarak çalışır (true → hidden, false → original).
 */
object LauncherBottomBar {

    private const val PREFS_NAME = "launcher_settings"
    private const val KEY = "bottom_bar"
    /** Eski mantıksal "Tam ekran" anahtarı; yalnızca taşıma için okunur. */
    private const val LEGACY_KEY = "fullscreen"
    private const val EXTRA = "launcher_bottom_bar"
    private const val EXTRA_LEGACY = "launcher_fullscreen"
    private const val TAG = "LauncherBottomBar"

    fun mode(context: Context): BottomBarMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val legacyKey = Profiles.key(context, LEGACY_KEY)
        return BottomBarMode.resolve(
            prefs.getString(Profiles.key(context, KEY), null),
            if (prefs.contains(legacyKey)) prefs.getBoolean(legacyKey, false) else null,
        )
    }

    fun setMode(context: Context, mode: BottomBarMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(Profiles.key(context, KEY), mode.key).apply()
    }

    /** OEM gezinme çubuğu gizlenir mi (Bizimki ya da Gizli). */
    fun hidesOemBar(context: Context): Boolean = mode(context).hidesOemBar

    /**
     * Debug ekstrası varsa (yalnızca debug yapıda) tercihi ayarlar. `launcher_bottom_bar` önceliklidir;
     * tanınmayan değer yok sayılır. Eski `launcher_fullscreen` mantıksal ekstrası takma addır.
     */
    fun applyFrom(activity: Activity, intent: Intent?) {
        if (intent == null) return
        if (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val requested = BottomBarMode.parse(intent.getStringExtra(EXTRA))
            ?: if (intent.hasExtra(EXTRA_LEGACY)) BottomBarMode.fromLegacyFullscreen(intent.getBooleanExtra(EXTRA_LEGACY, false)) else null
        if (requested != null) setMode(activity, requested)
        else if (intent.hasExtra(EXTRA)) Log.w(TAG, "Bilinmeyen $EXTRA değeri: ${intent.getStringExtra(EXTRA)}")
    }

    /** Yerleşimden sonra gerçek durumu günlüğe yazar: Desay gizlemeye uydu mu, kök yükseklik ne. */
    fun logResult(activity: Activity) {
        val decor = activity.window.decorView
        decor.post {
            val navVisible = if (Build.VERSION.SDK_INT >= 30) {
                decor.rootWindowInsets?.isVisible(WindowInsets.Type.navigationBars())
            } else null
            Log.i(TAG, "istenen=${mode(activity).key} kokYukseklik=${decor.height} navBarGorunur=$navVisible")
        }
    }
}
