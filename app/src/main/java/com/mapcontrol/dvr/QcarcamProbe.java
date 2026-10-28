package com.mapcontrol.dvr;

import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * qcarcam HIDL sorgusu (Faz 2): sadece getInputStreamList (transact code 1).
 * Akış açma / kare alma YOK. vendor.qti.automotive.qcarcam@1.0::IQcarCamera/default
 * servisine HwBinder reflection ile bağlanır (hedef SDK 28, gizli API erişimi serbest).
 *
 * Parcel düzeni (qcarcam_hal.so BpHw/BnHw analizinden):
 *  IQcarCamera: getInputStreamList=1, openStream=2, closeStream=3
 *  Reply: int32 error, hidl_vec<QcarcamInputInfo{ int64/id@0, string@8, string@24 }> (eleman 40 byte)
 */
public final class QcarcamProbe {
    private static final String TAG = "QcarcamProbe";
    private static final String DESCRIPTOR = "vendor.qti.automotive.qcarcam@1.0::IQcarCamera";
    private static final int CODE_GET_INPUT_STREAM_LIST = 1;
    private static final int INPUT_INFO_SIZE = 40;

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "qcarcam-probe");
        t.setDaemon(true);
        return t;
    });

    private QcarcamProbe() {
    }

    public static void queryInputListAsync(Consumer<String> callback) {
        EXEC.execute(() -> {
            String result;
            try {
                result = queryInputList();
            } catch (Throwable t) {
                Throwable cause = t instanceof java.lang.reflect.InvocationTargetException
                        && ((java.lang.reflect.InvocationTargetException) t).getCause() != null
                        ? ((java.lang.reflect.InvocationTargetException) t).getCause() : t;
                if (cause instanceof java.util.NoSuchElementException) {
                    result = "QCARCAM List: servis bulunamadı — SELinux { find } reddi "
                            + "(untrusted_app -> vendor_hal_ais_hwservice). HIDL yolu uygulama için kapalı; HAB yolu kullanılacak.";
                } else {
                    result = "QCARCAM List: FAIL " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
                }
                Log.w(TAG, result, t);
            }
            Log.i(TAG, result);
            if (callback != null) {
                callback.accept(result);
            }
        });
    }

    private static String queryInputList() throws Exception {
        Class<?> hwBinder = Class.forName("android.os.HwBinder");
        Class<?> hwParcel = Class.forName("android.os.HwParcel");
        Class<?> hwBlob = Class.forName("android.os.HwBlob");

        Object remote = hwBinder.getMethod("getService", String.class, String.class)
                .invoke(null, DESCRIPTOR, "default");
        if (remote == null) {
            return "QCARCAM List: service=null (IQcarCamera/default bulunamadı)";
        }

        Object request = hwParcel.getConstructor().newInstance();
        Object reply = hwParcel.getConstructor().newInstance();
        try {
            hwParcel.getMethod("writeInterfaceToken", String.class).invoke(request, DESCRIPTOR);
            Method transact = remote.getClass().getMethod(
                    "transact", int.class, hwParcel, hwParcel, int.class);
            transact.invoke(remote, CODE_GET_INPUT_STREAM_LIST, request, reply, 0);
            hwParcel.getMethod("verifySuccess").invoke(reply);
        } finally {
            try {
                hwParcel.getMethod("releaseTemporaryStorage").invoke(request);
            } catch (Exception ignored) {
            }
        }

        int error = (Integer) hwParcel.getMethod("readInt32").invoke(reply);
        Object vecBlob = hwParcel.getMethod("readBuffer", long.class).invoke(reply, 16L);
        int vecSize = (Integer) hwBlob.getMethod("getInt32", long.class).invoke(vecBlob, 8L);
        StringBuilder sb = new StringBuilder("QCARCAM List: error=").append(error)
                .append(" count=").append(vecSize);
        if (vecSize < 0 || vecSize > 64) {
            sb.append(" (şüpheli boyut, parse durduruldu)");
            release(reply, hwParcel);
            return sb.toString();
        }

        if (vecSize > 0) {
            long parentHandle = (Long) hwBlob.getMethod("handle").invoke(vecBlob);
            Object child = hwParcel.getMethod("readEmbeddedBuffer",
                    long.class, long.class, long.class, boolean.class)
                    .invoke(reply, (long) vecSize * INPUT_INFO_SIZE, parentHandle, 0L, true);
            for (int i = 0; i < vecSize; i++) {
                long off = (long) i * INPUT_INFO_SIZE;
                int id32 = (Integer) hwBlob.getMethod("getInt32", long.class).invoke(child, off);
                long id64 = (Long) hwBlob.getMethod("getInt64", long.class).invoke(child, off);
                String s1 = safeGetString(hwBlob, child, off + 8);
                String s2 = safeGetString(hwBlob, child, off + 24);
                sb.append("\n  [").append(i).append("] id32=").append(id32)
                        .append(" id64=").append(id64)
                        .append(" s1='").append(s1).append("' s2='").append(s2).append("'");
            }
        }
        release(reply, hwParcel);
        return sb.toString();
    }

    private static String safeGetString(Class<?> hwBlob, Object blob, long offset) {
        try {
            Object s = hwBlob.getMethod("getString", long.class).invoke(blob, offset);
            return s != null ? s.toString() : "null";
        } catch (Exception e) {
            return "<err:" + e.getClass().getSimpleName() + ">";
        }
    }

    private static void release(Object reply, Class<?> hwParcel) {
        try {
            hwParcel.getMethod("release").invoke(reply);
        } catch (Exception ignored) {
        }
    }
}
