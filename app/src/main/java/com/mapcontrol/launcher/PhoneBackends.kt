package com.mapcontrol.launcher

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.os.Process
import android.os.SystemClock
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.desaysv.ivi.vdb.client.VDBus
import com.desaysv.ivi.vdb.client.bind.VDServiceDef
import com.desaysv.ivi.vdb.client.bind.VDThreadType
import com.desaysv.ivi.vdb.client.listener.VDBindListener
import com.desaysv.ivi.vdb.client.listener.VDNotifyListener
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.base.VDKey
import com.desaysv.ivi.vdb.event.id.bt.VDEventBT
import com.desaysv.ivi.vdb.event.id.bt.VDValueBT
import com.desaysv.ivi.vdb.event.id.bt.bean.VDBTContacts
import com.desaysv.ivi.vdb.event.id.bt.bean.VDBTDial
import com.desaysv.ivi.vdb.event.id.bt.bean.VDBTDialInfo
import com.desaysv.ivi.vdb.event.id.bt.bean.VDBTListProgress
import com.desaysv.ivi.vdb.event.id.bt.bean.VDBTPhoneBookSync
import com.desaysv.ivi.vdb.event.id.bt.bean.VDBTRecord
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/*
 * Telefon arka uçları. Bütün yöntemler PhoneSource'un io kuyruğunda çağrılır (ana iş parçacığında asla); binder ya da
 * yayın iş parçacığından gelen geri çağrılar önce io'ya, oradan ana iş parçacığına aktarılır.
 *
 * GİZLİLİK: günlüğe yalnızca arka uç adı, durum, sayı ve istisna SINIFI yazılır; isim/numara/adres asla. OEM
 * bean'lerinin toString'i kullanılmaz.
 */

/** Okunan rehber alt kümesi ve arama geçmişi (yalnızca bellekte). */
internal data class PhoneData(val contacts: List<PhoneContact>, val recents: List<PhoneRecent>) {
    override fun toString(): String = "PhoneData(contacts=${contacts.size}, recents=${recents.size})"
}

internal interface CallBackend {
    val name: String
    fun available(): Boolean
    fun dial(number: String): Boolean
    fun answer(): Boolean
    fun reject(): Boolean
    fun hangUp(): Boolean
    /** false = desteklenmiyor. */
    fun setMute(on: Boolean): Boolean
    /**
     * Sesi telefona ([toPhone] true) ya da araca aktarır. false = desteklenmiyor/başarısız. VDBus: DIAL_AUDIO_MODE
     * (262548) {"status": 1 araç / 2 telefon}; OEM servisi 1'de connectAudio, diğerinde disconnectAudio çağırır
     * (OEM arayüzü de aynı yolu kullanır).
     */
    fun setAudioToPhone(toPhone: Boolean): Boolean = false
    /**
     * Konuşma sırasında tek DTMF tuşu ('0'..'9', '*', '#'). false = desteklenmiyor/başarısız. VDBus: DIAL ctrl 4; OEM
     * servisi number'ı Byte.parseByte ile ONDALIK sayı olarak okur, OEM arayüzü ASCII kodunu gönderir:
     * '5' için number = "53" ([DtmfCode.vdNumber]).
     */
    fun sendDtmf(c: Char): Boolean = false
    /**
     * io: arama durumunu kaynaktan BİR KEZ yeniden okur ve [open]'daki onState'e bildirir (abonelik kaçırırsa yoklama
     * yedeği; yalnızca arama sürerken 2 sn'de bir). Okuyamadıysa false.
     */
    fun refreshState(): Boolean = false
    /**
     * io: kaynak tarafında süren arama var mı (taze okuma). null = bilinmiyor. Kapat'ta "OEM'de arama yok" ise bizim
     * durum temizlenir (6 Kasım araç: kapatma 'callList.size=0' aldı, şerit ACTIVE kaldı).
     */
    fun hasLiveCall(): Boolean? = null
    /** io: ses telefonda mı (taze okuma; VDBus getOnce DIAL_AUDIO_MODE). null = bilinmiyor. */
    fun readAudioOnPhone(): Boolean? = null
    /** io: mikrofon kapalı mı (taze okuma; VDBus getOnce DIAL_MIC_MODE status 2). null = bilinmiyor. */
    fun readMicMuted(): Boolean? = null
    /** Ses yönü yayını (VDBus DIAL_AUDIO_MODE 262548) dinleyicisi; io kuyruğunda çağrılır. null bırakır. */
    fun setAudioListener(l: ((Boolean) -> Unit)?) {}
    /**
     * io: arayanın fotoğrafı (VDBus PHONE_BOOK_CONTACT_INFO 262383 getOnce {number} → portrait).
     * Bulunamazsa null. Numara günlüğe yazılmaz.
     */
    fun contactPortrait(number: String): ByteArray? = null
    /** Arama durumu dinlemesini başlatır. Geri çağrılar herhangi bir iş parçacığında gelebilir. */
    fun open(onState: (RawCall) -> Unit, onConnected: (Boolean, String?) -> Unit)
    fun close()
}

internal interface DataBackend {
    val name: String
    fun available(): Boolean
    /** [pinnedIds] sabitlenmiş kişileri bulmak için (yalnızca onlar, yıldızlılar ve geçmişteki numaralar tutulur). */
    fun read(pinnedIds: List<String>): PhoneData?
}

/**
 * Asistan isteğinin sonucu. BUSY: süren arama var (OEM de arama sırasında kendi asistanını kapatır). UNCONFIRMED:
 * istek telefona gitti (startVoiceRecognition true) ama telefon [HfpAssistant] süresinde +BVRA/SCO ile doğrulamadı;
 * Siri yine de açılmış olabilir (6 Kasım araç): kullanıcıya hata GÖSTERİLMEZ, yalnızca günlüğe yazılır.
 */
internal enum class AssistantResult { STARTED, STOPPED, BUSY, FAILED, UNCONFIRMED }

internal interface AssistantBackend {
    fun available(): Boolean
    /** Asistan oturumu açık (bildiğimiz kadarıyla). */
    val active: Boolean get() = false
    /** Aç/kapa: açıksa kapatır, değilse açar. Eşzamansız: sonuç [done] ile (io kuyruğunda) gelir. */
    fun start(done: (AssistantResult) -> Unit)
    /** Açıksa kapatır. */
    fun stop(done: (AssistantResult) -> Unit) = done(AssistantResult.FAILED)
}

private const val TAG = "PhoneSource"

private fun granted(ctx: Context, perm: String): Boolean =
    ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

/**
 * Gizli API muafiyeti (BluetoothHeadsetClient yansıması için). Yalnızca HFP istemci yolu ilk gerektiğinde bir kez
 * çağrılır; sistem ayarına dokunmaz (yalnızca bu süreç). Sonuç günlüğe başarı/başarısızlık olarak yazılır.
 */
internal object HiddenApi {
    @Volatile private var result: Boolean? = null

    @Synchronized
    fun exempt(): Boolean {
        result?.let { return it }
        val ok = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) HiddenApiBypass.addHiddenApiExemptions("L") else true
        } catch (t: Throwable) {
            Log.w(TAG, "gizli API muafiyeti: ${t.javaClass.simpleName}")
            false
        }
        Log.i(TAG, "gizli API muafiyeti: ${if (ok) "tamam" else "başarısız"}")
        result = ok
        return ok
    }
}

/** Mikrofon kapatma (AudioManager). Araç testinde SCO mikrofonunu gerçekten kapattığı doğrulanmalı. */
internal object MicMute {
    fun available(ctx: Context): Boolean = ctx.getSystemService(Context.AUDIO_SERVICE) is AudioManager

    fun set(ctx: Context, on: Boolean): Boolean = try {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am == null) false else {
            am.isMicrophoneMute = on
            true
        }
    } catch (t: Throwable) {
        Log.w(TAG, "mikrofon: ${t.javaClass.simpleName}")
        false
    }
}

/**
 * HFP istemci (profil 16, HEADSET_CLIENT) vekili: bağlı telefon adı, asistan ve HFP arama yolu bunu paylaşır.
 * Vekil geri çağrıları ana iş parçacığında gelir.
 */
internal object HfpLink {
    const val PROFILE_HEADSET_CLIENT = 16
    const val ACTION_CONNECTION = "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED"
    const val ACTION_CALL = "android.bluetooth.headsetclient.profile.action.AG_CALL_CHANGED"
    const val EXTRA_CALL = "android.bluetooth.headsetclient.extra.CALL"
    /** +BVRA (telefonun sesli tanıma durumu) değişince; ek: VOICE_RECOGNITION 0/1 (HeadsetClientStateMachine). */
    const val ACTION_AG_EVENT = "android.bluetooth.headsetclient.profile.action.AG_EVENT"
    const val EXTRA_VOICE_RECOGNITION = "android.bluetooth.headsetclient.extra.VOICE_RECOGNITION"
    /** SCO ses bağlantısı; ek: BluetoothProfile.EXTRA_STATE (0 kopuk, 1 bağlanıyor, 2 bağlı). */
    const val ACTION_AUDIO = "android.bluetooth.headsetclient.profile.action.AUDIO_STATE_CHANGED"
    const val AUDIO_CONNECTED = 2

    private val main = Handler(Looper.getMainLooper())

    @Volatile var proxy: BluetoothProfile? = null
        private set

    @Volatile private var requested = false
    /** [open] istedi, [close] bıraktı: geç gelen vekil istenmiyorsa hemen kapatılır. */
    @Volatile private var wanted = false

    /** Vekil bağlanınca/kopunca (ana iş parçacığında). */
    @Volatile var onProxyChanged: (() -> Unit)? = null

    private class Waiter(val executor: Executor, val cb: (BluetoothProfile?) -> Unit) {
        var timeout: Runnable? = null
    }

