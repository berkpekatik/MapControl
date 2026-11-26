package com.mapcontrol.nav

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.desaysv.ivi.extra.project.carinfo.HUDID
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy
import com.desaysv.ivi.extra.project.carinfo.proxy.IServiceConnectListener
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import com.mapcontrol.vehicle.CarInfoRead
import com.mapcontrol.vehicle.material.OemEolConfigReader

/** Gerçek araç köprüsü; Android testlerinde aynı sözleşmeyle kontrollü taşıma kullanılır. */
internal interface HudVehicle {
    /** HUD donanım kanıtı: true var, false yok, null bilinmiyor. */
    fun hudSupport(): Boolean?
    fun connected(): Boolean
    /** Araç güç modu; okunamazsa null (güç bilinmiyor). */
    fun powerMode(): Int?
    fun connect(context: Context)
    fun listen(listener: IServiceConnectListener)
    fun unlisten(listener: IServiceConnectListener)
    fun send(packet: IntArray): Boolean
    /** ProBro.onStopNavi: iki navigasyon bitiş yayını. Bağlantı donanım kanıtı sayılmaz. */
    fun stopNavigation(): Boolean
}

internal class NativeHudVehicle(context: Context) : HudVehicle {
    private val proxy = CarInfoProxy.getInstance()
    private val app = context.applicationContext
    private var checkedAt: Long? = null
    private var support: Boolean? = null
    private val decider = HudSupportDecider()

    override fun hudSupport(): Boolean? {
        val now = SystemClock.elapsedRealtime()
        if (checkedAt?.let { now - it < 2_000L } != true) {
            val config = OemEolConfigReader.readConfig4Bytes()
            // Config bu araçta üçüncü taraf uygulamaya kapalıysa (SELinux), HUD'un kendi yanıtı donanım kanıtıdır.
            val configBit = OemEolConfigReader.configBit(config, 4, 4)
            val state = if (configBit == null) CarInfoRead.read(VDEventCarInfo.MODULE_HUD, HUDID.ID_HUD_STATE) else null
            val display = if (configBit == null) CarInfoRead.read(VDEventCarInfo.MODULE_HUD, HUDID.ID_HUD_DISPLAY) else null
            val probeText = state?.joinToString(",") + "/" + display?.joinToString(",")
            val next = decider.decide(configBit, state, display, proxy.isServiceConnnected, now)
            if (next != support || checkedAt == null) {
                Log.i("HudNavigation", "HUD config4=" + config?.joinToString(",") + " yoklama=" + probeText + " destek=" + next)
            }
            support = next
            checkedAt = now
        }
        return support
    }

    override fun powerMode(): Int? = CarInfoRead.readOnly(ReadOnlyID.ID_SYSTEM_POWER_MODE)?.firstOrNull()

    override fun connected(): Boolean = proxy.isServiceConnnected

    override fun connect(context: Context) {
        proxy.init(app)
    }

    override fun listen(listener: IServiceConnectListener) { proxy.regServiceConnectListener(listener) }
    override fun unlisten(listener: IServiceConnectListener) { proxy.unRegServiceConnectListener(listener) }

    override fun send(packet: IntArray): Boolean = try {
        proxy.sendItemValues(VDEventCarInfo.MODULE_HUD, HUDID.ID_HUD_IHU_INTERCONNECT, packet)
        true
    } catch (e: RuntimeException) {
        Log.w("HudNavigation", "Yerleşik HUD paketi gönderilemedi", e)
        false
    }

    override fun stopNavigation(): Boolean {
        return try {
            app.sendBroadcast(Intent("turbodog.navigation.system.message").putExtra("CODE", 203))
            app.sendBroadcast(Intent("AUTONAVI_STANDARD_BROADCAST_SEND")
                .putExtra("KEY_TYPE", 10019)
                .putExtra("EXTRA_STATE", 12))
            true
        } catch (e: RuntimeException) {
            Log.w("HudNavigation", "Navigasyon bitiş yayını gönderilemedi", e)
            false
        }
    }
}
