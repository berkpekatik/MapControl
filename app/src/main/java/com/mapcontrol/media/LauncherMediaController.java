package com.mapcontrol.media;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Aktif medya oturumunu dinler ve launcher dashboard için oynatma bilgisi sunar.
 * <p>
 * Android, üçüncü parti uygulamalara oturum listesini yalnızca
 * {@link LauncherMediaNotificationListener} etkinse (Bildirim erişimi) verir.
 * {@code MEDIA_CONTENT_CONTROL} imza/sistem iznidir; sideload APK'da genelde yoktur.
 */
public final class LauncherMediaController {

    /** Progress bar yumuşak ilerlesin diye sık tick. */
    private static final long PROGRESS_TICK_MS = 50L;

    public interface Listener {
        void onMediaStateChanged(MediaState state);
    }

    public static final class MediaState {
        public final boolean sessionAvailable;
        /** Bildirim dinleyici ayarı açık mı — kapalıysa oturum okunamaz. */
        public final boolean notificationAccessGranted;
        public final boolean playing;
        public final String title;
        public final String artist;
        public final String source;
        public final long positionMs;
        public final long durationMs;
        @Nullable
        public final Bitmap artwork;

        public MediaState(
                boolean sessionAvailable,
                boolean notificationAccessGranted,
                boolean playing,
                String title,
                String artist,
                String source,
                long positionMs,
                long durationMs,
                @Nullable Bitmap artwork) {
            this.sessionAvailable = sessionAvailable;
            this.notificationAccessGranted = notificationAccessGranted;
            this.playing = playing;
            this.title = title;
            this.artist = artist;
            this.source = source;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.artwork = artwork;
        }

        public static MediaState empty(boolean notificationAccessGranted) {
            return new MediaState(
                    false, notificationAccessGranted, false, null, null, null, 0L, 0L, null);
        }
    }

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();

    private MediaSessionManager sessionManager;
    private MediaController activeController;
    private boolean started;
    private boolean sessionsListenerRegistered;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChangedListener =
            controllers -> mainHandler.post(this::refreshActiveController);

