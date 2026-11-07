package com.mapcontrol.launcher

import com.mapcontrol.launcher.PhoneUiLogic.BannerMode
import com.mapcontrol.launcher.PhoneUiLogic.CallControl
import com.mapcontrol.launcher.PhoneUiLogic.PanelState
import com.mapcontrol.launcher.PhoneUiLogic.PhoneButtonAction
import com.mapcontrol.launcher.PhoneUiLogic.RelTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Telefon arayüzünün saf kararları. Örnekler yalnızca sahte ad/numara (Ayşe, Mehmet, 0555 000 00 xx). */
class PhoneUiLogicTest {

    private val ayse = PhoneContact("c:1", "Ayşe", "0555 000 00 01", starred = true)
    private val recent = PhoneRecent(RecentType.MISSED, "Mehmet", "0555 000 00 02", 1_000L)

    @Test
    fun panelStateCoversEveryBranch() {
        assertEquals(PanelState.NO_PHONE, PhoneUiLogic.panelState(PhoneSnapshot.EMPTY))
        // Bağlı değilken izin eksikliği de olsa önce "bağlı değil".
        assertEquals(PanelState.NO_PHONE, PhoneUiLogic.panelState(PhoneSnapshot(connected = false, permissionNeeded = true)))
        assertEquals(PanelState.PERMISSION, PhoneUiLogic.panelState(PhoneSnapshot(connected = true, permissionNeeded = true, loaded = true)))
        assertEquals(PanelState.LOADING, PhoneUiLogic.panelState(PhoneSnapshot(connected = true, loaded = false)))
        assertEquals(PanelState.EMPTY, PhoneUiLogic.panelState(PhoneSnapshot(connected = true, loaded = true)))
        assertEquals(PanelState.CONTENT, PhoneUiLogic.panelState(PhoneSnapshot(connected = true, loaded = true, favorites = listOf(ayse))))
        assertEquals(PanelState.CONTENT, PhoneUiLogic.panelState(PhoneSnapshot(connected = true, loaded = true, recents = listOf(recent))))
    }

    @Test
    fun phoneButtonActionFollowsTheRule() {
        // Panel ekrandaysa hiçbir şey (bağlantı ve yoldan bağımsız).
        for (c in listOf(true, false)) for (d in listOf(true, false)) {
            assertEquals(PhoneButtonAction.NONE, PhoneUiLogic.phoneButtonAction(panelOnScreen = true, connected = c, canDial = d))
        }
        assertEquals(PhoneButtonAction.SHEET, PhoneUiLogic.phoneButtonAction(panelOnScreen = false, connected = false, canDial = false))
        assertEquals(PhoneButtonAction.SHEET, PhoneUiLogic.phoneButtonAction(panelOnScreen = false, connected = false, canDial = true))
        assertEquals(PhoneButtonAction.OEM_APP, PhoneUiLogic.phoneButtonAction(panelOnScreen = false, connected = true, canDial = false))
        assertEquals(PhoneButtonAction.SHEET, PhoneUiLogic.phoneButtonAction(panelOnScreen = false, connected = true, canDial = true))
    }

    @Test
    fun bannerModeMapsEveryCallKind() {
        assertEquals(BannerMode.HIDDEN, PhoneUiLogic.bannerMode(CallState.IDLE))
        assertEquals(BannerMode.INCOMING, PhoneUiLogic.bannerMode(CallState(CallKind.INCOMING, "Ayşe", "0555 000 00 01")))
        assertEquals(BannerMode.DIALING, PhoneUiLogic.bannerMode(CallState(CallKind.DIALING)))
        assertEquals(BannerMode.ACTIVE, PhoneUiLogic.bannerMode(CallState(CallKind.ACTIVE, startedAtMs = 5L)))
        assertEquals(BannerMode.ENDED, PhoneUiLogic.bannerMode(CallState(CallKind.ENDED)))
        assertEquals(CallKind.values().size, CallKind.values().map { PhoneUiLogic.bannerMode(CallState(it)) }.toSet().size)
    }

