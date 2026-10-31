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
import com.mapcontrol.trip.PairingUri;
import com.mapcontrol.trip.PhoneAccess;
import com.mapcontrol.trip.TripRecorder;
import com.mapcontrol.ui.theme.UiStyles;

public class FileUploadTabBuilder {
    /** Açıksa web sunucusu uygulama açılınca ve kontak açılınca (güç modu 1/2) kendiliğinden başlar. */
    public static final String KEY_WEB_SERVER_AUTO_START = "webServerAutoStart";

    /** Telefon eşleme sayfasının deneme aşaması bilgisi bir kez gösterildi. */
    public static final String KEY_PHONE_PAIR_BETA_SHOWN = "phonePairBetaNoticeShown";

    private final Context context;
    private final WebServerManager webServerManager;

    private ScrollView scrollView;
    private LinearLayout fileUploadTabContent;
    private Button btnWebServerToggle;
    private TextView webServerStatusText;
    private ImageView qrCodeImageView;
    private ImageView pairQrImageView;
    private Button btnSegmentWeb;
    private Button btnSegmentPair;
    private LinearLayout webPanelView;
    private LinearLayout pairPanel;
    private boolean pairPageShown;
    /** Sunucu çalışırken adresi; eşleşme QR'ı buradan ana makine ve portu alır. */
    private String serverUrl;
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

