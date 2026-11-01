package com.mapcontrol.ui.builder;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.service.DmsFaceRecognitionManager;
import com.mapcontrol.ui.activity.DmsFaceProfileActivity;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.vehicle.CarSeatMemoryManager;
import com.mapcontrol.vehicle.FaceProfileStore;

import java.util.List;

public final class DmsFaceRecognitionTabBuilder {
    public interface Logger {
        void log(String message);
    }

    public static final int CAMERA_PERMISSION_REQUEST = 4217;
    public static final String PREF_SEAT_TYPE_CHOSEN = "seat_memory_type_chosen_v1";

    private final Activity activity;
    private final SharedPreferences prefs;
    private final Logger logger;

    private View selectionScreenView;
    private LinearLayout normalContentContainer;
    private TextView activeModeLabel;

    private SwitchCompat enabledSwitch;
    private TextView description;
    private TextView toggleLabel;
    private TextView status;
    private LinearLayout savedProfilesContainer;
    private boolean pendingEnable;
    private boolean suppressSwitchCallback;

    public DmsFaceRecognitionTabBuilder(Activity activity, SharedPreferences prefs, Logger logger) {
        this.activity = activity;
        this.prefs = prefs;
        this.logger = logger;
    }

    public View build() {
        FrameLayout root = new FrameLayout(activity);

        selectionScreenView = buildSelectionScreen();
        normalContentContainer = buildNormalContent();

        root.addView(selectionScreenView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(normalContentContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        boolean chosen = prefs.getBoolean(PREF_SEAT_TYPE_CHOSEN, false);
        selectionScreenView.setVisibility(chosen ? View.GONE : View.VISIBLE);
        normalContentContainer.setVisibility(chosen ? View.VISIBLE : View.GONE);

        refresh();
        return root;
    }

    /**
     * Completely clean standalone screen with only the mode selection cards in the dead center.
     */
    private View buildSelectionScreen() {
        LinearLayout centerWrapper = new LinearLayout(activity);
        centerWrapper.setOrientation(LinearLayout.VERTICAL);
        centerWrapper.setGravity(Gravity.CENTER);
        centerWrapper.setPadding(dp(24), dp(40), dp(24), dp(40));
        centerWrapper.setMinimumHeight(dp(620));

        LinearLayout centerCard = new LinearLayout(activity);
        centerCard.setOrientation(LinearLayout.VERTICAL);
        centerCard.setGravity(Gravity.CENTER_HORIZONTAL);
        centerCard.setPadding(dp(36), dp(36), dp(36), dp(36));
        UiStyles.setGlassCardBackground(centerCard);
        centerWrapper.addView(centerCard, new LinearLayout.LayoutParams(
                dp(680), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(activity);
        title.setText("Koltuk Hafıza Tipi Seçimi");
        UiStyles.setTextSize(title, R.dimen.text_size_large);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(UiStyles.color(activity, R.color.textPrimary));
        title.setGravity(Gravity.CENTER);
        centerCard.addView(title);

        TextView subtitle = new TextView(activity);
        subtitle.setText("Lütfen aracınızın koltuk hafıza donanım tipini seçin:");
        UiStyles.setTextSize(subtitle, R.dimen.text_size_small);
        subtitle.setTextColor(UiStyles.color(activity, R.color.textSecondaryCool));
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(6);
        subLp.bottomMargin = dp(28);
        centerCard.addView(subtitle, subLp);

        // Selection Cards Row
        LinearLayout optionsRow = new LinearLayout(activity);
        optionsRow.setOrientation(LinearLayout.HORIZONTAL);
        optionsRow.setGravity(Gravity.CENTER);
        centerCard.addView(optionsRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Option 1: Multimedya Ekranı Üzerinde
        LinearLayout screenCard = new LinearLayout(activity);
        screenCard.setOrientation(LinearLayout.VERTICAL);
        screenCard.setGravity(Gravity.CENTER);
        screenCard.setPadding(dp(20), dp(24), dp(20), dp(24));
        UiStyles.applySolidRoundedBackgroundDp(screenCard, 0xFF1E293B, 14f);
        screenCard.setClickable(true);
        screenCard.setFocusable(true);

        TextView screenTitle = new TextView(activity);
        screenTitle.setText("Multimedya Ekranı Üzerinde");
        UiStyles.setTextSize(screenTitle, R.dimen.text_size_medium);
        screenTitle.setTypeface(null, Typeface.BOLD);
        screenTitle.setTextColor(0xFFFFFFFF);
        screenTitle.setGravity(Gravity.CENTER);
        screenCard.addView(screenTitle);

        screenCard.setOnClickListener(v -> selectModeAndProceed(CarSeatMemoryManager.MODE_SCREEN));

        LinearLayout.LayoutParams opt1Lp = new LinearLayout.LayoutParams(0, dp(110), 1f);
        opt1Lp.rightMargin = dp(14);
        optionsRow.addView(screenCard, opt1Lp);

        // Option 2: Araç Kapısı Üzerinde
        LinearLayout doorCard = new LinearLayout(activity);
        doorCard.setOrientation(LinearLayout.VERTICAL);
        doorCard.setGravity(Gravity.CENTER);
        doorCard.setPadding(dp(20), dp(24), dp(20), dp(24));
        UiStyles.applySolidRoundedBackgroundDp(doorCard, 0xFF1E293B, 14f);
        doorCard.setClickable(true);
        doorCard.setFocusable(true);

        TextView doorTitle = new TextView(activity);
        doorTitle.setText("Araç Kapısı Üzerinde");
        UiStyles.setTextSize(doorTitle, R.dimen.text_size_medium);
        doorTitle.setTypeface(null, Typeface.BOLD);
        doorTitle.setTextColor(0xFFFFFFFF);
        doorTitle.setGravity(Gravity.CENTER);
        doorCard.addView(doorTitle);

        doorCard.setOnClickListener(v -> selectModeAndProceed(CarSeatMemoryManager.MODE_DOOR));

        LinearLayout.LayoutParams opt2Lp = new LinearLayout.LayoutParams(0, dp(110), 1f);
        optionsRow.addView(doorCard, opt2Lp);

        return centerWrapper;
    }

    private void selectModeAndProceed(int mode) {
        CarSeatMemoryManager.getInstance(activity).setMemoryMode(mode);
        prefs.edit().putBoolean(PREF_SEAT_TYPE_CHOSEN, true).apply();
        if (selectionScreenView != null) {
            selectionScreenView.setVisibility(View.GONE);
        }
        if (normalContentContainer != null) {
            normalContentContainer.setVisibility(View.VISIBLE);
        }
        refresh();
        Toast.makeText(activity, mode == CarSeatMemoryManager.MODE_DOOR
                ? "Araç Kapısı Üzerinde modu seçildi."
                : "Multimedya Ekranı Üzerinde modu seçildi.", Toast.LENGTH_SHORT).show();
    }

    private LinearLayout buildNormalContent() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        content.setPadding(padding, dp(16), padding, dp(24));

        // Top Status Bar: shows active memory mode and a button to switch back to the selection screen
        LinearLayout topBar = new LinearLayout(activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(16), dp(10), dp(16), dp(10));
        UiStyles.applySolidRoundedBackgroundDp(
                topBar, UiStyles.color(activity, R.color.automationCardSurface), 12f);
        LinearLayout.LayoutParams topBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        topBarLp.bottomMargin = dp(14);
        content.addView(topBar, topBarLp);

        activeModeLabel = new TextView(activity);
        UiStyles.setTextSize(activeModeLabel, R.dimen.text_size_small);
        activeModeLabel.setTypeface(null, Typeface.BOLD);
        activeModeLabel.setTextColor(0xFF60A5FA);
        topBar.addView(activeModeLabel, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button changeModeBtn = new Button(activity);
        changeModeBtn.setText("Hafıza Tipini Değiştir");
        changeModeBtn.setAllCaps(false);
        UiStyles.setTextSize(changeModeBtn, R.dimen.text_size_small);
        changeModeBtn.setTextColor(0xFFFFFFFF);
        changeModeBtn.setMinHeight(0);
        changeModeBtn.setMinimumHeight(0);
        changeModeBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        UiStyles.applySolidRoundedBackgroundDp(changeModeBtn, 0xFF334155, 8f);
        changeModeBtn.setOnClickListener(v -> {
            normalContentContainer.setVisibility(View.GONE);
            selectionScreenView.setVisibility(View.VISIBLE);
        });
        topBar.addView(changeModeBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Header Card
        LinearLayout mainCard = new LinearLayout(activity);
        mainCard.setOrientation(LinearLayout.VERTICAL);
        mainCard.setPadding(dp(18), dp(16), dp(18), dp(18));
        UiStyles.setGlassCardBackground(mainCard);
        content.addView(mainCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(activity);
        title.setText(R.string.dms_recognition_title);
        UiStyles.setTextSize(title, R.dimen.text_size_large);
        title.setTextColor(UiStyles.color(activity, R.color.textPrimary));
        title.setTypeface(null, Typeface.BOLD);
        mainCard.addView(title);

        description = new TextView(activity);
        UiStyles.setTextSize(description, R.dimen.text_size_small);
        description.setTextColor(UiStyles.color(activity, R.color.textSecondaryCool));
        LinearLayout.LayoutParams descriptionLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionLp.topMargin = dp(8);
        mainCard.addView(description, descriptionLp);

        LinearLayout toggleRow = new LinearLayout(activity);
        toggleRow.setGravity(Gravity.CENTER_VERTICAL);
        toggleRow.setPadding(dp(14), dp(12), dp(14), dp(12));
        UiStyles.applySolidRoundedBackgroundDp(
                toggleRow, UiStyles.color(activity, R.color.automationCardSurface), 12f);
        LinearLayout.LayoutParams toggleRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        toggleRowLp.topMargin = dp(16);
        mainCard.addView(toggleRow, toggleRowLp);

        toggleLabel = new TextView(activity);
        UiStyles.setTextSize(toggleLabel, R.dimen.text_size_normal);
        toggleLabel.setTypeface(null, Typeface.BOLD);
        toggleLabel.setTextColor(UiStyles.color(activity, R.color.textPrimary));
        toggleRow.addView(toggleLabel, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        applySeatMemoryCopy();

        enabledSwitch = new SwitchCompat(activity);
        enabledSwitch.setChecked(DmsFaceRecognitionManager.isEnabled(activity));
        toggleRow.addView(enabledSwitch);
        enabledSwitch.setOnCheckedChangeListener((button, enabled) -> {
            if (suppressSwitchCallback) {
                return;
            }
            if (enabled) {
                enableRecognition();
            } else {
                pendingEnable = false;
                prefs.edit()
                        .putBoolean(DmsFaceRecognitionManager.PREF_ENABLED, false)
                        .putString(DmsFaceRecognitionManager.PREF_STATUS,
                                activity.getString(R.string.dms_recognition_stopped))
                        .apply();
                DmsFaceRecognitionManager.stop(activity);
                logger.log(activity.getString(R.string.dms_recognition_stopped));
                refresh();
            }
        });

        status = new TextView(activity);
        status.setTextColor(UiStyles.color(activity, R.color.textSecondaryCool));
        UiStyles.setTextSize(status, R.dimen.text_size_small);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(14);
        mainCard.addView(status, statusLp);

        Button profilesButton = new Button(activity);
        profilesButton.setText(R.string.dms_recognition_manage_profiles);
        profilesButton.setAllCaps(false);
        UiStyles.setTextSize(profilesButton, R.dimen.text_size_normal);
        profilesButton.setTypeface(null, Typeface.BOLD);
        profilesButton.setTextColor(0xFFFFFFFF);
        UiStyles.applySolidRoundedBackgroundDp(profilesButton,
                UiStyles.color(activity, R.color.buttonPrimary), 12f);
        profilesButton.setOnClickListener(v ->
                activity.startActivity(new Intent(activity, DmsFaceProfileActivity.class)));
        LinearLayout.LayoutParams profilesLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        profilesLp.topMargin = dp(16);
        mainCard.addView(profilesButton, profilesLp);

        // Saved Profiles Summary Card
        LinearLayout profilesCard = new LinearLayout(activity);
        profilesCard.setOrientation(LinearLayout.VERTICAL);
        profilesCard.setPadding(dp(18), dp(14), dp(18), dp(16));
        UiStyles.setGlassCardBackground(profilesCard);
        LinearLayout.LayoutParams profilesCardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        profilesCardLp.topMargin = dp(14);
        content.addView(profilesCard, profilesCardLp);

        TextView profilesTitle = new TextView(activity);
        profilesTitle.setText(R.string.log_dms_face_profiles);
        UiStyles.setTextSize(profilesTitle, R.dimen.text_size_normal);
        profilesTitle.setTypeface(null, Typeface.BOLD);
        profilesTitle.setTextColor(UiStyles.color(activity, R.color.textPrimary));
        profilesCard.addView(profilesTitle);

        savedProfilesContainer = new LinearLayout(activity);
        savedProfilesContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        containerLp.topMargin = dp(8);
        profilesCard.addView(savedProfilesContainer, containerLp);

        return content;
    }

    public void onCameraPermissionResult(boolean granted) {
        if (!pendingEnable) {
            return;
        }
        pendingEnable = false;
        if (granted) {
            enabledSwitch.setChecked(true);
        } else {
            Toast.makeText(activity, R.string.dms_face_camera_permission, Toast.LENGTH_LONG).show();
        }
    }

    public void refresh() {
        if (status == null) {
            return;
        }
        boolean isDoor = CarSeatMemoryManager.getInstance(activity).isDoorMode();
        if (activeModeLabel != null) {
            activeModeLabel.setText(isDoor
                    ? "Hafıza Tipi: Araç Kapısı Üzerinde"
                    : "Hafıza Tipi: Multimedya Ekranı Üzerinde");
        }
        applySeatMemoryCopy();
        int profileCount = 0;
        try {
            List<FaceProfileStore.Profile> profiles = FaceProfileStore.load(activity);
            profileCount = profiles.size();
            String lastName = prefs.getString(DmsFaceRecognitionManager.PREF_LAST_NAME, "");
            int memory = prefs.getInt(DmsFaceRecognitionManager.PREF_LAST_MEMORY, 0);
            boolean seatMemoryAvailable = CarSeatMemoryManager.isSeatMemoryAvailable(activity);
            String last = lastName.isEmpty() ? activity.getString(R.string.dms_recognition_no_match_yet)
                    : seatMemoryAvailable
                    ? activity.getString(R.string.dms_recognition_last_match, lastName, memory)
                    : activity.getString(R.string.dms_recognition_last_match_name, lastName);
            String serviceStatus = prefs.getString(DmsFaceRecognitionManager.PREF_STATUS,
                    activity.getString(R.string.dms_recognition_idle));
            boolean enabled = DmsFaceRecognitionManager.isEnabled(activity);
            String enabledText = enabled ? activity.getString(R.string.climate_auto_master_on)
                    : activity.getString(R.string.climate_auto_master_off);
            status.setText(activity.getString(R.string.dms_recognition_status,
                    profiles.size(), FaceProfileStore.MAX_PROFILES,
                    enabledText, last, serviceStatus));
            populateSavedProfilesSummary(profiles);
        } catch (IllegalStateException e) {
            status.setText(activity.getString(R.string.dms_face_load_error, e.getMessage()));
        }
        if (enabledSwitch != null) {
            boolean isServiceEnabled = DmsFaceRecognitionManager.isEnabled(activity);
            enabledSwitch.setEnabled(profileCount > 0 || isServiceEnabled);
            if (enabledSwitch.isChecked() != isServiceEnabled && !pendingEnable) {
                suppressSwitchCallback = true;
                enabledSwitch.setChecked(isServiceEnabled);
                suppressSwitchCallback = false;
            }
        }
    }

    private void applySeatMemoryCopy() {
        boolean seatMemory = CarSeatMemoryManager.isSeatMemoryAvailable(activity);
        if (description != null) {
            description.setText(seatMemory
                    ? R.string.dms_recognition_description
                    : R.string.dms_recognition_description_no_memory);
        }
        if (toggleLabel != null) {
            toggleLabel.setText(seatMemory
                    ? R.string.dms_recognition_enable
                    : R.string.dms_recognition_enable_no_memory);
        }
    }

    private void populateSavedProfilesSummary(List<FaceProfileStore.Profile> profiles) {
        if (savedProfilesContainer == null) {
            return;
        }
        savedProfilesContainer.removeAllViews();
        if (profiles.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText(CarSeatMemoryManager.isSeatMemoryAvailable(activity)
                    ? R.string.dms_face_no_profiles
                    : R.string.dms_face_no_profiles_no_memory);
            UiStyles.setTextSize(empty, R.dimen.text_size_small);
            empty.setTextColor(UiStyles.color(activity, R.color.textSecondaryCool));
            savedProfilesContainer.addView(empty);
            return;
        }
        for (int i = 0; i < profiles.size(); i++) {
            FaceProfileStore.Profile p = profiles.get(i);
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            UiStyles.applySolidRoundedBackgroundDp(
                    row, UiStyles.color(activity, R.color.automationCardSurface), 10f);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                rowLp.topMargin = dp(8);
            }

            TextView nameTv = new TextView(activity);
            nameTv.setText((i + 1) + ". " + p.name);
            UiStyles.setTextSize(nameTv, R.dimen.text_size_normal);
            nameTv.setTypeface(null, Typeface.BOLD);
            nameTv.setTextColor(UiStyles.color(activity, R.color.textPrimary));
            row.addView(nameTv, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            if (CarSeatMemoryManager.isSeatMemoryAvailable(activity)) {
                TextView badge = new TextView(activity);
                if (CarSeatMemoryManager.getInstance(activity).isDoorMode()) {
                    String coords = CarSeatMemoryManager.getInstance(activity).getCachedSlotSummary(p.seatMemory);
                    badge.setText("Hafıza " + p.seatMemory + "\n" + coords);
                } else {
                    badge.setText("Koltuk Hafızası " + p.seatMemory);
                }
                UiStyles.setTextSize(badge, R.dimen.text_size_small);
                badge.setTypeface(null, Typeface.BOLD);
                badge.setTextColor(UiStyles.color(activity, R.color.textAccent));
                badge.setGravity(Gravity.END);
                row.addView(badge);
            }

            savedProfilesContainer.addView(row, rowLp);
        }
    }

    private void enableRecognition() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            pendingEnable = true;
            suppressSwitchCallback = true;
            enabledSwitch.setChecked(false);
            suppressSwitchCallback = false;
            activity.requestPermissions(new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST);
            return;
        }
        try {
            prefs.edit()
                    .putBoolean(DmsFaceRecognitionManager.PREF_ENABLED, true)
                    .putString(DmsFaceRecognitionManager.PREF_STATUS,
                            activity.getString(R.string.dms_recognition_starting))
                    .apply();
            DmsFaceRecognitionManager.start(activity);
            logger.log(activity.getString(R.string.dms_recognition_started));
            refresh();
        } catch (RuntimeException e) {
            prefs.edit()
                    .putBoolean(DmsFaceRecognitionManager.PREF_ENABLED, false)
                    .putString(DmsFaceRecognitionManager.PREF_STATUS,
                            activity.getString(R.string.dms_recognition_stopped))
                    .apply();
            suppressSwitchCallback = true;
            enabledSwitch.setChecked(false);
            suppressSwitchCallback = false;
            Toast.makeText(activity, activity.getString(
                    R.string.dms_recognition_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