    private final LauncherMediaNotificationListener.Listener notificationListener =
            this::onNotificationListenerConnected;

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            notifyState();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            notifyState();
            scheduleProgressTick(state);
        }

        @Override
        public void onSessionDestroyed() {
            detachController();
            refreshActiveController();
        }
    };

    private final Runnable progressTick = () -> {
        notifyState();
        PlaybackState state = activeController != null ? activeController.getPlaybackState() : null;
        scheduleProgressTick(state);
    };

    public LauncherMediaController(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void start() {
        if (started) {
            // Ayarlardan dönünce izin yeni açılmış olabilir
            registerSessionsListener();
            refreshActiveController();
            return;
        }
        started = true;
        LauncherMediaNotificationListener.setListener(notificationListener);
        sessionManager = (MediaSessionManager) appContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
        registerSessionsListener();
        refreshActiveController();
    }

    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        LauncherMediaNotificationListener.setListener(null);
        mainHandler.removeCallbacks(progressTick);
        unregisterSessionsListener();
        detachController();
        notifyState();
    }

    public void skipToPrevious() {
        if (activeController != null) {
            activeController.getTransportControls().skipToPrevious();
        }
    }

    public void playPause() {
        if (activeController == null) {
            return;
        }
        PlaybackState state = activeController.getPlaybackState();
        if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
            activeController.getTransportControls().pause();
        } else {
            activeController.getTransportControls().play();
        }
    }

    public void skipToNext() {
        if (activeController != null) {
            activeController.getTransportControls().skipToNext();
        }
    }

    public void volumeDown() {
        adjustMusicVolume(android.media.AudioManager.ADJUST_LOWER);
    }

    public void volumeUp() {
        adjustMusicVolume(android.media.AudioManager.ADJUST_RAISE);
    }

    public void toggleMute() {
        android.media.AudioManager am = (android.media.AudioManager)
                appContext.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return;
        }
        am.adjustStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.ADJUST_TOGGLE_MUTE,
                android.media.AudioManager.FLAG_SHOW_UI);
    }

    private void adjustMusicVolume(int direction) {
        android.media.AudioManager am = (android.media.AudioManager)
                appContext.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return;
        }
        am.adjustStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                direction,
                android.media.AudioManager.FLAG_SHOW_UI);
    }

    public MediaState currentState() {
        return buildState(activeController);
    }

    public boolean isNotificationAccessGranted() {
        return isNotificationAccessEnabled(appContext);
    }

    /** Sistem bildirim erişimi ayar ekranını açar. */
    public void openNotificationAccessSettings() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            appContext.startActivity(intent);
        } catch (Exception ignored) {
            Intent fallback = new Intent(Settings.ACTION_SETTINGS);
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(fallback);
        }
    }

    public static boolean isNotificationAccessEnabled(Context context) {
        ComponentName expected = new ComponentName(
                context.getApplicationContext(), LauncherMediaNotificationListener.class);
        String flat = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(flat)) {
            return false;
        }
        for (String raw : flat.split(":")) {
            ComponentName enabled = ComponentName.unflattenFromString(raw);
            if (enabled != null && expected.equals(enabled)) {
                return true;
            }
        }
        return false;
    }

    private void onNotificationListenerConnected() {
        mainHandler.post(() -> {
            registerSessionsListener();
            refreshActiveController();
        });
    }

    private void registerSessionsListener() {
        if (sessionManager == null || sessionsListenerRegistered) {
            return;
        }
        if (!isNotificationAccessGranted()) {
            return;
        }
        try {
            ComponentName component = new ComponentName(
                    appContext, LauncherMediaNotificationListener.class);
            sessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, component);
            sessionsListenerRegistered = true;
        } catch (SecurityException ignored) {
            sessionsListenerRegistered = false;
        }
    }

    private void unregisterSessionsListener() {
        if (sessionManager == null || !sessionsListenerRegistered) {
            return;
        }
        try {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener);
        } catch (Exception ignored) {
            // no-op
        }
        sessionsListenerRegistered = false;
    }

    private void refreshActiveController() {
        if (!started) {
            return;
        }
        if (!isNotificationAccessGranted()) {
            detachController();
            notifyState();
            return;
        }
        // İzin yeni açıldıysa listener'ı kaydet
        registerSessionsListener();
        MediaController next = pickController(loadControllers());
        if (next == activeController) {
            notifyState();
            PlaybackState sameState = activeController != null
                    ? activeController.getPlaybackState() : null;
            scheduleProgressTick(sameState);
            return;
        }
        detachController();
        activeController = next;
        if (activeController != null) {
            activeController.registerCallback(controllerCallback, mainHandler);
        }
        notifyState();
        PlaybackState state = activeController != null ? activeController.getPlaybackState() : null;
        scheduleProgressTick(state);
    }

    @Nullable
    private List<MediaController> loadControllers() {
        if (sessionManager == null || !isNotificationAccessGranted()) {
            return null;
        }
        try {
            ComponentName component = new ComponentName(
                    appContext, LauncherMediaNotificationListener.class);
            return sessionManager.getActiveSessions(component);
        } catch (SecurityException e) {
            return null;
        }
    }

    @Nullable
    private static MediaController pickController(@Nullable List<MediaController> controllers) {
        if (controllers == null || controllers.isEmpty()) {
            return null;
        }
        MediaController withMetadata = null;
        for (MediaController controller : controllers) {
            PlaybackState state = controller.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                return controller;
            }
            if (withMetadata == null && hasMetadata(controller)) {
                withMetadata = controller;
            }
        }
        return withMetadata != null ? withMetadata : controllers.get(0);
    }

    private static boolean hasMetadata(MediaController controller) {
        MediaMetadata metadata = controller.getMetadata();
        if (metadata == null) {
            return false;
        }
        CharSequence title = metadata.getText(MediaMetadata.METADATA_KEY_TITLE);
        return title != null && title.length() > 0;
    }

    private void detachController() {
        mainHandler.removeCallbacks(progressTick);
        if (activeController != null) {
            activeController.unregisterCallback(controllerCallback);
            activeController = null;
        }
    }

    private void scheduleProgressTick(@Nullable PlaybackState state) {
        mainHandler.removeCallbacks(progressTick);
        if (!started || state == null || state.getState() != PlaybackState.STATE_PLAYING) {
            return;
        }
        mainHandler.postDelayed(progressTick, PROGRESS_TICK_MS);
    }

    private void notifyState() {
        MediaState state = buildState(activeController);
        for (Listener listener : listeners) {
            listener.onMediaStateChanged(state);
        }
    }

    private MediaState buildState(@Nullable MediaController controller) {
        boolean access = isNotificationAccessGranted();
        if (controller == null) {
            return MediaState.empty(access);
        }
        MediaMetadata metadata = controller.getMetadata();
        PlaybackState playbackState = controller.getPlaybackState();

        String title = metadata != null
                ? metadata.getString(MediaMetadata.METADATA_KEY_TITLE) : null;
        String artist = metadata != null
                ? metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) : null;
        if (artist == null && metadata != null) {
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        }
        String source = controller.getPackageName();
        Bitmap artwork = metadata != null
                ? metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) : null;
        if (artwork == null && metadata != null) {
            artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }

        long durationMs = metadata != null
                ? metadata.getLong(MediaMetadata.METADATA_KEY_DURATION) : 0L;
        long positionMs = 0L;
        boolean playing = false;
        if (playbackState != null) {
            playing = playbackState.getState() == PlaybackState.STATE_PLAYING;
            positionMs = playbackState.getPosition();
            // PlaybackState pozisyonu seyrek güncellenir; oynatırken gerçek zamanlı tahmin et
            if (playing) {
                long updatedAt = playbackState.getLastPositionUpdateTime();
                if (updatedAt > 0L) {
                    float speed = playbackState.getPlaybackSpeed();
                    if (speed <= 0f) {
                        speed = 1f;
                    }
                    long elapsed = SystemClock.elapsedRealtime() - updatedAt;
                    if (elapsed > 0L) {
                        positionMs += (long) (elapsed * speed);
                    }
                }
                if (durationMs > 0L) {
                    positionMs = Math.min(positionMs, durationMs);
                }
            }
        }

        boolean hasContent = title != null && !title.isEmpty();
        return new MediaState(
                hasContent || playing,
                access,
                playing,
                title,
                artist,
                source,
                Math.max(0L, positionMs),
                Math.max(0L, durationMs),
                artwork);
    }
}
