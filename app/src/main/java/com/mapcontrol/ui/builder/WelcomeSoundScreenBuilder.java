package com.mapcontrol.ui.builder;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
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
 * Açılış sesi formu — ana sekme içeriği için kaydırılabilir görünüm (geri çubuğu yok).
 */
public final class WelcomeSoundScreenBuilder {

    private WelcomeSoundScreenBuilder() {
    }

    public static final class Screen {
        public final ScrollView scrollView;
        public final TextView usbStatus;
        public final LinearLayout usbList;
        public final TextView tvFilePath;
        public final LinearLayout rowSelect;
        public final LinearLayout playbackRow;
        public final TextView playbackLabel;
        public final Button btnPreview;

        Screen(ScrollView scrollView, TextView usbStatus, LinearLayout usbList, TextView tvFilePath,
                LinearLayout rowSelect, LinearLayout playbackRow, TextView playbackLabel,
                Button btnPreview) {
            this.scrollView = scrollView;
            this.usbStatus = usbStatus;
            this.usbList = usbList;
            this.tvFilePath = tvFilePath;
            this.rowSelect = rowSelect;
            this.playbackRow = playbackRow;
            this.playbackLabel = playbackLabel;
            this.btnPreview = btnPreview;
        }
    }

    /** Shared touch surface for file selection, playback and USB files. */
    public static LinearLayout actionRow(Context context, CharSequence label) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(android.view.Gravity.CENTER);
        row.setClickable(true);
        row.setFocusable(true);
        row.setMinimumHeight(dp(context, 56));
        row.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
        TextView title = text(context, label, R.dimen.text_size_normal, false);
        title.setGravity(android.view.Gravity.CENTER);
        row.addView(title, new LinearLayout.LayoutParams(-1, -2));
        paintCard(context, row, false);
        row.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(
                        UiStyles.color(context, R.color.oemAccent), 55)), row.getBackground(), null));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(context, 8);
        row.setLayoutParams(params);
        return row;
    }

    public static Screen buildTabScrollView(Context context, boolean autoPlayInitial,
            Consumer<Boolean> onAutoPlayCommitted, String selectedDesignId,
            Consumer<String> onDesignCommitted, String customLine,
            Consumer<String> onLineCommitted) {
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        com.mapcontrol.ui.theme.FeatureGridLayout sections = new com.mapcontrol.ui.theme.FeatureGridLayout(context);
        int margin = UiStyles.dimenPx(context, R.dimen.oem_card_margin);
        sections.setPadding(margin / 2, margin / 2, margin / 2, margin / 2);
        LinearLayout audio = section(context, R.string.welcome_sound_section_title);
        audio.setTag("welcome_audio_section");
        LinearLayout design = section(context, R.string.welcome_design_section_title);
        design.setTag("welcome_design_section");
        sections.addCard(audio);
        sections.addCard(design);

        addLabel(context, audio, R.string.welcome_sound_selected_label);
        TextView tvFilePath = text(context, context.getString(R.string.welcome_sound_no_file_yet),
                R.dimen.text_size_normal, false);
        tvFilePath.setTag("welcome_selected_file");
        tvFilePath.setMaxLines(2);
        tvFilePath.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvFilePath.setMinHeight(dp(context, 48));
        tvFilePath.setGravity(android.view.Gravity.CENTER_VERTICAL);
        tvFilePath.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        paintCard(context, tvFilePath, false);
        addSpaced(context, audio, tvFilePath, 0, 12);

        LinearLayout controls = new LinearLayout(context);
        LinearLayout rowSelect = actionRow(context, context.getString(R.string.welcome_sound_choose_file));
        rowSelect.setTag("welcome_choose_sound");
        LinearLayout playbackRow = actionRow(context, context.getString(R.string.welcome_sound_play));
        playbackRow.setTag("welcome_play_sound");
        playbackRow.setAlpha(.45f);
        TextView playbackLabel = (TextView) playbackRow.getChildAt(0);
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(0, -2, 1);
        chooseParams.setMarginEnd(dp(context, 8));
        controls.addView(rowSelect, chooseParams);
        controls.addView(playbackRow, new LinearLayout.LayoutParams(0, -2, 1));
        addSpaced(context, audio, controls, 0, 12);

        UiStyles.addBinarySegmentedControl(context, audio,
                context.getString(R.string.welcome_sound_autoplay_title),
                context.getString(R.string.common_yes),
                context.getString(R.string.common_no),
                context.getString(R.string.welcome_sound_autoplay_desc),
                context.getString(R.string.welcome_sound_autoplay_desc),
                autoPlayInitial,
                onAutoPlayCommitted);

        addLabel(context, audio, R.string.welcome_sound_usb_title);
        TextView usbStatus = text(context, context.getString(R.string.welcome_sound_usb_absent),
                R.dimen.text_size_small, true);
        usbStatus.setTag("welcome_usb_status");
        addSpaced(context, audio, usbStatus, 0, 8);
        LinearLayout usbList = new LinearLayout(context);
        usbList.setOrientation(LinearLayout.VERTICAL);
        usbList.setVisibility(android.view.View.GONE);
        audio.addView(usbList, new LinearLayout.LayoutParams(-1, -2));

        Button help = new Button(context);
        help.setText(R.string.welcome_sound_import_help);
        help.setTag("welcome_import_help");
        UiStyles.styleOemButton(help, UiStyles.color(context, R.color.surfaceCardInner));
        help.setTextColor(UiStyles.color(context, R.color.textPrimary));
        help.setMinHeight(dp(context, 56));
        addSpaced(context, audio, help, 4, 0);
        help.setOnClickListener(v -> {
            androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle(R.string.welcome_sound_import_help)
                    .setMessage(R.string.welcome_sound_steps)
                    .setPositiveButton(android.R.string.ok, null).show();
            TextView title = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
            if (title != null) UiStyles.setTextSize(title, R.dimen.text_size_medium);
            TextView message = dialog.findViewById(android.R.id.message);
            if (message != null) UiStyles.setTextSize(message, R.dimen.text_size_small);
            UiStyles.setTextSize(dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE), R.dimen.text_size_normal);
        });

        Button btnPreview = addDesignPicker(context, design, selectedDesignId,
                onDesignCommitted, customLine, onLineCommitted);
        scrollView.addView(sections, new ScrollView.LayoutParams(-1, -2));
        return new Screen(scrollView, usbStatus, usbList, tvFilePath, rowSelect, playbackRow,
                playbackLabel, btnPreview);
    }

    private static Button addDesignPicker(Context context, LinearLayout parent,
            String selectedDesignId, Consumer<String> onDesignCommitted,
            String customLine, Consumer<String> onLineCommitted) {
        List<WelcomeDesign> designs = WelcomeDesignCatalog.designs();
        com.mapcontrol.ui.theme.FeatureGridLayout choices = new com.mapcontrol.ui.theme.FeatureGridLayout(context, 520);
        LinearLayout[] designCards = new LinearLayout[designs.size()];
        TextView[] designTitles = new TextView[designs.size()];
        androidx.appcompat.widget.AppCompatRadioButton[] buttons = new androidx.appcompat.widget.AppCompatRadioButton[designs.size()];
        String[] selected = {WelcomeDesignCatalog.find(selectedDesignId) == null
                ? WelcomeDesignCatalog.ID_OFF : selectedDesignId};
        TextView description = text(context, "", R.dimen.text_size_small, true);
        description.setTag("welcome_design_description");
        Button btnPreview = new Button(context);
        btnPreview.setTag("welcome_preview");
        btnPreview.setText(R.string.welcome_design_preview);
        UiStyles.styleOemButton(btnPreview, UiStyles.color(context, R.color.buttonPrimary));
        btnPreview.setMinHeight(dp(context, 56));
        Runnable refresh = () -> {
            for (int i = 0; i < buttons.length; i++) {
                boolean checked = designs.get(i).id().equals(selected[0]);
                buttons[i].setChecked(checked);
                paintCard(context, designCards[i], checked);
                designTitles[i].setTypeface(null, checked ? Typeface.BOLD : Typeface.NORMAL);
                designTitles[i].setTextColor(UiStyles.color(context,
                        checked ? R.color.textPrimary : R.color.textSecondary));
            }
            description.setText(WelcomeDesignCatalog.find(selected[0]).helpRes());
            boolean enabled = !WelcomeDesignCatalog.ID_OFF.equals(selected[0]);
            btnPreview.setEnabled(enabled);
            btnPreview.setAlpha(enabled ? 1f : .45f);
        };
        for (int i = 0; i < designs.size(); i++) {
            WelcomeDesign item = designs.get(i);
            LinearLayout card = new LinearLayout(context);
            card.setTag("welcome_design_" + item.id());
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(android.view.Gravity.CENTER_VERTICAL);
            card.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
            card.setMinimumHeight(dp(context, 64));
            card.setClickable(true);
            card.setFocusable(true);

            TextView title = text(context, context.getString(item.titleRes()), R.dimen.text_size_normal, false);
            title.setGravity(android.view.Gravity.CENTER_VERTICAL);
            card.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

            androidx.appcompat.widget.AppCompatRadioButton choice = new androidx.appcompat.widget.AppCompatRadioButton(context);
            choice.setButtonDrawable(R.drawable.bg_choice_control);
            choice.setButtonTintList(choiceTint(context));
            choice.setClickable(false);
            choice.setFocusable(false);
            card.addView(choice);

            final int fi = i;
            card.setOnClickListener(v -> {
                if (item.id().equals(selected[0])) return;
                selected[0] = item.id();
                refresh.run();
                onDesignCommitted.accept(item.id());
            });
            designCards[i] = card;
            designTitles[i] = title;
            buttons[i] = choice;
            choices.addCard(card);
        }
        parent.addView(choices, new LinearLayout.LayoutParams(-1, -2));
        refresh.run();
        addSpaced(context, parent, description, 8, 16);
        addLabel(context, parent, R.string.welcome_custom_text_optional_label);

        LinearLayout lineRow = new LinearLayout(context);
        lineRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        EditText lineInput = new EditText(context);
        lineInput.setTag("welcome_custom_line");
        lineInput.setHint(R.string.welcome_custom_text_hint);
        UiStyles.setTextSize(lineInput, R.dimen.text_size_normal);
        lineInput.setSingleLine(true);
        lineInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
                | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        lineInput.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
        lineInput.setMinHeight(dp(context, 56));
        lineInput.setTextColor(UiStyles.color(context, R.color.textPrimary));
        lineInput.setHintTextColor(UiStyles.color(context, R.color.textHint));
        paintCard(context, lineInput, false);
        if (customLine != null && !customLine.isEmpty()) {
            lineInput.setText(customLine);
            lineInput.setSelection(customLine.length());
        }
        lineInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                onLineCommitted.accept(s == null ? "" : s.toString());
            }
        });
        lineRow.addView(lineInput, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-2, -2);
        previewParams.setMarginStart(dp(context, 8));
        lineRow.addView(btnPreview, previewParams);
        parent.addView(lineRow, new LinearLayout.LayoutParams(-1, -2));
        return btnPreview;
    }

    private static LinearLayout section(Context context, int titleRes) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int inset = UiStyles.dimenPx(context, R.dimen.oem_card_inner_padding);
        card.setPadding(inset, inset, inset, inset);
        UiStyles.setGlassCardBackground(card);
        TextView title = text(context, context.getString(titleRes), R.dimen.text_size_large, false);
        title.setTypeface(null, Typeface.BOLD);
        addSpaced(context, card, title, 0, 16);
        return card;
    }

    private static void addLabel(Context context, LinearLayout parent, int resId) {
        addSpaced(context, parent, text(context, context.getString(resId), R.dimen.text_size_small, true), 0, 8);
    }

    private static TextView text(Context context, CharSequence label, int sizeRes, boolean secondary) {
        TextView text = new TextView(context);
        text.setText(label);
        UiStyles.setTextSize(text, sizeRes);
        text.setTextColor(UiStyles.color(context, secondary ? R.color.textSecondary : R.color.textPrimary));
        return text;
    }

    private static void addSpaced(Context context, LinearLayout parent, android.view.View child,
            int topDp, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(context, topDp), 0, dp(context, bottomDp));
        parent.addView(child, params);
    }

    private static android.content.res.ColorStateList choiceTint(Context context) {
        return new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{UiStyles.color(context, R.color.oemAccent), UiStyles.color(context, R.color.textSecondary)});
    }

    private static void paintCard(Context context, android.view.View view, boolean selected) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(16 * density);
        if (selected) {
            bg.setColor(UiStyles.color(context, R.color.assistCardActive));
            bg.setStroke(Math.round(2 * density), UiStyles.color(context, R.color.assistStrokeActive));
        } else {
            bg.setColor(UiStyles.color(context, R.color.assistCardIdle));
            bg.setStroke(Math.round(1 * density), UiStyles.color(context, R.color.outline));
        }
        view.setBackground(bg);
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
