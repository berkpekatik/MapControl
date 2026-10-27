package com.mapcontrol.media;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;

import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.client.bind.VDServiceDef;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carlan.VDEventCarLan;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDMusicID3Info;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Çalan parçanın başlık, yorumcu, albüm ve kapağını gösterge CarLan hattına yazar.
 * Metinler protokol alanlarına ayrı ayrı gider; kapak küçültülüp sınırlı boyutta basılır.
 */
public final class ClusterTrackRelay {

    private static final String TAG = "ClusterTrackRelay";

    /** Başka bir oynatıcıdan gelen parça. Gösterge bu etiketi bekler. */
    private static final int OUTSIDE_PLAYER = 5;
    private static final int TRACK_SLOT = 1;

    private static final int COVER_EDGE_PX = 192;
    private static final int COVER_JPEG_QUALITY = 70;
    private static final int COVER_BYTE_LIMIT = 48 * 1024;
    private static final int REMOTE_IMAGE_CAP = 512 * 1024;

    private static final long QUIET_MS = 200L;
    private static final long LINK_WAIT_MS = 2000L;
    private static final long LINK_STEP_MS = 100L;

    private static volatile ClusterTrackRelay installed;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "cluster-track");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final AtomicLong pullGeneration = new AtomicLong();

    private MediaSessionManager sessionManager;
    private MediaController activeController;
    private boolean sessionsHooked;
    private volatile boolean busOpened;
    private boolean laneReady;
    private int lastPlaybackCode = Integer.MIN_VALUE;

    private String lastSignature = "";
    private int lastCoverHash;

    private final Runnable pullRunnable = this::pullOnMain;

    private final Runnable accessReady = () -> mainHandler.post(this::attachSessions);
    private final Runnable accessLost = () -> mainHandler.post(this::dropSessions);

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            controllers -> mainHandler.post(() -> adopt(controllers));

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            schedulePull();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            int code = state == null ? -1 : state.getState();
            if (code == lastPlaybackCode) {
                return;
            }
            lastPlaybackCode = code;
            schedulePull();
        }

        @Override
        public void onSessionDestroyed() {
            mainHandler.post(ClusterTrackRelay.this::reloadSessions);
        }
    };

    private ClusterTrackRelay(Context appContext) {
        this.appContext = appContext;
    }

    public static void install(Context context) {
        if (installed != null) {
            return;
        }
        synchronized (ClusterTrackRelay.class) {
            if (installed != null) {
                return;
            }
            ClusterTrackRelay relay = new ClusterTrackRelay(context.getApplicationContext());
            relay.start();
            installed = relay;
        }
    }

    private void start() {
        sessionManager = (MediaSessionManager) appContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
        LauncherMediaNotificationListener.watchAccess(accessReady, accessLost);
        attachSessions();
        if (Looper.myLooper() == Looper.getMainLooper()) {
            openBusOnMain();
            worker.execute(this::warmLane);
            return;
        }
        mainHandler.post(() -> {
            openBusOnMain();
            worker.execute(this::warmLane);
        });
    }

    /**
     * VDBus kurucusu çağıran ipliğin Looper'ını ister.
     * cluster-track havuz ipliğinde açılırsa süreç kapanır.
     */
    private void openBusOnMain() {
        if (busOpened) {
            return;
        }
        try {
            VDBus.getDefault().init(appContext);
            busOpened = true;
        } catch (RuntimeException e) {
            Log.w(TAG, "VDBus init başarısız", e);
        }
    }

    private void attachSessions() {
        if (sessionManager == null || sessionsHooked) {
            reloadSessions();
            return;
        }
        if (!LauncherMediaController.isNotificationAccessEnabled(appContext)) {
            return;
        }
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent());
            sessionsHooked = true;
        } catch (SecurityException e) {
            Log.w(TAG, "Oturum listesi izni yok");
            return;
        }
        reloadSessions();
    }

    private void dropSessions() {
        detachController();
        if (sessionManager != null && sessionsHooked) {
            try {
                sessionManager.removeOnActiveSessionsChangedListener(sessionsListener);
            } catch (Exception ignored) {
                // dinleyici zaten düşmüş olabilir
            }
        }
        sessionsHooked = false;
        schedulePull();
    }

    private void reloadSessions() {
        if (!sessionsHooked || sessionManager == null) {
            adopt(null);
            return;
        }
        List<MediaController> controllers;
        try {
            controllers = sessionManager.getActiveSessions(listenerComponent());
        } catch (SecurityException e) {
            adopt(null);
            return;
        }
        adopt(controllers);
    }

    private void adopt(@Nullable List<MediaController> controllers) {
        MediaController next = chooseController(controllers);
        if (next == activeController) {
            schedulePull();
            return;
        }
        detachController();
        lastPlaybackCode = Integer.MIN_VALUE;
        activeController = next;
        if (activeController != null) {
            activeController.registerCallback(controllerCallback, mainHandler);
        }
        schedulePull();
    }

    private void detachController() {
        if (activeController != null) {
            activeController.unregisterCallback(controllerCallback);
            activeController = null;
        }
    }

    private void schedulePull() {
        mainHandler.removeCallbacks(pullRunnable);
        mainHandler.postDelayed(pullRunnable, QUIET_MS);
    }

    private void pullOnMain() {
        MediaController controller = activeController;
        TrackDraft draft = controller == null ? TrackDraft.idle() : TrackDraft.capture(controller);
        long token = pullGeneration.incrementAndGet();
        worker.execute(() -> {
            if (token != pullGeneration.get()) {
                draft.release();
                return;
            }
            try {
                dispatch(draft);
            } finally {
                draft.release();
            }
        });
    }

    private void dispatch(TrackDraft draft) {
        if (!draft.hasText()) {
            if (draft.idle && !lastSignature.isEmpty()) {
                if (push(draft, null, true)) {
                    lastSignature = "";
                    lastCoverHash = 0;
                }
            }
            return;
        }
        byte[] cover = encodeCover(draft);
        int coverHash = cover == null ? 0 : Arrays.hashCode(cover);
        String signature = draft.playerId + "\n" + draft.trackTitle + "\n"
                + draft.performer + "\n" + draft.collection + "\n" + coverHash;
        if (signature.equals(lastSignature)) {
            return;
        }
        boolean coverChanged = coverHash != lastCoverHash;
        if (push(draft, cover, coverChanged)) {
            lastSignature = signature;
            lastCoverHash = coverHash;
        }
    }

    private boolean push(TrackDraft draft, @Nullable byte[] cover, boolean coverChanged) {
        if (!ensureLane()) {
            return false;
        }
        VDMusicID3Info packet = new VDMusicID3Info();
        packet.setOrigin(OUTSIDE_PLAYER);
        packet.setSongIndex(TRACK_SLOT);
        packet.setSongName(draft.trackTitle);
        packet.setSongArtist(draft.performer);
        packet.setSongAlbum(draft.collection);
        packet.setSongSchools("");
        packet.setSongAges("");
        packet.setIsChangeAlbumPic(coverChanged ? "true" : "false");
        packet.setSongAlbumPicture(cover == null || cover.length == 0
                ? ""
                : Base64.encodeToString(cover, Base64.NO_WRAP));
        try {
            VDEvent event = VDMusicID3Info.createEvent(VDEventCarLan.MUSIC_ID3_INFO, packet);
            VDBus.getDefault().set(event);
            laneReady = true;
            Log.i(TAG, "Gösterge güncellendi: " + draft.playerId
                    + " title=" + draft.trackTitle.length()
                    + " cover=" + (cover == null ? 0 : cover.length));
            return true;
        } catch (RuntimeException e) {
            laneReady = false;
            Log.w(TAG, "Gösterge yazımı başarısız", e);
            return false;
        }
    }

    private void warmLane() {
        ensureLane();
    }

    private boolean ensureLane() {
        if (laneReady) {
            return true;
        }
        if (!busOpened) {
            return false;
        }
        VDBus bus;
        try {
            bus = VDBus.getDefault();
        } catch (RuntimeException e) {
            Log.w(TAG, "VDBus açılamadı", e);
            return false;
        }
        VDServiceDef.ServiceType lane = VDServiceDef.ServiceType.CAR_LAN;
        if (!bus.isServiceConnected(lane)) {
            try {
                bus.bindService(lane);
            } catch (RuntimeException e) {
                Log.w(TAG, "CarLan bind başarısız", e);
                return false;
            }
            long deadline = System.currentTimeMillis() + LINK_WAIT_MS;
            while (System.currentTimeMillis() < deadline && !bus.isServiceConnected(lane)) {
                try {
                    Thread.sleep(LINK_STEP_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        laneReady = bus.isServiceConnected(lane);
        return laneReady;
    }

    @Nullable
    private static MediaController chooseController(@Nullable List<MediaController> controllers) {
        if (controllers == null || controllers.isEmpty()) {
            return null;
        }
        MediaController playingWithoutText = null;
        MediaController withText = null;
        for (MediaController controller : controllers) {
            PlaybackState state = controller.getPlaybackState();
            boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
            boolean titled = hasText(controller);
            if (playing && titled) {
                return controller;
            }
            if (playing && playingWithoutText == null) {
                playingWithoutText = controller;
            }
            if (withText == null && titled) {
                withText = controller;
            }
        }
        return playingWithoutText != null ? playingWithoutText : withText;
    }

    @Nullable
    private byte[] encodeCover(TrackDraft draft) {
        Bitmap source = draft.cover;
        boolean owned = false;
        if (source == null && draft.coverUri != null) {
            source = loadLocalImage(draft.coverUri);
            owned = source != null;
        }
        if (source == null) {
            return null;
        }
        Bitmap fitted = null;
        try {
            fitted = fitDown(source, COVER_EDGE_PX);
            Bitmap toCompress = fitted != null ? fitted : source;
            byte[] jpeg = compress(toCompress, COVER_JPEG_QUALITY);
            if (jpeg != null && jpeg.length > COVER_BYTE_LIMIT) {
                jpeg = compress(toCompress, 45);
            }
            if (jpeg != null && jpeg.length > COVER_BYTE_LIMIT) {
                return null;
            }
            return jpeg;
        } finally {
            if (fitted != null && fitted != source) {
                fitted.recycle();
            }
            if (owned) {
                source.recycle();
            }
        }
    }

    @Nullable
    private static byte[] compress(Bitmap bitmap, int quality) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                return null;
            }
            return stream.toByteArray();
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static Bitmap fitDown(Bitmap source, int edge) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }
        int longest = Math.max(width, height);
        if (longest <= edge) {
            return null;
        }
        float scale = edge / (float) longest;
        int targetW = Math.max(1, Math.round(width * scale));
        int targetH = Math.max(1, Math.round(height * scale));
        try {
            return Bitmap.createScaledBitmap(source, targetW, targetH, true);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private Bitmap loadLocalImage(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equals("content") || scheme.equals("file") || scheme.equals("android.resource"))) {
            return null;
        }
        try (InputStream in = appContext.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            byte[] raw = readCapped(in, REMOTE_IMAGE_CAP);
            if (raw == null) {
                return null;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(raw, 0, raw.length, bounds);
            BitmapFactory.Options sample = new BitmapFactory.Options();
            sample.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, COVER_EDGE_PX * 2);
            return BitmapFactory.decodeByteArray(raw, 0, raw.length, sample);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static byte[] readCapped(InputStream in, int cap) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > cap) {
                return null;
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static int sampleSize(int width, int height, int edge) {
        int size = 1;
        int longest = Math.max(width, height);
        while (longest / size > edge) {
            size *= 2;
        }
        return size;
    }

    private ComponentName listenerComponent() {
        return new ComponentName(appContext, LauncherMediaNotificationListener.class);
    }

    private static boolean hasText(MediaController controller) {
        MediaMetadata metadata = controller.getMetadata();
        if (metadata == null) {
            return false;
        }
        if (!text(metadata, MediaMetadata.METADATA_KEY_TITLE).isEmpty()) {
            return true;
        }
        return !text(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE).isEmpty();
    }

    private static String text(@Nullable MediaMetadata metadata, String key) {
        if (metadata == null) {
            return "";
        }
        String value = metadata.getString(key);
        return value == null ? "" : value.trim();
    }

    private static final class TrackDraft {
        final boolean idle;
        final String playerId;
        final String trackTitle;
        final String performer;
        final String collection;
        @Nullable final Bitmap cover;
        @Nullable final Uri coverUri;

        private TrackDraft(
                boolean idle,
                String playerId,
                String trackTitle,
                String performer,
                String collection,
                @Nullable Bitmap cover,
                @Nullable Uri coverUri) {
            this.idle = idle;
            this.playerId = playerId;
            this.trackTitle = trackTitle;
            this.performer = performer;
            this.collection = collection;
            this.cover = cover;
            this.coverUri = coverUri;
        }

        static TrackDraft idle() {
            return new TrackDraft(true, "", "", "", "", null, null);
        }

        static TrackDraft capture(MediaController controller) {
            MediaMetadata metadata = controller.getMetadata();
            String title = text(metadata, MediaMetadata.METADATA_KEY_TITLE);
            if (title.isEmpty()) {
                title = text(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            }
            String performer = text(metadata, MediaMetadata.METADATA_KEY_ARTIST);
            if (performer.isEmpty()) {
                performer = text(metadata, MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            }
            String collection = text(metadata, MediaMetadata.METADATA_KEY_ALBUM);
            Bitmap cover = copyBitmap(firstBitmap(metadata));
            Uri coverUri = cover == null ? firstArtUri(metadata) : null;
            String playerId = controller.getPackageName() == null ? "" : controller.getPackageName();
            return new TrackDraft(false, playerId, title, performer, collection, cover, coverUri);
        }

        boolean hasText() {
            return !trackTitle.isEmpty() || !performer.isEmpty();
        }

        void release() {
            if (cover != null && !cover.isRecycled()) {
                cover.recycle();
            }
        }

        @Nullable
        private static Bitmap firstBitmap(@Nullable MediaMetadata metadata) {
            if (metadata == null) {
                return null;
            }
            Bitmap bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (bitmap == null) {
                bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            }
            if (bitmap == null) {
                bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            }
            return bitmap;
        }

        @Nullable
        private static Bitmap copyBitmap(@Nullable Bitmap source) {
            if (source == null || source.isRecycled()) {
                return null;
            }
            try {
                Bitmap.Config config = source.getConfig() != null
                        ? source.getConfig()
                        : Bitmap.Config.ARGB_8888;
                return source.copy(config, false);
            } catch (RuntimeException e) {
                return null;
            }
        }

        @Nullable
        private static Uri firstArtUri(@Nullable MediaMetadata metadata) {
            if (metadata == null) {
                return null;
            }
            Uri uri = parseUri(metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI));
            if (uri == null) {
                uri = parseUri(metadata.getString(MediaMetadata.METADATA_KEY_ART_URI));
            }
            if (uri == null) {
                uri = parseUri(metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI));
            }
            if (uri == null && metadata.getDescription() != null) {
                uri = metadata.getDescription().getIconUri();
            }
            return uri;
        }

        @Nullable
        private static Uri parseUri(@Nullable String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return null;
            }
            try {
                return Uri.parse(raw.trim());
            } catch (Exception e) {
                return null;
            }
        }
    }
}
