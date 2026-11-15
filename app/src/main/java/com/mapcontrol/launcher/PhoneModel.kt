package com.mapcontrol.launcher

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log
import com.desaysv.ivi.vdb.event.id.bt.VDValueBT
import java.lang.ref.WeakReference
import java.util.Locale

/*
 * Telefon özelliğinin ortak sözleşmesi (UI ve arka uç paketleri buna göre derlenir).
 *
 * GİZLİLİK: Bu dosyadaki isim ve numaralar yalnızca ekranda gösterilir. Hiçbir zaman günlüğe (Log), dosyaya,
 * SharedPreferences'a (sabitlenmiş favori KİMLİKLERİ hariç), çökme metnine yazılmaz. Tek istisna [PhoneCache]: son
 * rehber/geçmiş, bağlı telefonun adres ÖZETİYLE anahtarlanmış tek bir uygulamaya özel dosyada (noBackupFilesDir,
 * yedeğe girmez, fotoğrafsız) tutulur; uygulama yeniden başlayınca aynı telefon bağlıysa anında gösterilir, OEM
 * önbelleği gelince güncellenir, farklı telefon bağlanınca silinir. Veri sınıflarının toString'i isim/numara içermez.
 *
 * Arama, cevaplama, kapatma ve sesli asistan YALNIZCA kullanıcının açık bir eylemiyle (dokunuş ya da direksiyon tuşu)
 * çağrılır. Araç yapılandırmasına hiçbir yazma yoktur.
 */

/** Rehberden (PBAP) ya da arama geçmişinden türetilen bir kişi. [id] kaynağa göre kararlıdır (sabitleme bununla). */
data class PhoneContact(
    val id: String,
    val name: String,
    val number: String,
    val starred: Boolean = false,
    val initials: String = PhoneText.initials(name),
    /**
     * Fotoğraf varsa [PhonePhotos] anahtarı (genelde [id]); yoksa null (UI baş harf gösterir). Görüntü yalnızca bellekte
     * ([PhonePhotos]); diske yazılmaz.
     */
    val photoKey: String? = null,
) {
    override fun toString(): String = "PhoneContact(starred=$starred)"
}

enum class RecentType { MISSED, INCOMING, OUTGOING }

/** Son arama. [name] rehberde yoksa null (UI numarayı gösterir). [timeMs] duvar saati (System.currentTimeMillis). */
data class PhoneRecent(
    val type: RecentType,
    val name: String?,
    val number: String,
    val timeMs: Long,
    /** Rehberde eşleşen kişinin kimliği (yoksa null). "Son arama" kartı fotoğrafı/ismi bununla bulur. */
    val contactId: String? = null,
    /** Eşleşen kişinin [PhonePhotos] anahtarı (yoksa null). */
    val photoKey: String? = null,
) {
    override fun toString(): String = "PhoneRecent(type=$type)"
}

enum class CallKind { NONE, INCOMING, DIALING, ACTIVE, ENDED }

/**
 * Şu anki arama. [startedAtMs] arama ACTIVE olduğu andaki SystemClock.elapsedRealtime() (sayaç bundan hesaplanır;
 * ACTIVE değilse 0; konuşma sırasında bekleyen arama gelince alttaki konuşmanınki saklanır). [muted]: mikrofon kapalı. ENDED kısa süre (arka uç ~2 sn) gösterilip NONE'a döner.
 */
data class CallState(
    val kind: CallKind = CallKind.NONE,
    val name: String? = null,
    val number: String? = null,
    val startedAtMs: Long = 0L,
    val muted: Boolean = false,
    /**
     * Ses telefonda (SCO kopuk; OEM "Sesi telefona aktar" seçili). VDBus DIAL_AUDIO_MODE (262548) yayını/getOnce:
     * status 1 araç, 2 telefon, 3 bağlanıyor.
     */
    val audioOnPhone: Boolean = false,
    /** Arayanın fotoğrafı için [PhonePhotos] anahtarı (VDBTDialInfo.portrait ya da eşleşen kişi); yoksa null. */
    val photoKey: String? = null,
) {
    val inCall: Boolean get() = kind == CallKind.INCOMING || kind == CallKind.DIALING || kind == CallKind.ACTIVE
    override fun toString(): String = "CallState(kind=$kind, muted=$muted, audioOnPhone=$audioOnPhone, photo=${photoKey != null})"

    companion object {
        val IDLE = CallState()
    }
}

/** Seçilen arka uçların adları (günlüğe yalnızca bunlar yazılır). */
object PhoneBackendName {
    const val NONE = "none"
    const val DEMO = "demo"
    const val VDBUS = "vdbus"
    const val TELECOM = "telecom"
    const val HFP_CLIENT = "hfp_client"
    const val CONTACTS_PROVIDER = "contacts_provider"
}

/**
 * Ekranın tek veri kaynağı. Değişmez; her değişiklikte yeni örnek gelir (UI eşitlik karşılaştırmasıyla gereksiz
 * çizimi atlar).
 *
 * - [connected]: HFP ile bağlı bir telefon var.
 * - [deviceName]: bağlı telefonun adı (yoksa null).
 * - [backend]: arama/durum için seçilen arka uç ([PhoneBackendName]); veri arka ucu [dataBackend].
 * - [permissionNeeded]: rehber/geçmiş için izin eksik ve başka veri yolu da yok (UI "İzin ver" düğmesi gösterir).
 * - [permissionBlocked]: izin reddedildi ve sistem yeniden sormuyor (UI "Ayarları aç" gösterir).
 * - [loaded]: veri en az bir kez okundu (false iken UI "yükleniyor"/boş durum ayırt eder).
 * - [canDial]: arama başlatabilen bir yol var (yoksa UI telefon düğmesinde OEM uygulamasını açar).
 * - [canAssistant]: sesli asistan yolu var. [canMute]: mikrofon kapatma yolu var (yoksa "Sessiz" gizlenir).
 */
data class PhoneSnapshot(
    val connected: Boolean = false,
    val deviceName: String? = null,
    val backend: String = PhoneBackendName.NONE,
    val dataBackend: String = PhoneBackendName.NONE,
    val permissionNeeded: Boolean = false,
    /** İzin istendi, verilmedi ve sistem artık sormuyor: UI uygulama ayarlarını açmayı önerir. */
    val permissionBlocked: Boolean = false,
    val loaded: Boolean = false,
    val canDial: Boolean = false,
    val canAssistant: Boolean = false,
    val canMute: Boolean = false,
    /** Sesi telefona/araca aktarma yolu var (VDBus DIAL_AUDIO_MODE). Yoksa "Telefona aktar" gizlenir. */
    val canAudioTransfer: Boolean = false,
    /** Tuş takımı (DTMF) yolu var (VDBus DIAL ctrl 4). Yoksa "Tuş takımı" gizlenir. */
    val canDtmf: Boolean = false,
    val favorites: List<PhoneContact> = emptyList(),
    val recents: List<PhoneRecent> = emptyList(),
    val call: CallState = CallState.IDLE,
    /**
     * Rehber/geçmiş listesindeki toplam sayılar (bellekteki tam depo; "Tümü" sayfası [PhoneSource.directory] ile okur).
     * Snapshot'a tam liste konmaz: her durum değişiminde büyük liste kopyalanıp karşılaştırılmasın.
     */
    val contactCount: Int = 0,
    val recentCount: Int = 0,
    /** Depo sürümü: tam liste değişince artar (Tümü sayfası yalnız bu değişince yeniden kurar). */
    val directoryVersion: Int = 0,
) {
    override fun toString(): String =
        "PhoneSnapshot(connected=$connected, backend=$backend, data=$dataBackend, perm=$permissionNeeded, loaded=$loaded, " +
            "fav=${favorites.size}, recents=${recents.size}, all=$contactCount/$recentCount v$directoryVersion, call=$call)"

    companion object {
        val EMPTY = PhoneSnapshot()
    }
}

/** Bir eylemin neden yapılamadığı; UI kendi metnine çevirir (arka uç metin kaynağı eklemez). */
enum class PhoneError { NO_PHONE, NOT_SUPPORTED, FAILED, ASSISTANT_UNAVAILABLE, PERMISSION }

/** Dinleyici: her iki çağrı da ANA iş parçacığında gelir. */
interface PhoneListener {
    fun onPhoneChanged(snapshot: PhoneSnapshot)
    fun onPhoneError(error: PhoneError) {}
}

/**
 * Telefon durumu ve eylemleri. UI yalnızca bunu çağırır. Arka uçlar PhoneBackends.kt'de (VDBus BT, Telecom, HFP
 * istemci, rehber/geçmiş), seçim kuralı PhoneLogic.kt'de ([PhoneBackendChooser]), demo PhoneDemo.kt'de.
 *
 * Ömür: [start]/[stop] ekran görünürlüğüne bağlıdır (HomeLauncherActivity.onStart/onStop; telefon paneli sayfası da
 * açılınca start, kapanınca stop çağırabilir). Birden çok sahip olabilir; son sahip [stop] edince dinleme ve
 * okumalar durur (görünmezken sorgu yok). Direksiyon ses tuşu dinleyicisi ayrı ömürlüdür: [syncVoiceKey] /
 * [stopVoiceKey] (etkinlik ömrü, FavoriteKey gibi).
 *
 * Eylemler ana iş parçacığından çağrılır, hemen döner; Binder/VDBus işi arka plan kuyruğundadır. Dönüş değeri
 * "bir yol isteği kabul etti" demektir; sonradan başarısızlık [PhoneListener.onPhoneError] ile gelir.
 */
