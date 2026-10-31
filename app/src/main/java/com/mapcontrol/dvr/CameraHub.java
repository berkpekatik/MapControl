package com.mapcontrol.dvr;

import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.IntConsumer;

/**
 * Kamera akislarinin tek sahibi: kamera basina TEK grab thread'i, kareyi tum
 * tuketicilere (onizleme, kayit) dagitir. Ayni HAB kanalina asla iki thread
 * ayni anda istek gondermez.
 *
 * Tuketiciler {@link #acquire}/{@link #release} ile referans sayar; son tuketici
 * birakinca akislar kisa bir gecikmeyle kapanir (mod gecislerinde yeniden acma yok).
 * Her kamera bagimsizdir: acilamayan kamera digerlerini engellemez, watchdog
 * kare gelmeyen kamerayi artan aralikla yeniden acar.
 */
public final class CameraHub {
    private static final String TAG = "CameraHub";
    public static final int CAM_COUNT = 4;

    public static final int STATE_OFF = 0;
    public static final int STATE_LIVE = 1;
    public static final int STATE_NO_SIGNAL = 2;
    public static final int STATE_CONNECTING = 3;

    private static final long STOP_DELAY_MS = 1500;
    private static final long FRAME_TIMEOUT_MS = 3000;
    private static final int MAX_CONSECUTIVE_ERRORS = 5;
    private static final long RETRY_MIN_MS = 1000;
    private static final long RETRY_MAX_MS = 10_000;
    private static final long THREAD_JOIN_MS = 4000;

    /** Ana thread'de cagrilir. */
    public interface StateListener {
        void onCameraState(int cam, int state);
    }

    /** Grab thread'inde cagrilir; hizli donmeli. */
    public interface FrameListener {
        void onFrame(int cam);
    }

    /** frame null ise kamerada gosterilecek kare yok. width/height: kopyanin boyutu. */
    public interface FrameReader {
        void read(ByteBuffer frame, int width, int height, long seq);
    }

    /** frames[i] null ise kamera i'de gosterilecek kare yok. */
    public interface FramesReader {
        void read(ByteBuffer[] frames, int[] widths, int[] heights);
    }

    private static final CameraHub INSTANCE = new CameraHub();

    public static CameraHub get() {
        return INSTANCE;
    }

    private static final class Demand {
        final int[] copy = new int[CAM_COUNT];
        final boolean[] hold = new boolean[CAM_COUNT];
    }

