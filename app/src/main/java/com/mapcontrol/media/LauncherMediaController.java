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
import android.service.notification.NotificationListenerService;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.mapcontrol.launcher.MediaSourcePick;
import com.mapcontrol.launcher.OemAudioSource;

import java.util.ArrayList;
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

    /**
     * Saniyede bir tick yeterli (süre metni saniye çözünürlüğünde). 50 ms'lik tick her seferinde
     * metadata ve kapak resmini binder'dan yeniden okuyup GC baskısı yaratıyor, ses takılmasına katkı veriyordu.
     */
    private static final long PROGRESS_TICK_MS = 1000L;

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

    private static final String PREFS = "launcher_media";
    private static final String KEY_LAST_SOURCE = "last_source";
    /** Süreç başına bir kez: izin açık ama dinleyici bağlı değilse sistemden yeniden bağlama istenir. */
    private static volatile boolean rebindRequested;

    @Nullable
    private String lastSource;
    private MediaSessionManager sessionManager;
    private MediaController activeController;
    @Nullable private String lastArtworkKey;
    @Nullable private Bitmap lastArtwork;
    private boolean started;
    private boolean sessionsListenerRegistered;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChangedListener =
            controllers -> mainHandler.post(this::refreshActiveController);

    private final LauncherMediaNotificationListener.Listener notificationListener =
            this::onNotificationListenerConnected;

    /** OEM ses kaynağı (CURRENT_SOURCE_TYPE) değişti: kart yeni kaynağı izlesin ({@link MediaSourcePick#pick}). */
    private final OemAudioSource.SourceListener oemSourceListener = type -> refreshActiveController();

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            notifyState();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            notifyState();
            scheduleProgressTick(state);
            // Seçili oturum durdu: başka çalan varsa ona geç.
            if (state == null || state.getState() != PlaybackState.STATE_PLAYING) {
                mainHandler.post(LauncherMediaController.this::refreshActiveController);
            }
        }

        @Override
        public void onSessionDestroyed() {
            detachController();
            refreshActiveController();
        }
    };

    /**
     * Seçili olmayan oturumlar da izlenir: biri çalmaya başlarsa ya da seçili oturum durursa yeniden seçilir.
     * 6 Kasım araç: radyo (OEM ses uygulaması, "am") seçiliyken Bluetooth müziği başladı; iki oturum zaten etkin olduğundan
     * oturum listesi değişmedi ve kart radyoda kaldı.
     */
    private final List<MediaController> watched = new ArrayList<>();

    private final MediaController.Callback watcherCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            if (isPlayingState(state)) {
                mainHandler.post(LauncherMediaController.this::refreshActiveController);
            }
        }
    };

    private void watchOthers(@Nullable List<MediaController> controllers) {
        unwatchOthers();
        if (controllers == null) {
            return;
        }
        for (MediaController c : controllers) {
            // Liste her okunuşta yeni örnekler verir: seçili oturum kimliğiyle (token) ayıklanır.
            if (c == activeController || (activeController != null
                    && c.getSessionToken().equals(activeController.getSessionToken()))) {
                continue;
            }
            try {
                c.registerCallback(watcherCallback, mainHandler);
                watched.add(c);
            } catch (Exception ignored) {
                // oturum kapanmış olabilir
            }
        }
    }

    private void unwatchOthers() {
        for (MediaController c : watched) {
            try {
                c.unregisterCallback(watcherCallback);
            } catch (Exception ignored) {
                // no-op
            }
        }
        watched.clear();
    }

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
        LauncherMediaNotificationListener.addListener(notificationListener);
        OemAudioSource.INSTANCE.watchSource(appContext, oemSourceListener);
        selfGrantNotificationAccessOnce();
        requestListenerRebindOnce();
        sessionManager = (MediaSessionManager) appContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
        registerSessionsListener();
        refreshActiveController();
        // Telefon önceliği kartın ömründen bağımsız (süreç boyu); dinleyici henüz bağlanmadıysa bu bir şey yapmaz.
        BtSessionWatcher.start(appContext);
    }

    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        LauncherMediaNotificationListener.removeListener(notificationListener);
        OemAudioSource.INSTANCE.unwatchSource(oemSourceListener);
        mainHandler.removeCallbacks(progressTick);
        unregisterSessionsListener();
        detachController();
        notifyState();
    }

    /** Geri: OEM kaynağına göre VDBus ya da oturum ({@link OemAudioSource#skip}). */
    public void skipToPrevious() {
        skip(false);
    }

    /**
     * Oynat/duraklat. Oynat'ta bir Bluetooth oturumu varsa önce OEM ses kaynağı Bluetooth müziğine geçirilir (OEM medya
     * uygulaması gibi; radyo odağı tutarken BT çalması 1-2 sn'de duruyordu), sonra BT oturumuna oynat gönderilir
     * ({@link OemAudioSource#playBluetooth}). Yalnızca kullanıcı dokunuşuyla çağrılır.
     */
    public void playPause() {
        MediaController active = activeController;
        PlaybackState state = active != null ? active.getPlaybackState() : null;
        String activePkg = active != null ? active.getPackageName() : null;
        Integer oemSource = OemAudioSource.INSTANCE.getCurrentSource();
        boolean oemSession = MediaSourcePick.INSTANCE.keepOemOnPlay(activePkg, oemSource);
        boolean playing = MediaSourcePick.INSTANCE.radioActive(activePkg, oemSource)
                ? !OemAudioSource.INSTANCE.isOemPaused()
                : state != null && state.getState() == PlaybackState.STATE_PLAYING;
        android.util.Log.i("MediaSource", "playPause: pkg=" + activePkg + " state=" + (state != null ? state.getState() : -1)
                + " oemSource=" + oemSource);
        if (oemSession) {
            OemAudioSource.INSTANCE.oemPlayback(appContext, oemSource, !playing, () -> {
                if (playing) {
                    active.getTransportControls().pause();
                } else {
                    active.getTransportControls().play();
                }
            });
            return;
        }
        if (playing) {
            active.getTransportControls().pause();
            return;
        }
        // Üçüncü taraf uygulama kendi odağını alır; BT'ye geçirmek onu durdurur.
        if (MediaSourcePick.INSTANCE.isThirdParty(activePkg)) {
            active.getTransportControls().play();
            return;
        }
        MediaController bt = MediaSourcePick.INSTANCE.isBluetooth(active != null ? active.getPackageName() : null)
                ? active : findBluetooth(loadControllers());
        if (bt != null) {
            final MediaController target = bt;
            OemAudioSource.INSTANCE.playBluetooth(appContext, () -> {
                try {
                    target.getTransportControls().play();
                } catch (Exception ignored) {
                    // oturum bu arada kapanmış olabilir
                }
            });
            return;
        }
        if (active != null) {
            active.getTransportControls().play();
        }
    }

    @Nullable
    private static MediaController findBluetooth(@Nullable List<MediaController> controllers) {
        if (controllers == null) {
            return null;
        }
        for (MediaController c : controllers) {
            if (MediaSourcePick.INSTANCE.isBluetooth(c.getPackageName())) {
                return c;
            }
        }
        return null;
    }

    /** İleri: OEM kaynağına göre VDBus ya da oturum ({@link OemAudioSource#skip}). */
    public void skipToNext() {
        skip(true);
    }

    private void skip(boolean next) {
        final MediaController target = activeController;
        OemAudioSource.INSTANCE.skip(appContext, next, target != null ? target.getPackageName() : null, () -> {
            if (target == null) {
                return;
            }
            try {
                if (next) {
                    target.getTransportControls().skipToNext();
                } else {
                    target.getTransportControls().skipToPrevious();
                }
            } catch (Exception ignored) {
                // oturum bu arada kapanmış olabilir
            }
        });
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

    /** Son oturumu olan medya uygulamasının paket adı (yoksa null); yeniden açılışta da korunur. */
    @Nullable
    public String lastSourcePackage() {
        if (lastSource == null) {
            lastSource = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_LAST_SOURCE, null);
        }
        return lastSource;
    }

    /** Son kaynak uygulamayı başlatır; açılamazsa false döner. */
    public boolean launchLastSource() {
        String pkg = lastSourcePackage();
        if (pkg == null) {
            return false;
        }
        try {
            Intent launch = appContext.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) {
                return false;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(launch);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Kartın başlığına / kapağına dokunuldu: [pkg] uygulamasını açar. Etkin oturum bu uygulamanınsa oturumun kendi
     * "şu an çalıyor" ekranı ({@link MediaController#getSessionActivity}), yoksa uygulamanın başlatıcı etkinliği.
     * Açılamazsa false.
     */
    public boolean openApp(@Nullable String pkg) {
        if (pkg == null) {
            return false;
        }
        MediaController active = activeController;
        if (active != null && pkg.equals(active.getPackageName())) {
            try {
                android.app.PendingIntent session = active.getSessionActivity();
                if (session != null) {
                    session.send();
                    return true;
                }
            } catch (Exception ignored) {
                // iptal edilmiş PendingIntent: başlatıcıya düşülür
            }
        }
        try {
            Intent launch = appContext.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) {
                return false;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(launch);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void rememberSource(@Nullable String pkg) {
        if (pkg == null || pkg.equals(lastSource)) {
            return;
        }
        lastSource = pkg;
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LAST_SOURCE, pkg).apply();
    }

    /** Sistem bildirim erişimi ayar ekranını açar (API 30+ bu uygulamanın ayrıntı sayfası). */
    public void openNotificationAccessSettings() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
            intent.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    new ComponentName(appContext, LauncherMediaNotificationListener.class)
                            .flattenToString());
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            appContext.startActivity(intent);
        } catch (Exception ignored) {
            try {
                Intent general = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                general.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appContext.startActivity(general);
                return;
            } catch (Exception ignoredToo) {
                // son çare genel ayarlar
            }
            Intent fallback = new Intent(Settings.ACTION_SETTINGS);
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(fallback);
        }
    }

    /**
     * İzin kurulum betiğiyle ({@code cmd notification allow_listener com.mapcontrol/com.mapcontrol.media.LauncherMediaNotificationListener})
     * verildikten sonra dinleyici, uygulama güncellemesi ya da süreç ölümü sonrası bağsız kalabilir; sistemden yeniden bağlanması
     * istenir (zaten bağlıysa etkisizdir). Bağlanınca {@code onListenerConnected} kartları tazeler.
     */
    private boolean selfGrantTried;

    /**
     * Araçta "Bildirim erişimi" ayar ekranı yok (araç Ayarlar'ı NOTIFICATION_LISTENER_SETTINGS'i çözmüyor; araçta ölçüldü):
     * kullanıcı bu izni ekrandan veremez. Kurulum aracı WRITE_SECURE_SETTINGS verdiyse dinleyicimizi listeye kendimiz ekleriz
     * (yalnız ekleme; diğer dinleyiciler korunur). İzin yoksa sessizce geçer, kart "izin gerekli" durumunda kalır.
     */
    private void selfGrantNotificationAccessOnce() {
        if (selfGrantTried || isNotificationAccessGranted()) {
            return;
        }
        selfGrantTried = true;
        if (appContext.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        String self = new ComponentName(appContext, LauncherMediaNotificationListener.class).flattenToString();
        try {
            String current = Settings.Secure.getString(appContext.getContentResolver(), "enabled_notification_listeners");
            Settings.Secure.putString(appContext.getContentResolver(), "enabled_notification_listeners",
                    withListener(current, self));
            android.util.Log.i("MediaAccess", "bildirim erişimi kendiliğinden açıldı (WRITE_SECURE_SETTINGS)");
        } catch (RuntimeException e) {
            android.util.Log.w("MediaAccess", "bildirim erişimi açılamadı: " + e.getClass().getSimpleName());
        }
    }

    /** ':' ile ayrılmış dinleyici listesine [component]'i ekler; zaten varsa listeyi aynen döner. */
    public static String withListener(@Nullable String current, String component) {
        if (current == null || current.trim().isEmpty() || "null".equals(current.trim())) {
            return component;
        }
        for (String raw : current.split(":")) {
            if (raw.trim().equals(component)) {
                return current;
            }
        }
        return current + ":" + component;
    }

    private void requestListenerRebindOnce() {
        if (rebindRequested || !isNotificationAccessGranted()) {
            return;
        }
        rebindRequested = true;
        try {
            NotificationListenerService.requestRebind(
                    new ComponentName(appContext, LauncherMediaNotificationListener.class));
        } catch (RuntimeException ignored) {
            // izin yok / sistem reddetti: kart "izin gerekli" durumunda kalır
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
        List<MediaController> all = loadControllers();
        // Hiçbiri çalmıyorsa seçili oturum (hâlâ listedeyse) korunur: duraklatılan müzik radyoya atlamaz. BT oturumu varken
        // OEM radyosunun boş ("am") oturumu karta seçilmez (MediaSourcePick).
        MediaController next = pickController(all, activeController);
        if (next != null && activeController != null
                && next.getSessionToken().equals(activeController.getSessionToken())) {
            next = activeController;
        }
        if (next == activeController) {
            watchOthers(all);
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
        watchOthers(all);
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
    private static MediaController pickController(@Nullable List<MediaController> controllers,
                                                  @Nullable MediaController current) {
        if (controllers == null || controllers.isEmpty()) {
            return null;
        }
        List<MediaSourcePick.Cand> cands = new ArrayList<>(controllers.size());
        int currentIndex = -1;
        boolean hasBt = false;
        for (int i = 0; i < controllers.size(); i++) {
            MediaController c = controllers.get(i);
            cands.add(new MediaSourcePick.Cand(c.getPackageName(), isPlaying(c), hasMetadata(c)));
            hasBt |= MediaSourcePick.INSTANCE.isBluetooth(c.getPackageName());
            if (current != null && currentIndex < 0 && c.getSessionToken().equals(current.getSessionToken())) {
                currentIndex = i;
            }
        }
        Integer follow = OemAudioSource.INSTANCE.followSource(hasBt);
        int pick = MediaSourcePick.INSTANCE.pick(cands, currentIndex, follow, OemAudioSource.INSTANCE.isSourceUserPicked());
        return pick >= 0 ? (pick == currentIndex ? current : controllers.get(pick)) : null;
    }

    private static boolean isPlaying(@Nullable MediaController controller) {
        return isPlayingState(controller != null ? controller.getPlaybackState() : null);
    }

    static boolean isPlayingState(@Nullable PlaybackState state) {
        return state != null && state.getState() == PlaybackState.STATE_PLAYING;
    }

    /** Seçicide gösterilecek açık üçüncü taraf medya uygulamaları (içerik bilgisi olan ya da çalan oturumlar). */
    public List<String> appSessionPackages() {
        List<String> out = new ArrayList<>();
        List<MediaController> controllers = loadControllers();
        if (controllers == null) {
            return out;
        }
        for (MediaController c : controllers) {
            String pkg = c.getPackageName();
            if (MediaSourcePick.INSTANCE.isThirdParty(pkg) && !out.contains(pkg) && (hasMetadata(c) || isPlaying(c))) {
                out.add(pkg);
            }
        }
        return out;
    }

    /** Seçicide uygulama seçildi: oturumu çaldırır; kart çalan oturuma kendiliğinden geçer. */
    public void selectApp(String packageName) {
        OemAudioSource.INSTANCE.onAppSelected();
        List<MediaController> controllers = loadControllers();
        if (controllers == null) {
            return;
        }
        for (MediaController c : controllers) {
            if (packageName.equals(c.getPackageName())) {
                try {
                    c.getTransportControls().play();
                } catch (Exception ignored) {
                    // oturum bu arada kapanmış olabilir
                }
                return;
            }
        }
    }

    /** Bir Bluetooth (A2DP) oturumu var mı (seçicide Bluetooth satırı için). */
    public boolean hasBluetoothSession() {
        return findBluetooth(loadControllers()) != null;
    }

    /**
     * Seçicide Bluetooth: oturum varsa kartın Oynat'ıyla aynı yol ({@link OemAudioSource#playBluetooth}); yoksa oturumsuz
     * BT geçişi ({@link OemAudioSource#openBluetoothSource}). Yalnızca kullanıcı dokunuşuyla.
     */
    public void selectBluetooth() {
        MediaController bt = findBluetooth(loadControllers());
        if (bt == null) {
            OemAudioSource.INSTANCE.openBluetoothSource(appContext);
            return;
        }
        final MediaController target = bt;
        OemAudioSource.INSTANCE.playBluetooth(appContext, () -> {
            try {
                target.getTransportControls().play();
            } catch (Exception ignored) {
                // oturum bu arada kapanmış olabilir
            }
        });
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
        unwatchOthers();
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

    /** Parça kimliği: kaynak, başlık, sanatçı ve albüm. */
    static String trackKey(@Nullable String source, @Nullable String title,
                           @Nullable String artist, @Nullable String album) {
        return source + "\u0001" + title + "\u0001" + artist + "\u0001" + album;
    }

    /**
     * Kapak tutma anahtarı: [trackKey] artı süre ve medya kimliği (aynı yazılı farklı parçalar ayrılsın). Başlık, sanatçı
     * ve albüm hiç yoksa parça tanınamaz: null (önceki kapak tutulmaz; boş üstverili BT kaynağında eski kapak kalmasın).
     */
    @Nullable
    static String artworkKey(@Nullable String source, @Nullable String title, @Nullable String artist,
                             @Nullable String album, long durationMs, @Nullable String mediaId) {
        if (title == null && artist == null && album == null) {
            return null;
        }
        return trackKey(source, title, artist, album) + "\u0001" + durationMs + "\u0001" + mediaId;
    }

    interface SameContent<T> {
        boolean same(T a, T b);
    }

    /**
     * Aynı parçada kapak içeriği değişmediyse (ya da kapak geçici yoksa) önceki örneği döndürür;
     * parça değiştiyse ya da içerik gerçekten farklıysa yenisini.
     */
    @Nullable
    static <T> T stableArtwork(@Nullable String prevKey, @Nullable T prev,
                               @Nullable String key, @Nullable T next, SameContent<T> cmp) {
        if (prev == null || prevKey == null || !prevKey.equals(key)) {
            return next;
        }
        if (next == null || next == prev) {
            return prev;
        }
        return cmp.same(prev, next) ? prev : next;
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
        // Bluetooth servisi aynı şarkının üstverisini ~5 sn'de bir yeniden yollar ve her seferinde
        // yeni Bitmap nesnesi gelir; kapak titremesin diye aynı parçada eski örneği koru.
        String album = metadata != null
                ? metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) : null;
        String trackKey = artworkKey(source, title, artist, album,
                metadata != null ? metadata.getLong(MediaMetadata.METADATA_KEY_DURATION) : 0L,
                metadata != null ? metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) : null);
        artwork = stableArtwork(lastArtworkKey, lastArtwork, trackKey, artwork,
                (a, b) -> a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight() && a.sameAs(b));
        lastArtworkKey = trackKey;
        lastArtwork = artwork;

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

        // Radyo oturumu başlık ve çalma durumu bildirmez: kaynak radyodaysa kart boşta sayılmaz, ileri-geri görünür.
        boolean radioActive = MediaSourcePick.INSTANCE.radioActive(source, OemAudioSource.INSTANCE.getCurrentSource());
        if (radioActive) {
            playing = !OemAudioSource.INSTANCE.isOemPaused();
        }
        boolean hasContent = title != null && !title.isEmpty();
        if (hasContent || playing) {
            rememberSource(source);
        }
        return new MediaState(
                hasContent || playing || radioActive,
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
