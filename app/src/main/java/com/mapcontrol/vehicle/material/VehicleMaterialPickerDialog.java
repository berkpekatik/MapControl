package com.mapcontrol.vehicle.material;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

import java.util.List;

/** Yüklü OEM araç görsellerinden seçim diyaloğu. */
public final class VehicleMaterialPickerDialog {

    private static final float DIALOG_WIDTH_FRACTION = 0.88f;
    private static final int PREFERRED_COLUMNS = 4;

    public interface OnSelectedListener {
        void onManualSelected(MaterialVehicleCatalog.Entry entry);

        void onAutoDetectionSelected();
    }

    private static final class GridMetrics {
        final int columns;
        final int tileSize;
        final int pad;
        final int gap;
        final int dialogWidthPx;

        GridMetrics(int columns, int tileSize, int pad, int gap, int dialogWidthPx) {
            this.columns = columns;
            this.tileSize = tileSize;
            this.pad = pad;
            this.gap = gap;
            this.dialogWidthPx = dialogWidthPx;
        }
    }

    private VehicleMaterialPickerDialog() {
    }

    public static void show(Activity activity, OnSelectedListener listener) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        new Thread(() -> {
            List<MaterialVehicleCatalog.Entry> entries = MaterialVehicleCatalog.loadInstalled(activity);
            activity.runOnUiThread(() -> showOnUiThread(activity, entries, listener));
        }).start();
    }

    private static void showOnUiThread(
            Activity activity,
            List<MaterialVehicleCatalog.Entry> entries,
            OnSelectedListener listener) {
        if (activity.isFinishing()) {
            return;
        }

        GridMetrics metrics = computeGridMetrics(activity);

        ScrollView scroll = new ScrollView(activity);
        scroll.setPadding(metrics.pad, metrics.pad, metrics.pad, metrics.pad);
        scroll.setFillViewport(false);

        int gridWidth = metrics.columns * (metrics.tileSize + metrics.gap * 2);
        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(metrics.columns);
        grid.setUseDefaultMargins(false);
        scroll.addView(grid, new ScrollView.LayoutParams(gridWidth, ScrollView.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Araç görseli seçin")
                .setView(scroll)
                .setNegativeButton("İptal", null)
                .create();

        grid.addView(createAutoTile(activity, metrics, dialog, listener));

        for (MaterialVehicleCatalog.Entry entry : entries) {
            grid.addView(createPackageTile(activity, metrics, entry, dialog, listener));
        }

        if (entries.isEmpty()) {
            Toast.makeText(activity, "Yüklü araç paketi bulunamadı; yalnızca otomatik algılama kullanılabilir.",
                    Toast.LENGTH_LONG).show();
        }

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(metrics.dialogWidthPx, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private static GridMetrics computeGridMetrics(Activity activity) {
        int pad = UiStyles.dimenPx(activity, R.dimen.spacing_medium);
        int gap = UiStyles.dimenPx(activity, R.dimen.spacing_small);
        int minTile = UiStyles.dimenPx(activity, R.dimen.vehicle_picker_tile_min);
        int maxTile = UiStyles.dimenPx(activity, R.dimen.vehicle_picker_tile_max);

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int dialogWidthPx = Math.round(dm.widthPixels * DIALOG_WIDTH_FRACTION);
        int contentWidth = dialogWidthPx - pad * 2;

        int columns = PREFERRED_COLUMNS;
        int tileSize = (contentWidth - columns * gap * 2) / columns;
        while (columns > 2 && tileSize < minTile) {
            columns--;
            tileSize = (contentWidth - columns * gap * 2) / columns;
        }
        tileSize = Math.min(maxTile, Math.max(minTile, tileSize));

        int actualGridWidth = columns * (tileSize + gap * 2);
        int fittedDialogWidth = actualGridWidth + pad * 2;
        return new GridMetrics(columns, tileSize, pad, gap, fittedDialogWidth);
    }

    private static View createAutoTile(
            Activity activity,
            GridMetrics metrics,
            AlertDialog dialog,
            OnSelectedListener listener) {
        LinearLayout tile = createSquareTileShell(activity, metrics);
        int iconSize = Math.round(metrics.tileSize * 0.5f);

        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_mdi_car);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setColorFilter(UiStyles.color(activity, R.color.accentHighlight));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.weight = 1f;
        iconLp.gravity = Gravity.CENTER;
        tile.addView(icon, iconLp);

        TextView label = createTileLabel(activity, "Otomatik algılama");
        tile.addView(label, wrapContentLp());

        TextView hint = new TextView(activity);
        hint.setText("EOL");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        hint.setTextColor(UiStyles.color(activity, R.color.textHint));
        hint.setGravity(Gravity.CENTER);
        tile.addView(hint, wrapContentLp());

        tile.setOnClickListener(v -> {
            MaterialVehiclePreferences.enableAutoDetection(activity);
            MaterialVehicleResources.getInstance().initWithAutoDetection(activity);
            if (listener != null) {
                listener.onAutoDetectionSelected();
            }
            dialog.dismiss();
            Toast.makeText(activity, "Otomatik algılama etkin", Toast.LENGTH_SHORT).show();
        });
        return tile;
    }

    private static View createPackageTile(
            Activity activity,
            GridMetrics metrics,
            MaterialVehicleCatalog.Entry entry,
            AlertDialog dialog,
            OnSelectedListener listener) {
        LinearLayout tile = createSquareTileShell(activity, metrics);

        ImageView preview = new ImageView(activity);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setAdjustViewBounds(true);
        if (entry.preview != null) {
            preview.setImageBitmap(entry.preview);
        }
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        previewLp.gravity = Gravity.CENTER;
        tile.addView(preview, previewLp);

        TextView label = createTileLabel(activity, entry.label);
        tile.addView(label, wrapContentLp());

        tile.setOnClickListener(v -> {
            MaterialVehiclePreferences.saveSelection(
                    activity, entry.packageName, entry.drawableName, entry.label);
            if (listener != null) {
                listener.onManualSelected(entry);
            }
            dialog.dismiss();
            Toast.makeText(activity, "Seçildi: " + entry.label, Toast.LENGTH_SHORT).show();
        });
        return tile;
    }

    private static LinearLayout createSquareTileShell(Activity activity, GridMetrics metrics) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        int inner = UiStyles.dimenPx(activity, R.dimen.spacing_tiny);
        tile.setPadding(inner, inner, inner, inner);
        UiStyles.setGlassCardBackground(tile);
        tile.setClickable(true);
        tile.setFocusable(true);

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = metrics.tileSize;
        lp.height = metrics.tileSize;
        lp.setMargins(metrics.gap, metrics.gap, metrics.gap, metrics.gap);
        tile.setLayoutParams(lp);
        return tile;
    }

    private static LinearLayout.LayoutParams wrapContentLp() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static TextView createTileLabel(Activity activity, String text) {
        TextView label = new TextView(activity);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(UiStyles.color(activity, R.color.textPrimary));
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, UiStyles.dimenPx(activity, R.dimen.spacing_tiny), 0, 0);
        label.setMaxLines(2);
        return label;
    }
}
