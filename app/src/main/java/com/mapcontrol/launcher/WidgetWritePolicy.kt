package com.mapcontrol.launcher

import com.desaysv.ivi.extra.project.carinfo.CarSettingID
import com.desaysv.ivi.extra.project.carinfo.HvacID
import com.desaysv.ivi.extra.project.carinfo.NewEnergyID
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import com.desaysv.ivi.vdb.event.id.dsp.VDEventDsp
import com.mapcontrol.comfort.ComfortPlan
import com.mapcontrol.comfort.ParkGate

/**
 * Launcher widget'larından araca yazmanın SAF kuralları (JVM'de sınanır): beyaz liste, doğrulama bayrağı, park kapısı,
 * kapı birleşimi ve günlük satırı. İki derleme de ([WidgetWriter] üzerinden) kullanır. Liste, widget'ların GÖNDEREBİLECEĞİ
 * (modül, kimlik, değer) çiftlerinden türetildi.
 *
 * Kurallar:
 *  - Araçta geri okumayla doğrulanmamış girdi ([Entry.verified] false) hiçbir koşulda gönderilmez.
 *  - Bagaj, merkezi kilit ve sürüş modu ([Entry.parkOnly]) yalnızca araç dururken (hız 0) ve vites P'deyken gider
 *    ([ParkGate]); hız ya da vites okunamazsa reddedilir ve kullanıcıya neden gösterilir.
 *  - Diğer doğrulanmış widget'lar (koltuk, klima dock'u) sürüşte de kullanılır.
 */
internal object WidgetWritePolicy {

    const val LOG_TAG = "WidgetWrite"

    private const val CS = VDEventCarInfo.MODULE_CAR_SETTING
    private const val HV = VDEventCarInfo.MODULE_HVAC
    private const val RO = VDEventCarInfo.MODULE_READONLY_INFO
    private const val NE = VDEventCarInfo.MODULE_NEW_ENERGY

    /** Beyaz liste girdisi: widget adı, modül, kimlik, izinli değerler ve yazmadan sonra 3 sn izlenecek sinyaller. */
    class Entry(
        val widget: String,
        val module: Int,
        val id: Int,
        val values: Set<Int>,
        val readBack: List<ComfortPlan.Signal>,
        /** Araçta geri okumayla doğrulandı mı; false iken kapıdan geçmez. */
        val verified: Boolean,
        /** true: yalnızca park halinde (hız 0, vites P) gönderilir. */
        val parkOnly: Boolean = false,
    ) {
        fun matches(m: Int, i: Int, v: Int) = module == m && id == i && v in values
    }

    private fun sig(name: String, module: Int, id: Int) = ComfortPlan.Signal(name, module, id)

    private val TRUNK_READ = listOf(
        sig("ro98_plg_position", RO, ReadOnlyID.ID_PLG_POSITION),
        sig("ro19_trunk_state", RO, ReadOnlyID.ID_TRUNK_STATE),
        sig("cs92_rear_door_control", CS, CarSettingID.ID_CAR_REAR_DOOR_CONTROL),
    )

    /**
     * Koltuk ısıtma/soğutma: ham seviye 0..3 ya da sarmalanmış 1..4 (ikisi de izinli). Yazma araçta doğrulandı: geri okuma
     * 250 ms içinde yeni seviyeyi gösterdi.
     */
    private val SEAT_VALUES = (0..4).toSet()

    private val TEMP_VALUES = (ClimateMath.RAW_LO..ClimateMath.RAW_HI).toSet()

    /**
     * HVAC 9 geri okuması: bit alanı ([ClimatePanelBits]; bit 0 ön sıra, bit 1 PM2.5, bit 2 koku). Bitlerden biri gelince
     * bizim araçta OEM klima panelini açar; panel/dock kimliklerinin araçta
     * karşılaştırması için izlenir. Günlükte çözülmüş hâli de yazılır ([WidgetWriter]).
     */
    private val PANEL_ACTIVE = sig("hvac9_display_active", HV, HvacID.ID_AC_DISPLAY_ACTIVE)

    /**
     * Merkezi kilit (CarSetting 169) araçta doğrulandı: 2 yazınca kilitlendi, 1 yazınca açıldı; geri okumada 169 her
     * seferinde yeni değere geçti.
     */
    const val LOCK_WRITE_VERIFIED = true

