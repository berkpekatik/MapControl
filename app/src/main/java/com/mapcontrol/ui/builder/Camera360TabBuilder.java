package com.mapcontrol.ui.builder;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.dvr.CameraGlEngine;
import com.mapcontrol.dvr.CameraHub;
import com.mapcontrol.dvr.CameraRecorder;
import com.mapcontrol.dvr.DvrController;
import com.mapcontrol.dvr.HabNative;
import com.mapcontrol.ui.theme.UiStyles;

import java.io.File;
import java.util.Locale;

/**
 * Kamera 360 sekmesi: once 4 secenek (Canli Onizleme / USB Kayit / Arac Ici Kayit / Izleme Ekrani).
 * Kayit {@link DvrController}'dadir; bu sinif yalnizca arayuzdur.
 * Performans kurali: akis + render yalnizca secili gorunum acikken (veya kayit
 * surerken) calisir; menu gorunumunde hicbir sey calismaz.
 * Akislar {@link CameraHub}'da (kamera basina tek thread), cizim {@link CameraGlEngine}'de.
 */
public class Camera360TabBuilder {
    private static final int CAM_COUNT = 4;
    private static final long MIN_FIT_MINUTES = 10;
    private static final String HUB_TAG_RECORD_VIEW = "record-view";
    /** Kayit gorunumu bu genislikten itibaren iki sutun. */
    private static final int WIDE_LAYOUT_DP = 900;

    private static final int COLOR_REC = 0xFFE53935;
    private static final int COLOR_REC_PRESSED = 0xFFC62828;
    private static final int COLOR_LIVE = 0xFF43A047;
    private static final int COLOR_WAIT = 0xFFFFB300;
    private static final int COLOR_IDLE = 0xFF9E9E9E;
    private static final int COLOR_FPS = 0xFF7CFC9A;
    private static final int COLOR_NO_SIGNAL = 0xFFFFD54F;
    private static final int OVERLAY_BG = 0xB3101418;

    private enum Mode { MENU, PREVIEW, RECORD, AUTO, VIEWER }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CameraHub hub = CameraHub.get();
    private final CameraGlEngine engine = CameraGlEngine.get();
    private final DvrController dvr;
    private java.util.function.Consumer<Boolean> wideListener;
    private final CameraRecorder recorder;
    private final Camera360Ui ui;
    private Camera360AutoView autoView;
    private Camera360ClipViewer clipViewer;
    private View autoRoot;
    private View viewerRoot;
    private TextView menuUsbSub;
    private TextView menuAutoSub;
    private View menuUsbCard;
    private View menuAutoCard;
    private TextView lastMenuCardSub;
    private UiStyles.BinarySegmentHandle usbAutoSegment;
    private LinearLayout usbAutoColumn;
    private TextView usbAutoStatus;
    private TextView usbBlockedNote;

    private Mode mode = Mode.MENU;
    private boolean tabActive;
    /** Surround kameralar yalnizca arac unitesinde (/dev/hab) vardir; emulatorde yoktur. */
    private final boolean cameraSupported = new File("/dev/hab").exists();

    private View rootView;
    private LinearLayout menuView;
    private LinearLayout previewView;
    private View recordView;

    private SurfaceView previewSurfaceView;
    private LinearLayout stripOverlay;
    private FrameLayout bigOverlay;
    private TextView bigName;
    private GradientDrawable bigNameDot;
    private TextView bigFps;
    private TextView bigNoSignal;
    private TextView statusView;
    private TextView liveCountView;
    private GradientDrawable liveCountDot;
    private final TextView[] nameViews = new TextView[CAM_COUNT];
    private final GradientDrawable[] nameDots = new GradientDrawable[CAM_COUNT];
    private final TextView[] fpsViews = new TextView[CAM_COUNT];
    private final TextView[] noSignalViews = new TextView[CAM_COUNT];

    private TextView menuRecBanner;
    private TextView usbStatusView;
    private TextView usbStateView;
    private View usbStatePill;
    private TextView usbPathView;
    private TextView usbFreeView;
    private TextView usbTotalView;
    private TextView usbFitView;
    private android.widget.ProgressBar storageBar;
    private View usbDot;
    private TextView recStatusView;
    private Button recButton;
    private LinearLayout recBadge;
    private TextView recBadgeText;
    private View recPulseDot;
    private ObjectAnimator recPulse;
    private final View[] camDots = new View[CAM_COUNT];
    private final TextView[] camStateViews = new TextView[CAM_COUNT];
    private final GradientDrawable[] camTileBgs = new GradientDrawable[CAM_COUNT];
    private File usbRoot;

    // --- Kayit ayarlari (kayit baslamadan secilir, kayit suresince kilitli) ---
    private static final String PREFS = "camera360";
    private int selectedResIndex = 0;
    private boolean separateRecording;
    private boolean recordStartPending;
    private LinearLayout settingsContainer;
    private UiStyles.TernarySegmentHandle resSegment;
    private UiStyles.BinarySegmentHandle separateSegment;
    private TextView estimateView;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    /** recTick kuyrukta veya calisiyor (startRecTicker tekrar kurmaz). */
    private boolean recTickerRunning;

    private boolean previewRunning;
    /** Kayit gorunumu acikken sinyal noktalari icin akislar tutulur. */
    private boolean recordViewHoldsStreams;

    private final CameraHub.StateListener camStateListener = (cam, state) -> updateCamIndicators();
    private final CameraGlEngine.FpsListener fpsListener = (cam, fps) -> {
        if (!previewRunning) return;
        String text = fps + " fps";
        setFpsText(fpsViews[cam], text);
        if (engine.getBigCam() == cam) setFpsText(bigFps, text);
    };

    private final java.util.function.Supplier<Integer> speedSupplier;
    private final java.util.function.Supplier<String> gearSupplier;

    public Camera360TabBuilder(Context context) {
        this(context, null, null);
    }

    public Camera360TabBuilder(Context context, java.util.function.Supplier<Integer> speedSupplier,
                               java.util.function.Supplier<String> gearSupplier) {
        this.context = context;
        this.speedSupplier = speedSupplier;
        this.gearSupplier = gearSupplier;
        this.ui = new Camera360Ui(context);
        this.dvr = DvrController.get(context);
        this.recorder = dvr.recorder();
        dvr.setOverlaySuppliers(speedSupplier, gearSupplier);
        dvr.start();
        selectedResIndex = dvr.getUsbResIndex();
        separateRecording = dvr.getUsbSeparate();
        build();
    }

    public View getTabContent() {
        return rootView;
    }

    private String camName(int idx) {
        int id = HabNative.CAMERA_IDS[idx];
        int nameRes;
        switch (id) {
            case 5: nameRes = R.string.camera360_front; break;
            case 4: nameRes = R.string.camera360_right; break;
            case 8: nameRes = R.string.camera360_left; break;
            case 9: nameRes = R.string.camera360_back; break;
            default: return "id=" + id;
        }
        return context.getString(nameRes) + " " + context.getString(R.string.camera360_camera_word);
    }

    private String camShortName(int idx) {
        int id = HabNative.CAMERA_IDS[idx];
        switch (id) {
            case 5: return context.getString(R.string.camera360_front);
            case 4: return context.getString(R.string.camera360_right);
            case 8: return context.getString(R.string.camera360_left);
            case 9: return context.getString(R.string.camera360_back);
            default: return "id=" + id;
        }
    }

