package com.mapcontrol.util;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/**
 * MainActivity'deki çalışan hoşgeldin TTS ("mapcontrol-tts") sistemiyle birebir aynı
 * paylaşımlı sistem TTS yöneticisi.
 */
public final class SystemTtsManager {
    private static final String TAG = "SystemTtsManager";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static TextToSpeech systemTts;
    private static boolean ttsInitializing;
    private static String pendingTtsText;

    private SystemTtsManager() {
    }

    public static void speakDelayed(Context context, String text, long delayMs) {
        if (context == null || text == null) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        MAIN_HANDLER.postDelayed(() -> speak(appContext, text), delayMs);
    }

    public static void speak(Context context, String text) {
        if (context == null || text == null) {
            return;
        }
        final String t = text.trim();
        if (t.isEmpty()) {
            logBroadcast(context.getApplicationContext(), "TTS: metin boş");
            return;
        }
        final Context appContext = context.getApplicationContext();
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN_HANDLER.post(() -> speakOnMain(appContext, t));
        } else {
            speakOnMain(appContext, t);
        }
    }

    private static void speakOnMain(Context appContext, String t) {
        if (systemTts == null) {
            pendingTtsText = t;
            if (ttsInitializing) {
                return;
            }
            ttsInitializing = true;
            systemTts = new TextToSpeech(appContext, status -> MAIN_HANDLER.post(() -> {
                ttsInitializing = false;
                if (status != TextToSpeech.SUCCESS || systemTts == null) {
                    logBroadcast(appContext, "TTS: sistem ses sentezi başlatılamadı");
                    if (systemTts != null) {
                        try {
                            systemTts.shutdown();
                        } catch (Throwable ignored) {
                        }
                        systemTts = null;
                    }
                    pendingTtsText = null;
                    return;
                }
                systemTts.setLanguage(new Locale("tr", "TR"));
                if (pendingTtsText != null) {
                    String queued = pendingTtsText;
                    pendingTtsText = null;
                    speakNow(appContext, queued, false);
                }
            }));
            return;
        }
        if (ttsInitializing) {
            pendingTtsText = t;
            return;
        }
        speakNow(appContext, t, true);
    }

    private static void speakNow(Context appContext, String text, boolean allowReinitOnError) {
        if (systemTts == null) {
            return;
        }
        int result = systemTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mapcontrol-tts");
        if (result == TextToSpeech.SUCCESS) {
            logBroadcast(appContext, "TTS: sistem ses sentezi ile okunuyor (" + text + ")");
        } else if (allowReinitOnError) {
            try {
                systemTts.shutdown();
            } catch (Throwable ignored) {
            }
            systemTts = null;
            ttsInitializing = false;
            speakOnMain(appContext, text);
        } else {
            logBroadcast(appContext, "TTS: konuşma başlatılamadı (kod=" + result + ")");
        }
    }

    private static void logBroadcast(Context appContext, String msg) {
        Log.i(TAG, msg);
        Intent intent = new Intent("com.mapcontrol.LOG_MESSAGE");
        intent.putExtra("log_message", msg);
        appContext.sendBroadcast(intent);
    }
}
