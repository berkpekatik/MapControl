package com.mapcontrol.launcher

import com.mapcontrol.launcher.HealthMath.Issue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthMathTest {
    private fun issues(v: Double? = 13.8, fuel: Boolean? = false, km: Int? = 4000, days: Int? = 150, held: Boolean = true) =
        HealthMath.issues(v, fuel, km, days, held)

    @Test fun allNormalHasNoIssues() = assertTrue(issues().isEmpty())

    @Test fun missingValuesAreNotProblems() {
        assertTrue(issues(null, null, null, null).isEmpty())
        assertFalse(HealthMath.hasData(null, null, null, null, null))
        assertTrue(HealthMath.hasData(null, null, null, 10, null))
    }

    @Test fun batteryOutOfRange() {
        assertEquals(listOf(Issue.BATTERY_LOW), issues(v = 11.4))
        assertEquals(listOf(Issue.BATTERY_HIGH), issues(v = 15.6))
        assertTrue(issues(v = 12.0).isEmpty())
        // Motor çalışırken 14,5 V normal (araçta); eşik 15,3 V.
        assertTrue(issues(v = 14.5).isEmpty())
        assertTrue(issues(v = 15.3).isEmpty())
        // Aşırı gerilim 30 sn sürmediyse sorun değil; düşük gerilim beklemez.
        assertTrue(issues(v = 15.6, held = false).isEmpty())
        assertEquals(listOf(Issue.BATTERY_LOW), issues(v = 11.4, held = false))
    }

    @Test fun overVoltageMustPersist30Seconds() {
        val g = OverVoltageGate()
        assertFalse(g.update(15.6, 0L))
        assertFalse(g.update(15.7, 29_999L))
        assertTrue(g.update(15.6, 30_000L))
        assertTrue(g.update(15.4, 60_000L))
        // Eşiğe inen ya da okunamayan değer sayacı sıfırlar.
        assertFalse(g.update(15.3, 61_000L))
        assertFalse(g.update(15.6, 62_000L))
        assertFalse(g.update(null, 70_000L))
        assertFalse(g.update(15.6, 80_000L))
        assertFalse(g.update(15.6, 109_999L))
        assertTrue(g.update(15.6, 110_000L))
        // Normal şarj gerilimi hiç tetiklemez.
        val n = OverVoltageGate()
        assertFalse(n.update(14.5, 0L))
        assertFalse(n.update(14.6, 100_000L))
    }

    @Test fun serviceSoonAndOverdue() {
        assertEquals(listOf(Issue.SERVICE_SOON), issues(km = 900))
        assertEquals(listOf(Issue.SERVICE_SOON), issues(days = 30))
        assertEquals(listOf(Issue.SERVICE_OVERDUE), issues(km = 0))
        assertEquals(listOf(Issue.SERVICE_OVERDUE), issues(days = 0, km = 500))
        assertTrue(issues(km = 1001, days = 31).isEmpty())
    }

    @Test fun mostImportantFirst() {
        assertEquals(
            listOf(Issue.BATTERY_LOW, Issue.LOW_FUEL, Issue.SERVICE_SOON),
            issues(v = 11.0, fuel = true, km = 500),
        )
    }

    @Test fun coolantIsAStatusCodeNotDegrees() {
        // Araçta ReadOnly 100 hep 2 okundu (soğukta ve sürüşten sonra): 2 = normal, diğer kodlar bilinmiyor.
        assertTrue(CoolantStatus.isNormal(2))
        assertFalse(CoolantStatus.isNormal(null))
        assertFalse(CoolantStatus.isNormal(118))
        assertFalse(CoolantStatus.isNormal(0))
        // Kod tek başına da "veri var" sayılır; ama hiçbir kod "Motor sıcak" üretmez (derece sinyali doğrulanmadı).
        assertTrue(HealthMath.hasData(null, 2, null, null, null))
        assertFalse(Issue.ENGINE_HOT in issues())
    }

    @Test fun removedWidgetKeysMapToTheirReplacements() {
        assertEquals(WidgetType.HEALTH, WidgetType.fromKey("battery"))
        assertEquals(WidgetType.HEALTH, WidgetType.fromKey("service"))
        assertEquals(WidgetType.TRIP, WidgetType.fromKey("trip_totals"))
        assertEquals(null, WidgetType.fromKey("nonsense"))
        assertTrue(WidgetType.values().none { it.key in HealthMath.REMOVED_KEYS })
    }

    @Test fun totalsRowsDropMissingSignals() {
        assertTrue(TripTotals.rows(null, null, null).isEmpty())
        assertEquals(listOf(TripTotals.Kind.ODOMETER, TripTotals.Kind.TIME), TripTotals.rows(100, null, 5).map { it.first })
        assertEquals(47, TripTotals.drivingMinutes(47, 3552))
        assertEquals(3552, TripTotals.drivingMinutes(null, 3552))
        // 151 sıfır dönerse sıradaki dolu sinyal seçilir; hepsi sıfırsa satır düşer ("0 dk" gösterilmez).
        assertEquals(3552, TripTotals.drivingMinutes(0, 3552))
        assertEquals(12, TripTotals.drivingMinutes(0, 0, 12, 5))
        assertEquals(5, TripTotals.drivingMinutes(0, 0, null, 5))
        assertNull(TripTotals.drivingMinutes(0, 0, 0, 0))
        assertEquals(1_000L, TripTotals.accumulateMs(0, 1_000, true))
        assertEquals(0L, TripTotals.accumulateMs(0, 1_000, false))
        assertEquals(0L, TripTotals.accumulateMs(0, 60_000, true))
    }
}
