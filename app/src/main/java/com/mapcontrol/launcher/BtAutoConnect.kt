package com.mapcontrol.launcher

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log

/** Otomatik bağlanma denemesinin bir adımı. */
internal sealed class BtAutoStep {
    /** Bağdaştırıcı henüz hazır değil: sonraki denemede yeniden bakılır. */
    object Wait : BtAutoStep()
    /** Yapılacak bir şey kalmadı: bir cihaz bağlı ya da bağlanacak cihaz yok. */
    object Done : BtAutoStep()
    data class Connect(val address: String) : BtAutoStep()
}

/** Otomatik bağlanmanın saf kararları (JVM'de sınanır). */
internal object BtAutoConnectPolicy {
    /** Araç çalıştıktan sonraki denemelerin aralıkları: Bluetooth ve telefon geç hazır olabilir, toplam yaklaşık 3 dakika. */
    val ATTEMPT_DELAYS_MS = longArrayOf(8_000L, 10_000L, 15_000L, 20_000L, 30_000L, 30_000L, 30_000L, 30_000L)

    /** Son bağlanan cihaz hâlâ eşleşmişse o; kayıt yoksa ve tek eşleşmiş telefon varsa o; aksi halde null (yanlış telefona bağlanılmaz). */
    fun target(devices: List<BtDevice>, lastAddress: String?): String? {
        if (lastAddress != null) devices.firstOrNull { it.address.equals(lastAddress, true) }?.let { return it.address }
        return devices.filter { it.kind == BtKind.PHONE }.singleOrNull()?.address
    }

    /** Herhangi bir cihaz bağlıysa durur: bağlı başka bir telefonun bağlantısı alınmaz. */
    fun next(adapterOn: Boolean, canRead: Boolean, devices: List<BtDevice>, lastAddress: String?): BtAutoStep = when {
        !canRead -> BtAutoStep.Done
        !adapterOn -> BtAutoStep.Wait
        devices.any { it.connected } -> BtAutoStep.Done
        else -> target(devices, lastAddress)?.let { BtAutoStep.Connect(it) } ?: BtAutoStep.Done
    }

    /** Sonraki denemenin gecikmesi; denemeler bittiyse null. */
    fun delayAfter(index: Int): Long? = ATTEMPT_DELAYS_MS.getOrNull(index + 1)
}

/** Otomatik bağlanma tercihleri: açık/kapalı (varsayılan kapalı) ve son bağlanan cihaz. */
internal object BtAutoConnectPrefs {
    private const val PREFS_NAME = "launcher_bt_auto"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_ADDRESS = "last_address"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
    fun lastAddress(context: Context): String? = prefs(context).getString(KEY_LAST_ADDRESS, null)
    fun setLastAddress(context: Context, address: String) = prefs(context).edit().putString(KEY_LAST_ADDRESS, address).apply()
}

/**
 * Araç çalışınca (güç modu 2) son bağlanan telefona sınırlı sayıda bağlanmayı dener; tercih kapalıyken yalnızca son cihazı
 * öğrenir. Kullanıcı panelden bağlantıyı keserse araç kapanana (güç modu 0) kadar denemez. Sürüş sırasında kopan bağlantıyı
 * kovalamaz: denemeler yalnızca çalışma anında başlar. Durum ana iş parçacığındadır; gizli Bluetooth çağrıları ayrı iş parçacığında.
 */
object BtAutoConnect {
    private const val TAG = "BtAutoConnect"
    private const val POWER_OFF = 0
    private const val POWER_ON = 2
    private const val IDLE = -1

    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var backend: AndroidBluetoothBackend? = null
    /** Sıradaki denemenin sırası; [IDLE] ise döngü çalışmıyor. */
    private var index = IDLE
    private var userDisconnected = false
    private val attemptTask = Runnable { attempt() }

    private val tracker = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) != BluetoothProfile.STATE_CONNECTED) return
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            BtAutoConnectPrefs.setLastAddress(context, device.address)
        }
    }

    /** Son bağlanan telefonu izlemeye başlar; servis her açılışta çağırır, tekrar çağrı etkisizdir. */
    @JvmStatic
    fun start(context: Context) {
        val ctx = context.applicationContext
        main.post {
            if (app != null) return@post
            app = ctx
            backend = AndroidBluetoothBackend(ctx)
            // Araç ünitesi telefona alıcı olarak bağlanır (HFP istemcisi, A2DP alıcısı).
            ctx.registerReceiver(tracker, IntentFilter().apply {
                addAction(BluetoothStatus.ACTION_HFP_CLIENT_STATE)
                addAction(BluetoothStatus.ACTION_A2DP_SINK_STATE)
            })
        }
    }

    /** Servisin güç modu izleyicisi değişimde çağırır (arka plan iş parçacığından). */
    @JvmStatic
    fun onPowerMode(mode: Int) {
        main.post {
            if (mode == POWER_OFF) userDisconnected = false
            if (mode == POWER_ON) begin() else cancel()
        }
    }

    /** Kullanıcı panelden bağlantıyı kesti: araç kapanana kadar otomatik bağlanma yok. */
    internal fun noteUserDisconnect() {
        main.post { userDisconnected = true; cancel() }
    }

    /** Tercih kapatıldı: süren denemeler durur. */
    internal fun cancel() {
        main.post {
            if (index == IDLE) return@post
            index = IDLE
            main.removeCallbacks(attemptTask)
            backend?.close()
        }
    }

    private fun begin() {
        val ctx = app ?: return
        val be = backend ?: return
        if (index != IDLE || userDisconnected || !BtAutoConnectPrefs.enabled(ctx)) return
        index = 0
        be.open {}
        main.postDelayed(attemptTask, BtAutoConnectPolicy.ATTEMPT_DELAYS_MS[0])
    }

    private fun attempt() {
        val ctx = app ?: return
        val be = backend ?: return
        val current = index
        if (current == IDLE) return
        Thread({
            val step = try {
                val readable = be.canRead()
                val devices = if (readable) be.devices() else emptyList()
                // Servis başlamadan önce kurulmuş bağlantının yayını kaçmış olabilir: bağlı cihaz son cihaz olarak kaydedilir.
                devices.firstOrNull { it.connected }?.let { BtAutoConnectPrefs.setLastAddress(ctx, it.address) }
                BtAutoConnectPolicy.next(be.isEnabled(), readable, devices, BtAutoConnectPrefs.lastAddress(ctx)).also {
                    if (it is BtAutoStep.Connect) Log.i(TAG, "deneme ${current + 1}: ${be.connect(it.address)}")
                }
            } catch (e: RuntimeException) {
                Log.w(TAG, "deneme başarısız: ${e.javaClass.simpleName}")
                BtAutoStep.Wait
            }
            main.post { onAttempted(current, step) }
        }, "bt-auto-connect").start()
    }

    private fun onAttempted(attempted: Int, step: BtAutoStep) {
        if (index != attempted) return
        val delay = if (step == BtAutoStep.Done) null else BtAutoConnectPolicy.delayAfter(attempted)
        if (delay == null) {
            index = IDLE
            backend?.close()
            return
        }
        index = attempted + 1
        main.postDelayed(attemptTask, delay)
    }
}
