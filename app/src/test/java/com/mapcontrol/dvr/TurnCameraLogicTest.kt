package com.mapcontrol.dvr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TurnCameraLogicTest {

    /** Testlerin çoğu onay beklemesiz; dörtlü yarışı testleri gerçek sürelerle. */
    private fun logic() = TurnCameraLogic(holdMs = 1_500L, confirmMs = 0L, quietMs = 1_500L)
    private fun real() = TurnCameraLogic()

    @Test fun leftSignalShowsLeftCamera() {
        val l = logic()
        assertEquals(TurnSide.LEFT, l.update(true, left = 1, right = 0, hazard = 0, gear = 5, now = 0))
        assertEquals(2, TurnSide.LEFT.cam)
    }

    @Test fun rightSignalShowsRightCamera() {
        val l = logic()
        assertEquals(TurnSide.RIGHT, l.update(true, left = 0, right = 1, hazard = 0, gear = 5, now = 0))
        assertEquals(1, TurnSide.RIGHT.cam)
    }

    @Test fun singleSideNeedsConfirm() {
        val l = real()
        assertNull(l.update(true, 1, 0, 0, 5, now = 1_000))
        assertEquals(1_150L, l.confirmAt)
        assertNull(l.update(true, 1, 0, 0, 5, now = 1_100))
        assertEquals(TurnSide.LEFT, l.update(true, 1, 0, 0, 5, now = 1_150))
        assertNull(l.confirmAt)
    }

    /** Dörtlü açılışı: 35 önce gelir (yalnız sol), 34 ve 79 ms'ler sonra. Sol kamera açılmamalı. */
    @Test fun hazardOnRaceDoesNotShowLeft() {
        val l = real()
        assertNull(l.update(true, left = 1, right = 0, hazard = 0, gear = 5, now = 1_000))
        assertNull(l.update(true, left = 1, right = 1, hazard = 0, gear = 5, now = 1_005))
        assertNull(l.update(true, left = 1, right = 1, hazard = 1, gear = 5, now = 1_010))
        assertNull(l.update(true, left = 1, right = 1, hazard = 1, gear = 5, now = 1_200))
    }

    /** Dörtlü kapanışı: 79 ve 34 önce düşer, 35 bir süre 1 kalır. Sol kamera açılmamalı. */
    @Test fun hazardOffRaceDoesNotShowLeft() {
        val l = real()
        l.update(true, 1, 1, 1, 5, now = 1_000)
        assertNull(l.update(true, left = 1, right = 1, hazard = 0, gear = 5, now = 2_000))
        assertNull(l.update(true, left = 1, right = 0, hazard = 0, gear = 5, now = 2_005))
        assertNull(l.update(true, left = 1, right = 0, hazard = 0, gear = 5, now = 2_400))
        assertNull(l.update(true, left = 0, right = 0, hazard = 0, gear = 5, now = 2_600))
        assertNull(l.update(true, left = 0, right = 0, hazard = 0, gear = 5, now = 4_000))
    }

    /** Soldan hızlıca sağa: sağ (34) önce 1 gelir, sol (35) birkaç ms sonra 0. Sağ kamera beklemeden açılmalı. */
    @Test fun quickLeftToRightOverlapIsNotHazard() {
        val l = real()
        l.update(true, 1, 0, 0, 5, now = 0)
        assertEquals(TurnSide.LEFT, l.update(true, 1, 0, 0, 5, now = 150))
        // Örtüşme: ikisi de 1, 79 = 0. Açık sol kart kalır, dörtlü sayılmaz.
        assertEquals(TurnSide.LEFT, l.update(true, left = 1, right = 1, hazard = 0, gear = 5, now = 1_000))
        l.update(true, left = 0, right = 1, hazard = 0, gear = 5, now = 1_008)
        assertEquals(TurnSide.RIGHT, l.update(true, left = 0, right = 1, hazard = 0, gear = 5, now = 1_158))
    }

    @Test fun quickRightToLeftOverlapIsNotHazard() {
        val l = real()
        l.update(true, 0, 1, 0, 5, now = 0)
        l.update(true, 0, 1, 0, 5, now = 150)
        l.update(true, 1, 1, 0, 5, now = 1_000)
        l.update(true, 1, 0, 0, 5, now = 1_008)
        assertEquals(TurnSide.LEFT, l.update(true, 1, 0, 0, 5, now = 1_158))
    }

    /** 79'u olmayan yazılım: iki taraf 300 ms birlikte açık kalırsa dörtlü. */
    @Test fun longBothWithoutHazardSignalCountsAsHazard() {
        val l = real()
        assertNull(l.update(true, 1, 1, null, 5, now = 0))
        assertEquals(300L, l.confirmAt)
        assertNull(l.update(true, 1, 1, null, 5, now = 300))
        // Dörtlü sürerken yoklama her turda ikisini de 1 okur.
        assertNull(l.update(true, 1, 1, null, 5, now = 1_900))
        // Kapanışta sol bir süre kalır: sessiz süre içinde gösterilmez.
        assertNull(l.update(true, 1, 0, null, 5, now = 2_000))
        assertNull(l.update(true, 1, 0, null, 5, now = 2_200))
    }

    @Test fun hazardHidesShownCameraImmediately() {
        val l = logic()
        assertEquals(TurnSide.LEFT, l.update(true, 1, 0, 0, 5, now = 0))
        assertNull(l.update(true, 1, 1, 1, 5, now = 100))
    }

    @Test fun signalAfterHazardQuietShows() {
        val l = real()
        l.update(true, 1, 1, 1, 5, now = 0)
        assertNull(l.update(true, 0, 1, 0, 5, now = 1_000))
        l.update(true, 0, 1, 0, 5, now = 1_500)
        assertEquals(TurnSide.RIGHT, l.update(true, 0, 1, 0, 5, now = 1_650))
    }

    @Test fun hazardShowsNothing() {
        val l = logic()
        assertNull(l.update(true, left = 1, right = 1, hazard = 1, gear = 5, now = 0))
        assertNull(l.update(true, left = 1, right = 1, hazard = null, gear = 5, now = 100))
        assertNull(l.update(true, left = 1, right = 0, hazard = 1, gear = 5, now = 200))
    }

    @Test fun staysForHoldAfterSignalOff() {
        val l = logic()
        l.update(true, 1, 0, 0, 5, now = 0)
        assertEquals(TurnSide.LEFT, l.update(true, 0, 0, 0, 5, now = 1_000))
        assertNull(l.update(true, 0, 0, 0, 5, now = 1_500))
    }

    @Test fun unreadSignalCountsAsOffButHolds() {
        val l = logic()
        l.update(true, 1, 0, 0, 5, now = 0)
        assertEquals(TurnSide.LEFT, l.update(true, null, null, null, null, now = 300))
    }

    @Test fun switchesSideImmediately() {
        val l = logic()
        l.update(true, 1, 0, 0, 5, now = 0)
        assertEquals(TurnSide.RIGHT, l.update(true, 0, 1, 0, 5, now = 200))
    }

    @Test fun inactiveOrReverseHidesImmediately() {
        val l = logic()
        l.update(true, 1, 0, 0, 5, now = 0)
        assertNull(l.update(false, 1, 0, 0, 5, now = 100))
        l.update(true, 0, 1, 0, 5, now = 200)
        assertNull(l.update(true, 0, 1, 0, gear = 2, now = 300))
    }
}
