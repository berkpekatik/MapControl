package com.mapcontrol.launcher

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout

/**
 * Spor modu geçişleri (Sahne düzeninde). Orta alan [SahneScreen.showSport] ile spor ekranına döner; üst çubuk
 * ([HeaderBar.setSport]), bizim alt çubuk ([OurBottomBar.setSport], OEM çubuğunda yok) ve kök zemin (karbon) aynı anda
 * değişir. Değişim, ekranı kısa süre karartan tam ekran örtünün altında yapılır (tek kare ağır iş görünmez).
 *
 * Biçim [SportTransition.choose] ile seçilir (6 Kasım kararı):
 *  - Araç duruyor: giriş (~2,2 sn) karartma (250 ms) · değişim · karartma kalkar, kadran çerçeveleri sırayla parlar, ibre
 *    ağırca sona süpürür, tepede bekler, gerçek hıza iner (hız ibresi; spor-modu-brief sonu "önce hız"), canlı değerler
 *    belirir. Çıkış (~1,5 sn, sakin): değerler söner, ibre iner, karartma (400 ms) · değişim · karartma yavaşça kalkar; parlama yok.
 *  - Araç gidiyor: karartma ve süpürme yok; ~500 ms çapraz geçiş (orta alan, kök zemin), hız ibresi doğrudan gerçek hızda,
 *    medya ve uyarı şeridi geçiş boyunca ekranda. Çıkış da aynı sakin geçiş; sürüş özeti gösterilmez.
 * Yalnız görünüm alfası/dönmesi (donanım katmanı). Animasyonlar kapalıysa (ANIMATOR_DURATION_SCALE 0) anında.
 */
