package com.mapcontrol.wallpaper;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.view.Display;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.Nullable;

import com.mapcontrol.R;
import com.desaysv.appmenu.ILauncherAidlInterface;
import com.desaysv.launcher.svwallpaper.data.WallpaperInfo;
import com.desaysv.launcher.svwallpaper.data.WallpaperStyle;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * {@code SVWallpaper} klasöründeki statik duvar kağıtlarını yönetir, yüklenen görselleri (4K dahil)
 * aracın gerçek multimedya ekran çözünürlüğüne bozulmadan uyarlar ve {@code com.desaysv.launcher}
 * (SVLauncher) + Android sistem duvar kağıdı servisine uygular.
 */
public final class OemWallpaperManager {

    public interface Callback {
        void onComplete(boolean appliedToOemLauncher, boolean appliedToAndroidWallpaper, @NonNull String message);
    }

    public interface ImportCallback {
        void onImported(@Nullable WallpaperThemeCatalog.WallpaperItem item, @NonNull String message);
    }

    public interface DeleteCallback {
        void onDeleted(boolean deleted, boolean wasActive, @NonNull String message);
    }

    public interface CatalogCallback {
        void onLoaded(@NonNull List<WallpaperThemeCatalog.WallpaperItem> items);
    }

    public interface ThumbnailCallback {
        void onThumbnailReady(@NonNull String filePath, @Nullable Bitmap bitmap);
    }

    public interface RemoteCatalogCallback {
        void onCatalogResult(
                @NonNull List<RemoteWallpaperCatalog.RemoteItem> items,
                boolean fromCache,
                @Nullable String statusMessage);
    }

    public interface DownloadCallback {
        void onProgress(int percent);
        void onFinished(@Nullable WallpaperThemeCatalog.WallpaperItem item, @NonNull String message);
    }

    private interface StreamOpener {
        @Nullable
        InputStream open() throws IOException;
    }

    private static final String TAG = "OemWallpaperManager";
    private static final String OEM_LAUNCHER_PKG = "com.desaysv.launcher";
    private static final String OEM_LAUNCHER_SERVICE = "com.desaysv.launcher.service.LauncherService";
    private static final String OEM_LAUNCHER_ACTION = "action.desaysv.launcher.service";
    public static final String OEM_WALLPAPER_DIR = "/storage/emulated/0/SVWallpaper";
    private static final String KEY_USER_WALLPAPER = WallpaperInfo.KEY_USER_WALLPAPER;

