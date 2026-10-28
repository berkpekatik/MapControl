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

    private final Activity activity;
    private final SharedPreferences prefs;
    private final Logger logger;
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

    public LinearLayout build() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        root.setPadding(padding, dp(16), padding, dp(24));

        // Header Card
        LinearLayout mainCard = new LinearLayout(activity);
        mainCard.setOrientation(LinearLayout.VERTICAL);
        mainCard.setPadding(dp(18), dp(16), dp(18), dp(18));
        UiStyles.setGlassCardBackground(mainCard);
        root.addView(mainCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(activity);
        title.setText(R.string.dms_recognition_title);
        title.setTextSize(22);
        title.setTextColor(UiStyles.color(activity, R.color.textPrimary));
        title.setTypeface(null, Typeface.BOLD);
        mainCard.addView(title);

        description = new TextView(activity);
        description.setTextSize(14);
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
        toggleLabel.setTextSize(15);
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
        status.setTextSize(14);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(14);
        mainCard.addView(status, statusLp);

        Button profilesButton = new Button(activity);
        profilesButton.setText(R.string.dms_recognition_manage_profiles);
        profilesButton.setAllCaps(false);
        profilesButton.setTextSize(15);
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
        root.addView(profilesCard, profilesCardLp);

        TextView profilesTitle = new TextView(activity);
        profilesTitle.setText(R.string.log_dms_face_profiles);
        profilesTitle.setTextSize(16);
        profilesTitle.setTypeface(null, Typeface.BOLD);
        profilesTitle.setTextColor(UiStyles.color(activity, R.color.textPrimary));
        profilesCard.addView(profilesTitle);

        savedProfilesContainer = new LinearLayout(activity);
        savedProfilesContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        containerLp.topMargin = dp(8);
        profilesCard.addView(savedProfilesContainer, containerLp);

        refresh();
        return root;
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
            empty.setTextSize(14);
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
            nameTv.setTextSize(15);
            nameTv.setTypeface(null, Typeface.BOLD);
            nameTv.setTextColor(UiStyles.color(activity, R.color.textPrimary));
            row.addView(nameTv, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            if (CarSeatMemoryManager.isSeatMemoryAvailable(activity)) {
                TextView badge = new TextView(activity);
                badge.setText("Koltuk Hafızası " + p.seatMemory);
                badge.setTextSize(13);
                badge.setTypeface(null, Typeface.BOLD);
                badge.setTextColor(UiStyles.color(activity, R.color.textAccent));
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
