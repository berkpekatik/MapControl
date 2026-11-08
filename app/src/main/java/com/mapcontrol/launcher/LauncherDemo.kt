package com.mapcontrol.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.mapcontrol.media.LauncherMediaController.MediaState
import com.mapcontrol.vehicle.VehicleMetricsSnapshot

/**
 * Sunum ve ekran görüntüsü için sahte araç verisi (emülatörde her şey "—" görünmesin diye).
 *
 * Yalnızca debug (debuggable) derlemede ve başlatma isteğinde açıkça istenirse açılır:
 *   adb shell am start -n <paket>/com.mapcontrol.launcher.HomeLauncherActivity --ez launcher_demo true
 * Araç ekranı profili de verilebilir (`--es launcher_profile tiggo7|tiggo8`, bkz. [VehicleProfile.demoOverride]).
 * Açıkken widget'lar aracı değil buradaki sabit değerleri gösterir; komut gönderen yollar yine
 * LauncherEdition üzerinden geçer, demo onlara dokunmaz.
 */
internal object LauncherDemo {

    const val EXTRA = "launcher_demo"

    @Volatile
    var enabled = false
        private set

    /** Demo sürüş modu (ham değer 0..7, 6 tanımsız); `--ei launcher_demo_drive_mode N` ile verilir, yoksa Normal. */
    const val EXTRA_DRIVE_MODE = "launcher_demo_drive_mode"

    @Volatile
    var driveMode = 1
        private set

    /** Demo hareket durumu (`--ez launcher_demo_moving true|false`); verilmediyse null. Yalnızca demo açıkken. */
    const val EXTRA_MOVING = "launcher_demo_moving"

    @Volatile
    var moving: Boolean? = null
        private set

    fun applyMoving(intent: Intent?) {
        if (!enabled || intent?.hasExtra(EXTRA_MOVING) != true) return
        moving = intent.getBooleanExtra(EXTRA_MOVING, false)
    }

    /** Etkinlik her açıldığında çağrılır; istek demo içermiyorsa önceki durum korunur (tema değişimi). */
    fun applyFrom(activity: Activity, intent: Intent?) {
        if (intent?.hasExtra(EXTRA) != true) return
        val debuggable = activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        enabled = debuggable && intent.getBooleanExtra(EXTRA, false)
        if (!enabled) main.removeCallbacks(cycle)
        if (!enabled) main.removeCallbacks(roofSim)
        if (!enabled) NavigationSource.setDemo(false)
        if (enabled && intent.hasExtra(VehicleProfile.EXTRA_PROFILE)) VehicleProfile.demoOverride = VehicleProfile.parse(intent.getStringExtra(VehicleProfile.EXTRA_PROFILE))
        if (enabled && intent.hasExtra(EXTRA_WIFI)) wifi = intent.getBooleanExtra(EXTRA_WIFI, false)
        if (enabled && intent.hasExtra(EXTRA_CHARGING)) charging = intent.getBooleanExtra(EXTRA_CHARGING, true)
        if (enabled && intent.hasExtra(EXTRA_REAR_OFF)) rearOff = intent.getBooleanExtra(EXTRA_REAR_OFF, false)
        if (enabled && intent.hasExtra(EXTRA_TITLE)) title = intent.getStringExtra(EXTRA_TITLE)
        if (enabled) still = intent.getBooleanExtra(EXTRA_STILL, false)
        if (enabled && intent.hasExtra(EXTRA_ARTIST)) artist = intent.getStringExtra(EXTRA_ARTIST)
        if (enabled && intent.hasExtra(EXTRA_SOURCE)) source = intent.getStringExtra(EXTRA_SOURCE)
        if (enabled && intent.hasExtra(EXTRA_OEM_SOURCE)) oemSource = intent.getIntExtra(EXTRA_OEM_SOURCE, 0).takeIf { it > 0 }
        if (enabled && intent.hasExtra(EXTRA_USB)) usb = intent.getBooleanExtra(EXTRA_USB, false)
    }

    /** Demo şarkı adı (`--es launcher_demo_title "..."`): uzun başlığın kartta sığmasını denemek için. */
    const val EXTRA_TITLE = "launcher_demo_title"

