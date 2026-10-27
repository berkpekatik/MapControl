package com.mapcontrol.ui.builder;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaDataSource;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

import com.mapcontrol.R;
import com.mapcontrol.dvr.CameraGlEngine;
import com.mapcontrol.dvr.CameraRecorder;
import com.mapcontrol.dvr.DvrClipStore;
import com.mapcontrol.dvr.DvrController;
import com.mapcontrol.ui.theme.UiStyles;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * "Izleme Ekrani": arac ici klipler (gun gruplu liste) + 4 acinin senkron oynatimi
 * (tek dosya kayitta mozaik, aci secilince ilgili ceyrek buyutulur), silinme geri
 * sayimi, coklu secimle USB'ye aktarma ve silme.
 */
final class Camera360ClipViewer {
    private static final int CAM_COUNT = CameraRecorder.CAM_SUFFIX.length;
    private static final long TICK_MS = 250;
    private static final int DRIFT_MS = 250;
    private static final int RATIO_W = 16;
    private static final int RATIO_H = 10;

    private final Camera360Ui ui;
    private final DvrController dvr;
    private final Runnable onBack;
    /** true: sol menu gizlensin (tam genislik), false: geri gelsin. */
    private final Consumer<Boolean> onWide;
    private SwitchCompat wideSwitch;
    private boolean wide;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Locale locale;

    private View root;
    private TextView summaryPill;
    private View body;
    private View emptyView;
    private ScrollView listScroll;
    private LinearLayout listColumn;
    private final List<View> rowViews = new ArrayList<>();
    private final List<ImageView> rowChecks = new ArrayList<>();

    private ImageView selectButton;
    private View selectionBar;
    private TextView selectionText;
    private TextView selectAllButton;
    private Button selExportButton;
    private Button selDeleteButton;
    private boolean selecting;
    private final Set<String> selected = new HashSet<>();

    private LinearLayout gridView;
    private TextureView mosaicView;
    private Surface mosaicSurface;
    /** Secili klip tek dosya (mozaik) mi; oynatici players[0]'dir. */
    private boolean currentSingle;
    private float touchX;
    private float touchY;

    private LinearLayout gridRow0;
    private LinearLayout gridRow1;
    private final FrameLayout[] cells = new FrameLayout[CAM_COUNT];
    private final TextView[] missingViews = new TextView[CAM_COUNT];
    private final SurfaceHolder[] holders = new SurfaceHolder[CAM_COUNT];
    private Camera360Ui.Choice angleChoice;

    private TextView clipTitle;
    private TextView clipMeta;
    private Button deleteButton;
    private Button exportButton;
    private Button exportingButton;
    private ImageView playButton;
    private ImageView prevButton;
    private ImageView nextButton;
    private TextView timeView;
    private SeekBar seekBar;

    private List<DvrClipStore.Clip> clips = new ArrayList<>();
    private int current = -1;
    /** -1 = 4 aci birlikte; aksi halde tek aci. */
    private int focusCam = -1;

