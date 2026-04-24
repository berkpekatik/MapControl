package com.mapcontrol.ui.builder;
import android.content.Context;
import android.content.Intent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.mapcontrol.ui.activity.AudioTestActivity;
import com.mapcontrol.ui.activity.CameraActivity;

public class LogTabBuilder {
    public interface LogCallback {
        void onClearLogs();
        void log(String message);
    }

    private final Context context;
    private final LogCallback callback;

    private LinearLayout tabContent;
    private TextView logsTextView;
    private ScrollView logScrollView;

    public LogTabBuilder(Context context, LogCallback callback) {
        this.context = context;
        this.callback = callback;
        build();
    }

    public LinearLayout build() {
        tabContent = new LinearLayout(context);
        tabContent.setOrientation(LinearLayout.VERTICAL);
        tabContent.setPadding(12, 12, 12, 12);
        tabContent.setBackgroundColor(0xFF1E1E1E);
        tabContent.setVisibility(android.view.View.GONE);

        LinearLayout logControlPanel = new LinearLayout(context);
        logControlPanel.setOrientation(LinearLayout.HORIZONTAL);
        logControlPanel.setPadding(0, 0, 0, 8);
        logControlPanel.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView logTitle = new TextView(context);
        logTitle.setText("Sistem Kayıtları");
        logTitle.setTextSize(20);
        logTitle.setTextColor(0xFFFFFFFF);
        logTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams logTitleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        logControlPanel.addView(logTitle, logTitleParams);

        Button btnCameraTest = new Button(context);
        btnCameraTest.setText("📷 Kamera Test");
        btnCameraTest.setTextColor(0xFFFFFFFF);
        btnCameraTest.setTextSize(14);
        btnCameraTest.setBackgroundColor(0xFF3DAEA8);
        btnCameraTest.setPadding(16, 8, 16, 8);
        btnCameraTest.setOnClickListener(v -> context.startActivity(new Intent(context, CameraActivity.class)));
        LinearLayout.LayoutParams cameraTestParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cameraTestParams.setMargins(0, 0, 8, 0);
        logControlPanel.addView(btnCameraTest, cameraTestParams);

        Button btnAudioTest = new Button(context);
        btnAudioTest.setText("🔊 Hoşgeldin Ses Test");
        btnAudioTest.setTextColor(0xFFFFFFFF);
        btnAudioTest.setTextSize(14);
        btnAudioTest.setBackgroundColor(0xFF3DAEA8);
        btnAudioTest.setPadding(16, 8, 16, 8);
        btnAudioTest.setOnClickListener(v -> context.startActivity(new Intent(context, AudioTestActivity.class)));
        LinearLayout.LayoutParams audioTestParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        audioTestParams.setMargins(0, 0, 8, 0);
        logControlPanel.addView(btnAudioTest, audioTestParams);

        Button btnClearLogs = new Button(context);
        btnClearLogs.setText("🗑");
        btnClearLogs.setTextColor(0xFFFFFFFF);
        btnClearLogs.setTextSize(20);
        btnClearLogs.setBackgroundColor(0xFF3D1F1F);
        btnClearLogs.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams clearLogsParams = new LinearLayout.LayoutParams(56, 56);
        logControlPanel.addView(btnClearLogs, clearLogsParams);

        tabContent.addView(logControlPanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout terminalContainer = new LinearLayout(context);
        terminalContainer.setOrientation(LinearLayout.VERTICAL);
        terminalContainer.setBackgroundColor(0xFF0B1116);
        terminalContainer.setPadding(0, 0, 0, 0);

        LinearLayout terminalHeader = new LinearLayout(context);
        terminalHeader.setOrientation(LinearLayout.HORIZONTAL);
        terminalHeader.setBackgroundColor(0xFF161B22);
        terminalHeader.setPadding(16, 12, 16, 12);
        terminalHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout dotsLayout = new LinearLayout(context);
        dotsLayout.setOrientation(LinearLayout.HORIZONTAL);

        TextView redDot = new TextView(context);
        redDot.setText("●");
        redDot.setTextColor(0xFFFF5F56);
        redDot.setTextSize(16);
        redDot.setPadding(0, 0, 8, 0);
        dotsLayout.addView(redDot);

        TextView yellowDot = new TextView(context);
        yellowDot.setText("●");
        yellowDot.setTextColor(0xFFFFBD2E);
        yellowDot.setTextSize(16);
        yellowDot.setPadding(0, 0, 8, 0);
        dotsLayout.addView(yellowDot);

        TextView greenDot = new TextView(context);
        greenDot.setText("●");
        greenDot.setTextColor(0xFF27C93F);
        greenDot.setTextSize(16);
        dotsLayout.addView(greenDot);

        terminalHeader.addView(dotsLayout, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView terminalPath = new TextView(context);
        terminalPath.setText("/var/log/syslog/vehicle_core.log");
        terminalPath.setTextColor(0xFF9DABB9);
        terminalPath.setTextSize(10);
        terminalPath.setTypeface(android.graphics.Typeface.MONOSPACE);
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        pathParams.gravity = android.view.Gravity.END;
        terminalHeader.addView(terminalPath, pathParams);

        terminalContainer.addView(terminalHeader, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        logScrollView = new ScrollView(context);
        logScrollView.setBackgroundColor(0xFF0B1116);
        logScrollView.setPadding(12, 12, 12, 12);

        logsTextView = new TextView(context);
        logsTextView.setTextColor(0xFFD1D5DB);
        logsTextView.setTextSize(12);
        logsTextView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logsTextView.setPadding(8, 8, 8, 8);
        logsTextView.setLineSpacing(4, 1.0f);
        logScrollView.addView(logsTextView);

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f);
        terminalContainer.addView(logScrollView, scrollParams);

        LinearLayout.LayoutParams terminalParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f);
        terminalParams.setMargins(0, 8, 0, 0);
        tabContent.addView(terminalContainer, terminalParams);

        btnClearLogs.setOnClickListener(v -> callback.onClearLogs());

        return tabContent;
    }

    public LinearLayout getTabContent() {
        return tabContent;
    }

    public TextView getLogsTextView() {
        return logsTextView;
    }

    public ScrollView getScrollView() {
        return logScrollView;
    }
}
