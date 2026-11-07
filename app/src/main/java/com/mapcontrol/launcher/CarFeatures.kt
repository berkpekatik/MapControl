package com.mapcontrol.launcher

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.desaysv.ivi.vdb.client.VDBus
import com.desaysv.ivi.vdb.client.bind.VDServiceDef
import com.mapcontrol.vehicle.material.OemEolConfigReader

/**
 * EOL araç yapılandırmasından (CarConfig) widget kararları: saf çözücüler JVM'de sınanır, okuma [CarFeatures.init]'tedir.
 * Bit yerleri OEM `EolConfig.getConfig`: 109 sürüş modları = configs4[5] & 31; 120 direksiyon ısıtma = configs5[0] bit 1;
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
        if (LauncherDemo.enabled) return
        Thread({ readLoop(app) }, "car-config").apply { isDaemon = true }.start()
    }

    private fun readLoop(app: Context) {
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
            val edit = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            if (steer != null) { steerHeat = steer; edit.putInt(KEY_STEER, if (steer) 1 else 0) }
            if (drive != null) { config109 = drive; edit.putInt(KEY_DRIVE, drive) }
            edit.apply()
            Log.i(TAG, "config120+163 direksiyon ısıtma=$steer, config109=$drive (deneme ${attempt + 1})")
            if (steer != null && drive != null) return
        }
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
