package com.mapcontrol.service;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.FaceDetector;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.mapcontrol.R;
import com.mapcontrol.vehicle.CarSeatMemoryManager;
import com.mapcontrol.vehicle.DmsFaceMatcher;
import com.mapcontrol.vehicle.FaceProfileStore;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * DMS (Driver Monitoring System) Kamera ile Sürücü Yüz Tanıma Yöneticisi.
 * Bağımsız bir Android Service yerine MapControlService bünyesinde bir Manager olarak
 * çalışır. Bu sayede uygulama içi güncellemelerde ek servis ve FOREGROUND_SERVICE_CAMERA
 * izin gereksinimleri oluşmaz; kullanıcılar doğrudan güncelleme yapabilir.
 */
public final class DmsFaceRecognitionManager {
    private static final String TAG = "DmsFaceRecognition";
    public static final String PREF_ENABLED = "dmsFaceRecognitionEnabled";
    public static final String PREF_LAST_NAME = "dmsFaceRecognitionLastName";
    public static final String PREF_LAST_MEMORY = "dmsFaceRecognitionLastMemory";
    public static final String PREF_STATUS = "dmsFaceRecognitionStatus";
    public static final String ACTION_STATUS = "com.mapcontrol.DMS_FACE_RECOGNITION_STATUS";

    private static final String PREFS = "MapControlPrefs";
    private static final int FRAME_WIDTH = DmsFaceMatcher.FRAME_WIDTH;
    private static final int FRAME_HEIGHT = DmsFaceMatcher.FRAME_HEIGHT;
    private static final long ANALYSIS_INTERVAL_MS = 600;
    private static final long POWER_MODE_2_SCAN_WINDOW_MS = 20_000L;

    private static volatile DmsFaceRecognitionManager sInstance;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private boolean cameraOpening;
    private long nextCameraRetryAtMs;
    private FaceDetector faceDetector;

    private volatile List<FaceProfileStore.Profile> profiles;
    private boolean profileReceiverRegistered;
    private boolean recognitionStarted;
    private volatile boolean sessionRecognized;
    private volatile int lastPowerMode = Integer.MIN_VALUE;
    private volatile long powerMode2EnteredAtMs;

    private long lastAnalysisAt;
    private String candidateName;
    private int candidateHits;
    private String lastPublishedName = "";

    public static DmsFaceRecognitionManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (DmsFaceRecognitionManager.class) {
                if (sInstance == null) {
                    sInstance = new DmsFaceRecognitionManager(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private DmsFaceRecognitionManager(Context context) {
        this.context = context;
        CarSeatMemoryManager.getInstance(context).ensureInitialized();
    }

    public static void start(Context context) {
        getInstance(context).startRecognition();
    }

    public static void stop(Context context) {
        getInstance(context).stopRecognition();
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_ENABLED, false);
    }

    public synchronized void startRecognition() {
        if (recognitionStarted) {
            return;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            publishStatus(context.getString(R.string.dms_recognition_camera_permission));
            return;
        }
        try {
            profiles = FaceProfileStore.load(context);
            if (profiles.isEmpty()) {
                publishStatus(context.getString(R.string.dms_recognition_no_profiles));
                return;
            }

            recognitionStarted = true;

            IntentFilter filter = new IntentFilter("com.mapcontrol.DMS_FACE_PROFILES_CHANGED");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(profileChanges, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(profileChanges, filter);
            }
            profileReceiverRegistered = true;
            startPowerModeMonitoring();
        } catch (IllegalStateException e) {
            publishStatus(context.getString(R.string.dms_recognition_error, e.getMessage()));
            stopRecognition();
        }
    }

    public synchronized void stopRecognition() {
        if (!recognitionStarted) {
            return;
        }
        recognitionStarted = false;
        if (cameraHandler != null) {
            cameraHandler.removeCallbacks(powerModePoll);
        }
        if (profileReceiverRegistered) {
            try {
                context.unregisterReceiver(profileChanges);
            } catch (Exception ignored) {
            }
            profileReceiverRegistered = false;
        }
        closeDmsCamera();
        if (cameraThread != null) {
            cameraThread.quitSafely();
            cameraThread = null;
            cameraHandler = null;
        }
        faceDetector = null;
        candidateName = null;
        candidateHits = 0;
        lastPublishedName = "";
        sessionRecognized = false;
    }

    private final android.content.BroadcastReceiver profileChanges = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            try {
                profiles = FaceProfileStore.load(ctx);
                candidateName = null;
                candidateHits = 0;
                lastPublishedName = "";
                sessionRecognized = false;
                publishStatus(context.getString(R.string.dms_recognition_profiles_updated));
            } catch (IllegalStateException e) {
                publishStatus(context.getString(R.string.dms_recognition_error, e.getMessage()));
            }
        }
    };

