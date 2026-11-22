package com.mapcontrol.dvr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.mapcontrol.R
import com.mapcontrol.launcher.CarAnimSpec
import com.mapcontrol.util.AppLaunchHelper
import com.mapcontrol.util.DiagJournal

/**
 * Göstergede (cluster) sinyal kamerası kartı ([TurnCamera]). Yeri ve boyutu kullanıcı ayarıdır ([TurnCamLayout]; sol ve
 * sağ ortak, sağ aynalanır). Koyu, yuvarlak köşeli kart; görüntü GPU motorunun ikinci çıkışından
 * ([CameraGlEngine.setAuxOutput]) gelir. Motor her zaman sabit [BUF_W]x[BUF_H] yüzeye çizer, kart büyüyüp küçülünce
 * yalnızca ekranda ölçeklenir: boyut ayarı çizim ve kopya maliyetini değiştirmez.
 * Üstte karartma şeridinde sinyal yönündeki kehribar ok ve "SOL / SAĞ KAMERA" etiketi, sinyal tarafındaki kenarda
 * ince kehribar şerit; ikisi de aracın sinyal ritmiyle ([CarAnimSpec.BLINK_MS]) iki durumlu yanıp söner. Kart ilk gerçek
 * kare gelince girer (titreme yok); kamera gelmezse "Kamera açılıyor…" ile. Dokunulmaz, odak almaz.
 *
 * [demo]: ayar önizlemesi. Kamera yok (motora dokunmaz), içerikte "ÖNİZLEME" yazar, hemen görünür.
 * Yalnızca ana iş parçacığında kullanılır.
 */
internal class TurnCameraOverlay(private val app: Context, private val demo: Boolean = false) {

    private val engine = CameraGlEngine.get()
    private var wm: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private var root: FrameLayout? = null
    private var card: FrameLayout? = null
    private var header: LinearLayout? = null
    private var label: TextView? = null
    private var arrow: TurnArrowView? = null
    private var accent: View? = null
    private var placeholder: TextView? = null
    private var surface: Surface? = null
    private var screenW = 1
    private var screenH = 1
    private var side: TurnSide? = null
    /** Taraf değişimi için kısa sönüş sürüyor; bitince bu tarafa geçilir. */
    private var pendingSide: TurnSide? = null
    private var hiding = false
    /** Kart kapanışı EGL bırakılmasını bekliyor; bu sırada pencere hâlâ durur. */
    private var closing = false
    private var closeGen = 0
    /** Kart yerinde ama ilk gerçek kare bekleniyor (görünmez); kare gelince ya da [REVEAL_FALLBACK_MS] sonra girer. */
    private var revealPending = false
    /** Bu kartta kamera görüntüsü en az bir kez geldi (bekleme yazısı kalktı). */
    private var live = false
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val revealFallback = Runnable { if (revealPending) reveal() }

    /**
     * Akış: kart hedef yere görünmez yerleşir, motor o kamerayı çizmeye başlar, ilk gerçek kare yüzeye düşünce
     * ([TextureView.SurfaceTextureListener.onSurfaceTextureUpdated]) kart içeri süzülür. Böylece boş / gri ara kare
     * hiç görünmez. Kamera [REVEAL_FALLBACK_MS] içinde gelmezse kart "Kamera açılıyor…" ile girer.
     * Göstergeye pencere eklenemediyse false.
     */
    fun show(next: TurnSide): Boolean {
        if (closing) {
            // Kapanış henüz pencereyi kaldırmadı: EGL bırakma isteğini geçersiz kıl, akışı geri bağla.
            closeGen++
            closing = false
            val s = side
            val surf = surface
            if (!demo && s != null && surf != null) {
                engine.setAuxOutput(surf, BUF_W, BUF_H, s.cam, TurnCamera.dewarp(app))
            }
        }
        if (root == null && !build()) return false
        val c = card ?: return false
        if (pendingSide != null) {
            // Sönüş sürüyor: hedefi güncelle, bitince oraya geçilir.
            pendingSide = next
            return true
        }
        if (next != side) {
            if (side != null && !hiding && c.alpha > PENDING_ALPHA) {
                // Açık kart karşı tarafa geçiyor: kısa sönüş, sonra yeni yerde yeni kamera.
                pendingSide = next
                c.animate().cancel()
                c.animate().alpha(PENDING_ALPHA).setDuration(SWITCH_OUT_MS).setInterpolator(EASE_IN)
                    .withEndAction {
                        val target = pendingSide ?: return@withEndAction
                        pendingSide = null
                        switchTo(target)
                    }.start()
            } else {
                switchTo(next)
            }
        } else if (hiding) {
            // Kapanırken sinyal geri geldi: kare zaten akıyor, hemen geri gir.
            hiding = false
            reveal()
        }
        startBlink()
        return true
    }

