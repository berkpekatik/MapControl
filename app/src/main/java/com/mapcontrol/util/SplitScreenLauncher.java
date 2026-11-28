package com.mapcontrol.util;

import android.app.ActivityManager;
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
 * dikdörtgeninde açılır.
 *
 * <p>Mode 6 (Desay launcher oturumu) bilerek taşınmadı: hidden API muafiyeti ve launcher
 * sözleşmesi gerektirir, her araç imajında yoktur.
 */
public final class SplitScreenLauncher {

    private static final String TAG = "SplitScreen";

    /** {@code Settings.Global}: freeform pencere desteğini açar. */
    private static final String SETTING_ENABLE_FREEFORM = "enable_freeform_support";
    private static final String FEATURE_FREEFORM = "android.software.freeform_window_management";

    /** {@code ActivityOptions.WINDOWING_MODE_FREEFORM}. */
    private static final int WINDOWING_MODE_FREEFORM = 5;
    /** Süreç düşürüldükten sonra beklenen süre. */
    private static final long RECYCLE_WAIT_MS = 600L;
    /** NEW_TASK | RESET_TASK_IF_NEEDED | REORDER_TO_FRONT. */
    private static final int LAUNCH_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT;

    private SplitScreenLauncher() {
    }

    /** İlk uygulamanın ekrandaki payı. */
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
        if (!SplitSlot.isAllowed(pkg1, self) || !SplitSlot.isAllowed(pkg2, self)) {
            return Result.NOT_INSTALLED;
        }
        if (launchIntent(app, pkg1) == null || launchIntent(app, pkg2) == null) {
            return Result.NOT_INSTALLED;
        }

        boolean wasOn = isFreeformOn(app);
        if (!ensureFreeform(app)) {
            return Result.NO_FREEFORM;
        }

