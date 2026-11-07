package com.mapcontrol.launcher

import com.desaysv.ivi.extra.project.carinfo.CarSettingID
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo

/** Araca gidecek tek bir yazma: modül, sinyal, değer. */
internal data class ComfortCmd(val module: Int, val id: Int, val value: Int)

/**
 * Bagaj kapağı durumu. Okuma (araçta ölçüldü): ReadOnly PLG_POSITION (98) 0 kapalı .. ~81 tam açık, TRUNK_STATE (19) 0 kapalı / 1 açık,
 * CarSetting REAR_DOOR_CONTROL (92) 2 durdu / 3 açılıyor / 4 kapanıyor.
 */
internal enum class TrunkState {
    CLOSED, OPENING, CLOSING, PARTIAL, OPEN, UNKNOWN;

    val moving: Boolean get() = this == OPENING || this == CLOSING
}

/** Bagaj ham okuması; okunamayan alan null. */
internal data class TrunkReading(val position: Int? = null, val latchOpen: Int? = null, val motion: Int? = null)

/** Bagaj dokunuşunun sonucu: ne yapılacağı (saf karar, [TrunkMath.tapAction]). */
internal enum class TrunkAction { OPEN, CLOSE_CONFIRM, STOP, STOP_UNKNOWN, NONE }

/**
 * Kapılı bagaj yazmasının sonucu. [STALE]: araç durumu dokunuşun varsaydığından farklı (OEM kuralı ters yönü seçti), hiçbir şey
 * gönderilmedi. [TOO_SOON]: son komuttan beri OEM'in 1 sn tıklama kilidi dolmadı, sessizce yok sayıldı. Sürüş kilidi yoktur:
 * araç hareket halindeyken komutu kendisi reddederse geri okuma bunu gösterir.
 */
internal enum class TrunkWriteResult { SENT, DISABLED, STALE, TOO_SOON }

/** Bagaj kapağının saf hesapları (JVM'de sınanır): ham okuma → durum ve yüzde, dokunuş kararı, yazma komutu. */
internal object TrunkMath {

    /** PLG_POSITION tam açıkken ölçülen değer (~81); yüzde buna göre. */
    const val FULL_OPEN = 81

    /** Bu değerden itibaren (tam açık - 2) "tam açık" sayılır; ölçüm kıpırdaması payı. */
    const val OPEN_THRESHOLD = FULL_OPEN - 2

    const val MOTION_STOPPED = 2
    const val MOTION_OPENING = 3
    const val MOTION_CLOSING = 4

    /** Açıklık yüzdesi 0..100; konum okunamıyorsa null. */
    fun percent(position: Int?): Int? =
        position?.let { Math.round(it.coerceIn(0, FULL_OPEN) * 100f / FULL_OPEN) }

    /**
     * Durum: önce hareket (CS 92: 3 açılıyor, 4 kapanıyor). Sonra konum: 0 → kapalı (mandal açık diyorsa açık), [OPEN_THRESHOLD] ve üstü → açık,
     * arası → yarı açık. Konum okunamazsa mandal (TRUNK_STATE 19). Hiçbiri yoksa bilinmiyor.
     */
    fun decode(r: TrunkReading?): TrunkState {
        if (r == null) return TrunkState.UNKNOWN
        when (r.motion) {
            MOTION_OPENING -> return TrunkState.OPENING
            MOTION_CLOSING -> return TrunkState.CLOSING
        }
        val pos = r.position
        if (pos != null) return when {
            pos <= 0 -> if (r.latchOpen == 1) TrunkState.OPEN else TrunkState.CLOSED
            pos >= OPEN_THRESHOLD -> TrunkState.OPEN
            else -> TrunkState.PARTIAL
        }
        return when (r.latchOpen) {
            0 -> TrunkState.CLOSED
            1 -> TrunkState.OPEN
            else -> TrunkState.UNKNOWN
        }
    }

    /** Çizim için açıklık 0..1; konum yoksa durumdan (kapalı 0, açık 1), bilinmiyorsa null. */
    fun openFraction(r: TrunkReading?): Float? {
        val pos = r?.position
        if (pos != null) return pos.coerceIn(0, FULL_OPEN) / FULL_OPEN.toFloat()
        return when (decode(r)) {
            TrunkState.CLOSED -> 0f
            TrunkState.OPEN -> 1f
            else -> null
        }
    }

    /**
     * Dokunuş: kapalı → aç; açık/yarı açık → onaylı kapat; hareket halinde → durdur (yalnızca durdurma değeri doğrulandıysa
     * [stopKnown], değilse ipucu); bilinmiyorsa hiçbir şey.
     */
    fun tapAction(state: TrunkState, stopKnown: Boolean): TrunkAction = when (state) {
        TrunkState.CLOSED -> TrunkAction.OPEN
        TrunkState.OPEN, TrunkState.PARTIAL -> TrunkAction.CLOSE_CONFIRM
        TrunkState.OPENING, TrunkState.CLOSING -> if (stopKnown) TrunkAction.STOP else TrunkAction.STOP_UNKNOWN
        TrunkState.UNKNOWN -> TrunkAction.NONE
    }