    private val waiters = ArrayList<Waiter>()

    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, p: BluetoothProfile?) {
            if (profile != PROFILE_HEADSET_CLIENT) return
            val keep = synchronized(this@HfpLink) {
                val old = proxy
                when {
                    // Bekleyen istek iptal edildi (görünmezken vekil bağlı kalmasın).
                    !wanted -> false
                    // İkinci bir istekten gelen fazla vekil.
                    old != null && old !== p -> false
                    else -> { proxy = p; true }
                }
            }
            if (!keep) {
                if (p != null) runCatching { adapter()?.closeProfileProxy(PROFILE_HEADSET_CLIENT, p) }
                if (!wanted) flush(null)
                return
            }
            Log.i(TAG, "HFP istemci vekili bağlandı")
            flush(p)
            onProxyChanged?.invoke()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != PROFILE_HEADSET_CLIENT) return
            proxy = null
            requested = false
            Log.i(TAG, "HFP istemci vekili koptu")
            onProxyChanged?.invoke()
        }
    }

    @Suppress("DEPRECATION")
    fun adapter(): BluetoothAdapter? = try {
        BluetoothAdapter.getDefaultAdapter()
    } catch (t: Throwable) {
        null
    }

    /** Vekili ister (zaten açıksa/isteniyorsa bir şey yapmaz). Bağdaştırıcı yoksa false. */
    @Synchronized
    fun open(context: Context): Boolean {
        wanted = true
        if (proxy != null || requested) return true
        val a = adapter() ?: return false
        requested = try {
            a.getProfileProxy(context.applicationContext, listener, PROFILE_HEADSET_CLIENT)
        } catch (t: Throwable) {
            Log.w(TAG, "HFP istemci vekili: ${t.javaClass.simpleName}")
            false
        }
        return requested
    }

    @Synchronized
    fun close() {
        wanted = false
        val p = proxy
        proxy = null
        requested = false
        if (p != null) runCatching { adapter()?.closeProfileProxy(PROFILE_HEADSET_CLIENT, p) }
        flush(null)
    }

    /** Vekil hazırsa hemen, değilse en çok [timeoutMs] bekleyip (bloklamadan) [cb]'yi [executor]'da çağırır. */
    fun withProxy(context: Context, timeoutMs: Long, executor: Executor, cb: (BluetoothProfile?) -> Unit) {
        proxy?.let { p -> executor.execute { cb(p) }; return }
        if (!open(context)) {
            executor.execute { cb(null) }
            return
        }
        val w = Waiter(executor, cb)
        val t = Runnable {
            val removed = synchronized(waiters) { waiters.remove(w) }
            if (removed) executor.execute { cb(proxy) }
        }
        w.timeout = t
        synchronized(waiters) { waiters.add(w) }
        main.postDelayed(t, timeoutMs)
        // Bu arada bağlanmış olabilir.
        proxy?.let { flush(it) }
    }

    private fun flush(p: BluetoothProfile?) {
        val list = synchronized(waiters) { ArrayList(waiters).also { waiters.clear() } }
        for (w in list) {
            w.timeout?.let { main.removeCallbacks(it) }
            w.executor.execute { w.cb(p) }
        }
    }

    /**
     * Yansıma çağrıları gerçekten yapılabilir: vekil açık, gizli API muafiyeti alındı ve bağlı cihaz var. Değilse
     * seçici HFP'yi atlar (çağrı anında başarısız olmak yerine). io kuyruğunda.
     */
    fun usable(): Boolean = proxy != null && HiddenApi.exempt() && connectedDevice() != null

    /**
     * HFP istemci yayınları (AG_CALL_CHANGED, AG_EVENT, AUDIO_STATE_CHANGED) UserHandle.ALL ile değil, Bluetooth
     * sürecinin kullanıcısına gönderilir. Süreç kullanıcı 0'da değilse yayınlar
     * gelmeyebilir: o zaman durum yoklamayla okunur.
     */
    val userId: Int by lazy {
        val id = Process.myUid() / 100_000
        Log.i(TAG, "süreç kullanıcısı: $id")
        id
    }

    /** Bağlı ilk telefon (vekil yoksa null). */
    fun connectedDevice(): BluetoothDevice? = try {
        proxy?.connectedDevices?.firstOrNull()
    } catch (t: Throwable) {
        null
    }

    /** HFP istemci bağlantısı (vekil gerekmez). */
    fun isConnected(): Boolean = try {
        adapter()?.getProfileConnectionState(PROFILE_HEADSET_CLIENT) == BluetoothProfile.STATE_CONNECTED
    } catch (t: Throwable) {
        false
    }

    /** Bağlı telefonun adı: yalnızca ekranda gösterilir, günlüğe yazılmaz. */
    fun deviceName(): String? = try {
        connectedDevice()?.name?.takeIf { it.isNotBlank() }
    } catch (t: Throwable) {
        null
    }
}

/** android.bluetooth.BluetoothHeadsetClient gizli yöntemleri (yansıma). Her çağrı Throwable yakalar. */
internal object HfpClientApi {
    private val callClass: Class<*>? by lazy {
        runCatching { Class.forName("android.bluetooth.BluetoothHeadsetClientCall") }.getOrNull()
    }
    private val methods = HashMap<String, Method?>()

    private fun method(target: Any, name: String, vararg types: Class<*>): Method? = synchronized(methods) {
        val key = target.javaClass.name + "#" + name + "/" + types.size
        if (methods.containsKey(key)) return methods[key]
        val m = runCatching { target.javaClass.getMethod(name, *types) }.getOrNull()
        methods[key] = m
        m
    }

    private fun invoke(target: Any, name: String, types: Array<Class<*>>, vararg args: Any?): Result<Any?> = try {
        HiddenApi.exempt()
        val m = method(target, name, *types) ?: throw NoSuchMethodException(name)
        Result.success(m.invoke(target, *args))
    } catch (t: Throwable) {
        val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
        Log.w(TAG, "hfp $name: ${cause.javaClass.simpleName}")
        Result.failure(cause)
    }

    private val DEV = BluetoothDevice::class.java

    fun dial(proxy: Any, device: BluetoothDevice, number: String): Boolean =
        invoke(proxy, "dial", arrayOf(DEV, String::class.java), device, number).getOrNull() != null

    fun accept(proxy: Any, device: BluetoothDevice): Boolean =
        invoke(proxy, "acceptCall", arrayOf(DEV, Int::class.javaPrimitiveType!!), device, 0).getOrNull() == true

    fun reject(proxy: Any, device: BluetoothDevice): Boolean =
        invoke(proxy, "rejectCall", arrayOf(DEV), device).getOrNull() == true

    fun terminate(proxy: Any, device: BluetoothDevice): Boolean {
        val cc = callClass ?: return false
        return invoke(proxy, "terminateCall", arrayOf(DEV, cc), device, null).getOrNull() == true
    }

    /** Şu anki aramalar (BluetoothHeadsetClientCall listesi); çağrı başarısızsa null. */
    fun currentCalls(proxy: Any, device: BluetoothDevice): List<Any>? =
        (invoke(proxy, "getCurrentCalls", arrayOf(DEV), device).getOrNull() as? List<*>)?.filterNotNull()

    /**
     * AT+BVRA=1. Dikkat: araç yığını yalnızca kendi durumu 0 iken gönderir, değilse sessizce düşürür ama yine de true
     * döner; true "başladı" demek değildir.
     */
    fun startVoiceRecognition(proxy: Any, device: BluetoothDevice): Boolean {
        val r = invoke(proxy, "startVoiceRecognition", arrayOf(DEV), device)
        // Teşhis (6 Kasım araç: Siri bir kez açıldı, sonra hep "başlatılamadı"): sonuç ya da hata türü, kişisel veri yok.
        android.util.Log.i("PhoneSource", "asistan adım: startVoiceRecognition sonuç=${r.getOrNull()} hata=${r.exceptionOrNull()?.javaClass?.simpleName}")
        return r.getOrNull() == true
    }

    /** AT+BVRA=0 (yalnızca yığının durumu 1 iken gönderilir). */
    fun stopVoiceRecognition(proxy: Any, device: BluetoothDevice): Boolean =
        invoke(proxy, "stopVoiceRecognition", arrayOf(DEV), device).getOrNull() == true

    /** Bir BluetoothHeadsetClientCall'ın kimliği, durumu, numarası, giden mi. */
    data class CallInfo(val id: Int, val state: Int, val number: String?, val outgoing: Boolean) {
        override fun toString(): String = "CallInfo(id=$id, state=$state, outgoing=$outgoing)"
    }

    fun callInfo(call: Any): CallInfo? {
        val id = invoke(call, "getId", emptyArray()).getOrNull() as? Int ?: return null
        val state = invoke(call, "getState", emptyArray()).getOrNull() as? Int ?: return null
        val number = invoke(call, "getNumber", emptyArray()).getOrNull() as? String
        val outgoing = invoke(call, "isOutgoing", emptyArray()).getOrNull() as? Boolean ?: false
        return CallInfo(id, state, number, outgoing)
    }
}

/**
 * HFP istemci arama yolu (gizli BluetoothHeadsetClient). Durum: AG_CALL_CHANGED yayını (her arama ayrı gelir; kimliğe
 * göre birleştirilir). Sessiz: AudioManager mikrofonu.
 */
internal class HfpClientCallBackend(private val ctx: Context, private val io: Executor) : CallBackend {
    override val name: String = PhoneBackendName.HFP_CLIENT
    private var onState: ((RawCall) -> Unit)? = null
    private var registered = false

    // ---- Tanık modu: durum kaynağı VDBus iken HFP AG_CALL_CHANGED yalnızca "bitti" ipucu verir ----
    @Volatile private var witness: (() -> Unit)? = null
    private var witnessRegistered = false

