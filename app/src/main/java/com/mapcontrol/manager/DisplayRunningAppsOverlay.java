package com.mapcontrol.manager;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.ActivityDisplayTasksHelper;
import com.mapcontrol.util.AppForceStopHelper;
import com.mapcontrol.util.RunningAppsHelper;
import com.mapcontrol.util.UsageAccessHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Açık (süreci çalışan) kullanıcı uygulamaları, tüm ekranlar tek listede. Kısa tık: kapat; uzun bas:
 * uygulama bilgisi. Liste {@link RunningAppsHelper} ile dumpsys'ten gelir; DUMP izni yoksa son 1 saatin
 * kullanım geçmişine düşer. Eskiden 1. ve 2. ekran ayrı listelerdi ama araçta ekran kimliği okunamadığı için
 * ikisi aynı içeriği gösteriyordu.
 */
public final class DisplayRunningAppsOverlay {

    public interface LogCallback {
        void log(String message);
    }

    private static DisplayRunningAppsOverlay sInstance;

    private final Context appContext;
    private final Context themedContext;
    private final WindowManager windowManager;
    private final LogCallback logCallback;
    private View rootView;
    private final List<Row> rows = new ArrayList<>();
    private RowAdapter adapter;

    private static final class Row {
        final String packageName;
        final String label;
        /** null: durum bilinmiyor (kullanım geçmişi yedeği). */
        final RunningAppsHelper.State state;

        Row(String packageName, String label, RunningAppsHelper.State state) {
            this.packageName = packageName;
            this.label = label;
            this.state = state;
        }
    }

    private DisplayRunningAppsOverlay(Context appContext, LogCallback logCallback) {
        this.appContext = appContext.getApplicationContext();
        this.themedContext = new ContextThemeWrapper(this.appContext, R.style.Theme_MapControl);
        this.windowManager = (WindowManager) this.appContext.getSystemService(Context.WINDOW_SERVICE);
        this.logCallback = logCallback;
    }

    private void log(String msg) {
        if (logCallback == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(() -> logCallback.log(msg));
            return;
        }
        logCallback.log(msg);
    }

    /** Eski çağrılar (ekran başına liste) için; artık tek liste gösterilir. */
    public static void show(Context anyContext, int whichDisplay, String title, LogCallback logCallback) {
        show(anyContext, logCallback);
    }

    /** Ana iş parçacığından çağırın; liste arka planda toplanıp hazır olunca gösterilir. */
    public static void show(Context anyContext, LogCallback logCallback) {
        dismissIfShowing();
        DisplayRunningAppsOverlay o = new DisplayRunningAppsOverlay(anyContext, logCallback);
        sInstance = o;
        o.load();
    }

    public static void dismissIfShowing() {
        if (sInstance != null) {
            sInstance.detach();
            sInstance = null;
        }
    }

