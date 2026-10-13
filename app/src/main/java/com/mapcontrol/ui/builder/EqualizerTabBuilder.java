package com.mapcontrol.ui.builder;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.mapcontrol.R;
import com.mapcontrol.audio.AudioEqualizerController;
import com.mapcontrol.ui.theme.UiStyles;

/**
 * Ekolayzer sekmesi. Cam kart, parçalı anahtar ve kaydırıcılar diğer sekmelerle aynı dili kullanır.
 */
public class EqualizerTabBuilder {

    private static final String[] PRESET_IDS = {
            AudioEqualizerController.PRESET_FLAT,
            AudioEqualizerController.PRESET_BASS,
            AudioEqualizerController.PRESET_VOCAL,
            AudioEqualizerController.PRESET_TREBLE
    };

    private final Context context;
    private ScrollView scrollView;
    private boolean enabled;
    private int gainMb;
    private int minLevelMb;
    private int maxLevelMb;
    private int[] centerHz = new int[0];
    private int[] bandLevels = new int[0];
    private String preset = AudioEqualizerController.PRESET_FLAT;
    private boolean updatingSliders;
    private TextView gainValue;
    private SeekBar gainBar;
    private TextView[] presetChips;
    private TextView[] bandValues;
    private SeekBar[] bandBars;

    public EqualizerTabBuilder(Context context) {
        this.context = context;
        build();
    }

    public void rebuild() {
        build();
    }

    public ScrollView getScrollView() {
        return scrollView;
    }

