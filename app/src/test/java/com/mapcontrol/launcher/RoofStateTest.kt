package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Panoramik tavan: ham değer çözümü, perde konum tahmini, çizim geometrisi ve demo ekstrası. */
class RoofStateTest {

    private val eps = 1e-4f

    @Test
    fun shadeDecodesMeasuredStates() {
        assertEquals(RoofDecode.Motion.CLOSED, RoofDecode.shade(1)?.motion)
        assertEquals(RoofDecode.Motion.OPENING, RoofDecode.shade(2)?.motion)
        assertEquals(RoofDecode.Motion.STOPPED, RoofDecode.shade(3)?.motion)
        assertEquals(RoofDecode.Motion.CLOSING, RoofDecode.shade(4)?.motion)
        assertEquals(RoofDecode.Motion.OPEN, RoofDecode.shade(5)?.motion)
        assertEquals(RoofDecode.Motion.STOPPED, RoofDecode.shade(6)?.motion)
        // Desteklenmiyor / gelmedi / geçersiz: bilinmiyor.
        assertNull(RoofDecode.shade(7))
        assertNull(RoofDecode.shade(0))
        assertNull(RoofDecode.shade(null))
        assertNull(RoofDecode.shade(Int.MIN_VALUE))
        assertNull(RoofDecode.shade(-3))
        assertNull(RoofDecode.shade(255))
        // Yüzde gelen modeller: 8..100.
        assertEquals(RoofDecode.Motion.PERCENT, RoofDecode.shade(40)?.motion)
        assertEquals(0.4f, RoofDecode.shade(40)!!.fraction, eps)
    }

    @Test
    fun glassDecodesPosiThenOperate() {
        assertEquals(RoofDecode.Glass(0f), RoofDecode.glass(1, 2))
        assertEquals(RoofDecode.Glass(0f, tilt = true), RoofDecode.glass(12, null))
        assertEquals(0.4f, RoofDecode.glass(5, null)!!.fraction, eps)
        assertEquals(1f, RoofDecode.glass(11, null)!!.fraction, eps)
        assertEquals(0.55f, RoofDecode.glass(55, null)!!.fraction, eps)
        assertEquals(1f, RoofDecode.glass(100, null)!!.fraction, eps)
        // Konum okunamazsa SPF_OPERATE: 3/6/7 açık, 2/5 kapalı, 4 hareket = bilinmiyor.
        assertEquals(RoofDecode.Glass(1f), RoofDecode.glass(0, 3))
        assertEquals(RoofDecode.Glass(0f), RoofDecode.glass(null, 5))
        assertNull(RoofDecode.glass(0, 4))
        assertNull(RoofDecode.glass(300, 0))
        assertNull(RoofDecode.glass(null, null))
    }

    @Test
    fun edgeAlertNeedsOpenRoofAndHasHysteresis() {
        assertFalse(RoofDecode.edgeAlert(open = false, speedKmh = 130, raining = true, wasOn = false))
        assertTrue(RoofDecode.edgeAlert(open = true, speedKmh = 10, raining = true, wasOn = false))
        assertFalse(RoofDecode.edgeAlert(open = true, speedKmh = 85, raining = false, wasOn = false))
        assertTrue(RoofDecode.edgeAlert(open = true, speedKmh = 90, raining = false, wasOn = false))
        assertTrue(RoofDecode.edgeAlert(open = true, speedKmh = 85, raining = false, wasOn = true))
        assertFalse(RoofDecode.edgeAlert(open = true, speedKmh = 79, raining = false, wasOn = true))
        assertFalse(RoofDecode.edgeAlert(open = true, speedKmh = null, raining = false, wasOn = true))
    }

    @Test
    fun trackerEstimatesShadeFromMotionTime() {
        val t = RoofTracker(openMs = 10_000, closeMs = 20_000)
        val closed = t.update(1, 1, 2, 0, false, 0)!!
        assertEquals(0f, closed.shade!!, eps)
        assertEquals(0L, closed.shadeMs)
        // Açılıyor: hedef tam açık, kalan süre tam açılma süresi.
        val opening = t.update(2, 1, 2, 0, false, 1_000)!!
        assertEquals(1f, opening.shade!!, eps)
        assertEquals(10_000L, opening.shadeMs)
        // Aynı ham değer tekrar gelince durum değişmez (çizim yeniden başlamaz).
        assertEquals(opening, t.update(2, 1, 2, 0, false, 4_000))
        // 4 sn sonra yarıda durdu: %40'ta kalır.
        val stopped = t.update(6, 1, 2, 0, false, 5_000)!!
        assertEquals(0.4f, stopped.shade!!, eps)
        assertEquals(0L, stopped.shadeMs)
        // Kapanıyor: %40'tan 0'a, 0,4 × 20 sn.
        val closing = t.update(4, 1, 2, 0, false, 6_000)!!
        assertEquals(0f, closing.shade!!, eps)
        assertEquals(8_000L, closing.shadeMs)
        assertEquals(0.3f, t.estimate(8_000), eps)
        // Tam açık okununca kesin değer.
        assertEquals(1f, t.update(5, 1, 2, 0, false, 9_000)!!.shade!!, eps)
    }

