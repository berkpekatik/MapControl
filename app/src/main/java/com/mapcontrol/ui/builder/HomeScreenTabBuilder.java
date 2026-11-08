package com.mapcontrol.ui.builder;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.mapcontrol.BuildConfig;
import com.mapcontrol.R;
import com.mapcontrol.launcher.HomeScreenPrefs;
import com.mapcontrol.ui.theme.FeatureGridLayout;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.LauncherModeManager;

/**
 * "Ana ekran" sekmesi: widget'lı ana ekranın (launcher) tüm ayarları tek yerde — Launcher Modu,
 * görünüm, düzen ve sürüm. Değerler launcher ile aynı tercihlere {@link HomeScreenPrefs} üzerinden yazılır;
 * launcher bir sonraki öne gelişinde kendini yeniler. Renkler yalnızca {@link UiStyles#color} ile çözülür;
 * tema değişince {@link #rebuild()} çağrılır.
 */
public class HomeScreenTabBuilder {

    public interface Callback {
        void log(String message);
        /** Launcher Modu anahtarı değişti (Ayarlar sekmesindeki ile aynı akış). */
        void onLauncherModeChanged(boolean enabled);
    }

    private final Context context;
    private final Callback callback;
    private ScrollView scrollView;
    private LinearLayout content;

    public HomeScreenTabBuilder(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
        build();
    }

