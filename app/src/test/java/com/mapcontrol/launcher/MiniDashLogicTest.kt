package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniDashLogicTest {

    private val okTires = TireReading(listOf(2.4, 2.4, 2.3, 2.2), warning = false)

    // --- Uyarılar ---

    @Test fun calmWhenEverythingFine() {
        assertTrue(MiniAlerts.build(okTires, lowFuel = false, serviceKm = 3_978, serviceDays = 155, pm25In = 4).isEmpty())
    }

    @Test fun unknownValuesAreNotAlerts() {
        assertTrue(MiniAlerts.build(null, null, null, null, null).isEmpty())
    }

    @Test fun lowTireNamesWheelAndBar() {
        val a = MiniAlerts.build(TireReading(listOf(2.4, 2.4, 1.6, 2.2), warning = false), false, null, null, null)
        assertEquals(listOf(MiniAlerts.Kind.TIRE), a.map { it.kind })
        assertEquals(2, a[0].tireLow?.wheel)
        assertEquals(1.6, a[0].tireLow?.bar!!, 1e-9)
        assertEquals(23, TireDecode.psi(a[0].tireLow!!.bar!!))
    }

    @Test fun tireWarningFlagWithoutLowValueStillAlerts() {
        val a = MiniAlerts.build(okTires.copy(warning = true), false, null, null, null)
        assertEquals(MiniAlerts.Kind.TIRE, a.single().kind)
        assertNull(a.single().tireLow)
    }

    @Test fun orderIsImportanceOrder() {
        val a = MiniAlerts.build(TireReading(listOf(1.5, 2.4, 2.4, 2.4)), lowFuel = true, serviceKm = 800, serviceDays = 40, pm25In = 80)
        assertEquals(listOf(MiniAlerts.Kind.TIRE, MiniAlerts.Kind.FUEL_LOW, MiniAlerts.Kind.SERVICE_NEAR, MiniAlerts.Kind.AIR_BAD), a.map { it.kind })
        assertEquals(800, a[2].serviceKm)
        assertEquals(80, a[3].pm25)
    }

    @Test fun healthAndFuelCapComeFirstInImportanceOrder() {
        val a = MiniAlerts.build(
            TireReading(listOf(1.5, 2.4, 2.4, 2.4)), lowFuel = true, serviceKm = 800, serviceDays = 40, pm25In = 80,
            batteryV = 11.4, fuelCapOpen = true,
        )
        assertEquals(
            listOf(
                MiniAlerts.Kind.BATTERY_LOW, MiniAlerts.Kind.TIRE, MiniAlerts.Kind.FUEL_CAP,
                MiniAlerts.Kind.FUEL_LOW, MiniAlerts.Kind.SERVICE_NEAR, MiniAlerts.Kind.AIR_BAD,
            ),
            a.map { it.kind },
        )
        assertEquals(11.4, a[0].batteryV!!, 1e-9)
        val (named, more) = MiniAlerts.visible(a)
        assertEquals(listOf(MiniAlerts.Kind.BATTERY_LOW, MiniAlerts.Kind.TIRE), named.map { it.kind })
        assertEquals(4, more)
    }

    @Test fun healthUsesHealthMathThresholds() {
        assertTrue(MiniAlerts.build(null, null, null, null, null, batteryV = 12.6).isEmpty())
        assertEquals(listOf(MiniAlerts.Kind.BATTERY_HIGH), MiniAlerts.build(null, null, null, null, null, batteryV = 15.4, batteryHighHeld = true).map { it.kind })
        // 30 sn sürmeyen aşırı gerilim şeride çıkmaz; 14,5 V (çalışan motor) hiç çıkmaz.
        assertTrue(MiniAlerts.build(null, null, null, null, null, batteryV = 15.4).isEmpty())
        assertTrue(MiniAlerts.build(null, null, null, null, null, batteryV = 14.5, batteryHighHeld = true).isEmpty())
        assertEquals(listOf(MiniAlerts.Kind.FUEL_CAP), MiniAlerts.build(null, null, null, null, null, fuelCapOpen = true).map { it.kind })
    }

    @Test fun serviceThresholdsMatchSahne() {
        assertTrue(MiniAlerts.build(null, null, StageCorners.SERVICE_NEAR_KM, 200, null).any { it.kind == MiniAlerts.Kind.SERVICE_NEAR })
        assertTrue(MiniAlerts.build(null, null, StageCorners.SERVICE_NEAR_KM + 1, 200, null).isEmpty())
        // Gün yaklaşması Sahne'de bakım notu açmaz (yalnızca km ya da geçmiş).
        assertTrue(MiniAlerts.build(null, null, 5_000, 20, null).isEmpty())
        assertEquals(listOf(MiniAlerts.Kind.SERVICE_OVERDUE), MiniAlerts.build(null, null, 0, 0, null).map { it.kind })
        assertEquals(listOf(MiniAlerts.Kind.SERVICE_OVERDUE), MiniAlerts.build(null, null, 4_000, 0, null).map { it.kind })
    }

    @Test fun airOnlyWhenPoor() {
        assertTrue(MiniAlerts.build(null, null, null, null, StageCorners.AIR_BAD_PM25 - 1).isEmpty())
        assertEquals(MiniAlerts.Kind.AIR_BAD, MiniAlerts.build(null, null, null, null, StageCorners.AIR_BAD_PM25).single().kind)
    }

    @Test fun twoNamedThenPlusN() {
        val all = MiniAlerts.build(TireReading(listOf(1.5, 2.4, 2.4, 2.4)), true, 800, 40, 80)
        val (named, more) = MiniAlerts.visible(all)
        assertEquals(2, named.size)
        assertEquals(MiniAlerts.Kind.TIRE, named[0].kind)
        assertEquals(2, more)
        val (one, none) = MiniAlerts.visible(all.take(1))
        assertEquals(1, one.size)
        assertEquals(0, none)
    }
}
