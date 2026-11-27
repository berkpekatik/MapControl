package com.mapcontrol.nav

import com.mapcontrol.launcher.NavDistance
import com.mapcontrol.launcher.NavManeuver
import com.mapcontrol.launcher.NavigationState
import org.junit.Assert.*
import org.junit.Test

class NativeHudProtocolTest {
    private fun state(distance: NavDistance? = NavDistance("300", "m", 300)) = NavigationState(
        active = true, maneuver = NavManeuver.RIGHT, distance = distance,
        street = "Atatürk Cd.", remainingDistance = NavDistance("18", "km", 18_000),
        sourcePackage = "com.google.android.apps.maps",
    )

    private fun packet(frame: NativeHudFrame, command: Int) = frame.packets.single { it.first() == command }

    @Test fun lifecycleStartAndBlankPackets() {
        assertArrayEquals(intArrayOf(52, 1, 0), NativeHudProtocol.start())
        val blank = NativeHudProtocol.blank()
        assertEquals(listOf(48, 49, 51), blank.packets.map { it.first() })
        assertEquals(listOf(48, 1, 32), blank.packets[0])
        assertEquals(listOf(49, 5, -1, -1, 0, 0, 0), blank.packets[1])
        assertEquals(listOf(51, 4, 0, 0, 0, 0), blank.packets[2])
    }

    @Test fun rightTurnAndRemainingDistanceMatchGoldenPackets() {
        val frame = NativeHudProtocol.encode(state())!!
        assertEquals(listOf(48, 49, 51, 50, 53), frame.packets.map { it.first() })
        assertEquals(listOf(48, 12, 65, 116, 97, 116, 195, 188, 114, 107, 32, 67, 100, 46), packet(frame, 48))
        assertEquals(listOf(49, 5, 3, 0, 44, 1, 0), packet(frame, 49))
        assertEquals(listOf(51, 4, 80, 70, 0, 0), packet(frame, 51))
        // Araçta ok ve mesafeyi çizen OEM paketleri (2026-11-24 araçta doğrulandı): 300 m -> 3000 onda bir, m=0; 18 km -> 180, km=1.
        assertEquals(listOf(50, 6, 3, 0, -72, 11, 0, 0), packet(frame, 50))
        assertEquals(listOf(53, 5, -76, 0, 0, 0, 1), packet(frame, 53))
        assertEquals("com.google.android.apps.maps", frame.sourcePackage)
    }

    @Test fun numericBytesKeepJavaByteSignExtension() {
        val frame = NativeHudProtocol.encode(state(NavDistance("200", "m", 200)))!!
        assertEquals(listOf(49, 5, 3, 0, -56, 0, 0), packet(frame, 49))
    }

    @Test fun rawMetersAreEncodedNotDisplayedNumbers() {
        // 0.2 mi gösterimi 322 m'ye karşılık gelir; J7 ham metre değerini bekler.
        val frame = NativeHudProtocol.encode(state(NavDistance("0.2", "mi", 322)))!!
        assertEquals(listOf(49, 5, 3, 0, 66, 1, 0), packet(frame, 49))
    }

    @Test fun nameLimitTruncatesRawUtf8Bytes() {
        val input = "a".repeat(44) + "😀"
        val frame = NativeHudProtocol.encode(state().copy(street = input))!!
        val name = packet(frame, 48)
        assertEquals(47, name[1])
        assertEquals(47, name.size - 2)
        assertEquals(44, name.drop(2).count { it == 97 })
        assertEquals(0xF0, name[2 + 44])
        assertEquals(0x9F, name[2 + 45])
        assertEquals(0x98, name[2 + 46])
        val ascii = NativeHudProtocol.encode(state().copy(street = "a".repeat(80)))!!
        assertEquals(47, packet(ascii, 48)[1])
    }

    @Test fun missingNameIsASingleSpace() {
        val frame = NativeHudProtocol.encode(state().copy(street = null))!!
        assertEquals(listOf(48, 1, 32), packet(frame, 48))
        assertEquals(listOf(48, 1, 32), packet(NativeHudProtocol.encode(state().copy(street = " "))!!, 48))
    }

    @Test fun missingFieldsBecomeZeroInsteadOfOmittingPackets() {
        val frame = NativeHudProtocol.encode(NavigationState(
            active = true, maneuver = NavManeuver.UNKNOWN,
            distance = null, street = null, remainingDistance = null,
        ))!!
        assertEquals(listOf(48, 49, 51), frame.packets.map { it.first() })
        assertEquals(listOf(48, 1, 32), frame.packets[0])
        assertEquals(listOf(49, 5, 0, 0, 0, 0, 0), frame.packets[1])
        assertEquals(listOf(51, 4, 0, 0, 0, 0), frame.packets[2])
    }

