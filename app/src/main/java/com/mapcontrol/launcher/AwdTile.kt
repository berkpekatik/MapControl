package com.mapcontrol.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import com.mapcontrol.R
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 4x4 güç dağılımı: ön / arka aks payını gösterir. Yalnızca okur; araca hiçbir şey yazılmaz.
 *
 * Tiggo 7 (2026-10-30 yol kaydı) sinyalleri, ikisi de 2 bayt [hi, lo]:
 *  - 107 `ID_ESTIMATED_COUPLING_TORQUE`: arka aksa giden AWD kaplin torku. Rölantide/sabit hızda 0..11, ivmelenmede 100..665.
 *    Kayıtta ara sıra tam 1200 görüldü (sonraki örnekte 0'a düşüyor): doygunluk/ön dolum geçişi sayılır, GEÇERSİZ kabul edilir.
 *  - 106 `ID_MEAN_EFFECTIVE_TORQUE`: motor torku, ofsetli kodlama. Rölantide 2000±5, yük altında 2040..2287, motor freninde ~1964.
 *    Nm = ham − 2000: tepe değer 287, 1.6T motorun ~290 Nm'lik tepe torkuyla örtüşüyor (ofset büyük olasılıkla doğru).
 *
 * DOĞRULANMADI: kaplin torkunun ölçeği ve referansı. Nm × 1 varsayıldı; kaplin tork motordan büyük de çıkabiliyor (kayıtta 665 kaplin / 256 motor),
 * yani aynı kademede ölçülmüyor olabilir (vites çarpanı). Sabitler [AwdMath] içinde tek yerde; araçta göstergenin 4x4 animasyonuyla karşılaştırılıp ayarlanır.
 */
internal object AwdMath {
    const val ENGINE_ID = 106
    const val COUPLING_ID = 107

    /** Motor torku ofseti: Nm = (ham − ofset) × çarpan. Rölanti ~2000. */
    const val ENGINE_OFFSET = 2000
    const val ENGINE_FACTOR = 1.0

    /** Kaplin torku: Nm = ham × çarpan. */
    const val COUPLING_FACTOR = 1.0

    /** Bu ham değer ve üstü kaplin için doygunluk/geçiş değeridir, geçersiz sayılır. */
    const val COUPLING_SATURATED_RAW = 1200

    /** Yok/okunamıyor işareti (iki bayt da 0xFF). */
    const val INVALID_RAW = 0xFFFF

    /** Motor ham değeri için makul aralık (ofsetli kodlama: 2000 ± bin). */
    val ENGINE_RAW_RANGE = 1000..3000

    /** Bunun altındaki kaplin torku gürültüdür (rölantide 0..11 görüldü) ve 0 sayılır. */
    const val COUPLING_NOISE_NM = 15

    /** Motor torku, Nm; geçersizse null. Negatif değer (motor freni) korunur. */
    fun engineNm(raw: Int?): Int? {
        if (raw == null || raw == INVALID_RAW || raw !in ENGINE_RAW_RANGE) return null
        return ((raw - ENGINE_OFFSET) * ENGINE_FACTOR).roundToInt()
    }

    /** Arka aksa giden kaplin torku, Nm (>= 0); geçersiz ya da doygunsa null. */
    fun couplingNm(raw: Int?): Int? {
        if (raw == null || raw < 0 || raw == INVALID_RAW || raw >= COUPLING_SATURATED_RAW) return null
        return (raw * COUPLING_FACTOR).roundToInt()
    }

    /**
     * Arka aks payı, yüzde 0..100. Motor torku bilinmiyorsa ya da kaplin gürültü eşiğinin altındaysa 0 (ön çekiş).
     * Payda max(motor, kaplin): kaplin motordan büyük okunsa bile pay 100'ü aşmaz; sıfıra bölme yoktur.
     */
    fun rearPercent(engineNm: Int?, couplingNm: Int?): Int {
        val c = couplingNm ?: return 0
        val e = engineNm ?: return 0
        if (c < COUPLING_NOISE_NM) return 0
        val total = max(max(e, 0), c)
        if (total <= 0) return 0
        return (100.0 * c / total).roundToInt().coerceIn(0, 100)
    }

    /** Arka aks payı; motor torku ya da kaplin torku bilinmiyorsa null ("veri yok", ön çekiş değil). */
    fun rearPercentOrNull(engineNm: Int?, couplingNm: Int?): Int? =
        if (engineNm == null || couplingNm == null) null else rearPercent(engineNm, couplingNm)

    /** Ön aks payı: arka payın tamamlayanı. */
    fun frontPercent(rear: Int): Int = 100 - rear.coerceIn(0, 100)
}

/**
 * Okuma ve çizim temposu (saf karar, JVM testli). Araçta (2026-11-07 gfxinfo) kutu sürekli 20 fps çiziyor ve
 * her görünürlükte yeni bir yürütücüyle 250 ms'de bir okuyordu; değer çoğu zaman (park, sabit hız) hiç değişmiyor.
 *  - Okuma: pay son [FAST_WINDOW_POLLS] okumada değiştiyse [FAST_POLL_MS], yoksa [SLOW_POLL_MS] (park/sabit sürüş).
 *  - Akış noktaları: yalnızca pay son değiştikten sonra [FLOW_HOLD_MS] boyunca ve en çok ~10 fps ([FRAME_MS]) akar;
 *    sonra şema sabit kalır (aks kalınlığı ve parlaklığı payı göstermeye devam eder, "parça yanar").
 */
internal object AwdPacing {
    const val FAST_POLL_MS = 250L
    const val SLOW_POLL_MS = 1000L
    /** Son değişimden sonra bu kadar okuma boyunca hızlı tempoda kalınır (~1 sn). */
    const val FAST_WINDOW_POLLS = 4
    const val FRAME_MS = 100L // ~10 fps
    const val FLOW_HOLD_MS = 1500L

    /** Bir sonraki okumaya kadar bekleme; [unchangedPolls] pay değişmeden geçen ardışık okuma sayısı. */
    fun pollDelayMs(unchangedPolls: Int): Long =
        if (unchangedPolls < FAST_WINDOW_POLLS) FAST_POLL_MS else SLOW_POLL_MS

    /** Bir okumanın sonucu: ekrana yansıtılsın mı ve yeni değişmezlik sayacı. */
    data class Step(val bind: Boolean, val unchangedPolls: Int)

    /**
     * Okuma sonrası karar. [notBoundYet]: bu oturumda henüz ekrana yansıtılmadı; o zaman ilk geçerli okuma her zaman
     * yansır (gizle→göster sonrası eski değer kalmasın). Okuma yoksa (servis bağlı değil) sayaç artar; bağlantı
     * beklenirken de 1 sn tempoya düşülür.
     */
    fun step(notBoundYet: Boolean, read: Boolean, changed: Boolean, unchangedPolls: Int): Step = when {
        !read -> Step(false, unchangedPolls + 1)
        notBoundYet || changed -> Step(true, 0)
        else -> Step(false, unchangedPolls + 1)
    }

    /** Akan noktalar çizilsin mi (ve sonraki kare istensin mi). */
    fun animateFlow(running: Boolean, compact: Boolean, rearTarget: Float, nowMs: Long, lastChangeMs: Long): Boolean =
        running && !compact && rearTarget > 0f && nowMs - lastChangeMs in 0..FLOW_HOLD_MS
}

/**
 * Demo için yumuşak salınım: arka pay 0 → %40 → 0, 6 sn döngü. Dönen değer (motor Nm, kaplin Nm) gerçek yoldan geçer.
 */
internal object AwdDemo {
    const val PERIOD_MS = 6000L
    private const val ENGINE_NM = 200
    private const val PEAK_REAR = 0.4

    fun reading(nowMs: Long): Pair<Int, Int> {
        val x = (nowMs % PERIOD_MS).toDouble() / PERIOD_MS
        val tri = 1.0 - abs(2.0 * x - 1.0)
        return ENGINE_NM to (ENGINE_NM * PEAK_REAR * tri).roundToInt()
    }
}

/** Kutucuk: dolu boyutta (>= 4 sütun) üstten görünüm şeması + güç akışı, küçükte yalnızca ön/arka yüzde çubuğu. */
internal class AwdTile(private val context: Context) : SizedWidget, Themeable, StripSource {

    override var onStripChanged: (() -> Unit)? = null
    private var stripNow: StripState? = null
    override fun stripState(): StripState? = stripNow
    override fun stripTap(part: Int) { root.performClick() }

    /** Tema bağlı atamalar (kurucuda bir kez, [applyTheme]'de yeniden); bkz. [ThemeBindings]. */
    private val theme = ThemeBindings()

    private val main = Handler(Looper.getMainLooper())
    private val flow = AwdFlowView(context)
    private val title = text(context, LauncherTheme.LABEL_SP).apply { theme.bind { setTextColor(LauncherTheme.TEXT_SECONDARY) } }
    private val headIcon = icon(context, R.drawable.ic_mdi_car_cog, HEAD_ICON_DP).also { v -> theme.bind { v.setTint(LauncherTheme.TEXT_SECONDARY) } }
    private val status = text(context, STATUS_SP, medium = true).apply { theme.bind { setTextColor(LauncherTheme.TEXT_SECONDARY) } }
    private val split = text(context, LauncherTheme.LABEL_SP).apply { theme.bind { setTextColor(LauncherTheme.TEXT_SECONDARY) } }

    /** Üst satır, ortalı: ikon + "4x4" (ailenin etiket satırı gibi). */
    private val head = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        addView(headIcon)
        addView(gap(context, 10))
        addView(title)
    }

    private val root: LinearLayout = card(context, LinearLayout.VERTICAL, LauncherTheme.CARD_PAD_DP).apply {
        theme.bind { background = cardSurface(context) }
    }

    override val view: View get() = root

    /** Tema değişti: yüzey ve yazılar yeniden atanır, durum satırı [bind] ile yeni renkte; şema çizimde okur, yalnızca yeniden çizilir. */
    override fun applyTheme() {
        theme.reapply()
        bind(rear)
        flow.invalidate()
    }

    private var compact = false
    private var rear: Int? = null
    /**
     * Kutu başına tek yürütücü (her görünürlükte yenisi açılmaz). Gizliyken iş planlanmaz; çekirdek iş parçacığı
     * boşta [IDLE_THREAD_S] sn sonra kendiliğinden kapanır, [release] tamamen kapatır.
     */
    private val executor: ScheduledThreadPoolExecutor by lazy {
        ScheduledThreadPoolExecutor(1).apply {
            setKeepAliveTime(IDLE_THREAD_S, TimeUnit.SECONDS)
            allowCoreThreadTimeOut(true)
            removeOnCancelPolicy = true
        }
    }
    private var executorStarted = false
    private var released = false
    /** Ana iş parçacığında: okuma açık mı. */
    private var polling = false
    @Volatile private var pollFuture: ScheduledFuture<*>? = null
    /** Okuma oturumu: gizlenince artar, eski oturumun kendini yeniden planlaması durur. */
    @Volatile private var pollSession = 0

    /**
     * Yalnız yürütücü iş parçacığında: son okunan pay, kaç okumadır değişmediği ve son okunan oturum.
     * Oturum durumu burada tutulur (ana iş parçacığında sıfırlanmaz); eski oturumun geç kalan okuması yeni oturumu bozamaz.
     */
    private var lastPolled: Int? = null
    private var unchangedPolls = 0
    private var seenSession = -1
    private var boundSession = -1

    // Okuma kaçırılırsa son geçerli değer kısa süre tutulur; sürekli kaçırılırsa bilinmiyor (0) olur.
    private var heldEngine: Int? = null
    private var heldCoupling: Int? = null
    private var missEngine = 0
    private var missCoupling = 0

    init {
        title.setText(R.string.launcher_widget_awd)
        split.gravity = Gravity.CENTER_HORIZONTAL
        status.gravity = Gravity.CENTER_HORIZONTAL
        applySpan(Span(4, 4))
        bind(null)
    }

    /** 4x4 ve üstü şema; daha küçükte yalnızca çubuk. */
    override fun applySpan(span: Span) {
        compact = span.w < FULL_MIN_COLS || span.h < FULL_MIN_ROWS
        flow.compact = compact
        // Dizilim: etiket satırı ortada; büyükte şema ortada, durum ve pay altta; küçükte durum üstte, çubuk ortada, pay altta.
        val p = context.dp(if (compact) COMPACT_PAD_DP else LauncherTheme.CARD_PAD_DP)
        root.setPadding(p, p, p, p)
        root.removeAllViews()
        val match = LinearLayout.LayoutParams.MATCH_PARENT
        val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
        root.addView(head, LinearLayout.LayoutParams(match, wrap))
        val gapPx = context.dp(LauncherTheme.GAP_S_DP)
        if (compact) {
            root.addView(status, LinearLayout.LayoutParams(match, wrap).apply { topMargin = gapPx / 2 })
            root.addView(flow, LinearLayout.LayoutParams(match, 0, 1f).apply { topMargin = gapPx / 2; bottomMargin = gapPx / 2 })
            root.addView(split, LinearLayout.LayoutParams(match, wrap))
        } else {
            root.addView(flow, LinearLayout.LayoutParams(match, 0, 1f).apply { topMargin = gapPx; bottomMargin = gapPx })
            root.addView(status, LinearLayout.LayoutParams(match, wrap))
            root.addView(split, LinearLayout.LayoutParams(match, wrap))
        }
        split.textSize = LauncherTheme.LABEL_SP
        // Büyükte yüzdeler şemanın iki yanında çizilir; alttaki özet satırı yalnızca küçükte.
        split.visibility = if (compact) View.VISIBLE else View.GONE
        flow.requestLayout()
    }

    override fun onVisible() {
        flow.running = true
        startPolling()
    }

    override fun onHidden() {
        stopPolling()
        flow.running = false
    }

    override fun release() {
        onHidden()
        released = true
        if (executorStarted) executor.shutdownNow()
    }

    private fun startPolling() {
        if (released || polling) return
        polling = true
        executorStarted = true
        // Yeni oturumun ilk okuması her zaman ekrana yansır (poll içinde, AwdPacing.step).
        val session = ++pollSession
        pollFuture = executor.schedule({ poll(session) }, 0L, TimeUnit.MILLISECONDS)
    }

    private fun stopPolling() {
        polling = false
        pollSession++
        pollFuture?.cancel(false)
        pollFuture = null
    }

    /** Tek okuma; ardından değişime göre [AwdPacing.pollDelayMs] sonra kendini yeniden planlar. */
    private fun poll(session: Int) {
        if (session != pollSession) return
        // Hata döngüyü öldürmesin: her durumda yeniden planlanır.
        try {
            val reading = if (LauncherDemo.enabled) AwdDemo.reading(SystemClock.uptimeMillis()) else readCar()
            // Yeni oturum: hızlı tempoyla başla.
            if (session != seenSession) { seenSession = session; unchangedPolls = 0 }
            val pct = reading?.let { AwdMath.rearPercentOrNull(it.first, it.second) }
            val step = AwdPacing.step(session != boundSession, reading != null, pct != lastPolled, unchangedPolls)
            unchangedPolls = step.unchangedPolls
            if (step.bind) {
                boundSession = session
                lastPolled = pct
                main.post { if (session == pollSession) bind(pct) }
            }
        } catch (t: Throwable) {
            Log.w("AwdTile", "poll başarısız", t)
        }
        if (session != pollSession) return
        try {
            pollFuture = executor.schedule({ poll(session) }, AwdPacing.pollDelayMs(unchangedPolls), TimeUnit.MILLISECONDS)
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            // release() yürütücüyü kapattı.
        }
    }

    /** Ham 106/107'yi okur, çözer; geçersiz okumada son geçerli değeri kısa süre korur. Servis bağlı değilse null. */
    private fun readCar(): Pair<Int?, Int?>? {
        val proxy = CarInfoProxy.getInstance()
        if (!proxy.isServiceConnnected) {
            proxy.init(context.applicationContext)
            return null
        }
        val module = VDEventCarInfo.MODULE_READONLY_INFO
        val e = AwdMath.engineNm(CarSignals.readInt(module, AwdMath.ENGINE_ID))
        val c = AwdMath.couplingNm(CarSignals.readInt(module, AwdMath.COUPLING_ID))
        if (e != null) { heldEngine = e; missEngine = 0 } else if (++missEngine > MAX_MISS) heldEngine = null
        if (c != null) { heldCoupling = c; missCoupling = 0 } else if (++missCoupling > MAX_MISS) heldCoupling = null
        return heldEngine to heldCoupling
    }

    /** [rearPct] null: araç verisi yok; diğer kutular gibi "Araç verisi bekleniyor" ve "—" gösterilir (sahte ön çekiş değil). */
    private fun bind(rearPct: Int?) {
        rear = rearPct
        flow.hasData = rearPct != null
        if (rearPct == null) {
            flow.setRear(0)
            flow.labels = context.getString(R.string.launcher_awd_front_short) to context.getString(R.string.launcher_awd_rear_short)
            status.textSize = LauncherTheme.WAITING_SP
            status.typeface = launcherTypeface(context)
            status.setTextSmooth(context.getString(R.string.launcher_waiting_data), animate = false)
            status.setTextColor(LauncherTheme.TEXT_SECONDARY)
            val none = "—"
            split.setTextSmooth(none, animate = false)
            root.contentDescription = context.getString(R.string.launcher_waiting_data)
            stripNow = StripState(context.getString(R.string.launcher_widget_awd), none, R.drawable.ic_mdi_car_cog, StripTone.MUTED)
            onStripChanged?.invoke()
            return
        }
        status.textSize = STATUS_SP
        status.typeface = launcherTypeface(context, medium = true)
        flow.setRear(rearPct)
        flow.labels = context.getString(R.string.launcher_awd_front_short) to context.getString(R.string.launcher_awd_rear_short)
        val active = rearPct > 0
        status.setTextSmooth(context.getString(if (active) R.string.launcher_awd_active else R.string.launcher_awd_front), animate = false)
        status.setTextColor(if (active) LauncherTheme.ACCENT else LauncherTheme.TEXT)
        val front = AwdMath.frontPercent(rearPct)
        split.setTextSmooth(context.getString(R.string.launcher_awd_split, front, rearPct), animate = false)
        root.contentDescription = context.getString(R.string.launcher_awd_split, front, rearPct)
        stripNow = StripState(context.getString(R.string.launcher_widget_awd),
            context.getString(if (active) R.string.launcher_awd_active else R.string.launcher_awd_front), R.drawable.ic_mdi_car_cog,
            if (active) StripTone.ON else StripTone.NORMAL, detail = context.getString(R.string.launcher_awd_split, front, rearPct))
        onStripChanged?.invoke()
    }

    private companion object {
        const val HEAD_ICON_DP = 28
        const val STATUS_SP = 28f
        const val COMPACT_PAD_DP = 12
        const val FULL_MIN_COLS = 4
        const val FULL_MIN_ROWS = 4
        /** Ardışık kaçırma sınırı; hızlı tempoda ~2 sn, yavaşta ~8 sn son geçerli değer tutulur. */
        const val MAX_MISS = 8
        const val IDLE_THREAD_S = 5L
    }
}

