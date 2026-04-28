package com.mapcontrol.ui.activity;

import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.manager.ClusterVDBusBenchManager;
import com.mapcontrol.ui.theme.UiStyles;

/**
 * Log sekmesinden açılır; araç / VDBus olaylarını bench eder.
 */
public class ClusterVDBusTestActivity extends AppCompatActivity implements ClusterVDBusBenchManager.UiActions {
    private static final String TAG = "ClusterVDBusTest";
    private ClusterVDBusBenchManager benchManager;
    private TextView tvOut;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.backgroundPage));

        LinearLayout backBar = new LinearLayout(this);
        backBar.setOrientation(LinearLayout.HORIZONTAL);
        backBar.setBackgroundColor(ContextCompat.getColor(this, R.color.surfaceBar));
        backBar.setPadding(16, 16, 16, 16);
        backBar.setGravity(Gravity.CENTER_VERTICAL);
        Button btnBack = new Button(this);
        btnBack.setText("← Geri");
        btnBack.setTextColor(ContextCompat.getColor(this, R.color.textPrimary));
        UiStyles.styleOemButton(btnBack, ContextCompat.getColor(this, R.color.buttonPrimary));
        btnBack.setOnClickListener(v -> finish());
        backBar.addView(btnBack);
        root.addView(backBar);

        TextView title = new TextView(this);
        title.setText("Cluster / VDBus bench");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(ContextCompat.getColor(this, R.color.textPrimary));
        title.setPadding(16, 12, 16, 4);
        root.addView(title);

        tvOut = new TextView(this);
        tvOut.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvOut.setTextColor(ContextCompat.getColor(this, R.color.textMuted));
        tvOut.setPadding(16, 0, 16, 8);
        tvOut.setText("Çıktı hem burada hem ana logda (MainActivity açıkken).");
        root.addView(tvOut);

        benchManager = new ClusterVDBusBenchManager(this, this, line -> {
            Log.d(TAG, line);
            runOnUiThread(() -> {
                CharSequence cur = tvOut.getText();
                String next = (cur != null ? cur.toString() : "") + "\n" + line;
                if (next.length() > 4000) {
                    next = next.substring(next.length() - 3500);
                }
                tvOut.setText(next.trim());
            });
            MainActivity.appendBenchLogToMainIfActive(line);
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(16, 8, 16, 24);

        int gap = UiStyles.dimenPx(this, R.dimen.spacing_small);
        int btnColor = ContextCompat.getColor(this, R.color.buttonSecondaryMuted);

        addBenchButton(col, "Nav toggle (26/4 yolu)", () -> benchManager.benchNavToggle(), btnColor, gap);
        addBenchButton(col, "Uyarı sesi (26/1)", () -> benchManager.benchAlertTone(), btnColor, gap);
        addBenchButton(col, "Cluster aç (UI / manager)", () -> benchManager.benchClusterOpenUi(), btnColor, gap);
        addBenchButton(col, "Cluster kapat (UI / manager)", () -> benchManager.benchClusterCloseUi(), btnColor, gap);
        addBenchButton(col, "Cluster aç (servis yolu)", () -> benchManager.benchClusterOpenService(), btnColor, gap);
        addBenchButton(col, "Cluster kapat (servis yolu)", () -> benchManager.benchClusterCloseService(), btnColor, gap);

        scroll.addView(col);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f);
        root.addView(scroll, scrollLp);

        setContentView(root);
    }

    private void addBenchButton(LinearLayout col, String label, Runnable r, int color, int gapBottom) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(ContextCompat.getColor(this, R.color.textPrimary));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        UiStyles.styleOemButton(b, color);
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, gapBottom);
        col.addView(b, lp);
    }

    @Override
    public void navKeyToggleLikeVDBus() {
        MainActivity.benchNavKeyToggle();
    }

    @Override
    public void alertToneLikeVDBus() {
        MainActivity.benchAlertTone();
    }

    @Override
    public void clusterOpenDirectUi() {
        MainActivity.benchClusterOpenDirect();
    }

    @Override
    public void clusterCloseDirectUi() {
        MainActivity.benchClusterCloseDirect();
    }
}
