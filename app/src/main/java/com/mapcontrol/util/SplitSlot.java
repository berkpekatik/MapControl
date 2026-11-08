package com.mapcontrol.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Bölünmüş ekran kutusu: ya paket adı ("com.foo") ya da bileşen dizesi ("pkg/cls",
 * {@code ComponentName.flattenToShortString} biçimi; sınıf "." ile başlıyorsa paket öne eklenir).
 * Saf string işi; Android sınıfı kullanmaz, JVM'de test edilir.
 */
public final class SplitSlot {

    /** Mini gösterge activity sınıfı. */
    public static final String MINI_DASH_CLASS = "com.mapcontrol.launcher.MiniDashActivity";
    /** Sürüş ekranının harita yarısı, harita uygulaması seçilmemişken: "+ Harita ekle" seçicisi. */
    public static final String MAP_PICKER_CLASS = "com.mapcontrol.ui.activity.DriveMapPickerActivity";

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

    /** Kendi paketimizdeki Mini gösterge kutusu. */
    @NonNull
    public static String miniDash(@NonNull String selfPackage) {
        return component(selfPackage, MINI_DASH_CLASS);
    }

    /** Kutu, kendi paketimizdeki Mini gösterge bileşeni mi. */
    public static boolean isMiniDash(@Nullable String slot, @NonNull String selfPackage) {
        return isComponent(slot)
                && selfPackage.equals(packageOf(slot))
                && MINI_DASH_CLASS.equals(classOf(slot));
    }

    /** Kendi paketimizdeki harita seçici kutusu (sürüş ekranı). */
    @NonNull
    public static String mapPicker(@NonNull String selfPackage) {
        return component(selfPackage, MAP_PICKER_CLASS);
    }

    /** Kutu, kendi paketimizdeki harita seçici bileşeni mi. */
    public static boolean isMapPicker(@Nullable String slot, @NonNull String selfPackage) {
        return isComponent(slot)
                && selfPackage.equals(packageOf(slot))
                && MAP_PICKER_CLASS.equals(classOf(slot));
    }

    /** Kutu kendi etkinliklerimizden biri mi (Mini gösterge ya da harita seçici; görevleri bizim, yeniden açılabilir). */
    public static boolean isOwn(@Nullable String slot, @NonNull String selfPackage) {
        return isMiniDash(slot, selfPackage) || isMapPicker(slot, selfPackage);
    }

    /**
     * Başlatılabilir mi: düz paket kendi paketimiz olamaz; bileşen yalnız kendi Mini göstergemiz ya da harita seçicimiz olabilir.
     */
    public static boolean isAllowed(@Nullable String slot, @NonNull String selfPackage) {
        if (slot == null || slot.isEmpty()) {
            return false;
        }
        if (isComponent(slot)) {
            return isOwn(slot, selfPackage);
        }
        return !slot.equals(selfPackage);
    }
}
