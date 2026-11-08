package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [PhoneCache] saf kodlaması: gidiş-dönüş, fotoğrafsız kayıt, bozuk dosya, cihaz özeti. */
class PhoneCacheTest {

    private val mac = "AA:BB:CC:11:22:33"

    @Test fun roundTripKeepsContactsAndRecents() {
        val h = PhoneCache.deviceHash(mac)
        val contacts = listOf(
            PhoneContact("v:1", "Ayşe Yılmaz", "0555 000 00 01", starred = true, photoKey = "v:1"),
            PhoneContact("v:2", "Zeynep", "+90 555 000 00 02"),
        )
        val recents = listOf(
            PhoneRecent(RecentType.MISSED, "Ayşe Yılmaz", "0555 000 00 01", 20L, contactId = "v:1", photoKey = "v:1"),
            PhoneRecent(RecentType.OUTGOING, null, "0555 000 00 09", 10L),
        )
        val d = PhoneCache.decode(PhoneCache.encode(h, 1234L, contacts, recents))
        assertNotNull(d)
        d!!
        assertEquals(h, d.deviceHash)
        assertEquals(1234L, d.savedAtMs)
        assertEquals(listOf("v:1", "v:2"), d.contacts.map { it.id })
        assertEquals(listOf("Ayşe Yılmaz", "Zeynep"), d.contacts.map { it.name })
        assertEquals("+90 555 000 00 02", d.contacts[1].number)
        assertTrue(d.contacts[0].starred)
        // Fotoğraf anahtarı diske yazılmaz (fotoğraf yalnız bellekte).
        assertTrue(d.contacts.all { it.photoKey == null })
        assertEquals(listOf(RecentType.MISSED, RecentType.OUTGOING), d.recents.map { it.type })
        assertEquals(listOf("Ayşe Yılmaz", null), d.recents.map { it.name })
        assertEquals(listOf(20L, 10L), d.recents.map { it.timeMs })
        assertTrue(d.recents.all { it.photoKey == null && it.contactId == null })
    }

    @Test fun restoredDataFeedsStoreLikeALiveRead() {
        val h = PhoneCache.deviceHash(mac)
        val d = PhoneCache.decode(PhoneCache.encode(h, 0L,
            listOf(PhoneContact("v:1", "Ayşe", "0555 000 00 01")),
            listOf(PhoneRecent(RecentType.INCOMING, null, "05550000001", 5L))))!!
        val s = DirectoryStore()
        assertTrue(s.merge(d.contacts, d.recents))
        // Geçmiş satırı rehberle yeniden eşleşir.
        assertEquals("Ayşe", s.recents.single().name)
        assertEquals("v:1", s.recents.single().contactId)
    }

    @Test fun savedRecentsAreRawNotAnnotated() {
        val s = DirectoryStore()
        s.merge(listOf(PhoneContact("v:1", "Ayşe", "0555 000 00 01")),
            listOf(PhoneRecent(RecentType.INCOMING, null, "05550000001", 5L)))
        assertEquals("Ayşe", s.recents.single().name)
        // Diske telefonun gönderdiği hâl gider: rehberden eşlenen ad geri yüklemede "telefondan gelmiş" sayılmaz.
        val saved = PhoneCache.decode(PhoneCache.encode("h", 0L, s.contacts, s.rawRecentsForSave))!!
        assertNull(saved.recents.single().name)
        // Kişi yeniden adlandırılınca geri yüklenen geçmiş yeni adı alır, eskisi kalmaz.
        val r = DirectoryStore()
        r.merge(saved.contacts, saved.recents)
        r.merge(listOf(PhoneContact("v:1", "Ayşe Y.", "0555 000 00 01")), null)
        assertEquals("Ayşe Y.", r.recents.single().name)
    }

    @Test fun corruptOrForeignDataIsRejected() {
        val good = PhoneCache.encode(PhoneCache.deviceHash(mac), 0L, listOf(PhoneContact("v:1", "A", "1")), emptyList())
        assertNull(PhoneCache.decode(ByteArray(0)))
        assertNull(PhoneCache.decode(good.copyOf(good.size - 3)))
        assertNull(PhoneCache.decode(good + byteArrayOf(0)))
        val badMagic = good.copyOf().also { it[0] = 0 }
        assertNull(PhoneCache.decode(badMagic))
        assertNotNull(PhoneCache.decode(good))
    }

    @Test fun limitsAreApplied() {
        val many = (1..PhoneCache.MAX_CONTACTS + 10).map { PhoneContact("v:$it", "K$it", "$it") }
        val recents = (1..DirectoryStore.MAX_RECENTS + 5).map { PhoneRecent(RecentType.OUTGOING, null, "$it", it.toLong()) }
        val d = PhoneCache.decode(PhoneCache.encode("h", 0L, many, recents))!!
        assertEquals(PhoneCache.MAX_CONTACTS, d.contacts.size)
        assertEquals(DirectoryStore.MAX_RECENTS, d.recents.size)
    }

    @Test fun deviceHashIsStableCaseInsensitiveAndHidesAddress() {
        val a = PhoneCache.deviceHash(mac)
        assertEquals(a, PhoneCache.deviceHash(mac.lowercase()))
        assertEquals(16, a.length)
        assertNotEquals(a, PhoneCache.deviceHash("AA:BB:CC:11:22:34"))
        assertFalse(a.contains("aabbcc", ignoreCase = true))
        assertFalse(a.contains("112233"))
    }

    @Test fun toStringHasNoPersonalData() {
        val d = PhoneCache.decode(PhoneCache.encode("h", 0L, listOf(PhoneContact("v:1", "Ayşe", "0555")), emptyList()))!!
        assertFalse(d.toString().contains("Ayşe"))
        assertFalse(d.toString().contains("0555"))
    }
}
