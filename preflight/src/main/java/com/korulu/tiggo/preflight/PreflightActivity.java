package com.korulu.tiggo.preflight;

import android.app.Activity;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Read-only first-run diagnostic. Intentionally independent of MapControl's Application. */
public final class PreflightActivity extends Activity {
    private TextView information;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        column.setPadding(padding, padding, padding, padding);
        TextView description = new TextView(this);
        description.setText(R.string.description);
        description.setTextSize(18);
        column.addView(description);
        Button refresh = new Button(this);
        refresh.setText(R.string.refresh);
        refresh.setOnClickListener(view -> refreshInformation());
        column.addView(refresh);
        information = new TextView(this);
        information.setTextSize(18);
        information.setTextIsSelectable(true);
        column.addView(information);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(column);
        setContentView(scroll);
        refreshInformation();
    }

    private void refreshInformation() {
        StringBuilder text = new StringBuilder(getString(R.string.device_info,
                Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Build.MODEL,
                String.join(", ", Build.SUPPORTED_ABIS), getFilesDir().getUsableSpace() / (1024 * 1024)));
        try {
            DisplayManager manager = getSystemService(DisplayManager.class);
            if (manager == null) throw new IllegalStateException("DisplayManager unavailable");
            boolean secondary = false;
            for (Display display : manager.getDisplays()) {
                Display.Mode mode = display.getMode();
                text.append(getString(R.string.display_info, display.getDisplayId(), display.getName(),
                        mode.getPhysicalWidth(), mode.getPhysicalHeight(), display.getState()));
                secondary |= display.getDisplayId() != Display.DEFAULT_DISPLAY;
            }
            if (!secondary) text.append('\n').append(getString(R.string.no_secondary));
        } catch (RuntimeException error) {
            text.append('\n').append(getString(R.string.display_error, error.getClass().getSimpleName()));
        }
        information.setText(text);
    }
}
