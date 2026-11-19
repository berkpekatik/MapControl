package com.mapcontrol.nav

import com.mapcontrol.launcher.NavDistance
import com.mapcontrol.launcher.NavManeuver
import com.mapcontrol.launcher.NavigationState
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import org.junit.Assert.*
import org.junit.Test

class NativeHudProtocolTest {
    private fun state(distance: NavDistance? = NavDistance("300", "m", 300)) = NavigationState(
        active = true, maneuver = NavManeuver.RIGHT, distance = distance,
        street = "Atatürk Cd.", remainingDistance = NavDistance("18", "km", 18_000),
        sourcePackage = "com.google.android.apps.maps",
    )

    private fun packet(frame: NativeHudFrame, command: Int) = frame.packets.single { it.first() == command }

    @Test fun lifecycleMatchesOemCommands() {
        assertArrayEquals(intArrayOf(52, 1, 0), NativeHudProtocol.start())
        assertArrayEquals(intArrayOf(3, 1, 1), NativeHudProtocol.stop())
    }

    @Test fun rightTurnAndRemainingDistanceMatchGoldenPackets() {
        val frame = NativeHudProtocol.encode(state())!!
        assertEquals(listOf(48, 49, 51, 50, 53), frame.packets.map { it.first() })
        assertEquals(listOf(49, 5, 3, 0, 44, 1, 0), packet(frame, 49))
        assertEquals(listOf(51, 4, 80, 70, 0, 0), packet(frame, 51))
        assertEquals(listOf(50, 6, 3, 0, -72, 11, 0, 0), packet(frame, 50))
        assertEquals(listOf(53, 5, -76, 0, 0, 0, 1), packet(frame, 53))
        val name = "Atatürk Cd.".toByteArray(Charsets.UTF_8).map { it.toInt() and 0xFF }
        assertEquals(listOf(48, name.size) + name, packet(frame, 48))
        assertTrue(frame.hasTurn)
        assertTrue(frame.hasRemaining)
        assertEquals("com.google.android.apps.maps", frame.sourcePackage)
    }

    @Test fun numericBytesKeepJavaByteSignExtension() {
        val frame = NativeHudProtocol.encode(state(NavDistance("200", "m", 200)))!!
        assertEquals(listOf(49, 5, 3, 0, -56, 0, 0), packet(frame, 49))
        assertEquals(listOf(50, 6, 3, 0, -48, 7, 0, 0), packet(frame, 50))
    }

    @Test fun decimalCommaKilometersUseTenthsAndUnitOne() {
        val frame = NativeHudProtocol.encode(state(NavDistance("1,2", "km", 1200)))!!
        assertEquals(listOf(49, 5, 3, 0, -80, 4, 0), packet(frame, 49))
        assertEquals(listOf(50, 6, 3, 0, 12, 0, 0, 1), packet(frame, 50))
    }

    @Test fun imperialVehicleModeUsesOnlyUnitTaggedDistancePackets() {
        val frame = NativeHudProtocol.encode(state(), includeRawMetric = false)!!
        assertEquals(listOf(48, 50, 53), frame.packets.map { it.first() })
        assertTrue(frame.hasTurn)
        assertTrue(frame.hasRemaining)
    }

    @Test fun nameLimitKeepsUtf8CharactersWhole() {
        val input = "a".repeat(44) + "😀" + "x"
        val frame = NativeHudProtocol.encode(state().copy(street = input))!!
        val name = packet(frame, 48)
        assertEquals(44, name[1])
        assertEquals("a".repeat(44), decodeName(name))
        val fits = NativeHudProtocol.encode(state().copy(street = "a".repeat(43) + "😀" + "x"))!!
        assertEquals(47, packet(fits, 48)[1])
        assertEquals("a".repeat(43) + "😀", decodeName(packet(fits, 48)))
    }

