package com.mapcontrol.util;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class DisplayHelper {
    private static View preparingMessageView = null;
    private static android.view.WindowManager preparingWindowManager = null;
    private static android.view.WindowManager.LayoutParams preparingParams = null;
    private static Context displayContext = null;
    private static AnimatorSet preparingHeartbeatAnim = null;

    private DisplayHelper() {}

    public static void showPreparingMessageOnDisplay(Context context, int displayId) {
        try {
            hidePreparingMessage();

            DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (displayManager == null) {
                return;
            }

            Display targetDisplay = null;
            Display[] displays = displayManager.getDisplays();
            for (Display display : displays) {
                if (display.getDisplayId() == displayId) {
                    targetDisplay = display;
                    break;
                }
            }

            if (targetDisplay == null) {
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                displayContext = context.createDisplayContext(targetDisplay);
            } else {
                displayContext = context;
            }

            preparingWindowManager = (android.view.WindowManager) displayContext.getSystemService(Context.WINDOW_SERVICE);
            if (preparingWindowManager == null) {
                return;
            }

            LinearLayout messageContainer = new LinearLayout(displayContext);
            messageContainer.setOrientation(LinearLayout.VERTICAL);
            messageContainer.setGravity(android.view.Gravity.CENTER);
            messageContainer.setBackgroundColor(0xCC000000);
            messageContainer.setPadding(32, 32, 32, 32);

            TextView messageText = new TextView(displayContext);
            messageText.setText("⏳ Uygulama Hazırlanıyor...");
            messageText.setTextColor(0xFFFFFFFF);
            messageText.setTextSize(22);
            messageText.setTypeface(null, android.graphics.Typeface.BOLD);
            messageText.setGravity(android.view.Gravity.CENTER);
            messageContainer.addView(messageText);

            TextView subText = new TextView(displayContext);
            subText.setText("Lütfen bekleyin");
            subText.setTextColor(0xFFB0BEC5);
            subText.setTextSize(16);
            subText.setPadding(0, 12, 0, 0);
            subText.setGravity(android.view.Gravity.CENTER);
            messageContainer.addView(subText);

            preparingParams = new android.view.WindowManager.LayoutParams(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT
            );

            preparingMessageView = messageContainer;
            preparingWindowManager.addView(preparingMessageView, preparingParams);

            preparingMessageView.setAlpha(0f);
            preparingMessageView.animate()
                    .alpha(1f)
                    .setDuration(250)
                    .start();

            if (preparingHeartbeatAnim != null) {
                preparingHeartbeatAnim.cancel();
                preparingHeartbeatAnim = null;
            }
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(messageText, "scaleX", 1.0f, 1.1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(messageText, "scaleY", 1.0f, 1.1f);
            scaleX.setDuration(600);
            scaleY.setDuration(600);
            scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
            scaleY.setInterpolator(new AccelerateDecelerateInterpolator());
            scaleX.setRepeatCount(ValueAnimator.INFINITE);
            scaleY.setRepeatCount(ValueAnimator.INFINITE);
            scaleX.setRepeatMode(ValueAnimator.REVERSE);
            scaleY.setRepeatMode(ValueAnimator.REVERSE);
            preparingHeartbeatAnim = new AnimatorSet();
            preparingHeartbeatAnim.playTogether(scaleX, scaleY);
            preparingHeartbeatAnim.start();
        } catch (Exception ignored) {
        }
    }

    public static void hidePreparingMessage() {
        try {
            if (preparingHeartbeatAnim != null) {
                preparingHeartbeatAnim.cancel();
                preparingHeartbeatAnim = null;
            }
            if (preparingMessageView != null && preparingWindowManager != null) {
                try {
                    preparingMessageView.animate()
                            .alpha(0f)
                            .setDuration(200)
                            .withEndAction(() -> {
                                try {
                                    if (preparingWindowManager != null && preparingMessageView != null) {
                                        preparingWindowManager.removeView(preparingMessageView);
                                    }
                                } catch (Exception ignored) {
                                } finally {
                                    preparingMessageView = null;
                                    preparingWindowManager = null;
                                    preparingParams = null;
                                    displayContext = null;
                                }
                            })
                            .start();
                    return;
                } catch (Exception ignored) {
                }

                preparingWindowManager.removeView(preparingMessageView);
                preparingMessageView = null;
            }
        } catch (Exception ignored) {
        } finally {
            if (preparingHeartbeatAnim != null) {
                preparingHeartbeatAnim.cancel();
                preparingHeartbeatAnim = null;
            }
            preparingMessageView = null;
            preparingWindowManager = null;
            preparingParams = null;
            displayContext = null;
        }
    }
}

