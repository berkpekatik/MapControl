package com.mapcontrol.manager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import com.mapcontrol.R;
import com.mapcontrol.util.SplitScreenGesture;
import com.mapcontrol.util.SplitScreenLauncher;
import com.mapcontrol.util.SplitScreenMessages;
import com.mapcontrol.util.SplitScreenStore;

/**
 * Araç kaydırması ({@code android.intent.action.car_system_gesture_mode}) ile kayıtlı uygulama
 * çiftini bölünmüş ekranda açar. Yalnızca kullanıcının seçtiği tek hareket dinlenir.
 *
 * <p>Harita kaydırması ({@link CarSystemGestureController}) açıkken iki parmak sola/sağa o özelliğe
 * aittir; burada yok sayılır.
 */
public final class SplitScreenGestureController {

    private static final String TAG = "SplitGesture";
    private static final String ACTION = "android.intent.action.car_system_gesture_mode";
    private static final long DEBOUNCE_MS = 1500L;

    private final Context appContext;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            accept(intent);
        }
    };

    private volatile boolean registered;
    private volatile boolean released;
    private long lastAcceptMs;

    public SplitScreenGestureController(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /** Kayıtlı hareket değişince veya servis açılınca çağrılır. */
    public void syncRegistration() {
        main.post(this::syncRegistrationOnMain);
    }

    public void release() {
        released = true;
        main.post(this::unregisterQuietly);
    }

    private void accept(Intent intent) {
        if (released || intent == null || !ACTION.equals(intent.getAction())
                || !intent.hasExtra("gesture")) {
            return;
        }
        int configured = SplitScreenStore.getGestureCode(appContext);
        if (!SplitScreenGesture.isValid(configured)) {
            return;
        }
        int gesture = intent.getIntExtra("gesture", -1);
        if (gesture != configured) {
            return;
        }
        if (SplitScreenGesture.isReservedByMapSwipe(gesture)
                && CarSystemGestureController.isEnabled(appContext)) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastAcceptMs < DEBOUNCE_MS) {
            return;
        }
        lastAcceptMs = now;

        String pkg1 = SplitScreenStore.getPkg1(appContext);
        String pkg2 = SplitScreenStore.getPkg2(appContext);
        if (pkg1 == null || pkg2 == null || pkg1.equals(pkg2)) {
            main.post(() -> Toast.makeText(appContext,
                    R.string.split_gesture_need_pair, Toast.LENGTH_LONG).show());
            return;
        }
        Log.i(TAG, "gesture " + gesture + ": " + pkg1 + " + " + pkg2);
        SplitScreenLauncher.start(appContext, pkg1, pkg2, SplitScreenStore.getRatio(appContext),
                result -> {
                    if (result != SplitScreenLauncher.Result.STARTED) {
                        Toast.makeText(appContext,
                                SplitScreenMessages.resultText(appContext, result),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void syncRegistrationOnMain() {
        if (released) {
            unregisterQuietly();
            return;
        }
        boolean wanted = SplitScreenGesture.isValid(SplitScreenStore.getGestureCode(appContext));
        if (!wanted) {
            unregisterQuietly();
            return;
        }
        if (registered) {
            return;
        }
        IntentFilter filter = new IntentFilter(ACTION);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                appContext.registerReceiver(receiver, filter);
            }
            registered = true;
            Log.i(TAG, "split gesture receiver registered");
        } catch (Exception e) {
            Log.w(TAG, "split gesture receiver register failed", e);
        }
    }

    private void unregisterQuietly() {
        if (!registered) {
            return;
        }
        try {
            appContext.unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
        registered = false;
        Log.i(TAG, "split gesture receiver unregistered");
    }
}