object PhoneSource {
    /** HomeLauncherActivity.onRequestPermissionsResult bu kodu [onPermissionsResult]'a iletir. */
    const val REQ_PERMISSIONS = 4711

    private const val TAG = "PhoneSource"
    private const val ENDED_MS = 2_000L
    private const val REREAD_DEBOUNCE_MS = 1_000L
    private const val SETTLE_MS = 300L
    private const val VDBUS_WAIT_MS = 3_000L
    private const val ASSISTANT_IDLE_CLOSE_MS = 10_000L
    private const val MAX_FAVORITES = 6
    /** Bir veri sahibi geri geldiğinde bu süreden eski veri yeniden okunur (gözlemci yokken kaçan değişiklikler için). */
    private const val DATA_STALE_MS = 120_000L
    /** VDBus isteği ateşle-unut: bu süre içinde arama durumu beklenen yöne gitmezse sıradaki yol bir kez denenir. */
    private const val CONFIRM_MS = 1_500L
    private const val CONFIRM_DIAL_MS = 2_500L
    /** Panel açılışında bu süreden yeni okuma tekrarlanmaz. */
    private const val FRESH_MS = 10_000L
    /** PHONE_BOOK_SYNC "bitti" yayınlarından sonra önbellek isteği (rehber ve geçmiş ayrı ayrı biter; tek istek). */
    private const val SYNC_READ_DELAY_MS = 800L
    /** VDValueBT.SyncStatus.FINISHED. */
    private const val VDSYNC_FINISHED = 1
    private const val DEMO_VERSION_OFFSET = 1_000_000
    /** Depo değişince disk önbelleği bu gecikmeyle yazılır (parça parça gelen indirme tek yazım olsun). */
    private const val SAVE_DELAY_MS = 3_000L

    private val io = LauncherIo.serial()
    private val main = Handler(Looper.getMainLooper())

    // Yalnızca ana iş parçacığında.
    private val owners = ArrayList<WeakReference<Any>>()
    /** Rehber/geçmiş isteyen sahipler ([start] wantsData). */
    private val dataOwners = ArrayList<WeakReference<Any>>()
    private val listeners = ArrayList<PhoneListener>()
    private var receiverRegistered = false
    private var observerRegistered = false

    @Volatile private var snap = PhoneSnapshot.EMPTY
    @Volatile private var started = false
    @Volatile private var appContext: Context? = null
    @Volatile private var caps = PhoneBackendChooser.Caps()
    /** En az bir sahip rehber/geçmiş istiyor (yoksa veri okunmaz). */
    @Volatile private var wantData = false
    /** Sahip kalmadı ama sessizdeki görüşme bitene kadar durum kaynağı açık (bkz. [stop]). */
    @Volatile private var lingering = false

    // Arka uçlar (ilk kullanımda kurulur; durumları yalnızca io kuyruğunda değişir).
    private val demo = DemoBackend()
    private var vdbus: VdbusCallBackend? = null
    private var telecom: TelecomCallBackend? = null
    private var hfp: HfpClientCallBackend? = null
    private var contactsData: ContactsDataBackend? = null
    private var vdbusData: VdbusDataBackend? = null
    private var hfpAssistant: HfpAssistant? = null

    // Yalnızca io kuyruğunda.
    private var stateSource: CallBackend? = null
    private var lastDataBackend: String? = null
    private var lastReadAt = 0L
    private var mutedByMic = false
    /**
     * Bellekteki tam rehber/geçmiş (bağlı cihaz başına; [DirectoryStore]). Tercihlere, günlüğe asla yazılmaz; yalnız
     * [PhoneCache] dosyasına (uygulamaya özel, cihaz özetli) kopyalanır. Demo ayrı depoda tutulur ve diske yazılmaz.
     */
    private val store = DirectoryStore()
    private val demoStore = DirectoryStore()
    /** Bellekte: normalize numara → kişi (arayan ismi/fotoğrafı için). Diske yazılmaz. */
    private var byNumber: Map<String, PhoneContact> = emptyMap()
    /** Bu HFP bağlantısında indirme istendi (bağlantı başına en çok bir kez). */
    private var downloadRequested = false
    private var lastHfpConnected = false
    /** Eşitleme bitti ama görünür değildik: sonraki başlangıçta önbellek okunur. */
    private var pendingSyncRead = false
    private var witnessOpen = false
    /** Bağlı telefonun adres özeti ([PhoneCache.deviceHash]; ham adres tutulmaz). Bilinmiyorsa null: diske yazılmaz. */
    private var deviceHash: String? = null
    /** Diske son yazılan depo sürümü ([DirectoryStore.version]); aynıysa yeniden yazılmaz. */
    private var savedVersion = -1
    /**
     * Depodaki veri bu süreçte canlı bir kaynaktan (OEM önbelleği, indirme parçası, sağlayıcı) geldi. Yalnız diskten
     * geri yüklenmiş veri indirme kararında "dolu" sayılmaz ([maybeDownload]).
     */
    private var liveData = false

    private fun activeStore(): DirectoryStore = if (caps.demo) demoStore else store

    private val endedTask = Runnable {
        io.execute {
            if (snap.call.kind != CallKind.ENDED) return@execute
            update { it.copy(call = CallState.IDLE) }
            // Arama geçmişi değişti (VDBus'ta OEM kendisi indirir; parçalar edilgen gelir).
            refreshInternal(RefreshReason.CALL_ENDED)
        }
    }
    private val rereadTask = Runnable { io.execute { if (started) readData() } }
    private val reprobeTask = Runnable { io.execute { if (started) { reprobe(); refreshInternal(RefreshReason.HFP_CONNECTED) } } }
    private val saveTask = Runnable { io.execute { saveCache() } }
    private val syncReadTask = Runnable {
        io.execute {
            if (started && wantData) readVdbusCache() else pendingSyncRead = true
        }
    }

    /**
     * Arama durumu yoklama yedeği ([CallPoll]): YALNIZCA arama sürerken 2 sn'de bir taze okuma (VDBus getOnce
     * DIAL_INFO_LIST / HFP getCurrentCalls). Arama yokken hiç çalışmaz.
     */
    private val pollTask = object : Runnable {
        override fun run() {
            if (!CallPoll.shouldPoll(snap.call.kind, started || lingering)) return
            io.execute {
                if (!CallPoll.shouldPoll(snap.call.kind, started || lingering)) return@execute
                try {
                    stateSource?.refreshState()
                } catch (t: Throwable) {
                    Log.w(TAG, "yoklama: ${t.javaClass.simpleName}")
                }
            }
            main.postDelayed(this, CallPoll.INTERVAL_MS)
        }
    }

    private fun startPoll() {
        main.post {
            main.removeCallbacks(pollTask)
            main.postDelayed(pollTask, CallPoll.INTERVAL_MS)
        }
    }

