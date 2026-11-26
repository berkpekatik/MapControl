package com.mapcontrol.launcher

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.desaysv.ivi.extra.project.carinfo.CarSettingID
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import com.mapcontrol.R
import com.mapcontrol.vehicle.CarInfoRead

/**
 * Koltuk hafızasının saf kuralları (JVM'de sınanır). OEM ayarlarından (SVSetting `VehicleComfortFragment` + presenter `n`):
 *  - Çağırma: CS 52 ← [konum] (konum 1..3; "Konumu N" düğmesi `sendItemValues(CS, 52, [N])`).
 *  - Okuma: CS 52 = [hazır, etkin konum]; [0] == 1 koltuk ayarı hazır, [1] 1..3 etkin konum, [1] == 16 çağırma başarısız.
 *  - Kaydetme CS 54'tür; burada YOK (profil üzerine yazılmasın).
 * Günay'ın isteğiyle iki sürücü profili sunulur ([SLOTS]); OEM menüsünde üçüncü konum da var.
 */
internal object SeatMemoryLogic {

    const val ID_SELECT = CarSettingID.ID_CAR_SELECT_SEAT_MIRROR_POSI // 52

    val SLOTS: List<Int> = listOf(1, 2)

    const val RESTORE_FAILED = 16

    /** OEM aynı düğmeye 1 sn içinde ikinci basışı yok sayar; biz koltuk hareketi için daha geniş tutarız. */
    const val RESEND_GAP_MS = 3_000L

    fun ready(v: IntArray?): Boolean = v != null && v.size >= 2 && v[0] == 1

    /** Etkin konum (1..3); okunamadı ya da başka değer null. */
    fun activeSlot(v: IntArray?): Int? = v?.takeIf { it.size >= 2 }?.get(1)?.takeIf { it in 1..3 }

    fun failed(v: IntArray?): Boolean = v != null && v.size >= 2 && v[1] == RESTORE_FAILED

    /**
     * CS 52 yoklaması: hazır ve geçerli etkin konum varsa koltuk hafızası vardır (true). Diğer her durum "bilinmiyor"dur
     * (null), "yok" değil: kontak kapalıyken ya da servis yanıtsızken araç 0 döndürebilir.
     */
    fun probeSupport(v: IntArray?): Boolean? = if (ready(v) && activeSlot(v) != null) true else null

    /** Config 127 kesinse o (OEM menüsü de ona bakar); bilinmiyorsa yoklama. */
    fun support(config: Boolean?, probe: Boolean?): Boolean? = config ?: probe

    enum class Action { SEND, WAIT, NOT_READY, REFUSE }

    /**
     * [read] taze CS 52 okuması. Sıra: geçersiz konum REFUSE; son gönderimden beri [RESEND_GAP_MS] dolmadıysa WAIT;
     * okunamadı ya da hazır değilse NOT_READY (varsayımla komut gitmez); değilse SEND. Etkin konuma yeniden basmak da
     * gönderir: OEM'de olduğu gibi elle oynanmış koltuğu kayıtlı konuma geri getirir.
     */
    fun decide(slot: Int, read: IntArray?, nowMs: Long, lastSentMs: Long?): Action {
        if (slot !in SLOTS) return Action.REFUSE
        if (lastSentMs != null && nowMs - lastSentMs < RESEND_GAP_MS) return Action.WAIT
        if (!ready(read)) return Action.NOT_READY
        return Action.SEND
    }
}

/**
 * Sürücü koltuk hafızasının okuma/yazma yardımcısı; sürücü koltuğu seçicisindeki "Hafıza 1 · 2" satırı kullanır ([SeatTile]).
 * Seçici bir açılır pencere olduğundan dokunuş zaten iki adımdır (öğe → konum). Yazma tek noktadan
 * ([LauncherEdition.sendItemValue] → [WidgetWritePolicy]: beyaz liste, park kapısı) gider. Satır yalnız destek kesinken
 * ([CarFeatures.seatMemory]) gösterilir.
 */
internal class SeatMemoryRecall(private val activity: Activity) {

    private val main = Handler(Looper.getMainLooper())
    private val io = LauncherIo.serial()
    @Volatile private var lastSentMs: Long? = null

    /** Demo kipinde (emülatör) satır görünür ama hiçbir şey gönderilmez ([recall]). */
    val available: Boolean get() = CarFeatures.seatMemory == true || LauncherDemo.enabled

    private fun readNow(): IntArray? {
        if (!CarInfoProxy.getInstance().isServiceConnnected) return null
        return CarInfoRead.read(VDEventCarInfo.MODULE_CAR_SETTING, SeatMemoryLogic.ID_SELECT)
    }

    /** Etkin konumu arka planda okur, sonucu ana iş parçacığında verir (null bilinmiyor). */
    fun readActive(done: (Int?) -> Unit) {
        if (LauncherDemo.enabled) { done(1); return }
        io.runSafely {
            val v = readNow()
            main.post { done(SeatMemoryLogic.activeSlot(v)) }
        }
    }

    /** [slot] konumunu çağırır; sonuç [done] ile (gönderildiyse etkin konum olarak) verilir. */
    fun recall(slot: Int, done: (Int?) -> Unit) {
        if (LauncherDemo.enabled) { done(slot); return }
        io.runSafely {
            val current = readNow()
            val now = SystemClock.elapsedRealtime()
            val action = SeatMemoryLogic.decide(slot, current, now, lastSentMs)
            // Park kapısı ya da beyaz liste reddederse nedeni WidgetWriter gösterir; araca bir şey gitmez.
            val sent = action == SeatMemoryLogic.Action.SEND &&
                LauncherEdition.sendItemValue(CarInfoProxy.getInstance(), VDEventCarInfo.MODULE_CAR_SETTING,
                    SeatMemoryLogic.ID_SELECT, slot)
            if (sent) lastSentMs = now
            com.mapcontrol.util.DiagJournal.event("SeatMemory", "konum=$slot karar=$action gönderildi=$sent cs52=" +
                current?.joinToString(",", "[", "]"))
            main.post {
                when {
                    sent -> {
                        toast(activity.getString(R.string.launcher_seat_memory_moving, slot))
                        main.postDelayed({ checkResult() }, RESULT_CHECK_MS)
                    }
                    action == SeatMemoryLogic.Action.NOT_READY -> toast(activity.getString(R.string.launcher_seat_memory_not_ready))
                    else -> Unit
                }
                done(if (sent) slot else SeatMemoryLogic.activeSlot(current))
            }
        }
    }

    /** Gönderimden sonra CS 52[1] == 16 ise OEM gibi "başarısız" gösterilir. */
    private fun checkResult() {
        io.runSafely {
            val v = readNow()
            com.mapcontrol.util.DiagJournal.event("SeatMemory", "sonuç cs52=" + v?.joinToString(",", "[", "]"))
            if (SeatMemoryLogic.failed(v)) main.post { toast(activity.getString(R.string.launcher_seat_memory_failed)) }
        }
    }

    private fun toast(msg: String) {
        if (!activity.isFinishing && !activity.isDestroyed) Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        /** OEM çağırma sonucunu ~1 sn içinde yayınlar; koltuk hareketi sürerken 52 yeniden okunur. */
        const val RESULT_CHECK_MS = 2_500L
    }
}
