package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Yandex bildirim çözümleyicisi: çizim adları, mesafe/saat/süre/çıkış metinleri ve tüm bildirimin duruma çevrilmesi. */
class NavParseTest {

    private val maps = NavigationSource.YANDEX_MAPS

    @Test
    fun notificationDrawableNamesMapToManeuvers() {
        val cases = mapOf(
            "notification_straight_sdl" to NavManeuver.STRAIGHT,
            "notification_slight_left_sdl" to NavManeuver.SLIGHT_LEFT,
            "notification_slight_right_sdl" to NavManeuver.SLIGHT_RIGHT,
            "notification_fork_left_sdl" to NavManeuver.SLIGHT_LEFT,
            "notification_fork_right_sdl" to NavManeuver.SLIGHT_RIGHT,
            "notification_left_sdl" to NavManeuver.LEFT,
            "notification_right_sdl" to NavManeuver.RIGHT,
            "notification_hard_left_sdl" to NavManeuver.HARD_LEFT,
            "notification_hard_right_sdl" to NavManeuver.HARD_RIGHT,
            "notification_uturn_left_sdl" to NavManeuver.UTURN_LEFT,
            "notification_uturn_right_sdl" to NavManeuver.UTURN_RIGHT,
            "notification_enter_roundabout_sdl" to NavManeuver.ROUNDABOUT_ENTER,
            "notification_leave_roundabout_sdl" to NavManeuver.ROUNDABOUT_EXIT,
            "notification_exit_left_sdl" to NavManeuver.EXIT_LEFT,
            "notification_exit_right_sdl" to NavManeuver.EXIT_RIGHT,
            "notification_board_ferry_sdl" to NavManeuver.FERRY,
            "notification_finish_sdl" to NavManeuver.FINISH,
            "directions_lane_unknown_32" to NavManeuver.UNKNOWN,
        )
        cases.forEach { (name, want) -> assertEquals(name, want, NavParse.maneuverFromResourceName(name)) }
    }

    @Test
    fun balloonDrawableNamesMapToManeuvers() {
        assertEquals(NavManeuver.STRAIGHT, NavParse.maneuverFromResourceName("context_ra_forward"))
        assertEquals(NavManeuver.SLIGHT_LEFT, NavParse.maneuverFromResourceName("context_ra_take_left"))
        assertEquals(NavManeuver.RIGHT, NavParse.maneuverFromResourceName("context_ra_turn_right"))
        assertEquals(NavManeuver.HARD_LEFT, NavParse.maneuverFromResourceName("context_ra_hard_turn_left"))
        assertEquals(NavManeuver.UTURN_RIGHT, NavParse.maneuverFromResourceName("context_ra_turn_back_right"))
        assertEquals(NavManeuver.ROUNDABOUT_ENTER, NavParse.maneuverFromResourceName("context_ra_in_circular_movement"))
        assertEquals(NavManeuver.ROUNDABOUT_EXIT, NavParse.maneuverFromResourceName("context_ra_out_circular_movement"))
        assertEquals(NavManeuver.WAYPOINT, NavParse.maneuverFromResourceName("context_ra_via"))
        assertEquals(NavManeuver.FINISH, NavParse.maneuverFromResourceName("context_ra_finish"))
    }

    @Test
    fun nonManeuverDrawablesAreIgnored() {
        listOf(null, "", "ic_notification_logo", "notification_lane_left", "traffic_light_red", "notification_foo_sdl").forEach {
            assertNull("$it", NavParse.maneuverFromResourceName(it))
        }
    }

    @Test
    fun distancesInRussianTurkishAndEnglish() {
        assertEquals(NavDistance("300", "m", 300), NavParse.parseDistanceOnly("300 м"))
        assertEquals(NavDistance("300", "m", 300), NavParse.parseDistanceOnly("300 m"))
        assertEquals(NavDistance("300", "m", 300), NavParse.parseDistanceOnly("300м"))
        assertEquals(NavDistance("1,2", "km", 1200), NavParse.parseDistanceOnly("1,2 км"))
        assertEquals(NavDistance("1,2", "km", 1200), NavParse.parseDistanceOnly("1.2 km"))
        assertEquals(NavDistance("18", "km", 18_000), NavParse.parseDistanceOnly(" 18 KM "))
        assertEquals("1,2 km", NavParse.parseDistanceOnly("1,2 км")!!.text)
        assertNull(NavParse.parseDistanceOnly("Atatürk Cd."))
        assertNull(NavParse.parseDistanceOnly("18 min"))
        assertNull(NavParse.parseDistanceOnly("300 м, затем направо"))
        assertEquals(NavDistance("18", "km", 18_000), NavParse.findDistance("Осталось 18 км"))
        assertNull(NavParse.findDistance("Mimar Sinan Mah."))
    }

