package com.mapcontrol.launcher

/**
 * Uyarı şeridinin (Sahne şeridi ve Mini gösterge) saf kuralları; JVM'de sınanır.
 *
 * Uyarı satırı yalnızca bölünmüş ekranda BAŞKA YERDE görünmeyen şeyleri söyler (ana ekran o sırada gizli, fabrika çubuğu
 * yalnızca klimayı gösterir): motor suyu sıcak, akü zayıf / yüksek, düşük lastik (psi ile), yakıt kapağı açık, düşük yakıt,
 * yaklaşan / geçmiş bakım ve kötü kabin havası. Eşikler Sahne ile aynıdır ([HealthMath], [StageCorners], [TireDecode]); yeni
 * eşik yoktur.
 */
internal object MiniAlerts {

    /**
     * Uyarı türü; sıra önem sırasıdır (küçük = önce): motor suyu / akü > düşük lastik > yakıt kapağı > yakıt az > bakım > hava.
     * Bunların hiçbiri araç çiziminde görünmez (§10: çizimde görünen kapı, bagaj, kaput, cam yazılmaz).
     */
    enum class Kind { ENGINE_HOT, BATTERY_LOW, BATTERY_HIGH, TIRE, FUEL_CAP, FUEL_LOW, SERVICE_OVERDUE, SERVICE_NEAR, AIR_BAD }

    /**
     * Tek uyarı ve metni kuracak değerler ([tireLow] lastikte, [serviceKm] bakımda, [pm25] havada, [batteryV] aküde).
     */
    data class Item(
        val kind: Kind,
        val tireLow: TireLow? = null,
        val serviceKm: Int? = null,
        val pm25: Int? = null,
        val batteryV: Double? = null,
    )

    /** Satırda aynı anda adlandırılan en çok uyarı; fazlası "+N" olur. */
    const val MAX_NAMED = 2

    fun build(
        tires: TireReading?,
        lowFuel: Boolean?,
        serviceKm: Int?,
        serviceDays: Int?,
        pm25In: Int?,
        batteryV: Double? = null,
        fuelCapOpen: Boolean = false,
        batteryHighHeld: Boolean = false,
    ): List<Item> {
        val low = TireDecode.low(tires)
        return build(TireDecode.alertShown(tires?.warning, low), low, lowFuel, serviceKm, serviceDays, pm25In, batteryV, fuelCapOpen, batteryHighHeld)
    }

    /**
     * Sahne uyarı şeridi de aynı kuralları kullanır (§10): araç kartının özetinden gelen lastik uyarısı ([tireAlert],
     * [tireLow]) ile. Kapı, bagaj, kaput, cam ve cam tavan burada yoktur; onları araç çizimi gösterir. Akü [HealthMath]
     * eşikleriyle (okunamayan değer uyarı sayılmaz); motor suyu uyarısı derece sinyali doğrulanana dek yok ([CoolantStatus]);
     * yakıt kapağı çizimde yoktur, şeritte yazılır.
     */
    fun build(
        tireAlert: Boolean,
        tireLow: TireLow?,
        lowFuel: Boolean?,
        serviceKm: Int?,
        serviceDays: Int?,
        pm25In: Int?,
        batteryV: Double? = null,
        fuelCapOpen: Boolean = false,
        /** Aşırı gerilim 30 sn sürdü mü ([TripData.batteryHighHeld]); değilse "Akü yüksek" çıkmaz. */
        batteryHighHeld: Boolean = false,
    ): List<Item> = buildList {
        val health = HealthMath.issues(batteryV, null, null, null, batteryHighHeld)
        if (HealthMath.Issue.BATTERY_LOW in health) add(Item(Kind.BATTERY_LOW, batteryV = batteryV))
        if (HealthMath.Issue.BATTERY_HIGH in health) add(Item(Kind.BATTERY_HIGH, batteryV = batteryV))
        if (tireAlert) add(Item(Kind.TIRE, tireLow = tireLow))
        if (fuelCapOpen) add(Item(Kind.FUEL_CAP))
        if (lowFuel == true) add(Item(Kind.FUEL_LOW))
        when {
            HealthMath.serviceOverdue(serviceKm, serviceDays) -> add(Item(Kind.SERVICE_OVERDUE))
            StageCorners.serviceNear(serviceKm, serviceDays) -> add(Item(Kind.SERVICE_NEAR, serviceKm = serviceKm))
        }
        if (StageCorners.airShown(pm25In)) add(Item(Kind.AIR_BAD, pm25 = pm25In))
    }

    /** Adlandırılan uyarılar ve "+N" sayısı (en önemli uyarı hiç kaybolmaz). */
    fun visible(all: List<Item>): Pair<List<Item>, Int> = StageNotices.visible(all, MAX_NAMED)
}
