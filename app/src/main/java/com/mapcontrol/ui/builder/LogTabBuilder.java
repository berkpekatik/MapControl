package com.mapcontrol.ui.builder;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.desaysv.ivi.extra.project.carinfo.AvmID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.ui.activity.AudioTestActivity;
import com.mapcontrol.ui.activity.CameraActivity;
import com.mapcontrol.ui.activity.ClusterVDBusTestActivity;

public class LogTabBuilder {
    public interface LogCallback {
        void onClearLogs();
        void log(String message);
        /** Kullanıcı metnini TTS (önce iFly, sonra sistem) ile okut */
        void onReadAloud(String text);
        /** Hoşgeldin cümlesini tekrar okut */
        void onWelcomeTts();
    }

    private final Context context;
    private final LogCallback callback;

    private LinearLayout tabContent;
    private TextView logsTextView;
    private ScrollView logScrollView;
    private EditText ttsInput;

    public LogTabBuilder(Context context, LogCallback callback) {
        this.context = context;
        this.callback = callback;
        build();
    }

    public LinearLayout build() {
        tabContent = new LinearLayout(context);
        tabContent.setOrientation(LinearLayout.VERTICAL);
        tabContent.setBaselineAligned(false);
        int margin = UiStyles.dimenPx(context, R.dimen.oem_card_margin);
        tabContent.setPadding(margin, margin, margin, margin);
        tabContent.setBackgroundColor(Color.TRANSPARENT);
        tabContent.setVisibility(android.view.View.GONE);

        LinearLayout glassShell = new LinearLayout(context);
        glassShell.setOrientation(LinearLayout.VERTICAL);
        glassShell.setBaselineAligned(false);
        int inner = UiStyles.dimenPx(context, R.dimen.oem_card_inner_padding);
        glassShell.setPadding(inner, inner, inner, inner);
        UiStyles.setGlassCardBackground(glassShell);
        LinearLayout.LayoutParams shellParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f);
        tabContent.addView(glassShell, shellParams);

        LinearLayout logControlPanel = new LinearLayout(context);
        logControlPanel.setOrientation(LinearLayout.HORIZONTAL);
        logControlPanel.setPadding(0, 0, 0, 8);
        logControlPanel.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView logTitle = new TextView(context);
        logTitle.setText("Sistem Kayıtları");
        logTitle.setTextSize(20);
        logTitle.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        logTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams logTitleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        logControlPanel.addView(logTitle, logTitleParams);

        Button btnCameraTest = new Button(context);
        btnCameraTest.setText("Kamera Test");
        btnCameraTest.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        btnCameraTest.setTextSize(14);
        UiStyles.styleOemButton(btnCameraTest, ContextCompat.getColor(context, R.color.buttonPrimary));
        btnCameraTest.setPadding(16, 8, 16, 8);
        UiStyles.setButtonStartIconTinted(btnCameraTest, R.drawable.ic_mdi_camera,
                ContextCompat.getColor(context, R.color.textPrimary),
                UiStyles.dimenPx(context, R.dimen.spacing_small));
        btnCameraTest.setOnClickListener(v -> {
            try {
                CarInfoProxy carInfo = CarInfoProxy.getInstance();
                if (carInfo.isServiceConnnected()) {
                    carInfo.sendItemValue(VDEventCarInfo.MODULE_AVM, AvmID.ID_AVM_DISPLAY, 1);
                    callback.log("Kamera Test: AVM görüntü isteği gönderildi (CarInfo MODULE_AVM, ID_AVM_DISPLAY=1), Camera2 ekranı açılıyor");
                } else {
                    callback.log("Kamera Test: CarInfo bağlı değil — sadece Camera2 önizleme açılıyor (AVM isteği atlandı)");
                }
            } catch (Throwable t) {
                callback.log("Kamera Test: AVM isteği hata: " + t.getMessage() + " — yine de Camera2 açılıyor");
            }
            context.startActivity(new Intent(context, CameraActivity.class));
        });
        LinearLayout.LayoutParams cameraTestParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cameraTestParams.setMargins(0, 0, 8, 0);
        logControlPanel.addView(btnCameraTest, cameraTestParams);

        Button btnAudioTest = new Button(context);
        btnAudioTest.setText("Audio Test");
        btnAudioTest.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        btnAudioTest.setTextSize(14);
        UiStyles.styleOemButton(btnAudioTest, ContextCompat.getColor(context, R.color.buttonSecondaryMuted));
        btnAudioTest.setPadding(16, 8, 16, 8);
        UiStyles.setButtonStartIconTinted(btnAudioTest, R.drawable.ic_mdi_volume_high,
                ContextCompat.getColor(context, R.color.textPrimary),
                UiStyles.dimenPx(context, R.dimen.spacing_small));
        btnAudioTest.setOnClickListener(v -> context.startActivity(new Intent(context, AudioTestActivity.class)));
        LinearLayout.LayoutParams audioTestParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        audioTestParams.setMargins(0, 0, 8, 0);
        logControlPanel.addView(btnAudioTest, audioTestParams);