    /** OEM bagaj tıklama kilidi: iki komut arası en az bu kadar (OEM ayarlarında da 1 sn). */
    const val DEBOUNCE_MS = 1_000L

    /** CarSetting 92 yazma değerleri (OEM): 1 = aç, 2 = kapat. */
    const val CMD_OPEN = 1
    const val CMD_CLOSE = 2

    /**
     * OEM ayarlar / hızlı ayarlarla aynı değeri seçer:
     * `(CS92 == 2 || PLG_POSITION == 0) ? 1 (aç) : 2 (kapat)`, yani kapalı ya da duruyorsa aç, açık ya da hareketteyse kapat.
     * İkisi de okunamazsa null (OEM kuralı uygulanamaz).
     */
    fun oemValue(cs92: Int?, plgPosition: Int?): Int? {
        if (cs92 == null && plgPosition == null) return null
        return if (cs92 == 2 || plgPosition == 0) CMD_OPEN else CMD_CLOSE
    }

    /** Yazma komutu: CarSetting 92 ← [CMD_OPEN] / [CMD_CLOSE]. Araçta DOĞRULANMADI. */
    fun command(open: Boolean): ComfortCmd =
        ComfortCmd(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_REAR_DOOR_CONTROL, if (open) CMD_OPEN else CMD_CLOSE)

    /** Son komuttan [DEBOUNCE_MS] dolmadan yeni komut yok sayılır. [lastMs] null: hiç komut yok. */
    fun debounced(nowMs: Long, lastMs: Long?): Boolean = lastMs != null && nowMs - lastMs < DEBOUNCE_MS
}

/**
 * Konfor widget'larının (bagaj, arka hoparlör) yazma noktası. [COMFORT_WRITE_VERIFIED] ve ilgili
 * özellik bayrağı ya da [LauncherEdition.WRITES_ON] (hep true) kapıyı açar. Araca ulaşan tek yol `LauncherEdition.sendItemValue` → [WidgetWriter].
 * Kapı [ClimateWriter.canWrite] ile aynıdır.
 */
internal object ComfortWriter {

    /** Ana anahtar: araç testinden sonra true yapılır. Özellik bayrakları da true olmalı. */
    const val COMFORT_WRITE_VERIFIED = true

    /** Bagaj kapağı yazması araçta DOĞRULANMADI: false iken kutucuk salt-okur, dokunuş "henüz doğrulanmadı" ipucu gösterir. */
    const val TRUNK_WRITE_VERIFIED = false

    /** Hareketteki bagajı durduran CarSetting 92 değeri; araçta bulunamadı (2 denenecek). Null iken dokunuş yalnızca ipucu gösterir. */
    val TRUNK_STOP_VALUE: Int? = null

    /** Durdurma değeri doğrulandı mı. */
    val trunkStopKnown: Boolean get() = TRUNK_STOP_VALUE != null

    /**
     * OEM komutlarından türetilen yeni kutucukların yazması (arka hoparlör) araçta DOĞRULANMADI:
     * yazmalar her zaman açık olduğundan beyaz listeden geçerek ([WidgetWritePolicy]) gider.
     */
    const val REAR_SPEAKER_WRITE_VERIFIED = false

    /** Saf kapı: ana anahtar ve özellik bayrağı true ve salt-okur sürüm değilse yazılır. */
    fun canWrite(master: Boolean, feature: Boolean, readOnly: Boolean): Boolean =
        ClimateWriter.canWrite(master && feature, readOnly)

    val trunkEnabled: Boolean get() = canWrite(COMFORT_WRITE_VERIFIED, WidgetWritePolicy.gate(TRUNK_WRITE_VERIFIED, LauncherEdition.WRITES_ON), LauncherEdition.READ_ONLY)
    val rearSpeakerEnabled: Boolean get() = canWrite(COMFORT_WRITE_VERIFIED, WidgetWritePolicy.gate(REAR_SPEAKER_WRITE_VERIFIED, LauncherEdition.WRITES_ON), LauncherEdition.READ_ONLY)

    /**
     * Kapılı yazma (birim testli): [allowed] false ise [send] hiç çağrılmaz ve false döner.
     */
    fun writeAll(allowed: Boolean, commands: List<ComfortCmd>, send: (ComfortCmd) -> Unit): Boolean {
        if (!allowed) return false
        commands.forEach(send)
        return true
    }

    /** Bagaj yazması (saf, birim testli): [enabled] false ise DISABLED ve [send] çağrılmaz. Hız sorulmaz (sürüş kilidi yok). */
    fun writeTrunk(enabled: Boolean, commands: List<ComfortCmd>, send: (ComfortCmd) -> Unit): TrunkWriteResult {
        if (!enabled) return TrunkWriteResult.DISABLED
        commands.forEach(send)
        return TrunkWriteResult.SENT
    }

