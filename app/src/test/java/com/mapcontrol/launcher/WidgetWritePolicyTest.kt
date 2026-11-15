package com.mapcontrol.launcher

import com.desaysv.ivi.extra.project.carinfo.CarSettingID
import com.desaysv.ivi.extra.project.carinfo.HvacID
import com.desaysv.ivi.extra.project.carinfo.NewEnergyID
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import com.vnoisy.devfort.ParkGate
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

    private val ne = VDEventCarInfo.MODULE_NEW_ENERGY

    /** Beyaz listede mi (doğrulama ve park kapısından bağımsız). */
    private fun allowed(m: Int, i: Int, v: Int) = WidgetWritePolicy.find(m, i, v) != null

    private val parked = { ParkGate.evaluate(0, 0, ParkGate.GEAR_PARK) }
    private fun moving(kmh: Int) = { ParkGate.evaluate(kmh, kmh, ParkGate.GEAR_PARK) }
    private fun inGear(gear: Int) = { ParkGate.evaluate(0, 0, gear) }

    private val trunkOpen = TrunkMath.command(true)
    private val lockId = CarSettingID.ID_CAR_IVI_LOCK
    private val driveMode = DriveModes.WRITE_VALUES.first()

    private fun refusedForPark(v: WidgetWritePolicy.Verdict) = v is WidgetWritePolicy.Verdict.ParkRefused

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

    @Test fun `hız 0'dan büyükken bagaj komutu reddedilir`() {
        for (kmh in listOf(1, 5, 60)) {
            val v = WidgetWritePolicy.evaluate(trunkOpen.module, trunkOpen.id, trunkOpen.value, moving(kmh))
            assertTrue("hız $kmh", refusedForPark(v))
        }
        val close = TrunkMath.command(false)
        assertTrue(refusedForPark(WidgetWritePolicy.evaluate(close.module, close.id, close.value, moving(3))))
        assertTrue(WidgetWritePolicy.resultText(WidgetWritePolicy.evaluate(trunkOpen.module, trunkOpen.id, trunkOpen.value, moving(5)))
            .startsWith("refused-park"))
    }

    @Test fun `vites P değilken bagaj reddedilir, kilit ve sürüş modu sürüşte de geçer`() {
        for (gear in listOf(0, 2, 3, 4)) {
            assertTrue("kilit vites $gear", WidgetWritePolicy.evaluate(cs, lockId, 2, inGear(gear)) is WidgetWritePolicy.Verdict.Allowed)
            assertTrue("kilit aç vites $gear", WidgetWritePolicy.evaluate(cs, lockId, 1, inGear(gear)) is WidgetWritePolicy.Verdict.Allowed)
            assertTrue("mod vites $gear", WidgetWritePolicy.evaluate(ne, NewEnergyID.ID_DRIVE_MODE, driveMode, inGear(gear)) is WidgetWritePolicy.Verdict.Allowed)
            assertTrue("bagaj vites $gear", refusedForPark(WidgetWritePolicy.evaluate(trunkOpen.module, trunkOpen.id, trunkOpen.value, inGear(gear))))
        }
        // Hareket halinde kilit ve sürüş modu geçer (Berk isteği), bagaj reddedilir.
        assertTrue(WidgetWritePolicy.evaluate(cs, lockId, 2, moving(20)) is WidgetWritePolicy.Verdict.Allowed)
        assertTrue(WidgetWritePolicy.evaluate(ne, NewEnergyID.ID_DRIVE_MODE, driveMode, moving(20)) is WidgetWritePolicy.Verdict.Allowed)
        assertTrue(refusedForPark(WidgetWritePolicy.evaluate(trunkOpen.module, trunkOpen.id, trunkOpen.value, moving(20))))
    }

    @Test fun `hız ya da vites bilinmiyorsa bagaj reddedilir`() {
        val unknowns = listOf(
            { ParkGate.evaluate(null, 0, ParkGate.GEAR_PARK) },
            { ParkGate.evaluate(0, 0, null) },
            { ParkGate.evaluate(null, null, null) },
        )
        for (park in unknowns) {
            assertTrue(refusedForPark(WidgetWritePolicy.evaluate(trunkOpen.module, trunkOpen.id, trunkOpen.value, park)))
            // Kilit ve sürüş modu artık park kapısız: hız/vites okunamasa da geçer.
            assertTrue(WidgetWritePolicy.evaluate(cs, lockId, 2, park) is WidgetWritePolicy.Verdict.Allowed)
            assertTrue(WidgetWritePolicy.evaluate(ne, NewEnergyID.ID_DRIVE_MODE, driveMode, park) is WidgetWritePolicy.Verdict.Allowed)
        }
        // Park kararı verilmezse varsayılan "okunamadı" sayılır.
        assertTrue(refusedForPark(WidgetWritePolicy.evaluate(trunkOpen.module, trunkOpen.id, trunkOpen.value)))
    }

    @Test fun `doğrulanmamış giriş kapıdan geçemez`() {
        assertFalse(WidgetWritePolicy.gate(false, false))
        assertFalse(WidgetWritePolicy.gate(false, true))
        assertFalse(WidgetWritePolicy.gate(true, false))
        val unverified = WidgetWritePolicy.ENTRIES.filter { !it.verified }.map { it.widget }.toSet()
        // ClimatePower (HVAC 18) araç testi için açıldı (ClimateWriter.CLIMATE_POWER_WRITE_VERIFIED, OEM güç düğmesiyle aynı yazma).
        assertEquals(setOf("ClimateTempDriver", "ClimateTempPassenger", "ClimateAc", "ClimateAuto", "ClimateFan",
            "RearSpeaker", "SportAmbient", "SteerHeat"), unverified)
        // Park halinde bile geçmez.
        for (e in WidgetWritePolicy.ENTRIES.filter { !it.verified }) {
            val v = WidgetWritePolicy.evaluate(e.module, e.id, e.values.first(), parked)
            assertTrue(e.widget, v is WidgetWritePolicy.Verdict.NotVerified)
            assertEquals("refused-not-verified", WidgetWritePolicy.resultText(v))
        }
        assertFalse(ComfortWriter.rearSpeakerEnabled)
        assertFalse(SportAmbient.enabled)
        assertFalse(ClimateWriter.enabled(quiet = false))
    }

    @Test fun `doğrulanmış giriş geçer`() {
        assertTrue(WidgetWritePolicy.gate(true, true))
        assertTrue(LauncherEdition.WRITES_ON)
        assertFalse(LauncherEdition.READ_ONLY)
        // Park kapısız doğrulanmış girdiler (koltuk, klima dock'u) sürüşte de geçer; park kararı hiç sorulmaz.
        val noPark: () -> ParkGate.Decision = { throw AssertionError("park kapısı sorulmamalı") }
        for (e in WidgetWritePolicy.ENTRIES.filter { it.verified && !it.parkOnly }) {
            val v = WidgetWritePolicy.evaluate(e.module, e.id, e.values.first(), noPark)
            assertTrue(e.widget, v is WidgetWritePolicy.Verdict.Allowed)
            assertEquals("sent", WidgetWritePolicy.resultText(v))
        }
        // Park kapılı yalnız bagajdır: hız 0 ve vites P iken geçer.
        assertEquals(setOf("Trunk"), WidgetWritePolicy.ENTRIES.filter { it.parkOnly }.map { it.widget }.toSet())
        assertTrue(WidgetWritePolicy.ENTRIES.filter { it.parkOnly }.all { it.verified })
        assertTrue(WidgetWritePolicy.evaluate(trunkOpen.module, trunkOpen.id, trunkOpen.value, parked) is WidgetWritePolicy.Verdict.Allowed)
        assertTrue(WidgetWritePolicy.evaluate(cs, lockId, 2, parked) is WidgetWritePolicy.Verdict.Allowed)
        assertTrue(WidgetWritePolicy.evaluate(ne, NewEnergyID.ID_DRIVE_MODE, driveMode, parked) is WidgetWritePolicy.Verdict.Allowed)
        assertTrue(ComfortWriter.trunkEnabled)
        assertTrue(ClimateWriter.enabled(quiet = true))
        assertTrue(ComfortWriter.canWrite(true, WidgetWritePolicy.gate(true, true), readOnly = false))
        assertFalse(ComfortWriter.canWrite(true, WidgetWritePolicy.gate(true, true), readOnly = true))
        // Beyaz liste dışı yine ayrı nedenle reddedilir.
        assertEquals("refused-not-whitelisted", WidgetWritePolicy.resultText(WidgetWritePolicy.evaluate(cs, 10, 1, parked)))
        assertEquals(listOf("Allowed", "NotVerified", "NotWhitelisted", "ParkRefused"),
            WidgetWritePolicy.Verdict::class.java.declaredClasses.map { it.simpleName }.sorted())
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

    @Test fun rearSpeakerIsWhitelistedButNotVerified() {
        val v = WidgetWritePolicy.evaluate(
            com.desaysv.ivi.vdb.event.id.dsp.VDEventDsp.SOUND_FIELD, SoundFieldMath.WRITE_ID, SoundFieldMath.FRONT_EXTREME,
        )
        assertTrue(v is WidgetWritePolicy.Verdict.NotVerified)
    }
}
