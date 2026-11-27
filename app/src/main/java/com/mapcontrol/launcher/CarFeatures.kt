package com.mapcontrol.launcher

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.desaysv.ivi.vdb.client.VDBus
import com.desaysv.ivi.vdb.client.bind.VDServiceDef
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import com.mapcontrol.vehicle.CarInfoRead
import com.mapcontrol.vehicle.material.OemEolConfigReader

/**
 * EOL araç yapılandırmasından (CarConfig) widget kararları: saf çözücüler JVM'de sınanır, okuma [CarFeatures.init]'tedir.
 * Bit yerleri araçta ölçüldü: 109 sürüş modları = configs4[5] & 31; 120 direksiyon ısıtma = configs5[0] bit 1;
 * 163 ülke/bölge = configs5[6] & 255. Eksik ya da kısa (< 8 bayt) blok "bilinmiyor"dur (OEM 0 döner; biz 0'ı varsaymayız).
 */
internal object CarConfigMath {

    /** OEM ayarlarıyla aynı: bu ülkelerde koltuk ve direksiyon ısıtma gizlenir. */
    val HEAT_HIDDEN_REGIONS: Set<Int> = setOf(5, 7, 32)

    /**
     * Direksiyon ısıtma var mı: config 120 = 1 ve ülke (163) gizlenenlerden değil. OEM'in M1EFL1 istisnası parça numarasına
     * bakar (okunamaz); Tiggo 7/8 M1EFL1 değil, istisna uygulanmaz. Blok okunamadıysa null (bilinmiyor → widget gizli).
     * Tiggo 7'de ısıtıcı yok ama CS 160 yazmaları geri yansıtır: karar geri okumadan DEĞİL, buradan verilir.
     */
    fun steerHeat(configs5: IntArray?): Boolean? {
        if (configs5 == null || configs5.size < 8) return null
        if ((configs5[0] shr 1) and 1 != 1) return false
        return (configs5[6] and 255) !in HEAT_HIDDEN_REGIONS
    }

    /**
     * Sürücü koltuk hafızası: config 127 = configs5[1] bit 1 (OEM `EolConfig` case 127). OEM ayarları "Konumu 1/2/3"
     * satırını yalnız bu bit 1 iken gösterir (`VehicleComfortFragment.initOfflineConfig`). Blok okunamadıysa null.
     */
    fun seatMemory(configs5: IntArray?): Boolean? {
        if (configs5 == null || configs5.size < 8) return null
        return OemEolConfigReader.seatMirrorMemoryFromConfig5(configs5)
    }
}

/**
 * Süreç boyunca bilinen yapılandırma kararları. İlk değerler bir önceki açılıştan (SharedPreferences) gelir ki Sahne düzeni
 * açılışta doğru kurulsun; [init] arka planda VDBus'tan (yoksa persist özelliğinden) yeniden okur ve değişeni saklar.
 * Okunamayan değer null kalır (direksiyon ısıtma gizli, sürüş modları Tiggo 7 varsayılanı).
 */
internal object CarFeatures {
    private const val TAG = "CarFeatures"
    private const val PREFS = "launcher_car_config"
    private const val KEY_STEER = "steer_heat"      // -1 bilinmiyor, 0 yok, 1 var
    private const val KEY_DRIVE = "config109"       // -1 bilinmiyor
    private const val KEY_SEAT = "seat_memory"      // -1 bilinmiyor, 0 yok, 1 var
    private const val BIND_WAIT_MS = 3_000L
    private const val BIND_STEP_MS = 100L
    private const val TRIES = 3
    private const val RETRY_GAP_MS = 5_000L

    /** Direksiyon ısıtma (config 120 + ülke); null bilinmiyor. */
    @Volatile
    var steerHeat: Boolean? = null

    /** CarConfig 109 (sürüş modu listesi); null bilinmiyor. */
    @Volatile
    var config109: Int? = null

    /**
     * Sürücü koltuk hafızası; null bilinmiyor (widget gizli). Önce config 127; okunamazsa CS 52 yoklaması
     * ([SeatMemoryLogic.probeSupport]). Bu uygulamanın config okuması bazı araçlarda boş döner (SELinux).
     */
    @Volatile
    var seatMemory: Boolean? = null

    @Volatile
    private var started = false

