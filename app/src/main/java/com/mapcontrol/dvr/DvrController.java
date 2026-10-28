package com.mapcontrol.dvr;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;

import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.mapcontrol.service.RecordingService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Uygulama genelinde tek kayit sahibi. Uc kaynak ayni {@link CameraRecorder}'i paylasir:
 * <ul>
 *   <li>Elle USB kaydi (Kamera 360 &gt; USB Kayit)</li>
 *   <li>Kontakla USB'ye otomatik kayit (powermode 2 + USB takili)</li>
 *   <li>Arac ici otomatik kayit: {@link #getInternalDir()}, 3 dk klipler; Kalite modu 4 ayri
 *   aci 640x400, Performans modu tek mozaik 960x600</li>
 * </ul>
 * Arac ici mod acikken USB kaydi ve USB otomatik kayit kullanilamaz; USB otomatik
 * kayit acikken arac ici mod kullanilamaz. Arac ici modda bos alan her zaman en az
 * {@link #RESERVE_BYTES} kalir. Klipler zaman damgasi + artan "_id_N" ile adlandirilir
 * (son id'den devam eder, yoksa 1); rezerv asilmaya basladiginda en kucuk id'li klipten
 * baslanarak yer acilana kadar silinir. Tarihe gore silme yoktur.
 *
 * Tum durum main thread'de tutulur; dosya tarama/silme {@code dvr-io} thread'indedir.
 */
public final class DvrController {
    private static final String TAG = "DvrController";

    public enum Source { NONE, MANUAL_USB, AUTO_USB, AUTO_INTERNAL }

    /** Arac ici kayit bicimi. */
    public enum InternalMode {
        /** 4 aci ayri dosya (f_m_, r_m_, l_m_, b_m_). */
        QUALITY(640, 400, true),
        /** Tek mozaik dosya (s_); tek encoder, daha az yuk ve alan. */
        PERFORMANCE(960, 600, false);

        public final int width;
        public final int height;
        public final boolean separate;

        InternalMode(int width, int height, boolean separate) {
            this.width = width;
            this.height = height;
            this.separate = separate;
        }

        public long bytesPerMinute() {
            return CameraRecorder.bytesPerMinute(width, height, separate);
        }
    }

    public enum AutoStatus {
        OFF, WAITING_IGNITION, STARTING, RECORDING, SUSPENDED,
        NO_CAMERA, NOT_WRITABLE, LOW_SPACE, NO_USB, USB_FULL, ERROR
    }

    public static final String USB_DIR_NAME = "MapControl";
    public static final String USB_EXPORT_DIR_NAME = "Klipler";
    /** Arac ici kayitta her zaman bos birakilacak alan. */
    public static final long RESERVE_BYTES = 2_000_000_000L;
    public static final long SEGMENT_MS = 3 * 60_000L;
    public static final long MIN_USB_FIT_MINUTES = 10;
    /** Rezervin ustunde kayda baslamak icin gereken en az sure. */
    private static final long MIN_INTERNAL_START_MINUTES = 10;
    /**
     * Kayit surerken rezervin ustunde tutulan tampon: bir klibin (3 dk) + 1 dk boyutu.
     * Sonraki klip acilmadan once bu kadar yer yoksa en kucuk id'li klipler silinir.
     */
    private static final long HEADROOM_MINUTES = SEGMENT_MS / 60_000L + 1;

    public static final String PREFS = "camera360";
    private static final String KEY_AUTO_INTERNAL = "autoInternal";
    private static final String KEY_AUTO_USB = "autoUsb";
    private static final String KEY_RES_INDEX = "resIndex";
    private static final String KEY_SEPARATE = "separate";
    private static final String KEY_INTERNAL_MODE = "internalMode";

    private static final long TICK_MS = 10_000;
    private static final long RETRY_MS = 30_000;
    private static final String HUB_TAG_PROBE = "dvr-auto";

    public interface Listener {
        default void onStarted(Source source, File file) {
        }

        default void onStopped(Source source, File file, long elapsedMs) {
        }

        default void onError(Source source, String message) {
        }

        /** Otomatik mod durumu, ayarlar veya depolama bilgisi degisti. */
        default void onStateChanged() {
        }
    }

    /** Arac ici depolama ozeti (dvr-io thread'inde hesaplanir). */
    public static final class StorageInfo {
        public boolean writable;
        public long freeBytes = -1;
        public long totalBytes = -1;
        public long clipsBytes;
        public int clipCount;
        public long oldestClipMs;
        /** Hesaplandigi andaki kayit modunun dakikalik boyutu. */
        public long bytesPerMinute = InternalMode.QUALITY.bytesPerMinute();

        /** Klipler icin kullanilabilecek toplam alan: bos + mevcut klipler - rezerv. */
        public long recordBudgetBytes() {
            if (freeBytes < 0) return 0;
            return Math.max(0, freeBytes + clipsBytes - RESERVE_BYTES);
        }

        public long capacityMinutes() {
            return bytesPerMinute > 0 ? recordBudgetBytes() / bytesPerMinute : 0;
        }
    }

    private static DvrController instance;

    public static synchronized DvrController get(Context context) {
        if (instance == null) {
            instance = new DvrController(context.getApplicationContext());
        }
        return instance;
    }

    private final Context app;
    private final File internalDir;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> new Thread(r, "dvr-io"));
    private final CameraRecorder recorder = new CameraRecorder();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final boolean cameraSupported = new File("/dev/hab").exists();

    private Source source = Source.NONE;
    private int powerMode = -1;
    private boolean suspended;
    private boolean starting;
    private long retryAfterMs;
    private AutoStatus autoStatus = AutoStatus.OFF;
    private String lastError;
    private volatile StorageInfo storage = new StorageInfo();
    private volatile Supplier<Integer> speedSupplier;
    private volatile Supplier<String> gearSupplier;
    private boolean ticking;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            tick();
            main.postDelayed(this, TICK_MS);
        }
    };

    private DvrController(Context app) {
        this.app = app;
        this.internalDir = resolveInternalDir(app);
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        recorder.setOverlayProvider(this::overlayLines);
        if (isAutoInternalEnabled() && isAutoUsbEnabled()) {
            prefs.edit().putBoolean(KEY_AUTO_USB, false).apply();
        }
    }

    /**
     * Normal uygulama (untrusted_app) SELinux nedeniyle /data/local altina giremez;
     * uygulamaya ait harici dizin ayni /data bolumundedir (FUSE), izin gerektirmez.
     */
    private static File resolveInternalDir(Context app) {
        File ext = app.getExternalFilesDir(null);
        return new File(ext != null ? ext : app.getFilesDir(), "DVR");
    }

    public File getInternalDir() {
        return internalDir;
    }

    /** Servis (veya UI) acilisinda cagrilir; periyodik denetimi baslatir. */
    public void start() {
        main.post(() -> {
            if (ticking) return;
            ticking = true;
            main.post(tickRunnable);
            refreshStorageAsync();
        });
    }

    // ---------- Disaridan gelen olaylar ----------

    /** MapControlService powermode degisince cagirir (herhangi bir thread). */
    public void onPowerMode(int mode) {
        main.post(() -> {
            if (mode == powerMode) return;
            Log.i(TAG, "powermode " + powerMode + " -> " + mode);
            powerMode = mode;
            if (mode != 2) {
                suspended = false;
                retryAfterMs = 0;
            }
            tick();
        });
    }

    public boolean isIgnitionOn() {
        return powerMode == 2;
    }

    public void setOverlaySuppliers(Supplier<Integer> speed, Supplier<String> gear) {
        speedSupplier = speed;
        gearSupplier = gear;
    }

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    // ---------- Ayarlar ----------

    public boolean isAutoInternalEnabled() {
        return prefs.getBoolean(KEY_AUTO_INTERNAL, false);
    }

    public boolean isAutoUsbEnabled() {
        return prefs.getBoolean(KEY_AUTO_USB, false);
    }

    public boolean isCameraSupported() {
        return cameraSupported;
    }

    /** USB otomatik kayit aciksa veya baska kayit suruyorsa acilamaz. */
    public boolean canEnableAutoInternal() {
        return !isAutoUsbEnabled() && !recorder.isBusy();
    }

    public boolean canEnableAutoUsb() {
        return !isAutoInternalEnabled() && !(recorder.isBusy() && source == Source.AUTO_INTERNAL);
    }

    public boolean setAutoInternalEnabled(boolean enabled) {
        if (enabled == isAutoInternalEnabled()) return true;
        if (enabled && !canEnableAutoInternal()) return false;
        prefs.edit().putBoolean(KEY_AUTO_INTERNAL, enabled).apply();
        if (enabled) {
            retryAfterMs = 0;
            suspended = false;
        } else if (source == Source.AUTO_INTERNAL) {
            stopRecording(false);
        }
        tick();
        notifyState();
        return true;
    }

    public boolean setAutoUsbEnabled(boolean enabled) {
        if (enabled == isAutoUsbEnabled()) return true;
        if (enabled && !canEnableAutoUsb()) return false;
        prefs.edit().putBoolean(KEY_AUTO_USB, enabled).apply();
        if (enabled) {
            retryAfterMs = 0;
            suspended = false;
        } else if (source == Source.AUTO_USB) {
            stopRecording(false);
        }
        tick();
        notifyState();
        return true;
    }

    public InternalMode getInternalMode() {
        try {
            return InternalMode.valueOf(prefs.getString(KEY_INTERNAL_MODE, InternalMode.QUALITY.name()));
        } catch (IllegalArgumentException e) {
            return InternalMode.QUALITY;
        }
    }

    /** Kayit suruyorsa klip kapatilir; yeni bicimle hemen yeniden baslar. */
    public void setInternalMode(InternalMode mode) {
        if (mode == null || mode == getInternalMode()) return;
        prefs.edit().putString(KEY_INTERNAL_MODE, mode.name()).apply();
        if (source == Source.AUTO_INTERNAL && recorder.isRecording()) {
            stopRecording(false);
        }
        refreshStorageAsync();
        notifyState();
    }

    public int getUsbResIndex() {
        return Math.max(0, Math.min(CameraRecorder.RESOLUTIONS.length - 1, prefs.getInt(KEY_RES_INDEX, 0)));
    }

    public boolean getUsbSeparate() {
        return prefs.getBoolean(KEY_SEPARATE, false);
    }

    // ---------- Durum ----------

    public CameraRecorder recorder() {
        return recorder;
    }

    public Source getSource() {
        return recorder.isBusy() ? source : Source.NONE;
    }

    public boolean isRecording() {
        return recorder.isRecording();
    }

    public boolean isRecordingUsb() {
        return recorder.isRecording() && (source == Source.MANUAL_USB || source == Source.AUTO_USB);
    }

    public boolean isRecordingInternal() {
        return recorder.isRecording() && source == Source.AUTO_INTERNAL;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public AutoStatus getAutoStatus() {
        return autoStatus;
    }

    public String getLastError() {
        return lastError;
    }

    public StorageInfo getStorage() {
        return storage;
    }

    /** Yazilmakta olan klip(ler); izleme listesinden ve silmeden haric tutulur. */
    public Set<String> activeBases() {
        Set<String> out = new HashSet<>();
        if (recorder.isBusy()) {
            for (File f : recorder.getOutFiles()) {
                String b = DvrClipStore.baseOf(f);
                if (b != null) out.add(b);
            }
        }
        return out;
    }

    public long autoBytesPerMinute() {
        return getInternalMode().bytesPerMinute();
    }

    // ---------- Elle kayit ----------

    /** Elle USB kaydi. Arac ici otomatik mod acikken reddedilir. */
    public boolean startManualUsb(File usbRoot, int width, int height, boolean separate) {
        if (isAutoInternalEnabled() || recorder.isBusy() || usbRoot == null) {
            return false;
        }
        startRecorder(Source.MANUAL_USB, new File(usbRoot, USB_DIR_NAME), width, height, separate, 0);
        return true;
    }

    /**
     * Kaydi durdurur. byUser=true ve kayit otomatikse, otomatik kayit bu
     * kontak dongusu boyunca yeniden baslamaz.
     */
    public void stopRecording(boolean byUser) {
        if (!recorder.isRecording()) return;
        if (byUser && (source == Source.AUTO_INTERNAL || source == Source.AUTO_USB)) {
            suspended = true;
        }
        final Source src = source;
        recorder.stop(new RecorderCallbacks(src));
        updateAutoStatus();
        notifyState();
    }

    /** Kullanicinin durdurdugu otomatik kaydi bu kontak dongusunde yeniden etkinlestirir. */
    public void resumeAuto() {
        suspended = false;
        retryAfterMs = 0;
        tick();
        notifyState();
    }

    // ---------- Periyodik denetim ----------

    private void tick() {
        boolean ignition = isIgnitionOn();
        boolean busy = recorder.isBusy();
        if (busy) {
            if (source == Source.AUTO_INTERNAL) {
                if (!ignition || !isAutoInternalEnabled()) {
                    stopRecording(false);
                } else {
                    final Set<String> active = activeBases();
                    io.execute(() -> enforceInternalSpace(active));
                }
            } else if (source == Source.AUTO_USB) {
                if (!ignition || !isAutoUsbEnabled()) {
                    stopRecording(false);
                } else {
                    checkUsbWhileRecording();
                }
            }
        } else if (!starting && ignition && !suspended && cameraSupported
                && SystemClock.elapsedRealtime() >= retryAfterMs) {
            if (isAutoInternalEnabled()) {
                tryStartInternal();
            } else if (isAutoUsbEnabled()) {
                tryStartUsb();
            }
        }
        updateAutoStatus();
    }

    private void updateAutoStatus() {
        AutoStatus prev = autoStatus;
        boolean anyAuto = isAutoInternalEnabled() || isAutoUsbEnabled();
        if (!anyAuto) {
            autoStatus = AutoStatus.OFF;
        } else if (recorder.isRecording() && (source == Source.AUTO_INTERNAL || source == Source.AUTO_USB)) {
            autoStatus = AutoStatus.RECORDING;
        } else if (starting) {
            autoStatus = AutoStatus.STARTING;
        } else if (!cameraSupported) {
            autoStatus = AutoStatus.NO_CAMERA;
        } else if (suspended) {
            autoStatus = AutoStatus.SUSPENDED;
        } else if (!isIgnitionOn()) {
            autoStatus = AutoStatus.WAITING_IGNITION;
        } else if (autoStatus == AutoStatus.RECORDING || autoStatus == AutoStatus.STARTING) {
            autoStatus = AutoStatus.WAITING_IGNITION;
        }
        if (prev != autoStatus) notifyState();
    }

    private void setFailure(AutoStatus status, String message) {
        starting = false;
        autoStatus = status;
        lastError = message;
        retryAfterMs = SystemClock.elapsedRealtime() + RETRY_MS;
        Log.w(TAG, "otomatik kayit baslamadi: " + status + (message != null ? " " + message : ""));
        notifyState();
    }

    private void tryStartInternal() {
        starting = true;
        updateAutoStatus();
        io.execute(() -> {
            boolean writable = DvrClipStore.ensureWritable(internalDir);
            if (!writable) {
                refreshStorage();
                main.post(() -> setFailure(AutoStatus.NOT_WRITABLE, internalDir.getAbsolutePath()));
                return;
            }
            long need = RESERVE_BYTES + MIN_INTERNAL_START_MINUTES * autoBytesPerMinute();
            long free = DvrClipStore.freeUpTo(internalDir, need, null);
            refreshStorage();
            if (free < need) {
                main.post(() -> setFailure(AutoStatus.LOW_SPACE, null));
                return;
            }
            main.post(() -> {
                InternalMode mode = getInternalMode();
                startWhenCamerasReady(Source.AUTO_INTERNAL, internalDir,
                        mode.width, mode.height, mode.separate);
            });
        });
    }

    private void tryStartUsb() {
        File usb = findUsbRoot(app);
        if (usb == null) {
            autoStatus = AutoStatus.NO_USB;
            notifyState();
            return;
        }
        int[] wh = CameraRecorder.RESOLUTIONS[getUsbResIndex()];
        boolean sep = getUsbSeparate();
        long free = DvrClipStore.freeBytes(usb);
        if (CameraRecorder.estimateFitMinutes(free, wh[0], wh[1], sep) < MIN_USB_FIT_MINUTES) {
            setFailure(AutoStatus.USB_FULL, null);
            return;
        }
        starting = true;
        updateAutoStatus();
        startWhenCamerasReady(Source.AUTO_USB, new File(usb, USB_DIR_NAME), wh[0], wh[1], sep);
    }

    /** Hic kamera canli degilse otomatik kayit baslatilmaz (acilista kameralar gec gelebilir). */
    private void startWhenCamerasReady(Source src, File dir, int w, int h, boolean sep) {
        CameraHub hub = CameraHub.get();
        hub.acquire(HUB_TAG_PROBE, mask -> {
            boolean stillWanted = isIgnitionOn() && !suspended && !recorder.isBusy()
                    && (src == Source.AUTO_INTERNAL ? isAutoInternalEnabled() : isAutoUsbEnabled());
            if (!stillWanted) {
                starting = false;
                hub.release(HUB_TAG_PROBE);
                updateAutoStatus();
                return;
            }
            if (mask == 0) {
                hub.release(HUB_TAG_PROBE);
                setFailure(AutoStatus.NO_CAMERA, null);
                return;
            }
            startRecorder(src, dir, w, h, sep, SEGMENT_MS, src == Source.AUTO_INTERNAL);
            hub.release(HUB_TAG_PROBE);
        });
    }

    private void startRecorder(Source src, File dir, int w, int h, boolean sep, long segmentMs) {
        startRecorder(src, dir, w, h, sep, segmentMs, false);
    }

    private void startRecorder(Source src, File dir, int w, int h, boolean sep, long segmentMs,
                               boolean sequentialIds) {
        source = src;
        lastError = null;
        recorder.start(dir, new RecorderCallbacks(src), w, h, sep, segmentMs, sequentialIds);
        Log.i(TAG, "kayit istendi: " + src + " -> " + dir + " " + w + "x" + h + (sep ? " ayri" : " mozaik"));
    }

    private void checkUsbWhileRecording() {
        File usb = findUsbRoot(app);
        if (usb == null) {
            return; // yazma hatasi kaydi zaten bitirir
        }
        long free = DvrClipStore.freeBytes(usb);
        int[] wh = CameraRecorder.RESOLUTIONS[getUsbResIndex()];
        if (free >= 0 && CameraRecorder.estimateFitMinutes(free, wh[0], wh[1], getUsbSeparate()) < 1) {
            Log.w(TAG, "USB doldu, otomatik kayit durduruluyor");
            stopRecording(false);
            setFailure(AutoStatus.USB_FULL, null);
        }
    }

    // ---------- dvr-io ----------

    private void enforceInternalSpace(Set<String> active) {
        long headroom = HEADROOM_MINUTES * autoBytesPerMinute();
        long free = DvrClipStore.freeUpTo(internalDir, RESERVE_BYTES + headroom, active);
        refreshStorage(active);
        if (free >= 0 && free < RESERVE_BYTES + headroom / 2) {
            main.post(() -> {
                if (source == Source.AUTO_INTERNAL && recorder.isRecording()) {
                    Log.w(TAG, "rezerv korunamiyor, arac ici kayit durduruluyor");
                    stopRecording(false);
                    setFailure(AutoStatus.LOW_SPACE, null);
                }
            });
        }
    }

    public void refreshStorageAsync() {
        final Set<String> active = activeBases();
        io.execute(() -> refreshStorage(active));
    }

    private void refreshStorage() {
        refreshStorage(Collections.emptySet());
    }

    private void refreshStorage(Set<String> active) {
        StorageInfo info = new StorageInfo();
        if (!internalDir.exists()) internalDir.mkdirs();
        info.writable = internalDir.isDirectory() && internalDir.canWrite();
        File statDir = internalDir.exists() ? internalDir : internalDir.getParentFile();
        info.freeBytes = DvrClipStore.freeBytes(statDir);
        info.totalBytes = DvrClipStore.totalSpaceBytes(statDir);
        List<DvrClipStore.Clip> clips = DvrClipStore.list(internalDir, active);
        info.clipsBytes = DvrClipStore.totalBytes(clips);
        info.clipCount = clips.size();
        info.oldestClipMs = clips.isEmpty() ? 0 : clips.get(clips.size() - 1).timeMs;
        info.bytesPerMinute = autoBytesPerMinute();
        storage = info;
        main.post(this::notifyState);
    }

    // ---------- Klip islemleri (UI) ----------

    public interface ClipsCallback {
        void onClips(List<DvrClipStore.Clip> clips);
    }

    public void loadClips(ClipsCallback cb) {
        final Set<String> active = activeBases();
        io.execute(() -> {
            List<DvrClipStore.Clip> clips = DvrClipStore.list(internalDir, active);
            main.post(() -> cb.onClips(clips));
        });
    }

    public interface ExportCallback {
        void onProgress(int percent);

        void onDone(File targetDir, String error);
    }

    /** Kliplerin tum dosyalarini USB/MapControl/Klipler altina kopyalar. */
    public void exportClips(List<DvrClipStore.Clip> clips, ExportCallback cb) {
        File usb = findUsbRoot(app);
        if (usb == null) {
            main.post(() -> cb.onDone(null, "USB"));
            return;
        }
        final List<File> files = new ArrayList<>();
        for (DvrClipStore.Clip c : clips) files.addAll(c.files());
        new Thread(() -> {
            File target = new File(new File(usb, USB_DIR_NAME), USB_EXPORT_DIR_NAME);
            String error = null;
            try {
                if (!target.exists() && !target.mkdirs()) {
                    throw new java.io.IOException(target.getAbsolutePath());
                }
                long total = 1;
                for (File f : files) total += f.length();
                long done = 0;
                for (File src : files) {
                    File dst = new File(target, src.getName());
                    File tmp = new File(target, src.getName() + ".part");
                    try (FileChannel in = new FileInputStream(src).getChannel();
                         FileChannel out = new FileOutputStream(tmp).getChannel()) {
                        long size = in.size();
                        long pos = 0;
                        while (pos < size) {
                            pos += in.transferTo(pos, Math.min(8L << 20, size - pos), out);
                            final int pct = (int) ((done + pos) * 100 / total);
                            main.post(() -> cb.onProgress(Math.min(100, pct)));
                        }
                        out.force(true);
                        done += size;
                    }
                    if (dst.exists()) dst.delete();
                    if (!tmp.renameTo(dst)) {
                        throw new java.io.IOException(dst.getAbsolutePath());
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "aktarma hatasi", t);
                error = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            }
            final String err = error;
            main.post(() -> cb.onDone(target, err));
        }, "dvr-export").start();
    }

    public interface DeleteCallback {
        void onDeleted(int clipCount);
    }

    /** Klipleri siler; yazilmakta olan klip atlanir. */
    public void deleteClips(List<DvrClipStore.Clip> clips, DeleteCallback cb) {
        final List<DvrClipStore.Clip> copy = new ArrayList<>(clips);
        final Set<String> active = activeBases();
        io.execute(() -> {
            int n = 0;
            for (DvrClipStore.Clip c : copy) {
                if (active.contains(c.base)) continue;
                if (DvrClipStore.delete(c) > 0) n++;
            }
            Log.i(TAG, n + " klip kullanici tarafindan silindi");
            refreshStorage(active);
            final int deleted = n;
            main.post(() -> cb.onDeleted(deleted));
        });
    }

    // ---------- Yardimcilar ----------

    public static File findUsbRoot(Context context) {
        try {
            StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            if (sm != null) {
                for (StorageVolume v : sm.getStorageVolumes()) {
                    if (v.isRemovable()) {
                        File d = v.getDirectory();
                        if (d != null && d.exists()) {
                            return d;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            File[] dirs = context.getExternalFilesDirs(null);
            if (dirs != null && dirs.length > 1 && dirs[1] != null) {
                File d = dirs[1];
                for (int i = 0; i < 4 && d != null; i++) {
                    d = d.getParentFile();
                }
                if (d != null && d.exists()) {
                    return d;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String[] overlayLines() {
        String now = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new java.util.Date());
        int kmh = 0;
        String gear = "";
        Supplier<Integer> sp = speedSupplier;
        Supplier<String> gp = gearSupplier;
        try {
            if (sp != null) {
                Integer v = sp.get();
                if (v != null && v > 0) kmh = v;
            } else {
                kmh = readCarInt(ReadOnlyID.ID_SPEED_GAUGE_DISPLAY, 0, 400);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (gp != null) {
                String g = gp.get();
                if (g != null) gear = g;
            } else {
                int g = readCarInt(ReadOnlyID.ID_GEARBOX_STATE, 0, 20);
                if (g == 1) gear = "P";
                else if (g == 2) gear = "R";
                else if (g == 3) gear = "N";
                else if (g > 3) gear = "D" + (g - 3);
            }
        } catch (Throwable ignored) {
        }
        String line2 = kmh + " KM/SA";
        if (!gear.isEmpty()) {
            line2 += " VITES:" + gear;
        }
        return new String[]{now, line2};
    }

    /** UI yokken (servisten baslayan kayit) hiz/vites dogrudan arac verisinden okunur. */
    private static int readCarInt(int id, int min, int max) {
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        if (!proxy.isServiceConnnected()) return 0;
        int[] v = proxy.getItemValues(VDEventCarInfo.MODULE_READONLY_INFO, id);
        if (v == null || v.length == 0 || v[0] < min || v[0] > max) return 0;
        return v[0];
    }

    private void setRecordingService(boolean start) {
        try {
            Intent i = new Intent(app, RecordingService.class);
            if (start) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(i);
                } else {
                    app.startService(i);
                }
            } else {
                app.stopService(i);
            }
        } catch (Throwable ignored) {
        }
    }

    private void notifyState() {
        for (Listener l : listeners) {
            l.onStateChanged();
        }
    }

    /** Recorder geri cagrilari (main thread); kayit kaynagini tasir. */
    private final class RecorderCallbacks implements CameraRecorder.Listener {
        private final Source src;

        RecorderCallbacks(Source src) {
            this.src = src;
        }

        @Override
        public void onStarted(File file) {
            starting = false;
            setRecordingService(true);
            updateAutoStatus();
            for (Listener l : listeners) l.onStarted(src, file);
            notifyState();
        }

        @Override
        public void onStopped(File file, long elapsedMs) {
            if (!recorder.isBusy()) source = Source.NONE;
            setRecordingService(false);
            updateAutoStatus();
            for (Listener l : listeners) l.onStopped(src, file, elapsedMs);
            refreshStorageAsync();
            notifyState();
            // Mod degisimi gibi durdurmalarda otomatik kayit 10 sn beklemeden surer.
            if (src == Source.AUTO_INTERNAL || src == Source.AUTO_USB) tick();
        }

        @Override
        public void onError(String message) {
            starting = false;
            if (!recorder.isBusy()) source = Source.NONE;
            setRecordingService(false);
            if (src == Source.AUTO_INTERNAL || src == Source.AUTO_USB) {
                setFailure(AutoStatus.ERROR, message);
            }
            for (Listener l : listeners) l.onError(src, message);
            refreshStorageAsync();
            notifyState();
        }
    }
}
