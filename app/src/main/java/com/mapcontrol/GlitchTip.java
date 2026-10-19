package com.mapcontrol;

import android.content.Context;
import android.util.Log;

/** Local builds do not initialize remote crash reporting. Use Android logs for diagnostics. */
public final class GlitchTip {
    private GlitchTip() {}

    public static void start(Context context) {
        Log.i("MapControl", "Remote crash reporting disabled in local maintenance build");
    }
}
