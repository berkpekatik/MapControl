package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleNavParseTest {

    @Test
    fun englishTitleWithInstruction() {
        val s = GoogleNavParse.parse("500 ft - Head south-west", "Vega AVM - 12:26 ETA", "1 hr 34 min · 89 mi · 12:26 ETA")!!
        assertTrue(s.active)
        assertEquals(NavManeuver.STRAIGHT, s.maneuver)
        assertEquals(152, s.distance!!.meters)
        assertEquals("152 m", s.distance!!.text)
        assertEquals("Head south-west", s.street)
        assertEquals("12:26", s.arrivalTime)
        assertEquals(94, s.remainingMinutes)
        assertEquals("143,2 km", s.remainingDistance!!.text)
        assertEquals("com.google.android.apps.maps", s.sourcePackage)
    }

    @Test
    fun turkishWithoutTitle() {
        val s = GoogleNavParse.parse(null, "Salkım Söğüt Sk. yönüne doğru", "1 dk. · 260 m · TVS: 23:01")!!
        assertNull(s.distance)
        assertEquals("Salkım Söğüt Sk. yönüne doğru", s.street)
        assertEquals(NavManeuver.STRAIGHT, s.maneuver)
        assertEquals("23:01", s.arrivalTime)
        assertEquals(1, s.remainingMinutes)
        assertEquals(260, s.remainingDistance!!.meters)
    }

    @Test
    fun distanceTitleAndTurnText() {
        val s = GoogleNavParse.parse("200 m", "Turn left - Main", "5 min · 800 m")!!
        assertEquals(NavManeuver.LEFT, s.maneuver)
        assertEquals(200, s.distance!!.meters)
        assertEquals("Turn left", s.street)
        assertEquals(5, s.remainingMinutes)
        assertEquals(800, s.remainingDistance!!.meters)
        assertNull(s.arrivalTime)
    }

    @Test
    fun notNavigation() {
        assertNull(GoogleNavParse.parse("200 m", "Turn left - Main", null))
        assertNull(GoogleNavParse.parse("Sign in to Google", "Account - prompt", "Open Maps"))
        assertNull(GoogleNavParse.parse(null, null, null))
    }

    @Test
    fun instructionWords() {
        assertEquals(NavManeuver.RIGHT, GoogleNavParse.maneuverFromInstruction("Sağa dönün"))
        assertEquals(NavManeuver.LEFT, GoogleNavParse.maneuverFromInstruction("Atatürk Cd. caddesine sola dönün"))
        assertEquals(NavManeuver.SLIGHT_RIGHT, GoogleNavParse.maneuverFromInstruction("Keep right at the fork"))
        assertEquals(NavManeuver.SLIGHT_LEFT, GoogleNavParse.maneuverFromInstruction("Hafif sola dönün"))
        assertEquals(NavManeuver.HARD_RIGHT, GoogleNavParse.maneuverFromInstruction("Sharp right onto Oak"))
        assertEquals(NavManeuver.UTURN_LEFT, GoogleNavParse.maneuverFromInstruction("Make a U-turn"))
        assertEquals(NavManeuver.UTURN_LEFT, GoogleNavParse.maneuverFromInstruction("U dönüşü yapın"))
        assertEquals(NavManeuver.ROUNDABOUT_ENTER, GoogleNavParse.maneuverFromInstruction("At the roundabout, take the 3rd exit"))
        assertEquals(NavManeuver.ROUNDABOUT_ENTER, GoogleNavParse.maneuverFromInstruction("Döner kavşakta 2. çıkıştan çıkın"))
        assertEquals(NavManeuver.FINISH, GoogleNavParse.maneuverFromInstruction("Destination will be on the right"))
        assertEquals(NavManeuver.FERRY, GoogleNavParse.maneuverFromInstruction("Take the ferry"))
        assertEquals(NavManeuver.STRAIGHT, GoogleNavParse.maneuverFromInstruction("Continue onto E-5"))
        assertNull(GoogleNavParse.maneuverFromInstruction("Vega AVM"))
        assertNull(GoogleNavParse.maneuverFromInstruction(null))
    }

    @Test
    fun roundaboutExitNumber() {
        val s = GoogleNavParse.parse("300 m - Döner kavşakta 3. çıkıştan çıkın", null, "4 dk. · 1,2 km")!!
        assertEquals(NavManeuver.ROUNDABOUT_ENTER, s.maneuver)
        assertEquals(3, s.roundaboutExit)
        assertEquals(300, s.distance!!.meters)
    }

    @Test
    fun unknownInstructionKeepsDistance() {
        val s = GoogleNavParse.parse("1,5 km - Vega AVM", null, "20 dk. · 9 km · TVS: 18:40")!!
        assertEquals(NavManeuver.UNKNOWN, s.maneuver)
        assertEquals(1500, s.distance!!.meters)
        assertEquals("18:40", s.arrivalTime)
    }

    @Test
    fun twelveHourArrivalBecomes24Hour() {
        val s = GoogleNavParse.parse("200 m", "Turn left - Main", "5 min · 800 m · 1:26 PM ETA")!!
        assertEquals("13:26", s.arrivalTime)
        assertEquals("00:05", GoogleNavParse.arrivalClock("12:05 AM ETA"))
        assertEquals("12:40", GoogleNavParse.arrivalClock("12:40 p.m."))
        assertEquals("09:15", GoogleNavParse.arrivalClock("9:15 am"))
        assertEquals("23:01", GoogleNavParse.arrivalClock("TVS: 23:01"))
    }

    @Test
    fun onlyNavigationOrOngoingNotificationIsGuidance() {
        assertTrue(GoogleNavParse.isGuidanceNotification(android.app.Notification.CATEGORY_NAVIGATION, 0))
        assertTrue(GoogleNavParse.isGuidanceNotification(null, android.app.Notification.FLAG_ONGOING_EVENT))
        assertTrue(GoogleNavParse.isGuidanceNotification(null, android.app.Notification.FLAG_FOREGROUND_SERVICE))
        // Trafik / işe gidiş bildirimi: sayı içerse de rota değil.
        assertEquals(false, GoogleNavParse.isGuidanceNotification(android.app.Notification.CATEGORY_RECOMMENDATION, 0))
        assertEquals(false, GoogleNavParse.isGuidanceNotification(null, android.app.Notification.FLAG_AUTO_CANCEL))
    }
}
