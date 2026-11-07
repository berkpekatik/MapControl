package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sahne düzeninin saf mantığı: biçimler, varsayılanlar, kayıt biçimi, temizleme ve yerleştirme kuralları. */
class SahneSlotsTest {

    private val apps = listOf("com.google.android.apps.youtube.music", "com.google.android.apps.maps")

    @Test
    fun curatedSlotListsHoldOnlyReadAndAppWidgets() {
        assertEquals(listOf(WidgetType.TRIP, WidgetType.CONSUMPTION), SahneForm.PANEL_TYPES)
        assertEquals(
            listOf(WidgetType.APP, WidgetType.BREAK_REMINDER, WidgetType.SCREEN_OFF, WidgetType.PHONE_REMINDER, WidgetType.CONSUMPTION,
                WidgetType.WEB_PANEL, WidgetType.EQUALIZER, WidgetType.AUTOMATION, WidgetType.CLUSTER_MIRROR),
            SahneForm.STRIP_TYPES,
        )
        // Her listelenen tür en az bir yuvaya sığar; seçicide iki biçimde de aynı türler sunulur.
        (SahneForm.PANEL_TYPES + SahneForm.STRIP_TYPES).forEach { assertTrue(SahneForm.fitsPanel(it) || SahneForm.fitsStrip(it)) }
        assertEquals(SahneForm.offered(SahneSlotKind.STRIP), SahneForm.offered(SahneSlotKind.STRIP, quad = true))
        assertFalse(SahneForm.fitsPanel(WidgetType.APP))
    }

    @Test
    fun tiggo7DefaultIsTripLeftAndEmptyRight() {
        listOf(SahneSlots.defaultPark(apps), SahneSlots.defaultDrive(apps)).forEach { d ->
            assertEquals(WidgetType.TRIP, d.left?.type)
            assertNull(d.right)
            assertFalse(d.quad)
            assertEquals(
                listOf(WidgetType.SCREEN_OFF, WidgetType.CLUSTER_MIRROR, WidgetType.WEB_PANEL, WidgetType.EQUALIZER,
                    WidgetType.AUTOMATION, WidgetType.BREAK_REMINDER),
                d.strip.map { it?.type },
            )
            d.strip.forEach { assertTrue(SahneForm.fitsStrip(it!!.type)) }
        }
        // Boş sağ panel düzenleme dışında yer kaplamaz (araç sahnesi o yana genişler), düzenlemede "+" ile görünür.
        val d = SahneSlots.defaultPark()
        assertTrue(SahneBand.occupies(d, 0, editing = false))
        assertFalse(SahneBand.occupies(d, 1, editing = false))
        assertTrue(SahneBand.occupies(d, 1, editing = true))
    }

    @Test
    fun tiggo8DefaultHasTripAndConsumptionOnTop() {
        val q = SahneSlots.defaultsQuad(LayoutMode.PARK, apps)
        assertTrue(q.quad)
        assertEquals(WidgetType.TRIP, q.left?.type)
        assertEquals(WidgetType.CONSUMPTION, q.right?.type)
        assertNull(q.leftLower)
        assertNull(q.rightLower)
        assertEquals(SahneSlots.defaultStrip(), q.strip)
        assertEquals(q, SahneSlots.sanitize(q))
    }

    @Test
    fun placementRules() {
        val d = SahneSlots.defaultPark(apps)
        val right = SahneSlotRef(SahneSlotKind.PANEL, 1)
        // Boş sağ panele tüketim konabilir; yolculuk zaten solda.
        assertTrue(SahneForm.canPlace(d, right, WidgetType.CONSUMPTION))
        assertFalse(SahneForm.canPlace(d, right, WidgetType.TRIP))
        // Şerit kutucuğu panele konmaz; APP birden çok kez konabilir.
        assertFalse(SahneForm.canPlace(d, right, WidgetType.WEB_PANEL))
        val strip0 = SahneSlotRef(SahneSlotKind.STRIP, 0)
        assertTrue(SahneForm.canPlace(d, strip0, WidgetType.APP))
        val withApp = SahneSlots.set(d, strip0, SahneSlot("app", WidgetType.APP))
        assertTrue(SahneForm.canPlace(withApp, SahneSlotRef(SahneSlotKind.STRIP, 1), WidgetType.APP))
        // Aynı yuvadaki widget'ın yerine kendisi konabilir.
        assertTrue(SahneForm.canPlace(d, strip0, WidgetType.SCREEN_OFF))
        assertFalse(SahneForm.canPlace(d, SahneSlotRef(SahneSlotKind.STRIP, 1), WidgetType.SCREEN_OFF))
    }

