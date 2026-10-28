package com.mapcontrol.dvr;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Kayit: varsayilan tek MP4 mozaik; istenirse 4 kamera ayri MP4 dosyalarina.
 * Cozunurluk secilebilir (1280x800 / 960x600 / 640x400); bitrate piksel
 * sayisiyla orantili olceklenir, ayri kayitta toplam boyut x4 olur.
 *
 * Birincil yol: {@link CameraGlEngine} mozaigi/yaziyi dogrudan encoder giris
 * yuzeyine cizer (CPU donusumu yok). Yuzey yolu kurulamazsa CPU yoluna duser:
 * hub'in kare kopyalari native olarak I420'ye cevrilip codec giris buffer'ina yazilir.
 * Kareler {@link CameraHub} uzerinden gelir; kayit kendi grab thread'ini acmaz.
 */
public class CameraRecorder {
    private static final String TAG = "CameraRecorder";
    private static final String HUB_TAG = "recorder";
    public static final int WIDTH = 1280;
    public static final int HEIGHT = 800;
    public static final int FPS = 25;
    public static final int BITRATE = 8_000_000; // 1280x800 tek akis: ~60 MB/dk

    /** Kayit cozunurluk secenekleri {genislik, yukseklik}. */
    public static final int[][] RESOLUTIONS = {{1280, 800}, {960, 600}, {640, 400}};
    /** Kamera sirasiyla video etiketleri (0=On,1=Sag,2=Sol,3=Arka). */
    public static final String[] CAM_SUFFIX = {"ON", "SAG", "SOL", "ARKA"};
    /** Ayri kayitta dosya oneki: aci harfi + "_m_" (f_m_, r_m_, l_m_, b_m_). */
    public static final String[] CAM_FILE_TAG = {"f", "r", "l", "b"};
    public static final String MULTI_TAG = "_m_";
    /** Tek dosya (mozaik) kayit oneki. */
    public static final String SINGLE_PREFIX = "s_";
    public static final String FILE_TIME_PATTERN = "yyyy-MM-dd_HH-mm-ss";
    /** Siralı klip id'si eki: zaman + "_id_N" (arac ici kayit). */
    public static final String ID_TAG = "_id_";
    private static final long OVERLAY_REFRESH_MS = 250;
    private static final long EOS_TIMEOUT_MS = 3000;

    /** Kayda gomulecek satirlar: [tarih-saat, hiz]. Null donerse yazi yazilmaz. */
    public interface OverlayProvider {
        String[] getLines();
    }

    private volatile OverlayProvider overlayProvider;

    public void setOverlayProvider(OverlayProvider provider) {
        this.overlayProvider = provider;
    }

    public interface Listener {
        void onStarted(File file);
        void onStopped(File file, long elapsedMs);
        void onError(String message);
    }

    private final CameraHub hub = CameraHub.get();
    private final CameraGlEngine engine = CameraGlEngine.get();
    private Thread worker;
    private volatile boolean recording;
    private volatile boolean stopping;

    // Aktif kayit yapilandirmasi (start'ta sabitlenir, kayit boyunca degismez)
    private int recWidth = WIDTH;
    private int recHeight = HEIGHT;
    private boolean separateMode;
    private File outDir;
    /** 0: tek dosya; >0: bu surede bir sonraki anahtar karede yeni dosyaya gecilir. */
    private long segmentMs;
    /** true ise her klip adina DvrClipStore.nextId ile artan "_id_N" eklenir. */
    private boolean sequentialIds;
    private long lastClipId;
    private final List<File> outFiles = new ArrayList<>();
    /** Kapanmis segmentlerin toplam boyutu (getTotalBytes icin). */
    private volatile long closedBytes;
    private long segmentStartMs;
    private List<File> pendingFiles;
    private int rotationsPending;

    /** Kayit veya kapanma suruyorsa true. */
    public boolean isBusy() {
        return recording || stopping;
    }

    private File outFile;
    private long startMs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public boolean isRecording() {
        return recording;
    }

    public boolean isSeparateMode() {
        return separateMode;
    }

    public File getOutFile() {
        return outFile;
    }

    public List<File> getOutFiles() {
        synchronized (outFiles) {
            return new ArrayList<>(outFiles);
        }
    }

    /** Bu kaydin tum dosyalarinin toplam boyutu (kapanmis segmentler dahil). */
    public long getTotalBytes() {
        long total = closedBytes;
        synchronized (outFiles) {
            for (File f : outFiles) {
                if (f != null) total += f.length();
            }
        }
        return total;
    }