    private val witnessReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action != HfpLink.ACTION_CALL || witness == null) return
            val call: Parcelable? = try {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(HfpLink.EXTRA_CALL)
            } catch (t: Throwable) {
                null
            }
            if (call != null) io.execute { onWitnessCall(call) }
        }
    }

    /**
     * Tanık: VDBus yayını kaçarsa (6 Kasım araç) HFP'nin "arama bitti" yayını hemen yeniden okumayı tetikler. Tanık
     * durumu kendisi DEĞİŞTİRMEZ ([onTerminal] yalnızca taze okuma ister): iki kaynak birbirini ezmez.
     */
    fun openWitness(onTerminal: () -> Unit) {
        witness = onTerminal
        if (witnessRegistered) return
        witnessRegistered = try {
            ctx.registerReceiver(witnessReceiver, IntentFilter(HfpLink.ACTION_CALL))
            true
        } catch (t: Throwable) {
            Log.w(TAG, "hfp tanık: ${t.javaClass.simpleName}")
            false
        }
    }

    fun closeWitness() {
        witness = null
        if (witnessRegistered) runCatching { ctx.unregisterReceiver(witnessReceiver) }
        witnessRegistered = false
    }

    /** io: biten arama yayını geldi; liste boş ya da hepsi bitmişse tanık tetiklenir. */
    private fun onWitnessCall(call: Any) {
        val cb = witness ?: return
        val info = HfpClientApi.callInfo(call) ?: return
        if (info.state != PhoneStateMap.TERMINATED) return
        val p = HfpLink.proxy
        val d = HfpLink.connectedDevice()
        val rest = if (p != null && d != null) HfpClientApi.currentCalls(p, d)?.mapNotNull { HfpClientApi.callInfo(it) } else null
        if (rest == null || rest.all { it.state == PhoneStateMap.TERMINATED }) cb()
    }
    /** io kuyruğunda: kimlik → arama. */
    private val calls = LinkedHashMap<Int, HfpClientApi.CallInfo>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action != HfpLink.ACTION_CALL) return
            val call: Parcelable? = try {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(HfpLink.EXTRA_CALL)
            } catch (t: Throwable) {
                Log.w(TAG, "hfp çağrı yayını: ${t.javaClass.simpleName}")
                null
            }
            if (call != null) io.execute { onCall(call) }
        }
    }

    private val main = Handler(Looper.getMainLooper())
    /** Yalnızca io'da yazılır: boşta yoklaması kurulu mu. */
    @Volatile private var pollWanted = false
    /**
     * Kullanıcı 0 dışında yayın gelmeyebilir: arama YOKKEN [IDLE_POLL_MS]'de bir getCurrentCalls ([syncPoll]); gelen ya
     * da giden arama yalnızca böyle görülür. Arama sürerken PhoneModel.pollTask zaten 2 sn'de bir refreshState çağırır.
     */
    private val poll = object : Runnable {
        override fun run() {
            if (onState == null || !pollWanted) return
            io.execute { readCurrent() }
            main.postDelayed(this, IDLE_POLL_MS)
        }
    }

    override fun available(): Boolean = HfpLink.usable()

    private inline fun withDevice(block: (Any, BluetoothDevice) -> Boolean): Boolean {
        val p = HfpLink.proxy ?: return false
        val d = HfpLink.connectedDevice() ?: return false
        return block(p, d)
    }

    override fun dial(number: String): Boolean = withDevice { p, d -> HfpClientApi.dial(p, d, number) }
    override fun answer(): Boolean = withDevice { p, d -> HfpClientApi.accept(p, d) }
    override fun reject(): Boolean = withDevice { p, d -> HfpClientApi.reject(p, d) }
    override fun hangUp(): Boolean = withDevice { p, d -> HfpClientApi.terminate(p, d) }

    override fun setMute(on: Boolean): Boolean = MicMute.set(ctx, on)

    override fun refreshState(): Boolean = readCurrent()

    override fun hasLiveCall(): Boolean? {
        val p = HfpLink.proxy ?: return null
        val d = HfpLink.connectedDevice() ?: return null
        val list = HfpClientApi.currentCalls(p, d) ?: return null
        return list.mapNotNull { HfpClientApi.callInfo(it) }.any { it.state != PhoneStateMap.TERMINATED }
    }

    override fun open(onState: (RawCall) -> Unit, onConnected: (Boolean, String?) -> Unit) {
        this.onState = onState
        if (!registered) {
            registered = try {
                ctx.registerReceiver(receiver, IntentFilter(HfpLink.ACTION_CALL))
                true
            } catch (t: Throwable) {
                Log.w(TAG, "hfp alıcı: ${t.javaClass.simpleName}")
                false
            }
        }
        // Şu anki aramalar (yayın yalnızca değişiklikleri getirir): ana ekrana dönüşte süren görüşme de görünsün.
        // Kullanıcı 0 dışında boşta yavaş yoklama readCurrent → [syncPoll] ile kurulur; okuma başarısızsa da kurulsun.
        io.execute { if (!readCurrent()) syncPoll(CallKind.NONE) }
    }

    /** io: getCurrentCalls ile listeyi baştan kurar; liste boşsa NONE bildirir. Okuyamazsa false. */
    private fun readCurrent(): Boolean {
        val cb = onState ?: return false
        val p = HfpLink.proxy ?: return false
        val d = HfpLink.connectedDevice() ?: return false
        val list = HfpClientApi.currentCalls(p, d) ?: return false
        calls.clear()
        for (c in list) HfpClientApi.callInfo(c)?.let { calls[it.id] = it }
        if (calls.isEmpty()) {
            cb(RawCall.NONE)
            syncPoll(CallKind.NONE)
        } else {
            emit()
        }
        return true
    }

    override fun close() {
        onState = null
        pollWanted = false
        main.removeCallbacks(poll)
        calls.clear()
        if (registered) runCatching { ctx.unregisterReceiver(receiver) }
        registered = false
    }

    private fun onCall(call: Any) {
        val info = HfpClientApi.callInfo(call) ?: return
        calls[info.id] = info
        emit()
    }

    private fun emit() {
        val list = calls.values.toList()
        val lite = list.map { DialInfoLite(it.state, it.outgoing) }
        val kind = PhoneStateMap.fromVdDialInfos(lite)
        val src = list.getOrNull(PhoneStateMap.relevantIndex(lite))
        // Biten aramalar listeden çıkar (durum bir kez bildirildikten sonra).
        calls.entries.removeAll { it.value.state == PhoneStateMap.TERMINATED }
        onState?.invoke(RawCall(kind, null, src?.number?.takeIf { it.isNotBlank() }))
        syncPoll(kind)
    }

    /**
     * io: kullanıcı 0 dışında yayın gelmeyebilir. Arama yokken (ya da PhoneModel'in yoklamadığı durumlarda) bu arka uç
     * [IDLE_POLL_MS]'de bir yoklar, yoksa gelen arama hiç görülmez. Arama sürerken ([CallPoll.shouldPoll]) durur:
     * PhoneModel.pollTask 2 sn'de bir refreshState → readCurrent yapar (çift yoklama olmasın). Kullanıcı 0'da hiç yoklama yok.
     */
    private fun syncPoll(kind: CallKind) {
        val want = onState != null && HfpLink.userId != 0 && !CallPoll.shouldPoll(kind, true)
        if (want == pollWanted) return
        pollWanted = want
        main.removeCallbacks(poll)
        if (want) main.postDelayed(poll, IDLE_POLL_MS)
    }

    private companion object {
        /** Boşta yoklama: gelen arama en geç bu kadar gecikmeyle görülür (VDBus yayını varsa daha erken). */
        const val IDLE_POLL_MS = 5_000L
    }
}

/**
 * Telecom yolu: arama başlatır (HfpClientConnectionService "hfpc" hesabı varsa). ANSWER_PHONE_CALLS verildiyse
 * (araçta `adb shell pm grant`) cevap ve kapatma da yapar: araç HFP yığını her HFP aramasını Telecom'a ekler
 * (HfpClientDeviceBlock). Yansıma gerektirmeyen son yedek. Durum dinlemez (VDBus ya da HFP yayını verir).
 */
internal class TelecomCallBackend(private val ctx: Context) : CallBackend {
    override val name: String = PhoneBackendName.TELECOM

    private fun handle(): PhoneAccountHandle? {
        if (!granted(ctx, Manifest.permission.CALL_PHONE)) return null
        return try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return null
            tm.callCapablePhoneAccounts?.firstOrNull { it.componentName?.packageName == "com.android.bluetooth" }
        } catch (t: Throwable) {
            // READ_PHONE_STATE yoksa SecurityException.
            null
        }
    }

    override fun available(): Boolean = handle() != null

    override fun dial(number: String): Boolean {
        val h = handle() ?: return false
        return try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.placeCall(Uri.fromParts("tel", number, null), Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, h)
            })
            true
        } catch (t: Throwable) {
            Log.w(TAG, "telecom placeCall: ${t.javaClass.simpleName}")
            false
        }
    }

    /** Cevap/kapatma için izin var. */
    fun canControl(): Boolean = granted(ctx, Manifest.permission.ANSWER_PHONE_CALLS)

    private inline fun control(label: String, block: (TelecomManager) -> Boolean): Boolean {
        if (!canControl()) return false
        return try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return false
            block(tm)
        } catch (t: Throwable) {
            Log.w(TAG, "telecom $label: ${t.javaClass.simpleName}")
            false
        }
    }

    @Suppress("DEPRECATION")
    override fun answer(): Boolean = control("acceptRingingCall") { it.acceptRingingCall(); true }

    /** endCall çalan aramayı da reddeder. targetSdk 28 olduğu için hâlâ çalışır (29+ hedefte kaldırıldı). */
    @Suppress("DEPRECATION")
    override fun reject(): Boolean = control("endCall") { it.endCall() }

    @Suppress("DEPRECATION")
    override fun hangUp(): Boolean = control("endCall") { it.endCall() }

    override fun setMute(on: Boolean): Boolean = false
    override fun open(onState: (RawCall) -> Unit, onConnected: (Boolean, String?) -> Unit) {}
    override fun close() {}
}

