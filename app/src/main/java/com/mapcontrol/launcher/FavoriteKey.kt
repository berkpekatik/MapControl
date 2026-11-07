package com.mapcontrol.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import com.desaysv.ivi.vdb.IVDBusNotify
import com.desaysv.ivi.vdb.client.VDBus
import com.desaysv.ivi.vdb.client.bind.VDServiceDef
import com.desaysv.ivi.vdb.client.listener.VDBindListener
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.base.VDKey
import com.desaysv.ivi.vdb.event.id.sms.VDEventSms
import com.mapcontrol.R
import java.lang.ref.WeakReference

/**
 * Direksiyondaki favori (özel) tuşa basınca yapılacak iş. Yeni eylem eklemek için yalnızca buraya bir değer eklenir;
 * ayar satırı ve dinleyici bütün değerleri kendiliğinden kullanır. Anahtar tercihe yazılır, değiştirilmemeli.
 */
enum class FavoriteAction(val key: String, @StringRes val label: Int, val packageName: String?) {
    OFF("off", R.string.launcher_favorite_key_off, null),
    YANDEX_NAVI("yandex_navi", R.string.launcher_favorite_key_yandex_navi, "ru.yandex.yandexnavi"),
    YANDEX_MAPS("yandex_maps", R.string.launcher_favorite_key_yandex_maps, "ru.yandex.yandexmaps");

    companion object {
        /** Kayıtlı anahtar; bilinmeyen/boşsa Kapalı (yeni sürümden eski sürüme dönüşte de güvenli). */
        fun parse(value: String?): FavoriteAction =
            value?.trim()?.lowercase()?.let { v -> values().firstOrNull { it.key == v } } ?: OFF
    }
}

/**
 * Tuş olayından karara giden saf kural (JVM testli). OEM tuşu VDBus'tan (19, eylem) çiftleri olarak gelir:
 * kısa basış (19,0)(19,3), uzun basış (19,0)(19,1)(19,4). Yalnızca kısa bırakma (19,3) eylemi tetikler;
 * uzun basış şimdilik yalnızca günlüğe yazılır. Aynı basış iki kez gelirse [debounceMs] içinde ikincisi yok sayılır.
 */
class FavoriteKeyPolicy(private val debounceMs: Long = DEBOUNCE_MS) {

    enum class Decision { LAUNCH, LONG_PRESS, IGNORE }

    private var lastLaunchAt: Long? = null

    @Synchronized
    fun onKey(type: Int, action: Int, setting: FavoriteAction, nowMs: Long): Decision {
        if (type != KEY_TYPE_CUSTOM) return Decision.IGNORE
        return when (action) {
            ACTION_LONG_PRESS -> Decision.LONG_PRESS
            ACTION_SHORT_RELEASE -> {
                if (setting == FavoriteAction.OFF) return Decision.IGNORE
                val last = lastLaunchAt
                if (last != null && nowMs - last in 0 until debounceMs) return Decision.IGNORE
                lastLaunchAt = nowMs
                Decision.LAUNCH
            }
            else -> Decision.IGNORE
        }
    }

    companion object {
        /** VDKey.TYPE değeri: KEYCODE_CUSTOM (direksiyon favori tuşu). */
        const val KEY_TYPE_CUSTOM = 19
        const val ACTION_PRESS = 0
        const val ACTION_LONG_PRESS = 1
        const val ACTION_SHORT_RELEASE = 3
        const val ACTION_LONG_RELEASE = 4
        const val DEBOUNCE_MS = 800L
    }
}

/** "Favori tuşu" seçimi (profil başına, varsayılan Kapalı). */
object FavoriteKeyPrefs {
    private const val PREFS_NAME = "launcher_settings"
    private const val KEY = "favorite_key"

    fun get(context: Context): FavoriteAction =
        FavoriteAction.parse(context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(Profiles.key(context, KEY), null))

    fun set(context: Context, action: FavoriteAction) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(Profiles.key(context, KEY), action.key).apply()
    }
}

