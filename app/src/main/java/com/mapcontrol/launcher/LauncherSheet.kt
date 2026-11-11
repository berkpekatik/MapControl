package com.mapcontrol.launcher

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import androidx.annotation.StringRes
import com.mapcontrol.R

/**
 * Launcher kartı görünümünde, ekranın ortasında bir panel: başlık, kapat düğmesi ve [build] ile kurulan içerik.
 * Ayarlar ve "Widget ekle" bunu kullanır; sistem diyaloğu yerine launcher'ın kendi renk ve ölçüleri.
 */
fun showLauncherSheet(
    activity: Activity,
    @StringRes title: Int,
    widthDp: Int = SHEET_WIDTH_DP,
    fullScreen: Boolean = false,
    edgeToEdge: Boolean = false,
    header: (LinearLayout.(Dialog) -> Unit)? = null,
    build: LinearLayout.(Dialog) -> Unit,
): Dialog {
    val dialog = Dialog(activity)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    // Kenarsız tam ekran (yalnız [edgeToEdge] isteyenler, ör. yuva seçici): köşesiz ve daha dar kenarlı; tüm pencereyi
    // (alt çubuğun üstü dahil) kaplar, arkası görünmez. Diğer tam ekran sayfalar (ör. veri denetimi) eski görünümünde kalır.
    val edge = fullScreen && edgeToEdge
    val panel = card(activity, paddingDp = if (edge) FULL_SCREEN_PAD_DP else LauncherTheme.PADDING_DP).apply {
        if (edge) background = rounded(activity, LauncherTheme.CARD, 0f)
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(text(activity, LauncherTheme.SECTION_SP, bold = true).apply { setText(title) },
                weightLp(1f).apply { height = ViewGroup.LayoutParams.WRAP_CONTENT })
            // Başlık satırına ek öğeler (ör. seçicide "Şu an: X" ve "Kaldır"): kapat düğmesinden önce.
            header?.invoke(this, dialog)
            addView(iconButton(activity, R.drawable.ic_mdi_close, activity.getString(R.string.launcher_close)) { dialog.dismiss() })
        }, sheetFullWidth())
        addView(gap(activity, if (edge) FULL_SCREEN_HEADER_GAP_DP else 24, vertical = true))
        build(dialog)
    }
    dialog.setContentView(panel)
    dialog.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (fullScreen) {
            // Kenar payı kalmasın: pencere tüm ekranı (alt çubuğun üstü dahil) kaplar.
            if (edge) decorView.setPadding(0, 0, 0, 0)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        // Ekrandan geniş istenen sayfa kenarlarda pay bırakacak kadar daralır (dikey ekranda 1440 dp'lik araç detayı taşıyordu).
        else setLayout(sheetWidthPx(activity.dp(widthDp), activity.resources.displayMetrics.widthPixels, activity.dp(SHEET_EDGE_DP)),
            ViewGroup.LayoutParams.WRAP_CONTENT)
        setGravity(Gravity.CENTER)
    }
    dialog.hideOemBarIfNeeded(activity)
    dialog.show()
    return dialog
}

/** Sayfanın kenarlarda en az bırakacağı pay (dp). */
internal const val SHEET_EDGE_DP = 24

/** İstenen sayfa genişliği; ekrana ([screenPx]) iki yanda [edgePx] payla sığmıyorsa sığan genişlik (saf). */
internal fun sheetWidthPx(wantedPx: Int, screenPx: Int, edgePx: Int): Int =
    if (screenPx <= 0) wantedPx else minOf(wantedPx, maxOf(0, screenPx - 2 * edgePx))

/**
 * Diyalog penceresi açılınca sistem gezinme çubuğunu geri getirmesin: "Bizimki"/"Gizli" alt çubuk kipinde
 * aktivitedekiyle aynı gizleme (kaydırınca geçici görünür). Tüm sayfa/diyalog açılışları buradan geçer.
 * [Dialog.show]'dan ÖNCE çağrılır (pencere zaten oluşturulur).
 */
fun Dialog.hideOemBarIfNeeded(context: android.content.Context) {
    if (!LauncherBottomBar.hidesOemBar(context)) return
    val w = window ?: return
    if (android.os.Build.VERSION.SDK_INT >= 30) {
        w.decorView // pencereyi oluştur
        w.insetsController?.let {
            it.hide(android.view.WindowInsets.Type.navigationBars())
            it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        @Suppress("DEPRECATION")
        w.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}

fun sheetFullWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

private const val SHEET_WIDTH_DP = 760

/** Tam ekran sayfanın kenar boşluğu ve başlık altı boşluğu (seçici ızgarası bunlarla hesaplanır). */
const val FULL_SCREEN_PAD_DP = 24
const val FULL_SCREEN_HEADER_GAP_DP = 16