    @Test
    fun clockAndMinutes() {
        assertEquals("14:52", NavParse.findClock("14:52"))
        assertEquals("14:52", NavParse.findClock("Varış 14:52"))
        assertEquals("09:05", NavParse.findClock("Прибытие в 9:05"))
        assertNull(NavParse.findClock("300 м"))
        assertNull(NavParse.findClock("25:10"))
        assertEquals(18, NavParse.parseMinutes("18 dk"))
        assertEquals(18, NavParse.parseMinutes("18 мин"))
        assertEquals(45, NavParse.parseMinutes("45 min"))
        assertEquals(72, NavParse.parseMinutes("1 sa 12 dk"))
        assertEquals(72, NavParse.parseMinutes("1 ч 12 мин"))
        assertEquals(120, NavParse.parseMinutes("2 h"))
        assertNull(NavParse.parseMinutes("300 m"))
        assertNull(NavParse.parseMinutes("14:52"))
    }

    @Test
    fun roundaboutExitNumbers() {
        assertEquals(3, NavParse.parseExitNumber("3. çıkış"))
        assertEquals(2, NavParse.parseExitNumber("2.çıkış"))
        assertEquals(1, NavParse.parseExitNumber("1-й съезд"))
        assertEquals(7, NavParse.parseExitNumber("7 съезд"))
        assertEquals(7, NavParse.parseExitNumber("Exit 7"))
        assertEquals(3, NavParse.parseExitNumber("3rd exit"))
        assertEquals(2, NavParse.parseExitNumber("Second exit"))
        assertNull(NavParse.parseExitNumber("Atatürk Cd."))
    }

    @Test
    fun placeholderIsNoRoute() {
        assertTrue(NavParse.isPlaceholder("Navigator is running"))
        assertTrue(NavParse.isPlaceholder("Navigator başlatıldı"))
        assertTrue(NavParse.isPlaceholder("Навигатор запущен"))
        assertFalse(NavParse.isPlaceholder("Atatürk Cd."))
        assertNull(NavParse.parse(NavNotificationInput(maps, title = "Navigator başlatıldı")))
        assertNull(NavParse.parse(NavNotificationInput(maps, title = "Yandex Maps", text = "Navigator is running")))
        assertNull(NavParse.parse(NavNotificationInput(maps)))
        // Ne ok ne mesafe: rota sayılmaz (ör. trafik ya da indirme bildirimi).
        assertNull(NavParse.parse(NavNotificationInput(maps, title = "Пробки 5 баллов", text = "Москва")))
    }

    @Test
    fun richNotificationWithRemoteViews() {
        val s = NavParse.parse(NavNotificationInput(
            maps,
            title = "300 м",
            text = "Atatürk Cd.",
            viewTexts = listOf("titleView" to "300 м", "descriptionView" to "Atatürk Cd.", "etaView" to "14:52 · 18 km · 25 dk"),
            images = listOf("laneIcon" to "notification_lane_straight", "primaryIconTinted" to "notification_right_sdl"),
        ), nowMs = 42L)!!
        assertTrue(s.active)
        assertEquals(NavManeuver.RIGHT, s.maneuver)
        assertEquals(NavDistance("300", "m", 300), s.distance)
        assertEquals("Atatürk Cd.", s.street)
        assertEquals("14:52", s.arrivalTime)
        assertEquals(NavDistance("18", "km", 18_000), s.remainingDistance)
        assertEquals(25, s.remainingMinutes)
        assertEquals(maps, s.sourcePackage)
        assertEquals(42L, s.updatedAtMs)
        assertNull(s.roundaboutExit)
    }

