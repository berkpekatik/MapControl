package com.mapcontrol.util;

import android.content.Context;

import androidx.annotation.NonNull;

import com.mapcontrol.R;

/** Bölünmüş ekran sonuçlarının kullanıcıya gösterilen metni. */
public final class SplitScreenMessages {

    private SplitScreenMessages() {
    }

    @NonNull
    public static String resultText(@NonNull Context context, @NonNull SplitScreenLauncher.Result result) {
        switch (result) {
            case STARTED:
                return context.getString(R.string.split_started);
            case NO_FREEFORM:
                return context.getString(R.string.split_fail_unsupported);
            case SAME_APP:
                return context.getString(R.string.split_same_app);
            case NOT_INSTALLED:
                return context.getString(R.string.split_fail_not_installed);
            case BAD_BOUNDS:
                return context.getString(R.string.split_fail_bounds);
            case LAUNCH_FAILED:
            default:
                return context.getString(R.string.split_fail_launch);
        }
    }
}
