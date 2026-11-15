package com.mapcontrol.launcher

import android.content.Context
import android.util.Log
import com.mapcontrol.trip.AppBackup
import java.lang.reflect.Modifier
import kotlin.concurrent.thread

/**
 * Araç yazılımına göre `VDMediaPlay` paket biçimi: bazı yazılımlarda arada fazladan bir `long` alan vardır, biçim tutmazsa
 * OEM medya servisi komutu okuyamaz (AM/FM geçmez, hata yalnızca logda görünür). Otomatikte aracın kendi sınıfına bakılır;
 * okunamazsa kısa biçim kullanılır ve kullanıcı Ana ekran menüsünden elle seçebilir.
 */
internal object MediaPlayFormat {
    const val AUTO = 0
    const val SHORT = 1
    const val LONG = 2

    private const val TAG = "MediaSource"
    private const val KEY_MODE = "radioCommandMode"
    private const val OEM_PACKAGE = "com.desaysv.ivi.vds.media"
    private const val OEM_CLASS = "com.desaysv.ivi.vdb.event.id.media.bean.VDMediaPlay"

    private var probed = false
    private var detected: Boolean? = null

    /** Uzun biçim mi: elle seçim önce gelir; otomatikte tespit edilemediyse kısa biçim. */
    fun resolve(mode: Int, detectedLong: Boolean?): Boolean = when (mode) {
        SHORT -> false
        LONG -> true
        else -> detectedLong == true
    }

    @JvmStatic
    fun mode(context: Context): Int = prefs(context).getInt(KEY_MODE, AUTO)

    @JvmStatic
    fun setMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_MODE, mode).apply()
        val app = context.applicationContext
        thread(name = "media-format") { apply(app) }
    }

    /** Arka planda çağrılır (aracın sınıfını yüklemek ana iş parçacığını bekletmesin). */
    fun apply(context: Context) {
        val mode = mode(context)
        val found = if (mode == AUTO) detect(context) else null
        val long = resolve(mode, found)
        MediaPlayWire.withActions = long
        Log.i(TAG, "format: mode=$mode detectedLong=$found long=$long")
    }

    @Synchronized
    private fun detect(context: Context): Boolean? {
        if (probed) return detected
        probed = true
        detected = try {
            val oem = context.createPackageContext(OEM_PACKAGE, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
            oem.classLoader.loadClass(OEM_CLASS).declaredFields.any {
                !Modifier.isStatic(it.modifiers) && it.type == java.lang.Long.TYPE
            }
        } catch (t: Throwable) {
            Log.w(TAG, "format: detect failed ${t.javaClass.simpleName}")
            null
        }
        return detected
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(AppBackup.FILE_MAIN, Context.MODE_PRIVATE)
}