    public long getElapsedMs() {
        return recording ? System.currentTimeMillis() - startMs : 0;
    }

    /** Tek akis icin bitrate: 1280x800'de 8 Mbps, piksel oraniyla olceklenir. */
    public static int bitrateFor(int width, int height) {
        long br = (long) BITRATE * width * height / ((long) WIDTH * HEIGHT);
        br = Math.max(400_000L, (br / 100_000L) * 100_000L);
        return (int) br;
    }

    /** Yapilandirmaya gore dakikada yaklasik toplam boyut (bayt). */
    public static long bytesPerMinute(int width, int height, boolean separate) {
        long perStream = bitrateFor(width, height) / 8L * 60L;
        return separate ? perStream * 4L : perStream;
    }

    public static long estimateFitMinutes(long freeBytes, int width, int height, boolean separate) {
        long bpm = bytesPerMinute(width, height, separate);
        return bpm > 0 ? freeBytes / bpm : 0;
    }

    public static long estimateFitMinutes(long freeBytes) {
        return estimateFitMinutes(freeBytes, WIDTH, HEIGHT, false);
    }

    public void start(File outDir, Listener listener) {
        start(outDir, listener, WIDTH, HEIGHT, false);
    }

    public void start(File outDir, Listener listener, int width, int height, boolean separate) {
        start(outDir, listener, width, height, separate, 0);
    }

    /**
     * @param segmentMs 0 ise tek dosya; aksi halde kayit kesilmeden bu surelik
     *                  kliplere bolunur (her klip bir anahtar kareyle baslar).
     */
    public void start(File outDir, Listener listener, int width, int height, boolean separate,
                      long segmentMs) {
        start(outDir, listener, width, height, separate, segmentMs, false);
    }

    /**
     * @param sequentialIds true ise klip adlarina klasordeki son id'den devam eden "_id_N"
     *                      eklenir (klasorde id'li klip yoksa 1'den baslar).
     */
    public void start(File outDir, Listener listener, int width, int height, boolean separate,
                      long segmentMs, boolean sequentialIds) {
        if (recording || stopping) {
            return;
        }
        if (!outDir.exists() && !outDir.mkdirs()) {
            postError(listener, "Klasor olusturulamadi: " + outDir.getAbsolutePath());
            return;
        }
        recWidth = width;
        recHeight = height;
        separateMode = separate;
        this.outDir = outDir;
        this.segmentMs = Math.max(0, segmentMs);
        this.sequentialIds = sequentialIds;
        lastClipId = 0;
        closedBytes = 0;
        pendingFiles = null;
        rotationsPending = 0;
        List<File> files = buildFiles(new Date());
        synchronized (outFiles) {
            outFiles.clear();
            outFiles.addAll(files);
        }
        outFile = files.get(0);
        recording = true;
        startMs = System.currentTimeMillis();
        segmentStartMs = SystemClock.elapsedRealtime();
        worker = new Thread(() -> runRecording(listener), "dvr-record");
        worker.start();
    }

    private List<File> buildFiles(Date when) {
        String time = new SimpleDateFormat(FILE_TIME_PATTERN, Locale.US).format(when);
        if (sequentialIds) {
            lastClipId = Math.max(DvrClipStore.nextId(outDir), lastClipId + 1);
            time += ID_TAG + lastClipId;
        }
        List<File> files = new ArrayList<>();
        if (separateMode) {
            for (String tag : CAM_FILE_TAG) {
                files.add(new File(outDir, tag + MULTI_TAG + time + ".mp4"));
            }
        } else {
            files.add(new File(outDir, SINGLE_PREFIX + time + ".mp4"));
        }
        return files;
    }

    /** Kaydi durdurur; dosya kapaninca listener cagrilir. */
    public void stop(Listener listener) {
        if (!recording || stopping) {
            return;
        }
        stopping = true;
        long elapsed = System.currentTimeMillis() - startMs;
        recording = false;
        final Thread w = worker;
        new Thread(() -> {
            if (w != null) {
                try {
                    w.join(10_000);
                } catch (InterruptedException ignored) {
                }
            }
            stopping = false;
            if (listener != null) {
                mainHandler.post(() -> listener.onStopped(outFile, elapsed));
            }
        }, "dvr-stop").start();
    }

    // ---------- Kayit thread'i ----------

    private static class Sink {
        MediaCodec codec;
        MediaMuxer muxer;
        File file;
        Surface inputSurface;
        ByteBuffer frame;
        MediaFormat outFormat;
        int trackIndex = -1;
        boolean muxerStarted;
        boolean eosQueued;
        boolean eosDone;
        /** Segment degisiminde ilk anahtar karede gecilecek dosya. */
        File pendingFile;
        /** Dosyadaki ilk ornegin zamani; her dosya 0'dan baslasin diye cikarilir. */
        long ptsBaseUs = -1;
    }

