package com.mapcontrol.ui.builder;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.mapcontrol.util.ApkSessionInstaller;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.NetworkWifiHelper;

public class AppsTabBuilder {

    private static final String SERVER_LIST_URL = "https://vnoisy.dev/apk/list.json";
    private static final long SERVER_RETRY_INITIAL_MS = 2_000L;
    private static final long SERVER_RETRY_MAX_MS = 15_000L;
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

    private boolean tabVisible;
    private boolean serverLoadNeedsRetry;
    private long serverRetryDelayMs = SERVER_RETRY_INITIAL_MS;
    private int serverLoadGeneration;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean networkCallbackRegistered;

    /** Sunucu listesi yeniden çizilse de indirme yüzdesi ve kurulum yazısı kaybolmasın. */
    private final Map<String, DownloadProgress> downloadProgress = new HashMap<>();
    private final Map<String, AppRow> appRows = new HashMap<>();
    private JSONArray lastServerList;
    private JSONArray lastServerGroups;
    private final Map<String, CatalogApp> catalogApps = new LinkedHashMap<>();
    private final Map<String, CatalogGroup> catalogGroups = new LinkedHashMap<>();
    private final ArrayDeque<InstallJob> installQueue = new ArrayDeque<>();
    private int installQueueGeneration;
    private boolean serverListBound;
    private int downloadGenerationSeq;

    private static final class DownloadProgress {
        static final int DOWNLOADING = 1;
        static final int INSTALLING = 2;
        static final int FAILED = 3;

        int generation;
        int phase;
        int percent = -1;
        long bytes;
        String displayName;
        String downloadUrl;
    }

    private static final class AppRow {
        final Button button;
        final TextView status;
        final String displayName;

        AppRow(Button button, TextView status, String displayName) {
            this.button = button;
            this.status = status;
            this.displayName = displayName;
        }
    }

    private static final class CatalogGroup {
        final String title;
        final String warning;

        CatalogGroup(String title, String warning) {
            this.title = title != null ? title : "";
            this.warning = warning != null ? warning : "";
        }
    }

    private static final class CatalogApp {
        final String packageName;
        final String displayName;
        final String downloadUrl;
        final String groupId;
        final List<String> requires;

        CatalogApp(String packageName, String displayName, String downloadUrl,
                   String groupId, List<String> requires) {
            this.packageName = packageName;
            this.displayName = displayName;
            this.downloadUrl = downloadUrl;
            this.groupId = groupId;
            this.requires = requires;
        }

