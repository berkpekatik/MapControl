package com.mapcontrol.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Kablosuz CarPlay / Android Auto oturumunu, Wi-Fi radyosu değişmeden önce kapatır.
 * Baş ünitedeki {@code IPhoneLinkManager} binder'ına konuşur.
 */
public final class PhoneLinkSession {

    public enum Release {
        /** Oturum kapandı veya zaten kapalıydı. */
        INACTIVE,
        /** Servis bulunamadı ya da bağlanılamadı. */
        UNREACHABLE,
        /** Kesme gitti, süre dolunca oturum hâlâ açıktı. */
        STILL_ACTIVE
    }

    private static final String DESCRIPTOR = "com.desaysv.ipc.IPhoneLinkManager";
    private static final String ACTION_TEST = "com.desaysv.service.action.TEST_LINK_SERVICE";
    private static final String ACTION_LINK = "com.desaysv.service.action.LINK_SERVICE";
    private static final String EXTRA_CLIENT_PACKAGE = "EXTRA_CLIENT_PACKAGE";
    private static final String LINK_DEVICE = "com.desaysv.persistence.model.LinkDevice";

    private static final int TRANSACT_SET = 1;
    private static final int TRANSACT_GET = 2;
    private static final int DATA_ID_STATUS = 1;
    private static final int DATA_ID_DISCONNECT = 22;
    private static final int[] LINK_TYPES = {1, 2};
    private static final int CONNECTED = 2;

    private static final long BIND_MS = 2000L;
    private static final long WAIT_MS = 3000L;
    private static final long POLL_MS = 300L;

    private PhoneLinkSession() {
    }

    @NonNull
    public static Release releaseWireless(@NonNull Context context) {
        Context app = context.getApplicationContext();
        ComponentName component = resolve(app);
        if (component == null) {
            return Release.UNREACHABLE;
        }
        Intent intent = new Intent();
        intent.setComponent(component);
        intent.putExtra(EXTRA_CLIENT_PACKAGE, app.getPackageName());

        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBinder> binderRef = new AtomicReference<>();
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                binderRef.set(service);
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                binderRef.set(null);
            }

            @Override
            public void onNullBinding(ComponentName name) {
                binderRef.set(null);
                connected.countDown();
            }

            @Override
            public void onBindingDied(ComponentName name) {
                binderRef.set(null);
            }
        };

        Handler main = new Handler(Looper.getMainLooper());
        boolean[] bindOk = {false};
        main.post(() -> {
            try {
                app.startForegroundService(intent);
            } catch (Throwable ignored) {
            }
            try {
                bindOk[0] = app.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            } catch (Throwable ignored) {
                bindOk[0] = false;
            }
            if (!bindOk[0]) {
                connected.countDown();
            }
        });

        try {
            if (!connected.await(BIND_MS, TimeUnit.MILLISECONDS)) {
                return Release.UNREACHABLE;
            }
            IBinder binder = binderRef.get();
            if (binder == null || !binder.isBinderAlive()) {
                return Release.UNREACHABLE;
            }
            for (int type : LINK_TYPES) {
                transactDisconnect(binder, type);
            }
            long deadline = SystemClock.uptimeMillis() + WAIT_MS;
            while (SystemClock.uptimeMillis() < deadline) {
                LinkState state = query(binder);
                if (state == LinkState.INACTIVE) {
                    return Release.INACTIVE;
                }
                try {
                    Thread.sleep(POLL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Release.STILL_ACTIVE;
                }
            }
            return query(binder) == LinkState.INACTIVE ? Release.INACTIVE : Release.STILL_ACTIVE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Release.UNREACHABLE;
        } finally {
            main.post(() -> {
                if (!bindOk[0]) {
                    return;
                }
                try {
                    app.unbindService(connection);
                } catch (Throwable ignored) {
                }
            });
        }
    }

    @Nullable
    private static ComponentName resolve(Context context) {
        PackageManager packages = context.getPackageManager();
        ComponentName test = componentOf(packages, ACTION_TEST);
        if (test != null) {
            return test;
        }
        return componentOf(packages, ACTION_LINK);
    }

    @Nullable
    private static ComponentName componentOf(PackageManager packages, String action) {
        ResolveInfo info;
        try {
            info = packages.resolveService(new Intent(action), 0);
        } catch (Throwable ignored) {
            return null;
        }
        if (info == null) {
            return null;
        }
        ServiceInfo service = info.serviceInfo;
        if (service == null) {
            return null;
        }
        return new ComponentName(service.packageName, service.name);
    }

    private static void transactDisconnect(IBinder binder, int linkType) {
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(1);
            writePayload(data, 0, DATA_ID_DISCONNECT, linkType, true, false, true);
            binder.transact(TRANSACT_SET, data, null, IBinder.FLAG_ONEWAY);
        } catch (Throwable ignored) {
        } finally {
            data.recycle();
        }
    }

    @NonNull
    private static LinkState query(IBinder binder) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(1);
            writePayload(data, 0, DATA_ID_STATUS, 0, true, true, true);
            if (!binder.transact(TRANSACT_GET, data, reply, 0)) {
                return LinkState.UNKNOWN;
            }
            reply.readException();
            if (reply.readInt() == 0) {
                return LinkState.UNKNOWN;
            }
            return readState(reply);
        } catch (Throwable ignored) {
            return LinkState.UNKNOWN;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static void writePayload(Parcel parcel, int serverId, int dataId, int dataInt,
            boolean getter, boolean sync, boolean success) {
        parcel.writeInt(serverId);
        parcel.writeInt(dataId);
        parcel.writeString(null);
        parcel.writeInt(dataInt);
        parcel.writeByte(getter ? (byte) 1 : (byte) 0);
        parcel.writeByte((byte) 0);
        parcel.writeByte((byte) 0);
        parcel.writeByte((byte) 0);
        parcel.writeByte(sync ? (byte) 1 : (byte) 0);
        parcel.writeByte(success ? (byte) 1 : (byte) 0);
    }

    @NonNull
    private static LinkState readState(Parcel reply) {
        reply.readInt();
        reply.readInt();
        reply.readString();
        int dataInt = reply.readInt();
        reply.readByte();
        reply.readByte();
        if (reply.readByte() != 1) {
            return LinkState.INACTIVE;
        }
        reply.readString();
        String parcelClass = reply.readString();
        if (parcelClass == null) {
            return LinkState.INACTIVE;
        }
        if (!LINK_DEVICE.equals(parcelClass)) {
            return LinkState.UNKNOWN;
        }
        reply.readLong();
        reply.readInt();
        reply.readString();
        reply.readString();
        reply.readString();
        boolean wireless = reply.readByte() != 0;
        reply.readByte();
        reply.readByte();
        reply.readByte();
        reply.readLong();
        int connectStatus = reply.readInt();
        if ((dataInt == 1 || dataInt == 2) && wireless && connectStatus == CONNECTED) {
            return LinkState.ACTIVE;
        }
        return LinkState.INACTIVE;
    }

    private enum LinkState {
        ACTIVE,
        INACTIVE,
        UNKNOWN
    }
}
