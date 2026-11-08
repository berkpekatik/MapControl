package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitTileTextTest {
    @Test fun packageFromComponent() {
        assertEquals("ru.yandex.yandexmaps", SplitTileText.packageOf("ru.yandex.yandexmaps/.MainActivity"))
        assertEquals("com.mapcontrol", SplitTileText.packageOf("com.mapcontrol/com.mapcontrol.launcher.MiniDashActivity"))
    }

    @Test fun plainPackageKept() {
        assertEquals("com.spotify.music", SplitTileText.packageOf(" com.spotify.music "))
    }

    @Test fun emptyIsNull() {
        assertNull(SplitTileText.packageOf(null))
        assertNull(SplitTileText.packageOf("  "))
    }

    @Test fun displayPrefersLabelThenPackageNeverComponent() {
        assertEquals("Yandex Haritalar", SplitTileText.display("ru.yandex.yandexmaps/.MainActivity", "Yandex Haritalar"))
        assertEquals("ru.yandex.yandexmaps", SplitTileText.display("ru.yandex.yandexmaps/.MainActivity", null))
        assertEquals("ru.yandex.yandexmaps", SplitTileText.display("ru.yandex.yandexmaps/.MainActivity", " "))
    }
}

class SplitTileTextClassTest {
    @Test fun classOfComponent() {
        assertEquals("ru.yandex.yandexmaps.MainActivity", SplitTileText.classOf("ru.yandex.yandexmaps/.MainActivity"))
        assertEquals("a.b.C", SplitTileText.classOf("x.y/a.b.C"))
        assertNull(SplitTileText.classOf("com.spotify.music"))
        assertNull(SplitTileText.classOf("com.spotify.music/"))
        assertNull(SplitTileText.classOf(null))
    }
}

/** "Bölünmüş ekran" kutusu hangi bölmeyi açar (7 Kasım kararı: tek bölünmüş ekran deneyimi). */
class SplitTileRouteTest {
    private val self = "com.mapcontrol"
    private val mini = "com.mapcontrol/com.mapcontrol.launcher.MiniDashActivity"
    private val miniShort = "com.mapcontrol/.launcher.MiniDashActivity"
    private val picker = "com.mapcontrol/com.mapcontrol.ui.activity.DriveMapPickerActivity"

    @Test fun noOrIncompletePairOpensDriveScreen() {
        assertEquals(SplitRoute.DRIVE, SplitTileText.route(SplitPair(null, null), self))
        assertEquals(SplitRoute.DRIVE, SplitTileText.route(SplitPair("ru.yandex.yandexmaps", null), self))
        assertEquals(SplitRoute.DRIVE, SplitTileText.route(SplitPair("ru.yandex.yandexmaps", "ru.yandex.yandexmaps"), self))
    }

    @Test fun miniDashPairOpensDriveScreenOnEitherSide() {
        assertEquals(SplitRoute.DRIVE, SplitTileText.route(SplitPair(mini, "ru.yandex.yandexmaps"), self))
        assertEquals(SplitRoute.DRIVE, SplitTileText.route(SplitPair("com.google.android.apps.maps", mini), self))
        assertEquals(SplitRoute.DRIVE, SplitTileText.route(SplitPair(miniShort, "com.spotify.music"), self))
        assertEquals(SplitRoute.DRIVE, SplitTileText.route(SplitPair(mini, picker), self))
    }

    @Test fun twoOtherAppsKeepFrostNavSplit() {
        assertEquals(SplitRoute.PAIR, SplitTileText.route(SplitPair("ru.yandex.yandexmaps", "com.spotify.music"), self))
        // Başka paketin aynı adlı sınıfı bizim Mini göstergemiz değildir.
        assertEquals(SplitRoute.PAIR, SplitTileText.route(SplitPair("other.pkg/com.mapcontrol.launcher.MiniDashActivity", "com.spotify.music"), self))
    }

    @Test fun miniDashDetection() {
        assertEquals(true, SplitTileText.isMiniDash(mini, self))
        assertEquals(true, SplitTileText.isMiniDash(miniShort, self))
        assertEquals(false, SplitTileText.isMiniDash(picker, self))
        assertEquals(false, SplitTileText.isMiniDash("com.mapcontrol", self))
    }
}
