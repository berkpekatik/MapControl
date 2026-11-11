package com.mapcontrol.launcher

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * HOME kalıcı sabitlendiğinde (cihaz sahibi) çöken bir launcher sürücüyü ana ekransız bırakmasın diye
 * çökme döngüsü bekçisi. İki kural vardır ve biri yeterlidir: launcher başladıktan [WINDOW_MS] içinde süreç
 * [CRASH_LIMIT] kez çökerse ya da launcher açıldıktan sonraki çökmeler (aralarında [HEALTHY_MS]'den uzun sağlıklı
 * çalışma olsa da) son [LONG_WINDOW_MS] içinde [LONG_CRASH_LIMIT] kez olursa "tetiklendi" işaretlenir; uygulama tarafı (MapControlApplication) bunu görüp HOME sabitlemesini kaldırır, alias'ı
 * kapatır ve Launcher Modu'nu kapatır (aracın kendi ana ekranı döner). Bu sınıf yalnızca
 * SharedPreferences yazar; kapatma işini uygulama yapar.
 */
object LauncherCrashGuard {
    const val PREFS = "launcher_crash_guard"
    private const val KEY_START = "startMs"
    private const val KEY_CRASHES = "crashTimes"
    /** Uzun pencere kaydı; sağlıklı çalışmada ([onHealthy]) silinmez. */
    private const val KEY_LONG_CRASHES = "longCrashTimes"
    const val KEY_TRIPPED = "tripped"
    const val KEY_NOTICE = "noticePending"
    const val WINDOW_MS = 60_000L
    const val HEALTHY_MS = 10_000L
    const val CRASH_LIMIT = 2
    /** 10 sn'den uzun aralıklarla tekrarlayan çökmeler: 10 dk içinde 3 çökme de launcher modunu kapatır. */
    const val LONG_WINDOW_MS = 10 * 60_000L
    const val LONG_CRASH_LIMIT = 3
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
            try { recordCrash(app, e) } catch (_: Throwable) {}
            if (previous != null) previous.uncaughtException(t, e) else throw RuntimeException(e)
        }
    }

    /** Launcher ~10 sn sağlıklı çalıştı: hızlı döngü sayacı sıfırlanır (uzun pencere kaydı kalır). */
    fun onHealthy(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_CRASHES).remove(KEY_START).apply()
    }

    /** Saf karar sonucu: saklanacak çökme zamanları ve bekçinin tetiklenip tetiklenmediği. */
    internal data class Decision(val fastTimes: List<Long>, val longTimes: List<Long>, val tripped: Boolean)

    /**
     * Yeni bir çökmede karar (saf; JVM'de sınanır). [startMs] son launcher başlangıcı (0: sağlıklı çalışmadan sonra
     * silinmiş). Hızlı kural: başlangıçtan [WINDOW_MS] içindeki çökmeler [WINDOW_MS] içinde [CRASH_LIMIT] kez.
     * Uzun kural: her çökme [LONG_WINDOW_MS] boyunca sayılır, [LONG_CRASH_LIMIT] kez olursa tetiklenir.
     * Tetiklenince iki kayıt da boşalır (launcher yeniden açılırsa sayım baştan başlar).
     */
    internal fun decide(now: Long, startMs: Long, fastTimes: List<Long>, longTimes: List<Long>): Decision {
        val longKept = longTimes.filter { now - it in 0..LONG_WINDOW_MS } + now
        val nearStart = startMs != 0L && now - startMs in 0..WINDOW_MS
        val fastKept = if (nearStart) fastTimes.filter { now - it in 0..WINDOW_MS } + now else emptyList()
        val tripped = (nearStart && fastKept.size >= CRASH_LIMIT) || longKept.size >= LONG_CRASH_LIMIT
        return if (tripped) Decision(emptyList(), emptyList(), true) else Decision(fastKept, longKept, false)
    }

    private fun parseTimes(raw: String?): List<Long> =
        (raw ?: "").split(',').mapNotNull { it.toLongOrNull() }

    private fun recordCrash(app: Context, error: Throwable) {
        val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = decide(
            System.currentTimeMillis(),
            sp.getLong(KEY_START, 0L),
            parseTimes(sp.getString(KEY_CRASHES, null)),
            parseTimes(sp.getString(KEY_LONG_CRASHES, null)),
        )
        val e = sp.edit()
            .putString(KEY_CRASHES, d.fastTimes.joinToString(","))
            .putString(KEY_LONG_CRASHES, d.longTimes.joinToString(","))
        if (d.tripped) e.putBoolean(KEY_TRIPPED, true).putBoolean(KEY_NOTICE, true)
        e.commit()
        if (d.tripped) {
            // Ayrı uyarı: "kaç araçta launcher modu otomatik kapandı" doğrudan sayılsın; asıl çökme ayrıca raporlanır.
            try { com.mapcontrol.GlitchTip.reportWarning("launcher_guard:tripped", tripExtras(error)) } catch (_: Throwable) {}
            try { onTripped?.run() } catch (_: Throwable) {}
        }
    }

    /** Tetikleyen çökmenin tipi ve ilk MapControl satırı (saf; JVM'de sınanır). */
    internal fun tripExtras(error: Throwable): Map<String, String> {
        val site = error.stackTrace.firstOrNull { it.className.startsWith("com.mapcontrol.") }
        return mapOf(
            "exception" to error.javaClass.name,
            "site" to (site?.let { "${it.className}.${it.methodName}:${it.lineNumber}" } ?: "?"),
        )
    }

    fun isDebuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
