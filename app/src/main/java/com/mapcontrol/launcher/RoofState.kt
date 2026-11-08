package com.mapcontrol.launcher

/**
 * Panoramik tavanın Sahne aracındaki durumu (6 Kasım fikri, DURUM-5kasim "tavan perdesi"): perde ve ön cam panelin gerçek
 * konumu araba çiziminde görünür (parça kendisi değişir, yazı / rozet yok).
 *
 * [shade] perdenin açıklığı: 0 kapalı (kumaş tavan döşemesi görünür) .. 1 tam açık (cam + hafif gökyüzü yansıması); kısmi
 * değerde öndeki pay açılmıştır (perde öne doğru değil arkaya toplanır). null = perde sinyali yok, taban görseli olduğu gibi.
 * [shadeMs] bu hedefe varış süresi: 0 = kısa yumuşak geçiş; > 0 = perde şu an hareket ediyor, kalan yolun tahmini süresi
 * (çizim her karede sürekli izler, [RoofFollower]).
 * [glass] ön cam panelin araçtan son okunan açıklığı (0 kapalı .. 1 tam geri kaymış; hareket sırasında %10'luk adımlarla
 * gelir); [tilt] havalandırma (arka kenar kalkık). [glassDir] camın hareketi (SPF_OPERATE): +1 açılıyor, -1 kapanıyor, 0 duruyor.
 * [edgeAlert] tavan açıkken hızlı gidiliyor ya da yağmur var: açık kenar hafif kehribar yanar.
 */
data class RoofState(
    val shade: Float?,
    val shadeMs: Long = 0L,
    val glass: Float = 0f,
    val tilt: Boolean = false,
    val edgeAlert: Boolean = false,
    val glassDir: Int = 0,
) {
    /** Cam açık sayılır mı (kenar ışığı ve ice vurgusu için). */
    val glassOpen: Boolean get() = glass > RoofDecode.OPEN_EPS || tilt
}

/**
 * Ham CarSetting değerlerinin çözümü (saf, test edilir). Değerler araçta yalnızca kısmen doğrulandı; doğrulanmayanlar
 * savunmacı çözülür ve VehicleHeroCard her değişimde "Roof" etiketiyle ham değeri bir kez yazar (kalibrasyon için).
 */
object RoofDecode {

    // --- ID_CAR_SUNSHADE (CarSetting 10): araçta ölçüldü (araç günlükleri) ---
    const val SHADE_CLOSED = 1
    const val SHADE_OPENING = 2
    /** 3 ve 6: hareket yarıda durdu (10-30'da 2 → 3, 4 → 3; 10-19'da 2 → 6, 4 → 6 görüldü). */
    const val SHADE_STOPPED = 3
    const val SHADE_CLOSING = 4
    const val SHADE_OPEN = 5
    const val SHADE_STOPPED_2 = 6
    /** OEM sesli komutta "desteklenmiyor". */
    const val SHADE_UNSUPPORTED = 7

    // --- ID_CAR_SPF_POSI (CarSetting 6): yalnızca 1 (kapalı) ölçüldü; 12 = havalandırma OEM yazma kodundan ---
    const val POSI_CLOSED = 1
    const val POSI_TILT = 12
    /**
     * TAHMİN (araçta doğrulanacak): 2..11 on adımlı konum ((ham - 1) × %10); 13..100 doğrudan yüzde. Gerçek ölçek "Roof"
     * günlüğünden okunup burası düzeltilir.
     */
    const val POSI_STEP_MAX = 11
    const val POSI_PERCENT_MAX = 100

    /** Tam açılma / kapanma süresi (ms): 10-30 günlüğünden (2 → 5 ≈ 10,8 sn; kapanış yarıdan ≈ 8,7 sn → tam ≈ 13,5 sn). */
    const val SHADE_OPEN_MS = 11_000L
    const val SHADE_CLOSE_MS = 13_500L
    /** Geçmişi bilinmeyen "yarıda durdu" okuması (ör. açılışta ilk değer 6) için varsayılan açıklık. */
    const val SHADE_UNKNOWN_MID = 0.5f

