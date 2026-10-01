package com.mapcontrol.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;

import java.util.List;

/**
 * Launcher / hızlı erişim ikonları. OEM sistem uygulamalarında gerçek ikon çoğu zaman
 * {@link PackageManager#getApplicationIcon} yerine launcher activity kaynağındadır;
 * aksi halde yeşil varsayılan robot görünür.
 */
public final class AppIconHelper {

    private static final String[] LAUNCHER_CATEGORIES = {
            Intent.CATEGORY_LAUNCHER,
            "android.intent.category.CAR_LAUNCHER",
            "android.car.intent.category.LAUNCHER",
            Intent.CATEGORY_LEANBACK_LAUNCHER,
    };

    private AppIconHelper() {
    }

    public static void apply(@NonNull ImageView view, @Nullable String packageName) {
        apply(view, packageName, null);
    }

    public static void apply(
            @NonNull ImageView view,
            @Nullable String packageName,
            @Nullable String activityName) {
        view.clearColorFilter();
        ImageViewCompat.setImageTintList(view, null);
        view.setImageDrawable(load(view.getContext(), packageName, activityName));
    }

    @NonNull
    public static Drawable load(@NonNull Context context, @Nullable String packageName) {
        return load(context, packageName, null);
    }

    @NonNull
    public static Drawable load(
            @NonNull Context context,
            @Nullable String packageName,
            @Nullable String activityName) {
        PackageManager pm = context.getPackageManager();
        Drawable fallback = pm.getDefaultActivityIcon();
        if (packageName == null || packageName.isEmpty()) {
            return fallback != null ? fallback : loadSymDef(context);
        }

        int density = context.getResources().getDisplayMetrics().densityDpi;

        Drawable icon = loadFromLauncherApps(context, packageName, activityName, density);
        if (isRealIcon(pm, icon)) {
            return toImageViewDrawable(context, icon);
        }

        icon = loadFromComponentResources(context, pm, packageName, activityName, density);
        if (isRealIcon(pm, icon)) {
            return toImageViewDrawable(context, icon);
        }

        icon = loadFromResolvedLauncher(context, pm, packageName, density);
        if (isRealIcon(pm, icon)) {
            return toImageViewDrawable(context, icon);
        }

        icon = loadFromLaunchIntent(pm, packageName);
        if (isRealIcon(pm, icon)) {
            return toImageViewDrawable(context, icon);
        }

        try {
            icon = pm.getApplicationIcon(packageName);
            if (icon != null) {
                return toImageViewDrawable(context, icon);
            }
        } catch (Exception ignored) {
        }
        return fallback != null ? fallback : loadSymDef(context);
    }