    @Volatile
    var title: String? = null
        private set

    /** Demo sanatçı ve kaynak paketi (`--es launcher_demo_artist "..."`, `--es launcher_demo_source com.android.bluetooth`). */
    const val EXTRA_ARTIST = "launcher_demo_artist"
    const val EXTRA_SOURCE = "launcher_demo_source"

    @Volatile
    var artist: String? = null
        private set

    @Volatile
    var source: String? = null
        private set

    /**
     * Demo OEM ses kaynağı ve USB (müzik kartı radyo görünümü ve kaynak seçicisi için; araçta VDBus'tan gelir):
     * `--ei launcher_demo_oem_source 9` (FM 9, AM 10, BT 13, USB 3), `--ez launcher_demo_usb true`. Radyo örneği:
     * `--es launcher_demo_source com.desaysv.svaudioapp --es launcher_demo_title 89700 --es launcher_demo_artist fm`.
     * Seçicide yapılan seçim yalnızca demo kaynağını değiştirir (araca komut gitmez).
     */
    const val EXTRA_OEM_SOURCE = "launcher_demo_oem_source"
    const val EXTRA_USB = "launcher_demo_usb"

    @Volatile
    var oemSource: Int? = null

    @Volatile
    var usb = false
        private set

    /** Demo kablosuz şarj: varsayılan telefon pedde şarj oluyor; `--ez launcher_demo_charging false` pedi boş gösterir. */
    const val EXTRA_CHARGING = "launcher_demo_charging"

    @Volatile
    var charging = true
        private set

    /** Wi-Fi paneli önizlemesi: `--ez launcher_demo_wifi true` listeye örnek ağlar ekler (emülatörde tek sanal ağ var). */
    const val EXTRA_WIFI = "launcher_demo_wifi"

    @Volatile
    private var wifi = false

    /** Demo Bluetooth durumu: demo açıkken telefon bağlı görünür (Bluetooth panelindeki demo listesiyle aynı); aksi halde null (gerçek durum). */
    internal fun bluetooth(): BtUi? = if (enabled) BtUi.CONNECTED else null

    /** Bluetooth paneli önizlemesi: demo açıkken ve gerçek eşleşmiş cihaz yokken gösterilen örnek liste (adresler geçersiz, bağlanma denenmez). */
    internal fun bluetoothDevices(): List<BtDevice> = if (!enabled) emptyList() else listOf(
        BtDevice("DEMO-1", "Demo iPhone", BtKind.PHONE, phone = true, audio = true),
        BtDevice("DEMO-2", "AirPods", BtKind.AUDIO),
        BtDevice("DEMO-3", "Galaxy S23", BtKind.PHONE),
    )

    internal fun wifiScans(): List<WifiScan> = if (!enabled || !wifi) emptyList() else listOf(
        WifiScan("Ev_5G", 4, "[WPA2-PSK-CCMP][ESS]"),
        WifiScan("Kafe Misafir", 3, "[ESS]"),
        WifiScan("TurkNet_A1B2", 3, "[WPA2-PSK-CCMP][ESS]"),
        WifiScan("Ofis", 2, "[WPA3-SAE][ESS]"),
        WifiScan("iPhone", 2, "[WPA2-PSK-CCMP][ESS]"),
        WifiScan("Komşu_2.4", 1, "[WPA2-PSK-CCMP][ESS]"),
        WifiScan("DIRECT-roku", 1, "[WPA2-PSK-CCMP][ESS]"),
        WifiScan("Kitap Cafe", 1, "[ESS]"),
        WifiScan("Ev_5G", 2, "[WPA2-PSK-CCMP][ESS]"),
        WifiScan("", 3, "[ESS]"),
    )

    /** Demo arka hoparlör durumu (`--ez launcher_demo_rear_off true`): fader ön uçta, arka kapalı. Verilmezse açık. */
    const val EXTRA_REAR_OFF = "launcher_demo_rear_off"

    @Volatile
    var rearOff = false
        private set

