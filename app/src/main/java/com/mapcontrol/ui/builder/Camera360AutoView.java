package com.mapcontrol.ui.builder;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mapcontrol.R;
import com.mapcontrol.dvr.DvrController;
import com.mapcontrol.ui.theme.UiStyles;

/**
 * "Arac Ici Kayit" ekrani: Kalite/Performans modlu (3 dk klip) otomatik kayit,
 * id sirali saklama aciklamasi, 2 GB rezervli depolama dagilimi ve canli durum.
 * Kayit mantigi {@link DvrController}'dadir; bu sinif yalnizca gosterir/ayarlar.
 */
final class Camera360AutoView {
    private final Camera360Ui ui;
    private final DvrController dvr;
    private final Runnable onBack;
    private final Runnable openViewer;

    private ScrollView root;
    private TextView storageState;
    private GradientDrawable storageStateDot;
    private TextView pathView;
    private View barClips;
    private View barOther;
    private View barReserve;
    private View barFree;
    private TextView budgetValue;
    private TextView clipsValue;
    private TextView capacityValue;
    private TextView freeValue;

    private UiStyles.BinarySegmentHandle enableSegment;
    private LinearLayout enableColumn;
    private TextView blockedNote;
    private Camera360Ui.Choice modeChoice;
    private TextView modeDesc;
    private TextView retentionNote;
    private TextView statusText;
    private GradientDrawable statusDot;
    private TextView recLine;
    private View pulseDot;
    private ObjectAnimator pulse;
    private Button actionButton;
    private boolean visible;

    Camera360AutoView(Camera360Ui ui, DvrController dvr, Runnable onBack, Runnable openViewer) {
        this.ui = ui;
        this.dvr = dvr;
        this.onBack = onBack;
        this.openViewer = openViewer;
    }

    View build() {
        boolean wide = ui.isWide();
        root = new ScrollView(ui.context);
        root.setFillViewport(true);
        root.setVerticalScrollBarEnabled(false);
        LinearLayout page = new LinearLayout(ui.context);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, 0, 0, ui.dp(12));
        root.addView(page, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout header = ui.header(R.string.camera360_menu_auto);
        LinearLayout.LayoutParams hp = ui.matchWrap();
        hp.bottomMargin = ui.dp(12);
        page.addView(header, hp);

        LinearLayout body = new LinearLayout(ui.context);
        body.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        page.addView(body, ui.matchWrap());
        LinearLayout left = new LinearLayout(ui.context);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout right = new LinearLayout(ui.context);
        right.setOrientation(LinearLayout.VERTICAL);
        if (wide) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginEnd(ui.dp(6));
            body.addView(left, lp);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            rp.setMarginStart(ui.dp(6));
            body.addView(right, rp);
        } else {
            body.addView(left, ui.matchWrap());
            body.addView(right, ui.matchWrapTop(12));
        }