/**
 * Üstten görünüm şeması ve güç akışı (burun yukarıda). Merkezden iki aksa, oradan tekerleklere çizgiler gider; kalınlık ve parlaklık
 * aksın payıyla ölçeklenir. Pay değişince 300 ms yumuşak geçer; ince noktalar arka pay > 0 iken yalnızca pay değiştikten sonra kısa süre
 * ~10 fps akar ([AwdPacing.animateFlow]); değer sabitken şema durağandır ve yeniden çizilmez.
 * [compact] iken şema yerine yatay ön/arka çubuğu çizilir (animasyon döngüsü yok).
 */
internal class AwdFlowView(context: Context) : View(context) {

    var compact = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** Ön / Arka kısa adları (yüzde etiketleri için); büyük şemada iki yanda çizilir. */
    var labels: Pair<String, String> = "" to ""
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        textSize = PCT_SP * context.resources.displayMetrics.scaledDensity
    }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = CAP_SP * context.resources.displayMetrics.scaledDensity }

    /** false iken (araç verisi yok) yüzdeler "—" çizilir ve hiçbir aks vurgulanmaz. */
    var hasData = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** Ekran görünürken true; false iken nokta animasyonu durur. */
    var running = false
        set(value) {
            if (field == value) return
            field = value
            if (value) invalidate() else animator.cancel()
        }

    private var target = 0f
    private var shown = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = SHARE_MS
        addUpdateListener {
            shown = from + (target - from) * (it.animatedValue as Float)
            invalidate()
        }
    }
    private var from = 0f
    /** Payın son değiştiği an (uptime); akış noktaları bundan sonra [AwdPacing.FLOW_HOLD_MS] boyunca akar. */
    private var lastChangeMs = Long.MIN_VALUE / 2

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Arka aks payı, yüzde 0..100. */
    fun setRear(percent: Int) {
        val next = percent.coerceIn(0, 100) / 100f
        if (next == target) return
        lastChangeMs = SystemClock.uptimeMillis()
        animator.cancel()
        from = shown
        target = next
        if (isShown && running) animator.start() else {
            shown = next
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        if (compact) drawBar(canvas) else drawChassis(canvas)
        // Nokta animasyonu: yalnızca görünürken, arka pay varken ve pay yakın zamanda değiştiyse; ~10 fps. Sabit değerde döngü yok.
        if (flowing()) postInvalidateDelayed(AwdPacing.FRAME_MS)
    }

    private fun flowing(): Boolean =
        AwdPacing.animateFlow(running, compact, target, SystemClock.uptimeMillis(), lastChangeMs)

    private fun drawBar(c: Canvas) {
        val h = min(height.toFloat(), context.dpF(BAR_H_DP))
        val top = (height - h) / 2f
        val r = h / 2f
        val rearW = width * shown
        // Zemin: ön aks (silik vurgu); üstüne arka payı (tam vurgu) sağdan.
        fill.color = if (hasData) withAlpha(LauncherTheme.ACCENT, 90) else LauncherTheme.TRACK
        rect.set(0f, top, width.toFloat(), top + h)
        c.drawRoundRect(rect, r, r, fill)
        if (hasData && rearW > 0f) {
            fill.color = LauncherTheme.ACCENT
            rect.set(max(0f, width - max(rearW, h)), top, width.toFloat(), top + h)
            c.drawRoundRect(rect, r, r, fill)
        }
    }

    private fun drawChassis(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val carH = h
        val carW = min(w * 0.8f, carH * 0.62f)
        val cx = w / 2f
        val bodyHalf = carW * 0.32f
        val wheelW = carW * 0.17f
        val wheelH = carW * 0.34f
        val frontY = carH * 0.24f
        val rearY = carH * 0.76f
        val hubY = carH * 0.5f
        val rearShare = if (hasData) shown else 0f
        val frontShare = if (hasData) 1f - shown else 0f

        // Gövde: yuvarlak köşeli gövde ve iki cam çizgisi (burun yukarıda).
        val bodyR = bodyHalf * 0.7f
        rect.set(cx - bodyHalf, carH * 0.03f, cx + bodyHalf, carH * 0.97f)
        fill.color = LauncherTheme.CONTROL
        c.drawRoundRect(rect, bodyR, bodyR, fill)
        line.color = LauncherTheme.TRACK
        line.strokeWidth = context.dpF(2f)
        val glassX = bodyHalf * 0.72f
        c.drawLine(cx - glassX, carH * 0.3f, cx + glassX, carH * 0.3f, line)
        c.drawLine(cx - glassX, carH * 0.7f, cx + glassX, carH * 0.7f, line)

        // Tekerlekler: payına göre dolgu parlaklığı.
        wheel(c, cx - bodyHalf - wheelW * 0.15f, frontY, wheelW, wheelH, frontShare)
        wheel(c, cx + bodyHalf + wheelW * 0.15f, frontY, wheelW, wheelH, frontShare)
        wheel(c, cx - bodyHalf - wheelW * 0.15f, rearY, wheelW, wheelH, rearShare)
        wheel(c, cx + bodyHalf + wheelW * 0.15f, rearY, wheelW, wheelH, rearShare)

        // Akış: merkez → aks ortası → iki tekerlek.
        val inner = bodyHalf + wheelW * 0.15f - wheelW / 2f
        axle(c, cx, hubY, frontY, inner, frontShare)
        axle(c, cx, hubY, rearY, inner, rearShare)

        // Yüzde etiketleri: ön aksın solunda, arka aksın sağında (köşegen denge); sığmazsa çizilmez.
        val gutter = carW * 0.43f + context.dpF(20f)
        if (cx - gutter > context.dpF(70f)) {
            val front = (frontShare * 100f).roundToInt()
            val rearPctV = (rearShare * 100f).roundToInt()
            sideLabel(c, cx - gutter, frontY, Paint.Align.RIGHT, labels.first, if (hasData) "%$front" else "—")
            sideLabel(c, cx + gutter, rearY, Paint.Align.LEFT, labels.second, if (hasData) "%$rearPctV" else "—")
        }

        // Merkez (şanzıman / transfer): vurgu noktası.
        fill.color = LauncherTheme.ACCENT
        c.drawCircle(cx, hubY, context.dpF(7f), fill)
    }

    /** Küçük ad üstte, büyük yüzde altta; ikisi [y] çevresinde dikey ortalı. */
    private fun sideLabel(c: Canvas, x: Float, y: Float, align: Paint.Align, name: String, pct: String) {
        pctPaint.textAlign = align
        capPaint.textAlign = align
        pctPaint.color = LauncherTheme.TEXT
        capPaint.color = LauncherTheme.TEXT_SECONDARY
        val pctH = -pctPaint.fontMetrics.ascent * 0.72f
        val capH = -capPaint.fontMetrics.ascent * 0.8f
        val total = capH + context.dpF(6f) + pctH
        val top = y - total / 2f
        c.drawText(name, x, top + capH, capPaint)
        c.drawText(pct, x, top + total, pctPaint)
    }

    private fun wheel(c: Canvas, x: Float, y: Float, w: Float, h: Float, share: Float) {
        rect.set(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f)
        fill.color = blend(LauncherTheme.TRACK, LauncherTheme.ACCENT, share)
        val r = w * 0.35f
        c.drawRoundRect(rect, r, r, fill)
    }

    /** Bir aksın akışı: merkezden aks ortasına dikey, oradan sol/sağ tekerleğe yatay; kalınlık ve parlaklık [share] ile. */
    private fun axle(c: Canvas, cx: Float, hubY: Float, axleY: Float, inner: Float, share: Float) {
        line.color = withAlpha(LauncherTheme.ACCENT, (MIN_ALPHA + (255 - MIN_ALPHA) * share).roundToInt())
        line.strokeWidth = context.dpF(MIN_STROKE_DP + (MAX_STROKE_DP - MIN_STROKE_DP) * share)
        c.drawLine(cx, hubY, cx, axleY, line)
        c.drawLine(cx, axleY, cx - inner, axleY, line)
        c.drawLine(cx, axleY, cx + inner, axleY, line)
        if (share <= 0.02f || !flowing()) return
        // Akan noktalar: merkezden tekerleğe doğru, yol boyunca eşit aralıklı, faza göre kayar.
        val seg1 = abs(axleY - hubY)
        val total = seg1 + inner
        val phase = (SystemClock.uptimeMillis() % FLOW_PERIOD_MS).toFloat() / FLOW_PERIOD_MS
        fill.color = withAlpha(LauncherTheme.ON_ACCENT, 200)
        val dotR = context.dpF(2.5f + 2f * share)
        for (i in 0 until DOTS) {
            val d = ((phase + i.toFloat() / DOTS) % 1f) * total
            if (d < seg1) {
                val y = hubY + (axleY - hubY) * (d / seg1)
                c.drawCircle(cx, y, dotR, fill)
            } else {
                val dx = d - seg1
                c.drawCircle(cx - dx, axleY, dotR, fill)
                c.drawCircle(cx + dx, axleY, dotR, fill)
            }
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun blend(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(shift: Int) = (((a ushr shift) and 0xFF) * (1f - k) + ((b ushr shift) and 0xFF) * k).roundToInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private companion object {
        const val SHARE_MS = 300L
        const val FLOW_PERIOD_MS = 1200L
        const val DOTS = 3
        const val MIN_ALPHA = 70
        const val MIN_STROKE_DP = 2f
        const val MAX_STROKE_DP = 9f
        const val BAR_H_DP = 22f
        const val PCT_SP = 32f
        const val CAP_SP = 20f
    }
}
