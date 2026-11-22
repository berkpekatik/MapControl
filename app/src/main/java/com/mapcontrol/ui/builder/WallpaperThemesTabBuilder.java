package com.mapcontrol.ui.builder;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import androidx.core.graphics.ColorUtils;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.wallpaper.OemWallpaperManager;
import com.mapcontrol.wallpaper.WallpaperImageHelper;
import com.mapcontrol.wallpaper.WallpaperThemeCatalog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * "Arka Plan" sekmesi:
 * Yalnızca kullanıcının yüklediği veya {@code SVWallpaper} klasörüne eklediği cansız
 * görselleri listeler, 4K dahil farklı ekran çözünürlüklerine göre uyarlar, uygular ve siler.
 */
public class WallpaperThemesTabBuilder {

    public interface WallpaperThemesCallback {
        void log(@NonNull String message);
    }

    private final AppCompatActivity activity;
    private final ActivityResultLauncher<Intent> imagePicker;
    private final WallpaperThemesCallback callback;
    private final OemWallpaperManager wallpaperManager;

    private List<WallpaperThemeCatalog.WallpaperItem> items = new ArrayList<>();
    @Nullable
    private WallpaperThemeCatalog.WallpaperItem selectedItem;
    @Nullable
    private int downloadingPercent = 0;
    @Nullable

    private TextView tvActiveStatus;
    private TextView tvFolderPath;
    private TextView tvLauncherStatus;
    private TextView tvResolutionBadge;
    private TextView tvActionStatus;
    private TextView tvCatalogTitle;

    private ImageView previewImageView;
    private TextView previewEmptyView;
    private TextView tvPreviewName;
    private TextView tvPreviewMeta;
    private Button btnApplySelected;
    private Button btnDeleteSelected;
    private Button btnUploadImage;
    private Button btnRefreshFolder;
    private Button btnRestoreOem;
    private LinearLayout galleryContainer;
    private LinearLayout bundledGalleryContainer;
    private TextView tvBundledTitle;

    private boolean isLandscape;
    private float currentDensity;
    private int catalogLoadGen = 0;

    public WallpaperThemesTabBuilder(
            @NonNull AppCompatActivity activity,
            @NonNull ActivityResultLauncher<Intent> imagePicker,
            @NonNull WallpaperThemesCallback callback) {
        this.activity = activity;
        this.imagePicker = imagePicker;
        this.callback = callback;
        this.wallpaperManager = OemWallpaperManager.getInstance(activity);
    }

    @NonNull
    public ScrollView build() {
        currentDensity = activity.getResources().getDisplayMetrics().density;
        Configuration cfg = activity.getResources().getConfiguration();
        isLandscape = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
                || cfg.screenWidthDp >= 680;

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(true);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int hPad = dp(isLandscape ? 20 : 14, currentDensity);
        int vPad = dp(16, currentDensity);
        root.setPadding(hPad, vPad, hPad, dp(28, currentDensity));

        root.addView(buildHeader(currentDensity));
        root.addView(buildStatusBanner(currentDensity));
        root.addView(buildTopActionsBar(currentDensity, isLandscape));
        root.addView(buildPreviewCard(currentDensity, isLandscape));
        root.addView(buildBundledCatalogSection(currentDensity));
        root.addView(buildGallerySection(currentDensity));

        scrollView.addView(root);
        reloadCatalog();
        wallpaperManager.detectBackendAsync(b -> refreshHeaderAndStatus());
        return scrollView;
    }

    public void onImagePicked(@NonNull ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            return;
        }
        Uri uri = result.getData().getData();
        if (uri == null) {
            return;
        }
        try {
            activity.getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }

        WallpaperImageHelper.Size screen = wallpaperManager.getTargetDisplaySize();
        setBusyState(true, activity.getString(
                R.string.wallpaper_themes_importing, screen.width, screen.height));