    @Test
    fun formatDurationShapes() {
        assertEquals("0:00", PhoneUiLogic.formatDuration(0))
        assertEquals("0:00", PhoneUiLogic.formatDuration(-4_000))
        assertEquals("0:05", PhoneUiLogic.formatDuration(5_999))
        assertEquals("1:23", PhoneUiLogic.formatDuration(83_000))
        assertEquals("12:00", PhoneUiLogic.formatDuration(720_000))
        assertEquals("59:59", PhoneUiLogic.formatDuration(3_599_000))
        assertEquals("1:00:00", PhoneUiLogic.formatDuration(3_600_000))
        assertEquals("1:02:03", PhoneUiLogic.formatDuration(3_723_000))
    }

    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(year: Int, month: Int, day: Int, h: Int, m: Int): Long = Calendar.getInstance(utc).apply {
        clear()
        set(year, month, day, h, m, 0)
    }.timeInMillis

    @Test
    fun relativeTimeBranches() {
        val now = at(2026, Calendar.NOVEMBER, 6, 15, 30)
        assertEquals(RelTime.Now, PhoneUiLogic.relativeTime(now, now - 30_000, utc))
        // Saat ileri kaymış (gelecekteki kayıt): şimdi.
        assertEquals(RelTime.Now, PhoneUiLogic.relativeTime(now, now + 120_000, utc))
        assertEquals(RelTime.Minutes(1), PhoneUiLogic.relativeTime(now, now - 60_000, utc))
        assertEquals(RelTime.Minutes(12), PhoneUiLogic.relativeTime(now, now - 12 * 60_000, utc))
        assertEquals(RelTime.Minutes(59), PhoneUiLogic.relativeTime(now, now - 59 * 60_000 - 59_000, utc))
        assertEquals(RelTime.Hours(1), PhoneUiLogic.relativeTime(now, now - 3_600_000, utc))
        assertEquals(RelTime.Hours(15), PhoneUiLogic.relativeTime(now, at(2026, Calendar.NOVEMBER, 6, 0, 10), utc))
        assertEquals(RelTime.Yesterday, PhoneUiLogic.relativeTime(now, at(2026, Calendar.NOVEMBER, 5, 23, 50), utc))
        assertEquals(RelTime.Yesterday, PhoneUiLogic.relativeTime(now, at(2026, Calendar.NOVEMBER, 5, 0, 1), utc))
        assertEquals(RelTime.Date(3, Calendar.NOVEMBER), PhoneUiLogic.relativeTime(now, at(2026, Calendar.NOVEMBER, 3, 9, 0), utc))
        // Yıl başı: 1 Ocak'ta 31 Aralık "dün".
        val newYear = at(2027, Calendar.JANUARY, 1, 8, 0)
        assertEquals(RelTime.Yesterday, PhoneUiLogic.relativeTime(newYear, at(2026, Calendar.DECEMBER, 31, 22, 0), utc))
        assertEquals(RelTime.Date(30, Calendar.DECEMBER), PhoneUiLogic.relativeTime(newYear, at(2026, Calendar.DECEMBER, 30, 22, 0), utc))
    }

    @Test
    fun favoriteColumnsFitCellsBetweenMinAndMax() {
        assertEquals(3, PhoneUiLogic.favoriteColumns(0))
        assertEquals(3, PhoneUiLogic.favoriteColumns(300))
        assertEquals(4, PhoneUiLogic.favoriteColumns(392)) // Tiggo 7 panel içi
        assertEquals(5, PhoneUiLogic.favoriteColumns(480))
        assertEquals(6, PhoneUiLogic.favoriteColumns(700)) // sayfa
        assertEquals(6, PhoneUiLogic.favoriteColumns(2000))
        (400..2000 step 37).forEach { w ->
            assertTrue("$w", w / PhoneUiLogic.favoriteColumns(w) >= PhoneUiLogic.FAVORITE_CELL_MIN_DP)
        }
    }

