package com.mapcontrol.ui.activity;

import com.mapcontrol.ui.theme.UiStyles;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mapcontrol.R;
import com.mapcontrol.util.AppLaunchHelper;

/**
 * Profil ekranı açıkken DMS önizlemesini cluster'ın ortasına koyar.
 * Ekrandan çıkınca overlay kalkar ve cluster önceki görüntüsüne döner.
 */
final class DmsClusterPreview {
    private WindowManager windowManager;
    private FrameLayout root;
    private ImageView image;
    private TextView prompt;
    private TextView instruction;

    void show(Context context) {
        if (root != null) {
            return;
        }
        DisplayManager displayManager =
                (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager == null) {
            return;
        }
        int displayId = AppLaunchHelper.getClusterDisplayId(context);
        Display target = null;
        for (Display display : displayManager.getDisplays()) {
            if (display.getDisplayId() == displayId) {
                target = display;
                break;
            }
        }
        if (target == null || target.getDisplayId() == Display.DEFAULT_DISPLAY) {
            return;
        }
        Context displayContext = context.createDisplayContext(target);
        windowManager = (WindowManager) displayContext.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            return;
        }

        float density = displayContext.getResources().getDisplayMetrics().density;
        int screenW = displayContext.getResources().getDisplayMetrics().widthPixels;
        int screenH = displayContext.getResources().getDisplayMetrics().heightPixels;
        int previewH = Math.round(screenH * 0.86f);
        int previewW = previewH * 4 / 3;
        int maxW = Math.round(screenW * 0.46f);
        if (previewW > maxW) {
            previewW = maxW;
            previewH = previewW * 3 / 4;
        }

        root = new FrameLayout(displayContext);
        root.setBackgroundColor(0x00000000);

        image = new ImageView(displayContext);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(0xFF000000);
        image.setVisibility(View.GONE);
        root.addView(image, new FrameLayout.LayoutParams(previewW, previewH, Gravity.CENTER));

        LinearLayout promptColumn = new LinearLayout(displayContext);
        promptColumn.setOrientation(LinearLayout.VERTICAL);
        promptColumn.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams columnLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(promptColumn, columnLp);
        promptColumn.setTranslationY(-screenH * 0.16f);

        prompt = new TextView(displayContext);
        prompt.setTextColor(0xFFFFFFFF);
        prompt.setTextSize(32);
        UiStyles.setFontStyle(prompt, Typeface.BOLD);
        prompt.setGravity(Gravity.CENTER);
        prompt.setShadowLayer(8f * density, 0f, 2f * density, 0xCC000000);
        prompt.setVisibility(View.GONE);
        promptColumn.addView(prompt, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        instruction = new TextView(displayContext);
        instruction.setTextColor(0xFFE8EEF6);
        instruction.setTextSize(22);
        instruction.setGravity(Gravity.CENTER);
        instruction.setShadowLayer(6f * density, 0f, 2f * density, 0xCC000000);
        instruction.setVisibility(View.GONE);
        LinearLayout.LayoutParams instructionLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        instructionLp.topMargin = Math.round(10 * density);
        promptColumn.addView(instruction, instructionLp);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        try {
            windowManager.addView(root, params);
        } catch (RuntimeException e) {
            root = null;
            image = null;
            prompt = null;
            instruction = null;
            windowManager = null;
        }
    }

    void hide() {
        if (root != null && windowManager != null) {
            try {
                if (image != null) {
                    image.setImageBitmap(null);
                }
                windowManager.removeView(root);
            } catch (RuntimeException ignored) {
            }
        }
        root = null;
        image = null;
        prompt = null;
        instruction = null;
        windowManager = null;
    }

    /** Sağ ekrandaki durum satırının aynısı. Ayrı adım metni yazılmaz. */
    void setMirrorText(CharSequence text) {
        if (prompt == null) {
            return;
        }
        setInstruction(null);
        if (text == null || text.length() == 0) {
            prompt.setVisibility(View.GONE);
            return;
        }
        prompt.setText(text);
        prompt.setVisibility(View.VISIBLE);
    }

    void setInstruction(CharSequence text) {
        if (instruction == null) {
            return;
        }
        if (text == null || text.length() == 0) {
            instruction.setVisibility(View.GONE);
            return;
        }
        instruction.setText(text);
        instruction.setVisibility(View.VISIBLE);
    }

    void setFrame(Bitmap frame) {
        if (image == null || frame == null || frame.isRecycled()) {
            return;
        }
        if (image.getVisibility() != View.VISIBLE) {
            image.setVisibility(View.VISIBLE);
        }
        image.setImageBitmap(frame);
        image.invalidate();
    }
}
