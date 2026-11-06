package com.mapcontrol.dvr;

/** Park fotoğrafı için piksel dönüşümleri. Android'e bağlı değildir. */
public final class ParkPhotoImage {

    private ParkPhotoImage() {
    }

    /**
     * Düzlemsel I420 (Y, sonra U, sonra V) karesini {@code YuvImage}'in beklediği NV21'e (Y, sonra çift sıralı V,U) çevirir.
     * Mozaik, kayıt kodlayıcısının girişi olan {@code COLOR_FormatYUV420Planar} düzeninde üretilir.
     *
     * @param i420 {@code width * height * 3 / 2} bayt
     */
    public static byte[] i420ToNv21(byte[] i420, int width, int height) {
        int ySize = width * height;
        int quarter = ySize / 4;
        if (width <= 0 || height <= 0 || (width & 1) != 0 || (height & 1) != 0 || i420.length < ySize + 2 * quarter) {
            throw new IllegalArgumentException("I420 kare boyutu uyuşmuyor: " + width + "x" + height + ", " + i420.length);
        }
        byte[] nv21 = new byte[ySize + 2 * quarter];
        System.arraycopy(i420, 0, nv21, 0, ySize);
        int u = ySize;
        int v = ySize + quarter;
        int out = ySize;
        for (int i = 0; i < quarter; i++) {
            nv21[out++] = i420[v + i];
            nv21[out++] = i420[u + i];
        }
        return nv21;
    }
}