    /** İstek sürüş modu içeriyorsa (yalnızca demo açıkken) alır; uygulanacak mod döner, yoksa null. */
    fun applyDriveMode(intent: Intent?): Int? {
        if (!enabled || intent?.hasExtra(EXTRA_DRIVE_MODE) != true) return null
        driveMode = intent.getIntExtra(EXTRA_DRIVE_MODE, 1).coerceIn(0, 7)
        return driveMode
    }

    /**
     * Gövde animasyonu önizlemesi: `--es launcher_demo_open "lf,rf,lr,rr,trunk,sunroof,hood"` (virgülle; boş = hepsi kapalı).
     * Sahne görsellerindeki adlar da geçer (fl, rl, fr, rr, door_fl …; bkz. [CarPart.parse]).
     * Çalışırken yeni istekle de değiştirilebilir; araç kartı ve Sahne aracı anında yeniden çizer.
     */
    const val EXTRA_OPEN = "launcher_demo_open"

    @Volatile
    var openParts: Set<String> = emptySet()
        private set

    /**
     * Panoramik tavan önizlemesi: `--es launcher_demo_roof "shade=30,glass=40"` (yüzde; perde 0 kapalı .. 100 açık, ön panel
     * 0 kapalı .. 100 tam geri). İsteğe bağlı `tilt` (havalandırma), `alert` / `rain` / `speed=120` (açık kenar kehribar).
     * Boş değer tavanı demo dışı bırakır (taban görseli). Bkz. [RoofDemo.parse]. `sim`: araçtaki adımlı bildirimlerin
     * döngülü taklidi (perde açılır, cam adım adım açılıp kapanır, perde kapanır; [roofSim]).
     */
    const val EXTRA_ROOF = "launcher_demo_roof"

    @Volatile
    var roof: RoofState? = null
        private set

    /** Yandan "kil beyazı" araç çiziminin önizlemesi: `--ez launcher_demo_side true` (varsayılan değil, yalnızca demo). */
    const val EXTRA_SIDE = "launcher_demo_side"

    @Volatile
    var sidePreview = false
        private set

    /**
     * Araçtaki boşta duruşun taklidi (`--ez launcher_demo_noise true`): motor rölantide, araç duruyor; devir 735–785, akü
     * 14,3–14,6 V, motor suyu 88–90 °C ve PM2.5 her saniye kıpırdar (araçta ölçülen oynamalar gibi). Boştaki kare sayısını
     * emülatörde ölçmek içindir: ekranda görünen bir şey değişmedikçe kare çizilmemeli.
     */
    const val EXTRA_NOISE = "launcher_demo_noise"

    @Volatile
    var noise = false
        private set

    /** Koltuk seviyesi önizlemesi: `--ei launcher_demo_seat N` (0..3) tüm ısıtma/soğutma koltuklarını N yapar. */
    const val EXTRA_SEAT = "launcher_demo_seat"

    /** Araç sağlığı önizlemesi: `--es launcher_demo_health battery|hot|service|overdue|multi`; verilmezse her şey normal. */
    const val EXTRA_HEALTH = "launcher_demo_health"

    @Volatile
    var healthProblem: String? = null
        private set

    @Volatile
    var seatOverride: Int? = null
        private set

    /**
     * Sahne uyarı önizlemesi: `--ez launcher_demo_alerts true` aynı anda sol ön kapı + bagaj açık, arka sol lastik düşük
     * (1,6 bar ≈ 23 psi), bakıma 800 km, yakıt az (%8, 60 km) ve kabin havası kötü (PM2.5 80) gösterir; araç parkta (el freni
     * çekili), böylece bagaj kapsülü belirgin tonda çıkar. `false` hepsini geri alır (açık parçalar `launcher_demo_open` ile
     * ayrıca da verilebilir; camlar için wlf/wrf/wlr/wrr). Örnek:
     *   adb shell am start -n <paket>/com.mapcontrol.launcher.HomeLauncherActivity --ez launcher_demo true --ez launcher_demo_alerts true
     * Hareket halini görmek için `--ez launcher_demo_moving true` eklenir (bagaj kapsülü normal tona döner).
     */
    const val EXTRA_ALERTS = "launcher_demo_alerts"

    @Volatile
    var alerts = false
        private set