    private fun stopPoll() {
        main.post { main.removeCallbacks(pollTask) }
    }
    private val vdbusCheck = Runnable {
        io.execute {
            if (!started) return@execute
            if (vdbus?.available() != true) Log.i(TAG, "vdbus BT ${VDBUS_WAIT_MS / 1000} sn içinde bağlanmadı")
            reprobe()
        }
    }
    private val idleClose = Runnable { if (!started && !lingering) HfpLink.close() }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            // Bağlantı yayını durum oturmadan gelebilir: kısa gecikmeyle yeniden yoklanır ve veri okunur.
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val st = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                com.mapcontrol.GlitchTip.breadcrumb("bt", "adapter state=$st call=${snap.call.kind}")
            }
            main.removeCallbacks(reprobeTask)
            main.postDelayed(reprobeTask, SETTLE_MS)
        }
    }

    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            main.removeCallbacks(rereadTask)
            main.postDelayed(rereadTask, REREAD_DEBOUNCE_MS)
        }
    }

    private fun demoActive(): Boolean = LauncherDemo.enabled && PhoneDemo.active

    /** Ana iş parçacığında: arka uç nesnelerini kurar (hafif; bağlanma yok). */
    private fun ensureBackends(app: Context) {
        if (vdbus != null) return
        val vd = VdbusCallBackend(io) { if (started) reprobe() }
        vdbus = vd
        telecom = TelecomCallBackend(app)
        hfp = HfpClientCallBackend(app, io)
        contactsData = ContactsDataBackend(app)
        vdbusData = VdbusDataBackend(vd)
        hfpAssistant = HfpAssistant(app, io)
    }

    private fun callBackendFor(name: String): CallBackend? = when (name) {
        PhoneBackendName.DEMO -> demo
        PhoneBackendName.VDBUS -> vdbus
        PhoneBackendName.TELECOM -> telecom
        PhoneBackendName.HFP_CLIENT -> hfp
        else -> null
    }

    private fun dataBackendFor(name: String): DataBackend? = when (name) {
        PhoneBackendName.DEMO -> demo
        PhoneBackendName.CONTACTS_PROVIDER -> contactsData
        PhoneBackendName.VDBUS -> vdbusData
        else -> null
    }

    /**
     * Görünür sahip ekler; ilk sahipte arka uçlar seçilir. [owner] genelde Activity ya da panel. [wantsData]: rehber ve
     * geçmiş de gerekir (Telefon paneli/sayfası). Yalnız arama durumu isteyen sahipler (arama şeridi) false verir: o
     * zaman veri okunmaz, içerik gözlemcisi kurulmaz. Veri bellekte tutulur; yeni bir veri sahibi geldiğinde yalnızca
     * hiç okunmadıysa, arka uç değiştiyse ya da [DATA_STALE_MS]'den eskiyse yeniden okunur.
     */
    fun start(context: Context, owner: Any = context, wantsData: Boolean = true) {
        val app = context.applicationContext
        appContext = app
        owners.removeAll { it.get() == null }
        dataOwners.removeAll { it.get() == null }
        if (owners.none { it.get() === owner }) owners.add(WeakReference(owner))
        if (wantsData && dataOwners.none { it.get() === owner }) dataOwners.add(WeakReference(owner))
        val firstStart = !started
        if (firstStart) {
            started = true
            ensureBackends(app)
            main.removeCallbacks(idleClose)
            val vd = vdbus!!
            if (vd.bus == null) vd.prepare(app)
            registerReceiver(app)
            HfpLink.onProxyChanged = { io.execute { if (started) reprobe() } }
            HfpLink.open(app)
            io.execute {
                if (!started) return@execute
                // Arka planda sessizde kalmış bir görüşme izleniyorduysa (bkz. [stop]) artık normal sahiplik sürer.
                lingering = false
                vd.connect()
                reprobe()
                applyDemoCall()
                // Sessiz açıkken görüşme biz yokken bittiyse: ilk durum okuması görüşme göstermiyorsa mikrofon açılır.
                if (!snap.call.inCall) unmuteIfNeeded()
            }
            main.postDelayed(vdbusCheck, VDBUS_WAIT_MS)
        }
        if (dataOwners.isNotEmpty() && !wantData) {
            wantData = true
            registerObservers(app)
            io.execute {
                if (!started || !wantData) return@execute
                val stale = lastReadAt == 0L || SystemClock.elapsedRealtime() - lastReadAt > DATA_STALE_MS
                if (pendingSyncRead) {
                    pendingSyncRead = false
                    readVdbusCache()
                }
                if (stale || PhoneBackendChooser.dataBackend(caps) != lastDataBackend) refreshInternal(RefreshReason.APP_START)
            }
        }
    }

    /**
     * Sahibi çıkarır; sahip kalmazsa dinleme/okuma durur. Sessiz açıkken görüşme sürüyorsa yalnızca arama durumu
     * kaynağı açık kalır ([lingering]): görüşme bitince mikrofon açılır ve kaynak kapanır (kullanıcı sessiz sanırken
     * mikrofon kendiliğinden açılmasın, görüşme bitince de kapalı kalmasın).
     */
    fun stop(owner: Any) {
        owners.removeAll { it.get() == null || it.get() === owner }
        dataOwners.removeAll { it.get() == null || it.get() === owner }
        val app = appContext
        if (dataOwners.isEmpty() && wantData) {
            wantData = false
            if (app != null) unregisterObservers(app)
        }
        if (owners.isNotEmpty() || !started) return
        started = false
        main.removeCallbacks(vdbusCheck)
        main.removeCallbacks(rereadTask)
        main.removeCallbacks(reprobeTask)
        main.removeCallbacks(endedTask)
        main.removeCallbacks(syncReadTask)
        if (app != null) unregisterReceiver(app)
        HfpLink.onProxyChanged = null
        io.execute {
            if (started) return@execute
            if (mutedByMic && snap.call.inCall && stateSource != null) {
                lingering = true
                Log.i(TAG, "durdu; sessiz görüşme bitene kadar durum izleniyor")
                // Yoklama yedeği sahipsiz izlemede de sürer (arama bitince kendiliğinden durur).
                startPoll()
                return@execute
            }
            teardown()
        }
    }

    /** io: durum kaynağını ve VDBus aboneliğini bırakır. Son veri bellekte kalır (diske yazılmaz). */
    private fun teardown() {
        lingering = false
        stopPoll()
        if (witnessOpen) {
            hfp?.closeWitness()
            witnessOpen = false
        }
        stateSource?.close()
        stateSource = null
        vdbus?.disconnect()
        unmuteIfNeeded()
        caps = PhoneBackendChooser.Caps()
        update { it.copy(call = CallState.IDLE) }
        // Asistan az önce kullanıldıysa HFP vekili biraz daha açık kalır.
        main.post {
            if (started) return@post
            main.removeCallbacks(idleClose)
            main.postDelayed(idleClose, if (assistantRecently()) ASSISTANT_IDLE_CLOSE_MS else 0L)
        }
        Log.i(TAG, "durdu")
    }

    fun snapshot(): PhoneSnapshot = snap

    /** Eklenince güncel durum hemen (ana iş parçacığında) bir kez bildirilir. */
    fun addListener(listener: PhoneListener) {
        if (listener in listeners) return
        listeners.add(listener)
        main.post { if (listener in listeners) listener.onPhoneChanged(snap) }
    }

    fun removeListener(listener: PhoneListener) {
        listeners.remove(listener)
    }

    /** Numarayı arar. Bağlı telefon ya da yol yoksa false. */
    fun dial(number: String): Boolean {
        val n = number.filterNot { it.isWhitespace() }
        if (n.isEmpty()) return false
        // Herhangi bir arama durumu (aranıyor/konuşma) isteğin alındığını gösterir.
        return act(PhoneAction.DIAL, { it.kind != CallKind.NONE && it.kind != CallKind.ENDED }) { it.dial(n) }
    }

    /** Gelen aramayı cevaplar. */
    fun answer(): Boolean = act(PhoneAction.ANSWER, { it.kind != CallKind.INCOMING }) { it.answer() }

    /** Gelen aramayı reddeder. */
    fun reject(): Boolean = actLive(PhoneAction.REJECT, { !it.inCall }) { it.reject() }

    /**
     * Aktif/çıkan aramayı kapatır.
     *
     * SÖZLEŞME (PHONE-BACKEND, sağlam kapatma): io'da önce durum kaynağından taze okuma ([CallBackend.hasLiveCall]);
     * OEM tarafında arama YOKSA (false) bizim durum doğrudan ENDED→IDLE yapılır, hata gösterilmez, true döner. Arama
     * varsa mevcut yol (uuid'li DIAL ctrl 3 → HFP → Telecom); onay süresi dolunca bir taze okuma daha, yine yoksa temizle.
     */
    fun hangUp(): Boolean = actLive(PhoneAction.HANG_UP, { !it.inCall }) { it.hangUp() }

    /**
     * Kapat/Reddet: önce OEM'de canlı arama var mı ([HangUpPlan]). Yoksa yalnızca bizim durum temizlenir (hata yok).
     * Telefon bağlı değilken ekranda kalmış bir arama da temizlenir.
     */
    private fun actLive(action: PhoneAction, done: (CallState) -> Boolean, op: (CallBackend) -> Boolean): Boolean {
        val label = endLabel(action)
        val order = PhoneBackendChooser.fallbackOrder(caps, action)
        if (!snap.connected || order.isEmpty()) {
            if (snap.call.inCall) {
                io.execute { clearLocal(label) }
                return true
            }
            fail(if (!snap.connected) PhoneError.NO_PHONE else PhoneError.NOT_SUPPORTED)
            return false
        }
        io.execute {
            when (HangUpPlan.decide(liveOnSource(), snap.call.inCall)) {
                HangUpPlan.Decision.CLEAR_LOCAL -> clearLocal(label)
                HangUpPlan.Decision.SEND -> runFrom(order, 0, label, action, done, op, verifyLive = true)
                HangUpPlan.Decision.FAIL -> {
                    Log.i(TAG, "$label: başarısız")
                    fail(PhoneError.FAILED)
                }
            }
        }
        return true
    }

    private fun endLabel(action: PhoneAction): String = if (action == PhoneAction.REJECT) "reddet" else "kapat"

    /** io: durum kaynağında canlı arama var mı (taze okuma). Demo ya da hata: null (bilinmiyor). */
    private fun liveOnSource(): Boolean? {
        if (caps.demo) return null
        return try {
            stateSource?.hasLiveCall()
        } catch (t: Throwable) {
            Log.w(TAG, "canlı arama okuması: ${t.javaClass.simpleName}")
            null
        }
    }

    /** io: OEM'de arama yok; bizim durum ENDED → (2 sn) IDLE. Hata gösterilmez. */
    private fun clearLocal(label: String) {
        Log.i(TAG, "$label: OEM'de arama yok, durum temizlendi")
        applyState(RawCall.NONE)
    }

    /**
     * Ortak eylem yolu: bağlı telefon yoksa NO_PHONE, yol yoksa NOT_SUPPORTED (false). Varsa io'da sırayla denenir;
     * ilki başarısızsa bir sonraki bir kez; hepsi başarısızsa FAILED. VDBus isteği ateşle-unut olduğu için kabul
     * edilmesi yetmez: [done] arama durumunda kısa süre içinde görülmezse sıradaki yol bir kez denenir (durum kaynağı
     * varsa; yoksa doğrulanamaz).
     */
    private fun act(action: PhoneAction, done: (CallState) -> Boolean, op: (CallBackend) -> Boolean): Boolean {
        if (!snap.connected) {
            fail(PhoneError.NO_PHONE)
            return false
        }
        val order = PhoneBackendChooser.fallbackOrder(caps, action)
        if (order.isEmpty()) {
            fail(PhoneError.NOT_SUPPORTED)
            return false
        }
        val label = action.name.lowercase()
        io.execute { runFrom(order, 0, label, action, done, op) }
        return true
    }

    /** io: [order]'ı [from] sırasından dener; doğrulanması gereken bir yol kabul ederse sonucu sonra denetler. */
    private fun runFrom(
        order: List<String>, from: Int, label: String, action: PhoneAction,
        done: (CallState) -> Boolean, op: (CallBackend) -> Boolean,
        verifyLive: Boolean = false,
    ) {
        for (i in from until order.size) {
            val name = order[i]
            val ok = try {
                callBackendFor(name)?.let(op) == true
            } catch (t: Throwable) {
                Log.w(TAG, "$label: $name ${t.javaClass.simpleName}")
                false
            }
            if (ok) {
                Log.i(TAG, "$label: $name")
                val next = i + 1
                if (name == PhoneBackendName.VDBUS && (next < order.size || verifyLive) && stateSource != null) {
                    val wait = if (action == PhoneAction.DIAL) CONFIRM_DIAL_MS else CONFIRM_MS
                    main.postDelayed({
                        io.execute {
                            if (!started || done(snap.call)) return@execute
                            if (verifyLive) {
                                // Durum değişmedi: belki arama zaten bitmişti (yayın kaçtı). Taze okumayla karar ver.
                                val live = liveOnSource()
                                if (HangUpPlan.onTimeout(live) == HangUpPlan.Decision.CLEAR_LOCAL) return@execute clearLocal(label)
                                if (next >= order.size) {
                                    Log.i(TAG, "$label: başarısız")
                                    if (HangUpPlan.onExhausted(live) == HangUpPlan.Decision.FAIL) fail(PhoneError.FAILED)
                                    return@execute
                                }
                            }
                            Log.i(TAG, "$label: $name yanıt vermedi, ${order[next]} deneniyor")
                            runFrom(order, next, label, action, done, op, verifyLive)
                        }
                    }, wait)
                }
                return
            }
            order.getOrNull(i + 1)?.let { Log.i(TAG, "$label: $name başarısız, $it deneniyor") }
        }
        if (verifyLive && HangUpPlan.onExhausted(liveOnSource()) == HangUpPlan.Decision.CLEAR_LOCAL) return clearLocal(label)
        Log.i(TAG, "$label: başarısız")
        fail(PhoneError.FAILED)
    }

    /** Mikrofonu kapatır/açar ([CallState.muted] değişir). */
    fun toggleMute(): Boolean {
        val s = snap
        if (!s.call.inCall) return false
        if (!s.canMute) {
            fail(PhoneError.NOT_SUPPORTED)
            return false
        }
        io.execute {
            val target = !snap.call.muted
            val be = callBackendFor(PhoneBackendChooser.callBackend(caps))
            val app = appContext
            // VDBus: DIAL_MIC_MODE {"status": 2 sessiz / 1 açık} (OEM servisi sistem uid'siyle AudioManager'ı çağırır).
            // HFP/diğerleri ya da VDBus başarısızsa doğrudan AudioManager.
            var ok = be?.setMute(target) == true
            if (!ok && be != null && be.name != PhoneBackendName.DEMO && app != null) ok = MicMute.set(app, target)
            if (ok && be?.name != PhoneBackendName.DEMO) mutedByMic = target
            if (ok) update { it.copy(call = it.call.copy(muted = target)) } else fail(PhoneError.NOT_SUPPORTED)
            Log.i(TAG, "sessiz: ${if (ok) target else "başarısız"}")
        }
        return true
    }

    /**
     * Sesi telefona ([toPhone] true) ya da araca aktarır (OEM "Sesi telefona aktar"). Yalnızca arama sürerken ve
     * [PhoneSnapshot.canAudioTransfer] iken; sonuç [CallState.audioOnPhone] ile (DIAL_AUDIO_MODE yayını) gelir.
     *
     * SÖZLEŞME (PHONE-BACKEND): io'da [CallBackend.setAudioToPhone]; başarıda iyimser güncelleme, sonra yayın/getOnce
     * doğrular; başarısızsa NOT_SUPPORTED.
     */
    fun setAudioToPhone(toPhone: Boolean): Boolean {
        val s = snap
        if (!s.call.inCall) return false
        if (!s.canAudioTransfer) {
            fail(PhoneError.NOT_SUPPORTED)
            return false
        }
        io.execute {
            val be = callBackendFor(PhoneBackendChooser.callBackend(caps))
            val ok = be?.setAudioToPhone(toPhone) == true
            if (ok) update { it.copy(call = it.call.copy(audioOnPhone = toPhone)) } else fail(PhoneError.NOT_SUPPORTED)
            Log.i(TAG, "ses yönü: ${if (ok) (if (toPhone) "telefon" else "araç") else "başarısız"}")
        }
        return true
    }

    /** Ses yönünü tersine çevirir (tek düğme). */
    fun toggleAudioTarget(): Boolean = setAudioToPhone(!snap.call.audioOnPhone)

    /**
     * Konuşma sırasında tek DTMF tuşu ('0'..'9', '*', '#'). Yalnızca ACTIVE ve [PhoneSnapshot.canDtmf] iken. Tuş günlüğe
     * yazılmaz (kart/PIN girişi olabilir).
     */
    fun sendDtmf(c: Char): Boolean {
        val s = snap
        if (s.call.kind != CallKind.ACTIVE || !DtmfCode.valid(c)) return false
        if (!s.canDtmf) {
            fail(PhoneError.NOT_SUPPORTED)
            return false
        }
        io.execute {
            val ok = callBackendFor(PhoneBackendChooser.callBackend(caps))?.sendDtmf(c) == true
            if (!ok) fail(PhoneError.FAILED)
        }
        return true
    }

    /**
     * Bellekteki tam rehber/geçmiş ("Tümü" sayfası). Herhangi bir iş parçacığında; değişmez kopya. Diske yazılmaz.
     * Sürüm [PhoneSnapshot.directoryVersion] ile aynıdır.
     */
    fun directory(): PhoneDirectory = directorySnap

    /**
     * Rehber/geçmişi kaynaktan yeniden ister (panel/Tümü açılınca, HFP bağlanınca). Bellekteki dolu depo boş yanıtla
     * silinmez. SÖZLEŞME (PHONE-BACKEND): [PhonebookRefresh] kararına göre önbellek isteği ya da (yalnızca
     * [RefreshReason.USER_OPENED] ile) bağlantı başına bir kez PBAP indirmesi.
     */
    fun refreshData(reason: RefreshReason) {
        io.execute { refreshInternal(reason) }
    }

    /**
     * io: yenileme politikası. HFP bağlandı / açılış / panel → yalnızca önbellek (okuma). Kullanıcı rehber sayfasını
     * açtı → okuma + gerekirse bağlantı başına bir kez indirme ([maybeDownload]). Arama bitti → VDBus'ta istek yok
     * (OEM telefon uygulaması her aramadan sonra geçmiş indirmesini kendisi başlatır;
     * parçalar ve "bitti" durumu edilgen gelir); rehber sağlayıcısında yerel okuma.
     */
    private fun refreshInternal(reason: RefreshReason) {
        if (!started || !wantData) return
        val name = PhoneBackendChooser.dataBackend(caps)
        val fresh = lastReadAt != 0L && SystemClock.elapsedRealtime() - lastReadAt < FRESH_MS
        Log.i(TAG, "veri yenile: ${reason.name.lowercase()}${if (fresh) " (yeni okundu)" else ""}")
        when (reason) {
            RefreshReason.CALL_ENDED -> if (name == PhoneBackendName.CONTACTS_PROVIDER) readData()
            RefreshReason.PANEL_OPENED -> if (!fresh) readData()
            RefreshReason.APP_START, RefreshReason.HFP_CONNECTED -> readData()
            RefreshReason.USER_OPENED -> {
                if (!fresh || !activeStore().hasData) readData()
                maybeDownload()
            }
        }
    }

    /**
     * io: depo ve OEM önbelleği boşsa (yeniden kurulumdan sonra: önbellek yalnızca bir PBAP indirmesiyle dolar) bu
     * HFP bağlantısında BİR KEZ indirme ister. Yalnızca [RefreshReason.USER_OPENED] ile (kullanıcının açık eylemi).
     */
    private fun maybeDownload() {
        if (caps.demo || !caps.vdbusData) return
        val vd = vdbusData ?: return
        // Diskten geri yüklenen veri eski olabilir: indirme kararında yalnız bu süreçte canlı gelen veri sayılır.
        val has = store.hasData && liveData
        if (has) return
        var step = PhonebookRefresh.next(has, vd.cacheTotal, null, downloadRequested)
        if (step == PhonebookRefresh.Step.CACHE) {
            readVdbusCache()
            if (vd.cacheTotal == null) {
                Log.i(TAG, "rehber: OEM önbelleği yanıt vermedi, indirme istenmedi")
                return
            }
            step = PhonebookRefresh.next(store.hasData && liveData, vd.cacheTotal, null, downloadRequested)
        }
        if (step != PhonebookRefresh.Step.DOWNLOAD) {
            Log.i(TAG, "rehber: indirme gerekmiyor (önbellek=${vd.cacheTotal} istendi=$downloadRequested)")
            return
        }
        // OEM ana ekranının tarifi: durum tür başına sorulur (7 rehber, 6 geçmiş).
        val status = PhonebookRefresh.combine(
            vd.syncStatus(VDValueBT.PhoneBookSyncType.CONTACT_SYNC),
            vd.syncStatus(VDValueBT.PhoneBookSyncType.CALLLOG_SYNC),
        )
        step = PhonebookRefresh.next(store.hasData && liveData, vd.cacheTotal, status, downloadRequested)
        if (step != PhonebookRefresh.Step.DOWNLOAD) {
            Log.i(TAG, "rehber: indirme sürüyor ya da gerekmiyor (durum=$status)")
            return
        }
        if (vd.requestDownload()) {
            downloadRequested = true
            Log.i(TAG, "rehber: indirme istendi (durum=$status)")
        } else {
            Log.i(TAG, "rehber: indirme isteği gönderilemedi")
        }
    }

    /** io: VDBus önbelleğini (rehber + geçmiş) okur ve depoya katar (boş yanıt depoyu silmez). */
    private fun readVdbusCache() {
        if (caps.demo) return
        val app = appContext ?: return
        val vd = vdbusData ?: return
        if (!vd.available()) return
        val data = try {
            vd.read(PhonePrefs.pinnedIds(app))
        } catch (t: Throwable) {
            Log.w(TAG, "veri (vdbus): ${t.javaClass.simpleName}")
            null
        }
        Log.i(TAG, "rehber önbelleği: kişi=${data?.contacts?.size ?: -1} geçmiş=${data?.recents?.size ?: -1}")
        if (data != null) noteLive(data.contacts, data.recents)
        if (data != null && store.merge(data.contacts, data.recents)) publishStore()
    }

    @Volatile private var directorySnap = PhoneDirectory.EMPTY

    @Volatile private var assistantAt = 0L

    private fun assistantRecently(): Boolean =
        assistantAt != 0L && SystemClock.elapsedRealtime() - assistantAt < ASSISTANT_IDLE_CLOSE_MS

    /**
     * Bağlı telefonun sesli asistanı (Siri / Google, HFP BVRA): aç/kapa. Açıksa (bizim ya da telefonun başlattığı
     * oturum, AG_EVENT ile izlenir) kapatır. Launcher görünmezken de çalışır (direksiyon tuşu): HFP vekili gerekirse
     * açılır, sahip yoksa kullanımdan 10 sn sonra kapanır.
     *
     * Süren arama varken hiçbir şey yapmaz (true döner, uyarı yok): OEM de BT araması sırasında kendi asistanını
     * kapatır ve SCO sırasında BVRA istenmez. Başarı yalnızca telefon doğrulayınca
     * (+BVRA:1 ya da SCO sesi) sayılır; doğrulanmazsa ASSISTANT_UNAVAILABLE.
     */
    fun startVoiceAssistant(): Boolean {
        val app = appContext
        if (app == null) {
            fail(PhoneError.ASSISTANT_UNAVAILABLE)
            return false
        }
        if (started && snap.call.inCall && !demoActive()) {
            Log.i(TAG, "asistan: arama sürüyor, yok sayıldı")
            return true
        }
        val backend: AssistantBackend
        if (demoActive()) {
            backend = demo
        } else {
            val connected = if (started) snap.connected else HfpLink.isConnected()
            if (!connected) {
                Log.i(TAG, "asistan: bağlı telefon yok")
                fail(PhoneError.NO_PHONE)
                return false
            }
            ensureBackends(app)
            backend = hfpAssistant!!
        }
        assistantAt = SystemClock.elapsedRealtime()
        main.removeCallbacks(idleClose)
        io.execute { backend.start(::onAssistantResult) }
        return true
    }

    /** Asistan açıksa kapatır (direksiyon kısa basışı: OEM kendi asistanını açarken mikrofon bölünmesin). */
    fun stopVoiceAssistant() {
        val a = hfpAssistant ?: return
        if (!a.active) return
        assistantAt = SystemClock.elapsedRealtime()
        main.removeCallbacks(idleClose)
        io.execute { a.stop(::onAssistantResult) }
    }

    /** Telefonun asistan oturumu açık (bildiğimiz kadarıyla). Herhangi bir iş parçacığında. */
    fun voiceAssistantActive(): Boolean = hfpAssistant?.active == true

    /** io: asistan sonucu. Kişisel veri yok. */
    private fun onAssistantResult(r: AssistantResult) {
        Log.i(TAG, when (r) {
            AssistantResult.STARTED -> "asistan başladı (telefon doğruladı)"
            AssistantResult.STOPPED -> "asistan kapatıldı"
            AssistantResult.BUSY -> "asistan: arama sürüyor, yok sayıldı"
            AssistantResult.FAILED -> "asistan başlatılamadı"
            AssistantResult.UNCONFIRMED -> "asistan isteği gitti, telefon doğrulamadı (uyarı gösterilmez)"
        })
        if (r == AssistantResult.FAILED) fail(PhoneError.ASSISTANT_UNAVAILABLE)
        if (!started) {
            main.removeCallbacks(idleClose)
            main.postDelayed(idleClose, ASSISTANT_IDLE_CLOSE_MS)
        }
    }

    /**
     * Rehber/geçmiş/arama izinlerini ister (READ_CONTACTS, READ_CALL_LOG, CALL_PHONE; Telecom hesabını okumak için
     * aynı gruptaki READ_PHONE_STATE; Telecom ile cevap/kapat için ANSWER_PHONE_CALLS). Araçta sistem penceresi yoksa `adb shell pm grant` ile verilir.
     */
    fun requestPermissions(activity: Activity) {
        try {
            activity.requestPermissions(
                arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.ANSWER_PHONE_CALLS,
                ),
                REQ_PERMISSIONS,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "izin isteği: ${t.javaClass.simpleName}")
            fail(PhoneError.PERMISSION)
        }
    }

    /**
     * İzin sonucu geldi: veri yeniden okunur. Rehber izni hâlâ yoksa ve sistem artık sormuyorsa ("Bir daha sorma" ya da
     * araçta pencere yok) [permissionBlocked] olur; UI uygulama ayarlarını açmayı önerir.
     */
    fun onPermissionsResult(context: Context) {
        val app = context.applicationContext
        appContext = app
        val denied = app.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
        val blocked = denied && (context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) != true
        io.execute { update { it.copy(permissionBlocked = blocked) } }
        if (!started) return
        if (wantData) {
            unregisterObservers(app)
            registerObservers(app)
        }
        io.execute {
            if (!started) return@execute
            reprobe()
            readData()
        }
    }

    /** Sabitlenen kişiler değişti: bellekteki veri yeniden sıralanır (okuma yok; veri yoksa bir kez okunur). */
    fun reloadFavorites() {
        io.execute {
            if (!started || !wantData) return@execute
            if (!activeStore().hasData) return@execute readData()
            publishStore()
        }
    }

    /** Direksiyon ses tuşu (VR) uzun basış dinleyicisini ayara göre açar/kapatır ([PhonePrefs.assistantOnVoiceKey]). */
    fun syncVoiceKey(context: Context) {
        appContext = context.applicationContext
        PhoneKeys.sync(context)
    }

    /** Ana ekrana dönüşte ses tuşu aboneliğini tazeler (uyku sonrası düşmüş olabilir). */
    fun refreshVoiceKey() {
        PhoneKeys.refresh()
    }

    /** Etkinlik kapanırken: yalnızca dinleyiciyi son başlatan örnek kapatabilir. */
    fun stopVoiceKey(owner: Any) {
        PhoneKeys.stop(owner)
    }

    /** Demo telefon ekstraları değişti (LauncherDemo.applyPreview, ana iş parçacığı): durum yeniden üretilir. */
    internal fun demoChanged() {
        io.execute {
            if (!started) return@execute
            reprobe()
            applyDemoCall()
            readData()
        }
    }

    // ---- io kuyruğu ----

    /** Yetenekleri yoklar, arka uçları seçer, anlık görüntünün yetenek alanlarını günceller. */
    private fun reprobe() {
        val app = appContext ?: return
        val vdOk = vdbus?.available() == true
        val c = PhoneBackendChooser.Caps(
            demo = demoActive(),
            vdbusBt = vdOk,
            telecomHfpAccount = telecom?.available() == true,
            hfpClientProxy = hfp?.available() == true,
            contactsPermission = contactsData?.available() == true,
            vdbusData = vdOk,
            telecomCallControl = telecom?.canControl() == true,
        )
        val call = PhoneBackendChooser.callBackend(c)
        val data = PhoneBackendChooser.dataBackend(c)
        val asst = PhoneBackendChooser.assistantBackend(c)
        if (c != caps) Log.i(TAG, "arka uç: arama=$call veri=$data asistan=$asst")
        caps = c

        val wantState = PhoneBackendChooser.stateBackend(c)
        if (stateSource?.name != wantState) {
            val leavingDemo = stateSource?.name == PhoneBackendName.DEMO
            stateSource?.close()
            stateSource = callBackendFor(wantState)
            if (leavingDemo || stateSource == null) update { it.copy(call = CallState.IDLE) }
            // Bağlantı PhoneSource'un kendi yayın alıcısından izlenir.
            stateSource?.setAudioListener(::onAudioMode)
            stateSource?.open(::onRawState) { _, _ -> }
            if (leavingDemo || c.demo) publishStore()
        }
        // HFP tanığı: durum VDBus'tan gelirken HFP "arama bitti" yayını hemen yeniden okumayı tetikler.
        val wantWitness = stateSource?.name == PhoneBackendName.VDBUS && c.hfpClientProxy
        if (wantWitness != witnessOpen) {
            if (wantWitness) {
                hfp?.openWitness {
                    io.execute {
                        if ((started || lingering) && snap.call.inCall) {
                            Log.i(TAG, "arama: HFP bitti dedi, durum yeniden okunuyor")
                            stateSource?.refreshState()
                        }
                    }
                }
            } else {
                hfp?.closeWitness()
            }
            witnessOpen = wantWitness
        }
        // Edilgen rehber/geçmiş yakalama (yalnızca itme; süreç boyunca bir kez kurulur).
        if (vdOk && !c.demo) vdbusData?.watch(io, ::onPassiveContacts, ::onPassiveRecords, ::onSyncStatus)

        val connected = c.demo || HfpLink.isConnected()
        if (!c.demo) {
            if (connected && !lastHfpConnected) {
                // Yeni HFP bağlantısı: indirme hakkı ve önbellek bilgisi sıfırlanır.
                downloadRequested = false
                vdbusData?.resetCacheTotal()
            }
            if (connected != lastHfpConnected) com.mapcontrol.GlitchTip.breadcrumb("bt", "hfp connected=$connected")
            lastHfpConnected = connected
            // Farklı telefon: bellekteki rehber/geçmiş, fotoğraflar ve disk önbelleği silinir (adres yalnızca özet
            // olarak tutulur, günlüğe yazılmaz).
            val address = try {
                HfpLink.connectedDevice()?.address
            } catch (t: Throwable) {
                null
            }
            val key = address?.uppercase()?.hashCode()
            if (store.onDevice(key)) {
                PhonePhotos.clear()
                downloadRequested = false
                liveData = false
                savedVersion = -1
                main.removeCallbacks(saveTask)
                vdbusData?.resetCacheTotal()
                PhoneCache.clear(app)
                Log.i(TAG, "rehber: farklı telefon bağlandı, bellek ve disk önbelleği sıfırlandı")
                publishStore()
            }
            if (address != null) {
                val h = PhoneCache.deviceHash(address)
                if (h != deviceHash) {
                    deviceHash = h
                    restoreCache(app, h)
                }
            }
        }
        val deviceName = when {
            c.demo -> PhoneDemo.DEVICE_NAME
            connected -> HfpLink.deviceName()
            else -> null
        }
        val canMute = c.demo ||
            ((call == PhoneBackendName.VDBUS || call == PhoneBackendName.HFP_CLIENT) && MicMute.available(app))
        update {
            it.copy(
                connected = connected,
                deviceName = deviceName,
                backend = call,
                dataBackend = data,
                permissionNeeded = PhoneBackendChooser.permissionNeeded(c),
                canDial = call != PhoneBackendName.NONE,
                canAssistant = asst != PhoneBackendName.NONE,
                canMute = canMute,
                canAudioTransfer = c.demo || call == PhoneBackendName.VDBUS,
                canDtmf = c.demo || call == PhoneBackendName.VDBUS,
            )
        }
        if (wantData && data != lastDataBackend) readData()
    }

    /** Veri arka ucundan bir kez okur; favorileri sıralar, son aramaları biçimler. */
    private fun readData() {
        // Yalnız arama şeridi görünürken rehber/geçmiş okunmaz.
        if (!wantData) return
        val app = appContext ?: return
        val name = PhoneBackendChooser.dataBackend(caps)
        lastDataBackend = name
        lastReadAt = SystemClock.elapsedRealtime()
        val backend = dataBackendFor(name)
        if (backend == null) {
            // Yol yok (izin/BT servisi yok): bellekteki depo korunur (yalnızca farklı telefon bağlanınca silinir).
            if (activeStore().hasData) publishStore() else update { it.copy(loaded = false, favorites = emptyList(), recents = emptyList()) }
            return
        }
        val pinned = PhonePrefs.pinnedIds(app)
        fun readFrom(be: DataBackend, label: String): PhoneData? = try {
            be.read(pinned)
        } catch (t: Throwable) {
            Log.w(TAG, "veri ($label): ${t.javaClass.simpleName}")
            null
        }
        var data = readFrom(backend, name)
        // Sağlayıcı boşsa (araç BT servisi açılışta pbapsink/CallLog'u siler; OEM indirmesi gelene kadar boş) OEM önbelleği.
        val second = PhoneBackendChooser.dataFallback(caps, name)
        if (second != null && (data == null || (data.contacts.isEmpty() && data.recents.isEmpty()))) {
            val alt = dataBackendFor(second)?.let { readFrom(it, second) }
            if (alt != null && (data == null || alt.contacts.isNotEmpty() || alt.recents.isNotEmpty())) {
                Log.i(TAG, "veri: $name boş, $second kullanıldı")
                data = alt
            }
        }
        val target = activeStore()
        if (data != null && target === store) noteLive(data.contacts, data.recents)
        val changed = data != null && target.merge(data.contacts, data.recents)
        Log.d(TAG, "veri: $name kişi=${data?.contacts?.size ?: -1} geçmiş=${data?.recents?.size ?: -1} " +
            "depo=${target.contacts.size}/${target.recents.size}${if (changed) " (yeni)" else ""}")
        // Boş ya da yanıtsız okuma dolu depoyu silmez; her durumda "okundu" sayılır.
        publishStore()
    }

    /**
     * io: depodan (bellek) anlık görüntüyü üretir: favoriler, panelin son aramaları, sayılar ve "Tümü" dizini.
     * Yalnızca sayılar günlüğe yazılır.
     */
    private fun publishStore() {
        val s = activeStore()
        byNumber = s.byNumber()
        // Demo ayrı sürüm aralığında: demo ↔ gerçek geçişinde "Tümü" sayfası mutlaka yeniden kurulur.
        val version = s.version + if (caps.demo) DEMO_VERSION_OFFSET else 0
        if (directorySnap.version != version || directorySnap.contacts !== s.contacts || directorySnap.recents !== s.recents) {
            directorySnap = PhoneDirectory(s.contacts, s.recents, version)
        }
        val pinned = appContext?.let { PhonePrefs.pinnedIds(it) } ?: emptyList()
        val favorites = FavoritesRanker.rank(s.contacts, s.recents, pinned, MAX_FAVORITES, System.currentTimeMillis())
        val recents = RecentsShaper.shape(s.recents, 5)
        val dir = directorySnap
        update {
            it.copy(
                loaded = true, favorites = favorites, recents = recents,
                contactCount = dir.contacts.size, recentCount = dir.recents.size, directoryVersion = dir.version,
            )
        }
        if (!caps.demo) scheduleSave()
    }

    // ---- Disk önbelleği (io; [PhoneCache]) ----

    private fun noteLive(contacts: List<PhoneContact>?, recents: List<PhoneRecent>?) {
        if (!contacts.isNullOrEmpty() || !recents.isNullOrEmpty()) liveData = true
    }

    /**
     * io: bağlı telefonun ([hash]) önbelleğini depo boşsa yükler ve hemen gösterir. Başka telefonun ya da bozuk dosya
     * [PhoneCache.load] içinde silinir. Demo açıkken gösterilmez (gerçek depoya yine yüklenir).
     */
    private fun restoreCache(app: Context, hash: String) {
        if (store.hasData) {
            // Cihaz anahtarı öğrenilmeden okunan canlı veri: artık yazılabilir.
            scheduleSave()
            return
        }
        val d = PhoneCache.load(app, hash) ?: return
        if (!store.merge(d.contacts, d.recents)) return
        savedVersion = store.version
        Log.i(TAG, "rehber: disk önbelleğinden kişi=${store.contacts.size} geçmiş=${store.recents.size}")
        if (!caps.demo) publishStore()
    }

    /** io: gerçek depo değiştiyse kısa gecikmeyle diske yazılır (indirme parçaları tek yazıma toplanır). */
    private fun scheduleSave() {
        if (deviceHash == null || !store.hasData || store.version == savedVersion) return
        main.removeCallbacks(saveTask)
        main.postDelayed(saveTask, SAVE_DELAY_MS)
    }

    private fun saveCache() {
        val app = appContext ?: return
        val h = deviceHash ?: return
        if (!store.hasData || store.version == savedVersion) return
        val v = store.version
        if (PhoneCache.save(app, h, store.contacts, store.rawRecentsForSave)) savedVersion = v
    }

    // ---- Edilgen rehber/geçmiş (io; VdbusDataBackend.watch) ----

    private fun onPassiveContacts(raw: List<com.desaysv.ivi.vdb.event.id.bt.bean.VDBTContacts>) {
        val app = appContext ?: return
        val vd = vdbusData ?: return
        val wanted = store.recents.map { FavoritesRanker.normalizeNumber(it.number) }.toHashSet()
        val contacts = vd.convertContacts(raw, wanted, PhonePrefs.pinnedIds(app).toHashSet())
        Log.i(TAG, "rehber: indirmeden ${contacts.size} kişi")
        noteLive(contacts, null)
        if (store.merge(contacts, null) && !caps.demo) publishStore()
    }

    private fun onPassiveRecords(raw: List<com.desaysv.ivi.vdb.event.id.bt.bean.VDBTRecord>) {
        val vd = vdbusData ?: return
        val recents = vd.convertRecords(raw)
        Log.i(TAG, "geçmiş: indirmeden ${recents.size} kayıt")
        noteLive(null, recents)
        if (store.merge(null, recents) && !caps.demo) publishStore()
    }

    /** PHONE_BOOK_SYNC durumu (VDValueBT.SyncStatus). FINISHED → önbellek artık dolu: bir kez okunur. */
    private fun onSyncStatus(status: Int, type: Int) {
        Log.i(TAG, "rehber eşitleme: durum=$status tür=$type")
        if (status != VDSYNC_FINISHED) return
        main.removeCallbacks(syncReadTask)
        main.postDelayed(syncReadTask, SYNC_READ_DELAY_MS)
    }

    /** Arka uçtan ham durum (herhangi bir iş parçacığı) → io. */
    private fun onRawState(raw: RawCall) {
        io.execute { applyState(raw) }
    }

    /** io: OEM ses yönü yayını (DIAL_AUDIO_MODE). Yalnızca arama sürerken uygulanır. */
    private fun onAudioMode(onPhone: Boolean) {
        if (!snap.call.inCall || snap.call.audioOnPhone == onPhone) return
        Log.i(TAG, "ses yönü (OEM): ${if (onPhone) "telefon" else "araç"}")
        update { it.copy(call = it.call.copy(audioOnPhone = onPhone)) }
    }

    private fun applyState(raw: RawCall) {
        if (!started && !lingering) return
        val prev = snap.call
        val kind = PhoneStateMap.effectiveKind(prev.kind, raw.kind)
        val matched = raw.number?.let { byNumber[FavoritesRanker.normalizeNumber(it)] }
        val shownName = raw.name ?: matched?.name
        val nowElapsed = SystemClock.elapsedRealtime()
        val oemStart = if (kind == CallKind.ACTIVE && raw.startElapsedMs > 0L) {
            CallClock.startElapsed(raw.startElapsedMs, nowElapsed)
        } else {
            null
        }
        val photoKey = resolvePhoto(raw, prev, kind, matched)
        val next = PhoneStateMap.nextCall(prev, kind, shownName, raw.number, nowElapsed, oemStart, photoKey)
        if (!next.inCall) unmuteIfNeeded()
        if (prev.inCall && !next.inCall) stopPoll()
        if (!started) {
            // Sahipsiz izleme: sessizdeki görüşme bitti → mikrofon açıldı, kaynak kapanır.
            if (!next.inCall) teardown() else update { it.copy(call = next) }
            return
        }
        if (next.kind != prev.kind) {
            Log.i(TAG, "arama: ${prev.kind} → ${next.kind}")
            com.mapcontrol.GlitchTip.breadcrumb("call", "${prev.kind} -> ${next.kind} src=${stateSource?.name}")
        }
        update { it.copy(call = next) }
        if (next.inCall && !prev.inCall) startPoll()
        if (next.kind == CallKind.ACTIVE && prev.kind != CallKind.ACTIVE) seedAudioAndMic()
        if (kind == CallKind.ENDED && prev.kind != CallKind.ENDED) {
            main.removeCallbacks(endedTask)
            main.postDelayed(endedTask, ENDED_MS)
        }
    }

    /**
     * io: arayan fotoğrafı anahtarı (yalnızca bellek). Sıra: OEM arama kaydındaki portre ("call:<uuid>") → eşleşen
     * kişinin fotoğrafı → aramanın ilk görülüşünde bir kez PHONE_BOOK_CONTACT_INFO. Anahtar yalnızca görüntü önbelleğe
     * girdikten sonra döner; yoksa null (nextCall aynı görüşmede öncekini korur).
     */
    private fun resolvePhoto(raw: RawCall, prev: CallState, kind: CallKind, matched: PhoneContact?): String? {
        if (kind != CallKind.INCOMING && kind != CallKind.DIALING && kind != CallKind.ACTIVE) return null
        val uuid = raw.uuid
        val portrait = raw.portrait
        if (uuid != null && portrait != null && portrait.isNotEmpty()) {
            val key = "call:$uuid"
            if (PhonePhotos.contains(key) || PhonePhotos.putEncoded(key, portrait)) return key
        }
        matched?.photoKey?.takeIf { PhonePhotos.contains(it) }?.let { return it }
        // Arama sürüyor: OEM sorgusu yalnızca ilk görülüşte (aynı görüşmede önceki anahtar nextCall'da korunur).
        if (prev.inCall || caps.demo) return null
        val number = raw.number ?: return null
        val key = matched?.id ?: uuid?.let { "call:$it" } ?: return null
        val bytes = try {
            stateSource?.contactPortrait(number)
        } catch (t: Throwable) {
            null
        } ?: return null
        return if (PhonePhotos.putEncoded(key, bytes)) key else null
    }

    /** io: konuşmaya girişte OEM'den ses yönü ve mikrofon durumu bir kez okunur (OEM ekranında değiştirilmiş olabilir). */
    private fun seedAudioAndMic() {
        val src = stateSource ?: return
        if (src.name == PhoneBackendName.DEMO) return
        val onPhone = try { src.readAudioOnPhone() } catch (t: Throwable) { null }
        val muted = try { src.readMicMuted() } catch (t: Throwable) { null }
        if (onPhone == null && muted == null) return
        Log.i(TAG, "konuşma başı: ses=${onPhone?.let { if (it) "telefon" else "araç" } ?: "?"} sessiz=${muted ?: "?"}")
        update {
            if (it.call.kind != CallKind.ACTIVE) it else it.copy(
                call = it.call.copy(audioOnPhone = onPhone ?: it.call.audioOnPhone, muted = muted ?: it.call.muted),
            )
        }
    }

    /** Demo isteğindeki arama durumunu uygular (gelen / 83 sn'dir süren / yok). */
    private fun applyDemoCall() {
        if (!caps.demo) return
        val who = PhoneDemo.caller
        when (PhoneDemo.call) {
            CallKind.INCOMING -> update { it.copy(call = CallState(CallKind.INCOMING, who.name, who.number)) }
            CallKind.ACTIVE -> update {
                it.copy(call = CallState(CallKind.ACTIVE, who.name, who.number, SystemClock.elapsedRealtime() - 83_000L))
            }
            else -> if (snap.call.inCall) update { it.copy(call = CallState.IDLE) }
        }
    }

    private fun unmuteIfNeeded() {
        if (!mutedByMic) return
        mutedByMic = false
        // Sessize alan yol neyse onunla açılır (VDBus sistem uid'siyle kapatır); ayrıca AudioManager da açılır.
        runCatching { callBackendFor(PhoneBackendChooser.callBackend(caps))?.takeIf { it.name != PhoneBackendName.DEMO }?.setMute(false) }
        appContext?.let { MicMute.set(it, false) }
    }

    /** Yeni anlık görüntü eskisinden farklıysa yayınlar (ana iş parçacığında dinleyicilere). */
    private fun update(f: (PhoneSnapshot) -> PhoneSnapshot) {
        val old = snap
        val new = f(old)
        if (new == old) return
        snap = new
        main.post { for (l in ArrayList(listeners)) l.onPhoneChanged(new) }
    }

    private fun fail(e: PhoneError) {
        main.post { for (l in ArrayList(listeners)) l.onPhoneError(e) }
    }

    // ---- ana iş parçacığı: yayın alıcısı ve içerik gözlemcileri (yalnızca başlatılmışken) ----

    private fun registerReceiver(app: Context) {
        if (receiverRegistered) return
        receiverRegistered = try {
            app.registerReceiver(receiver, IntentFilter().apply {
                addAction(HfpLink.ACTION_CONNECTION)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            })
            true
        } catch (t: Throwable) {
            Log.w(TAG, "alıcı: ${t.javaClass.simpleName}")
            false
        }
    }

    private fun unregisterReceiver(app: Context) {
        if (!receiverRegistered) return
        receiverRegistered = false
        runCatching { app.unregisterReceiver(receiver) }
    }

    private fun registerObservers(app: Context) {
        if (observerRegistered) return
        val cr = app.contentResolver
        var any = false
        if (app.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            any = runCatching { cr.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer) }.isSuccess || any
        }
        if (app.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            any = runCatching { cr.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer) }.isSuccess || any
        }
        observerRegistered = any
    }

    private fun unregisterObservers(app: Context) {
        main.removeCallbacks(rereadTask)
        if (!observerRegistered) return
        observerRegistered = false
        runCatching { app.contentResolver.unregisterContentObserver(observer) }
    }
}