    @Test
    fun unknownOrNotYetAvailableTypesEmptyTheirSlot() {
        // Bu sürümde olmayan türler (ör. medya, koltuk) kayıttan okunurken yalnız kendi yuvasını boşaltır.
        // Panelde izinli olmayan bilinen tür (ör. sağlık) ise yedeğe döner.
        val d = SahneSlots.decode("v3|media~m~|trip~t~|seat_driver~s~,screen_off~o~,,,,")!!
        assertNull(d.left)
        assertEquals(WidgetType.TRIP, d.right?.type)
        assertNull(d.strip[0])
        assertEquals(WidgetType.SCREEN_OFF, d.strip[1]?.type)
        assertNull(WidgetType.fromKey("media"))
        assertNull(WidgetType.fromKey("lock"))
        val h = SahneSlots.decode("v3|health~h~||,,,,,")!!
        assertEquals(WidgetType.TRIP, h.left?.type)
    }

    @Test
    fun setGetAndNewIds() {
        val d = SahneSlots.defaultPark(apps)
        val ref = SahneSlotRef(SahneSlotKind.STRIP, 5)
        val placed = SahneSlots.set(d, ref, SahneSlot(SahneSlots.newId(d, WidgetType.APP), WidgetType.APP))
        assertEquals(WidgetType.APP, SahneSlots.get(placed, ref)?.type)
        assertEquals("app", SahneSlots.get(placed, ref)?.id)
        assertEquals("app_2", SahneSlots.newId(placed, WidgetType.APP))
        // İki panelli biçimde alt yuvalar yazılamaz.
        assertEquals(d, SahneSlots.set(d, SahneSlotRef(SahneSlotKind.PANEL, 2), SahneSlot("c", WidgetType.CONSUMPTION)))
    }

    @Test
    fun panelAndStripSpansAreAllowedSizes() {
        WidgetType.values().forEach { t ->
            assertTrue(SahneForm.panelSpan(t) in t.sizes)
            assertTrue(SahneForm.stripSpan(t) in t.sizes)
        }
    }

    @Test
    fun defaultsAreAlreadyClean() {
        listOf(SahneSlots.defaultPark(apps), SahneSlots.defaultDrive(apps), SahneSlots.defaultPark()).forEach { d ->
            assertEquals(d, SahneSlots.sanitize(d))
            assertEquals(d.slots().size, d.slots().map { it.id }.toSet().size)
        }
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val d = SahneSlots.defaultPark(apps)
        assertEquals(d, SahneSlots.decode(SahneSlots.encode(d)))
        val withGaps = SahneSlots.set(SahneSlots.set(d, SahneSlotRef(SahneSlotKind.PANEL, 1), null), SahneSlotRef(SahneSlotKind.STRIP, 2), null)
        assertEquals(withGaps, SahneSlots.decode(SahneSlots.encode(withGaps)))
    }

    @Test
    fun removedHeatsCardEmptiesItsSlotOnDecode() {
        // Tiggo 7'de direksiyon/ön cam ısıtma donanımı yok: eski "heats" yuvası boşalır, kalanlar korunur.
        val d = SahneSlots.decode("v2|heats~h~|trip~trip~|")!!
        assertNull(d.left)
        assertEquals(WidgetType.TRIP, d.right?.type)
    }

    @Test
    fun appPackagesParse() {
        assertEquals(listOf<String?>(null, null), SahneSlots.appPackages(null))
        assertEquals(listOf<String?>("a.b", null), SahneSlots.appPackages("a.b"))
        assertEquals(listOf<String?>("a.b", "c.d"), SahneSlots.appPackages("a.b+c.d"))
        assertNull(SahneSlots.appExtra(emptyList()))
        assertEquals("x+y", SahneSlots.appExtra(listOf("w", "x", "y", "z"), offset = 1))
    }

