package com.mapcontrol.launcher

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.mapcontrol.vehicle.VehicleMetricsSnapshot

/**
 * Karşılama kararı (§7.4; saf, JVM'de sınanır): kontak her açıldığında bir kez. Güç modu açık (1 ya da 2) görülünce ve
 * ekran hazırken (etkinlik önde, düzenleme / sayfa yok) [update] bir kez true döner; güç kapalı (0) görülünce yeni döngü
 * başlar. Bilinmeyen güç (servis yok, emülatör) hiçbir şey yapmaz. Spor açıkken o döngünün karşılaması atlanır.
 */
internal class WelcomeGate {
    var played = false
        private set

    fun update(powerOn: Boolean?, ready: Boolean, sportActive: Boolean): Boolean {
        if (powerOn == false) {
            played = false
            return false
        }
        if (powerOn != true || played || !ready) return false
        played = true
        return !sportActive
    }

    /** Demo tekrarı: bu döngüde yeniden oynatılabilir. */
    fun replay() {
        played = false
    }

    companion object {
        /** Ham güç modu → açık / kapalı / bilinmiyor (MapControl web sunucusuyla aynı yorum: 1 ve 2 açık, 0 kapalı). */
        fun powerOn(snap: VehicleMetricsSnapshot): Boolean? {
            if (!snap.connected || !VehicleMetricsSnapshot.isValid(snap.powerMode)) return null
            return when (snap.powerMode) {
                1, 2 -> true
                0 -> false
                else -> null
            }
        }
    }
}

/** Süreç boyunca tek karar: tema geçişiyle yeniden kurulan etkinlik karşılamayı tekrar oynatmaz. */
internal object WelcomeCycle {
    val gate = WelcomeGate()
    /** Soğuk açılışta ekran yalnızca bir kez siyah tutulur (tema geçişleri yeniden beklemez). */
    var armedOnce = false
}

/**
 * Sahne karşılaması (§7.4, ~2 sn): ekran siyah başlar; araç karanlıktan belirir (alfa + hafif ölçek), farlar ve stoplar
 * yanar ([SahneStage.welcomePlay]), zemin ışığı açılır, sonra zemin ve geri kalan arayüz ([others]: başlık, yan paneller,
 * şerit, alt çubuk) belirir. Yalnızca görünüm alfası / ölçeği donanım katmanında canlanır; kare başına bellek ayırma yok.
 * [prepare] ekranı siyah tutar (soğuk açılışta güç sinyali beklenirken), [play] oynatır, [reveal] oynatmadan kısa
 * solmayla açar, [finish] her şeyi son haline çeker.
 */
internal class SahneWelcome(private val root: View, private val stage: SahneStage, private val others: List<View>) {

    var prepared = false
        private set
    var running = false
        private set
    private var ground: Drawable? = null
    private var fade: TransitionDrawable? = null
    private val groundIn = Runnable { fade?.startTransition(GROUND_FADE_MS) }
    private val done = Runnable { finish() }

    /** Ekranı siyah tutar (araç, zemin ve arayüz görünmez); animasyonlar kapalıysa hiçbir şey yapmaz. */
    fun prepare() {
        if (prepared || !ValueAnimator.areAnimatorsEnabled()) return
        prepared = true
        val g = root.background
        ground = g
        fade = TransitionDrawable(arrayOf(ColorDrawable(Color.BLACK), g ?: ColorDrawable(Color.TRANSPARENT))).apply {
            isCrossFadeEnabled = true
        }
        root.background = fade
        others.forEach { it.animate().cancel(); it.alpha = 0f }
        stage.welcomePrepare()
    }

    /** Karşılamayı oynatır (gerekirse önce hazırlar); animasyonlar kapalıysa anında son hal. */
    fun play() {
        if (running) return
        if (!ValueAnimator.areAnimatorsEnabled()) {
            finish()
            return
        }
        prepare()
        running = true
        stage.welcomePlay()
        root.postDelayed(groundIn, GROUND_DELAY_MS)
        others.forEach {
            it.animate().alpha(1f).setStartDelay(UI_DELAY_MS).setDuration(UI_FADE_MS)
                .setInterpolator(EASE).withLayer().start()
        }
        root.postDelayed(done, TOTAL_MS)
    }

    /** Oynatmadan açar (güç sinyali gelmedi): kısa solma. */
    fun reveal() {
        if (!prepared || running) return
        running = true
        fade?.startTransition(REVEAL_MS.toInt())
        others.forEach { it.animate().alpha(1f).setStartDelay(0).setDuration(REVEAL_MS).withLayer().start() }
        stage.welcomeReveal(REVEAL_MS)
        root.postDelayed(done, REVEAL_MS + 40)
    }

    /** Hemen son hal (iptal, etkinlik arka plana geçti, spor açıldı); hazırlık yoksa hiçbir şey yapmaz. */
    fun finish() {
        if (!prepared && !running) return
        root.removeCallbacks(groundIn)
        root.removeCallbacks(done)
        others.forEach { it.animate().cancel(); it.alpha = 1f }
        stage.welcomeEnd()
        if (root.background === fade) root.background = ground
        fade = null
        ground = null
        prepared = false
        running = false
    }

    private companion object {
        val EASE = DecelerateInterpolator(1.4f)
        const val GROUND_DELAY_MS = 650L
        const val GROUND_FADE_MS = 900
        const val UI_DELAY_MS = 1_150L
        const val UI_FADE_MS = 650L
        const val TOTAL_MS = 2_100L
        const val REVEAL_MS = 260L
    }
}