    private fun decodeName(packet: List<Int>): String {
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(packet.drop(2).map { it.toByte() }.toByteArray())).toString()
    }

    @Test fun missingNameIsExplicitlyCleared() {
        val frame = NativeHudProtocol.encode(state().copy(street = null))!!
        assertEquals(listOf(48, 0), packet(frame, 48))
    }

    @Test fun partialUpdatesContainOnlyAvailableDistanceGroups() {
        val remainingOnly = NativeHudProtocol.encode(state(null).copy(street = null))!!
        assertEquals(listOf(48, 51, 53), remainingOnly.packets.map { it.first() })
        assertFalse(remainingOnly.hasTurn)
        assertTrue(remainingOnly.hasRemaining)
        val turnOnly = NativeHudProtocol.encode(state().copy(remainingDistance = null))!!
        assertEquals(listOf(48, 49, 50), turnOnly.packets.map { it.first() })
        assertFalse(turnOnly.hasRemaining)
    }

    @Test fun inactiveAndEtaOnlyStatesHaveNoNativeFrame() {
        assertNull(NativeHudProtocol.encode(state().copy(active = false)))
        assertNull(NativeHudProtocol.encode(NavigationState(true, remainingMinutes = 10, arrivalTime = "14:52")))
    }

    @Test fun unknownArrowIsEncodedWithoutInventingDirection() {
        val frame = NativeHudProtocol.encode(state().copy(maneuver = NavManeuver.UNKNOWN))!!
        assertEquals(68, packet(frame, 49)[2])
    }

    @Test fun allManeuversUseObservedArrowCodes() {
        val codes = listOf(9, 4, 5, 2, 3, 6, 7, 8, 19, 29, 55, 4, 5, 49, 15, 10, 68)
        NavManeuver.values().forEachIndexed { index, maneuver ->
            val frame = NativeHudProtocol.encode(state().copy(maneuver = maneuver, roundaboutExit = 7))!!
            assertEquals(maneuver.name, codes[index], packet(frame, 49)[2])
        }
    }

    @Test fun providerIdentitySurvivesEncodingAndAffectsFrameEquality() {
        val google = NativeHudProtocol.encode(state())!!
        val yandex = NativeHudProtocol.encode(state().copy(sourcePackage = "ru.yandex.yandexmaps"))!!
        assertEquals(google.packets, yandex.packets)
        assertNotEquals(google, yandex)
    }

    @Test fun invalidOrNegativeDistanceDoesNotCorruptOtherFields() {
        for (distance in listOf(
            NavDistance("-1", "m", -1), NavDistance("NaN", "m", 0),
            NavDistance("-1", "m", 1), NavDistance("1", "mi", 1609),
        )) {
            val frame = NativeHudProtocol.encode(state(distance))!!
            assertFalse(frame.hasTurn)
            assertTrue(frame.hasRemaining)
        }
    }

    @Test fun numericFieldLimitsRejectOverflowWithoutWrapping() {
        val upper = NativeHudProtocol.encode(state(NavDistance("1677.7215", "km", 0xFFFFFF)))!!
        assertEquals(listOf(49, 5, 3, 0, -1, -1, -1), packet(upper, 49))
        val above = NativeHudProtocol.encode(state(NavDistance("1677.7216", "km", 0x1000000)))!!
        assertFalse(above.hasTurn)
        val hugeDisplayValue = NativeHudProtocol.encode(state(NavDistance("999999999999999999999", "m", 300)))!!
        assertFalse(hugeDisplayValue.hasTurn)
        val hugeExponent = NativeHudProtocol.encode(state(NavDistance("1e2147483647", "m", 300)))!!
        assertFalse(hugeExponent.hasTurn)
        val remainingMax = NativeHudProtocol.encode(state().copy(
            remainingDistance = NavDistance("2147483.647", "km", Int.MAX_VALUE)))!!
        assertEquals(listOf(51, 4, -1, -1, -1, 127), packet(remainingMax, 51))
    }
}
