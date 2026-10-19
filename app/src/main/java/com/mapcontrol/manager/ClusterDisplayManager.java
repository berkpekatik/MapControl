package com.mapcontrol.manager;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carlan.VDEventCarLan;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDisplayArea;
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDisplayCluster;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.mapcontrol.util.AppLaunchHelper;
import com.mapcontrol.util.DisplayHelper;

public class ClusterDisplayManager {
    public interface ClusterCallback {
        void onNavigationStateChanged(boolean isOpen);
        String getTargetPackage();
        void log(String message);
    }

    private final Context context;
    private final ClusterCallback callback;
    private final Handler handler;
    /** Kapatma veya yeni açılış, bekleyen openClusterDisplay postDelayed zincirini iptal eder. */
    private final AtomicInteger openSessionId = new AtomicInteger(0);

    public ClusterDisplayManager(Context context, ClusterCallback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void openClusterDisplay() {
        final int session = openSessionId.incrementAndGet();
        try {
            int targetDisplay = AppLaunchHelper.getClusterDisplayId(context);
            if (targetDisplay <= 0) {
                callback.log("Cluster açılmadı: tek ve geçerli ikincil ekran bulunamadı.");
                return;
            }
            if (targetDisplay > 0) {
                DisplayHelper.hideBootSplash();
                DisplayHelper.showPreparingMessageOnDisplay(context, targetDisplay);
                Runnable timeoutRunnable = () -> {
                    if (session != openSessionId.get()) {
                        return;
                    }
                    callback.log("⚠️ İşlem zaman aşımına uğradı, mesaj gizleniyor.");
                    DisplayHelper.hidePreparingMessage();
                };
                handler.postDelayed(timeoutRunnable, 7000);
            }

            // Running processes do not identify their display. Do not move an unrelated app.
            VDNaviDisplayCluster payloadwakeUp = new VDNaviDisplayCluster();
            payloadwakeUp.setNaviFrontDeskStatus("true");
            payloadwakeUp.setDisplayCluster("true");
            payloadwakeUp.setPerspective(0);
            payloadwakeUp.setPerspectiveResult("false");
            payloadwakeUp.setRequestDisplayNaviArea("true");
            VDEvent eventwakeUp = VDNaviDisplayCluster.createEvent(VDEventCarLan.NAVIGATION_DISPLAY_TO_CLUSTER, payloadwakeUp);
            VDBus.getDefault().set(eventwakeUp);

            handler.postDelayed(() -> {
                if (session != openSessionId.get()) {
                    return;
                }
                if (AppLaunchHelper.getClusterDisplayId(context) != targetDisplay) {
                    DisplayHelper.hidePreparingMessage();
                    callback.log("Cluster açılışı durdu: ekran bağlantısı değişti.");
                    return;
                }
                VDNaviDisplayArea payloadDA = new VDNaviDisplayArea();
                payloadDA.setNaviDisplayArea(10);
                payloadDA.setNaviDisplayAreaResult("true");
                VDEvent eventDA = VDNaviDisplayArea.createEvent(VDEventCarLan.NAVIGATION_DISPLAY_AREA, payloadDA);
                VDBus.getDefault().set(eventDA);

                VDNaviDisplayCluster payload = new VDNaviDisplayCluster();
                payload.setNaviFrontDeskStatus("true");
                payload.setDisplayCluster("true");
                payload.setPerspective(0);
                payload.setPerspectiveResult("false");
                payload.setRequestDisplayNaviArea("false");
                VDEvent event = VDNaviDisplayCluster.createEvent(VDEventCarLan.NAVIGATION_DISPLAY_TO_CLUSTER, payload);
                VDBus.getDefault().set(event);

                callback.onNavigationStateChanged(true);
                callback.log("Navigasyon paneli açıldı");

                String targetPackage = callback.getTargetPackage();
                if (targetPackage != null && !targetPackage.trim().isEmpty()) {
                    handler.postDelayed(() -> {
                        if (session != openSessionId.get()) {
                            return;
                        }
                        callback.log("Seçilen uygulama cluster'da başlatılıyor: " + targetPackage);
                        try {
                            String tp = targetPackage != null ? targetPackage.trim() : "";
                            if (tp.isEmpty()) {
                                callback.log("Uygulama seçilmedi");
                            } else {
                                Intent li = context.getPackageManager().getLaunchIntentForPackage(tp);
                                if (li == null) {
                                    callback.log("Launch intent bulunamadı: " + targetPackage);
                                } else {
                                    int clusterId = AppLaunchHelper.getClusterDisplayId(context);
                                    AppLaunchHelper.launchAppOnDisplay(context, tp, clusterId, Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                                    callback.log("Intent cluster'da başlatıldı (displayId=" + clusterId + ")");
                                }
                            }
                        } catch (Exception e) {
                            callback.log("launchSelectedAppOnCluster hata: " + e.getMessage());
                        }
                        handler.postDelayed(DisplayHelper::hidePreparingMessage, 1000);
                    }, 600);
                }
            }, 1000);
        } catch (Exception e) {
            callback.log("Açma hatası: " + e.getMessage());
        }
    }

    /**
     * Cluster'da (display 2) ön planda üçüncü taraf uygulama yoksa boot splash basar.
     * "Ana Ekrana Al" sonrası boş cluster için {@link DisplayHelper#showBootSplashOnDisplay} ile aynı yol.
     */
    public void showBootSplashOnClusterIfNoForegroundApp() {
        // Android running-process APIs cannot prove that the cluster is empty.
        callback.log("Otomatik cluster görseli atlandı: ekranın boş olduğu doğrulanamıyor.");
    }

    public void closeClusterDisplay(boolean sendBackground) {
        openSessionId.incrementAndGet();
        try {
            VDNaviDisplayArea payloadDA = new VDNaviDisplayArea();
            payloadDA.setNaviDisplayArea(0);
            payloadDA.setNaviDisplayAreaResult("false");
            VDEvent eventDA = VDNaviDisplayArea.createEvent(VDEventCarLan.NAVIGATION_DISPLAY_AREA, payloadDA);
            VDBus.getDefault().set(eventDA);

            VDNaviDisplayCluster payload = new VDNaviDisplayCluster();
            payload.setNaviFrontDeskStatus("false");
            payload.setDisplayCluster("false");
            payload.setPerspective(0);
            payload.setPerspectiveResult("false");
            payload.setRequestDisplayNaviArea("false");
            VDEvent event = VDNaviDisplayCluster.createEvent(VDEventCarLan.NAVIGATION_DISPLAY_TO_CLUSTER, payload);
            VDBus.getDefault().set(event);
            DisplayHelper.showBootSplashOnDisplay(context, AppLaunchHelper.getClusterDisplayId(context));
            callback.onNavigationStateChanged(false);
            callback.log("Navigasyon paneli kapatıldı");

            String targetPackage = callback.getTargetPackage();
            if (targetPackage != null && AppLaunchHelper.GOOGLE_MAPS_PACKAGE.equals(targetPackage.trim())) {
                // Google Maps araçta özel yansıtma paketiyle gösterilir; ana ekrana taşıma gerekmez.
                callback.log("Google Maps hedefi: yalnızca VDBus kapatıldı, ana ekrana taşıma atlandı");
            } else if (targetPackage != null && !targetPackage.trim().isEmpty()) {
                try {
                    if (targetPackage.equals("com.mapcontrol")) {
                        callback.log("com.mapcontrol paketi taşınmayacak: " + targetPackage);
                    } else {
                        PackageManager pm = context.getPackageManager();
                        Intent li = pm.getLaunchIntentForPackage(targetPackage);
                        if (li == null) {
                            callback.log("Uygulama intent bulunamadı: " + targetPackage);
                        } else {
                            AppLaunchHelper.moveAppToDefaultDisplay(context, targetPackage);
                            callback.log("Uygulama default display'e taşındı (arka planda): " + targetPackage);
                        }
                    }
                } catch (Exception e) {
                    callback.log("moveAppToDefaultDisplay hatası: " + e.getMessage());
                }
                if (sendBackground) {
                    handler.postDelayed(() -> moveAppToBackground(targetPackage), 500);
                }
            }
        } catch (Exception e) {
            callback.log("Kapatma hatası: " + e.getMessage());
        }
    }

    private void moveAppToBackground(String packageName) {
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(homeIntent);
            callback.log("Uygulama arka plana gönderildi: " + packageName);
        } catch (Exception e) {
            callback.log("moveAppToBackground hatası: " + e.getMessage());
        }
    }

}
