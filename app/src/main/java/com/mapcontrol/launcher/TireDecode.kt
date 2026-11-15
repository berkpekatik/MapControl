package com.mapcontrol.launcher

/**
 * Lastik basıncı okuması (bar). [values]: dört tekerlek sırayla ön sol, ön sağ, arka sol, arka sağ (araçta doğrulandı) ya da tek
 * değer; okunamayan tekerlek null. [warning]: ReadOnly 95 `ID_TIRE_PRESSURE_WARNING` sıfırdan farklı; okunamadıysa null.
 * [warnWheels]: aynı sinyalin tekerlek başına çözümü ([TireDecode.warnWheels]); maske geçersizse null. Basınç sinyali (140)
 * olmayan yazılımda (T19C 2024, araçta ölçüldü 2026-11-15: 140 = [0], tanımsız kimliklerle aynı) [values] boş kalır ve
 * yalnızca uyarı tekerleği bilinir.
 */
internal data class TireReading(val values: List<Double?>, val warning: Boolean? = null, val warnWheels: List<Boolean>? = null) {
    val perWheel: Boolean get() = values.size == WHEELS

    companion object {
        const val WHEELS = 4
    }
}

/** Düşük basınç: hangi tekerlek ([wheel], 0..3; tekerlek ayrımı yoksa null) ve değeri (bar; yalnızca uyarı maskesinden geldiyse null). */
internal data class TireLow(val wheel: Int?, val bar: Double?)

/**
 * Lastik basıncı sinyalinin (ReadOnly 140 `ID_TIRE_PRESSURE`) saf çözümü (JVM'de sınanır). Araçta ölçüldü (2026-11-06, Tiggo 7):
 * ham [154, 152, 153, 153] iken gösterge ÖS 31, ÖS 30, AS 30, AS 30 psi gösterdi. Birim TPMS standardı 1,373 kPa/bit
 * (154 × 1,373 = 211,4 kPa = 30,7 psi); eleman 0 = ön sol, sonra ön sağ, arka sol, arka sağ. 1 elemanlı yük tek değer sayılır.
 * Türkiye'de sürücüler psi kullandığı için araç kartında değerler [psi] ile gösterilir.
 */
internal object TireDecode {

    /** Ham birim: 1,373 kPa/bit (TPMS standardı); bar = ham × 0,01373. */
    const val KPA_PER_BIT = 1.373

    /** Geçerli ham aralık: 0 sensör yok, 255 (0xFF) ve 0xFFFF geçersiz. */
    const val RAW_MIN = 1
    const val RAW_MAX = 254

    /** 1 bar = 14,5038 psi. */
    const val BAR_TO_PSI = 14.5038

    /** Düşük basınç eşiği, psi cinsinden (1,8 bar ≈ 26 psi). Bar eşiği bundan türetilir. */
    const val LOW_PSI = 26.0
    const val LOW_BAR = LOW_PSI / BAR_TO_PSI

    /** Ham → bar (ham × 1,373 kPa / 100); 1..254 dışı (0, 0xFF, 0xFFFF, negatif) geçersiz. */
    fun bar(raw: Int): Double? = if (raw in RAW_MIN..RAW_MAX) raw * KPA_PER_BIT / 100.0 else null

    /**
     * Okuma: ≥ 4 eleman → ilk dördü tekerlekler; 1 eleman → tek değer; başkası ya da hepsi geçersiz → basınç yok.
     * Basınç yoksa ama uyarı maskesi geçerliyse yalnızca uyarı tekerlekleriyle okuma döner (T19C); ikisi de yoksa null.
     */
    fun parse(raw: IntArray?, warningRaw: Int? = null): TireReading? {
        val warning = warningRaw?.let { it != 0 }
        val wheels = warnWheels(warningRaw)
        val values = pressures(raw) ?: return wheels?.let { TireReading(emptyList(), warning, it) }
        return TireReading(values, warning, wheels)
    }

    private fun pressures(raw: IntArray?): List<Double?>? {
        if (raw == null || raw.isEmpty()) return null
        val values = when {
            raw.size >= TireReading.WHEELS -> raw.take(TireReading.WHEELS).map(::bar)
            raw.size == 1 -> listOf(bar(raw[0]))
            else -> return null
        }
        return values.takeUnless { v -> v.all { it == null } }
    }

    /**
     * ReadOnly 95 tekerlek başına, OEM SVVehicleSetting gibi: bit0 ön sol, bit1 ön sağ, bit2 arka sol, bit3 arka sağ
     * ([TireReading.values] sırası). 0..15 dışı (0xFF vb.) ya da okunamadıysa null.
     */
    fun warnWheels(raw: Int?): List<Boolean>? =
        if (raw == null || raw !in 0..WARN_MASK_MAX) null else List(TireReading.WHEELS) { (raw shr it) and 1 == 1 }

    private const val WARN_MASK_MAX = 0x0F

    fun isLow(bar: Double?): Boolean = bar != null && bar < LOW_BAR

    /** Bar → psi, tam sayıya yuvarlanmış (gösterim için). */
    fun psi(bar: Double): Int = Math.round(bar * BAR_TO_PSI).toInt()

    /**
     * En düşük (eşiğin altındaki ilk) tekerlek; tekerlek ayrımı yoksa tek değer kontrol edilir. Değerle düşük tekerlek yoksa
     * uyarı maskesinde işaretli ilk tekerlek, değersiz ([TireLow.bar] null). Hiçbiri yoksa null.
     */
    fun low(t: TireReading?): TireLow? {
        if (t == null) return null
        if (t.perWheel) {
            val i = t.values.indexOfFirst { isLow(it) }
            if (i >= 0) return TireLow(i, t.values[i])
        } else {
            val v = t.values.firstOrNull()
            if (isLow(v)) return TireLow(null, v)
        }
        val w = t.warnWheels?.indexOfFirst { it } ?: -1
        return if (w >= 0) TireLow(w, null) else null
    }

    /** Araç kartında lastik uyarısı yalnızca uyarı sinyali açıkken ya da bir değer eşiğin altındayken görünür. */
    fun alertShown(warning: Boolean?, low: TireLow?): Boolean = warning == true || low != null
}
