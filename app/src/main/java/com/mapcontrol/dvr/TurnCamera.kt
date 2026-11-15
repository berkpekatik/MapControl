package com.mapcontrol.dvr

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import com.mapcontrol.launcher.CarSignals
import com.mapcontrol.util.ClusterNavigationState
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Sinyal tarafı ve gösterilecek kamera ([CameraHub] sırası: 0 ön, 1 sağ, 2 sol, 3 arka; [HabNative.CAMERA_IDS]). */
enum class TurnSide(val cam: Int) { LEFT(2), RIGHT(1) }

/**
 * Sinyal kamerası kararı (saf, JVM'de sınanır). Ham değerler ReadOnly 35 (sol), 34 (sağ), 79 (dörtlü), 26 (vites);
 * hepsi araçta doğrulandı ([com.mapcontrol.launcher.CarLightSignals]): 1 = açık, R = 2.
 *  - Yalnızca tek taraf açıkken gösterilir; dörtlüde (ikisi de 1 ya da 79 = 1) gösterilmez.
 *  - Dörtlü açılış / kapanışında araç 35, 34 ve 79'u ayrı ayrı gönderir; arada birkaç ms "yalnız sol" görünür. Bu
 *    yüzden tek taraf [confirmMs] kararlı kalmadan gösterilmez ve dörtlü (79 = 1) görüldükten sonra [quietMs] boyunca
 *    hiç gösterilmez. Dörtlü açılınca gösterilen kart beklemeden kapanır.
 *  - İki taraf birden 1 ama 79 değil: soldan sağa hızlı geçişteki kısa örtüşme. Dörtlü sayılmaz (sayılsaydı yeni taraf
 *    [quietMs] geç açılıyordu); yalnızca [bothHazardMs] sürerse (79'u olmayan yazılımda dörtlü) dörtlü sayılır.
 *  - Sinyal kapanınca [holdMs] daha kalır: kol ortadan geçerken ya da tek okuma kaçınca kart titremesin.
 *  - Kart açıkken karşı tarafa geçişte de [confirmMs] beklenir (aynı yarışa karşı).
 *  - Kapalıyken ([active] false: anahtar kapalı ya da göstergeye yansıtma kapalı) ve geri viteste hemen kapanır
 *    (geri viteste OEM 360 ekranı devreye girer).
 */
class TurnCameraLogic(
    private val holdMs: Long = HOLD_MS,
    private val confirmMs: Long = CONFIRM_MS,
    private val quietMs: Long = HAZARD_QUIET_MS,
    private val bothHazardMs: Long = BOTH_AS_HAZARD_MS,
) {
    var shown: TurnSide? = null
        private set
    private var lastSeenAt = 0L
    private var candidate: TurnSide? = null
    private var candidateSince = 0L
    /** Dörtlü en son görüldüğü an; hiç görülmediyse null. */
    private var bothAt: Long? = null
    /** İki taraf birden (79 olmadan) ilk görüldüğü an; örtüşme bitince null. */
    private var bothSince: Long? = null

    /** Aday bekliyorsa onaylanacağı an (çağıran o anda bir daha [update] çağırmalı); yoksa null. */
    var confirmAt: Long? = null
        private set

    fun update(active: Boolean, left: Int?, right: Int?, hazard: Int?, gear: Int?, now: Long): TurnSide? {
        confirmAt = null
        if (!active || gear == REVERSE) {
            shown = null
            candidate = null
            return null
        }
        if (hazard == ON) {
            bothAt = now
            bothSince = null
            shown = null
            candidate = null
            return null
        }
        if (left == ON && right == ON) {
            // Soldan sağa hızlı geçişte araç önce yeni tarafı 1, ms'ler sonra eskisini 0 gönderir: kısa örtüşme dörtlü
            // değildir. Yalnızca [bothHazardMs] sürerse dörtlü sayılır (79'u olmayan yazılım). O ana dek açık kart kalır.
            val since = bothSince ?: now.also { bothSince = it }
            candidate = null
            if (now - since >= bothHazardMs) {
                bothAt = now
                shown = null
                return null
            }
            confirmAt = since + bothHazardMs
            return shown
        }
        bothSince = null
        val quiet = bothAt?.let { now - it < quietMs } == true
        val want = if (quiet) null else side(left, right, hazard)
        if (want == null) {
            candidate = null
            if (quiet) {
                shown = null
            } else if (shown != null && now - lastSeenAt >= holdMs) {
                shown = null
            }
            return shown
        }
        if (want == shown) {
            candidate = null
            lastSeenAt = now
            return shown
        }
        if (want != candidate) {
            candidate = want
            candidateSince = now
        }
        if (now - candidateSince >= confirmMs) {
            shown = want
            candidate = null
            lastSeenAt = now
        } else {
            confirmAt = candidateSince + confirmMs
            // Bekleyen aday varken açık kart kendi tarafında kalır (bekleme süresi aşılmadıkça).
            if (shown != null && now - lastSeenAt >= holdMs) shown = null
        }
        return shown
    }

    companion object {
        const val HOLD_MS = 1_500L
        const val CONFIRM_MS = 150L
        const val HAZARD_QUIET_MS = 1_500L
        /** 79 yokken iki taraf bu kadar birlikte açık kalırsa dörtlü sayılır (kol geçişindeki örtüşme çok daha kısa). */
        const val BOTH_AS_HAZARD_MS = 300L
        const val ON = 1
        const val REVERSE = 2

        fun side(left: Int?, right: Int?, hazard: Int?): TurnSide? = when {
            hazard == ON -> null
            left == ON && right != ON -> TurnSide.LEFT
            right == ON && left != ON -> TurnSide.RIGHT
            else -> null
        }
    }
}

