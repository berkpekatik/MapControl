package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Birleşik koltuk seçicisinin saf kuralları: gösterilen mod ve "önce ötekini kapat" yazma sırası. */
class SeatChoiceTest {

    private fun heat(l: Int) = SeatTarget(SeatMode.HEAT, l)
    private fun cool(l: Int) = SeatTarget(SeatMode.COOL, l)
    private val off = SeatTarget(SeatMode.OFF)

    @Test fun heatingWhileCoolingTurnsCoolingOffFirst() {
        assertEquals(listOf(SeatWrite(vent = true, level = 0), SeatWrite(vent = false, level = 2)), SeatChoice.plan(heat = 0, vent = 1, target = heat(2)))
    }

    @Test fun coolingWhileHeatingTurnsHeatingOffFirst() {
        assertEquals(listOf(SeatWrite(vent = false, level = 0), SeatWrite(vent = true, level = 3)), SeatChoice.plan(heat = 2, vent = 0, target = cool(3)))
    }

    @Test fun sameModeOnlyChangesLevelAndSameLevelWritesNothing() {
        assertEquals(listOf(SeatWrite(vent = false, level = 1)), SeatChoice.plan(heat = 3, vent = 0, target = heat(1)))
        assertEquals(listOf(SeatWrite(vent = true, level = 2)), SeatChoice.plan(heat = 0, vent = 0, target = cool(2)))
        assertTrue(SeatChoice.plan(heat = 2, vent = 0, target = heat(2)).isEmpty())
    }

    @Test fun offTurnsOffWhateverIsOn() {
        assertEquals(listOf(SeatWrite(vent = false, level = 0)), SeatChoice.plan(heat = 2, vent = 0, target = off))
        assertEquals(listOf(SeatWrite(vent = true, level = 0)), SeatChoice.plan(heat = 0, vent = 3, target = off))
        assertTrue(SeatChoice.plan(heat = 0, vent = 0, target = off).isEmpty())
    }

    @Test fun levelsAreClampedToOneToThree() {
        assertEquals(listOf(SeatWrite(vent = false, level = 3)), SeatChoice.plan(0, 0, heat(9)))
        assertEquals(listOf(SeatWrite(vent = true, level = 1)), SeatChoice.plan(0, 0, cool(0)))
    }

    @Test fun shownModeFromReadings() {
        assertEquals(SeatShown(true, SeatMode.HEAT, 2), SeatChoice.shown(true, heat = 2, vent = 0))
        assertEquals(SeatShown(true, SeatMode.COOL, 1), SeatChoice.shown(true, heat = 0, vent = 1))
        assertEquals(SeatShown(true, SeatMode.OFF, 0), SeatChoice.shown(true, heat = 0, vent = 0))
        // Soğutma okunamazsa 0 sayılır (sinyal araçta hep [0]); ısıtma okunamazsa durum bilinmez.
        assertEquals(SeatShown(true, SeatMode.OFF, 0), SeatChoice.shown(true, heat = 0, vent = null))
        assertFalse(SeatChoice.shown(true, heat = null, vent = 1).known)
        assertFalse(SeatChoice.shown(false, heat = 2, vent = 0).known)
        // İkisi birden açık okunursa ısıtma gösterilir.
        assertEquals(SeatMode.HEAT, SeatChoice.shown(true, heat = 1, vent = 2).mode)
    }

    @Test fun currentButtonHighlight() {
        val s = SeatShown(true, SeatMode.HEAT, 2)
        assertTrue(SeatChoice.isCurrent(s, heat(2)))
        assertFalse(SeatChoice.isCurrent(s, heat(1)))
        assertFalse(SeatChoice.isCurrent(s, cool(2)))
        assertFalse(SeatChoice.isCurrent(s, off))
        assertTrue(SeatChoice.isCurrent(SeatShown(true, SeatMode.OFF, 0), off))
        assertFalse(SeatChoice.isCurrent(SeatShown(false, SeatMode.OFF, 0), off))
    }

    @Test fun writeEncodingIsLevelPlusOne() {
        // Mevcut kodlama korunur: seviye + 1 yazılır (0 kapalı → 1), okuma 0..3.
        assertEquals(listOf(1, 2, 3, 4), (0..3).map { SeatHeatModel.encode(it) })
        assertEquals(listOf(0, 1, 2, 3), (0..3).map { SeatHeatModel.decode(it) })
    }

    @Test fun mergedSeatArtFollowsMode() {
        assertEquals("seat_heat_on", SeatChoice.artKey(SeatMode.HEAT, 2))
        assertEquals("seat_vent_on", SeatChoice.artKey(SeatMode.COOL, 1))
        assertEquals("seat_off", SeatChoice.artKey(SeatMode.OFF, 0))
        assertEquals("seat_off", SeatChoice.artKey(SeatMode.HEAT, 0))
    }
}

/** Anlık tüketim panelinin kısa geçmişi. */
class ConsumptionHistoryTest {
    private fun v(x: Double) = ConsumptionSmoother.Display(ConsumptionSmoother.Mode.VALUE, x)

    @Test fun keepsOnlyShownValuesWithoutRepeatsAndCaps() {
        val h = ConsumptionHistory()
        h.add(ConsumptionSmoother.Display(ConsumptionSmoother.Mode.IDLE))
        h.add(ConsumptionSmoother.Display(ConsumptionSmoother.Mode.TAKEOFF))
        h.add(v(7.0)); h.add(v(7.0)); h.add(v(8.2))
        h.add(ConsumptionSmoother.Display(ConsumptionSmoother.Mode.CAP, 30.0))
        assertEquals(listOf(7.0f, 8.2f, 30.0f), h.values())
        repeat(ConsumptionHistory.MAX + 5) { h.add(v(5.0 + it * 0.1)) }
        assertEquals(ConsumptionHistory.MAX, h.values().size)
    }
}
