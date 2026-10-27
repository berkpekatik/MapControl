package com.mapcontrol.ui.builder;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.ui.welcome.WelcomeDesign;
import com.mapcontrol.ui.welcome.WelcomeDesignCatalog;

import java.util.List;
import java.util.function.Consumer;

/**
 * Açılış ekranı: Kamera 360 gibi menü, Ses Üretme ve Açılış Tasarımı sayfaları.
 */
public final class WelcomeSoundScreenBuilder {

    private WelcomeSoundScreenBuilder() {
    }

    public static final class Screen {
        public final ScrollView scrollView;
        public final TextView usbStatus;
        public final LinearLayout usbList;
        public final TextView downloadsStatus;
        public final LinearLayout downloadsList;
        public final TextView savedLine;
        public final TextView tvFilePath;
        public final LinearLayout rowSelect;
        public final LinearLayout playbackRow;
        public final TextView playbackLabel;
        public final Button btnPreview;
        public Runnable onShowSound;
        public Runnable openSoundPage;
        public TextView soundTitle;
        public EditText designLineInput;

        Screen(ScrollView scrollView, TextView usbStatus, LinearLayout usbList,
                TextView downloadsStatus, LinearLayout downloadsList, TextView savedLine,
                TextView tvFilePath, LinearLayout rowSelect, LinearLayout playbackRow,
                TextView playbackLabel, Button btnPreview) {
            this.scrollView = scrollView;
            this.usbStatus = usbStatus;
            this.usbList = usbList;
            this.downloadsStatus = downloadsStatus;
            this.downloadsList = downloadsList;
            this.savedLine = savedLine;
            this.tvFilePath = tvFilePath;
            this.rowSelect = rowSelect;
            this.playbackRow = playbackRow;
            this.playbackLabel = playbackLabel;
            this.btnPreview = btnPreview;
        }
    }

