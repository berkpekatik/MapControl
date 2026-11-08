package com.mapcontrol.launcher

import android.car.Car
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Spor hız kadranının hızlı kaynağı: android.car [CarPropertyManager] PERF_VEHICLE_SPEED (m/s, gerçek hız) 10 Hz.
 * İzin `android.car.permission.CAR_SPEED` (tehlikeli, verilebilir; araçta doğrulandı). Olaylar ana iş parçacığına değil ortak
 * "sport-io" iş parçacığına gelir; hız [SpeedPostGate] bandından çok değişince ana iş parçacığına tek bekleyen [onChange]
 * gönderilir (dururken gelen 0 olayları ana iş parçacığını uyandırmaz).
 *
 * Kaynak yoksa (izin verilmedi, android.car kitaplığı yok, kayıt reddedildi ya da [FRESH_MS] olay gelmedi) [live] false;
 * kadran önce VDBus VEHICLE_HAL 519 ([VhalSportReader], izin gerektirmez), o da yoksa
 * normal veri katmanındaki gösterge hızına (ReadOnly 119, [com.mapcontrol.vehicle.VehicleMetricsRepository]
 * aboneliği, değişince) döner. Yoklama yok: 519/773 VDBus aboneliğiyle gelir (eski SportFastReader kaldırıldı).
 *
 * android.car sınıfları yalnız [CarLink] içinde: kitaplık araçta yoksa sınıf yüklenemez, hata yakalanır, yedek sürer.
 */
internal class CarSpeedReader(context: Context, private val onChange: Runnable) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val pending = AtomicBoolean(false)
    private val gate = SpeedPostGate()
    @Volatile private var running = false
    @Volatile private var lastEventMs = 0L

    /** Son gerçek hız (km/sa); yoksa -1. */
    @Volatile var actualKmh: Float = -1f
        private set
    @Volatile var state: State = State.IDLE
        private set

    /** Ölçüm sayaçları (yalnız sayı; [SportSpeedLog]). */
    val events = AtomicInteger()
    val posts = AtomicInteger()

    enum class State(val key: String) { IDLE("bekliyor"), NO_PERMISSION("izin-yok"), UNAVAILABLE("yok"), LIVE("car") }

    /** Hızlı hız canlı mı (kayıtlı ve son olay [FRESH_MS] içinde). */
    val live: Boolean get() = running && state == State.LIVE && actualKmh >= 0f && SystemClock.elapsedRealtime() - lastEventMs < FRESH_MS

    private val fire = Runnable {
        pending.set(false)
        if (running) onChange.run()
    }

    /** "sport-io" iş parçacığında. */
    private fun onSpeed(kmh: Float) {
        if (!running) return
        events.incrementAndGet()
        lastEventMs = SystemClock.elapsedRealtime()
        actualKmh = kmh
        if (gate.shouldPost(kmh) && pending.compareAndSet(false, true)) {
            posts.incrementAndGet()
            main.post(fire)
        }
    }

    fun start() {
        if (running) return
        running = true
        gate.reset()
        active = this
        io.post { connect() }
    }

    fun stop() {
        if (!running) return
        running = false
        if (active === this) active = null
        main.removeCallbacks(fire)
        pending.set(false)
        actualKmh = -1f
        io.post { if (!running) link?.unregister() }
    }

    /** "sport-io" iş parçacığında: izin, bağlantı ve kayıt (binder çağrıları ana iş parçacığında değil). */
    private fun connect() {
        if (!running) return
        if (!hasPermission(app)) { setState(State.NO_PERMISSION); return }
        val l = link ?: try {
            CarLink(app, io).also { link = it }
        } catch (t: Throwable) {
            // NoClassDefFoundError (kitaplık yok), servis yok ya da güvenlik hatası.
            Log.w(SportSpeedLog.TAG, "android.car bağlanamadı: ${t.javaClass.simpleName}")
            setState(State.UNAVAILABLE)
            return
        }
        val ok = try { l.register { onSpeed(it) } } catch (t: Throwable) {
            Log.w(SportSpeedLog.TAG, "PERF_VEHICLE_SPEED kaydı başarısız: ${t.javaClass.simpleName}")
            false
        }
        setState(if (ok) State.LIVE else State.UNAVAILABLE)
    }

    private fun setState(s: State) {
        if (state == s) return
        state = s
        Log.i(SportSpeedLog.TAG, when (s) {
            State.LIVE -> "kaynak=car (PERF_VEHICLE_SPEED ${RATE_HZ.toInt()} Hz)"
            State.NO_PERMISSION -> "kaynak=vdbus (CAR_SPEED izni yok; VDBus 519, sonra ReadOnly 119 yedek)"
            State.UNAVAILABLE -> "kaynak=vdbus (android.car hız yok; VDBus 519, sonra ReadOnly 119 yedek)"
            State.IDLE -> "bekliyor"
        })
    }

    /** android.car'a dokunan tek yer (kitaplık yoksa bu sınıf yüklenemez). */
    private class CarLink(context: Context, handler: Handler) {
        private val car: Car = Car.createCar(context, handler) ?: throw IllegalStateException("Car null")
        private val mgr = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        @Volatile private var sink: ((Float) -> Unit)? = null
        private var registered = false
        private val cb = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>?) {
                val v = value ?: return
                if (v.propertyId != PERF_VEHICLE_SPEED || v.status != CarPropertyValue.STATUS_AVAILABLE) return
                val mps = (v.value as? Float) ?: return
                sink?.invoke(abs(mps) * MPS_TO_KMH)
            }

            override fun onErrorEvent(propId: Int, zone: Int) {}
        }

        fun register(to: (Float) -> Unit): Boolean {
            sink = to
            if (registered) return true
            registered = mgr.registerCallback(cb, PERF_VEHICLE_SPEED, RATE_HZ)
            return registered
        }

        fun unregister() {
            sink = null
            if (!registered) return
            registered = false
            try { mgr.unregisterCallback(cb) } catch (_: Throwable) {}
        }
    }

    companion object {
        const val PERMISSION = "android.car.permission.CAR_SPEED"
        /** VehiclePropertyIds.PERF_VEHICLE_SPEED (API 29'da da aynı). */
        const val PERF_VEHICLE_SPEED = 291504647
        const val RATE_HZ = 10f
        const val MPS_TO_KMH = 3.6f
        /** Bu süre olay gelmezse yedek kaynağa dönülür. */
        const val FRESH_MS = 1_500L
        const val REQ_PERMISSION = 7_410

        private val ioThread = HandlerThread("sport-io").apply { isDaemon = true; start() }
        /** Hız olayları ve G sensörü burada işlenir (ana iş parçacığı değil). */
        val io = Handler(ioThread.looper)
        /** Yalnız [io] iş parçacığında; süreç boyunca bir bağlantı (kayıt görünürken). */
        private var link: CarLink? = null
        @Volatile private var active: CarSpeedReader? = null

        fun hasPermission(context: Context): Boolean =
            context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

        /** İzin sonucu geldi (HomeLauncherActivity): görünür okuyucu yeniden bağlanır. */
        fun onPermissionResult() {
            val r = active ?: return
            io.post { if (r.running && r.state != State.LIVE) r.connect() }
        }
    }
}

