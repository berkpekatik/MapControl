package com.mapcontrol.ui.builder;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.mapcontrol.R;
import com.mapcontrol.media.LauncherMediaController;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.ui.widget.LauncherLitePanelView;
import com.mapcontrol.ui.widget.VehicleGlbView;
import com.mapcontrol.util.LauncherDisplayModeStore;
import com.mapcontrol.util.LauncherQuickAppsStore;
import com.mapcontrol.util.ProjectionTargetApps;
import com.mapcontrol.vehicle.VehicleMetricsFormatter;
import com.mapcontrol.vehicle.VehicleMetricsRepository;
import com.mapcontrol.vehicle.VehicleMetricsSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Launcher üst konsolu — sol hızlı erişim, orta araç/medya, sağ sürüş+yakıt.
 */
public final class LauncherDashboardBuilder implements
        VehicleMetricsRepository.Listener,
        LauncherMediaController.Listener {

    private static final int QUICK_SHORTCUT_COLUMNS = 5;

    private static final class DashboardShortcut {
        final int tabIndex;
        final int iconRes;
        final String title;

        DashboardShortcut(int tabIndex, int iconRes, String title) {
            this.tabIndex = tabIndex;
            this.iconRes = iconRes;
            this.title = title;
        }
    }

    private final Context context;
    private final VehicleMetricsRepository repository;
    private final VehicleMetricsFormatter formatter;
    private final LauncherMediaController mediaController;
    private final LauncherTabBuilder.LauncherCallback launcherCallback;

    private TextView speedValueView;
    private TextView gearLabelView;
    private TextView powerStatusView;
    private TextView rpmValueView;
    private TextView tripValueView;

    private View musicPanel;
    private View drivePanel;
    private boolean musicTabSelected = true;

    private LinearLayout mediaActiveContainer;
    private LinearLayout mediaEmptyContainer;
    private TextView mediaEmptyTitleView;
    private TextView mediaEmptyBodyView;
    private ImageView albumArtView;
    private TextView trackTitleView;
    private TextView trackArtistView;
    private TextView mediaPositionView;
    private TextView mediaDurationView;
    private ProgressBar mediaProgressBar;
    private View playPauseButton;
    private ImageView playPauseIconView;
    private long lastMediaPositionSec = -1L;
    private long lastMediaDurationMs = -1L;
    private boolean lastMediaPlaying;
    private String lastMediaTitle;
    private String lastMediaArtist;
    private Bitmap lastMediaArtwork;

    private TextView fuelPercentView;
    private ProgressBar fuelProgressBar;
    private TextView rangeValueView;
    private TextView consumptionValueView;
    private TextView coolantValueView;
    private TextView odoValueView;
    private TextView lowFuelValueView;

    private VehicleGlbView vehicleGlbView;
    private FrameLayout glbHost;
    private LauncherLitePanelView litePanelView;
    private AppCompatImageButton trunkButton;
    private AppCompatImageButton doorsButton;
    private AppCompatImageButton sunroofButton;
    private AppCompatImageButton interiorButton;
    private TextView driveModeLabelView;
    private LinearLayout poseEditorCard;
    private TextView mediaCardTitleView;
    private final Map<String, EditText> poseInputs = new LinkedHashMap<>();
    private GridLayout quickAppsGrid;
    private final ImageView[] quickAppIcons = new ImageView[LauncherQuickAppsStore.MAX_SLOT_COUNT];
    private final View[] quickAppSlots = new View[LauncherQuickAppsStore.MAX_SLOT_COUNT];
    private GridLayout shortcutsGrid;
    private TextView shortcutsLabelView;

    private LinearLayout quickAccessCard;
    private LinearLayout vehicleCard;
    private LinearLayout musicDriveCard;
    private TextView quickAccessTitleView;
    private AppCompatImageButton quickAccessGearButton;
    private AppCompatImageButton quickAccessExitButton;
    private View quickAccessDivider;
    private LinearLayout musicDriveTabTrack;
    private TextView musicTabView;
    private TextView driveTabView;
    private final List<ImageView> mediaControlIcons = new ArrayList<>();

    private boolean listening;

    public LauncherDashboardBuilder(
            Context context,
            VehicleMetricsRepository repository,
            LauncherTabBuilder.LauncherCallback launcherCallback) {
        this.context = context;
        this.repository = repository;
        this.launcherCallback = launcherCallback;
        this.formatter = new VehicleMetricsFormatter(context);
        this.mediaController = new LauncherMediaController(context);
    }

    public View build() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        // Dış sarmalayıcı kart yok — sadece 3 iç cam kart.
        root.setBackgroundColor(UiStyles.color(context, R.color.transparent));

        LinearLayout cardsRow = new LinearLayout(context);
        cardsRow.setOrientation(LinearLayout.HORIZONTAL);
        cardsRow.setBaselineAligned(false);
        int cardGap = UiStyles.dimenPx(context, R.dimen.launcher_dashboard_card_gap);

        LinearLayout quickCard = buildQuickAccessCard();
        quickAccessCard = quickCard;
        LinearLayout.LayoutParams quickLp = weightedLp(0.22f);
        quickLp.setMarginEnd(cardGap / 2);
        cardsRow.addView(quickCard, quickLp);

        vehicleCard = buildVehicleCard();
        LinearLayout.LayoutParams vehicleLp = weightedLp(0.56f);
        vehicleLp.setMarginStart(cardGap / 2);
        vehicleLp.setMarginEnd(cardGap / 2);
        cardsRow.addView(vehicleCard, vehicleLp);

        musicDriveCard = buildMusicDriveCard();
        LinearLayout.LayoutParams musicDriveLp = weightedLp(0.22f);
        musicDriveLp.setMarginStart(cardGap / 2);
        musicDriveCard.setMinimumWidth(0);
        cardsRow.addView(musicDriveCard, musicDriveLp);

        LinearLayout.LayoutParams cardsRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f);
        root.addView(cardsRow, cardsRowLp);

        poseEditorCard = buildPoseEditorCard();
        poseEditorCard.setVisibility(View.GONE);
        LinearLayout.LayoutParams poseLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        poseLp.topMargin = UiStyles.dimenPx(context, R.dimen.spacing_small);
        root.addView(poseEditorCard, poseLp);

        applySnapshot(repository.currentSnapshot());
        applyMediaState(mediaController.currentState());
        return root;
    }

    public void start() {
        if (vehicleGlbView != null) {
            vehicleGlbView.onHostStart();
        }
        if (litePanelView != null) {
            litePanelView.start();
        }
        mediaController.addListener(this);
        mediaController.start();
        if (listening) {
            applySnapshot(repository.currentSnapshot());
            return;
        }
        listening = true;
        repository.addListener(this);
    }

    public void stop() {
        if (vehicleGlbView != null) {
            vehicleGlbView.onHostStop();
        }
        if (litePanelView != null) {
            litePanelView.stop();
        }
        mediaController.removeListener(this);
        mediaController.stop();
        if (!listening) {
            return;
        }
        listening = false;
        repository.removeListener(this);
    }

    /**
     * Light/dark değişiminde cam kart + metin/ikon renklerini yeniler.
     * {@link VehicleGlbView} hierarchy'de kalır — Engine yok edilmez.
     */
    public void reapplyTheme() {
        if (quickAccessCard != null) {
            UiStyles.setGlassCardBackground(quickAccessCard);
        }
        if (vehicleCard != null) {
            UiStyles.setGlassCardBackground(vehicleCard);
        }
        if (musicDriveCard != null) {
            UiStyles.setGlassCardBackground(musicDriveCard);
        }
        if (poseEditorCard != null) {
            UiStyles.setGlassCardBackground(poseEditorCard);
        }
        if (musicDriveTabTrack != null) {
            UiStyles.setBackgroundRes(musicDriveTabTrack, R.drawable.bg_segment_track);
        }
        if (musicTabView != null && driveTabView != null) {
            refreshMusicDriveTabs(musicTabView, driveTabView);
        }
        if (quickAccessTitleView != null) {
            quickAccessTitleView.setTextColor(UiStyles.color(context, R.color.textMuted));
        }
        if (quickAccessGearButton != null) {
            quickAccessGearButton.setColorFilter(
                    UiStyles.color(context, R.color.textSecondary));
        }
        if (quickAccessExitButton != null) {
            quickAccessExitButton.setColorFilter(
                    UiStyles.color(context, R.color.textDestructive));
        }
        if (quickAccessDivider != null) {
            quickAccessDivider.setBackgroundColor(
                    UiStyles.color(context, R.color.outlineSubtle));
        }
        if (shortcutsLabelView != null) {
            shortcutsLabelView.setTextColor(UiStyles.color(context, R.color.textSecondary));
        }
        // Slot arka planları (surfaceCard) Activity Resources'ta yapışır — taze yükle / yeniden kur
        rebuildQuickAppsArea();
        reapplyShortcutsTheme();
        if (mediaCardTitleView != null) {
            mediaCardTitleView.setTextColor(UiStyles.color(context, R.color.textMuted));
        }
        applyTextColor(rpmValueView, R.color.textPrimary);
        applyTextColor(tripValueView, R.color.textPrimary);
        applyTextColor(fuelPercentView, R.color.textPrimary);
        applyTextColor(rangeValueView, R.color.textPrimary);
        applyTextColor(consumptionValueView, R.color.textPrimary);
        applyTextColor(coolantValueView, R.color.textPrimary);
        applyTextColor(odoValueView, R.color.textPrimary);
        applyTextColor(gearLabelView, R.color.accentHighlight);
        applyTextColor(powerStatusView, R.color.textSecondary);
        applyTextColor(trackTitleView, R.color.textSecondary);
        applyTextColor(trackArtistView, R.color.textMuted);
        applyTextColor(mediaPositionView, R.color.textMuted);
        applyTextColor(mediaDurationView, R.color.textMuted);
        applyTextColor(mediaEmptyTitleView, R.color.textPrimary);
        applyTextColor(mediaEmptyBodyView, R.color.textSecondary);
        int accent = UiStyles.color(context, R.color.oemAccent);
        for (ImageView icon : mediaControlIcons) {
            if (icon != null) {
                icon.setColorFilter(accent);
            }
        }
        if (playPauseIconView != null) {
            playPauseIconView.setColorFilter(accent);
        }
        refreshBodyControlBackgrounds();
        updateBodyControlButtons(repository.currentSnapshot());
        if (vehicleGlbView != null) {
            vehicleGlbView.reapplySceneBackground();
        }
        if (litePanelView != null) {
            litePanelView.reapplyTheme();
        }
        // Hız / düşük yakıt renkleri snapshot’tan (simülasyon vurgusu dahil)
        applySnapshot(repository.currentSnapshot());
        applyMediaState(mediaController.currentState());
    }

    private static void applyTextColor(@Nullable TextView view, int colorRes) {
        if (view != null) {
            view.setTextColor(UiStyles.color(view.getContext(), colorRes));
        }
    }

    @Override
    public void onMetricsUpdated(VehicleMetricsSnapshot snapshot) {
        applySnapshot(snapshot);
    }

    @Override
    public void onMediaStateChanged(LauncherMediaController.MediaState state) {
        applyMediaState(state);
    }

    private void applySnapshot(VehicleMetricsSnapshot snapshot) {
        if (speedValueView == null) {
            return;
        }
        speedValueView.setText(formatter.formatDashboardSpeed(snapshot));
        gearLabelView.setText(formatter.formatDashboardGearLabel(snapshot));
        powerStatusView.setText(formatter.formatDashboardPowerStatus(snapshot));
        rpmValueView.setText(formatter.formatDashboardRpm(
                repository.getCombined(ReadOnlyID.ID_ENGINE_RPM)));
        tripValueView.setText(formatter.formatDashboardTrip(
                repository.getCombined(ReadOnlyID.ID_TRIP)));

        odoValueView.setText(formatter.formatDashboardOdo(snapshot));
        fuelPercentView.setText(formatter.formatDashboardFuel(snapshot));
        fuelProgressBar.setProgress(formatter.fuelPercentOrZero(snapshot));
        rangeValueView.setText(formatter.formatDashboardRange(snapshot));
        consumptionValueView.setText(formatter.formatDashboardConsumption(
                repository.getCombined(ReadOnlyID.ID_AVG_FUEL_CONS)));
        coolantValueView.setText(formatter.formatDashboardTemperature(
                repository.getCombined(ReadOnlyID.ID_WATER_TEMPERATURE)));
        lowFuelValueView.setText(formatter.formatDashboardLowFuelShort(
                repository.getCombined(ReadOnlyID.ID_LOW_FUEL_WARNING)));

        int speed = snapshot.preferredSpeed();
        if (vehicleGlbView != null) {
            vehicleGlbView.setWheelSpeedKmh(speed);
            if (speed <= 0 && (vehicleGlbView.isWheelSimulationEnabled()
                    || vehicleGlbView.getEffectiveWheelSpeedKmh() > 0f)) {
                speed = Math.round(vehicleGlbView.getEffectiveWheelSpeedKmh());
                speedValueView.setText(speed > 0
                        ? String.format(Locale.getDefault(), "%d", speed)
                        : "—");
            }
        }

        int speedColor = speed > 0
                ? UiStyles.color(context, R.color.textPrimary)
                : UiStyles.color(context, R.color.textMuted);
        if (vehicleGlbView != null && vehicleGlbView.isWheelSimulationEnabled()) {
            speedColor = UiStyles.color(context, R.color.accentHighlight);
        }
        speedValueView.setTextColor(speedColor);

        int lowFuel = repository.getCombined(ReadOnlyID.ID_LOW_FUEL_WARNING);
        int lowFuelColor = lowFuel == 1
                ? UiStyles.color(context, R.color.textDestructive)
                : UiStyles.color(context, R.color.textPrimary);
        lowFuelValueView.setTextColor(lowFuelColor);

        syncVehicleBodyToGlb(snapshot);
        if (litePanelView != null) {
            litePanelView.bindVehicleMetrics(snapshot);
        }
    }

    /** OEM kapı/bagaj sinyali → 3D aç/kapa animasyonu. */
    private void syncVehicleBodyToGlb(VehicleMetricsSnapshot snapshot) {
        if (vehicleGlbView == null) {
            return;
        }
        if (snapshot.hasDoorLf()) {
            vehicleGlbView.setDoorOpen("LF", snapshot.isDoorLfOpen());
        }
        if (snapshot.hasDoorRf()) {
            vehicleGlbView.setDoorOpen("RF", snapshot.isDoorRfOpen());
        }
        if (snapshot.hasDoorLr()) {
            vehicleGlbView.setDoorOpen("LR", snapshot.isDoorLrOpen());
        }
        if (snapshot.hasDoorRr()) {
            vehicleGlbView.setDoorOpen("RR", snapshot.isDoorRrOpen());
        }
        if (snapshot.hasTrunk()) {
            vehicleGlbView.setTrunkOpen(snapshot.isTrunkOpen());
        }
        Boolean sunroofOpen = snapshot.sunroofOpenOrNull();
        if (sunroofOpen != null) {
            vehicleGlbView.setSunroofOpen(sunroofOpen);
        }
        if (snapshot.hasDriveMode()) {
            // Tema tint yok — sadece değer saklanır (API uyumu)
            vehicleGlbView.setDriveMode(snapshot.driveMode);
        }
        updateBodyControlButtons(snapshot);
    }

    private void updateBodyControlButtons(VehicleMetricsSnapshot snapshot) {
        int accent = UiStyles.color(context, R.color.oemAccent);
        int highlight = UiStyles.color(context, R.color.accentHighlight);
        updateDriveModeLabel(snapshot);

        if (doorsButton != null) {
            boolean open = snapshot.hasDoorLf() || snapshot.hasDoorRf()
                    || snapshot.hasDoorLr() || snapshot.hasDoorRr()
                    ? snapshot.isAnyDoorOpen()
                    : vehicleGlbView != null && vehicleGlbView.isDoorsOpen();
            doorsButton.setColorFilter(open ? highlight : accent);
            doorsButton.setContentDescription(context.getString(open
                    ? R.string.launcher_dashboard_doors_close
                    : R.string.launcher_dashboard_doors_open));
        }
        if (trunkButton != null) {
            boolean open = snapshot.hasTrunk()
                    ? snapshot.isTrunkOpen()
                    : vehicleGlbView != null && vehicleGlbView.isTrunkOpen();
            trunkButton.setColorFilter(open ? highlight : accent);
            trunkButton.setContentDescription(context.getString(open
                    ? R.string.launcher_dashboard_trunk_close
                    : R.string.launcher_dashboard_trunk_open));
        }
        if (sunroofButton != null) {
            boolean open = snapshot.hasSunroof()
                    ? snapshot.isSunroofOpen()
                    : vehicleGlbView != null && vehicleGlbView.isSunroofOpen();
            sunroofButton.setColorFilter(open ? highlight : accent);
            sunroofButton.setContentDescription(context.getString(open
                    ? R.string.launcher_dashboard_sunroof_close
                    : R.string.launcher_dashboard_sunroof_open));
        }
        if (interiorButton != null) {
            interiorButton.setColorFilter(accent);
        }
    }

    private void updateDriveModeLabel(VehicleMetricsSnapshot snapshot) {
        if (driveModeLabelView == null) {
            return;
        }
        // Tema renkleri (yeşil/mavi/kırmızı) kaldırıldı — etiket de göstermiyoruz
        driveModeLabelView.setVisibility(View.GONE);
    }

    private void refreshBodyControlBackgrounds() {
        AppCompatImageButton[] buttons = {
                doorsButton, trunkButton, sunroofButton, interiorButton
        };
        for (AppCompatImageButton button : buttons) {
            if (button != null) {
                UiStyles.setBackgroundRes(button, R.drawable.bg_vehicle_quick_control);
            }
        }
    }

    private int bodyControlIdleAccent() {
        return UiStyles.color(context, R.color.oemAccent);
    }

    private void applyMediaState(LauncherMediaController.MediaState state) {
        if (mediaActiveContainer == null) {
            return;
        }
        boolean hasSession = state.sessionAvailable;
        mediaActiveContainer.setVisibility(hasSession ? View.VISIBLE : View.GONE);
        mediaEmptyContainer.setVisibility(hasSession ? View.GONE : View.VISIBLE);

        if (!hasSession) {
            playPauseButton.setEnabled(false);
            lastMediaPositionSec = -1L;
            lastMediaDurationMs = -1L;
            lastMediaPlaying = false;
            lastMediaTitle = null;
            lastMediaArtist = null;
            lastMediaArtwork = null;
            applyMediaArtwork(null);
            if (mediaEmptyTitleView != null && mediaEmptyBodyView != null) {
                if (!state.notificationAccessGranted) {
                    mediaEmptyTitleView.setText(R.string.launcher_dashboard_media_need_access_title);
                    mediaEmptyBodyView.setText(R.string.launcher_dashboard_media_need_access_body);
                    mediaEmptyContainer.setClickable(true);
                    mediaEmptyContainer.setFocusable(true);
                    mediaEmptyContainer.setOnClickListener(v ->
                            mediaController.openNotificationAccessSettings());
                } else {
                    mediaEmptyTitleView.setText(R.string.launcher_dashboard_media_empty_title);
                    mediaEmptyBodyView.setText(R.string.launcher_dashboard_media_empty_body);
                    mediaEmptyContainer.setOnClickListener(null);
                    mediaEmptyContainer.setClickable(false);
                    mediaEmptyContainer.setFocusable(false);
                }
            }
            return;
        }

        playPauseButton.setEnabled(true);
        if (state.playing != lastMediaPlaying) {
            lastMediaPlaying = state.playing;
            playPauseIconView.setImageResource(state.playing
                    ? R.drawable.ic_mdi_pause
                    : R.drawable.ic_mdi_play);
            playPauseButton.setContentDescription(context.getString(state.playing
                    ? R.string.launcher_media_pause
                    : R.string.launcher_media_play));
        }

        String title = state.title != null && !state.title.isEmpty()
                ? state.title
                : context.getString(R.string.launcher_dashboard_media_unknown_title);
        if (!title.equals(lastMediaTitle)) {
            lastMediaTitle = title;
            trackTitleView.setText(title);
        }
        String artist = state.artist != null && !state.artist.isEmpty()
                ? state.artist
                : context.getString(R.string.launcher_dashboard_media_unknown_artist);
        if (!artist.equals(lastMediaArtist)) {
            lastMediaArtist = artist;
            trackArtistView.setText(artist);
        }

        // Süre yazısı saniyede bir; bar her tick’te (extrapolated) yumuşak ilerler
        long positionSec = state.positionMs / 1000L;
        if (positionSec != lastMediaPositionSec) {
            lastMediaPositionSec = positionSec;
            mediaPositionView.setText(formatter.formatMediaDuration(state.positionMs));
        }
        if (state.durationMs != lastMediaDurationMs) {
            lastMediaDurationMs = state.durationMs;
            mediaDurationView.setText(formatter.formatMediaDuration(state.durationMs));
        }
        int max = mediaProgressBar.getMax();
        int progress = 0;
        if (state.durationMs > 0L && max > 0) {
            progress = (int) Math.min((long) max,
                    (state.positionMs * (long) max) / state.durationMs);
        }
        if (mediaProgressBar.getProgress() != progress) {
            mediaProgressBar.setProgress(progress);
        }

        if (state.artwork != lastMediaArtwork) {
            lastMediaArtwork = state.artwork;
            applyMediaArtwork(state.artwork);
        }
    }

    private void applyMediaArtwork(@Nullable Bitmap artwork) {
        if (albumArtView == null) {
            return;
        }
        if (artwork != null) {
            albumArtView.setImageBitmap(artwork);
            albumArtView.setVisibility(View.VISIBLE);
        } else {
            albumArtView.setImageDrawable(null);
            albumArtView.setVisibility(View.GONE);
        }
    }

    private void onSimulatedWheelSpeedChanged(float kmh) {
        if (speedValueView == null) {
            return;
        }
        if (repository.currentSnapshot().preferredSpeed() > 0) {
            return;
        }
        int shown = Math.round(kmh);
        speedValueView.setText(shown > 0
                ? String.format(Locale.getDefault(), "%d", shown)
                : "—");
        boolean sim = vehicleGlbView != null && vehicleGlbView.isWheelSimulationEnabled();
        speedValueView.setTextColor(UiStyles.color(context,
                sim || shown > 0 ? R.color.accentHighlight : R.color.textMuted));
    }

    private LinearLayout buildQuickAccessCard() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        UiStyles.setGlassCardBackground(card);
        int pad = UiStyles.dimenPx(context, R.dimen.oem_card_inner_padding);
        card.setPadding(pad, pad, pad, pad);

        // Başlık + dişli (slot sayısı)
        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(R.string.launcher_dashboard_card_quick);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.text_size_small));
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(UiStyles.color(context, R.color.textMuted));
        titleView.setAllCaps(true);
        titleView.setLetterSpacing(0.08f);
        quickAccessTitleView = titleView;
        titleRow.addView(titleView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        AppCompatImageButton gearButton = new AppCompatImageButton(context);
        int headerBtnSize = UiStyles.dimenPx(context, R.dimen.launcher_dashboard_control_size);
        gearButton.setImageResource(R.drawable.ic_mdi_cog);
        gearButton.setColorFilter(UiStyles.color(context, R.color.textSecondary));
        gearButton.setBackground(null);
        gearButton.setPadding(0, 0, 0, 0);
        gearButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        gearButton.setContentDescription(
                context.getString(R.string.launcher_dashboard_quick_settings));
        applyBorderlessRipple(gearButton);
        gearButton.setOnClickListener(v -> showQuickAccessSettingsMenu());
        quickAccessGearButton = gearButton;
        LinearLayout.LayoutParams gearLp = new LinearLayout.LayoutParams(headerBtnSize, headerBtnSize);
        titleRow.addView(gearButton, gearLp);

        AppCompatImageButton exitButton = new AppCompatImageButton(context);
        exitButton.setImageResource(R.drawable.ic_mdi_close);
        exitButton.setColorFilter(UiStyles.color(context, R.color.textDestructive));
        exitButton.setBackground(null);
        exitButton.setPadding(0, 0, 0, 0);
        exitButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        exitButton.setContentDescription(
                context.getString(R.string.launcher_dashboard_quick_exit));
        applyBorderlessRipple(exitButton);
        exitButton.setOnClickListener(v -> {
            if (launcherCallback != null) {
                launcherCallback.onExitLauncherRequested();
            }
        });
        quickAccessExitButton = exitButton;
        LinearLayout.LayoutParams exitLp = new LinearLayout.LayoutParams(headerBtnSize, headerBtnSize);
        exitLp.setMarginStart(UiStyles.dimenPx(context, R.dimen.spacing_tiny));
        titleRow.addView(exitButton, exitLp);
        card.addView(titleRow, matchWidthWrap());

        LinearLayout body = cardContent(card);
        body.setGravity(Gravity.TOP);

        quickAppsGrid = new GridLayout(context);
        quickAppsGrid.setColumnCount(LauncherQuickAppsStore.GRID_COLUMNS);
        quickAppsGrid.setUseDefaultMargins(false);

        android.widget.ScrollView appsScroll = new android.widget.ScrollView(context);
        appsScroll.setFillViewport(false);
        appsScroll.setVerticalScrollBarEnabled(true);
        appsScroll.addView(quickAppsGrid, new android.widget.FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams appsScrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.2f);
        appsScrollLp.topMargin = UiStyles.dimenPx(context, R.dimen.spacing_tiny);
        body.addView(appsScroll, appsScrollLp);
        rebuildQuickAppsArea();

        View divider = new View(context);
        divider.setBackgroundColor(UiStyles.color(context, R.color.outlineSubtle));
        quickAccessDivider = divider;
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, UiStyles.dimenPx(context, R.dimen.spacing_tiny) / 2));
        dividerLp.topMargin = UiStyles.dimenPx(context, R.dimen.spacing_small);
        dividerLp.bottomMargin = UiStyles.dimenPx(context, R.dimen.spacing_small);
        body.addView(divider, dividerLp);

        // --- MapControl 5 sütun ---
        shortcutsLabelView = createSectionMiniLabel(R.string.launcher_dashboard_quick_shortcuts_label);
        body.addView(shortcutsLabelView, matchWidthWrap());

        shortcutsGrid = buildDashboardShortcutsGrid();
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        gridLp.topMargin = UiStyles.dimenPx(context, R.dimen.spacing_tiny);
        body.addView(shortcutsGrid, gridLp);

        return card;
    }

    /** MapControl kısayol kutuları + etiket renklerini güncel uiMode ile yeniler. */
    private void reapplyShortcutsTheme() {
        if (shortcutsGrid == null) {
            return;
        }
        int iconTint = UiStyles.color(context, R.color.textSecondary);
        int labelColor = UiStyles.color(context, R.color.textPrimary);
        for (int i = 0; i < shortcutsGrid.getChildCount(); i++) {
            View child = shortcutsGrid.getChildAt(i);
            if (!(child instanceof LinearLayout)) {
                continue;
            }
            LinearLayout tile = (LinearLayout) child;
            if (tile.getChildCount() < 2) {
                continue;
            }
            View slotOrIcon = tile.getChildAt(0);
            if (slotOrIcon instanceof FrameLayout) {
                FrameLayout slot = (FrameLayout) slotOrIcon;
                UiStyles.setBackgroundRes(slot, R.drawable.bg_launcher_icon_slot);
                if (slot.getChildCount() > 0 && slot.getChildAt(0) instanceof ImageView) {
                    ((ImageView) slot.getChildAt(0)).setColorFilter(iconTint);
                }
            }
            View label = tile.getChildAt(1);
            if (label instanceof TextView) {
                ((TextView) label).setTextColor(labelColor);
            }
        }
    }

    private void rebuildQuickAppsArea() {
        if (quickAppsGrid == null) {
            return;
        }
        if (LauncherQuickAppsStore.isShowAllApps(context)) {
            rebuildAllAppsGrid();
        } else {
            rebuildQuickAppSlots();
        }
    }

    private void rebuildQuickAppSlots() {
        if (quickAppsGrid == null) {
            return;
        }
        quickAppsGrid.removeAllViews();
        for (int i = 0; i < LauncherQuickAppsStore.MAX_SLOT_COUNT; i++) {
            quickAppSlots[i] = null;
            quickAppIcons[i] = null;
        }
        int visible = LauncherQuickAppsStore.getVisibleSlotCount(context);
        int gap = Math.max(1, UiStyles.dimenPx(context, R.dimen.spacing_tiny) / 2);
        for (int i = 0; i < visible; i++) {
            View slot = createQuickAppSlot(i);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(gap, gap, gap, gap);
            quickAppsGrid.addView(slot, lp);
        }
        bindQuickAppSlots();
    }

    /** Hızlı erişim alanında tüm yüklü + sistem uygulamalarını 5 sütun grid olarak gösterir. */
    private void rebuildAllAppsGrid() {
        if (quickAppsGrid == null) {
            return;
        }
        quickAppsGrid.removeAllViews();
        for (int i = 0; i < LauncherQuickAppsStore.MAX_SLOT_COUNT; i++) {
            quickAppSlots[i] = null;
            quickAppIcons[i] = null;
        }

        List<ProjectionTargetApps.Row> userApps = ProjectionTargetApps.loadSortedRows(context);
        List<ProjectionTargetApps.Row> systemApps = ProjectionTargetApps.loadSortedSystemRows(context);
        List<ProjectionTargetApps.Row> apps = new ArrayList<>(userApps.size() + systemApps.size());
        apps.addAll(userApps);
        apps.addAll(systemApps);

        if (apps.isEmpty()) {
            Toast.makeText(context, R.string.launcher_dashboard_quick_empty_list, Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        PackageManager pm = context.getPackageManager();
        int gap = Math.max(1, UiStyles.dimenPx(context, R.dimen.spacing_tiny) / 2);
        for (ProjectionTargetApps.Row row : apps) {
            View tile = createInstalledAppTile(pm, row);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(gap, gap, gap, gap);
            quickAppsGrid.addView(tile, lp);
        }
    }

    private View createInstalledAppTile(PackageManager pm, ProjectionTargetApps.Row row) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setClickable(true);
        tile.setFocusable(true);
        applyBorderlessRipple(tile);
        tile.setContentDescription(row.label);

        int slotSize = UiStyles.dimenPx(context, R.dimen.launcher_dashboard_quick_slot_size);
        int iconSize = UiStyles.dimenPx(context, R.dimen.launcher_dashboard_quick_icon_size);
        FrameLayout slot = new FrameLayout(context);
        UiStyles.setBackgroundRes(slot, R.drawable.bg_launcher_icon_slot);
        LinearLayout.LayoutParams slotLp = new LinearLayout.LayoutParams(slotSize, slotSize);
        slotLp.gravity = Gravity.CENTER_HORIZONTAL;
        tile.addView(slot, slotLp);

        ImageView icon = new AppCompatImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            icon.setImageDrawable(pm.getApplicationIcon(row.packageName));
        } catch (Exception e) {
            icon.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(iconSize, iconSize);
        iconLp.gravity = Gravity.CENTER;
        slot.addView(icon, iconLp);

        final String pkg = row.packageName;
        tile.setOnClickListener(v -> {
            if (launcherCallback != null) {
                launcherCallback.onAppLaunchRequested(pkg);
            }
        });
        return tile;
    }

    private void showQuickAccessSettingsMenu() {
        CharSequence[] items = new CharSequence[]{
                context.getString(R.string.launcher_dashboard_quick_menu_slot_count),
                context.getString(R.string.launcher_dashboard_quick_menu_list_all),
                context.getString(R.string.launcher_dashboard_quick_menu_launcher_mode),
        };
        new AlertDialog.Builder(context)
                .setTitle(R.string.launcher_dashboard_quick_settings)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showQuickSlotCountPicker();
                    } else if (which == 1) {
                        LauncherQuickAppsStore.setShowAllApps(context, true);
                        rebuildQuickAppsArea();
                    } else if (which == 2) {
                        showLauncherModePicker();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showLauncherModePicker() {
        final String mode3d = LauncherDisplayModeStore.MODE_3D;
        final String modeLite = LauncherDisplayModeStore.MODE_LITE;
        CharSequence[] labels = new CharSequence[]{
                context.getString(R.string.launcher_dashboard_display_mode_3d),
                context.getString(R.string.launcher_dashboard_display_mode_lite),
        };
        String current = LauncherDisplayModeStore.getMode(context);
        int checked = modeLite.equals(current) ? 1 : 0;
        new AlertDialog.Builder(context)
                .setTitle(R.string.launcher_dashboard_launcher_mode_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    String selected = which == 1 ? modeLite : mode3d;
                    if (!selected.equals(LauncherDisplayModeStore.getMode(context))) {
                        LauncherDisplayModeStore.setMode(context, selected);
                        applyLauncherDisplayMode();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void applyLauncherDisplayMode() {
        if (glbHost == null) {
            return;
        }
        if (LauncherDisplayModeStore.isLite(context)) {
            destroyVehicleGlbView();
            ensureLitePanelVisible();
            updateVehicleControlsVisibility(false);
            if (poseEditorCard != null) {
                poseEditorCard.setVisibility(View.GONE);
            }
            return;
        }
        removeLitePanel();
        createVehicleGlbView();
        updateVehicleControlsVisibility(true);
    }

    private void createVehicleGlbView() {
        if (vehicleGlbView != null || glbHost == null) {
            return;
        }
        vehicleGlbView = new VehicleGlbView(context);
        vehicleGlbView.setOnEffectiveWheelSpeedListener(this::onSimulatedWheelSpeedChanged);
        glbHost.addView(vehicleGlbView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        if (listening) {
            vehicleGlbView.onHostStart();
        }
        syncVehicleBodyToGlb(repository.currentSnapshot());
    }

    private void destroyVehicleGlbView() {
        if (vehicleGlbView == null) {
            return;
        }
        vehicleGlbView.onHostStop();
        glbHost.removeView(vehicleGlbView);
        vehicleGlbView = null;
    }

    private void ensureLitePanelVisible() {
        if (litePanelView != null) {
            litePanelView.setVisibility(View.VISIBLE);
            litePanelView.bindVehicleMetrics(repository.currentSnapshot());
            if (listening) {
                litePanelView.start();
            }
            return;
        }
        litePanelView = new LauncherLitePanelView(context);
        glbHost.addView(litePanelView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
        litePanelView.bindVehicleMetrics(repository.currentSnapshot());
        if (listening) {
            litePanelView.start();
        }
    }

    private void removeLitePanel() {
        if (litePanelView == null) {
            return;
        }
        litePanelView.destroy();
        glbHost.removeView(litePanelView);
        litePanelView = null;
    }

    private void updateVehicleControlsVisibility(boolean model3dVisible) {
        int visibility = model3dVisible ? View.VISIBLE : View.GONE;
        if (doorsButton != null) {
            doorsButton.setVisibility(visibility);
        }
        if (sunroofButton != null) {
            sunroofButton.setVisibility(visibility);
        }
        if (trunkButton != null) {
            trunkButton.setVisibility(visibility);
        }
        if (interiorButton != null) {
            interiorButton.setVisibility(visibility);
        }
    }

    private void showQuickSlotCountPicker() {
        int[] options = LauncherQuickAppsStore.SLOT_COUNT_OPTIONS;
        CharSequence[] labels = new CharSequence[options.length];
        int checked = 0;
        int current = LauncherQuickAppsStore.getVisibleSlotCount(context);
        for (int i = 0; i < options.length; i++) {
            labels[i] = context.getString(R.string.launcher_dashboard_quick_count_option, options[i]);
            if (options[i] == current) {
                checked = i;
            }
        }
        new AlertDialog.Builder(context)
                .setTitle(R.string.launcher_dashboard_quick_count_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    if (which < 0 || which >= options.length) {
                        return;
                    }
                    // setVisibleSlotCount aynı zamanda show-all modunu kapatır
                    LauncherQuickAppsStore.setVisibleSlotCount(context, options[which]);
                    rebuildQuickAppsArea();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private TextView createSectionMiniLabel(int textRes) {
        TextView label = new TextView(context);
        label.setText(textRes);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.text_size_small));
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(UiStyles.color(context, R.color.textSecondary));
        label.setAllCaps(true);
        label.setLetterSpacing(0.06f);
        return label;
    }

    private View createQuickAppSlot(int index) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setClickable(true);
        tile.setFocusable(true);
        applyBorderlessRipple(tile);

        int slotSize = UiStyles.dimenPx(context, R.dimen.launcher_dashboard_quick_slot_size);
        int iconSize = UiStyles.dimenPx(context, R.dimen.launcher_dashboard_quick_icon_size);
        FrameLayout slot = new FrameLayout(context);
        UiStyles.setBackgroundRes(slot, R.drawable.bg_launcher_icon_slot);
        LinearLayout.LayoutParams slotLp = new LinearLayout.LayoutParams(slotSize, slotSize);
        slotLp.gravity = Gravity.CENTER_HORIZONTAL;
        tile.addView(slot, slotLp);

        ImageView icon = new AppCompatImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(iconSize, iconSize);
        iconLp.gravity = Gravity.CENTER;
        slot.addView(icon, iconLp);

        quickAppSlots[index] = tile;
        quickAppIcons[index] = icon;

        tile.setOnClickListener(v -> onQuickAppSlotClick(index));
        tile.setOnLongClickListener(v -> {
            onQuickAppSlotLongClick(index);
            return true;
        });
        return tile;
    }

    private void bindQuickAppSlots() {
        String[] slots = LauncherQuickAppsStore.getSlots(context);
        int visible = LauncherQuickAppsStore.getVisibleSlotCount(context);
        PackageManager pm = context.getPackageManager();
        for (int i = 0; i < visible; i++) {
            ImageView icon = quickAppIcons[i];
            if (icon == null) {
                continue;
            }
            String pkg = slots[i];
            if (pkg == null || pkg.isEmpty()) {
                icon.setImageResource(R.drawable.ic_mdi_plus);
                icon.setColorFilter(UiStyles.color(context, R.color.textMuted));
                if (quickAppSlots[i] != null) {
                    quickAppSlots[i].setContentDescription(
                            context.getString(R.string.launcher_dashboard_quick_add));
                }
            } else {
                icon.clearColorFilter();
                try {
                    icon.setImageDrawable(pm.getApplicationIcon(pkg));
                } catch (Exception e) {
                    icon.setImageResource(android.R.drawable.sym_def_app_icon);
                }
                if (quickAppSlots[i] != null) {
                    CharSequence label = pkg;
                    try {
                        label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
                    } catch (Exception ignored) {
                        // keep pkg
                    }
                    quickAppSlots[i].setContentDescription(label);
                }
            }
        }
    }

    private void onQuickAppSlotClick(int index) {
        int visible = LauncherQuickAppsStore.getVisibleSlotCount(context);
        if (index < 0 || index >= visible) {
            return;
        }
        String[] slots = LauncherQuickAppsStore.getSlots(context);
        String pkg = slots[index];
        if (pkg != null && !pkg.isEmpty()) {
            if (launcherCallback != null) {
                launcherCallback.onAppLaunchRequested(pkg);
            }
            return;
        }
        showQuickAppPicker(index);
    }

    private void onQuickAppSlotLongClick(int index) {
        int visible = LauncherQuickAppsStore.getVisibleSlotCount(context);
        if (index < 0 || index >= visible) {
            return;
        }
        String[] slots = LauncherQuickAppsStore.getSlots(context);
        if (slots[index] == null || slots[index].isEmpty()) {
            return;
        }
        LauncherQuickAppsStore.clearSlot(context, index);
        bindQuickAppSlots();
        Toast.makeText(context, R.string.launcher_dashboard_quick_cleared, Toast.LENGTH_SHORT).show();
    }

    private void showQuickAppPicker(int slotIndex) {
        List<ProjectionTargetApps.Row> userApps = ProjectionTargetApps.loadSortedRows(context);
        List<ProjectionTargetApps.Row> systemApps = ProjectionTargetApps.loadSortedSystemRows(context);
        if (userApps.isEmpty() && systemApps.isEmpty()) {
            Toast.makeText(context, R.string.launcher_dashboard_quick_empty_list, Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        // Tek listede: kullanıcı uygulamaları, sonra sistem (etiketli).
        final List<ProjectionTargetApps.Row> apps = new ArrayList<>(userApps.size() + systemApps.size());
        List<CharSequence> labels = new ArrayList<>(userApps.size() + systemApps.size());
        for (ProjectionTargetApps.Row row : userApps) {
            apps.add(row);
            labels.add(row.label);
        }
        String systemSuffix = context.getString(R.string.launcher_dashboard_quick_system_suffix);
        for (ProjectionTargetApps.Row row : systemApps) {
            apps.add(row);
            labels.add(row.label + systemSuffix);
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.launcher_dashboard_quick_pick_title)
                .setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> {
                    if (which < 0 || which >= apps.size()) {
                        return;
                    }
                    LauncherQuickAppsStore.setSlot(context, slotIndex, apps.get(which).packageName);
                    bindQuickAppSlots();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private GridLayout buildDashboardShortcutsGrid() {
        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(QUICK_SHORTCUT_COLUMNS);
        grid.setUseDefaultMargins(false);

        DashboardShortcut[] shortcuts = new DashboardShortcut[]{
                new DashboardShortcut(0, R.drawable.ic_mdi_wifi, "Wi-Fi"),
                new DashboardShortcut(1, R.drawable.ic_mdi_web, "Web"),
                new DashboardShortcut(2, R.drawable.ic_mdi_account, "Profil"),
                new DashboardShortcut(3, R.drawable.ic_mdi_map, "Yansıtma"),
                new DashboardShortcut(5, R.drawable.ic_mdi_cellphone, "Uygulamalar"),
                new DashboardShortcut(6, R.drawable.ic_mdi_car, "Hafıza"),
                new DashboardShortcut(9, R.drawable.ic_mdi_speedometer, "Araç Bilgisi"),
                new DashboardShortcut(8, R.drawable.ic_mdi_volume_high, "Hoş Geldin"),
                new DashboardShortcut(7, R.drawable.ic_mdi_cog, "Ayarlar"),
        };
        for (DashboardShortcut shortcut : shortcuts) {
            addCompactShortcutTile(grid, shortcut);
        }
        return grid;
    }

    private void addCompactShortcutTile(GridLayout grid, DashboardShortcut shortcut) {
        LinearLayout tile = createCompactTileBase();
        // Screenshot: açık yuvarlatılmış kutu + koyu monokrom ikon
        ImageView icon = addCompactIcon(tile, R.drawable.bg_launcher_icon_slot,
                shortcut.iconRes, true);
        icon.setColorFilter(UiStyles.color(context, R.color.textSecondary));
        tile.addView(createCompactLabel(shortcut.title, R.color.textPrimary));
        tile.setOnClickListener(v -> {
            if (launcherCallback != null) {
                launcherCallback.onShortcutSelected(shortcut.tabIndex, shortcut.title);
            }
        });
        addCompactTileToGrid(grid, tile);
    }

    private LinearLayout createCompactTileBase() {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setClickable(true);
        tile.setFocusable(true);
        applyBorderlessRipple(tile);
        return tile;
    }

    private ImageView addCompactIcon(
            LinearLayout tile,
            @DrawableRes int slotBg,
            @DrawableRes int iconRes,
            boolean smallerIcon) {
        int slotSize = UiStyles.dimenPx(context, R.dimen.launcher_dashboard_quick_slot_size);
        int iconSize = UiStyles.dimenPx(context, R.dimen.launcher_dashboard_quick_icon_size);
        if (smallerIcon) {
            iconSize = Math.round(iconSize * 0.85f);
        }
        FrameLayout slot = new FrameLayout(context);
        UiStyles.setBackgroundRes(slot, slotBg);
        LinearLayout.LayoutParams slotLp = new LinearLayout.LayoutParams(slotSize, slotSize);
        slotLp.gravity = Gravity.CENTER_HORIZONTAL;
        tile.addView(slot, slotLp);
        ImageView icon = new AppCompatImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImageResource(iconRes);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(iconSize, iconSize);
        iconLp.gravity = Gravity.CENTER;
        slot.addView(icon, iconLp);
        return icon;
    }

    private TextView createCompactLabel(String text, int colorRes) {
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.text_size_small));
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setTextColor(UiStyles.color(context, colorRes));
        label.setPadding(0, UiStyles.dimenPx(context, R.dimen.spacing_tiny), 0, 0);
        return label;
    }

    private void addCompactTileToGrid(GridLayout grid, View tile) {
        int spacing = UiStyles.dimenPx(context, R.dimen.spacing_tiny);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(spacing / 2, spacing / 2, spacing / 2, spacing / 2);
        grid.addView(tile, lp);
    }

    private void applyBorderlessRipple(View view) {
        TypedValue outValue = new TypedValue();
        if (context.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, outValue, true)) {
            view.setBackgroundResource(outValue.resourceId);
        }
    }

    private LinearLayout buildMusicDriveCard() {
        LinearLayout card = createGlassCard();
        card.setMinimumWidth(0);

        // Sekme thumb çizilmeden önce default'u ayarla
        musicTabSelected = true;
        musicDriveTabTrack = buildMusicDriveTabTrack();
        card.addView(musicDriveTabTrack, matchWidthWrap());

        FrameLayout panelHost = new FrameLayout(context);
        LinearLayout.LayoutParams hostLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f);
        hostLp.topMargin = UiStyles.dimenPx(context, R.dimen.spacing_small);
        card.addView(panelHost, hostLp);

        musicPanel = buildMusicPanel();
        drivePanel = buildDrivePanel();
        panelHost.addView(musicPanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        panelHost.addView(drivePanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        applyMusicDriveTab(true);
        return card;
    }

    private LinearLayout buildMusicDriveTabTrack() {
        LinearLayout track = new LinearLayout(context);
        track.setOrientation(LinearLayout.HORIZONTAL);
        UiStyles.setBackgroundRes(track, R.drawable.bg_segment_track);
        int tpad = UiStyles.dimenPx(context, R.dimen.spacing_tiny);
        track.setPadding(tpad, tpad, tpad, tpad);

        TextView musicTab = new TextView(context);
        TextView driveTab = new TextView(context);
        musicTabView = musicTab;
        driveTabView = driveTab;
        musicTab.setText(R.string.launcher_dashboard_tab_music);
        driveTab.setText(R.string.launcher_dashboard_tab_drive);
        styleTabLabel(musicTab);
        styleTabLabel(driveTab);

        musicTab.setOnClickListener(v -> {
            if (!musicTabSelected) {
                musicTabSelected = true;
                refreshMusicDriveTabs(musicTab, driveTab);
                applyMusicDriveTab(true);
            }
        });
        driveTab.setOnClickListener(v -> {
            if (musicTabSelected) {
                musicTabSelected = false;
                refreshMusicDriveTabs(musicTab, driveTab);
                applyMusicDriveTab(false);
            }
        });
        refreshMusicDriveTabs(musicTab, driveTab);

        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        track.addView(musicTab, segLp);
        track.addView(driveTab, segLp);
        return track;
    }

    private void styleTabLabel(TextView tab) {
        tab.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.text_size_normal));
        tab.setGravity(Gravity.CENTER);
        tab.setMinHeight(UiStyles.dimenPx(context, R.dimen.button_height));
        tab.setClickable(true);
        tab.setFocusable(true);
    }

    private void refreshMusicDriveTabs(TextView musicTab, TextView driveTab) {
        if (musicTabSelected) {
            UiStyles.setBackgroundRes(musicTab, R.drawable.bg_segment_thumb);
            musicTab.setTextColor(UiStyles.color(context, R.color.textPrimary));
            musicTab.setTypeface(null, Typeface.BOLD);
            driveTab.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            driveTab.setTextColor(UiStyles.color(context, R.color.textSecondary));
            driveTab.setTypeface(null, Typeface.NORMAL);
        } else {
            UiStyles.setBackgroundRes(driveTab, R.drawable.bg_segment_thumb);
            driveTab.setTextColor(UiStyles.color(context, R.color.textPrimary));
            driveTab.setTypeface(null, Typeface.BOLD);
            musicTab.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            musicTab.setTextColor(UiStyles.color(context, R.color.textSecondary));
            musicTab.setTypeface(null, Typeface.NORMAL);
        }
    }

    private void applyMusicDriveTab(boolean showMusic) {
        if (musicPanel != null) {
            musicPanel.setVisibility(showMusic ? View.VISIBLE : View.GONE);
        }
        if (drivePanel != null) {
            drivePanel.setVisibility(showMusic ? View.GONE : View.VISIBLE);
        }
    }

    private LinearLayout buildMusicPanel() {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        buildMediaControls(panel);
        return panel;
    }

    private LinearLayout buildDrivePanel() {
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);

        LinearLayout driveSection = new LinearLayout(context);
        driveSection.setOrientation(LinearLayout.VERTICAL);
        driveSection.setGravity(Gravity.CENTER);
        fillDrivingSection(driveSection);
        body.addView(driveSection, weightedSectionLp(58f));

        View divider = new View(context);
        divider.setBackgroundColor(UiStyles.color(context, R.color.glassStroke));
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, UiStyles.dimenPx(context, R.dimen.spacing_tiny) / 2));
        dividerLp.topMargin = UiStyles.dimenPx(context, R.dimen.spacing_tiny);
        dividerLp.bottomMargin = UiStyles.dimenPx(context, R.dimen.spacing_tiny);
        body.addView(divider, dividerLp);

        TextView fuelTitle = createSectionMiniLabel(R.string.launcher_dashboard_card_fuel_motor);
        fuelTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.launcher_dashboard_fuel_section_title));
        fuelTitle.setIncludeFontPadding(false);
        body.addView(fuelTitle, matchWidthWrap());

        LinearLayout fuelSection = new LinearLayout(context);
        fuelSection.setOrientation(LinearLayout.VERTICAL);
        fillFuelSection(fuelSection);
        LinearLayout.LayoutParams fuelLp = weightedSectionLp(42f);
        fuelLp.topMargin = UiStyles.dimenPx(context, R.dimen.spacing_tiny);
        body.addView(fuelSection, fuelLp);

        return body;
    }

    private void fillDrivingSection(LinearLayout body) {
        speedValueView = new TextView(context);
        speedValueView.setText("—");
        speedValueView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.launcher_dashboard_speed_text));
        speedValueView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        speedValueView.setIncludeFontPadding(false);
        speedValueView.setTextColor(UiStyles.color(context, R.color.textPrimary));
        speedValueView.setGravity(Gravity.CENTER);
        speedValueView.setMaxLines(1);
        speedValueView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        speedValueView.setClickable(true);
        speedValueView.setFocusable(true);
        speedValueView.setOnClickListener(v -> {
            if (vehicleGlbView == null) {
                return;
            }
            boolean on = vehicleGlbView.toggleWheelSimulation();
            speedValueView.setTextColor(on
                    ? UiStyles.color(context, R.color.accentHighlight)
                    : UiStyles.color(context, R.color.textMuted));
            applySnapshot(repository.currentSnapshot());
        });
        body.addView(speedValueView, matchWidthWrap());

        TextView unit = new TextView(context);
        unit.setText(R.string.launcher_dashboard_speed_unit);
        unit.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.launcher_dashboard_drive_unit));
        unit.setTextColor(UiStyles.color(context, R.color.textSecondary));
        unit.setGravity(Gravity.CENTER);
        unit.setIncludeFontPadding(false);
        body.addView(unit, matchWidthWrap());

        gearLabelView = new TextView(context);
        gearLabelView.setText(formatter.formatDashboardGearLabel(
                VehicleMetricsSnapshot.empty(false)));
        gearLabelView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.launcher_dashboard_drive_gear));
        gearLabelView.setTypeface(null, Typeface.BOLD);
        gearLabelView.setTextColor(UiStyles.color(context, R.color.accentHighlight));
        gearLabelView.setGravity(Gravity.CENTER);
        gearLabelView.setIncludeFontPadding(false);
        body.addView(gearLabelView, matchWidthWrap());

        powerStatusView = new TextView(context);
        powerStatusView.setText(R.string.launcher_dashboard_loading);
        powerStatusView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.launcher_dashboard_drive_status));
        powerStatusView.setTextColor(UiStyles.color(context, R.color.textSecondary));
        powerStatusView.setGravity(Gravity.CENTER);
        powerStatusView.setIncludeFontPadding(false);
        body.addView(powerStatusView, matchWidthWrap());

        rpmValueView = addCenterMetric(body, R.string.launcher_dashboard_rpm_label);
        tripValueView = addCenterMetric(body, R.string.launcher_dashboard_trip_label);
    }

    private void fillFuelSection(LinearLayout body) {
        fuelPercentView = addFuelSection(body);
        rangeValueView = addMetricRow(body, R.string.launcher_dashboard_stat_range);
        consumptionValueView = addMetricRow(body, R.string.launcher_dashboard_stat_consumption);
        coolantValueView = addMetricRow(body, R.string.launcher_dashboard_stat_coolant);
        odoValueView = addMetricRow(body, R.string.launcher_dashboard_stat_odo);
        lowFuelValueView = addMetricRow(body, R.string.launcher_dashboard_stat_low_fuel);
    }

    private LinearLayout buildVehicleCard() {
        LinearLayout card = createCardShell(context.getString(R.string.launcher_dashboard_card_media));
        LinearLayout body = cardContent(card);

        int btnSize = UiStyles.dimenPx(context, R.dimen.launcher_dashboard_control_size);
        int inset = UiStyles.dimenPx(context, R.dimen.spacing_small);
        int iconPad = UiStyles.dimenPx(context, R.dimen.spacing_small);

        interiorButton = new AppCompatImageButton(context);
        UiStyles.setBackgroundRes(interiorButton, R.drawable.bg_vehicle_quick_control);
        interiorButton.setImageResource(R.drawable.ic_mdi_car_seat);
        interiorButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        interiorButton.setColorFilter(UiStyles.color(context, R.color.oemAccent));
        interiorButton.setPadding(iconPad, iconPad, iconPad, iconPad);
        interiorButton.setContentDescription(
                context.getString(R.string.launcher_dashboard_interior_mode));
        interiorButton.setOnClickListener(v -> {
            if (vehicleGlbView == null) {
                return;
            }
            boolean interior = vehicleGlbView.toggleInteriorMode();
            interiorButton.setImageResource(
                    interior ? R.drawable.ic_mdi_car : R.drawable.ic_mdi_car_seat);
            interiorButton.setContentDescription(context.getString(
                    interior
                            ? R.string.launcher_dashboard_exterior_mode
                            : R.string.launcher_dashboard_interior_mode));
            if (poseEditorCard != null && poseEditorCard.getVisibility() == View.VISIBLE) {
                fillPoseInputsFromModel();
            }
        });

        trunkButton = new AppCompatImageButton(context);
        UiStyles.setBackgroundRes(trunkButton, R.drawable.bg_vehicle_quick_control);
        trunkButton.setImageResource(R.drawable.ic_mdi_car_trunk);
        trunkButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        trunkButton.setColorFilter(UiStyles.color(context, R.color.oemAccent));
        trunkButton.setPadding(iconPad, iconPad, iconPad, iconPad);
        trunkButton.setContentDescription(
                context.getString(R.string.launcher_dashboard_trunk_open));
        trunkButton.setOnClickListener(v -> {
            if (vehicleGlbView == null) {
                return;
            }
            int speed = repository.currentSnapshot().preferredSpeed();
            float effective = vehicleGlbView.getEffectiveWheelSpeedKmh();
            boolean moving = speed > 0 || effective > 0.5f;
            if (moving && !vehicleGlbView.isTrunkOpen()) {
                Toast.makeText(context,
                        R.string.launcher_dashboard_trunk_blocked_moving,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            boolean open = vehicleGlbView.toggleTrunkOpen();
            trunkButton.setColorFilter(open
                    ? UiStyles.color(context, R.color.accentHighlight)
                    : bodyControlIdleAccent());
            trunkButton.setContentDescription(context.getString(open
                    ? R.string.launcher_dashboard_trunk_close
                    : R.string.launcher_dashboard_trunk_open));
        });

        sunroofButton = new AppCompatImageButton(context);
        UiStyles.setBackgroundRes(sunroofButton, R.drawable.bg_vehicle_quick_control);
        sunroofButton.setImageResource(R.drawable.ic_mdi_car_sunroof);
        sunroofButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        sunroofButton.setColorFilter(UiStyles.color(context, R.color.oemAccent));
        sunroofButton.setPadding(iconPad, iconPad, iconPad, iconPad);
        sunroofButton.setContentDescription(
                context.getString(R.string.launcher_dashboard_sunroof_open));
        sunroofButton.setOnClickListener(v -> {
            if (vehicleGlbView == null) {
                return;
            }
            boolean open = vehicleGlbView.toggleSunroofOpen();
            sunroofButton.setColorFilter(open
                    ? UiStyles.color(context, R.color.accentHighlight)
                    : bodyControlIdleAccent());
            sunroofButton.setContentDescription(context.getString(open
                    ? R.string.launcher_dashboard_sunroof_close
                    : R.string.launcher_dashboard_sunroof_open));
        });

        doorsButton = new AppCompatImageButton(context);
        UiStyles.setBackgroundRes(doorsButton, R.drawable.bg_vehicle_quick_control);
        doorsButton.setImageResource(R.drawable.ic_mdi_car_door);
        doorsButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        doorsButton.setColorFilter(UiStyles.color(context, R.color.oemAccent));
        doorsButton.setPadding(iconPad, iconPad, iconPad, iconPad);
        doorsButton.setContentDescription(
                context.getString(R.string.launcher_dashboard_doors_open));
        doorsButton.setOnClickListener(v -> {
            if (vehicleGlbView == null) {
                return;
            }
            boolean open = vehicleGlbView.toggleDoorsOpen();
            doorsButton.setColorFilter(open
                    ? UiStyles.color(context, R.color.accentHighlight)
                    : bodyControlIdleAccent());
            doorsButton.setContentDescription(context.getString(open
                    ? R.string.launcher_dashboard_doors_close
                    : R.string.launcher_dashboard_doors_open));
        });

        // Başlık: ECO etiketi + kapı + cam tavan + bagaj + interior
        View mediaTitle = card.getChildAt(0);
        if (mediaTitle instanceof TextView) {
            card.removeView(mediaTitle);
            LinearLayout headerRow = new LinearLayout(context);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(Gravity.CENTER_VERTICAL);
            mediaCardTitleView = (TextView) mediaTitle;
            headerRow.addView(mediaCardTitleView, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            driveModeLabelView = new TextView(context);
            driveModeLabelView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    context.getResources().getDimension(R.dimen.text_size_small));
            driveModeLabelView.setTypeface(null, Typeface.BOLD);
            driveModeLabelView.setLetterSpacing(0.08f);
            driveModeLabelView.setVisibility(View.GONE);
            LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            modeLp.setMarginStart(inset);
            headerRow.addView(driveModeLabelView, modeLp);

            LinearLayout.LayoutParams headerBtnLp = new LinearLayout.LayoutParams(btnSize, btnSize);
            headerBtnLp.setMarginStart(inset);
            headerRow.addView(doorsButton, headerBtnLp);
            LinearLayout.LayoutParams sunroofBtnLp = new LinearLayout.LayoutParams(btnSize, btnSize);
            sunroofBtnLp.setMarginStart(inset);
            headerRow.addView(sunroofButton, sunroofBtnLp);
            LinearLayout.LayoutParams trunkBtnLp = new LinearLayout.LayoutParams(btnSize, btnSize);
            trunkBtnLp.setMarginStart(inset);
            headerRow.addView(trunkButton, trunkBtnLp);
            LinearLayout.LayoutParams interiorBtnLp = new LinearLayout.LayoutParams(btnSize, btnSize);
            interiorBtnLp.setMarginStart(inset);
            headerRow.addView(interiorButton, interiorBtnLp);
            card.addView(headerRow, 0, matchWidthWrap());

            mediaCardTitleView.setClickable(true);
            mediaCardTitleView.setFocusable(true);
            mediaCardTitleView.setOnClickListener(v -> {
                if (vehicleGlbView == null) {
                    return;
                }
                boolean on = vehicleGlbView.togglePickDebugEnabled();
                mediaCardTitleView.setTextColor(on
                        ? UiStyles.color(context, R.color.accentHighlight)
                        : UiStyles.color(context, R.color.textMuted));
                if (poseEditorCard != null) {
                    poseEditorCard.setVisibility(on ? View.VISIBLE : View.GONE);
                }
                if (on) {
                    fillPoseInputsFromModel();
                }
            });
        }

        glbHost = new FrameLayout(context);
        body.addView(glbHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));
        applyLauncherDisplayMode();

        return card;
    }

    private void buildMediaControls(LinearLayout mediaSection) {
        FrameLayout mediaHost = new FrameLayout(context);
        // Cam kartın kendi rengi görünsün — ayrı koyu panel yok
        mediaHost.setBackgroundColor(UiStyles.color(context, R.color.transparent));
        mediaHost.setClipToOutline(false);

        mediaActiveContainer = new LinearLayout(context);
        mediaActiveContainer.setOrientation(LinearLayout.VERTICAL);
        mediaActiveContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        int mediaPad = UiStyles.dimenPx(context, R.dimen.spacing_small);
        mediaActiveContainer.setPadding(mediaPad, mediaPad, mediaPad, mediaPad);

        // Üst: kapak + meta + süre (esnek alanda ortalı)
        LinearLayout metaBlock = new LinearLayout(context);
        metaBlock.setOrientation(LinearLayout.VERTICAL);
        metaBlock.setGravity(Gravity.CENTER);

        int thumbSize = UiStyles.dimenPx(context, R.dimen.launcher_dashboard_media_art_size);
        thumbSize = Math.round(thumbSize * 1.35f);
        albumArtView = new ImageView(context);
        albumArtView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        albumArtView.setVisibility(View.GONE);
        UiStyles.applySolidRoundedBackgroundDp(albumArtView,
                UiStyles.color(context, R.color.surfaceCard), 10f);
        albumArtView.setClipToOutline(true);
        LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(thumbSize, thumbSize);
        thumbLp.gravity = Gravity.CENTER_HORIZONTAL;
        thumbLp.bottomMargin = UiStyles.dimenPx(context, R.dimen.spacing_small);
        metaBlock.addView(albumArtView, thumbLp);

        trackTitleView = createMediaText(R.dimen.launcher_dashboard_media_title, R.color.textSecondary, 2);
        trackTitleView.setTypeface(null, Typeface.BOLD);
        trackTitleView.setGravity(Gravity.CENTER);
        trackTitleView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        trackArtistView = createMediaText(R.dimen.launcher_dashboard_media_artist, R.color.textMuted, 1);
        trackArtistView.setGravity(Gravity.CENTER);
        trackArtistView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        LinearLayout.LayoutParams artistLp = matchWidthWrap();
        artistLp.topMargin = UiStyles.dimenPx(context, R.dimen.spacing_tiny);

        metaBlock.addView(trackTitleView, matchWidthWrap());
        metaBlock.addView(trackArtistView, artistLp);

        LinearLayout progressRow = new LinearLayout(context);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        progressRow.setPadding(0, UiStyles.dimenPx(context, R.dimen.spacing_small), 0, 0);
        mediaPositionView = createMediaText(R.dimen.launcher_dashboard_media_time, R.color.textMuted, 0);
        mediaPositionView.setGravity(Gravity.CENTER);
        mediaDurationView = createMediaText(R.dimen.launcher_dashboard_media_time, R.color.textMuted, 0);
        mediaDurationView.setGravity(Gravity.CENTER);
        mediaProgressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        mediaProgressBar.setIndeterminate(false);
        mediaProgressBar.setMax(10_000);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        progressLp.setMargins(UiStyles.dimenPx(context, R.dimen.spacing_tiny), 0,
                UiStyles.dimenPx(context, R.dimen.spacing_tiny), 0);
        progressRow.addView(mediaPositionView);
        progressRow.addView(mediaProgressBar, progressLp);
        progressRow.addView(mediaDurationView);
        metaBlock.addView(progressRow, matchWidthWrap());

        mediaActiveContainer.addView(metaBlock, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));

        // Alt: kontroller — tabandan bir satır yukarı
        LinearLayout bottomControls = new LinearLayout(context);
        bottomControls.setOrientation(LinearLayout.VERTICAL);
        bottomControls.setGravity(Gravity.CENTER_HORIZONTAL);

        int controlSize = UiStyles.dimenPx(context, R.dimen.launcher_dashboard_media_control_size);
        int volumeSize = Math.round(controlSize * 0.85f);

        View previousButton = createMediaControl(
                R.drawable.ic_mdi_skip_previous, R.string.launcher_media_previous,
                v -> mediaController.skipToPrevious());
        playPauseButton = createMediaControl(
                R.drawable.ic_mdi_play, R.string.launcher_media_play,
                v -> mediaController.playPause());
        playPauseIconView = (ImageView) ((FrameLayout) playPauseButton).getChildAt(0);
        View nextButton = createMediaControl(
                R.drawable.ic_mdi_skip_next, R.string.launcher_media_next,
                v -> mediaController.skipToNext());
        bottomControls.addView(
                buildMediaAlignedRow(previousButton, playPauseButton, nextButton, controlSize),
                matchWidthWrap());

        View volumeDown = createMediaControl(
                R.drawable.ic_mdi_volume_minus, R.string.launcher_media_volume_down,
                v -> mediaController.volumeDown());
        View muteButton = createMediaControl(
                R.drawable.ic_mdi_volume_mute, R.string.launcher_media_mute,
                v -> mediaController.toggleMute());
        View volumeUp = createMediaControl(
                R.drawable.ic_mdi_volume_plus, R.string.launcher_media_volume_up,
                v -> mediaController.volumeUp());
        // Sol: ses- | Orta: sessiz | Sağ: ses+
        LinearLayout.LayoutParams volumeRowLp = matchWidthWrap();
        volumeRowLp.topMargin = UiStyles.dimenPx(context, R.dimen.spacing_tiny);
        bottomControls.addView(
                buildMediaAlignedRow(volumeDown, muteButton, volumeUp, volumeSize),
                volumeRowLp);

        LinearLayout.LayoutParams bottomLp = matchWidthWrap();
        bottomLp.bottomMargin = controlSize; // bir satır yukarı
        mediaActiveContainer.addView(bottomControls, bottomLp);

        mediaHost.addView(mediaActiveContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        mediaEmptyContainer = new LinearLayout(context);
        mediaEmptyContainer.setOrientation(LinearLayout.VERTICAL);
        mediaEmptyContainer.setGravity(Gravity.CENTER);
        int emptyPad = UiStyles.dimenPx(context, R.dimen.spacing_small);
        mediaEmptyContainer.setPadding(emptyPad, emptyPad, emptyPad, emptyPad);
        ImageView emptyIcon = new ImageView(context);
        emptyIcon.setImageResource(R.drawable.ic_mdi_music_note);
        emptyIcon.setColorFilter(UiStyles.color(context, R.color.accentHighlight));
        int emptyIconSize = UiStyles.dimenPx(context, R.dimen.launcher_dashboard_media_art_size);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(emptyIconSize, emptyIconSize);
        iconLp.gravity = Gravity.CENTER_HORIZONTAL;
        mediaEmptyContainer.addView(emptyIcon, iconLp);
        mediaEmptyTitleView = new TextView(context);
        mediaEmptyTitleView.setText(R.string.launcher_dashboard_media_empty_title);
        mediaEmptyTitleView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.launcher_dashboard_media_title));
        mediaEmptyTitleView.setTypeface(null, Typeface.BOLD);
        mediaEmptyTitleView.setTextColor(UiStyles.color(context, R.color.textPrimary));
        mediaEmptyTitleView.setGravity(Gravity.CENTER);
        mediaEmptyTitleView.setPadding(0, UiStyles.dimenPx(context, R.dimen.spacing_small), 0, 0);
        mediaEmptyContainer.addView(mediaEmptyTitleView, matchWidthWrap());
        mediaEmptyBodyView = new TextView(context);
        mediaEmptyBodyView.setText(R.string.launcher_dashboard_media_empty_body);
        mediaEmptyBodyView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.launcher_dashboard_media_artist));
        mediaEmptyBodyView.setTextColor(UiStyles.color(context, R.color.textSecondary));
        mediaEmptyBodyView.setGravity(Gravity.CENTER);
        mediaEmptyContainer.addView(mediaEmptyBodyView, matchWidthWrap());

        mediaHost.addView(mediaEmptyContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        mediaSection.addView(mediaHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
    }

    private LinearLayout buildPoseEditorCard() {
        LinearLayout card = createCardShell(context.getString(R.string.launcher_dashboard_card_pose));
        LinearLayout body = cardContent(card);

        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        body.addView(grid, matchWidthWrap());

        addPoseRow(grid, "centerX", "centerY", "centerZ");
        addPoseRow(grid, "offsetX", "offsetY", "offsetZ");
        addPoseRow(grid, "yaw", "pitch", "roll");
        addPoseRow(grid, "scale", "eyeX", "eyeY");
        addPoseRow(grid, "eyeZ", null, null);

        Button applyButton = new Button(context);
        applyButton.setText(R.string.launcher_dashboard_pose_apply);
        applyButton.setAllCaps(false);
        UiStyles.styleOemButton(applyButton,
                UiStyles.color(context, R.color.accentHighlight));
        LinearLayout.LayoutParams btnLp = matchWidthWrap();
        btnLp.topMargin = UiStyles.dimenPx(context, R.dimen.spacing_small);
        body.addView(applyButton, btnLp);
        applyButton.setOnClickListener(v -> applyPoseFromInputs());

        return card;
    }

    private void addPoseRow(LinearLayout parent, String keyA, String keyB, String keyC) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int gap = UiStyles.dimenPx(context, R.dimen.spacing_tiny);
        row.setPadding(0, gap / 2, 0, gap / 2);

        if (keyA != null) {
            row.addView(buildPoseField(keyA), poseFieldLp());
        }
        if (keyB != null) {
            LinearLayout.LayoutParams lp = poseFieldLp();
            lp.setMarginStart(gap);
            row.addView(buildPoseField(keyB), lp);
        }
        if (keyC != null) {
            LinearLayout.LayoutParams lp = poseFieldLp();
            lp.setMarginStart(gap);
            row.addView(buildPoseField(keyC), lp);
        }
        parent.addView(row, matchWidthWrap());
    }

    private LinearLayout.LayoutParams poseFieldLp() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout buildPoseField(String key) {
        LinearLayout field = new LinearLayout(context);
        field.setOrientation(LinearLayout.VERTICAL);

        TextView label = new TextView(context);
        label.setText(key);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.text_size_small));
        label.setTextColor(UiStyles.color(context, R.color.textMuted));
        field.addView(label);

        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setShowSoftInputOnFocus(false);
        input.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.text_size_small));
        input.setTextColor(UiStyles.color(context, R.color.textPrimary));
        input.setHintTextColor(UiStyles.color(context, R.color.textMuted));
        input.setSingleLine(true);
        input.setPadding(
                UiStyles.dimenPx(context, R.dimen.spacing_tiny),
                UiStyles.dimenPx(context, R.dimen.spacing_tiny),
                UiStyles.dimenPx(context, R.dimen.spacing_tiny),
                UiStyles.dimenPx(context, R.dimen.spacing_tiny));
        field.addView(input, matchWidthWrap());
        poseInputs.put(key, input);
        return field;
    }

    private void fillPoseInputsFromModel() {
        if (vehicleGlbView == null) {
            return;
        }
        VehicleGlbView.PoseConfig c = vehicleGlbView.getPoseConfig();
        setPoseInput("centerX", c.centerX);
        setPoseInput("centerY", c.centerY);
        setPoseInput("centerZ", c.centerZ);
        setPoseInput("offsetX", c.offsetX);
        setPoseInput("offsetY", c.offsetY);
        setPoseInput("offsetZ", c.offsetZ);
        setPoseInput("yaw", c.yawDeg);
        setPoseInput("pitch", c.pitchDeg);
        setPoseInput("roll", c.rollDeg);
        setPoseInput("scale", c.scale);
        setPoseInput("eyeX", c.cameraEyeX);
        setPoseInput("eyeY", c.cameraEyeY);
        setPoseInput("eyeZ", c.cameraEyeZ);
    }

    private void setPoseInput(String key, float value) {
        EditText input = poseInputs.get(key);
        if (input != null) {
            input.setText(String.format(Locale.US, "%.3f", value));
        }
    }

    private void applyPoseFromInputs() {
        if (vehicleGlbView == null) {
            return;
        }
        VehicleGlbView.PoseConfig c = new VehicleGlbView.PoseConfig();
        c.centerX = readPoseInput("centerX", c.centerX);
        c.centerY = readPoseInput("centerY", c.centerY);
        c.centerZ = readPoseInput("centerZ", c.centerZ);
        c.offsetX = readPoseInput("offsetX", c.offsetX);
        c.offsetY = readPoseInput("offsetY", c.offsetY);
        c.offsetZ = readPoseInput("offsetZ", c.offsetZ);
        c.yawDeg = readPoseInput("yaw", c.yawDeg);
        c.pitchDeg = readPoseInput("pitch", c.pitchDeg);
        c.rollDeg = readPoseInput("roll", c.rollDeg);
        c.scale = readPoseInput("scale", c.scale);
        c.cameraEyeX = readPoseInput("eyeX", c.cameraEyeX);
        c.cameraEyeY = readPoseInput("eyeY", c.cameraEyeY);
        c.cameraEyeZ = readPoseInput("eyeZ", c.cameraEyeZ);
        vehicleGlbView.applyPoseConfig(c);
    }

    private float readPoseInput(String key, float fallback) {
        EditText input = poseInputs.get(key);
        if (input == null) {
            return fallback;
        }
        String raw = input.getText() != null ? input.getText().toString().trim() : "";
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return Float.parseFloat(raw.replace(',', '.'));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private LinearLayout createGlassCard() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        UiStyles.setGlassCardBackground(card);
        int pad = UiStyles.dimenPx(context, R.dimen.oem_card_inner_padding);
        card.setPadding(pad, pad, pad, pad);
        return card;
    }

    private LinearLayout createCardShell(String title) {
        LinearLayout card = createGlassCard();

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.text_size_small));
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(UiStyles.color(context, R.color.textMuted));
        titleView.setAllCaps(true);
        titleView.setLetterSpacing(0.08f);
        card.addView(titleView, matchWidthWrap());
        return card;
    }

    private LinearLayout cardContent(LinearLayout card) {
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f);
        bodyLp.topMargin = UiStyles.dimenPx(context, R.dimen.spacing_medium);
        card.addView(body, bodyLp);
        return body;
    }

    private TextView addCenterMetric(LinearLayout parent, int labelRes) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, 0, 0, 0);

        TextView label = new TextView(context);
        label.setText(labelRes);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.launcher_dashboard_drive_metric_label));
        label.setTextColor(UiStyles.color(context, R.color.textMuted));
        label.setIncludeFontPadding(false);
        label.setPadding(0, 0, UiStyles.dimenPx(context, R.dimen.spacing_tiny), 0);
        row.addView(label);

        TextView value = new TextView(context);
        value.setText("—");
        value.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.launcher_dashboard_drive_metric_value));
        value.setTypeface(null, Typeface.BOLD);
        value.setTextColor(UiStyles.color(context, R.color.textPrimary));
        value.setIncludeFontPadding(false);
        row.addView(value);

        parent.addView(row, matchWidthWrap());
        return value;
    }

    private TextView addFuelSection(LinearLayout parent) {
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        int padV = UiStyles.dimenPx(context, R.dimen.spacing_tiny);
        block.setPadding(0, padV, 0, padV);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(context);
        label.setText(R.string.launcher_dashboard_stat_fuel);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.launcher_dashboard_fuel_label));
        label.setTextColor(UiStyles.color(context, R.color.textMuted));
        label.setIncludeFontPadding(false);
        header.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(context);
        value.setText("—");
        value.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.launcher_dashboard_stat_value));
        value.setTypeface(null, Typeface.BOLD);
        value.setTextColor(UiStyles.color(context, R.color.textPrimary));
        value.setIncludeFontPadding(false);
        header.addView(value);

        block.addView(header, matchWidthWrap());

        fuelProgressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        fuelProgressBar.setIndeterminate(false);
        fuelProgressBar.setMax(100);
        fuelProgressBar.setProgressDrawable(ContextCompat.getDrawable(context, R.drawable.bg_launcher_fuel_progress));
        LinearLayout.LayoutParams barLp = matchWidthWrap();
        barLp.topMargin = UiStyles.dimenPx(context, R.dimen.spacing_tiny);
        block.addView(fuelProgressBar, barLp);

        parent.addView(block, matchWidthWrap());
        return value;
    }

    private TextView addMetricRow(LinearLayout parent, int labelRes) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, 0);

        TextView label = new TextView(context);
        label.setText(labelRes);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.launcher_dashboard_fuel_label));
        label.setTextColor(UiStyles.color(context, R.color.textMuted));
        label.setIncludeFontPadding(false);
        label.setMaxLines(1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(context);
        value.setText("—");
        value.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.launcher_dashboard_stat_value));
        value.setTypeface(null, Typeface.BOLD);
        value.setTextColor(UiStyles.color(context, R.color.textPrimary));
        value.setGravity(Gravity.END);
        value.setIncludeFontPadding(false);
        value.setMaxLines(1);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        // Value WRAP ederse kolon taşmasın — sabit max, küçülmeye izin ver
        value.setMinWidth(0);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        valueLp.setMarginStart(UiStyles.dimenPx(context, R.dimen.spacing_tiny));
        row.addView(value, valueLp);

        parent.addView(row, matchWidthWrap());
        return value;
    }

    private TextView createMediaText(int textSizeRes, int colorRes, int maxLines) {
        TextView textView = new TextView(context);
        textView.setText("—");
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(textSizeRes));
        textView.setTextColor(UiStyles.color(context, colorRes));
        if (maxLines > 0) {
            textView.setMaxLines(maxLines);
            textView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        }
        return textView;
    }

    /** Sol / orta / sağ hizalı üçlü kontrol satırı. */
    private LinearLayout buildMediaAlignedRow(View left, View center, View right, int buttonSize) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UiStyles.dimenPx(context, R.dimen.spacing_tiny), 0, 0);

        row.addView(wrapMediaControlSlot(left, buttonSize, Gravity.START | Gravity.CENTER_VERTICAL),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(wrapMediaControlSlot(center, buttonSize, Gravity.CENTER),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(wrapMediaControlSlot(right, buttonSize, Gravity.END | Gravity.CENTER_VERTICAL),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private FrameLayout wrapMediaControlSlot(View button, int buttonSize, int gravity) {
        FrameLayout slot = new FrameLayout(context);
        FrameLayout.LayoutParams btnLp = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        btnLp.gravity = gravity;
        slot.addView(button, btnLp);
        return slot;
    }

    /** Hızlı erişim ile aynı slot + ikon ölçeği. */
    private View createMediaControl(
            @DrawableRes int iconRes,
            int contentDescriptionRes,
            View.OnClickListener clickListener) {
        FrameLayout slot = new FrameLayout(context);
        UiStyles.setBackgroundRes(slot, R.drawable.bg_launcher_icon_slot);
        slot.setClickable(true);
        slot.setFocusable(true);
        applyBorderlessRipple(slot);
        slot.setContentDescription(context.getString(contentDescriptionRes));
        slot.setOnClickListener(clickListener);

        int iconSize = UiStyles.dimenPx(context, R.dimen.launcher_dashboard_media_control_icon_size);
        ImageView icon = new AppCompatImageView(context);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setColorFilter(UiStyles.color(context, R.color.oemAccent));
        mediaControlIcons.add(icon);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(iconSize, iconSize);
        iconLp.gravity = Gravity.CENTER;
        slot.addView(icon, iconLp);
        return slot;
    }

    private static LinearLayout.LayoutParams weightedLp(float weight) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
    }

    private static LinearLayout.LayoutParams weightedSectionLp(float weight) {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                weight);
    }

    private LinearLayout.LayoutParams matchWidthWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