    @Test
    fun recentRowsUseNameOrNumberAndLimit() {
        val rows = PhoneUiLogic.recentRows(listOf(
            recent,
            PhoneRecent(RecentType.INCOMING, null, "0555 000 00 99", 900L),
            PhoneRecent(RecentType.OUTGOING, "  ", "0555 000 00 03", 800L),
            PhoneRecent(RecentType.OUTGOING, "Ayşe", "0555 000 00 01", 700L),
            PhoneRecent(RecentType.MISSED, "Zeynep Kaya", "0555 000 00 06", 600L),
            PhoneRecent(RecentType.INCOMING, "Ev", "0555 000 00 03", 500L),
        ))
        assertEquals(5, rows.size)
        assertEquals("Mehmet", rows[0].label)
        assertTrue(rows[0].missed)
        assertEquals("0555 000 00 99", rows[1].label)
        assertEquals("0555 000 00 03", rows[2].label)
        assertFalse(rows[2].missed)
        assertEquals("0555 000 00 01", rows[3].number)
        assertEquals(2, PhoneUiLogic.recentRows(listOf(recent, recent, recent), max = 2).size)
        assertTrue(PhoneUiLogic.recentRows(emptyList()).isEmpty())
        // Satırın toString'i ad/numara içermez.
        assertFalse(rows[0].toString().contains("Mehmet") || rows[0].toString().contains("0555"))
    }

    @Test
    fun pinningOnlyContactIdsAndToggles() {
        assertTrue(PhoneUiLogic.canPin("c:12"))
        assertTrue(PhoneUiLogic.canPin("v:7"))
        assertFalse(PhoneUiLogic.canPin("n:5550000001"))
        assertFalse(PhoneUiLogic.canPin(""))
        assertEquals(listOf("c:1", "c:2"), PhoneUiLogic.togglePin(listOf("c:1"), "c:2"))
        assertEquals(listOf("c:2"), PhoneUiLogic.togglePin(listOf("c:1", "c:2"), "c:1"))
        assertEquals(emptyList<String>(), PhoneUiLogic.togglePin(listOf("c:1"), "c:1"))
    }

    @Test
    fun everyErrorHasItsOwnText() {
        val ids = PhoneError.values().map { PhoneUiLogic.errorRes(it) }
        ids.forEach { assertNotEquals(0, it) }
        assertEquals(PhoneError.values().size, ids.toSet().size)
    }

    // --- 6 Kasım: panel yeniden tasarımı, arama sayfası, öne getirme ---

    @Test
    fun panelRowsFitTheNaturalPanelHeight() {
        assertTrue(PhoneUiLogic.panelContentDp() <= PhoneUiLogic.PANEL_H_DP)
        // Dokunma alanları: asistan şeridi ve favori hücresi (76dp avatar) en az 76dp.
        assertTrue(PhoneUiLogic.PANEL_ASSIST_DP >= 76)
        assertTrue(PhoneUiLogic.PANEL_FAVORITES_DP >= 76)
        assertTrue(PhoneUiLogic.PANEL_LAST_CALL_DP >= 76)
    }

    @Test
    fun lastCallSkipsRecentsWithoutName() {
        val noName = PhoneRecent(RecentType.INCOMING, null, "0555 000 00 03", 3_000L)
        val numberAsName = PhoneRecent(RecentType.OUTGOING, "0555 000 00 04", "0555 000 00 04", 2_000L)
        assertEquals(recent, PhoneUiLogic.lastNamedRecent(listOf(noName, numberAsName, recent)))
        assertEquals(null, PhoneUiLogic.lastNamedRecent(listOf(noName, numberAsName)))
        assertEquals(null, PhoneUiLogic.lastNamedRecent(emptyList()))
    }

    @Test
    fun favoriteCellsAreOnlyNamedAndAtMostThree() {
        val bare = PhoneContact("n:1", "0555 000 00 09", "0555 000 00 09")
        val blank = PhoneContact("c:9", "  ", "0555 000 00 08")
        val list = listOf(bare, ayse, blank, PhoneContact("c:2", "Mehmet", "1"), PhoneContact("c:3", "Zeynep", "2"), PhoneContact("c:4", "Ali", "3"))
        val cells = PhoneUiLogic.favoriteCells(list)
        assertEquals(listOf("Ayşe", "Mehmet", "Zeynep"), cells.map { it.name })
        assertTrue(PhoneUiLogic.favoriteCells(listOf(bare, blank)).isEmpty())
    }

