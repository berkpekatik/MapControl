package com.mapcontrol.launcher

/**
 * Araç sağlığı özeti (saf mantık, JVM'de sınanır). Akü, motor suyu, yakıt uyarısı ve bakım aralığını tek karara
 * indirir: sorun yoksa sakin bir "Her şey normal"; sorun varsa en önemlisi başta olacak şekilde sorun listesi.
 * Yalnızca eldeki veriyi değerlendirir; okunamayan değer sorun sayılmaz.
 */
internal object HealthMath {

    /**
     * Sorunlar önem sırasıyla (küçük sıra = daha önemli). [ENGINE_HOT] şimdilik üretilmez: motor suyu için doğrulanmış
     * bir derece sinyali yok ([CoolantStatus]).
     */
    enum class Issue { ENGINE_HOT, BATTERY_LOW, BATTERY_HIGH, SERVICE_OVERDUE, LOW_FUEL, SERVICE_SOON }

    const val BATTERY_MIN_V = 12.0
    /** Motor çalışırken 14,5 V normal (araçta ölçüldü); aşırı gerilim ancak bunun üstünde ve [OverVoltageGate] süresince. */
    const val BATTERY_MAX_V = 15.3
    /** Aşırı gerilim uyarısı bu kadar sürmeden gösterilmez (şarj dalgalanması kısa sıçrar). */
    const val BATTERY_HIGH_HOLD_MS = 30_000L
    const val SOON_KM = 1_000
    const val SOON_DAYS = 30

    /** Hiçbir değer okunamıyorsa özet de yoktur (kutucuk "veri bekleniyor" gösterir). [coolantCode]: [CoolantStatus] kodu. */
    fun hasData(batteryV: Double?, coolantCode: Int?, lowFuel: Boolean?, serviceKm: Int?, serviceDays: Int?): Boolean =
        batteryV != null || coolantCode != null || lowFuel != null || serviceKm != null || serviceDays != null

    /**
     * [batteryHighHeld]: aşırı gerilim [BATTERY_HIGH_HOLD_MS] boyunca sürdü mü ([OverVoltageGate], [TripData.batteryHighHeld]);
     * false iken eşik üstü gerilim de sorun sayılmaz. Düşük gerilim beklemeden sayılır. Motor suyu sıcaklık eşiği kapalı
     * ([CoolantStatus]: okunan değer derece değil).
     */
    fun issues(batteryV: Double?, lowFuel: Boolean?, serviceKm: Int?, serviceDays: Int?, batteryHighHeld: Boolean): List<Issue> = buildList {
        if (batteryV != null && batteryV < BATTERY_MIN_V) add(Issue.BATTERY_LOW)
        if (batteryHighHeld && batteryV != null && batteryV > BATTERY_MAX_V) add(Issue.BATTERY_HIGH)
        if (serviceOverdue(serviceKm, serviceDays)) add(Issue.SERVICE_OVERDUE)
        if (lowFuel == true) add(Issue.LOW_FUEL)
        if (serviceSoon(serviceKm, serviceDays) && !serviceOverdue(serviceKm, serviceDays)) add(Issue.SERVICE_SOON)
    }

    /** Bakım zamanı geçti: kalan km ya da gün 0'a indi. */
    fun serviceOverdue(km: Int?, days: Int?): Boolean = (km != null && km <= 0) || (days != null && days <= 0)

    /** Bakım yaklaşıyor: <= 1000 km ya da <= 30 gün kaldı. */
    fun serviceSoon(km: Int?, days: Int?): Boolean = (km != null && km <= SOON_KM) || (days != null && days <= SOON_DAYS)

    /**
     * Eski kaydedilmiş düzenlerde kaldırılan widget anahtarları ve yeni karşılıkları: Akü ve Bakım "Araç sağlığı"na,
     * Toplam "Yolculuk"a katıldı.
     */
    val REMOVED_KEYS: Map<String, String> = mapOf(
        "battery" to "health",
        "service" to "health",
        "trip_totals" to "trip",
    )
}

/**
 * Motor suyu (ReadOnly ID_WATER_TEMPERATURE, 100): araçta °C değil bir **durum kodu**. Tiggo 7'de soğuk çalıştırmada da
 * sürüşten sonra da hep 2 okundu; 2 = normal sayılır. Diğer değerlerin anlamı bilinmiyor (aşırı ısınma kodu görülmedi),
 * doğrulanana dek "—" gösterilir. Gerçek bir derece sinyali doğrulanana dek motor suyu °C gösterimi ve "Motor sıcak"
 * eşiği kapalı. Aday: NewEnergy ID_VCU_TEMPERATURE_C (83) bir sürüşte 64→69 yükseldi; doğrulanmadı, kullanılmıyor.
 */
internal object CoolantStatus {
    const val NORMAL = 2

    /** Kod "normal" mi; bilinmeyen kod ve okunamayan değer false (ekranda "—"). */
    fun isNormal(code: Int?): Boolean = code == NORMAL
}

/**
 * Aşırı gerilim süzgeci (saf, JVM'de sınanır): gerilim [HealthMath.BATTERY_MAX_V]'un üstüne çıktığı andan itibaren [holdMs]
 * kesintisiz üstte kalırsa true döner; eşik altına inen ya da okunamayan (null) değer sayacı sıfırlar.
 */
internal class OverVoltageGate(private val holdMs: Long = HealthMath.BATTERY_HIGH_HOLD_MS) {
    private var since = -1L

    fun update(volts: Double?, nowMs: Long): Boolean {
        if (volts == null || volts <= HealthMath.BATTERY_MAX_V) {
            since = -1L
            return false
        }
        if (since < 0L) since = nowMs
        return nowMs - since >= holdMs
    }
}