/** [PhoneSource.refreshData] nedeni (yalnızca günlük ve indirme kararı için). */
enum class RefreshReason { APP_START, HFP_CONNECTED, PANEL_OPENED, USER_OPENED, CALL_ENDED }

/**
 * Bellekteki tam rehber ve geçmiş (süreç ömrü; diske yazılmaz). [version] her yeni listede artar. toString isim/numara
 * içermez.
 */
class PhoneDirectory(
    val contacts: List<PhoneContact>,
    val recents: List<PhoneRecent>,
    val version: Int,
) {
    override fun toString(): String = "PhoneDirectory(contacts=${contacts.size}, recents=${recents.size}, v=$version)"

    companion object {
        val EMPTY = PhoneDirectory(emptyList(), emptyList(), 0)
    }
}

/**
 * Kişi fotoğrafları: YALNIZCA bellekte, anahtar → küçültülmüş Bitmap (en çok [MAX_PX] kenar, toplam [MAX_BYTES]).
 * Diske, günlüğe, paylaşıma asla yazılmaz; farklı telefon bağlanınca ve süreç bitince gider.
 *
 * Anahtarlar: kişi için [PhoneContact.id] ("v:<OEM id>" / "c:<rehber id>"), arama için "call:<OEM uuid>".
 * Kaynaklar: VDBTContacts.portrait (rehber parçaları), VDBTDialInfo.portrait
 * (DIAL_INFO_LIST), PHONE_BOOK_CONTACT_INFO (262383) getOnce {number} → portrait.
 * Kural: [PhoneContact.photoKey]/[CallState.photoKey] yalnızca görüntü önbelleğe girdikten SONRA atanır; UI
 * [get] null dönerse baş harfe düşer (önbellekten atılmış olabilir).
 */
