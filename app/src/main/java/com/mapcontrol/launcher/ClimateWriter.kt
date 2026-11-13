package com.mapcontrol.launcher

import com.desaysv.ivi.extra.project.carinfo.HvacID
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import kotlin.math.roundToInt

/** Klima tarafı: sürücü ya da yolcu. */
internal enum class ClimateSide { DRIVER, PASSENGER }

/**
 * Klima ham değerinin ölçeği; hangisi olduğu araca göre değişir (OEM klima uygulaması `SVHvac` da ikisini ayırır).
 * [HALF]: ham 0 = LO, 1..29 = 16,0..30,0 °C (0,5 adım), 30 = HI. [WHOLE]: ham 0 = LO, 1..15 = 16..30 °C (1 adım), 16 = HI.
 */
internal enum class ClimateTempScale(val rawHi: Int) { HALF(30), WHOLE(16) }

/**
 * Klima sürüklemesinin saf hesapları (arayüzden bağımsız, birim testli): sürükleme mesafesi → adım,
 * sınırlama, ham kodlama.
 */
internal object ClimateMath {
    /** Etkin ölçek; "Ana ekran" sekmesindeki seçimden gelir ([HomeScreenPrefs.applyClimateScale]). */
    @Volatile
    var scale: ClimateTempScale = ClimateTempScale.HALF

    /** [ClimateTempScale.HALF] sıcaklık adımı. 30 °C üstü (ham 31..33) OEM'de yoktur ve yazılmaz. */
    const val TEMP_STEP = 0.5

    /** LO ve HI uçları; sıcaklık hep bu ikisi arasındadır. Ölçekten bağımsız, sıcaklık değeri değil uç işaretidir. */
    const val LO_TEMP = 15.5
    const val HI_TEMP = 30.5
    const val RAW_LO = 0
    /** Ölçeklerin en yükseği (yazma izin listesi); etkin üst değer `scale.rawHi`. */
    const val RAW_HI = 30

    const val FAN_MIN = 1
    // Araçta ölçüldü: A/C açıkken 4, maks. ön buğu çözmede 10, buğu kapanınca 1 okundu; en az 10'a kadar çıkıyor.
    // Gösterim için 10; yazma kapılı (ölçek doğrulanana kadar araca gitmez).
    const val FAN_MAX = 10

    /** Sıcaklık: dikey her 40dp yol = 1 adım (0,5 °C). */
    const val TEMP_DRAG_DP_PER_STEP = 40f

    /** Fan: yatay her 12dp yol = 1 kademe (hücre 120dp; tüm 1..10 aralığı tek harekette). */
    const val FAN_DRAG_DP_PER_STEP = 12f

    /**
     * Sürükleme mesafesinden (px) tam adım sayısı; sıfıra doğru yuvarlanır, yani yarım adım sayılmaz.
     * [delta] işaretlidir: sıcaklıkta yukarı = pozitif (çağıran `downY - y` verir), fanda sağ = pozitif.
     */
    fun stepsForDrag(delta: Float, density: Float, dpPerStep: Float): Int =
        (delta / (dpPerStep * density)).toInt()

    /** 0,5'e yuvarlar ve [LO_TEMP]..[HI_TEMP] aralığına sıkıştırır (uçlar LO / HI). */
    fun clampTemp(c: Double): Double =
        ((c / TEMP_STEP).roundToInt() * TEMP_STEP).coerceIn(LO_TEMP, HI_TEMP)

    fun isLo(c: Double): Boolean = c <= LO_TEMP
    fun isHi(c: Double): Boolean = c >= HI_TEMP

    /** [start] sıcaklığından [steps] adım sonra hedef (sınırlı). */
    fun targetTemp(start: Double, steps: Int): Double =
        decodeTemp((encodeTemp(start) + steps).coerceIn(RAW_LO, scale.rawHi)) ?: start

    /**
     * Fan hedefi. 0 (kapalı) yalnızca dokunarak ya da sola kaydırmadan gelir: sürüklemede 1..10'a sıkışır,
     * kapalıyken sola sürükleme kapalı kalır.
     */
    fun targetFan(start: Int, steps: Int): Int = when {
        steps == 0 -> start
        start == 0 && steps < 0 -> 0
        else -> (start + steps).coerceIn(FAN_MIN, FAN_MAX)
    }

