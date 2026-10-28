package com.mapcontrol.util;

import android.app.ActivityOptions;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mapcontrol.admin.MapControlDeviceAdminReceiver;

/**
 * Ekranı iki bitişik pencereye böler. Android'in hazır split API'si kullanılmaz; kullanılabilir alan
 * iki dikdörtgene ayrılır ve her uygulama {@code WINDOWING_MODE_FREEFORM} (5) ile kendi
 * dikdörtgeninde açılır. Mantık MyCar'ın freeform yolundan ({@code C0320p4.u/r}) alınmıştır.
 *
 * <p>Mode 6 (Desay launcher oturumu) bilerek taşınmadı: hidden API muafiyeti ve launcher
 * sözleşmesi gerektirir, her araç imajında yoktur.
 */
public final class SplitScreenLauncher {

    private static final String TAG = "SplitScreen";

    /** {@code Settings.Global}: freeform pencere desteğini açar (MyCar {@code RealApplication.b}). */
    private static final String SETTING_ENABLE_FREEFORM = "enable_freeform_support";
    private static final String FEATURE_FREEFORM = "android.software.freeform_window_management";

    /** {@code ActivityOptions.WINDOWING_MODE_FREEFORM}. */
    private static final int WINDOWING_MODE_FREEFORM = 5;
    /** Süreç düşürüldükten sonra beklenen süre (MyCar ile aynı). */
    private static final long RECYCLE_WAIT_MS = 600L;
    /** NEW_TASK | RESET_TASK_IF_NEEDED | REORDER_TO_FRONT (MyCar: 270663680). */
    private static final int LAUNCH_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT;

    private SplitScreenLauncher() {
    }

    /** İlk uygulamanın ekrandaki payı. Kimlikler MyCar ile aynıdır. */
    public enum Ratio {
        LEFT_THIRD("left_third", 1f / 3f),
        HALF("half", 0.5f),
        RIGHT_THIRD("right_third", 2f / 3f);

        public final String id;
        public final float firstShare;

        Ratio(String id, float firstShare) {
            this.id = id;
            this.firstShare = firstShare;
        }

        public static Ratio fromId(@Nullable String id) {
            for (Ratio r : values()) {
                if (r.id.equals(id)) {
                    return r;
                }
            }
            return HALF;
        }
    }

    public enum Result {
        STARTED,
        /** Freeform açık değil ve açılamadı. */
        NO_FREEFORM,
        SAME_APP,
        NOT_INSTALLED,
        BAD_BOUNDS,
        LAUNCH_FAILED
    }

    public interface Callback {
        /** Ana iş parçacığında çağrılır. */
        void onResult(@NonNull Result result);
    }

    /** Başlatmadan önce kullanıcıya gösterilen uyumluluk özeti. */
    public static final class Capability {
        /** Sistem şu anda freeform pencere kabul ediyor. */
        public final boolean freeformOn;
        /** Kapalıysa uygulama açabilir ({@code WRITE_SECURE_SETTINGS}). */
        public final boolean canEnableFreeform;
        /** Device owner ise çalışan uygulamalar yeniden başlatılıp boyutu doğru alır. */
        public final boolean deviceOwner;

        Capability(boolean freeformOn, boolean canEnableFreeform, boolean deviceOwner) {
            this.freeformOn = freeformOn;
            this.canEnableFreeform = canEnableFreeform;
            this.deviceOwner = deviceOwner;
        }

        public boolean usable() {
            return freeformOn || canEnableFreeform;
        }
    }

    @NonNull
    public static Capability probe(@NonNull Context context) {
        boolean on = isFreeformOn(context);
        boolean canEnable = !on && hasSecureSettingsPermission(context);
        return new Capability(on, canEnable, isDeviceOwner(context));
    }

