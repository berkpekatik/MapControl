package com.mapcontrol.nav

import com.mapcontrol.launcher.NavDistance
import com.mapcontrol.launcher.NavManeuver
import com.mapcontrol.launcher.NavigationState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Expected packets were recorded from the original JCarTools J7 methods, not this encoder. */
class JCarToolsHudParityTest {
    @Test fun nativePacketsMatchOriginalJCarToolsJ7Sender() {
        val fixture = javaClass.getResourceAsStream("/hud/jcartools-j7-golden.json")!!
            .bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        val cases = fixture.getJSONArray("cases")
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            // "unknown" örneği gönderici fonksiyonuna -1 verildiğinde çıkan baytlardır. JCarTools -1'i aktif rotada yalnız
            // tanımadığı ok adında üretir; J7 HUD bunu bitiş sayıp sönüyor, bu yüzden bilinmeyen ok artık 0 (JCarTools'un
            // başlangıç değeri) gider. -1 baytları temizleme karesinde ([NativeHudProtocol.blank]) aynen kalır.
            if (case.getString("name") == "unknown") continue
            // "roundabout": JCarTools genel kavşakta 24 gönderir; OEM tablosunda 24 "kavşakta 360°" okudur ve HUD'da tam
            // tur gibi çizilir. Biz OEM'in genel kavşak kodu 11'i gönderiyoruz ([NativeHudProtocolTest]).
            if (case.getString("name") == "roundabout") continue
            val turn = case.getInt("turnMeters")
            val remaining = case.getInt("remainingMeters")
            val state = NavigationState(
                active = true,
                maneuver = NavManeuver.valueOf(case.getString("maneuver")),
                distance = NavDistance(turn.toString(), "m", turn),
                remainingDistance = NavDistance(remaining.toString(), "m", remaining),
                street = case.getString("street"),
            )
            val expected = case.getJSONArray("packets").let { packets ->
                (0 until packets.length()).map { p ->
                    packets.getJSONArray(p).let { values ->
                        (0 until values.length()).map { values.getInt(it) }
                    }
                }
            }
            // JCarTools'un üç paketi aynen gider; ardından J7 HUD'un ok/mesafe için okuduğu OEM 50/53 paketleri eklenir.
            val actual = listOf(NativeHudProtocol.start().toList()) + NativeHudProtocol.encode(state)!!.packets
                .filter { it.first() != 50 && it.first() != 53 }
            assertEquals(case.getString("name"), expected, actual)
        }
    }
}
