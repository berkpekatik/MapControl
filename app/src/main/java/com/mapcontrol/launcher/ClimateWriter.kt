package com.mapcontrol.launcher

import com.desaysv.ivi.extra.project.carinfo.HvacID
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import kotlin.math.roundToInt

/** Klima tarafı: sürücü ya da yolcu. */
internal enum class ClimateSide { DRIVER, PASSENGER }

/**
 * Klima sürüklemesinin saf hesapları (arayüzden bağımsız, birim testli): sürükleme mesafesi → adım,
 * sınırlama, ham kodlama.
 */
internal object ClimateMath {
    /**
     * Sıcaklık adımı. Geçerli aralık (OEM, T1E24: ham = (°C - 15,5) * 2): ham 0 = LO, 1..29 = 16,0..30,0 °C, ham 30 = HI.
     * 30 °C üstü (ham 31..33) OEM'de yoktur ve yazılmaz.
     */
    const val TEMP_STEP = 0.5

    /** LO ve HI uçları; sıcaklık hep bu ikisi arasındadır. LO = ham 0, HI = ham 30 (sıcaklık değeri değil, uç işaretidir). */
    const val LO_TEMP = 15.5
    const val HI_TEMP = 30.5
    const val RAW_LO = 0
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
    fun targetTemp(start: Double, steps: Int): Double = clampTemp(start + steps * TEMP_STEP)

    /**
     * Fan hedefi. 0 (kapalı) yalnızca dokunarak ya da sola kaydırmadan gelir: sürüklemede 1..10'a sıkışır,
     * kapalıyken sola sürükleme kapalı kalır.
     */
    fun targetFan(start: Int, steps: Int): Int = when {
        steps == 0 -> start
        start == 0 && steps < 0 -> 0
        else -> (start + steps).coerceIn(FAN_MIN, FAN_MAX)
    }

    /** Ham değer: °C = 15,5 + ham/2 ([OurBottomBar.decodeTemp]'in tersi); LO = 0, HI = 30. */
    fun encodeTemp(c: Double): Int = ((clampTemp(c) - LO_TEMP) * 2).roundToInt()

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
     * OEM de böyle (T1E24, 6 Kasım ölçümü). Otomatik panel çubuğu en az 1 yazar (eksi düğmesi 1'de durur); alt dock da
     * 1'in altına inmez; sesli komut "en düşük" kademede kalır. Kapatma yalnızca ayrı güç düğmesi: HVAC 18 ← 2
     * (sesli "klimayı kapat" da aynı).
     */
    fun encodeFan(level: Int): Int = level.coerceIn(1, FAN_MAX)

    /** A/C (HVAC 1) ve otomatik (HVAC 4): 1 aç / 2 kapat; 0 yazılmaz (aracın klima paneliyle aynı sıra, FrostNav ClimateAutomationWrites). */
    const val AC_ON = 1
    const val AC_OFF = 2

    /** Açmada güç yazmasından sonra A/C + otomatik için bekleme (OEM postDelayed 150L). */
    const val POWER_FOLLOW_UP_MS = 150L

    /**
     * Klimayı açma/kapatma yazma planı (HVAC kimliği → ham değer), FrostNav konfor otomasyonuyla aynı
     * (ClimateAutomationWrites CLIMATE; eşitlik JVM testiyle korunur):
     *  - Aç: önce [first] = 18 ← 1, [POWER_FOLLOW_UP_MS] sonra [followUp] = 1 ← 1, 4 ← 1.
     *  - Kapat: [first] = 1 ← 2, 18 ← 2, 4 ← 2; [followUp] boş.
     * Not: sıcaklık/fan yazmadan önceki otomatik açma ([powerOnBeforeWrite]) bilerek yalnız 18 ← 1 kalır (OEM sesli komutu
     * gibi); orada A/C ve otomatiği açmak kullanıcının o an yazdığı fanı/ayarı ezer.
     */
    class PowerPlan(val first: List<Pair<Int, Int>>, val followUp: List<Pair<Int, Int>>)

    fun powerPlan(on: Boolean): PowerPlan = if (on) {
        PowerPlan(listOf(HvacID.ID_AC_WORKING to POWER_ON),
            listOf(HvacID.ID_AC_STATE to AC_ON, HvacID.ID_AC_AUTO_STATE to AC_ON))
    } else {
        PowerPlan(listOf(HvacID.ID_AC_STATE to AC_OFF, HvacID.ID_AC_WORKING to POWER_OFF, HvacID.ID_AC_AUTO_STATE to AC_OFF),
            emptyList())
    }
}

/**
 * Klima yazma noktası. Yazmalar her zaman açık ([LauncherEdition.WRITES_ON]); araca ulaşan tek yol
 * `LauncherEdition.sendItemValue` → [WidgetWriter] (beyaz liste + `WidgetWrite` günlüğü).
 */
internal object ClimateWriter {

