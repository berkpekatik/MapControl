package com.mapcontrol.ui.builder;
import android.graphics.Color;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;

public class AssistTabBuilder {
    public interface AssistCallback {
        void onSettingChanged(String settingKey, int newValue);
        /** Dialog onayından sonra UI + araç gönderimi için çalıştırılır */
        void onSafetyWarningRequired(String settingKey, int newValue, Runnable onUserConfirmed);
        void log(String message);
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final AssistCallback callback;

    public AssistTabBuilder(Context context, SharedPreferences prefs, AssistCallback callback) {
        this.context = context;
        this.prefs = prefs;
        this.callback = callback;
    }

    public ScrollView build() {
        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        scrollView.setFillViewport(false);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Color.TRANSPARENT);

        float densityTitle = context.getResources().getDisplayMetrics().density;
        LinearLayout assistTitleRow = new LinearLayout(context);
        assistTitleRow.setOrientation(LinearLayout.HORIZONTAL);
        assistTitleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        assistTitleRow.setPadding(16, 24, 16, 8);

        AppCompatImageView assistTitleIcon = new AppCompatImageView(context);
        assistTitleIcon.setImageResource(R.drawable.ic_mdi_car);
        assistTitleIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int titleIconPx = (int) (28 * densityTitle);
        assistTitleIcon.setLayoutParams(new LinearLayout.LayoutParams(titleIconPx, titleIconPx));
        assistTitleIcon.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.textPrimary)));
        assistTitleRow.addView(assistTitleIcon);

        TextView assistTitle = new TextView(context);
        assistTitle.setText("Araç ve Sürücü Yardımları");
        assistTitle.setTextSize(20);
        assistTitle.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        assistTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMargins((int) (12 * densityTitle), 0, 0, 0);
        assistTitleRow.addView(assistTitle, titleLp);

        container.addView(assistTitleRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView assistSubtitle = new TextView(context);
        assistSubtitle.setText("Sürüş güvenliği ve konfor ayarları");
        assistSubtitle.setTextSize(14);
        assistSubtitle.setTextColor(ContextCompat.getColor(context, R.color.textSecondaryCool));
        assistSubtitle.setPadding(16, 0, 16, 16);
        container.addView(assistSubtitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        GridLayout assistGrid = new GridLayout(context);
        assistGrid.setColumnCount(2);
        assistGrid.setPadding(16, 0, 16, 16);
        assistGrid.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        gridParams.setMargins(0, 0, 0, 16);

        Object[][] assistCards = {
                {"ISS (Start-Stop)", R.drawable.ic_mdi_restart, 0, -1, "issSetting", "ISS"},
                {"Hız Limitleyici", R.drawable.ic_mdi_speedometer, 2, -1, "spdLimitSetting", "Hız Limitleyici"},
                {"Şerit Takip Uyarısı", R.drawable.ic_mdi_road_variant, 2, -1, "ldwSetting", "LDW"},
                {"Şeritten Kaçınma (LDP)", R.drawable.ic_mdi_shield_car, 2, -1, "ldpSetting", "LDP"},
                {"Ön Çarpışma Uyarısı", R.drawable.ic_mdi_car_brake_alert, 2, -1, "fcwSetting", "FCW"},
                {"Aktif Acil Fren", R.drawable.ic_mdi_car_brake_abs, 2, -1, "aebSetting", "AEB"}
        };

        int[] savedValues = {
                prefs.getInt("issSetting", -1),
                prefs.getInt("spdLimitSetting", -1),
                prefs.getInt("ldwSetting", -1),
                prefs.getInt("ldpSetting", -1),
                prefs.getInt("fcwSetting", -1),
                prefs.getInt("aebSetting", -1)
        };

        final LinearLayout[] cardContainers = new LinearLayout[assistCards.length];
        final AppCompatImageView[] iconViews = new AppCompatImageView[assistCards.length];
        final TextView[] titleViews = new TextView[assistCards.length];
        final TextView[] statusViews = new TextView[assistCards.length];
        final TextView[] onChips = new TextView[assistCards.length];
        final FrameLayout[] cardFrames = new FrameLayout[assistCards.length];

        float density = context.getResources().getDisplayMetrics().density;

        for (int i = 0; i < assistCards.length; i++) {
            final int cardIndex = i;
            final String cardTitle = (String) assistCards[i][0];
            final int cardIconRes = (Integer) assistCards[i][1];
            final int activeValue = (Integer) assistCards[i][2];
            final int passiveValue = (Integer) assistCards[i][3];
            final String settingKey = (String) assistCards[i][4];
            final String logPrefix = (String) assistCards[i][5];

            FrameLayout cardFrame = new FrameLayout(context);
            cardFrames[cardIndex] = cardFrame;

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(android.view.Gravity.CENTER);
            card.setPadding((int) (16 * density), (int) (16 * density), (int) (16 * density), (int) (16 * density));
            card.setMinimumHeight((int) (130 * density));
            card.setClickable(true);
            card.setFocusable(true);
            cardContainers[cardIndex] = card;

            final boolean[] isActiveRef = {savedValues[cardIndex] == activeValue};

            android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
            if (isActiveRef[0]) {
                cardBg.setColor(ContextCompat.getColor(context, R.color.assistCardActive));
                cardBg.setCornerRadius(16 * density);
                cardBg.setStroke((int) (2 * density), ContextCompat.getColor(context, R.color.assistStrokeActive));
            } else {
                cardBg.setColor(ContextCompat.getColor(context, R.color.assistCardIdle));
                cardBg.setCornerRadius(16 * density);
                cardBg.setStroke((int) (1 * density), ContextCompat.getColor(context, R.color.outline));
            }
            card.setBackground(cardBg);

            AppCompatImageView iconView = new AppCompatImageView(context);
            iconView.setImageResource(cardIconRes);
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int iconPx = (int) (36 * density);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconPx, iconPx);
            iconLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            iconView.setLayoutParams(iconLp);
            iconView.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context,
                    isActiveRef[0] ? R.color.textPrimary : R.color.textPrimary70)));
            iconViews[cardIndex] = iconView;
            card.addView(iconView);

            TextView titleView = new TextView(context);
            titleView.setText(cardTitle);
            titleView.setTextSize(15);
            titleView.setGravity(android.view.Gravity.CENTER);
            titleView.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
            titleView.setTypeface(null, isActiveRef[0] ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            titleView.setPadding(0, (int) (8 * density), 0, (int) (4 * density));
            titleViews[cardIndex] = titleView;
            card.addView(titleView);

            TextView statusView = new TextView(context);
            statusView.setText(isActiveRef[0] ? getStatusText(settingKey) : "Ayarlanmadı");
            statusView.setTextSize(12);
            statusView.setGravity(android.view.Gravity.CENTER);
            statusView.setTextColor(ContextCompat.getColor(context, R.color.textSecondaryCool));
            statusViews[cardIndex] = statusView;
            card.addView(statusView);

            TextView onChip = new TextView(context);
            onChip.setText("ON");
            onChip.setTextSize(10);
            onChip.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
            onChip.setTypeface(null, android.graphics.Typeface.BOLD);
            onChip.setBackgroundColor(ContextCompat.getColor(context, R.color.chipOn));
            onChip.setPadding((int) (8 * density), (int) (2 * density), (int) (8 * density), (int) (2 * density));
            onChip.setVisibility(isActiveRef[0] ? android.view.View.VISIBLE : android.view.View.GONE);
            FrameLayout.LayoutParams chipParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            chipParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
            chipParams.setMargins(0, (int) (8 * density), (int) (8 * density), 0);
            cardFrame.addView(onChip, chipParams);
            onChips[cardIndex] = onChip;

            cardFrame.addView(card);

            GridLayout.LayoutParams cardParams = new GridLayout.LayoutParams();
            cardParams.width = 0;
            cardParams.height = GridLayout.LayoutParams.WRAP_CONTENT;
            cardParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            cardParams.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            cardParams.setMargins((int) (8 * density), (int) (8 * density), (int) (8 * density), (int) (8 * density));
            assistGrid.addView(cardFrame, cardParams);

            card.setOnClickListener(v -> {
                boolean newActive = !isActiveRef[0];
                int newValue = newActive ? activeValue : passiveValue;

                if ((settingKey.equals("fcwSetting") || settingKey.equals("aebSetting")) && newActive) {
                    callback.onSafetyWarningRequired(settingKey, newValue, () -> {
                        prefs.edit().putInt(settingKey, newValue).apply();
                        callback.onSettingChanged(settingKey, newValue);
                        savedValues[cardIndex] = newValue;
                        callback.log(logPrefix + ": Aktif");
                        trySendCarInfoValue(settingKey, true, logPrefix);
                        isActiveRef[0] = true;
                        updateCardUI(cardIndex, true, settingKey, cardContainers, iconViews, titleViews, statusViews, onChips, density);
                    });
                    return;
                }

                prefs.edit().putInt(settingKey, newValue).apply();
                callback.onSettingChanged(settingKey, newValue);
                savedValues[cardIndex] = newValue;

                if (newActive) {
                    callback.log(logPrefix + ": Aktif");
                    trySendCarInfoValue(settingKey, true, logPrefix);
                } else {
                    callback.log(logPrefix + ": Pasif (Hiçbiri)");
                    trySendCarInfoValue(settingKey, false, logPrefix);
                }

                isActiveRef[0] = newActive;
                updateCardUI(cardIndex, isActiveRef[0], settingKey, cardContainers, iconViews, titleViews, statusViews, onChips, density);
            });
        }

        container.addView(assistGrid, gridParams);
        scrollView.addView(container, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        return scrollView;
    }

    private void updateCardUI(int cardIndex, boolean isActive, String settingKey,
                              LinearLayout[] cardContainers, AppCompatImageView[] iconViews, TextView[] titleViews,
                              TextView[] statusViews, TextView[] onChips, float density) {
        android.graphics.drawable.GradientDrawable newBg = new android.graphics.drawable.GradientDrawable();
        if (isActive) {
            newBg.setColor(ContextCompat.getColor(context, R.color.assistCardActive));
            newBg.setCornerRadius(16 * density);
            newBg.setStroke((int) (2 * density), ContextCompat.getColor(context, R.color.assistStrokeActive));
        } else {
            newBg.setColor(ContextCompat.getColor(context, R.color.assistCardIdle));
            newBg.setCornerRadius(16 * density);
            newBg.setStroke((int) (1 * density), ContextCompat.getColor(context, R.color.outline));
        }
        cardContainers[cardIndex].setBackground(newBg);
        iconViews[cardIndex].setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context,
                isActive ? R.color.textPrimary : R.color.textPrimary70)));
        titleViews[cardIndex].setTypeface(null, isActive ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        statusViews[cardIndex].setText(isActive ? getStatusText(settingKey) : "Ayarlanmadı");
        onChips[cardIndex].setVisibility(isActive ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private String getStatusText(String settingKey) {
        switch (settingKey) {
            case "issSetting": return "ISS Kapalı";
            case "spdLimitSetting": return "Uyarı Kapalı";
            case "ldwSetting": return "LDW Kapalı";
            case "ldpSetting": return "LDP Kapalı";
            case "fcwSetting": return "FCW Kapalı";
            case "aebSetting": return "AEB Kapalı";
            default: return "Aktif";
        }
    }

    private void trySendCarInfoValue(String settingKey, boolean disable, String logPrefix) {
        try {
            int currentPowerMode = prefs.getInt("lastPowerMode", -1);
            int[] pwrItems = CarInfoProxy.getInstance().getItemValues(
                    VDEventCarInfo.MODULE_READONLY_INFO,
                    ReadOnlyID.ID_SYSTEM_POWER_MODE);
            if (pwrItems != null && pwrItems.length > 0) {
                currentPowerMode = pwrItems[0];
            }
            callback.log((disable ? "Kapatma" : "Açma") + " Modu PowerMode: " + currentPowerMode);
            if (currentPowerMode == 2 && CarInfoProxy.getInstance().isServiceConnnected()) {
                if (settingKey.equals("issSetting")) {
                    CarInfoProxy.getInstance().sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_ISS, disable ? 0 : 1);
                } else if (settingKey.equals("ldwSetting")) {
                    CarInfoProxy.getInstance().sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_LDW, disable ? 2 : 1);
                } else if (settingKey.equals("ldpSetting")) {
                    int[] vals = new int[]{disable ? 2 : 1};
                    CarInfoProxy.getInstance().sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_FCM_INHIBIT, vals);
                } else if (settingKey.equals("fcwSetting")) {
                    int[] vals = new int[]{disable ? 2 : 1, 1};
                    CarInfoProxy.getInstance().sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, 36, vals);
                } else if (settingKey.equals("aebSetting")) {
                    int[] vals = new int[]{disable ? 2 : 1};
                    CarInfoProxy.getInstance().sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, 37, vals);
                } else if (settingKey.equals("spdLimitSetting")) {
                    int[] vals = disable ? new int[]{2, 2} : new int[]{1, 1};
                    CarInfoProxy.getInstance().sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_SPD_LIMIT_WARN_SET, vals);
                }
                callback.log("✅ " + logPrefix + " değeri araca gönderildi");
            }
        } catch (Exception e) {
            callback.log("❌ CarInfo gönderim hatası: " + e.getMessage());
        }
    }
}