object PhonePhotos {
    const val MAX_PX = 256
    const val MAX_BYTES = 8 * 1024 * 1024

    private val map = LinkedHashMap<String, android.graphics.Bitmap>(32, 0.75f, true)
    private var bytes = 0

    @Synchronized
    fun get(key: String?): android.graphics.Bitmap? = if (key == null) null else map[key]

    @Synchronized
    fun contains(key: String?): Boolean = key != null && map.containsKey(key)

    /**
     * Kodlanmış görüntüyü (JPEG/PNG) çözüp küçültür ve saklar. Binder/io iş parçacığında çağrılır (ana iş parçacığında
     * asla). Çözülemezse false. SÖZLEŞME (PHONE-BACKEND): inJustDecodeBounds + inSampleSize ile [MAX_PX]'e indir.
     */
    fun putEncoded(key: String, data: ByteArray?): Boolean {
        if (data == null || data.isEmpty()) return false
        val bmp = try {
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size, o)
            var sample = 1
            while (o.outWidth / (sample * 2) >= MAX_PX && o.outHeight / (sample * 2) >= MAX_PX) sample *= 2
            android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (t: Throwable) {
            null
        } ?: return false
        put(key, bmp)
        return true
    }

    @Synchronized
    private fun put(key: String, bmp: android.graphics.Bitmap) {
        map.remove(key)?.let { bytes -= it.byteCount }
        map[key] = bmp
        bytes += bmp.byteCount
        val it = map.entries.iterator()
        while (bytes > MAX_BYTES && it.hasNext()) {
            val e = it.next()
            if (e.key == key) continue
            bytes -= e.value.byteCount
            it.remove()
        }
    }

