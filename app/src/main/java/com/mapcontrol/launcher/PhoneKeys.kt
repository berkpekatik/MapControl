package com.mapcontrol.launcher

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.desaysv.ivi.vdb.IVDBusNotify
import com.desaysv.ivi.vdb.client.VDBus
import com.desaysv.ivi.vdb.client.bind.VDServiceDef
import com.desaysv.ivi.vdb.client.listener.VDBindListener
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.base.VDKey
import com.desaysv.ivi.vdb.event.id.phonelink.VDEventPhoneLink
import com.desaysv.ivi.vdb.event.id.sms.VDEventSms
import java.lang.ref.WeakReference

/**
 * Direksiyon ses (VR) tuşundan karara giden saf kural (JVM testli). Tuş VDBus'tan (6, eylem) çiftleri olarak gelir
 * (SMS denetleyicisi): kısa basış (6,0)(6,3), uzun basış (6,0)(6,1)(6,4). Uzun basış
 * eşiği 1200 ms (araç Android Auto sertifikalıysa 600 ms); "süper uzun" (2) bu kodda hiç gönderilmez.
 *
 * - Uzun basış (6,1) ve ayar açık → [Decision.ASSISTANT] (PhoneSource aç/kapa yapar: asistan açıksa kapatır).
 * - Projeksiyon (CarPlay/Android Auto...) bağlıyken her şey yok sayılır: OEM projeksiyon uygulaması basılı tutulan
 *   VR tuşunu zaten telefona (Siri/Google) iletir; bizim BVRA'mız ikinci tetik olurdu.
 * - Bizim BVRA oturumumuz açıkken kısa basış (6,3) → [Decision.STOP_ASSISTANT]: OEM kendi asistanını (iFlytek/
 *   AiSpeech) yalnızca kısa basışta uyandırır ve HFP BVRA'yı izlemez; mikrofon iki asistana bölünmesin.
 * - Diğer her şey (0, 3, 4) yok sayılır. [debounceMs] içinde ikinci uzun basış yok sayılır.
 */
class VoiceKeyPolicy(private val debounceMs: Long = DEBOUNCE_MS) {

    enum class Decision { ASSISTANT, STOP_ASSISTANT, IGNORE }

    private var lastAssistantAt: Long? = null

    @Synchronized
    fun onKey(
        type: Int, action: Int, enabled: Boolean, nowMs: Long,
        projection: Boolean = false, assistantActive: Boolean = false,
    ): Decision {
        if (type != KEY_TYPE_VR || !enabled || projection) return Decision.IGNORE
        if (action == ACTION_SHORT_RELEASE && assistantActive) return Decision.STOP_ASSISTANT
        if (action != ACTION_LONG_PRESS) return Decision.IGNORE
        val last = lastAssistantAt
        if (last != null && nowMs - last in 0 until debounceMs) return Decision.IGNORE
        lastAssistantAt = nowMs
        return Decision.ASSISTANT
    }

    companion object {
        /** VDKey.TYPE değeri: direksiyon ses komutu (VR) tuşu (VDValueKey.KEYCODE_VR). */
        const val KEY_TYPE_VR = 6
        const val ACTION_PRESS = 0
        const val ACTION_LONG_PRESS = 1
        const val ACTION_SHORT_RELEASE = 3
        const val ACTION_LONG_RELEASE = 4
        const val DEBOUNCE_MS = 1500L
    }
}

/**
 * Ses tuşu uzun basış → bağlı telefonun sesli asistanı (Siri / Google, HFP BVRA). [FavoriteKey] ile aynı yapı: VDBus
 * SMS tuş olayına kendi callback'iyle yalnızca tip {6} için abone olur; sunucu tip listesini callback başına tuttuğu
 * için FavoriteKey'in {19} aboneliğiyle çakışmaz. Araç uykusu aboneliği düşürebildiği için dinlerken 30 sn'de bir
 * yenilenir (FavoriteKey'in de yaptığı tek zamanlayıcı).
 *
 * Abonelik tuşu yutmaz, OEM aboneleri de görür. Yazılım incelemesi: OEM ses servisi (com.desaysv.vrservice)
 * uzun basışta (1) yalnızca kendi uyanmasını bastıran bir bayrak kurar, asistanı yalnızca kısa basışta (3) açar;
 * yani projeksiyon yokken çift eylem yok. Projeksiyon varken OEM CarPlay/Android Auto uygulaması tuşu telefona
 * iletir: bunun için PHONELINK PHONE_STATE (851970) izlenir ve bağlıyken tuş yok sayılır. Projeksiyonun ses durumu
 * (VOICE_STATE 851971, "enable") da açıksa yok sayılır.
 */
internal object PhoneKeys {
    private const val TAG = "PhoneKeys"
    private const val REFRESH_MS = 30_000L
    private const val RESUBSCRIBE_MIN_MS = 5_000L

    private val io = LauncherIo.serial()
    private val main = Handler(Looper.getMainLooper())
    private val policy = VoiceKeyPolicy()

