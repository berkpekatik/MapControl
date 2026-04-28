package com.mapcontrol.manager;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.service.MapControlService;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.ProjectionTargetApps;
import com.mapcontrol.util.TargetPackageStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hedef uygulama overlay’i (arama yok) — VDBus: 7 veya 8 + action 1 yalnızca kapalıyken açar;
 * açıkken 7+action 4 sağ (sonraki), 8+action 4 sol (önceki). Kapatma tuşla değil, seçim/Temizle/✕ ile.
 * Dokunmatikteki yüzen "Değiştir" {@link TargetAppPickerOverlay} (arama ile).
 * {@link TargetAppPickerOverlay} ile bilinçli kopya; ortak refaktör yok.
 */
public final class ProjectionVDBusTargetPickerManager {

    /** Seçim sonrası dolum öncesi bekleme (~2 sn plana göre uzatıldı). */
    private static final int DWELL_START_MS = 4000;
    private static final int FILL_MS = 2000;
    private static final int PROGRESS_MAX = 1000;

    private static volatile ProjectionVDBusTargetPickerManager sInstance;

    private final Context appContext;
    private Context themedContext;
    private final WindowManager windowManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FloatingProjectionControlsManager.LogCallback logCallback;
    private View rootView;
    private WindowManager.LayoutParams rootParams;
    private ListView listView;
    private RowAdapter rowAdapter;
    private List<ProjectionTargetApps.Row> allRows = new ArrayList<>();
    private int selectedIndex;
    private boolean isOpen;
    private Runnable dwellRunnable;
    private ValueAnimator fillAnimator;
    private int fillProgress;
    private boolean inFillPhase;

    private android.content.ComponentCallbacks2 mThemeConfigCallback;
    private FrameLayout scrimView;
    private LinearLayout cardView;
    private TextView titleView;
    private TextView subtitleView;
    private TextView closeBtn;
    private TextView clearBtn;

    private ProjectionVDBusTargetPickerManager(Context anyContext) {
        this.appContext = anyContext.getApplicationContext();
        this.themedContext = new ContextThemeWrapper(this.appContext, R.style.Theme_MapControl);
        this.windowManager = (WindowManager) this.appContext.getSystemService(Context.WINDOW_SERVICE);
    }

    public static ProjectionVDBusTargetPickerManager getInstance(Context anyContext) {
        if (sInstance == null) {
            synchronized (ProjectionVDBusTargetPickerManager.class) {
                if (sInstance == null) {
                    sInstance = new ProjectionVDBusTargetPickerManager(anyContext);
                }
            }
        }
        return sInstance;
    }

    public boolean isOpen() {
        return isOpen;
    }

    private void log(String msg) {
        if (logCallback != null) {
            logCallback.log(msg);
        }
    }

    public static void show(Context anyContext, FloatingProjectionControlsManager.LogCallback logCallback) {
        TargetAppPickerOverlay.dismissIfShowing();
        ProjectionVDBusTargetPickerManager m = getInstance(anyContext);
        m.dismissIfShowing();
        m.logCallback = logCallback;
        m.attach();
    }

    /**
     * VDBus 7+1 / 8+1: yalnızca kapalıyken aç; açıkken aynı tuş tekrarını yut (kapatma yok).
     * Kapatma: seçim onayı, Temizle, ✕ veya {@link #dismissIfShowing()}.
     */
    public static void openIfClosed(Context anyContext, FloatingProjectionControlsManager.LogCallback logCallback) {
        if (getInstance(anyContext).isOpen) {
            return;
        }
        show(anyContext, logCallback);
    }

    public static void advanceSelectionIfOpen(Context anyContext) {
        getInstance(anyContext).advanceIfOpen();
    }

    public static void retreatSelectionIfOpen(Context anyContext) {
        getInstance(anyContext).retreatIfOpen();
    }