    @Test
    fun extrasOnlyStillGiveDistanceAndStreet() {
        // RemoteViews okunamazsa (gizli API kapalı): ok bilinmez, mesafe ve sokak yine gelir.
        val s = NavParse.parse(NavNotificationInput(maps, title = "1,2 км", text = "проспект Мира"))!!
        assertEquals(NavManeuver.UNKNOWN, s.maneuver)
        assertEquals(NavDistance("1,2", "km", 1200), s.distance)
        assertEquals("проспект Мира", s.street)
        assertNull(s.arrivalTime)
        assertNull(s.remainingDistance)
    }

    @Test
    fun subTextCarriesArrivalAndRemaining() {
        val s = NavParse.parse(NavNotificationInput(
            NavigationSource.YANDEX_NAVI,
            title = "850 m",
            text = "İstiklal Cd.",
            subText = "Varış 14:52, 18 km, 1 sa 5 dk",
            images = listOf(null to "notification_slight_left_sdl"),
        ))!!
        assertEquals(NavManeuver.SLIGHT_LEFT, s.maneuver)
        assertEquals("İstiklal Cd.", s.street)
        assertEquals("14:52", s.arrivalTime)
        assertEquals(NavDistance("18", "km", 18_000), s.remainingDistance)
        assertEquals(65, s.remainingMinutes)
    }

    @Test
    fun roundaboutTakesExitNumberAndKeepsStreetClean() {
        val s = NavParse.parse(NavNotificationInput(
            maps,
            title = "150 m",
            viewTexts = listOf("exit_number_text" to "3. çıkış", "descriptionView" to "Barbaros Blv."),
            images = listOf("primaryIcon" to "notification_leave_roundabout_sdl"),
        ))!!
        assertEquals(NavManeuver.ROUNDABOUT_EXIT, s.maneuver)
        assertEquals(3, s.roundaboutExit)
        assertEquals("Barbaros Blv.", s.street)
    }

    @Test
    fun maneuverFromSmallIconWhenNoRemoteViews() {
        val s = NavParse.parse(NavNotificationInput(maps, title = "2 km", text = "D100", smallIconName = "notification_exit_right_sdl"))!!
        assertEquals(NavManeuver.EXIT_RIGHT, s.maneuver)
        assertEquals("D100", s.street)
    }

    @Test
    fun primaryIconWinsOverOtherImages() {
        val s = NavParse.parse(NavNotificationInput(
            maps,
            title = "300 m",
            images = listOf("secondaryIcon" to "notification_left_sdl", "primaryIcon" to "notification_uturn_left_sdl"),
        ))!!
        assertEquals(NavManeuver.UTURN_LEFT, s.maneuver)
        assertNull(s.street)
    }

    @Test
    fun maneuverDistanceIsNotTakenAsRemainingDistance() {
        val s = NavParse.parse(NavNotificationInput(
            maps,
            title = "300 m",
            viewTexts = listOf("titleView" to "300 m"),
            images = listOf("primaryIcon" to "notification_straight_sdl"),
        ))!!
        assertNull(s.remainingDistance)
    }

    @Test
    fun latLonSetting() {
        assertEquals(41.0082 to 28.9784, NavParse.parseLatLon("41.0082, 28.9784"))
        assertEquals(41.0082 to 28.9784, NavParse.parseLatLon("41.0082 28.9784"))
        assertEquals(-33.9 to 151.2, NavParse.parseLatLon(" -33.9;151.2 "))
        assertNull(NavParse.parseLatLon("41,0082, 28,9784"))
        assertNull(NavParse.parseLatLon("95, 10"))
        assertNull(NavParse.parseLatLon("ev"))
        assertNull(NavParse.parseLatLon(null))
    }

    @Test
    fun demoStateMatchesTheBrief() {
        val d = NavigationState.DEMO
        assertTrue(d.active)
        assertEquals(NavManeuver.RIGHT, d.maneuver)
        assertEquals("300 m", d.distance!!.text)
        assertEquals("Atatürk Cd.", d.street)
        assertEquals("14:52", d.arrivalTime)
        assertEquals("18 km", d.remainingDistance!!.text)
        assertFalse(NavigationState.NONE.active)
    }
}
