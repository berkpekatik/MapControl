package com.mapcontrol.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Outline
import android.media.ExifInterface
import android.os.Environment
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.mapcontrol.R
import java.io.File
import java.util.Locale

/**
 * Fotoğraf widget'ının saf (Android'siz) mantığı; birim testle doğrulanır.
 * FrostNav web paneli dosyaları cihazın İndirilenler klasörüne yükler; buradan yalnızca resimler seçilir.
 */
object PhotoFiles {
    /** Seçicide en fazla bu kadar küçük resim listelenir (yavaş ünitede çözme sayısı sınırlı kalsın). */
    const val MAX_LISTED = 60

    private val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /** Listelenen bir dosya: yol, ad ve son değişiklik zamanı (ms). */
    data class Entry(val path: String, val name: String, val modified: Long)

    /** Ad jpg/jpeg/png/webp ile bitiyor mu (büyük/küçük harf fark etmez); gizli ("." ile başlayan) dosyalar hariç. */
    fun isPhotoName(name: String): Boolean {
        if (name.startsWith(".")) return false
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return false
        return name.substring(dot + 1).lowercase(Locale.ROOT) in EXTENSIONS
    }

    /** Yalnızca resim adlarını bırakır, en yeniden eskiye sıralar (eşitlikte ad sırası) ve [limit] ile keser. */
    fun filterAndSort(entries: List<Entry>, limit: Int = MAX_LISTED): List<Entry> =
        entries.filter { isPhotoName(it.name) }
            .sortedWith(compareByDescending<Entry> { it.modified }.thenBy { it.name.lowercase(Locale.ROOT) })
            .take(limit)

    /** Kopyalanan fotoğrafın dosya adı: profile ve kutucuğa özel, güvenli karakterlerle. */
    fun storedName(profileId: Int, tileId: String): String {
        val safe = tileId.map { if (it.isLetterOrDigit() || it == '_' || it == '-') it else '_' }.joinToString("")
        return "photo_p${profileId}_$safe"
    }

    /**
     * Çözme sırasında kullanılacak 2'nin kuvveti örnekleme: sonuç hedefin (en az) [reqW] × [reqH] kalır,
     * böylece kırpma sonrası keskin görünür; hedeften büyük en küçük kuvvet seçilir.
     */
    fun sampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        if (srcW <= 0 || srcH <= 0 || reqW <= 0 || reqH <= 0) return 1
        var s = 1
        while (srcW / (s * 2) >= reqW && srcH / (s * 2) >= reqH) s *= 2
        return s
    }
}

/** Çözülmüş küçük resimler için paylaşılan önbellek (anahtar: yol + değişiklik zamanı + uzunluk + hedef boyut). */
private object PhotoCache {
    private const val CACHE_BYTES = 12 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun key(f: File, w: Int, h: Int) = "${f.path}|${f.lastModified()}|${f.length()}|${w}x$h"
    fun get(k: String): Bitmap? = cache.get(k)
    fun put(k: String, b: Bitmap) { cache.put(k, b) }

    /** Dosya bir işlev tarafından değiştirilince eski ölçülerin kalmaması için. */
    fun clear() = cache.evictAll()

}