    /** Kayıtlı değerleri hemen yükler, bir kez (süreç başına) arka planda araçtan okur. Her yerden çağrılabilir. */
    fun init(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
        }
        val app = context.applicationContext
        // Widget yazmalarının servis bağlantısı yeniden denemesi için bağlam ([WidgetWriter.send]).
        WidgetWriter.attach(app)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        when (prefs.getInt(KEY_STEER, -1)) {
            0 -> steerHeat = false
            1 -> steerHeat = true
        }
        prefs.getInt(KEY_DRIVE, -1).let { if (it >= 0) config109 = it }
        when (prefs.getInt(KEY_SEAT, -1)) {
            0 -> seatMemory = false
            1 -> seatMemory = true
        }
        if (LauncherDemo.enabled) return
        Thread({ readLoop(app) }, "car-config").apply { isDaemon = true }.start()
    }

    private fun readLoop(app: Context) {
        com.mapcontrol.util.DiagJournal.event("SeatMemory", "okuma başladı")
        repeat(TRIES) { attempt ->
            if (attempt > 0) SystemClock.sleep(RETRY_GAP_MS)
            val (c4, c5) = try {
                bind(app)
                OemEolConfigReader.readConfig4Bytes() to OemEolConfigReader.readConfig5Bytes()
            } catch (e: RuntimeException) {
                Log.w(TAG, "config okunamadı: ${e.javaClass.simpleName}")
                null to null
            }
            val steer = CarConfigMath.steerHeat(c5)
            val drive = DriveModes.config109(c4)
            val seatConfig = CarConfigMath.seatMemory(c5)
            val probe = if (seatConfig == null) probeSeat52(app) else null
            val seat = try {
                SeatMemoryLogic.support(seatConfig, SeatMemoryLogic.probeSupport(probe))
            } catch (t: Throwable) {
                com.mapcontrol.util.DiagJournal.event("SeatMemory", "karar hatası: $t")
                null
            }
            val edit = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            if (steer != null) { steerHeat = steer; edit.putInt(KEY_STEER, if (steer) 1 else 0) }
            if (drive != null) { config109 = drive; edit.putInt(KEY_DRIVE, drive) }
            if (seat != null) { seatMemory = seat; edit.putInt(KEY_SEAT, if (seat) 1 else 0) }
            edit.apply()
            Log.i(TAG, "config120+163 direksiyon ısıtma=$steer, config109=$drive, koltuk hafızası=$seat " +
                "(config127=$seatConfig cs52=${probe?.joinToString(",", "[", "]")}) (deneme ${attempt + 1})")
            com.mapcontrol.util.DiagJournal.event("SeatMemory", "destek=$seat config127=$seatConfig cs52=" +
                probe?.joinToString(",", "[", "]") + " deneme=${attempt + 1}")
            if (steer != null && drive != null && seat != null) return
        }
    }

    /** CS 52 ham okuması (CAR_INFO bağlanana kadar en çok [BIND_WAIT_MS] bekler); yanıt yoksa null. */
    private fun probeSeat52(app: Context): IntArray? = try {
        val proxy = CarInfoProxy.getInstance()
        if (!proxy.isServiceConnnected) proxy.init(app)
        val until = SystemClock.elapsedRealtime() + BIND_WAIT_MS
        while (!proxy.isServiceConnnected && SystemClock.elapsedRealtime() < until) SystemClock.sleep(BIND_STEP_MS)
        if (proxy.isServiceConnnected) CarInfoRead.read(VDEventCarInfo.MODULE_CAR_SETTING, SeatMemoryLogic.ID_SELECT) else null
    } catch (t: Throwable) {
        // RemoteException gibi denetimli hatalar da gelebilir: iş parçacığı ölmesin, neden günlüğe.
        com.mapcontrol.util.DiagJournal.event("SeatMemory", "CS 52 okunamadı: $t")
        null
    }

    private fun bind(app: Context) {
        val vdbus = VDBus.getDefault()
        vdbus.init(app)
        val type = VDServiceDef.ServiceType.VEHICLE_DEVICE
        if (vdbus.isServiceConnected(type)) return
        vdbus.bindService(type)
        val until = SystemClock.elapsedRealtime() + BIND_WAIT_MS
        while (!vdbus.isServiceConnected(type) && SystemClock.elapsedRealtime() < until) SystemClock.sleep(BIND_STEP_MS)
    }
}
