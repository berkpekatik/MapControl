package com.mapcontrol.manager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Tek dosyalık multipart gövdesini belleğe almadan işler (GlitchTip #2301: 1 GB'lık yükleme tek byte[]'a
 * alınıyordu → OutOfMemoryError). Gövde {@link Limited} ile Content-Length'te kesilir.
 */
final class MultipartStream {

    private static final byte[] CRLF2 = {'\r', '\n', '\r', '\n'};
    static final int BUF = 64 * 1024;

    private MultipartStream() {
    }

    /** Content-Length'i aşmayan ve okunanı sayan sarmalayıcı. */
    static final class Limited extends InputStream {
        private final InputStream in;
        private final long limit;
        private long read;

        Limited(InputStream in, long limit) {
            this.in = in;
            this.limit = limit;
        }

        long consumed() {
            return read;
        }

        @Override
        public int read() throws IOException {
            if (read >= limit) {
                return -1;
            }
            int b = in.read();
            if (b >= 0) {
                read++;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (read >= limit) {
                return -1;
            }
            int n = in.read(b, off, (int) Math.min(len, limit - read));
            if (n > 0) {
                read += n;
            }
            return n;
        }

        /** Kalan gövdeyi okuyup atar (yanıt bağlantıda düzgün gitsin). */
        void drain() throws IOException {
            byte[] tmp = new byte[8192];
            while (read(tmp, 0, tmp.length) > 0) {
                // at
            }
        }
    }

    /**
     * İlk parçanın başlığını "\r\n\r\n" dahil okur. [max] bayt içinde bitmezse ya da akış biterse null.
     */
    static byte[] readPartHeader(InputStream in, int max) throws IOException {
        byte[] buf = new byte[max];
        int n = 0;
        while (n < max) {
            int b = in.read();
            if (b < 0) {
                return null;
            }
            buf[n++] = (byte) b;
            if (n >= 4 && buf[n - 4] == CRLF2[0] && buf[n - 3] == CRLF2[1]
                    && buf[n - 2] == CRLF2[2] && buf[n - 1] == CRLF2[3]) {
                byte[] out = new byte[n];
                System.arraycopy(buf, 0, out, 0, n);
                return out;
            }
        }
        return null;
    }

    /**
     * [delimiter] görülene kadar okunanı [out]'a yazar; ayıracın kendisi yazılmaz. Yazılan bayt sayısı,
     * ayıraç hiç görülmezse -1 (o durumda [out]'a yazılanlar geçersizdir).
     */
    static long copyUntil(InputStream in, byte[] delimiter, OutputStream out) throws IOException {
        final int d = delimiter.length;
        // buf = önceki turdan taşınan (en çok d-1) bayt + yeni okunan.
        byte[] buf = new byte[BUF + d];
        int carry = 0;
        long written = 0;
        while (true) {
            int n = in.read(buf, carry, BUF);
            if (n < 0) {
                return -1;
            }
            int len = carry + n;
            int hit = indexOf(buf, len, delimiter);
            if (hit >= 0) {
                out.write(buf, 0, hit);
                return written + hit;
            }
            // Sondaki d-1 bayt ayıracın başı olabilir: tut, gerisini yaz.
            int keep = Math.min(d - 1, len);
            int flush = len - keep;
            if (flush > 0) {
                out.write(buf, 0, flush);
                written += flush;
            }
            System.arraycopy(buf, flush, buf, 0, keep);
            carry = keep;
        }
    }

    static int indexOf(byte[] hay, int len, byte[] needle) {
        outer:
        for (int i = 0; i <= len - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /** Başlıktaki dosya adı (tırnaklı ya da tırnaksız); dizin parçaları atılır. Yoksa null. */
    static String fileName(String partHeader) {
        String name = null;
        int q = partHeader.indexOf("filename=\"");
        if (q != -1) {
            int start = q + 10;
            int end = partHeader.indexOf('"', start);
            if (end != -1) {
                name = partHeader.substring(start, end);
            }
        } else {
            int p = partHeader.indexOf("filename=");
            if (p != -1) {
                int start = p + 9;
                int end = partHeader.indexOf("\r\n", start);
                if (end == -1) {
                    end = partHeader.length();
                }
                name = partHeader.substring(start, end).trim();
            }
        }
        if (name == null) {
            return null;
        }
        // "../x", "C:\\a\\b.apk" → yalnızca son parça; Download dışına yazılamaz.
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(slash + 1).trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return null;
        }
        return name;
    }
}
