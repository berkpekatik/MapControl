package com.mapcontrol.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import com.mapcontrol.R
import com.mapcontrol.comfort.ComfortPlan
import com.mapcontrol.comfort.ParkGate
import com.mapcontrol.vehicle.VehicleQuickControls
import java.util.concurrent.Executors

/**
 * Launcher widget'larının araca yazdığı TEK yer (iki derlemede de, yalnızca [LauncherEdition]'dan, kullanıcı dokunuşuyla).
 * Her deneme: [WidgetWritePolicy.evaluate] (beyaz liste → doğrulanmış mı → park kapılıysa hız 0 ve vites P mi) →
 * `Log.i("WidgetWrite", ...)` tek satır → gönderilirse 3 sn'lik geri okuma (ilgili sinyallerin değişimleri günlüğe).
 * Park kapısı ya da doğrulama reddederse araca hiçbir şey gitmez ve kullanıcıya kısa neden gösterilir.
 */
internal object WidgetWriter {

    private const val READBACK_MS = 3_000L
    private const val READBACK_INTERVAL_MS = 250L

    private val readbackPool = Executors.newCachedThreadPool { r -> Thread(r, "widget-readback").apply { isDaemon = true } }

    /** Servis bağlı değilken CarInfoProxy.init için uygulama bağlamı ([attach]); yoksa yeniden deneme yapılmaz. */
    @Volatile
    private var appContext: Context? = null