/**
 * Fabrika VDBus "BT" modülü (com.desaysv.ivi.vds.bt): arama/cevap/kapat (DIAL) ve durum (DIAL_INFO_LIST). VDBus
 * uygulamanın başka parçalarıyla paylaşılır: kapanışta unbindService ÇAĞRILMAZ.
 */
internal class VdbusCallBackend(private val io: Executor, private val onAvailability: () -> Unit) : CallBackend {
    override val name: String = PhoneBackendName.VDBUS
    @Volatile var bus: VDBus? = null
        private set
    private var bindRegistered = false
    @Volatile private var onState: ((RawCall) -> Unit)? = null
    @Volatile private var onAudio: ((Boolean) -> Unit)? = null
    /** Yayın işleyicileri [VdbusBtNotify]'a eklendi (yalnızca io'da değişir). */
    private var handlersAdded = false
    /** Son arama listesi (durum + OEM uuid; isim/numara yok): kapatma/reddetme hangi uuid'yi göndereceğini buradan seçer. */
    @Volatile private var lastCalls: List<DialInfoLite> = emptyList()

    /** VDBus_DISPATCH: yalnızca çözümler ve io'ya aktarır ([handle]). */
    private val infoHandler: (VDEvent) -> Unit = { e -> handle(e) }

    /** VDBus_DISPATCH: DIAL_AUDIO_MODE {"value": MAC, "status": 1 araç / 2 telefon / 3 bağlanıyor}. */
    private val audioHandler: (VDEvent) -> Unit = { e ->
        val status = try {
            e.payload?.getInt(VDKey.STATUS, -1) ?: -1
        } catch (t: Throwable) {
            -1
        }
        val on = AudioModeMap.onPhone(status)
        if (on != null) io.execute { onAudio?.invoke(on) }
    }

    private val bindListener = object : VDBindListener {
        override fun onVDConnected(serviceType: VDServiceDef.ServiceType) {
            if (serviceType != VDServiceDef.ServiceType.BT) return
            io.execute {
                if (onState != null) subscribe()
                onAvailability()
            }
        }

        override fun onVDDisconnected(serviceType: VDServiceDef.ServiceType) {
            if (serviceType != VDServiceDef.ServiceType.BT) return
            // Standart abonelik listesi yeniden bağlanınca VDConnector tarafından yeniden gönderilir.
            io.execute { onAvailability() }
        }
    }

    /** Ana iş parçacığında (FavoriteKey gibi): VDBus init. */
    fun prepare(context: Context) {
        bus = try {
            VDBus.getDefault().also { it.init(context.applicationContext) }
        } catch (t: Throwable) {
            Log.w(TAG, "VDBus başlatılamadı: ${t.javaClass.simpleName}")
            null
        }
    }

    /** io: BT servisine bağlanır (bağlıysa bir şey yapmaz). */
    fun connect() {
        val b = bus ?: return
        if (!bindRegistered) {
            runCatching { b.registerVDBindListener(bindListener) }
            bindRegistered = true
        }
        if (!available()) runCatching { b.bindService(VDServiceDef.ServiceType.BT) }
            .onFailure { Log.w(TAG, "vdbus BT bind: ${it.javaClass.simpleName}") }
    }

    /** io: bağlanma dinleyicisini bırakır (servis bağı paylaşıldığı için açık kalır). */
    fun disconnect() {
        close()
        if (bindRegistered) runCatching { bus?.unregisterVDBindListener(bindListener) }
        bindRegistered = false
    }

    override fun available(): Boolean = try {
        bus?.isServiceConnected(VDServiceDef.ServiceType.BT) == true
    } catch (t: Throwable) {
        false
    }

    /**
     * DIAL (262545) yükü her zaman bir VDBTDial içerir: OEM sunucusu getValue sonucunu null denetimi yapmadan kullanır;
     * boş yük sistem BT servisini çökertir. Adres boşsa servis bağlı cihazı
     * kullanır; büyük/küçük harf fark etmez.
     */
    private fun send(ctrl: Int, number: String?, uuid: String? = null, acceptType: Int? = null): Boolean {
        val b = bus ?: return false
        if (!available()) return false
        return try {
            // Adres (MAC): HFP vekili açıksa bağlı telefonun adresi. Günlüğe yazılmaz.
            val address = try {
                HfpLink.connectedDevice()?.address
            } catch (t: Throwable) {
                null
            }
            b.set(VDBTDial.createEvent(VDEventBT.DIAL, VDBTDial().apply {
                if (address != null) putAddress(address)
                if (number != null) putNumber(number)
                if (uuid != null) putUuid(uuid)
                if (acceptType != null) putAcceptCallType(acceptType)
                putDialCtrl(ctrl)
            }))
            true
        } catch (t: Throwable) {
            Log.w(TAG, "vdbus DIAL($ctrl): ${t.javaClass.simpleName}")
            false
        }
    }

    override fun dial(number: String): Boolean = send(VDValueBT.DialCtrl.DIAL, number)

    /** acceptCallType 0 (NONE): VDBTDial varsayılanı 1, tek gelen aramada araç yığınınca yok sayılır. */
    override fun answer(): Boolean = send(VDValueBT.DialCtrl.ACCEPT, null, acceptType = VdCallPick.ACCEPT_TYPE)

    /** dialCtrl 3 uuid'siz hiçbir şey yapmaz: uuid yoksa false (sıradaki yol denenir). */
    override fun reject(): Boolean {
        val uuid = VdCallPick.forReject(lastCalls) ?: return false.also { Log.i(TAG, "vdbus reddet: uuid yok") }
        return send(VDValueBT.DialCtrl.HUNG_UP, null, uuid = uuid)
    }

    override fun hangUp(): Boolean {
        val uuid = VdCallPick.forHangUp(lastCalls) ?: return false.also { Log.i(TAG, "vdbus kapat: uuid yok") }
        return send(VDValueBT.DialCtrl.HUNG_UP, null, uuid = uuid)
    }

    /**
     * DIAL_MIC_MODE (262547): {"status": 2 sessiz / 1 açık}; araç servisi AudioManager.setMicrophoneMute'u sistem
     * uid'siyle çağırır.
     */
    override fun setMute(on: Boolean): Boolean {
        val b = bus ?: return false
        if (!available()) return false
        return try {
            b.set(VDEvent(VDEventBT.DIAL_MIC_MODE, Bundle().apply {
                putInt(VDKey.STATUS, if (on) VDValueBT.MicMode.MUTE else VDValueBT.MicMode.UNMUTE)
            }))
            true
        } catch (t: Throwable) {
            Log.w(TAG, "vdbus DIAL_MIC_MODE: ${t.javaClass.simpleName}")
            false
        }
    }

    /** DIAL_AUDIO_MODE (262548): status 1 araç / 2 telefon; "value" (MAC) boşsa bağlı cihaz. */
    override fun setAudioToPhone(toPhone: Boolean): Boolean {
        val b = bus ?: return false
        if (!available()) return false
        return try {
            b.set(VDEvent(VDEventBT.DIAL_AUDIO_MODE, Bundle().apply {
                putInt(VDKey.STATUS, if (toPhone) VDValueBT.AudioMode.PHONE else VDValueBT.AudioMode.CAR)
            }))
            true
        } catch (t: Throwable) {
            Log.w(TAG, "vdbus DIAL_AUDIO_MODE: ${t.javaClass.simpleName}")
            false
        }
    }

    /** DIAL ctrl 4: number = ASCII kodunun ondalık yazısı (bkz. [CallBackend.sendDtmf]). Günlüğe tuş yazılmaz. */
    override fun sendDtmf(c: Char): Boolean {
        val code = DtmfCode.vdNumber(c) ?: return false
        return send(VDValueBT.DialCtrl.DTMF, code)
    }

    override fun open(onState: (RawCall) -> Unit, onConnected: (Boolean, String?) -> Unit) {
        this.onState = onState
        subscribe()
        // Şu anki durum (abonelik yalnızca değişiklikleri getirir).
        refreshState()
    }

    override fun setAudioListener(l: ((Boolean) -> Unit)?) {
        onAudio = l
    }

    override fun close() {
        onState = null
        onAudio = null
        lastCalls = emptyList()
        if (handlersAdded) {
            VdbusBtNotify.remove(VDEventBT.DIAL_INFO_LIST, infoHandler)
            VdbusBtNotify.remove(VDEventBT.DIAL_AUDIO_MODE, audioHandler)
            handlersAdded = false
        }
    }

    /**
     * io: OEM'in kendi kullandığı STANDART abonelik ([VdbusBtNotify]; her 2 sn'de
     * DIAL_INFO_LIST alıyor). Eski `VDBus.subscribe(VDEvent, stub)` özel olay yoluydu ve BT servisi o yola hiç
     * yayımlamıyor: 6 Kasım araçta arama bitince şerit ACTIVE kalmasının nedeni buydu. Abonelik süreç boyunca açık
     * kalır; [close] yalnızca işleyicileri çıkarır.
     */
    private fun subscribe() {
        val b = bus ?: return
        if (!available()) return
        if (!VdbusBtNotify.ensure(b, CALL_IDS)) return
        if (handlersAdded) return
        VdbusBtNotify.add(VDEventBT.DIAL_INFO_LIST, infoHandler)
        VdbusBtNotify.add(VDEventBT.DIAL_AUDIO_MODE, audioHandler)
        handlersAdded = true
    }

    /** io: getOnce(DIAL_INFO_LIST) → [handle] (yoklama yedeği ve açılış okuması). */
    override fun refreshState(): Boolean {
        val e = getOnce(VDEventBT.DIAL_INFO_LIST) ?: return false
        if (e.payload == null) return false
        handle(e)
        return true
    }

