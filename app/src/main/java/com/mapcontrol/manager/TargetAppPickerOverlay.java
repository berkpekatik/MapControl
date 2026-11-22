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
import android.widget.GridView;
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
    /** Şu an seçili hedef paket (kutucukta vurgulanır). */
    private String currentTarget;

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
            Toast.makeText(appContext, R.string.target_picker_overlay_required, Toast.LENGTH_SHORT).show();
            sInstance = null;
            return;
        }
        allRows = ProjectionTargetApps.loadSortedRows(appContext);
        if (allRows.isEmpty()) {
            Toast.makeText(appContext, R.string.target_picker_list_failed, Toast.LENGTH_SHORT).show();
            sInstance = null;
            return;
        }
        filteredRows.clear();
        filteredRows.addAll(allRows);

        float density = appContext.getResources().getDisplayMetrics().density;
        currentTarget = TargetPackageStore.read(appContext);
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

        // Başlık satırı: başlık | arama | Temizle | ✕
        LinearLayout titleRow = new LinearLayout(c);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(CarPickerUi.title(c, c.getString(R.string.target_picker_title)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        searchInput = new EditText(c);
        searchInput.setHint(R.string.target_picker_search_hint);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(22);
        searchInput.setTextColor(UiStyles.color(c, R.color.textPrimary));
        searchInput.setHintTextColor(UiStyles.color(c, R.color.textHint));
        UiStyles.applySolidRoundedBackgroundDp(searchInput, UiStyles.color(c, R.color.surfaceCardInner), 16f);
        int sp = (int) (16 * density);
        searchInput.setPadding(sp, (int) (12 * density), sp, (int) (12 * density));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                (int) (380 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.setMargins((int) (16 * density), 0, (int) (12 * density), 0);
        titleRow.addView(searchInput, searchLp);

        TextView btnClear = new TextView(c);
        btnClear.setText(R.string.common_clear);
        btnClear.setTextSize(22);
        UiStyles.setFontStyle(btnClear, Typeface.BOLD);
        btnClear.setTextColor(UiStyles.color(c, R.color.textLoading));
        btnClear.setGravity(Gravity.CENTER);
        int fp = (int) (20 * density);
        btnClear.setPadding(fp, (int) (14 * density), fp, (int) (14 * density));
        btnClear.setBackground(CarPickerUi.ripple(c, UiStyles.color(c, R.color.surfaceCardInner), false, 0f));
        btnClear.setOnClickListener(v -> applySelection(""));
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clearLp.setMargins(0, 0, (int) (12 * density), 0);
        titleRow.addView(btnClear, clearLp);

        ImageView btnClose = CarPickerUi.closeButton(c, v -> detach());
        titleRow.addView(btnClose, btnClose.getLayoutParams());
        card.addView(titleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = CarPickerUi.subtitle(c, c.getString(R.string.target_picker_subtitle));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = (int) (6 * density);
        card.addView(subtitle, subLp);

        GridView grid = CarPickerUi.grid(c);
        adapter = new RowAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            if (position >= 0 && position < filteredRows.size()) {
                applySelection(filteredRows.get(position).packageName);
            }
        });
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        gridLp.topMargin = (int) (16 * density);
        card.addView(grid, gridLp);

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

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                CarPickerUi.cardWidth(c), CarPickerUi.cardHeight(c));
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
            Toast.makeText(appContext, R.string.target_picker_cleared, Toast.LENGTH_SHORT).show();
        } else {
            log("Seçilen uygulama (overlay): " + packageName);
            Toast.makeText(appContext, appContext.getString(R.string.projection_selected, packageName), Toast.LENGTH_SHORT).show();
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
            ProjectionTargetApps.Row r = filteredRows.get(position);
            android.graphics.drawable.Drawable icon = null;
            try {
                icon = appContext.getPackageManager().getApplicationIcon(r.packageName);
            } catch (Exception ignored) {
            }
            boolean selected = r.packageName.equals(currentTarget);
            return CarPickerUi.tile(themedContext, convertView, icon, r.label,
                    selected ? themedContext.getString(R.string.target_picker_current) : null, selected);
        }
    }
}
