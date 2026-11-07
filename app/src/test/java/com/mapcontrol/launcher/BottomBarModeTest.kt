package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarModeTest {
    @Test fun parsesKeysCaseInsensitively() {
        assertEquals(BottomBarMode.ORIGINAL, BottomBarMode.parse("original"))
        assertEquals(BottomBarMode.OURS, BottomBarMode.parse("OURS"))
        assertEquals(BottomBarMode.HIDDEN, BottomBarMode.parse(" hidden "))
    }

    @Test fun unknownOrEmptyParsesToNull() {
        assertNull(BottomBarMode.parse(null))
        assertNull(BottomBarMode.parse(""))
        assertNull(BottomBarMode.parse("fullscreen"))
    }

    @Test fun legacyFullscreenMigration() {
        assertEquals(BottomBarMode.HIDDEN, BottomBarMode.fromLegacyFullscreen(true))
        assertEquals(BottomBarMode.ORIGINAL, BottomBarMode.fromLegacyFullscreen(false))
    }

    @Test fun resolvePrefersNewKeyThenLegacyThenDefault() {
        assertEquals(BottomBarMode.OURS, BottomBarMode.resolve("ours", true))
        assertEquals(BottomBarMode.HIDDEN, BottomBarMode.resolve(null, true))
        assertEquals(BottomBarMode.ORIGINAL, BottomBarMode.resolve(null, false))
        assertEquals(BottomBarMode.ORIGINAL, BottomBarMode.resolve(null, null))
        // Bozuk kayıt: eski tercihe düşer.
        assertEquals(BottomBarMode.HIDDEN, BottomBarMode.resolve("???", true))
    }

    @Test fun onlyOriginalKeepsOemBar() {
        assertFalse(BottomBarMode.ORIGINAL.hidesOemBar)
        assertTrue(BottomBarMode.OURS.hidesOemBar)
        assertTrue(BottomBarMode.HIDDEN.hidesOemBar)
    }

    @Test fun drawsOurBarUnlessOemBarOrHidden() {
        assertTrue(BottomBarMode.drawsOurBar(BottomBarMode.OURS, oemBarPresent = true))
        assertTrue(BottomBarMode.drawsOurBar(BottomBarMode.ORIGINAL, oemBarPresent = false))
        assertFalse(BottomBarMode.drawsOurBar(BottomBarMode.ORIGINAL, oemBarPresent = true))
        assertFalse(BottomBarMode.drawsOurBar(BottomBarMode.HIDDEN, oemBarPresent = false))
    }

    @Test fun climateDecode() {
        assertEquals(26.0, OurBottomBar.decodeTemp(21)!!, 0.0)
        assertEquals(15.5, OurBottomBar.decodeTemp(0)!!, 0.0)
        assertNull(OurBottomBar.decodeTemp(-1))
        assertNull(OurBottomBar.decodeTemp(255))
        assertNull(OurBottomBar.decodeTemp(31))   // 30 °C üstü yok (OEM: ham 30 = HI)
        assertEquals(30.5, OurBottomBar.decodeTemp(30)!!, 0.0)
        assertEquals(3, OurBottomBar.decodeFan(3))
        assertNull(OurBottomBar.decodeFan(200))
    }

    @Test fun oemBarForcedOnlyWhenHiddenModeAndSystemShowsIt() {
        // Bölünmüş ekran: Bizimki/Gizli'de gizlediğimiz OEM çubuğu sistemce görünür (110 px) → bizimki çizilmez.
        assertTrue(BottomBarMode.oemBarForced(BottomBarMode.OURS, navBarVisible = true, navBarBottomPx = 110))
        assertTrue(BottomBarMode.oemBarForced(BottomBarMode.HIDDEN, navBarVisible = true, navBarBottomPx = 110))
        // Normal: gizli kaldı.
        assertFalse(BottomBarMode.oemBarForced(BottomBarMode.OURS, navBarVisible = false, navBarBottomPx = 110))
        // Görünür ama yer kaplamıyor.
        assertFalse(BottomBarMode.oemBarForced(BottomBarMode.OURS, navBarVisible = true, navBarBottomPx = 0))
        // Orijinal'de OEM çubuğu zaten görünür; zorlama sayılmaz (emülatörde bizim çubuk kalır).
        assertFalse(BottomBarMode.oemBarForced(BottomBarMode.ORIGINAL, navBarVisible = true, navBarBottomPx = 110))
    }
}