    fun hide() {
        val c = card ?: return
        if (hiding) return
        hiding = true
        pendingSide = null
        revealPending = false
        main.removeCallbacks(revealFallback)
        c.animate().cancel()
        if (c.alpha <= PENDING_ALPHA) {
            // Hiç görünmedi (kamera gelmeden sinyal bitti): beklemeden kaldır.
            removeWindow()
            return
        }
        c.animate().alpha(0f).translationX(edgeOffset(side, dp(16f))).scaleX(0.98f).scaleY(0.98f)
            .setDuration(EXIT_MS).setInterpolator(EASE_IN)
            .withEndAction { if (hiding) removeWindow() }
            .start()
    }

    /** Yer / boyut ayarı değişti: açık kart yeni yerine geçer (animasyonsuz, ayarlarken anında görünsün). */
    fun relayout() {
        side?.let { place(it) }
    }

    /** Kartı [s] tarafına görünmez yerleştirir, kamerayı değiştirir ve ilk kareyi bekler (önizlemede hemen girer). */
    private fun switchTo(s: TurnSide) {
        val c = card ?: return
        side = s
        hiding = false
        bindSide(s)
        place(s)
        c.animate().cancel()
        c.alpha = PENDING_ALPHA
        c.translationX = edgeOffset(s, dp(28f))
        c.scaleX = 0.96f
        c.scaleY = 0.96f
        if (demo) {
            reveal()
            return
        }
        surface?.let { engine.setAuxOutput(it, BUF_W, BUF_H, s.cam, TurnCamera.dewarp(app)) }
        revealPending = true
        main.removeCallbacks(revealFallback)
        main.postDelayed(revealFallback, REVEAL_FALLBACK_MS)
    }

    private fun reveal() {
        val c = card ?: return
        revealPending = false
        main.removeCallbacks(revealFallback)
        c.animate().cancel()
        c.animate().alpha(1f).translationX(0f).scaleX(1f).scaleY(1f)
            .setDuration(ENTER_MS).setInterpolator(EASE_OUT).start()
    }

    /** Kenara doğru kayma: sol kart sola, sağ kart sağa. */
    private fun edgeOffset(s: TurnSide?, d: Float): Float = if (s == TurnSide.RIGHT) d else -d

    /** Pencereyi ve kartı ayara göre [s] tarafındaki yerine ve boyutuna koyar ([TurnCamLayout.rect]; sağ aynalanır). */
    private fun place(s: TurnSide) {
        val p = params ?: return
        val r = root ?: return
        val rect = TurnCamera.layout(app).rect(screenW, screenH, s)
        p.gravity = Gravity.TOP or Gravity.LEFT
        p.x = rect.x
        p.y = rect.y
        p.width = rect.w
        p.height = rect.h
        card?.let { c ->
            val lp = c.layoutParams as FrameLayout.LayoutParams
            if (lp.width != rect.w || lp.height != rect.h) {
                lp.width = rect.w
                lp.height = rect.h
                c.layoutParams = lp
            }
            applyRadius(c, rect.h)
        }
        try {
            wm?.updateViewLayout(r, p)
        } catch (e: RuntimeException) {
            Log.w(TAG, "gösterge penceresi taşınamadı", e)
            DiagJournal.event("sinyal", "gösterge penceresi taşınamadı: $e")
        }
    }

