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

    public ClusterDisplayManager(Context context, ClusterCallback callback) {
        this.context = context;
        this.callback = callback;
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void openClusterDisplay() {
        try {
            int targetDisplay = AppLaunchHelper.getClusterDisplayId(context);
            if (targetDisplay != 0) {
                DisplayHelper.showPreparingMessageOnDisplay(context, targetDisplay);
                Runnable timeoutRunnable = () -> {
                    callback.log("⚠️ İşlem zaman aşımına uğradı, mesaj gizleniyor.");
                    DisplayHelper.hidePreparingMessage();
                };
                handler.postDelayed(timeoutRunnable, 7000);
            }

            String appOnDisplay2 = getAppOnDisplay2();
            if (appOnDisplay2 != null && !appOnDisplay2.isEmpty() && !"com.mapcontrol".equals(appOnDisplay2)) {
                callback.log("Display 2'de açık uygulama bulundu: " + appOnDisplay2 + " - Display 0'a taşınıyor ve kapatılıyor");
                try {
                    if (appOnDisplay2 == null || appOnDisplay2.equals("com.mapcontrol")) {
                        callback.log("com.mapcontrol paketi taşınmayacak: " + appOnDisplay2);
                    } else {
                        PackageManager pm = context.getPackageManager();
                        Intent li = pm.getLaunchIntentForPackage(appOnDisplay2);
                        if (li == null) {
                            callback.log("Uygulama intent bulunamadı: " + appOnDisplay2);
                        } else {
                            AppLaunchHelper.moveAppToDefaultDisplay(context, appOnDisplay2);
                            callback.log("Uygulama default display'e taşındı (arka planda): " + appOnDisplay2);
                        }
                    }
                } catch (Exception e) {
                    callback.log("moveAppToDefaultDisplay hatası: " + e.getMessage());
                }
                Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(homeIntent);
            } else if ("com.mapcontrol".equals(appOnDisplay2)) {
                callback.log("Display 2'de com.mapcontrol bulundu - taşıma işlemi atlandı");
            }

            VDNaviDisplayCluster payloadwakeUp = new VDNaviDisplayCluster();
            payloadwakeUp.setNaviFrontDeskStatus("true");
            payloadwakeUp.setDisplayCluster("true");
            payloadwakeUp.setPerspective(0);
            payloadwakeUp.setPerspectiveResult("false");
            payloadwakeUp.setRequestDisplayNaviArea("true");
            VDEvent eventwakeUp = VDNaviDisplayCluster.createEvent(VDEventCarLan.NAVIGATION_DISPLAY_TO_CLUSTER, payloadwakeUp);
            VDBus.getDefault().set(eventwakeUp);

            handler.postDelayed(() -> {
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

    public void closeClusterDisplay(boolean sendBackground) {
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

            callback.onNavigationStateChanged(false);
            callback.log("Navigasyon paneli kapatıldı");

            String targetPackage = callback.getTargetPackage();
            if (targetPackage != null && !targetPackage.trim().isEmpty()) {
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

    private String getAppOnDisplay2() {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
                if (processes != null) {
                    for (ActivityManager.RunningAppProcessInfo processInfo : processes) {
                        if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                                && processInfo.pkgList != null && processInfo.pkgList.length > 0) {
                            String packageName = processInfo.pkgList[0];
                            if (!"com.mapcontrol".equals(packageName) && !isSystemOrPrivApp(packageName)) {
                                return packageName;
                            }
                        }
                    }
                }
            }

            Process process = Runtime.getRuntime().exec("dumpsys activity activities | grep -A 5 'displayId=2'");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String lastPackage = null;
            while ((line = reader.readLine()) != null) {
                if (!line.contains("com.")) {
                    continue;
                }
                int startIndex = line.indexOf("com.");
                if (startIndex == -1) {
                    continue;
                }
                int endIndex = line.indexOf("/", startIndex);
                if (endIndex == -1) {
                    endIndex = line.indexOf(" ", startIndex);
                }
                if (endIndex == -1) {
                    endIndex = line.length();
                }
                String packageName = line.substring(startIndex, endIndex).trim();
                if (!"com.mapcontrol".equals(packageName) && !isSystemOrPrivApp(packageName)) {
                    lastPackage = packageName;
                }
            }
            reader.close();
            if (lastPackage != null) {
                callback.log("Display 2'de uygulama bulundu: " + lastPackage);
            }
            return lastPackage;
        } catch (Exception e) {
            callback.log("getAppOnDisplay2 hatası: " + e.getMessage());
            return null;
        }
    }

    private boolean isSystemOrPrivApp(String packageName) {
        try {
            if (packageName == null || packageName.isEmpty()) {
                return true;
            }
            if ("com.mapcontrol".equals(packageName)) {
                return false;
            }
            PackageManager pm = context.getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean isUpdatedSystemApp = (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            if (isSystemApp || isUpdatedSystemApp) {
                return true;
            }
            String sourceDir = appInfo.sourceDir;
            String publicSourceDir = appInfo.publicSourceDir;
            if (sourceDir != null && (sourceDir.contains("/system/priv-app/") || sourceDir.contains("/system/app/"))) {
                return true;
            }
            if (publicSourceDir != null && (publicSourceDir.contains("/system/priv-app/") || publicSourceDir.contains("/system/app/"))) {
                return true;
            }
            return false;
        } catch (Exception ignored) {
            return true;
        }
    }
}