    /** Sürüş modu (NewEnergy 4) araçta doğrulandı: her yazılan mod geri okumada aynı değerle göründü. */
    const val DRIVE_MODE_WRITE_VERIFIED = true

    /** Direksiyon ısıtma (CarSetting 160) yalnız Tiggo 8'de var; araçta denenmedi. */
    const val STEER_HEAT_WRITE_VERIFIED = false

    val ENTRIES: List<Entry> = listOf(
        seat("SeatHeatDriver", CarSettingID.ID_CAR_DRIVER_SEAT_HEAT),
        seat("SeatHeatPassenger", CarSettingID.ID_CAR_COPILOT_SEAT_HEAT),
        seat("SeatVentDriver", CarSettingID.ID_CAR_DRIVER_SEAT_VENTILATION),
        seat("SeatVentPassenger", CarSettingID.ID_CAR_COPILOT_SEAT_VENTILATION),
        // Klima: sıcaklık ham = (°C - 15,5) * 2 → LO = 0, 16,0..30,0 °C = 1..29, HI = 30 (OEM aralığı; 31..33 yok); fan 0..10.
        Entry("ClimateTempDriver", HV, HvacID.ID_AC_TEMPERATURE_DRIVER, TEMP_VALUES,
            listOf(sig("hvac10_temp_driver", HV, HvacID.ID_AC_TEMPERATURE_DRIVER), PANEL_ACTIVE), verified = false),
        Entry("ClimateTempPassenger", HV, HvacID.ID_AC_TEMPERATURE_COPILOT, TEMP_VALUES,
            listOf(sig("hvac13_temp_passenger", HV, HvacID.ID_AC_TEMPERATURE_COPILOT), PANEL_ACTIVE), verified = false),
        // Klima gücü (HVAC 18): 1 aç / 2 kapat. Panel yolunda sıcaklık/fan yazmadan önce otomatik 1 de buradan geçer (dock yolunda gönderilmez).
        Entry("ClimatePower", HV, HvacID.ID_AC_WORKING, setOf(ClimateMath.POWER_ON, ClimateMath.POWER_OFF),
            listOf(sig("hvac18_power", HV, HvacID.ID_AC_WORKING)), verified = false),
        // Klima aç/kapat, FrostNav sırası (ClimateMath.powerPlan): açmada 18 ← 1'den ~150 ms sonra A/C 1 ← 1 ve otomatik 4 ← 1;
        // kapatmada üçü de 2. 0 yazılmaz (aracın klima paneliyle aynı sıra).
        Entry("ClimateAc", HV, HvacID.ID_AC_STATE, setOf(ClimateMath.AC_ON, ClimateMath.AC_OFF),
            listOf(sig("hvac1_ac_state", HV, HvacID.ID_AC_STATE), sig("hvac18_power", HV, HvacID.ID_AC_WORKING)), verified = false),
        Entry("ClimateAuto", HV, HvacID.ID_AC_AUTO_STATE, setOf(ClimateMath.AC_ON, ClimateMath.AC_OFF),
            listOf(sig("hvac4_auto_state", HV, HvacID.ID_AC_AUTO_STATE), sig("hvac12_blow_speed", HV, HvacID.ID_AC_BLOW_SPEED_LEVEL)), verified = false),
        Entry("ClimateFan", HV, HvacID.ID_AC_BLOW_SPEED_LEVEL, (1..10).toSet(),
            listOf(sig("hvac12_blow_speed", HV, HvacID.ID_AC_BLOW_SPEED_LEVEL), PANEL_ACTIVE), verified = false),
        // Klima, "fabrika paneli: Gizle": OEM alt dock'unun kimlikleri, aynı ham değerler (bizim
        // sürücü 76, yolcu 78; fan 77 = elektrikli düzenin dock fanı, otomatik klimada OEM dock 12 yazar, bkz.
        // ClimateWriter.fanId). Sonuç panel kimliklerinden (10/13/12) geri okunur. HVAC 9'u biz yazmayız: panel kapatmada 9 ← 2'yi
        // OEM klima servisi kendisi gönderir. Dock yazmaları araçta doğrulandı: geri okumada 10/13/12 yeni değere geçti.
        Entry("ClimateTempDriverDock", HV, HvacID.ID_AC_DRIVER_SET_TEMPERATURE, TEMP_VALUES,
            listOf(sig("hvac10_temp_driver", HV, HvacID.ID_AC_TEMPERATURE_DRIVER), PANEL_ACTIVE), verified = ClimateWriter.CLIMATE_DOCK_WRITE_VERIFIED),
        Entry("ClimateTempPassengerDock", HV, HvacID.ID_AC_PASSENGER_SET_TEMPERATURE, TEMP_VALUES,
            listOf(sig("hvac13_temp_passenger", HV, HvacID.ID_AC_TEMPERATURE_COPILOT), PANEL_ACTIVE), verified = ClimateWriter.CLIMATE_DOCK_WRITE_VERIFIED),
        Entry("ClimateFanDock", HV, HvacID.ID_AC_SET_BLOW_LEVEL, (1..10).toSet(),
            listOf(sig("hvac12_blow_speed", HV, HvacID.ID_AC_BLOW_SPEED_LEVEL), PANEL_ACTIVE), verified = ClimateWriter.CLIMATE_DOCK_WRITE_VERIFIED),
        // Bagaj: yalnızca CarSetting 92, 1 aç / 2 kapat (OEM ayarlar / hızlı ayarlarla aynı). Araçta doğrulandı: 1 sonrası
        // kapak açıklığı (RO 98) arttı, 2 sonrası azaldı. Yalnızca park halinde.
        Entry("Trunk", CS, CarSettingID.ID_CAR_REAR_DOOR_CONTROL, setOf(TrunkMath.CMD_OPEN, TrunkMath.CMD_CLOSE), TRUNK_READ,
            verified = ComfortWriter.TRUNK_WRITE_VERIFIED, parkOnly = true),
        // Arka hoparlör: DSP SOUND_FIELD (modül = olay kimliği, kimlik sabit; değer = fade 0..20, balance gönderirken korunur). Uç değeri doğrulanmadı.
        Entry("RearSpeaker", VDEventDsp.SOUND_FIELD, SoundFieldMath.WRITE_ID, (0..SoundFieldMath.MAX).toSet(),
            listOf(sig("dsp_sound_field", VDEventDsp.SOUND_FIELD, SoundFieldMath.WRITE_ID)), verified = ComfortWriter.REAR_SPEAKER_WRITE_VERIFIED),
        // Spor ambiyansı: CarSetting 11 renk sırası (OEM tablosu 1..64; spor rengi 45, geri yazmada hatırlanan sıra). Doğrulanmadı.
        Entry("SportAmbient", CS, CarSettingID.ID_CAR_AMBIENT_LIGHT_COLOR,
            (SportAmbientLogic.COLOR_MIN..SportAmbientLogic.COLOR_MAX).toSet(),
            listOf(sig("cs11_ambient_color", CS, CarSettingID.ID_CAR_AMBIENT_LIGHT_COLOR), sig("cs7_ambient_light", CS, CarSettingID.ID_CAR_AMBIENT_LIGHT)),
            verified = SportAmbient.AMBIENT_WRITE_VERIFIED),
        // Merkezi kilit: VehicleQuickControls.centralLockValue → 2 kilitle, 1 aç. Yalnızca park halinde ([LOCK_WRITE_VERIFIED]).
        Entry("Lock", CS, CarSettingID.ID_CAR_IVI_LOCK, setOf(1, 2),
            listOf(sig("cs169_ivi_lock", CS, CarSettingID.ID_CAR_IVI_LOCK)), verified = LOCK_WRITE_VERIFIED, parkOnly = true),
        // Sürüş modu: NewEnergy 4 YAZMA değerleri (FrostNav'ın araçta çalışan yazması, OEM seçicisi): Eko 0, Normal 1, Spor 2,
        // Kar 3, Çamur 4, Arazi 5, Kum 7 ([DriveModes]; okunan değerler aynı). Araçta doğrulandı; yalnızca park halinde.
        Entry("DriveMode", NE, NewEnergyID.ID_DRIVE_MODE, DriveModes.WRITE_VALUES,
            listOf(sig("ne4_drive_mode", NE, NewEnergyID.ID_DRIVE_MODE)), verified = DRIVE_MODE_WRITE_VERIFIED, parkOnly = true),
        // Direksiyon ısıtma (Tiggo 8): anahtar gibi, açarken de kapatırken de 160 ← 1 (OEM sesli komut); 0 yazılmaz. Doğrulanmadı.
        Entry("SteerHeat", CS, CarSettingID.ID_CAR_STEER_WHEEL_HEAT, setOf(ComfortPlan.STEER_WHEEL_TOGGLE),
            listOf(ComfortPlan.STEER_WHEEL_HEAT), verified = STEER_HEAT_WRITE_VERIFIED),
    )

