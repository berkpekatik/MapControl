package com.mapcontrol.dvr;

import android.util.Log;

/**
 * Native HAB köprüsü: canlı akış için kamera hub'ı (kamera başına bağımsız oturum,
 * CPU kopyası veya EGLImage ile sıfır kopya) ve CPU kayıt yolu dönüşümleri.
 * Akış yönetimi {@link CameraHub} üzerinden yapılır.
 */
public final class HabNative {
    private static final String TAG = "HabNative";

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

    private static native int nativeHubStart();

    private static native void nativeHubStop();

    private static native boolean nativeHubRestart(int cam);

    private static native int nativeHubStatus(int cam);

    private static native int nativeHubGrab(int cam, java.nio.ByteBuffer out, int mode, boolean hold);

    private static native boolean nativeZcInit();

    private static native long nativeZcAcquire(int cam);

    private static native void nativeZcDetach(int cam);

    private static native void nativeZcFenceInsert();

    private static native void nativeSetIonCached(boolean enabled);

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

    /**
     * Kamera tamponlari onbellekli ayrilsin mi ("hizli okuma"). Acikken CPU kare kopyasi
     * belirgin ucuzlar; her okuma DMA_BUF sync ile korunur, sorun cikarsa kamera kendiliginden
     * onbelleksiz moda doner. Degisiklik kameralar yeniden acilinca gecerli olur.
     */
    public static void setIonCached(boolean enabled) {
        if (!LOADED) return;
        try {
            nativeSetIonCached(enabled);
        } catch (Throwable t) {
            Log.w(TAG, "ion onbellek ayari hata", t);
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

    /**
     * GL thread'inde, sifir kopya cizimden sonra (glFinish yerine): GPU bitis isareti koyar.
     * Kare iadesi bir sonraki zcAcquire/zcDetach'te bu isaret beklenerek yapilir.
     * Fence desteklenmiyorsa native taraf glFinish yapar. Hata olursa da glFinish'e duser.
     */
    public static void zcFenceInsert() {
        if (LOADED) {
            try {
                nativeZcFenceInsert();
                return;
            } catch (Throwable t) {
                Log.w(TAG, "zc fence hata", t);
            }
        }
        android.opengl.GLES20.glFinish();
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

    /** Canli kameralar (id sirasi kamera indeksiyle ayni: On, Sag, Sol, Arka). */
    public static final int[] CAMERA_IDS = {5, 4, 8, 9};
}