    // --- Asistansız düzen (Sesli asistan düğmesi: Kapalı) ---

    private fun people(n: Int) = (1..n).map { PhoneContact("c:$it", "Kişi $it", "0555 000 00 %02d".format(it)) }

    private fun shape(grid: List<List<PhoneUiLogic.FavCell>>): List<String> = grid.map { row ->
        row.joinToString("") {
            when (it) {
                is PhoneUiLogic.FavCell.Contact -> "K"
                PhoneUiLogic.FavCell.All -> "T"
            }
        }
    }

    /** Gerçek kart yüzünün gövdesi (362dp kart: ekran görüntüsünde y 100-462) ve alt çubuk sayfasınınki. */
    private val faceBody = 290
    private val sheetBody = 402

    private fun grid(n: Int, body: Int = faceBody, hasLast: Boolean = true) =
        shape(PhoneUiLogic.compactGrid(people(n), body, hasLast))

    @Test
    fun compactGridHasNoEmptyCellsAndAllRightAfterLastFavorite() {
        for (body in listOf(faceBody, sheetBody)) {
            assertEquals(listOf("KKK", "KKT"), grid(6, body))
            assertEquals(listOf("KKK", "KKT"), grid(5, body))
            assertEquals(listOf("KKK", "KT"), grid(4, body))
            // Üç kişi: tek satırda dört hücre (yalnız "Tümü" taşıyan ikinci satır yok).
            assertEquals(listOf("KKKT"), grid(3, body))
            assertEquals(listOf("KKT"), grid(2, body))
            assertEquals(listOf("KT"), grid(1, body))
            assertEquals(listOf("T"), grid(0, body))
        }
    }

    @Test
    fun compactSlotsKeepCellWidthAcrossRows() {
        assertEquals(3, PhoneUiLogic.compactSlots(PhoneUiLogic.compactGrid(people(4), faceBody, true)))
        assertEquals(4, PhoneUiLogic.compactSlots(PhoneUiLogic.compactGrid(people(3), faceBody, true)))
        assertEquals(3, PhoneUiLogic.compactSlots(PhoneUiLogic.compactGrid(people(1), faceBody, true)))
    }

    @Test
    fun compactGridSkipsUnnamedAndKeepsOrder() {
        val bare = PhoneContact("n:1", "0555 000 00 09", "0555 000 00 09")
        val grid = PhoneUiLogic.compactGrid(listOf(bare) + people(6), faceBody, true)
        val names = grid.flatten().filterIsInstance<PhoneUiLogic.FavCell.Contact>().map { it.contact.name }
        assertEquals(listOf("Kişi 1", "Kişi 2", "Kişi 3", "Kişi 4", "Kişi 5"), names)
    }

    @Test
    fun compactGridDropsToOneRowWhenTwoRowsDoNotFit() {
        // İki satırın en küçük gövdesi (son aramalı): 100dp kart + 2 × 76dp satır + 2 boşluk = 272dp.
        assertEquals(2, PhoneUiLogic.compactGrid(people(5), 272, true).size)
        assertEquals(listOf("KKKT"), grid(5, 271))
        assertEquals(listOf("KKKT"), grid(5, 200))
        // Son arama yoksa daha küçük gövdede de iki satır sığar.
        assertEquals(2, PhoneUiLogic.compactGrid(people(5), 200, false).size)
    }

