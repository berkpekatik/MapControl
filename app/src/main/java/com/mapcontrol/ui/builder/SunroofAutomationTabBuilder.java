package com.mapcontrol.ui.builder;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatImageView;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.DialogHelper;
import com.mapcontrol.util.GestureConflicts;
import com.mapcontrol.util.SplitScreenGesture;
import com.mapcontrol.vehicle.SunroofAutomationEngine;
import com.mapcontrol.vehicle.SunroofGestureOverlayManager;
import com.mapcontrol.vehicle.SunroofGestureOverlayManager.GestureAction;
import com.mapcontrol.vehicle.SunroofGestureStore;
import com.mapcontrol.vehicle.VehicleLightSensorManager;
import com.mapcontrol.vehicle.VehicleSunroofController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chery Tiggo (Yatay Ekran) ve Jaecoo (Dikey Ekran) uyumlu Cam Tavan Otomasyonu Sekmesi.
 * Anlık ölçülen lux değerini, insan dilinde hava/güneş durumunu ve tek dokunuşla
 * seçilebilir mod kartlarını sunar.
 */
public final class SunroofAutomationTabBuilder implements SunroofAutomationEngine.Listener {

    public interface Callback {
        void log(String message);
    }

    private final Context context;
    private final Callback callback;
    private final SunroofAutomationEngine engine;
    private final VehicleLightSensorManager lightManager;
    private final VehicleSunroofController sunroofController;
    private final SunroofGestureOverlayManager gestureManager;
    private final SharedPreferences prefs;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    // Canlı Gösterge View'ları
    private TextView tvConditionTitle;
    private TextView tvLiveLux;
    private TextView tvConditionAdvice;
    private TextView tvSunroofState;
    private TextView tvRainState;
    private TextView tvSpeed;
    private TextView tvStatusMessage;

    // Ayar View'ları
    private Switch switchMaster;
    private Switch switchRainGuard;
    private Switch switchSpeedGuard;
    private Switch switchCloseAtNight;

    // Seçilebilir Mod Kartları
    private LinearLayout cardModeSmart;
    private LinearLayout cardModeShadeOnly;
    private LinearLayout cardModeOpenAir;
    private LinearLayout cardModeAlwaysOpen;
    private TextView btnScopeShadeOnly;
    private TextView btnScopeSunroofAndShade;
    private LinearLayout sensSectionContainer;

    private Button btnSensSensitive;
    private Button btnSensBalanced;
    private Button btnSensBroad;

    // Hızlı kaydırma kumandası: işlem başına seçilen hareket
    private LinearLayout gestureGrid;

    public SunroofAutomationTabBuilder(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
        this.engine = SunroofAutomationEngine.getInstance(context);
        this.lightManager = VehicleLightSensorManager.getInstance(context);
        this.sunroofController = VehicleSunroofController.getInstance(context);
        this.gestureManager = SunroofGestureOverlayManager.getInstance(context);
        this.prefs = engine.getPrefs();
    }

    public LinearLayout build() {
        engine.removeListener(this);

        float density = context.getResources().getDisplayMetrics().density;
        boolean isPortrait = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        boolean compactLayout = context.getResources().getConfiguration().screenWidthDp < 600 || isPortrait;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pagePadding = dp(compactLayout ? 12 : 20, density);
        root.setPadding(pagePadding, dp(8, density), pagePadding, dp(32, density));

        // 1. Header (Chery / Jaecoo Uyumlu)
        root.addView(buildHeader(density, compactLayout));

        if (!isPortrait) {
            // YATAY EKRAN (Chery Tiggo 7/8, Omoda 5 vb.): 2 Sütunlu Dengeli Kokpit Grid
            LinearLayout twoColRow = new LinearLayout(context);
            twoColRow.setOrientation(LinearLayout.HORIZONTAL);
            twoColRow.setBaselineAligned(false);

            // Sol Sütun: Canlı Durum & Işık Ölçümü (Üst) + Otomatik Cam Tavan (Alt)
            LinearLayout colLeft = new LinearLayout(context);
            colLeft.setOrientation(LinearLayout.VERTICAL);
            colLeft.addView(buildLiveConditionCard(density));
            colLeft.addView(buildAutomationModeCard(density));

            // Sağ Sütun: Hızlı Kaydırma Kumandası (Üst) + Emniyet ve Entegrasyon (Alt)
            LinearLayout colRight = new LinearLayout(context);
            colRight.setOrientation(LinearLayout.VERTICAL);
            colRight.addView(buildQuickControlsCard(density));
            colRight.addView(buildSafetyCard(density));

            LinearLayout.LayoutParams lpLeft = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lpLeft.rightMargin = dp(10, density);
            twoColRow.addView(colLeft, lpLeft);

            LinearLayout.LayoutParams lpRight = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lpRight.leftMargin = dp(10, density);
            twoColRow.addView(colRight, lpRight);

            root.addView(twoColRow);
        } else {
            // DİKEY EKRAN (Jaecoo 7 vb.): Dikey sıralı akış
            root.addView(buildLiveConditionCard(density));
            root.addView(buildQuickControlsCard(density));
            root.addView(buildAutomationModeCard(density));
            root.addView(buildSafetyCard(density));
        }

        engine.addListener(this);
        return root;
    }

