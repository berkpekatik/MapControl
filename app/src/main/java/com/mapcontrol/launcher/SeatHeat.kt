package com.mapcontrol.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.desaysv.ivi.extra.project.carinfo.CarSettingID
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import com.mapcontrol.R
import com.mapcontrol.vehicle.ClimateAutomationStore
import java.util.concurrent.Executor

/** Koltuk ısıtma durumu: seviyeler 0 (kapalı) .. 3; okunamıyorsa null. */
internal data class SeatHeatState(
    val connected: Boolean = false,
    val driver: Int? = null,
    val passenger: Int? = null,
)

/**
 * Koltuk ısıtma okuma/yazma mantığı; koltuk kutucuğu ([SeatTile]) ve mini gösterge paylaşır.
 *
 * Durum 3 sn'de bir okunur; dokununca seviye Kapalı → 3 → 2 → 1 → Kapalı sırasıyla değişir, ekran hemen güncellenir (iyimser),
 * komut arka planda yalnızca [LauncherEdition] üzerinden gider, kısa süre sonra gerçek durum okunur.
 * Yalnızca ClimateAutomationEngine'de kullanılan, değer anlamı bilinen komutlar gönderilir.
 */
internal class SeatHeatModel(
    private val context: Context,
    /** true: koltuk soğutma (havalandırma) sinyalleri; false: ısıtma (davranış değişmez). */
    private val vent: Boolean = false,
    /** Sıralı yazma kuyruğu; birleşik koltuk ısıtma ve soğutma için aynı kuyruğu verir ki "önce kapat, sonra aç" sırası korunsun. */
    private val io: Executor = LauncherIo.serial(),
    private val onChange: (SeatHeatState) -> Unit,
) {

    private val main = Handler(Looper.getMainLooper())
    private var startup: LauncherStartup.Deferred? = null

    var state = SeatHeatState()
        private set

    /** Klima durumu 3 sn'de bir okunur; sürücü bir şeye bastıktan kısa süre sonra da yeniden okunur. */
    private val poll = object : Runnable {
        override fun run() {
            refreshNow()
            main.postDelayed(this, POLL_MS)
        }
    }

    fun onVisible() {
        onChange(state)
        startup?.cancel()
        startup = LauncherStartup.later { main.post(poll) }
    }

    fun onHidden() {
        startup?.cancel()
        main.removeCallbacks(poll)
    }

    /** Dokunuş: sıradaki seviyeyi yollar. Test sürümünde yalnızca bilgi mesajı gösterir. */
    fun cycle(driver: Boolean) {
        set(driver, nextLevel(if (driver) state.driver else state.passenger))
    }

    /** Bu model şu an araca yazabilir mi (test sürümü değil; soğutmada yazma doğrulanmış ya da test anahtarı açık). */
    val writable: Boolean
        get() = !LauncherEdition.READ_ONLY &&
            (!vent || WidgetWritePolicy.gate(LauncherEdition.SEAT_VENT_WRITE_VERIFIED, LauncherEdition.WRITES_ON))

    /**
     * Seviyeyi (0..3) doğrudan yazar: ekran hemen güncellenir, komut sıralı kuyrukta gider, kısa süre sonra gerçek durum okunur.
     * Yazılamıyorsa (test sürümü, doğrulanmamış soğutma) sakin bir bilgi mesajı gösterir ve false döner. Sürüş kilidi yoktur;
     * araç komutu reddederse sonraki okuma eski seviyeyi geri getirir.
     */
    fun set(driver: Boolean, level: Int): Boolean {
        val id = if (driver) {
            if (vent) CarSettingID.ID_CAR_DRIVER_SEAT_VENTILATION else CarSettingID.ID_CAR_DRIVER_SEAT_HEAT
        } else {
            if (vent) CarSettingID.ID_CAR_COPILOT_SEAT_VENTILATION else CarSettingID.ID_CAR_COPILOT_SEAT_HEAT
        }
        // Test sürümünde dokunuş hiçbir şey yazmaz; sürücü boşuna basmasın diye sakin bir açıklama gösterilir.
        if (LauncherEdition.READ_ONLY) {
            Toast.makeText(context, R.string.launcher_read_only_seat, Toast.LENGTH_LONG).show()
            return false
        }
        // Soğutma yazması araçta doğrulanana kadar kapalı: yalnızca sakin bir bilgi mesajı.
        if (!writable) {
            Toast.makeText(context, R.string.launcher_seat_vent_later, Toast.LENGTH_SHORT).show()
            return false
        }
        val lv = level.coerceIn(0, ClimateAutomationStore.LEVEL_MAX)
        if (LauncherDemo.enabled) {
            // Demo: araç yok; seviye demo durumuna yazılır ki okuma onu geri getirsin.
            LauncherDemo.setSeatLevel(vent, driver, lv)
        } else {
            val proxy = CarInfoProxy.getInstance()
            if (!proxy.isServiceConnnected) {
                android.util.Log.w("WidgetWrite", "widget=Seat id=$id value=? result=refused-no-service")
                return false
            }
            io.runSafely {
                try {
                    if (vent) LauncherEdition.sendSeatVentLevel(proxy, driver, lv)
                    else LauncherEdition.sendItemValue(proxy, SETTING, id, encode(lv))
                } catch (e: RuntimeException) {
                    // Okuma turu gerçek durumu geri getirir.
                }
            }
        }
        state = if (driver) state.copy(driver = lv) else state.copy(passenger = lv)
        onChange(state)
        main.removeCallbacks(poll)
        main.postDelayed(poll, CONFIRM_DELAY_MS)
        return true
    }

    private fun refreshNow() {
        io.runSafely {
            val read = read()
            main.post {
                // Okunan durum değişmediyse yeniden çizme (3 sn'lik yoklamada boşuna bind yok).
                if (read == state) return@post
                state = read
                onChange(state)
            }
        }
    }

    private fun read(): SeatHeatState {
        if (LauncherDemo.enabled) return SeatHeatState(
            connected = true,
            driver = if (vent) LauncherDemo.DRIVER_VENT_LEVEL else LauncherDemo.DRIVER_SEAT_LEVEL,
            passenger = if (vent) LauncherDemo.PASSENGER_VENT_LEVEL else LauncherDemo.PASSENGER_SEAT_LEVEL,
        )
        val proxy = CarInfoProxy.getInstance()
        if (!proxy.isServiceConnnected) return SeatHeatState()
        return try {
            if (vent) return SeatHeatState(
                connected = true,
                driver = proxy.first(CarSettingID.ID_CAR_DRIVER_SEAT_VENTILATION)?.let(::decodeVent),
                passenger = proxy.first(CarSettingID.ID_CAR_COPILOT_SEAT_VENTILATION)?.let(::decodeVent),
            )
            SeatHeatState(
                connected = true,
                driver = proxy.first(CarSettingID.ID_CAR_DRIVER_SEAT_HEAT)?.let(::decode),
                passenger = proxy.first(CarSettingID.ID_CAR_COPILOT_SEAT_HEAT)?.let(::decode),
            )
        } catch (e: RuntimeException) {
            SeatHeatState()
        }
    }

    private fun CarInfoProxy.first(id: Int): Int? = getItemValues(SETTING, id)?.firstOrNull()

    companion object {
        private val SETTING = VDEventCarInfo.MODULE_CAR_SETTING
        private const val POLL_MS = 3_000L
        private const val CONFIRM_DELAY_MS = 800L

        /** Seviye 0..3 → etiket; ClimateAutomationStore.LEVEL_MAX = 3. */
        val LEVEL_LABELS = intArrayOf(
            R.string.launcher_seat_off, R.string.launcher_seat_low, R.string.launcher_seat_mid, R.string.launcher_seat_high,
        )

        fun levelLabel(level: Int): Int = LEVEL_LABELS[level.coerceIn(0, LEVEL_LABELS.lastIndex)]

        /** Aracın kendi düğmesi gibi: Kapalı → 3 → 2 → 1 → Kapalı (kapalıdan açınca en yüksek seviyeden başlar). */
        fun nextLevel(current: Int?): Int {
            val level = current ?: 0
            return if (level <= 0) ClimateAutomationStore.LEVEL_MAX else level - 1
        }

        /**
         * Araç değeri doğrudan seviyedir: 0 = kapalı, 1..3 = seviye. Tiggo 7'de 2026-10-31'de ölçüldü
         * (ID_CAR_DRIVER_SEAT_HEAT / ID_CAR_COPILOT_SEAT_HEAT, 3→2→1→0 kademeli okundu).
         */
        fun decode(wire: Int): Int = wire.coerceIn(0, ClimateAutomationStore.LEVEL_MAX)

        /**
         * Soğutma değeri: 0 = kapalı, 1..3 = seviye. Sürücü (CS 93) araç testinde doğrulandı; yolcu (94) aynı varsayılıyor.
         */
        fun decodeVent(wire: Int): Int = wire.coerceIn(0, ClimateAutomationStore.LEVEL_MAX)

        /** Yazma kodlaması: kapalı = 1, seviye 1..3 → 2..4 (ısıtma ve sürücü soğutma CS 93 araçta doğrulandı). */
        fun encode(level: Int): Int = if (level <= 0) 1 else level.coerceIn(
            ClimateAutomationStore.LEVEL_MIN, ClimateAutomationStore.LEVEL_MAX) + 1
    }
}
