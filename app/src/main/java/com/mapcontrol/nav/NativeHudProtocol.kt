package com.mapcontrol.nav

import com.mapcontrol.launcher.NavDistance
import com.mapcontrol.launcher.NavManeuver
import com.mapcontrol.launcher.NavigationState
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode

/** Değer eşitliği paketlerin içeriğine göre yapılır; gönderici aynı kareyi yeniden göndermek zorunda kalmaz. */
internal data class NativeHudFrame(
    val packets: List<List<Int>>,
    val sourcePackage: String?,
    val hasTurn: Boolean,
    val hasRemaining: Boolean,
)

/**
 * SVVDSMap adapter.c.a(AmapRoadInfoBean) ile aynı MODULE_HUD / 200 paketleri.
 * Oklar LionManeuver + NaviUtil.formatLionNaviArrow üzerinden eşlendi.
 */
internal object NativeHudProtocol {
    private const val MAX_NAME_BYTES = 47
    private const val MAX_TURN = 0xFFFFFFL
    private const val MAX_REMAINING = 0xFFFFFFFFL

    fun start(): IntArray = intArrayOf(52, 1, 0)
    fun stop(): IntArray = intArrayOf(3, 1, 1)

    fun encode(state: NavigationState, includeRawMetric: Boolean = true): NativeHudFrame? {
        if (!state.active) return null
        val turn = distance(state.distance, MAX_TURN)
        val remaining = distance(state.remainingDistance, MAX_REMAINING)
        val name = nameBytes(state.street.orEmpty())
        if (turn == null && remaining == null && name.isEmpty()) return null

        val packets = ArrayList<List<Int>>(5)
        packets.add(listOf(48, name.size) + name)
        val icon = signedBytes(arrow(state.maneuver).toLong(), 2)
        if (includeRawMetric && turn != null) packets.add(listOf(49, 5) + icon + signedBytes(turn.meters, 3))
        if (includeRawMetric && remaining != null) packets.add(listOf(51, 4) + signedBytes(remaining.meters, 4))
        if (turn != null) packets.add(listOf(50, 6) + icon + signedBytes(turn.tenths, 3) + turn.unit)
        if (remaining != null) packets.add(listOf(53, 5) + signedBytes(remaining.tenths, 4) + remaining.unit)
        return NativeHudFrame(packets, state.sourcePackage, turn != null, remaining != null)
    }

    private data class Distance(val meters: Long, val tenths: Long, val unit: Int)

    /** Bozuk veya temsil edilemeyen alan gönderilmez; diğer geçerli alanlar kullanılabilir. */
    private fun distance(value: NavDistance?, maximum: Long): Distance? {
        if (value == null || value.meters < 0 || value.meters.toLong() > maximum) return null
        val unit = when (value.unit) {
            "m" -> 0
            "km" -> 1
            else -> return null
        }
        val number = value.value.replace(',', '.').toBigDecimalOrNull() ?: return null
        if (number.signum() < 0) return null
        val scaled = number.multiply(BigDecimal.TEN)
        if (scaled > BigDecimal.valueOf(maximum)) return null
        val tenths = if (scaled < BigDecimal.ONE) BigDecimal.ZERO else scaled.setScale(0, RoundingMode.DOWN)
        return Distance(value.meters.toLong(), tenths.toLong(), unit)
    }

    /** Sayı baytları OEM Java byte[] yolundaki gibi işaret genişletilir, düşük bayt önce gider. */
    private fun signedBytes(value: Long, count: Int): List<Int> =
        List(count) { index -> ((value ushr (index * 8)) and 0xFF).toByte().toInt() }

    /** Metin baytları OEM metodundaki gibi 0..255; son UTF-8 karakteri bütünüyle sığmalıdır. */
    private fun nameBytes(name: String): List<Int> {
        val bytes = ByteArrayOutputStream(MAX_NAME_BYTES)
        var offset = 0
        while (offset < name.length) {
            val point = Character.codePointAt(name, offset)
            val encoded = String(Character.toChars(point)).toByteArray(Charsets.UTF_8)
            if (bytes.size() + encoded.size > MAX_NAME_BYTES) break
            bytes.write(encoded)
            offset += Character.charCount(point)
        }
        return bytes.toByteArray().map { it.toInt() and 0xFF }
    }

    private fun arrow(maneuver: NavManeuver): Int = when (maneuver) {
        NavManeuver.STRAIGHT -> 9
        NavManeuver.SLIGHT_LEFT, NavManeuver.EXIT_LEFT -> 4
        NavManeuver.SLIGHT_RIGHT, NavManeuver.EXIT_RIGHT -> 5
        NavManeuver.LEFT -> 2
        NavManeuver.RIGHT -> 3
        NavManeuver.HARD_LEFT -> 6
        NavManeuver.HARD_RIGHT -> 7
        NavManeuver.UTURN_LEFT -> 8
        NavManeuver.UTURN_RIGHT -> 19
        NavManeuver.ROUNDABOUT_ENTER -> 29
        NavManeuver.ROUNDABOUT_EXIT -> 55
        NavManeuver.FERRY -> 49
        NavManeuver.FINISH -> 15
        NavManeuver.WAYPOINT -> 10
        NavManeuver.UNKNOWN -> 68
    }
}