    /** Farklı telefon bağlandı / süreç temizliği. */
    @Synchronized
    fun clear() {
        map.clear()
        bytes = 0
    }
}

/** Telefon tercihleri (profil başına, launcher_settings içinde). Yalnız açık/kapalı ve kişi KİMLİKLERİ yazılır. */
object PhonePrefs {
    private const val PREFS_NAME = "launcher_settings"
    private const val KEY_ASSISTANT = "phone_voice_key_assistant"
    private const val KEY_PINNED = "phone_pinned_ids"
    private const val KEY_SHOW_ASSISTANT = "phone_show_assistant"

    /**
     * "Ses tuşu uzun basış: Telefon asistanı" (varsayılan KAPALI: 6 Kasım araç testinde OEM uzun basışta kendi
     * CarPlay/Android Auto kablosuz bağlantı penceresini açıyor; Siri için panel tuşu ve alt bar uzun basış yeterli).
     */
    fun assistantOnVoiceKey(context: Context): Boolean =
        prefs(context).getBoolean(Profiles.key(context, KEY_ASSISTANT), false)

    fun setAssistantOnVoiceKey(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(Profiles.key(context, KEY_ASSISTANT), on).apply()
    }

    /** "Sesli asistan düğmesi" varsayılanı: KAPALI (büyük son arama + favori ızgarası); ayardan açılabilir. */
    const val SHOW_ASSISTANT_DEFAULT = false