        Button btnWelcome = new Button(context);
        btnWelcome.setText("Hoşgeldin");
        btnWelcome.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        btnWelcome.setTextSize(14);
        btnWelcome.setTypeface(null, Typeface.BOLD);
        UiStyles.styleOemButton(btnWelcome, ContextCompat.getColor(context, R.color.buttonWelcome));
        btnWelcome.setPadding(16, 8, 16, 8);
        UiStyles.setButtonStartIconTinted(btnWelcome, R.drawable.ic_mdi_bell,
                ContextCompat.getColor(context, R.color.textPrimary),
                UiStyles.dimenPx(context, R.dimen.spacing_small));
        btnWelcome.setOnClickListener(v -> callback.onWelcomeTts());
        LinearLayout.LayoutParams welcomeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        welcomeParams.setMargins(0, 0, 8, 0);
        logControlPanel.addView(btnWelcome, welcomeParams);

        Button btnClearLogs = new Button(context);
        btnClearLogs.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        btnClearLogs.setTextSize(20);
        btnClearLogs.setBackgroundColor(ContextCompat.getColor(context, R.color.buttonDestructiveBg));
        btnClearLogs.setPadding(0, 0, 0, 0);
        UiStyles.setButtonIconOnlyTinted(btnClearLogs, R.drawable.ic_mdi_delete,
                ContextCompat.getColor(context, R.color.textPrimary), "Kayıtları temizle");
        LinearLayout.LayoutParams clearLogsParams = new LinearLayout.LayoutParams(56, 56);
        logControlPanel.addView(btnClearLogs, clearLogsParams);

        LinearLayout ttsCard = new LinearLayout(context);
        ttsCard.setOrientation(LinearLayout.VERTICAL);
        UiStyles.applySolidRoundedBackgroundDp(ttsCard,
                ContextCompat.getColor(context, R.color.surfaceCard), 16f);
        ttsCard.setPadding(16, 14, 16, 14);
        LinearLayout.LayoutParams ttsCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        ttsCardParams.setMargins(0, 0, 0, 10);

