package com.mapcontrol.media;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.mapcontrol.launcher.MediaSourcePick;
import com.mapcontrol.launcher.OemAudioSource;

import java.util.List;

/**
 * Telefon önceliği için Bluetooth (A2DP) oturumunu süreç boyunca izler: kart ya da Mini gösterge gizliyken de (kullanıcı OEM
 * medya uygulamasında, ya da navigasyon tam ekran) {@link OemAudioSource#onBtPlayback} beslenir.
 * <p>
 * Bildirim dinleyicisi bağlanınca başlar ({@link LauncherMediaNotificationListener#onListenerConnected}; erişim açıkken
 * sistem onu süreç boyunca bağlı tutar), koptuğunda durur. Oturum listesi okunamazsa (erişim yok) hiçbir şey beslenmez.
 * <p>
 * OEM A2DP servisi AVRCP kopunca oturumu pasif yapar
 * (etkin oturumlardan düşer) ve durumu ERROR'a çeker; ikisi de "oturum yok" ({@code null}) olarak iletilir. Yeniden bağlanıp
 * çalınca oturum yeniden etkinleşir.
 */
public final class BtSessionWatcher {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final OemAudioSource.SourceListener NO_OP = type -> { };

    @Nullable private static Context appContext;
    @Nullable private static MediaSessionManager sessionManager;
    @Nullable private static MediaController tracked;
    private static boolean listening;

    private static final MediaSessionManager.OnActiveSessionsChangedListener SESSIONS =
            controllers -> MAIN.post(BtSessionWatcher::refresh);

    private static final MediaController.Callback CALLBACK = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            feed(state);
        }

        @Override
        public void onMetadataChanged(@Nullable android.media.MediaMetadata metadata) {
            // Parça bilgisi çalma durumundan sonra geldiyse telefon önceliği beklemeden yeniden dener.
            OemAudioSource.INSTANCE.onBtMetadataChanged();
        }

        @Override
        public void onSessionDestroyed() {
            MAIN.post(BtSessionWatcher::refresh);
        }
    };

    private BtSessionWatcher() {
    }

    /**
     * İzlenen BT oturumu anlamlı bir parça bildiriyor mu (AVRCP başlık ya da sanatçı). Telefon önceliği bunu sorar:
     * kilit açılınca gelen anlık sesler çoğunlukla parça bilgisi göndermez. Ana iş parçacığından çağrılır.
     */
    public static boolean hasTrackInfo() {
        MediaController c = tracked;
        if (c == null) {
            return false;
        }
        android.media.MediaMetadata m;
        try {
            m = c.getMetadata();
        } catch (Exception e) {
            return false;
        }
        if (m == null) {
            return false;
        }
        return com.mapcontrol.launcher.TrackInfo.INSTANCE.present(
                m.getString(android.media.MediaMetadata.METADATA_KEY_TITLE),
                m.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST));
    }

    /** Ana iş parçacığından; tekrar çağrılabilir (zaten dinliyorsa yalnızca yeniden okur). */
    public static void start(Context context) {
        MAIN.post(() -> {
            if (appContext == null) {
                appContext = context.getApplicationContext();
                sessionManager = (MediaSessionManager) appContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
            }
            // Kaynak bilinmezse karar UNKNOWN_SOURCE olur: CURRENT_SOURCE_TYPE aboneliği kart olmadan da açılır.
            OemAudioSource.INSTANCE.watchSource(appContext, NO_OP);
            if (!listening && sessionManager != null) {
                try {
                    sessionManager.addOnActiveSessionsChangedListener(SESSIONS, component(), MAIN);
                    listening = true;
                } catch (SecurityException ignored) {
                    // bildirim erişimi yok
                }
            }
            refresh();
        });
    }

    /** Bildirim dinleyicisi koptu: oturum listesi artık okunamaz. Son durum değiştirilmez. */
    public static void stop() {
        MAIN.post(() -> {
            if (listening && sessionManager != null) {
                try {
                    sessionManager.removeOnActiveSessionsChangedListener(SESSIONS);
                } catch (Exception ignored) {
                    // no-op
                }
            }
            listening = false;
            track(null);
        });
    }

    private static ComponentName component() {
        return new ComponentName(appContext, LauncherMediaNotificationListener.class);
    }

    private static void refresh() {
        if (appContext == null || sessionManager == null) {
            return;
        }
        List<MediaController> all;
        try {
            all = sessionManager.getActiveSessions(component());
        } catch (SecurityException e) {
            return; // okunamadı: "oturum yok" sayılmaz
        }
        MediaController bt = null;
        if (all != null) {
            for (MediaController c : all) {
                if (MediaSourcePick.INSTANCE.isBluetooth(c.getPackageName())) {
                    bt = c;
                    break;
                }
            }
        }
        track(bt);
        feed(bt != null ? bt.getPlaybackState() : null, bt != null);
    }

    /** İzlenen oturumu değiştirir (liste her okunuşta yeni örnek verir: kimlikle karşılaştırılır). */
    private static void track(@Nullable MediaController next) {
        MediaSession.Token old = tracked != null ? tracked.getSessionToken() : null;
        if (next != null && old != null && old.equals(next.getSessionToken())) {
            return;
        }
        if (tracked != null) {
            try {
                tracked.unregisterCallback(CALLBACK);
            } catch (Exception ignored) {
                // no-op
            }
        }
        tracked = next;
        if (next != null) {
            try {
                next.registerCallback(CALLBACK, MAIN);
            } catch (Exception ignored) {
                tracked = null; // oturum kapanmış olabilir
            }
        }
    }

    private static void feed(@Nullable PlaybackState state) {
        feed(state, tracked != null);
    }

    private static void feed(@Nullable PlaybackState state, boolean present) {
        if (appContext == null) {
            return;
        }
        Boolean playing = present ? MediaSourcePick.INSTANCE.btPlaying(state != null ? state.getState() : null) : null;
        OemAudioSource.INSTANCE.onBtPlayback(appContext, playing);
    }
}
