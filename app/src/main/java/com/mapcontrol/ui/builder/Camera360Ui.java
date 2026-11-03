package com.mapcontrol.ui.builder;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

import java.util.function.IntConsumer;

/** Kamera 360 ekranlarinin ortak gorsel yapi taslari (kart, hap, baslik, secici). */
final class Camera360Ui {
    static final int COLOR_REC = 0xFFE53935;
    static final int COLOR_REC_PRESSED = 0xFFC62828;
    static final int COLOR_LIVE = 0xFF43A047;
    static final int COLOR_WAIT = 0xFFFFB300;
    static final int COLOR_IDLE = 0xFF9E9E9E;
    static final int COLOR_FPS = 0xFF7CFC9A;
    static final int COLOR_NO_SIGNAL = 0xFFFFD54F;
    static final int OVERLAY_BG = 0xB3101418;
    /** Kayit ekranlari bu genislikten itibaren iki sutun. */
    static final int WIDE_LAYOUT_DP = 900;

    final Context context;

    Camera360Ui(Context context) {
        this.context = context;
    }

    boolean isWide() {
        return context.getResources().getConfiguration().screenWidthDp >= WIDE_LAYOUT_DP;
    }

    int dp(float v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }

    int themeColor(int colorRes) {
        return UiStyles.color(context, colorRes);
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    GradientDrawable roundRect(int fill, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    static GradientDrawable oval(int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        return d;
    }

    /** Icerik arka planinin uzerine dokunma dalgasi (ripple) ekler ve gorunumu tiklanabilir yapar. */
    void setPressableBackground(View view, Drawable content, float radiusDp) {
        int ripple = withAlpha(themeColor(R.color.textPrimary), 0x2E);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), content,
                roundRect(Color.WHITE, radiusDp)));
        view.setClickable(true);
    }

    LinearLayout card() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = roundRect(themeColor(R.color.surfaceCard), 20);
        bg.setStroke(dp(1), themeColor(R.color.outline));
        card.setBackground(bg);
        return card;
    }

    TextView sectionLabel(int textRes) {
        TextView tv = new TextView(context);
        tv.setText(textRes);
        tv.setTextSize(11);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(0.08f);
        tv.setAllCaps(true);
        tv.setTextColor(themeColor(R.color.textSecondaryCool));
        return tv;
    }

    TextView text(float sizeSp, int colorRes, boolean bold) {
        TextView tv = new TextView(context);
        tv.setTextSize(sizeSp);
        tv.setTextColor(themeColor(colorRes));
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    FrameLayout iconBadge(int iconRes, int tint, int fill, int sizeDp, int iconDp, boolean circle) {
        FrameLayout badge = new FrameLayout(context);
        badge.setBackground(circle ? oval(fill) : roundRect(fill, sizeDp * 0.3f));
        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setColorFilter(tint);
        badge.addView(icon, new FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER));
        badge.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return badge;
    }

    /** Yuvarlak, hafif zeminli ikon butonu. */
    ImageView iconButton(int iconRes, int sizeDp, View.OnClickListener click) {
        ImageView b = new ImageView(context);
        b.setImageResource(iconRes);
        b.setColorFilter(themeColor(R.color.textPrimary));
        int p = Math.round(sizeDp * 0.22f);
        b.setPadding(dp(p), dp(p), dp(p), dp(p));
        setPressableBackground(b, oval(themeColor(R.color.surfaceCardInner)), sizeDp / 2f);
        b.setOnClickListener(click);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return b;
    }

    /** Metnin basina renklendirilebilir durum noktasi koyar. */
    GradientDrawable attachDot(TextView tv, int color, int sizeDp) {
        GradientDrawable dot = oval(color);
        int s = dp(sizeDp);
        dot.setBounds(0, 0, s, s);
        tv.setCompoundDrawablesRelative(dot, null, null, null);
        tv.setCompoundDrawablePadding(dp(7));
        return dot;
    }

    void attachIcon(TextView tv, int iconRes, int tint, int sizeDp) {
        Drawable icon = ContextCompat.getDrawable(context, iconRes);
        if (icon == null) {
            return;
        }
        icon = icon.mutate();
        icon.setTint(tint);
        int s = dp(sizeDp);
        icon.setBounds(0, 0, s, s);
        tv.setCompoundDrawablesRelative(icon, null, null, null);
        tv.setCompoundDrawablePadding(dp(8));
    }

    /** Renkli zeminli kucuk durum hapi; rengi {@link #tintPill} ile degisir. */
    TextView statusPill() {
        TextView tv = text(13, R.color.textPrimary, true);
        tv.setPadding(dp(10), dp(5), dp(12), dp(5));
        tv.setSingleLine(true);
        return tv;
    }

    void tintPill(TextView pill, GradientDrawable dot, int color) {
        pill.setBackground(roundRect(withAlpha(color, 0x24), 14));
        pill.setTextColor(color);
        if (dot != null) dot.setColor(color);
    }

    /** Video uzerindeki yari saydam yuvarlak etiket. */
    TextView overlayPill(String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(13);
        tv.setBackground(roundRect(OVERLAY_BG, 14));
        tv.setPadding(dp(10), dp(5), dp(12), dp(5));
        tv.setClickable(false);
        return tv;
    }

    /** Alt gorunum basligi; geri gezinme ortak alt menu tarafindan saglanir. */
    LinearLayout header(int titleRes) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text(20, R.color.textPrimary, true);
        title.setText(titleRes);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMarginStart(0);
        tp.setMarginEnd(dp(12));
        row.addView(title, tp);
        return row;
    }

    /** cam indeksi CameraRecorder.CAM_SUFFIX / HabNative.CAMERA_IDS sirasidir. */
    String camShortName(int idx) {
        int id = com.mapcontrol.dvr.HabNative.CAMERA_IDS[idx];
        switch (id) {
            case 5: return context.getString(R.string.camera360_front);
            case 4: return context.getString(R.string.camera360_right);
            case 8: return context.getString(R.string.camera360_left);
            case 9: return context.getString(R.string.camera360_back);
            default: return "id=" + id;
        }
    }

    LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    LinearLayout.LayoutParams matchWrapTop(int topDp) {
        LinearLayout.LayoutParams p = matchWrap();
        p.topMargin = dp(topDp);
        return p;
    }

    /** N secenekli hap secici (segment track gorunumunde). */
    static final class Choice {
        final LinearLayout view;
        private final TextView[] items;
        private final Camera360Ui ui;
        private int selected;
        private boolean enabled = true;

        Choice(Camera360Ui ui, LinearLayout view, TextView[] items, int selected) {
            this.ui = ui;
            this.view = view;
            this.items = items;
            this.selected = selected;
            refresh();
        }

        int getSelected() {
            return selected;
        }

        void setSelected(int index) {
            selected = index;
            refresh();
        }

        void setEnabled(boolean on) {
            enabled = on;
            view.setAlpha(on ? 1f : 0.5f);
            for (TextView t : items) t.setEnabled(on);
        }

        boolean isEnabled() {
            return enabled;
        }

        private void refresh() {
            for (int i = 0; i < items.length; i++) {
                boolean on = i == selected;
                if (on) {
                    UiStyles.setBackgroundRes(items[i], R.drawable.bg_segment_thumb);
                } else {
                    items[i].setBackgroundColor(Color.TRANSPARENT);
                }
                items[i].setTextColor(ui.themeColor(on ? R.color.textPrimary : R.color.textSecondary));
                items[i].setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
            }
        }
    }

    Choice choice(String[] labels, int selected, IntConsumer onPick) {
        LinearLayout track = new LinearLayout(context);
        track.setOrientation(LinearLayout.HORIZONTAL);
        UiStyles.setBackgroundRes(track, R.drawable.bg_segment_track);
        int tpad = UiStyles.dimenPx(context, R.dimen.spacing_tiny);
        track.setPadding(tpad, tpad, tpad, tpad);
        TextView[] items = new TextView[labels.length];
        Choice[] holder = new Choice[1];
        for (int i = 0; i < labels.length; i++) {
            TextView t = new TextView(context);
            t.setText(labels[i]);
            t.setTextSize(15);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(UiStyles.dimenPx(context, R.dimen.button_height));
            final int index = i;
            t.setOnClickListener(v -> {
                Choice c = holder[0];
                if (c == null || !c.isEnabled() || c.getSelected() == index) return;
                onPick.accept(index);
            });
            items[i] = t;
            track.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        holder[0] = new Choice(this, track, items, selected);
        return holder[0];
    }

    static String formatSize(long bytes) {
        if (bytes >= 1_000_000_000L) {
            return String.format(java.util.Locale.US, "%.1f GB", bytes / 1_000_000_000.0);
        }
        if (bytes >= 1_000_000L) {
            return String.format(java.util.Locale.US, "%.0f MB", bytes / 1_000_000.0);
        }
        return String.format(java.util.Locale.US, "%.0f KB", bytes / 1_000.0);
    }

    static String formatElapsed(long elapsedMs) {
        long s = Math.max(0, elapsedMs / 1000);
        if (s >= 3600) {
            return String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        }
        return String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    String formatDuration(long minutes) {
        long h = minutes / 60;
        long m = minutes % 60;
        if (h > 0) {
            return h + " " + context.getString(R.string.camera360_hour) + " " +
                    m + " " + context.getString(R.string.camera360_minute);
        }
        return m + " " + context.getString(R.string.camera360_minute);
    }
}
