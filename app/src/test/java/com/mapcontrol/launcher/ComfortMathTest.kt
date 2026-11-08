package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComfortMathTest {
    private val VDH = com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo.MODULE_HVAC

    @Test fun gateNeverWritesWhenUnverified() {
        assertTrue(ComfortWriter.COMFORT_WRITE_VERIFIED)
        assertFalse(ComfortWriter.canWrite(false, true, false))
        assertFalse(ComfortWriter.canWrite(true, false, false))
        assertFalse(ComfortWriter.canWrite(true, true, true))
        assertTrue(ComfortWriter.canWrite(true, true, false))
        var sent = 0
        val cmds = listOf(TrunkMath.command(true))
        assertFalse(ComfortWriter.writeAll(ComfortWriter.canWrite(false, true, false), cmds) { sent++ })
        assertEquals(0, sent)
        assertTrue(ComfortWriter.writeAll(true, cmds) { sent++ })
        assertEquals(1, sent)
    }

    @Test fun sunshadeAndDefrostAreGone() {
        // Perde ve buğu widget'ları kaldırıldı: tür yok, beyaz listede yazmaları yok.
        assertNull(WidgetType.fromKey("sunshade"))
        assertNull(WidgetType.fromKey("defrost"))
        assertNull(WidgetWritePolicy.find(327696, 124, 1))
        assertNull(WidgetWritePolicy.find(VDH, 24, 1))
        assertNull(WidgetWritePolicy.find(VDH, 80, 1))
    }

    @Test fun trunkDecoding() {
        fun d(pos: Int?, latch: Int? = null, motion: Int? = 2) = TrunkMath.decode(TrunkReading(pos, latch, motion))
        assertEquals(TrunkState.CLOSED, d(0, 0))
        assertEquals(TrunkState.CLOSED, d(0, null))
        assertEquals(TrunkState.OPEN, d(0, 1)) // mandal açık, konum henüz 0
        assertEquals(TrunkState.PARTIAL, d(40, 1))
        assertEquals(TrunkState.PARTIAL, d(78, 1))
        assertEquals(TrunkState.OPEN, d(79, 1))
        assertEquals(TrunkState.OPEN, d(81, 1))
        assertEquals(TrunkState.OPENING, d(30, 1, 3))
        assertEquals(TrunkState.CLOSING, d(30, 1, 4))
        assertEquals(TrunkState.OPENING, d(null, null, 3))
        // Konum okunamazsa mandal.
        assertEquals(TrunkState.CLOSED, d(null, 0, null))
        assertEquals(TrunkState.OPEN, d(null, 1, null))
        assertEquals(TrunkState.UNKNOWN, d(null, null, null))
        assertEquals(TrunkState.UNKNOWN, TrunkMath.decode(null))
        assertTrue(TrunkState.OPENING.moving && TrunkState.CLOSING.moving && !TrunkState.OPEN.moving && !TrunkState.PARTIAL.moving)
    }

    @Test fun trunkPercent() {
        assertEquals(0, TrunkMath.percent(0))
        assertEquals(100, TrunkMath.percent(81))
        assertEquals(100, TrunkMath.percent(95))
        assertEquals(0, TrunkMath.percent(-3))
        assertEquals(41, TrunkMath.percent(33)) // 33/81 = 40,7
        assertEquals(49, TrunkMath.percent(40)) // 40/81 = 49,4
        assertNull(TrunkMath.percent(null))
        assertEquals(0f, TrunkMath.openFraction(TrunkReading(0, 0, 2)))
        assertEquals(1f, TrunkMath.openFraction(TrunkReading(81, 1, 2)))
        assertEquals(1f, TrunkMath.openFraction(TrunkReading(null, 1, 2)))
        assertEquals(0f, TrunkMath.openFraction(TrunkReading(null, 0, null)))
        assertNull(TrunkMath.openFraction(TrunkReading(null, null, null)))
        assertNull(TrunkMath.openFraction(null))
    }

    @Test fun trunkTapAction() {
        assertEquals(TrunkAction.OPEN, TrunkMath.tapAction(TrunkState.CLOSED, false))
        assertEquals(TrunkAction.CLOSE_CONFIRM, TrunkMath.tapAction(TrunkState.OPEN, false))
        assertEquals(TrunkAction.CLOSE_CONFIRM, TrunkMath.tapAction(TrunkState.PARTIAL, true))
        assertEquals(TrunkAction.STOP_UNKNOWN, TrunkMath.tapAction(TrunkState.OPENING, false))
        assertEquals(TrunkAction.STOP_UNKNOWN, TrunkMath.tapAction(TrunkState.CLOSING, false))
        assertEquals(TrunkAction.STOP, TrunkMath.tapAction(TrunkState.OPENING, true))
        assertEquals(TrunkAction.STOP, TrunkMath.tapAction(TrunkState.CLOSING, true))
        assertEquals(TrunkAction.NONE, TrunkMath.tapAction(TrunkState.UNKNOWN, true))
    }

    @Test fun trunkWriteVerifiedAndRefusalStopsSending() {
        // Doğrulandı (CS 92: 1 aç / 2 kapat); durdurma değeri bilinmiyor. Park kapısı WidgetWriter'da (WidgetWritePolicyTest).
        assertTrue(ComfortWriter.TRUNK_WRITE_VERIFIED)
        assertTrue(ComfortWriter.trunkEnabled)
        assertFalse(ComfortWriter.trunkStopKnown)
        assertEquals(TrunkWriteResult.DISABLED, ComfortWriter.stopTrunk())
        val sent = ArrayList<ComfortCmd>()
        val cmd = listOf(TrunkMath.command(true))
        assertEquals(TrunkWriteResult.DISABLED, ComfortWriter.writeTrunk(false, cmd) { sent += it; true })
        assertTrue(sent.isEmpty())
        // Kapı reddederse (send false) REFUSED.
        assertEquals(TrunkWriteResult.REFUSED, ComfortWriter.writeTrunk(true, cmd) { false })
        assertEquals(TrunkWriteResult.SENT, ComfortWriter.writeTrunk(true, cmd) { sent += it; true })
        assertEquals(cmd, sent)
        assertEquals(listOf("SENT", "DISABLED", "STALE", "TOO_SOON", "REFUSED"), TrunkWriteResult.values().map { it.name })
    }

    @Test fun trunkCommandValues() {
        // Yalnızca CarSetting 92: 1 aç, 2 kapat (OEM). VR 121 yolu ve 3/4 değerleri kaldırıldı.
        assertEquals(ComfortCmd(327681, 92, 1), TrunkMath.command(true))
        assertEquals(ComfortCmd(327681, 92, 2), TrunkMath.command(false))
    }

    @Test fun trunkValueChosenLikeOem() {
        // (CS92 == 2 || PLG == 0) ? 1 : 2
        assertEquals(1, TrunkMath.oemValue(cs92 = 2, plgPosition = 40))   // duruyor ama konum > 0: CS92 == 2 yeter
        assertEquals(1, TrunkMath.oemValue(cs92 = 0, plgPosition = 0))    // kapalı
        assertEquals(1, TrunkMath.oemValue(cs92 = null, plgPosition = 0))
        assertEquals(1, TrunkMath.oemValue(cs92 = 2, plgPosition = null))
        assertEquals(2, TrunkMath.oemValue(cs92 = 1, plgPosition = 81))   // açık
        assertEquals(2, TrunkMath.oemValue(cs92 = 3, plgPosition = 30))   // açılıyor
        assertEquals(2, TrunkMath.oemValue(cs92 = null, plgPosition = 81))
        assertNull(TrunkMath.oemValue(null, null))
    }

    @Test fun trunkOneSecondDebounce() {
        assertFalse(TrunkMath.debounced(5_000, null))
        assertTrue(TrunkMath.debounced(5_999, 5_000))
        assertFalse(TrunkMath.debounced(6_000, 5_000))
    }

    @Test fun trunkNoLongerHasVrOrOldValues() {
        for (open in listOf(true, false)) {
            val c = TrunkMath.command(open)
            assertEquals(327681, c.module)
            assertEquals(92, c.id)
            assertTrue(c.value == 1 || c.value == 2)
        }
        assertNull(WidgetWritePolicy.find(327696, 121, 1))
        assertNull(WidgetWritePolicy.find(327681, 92, 3))
        assertNull(WidgetWritePolicy.find(327681, 92, 4))
        assertEquals("Trunk", WidgetWritePolicy.find(327681, 92, 1)?.widget)
        assertEquals("Trunk", WidgetWritePolicy.find(327681, 92, 2)?.widget)
    }

    @Test fun trunkWidgetRegistered() {
        val t = WidgetType.fromKey("trunk")
        assertEquals(WidgetType.TRUNK, t)
        assertEquals(listOf(Span(2, 2), Span(2, 4)), WidgetType.TRUNK.sizes)
    }
}