    private var appContext: Context? = null
    private var owner: WeakReference<Any>? = null
    private var bus: VDBus? = null
    private var event: VDEvent? = null
    private var linkEvents: List<VDEvent> = emptyList()
    private var subscribed = false
    /** PHONELINK: projeksiyon bağlı / projeksiyon sesli asistanı açık (binder ya da io iş parçacığından yazılır). */
    @Volatile private var projectionConnected = false
    @Volatile private var projectionVoice = false
    private var lastSubscribeAt = 0L
    @Volatile private var listening = false
    @Volatile private var enabled = false

    private val notify = object : IVDBusNotify.Stub() {
        override fun onVDBusNotify(e: VDEvent?) {
            if (!listening || e == null) return
            when (e.id) {
                VDEventSms.ID_SMS_KEY_EVENT -> onKey(e)
                VDEventPhoneLink.PHONE_STATE, VDEventPhoneLink.VOICE_STATE -> onLink(e)
            }
        }
    }

    private fun onKey(e: VDEvent) {
        val payload = e.payload ?: return
        val type = payload.getInt(VDKey.TYPE, -1)
        val act = payload.getInt(VDKey.ACTION, -1)
        val projection = projectionConnected || projectionVoice
        when (policy.onKey(type, act, enabled, SystemClock.uptimeMillis(), projection, PhoneSource.voiceAssistantActive())) {
            VoiceKeyPolicy.Decision.ASSISTANT -> {
                Log.i(TAG, "ses tuşu uzun basış → asistan")
                main.post { if (listening) PhoneSource.startVoiceAssistant() }
            }
            VoiceKeyPolicy.Decision.STOP_ASSISTANT -> {
                Log.i(TAG, "ses tuşu kısa basış → telefon asistanı kapatılıyor (OEM asistanı açılıyor)")
                main.post { if (listening) PhoneSource.stopVoiceAssistant() }
            }
            VoiceKeyPolicy.Decision.IGNORE ->
                if (projection && type == VoiceKeyPolicy.KEY_TYPE_VR && act == VoiceKeyPolicy.ACTION_LONG_PRESS) {
                    Log.i(TAG, "ses tuşu uzun basış: projeksiyon bağlı, tuş telefona OEM yoluyla gidiyor")
                }
        }
    }

    /** PHONELINK yayını ya da getOnce yanıtı: projeksiyon bağlantısı ve ses durumu. */
    private fun onLink(e: VDEvent) {
        val p = e.payload ?: return
        when (e.id) {
            VDEventPhoneLink.PHONE_STATE -> {
                if (!p.containsKey(VDKey.STATUS)) return
                val on = ProjectionLink.active(p.getInt(VDKey.STATUS, -1), p.getInt(VDKey.TYPE, -1))
                if (on != projectionConnected) Log.i(TAG, "projeksiyon: ${if (on) "bağlı" else "yok"}")
                projectionConnected = on
                if (!on) projectionVoice = false
            }
            VDEventPhoneLink.VOICE_STATE -> if (p.containsKey(VDKey.ENABLE)) projectionVoice = p.getBoolean(VDKey.ENABLE, false)
        }
    }

    private val bindListener = object : VDBindListener {
        override fun onVDConnected(serviceType: VDServiceDef.ServiceType) {
            if (serviceType == VDServiceDef.ServiceType.SMS) io.execute { if (listening) subscribe() }
            if (serviceType == VDServiceDef.ServiceType.PHONELINK) io.execute { if (listening) subscribeLink() }
        }

        override fun onVDDisconnected(serviceType: VDServiceDef.ServiceType) {
            if (serviceType == VDServiceDef.ServiceType.PHONELINK) {
                io.execute {
                    unsubscribeLink()
                    projectionConnected = false
                    projectionVoice = false
                    if (listening) runCatching { bus?.bindService(VDServiceDef.ServiceType.PHONELINK) }
                }
                return
            }
            if (serviceType != VDServiceDef.ServiceType.SMS) return
            io.execute {
                subscribed = false
                event = null
                if (listening) runCatching { bus?.bindService(VDServiceDef.ServiceType.SMS) }
                    .onFailure { Log.w(TAG, "bindService: ${it.javaClass.simpleName}") }
            }
        }
    }

    /**
     * 30 sn'lik yenileme yalnızca ayar açıkken döner (kapalıyken kendini yeniden kurmaz) ve yalnızca io'da aboneliği
     * tazeler (tuş arka planda da çalışmalı). Bağlanamadıysa ana iş parçacığındaki VDBus init'i burada denenmez:
     * yeniden deneme ana ekrana dönüşte ([refresh]) yapılır.
     */
    private val refreshTick = object : Runnable {
        override fun run() {
            if (!enabled) return
            io.execute { if (listening) resubscribe(force = true) }
            main.postDelayed(this, REFRESH_MS)
        }
    }

    /** Ayarı okur: açıksa dinler, kapalıysa bırakır. Ana iş parçacığında. */
    fun sync(context: Context) {
        owner = WeakReference(context)
        val on = PhonePrefs.assistantOnVoiceKey(context)
        enabled = on
        if (on) start(context.applicationContext) else stopNow()
    }

