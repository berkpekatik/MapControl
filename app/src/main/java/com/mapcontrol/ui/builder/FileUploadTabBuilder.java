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
import com.mapcontrol.gps.GpsLinkManager;
import com.mapcontrol.gps.LinkState;
import com.mapcontrol.gps.LinkStatus;
import com.mapcontrol.gps.Wire;
import com.mapcontrol.manager.WebServerHost;
import com.mapcontrol.trip.PairingUri;
import com.mapcontrol.trip.PhoneAccess;
import com.mapcontrol.trip.TripRecorder;
import com.mapcontrol.ui.theme.UiStyles;

public class FileUploadTabBuilder {
    /** Açıksa web sunucusu uygulama açılınca ve kontak açılınca (güç modu 1/2) kendiliğinden başlar. */
    public static final String KEY_WEB_SERVER_AUTO_START = "webServerAutoStart";

    private final Context context;
    private final WebServerHost webServerHost;

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
    private TextView pairStatusText;
    private Button pairStartButton;
    private TextView gpsStatusText;
    private TextView gpsHintText;
    private final Runnable gpsRefresh = new Runnable() {
        @Override
        public void run() {
            if (!pairPageShown) {
                return;
            }
            renderGpsStatus();
            handler.postDelayed(this, 1000);
        }
    };
    /** Sunucu çalışırken adresi; eşleşme QR'ı buradan ana makine ve portu alır. */
    private String serverUrl;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public FileUploadTabBuilder(Context context, WebServerHost webServerHost) {
        this.context = context;
        this.webServerHost = webServerHost;
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
        btnSegmentWeb.setText(R.string.transfer_seg_browser);
        btnSegmentPair = new Button(context);
        btnSegmentPair.setText(R.string.transfer_seg_app);
        for (Button b : new Button[]{btnSegmentWeb, btnSegmentPair}) {
            b.setTextColor(UiStyles.color(context, R.color.textPrimary));
            UiStyles.setTextSize(b, R.dimen.text_size_normal);
            UiStyles.setFontStyle(b, Typeface.BOLD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(0, 0, UiStyles.dimenPx(context, R.dimen.spacing_medium) / 2, UiStyles.dimenPx(context, R.dimen.spacing_medium));
            segments.addView(b, lp);
        }
        btnSegmentWeb.setOnClickListener(v -> showPairPage(false));
        btnSegmentPair.setOnClickListener(v -> showPairPage(true));
        fileUploadTabContent.addView(segments, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout webPanel = new LinearLayout(context);
        webPanel.setOrientation(LinearLayout.VERTICAL);
        pairPanel = new LinearLayout(context);
        pairPanel.setOrientation(LinearLayout.VERTICAL);
        webPanelView = webPanel;
        // Yatay ekran: web yönetimi paneli iki kolonlu (sol kontroller, sağ durum + QR).
        webPanel.setOrientation(LinearLayout.HORIZONTAL);
        webPanel.setGravity(Gravity.TOP);
        LinearLayout row = webPanel;

        // --- SOL KOLON (%30-32) ---
        LinearLayout leftCol = new LinearLayout(context);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setGravity(Gravity.TOP);
        leftCol.setPadding(dp(8), dp(8), dp(16), dp(8));

        TextView fileUploadTitle = new TextView(context);
        fileUploadTitle.setText(R.string.transfer_browser_title);
        UiStyles.setTextSize(fileUploadTitle, R.dimen.text_size_large);
        fileUploadTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        UiStyles.setFontStyle(fileUploadTitle, Typeface.BOLD);
        leftCol.addView(fileUploadTitle);

        TextView fileUploadDesc = new TextView(context);
        fileUploadDesc.setText(R.string.transfer_browser_desc);
        UiStyles.setTextSize(fileUploadDesc, R.dimen.text_size_small);
        fileUploadDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        fileUploadDesc.setPadding(0, dp(6), 0, dp(12));
        leftCol.addView(fileUploadDesc);
        addSteps(leftCol, R.string.transfer_step_network, R.string.transfer_step_start, R.string.transfer_browser_step_scan);

        btnWebServerToggle = new Button(context);
        btnWebServerToggle.setText(R.string.web_server_start);
        btnWebServerToggle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        UiStyles.setTextSize(btnWebServerToggle, R.dimen.text_size_normal);
        UiStyles.setFontStyle(btnWebServerToggle, Typeface.BOLD);
        UiStyles.styleOemButton(btnWebServerToggle, UiStyles.color(context, R.color.buttonCta));
        btnWebServerToggle.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams webServerToggleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        webServerToggleParams.setMargins(0, 0, 0, UiStyles.dimenPx(context, R.dimen.spacing_medium));
        leftCol.addView(btnWebServerToggle, webServerToggleParams);

        android.content.SharedPreferences prefs = context.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE);
        UiStyles.addBinarySegmentedControl(context, leftCol,
                context.getString(R.string.web_server_auto_start),
                context.getString(R.string.common_on), context.getString(R.string.common_off),
                context.getString(R.string.web_server_auto_start_help_on),
                context.getString(R.string.web_server_auto_start_help_off),
                prefs.getBoolean(KEY_WEB_SERVER_AUTO_START, false),
                on -> prefs.edit().putBoolean(KEY_WEB_SERVER_AUTO_START, on).apply());

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
        UiStyles.setFontStyle(webServerStatusText, Typeface.BOLD);
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

        // Web Yönetimi ile aynı yerleşim: solda başlık ve kontroller, ortada ince çizgi, sağda durum ve büyük QR.
        LinearLayout pairRow = new LinearLayout(context);
        pairRow.setOrientation(LinearLayout.HORIZONTAL);
        pairRow.setGravity(Gravity.TOP);

        // --- SOL KOLON (%32) ---
        LinearLayout pairLeft = new LinearLayout(context);
        pairLeft.setOrientation(LinearLayout.VERTICAL);
        pairLeft.setGravity(Gravity.TOP);
        pairLeft.setPadding(dp(8), dp(8), dp(16), dp(8));

        TextView pairTitle = new TextView(context);
        pairTitle.setText(R.string.transfer_app_title);
        UiStyles.setTextSize(pairTitle, R.dimen.text_size_large);
        pairTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        UiStyles.setFontStyle(pairTitle, Typeface.BOLD);
        pairLeft.addView(pairTitle);

        TextView pairDesc = new TextView(context);
        pairDesc.setText(R.string.transfer_app_desc);
        UiStyles.setTextSize(pairDesc, R.dimen.text_size_small);
        pairDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        pairDesc.setPadding(0, dp(6), 0, dp(12));
        pairLeft.addView(pairDesc);
        addSteps(pairLeft, R.string.transfer_step_network, R.string.transfer_step_start, R.string.transfer_app_step_scan);

        TextView pairKeyLabel = new TextView(context);
        pairKeyLabel.setText(R.string.transfer_app_key_label);
        UiStyles.setTextSize(pairKeyLabel, R.dimen.text_size_small);
        pairKeyLabel.setTextColor(UiStyles.color(context, R.color.textHint));
        pairKeyLabel.setPadding(0, dp(12), 0, dp(4));
        pairLeft.addView(pairKeyLabel);

        TextView pairKey = new TextView(context);
        pairKey.setText(PhoneAccess.token(context));
        UiStyles.setTextSize(pairKey, R.dimen.text_size_normal);
        pairKey.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        pairKey.setTextIsSelectable(true);
        pairKey.setTextColor(UiStyles.color(context, R.color.accentHighlight));
        pairKey.setPadding(0, 0, 0, dp(16));
        pairLeft.addView(pairKey);

        Button btnPairRegenerate = new Button(context);
        btnPairRegenerate.setText(R.string.transfer_app_regenerate);
        btnPairRegenerate.setTextColor(UiStyles.color(context, R.color.textPrimary));
        UiStyles.setTextSize(btnPairRegenerate, R.dimen.text_size_normal);
        UiStyles.setFontStyle(btnPairRegenerate, Typeface.BOLD);
        UiStyles.styleOemButton(btnPairRegenerate, UiStyles.color(context, R.color.buttonCta));
        btnPairRegenerate.setPadding(dp(16), dp(16), dp(16), dp(16));
        btnPairRegenerate.setOnClickListener(v -> {
            pairKey.setText(PhoneAccess.regenerate(context));
            // GPS Link anahtarı eşleşme anahtarından türetilir: eski anahtarla çalışan alıcı kapanıp yenisi açılır.
            new Thread(() -> GpsLinkManager.get(context).restart(), "MapControlGPS-rekey").start();
            renderPairQr();
        });
        LinearLayout.LayoutParams regenerateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        regenerateParams.setMargins(0, 0, 0, dp(4));
        pairLeft.addView(btnPairRegenerate, regenerateParams);
        TextView regenerateNote = new TextView(context);
        regenerateNote.setText(R.string.transfer_app_regenerate_note);
        UiStyles.setTextSize(regenerateNote, R.dimen.text_size_small);
        regenerateNote.setTextColor(UiStyles.color(context, R.color.textHint));
        regenerateNote.setPadding(0, 0, 0, UiStyles.dimenPx(context, R.dimen.spacing_medium));
        pairLeft.addView(regenerateNote);

        // GPS Link: telefonun GPS'i aynı eşleşmeyle araca gelir; ayrı QR veya anahtar yok.
        View gpsDivider = new View(context);
        gpsDivider.setBackgroundColor(UiStyles.color(context, R.color.outlineSubtle));
        LinearLayout.LayoutParams gpsDividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        gpsDividerParams.setMargins(0, dp(8), 0, dp(12));
        pairLeft.addView(gpsDivider, gpsDividerParams);

        TextView gpsTitle = new TextView(context);
        gpsTitle.setText(R.string.transfer_app_gps_title);
        UiStyles.setTextSize(gpsTitle, R.dimen.text_size_normal);
        gpsTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        UiStyles.setFontStyle(gpsTitle, Typeface.BOLD);
        pairLeft.addView(gpsTitle);

        gpsStatusText = new TextView(context);
        UiStyles.setTextSize(gpsStatusText, R.dimen.text_size_normal);
        UiStyles.setFontStyle(gpsStatusText, Typeface.BOLD);
        gpsStatusText.setPadding(0, dp(4), 0, 0);
        pairLeft.addView(gpsStatusText);

        gpsHintText = new TextView(context);
        UiStyles.setTextSize(gpsHintText, R.dimen.text_size_small);
        gpsHintText.setTextColor(UiStyles.color(context, R.color.textHint));
        gpsHintText.setPadding(0, dp(2), 0, 0);
        pairLeft.addView(gpsHintText);
        renderGpsStatus();

        pairRow.addView(pairLeft, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.32f));

        // --- AYIRICI DİKEY ÇİZGİ ---
        View pairDivider = new View(context);
        pairDivider.setBackgroundColor(UiStyles.color(context, R.color.outlineSubtle));
        LinearLayout.LayoutParams pairDividerParams = new LinearLayout.LayoutParams(
                Math.max(1, dp(1)), LinearLayout.LayoutParams.MATCH_PARENT);
        pairDividerParams.setMargins(dp(8), dp(8), dp(8), dp(8));
        pairRow.addView(pairDivider, pairDividerParams);

        // --- SAĞ KOLON (%68) ---
        LinearLayout pairRight = new LinearLayout(context);
        pairRight.setOrientation(LinearLayout.VERTICAL);
        pairRight.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        pairRight.setPadding(dp(16), dp(8), dp(16), dp(8));

        pairStatusText = new TextView(context);
        pairStatusText.setText(R.string.transfer_app_server_off);
        UiStyles.setTextSize(pairStatusText, R.dimen.text_size_medium);
        pairStatusText.setTextColor(UiStyles.color(context, R.color.textHint));
        pairStatusText.setGravity(Gravity.CENTER);
        pairStatusText.setPadding(dp(8), dp(4), dp(8), dp(4));
        UiStyles.setFontStyle(pairStatusText, Typeface.BOLD);
        pairRight.addView(pairStatusText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Sunucu kapalıyken kullanıcı diğer sekmeye gitmesin: bağlantı buradan da başlar.
        pairStartButton = new Button(context);
        pairStartButton.setText(R.string.transfer_start);
        pairStartButton.setTextColor(UiStyles.color(context, R.color.textPrimary));
        UiStyles.setTextSize(pairStartButton, R.dimen.text_size_normal);
        UiStyles.setFontStyle(pairStartButton, Typeface.BOLD);
        UiStyles.styleOemButton(pairStartButton, UiStyles.color(context, R.color.buttonCta));
        pairStartButton.setPadding(dp(32), dp(16), dp(32), dp(16));
        pairStartButton.setOnClickListener(v -> webServerHost.toggleServer());
        LinearLayout.LayoutParams pairStartParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pairStartParams.gravity = Gravity.CENTER_HORIZONTAL;
        pairStartParams.topMargin = dp(16);
        pairRight.addView(pairStartButton, pairStartParams);

        pairQrImageView = new ImageView(context);
        pairQrImageView.setVisibility(View.GONE);
        pairQrImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pairQrImageView.setAdjustViewBounds(true);
        LinearLayout.LayoutParams pairQrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
        pairQrParams.gravity = Gravity.CENTER_HORIZONTAL;
        pairQrParams.topMargin = dp(6);
        pairRight.addView(pairQrImageView, pairQrParams);

        pairRow.addView(pairRight, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.68f));
        pairPanel.addView(pairRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        btnWebServerToggle.setOnClickListener(v -> webServerHost.toggleServer());

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
    private void showPairPage(boolean pair) {
        pairPageShown = pair;
        handler.removeCallbacks(gpsRefresh);
        if (pair && pairQrImageView != null) {
            renderPairQr();
        }
        if (pair) {
            handler.post(gpsRefresh);
        }
        webPanelView.setVisibility(pair ? View.GONE : View.VISIBLE);
        pairPanel.setVisibility(pair ? View.VISIBLE : View.GONE);
        UiStyles.styleOemButton(btnSegmentWeb, UiStyles.color(context, pair ? R.color.buttonSecondaryMuted : R.color.buttonPrimary));
        UiStyles.styleOemButton(btnSegmentPair, UiStyles.color(context, pair ? R.color.buttonPrimary : R.color.buttonSecondaryMuted));
    }

    public LinearLayout getFileUploadTabContent() {
        return fileUploadTabContent;
    }

    /** Numaralı kısa adımlar (1, 2, 3…): yuvarlak numara + tek satırlık açıklama. */
    private void addSteps(LinearLayout parent, int... steps) {
        int accent = UiStyles.color(context, R.color.accentHighlight);
        for (int i = 0; i < steps.length; i++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, dp(4));

            TextView number = new TextView(context);
            number.setText(String.valueOf(i + 1));
            number.setGravity(Gravity.CENTER);
            number.setTextColor(Color.WHITE);
            UiStyles.setTextSize(number, R.dimen.text_size_small);
            UiStyles.setFontStyle(number, Typeface.BOLD);
            android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circle.setColor(accent);
            number.setBackground(circle);
            row.addView(number, new LinearLayout.LayoutParams(dp(26), dp(26)));

            TextView label = new TextView(context);
            label.setText(steps[i]);
            UiStyles.setTextSize(label, R.dimen.text_size_small);
            label.setTextColor(UiStyles.color(context, R.color.textPrimary));
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            labelParams.setMarginStart(dp(12));
            row.addView(label, labelParams);
            parent.addView(row);
        }
        View spacer = new View(context);
        parent.addView(spacer, new LinearLayout.LayoutParams(1, dp(12)));
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
        serverUrl = url;
        renderQr(url, qrCodeImageView);
        renderPairQr();
    }

    /** Sunucu durunca eski adresli QR ekranda kalmasın. */
    public void hidePairQr() {
        serverUrl = null;
        showPairQrOff();
    }

    /** QR yok: görsel gizlenir; sunucu kapalıysa başlatma düğmesi, açık ama ağ yoksa ağ uyarısı görünür. */
    private void showPairQrOff() {
        boolean serverOn = serverUrl != null;
        if (pairQrImageView != null) {
            pairQrImageView.setVisibility(View.GONE);
        }
        if (pairStatusText != null) {
            pairStatusText.setText(serverOn ? R.string.transfer_app_no_network : R.string.transfer_app_server_off);
        }
        if (pairStartButton != null) {
            pairStartButton.setVisibility(serverOn ? View.GONE : View.VISIBLE);
        }
    }

    /** Telefonun okuyacağı eşleşme QR'ı; sunucu çalışmıyorsa adres yoktur, gizli kalır. */
    private void renderPairQr() {
        String[] hostPort = serverUrl == null ? null : PairingUri.hostAndPort(serverUrl);
        if (hostPort == null) {
            showPairQrOff();
            return;
        }
        // Adres sunucu açıldığından beri değişmiş olabilir (hotspot sonradan açıldı); QR her çizimde güncel adresi alır.
        String host = webServerHost.currentHost();
        if (host.startsWith("127.")) {
            showPairQrOff();
            return;
        }
        pairStatusText.setText(R.string.transfer_app_scan);
        if (pairStartButton != null) {
            pairStartButton.setVisibility(View.GONE);
        }
        String uri = PairingUri.build(host, Integer.parseInt(hostPort[1]),
                PhoneAccess.token(context), TripRecorder.get(context).vinOrEmpty(),
                Wire.PORT, PhoneAccess.carId(context));
        renderQr(uri, pairQrImageView);
    }

    /** GPS Link durumunu ve nedenini gösterir; canlıyken son konumun yaşı ve doğruluğu eklenir. */
    private void renderGpsStatus() {
        if (gpsStatusText == null || gpsHintText == null) {
            return;
        }
        LinkStatus status = LinkState.status();
        int color;
        switch (status.tone) {
            case OK:
                color = R.color.statusSuccess;
                break;
            case ERROR:
                color = R.color.statusErrorBright;
                break;
            case WARN:
                color = R.color.statusWarning;
                break;
            default:
                color = R.color.textHint;
                break;
        }
        gpsStatusText.setText(status.labelRes);
        gpsStatusText.setTextColor(UiStyles.color(context, color));
        long ageMs = LinkState.fixAgeMs();
        if (status == LinkStatus.LIVE && ageMs >= 0) {
            gpsHintText.setText(context.getString(status.hintRes, ageMs / 1000f, LinkState.accuracy()));
        } else {
            gpsHintText.setText(status.hintRes);
        }
    }

    private void renderQr(String content, ImageView target) {
        new Thread(() -> {
            try {
                QRCodeWriter qrCodeWriter = new QRCodeWriter();
                java.util.Map<EncodeHintType, Object> hints = new java.util.HashMap<>();
                hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
                hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
                hints.put(EncodeHintType.MARGIN, 1);

                int size = 1024;
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
