package com.mapcontrol.launcher

import com.desaysv.ivi.extra.project.carinfo.CarSettingID
import com.desaysv.ivi.extra.project.carinfo.HvacID
import com.desaysv.ivi.extra.project.carinfo.NewEnergyID
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo

/** "Veri kontrolü" satır durumu: yeşil = geçerli veri, gri = veri yok / servis bağlı değil, sarı = geçersiz sentinel. */
internal enum class CheckStatus { VALID, NO_DATA, INVALID }

/**
 * Durum sınıflandırması ve ham değer birleştirme (saf işlevler, JVM'de sınanır).
 * Sentinel: tek baytlık 0xFF, iki baytlık 0xFFFF (tüm baytlar 0xFF) ya da -1. Cevap yoksa gri; cevap var ama
 * çözücü reddettiyse (aralık dışı) sarı.
 */
internal object CheckClassify {
    /** CarSignals.readInt ile aynı birleştirme: tek eleman olduğu gibi, çoklu baytlar büyükten küçüğe. */
    fun combine(raw: IntArray): Int =
        if (raw.size == 1) raw[0] else raw.fold(0) { acc, b -> (acc shl 8) or (b and 0xFF) }

    fun isSentinel(raw: IntArray): Boolean {
        if (raw.isEmpty()) return false
        if (raw.size == 1) return raw[0] == 0xFF || raw[0] == 0xFFFF || raw[0] == -1
        return raw.all { it == 0xFF } || combine(raw) == 0xFFFF
    }

    /** [raw] null/boş ise gri; sentinel ya da [decoded] null ise sarı; aksi hâlde yeşil. */
    fun classify(raw: IntArray?, decoded: String?): CheckStatus = when {
        raw == null || raw.isEmpty() -> CheckStatus.NO_DATA
        isSentinel(raw) || decoded == null -> CheckStatus.INVALID
        else -> CheckStatus.VALID
    }
}

/** Satır başına "son değişim" izi: imza (ham metin + durum) değişince zaman damgası yenilenir. */
internal class ChangeTracker {
    private val signature = HashMap<String, String>()
    private val changedAt = HashMap<String, Long>()

    /** [key] için son değişim zamanını (ms) döndürür; ilk görüşte [now]. */
    fun update(key: String, sig: String, now: Long): Long {
        if (signature[key] != sig) {
            signature[key] = sig
            changedAt[key] = now
        }
        return changedAt.getValue(key)
    }

    companion object {
        /** "şimdi", "3 sn", "2 dk", "1 sa" biçiminde kısa yaş; [initial] ise ilk okuma ("ilk"). */
        fun ageText(ageMs: Long): String {
            val s = (ageMs / 1000).coerceAtLeast(0)
            return when {
                s < 2 -> "şimdi"
                s < 60 -> "$s sn"
                s < 3600 -> "${s / 60} dk"
                else -> "${s / 3600} sa"
            }
        }
    }
}

/** Tablodaki bir VDBus sinyali. [decode] birleşik ham değeri okunur metne çevirir; geçersizse null. */
internal class CheckSignal(
    val group: String,
    val name: String,
    val module: Int,
    val id: Int,
    val decode: (Int) -> String?,
) {
    val moduleName: String get() = DataCheckTable.moduleName(module)
    val key: String get() = "$moduleName#$id"
}

/**
 * Veri kontrolü tablosu: tek veri yapısı; yeni sinyal eklemek bir satır eklemektir (grup, ad, modül, kimlik, çözücü).
 * Çözücüler yalnızca araçta ölçülen kodlamaları bilir; ölçülmeyenler ham sayıyı "?" ile gösterir ve sarı
 * boyamaz (aralık yoksa), yani yeşil nokta "cevap geldi" demektir, "anlamı doğrulandı" demek değildir.
 */
internal object DataCheckTable {

