package com.mapcontrol.launcher;

/**
 * {@code VDMediaPlay} Parcel biçimi bayrağı (arada fazladan {@code long} alan var mı; bkz. {@link MediaPlayFormat}).
 *
 * <p>Neden ayrı sınıf: {@code com.desaysv.*} kopyaları bazı araç yazılımlarında {@code /system/framework/vdbus.jar}
 * içindeki sistem sınıfıyla gölgelenir; o araçlarda kopyaya eklenen her metot/alan yoktur ve çağrısı
 * {@code NoSuchMethodError} ile süreci öldürür. Kopyalar yalnızca sistem sınıfının imzasını taşımalı; kendi durumumuz
 * MapControl paketinde tutulur. Sistem sınıfı kullanılan araçlarda bu bayrak etkisizdir (biçim zaten yazılımla uyumludur).
 */
public final class MediaPlayWire {
    public static volatile boolean withActions;

    private MediaPlayWire() {
    }
}
