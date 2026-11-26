package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Koltuk hafızası: CS 52 okuması, destek kararı ve gönderim kararı (OEM SVSetting davranışından). */
class SeatMemoryLogicTest {

    @Test fun `52 okuması - etkin konum ikinci değerdir, ilk değer hazır bayrağı`() {
        assertNull(SeatMemoryLogic.activeSlot(null))
        assertNull(SeatMemoryLogic.activeSlot(intArrayOf()))
        assertNull(SeatMemoryLogic.activeSlot(intArrayOf(2))) // tek değer: konum değil
        assertEquals(1, SeatMemoryLogic.activeSlot(intArrayOf(1, 1))) // araçta okunan: [1, 1]
        assertEquals(2, SeatMemoryLogic.activeSlot(intArrayOf(1, 2)))
        assertEquals(3, SeatMemoryLogic.activeSlot(intArrayOf(0, 3)))
        assertNull(SeatMemoryLogic.activeSlot(intArrayOf(1, 0)))
        assertNull(SeatMemoryLogic.activeSlot(intArrayOf(1, 16)))
        assertTrue(SeatMemoryLogic.failed(intArrayOf(1, 16)))
        assertFalse(SeatMemoryLogic.failed(intArrayOf(1, 2)))
        assertFalse(SeatMemoryLogic.failed(null))
    }

    @Test fun `yoklama yalnız kesin varlığı söyler, yokluğu söylemez`() {
        assertEquals(true, SeatMemoryLogic.probeSupport(intArrayOf(1, 1)))
        assertNull(SeatMemoryLogic.probeSupport(intArrayOf(0, 1)))  // hazır değil
        assertNull(SeatMemoryLogic.probeSupport(intArrayOf(1, 0)))
        assertNull(SeatMemoryLogic.probeSupport(intArrayOf(0)))     // CarInfoProxy'nin "yanıt yok" değeri
        assertNull(SeatMemoryLogic.probeSupport(null))
    }

    @Test fun `config 127 kesinse yoklamadan önce gelir`() {
        assertEquals(false, SeatMemoryLogic.support(false, true))
        assertEquals(true, SeatMemoryLogic.support(true, null))
        assertEquals(true, SeatMemoryLogic.support(null, true))
        assertNull(SeatMemoryLogic.support(null, null))
    }

    @Test fun `config 127 configs5 bayt 1 bit 1`() {
        assertNull(CarConfigMath.seatMemory(null))
        assertNull(CarConfigMath.seatMemory(IntArray(4)))
        assertEquals(false, CarConfigMath.seatMemory(IntArray(8)))
        assertEquals(true, CarConfigMath.seatMemory(IntArray(8).also { it[1] = 0x02 }))
        assertEquals(false, CarConfigMath.seatMemory(IntArray(8).also { it[1] = 0xFD }))
        assertEquals(true, CarConfigMath.seatMemory(IntArray(8).also { it[1] = 0xFF }))
    }

    @Test fun `gönderim kararı`() {
        val ready = intArrayOf(1, 1)
        assertEquals(SeatMemoryLogic.Action.SEND, SeatMemoryLogic.decide(2, ready, 10_000, null))
        // Etkin konuma yeniden basmak da gönderir (elle oynanmış koltuğu geri getirir, OEM gibi).
        assertEquals(SeatMemoryLogic.Action.SEND, SeatMemoryLogic.decide(1, ready, 10_000, null))
        assertEquals(SeatMemoryLogic.Action.REFUSE, SeatMemoryLogic.decide(3, ready, 10_000, null))
        assertEquals(SeatMemoryLogic.Action.REFUSE, SeatMemoryLogic.decide(0, ready, 10_000, null))
        assertEquals(SeatMemoryLogic.Action.NOT_READY, SeatMemoryLogic.decide(1, null, 10_000, null))
        assertEquals(SeatMemoryLogic.Action.NOT_READY, SeatMemoryLogic.decide(1, intArrayOf(0, 1), 10_000, null))
        assertEquals(SeatMemoryLogic.Action.NOT_READY, SeatMemoryLogic.decide(1, intArrayOf(0), 10_000, null))
        assertEquals(SeatMemoryLogic.Action.WAIT, SeatMemoryLogic.decide(2, ready, 10_000, 10_000 - SeatMemoryLogic.RESEND_GAP_MS + 1))
        assertEquals(SeatMemoryLogic.Action.SEND, SeatMemoryLogic.decide(2, ready, 10_000, 10_000 - SeatMemoryLogic.RESEND_GAP_MS))
    }

    @Test fun `iki sürücü profili sunulur`() {
        assertEquals(listOf(1, 2), SeatMemoryLogic.SLOTS)
    }
}