    private static volatile OemWallpaperManager instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService networkWorker = Executors.newFixedThreadPool(2);
    private final LruCache<String, Bitmap> thumbnailCache = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value == null ? 0 : value.getAllocationByteCount();
        }
    };
    private final Object aidlLock = new Object();

    @Nullable
    private volatile ILauncherAidlInterface launcherAidl;
    private boolean serviceBound = false;
    @Nullable
    private volatile CountDownLatch bindLatch;
    private volatile boolean started = false;

    private final ServiceConnection launcherConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (aidlLock) {
                launcherAidl = ILauncherAidlInterface.Stub.asInterface(service);
                CountDownLatch latch = bindLatch;
                if (latch != null) {
                    latch.countDown();
                }
            }
            Log.i(TAG, "SVLauncher AIDL connected: " + (name != null ? name.flattenToShortString() : "null"));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (aidlLock) {
                launcherAidl = null;
            }
            Log.w(TAG, "SVLauncher AIDL disconnected");
        }
    };

    private OemWallpaperManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = WallpaperThemeCatalog.prefs(appContext);
    }

    @NonNull
    private String str(@StringRes int res, Object... args) {
        return appContext.getString(res, args);
    }

    @NonNull
    public static OemWallpaperManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (OemWallpaperManager.class) {
                if (instance == null) {
                    instance = new OemWallpaperManager(context);
                }
            }
        }
        return instance;
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        if (isOemLauncherInstalled()) {
            ensureLauncherBound();
        }
    }

    public synchronized void stop() {
        if (!started) {
            return;
        }
        started = false;
        unbindLauncher();
    }

    public boolean isOemLauncherInstalled() {
        try {
            appContext.getPackageManager().getPackageInfo(OEM_LAUNCHER_PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    @NonNull
    public WallpaperImageHelper.Size getTargetDisplaySize() {
        int width = 0;
        int height = 0;
        try {
            WindowManager wm = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowMetrics metrics = wm.getMaximumWindowMetrics();
                    if (metrics != null) {
                        Rect bounds = metrics.getBounds();
                        width = bounds.width();
                        height = bounds.height();
                    }
                } else {
                    Display display = wm.getDefaultDisplay();
                    if (display != null) {
                        DisplayMetrics dm = new DisplayMetrics();
                        display.getRealMetrics(dm);
                        width = dm.widthPixels;
                        height = dm.heightPixels;
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed reading physical display size: " + t.getMessage());
        }
        if (width <= 0 || height <= 0) {
            DisplayMetrics dm = appContext.getResources().getDisplayMetrics();
            width = dm.widthPixels;
            height = dm.heightPixels;
        }
        return WallpaperImageHelper.normalizeDisplaySize(width, height);
    }

    @Nullable
    public String getSelectedPath() {
        return prefs.getString(WallpaperThemeCatalog.PREF_SELECTED_PATH, null);
    }

    public void setSelectedPath(@Nullable String path) {
        if (path == null || path.trim().isEmpty()) {
            prefs.edit().remove(WallpaperThemeCatalog.PREF_SELECTED_PATH).apply();
        } else {
            prefs.edit().putString(WallpaperThemeCatalog.PREF_SELECTED_PATH, path).apply();
        }
    }

    @Nullable
    public String getAppliedPath() {
        return prefs.getString(WallpaperThemeCatalog.PREF_APPLIED_PATH, null);
    }

    @NonNull
    public File getStorageDirectory() {
        return resolveStorageDirectory();
    }

    public void loadCatalogAsync(@NonNull CatalogCallback callback) {
        worker.execute(() -> {
            List<WallpaperThemeCatalog.WallpaperItem> items = scanWallpapersInternal();
            mainHandler.post(() -> callback.onLoaded(items));
        });
    }

    @Nullable
    public Bitmap getCachedThumbnail(@NonNull String key) {
        Bitmap bmp = thumbnailCache.get(key);
        return (bmp != null && !bmp.isRecycled()) ? bmp : null;
    }

    public void loadThumbnailAsync(
            @NonNull String filePath,
            int maxWidth,
            int maxHeight,
            @NonNull ThumbnailCallback callback) {
        Bitmap cached = getCachedThumbnail(filePath);
        if (cached != null) {
            callback.onThumbnailReady(filePath, cached);
            return;
        }
        worker.execute(() -> {
            Bitmap bmp = decodeOrientedSampledBitmapFromFile(filePath, maxWidth, maxHeight);
            if (bmp != null) {
                thumbnailCache.put(filePath, bmp);
            }
            mainHandler.post(() -> callback.onThumbnailReady(filePath, bmp));
        });
    }

    public void loadAssetThumbnailAsync(
            @NonNull WallpaperThemeCatalog.BundledItem item,
            int maxWidth,
            int maxHeight,
            @NonNull ThumbnailCallback callback) {
        Bitmap cached = getCachedThumbnail(item.assetPath);
        if (cached != null) {
            callback.onThumbnailReady(item.assetPath, cached);
            return;
        }
        worker.execute(() -> {
            Bitmap bmp = null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream in = appContext.getAssets().open(item.assetPath)) {
                    BitmapFactory.decodeStream(in, null, bounds);
                }
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = WallpaperImageHelper.calculateInSampleSize(
                            bounds.outWidth, bounds.outHeight, maxWidth, maxHeight);
                    opts.inPreferredConfig = Bitmap.Config.RGB_565;
                    try (InputStream in = appContext.getAssets().open(item.assetPath)) {
                        bmp = BitmapFactory.decodeStream(in, null, opts);
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "loadAssetThumbnailAsync failed for " + item.assetPath + ": " + t.getMessage());
            }
            if (bmp != null) {
                thumbnailCache.put(item.assetPath, bmp);
            }
            final Bitmap finalBmp = bmp;
            mainHandler.post(() -> callback.onThumbnailReady(item.assetPath, finalBmp));
        });
    }

    public void applyBundledWallpaperAsync(
            @NonNull WallpaperThemeCatalog.BundledItem item,
            @Nullable Callback callback) {
        worker.execute(() -> {
            File storageDir = resolveStorageDirectory();
            File existing = WallpaperThemeCatalog.findInstalledBundledFile(storageDir, item.id);
            if (existing != null) {
                ApplyResult res = applyWallpaperSync(existing);
                if (callback != null) {
                    mainHandler.post(() -> callback.onComplete(res.oemApplied, res.systemApplied, res.message));
                }
                return;
            }

            String fixedBase = WallpaperThemeCatalog.localBaseNameForBundledId(item.id);
            StreamOpener assetOpener = () -> appContext.getAssets().open(item.assetPath);
            AdaptResult adapted = adaptStreamToSvWallpaperSync(assetOpener, fixedBase, item.title);
            if (adapted.item == null) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onComplete(false, false, adapted.message));
                }
                return;
            }
            ApplyResult applyRes = applyWallpaperSync(new File(adapted.item.filePath));
            if (callback != null) {
                mainHandler.post(() -> callback.onComplete(applyRes.oemApplied, applyRes.systemApplied, applyRes.message));
            }
        });
    }

    public void importWallpaperFromUri(@NonNull Uri uri, @NonNull ImportCallback callback) {
        worker.execute(() -> {
            String rawName = queryDisplayName(uri);
            StreamOpener opener = () -> appContext.getContentResolver().openInputStream(uri);
            adaptAndSaveStream(opener, null, rawName, callback);
        });
    }

    public void fetchRemoteCatalogAsync(boolean forceRefresh, @NonNull RemoteCatalogCallback callback) {
        String url = RemoteWallpaperCatalog.CATALOG_URL;
        if (!RemoteWallpaperCatalog.isValidHttpUrl(url)) {
            mainHandler.post(() -> callback.onCatalogResult(Collections.emptyList(), false, null));
            return;
        }
        networkWorker.execute(() -> {
            File cacheFile = new File(appContext.getFilesDir(), RemoteWallpaperCatalog.CACHE_FILENAME);

            if (!forceRefresh && cacheFile.isFile() && cacheFile.length() > 0) {
                List<RemoteWallpaperCatalog.RemoteItem> cached = readCatalogCache(cacheFile);
                if (!cached.isEmpty()) {
                    mainHandler.post(() -> callback.onCatalogResult(
                            cached, true, null));
                    return;
                }
            }

            try {
                String json = httpGetText(url, 1024 * 1024);
                RemoteWallpaperCatalog.CatalogParseResult parsed =
                        RemoteWallpaperCatalog.parseCatalogJson(json);
                writeUtf8File(cacheFile, json);
                mainHandler.post(() -> callback.onCatalogResult(
                        parsed.items,
                        false,
                        parsed.items.isEmpty() ? "Katalogda henüz tema bulunmuyor." : null));
            } catch (Throwable t) {
                Log.w(TAG, "fetchRemoteCatalogAsync failed: " + t.getMessage());
                List<RemoteWallpaperCatalog.RemoteItem> fallback = readCatalogCache(cacheFile);
                String note = !fallback.isEmpty()
                        ? "İnternet bağlantısı yok, kayıtlı katalog gösteriliyor."
                        : "Katalog sunucusuna şu anda erişilemedi.";
                mainHandler.post(() -> callback.onCatalogResult(fallback, !fallback.isEmpty(), note));
            }
        });
    }

    public void loadRemoteThumbnailAsync(
            @NonNull RemoteWallpaperCatalog.RemoteItem item,
            int maxWidth,
            int maxHeight,
            @NonNull ThumbnailCallback callback) {
        if (item.thumbUrl == null || item.thumbUrl.isEmpty()) {
            mainHandler.post(() -> callback.onThumbnailReady(item.id, null));
            return;
        }
        networkWorker.execute(() -> {
            try {
                File thumbDir = new File(appContext.getCacheDir(), "wallpaper_thumbs");
                ensureWritableDir(thumbDir);
                File cached = new File(thumbDir, RemoteWallpaperCatalog.localBaseNameForRemoteId(item.id) + ".img");
                if (!cached.isFile() || cached.length() == 0) {
                    downloadUrlToFile(item.thumbUrl, cached, 3 * 1024 * 1024, null);
                }
                Bitmap bmp = decodeOrientedSampledBitmapFromFile(cached.getAbsolutePath(), maxWidth, maxHeight);
                mainHandler.post(() -> callback.onThumbnailReady(item.id, bmp));
            } catch (Throwable t) {
                Log.w(TAG, "loadRemoteThumbnailAsync failed for " + item.id + ": " + t.getMessage());
                mainHandler.post(() -> callback.onThumbnailReady(item.id, null));
            }
        });
    }

    public void downloadRemoteWallpaperAsync(
            @NonNull RemoteWallpaperCatalog.RemoteItem item,
            boolean applyImmediately,
            @NonNull DownloadCallback callback) {
        networkWorker.execute(() -> {
            File storageDir = resolveStorageDirectory();
            File existing = RemoteWallpaperCatalog.findDownloadedFile(storageDir, item.id);
            if (existing != null) {
                WallpaperThemeCatalog.WallpaperItem localItem = buildWallpaperItem(existing);
                setSelectedPath(existing.getAbsolutePath());
                if (applyImmediately) {
                    ApplyResult res = applyWallpaperSync(existing);
                    mainHandler.post(() -> callback.onFinished(localItem, res.message));
                } else {
                    mainHandler.post(() -> callback.onFinished(localItem, existing.getName() + " zaten indirilmiş."));
                }
                return;
            }

            File dlDir = new File(appContext.getCacheDir(), "wallpaper_downloads");
            ensureWritableDir(dlDir);
            File tempFile = new File(dlDir, RemoteWallpaperCatalog.localBaseNameForRemoteId(item.id) + ".download");
            try {
                downloadUrlToFile(item.imageUrl, tempFile, 40L * 1024L * 1024L,
                        percent -> mainHandler.post(() -> callback.onProgress(percent)));

                String fixedBase = RemoteWallpaperCatalog.localBaseNameForRemoteId(item.id);
                StreamOpener fileOpener = () -> new FileInputStream(tempFile);
                AdaptResult adapted = adaptStreamToSvWallpaperSync(fileOpener, fixedBase, item.title);
                if (adapted.item == null) {
                    mainHandler.post(() -> callback.onFinished(null, adapted.message));
                    return;
                }
                setSelectedPath(adapted.item.filePath);
                if (applyImmediately) {
                    ApplyResult applyRes = applyWallpaperSync(new File(adapted.item.filePath));
                    mainHandler.post(() -> callback.onFinished(adapted.item, applyRes.message));
                } else {
                    mainHandler.post(() -> callback.onFinished(adapted.item, adapted.message));
                }
            } catch (Throwable t) {
                Log.w(TAG, "downloadRemoteWallpaperAsync failed: " + t.getMessage());
                String err = t.getMessage() != null ? t.getMessage() : "İndirme hatası";
                mainHandler.post(() -> callback.onFinished(null, "İndirilemedi: " + err));
            } finally {
                if (tempFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    tempFile.delete();
                }
            }
        });
    }

    private static final class AdaptResult {
        @Nullable
        final WallpaperThemeCatalog.WallpaperItem item;
        @NonNull
        final String message;

        AdaptResult(@Nullable WallpaperThemeCatalog.WallpaperItem item, @NonNull String message) {
            this.item = item;
            this.message = message;
        }
    }

    private void adaptAndSaveStream(
            @NonNull StreamOpener opener,
            @Nullable String fixedBaseName,
            @Nullable String rawName,
            @NonNull ImportCallback callback) {
        AdaptResult res = adaptStreamToSvWallpaperSync(opener, fixedBaseName, rawName);
        mainHandler.post(() -> callback.onImported(res.item, res.message));
    }

    @NonNull
    private AdaptResult adaptStreamToSvWallpaperSync(
            @NonNull StreamOpener opener,
            @Nullable String fixedBaseName,
            @Nullable String rawName) {
        Bitmap adapted = null;
        try {
            int orientation = readExifOrientationFromStream(opener);

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = opener.open()) {
                if (in == null) {
                    return new AdaptResult(null, str(R.string.wallpaper_msg_open_failed));
                }
                BitmapFactory.decodeStream(in, null, bounds);
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return new AdaptResult(null, str(R.string.wallpaper_msg_invalid_image));
            }

            boolean rotated90or270 = isQuarterTurn(orientation);
            int orientedW = rotated90or270 ? bounds.outHeight : bounds.outWidth;
            int orientedH = rotated90or270 ? bounds.outWidth : bounds.outHeight;

            WallpaperImageHelper.Size screen = getTargetDisplaySize();
            WallpaperImageHelper.Size outputSize = WallpaperImageHelper.calculateAdaptedOutputSize(
                    orientedW, orientedH, screen.width, screen.height);

            BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
            decodeOpts.inSampleSize = WallpaperImageHelper.calculateInSampleSize(
                    bounds.outWidth,
                    bounds.outHeight,
                    rotated90or270 ? outputSize.height : outputSize.width,
                    rotated90or270 ? outputSize.width : outputSize.height);
            decodeOpts.inPreferredConfig = Bitmap.Config.ARGB_8888;

            Bitmap rawBitmap;
            try (InputStream in = opener.open()) {
                if (in == null) {
                    return new AdaptResult(null, str(R.string.wallpaper_msg_read_failed));
                }
                rawBitmap = BitmapFactory.decodeStream(in, null, decodeOpts);
            }
            if (rawBitmap == null) {
                return new AdaptResult(null, str(R.string.wallpaper_msg_decode_failed));
            }

            Bitmap orientedBitmap = applyExifOrientation(rawBitmap, orientation);
            if (orientedBitmap != rawBitmap && !rawBitmap.isRecycled()) {
                rawBitmap.recycle();
            }

            adapted = cropAndScaleForDisplay(orientedBitmap, outputSize.width, outputSize.height);
            if (adapted != orientedBitmap && !orientedBitmap.isRecycled()) {
                orientedBitmap.recycle();
            }

            boolean hasAlpha = adapted.hasAlpha();
            File dir = resolveStorageDirectory();
            String targetName = fixedBaseName != null
                    ? (fixedBaseName + (hasAlpha ? ".png" : ".jpg"))
                    : WallpaperImageHelper.sanitizeImportFileName(rawName, System.currentTimeMillis(), hasAlpha);
            File targetFile = new File(dir, targetName);

            boolean saved = writeBitmapFile(adapted, targetFile, hasAlpha);
            if (!saved) {
                return new AdaptResult(null, str(R.string.wallpaper_msg_write_failed));
            }

            setSelectedPath(targetFile.getAbsolutePath());
            WallpaperThemeCatalog.WallpaperItem item = buildWallpaperItem(targetFile);
            String msg = str(R.string.wallpaper_msg_saved, item.formatResolution(), item.formatSize());
            return new AdaptResult(item, msg);
        } catch (Throwable t) {
            Log.e(TAG, "adaptStreamToSvWallpaperSync failed", t);
            String err = t.getMessage() != null ? t.getMessage() : str(R.string.wallpaper_msg_unknown_error);
            return new AdaptResult(null, str(R.string.wallpaper_msg_process_failed, err));
        } finally {
            if (adapted != null && !adapted.isRecycled()) {
                adapted.recycle();
            }
        }
    }

    private static final class ApplyResult {
        final boolean oemApplied;
        final boolean systemApplied;
        @NonNull
        final String message;

        ApplyResult(boolean oemApplied, boolean systemApplied, @NonNull String message) {
            this.oemApplied = oemApplied;
            this.systemApplied = systemApplied;
            this.message = message;
        }
    }

    public void applyWallpaperFile(@NonNull String filePath, @Nullable Callback callback) {
        worker.execute(() -> {
            ApplyResult res = applyWallpaperSync(new File(filePath));
            if (callback != null) {
                mainHandler.post(() -> callback.onComplete(res.oemApplied, res.systemApplied, res.message));
            }
        });
    }

    @NonNull
    private ApplyResult applyWallpaperSync(@NonNull File target) {
        if (!target.exists() || !target.isFile()) {
            return new ApplyResult(false, false, str(R.string.wallpaper_msg_file_not_found, target.getAbsolutePath()));
        }

        boolean oemApplied = false;
        boolean systemApplied = false;
        String errorNote = null;
        Bitmap systemBitmap = null;

        try {
            WallpaperImageHelper.Size screen = getTargetDisplaySize();
            String absPath = target.getAbsolutePath();
            String itemId = "mc_static_" + Integer.toHexString(target.getName().hashCode());

            WallpaperInfo info = new WallpaperInfo(KEY_USER_WALLPAPER, itemId, absPath);
            info.setDayNightImagePath(absPath, absPath);
            info.setUserEditedImagePath(absPath);
            info.setOriginalImagePath(absPath);
            info.setCoverImagePath(absPath);
            info.setFileType(WallpaperInfo.FILE_LOCATION_2D);
            info.setWallpaperStyle(WallpaperStyle.IMAGE);

            if (isOemLauncherInstalled()) {
                oemApplied = pushToSvLauncher(info);
                try {
                    Settings.Global.putString(appContext.getContentResolver(), KEY_USER_WALLPAPER, info.toJson());
                } catch (Throwable ignored) {
                }
            }

            // SVLauncher doğrudan kendi penceresinde duvar kağıdını günceller; araçta ayrıca sistem
            // WallpaperManager'ı tetiklemek Android'in renk/overlay Activity yeniden başlatmasını doğurabilir.
            if (!oemApplied) {
                systemBitmap = decodeCenterCroppedForScreen(absPath, screen.width, screen.height);
                if (systemBitmap != null) {
                    try {
                        WallpaperManager wm = WallpaperManager.getInstance(appContext);
                        try {
                            wm.suggestDesiredDimensions(screen.width, screen.height);
                        } catch (Throwable ignored) {
                        }
                        wm.setBitmap(systemBitmap);
                        systemApplied = true;
                    } catch (Throwable t) {
                        errorNote = t.getMessage();
                        Log.w(TAG, "WallpaperManager.setBitmap failed: " + t.getMessage());
                    }
                } else {
                    errorNote = str(R.string.wallpaper_msg_decode_short);
                }
            }

            if (oemApplied || systemApplied) {
                prefs.edit()
                        .putString(WallpaperThemeCatalog.PREF_SELECTED_PATH, absPath)
                        .putString(WallpaperThemeCatalog.PREF_APPLIED_PATH, absPath)
                        .apply();
            }
        } catch (Throwable t) {
            errorNote = t.getMessage();
            Log.e(TAG, "applyWallpaperSync failed", t);
        } finally {
            if (systemBitmap != null && !systemBitmap.isRecycled()) {
                systemBitmap.recycle();
            }
        }

        final String msg;
        if (oemApplied) {
            msg = str(R.string.wallpaper_msg_applied_oem, target.getName());
        } else if (systemApplied) {
            msg = isOemLauncherInstalled()
                    ? str(R.string.wallpaper_msg_applied_system_fallback, target.getName())
                    : str(R.string.wallpaper_msg_applied_system, target.getName());
        } else {
            msg = errorNote != null
                    ? str(R.string.wallpaper_msg_apply_failed_reason, errorNote)
                    : str(R.string.wallpaper_msg_apply_failed);
        }
        return new ApplyResult(oemApplied, systemApplied, msg);
    }

    public void deleteWallpaperFile(@NonNull String filePath, @Nullable DeleteCallback callback) {
        worker.execute(() -> {
            File storageDir = resolveStorageDirectory();
            File target = new File(filePath);
            if (!WallpaperImageHelper.isInsideDirectory(storageDir, target)) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onDeleted(false, false, str(R.string.wallpaper_msg_delete_outside)));
                }
                return;
            }
            if (!target.exists() || !target.isFile()) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onDeleted(false, false, str(R.string.wallpaper_msg_delete_missing)));
                }
                return;
            }

            String absPath = target.getAbsolutePath();
            boolean wasActive = absPath.equals(getAppliedPath());
            boolean deleted = target.delete();
            if (!deleted) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onDeleted(false, wasActive, str(R.string.wallpaper_msg_delete_failed)));
                }
                return;
            }

            if (isOemLauncherInstalled()) {
                removeFromSvLauncher(target.getName(), absPath);
            }

            thumbnailCache.remove(absPath);
            SharedPreferences.Editor editor = prefs.edit();
            if (absPath.equals(getSelectedPath())) {
                editor.remove(WallpaperThemeCatalog.PREF_SELECTED_PATH);
            }
            if (wasActive) {
                editor.remove(WallpaperThemeCatalog.PREF_APPLIED_PATH);
            }
            editor.apply();

            if (wasActive) {
                restoreDefaultInternal();
            }

            String msg = wasActive
                    ? str(R.string.wallpaper_msg_deleted_restored, target.getName())
                    : str(R.string.wallpaper_msg_deleted, target.getName());
            if (callback != null) {
                mainHandler.post(() -> callback.onDeleted(true, wasActive, msg));
            }
        });
    }

    public void restoreOemDefault(@Nullable Callback callback) {
        worker.execute(() -> {
            boolean[] res = restoreDefaultInternal();
            boolean finalOem = res[0];
            boolean finalSys = res[1];
            prefs.edit().remove(WallpaperThemeCatalog.PREF_APPLIED_PATH).apply();

            String msg = finalOem
                    ? str(R.string.wallpaper_msg_restored_oem)
                    : str(R.string.wallpaper_msg_restored_system);
            if (callback != null) {
                mainHandler.post(() -> callback.onComplete(finalOem, finalSys, msg));
            }
        });
    }

    @NonNull
    private boolean[] restoreDefaultInternal() {
        boolean oemRestored = false;
        boolean sysCleared = false;

        if (isOemLauncherInstalled()) {
            ILauncherAidlInterface aidl = ensureLauncherBoundAndWait();
            if (aidl != null) {
                try {
                    aidl.switchWallpaperStyle(0);
                    oemRestored = true;
                } catch (RemoteException e) {
                    Log.w(TAG, "switchWallpaperStyle(0) failed: " + e.getMessage());
                }
            }
        }
        try {
            WallpaperManager.getInstance(appContext).clear();
            sysCleared = true;
        } catch (Throwable t) {
            Log.w(TAG, "WallpaperManager.clear failed: " + t.getMessage());
        }
        return new boolean[]{oemRestored, sysCleared};
    }

    @NonNull
    private List<WallpaperThemeCatalog.WallpaperItem> scanWallpapersInternal() {
        File dir = resolveStorageDirectory();
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        List<WallpaperThemeCatalog.WallpaperItem> items = new ArrayList<>();
        for (File file : files) {
            if (!file.isFile() || !WallpaperImageHelper.isSupportedImageFilename(file.getName())) {
                continue;
            }
            items.add(buildWallpaperItem(file));
        }
        return items;
    }

    @NonNull
    private WallpaperThemeCatalog.WallpaperItem buildWallpaperItem(@NonNull File file) {
        int[] dims = readOrientedDimensionsFromFile(file);
        return new WallpaperThemeCatalog.WallpaperItem(
                file.getAbsolutePath(),
                file.getName(),
                dims[0],
                dims[1],
                file.length(),
                file.lastModified());
    }

    @NonNull
    private int[] readOrientedDimensionsFromFile(@NonNull File file) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        int w = opts.outWidth;
        int h = opts.outHeight;
        int orientation = readExifOrientationFromFile(file);
        if (isQuarterTurn(orientation)) {
            return new int[]{h, w};
        }
        return new int[]{w, h};
    }

    @Nullable
    private Bitmap decodeOrientedSampledBitmapFromFile(
            @NonNull String filePath,
            int maxWidth,
            int maxHeight) {
        try {
            File file = new File(filePath);
            if (!file.isFile()) {
                return null;
            }
            int orientation = readExifOrientationFromFile(file);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filePath, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            boolean swapped = isQuarterTurn(orientation);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = WallpaperImageHelper.calculateInSampleSize(
                    bounds.outWidth,
                    bounds.outHeight,
                    swapped ? maxHeight : maxWidth,
                    swapped ? maxWidth : maxHeight);
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap decoded = BitmapFactory.decodeFile(filePath, opts);
            if (decoded == null) {
                return null;
            }
            Bitmap oriented = applyExifOrientation(decoded, orientation);
            if (oriented != decoded && !decoded.isRecycled()) {
                decoded.recycle();
            }
            return oriented;
        } catch (Throwable t) {
            Log.w(TAG, "decodeThumbnail failed: " + t.getMessage());
            return null;
        }
    }

    @Nullable
    private Bitmap decodeCenterCroppedForScreen(
            @NonNull String filePath,
            int screenWidth,
            int screenHeight) {
        try {
            File file = new File(filePath);
            int orientation = readExifOrientationFromFile(file);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filePath, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            boolean swapped = isQuarterTurn(orientation);
            int orientedW = swapped ? bounds.outHeight : bounds.outWidth;
            int orientedH = swapped ? bounds.outWidth : bounds.outHeight;

            WallpaperImageHelper.Size target = WallpaperImageHelper.calculateAdaptedOutputSize(
                    orientedW, orientedH, screenWidth, screenHeight);

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = WallpaperImageHelper.calculateInSampleSize(
                    bounds.outWidth,
                    bounds.outHeight,
                    swapped ? target.height : target.width,
                    swapped ? target.width : target.height);
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap decoded = BitmapFactory.decodeFile(filePath, opts);
            if (decoded == null) {
                return null;
            }
            Bitmap oriented = applyExifOrientation(decoded, orientation);
            if (oriented != decoded && !decoded.isRecycled()) {
                decoded.recycle();
            }
            Bitmap finalBmp = cropAndScaleForDisplay(oriented, target.width, target.height);
            if (finalBmp != oriented && !oriented.isRecycled()) {
                oriented.recycle();
            }
            return finalBmp;
        } catch (Throwable t) {
            Log.w(TAG, "decodeCenterCroppedForScreen failed: " + t.getMessage());
            return null;
        }
    }

    @NonNull
    private static Bitmap cropAndScaleForDisplay(
            @NonNull Bitmap source,
            int targetWidth,
            int targetHeight) {
        WallpaperImageHelper.CropRect crop = WallpaperImageHelper.calculateCenterCropRect(
                source.getWidth(), source.getHeight(), targetWidth, targetHeight);

        if (crop.left == 0 && crop.top == 0
                && crop.width == targetWidth && crop.height == targetHeight) {
            return source;
        }

        Bitmap out = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        out.setHasAlpha(source.hasAlpha());
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        Rect srcRect = new Rect(crop.left, crop.top, crop.left + crop.width, crop.top + crop.height);
        Rect dstRect = new Rect(0, 0, targetWidth, targetHeight);
        canvas.drawBitmap(source, srcRect, dstRect, paint);
        return out;
    }

    private static int readExifOrientationFromFile(@NonNull File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            ExifInterface exif = new ExifInterface(fis);
            return exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
        } catch (Throwable ignored) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static int readExifOrientationFromStream(@NonNull StreamOpener opener) {
        try (InputStream in = opener.open()) {
            if (in == null) {
                return ExifInterface.ORIENTATION_NORMAL;
            }
            ExifInterface exif = new ExifInterface(in);
            return exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
        } catch (Throwable ignored) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private interface ProgressListener {
        void onProgress(int percent);
    }

    @NonNull
    private static List<RemoteWallpaperCatalog.RemoteItem> readCatalogCache(@NonNull File cacheFile) {
        if (!cacheFile.isFile() || cacheFile.length() == 0) {
            return Collections.emptyList();
        }
        try (FileInputStream fis = new FileInputStream(cacheFile);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            String json = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            return RemoteWallpaperCatalog.parseCatalogJson(json).items;
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    private static void writeUtf8File(@NonNull File target, @NonNull String content) {
        try (FileOutputStream fos = new FileOutputStream(target)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (Throwable ignored) {
        }
    }

    @NonNull
    private static String httpGetText(@NonNull String urlStr, int maxBytes) throws IOException {
        HttpURLConnection conn = openHttpConnection(urlStr);
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code);
            }
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                int total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > maxBytes) {
                        throw new IOException("Katalog dosyası çok büyük.");
                    }
                    bos.write(buf, 0, n);
                }
                return new String(bos.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static void downloadUrlToFile(
            @NonNull String urlStr,
            @NonNull File target,
            long maxBytes,
            @Nullable ProgressListener progressListener) throws IOException {
        File tmp = new File(target.getParentFile(), target.getName() + ".part");
        HttpURLConnection conn = openHttpConnection(urlStr);
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code);
            }
            long expected = conn.getContentLengthLong();
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream fos = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                long total = 0;
                int lastPct = -1;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > maxBytes) {
                        throw new IOException("Dosya boyutu limiti aşıldı.");
                    }
                    fos.write(buf, 0, n);
                    if (progressListener != null && expected > 0) {
                        int pct = (int) Math.min(100, (total * 100L) / expected);
                        if (pct != lastPct) {
                            lastPct = pct;
                            progressListener.onProgress(pct);
                        }
                    }
                }
                fos.flush();
            }
            if (target.exists()) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
            }
            if (!tmp.renameTo(target)) {
                throw new IOException("Geçici dosya taşınamadı.");
            }
        } catch (IOException e) {
            if (tmp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
            throw e;
        } finally {
            conn.disconnect();
        }
    }

    @NonNull
    private static HttpURLConnection openHttpConnection(@NonNull String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "MapControl/1.3.4");
        return conn;
    }

    private static boolean isQuarterTurn(int exifOrientation) {
        return exifOrientation == ExifInterface.ORIENTATION_ROTATE_90
                || exifOrientation == ExifInterface.ORIENTATION_ROTATE_270
                || exifOrientation == ExifInterface.ORIENTATION_TRANSPOSE
                || exifOrientation == ExifInterface.ORIENTATION_TRANSVERSE;
    }

    @NonNull
    private static Bitmap applyExifOrientation(@NonNull Bitmap source, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90f);
                break;
            default:
                return source;
        }
        try {
            return Bitmap.createBitmap(
                    source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        } catch (Throwable t) {
            return source;
        }
    }

    @Nullable
    private String queryDisplayName(@NonNull Uri uri) {
        try {
            ContentResolver cr = appContext.getContentResolver();
            try (Cursor cursor = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        return cursor.getString(idx);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return uri.getLastPathSegment();
    }

    private boolean pushToSvLauncher(@NonNull WallpaperInfo info) {
        ILauncherAidlInterface aidl = ensureLauncherBoundAndWait();
        if (aidl == null) {
            return false;
        }
        try {
            aidl.setWallpaper(info);
            return true;
        } catch (RemoteException e) {
            Log.w(TAG, "aidl.setWallpaper failed: " + e.getMessage());
            try {
                aidl.addWallpaper(info);
                return true;
            } catch (RemoteException ignored) {
                return false;
            }
        }
    }

    private void removeFromSvLauncher(@NonNull String fileName, @NonNull String absPath) {
        ILauncherAidlInterface aidl = ensureLauncherBoundAndWait();
        if (aidl == null) {
            return;
        }
        String itemId = "mc_static_" + Integer.toHexString(fileName.hashCode());
        WallpaperInfo info = new WallpaperInfo(KEY_USER_WALLPAPER, itemId, absPath);
        info.setUserEditedImagePath(absPath);
        info.setOriginalImagePath(absPath);
        try {
            aidl.deleteWallpaperInfo(info);
        } catch (Throwable ignored) {
        }
        try {
            aidl.deleteLauncherWallpaper(itemId);
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    private ILauncherAidlInterface ensureLauncherBoundAndWait() {
        ILauncherAidlInterface current = launcherAidl;
        if (current != null && current.asBinder().isBinderAlive()) {
            return current;
        }
        CountDownLatch latch;
        synchronized (aidlLock) {
            current = launcherAidl;
            if (current != null && current.asBinder().isBinderAlive()) {
                return current;
            }
            latch = new CountDownLatch(1);
            bindLatch = latch;
        }
        ensureLauncherBound();
        try {
            latch.await(1500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return launcherAidl;
    }

    private void ensureLauncherBound() {
        if (!isOemLauncherInstalled()) {
            return;
        }
        synchronized (aidlLock) {
            if (serviceBound && launcherAidl != null && launcherAidl.asBinder().isBinderAlive()) {
                return;
            }
        }
        try {
            Intent explicit = new Intent();
            explicit.setClassName(OEM_LAUNCHER_PKG, OEM_LAUNCHER_SERVICE);
            boolean bound = appContext.bindService(explicit, launcherConnection, Context.BIND_AUTO_CREATE);
            if (!bound) {
                Intent byAction = new Intent(OEM_LAUNCHER_ACTION);
                byAction.setPackage(OEM_LAUNCHER_PKG);
                bound = appContext.bindService(byAction, launcherConnection, Context.BIND_AUTO_CREATE);
            }
            synchronized (aidlLock) {
                serviceBound = bound;
            }
        } catch (Throwable t) {
            Log.w(TAG, "bindService SVLauncher failed: " + t.getMessage());
        }
    }

    private void unbindLauncher() {
        synchronized (aidlLock) {
            if (serviceBound) {
                try {
                    appContext.unbindService(launcherConnection);
                } catch (Throwable ignored) {
                }
                serviceBound = false;
            }
            launcherAidl = null;
        }
    }

    @NonNull
    private File resolveStorageDirectory() {
        File oemDir = new File(OEM_WALLPAPER_DIR);
        if (ensureWritableDir(oemDir)) {
            return oemDir;
        }
        File extRoot = Environment.getExternalStorageDirectory();
        if (extRoot != null) {
            File svDir = new File(extRoot, "SVWallpaper");
            if (ensureWritableDir(svDir)) {
                return svDir;
            }
        }
        File appExt = appContext.getExternalFilesDir("SVWallpaper");
        if (appExt != null && ensureWritableDir(appExt)) {
            return appExt;
        }
        File internal = new File(appContext.getFilesDir(), "SVWallpaper");
        ensureWritableDir(internal);
        return internal;
    }

    private static boolean ensureWritableDir(@NonNull File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            return false;
        }
        return dir.isDirectory() && dir.canWrite();
    }

    private static boolean writeBitmapFile(
            @NonNull Bitmap bitmap,
            @NonNull File target,
            boolean asPng) {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            Bitmap.CompressFormat fmt = asPng ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
            int quality = asPng ? 100 : 94;
            bitmap.compress(fmt, quality, fos);
            fos.flush();
            if (target.exists()) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
            }
            return tmp.renameTo(target);
        } catch (Throwable t) {
            Log.w(TAG, "writeBitmapFile failed for " + target.getAbsolutePath() + ": " + t.getMessage());
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
    }
}
