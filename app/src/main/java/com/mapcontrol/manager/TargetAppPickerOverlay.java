package com.mapcontrol.manager;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.ProjectionTargetApps;
import com.mapcontrol.util.TargetPackageStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Tam ekran overlay ile hedef uygulama seçimi; MainActivity açılmaz.
 */
public final class TargetAppPickerOverlay {

    private static TargetAppPickerOverlay sInstance;

    private final Context appContext;
    private final Context themedContext;
    private final WindowManager windowManager;
    private final FloatingProjectionControlsManager.LogCallback logCallback;
    private View rootView;
    private WindowManager.LayoutParams rootParams;
    private List<ProjectionTargetApps.Row> allRows = new ArrayList<>();
    private final List<ProjectionTargetApps.Row> filteredRows = new ArrayList<>();
    private RowAdapter adapter;
    private EditText searchInput;

    private TargetAppPickerOverlay(Context appContext,
            FloatingProjectionControlsManager.LogCallback logCallback) {
        this.appContext = appContext.getApplicationContext();
        this.themedContext = new ContextThemeWrapper(this.appContext,
                R.style.Theme_MapControl);
        this.windowManager = (WindowManager) this.appContext.getSystemService(Context.WINDOW_SERVICE);
        this.logCallback = logCallback;
    }

    private void log(String msg) {
        if (logCallback != null) {
            logCallback.log(msg);
        }
    }

    public static void show(Context anyContext, FloatingProjectionControlsManager.LogCallback logCallback) {
        ProjectionVDBusTargetPickerManager.dismissIfShowing();
        dismissIfShowing();
        TargetAppPickerOverlay o = new TargetAppPickerOverlay(anyContext, logCallback);
        sInstance = o;
        o.attach();
    }

    public static void dismissIfShowing() {
        if (sInstance != null) {
            sInstance.detach();
            sInstance = null;
        }
    }

    private void attach() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(appContext)) {
            Toast.makeText(appContext, "Overlay izni gerekli", Toast.LENGTH_SHORT).show();
            sInstance = null;
            return;
        }
        allRows = ProjectionTargetApps.loadSortedRows(appContext);
        if (allRows.isEmpty()) {
            Toast.makeText(appContext, "Liste oluşturulamadı", Toast.LENGTH_SHORT).show();
            sInstance = null;
            return;
        }
        filteredRows.clear();
        filteredRows.addAll(allRows);

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

        TextView title = new TextView(themedContext);
        title.setText("Hedef uygulama");
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(themedContext, R.color.textPrimary));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleRow.addView(title, titleLp);

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
        subtitle.setText("Yansıtma için uygulama seçin");
        subtitle.setTextSize(13);
        subtitle.setTextColor(ContextCompat.getColor(themedContext, R.color.textHint));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = (int) (4 * density);
        card.addView(subtitle, subLp);

        searchInput = new EditText(themedContext);
        searchInput.setHint("Ara…");
        searchInput.setSingleLine(true);
        searchInput.setTextSize(15);
        searchInput.setTextColor(ContextCompat.getColor(themedContext, R.color.textPrimary));
        searchInput.setHintTextColor(ContextCompat.getColor(themedContext, R.color.textHint));
        UiStyles.applySolidRoundedBackgroundDp(searchInput,
                ContextCompat.getColor(themedContext, R.color.surfaceCardInner), 12f);
        int sp = (int) (12 * density);
        searchInput.setPadding(sp, sp, sp, sp);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.topMargin = (int) (14 * density);
        card.addView(searchInput, searchLp);

        ListView listView = new ListView(themedContext);
        listView.setDivider(new android.graphics.drawable.ColorDrawable(
                ContextCompat.getColor(themedContext, R.color.dividerWhite12)));
        listView.setDividerHeight(Math.max(1, (int) (density)));
        listView.setClipToPadding(false);
        adapter = new RowAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            ProjectionTargetApps.Row row = filteredRows.get(position);
            applySelection(row.packageName);
        });
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listLp.topMargin = (int) (12 * density);
        listLp.weight = 1;
        listLp.height = 0;
        card.addView(listView, listLp);

        LinearLayout footer = new LinearLayout(themedContext);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END);
        TextView btnClear = new TextView(themedContext);
        btnClear.setText("Temizle");
        btnClear.setTextSize(15);
        btnClear.setTypeface(null, Typeface.BOLD);
        btnClear.setTextColor(ContextCompat.getColor(themedContext, R.color.textLoading));
        int fp = (int) (12 * density);
        btnClear.setPadding(fp, fp, fp, fp);
        btnClear.setOnClickListener(v -> applySelection(""));
        footer.addView(btnClear);
        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footLp.topMargin = (int) (10 * density);
        card.addView(footer, footLp);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilter(s != null ? s.toString() : "");
            }
        });

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(cardMaxW, cardMaxH);
        cardLp.gravity = Gravity.CENTER;
        scrim.addView(card, cardLp);

        rootView = scrim;
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        rootParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                PixelFormat.TRANSLUCENT);
        rootParams.gravity = Gravity.TOP | Gravity.START;
        rootParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        try {
            windowManager.addView(rootView, rootParams);
            log("[INFO] Hedef uygulama overlay açıldı (" + allRows.size() + " uygulama)");
        } catch (Exception e) {
            log("[ERROR] Overlay eklenemedi: " + e.getMessage());
            Toast.makeText(appContext, e.getMessage(), Toast.LENGTH_SHORT).show();
            sInstance = null;
        }
    }

    private void applyFilter(String query) {
        String q = query != null ? query.trim().toLowerCase() : "";
        filteredRows.clear();
        if (q.isEmpty()) {
            filteredRows.addAll(allRows);
        } else {
            for (ProjectionTargetApps.Row r : allRows) {
                if (r.label.toLowerCase().contains(q) || r.packageName.toLowerCase().contains(q)) {
                    filteredRows.add(r);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void applySelection(String packageName) {
        hideKeyboard();
        TargetPackageStore.writeAndBroadcast(appContext, packageName);
        if (packageName == null || packageName.isEmpty()) {
            log("Hedef uygulama temizlendi (overlay)");
            Toast.makeText(appContext, "Hedef temizlendi", Toast.LENGTH_SHORT).show();
        } else {
            log("Seçilen uygulama (overlay): " + packageName);
            Toast.makeText(appContext, "Seçildi: " + packageName, Toast.LENGTH_SHORT).show();
        }
        detach();
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) appContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && searchInput != null && searchInput.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }
    }

    private void detach() {
        hideKeyboard();
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
            return filteredRows.size();
        }

        @Override
        public Object getItem(int position) {
            return filteredRows.get(position);
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
            ProjectionTargetApps.Row r = filteredRows.get(position);
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
