package com.mapcontrol.nav

import com.mapcontrol.launcher.NavManeuver
import com.mapcontrol.launcher.NavigationState

/** Değer eşitliği paketlerin içeriğine göre yapılır; gönderici aynı kareyi yeniden göndermek zorunda kalmaz. */
internal data class NativeHudFrame(
    val packets: List<List<Int>>,
    val sourcePackage: String?,
)

/**
 * JCarTools J7 yerleşik HUD paketleri (ProInfoJ.SendHUD / StartHUD).
 * Aktif her karede tam olarak üç veri paketi gönderilir; eksik alanlar sıfırlanır.
 */
internal object NativeHudProtocol {
    private const val MAX_NAME_BYTES = 47

    fun start(): IntArray = intArrayOf(52, 1, 0)

    /** Rota bitişinde StartHUD olmadan gönderilen üç temizleme paketi. */
    fun blank(): NativeHudFrame = NativeHudFrame(
        listOf(
            listOf(48, 1, 0x20),
            listOf(49, 5, -1, -1, 0, 0, 0),
            listOf(51, 4, 0, 0, 0, 0),
        ),
        null,
    )

    fun encode(state: NavigationState): NativeHudFrame? {
        if (!state.active) return null
        val name = nameBytes(state.street)
        val turn = turnCode(state.maneuver, state.roundaboutExit)
        val turnMeters = meters(state.distance?.meters)
        val remainingMeters = meters(state.remainingDistance?.meters)
        val packets = mutableListOf(
            listOf(48, name.size) + name,
            listOf(49, 5) + signedBytes(turn, 2) + signedBytes(turnMeters, 3),
            listOf(51, 4) + signedBytes(remainingMeters, 4),
        )
        // J7 HUD ok ve mesafeyi OEM SVVDSMap paketlerinden (50: ok + onda bir mesafe + birim, 53: kalan) çizer;
        // yalnız 48/49/51 gönderildiğinde araçta yalnız yol adı göründü (2026-11-24). PR #50 biçimi.
        tenths(state.distance, 0xFFFFFF)?.let { (value, unit) ->
            packets.add(listOf(50, 6) + signedBytes(turn, 2) + signedBytes(value, 3) + unit)
        }
        tenths(state.remainingDistance, Int.MAX_VALUE)?.let { (value, unit) ->
            packets.add(listOf(53, 5) + signedBytes(value, 4) + unit)
        }
        return NativeHudFrame(packets, state.sourcePackage)
    }

    /** Mesafe, kendi biriminde onda bir hassasiyetle; birim kodu m=0, km=1, mi=2, yd/ft=3. Geçersizse null. */
    private fun tenths(value: com.mapcontrol.launcher.NavDistance?, maximum: Int): Pair<Int, Int>? {
        if (value == null || value.meters < 0) return null
        val unit = when (value.unit) {
            "m" -> 0
            "km" -> 1
            "mi" -> 2
            "yd", "ft" -> 3
            else -> return null
        }
        val number = value.value.replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() >= 0 } ?: return null
        val scaled = if (value.unit == "ft") number.multiply(java.math.BigDecimal.TEN)
            .divide(java.math.BigDecimal.valueOf(3), 0, java.math.RoundingMode.DOWN)
            else number.multiply(java.math.BigDecimal.TEN).setScale(0, java.math.RoundingMode.DOWN)
        if (scaled > java.math.BigDecimal.valueOf(maximum.toLong())) return null
        return scaled.toInt() to unit
    }

    private fun meters(value: Int?): Int = value?.takeIf { it >= 0 } ?: 0

    /** Java byte[] değerleri işaret genişletilir, düşük bayt önce gider. */
    private fun signedBytes(value: Int, count: Int): List<Int> =
        List(count) { index -> ((value ushr (index * 8)) and 0xFF).toByte().toInt() }

    /** UTF-8 metnin ilk 47 ham baytı; eksik yol adı tek boşlukla temizlenir. */
    private fun nameBytes(name: String?): List<Int> {
        val text = name ?: " "
        return text.toByteArray(Charsets.UTF_8).take(MAX_NAME_BYTES).map { it.toInt() and 0xFF }
    }

    /**
     * Çıkış numaralı kavşak oku: OEM harita adaptörü (SVVDSMap `adapter.a.b`) kavşak okunu (11/12, sağdan trafik) çıkış
     * numarasıyla 28 + n koduna çevirir (1. çıkış 29 … 10. çıkış 38); soldan trafikli 17/18 için 38 + n. Türkiye sağdan
     * trafik. Numara yoksa ya da aralık dışındaysa genel kavşak oku (11/12) gider.
     */
    private fun turnCode(maneuver: NavManeuver, roundaboutExit: Int?): Int {
        if ((maneuver == NavManeuver.ROUNDABOUT_ENTER || maneuver == NavManeuver.ROUNDABOUT_EXIT) &&
            roundaboutExit != null && roundaboutExit in 1..10
        ) return 28 + roundaboutExit
        return turnCode(maneuver)
    }

    private fun turnCode(maneuver: NavManeuver): Int = when (maneuver) {
        NavManeuver.STRAIGHT -> 9
        NavManeuver.SLIGHT_LEFT, NavManeuver.EXIT_LEFT -> 4
        NavManeuver.SLIGHT_RIGHT, NavManeuver.EXIT_RIGHT -> 5
        NavManeuver.LEFT -> 2
        NavManeuver.RIGHT -> 3
        NavManeuver.HARD_LEFT -> 6
        NavManeuver.HARD_RIGHT -> 7
        NavManeuver.UTURN_LEFT -> 8
        NavManeuver.UTURN_RIGHT -> 19
        // HUD ok kodları Amap simgeleridir (OEM SVVDSMap Amap okunu aynen geçirir): 11 kavşağa gir, 12 kavşaktan çık.
        // JCarTools'un 24/55'i açılı kavşak oklarıdır (OEM NaviUtil.formatTeleNaviArrow: 360° → 24, 135° → 55); 24 HUD'da
        // tam tur / U dönüşü gibi çiziliyordu (araçta 2026-11-26).
        NavManeuver.ROUNDABOUT_ENTER -> 11
        NavManeuver.ROUNDABOUT_EXIT -> 12
        NavManeuver.FERRY -> 49
        NavManeuver.FINISH -> 15
        // -1 yalnız bitiş/temizleme karesinindir (JCarTools e0.v0.g, [blank]); aktif karede HUD onu "rota bitti" sayıp
        // ok, mesafe ve yolu kapatıyor (araçta 2026-11-24: sonraki adımda HUD söndü, kapat-aç ile döndü). JCarTools da
        // ok bilgisi gelmezse önceki değeri tutar, hiç yoksa başlangıç değeri 0'dır (HUDMassageObj).
        NavManeuver.UNKNOWN, NavManeuver.WAYPOINT -> 0
    }
}
