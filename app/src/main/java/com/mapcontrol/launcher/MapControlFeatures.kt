package com.mapcontrol.launcher

import android.app.Activity
import android.content.Intent

/**
 * MapControl özelliklerine bağlanan dört widget'ın (web paneli, ekolayzer, otomasyon, göstergeye yansıt)
 * arka ucu. Widget'lar yalnızca bu arayüzü bilir; gerçek sürüm ([LauncherEdition.mapcontrol]) MapControl'ın kendi
 * yöneticilerini, tercihlerini ve servislerini çağırır, özelliklerin iç işleyişine dokunmaz. MapControl'sız bir derlemede
 * [NONE] her şeyi "yok" döner ve hiçbir şey yapmaz.
 *
 * Bu widget'lar aracın hareketini değil, uygulama/cihaz durumunu değiştirir; MapControl'ın kendisi kısıtlamadığı için sürüşte
 * de çalışır (araca yazma politikası yalnızca araç sinyali yazmaları içindir; park kapısı orada bagaj, kilit ve sürüş moduna uygulanır).
 *
 * Okuma çağrıları (adı `read` ile başlayanlar) G/Ç yapabilir: arka plan iş parçacığında çağrılır.
 */
internal interface MapControlFeatures {

    /** MapControl bu derlemede var mı (yoksa widget'lar "Yalnızca MapControl'da" der). */
    val available: Boolean

    /** Web yönetim paneli durumu: sunucu açık mı ve MapControl'ın gösterdiği adres. */
    fun readWebPanel(): WebPanelState

    /**
     * Web sunucusunu MapControl'ın Aktar sekmesiyle aynı örnek üzerinden açar/kapatır (sunucu MapControl ekranına bağlı değildir).
     * [done] ana iş parçacığında: null = istek yerine getirildi, yoksa kullanıcıya gösterilecek kısa neden (başlatılamadı).
     */
    fun setWebPanel(on: Boolean, done: (String?) -> Unit)

    /** Web paneli durumu değişince [onChange] ana iş parçacığında çağrılır; dönen işlev aboneliği bitirir. */
    fun watchWebPanel(onChange: () -> Unit): () -> Unit

    /** Kamera kaydı sürüyor mu (Kamera 360). */
    fun isRecording(): Boolean

    /** Kayıt başlayıp bitince [onChange]; dönen işlev dinlemeyi bırakır. */
    fun watchRecording(onChange: () -> Unit): () -> Unit

    /** Telefon eşleşme adresi (QR içeriği, anahtarı taşır); sunucu kapalıysa ya da araç bir ağda değilse null. */
    fun readPairingUri(): String?

    /** [text] için QR modül matrisi (kenar boşluksuz); üretilemezse null. */
    fun qr(text: String): QrMatrix?

    /** MapControl ekolayzerinin kayıtlı durumu ve hazır ayar listesi. */
    fun readEqualizer(): EqState

    /** Ekolayzeri MapControl'ın kendi yolundan ([EqState.presets] içinden bir hazır ayarla) günceller. */
    fun setEqualizer(enabled: Boolean, preset: String)

    /** MapControl'ın aç/kapa otomasyonları, sabit sırayla. */
    fun readAutomations(): List<AutomationItem>

    /** Otomasyonu MapControl ayar sekmesinin anahtarıyla aynı yoldan açar/kapatır (yalnızca [AutomationItem.toggleable]). */
    fun setAutomation(id: AutomationId, on: Boolean)

    /** Bölünmüş ekran için MapControl'da seçili uygulama çifti (paket adları). */
    fun readSplitPair(): SplitPair

    /** Bölme yönü: 0 Otomatik, 1 Sol-sağ, 2 Üst-alt (MapControl bölünmüş ekran sekmesiyle aynı kayıt). */
    fun readSplitAxis(): Int

    fun setSplitAxis(axis: Int)

    /** Bölünmüş ekrana seçilebilen uygulamalar (MapControl sekmesindeki listeyle aynı; Mini gösterge launcher modunda başta). */
    fun splitCandidates(): List<SplitCandidate>

    /** [slot] 0 ya da 1; [entry] null ise yuva boşaltılır. Geçersiz seçim yok sayılır. */
    fun setSplitApp(slot: Int, entry: String?)