    /**
     * Kayıtlı değer ([stored], hiç yazılmadıysa null) → geçerli ayar. Kullanıcının açıkça yazdığı değer (açık ya da
     * kapalı) aynen korunur; yalnız hiç dokunulmamışsa [SHOW_ASSISTANT_DEFAULT] geçerlidir. Saf, JVM testli.
     */
    fun resolveShowAssistant(stored: Boolean?): Boolean = stored ?: SHOW_ASSISTANT_DEFAULT

    /**
     * "Sesli asistan düğmesi" (varsayılan KAPALI). Kapalıyken telefon paneli/sayfası asistan şeridi olmadan büyük
     * "Son arama" kartı ve 2×3 favori ızgarasıyla kurulur. Değişim açık panellere [addShowAssistantListener] ile gider.
     */
    fun showAssistant(context: Context): Boolean {
        val p = prefs(context)
        val key = Profiles.key(context, KEY_SHOW_ASSISTANT)
        return resolveShowAssistant(if (p.contains(key)) p.getBoolean(key, SHOW_ASSISTANT_DEFAULT) else null)
    }

    fun setShowAssistant(context: Context, on: Boolean) {
        if (showAssistant(context) == on) return
        prefs(context).edit().putBoolean(Profiles.key(context, KEY_SHOW_ASSISTANT), on).apply()
        showAssistantListeners.forEach { it() }
    }

