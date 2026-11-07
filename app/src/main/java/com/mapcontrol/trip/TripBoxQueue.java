package com.mapcontrol.trip;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Sunucu kutusuna gidecek yolculuk paketlerinin diskteki kuyruğu. Paket ağa çıkmadan önce yazılır; gönderilince
 * silinir. Böylece kontak kapanışında internet kesilirse (kapı açılınca araç Wi-Fi'yi kapatır) paket kaybolmaz,
 * sonraki açılışta gider. Her yolculuğun yalnızca en yeni sürümü tutulur: sunucu aynı yolculukta son yazılanı
 * saklar, eski sürüm yeniyi ezmesin.
 *
 * <p>Dosya: {@code <tripId>.box} = 8 bayt sürüm ({@code X-Updated-At}) + şifresiz paket (yükleme anında eşleşme
 * anahtarıyla şifrelenir, anahtar değişse de doğru anahtarla gider). Android'e bağlı değildir.
 */
final class TripBoxQueue {

    /** Bu kadar paketten fazlası birikirse en eskisi düşer. */
    static final int MAX_ITEMS = 30;
    /** Bundan eski paket gönderilmez (sunucu da bir süre sonra siler; telefon zaten araçtan almıştır). */
    static final long MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000;

    private static final String SUFFIX = ".box";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    static final class Item {
        final String tripId;
        final long version;
        final byte[] payload;

        Item(String tripId, long version, byte[] payload) {
            this.tripId = tripId;
            this.version = version;
            this.payload = payload;
        }
    }

    private final File dir;

    TripBoxQueue(File dir) {
        this.dir = dir;
    }

    /** Aynı yolculuğun önceki sürümünün yerine yazar; yalnızca daha yeni sürüm kabul edilir. */
    synchronized boolean put(String tripId, long version, byte[] payload) {
        if (!SAFE_ID.matcher(tripId == null ? "" : tripId).matches() || payload == null) {
            return false;
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return false;
        }
        Item existing = read(file(tripId));
        if (existing != null && existing.version >= version) {
            return false;
        }
        File tmp = new File(dir, tripId + SUFFIX + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(ByteBuffer.allocate(8).putLong(version).array());
            out.write(payload);
            out.getFD().sync(); // araç bir sonraki satırdan önce uyuyabilir
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        if (!tmp.renameTo(file(tripId))) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        trim();
        return true;
    }

    /** Gönderilen sürümü siler; bu arada daha yeni sürüm yazıldıysa ona dokunmaz. */
    synchronized void remove(String tripId, long version) {
        if (!SAFE_ID.matcher(tripId == null ? "" : tripId).matches()) {
            return;
        }
        File f = file(tripId);
        Item existing = read(f);
        if (existing != null && existing.version == version) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /** Bekleyenler, eskiden yeniye. {@code nowMs}'e göre süresi geçenler silinir ve listelenmez. */
    synchronized List<Item> pending(long nowMs) {
        List<Item> items = new ArrayList<>();
        File[] files = dir.listFiles((d, name) -> name.endsWith(SUFFIX));
        if (files == null) {
            return items;
        }
        for (File f : files) {
            Item item = read(f);
            if (item == null || nowMs - item.version > MAX_AGE_MS) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                continue;
            }
            items.add(item);
        }
        Collections.sort(items, (a, b) -> Long.compare(a.version, b.version));
        return items;
    }

    synchronized int size() {
        File[] files = dir.listFiles((d, name) -> name.endsWith(SUFFIX));
        return files == null ? 0 : files.length;
    }

    private void trim() {
        List<Item> items = pending(Long.MIN_VALUE / 2); // süre filtresi yok: yalnızca sayı sınırı
        for (int i = 0; i < items.size() - MAX_ITEMS; i++) {
            //noinspection ResultOfMethodCallIgnored
            file(items.get(i).tripId).delete();
        }
    }

    private File file(String tripId) {
        return new File(dir, tripId + SUFFIX);
    }

    private static Item read(File f) {
        if (!f.isFile() || f.length() <= 8) {
            return null;
        }
        String name = f.getName();
        String tripId = name.substring(0, name.length() - SUFFIX.length());
        byte[] all = new byte[(int) f.length()];
        try (InputStream in = new FileInputStream(f)) {
            int off = 0;
            while (off < all.length) {
                int n = in.read(all, off, all.length - off);
                if (n < 0) {
                    return null;
                }
                off += n;
            }
        } catch (IOException e) {
            return null;
        }
        long version = ByteBuffer.wrap(all, 0, 8).getLong();
        byte[] payload = new byte[all.length - 8];
        System.arraycopy(all, 8, payload, 0, payload.length);
        return new Item(tripId, version, payload);
    }
}
