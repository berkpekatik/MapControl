package com.mapcontrol.launcher

import com.desaysv.ivi.extra.project.carinfo.HvacID
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import com.mapcontrol.launcher.ClimatePanelPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ClimatePanelPolicyTest {
    private val hv = VDEventCarInfo.MODULE_HVAC

    private fun lock() = ClimatePanelPolicy.Lock()

    @Test fun hardwareKeyPopupIsClosed() {
        // Tuşa basış: değer değişti, panel hemen ardından açıldı → kapat.
        val l = lock()
        assertEquals(Decision.NONE, l.change(1_000, visibleNow = false, showPref = false, canAct = true))
        assertEquals(Decision.CLOSE, l.shown(1_120, showPref = false, canAct = true))
        assertEquals(1, l.closes)
    }

    @Test fun popupBeforeTheValueChangeIsClosedWhenTheChangeArrives() {
        // 7 Kasım: açılış 12:41:23.007, değerler ~0,5 sn sonra okunabildi.
        val l = lock()
        assertEquals(Decision.PENDING, l.shown(10_000, showPref = false, canAct = true))
        assertEquals(Decision.CLOSE, l.change(10_500, visibleNow = true, showPref = false, canAct = true))
        // Değişiklik açılıştan 1 sn'den geç gelirse eşleşmez: OEM/sesle açılan panelde kullanıcının kendi ayarıdır.
        val late = lock()
        assertEquals(Decision.PENDING, late.shown(10_000, showPref = false, canAct = true))
        assertEquals(Decision.NONE, late.change(11_500, visibleNow = true, showPref = false, canAct = true))
        assertEquals(Decision.NONE, late.change(12_100, visibleNow = true, showPref = false, canAct = true))
        // Panel bu arada kapandıysa da kapatma yok.
        val gone = lock()
        gone.shown(10_000, showPref = false, canAct = true)
        gone.hidden(10_300)
        assertEquals(Decision.NONE, gone.change(10_500, visibleNow = false, showPref = false, canAct = true))
    }

    @Test fun reshowAfterOurCloseIsClosedAgain() {
        // 7 Kasım 12:38: dock yazması → kapatma (n=1) → 0,9 ve 1,3 sn sonra yeniden açıldı ama kapatılmadı.
        val l = lock()
        l.change(20_000, visibleNow = false, showPref = false, canAct = true)
        assertEquals(Decision.CLOSE, l.shown(21_494, showPref = false, canAct = true))
        // Aynı açılışın ikinci kaynaktan kenarı (panel hâlâ görünür sayılıyor): yeni kapatma yok.
        assertEquals(Decision.NONE, l.shown(21_600, showPref = false, canAct = true))
        l.hidden(22_300)
        assertEquals(Decision.CLOSE, l.shown(22_389, showPref = false, canAct = true))
        l.hidden(22_700)
        assertEquals(Decision.CLOSE, l.shown(23_100, showPref = false, canAct = true))
        assertEquals(3, l.closes)
        // Kaçmış yeniden açılış kapatmadan sonraki bakışta yakalanır.
        assertEquals(Decision.CLOSE, l.recheck(23_100 + ClimatePanelPolicy.RECHECK_MS, visibleNow = true, showPref = false, canAct = true))
        assertEquals(Decision.NONE, l.recheck(30_000, visibleNow = true, showPref = false, canAct = true))
    }

    @Test fun hv9ReshowRightAfterTheCloseIsClosedAtOnce() {
        // Kapatma T; ECU T+130'da 9=0 (hidden), `hvacshow` animasyon boyunca "true". T+400'de 9 bit 0 yeniden yükselir:
        // yeni açılıştır, 1,1 sn'lik bakışı beklemeden kapatılır.
        val l = lock()
        l.change(0, false, false, true)
        assertEquals(Decision.CLOSE, l.shown(100, showPref = false, canAct = true, fromHv9 = true))
        l.hidden(230)
        assertEquals(Decision.CLOSE, l.shown(500, showPref = false, canAct = true, fromHv9 = true))
        // Yavaş `hvacshow` kenarı kapatmanın kendi animasyonuna denk gelirse süzülür.
        l.hidden(630)
        assertEquals(Decision.KEEP_CLOSING, l.shown(700, showPref = false, canAct = true, fromHv9 = false))
        assertEquals(2, l.closes)
    }

    @Test fun limitDoesNotFillUpWhileAutoKeepsChangingValues() {
        // AUTO fan hızını 2 sn'de bir değiştiriyor (pencere hiç bitmiyor); kullanıcı 3 sn'de bir ayrı tuşa basıyor.
        val l = lock()
        var change = 0L
        var key = 1_000L
        while (key < 30_000) {
            while (change <= key) {
                l.change(change, visibleNow = false, showPref = false, canAct = true)
                change += 2_000
            }
            assertEquals("t=$key", Decision.CLOSE, l.shown(key, showPref = false, canAct = true))
            l.hidden(key + 200)
            key += 3_000
        }
    }

    @Test fun quickCloseAfterASwipeDoesNotDisableTheLock() {
        // Kullanıcı kaydırıp açtı ve 3 sn içinde kapattı: sonraki tuş açılışı yine kapatılır.
        val l = lock()
        l.explicitOpen(1_000, visibleNow = false)
        assertEquals(Decision.KEEP_EXPLICIT_OPEN, l.shown(1_300, showPref = false, canAct = true))
        l.hidden(2_500)
        l.change(30_000, false, false, true)
        assertEquals(Decision.CLOSE, l.shown(30_100, showPref = false, canAct = true))
        // Hemen ardından (kaydırmadan 3 sn geçmeden) gelen tuş açılışı da kapatılır.
        val q = lock()
        q.explicitOpen(0, visibleNow = false)
        q.shown(300, showPref = false, canAct = true)
        q.hidden(2_000)
        q.change(2_200, false, false, true)
        assertEquals(Decision.CLOSE, q.shown(2_300, showPref = false, canAct = true))
    }

    @Test fun newChangesExtendTheWindow() {
        val l = lock()
        l.change(0, false, false, true)
        l.change(2_000, false, false, true)
        assertEquals(Decision.CLOSE, l.shown(4_400, showPref = false, canAct = true))
        l.hidden(5_000)
        // Pencere bitti (son değişiklik 2 sn, son kapatma 4,4 sn): değişikliksiz açılış bekler.
        assertEquals(Decision.PENDING, l.shown(7_000, showPref = false, canAct = true))
    }

    @Test fun explicitSwipeOpenIsNeverClosed() {
        val l = lock()
        l.explicitOpen(1_000, visibleNow = false)
        assertEquals(Decision.KEEP_EXPLICIT_OPEN, l.shown(1_300, showPref = false, canAct = true))
        // Kullanıcı panelin içinde değer değiştiriyor: panel açık kalır (aynı açılış, yeni kenar yok).
        assertEquals(Decision.NONE, l.change(6_000, visibleNow = true, showPref = false, canAct = true))
        assertEquals(Decision.KEEP_EXPLICIT_OPEN, l.recheck(6_500, visibleNow = true, showPref = false, canAct = true))
        // Panel kapandıktan sonra tuşla açılan panel yeniden kilide tabi.
        l.hidden(20_000)
        l.change(30_000, false, false, true)
        assertEquals(Decision.CLOSE, l.shown(30_100, showPref = false, canAct = true))
    }

    @Test fun explicitOpenOnAnAlreadyVisiblePanelKeepsIt() {
        val l = lock()
        l.change(1_000, false, false, true)
        l.explicitOpen(1_050, visibleNow = true)
        assertEquals(Decision.KEEP_EXPLICIT_OPEN, l.shown(1_100, showPref = false, canAct = true))
        // Bilerek açma isteği paneli açamadıysa (3 sn içinde görünmedi) koruma biter.
        val l2 = lock()
        l2.explicitOpen(0, visibleNow = false)
        l2.change(5_000, false, false, true)
        assertEquals(Decision.CLOSE, l2.shown(5_100, showPref = false, canAct = true))
    }

    @Test fun popupWithoutAStateChangeIsLeftAlone() {
        // OEM uygulaması / sesli komut: çevrede değişiklik yok → beklenir, değişiklik gelmezse dokunulmaz.
        val l = lock()
        assertEquals(Decision.PENDING, l.shown(50_000, showPref = false, canAct = true))
        assertEquals(Decision.NONE, l.shown(50_100, showPref = false, canAct = true))
        assertEquals(Decision.NONE, l.recheck(51_000, visibleNow = true, showPref = false, canAct = true))
        // Uzun süredir açık panelde değişiklik (kullanıcı OEM panelinde ayar yapıyor): kapatılmaz.
        assertEquals(Decision.NONE, l.change(60_000, visibleNow = true, showPref = false, canAct = true))
        assertEquals(0, l.closes)
    }

    @Test fun showOnChangePrefKeepsThePanel() {
        val l = lock()
        l.change(1_000, false, showPref = true, canAct = true)
        assertEquals(Decision.KEEP_PREF_SHOW, l.shown(1_100, showPref = true, canAct = true))
        val p = lock()
        p.shown(1_000, showPref = false, canAct = true)
        assertEquals(Decision.KEEP_PREF_SHOW, p.change(1_200, visibleNow = true, showPref = true, canAct = true))
    }

    @Test fun unreadableHv9StillClosesButANonClosablePanelDoesNot() {
        // HVAC 9 okunamadı (null): closeCanAct true → kapatma denenir.
        val l = lock()
        l.change(1_000, false, false, ClimatePanelBits.closeCanAct(null))
        assertEquals(Decision.CLOSE, l.shown(1_100, showPref = false, canAct = ClimatePanelBits.closeCanAct(null)))
        // Panel açık ama CLOSE_HVAC_BT'nin temizleyeceği bit yok (ör. arka sıra): kapatma boşa gitmesin.
        val u = lock()
        u.change(1_000, false, false, true)
        assertEquals(Decision.UNCLOSABLE, u.shown(1_100, showPref = false, canAct = ClimatePanelBits.closeCanAct(8)))
        assertEquals(0, u.closes)
    }

    @Test fun closesPerWindowAreLimited() {
        val l = lock()
        var t = 1_000L
        l.change(t, false, false, true)
        repeat(ClimatePanelPolicy.MAX_CLOSES) {
            t += 700
            assertEquals(Decision.CLOSE, l.shown(t, showPref = false, canAct = true))
            l.hidden(t + 300)
        }
        t += 700
        assertEquals(Decision.LIMIT, l.shown(t, showPref = false, canAct = true))
        // Pencere bittikten sonra yeni tuşa basış: sayaç sıfırlanır.
        l.hidden(t + 100)
        l.change(t + 10_000, false, false, true)
        assertEquals(Decision.CLOSE, l.shown(t + 10_100, showPref = false, canAct = true))
        assertEquals(1, l.closes)
    }

    @Test fun aPanelTheCloseCannotTouchIsRecognised() {
        assertTrue(ClimatePanelBits.closeCanAct(null))
        assertTrue(ClimatePanelBits.closeCanAct(1))
        assertTrue(ClimatePanelBits.closeCanAct(2))
        assertTrue(ClimatePanelBits.closeCanAct(4))
        assertFalse(ClimatePanelBits.closeCanAct(0))
        assertFalse(ClimatePanelBits.closeCanAct(8))
    }

    @Test fun onlyRealValueChangesCount() {
        assertFalse(ClimatePanelPolicy.isChange(null, intArrayOf(3)))
        assertFalse(ClimatePanelPolicy.isChange(intArrayOf(3), intArrayOf(3)))
        assertTrue(ClimatePanelPolicy.isChange(intArrayOf(3), intArrayOf(4)))
        // Arka cam ısıtma paneli açmaz: izlenmez. HVAC 9 abonelikte, kontrol listesinde değil.
        assertFalse(HvacID.ID_AC_REAR_DEFROST in ClimatePanelPolicy.CONTROL_IDS)
        assertFalse(HvacID.ID_AC_DISPLAY_ACTIVE in ClimatePanelPolicy.CONTROL_IDS)
        assertTrue(HvacID.ID_AC_DISPLAY_ACTIVE in ClimatePanelPolicy.SUBSCRIBE_IDS)
        assertTrue(HvacID.ID_AC_MAX_FRONT_DEFOREST in ClimatePanelPolicy.CONTROL_IDS)
        assertTrue(HvacID.ID_AC_CIRCULATION_MODE in ClimatePanelPolicy.CONTROL_IDS)
    }

    @Test fun timingConstantsFitTogether() {
        // ECU ~130 ms + 800 ms animasyon: bakış ondan sonra; ama yeniden açılış penceresi bitmeden.
        assertTrue(ClimatePanelPolicy.RECHECK_MS >= 1_000L)
        assertTrue(ClimatePanelPolicy.RECHECK_MS < ClimatePanelPolicy.RESHOW_MS)
        assertTrue(ClimatePanelPolicy.CLOSING_MS < ClimatePanelPolicy.RECHECK_MS)
        assertTrue(ClimatePanelPolicy.CHANGE_MATCH_MS <= ClimatePanelPolicy.SETTLE_MS)
        // 7 Kasım: açılış değerden en çok ~0,67 sn önce.
        assertTrue(ClimatePanelPolicy.SHOW_BEFORE_CHANGE_MS in 700L..ClimatePanelPolicy.CHANGE_MATCH_MS)
    }

    @Test fun quietWritesUseTheOemDockIdsWithTheSameRawValue() {
        assertEquals(76, ClimateWriter.tempId(ClimateSide.DRIVER, quiet = true))
        assertEquals(78, ClimateWriter.tempId(ClimateSide.PASSENGER, quiet = true))
        assertEquals(77, ClimateWriter.fanId(quiet = true))
        assertEquals(HvacID.ID_AC_TEMPERATURE_DRIVER, ClimateWriter.tempId(ClimateSide.DRIVER, quiet = false))
        assertEquals(HvacID.ID_AC_TEMPERATURE_COPILOT, ClimateWriter.tempId(ClimateSide.PASSENGER, quiet = false))
        assertEquals(HvacID.ID_AC_BLOW_SPEED_LEVEL, ClimateWriter.fanId(quiet = false))
    }

    @Test fun dockIdsAreWhitelistedWithOemValuesOnly() {
        var c = ClimateMath.LO_TEMP
        while (c <= ClimateMath.HI_TEMP) {
            for (side in ClimateSide.values()) {
                assertNotNull("temp $c $side", WidgetWritePolicy.find(hv, ClimateWriter.tempId(side, true), ClimateMath.encodeTemp(c)))
            }
            c += 0.5
        }
        for (f in 1..ClimateMath.FAN_MAX) assertNotNull(WidgetWritePolicy.find(hv, ClimateWriter.fanId(true), ClimateMath.encodeFan(f)))
        assertNull(WidgetWritePolicy.find(hv, 76, 31))
        assertNull(WidgetWritePolicy.find(hv, 78, -1))
        assertNull(WidgetWritePolicy.find(hv, 77, 0))
        assertNull(WidgetWritePolicy.find(hv, 77, 11))
        // HVAC 9'u biz yazmayız (kapatmada 9 ← 2'yi OEM HvacService gönderir): beyaz listede YOK.
        for (v in 0..3) assertNull(WidgetWritePolicy.find(hv, HvacID.ID_AC_DISPLAY_ACTIVE, v))
    }

    @Test fun ourCarsLowercaseShowKeyIsReadFirst() {
        assertEquals("hvacshow", ClimatePanelBits.SHOW_KEYS.first())
        // Bizim araç (SVHvac) yalnız küçük harfli anahtarı yazar; eskiden okunan "hvacShow" null kalıyordu.
        assertTrue(ClimatePanelBits.anyShown { if (it == "hvacshow") "true" else null })
        assertFalse(ClimatePanelBits.anyShown { if (it == "hvacshow") "false" else null })
        assertFalse(ClimatePanelBits.anyShown { null })
        // Arkadaşın build'i: yedek anahtarlar.
        assertTrue(ClimatePanelBits.anyShown { if (it == "hvacShow") "true" else null })
        assertTrue(ClimatePanelBits.anyShown { if (it == "HVAC_DIALOG_IS_SHOW") "true" else null })
        assertFalse(ClimatePanelBits.anyShown { "TRUE" })
    }

    @Test fun hvac9IsABitField() {
        assertFalse(ClimatePanelBits.frontRow(null))
        assertFalse(ClimatePanelBits.frontRow(0))
        assertTrue(ClimatePanelBits.frontRow(1))
        assertFalse(ClimatePanelBits.frontRow(2))
        assertTrue(ClimatePanelBits.pm25(2))
        assertTrue(ClimatePanelBits.fragrance(4))
        assertTrue(ClimatePanelBits.frontRow(5))
        assertTrue(ClimatePanelBits.fragrance(5))
        assertFalse(ClimatePanelBits.pm25(5))
        assertEquals("null", ClimatePanelBits.describe(null))
        assertEquals("0", ClimatePanelBits.describe(0))
        assertEquals("1[front]", ClimatePanelBits.describe(1))
        assertEquals("7[front,pm25,fragrance]", ClimatePanelBits.describe(7))
    }

    @Test fun dockFanIdStaysAt77UntilCarTestEvenThoughOemAutoDockUses12() {
        assertEquals(77, ClimateWriter.fanId(quiet = true))
        assertEquals(12, ClimateWriter.OEM_DOCK_FAN_ID_T1E24)
    }
}