    private fun seat(widget: String, id: Int) =
        Entry(widget, CS, id, SEAT_VALUES, listOf(sig("cs${id}_${widget.lowercase()}", CS, id)), verified = true)

    /** Girdi beyaz listede mi; değilse null. */
    fun find(module: Int, id: Int, value: Int): Entry? = ENTRIES.firstOrNull { it.matches(module, id, value) }

    /** Girdi beyaz listede ve doğrulanmış mı (değerden bağımsız; widget'ın etkin/devre dışı görünümü için). */
    fun isVerified(module: Int, id: Int): Boolean = ENTRIES.any { it.module == module && it.id == id && it.verified }

    sealed class Verdict {
        class Allowed(val entry: Entry) : Verdict()
        object NotWhitelisted : Verdict()
        class NotVerified(val entry: Entry) : Verdict()
        class ParkRefused(val entry: Entry, val reason: String) : Verdict()
    }

    /**
     * Karar sırası: beyaz liste → doğrulama ([gate]: doğrulanmamış girdi geçmez) → park kapısı ([Entry.parkOnly] ise).
     * [park] yalnızca park kapılı girdilerde çağrılır (hız/vites okuması); varsayılanı "okunamadı" sayar ve reddeder.
     */
    fun evaluate(
        module: Int, id: Int, value: Int,
        park: () -> ParkGate.Decision = { ParkGate.evaluate(null, null, null) },
    ): Verdict {
        val entry = find(module, id, value) ?: return Verdict.NotWhitelisted
        if (!gate(entry.verified, LauncherEdition.WRITES_ON)) return Verdict.NotVerified(entry)
        if (entry.parkOnly) {
            val d = park()
            if (!d.allowed) return Verdict.ParkRefused(entry, d.reason)
        }
        return Verdict.Allowed(entry)
    }

