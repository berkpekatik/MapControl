package com.mapcontrol.launcher

import com.mapcontrol.launcher.FavoriteKeyPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteKeyPolicyTest {

    private val yandex = FavoriteAction.YANDEX_NAVI

    @Test fun shortPressLaunchesOnRelease() {
        val p = FavoriteKeyPolicy()
        assertEquals(Decision.IGNORE, p.onKey(19, 0, yandex, 1_000))
        assertEquals(Decision.LAUNCH, p.onKey(19, 3, yandex, 1_100))
    }

    @Test fun longPressNeverLaunches() {
        val p = FavoriteKeyPolicy()
        assertEquals(Decision.IGNORE, p.onKey(19, 0, yandex, 1_000))
        assertEquals(Decision.LONG_PRESS, p.onKey(19, 1, yandex, 2_200))
        assertEquals(Decision.IGNORE, p.onKey(19, 4, yandex, 2_500))
    }

    @Test fun otherKeyTypesIgnored() {
        val p = FavoriteKeyPolicy()
        assertEquals(Decision.IGNORE, p.onKey(26, 3, yandex, 1_000))
        assertEquals(Decision.IGNORE, p.onKey(7, 3, yandex, 1_000))
        assertEquals(Decision.IGNORE, p.onKey(26, 1, yandex, 1_000))
    }

    @Test fun offSettingIgnoresShortPress() {
        val p = FavoriteKeyPolicy()
        assertEquals(Decision.IGNORE, p.onKey(19, 3, FavoriteAction.OFF, 1_000))
        // Kapalıyken yok sayılan basış debounce saatini başlatmaz.
        assertEquals(Decision.LAUNCH, p.onKey(19, 3, yandex, 1_100))
    }

    @Test fun doubleEventWithinDebounceIgnored() {
        val p = FavoriteKeyPolicy(800)
        assertEquals(Decision.LAUNCH, p.onKey(19, 3, yandex, 10_000))
        assertEquals(Decision.IGNORE, p.onKey(19, 3, yandex, 10_000))
        assertEquals(Decision.IGNORE, p.onKey(19, 3, yandex, 10_799))
        assertEquals(Decision.LAUNCH, p.onKey(19, 3, yandex, 10_800))
    }

    @Test fun clockGoingBackwardsDoesNotBlock() {
        val p = FavoriteKeyPolicy(800)
        assertEquals(Decision.LAUNCH, p.onKey(19, 3, yandex, 10_000))
        assertEquals(Decision.LAUNCH, p.onKey(19, 3, yandex, 5_000))
    }

    @Test fun longPressDoesNotArmDebounce() {
        val p = FavoriteKeyPolicy(800)
        assertEquals(Decision.IGNORE, p.onKey(19, 0, yandex, 10_000))
        assertEquals(Decision.LONG_PRESS, p.onKey(19, 1, yandex, 11_200))
        assertEquals(Decision.IGNORE, p.onKey(19, 4, yandex, 11_300))
        // Uzun basıştan hemen sonra gelen kısa basış yine açar.
        assertEquals(Decision.IGNORE, p.onKey(19, 0, yandex, 11_350))
        assertEquals(Decision.LAUNCH, p.onKey(19, 3, yandex, 11_400))
    }

    @Test fun offStaysIgnoredAcrossRepeatedPresses() {
        val p = FavoriteKeyPolicy(800)
        assertEquals(Decision.IGNORE, p.onKey(19, 3, FavoriteAction.OFF, 0))
        assertEquals(Decision.IGNORE, p.onKey(19, 3, FavoriteAction.OFF, 900))
        assertEquals(Decision.IGNORE, p.onKey(19, 3, FavoriteAction.OFF, 1_800))
    }

    @Test fun parseKnownKeysAndDefaultOff() {
        assertEquals(FavoriteAction.YANDEX_NAVI, FavoriteAction.parse("yandex_navi"))
        assertEquals(FavoriteAction.YANDEX_NAVI, FavoriteAction.parse(" YANDEX_NAVI "))
        assertEquals(FavoriteAction.OFF, FavoriteAction.parse("off"))
        assertEquals(FavoriteAction.OFF, FavoriteAction.parse(null))
        assertEquals(FavoriteAction.OFF, FavoriteAction.parse("unknown_future_action"))
    }

    @Test fun actionPackages() {
        assertNull(FavoriteAction.OFF.packageName)
        assertEquals("ru.yandex.yandexnavi", FavoriteAction.YANDEX_NAVI.packageName)
        assertEquals("ru.yandex.yandexmaps", FavoriteAction.YANDEX_MAPS.packageName)
        assertEquals(FavoriteAction.YANDEX_MAPS, FavoriteAction.parse("yandex_maps"))
    }
}
