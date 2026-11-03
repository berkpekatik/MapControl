package com.mapcontrol.ui.welcome;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Hoşgeldin HTML şablonunun geldiği yer. Şimdilik uygulama içindeki assets;
 * ileride API'den gelen HTML için yeni bir kaynak yazılır, tasarım sınıfı değişmez.
 */
public interface WelcomeHtmlSource {

    /** Yer tutucular ({@code {{TIME}}} vb.) henüz doldurulmamış ham HTML. */
    String load(Context context) throws IOException;

    /** {@code assets/} altındaki bir dosyadan okur. */
    static WelcomeHtmlSource fromAsset(String assetPath) {
        return context -> {
            try (InputStream in = context.getAssets().open(assetPath);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        };
    }
}
