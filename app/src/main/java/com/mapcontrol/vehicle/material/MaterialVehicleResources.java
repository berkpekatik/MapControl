package com.mapcontrol.vehicle.material;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Arrays;

/**
 * OEM materialvehicle APK'sından drawable yükler.
 * Öncelik: kullanıcı seçimi → EOL → genel paket.
 */
public final class MaterialVehicleResources {

    private static final String TAG = "MaterialVehicleRes";
    private static final String CAR_MODEL_BASE = "img_car_energy_conservation";
    private static final String GENERIC_PACKAGE = "com.desaysv.materialvehicle";

    private static volatile MaterialVehicleResources instance;

    private Context appContext;
    private String packageName;
    private Resources resources;
    private String carDrawableBaseName = CAR_MODEL_BASE;
    private String frameSuffix = "";
    private String platformLabel;
    private String sourceLabel = "AUTO";

    private MaterialVehicleResources() {
    }

    public static MaterialVehicleResources getInstance() {
        if (instance == null) {
            synchronized (MaterialVehicleResources.class) {
                if (instance == null) {
                    instance = new MaterialVehicleResources();
                }
            }
        }
        return instance;
    }

    /** Paket taraması / önizleme için geçici örnek. */
    static MaterialVehicleResources createProbe(Context context, String packageName) {
        MaterialVehicleResources probe = new MaterialVehicleResources();
        probe.appContext = context.getApplicationContext();
        probe.applyPackage(packageName, CAR_MODEL_BASE, "PROBE");
        return probe;
    }

    public synchronized void init(Context context) {
        appContext = context.getApplicationContext();

        MaterialVehiclePreferences.Selection manual = MaterialVehiclePreferences.getSelection(appContext);
        if (manual != null && isPackageInstalled(appContext, manual.packageName)) {
            applyPackage(manual.packageName, manual.drawableName, "MANUAL:" + manual.label);
            Log.i(TAG, "Manuel seçim: " + manual.packageName + " / " + manual.drawableName);
            return;
        }

        if (MaterialVehiclePreferences.isAutoDetectionEnabled(appContext)) {
            initWithAutoDetection(context);
            return;
        }

        packageName = null;
        resources = null;
        sourceLabel = "UNSET";
        platformLabel = null;
    }

    /** Kullanıcı listeden "Otomatik algılama" seçtiğinde — EOL taraması yapılır. */
    public synchronized void initWithAutoDetection(Context context) {
        appContext = context.getApplicationContext();
        MaterialVehiclePackageResolver.Resolution resolution =
                MaterialVehiclePackageResolver.resolve(appContext);
        if (resolution.packageName != null) {
            applyPackage(resolution.packageName, resolution.carDrawableBaseName,
                    resolution.platformLabel != null ? "EOL:" + resolution.platformLabel : "EOL");
            return;
        }
        if (isPackageInstalled(appContext, GENERIC_PACKAGE)) {
            applyPackage(GENERIC_PACKAGE, resolution.carDrawableBaseName, "GENERIC");
            Log.i(TAG, "Yedek paket: " + GENERIC_PACKAGE);
            return;
        }
        packageName = null;
        resources = null;
        sourceLabel = "AUTO_FAILED";
        Log.w(TAG, "Otomatik algılama başarısız");
    }

    private void applyPackage(String pkg, String drawableBase, String source) {
        platformLabel = source;
        sourceLabel = source;
        carDrawableBaseName = drawableBase != null ? drawableBase : CAR_MODEL_BASE;
        updateFrameSuffix();
        packageName = pkg;
        resources = null;
        try {
            resources = appContext.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY)
                    .getResources();
            Log.i(TAG, "Kaynak paketi: " + pkg + " [" + source + "], drawable=" + carDrawableBaseName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Paket context açılamadı: " + pkg, e);
            packageName = null;
            resources = null;
        }
    }

    public boolean isReady() {
        return packageName != null && resources != null;
    }

    @Nullable
    public String getPackageName() {
        return packageName;
    }

    @Nullable
    public String getPlatformLabel() {
        return platformLabel;
    }

    public String getCarDrawableBaseName() {
        return carDrawableBaseName;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    @Nullable
    public Bitmap loadCarModelBitmap() {
        Bitmap primary = loadBitmap(carDrawableBaseName);
        if (primary != null) {
            return primary;
        }
        for (String name : drawableCandidates()) {
            if (name.equals(carDrawableBaseName)) {
                continue;
            }
            Bitmap bitmap = loadBitmap(name);
            if (bitmap != null) {
                carDrawableBaseName = name;
                updateFrameSuffix();
                return bitmap;
            }
        }
        return null;
    }

    private Iterable<String> drawableCandidates() {
        return Arrays.asList(
                CAR_MODEL_BASE,
                CAR_MODEL_BASE + "_face1",
                CAR_MODEL_BASE + "_face2");
    }

    @Nullable
    public Bitmap loadFrame(String prefix, int index) {
        return loadFrameBitmap(prefix, index);
    }

    @Nullable
    private Bitmap loadFrameBitmap(String prefix, int index) {
        if (!frameSuffix.isEmpty()) {
            Bitmap faced = loadBitmap(prefix + "_" + index + frameSuffix);
            if (faced != null) {
                return faced;
            }
        }
        return loadBitmap(prefix + "_" + index);
    }

    @Nullable
    public Bitmap loadBitmap(String drawableName) {
        if (resources == null || packageName == null || drawableName == null) {
            return null;
        }
        int id = resources.getIdentifier(drawableName, "drawable", packageName);
        if (id == 0) {
            return null;
        }
        try {
            Drawable drawable = resources.getDrawable(id, appContext.getTheme());
            if (drawable == null) {
                return null;
            }
            return drawableToBitmap(drawable);
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }

    public int findLastFrameIndex(String prefix, int maxIndex) {
        for (int i = maxIndex; i >= 0; i--) {
            if (loadFrameBitmap(prefix, i) != null) {
                return i;
            }
        }
        return -1;
    }

    private void updateFrameSuffix() {
        frameSuffix = deriveFrameSuffix(carDrawableBaseName);
    }

    /** T1EFL gibi paketlerde tailgate kareleri {@code tailgate_12_face1} biçiminde. */
    private static String deriveFrameSuffix(String drawableBase) {
        if (drawableBase == null) {
            return "";
        }
        if (drawableBase.endsWith("_face1")) {
            return "_face1";
        }
        if (drawableBase.endsWith("_face2")) {
            return "_face2";
        }
        return "";
    }

    private static boolean isPackageInstalled(Context context, String packageName) {
        if (context == null || packageName == null) {
            return false;
        }
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static Bitmap drawableToBitmap(Drawable drawable) {
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0 || height <= 0) {
            width = 1;
            height = 1;
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        return bitmap;
    }
}