    fun refresh() {
        if (enabled && !listening) appContext?.let { connect(it) }
        io.execute { if (listening) resubscribe(force = false) }
    }

    fun stop(caller: Any) {
        if (owner?.get() !== caller) return
        owner = null
        stopNow()
    }

    private fun stopNow() {
        enabled = false
        main.removeCallbacks(refreshTick)
        io.execute {
            if (!listening) return@execute
            listening = false
            unsubscribe()
            unsubscribeLink()
            runCatching { bus?.unregisterVDBindListener(bindListener) }
            Log.i(TAG, "dinleme durdu")
        }
    }

    private fun start(context: Context) {
        appContext = context
        main.removeCallbacks(refreshTick)
        main.postDelayed(refreshTick, REFRESH_MS)
        connect(context)
    }

    /** Ana iş parçacığında: VDBus init'i ana iş parçacığında (FavoriteKey gibi); başarısızsa ana ekrana dönüşte yeniden denenir. */
    private fun connect(context: Context) {
        val b = try {
            VDBus.getDefault().also { it.init(context) }
        } catch (t: Throwable) {
            Log.w(TAG, "VDBus başlatılamadı: ${t.javaClass.simpleName}")
            return
        }
        io.execute {
            if (listening) return@execute
            bus = b
            listening = true
            runCatching { b.registerVDBindListener(bindListener) }.onFailure { Log.w(TAG, "bindListener: ${it.javaClass.simpleName}") }
            if (b.isServiceConnected(VDServiceDef.ServiceType.SMS)) subscribe()
            else runCatching { b.bindService(VDServiceDef.ServiceType.SMS) }.onFailure { Log.w(TAG, "bindService: ${it.javaClass.simpleName}") }
            if (b.isServiceConnected(VDServiceDef.ServiceType.PHONELINK)) subscribeLink()
            else runCatching { b.bindService(VDServiceDef.ServiceType.PHONELINK) }.onFailure { Log.w(TAG, "bindService link: ${it.javaClass.simpleName}") }
            Log.i(TAG, "dinleme başladı")
        }
    }

    // Aşağıdakiler yalnızca io kuyruğunda.

    private fun subscribe() {
        val b = bus ?: return
        if (subscribed) return
        try {
            val e = event ?: VDEvent(VDEventSms.ID_SMS_KEY_EVENT, Bundle().apply {
                putIntArray(VDKey.TYPE, intArrayOf(VoiceKeyPolicy.KEY_TYPE_VR))
            })
            b.subscribe(e, notify)
            event = e
            subscribed = true
            lastSubscribeAt = SystemClock.uptimeMillis()
            Log.d(TAG, "SMS tuş olayına abone olundu (tip 6)")
        } catch (t: Throwable) {
            Log.w(TAG, "subscribe: ${t.javaClass.simpleName}")
        }
    }

    /**
     * PHONELINK PHONE_STATE/VOICE_STATE aboneliği ve şu anki değer (servis ilk değeri hemen döner). Servis
     * yoksa (projeksiyonsuz araç) sessizce atlanır; o zaman projeksiyon "yok" sayılır.
     */
    private fun subscribeLink() {
        val b = bus ?: return
        if (linkEvents.isNotEmpty()) return
        try {
            val list = listOf(
                VDEvent(VDEventPhoneLink.PHONE_STATE, Bundle()),
                VDEvent(VDEventPhoneLink.VOICE_STATE, Bundle()),
            )
            for (e in list) b.subscribe(e, notify)
            linkEvents = list
            for (id in intArrayOf(VDEventPhoneLink.PHONE_STATE, VDEventPhoneLink.VOICE_STATE)) {
                runCatching { b.getOnce(id) }.getOrNull()?.let { onLink(it) }
            }
            Log.d(TAG, "PHONELINK durumuna abone olundu")
        } catch (t: Throwable) {
            Log.w(TAG, "subscribe link: ${t.javaClass.simpleName}")
        }
    }

    private fun unsubscribeLink() {
        for (e in linkEvents) runCatching { bus?.unsubscribe(e, notify) }
        linkEvents = emptyList()
    }

    private fun unsubscribe() {
        val e = event
        if (e != null) runCatching { bus?.unsubscribe(e, notify) }
        subscribed = false
        event = null
    }

    private fun resubscribe(force: Boolean) {
        val b = bus ?: return
        if (!force && subscribed && SystemClock.uptimeMillis() - lastSubscribeAt < RESUBSCRIBE_MIN_MS) return
        if (b.isServiceConnected(VDServiceDef.ServiceType.PHONELINK)) {
            unsubscribeLink()
            subscribeLink()
        } else {
            runCatching { b.bindService(VDServiceDef.ServiceType.PHONELINK) }
        }
        if (!b.isServiceConnected(VDServiceDef.ServiceType.SMS)) {
            runCatching { b.bindService(VDServiceDef.ServiceType.SMS) }
            return
        }
        subscribed = false
        subscribe()
    }
}