    /** Köşe yarıçapı kart yüksekliğiyle orantılı (küçük kartta köşe kaba durmasın). */
    private var radius = 0f
    private var cardBg: GradientDrawable? = null
    private var ringBg: GradientDrawable? = null

    private fun applyRadius(c: View, h: Int) {
        val r = minOf(dp(22f), h * 0.07f)
        if (r == radius) return
        radius = r
        cardBg?.cornerRadius = r
        ringBg?.cornerRadius = r
        c.invalidateOutline()
    }

    // --- Kurulum ---

    private fun build(): Boolean {
        val dm = app.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return false
        val id = AppLaunchHelper.getClusterDisplayId(app)
        val display = dm.displays.firstOrNull { it.displayId == id }
        if (display == null || display.displayId == Display.DEFAULT_DISPLAY) {
            Log.w(TAG, "gösterge ekranı yok (id=$id)")
            DiagJournal.event("sinyal", "gösterge ekranı yok (id=$id)")
            return false
        }
        val ctx = app.createDisplayContext(display)
        val windowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        density = ctx.resources.displayMetrics.density
        screenW = ctx.resources.displayMetrics.widthPixels
        screenH = ctx.resources.displayMetrics.heightPixels
        val start = TurnCamera.layout(app).rect(screenW, screenH, TurnSide.LEFT)

        val bgDrawable = if (demo) {
            // Önizleme: kamera yok; yol hissi veren koyu degrade.
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF1A2230.toInt(), 0xFF0B0E13.toInt()))
        } else {
            GradientDrawable().apply { setColor(0xFF0B0E13.toInt()) }
        }
        cardBg = bgDrawable
        val cardView = FrameLayout(ctx).apply {
            background = bgDrawable
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
            clipToOutline = true
            // Tam 0 olursa TextureView çizilmez: yüzey kurulmaz, kare bildirimi gelmez. Göze görünmeyecek kadar soluk.
            alpha = PENDING_ALPHA
        }

        if (!demo) {
            val texture = TextureView(ctx).apply { surfaceTextureListener = textureListener }
            cardView.addView(texture, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }

        val wait = TextView(ctx).apply {
            if (demo) {
                setText(R.string.camera360_turn_cam_preview)
                setTextColor(0x66FFFFFF)
                textSize = 18f
                letterSpacing = 0.2f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            } else {
                setText(R.string.camera360_turn_cam_starting)
                setTextColor(0x99FFFFFF.toInt())
                textSize = 15f
            }
            gravity = Gravity.CENTER
        }
        cardView.addView(wait, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Üst karartma: etiket her görüntüde okunsun.
        val scrim = View(ctx).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xB3000000.toInt(), 0x00000000))
        }
        cardView.addView(scrim, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(72f).toInt(), Gravity.TOP))

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18f).toInt(), dp(14f).toInt(), dp(18f).toInt(), 0)
        }
        val arrowView = TurnArrowView(ctx)
        val text = TextView(ctx).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            letterSpacing = 0.12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(dp(4f), 0f, dp(1f), 0x99000000.toInt())
        }
        row.addView(arrowView, LinearLayout.LayoutParams(dp(22f).toInt(), dp(22f).toInt()))
        row.addView(text, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        cardView.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        // Sinyal tarafındaki kenar şeridi (kart köşesiyle kırpılır).
        val bar = View(ctx).apply { setBackgroundColor(AMBER) }
        cardView.addView(bar, FrameLayout.LayoutParams(dp(4f).toInt(), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START))

        // İnce açık kontur: koyu harita üstünde kartın sınırı belli olsun.
        val ringDrawable = GradientDrawable().apply {
            setColor(0x00000000)
            setStroke(dp(1f).toInt(), 0x2EFFFFFF)
        }
        ringBg = ringDrawable
        val ring = View(ctx).apply { background = ringDrawable }
        cardView.addView(ring, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val rootView = FrameLayout(ctx)
        rootView.addView(cardView, FrameLayout.LayoutParams(start.w, start.h))
        radius = 0f
        applyRadius(cardView, start.h)

        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            start.w, start.h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                // Hizmet bağlamından eklenen pencere kendiliğinden donanım hızlandırmalı değil; TextureView ister.
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // Kesin yer [place] ile sinyal tarafına göre verilir.
            gravity = Gravity.TOP or Gravity.LEFT
            x = start.x
            y = start.y
            title = if (demo) "TurnCameraPreview" else "TurnCamera"
        }
        try {
            windowManager.addView(rootView, params)
        } catch (e: RuntimeException) {
            Log.w(TAG, "gösterge penceresi eklenemedi", e)
            DiagJournal.event("sinyal", "gösterge penceresi eklenemedi: $e")
            return false
        }
        wm = windowManager
        this.params = params
        root = rootView
        card = cardView
        header = row
        label = text
        arrow = arrowView
        accent = bar
        placeholder = wait
        live = false
        side = null
        pendingSide = null
        hiding = false
        revealPending = false
        return true
    }

    private fun bindSide(s: TurnSide) {
        val nameRes = if (s == TurnSide.LEFT) R.string.camera360_left else R.string.camera360_right
        label?.text = (app.getString(nameRes) + " " + app.getString(R.string.camera360_camera_word)).uppercase()
        arrow?.pointLeft = s == TurnSide.LEFT
        // Ok sinyal yönünde: solda "ok + etiket", sağda "etiket + ok" ve satır sağa yaslı.
        header?.let { row ->
            val a = arrow ?: return@let
            val t = label ?: return@let
            row.removeAllViews()
            val gap = dp(10f).toInt()
            val alp = LinearLayout.LayoutParams(dp(22f).toInt(), dp(22f).toInt())
            val tlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (s == TurnSide.LEFT) {
                alp.marginEnd = gap
                row.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                row.addView(a, alp); row.addView(t, tlp)
            } else {
                alp.marginStart = gap
                row.gravity = Gravity.CENTER_VERTICAL or Gravity.END
                row.addView(t, tlp); row.addView(a, alp)
            }
        }
        accent?.let { (it.layoutParams as FrameLayout.LayoutParams).gravity = if (s == TurnSide.LEFT) Gravity.START else Gravity.END; it.requestLayout() }
    }

    /**
     * Ok ve kenar şeridi: aracın ritminde ([CarAnimSpec.BLINK_MS]) iki durumlu yanıp sönme. Eskiden 60 fps alfa
     * animasyonuydu; her karede kamera görüntüsüyle birlikte tüm pencere yeniden çiziliyordu (araçta RenderThread ~%13
     * çekirdek). Şimdi döngü başına iki çizim.
     */
    private var blinkOn = false
    private var blinking = false
    private val blinkStep = object : Runnable {
        override fun run() {
            if (!blinking) return
            blinkOn = !blinkOn
            val a = if (blinkOn) 1f else BLINK_DIM
            arrow?.alpha = a
            accent?.alpha = a
            main.postDelayed(this, CarAnimSpec.BLINK_MS / 2)
        }
    }

    private fun startBlink() {
        if (blinking) return
        blinking = true
        blinkOn = false
        main.removeCallbacks(blinkStep)
        main.post(blinkStep)
    }

    private fun stopBlink() {
        blinking = false
        main.removeCallbacks(blinkStep)
    }

    private fun removeWindow() {
        stopBlink()
        main.removeCallbacks(revealFallback)
        revealPending = false
        pendingSide = null
        if (demo) {
            detachWindow()
            return
        }
        if (closing) return
        // EGL ana thread'de beklenmez. Yüzey ve pencere, motor bıraktıktan sonra kalkar.
        closing = true
        val gen = ++closeGen
        engine.clearAuxOutput {
            if (gen != closeGen || !closing) return@clearAuxOutput
            closing = false
            detachWindow()
        }
    }

    private fun detachWindow() {
        surface?.release()
        surface = null
        val r = root
        try {
            if (r != null) wm?.removeView(r)
        } catch (e: RuntimeException) {
            Log.w(TAG, "gösterge penceresi kaldırılamadı", e)
            DiagJournal.event("sinyal", "gösterge penceresi kaldırılamadı: $e")
        }
        root = null; card = null; header = null; label = null; arrow = null; accent = null; placeholder = null
        cardBg = null; ringBg = null
        wm = null
        params = null
        side = null
        hiding = false
        live = false
    }

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
            // Sabit tampon: kart boyutu ne olursa olsun motor aynı küçük yüzeye çizer, TextureView ölçekler.
            st.setDefaultBufferSize(BUF_W, BUF_H)
            val s = Surface(st)
            surface = s
            side?.let { engine.setAuxOutput(s, BUF_W, BUF_H, it.cam, TurnCamera.dewarp(app)) }
        }

        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
            // Görünüm boyutu değişti (boyut ayarı); tampon sabit kalır, motora bir şey söylenmez.
            st.setDefaultBufferSize(BUF_W, BUF_H)
        }

        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
            if (surface == null) return true
            // Pencere biz kapatmadan yok oldu: EGL bırakılmadan texture framework'e geri verilmez.
            val owned = surface
            surface = null
            val gen = ++closeGen
            closing = true
            engine.clearAuxOutput {
                owned?.release()
                // false döndük: texture'ı framework değil biz bırakırız.
                runCatching { st.release() }
                if (gen == closeGen && closing) closing = false
            }
            return false
        }

        /** Motor yalnızca gerçek kamera karesi çizer ([CameraGlEngine] ikinci çıkış); her güncelleme gerçek görüntüdür. */
        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
            if (!live) {
                live = true
                val p = placeholder
                // Kart henüz görünmüyorsa yazı animasyonsuz kalkar; görünüyorsa (geç gelen kamera) yumuşakça.
                if (p != null) {
                    if (revealPending) p.alpha = 0f else p.animate().alpha(0f).setDuration(FADE_MS).start()
                }
            }
            if (revealPending && pendingSide == null) reveal()
        }
    }

    private var density = 1f
    private fun dp(v: Float) = v * density

    /** Kehribar dönüş oku: sapı ve üçgen başı, yumuşak köşeli. */
    private class TurnArrowView(context: Context) : View(context) {
        var pointLeft = true
            set(value) { field = value; invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AMBER
            style = Paint.Style.FILL
            pathEffect = android.graphics.CornerPathEffect(2f)
        }
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            path.reset()
            // Sola bakan ok; sağ için yatay aynalanır.
            path.moveTo(0f, h / 2f)
            path.lineTo(w * 0.48f, h * 0.06f)
            path.lineTo(w * 0.48f, h * 0.32f)
            path.lineTo(w, h * 0.32f)
            path.lineTo(w, h * 0.68f)
            path.lineTo(w * 0.48f, h * 0.68f)
            path.lineTo(w * 0.48f, h * 0.94f)
            path.close()
            canvas.save()
            if (!pointLeft) canvas.scale(-1f, 1f, w / 2f, h / 2f)
            canvas.drawPath(path, paint)
            canvas.restore()
        }
    }

    private companion object {
        const val TAG = "TurnCameraOverlay"
        /**
         * Motorun çizdiği sabit yüzey (16:10). Yükseklik yarım kareyi (400) aşmaz: motor yarım çözünürlük kopya ister
         * (düz görünüm hariç). Kart boyutu yalnızca ekranda ölçeklenir.
         */
        const val BUF_W = 640
        const val BUF_H = 400
        const val ENTER_MS = 240L
        const val EXIT_MS = 180L
        const val FADE_MS = 200L
        /** Taraf değişiminde eski kartın sönüşü. */
        const val SWITCH_OUT_MS = 90L
        /** İlk kare bu sürede gelmezse kart "Kamera açılıyor…" ile girer. */
        const val REVEAL_FALLBACK_MS = 400L
        /** Bekleyen kartın saydamlığı: görünmez ama TextureView çizilmeye devam eder. */
        const val PENDING_ALPHA = 0.01f
        /** Yanıp sönmenin sönük hâli (tam sönmez: ok her an okunur). */
        const val BLINK_DIM = 0.22f
        const val AMBER = 0xFFFFB020.toInt()
        val EASE_OUT = PathInterpolator(0.2f, 0f, 0f, 1f)
        val EASE_IN = PathInterpolator(0.4f, 0f, 1f, 1f)
    }
}
