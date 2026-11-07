package com.mapcontrol.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mapcontrol.R

/**
 * Uygulama kutucuklarında gösterilen ad ve ikon kuralları.
 * Araç üreticisinin (Desay) sistem uygulamalarında etiket ham paket adı, ikon da genel Android ikonu olabilir;
 * bilinen paketler için kendi adımız ve ikonumuz kullanılır, diğerleri için ad yedeklemesi uygulanır.
 */
object AppIdentity {

    /** Bilinen bir paketin kendi adı ve ikonu; [icon] 0 ise yalnız ad değişir, gerçek uygulama ikonu korunur. */
    class Override(@StringRes val label: Int, @DrawableRes val icon: Int = 0)

    /** Etiketi ve ikonu araçta çirkin görünen paketler. */
    val OVERRIDES: Map<String, Override> = mapOf(
        "com.desaysv.vehiclesetting" to Override(R.string.launcher_app_vehicle_settings, R.drawable.ic_mdi_car_cog),
        "com.desaysv.bluetooth.phone" to Override(R.string.launcher_app_phone, R.drawable.ic_mdi_phone),
        // FrostNav'ın ikonu kendi markası: yalnız ad yazılır, ikon olduğu gibi kalır.
        FrostNavLink.PRIMARY_PACKAGE to Override(R.string.launcher_app_frostnav),
    )

    /** Etiket paket adının kendisi ya da ona benziyor mu: boş, ya da noktalı ve boşluksuz. */
    fun looksLikePackage(label: String, packageName: String): Boolean {
        val l = label.trim()
        return l.isEmpty() || l == packageName || (l.contains('.') && l.none { it.isWhitespace() })
    }

    /** Paket adının son parçasından okunur bir ad: "com.x.my_app" -> "My app". */
    fun prettifyPackage(packageName: String): String {
        val last = packageName.substringAfterLast('.').ifEmpty { packageName }
        val words = last.replace('_', ' ').replace('-', ' ').trim()
        return words.replaceFirstChar { it.uppercase() }.ifEmpty { packageName }
    }

    /**
     * Gösterilecek ad: [override] (bilinen paketin kendi adı) varsa o; etiket paket adına benziyorsa
     * paket adının son parçası; aksi halde uygulamanın kendi etiketi.
     */
    fun displayLabel(packageName: String, rawLabel: String, override: String? = null): String = when {
        override != null -> override
        looksLikePackage(rawLabel, packageName) -> prettifyPackage(packageName)
        else -> rawLabel.trim()
    }

    /** [packageName] için etiket; [overrides] tablosundaki paketler kendi adını alır. */
    fun label(context: Context, packageName: String, rawLabel: String): String =
        displayLabel(packageName, rawLabel, OVERRIDES[packageName]?.let { context.getString(it.label) })

    /**
     * Bilinen paketin kendi ikonu: CONTROL plakası üzerinde metin renginde vektör.
     * Listede olmayan paketler için null; gerçek uygulama ikonu korunur.
     */
    fun overrideIcon(context: Context, packageName: String, sizePx: Int): Drawable? {
        val o = OVERRIDES[packageName]?.takeIf { it.icon != 0 } ?: return null
        val vector = context.getDrawable(o.icon)?.mutate() ?: return null
        val size = Math.max(1, sizePx)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LauncherTheme.CONTROL }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * 0.28f, size * 0.28f, paint)
        val pad = Math.round(size * 0.2f)
        vector.setTint(LauncherTheme.TEXT)
        vector.setBounds(pad, pad, size - pad, size - pad)
        vector.draw(canvas)
        return BitmapDrawable(context.resources, bitmap)
    }
}