    public ScrollView build() {
        AudioEqualizerController.State state = AudioEqualizerController.read(context);
        enabled = state.enabled;
        gainMb = state.gainMb;
        minLevelMb = state.minLevelMb;
        maxLevelMb = state.maxLevelMb;
        centerHz = state.centerHz;
        bandLevels = state.bands;
        preset = state.preset;

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

        outer.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        addSectionTitle(card, R.string.equalizer_title);
        addSectionDesc(card, R.string.equalizer_desc);

        if (!state.supported) {
            addSectionDesc(card, R.string.equalizer_unsupported);
            return scrollView;
        }

        UiStyles.addBinarySegmentedControl(context, card,
                null,
                context.getString(R.string.common_on),
                context.getString(R.string.common_off),
                context.getString(R.string.equalizer_help_on),
                context.getString(R.string.equalizer_help_off),
                enabled,
                this::onEnabledChanged);

        addSectionTitle(card, R.string.equalizer_gain_title);
        gainValue = addValueLabel(card, formatDb(gainMb, false));
        gainBar = addSeekBar(card, AudioEqualizerController.MAX_GAIN_MB, gainMb, new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || updatingSliders) {
                    return;
                }
                gainMb = progress;
                gainValue.setText(formatDb(gainMb, false));
                AudioEqualizerController.preview(context, gainMb, bandLevels);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                commit(preset);
            }
        });

        addSectionTitle(card, R.string.equalizer_presets_title);
        addPresetRow(card);

        addSectionTitle(card, R.string.equalizer_bands_title);
        addBandRows(card);
        return scrollView;
    }

    private void onEnabledChanged(boolean nowEnabled) {
        enabled = nowEnabled;
        commit(preset);
    }

    private void onPresetSelected(String presetId) {
        int[] curve = AudioEqualizerController.bandsForPreset(
                presetId, centerHz, minLevelMb, maxLevelMb);
        if (curve.length == bandLevels.length) {
            bandLevels = curve;
        }
        preset = presetId;
        pushSliders();
        stylePresetChips();
        commit(presetId);
    }

    private void commit(String presetId) {
        preset = presetId;
        AudioEqualizerController.update(context, enabled, gainMb, bandLevels, presetId);
        stylePresetChips();
    }

    private void addPresetRow(LinearLayout parent) {
        LinearLayout track = new LinearLayout(context);
        track.setOrientation(LinearLayout.HORIZONTAL);
        UiStyles.setBackgroundRes(track, R.drawable.bg_segment_track);
        int pad = UiStyles.dimenPx(context, R.dimen.spacing_tiny);
        track.setPadding(pad, pad, pad, pad);

        int[] labels = {
                R.string.equalizer_preset_flat,
                R.string.equalizer_preset_bass,
                R.string.equalizer_preset_vocal,
                R.string.equalizer_preset_treble
        };
        presetChips = new TextView[PRESET_IDS.length];
        for (int i = 0; i < PRESET_IDS.length; i++) {
            TextView chip = new TextView(context);
            chip.setText(labels[i]);
            chip.setTextSize(15);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(UiStyles.dimenPx(context, R.dimen.button_height));
            chip.setPadding(pad, pad, pad, pad);
            final String presetId = PRESET_IDS[i];
            chip.setOnClickListener(v -> onPresetSelected(presetId));
            track.addView(chip, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            presetChips[i] = chip;
        }
        stylePresetChips();

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        int h = UiStyles.dimenPx(context, R.dimen.spacing_medium);
        lp.setMargins(h, 0, h, UiStyles.dimenPx(context, R.dimen.spacing_medium));
        parent.addView(track, lp);
    }

    private void stylePresetChips() {
        if (presetChips == null) {
            return;
        }
        for (int i = 0; i < presetChips.length; i++) {
            boolean selected = PRESET_IDS[i].equals(preset);
            TextView chip = presetChips[i];
            if (selected) {
                UiStyles.setBackgroundRes(chip, R.drawable.bg_segment_thumb);
                chip.setTextColor(UiStyles.color(context, R.color.textPrimary));
                chip.setTypeface(null, Typeface.BOLD);
            } else {
                chip.setBackgroundColor(Color.TRANSPARENT);
                chip.setTextColor(UiStyles.color(context, R.color.textSecondary));
                chip.setTypeface(null, Typeface.NORMAL);
            }
        }
    }

    private void addBandRows(LinearLayout parent) {
        bandValues = new TextView[bandLevels.length];
        bandBars = new SeekBar[bandLevels.length];
        int span = Math.max(1, maxLevelMb - minLevelMb);
        for (int i = 0; i < bandLevels.length; i++) {
            final int index = i;
            TextView hz = new TextView(context);
            hz.setText(formatHz(i < centerHz.length ? centerHz[i] : 0));
            hz.setTextSize(15);
            hz.setTextColor(UiStyles.color(context, R.color.textPrimary));
            hz.setTypeface(null, Typeface.BOLD);
            int h = UiStyles.dimenPx(context, R.dimen.spacing_medium);
            hz.setPadding(h, UiStyles.dimenPx(context, R.dimen.spacing_small), h, 0);
            parent.addView(hz, matchWrap());

            bandValues[i] = addValueLabel(parent, formatDb(bandLevels[i], true));
            int progress = bandLevels[i] - minLevelMb;
            bandBars[i] = addSeekBar(parent, span, progress, new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                    if (!fromUser || updatingSliders) {
                        return;
                    }
                    bandLevels[index] = minLevelMb + value;
                    bandValues[index].setText(formatDb(bandLevels[index], true));
                    if (!AudioEqualizerController.PRESET_CUSTOM.equals(preset)) {
                        preset = AudioEqualizerController.PRESET_CUSTOM;
                        stylePresetChips();
                    }
                    AudioEqualizerController.preview(context, gainMb, bandLevels);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    commit(preset);
                }
            });
        }
    }

    private void pushSliders() {
        updatingSliders = true;
        if (gainBar != null) {
            gainBar.setProgress(gainMb);
        }
        if (gainValue != null) {
            gainValue.setText(formatDb(gainMb, false));
        }
        if (bandBars != null) {
            for (int i = 0; i < bandBars.length && i < bandLevels.length; i++) {
                bandBars[i].setProgress(bandLevels[i] - minLevelMb);
                if (bandValues != null && i < bandValues.length) {
                    bandValues[i].setText(formatDb(bandLevels[i], true));
                }
            }
        }
        updatingSliders = false;
    }

    private SeekBar addSeekBar(LinearLayout parent, int max, int progress,
            SeekBar.OnSeekBarChangeListener listener) {
        SeekBar bar = new SeekBar(context);
        bar.setMax(Math.max(1, max));
        bar.setProgress(Math.max(0, Math.min(progress, bar.getMax())));
        int accent = UiStyles.color(context, R.color.oemAccent);
        int thumbPx = UiStyles.dimenPx(context, R.dimen.equalizer_thumb_size);
        int trackPx = UiStyles.dimenPx(context, R.dimen.equalizer_track_height);
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(accent);
        thumb.setSize(thumbPx, thumbPx);
        bar.setThumb(thumb);
        bar.setThumbOffset(thumbPx / 2);
        bar.setSplitTrack(false);
        bar.setMinHeight(trackPx);
        bar.setMaxHeight(trackPx);
        bar.setProgressTintList(ColorStateList.valueOf(accent));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(
                UiStyles.color(context, R.color.buttonInactive)));
        bar.setThumbTintList(ColorStateList.valueOf(accent));
        int side = thumbPx / 2;
        bar.setPadding(side, 0, side, 0);
        bar.setOnSeekBarChangeListener(listener);
        int h = UiStyles.dimenPx(context, R.dimen.spacing_medium);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UiStyles.dimenPx(context, R.dimen.equalizer_slider_height));
        lp.setMargins(h, 0, h, UiStyles.dimenPx(context, R.dimen.spacing_small));
        parent.addView(bar, lp);
        return bar;
    }

    private TextView addValueLabel(LinearLayout parent, String text) {
        TextView value = new TextView(context);
        value.setText(text);
        value.setTextSize(14);
        value.setTextColor(UiStyles.color(context, R.color.textSecondary));
        int h = UiStyles.dimenPx(context, R.dimen.spacing_medium);
        value.setPadding(h, 0, h, 0);
        parent.addView(value, matchWrap());
        return value;
    }

    private void addSectionTitle(LinearLayout parent, int textRes) {
        TextView title = new TextView(context);
        title.setText(textRes);
        title.setTextSize(18);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(16, 24, 16, 8);
        parent.addView(title, matchWrap());
    }

    private void addSectionDesc(LinearLayout parent, int textRes) {
        TextView desc = new TextView(context);
        desc.setText(textRes);
        desc.setTextSize(13);
        desc.setTextColor(UiStyles.color(context, R.color.textHint));
        desc.setPadding(16, 0, 16, 12);
        parent.addView(desc, matchWrap());
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private String formatHz(int hz) {
        if (hz >= 1000) {
            if (hz % 1000 == 0) {
                return context.getString(R.string.equalizer_band_khz_whole, hz / 1000);
            }
            return context.getString(R.string.equalizer_band_khz, hz / 1000f);
        }
        return context.getString(R.string.equalizer_band_hz, hz);
    }

    private String formatDb(int milliBel, boolean signed) {
        float db = milliBel / 100f;
        if (signed) {
            return context.getString(R.string.equalizer_level_db, db);
        }
        return context.getString(R.string.equalizer_gain_db, db);
    }
}
