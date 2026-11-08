package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SportOemDialogTest {

    private val N = DriveAccent.NORMAL
    private val S = DriveAccent.SPORT
    private val E = DriveAccent.ECO

    // --- OEM sürüş modu penceresi kapatma ---

    @Test fun firstObservationIsNotAChange() {
        val c = OemDriveDialogCloser(300)
        assertFalse(c.update(0, S, allowed = true, sportShown = false))
        assertFalse(c.update(1_000, S, allowed = true, sportShown = false))
    }

    @Test fun closesOnceAfterDelayWhenEnteringSport() {
        val c = OemDriveDialogCloser(300)
        assertFalse(c.update(0, N, true, false))
        assertFalse(c.update(1_000, S, true, false))
        assertFalse(c.update(1_200, S, true, false))
        assertTrue(c.update(1_300, S, true, false))
        // Aynı değişim için bir daha gönderilmez.
        assertFalse(c.update(1_800, S, true, false))
        assertFalse(c.update(5_000, S, true, true))
    }

    @Test fun exitFromShownSportAlsoCloses() {
        val c = OemDriveDialogCloser(300)
        c.update(0, S, true, true)
        assertFalse(c.update(100, N, true, true))
        assertTrue(c.update(500, N, true, true))
    }

    @Test fun nonSportChangesAndHiddenSportExitDoNothing() {
        val c = OemDriveDialogCloser(300)
        c.update(0, N, true, false)
        c.update(100, E, true, false)
        assertFalse(c.update(1_000, E, true, false))
        // Spor ekranı açılmadan Spor'dan çıkılırsa (geçiş yok) yayın da yok.
        c.update(2_000, S, true, false)
        assertTrue(c.update(2_300, S, true, false))
        c.update(2_400, N, true, false)
        assertFalse(c.update(3_000, N, true, false))
    }

    @Test fun disallowedNeverSendsAndCancelsPending() {
        val c = OemDriveDialogCloser(300)
        c.update(0, N, false, false)
        c.update(100, S, false, false)
        assertFalse(c.update(1_000, S, false, false))
        // Bekleyen varken seçenek kapanırsa iptal; açılınca eski değişim için gönderilmez.
        c.update(2_000, N, true, false)
        c.update(2_100, S, true, false)
        assertFalse(c.update(2_200, S, false, false))
        assertFalse(c.update(3_000, S, true, false))
    }

    @Test fun dataGapKeepsLastModeAndNewChangeReplacesPending() {
        val c = OemDriveDialogCloser(300)
        c.update(0, N, true, false)
        assertFalse(c.update(100, null, true, false))
        // null sonrası aynı mod değişim sayılmaz.
        assertFalse(c.update(200, N, true, false))
        assertFalse(c.update(1_000, S, true, false))
        // Pencere açıkken hızlıca başka moda geçildi: yalnız son değişim, kendi gecikmesiyle.
        assertFalse(c.update(1_100, E, true, false))
        assertFalse(c.update(1_400, E, true, false))
        c.update(1_500, S, true, false)
        assertFalse(c.update(1_700, S, true, false))
        assertTrue(c.update(1_800, null, true, false))
    }

    @Test fun resetMakesNextObservationFirst() {
        val c = OemDriveDialogCloser(300)
        c.update(0, N, true, false)
        // Arka plandayken (tick yok) Spor'a geçildi; dönüşte reset sonrası ilk gözlem değişim sayılmaz.
        c.reset()
        assertFalse(c.update(600_000, S, true, false))
        assertFalse(c.update(601_000, S, true, false))
        // Bekleyen varken reset (onStop/demo) onu da iptal eder.
        c.update(602_000, N, true, true)
        c.reset()
        assertFalse(c.update(603_000, N, true, true))
    }

    @Test fun sendsOnlyAfterFocusLost() {
        val c = OemDriveDialogCloser(300)
        c.update(0, N, true, false)
        c.update(1_000, S, true, false, focusLost = false)
        // Süre doldu ama odak bizde: pencere henüz açılmadı, bekle.
        assertFalse(c.update(1_300, S, true, false, focusLost = false))
        assertTrue(c.update(1_500, S, true, false, focusLost = true))
        assertFalse(c.update(1_600, S, true, false, focusLost = true))
    }

    @Test fun neverShownDialogDropsAfterFocusWait() {
        val c = OemDriveDialogCloser(300)
        c.update(0, N, true, false)
        c.update(1_000, S, true, false, focusLost = false)
        assertFalse(c.update(1_300, S, true, false, focusLost = false))
        assertFalse(c.update(1_300 + OemDriveDialogCloser.FOCUS_WAIT_MS, S, true, false, focusLost = false))
        // Sonradan odak gitse de (başka bir pencere) bu değişim için yayın yok.
        assertFalse(c.update(4_000, S, true, false, focusLost = true))
    }

    @Test fun deferredTransitionDoesNotSend() {
        // Düzenleme/sayfa açıkken çağıran allowed=false verir: geçiş ertelenir, yayın da gitmez.
        val c = OemDriveDialogCloser(300)
        c.update(0, N, true, false)
        c.update(1_000, S, false, false, focusLost = true)
        assertFalse(c.update(1_300, S, false, false, focusLost = true))
        assertFalse(c.update(2_000, S, true, false, focusLost = true))
    }

    // --- Widget'ımızın istediği değişim: her mod için kapatılır ---

    @Test fun ownRequestClosesEveryModeChangeEvenWithoutSport() {
        val c = OemDriveDialogCloser(300)
        assertFalse(c.update(0, N, allowed = false, sportShown = false))
        // Eko'ya geçiş: spor kapısı kapalı (allowed false) ama istek bizden.
        assertFalse(c.update(1_000, E, allowed = false, sportShown = false, ownRequestAt = 900, ownRequests = 1, ownAllowed = true))
        assertTrue(c.update(1_300, E, allowed = false, sportShown = false, ownRequestAt = 900, ownRequests = 1, ownAllowed = true))
        // Aynı istek ikinci değişimi (ör. araç geri döndü) kapatmaz.
        assertFalse(c.update(2_000, N, allowed = false, sportShown = false, ownRequestAt = 900, ownRequests = 1, ownAllowed = true))
        assertFalse(c.update(2_400, N, allowed = false, sportShown = false, ownRequestAt = 900, ownRequests = 1, ownAllowed = true))
    }

    @Test fun ownRequestOutsideWindowOrNotAllowedDoesNothing() {
        val c = OemDriveDialogCloser(300)
        c.update(0, N, allowed = false, sportShown = false)
        // İstek çok eski: değişim bizim sayılmaz.
        c.update(10_000, E, allowed = false, sportShown = false, ownRequestAt = 10_000 - OemDriveDialogCloser.OWN_WINDOW_MS - 1, ownRequests = 1)
        assertFalse(c.update(10_400, E, allowed = false, sportShown = false, ownRequestAt = 10_000 - OemDriveDialogCloser.OWN_WINDOW_MS - 1, ownRequests = 1))
        // Düzenleme açıkken (ownAllowed false) kapatılmaz.
        c.update(20_000, N, allowed = false, sportShown = false, ownRequestAt = 19_900, ownRequests = 2, ownAllowed = false)
        assertFalse(c.update(20_400, N, allowed = false, sportShown = false, ownRequestAt = 19_900, ownRequests = 2, ownAllowed = false))
    }
    @Test fun fastTapsCloseEachOwnChange() {
        val c = OemDriveDialogCloser(300)
        assertFalse(c.update(0, E, allowed = false, sportShown = false))
        // İki hızlı dokunuş (Eko→Normal→Spor): iki istek, iki değişim; ikinci değişimin penceresi de kapanır.
        assertFalse(c.update(1_100, N, allowed = false, sportShown = false, ownRequestAt = 1_000, ownRequests = 2, ownAllowed = true))
        assertFalse(c.update(1_300, S, allowed = false, sportShown = false, ownRequestAt = 1_000, ownRequests = 2, ownAllowed = true))
        assertTrue(c.update(1_600, S, allowed = false, sportShown = false, ownRequestAt = 1_000, ownRequests = 2, ownAllowed = true))
        // Üçüncü değişim (istek yok): kapatılmaz.
        assertFalse(c.update(2_000, N, allowed = false, sportShown = false, ownRequestAt = 1_000, ownRequests = 2, ownAllowed = true))
        assertFalse(c.update(2_400, N, allowed = false, sportShown = false, ownRequestAt = 1_000, ownRequests = 2, ownAllowed = true))
    }

    @Test fun leftoverRequestDoesNotLeakIntoNextWindow() {
        val c = OemDriveDialogCloser(300)
        c.update(0, E, allowed = false, sportShown = false)
        // İki istek tek değişimde birleşti: bir istek artık kaldı.
        c.update(1_100, S, allowed = false, sportShown = false, ownRequestAt = 1_000, ownRequests = 2, ownAllowed = true)
        assertTrue(c.update(1_400, S, allowed = false, sportShown = false, ownRequestAt = 1_000, ownRequests = 2, ownAllowed = true))
        // Pencere doldu (artık istek kullanılmış sayılır); yeni tek istek yalnız bir değişimi kapatır.
        c.update(7_000, S, allowed = false, sportShown = false, ownRequestAt = 1_000, ownRequests = 2, ownAllowed = true)
        c.update(8_100, E, allowed = false, sportShown = false, ownRequestAt = 8_000, ownRequests = 3, ownAllowed = true)
        assertTrue(c.update(8_400, E, allowed = false, sportShown = false, ownRequestAt = 8_000, ownRequests = 3, ownAllowed = true))
        c.update(9_000, N, allowed = false, sportShown = false, ownRequestAt = 8_000, ownRequests = 3, ownAllowed = true)
        assertFalse(c.update(9_400, N, allowed = false, sportShown = false, ownRequestAt = 8_000, ownRequests = 3, ownAllowed = true))
    }
}