    private val ALERT_PARTS = setOf("lf", "trunk")

    /** Uyarı önizlemesinin kendisinin açtığı parçalar: kapatınca yalnız bunlar kapanır (`launcher_demo_open` ile açılanlar kalır). */
    private var alertAdded: Set<String> = emptySet()

    /** Araç kartı bunu atar; demo isteği değişince ana iş parçacığında çağrılır. */
    @Volatile
    var onBodyChanged: Runnable? = null

    /**
     * [restored]: etkinlik kayıtlı durumdan yeniden kuruldu (tema yenilemesi ya da süreç ölümünden dönüş).
     */
    fun applyPreview(intent: Intent?, restored: Boolean = false) {
        if (!enabled || intent == null) return
        if (intent.hasExtra(EXTRA_OPEN)) {
            openParts = parseOpen(intent.getStringExtra(EXTRA_OPEN))
            alertAdded = emptySet()
            onBodyChanged?.let { main.post(it) }
        }
        if (intent.hasExtra(EXTRA_ALERTS)) {
            alerts = intent.getBooleanExtra(EXTRA_ALERTS, false)
            if (alerts) {
                alertAdded = alertAdded + (ALERT_PARTS - openParts)
                openParts = openParts + ALERT_PARTS
            } else {
                openParts = openParts - alertAdded
                alertAdded = emptySet()
            }
            onBodyChanged?.let { main.post(it) }
        }
        if (intent.hasExtra(EXTRA_ROOF)) {
            main.removeCallbacks(roofSim)
            val v = intent.getStringExtra(EXTRA_ROOF)
            if (RoofDemo.isSim(v)) {
                roofSimStep = 0
                roofSimTracker = RoofTracker()
                main.post(roofSim)
            } else {
                roof = RoofDemo.parse(v)
                onBodyChanged?.let { main.post(it) }
            }
        }
        if (intent.hasExtra(EXTRA_DOORS_CYCLE)) setDoorsCycle(intent.getBooleanExtra(EXTRA_DOORS_CYCLE, false))
        if (intent.hasExtra(EXTRA_CLIMATE)) climate = intent.getStringExtra(EXTRA_CLIMATE).orEmpty().split(',').map { it.trim().toDoubleOrNull() }
        if (intent.hasExtra(EXTRA_SIDE)) sidePreview = intent.getBooleanExtra(EXTRA_SIDE, false)
        if (intent.hasExtra(EXTRA_HEALTH)) healthProblem = intent.getStringExtra(EXTRA_HEALTH)
        if (intent.hasExtra(EXTRA_NOISE)) noise = intent.getBooleanExtra(EXTRA_NOISE, false)
        if (intent.hasExtra(EXTRA_SEAT)) seatOverride = intent.getIntExtra(EXTRA_SEAT, 0).coerceIn(0, 3)
        if (intent.hasExtra(EXTRA_NAV)) NavigationSource.setDemo(intent.getBooleanExtra(EXTRA_NAV, false))
        if (intent.hasExtra(EXTRA_LIGHTS)) lights = CarLights.parse(intent.getStringExtra(EXTRA_LIGHTS))
        if (intent.getBooleanExtra(EXTRA_WELCOME, false)) welcomeRequested = true
        if (intent.hasExtra(PhoneDemo.EXTRA_PHONE) || intent.hasExtra(PhoneDemo.EXTRA_CALL)) {
            PhoneDemo.apply(intent)
            PhoneSource.demoChanged()
        }
    }

    /**
     * Karşılama önizlemesi (§7.4): `--ez launcher_demo_welcome true` Sahne karşılamasını kontak beklemeden bir kez daha
     * oynatır (etkinlik soğuk açılıyorsa ekran baştan siyah tutulur).
     */
    const val EXTRA_WELCOME = "launcher_demo_welcome"

    @Volatile
    var welcomeRequested = false

    /**
     * Dijital ikiz ışıkları önizlemesi: `--es launcher_demo_lights "head,brake,left,right,reverse,hazard"` (virgülle; boş =
     * hepsi kapalı). hazard dörtlü anahtarıdır (iki taraf yanıp söner); kilitlenince tek yanış `launcher_demo_open` ile bir
     * kapı açılıp kapanınca görülür.
     */
    const val EXTRA_LIGHTS = "launcher_demo_lights"

