package com.mapcontrol.ui.builder;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import com.mapcontrol.ui.activity.MainActivity;
import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

public class AppsTabBuilder {
    public interface AppsCallback {
        boolean isSystemOrPrivApp(ApplicationInfo appInfo);

        boolean isSystemOrPrivApp(String packageName);

        void log(String message);
    }

    private final Context context;
    private final Activity activity;
    private final AppsCallback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout tabContent;
    private LinearLayout appsListContainer;
    private boolean isLocalMode = false; // false = Sunucu, true = Yerel
    /** Açık "İndirilen Dosyalar" diyaloğu; yenilemeden önce kapatılır (üst üste binmeyi önler). */
    private AlertDialog downloadedFilesDialog;

    public AppsTabBuilder(Context context, AppsCallback callback) {
        this.context = context;
        this.activity = (context instanceof Activity) ? (Activity) context : null;
        this.callback = callback;
        build();
    }

    public LinearLayout build() {
        tabContent = new LinearLayout(context);
        tabContent.setOrientation(LinearLayout.VERTICAL);
        int margin = UiStyles.dimenPx(context, R.dimen.oem_card_margin);
        tabContent.setPadding(margin, margin, margin, margin);
        tabContent.setBackgroundColor(ContextCompat.getColor(context, R.color.transparent));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int inner = UiStyles.dimenPx(context, R.dimen.oem_card_inner_padding);
        card.setPadding(inner, inner, inner, inner);
        UiStyles.setGlassCardBackground(card);

        ScrollView appsListScrollView = new ScrollView(context);
        appsListScrollView.setBackgroundColor(ContextCompat.getColor(context, R.color.transparent));
        appsListScrollView.setPadding(0, 0, 0, 0);

        appsListContainer = new LinearLayout(context);
        appsListContainer.setOrientation(LinearLayout.VERTICAL);
        appsListScrollView.addView(appsListContainer);

        LinearLayout.LayoutParams appsListParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f);
        card.addView(appsListScrollView, appsListParams);

