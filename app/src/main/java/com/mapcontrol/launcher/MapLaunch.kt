package com.mapcontrol.launcher

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * Ana ekranın harita ve kendi ekranını açma yolları: harita her zaman bölmesiz, tam ekran açılır (Navigasyon kartı, "Haritayı aç",
 * Ev / İş rotası); harita seçilmemişse "+ Harita ekle" seçicisi ([com.mapcontrol.ui.activity.DriveMapPickerActivity]) tam ekran açılır.
 *
 * Eski sürüş ekranı (Mini gösterge + harita, bölünmüş) kaldırıldı. Güncelleme o ekran açıkken gelirse harita görevi yarım serbest
 * pencere olarak kalır; açılışta bir kez tüm alana büyütülür ([recoverStale]).
 */
internal object MapLaunch {

    private const val TAG = "MapLaunch"

    private const val WINDOWING_MODE_FULLSCREEN = 1

    /**
     * Süreç başlangıcında (korumalı): eski sürüş ekranı açıkken süreç öldüyse (uygulama güncellemesi) harita görevi yarım serbest
     * pencere olarak ana ekranın üstünde kalır. İşaretler önce silinir (döngü yok), sonra görev tüm kullanılabilir alana büyütülür;
     * uygulama başlatılmaz / kapatılmaz, görev yoksa hiçbir şey yapılmaz.
     */
    @JvmName("recoverStale")
    fun recoverStale(context: Context) {
        val app = context.applicationContext
        val pkg = DriveMapPrefs.takeStale(app) ?: return
        Thread {
            try {
                Log.i(TAG, "stale drive session -> restore map")
                LauncherEdition.mapcontrol(app).restoreStaleMap(pkg)
            } catch (t: Throwable) {
                Log.w(TAG, "stale restore failed: ${t.javaClass.simpleName}")
            }
        }.start()
    }

    /** Harita tam ekran; serbest pencerede kalmış görevi varsa tüm alana taşınır (Navigasyon kartı "Haritayı aç"). */
    fun openFullScreen(context: Context, pkg: String) {
        LauncherEdition.mapcontrol(context).openFullScreen(pkg)
    }

    /** Navigasyon kartındaki Ev / İş rotası: [route] tam ekran (pencere modu 1) açılır; başlatılamazsa [fallback] (harita tam ekran). */
    fun openRoute(context: Context, route: Intent, fallback: () -> Unit) {
        try {
            context.startActivity(route, fullScreenOptions())
        } catch (e: android.content.ActivityNotFoundException) {
            fallback()
        } catch (e: SecurityException) {
            fallback()
        }
    }

    /** ActivityOptions.setLaunchWindowingMode (gizli API) ile aynı anahtarlar; 1 = WINDOWING_MODE_FULLSCREEN. */
    private fun fullScreenOptions(): Bundle = (ActivityOptions.makeBasic().toBundle() ?: Bundle()).apply {
        putInt("android.activity.windowingMode", WINDOWING_MODE_FULLSCREEN)
        putInt("android:windowingMode", WINDOWING_MODE_FULLSCREEN)
    }

    /**
     * Bizim ana ekranımız tam ekran (pencere modu 1) öne gelir; HOME rolü kapalıyken de açıkça adlandırılır. Ana ekran tuşu
     * yönlendirmesi ve açılış da bu yolu kullanır ([HomeKey]).
     */
    fun launchHome(context: Context) {
        try {
            val intent = Intent(context, HomeLauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            context.startActivity(intent, fullScreenOptions())
        } catch (e: RuntimeException) {
            Log.w(TAG, "launcher could not be opened: ${e.javaClass.simpleName}")
        }
    }
}

/** Harita seçimi ve seçicinin saf kuralları (Android'e bağlı değil; JVM'de sınanır). */
internal object MapPickerPolicy {

    /** Eski sürüş ekranı işaretinden geri alınacak harita paketi (boş / boşluk = yok). */
    fun staleToRestore(pkg: String?): String? = pkg?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Açılacak harita: kartın paketi kuruluysa o; değilse seçicide seçilmiş başka navigasyon uygulaması kuruluysa o; ikisi de yoksa
     * null ("+ Harita ekle" seçicisi).
     */
    fun mapSlot(cardPkg: String?, otherPkg: String?, installed: (String) -> Boolean): String? {
        cardPkg?.trim()?.takeIf { it.isNotEmpty() && installed(it) }?.let { return it }
        return otherPkg?.trim()?.takeIf { it.isNotEmpty() && installed(it) }
    }

    /**
     * Seçicideki uygulamalar: kurulu harita seçenekleri ([MapApp.ORDER] sırasıyla), ardından `geo:` açabilen diğer uygulamalar (adına
     * göre; kendimiz ve yinelenenler hariç).
     */
    fun pickerChoices(mapApps: List<String>, geoApps: List<Pair<String, String>>, selfPkg: String): List<String> {
        val out = LinkedHashSet<String>()
        mapApps.filter { it != selfPkg }.forEach { out.add(it) }
        geoApps.filter { it.first != selfPkg && it.first !in out }.sortedBy { it.second.lowercase() }.forEach { out.add(it.first) }
        return out.toList()
    }
}

/** Seçicide seçilen, [MapApp] dışındaki navigasyon uygulaması (profil bağımsız, Ev / İş ile aynı tercih dosyası). */
internal object DriveMapPrefs {
    private const val PREFS = "launcher_nav"
    private const val KEY_OTHER = "drive_map_other"

    /** Eski sürüş ekranının işaretleri (yalnız [takeStale] okur ve siler; artık yazılmaz). */
    private const val KEY_SESSION = "drive_session_map"
    private const val KEY_PLACED = "drive_placed_map"
    private const val KEY_LEFTOVER = "drive_map_leftover"

    /**
     * Eski sürüş ekranından kalan harita paketi (oturum, yoksa yerleştirme işareti); üç işaret de silinir. Yarıda kalmış ("leftover")
     * harita yalnız silinir: oturum normal bittiyse harita ana ekranın arkasındadır, öne getirilmez.
     */
    fun takeStale(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_SESSION) && !prefs.contains(KEY_PLACED) && !prefs.contains(KEY_LEFTOVER)) return null
        val pkg = MapPickerPolicy.staleToRestore(prefs.getString(KEY_SESSION, null))
            ?: MapPickerPolicy.staleToRestore(prefs.getString(KEY_PLACED, null))
        prefs.edit().remove(KEY_SESSION).remove(KEY_PLACED).remove(KEY_LEFTOVER).apply()
        return pkg
    }

    fun other(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_OTHER, null)

    /** [pkg] bir [MapApp] ise kartın seçimi olur; değilse "diğer" olarak saklanır. */
    fun choose(context: Context, pkg: String) {
        val app = MapApp.fromPackage(pkg)
        val e = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (app != null) {
            MapAppPrefs.set(context, app)
            e.remove(KEY_OTHER)
        } else {
            e.putString(KEY_OTHER, pkg)
        }
        e.apply()
    }

    /** `geo:` açabilen uygulamalar (paket, ad); seçiciye "diğer navigasyon" adayları. */
    fun geoApps(context: Context): List<Pair<String, String>> = try {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=0,0")), 0)
            .mapNotNull { ri -> ri.activityInfo?.let { it.packageName to ri.loadLabel(pm).toString() } }
            .distinctBy { it.first }
    } catch (e: RuntimeException) {
        emptyList()
    }
}