    /** Ham değerden sıcaklık (uçlar [LO_TEMP] / [HI_TEMP]); etkin ölçeğin dışındaysa null. */
    fun decodeTemp(raw: Int): Double? {
        val s = scale
        return when {
            raw !in RAW_LO..s.rawHi -> null
            raw == RAW_LO -> LO_TEMP
            raw == s.rawHi -> HI_TEMP
            s == ClimateTempScale.WHOLE -> raw + 15.0
            else -> LO_TEMP + raw / 2.0
        }
    }

    /** [decodeTemp]'in tersi. */
    fun encodeTemp(c: Double): Int {
        val s = scale
        return when {
            s == ClimateTempScale.HALF -> ((clampTemp(c) - LO_TEMP) * 2).roundToInt()
            isLo(c) -> RAW_LO
            isHi(c) -> s.rawHi
            else -> (c - 15.0).roundToInt().coerceIn(1, s.rawHi - 1)
        }
    }

    /** Klima gücü (HVAC 18): okuma 1 = açık; yazma 1 aç / 2 kapat (araçta ölçüldü). */
    const val POWER_ON = 1
    const val POWER_OFF = 2

    /**
     * OEM sesli komutu klima kapalıyken sıcaklık/fan yazmadan önce 18 ← 1 gönderir. Güç okunabiliyor
     * ve açık değilse önce açılır; okunamıyorsa (sinyal yok) hiçbir şey eklenmez.
     */
    fun needsPowerOn(powerRaw: Int?): Boolean = powerRaw != null && powerRaw != POWER_ON

    /**
     * Sıcaklık/fan yazmadan önce güç (18 ← 1) gönderilsin mi. [quiet] (OEM dock kimlikleri 76/78/77): HAYIR. OEM alt dock'u
     * klima kapalıyken de yalnızca dock kimliğini yazar, güç göndermez (tek kapı kontak:
     * `car_power_on_tips`). 18 ← 1 panel yoludur (panelin fan +/− kapalıyken yalnızca güç gönderir)
     * ve 6 Kasım araçta klima kapalıyken OEM panelini açtı. Panel kimlikleri (10/13/12) ile eski yol (sesli komut gibi) korunur.
     * Bizim araçta da doğrulandı: OEM SystemUI'de HVAC 18 hiç yazılmıyor, yalnız okunuyor;
     * dock'un tek HVAC 9 yazması yukarı kaydırmadır.
     */
    fun powerOnBeforeWrite(quiet: Boolean, powerRaw: Int?): Boolean = !quiet && needsPowerOn(powerRaw)

    /**
     * Fan ham değeri doğrudan kademedir (okuma da öyle); yazılan 1..10. Fanı 1'in altına indirmek klimayı KAPATMAZ ve 0 yazılmaz:
     * OEM de böyle (araçta ölçüldü). Otomatik panel çubuğu en az 1 yazar (eksi düğmesi 1'de durur); alt dock da
     * 1'in altına inmez; sesli komut "en düşük" kademede kalır. Kapatma yalnızca ayrı güç düğmesi: HVAC 18 ← 2
     * (sesli "klimayı kapat" da aynı).
     */
    fun encodeFan(level: Int): Int = level.coerceIn(1, FAN_MAX)

    /** A/C (HVAC 1) ve otomatik (HVAC 4): 1 aç / 2 kapat; 0 yazılmaz (aracın klima paneliyle aynı sıra, MapControl ClimateAutomationWrites). */
    const val AC_ON = 1
    const val AC_OFF = 2

    /**
     * Klima gücü ham değeri (HVAC 18): 1 aç / 2 kapat. OEM SystemUI'deki klima güç düğmesiyle aynı tek yazma
     * (`HvacModel.onPowerClick`: `sendItemValue(HVAC, 18, açık ? 2 : 1)`); A/C (1) ve otomatik (4) yazılmaz, klima son
     * ayarlarıyla açılır. 0 yazılmaz.
     */
    fun powerValue(on: Boolean): Int = if (on) POWER_ON else POWER_OFF

    /** HVAC 18 okuması: 1 açık, başka bilinen değer kapalı (OEM `getPowerState`: `== 1`); okunamadıysa null. */
    fun powerOn(raw: Int?): Boolean? = raw?.let { it == POWER_ON }