/**
 * Kartın göstergedeki yeri ve boyutu (kullanıcı ayarı). Sol ve sağ ortaktır, sağ aynalanır: [edgePct] sol kartta sol
 * kenardan, sağ kartta sağ kenardan uzaklıktır.
 *  - [edgePct]: kenardan uzaklık, ekran genişliğinin yüzdesi (0..[EDGE_MAX]).
 *  - [posYPct]: dikey konum, 0 = en üst, 50 = tam orta, 100 = en alt (kartın sığdığı alan içinde).
 *  - [sizePct]: boyut, varsayılan kartın yüzdesi ([SIZE_MIN]..[SIZE_MAX]).
 */
data class TurnCamLayout(val edgePct: Int = EDGE_DEFAULT, val posYPct: Int = POS_Y_DEFAULT, val sizePct: Int = SIZE_DEFAULT) {

    /** Ekrandaki dikdörtgen: x, y (sol üst, piksel), genişlik, yükseklik. */
    data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

    /**
     * [screenW]x[screenH] göstergede [side] kartının yeri (saf). Kamera karesi 16:10, kart da öyle. Varsayılan boy
     * ekran genişliğinin %28'i (yüksekliğin %46'sını aşmaz); boyut ayarıyla ölçeklenir ama genişliğin %45'ini ve
     * yüksekliğin %90'ını geçmez. Kart ekrandan taşmaz.
     */
    fun rect(screenW: Int, screenH: Int, side: TurnSide): Rect {
        val base = minOf(screenW * BASE_W, screenH * BASE_H * ASPECT)
        val maxW = minOf(screenW * MAX_W, screenH * MAX_H * ASPECT)
        val w = (base * sizePct.coerceIn(SIZE_MIN, SIZE_MAX) / 100f).coerceAtMost(maxW).toInt().coerceAtLeast(1)
        val h = (w / ASPECT).toInt().coerceAtLeast(1)
        val edge = (screenW * edgePct.coerceIn(0, EDGE_MAX) / 100f).toInt().coerceIn(0, (screenW - w).coerceAtLeast(0))
        val x = if (side == TurnSide.LEFT) edge else screenW - w - edge
        val y = ((screenH - h).coerceAtLeast(0) * posYPct.coerceIn(0, 100) / 100f).toInt()
        return Rect(x, y, w, h)
    }

    companion object {
        const val ASPECT = 1.6f
        const val BASE_W = 0.28f
        const val BASE_H = 0.46f
        const val MAX_W = 0.45f
        const val MAX_H = 0.90f
        const val EDGE_DEFAULT = 8
        const val EDGE_MAX = 30
        const val POS_Y_DEFAULT = 50
        const val SIZE_DEFAULT = 100
        const val SIZE_MIN = 70
        const val SIZE_MAX = 150
    }
}

