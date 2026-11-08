package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** VDBus rehber parçaları: sunucu 10'arlık parçalar yayar (index = parçanın son sırası, total = liste boyu). */
class PhoneVdListTest {

    /** Sunucunun (araçtaki BT servisi) parçalama kuralı: test girdisi üretmek için. */
    private fun serverChunks(n: Int): List<Triple<Int, Int, List<Int>>> {
        if (n == 0) return listOf(Triple(0, 0, emptyList()))
        val list = (0 until n).toList()
        val out = ArrayList<Triple<Int, Int, List<Int>>>()
        var from = 0
        repeat((n - 1) / 10 + 1) {
            val part = list.drop(from).take(10)
            val end = from + part.size
            out.add(Triple(end - 1, n, part))
            from = end
        }
        return out
    }

    @Test fun collectsAllChunksAndFinishesOnLast() {
        val c = VdListCollector<Int>("ml-1")
        val chunks = serverChunks(23)
        assertEquals(3, chunks.size)
        chunks.forEachIndexed { i, (index, total, items) ->
            assertEquals(VdListCollector.Offer.ACCEPTED, c.offer("ml-1", index, total, items))
            assertEquals(i == chunks.lastIndex, c.finished)
        }
        assertEquals((0 until 23).toList(), c.result())
    }

    @Test fun exactMultipleOfTen() {
        val c = VdListCollector<Int>("k")
        for ((index, total, items) in serverChunks(20)) c.offer("k", index, total, items)
        assertTrue(c.finished)
        assertEquals(20, c.size())
    }

    @Test fun emptyListIsFinishedEmptyNotNull() {
        val c = VdListCollector<Int>("k")
        val (index, total, items) = serverChunks(0).single()
        c.offer("k", index, total, items)
        assertTrue(c.finished)
        assertEquals(emptyList<Int>(), c.result())
    }

    @Test fun noChunkMeansNull() {
        val c = VdListCollector<Int>("k")
        assertNull(c.result())
        assertFalse(c.any)
    }

    @Test fun otherKeysIgnored() {
        val c = VdListCollector<Int>("mine")
        assertEquals(VdListCollector.Offer.OTHER_KEY, c.offer("theirs", 0, 1, listOf(1)))
        assertEquals(VdListCollector.Offer.OTHER_KEY, c.offer(null, 0, 1, listOf(1)))
        assertNull(c.result())
    }

    @Test fun nullKeyMatchesDbPublish() {
        // Veritabanı yolu key=null yayar.
        val c = VdListCollector<Int>(null)
        assertEquals(VdListCollector.Offer.ACCEPTED, c.offer(null, 1, 2, listOf(1, 2)))
        assertEquals(listOf(1, 2), c.result())
    }

    @Test fun secondPublishAfterFinishIgnored() {
        // SP yükleme hem adressiz hem adresli olay atar: aynı liste iki kez yayımlanır.
        val c = VdListCollector<Int>(null)
        for ((index, total, items) in serverChunks(12)) c.offer(null, index, total, items)
        for ((index, total, items) in serverChunks(12)) {
            assertEquals(VdListCollector.Offer.AFTER_FINISH, c.offer(null, index, total, items))
        }
        assertEquals(12, c.size())
    }

    @Test fun negativeTotalIsDataError() {
        val c = VdListCollector<Int>("k")
        c.offer("k", 0, -1, emptyList())
        assertTrue(c.failed)
        assertNull(c.result())
    }

    @Test fun partialWithoutFinishStillReturnsWhatArrived() {
        val c = VdListCollector<Int>("k")
        val first = serverChunks(25).first()
        c.offer("k", first.first, first.second, first.third)
        assertFalse(c.finished)
        assertEquals(10, c.result()!!.size)
    }

    @Test fun summaryHasCountsOnly() {
        val c = VdListCollector<String>("k")
        c.offer("k", 0, 1, listOf("Ayşe 0555 000 00 01"))
        val s = c.summary()
        assertFalse(s.contains("Ayşe"))
        assertFalse(s.contains("0555"))
        assertTrue(s.contains("kayıt=1"))
    }

    @Test fun waitIdleResetsPerChunkAndHardCaps() {
        val start = 1_000L
        assertEquals(VdListWait.IDLE_MS, VdListWait.remaining(start, start, start))
        // Parça geldikçe sessizlik süresi yeniden başlar.
        assertEquals(VdListWait.IDLE_MS - 500, VdListWait.remaining(start + 2_500, start + 2_000, start))
        // Sessizlik doldu.
        assertTrue(VdListWait.remaining(start + 3_000, start, start) <= 0)
        // Üst sınır: sürekli parça gelse de biter.
        val late = start + VdListWait.HARD_MS
        assertTrue(VdListWait.remaining(late, late - 10, start) <= 0)
    }
}
