package com.mapcontrol.ui.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PointF;
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
import android.os.SystemClock;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
    private static final long TONE_FRAME_INTERVAL_MS = 80L;

    private final Handler uiHandler = new Handler();
    private TextureView preview;
    private ImageView tonePreview;
    private float faceCenterX = 0.5f;
    private float faceCenterY = 0.5f;
    private boolean faceCenterKnown;
    private long lastFaceLocateAt;
    private final Paint tonePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final ColorMatrix toneMatrix = new ColorMatrix();
    private final Canvas toneCanvas = new Canvas();
    private Bitmap toneFrame;
    private int[] toneSampleRow;
    private long lastToneFrameAt;
    private final DmsClusterPreview clusterPreview = new DmsClusterPreview();
    private ProgressBar progressBar;
    private TextView status;
    private EditText nameInput;
    private final Button[] memoryButtons = new Button[FaceProfileStore.MAX_PROFILES];
    private TextView memoryLabel;
    private LinearLayout memoryRow;
    private Button modeButton;
    private LinearLayout doorSlotsCard;
    private LinearLayout doorSlotsContainer;
    private boolean seatMemoryAvailable;
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
        seatMemoryAvailable = CarSeatMemoryManager.isSeatMemoryAvailable(this);
        if (seatMemoryAvailable) {
            CarSeatMemoryManager.getInstance(this).ensureInitialized();
        }
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

        if (!CarSeatMemoryManager.getInstance(this).hasExplicitModePreference()) {
            uiHandler.postDelayed(this::showModeSelectionDialog, 400L);
        }

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
        backBtn.setTextColor(textPrimary);
        backBtn.setTextSize(14);
        backBtn.setAllCaps(false);
        backBtn.setPadding(dp(18), dp(8), dp(18), dp(8));
        UiStyles.applySolidRoundedBackgroundDp(backBtn,
                UiStyles.color(this, R.color.buttonSecondaryMuted), 12f);
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

        modeButton = new Button(this);
        updateModeButtonText();
        modeButton.setTextColor(0xFFFFFFFF);
        modeButton.setTextSize(12);
        modeButton.setAllCaps(false);
        modeButton.setPadding(dp(12), dp(6), dp(12), dp(6));
        UiStyles.applySolidRoundedBackgroundDp(modeButton, 0xFF1E3A8A, 10f);
        modeButton.setOnClickListener(v -> showModeSelectionDialog());
        LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(40));
        modeLp.rightMargin = dp(10);
        topBar.addView(modeButton, modeLp);

        clearButton = new Button(this);
        clearButton.setText(R.string.dms_face_clear);
        clearButton.setTextColor(0xFFFFFFFF);
        clearButton.setTextSize(13);
        clearButton.setAllCaps(false);
        clearButton.setPadding(dp(16), dp(8), dp(16), dp(8));
        UiStyles.applySolidRoundedBackgroundDp(clearButton,
                UiStyles.color(this, R.color.buttonDanger), 12f);
        clearButton.setOnClickListener(v -> confirmClearProfiles());
        topBar.addView(clearButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)));

        boolean portrait = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;

        // Camera and enrollment sit side by side in landscape, stacked in portrait.
        LinearLayout contentRow = new LinearLayout(this);
        contentRow.setOrientation(portrait ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        root.addView(contentRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Left Column: Camera Preview + Face Guide + Status
        LinearLayout leftCard = new LinearLayout(this);
        leftCard.setOrientation(LinearLayout.VERTICAL);
        leftCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        UiStyles.setGlassCardBackground(leftCard);
        LinearLayout.LayoutParams leftLp = portrait
                ? new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.15f)
                : new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f);
        if (portrait) {
            leftLp.bottomMargin = dp(14);
        } else {
            leftLp.rightMargin = dp(14);
        }
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

        // Nötr ton katmanı yalnızca kare kopyası başarılıysa görünür.
        // Bu baş ünitede GPU kopyası sık başarısız oluyor; o zaman canlı TextureView kalır.
        tonePreview = new ImageView(this);
        tonePreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        tonePreview.setVisibility(View.GONE);
        cameraFrame.addView(tonePreview, new FrameLayout.LayoutParams(
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
        LinearLayout.LayoutParams rightLp = portrait
                ? new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                : new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
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
        enrollSub.setText(seatMemoryAvailable
                ? R.string.dms_face_local_notice
                : R.string.dms_face_local_notice_no_memory);
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
        nameInput.setHintTextColor(UiStyles.color(this, R.color.textMuted));
        nameInput.setTextColor(textPrimary);
        nameInput.setTextSize(15);
        nameInput.setPadding(dp(14), dp(8), dp(14), dp(8));
        UiStyles.applySolidRoundedBackgroundDp(nameInput,
                UiStyles.color(this, R.color.surfaceCardInner), 10f);
        enrollCard.addView(nameInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

        memoryLabel = new TextView(this);
        memoryLabel.setText("Koltuk Hafızası Seçin:");
        memoryLabel.setTextColor(textPrimary);
        memoryLabel.setTextSize(13);
        memoryLabel.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams memLabelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        memLabelLp.topMargin = dp(10);
        memLabelLp.bottomMargin = dp(6);
        enrollCard.addView(memoryLabel, memLabelLp);

        memoryRow = new LinearLayout(this);
        memoryRow.setOrientation(LinearLayout.HORIZONTAL);
        enrollCard.addView(memoryRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
        if (!seatMemoryAvailable) {
            memoryLabel.setVisibility(View.GONE);
            memoryRow.setVisibility(View.GONE);
        }

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
        UiStyles.applySolidRoundedBackgroundDp(enrollButton,
                UiStyles.color(this, R.color.buttonPrimary), 12f);
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

        doorSlotsCard = new LinearLayout(this);
        doorSlotsCard.setOrientation(LinearLayout.VERTICAL);
        doorSlotsCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        UiStyles.setGlassCardBackground(doorSlotsCard);
        LinearLayout.LayoutParams doorCardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorCardLp.topMargin = dp(12);
        doorCardLp.bottomMargin = dp(14);
        rightColumn.addView(doorSlotsCard, doorCardLp);

        TextView doorCardHeader = new TextView(this);
        doorCardHeader.setText("Araç Kapısı Üzerinde Hafıza Konumları");
        doorCardHeader.setTextColor(textPrimary);
        doorCardHeader.setTextSize(15);
        doorCardHeader.setTypeface(null, Typeface.BOLD);
        doorSlotsCard.addView(doorCardHeader);

        TextView doorCardSub = new TextView(this);
        doorCardSub.setText("Kapıdaki 1, 2 veya 3 tuşuna basarak istediğiniz yuvanın konumunu MapControl'e aktarabilir ve güncelleyebilirsiniz.");
        doorCardSub.setTextColor(textSecondary);
        doorCardSub.setTextSize(12);
        LinearLayout.LayoutParams doorSubLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        doorSubLp.topMargin = dp(2);
        doorSubLp.bottomMargin = dp(8);
        doorSlotsCard.addView(doorCardSub, doorSubLp);

        doorSlotsContainer = new LinearLayout(this);
        doorSlotsContainer.setOrientation(LinearLayout.VERTICAL);
        doorSlotsCard.addView(doorSlotsContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

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
            btn.setTextColor(isSelected ? 0xFFFFFFFF : UiStyles.color(this, R.color.textPrimary));
            btn.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            UiStyles.applySolidRoundedBackgroundDp(btn, UiStyles.color(this,
                    isSelected ? R.color.buttonPrimary : R.color.buttonSecondaryMuted), 10f);
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
        updateModeButtonText();
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
            updateDoorSlotsCard();
            return;
        }
        boolean isDoorMode = CarSeatMemoryManager.getInstance(this).isDoorMode();
        for (int i = 0; i < profiles.size(); i++) {
            final int index = i;
            final FaceProfileStore.Profile p = profiles.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            UiStyles.applySolidRoundedBackgroundDp(row,
                    UiStyles.color(this, R.color.surfaceCardInner), 10f);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                rowLp.topMargin = dp(8);
            }

            LinearLayout infoCol = new LinearLayout(this);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            TextView nameTv = new TextView(this);
            nameTv.setText((i + 1) + ". " + p.name);
            nameTv.setTextColor(UiStyles.color(this, R.color.textPrimary));
            nameTv.setTextSize(15);
            nameTv.setTypeface(null, Typeface.BOLD);
            infoCol.addView(nameTv);

            if (seatMemoryAvailable) {
                TextView subTv = new TextView(this);
                if (isDoorMode) {
                    String summary = CarSeatMemoryManager.getInstance(this).getCachedSlotSummary(p.seatMemory);
                    subTv.setText("Koltuk Hafızası " + p.seatMemory + " · " + summary);
                } else {
                    subTv.setText("Koltuk Hafızası " + p.seatMemory);
                }
                subTv.setTextColor(UiStyles.color(this, R.color.textAccent));
                subTv.setTextSize(13);
                infoCol.addView(subTv);
            }

            row.addView(infoCol, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            if (seatMemoryAvailable && isDoorMode) {
                Button updateBtn = new Button(this);
                updateBtn.setText("Hafıza " + p.seatMemory + " Güncelle");
                updateBtn.setAllCaps(false);
                updateBtn.setTextColor(0xFFFFFFFF);
                updateBtn.setTextSize(11);
                updateBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
                UiStyles.applySolidRoundedBackgroundDp(updateBtn, 0xFF0284C7, 8f);
                final int slotToUpdate = p.seatMemory;
                updateBtn.setOnClickListener(v -> showSeatCalibrationDialog(slotToUpdate, this::refreshProfilesListUi));
                LinearLayout.LayoutParams updateLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, dp(34));
                updateLp.rightMargin = dp(6);
                row.addView(updateBtn, updateLp);
            }

            if (seatMemoryAvailable) {
            for (int m = 1; m <= FaceProfileStore.MAX_PROFILES; m++) {
                final int targetMem = m;
                Button memBtn = new Button(this);
                memBtn.setText(String.valueOf(m));
                memBtn.setAllCaps(false);
                memBtn.setTextSize(12);
                memBtn.setPadding(0, 0, 0, 0);
                boolean active = (p.seatMemory == m);
                memBtn.setTextColor(active
                        ? 0xFFFFFFFF
                        : UiStyles.color(this, R.color.textPrimary));
                memBtn.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
                UiStyles.applySolidRoundedBackgroundDp(memBtn, UiStyles.color(this,
                        active ? R.color.buttonPrimary : R.color.buttonSecondaryMuted), 8f);
                memBtn.setOnClickListener(v -> updateProfileSeatMemory(index, targetMem));
                LinearLayout.LayoutParams memLp = new LinearLayout.LayoutParams(dp(34), dp(34));
                memLp.rightMargin = dp(6);
                row.addView(memBtn, memLp);
            }
            }

            Button delBtn = new Button(this);
            delBtn.setText(R.string.dms_face_delete_single);
            delBtn.setAllCaps(false);
            delBtn.setTextColor(0xFFFFFFFF);
            delBtn.setTextSize(12);
            delBtn.setPadding(dp(12), dp(4), dp(12), dp(4));
            UiStyles.applySolidRoundedBackgroundDp(delBtn,
                    UiStyles.color(this, R.color.buttonDanger), 8f);
            delBtn.setOnClickListener(v -> deleteSingleProfile(index));
            row.addView(delBtn, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)));

            profilesListContainer.addView(row, rowLp);
        }
        updateDoorSlotsCard();
    }

    private void updateProfileSeatMemory(int index, int newSeatMemory) {
        if (index < 0 || index >= profiles.size()) {
            return;
        }
        FaceProfileStore.Profile old = profiles.get(index);
        if (!seatMemoryAvailable) {
            return;
        }
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

    private void updateModeButtonText() {
        if (modeButton == null) return;
        boolean door = CarSeatMemoryManager.getInstance(this).isDoorMode();
        modeButton.setText(door ? "Araç Kapısı Üzerinde" : "Multimedya Ekranı Üzerinde");
        UiStyles.applySolidRoundedBackgroundDp(modeButton, door ? 0xFF0D9488 : 0xFF2563EB, 10f);
    }

    private void showModeSelectionDialog() {
        String[] options = new String[]{
                "Multimedya Ekranı Üzerinde",
                "Araç Kapısı Üzerinde"
        };
        int current = CarSeatMemoryManager.getInstance(this).getMemoryMode();
        new AlertDialog.Builder(this)
                .setTitle("Koltuk Hafıza Tipi")
                .setSingleChoiceItems(options, current, (dialog, which) -> {
                    CarSeatMemoryManager.getInstance(this).setMemoryMode(which);
                    updateModeButtonText();
                    refreshProfilesListUi();
                    dialog.dismiss();
                    Toast.makeText(this, which == CarSeatMemoryManager.MODE_DOOR
                            ? "Araç Kapısı Üzerinde modu seçildi."
                            : "Multimedya Ekranı Üzerinde modu seçildi.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Kapat", null)
                .show();
    }

    private void updateDoorSlotsCard() {
        if (doorSlotsCard == null || doorSlotsContainer == null) return;
        boolean isDoor = CarSeatMemoryManager.getInstance(this).isDoorMode();
        doorSlotsCard.setVisibility(isDoor ? View.VISIBLE : View.GONE);
        if (!isDoor) return;

        doorSlotsContainer.removeAllViews();
        CarSeatMemoryManager mgr = CarSeatMemoryManager.getInstance(this);
        for (int m = 1; m <= FaceProfileStore.MAX_PROFILES; m++) {
            final int slot = m;
            LinearLayout slotRow = new LinearLayout(this);
            slotRow.setOrientation(LinearLayout.HORIZONTAL);
            slotRow.setGravity(Gravity.CENTER_VERTICAL);
            slotRow.setPadding(dp(10), dp(8), dp(10), dp(8));
            UiStyles.applySolidRoundedBackgroundDp(slotRow, 0xFF1E293B, 8f);
            LinearLayout.LayoutParams slotRowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (m > 1) slotRowLp.topMargin = dp(6);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);

            TextView slotNameTv = new TextView(this);
            slotNameTv.setText("Hafıza " + slot);
            slotNameTv.setTextColor(0xFFFFFFFF);
            slotNameTv.setTextSize(14);
            slotNameTv.setTypeface(null, Typeface.BOLD);
            textCol.addView(slotNameTv);

            TextView slotSummaryTv = new TextView(this);
            slotSummaryTv.setText(mgr.getCachedSlotSummary(slot));
            slotSummaryTv.setTextColor(mgr.isSlotCalibrated(slot) ? 0xFF34D399 : 0xFF94A3B8);
            slotSummaryTv.setTextSize(12);
            textCol.addView(slotSummaryTv);

            slotRow.addView(textCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button calBtn = new Button(this);
            calBtn.setText("Kapıdan Güncelle");
            calBtn.setAllCaps(false);
            calBtn.setTextColor(0xFFFFFFFF);
            calBtn.setTextSize(12);
            calBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
            UiStyles.applySolidRoundedBackgroundDp(calBtn, 0xFF0284C7, 8f);
            calBtn.setOnClickListener(v -> showSeatCalibrationDialog(slot, this::refreshProfilesListUi));
            slotRow.addView(calBtn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)));

            doorSlotsContainer.addView(slotRow, slotRowLp);
        }
    }

    private void showSeatCalibrationDialog(int targetSlot, @Nullable Runnable onComplete) {
        CarSeatMemoryManager memoryMgr = CarSeatMemoryManager.getInstance(this);
        memoryMgr.ensureInitialized();

        LinearLayout dialogView = new LinearLayout(this);
        dialogView.setOrientation(LinearLayout.VERTICAL);
        dialogView.setPadding(dp(20), dp(16), dp(20), dp(16));

        TextView promptTv = new TextView(this);
        promptTv.setText("Lütfen aracınızın kapısındaki (veya koltuğundaki) " + targetSlot
                + " numaralı hafıza tuşuna basın.\n\nKoltuk durduğunda milimetrik koordinatlar otomatik kaydedilecektir.");
        promptTv.setTextColor(0xFFE2E8F0);
        promptTv.setTextSize(14);
        promptTv.setLineSpacing(dp(2), 1f);
        dialogView.addView(promptTv);

        TextView statusTv = new TextView(this);
        statusTv.setText("⏳ Durum: Kapıdaki tuşa basılması bekleniyor...");
        statusTv.setTextColor(0xFF38BDF8);
        statusTv.setTextSize(13);
        statusTv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(14);
        dialogView.addView(statusTv, statusLp);

        TextView coordsTv = new TextView(this);
        coordsTv.setText(memoryMgr.getCachedSlotSummary(targetSlot));
        coordsTv.setTextColor(0xFF94A3B8);
        coordsTv.setTextSize(12);
        LinearLayout.LayoutParams coordsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        coordsLp.topMargin = dp(6);
        dialogView.addView(coordsTv, coordsLp);

        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(
                dp(36), dp(36));
        spinnerLp.gravity = Gravity.CENTER_HORIZONTAL;
        spinnerLp.topMargin = dp(14);
        dialogView.addView(spinner, spinnerLp);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("🚪 Kapı Hafıza " + targetSlot + " Eşitleme")
                .setView(dialogView)
                .setPositiveButton("⚡ Şu Anki Konumu Kaydet", null)
                .setNegativeButton("İptal", (d, w) -> {
                    memoryMgr.stopDoorCalibration();
                })
                .setCancelable(false)
                .create();

        dialog.setOnShowListener(d -> {
            Button saveNowBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (saveNowBtn != null) {
                saveNowBtn.setOnClickListener(v -> {
                    memoryMgr.saveCurrentPositionToSlot(targetSlot);
                });
            }
        });

        memoryMgr.startDoorCalibration(targetSlot, new CarSeatMemoryManager.SeatCalibrationListener() {
            @Override
            public void onSeatPositionUpdate(int slot, int frPosi, int height, int backRest, boolean isMoving) {
                runOnUiThread(() -> {
                    if (isMoving) {
                        statusTv.setText("🔄 Koltuk hareket ediyor... Konum okunuyor...");
                        statusTv.setTextColor(0xFFFBBF24);
                    }
                    coordsTv.setText("Canlı Konum -> Ön/Arka: " + frPosi + ", Yükseklik: " + height + ", Sırtlık: " + backRest);
                });
            }

            @Override
            public void onCalibrationComplete(int slot, int frPosi, int height, int backRest) {
                runOnUiThread(() -> {
                    spinner.setVisibility(View.GONE);
                    statusTv.setText("✅ Konum başarıyla kaydedildi!");
                    statusTv.setTextColor(0xFF34D399);
                    coordsTv.setText("Kaydedilen -> Ön/Arka: " + frPosi + ", Yükseklik: " + height + ", Sırtlık: " + backRest);
                    uiHandler.postDelayed(() -> {
                        try {
                            if (dialog.isShowing()) {
                                dialog.dismiss();
                            }
                        } catch (Throwable ignored) {
                        }
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    }, 1000L);
                });
            }

            @Override
            public void onCalibrationFailed(String reason) {
                runOnUiThread(() -> {
                    spinner.setVisibility(View.GONE);
                    statusTv.setText("⚠️ " + reason);
                    statusTv.setTextColor(0xFFF87171);
                    uiHandler.postDelayed(() -> {
                        try {
                            if (dialog.isShowing()) {
                                dialog.dismiss();
                            }
                        } catch (Throwable ignored) {
                        }
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    }, 2200L);
                });
            }
        });

        dialog.show();
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
        enrollmentMemory = seatMemoryAvailable ? selectedMemory : 0;
        enrollmentSamples = 0;
        enrollmentTemplates.clear();
        progressBar.setProgress(0);
        progressBar.setVisibility(View.VISIBLE);
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
        showFaceStatus(getString(R.string.dms_face_enroll_prompt,
                enrollmentSamples + 1, REQUIRED_SAMPLES));
        if (progressBar != null) {
            progressBar.setProgress(enrollmentSamples);
        }
    }

    private void showFaceStatus(CharSequence text) {
        if (status != null) {
            status.setText(text);
        }
        clusterPreview.setMirrorText(text);
    }

    private void showFaceStatus(int resId) {
        showFaceStatus(getText(resId));
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
                    showFaceStatus(R.string.dms_face_center_prompt);
                } else {
                    showFaceStatus(R.string.dms_face_no_face);
                }
                return;
            }
            FaceDetector.Face face = faces[0];
            rememberFaceCenter(face, rgb565.getWidth(), rgb565.getHeight());
            if (enrollmentName != null) {
                captureEnrollmentSample(rgb565, face);
            } else if (!profiles.isEmpty()) {
                recognizeFace(rgb565, face);
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
            showFaceStatus(R.string.dms_face_adjust_prompt);
            return;
        }
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
            if (seatMemoryAvailable) {
                CarSeatMemoryManager.getInstance(this).syncCurrentFromCar();
            }

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
            String msg = seatMemoryAvailable
                    ? getString(R.string.dms_face_saved, savedName, savedMemory)
                    : getString(R.string.dms_face_saved_name, savedName);
            showFaceStatus(msg);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

            if (seatMemoryAvailable && CarSeatMemoryManager.getInstance(this).isDoorMode()) {
                showSeatCalibrationDialog(savedMemory, () -> {
                    refreshProfilesListUi();
                    Toast.makeText(this, "Yüz profili ve Kapı Hafıza " + savedMemory + " başarıyla eşitlendi!", Toast.LENGTH_LONG).show();
                });
            }
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
                showFaceStatus(seatMemoryAvailable
                        ? getString(R.string.dms_face_match, best.name, best.seatMemory)
                        : getString(R.string.dms_face_match_name, best.name));
                if (!best.name.equals(reportedRecognitionName)) {
                    reportedRecognitionName = best.name;
                    int memorySlot = seatMemoryAvailable ? best.seatMemory : 0;
                    getSharedPreferences("MapControlPrefs", MODE_PRIVATE).edit()
                            .putString(DmsFaceRecognitionManager.PREF_LAST_NAME, best.name)
                            .putInt(DmsFaceRecognitionManager.PREF_LAST_MEMORY, memorySlot)
                            .putString(DmsFaceRecognitionManager.PREF_STATUS,
                                    seatMemoryAvailable
                                            ? getString(R.string.dms_recognition_match,
                                                    best.name, best.seatMemory)
                                            : getString(R.string.dms_recognition_match_name, best.name))
                            .apply();
                    sendBroadcast(new Intent(DmsFaceRecognitionManager.ACTION_STATUS));
                    if (seatMemoryAvailable) {
                        CarSeatMemoryManager.getInstance(this).applySeatMemory(best.seatMemory);
                    }
                }
            } else {
                showFaceStatus(R.string.dms_face_checking);
            }
        } else {
            recognitionCandidate = null;
            recognitionCandidateHits = 0;
            reportedRecognitionName = null;
            showFaceStatus(R.string.dms_face_unknown);
        }
    }

    private void updateProfileStatus() {
        if (profileStoreError) {
            updateEnrollButtonState();
            return;
        }
        if (enrollmentName == null && !profiles.isEmpty()) {
            showFaceStatus(getString(R.string.dms_face_profile_count, profiles.size(),
                    FaceProfileStore.MAX_PROFILES));
        } else if (profiles.isEmpty()) {
            showFaceStatus(seatMemoryAvailable
                    ? R.string.dms_face_no_profiles
                    : R.string.dms_face_no_profiles_no_memory);
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
        publishNeutralToneFrame();
    }

    /**
     * DMS kameranın yeşil IR/renk dengesini ekranda nötr tona çeker.
     * Tanıma hâlâ alttaki ham kareyle çalışır; kayıtlı yüz şablonları değişmez.
     */
    private void publishNeutralToneFrame() {
        if (preview == null || tonePreview == null || !preview.isAvailable()) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastToneFrameAt < TONE_FRAME_INTERVAL_MS) {
            return;
        }
        int viewW = preview.getWidth();
        int viewH = preview.getHeight();
        if (viewW < 2 || viewH < 2) {
            return;
        }
        lastToneFrameAt = now;
        int targetW = Math.min(viewW, 960);
        int targetH = Math.max(1, viewH * targetW / viewW);
        Bitmap raw = preview.getBitmap(targetW, targetH);
        if (raw == null || isBlankFrame(raw)) {
            if (raw != null) {
                raw.recycle();
            }
            if (tonePreview.getVisibility() != View.GONE) {
                tonePreview.setVisibility(View.GONE);
            }
            return;
        }
        try {
            ensureToneFrame(raw.getWidth(), raw.getHeight());
            applyNeutralToneMatrix(raw);
            locateFaceForCentering(raw);
            tonePaint.setColorFilter(new ColorMatrixColorFilter(toneMatrix));
            toneCanvas.setBitmap(toneFrame);
            toneCanvas.drawColor(0xFF000000);
            float dx = toneFrame.getWidth() / 2f - faceCenterX * raw.getWidth();
            float dy = toneFrame.getHeight() / 2f - faceCenterY * raw.getHeight();
            toneCanvas.drawBitmap(raw, dx, dy, tonePaint);
        } finally {
            raw.recycle();
        }
        tonePreview.setImageBitmap(toneFrame);
        if (tonePreview.getVisibility() != View.VISIBLE) {
            tonePreview.setVisibility(View.VISIBLE);
        }
        tonePreview.invalidate();
        clusterPreview.setFrame(toneFrame);
    }

    private boolean isBlankFrame(Bitmap frame) {
        int width = frame.getWidth();
        int height = frame.getHeight();
        if (width < 2 || height < 2) {
            return true;
        }
        int samples = 0;
        int lit = 0;
        int stepX = Math.max(1, width / 8);
        int stepY = Math.max(1, height / 8);
        for (int y = stepY; y < height; y += stepY) {
            for (int x = stepX; x < width; x += stepX) {
                int color = frame.getPixel(x, y);
                int luma = (((color >> 16) & 0xFF) + ((color >> 8) & 0xFF) + (color & 0xFF)) / 3;
                samples++;
                if (luma > 12) {
                    lit++;
                }
            }
        }
        return samples == 0 || lit * 5 < samples;
    }

    private void ensureToneFrame(int width, int height) {
        if (toneFrame != null
                && toneFrame.getWidth() == width
                && toneFrame.getHeight() == height
                && !toneFrame.isRecycled()) {
            return;
        }
        if (toneFrame != null && !toneFrame.isRecycled()) {
            tonePreview.setImageBitmap(null);
            toneFrame.recycle();
        }
        toneFrame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }

    private void applyNeutralToneMatrix(Bitmap raw) {
        int width = raw.getWidth();
        int height = raw.getHeight();
        if (toneSampleRow == null || toneSampleRow.length < width) {
            toneSampleRow = new int[width];
        }
        int step = Math.max(1, Math.min(width, height) / 24);
        long sumR = 0;
        long sumG = 0;
        long sumB = 0;
        int count = 0;
        int y0 = height / 5;
        int y1 = height - y0;
        int x0 = width / 5;
        int x1 = width - x0;
        for (int y = y0; y < y1; y += step) {
            raw.getPixels(toneSampleRow, 0, width, 0, y, width, 1);
            for (int x = x0; x < x1; x += step) {
                int color = toneSampleRow[x];
                sumR += (color >> 16) & 0xFF;
                sumG += (color >> 8) & 0xFF;
                sumB += color & 0xFF;
                count++;
            }
        }
        if (count == 0) {
            toneMatrix.setSaturation(0f);
            return;
        }
        float meanR = sumR / (float) count;
        float meanG = sumG / (float) count;
        float meanB = sumB / (float) count;
        boolean greenDominant = meanG > 12f
                && meanG > meanR * 1.35f
                && meanG > meanB * 1.35f;
        if (!greenDominant) {
            toneMatrix.reset();
            return;
        }
        // DMS IR görüntüsü yeşil kanalda durur. Doygunluğu alınca normal gri ton kalır.
        toneMatrix.setSaturation(0f);
        toneMatrix.postConcat(new ColorMatrix(new float[]{
                1.18f, 0, 0, 0, -16f,
                0, 1.18f, 0, 0, -16f,
                0, 0, 1.18f, 0, -16f,
                0, 0, 0, 1, 0
        }));
    }

    private void rememberFaceCenter(FaceDetector.Face face, int frameWidth, int frameHeight) {
        if (face == null || frameWidth < 2 || frameHeight < 2) {
            return;
        }
        PointF mid = new PointF();
        face.getMidPoint(mid);
        float nx = mid.x / frameWidth;
        float ny = (mid.y + face.eyesDistance() * 0.35f) / frameHeight;
        smoothFaceCenter(clampUnit(nx), clampUnit(ny));
    }

    private void locateFaceForCentering(Bitmap source) {
        long now = SystemClock.uptimeMillis();
        if (now - lastFaceLocateAt < 220L) {
            return;
        }
        lastFaceLocateAt = now;
        Bitmap scaled = Bitmap.createScaledBitmap(source, FRAME_WIDTH, FRAME_HEIGHT, true);
        Bitmap rgb565 = scaled.getConfig() == Bitmap.Config.RGB_565
                ? scaled
                : scaled.copy(Bitmap.Config.RGB_565, false);
        if (rgb565 != scaled) {
            scaled.recycle();
        }
        if (rgb565 == null) {
            return;
        }
        try {
            if (faceDetector == null) {
                faceDetector = new FaceDetector(FRAME_WIDTH, FRAME_HEIGHT, 1);
            }
            FaceDetector.Face[] faces = new FaceDetector.Face[1];
            int count = faceDetector.findFaces(rgb565, faces);
            if (count == 1 && faces[0] != null && faces[0].confidence() >= 0.35f) {
                rememberFaceCenter(faces[0], rgb565.getWidth(), rgb565.getHeight());
            } else if (faceCenterKnown) {
                faceCenterX = faceCenterX * 0.82f + 0.5f * 0.18f;
                faceCenterY = faceCenterY * 0.82f + 0.5f * 0.18f;
            }
        } finally {
            rgb565.recycle();
        }
    }

    private void smoothFaceCenter(float nx, float ny) {
        if (!faceCenterKnown) {
            faceCenterX = nx;
            faceCenterY = ny;
            faceCenterKnown = true;
            return;
        }
        faceCenterX = faceCenterX * 0.62f + nx * 0.38f;
        faceCenterY = faceCenterY * 0.62f + ny * 0.38f;
    }

    private static float clampUnit(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    private void releaseToneFrame() {
        if (tonePreview != null) {
            tonePreview.setImageBitmap(null);
        }
        if (toneFrame != null && !toneFrame.isRecycled()) {
            toneFrame.recycle();
        }
        toneFrame = null;
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
    protected void onResume() {
        super.onResume();
        clusterPreview.show(this);
    }

    @Override
    protected void onPause() {
        CarSeatMemoryManager.getInstance(this).stopDoorCalibration();
        clusterPreview.hide();
        super.onPause();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        recreate();
    }

    @Override
    protected void onDestroy() {
        closeCamera();
        clusterPreview.hide();
        releaseToneFrame();
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
}

