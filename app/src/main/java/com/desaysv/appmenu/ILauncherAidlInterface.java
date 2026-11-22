package com.desaysv.appmenu;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

import com.desaysv.launcher.svwallpaper.data.WallpaperInfo;

import java.util.List;

/**
 * Matches {@code com.desaysv.appmenu.ILauncherAidlInterface} exposed by
 * {@code com.desaysv.launcher.service.LauncherService} in SVLauncher.apk.
 */
public interface ILauncherAidlInterface extends IInterface {

    abstract class Stub extends Binder implements ILauncherAidlInterface {
        private static final String DESCRIPTOR = "com.desaysv.appmenu.ILauncherAidlInterface";
        static final int TRANSACTION_setWallpaper = 1;
        static final int TRANSACTION_deleteLauncherWallpaper = 2;
        static final int TRANSACTION_deleteWallpaperInfo = 3;
        static final int TRANSACTION_regCallBack = 4;
        static final int TRANSACTION_unRegCallBack = 5;
        static final int TRANSACTION_getAllCustomWallpapers = 6;
        static final int TRANSACTION_setWallpaperList = 7;
        static final int TRANSACTION_addWallpaper = 8;
        static final int TRANSACTION_getCurrWallPaperInfo = 9;
        static final int TRANSACTION_isWallPaperInfoAdded = 10;
        static final int TRANSACTION_isWallPaperIDAdded = 11;
        static final int TRANSACTION_getPresetWallpaperList = 12;
        static final int TRANSACTION_getResetWallpaperList = 13;
        static final int TRANSACTION_getSelectedWallpaperList = 14;
        static final int TRANSACTION_switchWallpaperStyle = 15;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static ILauncherAidlInterface asInterface(IBinder binder) {
            if (binder == null) {
                return null;
            }
            IInterface iin = binder.queryLocalInterface(DESCRIPTOR);
            if (iin instanceof ILauncherAidlInterface) {
                return (ILauncherAidlInterface) iin;
            }
            return new Proxy(binder);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(DESCRIPTOR);
                return true;
            }
            switch (code) {
                case TRANSACTION_setWallpaper: {
                    data.enforceInterface(DESCRIPTOR);
                    WallpaperInfo info = data.readInt() != 0 ? WallpaperInfo.CREATOR.createFromParcel(data) : null;
                    setWallpaper(info);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_deleteLauncherWallpaper: {
                    data.enforceInterface(DESCRIPTOR);
                    deleteLauncherWallpaper(data.readString());
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_deleteWallpaperInfo: {
                    data.enforceInterface(DESCRIPTOR);
                    WallpaperInfo info = data.readInt() != 0 ? WallpaperInfo.CREATOR.createFromParcel(data) : null;
                    deleteWallpaperInfo(info);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_regCallBack: {
                    data.enforceInterface(DESCRIPTOR);
                    regCallBack(IWallpaperEventCallBack.Stub.asInterface(data.readStrongBinder()));
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_unRegCallBack: {
                    data.enforceInterface(DESCRIPTOR);
                    unRegCallBack(IWallpaperEventCallBack.Stub.asInterface(data.readStrongBinder()));
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_getAllCustomWallpapers: {
                    data.enforceInterface(DESCRIPTOR);
                    List<WallpaperInfo> res = getAllCustomWallpapers();
                    reply.writeNoException();
                    reply.writeTypedList(res);
                    return true;
                }
                case TRANSACTION_setWallpaperList: {
                    data.enforceInterface(DESCRIPTOR);
                    setWallpaperList(data.createTypedArrayList(WallpaperInfo.CREATOR), data.readInt());
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_addWallpaper: {
                    data.enforceInterface(DESCRIPTOR);
                    WallpaperInfo info = data.readInt() != 0 ? WallpaperInfo.CREATOR.createFromParcel(data) : null;
                    addWallpaper(info);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_getCurrWallPaperInfo: {
                    data.enforceInterface(DESCRIPTOR);
                    WallpaperInfo cur = getCurrWallPaperInfo();
                    reply.writeNoException();
                    if (cur != null) {
                        reply.writeInt(1);
                        cur.writeToParcel(reply, ParcelableFlagsHolder.WRITE_RETURN_VALUE);
                    } else {
                        reply.writeInt(0);
                    }
                    return true;
                }
                case TRANSACTION_isWallPaperInfoAdded: {
                    data.enforceInterface(DESCRIPTOR);
                    WallpaperInfo info = data.readInt() != 0 ? WallpaperInfo.CREATOR.createFromParcel(data) : null;
                    boolean added = isWallPaperInfoAdded(info);
                    reply.writeNoException();
                    reply.writeInt(added ? 1 : 0);
                    return true;
                }
                case TRANSACTION_isWallPaperIDAdded: {
                    data.enforceInterface(DESCRIPTOR);
                    boolean added = isWallPaperIDAdded(data.readString());
                    reply.writeNoException();
                    reply.writeInt(added ? 1 : 0);
                    return true;
                }
                case TRANSACTION_getPresetWallpaperList: {
                    data.enforceInterface(DESCRIPTOR);
                    List<WallpaperInfo> list = getPresetWallpaperList();
                    reply.writeNoException();
                    reply.writeTypedList(list);
                    return true;
                }
                case TRANSACTION_getResetWallpaperList: {
                    data.enforceInterface(DESCRIPTOR);
                    List<WallpaperInfo> list = getResetWallpaperList();
                    reply.writeNoException();
                    reply.writeTypedList(list);
                    return true;
                }
                case TRANSACTION_getSelectedWallpaperList: {
                    data.enforceInterface(DESCRIPTOR);
                    List<WallpaperInfo> list = getSelectedWallpaperList(data.readInt());
                    reply.writeNoException();
                    reply.writeTypedList(list);
                    return true;
                }
                case TRANSACTION_switchWallpaperStyle: {
                    data.enforceInterface(DESCRIPTOR);
                    switchWallpaperStyle(data.readInt());
                    reply.writeNoException();
                    return true;
                }
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static final class ParcelableFlagsHolder {
            static final int WRITE_RETURN_VALUE = android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE;
        }

        private static final class Proxy implements ILauncherAidlInterface {
            private final IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            @Override
            public IBinder asBinder() {
                return mRemote;
            }

            @Override
            public void setWallpaper(WallpaperInfo wallpaperInfo) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (wallpaperInfo != null) {
                        data.writeInt(1);
                        wallpaperInfo.writeToParcel(data, 0);
                    } else {
                        data.writeInt(0);
                    }
                    mRemote.transact(TRANSACTION_setWallpaper, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void deleteLauncherWallpaper(String id) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeString(id);
                    mRemote.transact(TRANSACTION_deleteLauncherWallpaper, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void deleteWallpaperInfo(WallpaperInfo wallpaperInfo) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (wallpaperInfo != null) {
                        data.writeInt(1);
                        wallpaperInfo.writeToParcel(data, 0);
                    } else {
                        data.writeInt(0);
                    }
                    mRemote.transact(TRANSACTION_deleteWallpaperInfo, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void regCallBack(IWallpaperEventCallBack callBack) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeStrongBinder(callBack != null ? callBack.asBinder() : null);
                    mRemote.transact(TRANSACTION_regCallBack, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void unRegCallBack(IWallpaperEventCallBack callBack) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeStrongBinder(callBack != null ? callBack.asBinder() : null);
                    mRemote.transact(TRANSACTION_unRegCallBack, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public List<WallpaperInfo> getAllCustomWallpapers() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_getAllCustomWallpapers, data, reply, 0);
                    reply.readException();
                    return reply.createTypedArrayList(WallpaperInfo.CREATOR);
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void setWallpaperList(List<WallpaperInfo> list, int index) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeTypedList(list);
                    data.writeInt(index);
                    mRemote.transact(TRANSACTION_setWallpaperList, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void addWallpaper(WallpaperInfo wallpaperInfo) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (wallpaperInfo != null) {
                        data.writeInt(1);
                        wallpaperInfo.writeToParcel(data, 0);
                    } else {
                        data.writeInt(0);
                    }
                    mRemote.transact(TRANSACTION_addWallpaper, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public WallpaperInfo getCurrWallPaperInfo() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_getCurrWallPaperInfo, data, reply, 0);
                    reply.readException();
                    return reply.readInt() != 0 ? WallpaperInfo.CREATOR.createFromParcel(reply) : null;
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public boolean isWallPaperInfoAdded(WallpaperInfo wallpaperInfo) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (wallpaperInfo != null) {
                        data.writeInt(1);
                        wallpaperInfo.writeToParcel(data, 0);
                    } else {
                        data.writeInt(0);
                    }
                    mRemote.transact(TRANSACTION_isWallPaperInfoAdded, data, reply, 0);
                    reply.readException();
                    return reply.readInt() != 0;
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public boolean isWallPaperIDAdded(String id) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeString(id);
                    mRemote.transact(TRANSACTION_isWallPaperIDAdded, data, reply, 0);
                    reply.readException();
                    return reply.readInt() != 0;
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public List<WallpaperInfo> getPresetWallpaperList() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_getPresetWallpaperList, data, reply, 0);
                    reply.readException();
                    return reply.createTypedArrayList(WallpaperInfo.CREATOR);
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public List<WallpaperInfo> getResetWallpaperList() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_getResetWallpaperList, data, reply, 0);
                    reply.readException();
                    return reply.createTypedArrayList(WallpaperInfo.CREATOR);
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public List<WallpaperInfo> getSelectedWallpaperList(int index) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(index);
                    mRemote.transact(TRANSACTION_getSelectedWallpaperList, data, reply, 0);
                    reply.readException();
                    return reply.createTypedArrayList(WallpaperInfo.CREATOR);
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void switchWallpaperStyle(int style) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(style);
                    mRemote.transact(TRANSACTION_switchWallpaperStyle, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }
        }
    }

    void addWallpaper(WallpaperInfo wallpaperInfo) throws RemoteException;

    void deleteLauncherWallpaper(String id) throws RemoteException;

    void deleteWallpaperInfo(WallpaperInfo wallpaperInfo) throws RemoteException;

    List<WallpaperInfo> getAllCustomWallpapers() throws RemoteException;

    WallpaperInfo getCurrWallPaperInfo() throws RemoteException;

    List<WallpaperInfo> getPresetWallpaperList() throws RemoteException;

    List<WallpaperInfo> getResetWallpaperList() throws RemoteException;

    List<WallpaperInfo> getSelectedWallpaperList(int index) throws RemoteException;

    boolean isWallPaperIDAdded(String id) throws RemoteException;

    boolean isWallPaperInfoAdded(WallpaperInfo wallpaperInfo) throws RemoteException;

    void regCallBack(IWallpaperEventCallBack callBack) throws RemoteException;

    void setWallpaper(WallpaperInfo wallpaperInfo) throws RemoteException;

    void setWallpaperList(List<WallpaperInfo> list, int index) throws RemoteException;

    void switchWallpaperStyle(int style) throws RemoteException;

    void unRegCallBack(IWallpaperEventCallBack callBack) throws RemoteException;
}
