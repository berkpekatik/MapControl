package com.mapcontrol.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Bölünmüş ekran kutusu: ya paket adı ("com.foo") ya da bileşen dizesi ("pkg/cls",
 * {@code ComponentName.flattenToShortString} biçimi; sınıf "." ile başlıyorsa paket öne eklenir).
 * Saf string işi; Android sınıfı kullanmaz, JVM'de test edilir.
 */
public final class SplitSlot {

    private SplitSlot() {
    }

    public static boolean isComponent(@Nullable String slot) {
        return slot != null && slot.indexOf('/') > 0 && slot.indexOf('/') < slot.length() - 1;
    }

    /** Kutudaki paket adı; bileşen dizesinde '/' öncesi. */
    @NonNull
    public static String packageOf(@NonNull String slot) {
        int i = slot.indexOf('/');
        return i > 0 ? slot.substring(0, i) : slot;
    }

    /** Bileşen dizesindeki tam sınıf adı; bileşen değilse null. */
    @Nullable
    public static String classOf(@Nullable String slot) {
        if (!isComponent(slot)) {
            return null;
        }
        int i = slot.indexOf('/');
        String cls = slot.substring(i + 1);
        if (cls.startsWith(".")) {
            return slot.substring(0, i) + cls;
        }
        return cls;
    }

    /** {@code ComponentName.flattenToShortString} ile aynı çıktı. */
    @NonNull
    public static String component(@NonNull String pkg, @NonNull String cls) {
        if (cls.startsWith(pkg) && cls.length() > pkg.length() && cls.charAt(pkg.length()) == '.') {
            return pkg + "/" + cls.substring(pkg.length());
        }
        return pkg + "/" + cls;
    }

    /**
     * Başlatılabilir mi: düz paket adı olmalı ve kendi paketimiz olamaz. Bileşen dizesi ("pkg/cls") geçersizdir; eski sürümlerde
     * kaydedilmiş Mini gösterge seçimi böylece boş sayılır.
     */
    public static boolean isAllowed(@Nullable String slot, @NonNull String selfPackage) {
        if (slot == null || slot.isEmpty() || isComponent(slot)) {
            return false;
        }
        return !slot.equals(selfPackage);
    }
}
