package com.mapcontrol.ui.widget;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.LauncherSwipeStore;

/**
 * Launcher saat kartı. Kaydırma eşiği geçilince yön oku belirir; bırakma veya fling
 * {@link Listener#onDirection(int)} ile bildirilir.
 */
public class LauncherClockSwipeLayout extends FrameLayout {

    public interface Listener {
        void onDirection(int direction);
    }

    private static final float RECOGNIZE_DP = 48f;
    private static final float COMMIT_DP = 96f;
    private static final int HINT_SIZE_DP = 72;
    private static final float HINT_MARGIN_DP = 4f;

    private final GestureDetector gestureDetector;
    private final int touchSlop;
    private final float density;
    private final float recognizePx;
    private final float commitPx;
    private final int edgeMarginPx;
    private final ImageView hintView;
    private final Rect contentRect = new Rect();

    private float downX;
    private float downY;
    private boolean dragging;
    private boolean downFed;
    private int flungDirection;
    @Nullable
    private Listener listener;

    public LauncherClockSwipeLayout(@NonNull Context context) {
        this(context, null);
    }

    public LauncherClockSwipeLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        recognizePx = RECOGNIZE_DP * density;
        commitPx = COMMIT_DP * density;
        edgeMarginPx = Math.round(HINT_MARGIN_DP * density);
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (Math.abs(velocityX) > Math.abs(velocityY)) {
                    flungDirection = velocityX > 0f ? LauncherSwipeStore.RIGHT : LauncherSwipeStore.LEFT;
                } else {
                    flungDirection = velocityY > 0f ? LauncherSwipeStore.DOWN : LauncherSwipeStore.UP;
                }
                return true;
            }
        });
        hintView = createHint(context);
        addView(hintView, hintLayoutParams(density));
    }

    public void setListener(@Nullable Listener swipeListener) {
        this.listener = swipeListener;
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        if (child != hintView && hintView != null) {
            hintView.bringToFront();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            resetGesture(ev);
            gestureDetector.onTouchEvent(ev);
            downFed = true;
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE && !dragging) {
            if (distanceFromDown(ev) > touchSlop) {
                dragging = true;
                return true;
            }
        }
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && !dragging) {
            hideHint(false);
        }
        return dragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (!downFed) {
                resetGesture(ev);
                gestureDetector.onTouchEvent(ev);
            }
            downFed = false;
            return true;
        }
        gestureDetector.onTouchEvent(ev);
        if (action == MotionEvent.ACTION_MOVE) {
            if (distanceFromDown(ev) > touchSlop) {
                dragging = true;
            }
            if (dragging) {
                updateHint(ev);
            }
            return dragging;
        }
        if (action == MotionEvent.ACTION_UP) {
            if (dragging) {
                finishSwipe(ev);
            } else {
                hideHint(false);
            }
            dragging = false;
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            hideHint(true);
            dragging = false;
            return true;
        }
        return dragging;
    }

    private void resetGesture(MotionEvent ev) {
        downX = ev.getX();
        downY = ev.getY();
        dragging = false;
        flungDirection = -1;
        hintView.animate().cancel();
        hintView.setAlpha(0f);
        hintView.setScaleX(1f);
        hintView.setScaleY(1f);
        hintView.setTranslationX(0f);
        hintView.setTranslationY(0f);
    }

    private void updateHint(MotionEvent ev) {
        float dx = ev.getX() - downX;
        float dy = ev.getY() - downY;
        float distance = (float) Math.hypot(dx, dy);
        if (distance < recognizePx) {
            hintView.setAlpha(0f);
            hintView.setTranslationX(0f);
            hintView.setTranslationY(0f);
            return;
        }
        int direction = directionOf(dx, dy);
        hintView.setRotation(rotationFor(direction));
        float span = Math.max(1f, commitPx - recognizePx);
        float progress = Math.min(1f, (distance - recognizePx) / span);
        placeHint(direction, progress);
        hintView.setAlpha(0.82f + 0.18f * progress);
        hintView.setVisibility(VISIBLE);
    }

    /**
     * Ok, saat ve durum yazılarının üstüne binmesin diye içerik kutusunun dışındaki
     * boşluğa, kaydırma yönünün kenarına oturtulur.
     */
    private void placeHint(int direction, float progress) {
        int iconW = hintView.getWidth();
        int iconH = hintView.getHeight();
        int hostW = getWidth();
        int hostH = getHeight();
        if (iconW <= 0 || iconH <= 0 || hostW <= 0 || hostH <= 0) {
            return;
        }
        float x;
        float y;
        if (!measureContent(contentRect)) {
            x = direction == LauncherSwipeStore.LEFT ? edgeMarginPx
                    : direction == LauncherSwipeStore.RIGHT ? hostW - iconW - edgeMarginPx
                    : (hostW - iconW) / 2f;
            y = direction == LauncherSwipeStore.UP ? edgeMarginPx
                    : direction == LauncherSwipeStore.DOWN ? hostH - iconH - edgeMarginPx
                    : (hostH - iconH) / 2f;
        } else if (direction == LauncherSwipeStore.UP) {
            x = (hostW - iconW) / 2f;
            y = yInVerticalGap(0, contentRect.top, iconH, true);
        } else if (direction == LauncherSwipeStore.DOWN) {
            x = (hostW - iconW) / 2f;
            y = yInVerticalGap(contentRect.bottom, hostH - contentRect.bottom, iconH, false);
        } else {
            int sideSpace = direction == LauncherSwipeStore.LEFT
                    ? contentRect.left
                    : hostW - contentRect.right;
            boolean above = contentRect.top >= hostH - contentRect.bottom;
            x = direction == LauncherSwipeStore.LEFT
                    ? edgeMarginPx
                    : hostW - iconW - edgeMarginPx;
            if (sideSpace >= iconW + edgeMarginPx * 2) {
                y = clamp(contentRect.centerY() - iconH / 2f, edgeMarginPx, hostH - iconH - edgeMarginPx);
            } else {
                y = above
                        ? yInVerticalGap(0, contentRect.top, iconH, true)
                        : yInVerticalGap(contentRect.bottom, hostH - contentRect.bottom, iconH, false);
            }
        }
        float scale = 0.94f + 0.06f * progress;
        hintView.setScaleX(scale);
        hintView.setScaleY(scale);
        hintView.setTranslationX(x - (hostW - iconW) / 2f);
        hintView.setTranslationY(y - (hostH - iconH) / 2f);
    }

    private float yInVerticalGap(int gapStart, int gapSize, int icon, boolean pinToStart) {
        float y;
        if (pinToStart) {
            y = edgeMarginPx;
        } else {
            y = gapStart + gapSize - icon - edgeMarginPx;
        }
        return clamp(y, edgeMarginPx, getHeight() - icon - edgeMarginPx);
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private boolean measureContent(Rect out) {
        out.set(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        int[] host = new int[2];
        getLocationInWindow(host);
        boolean any = false;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == hintView) {
                continue;
            }
            any |= accumulateContent(child, host, out);
        }
        return any && out.left < out.right && out.top < out.bottom;
    }

    private boolean accumulateContent(View view, int[] host, Rect out) {
        if (view.getVisibility() != VISIBLE) {
            return false;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            if (group.getChildCount() > 0) {
                boolean any = false;
                for (int i = 0; i < group.getChildCount(); i++) {
                    any |= accumulateContent(group.getChildAt(i), host, out);
                }
                return any;
            }
        }
        if (view.getWidth() <= 0 || view.getHeight() < Math.round(4f * density)) {
            return false;
        }
        int[] loc = new int[2];
        view.getLocationInWindow(loc);
        int left = loc[0] - host[0];
        int top = loc[1] - host[1];
        int right = left + view.getWidth();
        int bottom = top + view.getHeight();
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            CharSequence text = textView.getText();
            String value = text == null ? "" : text.toString();
            if (value.trim().isEmpty()) {
                return false;
            }
            float textWidth = textView.getPaint().measureText(value);
            int horizontal = textView.getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK;
            if (horizontal == Gravity.LEFT || horizontal == Gravity.START) {
                left += textView.getPaddingLeft();
                right = Math.round(left + textWidth);
            } else if (horizontal == Gravity.RIGHT || horizontal == Gravity.END) {
                right -= textView.getPaddingRight();
                left = Math.round(right - textWidth);
            } else {
                float center = left + view.getWidth() / 2f;
                left = Math.round(center - textWidth / 2f);
                right = Math.round(center + textWidth / 2f);
            }
        }
        out.left = Math.min(out.left, left);
        out.top = Math.min(out.top, top);
        out.right = Math.max(out.right, right);
        out.bottom = Math.max(out.bottom, bottom);
        return true;
    }

    private void finishSwipe(MotionEvent ev) {
        float dx = ev.getX() - downX;
        float dy = ev.getY() - downY;
        float distance = (float) Math.hypot(dx, dy);
        int direction = directionOf(dx, dy);
        int chosen = -1;
        if (distance >= commitPx) {
            chosen = direction;
        } else if (flungDirection >= 0) {
            chosen = flungDirection;
        }
        hideHint(true);
        if (chosen >= 0 && listener != null) {
            listener.onDirection(chosen);
        }
    }

    private void hideHint(boolean animate) {
        if (!animate) {
            hintView.animate().cancel();
            hintView.setAlpha(0f);
            hintView.setScaleX(1f);
            hintView.setScaleY(1f);
            hintView.setTranslationX(0f);
            hintView.setTranslationY(0f);
            return;
        }
        hintView.animate()
                .alpha(0f)
                .setDuration(180L)
                .start();
    }

    private float distanceFromDown(MotionEvent ev) {
        return (float) Math.hypot(ev.getX() - downX, ev.getY() - downY);
    }

    private static int directionOf(float dx, float dy) {
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0f ? LauncherSwipeStore.RIGHT : LauncherSwipeStore.LEFT;
        }
        return dy > 0f ? LauncherSwipeStore.DOWN : LauncherSwipeStore.UP;
    }

    private static float rotationFor(int direction) {
        if (direction == LauncherSwipeStore.RIGHT) {
            return 90f;
        }
        if (direction == LauncherSwipeStore.DOWN) {
            return 180f;
        }
        if (direction == LauncherSwipeStore.LEFT) {
            return 270f;
        }
        return 0f;
    }

    private ImageView createHint(Context context) {
        ImageView view = new ImageView(context);
        view.setImageResource(R.drawable.ic_swipe_arrow_up);
        view.setColorFilter(UiStyles.color(context, R.color.textPrimary));
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setPadding(0, 0, 0, 0);
        view.setAlpha(0f);
        view.setClickable(false);
        view.setFocusable(false);
        view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    private LayoutParams hintLayoutParams(float density) {
        int size = Math.round(HINT_SIZE_DP * density);
        LayoutParams lp = new LayoutParams(size, size);
        lp.gravity = android.view.Gravity.CENTER;
        return lp;
    }
}