    // --- ID_CAR_SPF_OPERATE: 7 Kasım araç günlüğü: cam açılırken 3, kapanırken 5; durunca 7 (açık), 2 (kapalı), 4 (yarıda) ---
    const val OPERATE_OPENING = 3
    const val OPERATE_CLOSING = 5
    /**
     * Cam hareket ederken SPF_POSI %10'luk adımlarla gelir, adım başına ≈ 0,9 sn (7 Kasım: 2 → 8 arası 0,87..1,01 sn). Çizim
     * adımların arasını bu hızla doldurur ([RoofFollower]).
     */
    const val GLASS_STEP = 0.1f
    const val GLASS_STEP_MS = 900L
    /** Hareket sürerken çizimin son okunan adımın ötesine gidebileceği pay (adım gecikirse orada bekler). */
    const val GLASS_LEAD_STEPS = 1.2f

    /** Kenar ışığı: bu hızda yanar, [EDGE_SPEED_OFF_KMH] altına inince söner (eşikte yanıp sönmesin). */
    const val EDGE_SPEED_ON_KMH = 90
    const val EDGE_SPEED_OFF_KMH = 80
    const val OPEN_EPS = 0.02f

    enum class Motion { CLOSED, OPEN, OPENING, CLOSING, STOPPED, PERCENT }

    class Shade(val motion: Motion, val fraction: Float = 0f) {
        override fun equals(other: Any?) = other is Shade && other.motion == motion && other.fraction == fraction
        override fun hashCode() = motion.hashCode() * 31 + fraction.hashCode()
        override fun toString() = "Shade($motion, $fraction)"
    }

    class Glass(val fraction: Float, val tilt: Boolean = false) {
        override fun equals(other: Any?) = other is Glass && other.fraction == fraction && other.tilt == tilt
        override fun hashCode() = fraction.hashCode() * 31 + tilt.hashCode()
        override fun toString() = "Glass($fraction, tilt=$tilt)"
    }

    /** Okunmuş ve anlamlı değer mi (null, geçersiz işaretçi, 0 = "desteklenmiyor / gelmedi" ve negatifler değil). */
    fun valid(raw: Int?): Boolean = raw != null && raw != Int.MIN_VALUE && raw > 0

    /** Perde; bilinmeyen (0, 7, > 100, geçersiz) null. 8..100 yüzde sayılır (bazı modellerde yüzde gelebilir). */
    fun shade(raw: Int?): Shade? {
        if (!valid(raw)) return null
        return when (raw!!) {
            SHADE_CLOSED -> Shade(Motion.CLOSED, 0f)
            SHADE_OPEN -> Shade(Motion.OPEN, 1f)
            SHADE_OPENING -> Shade(Motion.OPENING)
            SHADE_CLOSING -> Shade(Motion.CLOSING)
            SHADE_STOPPED, SHADE_STOPPED_2 -> Shade(Motion.STOPPED)
            SHADE_UNSUPPORTED -> null
            in 8..POSI_PERCENT_MAX -> Shade(Motion.PERCENT, raw / 100f)
            else -> null
        }
    }

    /**
     * Ön cam panel. Önce SPF_POSI (1 kapalı, 12 havalandırma, 2..11 adım, 13..100 yüzde); okunamazsa SPF_OPERATE
     * ([com.mapcontrol.vehicle.VehicleMetricsSnapshot.sunroofOpenOrNull] ile aynı: 3/6/7 açık, 2/5 kapalı, 4 hareket = null).
     */
    fun glass(posi: Int?, operate: Int?): Glass? {
        if (valid(posi)) {
            val p = posi!!
            when {
                p == POSI_CLOSED -> return Glass(0f)
                p == POSI_TILT -> return Glass(0f, tilt = true)
                p <= POSI_STEP_MAX -> return Glass((p - 1) / 10f)
                p <= POSI_PERCENT_MAX -> return Glass(p / 100f)
            }
        }
        if (!valid(operate)) return null
        return when (operate) {
            3, 6, 7 -> Glass(1f)
            2, 5 -> Glass(0f)
            else -> null
        }
    }