    private void load() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(appContext)) {
            Toast.makeText(appContext, R.string.target_picker_overlay_required, Toast.LENGTH_SHORT).show();
            sInstance = null;
            return;
        }
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            List<Row> loaded = collectRows();
            main.post(() -> {
                if (sInstance != this) {
                    return;
                }
                if (loaded == null) {
                    // Ne dumpsys ne kullanım erişimi: kullanıcıyı izne yönlendir.
                    try {
                        Toast.makeText(appContext, R.string.floating_qa_usage_access_required, Toast.LENGTH_LONG)
                                .show();
                        UsageAccessHelper.openUsageAccessSettings(appContext);
                    } catch (Exception ignored) {
                    }
                    sInstance = null;
                    return;
                }
                if (loaded.isEmpty()) {
                    Toast.makeText(appContext, R.string.floating_apps_none_running, Toast.LENGTH_LONG).show();
                    sInstance = null;
                    return;
                }
                rows.clear();
                rows.addAll(loaded);
                attach();
            });
        }, "RunningAppsLoad").start();
    }

    /** Arka planda çalışır. null = hiçbir kaynak okunamadı. */
    private List<Row> collectRows() {
        PackageManager pm = appContext.getPackageManager();
        List<Row> out = new ArrayList<>();
        List<RunningAppsHelper.RunningApp> running = RunningAppsHelper.listRunningUserApps(appContext);
        if (running != null) {
            for (RunningAppsHelper.RunningApp ra : running) {
                out.add(new Row(ra.packageName, labelOf(pm, ra.packageName), ra.state));
            }
            log("[INFO] Açık uygulamalar (dumpsys): " + out.size());
            return out;
        }
        if (!UsageAccessHelper.hasPackageUsageAccess(appContext)) {
            return null;
        }
        for (String pkg : ActivityDisplayTasksHelper.listRecentPackages(appContext)) {
            if (pkg != null && !pkg.isEmpty()) {
                out.add(new Row(pkg, labelOf(pm, pkg), null));
            }
        }
        log("[INFO] Açık uygulamalar (kullanım geçmişi yedeği): " + out.size());
        return out;
    }

    private static String labelOf(PackageManager pm, String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return String.valueOf(pm.getApplicationLabel(ai));
        } catch (PackageManager.NameNotFoundException ignored) {
            return pkg;
        }
    }

    private void attach() {
        String title = appContext.getString(R.string.floating_qa_open_apps);
        float density = appContext.getResources().getDisplayMetrics().density;
        Context c = themedContext;

        FrameLayout scrim = new FrameLayout(c);
        scrim.setBackgroundColor(UiStyles.color(c, R.color.modal_overlay_scrim));
        scrim.setOnClickListener(v -> detach());

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        UiStyles.applySolidRoundedBackgroundDp(card, UiStyles.color(c, R.color.surfaceCard), 24f);
        card.setElevation(12 * density);
        int cardPad = CarPickerUi.cardPadding(c);
        card.setPadding(cardPad, cardPad, cardPad, cardPad);

        LinearLayout titleRow = new LinearLayout(c);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(CarPickerUi.title(c, title),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView btnClose = CarPickerUi.closeButton(c, v -> detach());
        titleRow.addView(btnClose, btnClose.getLayoutParams());
        card.addView(titleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = CarPickerUi.subtitle(c, c.getString(R.string.floating_qa_tap_for_app_info));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = (int) (6 * density);
        card.addView(subtitle, subLp);

        GridView grid = CarPickerUi.grid(c);
        adapter = new RowAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            if (position >= 0 && position < rows.size()) {
                tryForceStopAt(position, rows.get(position));
            }
        });
        grid.setOnItemLongClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            if (position < 0 || position >= rows.size()) {
                return false;
            }
            openAppDetails(rows.get(position).packageName);
            return true;
        });
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        gridLp.topMargin = (int) (16 * density);
        card.addView(grid, gridLp);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                CarPickerUi.cardWidth(c), CarPickerUi.cardHeight(c));
        cardLp.gravity = Gravity.CENTER;
        scrim.addView(card, cardLp);

        rootView = scrim;
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams rootParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                PixelFormat.TRANSLUCENT);
        rootParams.gravity = Gravity.TOP | Gravity.START;

        try {
            windowManager.addView(rootView, rootParams);
            log("[INFO] Açık uygulamalar overlay (" + rows.size() + " adet)");
        } catch (Exception e) {
            log("[ERROR] Açık uygulamalar overlay: " + e.getMessage());
            Toast.makeText(appContext, e.getMessage(), Toast.LENGTH_SHORT).show();
            sInstance = null;
        }
    }

    private void tryForceStopAt(int position, @NonNull Row row) {
        if (!AppForceStopHelper.mayForceStop(appContext, row.packageName)) {
            Toast.makeText(appContext, R.string.floating_qa_force_stop_protected, Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        final String pkg = row.packageName;
        final String label = row.label;
        final DisplayRunningAppsOverlay self = this;
        new Thread(() -> {
            AppForceStopHelper.Result result = AppForceStopHelper.stopPackage(appContext, pkg);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (sInstance != self) {
                    return;
                }
                switch (result) {
                    case CLOSED:
                        removeRow(position, pkg);
                        Toast.makeText(appContext,
                                appContext.getString(R.string.floating_qa_force_stop_ok, label),
                                Toast.LENGTH_SHORT).show();
                        log("[INFO] Kapatıldı: " + pkg);
                        if (rows.isEmpty()) {
                            detach();
                        }
                        break;
                    case STILL_RUNNING:
                        Toast.makeText(appContext,
                                appContext.getString(R.string.floating_apps_kill_busy, label),
                                Toast.LENGTH_LONG).show();
                        log("[WARN] Kapatılamadı (ekranda/ön plan servisi): " + pkg);
                        break;
                    default:
                        Toast.makeText(appContext, R.string.floating_qa_force_stop_fail, Toast.LENGTH_LONG)
                                .show();
                        log("[WARN] Kapatma başarısız: " + pkg);
                        break;
                }
            });
        }, "RunningAppsKill").start();
    }

    private void removeRow(int position, String pkg) {
        if (position >= 0 && position < rows.size() && pkg.equals(rows.get(position).packageName)) {
            rows.remove(position);
        } else {
            for (int i = 0; i < rows.size(); i++) {
                if (pkg.equals(rows.get(i).packageName)) {
                    rows.remove(i);
                    break;
                }
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void openAppDetails(String packageName) {
        if (packageName == null) {
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + packageName));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(i);
            log("[INFO] Uygulama detayı: " + packageName);
        } catch (Exception e) {
            Toast.makeText(appContext, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void detach() {
        if (rootView != null && windowManager != null) {
            try {
                windowManager.removeView(rootView);
            } catch (Exception ignored) {
            }
        }
        rootView = null;
        if (sInstance == this) {
            sInstance = null;
        }
    }

    private String stateText(RunningAppsHelper.State state) {
        switch (state) {
            case SCREEN:
                return appContext.getString(R.string.floating_apps_state_screen);
            case SERVICE:
                return appContext.getString(R.string.floating_apps_state_service);
            default:
                return appContext.getString(R.string.floating_apps_state_cached);
        }
    }

    private class RowAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row r = rows.get(position);
            android.graphics.drawable.Drawable icon = null;
            try {
                icon = appContext.getPackageManager().getApplicationIcon(r.packageName);
            } catch (Exception ignored) {
            }
            return CarPickerUi.tile(themedContext, convertView, icon, r.label,
                    r.state == null ? null : stateText(r.state), false);
        }
    }
}