    @Test
    fun trackerFirstReadingsWithoutHistory() {
        assertEquals(RoofDecode.SHADE_UNKNOWN_MID, RoofTracker().update(6, null, null, null, false, 0)!!.shade!!, eps)
        val closing = RoofTracker(closeMs = 10_000).update(4, null, null, null, false, 0)!!
        assertEquals(0f, closing.shade!!, eps)
        assertEquals(10_000L, closing.shadeMs)
        // Hiç tavan sinyali yok: null (taban görseli olduğu gibi).
        assertNull(RoofTracker().update(0, 0, 0, 50, false, 0))
        assertNull(RoofTracker().update(null, null, null, null, false, 0))
    }

    @Test
    fun trackerKeepsLastGlassWhileMovingAndLightsEdge() {
        val t = RoofTracker()
        assertEquals(0.5f, t.update(5, 6, 3, 0, false, 0)!!.glass, eps)
        // SPF_POSI okunamadı, SPF_OPERATE 4 (hareket): son bilinen konum korunur.
        assertEquals(0.5f, t.update(5, 0, 4, 0, false, 100)!!.glass, eps)
        assertTrue(t.update(5, 6, 3, 100, false, 200)!!.edgeAlert)
        assertTrue(t.update(5, 6, 3, 85, false, 300)!!.edgeAlert)
        assertFalse(t.update(5, 6, 3, 60, false, 400)!!.edgeAlert)
        assertTrue(t.update(5, 6, 3, 0, true, 500)!!.edgeAlert)
        // Kapalı cam yağmurda da yanmaz.
        assertFalse(t.update(5, 1, 2, 120, true, 600)!!.edgeAlert)
        // Yalnız cam sinyali: perde bilinmiyor (null), cam yine çizilir.
        val glassOnly = RoofTracker().update(0, 12, 0, 0, false, 0)!!
        assertNull(glassOnly.shade)
        assertTrue(glassOnly.tilt)
        assertTrue(glassOnly.glassOpen)
    }

    @Test
    fun geometryMatchesArt() {
        assertEquals(CarAnimSpec.ROOF_X1, CarAnimSpec.shadeEdgeX(0f), eps)
        assertEquals(CarAnimSpec.ROOF_X0, CarAnimSpec.shadeEdgeX(1f), eps)
        assertEquals(CarAnimSpec.ROOF_X1 - 0.3f * CarAnimSpec.roofW, CarAnimSpec.shadeEdgeX(0.3f), eps)
        assertEquals(CarAnimSpec.ROOF_X0, CarAnimSpec.shadeEdgeX(2f), eps)
        val closed = CarAnimSpec.glassPanelX(0f)
        assertEquals(CarAnimSpec.ROOF_FRONT_X0, closed[0], eps)
        assertEquals(CarAnimSpec.ROOF_X1, closed[1], eps)
        val hole0 = CarAnimSpec.roofHoleX(0f)
        assertEquals(0f, hole0[1] - hole0[0], eps)
        val full = CarAnimSpec.glassPanelX(1f)
        // Tam açıkta panel çerçevenin içinde kalır ve açıklık kadar arkaya kaymıştır.
        assertTrue(full[0] > CarAnimSpec.ROOF_X0)
        assertEquals(CarAnimSpec.ROOF_GLASS_TRAVEL, CarAnimSpec.roofHoleX(1f).let { it[1] - it[0] }, eps)
        assertEquals(CarAnimSpec.ROOF_GLASS_TRAVEL * 0.4f, CarAnimSpec.roofHoleX(0.4f).let { it[1] - it[0] }, eps)
        // Katmanların piksel boyu (2x) cam kutusuyla aynı: 552 × 318.
        assertEquals(552, (CarAnimSpec.roofW * CarAnimSpec.ROOF_ART_SCALE).toInt())
        assertEquals(318, ((CarAnimSpec.ROOF_Y1 - CarAnimSpec.ROOF_Y0) * CarAnimSpec.ROOF_ART_SCALE).toInt())
        // Eski buz mavisi tavan kutusu yeni cam kutusunun içinde.
        val s = CarAnimSpec.sunroof.box
        assertTrue(s.x >= CarAnimSpec.ROOF_X0 && s.right <= CarAnimSpec.ROOF_X1 && s.y >= CarAnimSpec.ROOF_Y0 && s.bottom <= CarAnimSpec.ROOF_Y1)
    }