    /**
     * Hava yönü (HVAC 6) OKUMASI → yüz / ayak / cam. Tiggo 7 (`SVHvac` `HvacPageView.setBlowMode`) ve Tiggo 8 (SystemUI
     * `MainViewModel.onWindModePicStatus`) aynı tabloyu kullanır: 0 yüz, 1 yüz+ayak, 2 ayak, 3 ayak+cam, 4 cam,
     * 5 hiçbiri (OEM üçünü de söndürür), 6 yüz+cam, 7 üçü. Bilinmeyen ya da okunamayan değer null.
     */
    fun airFlowFromRead(raw: Int?): AirFlow? = when (raw) {
        0 -> AirFlow(face = true, foot = false, window = false)
        1 -> AirFlow(face = true, foot = true, window = false)
        2 -> AirFlow(face = false, foot = true, window = false)
        3 -> AirFlow(face = false, foot = true, window = true)
        4 -> AirFlow(face = false, foot = false, window = true)
        5 -> AirFlow(face = false, foot = false, window = false)
        6 -> AirFlow(face = true, foot = false, window = true)
        7 -> AirFlow(face = true, foot = true, window = true)
        else -> null
    }

    /**
     * Hava yönü YAZMA değeri (HVAC 6). Okumadan farklı: yüz+cam 5 (okunan 6), üçü 6 (okunan 7). OEM'in iki aracı da böyle
     * yazar (Tiggo 8 `HvacModel.onWindMode*Click`, Tiggo 7 `HvacModel.E0/C0/H/Q0/p/G/C`). Hiçbiri seçili değilse null:
     * OEM o dokunuşu yok sayar (en az bir yön açık kalır).
     */
    fun airFlowWriteValue(f: AirFlow): Int? = when (Triple(f.face, f.foot, f.window)) {
        Triple(true, false, false) -> 0
        Triple(true, true, false) -> 1
        Triple(false, true, false) -> 2
        Triple(false, true, true) -> 3
        Triple(false, false, true) -> 4
        Triple(true, false, true) -> 5
        Triple(true, true, true) -> 6
        else -> null
    }

    /** Devridaim (HVAC 8) yazması: 1 iç hava (devridaim açık), 2 dış hava. 0 yazılmaz (OEM `onCirculationClick`). */
    const val RECIRC_ON = 1
    const val RECIRC_OFF = 2

    fun recircValue(on: Boolean): Int = if (on) RECIRC_ON else RECIRC_OFF

    /** HVAC 8 okuması: 0 devridaim açık, başka değer dış hava (OEM `getCirculationState`: `== 0`); okunamadıysa null. */
    fun recircOn(raw: Int?): Boolean? = raw?.let { it == 0 }
}

/** Hava yönü: yüz, ayak ve ön cam menfezleri (birlikte seçilebilir; bkz. [ClimateMath.airFlowFromRead]). */
internal data class AirFlow(val face: Boolean, val foot: Boolean, val window: Boolean) {
    enum class Vent { FACE, FOOT, WINDOW }

    fun with(vent: Vent, on: Boolean): AirFlow = when (vent) {
        Vent.FACE -> copy(face = on)
        Vent.FOOT -> copy(foot = on)
        Vent.WINDOW -> copy(window = on)
    }

    fun isOn(vent: Vent): Boolean = when (vent) {
        Vent.FACE -> face
        Vent.FOOT -> foot
        Vent.WINDOW -> window
    }
}

/**
 * Klima yazma noktası. Yalnızca araçta doğrulanmış yol açıktır: dock kimlikleri (76/78/77, ayar "fabrika paneli: Gizle").
 * Panel kimlikleri (10/13/12) ve klima gücü (18/1/4) doğrulanana dek kapalı. Araca ulaşan tek yol
 * `LauncherEdition.sendItemValue` → [WidgetWriter] (beyaz liste + doğrulama + `WidgetWrite` günlüğü).
 */
internal object ClimateWriter {

    /** Panel kimlikleri (10/13/12) araçta DOĞRULANMADI; doğrulanınca true yapılır. */
    const val CLIMATE_WRITE_VERIFIED = false

    /**
     * Klima gücü (HVAC 18 ← 1/2, alt çubuk fan dokunuşu). OEM SystemUI'nin güç düğmesiyle birebir aynı yazma
     * (decompile, `HvacModel.onPowerClick`). ARAÇ TESTİ BEKLİYOR (11 Kasım 2026): testte 18 geri okuması değişmezse ya da
     * klima davranışı farklıysa false yapılır; o zaman dokunuş araca dokunmaz, bilgi mesajı gösterir.
     */
    const val CLIMATE_POWER_WRITE_VERIFIED = true

