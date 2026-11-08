package com.mapcontrol.launcher

import com.mapcontrol.launcher.PhotoFiles.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoFilesTest {

    @Test
    fun acceptsImageExtensionsCaseInsensitively() {
        listOf("a.jpg", "a.JPEG", "a.Png", "a.webp", "my.photo.WEBP").forEach { assertTrue(it, PhotoFiles.isPhotoName(it)) }
    }

    @Test
    fun rejectsOtherFilesAndHidden() {
        listOf("a.gif", "a.mp3", "a", "a.", ".hidden.jpg", "jpg", "a.jpg.tmp").forEach { assertFalse(it, PhotoFiles.isPhotoName(it)) }
    }

    @Test
    fun sortsNewestFirstThenByName() {
        val out = PhotoFiles.filterAndSort(listOf(
            Entry("/d/b.jpg", "b.jpg", 100), Entry("/d/x.mp3", "x.mp3", 999),
            Entry("/d/c.png", "c.png", 300), Entry("/d/a.jpg", "a.jpg", 100),
        ))
        assertEquals(listOf("c.png", "a.jpg", "b.jpg"), out.map { it.name })
    }

    @Test
    fun limitsListLength() {
        val many = (1..100).map { Entry("/d/$it.jpg", "$it.jpg", it.toLong()) }
        val out = PhotoFiles.filterAndSort(many, limit = 5)
        assertEquals(5, out.size)
        assertEquals("100.jpg", out.first().name)
    }

    @Test
    fun storedNameIsPerProfileAndSafe() {
        assertEquals("photo_p1_photo", PhotoFiles.storedName(1, "photo"))
        assertEquals("photo_p2_a_b_c", PhotoFiles.storedName(2, "a/b.c"))
    }

    @Test
    fun sampleSizeKeepsAtLeastRequestedSize() {
        assertEquals(1, PhotoFiles.sampleSize(800, 600, 1024, 640))
        assertEquals(4, PhotoFiles.sampleSize(8000, 6000, 1024, 640))
        assertEquals(1, PhotoFiles.sampleSize(0, 0, 100, 100))
        // 4000x3000 -> /4 = 1000x750: genişlik 1024'ün altında olduğundan en fazla 2 olur.
        assertEquals(2, PhotoFiles.sampleSize(4000, 3000, 1024, 640))
    }
}