    @Test
    fun sahneStorageKeysPerModeAndVehicle() {
        assertNotEquals(SahneStore.key(LayoutMode.PARK), SahneStore.key(LayoutMode.DRIVE))
        assertNotEquals(SahneStore.prefsName(0), SahneStore.prefsName(0, VehicleProfile.TIGGO8))
    }

    @Test
    fun quadRoundTripKeepsEmptyLowerSlots() {
        val d = SahneSlots.defaultsQuad(LayoutMode.PARK, apps)
        val raw = SahneSlots.encode(d)
        assertEquals(6, raw.split("|").size)
        assertEquals(d, SahneSlots.decode(raw))
        val gaps = SahneSlots.set(SahneSlots.set(d, SahneSlotRef(SahneSlotKind.PANEL, 2), null), SahneSlotRef(SahneSlotKind.PANEL, 3), null)
        val back = SahneSlots.decode(SahneSlots.encode(gaps))!!
        assertTrue(back.quad) // boşaltılmış alt yuvalar yeniden doldurulmaz
        assertNull(back.leftLower)
        assertNull(back.rightLower)
        assertEquals(back, SahneSlots.toQuad(back))
    }

    @Test
    fun panelContentShrinksOnlyWhenShorterThanItsDesign() {
        assertEquals(1f, PanelFit.scale(567, 567), 0f) // Tiggo 7: tasarım boyu, küçültme yok
        assertEquals(1f, PanelFit.scale(600, 567), 0f)
        assertEquals(1f, PanelFit.scale(300, 0), 0f) // kapalı
        assertEquals(469f / 567f, PanelFit.scale(469, 567), 1e-6f)
    }

    @Test
    fun encodeWritesV3() {
        assertTrue(SahneSlots.encode(SahneSlots.defaultPark(apps)).startsWith("v3|"))
        assertTrue(SahneSlots.encode(SahneSlots.defaultsQuad(LayoutMode.PARK, apps)).startsWith("v3|"))
        assertNull(SahneSlots.decode("v4|media~m~|trip~t~|"))
        assertTrue(SahneSlots.isLegacy("v1|||") && SahneSlots.isLegacy("v2|||"))
        assertFalse(SahneSlots.isLegacy("v3|||"))
    }

    @Test
    fun v1MigrationUsesBreakReminderWhenScreenOffIsTaken() {
        val d = SahneSlots.decode("v1|media~m~|trip~t~|screen_off~s~,consumption~c~,,,,")!!
        assertEquals(listOf(WidgetType.SCREEN_OFF, WidgetType.BREAK_REMINDER, null, null, null, null), d.strip.map { it?.type })
        assertEquals("break_reminder", d.strip[1]?.id)
        // İkisi de düzendeyse tüketim kalır.
        val e = SahneSlots.decode("v1|media~m~|trip~t~|screen_off~s~,consumption~c~,break_reminder~b~,,,")!!
        assertEquals(WidgetType.CONSUMPTION, e.strip[1]?.type)
    }

    @Test
    fun v1MigrationNeedsTripPanelAndV2IsNeverTouched() {
        // Yolculuk paneli yok (eski Sürüş varsayılanı: sağda tüketim paneli) → aynen.
        val drive = SahneSlots.decode("v1|media~m~|nav_turn~n~|consumption~c~,,,,,")!!
        assertEquals(WidgetType.CONSUMPTION, drive.strip[0]?.type)
        // v2 kaydı: kullanıcı tüketimi bilerek geri eklemiş olabilir → dokunulmaz.
        val v2 = SahneSlots.decode("v2|media~m~|trip~t~|consumption~c~,,,,,")!!
        assertEquals(WidgetType.CONSUMPTION, v2.strip[0]?.type)
        // Dört panelli v1: yolculuk sağ üstte; şeritteki tüketim ekranı kapata döner.
        val quad = SahneSlots.decode("v1|media~m~|trip~t~|consumption~c~,,,,,|nav_turn~n~|")!!
        assertEquals(WidgetType.SCREEN_OFF, quad.strip[0]?.type)
        // Saf geçiş: yolculuk solda da sayılır.
        val left = SahneSlots.decode("v2|trip~t~|media~m~|,,consumption~c~,,,")!!
        assertEquals(WidgetType.SCREEN_OFF, SahneSlots.migrateTripConsumption(left).strip[2]?.type)
    }
}
