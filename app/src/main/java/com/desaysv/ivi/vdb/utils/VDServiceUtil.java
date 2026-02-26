package com.desaysv.ivi.vdb.utils;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;

import java.lang.reflect.Method;
import java.util.List;

/* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
public class VDServiceUtil {
    public static void addServiceToServiceManager(IBinder binder, String name) {
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method addService = smClass.getMethod("addService", String.class, IBinder.class);
            addService.invoke(null, name, binder);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Notification getNotification(Context context, String channelId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null;
        }
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return null;
        }
        NotificationChannel channel = new NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(channel);
        Notification notification = new Notification.Builder(context, channelId)
                .setContentTitle(channelId)
                .setContentText(channelId)
                .setTicker(channelId)
                .setPriority(Notification.PRIORITY_LOW)
                .setWhen(System.currentTimeMillis())
                .setDefaults(Notification.DEFAULT_LIGHTS)
                .build();
        notification.flags = Notification.FLAG_ONGOING_EVENT;
        return notification;
    }

    public static IBinder getServiceFromServiceManager(String name) {
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getService = smClass.getMethod("getService", String.class);
            return (IBinder) getService.invoke(null, name);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean isServiceStarted(Context context, String str) {
        List<ActivityManager.RunningServiceInfo> runningServices = ((ActivityManager) context.getSystemService("activity")).getRunningServices(1000);
        for (int i9 = 0; i9 < runningServices.size(); i9++) {
            if (runningServices.get(i9).service.getClassName().equals(str)) {
                return true;
            }
        }
        return false;
    }

    public static void startForeground(Service service) {
        String simpleName = service.getClass().getSimpleName();
        if (Build.VERSION.SDK_INT >= 26) {
            service.startForeground(Process.myPid(), getNotification(service, simpleName));
        }
    }
}