    private void runRecording(Listener listener) {
        hub.acquire(HUB_TAG, null);
        List<Sink> sinks = new ArrayList<>();
        boolean glPath = false;
        try {
            List<File> files = getOutFiles();
            try {
                for (File f : files) {
                    sinks.add(createSink(f, true));
                }
                Surface[] surfaces = new Surface[sinks.size()];
                for (int i = 0; i < surfaces.length; i++) {
                    surfaces[i] = sinks.get(i).inputSurface;
                }
                glPath = engine.startRecording(new CameraGlEngine.RecordConfig(surfaces,
                        recWidth, recHeight, separateMode, CAM_SUFFIX, overlayProvider));
            } catch (Exception e) {
                Log.w(TAG, "yuzey encoder kurulamadi", e);
            }
            if (!glPath) {
                Log.w(TAG, "GL kayit yolu yok, CPU yoluna geciliyor");
                releaseSinks(sinks);
                sinks.clear();
                for (File f : files) {
                    sinks.add(createSink(f, false));
                }
            }
            Log.i(TAG, "kayit basladi (" + (glPath ? "GL yuzey" : "CPU") + ", "
                    + recWidth + "x" + recHeight + (separateMode ? ", ayri" : ", mozaik") + ")");
            if (listener != null) {
                mainHandler.post(() -> listener.onStarted(outFile));
            }
            if (glPath) {
                runSurfaceLoop(sinks);
            } else {
                runBufferLoop(sinks);
            }
        } catch (Exception e) {
            Log.w(TAG, "kayit hata", e);
            recording = false; // or. USB cikarildi: yazma hatasi kaydi bitirir
            postError(listener, "Kayit hatasi: " + e.getMessage());
        } finally {
            if (glPath) {
                engine.stopRecording();
            }
            releaseSinks(sinks);
            hub.clearDemand(HUB_TAG);
            hub.release(HUB_TAG);
        }
    }