    /** Servis bağlantısı adımı: bağlıysa gönder; ilk denemede bağlı değilse bağlan ve bir kez yeniden dene; yine yoksa reddet. */
    enum class ConnectStep { SEND, CONNECT_AND_RETRY, REFUSE }

    /** [retried]: bağlanma denemesinden ([CONNECT_RETRY_MS] sonra) gelen ikinci bakış. */
    fun connectStep(connected: Boolean, retried: Boolean): ConnectStep = when {
        connected -> ConnectStep.SEND
        retried -> ConnectStep.REFUSE
        else -> ConnectStep.CONNECT_AND_RETRY
    }

    /** CarInfoProxy.init'ten sonra yeniden bakmadan önce bekleme (FrostNav sürüş modu yazmasıyla aynı: 500 ms). */
    const val CONNECT_RETRY_MS = 500L

    /** Özellik kapısı: yalnızca doğrulanmış VE yazmalar açıksa ([LauncherEdition.WRITES_ON]). Doğrulanmamış girdi asla geçmez. */
    fun gate(verified: Boolean, writesOn: Boolean): Boolean = verified && writesOn

    /** Günlük sonucu metni. */
    fun resultText(v: Verdict): String = when (v) {
        is Verdict.Allowed -> "sent"
        is Verdict.NotWhitelisted -> "refused-not-whitelisted"
        is Verdict.NotVerified -> "refused-not-verified"
        is Verdict.ParkRefused -> "refused-park (${v.reason})"
    }

    /** Tek satır: `widget=… module=… id=… value=… result=…`. Beyaz liste dışıysa widget adı "?" olur. */
    fun logLine(widget: String?, module: Int, id: Int, value: Int, result: String): String =
        "widget=${widget ?: "?"} module=$module id=$id value=$value result=$result"
}
