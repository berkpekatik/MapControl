package com.mapcontrol.manager;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sunucu başlatma kuşağı: her başlatma yeni bir numara alır, kabul iş parçacığı yalnızca kendi kuşağı hâlâ
 * güncelken durum yazar. Android'e bağlı değildir.
 */
final class ServerGeneration {
    private final AtomicInteger current = new AtomicInteger();

    /** Yeni bir kuşak başlatır ve numarasını döner; öncekiler eskimiş olur. */
    int next() {
        return current.incrementAndGet();
    }

    /** Verilen kuşak hâlâ güncel mi. */
    boolean isCurrent(int gen) {
        return current.get() == gen;
    }
}
