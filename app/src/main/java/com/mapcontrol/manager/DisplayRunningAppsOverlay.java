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
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.ActivityDisplayTasksHelper;
import com.mapcontrol.util.AppForceStopHelper;
import com.mapcontrol.util.UsageAccessHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Belirli ekran için uygulama adaylarını listeler. Kısa tık: zorla durdur; uzun bas: uygulama bilgisi.
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

        Row(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    private DisplayRunningAppsOverlay(Context appContext, LogCallback logCallback) {
        this.appContext = appContext.getApplicationContext();
        this.themedContext = new ContextThemeWrapper(this.appContext, R.style.Theme_MapControl);
        this.windowManager = (WindowManager) this.appContext.getSystemService(Context.WINDOW_SERVICE);
        this.logCallback = logCallback;
    }

    private void log(String msg) {
        if (logCallback != null) {
            logCallback.log(msg);
        }
    }

    /**
     * @param whichDisplay 0 = ana ekran; aksi halde {@link AppLaunchHelper#getClusterDisplayId(Context)} ile
     *                     aynı ikinci ekran fikrini somut id için kullan (ör. cluster).
     */
    public static void show(Context anyContext, int whichDisplay, String title, LogCallback logCallback) {
        dismissIfShowing();
        DisplayRunningAppsOverlay o = new DisplayRunningAppsOverlay(anyContext, logCallback);
        sInstance = o;
        o.attach(whichDisplay, title);
    }

    public static void dismissIfShowing() {
        if (sInstance != null) {
            sInstance.detach();
            sInstance = null;
        }
    }

    private void attach(int whichDisplay, String title) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(appContext)) {
            Toast.makeText(appContext, "Overlay izni gerekli", Toast.LENGTH_SHORT).show();
            sInstance = null;
            return;
        }
        int displayId = whichDisplay;
        if (displayId < 0) {
            displayId = android.view.Display.DEFAULT_DISPLAY;
        }
        if (!UsageAccessHelper.hasPackageUsageAccess(appContext)) {
            try {
                Toast.makeText(
                        appContext, R.string.floating_qa_usage_access_required, Toast.LENGTH_LONG)
                        .show();
                UsageAccessHelper.openUsageAccessSettings(appContext);
            } catch (Exception ignored) {
            }
            sInstance = null;
            return;
        }
        List<String> pkgs = ActivityDisplayTasksHelper.listPackagesOnDisplay(appContext, displayId);
        if (pkgs == null) {
            pkgs = new ArrayList<>();
        }
        rows.clear();
        PackageManager pm = appContext.getPackageManager();
        for (String pkg : pkgs) {
            if (pkg == null || pkg.isEmpty()) {
                continue;
            }
            String label = pkg;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                label = String.valueOf(pm.getApplicationLabel(ai));
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            rows.add(new Row(pkg, label));
        }
        if (rows.isEmpty()) {
            Toast.makeText(appContext, R.string.floating_qa_no_recent_apps, Toast.LENGTH_LONG).show();
            sInstance = null;
            return;
        }

        float density = appContext.getResources().getDisplayMetrics().density;
        int screenW = appContext.getResources().getDisplayMetrics().widthPixels;
        int screenH = appContext.getResources().getDisplayMetrics().heightPixels;
        int cardMaxW = Math.min(screenW - (int) (32 * density), (int) (420 * density));
        int cardMaxH = (int) (screenH * 0.78f);

        FrameLayout scrim = new FrameLayout(themedContext);
        scrim.setBackgroundColor(ContextCompat.getColor(themedContext, R.color.modal_overlay_scrim));
        scrim.setOnClickListener(v -> detach());

        LinearLayout card = new LinearLayout(themedContext);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        UiStyles.applySolidRoundedBackgroundDp(card,
                ContextCompat.getColor(themedContext, R.color.surfaceCard), 18f);
        card.setElevation(12 * density);
        int cardPad = (int) (18 * density);
        card.setPadding(cardPad, cardPad, cardPad, cardPad);

        LinearLayout titleRow = new LinearLayout(themedContext);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(themedContext);
        titleView.setText(title != null ? title : appContext.getString(R.string.floating_qa_open_apps));
        titleView.setTextSize(20);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(ContextCompat.getColor(themedContext, R.color.textPrimary));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleRow.addView(titleView, titleLp);

        TextView btnClose = new TextView(themedContext);
        btnClose.setText("✕");
        btnClose.setTextSize(22);
        btnClose.setTextColor(ContextCompat.getColor(themedContext, R.color.textHint));
        btnClose.setPadding((int) (8 * density), 0, 0, 0);
        btnClose.setOnClickListener(v -> detach());
        titleRow.addView(btnClose, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        card.addView(titleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(themedContext);
        subtitle.setText(R.string.floating_qa_tap_for_app_info);
        subtitle.setTextSize(13);
        subtitle.setTextColor(ContextCompat.getColor(themedContext, R.color.textHint));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = (int) (4 * density);
        card.addView(subtitle, subLp);

        ListView listView = new ListView(themedContext);
        listView.setDivider(new android.graphics.drawable.ColorDrawable(
                ContextCompat.getColor(themedContext, R.color.dividerWhite12)));
        listView.setDividerHeight(Math.max(1, (int) (density)));
        listView.setClipToPadding(false);
        adapter = new RowAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            if (position < 0 || position >= rows.size()) {
                return;
            }
            tryForceStopAt(position, rows.get(position));
        });
        listView.setOnItemLongClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            if (position < 0 || position >= rows.size()) {
                return false;
            }
            openAppDetails(rows.get(position).packageName);
            return true;
        });
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listLp.topMargin = (int) (12 * density);
        listLp.weight = 1;
        listLp.height = 0;
        card.addView(listView, listLp);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(cardMaxW, cardMaxH);
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
            log("[INFO] Açık uygulamalar overlay: displayId=" + displayId + " (" + rows.size() + " adet)");
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
        final DisplayRunningAppsOverlay self = this;
        new Thread(() -> {
            boolean ok = AppForceStopHelper.amForceStop(pkg);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (sInstance != self) {
                    return;
                }
                if (ok) {
                    if (position >= 0 && position < rows.size()
                            && pkg.equals(rows.get(position).packageName)) {
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
                    String msg = appContext.getString(R.string.floating_qa_force_stop_ok, pkg);
                    Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show();
                    log("[INFO] force-stop: " + pkg);
                    if (rows.isEmpty()) {
                        detach();
                    }
                } else {
                    Toast.makeText(
                                    appContext,
                                    R.string.floating_qa_force_stop_fail,
                                    Toast.LENGTH_LONG)
                            .show();
                    log("[WARN] force-stop başarısız: " + pkg);
                }
            });
        }).start();
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
            LinearLayout row;
            ImageView icon;
            TextView t1;
            TextView t2;
            if (convertView instanceof LinearLayout && ((LinearLayout) convertView).getChildCount() >= 2) {
                row = (LinearLayout) convertView;
                icon = (ImageView) row.getChildAt(0);
                LinearLayout texts = (LinearLayout) row.getChildAt(1);
                t1 = (TextView) texts.getChildAt(0);
                t2 = (TextView) texts.getChildAt(1);
            } else {
                float d = themedContext.getResources().getDisplayMetrics().density;
                int p = (int) (12 * d);
                row = new LinearLayout(themedContext);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(p, (int) (10 * d), p, (int) (10 * d));
                row.setGravity(Gravity.CENTER_VERTICAL);
                icon = new ImageView(themedContext);
                int iconPx = (int) (40 * d);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconPx, iconPx);
                iconLp.setMargins(0, 0, (int) (12 * d), 0);
                row.addView(icon, iconLp);
                LinearLayout texts = new LinearLayout(themedContext);
                texts.setOrientation(LinearLayout.VERTICAL);
                t1 = new TextView(themedContext);
                t1.setTextSize(16);
                t1.setTextColor(ContextCompat.getColor(themedContext, R.color.textPrimary));
                t2 = new TextView(themedContext);
                t2.setTextSize(12);
                t2.setTextColor(ContextCompat.getColor(themedContext, R.color.textHint));
                texts.addView(t1);
                texts.addView(t2);
                row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            Row r = rows.get(position);
            t1.setText(r.label);
            t2.setText(r.packageName);
            try {
                icon.setImageDrawable(appContext.getPackageManager().getApplicationIcon(r.packageName));
            } catch (Exception e) {
                icon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
            return row;
        }
    }
}