    private var lastTrunkMs: Long? = null

    /**
     * Bagaj aç/kapat (CarSetting 92 ← 1 / 2). Doğrulanmadıysa araca dokunmaz. Değer OEM gibi
     * seçilir ([TrunkMath.oemValue]); [open] ile çelişirse (araç dokunuştan sonra durum değiştirdi) hiçbir şey gönderilmez
     * ([TrunkWriteResult.STALE]). 1 sn içinde ikinci komut yok sayılır. Arka planda çağrılır.
     */
    fun setTrunk(open: Boolean): TrunkWriteResult {
        if (!trunkEnabled) return TrunkWriteResult.DISABLED
        val now = android.os.SystemClock.elapsedRealtime()
        if (TrunkMath.debounced(now, lastTrunkMs)) return TrunkWriteResult.TOO_SOON
        val cmd = trunkCommandNow(open) ?: return TrunkWriteResult.STALE
        return writeTrunk(trunkEnabled, listOf(cmd), ::sendToCar).also {
            if (it == TrunkWriteResult.SENT) lastTrunkMs = now
        }
    }

    /** OEM kuralına göre komut; kural niyetle çelişirse null (bkz. [setTrunk]). Okunamıyorsa niyet kullanılır. */
    private fun trunkCommandNow(open: Boolean): ComfortCmd? {
        val oem = TrunkMath.oemValue(
            CarSignals.readInt(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_REAR_DOOR_CONTROL),
            CarSignals.readInt(VDEventCarInfo.MODULE_READONLY_INFO, ReadOnlyID.ID_PLG_POSITION),
        )
        val want = if (open) TrunkMath.CMD_OPEN else TrunkMath.CMD_CLOSE
        return if (oem == null || oem == want) TrunkMath.command(open) else null
    }

    /** Hareketteki bagajı durdur (CarSetting 92 ← [TRUNK_STOP_VALUE]); değer bilinmiyorsa DISABLED. Arka planda çağrılır. */
    fun stopTrunk(): TrunkWriteResult {
        val v = TRUNK_STOP_VALUE ?: return TrunkWriteResult.DISABLED
        val cmd = ComfortCmd(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_REAR_DOOR_CONTROL, v)
        return writeTrunk(trunkEnabled, listOf(cmd), ::sendToCar)
    }

    /**
     * Arka hoparlör: ses alanını yazar; [balance] korunur, [fade] ön-arka dengesidir (0..[SoundFieldMath.MAX]). Kapalıyken false döner ve araca dokunmaz.
     * Gönderim `LauncherEdition.sendSoundField` üzerindendir (test sürümünde beyaz liste kapısından geçer).
     */
    fun setFader(balance: Int, fade: Int): Boolean {
        if (!rearSpeakerEnabled) return false
        LauncherEdition.sendSoundField(balance.coerceIn(0, SoundFieldMath.MAX), fade.coerceIn(0, SoundFieldMath.MAX))
        return true
    }

    private fun sendToCar(cmd: ComfortCmd) {
        val proxy = CarInfoProxy.getInstance()
        if (!proxy.isServiceConnnected) return
        LauncherEdition.sendItemValue(proxy, cmd.module, cmd.id, cmd.value)
    }
}

/** Konfor widget'larının okunan anlık durumu; okunamayan alan null. */
internal data class ComfortReading(
    val connected: Boolean = false,
    val trunk: TrunkReading? = null,
    /** Arka hoparlör: ön-arka dengesi (fader) ve sol-sağ dengesi; okunamayan null. */
    val sound: SoundFieldReading? = null,
)

/** Konfor sinyallerini okur (yalnızca okuma; arka plan thread'inde çağrılır). */
internal object ComfortReader {
    /** [withTrunk] yalnızca bagaj kutucuğu için (üç ek okuma); diğer konfor kutucukları okumaz. */
    fun read(withTrunk: Boolean = false): ComfortReading {
        if (LauncherDemo.enabled) return LauncherDemo.comfort()
        val proxy = CarInfoProxy.getInstance()
        if (!proxy.isServiceConnnected) return ComfortReading()
        return try {
            fun cs(id: Int) = proxy.getItemValues(VDEventCarInfo.MODULE_CAR_SETTING, id)?.firstOrNull()
            fun ro(id: Int) = proxy.getItemValues(VDEventCarInfo.MODULE_READONLY_INFO, id)?.firstOrNull()
            ComfortReading(
                connected = true,
                trunk = if (withTrunk) {
                    TrunkReading(ro(ReadOnlyID.ID_PLG_POSITION), ro(ReadOnlyID.ID_TRUNK_STATE), cs(CarSettingID.ID_CAR_REAR_DOOR_CONTROL))
                } else null,
            )
        } catch (e: RuntimeException) {
            ComfortReading()
        }
    }
}
