package com.mapcontrol.launcher

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Telefon önizlemesi (yalnızca [LauncherDemo] açıkken). Gerçek kişi yok: sahte isimler ve 0555 000 00 xx numaraları.
 *
 *   adb shell am start -n <paket>/com.mapcontrol.launcher.HomeLauncherActivity \
 *     --ez launcher_demo true --ez launcher_demo_phone true --es launcher_demo_call incoming
 *
 * `launcher_demo_call` = incoming | active | none; verilmesi `launcher_demo_phone true` demektir.
 * Demo hiçbir zaman gerçek arama yapmaz.
 */
internal object PhoneDemo {
    const val EXTRA_PHONE = "launcher_demo_phone"
    const val EXTRA_CALL = "launcher_demo_call"

    const val DEVICE_NAME = "Demo iPhone"

    @Volatile
    var active = false

    @Volatile
    var call: CallKind = CallKind.NONE

    /** İstekteki telefon demo ekstralarını uygular (LauncherDemo.applyPreview çağırır). */
    fun apply(intent: Intent) {
        if (intent.hasExtra(EXTRA_PHONE)) active = intent.getBooleanExtra(EXTRA_PHONE, false)
        if (intent.hasExtra(EXTRA_CALL)) {
            active = true
            call = parseCall(intent.getStringExtra(EXTRA_CALL))
        }
        if (!active) call = CallKind.NONE
    }

    fun parseCall(raw: String?): CallKind = when (raw?.trim()?.lowercase()) {
        "incoming" -> CallKind.INCOMING
        "active" -> CallKind.ACTIVE
        else -> CallKind.NONE
    }

    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN

    val contacts: List<PhoneContact> = listOf(
        PhoneContact("d:1", "Ayşe", "0555 000 00 01", starred = true),
        PhoneContact("d:2", "Mehmet", "0555 000 00 02", starred = true),
        PhoneContact("d:3", "Ev", "0555 000 00 03", starred = true),
        PhoneContact("d:4", "Annem", "0555 000 00 04"),
        PhoneContact("d:5", "İş", "0555 000 00 05"),
        PhoneContact("d:6", "Zeynep Kaya", "0555 000 00 06"),
    )

    fun recents(nowMs: Long): List<PhoneRecent> = listOf(
        PhoneRecent(RecentType.MISSED, "Mehmet", "0555 000 00 02", nowMs - 12 * MIN),
        PhoneRecent(RecentType.OUTGOING, "Ev", "0555 000 00 03", nowMs - 2 * HOUR),
        PhoneRecent(RecentType.INCOMING, null, "0555 000 00 99", nowMs - 5 * HOUR),
        PhoneRecent(RecentType.OUTGOING, "Ayşe", "0555 000 00 01", nowMs - 26 * HOUR),
        PhoneRecent(RecentType.MISSED, "Zeynep Kaya", "0555 000 00 06", nowMs - 3 * 24 * HOUR),
    )

    /** Demo gelen/aktif aramanın arayanı. */
    val caller: PhoneContact get() = contacts[0]
}

/** Demo arka ucu: arama, cevap, kapatma, sessiz ve asistan yalnızca ekrandaki durumu değiştirir. */
internal class DemoBackend : CallBackend, DataBackend, AssistantBackend {
    override val name: String = PhoneBackendName.DEMO
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var onState: ((RawCall) -> Unit)? = null
    private var pending: Runnable? = null

    override fun available(): Boolean = LauncherDemo.enabled && PhoneDemo.active

    override fun dial(number: String): Boolean {
        val who = PhoneDemo.contacts.firstOrNull {
            FavoritesRanker.normalizeNumber(it.number) == FavoritesRanker.normalizeNumber(number)
        }
        state(CallKind.DIALING, who?.name, number)
        val r = Runnable { state(CallKind.ACTIVE, null, null) }
        pending?.let { main.removeCallbacks(it) }
        pending = r
        main.postDelayed(r, 2_000L)
        return true
    }

    override fun answer(): Boolean {
        cancelPending()
        if (PhoneDemo.call == CallKind.INCOMING) PhoneDemo.call = CallKind.ACTIVE
        state(CallKind.ACTIVE, null, null)
        return true
    }

    override fun reject(): Boolean = end()

    override fun hangUp(): Boolean = end()

    private fun end(): Boolean {
        cancelPending()
        PhoneDemo.call = CallKind.NONE
        state(CallKind.ENDED, null, null)
        return true
    }

    override fun setMute(on: Boolean): Boolean = true

    override fun setAudioToPhone(toPhone: Boolean): Boolean = true

    override fun sendDtmf(c: Char): Boolean = DtmfCode.vdNumber(c) != null

    override fun open(onState: (RawCall) -> Unit, onConnected: (Boolean, String?) -> Unit) {
        this.onState = onState
        onConnected(true, PhoneDemo.DEVICE_NAME)
    }

    override fun close() {
        cancelPending()
        onState = null
    }

    override fun read(pinnedIds: List<String>): PhoneData =
        PhoneData(PhoneDemo.contacts, PhoneDemo.recents(System.currentTimeMillis()))

    override fun start(done: (AssistantResult) -> Unit) {
        Log.i("PhoneSource", "demo asistan")
        done(AssistantResult.STARTED)
    }

    private fun cancelPending() {
        pending?.let { main.removeCallbacks(it) }
        pending = null
    }

    private fun state(kind: CallKind, name: String?, number: String?) {
        onState?.invoke(RawCall(kind, name, number))
    }
}
