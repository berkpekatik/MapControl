package com.mapcontrol.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import java.util.function.Consumer;

/**
 * iFly HMI (IflytekSpeechClient.apk) içindeki TtsService tersine mühendislik özeti:
 * <ul>
 *   <li>Manifest: action {@value #IFLY_TTS_ACTION}, sınıf {@value #IFLY_TTS_CLASS}, paket {@value #IFLY_HMI_PACKAGE},
 *   exported=true.</li>
 *   <li>{@code TtsService.onStartCommand}: action eşleşirse
 *   {@code "text"} metni ve çağıran {@code "package"} ile xTTS oturumuna iletilir; {@code "operation"/"STOP"}
 *   ile durdurma (son paket eşleşmesi) vardır.</li>
 *   <li>Tam sözleşme: decompile — {@code com.iflytek.autofly.voicecoreservice.tts.TtsService}.</li>
 * </ul>
 * Normal imzalı 3. parti uygulamada çalışması OEM politikasına bağlıdır.
 */
public final class IflyOemTtsHelper {

    public static final String IFLY_HMI_PACKAGE = "com.iflytek.cutefly.speechclient.hmi";
    public static final String IFLY_TTS_CLASS = "com.iflytek.autofly.voicecoreservice.tts.TtsService";
    public static final String IFLY_TTS_ACTION = "com.iflytek.autofly.TtsService";

    /** TtsService / FilterName.OPERATION ile uyumlu: durdurma isteği */
    public static final String EXTRA_OPERATION = "operation";
    public static final String OPERATION_STOP = "STOP";
    public static final String EXTRA_TEXT = "text";
    /** TTS oturumunu hangi paketin açtığını eşleştirmek için (onStop path) */
    public static final String EXTRA_CALLER_PACKAGE = "package";

    private IflyOemTtsHelper() { }

    /**
     * iFly xTTS’e metin okutma isteği gönderir (startService).
     *
     * @return true ise exception yok; ses garantisi değil
     */
    public static boolean trySpeak(Context context, String text, Consumer<String> log) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        Context app = context.getApplicationContext();
        try {
            Intent i = new Intent(IFLY_TTS_ACTION);
            i.setComponent(new ComponentName(IFLY_HMI_PACKAGE, IFLY_TTS_CLASS));
            i.putExtra(EXTRA_TEXT, text);
            i.putExtra(EXTRA_CALLER_PACKAGE, app.getPackageName());
            ComponentName started = app.startService(i);
            if (started == null) {
                if (log != null) {
                    log.accept("iFly TTS: startService null (servis bulunamadı veya başlatılamadı)");
                }
                return false;
            }
            if (log != null) {
                log.accept("iFly TTS: istek gönderildi (OEM xTTS yolu)");
            }
            return true;
        } catch (SecurityException e) {
            if (log != null) {
                log.accept("iFly TTS: erişim reddedildi — " + e.getMessage());
            }
            return false;
        } catch (IllegalStateException e) {
            if (log != null) {
                log.accept("iFly TTS: IllegalStateException — " + e.getMessage());
            }
            return false;
        } catch (Exception e) {
            if (log != null) {
                log.accept("iFly TTS: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Uzun süreli okumayı durdurmaya çalışır (kaynak koddaki STOP dalı).
     */
    public static void tryStop(Context context, Consumer<String> log) {
        Context app = context.getApplicationContext();
        try {
            Intent i = new Intent(IFLY_TTS_ACTION);
            i.setComponent(new ComponentName(IFLY_HMI_PACKAGE, IFLY_TTS_CLASS));
            i.putExtra(EXTRA_OPERATION, OPERATION_STOP);
            i.putExtra(EXTRA_CALLER_PACKAGE, app.getPackageName());
            app.startService(i);
            if (log != null) {
                log.accept("iFly TTS: STOP isteği gönderildi");
            }
        } catch (Exception e) {
            if (log != null) {
                log.accept("iFly TTS STOP: " + e.getMessage());
            }
        }
    }
}
