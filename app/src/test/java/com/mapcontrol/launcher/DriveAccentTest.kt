package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveAccentTest {

    private fun lum(c: Int): Double {
        fun ch(v: Int): Double { val x = v / 255.0; return if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch((c shr 16) and 0xFF) + 0.7152 * ch((c shr 8) and 0xFF) + 0.0722 * ch(c and 0xFF)
    }

    private fun contrastOnWhite(c: Int) = 1.05 / (lum(c) + 0.05)

    @Test fun normalAndUnknownKeepDefaultAccent() {
        for (night in listOf(true, false)) {
            assertNull(DriveAccent.accentFor(DriveAccent.NORMAL, night))
            assertNull(DriveAccent.accentFor(9, night))
            // 6 araçta hiç görülmedi: tanımsız, vurgu yok.
            assertNull(DriveAccent.accentFor(6, night))
            assertNull(DriveAccent.accentFor(-1, night))
        }
        assertFalse(DriveAccent.hasBadge(DriveAccent.NORMAL))
    }

    @Test fun everyOtherModeHasDistinctReadableAccent() {
        val modes = listOf(0, 2, 3, 4, 5, 7)
        val seen = HashSet<Int>()
        for (m in modes) {
            val n = DriveAccent.accentFor(m, true)
            val d = DriveAccent.accentFor(m, false)
            assertNotNull(n); assertNotNull(d)
            assertTrue("night $m", contrastOnWhite(n!!) >= 3.0)
            assertTrue("day $m", contrastOnWhite(d!!) >= 4.5)
            assertTrue(seen.add(n)); assertTrue(seen.add(d))
            assertTrue(DriveAccent.hasBadge(m))
        }
    }

    /** "Bitti" düğmesi: dolgu = buttonFill(vurgu), yazı beyaz; her mod ve tema (Normal'in varsayılan mavisi dahil) >= 4,5:1. */
    @Test fun doneButtonHasStrongContrastForEveryAccent() {
        val defaults = mapOf(true to 0xFF3B82F6.toInt(), false to 0xFF2563EB.toInt())
        for (night in listOf(true, false)) {
            for (m in 0..7) {
                val accent = DriveAccent.accentFor(m, night) ?: defaults.getValue(night)
                val fill = DriveAccent.buttonFill(accent)
                assertTrue("mode $m night=$night %06X".format(fill and 0xFFFFFF), DriveAccent.contrastWithWhite(fill) >= 4.5)
                assertEquals("ton aynı kalır", accent ushr 24, fill ushr 24)
            }
            val alert = if (night) 0xFFEF4444.toInt() else 0xFFDC2626.toInt()
            assertTrue("alert night=$night", DriveAccent.contrastWithWhite(DriveAccent.buttonFill(alert)) >= 4.5)
        }
        val already = 0xFF7A4E2D.toInt()
        assertEquals(already, DriveAccent.buttonFill(already))
    }

    @Test fun debounceAppliesOnlyAfterStableWindow() {
        val p = DriveModePolicy(2_000)
        assertNull(p.update(0, 2, true))
        assertNull(p.update(1_999, 2, true))
        assertEquals(2, p.update(2_000, 2, true))
        assertNull(p.update(5_000, 2, true))
    }

    @Test fun flickerResetsTimerAndReturningToAppliedCancels() {
        val p = DriveModePolicy(2_000)
        assertNull(p.update(0, 2, true))
        assertNull(p.update(1_500, 0, true))      // başka mod: sayaç yeniden
        assertNull(p.update(3_000, 0, true))
        assertEquals(0, p.update(3_500, 0, true))
        assertNull(p.update(4_000, 1, true))
        assertNull(p.update(5_000, 0, true))      // uygulanan moda dönüş: iptal
        assertNull(p.update(9_000, 0, true))
    }

    @Test fun deferredWhileBlockedAndNoDataKeepsMode() {
        val p = DriveModePolicy(2_000)
        assertNull(p.update(0, 2, false))
        assertNull(p.update(10_000, 2, false))
        assertEquals(2, p.update(10_500, 2, true))
        assertNull(p.update(20_000, null, true))
    }
}