    private View buildHeader(float density, boolean compactLayout) {
        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(0, dp(4, density), 0, dp(12, density));

        FrameLayout titleIconBadge = new FrameLayout(context);
        GradientDrawable titleIconBackground = new GradientDrawable();
        titleIconBackground.setColor(UiStyles.color(context, R.color.surfaceCardInner));
        titleIconBackground.setCornerRadius(dp(16, density));
        titleIconBadge.setBackground(titleIconBackground);

        AppCompatImageView titleIcon = new AppCompatImageView(context);
        titleIcon.setImageResource(R.drawable.ic_mdi_car_sunroof);
        titleIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        titleIcon.setImageTintList(ColorStateList.valueOf(UiStyles.color(context, R.color.oemAccent)));
        int iconPx = dp(28, density);
        FrameLayout.LayoutParams titleIconLp = new FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER);
        titleIconBadge.addView(titleIcon, titleIconLp);
        titleRow.addView(titleIconBadge, new LinearLayout.LayoutParams(dp(52, density), dp(52, density)));

        LinearLayout heading = new LinearLayout(context);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(14, density), 0, 0, 0);

        TextView title = new TextView(context);
        title.setText("Akıllı Cam Tavan Otomasyonu");
        title.setTextSize(22);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        title.setTypeface(null, Typeface.BOLD);
        heading.addView(title);

        TextView subtitle = new TextView(context);
        subtitle.setText("Güneş yakıyorsa tavanı kapatır; bulutlu havada veya gün batımında açar.");
        subtitle.setTextSize(12);
        subtitle.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
        heading.addView(subtitle);

        titleRow.addView(heading, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return titleRow;
    }

    /**
     * Canlı Durum Kartı: İnsani durum göstergesi + ANLIK LUX DEĞERİ + Entegre Kompakt Araç Rozetleri.
     */
    private View buildLiveConditionCard(float density) {
        LinearLayout card = createPanelCard(density);

        LinearLayout conditionBox = new LinearLayout(context);
        conditionBox.setOrientation(LinearLayout.VERTICAL);
        conditionBox.setPadding(dp(14, density), dp(12, density), dp(14, density), dp(12, density));

        GradientDrawable condBg = new GradientDrawable();
        condBg.setColor(UiStyles.color(context, R.color.surfaceCardInner));
        condBg.setCornerRadius(dp(16, density));
        condBg.setStroke(dp(1, density), UiStyles.color(context, R.color.automationCardStroke));
        conditionBox.setBackground(condBg);

        TextView tvCondLabel = new TextView(context);
        tvCondLabel.setText("ANLIK HAVA VE IŞIK DURUMU");
        tvCondLabel.setTextSize(10);
        tvCondLabel.setTypeface(null, Typeface.BOLD);
        tvCondLabel.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
        conditionBox.addView(tvCondLabel);

        tvConditionTitle = new TextView(context);
        tvConditionTitle.setTextSize(20);
        tvConditionTitle.setTypeface(null, Typeface.BOLD);
        tvConditionTitle.setTextColor(UiStyles.color(context, R.color.oemAccent));
        tvConditionTitle.setPadding(0, dp(3, density), 0, dp(1, density));
        conditionBox.addView(tvConditionTitle);

        // Anlık Ölçülen Lux Değeri
        tvLiveLux = new TextView(context);
        tvLiveLux.setTextSize(13);
        tvLiveLux.setTypeface(null, Typeface.BOLD);
        tvLiveLux.setTextColor(UiStyles.color(context, R.color.textPrimary));
        tvLiveLux.setPadding(0, dp(1, density), 0, dp(2, density));
        conditionBox.addView(tvLiveLux);

        SunroofAutomationEngine initialEngine = SunroofAutomationEngine.getInstance(context);
        SunroofAutomationEngine.Status initStatus = initialEngine.buildStatus();
        if (initStatus != null) {
            tvConditionTitle.setText(initStatus.conditionTitle);
            String src = initStatus.luxSource != null && !initStatus.luxSource.isEmpty() ? " (" + initStatus.luxSource + ")" : "";
            if (initStatus.averageLux > 0 && Math.abs(initStatus.currentLux - initStatus.averageLux) > 2500f) {
                tvLiveLux.setText(String.format(Locale.getDefault(), "Ölçülen Işık: %,d lx (Ort: %,d lx)%s",
                        (int) initStatus.currentLux, (int) initStatus.averageLux, src));
            } else {
                tvLiveLux.setText(String.format(Locale.getDefault(), "Ölçülen Işık: %,d lx%s", (int) initStatus.currentLux, src));
            }
        } else {
            tvConditionTitle.setText("Sensör Okunuyor...");
            tvLiveLux.setText("Ölçülen Işık: -- lx");
        }

        tvConditionAdvice = new TextView(context);
        tvConditionAdvice.setText("Işık rahatsız etmiyor, ferah sürüş için cam tavan açık tutuluyor.");
        tvConditionAdvice.setTextSize(11);
        tvConditionAdvice.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
        conditionBox.addView(tvConditionAdvice);

        // Kırmızı Kutu Rozetleri: Kompakt 3'lü Yatay Rozetler (Cam Tavan, Yağmur, Araç Hızı)
        LinearLayout badgesRow = new LinearLayout(context);
        badgesRow.setOrientation(LinearLayout.HORIZONTAL);
        badgesRow.setGravity(Gravity.CENTER_VERTICAL);
        badgesRow.setBaselineAligned(false);
        badgesRow.setPadding(0, dp(10, density), 0, 0);

        tvSunroofState = createCompactBadge(badgesRow, "Cam Tavan", "Bağlantı bekleniyor", density, false);
        tvRainState = createCompactBadge(badgesRow, "Yağmur", "Kuru ☀️", density, false);
        tvSpeed = createCompactBadge(badgesRow, "Araç Hızı", "0 km/s", density, true);
        conditionBox.addView(badgesRow);

        card.addView(conditionBox);

        // Alt Bilgi Notu (Son Otomasyon Hareketi)
        tvStatusMessage = new TextView(context);
        tvStatusMessage.setText("Son işlem: Bekleniyor");
        tvStatusMessage.setTextSize(11);
        tvStatusMessage.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
        tvStatusMessage.setPadding(dp(4, density), dp(6, density), 0, 0);
        card.addView(tvStatusMessage);

        return card;
    }

    private TextView createCompactBadge(LinearLayout parent, String label, String initialValue, float density, boolean isLast) {
        LinearLayout badge = new LinearLayout(context);
        badge.setOrientation(LinearLayout.VERTICAL);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(6, density), dp(6, density), dp(6, density), dp(6, density));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x221E293B);
        bg.setCornerRadius(dp(10, density));
        bg.setStroke(dp(1, density), 0x3338BDF8);
        badge.setBackground(bg);

        TextView tvLabel = new TextView(context);
        tvLabel.setText(label);
        tvLabel.setTextSize(10);
        tvLabel.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
        tvLabel.setGravity(Gravity.CENTER);
        badge.addView(tvLabel);

        TextView tvVal = new TextView(context);
        tvVal.setText(initialValue);
        tvVal.setTextSize(12);
        tvVal.setTypeface(null, Typeface.BOLD);
        tvVal.setTextColor(UiStyles.color(context, R.color.textPrimary));
        tvVal.setGravity(Gravity.CENTER);
        badge.addView(tvVal);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (!isLast) {
            lp.rightMargin = dp(6, density);
        }
        parent.addView(badge, lp);
        return tvVal;
    }

    /**
     * Otomasyon Modu ve Güneş Hassasiyeti Kartı (Kaldırılamayan RadioButton yerine Seçilebilir Kartlar).
     */
    private View buildAutomationModeCard(float density) {
        LinearLayout card = createPanelCard(density);

        // 1. Ana Otomasyon Anahtarı
        LinearLayout masterRow = new LinearLayout(context);
        masterRow.setOrientation(LinearLayout.HORIZONTAL);
        masterRow.setGravity(Gravity.CENTER_VERTICAL);
        masterRow.setPadding(0, 0, 0, dp(12, density));

        LinearLayout masterTextCol = new LinearLayout(context);
        masterTextCol.setOrientation(LinearLayout.VERTICAL);
        TextView masterTitle = new TextView(context);
        masterTitle.setText("Otomatik Cam Tavan");
        masterTitle.setTextSize(16);
        masterTitle.setTypeface(null, Typeface.BOLD);
        masterTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        masterTextCol.addView(masterTitle);

        TextView masterDesc = new TextView(context);
        masterDesc.setText("Güneşe, buluta ve gün batımına göre tavanı yönetir");
        masterDesc.setTextSize(12);
        masterDesc.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
        masterTextCol.addView(masterDesc);

        masterRow.addView(masterTextCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        switchMaster = new Switch(context);
        switchMaster.setChecked(prefs.getBoolean(SunroofAutomationEngine.KEY_ENABLED, false));
        switchMaster.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean(SunroofAutomationEngine.KEY_ENABLED, isChecked).apply();
            callback.log(isChecked ? "Cam tavan otomasyonu açıldı" : "Cam tavan otomasyonu kapatıldı");
        });
        masterRow.addView(switchMaster);
        card.addView(masterRow);

        // 1.5. Otomasyon Kapsamı (Yalnızca Perde / Tavan + Perde)
        TextView scopeTitle = createSectionTitle("Ne Yönetilsin? (Otomasyon Kapsamı)", density);
        scopeTitle.setPadding(0, dp(14, density), 0, dp(6, density));
        card.addView(scopeTitle);
        card.addView(buildScopeSelector(density));

        // 2. Çalışma Mantığı Seçimi (Seçilebilir Modern Kartlar)
        TextView modeTitle = createSectionTitle("Nasıl Çalışsın?", density);
        card.addView(modeTitle);

        int currentMode = prefs.getInt(SunroofAutomationEngine.KEY_MODE, SunroofAutomationEngine.MODE_SMART_COMFORT);

        cardModeSmart = createModeSelectableCard(
                "🌟 Akıllı Güneş Koruması (Önerilen)",
                "Yakıcı dik güneşte kapatır; bulutlu havada, gölgede, akşam ve gece açık tutar.",
                SunroofAutomationEngine.MODE_SMART_COMFORT,
                currentMode == SunroofAutomationEngine.MODE_SMART_COMFORT,
                density);

        cardModeShadeOnly = createModeSelectableCard(
                "☁️ Sadece Gölgede Aç",
                "Güneş ışığından çok rahatsız olanlar için; en ufak güneşte kapatır, gölgede açık tutar.",
                SunroofAutomationEngine.MODE_SHADE_ONLY,
                currentMode == SunroofAutomationEngine.MODE_SHADE_ONLY,
                density);

        cardModeOpenAir = createModeSelectableCard(
                "🌤️ Açık Hava Keyfi",
                "Tavanın sürekli açık kalmasını sevenler için; sadece aşırı yakıcı güneşte kapatır.",
                SunroofAutomationEngine.MODE_OPEN_AIR,
                currentMode == SunroofAutomationEngine.MODE_OPEN_AIR,
                density);

        card.addView(cardModeSmart);
        card.addView(cardModeShadeOnly);
        card.addView(cardModeOpenAir);
        cardModeAlwaysOpen = createModeSelectableCard(
                "\u267E\uFE0F Her Sürüşte Daima Aç (Işıktan Bağımsız)",
                "Işık seviyesine bakılmaksızın; araç her çalıştığında otomatik açar ve açık tutar.",
                SunroofAutomationEngine.MODE_ALWAYS_OPEN,
                currentMode == SunroofAutomationEngine.MODE_ALWAYS_OPEN,
                density);
        card.addView(cardModeAlwaysOpen);

        // 3. Güneş Hassasiyeti Butonları
        sensSectionContainer = new LinearLayout(context);
        sensSectionContainer.setOrientation(LinearLayout.VERTICAL);
        TextView sensTitle = createSectionTitle("Güneş Hassasiyeti", density);
        sensTitle.setPadding(0, dp(14, density), 0, dp(6, density));
        sensSectionContainer.addView(sensTitle);

        int currentSens = prefs.getInt(SunroofAutomationEngine.KEY_SENSITIVITY, SunroofAutomationEngine.SENSITIVITY_BALANCED);

        LinearLayout sensRow = new LinearLayout(context);
        sensRow.setOrientation(LinearLayout.HORIZONTAL);

        btnSensSensitive = createSensitivityButton("🌿 Hassas\n(Erken Kapat)", SunroofAutomationEngine.SENSITIVITY_SENSITIVE, currentSens, density);
        btnSensBalanced = createSensitivityButton("⚖️ Dengeli\n(Tavsiye Edilen)", SunroofAutomationEngine.SENSITIVITY_BALANCED, currentSens, density);
        btnSensBroad = createSensitivityButton("🕶️ Geniş\n(Geç Kapat)", SunroofAutomationEngine.SENSITIVITY_BROAD, currentSens, density);

        sensRow.addView(btnSensSensitive, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        sensRow.addView(btnSensBalanced, createMarginLayoutParams(dp(6, density), 0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        sensRow.addView(btnSensBroad, createMarginLayoutParams(dp(6, density), 0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        sensSectionContainer.addView(sensRow);
        sensSectionContainer.setVisibility(currentMode == SunroofAutomationEngine.MODE_ALWAYS_OPEN ? View.GONE : View.VISIBLE);
        card.addView(sensSectionContainer);

        return card;
    }

    /**
     * Hızlı Kaydırma Kumandası Kartı: her işlem için kullanıcı bir kaydırma hareketi seçer.
     * Aynı hareket bölünmüş ekran, harita kaydırma veya başka bir işlemde kullanılıyorsa uyarılır.
     */
    private View buildQuickControlsCard(float density) {
        LinearLayout card = createPanelCard(density);

        TextView title = createSectionTitle("Hızlı Kaydırma Kumandası", density);
        card.addView(title);

        TextView subtitle = new TextView(context);
        subtitle.setText("Her işlem için bir kaydırma hareketi seçin. Seçtiğiniz hareketi MapControl ekranında yaptığınızda tavan ve perde çalışır. Kutuya dokunarak işlemi deneyebilirsiniz.");
        subtitle.setTextSize(11);
        subtitle.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
        subtitle.setPadding(0, 0, 0, dp(6, density));
        card.addView(subtitle);

        gestureGrid = new LinearLayout(context);
        gestureGrid.setOrientation(LinearLayout.VERTICAL);
        card.addView(gestureGrid);
        populateGestureGrid(density);

        TextView tvInfo = new TextView(context);
        tvInfo.setText("⚠️ Bir kaydırma yalnızca tek özelliğe atanabilir. Bölünmüş ekran veya harita kaydırmada kullanılan bir hareketi seçerseniz uyarı alırsınız.");
        tvInfo.setTextSize(10);
        tvInfo.setTextColor(0xFF38BDF8);
        tvInfo.setPadding(dp(2, density), dp(6, density), 0, 0);
        card.addView(tvInfo);

        return card;
    }

    private void populateGestureGrid(float density) {
        if (gestureGrid == null) {
            return;
        }
        gestureGrid.removeAllViews();
        gestureGrid.addView(buildGestureRow(density,
                GestureAction.SWIPE_LEFT, GestureAction.SWIPE_RIGHT, dp(6, density)));
        gestureGrid.addView(buildGestureRow(density,
                GestureAction.SWIPE_UP, GestureAction.SWIPE_DOWN, 0));
    }

    private View buildScopeSelector(float density) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundResource(R.drawable.bg_segment_track);
        int pad = dp(4, density);
        row.setPadding(pad, pad, pad, pad);

        int currentScope = prefs.getInt(SunroofAutomationEngine.KEY_TARGET_SCOPE, SunroofAutomationEngine.TARGET_SUNROOF_AND_SHADE);

        btnScopeShadeOnly = new TextView(context);
        btnScopeShadeOnly.setText("\uD83E\uDE9F Yaln\u0131zca Perde");
        btnScopeShadeOnly.setTextSize(12);
        btnScopeShadeOnly.setGravity(Gravity.CENTER);
        btnScopeShadeOnly.setPadding(dp(12, density), dp(8, density), dp(12, density), dp(8, density));

        btnScopeSunroofAndShade = new TextView(context);
        btnScopeSunroofAndShade.setText("\u2728 Tavan + Perde");
        btnScopeSunroofAndShade.setTextSize(12);
        btnScopeSunroofAndShade.setGravity(Gravity.CENTER);
        btnScopeSunroofAndShade.setPadding(dp(12, density), dp(8, density), dp(12, density), dp(8, density));

        updateScopeButtonStyles(currentScope, density);

        btnScopeShadeOnly.setOnClickListener(v -> {
            prefs.edit().putInt(SunroofAutomationEngine.KEY_TARGET_SCOPE, SunroofAutomationEngine.TARGET_SHADE_ONLY).apply();
            updateScopeButtonStyles(SunroofAutomationEngine.TARGET_SHADE_ONLY, density);
            callback.log("Otomasyon kapsam\u0131 se\u00e7ildi: Yaln\u0131zca Perde (Cam tavan a\u00e7\u0131lmaz)");
        });

        btnScopeSunroofAndShade.setOnClickListener(v -> {
            prefs.edit().putInt(SunroofAutomationEngine.KEY_TARGET_SCOPE, SunroofAutomationEngine.TARGET_SUNROOF_AND_SHADE).apply();
            updateScopeButtonStyles(SunroofAutomationEngine.TARGET_SUNROOF_AND_SHADE, density);
            callback.log("Otomasyon kapsam\u0131 se\u00e7ildi: Tavan + Perde (Tam Otomasyon)");
        });

        row.addView(btnScopeShadeOnly, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(btnScopeSunroofAndShade, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private void updateScopeButtonStyles(int selectedScope, float density) {
        boolean isShadeOnly = (selectedScope == SunroofAutomationEngine.TARGET_SHADE_ONLY);
        styleScopeButton(btnScopeShadeOnly, isShadeOnly, density);
        styleScopeButton(btnScopeSunroofAndShade, !isShadeOnly, density);
    }

    private void styleScopeButton(TextView btn, boolean selected, float density) {
        if (btn == null) return;
        if (selected) {
            btn.setBackgroundResource(R.drawable.bg_segment_thumb);
            btn.setTextColor(UiStyles.color(context, R.color.textPrimary));
            btn.setTypeface(null, Typeface.BOLD);
        } else {
            btn.setBackgroundColor(Color.TRANSPARENT);
            btn.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
            btn.setTypeface(null, Typeface.NORMAL);
        }
    }

    private LinearLayout buildGestureRow(float density, GestureAction first, GestureAction second, int bottomMargin) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = bottomMargin;
        row.setLayoutParams(rowLp);

        LinearLayout.LayoutParams firstLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        firstLp.rightMargin = dp(6, density);
        row.addView(buildGestureCell(first, density), firstLp);
        row.addView(buildGestureCell(second, density), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private View buildGestureCell(GestureAction action, float density) {
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.VERTICAL);

        int code = SunroofGestureStore.getCode(context, action);
        boolean conflicted = code != SunroofGestureStore.GESTURE_OFF
                && GestureConflicts.findOwnerLabel(context, code, GestureConflicts.slotOf(action)) != 0;
        View pill = createOptionAPill(gestureArrow(code),
                conflicted ? gestureLabel(code) + " ⚠" : gestureLabel(code),
                actionTitle(action), actionColor(action), density,
                () -> gestureManager.handleGestureAction(action, "Hızlı Buton"));
        cell.addView(pill);

        TextView change = new TextView(context);
        change.setText("✎ " + context.getString(R.string.sunroof_gesture_change));
        change.setTextSize(11);
        change.setTextColor(0xFF38BDF8);
        change.setClickable(true);
        change.setFocusable(true);
        change.setPadding(dp(4, density), dp(6, density), dp(4, density), dp(2, density));
        change.setOnClickListener(v -> showGesturePicker(action, density));
        cell.addView(change);
        return cell;
    }

    private void showGesturePicker(GestureAction action, float density) {
        final List<Integer> codes = new ArrayList<>();
        codes.add(SunroofGestureStore.GESTURE_OFF);
        for (int fingers : SplitScreenGesture.FINGER_COUNTS) {
            for (int direction : SplitScreenGesture.DIRECTIONS) {
                codes.add(SplitScreenGesture.code(fingers, direction));
            }
        }

        GestureConflicts.Slot slot = GestureConflicts.slotOf(action);
        int current = SunroofGestureStore.getCode(context, action);
        CharSequence[] labels = new CharSequence[codes.size()];
        for (int i = 0; i < codes.size(); i++) {
            int code = codes.get(i);
            StringBuilder label = new StringBuilder();
            if (code == current) {
                label.append("✓ ");
            }
            label.append(code == SunroofGestureStore.GESTURE_OFF
                    ? context.getString(R.string.split_gesture_off)
                    : gestureLabel(code));
            if (code != SunroofGestureStore.GESTURE_OFF) {
                int owner = GestureConflicts.findOwnerLabel(context, code, slot);
                if (owner != 0) {
                    label.append("  ⚠ ").append(context.getString(
                            R.string.gesture_in_use_by, context.getString(owner)));
                }
            }
            labels[i] = label.toString();
        }

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.sunroof_gesture_pick_title, actionTitle(action)))
                .setItems(labels, (dialog, which) -> applyGesture(action, codes.get(which), density))
                .setNegativeButton(R.string.wifi_cancel, null)
                .show();
    }

    private void applyGesture(GestureAction action, int code, float density) {
        if (code != SunroofGestureStore.GESTURE_OFF) {
            int owner = GestureConflicts.findOwnerLabel(context, code, GestureConflicts.slotOf(action));
            if (owner != 0) {
                DialogHelper.showGestureConflict(context, owner);
                return;
            }
        }
        SunroofGestureStore.setCode(context, action, code);
        callback.log(code == SunroofGestureStore.GESTURE_OFF
                ? actionTitle(action) + " hareketi kapatıldı"
                : actionTitle(action) + " hareketi: " + gestureLabel(code));
        populateGestureGrid(density);
    }

    private String gestureLabel(int code) {
        if (!SplitScreenGesture.isValid(code)) {
            return "Hareket kapalı";
        }
        return context.getString(R.string.split_gesture_name,
                SplitScreenGesture.fingersOf(code),
                directionLabel(SplitScreenGesture.directionOf(code)));
    }

    private String directionLabel(int direction) {
        switch (direction) {
            case SplitScreenGesture.LEFT:
                return context.getString(R.string.split_dir_left);
            case SplitScreenGesture.RIGHT:
                return context.getString(R.string.split_dir_right);
            case SplitScreenGesture.DOWN:
                return context.getString(R.string.split_dir_down);
            case SplitScreenGesture.UP:
            default:
                return context.getString(R.string.split_dir_up);
        }
    }

    private static String gestureArrow(int code) {
        if (!SplitScreenGesture.isValid(code)) {
            return "🚫";
        }
        switch (SplitScreenGesture.directionOf(code)) {
            case SplitScreenGesture.LEFT:
                return "⬅️";
            case SplitScreenGesture.RIGHT:
                return "➡️";
            case SplitScreenGesture.DOWN:
                return "⬇️";
            case SplitScreenGesture.UP:
            default:
                return "⬆️";
        }
    }

    private static String actionTitle(GestureAction action) {
        switch (action) {
            case SWIPE_LEFT:
                return "Tavanı Kapat";
            case SWIPE_RIGHT:
                return "Tavanı Aç";
            case SWIPE_UP:
                return "Perdeyi Kapat";
            case SWIPE_DOWN:
            default:
                return "Perdeyi Aç";
        }
    }

    private static int actionColor(GestureAction action) {
        switch (action) {
            case SWIPE_LEFT:
                return 0xFFEF4444;
            case SWIPE_RIGHT:
                return 0xFF22C55E;
            case SWIPE_UP:
                return 0xFF8B5CF6;
            case SWIPE_DOWN:
            default:
                return 0xFFEAB308;
        }
    }

    private View createOptionAPill(String arrow, String subtext, String actionTitle, int color, float density, Runnable action) {
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(10, density), dp(8, density), dp(10, density), dp(8, density));
        pill.setClickable(true);
        pill.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x18000000 | (color & 0x00FFFFFF));
        bg.setCornerRadius(dp(10, density));
        bg.setStroke(dp(1, density), 0x33000000 | (color & 0x00FFFFFF));
        pill.setBackground(bg);

        TextView tvArrow = new TextView(context);
        tvArrow.setText(arrow);
        tvArrow.setTextSize(15);
        tvArrow.setPadding(0, 0, dp(8, density), 0);
        pill.addView(tvArrow);

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvSub = new TextView(context);
        tvSub.setText(subtext);
        tvSub.setTextSize(10);
        tvSub.setTextColor(0xCC000000 | (color & 0x00FFFFFF));
        tvSub.setTypeface(null, Typeface.BOLD);
        textCol.addView(tvSub);

        TextView tvTitle = new TextView(context);
        tvTitle.setText(actionTitle);
        tvTitle.setTextSize(12);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(0xFFFFFFFF);
        textCol.addView(tvTitle);

        pill.addView(textCol, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        if (action != null) {
            pill.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                action.run();
            });
        }
        return pill;
    }

    /**
     * Güvenlik Ayarları Kartı.
     */
    private View buildSafetyCard(float density) {
        LinearLayout card = createPanelCard(density);

        TextView title = createSectionTitle("Emniyet ve Entegrasyon", density);
        card.addView(title);

        switchRainGuard = createSwitchRow(
                card,
                "Yağmur Emniyeti",
                "Yağmur algılandığında tavanı otomatik kapatır; gerektiğinde manuel açılabilir.",
                SunroofAutomationEngine.KEY_RAIN_GUARD,
                true,
                density);

        switchSpeedGuard = createSwitchRow(
                card,
                "Yüksek Hız Koruması",
                "100 km/s üzerinde rüzgar uğultusu ve mekanik hasarı önlemek için tavanı kapatır.",
                SunroofAutomationEngine.KEY_SPEED_GUARD,
                true,
                density);

        switchCloseAtNight = createSwitchRow(
                card,
                "Gece / Karanlıkta Kapat",
                "Hava karardığında tavanı kapatır. Kapalıyken (varsayılan) gece de açık kalır; sadece güneşte kapanır.",
                SunroofAutomationEngine.KEY_CLOSE_AT_NIGHT,
                false,
                density);

        return card;
    }

    private LinearLayout createModeSelectableCard(String title, String desc, int modeId, boolean selected, float density) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14, density), dp(10, density), dp(14, density), dp(10, density));

        updateModeCardStyle(card, selected, density);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6, density);
        card.setLayoutParams(lp);

        TextView tvTitle = new TextView(context);
        tvTitle.setText(title);
        tvTitle.setTextSize(14);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(selected ? UiStyles.color(context, R.color.oemAccent) : UiStyles.color(context, R.color.textPrimary));
        card.addView(tvTitle);

        TextView tvDesc = new TextView(context);
        tvDesc.setText(desc);
        tvDesc.setTextSize(12);
        tvDesc.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
        tvDesc.setPadding(0, dp(2, density), 0, 0);
        card.addView(tvDesc);

        card.setOnClickListener(v -> {
            prefs.edit().putInt(SunroofAutomationEngine.KEY_MODE, modeId).apply();
            updateModeCardSelection(modeId, density);
            callback.log("Çalışma mantığı seçildi: " + title);
        });

        return card;
    }

    private void updateModeCardSelection(int selectedMode, float density) {
        updateModeCardStyle(cardModeSmart, selectedMode == SunroofAutomationEngine.MODE_SMART_COMFORT, density);
        updateModeCardStyle(cardModeShadeOnly, selectedMode == SunroofAutomationEngine.MODE_SHADE_ONLY, density);
        updateModeCardStyle(cardModeOpenAir, selectedMode == SunroofAutomationEngine.MODE_OPEN_AIR, density);

        updateModeCardTitleColor(cardModeSmart, selectedMode == SunroofAutomationEngine.MODE_SMART_COMFORT);
        updateModeCardTitleColor(cardModeShadeOnly, selectedMode == SunroofAutomationEngine.MODE_SHADE_ONLY);
        updateModeCardTitleColor(cardModeOpenAir, selectedMode == SunroofAutomationEngine.MODE_OPEN_AIR);
        updateModeCardStyle(cardModeAlwaysOpen, selectedMode == SunroofAutomationEngine.MODE_ALWAYS_OPEN, density);
        updateModeCardTitleColor(cardModeAlwaysOpen, selectedMode == SunroofAutomationEngine.MODE_ALWAYS_OPEN);
        if (sensSectionContainer != null) {
            sensSectionContainer.setVisibility(selectedMode == SunroofAutomationEngine.MODE_ALWAYS_OPEN ? View.GONE : View.VISIBLE);
        }
    }

    private void updateModeCardStyle(LinearLayout card, boolean selected, float density) {
        if (card == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12, density));
        if (selected) {
            bg.setColor(UiStyles.color(context, R.color.surfaceCardInner));
            bg.setStroke(dp(2, density), UiStyles.color(context, R.color.oemAccent));
        } else {
            bg.setColor(UiStyles.color(context, R.color.surfaceCardInner));
            bg.setStroke(dp(1, density), UiStyles.color(context, R.color.automationCardStroke));
        }
        card.setBackground(bg);
    }

    private void updateModeCardTitleColor(LinearLayout card, boolean selected) {
        if (card != null && card.getChildCount() > 0 && card.getChildAt(0) instanceof TextView) {
            TextView title = (TextView) card.getChildAt(0);
            title.setTextColor(selected ? UiStyles.color(context, R.color.oemAccent) : UiStyles.color(context, R.color.textPrimary));
        }
    }

    @Override
    public void onSunroofStatusChanged(SunroofAutomationEngine.Status status) {
        uiHandler.post(() -> {
            if (status == null) {
                return;
            }
            if (tvConditionTitle != null) {
                tvConditionTitle.setText(status.conditionTitle);
            }
            if (tvLiveLux != null) {
                if (status.currentLux < 0) {
                    tvLiveLux.setText("Ölçülen Işık: Bekleniyor...");
                } else {
                    String src = status.luxSource != null && !status.luxSource.isEmpty() ? " (" + status.luxSource + ")" : "";
                    if (status.averageLux > 0 && Math.abs(status.currentLux - status.averageLux) > 2500f) {
                        tvLiveLux.setText(String.format(Locale.getDefault(), "Ölçülen Işık: %,d lx (Ort: %,d lx)%s",
                                (int) status.currentLux, (int) status.averageLux, src));
                    } else {
                        tvLiveLux.setText(String.format(Locale.getDefault(), "Ölçülen Işık: %,d lx%s", (int) status.currentLux, src));
                    }
                }
            }
            if (tvConditionAdvice != null) {
                tvConditionAdvice.setText(status.conditionAdvice);
            }

            // Tavan Durumu
            if (tvSunroofState != null) {
                if (status.sunroofOpen == null) {
                    tvSunroofState.setText(status.connected ? "Hareketli / Bilinmiyor" : "Bağlı Değil");
                    tvSunroofState.setTextColor(0xFFFBBF24); // Sarı
                } else if (status.sunroofOpen) {
                    tvSunroofState.setText("Açık 🪟");
                    tvSunroofState.setTextColor(0xFF22C55E); // Yeşil
                } else {
                    tvSunroofState.setText("Kapalı 🔒");
                    tvSunroofState.setTextColor(0xFF38BDF8); // Cyan
                }
            }

            // Yağmur
            if (tvRainState != null) {
                if (status.isRaining) {
                    tvRainState.setText("🌧️ Yağmur Algılandı!");
                    tvRainState.setTextColor(0xFFEF4444); // Kırmızı
                } else {
                    tvRainState.setText("Kuru ☀️");
                    tvRainState.setTextColor(0xFF22C55E); // Yeşil
                }
            }

            // Hız
            if (tvSpeed != null) {
                tvSpeed.setText(status.vehicleSpeed + " km/s");
            }

            // Alt Bildirim
            if (tvStatusMessage != null) {
                StringBuilder msg = new StringBuilder();
                if (status.pendingAction != null) {
                    msg.append("⏳ ").append(status.pendingAction).append(" | ");
                }
                msg.append(status.lastAction);
                if (status.cooldownRemainingSec > 0) {
                    msg.append(" (Bekleme: ").append(status.cooldownRemainingSec).append(" sn)");
                }
                tvStatusMessage.setText(msg.toString());
            }
        });
    }

    // --- UI Yardımcı Metotları ---

    private LinearLayout createPanelCard(float density) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16, density), dp(14, density), dp(16, density), dp(14, density));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiStyles.color(context, R.color.automationPanel));
        bg.setCornerRadius(dp(18, density));
        bg.setStroke(dp(1, density), UiStyles.color(context, R.color.automationCardStroke));
        card.setBackground(bg);
        card.setElevation(dp(2, density));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12, density);
        card.setLayoutParams(lp);
        return card;
    }

    private TextView createSectionTitle(String text, float density) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(UiStyles.color(context, R.color.textPrimary));
        tv.setPadding(0, 0, 0, dp(8, density));
        return tv;
    }

    private TextView createStatusBadgeRow(LinearLayout parent, String label, String initialValue, float density) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4, density), 0, dp(4, density));

        TextView tvLabel = new TextView(context);
        tvLabel.setText(label + ": ");
        tvLabel.setTextSize(13);
        tvLabel.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
        row.addView(tvLabel);

        TextView tvVal = new TextView(context);
        tvVal.setText(initialValue);
        tvVal.setTextSize(13);
        tvVal.setTypeface(null, Typeface.BOLD);
        tvVal.setTextColor(UiStyles.color(context, R.color.textPrimary));
        row.addView(tvVal);

        parent.addView(row);
        return tvVal;
    }

    private Button createSensitivityButton(String label, int sensLevel, int currentSens, float density) {
        Button btn = new Button(context);
        btn.setText(label);
        btn.setTextSize(11);
        btn.setPadding(dp(4, density), dp(8, density), dp(4, density), dp(8, density));
        updateSensitivityButtonStyle(btn, sensLevel == currentSens, density);

        btn.setOnClickListener(v -> {
            prefs.edit().putInt(SunroofAutomationEngine.KEY_SENSITIVITY, sensLevel).apply();
            updateSensitivityButtonStyle(btnSensSensitive, sensLevel == SunroofAutomationEngine.SENSITIVITY_SENSITIVE, density);
            updateSensitivityButtonStyle(btnSensBalanced, sensLevel == SunroofAutomationEngine.SENSITIVITY_BALANCED, density);
            updateSensitivityButtonStyle(btnSensBroad, sensLevel == SunroofAutomationEngine.SENSITIVITY_BROAD, density);
            callback.log("Güneş hassasiyeti değiştirildi: " + label.replace("\n", " "));
        });
        return btn;
    }

    private void updateSensitivityButtonStyle(Button btn, boolean selected, float density) {
        if (btn == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10, density));
        if (selected) {
            bg.setColor(UiStyles.color(context, R.color.oemAccent));
            bg.setStroke(0, Color.TRANSPARENT);
            btn.setTextColor(Color.WHITE);
            btn.setTypeface(null, Typeface.BOLD);
        } else {
            bg.setColor(UiStyles.color(context, R.color.surfaceCardInner));
            bg.setStroke(dp(1, density), UiStyles.color(context, R.color.automationCardStroke));
            btn.setTextColor(UiStyles.color(context, R.color.textPrimary));
            btn.setTypeface(null, Typeface.NORMAL);
        }
        btn.setBackground(bg);
    }

    private Button createStyledActionButton(String text, int color, float density, View.OnClickListener onClick) {
        Button btn = new Button(context);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(12);
        btn.setTypeface(null, Typeface.BOLD);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(10, density));
        btn.setBackground(bg);
        btn.setOnClickListener(onClick);
        return btn;
    }

    private Switch createSwitchRow(LinearLayout parent, String title, String desc,
                                   String prefKey, boolean defaultVal, float density) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6, density), 0, dp(6, density));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(context);
        tvTitle.setText(title);
        tvTitle.setTextSize(14);
        tvTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        textCol.addView(tvTitle);

        if (desc != null && !desc.isEmpty()) {
            TextView tvDesc = new TextView(context);
            tvDesc.setText(desc);
            tvDesc.setTextSize(12);
            tvDesc.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
            textCol.addView(tvDesc);
        }

        row.addView(textCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(context);
        sw.setChecked(prefs.getBoolean(prefKey, defaultVal));
        sw.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean(prefKey, isChecked).apply();
            callback.log(title + ": " + (isChecked ? "Açık" : "Kapalı"));
        });
        row.addView(sw);
        parent.addView(row);
        return sw;
    }

    private LinearLayout.LayoutParams createMarginLayoutParams(int leftMargin, int rightMargin, int height, float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, height, weight);
        lp.leftMargin = leftMargin;
        lp.rightMargin = rightMargin;
        return lp;
    }

    private int dp(int value, float density) {
        return Math.round(value * density);
    }
}