    @Volatile
    var lights: CarLights = CarLights.OFF
        private set

    /**
     * Navigasyon paneli önizlemesi: `--ez launcher_demo_nav true` sahte rota gösterir (300 m sonra sağa, Atatürk Cd.,
     * Varış 14:52 · 18 km; [NavigationState.DEMO]), `false` gerçek bildirim durumuna döner.
     */
    const val EXTRA_NAV = "launcher_demo_nav"

    /** Virgüllü parça listesi → kart anahtarları (lf, lr, rf, rr, trunk, hood; tanınmayan olduğu gibi, ör. sunroof). */
    internal fun parseOpen(raw: String?): Set<String> =
        raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { CarPart.parse(it)?.demoKey ?: it }.toSet()

    /**
     * Kapı animasyonu döngüsü (`--ez launcher_demo_doors_cycle true`): ~1,5 sn arayla parçalar sırayla açılır, sonra aynı
     * sırayla kapanır; kayıt/izleme içindir. `false` döngüyü durdurur (son durum kalır).
     */
    const val EXTRA_DOORS_CYCLE = "launcher_demo_doors_cycle"
    const val DOORS_CYCLE_STEP_MS = 1_500L

    private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private var cycleStep = 0
    private val cycle = object : Runnable {
        override fun run() {
            val all = CarPart.values()
            val part = all[cycleStep % all.size]
            val opening = cycleStep / all.size % 2 == 0
            openParts = if (opening) openParts + part.demoKey else openParts - part.demoKey
            cycleStep++
            onBodyChanged?.run()
            main.postDelayed(this, DOORS_CYCLE_STEP_MS)
        }
    }

    private fun setDoorsCycle(on: Boolean) {
        main.removeCallbacks(cycle)
        if (!on) return
        cycleStep = 0
        openParts = emptySet()
        onBodyChanged?.let { main.post(it) }
        main.postDelayed(cycle, DOORS_CYCLE_STEP_MS)
    }

    /**
     * Tavan adım taklidi (`--es launcher_demo_roof sim`): araç günlüğündeki ham değerler ([RoofDemo.SIM_STEPS]) gerçek
     * aralıklarla [RoofTracker]'dan geçirilir; Sahne aracı araçtaki gibi seyrek adım alır (akıcılığı emülatörde görmek için).
     * Başka bir `launcher_demo_roof` değeri taklidi durdurur.
     */
    private var roofSimStep = 0
    private var roofSimTracker = RoofTracker()
    private val roofSim = object : Runnable {
        override fun run() {
            val steps = RoofDemo.SIM_STEPS
            val s = steps[roofSimStep % steps.size]
            roofSimStep++
            roof = roofSimTracker.update(s[1], s[2], s[3], 0, false, android.os.SystemClock.uptimeMillis())
            onBodyChanged?.run()
            main.postDelayed(this, s[0].toLong())
        }
    }

    private fun open(key: String) = if (key in openParts) 1 else 0

    /** Park halinde, kapılar kapalı, Normal mod, tavan kapalı bir Tiggo 7. */
    fun vehicle(): VehicleMetricsSnapshot = VehicleMetricsSnapshot(
        true,   // connected
        0, 0,   // hız
        18_420, // km
        if (alerts) 80 else 620,  // yakıt, onda bir (%62,0) — araçtaki gibi; uyarı önizlemesinde %8
        if (alerts) 60 else 540,  // menzil km
        0, 2, 0,
        open("lf"), open("rf"), open("lr"), open("rr"), open("trunk"), // kapılar ve bagaj (varsayılan kapalı)
        if ("sunroof" in openParts) 3 else 2, if ("sunroof" in openParts) 5 else 1, 1,       // tavan (varsayılan kapalı)
        0, 0, 0,
        driveMode,     // sürüş modu (varsayılan Normal)
    )