/** Görüntü dosyalarını örnekleyerek çözer. Arka plan iş parçacığında çağrılmalıdır. */
private object PhotoDecoder {
    /** Dosyanın boyutları; çözülemiyorsa null. */
    fun bounds(f: File): IntArray? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        return if (o.outWidth > 0 && o.outHeight > 0) intArrayOf(o.outWidth, o.outHeight) else null
    }

    fun decode(f: File, reqW: Int, reqH: Int, rotate: Boolean, lowQuality: Boolean): Bitmap? {
        val key = PhotoCache.key(f, reqW, reqH)
        PhotoCache.get(key)?.let { return it }
        val b = bounds(f) ?: return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = PhotoFiles.sampleSize(b[0], b[1], reqW, reqH)
            if (lowQuality) inPreferredConfig = Bitmap.Config.RGB_565
        }
        var bmp = try {
            BitmapFactory.decodeFile(f.path, opts)
        } catch (e: OutOfMemoryError) {
            null
        } ?: return null
        if (rotate) bmp = applyExif(f, bmp)
        PhotoCache.put(key, bmp)
        return bmp
    }

    private fun applyExif(f: File, src: Bitmap): Bitmap = try {
        val deg = when (ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (deg == 0f) src else {
            val m = Matrix().apply { postRotate(deg) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        }
    } catch (e: Exception) {
        src
    } catch (e: OutOfMemoryError) {
        src
    }
}

/**
 * Fotoğraf widget'ı (2×2, 4×2, 4×4): seçilen resmi kartın yuvarlak köşeleriyle, ortadan kırparak gösterir.
 * Resim yoksa "Fotoğraf ekle" boş durumu görünür; dokunmak İndirilenler'deki resimleri listeleyen seçiciyi açar.
 * Seçilen dosya uygulamanın kendi klasörüne kopyalanır (profil başına), böylece İndirilenler temizlense de kalır.
 * Uzun basış: değiştir ya da kaldır. Çözme ve kopyalama [LauncherIo] üzerinde, önbellekli ve örneklemelidir.
 */
class PhotoTile(
    private val activity: Activity,
    private val tileId: String,
) : SizedWidget, Themeable {

    private val context: Context = activity
    private val executor = LauncherIo.serial()

    /** Tema bağlı atamalar (kurucuda bir kez, [applyTheme]'de yeniden); bkz. [ThemeBindings]. */
    private val theme = ThemeBindings()

    /** Her yenilemede artar; eski arka plan sonuçları bu değişmişse yok sayılır. */
    private var generation = 0
    private var hasPhoto = false

    private val radius = context.dpF(LauncherTheme.CARD_RADIUS_DP)

    private val imageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        visibility = View.GONE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private val emptyIcon = icon(context, R.drawable.ic_mdi_image, EMPTY_ICON_DP).also { v -> theme.bind { v.setTint(LauncherTheme.TEXT_SECONDARY) } }
    private val emptyTitle = text(context, LauncherTheme.LABEL_SP, medium = true).apply {
        setText(R.string.launcher_photo_add)
        gravity = Gravity.CENTER
        theme.bind { setTextColor(LauncherTheme.TEXT) }
    }
    private val emptyHint = text(context, HINT_SP, maxLines = 2).apply {
        setText(R.string.launcher_photo_hint)
        gravity = Gravity.CENTER
        theme.bind { setTextColor(LauncherTheme.TEXT_SECONDARY) }
    }
    private val emptyBox = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        val p = context.dp(12)
        setPadding(p, p, p, p)
        addView(emptyIcon)
        addView(gap(context, 8, vertical = true))
        addView(emptyTitle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(gap(context, 4, vertical = true))
        addView(emptyHint, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private val root = FrameLayout(context).apply {
        theme.bind { background = cardSurface(context) }
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, radius)
            }
        }
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.launcher_widget_photo)
        addView(imageView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(emptyBox, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setOnClickListener { if (!hasPhoto) openPicker() }
        setOnLongClickListener {
            if (hasPhoto) openChangeSheet() else openPicker()
            true
        }
        pressFeedback()
    }

    override val view: View = root

    /** Tema değişti: boş durumun yüzeyi, ikonu ve yazıları yeniden atanır; yüklü fotoğraf dokunulmaz. */
    override fun applyTheme() = theme.reapply()

    /** Dar kutucukta (2 sütun) ipucu gizlenir ve ikon küçülür; 4 sütunda tam boy. */
    override fun applySpan(span: Span) {
        val narrow = span.w < 4
        val iconPx = context.dp(if (narrow) EMPTY_ICON_NARROW_DP else EMPTY_ICON_DP)
        emptyIcon.layoutParams = LinearLayout.LayoutParams(iconPx, iconPx)
        emptyHint.visibility = if (narrow) View.GONE else View.VISIBLE
        // Yerleşim değişince çözünürlük hedefi de değişebilir; önbellekten hızlı yeniden bağlanır.
        refresh()
    }

    override fun onVisible() = refresh()

    override fun onHidden() = Unit

    /** Paylaşılan [LauncherIo] havuzu kapatılmaz. Bellekteki resim de önbellekte kalır. */
    override fun release() {
        generation++
    }

    private fun photoFile(): File = File(photoDir(context), PhotoFiles.storedName(Profiles.current(context), tileId))

    /** Kayıtlı fotoğrafı (varsa) arka planda çözer ve gösterir. */
    private fun refresh() {
        val token = ++generation
        executor.runSafely {
            val f = photoFile()
            val bmp = if (f.isFile) PhotoDecoder.decode(f, DISPLAY_W, DISPLAY_H, rotate = true, lowQuality = false) else null
            root.post { if (token == generation) showPhoto(bmp) }
        }
    }

    private fun showPhoto(bmp: Bitmap?) {
        hasPhoto = bmp != null
        imageView.setImageBitmap(bmp)
        imageView.visibility = if (bmp != null) View.VISIBLE else View.GONE
        emptyBox.visibility = if (bmp != null) View.GONE else View.VISIBLE
    }

    /** Uzun basış: değiştir ya da kaldır. */
    private fun openChangeSheet() {
        showLauncherSheet(activity, R.string.launcher_widget_photo, widthDp = 420) { dialog ->
            addView(actionRow(R.drawable.ic_mdi_folder, R.string.launcher_photo_change) {
                dialog.dismiss()
                openPicker()
            }, sheetFullWidth())
            addView(gap(context, 12, vertical = true))
            addView(actionRow(R.drawable.ic_mdi_delete, R.string.launcher_photo_remove) {
                dialog.dismiss()
                removePhoto()
            }, sheetFullWidth())
        }
    }

    private fun actionRow(iconRes: Int, label: Int, onClick: () -> Unit): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(context, LauncherTheme.CONTROL)
        minimumHeight = context.dp(LauncherTheme.TOUCH_MIN_DP)
        val p = context.dp(16)
        setPadding(p, 0, p, 0)
        isClickable = true
        isFocusable = true
        addView(icon(context, iconRes, 28, LauncherTheme.TEXT))
        addView(gap(context, 14))
        addView(text(context, LauncherTheme.BODY_SP).apply { setText(label) })
        setOnClickListener { onClick() }
        pressFeedback()
    }

    private fun removePhoto() {
        val token = ++generation
        executor.runSafely {
            photoFile().delete()
            PhotoCache.clear()
            root.post { if (token == generation) showPhoto(null) }
        }
    }

    /** İndirilenler'deki resimleri küçük resimlerle (en yeni önce) listeler; seçilen kopyalanıp gösterilir. */
    private fun openPicker() {
        ensureReadPermission()
        showLauncherSheet(activity, R.string.launcher_photo_pick_title, widthDp = PICKER_WIDTH_DP) { d ->
            val status = text(context, LauncherTheme.BODY_SP, LauncherTheme.TEXT_SECONDARY, maxLines = 3).apply {
                gravity = Gravity.CENTER
            }
            val grid = GridLayout(context).apply { columnCount = PICKER_COLUMNS }
            val scroll = ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                addView(grid)
            }
            addView(status, sheetFullWidth())
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(PICKER_HEIGHT_DP)))
            addView(gap(context, 12, vertical = true))
            addView(actionRow(R.drawable.ic_mdi_folder, R.string.launcher_photo_refresh) { fillPicker(d, grid, status) }, sheetFullWidth())
            fillPicker(d, grid, status)
            // İzin sonradan verilirse açık seçici kendini yeniler (HomeLauncherActivity.onRequestPermissionsResult).
            pickerRefresh = { if (d.isShowing) fillPicker(d, grid, status) }
        }
    }

    /** İzin yoksa (Android 6+) bir kez ister; sonuç Activity'ye gelir, kullanıcı "Yenile" ile yeniden tarar. */
    private fun ensureReadPermission() {
        if (hasReadPermission(context)) return
        try {
            activity.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQ_STORAGE)
        } catch (e: Exception) {
            android.util.Log.w("MapControlLauncher", "Depolama izni istenemedi", e)
        }
    }

    private fun fillPicker(dialog: Dialog, grid: GridLayout, status: TextView) {
        grid.removeAllViews()
        status.setText(R.string.launcher_photo_loading)
        status.visibility = View.VISIBLE
        executor.runSafely {
            val permitted = hasReadPermission(context)
            val files = if (permitted) listDownloads() else emptyList()
            root.post {
                if (!dialog.isShowing) return@post
                if (files.isEmpty()) {
                    status.setText(if (permitted) R.string.launcher_photo_none else R.string.launcher_photo_permission)
                    return@post
                }
                status.visibility = View.GONE
                files.forEach { entry -> grid.addView(thumb(dialog, entry)) }
            }
        }
    }

    private fun thumb(dialog: Dialog, entry: PhotoFiles.Entry): View {
        val w = context.dp(THUMB_W_DP)
        val h = context.dp(THUMB_H_DP)
        val iv = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(context, LauncherTheme.CONTROL, 12f)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, outline: Outline) {
                    outline.setRoundRect(0, 0, v.width, v.height, context.dpF(12f))
                }
            }
            contentDescription = entry.name
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dialog.dismiss()
                pick(File(entry.path))
            }
            pressFeedback()
        }
        iv.layoutParams = GridLayout.LayoutParams().apply {
            width = w
            height = h
            val m = context.dp(5)
            setMargins(m, m, m, m)
        }
        executor.runSafely {
            val bmp = PhotoDecoder.decode(File(entry.path), THUMB_REQ_W, THUMB_REQ_H, rotate = false, lowQuality = true)
            if (bmp != null) iv.post { iv.setImageBitmap(bmp) }
        }
        return iv
    }

    /** Seçilen dosyayı uygulamanın klasörüne kopyalar (önce geçici dosya, sonra yeniden adlandırma) ve gösterir. */
    private fun pick(source: File) {
        val token = ++generation
        executor.runSafely {
            val ok = try {
                if (PhotoDecoder.bounds(source) == null) false else {
                    val target = photoFile()
                    val tmp = File(target.parentFile, target.name + ".tmp")
                    source.inputStream().use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
                    target.delete()
                    tmp.renameTo(target)
                }
            } catch (e: Exception) {
                android.util.Log.w("MapControlLauncher", "Fotoğraf kopyalanamadı", e)
                false
            }
            PhotoCache.clear()
            if (!ok) {
                root.post { Toast.makeText(context, R.string.launcher_photo_failed, Toast.LENGTH_SHORT).show() }
                return@runSafely
            }
            val bmp = PhotoDecoder.decode(photoFile(), DISPLAY_W, DISPLAY_H, rotate = true, lowQuality = false)
            root.post { if (token == generation) showPhoto(bmp) }
        }
    }

    companion object {
        private const val EMPTY_ICON_DP = 48
        private const val EMPTY_ICON_NARROW_DP = 40
        private const val HINT_SP = 19f
        private const val DISPLAY_W = 1024
        private const val DISPLAY_H = 640
        private const val PICKER_WIDTH_DP = 760
        private const val PICKER_HEIGHT_DP = 330
        private const val PICKER_COLUMNS = 4
        private const val THUMB_W_DP = 168
        private const val THUMB_H_DP = 112
        private const val THUMB_REQ_W = 240
        private const val THUMB_REQ_H = 160
        const val REQ_STORAGE = 4713

        /** Açık fotoğraf seçici varsa onu yeniden tarar; depolama izni verilince Activity çağırır. */
        @Volatile var pickerRefresh: (() -> Unit)? = null

        /** Kopyalanan fotoğrafların klasörü (uygulamaya özel, temizlenmeye karşı güvenli). */
        fun photoDir(context: Context): File = File(context.filesDir, "launcher_photos").apply { mkdirs() }

        @SuppressLint("NewApi")
        fun hasReadPermission(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

        /** İndirilenler klasöründeki resimler (en yeni önce). Klasör bulunamazsa ya da okunamazsa boş liste. */
        fun listDownloads(): List<PhotoFiles.Entry> {
            val dirs = listOfNotNull(
                try { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) } catch (e: Exception) { null },
                File("/storage/emulated/0/Download"),
                File("/sdcard/Download"),
            )
            val dir = dirs.firstOrNull { it.isDirectory } ?: return emptyList()
            val all = (dir.listFiles() ?: return emptyList())
                .filter { it.isFile }
                .map { PhotoFiles.Entry(it.path, it.name, it.lastModified()) }
            return PhotoFiles.filterAndSort(all)
        }
    }
}
