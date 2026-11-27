package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManeuverMaskTest {

    private val ink = 0xFFFFFFFF.toInt()
    private val clear = 0x00000000

    /**
     * Basit sola dönüş oku (Haritalar'ın `maneuver_turn_normal_left` biçimi): sağ altta dikey gövde, üstte sola giden
     * yatay kol, solda ok ucu. [unit] ölçek, [pad] kenar boşluğu, [mirror] sağa dönüş.
     */
    private fun turnArrow(unit: Int, pad: Int, mirror: Boolean = false, bg: Int = clear, fg: Int = ink): Triple<IntArray, Int, Int> {
        val cells = listOf(
            "..X.....",
            ".XX.....",
            "XXXXXXXX",
            ".XX...XX",
            "..X...XX",
            "......XX",
            "......XX",
            "......XX",
        )
        return draw(cells, unit, pad, mirror, bg, fg)
    }

    private fun straightArrow(unit: Int, pad: Int): Triple<IntArray, Int, Int> = draw(
        listOf(
            "...XX...",
            "..XXXX..",
            ".XXXXXX.",
            "...XX...",
            "...XX...",
            "...XX...",
            "...XX...",
            "...XX...",
        ),
        unit, pad, false, clear, ink,
    )

    private fun draw(cells: List<String>, unit: Int, pad: Int, mirror: Boolean, bg: Int, fg: Int): Triple<IntArray, Int, Int> {
        val n = cells.size * unit + 2 * pad
        val px = IntArray(n * n) { bg }
        for (cy in cells.indices) for (cx in cells[cy].indices) {
            val c = if (mirror) cells[cy][cells[cy].length - 1 - cx] else cells[cy][cx]
            if (c != 'X') continue
            for (y in 0 until unit) for (x in 0 until unit) px[(pad + cy * unit + y) * n + pad + cx * unit + x] = fg
        }
        return Triple(px, n, n)
    }

    private fun mask(t: Triple<IntArray, Int, Int>) = ManeuverMask.fromArgb(t.first, t.second, t.third)!!

    private val templates by lazy {
        listOf(
            ManeuverMask.Template("maneuver_turn_normal_left", NavManeuver.LEFT, mask(turnArrow(12, 0))),
            ManeuverMask.Template("maneuver_turn_normal_right", NavManeuver.RIGHT, mask(turnArrow(12, 0, mirror = true))),
            ManeuverMask.Template("maneuver_straight", NavManeuver.STRAIGHT, mask(straightArrow(12, 0))),
        )
    }

    @Test
    fun leftArrowWithOtherSizeAndPaddingMatchesLeft() {
        val m = ManeuverMask.match(mask(turnArrow(7, 20)), templates)!!
        assertEquals(NavManeuver.LEFT, m.maneuver)
        assertTrue(m.score > 0.9f)
    }

    @Test
    fun mirroredArrowMatchesRight() {
        assertEquals(NavManeuver.RIGHT, ManeuverMask.match(mask(turnArrow(5, 3, mirror = true)), templates)!!.maneuver)
    }

    @Test
    fun straightMatchesStraight() {
        assertEquals(NavManeuver.STRAIGHT, ManeuverMask.match(mask(straightArrow(9, 11)), templates)!!.maneuver)
    }

    @Test
    fun darkArrowOnOpaqueBackgroundMatches() {
        val t = turnArrow(10, 6, bg = 0xFF1A73E8.toInt(), fg = 0xFFFFFFFF.toInt())
        assertEquals(NavManeuver.LEFT, ManeuverMask.match(mask(t), templates)!!.maneuver)
    }

    @Test
    fun emptyOrTinyImageGivesNoMask() {
        assertNull(ManeuverMask.fromArgb(IntArray(100) { clear }, 10, 10))
        assertNull(ManeuverMask.fromArgb(IntArray(100) { if (it == 55) ink else clear }, 10, 10))
    }

    @Test
    fun unrelatedShapeIsNotForcedToAnArrow() {
        // Dolu kare: hiçbir oka yeterince benzemez.
        assertNull(ManeuverMask.match(ManeuverMask.fromArgb(IntArray(400) { ink }.also { it[0] = clear }, 20, 20)!!, templates))
    }

    @Test
    fun ambiguousMatchIsRejected() {
        val a = mask(turnArrow(10, 0))
        val t = listOf(
            ManeuverMask.Template("a", NavManeuver.LEFT, a),
            ManeuverMask.Template("b", NavManeuver.SLIGHT_LEFT, a.copyOf()),
        )
        assertNull(ManeuverMask.match(a, t))
    }

    @Test
    fun googleDrawableNames() {
        fun f(n: String) = ManeuverMask.maneuverFromGoogleName("maneuver_$n")
        assertEquals(NavManeuver.LEFT, f("turn_normal_left"))
        assertEquals(NavManeuver.RIGHT, f("turn_normal_right"))
        assertEquals(NavManeuver.SLIGHT_LEFT, f("turn_slight_left"))
        assertEquals(NavManeuver.HARD_RIGHT, f("turn_sharp_right"))
        assertEquals(NavManeuver.UTURN_LEFT, f("u_turn_left"))
        assertEquals(NavManeuver.SLIGHT_RIGHT, f("fork_right"))
        assertEquals(NavManeuver.SLIGHT_LEFT, f("keep_left"))
        assertEquals(NavManeuver.STRAIGHT, f("merge_left"))
        assertEquals(NavManeuver.SLIGHT_RIGHT, f("off_ramp_slight_right"))
        assertEquals(NavManeuver.EXIT_RIGHT, f("off_ramp_normal_right"))
        assertEquals(NavManeuver.HARD_LEFT, f("off_ramp_sharp_left"))
        assertEquals(NavManeuver.LEFT, f("on_ramp_normal_left"))
        assertEquals(NavManeuver.ROUNDABOUT_ENTER, f("roundabout_enter_and_exit_cw_normal_left"))
        assertEquals(NavManeuver.ROUNDABOUT_EXIT, f("roundabout_exit_ccw"))
        assertEquals(NavManeuver.FINISH, f("destination_left"))
        assertEquals(NavManeuver.STRAIGHT, f("straight"))
        assertEquals(NavManeuver.STRAIGHT, f("name_change"))
        assertNotNull(f("depart"))
        assertNull(f("something_else"))
    }

    @Test
    fun iconManeuverWinsOverText() {
        val s = GoogleNavParse.parse("90 m", "Ataşehir Blv.", "5 dk. · 1,3 km · TVS: 19:10", iconManeuver = NavManeuver.LEFT)!!
        assertEquals(NavManeuver.LEFT, s.maneuver)
        val plain = GoogleNavParse.parse("90 m", "Ataşehir Blv.", "5 dk. · 1,3 km · TVS: 19:10")!!
        assertEquals(NavManeuver.UNKNOWN, plain.maneuver)
    }
}
