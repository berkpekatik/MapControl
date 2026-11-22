package com.mapcontrol.ui.builder;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.mapcontrol.BuildConfig;
import com.mapcontrol.R;
import com.mapcontrol.files.FileBrowserPolicy;
import com.mapcontrol.files.FileKind;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.util.DialogHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Dosyalar" sekmesi: İndirilenler ve takılı USB'ler sabit kökler olarak gelir; kullanıcı yalnız bunların
 * altında gezer, siler, APK yükler ve USB'den İndirilenler'e kopyalar. "Serbest Gezinme" her açılışta
 * sorumluluk reddi gösterir ve dahili depolamanın tamamını ekler (oturumluk, kalıcı değil).
 * Hangi yolun gösterilip silinebileceğine yalnızca {@link FileBrowserPolicy} karar verir: uygulamaların
 * özel klasörleri ve sistem bölümleri iki modda da kapalıdır.
 *
 * <p>Araç ekranı için: büyük yazı, en az 88dp satır, 72dp dokunma hedefleri, iki adımda silme (düğme + onay).
 */
public class FileBrowserTabBuilder {

    public interface Callback {
        void log(String message);

        /** Mevcut Uygulamalar akışıyla (sessiz kurulum, yoksa PackageInstaller) yükler. */
        void installApk(File apk);

        /** Telefondan dosya gönderme (Web sekmesi, QR). */
        void openPhoneUpload();
    }

    private enum RootType { DOWNLOADS, USB, INTERNAL }

    private static final class Root {
        final RootType type;
        final String label;
        final File dir;

        Root(RootType type, String label, File dir) {
            this.type = type;
            this.label = label;
            this.dir = dir;
        }
    }

    private static final class Entry {
        final File file;
        final FileKind kind;
        final long size;
        final long modified;
        final int childCount;
        final boolean deletable;

        Entry(File file, FileKind kind, long size, long modified, int childCount, boolean deletable) {
            this.file = file;
            this.kind = kind;
            this.size = size;
            this.modified = modified;
            this.childCount = childCount;
            this.deletable = deletable;
        }
    }

    private static final int PAGE_SIZE = 100;
    private static final int THUMB_PX = 160;

