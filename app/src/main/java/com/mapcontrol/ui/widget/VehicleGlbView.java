package com.mapcontrol.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.filament.Box;
import com.google.android.filament.ColorGrading;
import com.google.android.filament.Engine;
import com.google.android.filament.EntityManager;
import com.google.android.filament.IndirectLight;
import com.google.android.filament.LightManager;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.Renderer;
import com.google.android.filament.Texture;
import com.google.android.filament.ToneMapper;
import com.google.android.filament.TransformManager;
import com.google.android.filament.android.UiHelper;
import com.google.android.filament.gltfio.FilamentAsset;
import com.google.android.filament.utils.Float3;
import com.google.android.filament.utils.Manipulator;
import com.google.android.filament.utils.ModelViewer;
import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Launcher orta kartı için GLB araç modeli — Filament ile render.
 *
 * Tesla tarzı 3D inspect: araç sabit, sürükleyince kamera etrafında döner (inertia'lı).
 */
public final class VehicleGlbView extends FrameLayout {

    private static final String TAG = "VehicleGlbView";
    private static final String MODEL_ASSET = "models/2023_chery_tiggo_7_plus_290t.glb";

    /** SÜRÜŞ simülasyonu: hedef hız (km/h). */
    public static final float SIMULATED_SPEED_KMH = 60f;
    /** 0 → 60 yaklaşık 4 sn. */
    private static final float SIM_ACCEL_KMH_PER_SEC = 15f;
    /** Yaklaşık lastik yarıçapı (m) — açısal hız = v / r. */
    private static final float WHEEL_RADIUS_M = 0.35f;
    private static final String[] WHEEL_PART_NAMES = {
            "hub_tire_map_c_B",
            "hub_tire_map_c_A",
            "hub_Brake_Disc_Map_c",
            "hub_metal_B",
            "hub_gray_metal_B",
            "hub_black_metal_B",
            "hub_metal_A",
            "hub_gray_metal_A",
            "hub_black_metal_A",
    };
    /** Mesh uzayında aks = Z (tekerlek XY düzleminde; ön/arka Z boyunca). */
    private static final float WHEEL_AXIS_X = 0f;
    private static final float WHEEL_AXIS_Y = 0f;
    private static final float WHEEL_AXIS_Z = 1f;

    private static final String[] TRUNK_PART_NAMES = {
            "trunk_cheqi",
            "trunk_deng_map_c",
            "trunk_inner_map_c",
            "trunk_glass",
            "trunk_black_plastic",
            "trunk_logo_red_metal",
            "trunk_logo_metal_1",
            "trunk_logo_metal",
            "trunk_plastic",
            "weideng_glass",
            "weideng_black_plastic",
            "weideng_red_plastic",
            "weideng_plastic",
            "weideng_map_c",
    };
    private static final String TRUNK_LID_NAME = "trunk_cheqi";
    /** SUV hatch: menteşe üst kenar, Z aksı etrafında açılış. */
    private static final float TRUNK_OPEN_DEG = 85f;
    private static final long TRUNK_ANIM_MS = 900L;
    /** Bagaj açılınca kamera ~180° arkaya (orbit yaw, radyan). */
    private static final float TRUNK_VIEW_YAW = (float) Math.PI;
    private static final float TRUNK_VIEW_PITCH = 0.12f;
    private static final long TRUNK_CAMERA_MS = 950L;

    /**
     * Başlangıç / home orbit — bagaj mesafesi hissi, ön-çapraz (GTA brochure).
     * yaw≈0 → ön; negatif yaw → burun sola / araç sağa çapraz.
     * pitch↑ → daha yüksek bakış.
     */
    private static final float HOME_VIEW_YAW = -0.42f;
    private static final float HOME_VIEW_PITCH = 0.34f;

    /** Cam tavan (top_glass_B) — +X = araç arkası, kayarak açılır. */
    private static final String SUNROOF_PART_NAME = "top_glass_B";
    private static final float SUNROOF_SLIDE_X = 0.55f;
    private static final long SUNROOF_ANIM_MS = 800L;

    /**
     * Kapı grupları — LF/RF/LR/RR ayrı (araç sinyali veya tuş).
     * Sol (−Y), sağ (+Y) dışa açılır; menteşe panel min-X (ön kenar).
     */
    private static final DoorSpec[] DOOR_SPECS = {
            new DoorSpec("LF", -65f, new float[]{-0.877f, 0.93f, 0.819f},
                    "lf_door_cheqi_1",
                    new String[]{
                            "lf_door_cheqi", "lf_door_cheqi_1", "lf_door_black_plastic",
                            "lf_door_daochejing", "lf_door_map_c", "lf_door_metal",
                            "lf_door_plastic", "lf_door_glass", "lf_door_plastic_or_metal",
                            "lf_door_black_plastic_or_plastic", "lf_door_yaguang_metal",
                    }),
            new DoorSpec("RF", 65f, new float[]{-0.877f, 0.93f, -0.819f},
                    "rf_door_cheqi_1",
                    new String[]{
                            "rf_door_cheqi", "rf_door_cheqi_1", "rf_door_black_plastic",
                            "rf_door_daochejing", "rf_door_map_c", "rf_door_metal",
                            "rf_door_plastic", "rf_door_glass", "rf_door_plastic_or_metal",
                            "rf_door_black_plastic_or_plastic", "rf_door_yaguang_metal",
                    }),
            new DoorSpec("LR", -65f, new float[]{0.144f, 0.928f, 0.748f},
                    "lr_door_cheqi_1",
                    new String[]{
                            "lr_door_cheqi", "lr_door_cheqi_1",
                            "lr_door_black_plastic_or_plastic", "lr_door_map_c",
                            "lr_door_metal", "lr_door_metal_or_plastic", "lr_door_plastic",
                            "lr_door_glass", "lr_door_yaguang_metal",
                    }),
            new DoorSpec("RR", 65f, new float[]{0.144f, 0.928f, -0.748f},
                    "rr_door_cheqi_1",
                    new String[]{
                            "rr_door_cheqi", "rr_door_cheqi_1",
                            "rr_door_black_plastic_or_plastic", "rr_door_map_c",
                            "rr_door_metal", "rr_door_metal_or_plastic", "rr_door_plastic",
                            "rr_door_glass", "rr_door_yaguang_metal",
                    }),
    };
    private static final long DOOR_ANIM_MS = 750L;

    private static final class DoorSpec {
        final String id;
        final float openDeg;
        final float[] fallbackHinge;
        final String lidName;
        final String[] partNames;

        DoorSpec(String id, float openDeg, float[] fallbackHinge, String lidName, String[] partNames) {
            this.id = id;
            this.openDeg = openDeg;
            this.fallbackHinge = fallbackHinge;
            this.lidName = lidName;
            this.partNames = partNames;
        }
    }

    private static final class DoorRuntime {
        final DoorSpec spec;
        final Map<Integer, float[]> restTransforms = new HashMap<>();
        float[] hingeLocal;
        boolean openTarget;
        float openFraction;
        ValueAnimator animator;

        DoorRuntime(DoorSpec spec) {
            this.spec = spec;
        }
    }

    // -------------------------------------------------------------------------
    // POSE varsayılanları (UI'dan runtime'da değişir)
    // -------------------------------------------------------------------------
    private static final float DEFAULT_CENTER_X = 0f;
    private static final float DEFAULT_CENTER_Y = 0f;
    private static final float DEFAULT_CENTER_Z = -2.3f;
    private static final float DEFAULT_OFFSET_X = 0f;
    private static final float DEFAULT_OFFSET_Y = 0f;
    private static final float DEFAULT_OFFSET_Z = 0f;
    private static final float DEFAULT_YAW_DEG = 0f;
    private static final float DEFAULT_PITCH_DEG = 0f;
    private static final float DEFAULT_ROLL_DEG = 0f;
    private static final float DEFAULT_SCALE = 1f;
    /**
     * Home eye — scale değiştirmeden yaklaştır (orbit bozulmasın).
     * Daha küçük eye mesafesi = ekranda daha büyük araç.
     */
    private static final float DEFAULT_CAMERA_EYE_X = 0f;
    private static final float DEFAULT_CAMERA_EYE_Y = 0.38f;
    private static final float DEFAULT_CAMERA_EYE_Z = 0.30f;

    /** Interior: kullanıcı ince ayarı. */
    private static final float INTERIOR_CENTER_X = 0f;
    private static final float INTERIOR_CENTER_Y = 0f;
    private static final float INTERIOR_CENTER_Z = -0.5f;
    private static final float INTERIOR_OFFSET_X = 0f;
    private static final float INTERIOR_OFFSET_Y = -0.13f;
    private static final float INTERIOR_OFFSET_Z = 0.9f;
    private static final float INTERIOR_YAW_DEG = 180f;
    private static final float INTERIOR_PITCH_DEG = 0f;
    private static final float INTERIOR_ROLL_DEG = 0f;
    private static final float INTERIOR_SCALE = 1f;
    private static final float INTERIOR_EYE_X = 2f;
    private static final float INTERIOR_EYE_Y = 0f;
    private static final float INTERIOR_EYE_Z = 0f;

    private float modelCenterX = DEFAULT_CENTER_X;
    private float modelCenterY = DEFAULT_CENTER_Y;
    private float modelCenterZ = DEFAULT_CENTER_Z;
    private float modelOffsetX = DEFAULT_OFFSET_X;
    private float modelOffsetY = DEFAULT_OFFSET_Y;
    private float modelOffsetZ = DEFAULT_OFFSET_Z;
    private float modelYawDeg = DEFAULT_YAW_DEG;
    private float modelPitchDeg = DEFAULT_PITCH_DEG;
    private float modelRollDeg = DEFAULT_ROLL_DEG;
    private float modelScale = DEFAULT_SCALE;
    private float cameraEyeX = DEFAULT_CAMERA_EYE_X;
    private float cameraEyeY = DEFAULT_CAMERA_EYE_Y;
    private float cameraEyeZ = DEFAULT_CAMERA_EYE_Z;
    private boolean interiorMode;
    private PoseConfig exteriorPoseBackup;

    // -------------------------------------------------------------------------
    // ORBIT (Tesla inspect) — araç sabit, kamera döner
    // -------------------------------------------------------------------------
    /**
     * Filament Manipulator orbitSpeed (radyan / grab-px).
     * Küçük değer = grab eşlemesinde daha ince çözünürlük (piksel basamak azalır).
     */
    private static final float ORBIT_MANIP_SPEED = 0.0025f;
    /** Dokunma hassasiyeti: radyan / density-independent px (dp). */
    private static final float ORBIT_TOUCH_RAD_PER_DP = 0.0055f;
    /** Bırakınca yavaşlama (0.82–0.92); frame başına üssel. */
    private static final float ORBIT_INERTIA = 0.86f;
    /** Maks fling (rad/s) — bırakınca fırlamayı keser. */
    private static final float ORBIT_FLING_MAX_RAD_S = 2.2f;
    /** Bu hızın altında fling yok (rad/s). */
    private static final float ORBIT_FLING_MIN_RAD_S = 0.25f;
    /** Görünen orbit → hedefe lerp (1/s); dokunma basamaklarını yumuşatır. */
    private static final float ORBIT_DISPLAY_SMOOTH = 22f;
    private static final float ORBIT_PITCH_MIN = -0.35f;
    private static final float ORBIT_PITCH_MAX = 0.62f;

    private static final float AMBIENT_LIGHT_INTENSITY = 35_000f;
    private static final float KEY_LIGHT_INTENSITY = 140_000f;
    private static final float FILL_LIGHT_INTENSITY = 32_000f;
    /** HDR yansımalar için daha yüksek çözünürlüklü stüdyo IBL. */
    private static final int STUDIO_CUBEMAP_SIZE = 128;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /**
     * ModelViewer detach'te Engine'i yok eder ve SurfaceView'a kalıcı listener bırakır.
     * Yeniden kurulumda aynı SurfaceView kullanılamaz — her döngüde yenilenir.
     */
    private SurfaceView surfaceView;
    private final View loadingOverlay;
    private final List<Integer> fillLightEntities = new ArrayList<>();
    private ProgressBar loadingProgressBar;
    private TextView loadingLabel;
    private TextView touchHudLabel;
    private PopupWindow touchHudWindow;
    private Engine engine;
    private UiHelper uiHelper;
    private ModelViewer modelViewer;
    private Manipulator cameraManipulator;
    private IndirectLight ambientLight;
    private Texture reflectionCubemap;
    private ColorGrading colorGrading;
    /** OEM sürüş modu — ışık tint (kamera dokunulmaz). */
    private int driveModeRaw = Integer.MIN_VALUE;
    private Choreographer choreographer;
    /** Sekme çıkışında artar; async GLB yüklemesinin stale sonuçlarını iptal eder. */
    private int viewerGeneration;
    /** ModelViewer detach destroy sonrası aynı SurfaceView yeniden kullanılamaz. */
    private boolean recreateSurfaceOnNextStart;
    /** Park→resume sonrası ilk ready frame'de sahne clear rengini yenile. */
    private boolean pendingSceneBackgroundReapply;
    private boolean active;
    private boolean modelRevealed;
    private boolean transformApplied;
    private boolean materialsAdjusted;
    private boolean initFailed;
    private boolean modelRequested;
    private boolean dragging;
    private boolean pickDebugEnabled;
    private float lastTouchX;
    private float lastTouchY;
    private long lastFrameNanos;
    private android.view.VelocityTracker orbitVelocityTracker;
    private String lastPickedPart = "—";
    /** Unit-cube + pose; kullanıcı dokununca model DÖNMEZ, kamera döner. */
    private float[] baseTransform;
    /** Orbit hedef açıları (radyan). 0 yaw = başlangıç bakışı. */
    private float orbitYaw;
    private float orbitPitch;
    /** Ekranda uygulanan yumuşatılmış açılar. */
    private float orbitYawDisplay;
    private float orbitPitchDisplay;
    /** Fling hızı (rad/s). */
    private float orbitYawVel;
    private float orbitPitchVel;

    /** Gerçek araç hızı (km/h); dashboard snapshot'tan gelir. */
    private float wheelSpeedKmh;
    private boolean wheelSimulationEnabled;
    /** Simülasyon rampası (0 → {@link #SIMULATED_SPEED_KMH}). */
    private float simSpeedKmh;
    private int lastReportedSimSpeed = -1;
    private OnEffectiveWheelSpeedListener wheelSpeedListener;
    private float wheelAngleDeg;
    private boolean wheelPartsResolved;
    private final Map<Integer, float[]> wheelRestTransforms = new HashMap<>();
    private final float[] wheelRotM = new float[16];
    private final float[] wheelOutM = new float[16];

    private boolean trunkPartsResolved;
    private boolean trunkOpenTarget;
    private float trunkOpenFraction;
    private float[] trunkHingeLocal;
    private ValueAnimator trunkAnimator;
    private ValueAnimator trunkCameraAnimator;
    private final Map<Integer, float[]> trunkRestTransforms = new HashMap<>();
    private final float[] trunkRotM = new float[16];
    private final float[] trunkTmpM = new float[16];
    private final float[] trunkSpinM = new float[16];
    private final float[] trunkToPivotM = new float[16];
    private final float[] trunkFromPivotM = new float[16];
    private final float[] trunkOutM = new float[16];

    private boolean sunroofPartsResolved;
    private boolean sunroofOpenTarget;
    private float sunroofOpenFraction;
    private int sunroofEntity;
    private float[] sunroofRestTransform;
    private ValueAnimator sunroofAnimator;
    private final float[] sunroofSlideM = new float[16];
    private final float[] sunroofOutM = new float[16];

    private boolean doorsPartsResolved;
    private final List<DoorRuntime> doorRuntimes = new ArrayList<>();
    /** Model yüklenmeden gelen araç kapı hedefleri (LF/RF/LR/RR). */
    private final Map<String, Boolean> pendingDoorOpen = new HashMap<>();
    private final float[] doorRotM = new float[16];
    private final float[] doorTmpM = new float[16];
    private final float[] doorSpinM = new float[16];
    private final float[] doorToPivotM = new float[16];
    private final float[] doorFromPivotM = new float[16];
    private final float[] doorOutM = new float[16];

    public interface OnEffectiveWheelSpeedListener {
        void onEffectiveWheelSpeedChanged(float kmh);
    }

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!active || modelViewer == null) {
                return;
            }
            float dt = 1f / 60f;
            if (lastFrameNanos != 0L) {
                dt = (frameTimeNanos - lastFrameNanos) / 1_000_000_000f;
                if (dt < 0.001f) {
                    dt = 0.001f;
                } else if (dt > 0.05f) {
                    dt = 0.05f;
                }
            }
            lastFrameNanos = frameTimeNanos;

            updateLoadingUi();
            ensureModelPose();
            tryRevealModel();
            tickSimSpeed(dt);
            tickOrbitInertia(dt);
            tickWheelSpin(dt);
            ensureTrunkPartsResolved();
            applyTrunkTransforms();
            ensureSunroofPartsResolved();
            applySunroofTransforms();
            ensureDoorsPartsResolved();
            applyDoorsTransforms();
            applyOrbitToCamera();
            if (uiHelper != null && !uiHelper.isReadyToRender()) {
                choreographer.postFrameCallback(this);
                return;
            }
            if (pendingSceneBackgroundReapply) {
                applySceneBackground();
                pendingSceneBackgroundReapply = false;
            }
            try {
                modelViewer.render(frameTimeNanos);
            } catch (Throwable error) {
                Log.e(TAG, "Filament render başarısız", error);
            }
            choreographer.postFrameCallback(this);
        }
    };

    public VehicleGlbView(@NonNull Context context) {
        super(context);
        setClipChildren(true);
        applyHostBackground();
        setClickable(true);
        setFocusable(true);

        surfaceView = createSurfaceView(context);
        addView(surfaceView, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        loadingOverlay = buildLoadingOverlay(context);
        addView(loadingOverlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private SurfaceView createSurfaceView(Context context) {
        SurfaceView view = new SurfaceView(context);
        // Surface VISIBLE kalmalı — INVISIBLE olursa UiHelper ready olmaz, asyncLoad asla bitmez.
        // ZOrderOnTop kullanma: araç IVI'de şeffaf surface siyah delik açar.
        view.setZOrderOnTop(false);
        view.setClickable(true);
        view.getHolder().setFormat(PixelFormat.OPAQUE);
        return view;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // Exterior'da orbit için scroll engelle; interior'da kamera kilitli — scroll serbest
        if (modelRevealed && !interiorMode
                && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            requestParentsDisallowIntercept(this, true);
        }
        if (ev.getActionMasked() == MotionEvent.ACTION_UP
                || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            requestParentsDisallowIntercept(this, false);
        }
        return super.dispatchTouchEvent(ev);
    }

    public void onHostStart() {
        if (initFailed) {
            return;
        }
        active = true;
        lastFrameNanos = 0L;

        // Launcher park sonrası: Engine hâlâ canlıysa yeniden yükleme yok.
        if (modelViewer != null && modelRequested && !recreateSurfaceOnNextStart) {
            resumeParkedSurface();
            loadingOverlay.setVisibility(modelRevealed ? GONE : VISIBLE);
            if (choreographer == null) {
                choreographer = Choreographer.getInstance();
            }
            choreographer.postFrameCallback(frameCallback);
            return;
        }

        modelRevealed = false;
        loadingOverlay.setVisibility(VISIBLE);
        if (loadingLabel != null) {
            loadingLabel.setText(R.string.launcher_dashboard_model_loading);
        }
        if (loadingProgressBar != null) {
            loadingProgressBar.setProgress(0);
        }
        if (recreateSurfaceOnNextStart) {
            replaceSurfaceView();
            recreateSurfaceOnNextStart = false;
        }
        surfaceView.setVisibility(VISIBLE);
        surfaceView.setZOrderOnTop(false);
        if (!ensureViewer()) {
            active = false;
            return;
        }
        requestModelLoad();
        if (choreographer == null) {
            choreographer = Choreographer.getInstance();
        }
        choreographer.postFrameCallback(frameCallback);
    }

    public void onHostStop() {
        active = false;
        dragging = false;
        lastFrameNanos = 0L;
        hideTouchHud();
        if (choreographer != null) {
            choreographer.removeFrameCallback(frameCallback);
        }
        // ZOrderOnTop(true) iken SurfaceView tüm window'un üstüne delik açar —
        // Ayarlar vb. sekmelerde araç görünür kalır. Park'ta kapat + GONE.
        parkSurfaceForOverlay();
    }

    /** Sekme üstü overlay sırasında SurfaceView deliğini kapatır (Engine canlı kalır). */
    private void parkSurfaceForOverlay() {
        if (surfaceView == null) {
            return;
        }
        surfaceView.setZOrderOnTop(false);
        surfaceView.setVisibility(GONE);
    }

    /** Park'tan dönüş: surface + kart rengine uyumlu clear yeniden kurulur. */
    private void resumeParkedSurface() {
        if (surfaceView == null) {
            return;
        }
        surfaceView.getHolder().setFormat(PixelFormat.OPAQUE);
        // Opak surface — ZOrderOnTop yok (IVI'de transparan → siyah delik).
        surfaceView.setZOrderOnTop(false);
        surfaceView.setVisibility(VISIBLE);
        applySceneBackground();
        pendingSceneBackgroundReapply = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        active = false;
        if (choreographer != null) {
            choreographer.removeFrameCallback(frameCallback);
        }
        // SurfaceView çocukları önce detach olur → ModelViewer Engine'i yok eder.
        abandonViewerReferences();
        // Eski SurfaceView yeniden attach olursa dead listener tekrar destroy dener → abort.
        // Detach haldeyken taze SurfaceView tak (removeView ikinci detach tetiklemez).
        replaceSurfaceView();
        recreateSurfaceOnNextStart = false;
        super.onDetachedFromWindow();
    }

    private void cancelPartAnimators() {
        if (trunkAnimator != null) {
            trunkAnimator.cancel();
            trunkAnimator = null;
        }
        if (trunkCameraAnimator != null) {
            trunkCameraAnimator.cancel();
            trunkCameraAnimator = null;
        }
        if (sunroofAnimator != null) {
            sunroofAnimator.cancel();
            sunroofAnimator = null;
        }
        for (DoorRuntime door : doorRuntimes) {
            if (door.animator != null) {
                door.animator.cancel();
                door.animator = null;
            }
        }
    }

    /** Eski ModelViewer listener'lı SurfaceView'ı atıp yenisini takar. */
    private void replaceSurfaceView() {
        if (surfaceView != null) {
            surfaceView.setOnTouchListener(null);
            removeView(surfaceView);
        }
        surfaceView = createSurfaceView(getContext());
        addView(surfaceView, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    /** Araç & Medya başlığından aç/kapa. Kapalıyken HUD hiç görünmez. */
    public boolean togglePickDebugEnabled() {
        setPickDebugEnabled(!pickDebugEnabled);
        return pickDebugEnabled;
    }

    public void setPickDebugEnabled(boolean enabled) {
        pickDebugEnabled = enabled;
        if (!pickDebugEnabled) {
            hideTouchHud();
        }
    }

    public boolean isPickDebugEnabled() {
        return pickDebugEnabled;
    }

    /** Dashboard'dan gerçek km/h. Simülasyon açıksa ve hız 0 ise rampa ile {@link #SIMULATED_SPEED_KMH}'e çıkar. */
    public void setWheelSpeedKmh(float kmh) {
        wheelSpeedKmh = Math.max(0f, kmh);
    }

    public float getWheelSpeedKmh() {
        return wheelSpeedKmh;
    }

    /** Efektif tekerlek hızı (simülasyon rampası dahil). */
    public float getEffectiveWheelSpeedKmh() {
        if (wheelSpeedKmh > 0f) {
            return wheelSpeedKmh;
        }
        return simSpeedKmh;
    }

    public boolean toggleWheelSimulation() {
        setWheelSimulationEnabled(!wheelSimulationEnabled);
        return wheelSimulationEnabled;
    }

    public void setWheelSimulationEnabled(boolean enabled) {
        wheelSimulationEnabled = enabled;
        if (enabled && wheelSpeedKmh <= 0f && simSpeedKmh <= 0f) {
            simSpeedKmh = 0f;
            lastReportedSimSpeed = -1;
        }
    }

    public boolean isWheelSimulationEnabled() {
        return wheelSimulationEnabled;
    }

    public void setOnEffectiveWheelSpeedListener(OnEffectiveWheelSpeedListener listener) {
        wheelSpeedListener = listener;
    }

    public boolean isInteriorMode() {
        return interiorMode;
    }

    /** Bagaj aç/kapa (animasyon). true = açık hedef. */
    public boolean toggleTrunkOpen() {
        setTrunkOpen(!trunkOpenTarget);
        return trunkOpenTarget;
    }

    public void setTrunkOpen(boolean open) {
        if (trunkOpenTarget == open && trunkAnimator != null && trunkAnimator.isRunning()) {
            return;
        }
        if (trunkOpenTarget == open
                && ((open && trunkOpenFraction >= 0.999f)
                || (!open && trunkOpenFraction <= 0.001f))) {
            return;
        }
        trunkOpenTarget = open;
        if (trunkAnimator != null) {
            trunkAnimator.cancel();
        }
        float start = trunkOpenFraction;
        float end = open ? 1f : 0f;
        trunkAnimator = ValueAnimator.ofFloat(start, end);
        trunkAnimator.setDuration(Math.max(120L,
                (long) (TRUNK_ANIM_MS * Math.abs(end - start))));
        trunkAnimator.setInterpolator(new DecelerateInterpolator());
        trunkAnimator.addUpdateListener(a ->
                trunkOpenFraction = (float) a.getAnimatedValue());
        trunkAnimator.start();
        if (open) {
            softOrbitCameraTo(TRUNK_VIEW_YAW, TRUNK_VIEW_PITCH, TRUNK_CAMERA_MS);
        }
    }

    /**
     * Orbit yaw/pitch'i yumuşakçe hedefe götürür (en kısa açı yolu).
     * Interior'da no-op.
     */
    private void softOrbitCameraTo(float targetYaw, float targetPitch, long durationMs) {
        if (interiorMode) {
            return;
        }
        if (trunkCameraAnimator != null) {
            trunkCameraAnimator.cancel();
            trunkCameraAnimator = null;
        }
        orbitYawVel = 0f;
        orbitPitchVel = 0f;
        final float startYaw = orbitYaw;
        final float startPitch = orbitPitch;
        final float deltaYaw = shortestAngleDelta(startYaw, targetYaw);
        final float deltaPitch = targetPitch - startPitch;
        if (Math.abs(deltaYaw) < 0.02f && Math.abs(deltaPitch) < 0.02f) {
            return;
        }
        trunkCameraAnimator = ValueAnimator.ofFloat(0f, 1f);
        trunkCameraAnimator.setDuration(durationMs);
        trunkCameraAnimator.setInterpolator(new DecelerateInterpolator(1.6f));
        trunkCameraAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            // Smoothstep
            float s = t * t * (3f - 2f * t);
            orbitYaw = startYaw + deltaYaw * s;
            orbitPitch = startPitch + deltaPitch * s;
            clampOrbitPitch();
        });
        trunkCameraAnimator.start();
    }

    private static float shortestAngleDelta(float from, float to) {
        float fromN = normalizeAngle(from);
        float toN = normalizeAngle(to);
        float d = toN - fromN;
        if (d > Math.PI) {
            d -= (float) (2.0 * Math.PI);
        } else if (d < -Math.PI) {
            d += (float) (2.0 * Math.PI);
        }
        return d;
    }

    private static float normalizeAngle(float angle) {
        double twoPi = 2.0 * Math.PI;
        double a = angle % twoPi;
        if (a > Math.PI) {
            a -= twoPi;
        } else if (a < -Math.PI) {
            a += twoPi;
        }
        return (float) a;
    }

    public boolean isTrunkOpen() {
        return trunkOpenTarget;
    }

    /** Cam tavan aç/kapa (geriye kaydırma). true = açık hedef. */
    public boolean toggleSunroofOpen() {
        setSunroofOpen(!sunroofOpenTarget);
        return sunroofOpenTarget;
    }

    public void setSunroofOpen(boolean open) {
        if (sunroofOpenTarget == open && sunroofAnimator != null && sunroofAnimator.isRunning()) {
            return;
        }
        if (sunroofOpenTarget == open
                && ((open && sunroofOpenFraction >= 0.999f)
                || (!open && sunroofOpenFraction <= 0.001f))) {
            return;
        }
        sunroofOpenTarget = open;
        if (sunroofAnimator != null) {
            sunroofAnimator.cancel();
        }
        float start = sunroofOpenFraction;
        float end = open ? 1f : 0f;
        sunroofAnimator = ValueAnimator.ofFloat(start, end);
        sunroofAnimator.setDuration(Math.max(120L,
                (long) (SUNROOF_ANIM_MS * Math.abs(end - start))));
        sunroofAnimator.setInterpolator(new DecelerateInterpolator());
        sunroofAnimator.addUpdateListener(a ->
                sunroofOpenFraction = (float) a.getAnimatedValue());
        sunroofAnimator.start();
    }

    public boolean isSunroofOpen() {
        return sunroofOpenTarget;
    }

    /**
     * Sürüş moduna göre sahne ışığı / ambient tonu.
     * Kamera / orbit değiştirilmez.
     */
    public void setDriveMode(int driveMode) {
        driveModeRaw = driveMode;
        // Tema renkleri kaldırıldı — ışık tint uygulanmaz
    }

    public int getDriveMode() {
        return driveModeRaw;
    }

    /**
     * Kapı aç/kapa (4 kapı birlikte). Manuel önizleme / test.
     */
    public boolean toggleDoorsOpen() {
        boolean open = !isDoorsOpen();
        setDoorsOpen(open);
        return open;
    }

    public void setDoorsOpen(boolean open) {
        for (DoorSpec spec : DOOR_SPECS) {
            setDoorOpen(spec.id, open);
        }
    }

    /**
     * Tek kapı: {@code LF}/{@code RF}/{@code LR}/{@code RR}.
     * Araç sinyali veya UI; açık→açılma, kapalı→kapanma animasyonu.
     */
    public void setDoorOpen(String doorId, boolean open) {
        if (doorId == null || doorId.isEmpty()) {
            return;
        }
        pendingDoorOpen.put(doorId, open);
        DoorRuntime door = findDoorRuntime(doorId);
        if (door != null) {
            animateDoor(door, open);
        }
    }

    public boolean isDoorOpen(String doorId) {
        DoorRuntime door = findDoorRuntime(doorId);
        if (door != null) {
            return door.openTarget;
        }
        Boolean pending = pendingDoorOpen.get(doorId);
        return pending != null && pending;
    }

    public boolean isDoorsOpen() {
        if (!doorRuntimes.isEmpty()) {
            for (DoorRuntime door : doorRuntimes) {
                if (door.openTarget) {
                    return true;
                }
            }
            return false;
        }
        for (Boolean open : pendingDoorOpen.values()) {
            if (Boolean.TRUE.equals(open)) {
                return true;
            }
        }
        return false;
    }

    private DoorRuntime findDoorRuntime(String doorId) {
        for (DoorRuntime door : doorRuntimes) {
            if (door.spec.id.equalsIgnoreCase(doorId)) {
                return door;
            }
        }
        return null;
    }

    private void animateDoor(DoorRuntime door, boolean open) {
        if (door.openTarget == open && door.animator != null && door.animator.isRunning()) {
            return;
        }
        if (door.openTarget == open
                && ((open && door.openFraction >= 0.999f)
                || (!open && door.openFraction <= 0.001f))) {
            return;
        }
        door.openTarget = open;
        if (door.animator != null) {
            door.animator.cancel();
        }
        float start = door.openFraction;
        float end = open ? 1f : 0f;
        ValueAnimator animator = ValueAnimator.ofFloat(start, end);
        animator.setDuration(Math.max(120L,
                (long) (DOOR_ANIM_MS * Math.abs(end - start))));
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a ->
                door.openFraction = (float) a.getAnimatedValue());
        door.animator = animator;
        animator.start();
    }

    /** Dış ↔ iç mekan kamerası. true = artık interior. */
    public boolean toggleInteriorMode() {
        if (interiorMode) {
            PoseConfig restore = exteriorPoseBackup != null
                    ? exteriorPoseBackup
                    : defaultExteriorPose();
            interiorMode = false;
            exteriorPoseBackup = null;
            applyPoseConfig(restore);
        } else {
            exteriorPoseBackup = getPoseConfig();
            interiorMode = true;
            applyPoseConfig(interiorPose());
            initOrbitFromHome(); // kamera kilitli home'da
        }
        return interiorMode;
    }

    private static PoseConfig defaultExteriorPose() {
        PoseConfig c = new PoseConfig();
        c.centerX = DEFAULT_CENTER_X;
        c.centerY = DEFAULT_CENTER_Y;
        c.centerZ = DEFAULT_CENTER_Z;
        c.offsetX = DEFAULT_OFFSET_X;
        c.offsetY = DEFAULT_OFFSET_Y;
        c.offsetZ = DEFAULT_OFFSET_Z;
        c.yawDeg = DEFAULT_YAW_DEG;
        c.pitchDeg = DEFAULT_PITCH_DEG;
        c.rollDeg = DEFAULT_ROLL_DEG;
        c.scale = DEFAULT_SCALE;
        c.cameraEyeX = DEFAULT_CAMERA_EYE_X;
        c.cameraEyeY = DEFAULT_CAMERA_EYE_Y;
        c.cameraEyeZ = DEFAULT_CAMERA_EYE_Z;
        return c;
    }

    private static PoseConfig interiorPose() {
        PoseConfig c = new PoseConfig();
        c.centerX = INTERIOR_CENTER_X;
        c.centerY = INTERIOR_CENTER_Y;
        c.centerZ = INTERIOR_CENTER_Z;
        c.offsetX = INTERIOR_OFFSET_X;
        c.offsetY = INTERIOR_OFFSET_Y;
        c.offsetZ = INTERIOR_OFFSET_Z;
        c.yawDeg = INTERIOR_YAW_DEG;
        c.pitchDeg = INTERIOR_PITCH_DEG;
        c.rollDeg = INTERIOR_ROLL_DEG;
        c.scale = INTERIOR_SCALE;
        c.cameraEyeX = INTERIOR_EYE_X;
        c.cameraEyeY = INTERIOR_EYE_Y;
        c.cameraEyeZ = INTERIOR_EYE_Z;
        return c;
    }

    /** UI paneli için güncel pose değerleri. */
    public PoseConfig getPoseConfig() {
        PoseConfig c = new PoseConfig();
        c.centerX = modelCenterX;
        c.centerY = modelCenterY;
        c.centerZ = modelCenterZ;
        c.offsetX = modelOffsetX;
        c.offsetY = modelOffsetY;
        c.offsetZ = modelOffsetZ;
        c.yawDeg = modelYawDeg;
        c.pitchDeg = modelPitchDeg;
        c.rollDeg = modelRollDeg;
        c.scale = modelScale;
        c.cameraEyeX = cameraEyeX;
        c.cameraEyeY = cameraEyeY;
        c.cameraEyeZ = cameraEyeZ;
        return c;
    }

    /** Göster: girilen pose'u modele hemen uygula. */
    public void applyPoseConfig(@NonNull PoseConfig c) {
        modelCenterX = c.centerX;
        modelCenterY = c.centerY;
        modelCenterZ = c.centerZ;
        modelOffsetX = c.offsetX;
        modelOffsetY = c.offsetY;
        modelOffsetZ = c.offsetZ;
        modelYawDeg = c.yawDeg;
        modelPitchDeg = c.pitchDeg;
        modelRollDeg = c.rollDeg;
        modelScale = c.scale <= 0f ? 1f : c.scale;
        cameraEyeX = c.cameraEyeX;
        cameraEyeY = c.cameraEyeY;
        cameraEyeZ = c.cameraEyeZ;

        if (modelViewer == null || modelViewer.getAsset() == null) {
            transformApplied = false;
            return;
        }
        try {
            replaceCameraManipulator();
            modelViewer.transformToUnitCube(
                    new Float3(modelCenterX, modelCenterY, modelCenterZ));
            captureFrontFacingBaseTransform();
            applyBaseTransform();
            initOrbitFromHome();
            resetCameraHome();
            transformApplied = true;
            Log.i(TAG, "Pose uygulandı: " + c);
        } catch (Throwable error) {
            Log.e(TAG, "Pose uygulanamadı", error);
        }
    }

    /** Pose ayarları (UI input). */
    public static final class PoseConfig {
        public float centerX = DEFAULT_CENTER_X;
        public float centerY = DEFAULT_CENTER_Y;
        public float centerZ = DEFAULT_CENTER_Z;
        public float offsetX = DEFAULT_OFFSET_X;
        public float offsetY = DEFAULT_OFFSET_Y;
        public float offsetZ = DEFAULT_OFFSET_Z;
        public float yawDeg = DEFAULT_YAW_DEG;
        public float pitchDeg = DEFAULT_PITCH_DEG;
        public float rollDeg = DEFAULT_ROLL_DEG;
        public float scale = DEFAULT_SCALE;
        public float cameraEyeX = DEFAULT_CAMERA_EYE_X;
        public float cameraEyeY = DEFAULT_CAMERA_EYE_Y;
        public float cameraEyeZ = DEFAULT_CAMERA_EYE_Z;

        @Override
        public String toString() {
            return String.format(Locale.US,
                    "center=(%.2f,%.2f,%.2f) offset=(%.2f,%.2f,%.2f) ypr=(%.1f,%.1f,%.1f) scale=%.2f eye=(%.2f,%.2f,%.2f)",
                    centerX, centerY, centerZ, offsetX, offsetY, offsetZ,
                    yawDeg, pitchDeg, rollDeg, scale, cameraEyeX, cameraEyeY, cameraEyeZ);
        }
    }

    private View buildLoadingOverlay(Context context) {
        LinearLayout overlay = new LinearLayout(context);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setClickable(true);
        // Opak arka plan: ZOrderOnTop kapalıyken SurfaceView deliğinden model sızmasın
        overlay.setBackgroundColor(UiStyles.color(context, R.color.preparing_overlay_root));

        ImageView spinner = new ImageView(context);
        spinner.setImageResource(R.drawable.ic_mdi_loading);
        spinner.setColorFilter(UiStyles.color(context, R.color.accentHighlight));
        int iconSize = UiStyles.dimenPx(context, R.dimen.launcher_dashboard_model_loading_icon);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.gravity = Gravity.CENTER_HORIZONTAL;
        overlay.addView(spinner, iconLp);

        RotateAnimation rotate = new RotateAnimation(
                0f, 360f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(900L);
        rotate.setRepeatCount(Animation.INFINITE);
        rotate.setInterpolator(new LinearInterpolator());
        spinner.startAnimation(rotate);

        loadingLabel = new TextView(context);
        loadingLabel.setText(R.string.launcher_dashboard_model_loading);
        loadingLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.text_size_normal));
        loadingLabel.setTypeface(null, Typeface.BOLD);
        loadingLabel.setTextColor(UiStyles.color(context, R.color.textSecondary));
        loadingLabel.setGravity(Gravity.CENTER);
        loadingLabel.setPadding(0, UiStyles.dimenPx(context, R.dimen.spacing_small), 0, 0);
        overlay.addView(loadingLabel, new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));

        loadingProgressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        loadingProgressBar.setIndeterminate(false);
        loadingProgressBar.setMax(100);
        loadingProgressBar.setProgress(0);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                UiStyles.dimenPx(context, R.dimen.launcher_dashboard_model_loading_bar_width),
                LayoutParams.WRAP_CONTENT);
        barLp.topMargin = UiStyles.dimenPx(context, R.dimen.spacing_small);
        barLp.gravity = Gravity.CENTER_HORIZONTAL;
        overlay.addView(loadingProgressBar, barLp);

        return overlay;
    }

    private void updateLoadingUi() {
        if (modelRevealed || initFailed || modelViewer == null) {
            return;
        }
        try {
            int progress = Math.round(modelViewer.getProgress() * 100f);
            loadingProgressBar.setProgress(Math.max(0, Math.min(progress, 100)));
        } catch (Throwable ignored) {
            // Detach sonrası ResourceLoader ölü olabilir
        }
    }

    private boolean ensureViewer() {
        if (modelViewer != null) {
            return true;
        }
        if (initFailed) {
            return false;
        }
        try {
            engine = Engine.create();
            try {
                Engine.FeatureLevel supported = engine.getSupportedFeatureLevel();
                engine.setActiveFeatureLevel(supported);
                Log.i(TAG, "Filament feature level: " + supported);
            } catch (Throwable ignored) {
                // Eski cihaz / sürücü
            }
            uiHelper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
            uiHelper.setOpaque(true);
            cameraManipulator = new Manipulator.Builder()
                    .viewport(Math.max(1, surfaceView.getWidth()), Math.max(1, surfaceView.getHeight()))
                    .targetPosition(modelCenterX, modelCenterY, modelCenterZ)
                    .orbitHomePosition(cameraEyeX, cameraEyeY, cameraEyeZ)
                    .orbitSpeed(ORBIT_MANIP_SPEED, ORBIT_MANIP_SPEED)
                    .panning(Boolean.FALSE)
                    .build(Manipulator.Mode.ORBIT);
            modelViewer = new ModelViewer(surfaceView, engine, uiHelper, cameraManipulator);
            surfaceView.setOnTouchListener(this::onInspectTouch);
            initOrbitFromHome();
            applySceneBackground();
            applyRenderQuality();
            applyEvenLighting();
            return true;
        } catch (Throwable error) {
            // ModelViewer oluşmadıysa detach listener yok — Engine'i burada bırak.
            if (modelViewer == null && engine != null) {
                try {
                    engine.destroy();
                } catch (Throwable ignored) {
                    // ignore
                }
            }
            if (uiHelper != null) {
                try {
                    uiHelper.detach();
                } catch (Throwable ignored) {
                    // ignore
                }
            }
            abandonViewerReferences();
            initFailed = true;
            surfaceView.setVisibility(GONE);
            loadingLabel.setText(R.string.launcher_dashboard_model_load_failed);
            Log.e(TAG, "Filament ModelViewer başlatılamadı", error);
            return false;
        }
    }

    /**
     * ModelViewer SurfaceView detach'te Engine'i zaten yok eder.
     * Burada yalnızca Java tarafı referansları / bayrakları sıfırlanır —
     * native destroy çağırmak PreconditionPanic (SIGABRT) üretir.
     */
    private void abandonViewerReferences() {
        if (modelViewer != null || engine != null || uiHelper != null) {
            recreateSurfaceOnNextStart = true;
        }
        viewerGeneration++;
        cancelPartAnimators();
        clearWheelParts();
        clearTrunkParts();
        clearSunroofParts();
        clearDoorsParts();

        modelRequested = false;
        transformApplied = false;
        materialsAdjusted = false;
        modelRevealed = false;
        baseTransform = null;
        cameraManipulator = null;
        modelViewer = null;
        engine = null;
        uiHelper = null;
        fillLightEntities.clear();
        ambientLight = null;
        reflectionCubemap = null;
        colorGrading = null;
        recycleOrbitVelocityTracker();
        if (surfaceView != null) {
            surfaceView.setOnTouchListener(null);
        }
    }

    private void initOrbitFromHome() {
        orbitYaw = HOME_VIEW_YAW;
        orbitPitch = HOME_VIEW_PITCH;
        orbitYawDisplay = HOME_VIEW_YAW;
        orbitPitchDisplay = HOME_VIEW_PITCH;
        orbitYawVel = 0f;
        orbitPitchVel = 0f;
        recycleOrbitVelocityTracker();
    }

    /**
     * Tesla tarzı: araç sabit, parmak kamerayı araç etrafında gezdirir.
     * Interior modda kamera kilitli — sadece pick-debug dokunuşu işlenir.
     */
    private boolean onInspectTouch(View view, MotionEvent event) {
        if (!modelRevealed || modelViewer == null || baseTransform == null) {
            return true;
        }
        if (interiorMode) {
            // Orbit yok; pick debug açıksa bilgi kutusu çalışsın
            if (!pickDebugEnabled) {
                return false; // scroll'a bırak
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = true;
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    pickMeshAt(lastTouchX, lastTouchY);
                    showTouchHud(event.getX(), event.getY(), event.getRawX(), event.getRawY());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                        showTouchHud(lastTouchX, lastTouchY, event.getRawX(), event.getRawY());
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    hideTouchHud();
                    return true;
                default:
                    return true;
            }
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                orbitYawVel = 0f;
                orbitPitchVel = 0f;
                // Display hedefin gerisinde kalmasın — sürüklemeye anında otur
                orbitYawDisplay = orbitYaw;
                orbitPitchDisplay = orbitPitch;
                obtainOrbitVelocityTracker().clear();
                orbitVelocityTracker.addMovement(event);
                if (trunkCameraAnimator != null) {
                    trunkCameraAnimator.cancel();
                    trunkCameraAnimator = null;
                }
                requestParentsDisallowIntercept(view, true);
                if (pickDebugEnabled) {
                    pickMeshAt(lastTouchX, lastTouchY);
                    showTouchHud(event.getX(), event.getY(), event.getRawX(), event.getRawY());
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging || event.getPointerCount() != 1) {
                    return true;
                }
                requestParentsDisallowIntercept(view, true);
                if (orbitVelocityTracker != null) {
                    orbitVelocityTracker.addMovement(event);
                }
                float density = Math.max(0.5f, getResources().getDisplayMetrics().density);
                float radPerPx = ORBIT_TOUCH_RAD_PER_DP / density;
                // Historical örnekler: seyrek touch panellerinde basamakları doldurur
                int history = event.getHistorySize();
                for (int i = 0; i < history; i++) {
                    applyOrbitTouchDelta(
                            event.getHistoricalX(i) - lastTouchX,
                            event.getHistoricalY(i) - lastTouchY,
                            radPerPx);
                    lastTouchX = event.getHistoricalX(i);
                    lastTouchY = event.getHistoricalY(i);
                }
                float x = event.getX();
                float y = event.getY();
                applyOrbitTouchDelta(x - lastTouchX, y - lastTouchY, radPerPx);
                lastTouchX = x;
                lastTouchY = y;
                if (pickDebugEnabled) {
                    showTouchHud(x, y, event.getRawX(), event.getRawY());
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (orbitVelocityTracker != null) {
                    orbitVelocityTracker.addMovement(event);
                    if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                        captureOrbitFlingFromTracker();
                    } else {
                        orbitYawVel = 0f;
                        orbitPitchVel = 0f;
                    }
                } else {
                    orbitYawVel = 0f;
                    orbitPitchVel = 0f;
                }
                recycleOrbitVelocityTracker();
                dragging = false;
                requestParentsDisallowIntercept(view, false);
                hideTouchHud();
                return true;
            default:
                return true;
        }
    }

    private void applyOrbitTouchDelta(float dxPx, float dyPx, float radPerPx) {
        if (dxPx == 0f && dyPx == 0f) {
            return;
        }
        orbitYaw += -dxPx * radPerPx;
        orbitPitch += dyPx * radPerPx;
        clampOrbitPitch();
    }

    private android.view.VelocityTracker obtainOrbitVelocityTracker() {
        if (orbitVelocityTracker == null) {
            orbitVelocityTracker = android.view.VelocityTracker.obtain();
        }
        return orbitVelocityTracker;
    }

    private void recycleOrbitVelocityTracker() {
        if (orbitVelocityTracker != null) {
            orbitVelocityTracker.recycle();
            orbitVelocityTracker = null;
        }
    }

    /** VelocityTracker px/s → rad/s; tavan + ölü bölge. */
    private void captureOrbitFlingFromTracker() {
        if (orbitVelocityTracker == null) {
            orbitYawVel = 0f;
            orbitPitchVel = 0f;
            return;
        }
        orbitVelocityTracker.computeCurrentVelocity(1000);
        float density = Math.max(0.5f, getResources().getDisplayMetrics().density);
        float radPerPx = ORBIT_TOUCH_RAD_PER_DP / density;
        float yawVel = -orbitVelocityTracker.getXVelocity() * radPerPx;
        float pitchVel = orbitVelocityTracker.getYVelocity() * radPerPx;
        orbitYawVel = clampFlingSpeed(yawVel);
        orbitPitchVel = clampFlingSpeed(pitchVel);
    }

    private static float clampFlingSpeed(float radPerSec) {
        if (Math.abs(radPerSec) < ORBIT_FLING_MIN_RAD_S) {
            return 0f;
        }
        if (radPerSec > ORBIT_FLING_MAX_RAD_S) {
            return ORBIT_FLING_MAX_RAD_S;
        }
        if (radPerSec < -ORBIT_FLING_MAX_RAD_S) {
            return -ORBIT_FLING_MAX_RAD_S;
        }
        return radPerSec;
    }

    private static void requestParentsDisallowIntercept(View view, boolean disallow) {
        android.view.ViewParent parent = view.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    /** SurfaceView ZOrderOnTop üstünde görünsün diye PopupWindow HUD. Sadece debug mod + basılıyken. */
    private void showTouchHud(float localX, float localY, float rawX, float rawY) {
        if (!pickDebugEnabled || !dragging || !modelRevealed) {
            return;
        }
        ensureTouchHud();
        int w = Math.max(getWidth(), 1);
        int h = Math.max(getHeight(), 1);
        float nx = localX / w;
        float ny = localY / h;
        touchHudLabel.setText(String.format(Locale.US,
                "part   %s\nlocal  x=%.0f  y=%.0f\nnorm   %.2f  %.2f\nraw    x=%.0f  y=%.0f",
                lastPickedPart, localX, localY, nx, ny, rawX, rawY));
        Log.i(TAG, String.format(Locale.US,
                "touch part=%s local=(%.0f,%.0f) norm=(%.2f,%.2f)",
                lastPickedPart, localX, localY, nx, ny));

        mainHandler.removeCallbacks(this::hideTouchHud);
        int[] loc = new int[2];
        getLocationInWindow(loc);
        int popupX = loc[0] + UiStyles.dimenPx(getContext(), R.dimen.spacing_small);
        int popupY = loc[1] + UiStyles.dimenPx(getContext(), R.dimen.spacing_small);
        if (!touchHudWindow.isShowing()) {
            touchHudWindow.showAtLocation(this, Gravity.NO_GRAVITY, popupX, popupY);
        } else {
            touchHudWindow.update(popupX, popupY, -1, -1);
        }
    }

    /**
     * Dokunulan piksellerdeki renderable'ı bulur (glTF node/mesh adı + material).
     * Not: pick Y ekseni alt-orijinli (GL); Android Y üst-orijinli.
     */
    private void pickMeshAt(float viewX, float viewY) {
        if (!pickDebugEnabled || modelViewer == null || modelViewer.getAsset() == null) {
            return;
        }
        int width = surfaceView.getWidth();
        int height = surfaceView.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        int pickX = Math.max(0, Math.min(width - 1, Math.round(viewX)));
        int pickY = Math.max(0, Math.min(height - 1, height - 1 - Math.round(viewY)));

        lastPickedPart = "…";
        modelViewer.getView().pick(pickX, pickY, mainHandler, result -> {
            if (!active || modelViewer == null) {
                return;
            }
            int entity = result.renderable;
            if (entity == 0) {
                lastPickedPart = "(boş / isabet yok)";
                refreshTouchHudPartOnly();
                Log.i(TAG, "pick miss");
                return;
            }

            FilamentAsset asset = modelViewer.getAsset();
            String nodeName = asset != null ? asset.getName(entity) : null;
            String extras = asset != null ? asset.getExtras(entity) : null;
            String materialName = null;
            try {
                RenderableManager rm = modelViewer.getEngine().getRenderableManager();
                if (rm.hasComponent(entity)) {
                    int ri = rm.getInstance(entity);
                    if (rm.getPrimitiveCount(ri) > 0) {
                        MaterialInstance mi = rm.getMaterialInstanceAt(ri, 0);
                        if (mi != null) {
                            materialName = mi.getName();
                        }
                    }
                }
            } catch (Throwable ignored) {
                // material yoksa sadece node adı
            }

            StringBuilder sb = new StringBuilder();
            if (nodeName != null && !nodeName.isEmpty()) {
                sb.append(nodeName);
            } else {
                sb.append("entity#").append(entity);
            }
            if (materialName != null && !materialName.isEmpty()) {
                sb.append("  [").append(materialName).append(']');
            }
            if (extras != null && !extras.isEmpty()) {
                sb.append("  extras=").append(extras);
            }
            sb.append(String.format(Locale.US, "  d=%.3f", result.depth));
            lastPickedPart = sb.toString();
            refreshTouchHudPartOnly();
            Log.i(TAG, "pick hit: " + lastPickedPart);
        });
    }

    private void refreshTouchHudPartOnly() {
        // Sadece debug açık ve parmak modeldeyken güncelle
        if (!pickDebugEnabled || !dragging
                || touchHudLabel == null || touchHudWindow == null || !touchHudWindow.isShowing()) {
            return;
        }
        CharSequence cur = touchHudLabel.getText();
        String rest = "";
        if (cur != null) {
            String s = cur.toString();
            int nl = s.indexOf('\n');
            if (nl >= 0) {
                rest = s.substring(nl);
            }
        }
        touchHudLabel.setText("part   " + lastPickedPart + rest);
    }

    private void ensureTouchHud() {
        if (touchHudWindow != null) {
            return;
        }
        touchHudLabel = new TextView(getContext());
        touchHudLabel.setTypeface(Typeface.MONOSPACE);
        touchHudLabel.setTextColor(Color.WHITE);
        touchHudLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        int pad = UiStyles.dimenPx(getContext(), R.dimen.spacing_small);
        touchHudLabel.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC111827);
        bg.setCornerRadius(pad);
        touchHudLabel.setBackground(bg);

        touchHudWindow = new PopupWindow(
                touchHudLabel,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                false);
        touchHudWindow.setTouchable(false);
        touchHudWindow.setFocusable(false);
        touchHudWindow.setOutsideTouchable(false);
        touchHudWindow.setClippingEnabled(false);
    }

    private void hideTouchHud() {
        mainHandler.removeCallbacks(this::hideTouchHud);
        if (touchHudWindow != null && touchHudWindow.isShowing()) {
            try {
                touchHudWindow.dismiss();
            } catch (Throwable ignored) {
                // window already gone
            }
        }
    }

    private void tickOrbitInertia(float dt) {
        if (interiorMode
                || (trunkCameraAnimator != null && trunkCameraAnimator.isRunning())) {
            // Display hedefe otursun (trunk anim / interior)
            orbitYawDisplay = orbitYaw;
            orbitPitchDisplay = orbitPitch;
            return;
        }
        if (!dragging) {
            // rad/s * dt — frame hızından bağımsız
            orbitYaw += orbitYawVel * dt;
            orbitPitch += orbitPitchVel * dt;
            clampOrbitPitch();
            float decay = (float) Math.pow(ORBIT_INERTIA, dt * 60f);
            orbitYawVel *= decay;
            orbitPitchVel *= decay;
            if (Math.abs(orbitYawVel) < 0.02f) {
                orbitYawVel = 0f;
            }
            if (Math.abs(orbitPitchVel) < 0.02f) {
                orbitPitchVel = 0f;
            }
        }
        // Hedefe yumuşak takip — seyrek touch / yuvarlama basamaklarını gizler
        float t = 1f - (float) Math.exp(-ORBIT_DISPLAY_SMOOTH * dt);
        if (dragging) {
            // Sürüklerken biraz daha sıkı takip (gecikme hissi olmasın)
            t = Math.min(1f, t * 1.6f);
        }
        orbitYawDisplay += (orbitYaw - orbitYawDisplay) * t;
        orbitPitchDisplay += (orbitPitch - orbitPitchDisplay) * t;
        if (Math.abs(orbitYaw - orbitYawDisplay) < 1e-5f) {
            orbitYawDisplay = orbitYaw;
        }
        if (Math.abs(orbitPitch - orbitPitchDisplay) < 1e-5f) {
            orbitPitchDisplay = orbitPitch;
        }
    }

    private void clampOrbitPitch() {
        if (orbitPitch < ORBIT_PITCH_MIN) {
            orbitPitch = ORBIT_PITCH_MIN;
            orbitPitchVel = 0f;
        } else if (orbitPitch > ORBIT_PITCH_MAX) {
            orbitPitch = ORBIT_PITCH_MAX;
            orbitPitchVel = 0f;
        }
    }

    /**
     * Orbit açılarını Manipulator'a işler (ModelViewer her frame manipulator'dan okur).
     * Home + sentetik grab = mutlak yaw/pitch; araç sabit, kamera döner.
     */
    private void applyOrbitToCamera() {
        if (cameraManipulator == null || surfaceView.getWidth() <= 0 || surfaceView.getHeight() <= 0) {
            return;
        }
        try {
            if (interiorMode) {
                // Interior: home'da kilitli, orbit yok
                initOrbitFromHome();
                cameraManipulator.jumpToBookmark(cameraManipulator.getHomeBookmark());
                return;
            }
            cameraManipulator.jumpToBookmark(cameraManipulator.getHomeBookmark());
            int width = surfaceView.getWidth();
            int height = surfaceView.getHeight();
            int cx = width / 2;
            int cy = height / 2;
            // GestureDetector gibi Y'yi çevir (Filament manipulatörü alt-sol orijin)
            int beginX = cx;
            int beginY = height - cy;
            // İnce manip speed → grab px çözünürlüğü yüksek (sert basamak yok)
            int grabX = cx + Math.round(orbitYawDisplay / ORBIT_MANIP_SPEED);
            int grabY = height - (cy + Math.round(orbitPitchDisplay / ORBIT_MANIP_SPEED));
            cameraManipulator.grabBegin(beginX, beginY, false);
            cameraManipulator.grabUpdate(grabX, grabY);
            cameraManipulator.grabEnd();
        } catch (Throwable error) {
            Log.w(TAG, "Orbit kamera uygulanamadı", error);
        }
    }

    private void applyHostBackground() {
        setBackgroundColor(UiStyles.color(getContext(), R.color.filament_scene_clear));
    }

    /**
     * SurfaceView şeffaflığı araç IVI'de siyah delik açar.
     * Clear rengi light/dark cam kart tonuna (opak) ayarlanır.
     */
    private void applySceneBackground() {
        applyHostBackground();
        if (modelViewer == null) {
            return;
        }
        int argb = UiStyles.color(getContext(), R.color.filament_scene_clear);
        float r = Color.red(argb) / 255f;
        float g = Color.green(argb) / 255f;
        float b = Color.blue(argb) / 255f;
        Renderer.ClearOptions options = modelViewer.getRenderer().getClearOptions();
        options.clear = true;
        options.clearColor = new float[] {r, g, b, 1f};
        modelViewer.getRenderer().setClearOptions(options);

        com.google.android.filament.View filamentView = modelViewer.getView();
        filamentView.setBlendMode(com.google.android.filament.View.BlendMode.OPAQUE);
        modelViewer.getScene().setSkybox(null);
    }

    /** Light/dark değişiminde Filament clear + host bg yenilenir. */
    public void reapplySceneBackground() {
        applySceneBackground();
        pendingSceneBackgroundReapply = true;
    }

    /**
     * Düz GLB önizleme: post-processing kapalı, tam çözünürlük, hafif FXAA.
     * SSAO/bloom/HDR grading yok — hem daha hafif hem de "çamur" görünümü önler.
     */
    private void applyRenderQuality() {
        if (modelViewer == null || engine == null) {
            return;
        }
        com.google.android.filament.View filamentView = modelViewer.getView();
        try {
            filamentView.setPostProcessingEnabled(false);
            filamentView.setAntiAliasing(com.google.android.filament.View.AntiAliasing.FXAA);
            filamentView.setDithering(com.google.android.filament.View.Dithering.NONE);

            com.google.android.filament.View.MultiSampleAntiAliasingOptions msaa =
                    new com.google.android.filament.View.MultiSampleAntiAliasingOptions();
            msaa.enabled = false;
            filamentView.setMultiSampleAntiAliasingOptions(msaa);

            com.google.android.filament.View.DynamicResolutionOptions drs =
                    filamentView.getDynamicResolutionOptions();
            drs.enabled = false;
            drs.minScale = 1f;
            drs.maxScale = 1f;
            filamentView.setDynamicResolutionOptions(drs);

            com.google.android.filament.View.AmbientOcclusionOptions ao =
                    new com.google.android.filament.View.AmbientOcclusionOptions();
            ao.enabled = false;
            filamentView.setAmbientOcclusionOptions(ao);

            com.google.android.filament.View.BloomOptions bloom = filamentView.getBloomOptions();
            bloom.enabled = false;
            filamentView.setBloomOptions(bloom);

            if (colorGrading != null) {
                engine.destroyColorGrading(colorGrading);
                colorGrading = null;
            }
        } catch (Throwable error) {
            Log.w(TAG, "Render quality ayarları uygulanamadı", error);
        }
    }

    private void applyEvenLighting() {
        if (modelViewer == null) {
            return;
        }
        Engine engine = modelViewer.getEngine();

        reflectionCubemap = createStudioCubemap(engine);
        ambientLight = new IndirectLight.Builder()
                .reflections(reflectionCubemap)
                .irradiance(2, buildStudioIrradianceSh(new float[] {1f, 1f, 1.02f}))
                .intensity(AMBIENT_LIGHT_INTENSITY)
                .build(engine);
        modelViewer.getScene().setIndirectLight(ambientLight);

        LightManager lightManager = engine.getLightManager();
        int sun = modelViewer.getLight();
        if (lightManager.hasComponent(sun)) {
            int instance = lightManager.getInstance(sun);
            lightManager.setShadowCaster(instance, false);
            // Üst-ön key light — gövde konturunu çıkarır
            lightManager.setDirection(instance, 0.35f, -0.78f, -0.52f);
            lightManager.setIntensity(instance, KEY_LIGHT_INTENSITY);
        }

        // Az ama kontrollü fill — yıkama ışığı yerine kontrast
        addFillPoint(engine, 2.6f, 2.1f, -1.8f, FILL_LIGHT_INTENSITY * 0.85f);
        addFillPoint(engine, -2.4f, 1.4f, -2.6f, FILL_LIGHT_INTENSITY * 0.55f);
        addFillPoint(engine, 0.2f, 1.6f, 3.2f, FILL_LIGHT_INTENSITY * 0.4f);
        applyNeutralLighting();
    }

    /** Nötr stüdyo ışığı — ECO/NORMAL/SPORT renk tint’i yok. */
    private void applyNeutralLighting() {
        if (modelViewer == null) {
            return;
        }
        float[] white = new float[] {1f, 1f, 1f};
        Engine engine = modelViewer.getEngine();

        if (reflectionCubemap != null) {
            try {
                if (ambientLight != null) {
                    modelViewer.getScene().setIndirectLight(null);
                    engine.destroyIndirectLight(ambientLight);
                    ambientLight = null;
                }
                ambientLight = new IndirectLight.Builder()
                        .reflections(reflectionCubemap)
                        .irradiance(2, buildStudioIrradianceSh(new float[] {1f, 1f, 1.02f}))
                        .intensity(AMBIENT_LIGHT_INTENSITY)
                        .build(engine);
                modelViewer.getScene().setIndirectLight(ambientLight);
            } catch (Throwable error) {
                Log.w(TAG, "Neutral ambient uygulanamadı", error);
            }
        }

        LightManager lightManager = engine.getLightManager();
        int sun = modelViewer.getLight();
        if (lightManager.hasComponent(sun)) {
            lightManager.setColor(lightManager.getInstance(sun), white[0], white[1], white[2]);
        }
        for (int entity : fillLightEntities) {
            if (!lightManager.hasComponent(entity)) {
                continue;
            }
            lightManager.setColor(lightManager.getInstance(entity), white[0], white[1], white[2]);
        }
    }

    private void addFillPoint(Engine engine, float x, float y, float z, float intensity) {
        int entity = EntityManager.get().create();
        new LightManager.Builder(LightManager.Type.POINT)
                .color(1f, 1f, 1f)
                .intensity(intensity)
                .position(x, y, z)
                .falloff(14f)
                .castShadows(false)
                .build(engine, entity);
        modelViewer.getScene().addEntity(entity);
        fillLightEntities.add(entity);
    }

    /**
     * bands=2 SH: gökyüzü üstten, zemin alttan — düz ambient yerine stüdyo kontrastı.
     * 4 × float3 = 12 float.
     */
    private static float[] buildStudioIrradianceSh(float[] tintRgb) {
        float r = tintRgb[0];
        float g = tintRgb[1];
        float b = tintRgb[2];
        return new float[] {
                // L00
                0.78f * r, 0.78f * g, 0.82f * b,
                // L1-1
                0.04f * r, 0.04f * g, 0.04f * b,
                // L10 ( +Y sky )
                0.42f * r, 0.42f * g, 0.46f * b,
                // L11
                0.1f * r, 0.1f * g, 0.1f * b,
        };
    }

    /** Softbox stüdyo cubemap + mip zinciri — boya yansımalarını canlandırır. */
    private static Texture createStudioCubemap(Engine engine) {
        final int size = STUDIO_CUBEMAP_SIZE;
        int levels = 1;
        for (int s = size; s > 1; s >>= 1) {
            levels++;
        }
        Texture texture = new Texture.Builder()
                .width(size)
                .height(size)
                .levels(levels)
                .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
                .format(Texture.InternalFormat.RGBA8)
                .build(engine);

        for (int level = 0; level < levels; level++) {
            int faceSize = Math.max(1, size >> level);
            int faceBytes = faceSize * faceSize * 4;
            ByteBuffer data = ByteBuffer.allocateDirect(faceBytes * 6);
            data.order(ByteOrder.nativeOrder());
            for (int face = 0; face < 6; face++) {
                writeStudioFace(data, face, faceSize);
            }
            data.flip();
            int[] faceOffsetsInBytes = new int[6];
            for (int face = 0; face < 6; face++) {
                faceOffsetsInBytes[face] = face * faceBytes;
            }
            Texture.PixelBufferDescriptor buffer = new Texture.PixelBufferDescriptor(
                    data, Texture.Format.RGBA, Texture.Type.UBYTE);
            texture.setImage(engine, level, buffer, faceOffsetsInBytes);
        }
        return texture;
    }

    /** Filament cubemap sırası: +X -X +Y -Y +Z -Z */
    private static void writeStudioFace(ByteBuffer data, int face, int faceSize) {
        for (int y = 0; y < faceSize; y++) {
            float v = faceSize <= 1 ? 0.5f : (y + 0.5f) / faceSize;
            for (int x = 0; x < faceSize; x++) {
                float u = faceSize <= 1 ? 0.5f : (x + 0.5f) / faceSize;
                float brightness;
                float cool;
                switch (face) {
                    case 2: // +Y ceiling softbox — HDR highlight kaynağı
                        brightness = 0.95f + 0.05f * (1f - distFromCenter(u, v));
                        cool = 1.08f;
                        break;
                    case 3: // -Y floor bounce
                        brightness = 0.1f + 0.12f * (1f - distFromCenter(u, v));
                        cool = 0.9f;
                        break;
                    default: // walls — üstten alta gradient + softbox şeritleri
                        float softbox = Math.max(0f, 1f - Math.abs(u - 0.5f) * 3.2f);
                        brightness = 0.22f + 0.48f * (1f - v) + 0.35f * softbox * (1f - v);
                        cool = 1.02f;
                        break;
                }
                int r = clampByte(brightness * 255f * cool);
                int g = clampByte(brightness * 252f);
                int b = clampByte(brightness * 255f * (cool > 1f ? 1.08f : 1f));
                data.put((byte) r).put((byte) g).put((byte) b).put((byte) 255);
            }
        }
    }

    private static float distFromCenter(float u, float v) {
        float du = u - 0.5f;
        float dv = v - 0.5f;
        return Math.min(1f, 2f * (float) Math.sqrt(du * du + dv * dv));
    }

    private static int clampByte(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }

    private void requestModelLoad() {
        if (modelRequested || modelViewer == null) {
            return;
        }
        modelRequested = true;
        final int generation = viewerGeneration;
        new Thread(() -> {
            ByteBuffer buffer = readModelBuffer();
            if (buffer == null) {
                mainHandler.post(() -> {
                    if (generation != viewerGeneration) {
                        return;
                    }
                    initFailed = true;
                    surfaceView.setVisibility(GONE);
                    loadingLabel.setText(R.string.launcher_dashboard_model_load_failed);
                    Log.e(TAG, "GLB asset okunamadı: " + MODEL_ASSET);
                });
                return;
            }
            mainHandler.post(() -> {
                if (generation != viewerGeneration || modelViewer == null || initFailed) {
                    return;
                }
                try {
                    clearWheelParts();
                    clearTrunkParts();
                    clearSunroofParts();
                    clearDoorsParts();
                    modelViewer.loadModelGlb(buffer);
                    // Texture beklemeden pose'u uygula (surface hâlâ gizli)
                    ensureModelPose();
                } catch (Throwable error) {
                    initFailed = true;
                    surfaceView.setVisibility(GONE);
                    loadingLabel.setText(R.string.launcher_dashboard_model_load_failed);
                    Log.e(TAG, "GLB model yüklenemedi", error);
                }
            });
        }, "vehicle-glb-loader").start();
    }

    /** Bir kez: unit cube + ön görünüm. Kullanıcı dönüşü baseTransform üzerine uygulanır. */
    private void ensureModelPose() {
        if (transformApplied || modelViewer == null || modelViewer.getAsset() == null) {
            return;
        }
        try {
            modelViewer.transformToUnitCube(
                    new Float3(modelCenterX, modelCenterY, modelCenterZ));
            captureFrontFacingBaseTransform();
            applyBaseTransform();
            initOrbitFromHome();
            resetCameraHome();
            transformApplied = true;
        } catch (Throwable error) {
            Log.e(TAG, "Model transform uygulanamadı", error);
        }
    }

    private void tryRevealModel() {
        if (modelRevealed || modelViewer == null || !transformApplied) {
            return;
        }
        try {
            if (modelViewer.getProgress() < 0.999f) {
                return;
            }
        } catch (Throwable ignored) {
            return;
        }
        materialsAdjusted = true;
        resetCameraHome();
        applyBaseTransform();
        initOrbitFromHome();
        modelRevealed = true;
        loadingProgressBar.setProgress(100);
        loadingOverlay.setVisibility(GONE);
        surfaceView.setVisibility(VISIBLE);
        // Opak surface kart hiyerarşisinde — ZOrderOnTop IVI'de siyah arka plan üretir.
        surfaceView.setZOrderOnTop(false);
        applySceneBackground();
    }

    private void captureFrontFacingBaseTransform() {
        FilamentAsset asset = modelViewer.getAsset();
        if (asset == null) {
            return;
        }
        TransformManager transformManager = modelViewer.getEngine().getTransformManager();
        int root = asset.getRoot();
        if (!transformManager.hasComponent(root)) {
            return;
        }
        int instance = transformManager.getInstance(root);
        float[] unit = new float[16];
        transformManager.getTransform(instance, unit);

        // scale * offset * roll * pitch * yaw * unitCube
        float[] scaleM = new float[16];
        Matrix.setIdentityM(scaleM, 0);
        Matrix.scaleM(scaleM, 0, modelScale, modelScale, modelScale);

        float[] offsetM = new float[16];
        Matrix.setIdentityM(offsetM, 0);
        Matrix.translateM(offsetM, 0, modelOffsetX, modelOffsetY, modelOffsetZ);

        float[] yawM = new float[16];
        float[] pitchM = new float[16];
        float[] rollM = new float[16];
        Matrix.setRotateM(yawM, 0, modelYawDeg, 0f, 1f, 0f);
        Matrix.setRotateM(pitchM, 0, modelPitchDeg, 1f, 0f, 0f);
        Matrix.setRotateM(rollM, 0, modelRollDeg, 0f, 0f, 1f);

        float[] rot = new float[16];
        float[] tmp = new float[16];
        Matrix.multiplyMM(tmp, 0, pitchM, 0, yawM, 0);
        Matrix.multiplyMM(rot, 0, rollM, 0, tmp, 0);

        float[] posed = new float[16];
        Matrix.multiplyMM(tmp, 0, rot, 0, unit, 0);
        Matrix.multiplyMM(posed, 0, offsetM, 0, tmp, 0);

        baseTransform = new float[16];
        Matrix.multiplyMM(baseTransform, 0, scaleM, 0, posed, 0);
    }

    /** Sabit model pozu — dokunma kamerayı döndürür, modeli değil. */
    private void applyBaseTransform() {
        if (modelViewer == null || baseTransform == null) {
            return;
        }
        FilamentAsset asset = modelViewer.getAsset();
        if (asset == null) {
            return;
        }
        TransformManager transformManager = modelViewer.getEngine().getTransformManager();
        int root = asset.getRoot();
        if (!transformManager.hasComponent(root)) {
            return;
        }
        transformManager.setTransform(transformManager.getInstance(root), baseTransform);
    }

    private void tickSimSpeed(float dt) {
        float target = 0f;
        if (wheelSimulationEnabled && wheelSpeedKmh <= 0f) {
            target = SIMULATED_SPEED_KMH;
        }
        if (simSpeedKmh < target) {
            simSpeedKmh = Math.min(target, simSpeedKmh + SIM_ACCEL_KMH_PER_SEC * dt);
        } else if (simSpeedKmh > target) {
            simSpeedKmh = Math.max(target, simSpeedKmh - SIM_ACCEL_KMH_PER_SEC * dt);
        }
        notifyEffectiveWheelSpeedIfNeeded();
    }

    private void notifyEffectiveWheelSpeedIfNeeded() {
        if (wheelSpeedListener == null) {
            return;
        }
        // Gerçek hız varken dashboard snapshot zaten günceller
        if (wheelSpeedKmh > 0f) {
            return;
        }
        int reported = Math.round(simSpeedKmh);
        if (reported == lastReportedSimSpeed) {
            return;
        }
        lastReportedSimSpeed = reported;
        wheelSpeedListener.onEffectiveWheelSpeedChanged(simSpeedKmh);
    }

    private void tickWheelSpin(float dt) {
        if (!modelRevealed || modelViewer == null) {
            return;
        }
        ensureWheelPartsResolved();
        if (wheelRestTransforms.isEmpty()) {
            return;
        }
        float speed = getEffectiveWheelSpeedKmh();
        if (speed > 0f) {
            float metersPerSec = speed / 3.6f;
            float radPerSec = metersPerSec / WHEEL_RADIUS_M;
            wheelAngleDeg += radPerSec * (180f / (float) Math.PI) * dt;
            if (wheelAngleDeg > 360_000f) {
                wheelAngleDeg %= 360f;
            }
        }
        applyWheelTransforms();
    }

    private void ensureWheelPartsResolved() {
        if (wheelPartsResolved || modelViewer == null) {
            return;
        }
        FilamentAsset asset = modelViewer.getAsset();
        if (asset == null) {
            return;
        }
        TransformManager tm = modelViewer.getEngine().getTransformManager();
        RenderableManager rm = modelViewer.getEngine().getRenderableManager();
        int found = 0;
        for (String name : WHEEL_PART_NAMES) {
            int entity = asset.getFirstEntityByName(name);
            if (entity == 0 || !tm.hasComponent(entity)) {
                Log.w(TAG, "Tekerlek parçası bulunamadı: " + name);
                continue;
            }
            // GLB: ön/arka (fren: 4) ayrı child — her biri origin'de merkezli + translation=pivot
            found += registerWheelTree(name, entity, tm, rm);
        }
        wheelPartsResolved = true;
        Log.i(TAG, "Tekerlek spin hedefleri: " + found);
    }

    private int registerWheelTree(
            String label,
            int entity,
            TransformManager tm,
            RenderableManager rm) {
        int ti = tm.getInstance(entity);
        int childCount = tm.getChildCount(ti);
        if (childCount > 0) {
            int[] children = tm.getChildren(ti, null);
            int registered = 0;
            if (children != null) {
                for (int child : children) {
                    if (!tm.hasComponent(child)) {
                        continue;
                    }
                    registered += registerWheelTree(label, child, tm, rm);
                }
            }
            if (registered > 0) {
                return registered;
            }
        }
        if (!rm.hasComponent(entity)) {
            return 0;
        }
        float[] rest = new float[16];
        tm.getTransform(tm.getInstance(entity), rest);
        wheelRestTransforms.put(entity, rest);
        Log.i(TAG, String.format(Locale.US,
                "Tekerlek %s#%d t=(%.3f,%.3f,%.3f)",
                label, entity, rest[12], rest[13], rest[14]));
        return 1;
    }

    private void applyWheelTransforms() {
        if (modelViewer == null || wheelRestTransforms.isEmpty()) {
            return;
        }
        TransformManager tm = modelViewer.getEngine().getTransformManager();
        // Aks = Z; mesh origin tekerlek merkezinde (GLB split + recenter)
        Matrix.setRotateM(wheelRotM, 0, wheelAngleDeg, WHEEL_AXIS_X, WHEEL_AXIS_Y, WHEEL_AXIS_Z);
        for (Map.Entry<Integer, float[]> entry : wheelRestTransforms.entrySet()) {
            int entity = entry.getKey();
            if (!tm.hasComponent(entity)) {
                continue;
            }
            // out = rest * Rz  → translation sabit, yerinde dönüş
            Matrix.multiplyMM(wheelOutM, 0, entry.getValue(), 0, wheelRotM, 0);
            tm.setTransform(tm.getInstance(entity), wheelOutM);
        }
    }

    private void clearWheelParts() {
        wheelPartsResolved = false;
        wheelRestTransforms.clear();
        wheelAngleDeg = 0f;
    }

    private void ensureTrunkPartsResolved() {
        if (trunkPartsResolved || !modelRevealed || modelViewer == null) {
            return;
        }
        FilamentAsset asset = modelViewer.getAsset();
        if (asset == null) {
            return;
        }
        TransformManager tm = modelViewer.getEngine().getTransformManager();
        RenderableManager rm = modelViewer.getEngine().getRenderableManager();
        trunkRestTransforms.clear();
        trunkHingeLocal = null;
        int found = 0;
        for (String name : TRUNK_PART_NAMES) {
            int entity = asset.getFirstEntityByName(name);
            if (entity == 0 || !tm.hasComponent(entity)) {
                Log.w(TAG, "Bagaj parçası bulunamadı: " + name);
                continue;
            }
            float[] rest = new float[16];
            tm.getTransform(tm.getInstance(entity), rest);
            trunkRestTransforms.put(entity, rest);
            found++;
            if (TRUNK_LID_NAME.equals(name)) {
                trunkHingeLocal = computeTrunkHinge(entity, tm, rm);
            }
        }
        if (trunkHingeLocal == null && !trunkRestTransforms.isEmpty()) {
            trunkHingeLocal = new float[]{1.70f, 1.636f, 0f};
        }
        trunkPartsResolved = true;
        Log.i(TAG, String.format(Locale.US,
                "Bagaj parçaları: %d/%d hinge=(%.3f,%.3f,%.3f)",
                found, TRUNK_PART_NAMES.length,
                trunkHingeLocal != null ? trunkHingeLocal[0] : 0f,
                trunkHingeLocal != null ? trunkHingeLocal[1] : 0f,
                trunkHingeLocal != null ? trunkHingeLocal[2] : 0f));
    }

    /**
     * SUV hatch menteşesi: kapak AABB üst-ön kenarı (Y max, X min tarafı).
     * Tüm trunk_* kardeşleri aynı hinge etrafında döner → göreli konum korunur.
     */
    private float[] computeTrunkHinge(
            int lidEntity,
            TransformManager tm,
            RenderableManager rm) {
        int renderEntity = findFirstRenderable(lidEntity, tm, rm);
        // glTF trunk_cheqi üst-ön (group1 local)
        float[] fallback = new float[]{1.70f, 1.636f, 0f};
        if (renderEntity == 0) {
            return fallback;
        }
        Box box = rm.getAxisAlignedBoundingBox(rm.getInstance(renderEntity), new Box());
        float[] center = box.getCenter();
        float[] half = box.getHalfExtent();
        float[] hinge = {
                center[0] - half[0] * 0.8f,
                center[1] + half[1],
                center[2]
        };
        // Unit-cube sonrası world AABB gelirse glTF aralığı dışına çıkar
        if (hinge[0] < 1.0f || hinge[0] > 2.6f || hinge[1] < 0.5f || hinge[1] > 2.2f) {
            Log.w(TAG, String.format(Locale.US,
                    "Bagaj hinge AABB beklenmeyen: (%.3f,%.3f,%.3f) → fallback",
                    hinge[0], hinge[1], hinge[2]));
            return fallback;
        }
        return hinge;
    }

    private int findFirstRenderable(int entity, TransformManager tm, RenderableManager rm) {
        if (rm.hasComponent(entity)) {
            return entity;
        }
        if (!tm.hasComponent(entity)) {
            return 0;
        }
        int[] children = tm.getChildren(tm.getInstance(entity), null);
        if (children == null) {
            return 0;
        }
        for (int child : children) {
            int found = findFirstRenderable(child, tm, rm);
            if (found != 0) {
                return found;
            }
        }
        return 0;
    }

    private void applyTrunkTransforms() {
        if (!trunkPartsResolved || trunkRestTransforms.isEmpty() || trunkHingeLocal == null) {
            return;
        }
        if (modelViewer == null) {
            return;
        }
        TransformManager tm = modelViewer.getEngine().getTransformManager();
        float angle = TRUNK_OPEN_DEG * trunkOpenFraction;
        Matrix.setRotateM(trunkRotM, 0, angle, 0f, 0f, 1f);
        float hx = trunkHingeLocal[0];
        float hy = trunkHingeLocal[1];
        float hz = trunkHingeLocal[2];
        Matrix.setIdentityM(trunkToPivotM, 0);
        Matrix.translateM(trunkToPivotM, 0, -hx, -hy, -hz);
        Matrix.setIdentityM(trunkFromPivotM, 0);
        Matrix.translateM(trunkFromPivotM, 0, hx, hy, hz);
        Matrix.multiplyMM(trunkTmpM, 0, trunkRotM, 0, trunkToPivotM, 0);
        Matrix.multiplyMM(trunkSpinM, 0, trunkFromPivotM, 0, trunkTmpM, 0);

        for (Map.Entry<Integer, float[]> entry : trunkRestTransforms.entrySet()) {
            int entity = entry.getKey();
            if (!tm.hasComponent(entity)) {
                continue;
            }
            // Aynı hinge transform → parçalar birlikte hareket eder
            Matrix.multiplyMM(trunkOutM, 0, trunkSpinM, 0, entry.getValue(), 0);
            tm.setTransform(tm.getInstance(entity), trunkOutM);
        }
    }

    private void clearTrunkParts() {
        if (trunkAnimator != null) {
            trunkAnimator.cancel();
            trunkAnimator = null;
        }
        if (trunkCameraAnimator != null) {
            trunkCameraAnimator.cancel();
            trunkCameraAnimator = null;
        }
        trunkPartsResolved = false;
        trunkRestTransforms.clear();
        trunkHingeLocal = null;
        trunkOpenFraction = 0f;
        trunkOpenTarget = false;
    }

    private void ensureSunroofPartsResolved() {
        if (sunroofPartsResolved || !modelRevealed || modelViewer == null) {
            return;
        }
        FilamentAsset asset = modelViewer.getAsset();
        if (asset == null) {
            return;
        }
        TransformManager tm = modelViewer.getEngine().getTransformManager();
        int entity = asset.getFirstEntityByName(SUNROOF_PART_NAME);
        if (entity == 0 || !tm.hasComponent(entity)) {
            Log.w(TAG, "Cam tavan bulunamadı: " + SUNROOF_PART_NAME);
            sunroofPartsResolved = true;
            return;
        }
        sunroofEntity = entity;
        sunroofRestTransform = new float[16];
        tm.getTransform(tm.getInstance(entity), sunroofRestTransform);
        sunroofPartsResolved = true;
        Log.i(TAG, String.format(Locale.US,
                "Cam tavan %s t=(%.3f,%.3f,%.3f) slideX=%.3f",
                SUNROOF_PART_NAME,
                sunroofRestTransform[12],
                sunroofRestTransform[13],
                sunroofRestTransform[14],
                SUNROOF_SLIDE_X));
    }

    private void applySunroofTransforms() {
        if (!sunroofPartsResolved
                || sunroofRestTransform == null
                || sunroofEntity == 0
                || modelViewer == null) {
            return;
        }
        TransformManager tm = modelViewer.getEngine().getTransformManager();
        if (!tm.hasComponent(sunroofEntity)) {
            return;
        }
        float dx = SUNROOF_SLIDE_X * sunroofOpenFraction;
        Matrix.setIdentityM(sunroofSlideM, 0);
        Matrix.translateM(sunroofSlideM, 0, dx, 0f, 0f);
        // out = T(+X) * rest  → geriye kaydır
        Matrix.multiplyMM(sunroofOutM, 0, sunroofSlideM, 0, sunroofRestTransform, 0);
        tm.setTransform(tm.getInstance(sunroofEntity), sunroofOutM);
    }

    private void clearSunroofParts() {
        if (sunroofAnimator != null) {
            sunroofAnimator.cancel();
            sunroofAnimator = null;
        }
        sunroofPartsResolved = false;
        sunroofEntity = 0;
        sunroofRestTransform = null;
        sunroofOpenFraction = 0f;
        sunroofOpenTarget = false;
    }

    private void ensureDoorsPartsResolved() {
        if (doorsPartsResolved || !modelRevealed || modelViewer == null) {
            return;
        }
        FilamentAsset asset = modelViewer.getAsset();
        if (asset == null) {
            return;
        }
        TransformManager tm = modelViewer.getEngine().getTransformManager();
        RenderableManager rm = modelViewer.getEngine().getRenderableManager();
        doorRuntimes.clear();
        int totalFound = 0;
        for (DoorSpec spec : DOOR_SPECS) {
            DoorRuntime runtime = new DoorRuntime(spec);
            int found = 0;
            for (String name : spec.partNames) {
                int entity = asset.getFirstEntityByName(name);
                if (entity == 0 || !tm.hasComponent(entity)) {
                    Log.w(TAG, "Kapı parçası bulunamadı: " + name);
                    continue;
                }
                float[] rest = new float[16];
                tm.getTransform(tm.getInstance(entity), rest);
                runtime.restTransforms.put(entity, rest);
                found++;
                if (spec.lidName.equals(name)) {
                    runtime.hingeLocal = computeDoorHinge(entity, tm, rm, spec);
                }
            }
            if (runtime.hingeLocal == null && !runtime.restTransforms.isEmpty()) {
                runtime.hingeLocal = spec.fallbackHinge.clone();
            }
            if (!runtime.restTransforms.isEmpty()) {
                Boolean pending = pendingDoorOpen.get(spec.id);
                if (pending != null) {
                    runtime.openTarget = pending;
                    runtime.openFraction = pending ? 1f : 0f;
                }
                doorRuntimes.add(runtime);
                totalFound += found;
                Log.i(TAG, String.format(Locale.US,
                        "Kapı %s: %d/%d hinge=(%.3f,%.3f,%.3f) open=%.0f° target=%s",
                        spec.id, found, spec.partNames.length,
                        runtime.hingeLocal[0], runtime.hingeLocal[1], runtime.hingeLocal[2],
                        spec.openDeg, runtime.openTarget ? "open" : "closed"));
            }
        }
        doorsPartsResolved = true;
        Log.i(TAG, "Kapı toplam parça: " + totalFound + " / grup: " + doorRuntimes.size());
    }

    /** Kapı menteşesi: panel AABB ön kenarı (min X), Y dikey aks. */
    private float[] computeDoorHinge(
            int lidEntity,
            TransformManager tm,
            RenderableManager rm,
            DoorSpec spec) {
        int renderEntity = findFirstRenderable(lidEntity, tm, rm);
        if (renderEntity == 0) {
            return spec.fallbackHinge.clone();
        }
        Box box = rm.getAxisAlignedBoundingBox(rm.getInstance(renderEntity), new Box());
        float[] center = box.getCenter();
        float[] half = box.getHalfExtent();
        float[] hinge = {
                center[0] - half[0],
                center[1],
                center[2]
        };
        // Kabaca araç gövdesi aralığı
        if (hinge[0] < -1.5f || hinge[0] > 1.5f
                || Math.abs(hinge[2]) < 0.3f || Math.abs(hinge[2]) > 1.3f) {
            Log.w(TAG, String.format(Locale.US,
                    "Kapı %s hinge AABB beklenmeyen: (%.3f,%.3f,%.3f) → fallback",
                    spec.id, hinge[0], hinge[1], hinge[2]));
            return spec.fallbackHinge.clone();
        }
        return hinge;
    }

    private void applyDoorsTransforms() {
        if (!doorsPartsResolved || doorRuntimes.isEmpty() || modelViewer == null) {
            return;
        }
        TransformManager tm = modelViewer.getEngine().getTransformManager();
        for (DoorRuntime door : doorRuntimes) {
            if (door.hingeLocal == null || door.restTransforms.isEmpty()) {
                continue;
            }
            float angle = door.spec.openDeg * door.openFraction;
            Matrix.setRotateM(doorRotM, 0, angle, 0f, 1f, 0f);
            float hx = door.hingeLocal[0];
            float hy = door.hingeLocal[1];
            float hz = door.hingeLocal[2];
            Matrix.setIdentityM(doorToPivotM, 0);
            Matrix.translateM(doorToPivotM, 0, -hx, -hy, -hz);
            Matrix.setIdentityM(doorFromPivotM, 0);
            Matrix.translateM(doorFromPivotM, 0, hx, hy, hz);
            Matrix.multiplyMM(doorTmpM, 0, doorRotM, 0, doorToPivotM, 0);
            Matrix.multiplyMM(doorSpinM, 0, doorFromPivotM, 0, doorTmpM, 0);

            for (Map.Entry<Integer, float[]> entry : door.restTransforms.entrySet()) {
                int entity = entry.getKey();
                if (!tm.hasComponent(entity)) {
                    continue;
                }
                Matrix.multiplyMM(doorOutM, 0, doorSpinM, 0, entry.getValue(), 0);
                tm.setTransform(tm.getInstance(entity), doorOutM);
            }
        }
    }

    private void clearDoorsParts() {
        for (DoorRuntime door : doorRuntimes) {
            if (door.animator != null) {
                door.animator.cancel();
                door.animator = null;
            }
        }
        doorsPartsResolved = false;
        doorRuntimes.clear();
    }

    private void resetCameraHome() {
        if (cameraManipulator == null) {
            return;
        }
        try {
            cameraManipulator.jumpToBookmark(cameraManipulator.getHomeBookmark());
        } catch (Throwable error) {
            Log.w(TAG, "Kamera home bookmark uygulanamadı", error);
        }
    }

    /** ModelViewer private manipulator alanını yeni home ile değiştirir. */
    private void replaceCameraManipulator() {
        if (modelViewer == null) {
            return;
        }
        cameraManipulator = new Manipulator.Builder()
                .viewport(Math.max(1, surfaceView.getWidth()), Math.max(1, surfaceView.getHeight()))
                .targetPosition(modelCenterX, modelCenterY, modelCenterZ)
                .orbitHomePosition(cameraEyeX, cameraEyeY, cameraEyeZ)
                .orbitSpeed(ORBIT_MANIP_SPEED, ORBIT_MANIP_SPEED)
                .panning(Boolean.FALSE)
                .build(Manipulator.Mode.ORBIT);
        try {
            java.lang.reflect.Field field = ModelViewer.class.getDeclaredField("cameraManipulator");
            field.setAccessible(true);
            field.set(modelViewer, cameraManipulator);
        } catch (Throwable error) {
            Log.w(TAG, "Manipulator değiştirilemedi; sadece model pozu güncellenecek", error);
        }
    }

    private ByteBuffer readModelBuffer() {
        try (InputStream input = getContext().getAssets().open(MODEL_ASSET)) {
            byte[] bytes = new byte[input.available()];
            int read = input.read(bytes);
            if (read <= 0) {
                return null;
            }
            ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
            buffer.order(ByteOrder.nativeOrder());
            buffer.put(bytes);
            buffer.flip();
            return buffer;
        } catch (IOException e) {
            Log.e(TAG, "GLB dosyası açılamadı", e);
            return null;
        }
    }
}
