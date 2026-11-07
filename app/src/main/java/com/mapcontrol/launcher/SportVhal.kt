package com.mapcontrol.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.desaysv.ivi.vdb.client.VDBus
import com.desaysv.ivi.vdb.client.bind.VDThreadType
import com.desaysv.ivi.vdb.client.listener.VDNotifyListener
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.id.vehicle.VDKeyVehicleHal
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * DesaySV VDBus VEHICLE_HAL servisinin spor ekranı olayları (saf kısım; JVM'de sınanır).
 *
 * VDBus VEHICLE_HAL servisi 0..65535 aralığını taşır (OEM `VDServiceDef.createServiceList`) ve olay kimliği VHAL özellik
 * kimliğinin alt 16 bitidir: PERF_VEHICLE_SPEED 0x11600207 → 519, GEAR_SELECTION 0x11400400 → 1024, IGNITION_STATE
 * 0x11400409 → 1033, PARKING_BRAKE_ON 0x11200402 → 1026 (OEM `VDEventVehicleHal`). Değer `DoubleVector` içinde gelir;
 * OEM 519'u m/s × 3,6 = km/sa okur.
 *
 * ENGINE_RPM 0x11600305 (`android.car.VehiclePropertyIds.ENGINE_RPM = 291504901`) aynı kurala göre 773 olur. Bunu hiçbir
 * OEM uygulaması kullanmıyor; bu yüzden 773 gösterime ancak taze geliyorsa ve bir kez ReadOnly 62 ile uyuştuysa ([agrees]) girer.
 */
internal object SportVhalMath {
    /** VDEventVehicleHal.PERF_VEHICLE_SPEED. */
    const val SPEED_ID = 519
    /** ENGINE_RPM alt 16 bit (çıkarım; OEM listesinde yok). */
    const val RPM_ID = 773
    const val MPS_TO_KMH = 3.6
    const val MAX_KMH = 300f
    const val MAX_RPM = 9_000
    /** Bu süre olay gelmezse kaynak bayat sayılır. */
    const val FRESH_MS = 1_500L
    /** 773 ile 62 uyuşması: en az bu kadar dev/dk ya da 62'nin [AGREE_RATIO] oranı fark. */
    const val AGREE_RPM = 300
    const val AGREE_RATIO = 0.25

    /** 519 yükü (m/s) → km/sa; geçersizse null. */
    fun speedKmh(v: DoubleArray?): Float? {
        val d = v?.firstOrNull() ?: return null
        if (d.isNaN() || d.isInfinite()) return null
        val k = (abs(d) * MPS_TO_KMH).toFloat()
        return k.takeIf { it <= MAX_KMH }
    }

    /** 773 yükü (dev/dk) → tam sayı; geçersiz ya da aralık dışıysa null. */
    fun rpm(v: DoubleArray?): Int? {
        val d = v?.firstOrNull() ?: return null
        if (d.isNaN() || d.isInfinite() || d < 0 || d > MAX_RPM) return null
        return d.roundToInt()
    }

    /** 773 ve 62 aynı şeyi mi ölçüyor (birim/ölçek doğrulaması). */
    fun agrees(vhal: Int, readOnly: Int): Boolean = abs(vhal - readOnly) <= max(AGREE_RPM, (readOnly * AGREE_RATIO).toInt())

    enum class RpmSource(val key: String) { VHAL("773"), READ_ONLY("62"), NONE("yok") }

    data class RpmPick(val rpm: Int?, val source: RpmSource, /** VHAL'dan geldiyse varış anı (tazelik için). */ val arrivedAt: Long?)

    /**
     * Gösterilecek devir: 773 taze ([FRESH_MS]) ve doğrulanmışsa ([trusted]) o, yoksa ReadOnly 62 (normal veri katmanı).
     */
    fun chooseRpm(now: Long, vhalRpm: Int?, vhalAt: Long, trusted: Boolean, readOnly: Int?): RpmPick = when {
        trusted && vhalRpm != null && now - vhalAt < FRESH_MS -> RpmPick(vhalRpm, RpmSource.VHAL, vhalAt)
        readOnly != null -> RpmPick(readOnly, RpmSource.READ_ONLY, null)
        else -> RpmPick(null, RpmSource.NONE, null)
    }
}

/**
 * Spor ekranı görünürken VDBus VEHICLE_HAL 519 (gerçek hız, CAR_SPEED izni olmadan da) ve 773 (devir adayı) aboneliği.
 * Olaylar VDBus dağıtım iş parçacığında gelir; hız [SpeedPostGate] bandından çok değişince ana iş parçacığına tek bekleyen
 * [onSpeedChange] gönderilir. Görünmezken abonelik kaldırılır (sürekli 10 Hz binder trafiği olmasın).
 */
internal class VhalSportReader(
    context: Context,
    /** Asıl hız yolu (CAR_SPEED) canlı mı; canlıyken 519 ana iş parçacığını uyandırmaz (VDBus iş parçacığında okunur, volatile alanlar). */
    private val primaryLive: () -> Boolean = { false },
    private val onSpeedChange: Runnable,
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val pending = AtomicBoolean(false)
    private val gate = SpeedPostGate()
    @Volatile private var running = false
    @Volatile private var bus: VDBus? = null

    @Volatile var speedKmh: Float = -1f
        private set
    @Volatile private var speedAt = 0L
    @Volatile var rpm: Int? = null
        private set
    @Volatile var rpmAt = 0L
        private set


    /** 519 canlı mı (abone ve son olay [SportVhalMath.FRESH_MS] içinde). */
    val speedLive: Boolean get() = running && speedKmh >= 0f && SystemClock.elapsedRealtime() - speedAt < SportVhalMath.FRESH_MS

    private val fire = Runnable {
        pending.set(false)
        if (running) onSpeedChange.run()
    }

    private val listener = VDNotifyListener { e, _ ->
        if (e == null || !running) return@VDNotifyListener
        when (e.id) {
            SportVhalMath.SPEED_ID -> onSpeed(e)
            SportVhalMath.RPM_ID -> onRpm(e)
        }
    }

    private fun doubles(e: VDEvent): DoubleArray? = try {
        e.payload?.getDoubleArray(VDKeyVehicleHal.DOUBLE_VECTOR)
    } catch (t: Throwable) {
        null
    }

    private fun onSpeed(e: VDEvent) {
        val kmh = SportVhalMath.speedKmh(doubles(e)) ?: return
        val now = SystemClock.elapsedRealtime()
        speedAt = now
        speedKmh = kmh
        if (!primaryLive() && gate.shouldPost(kmh) && pending.compareAndSet(false, true)) main.post(fire)
    }

    private fun onRpm(e: VDEvent) {
        val v = SportVhalMath.rpm(doubles(e)) ?: return
        val now = SystemClock.elapsedRealtime()
        rpmAt = now
        rpm = v
    }

    /** Ana iş parçacığında: VDBus init (diğer VDBus kullanıcıları gibi), abonelik "sport-io"da. */
    fun start() {
        if (running) return
        running = true
        gate.reset()
        val b = bus ?: try {
            VDBus.getDefault().also { it.init(app) }.also { bus = it }
        } catch (t: Throwable) {
            Log.w(TAG, "VDBus başlatılamadı: ${t.javaClass.simpleName}")
            null
        } ?: return
        CarSpeedReader.io.post { if (running) subscribe(b) }
    }

    fun stop() {
        if (!running) return
        running = false
        main.removeCallbacks(fire)
        pending.set(false)
        speedKmh = -1f
        rpm = null
        val b = bus ?: return
        CarSpeedReader.io.post { if (!running) unsubscribe(b) }
    }

    /** "sport-io": standart abonelik (addSubscribe + subscribeCommit + dinleyici; OEM VehiclehalManager ile aynı). */
    private fun subscribe(b: VDBus) {
        if (subscribed) return
        try {
            b.registerVDNotifyListener(listener)
            b.addSubscribe(SportVhalMath.SPEED_ID, VDThreadType.CHILD_THREAD)
            b.addSubscribe(SportVhalMath.RPM_ID, VDThreadType.CHILD_THREAD)
            b.subscribeCommit()
            subscribed = true
        } catch (t: Throwable) {
            Log.w(TAG, "519/773 aboneliği başarısız: ${t.javaClass.simpleName}")
            return
        }
    }

    private fun unsubscribe(b: VDBus) {
        if (!subscribed) return
        subscribed = false
        try {
            b.removeSubscribe(SportVhalMath.SPEED_ID)
            b.removeSubscribe(SportVhalMath.RPM_ID)
            b.subscribeCommit()
            b.unregisterVDNotifyListener(listener)
        } catch (t: Throwable) {
            Log.w(TAG, "519/773 abonelik kaldırma: ${t.javaClass.simpleName}")
        }
    }

    /** Yalnız "sport-io"da. */
    private var subscribed = false

    companion object {
        const val TAG = "SportRpm"
    }
}
