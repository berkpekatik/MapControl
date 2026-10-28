package com.mapcontrol.ui.builder;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.mapcontrol.R;
import com.mapcontrol.manager.WebServerManager;
import com.mapcontrol.ui.theme.UiStyles;

public class FileUploadTabBuilder {
    private final Context context;
    private final WebServerManager webServerManager;

    private ScrollView scrollView;
    private LinearLayout fileUploadTabContent;
    private Button btnWebServerToggle;
    private TextView webServerStatusText;
    private ImageView qrCodeImageView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public FileUploadTabBuilder(Context context, WebServerManager webServerManager) {
        this.context = context;
        this.webServerManager = webServerManager;
        build();
    }

    public ScrollView build() {
        scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        scrollView.setPadding(0, 0, 0, 0);
        scrollView.setFillViewport(true);

        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        int margin = UiStyles.dimenPx(context, R.dimen.oem_card_margin);
        outer.setPadding(margin, margin, margin, margin);

        fileUploadTabContent = new LinearLayout(context);
        fileUploadTabContent.setOrientation(LinearLayout.VERTICAL);
        int inner = UiStyles.dimenPx(context, R.dimen.oem_card_inner_padding);
        fileUploadTabContent.setPadding(inner, inner, inner, inner);
        UiStyles.setGlassCardBackground(fileUploadTabContent);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);

        // --- SOL KOLON (%30-32) ---
        LinearLayout leftCol = new LinearLayout(context);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setGravity(Gravity.TOP);
        leftCol.setPadding(dp(8), dp(8), dp(16), dp(8));

        TextView fileUploadTitle = new TextView(context);
        fileUploadTitle.setText(R.string.web_mgmt_title);
        UiStyles.setTextSize(fileUploadTitle, R.dimen.text_size_large);
        fileUploadTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        fileUploadTitle.setTypeface(null, Typeface.BOLD);
        leftCol.addView(fileUploadTitle);

        TextView fileUploadDesc = new TextView(context);
        fileUploadDesc.setText(R.string.web_mgmt_desc);
        UiStyles.setTextSize(fileUploadDesc, R.dimen.text_size_small);
        fileUploadDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        fileUploadDesc.setPadding(0, dp(6), 0, dp(16));
        leftCol.addView(fileUploadDesc);

        btnWebServerToggle = new Button(context);
        btnWebServerToggle.setText(R.string.web_server_start);
        btnWebServerToggle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        UiStyles.setTextSize(btnWebServerToggle, R.dimen.text_size_normal);
        btnWebServerToggle.setTypeface(null, Typeface.BOLD);
        UiStyles.styleOemButton(btnWebServerToggle, UiStyles.color(context, R.color.buttonPrimary));
        btnWebServerToggle.setPadding(dp(16), dp(16), dp(16), dp(16));
        leftCol.addView(btnWebServerToggle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        row.addView(leftCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.32f));

        // --- AYIRICI DİKEY ÇİZGİ ---
        View divider = new View(context);
        divider.setBackgroundColor(UiStyles.color(context, R.color.outlineSubtle));
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(Math.max(1, dp(1)), LinearLayout.LayoutParams.MATCH_PARENT);
        divParams.setMargins(dp(8), dp(8), dp(8), dp(8));
        row.addView(divider, divParams);

        // --- SAĞ KOLON (%68-70) ---
        LinearLayout rightCol = new LinearLayout(context);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        rightCol.setPadding(dp(16), dp(8), dp(16), dp(8));

        webServerStatusText = new TextView(context);
        webServerStatusText.setText(R.string.web_server_stopped);
        UiStyles.setTextSize(webServerStatusText, R.dimen.text_size_medium);
        webServerStatusText.setTextColor(UiStyles.color(context, R.color.textHint));
        webServerStatusText.setGravity(Gravity.CENTER);
        webServerStatusText.setPadding(dp(8), dp(4), dp(8), dp(4));
        webServerStatusText.setTypeface(null, Typeface.BOLD);
        rightCol.addView(webServerStatusText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        qrCodeImageView = new ImageView(context);
        qrCodeImageView.setVisibility(View.GONE);
        qrCodeImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrCodeImageView.setAdjustViewBounds(true);
        int screenHeightDp = context.getResources().getConfiguration().screenHeightDp;
        int maxQrDp = Math.max(260, screenHeightDp - 170);
        int qrSizeDp = Math.min(400, maxQrDp);
        int qrSize = Math.round(qrSizeDp * context.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
        qrParams.gravity = Gravity.CENTER_HORIZONTAL;
        qrParams.topMargin = dp(6);
        rightCol.addView(qrCodeImageView, qrParams);

        row.addView(rightCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.68f));

        fileUploadTabContent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        btnWebServerToggle.setOnClickListener(v -> {
            if (webServerManager.isRunning()) {
                webServerManager.stopServer();
            } else {
                webServerManager.startServer();
            }
        });

        outer.addView(fileUploadTabContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        return scrollView;
    }

    public LinearLayout getFileUploadTabContent() {
        return fileUploadTabContent;
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public ScrollView getScrollView() {
        if (scrollView == null) {
            return build();
        }
        return scrollView;
    }

    public Button getToggleButton() {
        return btnWebServerToggle;
    }

    public TextView getStatusText() {
        return webServerStatusText;
    }

    public ImageView getQrImageView() {
        return qrCodeImageView;
    }

    public void generateQRCode(String url) {
        new Thread(() -> {
            try {
                QRCodeWriter qrCodeWriter = new QRCodeWriter();
                java.util.Map<EncodeHintType, Object> hints = new java.util.HashMap<>();
                hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
                hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
                hints.put(EncodeHintType.MARGIN, 1);

                int size = 1024;
                BitMatrix bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, size, size, hints);

                Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
                for (int x = 0; x < size; x++) {
                    for (int y = 0; y < size; y++) {
                        bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                    }
                }

                handler.post(() -> {
                    if (qrCodeImageView != null) {
                        qrCodeImageView.setImageBitmap(bitmap);
                        qrCodeImageView.setVisibility(View.VISIBLE);
                    }
                });
            } catch (WriterException ignored) {
            }
        }).start();
    }
}