    @Test
    fun trackerReportsGlassDirectionFromOperate() {
        val t = RoofTracker()
        assertEquals(1, t.update(6, 4, 3, 0, false, 0)!!.glassDir)
        assertEquals(-1, t.update(6, 4, 5, 0, false, 100)!!.glassDir)
        // Durdu (7 açık, 2 kapalı, 4 yarıda): yön yok.
        assertEquals(0, t.update(6, 8, 7, 0, false, 200)!!.glassDir)
        assertEquals(0, t.update(6, 5, 4, 0, false, 300)!!.glassDir)
        // Havalandırma hareketi cam kayması değildir; konum okunamazsa da yön verilmez.
        assertEquals(0, t.update(6, 12, 3, 0, false, 400)!!.glassDir)
        assertEquals(0, t.update(6, 0, 3, 0, false, 500)!!.glassDir)
    }

    @Test
    fun followerEasesWhenStillAndGoesIdle() {
        val f = RoofFollower()
        f.snap(0f)
        assertFalse(f.active(0))
        f.report(1f, 0, 0f, 1f, 0)
        assertTrue(f.active(10))
        assertEquals(0.5f, f.at(CarAnimSpec.ROOF_EASE_MS / 2), eps)
        assertEquals(1f, f.at(CarAnimSpec.ROOF_EASE_MS), eps)
        assertFalse(f.active(CarAnimSpec.ROOF_EASE_MS))
        // Aynı değer tekrar: hiçbir şey başlamaz.
        f.report(1f, 0, 0f, 1f, 1_000)
        assertFalse(f.active(1_000))
    }

    /** Cam açılırken araçtaki gibi adım bildirimleri ([intervals] ms arayla 0,1 adım) ve 16 ms karelerle gösterilen konumlar. */
    private fun glassRun(f: RoofFollower, from: Float, dir: Int, steps: Int, intervals: LongArray, t0: Long): Pair<FloatArray, Long> {
        val out = ArrayList<Float>()
        var t = t0
        var next = t0
        var k = 0
        var v = from
        // Son bildirimden sonra bir aralık daha kare (hareket sürüyor, durma bildirimi henüz yok).
        while (k <= steps || t < next) {
            if (k <= steps && t >= next) {
                RoofFeed.glass(f, RoofState(null, glass = v, glassDir = dir), t)
                v += dir * RoofDecode.GLASS_STEP
                next += intervals[k % intervals.size]
                k++
            }
            out.add(f.at(t))
            t += 16
        }
        return out.toFloatArray() to t
    }

    @Test
    fun steppedGlassReportsGiveMonotonicSmoothMotion() {
        val f = RoofFollower()
        f.snap(0.1f)
        // Araç günlüğündeki gibi düzensiz aralıklar (0,4 .. 1,0 sn).
        val (xs, _) = glassRun(f, 0.1f, 1, 6, longArrayOf(400, 900, 910, 870, 1_010, 910), 0)
        var maxDelta = 0f
        var still = 0
        var maxStill = 0
        for (i in 1 until xs.size) {
            val d = xs[i] - xs[i - 1]
            assertTrue("geri gitmez: kare $i ${xs[i - 1]} -> ${xs[i]}", d >= -1e-6f)
            maxDelta = maxOf(maxDelta, d)
            if (d < 1e-5f) still++ else still = 0
            maxStill = maxOf(maxStill, still)
        }
        // Akıcı: kare başına sıçrama yok (bir adım 0,1 iken kare başı en çok ~0,006) ve hareket ortasında duraklama yok.
        assertTrue("kare başı en çok $maxDelta", maxDelta < 0.006f)
        assertTrue("en uzun duraklama $maxStill kare", maxStill <= 3)
        // Okunan konumun yakınında kalır.
        assertTrue(xs.last() > 0.65f && xs.last() < 0.85f)
    }

