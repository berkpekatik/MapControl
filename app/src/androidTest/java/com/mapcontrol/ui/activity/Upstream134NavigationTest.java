package com.mapcontrol.ui.activity;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.View;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.TargetPackageStore;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises the merged upstream tabs against our shared header, rail and lazy theme rebuild. */
@RunWith(AndroidJUnit4.class)
public class Upstream134NavigationTest {
    private Object field(Object object, String name) {
        try {
            Field f=object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
        } catch(Exception failure) { throw new AssertionError(failure); }
    }
    private void call(MainActivity host,String name,Class<?> arg,Object value) {
        try {
            Method m=MainActivity.class.getDeclaredMethod(name,arg);m.setAccessible(true);m.invoke(host,value);
        } catch(Exception failure) { throw new AssertionError(failure); }
    }
    private void clickMenu(Instrumentation instrumentation,MainActivity host,String menu) throws Exception {
        instrumentation.runOnMainSync(()->((View)field(field(host,"sideRailBuilder"),menu)).performClick());
        Thread.sleep(650);
        instrumentation.waitForIdleSync();
    }
    private void assertTab(MainActivity host,int tab,int title,String menu) {
        assertEquals(tab,field(host,"currentTab"));
        View header=host.getWindow().getDecorView().findViewWithTag("shared_header");
        assertNotNull(header);assertEquals(View.VISIBLE,header.getVisibility());
        assertEquals(host.getString(title),((TextView)header.findViewWithTag("header_title")).getText().toString());
        Object rail=field(host,"sideRailBuilder");
        assertSame(field(rail,menu),field(rail,"selectedMenuRow"));
        if(tab==MainActivity.TAB_CAMERA_360) {
            assertSame(field(host,"tabContentArea"),((View)field(host,"camera360TabContent")).getParent());
        }
    }
    @Test public void newMenusSurviveThemeAndOrientationChangesWithoutLosingExistingChrome() throws Exception {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        Context context=instrumentation.getTargetContext();SharedPreferences prefs=TargetPackageStore.prefs(context);
        Map<String,?> before=prefs.getAll();MainActivity activity=null;
        Configuration original=new Configuration(context.getResources().getConfiguration());
        prefs.edit().putBoolean("disclaimerAccepted",true).putBoolean("appManagementDisclaimerAccepted",true)
                .putBoolean("onboardingCompleted",true).putBoolean("launcherModeEnabled",false)
                .putBoolean("floatingBackButtonEnabled",false).putBoolean("bootAutoStart",false)
                .putBoolean("camera360BetaWarningSeen",true).putBoolean("splitScreenBetaWarningSeen",true).commit();
        try {
            activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));MainActivity host=activity;
            long end=System.currentTimeMillis()+15000;boolean[] ready={false};
            while(!ready[0] && System.currentTimeMillis()<end) {
                instrumentation.runOnMainSync(()->ready[0]=field(host,"camera360TabContent")!=null
                        && host.getWindow().getDecorView().findViewWithTag("shared_header")!=null);
                Thread.sleep(50);
            }
            assertTrue("Main UI initialized",ready[0]);
            clickMenu(instrumentation,host,"menuCamera360");
            instrumentation.runOnMainSync(()->{
                assertTab(host,MainActivity.TAB_CAMERA_360,R.string.camera360_title,"menuCamera360");
                View previous=(View)field(host,"camera360TabContent");
                Configuration dark=new Configuration(original);
                dark.uiMode=(dark.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)|Configuration.UI_MODE_NIGHT_YES;
                host.onConfigurationChanged(dark);
                assertNotSame(previous,field(host,"camera360TabContent"));
                assertTab(host,MainActivity.TAB_CAMERA_360,R.string.camera360_title,"menuCamera360");
                call(host,"setContentWide",boolean.class,true);
                assertEquals(View.GONE,((View)field(host,"sideRail")).getVisibility());
                assertEquals(View.VISIBLE,((View)field(field(host,"topBarBuilder"),"launcherBackButton")).getVisibility());
                call(host,"setContentWide",boolean.class,false);
            });
            clickMenu(instrumentation,host,"menuSplitScreen");
            instrumentation.runOnMainSync(()->{
                assertTab(host,MainActivity.TAB_SPLIT_SCREEN,R.string.side_rail_split_screen,"menuSplitScreen");
                Configuration portrait=new Configuration(original);
                portrait.orientation=Configuration.ORIENTATION_PORTRAIT;
                portrait.uiMode=(portrait.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)|Configuration.UI_MODE_NIGHT_NO;
                host.onConfigurationChanged(portrait);
                assertTab(host,MainActivity.TAB_SPLIT_SCREEN,R.string.side_rail_split_screen,"menuSplitScreen");
            });
            clickMenu(instrumentation,host,"menuCamera360");
            instrumentation.runOnMainSync(()->{
                assertTab(host,MainActivity.TAB_CAMERA_360,R.string.camera360_title,"menuCamera360");
                call(host,"switchTab",int.class,6);
                assertNotNull(host.getWindow().getDecorView().findViewWithTag("assist_card_dmsSetting"));
            });
            for(int tab:new int[]{MainActivity.TAB_CAMERA_360,MainActivity.TAB_SPLIT_SCREEN}) {
                instrumentation.runOnMainSync(()->{
                    call(host,"applyLauncherChrome",boolean.class,true);
                    call(host,"switchTab",int.class,10);
                    assertEquals(View.GONE,host.getWindow().getDecorView().findViewWithTag("shared_header").getVisibility());
                    View shortcut=host.getWindow().getDecorView().findViewWithTag("launcher_shortcut_"+tab);
                    assertNotNull(shortcut);assertTrue(shortcut.performClick());
                });
                Thread.sleep(650);instrumentation.waitForIdleSync();
                instrumentation.runOnMainSync(()->assertTab(host,tab,
                        tab==MainActivity.TAB_CAMERA_360?R.string.camera360_title:R.string.side_rail_split_screen,
                        tab==MainActivity.TAB_CAMERA_360?"menuCamera360":"menuSplitScreen"));
            }
        } finally {
            if(activity!=null){MainActivity host=activity;instrumentation.runOnMainSync(()->{
                host.onConfigurationChanged(original);host.finish();
            });instrumentation.waitForIdleSync();}
            UiStyles.setUiModeOverride(original);
            SharedPreferences.Editor restore=prefs.edit().clear();
            for(Map.Entry<String,?> e:before.entrySet()){
                Object v=e.getValue();String k=e.getKey();
                if(v instanceof Boolean)restore.putBoolean(k,(Boolean)v);
                else if(v instanceof Integer)restore.putInt(k,(Integer)v);
                else if(v instanceof Long)restore.putLong(k,(Long)v);
                else if(v instanceof Float)restore.putFloat(k,(Float)v);
                else if(v instanceof String)restore.putString(k,(String)v);
                else if(v instanceof java.util.Set)restore.putStringSet(k,new java.util.HashSet<>((java.util.Set<String>)v));
            }restore.commit();
        }
    }
}