    /** io: OEM listesinde durumu 7 (bitti) ya da 8 (cevapsız) olmayan arama var mı. Uuid listesini de tazeler. */
    override fun hasLiveCall(): Boolean? {
        val e = getOnce(VDEventBT.DIAL_INFO_LIST) ?: return null
        if (e.payload == null) return null
        val list = try {
            VDBTDialInfo.getList(e)?.filterNotNull() ?: emptyList()
        } catch (t: Throwable) {
            Log.w(TAG, "vdbus DialInfo: ${t.javaClass.simpleName}")
            return null
        }
        lastCalls = list.map { DialInfoLite(it.state, it.isOutGoing, it.uuid) }
        return list.any { it.state != PhoneStateMap.TERMINATED && it.state != PhoneStateMap.MISSED }
    }

    /** io: getOnce DIAL_AUDIO_MODE (boş "value" = bağlı cihaz). */
    override fun readAudioOnPhone(): Boolean? {
        val e = getOnce(VDEventBT.DIAL_AUDIO_MODE) ?: return null
        val status = e.payload?.getInt(VDKey.STATUS, -1) ?: return null
        return AudioModeMap.onPhone(status)
    }

    /** io: getOnce DIAL_MIC_MODE {"status": 2 sessiz / 1 açık}. */
    override fun readMicMuted(): Boolean? {
        val e = getOnce(VDEventBT.DIAL_MIC_MODE) ?: return null
        return when (e.payload?.getInt(VDKey.STATUS, -1)) {
            VDValueBT.MicMode.MUTE -> true
            VDValueBT.MicMode.UNMUTE -> false
            else -> null
        }
    }

    override fun contactPortrait(number: String): ByteArray? {
        val b = bus ?: return null
        if (!available() || number.isBlank()) return null
        return try {
            val r = b.getOnce(VDBTContacts.createEvent(VDEventBT.PHONE_BOOK_CONTACT_INFO, VDBTContacts().apply { putNumber(number) }))
            VDBTContacts.getValue(r)?.portrait?.takeIf { it.isNotEmpty() }
        } catch (t: Throwable) {
            Log.w(TAG, "vdbus CONTACT_INFO: ${t.javaClass.simpleName}")
            null
        }
    }

    private fun getOnce(id: Int): VDEvent? {
        val b = bus ?: return null
        if (!available()) return null
        return try {
            b.getOnce(id)
        } catch (t: Throwable) {
            Log.w(TAG, "vdbus getOnce($id): ${t.javaClass.simpleName}")
            null
        }
    }

    /**
     * VDBus_DISPATCH ya da io: listeyi çözüp durumu io'ya aktarır. Yayın yükünde "data" listesinin yanında "status" =
     * VDBTListProgress(index=size-1, total=size) vardır; boş liste total 0 ile yayımlanır → NONE.
     * getOnce yanıtında progress yoktur. Fotoğraf baytları çözülmeden io'ya geçer.
     */
    private fun handle(e: VDEvent) {
        val progress = try {
            VDBTListProgress.getValue(e)
        } catch (t: Throwable) {
            null
        }
        val list: List<VDBTDialInfo> = try {
            VDBTDialInfo.getList(e)?.filterNotNull() ?: emptyList()
        } catch (t: Throwable) {
            Log.w(TAG, "vdbus DialInfo: ${t.javaClass.simpleName}")
            return
        }
        if (progress?.total == 0 || list.isEmpty()) {
            lastCalls = emptyList()
            io.execute { onState?.invoke(RawCall.NONE) }
            return
        }
        val lite = list.map { DialInfoLite(it.state, it.isOutGoing, it.uuid) }
        lastCalls = lite
        val kind = PhoneStateMap.fromVdDialInfos(lite)
        val src = list.getOrNull(PhoneStateMap.relevantIndex(lite))
        val raw = RawCall(
            kind = kind,
            name = src?.name?.takeIf { it.isNotBlank() },
            number = src?.number?.takeIf { it.isNotBlank() },
            uuid = src?.uuid?.takeIf { it.isNotBlank() },
            startElapsedMs = src?.time ?: 0L,
            portrait = src?.portrait?.takeIf { it.isNotEmpty() },
        )
        io.execute { onState?.invoke(raw) }
    }

    private companion object {
        val CALL_IDS = intArrayOf(VDEventBT.DIAL_INFO_LIST, VDEventBT.DIAL_AUDIO_MODE)
    }
}

/**
 * VDBus BT yayınlarına OEM'in kullandığı STANDART abonelik: addSubscribe + subscribeCommit + registerVDNotifyListener.
 * `VDBus.subscribe(VDEvent, stub)` "özel olay" aboneliğidir
 * (IVDBus.subscribeCustomizedEvent); BT servisi onSubscribeCustomizedEvent'i ezmiyor (yalnızca
 * boş gövde), yayınlar yalnızca standart abonelere gider (VDBinder.publish → mEventMap). Bu yüzden
 * rehber/geçmiş yanıtları bize hiç ulaşmıyordu ("vdbus veri: yanıt yok").
 *
 * Abonelikler süreç boyunca açık kalır (sunucu her commit'te bu istemcinin tüm listesini değiştirir; servis yeniden
 * bağlanınca VDConnector.onVDBusConnected aynı listeyi yeniden gönderir). Dağıtım VDBus_DISPATCH iş parçacığında.
 */
internal object VdbusBtNotify {
    private val handlers = ConcurrentHashMap<Int, CopyOnWriteArrayList<(VDEvent) -> Unit>>()
    private val subscribed = HashSet<Int>()
    private var registered = false

    private val listener = VDNotifyListener { e, _ ->
        if (e == null) return@VDNotifyListener
        val list = handlers[e.id] ?: return@VDNotifyListener
        for (h in list) {
            try {
                h(e)
            } catch (t: Throwable) {
                Log.w(TAG, "vdbus yayın(${e.id}): ${t.javaClass.simpleName}")
            }
        }
    }

    /** io: [ids] standart aboneliğe eklenir (zaten varsa bir şey yapmaz). Başarısızsa false. */
    @Synchronized
    fun ensure(bus: VDBus, ids: IntArray): Boolean = try {
        if (!registered) {
            bus.registerVDNotifyListener(listener)
            registered = true
        }
        var changed = false
        for (id in ids) {
            if (subscribed.add(id)) {
                bus.addSubscribe(id, VDThreadType.CHILD_THREAD)
                changed = true
            }
        }
        if (changed) bus.subscribeCommit()
        true
    } catch (t: Throwable) {
        Log.w(TAG, "vdbus standart abonelik: ${t.javaClass.simpleName}")
        false
    }

    fun add(id: Int, h: (VDEvent) -> Unit) {
        handlers.getOrPut(id) { CopyOnWriteArrayList() }.add(h)
    }

    fun remove(id: Int, h: (VDEvent) -> Unit) {
        handlers[id]?.remove(h)
    }
}

/**
 * VDBus rehber ve geçmiş (yıldız bilgisi yok: favoriler yalnızca sıklıktan).
 *
 * Davranış araçta ölçüldü; başka bir araç yazılımı sürümünde de aynı.
 *
 * OEM telefon uygulaması rehberi VDBus'tan DEĞİL, BT servisinin ayrı bir bağlayıcı arayüzünden alır. O arayüzün dışa
 * açıklığı araç yazılımı sürümüne göre değişiyor ve OTA ile kapanabilir; bu yüzden burada kullanılmıyor. Aynı veri BT
 * servisinin VDBus PBAP dağıtıcısında da var:
 *
 * 1. ..._LIST_CACHE (262376 rehber / 262377 geçmiş) STANDART aboneliği ([VdbusBtNotify]).
 * 2. set PHONE_BOOK_SYNC_CACHE (262375) = VDBTPhoneBookSync{ctrl 1, tür 7 rehber / 6 geçmiş, adres null = bağlı
 * cihaz, key = bizim etiketimiz}. Sunucu bellekteki haritayı (yoksa tek boş
 *    yayın {index 0, total 0}) 10'arlık parçalarla yayar: {"data": liste, "status": VDBTListProgress(adres, index,
 * total, key)}. Harita yalnızca o PBAP oturumunda indirme bitince (durum 1)
 * ya da liste değişince dolar; PBAP kopunca silinir.
 * 3. PHONE_BOOK_SYNC_DB (262378) KULLANILMAZ: indirme görevleri SharedPreferences'ı her kayıtta siliyor;
 *    indirme bitince yükleme durumu 0 olur ve _LIST_DB yalnızca durum 3'te yayınlanır. İndirme sonrası hiç yanıt gelmez;
 *    eski kod her okumada boşuna beklerdi.
 *
 * Ayrıca ([watch]): indirme parçaları PHONE_BOOK_CONTACTS_LIST/RECORD_LIST (262347/262348, key null) ve eşitleme
 * durumu PHONE_BOOK_SYNC (262346, VDBTPhoneBookSync{status, type 7 rehber / 6 geçmiş}) edilgen dinlenir. Durum
 * getOnce ile tür başına sorulur ([syncStatus]; OEM ana ekranı da önbellekten önce bunu sorar).
 * İndirme isteği ([requestDownload]: PHONE_BOOK_SYNC ctrl 1 tür 50) yalnızca kullanıcı
 * rehber sayfasını açınca, bağlantı başına bir kez, PhoneSource'tan gelir.
 *
 * Kişiler: tam liste (isimli olanlar; "Tümü" sayfası için yalnızca bellekte). Fotoğraf yalnızca gereken alt küme için
 * çözülür (geçmişte geçen numaralar ve sabitlenenler): [PhonePhotos.putEncoded] başarılıysa [PhoneContact.photoKey].
 * Hiç yanıt gelmezse null döner (boş rehber gibi görünmesin).
 */
