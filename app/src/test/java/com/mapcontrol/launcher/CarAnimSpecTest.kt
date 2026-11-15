package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CarAnimSpecTest {

    @Test fun partKeysAcceptCardAndAssetNames() {
        assertEquals(CarPart.DOOR_FL, CarPart.parse("lf"))
        assertEquals(CarPart.DOOR_FL, CarPart.parse("fl"))
        assertEquals(CarPart.DOOR_FL, CarPart.parse("door_fl"))
        assertEquals(CarPart.DOOR_RL, CarPart.parse("lr"))
        assertEquals(CarPart.DOOR_RL, CarPart.parse(" RL "))
        assertEquals(CarPart.DOOR_FR, CarPart.parse("rf"))
        assertEquals(CarPart.DOOR_FR, CarPart.parse("fr"))
        assertEquals(CarPart.DOOR_RR, CarPart.parse("rr"))
        assertEquals(CarPart.TRUNK, CarPart.parse("trunk"))
        assertEquals(CarPart.HOOD, CarPart.parse("hood"))
        assertNull(CarPart.parse("sunroof"))
    }

    @Test fun demoOpenListNormalisesToCardKeys() {
        assertEquals(setOf("lf", "lr", "rf", "rr", "trunk", "hood"), LauncherDemo.parseOpen("fl,rl,fr,rr,trunk,hood"))
        assertEquals(setOf("lf", "sunroof"), LauncherDemo.parseOpen("lf, sunroof,"))
        assertEquals(emptySet<String>(), LauncherDemo.parseOpen(""))
    }

    @Test fun doorSignalsMapToImageSides() {
        // Sol ön = sürücü kapısı = görselin üst kenarındaki ön kapı (door_fl).
        assertEquals(setOf(CarPart.DOOR_FL), CarPart.fromSignals(lf = true, rf = false, lr = false, rr = false, trunk = false, hood = false))
        assertEquals(setOf(CarPart.DOOR_FR), CarPart.fromSignals(lf = false, rf = true, lr = false, rr = false, trunk = false, hood = false))
        assertEquals(setOf(CarPart.DOOR_RL, CarPart.DOOR_RR, CarPart.TRUNK, CarPart.HOOD),
            CarPart.fromSignals(lf = false, rf = false, lr = true, rr = true, trunk = true, hood = true))
        // Üst (sol) kapılar yukarı, alt (sağ) kapılar aşağı açılır; ön kapılar arka kapılardan burna yakın.
        for (p in listOf(CarPart.DOOR_FL, CarPart.DOOR_RL)) {
            val s = CarAnimSpec.part(p)
            assertTrue(p.name, s.pivotY < CarAnimSpec.BASE_H / 2 && s.rotationEnd > 0 && s.overlay.y < 0)
        }
        for (p in listOf(CarPart.DOOR_FR, CarPart.DOOR_RR)) {
            val s = CarAnimSpec.part(p)
            assertTrue(p.name, s.pivotY > CarAnimSpec.BASE_H / 2 && s.rotationEnd < 0 && s.overlay.bottom > CarAnimSpec.BASE_H)
        }
        assertTrue(CarAnimSpec.part(CarPart.DOOR_FL).sprite.x > CarAnimSpec.part(CarPart.DOOR_RL).sprite.x)
        assertTrue(CarAnimSpec.part(CarPart.HOOD).sprite.x > CarAnimSpec.BASE_W / 2)
        assertTrue(CarAnimSpec.part(CarPart.TRUNK).sprite.x < CarAnimSpec.BASE_W / 4)
    }

    @Test fun specMatchesAnimJson() {
        assertEquals(CarPart.values().size, CarAnimSpec.parts.size)
        val fl = CarAnimSpec.part(CarPart.DOOR_FL)
        assertEquals(522.73f, fl.pivotX, 0.001f)
        assertEquals(37f, fl.rotationEnd, 0f)
        assertEquals(360L, fl.openMs)
        assertEquals(250L, fl.xfadeStartMs)
        val hood = CarAnimSpec.part(CarPart.HOOD)
        assertEquals(CarAnimSpec.Kind.LID, hood.kind)
        assertEquals(-42.34f, hood.translateXEnd, 0.001f)
        assertEquals(320L, hood.closeMs)
        val trunk = CarAnimSpec.part(CarPart.TRUNK)
        assertEquals(CarAnimSpec.Kind.SLIDE_FADE, trunk.kind)
        assertEquals(260L, trunk.openMs)
        // Görünüm payları her açık hal fotoğrafını kapsar.
        for (s in CarAnimSpec.parts.values) {
            assertTrue(s.overlay.x >= -CarAnimSpec.padLeft && s.overlay.right <= CarAnimSpec.BASE_W + CarAnimSpec.padRight)
            assertTrue(s.overlay.y >= -CarAnimSpec.padTop && s.overlay.bottom <= CarAnimSpec.BASE_H + CarAnimSpec.padBottom)
        }
    }

    @Test fun bezierHitsEndsAndOvershoots() {
        val open = CubicBezier.of(CarAnimSpec.EASE_OPEN)
        assertEquals(0f, open(0f), 0f)
        assertEquals(1f, open(1f), 0f)
        assertTrue((1..99).any { open(it / 100f) > 1f })
        val std = CubicBezier.of(CarAnimSpec.EASE_STANDARD)
        assertEquals(0.775f, std(0.5f), 0.02f) // material standard: hızlı başlar, yavaş biter
    }

    private fun motion(p: CarPart) = CarAnimSpec.part(p).let { PartMotion(it, CubicBezier.of(it.openEasing), CubicBezier.of(it.closeEasing)) }

    @Test fun doorOpensThenHandsOverToOverlay() {
        val m = motion(CarPart.DOOR_FL)
        m.snap(false)
        assertEquals(0f, m.overlayAlpha(), 0f)
        assertEquals(0f, m.gapAlpha(), 0f)
        val total = m.start(true)
        assertEquals(370L, total) // 250 ms + 120 ms geçiş
        m.update(120f)
        assertTrue(m.x > 0.5f && m.spriteVisible() && m.gapAlpha() > 0.99f && m.overlayAlpha() == 0f)
        m.update(total.toFloat())
        assertEquals(1f, m.x, 0.0001f)
        assertEquals(1f, m.overlayAlpha(), 0f)
        assertEquals(0f, m.spriteAlpha(), 0f)
        assertEquals(0f, m.gapAlpha(), 0f)
        m.start(false)
        m.update(1000f)
        assertEquals(0f, m.x, 0f)
        assertEquals(0f, m.overlayAlpha(), 0f)
        assertTrue(!m.spriteVisible())
    }

    @Test fun reversingMidwayContinuesFromCurrentState() {
        for (p in CarPart.values()) {
            val m = motion(p)
            m.snap(false)
            m.start(true)
            m.update(150f)
            val x = m.x
            val sprite = m.spriteAlpha()
            val total = m.start(false)
            m.update(0f)
            assertEquals(p.name, x, m.x, 0.0001f)
            assertEquals(p.name, sprite, m.spriteAlpha(), 0.0001f)
            // Kalan yol kısa: tam kapanıştan kısa sürer.
            assertTrue(p.name, total < CarAnimSpec.part(p).closeMs || x > 0.95f)
            m.update(total.toFloat())
            assertEquals(p.name, 0f, m.x, 0.0001f)
        }
    }

    @Test fun trunkSlidesAndFades() {
        val m = motion(CarPart.TRUNK)
        m.snap(false)
        val total = m.start(true)
        assertEquals(260L, total)
        m.update(total.toFloat())
        assertEquals(1f, m.x, 0f)
        assertEquals(1f, m.overlayAlpha(), 0f)
        assertEquals(1f, m.gapAlpha(), 0f)
        assertTrue(!m.spriteVisible())
    }

    @Test fun snapAppliesFinalStateWithoutMotion() {
        val m = motion(CarPart.HOOD)
        m.snap(true)
        assertEquals(1f, m.x, 0f)
        assertEquals(1f, m.overlayAlpha(), 0f)
        assertTrue(!m.spriteVisible())
    }

    @Test fun colourPrefKeysRoundTripAndFallBackToGraphite() {
        for (c in CarColor.values()) assertEquals(c, CarColor.fromKey(c.key))
        assertEquals(CarColor.GRAFIT, CarColor.fromKey(null))
        assertEquals(CarColor.GRAFIT, CarColor.fromKey("pembe"))
        assertEquals(listOf("grafit", "siyah", "beyaz", "gumus", "kirmizi", "mavi"), CarColor.values().map { it.key })
    }

    @Test fun everyColourShipsEveryLayer() {
        val dir = listOf(File("src/main/res/drawable-nodpi"), File("app/src/main/res/drawable-nodpi")).first { it.isDirectory }
        for (m in CarModel.values()) {
            for (c in CarColor.values()) {
                assertTrue("${m.key} ${c.key}", File(dir, "${m.prefix}${c.key}_base.webp").isFile)
                for (p in CarPart.values()) for (layer in listOf("sprite", "gap", "overlay")) {
                    assertTrue("${m.key} ${c.key} $layer ${p.asset}", File(dir, "${m.prefix}${c.key}_${layer}_${p.asset}.webp").isFile)
                }
            }
            for (roof in listOf("shade_closed", "shade_open", "open_hole")) {
                assertTrue("${m.key} $roof", File(dir, "${m.prefix}roof_$roof.webp").isFile)
            }
        }
    }

    @Test fun glassCoversEachWindowOnItsOwnDoorSide() {
        // Dört yan cam da tanımlı; sol camlar üst kenarda (aracın solu), sağ camlar alt kenarda; hepsi gövdenin içinde.
        assertEquals(setOf(BodyPart.WINDOW_LF, BodyPart.WINDOW_RF, BodyPart.WINDOW_LR, BodyPart.WINDOW_RR), CarAnimSpec.windows.keys)
        val all = CarAnimSpec.windows.values + CarAnimSpec.sunroof
        all.forEach { g ->
            assertTrue(g.box.x >= 0f && g.box.y >= 0f && g.box.right <= CarAnimSpec.BASE_W && g.box.bottom <= CarAnimSpec.BASE_H)
        }
        CarAnimSpec.windows.forEach { (w, g) ->
            val door = g.door!!
            assertTrue(door.isDoor)
            val gap = CarAnimSpec.part(door).gap
            // Cam, kapısının boşluğuyla aynı yanda ve aynı boyda (kapı açılınca boşluk camın üstüne biner).
            assertTrue("$w", g.box.x < gap.right && g.box.right > gap.x)
            val left = w == BodyPart.WINDOW_LF || w == BodyPart.WINDOW_LR
            assertEquals("$w", left, g.box.y < CarAnimSpec.BASE_H / 2)
        }
        assertNull(CarAnimSpec.sunroof.door)
    }

    @Test fun glassFadesInAndHidesWhileItsDoorOpens() {
        assertEquals(0f, CarAnimSpec.glassAlpha(0f, 0f), 0f)
        assertEquals(CarAnimSpec.GLASS_MAX_ALPHA, CarAnimSpec.glassAlpha(1f, 0f), 1e-6f)
        assertEquals(CarAnimSpec.GLASS_MAX_ALPHA / 2, CarAnimSpec.glassAlpha(1f, 0.5f), 1e-6f)
        assertEquals(0f, CarAnimSpec.glassAlpha(1f, 1f), 0f)
        assertEquals(0f, CarAnimSpec.glassAlpha(1f, 1.05f), 0f) // kapı ucu hafif taşar: eksiye düşmez
        assertTrue(CarAnimSpec.GLASS_MAX_ALPHA <= 0.4f) // "hafif" renk
    }
}