    private void startPowerModeMonitoring() {
        if (cameraThread == null) {
            cameraThread = new HandlerThread("DmsFaceRecognition");
            cameraThread.start();
            cameraHandler = new Handler(cameraThread.getLooper());
        }
        cameraHandler.removeCallbacks(powerModePoll);
        cameraHandler.post(powerModePoll);
        publishStatus(context.getString(R.string.dms_recognition_waiting_for_power));
    }

    private boolean isScanAllowedNow() {
        if (sessionRecognized) {
            return false;
        }
        if (lastPowerMode == 1) {
            return true;
        }
        if (lastPowerMode == 2 && powerMode2EnteredAtMs > 0) {
            return (System.currentTimeMillis() - powerMode2EnteredAtMs) <= POWER_MODE_2_SCAN_WINDOW_MS;
        }
        return false;
    }

    private void openDmsCamera() {
        if (cameraHandler == null || !isScanAllowedNow() || cameraOpening
                || cameraDevice != null || System.currentTimeMillis() < nextCameraRetryAtMs) {
            return;
        }
        cameraOpening = true;
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            cameraOpening = false;
            return;
        }
        try {
            String selectedId = null;
            Size selectedSize = null;
            for (String id : manager.getCameraIdList()) {
                if ("0".equals(id)) {
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                    android.hardware.camera2.params.StreamConfigurationMap map =
                            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map != null) {
                        Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
                        if (sizes != null && sizes.length > 0) {
                            selectedId = id;
                            selectedSize = sizes[0];
                            break;
                        }
                    }
                }
            }
            if (selectedId == null || selectedSize == null) {
                cameraOpening = false;
                nextCameraRetryAtMs = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5);
                publishStatus(context.getString(R.string.dms_recognition_camera_missing));
                return;
            }
            imageReader = ImageReader.newInstance(selectedSize.getWidth(),
                    selectedSize.getHeight(), ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = reader.acquireLatestImage();
                if (image == null) {
                    return;
                }
                try {
                    if (!isScanAllowedNow()) {
                        return;
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastAnalysisAt >= ANALYSIS_INTERVAL_MS) {
                        lastAnalysisAt = now;
                        analyze(image);
                    }
                } catch (RuntimeException e) {
                    publishStatus(context.getString(R.string.dms_recognition_error, e.getMessage()));
                } finally {
                    image.close();
                }
            }, cameraHandler);
            manager.openCamera(selectedId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraOpening = false;
                    if (!isScanAllowedNow() || !recognitionStarted) {
                        camera.close();
                        closeImageReader();
                        return;
                    }
                    cameraDevice = camera;
                    createCaptureSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    if (cameraDevice == camera) {
                        cameraDevice = null;
                    }
                    cameraOpening = false;
                    closeImageReader();
                    nextCameraRetryAtMs = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5);
                    if (isScanAllowedNow()) {
                        publishStatus(context.getString(R.string.dms_recognition_camera_disconnected));
                    }
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                    if (cameraDevice == camera) {
                        cameraDevice = null;
                    }
                    cameraOpening = false;
                    closeImageReader();
                    nextCameraRetryAtMs = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5);
                    if (isScanAllowedNow()) {
                        publishStatus(context.getString(R.string.dms_recognition_error,
                                context.getString(R.string.dms_face_camera_error, String.valueOf(error))));
                    }
                }
            }, cameraHandler);
        } catch (CameraAccessException | SecurityException e) {
            cameraOpening = false;
            nextCameraRetryAtMs = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5);
            publishStatus(context.getString(R.string.dms_recognition_error, e.getMessage()));
        }
    }

    private void createCaptureSession() {
        if (cameraDevice == null || imageReader == null) {
            return;
        }
        try {
            Surface surface = imageReader.getSurface();
            CaptureRequest.Builder request = cameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW);
            request.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (!isScanAllowedNow() || cameraDevice == null) {
                                session.close();
                                return;
                            }
                            captureSession = session;
                            try {
                                request.set(CaptureRequest.CONTROL_MODE,
                                        CaptureRequest.CONTROL_MODE_AUTO);
                                session.setRepeatingRequest(request.build(), null, cameraHandler);
                                publishStatus(context.getString(R.string.dms_recognition_running));
                            } catch (CameraAccessException e) {
                                publishStatus(context.getString(R.string.dms_recognition_error,
                                        e.getMessage()));
                                closeDmsCamera();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            publishStatus(context.getString(R.string.dms_recognition_camera_disconnected));
                            closeDmsCamera();
                        }
                    }, cameraHandler);
        } catch (CameraAccessException e) {
            publishStatus(context.getString(R.string.dms_recognition_error, e.getMessage()));
            closeDmsCamera();
        }
    }

    private void closeDmsCamera() {
        if (captureSession != null) {
            try {
                captureSession.close();
            } catch (RuntimeException ignored) {
            }
            captureSession = null;
        }
        if (cameraDevice != null) {
            CameraDevice camera = cameraDevice;
            cameraDevice = null;
            try {
                camera.close();
            } catch (RuntimeException ignored) {
            }
        }
        closeImageReader();
        faceDetector = null;
        candidateName = null;
        candidateHits = 0;
    }

    private void closeImageReader() {
        if (imageReader != null) {
            imageReader.setOnImageAvailableListener(null, null);
            imageReader.close();
            imageReader = null;
        }
    }

    private void analyze(Image image) {
        Bitmap frame = toBitmap(image);
        if (frame == null) {
            return;
        }
        try {
            Bitmap rgb565 = frame.copy(Bitmap.Config.RGB_565, false);
            if (rgb565 == null) {
                return;
            }
            try {
                if (faceDetector == null) {
                    faceDetector = new FaceDetector(rgb565.getWidth(), rgb565.getHeight(), 1);
                }
                FaceDetector.Face[] faces = new FaceDetector.Face[1];
                int count = faceDetector.findFaces(rgb565, faces);
                if (count != 1 || faces[0] == null || faces[0].confidence() < 0.35f) {
                    candidateName = null;
                    candidateHits = 0;
                    return;
                }
                List<FaceProfileStore.Profile> currentProfiles = profiles;
                if (currentProfiles == null || currentProfiles.isEmpty()) {
                    return;
                }
                DmsFaceMatcher.Match match =
                        DmsFaceMatcher.findMatch(rgb565, faces[0], currentProfiles);
                if (match == null) {
                    candidateName = null;
                    candidateHits = 0;
                    return;
                }
                if (match.profile.name.equals(candidateName)) {
                    candidateHits++;
                } else {
                    candidateName = match.profile.name;
                    candidateHits = 1;
                }
                if (candidateHits >= 2
                        && !match.profile.name.equals(lastPublishedName)) {
                    lastPublishedName = match.profile.name;
                    sessionRecognized = true;
                    SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                    prefs.edit()
                            .putString(PREF_LAST_NAME, match.profile.name)
                            .putInt(PREF_LAST_MEMORY, match.profile.seatMemory)
                            .apply();
                    String matchedMsg = context.getString(R.string.dms_recognition_match,
                            match.profile.name, match.profile.seatMemory);
                    publishStatus(matchedMsg);
                    requestSeatMemoryWithRetry(match.profile.seatMemory, 5);
                    // Sürücü tespit edilip hafıza tetiklendiğinde kamerayı hemen serbest bırak
                    closeDmsCamera();
                }
            } finally {
                rgb565.recycle();
            }
        } finally {
            frame.recycle();
        }
    }

    private void requestSeatMemoryWithRetry(int seatMemory, int retriesLeft) {
        if (seatMemory < 1 || seatMemory > FaceProfileStore.MAX_PROFILES) {
            logOnly(context.getString(R.string.dms_recognition_memory_not_requested,
                    context.getString(R.string.dms_recognition_memory_invalid)));
            return;
        }
        if (lastPowerMode != 1 && lastPowerMode != 2) {
            logOnly(context.getString(R.string.dms_recognition_memory_not_requested,
                    context.getString(R.string.dms_recognition_memory_unsafe_state)));
            return;
        }
        CarInfoProxy proxy = CarInfoProxy.getInstance();
        if (!proxy.isServiceConnnected()) {
            proxy.init(context);
            if (retriesLeft > 0 && cameraHandler != null) {
                cameraHandler.postDelayed(
                        () -> requestSeatMemoryWithRetry(seatMemory, retriesLeft - 1), 1000L);
            } else {
                logOnly(context.getString(R.string.dms_recognition_memory_not_requested,
                        context.getString(R.string.dms_recognition_memory_disconnected)));
            }
            return;
        }
        try {
            boolean applied = CarSeatMemoryManager.getInstance(context)
                    .applySeatMemory(seatMemory);
            if (applied) {
                logOnly(context.getString(R.string.dms_recognition_memory_requested, seatMemory));
            } else {
                proxy.sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING,
                        CarSettingID.ID_CAR_SELECT_SEAT_MIRROR_POSI,
                        new int[]{seatMemory, seatMemory});
                logOnly(context.getString(R.string.dms_recognition_memory_requested, seatMemory));
            }
        } catch (RuntimeException e) {
            logOnly(context.getString(R.string.dms_recognition_memory_not_requested,
                    context.getString(R.string.dms_recognition_memory_error, e.getMessage())));
        }
    }

    private Bitmap toBitmap(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yPlane = planes[0].getBuffer();
        int yRowStride = planes[0].getRowStride();
        int yPixelStride = planes[0].getPixelStride();
        int yLimit = yPlane.limit();
        int[] pixels = new int[width * height];
        for (int row = 0; row < height; row++) {
            int yRow = yRowStride * row;
            for (int col = 0; col < width; col++) {
                int idx = yRow + col * yPixelStride;
                int y = (idx >= 0 && idx < yLimit) ? (yPlane.get(idx) & 0xFF) : 0;
                pixels[row * width + col] = 0xFF000000 | (y << 16) | (y << 8) | y;
            }
        }
        Bitmap color = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        if (width == FRAME_WIDTH && height == FRAME_HEIGHT) {
            return color;
        }
        Bitmap scaled = Bitmap.createScaledBitmap(color, FRAME_WIDTH, FRAME_HEIGHT, true);
        color.recycle();
        return scaled;
    }

    private final Runnable powerModePoll = new Runnable() {
        @Override
        public void run() {
            if (cameraHandler == null || !recognitionStarted) {
                return;
            }
            CarInfoProxy proxy = CarInfoProxy.getInstance();
            if (!proxy.isServiceConnnected()) {
                try {
                    proxy.init(context);
                } catch (RuntimeException ignored) {
                }
            } else {
                CarSeatMemoryManager.getInstance(context).ensureInitialized();
                int[] values = proxy.getItemValues(VDEventCarInfo.MODULE_READONLY_INFO,
                        ReadOnlyID.ID_SYSTEM_POWER_MODE);
                if (values != null && values.length > 0) {
                    onPowerModeChanged(values[0]);
                }
            }
            if (isScanAllowedNow()) {
                openDmsCamera();
            } else if (cameraDevice != null) {
                closeDmsCamera();
                if (!sessionRecognized && lastPowerMode >= 2) {
                    publishStatus(context.getString(R.string.dms_recognition_camera_released));
                }
            }
            cameraHandler.postDelayed(this, TimeUnit.SECONDS.toMillis(1));
        }
    };

    public void onPowerModeChanged(int mode) {
        int previous = lastPowerMode;
        if (mode == previous) {
            return;
        }
        lastPowerMode = mode;
        if (mode == 1) {
            powerMode2EnteredAtMs = 0L;
            if (previous <= 0) {
                beginWelcomeSession(mode);
            }
            openDmsCamera();
        } else if (mode == 2) {
            powerMode2EnteredAtMs = System.currentTimeMillis();
            if (previous <= 0) {
                beginWelcomeSession(mode);
            }
            if (!sessionRecognized) {
                openDmsCamera();
            } else {
                closeDmsCamera();
            }
        } else if (mode <= 0) {
            powerMode2EnteredAtMs = 0L;
            closeDmsCamera();
            sessionRecognized = false;
            lastPublishedName = "";
            candidateName = null;
            candidateHits = 0;
            clearLastRecognition();
            publishStatus(context.getString(R.string.dms_recognition_ignition_off));
        }
    }

    private void beginWelcomeSession(int powerMode) {
        sessionRecognized = false;
        lastPublishedName = "";
        candidateName = null;
        candidateHits = 0;
        clearLastRecognition();
        publishStatus(context.getString(R.string.dms_recognition_waiting_for_driver, powerMode));
    }

    private void clearLastRecognition() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(PREF_LAST_NAME, "")
                .putInt(PREF_LAST_MEMORY, 0)
                .apply();
    }


    private void logOnly(String message) {
        Log.i(TAG, message);
        Intent intent = new Intent("com.mapcontrol.LOG_MESSAGE");
        intent.putExtra("log_message", "Yüz tanıma: " + message);
        context.sendBroadcast(intent);
    }

    private void publishStatus(String message) {
        Log.i(TAG, message);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(PREF_STATUS, message).apply();
        Intent statusIntent = new Intent(ACTION_STATUS);
        context.sendBroadcast(statusIntent);
        Intent intent = new Intent("com.mapcontrol.LOG_MESSAGE");
        intent.putExtra("log_message", "Yüz tanıma: " + message);
        context.sendBroadcast(intent);
    }
}