internal class VdbusDataBackend(private val call: VdbusCallBackend) : DataBackend {
    override val name: String = PhoneBackendName.VDBUS

    /**
     * Son okumada önbellek isteklerine gelen kayıt sayısı (rehber + geçmiş); hiç yanıt yoksa null. [PhonebookRefresh]
     * kararı için. Bağlantı değişince [resetCacheTotal].
     */
    @Volatile var cacheTotal: Int? = null
        private set
    private var cacheSeen: Int? = null

    fun resetCacheTotal() {
        cacheTotal = null
    }

    override fun available(): Boolean = call.available()

    /** VDBTPhoneBookSync'li istek (bean'siz SYNC* sunucuyu çökertir). */
    private fun request(b: VDBus, eventId: Int, syncType: Int, key: String?) {
        b.set(VDBTPhoneBookSync.createEvent(eventId, VDBTPhoneBookSync().apply {
            putSyncCtrl(VDValueBT.SyncCtrl.START)
            putSyncType(syncType)
            if (key != null) putSyncKey(key)
        }))
    }

    /**
     * io: [listId] parçalarını toplar ([key] eşleşenler). Bitti, [VdListWait.IDLE_MS] boyunca yeni parça yok ya da
     * [VdListWait.HARD_MS] doldu. Hiç parça gelmediyse null.
     */
    private fun <T> collect(listId: Int, key: String?, trigger: (VDBus) -> Unit, parse: (VDEvent) -> List<*>?, cls: Class<T>): List<T>? {
        val b = call.bus ?: return null
        if (!VdbusBtNotify.ensure(b, LIST_IDS)) return null
        val acc = VdListCollector<T>(key)
        val finished = CountDownLatch(1)
        val startedAt = SystemClock.uptimeMillis()
        val lastAt = AtomicLong(startedAt)
        val handler: (VDEvent) -> Unit = handler@{ e ->
            val progress = VDBTListProgress.getValue(e)
            val items = parse(e)?.filterIsInstance(cls).orEmpty()
            val offer = acc.offer(progress?.key, progress?.index ?: 0, progress?.total ?: 0, items)
            if (offer != VdListCollector.Offer.ACCEPTED) return@handler
            lastAt.set(SystemClock.uptimeMillis())
            if (acc.finished || acc.failed) finished.countDown()
        }
        VdbusBtNotify.add(listId, handler)
        try {
            trigger(b)
            while (true) {
                val left = VdListWait.remaining(SystemClock.uptimeMillis(), lastAt.get(), startedAt)
                if (left <= 0) break
                if (finished.await(left, TimeUnit.MILLISECONDS)) break
            }
        } catch (t: Throwable) {
            Log.w(TAG, "vdbus istek($listId): ${t.javaClass.simpleName}")
        } finally {
            VdbusBtNotify.remove(listId, handler)
        }
        return acc.result()
    }

    /**
     * Yalnızca önbellek (_DB yolu indirmeden sonra hiç yanıt vermez; bkz. sınıf belgesi madde 3). Sunucu boş haritada
     * da tek bir {index 0, total 0} yayını yollar, yani boş yanıt hızlı gelir.
     */
    private fun <T> load(syncType: Int, cacheId: Int, parse: (VDEvent) -> List<*>?, cls: Class<T>): List<T>? {
        val key = "ml-" + UUID.randomUUID().toString().take(8)
        val cache = collect(cacheId, key, { request(it, VDEventBT.PHONE_BOOK_SYNC_CACHE, syncType, key) }, parse, cls)
            ?: return null
        cacheSeen = (cacheSeen ?: 0) + cache.size
        return cache
    }

    override fun read(pinnedIds: List<String>): PhoneData? {
        if (!available()) return null
        return try {
            cacheSeen = null
            val records = load(
                VDValueBT.PhoneBookSyncType.CALLLOG_SYNC, VDEventBT.PHONE_BOOK_RECORD_LIST_CACHE,
                { VDBTRecord.getList(it) }, VDBTRecord::class.java,
            )
            val recents = convertRecords(records.orEmpty())
            val raw = load(
                VDValueBT.PhoneBookSyncType.CONTACT_SYNC, VDEventBT.PHONE_BOOK_CONTACTS_LIST_CACHE,
                { VDBTContacts.getList(it) }, VDBTContacts::class.java,
            )
            cacheTotal = cacheSeen
            if (records == null && raw == null) {
                Log.i(TAG, "vdbus veri: yanıt yok")
                return null
            }
            val wanted = recents.map { FavoritesRanker.normalizeNumber(it.number) }.toHashSet()
            val contacts = convertContacts(raw.orEmpty(), wanted, pinnedIds.toHashSet())
            PhoneData(contacts, joinNames(recents, contacts))
        } catch (e: RuntimeException) {
            Log.w(TAG, "vdbus veri: ${e.javaClass.simpleName}")
            null
        }
    }

    /** VDBTRecord → geçmiş satırı (türü ya da numarası olmayanlar atlanır). */
    fun convertRecords(records: List<VDBTRecord>): List<PhoneRecent> = records.mapNotNull { r ->
        val type = PhoneStateMap.recentTypeFromVd(r.recordType) ?: return@mapNotNull null
        val number = r.number?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        // dateTime: ham vCard X-IRMC-CALL-DATETIME (yyyyMMdd'T'HHmmss). Çözülemezse 0: favori sıklığına girmez,
        // panel zaman yazısı göstermez. duration OEM'de hiç doldurulmuyor.
        PhoneRecent(type, r.name?.takeIf { it.isNotBlank() }, number, PhoneStateMap.parseVdDateTime(r.dateTime) ?: 0L)
    }

    /**
     * VDBTContacts → kişi (isimli olanlar, tam liste). Numara: geçmişte geçen ([wanted]) varsa o, yoksa ilki. Fotoğraf
     * yalnızca gereken alt kümede (geçmişte geçen ya da sabitlenen) çözülür; başarılıysa photoKey = kimlik.
     */
    fun convertContacts(raw: List<VDBTContacts>, wanted: Set<String>, pinned: Set<String>): List<PhoneContact> {
        val contacts = ArrayList<PhoneContact>(raw.size)
        var photos = 0
        for (c in raw) {
            val name = c.name?.takeIf { it.isNotBlank() } ?: continue
            val id = "v:" + (c.id ?: continue)
            val numbers = buildList {
                c.number?.let { add(it) }
                c.cardList?.forEach { card -> card?.number?.let { add(it) } }
            }.filter { it.isNotBlank() }
            val match = numbers.firstOrNull { FavoritesRanker.normalizeNumber(it) in wanted }
            val number = match ?: numbers.firstOrNull() ?: continue
            val keep = match != null || id in pinned
            val photoKey = if (keep && photos < MAX_PHOTOS && (PhonePhotos.contains(id) || PhonePhotos.putEncoded(id, c.portrait))) {
                photos++
                id
            } else {
                null
            }
            contacts.add(PhoneContact(id, name, number, photoKey = photoKey))
        }
        return contacts
    }

    /**
     * io: PHONE_BOOK_SYNC getOnce {tür} → indirme durumu (VDValueBT.SyncStatus; tür 1/2/7 rehber
     * görevinin, 3-6/8 geçmiş görevinin durumu, adres boşsa bağlı cihaz). null = bilinmiyor (bean yok ya da hata).
     */
    fun syncStatus(syncType: Int): Int? {
        val b = call.bus ?: return null
        if (!available()) return null
        return try {
            val r = b.getOnce(VDBTPhoneBookSync.createEvent(VDEventBT.PHONE_BOOK_SYNC, VDBTPhoneBookSync().apply {
                putSyncType(syncType)
            }))
            VDBTPhoneBookSync.getValue(r)?.syncStatus
        } catch (t: Throwable) {
            Log.w(TAG, "vdbus PHONE_BOOK_SYNC durum: ${t.javaClass.simpleName}")
            null
        }
    }