        // Aynı anda tek QR görünsün: web yönetimi ve telefon eşleme ayrı sayfalarda, üstten geçilir.
        LinearLayout segments = new LinearLayout(context);
        segments.setOrientation(LinearLayout.HORIZONTAL);
        btnSegmentWeb = new Button(context);
        btnSegmentWeb.setText(R.string.web_mgmt_title);
        btnSegmentPair = new Button(context);
        btnSegmentPair.setText(R.string.phone_pair_tab);
        for (Button b : new Button[]{btnSegmentWeb, btnSegmentPair}) {
            b.setTextColor(UiStyles.color(context, R.color.textPrimary));
            b.setTextSize(15);
            b.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(0, 0, UiStyles.dimenPx(context, R.dimen.spacing_medium) / 2, UiStyles.dimenPx(context, R.dimen.spacing_medium));
            segments.addView(b, lp);
        }
        btnSegmentWeb.setOnClickListener(v -> showPairPage(false));
        btnSegmentPair.setOnClickListener(v -> {
            showPairPage(true);
            showBetaNoticeOnce();
        });
        fileUploadTabContent.addView(segments, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout webPanel = new LinearLayout(context);
        webPanel.setOrientation(LinearLayout.VERTICAL);
        pairPanel = new LinearLayout(context);
        pairPanel.setOrientation(LinearLayout.VERTICAL);
        webPanelView = webPanel;

        TextView fileUploadTitle = new TextView(context);
        fileUploadTitle.setText(R.string.web_mgmt_title);
        fileUploadTitle.setTextSize(18);
        fileUploadTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        fileUploadTitle.setTypeface(null, Typeface.BOLD);
        fileUploadTitle.setPadding(16, 16, 16, 8);
        webPanel.addView(fileUploadTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView fileUploadDesc = new TextView(context);
        fileUploadDesc.setText(R.string.web_mgmt_desc);
        fileUploadDesc.setTextSize(13);
        fileUploadDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        fileUploadDesc.setPadding(16, 0, 16, 16);
        webPanel.addView(fileUploadDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        btnWebServerToggle = new Button(context);
        btnWebServerToggle.setText(R.string.web_server_start);
        btnWebServerToggle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        btnWebServerToggle.setTextSize(16);
        btnWebServerToggle.setTypeface(null, Typeface.BOLD);
        UiStyles.styleOemButton(btnWebServerToggle, UiStyles.color(context, R.color.buttonPrimary));
        btnWebServerToggle.setPadding(16, 20, 16, 20);
        LinearLayout.LayoutParams webServerToggleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        webServerToggleParams.setMargins(0, 0, 0, UiStyles.dimenPx(context, R.dimen.spacing_medium));
        webPanel.addView(btnWebServerToggle, webServerToggleParams);

        android.widget.Switch autoStart = new android.widget.Switch(context);
        autoStart.setText(R.string.web_server_auto_start);
        autoStart.setTextSize(15);
        autoStart.setTextColor(UiStyles.color(context, R.color.textPrimary));
        autoStart.setPadding(16, 8, 16, 8);
        android.content.SharedPreferences prefs = context.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE);
        autoStart.setChecked(prefs.getBoolean(KEY_WEB_SERVER_AUTO_START, false));
        autoStart.setOnCheckedChangeListener((b, on) ->
                prefs.edit().putBoolean(KEY_WEB_SERVER_AUTO_START, on).apply());
        webPanel.addView(autoStart, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout urlQrContainer = new LinearLayout(context);
        urlQrContainer.setOrientation(LinearLayout.VERTICAL);
        urlQrContainer.setGravity(Gravity.CENTER);
        urlQrContainer.setPadding(16, 16, 16, 16);

        webServerStatusText = new TextView(context);
        webServerStatusText.setText(R.string.web_server_stopped);
        webServerStatusText.setTextSize(20);
        webServerStatusText.setTextColor(UiStyles.color(context, R.color.textHint));
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

        webPanel.addView(urlQrContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Telefon uygulaması yalnızca test kullanıcılarına açık: sayfada kalıcı bir not.
        LinearLayout betaBanner = new LinearLayout(context);
        betaBanner.setOrientation(LinearLayout.VERTICAL);
        betaBanner.setPadding(16, 12, 16, 12);
        betaBanner.setBackgroundColor(UiStyles.color(context, R.color.surfaceColor));
        TextView betaBannerTitle = new TextView(context);
        betaBannerTitle.setText(R.string.phone_pair_banner_title);
        betaBannerTitle.setTextSize(13);
        betaBannerTitle.setTypeface(null, Typeface.BOLD);
        betaBannerTitle.setTextColor(UiStyles.color(context, R.color.statusWarning));
        betaBanner.addView(betaBannerTitle);
        TextView betaBannerText = new TextView(context);
        betaBannerText.setText(R.string.phone_pair_banner);
        betaBannerText.setTextSize(13);
        betaBannerText.setTextColor(UiStyles.color(context, R.color.textHint));
        betaBannerText.setPadding(0, 4, 0, 0);
        betaBanner.addView(betaBannerText);
        LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bannerParams.setMargins(0, 0, 0, UiStyles.dimenPx(context, R.dimen.spacing_medium));
        pairPanel.addView(betaBanner, bannerParams);

        TextView pairTitle = new TextView(context);
        pairTitle.setText(R.string.phone_pair_title);
        pairTitle.setTextSize(16);
        pairTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        pairTitle.setTypeface(null, Typeface.BOLD);
        pairTitle.setPadding(16, 16, 16, 4);
        pairPanel.addView(pairTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView pairDesc = new TextView(context);
        pairDesc.setText(R.string.phone_pair_desc);
        pairDesc.setTextSize(13);
        pairDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        pairDesc.setPadding(16, 0, 16, 8);
        pairPanel.addView(pairDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView pairKey = new TextView(context);
        pairKey.setText(PhoneAccess.token(context));
        pairKey.setTextSize(15);
        pairKey.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        pairKey.setTextIsSelectable(true);
        pairKey.setTextColor(UiStyles.color(context, R.color.accentHighlight));
        pairKey.setPadding(16, 8, 16, 8);
        pairPanel.addView(pairKey, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button btnPairRegenerate = new Button(context);
        btnPairRegenerate.setText(R.string.phone_pair_regenerate);
        btnPairRegenerate.setTextColor(UiStyles.color(context, R.color.textPrimary));
        btnPairRegenerate.setTextSize(14);
        UiStyles.styleOemButton(btnPairRegenerate, UiStyles.color(context, R.color.buttonPrimary));
        btnPairRegenerate.setOnClickListener(v -> {
            pairKey.setText(PhoneAccess.regenerate(context));
            renderPairQr();
        });
        pairPanel.addView(btnPairRegenerate, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        pairQrImageView = new ImageView(context);
        pairQrImageView.setVisibility(View.GONE);
        pairQrImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        pairQrImageView.setPadding(16, 16, 16, 16);
        int pairQrSize = (int) (200 * context.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams pairQrParams = new LinearLayout.LayoutParams(pairQrSize, pairQrSize);
        pairQrParams.gravity = Gravity.CENTER;
        pairPanel.addView(pairQrImageView, pairQrParams);

        btnWebServerToggle.setOnClickListener(v -> {
            if (webServerManager.isRunning()) {
                webServerManager.stopServer();
            } else {
                webServerManager.startServer();
            }
        });

        fileUploadTabContent.addView(webPanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        fileUploadTabContent.addView(pairPanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        showPairPage(pairPageShown);

        outer.addView(fileUploadTabContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        if (serverUrl != null) {
            generateQRCode(serverUrl);
        }
        return scrollView;
    }

    /** Telefon eşleme sayfasına ilk geçişte bir kez: uygulamanın deneme aşamasında olduğunu söyler. */
    private void showBetaNoticeOnce() {
        android.content.SharedPreferences prefs = context.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_PHONE_PAIR_BETA_SHOWN, false)) {
            return;
        }
        prefs.edit().putBoolean(KEY_PHONE_PAIR_BETA_SHOWN, true).apply();
        com.mapcontrol.util.DialogHelper.showPhonePairingBeta(context);
    }

    private void showPairPage(boolean pair) {
        pairPageShown = pair;
        if (pair && pairQrImageView != null) {
            renderPairQr();
        }
        webPanelView.setVisibility(pair ? View.GONE : View.VISIBLE);
        pairPanel.setVisibility(pair ? View.VISIBLE : View.GONE);
        UiStyles.styleOemButton(btnSegmentWeb, UiStyles.color(context, pair ? R.color.buttonSecondaryMuted : R.color.buttonPrimary));
        UiStyles.styleOemButton(btnSegmentPair, UiStyles.color(context, pair ? R.color.buttonPrimary : R.color.buttonSecondaryMuted));
    }

    public LinearLayout getFileUploadTabContent() {
        return fileUploadTabContent;
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
        serverUrl = url;
        renderQr(url, qrCodeImageView);
        renderPairQr();
    }

    /** Sunucu durunca eski adresli QR ekranda kalmasın. */
    public void hidePairQr() {
        serverUrl = null;
        if (pairQrImageView != null) {
            pairQrImageView.setVisibility(View.GONE);
        }
    }

    /** Telefonun okuyacağı eşleşme QR'ı; sunucu çalışmıyorsa adres yoktur, gizli kalır. */
    private void renderPairQr() {
        String[] hostPort = serverUrl == null ? null : PairingUri.hostAndPort(serverUrl);
        if (hostPort == null) {
            return;
        }
        // Adres sunucu açıldığından beri değişmiş olabilir (hotspot sonradan açıldı); QR her çizimde güncel adresi alır.
        String host = webServerManager.currentHost();
        if (host.startsWith("127.")) {
            pairQrImageView.setVisibility(View.GONE);
            return;
        }
        String uri = PairingUri.build(host, Integer.parseInt(hostPort[1]),
                PhoneAccess.token(context), TripRecorder.get(context).vinOrEmpty());
        renderQr(uri, pairQrImageView);
    }

    private void renderQr(String content, ImageView target) {
        new Thread(() -> {
            try {
                QRCodeWriter qrCodeWriter = new QRCodeWriter();
                java.util.Map<EncodeHintType, Object> hints = new java.util.HashMap<>();
                hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
                hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
                hints.put(EncodeHintType.MARGIN, 1);

                int size = 512;
                BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, size, size, hints);

                Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
                for (int x = 0; x < size; x++) {
                    for (int y = 0; y < size; y++) {
                        bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                    }
                }

                handler.post(() -> {
                    if (target != null) {
                        target.setImageBitmap(bitmap);
                        target.setVisibility(View.VISIBLE);
                    }
                });
            } catch (WriterException ignored) {
            }
        }).start();
    }
}