    private final Context context;
    private final Callback callback;
    private final FileBrowserPolicy policy;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService listExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2);
    private final LruCache<String, Bitmap> thumbs = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);

    // Durum (tema yeniden kurulumunda korunur)
    private List<Root> roots = new ArrayList<>();
    @Nullable private Root currentRoot;
    @Nullable private File currentRootCanonical;
    @Nullable private File currentDir;
    private boolean freeMode;
    private List<Entry> entries = Collections.emptyList();
    private boolean loading = true;
    private boolean unreadable;
    private long freeBytes = -1;
    private int shownCount = PAGE_SIZE;
    private int generation;

    // Görünümler
    private LinearLayout rootView;
    private LinearLayout chipRow;
    private Button freeModeButton;
    private View banner;
    private ImageView backButton;
    private TextView pathText;
    private TextView freeSpaceText;
    private ScrollView listScroll;
    private LinearLayout listContainer;

    public FileBrowserTabBuilder(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
        List<File> extra = new ArrayList<>();
        extra.add(context.getDataDir());
        extra.add(context.getFilesDir());
        extra.add(context.getCacheDir());
        this.policy = new FileBrowserPolicy(Environment.getExternalStorageDirectory(), extra);
        build();
    }

    // ---------------------------------------------------------------- Yaşam döngüsü

    /** Görünümleri (tema değişince de) baştan kurar; gezinme durumu korunur. */
    public View build() {
        rootView = new LinearLayout(context);
        rootView.setOrientation(LinearLayout.VERTICAL);
        int margin = UiStyles.dimenPx(context, R.dimen.oem_card_margin);
        rootView.setPadding(margin, margin, margin, 0);

        rootView.addView(buildHeader(), matchWrap());
        banner = buildBanner();
        LinearLayout.LayoutParams bannerLp = matchWrap();
        bannerLp.topMargin = dp(8);
        rootView.addView(banner, bannerLp);

        listScroll = new ScrollView(context);
        listScroll.setFillViewport(true);
        listScroll.setClipToPadding(false);
        listScroll.setPadding(0, dp(12), 0, dp(24));
        listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(listContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rootView.addView(listScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        renderAll();
        return rootView;
    }

    public View getView() {
        return rootView != null ? rootView : build();
    }

    /** Sekme her açıldığında: USB takılıp çıkarılmış olabilir, klasör içeriği değişmiş olabilir. */
    public void onTabVisible() {
        reload();
    }

    // ---------------------------------------------------------------- Üst bölüm

    private View buildHeader() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        card.setPadding(pad, pad, pad, pad);
        UiStyles.setGlassCardBackground(card);

        // 1. satır: kök çipleri + serbest gezinme
        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        HorizontalScrollView chipScroll = new HorizontalScrollView(context);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipRow = new LinearLayout(context);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipScroll.addView(chipRow);
        row1.addView(chipScroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        freeModeButton = new Button(context);
        freeModeButton.setText(R.string.file_browser_free_mode);
        freeModeButton.setOnClickListener(v -> onFreeModeClicked());
        LinearLayout.LayoutParams fmLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(72));
        fmLp.leftMargin = dp(12);
        row1.addView(freeModeButton, fmLp);
        card.addView(row1, matchWrap());

        // 2. satır: geri + yol + boş alan + yenile + telefondan gönder
        LinearLayout row2 = new LinearLayout(context);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);

        backButton = iconButton(R.drawable.ic_mdi_arrow_left,
                UiStyles.color(context, R.color.surfaceCardInner),
                UiStyles.color(context, R.color.textPrimary),
                context.getString(R.string.file_browser_back));
        backButton.setOnClickListener(v -> goUp());
        row2.addView(backButton, square(72));

        LinearLayout pathCol = new LinearLayout(context);
        pathCol.setOrientation(LinearLayout.VERTICAL);
        pathCol.setPadding(dp(16), 0, dp(12), 0);
        pathText = new TextView(context);
        UiStyles.setTextSize(pathText, R.dimen.text_size_large);
        UiStyles.setFontStyle(pathText, android.graphics.Typeface.BOLD);
        pathText.setTextColor(UiStyles.color(context, R.color.textPrimary));
        pathText.setSingleLine(true);
        pathText.setEllipsize(TextUtils.TruncateAt.START);
        pathCol.addView(pathText, matchWrap());
        freeSpaceText = new TextView(context);
        UiStyles.setTextSize(freeSpaceText, R.dimen.text_size_small);
        freeSpaceText.setTextColor(UiStyles.color(context, R.color.textSecondary));
        pathCol.addView(freeSpaceText, matchWrap());
        row2.addView(pathCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView refresh = iconButton(R.drawable.ic_mdi_refresh,
                UiStyles.color(context, R.color.surfaceCardInner),
                UiStyles.color(context, R.color.textPrimary),
                context.getString(R.string.file_browser_refresh));
        refresh.setOnClickListener(v -> reload());
        row2.addView(refresh, square(72));

        Button phone = new Button(context);
        phone.setText(R.string.file_browser_send_from_phone);
        int accent = UiStyles.color(context, R.color.accent);
        UiStyles.styleOemButton(phone, accent);
        UiStyles.setButtonStartIconTinted(phone, R.drawable.ic_mdi_cellphone, UiStyles.textOnColor(accent), dp(8));
        phone.setOnClickListener(v -> callback.openPhoneUpload());
        LinearLayout.LayoutParams phoneLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(72));
        phoneLp.leftMargin = dp(12);
        row2.addView(phone, phoneLp);

        LinearLayout.LayoutParams row2Lp = matchWrap();
        row2Lp.topMargin = dp(12);
        card.addView(row2, row2Lp);
        return card;
    }

    private View buildBanner() {
        LinearLayout b = new LinearLayout(context);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(16), dp(10), dp(10), dp(10));
        int warn = UiStyles.color(context, R.color.buttonDanger);
        UiStyles.applySolidRoundedBackgroundDp(b, withAlpha(warn, 0x33), 16);

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_mdi_alert);
        icon.setImageTintList(ColorStateList.valueOf(warn));
        b.addView(icon, square(32));

        TextView text = new TextView(context);
        text.setText(R.string.file_browser_free_mode_banner);
        UiStyles.setTextSize(text, R.dimen.text_size_normal);
        UiStyles.setFontStyle(text, android.graphics.Typeface.BOLD);
        text.setTextColor(UiStyles.color(context, R.color.textPrimary));
        text.setPadding(dp(12), 0, dp(12), 0);
        b.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button exit = new Button(context);
        exit.setText(R.string.file_browser_free_mode_exit);
        UiStyles.styleOemButton(exit, warn);
        exit.setOnClickListener(v -> setFreeMode(false));
        b.addView(exit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(64)));
        return b;
    }

    // ---------------------------------------------------------------- Çizim

    private void renderAll() {
        if (rootView == null) {
            return;
        }
        renderChips();
        renderFreeModeButton();
        banner.setVisibility(freeMode ? View.VISIBLE : View.GONE);
        renderPath();
        renderList();
    }

    private void renderChips() {
        chipRow.removeAllViews();
        for (Root r : roots) {
            boolean selected = currentRoot != null && currentRoot.dir.equals(r.dir);
            Button chip = new Button(context);
            chip.setText(r.label);
            int bg = selected ? UiStyles.color(context, R.color.accent)
                    : UiStyles.color(context, R.color.surfaceCardInner);
            UiStyles.styleOemButton(chip, bg);
            UiStyles.setTextSize(chip, R.dimen.text_size_medium);
            UiStyles.setButtonStartIconTinted(chip, rootIcon(r.type), UiStyles.textOnColor(bg), dp(10));
            chip.setOnClickListener(v -> selectRoot(r));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(72));
            lp.rightMargin = dp(12);
            chipRow.addView(chip, lp);
        }
    }

    private void renderFreeModeButton() {
        int bg = freeMode ? UiStyles.color(context, R.color.buttonDanger)
                : UiStyles.color(context, R.color.surfaceCardInner);
        UiStyles.styleOemButton(freeModeButton, bg);
        UiStyles.setButtonStartIconTinted(freeModeButton,
                freeMode ? R.drawable.ic_mdi_lock_open_variant : R.drawable.ic_mdi_lock,
                UiStyles.textOnColor(bg), dp(10));
        freeModeButton.setMinHeight(dp(72));
    }

    private void renderPath() {
        if (currentRoot == null || currentRootCanonical == null || currentDir == null) {
            pathText.setText(R.string.side_rail_file_browser);
            freeSpaceText.setText("");
            setBackEnabled(false);
            return;
        }
        String rel = currentDir.getPath().substring(currentRootCanonical.getPath().length());
        StringBuilder sb = new StringBuilder(currentRoot.label);
        for (String part : rel.split("/")) {
            if (!part.isEmpty()) {
                sb.append("  ›  ").append(part);
            }
        }
        pathText.setText(sb);
        freeSpaceText.setText(freeBytes >= 0
                ? context.getString(R.string.file_browser_free_space, Formatter.formatShortFileSize(context, freeBytes))
                : "");
        setBackEnabled(!currentDir.equals(currentRootCanonical));
    }

    private void setBackEnabled(boolean enabled) {
        backButton.setEnabled(enabled);
        backButton.setAlpha(enabled ? 1f : 0.35f);
    }

    private void renderList() {
        listContainer.removeAllViews();
        if (loading && entries.isEmpty()) {
            addStatus(R.drawable.ic_mdi_timer_sand, context.getString(R.string.file_browser_loading), null);
            return;
        }
        if (unreadable) {
            addStatus(R.drawable.ic_mdi_lock, context.getString(R.string.file_browser_unreadable), null);
            return;
        }
        if (entries.isEmpty()) {
            boolean downloads = currentRoot != null && currentRoot.type == RootType.DOWNLOADS;
            addStatus(R.drawable.ic_mdi_folder_open, context.getString(R.string.file_browser_empty),
                    downloads ? context.getString(R.string.file_browser_empty_downloads_hint) : null);
            return;
        }
        int count = Math.min(shownCount, entries.size());
        for (int i = 0; i < count; i++) {
            listContainer.addView(buildRow(entries.get(i)), rowParams());
        }
        if (entries.size() > count) {
            Button more = new Button(context);
            more.setText(context.getString(R.string.file_browser_show_more, entries.size() - count));
            UiStyles.styleOemButton(more, UiStyles.color(context, R.color.surfaceCardInner));
            more.setOnClickListener(v -> {
                shownCount += PAGE_SIZE;
                int y = listScroll.getScrollY();
                renderList();
                listScroll.post(() -> listScroll.scrollTo(0, y));
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(80));
            lp.topMargin = dp(4);
            listContainer.addView(more, lp);
        }
    }

    private void addStatus(@DrawableRes int iconRes, String title, @Nullable String hint) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(24), dp(56), dp(24), dp(24));

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(UiStyles.color(context, R.color.textHint)));
        box.addView(icon, square(96));

        TextView t = new TextView(context);
        t.setText(title);
        t.setGravity(Gravity.CENTER);
        UiStyles.setTextSize(t, R.dimen.text_size_title);
        UiStyles.setFontStyle(t, android.graphics.Typeface.BOLD);
        t.setTextColor(UiStyles.color(context, R.color.textSecondary));
        t.setPadding(0, dp(16), 0, 0);
        box.addView(t, matchWrap());

        if (hint != null) {
            TextView h = new TextView(context);
            h.setText(hint);
            h.setGravity(Gravity.CENTER);
            UiStyles.setTextSize(h, R.dimen.text_size_medium);
            h.setTextColor(UiStyles.color(context, R.color.textHint));
            h.setPadding(0, dp(8), 0, 0);
            box.addView(h, matchWrap());
        }
        listContainer.addView(box, matchWrap());
    }

    private View buildRow(Entry e) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(96));
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        UiStyles.setGlassCardBackground(row);
        row.setClickable(true);
        row.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(
                context, R.animator.oem_button_press));
        row.setOnClickListener(v -> onEntryClicked(e));

        row.addView(buildKindTile(e), square(72));

        // Ad + bilgi
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(18), 0, dp(12), 0);
        TextView name = new TextView(context);
        name.setText(e.file.getName());
        UiStyles.setTextSize(name, R.dimen.text_size_large);
        UiStyles.setFontStyle(name, android.graphics.Typeface.BOLD);
        name.setTextColor(UiStyles.color(context, R.color.textPrimary));
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        texts.addView(name, matchWrap());

        TextView meta = new TextView(context);
        meta.setText(metaText(e));
        UiStyles.setTextSize(meta, R.dimen.text_size_small);
        meta.setTextColor(UiStyles.color(context, R.color.textSecondary));
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        meta.setPadding(0, dp(4), 0, 0);
        texts.addView(meta, matchWrap());
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // İşlemler
        if (e.kind == FileKind.APK) {
            Button install = new Button(context);
            install.setText(R.string.file_browser_install);
            int green = UiStyles.color(context, R.color.buttonSuccess);
            UiStyles.styleOemButton(install, green);
            UiStyles.setTextSize(install, R.dimen.text_size_medium);
            UiStyles.setButtonStartIconTinted(install, R.drawable.ic_mdi_download, UiStyles.textOnColor(green), dp(8));
            install.setOnClickListener(v -> confirmInstall(e));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(72));
            lp.leftMargin = dp(12);
            install.setMinWidth(dp(150));
            row.addView(install, lp);
        }
        if (e.kind != FileKind.FOLDER && currentRoot != null && currentRoot.type != RootType.DOWNLOADS) {
            ImageView copy = iconButton(R.drawable.ic_mdi_content_copy,
                    UiStyles.color(context, R.color.surfaceCardInner),
                    UiStyles.color(context, R.color.textPrimary),
                    context.getString(R.string.file_browser_copy_to_downloads));
            copy.setOnClickListener(v -> copyToDownloads(e));
            LinearLayout.LayoutParams lp = square(72);
            lp.leftMargin = dp(12);
            row.addView(copy, lp);
        }
        if (e.deletable) {
            ImageView delete = iconButton(R.drawable.ic_mdi_delete,
                    UiStyles.color(context, R.color.surfaceCardInner),
                    UiStyles.color(context, R.color.textDestructive),
                    context.getString(R.string.file_browser_delete));
            delete.setOnClickListener(v -> confirmDelete(e));
            LinearLayout.LayoutParams lp = square(72);
            lp.leftMargin = dp(12);
            row.addView(delete, lp);
        }
        if (e.kind == FileKind.FOLDER) {
            ImageView chevron = new ImageView(context);
            chevron.setImageResource(R.drawable.ic_mdi_chevron_right);
            chevron.setImageTintList(ColorStateList.valueOf(UiStyles.color(context, R.color.textHint)));
            LinearLayout.LayoutParams lp = square(40);
            lp.leftMargin = dp(8);
            row.addView(chevron, lp);
        }
        return row;
    }

    /** Türe göre renkli kutu; resimlerde arka planda küçük önizleme yüklenir. */
    private View buildKindTile(Entry e) {
        int color = kindColor(e.kind);
        FrameLayout tile = new FrameLayout(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(withAlpha(color, 0x2E));
        bg.setCornerRadius(dp(18));
        tile.setBackground(bg);
        tile.setClipToOutline(true);

        ImageView icon = new ImageView(context);
        icon.setImageResource(kindIcon(e.kind));
        icon.setImageTintList(ColorStateList.valueOf(color));
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER);
        tile.addView(icon, iconLp);

        if (e.kind == FileKind.APK) {
            TextView badge = new TextView(context);
            badge.setText("APK");
            badge.setTextSize(11);
            badge.setTextColor(Color.WHITE);
            UiStyles.setFontStyle(badge, android.graphics.Typeface.BOLD);
            badge.setPadding(dp(6), dp(1), dp(6), dp(1));
            UiStyles.applySolidRoundedBackgroundDp(badge, color, 8);
            tile.addView(badge, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END));
        }

        if (e.kind == FileKind.IMAGE) {
            String key = e.file.getPath() + ":" + e.modified;
            Bitmap cached = thumbs.get(key);
            if (cached != null) {
                showThumb(icon, iconLp, cached);
            } else {
                icon.setTag(key);
                ioExecutor.execute(() -> {
                    Bitmap bmp = decodeSampled(e.file, THUMB_PX);
                    if (bmp == null) {
                        return;
                    }
                    thumbs.put(key, bmp);
                    main.post(() -> {
                        if (key.equals(icon.getTag())) {
                            showThumb(icon, iconLp, bmp);
                        }
                    });
                });
            }
        }
        return tile;
    }

    private static void showThumb(ImageView icon, FrameLayout.LayoutParams lp, Bitmap bmp) {
        icon.setImageTintList(null);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setImageBitmap(bmp);
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        icon.setLayoutParams(lp);
    }

    private String metaText(Entry e) {
        StringBuilder sb = new StringBuilder(context.getString(kindLabel(e.kind)));
        if (e.kind == FileKind.FOLDER) {
            if (e.childCount >= 0) {
                sb.append("  ·  ").append(context.getString(R.string.file_browser_folder_meta, e.childCount));
            }
        } else {
            sb.append("  ·  ").append(Formatter.formatShortFileSize(context, e.size));
        }
        if (e.modified > 0) {
            sb.append("  ·  ").append(dateFormat.format(new Date(e.modified)));
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- Gezinme

    private void selectRoot(Root r) {
        if (currentRoot != null && currentRoot.dir.equals(r.dir) && currentDir != null
                && currentDir.equals(currentRootCanonical)) {
            return;
        }
        currentRoot = r;
        currentDir = null;
        resetListAndReload();
    }

    private void navigateTo(File dir) {
        currentDir = dir;
        resetListAndReload();
    }

    private void goUp() {
        if (currentDir == null || currentRootCanonical == null || currentDir.equals(currentRootCanonical)) {
            return;
        }
        File parent = currentDir.getParentFile();
        navigateTo(parent != null ? parent : currentRootCanonical);
    }

    private void resetListAndReload() {
        entries = Collections.emptyList();
        shownCount = PAGE_SIZE;
        loading = true;
        unreadable = false;
        renderAll();
        if (listScroll != null) {
            listScroll.scrollTo(0, 0);
        }
        reload();
    }

    private void onFreeModeClicked() {
        if (freeMode) {
            setFreeMode(false);
            return;
        }
        DialogHelper.showFileBrowserFreeModeDisclaimer(context, () -> {
            callback.log("Dosyalar: serbest gezinme açıldı");
            setFreeMode(true);
        });
    }

    private void setFreeMode(boolean on) {
        freeMode = on;
        if (on) {
            // Dahili depolamanın köküne geç (kök listesi reload'da kurulur).
            currentRoot = new Root(RootType.INTERNAL, context.getString(R.string.file_browser_root_internal),
                    Environment.getExternalStorageDirectory());
        } else if (currentRoot != null && currentRoot.type == RootType.INTERNAL) {
            currentRoot = null;
        }
        currentDir = null;
        resetListAndReload();
    }

    /** Kökleri ve geçerli klasörü arka planda yeniden okur. Eski sonuçlar nesil sayacıyla atılır. */
    public void reload() {
        final int gen = ++generation;
        final boolean fm = freeMode;
        final File wantRoot = currentRoot != null ? currentRoot.dir : null;
        final File wantDir = currentDir;
        listExecutor.execute(() -> {
            List<Root> rs = discoverRoots(fm);
            Root root = null;
            for (Root r : rs) {
                if (wantRoot != null && r.dir.equals(wantRoot)) {
                    root = r;
                    break;
                }
            }
            if (root == null && !rs.isEmpty()) {
                root = rs.get(0);
            }
            File rootC = root != null ? FileBrowserPolicy.canonical(root.dir) : null;
            File dir = null;
            if (rootC != null && wantDir != null && root.dir.equals(wantRoot)) {
                // Klasör silinmiş olabilir: var olan ilk üst klasöre çık.
                for (File d = wantDir; d != null; d = d.getParentFile()) {
                    File ok = policy.resolve(rootC, d);
                    if (ok == null) {
                        break;
                    }
                    if (ok.isDirectory()) {
                        dir = ok;
                        break;
                    }
                }
            }
            if (dir == null && rootC != null && policy.resolve(rootC, rootC) != null) {
                dir = rootC;
            }

            List<Entry> list = new ArrayList<>();
            boolean cannotRead = dir == null;
            long free = -1;
            if (dir != null) {
                File[] children = dir.listFiles();
                if (children == null) {
                    cannotRead = true;
                } else {
                    for (File child : children) {
                        if (!policy.isListable(rootC, child, fm)) {
                            continue;
                        }
                        boolean isDir = child.isDirectory();
                        int count = -1;
                        if (isDir) {
                            String[] names = child.list();
                            count = names != null ? names.length : -1;
                        }
                        list.add(new Entry(child, FileKind.of(child.getName(), isDir),
                                isDir ? 0 : child.length(), child.lastModified(), count,
                                policy.canDelete(rootC, child)));
                    }
                    Collections.sort(list, (a, b) -> {
                        boolean da = a.kind == FileKind.FOLDER;
                        boolean db = b.kind == FileKind.FOLDER;
                        if (da != db) return da ? -1 : 1;
                        if (a.modified != b.modified) return Long.compare(b.modified, a.modified);
                        return a.file.getName().compareToIgnoreCase(b.file.getName());
                    });
                }
                free = dir.getUsableSpace();
            }

            final Root fRoot = root;
            final File fRootC = rootC;
            final File fDir = dir;
            final boolean fUnreadable = cannotRead;
            final long fFree = free;
            main.post(() -> {
                if (gen != generation) {
                    return;
                }
                roots = rs;
                currentRoot = fRoot;
                currentRootCanonical = fRootC;
                currentDir = fDir;
                entries = list;
                unreadable = fUnreadable;
                freeBytes = fFree;
                loading = false;
                renderAll();
            });
        });
    }

    /** İndirilenler + takılı USB'ler; serbest gezinmede dahili depolamanın tamamı da. */
    private List<Root> discoverRoots(boolean fm) {
        List<Root> list = new ArrayList<>();
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloads != null) {
            if (!downloads.exists()) {
                //noinspection ResultOfMethodCallIgnored
                downloads.mkdirs();
            }
            list.add(new Root(RootType.DOWNLOADS, context.getString(R.string.file_browser_root_downloads), downloads));
        }
        try {
            StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            if (sm != null) {
                List<File> usbDirs = new ArrayList<>();
                for (StorageVolume v : sm.getStorageVolumes()) {
                    String state = v.getState();
                    if (!v.isRemovable() || !(Environment.MEDIA_MOUNTED.equals(state)
                            || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))) {
                        continue;
                    }
                    File d = volumeDir(v);
                    if (d != null && d.isDirectory()) {
                        usbDirs.add(d);
                    }
                }
                for (int i = 0; i < usbDirs.size(); i++) {
                    String label = context.getString(R.string.file_browser_root_usb);
                    if (usbDirs.size() > 1) {
                        label = label + " " + (i + 1);
                    }
                    list.add(new Root(RootType.USB, label, usbDirs.get(i)));
                }
            }
        } catch (Throwable t) {
            callback.log("[WARN] Dosyalar: USB birimleri okunamadı: " + t.getMessage());
        }
        if (fm) {
            File internal = Environment.getExternalStorageDirectory();
            if (internal != null) {
                list.add(new Root(RootType.INTERNAL, context.getString(R.string.file_browser_root_internal), internal));
            }
        }
        return list;
    }

    @Nullable
    private static File volumeDir(StorageVolume v) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return v.getDirectory();
        }
        try {
            Object f = StorageVolume.class.getMethod("getPathFile").invoke(v);
            return f instanceof File ? (File) f : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- İşlemler

    private void onEntryClicked(Entry e) {
        switch (e.kind) {
            case FOLDER:
                navigateTo(e.file);
                break;
            case APK:
                confirmInstall(e);
                break;
            case IMAGE:
                showImagePreview(e.file);
                break;
            default:
                openExternally(e.file);
                break;
        }
    }

    /** İşlem anında yol yeniden doğrulanır (liste ile dokunma arasında değişmiş olabilir). */
    @Nullable
    private File checked(File f) {
        return currentRootCanonical != null ? policy.resolve(currentRootCanonical, f) : null;
    }

    private void confirmInstall(Entry e) {
        DialogHelper.showConfirm(context,
                context.getString(R.string.file_browser_install_title),
                e.file.getName(),
                context.getString(R.string.file_browser_install_msg),
                R.string.file_browser_install,
                () -> {
                    File f = checked(e.file);
                    if (f != null && f.isFile()) {
                        callback.log("Dosyalar: APK yükleniyor: " + f.getName());
                        callback.installApk(f);
                    }
                });
    }

    private void confirmDelete(Entry e) {
        boolean folder = e.kind == FileKind.FOLDER;
        DialogHelper.showConfirm(context,
                context.getString(R.string.file_browser_delete_title),
                e.file.getName(),
                context.getString(folder ? R.string.file_browser_delete_folder_msg
                        : R.string.file_browser_delete_file_msg),
                R.string.file_browser_delete,
                () -> {
                    final File rootC = currentRootCanonical;
                    if (rootC == null) {
                        return;
                    }
                    ioExecutor.execute(() -> {
                        boolean ok = policy.delete(rootC, e.file);
                        callback.log("Dosyalar: silme " + (ok ? "tamam: " : "başarısız: ") + e.file.getPath());
                        main.post(() -> {
                            Toast.makeText(context, ok ? R.string.file_browser_deleted
                                    : R.string.file_browser_delete_failed, Toast.LENGTH_SHORT).show();
                            reload();
                        });
                    });
                });
    }

    private void copyToDownloads(Entry e) {
        File src = checked(e.file);
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (src == null || !src.isFile() || downloads == null) {
            Toast.makeText(context, R.string.file_browser_copy_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(context, R.string.file_browser_copying, Toast.LENGTH_SHORT).show();
        ioExecutor.execute(() -> {
            //noinspection ResultOfMethodCallIgnored
            downloads.mkdirs();
            File target = FileBrowserPolicy.uniqueTarget(downloads, src.getName());
            boolean ok = false;
            try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[256 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                ok = true;
            } catch (IOException ex) {
                callback.log("[WARN] Dosyalar: kopyalama hatası: " + ex.getMessage());
            }
            if (!ok) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
            }
            final boolean fOk = ok;
            main.post(() -> Toast.makeText(context, fOk ? R.string.file_browser_copied
                    : R.string.file_browser_copy_failed, Toast.LENGTH_SHORT).show());
        });
    }

    private void openExternally(File file) {
        File f = checked(file);
        if (f == null) {
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", f);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileKind.extension(f.getName()));
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mime != null ? mime : "*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (IllegalArgumentException | ActivityNotFoundException | SecurityException ex) {
            Toast.makeText(context, R.string.file_browser_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** Uygulama içi tam ekran resim önizleme (USB'deki dosyalar dışarıya paylaşılamadığı için). */
    private void showImagePreview(File file) {
        File f = checked(file);
        if (f == null) {
            return;
        }
        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Color.BLACK);
        frame.setOnClickListener(v -> dialog.dismiss());

        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frame.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView name = new TextView(context);
        name.setText(f.getName());
        name.setTextColor(Color.WHITE);
        UiStyles.setTextSize(name, R.dimen.text_size_large);
        name.setShadowLayer(8, 0, 0, Color.BLACK);
        name.setPadding(dp(24), dp(24), dp(120), dp(24));
        frame.addView(name, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));

        ImageView close = iconButton(R.drawable.ic_mdi_close, 0x99000000, Color.WHITE,
                context.getString(R.string.file_browser_close));
        close.setOnClickListener(v -> dialog.dismiss());
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(dp(80), dp(80), Gravity.TOP | Gravity.END);
        closeLp.setMargins(dp(16), dp(16), dp(16), dp(16));
        frame.addView(close, closeLp);

        dialog.setContentView(frame);
        dialog.show();

        int target = Math.max(context.getResources().getDisplayMetrics().widthPixels,
                context.getResources().getDisplayMetrics().heightPixels);
        ioExecutor.execute(() -> {
            Bitmap bmp = decodeSampled(f, target);
            main.post(() -> {
                if (dialog.isShowing() && bmp != null) {
                    image.setImageBitmap(bmp);
                } else if (bmp == null) {
                    dialog.dismiss();
                    Toast.makeText(context, R.string.file_browser_open_failed, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Nullable
    private static Bitmap decodeSampled(File file, int targetPx) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) {
                return null;
            }
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= targetPx) {
                sample *= 2;
            }
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            return BitmapFactory.decodeFile(file.getPath(), o);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- Tür görünümü

    private int kindColor(FileKind kind) {
        switch (kind) {
            case FOLDER: return UiStyles.color(context, R.color.accent);
            case APK: return 0xFF2FBF71;
            case IMAGE: return 0xFFA970FF;
            case VIDEO: return 0xFFFF5C7A;
            case AUDIO: return 0xFFFF9F43;
            case ARCHIVE: return 0xFFD9A62E;
            case DOCUMENT: return 0xFF3FA7F5;
            default: return UiStyles.color(context, R.color.textSecondary);
        }
    }

    @DrawableRes
    private static int kindIcon(FileKind kind) {
        switch (kind) {
            case FOLDER: return R.drawable.ic_mdi_folder;
            case APK: return R.drawable.ic_mdi_package_variant;
            case IMAGE: return R.drawable.ic_mdi_image;
            case VIDEO: return R.drawable.ic_mdi_movie;
            case AUDIO: return R.drawable.ic_mdi_music_note;
            case ARCHIVE: return R.drawable.ic_mdi_zip_box_outline;
            case DOCUMENT: return R.drawable.ic_mdi_file_document_outline;
            default: return R.drawable.ic_mdi_file_outline;
        }
    }

    private static int kindLabel(FileKind kind) {
        switch (kind) {
            case FOLDER: return R.string.file_browser_kind_folder;
            case APK: return R.string.file_browser_kind_apk;
            case IMAGE: return R.string.file_browser_kind_image;
            case VIDEO: return R.string.file_browser_kind_video;
            case AUDIO: return R.string.file_browser_kind_audio;
            case ARCHIVE: return R.string.file_browser_kind_archive;
            case DOCUMENT: return R.string.file_browser_kind_document;
            default: return R.string.file_browser_kind_other;
        }
    }

    @DrawableRes
    private static int rootIcon(RootType type) {
        switch (type) {
            case DOWNLOADS: return R.drawable.ic_mdi_download;
            case USB: return R.drawable.ic_mdi_usb;
            default: return R.drawable.ic_mdi_folder;
        }
    }

    // ---------------------------------------------------------------- Yardımcılar

    private ImageView iconButton(@DrawableRes int iconRes, int bgColor, int tint, CharSequence description) {
        ImageView b = new ImageView(context);
        b.setImageResource(iconRes);
        b.setImageTintList(ColorStateList.valueOf(tint));
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int p = dp(20);
        b.setPadding(p, p, p, p);
        UiStyles.applySolidRoundedBackgroundDp(b, bgColor, 18);
        b.setContentDescription(description);
        b.setClickable(true);
        b.setFocusable(true);
        b.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(
                context, R.animator.oem_button_press));
        return b;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.bottomMargin = dp(10);
        return lp;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams square(int sizeDp) {
        return new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
    }

    private int dp(int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