    /**
     * io: telefondan rehber + geçmiş indirmesi (PHONE_BOOK_SYNC 262346 ctrl 1 tür 50 = downloadCurrentContactCallLog,
     * adres null = bağlı cihaz). YALNIZCA kullanıcı rehber sayfasını açınca ve bağlantı başına bir kez
     * (PhoneSource karar verir). Sonuç [watch] ile edilgen gelir.
     */
    fun requestDownload(): Boolean {
        val b = call.bus ?: return false
        if (!available()) return false
        return try {
            request(b, VDEventBT.PHONE_BOOK_SYNC, VDValueBT.PhoneBookSyncType.CONTACT_CALLLOG_SYNC, null)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "vdbus PHONE_BOOK_SYNC: ${t.javaClass.simpleName}")
            false
        }
    }

    // ---- Edilgen yakalama (yalnızca itme) ----

    private var watchIo: Executor? = null
    @Volatile private var onContacts: ((List<VDBTContacts>) -> Unit)? = null
    @Volatile private var onRecords: ((List<VDBTRecord>) -> Unit)? = null
    @Volatile private var onSync: ((Int, Int) -> Unit)? = null
    private var watching = false
    // Yalnızca io'da.
    private var passiveContacts = VdListCollector<VDBTContacts>(null)
    private var passiveRecords = VdListCollector<VDBTRecord>(null)
    private val main = Handler(Looper.getMainLooper())
    private val contactsIdle = Runnable { watchIo?.execute { flushContacts() } }
    private val recordsIdle = Runnable { watchIo?.execute { flushRecords() } }

    /**
     * io: indirme parçalarını ve eşitleme durumunu edilgen dinler (süreç boyunca; işleyiciler bir kez eklenir).
     * Geri çağrılar [io]'da gelir. Parçalar key=null [VdListCollector] ile toplanır; bitince ya da
     * [VdListWait.IDLE_MS] sessizlikten sonra liste teslim edilir.
     */
    fun watch(
        io: Executor,
        contacts: (List<VDBTContacts>) -> Unit,
        records: (List<VDBTRecord>) -> Unit,
        sync: (status: Int, type: Int) -> Unit,
    ) {
        watchIo = io
        onContacts = contacts
        onRecords = records
        onSync = sync
        val b = call.bus ?: return
        if (!available() || !VdbusBtNotify.ensure(b, PASSIVE_IDS)) return
        if (watching) return
        watching = true
        VdbusBtNotify.add(VDEventBT.PHONE_BOOK_CONTACTS_LIST) { e ->
            val p = VDBTListProgress.getValue(e)
            val items = VDBTContacts.getList(e)?.filterNotNull().orEmpty()
            io.execute { offerContacts(p?.key, p?.index ?: 0, p?.total ?: 0, items) }
        }
        VdbusBtNotify.add(VDEventBT.PHONE_BOOK_RECORD_LIST) { e ->
            val p = VDBTListProgress.getValue(e)
            val items = VDBTRecord.getList(e)?.filterNotNull().orEmpty()
            io.execute { offerRecords(p?.key, p?.index ?: 0, p?.total ?: 0, items) }
        }
        VdbusBtNotify.add(VDEventBT.PHONE_BOOK_SYNC) { e ->
            val s = VDBTPhoneBookSync.getValue(e) ?: return@add
            val status = s.syncStatus
            val type = s.syncType
            io.execute { onSync?.invoke(status, type) }
        }
        Log.i(TAG, "rehber: edilgen dinleme açık")
    }

    private fun offerContacts(key: String?, index: Int, total: Int, items: List<VDBTContacts>) {
        if (passiveContacts.offer(key, index, total, items) != VdListCollector.Offer.ACCEPTED) return
        main.removeCallbacks(contactsIdle)
        if (passiveContacts.finished || passiveContacts.failed) flushContacts() else main.postDelayed(contactsIdle, VdListWait.IDLE_MS)
    }

    private fun offerRecords(key: String?, index: Int, total: Int, items: List<VDBTRecord>) {
        if (passiveRecords.offer(key, index, total, items) != VdListCollector.Offer.ACCEPTED) return
        main.removeCallbacks(recordsIdle)
        if (passiveRecords.finished || passiveRecords.failed) flushRecords() else main.postDelayed(recordsIdle, VdListWait.IDLE_MS)
    }

    private fun flushContacts() {
        main.removeCallbacks(contactsIdle)
        val acc = passiveContacts
        passiveContacts = VdListCollector(null)
        val list = acc.result() ?: return
        Log.i(TAG, "rehber: indirme parçaları ${acc.summary()}")
        if (list.isNotEmpty()) onContacts?.invoke(list)
    }

    private fun flushRecords() {
        main.removeCallbacks(recordsIdle)
        val acc = passiveRecords
        passiveRecords = VdListCollector(null)
        val list = acc.result() ?: return
        Log.i(TAG, "geçmiş: indirme parçaları ${acc.summary()}")
        if (list.isNotEmpty()) onRecords?.invoke(list)
    }

    private companion object {
        /** Foto çözme üst sınırı (bir okumada). */
        const val MAX_PHOTOS = 64
        val LIST_IDS = intArrayOf(
            VDEventBT.PHONE_BOOK_CONTACTS_LIST_CACHE, VDEventBT.PHONE_BOOK_RECORD_LIST_CACHE,
        )
        val PASSIVE_IDS = intArrayOf(
            VDEventBT.PHONE_BOOK_CONTACTS_LIST, VDEventBT.PHONE_BOOK_RECORD_LIST, VDEventBT.PHONE_BOOK_SYNC,
        )
    }
}

/** Geçmişte ismi olmayan satırlara rehberden isim ekler (numara eşleşmesiyle). */
internal fun joinNames(recents: List<PhoneRecent>, contacts: List<PhoneContact>): List<PhoneRecent> {
    if (contacts.isEmpty()) return recents
    val byNumber = HashMap<String, String>()
    for (c in contacts) byNumber.putIfAbsent(FavoritesRanker.normalizeNumber(c.number), c.name)
    return recents.map { r -> if (r.name != null) r else byNumber[FavoritesRanker.normalizeNumber(r.number)]?.let { r.copy(name = it) } ?: r }
}

/**
 * Android rehberi (PBAP istemcisinin doldurduğu `com.android.bluetooth.pbapsink` hesabı) ve CallLog. Tüm rehber
 * tutulmaz: yalnızca yıldızlılar, sabitlenenler ve geçmişte geçen numaralar.
 */
internal class ContactsDataBackend(private val ctx: Context) : DataBackend {
    override val name: String = PhoneBackendName.CONTACTS_PROVIDER

    override fun available(): Boolean = granted(ctx, Manifest.permission.READ_CONTACTS)

    override fun read(pinnedIds: List<String>): PhoneData? {
        if (!available()) return null
        val recents = if (granted(ctx, Manifest.permission.READ_CALL_LOG)) readCallLog() else emptyList()
        val wanted = recents.map { FavoritesRanker.normalizeNumber(it.number) }.toHashSet()
        val contacts = readContacts(wanted, pinnedIds.toHashSet()) ?: return null
        return PhoneData(contacts, joinNames(recents, contacts))
    }

    private fun readCallLog(): List<PhoneRecent> {
        val out = ArrayList<PhoneRecent>()
        val uri = CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter("limit", LIMIT.toString()).build()
        try {
            ctx.contentResolver.query(
                uri,
                arrayOf(CallLog.Calls.TYPE, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE),
                null, null, CallLog.Calls.DATE + " DESC",
            )?.use { c ->
                while (c.moveToNext() && out.size < LIMIT) {
                    val type = PhoneStateMap.recentType(c.getInt(0)) ?: continue
                    val number = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    out.add(PhoneRecent(type, c.getString(2)?.takeIf { it.isNotBlank() }, number, c.getLong(3)))
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "arama geçmişi: ${t.javaClass.simpleName}")
        }
        return out
    }

    private fun readContacts(wanted: Set<String>, pinned: Set<String>): List<PhoneContact>? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.STARRED,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
        )
        /**
         * Tam kişi listesi (kimliğe göre tekil: geçmişte geçen numara öne alınır) ve görülen satır sayısı; sorgu
         * çalışmazsa null. Gereken alt kümenin (yıldızlı, sabitlenen, geçmişte geçen) küçük resim adresleri ayrıca.
         */
        fun query(selection: String?, args: Array<String>?): Triple<List<PhoneContact>, Int, Map<String, String>>? = try {
            val byId = LinkedHashMap<String, PhoneContact>()
            val thumbs = HashMap<String, String>()
            var rows = 0
            ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, selection, args, null)?.use { c ->
                while (c.moveToNext()) {
                    rows++
                    val id = "c:" + c.getLong(0)
                    val starred = c.getInt(3) != 0
                    val number = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    val name = c.getString(1)?.takeIf { it.isNotBlank() } ?: number
                    val matched = FavoritesRanker.normalizeNumber(number) in wanted
                    val old = byId[id]
                    if (old == null || (matched && FavoritesRanker.normalizeNumber(old.number) !in wanted)) {
                        byId[id] = PhoneContact(id, name, number, starred)
                    }
                    if ((starred || matched || id in pinned) && id !in thumbs) {
                        c.getString(4)?.takeIf { it.isNotBlank() }?.let { thumbs[id] = it }
                    }
                }
            }
            Triple(ArrayList(byId.values), rows, thumbs)
        } catch (t: Throwable) {
            Log.w(TAG, "rehber: ${t.javaClass.simpleName}")
            null
        }
        val pbap = query(ContactsContract.RawContacts.ACCOUNT_TYPE_AND_DATA_SET + " LIKE ?", arrayOf("$PBAP_ACCOUNT%"))
        // Seçimsiz tarama yalnızca PBAP sorgusu çalışmadıysa ya da hiç satır görmediyse (hesap türü farklı): boş liste
        // geçerli bir yanıttır, tüm tablo yeniden taranmaz.
        val r = if (pbap != null && pbap.second > 0) pbap else (query(null, null) ?: pbap) ?: return null
        return withThumbnails(r.first, r.third)
    }

    /** io: gereken alt kümenin küçük resimleri [PhonePhotos]'a (yalnızca bellek); başarılıysa photoKey = kimlik. */
    private fun withThumbnails(contacts: List<PhoneContact>, thumbs: Map<String, String>): List<PhoneContact> {
        if (thumbs.isEmpty()) return contacts
        var n = 0
        return contacts.map { c ->
            val uri = thumbs[c.id] ?: return@map c
            if (n >= MAX_PHOTOS) return@map c
            val ok = PhonePhotos.contains(c.id) || try {
                ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { PhonePhotos.putEncoded(c.id, it.readBytes()) } == true
            } catch (t: Throwable) {
                false
            }
            if (ok) {
                n++
                c.copy(photoKey = c.id)
            } else {
                c
            }
        }
    }

    private companion object {
        const val LIMIT = 100
        const val MAX_PHOTOS = 64
        const val PBAP_ACCOUNT = "com.android.bluetooth.pbapsink"
    }
}

/**
 * Sesli asistan: bağlı telefonda HFP sesli tanıma (BVRA → Siri / Google). Yazılım incelemesi: OEM yazılımı
 * BVRA'yı hiç çağırmaz ve bunun için VDBus olayı ya da dışa açık intent yoktur; yol BluetoothHeadsetClient
 * (yalnızca android.permission.BLUETOOTH + etkin kullanıcı denetimi).
 *
 * Yığın davranışı ve plan: [VoicePlan]. Başarı "SCO bağlanıyor/bağlı ya da AG_EVENT VOICE_RECOGNITION=1" ile
 * [CONFIRM_MS] içinde doğrulanır; binder'ın true dönmesi yetmez. Açıkken ikinci basış kapatır. Yığın 1'de kalmış
 * olabilirse ([stackMaybeActive]) önce durdurulur, [STALE_RESTART_MS] sonra başlatılır. Alıcı ilk kullanımda uygulama
 * bağlamına kaydedilir ve açık kalır (hafif).
 */