    /** Camın hareket yönü: SPF_OPERATE 3 açılıyor (+1), 5 kapanıyor (-1); havalandırmada ve diğer kodlarda 0. */
    fun glassDir(operate: Int?, glass: Glass?): Int {
        if (glass == null || glass.tilt) return 0
        return when (operate) {
            OPERATE_OPENING -> 1
            OPERATE_CLOSING -> -1
            else -> 0
        }
    }

    /** Kenar ışığı (histerezisli): tavan açıkken hız eşiği ya da yağmur. [wasOn] önceki durum. */
    fun edgeAlert(open: Boolean, speedKmh: Int?, raining: Boolean, wasOn: Boolean): Boolean {
        if (!open) return false
        if (raining) return true
        val v = speedKmh ?: return false
        return if (wasOn) v >= EDGE_SPEED_OFF_KMH else v >= EDGE_SPEED_ON_KMH
    }
}

/**
 * Ham okumalardan [RoofState] (durumlu, saf; zaman dışarıdan verilir). Perdenin yüzde sinyali yok (yalnız durum), bu yüzden
 * kısmi konum hareketin başladığı andan geçen süreyle tahmin edilir: açılırken [RoofDecode.SHADE_OPEN_MS], kapanırken
 * [RoofDecode.SHADE_CLOSE_MS]; "yarıda durdu" o anki tahminde kalır. Aynı ham perde değeri tekrar gelince durum aynen kalır
 * (çizim yeniden başlamaz). Cam okunamadığı anlarda (SPF_OPERATE 4 = hareket) son bilinen cam konumu korunur.
 */
class RoofTracker(
    private val openMs: Long = RoofDecode.SHADE_OPEN_MS,
    private val closeMs: Long = RoofDecode.SHADE_CLOSE_MS,
) {
    private var hasShadeRaw = false
    private var shadeRaw: Int? = null
    private var est = 0f
    private var dir = 0
    private var t0 = 0L
    private var shadeTarget: Float? = null
    private var shadeMs = 0L
    private var glass: RoofDecode.Glass? = null
    private var alert = false

    /** Perdenin [now] anındaki tahmini açıklığı (hareket sürüyorsa geçen süreyle). */
    fun estimate(now: Long): Float = when (dir) {
        1 -> CarAnimSpec.clamp01(est + (now - t0).toFloat() / openMs)
        -1 -> CarAnimSpec.clamp01(est - (now - t0).toFloat() / closeMs)
        else -> est
    }

    fun update(sunshade: Int?, posi: Int?, operate: Int?, speedKmh: Int?, raining: Boolean, now: Long): RoofState? {
        if (!hasShadeRaw || sunshade != shadeRaw) {
            val first = !hasShadeRaw || !RoofDecode.valid(shadeRaw)
            hasShadeRaw = true
            shadeRaw = sunshade
            val cur = estimate(now)
            val s = RoofDecode.shade(sunshade)
            when (s?.motion) {
                null -> { dir = 0; shadeTarget = null; shadeMs = 0L }
                RoofDecode.Motion.CLOSED, RoofDecode.Motion.OPEN, RoofDecode.Motion.PERCENT -> {
                    dir = 0; est = s.fraction; shadeTarget = est; shadeMs = 0L
                }
                RoofDecode.Motion.OPENING -> {
                    est = if (first) 0f else cur; dir = 1; t0 = now
                    shadeTarget = 1f; shadeMs = ((1f - est) * openMs).toLong()
                }
                RoofDecode.Motion.CLOSING -> {
                    est = if (first) 1f else cur; dir = -1; t0 = now
                    shadeTarget = 0f; shadeMs = (est * closeMs).toLong()
                }
                RoofDecode.Motion.STOPPED -> {
                    est = if (first) RoofDecode.SHADE_UNKNOWN_MID else cur; dir = 0
                    shadeTarget = est; shadeMs = 0L
                }
            }
        }
        val read = RoofDecode.glass(posi, operate)
        read?.let { glass = it }
        val g = glass
        // Yön yalnız SPF_POSI okunduysa (adım değeri); yalnız SPF_OPERATE'ten gelen tahmin adım değildir.
        val gDir = if (read != null && RoofDecode.valid(posi)) RoofDecode.glassDir(operate, read) else 0
        if (shadeTarget == null && g == null) {
            alert = false
            return null
        }
        val open = g != null && (g.fraction > RoofDecode.OPEN_EPS || g.tilt)
        alert = RoofDecode.edgeAlert(open, speedKmh, raining, alert)
        return RoofState(shadeTarget, shadeMs, g?.fraction ?: 0f, g?.tilt == true, alert, gDir)
    }
}

