package com.mapcontrol.manager;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.mapcontrol.service.MapControlService;

/**
 * Araç yanında olmadan VDBus / cluster yollarını bench etmek için.
 * {@link com.mapcontrol.ui.activity.ClusterVDBusTestActivity} üzerinden kullanılır.
 */
public final class ClusterVDBusBenchManager {

    public interface UiActions {
        void navKeyToggleLikeVDBus();

        void alertToneLikeVDBus();

        void clusterOpenDirectUi();

        void clusterCloseDirectUi();
    }

    public interface Logger {
        void log(String line);
    }

    private final Context appContext;
    private final UiActions uiActions;
    private final Logger logger;

    public ClusterVDBusBenchManager(Context context, UiActions uiActions, Logger logger) {
        this.appContext = context.getApplicationContext();
        this.uiActions = uiActions;
        this.logger = logger;
    }

    public void benchNavToggle() {
        logger.log("[Bench] Nav toggle (VDBus 26/4 ile aynı UI yolu)");
        uiActions.navKeyToggleLikeVDBus();
    }

    public void benchAlertTone() {
        logger.log("[Bench] Uyarı sesi (VDBus 26/1)");
        uiActions.alertToneLikeVDBus();
    }

    public void benchClusterOpenUi() {
        logger.log("[Bench] Cluster aç — ClusterDisplayManager (isNavigationOpen atlanır)");
        uiActions.clusterOpenDirectUi();
    }

    public void benchClusterCloseUi() {
        logger.log("[Bench] Cluster kapat — ClusterDisplayManager");
        uiActions.clusterCloseDirectUi();
    }

    public void benchClusterOpenService() {
        logger.log("[Bench] Cluster aç — MapControlService.openClusterDisplay yolu");
        startServiceBench(MapControlService.ACTION_BENCH_OPEN_CLUSTER);
    }

    public void benchClusterCloseService() {
        logger.log("[Bench] Cluster kapat — MapControlService.closeClusterDisplay yolu");
        startServiceBench(MapControlService.ACTION_BENCH_CLOSE_CLUSTER);
    }

    private void startServiceBench(String action) {
        Intent i = new Intent(appContext, MapControlService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(i);
        } else {
            appContext.startService(i);
        }
    }
}