/**
 * Launcher'ın kendi favori tuşu dinleyicisi: VDBus SMS tuş olayına yalnızca tip {19} ile abone olur. FrostNav'ın
 * VDBusManager'ı ayrı callback'le kendi tuşlarına abone kalır; sunucu tip listesini callback başına tuttuğu için
 * ikisi birbirini ezmez. Bağlanma/yeniden abone olma mantığı VDBusManager'ın aynısı; araç uykusu tuş listesini
 * sessizce düşürebildiği için dinlerken 30 sn'de bir abonelik yenilenir.
 *
 * Tuş Android KeyEvent olarak gelmez. OEM basışta kendi işini de yapar (Settings.Global
 * com.desaysv.vehiclesetting.key_custom_button); onu kapatmak araçta elle yapılır, buradan yazılmaz.
 * Tüm VDBus çağrıları tek sıralı arka plan kuyruğunda; olaylar binder iş parçacığında gelir, uygulama açma ana
 * iş parçacığında yapılır.
 */
object FavoriteKey {
    private const val TAG = "FavoriteKey"
    private const val REFRESH_MS = 30_000L
    /** Bu kadar yakın zamanda abone olunduysa tazeleme atlanır (onCreate + onStart art arda gelir). */
    private const val RESUBSCRIBE_MIN_MS = 5_000L

    private val io = LauncherIo.serial()
    private val main = Handler(Looper.getMainLooper())
    private val policy = FavoriteKeyPolicy()

    private var appContext: Context? = null
    /** Dinleyiciyi son başlatan etkinlik; eski örneğin geç gelen onDestroy'u yenisinin dinleyicisini kapatmasın. */
    private var owner: WeakReference<Any>? = null
    private var bus: VDBus? = null
    private var event: VDEvent? = null
    private var subscribed = false
    private var lastSubscribeAt = 0L
    @Volatile private var listening = false
    @Volatile private var action = FavoriteAction.OFF

    private val notify = object : IVDBusNotify.Stub() {
        override fun onVDBusNotify(e: VDEvent?) {
            // Durdurulduktan sonra sunucuda/bağlayıcı tamponunda kalmış abonelik hiçbir şey açamaz.
            if (!listening) return
            if (e == null || e.id != VDEventSms.ID_SMS_KEY_EVENT) return
            val payload = e.payload ?: return
            val type = payload.getInt(VDKey.TYPE, -1)
            val act = payload.getInt(VDKey.ACTION, -1)
            Log.d(TAG, "tuş: type=$type action=$act")
            val chosen = action
            when (policy.onKey(type, act, chosen, SystemClock.uptimeMillis())) {
                FavoriteKeyPolicy.Decision.LAUNCH -> main.post { if (listening) perform(chosen) }
                FavoriteKeyPolicy.Decision.LONG_PRESS -> Log.i(TAG, "uzun basış (şimdilik eylem yok)")
                FavoriteKeyPolicy.Decision.IGNORE -> Unit
            }
        }
    }

    private val bindListener = object : VDBindListener {
        override fun onVDConnected(serviceType: VDServiceDef.ServiceType) {
            if (serviceType == VDServiceDef.ServiceType.SMS) io.execute { if (listening) subscribe() }
        }

        override fun onVDDisconnected(serviceType: VDServiceDef.ServiceType) {
            if (serviceType != VDServiceDef.ServiceType.SMS) return
            io.execute {
                Log.i(TAG, "SMS servisi koptu, yeniden bağlanılıyor")
                subscribed = false
                event = null
                if (listening) runCatching { bus?.bindService(VDServiceDef.ServiceType.SMS) }
                    .onFailure { Log.w(TAG, "bindService: ${it.message}") }
            }
        }
    }

    /** Ana iş parçacığında: dinliyorsa aboneliği tazeler; VDBus açılamamışsa (erken açılış) yeniden dener. */
    private val refreshTick = object : Runnable {
        override fun run() {
            if (listening) io.execute { if (listening) resubscribe(force = true) }
            else if (action != FavoriteAction.OFF) appContext?.let { connect(it) }
            main.postDelayed(this, REFRESH_MS)
        }
    }

    /** Ayarı okur: Kapalı değilse dinlemeye başlar (zaten dinliyorsa eylemi günceller), Kapalı ise bırakır. */
    fun sync(context: Context) {
        owner = WeakReference(context)
        val chosen = FavoriteKeyPrefs.get(context)
        action = chosen
        if (chosen == FavoriteAction.OFF) stopNow() else start(context.applicationContext)
    }

    /** Görünür olmadan da çalışır; ana ekrana dönüşte aboneliği tazelemek için (uyku sonrası düşmüş olabilir). */
    fun refresh() {
        io.execute { if (listening) resubscribe(force = false) }
    }