    /** Bağlantı yeniden denemesi için uygulama bağlamını bir kez verir (widget kurucuları çağırır; tekrarı zararsız). */
    fun attach(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /**
     * CarInfoProxy.sendItemValue ile gönderilen yazmalar (koltuk, klima, bagaj, sürüş modu, direksiyon ısıtma). Servis bağlı
     * değilse bağlanıp [WidgetWritePolicy.CONNECT_RETRY_MS] sonra bir kez yeniden dener ([WidgetWritePolicy.connectStep]).
     */
    fun send(proxy: CarInfoProxy, module: Int, id: Int, value: Int): Boolean =
        attempt(module, id, value, isConnected = { proxy.isServiceConnnected },
            connect = appContext?.let { ctx -> { proxy.init(ctx) } }) {
            proxy.sendItemValue(module, id, value)
        }

    /** Ses alanı (DSP): beyaz listede modül = SOUND_FIELD olay kimliği, kimlik sabit, değer = fade; balance korunarak yazılır. */
    fun sendSoundField(balance: Int, fade: Int): Boolean =
        attempt(com.desaysv.ivi.vdb.event.id.dsp.VDEventDsp.SOUND_FIELD, SoundFieldMath.WRITE_ID, fade, isConnected = { true }) {
            SoundFieldIo.write(balance, fade)
        }

    /** Merkezi kilit: komut VehicleQuickControls içinde gider; değer aynı formülle ([VehicleQuickControls.centralLockValue]) denetlenir. */
    fun sendLock(context: Context, locked: Boolean): Boolean {
        attach(context)
        val controls = VehicleQuickControls.getInstance(context.applicationContext)
        return attempt(
            VDEventCarInfo.MODULE_CAR_SETTING, com.desaysv.ivi.extra.project.carinfo.CarSettingID.ID_CAR_IVI_LOCK,
            VehicleQuickControls.centralLockValue(locked),
            isConnected = { controls.isCarInfoConnected },
        ) { controls.setCentralLock(locked) }
    }

    /** Park kapısı girdisi: gösterge hızı (119), eski hız (0) ve vites (26) o anki okumadan. */
    private fun parkDecision(): ParkGate.Decision {
        val ro = VDEventCarInfo.MODULE_READONLY_INFO
        return ParkGate.evaluate(
            CarSignals.readInt(ro, ReadOnlyID.ID_SPEED_GAUGE_DISPLAY),
            CarSignals.readInt(ro, ReadOnlyID.ID_CAR_SPEED),
            CarSignals.readInt(ro, ReadOnlyID.ID_GEARBOX_STATE),
        )
    }

    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Reddin nedenini kısa bildirimle gösterir (bağlam yoksa yalnızca günlük). */
    private fun notifyRefused(res: Int) {
        val ctx = appContext ?: return
        main.post { Toast.makeText(ctx, res, Toast.LENGTH_SHORT).show() }
    }

    /** true: kapıdan geçti (gönderildi ya da bağlantı sonrası gönderilecek); false: reddedildi, araca bir şey gitmedi. */
    private fun attempt(
        module: Int, id: Int, value: Int, isConnected: () -> Boolean, connect: (() -> Unit)? = null, send: () -> Unit,
    ): Boolean {
        val verdict = WidgetWritePolicy.evaluate(module, id, value, ::parkDecision)
        if (verdict !is WidgetWritePolicy.Verdict.Allowed) {
            val widget = WidgetWritePolicy.find(module, id, value)?.widget
            Log.w(WidgetWritePolicy.LOG_TAG, WidgetWritePolicy.logLine(widget, module, id, value, WidgetWritePolicy.resultText(verdict)))
            when (verdict) {
                is WidgetWritePolicy.Verdict.ParkRefused -> notifyRefused(R.string.launcher_write_park_only)
                is WidgetWritePolicy.Verdict.NotVerified -> notifyRefused(R.string.launcher_write_unverified)
                else -> Unit
            }
            return false
        }
        val entry = verdict.entry
        when (WidgetWritePolicy.connectStep(isConnected(), retried = connect == null)) {
            WidgetWritePolicy.ConnectStep.SEND -> deliver(entry, module, id, value, send)
            WidgetWritePolicy.ConnectStep.REFUSE -> { refuseNoService(entry, module, id, value); return false }
            WidgetWritePolicy.ConnectStep.CONNECT_AND_RETRY -> {
                Log.i(WidgetWritePolicy.LOG_TAG, WidgetWritePolicy.logLine(entry.widget, module, id, value, "connecting"))
                runCatching { connect?.invoke() }
                readbackPool.execute {
                    SystemClock.sleep(WidgetWritePolicy.CONNECT_RETRY_MS)
                    when (WidgetWritePolicy.connectStep(isConnected(), retried = true)) {
                        WidgetWritePolicy.ConnectStep.SEND -> deliver(entry, module, id, value, send)
                        else -> refuseNoService(entry, module, id, value)
                    }
                }
            }
        }
        return true
    }

    private fun refuseNoService(entry: WidgetWritePolicy.Entry, module: Int, id: Int, value: Int) {
        Log.w(WidgetWritePolicy.LOG_TAG, WidgetWritePolicy.logLine(entry.widget, module, id, value, "refused-no-service"))
    }

    private fun deliver(entry: WidgetWritePolicy.Entry, module: Int, id: Int, value: Int, send: () -> Unit) {
        val before = entry.readBack.map { it to describe(it) }
        send()
        Log.i(WidgetWritePolicy.LOG_TAG, WidgetWritePolicy.logLine(entry.widget, module, id, value, "sent") +
            " before=" + before.joinToString(" ") { "${it.first.name}=${it.second}" })
        readbackPool.execute { watch(entry, before) }
    }

    private fun describe(s: ComfortPlan.Signal): String =
        if (s.module == com.desaysv.ivi.vdb.event.id.dsp.VDEventDsp.SOUND_FIELD) SoundFieldIo.read()?.let { "[balance=${it.balance},fade=${it.fade}]" } ?: "null"
        else if (s.module == VDEventCarInfo.MODULE_HVAC && s.id == com.desaysv.ivi.extra.project.carinfo.HvacID.ID_AC_DISPLAY_ACTIVE)
            ClimatePanelBits.describe(CarSignals.readInt(s.module, s.id)) // HVAC 9 bit alanı: ör. 1[front]
        else CarSignals.read(s.module, s.id)?.joinToString(",", "[", "]") ?: "null"

    /** 3 sn boyunca sinyalleri 250 ms'de bir okur; yalnızca değişimleri günlüğe yazar. */
    private fun watch(entry: WidgetWritePolicy.Entry, before: List<Pair<ComfortPlan.Signal, String>>) {
        val tracker = ComfortPlan.ChangeTracker()
        before.forEach { (s, v) -> tracker.observe(s.name, v) }
        val start = SystemClock.elapsedRealtime()
        var changes = 0
        while (true) {
            val t = SystemClock.elapsedRealtime() - start
            for (s in entry.readBack) {
                val v = describe(s)
                if (tracker.observe(s.name, v)) {
                    changes++
                    Log.i(WidgetWritePolicy.LOG_TAG, "widget=${entry.widget} readback +${t}ms ${s.name}=$v")
                }
            }
            if (t >= READBACK_MS) break
            SystemClock.sleep(READBACK_INTERVAL_MS)
        }
        if (changes == 0) Log.i(WidgetWritePolicy.LOG_TAG, "widget=${entry.widget} readback ${READBACK_MS}ms: no change")
    }
}