    /** HvacID.ID_CIRCULATION_MODE: yeni yazılımda devridaim durumu (bu SDK jar'ında yok); yalnızca veri denetimi tablosunda okunur. */
    private const val ID_CIRCULATION_MODE_NEW = 111
    private const val RO = VDEventCarInfo.MODULE_READONLY_INFO
    private const val SET = VDEventCarInfo.MODULE_CAR_SETTING
    private const val HVAC = VDEventCarInfo.MODULE_HVAC
    private const val NE = VDEventCarInfo.MODULE_NEW_ENERGY

    fun moduleName(module: Int): String = when (module) {
        RO -> "ReadOnly"
        SET -> "CarSetting"
        HVAC -> "Hvac"
        NE -> "NewEnergy"
        else -> "Modül$module"
    }

    // ---- Çözücüler (saf) ----

    /** Aralıkta ise "<değer> <birim>", değilse null. */
    fun num(range: IntRange, unit: String = ""): (Int) -> String? =
        { v -> if (v in range) (if (unit.isEmpty()) "$v" else "$v $unit") else null }

    /** Aralıkta ise ham/[div] (bir ondalık) + birim. */
    fun scaled(range: IntRange, div: Double, unit: String): (Int) -> String? =
        { v -> if (v in range) String.format(java.util.Locale.US, "%.1f %s", v / div, unit) else null }

    /** Etiket tablosu; tabloda olmayan değer null (sarı). */
    fun labels(vararg pairs: Pair<Int, String>): (Int) -> String? {
        val map = pairs.toMap()
        return { v -> map[v] }
    }

    /** Kodlaması ölçülmemiş sinyal: ham sayıyı gösterir (cevap geldi, anlamı doğrulanmadı). */
    val unverified: (Int) -> String? = { v -> "$v (anlam doğrulanmadı)" }

    /** Kapı/bagaj/kaput: 1 açık, 0 kapalı. */
    val openClosed = labels(0 to "Kapalı", 1 to "Açık")

    val onOff = labels(0 to "Kapalı", 1 to "Açık")

    /** Isıtma/soğutma kademesi 0..3. */
    val seatLevel: (Int) -> String? = { v -> if (v in 0..3) (if (v == 0) "Kapalı" else "Kademe $v") else null }

    /** Klima sıcaklığı: 15,5 + ham/2 (OurBottomBar ile aynı); ham 0 = LO, 30 = HI, üstü geçersiz. */
    val climateTemp: (Int) -> String? = { v ->
        when {
            v == ClimateMath.RAW_LO -> "LO"
            v == ClimateMath.RAW_HI -> "HI"
            v in 1 until ClimateMath.RAW_HI -> String.format(java.util.Locale.US, "%.1f °C", 15.5 + v / 2.0)
            else -> null
        }
    }

    val fuel: (Int) -> String? = { v ->
        if (v in 0..1000) String.format(java.util.Locale.US, "%%%.1f", v / 10.0) else null
    }

    /** Motor suyu: derece değil durum kodu ([CoolantStatus]); 2 normal, diğerleri ham kodla. */
    val coolant: (Int) -> String? = { v -> if (v == CoolantStatus.NORMAL) "Normal (kod $v)" else "kod $v" }

    val battery: (Int) -> String? = { v ->
        if (v in 80..165) String.format(java.util.Locale.US, "%.1f V", v / 10.0) else null
    }

    /** Sürüş modu (NewEnergy 4) değerleri; tek kaynak [DriveModes] (okuma = yazma; 6 bilinmiyor). */
    val driveMode = labels(*DriveMode.values().map { it.value to it.trName }.toTypedArray())

    /** Cam tavan operate: 2/5 kapalı, 3/6/7 açık, 4 hareket. */
    val sunroofOperate = labels(2 to "Kapalı", 5 to "Kapalı", 3 to "Açık", 6 to "Açık", 7 to "Açık", 4 to "Hareket")