    /** Etkinlik kapanırken: yalnızca dinleyiciyi son başlatan örnek kapatabilir. */
    fun stop(caller: Any) {
        if (owner?.get() !== caller) return
        owner = null
        stopNow()
    }

    private fun stopNow() {
        action = FavoriteAction.OFF
        main.removeCallbacks(refreshTick)
        io.execute {
            if (!listening) return@execute
            listening = false
            unsubscribe()
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

    /**
     * Ana iş parçacığında çağrılır. VDBus init'i (VDRouter bağlayıcı listesini kilitsiz doldurur) VDBusManager gibi
     * ana iş parçacığında yapılır; başarısızsa dinleme açılmaz, 30 sn'lik tik yeniden dener.
     */
    private fun connect(context: Context) {
        val b = try {
            VDBus.getDefault().also { it.init(context) }
        } catch (t: Throwable) {
            Log.w(TAG, "VDBus başlatılamadı: ${t.message}")
            return
        }
        io.execute {
            if (listening) return@execute
            bus = b
            listening = true
            runCatching { b.registerVDBindListener(bindListener) }.onFailure { Log.w(TAG, "bindListener: ${it.message}") }
            if (b.isServiceConnected(VDServiceDef.ServiceType.SMS)) subscribe()
            else runCatching { b.bindService(VDServiceDef.ServiceType.SMS) }.onFailure { Log.w(TAG, "bindService: ${it.message}") }
            Log.i(TAG, "dinleme başladı: ${action.key}")
        }
    }

    // Aşağıdakiler yalnızca io kuyruğunda çalışır.

    private fun subscribe() {
        val b = bus ?: return
        if (subscribed) return
        try {
            // Aynı olay nesnesi yeniden kullanılır: bağlayıcı tamponu (olay, callback) çiftini tekrarlamaz.
            val e = event ?: VDEvent(VDEventSms.ID_SMS_KEY_EVENT, Bundle().apply {
                putIntArray(VDKey.TYPE, intArrayOf(FavoriteKeyPolicy.KEY_TYPE_CUSTOM))
            })
            b.subscribe(e, notify)
            event = e
            subscribed = true
            lastSubscribeAt = SystemClock.uptimeMillis()
            Log.d(TAG, "SMS tuş olayına abone olundu (tip 19)")
        } catch (t: Throwable) {
            Log.w(TAG, "subscribe: ${t.message}")
        }
    }

    private fun unsubscribe() {
        val e = event
        if (e != null) runCatching { bus?.unsubscribe(e, notify) }.onFailure { Log.d(TAG, "unsubscribe: ${it.message}") }
        subscribed = false
        event = null
    }

    /**
     * Önce çıkmadan yeniden abone olur: sunucu aynı callback'i yeniden kaydeder ve tip listesini üzerine yazar
     * (SMS denetleyicisi RemoteCallbackList + callback başına harita), böylece arada tuş kaçıracak boşluk olmaz.
     * [force] değilse az önce abone olunduysa hiç dokunmaz.
     */
    private fun resubscribe(force: Boolean) {
        val b = bus ?: return
        if (!force && subscribed && SystemClock.uptimeMillis() - lastSubscribeAt < RESUBSCRIBE_MIN_MS) return
        if (!b.isServiceConnected(VDServiceDef.ServiceType.SMS)) {
            runCatching { b.bindService(VDServiceDef.ServiceType.SMS) }.onFailure { Log.w(TAG, "bindService: ${it.message}") }
            return
        }
        subscribed = false
        subscribe()
    }

    /** Ana iş parçacığında: seçilen uygulamayı yeni görevde açar; kurulu değilse kısa bir uyarı. */
    private fun perform(chosen: FavoriteAction) {
        val ctx = appContext ?: return
        val pkg = chosen.packageName ?: return
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent == null) {
            Log.i(TAG, "$pkg kurulu değil")
            Toast.makeText(ctx, ctx.getString(R.string.launcher_favorite_key_missing, ctx.getString(chosen.label)), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            ctx.startActivity(intent)
            Log.i(TAG, "açıldı: $pkg")
        } catch (e: Exception) {
            Log.w(TAG, "$pkg açılamadı: ${e.message}")
        }
    }
}