    public static LinearLayout actionRow(Context context, CharSequence label) {
        int primary = UiStyles.color(context, R.color.textPrimary);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setPadding(16, 14, 16, 14);
        TextView title = new TextView(context);
        title.setText(label);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(primary);
        row.addView(title);
        paintCard(context, row);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 8);
        row.setLayoutParams(params);
        return row;
    }

    public static Screen buildTabScrollView(Context context, boolean autoPlayInitial,
            Consumer<Boolean> onAutoPlayCommitted, String selectedDesignId,
            Consumer<String> onDesignCommitted, String designLine,
            Consumer<String> onDesignLine) {
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(UiStyles.color(context, R.color.backgroundPage));

        FrameLayout host = new FrameLayout(context);
        scrollView.addView(host, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.MATCH_PARENT));

        LinearLayout menu = buildMenu(context);
        LinearLayout soundPage = pageBody(context);
        LinearLayout designPage = pageBody(context);
        ScrollView soundScroll = pageScroll(context);
        ScrollView designScroll = pageScroll(context);
        soundScroll.addView(soundPage, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        designScroll.addView(designPage, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        host.addView(menu, fill());
        host.addView(soundScroll, fill());
        host.addView(designScroll, fill());
        soundScroll.setVisibility(View.GONE);
        designScroll.setVisibility(View.GONE);

        SoundSection sound = addSoundPage(context, soundPage, autoPlayInitial, onAutoPlayCommitted,
                () -> show(scrollView, menu, soundScroll, designScroll));
        DesignSection design = addDesignPage(context, designPage, selectedDesignId, onDesignCommitted,
                designLine, onDesignLine,
                () -> show(scrollView, menu, soundScroll, designScroll));

        Screen screen = new Screen(scrollView, sound.usbStatus, sound.usbList, sound.downloadsStatus,
                sound.downloadsList, sound.savedLine, sound.tvFilePath, sound.rowSelect,
                sound.playbackRow, sound.playbackLabel, design.preview);

        screen.designLineInput = design.lineInput;
        screen.soundTitle = sound.soundTitle;
        screen.openSoundPage = () -> show(scrollView, soundScroll, menu, designScroll);

        sound.card.setOnClickListener(v -> {
            show(scrollView, soundScroll, menu, designScroll);
            if (screen.onShowSound != null) {
                screen.onShowSound.run();
            }
        });
        design.card.setOnClickListener(v -> show(scrollView, designScroll, menu, soundScroll));
        return screen;
    }

    private static void show(ScrollView scroller, View visible, View hiddenA, View hiddenB) {
        visible.setVisibility(View.VISIBLE);
        hiddenA.setVisibility(View.GONE);
        hiddenB.setVisibility(View.GONE);
        scroller.scrollTo(0, 0);
        if (visible instanceof ScrollView) {
            visible.scrollTo(0, 0);
        }
    }

    private static LinearLayout buildMenu(Context context) {
        boolean tall = tallLayout(context);
        LinearLayout menu = new LinearLayout(context);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setGravity(Gravity.CENTER);
        int side = dp(context, 16);
        menu.setPadding(side, 0, side, 0);

        LinearLayout heading = new LinearLayout(context);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        int accent = UiStyles.color(context, R.color.oemAccent);
        heading.addView(iconBadge(context, R.drawable.ic_mdi_volume_high, accent));
        LinearLayout headingText = new LinearLayout(context);
        headingText.setOrientation(LinearLayout.VERTICAL);
        headingText.setPadding(dp(context, 14), 0, 0, 0);
        TextView title = new TextView(context);
        title.setText(R.string.welcome_sound_title);
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        headingText.addView(title);
        TextView subtitle = new TextView(context);
        subtitle.setText(R.string.welcome_menu_sub);
        subtitle.setTextSize(13);
        subtitle.setMaxLines(3);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setTextColor(UiStyles.color(context, R.color.textSecondary));
        headingText.addView(subtitle);
        heading.addView(headingText);
        menu.addView(heading, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout cards = new LinearLayout(context);
        cards.setOrientation(tall ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        cards.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cardsP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardsP.setMargins(0, dp(context, 18), 0, 0);
        menu.addView(cards, cardsP);
        menu.setTag(cards);
        return menu;
    }

    private static SoundSection addSoundPage(Context context, LinearLayout body,
            boolean autoPlayInitial, Consumer<Boolean> onAutoPlayCommitted, Runnable onBack) {
        body.addView(backRow(context, onBack));

        TextView title = sectionTitle(context, R.string.welcome_group_sound);
        body.addView(title);

        LinearLayout autoBlock = new LinearLayout(context);
        autoBlock.setOrientation(LinearLayout.VERTICAL);
        UiStyles.addBinarySegmentedControl(context, autoBlock,
                context.getString(R.string.welcome_sound_autoplay_title),
                context.getString(R.string.welcome_yes),
                context.getString(R.string.welcome_no),
                context.getString(R.string.welcome_sound_autoplay_on_help),
                context.getString(R.string.welcome_sound_autoplay_off_help),
                autoPlayInitial,
                onAutoPlayCommitted);
        body.addView(autoBlock);

        body.addView(hint(context, R.string.welcome_mp3_hint));
        LinearLayout rowSelect = actionRow(context, context.getString(R.string.welcome_downloads_title));
        body.addView(rowSelect);

        TextView savedLine = new TextView(context);
        savedLine.setVisibility(View.GONE);

        TextView downloadsStatus = hint(context, R.string.welcome_downloads_empty);
        downloadsStatus.setVisibility(View.GONE);
        LinearLayout downloadsList = new LinearLayout(context);
        downloadsList.setOrientation(LinearLayout.VERTICAL);
        downloadsList.setVisibility(View.GONE);
        TextView tvFilePath = new TextView(context);
        LinearLayout playbackRow = new LinearLayout(context);
        TextView playbackLabel = new TextView(context);
        playbackRow.addView(playbackLabel);

        LinearLayout usbBlock = new LinearLayout(context);
        usbBlock.setOrientation(LinearLayout.VERTICAL);
        usbBlock.addView(label(context, R.string.welcome_sound_usb_title));
        usbBlock.addView(hint(context, R.string.welcome_usb_copy_hint));
        TextView usbStatus = hint(context, R.string.welcome_sound_usb_empty);
        LinearLayout usbList = new LinearLayout(context);
        usbList.setOrientation(LinearLayout.VERTICAL);
        usbBlock.addView(usbStatus);
        usbBlock.addView(usbList, wrap());
        body.addView(usbBlock);

        LinearLayout cards = menuCards(body);
        LinearLayout card = menuCard(context, R.drawable.ic_mdi_music_note,
                R.string.welcome_group_sound, R.string.welcome_group_sound_sub);
        cards.addView(card);
        return new SoundSection(card, usbStatus, usbList, downloadsStatus, downloadsList,
                savedLine, tvFilePath, rowSelect, playbackRow, playbackLabel, title);
    }

    private static DesignSection addDesignPage(Context context, LinearLayout body,
            String selectedDesignId, Consumer<String> onDesignCommitted, String customLine,
            Consumer<String> onLineCommitted, Runnable onBack) {
        body.addView(backRow(context, onBack));
        body.addView(sectionTitle(context, R.string.welcome_group_design));
        body.addView(hint(context, R.string.welcome_group_design_sub));

        DesignSection design = addDesignPicker(context, body, selectedDesignId, onDesignCommitted,
                customLine, onLineCommitted);
        LinearLayout cards = menuCards(body);
        LinearLayout card = menuCard(context, R.drawable.ic_mdi_view_grid,
                R.string.welcome_group_design, R.string.welcome_group_design_sub);
        cards.addView(card);
        design.card = card;
        return design;
    }

    public static void applySavedLine(Context context, TextView savedLine, String customLine) {
        if (savedLine == null) {
            return;
        }
        if (customLine == null || customLine.trim().isEmpty()) {
            savedLine.setText(R.string.welcome_saved_line_empty);
        } else {
            savedLine.setText(context.getString(R.string.welcome_saved_line, customLine.trim()));
        }
    }

    private static DesignSection addDesignPicker(Context context, LinearLayout parent,
            String selectedDesignId, Consumer<String> onDesignCommitted,
            String customLine, Consumer<String> onLineCommitted) {
        int primary = UiStyles.color(context, R.color.textPrimary);
        int secondary = UiStyles.color(context, R.color.textSecondary);
        List<WelcomeDesign> designs = WelcomeDesignCatalog.designs();
        LinearLayout[] rows = new LinearLayout[designs.size()];
        final String[] selected = {selectedDesignId};

        for (int i = 0; i < designs.size(); i++) {
            WelcomeDesign design = designs.get(i);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setClickable(true);
            row.setFocusable(true);
            row.setPadding(16, 14, 16, 14);

            TextView title = new TextView(context);
            title.setText(design.titleRes());
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            title.setTextColor(primary);
            row.addView(title);

            TextView help = new TextView(context);
            help.setText(design.helpRes());
            help.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            help.setTextColor(secondary);
            help.setPadding(0, 4, 0, 0);
            row.addView(help);

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.setMargins(0, 0, 0, 8);
            parent.addView(row, rowParams);
            rows[i] = row;

            row.setOnClickListener(v -> {
                if (design.id().equals(selected[0])) {
                    return;
                }
                selected[0] = design.id();
                applyDesignSelection(context, designs, rows, selected[0]);
                onDesignCommitted.accept(design.id());
            });
        }
        applyDesignSelection(context, designs, rows, selected[0]);

        parent.addView(label(context, R.string.welcome_design_line_label));
        EditText lineInput = new EditText(context);
        lineInput.setHint(R.string.welcome_custom_text_hint);
        lineInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        lineInput.setSingleLine(true);
        lineInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        lineInput.setFilters(new android.text.InputFilter[0]);
        int pad = dp(context, 14);
        lineInput.setPadding(pad, pad, pad, pad);
        lineInput.setTextColor(primary);
        lineInput.setHintTextColor(UiStyles.color(context, R.color.textHint));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(UiStyles.color(context, R.color.surfaceColor));
        inputBg.setCornerRadius(context.getResources().getDimension(R.dimen.oem_button_radius));
        inputBg.setStroke(dp(context, 1), UiStyles.color(context, R.color.outlineMuted));
        lineInput.setBackground(inputBg);
        if (customLine != null && !customLine.isEmpty()) {
            lineInput.setText(customLine);
            lineInput.setSelection(customLine.length());
        }
        lineInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onLineCommitted.accept(s == null ? "" : s.toString());
            }
        });
        parent.addView(lineInput, bottomMargin(context, 8));
        parent.addView(hint(context, R.string.welcome_custom_text_help));

        Button btnPreview = new Button(context);
        btnPreview.setText(R.string.welcome_design_preview);
        btnPreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btnPreview.setTextColor(primary);
        UiStyles.styleOemButton(btnPreview, UiStyles.color(context, R.color.buttonPrimary));
        btnPreview.setPadding(24, 16, 24, 16);
        parent.addView(btnPreview, buttonParams(context));
        return new DesignSection(btnPreview, lineInput);
    }

    private static void applyDesignSelection(Context context, List<WelcomeDesign> designs,
            LinearLayout[] rows, String selectedId) {
        float radius = context.getResources().getDimension(R.dimen.oem_button_radius);
        float density = context.getResources().getDisplayMetrics().density;
        for (int i = 0; i < designs.size(); i++) {
            boolean selected = designs.get(i).id().equals(selectedId);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(UiStyles.color(context, R.color.surfaceCard));
            bg.setCornerRadius(radius);
            bg.setStroke(Math.max(1, Math.round((selected ? 2f : 1f) * density)),
                    UiStyles.color(context, selected ? R.color.oemAccent : R.color.outline));
            rows[i].setBackground(bg);
            TextView title = (TextView) rows[i].getChildAt(0);
            title.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            title.setTextColor(UiStyles.color(context,
                    selected ? R.color.textPrimary : R.color.textSecondary));
        }
    }

    private static LinearLayout menuCard(Context context, int iconRes, int titleRes, int subRes) {
        boolean tall = tallLayout(context);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(context, 18), dp(context, 18), dp(context, 12), dp(context, 18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiStyles.color(context, R.color.surfaceCard));
        bg.setCornerRadius(dp(context, 22));
        bg.setStroke(dp(context, 1), UiStyles.color(context, R.color.outline));
        card.setBackground(bg);
        card.setClickable(true);
        card.setFocusable(true);
        LinearLayout.LayoutParams params = tall
                ? new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 112))
                : new LinearLayout.LayoutParams(0, dp(context, 124), 1f);
        int margin = dp(context, 8);
        params.setMargins(margin, margin, margin, margin);
        card.setLayoutParams(params);
        card.addView(iconBadge(context, iconRes, UiStyles.color(context, R.color.oemAccent)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsP = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textsP.setMarginStart(dp(context, 16));
        card.addView(texts, textsP);

        TextView title = new TextView(context);
        title.setText(titleRes);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        texts.addView(title);
        TextView sub = new TextView(context);
        sub.setText(subRes);
        sub.setTextSize(14);
        sub.setMaxLines(2);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        sub.setTextColor(UiStyles.color(context, R.color.textSecondary));
        texts.addView(sub);

        ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.ic_mdi_chevron_up);
        chevron.setRotation(90f);
        chevron.setColorFilter(UiStyles.color(context, R.color.textMuted));
        card.addView(chevron, new LinearLayout.LayoutParams(dp(context, 28), dp(context, 28)));
        return card;
    }

    private static LinearLayout backRow(Context context, Runnable onBack) {
        LinearLayout back = new LinearLayout(context);
        back.setOrientation(LinearLayout.HORIZONTAL);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(dp(context, 12), 0, dp(context, 16), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiStyles.color(context, R.color.surfaceCard));
        bg.setCornerRadius(dp(context, 22));
        back.setBackground(bg);
        back.setOnClickListener(v -> onBack.run());
        ImageView arrow = new ImageView(context);
        arrow.setImageResource(R.drawable.ic_hub_arrow_left);
        arrow.setColorFilter(UiStyles.color(context, R.color.textPrimary));
        back.addView(arrow, new LinearLayout.LayoutParams(dp(context, 22), dp(context, 22)));
        TextView label = new TextView(context);
        label.setText(R.string.welcome_sound_title);
        label.setTextSize(15);
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(UiStyles.color(context, R.color.textPrimary));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.setMarginStart(dp(context, 8));
        back.addView(label, labelParams);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(context, 44));
        params.setMargins(0, 0, 0, dp(context, 16));
        back.setLayoutParams(params);
        return back;
    }

    private static FrameLayout iconBadge(Context context, int iconRes, int tint) {
        FrameLayout badge = new FrameLayout(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x332EE6D6);
        badge.setBackground(bg);
        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(tint);
        int pad = dp(context, 14);
        badge.setPadding(pad, pad, pad, pad);
        badge.addView(icon, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        badge.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 56), dp(context, 56)));
        return badge;
    }

    private static void paintCard(Context context, LinearLayout row) {
        float radius = context.getResources().getDimension(R.dimen.oem_button_radius);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiStyles.color(context, R.color.surfaceCard));
        bg.setCornerRadius(radius);
        bg.setStroke(dp(context, 1), UiStyles.color(context, R.color.outline));
        row.setBackground(bg);
    }

    private static LinearLayout pageBody(Context context) {
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        int margin = dp(context, tallLayout(context) ? 18 : 16);
        body.setPadding(margin, margin, margin, margin);
        return body;
    }

    private static ScrollView pageScroll(Context context) {
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(UiStyles.color(context, R.color.backgroundPage));
        return scroll;
    }

    private static TextView sectionTitle(Context context, int textRes) {
        TextView title = new TextView(context);
        title.setText(textRes);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        title.setPadding(0, 0, 0, dp(context, 8));
        return title;
    }

    private static TextView label(Context context, int textRes) {
        TextView label = new TextView(context);
        label.setText(textRes);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        label.setTextColor(UiStyles.color(context, R.color.textPrimary87));
        label.setPadding(0, dp(context, 16), 0, dp(context, 8));
        return label;
    }

    private static TextView hint(Context context, int textRes) {
        TextView hint = new TextView(context);
        hint.setText(textRes);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setTextColor(UiStyles.color(context, R.color.textHint));
        hint.setPadding(0, 0, 0, dp(context, 8));
        return hint;
    }

    private static TextView boxedText(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        view.setTextColor(UiStyles.color(context, R.color.textPrimary));
        int pad = dp(context, 12);
        view.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiStyles.color(context, R.color.surfaceCard));
        bg.setCornerRadius(context.getResources().getDimension(R.dimen.oem_button_radius));
        bg.setStroke(dp(context, 1), UiStyles.color(context, R.color.outline));
        view.setBackground(bg);
        return view;
    }

    private static LinearLayout.LayoutParams buttonParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(context, 8), 0, dp(context, 8));
        return params;
    }

    private static LinearLayout.LayoutParams bottomMargin(Context context, int dp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(context, dp));
        return params;
    }

    private static LinearLayout menuCards(View start) {
        View node = start;
        while (node != null) {
            if (node instanceof FrameLayout) {
                FrameLayout frame = (FrameLayout) node;
                if (frame.getChildCount() > 0 && frame.getChildAt(0).getTag() instanceof LinearLayout) {
                    return (LinearLayout) frame.getChildAt(0).getTag();
                }
            }
            if (!(node.getParent() instanceof View)) {
                break;
            }
            node = (View) node.getParent();
        }
        throw new IllegalStateException("Hoşgeldin menüsü bulunamadı.");
    }

    private static FrameLayout.LayoutParams fill() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
    }

    private static FrameLayout.LayoutParams frameWrap() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout gridRow(Context context, View left, View right) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(left, gridCellParams(context));
        row.addView(right, gridCellParams(context));
        return row;
    }

    private static LinearLayout.LayoutParams gridCellParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(context, 108), 1f);
        int margin = dp(context, 6);
        params.setMargins(margin, margin, margin, margin);
        return params;
    }

    private static LinearLayout gridTile(Context context, int titleRes, String subtitle) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        TextView title = new TextView(context);
        title.setText(titleRes);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(title);
        TextView sub = new TextView(context);
        sub.setText(subtitle);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        sub.setTextColor(UiStyles.color(context, R.color.textSecondary));
        sub.setMaxLines(2);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        sub.setPadding(0, dp(context, 4), 0, 0);
        tile.addView(sub);
        paintCard(context, tile);
        return tile;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static boolean tallLayout(Context context) {
        Configuration configuration = context.getResources().getConfiguration();
        if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            return true;
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return metrics.widthPixels <= 1200 && metrics.heightPixels >= metrics.widthPixels;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class SoundSection {
        final LinearLayout card;
        final TextView usbStatus;
        final LinearLayout usbList;
        final TextView downloadsStatus;
        final LinearLayout downloadsList;
        final TextView savedLine;
        final TextView tvFilePath;
        final LinearLayout rowSelect;
        final LinearLayout playbackRow;
        final TextView playbackLabel;
        final TextView soundTitle;

        SoundSection(LinearLayout card, TextView usbStatus, LinearLayout usbList,
                TextView downloadsStatus, LinearLayout downloadsList, TextView savedLine,
                TextView tvFilePath, LinearLayout rowSelect, LinearLayout playbackRow,
                TextView playbackLabel, TextView soundTitle) {
            this.card = card;
            this.usbStatus = usbStatus;
            this.usbList = usbList;
            this.downloadsStatus = downloadsStatus;
            this.downloadsList = downloadsList;
            this.savedLine = savedLine;
            this.tvFilePath = tvFilePath;
            this.rowSelect = rowSelect;
            this.playbackRow = playbackRow;
            this.playbackLabel = playbackLabel;
            this.soundTitle = soundTitle;
        }
    }

    private static final class DesignSection {
        LinearLayout card;
        final Button preview;
        final EditText lineInput;

        DesignSection(Button preview, EditText lineInput) {
            this.preview = preview;
            this.lineInput = lineInput;
        }
    }
}
