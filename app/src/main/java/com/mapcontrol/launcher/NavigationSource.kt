package com.mapcontrol.launcher

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.mapcontrol.BuildConfig
import com.mapcontrol.media.LauncherMediaController
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Bildirimleri tek NavParse kuyruğunda bir kez çözümler. Launcher seçili haritanın durumunu,
 * HUD ise paket bazındaki anlık görüntüyü dinler; kaynak seçimleri birbirinden bağımsızdır.
 * Kaldır/yeniden yayımla aralığı ortak 800 ms beklemeyle yönetilir.
 */
object NavigationSource {
    const val YANDEX_MAPS = "ru.yandex.yandexmaps"
    const val YANDEX_NAVI = "ru.yandex.yandexnavi"
    const val GOOGLE_MAPS = "com.google.android.apps.maps"
    @JvmField val PACKAGES: Set<String> = setOf(YANDEX_MAPS, YANDEX_NAVI, GOOGLE_MAPS)
    private const val TAG = "NavSource"
    private const val REMOVE_HOLD_MS = 800L
    private const val ACCEPTED_REFRESH_MS = 5_000L
    private const val SLOW_PARSE_MS = 50L
    private val main = Handler(Looper.getMainLooper())
    private val worker: Handler by lazy {
        val thread = HandlerThread("NavParse", android.os.Process.THREAD_PRIORITY_BACKGROUND)
        thread.start()
        Handler(thread.looper)
    }
    internal val parsingHandler: Handler get() = worker
    private val listeners = CopyOnWriteArraySet<(NavigationState) -> Unit>()
    private val packageListeners = CopyOnWriteArraySet<(Map<String, NavigationState>) -> Unit>()
    private val routes = NavigationRoutes()
    private val gate = NavParseGate()
    @Volatile private var real = NavigationState.NONE
    @Volatile private var packages: Map<String, NavigationState> = emptyMap()
    @Volatile private var demo = false
    @Volatile private var serviceRef = WeakReference<NotificationListenerService>(null)
    @Volatile private var epoch = 0
    @Volatile private var accepted = MapAppChoice.notificationPackages(null)
    private var acceptedReadAtMs = 0L

    @JvmStatic val state: NavigationState get() = if (demo) NavigationState.DEMO else real
    @JvmStatic val isListenerBound: Boolean get() = serviceRef.get() != null
    @JvmStatic fun hasAccess(context: Context): Boolean = LauncherMediaController.isNotificationAccessEnabled(context)

    /** Ana iş parçacığından abone olunur; hemen son durum, ardından değişiklikler verilir. */
    @JvmStatic fun addListener(listener: (NavigationState) -> Unit) {
        listeners.add(listener)
        listener(state)
    }
    @JvmStatic fun removeListener(listener: (NavigationState) -> Unit) { listeners.remove(listener) }

    /** Demo verisi bu hatta girmez. Aynı bildirim yeniden ayrıştırılmaz. */
    internal fun addPackageListener(listener: (Map<String, NavigationState>) -> Unit) {
        packageListeners.add(listener)
        listener(packages)
        worker.post { resync() }
    }
    internal fun removePackageListener(listener: (Map<String, NavigationState>) -> Unit) {
        packageListeners.remove(listener)
    }

    @JvmStatic fun setDemo(on: Boolean) {
        if (demo == on) return
        demo = on
        main.post { listeners.forEach { it(state) } }
    }

    @JvmStatic fun setMapApp(app: MapApp?) {
        val next = MapAppChoice.notificationPackages(app)
        if (next == accepted) return
        accepted = next
        worker.post { resync() }
    }

    private fun refreshAccepted(context: Context): Boolean {
        acceptedReadAtMs = SystemClock.elapsedRealtime()
        val next = MapAppChoice.notificationPackages(try { MapAppPrefs.current(context) } catch (_: Exception) { null })
        val changed = next != accepted
        accepted = next
        return changed
    }

    @JvmStatic fun onListenerConnected(service: NotificationListenerService) {
        val token = ++epoch
        serviceRef = WeakReference(service)
        worker.post {
            if (token != epoch) return@post
            worker.removeCallbacks(confirmRemoved)
            refreshAccepted(service)
            PACKAGES.forEach { NavNotificationReader.forget(it) }
            routes.clear()
            gate.clear()
            resync(force = true)
        }
    }

    @JvmStatic fun onListenerDisconnected() {
        val token = ++epoch
        serviceRef.clear()
        worker.post {
            if (token != epoch) return@post
            worker.removeCallbacks(confirmRemoved)
            routes.clear()
            gate.clear()
            publishRoutes(force = true)
        }
    }