internal class HfpAssistant(private val ctx: Context, private val io: Executor) : AssistantBackend {
    private val main = Handler(Looper.getMainLooper())
    /** Telefonun sesli tanıma durumu (bildiğimiz kadarıyla). */
    @Volatile private var vr = false
    /** SCO sesi bağlı. */
    @Volatile private var audio = false
    /**
     * Yığının sesli tanıma durumu 1'de olabilir (bizim başlatmamız gönderildi ve +BVRA:0 / durdurma görülmedi). Süreç
     * başında bilinmez: true. Yalnızca AG_EVENT 0 ya da kendi durdurmamız false yapar.
     */
    @Volatile private var stackMaybeActive = true
    private var registered = false
    // io kuyruğunda: doğrulama bekleyen istek.
    private var pending: ((AssistantResult) -> Unit)? = null
    private var pendingTimeout: Runnable? = null
    // io kuyruğunda: bekleyen başlatma "önce durdur" sonrası mı (doğrulanmazsa logcat'te ayrıca söylenir).
    private var pendingAfterReset = false
    // io kuyruğunda: başlatma yalnızca SCO "bağlanıyor" (1) ile doğrulandıysa o anın elapsedRealtime'ı; değilse 0.
    private var scoConnectingConfirmAt = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                HfpLink.ACTION_AG_EVENT -> {
                    if (!intent.hasExtra(HfpLink.EXTRA_VOICE_RECOGNITION)) return
                    val on = intent.getIntExtra(HfpLink.EXTRA_VOICE_RECOGNITION, 0) == 1
                    io.execute {
                        vr = on
                        stackMaybeActive = on
                        scoConnectingConfirmAt = 0L
                        Log.i(TAG, "asistan durumu (telefon): ${if (on) "açık" else "kapalı"}")
                        if (on) confirm(null)
                    }
                }
                HfpLink.ACTION_AUDIO -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                    io.execute {
                        audio = state == HfpLink.AUDIO_CONNECTED
                        if (pending != null) Log.i(TAG, "asistan adım: SCO durumu=$state")
                        if (state == HfpLink.AUDIO_CONNECTED) scoConnectingConfirmAt = 0L
                        if (state == AUDIO_DISCONNECTED && vr && scoConnectingConfirmAt != 0L &&
                            SystemClock.elapsedRealtime() - scoConnectingConfirmAt <= SCO_FALLBACK_MS
                        ) {
                            // "Bağlanıyor" ile doğrulandı ama SCO bağlanmadan düştü: oturum açılmamış say.
                            Log.i(TAG, "asistan adım: SCO bağlanmadan 0'a düştü; doğrulama geri alındı (vr=false)")
                            vr = false
                            scoConnectingConfirmAt = 0L
                        }
                        if (VoicePlan.confirmsStart(state)) confirm(state)
                    }
                }
                HfpLink.ACTION_CONNECTION -> {
                    if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) != BluetoothProfile.STATE_CONNECTED) {
                        // Yeniden bağlanınca yığının durumu bilinmez: ilk başlatmadan önce yine durdurulur.
                        io.execute { vr = false; audio = false; stackMaybeActive = true }
                    }
                }
            }
        }
    }

    override fun available(): Boolean = HfpLink.proxy != null

    override val active: Boolean get() = vr

    private fun register() {
        if (registered) return
        registered = try {
            ctx.applicationContext.registerReceiver(receiver, IntentFilter().apply {
                addAction(HfpLink.ACTION_AG_EVENT)
                addAction(HfpLink.ACTION_AUDIO)
                addAction(HfpLink.ACTION_CONNECTION)
            })
            true
        } catch (t: Throwable) {
            Log.w(TAG, "asistan alıcı: ${t.javaClass.simpleName}")
            false
        }
    }

    override fun start(done: (AssistantResult) -> Unit) {
        register()
        HfpLink.withProxy(ctx, PROXY_WAIT_MS, io) { p ->
            val d = if (p != null) HfpLink.connectedDevice() else null
            Log.i(TAG, "asistan adım: vekil=${p != null} cihaz=${d != null} vr=$vr ses=$audio yığın1=$stackMaybeActive muaf=${HiddenApi.exempt()}")
            if (p == null || d == null) return@withProxy done(AssistantResult.FAILED)
            val busy = HfpClientApi.currentCalls(p, d)?.mapNotNull { HfpClientApi.callInfo(it) }
                ?.any { it.state != PhoneStateMap.TERMINATED } == true
            when (VoicePlan.next(vr, audio, stackMaybeActive, busy)) {
                VoicePlan.Step.STOP_SESSION -> {
                    // Açık oturum: ikinci basış kapatır.
                    val ok = HfpClientApi.stopVoiceRecognition(p, d)
                    if (ok) { vr = false; stackMaybeActive = false }
                    done(if (ok) AssistantResult.STOPPED else AssistantResult.FAILED)
                }
                VoicePlan.Step.BUSY -> done(AssistantResult.BUSY)
                VoicePlan.Step.RESET_THEN_START -> {
                    // Yığın 1'de kalmış olabilir: başlatma sessizce düşerdi. Önce durdur (durum 0'da zararsız), OK'i
                    // bekleyip başlat.
                    // Durdurma telefondan hata alırsa yığın 1'de kalır (HeadsetClientStateMachine hata dalı): bu yüzden
                    // stackMaybeActive true kalır; yalnızca doğrulanmış başlatma ya da telefonun +BVRA:0'ı değiştirir.
                    val stopOk = HfpClientApi.stopVoiceRecognition(p, d)
                    Log.i(TAG, "asistan: önce durdur (binder=$stopOk), sonra başlat")
                    vr = false
                    main.postDelayed({ io.execute { begin(p, d, done, afterReset = true) } }, STALE_RESTART_MS)
                }
                VoicePlan.Step.START -> begin(p, d, done)
            }
        }
    }

    /** io: AT+BVRA=1 ve doğrulama beklemesi. */
    private fun begin(p: Any, d: BluetoothDevice, done: (AssistantResult) -> Unit, afterReset: Boolean = false) {
        finishPending(AssistantResult.FAILED)
        // OK gelirse yığın sessizce 1 olur; doğrulanmasa da bir sonraki basışta önce durdurulur. Binder false dönse de
        // (yığın 1'de kalmış olabilir) true kalır.
        stackMaybeActive = true
        if (!HfpClientApi.startVoiceRecognition(p, d)) {
            if (afterReset) Log.i(TAG, "asistan adım: durdur+başlat sonrası başlatma binder'da reddedildi; yığın 1'de kalmış olabilir")
            return done(AssistantResult.FAILED)
        }
        pending = done
        pendingAfterReset = afterReset
        scoConnectingConfirmAt = 0L
        val t = Runnable {
            io.execute {
                val why = if (pendingAfterReset) " (durdur+başlat sonrası: yığın 1'de kalmış olabilir, durdurma hata almış olabilir)" else ""
                Log.i(TAG, "asistan adım: telefon ${CONFIRM_MS} ms içinde doğrulamadı (+BVRA/SCO yok)$why")
                finishPending(AssistantResult.UNCONFIRMED)
            }
        }
        pendingTimeout = t
        main.postDelayed(t, CONFIRM_MS)
    }

    /**
     * io: +BVRA:1 ya da SCO bağlanıyor/bağlı → bekleyen istek başarılı. [scoState] 1 (yalnızca bağlanıyor) ise an
     * kaydedilir: SCO [SCO_FALLBACK_MS] içinde 0'a düşerse vr geri alınır (başka ses kaynağı ya da kurulamayan SCO).
     */
    private fun confirm(scoState: Int?) {
        if (pending == null) return
        vr = true
        scoConnectingConfirmAt = if (scoState == 1) SystemClock.elapsedRealtime() else 0L
        Log.i(TAG, "asistan adım: doğrulandı (${if (scoState == null) "+BVRA:1" else "SCO=$scoState"}${if (pendingAfterReset) ", durdur+başlat sonrası" else ""})")
        finishPending(AssistantResult.STARTED)
    }

    private fun finishPending(result: AssistantResult) {
        val cb = pending ?: return
        pending = null
        pendingAfterReset = false
        pendingTimeout?.let { main.removeCallbacks(it) }
        pendingTimeout = null
        cb(result)
    }

    override fun stop(done: (AssistantResult) -> Unit) {
        if (!vr) return done(AssistantResult.FAILED)
        HfpLink.withProxy(ctx, PROXY_WAIT_MS, io) { p ->
            val d = if (p != null) HfpLink.connectedDevice() else null
            val ok = p != null && d != null && HfpClientApi.stopVoiceRecognition(p, d)
            if (ok) { vr = false; stackMaybeActive = false }
            done(if (ok) AssistantResult.STOPPED else AssistantResult.FAILED)
        }
    }

    private companion object {
        const val PROXY_WAIT_MS = 1_500L
        /** SCO kurulumu telefona göre gecikebilir; doğrulama penceresi 2,5 sn'den 4 sn'ye (araçta ölçülecek). */
        const val CONFIRM_MS = 4_000L
        const val STALE_RESTART_MS = 700L
        /** "Bağlanıyor" doğrulamasından sonra SCO bu süre içinde 0'a düşerse doğrulama geri alınır. */
        const val SCO_FALLBACK_MS = 1_000L
        /** BluetoothHeadsetClient.STATE_AUDIO_DISCONNECTED. */
        const val AUDIO_DISCONNECTED = 0
    }
}