        // Açık görev yeni sınırları almaz; bu yüzden süreç düşürülür. Yalnızca device owner iken mümkün.
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
        // Kullanıcının bölme yönü (Otomatik / Sol-sağ / Üst-alt).
        Rect[] parts = split(usable, ratio, SplitScreenStore.getAxis(app));
        // Serbest pencere başlığı (küçült/kapat, ~42 px) pencerenin içinde çizilir; üst kenarı kullanılabilir alanın üstüne
        // değen yarılar başlık kadar yukarı alınır ki başlık durum çubuğunun altında kalsın ve içerik tam yarıyı alsın.
        int caption = captionHeightPx(app);
        for (Rect part : parts) {
            part.top = tuckedTop(part.top, usable.top, caption);
        }
        boolean first = launchFreeform(app, pkg1, parts[0]);
        boolean second = launchFreeform(app, pkg2, parts[1]);
        Log.d(TAG, "first=" + first + " second=" + second + " usable=" + usable);
        if (first && second) {
            // Başka uygulamanın önceden açık görevi açılış sınırlarını almaz (eski yerinde öne gelir): görev kimliği olay
            // günlüğünden bulunup sınırlarla yeniden öne alınır.
            Thread.sleep(PLACE_WAIT_MS);
            String[] slots = {pkg1, pkg2};
            for (int i = 0; i < slots.length; i++) {
                placeExistingTask(app, SplitSlot.packageOf(slots[i]), parts[i]);
            }
        }
        return first && second ? Result.STARTED : Result.LAUNCH_FAILED;
    }

    /** Açılıştan sonra görevin olay günlüğüne düşmesi için beklenen süre. */
    private static final long PLACE_WAIT_MS = 700L;
    /** Görev yerleştirmesi tekrarlanmadan önce beklenen süre (tam ekrandan serbest pencereye geçişin oturması). */
    private static final long PLACE_AGAIN_MS = 250L;

    /** {@link #findTaskId}: olay günlüğü okunamadı (READ_LOGS yok); görev olup olmadığı bilinmiyor. */
    private static final int TASK_UNKNOWN = -2;

    /** [pkg]'nin olay günlüğündeki en son görev kimliği; günlükte yoksa -1, günlük okunamazsa {@link #TASK_UNKNOWN}. */
    private static int findTaskId(String pkg) {
        try {
            Process p = new ProcessBuilder(SplitTaskFinder.logcatArgs()).redirectErrorStream(true).start();
            String out;
            try (java.io.InputStream in = p.getInputStream()) {
                java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) {
                    buf.write(b, 0, n);
                }
                out = buf.toString("UTF-8");
            }
            p.waitFor();
            return SplitTaskFinder.lastTaskId(out, pkg);
        } catch (Exception e) {
            Log.w(TAG, "event log unreadable for " + pkg + ": " + e.getClass().getSimpleName());
            return TASK_UNKNOWN;
        }
    }

    /**
     * [pkg]'nin görevini ([SplitTaskFinder] ile olay günlüğünden) bulup {@code moveTaskToFront} ile [bounds]'a yerleştirir.
     * Yeni açılmış görevde zararsızdır (sınırlar zaten aynı). READ_LOGS yoksa ya da görev bulunamazsa yalnız loglanır.
     */
    private static void placeExistingTask(Context context, String pkg, Rect bounds) {
        int taskId = findTaskId(pkg);
        if (taskId < 0) {
            Log.w(TAG, "place: no task id for " + pkg);
            return;
        }
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return;
            }
            am.moveTaskToFront(taskId, 0, freeformOptions(bounds));
            // Tam ekran görev (ör. harita ana ekrandan açılmıştı) ilk çağrıda serbest pencereye geçer ama sınır olarak kendi "son serbest
            // pencere" yerini alır (öykünücü, 7 Kasım: 0,24-1920,744); ikinci çağrı artık serbest pencere görevini [bounds]'a taşır.
            // Zaten serbest pencereyse zararsız (aynı sınır).
            try {
                Thread.sleep(PLACE_AGAIN_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            am.moveTaskToFront(taskId, 0, freeformOptions(bounds));
            Log.i(TAG, "place: task " + taskId + " (" + pkg + ") -> " + bounds);
        } catch (RuntimeException e) {
            Log.w(TAG, "place: moveTaskToFront failed for " + pkg + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Kutu için açılış intent'i: "pkg/cls" ise açık bileşen, değilse paketin launcher intent'i.
     */
    @Nullable
    private static Intent launchIntent(Context context, String slot) {
        PackageManager pm = context.getPackageManager();
        if (!SplitSlot.isComponent(slot)) {
            return pm.getLaunchIntentForPackage(slot);
        }
        ComponentName cn = new ComponentName(SplitSlot.packageOf(slot), SplitSlot.classOf(slot));
        try {
            pm.getActivityInfo(cn, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        return new Intent(Intent.ACTION_MAIN).setComponent(cn);
    }

    /** Paketi askıya alıp geri açarak çalışan süreci düşürür. */
    private static boolean recycleProcess(Context context, String slot) {
        String packageName = SplitSlot.packageOf(slot);
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

    /**
     * Android 11 serbest pencere başlığının yüksekliği (dp): {@code decor_caption.xml} 32 dp düğme + 2 × 5 dp kenar.
     * Araçta (160 dpi) 42 px; ekran görüntüsünde simgeler pencere üstünden ~21 px aşağıda ortalanıyor.
     */
    static final int CAPTION_HEIGHT_DP = 42;

    static int captionHeightPx(@NonNull Context context) {
        return Math.round(CAPTION_HEIGHT_DP * context.getResources().getDisplayMetrics().density);
    }

    /**
     * Saf kural (birim testlenir): üst kenarı kullanılabilir alanın üstüne ({@code usableTop}, durum çubuğunun altı)
     * denk gelen pencere başlık kadar yukarı başlar; başlık durum çubuğunun altında kalır (durum çubuğu uygulama
     * pencerelerinin üstünde çizilir). Üstte başlığı saklayacak kadar alan yoksa ya da yarı aşağıdaysa (üst/alt bölmede
     * alttaki) değişmez: alttaki yarının başlığı üsttekinin içeriğini örtmesin. Pencere boyutu değiştirme kenarları aynen kalır.
     */
    static int tuckedTop(int partTop, int usableTop, int captionPx) {
        if (captionPx <= 0 || partTop != usableTop || usableTop < captionPx) {
            return partTop;
        }
        return partTop - captionPx;
    }

    /** Sistem çubukları ve kesim düşülmüş kullanılabilir alan. */
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

    /** Tiggo 8 sınıfı ekran: uzun kenar bu pikselden uzun. */
    static final int TOP_BOTTOM_LONG_SIDE_PX = 2400;
    /** Tiggo 8 sınıfı ekran: en-boy oranı bundan küçük (Tiggo 7 1920x720 = 2,67). */
    static final float TOP_BOTTOM_MAX_ASPECT = 2.2f;

    /**
     * Saf kural (birim testlenir): yatay ve Tiggo 8 sınıfı (uzun kenar &gt;= 2400 px, oran &lt; 2,2) ekran üst/alt,
     * diğer yatay ekran sol/sağ, dikey ekran üst/alt bölünür. Böylece 2560x1440 ekranda her yarı 2560x~720 olur
     * (Tiggo 7 ekranıyla aynı oran).
     */
    static boolean sideBySideFor(int width, int height) {
        if (width < height) {
            return false;
        }
        if (height <= 0) {
            return true;
        }
        boolean tiggo8Class = width >= TOP_BOTTOM_LONG_SIDE_PX
                && (float) width / height < TOP_BOTTOM_MAX_ASPECT;
        return !tiggo8Class;
    }

    /** Bölme yönü: otomatik (ekran ölçüsüne göre, {@link #sideBySideFor}) ya da kullanıcının elle seçtiği. */
    public enum Axis {
        AUTO("auto"),
        SIDE_BY_SIDE("side"),
        TOP_BOTTOM("stack");

        @NonNull
        public final String id;

        Axis(@NonNull String id) {
            this.id = id;
        }

        @NonNull
        public static Axis fromId(@Nullable String id) {
            for (Axis axis : values()) {
                if (axis.id.equals(id)) {
                    return axis;
                }
            }
            return AUTO;
        }
    }

    /** Saf kural (birim testlenir): elle seçim önce gelir, otomatikte {@link #sideBySideFor}. */
    static boolean sideBySide(@NonNull Axis axis, int width, int height) {
        switch (axis) {
            case SIDE_BY_SIDE:
                return true;
            case TOP_BOTTOM:
                return false;
            default:
                return sideBySideFor(width, height);
        }
    }

    /** Otomatik yönde bölme (sürüş ekranı). */
    @NonNull
    static Rect[] split(@NonNull Rect usable, @NonNull Ratio ratio) {
        return split(usable, ratio, Axis.AUTO);
    }

    /** Sol/sağ ya da üst/alt; eksen {@link #sideBySide}. Oran bu eksene uygulanır. */
    @NonNull
    static Rect[] split(@NonNull Rect usable, @NonNull Ratio ratio, @NonNull Axis axis) {
        float share = ratio.firstShare;
        if (sideBySide(axis, usable.width(), usable.height())) {
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

    /** Ekranın yan yana mı bölüneceği (kayıtlı yönle); önizleme ile gerçek bölme aynı kuralı kullanır. */
    public static boolean splitsSideBySide(@NonNull Context context) {
        return splitsSideBySide(context, SplitScreenStore.getAxis(context));
    }

    public static boolean splitsSideBySide(@NonNull Context context, @NonNull Axis axis) {
        android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return sideBySide(axis, dm.widthPixels, dm.heightPixels);
    }

    /** Serbest pencere açılış seçenekleri (aşağıdaki pencere seçenekleriyle aynı anahtarlar): mod 5, [bounds], ekran 0. */
    @NonNull
    private static Bundle freeformOptions(Rect bounds) {
        return windowingOptions(bounds, WINDOWING_MODE_FREEFORM);
    }

    /** Pencere modu [mode] ve [bounds] (null: sınır yok) ile açılış seçenekleri (anahtarlar {@link #freeformOptions} ile aynı). */
    @NonNull
    private static Bundle windowingOptions(@Nullable Rect bounds, int mode) {
        ActivityOptions options = ActivityOptions.makeBasic();
        if (bounds != null) {
            options.setLaunchBounds(new Rect(bounds));
        }
        options.setLaunchDisplayId(Display.DEFAULT_DISPLAY);
        Bundle bundle = options.toBundle();
        if (bundle == null) {
            bundle = new Bundle();
        }
        bundle.putInt("android.activity.windowingMode", mode);
        bundle.putInt("android:windowingMode", mode);
        if (bounds != null) {
            bundle.putParcelable("android.activity.launchBounds", new Rect(bounds));
            bundle.putParcelable("android:launchBounds", new Rect(bounds));
        }
        if (mode == WINDOWING_MODE_FREEFORM) {
            bundle.putInt("android:freeformWindowSize", 1);
            bundle.putInt("android:multiWindowMode", 1);
        }
        bundle.putBoolean("android:overrideTransition", true);
        return bundle;
    }

    /** Pencere modu, sınırlar ve ekran 0; kutunun başlatıcı intent'i açılır. */
    private static boolean launchFreeform(Context context, String packageName, Rect bounds) {
        try {
            Intent intent = launchIntent(context, packageName);
            if (intent == null) {
                return false;
            }
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setFlags(LAUNCH_FLAGS);
            context.startActivity(intent, freeformOptions(bounds));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "launch failed: " + packageName, e);
            return false;
        }
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Tam ekran harita (Navigasyon kartı, harita seçici)
    // ------------------------------------------------------------------------------------------------------------------

    /** {@code WINDOWING_MODE_FULLSCREEN}. */
    private static final int WINDOWING_MODE_FULLSCREEN = 1;

    /**
     * Haritayı tam ekran yapar ({@link #openFullScreen}); arka planda, sonuç ana iş parçacığında. {@link Result#STARTED} değilse
     * harita yerinden oynamamış olabilir.
     */
    public static void mapFullScreen(@NonNull Context context, @NonNull String pkg, @Nullable Callback callback) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Result result = openFullScreen(app, pkg);
            if (callback != null) {
                main.post(() -> callback.onResult(result));
            }
        }, "split-map-full").start();
    }

    /**
     * [pkg]'yi tam ekran açar; serbest pencerede (ör. bölünmüş ekranın yarısı) kalmış görevi varsa onu tüm kullanılabilir alana büyütür.
     * Engelleyen iş parçacığında çağrılır (olay günlüğü okunur).
     *
     * <p>Görev olay günlüğünden bulunursa {@code moveTaskToFront} ile SERBEST PENCERE olarak tüm alana (durum çubuğunun altı)
     * büyütülür; tam ekran pencere moduna geçirilmez. Öykünücü, 7 Kasım: tam ekran moduna geçişte (yarının sınırıyla da, ekranın
     * tamamıyla da) görev 1920x720 oldu ama Yandex 960x680'de çizmeye devam etti (ekranın geri kalanı siyah, başlık duruyor); sınırsız
     * geçişte ise görev hiç taşınmadı. Serbest pencere boyutlandırmasını Yandex sürüş ekranına yerleşirken zaten düzgün karşılıyor;
     * sonraki sürüş ekranında harita yarısına {@link #placeExistingTask} ile yine aynı yolla yerleştirilir. Başlık (küçült / X) bu
     * yüzden tam ekranda da kalır. Ardından başlatıcı intent'i tam ekran açılır: görev kapanmışsa (ör. başlıktaki X) yeni tam ekran
     * görev açılır, açıksa yalnız öne gelir (yalnız intent açık serbest pencere görevini taşımaz). Günlük okunamazsa (READ_LOGS yok)
     * intent yine açılır ama sonuç {@link Result#LAUNCH_FAILED} olur (harita yarıda kalmış olabilir).
     */
    @NonNull
    static Result openFullScreen(@NonNull Context app, @NonNull String pkg) {
        int taskId = findTaskId(pkg);
        boolean moved = false;
        if (taskId > 0) {
            try {
                ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    Rect usable = usableBounds(app);
                    am.moveTaskToFront(taskId, 0, usable.isEmpty()
                            ? windowingOptions(displayBounds(app), WINDOWING_MODE_FULLSCREEN)
                            : freeformOptions(usable));
                    Log.i(TAG, "full: task " + taskId + " (" + pkg + ")");
                    moved = true;
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "full: moveTaskToFront failed for " + pkg + ": " + e.getClass().getSimpleName());
            }
        }
        boolean launched = false;
        try {
            Intent intent = launchIntent(app, pkg);
            if (intent != null) {
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.setFlags(LAUNCH_FLAGS);
                app.startActivity(intent, windowingOptions(null, WINDOWING_MODE_FULLSCREEN));
                Log.i(TAG, "full: launched " + pkg);
                launched = true;
            }
        } catch (Exception e) {
            Log.w(TAG, "full: launch failed for " + pkg + ": " + e.getClass().getSimpleName());
        }
        return fullScreenResult(taskId, moved, launched);
    }

    /** Ekranın tamamı (sistem çubukları dahil); okunamazsa null. */
    @Nullable
    private static Rect displayBounds(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            return null;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return new Rect(wm.getMaximumWindowMetrics().getBounds());
            }
            Point size = new Point();
            wm.getDefaultDisplay().getRealSize(size);
            return new Rect(0, 0, size.x, size.y);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Saf kural (birim testlenir): tam ekran yapıldı mı. Görev taşındıysa evet; günlükte görev yoksa ([taskId] -1) intent'le yeni
     * tam ekran görev açıldıysa evet; günlük okunamadıysa ({@link #TASK_UNKNOWN}) ya da taşıma başarısızsa hayır (serbest pencere
     * görevi yarıda kalmış olabilir).
     */
    static Result fullScreenResult(int taskId, boolean moved, boolean launched) {
        if (moved) {
            return Result.STARTED;
        }
        if (taskId == -1 && launched) {
            return Result.STARTED;
        }
        return Result.LAUNCH_FAILED;
    }

    /**
     * Haritayı tam ekran açar ({@link #openFullScreen}); arka planda. Serbest pencerede kalmış görevi tüm alana taşınır, yoksa
     * Android açık serbest pencere görevini ana ekranın üstünde yarım pencere olarak öne getirir.
     */
    public static void openFullScreenAsync(@NonNull Context context, @NonNull String pkg) {
        final Context app = context.getApplicationContext();
        new Thread(() -> openFullScreen(app, pkg), "split-open-full").start();
    }

    /**
     * Eski sürüş ekranından süreç ölümüyle yarıda kalmış haritayı ({@code MapLaunch.recoverStale}) tüm kullanılabilir alana büyütür.
     * Engelleyen iş parçacığında çağrılır. {@link #openFullScreen}'den farkı: görev günlükte yoksa uygulama BAŞLATILMAZ; uygulama
     * hiçbir koşulda kapatılmaz. Görev yoksa / günlük okunamazsa / taşıma başarısızsa hiçbir şey yapılmaz.
     */
    public static void restoreStaleMap(@NonNull Context context, @NonNull String pkg) {
        Context app = context.getApplicationContext();
        int taskId = findTaskId(pkg);
        if (taskId <= 0) {
            Log.i(TAG, "stale: no task for " + pkg);
            return;
        }
        try {
            ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
            Rect usable = usableBounds(app);
            if (am == null || usable.isEmpty()) {
                return;
            }
            am.moveTaskToFront(taskId, 0, freeformOptions(usable));
            Log.i(TAG, "stale: task " + taskId + " restored");
        } catch (RuntimeException e) {
            Log.w(TAG, "stale: restore failed: " + e.getClass().getSimpleName());
        }
    }
}