    /** Cam tavan konum: 1 kapalı, 2..11 açık kademe, 12 havalandırma. */
    val sunroofPosi: (Int) -> String? = { v ->
        when (v) {
            1 -> "Kapalı"
            in 2..11 -> "Açık (kademe ${v - 1})"
            12 -> "Havalandırma"
            else -> null
        }
    }

    /** Tavan perdesi (araçta ölçüldü): 1 kapalı, 2 açılıyor, 3 yarı açık / durdu, 4 kapanıyor; 5/6 diğer araçlar. */
    val sunshade = labels(1 to "Kapalı", 2 to "Açılıyor", 3 to "Yarı açık / Durdu", 4 to "Kapanıyor", 5 to "Açık", 6 to "Açık (düğmeyle)")

    /** Kilit (IVI_LOCK 169): 1 açık, 0 kilitli. */
    val lock = labels(0 to "Kilitli", 1 to "Açık")

    /** Vites (ReadOnly 26): P=1, R=2, N=3, D=5. */
    val gear = labels(1 to "P", 2 to "R", 3 to "N", 5 to "D")

    /** El freni (ReadOnly 75): 1 çekili, 2 bırakılıyor, 0 inik, 3 çekiliyor. */
    val handbrake = labels(1 to "Çekili", 2 to "Bırakılıyor", 0 to "İnik", 3 to "Çekiliyor")

    /** Kablosuz şarj (CarSetting 44, OEM SystemUI): 0 pad boş, 1 şarj oluyor, 2 şarj tamamlandı, 3 hata. */
    val wirelessCharge = labels(0 to "Şarj pedi boş", 1 to "Telefon şarjda", 2 to "Şarj tamamlandı", 3 to "Şarj hatası")

    /** Cam (CarSetting 100..): 2 kapalı, diğerleri açık / hareket. */
    val window: (Int) -> String? = { v -> if (v == 2) "Kapalı" else "Açık ($v)" }

    // ---- Tablo ----

