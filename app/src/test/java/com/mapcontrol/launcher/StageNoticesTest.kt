package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sahne uyarı şeridi (§10, son karar): yalnızca arabada görünmeyenler (lastik, bakım, yakıt, hava), en çok iki madde ve
 * "+N"; araç çiziminde görünen açık parçalar yazılmaz.
 */
class StageNoticesTest {

    private fun kinds(l: List<StripAlerts.Item>) = l.map { it.kind }

    @Test
    fun calmCarHasNoStrip() {
        assertTrue(StripAlerts.build(false, null, false, 3_978, 155, 4).isEmpty())
        assertTrue(StripAlerts.build(false, null, null, null, null, null).isEmpty())
    }

    @Test
    fun onlyInvisibleThingsInImportanceOrder() {
        val low = TireLow(2, 1.6)
        val l = StripAlerts.build(true, low, true, 800, 40, 80)
        assertEquals(listOf(StripAlerts.Kind.TIRE, StripAlerts.Kind.FUEL_LOW, StripAlerts.Kind.SERVICE_NEAR, StripAlerts.Kind.AIR_BAD), kinds(l))
        assertEquals(low, l.first().tireLow)
        assertEquals(800, l.first { it.kind == StripAlerts.Kind.SERVICE_NEAR }.serviceKm)
    }

    @Test
    fun atMostTwoNamedPlusCount() {
        val all = StripAlerts.build(true, null, true, 0, 0, 80)
        assertEquals(4, all.size) // lastik, yakıt, bakım geçti, hava
        val (shown, more) = StageNotices.visible(all)
        assertEquals(2, shown.size)
        assertEquals(2, more)
        assertEquals(all.take(2), shown)
        // Hiç sığmazsa en önemlisi yine görünür.
        assertEquals(1 to 3, StageNotices.visible(all, fit = 0).let { it.first.size to it.second })
        assertEquals(0, StageNotices.visible(all.take(2)).second)
        assertEquals(0, StageNotices.visible(emptyList<StripAlerts.Item>()).second)
    }

    @Test
    fun stationSearchUris() {
        assertEquals("yandexmaps://maps.yandex.ru/?text=%D0%90%D0%97%D0%A1", NearestStation.yandexUri())
        assertEquals("geo:0,0?q=benzin%20istasyonu", NearestStation.geoUri())
    }
}
