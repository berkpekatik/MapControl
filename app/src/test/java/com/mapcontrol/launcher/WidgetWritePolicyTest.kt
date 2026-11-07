package com.mapcontrol.launcher

import com.desaysv.ivi.extra.project.carinfo.CarSettingID
import com.desaysv.ivi.extra.project.carinfo.HvacID
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import com.mapcontrol.vehicle.VehicleQuickControls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetWritePolicyTest {
    private val cs = VDEventCarInfo.MODULE_CAR_SETTING
    private val hv = VDEventCarInfo.MODULE_HVAC
    private val vr = 327696

    private fun allowed(m: Int, i: Int, v: Int) = WidgetWritePolicy.evaluate(m, i, v) is WidgetWritePolicy.Verdict.Allowed

    @Test fun everyValueTheWidgetsCanSendIsWhitelisted() {
        // Koltuk: SeatHeatModel.encode(0..3) (ısıtma ve soğutma aynı kodlama).
        for (level in 0..3) {
            val v = SeatHeatModel.encode(level)
            assertTrue(allowed(cs, CarSettingID.ID_CAR_DRIVER_SEAT_HEAT, v))
            assertTrue(allowed(cs, CarSettingID.ID_CAR_COPILOT_SEAT_HEAT, v))
            assertTrue(allowed(cs, CarSettingID.ID_CAR_DRIVER_SEAT_VENTILATION, v))
            assertTrue(allowed(cs, CarSettingID.ID_CAR_COPILOT_SEAT_VENTILATION, v))
        }
        // Klima: LO, 16,0..30,0 °C 0,5 adım, HI ve fan 1..10 (0 yazılmaz).
        var c = ClimateMath.LO_TEMP
        while (c <= ClimateMath.HI_TEMP) {
            assertTrue("temp $c", allowed(hv, HvacID.ID_AC_TEMPERATURE_DRIVER, ClimateMath.encodeTemp(c)))
            assertTrue("temp $c", allowed(hv, HvacID.ID_AC_TEMPERATURE_COPILOT, ClimateMath.encodeTemp(c)))
            c += 0.5
        }
        for (f in 1..ClimateMath.FAN_MAX) assertTrue(allowed(hv, HvacID.ID_AC_BLOW_SPEED_LEVEL, ClimateMath.encodeFan(f)))
        assertFalse(allowed(hv, HvacID.ID_AC_BLOW_SPEED_LEVEL, 0))   // fan 0 OEM'de doğrulanmadı
        assertFalse(allowed(hv, HvacID.ID_AC_BLOW_SPEED_LEVEL, ClimateMath.FAN_MAX + 1))
        // Bagaj, kilit.
        for (open in listOf(true, false)) {
            val cmd = TrunkMath.command(open)
            assertTrue(allowed(cmd.module, cmd.id, cmd.value))
        }
        for (locked in listOf(true, false)) {
            assertTrue(allowed(cs, CarSettingID.ID_CAR_IVI_LOCK, VehicleQuickControls.centralLockValue(locked)))
        }
    }

    @Test fun lockEncodingIsLockTwoUnlockOne() {
        assertEquals(2, VehicleQuickControls.centralLockValue(true))
        assertEquals(1, VehicleQuickControls.centralLockValue(false))
    }

    @Test fun everythingElseIsRefused() {
        assertNull(WidgetWritePolicy.find(cs, 10, 1))                         // CarSetting 10 (perde) yok sayılıyor, listede yok
        assertNull(WidgetWritePolicy.find(vr, 124, 1))                        // perde widget'ı kaldırıldı
        assertNull(WidgetWritePolicy.find(hv, HvacID.ID_AC_MAX_FRONT_DEFOREST, 1))  // buğu widget'ı kaldırıldı
        assertNull(WidgetWritePolicy.find(hv, HvacID.ID_AC_REAR_DEFROST, 3))
        assertNull(WidgetWritePolicy.find(cs, CarSettingID.ID_CAR_IVI_LOCK, 0))
        assertNull(WidgetWritePolicy.find(hv, HvacID.ID_AC_TEMPERATURE_DRIVER, -1))
        // 30 °C üstü (ham 31..33) OEM'de yok: reddedilir. 0 = LO ve 30 = HI izinli.
        for (raw in 31..34) {
            assertNull("raw $raw", WidgetWritePolicy.find(hv, HvacID.ID_AC_TEMPERATURE_DRIVER, raw))
            assertNull("raw $raw", WidgetWritePolicy.find(hv, HvacID.ID_AC_TEMPERATURE_COPILOT, raw))
        }
        assertNotNull(WidgetWritePolicy.find(hv, HvacID.ID_AC_TEMPERATURE_DRIVER, 0))
        assertNotNull(WidgetWritePolicy.find(hv, HvacID.ID_AC_TEMPERATURE_COPILOT, 30))
        assertNull(WidgetWritePolicy.find(hv, HvacID.ID_AC_BLOW_SPEED_LEVEL, 11))
        assertNull(WidgetWritePolicy.find(cs, CarSettingID.ID_CAR_DRIVER_SEAT_HEAT, 5))
        assertNull(WidgetWritePolicy.find(cs, CarSettingID.ID_CAR_REAR_DOOR_CONTROL, 0))  // bagaj: yalnızca 1 aç / 2 kapat (durdurma yok)
        assertNull(WidgetWritePolicy.find(cs, CarSettingID.ID_CAR_REAR_DOOR_CONTROL, 3))
        assertNull(WidgetWritePolicy.find(0, 0, 0))
        assertEquals(WidgetWritePolicy.Verdict.NotWhitelisted, WidgetWritePolicy.evaluate(cs, 10, 1))
    }

    @Test fun noDrivingLockOnlyTheWhitelist() {
        // Sürüş kilidi yok: karar yalnızca beyaz listeye bakar, park/hız sorulmaz; ret nedeni yalnızca "beyaz liste dışı".
        assertEquals(listOf("Allowed", "NotWhitelisted"),
            WidgetWritePolicy.Verdict::class.java.declaredClasses.map { it.simpleName }.sorted())
        assertTrue(WidgetWritePolicy::class.java.declaredFields.none { it.name.contains("MOVING") })
        val lock = WidgetWritePolicy.evaluate(cs, CarSettingID.ID_CAR_IVI_LOCK, 2)
        assertTrue(lock is WidgetWritePolicy.Verdict.Allowed)
        assertEquals("sent", WidgetWritePolicy.resultText(lock))
        assertEquals("refused-not-whitelisted", WidgetWritePolicy.resultText(WidgetWritePolicy.evaluate(cs, 10, 1)))
    }

    @Test fun gateOpensWithVerifiedOrWritesOn() {
        assertFalse(WidgetWritePolicy.gate(false, false))
        assertTrue(WidgetWritePolicy.gate(true, false))
        assertTrue(WidgetWritePolicy.gate(false, true))
        assertTrue(ComfortWriter.canWrite(true, WidgetWritePolicy.gate(false, true), readOnly = false))
        assertFalse(ComfortWriter.canWrite(true, WidgetWritePolicy.gate(false, true), readOnly = true))
        assertFalse(ClimateWriter.canWrite(WidgetWritePolicy.gate(false, false), readOnly = false))
        // Yazmalar her zaman açık (test anahtarı kaldırıldı): doğrulanmamış özellikler de beyaz listeden geçerek gider.
        assertTrue(LauncherEdition.WRITES_ON)
        assertFalse(LauncherEdition.READ_ONLY)
        assertTrue(ComfortWriter.trunkEnabled)
        assertTrue(ComfortWriter.rearSpeakerEnabled)
        assertTrue(ClimateWriter.enabled)
    }

    @Test fun logLineFormat() {
        assertEquals("widget=Trunk module=327681 id=92 value=1 result=sent", WidgetWritePolicy.logLine("Trunk", cs, 92, 1, "sent"))
        assertEquals("widget=? module=327681 id=10 value=1 result=refused-not-whitelisted",
            WidgetWritePolicy.logLine(null, cs, 10, 1, "refused-not-whitelisted"))
    }

    @Test fun entriesAreUniqueAndHaveReadBack() {
        val keys = WidgetWritePolicy.ENTRIES.map { it.module to it.id }
        assertEquals(keys.size, keys.toSet().size)
        WidgetWritePolicy.ENTRIES.forEach { assertTrue(it.widget, it.readBack.isNotEmpty()); assertNotNull(it.values) }
    }

    @Test fun decodeFanAcceptsUpToTen() {
        assertEquals(10, OurBottomBar.decodeFan(10))
        assertNull(OurBottomBar.decodeFan(11))
    }

    @Test fun rearSpeakerIsAllowed() {
        val v = WidgetWritePolicy.evaluate(
            com.desaysv.ivi.vdb.event.id.dsp.VDEventDsp.SOUND_FIELD, SoundFieldMath.WRITE_ID, SoundFieldMath.FRONT_EXTREME,
        )
        assertTrue(v is WidgetWritePolicy.Verdict.Allowed)
    }
}
