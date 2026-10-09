package com.mapcontrol.ui.builder;

import android.content.Context;
import android.content.res.Configuration;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.vehicle.VehicleMetricsRepository;

/**
 * Araç Launcher Modu ana ekranı — üstteki 3 dashboard kartı viewport yüksekliğini doldurur.
 */
public class LauncherTabBuilder {

    public interface LauncherCallback {
        void onShortcutSelected(int tabIndex, String title);
        void onExitLauncherRequested();
        void onAppLaunchRequested(String packageName);
        void onClusterProjectionRequested(String action);
    }

    private final Context context;
    private final LauncherCallback callback;
    private final VehicleMetricsRepository vehicleMetricsRepository;
    private LauncherDashboardBuilder dashboardBuilder;
    private ScrollView scrollView;

    public LauncherTabBuilder(
            Context context,
            VehicleMetricsRepository vehicleMetricsRepository,
            LauncherCallback callback) {
        this.context = context;
        this.vehicleMetricsRepository = vehicleMetricsRepository;
        this.callback = callback;
    }

    public ScrollView build() {
        scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(UiStyles.color(context, R.color.transparent));
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int margin = UiStyles.dimenPx(context, R.dimen.oem_card_margin);
        root.setPadding(margin, margin, margin, margin);

        dashboardBuilder = new LauncherDashboardBuilder(
                context, vehicleMetricsRepository, callback);
        boolean portrait = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
        // Yatay: fillViewport + weight ile kart satırı viewport'u doldurur.
        // Dikey: kartlar alt alta, yükseklikleri içeriğe göre kayar.
        LinearLayout.LayoutParams heroLp = portrait
                ? new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f);
        root.addView(dashboardBuilder.build(), heroLp);

        scrollView.addView(root, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                portrait
                        ? LinearLayout.LayoutParams.WRAP_CONTENT
                        : LinearLayout.LayoutParams.MATCH_PARENT));
        return scrollView;
    }

    public void refreshInstalledApps() {
        // Ana ekranda uygulama listesi yok; hızlı erişim kartı kullanılıyor.
    }

    /** Launcher sekmesi görünür olduğunda dashboard canlı veriyi dinler. */
    public void onTabVisible() {
        if (dashboardBuilder != null) {
            dashboardBuilder.start();
        }
    }

    /** Launcher sekmesinden çıkınca dashboard dinlemeyi bırakır. */
    public void onTabHidden() {
        if (dashboardBuilder != null) {
            dashboardBuilder.stop();
        }
    }

    /**
     * Sistem light/dark değişimi — kart/metin renklerini yeniler.
     */
    public void onUiModeChanged() {
        if (dashboardBuilder != null) {
            dashboardBuilder.reapplyTheme();
        }
    }

    public ScrollView getScrollView() {
        return scrollView;
    }
}
