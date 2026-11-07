package com.mapcontrol.launcher

import android.app.Activity
import android.content.Intent

/**
 * FrostNav özelliklerine bağlanan beş widget'ın (web paneli, ekolayzer, otomasyon, bölünmüş ekran, göstergeye yansıt)
 * arka ucu. Widget'lar yalnızca bu arayüzü bilir; gerçek sürüm ([LauncherEdition.frostNav]) FrostNav'ın kendi
 * yöneticilerini, tercihlerini ve servislerini çağırır, özelliklerin iç işleyişine dokunmaz. Salt-okur test derlemesinde
 * FrostNav yoktur: [NONE] her şeyi "yok" döner ve hiçbir şey yapmaz.
 *
 * Bu widget'lar aracın hareketini değil, uygulama/cihaz durumunu değiştirir; FrostNav'ın kendisi kısıtlamadığı için sürüşte
 * de çalışır ([WidgetWritePolicy] beyaz listesi yalnızca araç sinyali yazmaları içindir; sürüş kilidi yoktur).
 *
 * Okuma çağrıları (adı `read` ile başlayanlar) G/Ç yapabilir: arka plan iş parçacığında çağrılır.
 */
internal interface FrostNavFeatures {

    /** FrostNav bu derlemede var mı (yoksa widget'lar "Yalnızca FrostNav'da" der). */
    val available: Boolean

    /** Web yönetim paneli durumu: sunucu açık mı ve FrostNav'ın gösterdiği adres. */
    fun readWebPanel(): WebPanelState

    /**
     * Web sunucusunu FrostNav'ın Aktar sekmesiyle aynı örnek üzerinden açar/kapatır (sunucu FrostNav ekranına bağlı değildir).
     * [done] ana iş parçacığında: null = istek yerine getirildi, yoksa kullanıcıya gösterilecek kısa neden (başlatılamadı).
     */
    fun setWebPanel(on: Boolean, done: (String?) -> Unit)

    /** Web paneli durumu değişince [onChange] ana iş parçacığında çağrılır; dönen işlev aboneliği bitirir. */
    fun watchWebPanel(onChange: () -> Unit): () -> Unit

    /** [text] için QR modül matrisi (kenar boşluksuz); üretilemezse null. */
    fun qr(text: String): QrMatrix?

    /** FrostNav ekolayzerinin kayıtlı durumu ve hazır ayar listesi. */
    fun readEqualizer(): EqState

    /** Ekolayzeri FrostNav'ın kendi yolundan ([EqState.presets] içinden bir hazır ayarla) günceller. */
    fun setEqualizer(enabled: Boolean, preset: String)

    /** FrostNav'ın aç/kapa otomasyonları, sabit sırayla. */
    fun readAutomations(): List<AutomationItem>

    /** Otomasyonu FrostNav ayar sekmesinin anahtarıyla aynı yoldan açar/kapatır (yalnızca [AutomationItem.toggleable]). */
    fun setAutomation(id: AutomationId, on: Boolean)

    /** Bölünmüş ekran için FrostNav'da seçili uygulama çifti (paket adları). */
    fun readSplitPair(): SplitPair

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

    /** Yansıtmayı FrostNav servisinin aç/kapa giriş noktasıyla başlatır/durdurur. */
    fun setCluster(open: Boolean)

    /** FrostNav'ı ilgili ayar sekmesinde açar. */
    fun openTab(activity: Activity, tab: FrostNavTab)

    companion object {
        /** FrostNav'sız derleme: hiçbir şey yok, hiçbir şey yapılmaz. */
        val NONE: FrostNavFeatures = object : FrostNavFeatures {
            override val available = false
            override fun readWebPanel() = WebPanelState(running = false, url = null, reachable = false)
            override fun setWebPanel(on: Boolean, done: (String?) -> Unit) = Unit
            override fun watchWebPanel(onChange: () -> Unit): () -> Unit = {}
            override fun qr(text: String): QrMatrix? = null
            override fun readEqualizer() = EqState(supported = false, enabled = false, preset = "", presets = emptyList())
            override fun setEqualizer(enabled: Boolean, preset: String) = Unit
            override fun readAutomations(): List<AutomationItem> = emptyList()
            override fun setAutomation(id: AutomationId, on: Boolean) = Unit
            override fun readSplitPair() = SplitPair(null, null)
            override fun startSplit(done: (String?) -> Unit) = Unit
            override fun startSplitWithMiniDash(done: (String?) -> Unit) = Unit
            override fun startDriveScreen(mapPkg: String?, route: Intent?, done: (String?) -> Unit) = Unit
            override fun mapFullScreen(pkg: String, done: (Boolean) -> Unit) = Unit
            override fun endDriveScreen() = Unit
            override fun openFullScreen(pkg: String) = Unit
            override fun driveMapSlot(): android.graphics.Rect? = null
            override fun readCluster() = ClusterState(open = false, target = null)
            override fun setCluster(open: Boolean) = Unit
            override fun openTab(activity: Activity, tab: FrostNavTab) = Unit
        }
    }
}

/** Widget'ların açabildiği FrostNav sekmeleri; gerçek sekme numaraları [LauncherEdition]'da eşlenir. */
enum class FrostNavTab { WEB, EQUALIZER, CLIMATE_AUTOMATION, SUNROOF_AUTOMATION, FACE_RECOGNITION, SPLIT_SCREEN, PROJECTION }

/** [url]: sunucu açıkken FrostNav'ın gösterdiği "http://ip:port"; [reachable] false ise adres yalnızca yerel (127.x). */
data class WebPanelState(val running: Boolean, val url: String?, val reachable: Boolean)

/** [preset]: kayıtlı hazır ayar kimliği ("flat", "bass", … ya da elle ayar için "custom"). */
data class EqState(val supported: Boolean, val enabled: Boolean, val preset: String, val presets: List<String>)

enum class AutomationId { CLIMATE, SUNROOF, FACE }

/** [toggleable] false: launcher'dan açılamaz (ör. yüz tanıma kamera izni ister), dokunuş FrostNav sekmesini açar. */
data class AutomationItem(val id: AutomationId, val on: Boolean, val toggleable: Boolean)

data class SplitPair(val first: String?, val second: String?)

/** [target]: göstergede açılacak uygulamanın paketi (FrostNav "Yansıtma" sekmesinde seçilen); yoksa null. */
data class ClusterState(val open: Boolean, val target: String?)

/** Kare QR modül matrisi; [get] true = koyu modül. */
class QrMatrix(val size: Int, private val bits: BooleanArray) {
    init { require(bits.size == size * size) }
    operator fun get(x: Int, y: Int): Boolean = bits[y * size + x]
}

/** FrostNav widget'larının saf kuralları (JVM'de sınanır). */
object FrostNavWidgetMath {

    /** FrostNav web sunucusunun sabit portu (WebServerManager.startServer). */
    const val WEB_PORT = 7462

    /** FrostNav'ın gösterdiği biçimde panel adresi; adres yoksa null. */
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