internal class SportController(
    private val activity: Activity,
    private val sahne: () -> SahneScreen?,
    private val header: () -> HeaderBar,
    private val bar: () -> OurBottomBar?,
    private val root: () -> View?,
    /** Araç gidiyor mu (hız > 0); bilinmiyorsa null (duruyor sayılır). */
    private val moving: () -> Boolean? = { null },
    /** Vites P mi (ReadOnly 26); bilinmiyorsa null. P ise hız bilinmese de tam giriş (süpürme) seçilir. */
    private val inPark: () -> Boolean? = { null },
) {
    /** Ekranda spor görünümü var mı (değişim yapıldı). */
    var shown = false
        private set
    /** İstenen son durum; geçiş sürerken gelen istek geçiş bitince uygulanır. */
    private var target = false
    private var busy = false
    private var dim: View? = null
    private var savedGround: Drawable? = null
    /** Spor zemini (kendi bitmap başvurusunu tutar): yerinde tema yenilemesinde yeniden kullanılır, görsel ana iş
     *  parçacığında yeniden çözülmez. */
    private var ground: SportGround? = null
    /** Geçiş kuşağı: iptal edilen ya da yerine yenisi başlayan geçişin geç gelen geri çağrıları hiçbir şey yapmaz. */
    private var gen = 0

    /** Geçiş sürüyor (karartma altında): düzenlemeye girerken kesilir. */
    val transitioning: Boolean get() = busy
    /** Spor ekranının açıldığı an (monoton ms); özet kartı yalnız [MIN_STINT_MS]'den uzun spor açıklığında. */
    private var shownAt = 0L
    /** Analog saat seçimi değişince başlıktaki dijital saat. */
    private val dialsListener: () -> Unit = { if (shown) header().setSportClockHidden(SportDialPrefs.clockChosen(activity)) }

    init {
        SportDialPrefs.addListener(dialsListener)
    }

    fun request(on: Boolean) {
        target = on
        if (busy || on == shown) return
        if (on) enter() else exit()
    }

    /**
     * Araç Spor'a geçmek üzere (karar süzgeci sayıyor): durağan çizim şimdiden arka planda hazırlanır, geçiş beklemesin.
     * Mod geri dönerse ([idle]) hazırlanan görünüm bırakılır.
     */
    fun prepare() {
        if (shown || busy) return
        sahne()?.sportStage()?.prepare()
    }

    fun idle() {
        if (shown || busy || target) return
        sahne()?.dropSport()
    }

    /** Düzenleme/tema yenilemesi gibi durumlarda animasyonsuz uygular. */
    fun applyInstant(on: Boolean) {
        target = on
        cancelRunning()
        if (on == shown) return
        val s = sahne()
        if (!on) {
            swap(false)
            s?.dropSport()
            return
        }
        // Durağan çizim hazır olunca tek seferde (boş kadranlar görünmesin). Çizim hiç olmazsa Normal kalır.
        val stage = s?.sportStage() ?: return
        val my = gen
        stage.whenReady {
            if (my != gen || !target || shown || busy || sahne() !== s || s.sportStage() !== stage) return@whenReady
            if (swap(true)) stage.showInstant()
        }
    }

    /**
     * Sahne/alt çubuk yeniden kuruldu (tema yerinde yenilendi): süren geçiş kesilir ve istenen son durum ([target]) yeni
     * parçalara anında uygulanır. Başlık yeniden kurulmaz (kendi spor durumunu korur); kök zemin yeniden kurulduğu için
     * Normal zemin yeniden saklanır.
     */
    fun onRebuilt() {
        cancelRunning()
        if (!shown) {
            if (target) applyInstant(true)
            return
        }
        val r = root()
        if (!target) {
            // Çıkış sürerken yenilendi: yeni parçalar zaten Normal; yalnız başlık ve durum geri alınır.
            shown = false
            rememberedSport = false
            header().setSport(false)
            savedGround = null
            ground = null
            return
        }
        header().setSportClockHidden(SportDialPrefs.clockChosen(activity))
        r?.let {
            savedGround = it.background
            it.background = ground ?: SportGround(activity).also { g -> ground = g }
        }
        bar()?.setSport(true)
        val s = sahne() ?: return
        if (!s.showSport(true)) { applyInstant(false); return }
        val stage = s.sportStage()
        val my = gen
        stage.whenReady { if (my == gen && shown && sahne() === s) stage.showInstant() }
    }

    fun release() {
        cancelRunning()
        SportDialPrefs.removeListener(dialsListener)
    }

    private fun animationsOn(): Boolean = try {
        Settings.Global.getFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f && ValueAnimator.areAnimatorsEnabled()
    } catch (e: RuntimeException) {
        true
    }

    private fun kind(): SportTransition.Kind = SportTransition.choose(animationsOn(), moving(), inPark())

    private fun enter() {
        val s = sahne() ?: return
        when (kind()) {
            SportTransition.Kind.INSTANT -> { applyInstant(true); return }
            SportTransition.Kind.CALM -> { enterCalm(s); return }
            SportTransition.Kind.SWEEP -> {}
        }
        busy = true
        val my = ++gen
        val stage = s.sportStage()
        var dimmed = false
        var ready = false
        fun go() {
            if (my != gen || !dimmed || !ready || !busy) return
            // Örtünün altındaki an: sahne bu arada yenilendiyse ya da bırakıldıysa eski görünüm kullanılmaz.
            val now = sahne()
            if (now !== s || now.sportStage() !== stage || !swap(true)) { abortEnter(); return }
            stage.prepareIntro()
            stage.playIntro()
            fade(DIM_ALPHA, 0f, IN_REVEAL_MS, 0L) { if (my == gen) finish() }
        }
        stage.whenReady { ready = true; go() }
        // Durağan çizim gecikirse karartmada takılı kalınmaz: süre dolunca giriş bırakılır (Normal kalır). Çizim sonra
        // biterse ve Spor hâlâ isteniyorsa giriş yeniden denenir (bu kez çizim hazır, bekleme yok).
        stage.postDelayed({
            if (my != gen || ready) return@postDelayed
            // Yeniden deneme karartma kalktıktan (busy bitti) sonra kurulur; çizim solma sırasında biterse istek düşmesin.
            abortEnter { stage.whenReady { if (target && !shown && !busy && sahne() === s) request(true) } }
        }, READY_TIMEOUT_MS)
        fade(0f, DIM_ALPHA, IN_DIM_MS, 0L) { dimmed = true; go() }
    }

    /** Giderken giriş: durağan çizim hazır olunca karartmasız ~500 ms çapraz geçiş; hız ibresi gerçek hızda başlar. */
    private fun enterCalm(s: SahneScreen) {
        busy = true
        val my = ++gen
        val stage = s.sportStage()
        var done = false
        stage.whenReady {
            if (my != gen || done || !busy) return@whenReady
            done = true
            if (sahne() !== s || s.sportStage() !== stage || !swap(true, SportTransition.CALM_MS)) { busy = false; return@whenReady }
            stage.showInstant()
            stage.postDelayed({ if (my == gen) finish() }, SportTransition.CALM_MS)
        }
        // Çizim gecikirse Normal kalınır; çizim sonra biterse ve Spor hâlâ isteniyorsa yeniden denenir.
        stage.postDelayed({
            if (my != gen || done) return@postDelayed
            done = true
            busy = false
            stage.whenReady { if (target && !shown && !busy && sahne() === s) request(true) }
        }, READY_TIMEOUT_MS)
    }

    /** Giderken çıkış: ~500 ms çapraz geçiş, özet kartı yok. */
    private fun exitCalm(s: SahneScreen) {
        busy = true
        val my = ++gen
        swap(false, SportTransition.CALM_MS)
        s.postDelayed({
            if (my != gen) return@postDelayed
            sahne()?.dropSport()
            finish()
        }, SportTransition.CALM_MS + CALM_SETTLE_MS)
    }

    /** Giriş yapılamadı (çizim gecikti, düzenleme açıldı): karartma kalkar, Normal kalır. */
    private fun abortEnter(then: (() -> Unit)? = null) {
        val my = ++gen
        fade(dim?.alpha ?: 0f, 0f, IN_REVEAL_MS, 0L) {
            if (my != gen) return@fade
            removeDim()
            busy = false
            then?.invoke()
        }
    }

    private fun exit() {
        val s = sahne()
        if (s == null || !s.sportShown) { swap(false); return }
        // Sürüş özeti: spor ekranı bir süre açık kaldıysa ve araç duruyorsa çıkış bitince kısa süre gösterilir.
        val summarize = SportTransition.summaryOnExit(SystemClock.elapsedRealtime() - shownAt >= MIN_STINT_MS, moving())
        val k = kind()
        if (k == SportTransition.Kind.CALM) { exitCalm(s); return }
        if (k == SportTransition.Kind.INSTANT) {
            applyInstant(false)
            if (summarize) SportSummaryCard.show(activity, SportSummaryCard.current(activity))
            return
        }
        busy = true
        val my = ++gen
        val stage = s.sportStage()
        stage.playOutro()
        fade(0f, DIM_ALPHA, OUT_DIM_MS, OUT_DIM_DELAY_MS) {
            if (my != gen) return@fade
            swap(false)
            fade(DIM_ALPHA, 0f, OUT_REVEAL_MS, 0L) {
                if (my != gen) return@fade
                sahne()?.dropSport()
                finish()
                if (summarize && !shown) SportSummaryCard.show(activity, SportSummaryCard.current(activity))
            }
        }
    }

    private fun finish() {
        removeDim()
        busy = false
        if (target != shown) request(target)
    }

    /** Asıl değişim (örtünün altında, tek seferde). Orta alan spora geçemediyse (düzenleme) hiçbir şey değişmez: false. */
    private fun swap(on: Boolean, fadeMs: Long = 0L): Boolean {
        if (on) {
            val s = sahne() ?: return false
            if (!s.showSport(true, fadeMs)) return false
        } else {
            sahne()?.showSport(false, fadeMs)
        }
        shown = on
        rememberedSport = on
        if (on) shownAt = SystemClock.elapsedRealtime()
        header().setSport(on)
        header().setSportClockHidden(on && SportDialPrefs.clockChosen(activity))
        bar()?.setSport(on)
        if (!on) ground = null
        val r = root() ?: return true
        val from = r.background
        val to: Drawable?
        if (on) {
            savedGround = from
            to = SportGround(activity).also { ground = it }
        } else {
            to = savedGround
            savedGround = null
        }
        if (to == null) return true
        if (fadeMs > 0L && from != null) {
            // Kök zemin de çapraz geçer; bitince yalnız yeni zemin kalır (eski bitmap tutulmaz).
            val t = android.graphics.drawable.TransitionDrawable(arrayOf(from, to)).apply { isCrossFadeEnabled = true }
            r.background = t
            t.startTransition(fadeMs.toInt())
            r.postDelayed({ if (r.background === t) r.background = to }, fadeMs)
        } else {
            r.background = to
        }
        return true
    }

    private fun fade(from: Float, to: Float, ms: Long, delay: Long, end: () -> Unit) {
        val v = dim ?: addDim() ?: run { end(); return }
        v.animate().cancel()
        v.alpha = from
        v.animate().alpha(to).setStartDelay(delay).setDuration(ms).setInterpolator(DecelerateInterpolator()).withEndAction(end).start()
    }

    private fun addDim(): View? {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout ?: return null
        return View(activity).apply {
            setBackgroundColor(0xFF000000.toInt())
            alpha = 0f
            isClickable = true // geçiş sırasında dokunuşlar alttaki düğmelere gitmez
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            content.addView(this, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            dim = this
        }
    }

    private fun removeDim() {
        val v = dim ?: return
        v.animate().cancel()
        (v.parent as? ViewGroup)?.removeView(v)
        dim = null
    }

    private fun cancelRunning() {
        gen++
        if (!busy) { removeDim(); return }
        busy = false
        removeDim()
        if (!shown) sahne()?.dropSport()
    }

    companion object {
        const val DIM_ALPHA = 0.88f
        const val IN_DIM_MS = 250L
        const val IN_REVEAL_MS = 380L
        const val OUT_DIM_DELAY_MS = 350L
        const val OUT_DIM_MS = 420L
        const val OUT_REVEAL_MS = 650L
        const val READY_TIMEOUT_MS = 1_500L
        /** Sakin çıkışta çapraz geçiş bittikten sonra spor görünümü bırakılmadan önceki kısa pay. */
        const val CALM_SETTLE_MS = 50L
        /** Bundan kısa spor açıklığında çıkışta özet kartı gösterilmez (titreşim, deneme). */
        const val MIN_STINT_MS = 20_000L

        /** Etkinlik yeniden kurulursa (tema, tercih) spor ekranı geçişsiz geri gelsin diye. */
        @Volatile var rememberedSport = false
    }
}
