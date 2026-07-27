package com.mapcontrol.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Launcher dashboard sol kartındaki Quick App kalıcılığı.
 * Mod: seçilebilir kısayol slotları (5…35) veya tüm yüklü+sistem uygulamaları listesi.
 */
public final class LauncherQuickAppsStore {

    public static final int MAX_SLOT_COUNT = 35;
    public static final int GRID_COLUMNS = 5;
    public static final int DEFAULT_VISIBLE_COUNT = 10;
    public static final int[] SLOT_COUNT_OPTIONS = {5, 10, 15, 20, 25, 30, 35};

    /** @deprecated Yerine {@link #MAX_SLOT_COUNT} / {@link #getVisibleSlotCount(Context)} kullan. */
    public static final int SLOT_COUNT = MAX_SLOT_COUNT;

    private static final String PREFS_NAME = "MapControlPrefs";
    private static final String KEY_QUICK_APPS = "launcher_quick_apps";
    private static final String KEY_VISIBLE_COUNT = "launcher_quick_apps_visible_count";
    private static final String KEY_SHOW_ALL_APPS = "launcher_quick_apps_show_all";

    private LauncherQuickAppsStore() {
    }

    /** true = hızlı erişimde tüm yüklü+sistem uygulamaları; false = seçilebilir kısayol slotları. */
    public static boolean isShowAllApps(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_SHOW_ALL_APPS, false);
    }

    public static void setShowAllApps(@NonNull Context context, boolean showAll) {
        prefs(context).edit().putBoolean(KEY_SHOW_ALL_APPS, showAll).apply();
    }

    public static int getVisibleSlotCount(@NonNull Context context) {
        int stored = prefs(context).getInt(KEY_VISIBLE_COUNT, DEFAULT_VISIBLE_COUNT);
        return sanitizeVisibleCount(stored);
    }

    public static void setVisibleSlotCount(@NonNull Context context, int count) {
        prefs(context).edit()
                .putInt(KEY_VISIBLE_COUNT, sanitizeVisibleCount(count))
                .putBoolean(KEY_SHOW_ALL_APPS, false)
                .apply();
    }

    private static int sanitizeVisibleCount(int count) {
        int best = DEFAULT_VISIBLE_COUNT;
        int bestDist = Integer.MAX_VALUE;
        for (int option : SLOT_COUNT_OPTIONS) {
            int dist = Math.abs(option - count);
            if (dist < bestDist) {
                bestDist = dist;
                best = option;
            }
        }
        return best;
    }

    @NonNull
    public static String[] getSlots(@NonNull Context context) {
        SharedPreferences prefs = prefs(context);
        String raw = prefs.getString(KEY_QUICK_APPS, null);
        String[] slots = new String[MAX_SLOT_COUNT];
        if (raw == null || raw.isEmpty()) {
            for (int i = 0; i < MAX_SLOT_COUNT; i++) {
                slots[i] = "";
            }
            return slots;
        }
        String[] parts = raw.split("\\|", -1);
        for (int i = 0; i < MAX_SLOT_COUNT; i++) {
            slots[i] = i < parts.length && parts[i] != null ? parts[i].trim() : "";
        }
        return slots;
    }

    public static void setSlot(@NonNull Context context, int index, @Nullable String packageName) {
        if (index < 0 || index >= MAX_SLOT_COUNT) {
            return;
        }
        String[] slots = getSlots(context);
        slots[index] = packageName != null ? packageName.trim() : "";
        save(context, slots);
    }

    public static void clearSlot(@NonNull Context context, int index) {
        setSlot(context, index, "");
    }

    private static void save(@NonNull Context context, @NonNull String[] slots) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MAX_SLOT_COUNT; i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(i < slots.length && slots[i] != null ? slots[i] : "");
        }
        prefs(context).edit().putString(KEY_QUICK_APPS, sb.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
