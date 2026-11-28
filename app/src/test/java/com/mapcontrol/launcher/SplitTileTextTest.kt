package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitTileTextTest {
    @Test fun packageFromComponent() {
        assertEquals("ru.yandex.yandexmaps", SplitTileText.packageOf("ru.yandex.yandexmaps/.MainActivity"))
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

/** "Bölünmüş ekran" kutusu yalnız iki farklı uygulama seçiliyken bölmeyi başlatır; yoksa hızlı ayar sayfası açılır. */
class SplitLoadReadyTest {
    @Test fun readyOnlyWithTwoDifferentApps() {
        assertEquals(false, SplitLoad(SplitPair(null, null), null, null).ready)
        assertEquals(false, SplitLoad(SplitPair("ru.yandex.yandexmaps", null), null, null).ready)
        assertEquals(false, SplitLoad(SplitPair("ru.yandex.yandexmaps", "ru.yandex.yandexmaps"), null, null).ready)
        assertEquals(true, SplitLoad(SplitPair("ru.yandex.yandexmaps", "com.spotify.music"), null, null).ready)
    }
}