    /**
     * Dock kimlikleri araçta doğrulandı: sürücü 76 / yolcu 78 sıcaklığı ve fan 77 yazınca geri okumada panel değerleri
     * (10/13/12) yeni değere geçti.
     */
    const val CLIMATE_DOCK_WRITE_VERIFIED = true

    /**
     * Hava yönü (HVAC 6) ve devridaim (HVAC 8): Tiggo 8 alt çubuğundaki dört düğme. Yazma değerleri iki aracın OEM koduyla
     * aynı ([ClimateMath.airFlowWriteValue], [ClimateMath.recircValue]). Araç testi yapılmadan sahibin kararıyla AÇILDI
     * (13 Kasım 2026; araç uzakta, değerler OEM decompile'ıyla birebir). Her yazma `WidgetWrite` günlüğüne 3 sn geri okumayla
     * düşer (hvac6_air_flow / hvac8_recirc); araç değeri yok sayıyorsa ya da beklenmeyen değer okunuyorsa false yapılır.
     */
    const val AIR_WRITE_VERIFIED = true

    /** Hava yönü / devridaim yazılabilir mi ([AIR_WRITE_VERIFIED], yazmalar açık, salt-okur değil). */
    fun airEnabled(): Boolean = canWrite(
        WidgetWritePolicy.gate(AIR_WRITE_VERIFIED, LauncherEdition.WRITES_ON), LauncherEdition.READ_ONLY)

    /** Klima gücü yazılabilir mi ([CLIMATE_POWER_WRITE_VERIFIED], yazmalar açık, salt-okur değil). */
    fun powerEnabled(): Boolean = canWrite(
        WidgetWritePolicy.gate(CLIMATE_POWER_WRITE_VERIFIED, LauncherEdition.WRITES_ON), LauncherEdition.READ_ONLY)

    /** Saf kapı: yalnızca doğrulanmışsa ve salt-okur sürüm değilse yazılır. */
    fun canWrite(verified: Boolean, readOnly: Boolean): Boolean = verified && !readOnly

    /** Arayüzün "yazılacak mı / bilgi mesajı mı" kararı için: [quiet] dock yolu, değilse panel yolu. */
    fun enabled(quiet: Boolean): Boolean = canWrite(
        WidgetWritePolicy.gate(if (quiet) CLIMATE_DOCK_WRITE_VERIFIED else CLIMATE_WRITE_VERIFIED, LauncherEdition.WRITES_ON),
        LauncherEdition.READ_ONLY,
    )

    /**
     * Sıcaklık kimliği. [quiet] (ayar "fabrika paneli: Gizle"): OEM alt dock'unun kimlikleri, sürücü 76 / yolcu 78, aynı ham
     * kodlama. Araçta doğrulandı: dock sürücü → HVAC 76,
     * yolcu → 78, klima tipinden bağımsız.
     * Aksi halde OEM panelinin 10 / 13.
     */
    fun tempId(side: ClimateSide, quiet: Boolean): Int = when {
        quiet && side == ClimateSide.DRIVER -> HvacID.ID_AC_DRIVER_SET_TEMPERATURE
        quiet -> HvacID.ID_AC_PASSENGER_SET_TEMPERATURE
        side == ClimateSide.DRIVER -> HvacID.ID_AC_TEMPERATURE_DRIVER
        else -> HvacID.ID_AC_TEMPERATURE_COPILOT
    }

    /**
     * Fan kimliği: [quiet] → 77, aksi halde panelin 12. Ham değer ikisinde de kademe.
     *
     * KARAR (belirsiz kanıt, mevcut davranış korunuyor): bizim aracın OEM dock'u otomatik klimada (config 4 == 1, günlükte
     * `HvacConfig =0`) fanı 77'ye DEĞİL 12'ye yazar
     * (HVAC 12). 77 bizim SystemUI'de yalnız elektrikli/manuel klima düzeninin fanıdır
     * ve bazı yazılım sürümlerinin dock'unun kimliğidir. 12 aynı zamanda panelin kimliği; 12 yazmasının
     * ECU'da paneli (HVAC 9 bit 0) açıp açmadığı araçta ölçülmedi, 77'nin bizim ECU'da kademeyi değiştirdiği de günlükle
     * kanıtlanmadı. Araç testi bitene kadar 77 kalır; her dock fan yazmasında karar `WidgetWrite` günlüğüne düşer.
     */
    fun fanId(quiet: Boolean): Int = if (quiet) HvacID.ID_AC_SET_BLOW_LEVEL else HvacID.ID_AC_BLOW_SPEED_LEVEL

