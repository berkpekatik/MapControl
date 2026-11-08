package com.mapcontrol.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Başka bir uygulamanın görev kimliğini sistemin olay günlüğünden ({@code logcat -b events}, READ_LOGS) bulur.
 *
 * <p>Neden: Android 11'de açılış sınırları ({@code setLaunchBounds}) yalnız YENİ görevde uygulanır. Yandex'in harita
 * etkinliği singleTask: uygulama önceden serbest pencerede açıldıysa eski görev eski yerinde öne gelir (7 Kasım aracı:
 * #1000215 ekranın ortasında). Yeni görev zorlanamaz (MULTIPLE_TASK/belge bayrakları singleTask'ta yok sayılır) ve başka
 * uygulamanın görevini kapatmak/boyutlandırmak yetki ister. {@code ActivityManager.moveTaskToFront(id, 0, seçenekler)}
 * ise yalnız normal REORDER_TASKS izni ister ve serbest pencerede seçeneklerdeki sınırları göreve uygular; bunun için
 * görev kimliği gerekir, o da yalnız olay günlüğünde var ({@code getRunningTasks} başka uygulamayı göstermez).
 *
 * <p>Satır biçimi (Android 11): {@code ... I wm_resume_activity: [0,126535323,1071,ru.yandex.yandexmaps/.SplashScreen]}
 * — alanlar kullanıcı, belirteç, görev kimliği, bileşen. {@code wm_new_intent}, {@code wm_create_activity},
 * {@code wm_restart_activity} aynı ilk dört alanı taşır.
 */
public final class SplitTaskFinder {

    /** Görev kimliğini 3. alanda, bileşeni 4. alanda taşıyan olay etiketleri. */
    static final String[] TAGS = {"wm_resume_activity", "wm_new_intent", "wm_create_activity", "wm_restart_activity"};

    private SplitTaskFinder() {
    }

    /** logcat argümanları: olay arabelleği, döküp çık, yalnız {@link #TAGS}. */
    @NonNull
    public static String[] logcatArgs() {
        String[] args = new String[5 + TAGS.length];
        args[0] = "logcat";
        args[1] = "-b";
        args[2] = "events";
        args[3] = "-d";
        args[4] = "-s";
        System.arraycopy(TAGS, 0, args, 5, TAGS.length);
        return args;
    }

    /**
     * Günlükte [pkg] paketine ait en son olayın görev kimliği; yoksa -1. Satırlar eskiden yeniye sıralıdır.
     */
    public static int lastTaskId(@Nullable String log, @NonNull String pkg) {
        if (log == null || log.isEmpty() || pkg.isEmpty()) {
            return -1;
        }
        int found = -1;
        for (String line : log.split("\n")) {
            int id = taskIdOf(line, pkg);
            if (id >= 0) {
                found = id;
            }
        }
        return found;
    }

    /** Tek satırdan görev kimliği (etiket ve paket uyuyorsa); yoksa -1. */
    static int taskIdOf(@NonNull String line, @NonNull String pkg) {
        boolean tagged = false;
        for (String tag : TAGS) {
            if (line.contains(tag + ":") || line.contains(tag + "(")) {
                tagged = true;
                break;
            }
        }
        if (!tagged) {
            return -1;
        }
        int open = line.lastIndexOf('[');
        int close = line.lastIndexOf(']');
        if (open < 0 || close <= open) {
            return -1;
        }
        String[] f = line.substring(open + 1, close).split(",", -1);
        if (f.length < 4) {
            return -1;
        }
        String component = f[3].trim();
        if (!component.startsWith(pkg + "/")) {
            return -1;
        }
        try {
            int id = Integer.parseInt(f[2].trim());
            return id > 0 ? id : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