    /**
     * Cift buffer: grab thread'i back'e yazar, kilit altinda front ile degistirir.
     * readFrame kareyi kilit DISINDA okur (GL yuklemesi grab thread'ini bekletmesin); o sirada
     * okunan buffer "reading" olarak isaretlenir. Takastan sonra back o buffer'a denk gelirse
     * grab thread'i yazmak icin "spare"i kullanir (okunan kare yazilirken bozulmaz).
     */
    private static final class FrameSlot {
        final Object lock = new Object();
        ByteBuffer front;
        ByteBuffer back;
        ByteBuffer spare;
        /** readFrame'in kilit disinda okudugu buffer (yalnizca tek okuyucu: GL thread'i). */
        ByteBuffer reading;
        int frontWidth;
        int frontHeight;
        long seq;
        /** front, kopya talebi kesildiginden beri guncellenmedi; gosterilmez. */
        boolean stale;
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService control = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cam-hub-ctl");
        t.setDaemon(true);
        return t;
    });
    private final CopyOnWriteArrayList<StateListener> stateListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<FrameListener> frameListeners = new CopyOnWriteArrayList<>();
    private final AtomicInteger session = new AtomicInteger();
    private final AtomicIntegerArray states = new AtomicIntegerArray(CAM_COUNT);
    private final AtomicIntegerArray restartRequested = new AtomicIntegerArray(CAM_COUNT);
    /** Kameranin tam kare boyutu (native giris listesinden; yoksa varsayilan). */
    private final AtomicIntegerArray frameWidths = new AtomicIntegerArray(CAM_COUNT);
    private final AtomicIntegerArray frameHeights = new AtomicIntegerArray(CAM_COUNT);
    private final AtomicIntegerArray frameFormats = new AtomicIntegerArray(CAM_COUNT);
    private final FrameSlot[] slots = new FrameSlot[CAM_COUNT];

    // this ile korunur
    private final Map<String, Demand> consumers = new HashMap<>();
    private final List<IntConsumer> readyCallbacks = new ArrayList<>();
    private final Thread[] grabThreads = new Thread[CAM_COUNT];
    private boolean started;
    private boolean ready;

    private final Runnable stopRunnable = this::stopIfIdle;

    private CameraHub() {
        for (int i = 0; i < CAM_COUNT; i++) {
            slots[i] = new FrameSlot();
            frameWidths.set(i, HabNative.FRAME_WIDTH);
            frameHeights.set(i, HabNative.FRAME_HEIGHT);
        }
    }

    // ---------- Tuketici yonetimi ----------

    /**
     * Akislari acik tutar. onReady (ana thread) ilk acilis denemesi bitince
     * o anda canli kameralarin bit maskesiyle cagrilir; hub zaten hazirsa hemen.
     */
    public void acquire(String tag, IntConsumer onReady) {
        synchronized (this) {
            mainHandler.removeCallbacks(stopRunnable);
            if (!consumers.containsKey(tag)) {
                consumers.put(tag, new Demand());
            }
            if (!started) {
                started = true;
                ready = false;
                final int mySession = session.incrementAndGet();
                control.execute(() -> doStart(mySession));
            }
            if (onReady != null) {
                if (ready) {
                    final int mask = liveMask();
                    mainHandler.post(() -> onReady.accept(mask));
                } else {
                    readyCallbacks.add(onReady);
                }
            }
        }
    }

    public void release(String tag) {
        synchronized (this) {
            if (consumers.remove(tag) == null) {
                return;
            }
            if (consumers.isEmpty() && started) {
                mainHandler.removeCallbacks(stopRunnable);
                mainHandler.postDelayed(stopRunnable, STOP_DELAY_MS);
            }
        }
    }

    /** Tuketicinin kamera icin istedigi CPU kopyasi (HabNative.COPY_*) ve/veya GL tutmasi. */
    public synchronized void setDemand(String tag, int cam, int copyMode, boolean hold) {
        Demand d = consumers.get(tag);
        if (d == null || cam < 0 || cam >= CAM_COUNT) {
            return;
        }
        d.copy[cam] = copyMode;
        d.hold[cam] = hold;
    }

    public synchronized void clearDemand(String tag) {
        Demand d = consumers.get(tag);
        if (d == null) {
            return;
        }
        for (int i = 0; i < CAM_COUNT; i++) {
            d.copy[i] = HabNative.COPY_NONE;
            d.hold[i] = false;
        }
    }

    /** Tum kameralari kapatip yeniden acar (sinyal yenileme). */
    public void restartAll() {
        for (int i = 0; i < CAM_COUNT; i++) {
            restartRequested.set(i, 1);
            setState(i, STATE_CONNECTING);
        }
    }

    public int getState(int cam) {
        return cam >= 0 && cam < CAM_COUNT ? states.get(cam) : STATE_OFF;
    }

    /** Kameranin tam kare genisligi (piksel). */
    public int getFrameWidth(int cam) {
        return cam >= 0 && cam < CAM_COUNT ? frameWidths.get(cam) : HabNative.FRAME_WIDTH;
    }

    /** Kameranin piksel formati (descriptor'dan; 0 = bilinmiyor). */
    public int getFrameFormat(int cam) {
        return cam >= 0 && cam < CAM_COUNT ? frameFormats.get(cam) : 0;
    }

    /** Kameranin tam kare yuksekligi (piksel). */
    public int getFrameHeight(int cam) {
        return cam >= 0 && cam < CAM_COUNT ? frameHeights.get(cam) : HabNative.FRAME_HEIGHT;
    }

    public int liveMask() {
        int mask = 0;
        for (int i = 0; i < CAM_COUNT; i++) {
            if (states.get(i) == STATE_LIVE) mask |= 1 << i;
        }
        return mask;
    }

    public void addStateListener(StateListener l) {
        if (l != null) stateListeners.addIfAbsent(l);
    }

    public void removeStateListener(StateListener l) {
        stateListeners.remove(l);
    }

    public void addFrameListener(FrameListener l) {
        if (l != null) frameListeners.addIfAbsent(l);
    }

    public void removeFrameListener(FrameListener l) {
        frameListeners.remove(l);
    }

    // ---------- Kare okuma (CPU kopya yolu) ----------

    /**
     * Kameranin son karesini okur. Okuma (GL yuklemesi) kilit disinda yapilir; okunan buffer
     * okuma bitene kadar grab thread'i tarafindan yazilmaz. Ayni kamera icin ayni anda yalnizca
     * tek cagiran olmali (GL thread'i).
     */
    public void readFrame(int cam, FrameReader reader) {
        FrameSlot s = slots[cam];
        ByteBuffer buf = null;
        int width = 0;
        int height = 0;
        long seq = 0;
        synchronized (s.lock) {
            boolean has = s.front != null && s.seq > 0 && !s.stale && states.get(cam) == STATE_LIVE;
            if (has) {
                buf = s.front;
                width = s.frontWidth;
                height = s.frontHeight;
                seq = s.seq;
                s.reading = buf;
            }
        }
        if (buf == null) {
            reader.read(null, 0, 0, 0);
            return;
        }
        try {
            // Konum paylasilmasin: readFrames (CPU kayit) ayni buffer'i ayni anda okuyabilir.
            ByteBuffer view = buf.duplicate();
            view.position(0);
            reader.read(view, width, height, seq);
        } finally {
            synchronized (s.lock) {
                if (s.reading == buf) s.reading = null;
            }
        }
    }

    /** 4 kameranin son karelerini birlikte kilitleyip okur (mozaik/ayri kayit). */
    public void readFrames(FramesReader reader) {
        synchronized (slots[0].lock) {
            synchronized (slots[1].lock) {
                synchronized (slots[2].lock) {
                    synchronized (slots[3].lock) {
                        ByteBuffer[] frames = new ByteBuffer[CAM_COUNT];
                        int[] widths = new int[CAM_COUNT];
                        int[] heights = new int[CAM_COUNT];
                        for (int i = 0; i < CAM_COUNT; i++) {
                            FrameSlot s = slots[i];
                            if (s.front != null && s.seq > 0 && !s.stale && states.get(i) == STATE_LIVE) {
                                s.front.position(0);
                                frames[i] = s.front;
                                widths[i] = s.frontWidth;
                                heights[i] = s.frontHeight;
                            }
                        }
                        reader.read(frames, widths, heights);
                    }
                }
            }
        }
    }

    // ---------- Yasam dongusu (control thread) ----------

    private void stopIfIdle() {
        final Thread[] threads;
        synchronized (this) {
            if (!consumers.isEmpty() || !started) {
                return;
            }
            started = false;
            ready = false;
            session.incrementAndGet();
            threads = grabThreads.clone();
            for (int i = 0; i < CAM_COUNT; i++) {
                grabThreads[i] = null;
            }
        }
        control.execute(() -> doStop(threads));
    }

    private void doStart(int mySession) {
        for (int i = 0; i < CAM_COUNT; i++) {
            setState(i, STATE_CONNECTING);
        }
        int mask = HabNative.hubStart();
        List<IntConsumer> callbacks;
        int liveMask;
        synchronized (this) {
            if (session.get() != mySession) {
                // Arada durduruldu; kuyruktaki doStop native tarafi kapatir.
                return;
            }
            for (int i = 0; i < CAM_COUNT; i++) {
                boolean active = mask > 0 && (mask & (1 << i)) != 0;
                setState(i, active ? STATE_LIVE : STATE_NO_SIGNAL);
                restartRequested.set(i, 0);
                final int cam = i;
                Thread t = new Thread(() -> grabLoop(cam, mySession, active), "cam-hub-" + i);
                grabThreads[i] = t;
                t.start();
            }
            ready = true;
            liveMask = liveMask();
            callbacks = new ArrayList<>(readyCallbacks);
            readyCallbacks.clear();
        }
        Log.i(TAG, "akislar acik, canli maske=0x" + Integer.toHexString(liveMask));
        for (IntConsumer cb : callbacks) {
            mainHandler.post(() -> cb.accept(liveMask));
        }
    }

    private void doStop(Thread[] threads) {
        for (Thread t : threads) {
            if (t != null) t.interrupt();
        }
        for (Thread t : threads) {
            if (t == null) continue;
            try {
                t.join(THREAD_JOIN_MS);
            } catch (InterruptedException ignored) {
            }
            if (t.isAlive()) {
                // Native taraf kamera kilidiyle korunur; kapatma devam eden islemi bekler.
                Log.w(TAG, t.getName() + " zamaninda cikmadi");
            }
        }
        HabNative.hubStop();
        for (int i = 0; i < CAM_COUNT; i++) {
            FrameSlot s = slots[i];
            synchronized (s.lock) {
                s.front = null;
                s.back = null;
                s.spare = null;
                s.reading = null;
                s.seq = 0;
            }
            setState(i, STATE_OFF);
        }
        Log.i(TAG, "akislar kapandi");
    }

    // ---------- Grab dongusu (kamera basina tek thread) ----------

    private int combinedCopy(int cam) {
        int best = HabNative.COPY_NONE;
        for (Demand d : consumers.values()) {
            int m = d.copy[cam];
            if (m == HabNative.COPY_FULL) return HabNative.COPY_FULL;
            if (m == HabNative.COPY_HALF) best = HabNative.COPY_HALF;
        }
        return best;
    }

    private boolean combinedHold(int cam) {
        for (Demand d : consumers.values()) {
            if (d.hold[cam]) return true;
        }
        return false;
    }

    private void grabLoop(int cam, int mySession, boolean initiallyActive) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
        FrameSlot slot = slots[cam];
        boolean active = initiallyActive;
        long retryMs = RETRY_MIN_MS;
        long lastFrameMs = SystemClock.uptimeMillis();
        int errors = 0;
        if (active) loadGeometry(cam);
        while (session.get() == mySession) {
            if (restartRequested.getAndSet(cam, 0) == 1) {
                active = false;
                retryMs = 0;
            }
            if (!active) {
                if (retryMs > 0 && !sleepUnlessRestart(cam, retryMs)) return;
                if (session.get() != mySession) return;
                setState(cam, STATE_CONNECTING);
                active = HabNative.hubRestart(cam);
                if (active) loadGeometry(cam);
                setState(cam, active ? STATE_LIVE : STATE_NO_SIGNAL);
                retryMs = active ? RETRY_MIN_MS
                        : Math.min(Math.max(retryMs * 2, RETRY_MIN_MS), RETRY_MAX_MS);
                lastFrameMs = SystemClock.uptimeMillis();
                errors = 0;
                continue;
            }

            int mode;
            boolean hold;
            synchronized (this) {
                mode = combinedCopy(cam);
                hold = combinedHold(cam);
            }
            boolean idle = mode == HabNative.COPY_NONE && !hold;
            if (idle) {
                // Kimse cizmiyor (or. buyuk modda diger kameralar) ama kare yine cekilip
                // kopyasiz iade edilir: HAB sunucusu tuketilmeyen akista sonraki istekleri
                // hatayla donduruyor (geri donuste donuk kare + yeniden acilis).
                synchronized (slot.lock) {
                    slot.stale = true;
                }
            }
            final int width = frameWidths.get(cam);
            final int height = frameHeights.get(cam);
            ByteBuffer back = null;
            if (mode != HabNative.COPY_NONE) {
                int bytes = width * height * 2;
                synchronized (slot.lock) {
                    if (slot.back != null && slot.back == slot.reading) {
                        // GL bu buffer'i kilit disinda okuyor: yedek buffer'a yaz.
                        ByteBuffer t = slot.back;
                        slot.back = slot.spare;
                        slot.spare = t;
                    }
                    if (slot.back == null || slot.back.capacity() < bytes) {
                        slot.back = ByteBuffer.allocateDirect(bytes);
                    }
                    back = slot.back;
                }
            }
            int r = HabNative.hubGrab(cam, back, mode, hold);
            long now = SystemClock.uptimeMillis();
            if (r == 1) {
                lastFrameMs = now;
                errors = 0;
                if (back != null) {
                    synchronized (slot.lock) {
                        if (slot.back == back) {
                            slot.back = slot.front;
                            slot.front = back;
                            slot.frontWidth = width;
                            slot.frontHeight = mode == HabNative.COPY_FULL ? height : height / 2;
                            slot.seq++;
                            slot.stale = false;
                        }
                    }
                }
                if (states.get(cam) != STATE_LIVE) setState(cam, STATE_LIVE);
                if (!idle) {
                    for (FrameListener l : frameListeners) {
                        l.onFrame(cam);
                    }
                }
            } else if (r < 0) {
                errors++;
                if (!sleepQuietly(100)) return;
            }
            if (errors >= MAX_CONSECUTIVE_ERRORS || now - lastFrameMs > FRAME_TIMEOUT_MS) {
                Log.w(TAG, "kamera " + cam + " yanit vermiyor (hata=" + errors + "), yeniden aciliyor");
                active = false;
                retryMs = 500;
                setState(cam, STATE_NO_SIGNAL);
            }
        }
    }

    /** Acilan kameranin native geometrisini alir (CPU kopya tamponlari buna gore ayrilir). */
    private void loadGeometry(int cam) {
        int[] d = HabNative.hubDescriptor(cam);
        int w = HabNative.FRAME_WIDTH;
        int h = HabNative.FRAME_HEIGHT;
        if (d != null && d.length > HabNative.DESC_HEIGHT
                && d[HabNative.DESC_WIDTH] > 0 && d[HabNative.DESC_HEIGHT] > 0) {
            w = d[HabNative.DESC_WIDTH];
            h = d[HabNative.DESC_HEIGHT];
        }
        frameFormats.set(cam, d != null && d.length > HabNative.DESC_FORMAT
                ? d[HabNative.DESC_FORMAT] : 0);
        int oldW = frameWidths.getAndSet(cam, w);
        int oldH = frameHeights.getAndSet(cam, h);
        if (oldW != w || oldH != h) {
            Log.i(TAG, "kamera " + cam + " geometri " + w + "x" + h);
        }
    }

    private void setState(int cam, int state) {
        if (states.getAndSet(cam, state) == state) {
            return;
        }
        if (stateListeners.isEmpty()) {
            return;
        }
        mainHandler.post(() -> {
            for (StateListener l : stateListeners) {
                l.onCameraState(cam, state);
            }
        });
    }

    /** Yeniden deneme beklemesi; kullanici yenileme isterse erken biter. */
    private boolean sleepUnlessRestart(int cam, long ms) {
        long end = SystemClock.uptimeMillis() + ms;
        while (restartRequested.get(cam) == 0) {
            long left = end - SystemClock.uptimeMillis();
            if (left <= 0) return true;
            if (!sleepQuietly(Math.min(left, 100))) return false;
        }
        restartRequested.set(cam, 0);
        return true;
    }

    private static boolean sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }
}
