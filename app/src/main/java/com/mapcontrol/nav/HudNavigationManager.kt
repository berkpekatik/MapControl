package com.mapcontrol.nav

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.desaysv.ivi.extra.project.carinfo.proxy.IServiceConnectListener
import com.mapcontrol.R
import com.mapcontrol.launcher.NavParse
import com.mapcontrol.launcher.NavigationSource
import com.mapcontrol.launcher.NavigationState
import com.mapcontrol.launcher.latestNavigation
import com.mapcontrol.service.GlobalBackService
import java.util.concurrent.CopyOnWriteArraySet

/** Ortak navigasyon kaynağından J7 yerleşik HUD arayüzüne aktarım. */
class HudNavigationManager @JvmOverloads internal constructor(
    context: Context,
    private val vehicle: HudVehicle,
    private val cleanupDelayMs: Long = 20_000L,
    private val blankDelayMs: Long = 50L,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val worker: Handler get() = NavigationSource.parsingHandler
    private val watchers = CopyOnWriteArraySet<Runnable>()
    private val session = NativeHudSession(
        initiallyOwned = prefs.getBoolean(OWNED, false),
        blankDelayMs = blankDelayMs,
        cleanupDelayMs = cleanupDelayMs,
        now = clock,
        persistOwnership = { owned -> prefs.edit().putBoolean(OWNED, owned).commit() },
        finish = { finishNavigation() },
        send = ::sendPacket,
    )

    @Volatile private var running = false
    @Volatile private var connected = false
    /** Donanım kanıtı: true var, false yok, null bilinmiyor. Gönderim yalnız true iken yapılır. */
    @Volatile private var support: Boolean? = null
    private var power: Int? = null
    @Volatile private var generation = 0
    private var sendingGeneration = 0
    private var sendingGuidance = false
    private var prefsListening = false
    private var connectionListening = false
    private var checking = false
    private var pollScheduled = false
    private var sourceListening = false
    private var initialized = false
    private var sending = false
    private var shutdownPending = false
    private val lastKnown = HudLastKnown(clock)
    private var previewUntil = 0L
    private var lastStatus = R.string.hud_nav_off
    private var connectAttemptAt: Long? = null
    private var routes: Map<String, NavigationState> = emptyMap()
    private var yandexForeground = NavigationState.NONE
    @Volatile private var desired: NativeHudFrame? = null
    @Volatile private var version = 0

    private val poll = Runnable { pollScheduled = false; checkVehicle() }
    private val retry = Runnable { dispatch() }
    private val sessionTimer = Runnable { dispatch() }
    private val endPreview = Runnable { previewUntil = 0L; render() }
    private val sourceListener: (Map<String, NavigationState>) -> Unit = {
        routes = it
        render()
    }
    private val connectionListener = IServiceConnectListener { value ->
        main.post {
            if (value == 0) {
                generation++
                connected = false
                worker.post { session.connectionLost() }
                render(force = true)
                ensureConnectionTracking()
                if (vehicleNeeded()) {
                    if (support == true) connect()
                    schedulePoll()
                }
            } else {
                checkVehicle()
            }
        }
    }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            ENABLED -> main.post {
                if (isPreferenceOn()) {
                    if (running) reconfigure() else start()
                } else {
                    beginShutdown()
                }
            }
            PROVIDER -> main.post { reconfigure() }
        }
    }

    fun isSupported(): Boolean = support == true
    /** Donanım yok diye doğrulanmadıkça (bilinmiyor dahil) kart görünür; gönderim yalnız destek doğruysa yapılır. */
    fun isKnownAbsent(): Boolean = support == false
    /** Kullanıcının kayıtlı tercihi; donanım doğrulanmasından bağımsız (arayüz anahtarı bunu gösterir). */
    fun isPreferenceOn(): Boolean = prefs.getBoolean(ENABLED, false)
    /** Aktarım gerçekten çalışabilir mi: servis çalışıyor, tercih açık, HUD donanımı doğrulanmış ve araç bağlı. */
    fun isEnabled(): Boolean = running && isPreferenceOn() && support == true && connected
    fun provider(): String = prefs.getString(PROVIDER, "auto") ?: "auto"
    fun status(): String = app.getString(lastStatus)
    fun needsYandexScraping(): Boolean =
        running && isPreferenceOn() && support == true && !shutdownPending && provider() != "google"

    fun setEnabled(value: Boolean) {
        val changed = isPreferenceOn() != value
        prefs.edit().putBoolean(ENABLED, value).apply()
        // Çalışırken tek geçişi tercih dinleyicisi yönetir; ikinci bir geçiş
        // aynı anda başlayan kareyi gereksiz yere iptal etmemeli.
        if (!prefsListening || !changed) {
            if (value) start() else beginShutdown()
        }
    }

    fun setProvider(value: String) {
        require(value in listOf("auto", "google", "yandex"))
        prefs.edit().putString(PROVIDER, value).apply()
    }

    fun addWatcher(watcher: Runnable) { watchers.add(watcher); watcher.run() }
    fun removeWatcher(watcher: Runnable) { watchers.remove(watcher) }

    /** Tercih ve bağlantı dinleyicileri donanım algılamasından bağımsız kaydedilir. */
    fun start() {
        running = true
        if (!prefsListening) {
            prefs.registerOnSharedPreferenceChangeListener(prefListener)
            prefsListening = true
        }
        if (isPreferenceOn()) shutdownPending = false
        syncSource()
        ensureConnectionTracking()
        schedulePoll()
        checkVehicle()
        render(force = true)
        GlobalBackService.refreshHudNavigation()
        notifyWatchers()
    }

    fun refresh() = start()

    fun stop() {
        running = false
        generation++
        if (prefsListening) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            prefsListening = false
        }
        syncSource()
        previewUntil = 0L
        yandexForeground = NavigationState.NONE
        lastKnown.reset()
        routes = emptyMap()
        main.removeCallbacks(endPreview)
        beginShutdown()
        GlobalBackService.refreshHudNavigation()
        notifyWatchers()
    }

    private fun beginShutdown() {
        generation++
        shutdownPending = prefs.getBoolean(OWNED, false)
        desired = null
        version++
        previewUntil = 0L
        yandexForeground = NavigationState.NONE
        lastKnown.reset()
        routes = emptyMap()
        main.removeCallbacks(endPreview)
        main.removeCallbacks(retry)
        main.removeCallbacks(sessionTimer)
        syncSource()
        ensureConnectionTracking()
        if (vehicleNeeded()) {
            dispatch()
            schedulePoll()
        } else {
            shutdownPending = false
            render(force = true)
        }
    }

    private fun syncSource() {
        val listen = running && isPreferenceOn() && support == true && !shutdownPending
        if (listen == sourceListening) return
        sourceListening = listen
        if (listen) NavigationSource.addPackageListener(sourceListener)
        else {
            NavigationSource.removePackageListener(sourceListener)
            routes = emptyMap()
        }
    }

    private fun reconfigure() {
        generation++
        shutdownPending = false
        previewUntil = 0L
        yandexForeground = NavigationState.NONE
        lastKnown.reset()
        main.removeCallbacks(endPreview)
        main.removeCallbacks(retry)
        main.removeCallbacks(sessionTimer)
        syncSource()
        ensureConnectionTracking()
        render(force = true)
        schedulePoll()
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
        if (!running || !isPreferenceOn() || shutdownPending) return
        previewUntil = clock() + 10_000L
        main.removeCallbacks(endPreview)
        main.postDelayed(endPreview, 10_000L)
        render(force = true)
    }

    private fun render(force: Boolean = false) {
        val preview = clock() < previewUntil
        val source = provider()
        val accepted = when (source) {
            "google" -> setOf(NavigationSource.GOOGLE_MAPS)
            "yandex" -> setOf(NavigationSource.YANDEX_MAPS, NavigationSource.YANDEX_NAVI)
            else -> NavigationSource.PACKAGES
        }
        // Google ve Yandex aynı yoldan geçer; geçici eksik alan (ok/mesafe/yol) son bilinen değerle tamamlanır.
        val route = if (preview) NavigationState.DEMO
            else lastKnown.merge(hudRoute(latestNavigation(routes, accepted), yandexForeground, source))
        val transfer = running && isPreferenceOn() && support == true && !shutdownPending
        val frame = if (transfer && (preview || NavigationSource.isListenerBound || yandexForeground.active)) {
            NativeHudProtocol.encode(route)
        } else null
        val status = statusFor(route, preview, frame, sending = false)
        if (!force && initialized && desired == frame) {
            if (frame == null || !hudReady() || !transfer) updateStatus(status)
            return
        }
        initialized = true
        desired = frame
        version++
        if (frame != null) main.removeCallbacks(sessionTimer)
        if (frame == null || !hudReady() || !transfer) updateStatus(status)
        if (!sending) dispatch()
        notifyWatchers()
    }

    private fun statusFor(
        route: NavigationState,
        preview: Boolean,
        frame: NativeHudFrame?,
        sending: Boolean,
    ): Int = when {
        !running -> R.string.hud_nav_off
        !isPreferenceOn() || shutdownPending -> R.string.hud_nav_off
        support == null -> R.string.hud_nav_check_support
        support == false -> R.string.hud_nav_not_supported
        !connected -> R.string.hud_nav_need_vehicle
        power?.let { it > 0 } != true -> R.string.hud_nav_need_power
        !preview && !NavigationSource.isListenerBound && !yandexForeground.active -> R.string.hud_nav_need_notifications
        frame == null -> R.string.hud_nav_waiting
        sending -> R.string.hud_nav_sending
        else -> R.string.hud_nav_showing
    }

    private fun checkVehicle() {
        if (checking) return
        checking = true
        cancelPoll()
        worker.post {
            // Config/güç okumaları UI iş parçacığına taşınmaz; bilinmeyen destek daha sonra tekrar okunur.
            val nextSupport = vehicle.hudSupport()
            val nextConnected = vehicle.connected()
            val nextPower = if (nextConnected) vehicle.powerMode() else null
            main.post result@{
                checking = false
                if (!running && !vehicleNeeded()) {
                    releaseConnectionTracking()
                    return@result
                }
                val changed = nextSupport != support || nextConnected != connected || nextPower != power
                if (changed) {
                    generation++
                    if (nextSupport != true || !nextConnected || nextPower?.let { it > 0 } != true) {
                        worker.post { session.connectionLost() }
                    }
                }
                val supportChanged = nextSupport != support
                support = nextSupport
                connected = nextConnected
                power = nextPower
                if (supportChanged) {
                    syncSource()
                    GlobalBackService.refreshHudNavigation()
                }
                if (support != true) {
                    main.removeCallbacks(retry)
                    main.removeCallbacks(sessionTimer)
                }
                ensureConnectionTracking()
                if (!connected && support == true && vehicleNeeded()) connect()
                if (running || shutdownPending) render(force = changed)
                if (vehicleNeeded()) schedulePoll() else {
                    releaseConnectionTracking()
                    cancelPoll()
                }
                notifyWatchers()
            }
        }
    }

    /** İçerik değişimi çerçeveyi yarıda kesmez; sıradaki iş en güncel hedefi alır. */
    private fun dispatch() {
        if (sending) return
        sending = true
        val token = generation
        val revision = version
        val frame = desired
        val shutdown = shutdownPending || !running || !isPreferenceOn()
        val preview = clock() < previewUntil
        if (frame != null && !shutdown && connected) updateStatus(R.string.hud_nav_sending)
        worker.post {
            sendingGeneration = token
            sendingGuidance = frame != null && !shutdown
            var success = false
            var canSend = false
            if (token == generation) {
                val connectedNow = vehicle.connected()
                canSend = hudSendable()
                if (!canSend) {
                    // HUD doğrulanmadı, araç bağlı değil ya da güç kapalı: hiçbir paket gönderilmez.
                    session.connectionLost()
                    if (support == true && (frame != null || session.hasWork) && !connectedNow) connect()
                    success = frame == null && !session.hasWork
                } else {
                    val applied = when {
                        shutdown -> session.shutdown()
                        frame != null -> session.apply(frame)
                        else -> session.apply(null)
                    }
                    success = applied && session.tick()
                }
            }
            val work = session.hasWork
            val delay = session.nextActionDelayMs()
            main.post result@{
                sending = false
                if (token != generation || revision != version) {
                    dispatch()
                    return@result
                }
                if (shutdown && success && !work) {
                    shutdownPending = false
                    main.removeCallbacks(retry)
                    main.removeCallbacks(sessionTimer)
                }
                val status = statusFor(
                    NavigationState.NONE,
                    preview,
                    if (shutdown) null else frame,
                    sending = false,
                )
                updateStatus(if (!success && frame != null && !shutdown && support == true) when {
                    !vehicle.connected() -> R.string.hud_nav_need_vehicle
                    !canSend -> R.string.hud_nav_need_power
                    else -> R.string.hud_nav_send_error
                } else status)
                if (!success && support == true && !shutdown) main.postDelayed(retry, 3_000L)
                scheduleSession(delay, success)
                ensureConnectionTracking()
                if (vehicleNeeded()) schedulePoll() else {
                    releaseConnectionTracking()
                    cancelPoll()
                }
                notifyWatchers()
            }
        }
    }

    private fun scheduleSession(delay: Long?, success: Boolean) {
        main.removeCallbacks(sessionTimer)
        if (delay == null) return
        val wait = if (!success && delay <= 0L) 3_000L else delay.coerceAtLeast(0L)
        main.postDelayed(sessionTimer, wait)
    }

    private fun schedulePoll() {
        if (!vehicleNeeded()) {
            cancelPoll()
        } else if (!pollScheduled) {
            // Her yeni rota kontrolü ötelememeli; bağlantı yokken bile tekrar denemek gerekir.
            pollScheduled = true
            main.postDelayed(poll, if (support == true) 2_000L else 5_000L)
        }
    }

    private fun cancelPoll() {
        main.removeCallbacks(poll)
        pollScheduled = false
    }

    /** Donanım doğrulandı, araç bağlı ve güç açık mı; her paketten önce yeniden okunur. */
    private fun hudSendable(): Boolean =
        support == true && vehicle.hudSupport() == true && vehicle.connected() &&
            vehicle.powerMode()?.let { it > 0 } == true

    private fun hudReady(): Boolean = support == true && connected && power?.let { it > 0 } == true

    private fun sendPacket(packet: IntArray): Boolean =
        sendingGeneration == generation && hudSendable() &&
            (!sendingGuidance || (running && isPreferenceOn())) && vehicle.send(packet)

    private fun finishNavigation(): Boolean =
        sendingGeneration == generation && hudSendable() && vehicle.stopNavigation()

    private fun connect() {
        val now = SystemClock.elapsedRealtime()
        if (connectAttemptAt?.let { now - it < 5_000L } == true) return
        connectAttemptAt = now
        try {
            vehicle.connect(app)
        } catch (e: RuntimeException) {
            Log.w("HudNavigation", "Araç HUD servisine bağlanılamadı", e)
        }
    }

    private fun ensureConnectionTracking() {
        if (vehicleNeeded()) {
            if (!connectionListening) {
                vehicle.listen(connectionListener)
                connectionListening = true
            }
        } else {
            releaseConnectionTracking()
        }
    }

    private fun releaseConnectionTracking() {
        if (!connectionListening) return
        vehicle.unlisten(connectionListener)
        connectionListening = false
    }

    /** Destek yok diye doğrulanan araçta dinleyici ve yoklama çalışmaz. */
    private fun vehicleNeeded(): Boolean = support != false && (
        (running && (isPreferenceOn() || prefs.getBoolean(OWNED, false))) ||
            shutdownPending || prefs.getBoolean(OWNED, false))

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
            instance ?: HudNavigationManager(context, NativeHudVehicle(context)).also { instance = it }
    }
}
