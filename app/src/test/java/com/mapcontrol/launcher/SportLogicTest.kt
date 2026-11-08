package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SportLogicTest {

    // --- Kapı (debounce) ---

    @Test fun sportNeedsOneSecondOfStableSignal() {
        val g = SportGate(1_000)
        assertNull(g.update(0, DriveAccent.SPORT, true))
        assertNull(g.update(900, DriveAccent.SPORT, true))
        assertEquals(true, g.update(1_000, DriveAccent.SPORT, true))
        assertTrue(g.active)
        assertNull(g.update(1_500, DriveAccent.SPORT, true))
    }

    @Test fun flickerDoesNotSwitch() {
        val g = SportGate(1_000)
        g.update(0, DriveAccent.SPORT, true)
        g.update(600, DriveAccent.NORMAL, true)
        assertNull(g.update(1_200, DriveAccent.SPORT, true))
        assertNull(g.update(2_100, DriveAccent.SPORT, true))
        assertEquals(true, g.update(2_200, DriveAccent.SPORT, true))
        // Spor'dan tek okumalık sapma kapatmaz.
        g.update(3_000, DriveAccent.ECO, true)
        assertNull(g.update(3_500, DriveAccent.SPORT, true))
        assertTrue(g.active)
    }

    @Test fun unknownKeepsStateAndDeferredWhileBusy() {
        val g = SportGate(1_000)
        g.reset(true)
        assertNull(g.update(0, null, true))
        assertNull(g.update(0, DriveAccent.NORMAL, false))
        assertNull(g.update(2_000, DriveAccent.NORMAL, false))
        assertTrue(g.active)
        assertEquals(false, g.update(2_100, DriveAccent.NORMAL, true))
    }

    // --- Devir ve vites ---

    @Test fun rpmIsRawTimesFour() {
        assertEquals(752, SportMath.rpm(188))
        assertEquals(0, SportMath.rpm(0))
        assertEquals(3_400, SportMath.rpm(850))
        assertNull(SportMath.rpm(null))
        assertNull(SportMath.rpm(-1))
        assertNull(SportMath.rpm(0xFFFF))
    }

    @Test fun gearText() {
        assertEquals("P", SportMath.gearText(1))
        assertEquals("R", SportMath.gearText(2))
        assertEquals("N", SportMath.gearText(3))
        assertEquals("D", SportMath.gearText(5))
        assertEquals("D4", SportMath.gearText(5, 4))
        assertEquals("D", SportMath.gearText(5, 0))
        assertEquals("—", SportMath.gearText(null))
        // Sürüşte D sinyali 6/7'ye de geçer (araçta görüldü); eski biçimleyici 4'ü D sayar: hepsi "D", kademe uydurulmaz.
        assertEquals("D", SportMath.gearText(4))
        assertEquals("D", SportMath.gearText(6))
        assertEquals("D", SportMath.gearText(7))
        assertEquals("D", SportMath.gearText(11))
        assertEquals("—", SportMath.gearText(0))
        assertEquals("—", SportMath.gearText(16))
    }

    // --- 0–100 ve en yüksek hız ---

    /** Her 100 ms'de 1 km/sa artış: 0'dan sonra ilk örnek 1 km/sa. */
    private fun accelerate(s: SportSession, from: Long, toKmh: Int, stepMs: Long = 100, startKmh: Int = 0): Long {
        var t = from
        var v = startKmh
        while (v < toKmh) {
            v++
            t += stepMs
            s.update(t, v, 3_000)
        }
        return t
    }

    @Test fun zeroToHundredFromStandstill() {
        val s = SportSession()
        s.update(0, 0, 800)
        s.update(500, 0, 800)
        // Son 0 örneği t=500, ilk örnek t=600 (1 km/sa), ikinci t=700 (2): geriye uzatma kalkışı t=500 bulur;
        // 100 km/sa t=600+99*100.
        accelerate(s, 500, 100)
        assertEquals(10_000L, s.best0to100Ms)
        assertEquals(100, s.maxSpeedKmh)
        assertFalse(s.timing)
    }

    @Test fun crossingIsInterpolated() {
        val s = SportSession()
        s.update(0, 0, 800)
        s.update(1_000, 20, 3_000) // başlangıç (kalkış 0..1 000 arasında)
        s.update(8_000, 90, 4_000) // geriye uzatma t=0'ın da öncesini verir: son 0 örneğine (0) kırpılır
        s.update(9_000, 110, 4_500) // 100'e yarı yolda: 8 500
        assertEquals(8_500L, s.best0to100Ms)
        assertEquals(110, s.maxSpeedKmh)
    }

    @Test fun sparseSamplesEstimateTheLaunch() {
        // ~1 Hz okuma: gerçek kalkış t=500, sabit 12 km/sa/sn (gerçek 0–100 = 8 333 ms).
        val s = SportSession()
        s.update(0, 0, 800)
        var t = 1_000L
        while (t <= 9_000L) {
            s.update(t, (12 * (t - 500) / 1000).toInt(), 3_000)
            t += 1_000
        }
        assertEquals(8_333.0, s.best0to100Ms!!.toDouble(), 5.0)
    }

    @Test fun singleMovingSampleUsesMidpoint() {
        // İkinci hareket örneği gelmeden 100 aşılırsa (aradeğerle) kalkış son 0 ile ilk hareket örneğinin ortası.
        val s = SportSession()
        s.update(0, 0, 800)
        s.update(1_000, 10, 3_000)
        s.update(9_000, 10, 4_000) // hız aynı: kestirim yapılamaz
        s.update(10_000, 110, 4_500) // 100'e geçiş: 9 900
        assertEquals(9_400L, s.best0to100Ms)
    }

    @Test fun firstSampleAlreadyAboveTargetDoesNotTime() {
        val s = SportSession()
        s.update(0, 0, 800)
        s.update(10_000, 104, 4_000)
        s.update(11_000, 110, 4_000)
        assertNull(s.best0to100Ms)
    }

    @Test fun rollingStartDoesNotTime() {
        val s = SportSession()
        s.update(0, 40, 2_000)
        s.update(1_000, 60, 3_000)
        s.update(3_000, 105, 5_000)
        assertNull(s.best0to100Ms)
        assertEquals(105, s.maxSpeedKmh)
    }

    @Test fun stopCancelsAndTimeoutCancels() {
        val s = SportSession()
        s.update(0, 0, 800)
        s.update(100, 5, 2_000)
        s.update(2_000, 30, 3_000)
        s.update(3_000, 0, 900) // durdu: iptal
        assertFalse(s.timing)
        s.update(3_100, 3, 2_000) // yeniden başlar
        assertTrue(s.timing)
        s.update(34_000, 60, 3_000) // 30 sn geçti
        assertFalse(s.timing)
        s.update(35_000, 101, 4_000)
        assertNull(s.best0to100Ms)
    }

    @Test fun keepsBestOfDrive() {
        val s = SportSession()
        s.update(0, 0, 800)
        accelerate(s, 0, 100, stepMs = 110) // 10,89 sn
        val slow = s.best0to100Ms!!
        var t = 20_000L
        s.update(t, 0, 800)
        t = accelerate(s, t, 100, stepMs = 90) // 8,91 sn
        val fast = s.best0to100Ms!!
        assertTrue(fast < slow)
        s.update(t + 1_000, 0, 800)
        accelerate(s, t + 1_000, 100, stepMs = 150)
        assertEquals(fast, s.best0to100Ms)
    }

    @Test fun newDriveAfterEngineOffResets() {
        val s = SportSession()
        s.update(0, 0, 800)
        accelerate(s, 0, 120)
        assertEquals(120, s.maxSpeedKmh)
        s.update(20_000, 0, 0)
        s.update(60_000, 0, 0) // kısa duruş (start-stop): korunur
        s.update(61_000, 0, 800)
        assertEquals(120, s.maxSpeedKmh)
        s.update(70_000, 0, 0)
        s.update(70_000 + SportSession.NEW_DRIVE_OFF_MS, 0, 0)
        s.update(70_000 + SportSession.NEW_DRIVE_OFF_MS + 5_000, 0, 750) // motor çalıştı: yeni sürüş
        assertNull(s.maxSpeedKmh)
        assertNull(s.best0to100Ms)
    }

    @Test fun newDriveWithChangeOnlySamples() {
        // Değer yalnız değişince gelir: kapalı örnekten sonra 2 dk boyunca örnek yok, sonra motor çalışır.
        val s = SportSession()
        s.update(0, 0, 800)
        accelerate(s, 0, 80)
        s.update(30_000, 0, 0)
        s.update(30_000 + SportSession.NEW_DRIVE_OFF_MS + 1, 0, 780)
        assertNull(s.maxSpeedKmh)
    }

    @Test fun disconnectCountsAsOff() {
        // Kontak kapanınca servis düşerse çağıran devir 0, hız bilinmiyor verir.
        val s = SportSession()
        s.update(0, 0, 800)
        accelerate(s, 0, 90)
        s.update(40_000, null, 0)
        s.update(40_000 + SportSession.NEW_DRIVE_OFF_MS, 0, 760)
        assertNull(s.maxSpeedKmh)
    }

    @Test fun unknownSpeedCancelsRun() {
        val s = SportSession()
        s.update(0, 0, 800)
        s.update(100, 10, 2_000)
        s.update(200, null, 2_000)
        assertFalse(s.timing)
    }

    // --- G süzgeci ---

    @Test fun gravityIsRemovedForPlainAccelerometer() {
        val f = GFilter(linear = false)
        var t = 0L
        // Eğik duran ekran: yerçekimi y ve z'ye dağılmış, hareket yok.
        repeat(200) { f.add(t, 0f, 8f, 5.6f); t += 66_666_000L }
        assertEquals(0f, f.lateral, 0.01f)
        assertEquals(0f, f.longitudinal, 0.01f)
        // Sola dönüş (araç gidiyor): ani 0,5 g ivme -x; yolcu sağa itilir, kısa sürede görünür.
        f.still = false
        repeat(8) { f.add(t, -0.5f * GFilter.G, 8f, 5.6f); t += 66_666_000L }
        assertTrue(f.lateral > 0.3f)
    }

    @Test fun aLongCornerIsNotEatenByTheGravityEstimateWhileMoving() {
        val f = GFilter(linear = false)
        var t = 0L
        repeat(200) { f.add(t, 0f, 8f, 5.6f); t += 66_666_000L }
        f.still = false
        // 10 sn süren 0,3 g viraj: giderken yerçekimi kestirimi kaymaz, değer korunur.
        repeat(150) { f.add(t, -0.3f * GFilter.G, 8f, 5.6f); t += 66_666_000L }
        assertTrue(f.lateral > 0.25f)
        // Durunca (ör. eğimde park) yeni duruş birkaç saniyede sıfırlanır.
        f.still = true
        repeat(150) { f.add(t, -0.3f * GFilter.G, 8f, 5.6f); t += 66_666_000L }
        assertEquals(0f, f.lateral, 0.03f)
    }

    @Test fun dialReachesTheEdgeAtHalfAG() {
        assertEquals(0.4f, GFilter.dialUnit(0.2f), 0.001f)
        assertEquals(1f, GFilter.dialUnit(0.5f), 0.001f)
        assertEquals(-1f, GFilter.dialUnit(-0.9f), 0.001f)
    }

    @Test fun linearAccelerationIsSmoothed() {
        val f = GFilter(linear = true)
        var t = 0L
        f.add(t, 0f, 0f, 0f)
        t += 66_666_000L
        f.add(t, -GFilter.G, 0f, 0f)
        assertTrue(f.lateral in 0.05f..0.5f) // tek örnek sıçratmaz
        repeat(60) { t += 66_666_000L; f.add(t, -GFilter.G, 0f, -GFilter.G / 2) }
        assertEquals(1f, f.lateral, 0.02f)
        assertEquals(0.5f, f.longitudinal, 0.02f)
        assertEquals(1.118f, f.magnitude, 0.03f)
    }

    @Test fun brakingMovesTheDotUp() {
        val f = GFilter(linear = true)
        var t = 0L
        repeat(60) { f.add(t, 0f, 0f, -6.42f); t += 66_666_000L }
        assertTrue(f.longitudinal > 0f)
        // SportStage: ty = -dialUnit(longitudinal) * range; eksi y ekranda yukarı.
        assertTrue(-GFilter.dialUnit(f.longitudinal) < 0f)
    }

    @Test fun gSignsFollowFeltForce() {
        fun settle(x: Float, z: Float): GFilter = GFilter(linear = true).also { f ->
            var t = 0L
            repeat(60) { f.add(t, x, 0f, z); t += 66_666_000L }
        }
        // Sola dönüş (ivme -x): sağa itiş, nokta sağa (+).
        assertEquals(0.5f, settle(-0.5f * GFilter.G, 0f).lateral, 0.02f)
        // Sağa dönüş (ivme +x): sola itiş (-).
        assertEquals(-0.4f, settle(0.4f * GFilter.G, 0f).lateral, 0.02f)
        // Fren (araçta ölçüldü: z = -6,42 m/s², +z aracın önü): öne itiş (+), ekranda nokta yukarı.
        assertEquals(0.655f, settle(0f, -6.42f).longitudinal, 0.02f)
        assertEquals(0.6f, settle(0f, -0.6f * GFilter.G).longitudinal, 0.02f)
        // Gaz (ivme öne, +z): arkaya itiş (-), nokta aşağı.
        assertEquals(-0.3f, settle(0f, 0.3f * GFilter.G).longitudinal, 0.02f)
    }

    // --- Biçim ---

    @Test fun formatsWithComma() {
        assertEquals("9,6", SportFormat.seconds(9_600L))
        assertEquals("9,6", SportFormat.seconds(9_640L))
        assertEquals("9,8", SportFormat.l100(9.8))
        assertEquals("0,0", SportFormat.g(0.02f))
        assertEquals("-0,3", SportFormat.g(-0.3f))
        assertNull(SportFormat.seconds(null))
        assertEquals("47", SportFormat.duration(47))
        assertEquals("dk", SportFormat.durationUnit(47))
        assertEquals("1:05", SportFormat.duration(65))
        assertEquals("sa", SportFormat.durationUnit(65))
        assertNull(SportFormat.duration(null))
    }

    // --- 0–100 sonucu, tepe devir, 80–120 ---

    @Test fun resultIsReportedEvenWhenNotBest() {
        val s = SportSession()
        s.update(0, 0, 800)
        assertTrue(s.stopped)
        var t = accelerate(s, 0, 100, stepMs = 90)
        assertEquals(1, s.results)
        assertTrue(s.lastWasBest)
        assertFalse(s.stopped)
        val best = s.best0to100Ms
        t += 1_000
        s.update(t, 0, 800)
        accelerate(s, t, 100, stepMs = 150)
        assertEquals(2, s.results)
        assertFalse(s.lastWasBest)
        assertEquals(best, s.best0to100Ms)
        assertTrue(s.last0to100Ms!! > best!!)
    }

    @Test fun peakRpmFollowsDriveAndResets() {
        val s = SportSession()
        s.update(0, 0, 800)
        s.update(1_000, 30, 5_600)
        s.update(2_000, 40, 3_000)
        assertEquals(5_600, s.peakRpm)
        s.update(3_000, 0, 0)
        s.update(3_000 + SportSession.NEW_DRIVE_OFF_MS, 0, 0)
        s.update(4_000 + SportSession.NEW_DRIVE_OFF_MS, 0, 800)
        assertEquals(800, s.peakRpm)
    }

    // --- Küçük kadran seçimi ---

    @Test fun dialDefaultsAndDecode() {
        assertEquals(listOf(SportDial.ZERO100, SportDial.G, SportDial.INSTANT, SportDial.MAX), SportDials.decode(null, hasG = true))
        assertEquals(listOf(SportDial.ZERO100, SportDial.AVG_SPEED, SportDial.INSTANT, SportDial.MAX), SportDials.decode(null, hasG = false))
        assertEquals(listOf(SportDial.CLOCK, SportDial.AVG_SPEED, SportDial.RANGE, SportDial.MAX), SportDials.decode("clock,avg,range,max", true))
        // G sensör yoksa düşer; tekrarlar ve bilinmeyenler düşer, boşluk varsayılanla dolar.
        assertEquals(listOf(SportDial.ZERO100, SportDial.AVG_SPEED, SportDial.INSTANT, SportDial.MAX), SportDials.decode("g,g,xx", false))
        // Kaldırılan motor suyu ("cool") kayıtta kaldıysa o yuva sıradaki varsayılana geçer.
        assertEquals(listOf(SportDial.ZERO100, SportDial.AVG_SPEED, SportDial.INSTANT, SportDial.MAX), SportDials.decode("z100,cool,inst,max", false))
        assertEquals(listOf(SportDial.ZERO100, SportDial.G, SportDial.INSTANT, SportDial.MAX), SportDials.decode("z100,cool,inst,max", true))
        assertEquals(listOf(SportDial.CLOCK, SportDial.AVG_SPEED, SportDial.RANGE, SportDial.MAX), SportDials.decode("clock,cool,range,max", false))
        assertNull(SportDial.of("cool"))
        val d = SportDials.decode("max,max,max,max", true)
        assertEquals(4, d.toSet().size)
        assertEquals(SportDial.MAX, d[0])
        assertFalse(SportDial.G in SportDials.offered(false))
        assertEquals(SportDial.values().size, SportDials.offered(true).size)
        val all = SportDials.decode("clock,avg,range,max", true)
        assertEquals(all, SportDials.decode(SportDials.encode(all), true))
    }

    @Test fun removedDialsMigrateToDistinctValidDials() {
        assertNull(SportDial.of("spd"))
        assertNull(SportDial.of("out"))
        assertNull(SportDial.of("p80"))
        for (hasG in listOf(true, false)) {
            val d = SportDials.decode("z100,spd,out,p80", hasG)
            assertEquals(4, d.size)
            assertEquals(4, d.toSet().size)
            assertTrue(d.all { it in SportDials.offered(hasG) })
            assertEquals(SportDial.ZERO100, d[0])
        }
    }

    @Test fun choosingAUsedDialSwaps() {
        val cur = SportDials.defaults(true)
        assertEquals(listOf(SportDial.MAX, SportDial.G, SportDial.INSTANT, SportDial.ZERO100), SportDials.choose(cur, 0, SportDial.MAX))
        assertEquals(listOf(SportDial.CLOCK, SportDial.G, SportDial.INSTANT, SportDial.MAX), SportDials.choose(cur, 0, SportDial.CLOCK))
        assertEquals(cur, SportDials.choose(cur, 1, SportDial.G))
        assertEquals(cur, SportDials.choose(cur, 7, SportDial.CLOCK))
    }

    @Test fun summaryNeedsSomeData() {
        assertFalse(SportSummary(null, null, null, null).hasData)
        assertTrue(SportSummary(null, 120, null, null).hasData)
    }

    // --- Uzun alan kadran yerleşimi (Tiggo 8) ---

    private val fourDials = listOf(SportDial.ZERO100, SportDial.G, SportDial.INSTANT, SportDial.MAX)

    private fun assertNoOverlapAndInside(g: SportGeometry) {
        val all = g.all
        all.forEach { d ->
            assertTrue(d.cx - d.r >= -0.5f && d.cx + d.r <= g.w + 0.5f)
            assertTrue(d.cy - d.r >= -0.5f && d.cy + d.r <= g.h + 0.5f)
        }
        for (i in all.indices) for (j in i + 1 until all.size) {
            val a = all[i]; val b = all[j]
            val dist = Math.hypot((a.cx - b.cx).toDouble(), (a.cy - b.cy).toDouble())
            assertTrue("kadran $i ile $j çakışıyor", dist >= a.r + b.r)
        }
    }

    @Test fun tallLayoutOnlyOnTheTallArea() {
        assertFalse(SportGeometry(1872, 520, fourDials).tall) // Tiggo 7
        assertTrue(SportGeometry(1650, 750, fourDials).tall) // Tiggo 8
    }

    @Test fun normalLayoutIsUnchanged() {
        val g = SportGeometry(1872, 488, fourDials)
        assertEquals(1f, g.s, 1e-6f)
        assertEquals(236f, g.rpm.r, 1e-3f)
        assertEquals(210f, g.media.r, 1e-3f)
        assertEquals(104f, g.subs[0].r, 1e-3f)
        assertNoOverlapAndInside(g)
    }

    @Test fun tallLayoutGrowsTheRpmDialWithoutOverlap() {
        val t8 = SportGeometry(1650, 750, fourDials)
        val flat = SportGeometry(1650, 430, fourDials) // aynı genişlikte eski taslak
        assertTrue(t8.rpm.r > flat.rpm.r * 1.3f)
        assertTrue(t8.subs[0].r > flat.subs[0].r)
        assertTrue(t8.media.r >= flat.media.r)
        assertNoOverlapAndInside(t8)
        assertNoOverlapAndInside(SportGeometry(1200, 1000, fourDials)) // çok uzun alanda da
    }

    // --- Devir kadranındaki dijital hız ---

    @Test fun speedReadoutWritesOnlyChangesAtMostFourTimesASecond() {
        val r = SpeedReadout()
        assertEquals("—", r.next(0, -1))
        assertEquals("0", r.next(300, 0))
        assertNull(r.next(400, 0)) // aynı değer: iş yok
        assertNull(r.next(450, 1)) // 250 ms dolmadı
        assertEquals("1", r.next(550, 1))
        assertNull(r.next(560, 1))
        assertEquals("142", r.next(900, 142))
        assertEquals("—", r.next(1_200, -1)) // aralık dışı bilinmiyor
        assertNull(r.next(1_500, 400)) // hâlâ bilinmiyor: iş yok
        r.reset()
        assertEquals("88", r.next(1_510, 88)) // yeniden açılınca beklemeden
    }

    @Test fun needleDeadbandAgainstShownOrTarget() {
        // Dururken rölanti kıpırtısı (< 50) oynatmaz, büyük fark oynatır.
        assertFalse(SportMath.needleShouldMove(false, 800f, 800f, 830f, 50f))
        assertTrue(SportMath.needleShouldMove(false, 800f, 800f, 1_400f, 50f))
        // Hareket sürerken: hedefe göre küçük kıpırtı yeniden başlatmaz (sonsuz çizim olmaz), büyük değişim hemen yeni hedef.
        assertFalse(SportMath.needleShouldMove(true, 3_000f, 2_000f, 3_020f, 50f))
        assertFalse(SportMath.needleShouldMove(true, 3_000f, 2_000f, 3_000f, 50f))
        assertTrue(SportMath.needleShouldMove(true, 3_000f, 2_000f, 3_300f, 50f))
        assertTrue(SportMath.needleShouldMove(true, 3_000f, 2_000f, 2_900f, 50f))
        // Rölanti kıpırtısı (735–785) hareket sürerken de hedefi oynatmaz.
        assertFalse(SportMath.needleShouldMove(true, 760f, 900f, 785f, 50f))
    }

    @Test fun speedSitsBetweenNeedleTailAndGearWindow() {
        val tail = NeedleView.LENGTH * 0.16f // ibre kuyruğu (yüz yarıçapına oran)
        val digitsTop = SportGeometry.SPEED_Y - SportGeometry.SPEED_SIZE * SportGeometry.CAP / 2f
        val digitsBottom = SportGeometry.SPEED_Y + SportGeometry.SPEED_SIZE * SportGeometry.CAP / 2f
        assertTrue(digitsTop > tail)
        assertTrue(SportGeometry.SPEED_UNIT_Y - SportGeometry.SPEED_UNIT_SIZE / 2f > digitsBottom)
        assertTrue(SportGeometry.GEAR_Y - SportGeometry.GEAR_HH > SportGeometry.SPEED_UNIT_Y + SportGeometry.SPEED_UNIT_SIZE / 2f)
        assertTrue(SportGeometry.RPM_LABEL_Y > SportGeometry.GEAR_Y + SportGeometry.GEAR_HH)
        assertTrue(SportGeometry.RPM_LABEL_Y < 0.95f)
        // Alt boşluk: ibre 0'da 135°, en üst hızda 405° (45°); hız bölgesi (aşağı, 90°) hiç süpürülmez.
        assertTrue(SportMath.speedDeg(0f) >= 135f - 1e-3f && SportMath.speedDeg(SportMath.SPEED_MAX.toFloat()) <= 405f + 1e-3f)
        assertEquals("km/sa", SportGeometry.SPEED_UNIT_TEXT)
    }

    @Test fun mediaContentKeepsTiggo7ScaleAndShrinksOnlyWhenSmaller() {
        assertEquals(208f, SportGeometry.mediaContentRadius(208f, 210f), 0f)
        assertEquals(240f, SportGeometry.mediaContentRadius(240f, 210f), 0f)
        assertEquals(210f, SportGeometry.mediaContentRadius(180f, 210f), 0f)
    }

    // --- Geçiş biçimi (6 Kasım) ---

    @Test fun transitionKindFollowsMotionAndAnimationSetting() {
        assertEquals(SportTransition.Kind.SWEEP, SportTransition.choose(true, false))
        assertEquals(SportTransition.Kind.SWEEP, SportTransition.choose(true, null)) // hız bilinmiyor: duruyor sayılır
        assertEquals(SportTransition.Kind.CALM, SportTransition.choose(true, true))
        assertEquals(SportTransition.Kind.SWEEP, SportTransition.choose(true, true, inPark = true))
        assertEquals(SportTransition.Kind.SWEEP, SportTransition.choose(true, null, inPark = true)) // P ve hız bilinmiyor
        assertEquals(SportTransition.Kind.CALM, SportTransition.choose(true, true, inPark = false))
        assertEquals(SportTransition.Kind.INSTANT, SportTransition.choose(false, true, inPark = true))
        assertEquals(SportTransition.Kind.INSTANT, SportTransition.choose(false, true))
        assertEquals(SportTransition.Kind.INSTANT, SportTransition.choose(false, false))
    }

    @Test fun summaryOnlyWhenStoppedAfterALongStint() {
        assertTrue(SportTransition.summaryOnExit(true, false))
        assertTrue(SportTransition.summaryOnExit(true, null))
        assertFalse(SportTransition.summaryOnExit(true, true))
        assertFalse(SportTransition.summaryOnExit(false, false))
    }

    @Test fun sweepTimingIsSlowAndGlowsSpanTheSweep() {
        assertEquals(1_950L, SportTransition.SWEEP_TOTAL_MS) // + 250 ms karartma = ~2,2 sn
        val end = SportTransition.glowEndMs(7)
        assertTrue(end in 1_500L..SportTransition.SWEEP_TOTAL_MS)
        assertEquals(500L, SportTransition.CALM_MS)
    }
}