    /** Bölünmüş ekranı seçili çiftle başlatır; [done] ana iş parçacığında: null = başladı, yoksa kullanıcıya gösterilecek neden. */
    fun startSplit(done: (String?) -> Unit)

    /**
     * Mini gösterge solda, son seçilen diğer uygulama (yoksa Yandex Haritalar) sağda yarı yarıya bölünmüş ekran açar;
     * [done] ana iş parçacığında: null = başladı, yoksa kullanıcıya gösterilecek neden.
     */
    fun startSplitWithMiniDash(done: (String?) -> Unit)

    /**
     * Sürüş ekranı ([DriveScreen]): Mini gösterge bir yarıda, [mapPkg] (null: "+ Harita ekle" seçicisi) diğer yarıda; [route] verilirse
     * harita onunla açılır (Ev / İş). [done] ana iş parçacığında: null = başladı, yoksa kullanıcıya gösterilecek neden.
     */
    fun startDriveScreen(mapPkg: String?, route: Intent?, done: (String?) -> Unit)

    /** Sürüş ekranındaki haritayı tam ekran yapar; [done] ana iş parçacığında (true = yapıldı). */
    fun mapFullScreen(pkg: String, done: (Boolean) -> Unit)

    /** Sürüş ekranı bitti: kendi yarılarımızın (Mini gösterge, seçici) görevleri kapatılır. */
    fun endDriveScreen()

    /** [pkg]'yi tam ekran açar; sürüş ekranından yarıda serbest pencere kalmış görevi tam ekrana taşınır (arka planda). */
    fun openFullScreen(pkg: String)

    /** Süreç ölümüyle yarıda kalmış sürüş ekranı haritasını tüm alana büyütür; engelleyen çağrı (arka plan iş parçacığında kullanılır). */
    fun restoreStaleMap(pkg: String) = Unit

    /** Sürüş ekranında haritanın yarısı (ekran koordinatı); bölme yoksa null. */
    fun driveMapSlot(): android.graphics.Rect?

    /** Göstergeye (cluster) yansıtma durumu ve hedef uygulama. */
    fun readCluster(): ClusterState

    /** Yansıtmayı MapControl servisinin aç/kapa giriş noktasıyla başlatır/durdurur. */
    fun setCluster(open: Boolean)

    /** MapControl'ı ilgili ayar sekmesinde açar. */
    fun openTab(activity: Activity, tab: MapControlTab)

    companion object {
        /** MapControl'sız derleme: hiçbir şey yok, hiçbir şey yapılmaz. */
        val NONE: MapControlFeatures = object : MapControlFeatures {
            override val available = false
            override fun readWebPanel() = WebPanelState(running = false, url = null, reachable = false)
            override fun setWebPanel(on: Boolean, done: (String?) -> Unit) = Unit
            override fun watchWebPanel(onChange: () -> Unit): () -> Unit = {}
            override fun isRecording() = false
            override fun watchRecording(onChange: () -> Unit): () -> Unit = {}
            override fun readPairingUri(): String? = null
            override fun qr(text: String): QrMatrix? = null
            override fun readEqualizer() = EqState(supported = false, enabled = false, preset = "", presets = emptyList())
            override fun setEqualizer(enabled: Boolean, preset: String) = Unit
            override fun readAutomations(): List<AutomationItem> = emptyList()
            override fun setAutomation(id: AutomationId, on: Boolean) = Unit
            override fun readSplitPair() = SplitPair(null, null)
            override fun startSplit(done: (String?) -> Unit) = Unit
            override fun readSplitAxis() = 0
            override fun setSplitAxis(axis: Int) = Unit
            override fun splitCandidates(): List<SplitCandidate> = emptyList()
            override fun setSplitApp(slot: Int, entry: String?) = Unit
            override fun startSplitWithMiniDash(done: (String?) -> Unit) = Unit
            override fun startDriveScreen(mapPkg: String?, route: Intent?, done: (String?) -> Unit) = Unit
            override fun mapFullScreen(pkg: String, done: (Boolean) -> Unit) = Unit
            override fun endDriveScreen() = Unit
            override fun openFullScreen(pkg: String) = Unit
            override fun driveMapSlot(): android.graphics.Rect? = null
            override fun readCluster() = ClusterState(open = false, target = null)
            override fun setCluster(open: Boolean) = Unit
            override fun openTab(activity: Activity, tab: MapControlTab) = Unit
        }
    }
}

