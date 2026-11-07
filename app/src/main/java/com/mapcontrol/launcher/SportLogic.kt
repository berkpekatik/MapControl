package com.mapcontrol.launcher

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Spor modu kararı (saf, JVM'de sınanır): araç sürüş modu [DriveAccent.SPORT] kesintisiz [stableMs] gözlenirse açılır,
 * başka bir mod kesintisiz [stableMs] gözlenirse kapanır. Kısa titremeler (tek okuma) bir şey değiştirmez.
 * [observed] null (veri yok) uygulanan durumu korur ve sayacı sıfırlar. [canSwitch] false iken (düzenleme, açık sayfa)
 * karar ertelenir, sayaç işlemeye devam eder.
 */
class SportGate(private val stableMs: Long = STABLE_MS) {
    var active = false
        private set
    private var candidate: Boolean? = null
    private var since = 0L

    fun reset(on: Boolean) {
        active = on
        candidate = null
    }

    /** Yeni durum (değiştiyse), yoksa null. */
    fun update(now: Long, observedMode: Int?, canSwitch: Boolean): Boolean? {
        val want = observedMode?.let { it == DriveAccent.SPORT }
        if (want == null || want == active) {
            candidate = null
            return null
        }
        if (candidate != want) {
            candidate = want
            since = now
        }
        if (now - since < stableMs || !canSwitch) return null
        active = want
        candidate = null
        return want
    }

    companion object {
        const val STABLE_MS = 1_000L
    }
}

/** Spor kadranlarının saf çözümleri. */
object SportMath {
    /** Kadran yayı: 0 sol altta (135°), 8 sağ altta; saat yönünde 270° (tuval açısı, 0° = sağ). */
    const val START_DEG = 135f
    const val SWEEP_DEG = 270f

    /**
     * ReadOnly ENGINE_RPM (62) ham değerinden dev/dk: ham × 4 (Tiggo 7'de gösterge fotoğrafıyla doğrulandı, 188 → 752).
     * Geçersiz (null, negatif, 0xFFFF gibi aralık dışı) değer null.
     */
    fun rpm(raw: Int?): Int? = raw?.takeIf { it in 0..RAW_MAX }?.let { it * com.mapcontrol.vehicle.VehicleMetricsSnapshot.RPM_FACTOR }

    const val RAW_MAX = 3_000 // 12 000 dev/dk; üstü bozuk okuma

    /**
     * İbre yeni devre gitsin mi: yalnız [deadband]'den büyük farkta. Dururken fark gösterilen devirden, hareket sürerken
     * gidilen hedeften ölçülür: rölanti kıpırtısı süren hareketi her 100 ms yeniden başlatıp ekranı durmadan çizdirmez
     * (6 Kasım emülatör ölçümü: kıpırtıda saniyede ~50 kare), hızlı gaz/bırakma (100 ms'de yüzlerce devir) yine kaçmaz.
     */
    fun needleShouldMove(moving: Boolean, target: Float, shown: Float, rpm: Float, deadband: Float): Boolean =
        kotlin.math.abs(rpm - if (moving) target else shown) >= deadband

    /**
     * Ana (büyük) kadran hızdır (spor-modu-brief sonu, kullanıcı kararı "önce hız"): 0–[SPEED_MAX] km/sa aynı 270° yayda.
     * Devir ReadOnly 62 araçta ~5 sn'de bir değişiyor (tur 6), ibre için işe yaramıyor; kadranın altında küçük okumadır.
     */
    const val SPEED_MAX = 220
    /** Hız rakamları ve uzun bölüntü adımı (km/sa). */
    const val SPEED_MAJOR = 20

    fun speedDeg(kmh: Float): Float = START_DEG + SWEEP_DEG * (kmh.coerceIn(0f, SPEED_MAX.toFloat()) / SPEED_MAX)

    /** Sonraki tike kadar (ms): en çok [tickMs], ama bir sonraki saniye sınırını (+[SECOND_PAD_MS]) geçmeden. */
    fun tickDelay(wallMs: Long, tickMs: Long): Long {
        val toSecond = 1_000L - Math.floorMod(wallMs, 1_000L) + SECOND_PAD_MS
        return minOf(tickMs, toSecond)
    }

    const val SECOND_PAD_MS = 5L

    /**
     * G noktası yeni yere gitsin mi: [band]'den (px) küçük kıpırtı yeni kare çizdirmez, merkeze dönüş tam. Yeni değer ya da
     * null (yerinde kalır).
     */
    fun gStep(shown: Float, target: Float, band: Float): Float? = when {
        target == shown -> null
        target == 0f -> 0f
        abs(target - shown) >= band -> target
        else -> null
    }

    /**
     * Vites penceresi: ReadOnly GEARBOX_STATE (26) P=1, R=2, N=3; D araçta 5 ölçüldü ve sürüşte 6/7'ye de geçiyor
     * (6'nın M de olabileceği notu doğrulanmadı). Eski gösterge biçimleyicisi ([com.mapcontrol.vehicle.VehicleMetricsFormatter])
     * 4'ü D, üstünü D+kademe sayıyor; ikisiyle de uyumlu olmak için [D_MIN]..[D_MAX] hep "D" gösterilir (kademe sinyalden
     * çıkarılmaz; uydurma veri yok). Kademe numarası biliniyorsa (demo) yanına yazılır ("D4"). Bilinmeyen durum "—".
     */
    fun gearText(state: Int?, gearNumber: Int? = null): String = when (state) {
        1 -> "P"
        2 -> "R"
        3 -> "N"
        in D_MIN..D_MAX -> if (gearNumber != null && gearNumber in 1..9) "D$gearNumber" else "D"
        else -> "—"
    }

    private const val D_MIN = 4
    private const val D_MAX = 15
}

/**
 * Bu sürüşün spor verisi (saf, JVM'de sınanır): en yüksek hız ve 0–100 km/sa süresi.
 *
 * Hız: çağıran gösterge hızını verir (aracın kadranıyla aynı; gerçek hızdan birkaç yüzde yüksek okur, yani 0–100 süresi
 * gerçek 0–100'den biraz kısa çıkar). Araç hız sinyalinin (CAR_SPEED) ölçeği bu araçta doğrulanmadığı için o kullanılmıyor.
 *
 * 0–100: araç durmuşken (hız 0) hız 0'dan çıkınca ölçüm başlar; 100 km/sa'ya ulaşınca durur (iki örnek arası doğrusal
 * aradeğerle). Kalkış anı son 0 örneği ile ilk hareket örneği arasında bir yerdedir: ikinci hareket örneği gelince ilk iki
 * örnekten geriye doğru 0'a uzatılarak kestirilir (o aralığa kırpılır), gelmezse aralığın ortası alınır. Böylece seyrek
 * örnekte (~1 Hz) başlangıç hatası bir örnek aralığından çok daha küçük kalır. Hız yeniden 0'a düşer ya da [MAX_RUN_MS]
 * geçerse ölçüm iptal olur. Sürüşün en iyisi tutulur.
 *
 * Yeni sürüş: motor kapalı (devir 0) ve hız 0/bilinmiyor durumu ilk gözlendiği andan en az [NEW_DRIVE_OFF_MS] sonra motor
 * çalışınca ya da araç hareket edince en yüksek hız ve en iyi 0–100 sıfırlanır (kontak kapatılıp açıldı sayılır). Örnekler
 * yalnız değer değişince gelebilir: süre bir sonraki "açık" örnekte ölçülür, arada örnek gelmesi gerekmez. Bağlantı yoksa
 * çağıran devir 0, hız null verir (kontak kapanınca servis düşerse de kapalı sayılır). Süreç yeniden başlayınca da sıfırdır.
 */
class SportSession {
    var maxSpeedKmh: Int? = null
        private set
    /** Bu sürüşün en iyi 0–100 süresi (ms). */
    var best0to100Ms: Long? = null
        private set
    /** Sürmekte olan 0–100 ölçümü var mı. */
    val timing: Boolean get() = runStart != null
    /** Son biten 0–100 ölçümü (rekor olmasa da), bittiği an ([lastResultAt], monoton ms) ve sürüşün en iyisi mi. */
    var last0to100Ms: Long? = null
        private set
    var lastResultAt = 0L
        private set
    var lastWasBest = false
        private set
    /** Biten 0–100 ölçümü sayacı (kadran yeni sonucu bundan anlar ve kısa parlar). */
    var results = 0
        private set
    /** Bu sürüşün en yüksek devri (özet kartı; kadrandaki tepe işareti artık en yüksek hız). */
    var peakRpm: Int? = null
        private set
    /** Araç duruyor (son bilinen hız 0): 0–100 "Hazır". */
    val stopped: Boolean get() = lastSpeed == 0

    private var lastSpeed: Int? = null
    private var lastAt = 0L
    private var runStart: Long? = null
    /** Kalkış aralığı: son 0 örneği ve ilk hareket örneği (zaman, hız); ikinci hareket örneğinde kestirim yapılır. */
    private var zeroAt = 0L
    private var firstAt = 0L
    private var firstKmh = 0
    private var refined = false
    private var offSince: Long? = null

    fun reset() {
        clearDrive()
        lastSpeed = null
        offSince = null
    }

    private fun clearDrive() {
        maxSpeedKmh = null
        best0to100Ms = null
        last0to100Ms = null
        lastWasBest = false
        peakRpm = null
        runStart = null
    }

    private fun stateHash(): Long =
        results * 1_000_003L + (maxSpeedKmh ?: -1) * 7_919L + (peakRpm ?: -1) * 131L + (best0to100Ms ?: -1L)

    /** Yeni örnek: [now] ms (monoton), hız km/sa (bilinmiyorsa null), devir (bilinmiyorsa null). Bir şey değiştiyse true. */
    fun update(now: Long, speedKmh: Int?, rpm: Int?): Boolean {
        val before = stateHash()
        // Kontak/yeni sürüş: motor kapalı ve duruyor.
        val off = rpm == 0 && (speedKmh == null || speedKmh == 0)
        if (off) {
            if (offSince == null) offSince = now
        } else {
            val s = offSince
            if (s != null && now - s >= NEW_DRIVE_OFF_MS && ((rpm ?: 0) > 0 || (speedKmh ?: 0) > 0)) clearDrive()
            offSince = null
        }
        if (rpm != null && rpm > (peakRpm ?: 0)) peakRpm = rpm
        if (speedKmh == null) {
            // Bilinmeyen hız ölçümü bozar: iptal.
            runStart = null
            lastSpeed = null
            return before != stateHash()
        }
        if (speedKmh > (maxSpeedKmh ?: 0)) maxSpeedKmh = speedKmh

        val prev = lastSpeed
        val prevAt = lastAt
        when {
            speedKmh == 0 -> runStart = null
            runStart == null && prev == 0 -> {
                zeroAt = prevAt
                firstAt = now
                firstKmh = speedKmh
                refined = false
                // Seyrek örnekte ilk hareket örneği zaten 100'ü aşmışsa ölçüm anlamsız: başlatılmaz.
                runStart = if (speedKmh >= TARGET_KMH) null else prevAt + (now - prevAt) / 2
            }
        }
        val start = runStart
        if (start != null && speedKmh > 0) {
            if (!refined && now > firstAt) {
                // Yalnız ikinci hareket örneğinde (kalkışa en yakın, ivme en doğrusal): ilk iki örnekten hızın 0 olduğu an
                // geriye uzatılır ve [son 0, ilk hareket] aralığına kırpılır. Hız artmadıysa orta nokta kalır.
                if (speedKmh > firstKmh) {
                    val back = ((now - firstAt) * firstKmh.toDouble() / (speedKmh - firstKmh)).toLong()
                    runStart = (firstAt - back).coerceIn(zeroAt, firstAt)
                }
                refined = true
            }
            val st = runStart ?: start
            when {
                now - st > MAX_RUN_MS -> runStart = null
                speedKmh >= TARGET_KMH && now > firstAt -> {
                    // Eşiği geçiş anı: önceki örnekle doğrusal aradeğer.
                    val at = if (prev != null && prev < TARGET_KMH && speedKmh > prev)
                        prevAt + ((now - prevAt) * (TARGET_KMH - prev).toDouble() / (speedKmh - prev)).toLong()
                    else now
                    val run = at - st
                    if (run in 1..MAX_RUN_MS) {
                        val best = run < (best0to100Ms ?: Long.MAX_VALUE)
                        if (best) best0to100Ms = run
                        last0to100Ms = run
                        lastResultAt = now
                        lastWasBest = best
                        results++
                    }
                    runStart = null
                }
            }
        }
        lastSpeed = speedKmh
        lastAt = now
        return before != stateHash()
    }

    companion object {
        const val TARGET_KMH = 100
        const val MAX_RUN_MS = 30_000L
        const val NEW_DRIVE_OFF_MS = 120_000L
    }
}

/** Spor modundaki dört küçük kadranın gösterebileceği değerler (yalnız gerçek veri; akü voltajı yok). */
enum class SportDial(val key: String) {
    ZERO100("z100"),
    G("g"),
    INSTANT("inst"),
    MAX("max"),
    AVG_SPEED("avg"),
    TRIP_TIME("time"),
    RANGE("range"),
    CLOCK("clock");

    companion object {
        fun of(key: String): SportDial? = values().firstOrNull { it.key == key }
    }
}

/**
 * Küçük kadran seçimi (saf, JVM'de sınanır). Dört yuva: 0 sol üst, 1 sol alt, 2 sağ üst, 3 sağ alt. Bir değer en çok bir
 * yuvada; G-metre yalnız ivme sensörü varsa sunulur. Kayıt "z100,g,inst,max" biçiminde; bozuk/eksik kayıt varsayılanla
 * tamamlanır.
 */
object SportDials {
    const val SLOTS = 4

    fun offered(hasG: Boolean): List<SportDial> = SportDial.values().filter { hasG || it != SportDial.G }

    /**
     * Varsayılan: 0–100, G, Anlık, Maks. Sensör yoksa G'nin yerinde Ortalama hız. Motor suyu artık listede yok (okunan değer
     * derece değil, [CoolantStatus]): kayıttaki eski "cool" bilinmeyen anahtar sayılır, yuvası varsayılanla dolar.
     */
    fun defaults(hasG: Boolean): List<SportDial> =
        if (hasG) listOf(SportDial.ZERO100, SportDial.G, SportDial.INSTANT, SportDial.MAX)
        else listOf(SportDial.ZERO100, SportDial.AVG_SPEED, SportDial.INSTANT, SportDial.MAX)

    fun encode(dials: List<SportDial>): String = dials.joinToString(",") { it.key }

    fun decode(raw: String?, hasG: Boolean): List<SportDial> {
        val ok = offered(hasG)
        val out = ArrayList<SportDial?>()
        raw?.split(',')?.take(SLOTS)?.forEach { k ->
            out += SportDial.of(k.trim())?.takeIf { it in ok && it !in out }
        }
        while (out.size < SLOTS) out += null
        // Boş kalan yuva önce kendi varsayılanıyla, o kullanılıyorsa listedeki ilk kullanılmayanla dolar.
        val def = defaults(hasG)
        for (i in 0 until SLOTS) if (out[i] == null) out[i] = def[i].takeIf { it !in out }
        for (i in 0 until SLOTS) if (out[i] == null) out[i] = ok.first { it !in out }
        return out.map { it!! }
    }

    /** [slot] yuvasına [dial] seçildi: başka yuvadaysa iki yuva yer değiştirir (bir değer bir kez). */
    fun choose(current: List<SportDial>, slot: Int, dial: SportDial): List<SportDial> {
        if (slot !in current.indices || current[slot] == dial) return current
        val out = current.toMutableList()
        val other = out.indexOf(dial)
        if (other >= 0) out[other] = out[slot]
        out[slot] = dial
        return out
    }
}

/** Spor çıkışındaki sürüş özeti kartının değerleri (bu sürüş; yalnız okunan veri). */
data class SportSummary(
    val minutes: Int?,
    val maxKmh: Int?,
    val best0to100Ms: Long?,
    val avgL100: Double?,
) {
    /** Gösterilecek bir şey var mı (hepsi bilinmiyorsa kart açılmaz). */
    val hasData: Boolean get() = minutes != null || maxKmh != null || best0to100Ms != null || avgL100 != null
}

/**
 * G-metre süzgeci (saf, JVM'de sınanır). Girdi m/s², Android cihaz ekseni: x ekranın sağı, y yukarısı, z ekranın
 * normali. Bu araçtaki (Tiggo 7) montajda +z aracın **önü**: 7 Kasım araç ölçümünde frende ham doğrusal ivme
 * z = -6,42 / -5,07 / -3,62 m/s² okundu (fren = araç geriye ivmelenir → -z geriye).
 *  - [linear] false (yalnız TYPE_ACCELEROMETER): yerçekimi alçak geçiren süzgeçle kestirilip çıkarılır; ekranın eğimi
 *    böylece sıfırlanır. Araç dururken ([still]) hızlı öğrenir ([GRAVITY_TAU_STILL_S]), giderken neredeyse sabit tutulur
 *    ([GRAVITY_TAU_MOVING_S]): uzun fren ya da viraj yerçekimi sanılıp noktadan silinmez (Tur 6: "G çok az oynuyor").
 *  - Sonuç [DISPLAY_TAU_S] ile yumuşatılır (titreşim ve yol pürüzü).
 * Çıktı g biriminde ve **hissedilen kuvvet** yönünde (ivmenin tersi; G-metre noktası yolcuyu iten yöne gider):
 *  - [lateral] sağa itiş +: sola dönüşte araç sola ivmelenir (ivme -x), yolcu sağa itilir, değer +.
 *  - [longitudinal] öne itiş +: frende araç geriye ivmelenir (ivme -z), yolcu öne itilir, değer +, nokta yukarı;
 *    gazda -, nokta aşağı. Araçta doğrulandı (7 Kasım: fren ~0,4 g, eski işaretle nokta aşağı gidiyordu).
 * Ekranın öne/arkaya eğimi z eksenine y'nin bir kısmını karıştırır (küçük hata). Yanal işaret araçta henüz doğrulanmadı.
 */
class GFilter(private val linear: Boolean) {
    private val gravity = FloatArray(3)
    private var seeded = false
    private var lastNs = 0L
    var lateral = 0f
        private set
    var longitudinal = 0f
        private set
    /** Araç duruyor mu (hız ~0): yerçekimi kestirimi yalnız o zaman hızlı güncellenir. Sensör iş parçacığı okur. */
    @Volatile var still = true

    fun reset() {
        seeded = false
        lateral = 0f
        longitudinal = 0f
    }

    fun add(timestampNs: Long, x: Float, y: Float, z: Float) {
        if (!seeded) {
            gravity[0] = x; gravity[1] = y; gravity[2] = z
            lastNs = timestampNs
            seeded = true
            if (linear) push(x, z, 1f)
            return
        }
        val dt = ((timestampNs - lastNs) / 1e9f).coerceIn(0f, 0.5f)
        lastNs = timestampNs
        val lx: Float
        val lz: Float
        if (linear) {
            lx = x; lz = z
        } else {
            val a = alpha(dt, if (still) GRAVITY_TAU_STILL_S else GRAVITY_TAU_MOVING_S)
            gravity[0] += a * (x - gravity[0])
            gravity[1] += a * (y - gravity[1])
            gravity[2] += a * (z - gravity[2])
            lx = x - gravity[0]
            lz = z - gravity[2]
        }
        push(lx, lz, alpha(dt, DISPLAY_TAU_S))
    }

    private fun push(lx: Float, lz: Float, a: Float) {
        // Hissedilen kuvvet ivmenin tersi. Yanal: ivme -x (sola dönüş) → sağa itiş +. Boyuna: +z aracın önü (araçta
        // ölçüldü), fren ivmesi -z → öne itiş + (nokta yukarı).
        val latG = -lx / G
        val lonG = -lz / G
        lateral += a * (latG - lateral)
        longitudinal += a * (lonG - longitudinal)
    }

    /** Toplam büyüklük (g). */
    val magnitude: Float get() = sqrt(lateral * lateral + longitudinal * longitudinal)

    companion object {
        const val G = 9.80665f
        const val GRAVITY_TAU_STILL_S = 1f
        const val GRAVITY_TAU_MOVING_S = 60f
        /** Kadranın tam ölçeği (g): günlük sürüş 0,1–0,4 g; 0,5 g'de nokta kenara varır. */
        const val FULL_SCALE_G = 0.5f
        /** Ekran süzgeci (sakin nokta; ani sarsıntı kadranı titretmez). */
        const val DISPLAY_TAU_S = 0.35f
        /** Sensör 10 Hz (SENSOR_DELAY_UI'den seyrek); olaylar ana iş parçacığına değil sensör iş parçacığına gelir. */
        const val SAMPLING_US = 100_000

        fun alpha(dt: Float, tau: Float): Float = if (dt <= 0f) 0f else (1f - exp(-dt / tau))

        /** Ekranda 1 g'yi aşan değer kenarda durur. */
        fun clampUnit(v: Float): Float = v.coerceIn(-1f, 1f)

        /** g değerinin kadran yarıçapına oranı ([FULL_SCALE_G]'de kenar, ötesi kenarda durur). */
        fun dialUnit(g: Float): Float = clampUnit(g / FULL_SCALE_G)
    }
}

/**
 * Devir kadranının ortasındaki dijital hız (saf, JVM'de sınanır): metin yalnız tam sayı değişince ve en çok
 * [INTERVAL_MS]'de bir (~4 Hz) yenilenir; dururken "0", bilinmiyorsa "—". Değişmeyen değer için iş ve ayırma yok.
 */
class SpeedReadout {
    private var shown = UNSET
    private var at = 0L

    /** [now] anında [kmh] (eksi: bilinmiyor) için gösterilecek yeni metin; değişmediyse ya da aralık dolmadıysa null. */
    fun next(now: Long, kmh: Int): String? {
        val v = if (kmh in 0..MAX_KMH) kmh else UNKNOWN
        if (v == shown) return null
        if (shown != UNSET && now - at < INTERVAL_MS) return null
        shown = v
        at = now
        return if (v == UNKNOWN) DASH else v.toString()
    }

    /** Görünüm yeniden açıldı: ilk değer beklemeden yazılır. */
    fun reset() {
        shown = UNSET
    }

    companion object {
        const val INTERVAL_MS = 250L
        const val MAX_KMH = 300
        const val DASH = "—"
        private const val UNSET = Int.MIN_VALUE
        private const val UNKNOWN = -1
    }
}

/** Spor kadranlarında gösterilecek değerlerin biçimi (Türkçe ondalık virgül). */
object SportFormat {
    fun seconds(ms: Long?): String? = ms?.let { tenths(it / 1000.0) }
    fun l100(v: Double?): String? = v?.let { tenths(it) }
    fun g(v: Float): String = tenths(v.toDouble())

    /** Süre (dk): 60'tan azsa "47", yoksa "1:05" (birimi [durationUnit]). */
    fun duration(min: Int?): String? = min?.takeIf { it >= 0 }?.let { if (it < 60) it.toString() else "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }
    fun durationUnit(min: Int?): String = if (min != null && min >= 60) "sa" else "dk"

    /** Onda bir hassasiyet, virgülle ("9,6"). */
    fun tenths(v: Double): String {
        val t = Math.round(v * 10).toLong()
        val sign = if (t < 0) "-" else ""
        val a = abs(t)
        return "$sign${a / 10},${a % 10}"
    }
}

/**
 * Spor geçişinin biçimi (saf; JVM'de sınanır, 6 Kasım kararı):
 *  - [SWEEP]: araç duruyor (hız 0, P ya da hız bilinmiyor): karartma ve ağır ibre süpürmesi (~2,2 sn).
 *  - [CALM]: araç gidiyor (hız > 0, P değil): karartma ve süpürme yok, ~500 ms sakin çapraz geçiş; ibre doğrudan gerçek
 *    hızda (hız ibresi), medya ve uyarı şeridi hiç kaybolmaz.
 *  - [INSTANT]: animasyonlar kapalı.
 */
internal object SportTransition {
    enum class Kind { SWEEP, CALM, INSTANT }

    fun choose(animationsOn: Boolean, moving: Boolean?, inPark: Boolean? = null): Kind = when {
        !animationsOn -> Kind.INSTANT
        moving == true && inPark != true -> Kind.CALM
        else -> Kind.SWEEP
    }

    /** Çıkışta sürüş özeti yalnız araç dururken (gitmiyorken) ve spor yeterince uzun açık kaldıysa. */
    fun summaryOnExit(longStint: Boolean, moving: Boolean?): Boolean = longStint && moving != true

    /** Sakin çapraz geçiş süresi (giriş ve çıkış). */
    const val CALM_MS = 500L

    // Duran araçta giriş (karartma kalktıktan sonra): ibre gecikme · çıkış · tepede bekleme · gerçek hıza iniş (hız ibresi).
    const val SWEEP_DELAY_MS = 150L
    const val SWEEP_UP_MS = 900L
    const val SWEEP_HOLD_MS = 150L
    const val SWEEP_DOWN_MS = 750L

    /** Süpürmenin toplamı (karartma hariç). */
    const val SWEEP_TOTAL_MS = SWEEP_DELAY_MS + SWEEP_UP_MS + SWEEP_HOLD_MS + SWEEP_DOWN_MS

    // Çerçeve parlaması süpürmeye yayılır: ilk kadran gecikmeyle, her biri arayla parlar, son parlama inişle biter.
    const val GLOW_DELAY_MS = 150L
    const val GLOW_STAGGER_MS = 130L
    const val GLOW_UP_MS = 300L
    const val GLOW_DOWN_MS = 650L

    /** Canlı değerler ibre tepeden inmeye başlarken belirir. */
    const val LIVE_DELAY_MS = SWEEP_DELAY_MS + SWEEP_UP_MS + SWEEP_HOLD_MS
    const val LIVE_FADE_MS = 400L

    /** [dials] kadranlı sahnede son parlamanın bittiği an (karartma kalktıktan sonra). */
    fun glowEndMs(dials: Int): Long = GLOW_DELAY_MS + (dials - 1).coerceAtLeast(0) * GLOW_STAGGER_MS + GLOW_UP_MS + GLOW_DOWN_MS
}

/**
 * Hız kadranının altındaki küçük devir okuması (saf, JVM'de sınanır). ReadOnly 62 normal veri katmanından (değişince)
 * gelir; değer [staleMs]'den uzun süredir değişmediyse eski sayılır ve gizlenir (bayat devir gösterilmez). Gösterim
 * 100 dev/dk'ya yuvarlanır (rölanti kıpırtısı metni değiştirmez).
 */
class RpmReadout(private val staleMs: Long = STALE_MS) {
    private var last: Int? = null
    private var changedAt = 0L

    /**
     * [now] anında okunan [rpm] (null: bilinmiyor) için gösterilecek yuvarlanmış devir; eski ya da bilinmiyorsa null.
     * [arrivedAt] verilirse (akan kaynak, VDBus 773) tazelik değişime değil son varışa göre ölçülür: sabit seyirde aynı
     * değer akarken okuma gizlenmez.
     */
    fun next(now: Long, rpm: Int?, arrivedAt: Long? = null): Int? {
        if (rpm == null || rpm < 0) { last = null; return null }
        if (rpm != last) { last = rpm; changedAt = now }
        if (arrivedAt != null && arrivedAt > changedAt) changedAt = arrivedAt
        if (now - changedAt > staleMs) return null
        return (rpm + 50) / 100 * 100
    }

    fun reset() { last = null }

    companion object {
        const val STALE_MS = 3_000L
    }
}

/**
 * Hızlı hız (android.car PERF_VEHICLE_SPEED, gerçek hız) ile gösterge hızı (ReadOnly 119, aracın kadranı; gerçek hızdan
 * birkaç yüzde yüksek) arasındaki oran (saf, JVM'de sınanır). Kadranımız göstergeyle aynı okusun diye hızlı hız bu oranla
 * çarpılır; Maks ve 0–100 de gösterge hızından ölçülür. Oran yalnız sabit hızda ([STEADY_KMH]) ve [MIN_KMH] üstünde
 * öğrenilir (tam sayı gösterge hızının yuvarlama hatası küçük kalsın), [MIN_RATIO]..[MAX_RATIO] arasına kırpılır.
 */
class SpeedCalibration {
    var ratio = 1f
        private set
    private var lastActual = Float.NaN

    fun learn(actualKmh: Float, gaugeKmh: Int?) {
        val prev = lastActual
        lastActual = actualKmh
        if (gaugeKmh == null || prev.isNaN()) return
        if (actualKmh < MIN_KMH || gaugeKmh < MIN_KMH || abs(actualKmh - prev) > STEADY_KMH) return
        val r = (gaugeKmh / actualKmh).coerceIn(MIN_RATIO, MAX_RATIO)
        ratio += ALPHA * (r - ratio)
    }

    fun display(actualKmh: Float): Float = actualKmh * ratio

    companion object {
        const val MIN_KMH = 40
        const val STEADY_KMH = 2f
        const val MIN_RATIO = 0.97f
        const val MAX_RATIO = 1.12f
        const val ALPHA = 0.15f
    }
}

/**
 * Hızlı hız okuyucusunun ana iş parçacığına ne zaman haber vereceği (saf, JVM'de sınanır): olaylar ~10 Hz gelir (dururken
 * de 0 olarak); yalnız [band] km/sa'dan büyük değişimde ya da 0'a inişte iletilir. Değişmeyen hızda ana iş parçacığında iş
 * ve yeniden çizim yok.
 */
class SpeedPostGate(private val band: Float = BAND_KMH) {
    @Volatile private var last = Float.NaN

    fun shouldPost(kmh: Float): Boolean {
        val prev = last
        if (!prev.isNaN() && kmh == prev) return false
        if (prev.isNaN() || abs(kmh - prev) >= band || (kmh == 0f)) {
            last = kmh
            return true
        }
        return false
    }

    fun reset() { last = Float.NaN }

    companion object {
        /** Kadranda ~0,6°: gözle seçilmez, ibre hareketi akıcı kalır. */
        const val BAND_KMH = 0.5f
    }
}

/**
 * Araç kadranındaki motor torku halkasının çözümü (saf, JVM'de sınanır). Kaynak ReadOnly 106 `ID_MEAN_EFFECTIVE_TORQUE`;
 * ham değer [AwdMath.engineNm] ile çözülür (Nm = ham − 2000; kod tek yerde). Tiggo 7 (7 Kasım sürüşü): rölantide ~2000
 * (0 Nm), sert gazda ~2287..2509, motor freninde ~1956..1964 (negatif); motorun tepe torku ~290 Nm.
 *
 * Tam ölçek [FULL_SCALE_NM]; doluluk 0..1'e kırpılır. Motor freni (negatif) ve rölanti kıpırtısı ([NOISE_NM] ve altı) boş
 * halkadır; geçersiz/okunamayan değer null (halka söner, uydurma değer yok). Halka [SWEEP_DEG] derecelik yaydır, alt boşluklu,
 * sol alttan ([START_DEG], tuval açısı) saat yönünde dolar; gösterilen değer tam dereceye yuvarlanır ([degrees]) ve yalnız
 * derece değişince çizilir.
 */
internal object SportTorque {
    const val FULL_SCALE_NM = 300
    /** Rölantide ham değer 2000±5 oynar: bu kadarı boş halka sayılır (rölantide halka kıpırdamaz). */
    const val NOISE_NM = 8
    const val SWEEP_DEG = 270f
    /** Yayın başı (tuval açısı, +x 0°, saat yönü artı): sol alt; boşluk altta ortalı. */
    const val START_DEG = 135f

    /** Yükselirken (hedef gösterilenin üstünde) yayın doğal frekansı (rad/sn): ~0,6–0,8 sn'de oturur. */
    const val ATTACK_W = 7f
    /** Alçalırken (bırakma) doğal frekans (rad/sn): ~1,2–1,6 sn'de oturur; okumalar (~1 sn) arasında durmaz. */
    const val RELEASE_W = 3.5f
    /** Kare süresi üst sınırı (sn): takılan kare yayı sıçratmasın. */
    const val MAX_DT = 0.05f
    private const val SETTLE_POS = 0.002f
    private const val SETTLE_VEL = 0.01f

    private fun omega(shown: Float, target: Float) = if (target > shown) ATTACK_W else RELEASE_W

    /**
     * Kritik sönümlü yay, tam çözüm (kararlı, taşmaz; durgundan başlayan basamakta tekdüze yaklaşır): e = (e0 + (v0 + w·e0)·t)·e^(−w·t),
     * e = gösterilen − hedef. [dt] 0..[MAX_DT]'ye kırpılır. Yeni gösterilen değeri verir; hızı [springVelocity] verir (aynı girdilerle).
     */
    fun springStep(shown: Float, vel: Float, target: Float, dt: Float): Float {
        val t = dt.coerceIn(0f, MAX_DT)
        val w = omega(shown, target)
        val e0 = shown - target
        val ex = Math.exp((-w * t).toDouble()).toFloat()
        return target + (e0 + (vel + w * e0) * t) * ex
    }

    /** [springStep]'in eşi: aynı eski gösterilen değerle yeni hız. (Çağrıda [shown] adımdan ÖNCEKİ değer olmalıdır.) */
    fun springVelocity(shown: Float, vel: Float, target: Float, dt: Float): Float {
        val t = dt.coerceIn(0f, MAX_DT)
        val w = omega(shown, target)
        val e0 = shown - target
        val ex = Math.exp((-w * t).toDouble()).toFloat()
        return (vel - w * (vel + w * e0) * t) * ex
    }

    /** Yay oturdu mu (konum ve hız çok küçük): kare döngüsü durur. */
    fun isSettled(shown: Float, vel: Float, target: Float): Boolean =
        Math.abs(target - shown) < SETTLE_POS && Math.abs(vel) < SETTLE_VEL

    /** Ham 106 → halka doluluğu 0..1; geçersizse null. */
    fun fraction(raw: Int?): Float? = AwdMath.engineNm(raw)?.let { fractionOfNm(it) }

    /** Nm → doluluk 0..1 (negatif ve rölanti kıpırtısı 0). */
    fun fractionOfNm(nm: Int): Float = if (nm <= NOISE_NM) 0f else (nm.toFloat() / FULL_SCALE_NM).coerceIn(0f, 1f)

    /** Doluluğun yaydaki tam derecesi (0..[SWEEP_DEG]); çizim yalnız bu değişince yenilenir. */
    fun degrees(fraction: Float): Int = Math.round(fraction.coerceIn(0f, 1f) * SWEEP_DEG)

    /**
     * Demo torku (Nm): 5 sn'lik yumuşak dalga, ~30 → ~240 → ~30 (gaz verip bırakma). Rölanti taklidinde ([idle]) 0,
     * durgun ekranda ([still], otomatik test) sabit [DEMO_STILL_NM].
     */
    fun demoNm(nowMs: Long, idle: Boolean, still: Boolean): Int {
        if (idle) return 0
        if (still) return DEMO_STILL_NM
        val x = (nowMs % DEMO_PERIOD_MS).toDouble() / DEMO_PERIOD_MS
        return Math.round(DEMO_LOW_NM + (DEMO_HIGH_NM - DEMO_LOW_NM) * (0.5 - 0.5 * Math.cos(2 * Math.PI * x))).toInt()
    }

    const val DEMO_PERIOD_MS = 5_000L
    const val DEMO_LOW_NM = 30
    const val DEMO_HIGH_NM = 240
    const val DEMO_STILL_NM = 210
    /** Demoda bu sürüşün tepe torku işareti. */
    const val DEMO_PEAK_NM = 262
}