    private final MediaPlayer[] players = new MediaPlayer[CAM_COUNT];
    private final boolean[] prepared = new boolean[CAM_COUNT];
    private int pendingPrepare;
    private int activePlayers;
    private int completedPlayers;
    private int loadToken;
    private boolean playing;
    private int durationMs;
    private int startOnReadyMs;
    /** Oynaticilar birakildiginda kaldigi yer. */
    private int resumeMs;
    private boolean userSeeking;
    private boolean visible;
    private boolean exporting;
    private boolean usbPresent;
    private int tickCount;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!visible) return;
            onTick();
            main.postDelayed(this, TICK_MS);
        }
    };

    Camera360ClipViewer(Camera360Ui ui, DvrController dvr, Runnable onBack, Consumer<Boolean> onWide) {
        this.ui = ui;
        this.dvr = dvr;
        this.onBack = onBack;
        this.onWide = onWide;
        this.locale = ui.context.getResources().getConfiguration().getLocales().get(0);
    }

    View build() {
        LinearLayout page = new LinearLayout(ui.context);
        page.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = ui.header(R.string.camera360_menu_viewer, onBack);
        summaryPill = ui.statusPill();
        ui.attachIcon(summaryPill, R.drawable.ic_mdi_folder, ui.themeColor(R.color.textSecondaryCool), 16);
        summaryPill.setTextColor(ui.themeColor(R.color.textSecondaryCool));
        summaryPill.setBackground(ui.roundRect(ui.themeColor(R.color.surfaceCardInner), 14));
        header.addView(summaryPill);
        selectButton = ui.iconButton(R.drawable.ic_mdi_check, 44, v -> setSelecting(!selecting));
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44));
        sbp.setMarginStart(ui.dp(10));
        header.addView(selectButton, sbp);
        header.addView(buildWideToggle(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(44)) {{
            setMarginStart(ui.dp(10));
        }});
        ImageView refresh = ui.iconButton(R.drawable.ic_mdi_refresh, 44, v -> reloadClips(false));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44));
        rp.setMarginStart(ui.dp(10));
        header.addView(refresh, rp);
        LinearLayout.LayoutParams hp = ui.matchWrap();
        hp.bottomMargin = ui.dp(10);
        page.addView(header, hp);

        FrameLayout content = new FrameLayout(ui.context);
        page.addView(content, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        body = buildBody();
        content.addView(body, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        emptyView = buildEmpty();
        emptyView.setVisibility(View.GONE);
        content.addView(emptyView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root = page;
        return page;
    }

    // ---------- Yerlesim ----------

    /** "Genis" etiketi + switch: sol menuyu gizler/gosterir. */
    private View buildWideToggle() {
        LinearLayout box = new LinearLayout(ui.context);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(ui.dp(12), 0, ui.dp(6), 0);
        box.setBackground(ui.roundRect(ui.themeColor(R.color.surfaceCardInner), 22));
        TextView label = ui.text(13, R.color.textSecondaryCool, true);
        label.setText(R.string.camera360_viewer_wide);
        label.setSingleLine(true);
        box.addView(label);
        wideSwitch = new SwitchCompat(ui.context);
        wideSwitch.setShowText(false);
        wideSwitch.setThumbTintList(switchTint(ui.themeColor(R.color.oemAccent),
                ui.themeColor(R.color.outlineMuted)));
        wideSwitch.setTrackTintList(switchTint(ui.themeColor(R.color.assistStrokeActive),
                ui.themeColor(R.color.outline)));
        wideSwitch.setOnCheckedChangeListener((b, on) -> setWide(on));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.setMarginStart(ui.dp(6));
        box.addView(wideSwitch, sp);
        box.setOnClickListener(v -> wideSwitch.toggle());
        return box;
    }

    private static ColorStateList switchTint(int checked, int unchecked) {
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{checked, unchecked});
    }

    private void setWide(boolean on) {
        if (wide == on) return;
        wide = on;
        if (wideSwitch != null && wideSwitch.isChecked() != on) wideSwitch.setChecked(on);
        if (onWide != null) onWide.accept(on);
    }

    private View buildBody() {
        LinearLayout row = new LinearLayout(ui.context);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout listCard = ui.card();
        listCard.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
        listScroll = new ScrollView(ui.context);
        listScroll.setVerticalScrollBarEnabled(false);
        listColumn = new LinearLayout(ui.context);
        listColumn.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(listColumn, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        listCard.addView(listScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        selectionBar = buildSelectionBar();
        selectionBar.setVisibility(View.GONE);
        listCard.addView(selectionBar, ui.matchWrap());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ui.dp(ui.isWide() ? 300 : 220), LinearLayout.LayoutParams.MATCH_PARENT);
        lp.setMarginEnd(ui.dp(12));
        row.addView(listCard, lp);

        LinearLayout playerCol = new LinearLayout(ui.context);
        playerCol.setOrientation(LinearLayout.VERTICAL);
        row.addView(playerCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        String[] labels = new String[CAM_COUNT + 1];
        labels[0] = ui.context.getString(R.string.camera360_all);
        for (int i = 0; i < CAM_COUNT; i++) labels[i + 1] = ui.camShortName(i);
        angleChoice = ui.choice(labels, 0, idx -> setFocus(idx - 1));
        playerCol.addView(angleChoice.view, ui.matchWrap());

        FrameLayout stage = new FrameLayout(ui.context);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        sp.topMargin = ui.dp(8);
        playerCol.addView(stage, sp);
        RatioFrame ratio = new RatioFrame(ui.context, RATIO_W / (float) RATIO_H);
        GradientDrawable stageBg = ui.roundRect(Color.BLACK, 16);
        ratio.setBackground(stageBg);
        ratio.setClipToOutline(true);
        stage.addView(ratio, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        LinearLayout grid = new LinearLayout(ui.context);
        grid.setOrientation(LinearLayout.VERTICAL);
        ratio.addView(grid, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        gridView = grid;
        mosaicView = buildMosaicView();
        mosaicView.setVisibility(View.GONE);
        ratio.addView(mosaicView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        gridRow0 = gridRow(grid);
        gridRow1 = gridRow(grid);
        for (int i = 0; i < CAM_COUNT; i++) {
            cells[i] = buildCell(i);
            LinearLayout target = i < 2 ? gridRow0 : gridRow1;
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            cp.setMargins(ui.dp(1), ui.dp(1), ui.dp(1), ui.dp(1));
            target.addView(cells[i], cp);
        }

        playerCol.addView(buildInfoRow(), ui.matchWrapTop(10));
        playerCol.addView(buildControls(), ui.matchWrapTop(6));
        return row;
    }

    private LinearLayout gridRow(LinearLayout grid) {
        LinearLayout r = new LinearLayout(ui.context);
        r.setOrientation(LinearLayout.HORIZONTAL);
        grid.addView(r, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return r;
    }

    /** Tek dosya kayit icin oynatici yuzeyi; donusum matrisiyle tek ceyrek buyutulebilir. */
    private TextureView buildMosaicView() {
        TextureView tv = new TextureView(ui.context);
        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
                mosaicSurface = new Surface(st);
                MediaPlayer mp = currentSingle ? players[0] : null;
                if (mp != null) {
                    try {
                        mp.setSurface(mosaicSurface);
                    } catch (Throwable ignored) {
                    }
                }
                applyMosaicZoom();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
                applyMosaicZoom();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                MediaPlayer mp = currentSingle ? players[0] : null;
                if (mp != null) {
                    try {
                        mp.setSurface(null);
                    } catch (Throwable ignored) {
                    }
                }
                if (mosaicSurface != null) mosaicSurface.release();
                mosaicSurface = null;
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture st) {
            }
        });
        tv.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                touchX = e.getX();
                touchY = e.getY();
            }
            return false;
        });
        tv.setOnClickListener(v -> {
            if (focusCam >= 0 || v.getWidth() <= 0 || v.getHeight() <= 0) {
                setFocus(-1);
                return;
            }
            int pos = (touchY < v.getHeight() / 2f ? 0 : 2) + (touchX < v.getWidth() / 2f ? 0 : 1);
            setFocus(CameraGlEngine.GRID_ORDER[pos]);
        });
        return tv;
    }

    private static int mosaicPosOf(int cam) {
        for (int i = 0; i < CameraGlEngine.GRID_ORDER.length; i++) {
            if (CameraGlEngine.GRID_ORDER[i] == cam) return i;
        }
        return 0;
    }

    private void applyMosaicZoom() {
        if (mosaicView == null) return;
        int w = mosaicView.getWidth();
        int h = mosaicView.getHeight();
        Matrix m = new Matrix();
        if (currentSingle && focusCam >= 0 && w > 0 && h > 0) {
            int pos = mosaicPosOf(focusCam);
            m.setScale(2f, 2f);
            m.postTranslate(-(pos % 2) * w, -(pos / 2) * h);
        }
        mosaicView.setTransform(m);
    }

    private Button actionButton(int iconRes, int fill) {
        Button b = new Button(ui.context);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setSingleLine(true);
        UiStyles.styleOemButton(b, fill);
        UiStyles.setButtonStartIconTinted(b, iconRes, Color.WHITE, ui.dp(6));
        b.setStateListAnimator(null);
        b.setPadding(ui.dp(12), 0, ui.dp(14), 0);
        return b;
    }

    private View buildSelectionBar() {
        LinearLayout bar = new LinearLayout(ui.context);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(ui.dp(4), ui.dp(8), ui.dp(4), ui.dp(2));

        LinearLayout top = new LinearLayout(ui.context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(top, ui.matchWrap());
        selectionText = ui.text(13, R.color.textPrimary, true);
        selectionText.setSingleLine(true);
        top.addView(selectionText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        selectAllButton = ui.text(13, R.color.textPrimary, true);
        selectAllButton.setTextColor(ui.themeColor(R.color.oemAccent));
        selectAllButton.setSingleLine(true);
        selectAllButton.setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(6));
        ui.setPressableBackground(selectAllButton, ui.roundRect(ui.themeColor(R.color.surfaceCardInner), 12), 12);
        selectAllButton.setOnClickListener(v -> toggleSelectAll());
        top.addView(selectAllButton);

        LinearLayout buttons = new LinearLayout(ui.context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(buttons, ui.matchWrapTop(8));
        selExportButton = actionButton(R.drawable.ic_mdi_usb, ui.themeColor(R.color.buttonPrimary));
        selExportButton.setOnClickListener(v -> startExport(selectedClips(), selExportButton));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, ui.dp(44), 1f);
        ep.setMarginEnd(ui.dp(6));
        buttons.addView(selExportButton, ep);
        selDeleteButton = actionButton(R.drawable.ic_mdi_delete, Camera360Ui.COLOR_REC);
        selDeleteButton.setOnClickListener(v -> confirmDelete(selectedClips()));
        buttons.addView(selDeleteButton, new LinearLayout.LayoutParams(0, ui.dp(44), 1f));
        return bar;
    }

    private FrameLayout buildCell(int cam) {
        FrameLayout cell = new FrameLayout(ui.context);
        cell.setBackgroundColor(0xFF0B0E12);
        SurfaceView sv = new SurfaceView(ui.context);
        sv.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                holders[cam] = holder;
                MediaPlayer mp = currentSingle ? null : players[cam];
                if (mp != null) {
                    try {
                        mp.setDisplay(holder);
                    } catch (Throwable ignored) {
                    }
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                holders[cam] = null;
                MediaPlayer mp = currentSingle ? null : players[cam];
                if (mp != null) {
                    try {
                        mp.setDisplay(null);
                    } catch (Throwable ignored) {
                    }
                }
            }
        });
        cell.addView(sv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView missing = ui.text(14, R.color.textSecondaryCool, false);
        missing.setText(R.string.camera360_no_signal);
        missing.setTextColor(Camera360Ui.COLOR_NO_SIGNAL);
        missing.setVisibility(View.GONE);
        cell.addView(missing, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        missingViews[cam] = missing;

        TextView label = ui.overlayPill(ui.camShortName(cam));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        lp.setMargins(ui.dp(8), ui.dp(8), 0, 0);
        cell.addView(label, lp);

        cell.setOnClickListener(v -> setFocus(focusCam == cam ? -1 : cam));
        return cell;
    }

    private View buildInfoRow() {
        LinearLayout row = new LinearLayout(ui.context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(ui.context);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        clipTitle = ui.text(17, R.color.textPrimary, true);
        clipTitle.setSingleLine(true);
        texts.addView(clipTitle);
        clipMeta = ui.text(12, R.color.textSecondaryCool, false);
        clipMeta.setSingleLine(true);
        texts.addView(clipMeta);

        deleteButton = actionButton(R.drawable.ic_mdi_delete, Camera360Ui.COLOR_REC);
        deleteButton.setText(R.string.camera360_delete);
        deleteButton.setOnClickListener(v -> {
            if (current >= 0 && current < clips.size()) {
                confirmDelete(Collections.singletonList(clips.get(current)));
            }
        });
        LinearLayout.LayoutParams xp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(44));
        xp.setMarginEnd(ui.dp(8));
        row.addView(deleteButton, xp);

        exportButton = actionButton(R.drawable.ic_mdi_usb, ui.themeColor(R.color.buttonPrimary));
        exportButton.setText(R.string.camera360_export);
        exportButton.setOnClickListener(v -> {
            if (current >= 0 && current < clips.size()) {
                startExport(Collections.singletonList(clips.get(current)), exportButton);
            }
        });
        row.addView(exportButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(44)));
        return row;
    }

    private View buildControls() {
        LinearLayout row = new LinearLayout(ui.context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        prevButton = ui.iconButton(R.drawable.ic_mdi_skip_previous, 44, v -> step(+1));
        row.addView(prevButton);
        playButton = ui.iconButton(R.drawable.ic_mdi_play, 52, v -> togglePlay());
        playButton.setColorFilter(Color.WHITE);
        ui.setPressableBackground(playButton, Camera360Ui.oval(ui.themeColor(R.color.buttonPrimary)), 26);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ui.dp(52), ui.dp(52));
        pp.setMargins(ui.dp(8), 0, ui.dp(8), 0);
        row.addView(playButton, pp);
        nextButton = ui.iconButton(R.drawable.ic_mdi_skip_next, 44, v -> step(-1));
        row.addView(nextButton);

        timeView = ui.text(13, R.color.textSecondaryCool, false);
        timeView.setTypeface(Typeface.MONOSPACE);
        timeView.setText("00:00 / 00:00");
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.setMarginStart(ui.dp(14));
        row.addView(timeView, tp);

        seekBar = new SeekBar(ui.context);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) updateTime(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                userSeeking = false;
                seekAll(bar.getProgress());
            }
        });
        row.addView(seekBar, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private View buildEmpty() {
        LinearLayout box = new LinearLayout(ui.context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        int accent = ui.themeColor(R.color.oemAccent);
        box.addView(ui.iconBadge(R.drawable.ic_mdi_play, accent, Camera360Ui.withAlpha(accent, 0x2A), 76, 40, true));
        TextView title = ui.text(20, R.color.textPrimary, true);
        title.setText(R.string.camera360_viewer_empty);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.topMargin = ui.dp(14);
        box.addView(title, tp);
        TextView sub = ui.text(14, R.color.textSecondaryCool, false);
        sub.setText(R.string.camera360_viewer_empty_sub);
        sub.setGravity(Gravity.CENTER);
        box.addView(sub);
        return box;
    }

    // ---------- Yasam dongusu ----------

    boolean isWide() {
        return wide;
    }

    void restoreWide(boolean on) {
        setWide(on);
    }

    void onShow() {
        visible = true;
        usbPresent = DvrController.findUsbRoot(ui.context) != null;
        updateExportButton();
        reloadClips(true);
        main.removeCallbacks(ticker);
        main.post(ticker);
    }

    void onHide() {
        visible = false;
        // Izleme ekranindan cikinca (geri/sekme degisimi) sol menu her zaman geri gelir.
        setWide(false);
        main.removeCallbacks(ticker);
        releasePlayers();
        playing = false;
        updatePlayButton();
    }

    // ---------- Klip listesi ----------

    private void reloadClips(boolean autoSelect) {
        dvr.loadClips(list -> {
            if (!visible) return;
            String keepBase = current >= 0 && current < clips.size() ? clips.get(current).base : null;
            clips = list;
            buildList();
            int idx = -1;
            if (keepBase != null) {
                for (int i = 0; i < clips.size(); i++) {
                    if (clips.get(i).base.equals(keepBase)) idx = i;
                }
            }
            if (idx >= 0) {
                current = idx;
                highlightRow();
            } else {
                current = -1;
                releasePlayers();
                if (!clips.isEmpty() && (autoSelect || keepBase != null)) {
                    loadClip(0, 0, false);
                } else {
                    updateInfo();
                }
            }
        });
    }

    private void buildList() {
        listColumn.removeAllViews();
        rowViews.clear();
        rowChecks.clear();
        Set<String> present = new HashSet<>();
        for (DvrClipStore.Clip c : clips) present.add(c.base);
        selected.retainAll(present);
        boolean empty = clips.isEmpty();
        if (empty && selecting) setSelecting(false);
        body.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        summaryPill.setText(ui.context.getString(R.string.camera360_viewer_summary,
                clips.size(), Camera360Ui.formatSize(DvrClipStore.totalBytes(clips))));

        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", locale);
        String lastDay = null;
        for (int i = 0; i < clips.size(); i++) {
            DvrClipStore.Clip c = clips.get(i);
            String day = dayLabel(c.timeMs);
            if (!day.equals(lastDay)) {
                TextView h = ui.text(11, R.color.textSecondaryCool, true);
                h.setText(day);
                h.setAllCaps(true);
                h.setLetterSpacing(0.08f);
                h.setPadding(ui.dp(8), ui.dp(lastDay == null ? 4 : 14), ui.dp(8), ui.dp(6));
                h.setOnClickListener(v -> {
                    if (selecting) toggleDay(day);
                });
                listColumn.addView(h);
                lastDay = day;
            }
            listColumn.addView(buildRow(i, c, timeFmt));
        }
        highlightRow();
        refreshSelection();
    }

    private View buildRow(int index, DvrClipStore.Clip c, SimpleDateFormat timeFmt) {
        LinearLayout row = new LinearLayout(ui.context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(12), ui.dp(10), ui.dp(10), ui.dp(10));
        row.setOnClickListener(v -> {
            if (selecting) toggleSelected(index);
            else loadClip(index, 0, true);
        });
        row.setOnLongClickListener(v -> {
            if (!selecting) setSelecting(true);
            toggleSelected(index);
            return true;
        });

        ImageView check = new ImageView(ui.context);
        int cpad = ui.dp(3);
        check.setPadding(cpad, cpad, cpad, cpad);
        check.setVisibility(selecting ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24));
        chp.setMarginEnd(ui.dp(10));
        row.addView(check, chp);
        rowChecks.add(check);
        applyCheck(check, selected.contains(c.base));

        LinearLayout texts = new LinearLayout(ui.context);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView time = ui.text(16, R.color.textPrimary, true);
        time.setText(timeFmt.format(new Date(c.timeMs)));
        texts.addView(time);
        TextView meta = ui.text(12, R.color.textSecondaryCool, false);
        meta.setText(metaText(c));
        meta.setSingleLine(true);
        texts.addView(meta);

        LinearLayout.LayoutParams p = ui.matchWrap();
        p.bottomMargin = ui.dp(4);
        row.setLayoutParams(p);
        rowViews.add(row);
        return row;
    }

    private void highlightRow() {
        int accent = ui.themeColor(R.color.oemAccent);
        for (int i = 0; i < rowViews.size(); i++) {
            View row = rowViews.get(i);
            if (i == current) {
                GradientDrawable bg = ui.roundRect(Camera360Ui.withAlpha(accent, 0x30), 14);
                bg.setStroke(ui.dp(1), Camera360Ui.withAlpha(accent, 0xAA));
                ui.setPressableBackground(row, bg, 14);
            } else {
                ui.setPressableBackground(row, ui.roundRect(ui.themeColor(R.color.surfaceCardInner), 14), 14);
            }
        }
        if (current >= 0 && current < rowViews.size()) {
            View row = rowViews.get(current);
            listScroll.post(() -> {
                int top = row.getTop() - ui.dp(36);
                int bottom = row.getBottom();
                int y = listScroll.getScrollY();
                if (top < y || bottom > y + listScroll.getHeight()) {
                    listScroll.smoothScrollTo(0, Math.max(0, top));
                }
            });
        }
    }

    private String dayLabel(long timeMs) {
        if (DateUtils.isToday(timeMs)) return ui.context.getString(R.string.camera360_today);
        Calendar y = Calendar.getInstance();
        y.add(Calendar.DAY_OF_YEAR, -1);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timeMs);
        if (y.get(Calendar.YEAR) == c.get(Calendar.YEAR) && y.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)) {
            return ui.context.getString(R.string.camera360_yesterday);
        }
        return DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(new Date(timeMs));
    }

    private String metaText(DvrClipStore.Clip c) {
        String size = Camera360Ui.formatSize(c.bytes);
        if (c.isSingle()) return ui.context.getString(R.string.camera360_viewer_single, size);
        int n = 0;
        for (File f : c.camFiles) if (f != null) n++;
        return ui.context.getString(R.string.camera360_viewer_angles, n, size);
    }

    // ---------- Coklu secim ----------

    private void setSelecting(boolean on) {
        selecting = on;
        if (!on) selected.clear();
        selectButton.setImageResource(on ? R.drawable.ic_mdi_close : R.drawable.ic_mdi_check);
        selectionBar.setVisibility(on ? View.VISIBLE : View.GONE);
        for (ImageView c : rowChecks) c.setVisibility(on ? View.VISIBLE : View.GONE);
        refreshSelection();
    }

    private void toggleSelected(int index) {
        if (index < 0 || index >= clips.size()) return;
        String base = clips.get(index).base;
        if (!selected.remove(base)) selected.add(base);
        refreshSelection();
    }

    private void toggleDay(String day) {
        List<String> bases = new ArrayList<>();
        for (DvrClipStore.Clip c : clips) {
            if (dayLabel(c.timeMs).equals(day)) bases.add(c.base);
        }
        if (selected.containsAll(bases)) selected.removeAll(bases);
        else selected.addAll(bases);
        refreshSelection();
    }

    private void toggleSelectAll() {
        if (!clips.isEmpty() && selected.size() == clips.size()) {
            selected.clear();
        } else {
            for (DvrClipStore.Clip c : clips) selected.add(c.base);
        }
        refreshSelection();
    }

    private List<DvrClipStore.Clip> selectedClips() {
        List<DvrClipStore.Clip> out = new ArrayList<>();
        for (DvrClipStore.Clip c : clips) {
            if (selected.contains(c.base)) out.add(c);
        }
        return out;
    }

    private void applyCheck(ImageView v, boolean on) {
        GradientDrawable bg = Camera360Ui.oval(on ? ui.themeColor(R.color.oemAccent) : Color.TRANSPARENT);
        if (!on) bg.setStroke(ui.dp(2), ui.themeColor(R.color.textSecondaryCool));
        v.setBackground(bg);
        if (on) {
            v.setImageResource(R.drawable.ic_mdi_check);
            v.setColorFilter(Color.WHITE);
        } else {
            v.setImageDrawable(null);
        }
    }

    private void refreshSelection() {
        if (selectionBar == null) return;
        for (int i = 0; i < rowChecks.size() && i < clips.size(); i++) {
            applyCheck(rowChecks.get(i), selected.contains(clips.get(i).base));
        }
        if (!selecting) return;
        List<DvrClipStore.Clip> sel = selectedClips();
        int n = sel.size();
        selectionText.setText(n == 0
                ? ui.context.getString(R.string.camera360_viewer_select_hint)
                : ui.context.getString(R.string.camera360_viewer_selected, n,
                Camera360Ui.formatSize(DvrClipStore.totalBytes(sel))));
        selectAllButton.setText(!clips.isEmpty() && n == clips.size()
                ? R.string.camera360_viewer_select_none : R.string.camera360_viewer_select_all);
        if (!(exporting && exportingButton == selExportButton)) {
            selExportButton.setText(ui.context.getString(R.string.camera360_export_n, n));
            selExportButton.setAlpha(n > 0 && usbPresent && !exporting ? 1f : 0.5f);
        }
        selDeleteButton.setText(ui.context.getString(R.string.camera360_delete_n, n));
        selDeleteButton.setAlpha(n > 0 && !exporting ? 1f : 0.5f);
    }

    // ---------- Silme ----------

    private void confirmDelete(List<DvrClipStore.Clip> targets) {
        if (targets.isEmpty() || exporting) return;
        new AlertDialog.Builder(ui.context)
                .setTitle(R.string.camera360_delete_title)
                .setMessage(ui.context.getString(R.string.camera360_delete_msg, targets.size(),
                        Camera360Ui.formatSize(DvrClipStore.totalBytes(targets))))
                .setPositiveButton(R.string.camera360_delete, (d, w) -> deleteClips(targets))
                .setNegativeButton(R.string.camera360_cancel, null)
                .show();
    }

    private void deleteClips(List<DvrClipStore.Clip> targets) {
        boolean currentGone = false;
        if (current >= 0 && current < clips.size()) {
            String base = clips.get(current).base;
            for (DvrClipStore.Clip t : targets) {
                if (t.base.equals(base)) currentGone = true;
            }
        }
        if (currentGone) {
            // Oynatici dosyayi acik tutmasin.
            releasePlayers();
            playing = false;
            current = -1;
            updateInfo();
        }
        final boolean reselect = currentGone;
        dvr.deleteClips(targets, n -> {
            for (DvrClipStore.Clip t : targets) selected.remove(t.base);
            Toast.makeText(ui.context, ui.context.getString(R.string.camera360_deleted, n),
                    Toast.LENGTH_SHORT).show();
            if (visible) reloadClips(reselect);
        });
    }

    // ---------- Oynatma ----------

    private void loadClip(int index, int startMs, boolean play) {
        releasePlayers();
        if (index < 0 || index >= clips.size()) {
            current = -1;
            updateInfo();
            return;
        }
        current = index;
        DvrClipStore.Clip clip = clips.get(index);
        final int token = loadToken;
        playing = play;
        startOnReadyMs = Math.max(0, startMs);
        resumeMs = startOnReadyMs;
        durationMs = 0;
        currentSingle = clip.isSingle();
        gridView.setVisibility(currentSingle ? View.GONE : View.VISIBLE);
        mosaicView.setVisibility(currentSingle ? View.VISIBLE : View.GONE);
        applyMosaicZoom();
        if (currentSingle) {
            openPlayer(clip.mosaic, token, 0);
        } else {
            for (int cam = 0; cam < CAM_COUNT; cam++) {
                File f = clip.camFiles[cam];
                missingViews[cam].setVisibility(f == null ? View.VISIBLE : View.GONE);
                if (f == null || !isCellShown(cam)) continue;
                if (!openPlayer(f, token, cam)) missingViews[cam].setVisibility(View.VISIBLE);
            }
        }
        if (activePlayers == 0) {
            playing = false;
            Toast.makeText(ui.context, R.string.camera360_viewer_play_error, Toast.LENGTH_SHORT).show();
        }
        highlightRow();
        updateInfo();
    }

    /** players[cam]'i acar; tek dosya kayitta goruntu mozaik yuzeyine gider. */
    private boolean openPlayer(File f, int token, int cam) {
        MediaPlayer mp = new MediaPlayer();
        try {
            // Medya sunucusu uygulama dizinlerini (SELinux/FUSE) okuyamayabilir; veri uygulama surecinden okunur.
            mp.setDataSource(new FileSource(f));
            mp.setVolume(0f, 0f);
            mp.setOnPreparedListener(p -> onPrepared(token, cam));
            mp.setOnCompletionListener(p -> onCompleted(token));
            mp.setOnErrorListener((p, what, extra) -> {
                onPlayError(token, cam);
                return true;
            });
            if (currentSingle) {
                if (mosaicSurface != null) mp.setSurface(mosaicSurface);
            } else if (holders[cam] != null) {
                mp.setDisplay(holders[cam]);
            }
            mp.prepareAsync();
        } catch (Throwable t) {
            try {
                mp.release();
            } catch (Throwable ignored) {
            }
            return false;
        }
        players[cam] = mp;
        pendingPrepare++;
        activePlayers++;
        return true;
    }

    private void onPrepared(int token, int cam) {
        if (token != loadToken) return;
        MediaPlayer mp = players[cam];
        if (mp == null) return;
        prepared[cam] = true;
        durationMs = Math.max(durationMs, mp.getDuration());
        pendingPrepare--;
        if (pendingPrepare > 0) return;
        seekBar.setMax(Math.max(1, durationMs));
        if (startOnReadyMs > 0) seekAll(Math.min(startOnReadyMs, durationMs));
        else seekAll(0);
        if (playing) startAll();
        updatePlayButton();
        updateTime(startOnReadyMs);
    }

    private void onCompleted(int token) {
        if (token != loadToken) return;
        completedPlayers++;
        if (completedPlayers < countPrepared()) return;
        if (current > 0) {
            loadClip(current - 1, 0, true);
        } else {
            playing = false;
            updatePlayButton();
            // Kayit suruyorsa yeni klip olusmus olabilir.
            reloadClips(false);
        }
    }

    private void onPlayError(int token, int cam) {
        if (token != loadToken) return;
        if (!playing && pendingPrepare == 0) {
            // Duraklatilmis cozucu zaman asimina dusebilir; oynatinca kaldigi yerden yeniden yuklenir.
            releasePlayers();
            updateTime(resumeMs);
            return;
        }
        MediaPlayer mp = players[cam];
        if (mp != null) {
            try {
                mp.release();
            } catch (Throwable ignored) {
            }
        }
        players[cam] = null;
        if (!prepared[cam]) pendingPrepare = Math.max(0, pendingPrepare - 1);
        prepared[cam] = false;
        activePlayers = Math.max(0, activePlayers - 1);
        missingViews[cam].setVisibility(View.VISIBLE);
        if (activePlayers == 0) {
            playing = false;
            updatePlayButton();
            Toast.makeText(ui.context, R.string.camera360_viewer_play_error, Toast.LENGTH_SHORT).show();
            reloadClips(false);
        } else if (pendingPrepare == 0 && durationMs > 0 && playing) {
            startAll();
        }
    }

    private void releasePlayers() {
        loadToken++;
        for (int i = 0; i < CAM_COUNT; i++) {
            MediaPlayer mp = players[i];
            players[i] = null;
            prepared[i] = false;
            if (mp != null) {
                try {
                    mp.release();
                } catch (Throwable ignored) {
                }
            }
        }
        pendingPrepare = 0;
        activePlayers = 0;
        completedPlayers = 0;
    }

    private int countPrepared() {
        int n = 0;
        for (boolean p : prepared) if (p) n++;
        return n;
    }

    private MediaPlayer master() {
        MediaPlayer best = null;
        int bestDur = -1;
        for (int i = 0; i < CAM_COUNT; i++) {
            if (prepared[i] && players[i] != null) {
                int d = players[i].getDuration();
                if (d > bestDur) {
                    bestDur = d;
                    best = players[i];
                }
            }
        }
        return best;
    }

    private void startAll() {
        completedPlayers = 0;
        for (int i = 0; i < CAM_COUNT; i++) {
            if (prepared[i] && players[i] != null && !players[i].isPlaying()) {
                players[i].start();
            }
        }
    }

    private void pauseAll() {
        MediaPlayer m = master();
        if (m != null) resumeMs = m.getCurrentPosition();
        for (int i = 0; i < CAM_COUNT; i++) {
            if (prepared[i] && players[i] != null && players[i].isPlaying()) {
                players[i].pause();
            }
        }
    }

    private void seekAll(int ms) {
        completedPlayers = 0;
        resumeMs = ms;
        for (int i = 0; i < CAM_COUNT; i++) {
            if (prepared[i] && players[i] != null) {
                players[i].seekTo(Math.min(ms, players[i].getDuration()), MediaPlayer.SEEK_CLOSEST);
            }
        }
        updateTime(ms);
    }

    private void togglePlay() {
        if (current < 0) {
            if (!clips.isEmpty()) loadClip(0, 0, true);
            return;
        }
        if (activePlayers == 0) {
            loadClip(current, resumeMs >= durationMs - 200 ? 0 : resumeMs, true);
            return;
        }
        if (pendingPrepare > 0) {
            playing = !playing;
            updatePlayButton();
            return;
        }
        if (playing) {
            playing = false;
            pauseAll();
        } else {
            playing = true;
            MediaPlayer m = master();
            if (m != null && m.getCurrentPosition() >= durationMs - 200) seekAll(0);
            startAll();
        }
        updatePlayButton();
    }

    /** delta=-1 zamanca sonraki (daha yeni), +1 onceki (daha eski) klip. */
    private void step(int delta) {
        int target = current + delta;
        if (target < 0 || target >= clips.size()) return;
        loadClip(target, 0, playing || current < 0);
    }

    private boolean isCellShown(int cam) {
        return focusCam < 0 || focusCam == cam;
    }

    private void setFocus(int cam) {
        focusCam = cam;
        angleChoice.setSelected(cam + 1);
        for (int i = 0; i < CAM_COUNT; i++) {
            cells[i].setVisibility(isCellShown(i) ? View.VISIBLE : View.GONE);
        }
        gridRow0.setVisibility(cam < 0 || cam < 2 ? View.VISIBLE : View.GONE);
        gridRow1.setVisibility(cam < 0 || cam >= 2 ? View.VISIBLE : View.GONE);
        applyMosaicZoom();
        if (current >= 0 && !currentSingle) {
            MediaPlayer m = master();
            int pos = m != null ? m.getCurrentPosition() : resumeMs;
            loadClip(current, pos, playing);
        }
    }

    // ---------- Periyodik guncelleme ----------

    private void onTick() {
        tickCount++;
        MediaPlayer m = master();
        if (m != null && pendingPrepare == 0 && !userSeeking) {
            int pos = m.getCurrentPosition();
            if (playing) resumeMs = pos;
            seekBar.setProgress(pos);
            updateTime(pos);
            if (playing && tickCount % 8 == 0) {
                for (int i = 0; i < CAM_COUNT; i++) {
                    MediaPlayer p = players[i];
                    if (p == null || p == m || !prepared[i] || !p.isPlaying()) continue;
                    if (Math.abs(p.getCurrentPosition() - pos) > DRIFT_MS) {
                        p.seekTo(pos, MediaPlayer.SEEK_CLOSEST);
                    }
                }
            }
        }
        if (tickCount % 12 == 0) {
            boolean usb = DvrController.findUsbRoot(ui.context) != null;
            if (usb != usbPresent) {
                usbPresent = usb;
                updateExportButton();
            }
        }
    }

    private void updateTime(int posMs) {
        timeView.setText(Camera360Ui.formatElapsed(posMs) + " / " + Camera360Ui.formatElapsed(durationMs));
    }

    private void updatePlayButton() {
        playButton.setImageResource(playing ? R.drawable.ic_mdi_pause : R.drawable.ic_mdi_play);
    }

    private void updateInfo() {
        boolean has = current >= 0 && current < clips.size();
        prevButton.setAlpha(has && current < clips.size() - 1 ? 1f : 0.4f);
        nextButton.setAlpha(has && current > 0 ? 1f : 0.4f);
        updatePlayButton();
        updateExportButton();
        if (!has) {
            clipTitle.setText("");
            clipMeta.setText("");
            seekBar.setProgress(0);
            durationMs = 0;
            updateTime(0);
            return;
        }
        DvrClipStore.Clip c = clips.get(current);
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", locale);
        clipTitle.setText(dayLabel(c.timeMs) + " · " + timeFmt.format(new Date(c.timeMs)));
        clipMeta.setText(metaText(c));
        seekBar.setProgress(0);
        updateTime(0);
    }

    // ---------- USB'ye aktarma ----------

    private void updateExportButton() {
        if (exportButton == null) return;
        boolean has = current >= 0 && current < clips.size();
        deleteButton.setAlpha(has && !exporting ? 1f : 0.5f);
        refreshSelection();
        if (exporting) {
            if (exportingButton != exportButton) exportButton.setAlpha(0.5f);
            return;
        }
        exportButton.setAlpha(usbPresent && has ? 1f : 0.5f);
        exportButton.setText(R.string.camera360_export);
    }

    private void startExport(List<DvrClipStore.Clip> targets, Button button) {
        if (exporting || targets.isEmpty()) return;
        if (!usbPresent) {
            usbPresent = DvrController.findUsbRoot(ui.context) != null;
            if (!usbPresent) {
                Toast.makeText(ui.context, R.string.camera360_export_no_usb, Toast.LENGTH_SHORT).show();
                updateExportButton();
                return;
            }
        }
        exporting = true;
        exportingButton = button;
        updateExportButton();
        button.setAlpha(0.7f);
        button.setText(ui.context.getString(R.string.camera360_export_progress, 0));
        dvr.exportClips(targets, new DvrController.ExportCallback() {
            @Override
            public void onProgress(int percent) {
                if (exporting) {
                    button.setText(ui.context.getString(R.string.camera360_export_progress, percent));
                }
            }

            @Override
            public void onDone(File targetDir, String error) {
                exporting = false;
                exportingButton = null;
                Context ctx = ui.context;
                if (error == null) {
                    Toast.makeText(ctx, ctx.getString(R.string.camera360_export_done,
                            targetDir.getAbsolutePath()), Toast.LENGTH_LONG).show();
                } else if ("USB".equals(error)) {
                    usbPresent = false;
                    Toast.makeText(ctx, R.string.camera360_export_no_usb, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ctx, ctx.getString(R.string.camera360_export_fail, error),
                            Toast.LENGTH_LONG).show();
                }
                updateExportButton();
            }
        });
    }

    private static final class FileSource extends MediaDataSource {
        private final RandomAccessFile raf;

        FileSource(File file) throws IOException {
            raf = new RandomAccessFile(file, "r");
        }

        @Override
        public synchronized int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
            long length = raf.length();
            if (position >= length) return -1;
            raf.seek(position);
            return raf.read(buffer, offset, (int) Math.min(size, length - position));
        }

        @Override
        public synchronized long getSize() throws IOException {
            return raf.length();
        }

        @Override
        public synchronized void close() throws IOException {
            raf.close();
        }
    }

    /** Verilen en/boy oranini koruyarak ebeveynine sigan kutu. */
    private static final class RatioFrame extends FrameLayout {
        private final float ratio;

        RatioFrame(Context context, float ratio) {
            super(context);
            this.ratio = ratio;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            int h = MeasureSpec.getSize(heightMeasureSpec);
            if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED || h == 0) {
                h = Math.round(w / ratio);
            }
            if (w / ratio > h) {
                w = Math.round(h * ratio);
            } else {
                h = Math.round(w / ratio);
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
    }
}