    @Test
    fun compactSizesFillTheBodyWithLargeTouchTargets() {
        // En küçük iki satırlı gövdeden (272dp) sayfaya (402dp) kadar her ölçü; tek satır (son aramalı) 186dp: 100 + 10 + 76.
        for (body in 186..sheetBody) for (hasLast in listOf(true, false)) for (n in 0..6) {
            val g = PhoneUiLogic.compactGrid(people(n), body, hasLast)
            val rows = g.size
            val z = PhoneUiLogic.compactSizes(body, rows, hasLast)
            val gaps = PhoneUiLogic.PANEL_GAP_DP * (if (hasLast) rows else rows - 1)
            val used = z.lastDp + rows * z.rowDp + gaps
            val tag = "body=$body n=$n last=$hasLast rows=$rows z=$z"
            // Boşluk kalmaz (tam bölünmeden gelen en çok satır sayısı kadar dp).
            assertTrue(tag, used in (body - rows)..body)
            // Satır taşmaz: avatar + ad satıra sığar, dokunma hedefi ≥ 64dp.
            assertTrue(tag, z.rowDp >= PhoneUiLogic.COMPACT_ROW_MIN_DP)
            assertTrue(tag, z.favAvatarDp + PhoneUiLogic.COMPACT_NAME_ROW_DP + PhoneUiLogic.COMPACT_NAME_GAP_DP <= z.rowDp)
            if (hasLast) {
                assertTrue(tag, z.lastDp >= PhoneUiLogic.COMPACT_LAST_MIN_DP && z.lastDp >= PhoneUiLogic.PANEL_LAST_CALL_DP)
                assertTrue(tag, z.lastAvatarDp + 2 * PhoneUiLogic.COMPACT_CARD_PAD_DP <= z.lastDp)
                assertTrue(tag, z.lastDp - 2 * PhoneUiLogic.COMPACT_CARD_PAD_DP >= PhoneUiLogic.TOUCH_MIN_DP)
            } else {
                assertEquals(0, z.lastDp)
            }
        }
    }

    @Test
    fun compactSizesOnRealCardFace() {
        val z = PhoneUiLogic.compactSizes(faceBody, 2, true, cellWidthDp = 392 / 3)
        assertEquals(100, z.lastDp)
        assertEquals(85, z.rowDp)
        assertEquals(57, z.favAvatarDp)
        // Dört hücreli satırda avatar hücre genişliğiyle sınırlanır.
        assertTrue(PhoneUiLogic.compactSizes(sheetBody, 1, true, cellWidthDp = 80).favAvatarDp <= 64)
    }

    @Test
    fun recentPhotoKeyFallsBackToMatchingContact() {
        val withPhoto = PhoneContact("v:7", "Mehmet", "+90 555 000 00 02", photoKey = "v:7")
        // Kaydın kendi anahtarı önce gelir.
        assertEquals("call:x", PhoneUiLogic.recentPhotoKey(recent.copy(photoKey = "call:x"), listOf(withPhoto)))
        // Kimlikle eşleşme.
        assertEquals("v:7", PhoneUiLogic.recentPhotoKey(recent.copy(contactId = "v:7"), listOf(withPhoto)))
        // Numaranın son hanesiyle eşleşme (ülke kodu farkı önemsiz).
        assertEquals("v:7", PhoneUiLogic.recentPhotoKey(recent, listOf(withPhoto)))
        // Eşleşme yok ya da fotoğraf yok: null (UI baş harf gösterir).
        assertEquals(null, PhoneUiLogic.recentPhotoKey(recent, listOf(withPhoto.copy(photoKey = null))))
        assertEquals(null, PhoneUiLogic.recentPhotoKey(recent.copy(number = "112"), listOf(withPhoto)))
    }

    @Test
    fun callLabelShowsNumberOnlyUnderAName() {
        assertEquals(PhoneUiLogic.CallLabel("Ayşe", "0555"), PhoneUiLogic.callLabel(CallState(CallKind.ACTIVE, " Ayşe ", "0555")))
        assertEquals(PhoneUiLogic.CallLabel("0555", null), PhoneUiLogic.callLabel(CallState(CallKind.ACTIVE, null, "0555")))
        assertEquals(PhoneUiLogic.CallLabel("", null), PhoneUiLogic.callLabel(CallState(CallKind.DIALING)))
        // toString isim/numara içermez.
        assertFalse(PhoneUiLogic.CallLabel("Ayşe", "0555").toString().contains("Ayşe"))
    }