    /** Demo kilit: kapılardan biri açıksa kilit açık (araçta kapı açıkken kilitli görünmez), değilse kilitli. */
    val LOCKED: Boolean get() = listOf("lf", "rf", "lr", "rr").none { it in openParts }
    /** Lastik uyarı bayrağı (ReadOnly 95): yalnızca uyarı önizlemesinde açık. */
    val TIRE_WARNING: Boolean get() = alerts

    /** Demo lastik basınçları (bar; ön sol, ön sağ, arka sol, arka sağ): uyarı önizlemesinde arka sol düşük. */
    val TIRES: List<Double?> get() = if (alerts) listOf(2.4, 2.4, 1.6, 2.2) else listOf(2.4, 2.4, 2.3, 2.2)

    /**
     * Demo koltuk seviyeleri (0..3): sürücü ısıtma 2, yolcu soğutma 1. Koltuk seçicisinden yazılan seviye burada tutulur ki
     * demo okuma onu geri getirsin (araçtaki gibi). `launcher_demo_seat` ısıtmayı ikisine birden zorlar, soğutmayı kapatır.
     */
    private val demoSeat = intArrayOf(2, 0, 0, 1) // ısıtma sürücü, ısıtma yolcu, soğutma sürücü, soğutma yolcu

    fun seatLevel(vent: Boolean, driver: Boolean): Int =
        seatOverride?.let { if (vent) 0 else it } ?: demoSeat[(if (vent) 2 else 0) + (if (driver) 0 else 1)]

    fun setSeatLevel(vent: Boolean, driver: Boolean, level: Int) {
        seatOverride = null
        demoSeat[(if (vent) 2 else 0) + (if (driver) 0 else 1)] = level.coerceIn(0, 3)
    }

    val DRIVER_SEAT_LEVEL get() = seatLevel(vent = false, driver = true)
    val PASSENGER_SEAT_LEVEL get() = seatLevel(vent = false, driver = false)
    /** Klima önizlemesi: `--es launcher_demo_climate "17.5,27.5,7"` sürücü °C, yolcu °C, fan kademesi (boş alan varsayılan). */
    const val EXTRA_CLIMATE = "launcher_demo_climate"

    @Volatile
    private var climate: List<Double?> = emptyList()

    val DRIVER_TEMP: Double get() = climate.getOrNull(0) ?: 22.0
    val PASSENGER_TEMP: Double get() = climate.getOrNull(1) ?: 24.5
    val FAN_LEVEL: Int get() = climate.getOrNull(2)?.toInt() ?: 3
    val DRIVER_VENT_LEVEL get() = seatLevel(vent = true, driver = true)
    val PASSENGER_VENT_LEVEL get() = seatLevel(vent = true, driver = false)

    /** Anlık tüketim panelinin demo geçmişi (L/100 km, eskiden yeniye): şehir içi dalgalanma ve yolda düşüş. */
    val CONSUMPTION_HISTORY: List<Float> = listOf(
        9.8f, 11.2f, 8.4f, 7.6f, 12.4f, 9.1f, 7.2f, 6.8f, 6.4f, 7.9f, 6.6f, 6.1f,
        5.9f, 6.3f, 7.4f, 6.9f, 6.2f, 5.8f, 6.5f, 7.1f, 6.8f, 6.4f, 6.9f, 6.8f,
    )

    /** Konfor kutucukları: bagaj kapalı, klima tuşları, ısıtmalar kapalı, arka hoparlör demo anahtarına göre. */
    fun comfort(): ComfortReading = ComfortReading(
        connected = true,
        // Bagaj, sahne aracıyla aynı demo anahtarından (`launcher_demo_open "trunk"`): tek kaynak.
        trunk = if ("trunk" in openParts) TrunkReading(position = TrunkMath.FULL_OPEN, latchOpen = 1, motion = TrunkMath.MOTION_STOPPED)
        else TrunkReading(position = 0, latchOpen = 0, motion = TrunkMath.MOTION_STOPPED),
        sound = SoundFieldReading(balance = SoundFieldMath.CENTER, fade = if (rearOff) SoundFieldMath.FRONT_EXTREME else SoundFieldMath.CENTER),
    )