/**
 * Tek tavan büyüklüğünün çizimdeki (gösterilen) konumu (saf, zaman dışarıdan verilir). Araç konumu seyrek ve adım adım
 * bildirir (cam %10'luk adımlarla ≈ 0,9 sn arayla, perde yalnız başladı / durdu); çizim bu adımlara atlamaz:
 *
 * - Hareket sürerken ([report] `dir` ≠ 0) beklenen konum son bildirilen değerden ölçülen hızla sürekli ilerler ve `cap`'te
 *   (bir sonraki beklenen adımın biraz ötesi ya da hedef) bekler. Gösterilen konum beklenene yumuşak yaklaşır: aradaki fark
 *   [tauMs] zaman sabitiyle üstel söner (kare hızından bağımsız). Hareket yönünde asla geri gitmez (bildirim gerideyse
 *   gösterilen bekler, beklenen yetişince yine ilerler); yön değişince aynı kural yeni yönde geçerlidir.
 * - Hareket durunca (`dir` = 0) gösterilen konum bildirilen değere yerleşir: hareketin ardından [CarAnimSpec.ROOF_SETTLE_MS],
 *   duran bir değer değişince [CarAnimSpec.ROOF_EASE_MS] yumuşak geçiş.
 *
 * [at] durumu [now]'a ilerletir (aynı ya da geri zamanla çağrı bir şey değiştirmez); [active] bir sonraki karenin gerekip
 * gerekmediğini söyler (hareket bitince ya da beklenen konum sınırda durunca false: boşta kare çizilmez). Nesne ayrılmaz.
 */
class RoofFollower(private val tauMs: Float = CarAnimSpec.ROOF_CORRECT_MS.toFloat()) {
    /** Son hesaplanan gösterilen konum. */
    var shown = 0f
        private set
    /** Son bildirilen değer (hareketin çıkış noktası ya da yerleşme hedefi). */
    var reported = 0f
        private set
    /** Hareket yönü (+1 / -1), duruyorsa 0. */
    var dir = 0
        private set
    private var lastT = Long.MIN_VALUE
    private var moved = 0f
    // Hareket: beklenen = reported + dir × rate × (t - baseT), cap'i aşmaz; gösterilen = beklenen + err (err üstel söner).
    private var baseT = 0L
    private var rate = 0f
    private var cap = 0f
    private var err = 0f
    // Yerleşme: from → reported, start'tan dur ms.
    private var from = 0f
    private var start = 0L
    private var dur = 0L

    fun snap(v: Float) {
        shown = v; reported = v; from = v; dir = 0; dur = 0L; err = 0f; moved = 0f; lastT = Long.MIN_VALUE
    }

    /**
     * Yeni bildirim: [value] araçtan okunan (ya da tahmin edilen) konum; [dir] hareket yönü (0 = duruyor); [rate] hareket
     * hızı (birim / ms); [cap] hareket sürerken gösterilebilecek en uç değer.
     */
    fun report(value: Float, dir: Int, rate: Float, cap: Float, now: Long) {
        val cur = at(now)
        val wasMoving = this.dir != 0
        reported = value
        if (dir != 0) {
            this.dir = if (dir > 0) 1 else -1
            this.rate = rate
            this.cap = cap
            baseT = now
            err = cur - value
        } else {
            this.dir = 0
            from = cur
            start = now
            dur = when {
                Math.abs(value - cur) < 0.0005f -> 0L
                wasMoving -> CarAnimSpec.ROOF_SETTLE_MS
                else -> CarAnimSpec.ROOF_EASE_MS
            }
            if (dur == 0L) shown = value
        }
    }