    @Test
    fun stopSettlesToReportedValue() {
        val f = RoofFollower()
        f.snap(0.1f)
        val (xs, t) = glassRun(f, 0.1f, 1, 6, longArrayOf(900), 0)
        assertTrue(xs.last() > 0.7f)
        // Durdu: okunan değere (0,7) ROOF_SETTLE_MS içinde yerleşir, sonra kare istenmez.
        RoofFeed.glass(f, RoofState(null, glass = 0.7f, glassDir = 0), t)
        assertTrue(f.active(t + 16))
        assertEquals(0.7f, f.at(t + CarAnimSpec.ROOF_SETTLE_MS), eps)
        assertFalse(f.active(t + CarAnimSpec.ROOF_SETTLE_MS))
    }

    @Test
    fun movingFollowerGoesIdleWhenReportsStop() {
        // Bildirim gelmezse beklenen konum bir sonraki adımın biraz ötesinde durur ve kare istemez.
        val f = RoofFollower()
        f.snap(0.3f)
        RoofFeed.glass(f, RoofState(null, glass = 0.3f, glassDir = 1), 0)
        var t = 0L
        while (t < 5_000) { f.at(t); t += 16 }
        assertEquals(0.3f + RoofDecode.GLASS_STEP * RoofDecode.GLASS_LEAD_STEPS, f.shown, 1e-3f)
        assertFalse(f.active(t))
    }

    @Test
    fun directionChangeReversesWithoutJump() {
        val f = RoofFollower()
        f.snap(0.1f)
        val (up, t) = glassRun(f, 0.1f, 1, 3, longArrayOf(900), 0)
        val top = up.last()
        // Araç yarıda döndü: 0,4'ten kapanıyor.
        val (down, _) = glassRun(f, 0.4f, -1, 3, longArrayOf(900), t)
        assertTrue(Math.abs(down.first() - top) < 0.01f)
        for (i in 1 until down.size) assertTrue("geri açılmaz: ${down[i - 1]} -> ${down[i]}", down[i] <= down[i - 1] + 1e-6f)
        assertTrue(down.last() < 0.15f)
    }

    @Test
    fun shadeFollowsEstimatedMotionContinuously() {
        val f = RoofFollower()
        f.snap(0f)
        // Açılıyor: kapalıdan, tam açılma süresiyle (11 sn) sürekli.
        RoofFeed.shade(f, RoofState(1f, RoofDecode.SHADE_OPEN_MS), 0)
        assertEquals(0.5f, f.at(RoofDecode.SHADE_OPEN_MS / 2), 0.01f)
        assertTrue(f.active(RoofDecode.SHADE_OPEN_MS / 2))
        assertEquals(1f, f.at(RoofDecode.SHADE_OPEN_MS + 2_000), 0.001f)
        // Yarıda durdu (tahmin %40): okunan tahmine yerleşir.
        f.snap(0.3f)
        RoofFeed.shade(f, RoofState(0.4f, 0L), 0)
        assertEquals(0.4f, f.at(CarAnimSpec.ROOF_EASE_MS), eps)
        // Kapanıyor: %40'tan, kalan süre 0,4 × kapanma.
        val ms = (0.4f * RoofDecode.SHADE_CLOSE_MS).toLong()
        RoofFeed.shade(f, RoofState(0f, ms), 1_000)
        assertEquals(0.4f, f.at(1_000), 0.001f)
        assertEquals(0.2f, f.at(1_000 + ms / 2), 0.01f)
    }

    @Test
    fun firstOpeningStepHasNoLead() {
        // 7 Kasım (10:57:29): posi 1 op 3, 2 ms sonra posi 2 op 3, 0,58 sn sonra posi 2 op 1 ve havalandırma (12), 1,1 sn
        // sonra havalandırmadan posi 2 op 3 ile gerçek kayma.
        val tracker = RoofTracker()
        val shade = RoofFollower()
        val glass = RoofFollower()
        var prev: RoofState? = null
        var t = 0L
        var maxGlass = 0f
        fun feed(dur: Int, posi: Int, op: Int) {
            val st = tracker.update(6, posi, op, 0, false, t)!!
            RoofFeed.apply(shade, glass, prev, st, true, t)
            prev = st
            val end = t + dur
            while (t < end) { maxGlass = maxOf(maxGlass, glass.at(t)); t += 16 }
        }
        feed(1_000, 1, 2)
        feed(2, 1, 3)
        feed(584, 2, 3)
        feed(1, 2, 1)
        feed(1_120, 12, 1)
        // Okunan ilk adımı (0,1) aşıp geri kaymaz; havalandırmada kapalı çizilir.
        assertTrue("cam en çok $maxGlass", maxGlass <= RoofDecode.GLASS_STEP + 1e-4f)
        assertEquals(0f, glass.shown, eps)
        // Havalandırmadan sonraki ilk adım gerçek kayma: pay var, bir sonraki adımı beklemeden ilerler.
        feed(400, 2, 3)
        assertTrue("havalandırmadan sonra ${glass.shown}", glass.shown > RoofDecode.GLASS_STEP)
    }