        TextView ttsSectionTitle = new TextView(context);
        ttsSectionTitle.setText("Metin (klavye) — Sesle oku: önce iFly TTS, yoksa sistem TTS");
        ttsSectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        ttsSectionTitle.setTextColor(ContextCompat.getColor(context, R.color.textSecondary));
        ttsCard.addView(ttsSectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ttsInput = new EditText(context);
        ttsInput.setHint(R.string.log_tts_hint);
        ttsInput.setTextColor(ContextCompat.getColor(context, R.color.textTertiary));
        ttsInput.setHintTextColor(ContextCompat.getColor(context, R.color.textMuted));
        ttsInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        ttsInput.setTypeface(Typeface.MONOSPACE);
        ttsInput.setMinLines(2);
        ttsInput.setMaxLines(5);
        ttsInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        ttsInput.setPadding(14, 12, 14, 12);
        android.graphics.drawable.GradientDrawable ttsFieldBg = new android.graphics.drawable.GradientDrawable();
        ttsFieldBg.setColor(ContextCompat.getColor(context, R.color.logFieldBg));
        ttsFieldBg.setCornerRadius(context.getResources().getDimension(R.dimen.oem_button_radius));
        ttsFieldBg.setStroke(1, ContextCompat.getColor(context, R.color.outlineStrong));
        ttsInput.setBackground(ttsFieldBg);
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        etParams.setMargins(0, 8, 0, 0);
        ttsCard.addView(ttsInput, etParams);

        LinearLayout ttsButtons = new LinearLayout(context);
        ttsButtons.setOrientation(LinearLayout.HORIZONTAL);
        ttsButtons.setGravity(Gravity.END);
        ttsButtons.setPadding(0, 10, 0, 0);

        Button btnReadAloud = new Button(context);
        btnReadAloud.setText("Sesle oku");
        btnReadAloud.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        btnReadAloud.setTextSize(15);
        btnReadAloud.setTypeface(null, Typeface.BOLD);
        UiStyles.styleOemButton(btnReadAloud, ContextCompat.getColor(context, R.color.buttonPrimary));
        btnReadAloud.setPadding(20, 12, 20, 12);
        btnReadAloud.setOnClickListener(v -> {
            CharSequence cs = ttsInput.getText();
            String t = cs != null ? cs.toString() : "";
            callback.onReadAloud(t);
        });
        LinearLayout.LayoutParams readParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        ttsButtons.addView(btnReadAloud, readParams);
        ttsCard.addView(ttsButtons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button btnClusterVDBusTest = new Button(context);
        btnClusterVDBusTest.setText("Cluster / VDBus test ekranı");
        btnClusterVDBusTest.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnClusterVDBusTest.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        UiStyles.styleOemButton(btnClusterVDBusTest, ContextCompat.getColor(context, R.color.buttonSecondaryMuted));
        btnClusterVDBusTest.setPadding(16, 12, 16, 12);
        btnClusterVDBusTest.setOnClickListener(v ->
                context.startActivity(new Intent(context, ClusterVDBusTestActivity.class)));
        LinearLayout.LayoutParams benchBtnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        benchBtnLp.setMargins(0, 10, 0, 0);

        ScrollView upperScroll = new ScrollView(context);
        upperScroll.setFillViewport(true);
        LinearLayout upperColumn = new LinearLayout(context);
        upperColumn.setOrientation(LinearLayout.VERTICAL);
        upperColumn.setBaselineAligned(false);
        upperColumn.addView(logControlPanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        upperColumn.addView(btnClusterVDBusTest, benchBtnLp);
        upperColumn.addView(ttsCard, ttsCardParams);
        upperScroll.addView(upperColumn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams upperParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 0.34f);
        glassShell.addView(upperScroll, upperParams);

        LinearLayout terminalContainer = new LinearLayout(context);
        terminalContainer.setOrientation(LinearLayout.VERTICAL);
        terminalContainer.setBackgroundColor(ContextCompat.getColor(context, R.color.logTerminalChrome));
        terminalContainer.setPadding(0, 0, 0, 0);

        LinearLayout terminalHeader = new LinearLayout(context);
        terminalHeader.setOrientation(LinearLayout.HORIZONTAL);
        terminalHeader.setBackgroundColor(ContextCompat.getColor(context, R.color.surfaceColor));
        terminalHeader.setPadding(16, 12, 16, 12);
        terminalHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout dotsLayout = new LinearLayout(context);
        dotsLayout.setOrientation(LinearLayout.HORIZONTAL);

        TextView redDot = new TextView(context);
        redDot.setText("●");
        redDot.setTextColor(ContextCompat.getColor(context, R.color.terminalDotRed));
        redDot.setTextSize(16);
        redDot.setPadding(0, 0, 8, 0);
        dotsLayout.addView(redDot);

        TextView yellowDot = new TextView(context);
        yellowDot.setText("●");
        yellowDot.setTextColor(ContextCompat.getColor(context, R.color.terminalDotYellow));
        yellowDot.setTextSize(16);
        yellowDot.setPadding(0, 0, 8, 0);
        dotsLayout.addView(yellowDot);

        TextView greenDot = new TextView(context);
        greenDot.setText("●");
        greenDot.setTextColor(ContextCompat.getColor(context, R.color.terminalDotGreen));
        greenDot.setTextSize(16);
        dotsLayout.addView(greenDot);

        terminalHeader.addView(dotsLayout, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView terminalPath = new TextView(context);
        terminalPath.setText("/var/log/syslog/vehicle_core.log");
        terminalPath.setTextColor(ContextCompat.getColor(context, R.color.textSecondaryCool));
        terminalPath.setTextSize(11);
        terminalPath.setTypeface(android.graphics.Typeface.MONOSPACE);
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        pathParams.gravity = android.view.Gravity.END;
        terminalHeader.addView(terminalPath, pathParams);

        terminalContainer.addView(terminalHeader, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        logScrollView = new ScrollView(context);
        logScrollView.setFillViewport(true);
        logScrollView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_INSET);
        logScrollView.setBackgroundColor(ContextCompat.getColor(context, R.color.logTerminalChrome));
        logScrollView.setPadding(12, 12, 12, 12);

        logsTextView = new TextView(context);
        logsTextView.setTextColor(ContextCompat.getColor(context, R.color.textTertiary));
        logsTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        logsTextView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logsTextView.setPadding(10, 12, 10, 12);
        logsTextView.setLineSpacing(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 5f, context.getResources().getDisplayMetrics()), 1.0f);
        logsTextView.setMinHeight((int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 120f, context.getResources().getDisplayMetrics()));
        logScrollView.addView(logsTextView);

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f);
        terminalContainer.addView(logScrollView, scrollParams);

        LinearLayout.LayoutParams terminalParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 0.66f);
        terminalParams.setMargins(0, 8, 0, 0);
        glassShell.addView(terminalContainer, terminalParams);

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

    public EditText getTtsInput() {
        return ttsInput;
    }
}
