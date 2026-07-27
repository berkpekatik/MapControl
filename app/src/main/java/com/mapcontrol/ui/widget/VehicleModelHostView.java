package com.mapcontrol.ui.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import com.mapcontrol.R;
import com.mapcontrol.vehicle.material.MaterialVehicleCatalog;
import com.mapcontrol.vehicle.material.MaterialVehicleResources;
import com.mapcontrol.vehicle.material.VehicleMaterialPickerDialog;

/**
 * Launcher dashboard araç görseli — OEM materialvehicle APK'sından 2D görsel + frame animasyon.
 * Görsele (veya varsayılan SVG'ye) dokununca araç seçim listesi açılır.
 */
public final class VehicleModelHostView extends FrameLayout {

    public enum ViewMode {
        NORMAL,
        SUNROOF,
        TRUNK
    }

    private static final String DRAWABLE_TAILGATE = "tailgate";
    private static final String DRAWABLE_SCUTTLE = "scuttle";
    private static final int MAX_FRAME_INDEX = 48;

    private final MaterialVehicleResources resources = MaterialVehicleResources.getInstance();
    private final View glowView;
    private final ImageView carImageView;
    private final OemFrameAnimationView animationView;
    private final ImageView fallbackView;

    private boolean active;
    private boolean oemAssetsLoaded;
    private ViewMode currentMode = ViewMode.NORMAL;

    public VehicleModelHostView(@NonNull Context context) {
        this(context, null);
    }

    public VehicleModelHostView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(false);
        setClickable(true);
        setFocusable(true);
        setOnClickListener(v -> openVehiclePicker());

        glowView = new View(context);
        glowView.setBackgroundResource(R.drawable.bg_launcher_vehicle_glow);
        addView(glowView, createGlowLayoutParams(0));

        carImageView = new AppCompatImageView(context);
        carImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carImageView.setContentDescription(context.getString(R.string.launcher_dashboard_vehicle_tap_select));
        carImageView.setClickable(false);
        addView(carImageView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        animationView = new OemFrameAnimationView(context);
        animationView.setVisibility(GONE);
        animationView.setClickable(false);
        addView(animationView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        fallbackView = new AppCompatImageView(context);
        fallbackView.setImageResource(R.drawable.ic_launcher_vehicle_top);
        fallbackView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        fallbackView.setVisibility(GONE);
        fallbackView.setClickable(false);
        fallbackView.setContentDescription(context.getString(R.string.launcher_dashboard_vehicle_tap_select));
        addView(fallbackView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (h > 0 && glowView.getLayoutParams() != null) {
            LayoutParams glowLp = createGlowLayoutParams(h);
            glowView.setLayoutParams(glowLp);
        }
    }

    private LayoutParams createGlowLayoutParams(int hostHeight) {
        int reference = hostHeight > 0
                ? hostHeight
                : getResources().getDimensionPixelSize(R.dimen.launcher_dashboard_vehicle_height);
        LayoutParams glowLp = new LayoutParams((int) (reference * 0.95f), (int) (reference * 0.72f));
        glowLp.gravity = Gravity.CENTER;
        return glowLp;
    }

    public void onHostStart() {
        active = true;
        resources.init(getContext());
        animationView.setResources(resources);
        loadOemCarImage();
    }

    public void reloadVehicleImage() {
        resources.init(getContext());
        animationView.setResources(resources);
        loadOemCarImage();
    }

    private void openVehiclePicker() {
        Activity activity = findActivity(getContext());
        if (activity == null) {
            return;
        }
        VehicleMaterialPickerDialog.show(activity, new VehicleMaterialPickerDialog.OnSelectedListener() {
            @Override
            public void onManualSelected(MaterialVehicleCatalog.Entry entry) {
                reloadVehicleImage();
            }

            @Override
            public void onAutoDetectionSelected() {
                reloadVehicleImage();
            }
        });
    }

    @Nullable
    private static Activity findActivity(Context context) {
        Context ctx = context;
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) {
                return (Activity) ctx;
            }
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        return ctx instanceof Activity ? (Activity) ctx : null;
    }

    public void onHostStop() {
        active = false;
        animationView.hide();
        currentMode = ViewMode.NORMAL;
    }

    public void showTrunkMode() {
        if (!oemAssetsLoaded) {
            return;
        }
        int lastFrame = resources.findLastFrameIndex(DRAWABLE_TAILGATE, MAX_FRAME_INDEX);
        if (lastFrame < 0) {
            return;
        }
        currentMode = ViewMode.TRUNK;
        startTransition(false, DRAWABLE_TAILGATE, 0, lastFrame);
    }

    public void showSunroofMode() {
        if (!oemAssetsLoaded) {
            return;
        }
        currentMode = ViewMode.SUNROOF;
        startTransition(false, DRAWABLE_SCUTTLE, 0, resources.findLastFrameIndex(DRAWABLE_SCUTTLE, MAX_FRAME_INDEX));
    }

    public void showNormalMode() {
        if (!oemAssetsLoaded || currentMode == ViewMode.NORMAL) {
            return;
        }
        String prefix = currentMode == ViewMode.SUNROOF ? DRAWABLE_SCUTTLE : DRAWABLE_TAILGATE;
        int end = resources.findLastFrameIndex(prefix, MAX_FRAME_INDEX);
        startTransition(true, prefix, end >= 0 ? end : 0, 0);
        currentMode = ViewMode.NORMAL;
    }

    private void loadOemCarImage() {
        Bitmap carBitmap = resources.loadCarModelBitmap();
        if (carBitmap != null) {
            oemAssetsLoaded = true;
            carImageView.setImageBitmap(carBitmap);
            carImageView.setVisibility(VISIBLE);
            fallbackView.setVisibility(GONE);
            return;
        }
        oemAssetsLoaded = false;
        carImageView.setVisibility(GONE);
        fallbackView.setVisibility(VISIBLE);
    }

    private void startTransition(boolean backToNormal, String prefix, int fromFrame, int toFrame) {
        final View outgoing = backToNormal ? animationView : carImageView;
        final View incoming = backToNormal ? carImageView : animationView;

        if (!backToNormal) {
            animationView.showFrame(prefix, fromFrame);
        }

        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(outgoing, ALPHA, 1f, 0f);
        fadeOut.setDuration(300L);
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                outgoing.setVisibility(GONE);
                if (backToNormal) {
                    animationView.hide();
                }
            }
        });

        incoming.setVisibility(VISIBLE);
        incoming.setAlpha(0f);
        incoming.setScaleX(0f);
        incoming.setScaleY(0f);

        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(incoming, ALPHA, 0f, 1f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(incoming, SCALE_X, 0f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(incoming, SCALE_Y, 0f, 1f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(fadeIn, scaleX, scaleY);
        set.setStartDelay(200L);
        set.setDuration(300L);
        set.start();
        fadeOut.start();

        if (!backToNormal && toFrame != fromFrame) {
            animationView.play(prefix, fromFrame, toFrame, toFrame < fromFrame);
        }
    }
}
