package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StripAlertsTest {

    private val okTires = TireReading(listOf(2.4, 2.4, 2.3, 2.2), warning = false)

    // --- Uyarılar ---

    @Test fun calmWhenEverythingFine() {
        assertTrue(StripAlerts.build(okTires, lowFuel = false, serviceKm = 3_978, serviceDays = 155, pm25In = 4).isEmpty())
    }

    @Test fun unknownValuesAreNotAlerts() {
        assertTrue(StripAlerts.build(null, null, null, null, null).isEmpty())
    }

    @Test fun lowTireNamesWheelAndBar() {
        val a = StripAlerts.build(TireReading(listOf(2.4, 2.4, 1.6, 2.2), warning = false), false, null, null, null)
        assertEquals(listOf(StripAlerts.Kind.TIRE), a.map { it.kind })
        assertEquals(2, a[0].tireLow?.wheel)
        assertEquals(1.6, a[0].tireLow?.bar!!, 1e-9)
        assertEquals(23, TireDecode.psi(a[0].tireLow!!.bar!!))
    }

    @Test fun tireWarningFlagWithoutLowValueStillAlerts() {
        val a = StripAlerts.build(okTires.copy(warning = true), false, null, null, null)
        assertEquals(StripAlerts.Kind.TIRE, a.single().kind)
        assertNull(a.single().tireLow)
    }

    @Test fun orderIsImportanceOrder() {
        val a = StripAlerts.build(TireReading(listOf(1.5, 2.4, 2.4, 2.4)), lowFuel = true, serviceKm = 800, serviceDays = 40, pm25In = 80)
        assertEquals(listOf(StripAlerts.Kind.TIRE, StripAlerts.Kind.FUEL_LOW, StripAlerts.Kind.SERVICE_NEAR, StripAlerts.Kind.AIR_BAD), a.map { it.kind })
        assertEquals(800, a[2].serviceKm)
        assertEquals(80, a[3].pm25)
    }

    @Test fun healthAndFuelCapComeFirstInImportanceOrder() {
        val a = StripAlerts.build(
            TireReading(listOf(1.5, 2.4, 2.4, 2.4)), lowFuel = true, serviceKm = 800, serviceDays = 40, pm25In = 80,
            batteryV = 11.4, fuelCapOpen = true,
        )
        assertEquals(
            listOf(
                StripAlerts.Kind.BATTERY_LOW, StripAlerts.Kind.TIRE, StripAlerts.Kind.FUEL_CAP,
                StripAlerts.Kind.FUEL_LOW, StripAlerts.Kind.SERVICE_NEAR, StripAlerts.Kind.AIR_BAD,
            ),
            a.map { it.kind },
        )
        assertEquals(11.4, a[0].batteryV!!, 1e-9)
        val (named, more) = StripAlerts.visible(a)
        assertEquals(listOf(StripAlerts.Kind.BATTERY_LOW, StripAlerts.Kind.TIRE), named.map { it.kind })
        assertEquals(4, more)
    }

    @Test fun healthUsesHealthMathThresholds() {
        assertTrue(StripAlerts.build(null, null, null, null, null, batteryV = 12.6).isEmpty())
        assertEquals(listOf(StripAlerts.Kind.BATTERY_HIGH), StripAlerts.build(null, null, null, null, null, batteryV = 15.4, batteryHighHeld = true).map { it.kind })
        // 30 sn sürmeyen aşırı gerilim şeride çıkmaz; 14,5 V (çalışan motor) hiç çıkmaz.
        assertTrue(StripAlerts.build(null, null, null, null, null, batteryV = 15.4).isEmpty())
        assertTrue(StripAlerts.build(null, null, null, null, null, batteryV = 14.5, batteryHighHeld = true).isEmpty())
        assertEquals(listOf(StripAlerts.Kind.FUEL_CAP), StripAlerts.build(null, null, null, null, null, fuelCapOpen = true).map { it.kind })
    }

    @Test fun serviceThresholdsMatchSahne() {
        assertTrue(StripAlerts.build(null, null, StageCorners.SERVICE_NEAR_KM, 200, null).any { it.kind == StripAlerts.Kind.SERVICE_NEAR })
        assertTrue(StripAlerts.build(null, null, StageCorners.SERVICE_NEAR_KM + 1, 200, null).isEmpty())
        // Gün yaklaşması Sahne'de bakım notu açmaz (yalnızca km ya da geçmiş).
        assertTrue(StripAlerts.build(null, null, 5_000, 20, null).isEmpty())
        assertEquals(listOf(StripAlerts.Kind.SERVICE_OVERDUE), StripAlerts.build(null, null, 0, 0, null).map { it.kind })
        assertEquals(listOf(StripAlerts.Kind.SERVICE_OVERDUE), StripAlerts.build(null, null, 4_000, 0, null).map { it.kind })
    }

    @Test fun airOnlyWhenPoor() {
        assertTrue(StripAlerts.build(null, null, null, null, StageCorners.AIR_BAD_PM25 - 1).isEmpty())
        assertEquals(StripAlerts.Kind.AIR_BAD, StripAlerts.build(null, null, null, null, StageCorners.AIR_BAD_PM25).single().kind)
    }

    @Test fun twoNamedThenPlusN() {
        val all = StripAlerts.build(TireReading(listOf(1.5, 2.4, 2.4, 2.4)), true, 800, 40, 80)
        val (named, more) = StripAlerts.visible(all)
        assertEquals(2, named.size)
        assertEquals(StripAlerts.Kind.TIRE, named[0].kind)
        assertEquals(2, more)
        val (one, none) = StripAlerts.visible(all.take(1))
        assertEquals(1, one.size)
        assertEquals(0, none)
    }

}
