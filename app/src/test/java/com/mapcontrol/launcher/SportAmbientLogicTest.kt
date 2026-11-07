package com.mapcontrol.launcher

import com.desaysv.ivi.extra.project.carinfo.CarSettingID
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spor ambiyansı: hatırla / kırmızı-turuncu yaz / geri yaz kuralları ve yazma kapısı. */
class SportAmbientLogicTest {
    private val idle = SportAmbientLogic.State.IDLE
    private val sport = SportAmbientLogic.SPORT_COLOR
    private fun on(color: Int?, relate: Int? = 2, rhythm: Int? = 2) =
        SportAmbientLogic.Reading(ambientOn = 1, color = color, relateDrive = relate, rhythm = rhythm)

    @Test fun sportStartRemembersAndWritesOemRedOrange() {
        assertEquals(45, sport)
        val d = SportAmbientLogic.decide(idle, wantSport = true, r = on(color = 2))
        assertEquals(SportAmbientLogic.State(saved = 2, applied = true), d.next)
        assertEquals(45, d.write)
        assertTrue(d.settled)
    }

    @Test fun sportEndRestoresRememberedColor() {
        val applied = SportAmbientLogic.State(saved = 2, applied = true)
        val d = SportAmbientLogic.decide(applied, wantSport = false, r = SportAmbientLogic.Reading(color = sport))
        assertEquals(idle, d.next)
        assertEquals(2, d.write)
    }

    @Test fun fullCycleRoundTrips() {
        var s = idle
        var color = 19
        fun step(want: Boolean) {
            val d = SportAmbientLogic.decide(s, want, on(color))
            s = d.next
            d.write?.let { color = it }
        }
        step(true); assertEquals(sport, color)
        step(true); assertEquals(sport, color) // kararlı sporda yeniden yazma yok
        step(false); assertEquals(19, color)
        assertEquals(idle, s)
        step(false); assertEquals(19, color)
    }

    @Test fun nothingWrittenWhenAmbientOffLinkedToDriveModeOrRhythm() {
        val off = SportAmbientLogic.Reading(ambientOn = 2, color = 3, relateDrive = 2, rhythm = 2)
        assertNoop(SportAmbientLogic.decide(idle, true, off))
        assertNoop(SportAmbientLogic.decide(idle, true, SportAmbientLogic.Reading(color = 3)))   // 7 okunamadı
        assertNoop(SportAmbientLogic.decide(idle, true, on(3, relate = 1)))                       // araç sürüş moduna göre seçer
        assertNoop(SportAmbientLogic.decide(idle, true, on(3, rhythm = 1)))                       // müzik ritmi
    }

    @Test fun sportStartWithUnreadableSettingsRetries() {
        val noColor = SportAmbientLogic.decide(idle, true, on(null))
        assertNoop(noColor)
        assertFalse(noColor.settled)
        val noSwitch = SportAmbientLogic.decide(idle, true, SportAmbientLogic.Reading(color = 3))
        assertNoop(noSwitch)
        assertFalse(noSwitch.settled)
        // Bilinçli ret kesin: yeniden denenmez.
        assertTrue(SportAmbientLogic.decide(idle, true, SportAmbientLogic.Reading(ambientOn = 2, color = 3)).settled)
        assertTrue(SportAmbientLogic.decide(idle, true, on(3, relate = 1)).settled)
    }

    @Test fun onlyOemColorIndicesAreRemembered() {
        for (bad in listOf(null, 0, 65, 255, -1)) assertNoop(SportAmbientLogic.decide(idle, true, on(bad)))
        // Zaten kırmızı-turuncu: geri yazılacak bir şey yok.
        assertNoop(SportAmbientLogic.decide(idle, true, on(sport)))
        assertEquals(1, SportAmbientLogic.decide(idle, true, on(1)).next.saved)
        assertEquals(64, SportAmbientLogic.decide(idle, true, on(64)).next.saved)
    }

    @Test fun userColorChangeDuringSportIsKept() {
        val applied = SportAmbientLogic.State(saved = 2, applied = true)
        val d = SportAmbientLogic.decide(applied, false, SportAmbientLogic.Reading(color = 57))
        assertEquals(idle, d.next)
        assertNull(d.write)
    }

    @Test fun unreadableColorDefersRestore() {
        val applied = SportAmbientLogic.State(saved = 2, applied = true)
        val d = SportAmbientLogic.decide(applied, false, SportAmbientLogic.Reading())
        assertFalse(d.settled)
        assertEquals(applied, d.next)
        assertNull(d.write)
    }

    @Test fun pendingWithoutValidSavedClearsWithoutWriting() {
        val d = SportAmbientLogic.decide(SportAmbientLogic.State(saved = null, applied = true), false, SportAmbientLogic.Reading(color = sport))
        assertEquals(idle, d.next)
        assertNull(d.write)
    }

    @Test fun savedColorPrefsCodec() {
        assertEquals(-1, SportAmbientLogic.encodeSaved(null))
        assertEquals(-1, SportAmbientLogic.encodeSaved(70))
        assertEquals(12, SportAmbientLogic.encodeSaved(12))
        assertEquals(12, SportAmbientLogic.decodeSaved(12))
        assertNull(SportAmbientLogic.decodeSaved(-1))
        assertNull(SportAmbientLogic.decodeSaved(0))
    }

    @Test fun writeGateClosedInRealBuildAndWhitelisted() {
        assertFalse(SportAmbient.AMBIENT_WRITE_VERIFIED)
        assertTrue(SportAmbient.enabled)  // yazmalar her zaman açık; beyaz liste yine geçerli
        val cs = VDEventCarInfo.MODULE_CAR_SETTING
        val e = WidgetWritePolicy.find(cs, CarSettingID.ID_CAR_AMBIENT_LIGHT_COLOR, sport)
        assertNotNull(e)
        assertEquals("SportAmbient", e!!.widget)
        assertNotNull(WidgetWritePolicy.find(cs, CarSettingID.ID_CAR_AMBIENT_LIGHT_COLOR, 1))
        assertNull(WidgetWritePolicy.find(cs, CarSettingID.ID_CAR_AMBIENT_LIGHT_COLOR, 0))
        assertNull(WidgetWritePolicy.find(cs, CarSettingID.ID_CAR_AMBIENT_LIGHT_COLOR, 65))
        // Aç/kapa (7) ve sürüş moduna bağlama (55) yazılmaz.
        assertNull(WidgetWritePolicy.find(cs, CarSettingID.ID_CAR_AMBIENT_LIGHT, 1))
        assertNull(WidgetWritePolicy.find(cs, CarSettingID.ID_CAR_AMBIENT_LIGHT_RELATE_DRIVE_MODE, 1))
    }

    private fun assertNoop(d: SportAmbientLogic.Decision) {
        assertEquals(idle, d.next)
        assertNull(d.write)
    }
}