    @Test
    fun applySnapsWithoutAnimationAndStartsMovingShadeFromEstimate() {
        val shade = RoofFollower()
        val glass = RoofFollower()
        // Animasyon kapalı: hedefe atlar, kare istenmez.
        RoofFeed.apply(shade, glass, null, RoofState(1f, RoofDecode.SHADE_OPEN_MS / 2, glass = 0.3f, glassDir = 1), false, 0)
        assertEquals(1f, shade.shown, eps)
        assertEquals(0.3f, glass.shown, eps)
        assertFalse(shade.active(0) || glass.active(0))
        // İlk okuma hareket halinde: perde tahmini yerden (yarı yol) ilerler.
        RoofFeed.apply(shade, glass, null, RoofState(1f, RoofDecode.SHADE_OPEN_MS / 2), true, 0)
        assertEquals(0.5f, shade.at(0), 0.01f)
        assertTrue(shade.active(16))
        assertTrue(shade.at(RoofDecode.SHADE_OPEN_MS / 4) > 0.7f)
        // Sinyal kayboldu: ikisi de kapalıya.
        RoofFeed.apply(shade, glass, RoofState(1f, 0L), null, true, 0)
        assertEquals(0f, shade.shown, eps)
        assertEquals(0f, glass.shown, eps)
    }

    @Test
    fun simulatedCarLogPlaysSmoothly() {
        // Debug demo taklidi: araç günlüğündeki sıra tracker ve çizimden geçince kare başına sıçrama olmaz, sonunda kapalı.
        val tracker = RoofTracker()
        val shade = RoofFollower()
        val glass = RoofFollower()
        var prev: RoofState? = null
        var t = 0L
        var maxShade = 0f
        var maxGlass = 0f
        for (s in RoofDemo.SIM_STEPS) {
            val st = tracker.update(s[1], s[2], s[3], 0, false, t)!!
            RoofFeed.apply(shade, glass, prev, st, true, t)
            prev = st
            val end = t + s[0]
            var a = shade.at(t)
            var b = glass.at(t)
            while (t < end) {
                t += 16
                val a2 = shade.at(t)
                val b2 = glass.at(t)
                maxShade = maxOf(maxShade, Math.abs(a2 - a))
                maxGlass = maxOf(maxGlass, Math.abs(b2 - b))
                a = a2
                b = b2
            }
        }
        assertTrue("perde kare başı $maxShade", maxShade < 0.01f)
        // Cam: kısa havalandırma geçişi (0,1 → 0) dahil kare başı küçük.
        assertTrue("cam kare başı $maxGlass", maxGlass < 0.02f)
        assertEquals(0f, shade.shown, eps)
        assertEquals(0f, glass.shown, eps)
        assertFalse(shade.active(t) || glass.active(t))
    }

    @Test
    fun demoExtraParses() {
        val r = RoofDemo.parse("shade=30,glass=40")!!
        assertEquals(0.3f, r.shade!!, eps)
        assertEquals(0.4f, r.glass, eps)
        assertFalse(r.tilt)
        assertFalse(r.edgeAlert)
        assertTrue(RoofDemo.parse("shade=100, glass=60, speed=120")!!.edgeAlert)
        assertTrue(RoofDemo.parse("glass=60,rain")!!.edgeAlert)
        assertNull(RoofDemo.parse("glass=60,rain")!!.shade)
        // Kapalı camda kenar ışığı yok.
        assertFalse(RoofDemo.parse("shade=0,alert")!!.edgeAlert)
        assertTrue(RoofDemo.parse("shade=100,tilt")!!.tilt)
        assertEquals(1f, RoofDemo.parse("shade=250")!!.shade!!, eps)
        assertNull(RoofDemo.parse(""))
        assertNull(RoofDemo.parse("nonsense"))
        assertNotNull(RoofDemo.parse("SHADE=0"))
    }
}