    private Sink createSink(File file, boolean surfaceInput) throws Exception {
        Sink sink = new Sink();
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, recWidth, recHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, surfaceInput
                ? MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                : MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrateFor(recWidth, recHeight));
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        try {
            sink.codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            sink.codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            if (surfaceInput) {
                sink.inputSurface = sink.codec.createInputSurface();
            } else {
                sink.frame = ByteBuffer.allocateDirect(recWidth * recHeight * 3 / 2);
            }
            sink.codec.start();
            sink.file = file;
            sink.muxer = new MediaMuxer(file.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (Exception e) {
            releaseSink(sink);
            throw e;
        }
        return sink;
    }

    private static void releaseSinks(List<Sink> sinks) {
        for (Sink sink : sinks) {
            releaseSink(sink);
        }
    }

    private static void releaseSink(Sink sink) {
        try {
            if (sink.codec != null) {
                sink.codec.stop();
            }
        } catch (Exception ignored) {
        }
        try {
            if (sink.codec != null) {
                sink.codec.release();
            }
        } catch (Exception ignored) {
        }
        sink.codec = null;
        if (sink.inputSurface != null) {
            sink.inputSurface.release();
            sink.inputSurface = null;
        }
        try {
            if (sink.muxer != null) {
                if (sink.muxerStarted) sink.muxer.stop();
                sink.muxer.release();
            }
        } catch (Exception ignored) {
        }
        sink.muxer = null;
        sink.muxerStarted = false;
    }

    /** Bir sink'in hazir cikislarini muxer'a bosaltir. true: EOS tamamlandi. */
    private boolean drainSink(Sink sink, MediaCodec.BufferInfo info, long timeoutUs) {
        int outIdx = sink.codec.dequeueOutputBuffer(info, timeoutUs);
        if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            sink.outFormat = sink.codec.getOutputFormat();
            if (!sink.muxerStarted) {
                sink.trackIndex = sink.muxer.addTrack(sink.outFormat);
                sink.muxer.start();
                sink.muxerStarted = true;
            }
        } else if (outIdx >= 0) {
            ByteBuffer out = sink.codec.getOutputBuffer(outIdx);
            boolean config = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
            boolean key = (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
            if (sink.pendingFile != null && key && !config && info.size > 0 && sink.outFormat != null) {
                switchMuxer(sink);
            }
            if (out != null && info.size > 0 && sink.muxerStarted && !config) {
                if (sink.ptsBaseUs < 0) {
                    sink.ptsBaseUs = info.presentationTimeUs;
                }
                info.presentationTimeUs = Math.max(0, info.presentationTimeUs - sink.ptsBaseUs);
                out.position(info.offset);
                out.limit(info.offset + info.size);
                sink.muxer.writeSampleData(sink.trackIndex, out, info);
            }
            sink.codec.releaseOutputBuffer(outIdx, false);
            return (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
        }
        return false;
    }

    /** Segment suresi dolduysa tum encoder'lardan anahtar kare ister; gecis drainSink'te olur. */
    private void maybeStartRotation(List<Sink> sinks) {
        if (segmentMs <= 0 || rotationsPending > 0
                || SystemClock.elapsedRealtime() - segmentStartMs < segmentMs) {
            return;
        }
        segmentStartMs = SystemClock.elapsedRealtime();
        List<File> next = buildFiles(new Date());
        pendingFiles = next;
        rotationsPending = sinks.size();
        android.os.Bundle sync = new android.os.Bundle();
        sync.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
        for (int i = 0; i < sinks.size(); i++) {
            Sink sink = sinks.get(i);
            sink.pendingFile = next.get(i);
            try {
                sink.codec.setParameters(sync);
            } catch (Exception ignored) {
                // Istek desteklenmezse periyodik anahtar kare (2 sn) bekleyecegiz.
            }
        }
    }

    private void switchMuxer(Sink sink) {
        File next = sink.pendingFile;
        sink.pendingFile = null;
        File closed = sink.file;
        try {
            if (sink.muxerStarted) sink.muxer.stop();
        } catch (Exception e) {
            Log.w(TAG, "segment kapatilamadi: " + closed, e);
        }
        try {
            sink.muxer.release();
        } catch (Exception ignored) {
        }
        sink.muxer = null;
        sink.muxerStarted = false;
        if (closed != null) {
            closedBytes += closed.length();
        }
        try {
            sink.muxer = new MediaMuxer(next.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Yeni klip acilamadi: " + next.getName(), e);
        }
        sink.file = next;
        sink.trackIndex = sink.muxer.addTrack(sink.outFormat);
        sink.muxer.start();
        sink.muxerStarted = true;
        sink.ptsBaseUs = -1;
        if (--rotationsPending <= 0) {
            List<File> files = pendingFiles;
            pendingFiles = null;
            if (files != null) {
                synchronized (outFiles) {
                    outFiles.clear();
                    outFiles.addAll(files);
                }
                outFile = files.get(0);
            }
        }
    }

    /** GL yolu: motor yuzeylere cizer, burada sadece cikis muxer'a yazilir. */
    private void runSurfaceLoop(List<Sink> sinks) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long timeoutUs = sinks.size() > 1 ? 2_500 : 10_000;
        while (recording) {
            maybeStartRotation(sinks);
            for (Sink sink : sinks) {
                drainSink(sink, info, timeoutUs);
            }
        }
        // Yeni kare yazilmasin, sonra EOS
        engine.stopRecording();
        for (Sink sink : sinks) {
            try {
                sink.codec.signalEndOfInputStream();
                sink.eosQueued = true;
            } catch (Exception e) {
                sink.eosDone = true;
            }
        }
        drainUntilEos(sinks, info);
    }

    /**
     * Kamera basina kopya talebi: ayri kayit kaynagin yarisindan yuksekse tam kare,
     * degilse yarim. Kamera yeniden acilinca geometri degisebilir; yalnizca degiseni gonderir.
     */
    private void updateCopyDemand(int[] last) {
        for (int cam = 0; cam < CameraHub.CAM_COUNT; cam++) {
            int mode = separateMode && recHeight > hub.getFrameHeight(cam) / 2
                    ? HabNative.COPY_FULL : HabNative.COPY_HALF;
            if (last[cam] != mode) {
                hub.setDemand(HUB_TAG, cam, mode, false);
                last[cam] = mode;
            }
        }
    }

    /** CPU yolu: hub karelerini native olarak I420'ye cevirip codec giris buffer'ina yazar. */
    private void runBufferLoop(List<Sink> sinks) {
        final int[] copyModes = new int[CameraHub.CAM_COUNT];
        updateCopyDemand(copyModes);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        final int frameBytes = recWidth * recHeight * 3 / 2;
        final int[] inIdx = new int[sinks.size()];
        final ByteBuffer[] targets = new ByteBuffer[sinks.size()];
        String[] lines = null;
        long lastOverlayMs = 0;
        long frameIndex = 0;
        long nextFrameUs = System.nanoTime() / 1000;
        while (recording) {
            long nowUs = System.nanoTime() / 1000;
            if (nowUs >= nextFrameUs) {
                long nowMs = SystemClock.uptimeMillis();
                if (nowMs - lastOverlayMs >= OVERLAY_REFRESH_MS) {
                    lastOverlayMs = nowMs;
                    OverlayProvider op = overlayProvider;
                    try {
                        lines = op != null ? op.getLines() : null;
                    } catch (Throwable ignored) {
                        lines = null;
                    }
                }
                final String line1 = lines != null && lines.length > 0 ? lines[0] : null;
                final String line2 = lines != null && lines.length > 1 ? lines[1] : null;
                // Giris buffer'lari kilit disinda alinir; hub kilidi yalnizca donusum suresince tutulur.
                for (int i = 0; i < sinks.size(); i++) {
                    Sink sink = sinks.get(i);
                    inIdx[i] = sink.codec.dequeueInputBuffer(10_000);
                    targets[i] = null;
                    if (inIdx[i] >= 0) {
                        ByteBuffer in = sink.codec.getInputBuffer(inIdx[i]);
                        if (in != null) {
                            in.clear();
                            targets[i] = in.isDirect() && in.capacity() >= frameBytes ? in : sink.frame;
                        }
                    }
                }
                updateCopyDemand(copyModes);
                hub.readFrames((frames, widths, heights) -> {
                    if (separateMode) {
                        for (int i = 0; i < targets.length; i++) {
                            if (targets[i] == null) continue;
                            HabNative.convertSingle(frames[i], widths[i], heights[i], targets[i],
                                    recWidth, recHeight, CAM_SUFFIX[i], line1, line2);
                        }
                    } else if (targets[0] != null) {
                        HabNative.composeMosaic(frames, widths, heights, targets[0],
                                recWidth, recHeight, line1, line2);
                    }
                });
                long ptsUs = frameIndex * 1_000_000L / FPS;
                for (int i = 0; i < sinks.size(); i++) {
                    if (inIdx[i] < 0) continue;
                    Sink sink = sinks.get(i);
                    ByteBuffer in = sink.codec.getInputBuffer(inIdx[i]);
                    int size = 0;
                    if (in != null && targets[i] != null) {
                        if (targets[i] == sink.frame) {
                            in.clear();
                            ByteBuffer src = sink.frame.duplicate();
                            src.position(0);
                            src.limit(Math.min(in.capacity(), frameBytes));
                            in.put(src);
                        }
                        size = Math.min(in.capacity(), frameBytes);
                    }
                    sink.codec.queueInputBuffer(inIdx[i], 0, size, ptsUs, 0);
                }
                frameIndex++;
                nextFrameUs += 1_000_000L / FPS;
                if (nextFrameUs < nowUs - 1_000_000L) {
                    nextFrameUs = nowUs; // cok geri kaldik, tempo sifirla
                }
            }
            maybeStartRotation(sinks);
            for (Sink sink : sinks) {
                drainSink(sink, info, sinks.size() > 1 ? 2_000 : 10_000);
            }
        }
        hub.clearDemand(HUB_TAG);
        if (frameIndex == 0) {
            return; // hic kare yok, EOS cikisi beklenmez
        }
        long ptsUs = frameIndex * 1_000_000L / FPS;
        long deadline = SystemClock.uptimeMillis() + EOS_TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            boolean allQueued = true;
            for (Sink sink : sinks) {
                if (sink.eosQueued) continue;
                int idx = sink.codec.dequeueInputBuffer(50_000);
                if (idx >= 0) {
                    sink.codec.queueInputBuffer(idx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    sink.eosQueued = true;
                } else {
                    allQueued = false;
                }
                drainSink(sink, info, 2_000);
            }
            if (allQueued) break;
        }
        drainUntilEos(sinks, info);
    }

    private void drainUntilEos(List<Sink> sinks, MediaCodec.BufferInfo info) {
        long deadline = SystemClock.uptimeMillis() + EOS_TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            boolean all = true;
            for (Sink sink : sinks) {
                if (sink.eosDone || !sink.eosQueued) continue;
                if (drainSink(sink, info, 10_000)) {
                    sink.eosDone = true;
                } else {
                    all = false;
                }
            }
            if (all) return;
        }
        Log.w(TAG, "EOS zaman asimi; dosyalar mevcut haliyle kapatiliyor");
    }

    private void postError(Listener listener, String msg) {
        if (listener != null) {
            mainHandler.post(() -> listener.onError(msg));
        }
    }
}