        wallpaperManager.importWallpaperFromUri(uri, (item, message) -> {
            setBusyState(false, message);
            callback.log("Arka Plan İçe Aktarma: " + message);
            if (item != null) {
                selectedItem = item;
            } else {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
            }
            reloadCatalog();
        });
    }

    public void reloadCatalog() {
        final int gen = ++catalogLoadGen;
        wallpaperManager.loadCatalogAsync(loaded -> {
            if (gen != catalogLoadGen) {
                return;
            }
            items = new ArrayList<>(loaded);
            syncSelectedItemWithCatalog();
            refreshHeaderAndStatus();
            refreshPreviewCard();
            renderBundledItems();
            renderGalleryItems();
        });
    }

    private void syncSelectedItemWithCatalog() {
        String selectedPath = wallpaperManager.getSelectedPath();
        String appliedPath = wallpaperManager.getAppliedPath();
        WallpaperThemeCatalog.WallpaperItem foundSelected = findByPath(selectedPath);
        if (foundSelected == null) {
            foundSelected = findByPath(appliedPath);
        }
        if (foundSelected == null && !items.isEmpty()) {
            foundSelected = items.get(0);
            wallpaperManager.setSelectedPath(foundSelected.filePath);
        }
        selectedItem = foundSelected;
    }

    @Nullable
    private WallpaperThemeCatalog.WallpaperItem findByPath(@Nullable String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        for (WallpaperThemeCatalog.WallpaperItem item : items) {
            if (path.equals(item.filePath)) {
                return item;
            }
        }
        return null;
    }

    private View buildHeader(float density) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(12, density);
        row.setLayoutParams(rowLp);

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText(R.string.wallpaper_themes_title);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(UiStyles.color(activity, R.color.textPrimary));
        texts.addView(title);

        TextView subtitle = new TextView(activity);
        subtitle.setText(R.string.wallpaper_themes_subtitle);
        subtitle.setTextSize(13);
        subtitle.setTextColor(UiStyles.color(activity, R.color.textSecondaryCool));
        subtitle.setPadding(0, dp(3, density), 0, 0);
        texts.addView(subtitle);

        row.addView(texts, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        WallpaperImageHelper.Size screen = wallpaperManager.getTargetDisplaySize();
        tvResolutionBadge = new TextView(activity);
        tvResolutionBadge.setText(activity.getString(
                R.string.wallpaper_themes_screen_res,
                screen.width,
                screen.height,
                WallpaperImageHelper.formatAspectLabel(screen.width, screen.height)));
        tvResolutionBadge.setTextSize(12);
        tvResolutionBadge.setTypeface(null, Typeface.BOLD);
        tvResolutionBadge.setTextColor(UiStyles.color(activity, R.color.textAccent));
        tvResolutionBadge.setPadding(dp(12, density), dp(6, density), dp(12, density), dp(6, density));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(UiStyles.color(activity, R.color.surfaceCardInner));
        badgeBg.setCornerRadius(dp(12, density));
        badgeBg.setStroke(dp(1, density), UiStyles.color(activity, R.color.automationCardStroke));
        tvResolutionBadge.setBackground(badgeBg);

        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.leftMargin = dp(12, density);
        row.addView(tvResolutionBadge, badgeLp);

        return row;
    }

    private View buildStatusBanner(float density) {
        LinearLayout card = createThemedCard(density, 12);

        tvActiveStatus = new TextView(activity);
        tvActiveStatus.setTextSize(14);
        tvActiveStatus.setTypeface(null, Typeface.BOLD);
        tvActiveStatus.setTextColor(UiStyles.color(activity, R.color.textPrimary));
        card.addView(tvActiveStatus);

        tvFolderPath = new TextView(activity);
        tvFolderPath.setTextSize(12);
        tvFolderPath.setTextColor(UiStyles.color(activity, R.color.textSecondaryCool));
        tvFolderPath.setPadding(0, dp(4, density), 0, 0);
        card.addView(tvFolderPath);

        tvLauncherStatus = new TextView(activity);
        tvLauncherStatus.setTextSize(12);
        tvLauncherStatus.setPadding(0, dp(3, density), 0, 0);
        card.addView(tvLauncherStatus);

        return card;
    }

    private View buildTopActionsBar(float density, boolean landscape) {
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        wrapLp.bottomMargin = dp(14, density);
        wrap.setLayoutParams(wrapLp);

        int accentColor = UiStyles.color(activity, R.color.oemAccent);
        btnUploadImage = new Button(activity);
        btnUploadImage.setText(R.string.wallpaper_themes_upload_btn);
        btnUploadImage.setAllCaps(false);
        btnUploadImage.setTextSize(14);
        btnUploadImage.setTypeface(null, Typeface.BOLD);
        btnUploadImage.setTextColor(contrastTextColor(accentColor));
        btnUploadImage.setMinimumHeight(dp(48, density));
        btnUploadImage.setPadding(dp(16, density), dp(8, density), dp(16, density), dp(8, density));
        UiStyles.styleOemButton(btnUploadImage, accentColor);
        btnUploadImage.setOnClickListener(v -> launchImagePicker());

        btnRefreshFolder = createSecondaryButton(R.string.wallpaper_themes_refresh_btn, density);
        btnRefreshFolder.setOnClickListener(v -> {
            setStatusNote(null);
            reloadCatalog();
        });

        btnRestoreOem = createSecondaryButton(R.string.wallpaper_themes_restore_oem_btn, density);
        btnRestoreOem.setOnClickListener(v -> restoreOemDefault());

        if (landscape) {
            wrap.addView(btnUploadImage, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.35f));

            LinearLayout.LayoutParams refLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.75f);
            refLp.leftMargin = dp(10, density);
            wrap.addView(btnRefreshFolder, refLp);

            LinearLayout.LayoutParams rstLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            rstLp.leftMargin = dp(10, density);
            wrap.addView(btnRestoreOem, rstLp);
        } else {
            wrap.addView(btnUploadImage, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            LinearLayout subRow = new LinearLayout(activity);
            subRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = dp(8, density);
            subRow.addView(btnRefreshFolder, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.85f));
            LinearLayout.LayoutParams rstLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.15f);
            rstLp.leftMargin = dp(8, density);
            subRow.addView(btnRestoreOem, rstLp);
            wrap.addView(subRow, subLp);
        }

        return wrap;
    }

    private View buildPreviewCard(float density, boolean landscape) {
        LinearLayout card = createThemedCard(density, 14);

        FrameLayout previewFrame = new FrameLayout(activity);
        GradientDrawable frameBg = new GradientDrawable();
        frameBg.setColor(UiStyles.color(activity, R.color.surfaceCardInner));
        frameBg.setCornerRadius(dp(12, density));
        frameBg.setStroke(dp(1, density), UiStyles.color(activity, R.color.automationCardStroke));
        previewFrame.setBackground(frameBg);
        previewFrame.setClipToOutline(true);
        previewFrame.setOutlineProvider(ViewOutlineProvider.BACKGROUND);

        previewImageView = new ImageView(activity);
        previewImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewFrame.addView(previewImageView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        previewEmptyView = new TextView(activity);
        previewEmptyView.setText(R.string.wallpaper_themes_preview_empty);
        previewEmptyView.setTextSize(13);
        previewEmptyView.setTextColor(UiStyles.color(activity, R.color.textSecondaryCool));
        previewEmptyView.setGravity(Gravity.CENTER);
        previewEmptyView.setPadding(dp(20, density), dp(16, density), dp(20, density), dp(16, density));
        previewFrame.addView(previewEmptyView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        WallpaperImageHelper.Size screen = wallpaperManager.getTargetDisplaySize();
        float aspect = Math.max(1.2f, Math.min(3.2f, (float) screen.width / screen.height));
        int baseWidth = landscape ? dp(520, density) : dp(340, density);
        int computedHeight = Math.round(baseWidth / aspect);
        int previewHeight = Math.max(dp(155, density), Math.min(dp(235, density), computedHeight));

        card.addView(previewFrame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, previewHeight));

        LinearLayout footerRow = new LinearLayout(activity);
        footerRow.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        footerRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        footerLp.topMargin = dp(12, density);
        footerRow.setLayoutParams(footerLp);

        LinearLayout metaCol = new LinearLayout(activity);
        metaCol.setOrientation(LinearLayout.VERTICAL);

        tvPreviewName = new TextView(activity);
        tvPreviewName.setTextSize(15);
        tvPreviewName.setTypeface(null, Typeface.BOLD);
        tvPreviewName.setTextColor(UiStyles.color(activity, R.color.textPrimary));
        tvPreviewName.setSingleLine(true);
        tvPreviewName.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        metaCol.addView(tvPreviewName);

        tvPreviewMeta = new TextView(activity);
        tvPreviewMeta.setTextSize(12);
        tvPreviewMeta.setTextColor(UiStyles.color(activity, R.color.textSecondaryCool));
        tvPreviewMeta.setPadding(0, dp(2, density), 0, 0);
        metaCol.addView(tvPreviewMeta);

        LinearLayout btnGroup = new LinearLayout(activity);
        btnGroup.setOrientation(LinearLayout.HORIZONTAL);
        btnGroup.setGravity(Gravity.CENTER_VERTICAL);

        int accentColor = UiStyles.color(activity, R.color.oemAccent);
        btnApplySelected = new Button(activity);
        btnApplySelected.setText(R.string.wallpaper_themes_apply_btn);
        btnApplySelected.setAllCaps(false);
        btnApplySelected.setTextSize(13);
        btnApplySelected.setTypeface(null, Typeface.BOLD);
        btnApplySelected.setTextColor(contrastTextColor(accentColor));
        btnApplySelected.setMinimumHeight(dp(44, density));
        btnApplySelected.setPadding(dp(16, density), dp(6, density), dp(16, density), dp(6, density));
        UiStyles.styleOemButton(btnApplySelected, accentColor);
        btnApplySelected.setOnClickListener(v -> {
            if (selectedItem != null) {
                applyWallpaper(selectedItem);
            }
        });

        btnDeleteSelected = createDestructiveButton(R.string.wallpaper_themes_delete_btn, density);
        btnDeleteSelected.setOnClickListener(v -> {
            if (selectedItem != null) {
                confirmDeleteWallpaper(selectedItem);
            }
        });

        btnGroup.addView(btnApplySelected, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        delLp.leftMargin = dp(8, density);
        btnGroup.addView(btnDeleteSelected, delLp);

        if (landscape) {
            footerRow.addView(metaCol, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams grpLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            grpLp.leftMargin = dp(12, density);
            footerRow.addView(btnGroup, grpLp);
        } else {
            footerRow.addView(metaCol, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams grpLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            grpLp.topMargin = dp(10, density);
            footerRow.addView(btnGroup, grpLp);
        }
        card.addView(footerRow);

        tvActionStatus = new TextView(activity);
        tvActionStatus.setTextSize(12);
        tvActionStatus.setTextColor(UiStyles.color(activity, R.color.textSecondaryCool));
        tvActionStatus.setPadding(dp(2, density), dp(8, density), 0, 0);
        tvActionStatus.setVisibility(View.GONE);
        card.addView(tvActionStatus);

        return card;
    }

    private View buildGallerySection(float density) {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);

        tvCatalogTitle = new TextView(activity);
        tvCatalogTitle.setText(activity.getString(R.string.wallpaper_themes_section_catalog, 0));
        tvCatalogTitle.setTextSize(14);
        tvCatalogTitle.setTypeface(null, Typeface.BOLD);
        tvCatalogTitle.setTextColor(UiStyles.color(activity, R.color.textPrimary));
        tvCatalogTitle.setPadding(dp(2, density), 0, 0, dp(8, density));
        section.addView(tvCatalogTitle);

        galleryContainer = new LinearLayout(activity);
        galleryContainer.setOrientation(LinearLayout.VERTICAL);
        section.addView(galleryContainer);

        return section;
    }

    private interface CardBuilder<T> {
        View build(T item);
    }

    private <T> void renderGridCards(
            @NonNull LinearLayout container,
            @NonNull List<T> data,
            @NonNull CardBuilder<T> cardBuilder) {
        if (isLandscape) {
            for (int i = 0; i < data.size(); i += 2) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setBaselineAligned(false);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i + 2 < data.size()) {
                    rowLp.bottomMargin = dp(10, currentDensity);
                }
                row.setLayoutParams(rowLp);

                row.addView(cardBuilder.build(data.get(i)), new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                if (i + 1 < data.size()) {
                    LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    rightLp.leftMargin = dp(10, currentDensity);
                    row.addView(cardBuilder.build(data.get(i + 1)), rightLp);
                } else {
                    View spacer = new View(activity);
                    LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, 1, 1f);
                    spLp.leftMargin = dp(10, currentDensity);
                    row.addView(spacer, spLp);
                }
                container.addView(row);
            }
        } else {
            for (int i = 0; i < data.size(); i++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i + 1 < data.size()) {
                    lp.bottomMargin = dp(10, currentDensity);
                }
                container.addView(cardBuilder.build(data.get(i)), lp);
            }
        }
    }

    private View buildBundledCatalogSection(float density) {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sectionLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sectionLp.bottomMargin = dp(18, density);
        section.setLayoutParams(sectionLp);

        tvBundledTitle = new TextView(activity);
        tvBundledTitle.setText(activity.getString(
                R.string.wallpaper_bundled_section_title,
                WallpaperThemeCatalog.bundledItems().size()));
        tvBundledTitle.setTextSize(14);
        tvBundledTitle.setTypeface(null, Typeface.BOLD);
        tvBundledTitle.setTextColor(UiStyles.color(activity, R.color.textPrimary));
        tvBundledTitle.setPadding(dp(2, density), 0, 0, dp(8, density));
        section.addView(tvBundledTitle);

        bundledGalleryContainer = new LinearLayout(activity);
        bundledGalleryContainer.setOrientation(LinearLayout.VERTICAL);
        section.addView(bundledGalleryContainer);

        return section;
    }

    private void renderBundledItems() {
        if (bundledGalleryContainer == null) {
            return;
        }
        bundledGalleryContainer.removeAllViews();
        List<WallpaperThemeCatalog.BundledItem> bundled = WallpaperThemeCatalog.bundledItems();
        if (tvBundledTitle != null) {
            tvBundledTitle.setText(activity.getString(
                    R.string.wallpaper_bundled_section_title, bundled.size()));
        }
        renderGridCards(bundledGalleryContainer, bundled, this::buildBundledWallpaperCard);
    }

    private View buildBundledWallpaperCard(@NonNull WallpaperThemeCatalog.BundledItem bundled) {
        File storageDir = wallpaperManager.getStorageDirectory();
        File localFile = WallpaperThemeCatalog.findInstalledBundledFile(storageDir, bundled.id);
        boolean isInstalled = localFile != null;
        boolean isApplied = localFile != null && localFile.getAbsolutePath().equals(wallpaperManager.getAppliedPath());

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(10, currentDensity);
        card.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiStyles.color(activity, R.color.surfaceCard));
        bg.setCornerRadius(dp(14, currentDensity));
        int strokeColor = isApplied
                ? UiStyles.color(activity, R.color.statusSuccess)
                : UiStyles.color(activity, R.color.automationCardStroke);
        bg.setStroke(dp(isApplied ? 2 : 1, currentDensity), strokeColor);
        card.setBackground(bg);

        FrameLayout thumbFrame = new FrameLayout(activity);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setColor(UiStyles.color(activity, R.color.surfaceCardInner));
        thumbBg.setCornerRadius(dp(10, currentDensity));
        thumbFrame.setBackground(thumbBg);
        thumbFrame.setClipToOutline(true);
        thumbFrame.setOutlineProvider(ViewOutlineProvider.BACKGROUND);

        ImageView thumb = new ImageView(activity);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setTag("asset:" + bundled.assetPath);
        thumbFrame.addView(thumb, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        if (isApplied || isInstalled) {
            int badgeColor = UiStyles.color(activity, R.color.statusSuccess);
            TextView pill = new TextView(activity);
            pill.setText(isApplied ? R.string.wallpaper_themes_card_applied : R.string.wallpaper_bundled_saved_badge);
            pill.setTextSize(11);
            pill.setTypeface(null, Typeface.BOLD);
            pill.setTextColor(contrastTextColor(badgeColor));
            pill.setPadding(dp(8, currentDensity), dp(3, currentDensity), dp(8, currentDensity), dp(3, currentDensity));
            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setCornerRadius(dp(8, currentDensity));
            pillBg.setColor(badgeColor);
            pill.setBackground(pillBg);
            FrameLayout.LayoutParams pillLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END);
            pillLp.setMargins(0, dp(8, currentDensity), dp(8, currentDensity), 0);
            thumbFrame.addView(pill, pillLp);
        }

        if (localFile != null) {
            wallpaperManager.loadThumbnailAsync(localFile.getAbsolutePath(), 640, 360, (path, bmp) -> {
                if (bmp != null && ("asset:" + bundled.assetPath).equals(thumb.getTag())) {
                    thumb.setImageBitmap(bmp);
                }
            });
        } else {
            wallpaperManager.loadAssetThumbnailAsync(bundled, 640, 360, (assetPath, bmp) -> {
                if (bmp != null && ("asset:" + bundled.assetPath).equals(thumb.getTag())) {
                    thumb.setImageBitmap(bmp);
                }
            });
        }

        card.addView(thumbFrame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(120, currentDensity)));

        TextView titleView = new TextView(activity);
        titleView.setText(bundled.title);
        titleView.setTextSize(14);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(UiStyles.color(activity, R.color.textPrimary));
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setPadding(dp(2, currentDensity), dp(8, currentDensity), dp(2, currentDensity), 0);
        card.addView(titleView);

        TextView metaView = new TextView(activity);
        metaView.setText(bundled.resolution + " (16:9)");
        metaView.setTextSize(12);
        metaView.setTextColor(UiStyles.color(activity, R.color.textSecondaryCool));
        metaView.setPadding(dp(2, currentDensity), dp(2, currentDensity), dp(2, currentDensity), dp(8, currentDensity));
        card.addView(metaView);

        int accentColor = UiStyles.color(activity, R.color.oemAccent);
        LinearLayout actionRow = new LinearLayout(activity);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        Button primaryBtn = new Button(activity);
        primaryBtn.setAllCaps(false);
        primaryBtn.setTextSize(12);
        primaryBtn.setTypeface(null, Typeface.BOLD);
        primaryBtn.setTextColor(contrastTextColor(accentColor));
        primaryBtn.setMinimumHeight(dp(40, currentDensity));
        primaryBtn.setPadding(dp(12, currentDensity), dp(4, currentDensity), dp(12, currentDensity), dp(4, currentDensity));
        UiStyles.styleOemButton(primaryBtn, accentColor);

        if (localFile != null) {
            WallpaperThemeCatalog.WallpaperItem matchingLocal = findByPath(localFile.getAbsolutePath());
            primaryBtn.setText(R.string.wallpaper_themes_apply_btn);
            primaryBtn.setOnClickListener(v -> applyBundledItem(bundled));

            Button delBtn = createDestructiveButton(R.string.wallpaper_themes_delete_btn, currentDensity);
            delBtn.setMinimumHeight(dp(40, currentDensity));
            delBtn.setTextSize(12);
            delBtn.setOnClickListener(v -> {
                if (matchingLocal != null) {
                    confirmDeleteWallpaper(matchingLocal);
                } else {
                    wallpaperManager.deleteWallpaperFile(localFile.getAbsolutePath(), (d, w, m) -> {
                        setStatusNote(m);
                        reloadCatalog();
                    });
                }
            });

            actionRow.addView(primaryBtn, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f));
            LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f);
            delLp.leftMargin = dp(8, currentDensity);
            actionRow.addView(delBtn, delLp);
        } else {
            primaryBtn.setText(R.string.wallpaper_bundled_save_apply_btn);
            primaryBtn.setOnClickListener(v -> applyBundledItem(bundled));
            actionRow.addView(primaryBtn, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        card.addView(actionRow);
        return card;
    }

    private void applyBundledItem(@NonNull WallpaperThemeCatalog.BundledItem bundled) {
        setBusyState(true, activity.getString(R.string.wallpaper_themes_applying));
        wallpaperManager.applyBundledWallpaperAsync(bundled, (oemOk, sysOk, message) -> {
            setBusyState(false, message);
            callback.log("Hazır 4K Duvar Kağıdı: " + message);
            reloadCatalog();
        });
    }

    private void renderGalleryItems() {
        if (galleryContainer == null) {
            return;
        }
        galleryContainer.removeAllViews();

        if (tvCatalogTitle != null) {
            tvCatalogTitle.setText(activity.getString(
                    R.string.wallpaper_themes_section_catalog, items.size()));
        }

        if (items.isEmpty()) {
            LinearLayout emptyCard = createThemedCard(currentDensity, 0);
            TextView emptyText = new TextView(activity);
            emptyText.setText(R.string.wallpaper_themes_empty_list);
            emptyText.setTextSize(13);
            emptyText.setTextColor(UiStyles.color(activity, R.color.textSecondaryCool));
            emptyCard.addView(emptyText);
            galleryContainer.addView(emptyCard);
            return;
        }

        renderGridCards(galleryContainer, items, this::buildWallpaperItemCard);
    }

    private View buildWallpaperItemCard(@NonNull WallpaperThemeCatalog.WallpaperItem item) {
        boolean isSelected = selectedItem != null && item.filePath.equals(selectedItem.filePath);
        boolean isApplied = item.filePath.equals(wallpaperManager.getAppliedPath());

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(10, currentDensity);
        card.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiStyles.color(activity, R.color.surfaceCard));
        bg.setCornerRadius(dp(14, currentDensity));
        int strokeColor = isSelected
                ? UiStyles.color(activity, R.color.oemAccent)
                : UiStyles.color(activity, R.color.automationCardStroke);
        bg.setStroke(dp(isSelected ? 2 : 1, currentDensity), strokeColor);
        card.setBackground(bg);

        FrameLayout thumbFrame = new FrameLayout(activity);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setColor(UiStyles.color(activity, R.color.surfaceCardInner));
        thumbBg.setCornerRadius(dp(10, currentDensity));
        thumbFrame.setBackground(thumbBg);
        thumbFrame.setClipToOutline(true);
        thumbFrame.setOutlineProvider(ViewOutlineProvider.BACKGROUND);

        ImageView thumb = new ImageView(activity);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setTag(item.filePath);
        thumbFrame.addView(thumb, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        if (isApplied || isSelected) {
            int pillBgColor = UiStyles.color(activity,
                    isApplied ? R.color.statusSuccess : R.color.oemAccent);
            TextView pill = new TextView(activity);
            pill.setTextSize(11);
            pill.setTypeface(null, Typeface.BOLD);
            pill.setTextColor(contrastTextColor(pillBgColor));
            pill.setPadding(dp(8, currentDensity), dp(3, currentDensity), dp(8, currentDensity), dp(3, currentDensity));
            pill.setText(isApplied ? R.string.wallpaper_themes_card_applied : R.string.wallpaper_themes_card_selected);
            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setCornerRadius(dp(8, currentDensity));
            pillBg.setColor(pillBgColor);
            pill.setBackground(pillBg);
            FrameLayout.LayoutParams pillLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END);
            pillLp.setMargins(0, dp(8, currentDensity), dp(8, currentDensity), 0);
            thumbFrame.addView(pill, pillLp);
        }

        wallpaperManager.loadThumbnailAsync(item.filePath, 640, 360, (path, bmp) -> {
            if (bmp != null && path.equals(thumb.getTag())) {
                thumb.setImageBitmap(bmp);
            }
        });

        card.addView(thumbFrame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(120, currentDensity)));

        TextView nameView = new TextView(activity);
        nameView.setText(item.fileName);
        nameView.setTextSize(14);
        nameView.setTypeface(null, Typeface.BOLD);
        nameView.setTextColor(UiStyles.color(activity, R.color.textPrimary));
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        nameView.setPadding(dp(2, currentDensity), dp(8, currentDensity), dp(2, currentDensity), 0);
        card.addView(nameView);

        TextView metaView = new TextView(activity);
        String aspect = WallpaperImageHelper.formatAspectLabel(item.width, item.height);
        metaView.setText(item.formatResolution() + " (" + aspect + ") · " + item.formatSize());
        metaView.setTextSize(12);
        metaView.setTextColor(UiStyles.color(activity, R.color.textSecondaryCool));
        metaView.setPadding(dp(2, currentDensity), dp(2, currentDensity), dp(2, currentDensity), dp(8, currentDensity));
        card.addView(metaView);

        LinearLayout actionRow = new LinearLayout(activity);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        int accentColor = UiStyles.color(activity, R.color.oemAccent);
        Button applyBtn = new Button(activity);
        applyBtn.setText(R.string.wallpaper_themes_apply_btn);
        applyBtn.setAllCaps(false);
        applyBtn.setTextSize(12);
        applyBtn.setTypeface(null, Typeface.BOLD);
        applyBtn.setTextColor(contrastTextColor(accentColor));
        applyBtn.setMinimumHeight(dp(40, currentDensity));
        applyBtn.setPadding(dp(12, currentDensity), dp(4, currentDensity), dp(12, currentDensity), dp(4, currentDensity));
        UiStyles.styleOemButton(applyBtn, accentColor);
        applyBtn.setOnClickListener(v -> {
            selectItem(item);
            applyWallpaper(item);
        });

        Button delBtn = createDestructiveButton(R.string.wallpaper_themes_delete_btn, currentDensity);
        delBtn.setMinimumHeight(dp(40, currentDensity));
        delBtn.setTextSize(12);
        delBtn.setOnClickListener(v -> confirmDeleteWallpaper(item));

        actionRow.addView(applyBtn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f));
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f);
        delLp.leftMargin = dp(8, currentDensity);
        actionRow.addView(delBtn, delLp);

        card.addView(actionRow);

        card.setOnClickListener(v -> selectItem(item));
        return card;
    }

    private void selectItem(@NonNull WallpaperThemeCatalog.WallpaperItem item) {
        selectedItem = item;
        wallpaperManager.setSelectedPath(item.filePath);
        refreshPreviewCard();
        renderGalleryItems();
    }

    private void refreshHeaderAndStatus() {
        WallpaperImageHelper.Size screen = wallpaperManager.getTargetDisplaySize();
        if (tvResolutionBadge != null) {
            tvResolutionBadge.setText(activity.getString(
                    R.string.wallpaper_themes_screen_res,
                    screen.width,
                    screen.height,
                    WallpaperImageHelper.formatAspectLabel(screen.width, screen.height)));
        }

        if (tvActiveStatus != null) {
            String appliedPath = wallpaperManager.getAppliedPath();
            WallpaperThemeCatalog.WallpaperItem appliedItem = findByPath(appliedPath);
            if (appliedItem != null) {
                tvActiveStatus.setText(activity.getString(
                        R.string.wallpaper_themes_status_custom, appliedItem.fileName));
            } else {
                tvActiveStatus.setText(R.string.wallpaper_themes_status_oem);
            }
        }

        if (tvFolderPath != null) {
            tvFolderPath.setText(activity.getString(
                    R.string.wallpaper_themes_folder_path,
                    wallpaperManager.getStorageDirectory().getAbsolutePath()));
        }

        if (tvLauncherStatus != null) {
            OemWallpaperManager.Backend backend = wallpaperManager.getCachedBackend();
            int textRes;
            int colorRes = R.color.textSecondaryCool;
            if (backend == null) {
                textRes = R.string.wallpaper_themes_launcher_checking;
            } else {
                switch (backend) {
                    case SV_AIDL:
                    case JAECOO_FILE:
                        textRes = R.string.wallpaper_themes_launcher_connected;
                        colorRes = R.color.statusSuccess;
                        break;
                    case UNSUPPORTED_OEM:
                        textRes = R.string.wallpaper_themes_launcher_unsupported;
                        colorRes = R.color.statusWarning;
                        break;
                    case SYSTEM:
                    default:
                        textRes = R.string.wallpaper_themes_launcher_fallback;
                        break;
                }
            }
            tvLauncherStatus.setText(textRes);
            tvLauncherStatus.setTextColor(UiStyles.color(activity, colorRes));
        }
    }

    private void refreshPreviewCard() {
        boolean hasSelection = selectedItem != null;
        if (btnApplySelected != null) {
            btnApplySelected.setEnabled(hasSelection);
            btnApplySelected.setAlpha(hasSelection ? 1f : 0.45f);
        }
        if (btnDeleteSelected != null) {
            btnDeleteSelected.setEnabled(hasSelection);
            btnDeleteSelected.setAlpha(hasSelection ? 1f : 0.45f);
        }

        if (selectedItem == null) {
            if (previewImageView != null) {
                previewImageView.setImageDrawable(null);
                previewImageView.setTag(null);
            }
            if (previewEmptyView != null) {
                previewEmptyView.setVisibility(View.VISIBLE);
            }
            if (tvPreviewName != null) {
                tvPreviewName.setText("—");
            }
            if (tvPreviewMeta != null) {
                WallpaperImageHelper.Size screen = wallpaperManager.getTargetDisplaySize();
                tvPreviewMeta.setText(activity.getString(
                        R.string.wallpaper_themes_screen_res,
                        screen.width,
                        screen.height,
                        WallpaperImageHelper.formatAspectLabel(screen.width, screen.height)));
            }
            return;
        }

        WallpaperThemeCatalog.WallpaperItem current = selectedItem;
        if (previewEmptyView != null) {
            previewEmptyView.setVisibility(View.GONE);
        }
        if (tvPreviewName != null) {
            tvPreviewName.setText(current.fileName);
        }
        if (tvPreviewMeta != null) {
            String aspect = WallpaperImageHelper.formatAspectLabel(current.width, current.height);
            tvPreviewMeta.setText(current.formatResolution() + " (" + aspect + ") · " + current.formatSize());
        }
        if (previewImageView != null) {
            previewImageView.setTag(current.filePath);
            wallpaperManager.loadThumbnailAsync(current.filePath, 1280, 720, (path, bmp) -> {
                if (previewImageView != null && path.equals(previewImageView.getTag())) {
                    previewImageView.setImageBitmap(bmp);
                }
            });
        }
    }

    private void launchImagePicker() {
        Intent docIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        docIntent.addCategory(Intent.CATEGORY_OPENABLE);
        docIntent.setType("image/*");
        docIntent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"image/jpeg", "image/png", "image/webp"});
        docIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        if (docIntent.resolveActivity(activity.getPackageManager()) != null) {
            imagePicker.launch(docIntent);
            return;
        }

        Intent getIntent = new Intent(Intent.ACTION_GET_CONTENT);
        getIntent.addCategory(Intent.CATEGORY_OPENABLE);
        getIntent.setType("image/*");
        if (getIntent.resolveActivity(activity.getPackageManager()) != null) {
            imagePicker.launch(getIntent);
            return;
        }

        Toast.makeText(activity, R.string.wallpaper_themes_no_picker, Toast.LENGTH_LONG).show();
    }

    private void applyWallpaper(@NonNull WallpaperThemeCatalog.WallpaperItem item) {
        setBusyState(true, activity.getString(R.string.wallpaper_themes_applying));
        wallpaperManager.applyWallpaperFile(item.filePath, (appliedOem, appliedSys, message) -> {
            setBusyState(false, message);
            callback.log("Arka Plan Uygulandı: " + message);
            refreshHeaderAndStatus();
            renderGalleryItems();
        });
    }

    private void confirmDeleteWallpaper(@NonNull WallpaperThemeCatalog.WallpaperItem item) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.wallpaper_themes_delete_title)
                .setMessage(activity.getString(R.string.wallpaper_themes_delete_confirm, item.fileName))
                .setPositiveButton(R.string.wallpaper_themes_delete_yes, (dialog, which) -> deleteWallpaper(item))
                .setNegativeButton(R.string.wallpaper_themes_delete_no, null)
                .show();
    }

    private void deleteWallpaper(@NonNull WallpaperThemeCatalog.WallpaperItem item) {
        setBusyState(true, null);
        wallpaperManager.deleteWallpaperFile(item.filePath, (deleted, wasActive, message) -> {
            setBusyState(false, message);
            callback.log("Arka Plan Silme: " + message);
            reloadCatalog();
        });
    }

    private void restoreOemDefault() {
        setBusyState(true, null);
        wallpaperManager.restoreOemDefault((oemOk, sysOk, message) -> {
            setBusyState(false, message);
            callback.log("Fabrika Arka Planı: " + message);
            refreshHeaderAndStatus();
            renderGalleryItems();
        });
    }

    private void setBusyState(boolean busy, @Nullable String note) {
        boolean hasSelection = selectedItem != null;
        if (btnUploadImage != null) {
            btnUploadImage.setEnabled(!busy);
        }
        if (btnRefreshFolder != null) {
            btnRefreshFolder.setEnabled(!busy);
        }
        if (btnRestoreOem != null) {
            btnRestoreOem.setEnabled(!busy);
        }
        if (btnApplySelected != null) {
            btnApplySelected.setEnabled(!busy && hasSelection);
        }
        if (btnDeleteSelected != null) {
            btnDeleteSelected.setEnabled(!busy && hasSelection);
        }
        setStatusNote(note);
    }

    private void setStatusNote(@Nullable String note) {
        if (tvActionStatus == null) {
            return;
        }
        if (note == null || note.trim().isEmpty()) {
            tvActionStatus.setVisibility(View.GONE);
        } else {
            tvActionStatus.setText(note);
            tvActionStatus.setVisibility(View.VISIBLE);
        }
    }

    private LinearLayout createThemedCard(float density, int bottomMarginDp) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(14, density);
        card.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiStyles.color(activity, R.color.surfaceCard));
        bg.setCornerRadius(dp(16, density));
        bg.setStroke(dp(1, density), UiStyles.color(activity, R.color.automationCardStroke));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        if (bottomMarginDp > 0) {
            lp.bottomMargin = dp(bottomMarginDp, density);
        }
        card.setLayoutParams(lp);
        return card;
    }

    private Button createSecondaryButton(int textResId, float density) {
        Button btn = new Button(activity);
        btn.setText(textResId);
        btn.setAllCaps(false);
        btn.setTextSize(13);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setTextColor(UiStyles.color(activity, R.color.textPrimary));
        btn.setMinimumHeight(dp(48, density));
        btn.setPadding(dp(14, density), dp(8, density), dp(14, density), dp(8, density));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiStyles.color(activity, R.color.surfaceCardInner));
        bg.setCornerRadius(activity.getResources().getDimension(R.dimen.oem_button_radius));
        bg.setStroke(dp(1, density), UiStyles.color(activity, R.color.automationCardStroke));
        btn.setBackground(bg);
        return btn;
    }

    private Button createDestructiveButton(int textResId, float density) {
        Button btn = new Button(activity);
        btn.setText(textResId);
        btn.setAllCaps(false);
        btn.setTextSize(13);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setTextColor(UiStyles.color(activity, R.color.textDestructive));
        btn.setMinimumHeight(dp(44, density));
        btn.setPadding(dp(14, density), dp(6, density), dp(14, density), dp(6, density));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiStyles.color(activity, R.color.buttonDestructiveBg));
        bg.setCornerRadius(activity.getResources().getDimension(R.dimen.oem_button_radius));
        bg.setStroke(dp(1, density), UiStyles.color(activity, R.color.automationCardStroke));
        btn.setBackground(bg);
        return btn;
    }

    private static int contrastTextColor(int backgroundColor) {
        return ColorUtils.calculateLuminance(backgroundColor) > 0.38 ? 0xFF0F172A : Color.WHITE;
    }

    private static int dp(int dp, float density) {
        return Math.round(dp * density);
    }
}
