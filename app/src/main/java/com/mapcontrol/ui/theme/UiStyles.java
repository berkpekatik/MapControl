package com.mapcontrol.ui.theme;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import androidx.core.graphics.ColorUtils;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.mapcontrol.R;

import java.util.function.Consumer;

/**
 * OEM-style surfaces, glass cards, and rounded controls shared across tab builders.
 */
public final class UiStyles {

    /** Activity onConfigurationChanged'den gelen güncel uiMode (sticky AppCompat bypass). */
    @Nullable
    private static volatile Configuration uiModeOverride;

    private UiStyles() {
    }

    /** Apply an sp dimension without applying the display scale twice. */
    public static void setTextSize(TextView view, int sizeResId) {
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                view.getResources().getDimension(sizeResId));
    }

    public static int dimenPx(Context context, int dimenResId) {
        return Math.round(context.getResources().getDimension(dimenResId));
    }

    /** MainActivity uiMode değişiminde çağrılır; renk/drawable çözümünü kilitlemez. */
    public static void setUiModeOverride(@Nullable Configuration config) {
        uiModeOverride = config != null ? new Configuration(config) : null;
    }

    /**
     * Activity {@code configChanges=uiMode} + AppCompat DayNight, Activity Resources'ı
     * bazen gece/gündüzde yapıştırır. Override veya Application uiMode kullan.
     */
    public static Context uiModeContext(Context context) {
        if (context == null) {
            return null;
        }
        Configuration override = uiModeOverride;
        if (override != null) {
            Configuration cfg = new Configuration(context.getResources().getConfiguration());
            int night = override.uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (night != 0) {
                cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
                return context.createConfigurationContext(cfg);
            }
        }
        Context app = context.getApplicationContext();
        return app != null ? app : context;
    }

    @ColorInt
    public static int color(Context context, @ColorRes int colorRes) {
        return ContextCompat.getColor(uiModeContext(context), colorRes);
    }

    @Nullable
    public static Drawable drawable(Context context, @DrawableRes int drawableRes) {
        Drawable d = ContextCompat.getDrawable(uiModeContext(context), drawableRes);
        return d != null ? d.mutate() : null;
    }

    public static void setBackgroundRes(View view, @DrawableRes int drawableRes) {
        view.setBackground(drawable(view.getContext(), drawableRes));
    }

    public static void setRootBackground(View view) {
        setBackgroundRes(view, R.drawable.bg_root_gradient);
    }

    public static void setRailPanelBackground(View view) {
        setBackgroundRes(view, R.drawable.bg_rail_panel);
    }

    public static void setGlassCardBackground(View view) {
        setBackgroundRes(view, R.drawable.bg_card_glass);
    }

    public static void setTabContentBackdrop(View view) {
        view.setBackgroundColor(color(view.getContext(), R.color.tabContentScrim));
    }

    public static void applySolidRoundedBackground(View view, int colorArgb) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(colorArgb);
        d.setCornerRadius(view.getResources().getDimension(R.dimen.oem_button_radius));
        view.setBackground(d);
    }

    public static void applySolidRoundedBackgroundDp(View view, int colorArgb, float cornerRadiusDp) {
        float density = view.getContext().getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(colorArgb);
        d.setCornerRadius(cornerRadiusDp * density);
        view.setBackground(d);
    }

    /** Choose a readable foreground for the actual button fill in either theme. */
    public static int textOnColor(int backgroundColorArgb) {
        return androidx.core.graphics.ColorUtils.calculateLuminance(backgroundColorArgb) > 0.179
                ? Color.BLACK : Color.WHITE;
    }

    public static void styleOemButton(Button button, int backgroundColorArgb) {
        applySolidRoundedBackground(button, backgroundColorArgb);
        setTextSize(button, R.dimen.text_size_normal);
        button.setTextColor(textOnColor(backgroundColorArgb));
        button.setAllCaps(false);
        int inset = dimenPx(button.getContext(), R.dimen.spacing_medium);
        button.setPadding(inset, inset / 4, inset, inset / 4);
        button.setMinHeight(dimenPx(button.getContext(), R.dimen.button_height));
        button.setMinimumHeight(dimenPx(button.getContext(), R.dimen.button_height));
    }

    /**
     * Optional real blur on supported API levels; safe no-op where unsupported.
     * Prefer glass drawables for content-heavy regions — blur affects descendants.
     */
    public static void applyOptionalBackdropBlur(View view, float radiusPx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP));
        }
    }

    public static void clearRenderEffect(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(null);
        }
    }

    /**
     * OEM-style two-segment control (hap). {@link #setLeftSelected} updates visuals and runs
     * {@code onCommit} when the value changes.
     */
    public static final class BinarySegmentHandle {
        private final boolean[] state;
        private final Runnable refresh;
        private final Consumer<Boolean> onCommit;
        private final TextView leftSeg;
        private final TextView rightSeg;
        private final TextView helpSeg;

        private BinarySegmentHandle(boolean[] state, Runnable refresh, Consumer<Boolean> onCommit,
                TextView leftSeg, TextView rightSeg, TextView helpSeg) {
            this.state = state;
            this.refresh = refresh;
            this.onCommit = onCommit;
            this.leftSeg = leftSeg;
            this.rightSeg = rightSeg;
            this.helpSeg = helpSeg;
        }

        /**
         * Programmatic selection (e.g. overlay permission denied). Fires {@code onCommit} if value changes.
         */
        public void setLeftSelected(boolean left) {
            if (state[0] == left) {
                return;
            }
            state[0] = left;
            refresh.run();
            onCommit.accept(left);
        }

        /** Updates thumb/help only; does not call {@code onCommit} (e.g. prefs sync after layout). */
        public void syncVisualWithoutCommit(boolean left) {
            if (state[0] == left) {
                return;
            }
            state[0] = left;
            refresh.run();
        }

        public void setInteractionEnabled(boolean enabled) {
            float a = enabled ? 1f : 0.5f;
            leftSeg.setEnabled(enabled);
            rightSeg.setEnabled(enabled);
            leftSeg.setAlpha(a);
            rightSeg.setAlpha(a);
            if (helpSeg != null) {
                helpSeg.setAlpha(a);
            }
        }

        public boolean isLeftSelected() {
            return state[0];
        }

        /** Light/dark sonrası segment thumb/metin renklerini yeniler. */
        public void reapplyTheme() {
            refresh.run();
        }
    }

    /** Explain only the current choice; the other choices remain visible on the control. */
    private static CharSequence buildOptionsHelp(Context context, String[] labels, String[] helps, int selected) {
        return helps[selected];
    }

    /**
     * @param title optional section title above the track
     */
    public static BinarySegmentHandle addBinarySegmentedControl(Context context, android.view.ViewGroup parent,
            @Nullable String title, String leftLabel, String rightLabel,
            String leftHelp, String rightHelp, boolean initialLeftSelected,
            Consumer<Boolean> onCommit) {
        boolean languageChoice = leftLabel.equals(context.getString(R.string.settings_language_tr))
                && rightLabel.equals(context.getString(R.string.settings_language_en));
        int gap = dimenPx(context, R.dimen.spacing_small);
        TextView heading = new TextView(context);
        heading.setText(title);
        heading.setTag("feature_toggle_title");
        setTextSize(heading, R.dimen.text_size_medium);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setTextColor(color(context, R.color.textPrimary));
        final boolean[] state = {initialLeftSelected};
        TextView help = new TextView(context);
        setTextSize(help, R.dimen.text_size_small);
        help.setTextColor(color(context, R.color.textHint));
        help.setPadding(0, gap / 2, 0, gap / 2);
        TextView left;
        TextView right;
        Runnable refresh;
        float density = context.getResources().getDisplayMetrics().density;
        if (languageChoice) {
            LinearLayout container = (parent instanceof FeatureGridLayout) ? new LinearLayout(context) : null;
            if (container != null) {
                container.setOrientation(LinearLayout.VERTICAL);
            }
            if (title != null && !title.isEmpty()) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(heading, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                if (container != null) {
                    container.addView(row);
                } else {
                    parent.addView(row);
                }
            }
            LinearLayout track = new LinearLayout(context);
            track.setOrientation(LinearLayout.HORIZONTAL);
            int langGap = Math.round(10f * density);

            LinearLayout[] langCards = new LinearLayout[2];
            androidx.appcompat.widget.AppCompatRadioButton[] langRbs = new androidx.appcompat.widget.AppCompatRadioButton[2];
            TextView[] langTvs = new TextView[2];

            String[] langLabels = {leftLabel, rightLabel};
            for (int i = 0; i < 2; i++) {
                LinearLayout card = new LinearLayout(context);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER);
                card.setPadding(Math.round(16 * density), Math.round(12 * density),
                        Math.round(16 * density), Math.round(12 * density));
                card.setMinimumHeight(Math.round(52 * density));
                card.setClickable(true);
                card.setFocusable(true);

                androidx.appcompat.widget.AppCompatRadioButton rb = new androidx.appcompat.widget.AppCompatRadioButton(context);
                rb.setButtonDrawable(R.drawable.bg_choice_control);
                rb.setFocusable(false);
                LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rbLp.setMarginEnd(Math.round(6 * density));
                card.addView(rb, rbLp);

                TextView tv = new TextView(context);
                tv.setText(langLabels[i]);
                setTextSize(tv, R.dimen.text_size_normal);
                tv.setGravity(Gravity.CENTER_VERTICAL);
                card.addView(tv, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                langCards[i] = card;
                langRbs[i] = rb;
                langTvs[i] = tv;

                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (i > 0) cardLp.setMarginStart(langGap);
                track.addView(card, cardLp);
            }

            LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            trackLp.setMargins(0, Math.round(4 * density), 0, Math.round(8 * density));
            if (container != null) {
                container.addView(track, trackLp);
                ((FeatureGridLayout) parent).addCard(container);
            } else {
                parent.addView(track, trackLp);
            }

            left = langRbs[0];
            right = langRbs[1];

            refresh = () -> {
                for (int i = 0; i < 2; i++) {
                    boolean isSelected = (i == 0) ? state[0] : !state[0];
                    langRbs[i].setChecked(isSelected);
                    tintChoice(langRbs[i], context);

                    GradientDrawable bg = new GradientDrawable();
                    bg.setCornerRadius(16 * density);
                    if (isSelected) {
                        bg.setColor(color(context, R.color.assistCardActive));
                        bg.setStroke(Math.round(2 * density), color(context, R.color.assistStrokeActive));
                        langTvs[i].setTextColor(color(context, R.color.textPrimary));
                        langTvs[i].setTypeface(null, android.graphics.Typeface.BOLD);
                    } else {
                        bg.setColor(color(context, R.color.assistCardIdle));
                        bg.setStroke(Math.round(1 * density), color(context, R.color.outline));
                        langTvs[i].setTextColor(color(context, R.color.textSecondary));
                        langTvs[i].setTypeface(null, android.graphics.Typeface.NORMAL);
                    }
                    langCards[i].setBackground(bg);
                }
            };

            langCards[0].setOnClickListener(v -> {
                if (!state[0]) {
                    state[0] = true;
                    refresh.run();
                    onCommit.accept(true);
                }
            });
            langRbs[0].setOnClickListener(v -> langCards[0].performClick());

            langCards[1].setOnClickListener(v -> {
                if (state[0]) {
                    state[0] = false;
                    refresh.run();
                    onCommit.accept(false);
                }
            });
            langRbs[1].setOnClickListener(v -> langCards[1].performClick());
        } else {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(Math.round(16 * density), Math.round(14 * density),
                    Math.round(16 * density), Math.round(14 * density));
            card.setMinimumHeight(Math.round(64 * density));
            card.setClickable(true);
            card.setFocusable(true);

            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            labelsLp.setMargins(0, 0, Math.round(12 * density), 0);
            card.addView(labels, labelsLp);

            if (title != null && !title.isEmpty()) {
                heading.setText(title);
                heading.setPadding(0, 0, 0, 0);
                heading.setVisibility(View.VISIBLE);
                labels.addView(heading);
            } else {
                heading.setVisibility(View.GONE);
            }

            help.setPadding(0, Math.round(3 * density), 0, 0);
            labels.addView(help);

            androidx.appcompat.widget.AppCompatCheckBox choice = new androidx.appcompat.widget.AppCompatCheckBox(context);
            choice.setButtonDrawable(R.drawable.bg_choice_control);
            tintChoice(choice, context);
            choice.setFocusable(false);
            choice.setClickable(false);
            card.addView(choice);

            if (parent instanceof FeatureGridLayout) {
                ((FeatureGridLayout) parent).addCard(card);
            } else {
                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cardLp.setMargins(0, Math.round(4 * density), 0, Math.round(6 * density));
                parent.addView(card, cardLp);
            }

            left = choice;
            right = choice;

            refresh = () -> {
                choice.setChecked(state[0]);
                tintChoice(choice, context);

                GradientDrawable cardBg = new GradientDrawable();
                cardBg.setCornerRadius(16 * density);
                if (state[0]) {
                    cardBg.setColor(color(context, R.color.assistCardActive));
                    cardBg.setStroke(Math.round(2 * density), color(context, R.color.assistStrokeActive));
                    heading.setTypeface(null, android.graphics.Typeface.BOLD);
                    heading.setTextColor(color(context, R.color.textPrimary));
                } else {
                    cardBg.setColor(color(context, R.color.assistCardIdle));
                    cardBg.setStroke(Math.round(1 * density), color(context, R.color.outline));
                    heading.setTypeface(null, android.graphics.Typeface.NORMAL);
                    heading.setTextColor(color(context, R.color.textSecondary));
                }
                card.setBackground(cardBg);

                help.setTextColor(color(context, R.color.textHint));
                help.setText(state[0] ? leftHelp : rightHelp);
            };

            card.setOnClickListener(v -> {
                if (choice.isEnabled()) {
                    state[0] = !state[0];
                    refresh.run();
                    onCommit.accept(state[0]);
                }
            });
        }
        refresh.run();
        return new BinarySegmentHandle(state, refresh, onCommit, left, right, help);
    }

    private static void tintChoice(android.widget.CompoundButton button, Context context) {
        int accent = color(context, R.color.oemAccent);
        int idle = color(context, R.color.textSecondary);
        androidx.core.widget.CompoundButtonCompat.setButtonTintList(button,
                new android.content.res.ColorStateList(new int[][]{
                        new int[]{-android.R.attr.state_enabled},
                        new int[]{android.R.attr.state_checked}, new int[]{}},
                        new int[]{color(context, R.color.textMuted), accent, idle}));
    }

    public static final class TernarySegmentHandle {
        private final int[] modeIds;
        private final int[] selectedIndex;
        private final Runnable refresh;
        private View[] segs;
        private TextView helpSeg;

        private TernarySegmentHandle(int[] modeIds, int[] selectedIndex, Runnable refresh) {
            this.modeIds = modeIds;
            this.selectedIndex = selectedIndex;
            this.refresh = refresh;
        }

        private void bindViews(View[] segs, TextView helpSeg) {
            this.segs = segs;
            this.helpSeg = helpSeg;
        }

        public void setInteractionEnabled(boolean enabled) {
            float a = enabled ? 1f : 0.5f;
            if (segs != null) {
                for (View tv : segs) {
                    tv.setEnabled(enabled);
                    if (tv instanceof android.view.ViewGroup) {
                        android.view.ViewGroup card = (android.view.ViewGroup) tv;
                        for (int i = 0; i < card.getChildCount(); i++) card.getChildAt(i).setEnabled(enabled);
                    }
                    tv.setAlpha(a);
                }
            }
            if (helpSeg != null) {
                helpSeg.setAlpha(a);
            }
        }

        /** Sync UI from persisted mode id without calling {@code onCommit}. */
        public void syncVisualFromModeId(int modeId) {
            int idx = -1;
            for (int i = 0; i < modeIds.length; i++) {
                if (modeIds[i] == modeId) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0) {
                idx = 0;
            }
            selectedIndex[0] = idx;
            refresh.run();
        }

        public int getSelectedModeId() {
            return modeIds[selectedIndex[0]];
        }
    }

    /**
     * Three-segment control; {@code modeIds} order must match {@code labels} / {@code helps} (e.g. 2, 1, 0).
     */
    public static TernarySegmentHandle addTernarySegmentedControl(Context context, LinearLayout parent,
            @Nullable String title,
            String[] labels, String[] helps,
            int[] modeIds,
            int initialModeId,
            Consumer<Integer> onCommit) {
        if (labels.length != 3 || helps.length != 3 || modeIds.length != 3) {
            throw new IllegalArgumentException("labels, helps, modeIds must have length 3");
        }

        if (title != null && !title.isEmpty()) {
            TextView rowTitle = new TextView(context);
            rowTitle.setText(title);
            setTextSize(rowTitle, R.dimen.text_size_medium);
            rowTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
            rowTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            int hPad = dimenPx(context, R.dimen.spacing_medium);
            rowTitle.setPadding(hPad, dimenPx(context, R.dimen.spacing_medium), hPad,
                    dimenPx(context, R.dimen.spacing_small));
            parent.addView(rowTitle, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout track = new LinearLayout(context);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackgroundColor(Color.TRANSPARENT);
        track.setPadding(0, 0, 0, 0);

        LinearLayout[] cards = new LinearLayout[3];
        androidx.appcompat.widget.AppCompatRadioButton[] radioButtons = new androidx.appcompat.widget.AppCompatRadioButton[3];
        TextView[] labelViews = new TextView[3];

        float density = context.getResources().getDisplayMetrics().density;
        int cornerRadius = Math.round(16f * density);
        int gap = Math.round(10f * density);

        for (int i = 0; i < 3; i++) {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(Math.round(14 * density), Math.round(12 * density),
                    Math.round(14 * density), Math.round(12 * density));
            card.setMinimumHeight(Math.round(52 * density));
            card.setClickable(true);
            card.setFocusable(true);

            androidx.appcompat.widget.AppCompatRadioButton rb = new androidx.appcompat.widget.AppCompatRadioButton(context);
            rb.setButtonDrawable(R.drawable.bg_choice_control);
            rb.setFocusable(false);
            LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rbLp.setMarginEnd(Math.round(6f * density));
            card.addView(rb, rbLp);

            TextView tv = new TextView(context);
            tv.setText(labels[i]);
            setTextSize(tv, R.dimen.text_size_normal);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setMaxLines(2);
            card.addView(tv, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            cards[i] = card;
            radioButtons[i] = rb;
            labelViews[i] = tv;
        }

        TextView help = new TextView(context);
        setTextSize(help, R.dimen.text_size_small);
        help.setTextColor(UiStyles.color(context, R.color.textHint));
        int hPad = dimenPx(context, R.dimen.spacing_medium);
        help.setPadding(hPad, dimenPx(context, R.dimen.spacing_small), hPad,
                dimenPx(context, R.dimen.spacing_medium));

        int initialIndex = 0;
        for (int i = 0; i < 3; i++) {
            if (modeIds[i] == initialModeId) {
                initialIndex = i;
                break;
            }
        }

        final int[] selectedIndex = {initialIndex};
        Runnable refresh = () -> {
            int idx = selectedIndex[0];
            for (int i = 0; i < 3; i++) {
                boolean isSelected = (i == idx);
                androidx.appcompat.widget.AppCompatRadioButton rb = radioButtons[i];
                TextView tv = labelViews[i];
                LinearLayout card = cards[i];

                rb.setChecked(isSelected);
                tintChoice(rb, context);

                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(cornerRadius);

                if (isSelected) {
                    bg.setColor(color(context, R.color.assistCardActive));
                    bg.setStroke(Math.round(2 * density), color(context, R.color.assistStrokeActive));
                    tv.setTextColor(color(context, R.color.textPrimary));
                    tv.setTypeface(null, android.graphics.Typeface.BOLD);
                } else {
                    bg.setColor(color(context, R.color.assistCardIdle));
                    bg.setStroke(Math.round(1 * density), color(context, R.color.outline));
                    tv.setTextColor(color(context, R.color.textSecondary));
                    tv.setTypeface(null, android.graphics.Typeface.NORMAL);
                }
                card.setBackground(bg);
            }
            help.setText(buildOptionsHelp(context, labels, helps, idx));
        };

        for (int i = 0; i < 3; i++) {
            final int fi = i;
            cards[i].setOnClickListener(v -> {
                if (cards[fi].isEnabled() && selectedIndex[0] != fi) {
                    selectedIndex[0] = fi;
                    refresh.run();
                    onCommit.accept(modeIds[fi]);
                }
            });
            radioButtons[i].setOnClickListener(v -> cards[fi].performClick());
        }
        refresh.run();

        for (int i = 0; i < 3; i++) {
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) {
                cardLp.setMarginStart(gap);
            }
            track.addView(cards[i], cardLp);
        }

        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        trackLp.setMargins(0, 0, 0, 0);
        parent.addView(track, trackLp);
        help.setPadding(0, dimenPx(context, R.dimen.spacing_small), 0,
                dimenPx(context, R.dimen.spacing_small));
        parent.addView(help, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TernarySegmentHandle handle = new TernarySegmentHandle(modeIds, selectedIndex, refresh);
        handle.bindViews(cards, help);
        return handle;
    }

    /** Tinted start drawable for {@link Button} labels (Material-style icons as vector drawables). */
    public static void setButtonStartIconTinted(Button button, @DrawableRes int iconRes, int colorArgb, int drawablePaddingPx) {
        Drawable d = ContextCompat.getDrawable(button.getContext(), iconRes);
        if (d != null) {
            d = DrawableCompat.wrap(d.mutate());
            DrawableCompat.setTint(d, colorArgb);
        }
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(d, null, null, null);
        button.setCompoundDrawablePadding(drawablePaddingPx);
    }

    public static void setButtonIconOnlyTinted(Button button, @DrawableRes int iconRes, int colorArgb, CharSequence contentDescription) {
        button.setText("");
        button.setContentDescription(contentDescription);
        setButtonStartIconTinted(button, iconRes, colorArgb, 0);
    }

    public static void clearButtonCompoundDrawables(Button button) {
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        button.setCompoundDrawablePadding(0);
    }
}
