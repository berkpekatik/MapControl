package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimateMathTest {
    private val tempDp = ClimateMath.TEMP_DRAG_DP_PER_STEP

    @Test fun dragDistanceToSteps() {
        assertEquals(0, ClimateMath.stepsForDrag(39f, 1f, tempDp))
        assertEquals(1, ClimateMath.stepsForDrag(40f, 1f, tempDp))
        assertEquals(-2, ClimateMath.stepsForDrag(-85f, 1f, tempDp))
        // 2x yoğunluk: 40dp = 80px
        assertEquals(0, ClimateMath.stepsForDrag(79f, 2f, tempDp))
        assertEquals(3, ClimateMath.stepsForDrag(240f, 2f, tempDp))
    }

    @Test fun tempClampsToRangeAndHalfSteps() {
        // Uçlar LO (15,5) ve HI (30,5); 30,5 üstüne çıkılamaz.
        assertEquals(30.5, ClimateMath.targetTemp(30.0, 5), 0.0)
        assertEquals(30.5, ClimateMath.targetTemp(29.5, 9), 0.0)
        assertEquals(15.5, ClimateMath.targetTemp(16.5, -9), 0.0)
        assertEquals(16.0, ClimateMath.targetTemp(16.5, -1), 0.0)
        assertEquals(22.5, ClimateMath.targetTemp(22.0, 1), 0.0)
        assertEquals(22.5, ClimateMath.clampTemp(22.4), 0.0)
    }

    @Test fun fanTargets() {
        assertEquals(10, ClimateMath.targetFan(6, 6))
        assertEquals(1, ClimateMath.targetFan(2, -5))
        assertEquals(0, ClimateMath.targetFan(0, -2))
        assertEquals(1, ClimateMath.targetFan(0, 1))
        assertEquals(3, ClimateMath.targetFan(3, 0))
    }

    @Test fun rawEncodingInvertsDecode() {
        assertEquals(0, ClimateMath.encodeTemp(10.0))   // LO
        assertEquals(0, ClimateMath.encodeTemp(15.5))
        assertEquals(1, ClimateMath.encodeTemp(16.0))
        assertEquals(21, ClimateMath.encodeTemp(26.0))
        assertEquals(29, ClimateMath.encodeTemp(30.0))
        assertEquals(30, ClimateMath.encodeTemp(30.5))  // HI
        assertEquals(30, ClimateMath.encodeTemp(32.0))  // eski üst sınır (ham 33) artık HI'ye sıkışır
        assertEquals(30, ClimateMath.encodeTemp(40.0))
        for (raw in 0..30) assertEquals(raw, ClimateMath.encodeTemp(OurBottomBar.decodeTemp(raw)!!))
        assertNull(OurBottomBar.decodeTemp(31))
        assertNull(OurBottomBar.decodeTemp(33))
        assertEquals(10, ClimateMath.encodeFan(12))
        assertEquals(1, ClimateMath.encodeFan(0))       // 0 hiç yazılmaz
        assertEquals(1, ClimateMath.encodeFan(-3))
    }

    /**
     * OEM fanı 1'in altına indirince klimayı kapatmaz: panel, dock ve sesli komut 1'de durur; kapatma ayrı güç
     * düğmesidir (HVAC 18 ← 2). Biz de aynısını yaparız: sürükleme 1'de durur, 0 hiçbir fan kimliğine yazılamaz.
     */
    @Test fun fanBelowOneDoesNotTurnClimateOff() {
        assertEquals(ClimateMath.FAN_MIN, ClimateMath.targetFan(1, -1))
        assertEquals(ClimateMath.FAN_MIN, ClimateMath.targetFan(3, -9))
        assertEquals(ClimateMath.FAN_MIN, ClimateMath.encodeFan(0))
        val hv = com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo.MODULE_HVAC
        for (quiet in listOf(true, false)) {
            assertNull(WidgetWritePolicy.find(hv, ClimateWriter.fanId(quiet), 0))
            assertFalse(ClimateWriter.setFan(0, quiet))
        }
    }

    @Test fun loAndHiEnds() {
        assertTrue(ClimateMath.isLo(15.5))
        assertFalse(ClimateMath.isLo(16.0))
        assertTrue(ClimateMath.isHi(30.5))
        assertFalse(ClimateMath.isHi(30.0))
        assertEquals(15.5, OurBottomBar.decodeTemp(ClimateMath.RAW_LO)!!, 0.0)
        assertEquals(30.5, OurBottomBar.decodeTemp(ClimateMath.RAW_HI)!!, 0.0)
        assertEquals(30.0, OurBottomBar.decodeTemp(29)!!, 0.0)
    }

    @Test fun powerIsTurnedOnFirstOnlyWhenKnownOff() {
        assertTrue(ClimateMath.needsPowerOn(2))
        assertTrue(ClimateMath.needsPowerOn(0))
        assertFalse(ClimateMath.needsPowerOn(1))
        assertFalse(ClimateMath.needsPowerOn(null))   // okunamıyorsa 18'e dokunulmaz
        // Dock yolu (76/78/77): OEM dock'u gibi güç hiç gönderilmez (18 ← 1 OEM panelini açıyordu).
        assertFalse(ClimateMath.powerOnBeforeWrite(quiet = true, powerRaw = 2))
        assertFalse(ClimateMath.powerOnBeforeWrite(quiet = true, powerRaw = 0))
        assertTrue(ClimateMath.powerOnBeforeWrite(quiet = false, powerRaw = 2))
        assertFalse(ClimateMath.powerOnBeforeWrite(quiet = false, powerRaw = 1))
        assertFalse(ClimateMath.powerOnBeforeWrite(quiet = false, powerRaw = null))
    }

    @Test fun neverWritesUnlessVerifiedAndNotReadOnly() {
        assertFalse(ClimateWriter.canWrite(verified = false, readOnly = false))
        assertFalse(ClimateWriter.canWrite(verified = false, readOnly = true))
        assertFalse(ClimateWriter.canWrite(verified = true, readOnly = true))
        assertTrue(ClimateWriter.canWrite(verified = true, readOnly = false))
    }

    @Test fun powerMatchesOemPowerButton() {
        // OEM SystemUI HvacModel.onPowerClick: sendItemValue(HVAC, 18, açık ? 2 : 1); durum 18 == 1. 0 hiç yazılmaz.
        assertEquals(1, ClimateMath.powerValue(on = true))
        assertEquals(2, ClimateMath.powerValue(on = false))
        assertEquals(true, ClimateMath.powerOn(1))
        assertEquals(false, ClimateMath.powerOn(2))
        assertEquals(false, ClimateMath.powerOn(0))
        assertEquals(null, ClimateMath.powerOn(null))
        // Araç testi için açık; beyaz listede yalnız 1 ve 2 var.
        assertTrue(ClimateWriter.powerEnabled())
        val hv = com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo.MODULE_HVAC
        val id = com.desaysv.ivi.extra.project.carinfo.HvacID.ID_AC_WORKING
        assertTrue(WidgetWritePolicy.evaluate(hv, id, 1) is WidgetWritePolicy.Verdict.Allowed)
        assertTrue(WidgetWritePolicy.evaluate(hv, id, 2) is WidgetWritePolicy.Verdict.Allowed)
        assertTrue(WidgetWritePolicy.evaluate(hv, id, 0) is WidgetWritePolicy.Verdict.NotWhitelisted)
    }

    @Test fun onlyVerifiedDockPathWritesAndFanZeroNeverSent() {
        // Panel yolu (10/13/12, güç) doğrulanmadı: kapalı. Dock yolu (76/78/77) doğrulandı: açık. Fan 0 ya da negatif hiç gönderilmez.
        assertFalse(ClimateWriter.CLIMATE_WRITE_VERIFIED)
        assertTrue(ClimateWriter.CLIMATE_DOCK_WRITE_VERIFIED)
        assertFalse(ClimateWriter.enabled(quiet = false))
        assertTrue(ClimateWriter.enabled(quiet = true))
        assertFalse(ClimateWriter.setTemperature(ClimateSide.DRIVER, 21.0, quiet = false))
        assertFalse(ClimateWriter.setFan(0, quiet = true))
        assertFalse(ClimateWriter.setFan(-1, quiet = true))
    }
}
