package com.mapcontrol.nav

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.desaysv.ivi.extra.project.carinfo.HUDID
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy
import com.desaysv.ivi.extra.project.carinfo.proxy.IServiceConnectListener
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import com.mapcontrol.R
import com.mapcontrol.launcher.NavParse
import com.mapcontrol.launcher.NavNotificationReader
import com.mapcontrol.launcher.NavParseGate
import com.mapcontrol.launcher.NavigationSource
import com.mapcontrol.launcher.NavigationState
import com.mapcontrol.service.GlobalBackService
import com.mapcontrol.vehicle.JaecooJ7Features
import com.mapcontrol.trip.AppBackup
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArraySet

/** Google/Yandex rehberliğini üreticinin MODULE_HUD / IHU_INTERCONNECT arayüzüne aktarır. */
class HudNavigationManager private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE)
    private val profilePrefs = app.getSharedPreferences(AppBackup.FILE_LAUNCHER, Context.MODE_PRIVATE)
    private var j7Selected = JaecooJ7Features.isEnabled(app)
    private val main = Handler(Looper.getMainLooper())
    private val worker: Handler get() = NavigationSource.parsingHandler
    private val watchers = CopyOnWriteArraySet<Runnable>()
    private val routes = HudRouteStore()
    private val gate = NavParseGate()
    private var service = WeakReference<NotificationListenerService>(null)
    private val proxy = CarInfoProxy.getInstance()
    private val session = NativeHudSession(
        initiallyOwned = prefs.getBoolean(OWNED, false),
        persistOwnership = { owned -> prefs.edit().putBoolean(OWNED, owned).commit() },
        send = ::sendPacket,
    )
    @Volatile private var running = false
    @Volatile private var revision = 0
    @Volatile private var sendRevision = 0
    private var sendingToken = 0
    private var state = NavigationState.NONE
    private var yandexForeground = NavigationState.NONE
    private var previewUntil = 0L
    private var lastStatus = R.string.hud_nav_off
    private var connectionListening = false
    private var connectAttemptAt: Long? = null
    private val endPreview = Runnable { previewUntil = 0L; render() }
    private val retry = Runnable { render() }
    private val repeatClear = Runnable { repeatNativeClear() }
    private val connectionListener = IServiceConnectListener {
        worker.post {
            session.connectionLost()
            main.post { render() }
        }
    }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == ENABLED || key == PROVIDER) main.post { reconfigure() }
    }

    private val profileListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        main.post {
            val selected = JaecooJ7Features.isEnabled(app)
            if (selected != j7Selected) {
                j7Selected = selected
                reconfigure()
            }
        }
    }

    fun isEnabled(): Boolean = JaecooJ7Features.isEnabled(app) && prefs.getBoolean(ENABLED, false)
    fun provider(): String = prefs.getString(PROVIDER, "auto") ?: "auto"
    fun status(): String = app.getString(lastStatus)
    fun needsYandexScraping(): Boolean = running && isEnabled() && provider() != "google"

    fun setEnabled(value: Boolean) {
        start()
        prefs.edit().putBoolean(ENABLED, value).apply()
    }

    fun setProvider(value: String) {
        require(value in listOf("auto", "google", "yandex"))
        prefs.edit().putString(PROVIDER, value).apply()
    }

    fun addWatcher(watcher: Runnable) {
        watchers.add(watcher)
        watcher.run()
    }

    fun removeWatcher(watcher: Runnable) { watchers.remove(watcher) }

    fun start() {
        if (running) return
        running = true
        j7Selected = JaecooJ7Features.isEnabled(app)
        profilePrefs.registerOnSharedPreferenceChangeListener(profileListener)
        if (!connectionListening) {
            proxy.regServiceConnectListener(connectionListener)
            connectionListening = true
        }
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        reconfigure()
    }

    fun stop() {
        running = false
        profilePrefs.unregisterOnSharedPreferenceChangeListener(profileListener)
        revision++
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        main.removeCallbacks(endPreview)
        previewUntil = 0L
        state = NavigationState.NONE
        yandexForeground = NavigationState.NONE
        render()
        GlobalBackService.refreshHudNavigation()
    }

    private fun reconfigure() {
        if (!running) return
        revision++
        state = NavigationState.NONE
        yandexForeground = NavigationState.NONE
        previewUntil = 0L
        main.removeCallbacks(endPreview)
        if (isEnabled()) {
            val token = revision
            val currentService = service.get()
            worker.post {
                if (token != revision) return@post
                routes.clear()
                gate.clear()
                if (currentService != null) resync(currentService, token)
            }
        }
        render()
        GlobalBackService.refreshHudNavigation()
        notifyWatchers()
    }

    fun refresh() {
        if (!running) start() else render()
        notifyWatchers()
    }

    fun onListenerConnected(value: NotificationListenerService) {
        service = WeakReference(value)
        if (running) reconfigure()
    }

    fun onListenerDisconnected() {
        service.clear()
        revision++
        state = NavigationState.NONE
        yandexForeground = NavigationState.NONE
        previewUntil = 0L
        main.removeCallbacks(endPreview)
        render()
    }

    /** Yandex öndeyken bildirim sadeleşebilir; erişilebilirlikten yalnız doğrulanmış mesafe/yol alanları alınır. */
    fun onYandexSnapshot(snapshot: YandexNavSnapshot) {
        val token = revision
        main.post {
            if (token != revision || !needsYandexScraping()) return@post
            yandexForeground = if (snapshot.guidanceActive) NavigationState(
                active = true,
                distance = NavParse.parseDistanceOnly(snapshot.formatManeuverLine()),
                street = snapshot.nextStreet?.takeIf { it.isNotBlank() } ?: snapshot.statusPanel,
                arrivalTime = snapshot.etaArrival,
                remainingDistance = NavParse.findDistance(snapshot.etaDistance),
                remainingMinutes = NavParse.parseMinutes(snapshot.etaTime),
                sourcePackage = NavigationSource.YANDEX_MAPS,
                updatedAtMs = System.currentTimeMillis(),
            ) else NavigationState.NONE
            render()
        }
    }

    fun onNotificationPosted(value: NotificationListenerService, notification: StatusBarNotification?) {
        if (!running || !isEnabled() || notification == null || notification.packageName !in NavigationSource.PACKAGES) return
        val token = revision
        val source = provider()
        worker.post {
            if (token != revision) return@post
            val signature = try { NavNotificationReader.contentSignature(notification) } catch (_: Exception) { notification.postTime.hashCode() }
            if (!gate.shouldParse(notification.key, notification.postTime) { signature }) return@post
            gate.remember(notification.key, notification.postTime, signature)
            val parsed = NavigationSource.parseForHud(value, notification)?.copy(updatedAtMs = notification.postTime)
            routes.update(notification.key, parsed)
            publish(routes.current(source), token)
        }
    }

    fun onNotificationRemoved(value: NotificationListenerService, notification: StatusBarNotification?) {
        if (!running || !isEnabled() || notification == null || notification.packageName !in NavigationSource.PACKAGES) return
        val token = revision
        worker.post {
            if (token != revision) return@post
            gate.forget(notification.key)
            routes.remove(notification.key)
            // Aynı anahtarlı kaldır/yeniden yayımla aralığında kart titreşmesin.
            worker.postDelayed({
                if (token == revision) resync(value, token)
            }, 800L)
        }
    }

    private fun resync(value: NotificationListenerService, token: Int) {
        if (token != revision) return
        val active = try { value.activeNotifications } catch (_: Exception) { null }
        routes.clear()
        gate.clear()
        active?.filterNotNull()?.filter { it.packageName in NavigationSource.PACKAGES }?.forEach {
            val parsed = NavigationSource.parseForHud(value, it)?.copy(updatedAtMs = it.postTime)
            routes.update(it.key, parsed)
        }
        publish(routes.current(provider()), token)
    }

    private fun publish(next: NavigationState, token: Int) {
        main.post {
            if (token != revision || !running || !isEnabled()) return@post
            state = next
            render()
        }
    }

    /** Üreticinin HUD arayüzüne 10 saniyelik örnek rota yollar; canlı kaynak verisini değiştirmez. */
    fun showTest() {
        if (!running || !isEnabled()) return
        previewUntil = SystemClock.elapsedRealtime() + 10_000L
        main.removeCallbacks(endPreview)
        main.postDelayed(endPreview, 10_000L)
        render()
    }

    private fun render() {
        if (!running || !isEnabled()) {
            transmit(null, R.string.hud_nav_off)
            return
        }
        val preview = SystemClock.elapsedRealtime() < previewUntil
        if (!preview && service.get() == null && !yandexForeground.active) {
            transmit(null, R.string.hud_nav_need_notifications)
            return
        }
        val route = if (preview) NavigationState.DEMO else hudRoute(state, yandexForeground, provider())
        val metric = Settings.System.getInt(app.contentResolver, "com.desaysv.setting.unit", 0) == 0
        val frame = NativeHudProtocol.encode(route, includeRawMetric = metric)
        transmit(frame, if (frame == null) R.string.hud_nav_waiting else R.string.hud_nav_showing)
    }

    /** Binder ve bütün oturum yazmaları ayrıştırmayla aynı seri arka plan kuyruğunda çalışır. */
    private fun transmit(frame: NativeHudFrame?, status: Int) {
        val token = ++sendRevision
        if (frame != null) main.removeCallbacks(repeatClear)
        updateStatus(if (frame == null) status else R.string.hud_nav_sending)
        worker.post {
            if (token != sendRevision) return@post
            sendingToken = token
            val connected = proxy.isServiceConnnected
            val success = if (connected) {
                session.apply(frame)
            } else {
                session.connectionLost()
                if (frame != null || session.hasWork) connect()
                frame == null && !session.hasWork
            }
            val repeat = session.needsRepeatClear
            main.post result@{
                if (token != sendRevision) return@result
                if (frame != null) {
                    updateStatus(when {
                        success -> R.string.hud_nav_showing
                        !connected -> R.string.hud_nav_need_vehicle
                        else -> R.string.hud_nav_send_error
                    })
                }
                if (!success) scheduleRetry() else main.removeCallbacks(retry)
                if (repeat && !main.hasCallbacks(repeatClear)) main.postDelayed(repeatClear, 1500L)
                if (!running && success && !repeat) releaseConnectionListener()
            }
        }
    }

    private fun connect() {
        val now = SystemClock.elapsedRealtime()
        if (connectAttemptAt?.let { now - it < 5_000L } == true) return
        connectAttemptAt = now
        try {
            proxy.init(app)
        } catch (e: RuntimeException) {
            Log.w("HudNavigation", "Araç HUD servisine bağlanılamadı", e)
        }
    }

    private fun sendPacket(packet: IntArray): Boolean {
        if (sendingToken != sendRevision || !proxy.isServiceConnnected) return false
        return try {
            proxy.sendItemValues(VDEventCarInfo.MODULE_HUD, HUDID.ID_HUD_IHU_INTERCONNECT, packet)
            true
        } catch (e: RuntimeException) {
            Log.w("HudNavigation", "Yerleşik HUD paketi gönderilemedi", e)
            false
        }
    }

    private fun repeatNativeClear() {
        val token = sendRevision
        worker.post {
            if (token != sendRevision) return@post
            sendingToken = token
            val success = session.repeatClear()
            main.post result@{
                if (token != sendRevision) return@result
                if (!success) scheduleRetry() else if (!running) releaseConnectionListener()
            }
        }
    }

    private fun scheduleRetry() {
        if (!main.hasCallbacks(retry)) main.postDelayed(retry, 3_000L)
    }

    private fun releaseConnectionListener() {
        if (!connectionListening) return
        proxy.unRegServiceConnectListener(connectionListener)
        connectionListening = false
    }

    private fun updateStatus(value: Int) {
        if (lastStatus == value) return
        lastStatus = value
        notifyWatchers()
    }

    private fun notifyWatchers() { watchers.forEach { it.run() } }

    companion object {
        private const val ENABLED = "hudNavEnabled"
        private const val PROVIDER = "hudNavProvider"
        private const val OWNED = "nativeHudOwned"
        private var instance: HudNavigationManager? = null

        @JvmStatic
        @Synchronized
        fun get(context: Context): HudNavigationManager =
            instance ?: HudNavigationManager(context).also { instance = it }
    }
}