    /** Ana iş parçacığında çağrılır (ayar satırı ve adb ekstrası ana iş parçacığında yazar). */
    fun addShowAssistantListener(l: () -> Unit) { showAssistantListeners.add(l) }

    fun removeShowAssistantListener(l: () -> Unit) { showAssistantListeners.remove(l) }

    /**
     * Araç testinde adb ile çevirmek için (yalnız debug derlemede, demo gerekmez):
     * `am start -n <paket>/com.mapcontrol.launcher.HomeLauncherActivity --ez launcher_phone_assistant false`. Kalıcı yazar.
     */
    const val EXTRA_SHOW_ASSISTANT = "launcher_phone_assistant"

    fun applyShowAssistantExtra(context: Context, intent: android.content.Intent?) {
        if (intent?.hasExtra(EXTRA_SHOW_ASSISTANT) != true || !LauncherCrashGuard.isDebuggable(context)) return
        setShowAssistant(context, intent.getBooleanExtra(EXTRA_SHOW_ASSISTANT, SHOW_ASSISTANT_DEFAULT))
    }

    private val showAssistantListeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()

    /** Sabitlenmiş favori kişi kimlikleri, sırayla. */
    fun pinnedIds(context: Context): List<String> =
        prefs(context).getString(Profiles.key(context, KEY_PINNED), null)
            ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct() ?: emptyList()

    fun setPinnedIds(context: Context, ids: List<String>) {
        val clean = ids.map { it.replace(",", "").trim() }.filter { it.isNotEmpty() }.distinct()
        prefs(context).edit().putString(Profiles.key(context, KEY_PINNED), clean.joinToString(",")).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

/** Saf metin yardımcıları (JVM testli). */
object PhoneText {
    private val TR = Locale("tr", "TR")

    /**
     * Avatar baş harfleri: ilk iki kelimenin ilk harfi, Türkçe büyük harf ("ayşe yılmaz" -> "AY"). Harf yoksa
     * (numara ya da boş) "#".
     */
    fun initials(name: String?): String {
        val words = name.orEmpty().trim().split(Regex("\\s+")).filter { w -> w.firstOrNull()?.isLetter() == true }
        if (words.isEmpty()) return "#"
        return words.take(2).joinToString("") { it.substring(0, it.offsetByCodePoints(0, 1)).uppercase(TR) }
    }
}