    public void advanceIfOpen() {
        if (!isOpen || allRows == null || allRows.isEmpty() || listView == null) {
            return;
        }
        cancelDwellAndFill();
        selectedIndex = (selectedIndex + 1) % allRows.size();
        notifySelectionScrollAndDwell();
    }

    public void retreatIfOpen() {
        if (!isOpen || allRows == null || allRows.isEmpty() || listView == null) {
            return;
        }
        cancelDwellAndFill();
        int n = allRows.size();
        selectedIndex = (selectedIndex - 1 + n) % n;
        notifySelectionScrollAndDwell();
    }

    private void notifySelectionScrollAndDwell() {
        if (rowAdapter != null) {
            rowAdapter.notifyDataSetChanged();
        }
        if (listView != null) {
            listView.post(() -> {
                if (listView != null) {
                    listView.smoothScrollToPosition(selectedIndex);
                }
            });
        }
        startDwellAndFill();
    }

    public static void dismissIfShowing() {
        if (sInstance != null && sInstance.isOpen) {
            sInstance.detach();
        }
    }

    private void cancelDwellAndFill() {
        if (dwellRunnable != null) {
            mainHandler.removeCallbacks(dwellRunnable);
            dwellRunnable = null;
        }
        if (fillAnimator != null) {
            try {
                fillAnimator.removeAllListeners();
                fillAnimator.removeAllUpdateListeners();
                fillAnimator.cancel();
            } catch (Exception ignored) {
            }
            fillAnimator = null;
        }
        inFillPhase = false;
        fillProgress = 0;
    }

