package com.mapcontrol.launcher

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.lang.reflect.InvocationTargetException

/**
 * Cihazın Bluetooth'u: eşleşmiş cihazlar, profil vekilleri (panel açıkken) ve gizli `connect`/`disconnect`.
 * Bağlanma/kesme gizli API'dir; Android 11'de çoğunlukla BLUETOOTH_PRIVILEGED ister (tam sürümde FrostNav'ın adb izinleriyle
 * gelebilir). Yoksa false döner ya da SecurityException atar; ikisi de yakalanır, panel yedek yolu önerir. Hiçbir çağrı çökmez.
 */
@Suppress("DEPRECATION")
internal class AndroidBluetoothBackend(context: Context) : BluetoothBackend {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? = try { BluetoothAdapter.getDefaultAdapter() } catch (e: RuntimeException) { null }
    /** Bağlanan profil vekilleri (kimlik → vekil); yalnızca ana iş parçacığında değişir, okumada kopyalanır. */
    @Volatile private var proxies: Map<Int, BluetoothProfile> = emptyMap()
    private var listening = false
    private var onChange: () -> Unit = {}

    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            main.post {
                if (!listening || proxy == null) { proxy?.let { closeProxy(profile, it) }; return@post }
                proxies = proxies + (profile to proxy)
                onChange()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            main.post {
                proxies = proxies - profile
                if (listening) onChange()
            }
        }
    }

    override val present: Boolean get() = adapter != null

    override fun isEnabled(): Boolean = try { adapter?.isEnabled == true } catch (e: RuntimeException) { false }

    override fun canToggle(): Boolean = adapter != null && granted(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH_ADMIN)

    override fun canRead(): Boolean = adapter != null && granted(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH)

    private fun granted(p: String) = try { app.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED } catch (e: RuntimeException) { false }

    override fun setEnabled(on: Boolean, done: () -> Unit) {
        Thread({
            try { if (on) adapter?.enable() else adapter?.disable() } catch (e: RuntimeException) { }
            main.postDelayed(done, 800L)
        }, "bt-radio").start()
    }

    override fun devices(): List<BtDevice> {
        val a = adapter ?: return emptyList()
        val bonded = try { a.bondedDevices?.toList() } catch (e: RuntimeException) { null } ?: return emptyList()
        val px = proxies
        return bonded.map { d ->
            var phone = false
            var audio = false
            for ((id, proxy) in px) {
                val on = try { proxy.getConnectionState(d) == BluetoothProfile.STATE_CONNECTED } catch (e: RuntimeException) { false }
                if (!on) continue
                if (BtListShaper.isPhoneProfile(id)) phone = true
                if (BtListShaper.isAudioProfile(id)) audio = true
            }
            BtDevice(
                address = d.address,
                name = try { d.name } catch (e: RuntimeException) { null },
                kind = try { BtListShaper.kind(d.bluetoothClass?.majorDeviceClass ?: 0) } catch (e: RuntimeException) { BtKind.OTHER },
                phone = phone,
                audio = audio,
            )
        }
    }

    override fun open(onChange: () -> Unit) {
        this.onChange = onChange
        if (listening) return
        listening = true
        val a = adapter ?: return
        for (id in BtListShaper.PROFILES) {
            try { a.getProfileProxy(app, listener, id) } catch (e: RuntimeException) { }
        }
    }

    override fun close() {
        listening = false
        onChange = {}
        val px = proxies
        proxies = emptyMap()
        px.forEach { (id, p) -> closeProxy(id, p) }
    }

    private fun closeProxy(id: Int, p: BluetoothProfile) {
        try { adapter?.closeProfileProxy(id, p) } catch (e: RuntimeException) { }
    }

    override fun connect(address: String): BtOutcome = call(address, "connect")

    override fun disconnect(address: String): BtOutcome = call(address, "disconnect")

    /** Bağlı profil vekillerinin her birinde gizli [method]`(BluetoothDevice)`; hepsi denenir (telefon ve ses ayrı profillerdir). */
    private fun call(address: String, method: String): BtOutcome {
        val a = adapter ?: return BtOutcome.FAILED
        val device = try { a.getRemoteDevice(address) } catch (e: RuntimeException) { return BtOutcome.FAILED }
        val px = proxies
        if (px.isEmpty()) return BtOutcome.FAILED
        val calls = BtListShaper.PROFILES.mapNotNull { id -> px[id]?.let { invoke(it, method, device) } }
        return BtListShaper.outcome(calls)
    }

    private fun invoke(proxy: BluetoothProfile, method: String, device: BluetoothDevice): BtCall = try {
        val m = proxy.javaClass.getMethod(method, BluetoothDevice::class.java)
        if (m.invoke(proxy, device) == true) BtCall.OK else BtCall.FAILED
    } catch (e: InvocationTargetException) {
        if (e.targetException is SecurityException) BtCall.DENIED else BtCall.FAILED
    } catch (e: SecurityException) {
        BtCall.DENIED
    } catch (e: ReflectiveOperationException) {
        BtCall.UNSUPPORTED
    } catch (e: RuntimeException) {
        BtCall.FAILED
    }
}

/** OEM Bluetooth telefon uygulaması varsa onu, yoksa sistem Bluetooth ayarlarını açar; ikisi de yoksa bir şey yapmaz. */
internal fun openOemBluetooth(context: Context) {
    val pm = context.packageManager
    val intent = pm.getLaunchIntentForPackage(OEM_PHONE_PACKAGE)
        ?: Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).takeIf { it.resolveActivity(pm) != null }
        ?: return
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: RuntimeException) {
        // Açılamadı: sessizce vazgeçilir.
    }
}

internal const val OEM_PHONE_PACKAGE = "com.desaysv.bluetooth.phone"
