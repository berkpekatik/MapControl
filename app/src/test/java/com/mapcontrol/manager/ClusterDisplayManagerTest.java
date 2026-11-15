package com.mapcontrol.manager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClusterDisplayManagerTest {

    private static final String MAPS = "com.google.android.apps.maps";

    @Test
    public void emptyOrOwnClusterIsLeftAlone() {
        assertFalse(ClusterDisplayManager.shouldEvictFromCluster(null, MAPS));
        assertFalse(ClusterDisplayManager.shouldEvictFromCluster("", MAPS));
        assertFalse(ClusterDisplayManager.shouldEvictFromCluster("com.mapcontrol", MAPS));
    }

    /** Gösterge kapat/aç: kapatma Haritalar'ı cluster'da bırakır, açılış onu ana ekrana taşıyıp HOME'a göndermemeli. */
    @Test
    public void googleMapsTargetAlreadyOnClusterStays() {
        assertFalse(ClusterDisplayManager.shouldEvictFromCluster(MAPS, MAPS));
        assertFalse(ClusterDisplayManager.shouldEvictFromCluster(MAPS, " " + MAPS + " "));
    }

    @Test
    public void otherAppsOnClusterAreEvicted() {
        assertTrue(ClusterDisplayManager.shouldEvictFromCluster("ru.yandex.yandexnavi", MAPS));
        assertTrue(ClusterDisplayManager.shouldEvictFromCluster(MAPS, "ru.yandex.yandexnavi"));
        assertTrue(ClusterDisplayManager.shouldEvictFromCluster("ru.yandex.yandexnavi", "ru.yandex.yandexnavi"));
        assertTrue(ClusterDisplayManager.shouldEvictFromCluster(MAPS, null));
    }
}