    /** OEM dock'unun bizim araçtaki fan kimliği (otomatik klima): bilgi ve test için; yazmada kullanılmıyor (bkz. [fanId]). */
    const val OEM_DOCK_FAN_ID_AUTO = 12

    /** Sıcaklığı yazar. Kapalıyken false döner ve araca dokunmaz. */
    fun setTemperature(side: ClimateSide, celsius: Double, quiet: Boolean): Boolean {
        if (!enabled(quiet)) return false
        powerOnIfOff(quiet)
        return send(tempId(side, quiet), ClimateMath.encodeTemp(celsius))
    }

    /** Fan kademesini (1..10) yazar. Kapalıyken ya da level < 1 iken (0 yazılmaz) false döner ve araca dokunmaz. */
    fun setFan(level: Int, quiet: Boolean): Boolean {
        if (!enabled(quiet)) return false
        if (level < 1) return false
        powerOnIfOff(quiet)
        if (quiet) {
            android.util.Log.i(WidgetWritePolicy.LOG_TAG, "widget=Climate fan dock-id=${fanId(true)} " +
                "(OEM auto dock uses $OEM_DOCK_FAN_ID_AUTO; kept pending car test)")
        }
        return send(fanId(quiet), ClimateMath.encodeFan(level))
    }

    /**
     * Klimayı açar ([on]) ya da kapatır: HVAC 18 ← 1/2, OEM güç düğmesi gibi tek yazma ([ClimateMath.powerValue]).
     * Kapalıyken ([powerEnabled] false) ya da servis yokken false döner ve araca dokunmaz. Arka planda çağrılır.
     */
    fun setPower(on: Boolean): Boolean {
        if (!powerEnabled()) return false
        return send(HvacID.ID_AC_WORKING, ClimateMath.powerValue(on))
    }

    /** Hava yönünü yazar (HVAC 6, tek değer). Kapalıyken ya da hiçbir yön seçili değilken false döner ve araca dokunmaz. */
    fun setAirFlow(flow: AirFlow): Boolean {
        if (!airEnabled()) return false
        val raw = ClimateMath.airFlowWriteValue(flow) ?: return false
        return send(HvacID.ID_AC_MODE_ADJUST, raw)
    }

    /** Devridaimi açar ([on]: iç hava) ya da kapatır (dış hava): HVAC 8 ← 1/2. Kapalıyken false döner ve araca dokunmaz. */
    fun setRecirc(on: Boolean): Boolean {
        if (!airEnabled()) return false
        return send(HvacID.ID_AC_CIRCULATION_MODE, ClimateMath.recircValue(on))
    }

    /**
     * Panel yolunda klima kapalıysa önce aç (HVAC 18 ← 1, sesli komut gibi). Dock yolunda ([quiet]) güç gönderilmez, OEM dock'u
     * gibi yalnızca 76/78/77 gider ([ClimateMath.powerOnBeforeWrite]); karar `WidgetWrite` günlüğüne yazılır. Arka planda çağrılır.
     */
    private fun powerOnIfOff(quiet: Boolean) {
        val power = CarSignals.readInt(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_WORKING)
        if (ClimateMath.powerOnBeforeWrite(quiet, power)) {
            send(HvacID.ID_AC_WORKING, ClimateMath.POWER_ON)
        } else if (quiet && ClimateMath.needsPowerOn(power)) {
            android.util.Log.i(WidgetWritePolicy.LOG_TAG, "widget=Climate power=$power dock-path: no power write (OEM dock)")
        }
    }

    private fun send(id: Int, raw: Int): Boolean {
        val proxy = CarInfoProxy.getInstance()
        if (!proxy.isServiceConnnected) {
            android.util.Log.w("WidgetWrite", "widget=Climate id=$id value=$raw result=refused-no-service")
            return false
        }
        return LauncherEdition.sendItemValue(proxy, VDEventCarInfo.MODULE_HVAC, id, raw)
    }
}
