package com.mapcontrol.launcher

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * HOME kalıcı sabitlendiğinde (cihaz sahibi) çöken bir launcher sürücüyü ana ekransız bırakmasın diye
 * çökme döngüsü bekçisi. Launcher başladıktan [WINDOW_MS] içinde süreç iki kez çökerse "tetiklendi"
 * işaretlenir; uygulama tarafı (MapControlApplication) bunu görüp HOME sabitlemesini kaldırır, alias'ı
 * kapatır ve Launcher Modu'nu kapatır (aracın kendi ana ekranı döner). Test derlemesi [util] paketini
 * görmediği için bu sınıf yalnızca SharedPreferences yazar; kapatma işini uygulama yapar.
 */
object LauncherCrashGuard {
    const val PREFS = "launcher_crash_guard"
    private const val KEY_START = "startMs"
    private const val KEY_CRASHES = "crashTimes"
    const val KEY_TRIPPED = "tripped"
    const val KEY_NOTICE = "noticePending"
    const val WINDOW_MS = 60_000L
    const val HEALTHY_MS = 10_000L
    const val CRASH_LIMIT = 2
    /** Yalnızca debuggable derlemelerde: HOME intent'ine bu ekstra ile bilerek çökme tetiklenir. */
    const val EXTRA_DEBUG_CRASH = "launcher_debug_crash"

    @Volatile private var installed = false

    /** Uygulama tarafı kapatma işlemi; çöken süreç ölmeden hemen önce çağrılır (yeniden başlatmayı beklemez). */
    @Volatile var onTripped: Runnable? = null

    /** HomeLauncherActivity.onCreate'in en başında çağrılır. */
    fun onLauncherStart(context: Context) {
        val app = context.applicationContext
        val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putLong(KEY_START, System.currentTimeMillis()).commit()
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { recordCrash(app) } catch (_: Throwable) {}
            if (previous != null) previous.uncaughtException(t, e) else throw RuntimeException(e)
        }
    }

    /** Launcher ~10 sn sağlıklı çalıştı: sayaç sıfırlanır. */
    fun onHealthy(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_CRASHES).remove(KEY_START).apply()
    }

    private fun recordCrash(app: Context) {
        val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val start = sp.getLong(KEY_START, 0L)
        if (start == 0L || now - start > WINDOW_MS) return // launcher başlangıcından uzak çökme sayılmaz
        val times = (sp.getString(KEY_CRASHES, "") ?: "").split(',').mapNotNull { it.toLongOrNull() }
            .filter { now - it <= WINDOW_MS } + now
        val e = sp.edit().putString(KEY_CRASHES, times.joinToString(","))
        if (times.size >= CRASH_LIMIT) e.putBoolean(KEY_TRIPPED, true).putBoolean(KEY_NOTICE, true)
        e.commit()
        if (times.size >= CRASH_LIMIT) {
            try { onTripped?.run() } catch (_: Throwable) {}
        }
    }

    fun isDebuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