    @Nullable
    private static Drawable loadFromLauncherApps(
            Context context,
            String packageName,
            @Nullable String activityName,
            int density) {
        try {
            LauncherApps launcherApps =
                    (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (launcherApps == null) {
                return null;
            }
            List<LauncherActivityInfo> activities =
                    launcherApps.getActivityList(packageName, Process.myUserHandle());
            if (activities == null || activities.isEmpty()) {
                return null;
            }
            if (activityName != null && !activityName.isEmpty()) {
                for (LauncherActivityInfo info : activities) {
                    ComponentName cn = info.getComponentName();
                    if (cn != null && activityName.equals(cn.getClassName())) {
                        return info.getIcon(density);
                    }
                }
            }
            return activities.get(0).getIcon(density);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static Drawable loadFromComponentResources(
            Context context,
            PackageManager pm,
            String packageName,
            @Nullable String activityName,
            int density) {
        if (activityName != null && !activityName.isEmpty()) {
            try {
                return loadActivityResources(
                        context, pm, new ComponentName(packageName, activityName), density);
            } catch (Exception ignored) {
            }
        }
        try {
            Intent launch = pm.getLaunchIntentForPackage(packageName);
            if (launch != null && launch.getComponent() != null) {
                return loadActivityResources(context, pm, launch.getComponent(), density);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    private static Drawable loadFromResolvedLauncher(
            Context context,
            PackageManager pm,
            String packageName,
            int density) {
        for (String category : LAUNCHER_CATEGORIES) {
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(category);
                intent.setPackage(packageName);
                List<ResolveInfo> matches = pm.queryIntentActivities(intent, 0);
                if (matches == null || matches.isEmpty()) {
                    continue;
                }
                ResolveInfo info = matches.get(0);
                if (info.activityInfo != null && info.activityInfo.name != null) {
                    Drawable fromRes = loadActivityResources(
                            context,
                            pm,
                            new ComponentName(packageName, info.activityInfo.name),
                            density);
                    if (isRealIcon(pm, fromRes)) {
                        return fromRes;
                    }
                }
                Drawable loaded = info.loadIcon(pm);
                if (isRealIcon(pm, loaded)) {
                    return loaded;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Nullable
    private static Drawable loadFromLaunchIntent(PackageManager pm, String packageName) {
        try {
            Intent launch = pm.getLaunchIntentForPackage(packageName);
            if (launch != null) {
                return pm.getActivityIcon(launch);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    private static Drawable loadActivityResources(
            Context context,
            PackageManager pm,
            ComponentName component,
            int density) throws PackageManager.NameNotFoundException {
        ActivityInfo activityInfo = pm.getActivityInfo(component, 0);
        Drawable icon = drawableForRes(
                context, pm, activityInfo.applicationInfo, activityInfo.icon, density);
        if (icon != null) {
            return icon;
        }
        int activityIcon = activityInfo.getIconResource();
        icon = drawableForRes(context, pm, activityInfo.applicationInfo, activityIcon, density);
        if (icon != null) {
            return icon;
        }
        ApplicationInfo appInfo = activityInfo.applicationInfo;
        if (appInfo != null) {
            icon = drawableForRes(context, pm, appInfo, appInfo.icon, density);
            if (icon != null) {
                return icon;
            }
            icon = drawableForRes(context, pm, appInfo, appInfo.logo, density);
            if (icon != null) {
                return icon;
            }
            icon = drawableForRes(context, pm, appInfo, appInfo.banner, density);
            if (icon != null) {
                return icon;
            }
        }
        try {
            Drawable loaded = activityInfo.loadIcon(pm);
            if (isRealIcon(pm, loaded)) {
                return loaded;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    private static Drawable drawableForRes(
            Context context,
            PackageManager pm,
            ApplicationInfo appInfo,
            int resId,
            int density) {
        if (appInfo == null || resId == 0) {
            return null;
        }
        try {
            Resources resources = pm.getResourcesForApplication(appInfo);
            Drawable d = resources.getDrawableForDensity(resId, density, null);
            if (d != null) {
                return d;
            }
        } catch (Exception ignored) {
        }
        try {
            Context pkgContext = context.createPackageContext(
                    appInfo.packageName, Context.CONTEXT_IGNORE_SECURITY);
            Drawable d = pkgContext.getResources().getDrawableForDensity(resId, density, null);
            if (d != null) {
                return d;
            }
        } catch (Exception ignored) {
        }
        try {
            return pm.getDrawable(appInfo.packageName, resId, appInfo);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isRealIcon(PackageManager pm, @Nullable Drawable icon) {
        if (icon == null) {
            return false;
        }
        Drawable def = pm.getDefaultActivityIcon();
        if (def == null) {
            return true;
        }
        Drawable.ConstantState a = icon.getConstantState();
        Drawable.ConstantState b = def.getConstantState();
        return a == null || b == null || !a.equals(b);
    }

    @NonNull
    private static Drawable toImageViewDrawable(@NonNull Context context, @NonNull Drawable drawable) {
        if (!(drawable instanceof AdaptiveIconDrawable)) {
            return drawable;
        }
        int size = Math.max(1, Math.round(48f * context.getResources().getDisplayMetrics().density));
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    @NonNull
    private static Drawable loadSymDef(Context context) {
        Drawable d = context.getDrawable(android.R.drawable.sym_def_app_icon);
        return d != null ? d : new BitmapDrawable(context.getResources(),
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888));
    }
}
