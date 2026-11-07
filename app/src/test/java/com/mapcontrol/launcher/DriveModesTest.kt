package com.mapcontrol.launcher

import com.desaysv.ivi.extra.project.carinfo.CarSettingID
import com.desaysv.ivi.extra.project.carinfo.NewEnergyID
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import com.mapcontrol.vehicle.DriveModeStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sürüş modu widget'ının, direksiyon ısıtmanın ve yapılandırma kararlarının saf kuralları. */
class DriveModesTest {

    @After fun clear() { CarFeatures.steerHeat = null; CarFeatures.config109 = null }

    // --- Okuma / yazma eşlemesi (tek kaynak) ---

    @Test fun readAndWriteValuesAreOneTable() {
        // 7 Kasım araç ölçümü (fiziksel düğme): Eko 0, Normal 1, Spor 2, Kar 3, Çamur 4, Arazi 5, Kum 7; 6 hiç görülmedi.
        val expected = mapOf(0 to DriveMode.ECO, 1 to DriveMode.NORMAL, 2 to DriveMode.SPORT, 3 to DriveMode.SNOW,
            4 to DriveMode.MUD, 5 to DriveMode.OFFROAD, 7 to DriveMode.SAND)
        expected.forEach { (v, mode) ->
            assertEquals(mode, DriveModes.fromRead(v))
            assertEquals(v, mode.value)
        }
        assertNull(DriveModes.fromRead(6))
        assertNull(DriveModes.fromRead(8))
        assertNull(DriveModes.fromRead(-1))
        assertNull(DriveModes.fromRead(null))
        // Okunan mod geri yazılınca aynı değer gider (okuma = yazma).
        DriveMode.values().forEach { assertEquals(it, DriveModes.fromRead(it.value)) }
    }

    @Test fun turkishNamesForShownModes() {
        assertEquals(listOf("Eco", "Normal", "Sport", "Kar", "Çamur", "Arazi", "Kum"),
            listOf(DriveMode.ECO, DriveMode.NORMAL, DriveMode.SPORT, DriveMode.SNOW, DriveMode.MUD, DriveMode.OFFROAD, DriveMode.SAND).map { it.trName })
    }

    @Test fun writeValuesMatchFrostNavDriveModeStyle() {
        assertEquals(DriveModeStyle.SAND, DriveMode.SAND.value)
        assertEquals(DriveModeStyle.OFFROAD, DriveMode.OFFROAD.value)
        assertEquals(setOf(0, 1, 2, 3, 4, 5, 7), DriveModes.WRITE_VALUES)
    }

    @Test fun accentAndDataCheckUseSameTable() {
        assertEquals(DriveModes.SAND, DriveAccent.SAND)
        assertEquals(DriveModes.OFFROAD, DriveAccent.OFFROAD)
        assertEquals(DriveModes.SPORT, DriveAccent.SPORT)
        assertEquals(7, DriveAccent.SAND)
        assertEquals(5, DriveAccent.OFFROAD)
        // Veri denetimi tablosu da aynı kaynaktan.
        assertEquals("Arazi", DataCheckTable.driveMode(5))
        assertEquals("Kum", DataCheckTable.driveMode(7))
        assertEquals("Sport", DataCheckTable.driveMode(2))
    }

    @Test fun unknownSixHasNoLabelInDataCheck() {
        assertNull(DataCheckTable.driveMode(6))
    }

    // --- CarConfig 109 ---

    @Test fun config109ListsMatchOemQuickSetting() {
        assertEquals(DriveModes.TIGGO7_DEFAULT, DriveModes.available(null))
        assertEquals(emptyList<DriveMode>(), DriveModes.available(0))
        assertEquals(listOf(DriveMode.ECO), DriveModes.available(1))
        assertEquals(listOf(DriveMode.SPORT), DriveModes.available(2))
        assertEquals(listOf(DriveMode.ECO, DriveMode.SPORT), DriveModes.available(3))
        assertEquals(listOf(DriveMode.ECO, DriveMode.SPORT), DriveModes.available(7))
        listOf(4, 5, 6).forEach { assertEquals(listOf(DriveMode.ECO, DriveMode.NORMAL, DriveMode.SPORT), DriveModes.available(it)) }
        assertEquals(7, DriveModes.available(10).size)
        assertFalse(DriveMode.MUD in DriveModes.available(11))
        assertFalse(DriveMode.ECO in DriveModes.available(8))
        assertEquals(emptyList<DriveMode>(), DriveModes.available(12))
    }

    @Test fun config109IsFifthByteLowBitsOfConfigs4() {
        assertNull(DriveModes.config109(null))
        assertNull(DriveModes.config109(IntArray(7)))
        assertEquals(5, DriveModes.config109(intArrayOf(0, 0, 0, 0, 0, 0xE5, 0, 0)))
    }

    // --- Dokunuşla sıradaki mod ---

