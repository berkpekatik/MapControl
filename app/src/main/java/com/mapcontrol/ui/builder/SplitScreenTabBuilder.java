package com.mapcontrol.ui.builder;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.ui.widget.SplitRatioView;
import com.mapcontrol.ui.widget.SwipeGestureIconView;
import com.mapcontrol.util.AppIconHelper;
import com.mapcontrol.util.DialogHelper;
import com.mapcontrol.util.GestureConflicts;
import com.mapcontrol.util.ProjectionTargetApps;
import com.mapcontrol.util.SplitScreenGesture;
import com.mapcontrol.util.SplitScreenLauncher;
import com.mapcontrol.util.SplitScreenMessages;
import com.mapcontrol.util.SplitScreenStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Bölünmüş ekran sekmesi: iki uygulama, oran, başlatma ve isteğe bağlı hızlı başlatma hareketi.
 * Renkler yalnızca {@link UiStyles#color} ile çözülür; tema değişince {@link #rebuild()} çağrılır.
 */
public class SplitScreenTabBuilder {

    public interface Callback {
        void log(String message);
    }

    private final Context context;
    private final Callback callback;

    private ScrollView scrollView;
    private TextView statusText;
    private TextView noteText;
    private Button startButton;
    private ImageView[] appIcons = new ImageView[2];
    private TextView[] appNames = new TextView[2];
    private SplitRatioView ratioPreview;
    private LinearLayout gestureInfo;
    private final List<GestureCell> gestureCells = new ArrayList<>();
    private boolean starting;

    private static final class GestureCell {
        final int code;
        final LinearLayout view;
        final SwipeGestureIconView icon;
        final TextView label;

        GestureCell(int code, LinearLayout view, SwipeGestureIconView icon, TextView label) {
            this.code = code;
            this.view = view;
            this.icon = icon;
            this.label = label;
        }
    }

    public SplitScreenTabBuilder(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
        build();
    }

    public ScrollView getScrollView() {
        return scrollView != null ? scrollView : build();
    }

    public void rebuild() {
        build();
    }

    /** Sekme görünür olunca uyumluluk ve çakışma durumunu tazeler. */
    public void onTabVisible() {
        refreshStatus();
        refreshGestureUi();
    }

    public ScrollView build() {
        gestureCells.clear();
        scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        scrollView.setFillViewport(true);

        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        int margin = UiStyles.dimenPx(context, R.dimen.oem_card_margin);
        outer.setPadding(margin, margin, margin, margin);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int inner = UiStyles.dimenPx(context, R.dimen.oem_card_inner_padding);
        card.setPadding(inner, inner, inner, inner);
        UiStyles.setGlassCardBackground(card);
        outer.addView(card, matchWrap());
        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        addSectionTitle(card, R.string.split_title, 8);
        addSectionDesc(card, R.string.split_desc);

        statusText = new TextView(context);
        statusText.setTextSize(14);
        statusText.setTypeface(null, Typeface.BOLD);
        statusText.setPadding(16, 0, 16, 4);
        card.addView(statusText, matchWrap());
        noteText = new TextView(context);
        noteText.setTextSize(13);
        noteText.setTextColor(UiStyles.color(context, R.color.textHint));
        noteText.setPadding(16, 0, 16, 12);
        card.addView(noteText, matchWrap());
        refreshStatus();

        addAppRow(card, 0);
        addAppRow(card, 1);
        addRatioSection(card);
        addStartButton(card);
        addDivider(card);
        addGestureSection(card);
        return scrollView;
    }

    // ------------------------------------------------------------------ durum

    private void refreshStatus() {
        if (statusText == null || noteText == null) {
            return;
        }
        SplitScreenLauncher.Capability cap = SplitScreenLauncher.probe(context);
        if (cap.freeformOn) {
            statusText.setText(R.string.split_status_ready);
            statusText.setTextColor(UiStyles.color(context, R.color.accentHighlight));
        } else if (cap.canEnableFreeform) {
            statusText.setText(R.string.split_status_will_enable);
            statusText.setTextColor(UiStyles.color(context, R.color.textLoading));
        } else {
            statusText.setText(R.string.split_status_unsupported);
            statusText.setTextColor(UiStyles.color(context, R.color.statusErrorBright));
        }
        if (cap.usable() && !cap.deviceOwner) {
            noteText.setText(R.string.split_note_not_owner);
            noteText.setVisibility(View.VISIBLE);
        } else {
            noteText.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------- uygulamalar

    private void addAppRow(LinearLayout parent, int slot) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        int pad = Math.round(14 * density);
        row.setPadding(pad, pad, pad, pad);
        row.setMinimumHeight(Math.round(72 * density));
        UiStyles.applySolidRoundedBackground(row, UiStyles.color(context, R.color.surfaceCardInner));

        int iconSize = Math.round(48 * density);
        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textsLp.setMargins(Math.round(16 * density), 0, 0, 0);
        row.addView(texts, textsLp);

        TextView slotLabel = new TextView(context);
        slotLabel.setText(slot == 0 ? R.string.split_app1 : R.string.split_app2);
        slotLabel.setTextSize(13);
        slotLabel.setTextColor(UiStyles.color(context, R.color.textSecondary));
        texts.addView(slotLabel, matchWrap());

        TextView name = new TextView(context);
        name.setTextSize(18);
        name.setTypeface(null, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(name, matchWrap());

        appIcons[slot] = icon;
        appNames[slot] = name;
        refreshAppRow(slot);

        row.setOnClickListener(v -> openPicker(slot));

        LinearLayout.LayoutParams lp = matchWrap();
        int h = UiStyles.dimenPx(context, R.dimen.spacing_medium);
        lp.setMargins(h, 0, h, UiStyles.dimenPx(context, R.dimen.spacing_small));
        parent.addView(row, lp);
    }

    private void refreshAppRow(int slot) {
        String pkg = slot == 0 ? SplitScreenStore.getPkg1(context) : SplitScreenStore.getPkg2(context);
        ImageView icon = appIcons[slot];
        TextView name = appNames[slot];
        if (icon == null || name == null) {
            return;
        }
        if (pkg == null) {
            icon.setImageResource(R.drawable.ic_mdi_view_grid);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                    UiStyles.color(context, R.color.textSecondary)));
            name.setText(R.string.split_select_app);
            name.setTextColor(UiStyles.color(context, R.color.textSecondary));
        } else {
            AppIconHelper.apply(icon, pkg);
            name.setText(labelOf(pkg));
            name.setTextColor(UiStyles.color(context, R.color.textPrimary));
        }
    }

    private String labelOf(String pkg) {
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private void openPicker(int slot) {
        try {
            List<ProjectionTargetApps.Row> rows = ProjectionTargetApps.loadAllLaunchableRows(context);
            if (rows.isEmpty()) {
                Toast.makeText(context, R.string.projection_list_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            String[] items = new String[rows.size()];
            List<String> packages = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                ProjectionTargetApps.Row r = rows.get(i);
                items[i] = r.label + " (" + r.packageName + ")";
                packages.add(r.packageName);
            }
            String title = context.getString(R.string.split_pick_title, slot + 1, items.length);
            DialogHelper.showAppSelectionDialog(context, title, items, packages, null,
                    selected -> {
                        if (slot == 0) {
                            SplitScreenStore.setPkg1(context, selected);
                        } else {
                            SplitScreenStore.setPkg2(context, selected);
                        }
                        refreshAppRow(slot);
                        callback.log("Bölünmüş ekran: uygulama " + (slot + 1) + " = " + selected);
                    },
                    null,
                    () -> {
                        if (slot == 0) {
                            SplitScreenStore.setPkg1(context, null);
                        } else {
                            SplitScreenStore.setPkg2(context, null);
                        }
                        refreshAppRow(slot);
                    });
        } catch (Exception e) {
            callback.log("Bölünmüş ekran listesi hatası: " + e.getMessage());
            Toast.makeText(context, context.getString(R.string.common_error_prefix, e.getMessage()),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // -------------------------------------------------------------------- oran

    private void addRatioSection(LinearLayout parent) {
        SplitScreenLauncher.Ratio current = SplitScreenStore.getRatio(context);

        ratioPreview = new SplitRatioView(context);
        applyPreviewColors();
        ratioPreview.setShare(current.firstShare);

        UiStyles.addTernarySegmentedControl(context, parent,
                context.getString(R.string.split_ratio_title),
                new String[]{
                        context.getString(R.string.split_ratio_a),
                        context.getString(R.string.split_ratio_b),
                        context.getString(R.string.split_ratio_c)},
                new String[]{
                        context.getString(R.string.split_ratio_help_a),
                        context.getString(R.string.split_ratio_help_b),
                        context.getString(R.string.split_ratio_help_c)},
                new int[]{0, 1, 2},
                current.ordinal(),
                modeId -> {
                    SplitScreenLauncher.Ratio ratio = SplitScreenLauncher.Ratio.values()[modeId];
                    SplitScreenStore.setRatio(context, ratio);
                    ratioPreview.setShare(ratio.firstShare);
                });

        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Math.round(260 * density), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.setMargins(0, UiStyles.dimenPx(context, R.dimen.spacing_small), 0,
                UiStyles.dimenPx(context, R.dimen.spacing_medium));
        parent.addView(ratioPreview, lp);
    }

    private void applyPreviewColors() {
        int accent = UiStyles.color(context, R.color.oemAccent);
        ratioPreview.setColors(
                ColorUtils.setAlphaComponent(accent, 140),
                UiStyles.color(context, R.color.surfaceCardElevated),
                UiStyles.color(context, R.color.outlineSubtle),
                UiStyles.color(context, R.color.textPrimary));
    }

    // ---------------------------------------------------------------- başlatma

    private void addStartButton(LinearLayout parent) {
        startButton = new Button(context);
        startButton.setText(R.string.split_start);
        startButton.setAllCaps(false);
        startButton.setTextSize(17);
        startButton.setTypeface(null, Typeface.BOLD);
        startButton.setTextColor(UiStyles.color(context, R.color.textPrimary));
        UiStyles.styleOemButton(startButton, UiStyles.color(context, R.color.buttonPrimary));
        UiStyles.setButtonStartIconTinted(startButton, R.drawable.ic_mdi_view_split,
                UiStyles.color(context, R.color.textPrimary),
                UiStyles.dimenPx(context, R.dimen.spacing_small));
        startButton.setOnClickListener(v -> onStartClicked());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UiStyles.dimenPx(context, R.dimen.button_height) * 5 / 4);
        int h = UiStyles.dimenPx(context, R.dimen.spacing_medium);
        lp.setMargins(h, 0, h, UiStyles.dimenPx(context, R.dimen.spacing_small));
        parent.addView(startButton, lp);
    }

    private void onStartClicked() {
        if (starting) {
            return;
        }
        String pkg1 = SplitScreenStore.getPkg1(context);
        String pkg2 = SplitScreenStore.getPkg2(context);
        if (pkg1 == null || pkg2 == null) {
            Toast.makeText(context, R.string.split_select_both, Toast.LENGTH_SHORT).show();
            return;
        }
        if (pkg1.equals(pkg2)) {
            Toast.makeText(context, R.string.split_same_app, Toast.LENGTH_SHORT).show();
            return;
        }
        starting = true;
        startButton.setEnabled(false);
        startButton.setAlpha(0.6f);
        startButton.setText(R.string.split_starting);
        callback.log("Bölünmüş ekran başlatılıyor: " + pkg1 + " + " + pkg2);
        SplitScreenLauncher.start(context, pkg1, pkg2, SplitScreenStore.getRatio(context), result -> {
            starting = false;
            if (startButton != null) {
                startButton.setEnabled(true);
                startButton.setAlpha(1f);
                startButton.setText(R.string.split_start);
            }
            callback.log("Bölünmüş ekran sonucu: " + result);
            if (result != SplitScreenLauncher.Result.STARTED) {
                Toast.makeText(context, SplitScreenMessages.resultText(context, result),
                        Toast.LENGTH_LONG).show();
            }
            refreshStatus();
        });
    }

    // ------------------------------------------------------------------ hareket

    private void addGestureSection(LinearLayout parent) {
        addSectionTitle(parent, R.string.split_gesture_title, 8);
        addSectionDesc(parent, R.string.split_gesture_desc);

        gestureInfo = new LinearLayout(context);
        gestureInfo.setOrientation(LinearLayout.VERTICAL);
        int h = UiStyles.dimenPx(context, R.dimen.spacing_medium);
        LinearLayout.LayoutParams infoLp = matchWrap();
        infoLp.setMargins(h, 0, h, UiStyles.dimenPx(context, R.dimen.spacing_medium));
        parent.addView(gestureInfo, infoLp);

        for (int fingers : SplitScreenGesture.FINGER_COUNTS) {
            TextView header = new TextView(context);
            header.setText(context.getString(R.string.split_gesture_row_fingers, fingers));
            header.setTextSize(15);
            header.setTypeface(null, Typeface.BOLD);
            header.setTextColor(UiStyles.color(context, R.color.textPrimary));
            header.setPadding(16, 8, 16, 6);
            parent.addView(header, matchWrap());

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = matchWrap();
            rowLp.setMargins(h, 0, h, UiStyles.dimenPx(context, R.dimen.spacing_small));
            parent.addView(row, rowLp);

            for (int direction : SplitScreenGesture.DIRECTIONS) {
                addGestureCell(row, SplitScreenGesture.code(fingers, direction));
            }
        }

        Button off = new Button(context);
        off.setText(R.string.split_gesture_off);
        off.setAllCaps(false);
        off.setTextSize(15);
        off.setTextColor(UiStyles.color(context, R.color.textPrimary));
        UiStyles.styleOemButton(off, UiStyles.color(context, R.color.buttonSecondaryMuted));
        off.setOnClickListener(v -> {
            SplitScreenStore.setGestureCode(context, SplitScreenStore.GESTURE_OFF);
            callback.log("Bölünmüş ekran hareketi kapatıldı");
            refreshGestureUi();
        });
        LinearLayout.LayoutParams offLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UiStyles.dimenPx(context, R.dimen.button_height));
        offLp.setMargins(h, UiStyles.dimenPx(context, R.dimen.spacing_small), h, 0);
        parent.addView(off, offLp);

        refreshGestureUi();
    }

    private void addGestureCell(LinearLayout row, int code) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setClickable(true);
        cell.setFocusable(true);
        int pad = Math.round(8 * density);
        cell.setPadding(pad, pad, pad, pad);

        SwipeGestureIconView icon = new SwipeGestureIconView(context);
        icon.setGesture(SplitScreenGesture.fingersOf(code), SplitScreenGesture.directionOf(code));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                Math.round(104 * density), Math.round(68 * density));
        iconLp.gravity = Gravity.CENTER_HORIZONTAL;
        cell.addView(icon, iconLp);

        TextView label = new TextView(context);
        label.setText(directionLabel(SplitScreenGesture.directionOf(code)));
        label.setTextSize(13);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelLp = matchWrap();
        labelLp.topMargin = Math.round(4 * density);
        cell.addView(label, labelLp);

        cell.setContentDescription(describeGesture(code));
        cell.setOnClickListener(v -> onGestureCellClicked(code));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        int m = Math.round(4 * density);
        lp.setMargins(m, 0, m, 0);
        row.addView(cell, lp);
        gestureCells.add(new GestureCell(code, cell, icon, label));
    }

    private void onGestureCellClicked(int code) {
        int current = SplitScreenStore.getGestureCode(context);
        if (current != code) {
            int owner = ownerOf(code);
            if (owner != 0) {
                DialogHelper.showGestureConflict(context, owner);
                return;
            }
        }
        int next = current == code ? SplitScreenStore.GESTURE_OFF : code;
        SplitScreenStore.setGestureCode(context, next);
        callback.log(next == SplitScreenStore.GESTURE_OFF
                ? "Bölünmüş ekran hareketi kapatıldı"
                : "Bölünmüş ekran hareketi: " + describeGesture(next));
        if (next != SplitScreenStore.GESTURE_OFF && !SplitScreenStore.hasPair(context)) {
            Toast.makeText(context, R.string.split_gesture_need_pair, Toast.LENGTH_LONG).show();
        }
        refreshGestureUi();
    }

    /** Bu hareketi bölünmüş ekran dışında kullanan özelliğin ad kaynağı; yoksa 0. */
    private int ownerOf(int code) {
        return GestureConflicts.findOwnerLabel(context, code, GestureConflicts.Slot.SPLIT_SCREEN);
    }

    private void refreshGestureUi() {
        if (gestureInfo == null) {
            return;
        }
        int selected = SplitScreenStore.getGestureCode(context);
        int accent = UiStyles.color(context, R.color.oemAccent);
        int idle = UiStyles.color(context, R.color.textSecondary);
        float density = context.getResources().getDisplayMetrics().density;

        for (GestureCell cell : gestureCells) {
            boolean isSelected = cell.code == selected;
            boolean blocked = !isSelected && ownerOf(cell.code) != 0;
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(UiStyles.dimenPx(context, R.dimen.oem_button_radius));
            bg.setColor(UiStyles.color(context, R.color.surfaceCardInner));
            if (isSelected) {
                bg.setStroke(Math.round(2 * density), accent);
            } else {
                bg.setStroke(Math.round(1 * density), UiStyles.color(context, R.color.outlineSubtle));
            }
            cell.view.setBackground(bg);
            cell.view.setAlpha(blocked ? 0.4f : 1f);
            int tint = isSelected ? accent : idle;
            cell.icon.setTint(tint);
            cell.label.setTextColor(tint);
            cell.label.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
        }
        rebuildGestureInfo(selected);
    }

    /** Seçili hareketin büyük çizimi: kaç parmak, hangi yön, nereye. */
    private void rebuildGestureInfo(int selected) {
        gestureInfo.removeAllViews();
        float density = context.getResources().getDisplayMetrics().density;
        int pad = Math.round(14 * density);
        gestureInfo.setPadding(pad, pad, pad, pad);
        UiStyles.applySolidRoundedBackground(gestureInfo,
                UiStyles.color(context, R.color.surfaceCardInner));

        if (!SplitScreenGesture.isValid(selected)) {
            TextView none = new TextView(context);
            none.setText(R.string.split_gesture_none);
            none.setTextSize(14);
            none.setTextColor(UiStyles.color(context, R.color.textSecondary));
            gestureInfo.addView(none, matchWrap());
            return;
        }

        int fingers = SplitScreenGesture.fingersOf(selected);
        int direction = SplitScreenGesture.directionOf(selected);

        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        gestureInfo.addView(line, matchWrap());

        SwipeGestureIconView big = new SwipeGestureIconView(context);
        big.setGesture(fingers, direction);
        big.setTint(UiStyles.color(context, R.color.oemAccent));
        line.addView(big, new LinearLayout.LayoutParams(
                Math.round(150 * density), Math.round(110 * density)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textsLp.setMargins(Math.round(16 * density), 0, 0, 0);
        line.addView(texts, textsLp);

        TextView title = new TextView(context);
        title.setText(context.getString(R.string.split_gesture_selected_title,
                describeGesture(selected)));
        title.setTextSize(17);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        texts.addView(title, matchWrap());

        TextView how = new TextView(context);
        how.setText(context.getString(R.string.split_gesture_how, fingers,
                directionPhrase(direction)));
        how.setTextSize(14);
        how.setTextColor(UiStyles.color(context, R.color.textPrimary87));
        how.setLineSpacing(3, 1.05f);
        how.setPadding(0, Math.round(6 * density), 0, 0);
        texts.addView(how, matchWrap());

        TextView where = new TextView(context);
        where.setText(R.string.split_gesture_where);
        where.setTextSize(13);
        where.setTextColor(UiStyles.color(context, R.color.textHint));
        where.setLineSpacing(3, 1.05f);
        where.setPadding(0, Math.round(6 * density), 0, 0);
        texts.addView(where, matchWrap());

        if (!SplitScreenStore.hasPair(context)) {
            addInfoWarning(R.string.split_gesture_need_pair);
        }
        int owner = ownerOf(selected);
        if (owner != 0) {
            addInfoWarning(context.getString(R.string.split_gesture_conflict, context.getString(owner)));
        }
    }

    private void addInfoWarning(int textRes) {
        addInfoWarning(context.getString(textRes));
    }

    private void addInfoWarning(String text) {
        TextView warn = new TextView(context);
        warn.setText(text);
        warn.setTextSize(13);
        warn.setTypeface(null, Typeface.BOLD);
        warn.setTextColor(UiStyles.color(context, R.color.textLoading));
        warn.setPadding(0, Math.round(10 * context.getResources().getDisplayMetrics().density), 0, 0);
        gestureInfo.addView(warn, matchWrap());
    }

    private String describeGesture(int code) {
        return context.getString(R.string.split_gesture_name,
                SplitScreenGesture.fingersOf(code),
                directionLabel(SplitScreenGesture.directionOf(code)));
    }

    private String directionLabel(int direction) {
        switch (direction) {
            case SplitScreenGesture.LEFT:
                return context.getString(R.string.split_dir_left);
            case SplitScreenGesture.RIGHT:
                return context.getString(R.string.split_dir_right);
            case SplitScreenGesture.DOWN:
                return context.getString(R.string.split_dir_down);
            case SplitScreenGesture.UP:
            default:
                return context.getString(R.string.split_dir_up);
        }
    }

    private String directionPhrase(int direction) {
        switch (direction) {
            case SplitScreenGesture.LEFT:
                return context.getString(R.string.split_dir_phrase_left);
            case SplitScreenGesture.RIGHT:
                return context.getString(R.string.split_dir_phrase_right);
            case SplitScreenGesture.DOWN:
                return context.getString(R.string.split_dir_phrase_down);
            case SplitScreenGesture.UP:
            default:
                return context.getString(R.string.split_dir_phrase_up);
        }
    }

    // --------------------------------------------------------------- ortak UI

    private void addDivider(LinearLayout parent) {
        View divider = new View(context);
        divider.setBackgroundColor(UiStyles.color(context, R.color.dividerWhite12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(20, 20, 20, 12);
        parent.addView(divider, lp);
    }

    private void addSectionTitle(LinearLayout parent, int textRes, int topPad) {
        TextView title = new TextView(context);
        title.setText(textRes);
        title.setTextSize(18);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(16, topPad, 16, 8);
        parent.addView(title, matchWrap());
    }

    private void addSectionDesc(LinearLayout parent, int textRes) {
        TextView desc = new TextView(context);
        desc.setText(textRes);
        desc.setTextSize(13);
        desc.setTextColor(UiStyles.color(context, R.color.textHint));
        desc.setLineSpacing(3, 1.05f);
        desc.setPadding(16, 0, 16, 12);
        parent.addView(desc, matchWrap());
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