        left.addView(buildStorageCard(), ui.matchWrap());
        left.addView(buildStatusCard(), ui.matchWrapTop(12));
        right.addView(buildSettingsCard(), ui.matchWrap());
        refresh();
        return root;
    }

    // ---------- Depolama karti ----------

    private LinearLayout buildStorageCard() {
        LinearLayout card = ui.card();
        LinearLayout head = new LinearLayout(ui.context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(head);
        head.addView(ui.iconBadge(R.drawable.ic_mdi_folder, ui.themeColor(R.color.oemAccent),
                ui.themeColor(R.color.surfaceCardInner), 44, 26, false));
        LinearLayout titles = new LinearLayout(ui.context);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMarginStart(ui.dp(12));
        tp.setMarginEnd(ui.dp(8));
        head.addView(titles, tp);
        TextView title = ui.text(17, R.color.textPrimary, true);
        title.setText(R.string.camera360_auto_storage_title);
        titles.addView(title);
        pathView = ui.text(12, R.color.textSecondary, false);
        pathView.setText(dvr.getInternalDir().getAbsolutePath());
        pathView.setSingleLine(true);
        pathView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titles.addView(pathView);
        storageState = ui.statusPill();
        storageStateDot = ui.attachDot(storageState, Camera360Ui.COLOR_IDLE, 8);
        head.addView(storageState);

        // Bos alan dagilimi: klipler | diger | rezerv | kayda acik
        LinearLayout bar = new LinearLayout(ui.context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(ui.roundRect(ui.themeColor(R.color.outline), 6));
        bar.setClipToOutline(true);
        card.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(12)) {{
            topMargin = ui.dp(14);
        }});
        int accent = ui.themeColor(R.color.oemAccent);
        int other = ui.themeColor(R.color.textMuted);
        barClips = barSegment(bar, accent);
        barOther = barSegment(bar, Camera360Ui.withAlpha(other, 0x99));
        barReserve = barSegment(bar, Camera360Ui.withAlpha(Camera360Ui.COLOR_WAIT, 0xCC));
        barFree = barSegment(bar, Camera360Ui.withAlpha(Camera360Ui.COLOR_LIVE, 0x99));

        LinearLayout legend = new LinearLayout(ui.context);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(legend, ui.matchWrapTop(8));
        legendItem(legend, accent, R.string.camera360_auto_legend_clips);
        legendItem(legend, Camera360Ui.withAlpha(other, 0x99), R.string.camera360_auto_legend_other);
        legendItem(legend, Camera360Ui.COLOR_WAIT, R.string.camera360_auto_legend_reserve);
        legendItem(legend, Camera360Ui.COLOR_LIVE, R.string.camera360_auto_legend_free);

        LinearLayout stats = new LinearLayout(ui.context);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sp = ui.matchWrapTop(12);
        sp.leftMargin = ui.dp(-4);
        sp.rightMargin = ui.dp(-4);
        card.addView(stats, sp);
        budgetValue = stat(stats, R.string.camera360_auto_budget);
        clipsValue = stat(stats, R.string.camera360_auto_legend_clips);
        capacityValue = stat(stats, R.string.camera360_auto_capacity);
        freeValue = stat(stats, R.string.camera360_free);

        TextView note = ui.text(12, R.color.textSecondaryCool, false);
        note.setText(R.string.camera360_auto_reserve_note);
        card.addView(note, ui.matchWrapTop(10));
        return card;
    }

    private View barSegment(LinearLayout bar, int color) {
        View v = new View(ui.context);
        v.setBackgroundColor(color);
        bar.addView(v, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0f));
        return v;
    }

    private static void setWeight(View v, float weight) {
        LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) v.getLayoutParams();
        p.weight = Math.max(0f, weight);
        v.setLayoutParams(p);
    }

    private void legendItem(LinearLayout parent, int color, int labelRes) {
        TextView tv = ui.text(11, R.color.textSecondaryCool, false);
        tv.setText(labelRes);
        tv.setSingleLine(true);
        ui.attachDot(tv, color, 8);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMarginEnd(ui.dp(14));
        parent.addView(tv, p);
    }

    private TextView stat(LinearLayout parent, int labelRes) {
        LinearLayout box = new LinearLayout(ui.context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(ui.dp(6), ui.dp(10), ui.dp(6), ui.dp(10));
        box.setBackground(ui.roundRect(ui.themeColor(R.color.surfaceCardInner), 14));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bp.setMargins(ui.dp(4), 0, ui.dp(4), 0);
        parent.addView(box, bp);
        TextView value = ui.text(18, R.color.textPrimary, true);
        value.setText("-");
        value.setSingleLine(true);
        value.setGravity(Gravity.CENTER);
        box.addView(value);
        TextView label = ui.text(11, R.color.textSecondaryCool, false);
        label.setText(labelRes);
        label.setAllCaps(true);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        box.addView(label);
        return value;
    }

    // ---------- Durum karti ----------

    private LinearLayout buildStatusCard() {
        LinearLayout card = ui.card();
        LinearLayout row = new LinearLayout(ui.context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row);

        pulseDot = new View(ui.context);
        GradientDrawable dot = Camera360Ui.oval(Camera360Ui.COLOR_IDLE);
        pulseDot.setBackground(dot);
        statusDot = dot;
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(ui.dp(12), ui.dp(12));
        dp.setMarginEnd(ui.dp(10));
        row.addView(pulseDot, dp);

        LinearLayout texts = new LinearLayout(ui.context);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        statusText = ui.text(17, R.color.textPrimary, true);
        texts.addView(statusText);
        recLine = ui.text(13, R.color.textSecondaryCool, false);
        recLine.setTypeface(Typeface.MONOSPACE);
        recLine.setVisibility(View.GONE);
        texts.addView(recLine);

        actionButton = new Button(ui.context);
        actionButton.setAllCaps(false);
        actionButton.setTextSize(14);
        actionButton.setTextColor(ui.themeColor(R.color.textPrimary));
        UiStyles.styleOemButton(actionButton, ui.themeColor(R.color.buttonSecondaryMuted));
        actionButton.setStateListAnimator(null);
        actionButton.setPadding(ui.dp(16), 0, ui.dp(16), 0);
        actionButton.setOnClickListener(v -> {
            if (dvr.isRecordingInternal()) {
                dvr.stopRecording(true);
            } else if (dvr.isSuspended()) {
                dvr.resumeAuto();
            }
            refresh();
        });
        row.addView(actionButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(44)));

        Button viewer = new Button(ui.context);
        viewer.setText(R.string.camera360_auto_open_viewer);
        viewer.setAllCaps(false);
        viewer.setTextSize(15);
        viewer.setTypeface(null, Typeface.BOLD);
        viewer.setTextColor(0xFFFFFFFF);
        UiStyles.styleOemButton(viewer, ui.themeColor(R.color.buttonPrimary));
        UiStyles.setButtonStartIconTinted(viewer, R.drawable.ic_mdi_play, 0xFFFFFFFF, ui.dp(8));
        viewer.setStateListAnimator(null);
        viewer.setOnClickListener(v -> openViewer.run());
        card.addView(viewer, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(52)) {{
            topMargin = ui.dp(16);
        }});
        return card;
    }

    // ---------- Ayar karti ----------

    private LinearLayout buildSettingsCard() {
        LinearLayout card = ui.card();
        card.setPadding(ui.dp(2), ui.dp(16), ui.dp(2), ui.dp(16));
        int inset = UiStyles.dimenPx(ui.context, R.dimen.spacing_medium);
        TextView label = ui.sectionLabel(R.string.camera360_auto_title);
        label.setPadding(inset, 0, inset, 0);
        card.addView(label);

        enableColumn = new LinearLayout(ui.context);
        enableColumn.setOrientation(LinearLayout.VERTICAL);
        card.addView(enableColumn, ui.matchWrap());
        enableSegment = UiStyles.addBinarySegmentedControl(ui.context, enableColumn,
                ui.context.getString(R.string.camera360_auto_toggle),
                ui.context.getString(R.string.common_yes), ui.context.getString(R.string.common_no),
                "", "", dvr.isAutoInternalEnabled(), this::onToggle);
        hideLastChild(enableColumn);

        blockedNote = ui.text(13, R.color.textSecondaryCool, false);
        blockedNote.setText(R.string.camera360_auto_blocked_usb);
        blockedNote.setTextColor(Camera360Ui.COLOR_WAIT);
        blockedNote.setVisibility(View.GONE);
        LinearLayout.LayoutParams bp = ui.matchWrapTop(8);
        bp.leftMargin = inset;
        bp.rightMargin = inset;
        card.addView(blockedNote, bp);

        TextView modeTitle = ui.text(16, R.color.textPrimary, true);
        modeTitle.setText(R.string.camera360_auto_mode);
        modeTitle.setPadding(inset, ui.dp(16), inset, ui.dp(8));
        card.addView(modeTitle);
        modeChoice = ui.choice(new String[]{
                ui.context.getString(R.string.camera360_auto_mode_quality),
                ui.context.getString(R.string.camera360_auto_mode_perf)},
                dvr.getInternalMode().ordinal(), this::onModePicked);
        LinearLayout.LayoutParams mp = ui.matchWrap();
        mp.leftMargin = inset;
        mp.rightMargin = inset;
        card.addView(modeChoice.view, mp);

        modeDesc = ui.text(13, R.color.textSecondaryCool, false);
        modeDesc.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
        modeDesc.setBackground(ui.roundRect(ui.themeColor(R.color.surfaceCardInner), 12));
        LinearLayout.LayoutParams fp = ui.matchWrapTop(10);
        fp.leftMargin = inset;
        fp.rightMargin = inset;
        card.addView(modeDesc, fp);

        TextView retTitle = ui.text(16, R.color.textPrimary, true);
        retTitle.setText(R.string.camera360_auto_retention);
        retTitle.setPadding(inset, ui.dp(16), inset, ui.dp(8));
        card.addView(retTitle);

        // Tarihe gore silme yok: kural sadece aciklama olarak gosterilir.
        retentionNote = ui.text(13, R.color.textSecondaryCool, false);
        retentionNote.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
        retentionNote.setBackground(ui.roundRect(ui.themeColor(R.color.surfaceCardInner), 12));
        LinearLayout.LayoutParams np = ui.matchWrap();
        np.leftMargin = inset;
        np.rightMargin = inset;
        card.addView(retentionNote, np);
        return card;
    }

    private static void hideLastChild(LinearLayout column) {
        if (column.getChildCount() > 0) {
            column.getChildAt(column.getChildCount() - 1).setVisibility(View.GONE);
        }
    }

    private void onToggle(boolean enabled) {
        if (!enabled) {
            dvr.setAutoInternalEnabled(false);
            refresh();
            return;
        }
        if (!dvr.canEnableAutoInternal()) {
            enableSegment.syncVisualWithoutCommit(false);
            refresh();
            return;
        }
        // Silme kurallarina acik onay alinmadan mod acilmaz.
        new AlertDialog.Builder(ui.context)
                .setTitle(R.string.camera360_auto_consent_title)
                .setMessage(ui.context.getString(R.string.camera360_auto_consent_msg,
                        dvr.getInternalDir().getAbsolutePath()))
                .setPositiveButton(R.string.camera360_auto_consent_ok, (d, w) -> {
                    if (!dvr.setAutoInternalEnabled(true)) {
                        enableSegment.syncVisualWithoutCommit(false);
                    }
                    refresh();
                })
                .setNegativeButton(R.string.camera360_cancel, (d, w) -> {
                    enableSegment.syncVisualWithoutCommit(false);
                    refresh();
                })
                .setOnCancelListener(d -> {
                    enableSegment.syncVisualWithoutCommit(false);
                    refresh();
                })
                .show();
    }

    private void onModePicked(int index) {
        dvr.setInternalMode(DvrController.InternalMode.values()[index]);
        refresh();
    }

    // ---------- Durum guncelleme ----------

    void onShow() {
        visible = true;
        dvr.refreshStorageAsync();
        refresh();
    }

    void onHide() {
        visible = false;
        updatePulse(false);
    }

    /** Saniyelik guncelleme (kayit suresi/boyutu). */
    void tick() {
        if (visible && dvr.isRecordingInternal()) {
            updateRecLine();
        }
    }

    void refresh() {
        if (root == null) return;
        boolean enabled = dvr.isAutoInternalEnabled();
        boolean blocked = !enabled && dvr.isAutoUsbEnabled();
        enableSegment.syncVisualWithoutCommit(enabled);
        enableSegment.setInteractionEnabled(!blocked);
        enableColumn.setAlpha(blocked ? 0.5f : 1f);
        blockedNote.setVisibility(blocked ? View.VISIBLE : View.GONE);
        DvrController.InternalMode mode = dvr.getInternalMode();
        modeChoice.setSelected(mode.ordinal());
        modeDesc.setText(ui.context.getString(mode.separate
                        ? R.string.camera360_auto_mode_quality_desc : R.string.camera360_auto_mode_perf_desc,
                Camera360Ui.formatSize(mode.bytesPerMinute())));

        DvrController.StorageInfo s = dvr.getStorage();
        long total = Math.max(1, s.totalBytes);
        long free = Math.max(0, s.freeBytes);
        long reserve = Math.min(free, DvrController.RESERVE_BYTES);
        long otherUsed = Math.max(0, total - free - s.clipsBytes);
        setWeight(barClips, s.clipsBytes / (float) total);
        setWeight(barOther, otherUsed / (float) total);
        setWeight(barReserve, reserve / (float) total);
        setWeight(barFree, (free - reserve) / (float) total);
        budgetValue.setText(Camera360Ui.formatSize(s.recordBudgetBytes()));
        clipsValue.setText(s.clipCount + " · " + Camera360Ui.formatSize(s.clipsBytes));
        capacityValue.setText(ui.formatDuration(s.capacityMinutes()));
        freeValue.setText(s.freeBytes >= 0 ? Camera360Ui.formatSize(s.freeBytes) : "-");
        retentionNote.setText(ui.context.getString(R.string.camera360_auto_retention_note,
                ui.formatDuration(s.capacityMinutes())));

        boolean lowSpace = s.freeBytes >= 0
                && s.freeBytes + s.clipsBytes < DvrController.RESERVE_BYTES + 10 * dvr.autoBytesPerMinute();
        if (!s.writable && dvr.getAutoStatus() == DvrController.AutoStatus.NOT_WRITABLE) {
            storageState.setText(R.string.camera360_auto_st_not_writable);
            ui.tintPill(storageState, storageStateDot, Camera360Ui.COLOR_REC);
        } else if (lowSpace) {
            storageState.setText(R.string.camera360_auto_st_low_space);
            ui.tintPill(storageState, storageStateDot, Camera360Ui.COLOR_REC);
        } else {
            storageState.setText(R.string.camera360_auto_ready);
            ui.tintPill(storageState, storageStateDot, Camera360Ui.COLOR_LIVE);
        }

        DvrController.AutoStatus st = enabled ? dvr.getAutoStatus() : DvrController.AutoStatus.OFF;
        statusText.setText(statusLabel(ui, dvr, st));
        statusDot.setColor(statusColor(st));
        boolean recording = enabled && dvr.isRecordingInternal();
        recLine.setVisibility(recording ? View.VISIBLE : View.GONE);
        if (recording) updateRecLine();
        updatePulse(recording && visible);
        if (recording) {
            actionButton.setVisibility(View.VISIBLE);
            actionButton.setText(R.string.camera360_auto_pause);
        } else if (enabled && dvr.isSuspended()) {
            actionButton.setVisibility(View.VISIBLE);
            actionButton.setText(R.string.camera360_auto_resume);
        } else {
            actionButton.setVisibility(View.GONE);
        }
    }

    private void updateRecLine() {
        recLine.setText(ui.context.getString(R.string.camera360_auto_recording_line,
                Camera360Ui.formatElapsed(dvr.recorder().getElapsedMs()),
                Camera360Ui.formatSize(dvr.recorder().getTotalBytes())));
    }

    private void updatePulse(boolean on) {
        if (on && pulse == null) {
            pulse = ObjectAnimator.ofFloat(pulseDot, View.ALPHA, 1f, 0.2f);
            pulse.setDuration(700);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.start();
        } else if (!on && pulse != null) {
            pulse.cancel();
            pulse = null;
            pulseDot.setAlpha(1f);
        }
    }

    static int statusColor(DvrController.AutoStatus st) {
        switch (st) {
            case RECORDING: return Camera360Ui.COLOR_REC;
            case WAITING_IGNITION:
            case STARTING:
            case NO_USB:
            case SUSPENDED: return Camera360Ui.COLOR_WAIT;
            case OFF: return Camera360Ui.COLOR_IDLE;
            default: return Camera360Ui.COLOR_REC;
        }
    }

    static String statusLabel(Camera360Ui ui, DvrController dvr, DvrController.AutoStatus st) {
        switch (st) {
            case WAITING_IGNITION: return ui.context.getString(R.string.camera360_auto_st_waiting);
            case STARTING: return ui.context.getString(R.string.camera360_auto_st_starting);
            case RECORDING: return ui.context.getString(R.string.camera360_auto_st_recording);
            case SUSPENDED: return ui.context.getString(R.string.camera360_auto_st_suspended);
            case NO_CAMERA: return ui.context.getString(R.string.camera360_auto_st_no_camera);
            case NOT_WRITABLE: return ui.context.getString(R.string.camera360_auto_st_not_writable);
            case LOW_SPACE: return ui.context.getString(R.string.camera360_auto_st_low_space);
            case NO_USB: return ui.context.getString(R.string.camera360_auto_st_no_usb);
            case USB_FULL: return ui.context.getString(R.string.camera360_auto_st_usb_full);
            case ERROR: {
                String e = dvr.getLastError();
                return ui.context.getString(R.string.camera360_auto_st_error, e != null ? e : "-");
            }
            default: return ui.context.getString(R.string.camera360_auto_st_off);
        }
    }
}
