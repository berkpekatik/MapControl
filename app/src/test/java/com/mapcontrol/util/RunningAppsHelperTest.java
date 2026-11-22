package com.mapcontrol.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class RunningAppsHelperTest {

    /** T19C (Android 11) {@code dumpsys activity lru} çıktısından kesit. */
    private static final String DUMP = ""
            + "ACTIVITY MANAGER LRU PROCESSES (dumpsys activity lru)\n"
            + "  Activities:\n"
            + "  #57: fg     TOP  LCM 14134:com.mapcontrol/u0a33 act:activities|recents\n"
            + "  #56: vis    BTOP LCM 1914:com.desaysv.ivi.vds.carinfo/1000 act:client\n"
            + "  #53: vis+ 2 TOP  LCM 12552:ru.yandex.yandexmaps/u0a38 act:activities|recents\n"
            + "  #52: prcp   IMPB --- 1757:rkr.simplekeyboard.inputmethod/u0a37 act:treated\n"
            + "  #51: fg +50 FGS  L-- 22775:com.google.android.apps.maps/u0a43 act:activities|recents\n"
            + "  Other:\n"
            + "  #41: cch+ 5 CEM  --- 21941:com.android.permissioncontroller/u0a28\n"
            + "  #40: prev   LAST --- 3923:ru.yandex.yandexmaps:passport/u0a38\n"
            + "  #37: cch+25 CEM  --- 12780:com.android.webview:webview_service/u0a26\n"
            + "  #36: cch+35 SVC  --- 3290:com.desaysv.engmode/1000\n"
            + "  #12: svc    SVC  --- 4400:app.revanced.android.apps.youtube.music/u0a36\n"
            + "  #11: cch+45 CEM  --- 3660:app.revanced.android.apps.youtube.music:bg/u0a36\n";

    @Test
    public void parsesAdjStateProcessAndUid() {
        List<RunningAppsHelper.LruEntry> e = RunningAppsHelper.parseLru(DUMP);
        assertEquals(11, e.size());
        RunningAppsHelper.LruEntry yandex = e.get(2);
        assertEquals("ru.yandex.yandexmaps", yandex.packageName);
        assertEquals("vis", yandex.adj);
        assertEquals("TOP", yandex.procState);
        assertTrue(yandex.appUid);
        RunningAppsHelper.LruEntry maps = e.get(4);
        assertEquals("fg", maps.adj);
        assertEquals("FGS", maps.procState);
        assertEquals("ru.yandex.yandexmaps", e.get(6).packageName); // :passport alt süreci
        assertFalse(e.get(1).appUid); // uid 1000
    }

    @Test
    public void aggregatesByPackageKeepingMostForegroundState() {
        List<RunningAppsHelper.RunningApp> apps =
                RunningAppsHelper.aggregate(RunningAppsHelper.parseLru(DUMP));
        assertEquals("com.mapcontrol", apps.get(0).packageName);
        assertEquals("ru.yandex.yandexmaps", apps.get(1).packageName);
        // Ana süreç ekranda, :passport önceki: paket ekranda sayılır.
        assertEquals(RunningAppsHelper.State.SCREEN, apps.get(1).state);
        assertEquals(RunningAppsHelper.State.SERVICE, apps.get(2).state); // klavye prcp
        assertEquals(RunningAppsHelper.State.SCREEN, apps.get(3).state); // gösterge haritası fg/FGS
        RunningAppsHelper.RunningApp music = apps.get(apps.size() - 1);
        assertEquals("app.revanced.android.apps.youtube.music", music.packageName);
        assertEquals(RunningAppsHelper.State.CACHED, music.state);
        for (RunningAppsHelper.RunningApp a : apps) {
            assertFalse(a.packageName.startsWith("com.desaysv")); // sistem uid'leri elenir
        }
    }

    @Test
    public void killableStatesMatchSystemServiceAdjThreshold() {
        assertEquals(RunningAppsHelper.State.CACHED, RunningAppsHelper.stateOf("cch", "CEM"));
        assertEquals(RunningAppsHelper.State.CACHED, RunningAppsHelper.stateOf("prev", "LAST"));
        assertEquals(RunningAppsHelper.State.CACHED, RunningAppsHelper.stateOf("svc", "SVC"));
        assertEquals(RunningAppsHelper.State.CACHED, RunningAppsHelper.stateOf("home", "HOME"));
        assertEquals(RunningAppsHelper.State.SERVICE, RunningAppsHelper.stateOf("prcp", "FGS"));
        assertEquals(RunningAppsHelper.State.SERVICE, RunningAppsHelper.stateOf("pers", "PER"));
        assertEquals(RunningAppsHelper.State.SCREEN, RunningAppsHelper.stateOf("cch", "TOP"));
    }

    @Test
    public void emptyOrDeniedDumpYieldsNothing() {
        assertTrue(RunningAppsHelper.parseLru(null).isEmpty());
        assertTrue(RunningAppsHelper.parseLru(
                "Permission Denial: can't dump ActivityManager from from pid=1, uid=10033").isEmpty());
    }
}
