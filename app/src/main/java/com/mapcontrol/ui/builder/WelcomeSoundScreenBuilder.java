package com.mapcontrol.ui.builder;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
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
        public final TextView tvFilePath;
        public final Button btnSelectFile;
        public final Button btnPlay;
        public final Button btnStop;
        public final Button btnPreview;

        Screen(ScrollView scrollView, TextView tvFilePath, Button btnSelectFile,
                Button btnPlay, Button btnStop, Button btnPreview) {
            this.scrollView = scrollView;
            this.tvFilePath = tvFilePath;
            this.btnSelectFile = btnSelectFile;
            this.btnPlay = btnPlay;
            this.btnStop = btnStop;
            this.btnPreview = btnPreview;
        }
    }

    public static Screen buildTabScrollView(Context context, boolean autoPlayInitial,
            Consumer<Boolean> onAutoPlayCommitted, String selectedDesignId,
            Consumer<String> onDesignCommitted, String customLine,
            Consumer<String> onLineCommitted) {
        int primary = UiStyles.color(context, R.color.textPrimary);
        int secondary = UiStyles.color(context, R.color.textSecondary);
        int padSmall = UiStyles.dimenPx(context, R.dimen.spacing_small);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(UiStyles.color(context, R.color.backgroundPage));

        LinearLayout mainContainer = new LinearLayout(context);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        int margin = UiStyles.dimenPx(context, R.dimen.oem_card_margin);
        mainContainer.setPadding(margin, margin, margin, margin);
        mainContainer.setBackgroundColor(UiStyles.color(context, R.color.backgroundPage));

        TextView titleText = new TextView(context);
        titleText.setText(R.string.welcome_sound_title);
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        titleText.setTextColor(primary);
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        titleText.setPadding(0, 0, 0, 24);
        mainContainer.addView(titleText);

        Button btnSelectFile = new Button(context);
        btnSelectFile.setText(R.string.welcome_sound_choose_file);
        btnSelectFile.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btnSelectFile.setTextColor(primary);
        UiStyles.styleOemButton(btnSelectFile, UiStyles.color(context, R.color.buttonPrimary));
        btnSelectFile.setPadding(24, 16, 24, 16);
        UiStyles.setButtonStartIconTinted(btnSelectFile, R.drawable.ic_mdi_folder,
                primary, padSmall);
        LinearLayout.LayoutParams selectBtnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        selectBtnParams.setMargins(0, 0, 0, 16);
        mainContainer.addView(btnSelectFile, selectBtnParams);

        TextView filePathLabel = new TextView(context);
        filePathLabel.setText(R.string.welcome_sound_selected_label);
        filePathLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        filePathLabel.setTextColor(secondary);
        filePathLabel.setPadding(0, 0, 0, 8);
        mainContainer.addView(filePathLabel);

        TextView tvFilePath = new TextView(context);
        tvFilePath.setText(R.string.welcome_sound_no_file_yet);
        tvFilePath.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvFilePath.setTextColor(primary);
        tvFilePath.setPadding(16, 12, 16, 12);
        android.graphics.drawable.GradientDrawable filePathBg = new android.graphics.drawable.GradientDrawable();
        filePathBg.setColor(UiStyles.color(context, R.color.surfaceCard));
        filePathBg.setCornerRadius(context.getResources().getDimension(R.dimen.oem_button_radius));
        filePathBg.setStroke(1, UiStyles.color(context, R.color.outline));
        tvFilePath.setBackground(filePathBg);
        LinearLayout.LayoutParams filePathParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        filePathParams.setMargins(0, 0, 0, 24);
        mainContainer.addView(tvFilePath, filePathParams);

        LinearLayout buttonsContainer = new LinearLayout(context);
        buttonsContainer.setOrientation(LinearLayout.HORIZONTAL);

        Button btnPlay = new Button(context);
        btnPlay.setText(R.string.welcome_sound_play);
        btnPlay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btnPlay.setTextColor(primary);
        UiStyles.styleOemButton(btnPlay, UiStyles.color(context, R.color.buttonSuccessBright));
        btnPlay.setPadding(24, 16, 24, 16);
        btnPlay.setEnabled(false);
        LinearLayout.LayoutParams playBtnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        playBtnParams.setMargins(0, 0, 8, 0);
        buttonsContainer.addView(btnPlay, playBtnParams);

        Button btnStop = new Button(context);
        btnStop.setText(R.string.welcome_sound_stop);
        btnStop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btnStop.setTextColor(primary);
        UiStyles.styleOemButton(btnStop, UiStyles.color(context, R.color.statusErrorBright));
        btnStop.setPadding(24, 16, 24, 16);
        btnStop.setEnabled(false);
        LinearLayout.LayoutParams stopBtnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        buttonsContainer.addView(btnStop, stopBtnParams);

        mainContainer.addView(buttonsContainer);

        TextView autoPlayTitle = new TextView(context);
        autoPlayTitle.setText(R.string.welcome_sound_autoplay_title);
        autoPlayTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        autoPlayTitle.setTextColor(UiStyles.color(context, R.color.textPrimary87));
        autoPlayTitle.setPadding(0, 24, 0, 8);
        mainContainer.addView(autoPlayTitle);

        TextView autoPlayDesc = new TextView(context);
        autoPlayDesc.setText(R.string.welcome_sound_autoplay_desc);
        autoPlayDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        autoPlayDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        autoPlayDesc.setPadding(0, 0, 0, 12);
        mainContainer.addView(autoPlayDesc);

        LinearLayout autoPlayBlock = new LinearLayout(context);
        autoPlayBlock.setOrientation(LinearLayout.VERTICAL);
        UiStyles.addBinarySegmentedControl(context, autoPlayBlock,
                null,
                context.getString(R.string.welcome_sound_autoplay_on),
                context.getString(R.string.welcome_sound_autoplay_off),
                context.getString(R.string.welcome_sound_autoplay_on_help),
                context.getString(R.string.welcome_sound_autoplay_off_help),
                autoPlayInitial,
                onAutoPlayCommitted);
        mainContainer.addView(autoPlayBlock);

        Button btnPreview = addDesignPicker(context, mainContainer, selectedDesignId,
                onDesignCommitted, customLine, onLineCommitted);

        scrollView.addView(mainContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        return new Screen(scrollView, tvFilePath, btnSelectFile, btnPlay, btnStop, btnPreview);
    }

    private static Button addDesignPicker(Context context, LinearLayout parent,
            String selectedDesignId, Consumer<String> onDesignCommitted,
            String customLine, Consumer<String> onLineCommitted) {
        int primary = UiStyles.color(context, R.color.textPrimary);
        int secondary = UiStyles.color(context, R.color.textSecondary);

        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(R.string.welcome_design_section_title);
        sectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sectionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary87));
        sectionTitle.setPadding(0, 32, 0, 8);
        parent.addView(sectionTitle);

        TextView sectionDesc = new TextView(context);
        sectionDesc.setText(R.string.welcome_design_section_desc);
        sectionDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        sectionDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        sectionDesc.setPadding(0, 0, 0, 12);
        parent.addView(sectionDesc);

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

        TextView lineLabel = new TextView(context);
        lineLabel.setText(R.string.welcome_custom_text_label);
        lineLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        lineLabel.setTextColor(secondary);
        lineLabel.setPadding(0, 16, 0, 8);
        parent.addView(lineLabel);

        EditText lineInput = new EditText(context);
        lineInput.setHint(R.string.welcome_custom_text_hint);
        lineInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        lineInput.setSingleLine(true);
        lineInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
                | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        lineInput.setPadding(16, 16, 16, 16);
        lineInput.setTextColor(primary);
        lineInput.setHintTextColor(UiStyles.color(context, R.color.textHint));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(UiStyles.color(context, R.color.surfaceColor));
        inputBg.setCornerRadius(context.getResources().getDimension(R.dimen.oem_button_radius));
        inputBg.setStroke(1, UiStyles.color(context, R.color.outlineMuted));
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
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lineParams.setMargins(0, 0, 0, 8);
        parent.addView(lineInput, lineParams);

        TextView lineHelp = new TextView(context);
        lineHelp.setText(R.string.welcome_custom_text_help);
        lineHelp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        lineHelp.setTextColor(UiStyles.color(context, R.color.textHint));
        lineHelp.setPadding(0, 0, 0, 8);
        parent.addView(lineHelp);

        Button btnPreview = new Button(context);
        btnPreview.setText(R.string.welcome_design_preview);
        btnPreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btnPreview.setTextColor(primary);
        UiStyles.styleOemButton(btnPreview, UiStyles.color(context, R.color.buttonPrimary));
        btnPreview.setPadding(24, 16, 24, 16);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        previewParams.setMargins(0, 8, 0, 0);
        parent.addView(btnPreview, previewParams);
        return btnPreview;
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
}
