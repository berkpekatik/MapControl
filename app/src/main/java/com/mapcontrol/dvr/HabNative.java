package com.mapcontrol.dvr;

import android.graphics.Bitmap;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Native HAB köprüsü: teşhis testleri (probe/oturum/tek kare) ve canlı akış
 * için kamera hub'ı (kamera başına bağımsız oturum, CPU kopyası veya
 * EGLImage ile sıfır kopya). Akış yönetimi {@link CameraHub} üzerinden yapılır.
 */
public final class HabNative {
    private static final String TAG = "HabNative";
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hab-native-probe");
        t.setDaemon(true);
        return t;
    });

    private static final boolean LOADED;

    static {
        boolean ok;
        try {
            System.loadLibrary("mapcontrol_hab");
            ok = true;
        } catch (Throwable t) {
            Log.w(TAG, "libmapcontrol_hab yüklenemedi", t);
            ok = false;
        }
        LOADED = ok;
    }

    private HabNative() {
    }

    private static native String nativeProbe();

    private static native String nativeSessionTest();

    private static native String nativeStreamTest();

    private static native byte[] nativeCaptureFrame();

    private static native int nativeHubStart();

    private static native void nativeHubStop();

    private static native boolean nativeHubRestart(int cam);

    private static native int nativeHubStatus(int cam);

    private static native int nativeHubGrab(int cam, java.nio.ByteBuffer out, int mode, boolean hold);

    private static native boolean nativeZcInit();

    private static native long nativeZcAcquire(int cam);

    private static native void nativeZcDetach(int cam);

    private static native void nativeZcShutdown();

    private static native int[] nativeHubDescriptor(int cam);

    private static native String nativeHubError(int cam);

    private static native void nativeComposeMosaicSized(java.nio.ByteBuffer[] srcs, int[] srcWidths,
                                                     int[] srcHeights, java.nio.ByteBuffer out,
                                                     int outW, int outH, String line1, String line2);

    private static native void nativeConvertSingle(java.nio.ByteBuffer src, int srcWidth,
                                                     int srcHeight, java.nio.ByteBuffer out,
                                                     int outW, int outH, String label,
                                                     String line1, String line2);

    public static boolean isLoaded() {
        return LOADED;
    }

    /** Kopya modlari (native kCopy* ile ayni). */
    public static final int COPY_NONE = 0;
    public static final int COPY_FULL = 1;
    /** Her ikinci satir: W x H/2 YUYV (izgara/mozaik icin yeterli, 4 kat az bant). */
    public static final int COPY_HALF = 2;

    /** {@link #hubDescriptor} dizisinin alanlari. */
    public static final int DESC_ID = 0;
    public static final int DESC_WIDTH = 1;
    public static final int DESC_HEIGHT = 2;
    public static final int DESC_FORMAT = 3;
    /** fps * 100 */
    public static final int DESC_FPS_X100 = 4;

    /**
     * Tum kameralari bagimsiz oturumlarla acar (kismi acilis). Acilan kameralarin
     * bit maskesi; -1 = ION/HAB kullanilamiyor. Bloklar, arka thread'de cagir.
     */
    public static int hubStart() {
        if (!LOADED) return -1;
        try {
            return nativeHubStart();
        } catch (Throwable t) {
            Log.w(TAG, "hub baslatma hata", t);
            return -1;
        }
    }

    public static void hubStop() {
        if (!LOADED) return;
        try {
            nativeHubStop();
        } catch (Throwable t) {
            Log.w(TAG, "hub durdurma hata", t);
        }
    }

    /** Tek kamerayi kapatip yeniden acar (watchdog / sinyal yenileme). */
    public static boolean hubRestart(int cam) {
        if (!LOADED) return false;
        try {
            return nativeHubRestart(cam);
        } catch (Throwable t) {
            Log.w(TAG, "kamera yeniden baslatma hata", t);
            return false;
        }
    }

    /** Son acilis sonucu: 0=bilinmiyor, 1=sinyal var, 2=yok/desteklenmeyen format. */
    public static int hubStatus(int cam) {
        if (!LOADED) return 0;
        try {
            return nativeHubStatus(cam);
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Acik kameranin giris listesinden (tip 0) gelen geometrisi, alanlar DESC_*;
     * kamera kapaliysa null.
     */
    public static int[] hubDescriptor(int cam) {
        if (!LOADED) return null;
        try {
            return nativeHubDescriptor(cam);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Son acilis hatasi: "kod|ayrinti". Kod: 1=id yok, 2=format, 3=geometri, 4=acilis. */
    public static String hubError(int cam) {
        if (!LOADED) return null;
        try {
            return nativeHubError(cam);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Siradaki kareyi alir. out/mode: istege bagli CPU kopyasi (COPY_*);
     * hold: kare GL tarafinda sifir kopya ile cizilecek (zcAcquire ile alinir).
     * 1 = yeni kare, 0 = kare yok, -1 = hata/kapali.
     */
    public static int hubGrab(int cam, java.nio.ByteBuffer out, int mode, boolean hold) {
        if (!LOADED) return -1;
        try {
            return nativeHubGrab(cam, out, mode, hold);
        } catch (Throwable t) {
            Log.w(TAG, "kare alma hata", t);
            return -1;
        }
    }

    /** GL thread'inde: dma-buf -> EGLImage destegi varsa true. */
    public static boolean zcInit() {
        if (!LOADED) return false;
        try {
            return nativeZcInit();
        } catch (Throwable t) {
            Log.w(TAG, "zc init hata", t);
            return false;
        }
    }

    /**
     * GL thread'inde: kameranin cizilecek dokusu. &lt;0 = sifir kopya calismiyor;
     * alt 32 bit doku (0 = kare yok), bit 32 = yeni kare.
     */
    public static long zcAcquire(int cam) {
        if (!LOADED) return -1;
        try {
            return nativeZcAcquire(cam);
        } catch (Throwable t) {
            Log.w(TAG, "zc acquire hata", t);
            return -1;
        }
    }

    public static void zcDetach(int cam) {
        if (!LOADED) return;
        try {
            nativeZcDetach(cam);
        } catch (Throwable t) {
            Log.w(TAG, "zc detach hata", t);
        }
    }

    /** GL thread'inde, baglam yok edilmeden once. */
    public static void zcShutdown() {
        if (!LOADED) return;
        try {
            nativeZcShutdown();
        } catch (Throwable t) {
            Log.w(TAG, "zc kapatma hata", t);
        }
    }

    /**
     * 4 YUYV kareyi tek I420 mozaik kareye cevirir; line1/line2 sol alta gomulur.
     * srcWidths/srcHeights: kaynak karenin boyutu (yukseklik tam veya yarim);
     * kaynak null ise kadran siyah.
     */
    public static void composeMosaic(java.nio.ByteBuffer[] srcs, int[] srcWidths, int[] srcHeights,
                                     java.nio.ByteBuffer out, int outW, int outH,
                                     String line1, String line2) {
        if (!LOADED) return;
        try {
            nativeComposeMosaicSized(srcs, srcWidths, srcHeights, out, outW, outH, line1, line2);
        } catch (Throwable t) {
            Log.w(TAG, "mozaik hata", t);
        }
    }

    /** Tek kamerayi istenen boyutta I420'ye cevirir (ayri kayit). */
    public static void convertSingle(java.nio.ByteBuffer src, int srcWidth, int srcHeight,
                                     java.nio.ByteBuffer out, int outW, int outH,
                                     String label, String line1, String line2) {
        if (!LOADED) return;
        try {
            nativeConvertSingle(src, srcWidth, srcHeight, out, outW, outH, label, line1, line2);
        } catch (Throwable t) {
            Log.w(TAG, "tek kare donusum hata", t);
        }
    }


    /** Varsayilan geometri; acik kameranin gercek boyutu {@link #hubDescriptor} ile alinir. */
    public static final int FRAME_WIDTH = 1280;
    public static final int FRAME_HEIGHT = 800;

    /** Varsayilan ham kare boyutu (YUYV 1280x800x2; tek kare testi bununla calisir). */
    public static final int YUYV_BYTES = FRAME_WIDTH * FRAME_HEIGHT * 2;

    /** Canli kameralar (id sirasi kamera indeksiyle ayni: On, Sag, Sol, Arka). */
    public static final int[] CAMERA_IDS = {5, 4, 8, 9};

    /** Tek kare yakala (YUYV baytları); başarısızsa null. Arka planda çalışır. */
    public static void captureFrameRawAsync(Consumer<byte[]> callback) {
        EXEC.execute(() -> {
            byte[] result = null;
            if (LOADED) {
                try {
                    result = nativeCaptureFrame();
                } catch (Throwable t) {
                    Log.w(TAG, "kare yakalama hata", t);
                }
            }
            if (callback != null) {
                callback.accept(result);
            }
        });
    }

    /** YUYV (Y0 U Y1 V) paketlenmiş kareyi Bitmap'e çevirir (BT.601). */
    public static Bitmap yuyvToBitmap(byte[] data, int width, int height) {
        if (data == null || data.length < width * height * 2) {
            return null;
        }
        int[] pixels = new int[width * height];
        for (int i = 0, p = 0; i + 3 < data.length && p < pixels.length - 1; i += 4) {
            int y0 = data[i] & 0xFF;
            int u = data[i + 1] & 0xFF;
            int y1 = data[i + 2] & 0xFF;
            int v = data[i + 3] & 0xFF;
            pixels[p++] = yuvToRgb(y0, u, v);
            pixels[p++] = yuvToRgb(y1, u, v);
        }
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.setPixels(pixels, 0, width, 0, 0, width, height);
        return bmp;
    }

    private static int yuvToRgb(int y, int u, int v) {
        int c = y - 16;
        if (c < 0) c = 0;
        int d = u - 128;
        int e = v - 128;
        int r = (298 * c + 409 * e + 128) >> 8;
        int g = (298 * c - 100 * d - 208 * e + 128) >> 8;
        int b = (298 * c + 516 * d + 128) >> 8;
        r = r < 0 ? 0 : (r > 255 ? 255 : r);
        g = g < 0 ? 0 : (g > 255 ? 255 : g);
        b = b < 0 ? 0 : (b > 255 ? 255 : b);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Tek stream denemesi: tip1 + ION + tip5/export + tip7 + 1 kare (tip11/12). */
    public static void streamTestAsync(Consumer<String> callback) {
        EXEC.execute(() -> {
            String result;
            if (!LOADED) {
                result = "HAB Stream: FAIL libmapcontrol_hab yüklenemedi";
            } else {
                try {
                    result = nativeStreamTest();
                } catch (Throwable t) {
                    result = "HAB Stream: FAIL " + t.getClass().getSimpleName() + ": " + t.getMessage();
                }
            }
            Log.i(TAG, result);
            if (callback != null) {
                callback.accept(result);
            }
        });
    }

    /** Oturum açma denemesi: kanal + tip 17/0/19 (stream/kare yok). */
    public static void sessionTestAsync(Consumer<String> callback) {
        EXEC.execute(() -> {
            String result;
            if (!LOADED) {
                result = "HAB Oturum: FAIL libmapcontrol_hab yüklenemedi";
            } else {
                try {
                    result = nativeSessionTest();
                } catch (Throwable t) {
                    result = "HAB Oturum: FAIL " + t.getClass().getSimpleName() + ": " + t.getMessage();
                }
            }
            Log.i(TAG, result);
            if (callback != null) {
                callback.accept(result);
            }
        });
    }

    public static void probeAsync(Consumer<String> callback) {
        EXEC.execute(() -> {
            String result;
            if (!LOADED) {
                result = "HAB Native: FAIL libmapcontrol_hab yüklenemedi";
            } else {
                try {
                    result = nativeProbe();
                } catch (Throwable t) {
                    result = "HAB Native: FAIL " + t.getClass().getSimpleName() + ": " + t.getMessage();
                }
            }
            Log.i(TAG, result);
            if (callback != null) {
                callback.accept(result);
            }
        });
    }
}
