package com.mapcontrol.launcher

import androidx.annotation.StringRes
import com.mapcontrol.R
import com.mapcontrol.vehicle.DriveModeStyle

/**
 * Sürüş modlarının TEK kaynağı (saf; JVM'de sınanır): NewEnergy 4 (ID_DRIVE_MODE) için okunan ve yazılan değer AYNIDIR.
 *
 * 7 Kasım araç ölçümü (fiziksel düğmeyle, OEM Container driveModeValue): Eko 0, Normal 1, Spor 2, Kar 3, Çamur 4, Arazi 5, Kum 7;
 * 6 hiç görülmedi (bilinmeyen sayılır). FrostNav'ın yazması ve OEM QuickSettingFragment seçicisi (Item kimliği = gönderilen değer)
 * aynı tabloyu kullanır ([DriveModeStyle] sabitleri). 29 Ekim'deki ayrı okuma haritası (5 = Kum, 6 = Arazi) yanlıştı.
 *
 * [DriveAccent], [DriveModeTile] ve veri denetimi tablosu (DataCheckTable) değeri buradan çözer.
 */
internal enum class DriveMode(val value: Int, @StringRes val label: Int, val trName: String) {
    ECO(DriveModeStyle.ECO, R.string.launcher_drive_eco, "Eco"),
    NORMAL(DriveModeStyle.NORMAL, R.string.launcher_drive_normal, "Normal"),
    SPORT(DriveModeStyle.SPORT, R.string.launcher_drive_sport, "Sport"),
    SNOW(DriveModeStyle.SNOW, R.string.launcher_drive_snow, "Kar"),
    MUD(DriveModeStyle.MUD, R.string.launcher_drive_mud, "Çamur"),
    OFFROAD(DriveModeStyle.OFFROAD, R.string.launcher_drive_offroad, "Arazi"),
    SAND(DriveModeStyle.SAND, R.string.launcher_drive_sand, "Kum"),
}

internal object DriveModes {
    // Mod değerleri (okuma = yazma; 7 Kasım ölçümü). 6 tanımsız.
    const val ECO = DriveModeStyle.ECO
    const val NORMAL = DriveModeStyle.NORMAL
    const val SPORT = DriveModeStyle.SPORT
    const val SNOW = DriveModeStyle.SNOW
    const val MUD = DriveModeStyle.MUD
    const val OFFROAD = DriveModeStyle.OFFROAD
    const val SAND = DriveModeStyle.SAND

    /** Okunan değerin modu; bilinmeyen değer (6 dahil) null. */
    fun fromRead(value: Int?): DriveMode? = value?.let { v -> DriveMode.values().firstOrNull { it.value == v } }

    /** Yazılabilecek bütün değerler (beyaz liste: [WidgetWritePolicy]). */
    val WRITE_VALUES: Set<Int> = DriveMode.values().map { it.value }.toSet()

    /** Tiggo 7 varsayılanı (config okunamazsa): Eko / Normal / Spor. */
    val TIGGO7_DEFAULT: List<DriveMode> = listOf(DriveMode.ECO, DriveMode.NORMAL, DriveMode.SPORT)

    /**
     * Araçta seçilebilen modlar, OEM sırasıyla: CarConfig 109 (configs4[5] & 31) → OEM QuickSettingFragment.initOfflineConfig.
     * 0 ya da tanınmayan değer: OEM seçiciyi gizler → boş liste (kutucuk yalnız gösterir). Okunamadı (null): Tiggo 7 varsayılanı.
     */
    fun available(config109: Int?): List<DriveMode> = when (config109) {
        null -> TIGGO7_DEFAULT
        1 -> listOf(DriveMode.ECO)
        2 -> listOf(DriveMode.SPORT)
        3, 7 -> listOf(DriveMode.ECO, DriveMode.SPORT)
        4, 5, 6 -> TIGGO7_DEFAULT
        8 -> listOf(DriveMode.NORMAL, DriveMode.SPORT, DriveMode.SNOW, DriveMode.MUD, DriveMode.OFFROAD)
        9 -> listOf(DriveMode.ECO, DriveMode.NORMAL, DriveMode.SPORT, DriveMode.SNOW, DriveMode.MUD, DriveMode.OFFROAD)
        10 -> listOf(DriveMode.ECO, DriveMode.NORMAL, DriveMode.SPORT, DriveMode.SNOW, DriveMode.MUD, DriveMode.SAND, DriveMode.OFFROAD)
        11 -> listOf(DriveMode.ECO, DriveMode.NORMAL, DriveMode.SPORT, DriveMode.SNOW, DriveMode.SAND, DriveMode.OFFROAD)
        else -> emptyList()
    }

    /** CarConfig 109 = configs4[5] & 31 (OEM EolConfig); blok yok ya da kısa (< 8 bayt) → null (bilinmiyor). */
    fun config109(configs4: IntArray?): Int? =
        if (configs4 == null || configs4.size < 8) null else configs4[5] and 31

    /**
     * Dokunuşta gidilecek mod: [current] (okunan mod ya da bekleyen hedef) listede sıradakine geçer, sondan başa döner.
     * Mevcut mod listede yoksa listenin ilki. Mod bilinmiyorsa ya da seçilebilen mod yoksa null (hiçbir şey gönderilmez);
     * tek seçenekli listede zaten o moddaysa da null.
     */
    fun next(current: DriveMode?, modes: List<DriveMode>): DriveMode? {
        if (current == null || modes.isEmpty()) return null
        val i = modes.indexOf(current)
        val to = if (i < 0) modes.first() else modes[(i + 1) % modes.size]
        return to.takeIf { it != current }
    }
}

/**
 * Bizim widget'ımızdan istenen son sürüş modu değişiminin zamanı (monoton saat, ms) ve toplam istek sayısı. OEM sürüş modu
 * penceresi ([OemDriveDialogCloser]) bu isteklerden sonra gelen değişimlerde (istek başına en çok bir) spor ekranından
 * bağımsız kapatılır; sayaç hızlı ardışık dokunuşlarda (Eko→Normal→Spor) her değişimin penceresinin kapanmasını sağlar.
 */
internal object DriveModeRequests {
    @Volatile
    var lastAt: Long? = null
        private set

    @Volatile
    var count: Int = 0
        private set

    /** Ana iş parçacığından çağrılır (widget dokunuşu). */
    fun mark(now: Long) {
        lastAt = now
        count++
    }
}
