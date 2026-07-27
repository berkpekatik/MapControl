package com.mapcontrol.ui.builder;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

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

        Screen(ScrollView scrollView, TextView tvFilePath, Button btnSelectFile,
                Button btnPlay, Button btnStop) {
            this.scrollView = scrollView;
            this.tvFilePath = tvFilePath;
            this.btnSelectFile = btnSelectFile;
            this.btnPlay = btnPlay;
            this.btnStop = btnStop;
        }
    }

    public static Screen buildTabScrollView(Context context, boolean autoPlayInitial,
            Consumer<Boolean> onAutoPlayCommitted) {
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

        scrollView.addView(mainContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        return new Screen(scrollView, tvFilePath, btnSelectFile, btnPlay, btnStop);
    }
}
