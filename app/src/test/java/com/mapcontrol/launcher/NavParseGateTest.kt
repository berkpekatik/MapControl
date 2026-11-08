package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavParseGateTest {
    private fun counting(value: Int): Pair<() -> Int, IntArray> {
        val calls = IntArray(1)
        return { calls[0]++; value } to calls
    }

    @Test fun firstSightIsParsedWithoutSignature() {
        val gate = NavParseGate()
        val (sig, calls) = counting(7)
        assertTrue(gate.shouldParse("k", 100, sig))
        assertEquals(0, calls[0])
    }

    @Test fun samePostTimeIsSkippedWithoutSignature() {
        val gate = NavParseGate()
        gate.remember("k", 100, 7)
        val (sig, calls) = counting(99)
        assertFalse(gate.shouldParse("k", 100, sig))
        assertEquals(0, calls[0])
    }

    @Test fun repostWithSameContentIsSkipped() {
        // Yandex aynı bildirimi saniyede bir yeniden yayımlar: yeni postTime, aynı içerik.
        val gate = NavParseGate()
        gate.remember("k", 100, 7)
        val (sig, calls) = counting(7)
        assertFalse(gate.shouldParse("k", 1100, sig))
        assertEquals(1, calls[0])
        // postTime güncellendi: aynı gönderinin tekrarı imza hesaplatmaz.
        val (sig2, calls2) = counting(7)
        assertFalse(gate.shouldParse("k", 1100, sig2))
        assertEquals(0, calls2[0])
    }

    @Test fun changedContentIsParsed() {
        val gate = NavParseGate()
        gate.remember("k", 100, 7)
        assertTrue(gate.shouldParse("k", 1100) { 8 })
        gate.remember("k", 1100, 8)
        assertFalse(gate.shouldParse("k", 2100) { 8 })
        // İçerik eski değere dönerse yine ayrıştırılır (yalnız son imza tutulur).
        assertTrue(gate.shouldParse("k", 3100) { 7 })
    }

    @Test fun keysAreIndependent() {
        val gate = NavParseGate()
        gate.remember("a", 100, 7)
        assertTrue(gate.shouldParse("b", 100) { 7 })
        assertFalse(gate.shouldParse("a", 200) { 7 })
    }

    @Test fun forgetAndClearForceReparse() {
        val gate = NavParseGate()
        gate.remember("a", 100, 7)
        gate.remember("b", 100, 7)
        gate.forget("a")
        assertTrue(gate.shouldParse("a", 100) { 7 })
        assertFalse(gate.shouldParse("b", 100) { 7 })
        gate.clear()
        assertTrue(gate.shouldParse("b", 100) { 7 })
        assertEquals(0, gate.size)
    }

    @Test fun oldestKeyIsEvictedAtCapacity() {
        val gate = NavParseGate(maxKeys = 2)
        gate.remember("a", 1, 1)
        gate.remember("b", 1, 1)
        gate.remember("c", 1, 1)
        assertEquals(2, gate.size)
        assertTrue(gate.shouldParse("a", 1) { 1 })
        assertFalse(gate.shouldParse("c", 1) { 1 })
    }
}
