package com.mapcontrol.ui.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.AudioManager;
import android.media.FaceDetector;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.speech.tts.TextToSpeech;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.service.DmsFaceRecognitionManager;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.IflyOemTtsHelper;
import com.mapcontrol.vehicle.CarSeatMemoryManager;
import com.mapcontrol.vehicle.DmsFaceMatcher;
import com.mapcontrol.vehicle.FaceProfileStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class DmsFaceProfileActivity extends AppCompatActivity
        implements TextureView.SurfaceTextureListener {
    private static final int CAMERA_REQUEST = 41;
    private static final int FRAME_WIDTH = 320;
    private static final int FRAME_HEIGHT = 240;
    private static final long ANALYSIS_INTERVAL_MS = 700;
    private static final int REQUIRED_SAMPLES = 5;

    private final Handler uiHandler = new Handler();
    private TextureView preview;
    private FaceGuideView guideOverlay;
    private ProgressBar progressBar;
    private TextView status;
    private EditText nameInput;
    private final Button[] memoryButtons = new Button[FaceProfileStore.MAX_PROFILES];
    private int selectedMemory = 1;
    private Button enrollButton;
    private Button clearButton;
    private LinearLayout profilesListContainer;

    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private Size previewSize;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private boolean dmsCameraAvailable;
    private boolean cameraOpening;
    private FaceDetector faceDetector;
    private boolean cameraReady;
    private boolean analyzing;
    private boolean profileStoreError;
    private long lastAnalysisAt;
    private int enrollmentSamples;
    private String enrollmentName;
    private int enrollmentMemory;
    private String recognitionCandidate;
    private String reportedRecognitionName;
    private int recognitionCandidateHits;
    private TextToSpeech textToSpeech;
    private boolean ttsReady;
    private String pendingTtsPhrase;
    private final List<byte[]> enrollmentTemplates = new ArrayList<>();
    private final List<FaceProfileStore.Profile> profiles = new ArrayList<>();

    private final Runnable analysisLoop = new Runnable() {
        @Override
        public void run() {
            if (!analyzing || !preview.isAvailable()) {
                return;
            }
            Bitmap frame = preview.getBitmap(FRAME_WIDTH, FRAME_HEIGHT);
            if (frame != null) {
                analyzeFrame(frame);
                frame.recycle();
            }
            uiHandler.postDelayed(this, ANALYSIS_INTERVAL_MS);
        }
    };

    private final CameraManager.AvailabilityCallback cameraAvailabilityCallback =
            new CameraManager.AvailabilityCallback() {
                @Override
                public void onCameraAvailable(@NonNull String cameraId) {
                    if ("0".equals(cameraId)) {
                        dmsCameraAvailable = true;
                        openDmsCamera();
                    }
                }

                @Override
                public void onCameraUnavailable(@NonNull String cameraId) {
                    if ("0".equals(cameraId)) {
                        dmsCameraAvailable = false;
                        if (cameraDevice == null && !cameraOpening) {
                            runOnUiThread(() ->
                                    status.setText(R.string.dms_face_camera_disconnected));
                        }
                    }
                }
            };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Stop background service first if it is holding the DMS camera
        if (DmsFaceRecognitionManager.isEnabled(this)) {
            DmsFaceRecognitionManager.stop(this);
        }
        CarSeatMemoryManager.getInstance(this).ensureInitialized();
        initTts();
        try {
            profiles.addAll(FaceProfileStore.load(this));
        } catch (IllegalStateException e) {
            profileStoreError = true;
        }
        selectedMemory = pickNextAvailableMemory();
        buildScreen();
        if (profileStoreError) {
            status.setText(getString(R.string.dms_face_load_error, "Keystore"));
        } else {
            updateProfileStatus();
        }
        refreshProfilesListUi();
        updateMemoryButtonsUi();

        preview.setSurfaceTextureListener(this);
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
        } else {
            startCameraThread();
            cameraManager.registerAvailabilityCallback(cameraAvailabilityCallback, cameraHandler);
        }
    }

    private void buildScreen() {
        int textPrimary = UiStyles.color(this, R.color.textPrimary);
        int textSecondary = UiStyles.color(this, R.color.textSecondaryCool);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(16));
        UiStyles.setRootBackground(root);

        // Top Bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams topBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        topBarLp.bottomMargin = dp(12);
        root.addView(topBar, topBarLp);

        Button backBtn = new Button(this);
        backBtn.setText(R.string.dms_face_back);
        backBtn.setTextColor(0xFFFFFFFF);
        backBtn.setTextSize(14);
        backBtn.setAllCaps(false);
        backBtn.setPadding(dp(18), dp(8), dp(18), dp(8));
        UiStyles.applySolidRoundedBackgroundDp(backBtn, 0xFF263244, 12f);
        backBtn.setOnClickListener(v -> finish());
        topBar.addView(backBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(42)));

        TextView titleView = new TextView(this);
        titleView.setText(R.string.dms_face_title);
        titleView.setTextColor(textPrimary);
        titleView.setTextSize(20);
        titleView.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(16);
        topBar.addView(titleView, titleLp);

        clearButton = new Button(this);
        clearButton.setText(R.string.dms_face_clear);
        clearButton.setTextColor(0xFFFFFFFF);
        clearButton.setTextSize(13);
        clearButton.setAllCaps(false);
        clearButton.setPadding(dp(16), dp(8), dp(16), dp(8));
        UiStyles.applySolidRoundedBackgroundDp(clearButton, 0xFFB91C1C, 12f);
        clearButton.setOnClickListener(v -> confirmClearProfiles());
        topBar.addView(clearButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)));

        // Main Two-Column Content
        LinearLayout contentRow = new LinearLayout(this);
        contentRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(contentRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Left Column: Camera Preview + Face Guide + Status
        LinearLayout leftCard = new LinearLayout(this);
        leftCard.setOrientation(LinearLayout.VERTICAL);
        leftCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        UiStyles.setGlassCardBackground(leftCard);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f);
        leftLp.rightMargin = dp(14);
        contentRow.addView(leftCard, leftLp);

        FrameLayout cameraFrame = new FrameLayout(this);
        cameraFrame.setBackgroundColor(0xFF000000);
        LinearLayout.LayoutParams camFrameLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        leftCard.addView(cameraFrame, camFrameLp);

        // Plain TextureView without custom transform/clipToOutline so DesaySV hardware surface renders cleanly
        preview = new TextureView(this);
        cameraFrame.addView(preview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        guideOverlay = new FaceGuideView(this);
        cameraFrame.addView(guideOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(REQUIRED_SAMPLES);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams progLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(10));
        progLp.topMargin = dp(10);
        leftCard.addView(progressBar, progLp);

        status = new TextView(this);
        status.setTextColor(textPrimary);
        status.setTextSize(15);
        status.setTypeface(null, Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(10), dp(10), dp(10), dp(6));
        leftCard.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Right Column: Scrollable Enrollment Form + Profiles List
        ScrollView rightScroll = new ScrollView(this);
        rightScroll.setFillViewport(true);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
        contentRow.addView(rightScroll, rightLp);

        LinearLayout rightColumn = new LinearLayout(this);
        rightColumn.setOrientation(LinearLayout.VERTICAL);
        rightScroll.addView(rightColumn, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        // Card 1: Enrollment Form
        LinearLayout enrollCard = new LinearLayout(this);
        enrollCard.setOrientation(LinearLayout.VERTICAL);
        enrollCard.setPadding(dp(16), dp(14), dp(16), dp(16));
        UiStyles.setGlassCardBackground(enrollCard);
        LinearLayout.LayoutParams enrollCardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        enrollCardLp.bottomMargin = dp(12);
        rightColumn.addView(enrollCard, enrollCardLp);

        TextView enrollHeader = new TextView(this);
        enrollHeader.setText("Yeni Kişi Tanımla (Maks. 3 Kişi)");
        enrollHeader.setTextColor(textPrimary);
        enrollHeader.setTextSize(16);
        enrollHeader.setTypeface(null, Typeface.BOLD);
        enrollCard.addView(enrollHeader);

        TextView enrollSub = new TextView(this);
        enrollSub.setText(R.string.dms_face_local_notice);
        enrollSub.setTextColor(textSecondary);
        enrollSub.setTextSize(13);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(4);
        subLp.bottomMargin = dp(10);
        enrollCard.addView(enrollSub, subLp);

        nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint(R.string.dms_face_name_hint);
        nameInput.setHintTextColor(0xFF94A3B8);
        nameInput.setTextColor(0xFFFFFFFF);
        nameInput.setTextSize(15);
        nameInput.setPadding(dp(14), dp(8), dp(14), dp(8));
        UiStyles.applySolidRoundedBackgroundDp(nameInput, 0xFF1E293B, 10f);
        enrollCard.addView(nameInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

        TextView memLabel = new TextView(this);
        memLabel.setText("Koltuk Hafızası Seçin:");
        memLabel.setTextColor(textPrimary);
        memLabel.setTextSize(13);
        memLabel.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams memLabelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        memLabelLp.topMargin = dp(10);
        memLabelLp.bottomMargin = dp(6);
        enrollCard.addView(memLabel, memLabelLp);

        LinearLayout memoryRow = new LinearLayout(this);
        memoryRow.setOrientation(LinearLayout.HORIZONTAL);
        enrollCard.addView(memoryRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        for (int i = 0; i < FaceProfileStore.MAX_PROFILES; i++) {
            final int memNum = i + 1;
            Button btn = new Button(this);
            btn.setText("Hafıza " + memNum);
            btn.setAllCaps(false);
            btn.setTextSize(13);
            btn.setOnClickListener(v -> {
                selectedMemory = memNum;
                enrollmentMemory = memNum;
                updateMemoryButtonsUi();
            });
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) {
                btnLp.leftMargin = dp(8);
            }
            memoryButtons[i] = btn;
            memoryRow.addView(btn, btnLp);
        }

        enrollButton = new Button(this);
        enrollButton.setText(R.string.dms_face_enroll);
        enrollButton.setTextColor(0xFFFFFFFF);
        enrollButton.setTextSize(15);
        enrollButton.setTypeface(null, Typeface.BOLD);
        enrollButton.setAllCaps(false);
        enrollButton.setEnabled(false);
        UiStyles.applySolidRoundedBackgroundDp(enrollButton, 0xFF2563EB, 12f);
        enrollButton.setOnClickListener(v -> onEnrollButtonClicked());
        LinearLayout.LayoutParams enrollBtnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        enrollBtnLp.topMargin = dp(12);
        enrollCard.addView(enrollButton, enrollBtnLp);

        // Card 2: Saved Profiles List
        LinearLayout listCard = new LinearLayout(this);
        listCard.setOrientation(LinearLayout.VERTICAL);
        listCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        UiStyles.setGlassCardBackground(listCard);
        rightColumn.addView(listCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView listHeader = new TextView(this);
        listHeader.setText("Kayıtlı Profiller");
        listHeader.setTextColor(textPrimary);
        listHeader.setTextSize(16);
        listHeader.setTypeface(null, Typeface.BOLD);
        listCard.addView(listHeader);

        profilesListContainer = new LinearLayout(this);
        profilesListContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listContainerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        listContainerLp.topMargin = dp(8);
        listCard.addView(profilesListContainer, listContainerLp);

        setContentView(root);
    }

    private void updateMemoryButtonsUi() {
        for (int i = 0; i < memoryButtons.length; i++) {
            Button btn = memoryButtons[i];
            if (btn == null) {
                continue;
            }
            int memNum = i + 1;
            boolean isSelected = (selectedMemory == memNum);
            btn.setTextColor(0xFFFFFFFF);
            btn.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            UiStyles.applySolidRoundedBackgroundDp(
                    btn, isSelected ? 0xFF2563EB : 0xFF334155, 10f);
        }
    }

    private int pickNextAvailableMemory() {
        for (int mem = 1; mem <= FaceProfileStore.MAX_PROFILES; mem++) {
            boolean used = false;
            for (FaceProfileStore.Profile p : profiles) {
                if (p.seatMemory == mem) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return mem;
            }
        }
        return 1;
    }

    private void refreshProfilesListUi() {
        if (profilesListContainer == null) {
            return;
        }
        int textSecondary = UiStyles.color(this, R.color.textSecondaryCool);
        profilesListContainer.removeAllViews();
        if (clearButton != null) {
            clearButton.setVisibility(profiles.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (profiles.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Henüz kayıtlı kişi bulunmuyor.");
            empty.setTextColor(textSecondary);
            empty.setTextSize(14);
            empty.setPadding(0, dp(6), 0, dp(4));
            profilesListContainer.addView(empty);
            return;
        }
        for (int i = 0; i < profiles.size(); i++) {
            final int index = i;
            final FaceProfileStore.Profile p = profiles.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            UiStyles.applySolidRoundedBackgroundDp(row, 0xFF1E293B, 10f);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                rowLp.topMargin = dp(8);
            }

            LinearLayout infoCol = new LinearLayout(this);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            TextView nameTv = new TextView(this);
            nameTv.setText((i + 1) + ". " + p.name);
            nameTv.setTextColor(0xFFFFFFFF);
            nameTv.setTextSize(15);
            nameTv.setTypeface(null, Typeface.BOLD);
            infoCol.addView(nameTv);

            TextView subTv = new TextView(this);
            subTv.setText("Koltuk Hafızası " + p.seatMemory);
            subTv.setTextColor(0xFF60A5FA);
            subTv.setTextSize(13);
            infoCol.addView(subTv);

            row.addView(infoCol, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            for (int m = 1; m <= FaceProfileStore.MAX_PROFILES; m++) {
                final int targetMem = m;
                Button memBtn = new Button(this);
                memBtn.setText(String.valueOf(m));
                memBtn.setAllCaps(false);
                memBtn.setTextColor(0xFFFFFFFF);
                memBtn.setTextSize(12);
                memBtn.setPadding(0, 0, 0, 0);
                boolean active = (p.seatMemory == m);
                memBtn.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
                UiStyles.applySolidRoundedBackgroundDp(
                        memBtn, active ? 0xFF2563EB : 0xFF334155, 8f);
                memBtn.setOnClickListener(v -> updateProfileSeatMemory(index, targetMem));
                LinearLayout.LayoutParams memLp = new LinearLayout.LayoutParams(dp(34), dp(34));
                memLp.rightMargin = dp(6);
                row.addView(memBtn, memLp);
            }

            Button delBtn = new Button(this);
            delBtn.setText(R.string.dms_face_delete_single);
            delBtn.setAllCaps(false);
            delBtn.setTextColor(0xFFFFFFFF);
            delBtn.setTextSize(12);
            delBtn.setPadding(dp(12), dp(4), dp(12), dp(4));
            UiStyles.applySolidRoundedBackgroundDp(delBtn, 0xFFB91C1C, 8f);
            delBtn.setOnClickListener(v -> deleteSingleProfile(index));
            row.addView(delBtn, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)));

            profilesListContainer.addView(row, rowLp);
        }
    }

    private void updateProfileSeatMemory(int index, int newSeatMemory) {
        if (index < 0 || index >= profiles.size()) {
            return;
        }
        FaceProfileStore.Profile old = profiles.get(index);
        if (old.seatMemory == newSeatMemory) {
            CarSeatMemoryManager.getInstance(this).applySeatMemory(newSeatMemory);
            return;
        }
        profiles.set(index, new FaceProfileStore.Profile(
                old.name, newSeatMemory, old.templates));
        try {
            FaceProfileStore.save(this, profiles);
            reportedRecognitionName = null;
            sendBroadcast(new Intent("com.mapcontrol.DMS_FACE_PROFILES_CHANGED"));
            selectedMemory = pickNextAvailableMemory();
            updateMemoryButtonsUi();
            refreshProfilesListUi();
            CarSeatMemoryManager.getInstance(this).applySeatMemory(newSeatMemory);
        } catch (IllegalStateException e) {
            Toast.makeText(this, getString(R.string.dms_face_save_error, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void deleteSingleProfile(int index) {
        if (index < 0 || index >= profiles.size()) {
            return;
        }
        profiles.remove(index);
        try {
            FaceProfileStore.save(this, profiles);
            sendBroadcast(new Intent("com.mapcontrol.DMS_FACE_PROFILES_CHANGED"));
            selectedMemory = pickNextAvailableMemory();
            updateMemoryButtonsUi();
            refreshProfilesListUi();
            updateProfileStatus();
            updateEnrollButtonState();
        } catch (IllegalStateException e) {
            Toast.makeText(this, getString(R.string.dms_face_save_error, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void onEnrollButtonClicked() {
        if (enrollmentName != null) {
            cancelActiveEnrollment();
            updateProfileStatus();
            return;
        }
        if (!cameraReady) {
            Toast.makeText(this, R.string.dms_face_camera_disconnected, Toast.LENGTH_LONG).show();
            return;
        }
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.dms_face_name_required, Toast.LENGTH_LONG).show();
            nameInput.requestFocus();
            return;
        }
        int existingIndex = -1;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).name.equalsIgnoreCase(name)) {
                existingIndex = i;
                break;
            }
        }
        if (existingIndex < 0 && profiles.size() >= FaceProfileStore.MAX_PROFILES) {
            Toast.makeText(this, R.string.dms_face_max_profiles, Toast.LENGTH_LONG).show();
            return;
        }
        hideKeyboard();
        enrollmentName = name;
        enrollmentMemory = selectedMemory;
        enrollmentSamples = 0;
        enrollmentTemplates.clear();
        progressBar.setProgress(0);
        progressBar.setVisibility(View.VISIBLE);
        guideOverlay.setState(FaceGuideView.STATE_SCANNING);
        updateEnrollButtonState();
        analyzing = true;
        uiHandler.removeCallbacks(analysisLoop);
        uiHandler.post(analysisLoop);
        updateEnrollmentPrompt();
    }

    private void cancelActiveEnrollment() {
        enrollmentName = null;
        enrollmentSamples = 0;
        enrollmentTemplates.clear();
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (guideOverlay != null) {
            guideOverlay.setState(FaceGuideView.STATE_IDLE);
        }
        updateEnrollButtonState();
    }

    private void updateEnrollButtonState() {
        if (enrollButton == null) {
            return;
        }
        if (enrollmentName != null) {
            enrollButton.setText(R.string.dms_face_cancel_enroll);
            enrollButton.setEnabled(true);
            UiStyles.applySolidRoundedBackgroundDp(enrollButton, 0xFFB91C1C, 12f);
        } else {
            enrollButton.setText(R.string.dms_face_enroll);
            boolean canEnroll = cameraReady && !profileStoreError;
            enrollButton.setEnabled(canEnroll);
            UiStyles.applySolidRoundedBackgroundDp(
                    enrollButton, canEnroll ? 0xFF2563EB : 0xFF475569, 12f);
        }
    }

    private void updateEnrollmentPrompt() {
        status.setText(getString(R.string.dms_face_enroll_prompt,
                enrollmentSamples + 1, REQUIRED_SAMPLES));
        if (progressBar != null) {
            progressBar.setProgress(enrollmentSamples);
        }
    }

    private void analyzeFrame(Bitmap source) {
        Bitmap rgb565 = source.copy(Bitmap.Config.RGB_565, false);
        if (rgb565 == null) {
            return;
        }
        try {
            if (faceDetector == null) {
                faceDetector = new FaceDetector(FRAME_WIDTH, FRAME_HEIGHT, 1);
            }
            FaceDetector.Face[] faces = new FaceDetector.Face[1];
            int faceCount = faceDetector.findFaces(rgb565, faces);
            if (faceCount != 1 || faces[0] == null || faces[0].confidence() < 0.35f) {
                if (enrollmentName != null) {
                    guideOverlay.setState(FaceGuideView.STATE_WARN);
                    status.setText(R.string.dms_face_center_prompt);
                } else {
                    guideOverlay.setState(FaceGuideView.STATE_IDLE);
                    status.setText(R.string.dms_face_no_face);
                }
                return;
            }
            FaceDetector.Face face = faces[0];
            if (enrollmentName != null) {
                captureEnrollmentSample(rgb565, face);
            } else if (!profiles.isEmpty()) {
                recognizeFace(rgb565, face);
            } else {
                guideOverlay.setState(FaceGuideView.STATE_DETECTED);
            }
        } finally {
            rgb565.recycle();
        }
    }

    private void captureEnrollmentSample(Bitmap frame, FaceDetector.Face face) {
        long now = System.currentTimeMillis();
        if (now - lastAnalysisAt < 900) {
            return;
        }
        lastAnalysisAt = now;
        byte[] descriptor = DmsFaceMatcher.describeFace(frame, face);
        if (descriptor == null) {
            guideOverlay.setState(FaceGuideView.STATE_WARN);
            status.setText(R.string.dms_face_adjust_prompt);
            return;
        }
        guideOverlay.setState(FaceGuideView.STATE_DETECTED);
        enrollmentTemplates.add(descriptor);
        enrollmentSamples++;
        if (progressBar != null) {
            progressBar.setProgress(enrollmentSamples);
        }
        if (enrollmentSamples >= REQUIRED_SAMPLES) {
            finishEnrollment();
        } else {
            updateEnrollmentPrompt();
        }
    }

    private void finishEnrollment() {
        String savedName = enrollmentName;
        int savedMemory = enrollmentMemory;
        List<FaceProfileStore.Profile> updated = new ArrayList<>();
        boolean replaced = false;
        for (FaceProfileStore.Profile existing : profiles) {
            if (existing.name.equalsIgnoreCase(savedName)) {
                updated.add(new FaceProfileStore.Profile(
                        savedName, savedMemory, new ArrayList<>(enrollmentTemplates)));
                replaced = true;
            } else {
                updated.add(existing);
            }
        }
        if (!replaced) {
            updated.add(new FaceProfileStore.Profile(
                    savedName, savedMemory, new ArrayList<>(enrollmentTemplates)));
        }
        enrollmentName = null;
        enrollmentSamples = 0;
        enrollmentTemplates.clear();
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        try {
            FaceProfileStore.save(this, updated);
            profiles.clear();
            profiles.addAll(updated);
            CarSeatMemoryManager.getInstance(this).syncCurrentFromCar();

            SharedPreferences prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
            prefs.edit()
                    .putBoolean(DmsFaceRecognitionManager.PREF_ENABLED, true)
                    .putString(DmsFaceRecognitionManager.PREF_STATUS,
                            getString(R.string.dms_recognition_waiting_for_power))
                    .apply();

            sendBroadcast(new Intent("com.mapcontrol.DMS_FACE_PROFILES_CHANGED"));
            sendBroadcast(new Intent(DmsFaceRecognitionManager.ACTION_STATUS));

            nameInput.setText("");
            selectedMemory = pickNextAvailableMemory();
            updateMemoryButtonsUi();
            refreshProfilesListUi();
            guideOverlay.setState(FaceGuideView.STATE_DETECTED);
            String msg = getString(R.string.dms_face_saved, savedName, savedMemory);
            status.setText(msg);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        } catch (IllegalStateException e) {
            status.setText(getString(R.string.dms_face_save_error, e.getMessage()));
        }
        updateEnrollButtonState();
    }

    private void recognizeFace(Bitmap frame, FaceDetector.Face face) {
        DmsFaceMatcher.Match match = DmsFaceMatcher.findMatch(frame, face, profiles);
        if (match != null) {
            FaceProfileStore.Profile best = match.profile;
            if (best.name.equals(recognitionCandidate)) {
                recognitionCandidateHits++;
            } else {
                recognitionCandidate = best.name;
                recognitionCandidateHits = 1;
            }
            if (recognitionCandidateHits >= 2) {
                guideOverlay.setState(FaceGuideView.STATE_DETECTED);
                status.setText(getString(R.string.dms_face_match, best.name, best.seatMemory));
                if (!best.name.equals(reportedRecognitionName)) {
                    reportedRecognitionName = best.name;
                    getSharedPreferences("MapControlPrefs", MODE_PRIVATE).edit()
                            .putString(DmsFaceRecognitionManager.PREF_LAST_NAME, best.name)
                            .putInt(DmsFaceRecognitionManager.PREF_LAST_MEMORY, best.seatMemory)
                            .putString(DmsFaceRecognitionManager.PREF_STATUS,
                                    getString(R.string.dms_recognition_match,
                                            best.name, best.seatMemory))
                            .apply();
                    sendBroadcast(new Intent(DmsFaceRecognitionManager.ACTION_STATUS));
                    CarSeatMemoryManager.getInstance(this).applySeatMemory(best.seatMemory);
                    speakWelcomePhrase(best.name);
                }
            } else {
                guideOverlay.setState(FaceGuideView.STATE_SCANNING);
                status.setText(R.string.dms_face_checking);
            }
        } else {
            recognitionCandidate = null;
            recognitionCandidateHits = 0;
            reportedRecognitionName = null;
            guideOverlay.setState(FaceGuideView.STATE_WARN);
            status.setText(R.string.dms_face_unknown);
        }
    }

    private void initTts() {
        // Sistem TTS, MainActivity ile aynı SystemTtsManager üzerinden yönetilir.
    }

    private void speakWelcomePhrase(String phrase) {
        if (phrase == null || phrase.trim().isEmpty()) {
            return;
        }
        com.mapcontrol.util.SystemTtsManager.speakDelayed(getApplicationContext(), phrase.trim(), 450L);
    }

    private void updateProfileStatus() {
        if (profileStoreError) {
            updateEnrollButtonState();
            return;
        }
        if (enrollmentName == null && !profiles.isEmpty()) {
            status.setText(getString(R.string.dms_face_profile_count, profiles.size(),
                    FaceProfileStore.MAX_PROFILES));
        } else if (profiles.isEmpty()) {
            status.setText(R.string.dms_face_no_profiles);
        }
        updateEnrollButtonState();
    }

    private void confirmClearProfiles() {
        if (profiles.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.dms_face_clear)
                .setMessage(R.string.dms_face_clear_confirm)
                .setNegativeButton(R.string.common_no, null)
                .setPositiveButton(R.string.common_yes, (dialog, which) -> {
                    cancelActiveEnrollment();
                    profiles.clear();
                    FaceProfileStore.save(this, profiles);
                    sendBroadcast(new Intent("com.mapcontrol.DMS_FACE_PROFILES_CHANGED"));
                    selectedMemory = 1;
                    updateMemoryButtonsUi();
                    refreshProfilesListUi();
                    updateProfileStatus();
                })
                .show();
    }

    private void startCameraThread() {
        cameraThread = new HandlerThread("DmsFaceCamera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void openDmsCamera() {
        if (cameraManager == null || !preview.isAvailable()
                || !dmsCameraAvailable || cameraOpening || cameraDevice != null
                || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics("0");
            android.hardware.camera2.params.StreamConfigurationMap map =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = map == null ? null : map.getOutputSizes(SurfaceTexture.class);
            if (sizes == null || sizes.length == 0) {
                runOnUiThread(() -> status.setText(R.string.dms_face_camera_missing));
                return;
            }
            // IMPORTANT: Must use sizes[0] (native resolution) on DesaySV DMS camera ("0")
            previewSize = sizes[0];
            cameraOpening = true;
            cameraManager.openCamera("0", new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraOpening = false;
                    cameraDevice = camera;
                    createPreviewSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    cameraOpening = false;
                    camera.close();
                    cameraDevice = null;
                    cameraReady = false;
                    runOnUiThread(() -> {
                        updateEnrollButtonState();
                        status.setText(R.string.dms_face_camera_disconnected);
                    });
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    cameraOpening = false;
                    camera.close();
                    cameraDevice = null;
                    cameraReady = false;
                    runOnUiThread(() -> {
                        updateEnrollButtonState();
                        status.setText(getString(R.string.dms_face_camera_error, String.valueOf(error)));
                    });
                }
            }, cameraHandler);
        } catch (CameraAccessException | SecurityException e) {
            cameraOpening = false;
            runOnUiThread(() ->
                    status.setText(getString(R.string.dms_face_camera_error, e.getMessage())));
        }
    }

    private void createPreviewSession() {
        if (cameraDevice == null || !preview.isAvailable()) {
            return;
        }
        try {
            SurfaceTexture texture = preview.getSurfaceTexture();
            if (previewSize == null) {
                throw new IllegalStateException("DMS kamera önizleme çözünürlüğü bulunamadı");
            }
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);
            CaptureRequest.Builder request =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            request.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                request.set(CaptureRequest.CONTROL_MODE,
                                        CaptureRequest.CONTROL_MODE_AUTO);
                                session.setRepeatingRequest(request.build(), null, cameraHandler);
                                cameraReady = true;
                                runOnUiThread(() -> updateEnrollButtonState());
                                if (!profiles.isEmpty() || enrollmentName != null) {
                                    analyzing = true;
                                    uiHandler.removeCallbacks(analysisLoop);
                                    uiHandler.post(analysisLoop);
                                }
                            } catch (CameraAccessException e) {
                                runOnUiThread(() -> status.setText(getString(
                                        R.string.dms_face_camera_error, e.getMessage())));
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            cameraReady = false;
                            runOnUiThread(() -> {
                                updateEnrollButtonState();
                                status.setText(R.string.dms_face_camera_disconnected);
                            });
                        }
                    }, cameraHandler);
        } catch (CameraAccessException | IllegalStateException e) {
            runOnUiThread(() ->
                    status.setText(getString(R.string.dms_face_camera_error, e.getMessage())));
        }
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        if (cameraThread != null) {
            openDmsCamera();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        closeCamera();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
    }

    private void closeCamera() {
        analyzing = false;
        cameraReady = false;
        uiHandler.removeCallbacks(analysisLoop);
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCameraThread();
            cameraManager.registerAvailabilityCallback(cameraAvailabilityCallback, cameraHandler);
        } else if (requestCode == CAMERA_REQUEST) {
            status.setText(R.string.dms_face_camera_permission);
        }
    }

    @Override
    protected void onDestroy() {
        closeCamera();
        if (cameraManager != null && cameraHandler != null) {
            cameraManager.unregisterAvailabilityCallback(cameraAvailabilityCallback);
        }
        if (cameraThread != null) {
            cameraThread.quitSafely();
            cameraThread = null;
        }
        if (faceDetector != null) {
            faceDetector = null;
        }
        if (textToSpeech != null) {
            try {
                textToSpeech.stop();
                textToSpeech.shutdown();
            } catch (Throwable ignored) {
            }
            textToSpeech = null;
            ttsReady = false;
        }
        // Resume background service after camera is fully closed
        if (DmsFaceRecognitionManager.isEnabled(this) && !profiles.isEmpty()) {
            try {
                DmsFaceRecognitionManager.start(this);
            } catch (RuntimeException ignored) {
            }
        }
        super.onDestroy();
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class FaceGuideView extends View {
        static final int STATE_IDLE = 0;
        static final int STATE_SCANNING = 1;
        static final int STATE_DETECTED = 2;
        static final int STATE_WARN = 3;

        private final Paint ovalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF ovalRect = new RectF();
        private int state = STATE_IDLE;

        FaceGuideView(Context context) {
            super(context);
            float density = context.getResources().getDisplayMetrics().density;
            ovalPaint.setStyle(Paint.Style.STROKE);
            ovalPaint.setStrokeWidth(3f * density);
            scrimPaint.setStyle(Paint.Style.STROKE);
            scrimPaint.setStrokeWidth(8f * density);
            updateColors();
        }

        void setState(int newState) {
            if (this.state != newState) {
                this.state = newState;
                updateColors();
                invalidate();
            }
        }

        private void updateColors() {
            int mainColor;
            int glowColor;
            switch (state) {
                case STATE_DETECTED:
                    mainColor = 0xFF22C55E;
                    glowColor = 0x4422C55E;
                    break;
                case STATE_SCANNING:
                    mainColor = 0xFF3B82F6;
                    glowColor = 0x443B82F6;
                    break;
                case STATE_WARN:
                    mainColor = 0xFFF59E0B;
                    glowColor = 0x44F59E0B;
                    break;
                case STATE_IDLE:
                default:
                    mainColor = 0x8894A3B8;
                    glowColor = 0x2294A3B8;
                    break;
            }
            ovalPaint.setColor(mainColor);
            scrimPaint.setColor(glowColor);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            float ovalH = h * 0.68f;
            float ovalW = ovalH * 0.76f;
            if (ovalW > w * 0.65f) {
                ovalW = w * 0.65f;
                ovalH = ovalW / 0.76f;
            }
            float cx = w / 2f;
            float cy = h / 2f;
            ovalRect.set(cx - ovalW / 2f, cy - ovalH / 2f, cx + ovalW / 2f, cy + ovalH / 2f);
            canvas.drawOval(ovalRect, scrimPaint);
            canvas.drawOval(ovalRect, ovalPaint);
        }
    }
}
