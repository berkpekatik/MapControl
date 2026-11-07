package com.mapcontrol.launcher

/** Taramadan gelen ham bir ağ: [ssid] tırnaksız, [level] 0..4, [capabilities] ScanResult.capabilities. */
internal data class WifiScan(val ssid: String, val level: Int, val capabilities: String?) {
    val secured: Boolean get() = WifiListShaper.isSecured(capabilities)
}

/** Panelde gösterilen bir satır. */
internal data class WifiRow(val scan: WifiScan, val saved: Boolean)

/** Panelin Wi-Fi'ye dokunan tek yüzü; uygulama sürümü FrostNav'ın WifiController'ını, test sürümü sınırlı WifiManager'ı kullanır. */
internal interface WifiBackend {
    /** Yakındaki ağlar taranıp bağlanılabiliyor mu (test sürümünde false: yalnızca aç/kapa + geçerli ağ). */
    val canList: Boolean
    fun isEnabled(): Boolean
    /** Arka planda açar/kapatır, bitince ana iş parçacığında [done] çağrılır. */
    fun setEnabled(on: Boolean, done: () -> Unit)
    fun hasLocationPermission(): Boolean
    /** Sistem konumu açık mı (Settings.Secure.LOCATION_MODE != 0); kapalıyken Android tarama sonucu vermez. */
    fun isLocationOn(): Boolean
    fun startScan(): Boolean
    fun scanResults(): List<WifiScan>
    /** Bağlı ağ: (ad ya da bilinmiyorsa null, seviye); bağlı değilse null. */
    fun current(): Pair<String?, Int>?
    fun isSaved(ssid: String): Boolean
    /** Bağlanmayı başlatır; [password] yalnızca yeni şifreli ağda. Başlatıldıysa true. Arka planda çağrılır. */
    fun connect(scan: WifiScan, password: String?): Boolean
    fun disconnect(): Boolean
}

/** Liste şekillendirme (saf, JVM'de sınanır). */
internal object WifiListShaper {
    /** Sürüşte ve park halinde aynı anda görünen en çok satır. */
    const val MAX_VISIBLE = 6

    fun isSecured(capabilities: String?): Boolean =
        capabilities != null && (capabilities.contains("WPA") || capabilities.contains("WEP"))

    fun cleanSsid(raw: String?): String? {
        val s = raw?.trim()?.removeSurrounding("\"")?.trim() ?: return null
        if (s.isEmpty() || s == "<unknown ssid>" || s == "0x") return null
        return s
    }

    /**
     * Aynı adlı ağları tek satıra indirir (en güçlüsü kalır), boş adları atar, bağlı olanı çıkarır (üstte ayrı satırdır),
     * en güçlüden zayıfa sıralar (eşitte ada göre). [driving] iken en çok [MAX_VISIBLE] satır (kaydırma yok), park halinde hepsi.
     */
    fun shape(scans: List<WifiScan>, connectedSsid: String?, saved: (String) -> Boolean, driving: Boolean): List<WifiRow> {
        val best = LinkedHashMap<String, WifiScan>()
        for (s in scans) {
            val name = cleanSsid(s.ssid) ?: continue
            val prev = best[name]
            if (prev == null || s.level > prev.level) best[name] = s.copy(ssid = name)
        }
        val rows = best.values
            .filter { it.ssid != connectedSsid }
            .sortedWith(compareByDescending<WifiScan> { it.level }.thenBy { it.ssid.lowercase() })
            .map { WifiRow(it, saved(it.ssid)) }
        return if (driving) rows.take(MAX_VISIBLE) else rows
    }

    /** Şifre girişi yalnızca park halinde: Sürüş düzeni etkin değil ve araç hareket etmiyor ([driving] ikisini birleştirir). */
    fun passwordAllowed(driving: Boolean): Boolean = !driving

    /** Kaydırma yalnızca park halinde (sürüşte kaydırma yok). */
    fun scrollAllowed(driving: Boolean): Boolean = !driving
}

/**
 * Ağ listesinin önündeki engel (saf, JVM'de sınanır). Android 9+ tarama ve sonuç için hem ACCESS_FINE_LOCATION hem de açık
 * sistem konumu ister; eksikse WifiService sessizce boş liste döner ("startScan not allowed ... has no location permission").
 */
internal object WifiAccess {
    enum class Gate { LIST, NEED_PERMISSION, LOCATION_OFF }

    /** Önce izin (verilmeden konum ayarı işe yaramaz), sonra sistem konumu. */
    fun gate(hasPermission: Boolean, locationOn: Boolean): Gate = when {
        !hasPermission -> Gate.NEED_PERMISSION
        !locationOn -> Gate.LOCATION_OFF
        else -> Gate.LIST
    }

    enum class PermissionAction { REQUEST, OPEN_APP_SETTINGS }

    /**
     * İzin düğmesinin işi: daha önce reddedildiyse ve sistem artık gerekçe göstermiyorsa ("Bir daha sorma", Android 11+'da iki
     * ret ya da politika) pencere açılmaz, uygulama ayrıntıları açılır; aksi halde çalışma anında izin istenir.
     */
    @JvmStatic
    fun permissionAction(deniedBefore: Boolean, showRationale: Boolean): PermissionAction =
        if (deniedBefore && !showRationale) PermissionAction.OPEN_APP_SETTINGS else PermissionAction.REQUEST

    enum class PermissionResult { GRANTED, DENIED, CANCELLED }

    /**
     * İzin penceresi sonucu; istekte ilk izin ACCESS_FINE_LOCATION olmalı. Boş dizi: pencere yarıda kesildi (sayılmaz).
     * Android 12+'da "Yaklaşık" seçilirse yalnızca COARSE verilir: Wi-Fi tarama sonuçları FINE ister, bu yüzden ret sayılır.
     */
    @JvmStatic
    fun permissionResult(grantResults: IntArray): PermissionResult = when {
        grantResults.isEmpty() -> PermissionResult.CANCELLED
        grantResults[0] == PERMISSION_GRANTED -> PermissionResult.GRANTED
        else -> PermissionResult.DENIED
    }

    /**
     * Bu süreçte konum izni en az bir kez reddedildi mi (başlatıcı paneli ve FrostNav sekmesi ortak; izin uygulamaya ait).
     * Pencere açılmadan önce değil sonuçta işlenir: dışına dokunup kapatmak ya da yarıda kesilmek düğmeyi "Ayarları aç"a çevirmez.
     */
    @Volatile private var deniedBefore = false

    /** Sonucu işler ve döner; yalnızca gerçek ret [deniedBefore]'u açar. */
    @JvmStatic
    fun onPermissionResult(grantResults: IntArray): PermissionResult =
        permissionResult(grantResults).also { if (it == PermissionResult.DENIED) deniedBefore = true }

    @JvmStatic
    fun isDeniedBefore(): Boolean = deniedBefore

    /** PackageManager.PERMISSION_GRANTED (JVM testinde Android sınıfı olmadan). */
    private const val PERMISSION_GRANTED = 0
}
