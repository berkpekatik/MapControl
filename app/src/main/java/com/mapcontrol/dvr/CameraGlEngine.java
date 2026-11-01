package com.mapcontrol.dvr;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Kamera 360 GPU motoru: tek EGL thread'i hem onizleme yuzeyine hem kayit
 * encoder yuzeylerine cizer (CPU'da renk donusumu/olcekleme yok).
 *
 * Kareler: cihaz dma-buf import destekliyorsa ION buffer'lari EGLImage olarak
 * dogrudan orneklenir (sifir kopya); desteklemiyorsa hub'in CPU kopyasi doku
 * olarak yuklenir (izgarada yarim cozunurluk). Paketlenmis YUYV fragment
 * shader'da YUV->RGB (BT.601) + istege bagli balikgozu duzeltme ile cozulur.
 */
public final class CameraGlEngine implements CameraHub.FrameListener {
    private static final String TAG = "CameraGlEngine";
    private static final String HUB_TAG = "gl-engine";
    private static final int CAM_COUNT = CameraHub.CAM_COUNT;
    /** Izgara sirasi (satir satir): On(0), Arka(3), Sol(2), Sag(1). CAMERA_IDS={5,4,8,9}. */
    public static final int[] GRID_ORDER = {0, 3, 2, 1};
    private static final long RECORD_FRAME_NS = 1_000_000_000L / CameraRecorder.FPS;
    private static final long OVERLAY_REFRESH_MS = 250;
    private static final long REQUEST_TIMEOUT_MS = 3000;
    private static final int EGL_RECORDABLE_ANDROID = 0x3142;
    private static final int MAX_FAIL_RESTARTS = 3;
    /** Art arda bu kadar cizim hatasindan sonra motor durur. */
    private static final int MAX_DRAW_ERRORS = 5;
    private static final long SURFACE_RETRY_MS = 500;
    /**
     * Izgara onizlemesinde ilk yeni kareden sonra diger kameralarin karesi en fazla bu kadar
     * beklenir; boylece 4 kameranin kareleri tek cizimde toplanir (kamera basina cizim yerine).
     * 25 fps kare araliginin yarisi: senkron kameralarda kare basina tek cizim, senkron
     * olmayanlarda en fazla bu kadar ek gecikme.
     */
    private static final long PREVIEW_COALESCE_NS = 20_000_000L;
    /** Cizim/sn olcumunun log araligi. */
    private static final long DRAW_STATS_LOG_MS = 5000;

    private static final String VERT =
            "attribute vec4 aPos;\n" +
            "attribute vec2 aUv;\n" +
            "varying vec2 vUv;\n" +
            "void main(){ vUv = aUv; gl_Position = aPos; }\n";

    private static String camFrag(boolean external) {
        return (external ? "#extension GL_OES_EGL_image_external : require\n" : "") +
                "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
                "precision highp float;\n" +
                "#else\n" +
                "precision mediump float;\n" +
                "#endif\n" +
                "varying vec2 vUv;\n" +
                (external ? "uniform samplerExternalOES uTex;\n" : "uniform sampler2D uTex;\n") +
                "uniform float uDewarp;\n" +      // 0 = ham, 1 = tam duzeltme
                "uniform float uAspect;\n" +      // cizim alani en/boy
                "uniform float uFocal;\n" +       // rektilineer odak (kalibrasyon)
                "uniform float uThetaMax;\n" +    // lens yari acisi (rad)
                "uniform vec2 uSrcSize;\n" +      // kaynak kare W,H (piksel)
                "uniform float uUyvy;\n" +        // 1: kaynak UYVY (U Y0 V Y1), 0: YUYV
                "void main(){\n" +
                "  vec2 uv = vUv;\n" +
                "  if (uDewarp > 0.001) {\n" +
                "    vec2 p = uv * 2.0 - 1.0;\n" +
                "    p.x *= uAspect;\n" +
                "    float r = length(p);\n" +
                "    vec2 fishUv = vec2(0.5, 0.5);\n" +
                "    if (r > 0.0001) {\n" +
                "      float theta = atan(r, uFocal);\n" +
                "      float rFish = theta / uThetaMax;\n" +
                "      vec2 q = (p / r) * rFish;\n" +
                // fisheye dairesi kaynakta yuksekligi doldurur: x yaricapi = 0.5*(H/W)
                "      fishUv = vec2(0.5 + q.x * 0.5 * uSrcSize.y / uSrcSize.x, 0.5 + q.y * 0.5);\n" +
                "    }\n" +
                "    uv = mix(uv, fishUv, uDewarp);\n" +
                "  }\n" +
                "  if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {\n" +
                "    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);\n" +
                "    return;\n" +
                "  }\n" +
                // Paketlenmis YUYV: texel = (Y0,U,Y1,V); cift genislikte piksel sec
                "  float fx = uv.x * uSrcSize.x;\n" +
                "  float pair = floor(fx * 0.5);\n" +
                "  vec4 t = texture2D(uTex, vec2((pair + 0.5) / (uSrcSize.x * 0.5), uv.y));\n" +
                "  float evenPx = (mod(fx, 2.0) < 1.0) ? 1.0 : 0.0;\n" +
                "  float yv = mix(mix(t.b, t.r, evenPx), mix(t.a, t.g, evenPx), uUyvy);\n" +
                "  float yc = 1.1643 * (yv - 0.0627);\n" +
                "  float uu = mix(t.g, t.r, uUyvy) - 0.5;\n" +
                "  float vv = mix(t.a, t.b, uUyvy) - 0.5;\n" +
                "  vec3 rgb = vec3(yc + 1.596 * vv, yc - 0.3918 * uu - 0.8129 * vv, yc + 2.017 * uu);\n" +
                "  gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), 1.0);\n" +
                "}\n";
    }

    private static final String SPRITE_FRAG =
            "precision mediump float;\n" +
            "varying vec2 vUv;\n" +
            "uniform sampler2D uTex;\n" +
            "void main(){ gl_FragColor = texture2D(uTex, vUv); }\n";

    private static final String SOLID_FRAG =
            "precision mediump float;\n" +
            "uniform vec4 uColor;\n" +
            "void main(){ gl_FragColor = uColor; }\n";

    /** Ana thread'de cagrilir. */
    public interface FpsListener {
        void onFps(int cam, int fps);
    }

    /** Kayit hedefi: ayri modda 4 yuzey (kamera sirasi), mozaikte 1. */
    public static final class RecordConfig {
        final Surface[] surfaces;
        final int width;
        final int height;
        final boolean separate;
        final String[] labels;
        final CameraRecorder.OverlayProvider overlay;

        /** labels: kamera sirasiyla (0=On,1=Sag,2=Sol,3=Arka) etiketler. */
        public RecordConfig(Surface[] surfaces, int width, int height, boolean separate,
                            String[] labels, CameraRecorder.OverlayProvider overlay) {
            this.surfaces = surfaces;
            this.width = width;
            this.height = height;
            this.separate = separate;
            this.labels = labels;
            this.overlay = overlay;
        }
    }

    private static final class Request {
        final RecordConfig config;
        final CountDownLatch done = new CountDownLatch(1);
        volatile boolean ok;

        Request(RecordConfig config) {
            this.config = config;
        }
    }

    private static final CameraGlEngine INSTANCE = new CameraGlEngine();

    public static CameraGlEngine get() {
        return INSTANCE;
    }

    private final CameraHub hub = CameraHub.get();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();

    // lock ile korunur
    private Thread thread;
    private boolean previewEnabled;
    private Surface previewSurface;
    private int previewW = 1;
    private int previewH = 1;
    private boolean previewSurfaceChanged;
    private CountDownLatch previewReleased;
    private Request startRequest;
    private Request stopRequest;
    /** Son onizleme ciziminden beri yeni kare gelen kameralar (bit i = kamera i). */
    private int frameDirtyMask;
    /** frameDirtyMask'in bos durumdan doldugu an (System.nanoTime). */
    private long frameDirtySinceNs;
    private boolean layoutDirty;
    /** Onizleme EGL yuzeyi sifirdan kurulsun (buyuk/izgara gecisinde bayat kare kalmasin). */
    private boolean previewRecreate;
    /** Olu motor thread'i en fazla bu kadar kez onizleme acikken yeniden baslatilir. */
    private int failRestarts;

    private volatile int bigCam = -1;
    private volatile float dewarp = 0f;
    private volatile FpsListener fpsListener;

    // ---- Yalnizca motor thread'i ----
    private EglCore egl;
    private EGLSurface previewEgl = EGL14.EGL_NO_SURFACE;
    private Surface previewEglOwner;
    private int viewW = 1;
    private int viewH = 1;
    /** Onizlemede en son cizilen buyuk kamera (-1 = izgara, -2 = henuz cizilmedi). */
    private int drawnBig = -2;
    /** Kaybolan onizleme yuzeyi icin sonraki yeniden deneme zamani (uptimeMillis). */
    private long surfaceRetryAtMs;
    private RecordConfig record;
    private EGLSurface[] recordEgl;
    private long recordStartNs;
    private long nextRecordNs;
    private long recordFrameIndex;
    private long lastOverlayMs;
    private boolean zeroCopy;
    private CamProgram cam2d;
    private CamProgram camExt;
    private int spriteProg;
    private int spriteAPos;
    private int spriteAUv;
    private int spriteUTex;
    private int solidProg;
    private int solidAPos;
    private int solidUColor;
    private FloatBuffer quad;
    private final int[] copyTex = new int[CAM_COUNT];
    private final int[] copyTexW = new int[CAM_COUNT];
    private final int[] copyTexH = new int[CAM_COUNT];
    /** Cizilecek dokunun kaynak kare boyutu (shader uSrcSize). */
    private final int[] drawSrcW = new int[CAM_COUNT];
    private final int[] drawSrcH = new int[CAM_COUNT];
    /** 1 ise doku UYVY duzeninde (sadece sifir kopya; kopya yolu native'de YUYV'ye cevrilir). */
    private final int[] drawUyvy = new int[CAM_COUNT];
    private final long[] copySeq = new long[CAM_COUNT];
    private final int[] drawTex = new int[CAM_COUNT];
    private final boolean[] drawExternal = new boolean[CAM_COUNT];
    private final int[] lastCopy = new int[CAM_COUNT];
    private final boolean[] lastHold = new boolean[CAM_COUNT];
    private boolean demandSent;
    private boolean uploadFresh;
    private final int[] frameCounts = new int[CAM_COUNT];
    private long fpsWindowStart;
    private int previewDraws;
    private int recordDraws;
    private long drawStatsStart;
    private final TextSprite[] labelSprites = new TextSprite[CAM_COUNT];
    private final TextSprite infoSprite = new TextSprite();
    private final TextSprite noSignalSprite = new TextSprite();
    private final CameraHub.FrameReader[] readers = new CameraHub.FrameReader[CAM_COUNT];

    private CameraGlEngine() {
        for (int i = 0; i < CAM_COUNT; i++) {
            final int cam = i;
            readers[i] = (buf, width, height, seq) -> uploadCopy(cam, buf, width, height, seq);
            labelSprites[i] = new TextSprite();
        }
    }

    // ---------- Genel API (herhangi bir thread) ----------

    public void setFpsListener(FpsListener l) {
        fpsListener = l;
    }

    public int getBigCam() {
        return bigCam;
    }

    public void setBigCam(int cam) {
        int old = bigCam;
        bigCam = cam;
        if (old != cam) {
            Log.i(TAG, "onizleme gorunumu: " + old + " -> " + cam);
        }
        synchronized (lock) {
            layoutDirty = true;
            // Buyuk <-> izgara gecisinde yuzeyi yeniden kurup ilk kareyi kesin cizdir.
            previewRecreate = true;
            lock.notifyAll();
        }
    }

    public void setDewarp(float value) {
        dewarp = value;
        synchronized (lock) {
            layoutDirty = true;
            lock.notifyAll();
        }
    }

    /** Onizleme cizimi (ve akis talebi) acik/kapali. */
    public void setPreviewEnabled(boolean enabled) {
        synchronized (lock) {
            previewEnabled = enabled;
            layoutDirty = true;
            if (enabled) failRestarts = 0;
            if (enabled) ensureThreadLocked();
            lock.notifyAll();
        }
    }

    public void setPreviewSurface(Surface surface, int width, int height) {
        synchronized (lock) {
            previewSurface = surface;
            previewW = Math.max(1, width);
            previewH = Math.max(1, height);
            previewSurfaceChanged = true;
            layoutDirty = true;
            lock.notifyAll();
        }
    }

    /** SurfaceHolder.surfaceDestroyed'dan cagrilir: EGL yuzeyi birakilana kadar bekler. */
    public void clearPreviewSurface() {
        CountDownLatch latch;
        synchronized (lock) {
            previewSurface = null;
            previewSurfaceChanged = true;
            if (thread == null) {
                return;
            }
            latch = new CountDownLatch(1);
            previewReleased = latch;
            lock.notifyAll();
        }
        await(latch, 1000);
    }

    /** Encoder yuzeylerine cizime baslar; false ise cagiran CPU yoluna gecmeli. */
    public boolean startRecording(RecordConfig config) {
        Request req = new Request(config);
        synchronized (lock) {
            startRequest = req;
            ensureThreadLocked();
            lock.notifyAll();
        }
        if (!await(req.done, REQUEST_TIMEOUT_MS)) {
            Log.w(TAG, "kayit baslatma zaman asimi");
            stopRecording();
            return false;
        }
        return req.ok;
    }

    /** Donunce motor encoder yuzeylerine artik cizmez (codec guvenle kapatilabilir). */
    public void stopRecording() {
        Request req = new Request(null);
        synchronized (lock) {
            if (thread == null) {
                return;
            }
            stopRequest = req;
            lock.notifyAll();
        }
        await(req.done, REQUEST_TIMEOUT_MS);
    }

    @Override
    public void onFrame(int cam) {
        // Buyuk modda yalnizca gosterilen kamera yeniden cizim gerektirir (kayit sirasinda
        // diger kameralar da kare uretir; kayit kendi temposuyla cizilir).
        int big = bigCam;
        if (big >= 0 && big != cam) {
            return;
        }
        synchronized (lock) {
            if (frameDirtyMask == 0) {
                frameDirtySinceNs = System.nanoTime();
            }
            frameDirtyMask |= 1 << cam;
            lock.notifyAll();
        }
    }

    /**
     * Onizleme icin kare toplama penceresi doldu mu: beklenen tum kameralar yeni kare verdi
     * veya ilk kareden beri PREVIEW_COALESCE_NS gecti. lock altinda cagrilir.
     */
    private boolean previewFramesDueLocked(long nowNs) {
        int mask = frameDirtyMask;
        if (mask == 0) {
            return false;
        }
        int want = expectedFrameMask();
        return (mask & want) == want || nowNs - frameDirtySinceNs >= PREVIEW_COALESCE_NS;
    }

    /** Onizlemede kare beklenen kameralar: buyuk modda yalnizca o, izgarada canli olanlar. */
    private int expectedFrameMask() {
        int big = bigCam;
        if (big >= 0 && big < CAM_COUNT) {
            return 1 << big;
        }
        int want = 0;
        for (int cam = 0; cam < CAM_COUNT; cam++) {
            if (hub.getState(cam) == CameraHub.STATE_LIVE) want |= 1 << cam;
        }
        return want;
    }

    private void ensureThreadLocked() {
        if (thread == null) {
            thread = new Thread(this::run, "cam-gl");
            thread.start();
        }
    }

    private static boolean await(CountDownLatch latch, long ms) {
        try {
            return latch.await(ms, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ---------- Motor thread'i ----------

    private void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
        boolean failed = false;
        try {
            egl = new EglCore();
            zeroCopy = HabNative.zcInit();
            initGl();
            hub.addFrameListener(this);
            hub.acquire(HUB_TAG, null);
            loop();
        } catch (Throwable t) {
            failed = true;
            Log.e(TAG, "GL motoru durdu", t);
        } finally {
            teardown();
            synchronized (lock) {
                thread = null;
                if (previewReleased != null) {
                    previewReleased.countDown();
                    previewReleased = null;
                }
                boolean retryAfterFailure = failed && previewEnabled && failRestarts < MAX_FAIL_RESTARTS;
                if (retryAfterFailure) {
                    // Motor dusunce onizleme son kareyle donuyordu; sinirli sayida yeniden baslat.
                    failRestarts++;
                    Log.w(TAG, "GL motoru yeniden baslatiliyor (" + failRestarts + "/"
                            + MAX_FAIL_RESTARTS + ")");
                }
                if ((!failed && (previewEnabled || startRequest != null)) || retryAfterFailure) {
                    ensureThreadLocked();
                } else {
                    if (startRequest != null) {
                        startRequest.done.countDown();
                        startRequest = null;
                    }
                    if (stopRequest != null) {
                        stopRequest.done.countDown();
                        stopRequest = null;
                    }
                }
            }
        }
    }

    private void loop() throws InterruptedException {
        fpsWindowStart = SystemClock.uptimeMillis();
        drawStatsStart = fpsWindowStart;
        previewDraws = 0;
        recordDraws = 0;
        int drawErrors = 0;
        while (true) {
            boolean surfaceChange;
            boolean recreate;
            boolean surfaceRetry;
            Surface surf;
            int pw;
            int ph;
            CountDownLatch released;
            Request startReq;
            Request stopReq;
            boolean layout;
            boolean previewOn;
            boolean framesReady;
            synchronized (lock) {
                while (true) {
                    if (previewSurfaceChanged || startRequest != null || stopRequest != null) break;
                    if (!previewEnabled && record == null) return;
                    long now = System.nanoTime();
                    long nowMs = SystemClock.uptimeMillis();
                    boolean recDue = record != null && now >= nextRecordNs;
                    boolean surfaceUp = previewEgl != EGL14.EGL_NO_SURFACE;
                    // Yuzey kaybolduysa (swap/makeCurrent hatasi) kalici donmasin: yeniden dene.
                    boolean lost = previewEnabled && !surfaceUp && previewSurface != null
                            && previewSurface.isValid();
                    if (lost && nowMs >= surfaceRetryAtMs) break;
                    if (previewEnabled && previewRecreate && previewSurface != null) break;
                    // Buyuk kamera degistiyse bayraklar yarisa girse bile yeniden ciz.
                    boolean prevDue = previewEnabled && surfaceUp
                            && (previewFramesDueLocked(now) || layoutDirty || bigCam != drawnBig);
                    if (recDue || prevDue) break;
                    long waitMs = record != null ? Math.max(1, (nextRecordNs - now) / 1_000_000L) : 500;
                    if (lost) waitMs = Math.min(waitMs, Math.max(1, surfaceRetryAtMs - nowMs));
                    if (previewEnabled && surfaceUp && frameDirtyMask != 0) {
                        // Toplama penceresi bitince eksik kamera beklenmeden cizilsin.
                        long leftNs = PREVIEW_COALESCE_NS - (now - frameDirtySinceNs);
                        waitMs = Math.min(waitMs, Math.max(1, (leftNs + 999_999L) / 1_000_000L));
                    }
                    lock.wait(waitMs);
                }
                surfaceChange = previewSurfaceChanged;
                previewSurfaceChanged = false;
                recreate = previewRecreate;
                previewRecreate = false;
                surf = previewSurface;
                pw = previewW;
                ph = previewH;
                released = previewReleased;
                previewReleased = null;
                startReq = startRequest;
                startRequest = null;
                stopReq = stopRequest;
                stopRequest = null;
                layout = layoutDirty;
                layoutDirty = false;
                previewOn = previewEnabled;
                // Kayit temposuyla uyanildiginda pencere dolmadiysa kareler sonraki cizime kalir.
                framesReady = previewOn && previewFramesDueLocked(System.nanoTime());
                if (framesReady || !previewOn) {
                    frameDirtyMask = 0;
                }
            }
            surfaceRetry = previewOn && previewEgl == EGL14.EGL_NO_SURFACE && surf != null
                    && surf.isValid() && !surfaceChange
                    && SystemClock.uptimeMillis() >= surfaceRetryAtMs;
            if (recreate && previewEgl != EGL14.EGL_NO_SURFACE) {
                egl.destroySurface(previewEgl);
                previewEgl = EGL14.EGL_NO_SURFACE;
                previewEglOwner = null;
            }
            if (surfaceChange || recreate || surfaceRetry) {
                applyPreviewSurface(surf, pw, ph);
                if (previewOn && surf != null && previewEgl == EGL14.EGL_NO_SURFACE) {
                    surfaceRetryAtMs = SystemClock.uptimeMillis() + SURFACE_RETRY_MS;
                }
                layout = true;
            }
            if (released != null) released.countDown();
            if (stopReq != null) {
                destroyRecordSurfaces();
                record = null;
                stopReq.ok = true;
                stopReq.done.countDown();
            }
            if (startReq != null) {
                startReq.ok = beginRecord(startReq.config);
                startReq.done.countDown();
            }

            boolean drew = false;
            try {
                updateDemand(previewOn);
                // Dokular her turda guncellenir (sifir kopyada eski kare boylece iade edilir);
                // onizleme ise yalnizca toplanan kareler hazir olunca cizilir.
                updateTextures();
                if (previewOn && previewEgl != EGL14.EGL_NO_SURFACE
                        && (framesReady || layout || surfaceChange || bigCam != drawnBig)) {
                    drew = drawPreview();
                    if (drew) previewDraws++;
                }
                drawErrors = 0;
            } catch (RuntimeException e) {
                // Tek bir bozuk kare/yukleme motoru oldurmesin (onizleme son karede donuyordu).
                if (++drawErrors >= MAX_DRAW_ERRORS) {
                    throw e;
                }
                Log.w(TAG, "onizleme cizim hatasi (" + drawErrors + "/" + MAX_DRAW_ERRORS + ")", e);
                drawnBig = -2;
                synchronized (lock) {
                    layoutDirty = true;
                }
                Thread.sleep(40);
            }
            if (record != null && System.nanoTime() >= nextRecordNs) {
                drawRecord();
                drew = true;
                recordDraws++;
            }
            if (zeroCopy && drew) {
                // Cizim bitmeden onceki kare sunucuya iade edilmemeli. glFinish yerine fence:
                // render thread'i GPU'yu beklemez; zcAcquire/zcDetach iadeden once fence'i bekler.
                HabNative.zcFenceInsert();
            }
            reportFps();
        }
    }

    private void teardown() {
        hub.removeFrameListener(this);
        if (egl != null) {
            try {
                egl.makeCurrentPbuffer();
                if (zeroCopy) HabNative.zcShutdown();
                destroyRecordSurfaces();
                if (previewEgl != EGL14.EGL_NO_SURFACE) egl.destroySurface(previewEgl);
                for (TextSprite s : labelSprites) s.release();
                infoSprite.release();
                noSignalSprite.release();
            } catch (Throwable t) {
                Log.w(TAG, "GL kapatma hata", t);
            }
            egl.release();
            egl = null;
        }
        previewEgl = EGL14.EGL_NO_SURFACE;
        previewEglOwner = null;
        drawnBig = -2;
        record = null;
        zeroCopy = false;
        cam2d = null;
        camExt = null;
        for (int i = 0; i < CAM_COUNT; i++) {
            copyTex[i] = 0;
            copyTexW[i] = 0;
            copyTexH[i] = 0;
            copySeq[i] = 0;
            drawTex[i] = 0;
        }
        demandSent = false;
        hub.clearDemand(HUB_TAG);
        hub.release(HUB_TAG);
    }

    private void initGl() {
        cam2d = new CamProgram(buildProgram(VERT, camFrag(false)), GLES20.GL_TEXTURE_2D);
        if (zeroCopy) {
            int p = tryBuildProgram(VERT, camFrag(true));
            if (p != 0) {
                camExt = new CamProgram(p, GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
            } else {
                disableZeroCopy();
            }
        }
        spriteProg = buildProgram(VERT, SPRITE_FRAG);
        spriteAPos = GLES20.glGetAttribLocation(spriteProg, "aPos");
        spriteAUv = GLES20.glGetAttribLocation(spriteProg, "aUv");
        spriteUTex = GLES20.glGetUniformLocation(spriteProg, "uTex");
        solidProg = buildProgram(VERT, SOLID_FRAG);
        solidAPos = GLES20.glGetAttribLocation(solidProg, "aPos");
        solidUColor = GLES20.glGetUniformLocation(solidProg, "uColor");
        quad = ByteBuffer.allocateDirect(6 * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4);
    }

    private void disableZeroCopy() {
        Log.w(TAG, "sifir kopya kullanilamiyor, kopya yoluna geciliyor");
        for (int i = 0; i < CAM_COUNT; i++) HabNative.zcDetach(i);
        HabNative.zcShutdown();
        zeroCopy = false;
        camExt = null;
        demandSent = false;
    }

    private void applyPreviewSurface(Surface surf, int w, int h) {
        if (previewEgl != EGL14.EGL_NO_SURFACE && surf != previewEglOwner) {
            egl.destroySurface(previewEgl);
            previewEgl = EGL14.EGL_NO_SURFACE;
            previewEglOwner = null;
        }
        if (surf != null && previewEgl == EGL14.EGL_NO_SURFACE) {
            try {
                previewEgl = egl.createWindowSurface(surf);
                previewEglOwner = surf;
            } catch (RuntimeException e) {
                Log.w(TAG, "onizleme yuzeyi olusturulamadi", e);
            }
        }
        viewW = w;
        viewH = h;
    }

    private boolean beginRecord(RecordConfig cfg) {
        destroyRecordSurfaces();
        record = null;
        if (cfg == null || cfg.surfaces == null || cfg.surfaces.length == 0) {
            return false;
        }
        EGLSurface[] created = new EGLSurface[cfg.surfaces.length];
        try {
            for (int i = 0; i < created.length; i++) {
                created[i] = egl.createWindowSurface(cfg.surfaces[i]);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "encoder yuzeyi olusturulamadi", e);
            for (EGLSurface s : created) {
                if (s != null) egl.destroySurface(s);
            }
            return false;
        }
        recordEgl = created;
        record = cfg;
        recordStartNs = System.nanoTime();
        nextRecordNs = recordStartNs;
        recordFrameIndex = 0;
        lastOverlayMs = 0;
        float labelPx = cfg.height / (cfg.separate ? 26f : 34f);
        for (int i = 0; i < CAM_COUNT; i++) {
            String label = cfg.labels != null && i < cfg.labels.length ? cfg.labels[i] : "";
            labelSprites[i].update(new String[]{label}, labelPx);
        }
        noSignalSprite.update(new String[]{"SINYAL YOK"}, labelPx);
        return true;
    }

    private void destroyRecordSurfaces() {
        if (recordEgl == null) {
            return;
        }
        for (EGLSurface s : recordEgl) {
            if (s != null) egl.destroySurface(s);
        }
        recordEgl = null;
    }

    // ---------- Akis talebi + dokular ----------

    private void updateDemand(boolean previewOn) {
        int big = bigCam;
        for (int cam = 0; cam < CAM_COUNT; cam++) {
            int previewMode = HabNative.COPY_NONE;
            if (previewOn && (big < 0 || big == cam)) {
                previewMode = big == cam ? HabNative.COPY_FULL : HabNative.COPY_HALF;
            }
            int recordMode = HabNative.COPY_NONE;
            if (record != null) {
                recordMode = record.separate && record.height > hub.getFrameHeight(cam) / 2
                        ? HabNative.COPY_FULL : HabNative.COPY_HALF;
            }
            int mode;
            if (previewMode == HabNative.COPY_FULL || recordMode == HabNative.COPY_FULL) {
                mode = HabNative.COPY_FULL;
            } else if (previewMode == HabNative.COPY_HALF || recordMode == HabNative.COPY_HALF) {
                mode = HabNative.COPY_HALF;
            } else {
                mode = HabNative.COPY_NONE;
            }
            int copy = zeroCopy ? HabNative.COPY_NONE : mode;
            boolean hold = zeroCopy && mode != HabNative.COPY_NONE;
            if (!demandSent || copy != lastCopy[cam] || hold != lastHold[cam]) {
                hub.setDemand(HUB_TAG, cam, copy, hold);
                lastCopy[cam] = copy;
                lastHold[cam] = hold;
            }
        }
        demandSent = true;
    }

    /** true: en az bir kamerada yeni kare. */
    private boolean updateTextures() {
        boolean fresh = false;
        for (int cam = 0; cam < CAM_COUNT; cam++) {
            drawTex[cam] = 0;
            if (hub.getState(cam) != CameraHub.STATE_LIVE) {
                continue;
            }
            if (zeroCopy) {
                long v = HabNative.zcAcquire(cam);
                if (v < 0) {
                    disableZeroCopy();
                    return updateTextures();
                }
                drawTex[cam] = (int) (v & 0xFFFFFFFFL);
                drawExternal[cam] = true;
                drawSrcW[cam] = hub.getFrameWidth(cam);
                drawSrcH[cam] = hub.getFrameHeight(cam);
                drawUyvy[cam] = hub.getFrameFormat(cam) == 0x07080102 ? 1 : 0;
                if (((v >>> 32) & 1L) != 0) {
                    fresh = true;
                    frameCounts[cam]++;
                }
            } else {
                uploadFresh = false;
                hub.readFrame(cam, readers[cam]);
                if (uploadFresh) {
                    fresh = true;
                    frameCounts[cam]++;
                }
            }
        }
        return fresh;
    }

    /** readFrame'den, hub kilidi DISINDA cagrilir; buf okuma suresince yazilmaz (CameraHub.reading). */
    private void uploadCopy(int cam, ByteBuffer buf, int width, int height, long seq) {
        if (buf == null || width < 2 || height <= 0) {
            return;
        }
        if (copyTex[cam] == 0) {
            int[] t = new int[1];
            GLES20.glGenTextures(1, t, 0);
            copyTex[cam] = t[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0]);
            setTexParams(GLES20.GL_TEXTURE_2D);
            copyTexW[cam] = 0;
            copyTexH[cam] = 0;
            copySeq[cam] = 0;
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, copyTex[cam]);
        }
        if (seq != copySeq[cam]) {
            buf.position(0);
            int texW = width / 2;
            if (width != copyTexW[cam] || height != copyTexH[cam]) {
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, texW, height, 0,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
                copyTexW[cam] = width;
                copyTexH[cam] = height;
            } else {
                GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, texW, height,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
            }
            copySeq[cam] = seq;
            uploadFresh = true;
        }
        drawTex[cam] = copyTex[cam];
        drawExternal[cam] = false;
        drawSrcW[cam] = copyTexW[cam];
        drawSrcH[cam] = hub.getFrameHeight(cam);
        drawUyvy[cam] = 0; // kopya yolu native'de YUYV'ye normalize edilir
    }

    private static void setTexParams(int target) {
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }

    // ---------- Cizim ----------

    private boolean drawPreview() {
        if (!egl.makeCurrent(previewEgl)) {
            dropPreviewSurface();
            return false;
        }
        GLES20.glViewport(0, 0, viewW, viewH);
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        float dw = dewarp;
        int big = bigCam;
        drawnBig = big;
        if (big >= 0 && big < CAM_COUNT) {
            drawCam(big, -1f, -1f, 1f, 1f, (float) viewW / viewH, dw);
        } else {
            // 2x2 kart izgara: hucreler arasi bosluk kart hissi verir
            float gapX = 0.012f, gapY = 0.02f;
            float cellAspect = (viewW / 2f) / (viewH / 2f);
            for (int pos = 0; pos < CAM_COUNT; pos++) {
                int col = pos % 2;
                int row = pos / 2; // 0 = ust satir
                float x0 = -1f + col + gapX;
                float x1 = -1f + (col + 1) - gapX;
                float y1 = 1f - row - gapY;
                float y0 = 1f - (row + 1) + gapY;
                drawCam(GRID_ORDER[pos], x0, y0, x1, y1, cellAspect, dw);
            }
        }
        if (!egl.swap(previewEgl)) {
            dropPreviewSurface();
        }
        return true;
    }

    private void dropPreviewSurface() {
        Log.w(TAG, "onizleme yuzeyi kayboldu (egl=0x" + Integer.toHexString(EGL14.eglGetError()) + ")");
        egl.makeCurrentPbuffer();
        egl.destroySurface(previewEgl);
        previewEgl = EGL14.EGL_NO_SURFACE;
        previewEglOwner = null;
        // Yuzey gecerliyse dongu bir sure sonra yeniden kurar; ilk kare kesin cizilir.
        drawnBig = -2;
        surfaceRetryAtMs = SystemClock.uptimeMillis() + 100;
    }

    private void drawRecord() {
        RecordConfig r = record;
        long nowMs = SystemClock.uptimeMillis();
        if (nowMs - lastOverlayMs >= OVERLAY_REFRESH_MS) {
            lastOverlayMs = nowMs;
            String[] lines = null;
            try {
                if (r.overlay != null) lines = r.overlay.getLines();
            } catch (Throwable ignored) {
            }
            infoSprite.update(lines != null ? lines : new String[0], r.height / 28f);
        }
        long ptsNs = recordStartNs + recordFrameIndex * RECORD_FRAME_NS;
        if (r.separate) {
            for (int i = 0; i < recordEgl.length && i < CAM_COUNT; i++) {
                renderRecordTarget(r, i, ptsNs);
            }
        } else {
            renderRecordTarget(r, -1, ptsNs);
        }
        recordFrameIndex++;
        nextRecordNs += RECORD_FRAME_NS;
        long nowNs = System.nanoTime();
        if (nextRecordNs < nowNs - 1_000_000_000L) {
            nextRecordNs = nowNs; // cok geri kaldik, tempo sifirla
        }
    }

    /** cam >= 0: ayri kayit (yuzey = kamera); cam < 0: mozaik (tek yuzey). */
    private void renderRecordTarget(RecordConfig r, int cam, long ptsNs) {
        EGLSurface surface = recordEgl[cam >= 0 ? cam : 0];
        if (surface == null || !egl.makeCurrent(surface)) {
            return;
        }
        int w = r.width, h = r.height;
        GLES20.glViewport(0, 0, w, h);
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        int pad = Math.max(8, h / 60);
        if (cam >= 0) {
            drawCam(cam, -1f, -1f, 1f, 1f, (float) w / h, 0f);
            if (drawTex[cam] == 0) drawSpriteCentered(noSignalSprite, 0, 0, w, h, w, h);
            drawSprite(labelSprites[cam], pad, pad, w, h);
        } else {
            int qw = w / 2, qh = h / 2;
            for (int pos = 0; pos < CAM_COUNT; pos++) {
                int c = GRID_ORDER[pos];
                int col = pos % 2, row = pos / 2;
                drawCam(c, -1f + col, -row, (float) col, 1f - row, (float) qw / qh, 0f);
                if (drawTex[c] == 0) drawSpriteCentered(noSignalSprite, col * qw, row * qh, qw, qh, w, h);
                drawSprite(labelSprites[c], col * qw + pad, row * qh + pad, w, h);
            }
        }
        drawSprite(infoSprite, pad, h - infoSprite.h - pad, w, h);
        egl.setPresentationTime(surface, ptsNs);
        if (!egl.swap(surface)) {
            Log.w(TAG, "encoder yuzeyine yazilamadi (egl=0x" + Integer.toHexString(EGL14.eglGetError()) + ")");
        }
    }

    private void drawCam(int cam, float x0, float y0, float x1, float y1, float aspect, float dw) {
        int tex = drawTex[cam];
        if (tex == 0) {
            drawSolid(x0, y0, x1, y1, 0.07f, 0.07f, 0.08f);
            return;
        }
        CamProgram p = drawExternal[cam] && camExt != null ? camExt : cam2d;
        GLES20.glUseProgram(p.id);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(p.target, tex);
        GLES20.glUniform1i(p.uTex, 0);
        GLES20.glUniform1f(p.uDewarp, dw);
        GLES20.glUniform1f(p.uAspect, aspect);
        GLES20.glUniform1f(p.uFocal, 0.42f);
        GLES20.glUniform1f(p.uThetaMax, 1.62f);
        int srcW = drawSrcW[cam] > 0 ? drawSrcW[cam] : HabNative.FRAME_WIDTH;
        int srcH = drawSrcH[cam] > 0 ? drawSrcH[cam] : HabNative.FRAME_HEIGHT;
        GLES20.glUniform2f(p.uSrcSize, srcW, srcH);
        GLES20.glUniform1f(p.uUyvy, drawUyvy[cam]);
        drawQuad(p.aPos, p.aUv, x0, y0, x1, y1);
    }

    private void drawSolid(float x0, float y0, float x1, float y1, float r, float g, float b) {
        GLES20.glUseProgram(solidProg);
        GLES20.glUniform4f(solidUColor, r, g, b, 1f);
        drawQuad(solidAPos, -1, x0, y0, x1, y1);
    }

    /** Piksel koordinatlarinda (sol ust orijin) metin karosu, alfa karisimli. */
    private void drawSprite(TextSprite s, int px, int py, int outW, int outH) {
        s.upload();
        if (s.tex == 0 || s.w <= 0) {
            return;
        }
        float x0 = -1f + 2f * px / outW;
        float x1 = -1f + 2f * (px + s.w) / outW;
        float y1 = 1f - 2f * py / outH;
        float y0 = 1f - 2f * (py + s.h) / outH;
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(spriteProg);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, s.tex);
        GLES20.glUniform1i(spriteUTex, 0);
        drawQuad(spriteAPos, spriteAUv, x0, y0, x1, y1);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private void drawSpriteCentered(TextSprite s, int areaX, int areaY, int areaW, int areaH,
                                    int outW, int outH) {
        drawSprite(s, areaX + (areaW - s.w) / 2, areaY + (areaH - s.h) / 2, outW, outH);
    }

    private void drawQuad(int aPos, int aUv, float x0, float y0, float x1, float y1) {
        // v: ust kose 0, alt kose 1 (doku satir 0 = goruntu ustu, ekran ustunde olmali)
        quad.position(0);
        quad.put(x0).put(y0).put(0f).put(1f);
        quad.put(x1).put(y0).put(1f).put(1f);
        quad.put(x0).put(y1).put(0f).put(0f);
        quad.put(x1).put(y0).put(1f).put(1f);
        quad.put(x1).put(y1).put(1f).put(0f);
        quad.put(x0).put(y1).put(0f).put(0f);
        quad.position(0);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad);
        if (aUv >= 0) {
            quad.position(2);
            GLES20.glEnableVertexAttribArray(aUv);
            GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 16, quad);
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        GLES20.glDisableVertexAttribArray(aPos);
        if (aUv >= 0) GLES20.glDisableVertexAttribArray(aUv);
    }

    private void reportFps() {
        long now = SystemClock.uptimeMillis();
        if (drawStatsStart == 0) {
            drawStatsStart = now;
        }
        long statsElapsed = now - drawStatsStart;
        if (statsElapsed >= DRAW_STATS_LOG_MS) {
            if (previewDraws > 0 || recordDraws > 0) {
                Log.i(TAG, "cizim/sn: onizleme=" + (previewDraws * 1000L / statsElapsed)
                        + " kayit=" + (recordDraws * 1000L / statsElapsed)
                        + " (buyuk=" + bigCam + ", sifir kopya=" + zeroCopy + ")");
            }
            previewDraws = 0;
            recordDraws = 0;
            drawStatsStart = now;
        }
        long elapsed = now - fpsWindowStart;
        if (elapsed < 1000) {
            return;
        }
        FpsListener l = fpsListener;
        for (int i = 0; i < CAM_COUNT; i++) {
            final int cam = i;
            final int fps = (int) (frameCounts[i] * 1000L / elapsed);
            frameCounts[i] = 0;
            if (l != null) {
                mainHandler.post(() -> l.onFps(cam, fps));
            }
        }
        fpsWindowStart = now;
    }

    // ---------- GL yardimcilari ----------

    private static final class CamProgram {
        final int id;
        final int target;
        final int aPos;
        final int aUv;
        final int uTex;
        final int uDewarp;
        final int uAspect;
        final int uFocal;
        final int uThetaMax;
        final int uSrcSize;
        final int uUyvy;

        CamProgram(int id, int target) {
            this.id = id;
            this.target = target;
            aPos = GLES20.glGetAttribLocation(id, "aPos");
            aUv = GLES20.glGetAttribLocation(id, "aUv");
            uTex = GLES20.glGetUniformLocation(id, "uTex");
            uDewarp = GLES20.glGetUniformLocation(id, "uDewarp");
            uAspect = GLES20.glGetUniformLocation(id, "uAspect");
            uFocal = GLES20.glGetUniformLocation(id, "uFocal");
            uThetaMax = GLES20.glGetUniformLocation(id, "uThetaMax");
            uSrcSize = GLES20.glGetUniformLocation(id, "uSrcSize");
            uUyvy = GLES20.glGetUniformLocation(id, "uUyvy");
        }
    }

    private static int buildProgram(String vs, String fs) {
        int p = tryBuildProgram(vs, fs);
        if (p == 0) {
            throw new RuntimeException("shader derlenemedi");
        }
        return p;
    }

    private static int tryBuildProgram(String vsSrc, String fsSrc) {
        int vs = compile(GLES20.GL_VERTEX_SHADER, vsSrc);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, fsSrc);
        if (vs == 0 || fs == 0) {
            return 0;
        }
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, vs);
        GLES20.glAttachShader(p, fs);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            Log.w(TAG, "program baglanamadi: " + GLES20.glGetProgramInfoLog(p));
            GLES20.glDeleteProgram(p);
            return 0;
        }
        return p;
    }

    private static int compile(int type, String src) {
        int sh = GLES20.glCreateShader(type);
        GLES20.glShaderSource(sh, src);
        GLES20.glCompileShader(sh);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(sh, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            Log.w(TAG, "shader hata: " + GLES20.glGetShaderInfoLog(sh));
            GLES20.glDeleteShader(sh);
            return 0;
        }
        return sh;
    }

    /** Arka planli metin karosu (Canvas ile cizilir, degisince yeniden yuklenir). */
    private static final class TextSprite {
        int tex;
        int w;
        int h;
        private String key;
        private Bitmap pending;

        void update(String[] lines, float textPx) {
            List<String> rows = new ArrayList<>();
            for (String l : lines) {
                if (l != null && !l.isEmpty()) rows.add(l);
            }
            String k = String.join("\n", rows) + "@" + (int) textPx;
            if (k.equals(key)) {
                return;
            }
            key = k;
            if (rows.isEmpty()) {
                w = 0;
                h = 0;
                return;
            }
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            paint.setTextSize(Math.max(10f, textPx));
            paint.setColor(Color.WHITE);
            Paint.FontMetrics fm = paint.getFontMetrics();
            float lineH = fm.descent - fm.ascent;
            int pad = Math.round(paint.getTextSize() * 0.4f);
            float maxW = 0;
            for (String r : rows) maxW = Math.max(maxW, paint.measureText(r));
            int bw = (int) Math.ceil(maxW) + pad * 2;
            int bh = (int) Math.ceil(lineH * rows.size()) + pad * 2;
            Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(0x99000000);
            c.drawRoundRect(0, 0, bw, bh, pad, pad, bg);
            float y = pad - fm.ascent;
            for (String r : rows) {
                c.drawText(r, pad, y, paint);
                y += lineH;
            }
            if (pending != null) pending.recycle();
            pending = b;
            w = bw;
            h = bh;
        }

        void upload() {
            if (pending == null) {
                return;
            }
            if (tex == 0) {
                int[] t = new int[1];
                GLES20.glGenTextures(1, t, 0);
                tex = t[0];
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
                setTexParams(GLES20.GL_TEXTURE_2D);
            } else {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
            }
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, pending, 0);
            pending.recycle();
            pending = null;
        }

        /** GL thread'inde, baglam yok edilmeden once. */
        void release() {
            if (tex != 0) {
                GLES20.glDeleteTextures(1, new int[]{tex}, 0);
                tex = 0;
            }
            if (pending != null) {
                pending.recycle();
                pending = null;
            }
            key = null;
            w = 0;
            h = 0;
        }
    }

    /** Minimal EGL14 sarmalayici: kayit edilebilir config + 1x1 pbuffer. */
    private static final class EglCore {
        final EGLDisplay display;
        final EGLConfig config;
        final EGLContext context;
        final EGLSurface pbuffer;

        EglCore() {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (display == EGL14.EGL_NO_DISPLAY) throw new RuntimeException("EGL display yok");
            int[] ver = new int[2];
            if (!EGL14.eglInitialize(display, ver, 0, ver, 1)) {
                throw new RuntimeException("eglInitialize hata");
            }
            EGLConfig cfg = chooseConfig(true);
            if (cfg == null) cfg = chooseConfig(false);
            if (cfg == null) throw new RuntimeException("EGL config yok");
            config = cfg;
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            if (context == EGL14.EGL_NO_CONTEXT) throw new RuntimeException("EGL context yok");
            pbuffer = EGL14.eglCreatePbufferSurface(display, config,
                    new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
            if (pbuffer == EGL14.EGL_NO_SURFACE) throw new RuntimeException("EGL pbuffer yok");
            makeCurrentPbuffer();
        }

        private EGLConfig chooseConfig(boolean recordable) {
            int[] attribs = {
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT | EGL14.EGL_PBUFFER_BIT,
                    recordable ? EGL_RECORDABLE_ANDROID : EGL14.EGL_NONE, 1,
                    EGL14.EGL_NONE};
            EGLConfig[] configs = new EGLConfig[1];
            int[] num = new int[1];
            if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0) || num[0] == 0) {
                return null;
            }
            return configs[0];
        }

        EGLSurface createWindowSurface(Surface surface) {
            EGLSurface s = EGL14.eglCreateWindowSurface(display, config, surface,
                    new int[]{EGL14.EGL_NONE}, 0);
            if (s == null || s == EGL14.EGL_NO_SURFACE) {
                throw new RuntimeException("eglCreateWindowSurface hata 0x"
                        + Integer.toHexString(EGL14.eglGetError()));
            }
            return s;
        }

        boolean makeCurrent(EGLSurface s) {
            return EGL14.eglMakeCurrent(display, s, s, context);
        }

        void makeCurrentPbuffer() {
            EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context);
        }

        boolean swap(EGLSurface s) {
            return EGL14.eglSwapBuffers(display, s);
        }

        void setPresentationTime(EGLSurface s, long ns) {
            EGLExt.eglPresentationTimeANDROID(display, s, ns);
        }

        void destroySurface(EGLSurface s) {
            if (s == null || s == EGL14.EGL_NO_SURFACE) return;
            if (s.equals(EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW))) {
                makeCurrentPbuffer();
            }
            EGL14.eglDestroySurface(display, s);
        }

        void release() {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(display, pbuffer);
            EGL14.eglDestroyContext(display, context);
            EGL14.eglReleaseThread();
        }
    }
}
