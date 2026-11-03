package com.mapcontrol.ui.builder;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.vehicle.VehicleMetricsRepository;

/**
 * Araç Launcher Modu ana ekranı — tam ekran, kaydırmasız 3 dashboard kartı.
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
    private FrameLayout rootView;

    public LauncherTabBuilder(
            Context context,
            VehicleMetricsRepository vehicleMetricsRepository,
            LauncherCallback callback) {
        this.context = context;
        this.vehicleMetricsRepository = vehicleMetricsRepository;
        this.callback = callback;
    }

    public View build() {
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(UiStyles.color(context, R.color.transparent));
        int margin = UiStyles.dimenPx(context, R.dimen.oem_card_margin);
        root.setPadding(margin, margin, margin, margin);

        dashboardBuilder = new LauncherDashboardBuilder(
                context, vehicleMetricsRepository, callback);
        root.addView(dashboardBuilder.build(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        rootView = root;
        return root;
    }

    public LauncherDashboardBuilder getDashboardBuilder() {
        return dashboardBuilder;
    }

    public View getRootView() {
        return rootView;
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
}