    /** Hareketin beklenen konumu (cap'i aşmaz). */
    private fun expected(now: Long): Float {
        val e = reported + dir * rate * (now - baseT)
        return if (dir > 0) minOf(e, cap) else maxOf(e, cap)
    }

    fun at(now: Long): Float {
        val dt = if (lastT == Long.MIN_VALUE) 0L else now - lastT
        if (dt < 0L) return shown
        lastT = now
        val prev = shown
        if (dir != 0) {
            if (dt > 0L) err *= Math.exp(-dt / tauMs.toDouble()).toFloat()
            val e = expected(now)
            var p = e + err
            if (dir > 0 && p < prev) p = prev
            if (dir < 0 && p > prev) p = prev
            p = CarAnimSpec.clamp01(p)
            err = p - e
            shown = p
        } else {
            shown = if (dur <= 0L || now - start >= dur) reported
            else from + (reported - from) * CarAnimSpec.smooth((now - start).toFloat() / dur, 0f, 1f)
        }
        moved = Math.abs(shown - prev)
        return shown
    }

    /** Bir sonraki karede gösterilen konum değişecek mi (değilse kare istenmez; yeni bildirim yeniden başlatır). */
    fun active(now: Long): Boolean {
        if (dir == 0) return dur > 0L && now - start < dur
        val e = expected(now)
        val capped = if (dir > 0) e >= cap else e <= cap
        return !capped || moved > 1e-5f
    }
}

/** [RoofState] değişimlerini [RoofFollower]'lara aktarır (saf; Sahne aracı ve testler aynı yolu kullanır). */
object RoofFeed {

    /**
     * Yeni tavan durumu [state] (önceki [prev]) iki izleyiciye: [anim] false (detay, animasyon kapalı) ise hedefe atlar;
     * ilk okuma hareket halindeyse perde tahmini yerden ilerler, duruyorsa doğrudan yerinde; sonra yalnız değişen büyüklük
     * bildirilir. [CarStageView.setRoof] ve testler bunu çağırır.
     */
    fun apply(shadeF: RoofFollower, glassF: RoofFollower, prev: RoofState?, state: RoofState?, anim: Boolean, now: Long) {
        if (state == null) {
            shadeF.snap(0f)
            glassF.snap(0f)
            return
        }
        val target = state.shade
        when {
            target == null -> shadeF.snap(0f)
            !anim -> shadeF.snap(target)
            prev?.shade == null -> {
                shadeF.snap(if (state.shadeMs > 0) shadeStart(target, state.shadeMs) else target)
                if (state.shadeMs > 0) shade(shadeF, state, now)
            }
            target != prev.shade || state.shadeMs != prev.shadeMs -> shade(shadeF, state, now)
        }
        when {
            !anim || prev == null -> glassF.snap(state.glass)
            state.glass != prev.glass || state.glassDir != prev.glassDir -> {
                // Kapalıdan açılışın ilk adımı (havalandırmadan değil): araç bu adımdan sonra durup havalandırmaya geçer
                // (7 Kasım: posi 2 op 3, 0,6 sn sonra op 1 ve 12), kayma ancak havalandırmadan sonra başlar.
                val first = state.glassDir > 0 && state.glass < RoofDecode.GLASS_STEP + RoofDecode.OPEN_EPS && !prev.tilt
                glass(glassF, state, now, if (first) 0f else RoofDecode.GLASS_LEAD_STEPS)
            }
        }
    }

    /** Perde: hareket ediyorsa ([RoofState.shadeMs] > 0) tahmini yerden hedefe ölçülen sürenin hızıyla, değilse yerleşme. */
    fun shade(f: RoofFollower, s: RoofState, now: Long) {
        val target = s.shade ?: return
        if (s.shadeMs <= 0L) {
            f.report(target, 0, 0f, target, now)
            return
        }
        val opening = target >= 0.5f
        val start = shadeStart(target, s.shadeMs)
        val dir = if (opening) 1 else -1
        val rate = 1f / (if (opening) RoofDecode.SHADE_OPEN_MS else RoofDecode.SHADE_CLOSE_MS)
        f.report(start, dir, rate, target, now)
    }

