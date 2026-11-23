package com.mapcontrol.nav

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.desaysv.ivi.extra.project.carinfo.proxy.IServiceConnectListener
import com.mapcontrol.R
import com.mapcontrol.launcher.NavParse
import com.mapcontrol.launcher.NavigationSource
import com.mapcontrol.launcher.NavigationState
import com.mapcontrol.launcher.latestNavigation
import com.mapcontrol.service.GlobalBackService
import java.util.concurrent.CopyOnWriteArraySet

/** Ortak navigasyon kaynağından üreticinin yerleşik HUD arayüzüne aktarım. */
class HudNavigationManager internal constructor(context: Context, private val vehicle: HudVehicle) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val worker: Handler get() = NavigationSource.parsingHandler
    private val watchers = CopyOnWriteArraySet<Runnable>()
    private val session = NativeHudSession(
        initiallyOwned = prefs.getBoolean(OWNED, false),
        persistOwnership = { owned -> prefs.edit().putBoolean(OWNED, owned).commit() },
        send = ::sendPacket,
    )
    @Volatile private var running = false
    @Volatile private var support: Boolean? = null
    @Volatile private var generation = 0
    private var sendingGeneration = 0
    private var requested = false
    private var connectionListening = false
    private var sourceListening = false
    private var checking = false
    private var connected = false
    private var power: Int? = null
    private var routes: Map<String, NavigationState> = emptyMap()
    private var yandexForeground = NavigationState.NONE
    private var previewUntil = 0L
    private var lastStatus = R.string.hud_nav_off
    private var connectAttemptAt: Long? = null
    @Volatile private var desired: NativeHudFrame? = null
    @Volatile private var version = 0
    private var desiredStatus = R.string.hud_nav_off
    private var initialized = false
    private var sending = false
    private val poll = Runnable { checkVehicle() }
    private val retry = Runnable { render(force = true) }
    private val endPreview = Runnable { previewUntil = 0L; render() }
    private val repeatClear = Runnable { dispatchRepeatClear() }
    private val sourceListener: (Map<String, NavigationState>) -> Unit = {
        routes = it
        render()
    }
    private val connectionListener = IServiceConnectListener { value ->
        main.post {
            if (value == 0) {
                generation++
                worker.post { session.connectionLost() }
            }
            if (requested) checkVehicle() else render(force = true)
        }
    }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == ENABLED || key == PROVIDER) main.post { reconfigure() }
    }

    fun isSupported(): Boolean = support == true
    /** Donanım yok diye doğrulanmadıkça (bilinmiyor dahil) kart görünür; gönderim yine yalnız destek doğruysa yapılır. */
    fun isKnownAbsent(): Boolean = support == false
    /** Kullanıcının kayıtlı tercihi; donanım doğrulanmasından bağımsız (arayüz anahtarı bunu gösterir). */
    fun isPreferenceOn(): Boolean = prefs.getBoolean(ENABLED, false)
    /** Aktarım gerçekten çalışabilir mi: tercih açık ve donanım doğrulanmış. */
    fun isEnabled(): Boolean = isSupported() && isPreferenceOn()
    fun provider(): String = prefs.getString(PROVIDER, "auto") ?: "auto"
    fun status(): String = app.getString(lastStatus)
    fun needsYandexScraping(): Boolean = running && isEnabled() && provider() != "google"

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(ENABLED, value).apply()
        start()
    }
    fun setProvider(value: String) {
        require(value in listOf("auto", "google", "yandex"))
        prefs.edit().putString(PROVIDER, value).apply()
    }
    fun addWatcher(watcher: Runnable) { watchers.add(watcher); watcher.run() }
    fun removeWatcher(watcher: Runnable) { watchers.remove(watcher) }

    /** HUD yoksa kaynak, tercih ve araç bağlantı dinleyicileri kaydedilmez. */
    fun start() {
        requested = true
        checkVehicle()
    }
    fun refresh() = start()

    fun stop() {
        requested = false
        generation++
        deactivate()
        main.removeCallbacks(poll)
        render(force = true)
    }

    private fun activate() {
        if (running) return
        running = true
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        if (!connectionListening) {
            vehicle.listen(connectionListener)
            connectionListening = true
        }
        syncSource()
        GlobalBackService.refreshHudNavigation()
    }

    private fun deactivate() {
        if (running) {
            running = false
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        }
        syncSource()
        previewUntil = 0L
        yandexForeground = NavigationState.NONE
        routes = emptyMap()
        main.removeCallbacks(endPreview)
        GlobalBackService.refreshHudNavigation()
    }

    /** Config okuması UI iş parçacığına taşınmaz; bilinmeyen destek daha sonra tekrar okunur. */
    private fun checkVehicle() {
        if (!requested || checking) return
        checking = true
        main.removeCallbacks(poll)
        worker.post {
            val nextSupport = vehicle.hudSupport()
            val nextConnected = vehicle.connected()
            val nextPower = if (nextConnected) vehicle.powerMode() else null
            main.post result@{
                checking = false
                if (!requested) return@result
                val changed = nextSupport != support || nextConnected != connected || nextPower != power
                if (changed) {
                    generation++
                    if (nextSupport != true || !nextConnected || nextPower?.let { it > 0 } != true) {
                        worker.post { session.connectionLost() }
                    }
                }
                support = nextSupport
                connected = nextConnected
                power = nextPower
                if (support == true) activate() else {
                    deactivate()
                    main.removeCallbacks(retry)
                    main.removeCallbacks(repeatClear)
                    releaseConnectionListener()
                }
                render(force = changed)
                notifyWatchers()
                // Destek olmadığı bilinen araçta HUD yöneticisi çalışmaz/yoklama yapmaz.
                if (support != false) main.postDelayed(poll, if (support == true) 2_000L else 5_000L)
            }
        }
    }

    private fun syncSource() {
        val listen = running && isEnabled()
        if (listen == sourceListening) return
        sourceListening = listen
        if (listen) NavigationSource.addPackageListener(sourceListener) else {
            NavigationSource.removePackageListener(sourceListener)
            routes = emptyMap()
        }
    }

    private fun reconfigure() {
        generation++
        syncSource()
        previewUntil = 0L
        yandexForeground = NavigationState.NONE
        main.removeCallbacks(endPreview)
        render(force = true)
        GlobalBackService.refreshHudNavigation()
        notifyWatchers()
    }

    fun onYandexSnapshot(snapshot: YandexNavSnapshot) {
        val token = generation
        main.post {
            if (token != generation || !needsYandexScraping()) return@post
            yandexForeground = if (snapshot.guidanceActive) NavigationState(
                active = true,
                distance = NavParse.parseDistanceOnly(snapshot.formatManeuverLine()),
                street = snapshot.nextStreet?.takeIf { it.isNotBlank() } ?: snapshot.statusPanel,
                arrivalTime = snapshot.etaArrival,
                remainingDistance = NavParse.findDistance(snapshot.etaDistance),
                remainingMinutes = NavParse.parseMinutes(snapshot.etaTime),
                sourcePackage = NavigationSource.YANDEX_MAPS,
            ) else NavigationState.NONE
            render()
        }
    }

    fun showTest() {
        if (!running || !isEnabled()) return
        previewUntil = SystemClock.elapsedRealtime() + 10_000L
        main.removeCallbacks(endPreview)
        main.postDelayed(endPreview, 10_000L)
        render()
    }

    private fun render(force: Boolean = false) {
        val preview = SystemClock.elapsedRealtime() < previewUntil
        val source = provider()
        val accepted = when (source) {
            "google" -> setOf(NavigationSource.GOOGLE_MAPS)
            "yandex" -> setOf(NavigationSource.YANDEX_MAPS, NavigationSource.YANDEX_NAVI)
            else -> NavigationSource.PACKAGES
        }
        val route = if (preview) NavigationState.DEMO else hudRoute(latestNavigation(routes, accepted), yandexForeground, source)
        val enabled = running && isEnabled()
        val frame = if (enabled && (preview || NavigationSource.isListenerBound || yandexForeground.active)) NativeHudProtocol.encode(route,
            includeRawMetric = Settings.System.getInt(app.contentResolver, "com.desaysv.setting.unit", 0) == 0) else null
        val status = when {
            !requested -> R.string.hud_nav_off
            support == null -> R.string.hud_nav_check_support
            support == false -> R.string.hud_nav_not_supported
            !enabled -> R.string.hud_nav_off
            !preview && !NavigationSource.isListenerBound && !yandexForeground.active -> R.string.hud_nav_need_notifications
            frame == null -> R.string.hud_nav_waiting
            else -> R.string.hud_nav_showing
        }
        if (!force && initialized && desired == frame) {
            if (frame == null) updateStatus(status)
            return
        }
        initialized = true
        desired = frame
        desiredStatus = status
        version++
        main.removeCallbacks(retry)
        if (frame != null) main.removeCallbacks(repeatClear)
        if (!sending) dispatch()
    }

    /** İçerik değişimi çerçeveyi yarıda kesmez; sıradaki iş en güncel hedefi alır. */
    private fun dispatch() {
        sending = true
        val token = generation
        val revision = version
        val frame = desired
        if (frame != null) updateStatus(R.string.hud_nav_sending)
        worker.post {
            sendingGeneration = token
            val canSend = support == true && vehicle.hudSupport() == true && vehicle.connected() &&
                vehicle.powerMode()?.let { it > 0 } == true
            val success = if (token != generation) false else if (canSend) session.apply(frame) else {
                session.connectionLost()
                if (support == true && (frame != null || session.hasWork) && !vehicle.connected()) connect()
                frame == null && !session.hasWork
            }
            val repeat = session.needsRepeatClear
            val work = session.hasWork
            main.post result@{
                sending = false
                if (token != generation || revision != version) {
                    dispatch()
                    return@result
                }
                updateStatus(when {
                    frame == null -> desiredStatus
                    success -> R.string.hud_nav_showing
                    !vehicle.connected() -> R.string.hud_nav_need_vehicle
                    !canSend -> R.string.hud_nav_need_power
                    else -> R.string.hud_nav_send_error
                })
                if (!success && support == true) main.postDelayed(retry, 3_000L)
                if (repeat && support == true) main.postDelayed(repeatClear, 1_500L)
                if (!running && !work) releaseConnectionListener()
            }
        }
    }

    private fun sendPacket(packet: IntArray): Boolean =
        sendingGeneration == generation && support == true && vehicle.hudSupport() == true &&
            vehicle.connected() && vehicle.powerMode()?.let { it > 0 } == true && vehicle.send(packet)

    private fun connect() {
        val now = SystemClock.elapsedRealtime()
        if (connectAttemptAt?.let { now - it < 5_000L } == true) return
        connectAttemptAt = now
        try { vehicle.connect(app) } catch (e: RuntimeException) {
            Log.w("HudNavigation", "Araç HUD servisine bağlanılamadı", e)
        }
    }

    private fun dispatchRepeatClear() {
        val token = generation
        worker.post {
            sendingGeneration = token
            val success = token == generation && session.repeatClear()
            main.post result@{
                if (token != generation) return@result
                if (!success && support == true) main.postDelayed(retry, 3_000L)
                else if (success && !running) releaseConnectionListener()
            }
        }
    }

    private fun releaseConnectionListener() {
        if (!connectionListening) return
        vehicle.unlisten(connectionListener)
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
        @JvmStatic @Synchronized fun get(context: Context): HudNavigationManager =
            instance ?: HudNavigationManager(context, NativeHudVehicle()).also { instance = it }
    }
}
