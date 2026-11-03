package com.mapcontrol.ui.widget;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.vehicle.DriveModeStyle;
import com.mapcontrol.vehicle.VehicleMetricsSnapshot;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DriveModeBadgeTest {
    private Context context() {
        Context isolated = new ContextWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext()) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("drive_badge_test_" + name, mode);
            }
        };
        return new ContextThemeWrapper(isolated, R.style.Theme_MapControl);
    }

    private VehicleMetricsSnapshot snapshot(boolean connected, int mode) {
        return new VehicleMetricsSnapshot(connected, 0, 0, 12000, 50, 300, 1, 2, 12600,
                0, 0, 0, 0, 0, 2, 1, mode);
    }

    @Test public void liveModeOverridesStartupPreferenceAndDisconnectClearsItsColor() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = context();
            SharedPreferences prefs = context.getSharedPreferences("MapControlPrefs", 0);
            prefs.edit().putInt("driveModeSetting", DriveModeStyle.SPORT).commit();
            LauncherLitePanelView panel = new LauncherLitePanelView(context);
            try {
                panel.bindVehicleMetrics(snapshot(true, DriveModeStyle.ECO));
                TextView badge = panel.findViewWithTag("current_drive_mode");
                assertEquals("ECO", badge.getText().toString());
                assertFalse(badge.getText().toString().contains("SPORT"));
                assertEquals(DriveModeStyle.accentColor(context, DriveModeStyle.ECO),
                        ((GradientDrawable) badge.getBackground()).getColor().getDefaultColor());
                panel.bindVehicleMetrics(snapshot(false, DriveModeStyle.ECO));
                assertEquals("—", badge.getText().toString());
                assertEquals(UiStyles.color(context, R.color.surfaceCardInner),
                        ((GradientDrawable) badge.getBackground()).getColor().getDefaultColor());
                panel.bindVehicleMetrics(snapshot(true, 6));
                assertEquals("—", badge.getText().toString());
            } finally {
                panel.destroy();
                prefs.edit().clear().commit();
            }
        });
    }

    @Test public void allModesUpdateTheBadgeAndThemeKeepsTheCurrentMode() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = context();
            LauncherLitePanelView panel = new LauncherLitePanelView(context);
            try {
                TextView badge = panel.findViewWithTag("current_drive_mode");
                Set<Integer> accents = new HashSet<>();
                for (int mode : new int[]{0, 1, 2, 3, 4, 5, 7}) {
                    panel.bindVehicleMetrics(snapshot(true, mode));
                    assertEquals(DriveModeStyle.shortLabel(mode), badge.getText().toString());
                    int color = ((GradientDrawable) badge.getBackground()).getColor().getDefaultColor();
                    accents.add(color);
                    assertEquals(DriveModeStyle.accentColor(context, mode), color);
                }
                assertEquals(7, accents.size());
                panel.bindVehicleMetrics(snapshot(true, DriveModeStyle.SNOW));
                Configuration night = new Configuration(context.getResources().getConfiguration());
                night.uiMode = (night.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
                UiStyles.setUiModeOverride(night);
                panel.reapplyTheme();
                assertEquals("SNOW", badge.getText().toString());
                assertEquals(DriveModeStyle.accentColor(context, DriveModeStyle.SNOW),
                        ((GradientDrawable) badge.getBackground()).getColor().getDefaultColor());
                UiStyles.setUiModeOverride(null);
                panel.bindVehicleMetrics(snapshot(true, DriveModeStyle.ECO));
                panel.reapplyTheme();
                saveLabeledPreview(context, panel);
            } finally {
                UiStyles.setUiModeOverride(null);
                panel.destroy();
            }
        });
    }

    private void saveLabeledPreview(Context context, View panel) {
        LinearLayout host = new LinearLayout(context);
        host.setOrientation(LinearLayout.VERTICAL);
        host.setBackgroundColor(UiStyles.color(context, R.color.surfaceCardInner));
        TextView caption = new TextView(context);
        caption.setText("ÖRNEK VERİ · ARAÇ BAĞLANTISI YOK / SAMPLE DATA");
        caption.setTextColor(UiStyles.color(context, R.color.textHint));
        caption.setTextSize(20);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(16, 16, 16, 16);
        host.addView(caption);
        host.addView(panel, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        host.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(680, View.MeasureSpec.EXACTLY));
        host.layout(0, 0, 1200, 680);
        Bitmap bitmap = Bitmap.createBitmap(1200, 680, Bitmap.Config.ARGB_8888);
        host.draw(new Canvas(bitmap));
        File output = new File(context.getFilesDir(), "drive-mode-home-sample.png");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } catch (Exception error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }
}