    val signals: List<CheckSignal> = buildList {
        fun s(group: String, name: String, module: Int, id: Int, decode: (Int) -> String?) =
            add(CheckSignal(group, name, module, id, decode))

        val car = "Araç kartı"
        s(car, "Menzil", RO, ReadOnlyID.ID_ENDURANCE_KM, num(0..2000, "km"))
        s(car, "Yakıt", RO, ReadOnlyID.ID_FUEL_PERCENT, fuel)
        s(car, "Kilit (IVI_LOCK)", SET, CarSettingID.ID_CAR_IVI_LOCK, lock)
        s(car, "Kapı sol ön", RO, ReadOnlyID.ID_DRIVE_DOOR_STATE, openClosed)
        s(car, "Kapı sağ ön", RO, ReadOnlyID.ID_PASSENGER_DOOR_STATE, openClosed)
        s(car, "Kapı sol arka", RO, ReadOnlyID.ID_LFFT_BEHIND_DOOR_STATE, openClosed)
        s(car, "Kapı sağ arka", RO, ReadOnlyID.ID_RIGHT_BEHIND_DOOR_STATE, openClosed)
        s(car, "Bagaj", RO, ReadOnlyID.ID_TRUNK_STATE, openClosed)
        s(car, "Cam tavan (SPF operate)", SET, CarSettingID.ID_CAR_SPF_OPERATE, sunroofOperate)
        s(car, "Cam tavan (SPF konum)", SET, CarSettingID.ID_CAR_SPF_POSI, sunroofPosi)
        s(car, "Lastik basıncı uyarısı", RO, ReadOnlyID.ID_TIRE_PRESSURE_WARNING, labels(0 to "Normal", 1 to "Uyarı"))

        s("Sürüş modu", "Sürüş modu", NE, NewEnergyID.ID_DRIVE_MODE, driveMode)

        val speed = "Hız ve tüketim"
        s(speed, "Gösterge hızı", RO, ReadOnlyID.ID_SPEED_GAUGE_DISPLAY) { TripDecode.speedKmh(it)?.let { v -> "$v km/sa" } }
        s(speed, "Anlık tüketim", RO, ReadOnlyID.ID_INSTANTANEOUS_CONSUMPTION) { TripDecode.instantL100(it)?.let { v -> fmt1(v, "L/100km") } }

        val trip = "Yolculuk"
        s(trip, "Mesafe (sıfırlamadan beri)", RO, ReadOnlyID.ID_GRAND_TOTAL_KM_AFTER_CLEAR) { TripDecode.tripKm(it)?.let { v -> fmt1(v, "km") } }
        s(trip, "Ort. tüketim", RO, ReadOnlyID.ID_AVERAGE_OIL_CONSUMPTION_AFTER_CLEAR) { TripDecode.avgL100(it)?.let { v -> fmt1(v, "L/100km") } }
        s(trip, "Ort. hız", RO, ReadOnlyID.ID_AVERAGE_SPEED_AFTER_CLEAR) { TripDecode.avgSpeedKmh(it)?.let { v -> "$v km/sa" } }

        val health = "Araç sağlığı"
        s(health, "Akü voltajı", RO, ReadOnlyID.ID_BATTERY_LEVEL, battery)
        s(health, "IBS (anlamı belirsiz)", RO, ReadOnlyID.ID_IBS_VOLTAGE) { v -> "ham $v" }
        s(health, "Soğutma suyu durumu", RO, ReadOnlyID.ID_WATER_TEMPERATURE, coolant)
        s(health, "Akü doluluk (IBS SOC)", RO, ReadOnlyID.ID_IBS_SOC, num(0..100, "%"))

        val service = "Bakım"
        s(service, "Servise kalan km", RO, ReadOnlyID.ID_ICMT_DISTANCE, num(0..99_999, "km"))
        s(service, "Servise kalan gün", RO, ReadOnlyID.ID_ICMT_DAY, num(0..9_999, "gün"))

        val air = "Hava kalitesi"
        s(air, "PM2.5 kabin içi", HVAC, HvacID.ID_AC_PM25_INCAR, num(0..999, "µg/m³"))
        s(air, "PM2.5 dış", HVAC, HvacID.ID_AC_PM25_OUTCAR, num(0..999, "µg/m³"))

        val seats = "Koltuklar"
        s(seats, "Sürücü ısıtma", SET, CarSettingID.ID_CAR_DRIVER_SEAT_HEAT, seatLevel)
        s(seats, "Yolcu ısıtma", SET, CarSettingID.ID_CAR_COPILOT_SEAT_HEAT, seatLevel)
        s(seats, "Sürücü havalandırma", SET, CarSettingID.ID_CAR_DRIVER_SEAT_VENTILATION, seatLevel)
        s(seats, "Yolcu havalandırma", SET, CarSettingID.ID_CAR_COPILOT_SEAT_VENTILATION, seatLevel)

        val climate = "Klima"
        s(climate, "Sıcaklık sürücü", HVAC, HvacID.ID_AC_TEMPERATURE_DRIVER, climateTemp)
        s(climate, "Sıcaklık yolcu", HVAC, HvacID.ID_AC_TEMPERATURE_COPILOT, climateTemp)
        s(climate, "Fan kademesi", HVAC, HvacID.ID_AC_BLOW_SPEED_LEVEL, num(0..10, "/10?")) // araçta 10 okundu (maks. ön buğu); ölçek doğrulanmadı
        s(climate, "A/C durumu", HVAC, HvacID.ID_AC_STATE, unverified)

        s("Tavan perdesi", "Perde", SET, CarSettingID.ID_CAR_SUNSHADE, sunshade)

        val defog = "Buğu"
        s(defog, "Ön cam ısıtma", HVAC, HvacID.ID_AC_FRONT_GLASS_HEAT, unverified)
        s(defog, "Maks. ön buğu çözme", HVAC, HvacID.ID_AC_MAX_FRONT_DEFOREST, onOff)
        s(defog, "Arka buğu çözme", HVAC, HvacID.ID_AC_REAR_DEFROST, onOff)
        s(defog, "Arka cam ısıtma", SET, CarSettingID.ID_CAR_REAR_WINDOW_HEAT, unverified)

        s("Direksiyon ısıtma", "Direksiyon ısıtma", SET, CarSettingID.ID_CAR_STEER_WHEEL_HEAT, unverified)

        s("Mola", "Sürüş süresi", RO, ReadOnlyID.ID_DRIVE_TIME, unverified)

        val limit = "Hız limiti"
        s(limit, "Navigasyon hız limiti", SET, CarSettingID.ID_NAV_SPEED_LIMIT, unverified)
        s(limit, "Hız limiti durumu", SET, CarSettingID.ID_NAV_SPEED_LIMIT_STATUS, unverified)

        val phone = "Telefon"
        s(phone, "Telefon unutuldu", SET, CarSettingID.ID_CAR_PHONE_FORGOT, unverified)
        s(phone, "Kablosuz şarj durumu", SET, CarSettingID.ID_CAR_WIRLESS_CHARGE_STATE, wirelessCharge)

        val body = "Gövde"
        s(body, "Cam sol ön", SET, CarSettingID.ID_CAR_FL_WINDOW, window)
        s(body, "Cam sağ ön", SET, CarSettingID.ID_CAR_FR_WINDOW, window)
        s(body, "Cam sol arka", SET, CarSettingID.ID_CAR_RL_WINDOW, window)
        s(body, "Cam sağ arka", SET, CarSettingID.ID_CAR_RR_WINDOW, window)
        s(body, "Kaput", RO, ReadOnlyID.ID_HOOD_STATE, openClosed)
        s(body, "El freni", RO, ReadOnlyID.ID_HANDBRAKE_STATE, handbrake)
        s(body, "Yakıt kapağı", SET, CarSettingID.ID_FUEL_FILLER_CAP, unverified)

        s("Vites", "Vites", RO, ReadOnlyID.ID_GEARBOX_STATE, gear)

        // Tiggo 8 yazılımının yeni sinyalleri: ham değer görülsün diye anlamı doğrulanmadı sayılır.
        val oem = "Yeni sinyaller"
        s(oem, "Lastik basıncı (140)", RO, ReadOnlyIds.TIRE_PRESSURE, unverified)
        s(oem, "Toplam km (150)", RO, ReadOnlyIds.TOTAL_MILEAGE, unverified)
        s(oem, "Toplam yakıt (153)", RO, ReadOnlyIds.TOTAL_FUEL, unverified)
        s(oem, "Sürüş süresi, çalışma (151)", RO, ReadOnlyIds.DRIVING_TIME_AFTER_RUNNING, unverified)
        s(oem, "Sürüş süresi, sıfırlama (152)", RO, ReadOnlyIds.DRIVING_TIME_AFTER_RESET, unverified)
        s(oem, "Klima Oto (HVAC 4)", HVAC, HvacID.ID_AC_AUTO_STATE, unverified)
        s(oem, "Klima gücü (HVAC 18)", HVAC, HvacID.ID_AC_WORKING, unverified)
        s(oem, "Devridaim (HVAC 8)", HVAC, HvacID.ID_AC_CIRCULATION_MODE, unverified)
        s(oem, "Devridaim (HVAC 111)", HVAC, ID_CIRCULATION_MODE_NEW, unverified)
    }

    /** Grup sırası, tablodaki ilk görünüşe göre. */
    val groups: List<String> get() = signals.map { it.group }.distinct()

    private fun fmt1(v: Double, unit: String) = String.format(java.util.Locale.US, "%.1f %s", v, unit)
}
