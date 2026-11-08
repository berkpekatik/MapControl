package com.mapcontrol.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class SplitTaskFinderTest {

    private static final String YX = "ru.yandex.yandexmaps";

    @Test
    public void picksLatestTaskOfPackage() {
        String log = "11-07 11:10:09.091   503   784 I wm_resume_activity: [0,126535323,1071,ru.yandex.yandexmaps/.SplashScreen]\n"
                + "11-07 11:10:10.000   503   784 I wm_resume_activity: [0,5555,1080,com.mapcontrol/com.mapcontrol.launcher.MiniDashActivity]\n"
                + "11-07 11:10:28.036   503   635 I wm_new_intent: [0,99,1000215,ru.yandex.yandexmaps/.app.MapActivity,android.intent.action.MAIN,NULL,NULL,270663680]\n"
                + "11-07 11:10:29.036   503   635 I wm_resume_activity: [0,1,1081,com.other/.Main]\n";
        assertEquals(1000215, SplitTaskFinder.lastTaskId(log, YX));
        assertEquals(1080, SplitTaskFinder.lastTaskId(log, "com.mapcontrol"));
    }

    @Test
    public void ignoresOtherTagsAndPrefixPackages() {
        String log = "I wm_set_resumed_activity: [0,ru.yandex.yandexmaps/.SplashScreen,minimalResumeActivityLocked]\n"
                + "I wm_task_to_front: [0,1071]\n"
                + "I wm_resume_activity: [0,1,77,ru.yandex.yandexmapsx/.Main]\n";
        assertEquals(-1, SplitTaskFinder.lastTaskId(log, YX));
    }

    @Test
    public void malformedAndEmpty() {
        assertEquals(-1, SplitTaskFinder.lastTaskId(null, YX));
        assertEquals(-1, SplitTaskFinder.lastTaskId("", YX));
        assertEquals(-1, SplitTaskFinder.lastTaskId("I wm_resume_activity: [0,1]\n", YX));
        assertEquals(-1, SplitTaskFinder.lastTaskId("I wm_resume_activity: [0,1,abc,ru.yandex.yandexmaps/.A]\n", YX));
        assertEquals(-1, SplitTaskFinder.lastTaskId("I wm_resume_activity: [0,1,-1,ru.yandex.yandexmaps/.A]\n", YX));
    }

    @Test
    public void briefFormatParses() {
        assertEquals(42, SplitTaskFinder.lastTaskId("I/wm_create_activity( 503): [0,9,42,ru.yandex.yandexmaps/.SplashScreen,a,b,c,0]", YX));
    }

    @Test
    public void logcatArgsReadEventsBufferOnlyWithTags() {
        String[] a = SplitTaskFinder.logcatArgs();
        assertEquals("logcat", a[0]);
        assertTrue(Arrays.asList(a).contains("events"));
        assertTrue(Arrays.asList(a).contains("wm_new_intent"));
        assertTrue(Arrays.asList(a).contains("-d"));
    }
}