    @Test
    fun callControlsFollowCapabilities() {
        assertEquals(listOf(CallControl.REJECT, CallControl.ANSWER), PhoneUiLogic.callControls(CallKind.INCOMING, true, true, true))
        assertEquals(listOf(CallControl.MUTE, CallControl.AUDIO, CallControl.KEYPAD, CallControl.MINIMIZE, CallControl.HANGUP), PhoneUiLogic.callControls(CallKind.ACTIVE, true, true, true))
        assertEquals(listOf(CallControl.MINIMIZE, CallControl.HANGUP), PhoneUiLogic.callControls(CallKind.ACTIVE, false, false, false))
        assertEquals(listOf(CallControl.MUTE, CallControl.KEYPAD, CallControl.MINIMIZE, CallControl.HANGUP), PhoneUiLogic.callControls(CallKind.DIALING, true, false, true))
        assertEquals(listOf(CallControl.MINIMIZE), PhoneUiLogic.callControls(CallKind.ENDED, true, true, true))
        assertTrue(PhoneUiLogic.keypadEnabled(CallKind.ACTIVE))
        assertFalse(PhoneUiLogic.keypadEnabled(CallKind.DIALING))
        assertFalse(PhoneUiLogic.keypadEnabled(CallKind.INCOMING))
    }

    @Test
    fun keypadKeepsOnlyValidKeysAndTrimsFromTheStart() {
        var s = ""
        for (c in "12a*#9 ") s = PhoneUiLogic.keypadAppend(s, c)
        assertEquals("12*#9", s)
        val long = (1..30).fold("") { acc, i -> PhoneUiLogic.keypadAppend(acc, ('0' + i % 10)) }
        assertEquals(PhoneUiLogic.KEYPAD_MAX, long.length)
        assertTrue(long.endsWith("890"))
    }

    @Test
    fun callScreenClosesOnlyWhenCallIsGone() {
        assertTrue(PhoneUiLogic.callScreenShouldClose(CallKind.NONE))
        for (k in listOf(CallKind.INCOMING, CallKind.DIALING, CallKind.ACTIVE, CallKind.ENDED)) assertFalse(PhoneUiLogic.callScreenShouldClose(k))
    }

    @Test
    fun bringToFrontOncePerTransitionAndOnlyAsDefaultHome() {
        // Gelen arama, aranıyor ve konuşmaya geçişte.
        assertTrue(PhoneUiLogic.shouldBringToFront(CallKind.NONE, CallKind.INCOMING, isDefaultHome = true, alreadyForThisCall = false))
        assertTrue(PhoneUiLogic.shouldBringToFront(CallKind.NONE, CallKind.DIALING, isDefaultHome = true, alreadyForThisCall = false))
        assertTrue(PhoneUiLogic.shouldBringToFront(CallKind.DIALING, CallKind.ACTIVE, isDefaultHome = true, alreadyForThisCall = false))
        assertTrue(PhoneUiLogic.shouldBringToFront(CallKind.INCOMING, CallKind.ACTIVE, isDefaultHome = true, alreadyForThisCall = false))
        // Aynı aramada aynı geçiş tekrar yok; varsayılan ana ekran değilsek hiç yok.
        assertFalse(PhoneUiLogic.shouldBringToFront(CallKind.DIALING, CallKind.ACTIVE, isDefaultHome = true, alreadyForThisCall = true))
        assertFalse(PhoneUiLogic.shouldBringToFront(CallKind.NONE, CallKind.INCOMING, isDefaultHome = false, alreadyForThisCall = false))
        // Durum değişmeden (ör. sessiz/ses yönü değişimi) ya da arama biterken yok.
        assertFalse(PhoneUiLogic.shouldBringToFront(CallKind.ACTIVE, CallKind.ACTIVE, isDefaultHome = true, alreadyForThisCall = false))
        assertFalse(PhoneUiLogic.shouldBringToFront(CallKind.ACTIVE, CallKind.ENDED, isDefaultHome = true, alreadyForThisCall = false))
        assertFalse(PhoneUiLogic.shouldBringToFront(CallKind.ENDED, CallKind.NONE, isDefaultHome = true, alreadyForThisCall = false))
    }

    // --- OEM arama hapı ile çift gösterim ---