/**
 * Spor ekranı ölçüm günlüğü (`adb logcat -s SportSpeed`): kaynak değişimleri her zaman; görünürken 10 sn'de bir özet
 * (hız kaynağı, olay ve ana iş parçacığı haber sayısı, ibre hareketi, süreç CPU yüzdesi). Yalnız sayılar. Özet debug
 * derlemede varsayılan açık; `--ez launcher_speed_log false` kapatır.
 */
internal object SportSpeedLog {
    const val EXTRA = "launcher_speed_log"
    const val TAG = "SportSpeed"
    const val SUMMARY_MS = 10_000L

    @Volatile var enabled = true
        private set
    @Volatile private var debuggable = false

    fun applyFrom(context: Context, intent: Intent?) {
        debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        enabled = debuggable && (intent?.getBooleanExtra(EXTRA, true) ?: true)
        if (intent?.hasExtra(EXTRA) == true) Log.i(TAG, if (enabled) "özet açık" else "özet kapalı")
    }

    /**
     * Özet satırı. [cpuMs] pencerede sürecin harcadığı CPU (tüm iş parçacıkları); yüzde tek çekirdeğe göre (top ile aynı).
     */
    fun summaryLine(windowMs: Long, source: String, events: Int, posts: Int, needleMoves: Int, kmh: Int, rpmShown: Boolean, cpuMs: Long): String {
        val w = windowMs.coerceAtLeast(1)
        val cpu = String.format(java.util.Locale.US, "%.1f", cpuMs * 100.0 / w)
        val rate = String.format(java.util.Locale.US, "%.1f", events * 1000.0 / w)
        return "özet ${w}ms kaynak=$source olay=$events (${rate}/sn) anaİş=$posts ibre=$needleMoves hız=${if (kmh < 0) "-" else kmh} " +
            "devir=${if (rpmShown) "görünür" else "gizli"} cpu=$cpu%"
    }
}