    public ScrollView build() {
        if (scrollView == null) {
            scrollView = new ScrollView(context);
            scrollView.setBackgroundColor(Color.TRANSPARENT);
            scrollView.setFillViewport(true);

            LinearLayout outer = new LinearLayout(context);
            outer.setOrientation(LinearLayout.VERTICAL);
            int margin = UiStyles.dimenPx(context, R.dimen.oem_card_margin);
            outer.setPadding(margin, margin, margin, margin);

            content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            int inner = UiStyles.dimenPx(context, R.dimen.oem_card_inner_padding);
            content.setPadding(inner, inner, inner, inner);
            outer.addView(content, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            scrollView.addView(outer, new ScrollView.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        content.removeAllViews();

        boolean launcherModeOn = LauncherModeManager.isEnabled(context);
        FeatureGridLayout modeGrid = new FeatureGridLayout(context);
        content.addView(modeGrid);
        createLauncherModeSection(modeGrid);

        if (!launcherModeOn) {
            TextView hint = new TextView(context);
            hint.setText(R.string.home_screen_off_hint);
            UiStyles.setTextSize(hint, R.dimen.text_size_normal);
            hint.setTextColor(UiStyles.color(context, R.color.textHint));
            int pad = UiStyles.dimenPx(context, R.dimen.spacing_medium);
            hint.setPadding(pad, pad, pad, pad);
            content.addView(hint, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            return scrollView;
        }

        addSectionTitle(R.string.home_screen_appearance_title);
        FeatureGridLayout appearanceGrid = new FeatureGridLayout(context);
        content.addView(appearanceGrid);
        createThemeSection(appearanceGrid);
        createDriveAccentSection(appearanceGrid);
        createSportModeSection(appearanceGrid);
        createBottomBarSection(appearanceGrid);
        createAutoLayoutSection(appearanceGrid);
        createVehicleScreenSection(appearanceGrid);
        createCarColorSection(appearanceGrid);

        addSectionTitle(R.string.home_screen_layout_title);
        FeatureGridLayout layoutGrid = new FeatureGridLayout(context);
        content.addView(layoutGrid);
        createLayoutSection(layoutGrid);

        addSectionTitle(R.string.home_screen_about_title);
        FeatureGridLayout aboutGrid = new FeatureGridLayout(context);
        content.addView(aboutGrid);
        createAboutSection(aboutGrid);
        return scrollView;
    }

    /** Tema ya da Launcher Modu değişince içeriği baştan kurar (kaydırma konumu korunur). */
    public void rebuild() {
        int scrollY = scrollView != null ? scrollView.getScrollY() : 0;
        build();
        if (scrollY > 0) {
            scrollView.post(() -> scrollView.scrollTo(0, scrollY));
        }
    }

    public ScrollView getScrollView() {
        return scrollView != null ? scrollView : build();
    }

    private void addSectionTitle(int titleRes) {
        TextView title = new TextView(context);
        title.setText(titleRes);
        UiStyles.setTextSize(title, R.dimen.text_size_large);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        int pad = UiStyles.dimenPx(context, R.dimen.spacing_medium);
        title.setPadding(pad / 2, pad, pad / 2, pad / 2);
        content.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout createCard(FeatureGridLayout grid) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int padding = UiStyles.dimenPx(context, R.dimen.oem_card_inner_padding);
        card.setPadding(padding, padding, padding, padding);
        UiStyles.setGlassCardBackground(card);
        grid.addCard(card);
        return card;
    }

    private TextView createText(LinearLayout parent, CharSequence text, int sizeRes, int colorRes) {
        TextView view = new TextView(context);
        view.setText(text);
        UiStyles.setTextSize(view, sizeRes);
        view.setTextColor(UiStyles.color(context, colorRes));
        view.setPadding(0, 0, 0, UiStyles.dimenPx(context, R.dimen.spacing_small));
        parent.addView(view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return view;
    }

    private void addButton(LinearLayout parent, int labelRes, int colorRes, View.OnClickListener onClick) {
        Button button = new Button(context);
        button.setText(labelRes);
        UiStyles.styleOemButton(button, UiStyles.color(context, colorRes));
        button.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UiStyles.dimenPx(context, R.dimen.spacing_small);
        parent.addView(button, lp);
    }

    /** Launcher Modu: Ayarlar sekmesindeki anahtarla aynı akış ve aynı yardım metinleri. */
    private void createLauncherModeSection(FeatureGridLayout grid) {
        LinearLayout card = createCard(grid);
        UiStyles.addBinarySegmentedControl(context, card,
                context.getString(R.string.settings_launcher_mode_title),
                context.getString(R.string.common_yes), context.getString(R.string.common_no),
                context.getString(R.string.settings_launcher_mode_desc),
                context.getString(R.string.settings_launcher_mode_help_off),
                LauncherModeManager.isEnabled(context),
                enabled -> {
                    callback.log("Araç Launcher Modu: " + (enabled ? "Evet" : "Hayır"));
                    callback.onLauncherModeChanged(enabled);
                    rebuild();
                });

        TextView homeLink = new TextView(context);
        homeLink.setText(R.string.settings_launcher_home_link);
        UiStyles.setTextSize(homeLink, R.dimen.text_size_small);
        homeLink.setTextColor(UiStyles.color(context, R.color.accentHighlight));
        homeLink.setPadding(16, 4, 16, 16);
        homeLink.setOnClickListener(v -> {
            LauncherModeManager.logHomeResolutionState(context, callback::log);
            LauncherModeManager.openHomeChooser(context);
        });
        card.addView(homeLink, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /** Tema: Otomatik / Gündüz / Gece (sıra {@code ThemeMode} sırası). */
    private void createThemeSection(FeatureGridLayout grid) {
        UiStyles.addTernarySegmentedControl(context, createCard(grid),
                context.getString(R.string.launcher_theme),
                new String[]{
                        context.getString(R.string.launcher_theme_auto),
                        context.getString(R.string.launcher_theme_day),
                        context.getString(R.string.launcher_theme_night)},
                new String[]{
                        context.getString(R.string.home_screen_theme_help_auto),
                        context.getString(R.string.home_screen_theme_help_day),
                        context.getString(R.string.home_screen_theme_help_night)},
                new int[]{0, 1, 2},
                HomeScreenPrefs.themeIndex(context),
                index -> {
                    HomeScreenPrefs.setThemeIndex(context, index);
                    callback.log("Ana ekran teması: " + index);
                });
    }

    private void createDriveAccentSection(FeatureGridLayout grid) {
        UiStyles.addBinarySegmentedControl(context, createCard(grid),
                context.getString(R.string.launcher_drive_theme),
                context.getString(R.string.common_on), context.getString(R.string.common_off),
                context.getString(R.string.home_screen_drive_theme_help_on),
                context.getString(R.string.home_screen_drive_theme_help_off),
                HomeScreenPrefs.driveAccent(context),
                on -> {
                    HomeScreenPrefs.setDriveAccent(context, on);
                    callback.log("Ana ekran sürüş rengi: " + on);
                });
    }

    /** Spor modu (deneysel, yalnız Sahne): araç Spor'dayken kronograf ekranı; varsayılan kapalı. */
    private void createSportModeSection(FeatureGridLayout grid) {
        UiStyles.addBinarySegmentedControl(context, createCard(grid),
                context.getString(R.string.launcher_sport_mode),
                context.getString(R.string.common_on), context.getString(R.string.common_off),
                context.getString(R.string.home_screen_sport_mode_help_on),
                context.getString(R.string.home_screen_sport_mode_help_off),
                HomeScreenPrefs.sportMode(context),
                on -> {
                    HomeScreenPrefs.setSportMode(context, on);
                    callback.log("Spor modu (deneysel): " + on);
                });
    }

    /** Alt çubuk: Orijinal / Bizimki / Gizli (sıra {@code BottomBarMode} sırası). */
    private void createBottomBarSection(FeatureGridLayout grid) {
        UiStyles.addTernarySegmentedControl(context, createCard(grid),
                context.getString(R.string.launcher_bottom_bar),
                new String[]{
                        context.getString(R.string.launcher_bottom_bar_original),
                        context.getString(R.string.launcher_bottom_bar_ours),
                        context.getString(R.string.launcher_bottom_bar_hidden)},
                new String[]{
                        context.getString(R.string.home_screen_bottom_bar_help_original),
                        context.getString(R.string.home_screen_bottom_bar_help_ours),
                        context.getString(R.string.home_screen_bottom_bar_help_hidden)},
                new int[]{0, 1, 2},
                HomeScreenPrefs.bottomBarIndex(context),
                index -> {
                    HomeScreenPrefs.setBottomBarIndex(context, index);
                    callback.log("Ana ekran alt çubuk: " + index);
                });
    }

    /** Araç ekranı: Otomatik / Tiggo 7 / Tiggo 8 (sıra {@code HomeScreenPrefs.vehicleProfileIndex}); launcher bir sonraki açılışta yeniden kurulur. */
    private void createVehicleScreenSection(FeatureGridLayout grid) {
        UiStyles.addTernarySegmentedControl(context, createCard(grid),
                context.getString(R.string.home_screen_vehicle_screen),
                new String[]{
                        context.getString(R.string.home_screen_vehicle_auto),
                        context.getString(R.string.home_screen_vehicle_t7),
                        context.getString(R.string.home_screen_vehicle_t8)},
                new String[]{
                        context.getString(R.string.home_screen_vehicle_help_auto),
                        context.getString(R.string.home_screen_vehicle_help_t7),
                        context.getString(R.string.home_screen_vehicle_help_t8)},
                new int[]{0, 1, 2},
                HomeScreenPrefs.vehicleProfileIndex(context),
                index -> {
                    HomeScreenPrefs.setVehicleProfileIndex(context, index);
                    callback.log("Ana ekran araç ekranı: " + index);
                });
    }

    /**
     * Araç rengi: Sahne'nin ortasındaki aracın boyası (Grafit / Siyah / Beyaz / Gümüş / Kırmızı / Mavi), renk örnekleri
     * satırı. Launcher açıksa araç hemen yeni renge geçer (yeniden kurulmaz).
     */
    private void createCarColorSection(FeatureGridLayout grid) {
        LinearLayout card = createCard(grid);
        int hPad = UiStyles.dimenPx(context, R.dimen.spacing_medium);
        TextView title = createText(card, context.getString(R.string.launcher_car_color),
                R.dimen.text_size_medium, R.color.textPrimary);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(hPad, UiStyles.dimenPx(context, R.dimen.spacing_medium), hPad,
                UiStyles.dimenPx(context, R.dimen.spacing_small));
        float labelSp = context.getResources().getDimension(R.dimen.text_size_small)
                / context.getResources().getDisplayMetrics().scaledDensity;
        int outline = (UiStyles.color(context, R.color.textPrimary) & 0x00FFFFFF) | 0x40000000;
        card.addView(com.mapcontrol.launcher.CarColorSwatches.build(context,
                UiStyles.color(context, R.color.accentHighlight),
                UiStyles.color(context, R.color.textPrimary),
                outline, labelSp, 52,
                () -> {
                    callback.log("Ana ekran araç rengi: " + HomeScreenPrefs.carColorIndex(context));
                    rebuild();
                }), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView help = createText(card, context.getString(R.string.launcher_car_color_help),
                R.dimen.text_size_small, R.color.textHint);
        help.setPadding(hPad, UiStyles.dimenPx(context, R.dimen.spacing_small), hPad,
                UiStyles.dimenPx(context, R.dimen.spacing_medium));
    }

    private void createAutoLayoutSection(FeatureGridLayout grid) {
        UiStyles.addBinarySegmentedControl(context, createCard(grid),
                context.getString(R.string.launcher_auto_drive_layout),
                context.getString(R.string.common_on), context.getString(R.string.common_off),
                context.getString(R.string.home_screen_auto_layout_help_on),
                context.getString(R.string.home_screen_auto_layout_help_off),
                HomeScreenPrefs.autoDriveLayout(context),
                on -> {
                    HomeScreenPrefs.setAutoDriveLayout(context, on);
                    callback.log("Ana ekran otomatik düzen: " + on);
                    rebuild(); // sıfırlama düğmeleri tek/çift düzene göre yeniden kurulur
                });
    }

    /** Düzen: launcher'ı doğrudan düzenleme modunda aç, Park / Sürüş düzenini varsayılana döndür. */
    private void createLayoutSection(FeatureGridLayout grid) {
        LinearLayout editCard = createCard(grid);
        TextView editTitle = createText(editCard, context.getString(R.string.launcher_edit_title),
                R.dimen.text_size_medium, R.color.textPrimary);
        editTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        createText(editCard, context.getString(R.string.home_screen_edit_desc),
                R.dimen.text_size_small, R.color.textHint);
        addButton(editCard, R.string.launcher_edit_title, R.color.accentHighlight,
                v -> HomeScreenPrefs.openEditor(context));

        LinearLayout resetCard = createCard(grid);
        // Tek düzende (ayrı sürüş düzeni kapalı) yalnızca ana ekranı (Park) sıfırlayan tek düğme gösterilir.
        boolean twoLayouts = HomeScreenPrefs.autoDriveLayout(context);
        addButton(resetCard, twoLayouts ? R.string.home_screen_reset_park : R.string.home_screen_reset_home,
                R.color.buttonSecondaryMuted, v -> confirmReset(false));
        if (twoLayouts) {
            addButton(resetCard, R.string.home_screen_reset_drive, R.color.buttonSecondaryMuted,
                    v -> confirmReset(true));
        }
    }

    private void confirmReset(boolean drive) {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setMessage(drive ? R.string.home_screen_reset_drive_confirm
                        : HomeScreenPrefs.autoDriveLayout(context) ? R.string.home_screen_reset_park_confirm
                        : R.string.home_screen_reset_home_confirm)
                .setPositiveButton(R.string.home_screen_reset_button, (d, w) -> {
                    HomeScreenPrefs.resetLayout(context, drive);
                    callback.log("Ana ekran " + (drive ? "Sürüş" : "Park") + " düzeni sıfırlandı");
                    Toast.makeText(context, R.string.home_screen_reset_done, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.wifi_cancel, (d, w) -> d.dismiss())
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(UiStyles.color(context, R.color.surfaceColor)));
        }
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(UiStyles.color(context, R.color.accentHighlight));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(UiStyles.color(context, R.color.textDialogButtonSecondary));
    }

    private void createAboutSection(FeatureGridLayout grid) {
        LinearLayout card = createCard(grid);
        createText(card, context.getString(R.string.home_screen_version_line,
                BuildConfig.VERSION_NAME, BuildConfig.GIT_SHA),
                R.dimen.text_size_normal, R.color.textPrimary);
    }
}