    @Test
    fun oemPillOwnsDurationOnlyForDesaySvDialingAndActive() {
        val vd = PhoneBackendName.VDBUS
        assertTrue(PhoneUiLogic.oemPillShowsDuration(CallKind.DIALING, vd))
        assertTrue(PhoneUiLogic.oemPillShowsDuration(CallKind.ACTIVE, vd))
        // Gelen aramada OEM büyük şeridi var, küçük hap yok; bitti/arama yokta hap kalkar: durum bizde.
        assertFalse(PhoneUiLogic.oemPillShowsDuration(CallKind.INCOMING, vd))
        assertFalse(PhoneUiLogic.oemPillShowsDuration(CallKind.ENDED, vd))
        assertFalse(PhoneUiLogic.oemPillShowsDuration(CallKind.NONE, vd))
        // OEM yığını dışındaki arka uçlarda (emülatör, demo, Telecom, HFP istemci) süre bizde kalır.
        for (b in listOf(PhoneBackendName.TELECOM, PhoneBackendName.HFP_CLIENT, PhoneBackendName.DEMO, PhoneBackendName.NONE)) {
            for (k in CallKind.values()) assertFalse("$b $k", PhoneUiLogic.oemPillShowsDuration(k, b))
        }
    }

    @Test
    fun compactPillSitsRightOfClockAndBeforeRightGroup() {
        // 1920 genişlik: saat 900-1020, sağ grup 1450'den, sağ sınır 1896, boşluk 16.
        val s = PhoneUiLogic.compactPillSlot(1020, 1450, 1896, 16, 580, 300)!!
        assertEquals(1036, s.leftPx)
        assertEquals(1450 - 16 - 1036, s.widthPx)
        // Hap saatin üstüne binmez ve sağ gruba değmez.
        assertTrue(s.leftPx >= 1020 + 16)
        assertTrue(s.leftPx + s.widthPx <= 1450 - 16)
        // Geniş boşlukta en çok 580.
        assertEquals(580, PhoneUiLogic.compactPillSlot(1020, 2400, 2536, 16, 580, 300)!!.widthPx)
        // Sağ grup boşsa sağ sınırdan boşluk kadar önce biter.
        assertEquals(1896 - 16 - 1036, PhoneUiLogic.compactPillSlot(1020, null, 1896, 16, 900, 300)!!.widthPx)
    }

    @Test
    fun compactPillCoversRightGroupWhenGapTooNarrowButNeverTheClock() {
        // Saat ile sağ grup arası 200 (< 300): hap tarih/hava üstüne uzanır, saat açık kalır.
        val s = PhoneUiLogic.compactPillSlot(1020, 1252, 1896, 16, 580, 300)!!
        assertEquals(1036, s.leftPx)
        assertEquals(580, s.widthPx)
        // Aralık en küçük genişliğe (220) yetiyorsa hap sağ grubun önünde kalır, ad kısalır.
        val narrow = PhoneUiLogic.compactPillSlot(1020, 1302, 1896, 16, 580, 220)!!
        assertEquals(1036, narrow.leftPx)
        assertEquals(1302 - 16 - 1036, narrow.widthPx)
        assertTrue(narrow.leftPx + narrow.widthPx <= 1302 - 16)
        // Sağ kenara da sığmıyorsa ya da saat ölçülmemişse null (çağıran üst ortaya koyar).
        assertEquals(null, PhoneUiLogic.compactPillSlot(1700, 1800, 1896, 16, 580, 300))
        assertEquals(null, PhoneUiLogic.compactPillSlot(null, 1450, 1896, 16, 580, 300))
        assertEquals(null, PhoneUiLogic.compactPillSlot(0, 1450, 1896, 16, 580, 300))
    }

    // --- Favoriler "Son arama"yı tekrar etmez ---

