package com.mapcontrol.vehicle.material;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Yüklü materialvehicle paketlerini ve önizleme görsellerini listeler. */
public final class MaterialVehicleCatalog {

    private static final String TAG = "MaterialVehicleCat";
    private static final String PACKAGE_PREFIX = "com.desaysv.materialvehicle";

    public static final class Entry {
        public final String packageName;
        public final String drawableName;
        public final String label;
        @Nullable public final Bitmap preview;

        Entry(String packageName, String drawableName, String label, @Nullable Bitmap preview) {
            this.packageName = packageName;
            this.drawableName = drawableName;
            this.label = label;
            this.preview = preview;
        }
    }

    private MaterialVehicleCatalog() {
    }

    public static List<Entry> loadInstalled(Context context) {
        List<String> packages = listInstalledPackages(context);
        List<Entry> entries = new ArrayList<>();
        for (String pkg : packages) {
            Entry entry = probePackage(context, pkg);
            if (entry != null) {
                entries.add(entry);
            }
        }
        Collections.sort(entries, Comparator.comparing(e -> e.packageName));
        Log.i(TAG, "Bulunan materialvehicle paketleri: " + entries.size());
        return entries;
    }

    @Nullable
    public static Entry probePackage(Context context, String packageName) {
        MaterialVehicleResources probe = MaterialVehicleResources.createProbe(context, packageName);
        if (!probe.isReady()) {
            return null;
        }
        String drawable = probe.getCarDrawableBaseName();
        Bitmap preview = probe.loadCarModelBitmap();
        if (preview == null) {
            return null;
        }
        return new Entry(packageName, drawable, MaterialVehiclePreferences.friendlyLabel(packageName), preview);
    }

    private static List<String> listInstalledPackages(Context context) {
        List<String> installed = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        try {
            List<PackageInfo> packages = pm.getInstalledPackages(0);
            for (PackageInfo info : packages) {
                if (info.packageName != null
                        && (info.packageName.startsWith(PACKAGE_PREFIX)
                        || info.packageName.startsWith("com.desaysv.materialvehiclet"))) {
                    installed.add(info.packageName);
                }
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Paket listesi alınamadı", e);
        }
        return installed;
    }
}
