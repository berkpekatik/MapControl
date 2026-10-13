package com.mapcontrol.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * İzin testini kaydırılabilir bir pencerede gösterir.
 */
public final class PermissionProbeDialog {

    private static final int COLOR_OK = 0xFF66BB6A;
    private static final int COLOR_FAIL = 0xFFEF5350;
    private static final int COLOR_SKIP = 0xFFFFB74D;

    private PermissionProbeDialog() {
    }

    public static void show(Context context) {
        if (!(context instanceof Activity)) {
            return;
        }
        Activity activity = (Activity) context;
        if (activity.isFinishing()) {
            return;
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 28, 36, 12);
        root.setBackgroundColor(UiStyles.color(activity, R.color.backgroundPage));

        TextView title = new TextView(activity);
        title.setText(R.string.settings_permissions_dialog_title);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(UiStyles.color(activity, R.color.textPrimary));
        root.addView(title);

        TextView legend = new TextView(activity);
        legend.setText(R.string.settings_permissions_legend);
        legend.setTextSize(13);
        legend.setTextColor(UiStyles.color(activity, R.color.textHint));
        legend.setPadding(0, 12, 0, 8);
        root.addView(legend);

        TextView summary = new TextView(activity);
        summary.setText(R.string.settings_permissions_running);
        summary.setTextSize(15);
        summary.setTypeface(null, Typeface.BOLD);
        summary.setTextColor(UiStyles.color(activity, R.color.textPrimary));
        summary.setPadding(0, 4, 0, 12);
        root.addView(summary);

        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(rows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(root)
                .setNegativeButton(R.string.settings_permissions_close, null)
                .setCancelable(true)
                .create();
        final boolean[] open = {true};
        dialog.setOnDismissListener(d -> open[0] = false);
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(UiStyles.color(activity, R.color.backgroundPage)));
                int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f);
                int height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.86f);
                window.setLayout(width, height);
            }
            if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                        .setTextColor(UiStyles.color(activity, R.color.textDialogButtonSecondary));
            }
        });
        dialog.show();

        Handler main = new Handler(Looper.getMainLooper());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<PermissionProbe.Result> results;
            try {
                results = PermissionProbe.run(activity.getApplicationContext());
            } catch (Exception e) {
                results = java.util.Collections.singletonList(new PermissionProbe.Result(
                        "PROBE",
                        PermissionProbe.Result.Status.FAIL,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
            List<PermissionProbe.Result> ready = results;
            main.post(() -> {
                executor.shutdown();
                if (!open[0] || activity.isFinishing()) {
                    return;
                }
                bind(activity, rows, summary, ready);
            });
        });
    }

    private static void bind(Context context, LinearLayout rows, TextView summary, List<PermissionProbe.Result> results) {
        rows.removeAllViews();
        int ok = 0;
        int fail = 0;
        int skip = 0;
        for (PermissionProbe.Result result : results) {
            if (result.status == PermissionProbe.Result.Status.OK) {
                ok++;
            } else if (result.status == PermissionProbe.Result.Status.FAIL) {
                fail++;
            } else {
                skip++;
            }
            rows.addView(row(context, result));
        }
        summary.setText(context.getString(R.string.settings_permissions_summary, ok, fail, skip));
    }

    private static LinearLayout row(Context context, PermissionProbe.Result result) {
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.VERTICAL);
        line.setPadding(0, 10, 0, 10);

        TextView name = new TextView(context);
        int color = result.status == PermissionProbe.Result.Status.OK
                ? COLOR_OK
                : result.status == PermissionProbe.Result.Status.FAIL ? COLOR_FAIL : COLOR_SKIP;
        String mark = result.status == PermissionProbe.Result.Status.OK
                ? "OK"
                : result.status == PermissionProbe.Result.Status.FAIL ? "FAIL" : "SKIP";
        name.setText(mark + "  " + result.name);
        name.setTextSize(15);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextColor(color);
        line.addView(name);

        TextView detail = new TextView(context);
        detail.setText(result.detail);
        detail.setTextSize(13);
        detail.setTextColor(UiStyles.color(context, R.color.textHint));
        detail.setPadding(0, 4, 0, 0);
        line.addView(detail, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.START;
        line.setLayoutParams(params);
        return line;
    }
}