    /**
     * Anlık tüketim için 60 sn'lik döngü: duruş, kalkış (düşük hızda absürt değerler), seyir (gürültülü ~7), kısa
     * ani yükseliş (eşik üstü 30+ sınırı) ve tekrar duruş. `launcher_demo_moving false` hep duruyor gösterir.
     */
    private fun tripLive(): Triple<Boolean, Int, Double?> {
        if (moving == false) return Triple(false, 0, null)
        val t = (System.currentTimeMillis() / 1000 % 60).toInt()
        return when {
            t < 6 -> Triple(false, 0, null)
            t < 18 -> Triple(true, 8 + (t - 6), listOf(95.4, 45.0, 30.8, 21.0, 17.5)[t % 5])
            t < 48 -> Triple(true, 52 + t % 7, 6.5 + (t % 4) * 0.4 + if (t in 36..37) 25.0 else 0.0)
            t < 54 -> Triple(true, 25, 12.0)
            else -> Triple(false, 0, null)
        }
    }

    fun trip(): TripData {
        val (isMoving, speed, instant) = tripLive()
        val base = tripBase()
        val sick = when (healthProblem) {
            "battery" -> base.copy(batteryV = 11.4)
            // Motor suyu derece sinyali yok ([CoolantStatus]): "hot" artık uyarı üretmez.
            "hot" -> base
            "service" -> base.copy(serviceKm = 640, serviceDays = 22)
            "overdue" -> base.copy(serviceKm = 0, serviceDays = 0)
            "multi" -> base.copy(batteryV = 11.4, serviceKm = 640)
            else -> base
        }
        val alerted = if (alerts) sick.copy(serviceKm = 800, serviceDays = 40, lowFuel = true, pm25In = 80, pm25Out = 95) else sick
        if (noise) {
            val t = (System.currentTimeMillis() / 1000 % 60).toInt()
            return alerted.copy(moving = false, speedKmh = 0, instantL100 = null, lights = lights,
                batteryV = 14.3 + (t % 4) * 0.1, coolantCode = CoolantStatus.NORMAL, pm25In = 1 + t % 3, pm25Out = 4 + t % 2)
        }
        return alerted.copy(moving = isMoving, speedKmh = speed, instantL100 = instant, lights = lights)
    }

    private fun tripBase(): TripData = TripData(
        connected = true, moving = true, instantL100 = 6.8, driveAvgL100 = 7.4, tripKm = 53.8,
        avgL100 = 7.9, avgSpeedKmh = 45, batteryV = 14.1, coolantCode = CoolantStatus.NORMAL, lowFuel = false,
        serviceKm = 3978, serviceDays = 155, pm25In = 1, pm25Out = 4, outsideC = 17.0,
        totalKm = 18_420, totalFuelL = 1_284.6, drivingRunMin = 47, drivingResetMin = 3_552,
    )

    /** Demo kapak: ekran görüntülerinde boş yer tutucu yerine gerçekçi bir albüm resmi (bir kez çizilir). */
    private val artwork: android.graphics.Bitmap by lazy {
        val size = 240
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), 0xFF3B1E6E.toInt(), 0xFFFF7A3D.toInt(), android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        p.shader = null
        p.color = 0x33FFFFFF
        c.drawCircle(size * 0.64f, size * 0.42f, size * 0.30f, p)
        p.color = 0x55000000
        c.drawCircle(size * 0.30f, size * 0.78f, size * 0.22f, p)
        bmp
    }

    /** Ses çalıyormuş gibi; konum gerçek zamanla ilerlemesin diye her çağrıda aynı başlangıç. */
    /**
     * Otomatik test için durgun ekran (`--ez launcher_demo_still true`): demo müziği duraklatılmış görünür, geçen süre her
     * saniye değişmez. uiautomator ekran ağacını ancak arayüz yarım saniye sessiz kalınca alabilir.
     */
    const val EXTRA_STILL = "launcher_demo_still"

    @Volatile
    var still = false
        private set

    fun media(): MediaState = MediaState(
        true, true, !still,
        title ?: "Yolun Sonu", artist ?: "Demo Sanatçı", source ?: "com.google.android.apps.youtube.music",
        72_000L, 215_000L, artwork,
    )
}