        tabContent.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f));

        return tabContent;
    }

    public LinearLayout getTabContent() {
        return tabContent;
    }

    public LinearLayout buildTopBarButtons(Context ctx) {
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(android.view.Gravity.CENTER_VERTICAL);

        Button btnRefreshApps = new Button(ctx);
        btnRefreshApps.setTextSize(20);
        btnRefreshApps.setTextColor(ContextCompat.getColor(ctx, R.color.textPrimary));
        btnRefreshApps.setBackgroundColor(ContextCompat.getColor(ctx, R.color.transparent));
        btnRefreshApps.setPadding(12, 12, 12, 12);
        UiStyles.setButtonIconOnlyTinted(btnRefreshApps, R.drawable.ic_mdi_refresh,
                ContextCompat.getColor(ctx, R.color.textPrimary), "Listeyi yenile");
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        refreshParams.setMargins(0, 0, 4, 0);
        container.addView(btnRefreshApps, refreshParams);
        btnRefreshApps.setOnClickListener(v -> {
            if (isLocalMode) {
                loadLocalApps();
            } else {
                loadAppsFromServer();
            }
        });

        Button btnDownloadedFiles = new Button(ctx);
        btnDownloadedFiles.setTextSize(20);
        btnDownloadedFiles.setTextColor(ContextCompat.getColor(ctx, R.color.textPrimary));
        btnDownloadedFiles.setBackgroundColor(ContextCompat.getColor(ctx, R.color.transparent));
        btnDownloadedFiles.setPadding(12, 12, 12, 12);
        UiStyles.setButtonIconOnlyTinted(btnDownloadedFiles, R.drawable.ic_mdi_folder,
                ContextCompat.getColor(ctx, R.color.textPrimary), "İndirilen dosyalar");
        LinearLayout.LayoutParams filesParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        filesParams.setMargins(0, 0, 4, 0);
        container.addView(btnDownloadedFiles, filesParams);
        btnDownloadedFiles.setOnClickListener(v -> showDownloadedFiles());

        Button btnModeToggle = new Button(ctx);
        updateModeToggleButton(btnModeToggle);
        btnModeToggle.setTextSize(16);
        btnModeToggle.setTextColor(ContextCompat.getColor(ctx, R.color.textPrimary));
        btnModeToggle.setBackgroundColor(ContextCompat.getColor(ctx, R.color.transparent));
        btnModeToggle.setPadding(12, 12, 12, 12);
        btnModeToggle.setOnClickListener(v -> {
            isLocalMode = !isLocalMode;
            updateModeToggleButton(btnModeToggle);
            if (isLocalMode) {
                loadLocalApps();
            } else {
                loadAppsFromServer();
            }
        });
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        toggleParams.setMargins(0, 0, 4, 0);
        container.addView(btnModeToggle, toggleParams);

        Button overflowMenu = new Button(ctx);
        overflowMenu.setTextSize(20);
        overflowMenu.setTextColor(ContextCompat.getColor(ctx, R.color.textPrimary));
        overflowMenu.setBackgroundColor(ContextCompat.getColor(ctx, R.color.transparent));
        overflowMenu.setPadding(12, 12, 12, 12);
        UiStyles.setButtonIconOnlyTinted(overflowMenu, R.drawable.ic_mdi_dots_vertical,
                ContextCompat.getColor(ctx, R.color.textPrimary), "Diğer seçenekler");
        overflowMenu.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(ctx, overflowMenu);
            popupMenu.getMenu().add(0, 1, 0, "Tümünü Sil");
            popupMenu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    new AlertDialog.Builder(ctx)
                            .setTitle("Tümünü Sil")
                            .setMessage("Tüm indirilen dosyaları silmek istediğinize emin misiniz?")
                            .setPositiveButton("Sil", (d, which) -> performReset())
                            .setNegativeButton("İptal", null)
                            .create()
                            .show();
                    return true;
                }
                return false;
            });
            popupMenu.show();
        });
        container.addView(overflowMenu);

        return container;
    }

    private void updateModeToggleButton(Button btnModeToggle) {
        if (btnModeToggle == null) return;
        int icon = isLocalMode ? R.drawable.ic_mdi_cellphone : R.drawable.ic_mdi_web;
        btnModeToggle.setText(isLocalMode ? "Yerel" : "Sunucu");
        UiStyles.setButtonStartIconTinted(btnModeToggle, icon,
                ContextCompat.getColor(context, R.color.textPrimary),
                UiStyles.dimenPx(context, R.dimen.spacing_small));
    }

    // ============================================================
    //  App operations (physically moved from MainActivity)
    // ============================================================

    public void loadLocalApps() {
        if (appsListContainer == null) return;

        new Thread(() -> {
            try {
                handler.post(() -> {
                    appsListContainer.removeAllViews();
                    TextView loadingText = new TextView(context);
                    loadingText.setText("Yükleniyor...");
                    loadingText.setTextColor(ContextCompat.getColor(context, R.color.textLoading));
                    loadingText.setTextSize(14);
                    loadingText.setPadding(8, 8, 8, 8);
                    appsListContainer.addView(loadingText);
                });

                PackageManager pm = context.getPackageManager();
                List<PackageInfo> allPackages = pm.getInstalledPackages(0);
                List<PackageInfo> user0Apps = new ArrayList<>();

                for (PackageInfo pkgInfo : allPackages) {
                    try {
                        String packageName = pkgInfo.packageName;
                        if (packageName.equals("com.mapcontrol")) continue;

                        ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                        if (callback.isSystemOrPrivApp(appInfo)) continue;

                        int userId = appInfo.uid / 100000;
                        if (userId == 0) {
                            Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
                            if (launchIntent != null) {
                                user0Apps.add(pkgInfo);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }

                handler.post(() -> {
                    appsListContainer.removeAllViews();
                    displayLocalAppsList(user0Apps);
                });

                callback.log("" + user0Apps.size() + " yerel uygulama yüklendi");
            } catch (Exception e) {
                handler.post(() -> {
                    appsListContainer.removeAllViews();
                    TextView errorText = new TextView(context);
                    errorText.setText("Hata: " + e.getMessage());
                    errorText.setTextColor(ContextCompat.getColor(context, R.color.statusErrorBright));
                    errorText.setTextSize(14);
                    errorText.setPadding(8, 8, 8, 8);
                    appsListContainer.addView(errorText);
                });
                callback.log("Yerel uygulama listesi yükleme hatası: " + e.getMessage());
            }
        }).start();
    }

    private void displayLocalAppsList(List<PackageInfo> packages) {
        if (appsListContainer == null) return;

        try {
            PackageManager pm = context.getPackageManager();

            for (PackageInfo pkgInfo : packages) {
                String packageName = pkgInfo.packageName;
                String currentVersion = pkgInfo.versionName != null ? pkgInfo.versionName : "0";

                String displayName = packageName;
                try {
                    ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                    CharSequence label = pm.getApplicationLabel(appInfo);
                    if (label != null) displayName = label.toString();
                } catch (Exception ignored) {
                }

                final String finalPackageName = packageName;
                final String finalDisplayName = displayName;
                final String finalCurrentVersion = currentVersion;

                LinearLayout appCard = new LinearLayout(context);
                appCard.setOrientation(LinearLayout.HORIZONTAL);
                appCard.setPadding(20, 20, 20, 20);
                appCard.setGravity(android.view.Gravity.CENTER_VERTICAL);
                appCard.setClickable(true);
                appCard.setFocusable(true);

                UiStyles.applySolidRoundedBackgroundDp(appCard,
                        ContextCompat.getColor(context, R.color.surfaceCardInner), 16f);

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    appCard.setElevation(2f);
                }

                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                cardParams.setMargins(0, 0, 0, UiStyles.dimenPx(context, R.dimen.oem_card_margin));
                appCard.setMinimumHeight((int) (120 * context.getResources().getDisplayMetrics().density));

                android.widget.ImageView appIcon = new android.widget.ImageView(context);
                try {
                    android.graphics.drawable.Drawable icon = pm.getApplicationIcon(finalPackageName);
                    appIcon.setImageDrawable(icon);
                } catch (Exception e) {
                    appIcon.setImageResource(android.R.drawable.sym_def_app_icon);
                }
                appIcon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                        (int) (44 * context.getResources().getDisplayMetrics().density),
                        (int) (44 * context.getResources().getDisplayMetrics().density));
                iconParams.setMargins(0, 0, 16, 0);
                appCard.addView(appIcon, iconParams);

                LinearLayout infoContainer = new LinearLayout(context);
                infoContainer.setOrientation(LinearLayout.VERTICAL);
                infoContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);

                TextView nameText = new TextView(context);
                nameText.setText(finalDisplayName);
                nameText.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
                nameText.setTextSize(17);
                nameText.setTypeface(null, android.graphics.Typeface.NORMAL);
                infoContainer.addView(nameText);

                TextView statusText = new TextView(context);
                statusText.setText("Kurulu • v" + finalCurrentVersion);
                statusText.setTextColor(ContextCompat.getColor(context, R.color.textSecondaryCool));
                statusText.setTextSize(13);
                LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                statusParams.setMargins(0, 4, 0, 0);
                infoContainer.addView(statusText, statusParams);

                LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                appCard.addView(infoContainer, infoParams);

                LinearLayout rightContainer = new LinearLayout(context);
                rightContainer.setOrientation(LinearLayout.HORIZONTAL);
                rightContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);

                Button actionButton = new Button(context);
                actionButton.setText("AÇ");
                actionButton.setTextSize(14);
                actionButton.setTypeface(null, android.graphics.Typeface.BOLD);
                actionButton.setPadding(24, 12, 24, 12);

                UiStyles.styleOemButton(actionButton, ContextCompat.getColor(context, R.color.buttonPrimary));
                actionButton.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
                actionButton.setOnClickListener(v -> {
                    try {
                        Intent launchIntent = pm.getLaunchIntentForPackage(finalPackageName);
                        if (launchIntent != null && activity != null) {
                            activity.startActivity(launchIntent);
                            callback.log(finalDisplayName + " açıldı");
                        } else {
                            Toast.makeText(context, "Uygulama açılamadı", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        callback.log("Uygulama açma hatası: " + e.getMessage());
                        Toast.makeText(context, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
                rightContainer.addView(actionButton);

                Button removeButton = new Button(context);
                removeButton.setText("KALDIR");
                removeButton.setTextSize(14);
                removeButton.setTypeface(null, android.graphics.Typeface.BOLD);
                removeButton.setTextColor(ContextCompat.getColor(context, R.color.textDestructive));
                removeButton.setBackgroundColor(ContextCompat.getColor(context, R.color.transparent));
                removeButton.setPadding(18, 12, 18, 12);
                removeButton.setOnClickListener(v -> uninstallApp(finalPackageName, finalDisplayName));
                LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                removeParams.setMargins(UiStyles.dimenPx(context, R.dimen.spacing_medium), 0, 0, 0);
                rightContainer.addView(removeButton, removeParams);

                appCard.addView(rightContainer);
                appsListContainer.addView(appCard, cardParams);
            }

            if (packages.isEmpty()) {
                float d = context.getResources().getDisplayMetrics().density;
                LinearLayout emptyCard = new LinearLayout(context);
                emptyCard.setOrientation(LinearLayout.VERTICAL);
                emptyCard.setGravity(android.view.Gravity.CENTER);
                emptyCard.setPadding(32, 48, 32, 48);

                AppCompatImageView emptyIcon = new AppCompatImageView(context);
                emptyIcon.setImageResource(R.drawable.ic_mdi_package_variant);
                emptyIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int iconPx = Math.round(56 * d);
                emptyIcon.setLayoutParams(new LinearLayout.LayoutParams(iconPx, iconPx));
                emptyIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.textMuted)));
                emptyCard.addView(emptyIcon);

                TextView emptyText = new TextView(context);
                emptyText.setText("Yerel uygulama bulunamadı");
                emptyText.setTextColor(ContextCompat.getColor(context, R.color.textMuted));
                emptyText.setTextSize(15);
                emptyText.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                etLp.topMargin = Math.round(16 * d);
                emptyCard.addView(emptyText, etLp);

                appsListContainer.addView(emptyCard);
            }
        } catch (Exception e) {
            callback.log("Yerel uygulama listesi gösterim hatası: " + e.getMessage());
        }
    }

    public void loadAppsFromServer() {
        if (appsListContainer == null) return;

        new Thread(() -> {
            try {
                handler.post(() -> {
                    appsListContainer.removeAllViews();
                    TextView loadingText = new TextView(context);
                    loadingText.setText("Yükleniyor...");
                    loadingText.setTextColor(ContextCompat.getColor(context, R.color.textLoading));
                    loadingText.setTextSize(14);
                    loadingText.setPadding(8, 8, 8, 8);
                    appsListContainer.addView(loadingText);
                });

                URL url = new URL("https://vnoisy.dev/apk/list.json");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    reader.close();
                    inputStream.close();

                    JSONObject jsonObject = new JSONObject(response.toString());
                    JSONArray listArray = jsonObject.getJSONArray("list");

                    handler.post(() -> {
                        appsListContainer.removeAllViews();
                        displayAppsList(listArray);
                    });

                    callback.log("" + listArray.length() + " uygulama yüklendi");
                } else {
                    handler.post(() -> {
                        appsListContainer.removeAllViews();
                        TextView errorText = new TextView(context);
                        errorText.setText("Hata: " + responseCode);
                        errorText.setTextColor(ContextCompat.getColor(context, R.color.statusErrorBright));
                        errorText.setTextSize(14);
                        errorText.setPadding(8, 8, 8, 8);
                        appsListContainer.addView(errorText);
                    });
                    callback.log("HTTP hatası: " + responseCode);
                }
                connection.disconnect();
            } catch (Exception e) {
                handler.post(() -> {
                    appsListContainer.removeAllViews();
                    TextView errorText = new TextView(context);
                    errorText.setText("Hata: " + e.getMessage());
                    errorText.setTextColor(ContextCompat.getColor(context, R.color.statusErrorBright));
                    errorText.setTextSize(14);
                    errorText.setPadding(8, 8, 8, 8);
                    appsListContainer.addView(errorText);
                });
                callback.log("Uygulama listesi yükleme hatası: " + e.getMessage());
            }
        }).start();
    }

    private void displayAppsList(JSONArray listArray) {
        if (appsListContainer == null || listArray == null) return;

        try {
            PackageManager pm = context.getPackageManager();

            for (int i = 0; i < listArray.length(); i++) {
                JSONObject appObj = listArray.getJSONObject(i);
                String packageName = appObj.getString("packageName");
                String displayName = appObj.getString("displayName");
                String downloadUrl = appObj.getString("downloadUrl");
                String version = appObj.getString("version");
                String currentVersion = "0";

                boolean isInstalled;
                try {
                    PackageInfo info = pm.getPackageInfo(packageName, 0);
                    currentVersion = info.versionName;
                    callback.log("currentVersion: " + currentVersion + " packageName: " + packageName);
                    isInstalled = true;
                } catch (PackageManager.NameNotFoundException e) {
                    isInstalled = false;
                }

                File downloadDir = new File(context.getCacheDir(), "downloads");
                File downloadedFile = new File(downloadDir, packageName + ".apk");
                boolean isDownloaded = downloadedFile.exists() && downloadedFile.isFile();

                LinearLayout appCard = new LinearLayout(context);
                appCard.setOrientation(LinearLayout.HORIZONTAL);
                appCard.setPadding(20, 20, 20, 20);
                appCard.setGravity(android.view.Gravity.CENTER_VERTICAL);
                appCard.setClickable(true);
                appCard.setFocusable(true);

                UiStyles.applySolidRoundedBackgroundDp(appCard,
                        ContextCompat.getColor(context, R.color.surfaceCardInner), 16f);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    appCard.setElevation(2f);
                }

                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                cardParams.setMargins(0, 0, 0, UiStyles.dimenPx(context, R.dimen.oem_card_margin));
                appCard.setMinimumHeight((int) (120 * context.getResources().getDisplayMetrics().density));

                android.widget.ImageView appIcon = new android.widget.ImageView(context);
                try {
                    if (isInstalled) {
                        android.graphics.drawable.Drawable icon = pm.getApplicationIcon(packageName);
                        appIcon.setImageDrawable(icon);
                    } else {
                        appIcon.setImageResource(android.R.drawable.sym_def_app_icon);
                    }
                } catch (Exception e) {
                    appIcon.setImageResource(android.R.drawable.sym_def_app_icon);
                }
                appIcon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                        (int) (44 * context.getResources().getDisplayMetrics().density),
                        (int) (44 * context.getResources().getDisplayMetrics().density));
                iconParams.setMargins(0, 0, 16, 0);
                appCard.addView(appIcon, iconParams);

                LinearLayout infoContainer = new LinearLayout(context);
                infoContainer.setOrientation(LinearLayout.VERTICAL);
                infoContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);

                final boolean hasUpdate = isInstalled && !currentVersion.equals(version);
                final boolean isLocked = packageName.equals("com.mapcontrol");
                final boolean finalIsInstalled = isInstalled;

                TextView nameText = new TextView(context);
                nameText.setText(displayName);
                nameText.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
                nameText.setTextSize(17);
                nameText.setTypeface(null, android.graphics.Typeface.NORMAL);
                infoContainer.addView(nameText);

                TextView statusText = new TextView(context);
                if (isInstalled) {
                    statusText.setText("Kurulu • v" + currentVersion);
                    statusText.setTextColor(ContextCompat.getColor(context, R.color.textSecondaryCool));
                } else {
                    statusText.setText("Kurulu değil");
                    statusText.setTextColor(ContextCompat.getColor(context, R.color.textMuted));
                }
                statusText.setTextSize(13);
                LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                statusParams.setMargins(0, 4, 0, 0);
                infoContainer.addView(statusText, statusParams);

                LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                appCard.addView(infoContainer, infoParams);

                LinearLayout rightContainer = new LinearLayout(context);
                rightContainer.setOrientation(LinearLayout.HORIZONTAL);
                rightContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);

                Button actionButton = new Button(context);
                actionButton.setTextSize(14);
                actionButton.setTypeface(null, android.graphics.Typeface.BOLD);
                actionButton.setPadding(24, 12, 24, 12);

                if (hasUpdate) {
                    actionButton.setText("GÜNCELLE");
                    actionButton.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
                    UiStyles.styleOemButton(actionButton, ContextCompat.getColor(context, R.color.buttonSuccessBright));
                    actionButton.setOnClickListener(v -> {
                        callback.log(displayName + " güncelleniyor...");
                        actionButton.setTag(downloadUrl);
                        downloadAndInstallApp(packageName, displayName, downloadUrl, actionButton);
                    });
                } else if (isInstalled && !isLocked) {
                    actionButton.setText("AÇ");
                    actionButton.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
                    UiStyles.styleOemButton(actionButton, ContextCompat.getColor(context, R.color.buttonPrimary));
                    actionButton.setOnClickListener(v -> launchApp(packageName));
                } else if (isLocked) {
                    actionButton.setText("KİLİTLİ");
                    actionButton.setTextColor(ContextCompat.getColor(context, R.color.textSecondaryCool));
                    UiStyles.styleOemButton(actionButton, ContextCompat.getColor(context, R.color.textMuted));
                    actionButton.setEnabled(false);
                } else if (isDownloaded) {
                    actionButton.setText("KUR");
                    actionButton.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
                    UiStyles.styleOemButton(actionButton, ContextCompat.getColor(context, R.color.buttonSuccessBright));
                    actionButton.setOnClickListener(v -> {
                        callback.log(displayName + " kuruluyor (indirilmiş dosyadan)...");
                        installApkFile(downloadedFile);
                    });
                } else {
                    actionButton.setText("KUR");
                    actionButton.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
                    UiStyles.styleOemButton(actionButton, ContextCompat.getColor(context, R.color.buttonSuccessBright));
                    actionButton.setOnClickListener(v -> {
                        actionButton.setTag(downloadUrl);
                        downloadAndInstallApp(packageName, displayName, downloadUrl, actionButton);
                    });
                }

                rightContainer.addView(actionButton);

                if (finalIsInstalled && !isLocked) {
                    Button removeButton = new Button(context);
                    removeButton.setText("KALDIR");
                    removeButton.setTextSize(14);
                    removeButton.setTypeface(null, android.graphics.Typeface.BOLD);
                    removeButton.setTextColor(ContextCompat.getColor(context, R.color.textDestructive));
                    removeButton.setBackgroundColor(ContextCompat.getColor(context, R.color.transparent));
                    removeButton.setPadding(18, 12, 18, 12);
                    removeButton.setOnClickListener(v -> uninstallApp(packageName, displayName));
                    LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    removeParams.setMargins(UiStyles.dimenPx(context, R.dimen.spacing_medium), 0, 0, 0);
                    rightContainer.addView(removeButton, removeParams);
                }

                appCard.addView(rightContainer);
                appsListContainer.addView(appCard, cardParams);
            }

            if (listArray.length() == 0) {
                float d = context.getResources().getDisplayMetrics().density;
                LinearLayout emptyCard = new LinearLayout(context);
                emptyCard.setOrientation(LinearLayout.VERTICAL);
                emptyCard.setGravity(android.view.Gravity.CENTER);
                emptyCard.setPadding(32, 48, 32, 48);
                AppCompatImageView emptyIcon = new AppCompatImageView(context);
                emptyIcon.setImageResource(R.drawable.ic_mdi_package_variant);
                emptyIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int iconPx = Math.round(56 * d);
                emptyIcon.setLayoutParams(new LinearLayout.LayoutParams(iconPx, iconPx));
                emptyIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.textMuted)));
                emptyCard.addView(emptyIcon);
                TextView emptyText = new TextView(context);
                emptyText.setText("Henüz uygulama bulunamadı");
                emptyText.setTextColor(ContextCompat.getColor(context, R.color.textMuted));
                emptyText.setTextSize(15);
                emptyText.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                etLp.topMargin = Math.round(16 * d);
                emptyCard.addView(emptyText, etLp);
                appsListContainer.addView(emptyCard);
            }
        } catch (Exception e) {
            callback.log("Uygulama listesi gösterim hatası: " + e.getMessage());
        }
    }

    public void deleteApp(String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            String displayName = pm.getApplicationLabel(appInfo).toString();

            if (callback.isSystemOrPrivApp(packageName)) {
                callback.log("Sistem uygulaması silinemez: " + packageName);
                return;
            }

            if ("com.mapcontrol".equals(packageName)) {
                callback.log("Bu uygulama silinemez: " + packageName);
                return;
            }

            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(android.net.Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            callback.log("Uygulama silme başlatıldı: " + displayName + " (" + packageName + ")");
        } catch (Exception e) {
            callback.log("Uygulama silme hatası: " + e.getMessage());
        }
    }

    public void launchApp(String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP);
                context.getApplicationContext().startActivity(launchIntent);
                callback.log("Uygulama açıldı: " + packageName);
            } else {
                callback.log("Uygulama açılamadı (launch intent bulunamadı): " + packageName);
            }
        } catch (Exception e) {
            callback.log("Uygulama açma hatası: " + e.getMessage());
            try {
                PackageManager pm = context.getPackageManager();
                Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.getApplicationContext().startActivity(launchIntent);
                    callback.log("Uygulama açıldı (ikinci deneme): " + packageName);
                }
            } catch (Exception e2) {
                callback.log("Uygulama açma hatası (ikinci deneme): " + e2.getMessage());
            }
        }
    }

    private void uninstallApp(String packageName, String displayName) {
        if (activity == null) return;

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Uygulamayı Kaldır")
                .setMessage(displayName + " uygulamasını kaldırmak istediğinize emin misiniz?")
                .setPositiveButton("Evet", (d, which) -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_DELETE);
                        intent.setData(android.net.Uri.parse("package:" + packageName));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                        callback.log(displayName + " kaldırılıyor...");
                        handler.postDelayed(() -> {
                            if (isLocalMode) loadLocalApps(); else loadAppsFromServer();
                        }, 2000);
                    } catch (Exception e) {
                        callback.log("Uygulama kaldırma hatası: " + e.getMessage());
                        Toast.makeText(context, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Hayır", null)
                .create();

        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(context, R.color.statusErrorBright));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(17);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTypeface(null, android.graphics.Typeface.BOLD);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(context, R.color.statusNeutralGray));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextSize(17);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTypeface(null, android.graphics.Typeface.BOLD);
    }

    private void downloadAndInstallApp(String packageName, String displayName, String downloadUrl, Button button) {
        // (same as MainActivity implementation) - kept as-is but with context replacements
        new Thread(() -> {
            File apkFile = null;
            InputStream inputStream = null;
            FileOutputStream outputStream = null;
            HttpURLConnection connection = null;
            try {
                handler.post(() -> {
                    button.setEnabled(false);
                    button.setText("İndiriliyor...");
                    UiStyles.setButtonStartIconTinted(button, R.drawable.ic_mdi_timer_sand,
                            ContextCompat.getColor(context, R.color.textPrimary),
                            UiStyles.dimenPx(context, R.dimen.spacing_small));
                });

                callback.log("[INFO] APK indirme başlatılıyor: " + displayName);
                callback.log("[INFO] URL: " + downloadUrl);

                URL url = new URL(downloadUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(60000);
                connection.setReadTimeout(60000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");
                connection.setRequestProperty("Accept", "*/*");
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                connection.setRequestProperty("Cache-Control", "no-cache");

                int responseCode = connection.getResponseCode();
                int redirectCount = 0;
                while (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    redirectCount++;
                    if (redirectCount > 5) throw new Exception("Çok fazla yönlendirme (redirect)");
                    String location = connection.getHeaderField("Location");
                    if (location == null) break;
                    callback.log("[INFO] Redirect takip ediliyor: " + location);
                    connection.disconnect();
                    url = new URL(location);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(60000);
                    connection.setReadTimeout(60000);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");
                    connection.setRequestProperty("Accept", "*/*");
                    responseCode = connection.getResponseCode();
                }

                if (responseCode != HttpURLConnection.HTTP_OK) throw new Exception("HTTP hatası: " + responseCode);

                String contentType = connection.getContentType();
                callback.log("[INFO] Content-Type: " + contentType);
                if (contentType != null && !contentType.contains("application/vnd.android.package-archive") &&
                        !contentType.contains("application/octet-stream") && !contentType.contains("application/zip")) {
                    callback.log("[WARN] Beklenmeyen Content-Type: " + contentType + " (devam ediliyor)");
                }

                long fileLength = connection.getContentLengthLong();
                callback.log("[INFO] Beklenen dosya boyutu: " + fileLength + " bytes");

                inputStream = connection.getInputStream();
                File downloadDir = new File(context.getCacheDir(), "downloads");
                if (!downloadDir.exists()) downloadDir.mkdirs();
                apkFile = new File(downloadDir, packageName + ".apk");
                if (apkFile.exists()) apkFile.delete();

                outputStream = new FileOutputStream(apkFile);
                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                long lastLogTime = System.currentTimeMillis();
                int lastUiPercent = -1;
                long lastUiBytesUpdate = 0L;

                while ((count = inputStream.read(buffer)) != -1) {
                    total += count;
                    outputStream.write(buffer, 0, count);
                    long currentTime = System.currentTimeMillis();
                    if (fileLength > 0) {
                        int percent = (int) ((total * 100L) / fileLength);
                        if (percent > 100) percent = 100;
                        if (percent != lastUiPercent) {
                            lastUiPercent = percent;
                            final int p = percent;
                            handler.post(() -> {
                                button.setText("İndiriliyor %" + p);
                                UiStyles.setButtonStartIconTinted(button, R.drawable.ic_mdi_timer_sand,
                                        ContextCompat.getColor(context, R.color.textPrimary),
                                        UiStyles.dimenPx(context, R.dimen.spacing_small));
                            });
                        }
                    } else {
                        if (currentTime - lastUiBytesUpdate >= 400L) {
                            lastUiBytesUpdate = currentTime;
                            final String sizeLabel = formatFileSize(total);
                            handler.post(() -> {
                                button.setText("İndiriliyor… " + sizeLabel);
                                UiStyles.setButtonStartIconTinted(button, R.drawable.ic_mdi_timer_sand,
                                        ContextCompat.getColor(context, R.color.textPrimary),
                                        UiStyles.dimenPx(context, R.dimen.spacing_small));
                            });
                        }
                    }
                    if (currentTime - lastLogTime > 1000) {
                        if (fileLength > 0) {
                            int percent = (int) ((total * 100L) / fileLength);
                            if (percent > 100) percent = 100;
                            callback.log("[INFO] İndirme ilerlemesi: " + percent + "% (" + total + "/" + fileLength + " bytes)");
                        } else {
                            callback.log("[INFO] İndirilen: " + total + " bytes");
                        }
                        lastLogTime = currentTime;
                    }
                }

                outputStream.flush();
                outputStream.close();
                outputStream = null;
                inputStream.close();
                inputStream = null;
                connection.disconnect();
                connection = null;

                long actualFileSize = apkFile.length();
                callback.log("[INFO] İndirilen dosya boyutu: " + actualFileSize + " bytes");
                if (!apkFile.exists() || actualFileSize == 0) throw new Exception("APK dosyası boş veya oluşturulamadı");

                handler.post(() -> {
                    button.setText("Kuruluyor...");
                    UiStyles.setButtonStartIconTinted(button, R.drawable.ic_mdi_package_variant,
                            ContextCompat.getColor(context, R.color.textPrimary),
                            UiStyles.dimenPx(context, R.dimen.spacing_small));
                });

                boolean installSuccess = installApkViaShell(apkFile);
                if (!installSuccess) {
                    android.net.Uri apkUri;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", apkFile);
                    } else {
                        apkUri = android.net.Uri.fromFile(apkFile);
                    }

                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(intent);
                }

                callback.log(displayName + " indirildi ve kuruluyor...");
                handler.post(() -> Toast.makeText(context, displayName + " kuruluyor...", Toast.LENGTH_SHORT).show());
                handler.postDelayed(this::loadAppsFromServer, 5000);
            } catch (Exception e) {
                callback.log("[ERROR] APK indirme/yükleme hatası: " + e.getMessage());
                handler.post(() -> {
                    button.setEnabled(true);
                    button.setText("Manuel İndir");
                    UiStyles.setButtonStartIconTinted(button, R.drawable.ic_mdi_download,
                            ContextCompat.getColor(context, R.color.textPrimary),
                            UiStyles.dimenPx(context, R.dimen.spacing_small));
                });
            } finally {
                try { if (inputStream != null) inputStream.close(); } catch (Exception ignored) {}
                try { if (outputStream != null) outputStream.close(); } catch (Exception ignored) {}
                try { if (connection != null) connection.disconnect(); } catch (Exception ignored) {}
            }
        }).start();
    }

    public void showDownloadedFiles() {
        showDownloadedFilesDialog();
    }

    private void showDownloadedFilesDialog() {
        // Use the original MainActivity logic, but with context replacements.
        try {
            if (downloadedFilesDialog != null) {
                try {
                    if (downloadedFilesDialog.isShowing()) {
                        downloadedFilesDialog.dismiss();
                    }
                } catch (Exception ignored) {}
                downloadedFilesDialog = null;
            }

            File downloadDir;
            String folderName;
            if (isLocalMode) {
                downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                folderName = "Download klasörü";
            } else {
                downloadDir = new File(context.getCacheDir(), "downloads");
                folderName = "Cache klasörü";
            }
            if (!downloadDir.exists()) downloadDir.mkdirs();

            File[] files = downloadDir.listFiles();
            int fileCount = 0;
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".apk")) fileCount++;
                }
            }

            LinearLayout dialogLayout = new LinearLayout(context);
            dialogLayout.setOrientation(LinearLayout.VERTICAL);
            dialogLayout.setPadding(0, 0, 0, 0);
            dialogLayout.setBackgroundColor(ContextCompat.getColor(context, R.color.backgroundPage));

            LinearLayout headerLayout = new LinearLayout(context);
            headerLayout.setOrientation(LinearLayout.HORIZONTAL);
            headerLayout.setBackgroundColor(ContextCompat.getColor(context, R.color.surfaceCardInner));
            headerLayout.setPadding(24, 20, 24, 20);
            headerLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

            LinearLayout titleContainer = new LinearLayout(context);
            titleContainer.setOrientation(LinearLayout.VERTICAL);

            TextView titleText = new TextView(context);
            titleText.setText("İndirilen Dosyalar");
            titleText.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
            titleText.setTextSize(20);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            titleContainer.addView(titleText);

            TextView subtitleText = new TextView(context);
            subtitleText.setText(fileCount + " dosya • " + folderName);
            subtitleText.setTextColor(ContextCompat.getColor(context, R.color.textSecondaryCool));
            subtitleText.setTextSize(13);
            subtitleText.setPadding(0, 4, 0, 0);
            titleContainer.addView(subtitleText);

            LinearLayout.LayoutParams titleContainerParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            headerLayout.addView(titleContainer, titleContainerParams);

            if (fileCount > 0) {
                Button deleteAllButton = new Button(context);
                deleteAllButton.setText("Tümünü Sil");
                deleteAllButton.setTextSize(13);
                deleteAllButton.setTextColor(ContextCompat.getColor(context, R.color.textDestructive));
                deleteAllButton.setBackgroundColor(ContextCompat.getColor(context, R.color.transparent));
                deleteAllButton.setPadding(12, 8, 12, 8);
                UiStyles.setButtonStartIconTinted(deleteAllButton, R.drawable.ic_mdi_delete,
                        ContextCompat.getColor(context, R.color.textDestructive),
                        UiStyles.dimenPx(context, R.dimen.spacing_small));
                deleteAllButton.setOnClickListener(v -> {
                    AlertDialog confirmDialog = new AlertDialog.Builder(context)
                            .setTitle("Tümünü Sil")
                            .setMessage("Tüm indirilen dosyaları silmek istediğinize emin misiniz?")
                            .setPositiveButton("Sil", (d, which) -> {
                                File dirToDelete = isLocalMode
                                        ? android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                        : new File(context.getCacheDir(), "downloads");
                                if (dirToDelete.exists()) {
                                    File[] del = dirToDelete.listFiles();
                                    if (del != null) {
                                        for (File f : del) {
                                            if (f.isFile() && f.getName().endsWith(".apk")) f.delete();
                                        }
                                    }
                                }
                                showDownloadedFilesDialog();
                            })
                            .setNegativeButton("İptal", null)
                            .create();
                    confirmDialog.show();
                });
                headerLayout.addView(deleteAllButton);
            }

            dialogLayout.addView(headerLayout);

            ScrollView scrollView = new ScrollView(context);
            scrollView.setBackgroundColor(ContextCompat.getColor(context, R.color.backgroundPage));
            scrollView.setPadding(16, 16, 16, 16);
            scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    500));

            LinearLayout filesLayout = new LinearLayout(context);
            filesLayout.setOrientation(LinearLayout.VERTICAL);

            if (fileCount == 0) {
                LinearLayout emptyWrap = new LinearLayout(context);
                emptyWrap.setOrientation(LinearLayout.VERTICAL);
                emptyWrap.setGravity(android.view.Gravity.CENTER);
                emptyWrap.setPadding(32, 64, 32, 64);
                AppCompatImageView emptyIco = new AppCompatImageView(context);
                emptyIco.setImageResource(R.drawable.ic_mdi_inbox_outline);
                emptyIco.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int epx = Math.round(48 * context.getResources().getDisplayMetrics().density);
                emptyIco.setLayoutParams(new LinearLayout.LayoutParams(epx, epx));
                emptyIco.setImageTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.textSecondaryCool)));
                emptyWrap.addView(emptyIco);
                TextView emptyText = new TextView(context);
                emptyText.setText("İndirilen dosya bulunmuyor");
                emptyText.setTextColor(ContextCompat.getColor(context, R.color.textSecondaryCool));
                emptyText.setTextSize(16);
                emptyText.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams etP = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                etP.topMargin = Math.round(12 * context.getResources().getDisplayMetrics().density);
                emptyWrap.addView(emptyText, etP);
                filesLayout.addView(emptyWrap);
            } else if (files != null) {
                for (File file : files) {
                    if (!file.isFile() || !file.getName().endsWith(".apk")) continue;

                    LinearLayout fileCard = new LinearLayout(context);
                    fileCard.setOrientation(LinearLayout.HORIZONTAL);
                    fileCard.setPadding(UiStyles.dimenPx(context, R.dimen.spacing_medium),
                            UiStyles.dimenPx(context, R.dimen.spacing_medium),
                            UiStyles.dimenPx(context, R.dimen.spacing_medium),
                            UiStyles.dimenPx(context, R.dimen.spacing_medium));
                    UiStyles.applySolidRoundedBackgroundDp(fileCard,
                            ContextCompat.getColor(context, R.color.surfaceCardElevated), 16f);
                    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    cardParams.setMargins(0, 0, 0, UiStyles.dimenPx(context, R.dimen.oem_card_margin));

                    LinearLayout iconBox = new LinearLayout(context);
                    iconBox.setOrientation(LinearLayout.VERTICAL);
                    iconBox.setGravity(android.view.Gravity.CENTER);
                    UiStyles.applySolidRoundedBackgroundDp(iconBox,
                            ContextCompat.getColor(context, R.color.buttonPrimary), 12f);
                    iconBox.setPadding(16, 16, 16, 16);

                    AppCompatImageView iconApk = new AppCompatImageView(context);
                    iconApk.setImageResource(R.drawable.ic_mdi_package_variant);
                    iconApk.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    int innerIcon = Math.round(28 * context.getResources().getDisplayMetrics().density);
                    iconApk.setLayoutParams(new LinearLayout.LayoutParams(innerIcon, innerIcon));
                    iconApk.setImageTintList(ColorStateList.valueOf(
                            ContextCompat.getColor(context, R.color.textPrimary)));
                    iconBox.addView(iconApk);

                    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(60, 60);
                    iconParams.setMargins(0, 0, 12, 0);
                    fileCard.addView(iconBox, iconParams);

                    LinearLayout infoLayout = new LinearLayout(context);
                    infoLayout.setOrientation(LinearLayout.VERTICAL);
                    infoLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

                    String sizeStr = formatFileSize(file.length());
                    TextView nameText = new TextView(context);
                    nameText.setText(file.getName().replace(".apk", ""));
                    nameText.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
                    nameText.setTextSize(14);
                    nameText.setTypeface(null, android.graphics.Typeface.BOLD);
                    nameText.setMaxLines(1);
                    nameText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    infoLayout.addView(nameText);

                    TextView sizeText = new TextView(context);
                    sizeText.setText("Boyut: " + sizeStr);
                    sizeText.setTextColor(ContextCompat.getColor(context, R.color.textSecondaryCool));
                    sizeText.setTextSize(12);
                    sizeText.setPadding(0, 2, 0, 0);
                    infoLayout.addView(sizeText);

                    fileCard.addView(infoLayout, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                    LinearLayout buttonsContainer = new LinearLayout(context);
                    buttonsContainer.setOrientation(LinearLayout.HORIZONTAL);
                    buttonsContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);

                    Button installButton = new Button(context);
                    installButton.setText("KUR");
                    installButton.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
                    UiStyles.styleOemButton(installButton, ContextCompat.getColor(context, R.color.buttonPrimary));
                    installButton.setTextSize(13);
                    installButton.setTypeface(null, android.graphics.Typeface.BOLD);
                    installButton.setPadding(16, 12, 16, 12);
                    UiStyles.setButtonStartIconTinted(installButton, R.drawable.ic_mdi_package_variant,
                            ContextCompat.getColor(context, R.color.textPrimary),
                            UiStyles.dimenPx(context, R.dimen.spacing_small));
                    buttonsContainer.addView(installButton);

                    Button deleteButton = new Button(context);
                    deleteButton.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
                    UiStyles.styleOemButton(deleteButton, ContextCompat.getColor(context, R.color.buttonDestructiveBg));
                    deleteButton.setTextSize(18);
                    deleteButton.setPadding(16, 12, 16, 12);
                    UiStyles.setButtonIconOnlyTinted(deleteButton, R.drawable.ic_mdi_delete,
                            ContextCompat.getColor(context, R.color.textPrimary), "Dosyayı sil");
                    buttonsContainer.addView(deleteButton);

                    fileCard.addView(buttonsContainer);
                    filesLayout.addView(fileCard, cardParams);

                    File finalFile = file;
                    installButton.setOnClickListener(v -> installApkFile(finalFile));
                    deleteButton.setOnClickListener(v -> {
                        AlertDialog confirmDialog = new AlertDialog.Builder(context)
                                .setTitle("Dosyayı Sil")
                                .setMessage(finalFile.getName() + " dosyasını silmek istediğinize emin misiniz?")
                                .setPositiveButton("Evet", (d, which) -> {
                                    if (finalFile.delete()) {
                                        Toast.makeText(context, "Dosya silindi", Toast.LENGTH_SHORT).show();
                                        callback.log("Dosya silindi: " + finalFile.getName());
                                        showDownloadedFilesDialog();
                                    } else {
                                        Toast.makeText(context, "Dosya silinemedi", Toast.LENGTH_SHORT).show();
                                        callback.log("Dosya silinemedi: " + finalFile.getName());
                                    }
                                })
                                .setNegativeButton("Hayır", null)
                                .create();
                        confirmDialog.show();
                    });
                }
            }

            scrollView.addView(filesLayout);
            dialogLayout.addView(scrollView);

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setView(dialogLayout)
                    .setPositiveButton("Kapat", null)
                    .create();

            dialog.setOnDismissListener(d -> {
                if (downloadedFilesDialog == d) {
                    downloadedFilesDialog = null;
                }
            });
            if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            downloadedFilesDialog = dialog;
            dialog.show();
        } catch (Exception e) {
            callback.log("İndirilen dosyalar gösterim hatası: " + e.getMessage());
            Toast.makeText(context, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    public void installApkFile(String fileName) {
        try {
            File downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            File apkFile = new File(downloadDir, fileName);
            installApkFile(apkFile);
        } catch (Exception e) {
            callback.log("APK kurulum hatası: " + e.getMessage());
            handler.post(() -> Toast.makeText(context, "APK kurulum hatası: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    public void installApkFile(File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(context, "APK dosyası bulunamadı", Toast.LENGTH_SHORT).show();
            callback.log("APK dosyası bulunamadı");
            return;
        }

        new Thread(() -> {
            try {
                handler.post(() -> Toast.makeText(context, "Kurulum başlatılıyor...", Toast.LENGTH_SHORT).show());

                android.net.Uri apkUri;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", apkFile);
                } else {
                    apkUri = android.net.Uri.fromFile(apkFile);
                }

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                handler.post(() -> {
                    try {
                        context.startActivity(intent);
                        callback.log("APK kurulum intent başlatıldı: " + apkFile.getName());
                        Toast.makeText(context, "Kurulum başlatıldı", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(context, "Kurulum başlatılamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        callback.log("[ERROR] APK kurulum intent hatası: " + e.getMessage());
                    }
                });

                handler.postDelayed(this::loadAppsFromServer, 2000);
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(context, "Kurulum hatası: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                callback.log("[ERROR] APK kurulum hatası: " + e.getMessage());
            }
        }).start();
    }

    private boolean installApkViaShell(File apkFile) {
        try {
            String installCmd = "pm install -r " + apkFile.getAbsolutePath();
            callback.log("Kurulum komutu: " + installCmd);

            Process installProcess = Runtime.getRuntime().exec(installCmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(installProcess.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(installProcess.getErrorStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append("\n");
            while ((line = errorReader.readLine()) != null) output.append("ERR: ").append(line).append("\n");

            int exitCode = installProcess.waitFor();
            String outputStr = output.toString().trim();
            callback.log("pm install exit code: " + exitCode);
            callback.log("pm install output: " + (outputStr.isEmpty() ? "(boş)" : outputStr));

            if (exitCode == 0 || outputStr.contains("Success")) {
                handler.post(() -> Toast.makeText(context, "Uygulama kuruldu!", Toast.LENGTH_SHORT).show());
                callback.log("APK shell ile başarıyla kuruldu");
                return true;
            } else {
                callback.log("Shell kurulum başarısız, intent deneniyor");
                return false;
            }
        } catch (Exception e) {
            callback.log("Shell kurulum hatası: " + e.getMessage());
            return false;
        }
    }

    public void performReset() {
        callback.log("Reset işlemi başlatılıyor...");

        new Thread(() -> {
            try {
                URL url = new URL("https://vnoisy.dev/apk/list.json");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    handler.post(() -> Toast.makeText(context, "JSON yüklenemedi: " + responseCode, Toast.LENGTH_SHORT).show());
                    connection.disconnect();
                    return;
                }

                InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();
                inputStream.close();
                connection.disconnect();

                JSONObject jsonObject = new JSONObject(response.toString());
                JSONArray listArray = jsonObject.getJSONArray("list");

                PackageManager pm = context.getPackageManager();
                ArrayList<String> matchingPackages = new ArrayList<>();

                for (int i = 0; i < listArray.length(); i++) {
                    JSONObject appObj = listArray.getJSONObject(i);
                    String packageName = appObj.getString("packageName");
                    try {
                        pm.getPackageInfo(packageName, 0);
                        matchingPackages.add(packageName);
                        callback.log("Eşleşen uygulama bulundu: " + packageName);
                    } catch (PackageManager.NameNotFoundException ignored) {
                    }
                }

                handler.post(() -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle("Sıfırlama Seçeneği");
                    builder.setMessage("Ne silmek istiyorsunuz?");

                    builder.setPositiveButton("Bağlı Uygulamaları Kaldır", (dialog, which) -> {
                        showDeleteConfirmationDialog(matchingPackages, true, true);
                    });

                    builder.setNeutralButton("Sadece Bu Uygulamayı Kaldır", (dialog, which) -> {
                        showDeleteConfirmationDialog(null, false, true);
                    });

                    builder.setNegativeButton("İptal", (dialog, which) -> {
                        dialog.dismiss();
                        callback.log("Reset işlemi iptal edildi");
                    });
                    builder.setCancelable(false);
                    builder.show();
                });

            } catch (Exception e) {
                handler.post(() -> Toast.makeText(context, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                callback.log("Reset işlemi hatası: " + e.getMessage());
            }
        }).start();
    }

    private void showDeleteConfirmationDialog(ArrayList<String> matchingPackages, boolean deleteRelatedFiles, boolean deleteSelf) {
        String message;
        if (matchingPackages != null && matchingPackages.size() > 0) {
            int appCount = matchingPackages.size();
            message = "Mevcut uygulama ve (" + appCount + ") yüklediğiniz tüm uygulamalar silinecek";
            if (deleteRelatedFiles) {
                message += " ve bağlı olanlar (/data/local/tmp altındaki APK'lar) da silinecek";
            }
            message += ", yüklü uygulama sayısı kadar onay vermeniz gerekebilir.";
        } else {
            message = "Sadece mevcut uygulama (com.mapcontrol) silinecek.";
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Sıfırlama Onayı");
        builder.setMessage(message);
        builder.setPositiveButton("Evet", (dialog, which) -> deleteMatchingApps(matchingPackages, deleteRelatedFiles, deleteSelf));
        builder.setNegativeButton("Hayır", (dialog, which) -> {
            dialog.dismiss();
            callback.log("Reset işlemi iptal edildi");
        });
        builder.setCancelable(false);
        builder.show();
    }

    private void deleteMatchingApps(ArrayList<String> packages, boolean deleteRelatedFiles, boolean deleteSelf) {
        new Thread(() -> {
            try {
                callback.log("Uygulama silme işlemi başlatılıyor...");
                if (packages != null && packages.size() > 0) {
                    deletePackagesSequentially(packages, 0, deleteRelatedFiles, deleteSelf);
                } else {
                    if (deleteSelf) deleteSelfApp();
                }
            } catch (Exception e) {
                callback.log("Uygulama silme işlemi hatası: " + e.getMessage());
            }
        }).start();
    }

    private void deletePackagesSequentially(ArrayList<String> packages, int index, boolean deleteRelatedFiles, boolean deleteSelf) {
        if (index >= packages.size()) {
            if (deleteRelatedFiles) deleteRelatedFiles();
            if (deleteSelf) deleteSelfApp();
            return;
        }

        String packageName = packages.get(index);
        callback.log("Siliniyor: " + packageName);

        try {
            Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
            uninstallIntent.setData(android.net.Uri.parse("package:" + packageName));
            uninstallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            handler.post(() -> {
                try {
                    context.startActivity(uninstallIntent);
                    callback.log("Silme Intent'i gönderildi: " + packageName);
                } catch (Exception e) {
                    callback.log("Intent ile silme başarısız, pm komutu deneniyor: " + e.getMessage());
                    tryUninstallViaPm(packageName);
                }
            });

            handler.postDelayed(() -> deletePackagesSequentially(packages, index + 1, deleteRelatedFiles, deleteSelf), 500);
        } catch (Exception intentEx) {
            callback.log("Intent hatası, pm komutu deneniyor: " + intentEx.getMessage());
            tryUninstallViaPm(packageName);
            handler.postDelayed(() -> deletePackagesSequentially(packages, index + 1, deleteRelatedFiles, deleteSelf), 500);
        }
    }

    private void deleteRelatedFiles() {
        new Thread(() -> {
            try {
                callback.log("/data/local/tmp altındaki APK'lar siliniyor...");
                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", "rm -f /data/local/tmp/*.apk"});
                process.waitFor();
                callback.log("/data/local/tmp temizleme işlemi tamamlandı");
            } catch (Exception e) {
                callback.log("/data/local/tmp temizleme hatası: " + e.getMessage());
            }
        }).start();
    }

    private void deleteSelfApp() {
        handler.post(() -> {
            try {
                String selfPackage = context.getPackageName();
                callback.log("Uygulama kendisini siliyor: " + selfPackage);
                Toast.makeText(context, "Uygulama kendisini siliyor...", Toast.LENGTH_SHORT).show();

                handler.postDelayed(() -> {
                    try {
                        Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
                        uninstallIntent.setData(android.net.Uri.parse("package:" + selfPackage));
                        uninstallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(uninstallIntent);
                        callback.log("Uygulama silme Intent'i gönderildi");
                        handler.postDelayed(() -> {
                            new Thread(() -> {
                                try {
                                    Runtime.getRuntime().exec(new String[]{"sh", "-c", "pm uninstall " + selfPackage});
                                    callback.log("Uygulama silme pm komutu da gönderildi");
                                } catch (Exception ignored) {
                                }
                            }).start();
                        }, 2000);
                    } catch (Exception e) {
                        callback.log("Uygulama silme hatası: " + e.getMessage());
                    }
                }, 1000);
            } catch (Exception e) {
                callback.log("Uygulama silme hatası: " + e.getMessage());
            }
        });
    }

    private void tryUninstallViaPm(String packageName) {
        new Thread(() -> {
            try {
                callback.log("pm komutu ile silme deneniyor: " + packageName);
                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", "pm uninstall " + packageName});
                process.waitFor();
            } catch (Exception e) {
                callback.log("pm komutu silme hatası (" + packageName + "): " + e.getMessage());
            }
        }).start();
    }
}