    @Test fun tapCyclesThroughAvailableModes() {
        val t7 = DriveModes.TIGGO7_DEFAULT
        assertEquals(DriveMode.NORMAL, DriveModes.next(DriveMode.ECO, t7))
        assertEquals(DriveMode.SPORT, DriveModes.next(DriveMode.NORMAL, t7))
        assertEquals(DriveMode.ECO, DriveModes.next(DriveMode.SPORT, t7))
        // Listede olmayan moddan (ör. Kar) ilkine.
        assertEquals(DriveMode.ECO, DriveModes.next(DriveMode.SNOW, t7))
        // Mod bilinmiyorsa ya da seçici yoksa gönderilmez; tek seçenekte zaten o moddaysa da.
        assertNull(DriveModes.next(null, t7))
        assertNull(DriveModes.next(DriveMode.ECO, emptyList()))
        assertNull(DriveModes.next(DriveMode.ECO, listOf(DriveMode.ECO)))
        assertEquals(DriveMode.ECO, DriveModes.next(DriveMode.SPORT, listOf(DriveMode.ECO)))
    }

    // --- Beyaz liste ---

    @Test fun whitelistHasDriveModeAndSteerToggleOnly() {
        val ne = VDEventCarInfo.MODULE_NEW_ENERGY
        listOf(0, 1, 2, 3, 4, 5, 7).forEach { assertEquals("DriveMode", WidgetWritePolicy.find(ne, NewEnergyID.ID_DRIVE_MODE, it)?.widget) }
        assertNull(WidgetWritePolicy.find(ne, NewEnergyID.ID_DRIVE_MODE, 6))
        val cs = VDEventCarInfo.MODULE_CAR_SETTING
        assertEquals("SteerHeat", WidgetWritePolicy.find(cs, CarSettingID.ID_CAR_STEER_WHEEL_HEAT, 1)?.widget)
        assertNull(WidgetWritePolicy.find(cs, CarSettingID.ID_CAR_STEER_WHEEL_HEAT, 0))
        assertNull(WidgetWritePolicy.find(cs, CarSettingID.ID_CAR_STEER_WHEEL_HEAT, 2))
    }

    @Test fun connectRetriesOnceThenRefuses() {
        assertEquals(WidgetWritePolicy.ConnectStep.SEND, WidgetWritePolicy.connectStep(connected = true, retried = false))
        assertEquals(WidgetWritePolicy.ConnectStep.CONNECT_AND_RETRY, WidgetWritePolicy.connectStep(connected = false, retried = false))
        assertEquals(WidgetWritePolicy.ConnectStep.SEND, WidgetWritePolicy.connectStep(connected = true, retried = true))
        assertEquals(WidgetWritePolicy.ConnectStep.REFUSE, WidgetWritePolicy.connectStep(connected = false, retried = true))
    }

    // --- Direksiyon ısıtma ---

    @Test fun steerHeatComesFromConfig120AndRegion() {
        assertNull(CarConfigMath.steerHeat(null))
        assertNull(CarConfigMath.steerHeat(IntArray(4)))
        assertEquals(false, CarConfigMath.steerHeat(IntArray(8)))
        val withBit = intArrayOf(0x02, 0, 0, 0, 0, 0, 39, 0)
        assertEquals(true, CarConfigMath.steerHeat(withBit))
        listOf(5, 7, 32).forEach { region ->
            assertEquals(false, CarConfigMath.steerHeat(withBit.copyOf().also { it[6] = region }))
        }
        // Yalnız bit 1: bit 0 tek başına ısıtma değildir.
        assertEquals(false, CarConfigMath.steerHeat(intArrayOf(0x01, 0, 0, 0, 0, 0, 0, 0)))
    }

    @Test fun steerToggleSendsOnlyWhenDifferentAndNotTwiceInTheGap() {
        assertEquals(SteerHeatLogic.Action.SEND, SteerHeatLogic.decide(0, wantOn = true, nowMs = 10_000, lastSentMs = null))
        assertEquals(SteerHeatLogic.Action.SEND, SteerHeatLogic.decide(1, wantOn = false, nowMs = 10_000, lastSentMs = null))
        assertEquals(SteerHeatLogic.Action.SKIP, SteerHeatLogic.decide(1, wantOn = true, nowMs = 10_000, lastSentMs = null))
        assertEquals(SteerHeatLogic.Action.SKIP, SteerHeatLogic.decide(2, wantOn = false, nowMs = 10_000, lastSentMs = null))
        assertEquals(SteerHeatLogic.Action.REFUSE, SteerHeatLogic.decide(null, wantOn = true, nowMs = 10_000, lastSentMs = null))
        // Çift dokunuş: ikinci dokunuş gönderim boşluğunda yok sayılır (anahtar iki kez çevrilmez).
        assertEquals(SteerHeatLogic.Action.WAIT, SteerHeatLogic.decide(0, wantOn = false, nowMs = 10_400, lastSentMs = 10_000))
        assertEquals(SteerHeatLogic.Action.SEND, SteerHeatLogic.decide(1, wantOn = false, nowMs = 13_000, lastSentMs = 10_000))
        assertTrue(SteerHeatLogic.isOn(1))
        assertFalse(SteerHeatLogic.isOn(0))
    }
}