    @Test
    fun favoritesSkipTheLastCallContact() {
        val list = people(5)
        val lastById = PhoneRecent(RecentType.OUTGOING, "Kişi 2", "0555 999 99 99", 5L, contactId = "c:2")
        assertEquals(listOf("c:1", "c:3", "c:4"), PhoneUiLogic.favoriteCells(list, exclude = lastById).map { it.id })
        // Numara biçimi farklı olsa da son 9 hane eşitse aynı kişi.
        val lastByNumber = PhoneRecent(RecentType.INCOMING, "Başka Ad", "+90 555 000 00 01", 5L)
        assertEquals(listOf("c:2", "c:3", "c:4"), PhoneUiLogic.favoriteCells(list, exclude = lastByNumber).map { it.id })
        // Numara eşleşmezse isim (büyük/küçük harf, boşluk farkı yok).
        val lastByName = PhoneRecent(RecentType.MISSED, " kişi 3 ", "0555 111 11 11", 5L)
        assertEquals(listOf("c:1", "c:2", "c:4"), PhoneUiLogic.favoriteCells(list, exclude = lastByName).map { it.id })
        // Başka biri ise liste değişmez.
        val other = PhoneRecent(RecentType.MISSED, "Mehmet", "0555 222 22 22", 5L)
        assertEquals(listOf("c:1", "c:2", "c:3"), PhoneUiLogic.favoriteCells(list, exclude = other).map { it.id })
        assertEquals(PhoneUiLogic.favoriteCells(list), PhoneUiLogic.favoriteCells(list, exclude = null))
    }

    @Test
    fun compactGridSkipsTheLastCallContact() {
        val last = PhoneRecent(RecentType.OUTGOING, "Kişi 1", "0555 000 00 01", 5L, contactId = "c:1")
        val ids = PhoneUiLogic.compactGrid(people(3), faceBody, true, exclude = last).flatten()
            .filterIsInstance<PhoneUiLogic.FavCell.Contact>().map { it.contact.id }
        assertEquals(listOf("c:2", "c:3"), ids)
        // Kısa numara (6 haneden az) yalnız başına eşleşme sayılmaz.
        val short = PhoneRecent(RecentType.OUTGOING, null, "112", 5L)
        assertFalse(PhoneUiLogic.isSamePerson(PhoneContact("c:9", "Acil", "112"), short))
    }

    // --- "Son arama" adı iki satır ---

    @Test
    fun lastCallNameTwoLinesFitsBothCards() {
        assertEquals(2, PhoneUiLogic.LAST_NAME_MAX_LINES)
        assertTrue(PhoneUiLogic.lastCardCaptionVisible(1))
        assertFalse(PhoneUiLogic.lastCardCaptionVisible(2))
        // Asistanlı kart: PANEL_LAST_CALL_DP, iç pay her yanda FACE_CARD_PAD_DP (PhonePanel aynı sabitleri kullanır).
        val name = PhoneUiLogic.LAST_NAME_SP
        val cap = PhoneUiLogic.LAST_CAPTION_SP
        val faceInner = PhoneUiLogic.PANEL_LAST_CALL_DP - 2 * PhoneUiLogic.FACE_CARD_PAD_DP
        assertTrue(PhoneUiLogic.lastCardTextDp(1, true, name, cap) <= faceInner)
        assertTrue(PhoneUiLogic.lastCardTextDp(2, PhoneUiLogic.lastCardCaptionVisible(2), name, cap) <= faceInner)
        // İki satır + başlık sığmazdı: başlığın kalkması gerekli.
        assertTrue(PhoneUiLogic.lastCardTextDp(2, true, name, cap) > faceInner)
        // Asistansız büyük kart: en az COMPACT_LAST_MIN_DP, iç pay 2 × COMPACT_CARD_PAD_DP.
        val cName = PhoneUiLogic.C_LAST_NAME_SP
        val cCap = PhoneUiLogic.C_LAST_CAPTION_SP
        val compactInner = PhoneUiLogic.COMPACT_LAST_MIN_DP - 2 * PhoneUiLogic.COMPACT_CARD_PAD_DP
        assertTrue(PhoneUiLogic.lastCardTextDp(1, true, cName, cCap) <= compactInner)
        assertTrue(PhoneUiLogic.lastCardTextDp(2, false, cName, cCap) <= compactInner)
    }
}
