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

    @Test fun modelsUseDistinctResourcePrefixes() {
        assertEquals(listOf("car_", "car_j7_", "car_t8_"), CarModel.values().map { it.prefix })
        assertEquals(CarModel.values().size, CarModel.values().map { it.key }.toSet().size)
        // Bir modelin ön eki diğerinin renk dosyalarıyla çakışmaz ("car_j7_" hiçbir "car_<renk>_" ile başlamaz).
        for (c in CarColor.values()) assertTrue(c.key, !"car_j7_".startsWith("car_${c.key}_") && !c.key.startsWith("j7"))
        for (c in CarColor.values()) assertTrue(c.key, !"car_t8_".startsWith("car_${c.key}_") && !c.key.startsWith("t8"))
    }

    /** Jaecoo 7 ölçüleri emülatörde ayarlanan deneme değerleridir; ana noktalar sabitlenir. */
    @Test fun jaecooGeometryKeepsTunedValues() {
        val g = CarModel.JAECOO7.geometry
        same(floatArrayOf(366f, 26f, 148f, 73f, 365f, 51f, 150f, 47f, 364f, -1f, 152f, 100f, 503.96f, 61.26f, 24f, 0.925f, 0.9f, 0f,
            360f, 360f, 250f, 120f, 120f, 370.2f, 50.6f, 139.8f, 38.0f, 370.2f, 52.7f, 139.8f, 2.1f), flat(g.parts.getValue(CarPart.DOOR_FL)))
        same(floatArrayOf(528f, 65f, 144f, 227f, 527f, 64f, 146f, 227f, 515f, 44f, 157f, 259f, 529f, 216.5f, 0f, 0.22f, 1.132f, -6f,
            300f, 320f, 140f, 160f, 100f), flat(g.parts.getValue(CarPart.HOOD)))
        same(floatArrayOf(85f, 86f, 63f, 184f, 84f, 85f, 65f, 186f, -20f, 65f, 191f, 228f, 0f, 0f, 0f, 1f, 1f, -40f,
            260f, 260f, 0f, 0f, 0f), flat(g.parts.getValue(CarPart.TRUNK)))
        same(floatArrayOf(218.47f, 399.68f, 112.31f, 242.23f, 326f, 329.70f, 57.4f),
            floatArrayOf(g.roofX0, g.roofX1, g.roofY0, g.roofY1, g.roofSplit, g.roofFrontX0, g.roofGlassTravel))
        same(floatArrayOf(568f, 568f, 206f, 206f), g.tireX)
        same(floatArrayOf(53f, 302f, 53f, 302f), g.tireEdge)
        same(floatArrayOf(73f, 60f, 157f, 295f), g.tail)
        assertEquals(329f, g.bodyBottom, 0f)
        same(floatArrayOf(-24f, 20f, 214f, 335f), g.trunkCrop)
        // Dış ayna yalnız ön kapı sprite'larında.
        assertEquals(setOf(CarPart.DOOR_FL, CarPart.DOOR_FR), g.parts.filterValues { it.mirror }.keys)
        val gate = g.tailGate!!
        same(floatArrayOf(89f, 269f, -105f), floatArrayOf(gate.cut0, gate.cut1, gate.reverseDx))
        same(floatArrayOf(-52f, 62f, 32f, 288f), gate.openBox)
        assertTrue(g.brakePoolSoftEdge)
    }

    /**
     * Açık kapı görselleri: kutu, açılan kapının ucunu da kapsayacak kadar dışa taşar (uç düz kesilmez) ve
     * komşu kapının kapalı görseline girmez (arka kapının kutusu ön kapının başladığı yerde biter); sağ ve sol taraf simetriktir.
     */
    @Test fun jaecooDoorOverlaysCoverTheSwungDoorAndStayOffTheNeighbour() {
        val p = CarModel.JAECOO7.geometry.parts
        val fl = p.getValue(CarPart.DOOR_FL); val rl = p.getValue(CarPart.DOOR_RL)
        val fr = p.getValue(CarPart.DOOR_FR); val rr = p.getValue(CarPart.DOOR_RR)
        for ((rear, front) in listOf(rl to fl, rr to fr)) {
            assertTrue(rear.overlay.right <= front.sprite.x + 4f)
            assertTrue(front.overlay.x >= rear.sprite.right - 4f)
        }
        // Gövde üstte y=26, altta y=329'da biter: açık kapı en az 20 px dışarı uzanır.
        for (d in listOf(fl, rl)) assertTrue(d.overlay.y <= 26f - 20f)
        for (d in listOf(fr, rr)) assertTrue(d.overlay.bottom >= 329f + 20f)
        assertEquals(fl.overlay.h, fr.overlay.h, 0f)
        assertEquals(rl.overlay.h, rr.overlay.h, 0f)
        assertEquals(fl.overlay.w, fr.overlay.w, 0f)
        // Sprite'ın son duruşu açık görsele oturtuldu: iki yanın açısı ve ölçeği birbirinin aynası (yarım derece içinde).
        assertEquals(fl.rotationEnd, -fr.rotationEnd, 0.6f)
        assertEquals(rl.rotationEnd, -rr.rotationEnd, 0.6f)
    }

    /**
     * Kaput: sprite'ın son duruşu açık görseldeki kalkık kaputun boyuna oturur (görselin üst ve alt kenarına 3 px içinde; geçişte
     * boy sıçramaz) ve kapanışta motor görseli en geç 120 ms'de söner (genişleyen kaputun üstünde yarı saydam motor kalmaz).
     */
    @Test fun jaecooHoodSpriteEndsOnTheRaisedLid() {
        val h = CarModel.JAECOO7.geometry.parts.getValue(CarPart.HOOD)
        val top = h.pivotY + (h.sprite.y - h.pivotY) * h.scaleYEnd
        val bottom = h.pivotY + (h.sprite.bottom - h.pivotY) * h.scaleYEnd
        assertEquals(h.overlay.y, top, 3f)
        assertEquals(h.overlay.bottom, bottom, 3f)
        assertTrue(h.closeXfadeMs <= 120)
        assertTrue(h.xfadeStartMs + h.xfadeMs <= h.openMs)
    }

    /** Kalkık kapaktaki çubuğun kutusu açık bagaj görselinin içinde, kapalı çubuğun gerisindedir; kesimler çubuk kutusunun içinde. */
    @Test fun gateBarBoxLiesOnTheOpenTrunkOverlay() {
        for (m in CarModel.values()) {
            val g = m.geometry
            val gate = g.tailGate ?: continue
            val ov = g.parts.getValue(CarPart.TRUNK).overlay
            val b = gate.openBox
            assertEquals(m.key, 4, b.size)
            assertTrue(m.key, b[0] < b[2] && b[1] < b[3])
            // LED çizgisi görselin arka (sol) kenarındadır: kutu o kenarı içerir ve gövdedeki çubuğun gerisinde kalır.
            assertTrue(m.key, b[0] <= ov.x && ov.x < b[2] && b[2] < g.tail[0] + (g.tail[2] - g.tail[0]) / 2f)
            assertTrue(m.key, b[1] >= ov.y - 8f && b[3] <= ov.bottom + 8f)
            assertTrue(m.key, gate.reverseDx < 0f)
        }
    }

    /** Jaecoo'ya özgü çizim seçenekleri Tiggo'da kapalıdır (1.5.1 çizimi değişmez). */
    @Test fun jaecooOnlyDrawingOptionsAreOffForTiggo() {
        val t = CarModel.TIGGO.geometry
        assertNull(t.tailGate)
        assertTrue(!t.brakePoolSoftEdge)
        assertTrue(t.parts.values.none { it.mirror })
    }

    /** Tiggo 8 ölçüleri görsel takımının dışa aktarımından gelir, emülatörde ayarlandı; ana noktalar sabitlenir. */
    @Test fun tiggo8GeometryKeepsTunedValues() {
        val g = CarModel.TIGGO8.geometry
        assertEquals("car_t8_", CarModel.TIGGO8.prefix)
        same(floatArrayOf(383f, 37f, 133f, 58f, 382f, 59f, 135f, 36f, 382f, -25f, 144f, 120f, 507.65f, 83.45f, 37.5f, 1.09f, 1.24f, 0f,
            360f, 360f, 250f, 120f, 120f, 387.2f, 58.6f, 124.8f, 27f, 387.2f, 60.7f, 84f, 2.1f), flat(g.parts.getValue(CarPart.DOOR_FL)))
        same(floatArrayOf(257f, 261f, 129f, 39f, 258f, 260f, 129f, 40f, 231f, 259f, 155f, 121f, 390.44f, 305.46f, -40f, 0.96f, 1.24f, 0f,
            360f, 360f, 250f, 120f, 120f, 263.2f, 269.8f, 118.8f, 31f, 293f, 292.9f, 89f, 2.1f), flat(g.parts.getValue(CarPart.DOOR_RR)))
        same(floatArrayOf(532f, 70f, 129f, 215f, 531f, 69f, 130f, 216f, 511f, 69f, 150f, 217f, 553.7f, 177.27f, 0f, 0.24f, 1f, -12.09f,
            300f, 320f, 220f, 120f, 120f), flat(g.parts.getValue(CarPart.HOOD)))
        same(floatArrayOf(83f, 94f, 103f, 165f, 83f, 95f, 104f, 165f, 12f, 85f, 174f, 185f, 0f, 0f, 0f, 1f, 1f, -40f,
            260f, 260f, 0f, 0f, 0f), flat(g.parts.getValue(CarPart.TRUNK)))
        same(floatArrayOf(223.23f, 425.33f, 108.99f, 245.54f, 279.05f, 288.12f, 64.49f),
            floatArrayOf(g.roofX0, g.roofX1, g.roofY0, g.roofY1, g.roofSplit, g.roofFrontX0, g.roofGlassTravel))
        same(floatArrayOf(565.33f, 565.33f, 221.6f, 221.6f), g.tireX)
        same(floatArrayOf(58.83f, 296.17f, 56.04f, 298.96f), g.tireEdge)
        same(floatArrayOf(92f, 24f, 7f), floatArrayOf(g.tireL, g.tireW, g.tireOut))
        // Yakıt kapağı sol arka çamurlukta (görünüşte üst kenar), arka lastiğin gerisinde: lastik uyarısıyla tek blok olmaz.
        same(floatArrayOf(145.81f, 68.94f, 26f, 11f), box(g.fuelCap))
        assertTrue(g.fuelCap.right < CarAnimSpec.tireBox(2, g)[0])
        same(floatArrayOf(-214f, 27.27f, 116f, 327.27f), g.brakePool)
        same(floatArrayOf(-26.53f, 93.27f, 173.47f, 261.27f), g.reverseFloor)
        // Ön cam vurgusu A direğine doğru daralan kısma girmez (kısa kutu).
        same(floatArrayOf(393.7f, 79.0f, 62.0f, 10.4f), box(g.windows.getValue(BodyPart.WINDOW_LF).box))
        same(floatArrayOf(71.47f, 58f, 155.47f, 296.5f), g.tail)
        assertEquals(318f, g.bodyBottom, 0f)
        same(floatArrayOf(8f, 20.27f, 238.81f, 334.27f), g.trunkCrop)
        // Jaecoo 7 ile aynı seçenekler: dış ayna ön kapı sprite'larında, ışık çubuğunun ortası bagaj kapağında, fren ışığı yumuşak kenarlı.
        assertEquals(setOf(CarPart.DOOR_FL, CarPart.DOOR_FR), g.parts.filterValues { it.mirror }.keys)
        val gate = g.tailGate!!
        same(floatArrayOf(96.45f, 258.08f, -70f, -70f), floatArrayOf(gate.cut0, gate.cut1, gate.reverseDx, gate.brakeDx))
        same(floatArrayOf(-24f, 90f, 60f, 265f), gate.openBox)
        assertTrue(g.brakePoolSoftEdge)
        // Sol ve sağ kapılar orta çizgiye (y = 177,27) göre simetrik.
        for ((l, r) in listOf(CarPart.DOOR_FL to CarPart.DOOR_FR, CarPart.DOOR_RL to CarPart.DOOR_RR)) {
            val a = g.parts.getValue(l)
            val b = g.parts.getValue(r)
            assertEquals(a.pivotX, b.pivotX, 0f)
            assertEquals(2 * 177.27f, a.pivotY + b.pivotY, 0.02f)
            assertEquals(a.rotationEnd, -b.rotationEnd, 0f)
            same(floatArrayOf(a.scaleXEnd, a.scaleYEnd), floatArrayOf(b.scaleXEnd, b.scaleYEnd))
        }
    }

    /** Tiggo 8'e özgü seçenekler: cam tavan payı ve gerçek boy oranı (gövde boyu Tiggo 7'ninkine, sonra gerçek boya oranlanır). */
    @Test fun tiggo8OnlyOptionsKeepTunedValues() {
        val t7 = CarModel.TIGGO.geometry
        val t8 = CarModel.TIGGO8.geometry
        assertEquals(5.8f, t8.roofGlassInsetY, 0f)
        // Pay cam kutusunu tüketmez (kayan panele yer kalır).
        assertTrue(2 * t8.roofGlassInsetY < (t8.roofY1 - t8.roofY0) / 4f)
        val size = t8.bodySize!!
        same(floatArrayOf(83f, 683f, 4722f), floatArrayOf(size.x0, size.x1, size.lengthMm))
        assertEquals(764f / 600f, t8.bodyScale, 0.0005f)
        assertEquals(764f / 600f * 4722f / 4500f, t8.stageScale, 0.0005f)
        // Aynı çerçevede gövde boyu (px) = ölçek × tuvaldeki gövde boyu: Tiggo 8 / Tiggo 7 = gerçek boy oranı.
        val t7Body = t7.stageScale * BodySize.REF_PX
        assertEquals(4722f / 4500f, t8.stageScale * (size.x1 - size.x0) / t7Body, 0.0005f)
    }

    /**
     * Tiggo 8 için eklenen her çizim seçeneği Tiggo 7 ve Jaecoo 7'de kapalıdır: ölçekler tam 1 (kayan nokta hatası da yok), cam
     * tavan payı 0. Bu ikisinde çizim, seçenekler eklenmeden önceki kodla aynı sayılarla yapılır.
     */
    @Test fun tiggo8OnlyDrawingOptionsAreOffForTiggoAndJaecoo() {
        for (m in listOf(CarModel.TIGGO, CarModel.JAECOO7)) {
            val g = m.geometry
            assertNull(m.key, g.bodySize)
            assertEquals(m.key, 1f, g.bodyScale, 0f)
            assertEquals(m.key, 1f, g.stageScale, 0f)
            assertEquals(m.key, 0f, g.roofGlassInsetY, 0f)
            // Ölçek 1 iken yerleşim yardımcıları girdiyi olduğu gibi verir (aynı nesne / aynı bit).
            val box = StageFit.Box(101.3f, 57.7f, 913.9f, 435.1f)
            assertSame(m.key, box, StageFit.scaled(box, g.stageScale))
            assertSame(m.key, box, StageFit.scaled(box, g.bodyScale))
            val trunkX = g.parts.getValue(CarPart.TRUNK).overlay.x
            assertEquals(m.key, -trunkX / CarAnimSpec.BASE_W, StageFit.frameOverflowLeft(trunkX / CarAnimSpec.BASE_W, g.stageScale), 0f)
            assertEquals(m.key, 0f, StageFit.frameOverflowRight(1f, g.stageScale), 0f)
        }
        // Seçenekler yalnız Tiggo 8'de açık.
        assertEquals(listOf(CarModel.TIGGO8), CarModel.values().filter { it.geometry.bodySize != null })
        assertEquals(listOf(CarModel.TIGGO8), CarModel.values().filter { it.geometry.roofGlassInsetY != 0f })
    }

    /** Kapı sprite'ının açık haldeki dönüşümü ([CarStageView.drawSprite] ile aynı sıra: pivot · dönme · ölçek). */
    private fun swung(p: CarAnimSpec.Part, x: Float, y: Float): FloatArray {
        val dx = (x - p.pivotX) * p.scaleXEnd
        val dy = (y - p.pivotY) * p.scaleYEnd
        val r = Math.toRadians(p.rotationEnd.toDouble())
        val c = Math.cos(r).toFloat()
        val s = Math.sin(r).toFloat()
        return floatArrayOf(p.pivotX + dx * c - dy * s, p.pivotY + dx * s + dy * c)
    }

    /**
     * Tiggo 8 kapı kenar ışığı sprite'ın dış kenar satırındadır (grafit sprite'lardan ölçüldü: üst kapılarda dış kenar y = 60,
     * alt kapılarda son dolu satır 294; arka kapının arka ucu x < 293'te içe kıvrılır) ve aynalı kapıda aynadan (x ≥ 474) önce biter.
     */
    @Test fun tiggo8DoorRimsLieOnTheDoorEdgeAndStopBeforeTheMirror() {
        val g = CarModel.TIGGO8.geometry
        val edgeTop = 60f
        val edgeBottom = 295f
        val mirrorX0 = 474f
        for ((p, s) in g.parts.filterKeys { it.isDoor }) {
            val rim = s.rim!!
            val upper = s.pivotY < CarAnimSpec.BASE_H / 2
            if (upper) assertTrue("$p", rim.y >= edgeTop - 0.01f && rim.y <= edgeTop + 1f)
            else assertTrue("$p", rim.bottom <= edgeBottom + 0.01f && rim.bottom >= edgeBottom - 1f)
            assertEquals("$p", 2.1f, rim.h, 0f)
            assertTrue("$p", rim.x >= s.sprite.x && rim.right <= s.sprite.right)
            if (s.mirror) assertTrue("$p", rim.right < mirrorX0) else assertTrue("$p", rim.x >= 293f)
        }
        // Sol ve sağ kenar ışıkları orta çizgiye göre simetrik (aynı x, y yansımış).
        for ((l, r) in listOf(CarPart.DOOR_FL to CarPart.DOOR_FR, CarPart.DOOR_RL to CarPart.DOOR_RR)) {
            val a = g.parts.getValue(l).rim!!
            val b = g.parts.getValue(r).rim!!
            same(floatArrayOf(a.x, a.w), floatArrayOf(b.x, b.w))
            // Görsel tam piksele oturur: dış kenar satırları 60 ve 295 (toplam 355; orta çizginin iki katı 354,54).
            assertEquals(2 * 177.27f, a.y + b.bottom, 0.6f)
        }
    }

    /**
     * Tiggo 8 arka kapıları: açılan sprite'ın dış kenarının ucu, açık kapı görselindeki dış kenarın ucuna oturur (grafit görselden
     * ölçüldü: sol arka (291, -22); sağ arka orta çizgiye göre yansıması). Geçişte iki kapı V biçiminde ayrışmasın.
     */
    @Test fun tiggo8RearDoorSwingEndsOnTheOpenDoorArt() {
        val g = CarModel.TIGGO8.geometry
        val rl = g.parts.getValue(CarPart.DOOR_RL)
        val rr = g.parts.getValue(CarPart.DOOR_RR)
        val tipL = swung(rl, 263f, 56f)
        val tipR = swung(rr, 263f, 2 * 177.27f - 56f)
        assertTrue("rl ${tipL.toList()}", Math.hypot((tipL[0] - 291f).toDouble(), (tipL[1] + 22f).toDouble()) <= 4.0)
        assertTrue("rr ${tipR.toList()}", Math.hypot((tipR[0] - 291f).toDouble(), (tipR[1] - (2 * 177.27f + 22f)).toDouble()) <= 4.0)
        // Açık hal görselinin kutusu dönen kapının ucunu kapsar.
        assertTrue(tipL[0] >= rl.overlay.x && tipL[1] >= rl.overlay.y - 1f)
        assertTrue(tipR[0] >= rr.overlay.x && tipR[1] <= rr.overlay.bottom + 1f)
    }

    /**
     * Stop çubuğunun uç sönümü: Tiggo 8'de kutu merceğin dışına taşar, kesimlerde (gövdedeki uç parçaların iç ucu) çubuk tam güçtedir.
     * Tiggo 7 ve Jaecoo 7 kutuları ve ortak sönüm boyu değişmedi.
     */
    @Test fun tiggo8TailBarIsAtFullStrengthAtTheGateCuts() {
        val g = CarModel.TIGGO8.geometry
        val gate = g.tailGate!!
        val rows = (g.tail[3] - g.tail[1]).toInt()
        val y0 = g.tail[1].toInt()
        assertEquals(1f, CarAnimSpec.tailTaper(gate.cut0.toInt() - y0, rows), 0f)
        assertEquals(1f, CarAnimSpec.tailTaper(gate.cut1.toInt() + 1 - y0, rows), 0f)
        // Merceğin ucunda (y ≈ 78 / 276) yarıdan fazla, kutunun ucunda 0.
        assertTrue(CarAnimSpec.tailTaper(78 - y0, rows) > 0.5f && CarAnimSpec.tailTaper(276 - y0, rows) > 0.5f)
        assertEquals(0f, CarAnimSpec.tailTaper(0, rows), 0f)
        assertEquals(0f, CarAnimSpec.tailTaper(rows - 1, rows), 0f)
        assertEquals(2 * 177.27f, g.tail[1] + g.tail[3], 0.05f)
        assertEquals(34f, CarAnimSpec.TAIL_TAPER, 0f)
        same(floatArrayOf(-12f, 20f, 72f, 336f), CarModel.TIGGO.geometry.tail)
        same(floatArrayOf(73f, 60f, 157f, 295f), CarModel.JAECOO7.geometry.tail)
    }

    /** Tiggo 8 seçenekleri diğer modellerde kapalıdır: fren ışığı kayması, iki parçalı cam vurgusu, cam satırları. */
    @Test fun tiggo8ReviewOptionsAreOffForTiggoAndJaecoo() {
        assertEquals(0f, CarModel.JAECOO7.geometry.tailGate!!.brakeDx, 0f)
        same(floatArrayOf(89f, 269f, -105f), CarModel.JAECOO7.geometry.tailGate!!.let { floatArrayOf(it.cut0, it.cut1, it.reverseDx) })
        for (m in listOf(CarModel.TIGGO, CarModel.JAECOO7)) {
            val g = m.geometry
            assertTrue(m.key, (g.windows.values + g.sunroof).all { it.box2 == null })
            // Cam satırları katman kutusunun kendisi (aynı bit): perde kenarı ve kenar ışığı eski yerinde.
            assertEquals(m.key, g.roofY0, g.roofGlassY0, 0f)
            assertEquals(m.key, g.roofY1, g.roofGlassY1, 0f)
            assertEquals(m.key, g.roofX1, g.roofGlassX1, 0f)
            assertEquals(m.key, 0f, g.roofGlassInsetFront, 0f)
        }
        val t8 = CarModel.TIGGO8.geometry
        assertEquals(t8.roofY0 + 5.8f, t8.roofGlassY0, 0f)
        assertEquals(t8.roofY1 - 5.8f, t8.roofGlassY1, 0f)
        assertEquals(t8.roofX1 - 4.5f, t8.roofGlassX1, 0f)
        assertTrue(t8.tailGate!!.brakeDx < 0f)
        // Yedek cam tavan vurgusu: iki cam, çerçeve payının içinde, aradaki kiriş (roofSplit..roofFrontX0) boş.
        val a = t8.sunroof.box
        val b = t8.sunroof.box2!!
        assertTrue(a.y >= t8.roofGlassY0 && a.bottom <= t8.roofGlassY1 + 0.01f && b.y == a.y && b.h == a.h)
        assertTrue(a.right <= t8.roofSplit && b.x >= t8.roofFrontX0 && b.right <= t8.roofX1)
        assertTrue(t8.windows.values.all { it.box2 == null })
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
            g.tailGate?.let { assertTrue(m.key, g.tail[1] < it.cut0 && it.cut0 < it.cut1 && it.cut1 < g.tail[3]) }
        }
    }

    /** Aynalı kapının sprite'ı kapalıyken de çizilir (base aynasız); aynasız kapınınki yalnız açılırken. */
    @Test fun mirrorDoorSpriteStaysVisibleWhenClosed() {
        fun closed(model: CarModel, p: CarPart) = model.geometry.parts.getValue(p)
            .let { PartMotion(it, CubicBezier.of(it.openEasing), CubicBezier.of(it.closeEasing)) }.apply { snap(false) }
        assertTrue(closed(CarModel.JAECOO7, CarPart.DOOR_FL).spriteVisible())
        assertTrue(closed(CarModel.JAECOO7, CarPart.DOOR_FR).spriteVisible())
        assertTrue(!closed(CarModel.JAECOO7, CarPart.DOOR_RL).spriteVisible())
        for (p in CarPart.values()) assertTrue(p.name, !closed(CarModel.TIGGO, p).spriteVisible())
        for (p in CarPart.values()) assertEquals(p.name, p == CarPart.DOOR_FL || p == CarPart.DOOR_FR, closed(CarModel.TIGGO8, p).spriteVisible())
        // Açık hal fotoğrafına geçince aynalı sprite da söner.
        val open = closed(CarModel.JAECOO7, CarPart.DOOR_FL).apply { snap(true) }
        assertTrue(!open.spriteVisible())
    }

    @Test fun automaticPicksModelFromVehicleScreen() {
        assertEquals(CarModel.TIGGO, CarModel.choose(CarModel.OVERRIDE_AUTO, VehicleProfile.TIGGO7))
        assertEquals(CarModel.TIGGO8, CarModel.choose(CarModel.OVERRIDE_AUTO, VehicleProfile.TIGGO8))
        assertEquals(CarModel.JAECOO7, CarModel.choose(CarModel.OVERRIDE_AUTO, VehicleProfile.PORTRAIT))
    }

    @Test fun manualChoiceWinsOverVehicleScreen() {
        for (p in VehicleProfile.values()) {
            assertEquals(p.name, CarModel.TIGGO, CarModel.choose(1, p))
            assertEquals(p.name, CarModel.JAECOO7, CarModel.choose(2, p))
            assertEquals(p.name, CarModel.TIGGO8, CarModel.choose(3, p))
            // Bilinmeyen değer Otomatik gibi davranır.
            assertEquals(p.name, CarModel.choose(CarModel.OVERRIDE_AUTO, p), CarModel.choose(7, p))
            assertEquals(p.name, CarModel.choose(CarModel.OVERRIDE_AUTO, p), CarModel.choose(-1, p))
        }
        assertEquals(CarModel.values().size, CarModel.OVERRIDE_MAX)
    }

    @Test fun demoNameParsesToModel() {
        assertEquals(CarModel.TIGGO, CarModel.parse("tiggo"))
        assertEquals(CarModel.JAECOO7, CarModel.parse(" Jaecoo7 "))
        assertEquals(CarModel.TIGGO8, CarModel.parse("tiggo8"))
        assertNull(CarModel.parse("tiggo7"))
        assertNull(CarModel.parse(null))
    }
}