    private static boolean isFreeformOn(Context context) {
        try {
            if (context.getPackageManager().hasSystemFeature(FEATURE_FREEFORM)) {
                return true;
            }
        } catch (Exception ignored) {
        }
        try {
            return Settings.Global.getInt(context.getContentResolver(), SETTING_ENABLE_FREEFORM, 0) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasSecureSettingsPermission(Context context) {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isDeviceOwner(Context context) {
        try {
            DevicePolicyManager dpm =
                    (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            return dpm != null && dpm.isDeviceOwnerApp(context.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Freeform kapalıysa açmayı dener. Sistem ayarı yalnızca bu çağrıda değişir ve geri alınmaz;
     * açık pencereler bozulmasın diye.
     *
     * @return freeform bu çağrıdan sonra açık mı
     */
    private static boolean ensureFreeform(Context context) {
        if (isFreeformOn(context)) {
            return true;
        }
        if (!hasSecureSettingsPermission(context)) {
            return false;
        }
        try {
            Settings.Global.putInt(context.getContentResolver(), SETTING_ENABLE_FREEFORM, 1);
            Log.i(TAG, "enable_freeform_support=1 yazıldı");
        } catch (Exception e) {
            Log.w(TAG, "enable_freeform_support yazılamadı", e);
            return false;
        }
        return isFreeformOn(context);
    }

    /**
     * Arka planda başlatır; sonuç ana iş parçacığında bildirilir.
     */
    public static void start(
            @NonNull Context context,
            @NonNull String pkg1,
            @NonNull String pkg2,
            @NonNull Ratio ratio,
            @Nullable Callback callback) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Result result;
            try {
                result = startBlocking(app, pkg1, pkg2, ratio);
            } catch (Exception e) {
                Log.w(TAG, "split start failed", e);
                result = Result.LAUNCH_FAILED;
            }
            final Result finalResult = result;
            if (callback != null) {
                main.post(() -> callback.onResult(finalResult));
            }
        }, "split-screen-start").start();
    }

    private static Result startBlocking(Context app, String pkg1, String pkg2, Ratio ratio)
            throws InterruptedException {
        if (pkg1.equals(pkg2)) {
            return Result.SAME_APP;
        }
        String self = app.getPackageName();
        if (pkg1.equals(self) || pkg2.equals(self)) {
            return Result.NOT_INSTALLED;
        }
        PackageManager pm = app.getPackageManager();
        if (pm.getLaunchIntentForPackage(pkg1) == null || pm.getLaunchIntentForPackage(pkg2) == null) {
            return Result.NOT_INSTALLED;
        }

        boolean wasOn = isFreeformOn(app);
        if (!ensureFreeform(app)) {
            return Result.NO_FREEFORM;
        }

        // Açık görev yeni sınırları almaz; MyCar süreci düşürür. Yalnızca device owner iken mümkün.
        boolean recycled = recycleProcess(app, pkg1);
        recycled |= recycleProcess(app, pkg2);
        if (recycled || !wasOn) {
            Thread.sleep(RECYCLE_WAIT_MS);
        }

        Rect usable = usableBounds(app);
        if (usable.width() <= 0 || usable.height() <= 0) {
            Log.e(TAG, "Invalid usable bounds: " + usable);
            return Result.BAD_BOUNDS;
        }
        Rect[] parts = split(usable, ratio);
        boolean first = launchFreeform(app, pkg1, parts[0]);
        boolean second = launchFreeform(app, pkg2, parts[1]);
        Log.d(TAG, "first=" + first + " second=" + second + " usable=" + usable);
        return first && second ? Result.STARTED : Result.LAUNCH_FAILED;
    }

    /** Paketi askıya alıp geri açarak çalışan süreci düşürür (MyCar {@code A()}). */
    private static boolean recycleProcess(Context context, String packageName) {
        if (packageName.equals(context.getPackageName()) || !isDeviceOwner(context)) {
            return false;
        }
        try {
            DevicePolicyManager dpm =
                    (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(context, MapControlDeviceAdminReceiver.class);
            dpm.setPackagesSuspended(admin, new String[]{packageName}, true);
            dpm.setPackagesSuspended(admin, new String[]{packageName}, false);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "recycle failed for " + packageName, e);
            return false;
        }
    }

    /** Sistem çubukları ve kesim düşülmüş kullanılabilir alan (MyCar {@code o()}). */
    @NonNull
    static Rect usableBounds(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            return new Rect();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                WindowMetrics metrics = wm.getMaximumWindowMetrics();
                Rect rect = new Rect(metrics.getBounds());
                Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                        WindowInsets.Type.systemBars()
                                | WindowInsets.Type.displayCutout()
                                | WindowInsets.Type.navigationBars()
                                | WindowInsets.Type.statusBars());
                rect.left += insets.left;
                rect.top += insets.top;
                rect.right -= insets.right;
                rect.bottom -= insets.bottom;
                return rect;
            } catch (Throwable t) {
                Log.w(TAG, "WindowMetrics failed, falling back", t);
            }
        }
        try {
            Display display = wm.getDefaultDisplay();
            Rect rect = new Rect();
            display.getRectSize(rect);
            if (rect.width() <= 0 || rect.height() <= 0) {
                Point size = new Point();
                display.getRealSize(size);
                rect.set(0, 0, size.x, size.y);
            }
            return rect;
        } catch (Throwable t) {
            Log.e(TAG, "usable bounds failed", t);
            return new Rect();
        }
    }

    /** Yatay ekranda sol/sağ, dikeyde üst/alt. */
    @NonNull
    static Rect[] split(@NonNull Rect usable, @NonNull Ratio ratio) {
        float share = ratio.firstShare;
        if (usable.width() >= usable.height()) {
            int edge = usable.left + (int) (usable.width() * share);
            return new Rect[]{
                    new Rect(usable.left, usable.top, edge, usable.bottom),
                    new Rect(edge, usable.top, usable.right, usable.bottom)};
        }
        int edge = usable.top + (int) (usable.height() * share);
        return new Rect[]{
                new Rect(usable.left, usable.top, usable.right, edge),
                new Rect(usable.left, edge, usable.right, usable.bottom)};
    }

    /** Ekranın yatay mı bölüneceği; önizleme ile gerçek bölme aynı kuralı kullanır. */
    public static boolean splitsSideBySide(@NonNull Context context) {
        android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return dm.widthPixels >= dm.heightPixels;
    }

    /** MyCar {@code r()}: pencere modu, sınırlar ve ekran 0. Anahtarlar birebir korunur. */
    private static boolean launchFreeform(Context context, String packageName, Rect bounds) {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent == null) {
                return false;
            }
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setFlags(LAUNCH_FLAGS);

            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchBounds(new Rect(bounds));
            options.setLaunchDisplayId(Display.DEFAULT_DISPLAY);
            Bundle bundle = options.toBundle();
            if (bundle == null) {
                bundle = new Bundle();
            }
            bundle.putInt("android.activity.windowingMode", WINDOWING_MODE_FREEFORM);
            bundle.putInt("android:windowingMode", WINDOWING_MODE_FREEFORM);
            bundle.putParcelable("android.activity.launchBounds", new Rect(bounds));
            bundle.putParcelable("android:launchBounds", new Rect(bounds));
            bundle.putInt("android:freeformWindowSize", 1);
            bundle.putInt("android:multiWindowMode", 1);
            bundle.putBoolean("android:overrideTransition", true);
            context.startActivity(intent, bundle);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "launch failed: " + packageName, e);
            return false;
        }
    }
}
