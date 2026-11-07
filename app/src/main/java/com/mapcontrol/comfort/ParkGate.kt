package com.mapcontrol.comfort

/**
 * Park kapısı kararı (saf). Girdi: gösterge hızı (ID_SPEED_GAUGE_DISPLAY 119) ve eski hız (ID_CAR_SPEED 0).
 * Tavan perdesi ve bagaj yazmaları bu kapıdan geçmeden araca gitmez.
 *
 * Karar hıza ve vitese dayanır: 119 numaralı sinyal okunmalı ve 0 olmalı; eski hız cevap veriyorsa o da 0 olmalı;
 * vites (ID_GEARBOX_STATE 26) okunmalı ve P (== 1, bu araçta ölçüldü) olmalı. Yalnızca adb test aracı kullanır;
 * launcher widget'larına sürüş/park kilidi KONMAZ. El freni kapıya katılmaz, yalnızca günlüğe yazılır.
 */
object ParkGate {

    data class Decision(val allowed: Boolean, val reason: String)

    const val GEAR_PARK = 1

    fun evaluate(gaugeSpeed: Int?, legacySpeed: Int?, gear: Int?): Decision {
        if (gaugeSpeed == null) return Decision(false, "hız (119) okunamadı")
        if (gaugeSpeed !in 0..300) return Decision(false, "hız (119) geçersiz: $gaugeSpeed")
        if (gaugeSpeed != 0) return Decision(false, "araç hareketli: hız=$gaugeSpeed km/sa")
        if (legacySpeed != null && legacySpeed in 1..300) return Decision(false, "eski hız sinyali hareketli: $legacySpeed")
        if (gear == null) return Decision(false, "vites (26) okunamadı")
        if (gear != GEAR_PARK) return Decision(false, "vites P değil: $gear")
        return Decision(true, "park: hız=0, vites=P")
    }
}