    @JvmStatic fun onNotificationPosted(service: NotificationListenerService, sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName !in PACKAGES) return
        val token = epoch
        worker.post {
            if (token != epoch || serviceRef.get() !== service) return@post
            if (SystemClock.elapsedRealtime() - acceptedReadAtMs > ACCEPTED_REFRESH_MS && refreshAccepted(service)) resync()
            if (wanted(sbn.packageName)) {
                parseGated(service, sbn)
                publishRoutes()
            }
        }
    }

    @JvmStatic fun onNotificationRemoved(service: NotificationListenerService, sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName !in PACKAGES) return
        val token = epoch
        worker.post {
            if (token != epoch || serviceRef.get() !== service) return@post
            // Rota bu aralıkta tutulur; aynı anahtarlı yeniden yayım önceki çerçeveyi boşaltmaz.
            worker.removeCallbacks(confirmRemoved)
            worker.postDelayed(confirmRemoved, REMOVE_HOLD_MS)
        }
    }

    private val confirmRemoved = Runnable { resync() }
    private fun wanted(pkg: String): Boolean = pkg in accepted || packageListeners.isNotEmpty()

    private fun resync(force: Boolean = false) {
        val service = serviceRef.get() ?: return
        val active = try { service.activeNotifications } catch (_: Exception) { null }
        // Geçici binder hatasında çalışan rota korunur; kopma ayrı yaşam döngüsü olayıdır.
        if (active == null) {
            if (force) publishRoutes(force = true)
            return
        }
        val current = active.filterNotNull().filter { it.packageName in PACKAGES && wanted(it.packageName) }
        val keys = current.map { it.key }.toSet()
        routes.retainKeys(keys)
        gate.retainKeys(keys)
        current.forEach { parseGated(service, it) }
        publishRoutes(force)
    }

    private fun parseGated(context: Context, sbn: StatusBarNotification) {
        var sig: Int? = null
        val signatureOf = { sig ?: signature(sbn).also { sig = it } }
        if (!gate.shouldParse(sbn.key, sbn.postTime, signatureOf)) return
        routes.update(sbn.key, parse(context, sbn))
        gate.remember(sbn.key, sbn.postTime, signatureOf())
    }

    private fun signature(sbn: StatusBarNotification): Int = try {
        NavNotificationReader.contentSignature(sbn)
    } catch (_: Exception) { sbn.postTime.hashCode() }

    private fun publishRoutes(force: Boolean = false) {
        val next = routes.byPackage()
        val nextReal = latestNavigation(next, accepted)
        val changed = nextReal.copy(updatedAtMs = 0) != real.copy(updatedAtMs = 0)
        val packagesChanged = next != packages
        packages = next
        real = nextReal
        if (!force && !changed && !packagesChanged) return
        val token = epoch
        main.post {
            if (token != epoch) return@post
            if (force || changed) listeners.forEach { it(state) }
            if (force || packagesChanged) packageListeners.forEach { it(packages) }
        }
    }

    private fun parse(context: Context, sbn: StatusBarNotification): NavigationState? {
        val t0 = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else 0L
        val google = sbn.packageName == GOOGLE_MAPS
        val result = try {
            if (google) parseGoogle(sbn) else NavParse.parse(NavNotificationReader.read(context, sbn), System.currentTimeMillis())
        } catch (e: Exception) {
            null
        }
        val withEnd = result?.copy(endAction = findEndAction(sbn), sourcePackage = sbn.packageName, updatedAtMs = sbn.postTime)
        if (BuildConfig.DEBUG) {
            val dt = SystemClock.elapsedRealtime() - t0
            if (dt > SLOW_PARSE_MS) Log.w(TAG, "slow parse ${dt} ms pkg=${sbn.packageName}")
            // Yalnız kısa bayraklar: adres ve yol adı yazılmaz.
            if (google) Log.d(TAG, "google active=${result != null} maneuver=${result?.maneuver} dist=${result?.distance != null} eta=${result?.arrivalTime != null}")
        }
        return withEnd
    }

    /** Bildirim eylemlerinden "bitir" eyleminin PendingIntent'i ([NavEndAction] başlık eşlemesi); yoksa null. */
    private fun findEndAction(sbn: StatusBarNotification): android.app.PendingIntent? = try {
        val actions = sbn.notification?.actions
        actions?.firstOrNull { NavEndAction.isEndTitle(it.title) }?.actionIntent
    } catch (e: Exception) {
        null
    }

    /** Google Haritalar: yalnız rehberlik bildirimi ([GoogleNavParse.isGuidanceNotification]) ve extras metinleri (başlık, metin, alt metin); RemoteViews gezilmez. */
    private fun parseGoogle(sbn: StatusBarNotification): NavigationState? {
        val n = sbn.notification ?: return null
        // Trafik / işe gidiş gibi sayı içeren diğer Google bildirimleri kartı ele geçirmesin.
        if (!GoogleNavParse.isGuidanceNotification(n.category, n.flags)) return null
        val extras = n.extras ?: return null
        return GoogleNavParse.parse(
            extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString(),
            extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString(),
            System.currentTimeMillis(),
        )
    }

}
