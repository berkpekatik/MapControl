package com.mapcontrol.launcher

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CarModelTest {

    private fun box(b: CarAnimSpec.Box) = floatArrayOf(b.x, b.y, b.w, b.h)

    /** Parçanın tüm ölçüleri tek dizide: sprite, gap, overlay, pivot, dönme, ölçek, öteleme, süreler, gölge, kenar ışığı. */
    private fun flat(p: CarAnimSpec.Part): FloatArray =
        box(p.sprite) + box(p.gap) + box(p.overlay) +
            floatArrayOf(p.pivotX, p.pivotY, p.rotationEnd, p.scaleXEnd, p.scaleYEnd, p.translateXEnd) +
            floatArrayOf(p.openMs.toFloat(), p.closeMs.toFloat(), p.xfadeStartMs.toFloat(), p.xfadeMs.toFloat(), p.closeXfadeMs.toFloat()) +
            (p.shadow?.let { box(it) } ?: floatArrayOf()) + (p.rim?.let { box(it) } ?: floatArrayOf())

    private fun same(expected: FloatArray, actual: FloatArray) = assertArrayEquals(expected, actual, 0f)

    @Test fun defaultModelIsTiggo() {
        assertSame(CarModel.TIGGO, CarModel.active)
        assertEquals("car_", CarModel.TIGGO.prefix)
        assertSame(CarModel.TIGGO.geometry.parts, CarAnimSpec.parts)
    }

    /** Tiggo ölçüleri model soyutlamasından önceki sabitlerle birebir aynı (1.5.1'deki CarAnimSpec / CarStageView / TrunkArt). */
    @Test fun tiggoGeometryEqualsUpstreamConstants() {
        val g = CarModel.TIGGO.geometry
        assertEquals(766f, CarAnimSpec.BASE_W, 0f)
        assertEquals(356f, CarAnimSpec.BASE_H, 0f)
        assertEquals(CarPart.values().toSet(), g.parts.keys)
        val doorMs = floatArrayOf(360f, 360f, 250f, 120f, 120f)
        same(floatArrayOf(350f, 16f, 169f, 56f, 349f, 15f, 171f, 59f, 336f, -64f, 253f, 134f, 522.73f, 20.25f, 37f, 0.825f, 0.85f, 0f) + doorMs +
            floatArrayOf(354.1f, 14.6f, 160.9f, 48.0f, 354.1f, 16.7f, 160.9f, 2.1f), flat(g.parts.getValue(CarPart.DOOR_FL)))
        same(floatArrayOf(169f, 22f, 186f, 50f, 167f, 22f, 190f, 52f, 124f, -76f, 239f, 146f, 356.88f, 25.89f, 45f, 0.775f, 0.95f, 0f) + doorMs +
            floatArrayOf(173.4f, 18.8f, 177.8f, 43.8f, 173.4f, 21.0f, 177.8f, 2.1f), flat(g.parts.getValue(CarPart.DOOR_RL)))
        same(floatArrayOf(350f, 283f, 169f, 61f, 349f, 281f, 172f, 65f, 336f, 287f, 253f, 146f, 522.73f, 333.59f, -45f, 0.775f, 1.15f, 0f) + doorMs +
            floatArrayOf(354.1f, 291.9f, 160.9f, 53.6f, 354.1f, 341.3f, 160.9f, 2.1f), flat(g.parts.getValue(CarPart.DOOR_FR)))
        same(floatArrayOf(169f, 283f, 187f, 57f, 167f, 281f, 190f, 59f, 124f, 287f, 239f, 146f, 351.24f, 327.24f, -45f, 0.775f, 0.85f, 0f) + doorMs +
            floatArrayOf(172.7f, 291.9f, 179.3f, 50.1f, 172.7f, 337.8f, 179.3f, 2.1f), flat(g.parts.getValue(CarPart.DOOR_RR)))
        same(floatArrayOf(546f, 41f, 220f, 275f, 544f, 39f, 222f, 278f, 541f, 31f, 220f, 302f, 590.47f, 177.62f, 0f, 0.42f, 1.05f, -42.34f,
            300f, 320f, 220f, 120f, 120f), flat(g.parts.getValue(CarPart.HOOD)))
        same(floatArrayOf(0f, 38f, 70f, 287f, 0f, 38f, 72f, 287f, -79f, 12f, 209f, 325f, 0f, 0f, 0f, 1f, 1f, -22.58f,
            260f, 260f, 0f, 0f, 0f), flat(g.parts.getValue(CarPart.TRUNK)))
        for (p in CarPart.values()) {
            val s = g.parts.getValue(p)
            val kind = when (p) { CarPart.HOOD -> CarAnimSpec.Kind.LID; CarPart.TRUNK -> CarAnimSpec.Kind.SLIDE_FADE; else -> CarAnimSpec.Kind.HINGE }
            assertEquals(p.name, kind, s.kind)
            assertSame(p.name, if (p == CarPart.TRUNK) CarAnimSpec.EASE_STANDARD else CarAnimSpec.EASE_OPEN, s.openEasing)
            assertSame(p.name, if (p == CarPart.TRUNK) CarAnimSpec.EASE_STANDARD else CarAnimSpec.EASE_CLOSE, s.closeEasing)
        }

        same(floatArrayOf(352f, 47f, 128f, 22f), box(g.windows.getValue(BodyPart.WINDOW_LF).box))
        same(floatArrayOf(205f, 47f, 126f, 22f), box(g.windows.getValue(BodyPart.WINDOW_LR).box))
        same(floatArrayOf(352f, 286f, 128f, 22f), box(g.windows.getValue(BodyPart.WINDOW_RF).box))
        same(floatArrayOf(205f, 286f, 126f, 22f), box(g.windows.getValue(BodyPart.WINDOW_RR).box))
        assertEquals(listOf(CarPart.DOOR_FL, CarPart.DOOR_RL, CarPart.DOOR_FR, CarPart.DOOR_RR),
            listOf(BodyPart.WINDOW_LF, BodyPart.WINDOW_LR, BodyPart.WINDOW_RF, BodyPart.WINDOW_RR).map { g.windows.getValue(it).door })
        same(floatArrayOf(163f, 96f, 268f, 156f), box(g.sunroof.box))
        assertNull(g.sunroof.door)
        assertEquals(0.6f, g.sunroof.strength, 0f)

        same(floatArrayOf(162f, 438f, 95f, 254f, 291f, 293f, 118f),
            floatArrayOf(g.roofX0, g.roofX1, g.roofY0, g.roofY1, g.roofSplit, g.roofFrontX0, g.roofGlassTravel))
        same(floatArrayOf(622f, 622f, 168f, 168f), g.tireX)
        same(floatArrayOf(26f, 337f, 23f, 338f), g.tireEdge)
        same(floatArrayOf(92f, 26f, 11f), floatArrayOf(g.tireL, g.tireW, g.tireOut))
        same(floatArrayOf(127f, 321f, 26f, 11f), box(g.fuelCap))
        same(floatArrayOf(655f, 178f, 118f, 132f), floatArrayOf(g.hoodCx, g.hoodCy, g.hoodRx, g.hoodRy))

        same(floatArrayOf(735f, -23f, 985f, 167f), g.beamTop)
        same(floatArrayOf(735f, 189f, 985f, 379f), g.beamBottom)
        same(floatArrayOf(-300f, -40f, 30f, 396f), g.brakePool)
        same(floatArrayOf(-160f, 88f, 40f, 268f), g.reverseFloor)
        same(floatArrayOf(690f, 36f, 772f, 104f), g.headTop)
        same(floatArrayOf(690f, 252f, 772f, 320f), g.headBottom)
        same(floatArrayOf(0f, 95f, 56f, 145f), g.reverseTop)
        same(floatArrayOf(0f, 211f, 56f, 261f), g.reverseBottom)
        same(floatArrayOf(690f, 17f, 760f, 87f), g.turnLeftFront)
        same(floatArrayOf(2f, 17f, 72f, 87f), g.turnLeftRear)
        same(floatArrayOf(690f, 269f, 760f, 339f), g.turnRightFront)
        same(floatArrayOf(2f, 269f, 72f, 339f), g.turnRightRear)
        same(floatArrayOf(-12f, 20f, 72f, 336f), g.tail)
        assertEquals(356f, g.bodyBottom, 0f)
        same(floatArrayOf(-60f, 6f, 200f, 350f), g.trunkCrop)

        // Görünüm payları: açık kapı / bagaj görsellerinin taşması + gölge bulanıklığı (eski hesapla aynı sonuç).
        same(floatArrayOf(79f, 76f + 14.2f, 0f, 77f + 14.2f), floatArrayOf(g.padLeft, g.padTop, g.padRight, g.padBottom))
    }

    /** [CarAnimSpec]'in modele bağlı her okuması etkin modelin ölçüsüdür. */
    @Test fun animSpecReadsActiveModel() {
        val g = CarModel.active.geometry
        same(floatArrayOf(g.roofX0, g.roofX1, g.roofY0, g.roofY1, g.roofSplit, g.roofFrontX0, g.roofGlassTravel),
            floatArrayOf(CarAnimSpec.ROOF_X0, CarAnimSpec.ROOF_X1, CarAnimSpec.ROOF_Y0, CarAnimSpec.ROOF_Y1, CarAnimSpec.ROOF_SPLIT,
                CarAnimSpec.ROOF_FRONT_X0, CarAnimSpec.ROOF_GLASS_TRAVEL))
        assertSame(g.tireX, CarAnimSpec.TIRE_X)
        assertSame(g.tireEdge, CarAnimSpec.TIRE_EDGE)
        same(floatArrayOf(g.tireL, g.tireW, g.tireOut), floatArrayOf(CarAnimSpec.TIRE_L, CarAnimSpec.TIRE_W, CarAnimSpec.TIRE_OUT))
        assertSame(g.fuelCap, CarAnimSpec.FUEL_CAP)
        same(floatArrayOf(g.hoodCx, g.hoodCy, g.hoodRx, g.hoodRy),
            floatArrayOf(CarAnimSpec.HOOD_CX, CarAnimSpec.HOOD_CY, CarAnimSpec.HOOD_RX, CarAnimSpec.HOOD_RY))
        assertSame(g.windows, CarAnimSpec.windows)
        assertSame(g.sunroof, CarAnimSpec.sunroof)
        same(floatArrayOf(g.padLeft, g.padTop, g.padRight, g.padBottom),
            floatArrayOf(CarAnimSpec.padLeft, CarAnimSpec.padTop, CarAnimSpec.padRight, CarAnimSpec.padBottom))
        // Lastik kutusu eski formülle: üsttekiler kenardan dışarı, alttakiler içeri.
        same(floatArrayOf(576f, 15f, 668f, 41f), CarAnimSpec.tireBox(0))
        same(floatArrayOf(122f, 323f, 214f, 349f), CarAnimSpec.tireBox(3))
    }

    /** Her modelin ölçüleri kendi içinde tutarlı: parçalar tam, paylar açık hal görsellerini kapsar, kutular tuvalin içinde. */
    @Test fun everyModelGeometryIsConsistent() {
        for (m in CarModel.values()) {
            val g = m.geometry
            val w = CarAnimSpec.BASE_W
            val h = CarAnimSpec.BASE_H
            assertEquals(m.key, CarPart.values().toSet(), g.parts.keys)
            for ((p, s) in g.parts) {
                val name = "${m.key} $p"
                assertTrue(name, s.overlay.x >= -g.padLeft && s.overlay.right <= w + g.padRight)
                assertTrue(name, s.overlay.y >= -g.padTop && s.overlay.bottom <= h + g.padBottom)
                assertEquals(name, p.isDoor, s.shadow != null && s.rim != null)
                // Üst (sol) kapılar yukarı, alt (sağ) kapılar aşağı döner.
                if (p == CarPart.DOOR_FL || p == CarPart.DOOR_RL) assertTrue(name, s.pivotY < h / 2 && s.rotationEnd > 0)
                if (p == CarPart.DOOR_FR || p == CarPart.DOOR_RR) assertTrue(name, s.pivotY > h / 2 && s.rotationEnd < 0)
            }
            assertTrue(m.key, g.parts.getValue(CarPart.HOOD).sprite.x > w / 2 && g.parts.getValue(CarPart.TRUNK).sprite.x < w / 4)
            assertEquals(m.key, setOf(BodyPart.WINDOW_LF, BodyPart.WINDOW_RF, BodyPart.WINDOW_LR, BodyPart.WINDOW_RR), g.windows.keys)
            for (glass in g.windows.values + g.sunroof) {
                assertTrue(m.key, glass.box.x >= 0f && glass.box.y >= 0f && glass.box.right <= w && glass.box.bottom <= h)
            }
            // Tavan: arka panel solda, ön panel sağda; ön panel tam açıkken cam kutusunun içinde kalır.
            assertTrue(m.key, 0f < g.roofX0 && g.roofX0 < g.roofSplit && g.roofSplit <= g.roofFrontX0 && g.roofFrontX0 < g.roofX1 && g.roofX1 < w)
            assertTrue(m.key, 0f < g.roofY0 && g.roofY0 < g.roofY1 && g.roofY1 < h)
            assertTrue(m.key, g.roofGlassTravel > 0f && g.roofFrontX0 - g.roofGlassTravel >= g.roofX0)
            assertTrue(m.key, g.sunroof.box.x >= g.roofX0 && g.sunroof.box.right <= g.roofX1)
            // Lastikler: ön çift burunda (sağ), arka çift arkada; gövdenin alt kenarı tuvalin içinde.
            assertTrue(m.key, g.tireX[0] == g.tireX[1] && g.tireX[2] == g.tireX[3] && g.tireX[0] > w / 2 && g.tireX[2] < w / 2)
            assertTrue(m.key, g.tireEdge[0] < h / 2 && g.tireEdge[1] > h / 2 && g.tireEdge[2] < h / 2 && g.tireEdge[3] > h / 2)
            assertTrue(m.key, g.bodyBottom > h / 2 && g.bodyBottom <= h)
            for (b in listOf(g.beamTop, g.beamBottom, g.brakePool, g.reverseFloor, g.headTop, g.headBottom, g.reverseTop, g.reverseBottom,
                g.turnLeftFront, g.turnLeftRear, g.turnRightFront, g.turnRightRear, g.tail, g.trunkCrop)) {
                assertEquals(m.key, 4, b.size)
                assertTrue(m.key, b[0] < b[2] && b[1] < b[3])
            }
        }
    }
}
