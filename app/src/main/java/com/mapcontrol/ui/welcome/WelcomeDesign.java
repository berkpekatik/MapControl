package com.mapcontrol.ui.welcome;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * Açılışta gösterilebilecek bir giriş tasarımı. Yeni tasarım bu arayüzü uygular
 * ve {@link WelcomeDesignCatalog} listesine eklenir.
 */
public interface WelcomeDesign {

    String id();

    @StringRes
    int titleRes();

    @StringRes
    int helpRes();

    /**
     * Tam ekran içerik. Görsel basmayan seçenekler {@code null} döner.
     */
    @Nullable
    View createView(Context context);
}
