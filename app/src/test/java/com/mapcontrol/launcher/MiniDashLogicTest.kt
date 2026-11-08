package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniDashLogicTest {

    private val okTires = TireReading(listOf(2.4, 2.4, 2.3, 2.2), warning = false)

    // --- Uyarılar ---

    @Test fun calmWhenEverythingFine() {
        assertTrue(MiniAlerts.build(okTires, lowFuel = false, serviceKm = 3_978, serviceDays = 155, pm25In = 4).isEmpty())
    }

    @Test fun unknownValuesAreNotAlerts() {
        assertTrue(MiniAlerts.build(null, null, null, null, null).isEmpty())
    }

    @Test fun lowTireNamesWheelAndBar() {
        val a = MiniAlerts.build(TireReading(listOf(2.4, 2.4, 1.6, 2.2), warning = false), false, null, null, null)
        assertEquals(listOf(MiniAlerts.Kind.TIRE), a.map { it.kind })
        assertEquals(2, a[0].tireLow?.wheel)
        assertEquals(1.6, a[0].tireLow?.bar!!, 1e-9)
        assertEquals(23, TireDecode.psi(a[0].tireLow!!.bar!!))
    }

    @Test fun tireWarningFlagWithoutLowValueStillAlerts() {
        val a = MiniAlerts.build(okTires.copy(warning = true), false, null, null, null)
        assertEquals(MiniAlerts.Kind.TIRE, a.single().kind)
        assertNull(a.single().tireLow)
    }

    @Test fun orderIsImportanceOrder() {
        val a = MiniAlerts.build(TireReading(listOf(1.5, 2.4, 2.4, 2.4)), lowFuel = true, serviceKm = 800, serviceDays = 40, pm25In = 80)
        assertEquals(listOf(MiniAlerts.Kind.TIRE, MiniAlerts.Kind.FUEL_LOW, MiniAlerts.Kind.SERVICE_NEAR, MiniAlerts.Kind.AIR_BAD), a.map { it.kind })
        assertEquals(800, a[2].serviceKm)
        assertEquals(80, a[3].pm25)
    }

    @Test fun healthAndFuelCapComeFirstInImportanceOrder() {
        val a = MiniAlerts.build(
            TireReading(listOf(1.5, 2.4, 2.4, 2.4)), lowFuel = true, serviceKm = 800, serviceDays = 40, pm25In = 80,
            batteryV = 11.4, fuelCapOpen = true,
        )
        assertEquals(
            listOf(
                MiniAlerts.Kind.BATTERY_LOW, MiniAlerts.Kind.TIRE, MiniAlerts.Kind.FUEL_CAP,
                MiniAlerts.Kind.FUEL_LOW, MiniAlerts.Kind.SERVICE_NEAR, MiniAlerts.Kind.AIR_BAD,
            ),
            a.map { it.kind },
        )
        assertEquals(11.4, a[0].batteryV!!, 1e-9)
        val (named, more) = MiniAlerts.visible(a)
        assertEquals(listOf(MiniAlerts.Kind.BATTERY_LOW, MiniAlerts.Kind.TIRE), named.map { it.kind })
        assertEquals(4, more)
    }

    @Test fun healthUsesHealthMathThresholds() {
        assertTrue(MiniAlerts.build(null, null, null, null, null, batteryV = 12.6).isEmpty())
        assertEquals(listOf(MiniAlerts.Kind.BATTERY_HIGH), MiniAlerts.build(null, null, null, null, null, batteryV = 15.4, batteryHighHeld = true).map { it.kind })
        // 30 sn sürmeyen aşırı gerilim şeride çıkmaz; 14,5 V (çalışan motor) hiç çıkmaz.
        assertTrue(MiniAlerts.build(null, null, null, null, null, batteryV = 15.4).isEmpty())
        assertTrue(MiniAlerts.build(null, null, null, null, null, batteryV = 14.5, batteryHighHeld = true).isEmpty())
        assertEquals(listOf(MiniAlerts.Kind.FUEL_CAP), MiniAlerts.build(null, null, null, null, null, fuelCapOpen = true).map { it.kind })
    }

    @Test fun serviceThresholdsMatchSahne() {
        assertTrue(MiniAlerts.build(null, null, StageCorners.SERVICE_NEAR_KM, 200, null).any { it.kind == MiniAlerts.Kind.SERVICE_NEAR })
        assertTrue(MiniAlerts.build(null, null, StageCorners.SERVICE_NEAR_KM + 1, 200, null).isEmpty())
        // Gün yaklaşması Sahne'de bakım notu açmaz (yalnızca km ya da geçmiş).
        assertTrue(MiniAlerts.build(null, null, 5_000, 20, null).isEmpty())
        assertEquals(listOf(MiniAlerts.Kind.SERVICE_OVERDUE), MiniAlerts.build(null, null, 0, 0, null).map { it.kind })
        assertEquals(listOf(MiniAlerts.Kind.SERVICE_OVERDUE), MiniAlerts.build(null, null, 4_000, 0, null).map { it.kind })
    }

    @Test fun airOnlyWhenPoor() {
        assertTrue(MiniAlerts.build(null, null, null, null, StageCorners.AIR_BAD_PM25 - 1).isEmpty())
        assertEquals(MiniAlerts.Kind.AIR_BAD, MiniAlerts.build(null, null, null, null, StageCorners.AIR_BAD_PM25).single().kind)
    }

    @Test fun twoNamedThenPlusN() {
        val all = MiniAlerts.build(TireReading(listOf(1.5, 2.4, 2.4, 2.4)), true, 800, 40, 80)
        val (named, more) = MiniAlerts.visible(all)
        assertEquals(2, named.size)
        assertEquals(MiniAlerts.Kind.TIRE, named[0].kind)
        assertEquals(2, more)
        val (one, none) = MiniAlerts.visible(all.take(1))
        assertEquals(1, one.size)
        assertEquals(0, none)
    }

    // --- Navigasyon ---

    @Test fun navHiddenWithoutData() {
        assertFalse(MiniNav.shown(null))
        assertFalse(MiniNav.shown(NavHint(" ", null, null)))
        assertTrue(MiniNav.shown(NavHint(null, 200, null)))
        assertTrue(MiniNav.shown(NavHint("Sağa dönün", null, null)))
    }

    @Test fun navDistanceFormatting() {
        assertEquals("350" to false, MiniNav.distance(347))
        assertEquals("0" to false, MiniNav.distance(3))
        assertEquals("1,0" to true, MiniNav.distance(996))
        assertEquals("1,2" to true, MiniNav.distance(1_240))
        assertEquals("9,9" to true, MiniNav.distance(9_940))
        assertEquals("12" to true, MiniNav.distance(12_400))
        assertEquals("1.5" to true, MiniNav.distance(1_500, '.'))
        assertNull(MiniNav.distance(-1))
        assertNull(MiniNav.distance(null))
    }

    @Test fun navArrival() {
        assertEquals("14:32", MiniNav.arrival(14 * 60 + 32))
        assertEquals("09:05", MiniNav.arrival(9 * 60 + 5))
        assertEquals("2:32", MiniNav.arrival(14 * 60 + 32, is24 = false))
        assertEquals("12:00", MiniNav.arrival(0, is24 = false))
        assertNull(MiniNav.arrival(24 * 60))
        assertNull(MiniNav.arrival(null))
    }

    // --- Ölçüler ---

    @Test fun halfScreenUnderCaptionKeepsEverythingGlanceable() {
        // Tiggo 7 sol yarı: 960 × 648 pencere, başlık 42 dp örtüyor.
        val m = MiniLayout.forSize(960, 648, captionDp = 42)
        assertFalse(m.wide)
        assertTrue(m.pillText)
        assertTrue(m.coverDp > 0)
        assertFalse("bölünmüşte harita yanda, rota satırı yok", m.showNav)
        assertTrue(m.clockSp >= 56f)
        assertTrue(m.valueSp >= 36f)
        assertTrue("yan sütun iki paneli taşır: ${m.sideHeightDp}", m.sideHeightDp >= MiniSide.sideNeedDp(m))
    }

    @Test fun captionShrinksUsableHeight() {
        val free = MiniLayout.forSize(960, 560)
        val covered = MiniLayout.forSize(960, 560, captionDp = 42)
        assertTrue("başlık payı kısa pencere kurallarını tetikler", covered.clockSp < free.clockSp)
        assertEquals(MiniLayout.forSize(960, 518), covered)
    }

    @Test fun touchTargetsNeverBelowMinimum() {
        for (w in listOf(640, 760, 960, 1280, 1707, 1920)) for (h in listOf(440, 520, 610, 648, 720)) for (cap in listOf(0, 42)) {
            val m = MiniLayout.forSize(w, h, cap)
            val at = "$w x $h (başlık $cap)"
            assertTrue(at, m.skipDp >= MiniLayout.MIN_TOUCH_DP)
            assertTrue(at, m.playDp >= MiniLayout.MIN_TOUCH_DP)
            assertTrue(at, m.mediaDp >= MiniLayout.MIN_TOUCH_DP)
            assertTrue(at, m.callRowDp >= MiniLayout.MIN_TOUCH_DP)
            assertTrue(at, MiniSide.favoriteRowDp(m) >= MiniLayout.MIN_TOUCH_DP)
        }
    }

    @Test fun fullScreenUsesWideColumnsWithRoute() {
        // 1920 × 720 ekran, durum çubuğu ve gezinme çubuğu dışında 1920 × 648.
        val m = MiniLayout.forSize(1920, 648)
        assertTrue(m.wide)
        assertTrue(m.showNav)
        assertEquals(0, m.headerDp)
        assertTrue(m.infoDp in 372..420)
        assertTrue(m.sideDp in 420..560)
        // Bilgi ve yan sütunlardan sonra araç sahnesine en az yarı ekran kalır.
        assertTrue(1920 - m.infoDp - m.sideDp - 2 * m.paddingDp - 2 * m.gapDp >= 800)
        assertTrue("tam ekranda yan sütun iki paneli taşır", m.sideHeightDp >= MiniSide.sideNeedDp(m))
        assertTrue(m.clockSp > MiniLayout.forSize(960, 606).clockSp)
    }

    @Test fun tiggo8TopHalfUsesWideRow() {
        // 2560 x ~700 px @240 dpi = 1707 x ~467 dp
        val m = MiniLayout.forSize(1707, 467)
        assertTrue(m.wide)
        assertTrue(m.coverDp > 0)
        assertTrue(m.skipDp >= MiniLayout.MIN_TOUCH_DP)
        assertTrue(MiniLayout.forSize(1707, 480).wide)
        // Kısa geniş pencerede yolculuk değerleri tek satır (etiketli liste sığmaz); yan sütun iki paneli taşır.
        for (h in listOf(467, 470)) {
            val t = MiniLayout.forSize(1707, h)
            assertFalse(t.tripList)
            assertTrue("yan sütun sığar ($h)", t.sideHeightDp >= MiniSide.sideNeedDp(t))
            // Bilgi sütunu (rota satırı dahil) içerik yüksekliğine sığar; tarih düşer.
            assertFalse(t.showDate)
            assertTrue("bilgi sütunu sığar ($h): ${MiniLayout.infoNeedDp(t)}", MiniLayout.infoNeedDp(t) <= h - 2 * t.paddingDp)
        }
    }

    @Test fun fullScreenInfoColumnFits() {
        val m = MiniLayout.forSize(1920, 648)
        assertTrue(m.tripList)
        assertTrue(m.showDate)
        assertTrue(MiniLayout.infoNeedDp(m) <= 648 - 2 * m.paddingDp)
        assertTrue(m.sideHeightDp >= MiniSide.sideNeedDp(m))
    }

    @Test fun narrowerHalvesStayColumn() {
        assertFalse(MiniLayout.forSize(960, 610).wide)
        assertFalse(MiniLayout.forSize(960, 520).wide)
        assertFalse(MiniLayout.forSize(640, 610).wide)
        assertFalse(MiniLayout.forSize(1280, 720).wide)
        assertFalse(MiniLayout.forSize(1707, 960).wide)
        assertFalse(MiniLayout.isWide(1707, 0))
    }

    @Test fun thirdScreenIsCompactWithIconPills() {
        val m = MiniLayout.forSize(640, 610)
        assertFalse(m.pillText)
        assertTrue(m.clockSp < MiniLayout.forSize(960, 610).clockSp)
        assertTrue(m.sideDp >= 280)
    }

    @Test fun shortWindowThinsMediaBeforeSidePanels() {
        val m = MiniLayout.forSize(960, 462, captionDp = 42)
        assertEquals(88, m.mediaDp)
        assertEquals(0, m.coverDp)
        assertEquals(m.sideHeightDp, 462 - 42 - 2 * m.paddingDp - m.headerDp - m.gapDp - 88 - m.gapDp)
    }

    @Test fun tiggo7HalfKeepsCoverAndBothPanels() {
        // Tiggo 7 sol yarı: 960 × 650, başlık 40 dp.
        val m = MiniLayout.forSize(960, 650, captionDp = 40)
        assertTrue("kapak kalır", m.coverDp > 0)
        assertTrue(m.sideHeightDp >= MiniSide.sideNeedDp(m))
        // Telefon paneli dolu hâlinde yolculuğa en az sığacağı kadar yer kalır.
        assertTrue(m.sideHeightDp - m.gapDp - MiniSide.phoneMaxDp(m) >= MiniSide.tripMinDp(m))
    }

    @Test fun driveScreenHalfKeepsTripStatsRow() {
        // 7 Kasım araç testi: sürüş ekranında Mini gösterge yarısı ~960 × 580 dp (başlık gizli), telefon paneli dolu; eski kural
        // 1 dp eksik kalıyordu ve yolculuk panelinde yalnız "89,6 km" görünüyordu.
        for (h in listOf(560, 570, 580, 590, 606)) for (w in listOf(940, 960)) {
            val m = MiniLayout.forSize(w, h)
            val at = "$w x $h"
            assertFalse(at, m.wide)
            assertFalse(at, m.tripList)
            assertTrue("$at yan sütun: ${m.sideHeightDp} / ${MiniSide.sideNeedDp(m)}",
                m.sideHeightDp >= MiniSide.sideNeedDp(m) + MiniLayout.SIDE_SPARE_DP)
            // Dolu telefon paneliyle yolculuk paneline başlık + öncü sayı + değer satırı sığar.
            assertTrue(at, m.sideHeightDp - m.gapDp - MiniSide.phoneMaxDp(m) >= MiniSide.tripMinDp(m))
            // Üç değer tek satırda yan sütun genişliğine sığar.
            assertTrue("$at değer satırı ${MiniSide.statsRowDp(m)} / ${m.sideDp}",
                MiniSide.statsRowDp(m) <= m.sideDp - 2 * (m.panelPadDp + 6))
            assertTrue(at, m.headerDp >= MiniLayout.MIN_TOUCH_DP)
            assertTrue(at, MiniSide.favoriteRowDp(m) >= MiniLayout.MIN_TOUCH_DP)
        }
    }

    @Test fun tiggo8TopHalfKeepsTripStatsRow() {
        for (h in listOf(440, 467, 480)) {
            val m = MiniLayout.forSize(1707, h)
            assertTrue(m.wide)
            assertTrue(m.sideHeightDp - m.gapDp - MiniSide.phoneMaxDp(m) >= MiniSide.tripMinDp(m))
            assertTrue(MiniSide.statsRowDp(m) <= m.sideDp - 2 * (m.panelPadDp + 6))
        }
    }

    @Test fun statSizeShrinksOnlyWhenRowIsTooWide() {
        assertEquals(20f, MiniLayout.forSize(960, 650, captionDp = 40).tripStatSp)
        val narrow = MiniLayout.forSize(640, 610)
        assertTrue(narrow.tripStatSp < 20f)
        assertTrue(narrow.tripStatSp >= MiniSide.MIN_STAT_SP)
    }

    @Test fun tripMinHeightMatchesManropeLines() {
        // Yazı tipi payı kapalı Manrope satırı 1,366 em: 960 × 650 (başlık 40) → 2·12 + 22 + 6 + 52 (38 sp) + 6 + 27 (20 sp).
        val m = MiniLayout.forSize(960, 650, captionDp = 40)
        assertEquals(137, MiniSide.tripMinDp(m))
        // Tam ekran (büyük, etiketli liste): 2·24 + 22 + 6 + 88 (64 sp) + 20 + 3·53.
        val big = MiniLayout.forSize(1920, 650)
        assertEquals(343, MiniSide.tripMinDp(big))
    }

    // --- Yan sütun: yolculuk ---

    @Test fun tripPanelHiddenWithoutData() {
        assertFalse(MiniSide.tripShown(TripHero.Content.NO_DATA))
        assertTrue(MiniSide.tripShown(TripHero.Content.TRIP))
    }

    @Test fun tripStatsSameWhileDrivingAndParked() {
        // Anlık tüketim yok (7 Kasım): sürüşte de süre, ortalama hız, ortalama tüketim.
        assertEquals(emptyList<MiniSide.Stat>(), MiniSide.stats(TripHero.Content.NO_DATA, 12.0, 30, 40, 7.0))
        assertEquals(listOf(MiniSide.Stat.DURATION, MiniSide.Stat.AVG_SPEED, MiniSide.Stat.AVG_CONS),
            MiniSide.stats(TripHero.Content.TRIP, 53.8, 47, 45, 7.9))
        // Okunamayan (ya da 0 dk) değer düşer; tire yazılmaz.
        assertEquals(listOf(MiniSide.Stat.AVG_SPEED), MiniSide.stats(TripHero.Content.TRIP, null, 0, 45, null))
        assertEquals(emptyList<MiniSide.Stat>(), MiniSide.stats(TripHero.Content.TRIP, 53.8, null, null, null))
    }

    // --- Yan sütun: telefon ---

    private val ayse = PhoneContact("1", "Ayşe", "0555 000 00 01", starred = true)
    private val mehmet = PhoneContact("2", "Mehmet", "+90 555 000 00 02", starred = true)
    private val ev = PhoneContact("3", "Ev", "0555 000 00 03")
    private val annem = PhoneContact("4", "Annem", "0555 000 00 04")
    private val bare = PhoneContact("5", "0555 000 00 05", "0555 000 00 05")

    @Test fun phoneOffIsQuietRow() {
        val v = MiniSide.phone(PhoneSnapshot(connected = false, favorites = listOf(ayse)))
        assertEquals(MiniSide.Phone.OFF, v.state)
        assertNull(v.last)
        assertEquals(MiniLayout.MIN_TOUCH_DP, MiniSide.phoneDp(v, MiniLayout.forSize(960, 606)))
    }

    @Test fun phoneStatesFollowPanelState() {
        assertEquals(MiniSide.Phone.PERMISSION, MiniSide.phone(PhoneSnapshot(connected = true, permissionNeeded = true)).state)
        assertEquals(MiniSide.Phone.LOADING, MiniSide.phone(PhoneSnapshot(connected = true)).state)
        assertEquals(MiniSide.Phone.EMPTY, MiniSide.phone(PhoneSnapshot(connected = true, loaded = true)).state)
        // Yalnız isimsiz kayıtlar ve çıplak numaralı kişi: gösterilecek bir şey yok, boş sayılır.
        val unnamed = PhoneSnapshot(connected = true, loaded = true, favorites = listOf(bare),
            recents = listOf(PhoneRecent(RecentType.INCOMING, null, "0555 000 00 99", 1L)))
        assertEquals(MiniSide.Phone.EMPTY, MiniSide.phone(unnamed).state)
    }

    @Test fun callBackIsLastNamedAndFavoritesSkipIt() {
        val s = PhoneSnapshot(connected = true, loaded = true, favorites = listOf(ayse, mehmet, ev, annem),
            recents = listOf(
                PhoneRecent(RecentType.INCOMING, null, "0555 000 00 99", 3L),
                PhoneRecent(RecentType.MISSED, "Mehmet", "0555 000 00 02", 2L),
            ))
        val v = MiniSide.phone(s)
        assertEquals(MiniSide.Phone.CONTENT, v.state)
        assertEquals("Mehmet", v.last?.name)
        // "+90 555…" ile "0555…" aynı kişi: favorilerde tekrar edilmez; en çok üç.
        assertEquals(listOf(ayse, ev, annem), v.favorites)
        val m = MiniLayout.forSize(960, 606)
        assertEquals(MiniSide.phoneMaxDp(m), MiniSide.phoneDp(v, m))
    }

    @Test fun phoneHeightShrinksWithRows() {
        val m = MiniLayout.forSize(960, 606)
        val onlyFavs = MiniSide.phone(PhoneSnapshot(connected = true, loaded = true, favorites = listOf(ayse)))
        assertNull(onlyFavs.last)
        assertEquals(2 * m.panelPadDp + MiniSide.favoriteRowDp(m), MiniSide.phoneDp(onlyFavs, m))
        val onlyLast = MiniSide.phone(PhoneSnapshot(connected = true, loaded = true,
            recents = listOf(PhoneRecent(RecentType.OUTGOING, "Ev", "0555 000 00 03", 1L))))
        assertEquals(2 * m.panelPadDp + m.callRowDp, MiniSide.phoneDp(onlyLast, m))
        assertTrue(MiniSide.phoneDp(onlyLast, m) < MiniSide.phoneMaxDp(m))
    }

    // --- Gerçek navigasyon kaynağı ---

    @Test fun navStateBecomesHint() {
        assertNull(MiniNav.hint(NavigationState.NONE, { "x" }, { "Çıkış $it" }))
        val h = MiniNav.hint(NavigationState.DEMO, { if (it == NavManeuver.RIGHT) "Sağa dönün" else null }, { "Çıkış $it" })!!
        assertEquals("Sağa dönün · Atatürk Cd.", h.maneuver)
        assertEquals(300, h.distanceM)
        assertEquals(14 * 60 + 52, h.arrivalMinutesOfDay)
        assertNull(MiniNav.minutesOfDay("25:00"))
        assertEquals(5, MiniNav.minutesOfDay("0:05"))
    }
}
