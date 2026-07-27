package com.mapcontrol.manager;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.mapcontrol.util.AlertSoundHelper;
import com.mapcontrol.util.ClusterNavigationState;
import com.mapcontrol.util.TargetPackageStore;

/**
 * VDBus fiziksel tuş dinleyicisi — {@link MapControlService} (boot) ve {@link com.mapcontrol.ui.activity.MainActivity}
 * arasında tek örnek; referans sayacı ile çift subscribe önlenir.
 */
public final class MapControlVDBusKeyBridge {

    private static final String TAG = "MapControlVDBusKey";
    private static final String ACTION_LOG = "com.mapcontrol.LOG_MESSAGE";
    private static final String EXTRA_LOG_MESSAGE = "log_message";

    private static VDBusManager manager;
    private static ClusterDisplayManager clusterDisplayManager;
    private static int refCount;

    private MapControlVDBusKeyBridge() {
    }

    /** Servis veya Activity yaşam döngüsü başında. */
    public static synchronized void acquire(Context context) {
        Context app = context.getApplicationContext();
        if (manager == null) {
            manager = new VDBusManager(app, createCallback(app));
            manager.init();
        }
        refCount++;
        logToBroadcast(app, "VDBus key bridge acquire (refs=" + refCount + ")");
    }

    /** İlgili bileşen kapanırken; son referans gidince dinleyici durur. */
    public static synchronized void release(Context context) {
        if (refCount <= 0) {
            return;
        }
        refCount--;
        Context app = context.getApplicationContext();
        logToBroadcast(app, "VDBus key bridge release (refs=" + refCount + ")");
        if (refCount == 0 && manager != null) {
            manager.destroy();
            manager = null;
            clusterDisplayManager = null;
        }
    }

    /** Yansıtma sekmesi: harita kontrol tuşu Açık/Kapalı. */
    public static synchronized void start() {
        if (manager != null) {
            manager.start();
        }
    }

    public static synchronized void stop() {
        if (manager != null) {
            manager.stop();
        }
    }

    private static VDBusManager.VDBusCallback createCallback(Context app) {
        return new VDBusManager.VDBusCallback() {
            @Override
            public void onNavKeyToggle() {
                ClusterDisplayManager cdm = getOrCreateCluster(app);
                if (ClusterNavigationState.getLastKnownOpen()) {
                    cdm.closeClusterDisplay(false);
                } else {
                    cdm.openClusterDisplay();
                }
            }

            @Override
            public void onAlertTone() {
                AlertSoundHelper.playSoftAlert(app, msg -> logToBroadcast(app, msg));
            }

            @Override
            public void log(String message) {
                logToBroadcast(app, message);
            }

            @Override
            public void onProjectionTargetPickerToggle() {
                ProjectionVDBusTargetPickerManager.openIfClosed(app,
                        msg -> logToBroadcast(app, msg));
            }

            @Override
            public void onProjectionTargetPickerKeyRight() {
                ProjectionVDBusTargetPickerManager.advanceSelectionIfOpen(app);
            }

            @Override
            public void onProjectionTargetPickerKeyLeft() {
                ProjectionVDBusTargetPickerManager.retreatSelectionIfOpen(app);
            }
        };
    }

    private static ClusterDisplayManager getOrCreateCluster(Context app) {
        if (clusterDisplayManager == null) {
            clusterDisplayManager = new ClusterDisplayManager(app,
                    new ClusterDisplayManager.ClusterCallback() {
                        @Override
                        public void onNavigationStateChanged(boolean isOpen) {
                            ClusterNavigationState.publishFromService(app, isOpen);
                        }

                        @Override
                        public String getTargetPackage() {
                            String v = TargetPackageStore.read(app);
                            return v.isEmpty() ? null : v;
                        }

                        @Override
                        public void log(String message) {
                            logToBroadcast(app, message);
                        }
                    });
        }
        return clusterDisplayManager;
    }

    private static void logToBroadcast(Context app, String msg) {
        Log.d(TAG, msg);
        try {
            Intent intent = new Intent(ACTION_LOG);
            intent.putExtra(EXTRA_LOG_MESSAGE, msg);
            intent.setPackage(app.getPackageName());
            app.sendBroadcast(intent);
        } catch (Exception ignored) {
        }
    }

}