/** Widget'ların açabildiği MapControl sekmeleri; gerçek sekme numaraları [LauncherEdition]'da eşlenir. */
enum class MapControlTab { CAMERA_360, WEB,EQUALIZER, CLIMATE_AUTOMATION, SUNROOF_AUTOMATION, FACE_RECOGNITION, SPLIT_SCREEN, PROJECTION }

/** [url]: sunucu açıkken MapControl'ın gösterdiği "http://ip:port"; [reachable] false ise adres yalnızca yerel (127.x). */
data class WebPanelState(val running: Boolean, val url: String?, val reachable: Boolean)

/** [preset]: kayıtlı hazır ayar kimliği ("flat", "bass", … ya da elle ayar için "custom"). */
data class EqState(val supported: Boolean, val enabled: Boolean, val preset: String, val presets: List<String>)

enum class AutomationId { CLIMATE, SUNROOF, FACE }

/** [toggleable] false: launcher'dan açılamaz (ör. yüz tanıma kamera izni ister), dokunuş MapControl sekmesini açar. */
data class AutomationItem(val id: AutomationId, val on: Boolean, val toggleable: Boolean)

data class SplitPair(val first: String?, val second: String?)

/** Bölünmüş ekran yuvasına seçilebilen giriş: paket adı ya da bileşen ([com.mapcontrol.util.SplitSlot]) ve görünen ad. */
data class SplitCandidate(val entry: String, val label: String)

/** [target]: göstergede açılacak uygulamanın paketi (MapControl "Yansıtma" sekmesinde seçilen); yoksa null. */
data class ClusterState(val open: Boolean, val target: String?)

/** Kare QR modül matrisi; [get] true = koyu modül. */
class QrMatrix(val size: Int, private val bits: BooleanArray) {
    init { require(bits.size == size * size) }
    operator fun get(x: Int, y: Int): Boolean = bits[y * size + x]
}

/** MapControl widget'larının saf kuralları (JVM'de sınanır). */
object MapControlWidgetMath {

    /** MapControl web sunucusunun sabit portu (WebServerManager.startServer). */
    const val WEB_PORT = 7462

    /** MapControl'ın gösterdiği biçimde panel adresi; adres yoksa null. */
    fun webUrl(host: String?, port: Int = WEB_PORT): String? {
        val h = host?.trim().orEmpty()
        if (h.isEmpty() || port <= 0) return null
        return "http://$h:$port"
    }

    /** Yalnızca cihazın kendisinden erişilen adres (telefon okuyamaz). */
    fun isLoopback(host: String?): Boolean = host == null || host.startsWith("127.") || host == "localhost"

    /** Açık otomasyon sayısı ve toplam. */
    fun automationCount(items: List<AutomationItem>): Pair<Int, Int> = items.count { it.on } to items.size

    /** Bölünmüş ekran başlatılabilir mi: iki farklı uygulama seçili. */
    fun splitReady(pair: SplitPair): Boolean {
        val a = pair.first?.trim().orEmpty()
        val b = pair.second?.trim().orEmpty()
        return a.isNotEmpty() && b.isNotEmpty() && a != b
    }

    /** Mini gösterge ile bölünecek varsayılan eş: Yandex Haritalar. */
    const val SPLIT_DEFAULT_PKG = "ru.yandex.yandexmaps"

    /**
     * Mini gösterge bölünmüş ekranının diğer uygulaması: son seçilen (paketi hâlâ kuruluysa; "pkg/cls" ise paket kısmı
     * denetlenir), yoksa kuruluysa varsayılan, o da yoksa null.
     */
    fun miniDashSplitOther(last: String?, isInstalled: (String) -> Boolean): String? {
        val l = last?.trim().orEmpty()
        if (l.isNotEmpty() && isInstalled(l.substringBefore('/'))) return l
        return SPLIT_DEFAULT_PKG.takeIf(isInstalled)
    }

    /**
     * Dokunuştan sonra gösterilecek yansıtma durumu: [pending] (istenen hedef) [PENDING_MS] dolana ya da araç onu
     * bildirene dek gösterilir; sonra okunan durum.
     */
    fun clusterShown(read: Boolean, pending: Boolean?, pendingAgeMs: Long): Boolean =
        if (pending != null && pending != read && pendingAgeMs < PENDING_MS) pending else read

    const val PENDING_MS = 4_000L
}
