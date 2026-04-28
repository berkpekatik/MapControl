package com.mapcontrol.ui.theme;

import android.content.Context;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

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

    private UiStyles() {
    }

    public static int dimenPx(Context context, int dimenResId) {
        return Math.round(context.getResources().getDimension(dimenResId));
    }

    public static void setRootBackground(View view) {
        view.setBackgroundResource(R.drawable.bg_root_gradient);
    }

    public static void setRailPanelBackground(View view) {
        view.setBackgroundResource(R.drawable.bg_rail_panel);
    }

    public static void setGlassCardBackground(View view) {
        view.setBackgroundResource(R.drawable.bg_card_glass);
    }

    public static void setTabContentBackdrop(View view) {
        view.setBackgroundColor(ContextCompat.getColor(view.getContext(), R.color.tabContentScrim));
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

    public static void styleOemButton(Button button, int backgroundColorArgb) {
        applySolidRoundedBackground(button, backgroundColorArgb);
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
    }

    /**
     * @param title optional section title above the track
     */
    public static BinarySegmentHandle addBinarySegmentedControl(Context context, LinearLayout parent,
            @Nullable String title,
            String leftLabel, String rightLabel,
            String leftHelp, String rightHelp,
            boolean initialLeftSelected,
            Consumer<Boolean> onCommit) {
        if (title != null && !title.isEmpty()) {
            TextView rowTitle = new TextView(context);
            rowTitle.setText(title);
            rowTitle.setTextSize(16);
            rowTitle.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
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
        track.setBackgroundResource(R.drawable.bg_segment_track);
        int tpad = dimenPx(context, R.dimen.spacing_tiny);
        track.setPadding(tpad, tpad, tpad, tpad);

        TextView left = new TextView(context);
        TextView right = new TextView(context);
        left.setText(leftLabel);
        right.setText(rightLabel);
        left.setTextSize(15);
        right.setTextSize(15);
        left.setGravity(Gravity.CENTER);
        right.setGravity(Gravity.CENTER);
        left.setMinHeight(dimenPx(context, R.dimen.button_height));
        right.setMinHeight(dimenPx(context, R.dimen.button_height));

        TextView help = new TextView(context);
        help.setTextSize(13);
        help.setTextColor(ContextCompat.getColor(context, R.color.textHint));
        int hPad = dimenPx(context, R.dimen.spacing_medium);
        help.setPadding(hPad, dimenPx(context, R.dimen.spacing_small), hPad,
                dimenPx(context, R.dimen.spacing_medium));

        final boolean[] state = {initialLeftSelected};
        Runnable refresh = () -> {
            boolean on = state[0];
            if (on) {
                left.setBackgroundResource(R.drawable.bg_segment_thumb);
                left.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
                left.setTypeface(null, android.graphics.Typeface.BOLD);
                right.setBackgroundColor(Color.TRANSPARENT);
                right.setTextColor(ContextCompat.getColor(context, R.color.textSecondary));
                right.setTypeface(null, android.graphics.Typeface.NORMAL);
                help.setText(leftHelp);
            } else {
                right.setBackgroundResource(R.drawable.bg_segment_thumb);
                right.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
                right.setTypeface(null, android.graphics.Typeface.BOLD);
                left.setBackgroundColor(Color.TRANSPARENT);
                left.setTextColor(ContextCompat.getColor(context, R.color.textSecondary));
                left.setTypeface(null, android.graphics.Typeface.NORMAL);
                help.setText(rightHelp);
            }
        };

        left.setOnClickListener(v -> {
            if (!left.isEnabled()) {
                return;
            }
            if (!state[0]) {
                state[0] = true;
                refresh.run();
                onCommit.accept(true);
            }
        });
        right.setOnClickListener(v -> {
            if (!right.isEnabled()) {
                return;
            }
            if (state[0]) {
                state[0] = false;
                refresh.run();
                onCommit.accept(false);
            }
        });
        refresh.run();

        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        track.addView(left, segLp);
        track.addView(right, segLp);

        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        trackLp.setMargins(hPad, 0, hPad, 0);
        parent.addView(track, trackLp);
        parent.addView(help, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        return new BinarySegmentHandle(state, refresh, onCommit, left, right, help);
    }

    public static final class TernarySegmentHandle {
        private final int[] modeIds;
        private final int[] selectedIndex;
        private final Runnable refresh;

        private TernarySegmentHandle(int[] modeIds, int[] selectedIndex, Runnable refresh) {
            this.modeIds = modeIds;
            this.selectedIndex = selectedIndex;
            this.refresh = refresh;
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
            rowTitle.setTextSize(16);
            rowTitle.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
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
        track.setBackgroundResource(R.drawable.bg_segment_track);
        int tpad = dimenPx(context, R.dimen.spacing_tiny);
        track.setPadding(tpad, tpad, tpad, tpad);

        TextView[] segs = new TextView[3];
        for (int i = 0; i < 3; i++) {
            TextView tv = new TextView(context);
            tv.setText(labels[i]);
            tv.setTextSize(14);
            tv.setGravity(Gravity.CENTER);
            tv.setMinHeight(dimenPx(context, R.dimen.button_height));
            tv.setMaxLines(2);
            segs[i] = tv;
        }

        TextView help = new TextView(context);
        help.setTextSize(13);
        help.setTextColor(ContextCompat.getColor(context, R.color.textHint));
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
                TextView tv = segs[i];
                if (i == idx) {
                    tv.setBackgroundResource(R.drawable.bg_segment_thumb);
                    tv.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
                    tv.setTypeface(null, android.graphics.Typeface.BOLD);
                } else {
                    tv.setBackgroundColor(Color.TRANSPARENT);
                    tv.setTextColor(ContextCompat.getColor(context, R.color.textSecondary));
                    tv.setTypeface(null, android.graphics.Typeface.NORMAL);
                }
            }
            help.setText(helps[idx]);
        };

        for (int i = 0; i < 3; i++) {
            final int fi = i;
            segs[i].setOnClickListener(v -> {
                if (selectedIndex[0] != fi) {
                    selectedIndex[0] = fi;
                    refresh.run();
                    onCommit.accept(modeIds[fi]);
                }
            });
        }
        refresh.run();

        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        for (TextView tv : segs) {
            track.addView(tv, segLp);
        }

        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        trackLp.setMargins(hPad, 0, hPad, 0);
        parent.addView(track, trackLp);
        parent.addView(help, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        return new TernarySegmentHandle(modeIds, selectedIndex, refresh);
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