        static CatalogApp from(JSONObject appObj) {
            String groupId = appObj.optString("group", "").trim();
            List<String> requires = new ArrayList<>();
            JSONArray array = appObj.optJSONArray("requires");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    String req = array.optString(i, "").trim();
                    if (!req.isEmpty()) requires.add(req);
                }
            }
            return new CatalogApp(
                    appObj.optString("packageName", "").trim(),
                    appObj.optString("displayName", ""),
                    appObj.optString("downloadUrl", ""),
                    groupId,
                    requires);
        }
    }

    private static final class InstallJob {
        final String packageName;
        final String displayName;
        final String downloadUrl;
        final File readyFile;

        InstallJob(String packageName, String displayName, String downloadUrl, File readyFile) {
            this.packageName = packageName;
            this.displayName = displayName;
            this.downloadUrl = downloadUrl;
            this.readyFile = readyFile;
        }
    }

    private final Runnable serverRetryRunnable = () -> {
        if (!isLocalMode && serverLoadNeedsRetry) {
            loadAppsFromServerInternal(false);
        }
    };

    public AppsTabBuilder(Context context, AppsCallback callback) {
        this.context = context;
        this.activity = (context instanceof Activity) ? (Activity) context : null;
        this.callback = callback;
        build();
    }

    public LinearLayout build() {
        appRows.clear();
        serverListBound = false;
        tabContent = new LinearLayout(context);
        tabContent.setOrientation(LinearLayout.VERTICAL);
        int margin = UiStyles.dimenPx(context, R.dimen.oem_card_margin);
        tabContent.setPadding(margin, margin, margin, margin);
        tabContent.setBackgroundColor(UiStyles.color(context, R.color.transparent));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int inner = UiStyles.dimenPx(context, R.dimen.oem_card_inner_padding);
        card.setPadding(inner, inner, inner, inner);
        UiStyles.setGlassCardBackground(card);

        ScrollView appsListScrollView = new ScrollView(context);
        appsListScrollView.setBackgroundColor(UiStyles.color(context, R.color.transparent));
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

        if (!isLocalMode && lastServerList != null) {
            displayAppsList(lastServerList);
        }

        return tabContent;
    }

    public LinearLayout getTabContent() {
        return tabContent;
    }

    public LinearLayout buildTopBarButtons(Context ctx) {
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(android.view.Gravity.CENTER_VERTICAL);

        float density = ctx.getResources().getDisplayMetrics().density;
        int btnSize = Math.round(38 * density);
        int marginEnd = Math.round(4 * density);
        int pad = Math.round(8 * density);

        Button btnRefreshApps = new Button(ctx);
        btnRefreshApps.setMinWidth(0);
        btnRefreshApps.setMinimumWidth(0);
        btnRefreshApps.setMinHeight(0);
        btnRefreshApps.setMinimumHeight(0);
        btnRefreshApps.setPadding(pad, pad, pad, pad);
        UiStyles.setGlassCardBackground(btnRefreshApps);
        UiStyles.setButtonIconOnlyTinted(btnRefreshApps, R.drawable.ic_mdi_refresh,
                UiStyles.color(ctx, R.color.textPrimary), ctx.getString(R.string.apps_cd_refresh_list));
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(btnSize, btnSize);
        refreshParams.setMargins(0, 0, marginEnd, 0);
        container.addView(btnRefreshApps, refreshParams);
        btnRefreshApps.setOnClickListener(v -> {
            if (isLocalMode) {
                loadLocalApps();
            } else {
                loadAppsFromServer();
            }
        });

        Button btnDownloadedFiles = new Button(ctx);
        btnDownloadedFiles.setMinWidth(0);
        btnDownloadedFiles.setMinimumWidth(0);
        btnDownloadedFiles.setMinHeight(0);
        btnDownloadedFiles.setMinimumHeight(0);
        btnDownloadedFiles.setPadding(pad, pad, pad, pad);
        UiStyles.setGlassCardBackground(btnDownloadedFiles);
        UiStyles.setButtonIconOnlyTinted(btnDownloadedFiles, R.drawable.ic_mdi_folder,
                UiStyles.color(ctx, R.color.textPrimary), ctx.getString(R.string.apps_cd_downloads));
        LinearLayout.LayoutParams filesParams = new LinearLayout.LayoutParams(btnSize, btnSize);
        filesParams.setMargins(0, 0, marginEnd, 0);
        container.addView(btnDownloadedFiles, filesParams);
        btnDownloadedFiles.setOnClickListener(v -> showDownloadedFiles());

        Button btnModeToggle = new Button(ctx);
        btnModeToggle.setMinWidth(0);
        btnModeToggle.setMinimumWidth(0);
        btnModeToggle.setMinHeight(0);
        btnModeToggle.setMinimumHeight(0);
        btnModeToggle.setSingleLine(true);
        btnModeToggle.setTextSize(13);
        btnModeToggle.setTextColor(UiStyles.color(ctx, R.color.textPrimary));
        btnModeToggle.setAllCaps(false);
        UiStyles.setGlassCardBackground(btnModeToggle);
        int padH = Math.round(8 * density);
        int padV = Math.round(6 * density);
        btnModeToggle.setPadding(padH, padV, padH, padV);
        updateModeToggleButton(btnModeToggle);
        btnModeToggle.setOnClickListener(v -> {
            isLocalMode = !isLocalMode;
            updateModeToggleButton(btnModeToggle);
            if (isLocalMode) {
                cancelServerAutoRetry();
                loadLocalApps();
            } else {
                loadAppsFromServer();
            }
        });
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, btnSize);
        toggleParams.setMargins(0, 0, marginEnd, 0);
        container.addView(btnModeToggle, toggleParams);

        int destructive = UiStyles.color(ctx, R.color.textDestructive);
        Button btnDeleteSelf = new Button(ctx);
        btnDeleteSelf.setMinWidth(0);
        btnDeleteSelf.setMinimumWidth(0);
        btnDeleteSelf.setMinHeight(0);
        btnDeleteSelf.setMinimumHeight(0);
        btnDeleteSelf.setPadding(pad, pad, pad, pad);
        UiStyles.setGlassCardBackground(btnDeleteSelf);
        UiStyles.setButtonIconOnlyTinted(btnDeleteSelf, R.drawable.ic_mdi_delete,
                destructive, ctx.getString(R.string.apps_delete_self));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(btnSize, btnSize);
        container.addView(btnDeleteSelf, deleteParams);
        btnDeleteSelf.setOnClickListener(v -> confirmDeleteSelf());

        return container;
    }

    private void confirmDeleteSelf() {
        new AlertDialog.Builder(context)
                .setTitle(R.string.apps_delete_self)
                .setMessage(R.string.apps_local_uninstall_explanation)
                .setPositiveButton(R.string.dialog_delete, (d, which) -> deleteSelfApp())
                .setNegativeButton(R.string.wifi_cancel, null)
                .show();
    }

    private void updateModeToggleButton(Button btnModeToggle) {
        if (btnModeToggle == null) return;
        int icon = isLocalMode ? R.drawable.ic_mdi_cellphone : R.drawable.ic_mdi_web;
        btnModeToggle.setText(isLocalMode ? R.string.apps_mode_local : R.string.apps_mode_server);
        btnModeToggle.setSingleLine(true);
        btnModeToggle.setMinWidth(0);
        btnModeToggle.setMinimumWidth(0);
        UiStyles.setButtonStartIconTinted(btnModeToggle, icon,
                UiStyles.color(context, R.color.textPrimary),
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
                    loadingText.setText(R.string.common_loading);
                    loadingText.setTextColor(UiStyles.color(context, R.color.textLoading));
                    loadingText.setTextSize(14);
                    loadingText.setPadding(8, 8, 8, 8);
                    appsListContainer.addView(loadingText);
                });

                List<PackageInfo> user0Apps = getLocalApps();

                handler.post(() -> {
                    appsListContainer.removeAllViews();
                    displayLocalAppsList(user0Apps);
                });

                callback.log("" + user0Apps.size() + " yerel uygulama yüklendi");
            } catch (Exception e) {
                handler.post(() -> {
                    appsListContainer.removeAllViews();
                    TextView errorText = new TextView(context);
                    errorText.setText(context.getString(R.string.common_error_prefix, e.getMessage()));
                    errorText.setTextColor(UiStyles.color(context, R.color.statusErrorBright));
                    errorText.setTextSize(14);
                    errorText.setPadding(8, 8, 8, 8);
                    appsListContainer.addView(errorText);
                });
                callback.log("Yerel uygulama listesi yükleme hatası: " + e.getMessage());
            }
        }).start();
    }

    private List<PackageInfo> getLocalApps() {
        PackageManager pm = context.getPackageManager();
        List<PackageInfo> user0Apps = new ArrayList<>();
        for (PackageInfo pkgInfo : pm.getInstalledPackages(0)) {
            try {
                String packageName = pkgInfo.packageName;
                if (packageName.equals("com.mapcontrol")) continue;

                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                if (callback.isSystemOrPrivApp(appInfo)) continue;

                if (appInfo.uid / 100000 == 0 && pm.getLaunchIntentForPackage(packageName) != null) {
                    user0Apps.add(pkgInfo);
                }
            } catch (Exception ignored) {
            }
        }
        return user0Apps;
    }

    private void displayLocalAppsList(List<PackageInfo> packages) {
        if (appsListContainer == null) return;
        serverListBound = false;
        appRows.clear();

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

                float density = context.getResources().getDisplayMetrics().density;
                LinearLayout appCard = new LinearLayout(context);
                appCard.setOrientation(LinearLayout.HORIZONTAL);
                int padH = Math.round(14 * density);
                int padV = Math.round(12 * density);
                appCard.setPadding(padH, padV, padH, padV);
                appCard.setGravity(android.view.Gravity.CENTER_VERTICAL);
                appCard.setClickable(true);
                appCard.setFocusable(true);

                UiStyles.applySolidRoundedBackgroundDp(appCard,
                        UiStyles.color(context, R.color.surfaceCardInner), 16f);

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    appCard.setElevation(2f);
                }

                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                cardParams.setMargins(0, 0, 0, UiStyles.dimenPx(context, R.dimen.oem_card_margin));
                appCard.setMinimumHeight(Math.round(64 * density));

                android.widget.ImageView appIcon = new android.widget.ImageView(context);
                try {
                    android.graphics.drawable.Drawable icon = pm.getApplicationIcon(finalPackageName);
                    appIcon.setImageDrawable(icon);
                } catch (Exception e) {
                    appIcon.setImageResource(android.R.drawable.sym_def_app_icon);
                }
                appIcon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                        Math.round(44 * density),
                        Math.round(44 * density));
                iconParams.setMargins(0, 0, Math.round(12 * density), 0);
                appCard.addView(appIcon, iconParams);

                LinearLayout infoContainer = new LinearLayout(context);
                infoContainer.setOrientation(LinearLayout.VERTICAL);
                infoContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
                infoContainer.setMinimumWidth(Math.round(90 * density));

                TextView nameText = new TextView(context);
                nameText.setText(finalDisplayName);
                nameText.setTextColor(UiStyles.color(context, R.color.textPrimary));
                nameText.setTextSize(16);
                nameText.setSingleLine(true);
                nameText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                nameText.setTypeface(null, android.graphics.Typeface.NORMAL);
                infoContainer.addView(nameText);

                TextView statusText = new TextView(context);
                statusText.setText(context.getString(R.string.apps_status_installed, finalCurrentVersion));
                statusText.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
                statusText.setTextSize(12);
                statusText.setSingleLine(true);
                statusText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                statusParams.setMargins(0, 4, 0, 0);
                infoContainer.addView(statusText, statusParams);

                LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                infoParams.setMargins(0, 0, Math.round(8 * density), 0);
                appCard.addView(infoContainer, infoParams);

                LinearLayout rightContainer = new LinearLayout(context);
                rightContainer.setOrientation(LinearLayout.HORIZONTAL);
                rightContainer.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);

                Button actionButton = new Button(context);
                actionButton.setText(R.string.apps_action_open);
                actionButton.setTextSize(13);
                actionButton.setSingleLine(true);
                actionButton.setTypeface(null, android.graphics.Typeface.BOLD);
                actionButton.setMinWidth(0);
                actionButton.setMinimumWidth(0);
                int btnPadH = Math.round(10 * density);
                int btnPadV = Math.round(5 * density);
                actionButton.setPadding(btnPadH, btnPadV, btnPadH, btnPadV);
                actionButton.setMinHeight(0);
                actionButton.setMinimumHeight(Math.round(34 * density));

                UiStyles.styleOemButton(actionButton, UiStyles.color(context, R.color.buttonPrimary));
                actionButton.setTextColor(UiStyles.color(context, R.color.textPrimary));
                actionButton.setOnClickListener(v -> {
                    try {
                        Intent launchIntent = pm.getLaunchIntentForPackage(finalPackageName);
                        if (launchIntent != null && activity != null) {
                            activity.startActivity(launchIntent);
                            callback.log(finalDisplayName + " açıldı");
                        } else {
                            Toast.makeText(context, R.string.apps_open_failed, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        callback.log("Uygulama açma hatası: " + e.getMessage());
                        Toast.makeText(context, context.getString(R.string.common_error_prefix, e.getMessage()), Toast.LENGTH_SHORT).show();
                    }
                });
                rightContainer.addView(actionButton);

                Button removeButton = new Button(context);
                removeButton.setText(R.string.apps_action_remove);
                removeButton.setTextSize(12);
                removeButton.setSingleLine(true);
                removeButton.setTypeface(null, android.graphics.Typeface.BOLD);
                removeButton.setTextColor(UiStyles.color(context, R.color.textDestructive));
                removeButton.setBackgroundColor(UiStyles.color(context, R.color.transparent));
                removeButton.setMinWidth(0);
                removeButton.setMinimumWidth(0);
                int remPadH = Math.round(6 * density);
                removeButton.setPadding(remPadH, btnPadV, remPadH, btnPadV);
                removeButton.setMinHeight(0);
                removeButton.setMinimumHeight(Math.round(34 * density));
                removeButton.setOnClickListener(v -> uninstallApp(finalPackageName, finalDisplayName));
                LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                removeParams.setMargins(Math.round(4 * density), 0, 0, 0);
                rightContainer.addView(removeButton, removeParams);

                LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                appCard.addView(rightContainer, rightParams);
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
                emptyIcon.setImageTintList(ColorStateList.valueOf(UiStyles.color(context, R.color.textMuted)));
                emptyCard.addView(emptyIcon);

                TextView emptyText = new TextView(context);
                emptyText.setText(R.string.apps_local_empty);
                emptyText.setTextColor(UiStyles.color(context, R.color.textMuted));
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

    /** Sol menüden Uygulama Yönetimi sekmesi görünür olduğunda. */
    public void onTabVisible() {
        tabVisible = true;
        registerNetworkCallback();
        for (String packageName : new ArrayList<>(downloadProgress.keySet())) {
            refreshRow(packageName);
        }
        if (!isLocalMode && serverLoadNeedsRetry) {
            loadAppsFromServer();
        }
    }

    /** Kurulum oturumu başarısız olunca "Kuruluyor" yazısını yerel dosya veya indirme durumuna çevirir. */
    public void clearInstallingProgress() {
        Runnable clear = () -> {
            ArrayList<String> packages = new ArrayList<>();
            for (Map.Entry<String, DownloadProgress> entry : downloadProgress.entrySet()) {
                if (entry.getValue().phase == DownloadProgress.INSTALLING) {
                    packages.add(entry.getKey());
                }
            }
            for (String packageName : packages) {
                releaseInstallUi(packageName);
            }
        };
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            clear.run();
        } else {
            handler.post(clear);
        }
    }

    public void onTabHidden() {
        tabVisible = false;
        unregisterNetworkCallback();
        cancelServerAutoRetry();
    }

    public void loadAppsFromServer() {
        serverRetryDelayMs = SERVER_RETRY_INITIAL_MS;
        cancelServerAutoRetry();
        loadAppsFromServerInternal(true);
    }

    private void loadAppsFromServerInternal(boolean userInitiated) {
        if (appsListContainer == null || isLocalMode) return;

        final int generation = ++serverLoadGeneration;
        if (userInitiated) {
            serverLoadNeedsRetry = false;
        }

        new Thread(() -> {
            try {
                handler.post(() -> {
                    if (generation != serverLoadGeneration) return;
                    showServerLoadingState(serverLoadNeedsRetry);
                });

                URL url = new URL(SERVER_LIST_URL);
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
                    JSONArray groupsArray = jsonObject.optJSONArray("groups");

                    // Aurora Store, Petal Maps ve HMS Core'u kataloğa dahil et
                    boolean hasPetal = false;
                    boolean hasHms = false;
                    boolean hasAurora = false;
                    for (int i = 0; i < listArray.length(); i++) {
                        String pkg = listArray.getJSONObject(i).optString("packageName", "");
                        if ("com.huawei.maps.app".equals(pkg)) hasPetal = true;
                        if ("com.huawei.hwid".equals(pkg)) hasHms = true;
                        if ("com.aurora.store".equals(pkg)) hasAurora = true;
                    }
                    if (!hasAurora) {
                        JSONObject auroraObj = new JSONObject();
                        auroraObj.put("packageName", "com.aurora.store");
                        auroraObj.put("displayName", "Aurora Store (Play Mağazası)");
                        auroraObj.put("version", "4.6.1");
                        auroraObj.put("downloadUrl", "https://f-droid.org/repo/com.aurora.store_65.apk");
                        listArray.put(auroraObj);
                    }
                    if (!hasPetal) {
                        JSONObject petalObj = new JSONObject();
                        petalObj.put("packageName", "com.huawei.maps.app");
                        petalObj.put("displayName", "Petal Maps");
                        petalObj.put("version", "4.7.0.312");
                        petalObj.put("downloadUrl", "");
                        petalObj.put("group", "navigation");
                        listArray.put(petalObj);
                    }
                    if (!hasHms) {
                        JSONObject hmsObj = new JSONObject();
                        hmsObj.put("packageName", "com.huawei.hwid");
                        hmsObj.put("displayName", "HMS Core (Huawei)");
                        hmsObj.put("version", "6.13.0");
                        hmsObj.put("downloadUrl", "");
                        hmsObj.put("group", "system");
                        listArray.put(hmsObj);
                    }

                    handler.post(() -> {
                        if (generation != serverLoadGeneration) return;
                        serverLoadNeedsRetry = false;
                        cancelServerAutoRetry();
                        lastServerList = listArray;
                        lastServerGroups = groupsArray;
                        appsListContainer.removeAllViews();
                        displayAppsList(listArray);
                    });

                    callback.log("" + listArray.length() + " uygulama yüklendi");
                } else {
                    handler.post(() -> {
                        if (generation != serverLoadGeneration) return;
                        onServerLoadFailed("HTTP " + responseCode);
                    });
                    callback.log("HTTP hatası: " + responseCode);
                }
                connection.disconnect();
            } catch (Exception e) {
                handler.post(() -> {
                    if (generation != serverLoadGeneration) return;
                    onServerLoadFailed(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                });
                callback.log("Uygulama listesi yükleme hatası: " + e.getMessage());
            }
        }).start();
    }

    private void showServerLoadingState(boolean reconnecting) {
        if (serverListBound && hasActiveTransfer()) {
            return;
        }
        serverListBound = false;
        appRows.clear();
        appsListContainer.removeAllViews();
        TextView loadingText = new TextView(context);
        loadingText.setText(reconnecting ? R.string.apps_server_reconnecting : R.string.common_loading);
        loadingText.setTextColor(UiStyles.color(context, reconnecting ? R.color.textSecondaryCool : R.color.textLoading));
        loadingText.setTextSize(14);
        loadingText.setPadding(8, 8, 8, 8);
        appsListContainer.addView(loadingText);
    }

    private void onServerLoadFailed(String detail) {
        serverLoadNeedsRetry = true;
        showServerLoadingState(true);
        callback.log("Sunucu listesi yeniden denenecek: " + detail);
        scheduleServerAutoRetry();
    }

    private void scheduleServerAutoRetry() {
        if (isLocalMode) return;
        handler.removeCallbacks(serverRetryRunnable);
        handler.postDelayed(serverRetryRunnable, serverRetryDelayMs);
        serverRetryDelayMs = Math.min(serverRetryDelayMs * 2, SERVER_RETRY_MAX_MS);
    }

    private void cancelServerAutoRetry() {
        handler.removeCallbacks(serverRetryRunnable);
        serverRetryDelayMs = SERVER_RETRY_INITIAL_MS;
    }

    private void registerNetworkCallback() {
        if (networkCallbackRegistered) return;
        connectivityManager = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        if (networkCallback == null) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    if (!tabVisible || isLocalMode || !serverLoadNeedsRetry) return;
                    if (!NetworkWifiHelper.isWifiConnectedWithInternet(context)) return;
                    handler.post(() -> {
                        serverRetryDelayMs = SERVER_RETRY_INITIAL_MS;
                        cancelServerAutoRetry();
                        loadAppsFromServerInternal(false);
                    });
                }
            };
        }

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback);
            } else {
                connectivityManager.registerNetworkCallback(request, networkCallback);
            }
            networkCallbackRegistered = true;
        } catch (Exception e) {
            callback.log("Ağ dinleyicisi kaydı başarısız: " + e.getMessage());
        }
    }

    private void unregisterNetworkCallback() {
        if (!networkCallbackRegistered || connectivityManager == null || networkCallback == null) return;
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (Exception ignored) {
        }
        networkCallbackRegistered = false;
    }

    private void displayAppsList(JSONArray listArray) {
        if (appsListContainer == null || listArray == null) return;
        serverListBound = true;
        appRows.clear();

        try {
            PackageManager pm = context.getPackageManager();
            rebuildCatalog(listArray);

            Set<String> renderedGroups = new HashSet<>();
            for (int i = 0; i < listArray.length(); i++) {
                JSONObject appObj = listArray.getJSONObject(i);
                String groupId = appObj.optString("group", "").trim();
                if (!groupId.isEmpty()) {
                    if (renderedGroups.add(groupId)) {
                        addGroupSection(groupId, listArray, pm);
                    }
                    continue;
                }
                addServerAppCard(appObj, pm, appsListContainer);
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
                emptyIcon.setImageTintList(ColorStateList.valueOf(UiStyles.color(context, R.color.textMuted)));
                emptyCard.addView(emptyIcon);
                TextView emptyText = new TextView(context);
                emptyText.setText(R.string.apps_remote_empty);
                emptyText.setTextColor(UiStyles.color(context, R.color.textMuted));
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

    private void addGroupSection(String groupId, JSONArray listArray, PackageManager pm) throws JSONException {
        CatalogGroup group = catalogGroups.get(groupId);
        float density = context.getResources().getDisplayMetrics().density;
        int pad = Math.round(12 * density);

        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(pad, pad, pad, Math.round(4 * density));
        UiStyles.applySolidRoundedBackgroundDp(section,
                UiStyles.color(context, R.color.surfaceCardElevated), 16f);

        TextView title = new TextView(context);
        String titleText;
        if (group != null && !group.title.trim().isEmpty()) {
            titleText = group.title.trim();
        } else if ("navigation".equalsIgnoreCase(groupId)) {
            titleText = "Navigasyon";
        } else if ("system".equalsIgnoreCase(groupId)) {
            titleText = "Sistem & Altyapı";
        } else {
            titleText = groupId;
        }
        title.setText(titleText);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        title.setTextSize(15);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        section.addView(title);

        if (group != null && !group.warning.trim().isEmpty()) {
            TextView warn = new TextView(context);
            warn.setText(group.warning.trim());
            warn.setTextColor(UiStyles.color(context, R.color.statusWarning));
            warn.setTextSize(13);
            LinearLayout.LayoutParams warnParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            warnParams.topMargin = Math.round(4 * density);
            warnParams.bottomMargin = Math.round(8 * density);
            section.addView(warn, warnParams);
        }

        for (int i = 0; i < listArray.length(); i++) {
            JSONObject appObj = listArray.getJSONObject(i);
            if (groupId.equals(appObj.optString("group", "").trim())) {
                addServerAppCard(appObj, pm, section);
            }
        }

        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sectionParams.setMargins(0, 0, 0, UiStyles.dimenPx(context, R.dimen.oem_card_margin));
        appsListContainer.addView(section, sectionParams);
    }

    private static boolean isNewerVersion(String serverVersion, String installedVersion) {
        if (serverVersion == null || serverVersion.trim().isEmpty()) return false;
        if (installedVersion == null || installedVersion.trim().isEmpty()) return false;
        if (serverVersion.trim().equals(installedVersion.trim())) return false;
        try {
            String[] sParts = serverVersion.trim().split("[^0-9]+");
            String[] iParts = installedVersion.trim().split("[^0-9]+");
            int len = Math.max(sParts.length, iParts.length);
            for (int i = 0; i < len; i++) {
                int sNum = 0;
                if (i < sParts.length && !sParts[i].isEmpty()) {
                    sNum = Integer.parseInt(sParts[i]);
                }
                int iNum = 0;
                if (i < iParts.length && !iParts[i].isEmpty()) {
                    iNum = Integer.parseInt(iParts[i]);
                }
                if (sNum > iNum) return true;
                if (sNum < iNum) return false;
            }
        } catch (Exception ignore) {
        }
        return false;
    }

    private void addServerAppCard(JSONObject appObj, PackageManager pm, LinearLayout container) throws JSONException {
        String packageName = appObj.optString("packageName", "");
        String displayName = appObj.optString("displayName", packageName);
        String downloadUrl = appObj.optString("downloadUrl", "");
        String version = appObj.optString("version", "");
        String currentVersion = "0";

        boolean isInstalled;
        try {
            PackageInfo info = pm.getPackageInfo(packageName, 0);
            currentVersion = info.versionName != null ? info.versionName : "0";
            callback.log("currentVersion: " + currentVersion + " packageName: " + packageName);
            isInstalled = true;
        } catch (PackageManager.NameNotFoundException e) {
            isInstalled = false;
        }

        DownloadProgress progress = downloadProgress.get(packageName);
        if (progress != null && progress.phase == DownloadProgress.INSTALLING && isInstalled) {
            downloadProgress.remove(packageName);
            progress = null;
        }
        if (progress != null && progress.phase == DownloadProgress.FAILED
                && readyCachedApk(packageName) != null) {
            downloadProgress.remove(packageName);
            progress = null;
        }
        boolean trackProgress = progress != null;
        File readyFile = trackProgress ? null : readyCachedApk(packageName);

        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout appCard = new LinearLayout(context);
        appCard.setOrientation(LinearLayout.HORIZONTAL);
        int padH = Math.round(14 * density);
        int padV = Math.round(12 * density);
        appCard.setPadding(padH, padV, padH, padV);
        appCard.setGravity(android.view.Gravity.CENTER_VERTICAL);
        appCard.setClickable(true);
        appCard.setFocusable(true);

        UiStyles.applySolidRoundedBackgroundDp(appCard,
                UiStyles.color(context, R.color.surfaceCardInner), 16f);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            appCard.setElevation(2f);
        }

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, UiStyles.dimenPx(context, R.dimen.oem_card_margin));
        appCard.setMinimumHeight(Math.round(64 * density));

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
                Math.round(44 * density),
                Math.round(44 * density));
        iconParams.setMargins(0, 0, Math.round(12 * density), 0);
        appCard.addView(appIcon, iconParams);

        LinearLayout infoContainer = new LinearLayout(context);
        infoContainer.setOrientation(LinearLayout.VERTICAL);
        infoContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
        infoContainer.setMinimumWidth(Math.round(90 * density));

        final boolean isLocked = packageName.equals("com.mapcontrol");
        final boolean hasUpdate = !isLocked && isInstalled && !downloadUrl.isEmpty() && isNewerVersion(version, currentVersion);
        final boolean finalIsInstalled = isInstalled;

        TextView nameText = new TextView(context);
        nameText.setText(displayName);
        nameText.setTextColor(UiStyles.color(context, R.color.textPrimary));
        nameText.setTextSize(16);
        nameText.setSingleLine(true);
        nameText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameText.setTypeface(null, android.graphics.Typeface.NORMAL);
        infoContainer.addView(nameText);

        TextView statusText = new TextView(context);
        if (!trackProgress && readyFile != null) {
            statusText.setText(context.getString(R.string.apps_status_local_ready, formatFileSize(readyFile.length())));
            statusText.setTextColor(UiStyles.color(context, R.color.statusSuccess));
        } else if (isInstalled) {
            statusText.setText(context.getString(R.string.apps_status_installed, currentVersion));
            statusText.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
        } else {
            statusText.setText(R.string.apps_status_not_installed);
            statusText.setTextColor(UiStyles.color(context, R.color.textMuted));
        }
        statusText.setTextSize(12);
        statusText.setSingleLine(true);
        statusText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, 4, 0, 0);
        infoContainer.addView(statusText, statusParams);

        String companionNote = missingCompanionLabel(appObj);
        if (companionNote != null) {
            TextView companionText = new TextView(context);
            companionText.setText(companionNote);
            companionText.setTextColor(UiStyles.color(context, R.color.statusWarning));
            companionText.setTextSize(12);
            companionText.setSingleLine(true);
            companionText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams companionParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            companionParams.setMargins(0, 2, 0, 0);
            infoContainer.addView(companionText, companionParams);
        }

        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        infoParams.setMargins(0, 0, Math.round(8 * density), 0);
        appCard.addView(infoContainer, infoParams);

        LinearLayout rightContainer = new LinearLayout(context);
        rightContainer.setOrientation(LinearLayout.HORIZONTAL);
        rightContainer.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);

        Button actionButton = new Button(context);
        actionButton.setTextSize(13);
        actionButton.setSingleLine(true);
        actionButton.setTypeface(null, android.graphics.Typeface.BOLD);
        actionButton.setMinWidth(0);
        actionButton.setMinimumWidth(0);
        int btnPadH = Math.round(10 * density);
        int btnPadV = Math.round(5 * density);
        actionButton.setPadding(btnPadH, btnPadV, btnPadH, btnPadV);
        actionButton.setMinHeight(0);
        actionButton.setMinimumHeight(Math.round(34 * density));

        if (trackProgress) {
            actionButton.setEnabled(false);
        } else if (hasUpdate) {
            actionButton.setText(R.string.apps_action_update);
            actionButton.setTextColor(UiStyles.color(context, R.color.textPrimary));
            UiStyles.styleOemButton(actionButton, UiStyles.color(context, R.color.buttonSuccessBright));
            actionButton.setOnClickListener(v -> {
                callback.log(displayName + " güncelleniyor...");
                requestInstall(packageName, displayName, downloadUrl, null);
            });
        } else if (isInstalled && !isLocked) {
            actionButton.setText(R.string.apps_action_open);
            actionButton.setTextColor(UiStyles.color(context, R.color.textPrimary));
            UiStyles.styleOemButton(actionButton, UiStyles.color(context, R.color.buttonPrimary));
            actionButton.setOnClickListener(v -> launchApp(packageName));
        } else if (isLocked) {
            actionButton.setText(R.string.apps_action_locked);
            actionButton.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
            UiStyles.styleOemButton(actionButton, UiStyles.color(context, R.color.textMuted));
            actionButton.setEnabled(false);
        } else if (readyFile != null) {
            bindLocalInstallButton(actionButton, statusText, displayName, readyFile);
        } else if (downloadUrl.isEmpty()) {
            actionButton.setText(R.string.apps_status_not_installed);
            actionButton.setEnabled(false);
            UiStyles.styleOemButton(actionButton, UiStyles.color(context, R.color.textMuted));
        } else {
            actionButton.setText(R.string.apps_action_install);
            actionButton.setTextColor(UiStyles.color(context, R.color.textPrimary));
            UiStyles.styleOemButton(actionButton, UiStyles.color(context, R.color.buttonSuccessBright));
            actionButton.setOnClickListener(v -> requestInstall(packageName, displayName, downloadUrl, null));
        }

        appRows.put(packageName, new AppRow(actionButton, statusText, displayName));
        if (trackProgress) {
            refreshRow(packageName);
        }

        rightContainer.addView(actionButton);

        if (finalIsInstalled && !isLocked) {
            Button removeButton = new Button(context);
            removeButton.setText(R.string.apps_action_remove);
            removeButton.setTextSize(12);
            removeButton.setSingleLine(true);
            removeButton.setTypeface(null, android.graphics.Typeface.BOLD);
            removeButton.setTextColor(UiStyles.color(context, R.color.textDestructive));
            removeButton.setBackgroundColor(UiStyles.color(context, R.color.transparent));
            removeButton.setMinWidth(0);
            removeButton.setMinimumWidth(0);
            int remPadH = Math.round(6 * density);
            removeButton.setPadding(remPadH, btnPadV, remPadH, btnPadV);
            removeButton.setMinHeight(0);
            removeButton.setMinimumHeight(Math.round(34 * density));
            removeButton.setOnClickListener(v -> uninstallApp(packageName, displayName));
            LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            removeParams.setMargins(Math.round(4 * density), 0, 0, 0);
            rightContainer.addView(removeButton, removeParams);
        }

        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        appCard.addView(rightContainer, rightParams);
        container.addView(appCard, cardParams);
    }

    private void rebuildCatalog(JSONArray listArray) {
        catalogApps.clear();
        catalogGroups.clear();
        if (lastServerGroups != null) {
            for (int i = 0; i < lastServerGroups.length(); i++) {
                JSONObject obj = lastServerGroups.optJSONObject(i);
                if (obj == null) continue;
                String id = obj.optString("id", "").trim();
                if (id.isEmpty()) continue;
                catalogGroups.put(id, new CatalogGroup(obj.optString("title", ""), obj.optString("warning", "")));
            }
        }
        for (int i = 0; i < listArray.length(); i++) {
            JSONObject appObj = listArray.optJSONObject(i);
            if (appObj == null) continue;
            CatalogApp app = CatalogApp.from(appObj);
            if (!app.packageName.isEmpty()) {
                catalogApps.put(app.packageName, app);
            }
        }
    }

    private String missingCompanionLabel(JSONObject appObj) {
        JSONArray requires = appObj.optJSONArray("requires");
        if (requires == null || requires.length() == 0) return null;
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < requires.length(); i++) {
            String req = requires.optString(i, "").trim();
            if (req.isEmpty() || isPackageInstalled(req)) continue;
            if (names.length() > 0) names.append(", ");
            CatalogApp app = catalogApps.get(req);
            names.append(app != null && app.displayName != null && !app.displayName.trim().isEmpty()
                    ? app.displayName.trim() : req);
        }
        if (names.length() == 0) return null;
        return context.getString(R.string.apps_companion_required, names.toString());
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private List<CatalogApp> missingRequires(String packageName) {
        List<CatalogApp> missing = new ArrayList<>();
        collectMissingRequires(packageName, missing, new HashSet<>());
        return missing;
    }

    private void collectMissingRequires(String packageName, List<CatalogApp> missing, Set<String> seen) {
        CatalogApp app = catalogApps.get(packageName);
        if (app == null) return;
        for (String req : app.requires) {
            if (!seen.add(req)) continue;
            collectMissingRequires(req, missing, seen);
            if (isPackageInstalled(req)) continue;
            CatalogApp required = catalogApps.get(req);
            if (required != null) missing.add(required);
        }
    }

    private void requestInstall(String packageName, String displayName, String downloadUrl, File readyFile) {
        List<CatalogApp> missing = missingRequires(packageName);
        if (missing.isEmpty()) {
            startSingleInstall(packageName, displayName, downloadUrl, readyFile);
            return;
        }
        showCompanionDialog(packageName, displayName, downloadUrl, readyFile, missing);
    }

    private void showCompanionDialog(String packageName, String displayName, String downloadUrl,
                                     File readyFile, List<CatalogApp> missing) {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.apps_companion_dialog_title)
                .setMessage(companionWarning(packageName, displayName, missing))
                .setPositiveButton(R.string.apps_companion_install_all, (d, which) ->
                        startCompanionInstall(packageName, displayName, downloadUrl, readyFile, missing))
                .setNeutralButton(R.string.apps_companion_install_one, (d, which) ->
                        startSingleInstall(packageName, displayName, downloadUrl, readyFile))
                .setNegativeButton(R.string.wifi_cancel, null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(UiStyles.color(context, R.color.statusSuccess));
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(UiStyles.color(context, R.color.textPrimary));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(UiStyles.color(context, R.color.statusNeutralGray));
    }

    private String companionWarning(String packageName, String displayName, List<CatalogApp> missing) {
        CatalogApp selected = catalogApps.get(packageName);
        if (selected != null && selected.groupId != null && !selected.groupId.isEmpty()) {
            CatalogGroup group = catalogGroups.get(selected.groupId);
            if (group != null && !group.warning.trim().isEmpty()) {
                return group.warning.trim();
            }
        }
        StringBuilder names = new StringBuilder();
        for (CatalogApp app : missing) {
            if (names.length() > 0) names.append(", ");
            names.append(app.displayName != null && !app.displayName.trim().isEmpty()
                    ? app.displayName.trim() : app.packageName);
        }
        return context.getString(R.string.apps_companion_fallback_warning, displayName, names.toString());
    }

    private void startSingleInstall(String packageName, String displayName, String downloadUrl, File readyFile) {
        if (readyFile != null) {
            callback.log(displayName + " kuruluyor (indirilmiş dosyadan)...");
            installApkFile(readyFile);
            return;
        }
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            callback.log("İndirme adresi yok: " + packageName);
            return;
        }
        downloadAndInstallApp(packageName, displayName, downloadUrl);
    }

    private void startCompanionInstall(String packageName, String displayName, String downloadUrl,
                                       File readyFile, List<CatalogApp> missing) {
        List<InstallJob> jobs = new ArrayList<>();
        for (CatalogApp req : missing) {
            File cached = readyCachedApk(req.packageName);
            if (cached == null && (req.downloadUrl == null || req.downloadUrl.isEmpty())) {
                callback.log("Bağımlılık atlandı, adres yok: " + req.packageName);
                continue;
            }
            jobs.add(new InstallJob(req.packageName, req.displayName, req.downloadUrl, cached));
        }
        jobs.add(new InstallJob(packageName, displayName, downloadUrl, readyFile));
        beginInstallSequence(jobs);
    }

    private void beginInstallSequence(List<InstallJob> jobs) {
        installQueueGeneration++;
        if (installQueueGeneration == Integer.MAX_VALUE) installQueueGeneration = 1;
        installQueue.clear();
        installQueue.addAll(jobs);
        advanceInstallQueue(installQueueGeneration);
    }

    private void advanceInstallQueue(int generation) {
        if (generation != installQueueGeneration) return;
        InstallJob next = installQueue.pollFirst();
        if (next == null) return;
        int continueGeneration = installQueue.isEmpty() ? 0 : generation;
        if (next.readyFile != null && next.readyFile.isFile()) {
            installApkFile(next.readyFile, continueGeneration);
            return;
        }
        if (next.downloadUrl == null || next.downloadUrl.isEmpty()) {
            callback.log("İndirme adresi yok: " + next.packageName);
            advanceInstallQueue(generation);
            return;
        }
        downloadAndInstallApp(next.packageName, next.displayName, next.downloadUrl, continueGeneration);
    }

    private void abandonInstallQueue(int generation) {
        if (generation != 0 && generation == installQueueGeneration) {
            installQueue.clear();
            installQueueGeneration++;
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
                try {
                    Intent detailsIntent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    detailsIntent.setData(Uri.parse("package:" + packageName));
                    detailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(detailsIntent);
                    callback.log("Uygulama ayarları açıldı: " + packageName);
                } catch (Exception ex) {
                    callback.log("Uygulama açılamadı (launch intent bulunamadı): " + packageName);
                }
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
                .setTitle(R.string.apps_dialog_uninstall_title)
                .setMessage(context.getString(R.string.apps_dialog_uninstall_message, displayName))
                .setPositiveButton(R.string.common_yes, (d, which) -> {
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
                        Toast.makeText(context, context.getString(R.string.common_error_prefix, e.getMessage()), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.common_no, null)
                .create();

        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(UiStyles.color(context, R.color.statusErrorBright));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(17);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTypeface(null, android.graphics.Typeface.BOLD);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(UiStyles.color(context, R.color.statusNeutralGray));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextSize(17);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTypeface(null, android.graphics.Typeface.BOLD);
    }

    private File cacheDownloadDir() {
        return new File(context.getCacheDir(), "downloads");
    }

    private File cachedApkFile(String packageName) {
        return new File(cacheDownloadDir(), packageName + ".apk");
    }

    /** Tamamlanmış önbellek veya yerel indirme APK'si. İndirme sırasındaki .part dosyası sayılmaz. */
    private File readyCachedApk(String packageName) {
        File file = cachedApkFile(packageName);
        if (file.isFile() && file.length() > 0) return file;
        File pubFile = new File(android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS), packageName + ".apk");
        if (pubFile.isFile() && pubFile.length() > 0) return pubFile;
        File pubDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS);
        if (pubDir != null && pubDir.exists()) {
            File[] list = pubDir.listFiles();
            if (list != null) {
                for (File f : list) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".apk")) {
                        String pkg = packageNameFromApkFile(f);
                        if (packageName.equals(pkg)) return f;
                    }
                }
            }
        }
        return null;
    }

    private boolean hasActiveTransfer() {
        for (DownloadProgress progress : downloadProgress.values()) {
            if (progress.phase == DownloadProgress.DOWNLOADING
                    || progress.phase == DownloadProgress.INSTALLING) {
                return true;
            }
        }
        return false;
    }

    private void bindLocalInstallButton(Button button, TextView status, String displayName, File readyFile) {
        status.setText(context.getString(R.string.apps_status_local_ready, formatFileSize(readyFile.length())));
        status.setTextColor(UiStyles.color(context, R.color.statusSuccess));
        button.setEnabled(true);
        button.setText(R.string.apps_action_install_local);
        button.setTextColor(UiStyles.color(context, R.color.textPrimary));
        UiStyles.styleOemButton(button, UiStyles.color(context, R.color.buttonSuccessBright));
        UiStyles.clearButtonCompoundDrawables(button);
        button.setOnClickListener(v -> {
            String pkg = packageNameFromApkFile(readyFile);
            if (pkg == null) {
                callback.log(displayName + " kuruluyor (indirilmiş dosyadan)...");
                installApkFile(readyFile);
                return;
            }
            requestInstall(pkg, displayName, null, readyFile);
        });
    }

    private CharSequence progressLabel(DownloadProgress progress) {
        if (progress.percent >= 0) {
            return context.getString(R.string.apps_downloading_percent, progress.percent);
        }
        if (progress.bytes > 0) {
            return context.getString(R.string.apps_downloading_size, formatFileSize(progress.bytes));
        }
        return context.getString(R.string.apps_downloading);
    }

    private void refreshRow(String packageName) {
        AppRow row = appRows.get(packageName);
        DownloadProgress progress = downloadProgress.get(packageName);
        if (row == null || progress == null) return;

        Button button = row.button;
        TextView status = row.status;
        int iconGap = UiStyles.dimenPx(context, R.dimen.spacing_small);
        int iconColor = UiStyles.color(context, R.color.textPrimary);

        if (progress.phase == DownloadProgress.DOWNLOADING) {
            CharSequence label = progressLabel(progress);
            button.setEnabled(false);
            button.setText(label);
            button.setTextColor(iconColor);
            status.setText(label);
            status.setTextColor(UiStyles.color(context, R.color.statusInfo));
            UiStyles.styleOemButton(button, UiStyles.color(context, R.color.buttonSuccessBright));
            UiStyles.setButtonStartIconTinted(button, R.drawable.ic_mdi_timer_sand, iconColor, iconGap);
        } else if (progress.phase == DownloadProgress.INSTALLING) {
            button.setEnabled(false);
            button.setText(R.string.apps_installing);
            button.setTextColor(iconColor);
            status.setText(R.string.apps_installing);
            status.setTextColor(UiStyles.color(context, R.color.statusInfo));
            UiStyles.styleOemButton(button, UiStyles.color(context, R.color.buttonSuccessBright));
            UiStyles.setButtonStartIconTinted(button, R.drawable.ic_mdi_package_variant, iconColor, iconGap);
        } else if (progress.phase == DownloadProgress.FAILED) {
            button.setEnabled(true);
            button.setText(R.string.apps_manual_download);
            button.setTextColor(iconColor);
            status.setText(R.string.apps_status_download_failed);
            status.setTextColor(UiStyles.color(context, R.color.statusError));
            UiStyles.styleOemButton(button, UiStyles.color(context, R.color.buttonPrimary));
            UiStyles.setButtonStartIconTinted(button, R.drawable.ic_mdi_download, iconColor, iconGap);
            String retryName = progress.displayName != null ? progress.displayName : row.displayName;
            String retryUrl = progress.downloadUrl;
            button.setOnClickListener(v -> requestInstall(packageName, retryName, retryUrl, null));
        }
    }

    private void postDownloadUi(String packageName, int generation, int percent, long bytes) {
        handler.post(() -> {
            DownloadProgress progress = downloadProgress.get(packageName);
            if (progress == null || progress.generation != generation) return;
            if (progress.phase != DownloadProgress.DOWNLOADING) return;
            progress.percent = percent;
            progress.bytes = bytes;
            refreshRow(packageName);
        });
    }

    private void onDownloadFailedUi(String packageName, int generation) {
        DownloadProgress progress = downloadProgress.get(packageName);
        if (progress == null || progress.generation != generation) return;
        File ready = readyCachedApk(packageName);
        AppRow row = appRows.get(packageName);
        if (ready != null && row != null) {
            downloadProgress.remove(packageName);
            bindLocalInstallButton(row.button, row.status, row.displayName, ready);
            return;
        }
        progress.phase = DownloadProgress.FAILED;
        refreshRow(packageName);
    }

    private void markInstalling(String packageName) {
        if (!appRows.containsKey(packageName) && !downloadProgress.containsKey(packageName)) return;
        DownloadProgress progress = downloadProgress.get(packageName);
        if (progress == null) {
            progress = new DownloadProgress();
            progress.displayName = packageName;
            downloadProgress.put(packageName, progress);
        }
        progress.phase = DownloadProgress.INSTALLING;
        refreshRow(packageName);
    }

    private void releaseInstallUi(String packageName) {
        DownloadProgress progress = downloadProgress.get(packageName);
        if (progress == null || progress.phase != DownloadProgress.INSTALLING) return;
        File ready = readyCachedApk(packageName);
        AppRow row = appRows.get(packageName);
        if (ready != null && row != null) {
            downloadProgress.remove(packageName);
            bindLocalInstallButton(row.button, row.status, row.displayName, ready);
            return;
        }
        if (progress.downloadUrl != null) {
            progress.phase = DownloadProgress.FAILED;
            refreshRow(packageName);
            return;
        }
        downloadProgress.remove(packageName);
    }

    private void publishCompletedApk(File downloadDir, String packageName, File partial) throws Exception {
        File dest = new File(downloadDir, packageName + ".apk");
        File backup = new File(downloadDir, packageName + ".apk.bak");
        if (backup.exists()) backup.delete();
        if (dest.exists() && !dest.renameTo(backup)) {
            throw new Exception("Eski APK yedeklenemedi");
        }
        if (!partial.renameTo(dest)) {
            if (backup.exists()) backup.renameTo(dest);
            throw new Exception("APK tamamlanamadı");
        }
        if (!dest.exists() || dest.length() == 0) {
            if (dest.exists()) dest.delete();
            if (backup.exists()) backup.renameTo(dest);
            throw new Exception("APK dosyası boş veya oluşturulamadı");
        }
        if (backup.exists()) backup.delete();
    }

    private void restoreBackupIfNeeded(String packageName) {
        File dest = cachedApkFile(packageName);
        File backup = new File(cacheDownloadDir(), packageName + ".apk.bak");
        if (!dest.exists() && backup.exists()) {
            backup.renameTo(dest);
        } else if (dest.exists() && backup.exists()) {
            backup.delete();
        }
    }

    private void downloadAndInstallApp(String packageName, String displayName, String downloadUrl) {
        downloadAndInstallApp(packageName, displayName, downloadUrl, 0);
    }

    private void downloadAndInstallApp(String packageName, String displayName, String downloadUrl, int queueGeneration) {
        DownloadProgress progress = downloadProgress.get(packageName);
        if (progress == null) {
            progress = new DownloadProgress();
            downloadProgress.put(packageName, progress);
        }
        progress.generation = ++downloadGenerationSeq;
        progress.phase = DownloadProgress.DOWNLOADING;
        progress.percent = -1;
        progress.bytes = 0;
        progress.displayName = displayName;
        progress.downloadUrl = downloadUrl;
        final int generation = progress.generation;
        refreshRow(packageName);

        new Thread(() -> {
            File partial = null;
            InputStream inputStream = null;
            FileOutputStream outputStream = null;
            HttpURLConnection connection = null;
            try {
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
                File downloadDir = cacheDownloadDir();
                if (!downloadDir.exists()) downloadDir.mkdirs();
                partial = new File(downloadDir, packageName + ".apk.part");
                if (partial.exists()) partial.delete();

                outputStream = new FileOutputStream(partial);
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
                            postDownloadUi(packageName, generation, percent, total);
                        }
                    } else if (currentTime - lastUiBytesUpdate >= 400L) {
                        lastUiBytesUpdate = currentTime;
                        postDownloadUi(packageName, generation, -1, total);
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

                publishCompletedApk(downloadDir, packageName, partial);
                partial = null;
                File apkFile = cachedApkFile(packageName);
                callback.log("[INFO] İndirilen dosya boyutu: " + apkFile.length() + " bytes");

                callback.log(displayName + " indirildi ve kuruluyor...");
                handler.post(() -> {
                    DownloadProgress current = downloadProgress.get(packageName);
                    if (current == null || current.generation != generation) return;
                    Toast.makeText(context, context.getString(R.string.apps_installing_named, displayName), Toast.LENGTH_SHORT).show();
                    installApkFile(apkFile, queueGeneration);
                });
            } catch (Exception e) {
                if (partial != null && partial.exists()) partial.delete();
                restoreBackupIfNeeded(packageName);
                callback.log("[ERROR] APK indirme/yükleme hatası: " + e.getMessage());
                handler.post(() -> {
                    onDownloadFailedUi(packageName, generation);
                    abandonInstallQueue(queueGeneration);
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

            List<File> apkFileList = new ArrayList<>();
            java.util.Set<String> seenFileNames = new java.util.HashSet<>();
            File extDownloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            File cacheDownloadDir = new File(context.getCacheDir(), "downloads");

            File[] extFiles = extDownloadDir != null && extDownloadDir.exists() ? extDownloadDir.listFiles() : null;
            if (extFiles != null) {
                for (File f : extFiles) {
                    if (f.isFile() && f.getName().endsWith(".apk") && seenFileNames.add(f.getName())) {
                        apkFileList.add(f);
                    }
                }
            }
            File[] cacheFiles = cacheDownloadDir.exists() ? cacheDownloadDir.listFiles() : null;
            if (cacheFiles != null) {
                for (File f : cacheFiles) {
                    if (f.isFile() && f.getName().endsWith(".apk") && seenFileNames.add(f.getName())) {
                        apkFileList.add(f);
                    }
                }
            }
            int fileCount = apkFileList.size();
            String folderName = isLocalMode ? context.getString(R.string.apps_folder_downloads) : context.getString(R.string.apps_folder_cache);

            LinearLayout dialogLayout = new LinearLayout(context);
            dialogLayout.setOrientation(LinearLayout.VERTICAL);
            dialogLayout.setPadding(0, 0, 0, 0);
            dialogLayout.setBackgroundColor(UiStyles.color(context, R.color.backgroundPage));

            LinearLayout headerLayout = new LinearLayout(context);
            headerLayout.setOrientation(LinearLayout.HORIZONTAL);
            headerLayout.setBackgroundColor(UiStyles.color(context, R.color.surfaceCardInner));
            headerLayout.setPadding(24, 20, 24, 20);
            headerLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

            LinearLayout titleContainer = new LinearLayout(context);
            titleContainer.setOrientation(LinearLayout.VERTICAL);

            TextView titleText = new TextView(context);
            titleText.setText(R.string.apps_downloads_title);
            titleText.setTextColor(UiStyles.color(context, R.color.textPrimary));
            titleText.setTextSize(20);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            titleContainer.addView(titleText);

            TextView subtitleText = new TextView(context);
            subtitleText.setText(context.getString(R.string.apps_downloads_subtitle, fileCount, folderName));
            subtitleText.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
            subtitleText.setTextSize(13);
            subtitleText.setPadding(0, 4, 0, 0);
            titleContainer.addView(subtitleText);

            LinearLayout.LayoutParams titleContainerParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            headerLayout.addView(titleContainer, titleContainerParams);

            if (fileCount > 0) {
                Button deleteAllButton = new Button(context);
                deleteAllButton.setText(R.string.apps_delete_all);
                deleteAllButton.setTextSize(13);
                deleteAllButton.setTextColor(UiStyles.color(context, R.color.textDestructive));
                deleteAllButton.setBackgroundColor(UiStyles.color(context, R.color.transparent));
                deleteAllButton.setPadding(12, 8, 12, 8);
                UiStyles.setButtonStartIconTinted(deleteAllButton, R.drawable.ic_mdi_delete,
                        UiStyles.color(context, R.color.textDestructive),
                        UiStyles.dimenPx(context, R.dimen.spacing_small));
                deleteAllButton.setOnClickListener(v -> {
                    AlertDialog confirmDialog = new AlertDialog.Builder(context)
                            .setTitle(R.string.apps_dialog_delete_all_title)
                            .setMessage(R.string.apps_dialog_delete_all_message)
                            .setPositiveButton(R.string.dialog_delete, (d, which) -> {
                                for (File f : apkFileList) {
                                    if (f.exists()) f.delete();
                                }
                                showDownloadedFilesDialog();
                            })
                            .setNegativeButton(R.string.wifi_cancel, null)
                            .create();
                    confirmDialog.show();
                });
                headerLayout.addView(deleteAllButton);
            }

            dialogLayout.addView(headerLayout);

            ScrollView scrollView = new ScrollView(context);
            scrollView.setBackgroundColor(UiStyles.color(context, R.color.backgroundPage));
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
                        UiStyles.color(context, R.color.textSecondaryCool)));
                emptyWrap.addView(emptyIco);
                TextView emptyText = new TextView(context);
                emptyText.setText(R.string.apps_downloads_empty);
                emptyText.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
                emptyText.setTextSize(16);
                emptyText.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams etP = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                etP.topMargin = Math.round(12 * context.getResources().getDisplayMetrics().density);
                emptyWrap.addView(emptyText, etP);
                filesLayout.addView(emptyWrap);
            } else {
                for (File file : apkFileList) {
                    if (!file.isFile() || !file.getName().endsWith(".apk")) continue;

                    LinearLayout fileCard = new LinearLayout(context);
                    fileCard.setOrientation(LinearLayout.HORIZONTAL);
                    fileCard.setPadding(UiStyles.dimenPx(context, R.dimen.spacing_medium),
                            UiStyles.dimenPx(context, R.dimen.spacing_medium),
                            UiStyles.dimenPx(context, R.dimen.spacing_medium),
                            UiStyles.dimenPx(context, R.dimen.spacing_medium));
                    UiStyles.applySolidRoundedBackgroundDp(fileCard,
                            UiStyles.color(context, R.color.surfaceCardElevated), 16f);
                    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    cardParams.setMargins(0, 0, 0, UiStyles.dimenPx(context, R.dimen.oem_card_margin));

                    LinearLayout iconBox = new LinearLayout(context);
                    iconBox.setOrientation(LinearLayout.VERTICAL);
                    iconBox.setGravity(android.view.Gravity.CENTER);
                    UiStyles.applySolidRoundedBackgroundDp(iconBox,
                            UiStyles.color(context, R.color.buttonPrimary), 12f);
                    iconBox.setPadding(16, 16, 16, 16);

                    AppCompatImageView iconApk = new AppCompatImageView(context);
                    iconApk.setImageResource(R.drawable.ic_mdi_package_variant);
                    iconApk.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    int innerIcon = Math.round(28 * context.getResources().getDisplayMetrics().density);
                    iconApk.setLayoutParams(new LinearLayout.LayoutParams(innerIcon, innerIcon));
                    iconApk.setImageTintList(ColorStateList.valueOf(
                            UiStyles.color(context, R.color.textPrimary)));
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
                    nameText.setTextColor(UiStyles.color(context, R.color.textPrimary));
                    nameText.setTextSize(14);
                    nameText.setTypeface(null, android.graphics.Typeface.BOLD);
                    nameText.setMaxLines(1);
                    nameText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    infoLayout.addView(nameText);

                    TextView sizeText = new TextView(context);
                    sizeText.setText(context.getString(R.string.apps_file_size, sizeStr));
                    sizeText.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
                    sizeText.setTextSize(12);
                    sizeText.setPadding(0, 2, 0, 0);
                    infoLayout.addView(sizeText);

                    fileCard.addView(infoLayout, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                    LinearLayout buttonsContainer = new LinearLayout(context);
                    buttonsContainer.setOrientation(LinearLayout.HORIZONTAL);
                    buttonsContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);

                    Button installButton = new Button(context);
                    installButton.setText(R.string.apps_action_install);
                    installButton.setTextColor(UiStyles.color(context, R.color.textPrimary));
                    UiStyles.styleOemButton(installButton, UiStyles.color(context, R.color.buttonPrimary));
                    installButton.setTextSize(13);
                    installButton.setTypeface(null, android.graphics.Typeface.BOLD);
                    installButton.setPadding(16, 12, 16, 12);
                    UiStyles.setButtonStartIconTinted(installButton, R.drawable.ic_mdi_package_variant,
                            UiStyles.color(context, R.color.textPrimary),
                            UiStyles.dimenPx(context, R.dimen.spacing_small));
                    buttonsContainer.addView(installButton);

                    Button deleteButton = new Button(context);
                    deleteButton.setTextColor(UiStyles.color(context, R.color.textPrimary));
                    UiStyles.styleOemButton(deleteButton, UiStyles.color(context, R.color.buttonDestructiveBg));
                    deleteButton.setTextSize(18);
                    deleteButton.setPadding(16, 12, 16, 12);
                    UiStyles.setButtonIconOnlyTinted(deleteButton, R.drawable.ic_mdi_delete,
                            UiStyles.color(context, R.color.textPrimary), context.getString(R.string.apps_cd_delete_file));
                    buttonsContainer.addView(deleteButton);

                    fileCard.addView(buttonsContainer);
                    filesLayout.addView(fileCard, cardParams);

                    File finalFile = file;
                    installButton.setOnClickListener(v -> installApkFile(finalFile));
                    deleteButton.setOnClickListener(v -> {
                        AlertDialog confirmDialog = new AlertDialog.Builder(context)
                                .setTitle(R.string.apps_dialog_delete_file_title)
                                .setMessage(context.getString(R.string.apps_dialog_delete_file_message, finalFile.getName()))
                                .setPositiveButton(R.string.common_yes, (d, which) -> {
                                    if (finalFile.delete()) {
                                        Toast.makeText(context, R.string.apps_file_deleted, Toast.LENGTH_SHORT).show();
                                        callback.log("Dosya silindi: " + finalFile.getName());
                                        showDownloadedFilesDialog();
                                    } else {
                                        Toast.makeText(context, R.string.apps_file_delete_failed, Toast.LENGTH_SHORT).show();
                                        callback.log("Dosya silinemedi: " + finalFile.getName());
                                    }
                                })
                                .setNegativeButton(R.string.common_no, null)
                                .create();
                        confirmDialog.show();
                    });
                }
            }

            scrollView.addView(filesLayout);
            dialogLayout.addView(scrollView);

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setView(dialogLayout)
                    .setPositiveButton(R.string.dialog_close, null)
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
            Toast.makeText(context, context.getString(R.string.common_error_prefix, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.2f KB", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0));
    }

    public void installApkFile(String fileName) {
        try {
            File downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            File apkFile = new File(downloadDir, fileName);
            installApkFile(apkFile);
        } catch (Exception e) {
            callback.log("APK kurulum hatası: " + e.getMessage());
            handler.post(() -> Toast.makeText(context, context.getString(R.string.apps_apk_install_error, e.getMessage()), Toast.LENGTH_SHORT).show());
        }
    }

    public void installApkFile(File apkFile) {
        installApkFile(apkFile, 0);
    }

    private void installApkFile(File apkFile, int queueGeneration) {
        if (apkFile == null || !apkFile.exists()) {
            handler.post(() -> {
                Toast.makeText(context, R.string.apps_apk_not_found, Toast.LENGTH_SHORT).show();
                abandonInstallQueue(queueGeneration);
            });
            callback.log("APK dosyası bulunamadı");
            return;
        }

        final String trackedPackage = packageNameFromApkFile(apkFile);
        if (trackedPackage != null) {
            Runnable mark = () -> markInstalling(trackedPackage);
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                mark.run();
            } else {
                handler.post(mark);
            }
        }

        new Thread(() -> {
            handler.post(() -> Toast.makeText(context, R.string.apps_install_starting, Toast.LENGTH_SHORT).show());
            callback.log("Android PackageInstaller ile kullanıcı onaylı kurulum hazırlanıyor");
            handler.post(() -> abandonInstallQueue(queueGeneration));
            if (activity == null) {
                handler.post(() -> {
                    Toast.makeText(context,
                            context.getString(R.string.apps_install_start_failed, "Activity"), Toast.LENGTH_LONG).show();
                    if (trackedPackage != null) releaseInstallUi(trackedPackage);
                });
                callback.log("[ERROR] APK kurulumu için Activity yok");
                return;
            }

            try {
                callback.log("PackageInstaller session başlatılıyor: " + apkFile.getAbsolutePath());
                ApkSessionInstaller.install(activity, apkFile, null);
                handler.post(() -> {
                    callback.log("APK session commit edildi: " + apkFile.getName());
                    Toast.makeText(context, R.string.apps_install_started, Toast.LENGTH_SHORT).show();
                });
                handler.postDelayed(this::loadAppsFromServer, 2000);
            } catch (Exception e) {
                String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                handler.post(() -> {
                    Toast.makeText(context,
                            context.getString(R.string.apps_install_start_failed, detail), Toast.LENGTH_LONG).show();
                    if (trackedPackage != null) releaseInstallUi(trackedPackage);
                });
                callback.log("[ERROR] APK session kurulum hatası: " + detail);
            }
        }).start();
    }

    private static String packageNameFromApkFile(File apkFile) {
        String name = apkFile.getName();
        if (name.endsWith(".apk")) {
            return name.substring(0, name.length() - 4);
        }
        return null;
    }

    /** Kept for callers; removal is limited to this application. */
    public void performReset() {
        handler.post(this::confirmDeleteSelf);
    }

    @SuppressWarnings("deprecation")
    private void deleteSelfApp() {
        handler.post(() -> {
            try {
                String selfPackage = context.getPackageName();
                DevicePolicyManager dpm = (DevicePolicyManager)
                        context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                com.mapcontrol.util.SelfUninstallGate.prepare(dpm == null ? null :
                        new com.mapcontrol.util.SelfUninstallGate.OwnerAccess() {
                            public boolean isOwner() { return dpm.isDeviceOwnerApp(selfPackage); }
                            public void clearOwner() { dpm.clearDeviceOwnerApp(selfPackage); }
                        });
                Intent intent = new Intent(Intent.ACTION_DELETE);
                intent.setData(android.net.Uri.parse("package:" + selfPackage));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                callback.log("Sistem kaldırma ekranı açıldı: " + selfPackage
                        + "; kaldırma henüz doğrulanmadı. İptal edilirse başka komut çalıştırılmaz.");
            } catch (Exception e) {
                callback.log("Kaldırma durduruldu: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                Toast.makeText(context, R.string.apps_local_uninstall_blocked, Toast.LENGTH_LONG).show();
            }
        });
    }
}