    private void attach() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(appContext)) {
            Toast.makeText(appContext, "Overlay izni gerekli", Toast.LENGTH_SHORT).show();
            return;
        }
        allRows = ProjectionTargetApps.loadSortedRows(appContext);
        if (allRows.isEmpty()) {
            Toast.makeText(appContext, "Liste oluşturulamadı", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedIndex = 0;
        cancelDwellAndFill();
        themedContext = new ContextThemeWrapper(appContext, R.style.Theme_MapControl);

        float density = appContext.getResources().getDisplayMetrics().density;
        int screenW = appContext.getResources().getDisplayMetrics().widthPixels;
        int screenH = appContext.getResources().getDisplayMetrics().heightPixels;
        int cardMaxW = Math.min(screenW - (int) (32 * density), (int) (420 * density));
        int cardMaxH = (int) (screenH * 0.78f);

        scrimView = new FrameLayout(themedContext);
        scrimView.setBackgroundColor(ContextCompat.getColor(themedContext, R.color.modal_overlay_scrim));
        scrimView.setOnClickListener(v -> detach());

        cardView = new LinearLayout(themedContext);
        cardView.setOrientation(LinearLayout.VERTICAL);
        cardView.setClickable(true);
        UiStyles.applySolidRoundedBackgroundDp(cardView,
                ContextCompat.getColor(themedContext, R.color.surfaceCard), 18f);
        cardView.setElevation(12 * density);
        int cardPad = (int) (18 * density);
        cardView.setPadding(cardPad, cardPad, cardPad, cardPad);

        LinearLayout titleRow = new LinearLayout(themedContext);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        titleView = new TextView(themedContext);
        titleView.setText("Hedef uygulama");
        titleView.setTextSize(20);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(ContextCompat.getColor(themedContext, R.color.textPrimary));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleRow.addView(titleView, titleLp);

        closeBtn = new TextView(themedContext);
        closeBtn.setText("✕");
        closeBtn.setTextSize(22);
        closeBtn.setTextColor(ContextCompat.getColor(themedContext, R.color.textHint));
        closeBtn.setPadding((int) (8 * density), 0, 0, 0);
        closeBtn.setOnClickListener(v -> detach());
        titleRow.addView(closeBtn, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardView.addView(titleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        subtitleView = new TextView(themedContext);
        subtitleView.setText("Yansıtma için uygulama seçin");
        subtitleView.setTextSize(13);
        subtitleView.setTextColor(ContextCompat.getColor(themedContext, R.color.textHint));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = (int) (4 * density);
        cardView.addView(subtitleView, subLp);

        listView = new ListView(themedContext);
        listView.setDivider(new android.graphics.drawable.ColorDrawable(
                ContextCompat.getColor(themedContext, R.color.dividerWhite12)));
        listView.setDividerHeight(Math.max(1, (int) (density)));
        listView.setClipToPadding(false);
        rowAdapter = new RowAdapter();
        listView.setAdapter(rowAdapter);
        listView.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            cancelDwellAndFill();
            selectedIndex = position;
            rowAdapter.notifyDataSetChanged();
            listView.post(() -> listView.smoothScrollToPosition(selectedIndex));
            startDwellAndFill();
        });
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listLp.topMargin = (int) (14 * density);
        listLp.weight = 1;
        listLp.height = 0;
        cardView.addView(listView, listLp);

        LinearLayout footer = new LinearLayout(themedContext);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END);
        clearBtn = new TextView(themedContext);
        clearBtn.setText("Temizle");
        clearBtn.setTextSize(15);
        clearBtn.setTypeface(null, Typeface.BOLD);
        clearBtn.setTextColor(ContextCompat.getColor(themedContext, R.color.textLoading));
        int fp = (int) (12 * density);
        clearBtn.setPadding(fp, fp, fp, fp);
        clearBtn.setOnClickListener(v -> applyClearAndClose());
        footer.addView(clearBtn);
        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footLp.topMargin = (int) (10 * density);
        cardView.addView(footer, footLp);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(cardMaxW, cardMaxH);
        cardLp.gravity = Gravity.CENTER;
        scrimView.addView(cardView, cardLp);

        rootView = scrimView;
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        rootParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                PixelFormat.TRANSLUCENT);
        rootParams.gravity = Gravity.TOP | Gravity.START;
        rootParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;

        try {
            windowManager.addView(rootView, rootParams);
            isOpen = true;
            registerThemeConfigCallback();
            startDwellAndFill();
            log(String.format(Locale.US, "[INFO] VDBus hedef picker açıldı (%d uygulama)", allRows.size()));
        } catch (Exception e) {
            log("[ERROR] VDBus hedef picker eklenemedi: " + e.getMessage());
            Toast.makeText(appContext, e.getMessage(), Toast.LENGTH_SHORT).show();
            isOpen = false;
            listView = null;
            rootView = null;
            rowAdapter = null;
        }
    }

    private void startDwellAndFill() {
        cancelDwellAndFill();
        dwellRunnable = () -> {
            dwellRunnable = null;
            if (!isOpen || allRows == null || allRows.isEmpty()) {
                return;
            }
            inFillPhase = true;
            fillProgress = 0;
            if (rowAdapter != null) {
                rowAdapter.notifyDataSetChanged();
            }
            if (listView == null) {
                return;
            }
            listView.post(() -> {
                if (!isOpen) {
                    return;
                }
                fillAnimator = ValueAnimator.ofInt(0, PROGRESS_MAX);
                fillAnimator.setDuration(FILL_MS);
                fillAnimator.addUpdateListener(animator -> {
                    fillProgress = (int) animator.getAnimatedValue();
                    if (rowAdapter != null) {
                        rowAdapter.notifyDataSetChanged();
                    }
                });
                fillAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        completeSelectionAndOpenCluster();
                    }
                });
                try {
                    fillAnimator.start();
                } catch (Exception e) {
                    log("[ERROR] VDBus picker fill: " + e.getMessage());
                }
            });
        };
        mainHandler.postDelayed(dwellRunnable, DWELL_START_MS);
    }

    private void completeSelectionAndOpenCluster() {
        fillAnimator = null;
        if (!isOpen || allRows == null || selectedIndex < 0 || selectedIndex >= allRows.size()) {
            detach();
            return;
        }
        String pkg = allRows.get(selectedIndex).packageName;
        if (pkg == null || pkg.isEmpty()) {
            detach();
            return;
        }
        TargetPackageStore.writeAndBroadcast(appContext, pkg);
        log("VDBus hedef picker — seçim onaylandı: " + pkg);
        Toast.makeText(appContext, "Yansıtılıyor…", Toast.LENGTH_SHORT).show();
        startBenchOpenCluster();
        detach();
    }

    private void applyClearAndClose() {
        cancelDwellAndFill();
        TargetPackageStore.writeAndBroadcast(appContext, "");
        log("Hedef uygulama temizlendi (VDBus picker)");
        Toast.makeText(appContext, "Hedef temizlendi", Toast.LENGTH_SHORT).show();
        detach();
    }

    private void startBenchOpenCluster() {
        try {
            Intent i = new Intent(appContext, MapControlService.class)
                    .setAction(MapControlService.ACTION_BENCH_OPEN_CLUSTER);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(i);
            } else {
                appContext.startService(i);
            }
            log("[INFO] VDBus picker: " + MapControlService.ACTION_BENCH_OPEN_CLUSTER);
        } catch (Exception e) {
            log("[ERROR] VDBus picker servis: " + e.getMessage());
            Toast.makeText(appContext, "Servis başlatılamadı: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void registerThemeConfigCallback() {
        unregisterThemeConfigCallback();
        mThemeConfigCallback = new android.content.ComponentCallbacks2() {
            @Override
            public void onConfigurationChanged(@NonNull Configuration newConfig) {
                mainHandler.post(ProjectionVDBusTargetPickerManager.this::reapplyTheme);
            }

            @Override
            public void onLowMemory() {
            }

            @Override
            public void onTrimMemory(int level) {
            }
        };
        appContext.registerComponentCallbacks(mThemeConfigCallback);
    }

    private void unregisterThemeConfigCallback() {
        if (mThemeConfigCallback != null) {
            try {
                appContext.unregisterComponentCallbacks(mThemeConfigCallback);
            } catch (Exception ignored) {
            }
            mThemeConfigCallback = null;
        }
    }

    private void reapplyTheme() {
        if (!isOpen) {
            return;
        }
        themedContext = new ContextThemeWrapper(appContext, R.style.Theme_MapControl);
        int scrim = ContextCompat.getColor(appContext, R.color.modal_overlay_scrim);
        int card = ContextCompat.getColor(appContext, R.color.surfaceCard);
        int textPri = ContextCompat.getColor(appContext, R.color.textPrimary);
        int textHint = ContextCompat.getColor(appContext, R.color.textHint);
        int textLoad = ContextCompat.getColor(appContext, R.color.textLoading);
        int div = ContextCompat.getColor(appContext, R.color.dividerWhite12);
        if (scrimView != null) {
            scrimView.setBackgroundColor(scrim);
        }
        if (cardView != null) {
            UiStyles.applySolidRoundedBackgroundDp(cardView, card, 18f);
        }
        if (titleView != null) {
            titleView.setTextColor(textPri);
        }
        if (subtitleView != null) {
            subtitleView.setTextColor(textHint);
        }
        if (closeBtn != null) {
            closeBtn.setTextColor(textHint);
        }
        if (clearBtn != null) {
            clearBtn.setTextColor(textLoad);
        }
        if (listView != null) {
            listView.setDivider(new android.graphics.drawable.ColorDrawable(div));
        }
        if (rowAdapter != null) {
            rowAdapter.notifyDataSetChanged();
        }
    }

    private void detach() {
        cancelDwellAndFill();
        unregisterThemeConfigCallback();
        isOpen = false;
        inFillPhase = false;
        fillProgress = 0;
        if (rootView != null && windowManager != null) {
            try {
                windowManager.removeView(rootView);
            } catch (Exception ignored) {
            }
        }
        rootView = null;
        listView = null;
        rowAdapter = null;
        scrimView = null;
        cardView = null;
        titleView = null;
        subtitleView = null;
        closeBtn = null;
        clearBtn = null;
        allRows = new ArrayList<>();
    }

    private static final class RowItemViews {
        final LinearLayout rowInner;
        final ProgressBar bar;
        final ImageView icon;
        final TextView t1;
        final TextView t2;

        RowItemViews(LinearLayout rowInner, ProgressBar bar, ImageView icon, TextView t1, TextView t2) {
            this.rowInner = rowInner;
            this.bar = bar;
            this.icon = icon;
            this.t1 = t1;
            this.t2 = t2;
        }
    }

    private LinearLayout buildRowItemLayout() {
        float d = themedContext.getResources().getDisplayMetrics().density;
        int p = (int) (12 * d);
        LinearLayout outer = new LinearLayout(themedContext);
        outer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout rowInner = new LinearLayout(themedContext);
        rowInner.setOrientation(LinearLayout.HORIZONTAL);
        rowInner.setPadding(p, (int) (10 * d), p, (int) (10 * d));
        rowInner.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(themedContext);
        int iconPx = (int) (40 * d);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconPx, iconPx);
        iconLp.setMargins(0, 0, (int) (12 * d), 0);
        rowInner.addView(icon, iconLp);
        LinearLayout texts = new LinearLayout(themedContext);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = new TextView(themedContext);
        t1.setTextSize(16);
        t1.setTextColor(ContextCompat.getColor(themedContext, R.color.textPrimary));
        TextView t2 = new TextView(themedContext);
        t2.setTextSize(12);
        t2.setTextColor(ContextCompat.getColor(themedContext, R.color.textHint));
        texts.addView(t1);
        texts.addView(t2);
        rowInner.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        outer.addView(rowInner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ProgressBar bar = new ProgressBar(themedContext, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(PROGRESS_MAX);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(2, (int) (3 * d)));
        barLp.topMargin = (int) (2 * d);
        bar.setLayoutParams(barLp);
        outer.addView(bar, barLp);
        outer.setTag(new RowItemViews(rowInner, bar, icon, t1, t2));
        return outer;
    }

    private class RowAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return allRows == null ? 0 : allRows.size();
        }

        @Override
        public Object getItem(int position) {
            return allRows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            float d = themedContext.getResources().getDisplayMetrics().density;
            int strokePx = Math.max(1, (int) (2 * d));

            LinearLayout outer;
            if (convertView instanceof LinearLayout && convertView.getTag() instanceof RowItemViews) {
                outer = (LinearLayout) convertView;
            } else {
                outer = buildRowItemLayout();
            }
            RowItemViews v = (RowItemViews) outer.getTag();

            boolean sel = (position == selectedIndex);
            ProjectionTargetApps.Row r = allRows.get(position);
            v.t1.setText(r.label);
            v.t2.setText(r.packageName);
            try {
                v.icon.setImageDrawable(appContext.getPackageManager().getApplicationIcon(r.packageName));
            } catch (Exception e) {
                v.icon.setImageResource(android.R.drawable.sym_def_app_icon);
            }

            int tPri = ContextCompat.getColor(appContext, R.color.textPrimary);
            int tHi = ContextCompat.getColor(appContext, R.color.textHint);
            v.t1.setTextColor(tPri);
            v.t2.setTextColor(tHi);

            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            if (sel) {
                bg.setColor(ContextCompat.getColor(appContext, R.color.surfaceCardInner));
                bg.setCornerRadius(12f * d);
                bg.setStroke(strokePx, tPri);
            } else {
                bg.setColor(0x00000000);
                bg.setCornerRadius(12f * d);
            }
            v.rowInner.setBackground(bg);

            boolean showBar = sel && inFillPhase;
            v.bar.setVisibility(showBar ? View.VISIBLE : View.GONE);
            if (showBar) {
                v.bar.setProgress(fillProgress);
            } else {
                v.bar.setProgress(0);
            }
            return outer;
        }
    }
}