    @Test fun activeFrameNeverCarriesTheEndArrowCode() {
        // -1 yalnız temizleme karesinde: aktif karede HUD rotayı bitmiş sayıyor.
        NavManeuver.values().forEach { m ->
            val frame = NativeHudProtocol.encode(state().copy(maneuver = m))!!
            for (id in listOf(49, 50)) {
                val p = frame.packets.firstOrNull { it.first() == id } ?: continue
                assertTrue("$m $id", p.subList(2, 4) != listOf(-1, -1))
            }
            assertTrue(m.name, frame.packets != NativeHudProtocol.blank().packets)
        }
        assertEquals(listOf(49, 5, -1, -1, 0, 0, 0), NativeHudProtocol.blank().packets[1])
    }

    @Test fun explicitEmptyRoadMatchesTheReferenceWithoutBecomingASpace() {
        assertEquals(listOf(48, 0), packet(NativeHudProtocol.encode(state().copy(street = ""))!!, 48))
        assertEquals(listOf(48, 2, 32, 32), packet(NativeHudProtocol.encode(state().copy(street = "  "))!!, 48))
    }

    @Test fun negativeMetersAreTreatedAsMissing() {
        val frame = NativeHudProtocol.encode(state(NavDistance("-1", "m", -1))
            .copy(remainingDistance = NavDistance("-1", "m", -1)))!!
        assertEquals(listOf(49, 5, 3, 0, 0, 0, 0), packet(frame, 49))
        assertEquals(listOf(51, 4, 0, 0, 0, 0), packet(frame, 51))
    }

    @Test fun inactiveStateHasNoNativeFrame() {
        assertNull(NativeHudProtocol.encode(state().copy(active = false)))
    }

    @Test fun roundaboutAndUnknownCodes() {
        assertEquals(11, packet(NativeHudProtocol.encode(state().copy(maneuver = NavManeuver.ROUNDABOUT_ENTER))!!, 49)[2])
        assertEquals(12, packet(NativeHudProtocol.encode(state().copy(maneuver = NavManeuver.ROUNDABOUT_EXIT))!!, 49)[2])
        val unknown = packet(NativeHudProtocol.encode(state().copy(maneuver = NavManeuver.UNKNOWN))!!, 49)
        assertEquals(listOf(0, 0), unknown.subList(2, 4))
        val waypoint = packet(NativeHudProtocol.encode(state().copy(maneuver = NavManeuver.WAYPOINT))!!, 49)
        assertEquals(listOf(0, 0), waypoint.subList(2, 4))
    }

    @Test fun roundaboutExitNumberUsesOemCodes() {
        fun code(m: NavManeuver, exit: Int?) =
            packet(NativeHudProtocol.encode(state().copy(maneuver = m, roundaboutExit = exit))!!, 49)[2]
        // SVVDSMap adapter.a.b: 28 + çıkış numarası (sağdan trafik).
        assertEquals(29, code(NavManeuver.ROUNDABOUT_ENTER, 1))
        assertEquals(31, code(NavManeuver.ROUNDABOUT_ENTER, 3))
        assertEquals(38, code(NavManeuver.ROUNDABOUT_ENTER, 10))
        assertEquals(31, code(NavManeuver.ROUNDABOUT_EXIT, 3))
        assertEquals(11, code(NavManeuver.ROUNDABOUT_ENTER, null))
        assertEquals(11, code(NavManeuver.ROUNDABOUT_ENTER, 0))
        assertEquals(11, code(NavManeuver.ROUNDABOUT_ENTER, 11))
        assertEquals(12, code(NavManeuver.ROUNDABOUT_EXIT, null))
        assertEquals(2, code(NavManeuver.LEFT, 3))
        // Ok ve mesafe paketi (50) aynı kodu taşır.
        val p50 = packet(NativeHudProtocol.encode(state().copy(maneuver = NavManeuver.ROUNDABOUT_ENTER, roundaboutExit = 3))!!, 50)
        assertEquals(31, p50[2])
    }

    @Test fun allManeuversUseReferenceArrowCodes() {
        val codes = listOf(9, 4, 5, 2, 3, 6, 7, 8, 19, 11, 12, 4, 5, 49, 15, 0, 0)
        NavManeuver.values().forEachIndexed { index, maneuver ->
            val frame = NativeHudProtocol.encode(state().copy(maneuver = maneuver, roundaboutExit = null))!!
            assertEquals(maneuver.name, codes[index], packet(frame, 49)[2])
        }
    }

    @Test fun providerIdentitySurvivesEncodingAndAffectsFrameEquality() {
        val google = NativeHudProtocol.encode(state())!!
        val yandex = NativeHudProtocol.encode(state().copy(sourcePackage = "ru.yandex.yandexmaps"))!!
        assertEquals(google.packets, yandex.packets)
        assertNotEquals(google, yandex)
    }
}