/**
 * Kamera 360 > "Sinyalde göstergede yan kamera". Anahtar açık ve göstergeye yansıtma açıkken
 * ([ClusterNavigationState], launcher'ın da okuduğu kayıt) sinyal kolunu [POLL_MS]'de bir okur; tek taraf
 * yandığı an o taraftaki kamerayı göstergenin üstünde gösterir ([TurnCameraOverlay]). Servisten başlar
 * (ana ekran açık olmasa da çalışır). Yalnızca okur, araca hiçbir şey yazmaz.
 */
object TurnCamera {
    private const val TAG = "TurnCamera"
    const val PREFS = "camera360"
    const val KEY_ENABLED = "turnCamCluster"
    /** Sinyal okuma aralığı: kolun açılışı en geç bu kadar gecikir (3-4 bağlayıcı çağrısı, ucuz). */
    const val POLL_MS = 200L

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "TurnCamera").apply { isDaemon = true } }
    private val logic = TurnCameraLogic()
    private var task: ScheduledFuture<*>? = null
    private var app: Context? = null
    private var overlay: TurnCameraOverlay? = null
    @Volatile private var enabled = false
    private var posted: TurnSide? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when (key) {
            KEY_ENABLED -> apply(p.getBoolean(KEY_ENABLED, false))
            KEY_EDGE, KEY_POS_Y, KEY_SIZE -> app?.let { c -> main.post { onLayoutChanged(c) } }
        }
    }

    // --- Kart yeri / boyutu ve ayar önizlemesi ---

    const val KEY_EDGE = "turnCamEdge"
    const val KEY_POS_Y = "turnCamPosY"
    const val KEY_SIZE = "turnCamSize"
    /** Son ayar değişikliğinden bu kadar sonra önizleme kalkar. */
    const val PREVIEW_HOLD_MS = 2_500L

    fun layout(context: Context): TurnCamLayout {
        val p = prefs(context)
        return TurnCamLayout(
            p.getInt(KEY_EDGE, TurnCamLayout.EDGE_DEFAULT),
            p.getInt(KEY_POS_Y, TurnCamLayout.POS_Y_DEFAULT),
            p.getInt(KEY_SIZE, TurnCamLayout.SIZE_DEFAULT),
        )
    }

    fun setLayout(context: Context, l: TurnCamLayout) {
        prefs(context).edit().putInt(KEY_EDGE, l.edgePct).putInt(KEY_POS_Y, l.posYPct).putInt(KEY_SIZE, l.sizePct).apply()
    }

    /** Ayar önizlemesi sürüyor: gerçek sinyal kamerası gösterilmez (kullanıcı ayarlarken sinyal açılmasın). */
    @Volatile private var previewing = false
    private var previewCards: Pair<TurnCameraOverlay, TurnCameraOverlay>? = null
    private val previewEnd = Runnable { stopPreview() }

    /**
     * Ayar kaydırılıyor (ana iş parçacığı): gösterge ekranı açıksa (yansıtma açık) iki tarafa demo kart çizilir; kamera
     * açılmaz. Her çağrı süreyi tazeler, son değişiklikten [PREVIEW_HOLD_MS] sonra kalkar. Göstergeye çizilemediyse false.
     */
    fun preview(context: Context): Boolean {
        val c = context.applicationContext
        main.removeCallbacks(previewEnd)
        if (!ClusterNavigationState.getLastKnownOpen()) {
            stopPreview()
            return false
        }
        if (!previewing) {
            previewing = true
            // Açık gerçek kartı kapat; önizleme bitince sinyal yeniden değerlendirilir.
            posted = null
            overlay?.hide()
        }
        val cards = previewCards ?: Pair(TurnCameraOverlay(c, demo = true), TurnCameraOverlay(c, demo = true)).also { previewCards = it }
        val ok = cards.first.show(TurnSide.LEFT) and cards.second.show(TurnSide.RIGHT)
        main.postDelayed(previewEnd, PREVIEW_HOLD_MS)
        return ok
    }

    fun stopPreview() {
        main.removeCallbacks(previewEnd)
        previewCards?.let { it.first.hide(); it.second.hide() }
        previewCards = null
        previewing = false
    }

    private fun onLayoutChanged(c: Context) {
        overlay?.relayout()
        previewCards?.let { it.first.relayout(); it.second.relayout() }
    }

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    /** Kamera 360 > "Düz görünüm": yan kameranın balık gözü kısmen düzeltilir. */
    const val KEY_FLAT = "turnCamFlat"

    /**
     * Düzeltme gücü (0 ham, 1 tam). Lens değerleri ([CameraGlEngine] uFocal / uThetaMax) bu kameralarda doğrulanmadı;
     * tam düzeltme kenarları (şerit, kaldırım) fazla gerebilir, o yüzden kısmi. Araçta bakılıp ayarlanacak.
     */
    const val FLAT_STRENGTH = 0.55f

    fun isFlat(context: Context): Boolean = prefs(context).getBoolean(KEY_FLAT, false)

    fun setFlat(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_FLAT, on).apply()
    }

    /** Motora verilecek düzeltme değeri. */
    fun dewarp(context: Context): Float = if (isFlat(context)) FLAT_STRENGTH else 0f

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    /** Servis onCreate (ana iş parçacığı). */
    fun start(context: Context) {
        if (app != null) return
        val c = context.applicationContext
        app = c
        prefs(c).registerOnSharedPreferenceChangeListener(prefListener)
        runCatching { CarInfoProxy.getInstance().regServiceConnectListener(connectListener) }
        apply(prefs(c).getBoolean(KEY_ENABLED, false))
    }

    /** Araç servisi yeniden bağlandı: abonelik düşmüş olabilir, sonraki yoklamada yeniden açılır. */
    private val connectListener = com.desaysv.ivi.extra.project.carinfo.proxy.IServiceConnectListener { state ->
        if (state == 1) io.execute { subscribed = false }
    }

    /** Servis onDestroy (ana iş parçacığı). */
    fun stop() {
        val c = app ?: return
        prefs(c).unregisterOnSharedPreferenceChangeListener(prefListener)
        runCatching { CarInfoProxy.getInstance().unRegServiceConnectListener(connectListener) }
        apply(false)
        app = null
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun apply(on: Boolean) {
        enabled = on
        synchronized(this) {
            if (on && task == null) {
                task = io.scheduleWithFixedDelay({ poll() }, 0, POLL_MS, TimeUnit.MILLISECONDS)
            } else if (!on) {
                task?.cancel(false)
                task = null
                io.execute {
                    unsubscribe()
                    setWarm(false)
                    logic.update(false, null, null, null, null, 0L)
                }
            }
        }
        if (!on) post(null)
    }

    // --- Okuma: anlık bildirim (VDBus aboneliği) + yedek yoklama. Değerler yalnızca io iş parçacığında. ---

    private val PUSH_IDS = intArrayOf(
        ReadOnlyID.ID_DIRECTION_IND_LEFT, ReadOnlyID.ID_DIRECTION_IND_RIGHT,
        ReadOnlyID.ID_HAZARD_LIGHT, ReadOnlyID.ID_GEARBOX_STATE,
    )
    private var left: Int? = null
    private var right: Int? = null
    private var hazard: Int? = null
    private var gear: Int? = null
    private var subscribed = false
    private val pushSeen = HashSet<Int>()

    /**
     * Sinyal kolu değişince araç bildirim gönderirse (ReadOnly aboneliği; kontak bu yolla ~100 ms'de geliyor) kart
     * yoklamayı beklemeden hemen açılır. Bildirim gelmeyen yazılımda [POLL_MS]'lik yoklama yine yakalar.
     */
    private val notify = object : com.desaysv.ivi.vdb.IVDBusNotify.Stub() {
        override fun onVDBusNotify(event: com.desaysv.ivi.vdb.event.VDEvent?) {
            if (event == null || event.id != RO) return
            val payload = event.payload ?: return
            val id = payload.getInt(com.desaysv.ivi.extra.project.carinfo.proxy.Constants.CMD_ID, -1)
            val v = payload.getIntArray(com.desaysv.ivi.extra.project.carinfo.proxy.Constants.VALUE) ?: return
            if (v.isEmpty() || id !in PUSH_IDS) return
            val value = if (v.size == 1) v[0] else v.fold(0) { acc, b -> (acc shl 8) or (b and 0xFF) }
            io.execute { onPush(id, value) }
        }
    }

    private fun onPush(id: Int, value: Int) {
        if (pushSeen.add(id)) Log.i(TAG, "bildirim geliyor: ReadOnly $id = $value")
        when (id) {
            ReadOnlyID.ID_DIRECTION_IND_LEFT -> left = value
            ReadOnlyID.ID_DIRECTION_IND_RIGHT -> right = value
            ReadOnlyID.ID_HAZARD_LIGHT -> hazard = value
            ReadOnlyID.ID_GEARBOX_STATE -> gear = value
        }
        evaluate()
    }

    private fun poll() {
        // Yakalanmayan hata scheduleWithFixedDelay'i sessizce iptal eder.
        try {
            val armed = enabled && ClusterNavigationState.getLastKnownOpen() && connected()
            setWarm(armed)
            if (armed) {
                fun ro(id: Int) = CarSignals.readInt(RO, id)
                left = ro(ReadOnlyID.ID_DIRECTION_IND_LEFT)
                right = ro(ReadOnlyID.ID_DIRECTION_IND_RIGHT)
                hazard = ro(ReadOnlyID.ID_HAZARD_LIGHT)
                gear = ro(ReadOnlyID.ID_GEARBOX_STATE)
            }
            evaluate()
        } catch (t: Throwable) {
            Log.w(TAG, "poll başarısız", t)
        }
    }

    /**
     * Kameraları hazır tut: anahtar ve yansıtma açıkken akışlar kopyasız açık kalır ([CameraHub]: kare çekilip kopyasız
     * iade edilir, ~sıfır CPU). Yoksa ilk sinyalde dört kamera sıfırdan açılıyor (saniyeler) ve kart çoğu zaman
     * "Kamera açılıyor…" ile kapanıyordu. Kart açılınca yalnızca o kameranın kopyası istenir.
     */
    private var warm = false

    private fun setWarm(on: Boolean) {
        if (on == warm) return
        warm = on
        if (on) CameraHub.get().acquire(WARM_TAG, null) else CameraHub.get().release(WARM_TAG)
        Log.i(TAG, if (on) "kameralar hazırda tutuluyor" else "kameralar bırakıldı")
    }

    private const val WARM_TAG = "turn-cam-warm"

    private fun evaluate() {
        // Ayar önizlemesi sürerken gerçek kart açılmaz.
        val active = enabled && !previewing && ClusterNavigationState.getLastKnownOpen() && CarInfoProxy.getInstance().isServiceConnnected
        val now = SystemClock.elapsedRealtime()
        post(logic.update(active, left, right, hazard, gear, now))
        // Tek taraf onay bekliyor: süre dolunca yoklamayı beklemeden yeniden değerlendir.
        logic.confirmAt?.let { at ->
            if (confirmTask?.isDone != false) {
                confirmTask = io.schedule({ runCatching { evaluate() } }, (at - now).coerceAtLeast(1L), TimeUnit.MILLISECONDS)
            }
        }
    }

    private var confirmTask: ScheduledFuture<*>? = null

    /** Bağlantıyı kurar ve aboneliği bir kez açar (servis yeniden bağlanırsa [subscribed] sıfırlanmaz: VDBus kendisi tazeler). */
    private fun connected(): Boolean {
        val proxy = CarInfoProxy.getInstance()
        if (!proxy.isServiceConnnected) {
            app?.let { proxy.init(it) }
            return false
        }
        if (!subscribed) {
            try {
                proxy.regCallBack(RO, notify, PUSH_IDS)
                subscribed = true
                Log.i(TAG, "sinyal aboneliği açıldı")
            } catch (t: Throwable) {
                Log.w(TAG, "sinyal aboneliği açılamadı: ${t.javaClass.simpleName}")
            }
        }
        return true
    }

    private fun unsubscribe() {
        if (!subscribed) return
        runCatching { CarInfoProxy.getInstance().unRegCallBack(RO, notify) }
        subscribed = false
        left = null; right = null; hazard = null; gear = null
    }

    private fun post(side: TurnSide?) {
        main.post {
            if (side == posted) return@post
            Log.i(TAG, "sinyal kamerası: $posted -> $side")
            posted = side
            val c = app
            if (side == null || c == null) {
                overlay?.hide()
            } else {
                (overlay ?: TurnCameraOverlay(c).also { overlay = it }).show(side)
            }
        }
    }

    private val RO = VDEventCarInfo.MODULE_READONLY_INFO
}
