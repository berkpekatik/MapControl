package com.mapcontrol.util;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * {@code assets/soft_alert.mp3} — VDBus uyarı tuşu; Activity veya Service bağlamından çalınabilir.
 */
public final class AlertSoundHelper {

    private static final Object LOCK = new Object();
    private static MediaPlayer player;

    private AlertSoundHelper() {
    }

    public static void playSoftAlert(Context context, Consumer<String> log) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            synchronized (LOCK) {
                try {
                    releaseLocked(log);
                    player = new MediaPlayer();
                    AudioAttributes attrs = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build();
                    player.setAudioAttributes(attrs);
                    AssetFileDescriptor afd = app.getAssets().openFd("soft_alert.mp3");
                    player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                    afd.close();
                    player.prepare();
                    player.setOnCompletionListener(mp -> {
                        synchronized (LOCK) {
                            try {
                                if (mp != null) {
                                    mp.release();
                                }
                            } catch (Exception ignored) {
                            }
                            if (player == mp) {
                                player = null;
                            }
                        }
                        if (log != null) {
                            log.accept("soft_alert.mp3 çalma tamamlandı");
                        }
                    });
                    player.setOnErrorListener((mp, what, extra) -> {
                        synchronized (LOCK) {
                            try {
                                if (mp != null) {
                                    mp.release();
                                }
                            } catch (Exception ignored) {
                            }
                            if (player == mp) {
                                player = null;
                            }
                        }
                        if (log != null) {
                            log.accept("soft_alert.mp3 çalma hatası: what=" + what + " extra=" + extra);
                        }
                        return true;
                    });
                    player.start();
                    if (log != null) {
                        log.accept("soft_alert.mp3 çalınıyor");
                    }
                } catch (IOException e) {
                    releaseLocked(log);
                    if (log != null) {
                        log.accept("soft_alert.mp3 dosyası açılamadı: " + e.getMessage());
                    }
                } catch (Exception e) {
                    releaseLocked(log);
                    if (log != null) {
                        log.accept("soft_alert.mp3 çalma hatası: " + e.getMessage());
                    }
                }
            }
        }).start();
    }

    private static void releaseLocked(Consumer<String> log) {
        if (player == null) {
            return;
        }
        try {
            if (player.isPlaying()) {
                player.stop();
            }
            player.release();
        } catch (Exception e) {
            if (log != null) {
                log.accept("Alert MediaPlayer temizleme hatası: " + e.getMessage());
            }
        }
        player = null;
    }
}