    public View build() {
        int pad = dp(10);

        FrameLayout root = new FrameLayout(context);
        root.setPadding(pad, pad, pad, pad);

        menuView = buildMenuView();
        root.addView(menuView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        previewView = buildPreviewView();
        previewView.setVisibility(View.GONE);
        root.addView(previewView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        recordView = buildRecordView();
        recordView.setVisibility(View.GONE);
        root.addView(recordView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        autoView = new Camera360AutoView(ui, dvr, this::enterMenu, this::enterViewer);
        autoRoot = autoView.build();
        autoRoot.setVisibility(View.GONE);
        root.addView(autoRoot, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        clipViewer = new Camera360ClipViewer(ui, dvr, this::enterMenu, wide -> {
            java.util.function.Consumer<Boolean> l = wideListener;
            if (l != null) l.accept(wide);
        });
        viewerRoot = clipViewer.build();
        viewerRoot.setVisibility(View.GONE);
        root.addView(viewerRoot, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        rootView = root;
        return root;
    }

    /**
     * Gece/gündüz değişince ağacı güncel paletle kurar. Kayıt sürer; açık mod korunur.
     * Renkler build anında çözüldüğü için yeniden kurmadan butonlar eski temada kalır.
     */
    public View rebuildForUiMode() {
        Mode keep = mode;
        boolean active = tabActive;
        boolean keepWide = clipViewer != null && clipViewer.isWide();
        if (active) {
            onTabHidden();
        } else {
            leaveCurrentMode();
        }
        if (recPulse != null) {
            recPulse.cancel();
            recPulse = null;
        }
        build();
        showModeShell(keep);
        if (active) {
            onTabVisible();
        }
        if (keepWide && clipViewer != null) {
            clipViewer.restoreWide(true);
        }
        return rootView;
    }

    private void showModeShell(Mode keep) {
        mode = keep;
        switch (keep) {
            case PREVIEW:
                showOnly(previewView);
                break;
            case RECORD:
                showOnly(recordView);
                break;
            case AUTO:
                showOnly(autoRoot);
                break;
            case VIEWER:
                showOnly(viewerRoot);
                break;
            default:
                showOnly(menuView);
                break;
        }
    }

    /** Izleme ekrani sol menunun gizlenmesini (true) / geri gelmesini (false) ister. */
    public void setWideListener(java.util.function.Consumer<Boolean> listener) {
        wideListener = listener;
    }

    private void showOnly(View visible) {
        View[] all = {menuView, previewView, recordView, autoRoot, viewerRoot};
        for (View v : all) {
            if (v != null) v.setVisibility(v == visible ? View.VISIBLE : View.GONE);
        }
    }

    /** Kayit kaynagi USB (elle veya otomatik) ve recorder mesgul. */
    private boolean usbActive() {
        DvrController.Source s = dvr.getSource();
        return s == DvrController.Source.MANUAL_USB || s == DvrController.Source.AUTO_USB;
    }

    private boolean usbRecording() {
        return dvr.isRecordingUsb();
    }

    /** Arac ici otomatik mod acikken USB kaydi kullanilamaz. */
    private boolean usbBlocked() {
        return dvr.isAutoInternalEnabled();
    }

    // ---------- Gorsel yardimcilar ----------

    private int dp(float v) {
        return ui.dp(v);
    }

    private int themeColor(int colorRes) {
        return ui.themeColor(colorRes);
    }

    private static int withAlpha(int color, int alpha) {
        return Camera360Ui.withAlpha(color, alpha);
    }

    private GradientDrawable roundRect(int fill, float radiusDp) {
        return ui.roundRect(fill, radiusDp);
    }

    private static GradientDrawable oval(int fill) {
        return Camera360Ui.oval(fill);
    }

    /** Icerik arka planinin uzerine dokunma dalgasi (ripple) ekler ve gorunumu tiklanabilir yapar. */
    private void setPressableBackground(View view, Drawable content, float radiusDp) {
        ui.setPressableBackground(view, content, radiusDp);
    }

    private LinearLayout card() {
        return ui.card();
    }

    private TextView sectionLabel(int textRes) {
        return ui.sectionLabel(textRes);
    }

    private FrameLayout iconBadge(int iconRes, int tint, int fill, int sizeDp, int iconDp, boolean circle) {
        return ui.iconBadge(iconRes, tint, fill, sizeDp, iconDp, circle);
    }

    /** Metnin basina renklendirilebilir durum noktasi koyar. */
    private GradientDrawable attachDot(TextView tv, int color, int sizeDp) {
        return ui.attachDot(tv, color, sizeDp);
    }

    private void attachIcon(TextView tv, int iconRes, int tint, int sizeDp) {
        ui.attachIcon(tv, iconRes, tint, sizeDp);
    }

    private static int stateColor(int hubState) {
        switch (hubState) {
            case CameraHub.STATE_LIVE: return COLOR_LIVE;
            case CameraHub.STATE_NO_SIGNAL: return COLOR_REC;
            case CameraHub.STATE_CONNECTING: return COLOR_WAIT;
            default: return COLOR_IDLE;
        }
    }

    /** Alt gorunumlerin ust satiri: geri butonu + baslik; sag tarafa cagiran ekler. */
    private LinearLayout buildHeader(int titleRes) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout back = new LinearLayout(context);
        back.setOrientation(LinearLayout.HORIZONTAL);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(dp(12), 0, dp(16), 0);
        setPressableBackground(back, roundRect(themeColor(R.color.surfaceCardInner), 22), 22);
        back.setOnClickListener(v -> enterMenu());
        ImageView arrow = new ImageView(context);
        arrow.setImageResource(R.drawable.ic_hub_arrow_left);
        arrow.setColorFilter(themeColor(R.color.textPrimary));
        back.addView(arrow, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView backText = new TextView(context);
        backText.setText(R.string.camera360_title);
        backText.setTextSize(15);
        backText.setTypeface(null, Typeface.BOLD);
        backText.setTextColor(themeColor(R.color.textPrimary));
        LinearLayout.LayoutParams btp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btp.setMarginStart(dp(8));
        back.addView(backText, btp);
        row.addView(back, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)));

        TextView title = new TextView(context);
        title.setText(titleRes);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(themeColor(R.color.textPrimary));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMarginStart(dp(16));
        tp.setMarginEnd(dp(12));
        row.addView(title, tp);
        return row;
    }

    // ---------- Menu gorunumu ----------

    private LinearLayout buildMenuView() {
        LinearLayout menu = new LinearLayout(context);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setGravity(Gravity.CENTER);
        int side = dp(16);
        menu.setPadding(side, 0, side, 0);

        LinearLayout heading = new LinearLayout(context);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        int accent = themeColor(R.color.oemAccent);
        heading.addView(iconBadge(R.drawable.ic_mdi_camera, accent,
                themeColor(R.color.surfaceCardInner), 52, 28, false));
        LinearLayout headingText = new LinearLayout(context);
        headingText.setOrientation(LinearLayout.VERTICAL);
        headingText.setPadding(dp(14), 0, 0, 0);
        TextView title = new TextView(context);
        title.setText(R.string.camera360_title);
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(themeColor(R.color.textPrimary));
        headingText.addView(title);
        TextView subtitle = new TextView(context);
        subtitle.setText(R.string.camera360_menu_title);
        subtitle.setTextSize(14);
        subtitle.setTextColor(themeColor(R.color.textSecondaryCool));
        headingText.addView(subtitle);
        heading.addView(headingText);
        menu.addView(heading, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout cards = new LinearLayout(context);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cardsP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardsP.setMargins(0, dp(18), 0, 0);
        menu.addView(cards, cardsP);

        cards.addView(makeMenuCard(
                iconBadge(R.drawable.ic_mdi_play, accent, withAlpha(accent, 0x2A), 68, 36, true),
                R.string.camera360_menu_live, R.string.camera360_menu_live_sub,
                v -> enterPreview()));
        menuUsbCard = makeMenuCard(recordGlyph(68),
                R.string.camera360_menu_record, R.string.camera360_menu_record_sub,
                v -> enterRecord());
        menuUsbSub = lastMenuCardSub;
        cards.addView(menuUsbCard);

        LinearLayout cards2 = new LinearLayout(context);
        cards2.setOrientation(LinearLayout.HORIZONTAL);
        cards2.setGravity(Gravity.CENTER);
        menu.addView(cards2, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        menuAutoCard = makeMenuCard(
                iconBadge(R.drawable.ic_mdi_car, COLOR_REC, withAlpha(COLOR_REC, 0x2A), 68, 36, true),
                R.string.camera360_menu_auto, R.string.camera360_menu_auto_sub_off,
                v -> enterAuto());
        menuAutoSub = lastMenuCardSub;
        cards2.addView(menuAutoCard);
        cards2.addView(makeMenuCard(
                iconBadge(R.drawable.ic_mdi_view_grid, accent, withAlpha(accent, 0x2A), 68, 36, true),
                R.string.camera360_menu_viewer, R.string.camera360_menu_viewer_sub,
                v -> enterViewer()));

        menuRecBanner = new TextView(context);
        menuRecBanner.setText(R.string.camera360_rec_banner);
        menuRecBanner.setTextSize(15);
        menuRecBanner.setTypeface(null, Typeface.BOLD);
        menuRecBanner.setTextColor(COLOR_REC);
        menuRecBanner.setGravity(Gravity.CENTER);
        menuRecBanner.setPadding(dp(20), dp(10), dp(20), dp(10));
        GradientDrawable bannerBg = roundRect(withAlpha(COLOR_REC, 0x24), 22);
        bannerBg.setStroke(dp(1), withAlpha(COLOR_REC, 0x66));
        setPressableBackground(menuRecBanner, bannerBg, 22);
        menuRecBanner.setVisibility(View.GONE);
        menuRecBanner.setOnClickListener(v -> {
            if (dvr.isRecordingInternal()) enterAuto();
            else enterRecord();
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, dp(18), 0, 0);
        menu.addView(menuRecBanner, bp);
        return menu;
    }

    /** Kayit ikonu: kirmizi halka icinde dolu nokta. */
    private FrameLayout recordGlyph(int sizeDp) {
        FrameLayout glyph = new FrameLayout(context);
        glyph.setBackground(oval(withAlpha(COLOR_REC, 0x2A)));
        View ring = new View(context);
        GradientDrawable ringBg = oval(Color.TRANSPARENT);
        ringBg.setStroke(dp(3), COLOR_REC);
        ring.setBackground(ringBg);
        glyph.addView(ring, new FrameLayout.LayoutParams(dp(sizeDp * 0.53f), dp(sizeDp * 0.53f), Gravity.CENTER));
        View dot = new View(context);
        dot.setBackground(oval(COLOR_REC));
        glyph.addView(dot, new FrameLayout.LayoutParams(dp(sizeDp * 0.29f), dp(sizeDp * 0.29f), Gravity.CENTER));
        glyph.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return glyph;
    }

    private LinearLayout makeMenuCard(View icon, int titleRes, int subRes, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(22), dp(20), dp(16), dp(20));
        GradientDrawable bg = roundRect(themeColor(R.color.surfaceCard), 22);
        bg.setStroke(dp(1), themeColor(R.color.outline));
        setPressableBackground(card, bg, 22);
        card.setOnClickListener(click);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(124), 1f);
        int m = dp(8);
        cp.setMargins(m, m, m, m);
        card.setLayoutParams(cp);

        card.addView(icon);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsP = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textsP.setMarginStart(dp(18));
        textsP.setMarginEnd(dp(8));
        card.addView(texts, textsP);

        TextView title = new TextView(context);
        title.setText(titleRes);
        title.setTextSize(21);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(themeColor(R.color.textPrimary));
        texts.addView(title);

        TextView sub = new TextView(context);
        sub.setText(subRes);
        sub.setTextSize(14);
        sub.setMaxLines(2);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        sub.setTextColor(themeColor(R.color.textSecondaryCool));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(4);
        texts.addView(sub, sp);
        lastMenuCardSub = sub;

        ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.ic_mdi_chevron_up);
        chevron.setRotation(90f);
        chevron.setColorFilter(themeColor(R.color.textMuted));
        card.addView(chevron, new LinearLayout.LayoutParams(dp(28), dp(28)));
        return card;
    }

    // ---------- Onizleme gorunumu ----------

    private LinearLayout buildPreviewView() {
        int pad = dp(10);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = buildHeader(R.string.camera360_menu_live);
        liveCountView = new TextView(context);
        liveCountView.setTextSize(14);
        liveCountView.setTypeface(null, Typeface.BOLD);
        liveCountView.setTextColor(themeColor(R.color.textPrimary));
        liveCountView.setPadding(dp(12), dp(6), dp(14), dp(6));
        liveCountView.setBackground(roundRect(themeColor(R.color.surfaceCardInner), 16));
        liveCountDot = attachDot(liveCountView, COLOR_IDLE, 9);
        liveCountView.setVisibility(View.GONE);
        header.addView(liveCountView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams headerP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headerP.bottomMargin = dp(10);
        root.addView(header, headerP);

        FrameLayout area = new FrameLayout(context);
        area.setBackgroundColor(Color.BLACK);
        root.addView(area, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        previewSurfaceView = new SurfaceView(context);
        previewSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                engine.setPreviewSurface(holder.getSurface(), width, height);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                engine.clearPreviewSurface();
            }
        });
        previewSurfaceView.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                handleTap(e.getX(), e.getY(), v.getWidth(), v.getHeight());
                return true;
            }
            return true;
        });
        area.addView(previewSurfaceView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        stripOverlay = new LinearLayout(context);
        stripOverlay.setOrientation(LinearLayout.VERTICAL);
        stripOverlay.setClickable(false);
        for (int row = 0; row < 2; row++) {
            LinearLayout rowLayout = new LinearLayout(context);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setClickable(false);
            stripOverlay.addView(rowLayout, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            for (int col = 0; col < 2; col++) {
                int pos = row * 2 + col;
                int cam = CameraGlEngine.GRID_ORDER[pos];
                FrameLayout cellTop = new FrameLayout(context);
                cellTop.setClickable(false);
                rowLayout.addView(cellTop, new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

                TextView name = overlayPill(camName(cam));
                name.setTextSize(15);
                name.setTypeface(null, Typeface.BOLD);
                nameDots[cam] = attachDot(name, COLOR_IDLE, 9);
                FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                np.gravity = Gravity.TOP | Gravity.START;
                np.setMargins(pad, pad, pad, pad);
                cellTop.addView(name, np);
                nameViews[cam] = name;

                TextView fps = fpsPill();
                FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                fp.gravity = Gravity.TOP | Gravity.END;
                fp.setMargins(pad, pad, pad, pad);
                cellTop.addView(fps, fp);
                fpsViews[cam] = fps;

                TextView noSignal = noSignalChip();
                cellTop.addView(noSignal, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER));
                noSignalViews[cam] = noSignal;
            }
        }
        area.addView(stripOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        bigOverlay = new FrameLayout(context);
        bigOverlay.setVisibility(View.GONE);
        bigOverlay.setClickable(false);

        TextView backAll = overlayPill(context.getString(R.string.camera360_all));
        backAll.setTextSize(15);
        backAll.setTypeface(null, Typeface.BOLD);
        backAll.setGravity(Gravity.CENTER_VERTICAL);
        backAll.setPadding(dp(14), 0, dp(16), 0);
        attachIcon(backAll, R.drawable.ic_mdi_view_grid, Color.WHITE, 18);
        setPressableBackground(backAll, roundRect(OVERLAY_BG, 20), 20);
        backAll.setOnClickListener(v -> showStrip());
        FrameLayout.LayoutParams backAllP = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, dp(40));
        backAllP.gravity = Gravity.TOP | Gravity.START;
        backAllP.setMargins(pad, pad, pad, pad);
        bigOverlay.addView(backAll, backAllP);

        bigName = overlayPill("");
        bigName.setTextSize(15);
        bigName.setTypeface(null, Typeface.BOLD);
        bigNameDot = attachDot(bigName, COLOR_IDLE, 9);
        FrameLayout.LayoutParams bnP = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        bnP.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        bnP.setMargins(pad, pad, pad, pad);
        bigOverlay.addView(bigName, bnP);

        bigFps = fpsPill();
        bigFps.setTextSize(15);
        FrameLayout.LayoutParams bfP = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        bfP.gravity = Gravity.TOP | Gravity.END;
        bfP.setMargins(pad, pad, pad, pad);
        bigOverlay.addView(bigFps, bfP);

        bigNoSignal = noSignalChip();
        bigOverlay.addView(bigNoSignal, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        area.addView(bigOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        statusView = new TextView(context);
        statusView.setText(R.string.camera360_hint);
        statusView.setTextSize(13);
        statusView.setMaxLines(2);
        statusView.setEllipsize(TextUtils.TruncateAt.END);
        statusView.setTextColor(themeColor(R.color.textSecondaryCool));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.setMargins(dp(4), dp(8), dp(4), 0);
        root.addView(statusView, sp);
        return root;
    }

    // ---------- Kayit gorunumu ----------

    private View buildRecordView() {
        boolean wide = context.getResources().getConfiguration().screenWidthDp >= WIDE_LAYOUT_DP;
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, dp(12));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout header = buildHeader(R.string.camera360_menu_record);
        header.addView(buildRecBadge(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams headerP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headerP.bottomMargin = dp(12);
        root.addView(header, headerP);

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout left = new LinearLayout(context);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout right = new LinearLayout(context);
        right.setOrientation(LinearLayout.VERTICAL);
        if (wide) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginEnd(dp(6));
            body.addView(left, lp);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            rp.setMarginStart(dp(6));
            body.addView(right, rp);
        } else {
            body.addView(left, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rp.topMargin = dp(12);
            body.addView(right, rp);
        }

        left.addView(buildUsbCard(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sigP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sigP.topMargin = dp(12);
        left.addView(buildSignalsCard(), sigP);

        right.addView(buildSettingsCard(wide), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams recP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        recP.topMargin = dp(12);
        right.addView(buildRecordCard(), recP);

        // Eski alan uyumu: usbStatusView artik kullanilmiyor ama null kalmasin
        usbStatusView = usbPathView;
        return scroll;
    }

    /** Baslikta kayit surerken gorunen yanip sonen "KAYIT 00:00" rozeti. */
    private LinearLayout buildRecBadge() {
        recBadge = new LinearLayout(context);
        recBadge.setOrientation(LinearLayout.HORIZONTAL);
        recBadge.setGravity(Gravity.CENTER_VERTICAL);
        recBadge.setPadding(dp(12), dp(6), dp(14), dp(6));
        GradientDrawable bg = roundRect(withAlpha(COLOR_REC, 0x26), 16);
        bg.setStroke(dp(1), withAlpha(COLOR_REC, 0x66));
        recBadge.setBackground(bg);
        recBadge.setVisibility(View.GONE);

        recPulseDot = new View(context);
        recPulseDot.setBackground(oval(COLOR_REC));
        LinearLayout.LayoutParams dotP = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotP.setMarginEnd(dp(8));
        recBadge.addView(recPulseDot, dotP);

        recBadgeText = new TextView(context);
        recBadgeText.setTextSize(14);
        recBadgeText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        recBadgeText.setTextColor(COLOR_REC);
        recBadge.addView(recBadgeText);
        return recBadge;
    }

    private LinearLayout buildUsbCard() {
        LinearLayout card = card();

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(head);

        head.addView(iconBadge(R.drawable.ic_mdi_usb, themeColor(R.color.oemAccent),
                themeColor(R.color.surfaceCardInner), 44, 26, false));

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titlesP = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titlesP.setMarginStart(dp(12));
        titlesP.setMarginEnd(dp(8));
        head.addView(titles, titlesP);

        TextView heroTitle = new TextView(context);
        heroTitle.setText(R.string.camera360_usb_title2);
        heroTitle.setTextSize(17);
        heroTitle.setTypeface(null, Typeface.BOLD);
        heroTitle.setTextColor(themeColor(R.color.textPrimary));
        titles.addView(heroTitle);

        usbPathView = new TextView(context);
        usbPathView.setTextSize(12);
        usbPathView.setTextColor(themeColor(R.color.textSecondary));
        usbPathView.setSingleLine(true);
        usbPathView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams pathP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pathP.topMargin = dp(2);
        titles.addView(usbPathView, pathP);

        LinearLayout statePill = new LinearLayout(context);
        statePill.setOrientation(LinearLayout.HORIZONTAL);
        statePill.setGravity(Gravity.CENTER_VERTICAL);
        statePill.setPadding(dp(10), dp(5), dp(12), dp(5));
        usbStatePill = statePill;
        head.addView(statePill);

        usbDot = new View(context);
        usbDot.setBackground(oval(COLOR_IDLE));
        LinearLayout.LayoutParams dotP = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotP.setMarginEnd(dp(6));
        statePill.addView(usbDot, dotP);

        usbStateView = new TextView(context);
        usbStateView.setTextSize(13);
        usbStateView.setTypeface(null, Typeface.BOLD);
        statePill.addView(usbStateView);
        setDot(COLOR_IDLE);

        storageBar = new android.widget.ProgressBar(context, null,
                android.R.attr.progressBarStyleHorizontal);
        storageBar.setMax(1000);
        storageBar.setProgressDrawable(storageBarDrawable());
        LinearLayout.LayoutParams barP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(10));
        barP.setMargins(0, dp(14), 0, 0);
        card.addView(storageBar, barP);

        LinearLayout stats = new LinearLayout(context);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams statsP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statsP.setMargins(dp(-4), dp(12), dp(-4), 0);
        card.addView(stats, statsP);
        usbFreeView = addStat(stats, R.string.camera360_free);
        usbTotalView = addStat(stats, R.string.camera360_total);
        usbFitView = addStat(stats, R.string.camera360_fits);
        return card;
    }

    private Drawable storageBarDrawable() {
        GradientDrawable track = roundRect(themeColor(R.color.outline), 5);
        ClipDrawable fill = new ClipDrawable(roundRect(themeColor(R.color.oemAccent), 5),
                Gravity.START, ClipDrawable.HORIZONTAL);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{track, fill});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        return layers;
    }

    private LinearLayout buildSignalsCard() {
        LinearLayout card = card();

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(head);
        head.addView(sectionLabel(R.string.camera360_cam_signals), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageView signalsRefresh = new ImageView(context);
        signalsRefresh.setImageResource(R.drawable.ic_mdi_refresh);
        signalsRefresh.setColorFilter(themeColor(R.color.textSecondary));
        int rp = dp(8);
        signalsRefresh.setPadding(rp, rp, rp, rp);
        setPressableBackground(signalsRefresh, oval(themeColor(R.color.surfaceCardInner)), 19);
        signalsRefresh.setContentDescription(context.getString(R.string.camera360_refresh));
        signalsRefresh.setOnClickListener(v -> recheckCameras());
        // Gizli ayar: uzun basinca kamera "hizli okuma" (onbellekli tampon) acilir/kapanir.
        // Sorun cikan bir aracta yeniden derlemeden eski davranisa donmek icin.
        signalsRefresh.setOnLongClickListener(v -> {
            boolean enable = !dvr.isCameraFastReadEnabled();
            dvr.setCameraFastReadEnabled(enable);
            android.widget.Toast.makeText(context, enable ? R.string.camera360_fast_read_on
                    : R.string.camera360_fast_read_off, android.widget.Toast.LENGTH_LONG).show();
            return true;
        });
        head.addView(signalsRefresh, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout camRow = new LinearLayout(context);
        camRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowP.setMargins(dp(-4), dp(10), dp(-4), 0);
        card.addView(camRow, rowP);
        int[] displayOrder = {0, 3, 2, 1}; // On, Arka, Sol, Sag
        for (int cam : displayOrder) {
            LinearLayout tile = new LinearLayout(context);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(dp(12), dp(10), dp(10), dp(10));
            GradientDrawable tileBg = roundRect(themeColor(R.color.surfaceCardInner), 14);
            tile.setBackground(tileBg);
            camTileBgs[cam] = tileBg;
            LinearLayout.LayoutParams tileP = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tileP.setMargins(dp(4), 0, dp(4), 0);
            camRow.addView(tile, tileP);

            LinearLayout top = new LinearLayout(context);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            tile.addView(top);

            View dot = new View(context);
            dot.setBackground(oval(COLOR_IDLE));
            LinearLayout.LayoutParams dotP = new LinearLayout.LayoutParams(dp(10), dp(10));
            dotP.setMarginEnd(dp(8));
            top.addView(dot, dotP);
            camDots[cam] = dot;

            TextView label = new TextView(context);
            label.setText(camShortName(cam));
            label.setTextSize(15);
            label.setTypeface(null, Typeface.BOLD);
            label.setTextColor(themeColor(R.color.textPrimary));
            label.setSingleLine(true);
            top.addView(label);

            TextView state = new TextView(context);
            state.setText(R.string.camera360_sig_wait);
            state.setTextSize(12);
            state.setTextColor(themeColor(R.color.textSecondary));
            state.setSingleLine(true);
            state.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams stateP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            stateP.topMargin = dp(4);
            tile.addView(state, stateP);
            camStateViews[cam] = state;
        }
        return card;
    }

    private LinearLayout buildSettingsCard(boolean wide) {
        LinearLayout card = card();
        card.setPadding(dp(2), dp(16), dp(2), dp(14));
        TextView label = sectionLabel(R.string.camera360_settings_title);
        int labelInset = UiStyles.dimenPx(context, R.dimen.spacing_medium);
        label.setPadding(labelInset, 0, labelInset, 0);
        card.addView(label);

        // Iki sutunlu duzende sag sutun dar: segmentler alt alta; tek sutunda yan yana.
        settingsContainer = new LinearLayout(context);
        settingsContainer.setOrientation(wide ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        card.addView(settingsContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout resColumn = new LinearLayout(context);
        resColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout sepColumn = new LinearLayout(context);
        sepColumn.setOrientation(LinearLayout.VERTICAL);
        if (wide) {
            settingsContainer.addView(resColumn, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            settingsContainer.addView(sepColumn, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        } else {
            settingsContainer.addView(resColumn, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            settingsContainer.addView(sepColumn, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }

        String[] resLabels = new String[CameraRecorder.RESOLUTIONS.length];
        for (int i = 0; i < resLabels.length; i++) {
            int[] wh = CameraRecorder.RESOLUTIONS[i];
            resLabels[i] = wh[0] + "\u00d7" + wh[1];
        }
        resSegment = UiStyles.addTernarySegmentedControl(context, resColumn,
                context.getString(R.string.camera360_resolution),
                resLabels,
                new String[]{
                        context.getString(R.string.camera360_res_help_high),
                        context.getString(R.string.camera360_res_help_mid),
                        context.getString(R.string.camera360_res_help_low)},
                new int[]{0, 1, 2},
                selectedResIndex,
                modeId -> {
                    if (usbActive() || recordStartPending) {
                        resSegment.syncVisualFromModeId(selectedResIndex);
                        return;
                    }
                    selectedResIndex = modeId;
                    saveRecordSettings();
                    updateResolutionUi();
                    refreshUsbInfo();
                });
        hideSegmentHelp(resColumn);

        separateSegment = UiStyles.addBinarySegmentedControl(context, sepColumn,
                context.getString(R.string.camera360_separate_short),
                context.getString(R.string.common_yes), context.getString(R.string.common_no),
                context.getString(R.string.camera360_separate_help_on),
                context.getString(R.string.camera360_separate_help_off),
                separateRecording,
                enabled -> {
                    if (usbActive() || recordStartPending) {
                        separateSegment.syncVisualWithoutCommit(separateRecording);
                        return;
                    }
                    separateRecording = enabled;
                    saveRecordSettings();
                    updateResolutionUi();
                    refreshUsbInfo();
                });
        hideSegmentHelp(sepColumn);

        estimateView = new TextView(context);
        estimateView.setTextSize(13);
        estimateView.setTextColor(themeColor(R.color.textSecondaryCool));
        estimateView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams estP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        estP.setMargins(labelInset, dp(10), labelInset, 0);
        card.addView(estimateView, estP);

        usbAutoColumn = new LinearLayout(context);
        usbAutoColumn.setOrientation(LinearLayout.VERTICAL);
        card.addView(usbAutoColumn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        usbAutoSegment = UiStyles.addBinarySegmentedControl(context, usbAutoColumn,
                context.getString(R.string.camera360_usb_auto),
                context.getString(R.string.common_yes), context.getString(R.string.common_no),
                "", "", dvr.isAutoUsbEnabled(),
                enabled -> {
                    if (!dvr.setAutoUsbEnabled(enabled)) {
                        usbAutoSegment.syncVisualWithoutCommit(dvr.isAutoUsbEnabled());
                    }
                    refreshUsbAutoUi();
                    refreshUsbInfo();
                });
        hideSegmentHelp(usbAutoColumn);
        TextView autoNote = new TextView(context);
        autoNote.setText(R.string.camera360_usb_auto_note);
        autoNote.setTextSize(12);
        autoNote.setTextColor(themeColor(R.color.textSecondaryCool));
        autoNote.setPadding(labelInset, dp(6), labelInset, 0);
        usbAutoColumn.addView(autoNote);
        usbAutoStatus = new TextView(context);
        usbAutoStatus.setTextSize(13);
        usbAutoStatus.setTypeface(null, Typeface.BOLD);
        usbAutoStatus.setPadding(labelInset, dp(6), labelInset, 0);
        usbAutoStatus.setVisibility(View.GONE);
        usbAutoColumn.addView(usbAutoStatus);
        updateResolutionUi();
        return card;
    }

    /** USB otomatik kayit secenegi: arac ici mod acikken devre disi. */
    private void refreshUsbAutoUi() {
        if (usbAutoSegment == null) return;
        boolean on = dvr.isAutoUsbEnabled();
        usbAutoSegment.syncVisualWithoutCommit(on);
        boolean blocked = !on && usbBlocked();
        usbAutoSegment.setInteractionEnabled(!blocked);
        usbAutoColumn.setAlpha(blocked ? 0.5f : 1f);
        if (on) {
            DvrController.AutoStatus st = dvr.getAutoStatus();
            usbAutoStatus.setVisibility(View.VISIBLE);
            usbAutoStatus.setText(context.getString(R.string.camera360_usb_auto_status,
                    Camera360AutoView.statusLabel(ui, dvr, st)));
            usbAutoStatus.setTextColor(Camera360AutoView.statusColor(st));
        } else {
            usbAutoStatus.setVisibility(View.GONE);
        }
        if (usbBlockedNote != null) {
            usbBlockedNote.setVisibility(usbBlocked() ? View.VISIBLE : View.GONE);
        }
    }

    private LinearLayout buildRecordCard() {
        LinearLayout card = card();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(18), dp(20), dp(18), dp(16));

        recButton = new Button(context);
        recButton.setAllCaps(false);
        recButton.setTextSize(17);
        recButton.setTypeface(null, Typeface.BOLD);
        recButton.setTextColor(new ColorStateList(
                new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{0x99FFFFFF, Color.WHITE}));
        GradientDrawable recBg = new GradientDrawable();
        recBg.setCornerRadius(dp(30));
        recBg.setColor(new ColorStateList(
                new int[][]{{-android.R.attr.state_enabled}, {android.R.attr.state_pressed}, {}},
                new int[]{withAlpha(COLOR_REC, 0x59), COLOR_REC_PRESSED, COLOR_REC}));
        recButton.setBackground(recBg);
        recButton.setStateListAnimator(null);
        recButton.setMinWidth(0);
        recButton.setMinimumWidth(0);
        recButton.setPadding(dp(30), 0, dp(32), 0);
        setRecButtonRecording(false);
        recButton.setOnClickListener(v -> toggleRecording());
        card.addView(recButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(60)));

        usbBlockedNote = new TextView(context);
        usbBlockedNote.setText(R.string.camera360_usb_blocked_internal);
        usbBlockedNote.setTextSize(13);
        usbBlockedNote.setTypeface(null, Typeface.BOLD);
        usbBlockedNote.setGravity(Gravity.CENTER);
        usbBlockedNote.setTextColor(COLOR_WAIT);
        usbBlockedNote.setPadding(dp(12), dp(8), dp(12), dp(8));
        usbBlockedNote.setBackground(roundRect(withAlpha(COLOR_WAIT, 0x1F), 12));
        usbBlockedNote.setVisibility(View.GONE);
        LinearLayout.LayoutParams bnp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bnp.setMargins(0, dp(12), 0, 0);
        card.addView(usbBlockedNote, bnp);

        recStatusView = new TextView(context);
        recStatusView.setText(R.string.camera360_rec_hint);
        recStatusView.setTextSize(14);
        recStatusView.setGravity(Gravity.CENTER);
        recStatusView.setTextColor(themeColor(R.color.textPrimary));
        LinearLayout.LayoutParams rsp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rsp.setMargins(0, dp(14), 0, 0);
        card.addView(recStatusView, rsp);

        Button refresh = new Button(context);
        refresh.setText(R.string.camera360_refresh);
        refresh.setAllCaps(false);
        refresh.setTextSize(14);
        refresh.setTextColor(themeColor(R.color.textPrimary));
        UiStyles.styleOemButton(refresh, themeColor(R.color.buttonSecondaryMuted));
        UiStyles.setButtonStartIconTinted(refresh, R.drawable.ic_mdi_refresh,
                themeColor(R.color.textPrimary), dp(8));
        refresh.setStateListAnimator(null);
        refresh.setPadding(dp(16), 0, dp(20), 0);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(44));
        rp.setMargins(0, dp(12), 0, 0);
        refresh.setOnClickListener(v -> refreshUsbInfo());
        card.addView(refresh, rp);
        return card;
    }

    /** Kayit butonu: bosta dolu daire + "Kaydi Baslat", kayitta kare + "Kaydi Durdur". */
    private void setRecButtonRecording(boolean recording) {
        recButton.setText(recording ? R.string.camera360_rec_stop : R.string.camera360_rec_start);
        GradientDrawable icon = new GradientDrawable();
        icon.setColor(Color.WHITE);
        if (recording) {
            icon.setCornerRadius(dp(3));
        } else {
            icon.setShape(GradientDrawable.OVAL);
        }
        int s = dp(16);
        icon.setBounds(0, 0, s, s);
        recButton.setCompoundDrawablesRelative(icon, null, null, null);
        recButton.setCompoundDrawablePadding(dp(12));
    }

    private TextView addStat(LinearLayout parent, int labelRes) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(6), dp(10), dp(6), dp(10));
        box.setBackground(roundRect(themeColor(R.color.surfaceCardInner), 14));
        LinearLayout.LayoutParams boxP = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        boxP.setMargins(dp(4), 0, dp(4), 0);
        parent.addView(box, boxP);
        TextView value = new TextView(context);
        value.setText("-");
        value.setTextSize(19);
        value.setTypeface(null, Typeface.BOLD);
        value.setGravity(Gravity.CENTER);
        value.setSingleLine(true);
        value.setTextColor(themeColor(R.color.textPrimary));
        box.addView(value);
        TextView label = new TextView(context);
        label.setText(labelRes);
        label.setTextSize(11);
        label.setAllCaps(true);
        label.setLetterSpacing(0.06f);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(themeColor(R.color.textSecondaryCool));
        box.addView(label);
        return value;
    }

    private int selectedWidth() {
        return CameraRecorder.RESOLUTIONS[selectedResIndex][0];
    }

    private int selectedHeight() {
        return CameraRecorder.RESOLUTIONS[selectedResIndex][1];
    }

    private void saveRecordSettings() {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putInt("resIndex", selectedResIndex)
                    .putBoolean("separate", separateRecording)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1_000_000_000L) {
            return String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0);
        }
        if (bytes >= 1_000_000L) {
            return String.format(Locale.US, "%.0f MB", bytes / 1_000_000.0);
        }
        return String.format(Locale.US, "%.0f KB", bytes / 1_000.0);
    }

    private static String formatElapsed(long elapsedMs) {
        return String.format(Locale.US, "%02d:%02d", elapsedMs / 60000, (elapsedMs / 1000) % 60);
    }

    /** Segment kontrolunun altindaki cok satirli yardim metnini gizler (kompakt duzen). */
    private static void hideSegmentHelp(LinearLayout column) {
        // Yapi: [baslik, track, yardim] - yardim son cocuk
        if (column.getChildCount() > 0) {
            column.getChildAt(column.getChildCount() - 1).setVisibility(View.GONE);
        }
    }

    private void updateResolutionUi() {
        if (resSegment != null) {
            resSegment.syncVisualFromModeId(selectedResIndex);
        }
        if (separateSegment != null) {
            separateSegment.syncVisualWithoutCommit(separateRecording);
        }
        if (estimateView != null) {
            long bpm = CameraRecorder.bytesPerMinute(selectedWidth(), selectedHeight(), separateRecording);
            estimateView.setText(context.getString(
                    separateRecording ? R.string.camera360_estimate_separate
                            : R.string.camera360_estimate,
                    formatSize(bpm), formatSize(bpm * 60)));
        }
    }

    /** Kayit/bekleme varken ayar kontrolleri kilitli; degilken acik. */
    private void refreshSettingsState() {
        boolean locked = usbActive() || recordStartPending;
        if (resSegment != null) resSegment.setInteractionEnabled(!locked);
        if (separateSegment != null) separateSegment.setInteractionEnabled(!locked);
        if (settingsContainer != null) settingsContainer.setAlpha(locked ? 0.6f : 1f);
    }

    /**
     * Kayit durumunu tum gorunur kontrollere yansitir. Menu/arka plan donuslerinde
     * cagrilir; boylece ayar kilidi ve buton metni her zaman gercek durumu gosterir.
     */
    private void syncRecordingUi() {
        if (recorder.isRecording()) {
            recordStartPending = false;
        }
        if (menuRecBanner != null) {
            menuRecBanner.setVisibility(recorder.isRecording() ? View.VISIBLE : View.GONE);
            menuRecBanner.setText(dvr.isRecordingInternal()
                    ? R.string.camera360_rec_banner_internal : R.string.camera360_rec_banner);
        }
        updateMenuCards();
        if (recButton != null) {
            if (usbRecording()) {
                setRecButtonRecording(true);
                recButton.setEnabled(true);
            } else if (recorder.isBusy() || recordStartPending) {
                setRecButtonRecording(usbActive() || recordStartPending);
                recButton.setEnabled(false);
            } else {
                setRecButtonRecording(false);
                if (usbBlocked()) recButton.setEnabled(false);
                // aksi halde enabled durumu refreshUsbInfo'ya birakilir
            }
        }
        refreshUsbAutoUi();
        refreshSettingsState();
        updateRecBadge();
        if (usbRecording()) {
            startRecTicker();
        }
    }

    /** Menu kartlarinin alt metinleri ve karsilikli devre disi gorunumu. */
    private void updateMenuCards() {
        if (menuAutoSub == null) return;
        boolean internalOn = dvr.isAutoInternalEnabled();
        boolean usbAutoOn = dvr.isAutoUsbEnabled();
        if (internalOn) {
            menuAutoSub.setText(context.getString(R.string.camera360_menu_auto_sub_on,
                    Camera360AutoView.statusLabel(ui, dvr, dvr.getAutoStatus())));
        } else if (usbAutoOn) {
            menuAutoSub.setText(R.string.camera360_menu_blocked_usb_auto);
        } else {
            menuAutoSub.setText(R.string.camera360_menu_auto_sub_off);
        }
        menuAutoCard.setAlpha(usbAutoOn ? 0.55f : 1f);
        if (internalOn) {
            menuUsbSub.setText(R.string.camera360_menu_blocked_internal);
        } else if (usbAutoOn) {
            menuUsbSub.setText(context.getString(R.string.camera360_usb_auto_status,
                    Camera360AutoView.statusLabel(ui, dvr, dvr.getAutoStatus())));
        } else {
            menuUsbSub.setText(R.string.camera360_menu_record_sub);
        }
        menuUsbCard.setAlpha(internalOn ? 0.55f : 1f);
    }

    /**
     * Baslik rozetini kayit durumuna esitler. Yanip sonme yalnizca kayit
     * gorunumu ekrandayken calisir; arka planda animasyon maliyeti olmaz.
     */
    private void updateRecBadge() {
        boolean recording = usbRecording();
        if (recBadge != null) {
            recBadge.setVisibility(recording ? View.VISIBLE : View.GONE);
            if (recording) {
                recBadgeText.setText(context.getString(R.string.camera360_rec_badge,
                        formatElapsed(recorder.getElapsedMs())));
            }
        }
        boolean pulse = recording && tabActive && mode == Mode.RECORD;
        if (pulse && recPulse == null && recPulseDot != null) {
            recPulse = ObjectAnimator.ofFloat(recPulseDot, View.ALPHA, 1f, 0.2f);
            recPulse.setDuration(700);
            recPulse.setRepeatMode(ValueAnimator.REVERSE);
            recPulse.setRepeatCount(ValueAnimator.INFINITE);
            recPulse.start();
        } else if (!pulse && recPulse != null) {
            recPulse.cancel();
            recPulse = null;
            recPulseDot.setAlpha(1f);
        }
    }

    // ---------- Gorunum gecisleri ----------

    private void enterMenu() {
        leaveCurrentMode();
        mode = Mode.MENU;
        showOnly(menuView);
        syncRecordingUi();
    }

    private void enterPreview() {
        leaveCurrentMode();
        mode = Mode.PREVIEW;
        showOnly(previewView);
        updateRecBadge();
        if (tabActive) {
            startPreview();
        }
    }

    private void enterAuto() {
        leaveCurrentMode();
        mode = Mode.AUTO;
        showOnly(autoRoot);
        if (tabActive) {
            autoView.onShow();
            startAutoTicker();
        }
    }

    private void enterViewer() {
        leaveCurrentMode();
        mode = Mode.VIEWER;
        showOnly(viewerRoot);
        if (tabActive) {
            clipViewer.onShow();
        }
    }

    private final Runnable autoTicker = new Runnable() {
        @Override
        public void run() {
            if (!tabActive || mode != Mode.AUTO) return;
            autoView.tick();
            mainHandler.postDelayed(this, 1000);
        }
    };

    private void startAutoTicker() {
        mainHandler.removeCallbacks(autoTicker);
        mainHandler.postDelayed(autoTicker, 1000);
    }

    private void enterRecord() {
        leaveCurrentMode();
        mode = Mode.RECORD;
        showOnly(recordView);
        refreshUsbInfo();
        syncRecordingUi();
        refreshCamStatusDots();
        if (tabActive) {
            ensureStreams(this::refreshCamStatusDots);
        }
    }

    /**
     * Moddan cikarken onizleme/render durur. Kayit SURER (akislar acik kalir);
     * kayit yalnizca kullanici durdurunca veya USB cikince biter.
     */
    private void leaveCurrentMode() {
        stopPreviewLoops();
        releaseRecordViewStreams();
        mainHandler.removeCallbacks(autoTicker);
        if (autoView != null) autoView.onHide();
        if (clipViewer != null) clipViewer.onHide();
        // NOT: kayit ticker'i burada durdurulmaz; kayit suresince (hangi ekranda
        // olursa olsun) sure/boyut guncel kalir, onStopped/onError'da durur.
        // Kayit kendi hub referansini tutar; akislar kayit bitene kadar acik kalir.
    }

    /** Kayit gorunumunun akis referansini birakir; son tuketiciyse hub kisa gecikmeyle kapanir. */
    private void releaseRecordViewStreams() {
        if (!recordViewHoldsStreams) {
            return;
        }
        recordViewHoldsStreams = false;
        hub.release(HUB_TAG_RECORD_VIEW);
    }

    // ---------- Akis yonetimi ----------

    /**
     * Kayit gorunumu icin akislari acik tutar. onReady, en az bir kamera canliysa
     * calisir; hic kamera yoksa hata gosterilir (hub arka planda denemeye devam eder).
     */
    private void ensureStreams(Runnable onReady) {
        if (!cameraSupported) {
            recordStartPending = false;
            refreshSettingsState();
            if (mode == Mode.PREVIEW && statusView != null) {
                statusView.setText(R.string.camera360_unsupported);
            }
            if (mode == Mode.RECORD && recStatusView != null) {
                recStatusView.setText(R.string.camera360_unsupported);
            }
            return;
        }
        recordViewHoldsStreams = true;
        hub.acquire(HUB_TAG_RECORD_VIEW, liveMask -> {
            refreshCamStatusDots();
            if (liveMask == 0) {
                recordStartPending = false;
                refreshSettingsState();
                if (recButton != null && !recorder.isBusy()) {
                    syncRecordingUi();
                    refreshUsbInfo();
                }
                if (mode == Mode.RECORD && recStatusView != null) {
                    recStatusView.setText(failTextWithReason());
                }
                return;
            }
            if (onReady != null) onReady.run();
        });
    }


    // ---------- Onizleme ----------

    private void startPreview() {
        if (previewRunning) {
            return;
        }
        if (!cameraSupported) {
            statusView.setText(R.string.camera360_unsupported);
            return;
        }
        previewRunning = true;
        statusView.setText(R.string.camera360_working);
        engine.setFpsListener(fpsListener);
        engine.setPreviewEnabled(true);
        updateCamIndicators();
    }

    private void stopPreviewLoops() {
        if (!previewRunning) {
            return;
        }
        previewRunning = false;
        engine.setPreviewEnabled(false);
        engine.setFpsListener(null);
        for (int i = 0; i < CAM_COUNT; i++) {
            setFpsText(fpsViews[i], "");
        }
        setFpsText(bigFps, "");
        if (statusView != null) statusView.setText(R.string.camera360_hint);
        updateCamIndicators();
    }

    private void handleTap(float x, float y, int width, int height) {
        if (!previewRunning || width <= 0 || height <= 0) {
            return;
        }
        if (engine.getBigCam() >= 0) {
            showStrip();
        } else {
            int col = clamp((int) (x / (width / 2f)), 0, 1);
            int row = clamp((int) (y / (height / 2f)), 0, 1);
            showBig(CameraGlEngine.GRID_ORDER[row * 2 + col]);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private void showBig(int idx) {
        engine.setBigCam(idx);
        bigName.setText(camName(idx));
        setFpsText(bigFps, "");
        stripOverlay.setVisibility(View.GONE);
        bigOverlay.setVisibility(View.VISIBLE);
        updateCamIndicators();
    }

    private void showStrip() {
        engine.setBigCam(-1);
        bigOverlay.setVisibility(View.GONE);
        stripOverlay.setVisibility(View.VISIBLE);
        updateCamIndicators();
    }

    // ---------- USB / Kayit ----------

    private void refreshUsbInfo() {
        usbRoot = DvrController.findUsbRoot(context);
        refreshUsbAutoUi();
        if (usbRoot == null) {
            usbStateView.setText(R.string.camera360_usb_disconnected);
            usbStateView.setTextColor(COLOR_REC);
            setDot(COLOR_REC);
            usbPathView.setText(R.string.camera360_usb_none);
            usbFreeView.setText("-");
            usbTotalView.setText("-");
            usbFitView.setText("-");
            usbFitView.setTextColor(themeColor(R.color.textPrimary));
            storageBar.setProgress(0);
            recButton.setEnabled(usbRecording());
            refreshSettingsState();
            return;
        }
        try {
            StatFs fs = new StatFs(usbRoot.getAbsolutePath());
            long total = fs.getTotalBytes();
            long free = fs.getAvailableBytes();
            usbStateView.setText(R.string.camera360_usb_connected);
            usbStateView.setTextColor(COLOR_LIVE);
            setDot(COLOR_LIVE);
            usbPathView.setText(usbRoot.getAbsolutePath() + "/MapControl");
            usbFreeView.setText(formatGb(free));
            usbTotalView.setText(formatGb(total));
            storageBar.setProgress(total > 0 ? (int) ((total - free) * 1000 / total) : 0);
            if (usbRecording()) {
                recButton.setEnabled(true);
                refreshSettingsState();
                return;
            }
            if (recorder.isBusy() || recordStartPending) {
                recButton.setEnabled(false);
                refreshSettingsState();
                return;
            }
            long fit = CameraRecorder.estimateFitMinutes(free,
                    selectedWidth(), selectedHeight(), separateRecording);
            if (fit < MIN_FIT_MINUTES) {
                usbFitView.setText("< " + formatDuration(MIN_FIT_MINUTES));
                usbFitView.setTextColor(COLOR_REC);
                recButton.setEnabled(false);
                recStatusView.setText(context.getString(R.string.camera360_usb_low,
                        formatDuration(MIN_FIT_MINUTES)));
            } else {
                usbFitView.setText(formatDuration(fit));
                usbFitView.setTextColor(themeColor(R.color.textPrimary));
                recButton.setEnabled(cameraSupported && !usbBlocked());
            }
        } catch (Throwable t) {
            usbStateView.setText(R.string.camera360_usb_disconnected);
            usbStateView.setTextColor(COLOR_REC);
            setDot(COLOR_REC);
            recButton.setEnabled(usbRecording());
        }
        refreshSettingsState();
    }

    /** USB durum noktasi ve etrafindaki hapin tonunu ayni renge ceker. */
    private void setDot(int color) {
        if (usbDot != null && usbDot.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) usbDot.getBackground()).setColor(color);
        }
        if (usbStatePill != null) {
            usbStatePill.setBackground(roundRect(withAlpha(color, 0x24), 14));
        }
    }

    private static String formatGb(long bytes) {
        return String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0);
    }

    private String formatDuration(long minutes) {
        return ui.formatDuration(minutes);
    }

    private static boolean isUsbSource(DvrController.Source src) {
        return src == DvrController.Source.MANUAL_USB || src == DvrController.Source.AUTO_USB;
    }

    private final DvrController.Listener dvrListener = new DvrController.Listener() {
        @Override
        public void onStarted(DvrController.Source src, File file) {
            recordStartPending = false;
            if (isUsbSource(src) && recStatusView != null) {
                recStatusView.setText(context.getString(R.string.camera360_rec_active,
                        "00:00", file.getName()));
            }
            syncRecordingUi();
        }

        @Override
        public void onStopped(DvrController.Source src, File file, long elapsedMs) {
            recordStartPending = false;
            if (isUsbSource(src) && recStatusView != null) {
                recStatusView.setText(context.getString(R.string.camera360_rec_done,
                        file.getName() + " \u00b7 " + formatGb(file.length())));
            }
            stopRecTicker();
            syncRecordingUi();
            refreshUsbInfo();
            if (mode == Mode.RECORD && tabActive) {
                ensureStreams(Camera360TabBuilder.this::refreshCamStatusDots); // sonraki kayit icin hazir tut
            }
        }

        @Override
        public void onError(DvrController.Source src, String message) {
            recordStartPending = false;
            if (isUsbSource(src) && recStatusView != null) {
                recStatusView.setText(message);
            }
            stopRecTicker();
            syncRecordingUi();
            refreshUsbInfo();
        }

        @Override
        public void onStateChanged() {
            syncRecordingUi();
            if (mode == Mode.AUTO) autoView.refresh();
        }
    };

    /**
     * Saniyelik kayit sayaci. Zaten calisiyorsa yeniden kurulmaz: syncRecordingUi her durum
     * degisiminde cagrilir ve eskiden sayacin fazini sifirlayip bir saniyeyi atlatiyordu.
     */
    private void startRecTicker() {
        if (recTickerRunning) {
            return;
        }
        recTickerRunning = true;
        ticker.postDelayed(recTick, 1000);
    }

    private void stopRecTicker() {
        ticker.removeCallbacks(recTick);
        recTickerRunning = false;
    }

    private final Runnable recTick = new Runnable() {
        @Override
        public void run() {
            if (!usbRecording()) {
                recTickerRunning = false;
                return;
            }
            File f = recorder.getOutFile();
            int fileCount = recorder.getOutFiles().size();
            String displayName = f == null ? "" : f.getName()
                    + (fileCount > 1 ? " +" + (fileCount - 1) : "");
            recStatusView.setText(context.getString(R.string.camera360_rec_active,
                    formatElapsed(recorder.getElapsedMs()) + " \u00b7 "
                            + formatSize(recorder.getTotalBytes()),
                    displayName));
            updateRecBadge();
            ticker.postDelayed(this, 1000);
        }
    };

    // ---------- Kamera sinyal gostergeleri ----------

    /** state: 0 = olculuyor/bilinmiyor, 1 = canli, 2 = sinyal yok. */
    private void setCamDot(int cam, int state) {
        int color = state == 1 ? COLOR_LIVE : (state == 2 ? COLOR_REC : COLOR_IDLE);
        View dot = camDots[cam];
        if (dot != null && dot.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) dot.getBackground()).setColor(color);
        }
        if (camStateViews[cam] != null) {
            camStateViews[cam].setText(state == 1 ? R.string.camera360_sig_live
                    : (state == 2 ? R.string.camera360_sig_none : R.string.camera360_sig_wait));
        }
        if (camTileBgs[cam] != null) {
            camTileBgs[cam].setStroke(dp(1), state == 0 ? Color.TRANSPARENT : withAlpha(color, 0x8C));
        }
    }

    private void setAllCamDots(int state) {
        for (int i = 0; i < CAM_COUNT; i++) {
            setCamDot(i, state);
        }
    }

    /** Hub'daki kamera durumlarini kayit gorunumu noktalarina yansitir. */
    private void refreshCamStatusDots() {
        if (!cameraSupported) {
            setAllCamDots(2);
            return;
        }
        for (int i = 0; i < CAM_COUNT; i++) {
            int state = hub.getState(i);
            setCamDot(i, state == CameraHub.STATE_LIVE ? 1 : (state == CameraHub.STATE_NO_SIGNAL ? 2 : 0));
        }
    }

    /**
     * Kamera durum degisikliklerini tum gostergelere uygular: kayit noktalari,
     * onizleme karolarindaki "Sinyal yok / Baglaniyor" etiketleri ve durum satiri.
     */
    private void updateCamIndicators() {
        refreshCamStatusDots();
        int big = engine.getBigCam();
        boolean anyLive = false;
        boolean allDown = true;
        int liveCount = 0;
        for (int i = 0; i < CAM_COUNT; i++) {
            int state = hub.getState(i);
            anyLive |= state == CameraHub.STATE_LIVE;
            allDown &= state == CameraHub.STATE_NO_SIGNAL;
            if (state == CameraHub.STATE_LIVE) liveCount++;
            if (nameDots[i] != null) nameDots[i].setColor(stateColor(state));
            applyNoSignal(noSignalViews[i], previewRunning, state);
        }
        applyNoSignal(bigNoSignal, previewRunning && big >= 0,
                big >= 0 ? hub.getState(big) : CameraHub.STATE_LIVE);
        if (bigNameDot != null && big >= 0) {
            bigNameDot.setColor(stateColor(hub.getState(big)));
        }
        if (liveCountView != null) {
            liveCountView.setVisibility(previewRunning ? View.VISIBLE : View.GONE);
            if (previewRunning) {
                liveCountView.setText(context.getString(R.string.camera360_live_count,
                        liveCount, CAM_COUNT));
                liveCountDot.setColor(liveCount == CAM_COUNT ? COLOR_LIVE
                        : (liveCount > 0 ? COLOR_WAIT : COLOR_REC));
            }
        }
        if (previewRunning && statusView != null) {
            if (anyLive) {
                statusView.setText(R.string.camera360_live);
            } else if (allDown) {
                statusView.setText(failTextWithReason());
            } else {
                statusView.setText(R.string.camera360_working);
            }
        }
    }

    /** Genel basarisizlik metni + ilk sinyal alamayan kameranin gercek nedeni. */
    private String failTextWithReason() {
        String base = context.getString(R.string.camera360_fail);
        for (int i = 0; i < CAM_COUNT; i++) {
            if (hub.getState(i) != CameraHub.STATE_NO_SIGNAL) continue;
            String err = HabNative.hubError(i);
            if (err == null) continue;
            int bar = err.indexOf('|');
            if (bar < 0) continue;
            int code;
            try {
                code = Integer.parseInt(err.substring(0, bar).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            String detail = err.substring(bar + 1).trim();
            switch (code) {
                case 1: return base + "\n" + context.getString(R.string.camera360_err_id);
                case 2: return base + "\n" + context.getString(R.string.camera360_err_format, detail);
                case 3: return base + "\n" + context.getString(R.string.camera360_err_geometry, detail);
                case 4: return base + "\n" + context.getString(R.string.camera360_err_open, detail);
                default: break;
            }
        }
        return base;
    }

    private static void applyNoSignal(TextView view, boolean active, int state) {
        if (view == null) {
            return;
        }
        if (!active || state == CameraHub.STATE_LIVE) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setText(state == CameraHub.STATE_NO_SIGNAL
                ? R.string.camera360_no_signal : R.string.camera360_connecting);
        view.setVisibility(View.VISIBLE);
    }

    /**
     * Sinyal yenileme ikonu: (kayit yoksa) kamera baglantilarini kapatip
     * yeniden acarak sinyal durumlarini gercekten yeniden olcer.
     * Kayit surerken bolmez, sadece mevcut durumu tazeler.
     */
    private void recheckCameras() {
        if (mode != Mode.RECORD) {
            return;
        }
        if (recorder.isBusy() || recordStartPending) {
            refreshCamStatusDots();
            return;
        }
        if (!cameraSupported) {
            setAllCamDots(2);
            return;
        }
        setAllCamDots(0); // olculuyor: gri
        if (recordViewHoldsStreams) {
            hub.restartAll(); // noktalar durum dinleyicisiyle guncellenir
        } else {
            ensureStreams(this::refreshCamStatusDots);
        }
    }

    private void toggleRecording() {
        if (recorder.isBusy() && !recorder.isRecording()) {
            return; // kapanma suruyor
        }
        if (usbRecording()) {
            recButton.setEnabled(false);
            dvr.stopRecording(true);
            refreshSettingsState();
            return;
        }
        if (usbRoot == null || recordStartPending || usbBlocked() || recorder.isBusy()) {
            return;
        }
        // Baslatma anindan itibaren ayarlar kilitli; akis acilamazsa onReady
        // calismaz, ensureStreams hata yollari pending'i temizler.
        recordStartPending = true;
        recButton.setEnabled(false);
        refreshSettingsState();
        final int w = selectedWidth();
        final int h = selectedHeight();
        final boolean sep = separateRecording;
        final File root = usbRoot;
        ensureStreams(() -> {
            if (!recordStartPending) return;
            if (!dvr.startManualUsb(root, w, h, sep)) {
                recordStartPending = false;
                syncRecordingUi();
                refreshUsbInfo();
            }
        });
    }

    // ---------- Sekme yasam dongusu ----------

    public void onTabVisible() {
        tabActive = true;
        hub.addStateListener(camStateListener);
        dvr.addListener(dvrListener);
        if (usbRecording()) {
            startRecTicker();
        }
        syncRecordingUi();
        if (mode == Mode.PREVIEW) {
            startPreview();
        } else if (mode == Mode.RECORD) {
            ensureStreams(this::refreshCamStatusDots);
            refreshUsbInfo();
        } else if (mode == Mode.AUTO) {
            autoView.onShow();
            startAutoTicker();
        } else if (mode == Mode.VIEWER) {
            clipViewer.onShow();
        }
    }

    public void onTabHidden() {
        tabActive = false;
        leaveCurrentMode();
        hub.removeStateListener(camStateListener);
        dvr.removeListener(dvrListener);
        syncRecordingUi();
        // Mod secimi korunur; gorunume donunce kaldigi yerden devam eder.
        if (mode == Mode.PREVIEW) {
            previewView.setVisibility(View.VISIBLE);
        }
    }

    /** Video uzerindeki yari saydam yuvarlak etiket. */
    private TextView overlayPill(String text) {
        return ui.overlayPill(text);
    }

    private TextView fpsPill() {
        TextView tv = overlayPill("");
        tv.setTextColor(COLOR_FPS);
        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tv.setVisibility(View.GONE);
        return tv;
    }

    private static void setFpsText(TextView view, String text) {
        if (view == null) {
            return;
        }
        view.setText(text);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Karo ortasinda "Sinyal yok / Baglaniyor" etiketi (baslangicta gizli). */
    private TextView noSignalChip() {
        TextView tv = overlayPill(context.getString(R.string.camera360_connecting));
        tv.setTextSize(15);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(COLOR_NO_SIGNAL);
        tv.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable bg = roundRect(OVERLAY_BG, 18);
        bg.setStroke(dp(1), withAlpha(COLOR_NO_SIGNAL, 0x80));
        tv.setBackground(bg);
        tv.setVisibility(View.GONE);
        return tv;
    }
}
