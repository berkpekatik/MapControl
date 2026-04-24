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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.mapcontrol.manager.WebServerManager;

public class FileUploadTabBuilder {
    private final Context context;
    private final WebServerManager webServerManager;

    private ScrollView scrollView;
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
        scrollView.setBackgroundColor(0xFF0A0F14);
        scrollView.setPadding(0, 0, 0, 0);
        scrollView.setFillViewport(true);

        LinearLayout fileUploadTabContent = new LinearLayout(context);
        fileUploadTabContent.setOrientation(LinearLayout.VERTICAL);
        fileUploadTabContent.setPadding(0, 0, 0, 0);
        fileUploadTabContent.setBackgroundColor(0xFF0A0F14);

        TextView fileUploadTitle = new TextView(context);
        fileUploadTitle.setText("Dosya Yükle");
        fileUploadTitle.setTextSize(18);
        fileUploadTitle.setTextColor(0xFFFFFFFF);
        fileUploadTitle.setTypeface(null, Typeface.BOLD);
        fileUploadTitle.setPadding(16, 16, 16, 8);
        fileUploadTabContent.addView(fileUploadTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView fileUploadDesc = new TextView(context);
        fileUploadDesc.setText("Web Server'ı başlatarak aynı ağdaki cihazlardan dosya yükleyebilirsiniz.");
        fileUploadDesc.setTextSize(13);
        fileUploadDesc.setTextColor(0xAAFFFFFF);
        fileUploadDesc.setPadding(16, 0, 16, 16);
        fileUploadTabContent.addView(fileUploadDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        btnWebServerToggle = new Button(context);
        btnWebServerToggle.setText("▶ Web Server Başlat");
        btnWebServerToggle.setTextColor(0xFFFFFFFF);
        btnWebServerToggle.setTextSize(16);
        btnWebServerToggle.setTypeface(null, Typeface.BOLD);
        btnWebServerToggle.setBackgroundColor(0xFF3DAEA8);
        btnWebServerToggle.setPadding(16, 20, 16, 20);
        LinearLayout.LayoutParams webServerToggleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        webServerToggleParams.setMargins(16, 0, 16, 16);
        fileUploadTabContent.addView(btnWebServerToggle, webServerToggleParams);

        LinearLayout urlQrContainer = new LinearLayout(context);
        urlQrContainer.setOrientation(LinearLayout.VERTICAL);
        urlQrContainer.setGravity(Gravity.CENTER);
        urlQrContainer.setPadding(16, 16, 16, 16);

        webServerStatusText = new TextView(context);
        webServerStatusText.setText("Sunucu durduruldu");
        webServerStatusText.setTextSize(20);
        webServerStatusText.setTextColor(0xAAFFFFFF);
        webServerStatusText.setGravity(Gravity.CENTER);
        webServerStatusText.setPadding(16, 16, 16, 16);
        webServerStatusText.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        urlQrContainer.addView(webServerStatusText, urlParams);

        qrCodeImageView = new ImageView(context);
        qrCodeImageView.setVisibility(View.GONE);
        qrCodeImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        qrCodeImageView.setPadding(16, 16, 16, 16);
        int qrSize = (int) (200 * context.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(
                qrSize,
                qrSize);
        qrParams.gravity = Gravity.CENTER;
        urlQrContainer.addView(qrCodeImageView, qrParams);

        fileUploadTabContent.addView(urlQrContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        btnWebServerToggle.setOnClickListener(v -> {
            if (webServerManager.isRunning()) {
                webServerManager.stopServer();
            } else {
                webServerManager.startServer();
            }
        });

        scrollView.addView(fileUploadTabContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        return scrollView;
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

                int size = 512;
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