    /** Hareketin bildirildiği andaki tahmini açıklık: hedef ve kalan süreden ([RoofTracker] ile aynı süreler). */
    fun shadeStart(target: Float, ms: Long): Float =
        if (target >= 0.5f) CarAnimSpec.clamp01(1f - ms.toFloat() / RoofDecode.SHADE_OPEN_MS)
        else CarAnimSpec.clamp01(ms.toFloat() / RoofDecode.SHADE_CLOSE_MS)

    /**
     * Ön cam: hareket ederken okunan adımdan adım hızıyla, en çok [lead] adım öteye (0 = okunanda bekler); değilse yerleşme.
     */
    fun glass(f: RoofFollower, s: RoofState, now: Long, lead: Float = RoofDecode.GLASS_LEAD_STEPS) {
        val d = s.glassDir
        if (d == 0) {
            f.report(s.glass, 0, 0f, s.glass, now)
            return
        }
        val cap = CarAnimSpec.clamp01(s.glass + d * RoofDecode.GLASS_STEP * lead)
        f.report(s.glass, d, RoofDecode.GLASS_STEP / RoofDecode.GLASS_STEP_MS, cap, now)
    }
}

/** Demo tavanı: `--es launcher_demo_roof "shade=0..100,glass=0..100[,tilt][,alert|speed=N|rain]"`; boş / bozuk = null. */
object RoofDemo {
    fun parse(raw: String?): RoofState? {
        if (raw.isNullOrBlank()) return null
        var shade: Float? = null
        var glass = 0f
        var tilt = false
        var alert = false
        var any = false
        for (part in raw.split(',')) {
            val kv = part.trim().lowercase(java.util.Locale.ROOT).split('=', limit = 2)
            val k = kv[0].trim()
            val v = kv.getOrNull(1)?.trim()
            val pct = v?.toFloatOrNull()?.let { CarAnimSpec.clamp01(it / 100f) }
            when (k) {
                "shade" -> if (pct != null) { shade = pct; any = true }
                "glass" -> if (pct != null) { glass = pct; any = true }
                "tilt" -> { tilt = v == null || v == "1" || v == "true"; any = true }
                "alert", "rain" -> alert = v == null || v == "1" || v == "true"
                "speed" -> alert = (v?.toIntOrNull() ?: 0) >= RoofDecode.EDGE_SPEED_ON_KMH
            }
        }
        if (!any) return null
        val open = glass > RoofDecode.OPEN_EPS || tilt
        return RoofState(shade, 0L, glass, tilt, alert && open)
    }

    /** `--es launcher_demo_roof sim`: araçtaki adımlı bildirimlerin taklidi ([SIM_STEPS], yalnız debug demo). */
    const val SIM = "sim"

    fun isSim(raw: String?): Boolean = raw?.trim()?.lowercase(java.util.Locale.ROOT) == SIM

    /**
     * 7 Kasım araç günlüğündeki sıra (ham değerler): { sonraki adıma kadar ms, SUNSHADE, SPF_POSI, SPF_OPERATE }. Perde açılır
     * (2 → 5, ≈ 11 sn), cam 2..8 adımla açılır (≈ 0,9 sn arayla, kısa havalandırma dahil; 3 → 7), cam kapanır (5 → 2),
     * perde kapanır (4 → 1, ≈ 13,5 sn). Sonda başa döner.
     */
    val SIM_STEPS: List<IntArray> = buildList {
        add(intArrayOf(1_500, 1, 1, 2))
        add(intArrayOf(11_200, 2, 1, 2))
        add(intArrayOf(2_000, 5, 1, 2))
        add(intArrayOf(580, 5, 2, 3))
        add(intArrayOf(1_100, 5, 12, 1))
        for (p in 2..7) add(intArrayOf(if (p == 2) 400 else 900, 5, p, 3))
        add(intArrayOf(500, 5, 8, 3))
        add(intArrayOf(2_500, 5, 8, 7))
        for (p in 8 downTo 2) add(intArrayOf(if (p == 8) 500 else 900, 5, p, 5))
        add(intArrayOf(1_500, 5, 1, 2))
        add(intArrayOf(13_700, 4, 1, 2))
        add(intArrayOf(2_000, 1, 1, 2))
    }
}
