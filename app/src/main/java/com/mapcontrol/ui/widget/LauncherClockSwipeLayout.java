package com.mapcontrol.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

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
    private static final float HINT_SHIFT_DP = 28f;
    private static final int HINT_SIZE_DP = 56;

    private final GestureDetector gestureDetector;
    private final int touchSlop;
    private final float recognizePx;
    private final float commitPx;
    private final float hintShiftPx;
    private final ImageView hintView;

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
        float density = context.getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        recognizePx = RECOGNIZE_DP * density;
        commitPx = COMMIT_DP * density;
        hintShiftPx = HINT_SHIFT_DP * density;
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
        hintView = createHint(context, density);
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
        float shift = hintShiftPx * progress;
        hintView.setTranslationX(direction == LauncherSwipeStore.LEFT ? -shift
                : direction == LauncherSwipeStore.RIGHT ? shift : 0f);
        hintView.setTranslationY(direction == LauncherSwipeStore.UP ? -shift
                : direction == LauncherSwipeStore.DOWN ? shift : 0f);
        hintView.setAlpha(0.35f + 0.65f * progress);
        hintView.setVisibility(VISIBLE);
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
            hintView.setTranslationX(0f);
            hintView.setTranslationY(0f);
            return;
        }
        hintView.animate()
                .alpha(0f)
                .translationX(0f)
                .translationY(0f)
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

    private ImageView createHint(Context context, float density) {
        ImageView view = new ImageView(context);
        view.setImageResource(R.drawable.ic_mdi_chevron_up);
        view.setColorFilter(UiStyles.color(context, R.color.textPrimary));
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = Math.round(8f * density);
        view.setPadding(pad, pad, pad, pad);
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
