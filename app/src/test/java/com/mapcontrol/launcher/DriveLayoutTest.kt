package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveLayoutTest {
    private fun policy() = DriveLayoutPolicy()
    private fun DriveLayoutPolicy.at(sec: Long, moving: Boolean?, parked: Boolean = false, can: Boolean = true) =
        update(sec * 1000, moving, parked, can)

    @Test fun entersDriveAfterFiveSecondsOfMotion() {
        val p = policy()
        assertNull(p.at(0, true)); assertNull(p.at(4, true))
        assertEquals(LayoutMode.DRIVE, p.at(5, true))
        assertEquals(LayoutMode.DRIVE, p.mode)
    }

    @Test fun shortMotionDoesNotSwitch() {
        val p = policy()
        p.at(0, true); p.at(3, false)
        assertNull(p.at(6, true)); assertNull(p.at(10, true))
        assertEquals(LayoutMode.DRIVE, p.at(11, true))
    }

    @Test fun unknownSpeedResetsCounters() {
        val p = policy()
        p.at(0, true); p.at(4, null)
        assertNull(p.at(6, true))
    }

    @Test fun redLightStaysInDrive() {
        val p = policy()
        p.at(0, true); p.at(5, true)
        p.at(10, false)
        assertNull(p.at(100, false))      // 90 sn kırmızı ışık
        assertEquals(LayoutMode.DRIVE, p.mode)
    }

    @Test fun fallbackReturnsToParkAfter180Seconds() {
        val p = policy()
        p.at(0, true); p.at(5, true)
        p.at(10, false)
        assertNull(p.at(189, false))
        assertEquals(LayoutMode.PARK, p.at(190, false))
    }

    @Test fun movingAgainRestartsStillTimer() {
        val p = policy()
        p.at(0, true); p.at(5, true)
        p.at(10, false); p.at(150, false); p.at(151, true)
        p.at(152, false)
        assertNull(p.at(320, false))
        assertEquals(LayoutMode.PARK, p.at(332, false))
    }

    @Test fun parkGearSwitchesAfterTwoSeconds() {
        val p = policy()
        p.at(0, true); p.at(5, true)
        p.at(10, false, parked = true)
        assertNull(p.at(11, false, parked = true))
        assertEquals(LayoutMode.PARK, p.at(12, false, parked = true))
    }

    @Test fun parkGearBlipDoesNotSwitch() {
        val p = policy()
        p.at(0, true); p.at(5, true)
        p.at(10, false, parked = true); p.at(11, false, parked = false)
        assertNull(p.at(13, false, parked = true))
        assertNull(p.at(14, false, parked = true))
        assertEquals(LayoutMode.PARK, p.at(15, false, parked = true))
    }

    @Test fun deferredWhileBusyThenSwitches() {
        val p = policy()
        p.at(0, true)
        assertNull(p.at(20, true, can = false))
        assertEquals(LayoutMode.DRIVE, p.at(21, true))
    }

    @Test fun immediateParkForDemo() {
        val p = policy()
        p.at(0, true); p.at(5, true)
        assertEquals(LayoutMode.PARK, p.update(6000, false, false, true, immediatePark = true))
    }

    @Test fun signalsMeasuredOnCar() {
        assertEquals(1, DriveSignals.PARK_GEAR_VALUE)
        assertEquals(1, DriveSignals.HANDBRAKE_ON_VALUE)
    }

    @Test fun driveLayoutPrefsKeyPerProfile() {
        assertEquals("auto_drive_layout", DriveLayoutPrefs.key(1))
        assertEquals("auto_drive_layout_p2", DriveLayoutPrefs.key(2))
    }

}

class ParkOnlyTest {