    /** Araç testinde doğrulanınca true yapılır (koltuk soğutmadaki SEAT_VENT_WRITE_VERIFIED gibi). */
    const val CLIMATE_WRITE_VERIFIED = false

    /** Saf kapı: yalnızca doğrulanmışsa ve salt-okur sürüm değilse yazılır. */
    fun canWrite(verified: Boolean, readOnly: Boolean): Boolean = verified && !readOnly

    /** Arayüzün "yazılacak mı / bilgi mesajı mı" kararı için. */
    val enabled: Boolean get() = canWrite(WidgetWritePolicy.gate(CLIMATE_WRITE_VERIFIED, LauncherEdition.WRITES_ON), LauncherEdition.READ_ONLY)

    /**
     * Sıcaklık kimliği. [quiet] (ayar "fabrika paneli: Gizle"): OEM alt dock'unun kimlikleri, sürücü 76 / yolcu 78, aynı ham
     * kodlama. Bizim araçta (T1E24) doğrulandı: dock sürücü → HVAC 76,
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
     * ve arkadaşın dock'unun kimliğidir. 12 aynı zamanda panelin kimliği; 12 yazmasının
     * ECU'da paneli (HVAC 9 bit 0) açıp açmadığı araçta ölçülmedi, 77'nin bizim ECU'da kademeyi değiştirdiği de günlükle
     * kanıtlanmadı. Araç testi bitene kadar 77 kalır; her dock fan yazmasında karar `WidgetWrite` günlüğüne düşer.
     */
    fun fanId(quiet: Boolean): Int = if (quiet) HvacID.ID_AC_SET_BLOW_LEVEL else HvacID.ID_AC_BLOW_SPEED_LEVEL

    /** OEM dock'unun bizim araçtaki fan kimliği (otomatik klima): bilgi ve test için; yazmada kullanılmıyor (bkz. [fanId]). */
    const val OEM_DOCK_FAN_ID_T1E24 = 12

    /** Sıcaklığı yazar. Kapalıyken false döner ve araca dokunmaz. */
    fun setTemperature(side: ClimateSide, celsius: Double, quiet: Boolean): Boolean {
        if (!enabled) return false
        powerOnIfOff(quiet)
        return send(tempId(side, quiet), ClimateMath.encodeTemp(celsius))
    }

    /** Fan kademesini (1..10) yazar. Kapalıyken ya da level < 1 iken (0 yazılmaz) false döner ve araca dokunmaz. */
    fun setFan(level: Int, quiet: Boolean): Boolean {
        if (!enabled) return false
        if (level < 1) return false
        powerOnIfOff(quiet)
        if (quiet) {
            android.util.Log.i(WidgetWritePolicy.LOG_TAG, "widget=Climate fan dock-id=${fanId(true)} " +
                "(OEM T1E24 auto dock uses $OEM_DOCK_FAN_ID_T1E24; kept pending car test)")
        }
        return send(fanId(quiet), ClimateMath.encodeFan(level))
    }

    /**
     * Klimayı açar/kapatır, FrostNav ile aynı sıra ([ClimateMath.powerPlan]). Arka planda çağrılır (açmada 150 ms bekler).
     * Kapalıyken (yazma kapısı) ya da servis yokken false döner; ilk yazma reddedilirse devam yazmaları gönderilmez.
     */
    fun setPower(on: Boolean): Boolean {
        if (!enabled) return false
        val plan = ClimateMath.powerPlan(on)
        for ((id, raw) in plan.first) if (!send(id, raw)) return false
        if (plan.followUp.isEmpty()) return true
        android.os.SystemClock.sleep(ClimateMath.POWER_FOLLOW_UP_MS)
        var ok = true
        for ((id, raw) in plan.followUp) ok = send(id, raw) && ok
        return ok
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
        LauncherEdition.sendItemValue(proxy, VDEventCarInfo.MODULE_HVAC, id, raw)
        return true
    }
}
