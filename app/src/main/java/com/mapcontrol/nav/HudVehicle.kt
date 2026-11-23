package com.mapcontrol.nav

import android.content.Context
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
    fun hudSupport(): Boolean?
    fun connected(): Boolean
    fun powerMode(): Int?
    fun connect(context: Context)
    fun listen(listener: IServiceConnectListener)
    fun unlisten(listener: IServiceConnectListener)
    fun send(packet: IntArray): Boolean
}

internal class NativeHudVehicle : HudVehicle {
    private val proxy = CarInfoProxy.getInstance()
    private var checkedAt: Long? = null
    private var support: Boolean? = null

    override fun hudSupport(): Boolean? {
        val now = SystemClock.elapsedRealtime()
        if (checkedAt?.let { now - it < 2_000L } != true) {
            val config = OemEolConfigReader.readConfig4Bytes()
            // Config bu araçta üçüncü taraf uygulamaya kapalıysa (SELinux), HUD'un kendi yanıtı donanım kanıtıdır.
            support = OemEolConfigReader.configBit(config, 4, 4) ?: probeHud()
            Log.i("HudNavigation", "HUD config4=" + config?.joinToString(",") + " destek=" + support)
            checkedAt = now
        }
        return support
    }
    /** Config okunamayan araçlarda (SELinux) HUD ünitesinin kendi yanıtı donanım kanıtıdır. */
    private fun probeHud(): Boolean? {
        val state = CarInfoRead.read(VDEventCarInfo.MODULE_HUD, HUDID.ID_HUD_STATE)
        val display = CarInfoRead.read(VDEventCarInfo.MODULE_HUD, HUDID.ID_HUD_DISPLAY)
        Log.i("HudNavigation", "HUD probe state=" + state?.joinToString(",") + " display=" + display?.joinToString(","))
        // Yanıt yoksa "yok" denmez; bilinmiyor kalır ve gönderim yapılmaz.
        return if (state != null || display != null) true else null
    }
    override fun connected(): Boolean = proxy.isServiceConnnected
    override fun powerMode(): Int? = CarInfoRead.readOnly(ReadOnlyID.ID_SYSTEM_POWER_MODE)?.firstOrNull()
    override fun connect(context: Context) { proxy.init(context) }
    override fun listen(listener: IServiceConnectListener) { proxy.regServiceConnectListener(listener) }
    override fun unlisten(listener: IServiceConnectListener) { proxy.unRegServiceConnectListener(listener) }
    override fun send(packet: IntArray): Boolean {
        return try {
            proxy.sendItemValues(VDEventCarInfo.MODULE_HUD, HUDID.ID_HUD_IHU_INTERCONNECT, packet)
            true
        } catch (e: RuntimeException) {
            Log.w("HudNavigation", "Yerleşik HUD paketi gönderilemedi", e)
            false
        }
    }
}