    @Test fun noParkOnlyFlagOrModeFilterLeft() {
        // Eski "parkOnly" alanı ve mod filtresi kaldırıldı (yansımayla denetlenir: kod yeniden eklenirse test düşer).
        assertTrue(WidgetType::class.java.declaredFields.none { it.name == "parkOnly" })
        val kit = Class.forName("com.mapcontrol.launcher.WidgetKitKt").declaredMethods.map { it.name }
        assertTrue(kit.none { it == "isAllowedIn" || it == "visibleForMode" })
        assertTrue(SahneSlots::class.java.declaredMethods.none { it.name == "visibleForMode" })
    }

    // --- Park sinyali kapılı okuma (layoutTick'i taklit eder; sahte saat, 500 ms tur) ---

    /** Bir tur: sinyal yalnızca politika isterse okunur; [reads] okuma sayısı. */
    private class Sim(val parkedAt: (Long) -> Boolean) {
        val p = DriveLayoutPolicy()
        var reads = 0
        fun tick(now: Long, moving: Boolean?): LayoutMode? {
            val parked = if (p.wantsParkSignal(moving)) { reads++; parkedAt(now) } else false
            return p.update(now, moving, parked, true)
        }
    }

    @Test fun noParkSignalReadsInParkMode() {
        val sim = Sim { true }
        for (i in 0..40) sim.tick(i * 500L, false)
        assertEquals(0, sim.reads)
    }

    @Test fun noParkSignalReadsWhileMovingInDrive() {
        val sim = Sim { true }
        for (i in 0..10) sim.tick(i * 500L, true)   // 5 sn hareket -> Sürüş
        assertEquals(LayoutMode.DRIVE, sim.p.mode)
        val before = sim.reads
        for (i in 11..60) sim.tick(i * 500L, true)
        assertEquals(before, sim.reads)
    }

    @Test fun returnsToParkTwoSecondsAfterGearPEvenWithGating() {
        val pAt = 20_000L
        val sim = Sim { it >= pAt }
        var t = 0L
        while (t <= 10_000) { sim.tick(t, true); t += 500 }          // sürüşe geçildi, giderken okunmaz
        assertEquals(LayoutMode.DRIVE, sim.p.mode)
        // 12 sn'de durdu (hız 0); vites 20 sn'de P.
        var switchedAt = -1L
        while (t <= 40_000 && switchedAt < 0) { if (sim.tick(t, false) == LayoutMode.PARK) switchedAt = t; t += 500 }
        // P'den 2 sn sonra (+ en çok bir tur) Park'a dönmeli; 4 sn'e çıkmamalı.
        assertTrue("switchedAt=$switchedAt", switchedAt in (pAt + 2_000)..(pAt + 2_500))
    }

    @Test fun staleParkSignalDoesNotStartCounterAfterResume() {
        // Sinyal hareket sırasında okunmadığı için, durunca ilk tur false (önbellek geçersiz) sayılır.
        val sim = Sim { false }
        var t = 0L
        while (t <= 10_000) { sim.tick(t, true); t += 500 }
        assertNull(sim.tick(t, false))
        assertEquals(LayoutMode.DRIVE, sim.p.mode)
    }

    @Test fun noticeOnlyOnAutomaticSwitch() {
        assertEquals(LayoutMode.DRIVE, layoutNoticeFor(LayoutMode.PARK, LayoutMode.DRIVE, automatic = true, started = true))
        assertEquals(LayoutMode.PARK, layoutNoticeFor(LayoutMode.DRIVE, LayoutMode.PARK, automatic = true, started = true))
        // Elle geçiş (düzenleme seçicisi, çıkışta dönüş) bildirim göstermez.
        assertNull(layoutNoticeFor(LayoutMode.PARK, LayoutMode.DRIVE, automatic = false, started = true))
        // İlk açılış / mod değişmedi: bildirim yok.
        assertNull(layoutNoticeFor(LayoutMode.PARK, LayoutMode.DRIVE, automatic = true, started = false))
        assertNull(layoutNoticeFor(LayoutMode.DRIVE, LayoutMode.DRIVE, automatic = true, started = true))
    }
}
