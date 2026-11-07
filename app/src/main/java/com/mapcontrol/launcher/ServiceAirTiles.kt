package com.mapcontrol.launcher

import android.content.Context
import com.mapcontrol.R
import java.text.NumberFormat
import java.util.Locale

/**
 * Hava kalitesi: kabin içi PM2.5'e göre kelime ("İyi" <= 35, "Orta" <= 75, "Kötü" > 75 µg/m³), geniş boyutta
 * sayılar (2x2'de ortalı: kelime büyük, altında "PM2.5" satırı). Dışarısı "Kötü" ve içerisi daha iyiyse yalnızca metin ipucu: "İç sirkülasyon önerilir" (araca bir şey gönderilmez).
 */
internal class AirQualityTile(context: Context) :
    CenteredTripTile(context, R.drawable.ic_mdi_air_filter, R.string.launcher_widget_air) {

    override fun bind() {
        val inside = data.pm25In
        val outside = data.pm25Out
        if (!data.connected || inside == null) {
            showMissing()
            return
        }
        val poor = grade(inside) == POOR
        setIcon(if (poor) R.drawable.ic_mdi_alert else R.drawable.ic_mdi_air_filter,
            if (poor) LauncherTheme.ALERT else LauncherTheme.TEXT_SECONDARY)
        setValue(gradeLabel(inside), if (poor) LauncherTheme.ALERT else LauncherTheme.TEXT)
        val outPoor = outside != null && grade(outside) == POOR && outside > inside
        val lines = listOfNotNull(
            context.getString(R.string.launcher_air_in, inside),
            when {
                outside == null -> null
                outPoor -> context.getString(R.string.launcher_air_out_poor, outside)
                else -> context.getString(R.string.launcher_air_out, outside)
            },
            if (outPoor) context.getString(R.string.launcher_air_recirc_hint) else null,
        )
        // Geniş: en çok 2 satır (dış hava kötüyse içeri satırı ipucuna yer açar). Dar: ortalı tek küçük satır, yalnızca iç PM2.5.
        val shown = if (outPoor) lines.drop(1) else lines
        showDetail(if (tier >= 2) shown.joinToString("\n") else context.getString(R.string.launcher_air_pm, inside))
        // Geniş boyutta kelimenin yanına üç dilimli ölçek: iyi / orta / kötü; dolu dilim sayısı derece + 1.
        showSegments(grade(inside) + 1, GRADES, if (poor) LauncherTheme.ALERT else LauncherTheme.ACCENT)
    }

    private fun grade(pm: Int) = AirGrade.of(pm)

    private fun gradeLabel(pm: Int) = context.getString(when (grade(pm)) {
        GOOD -> R.string.launcher_air_good
        MODERATE -> R.string.launcher_air_moderate
        else -> R.string.launcher_air_poor
    })

    private companion object {
        const val GRADES = 3
        const val GOOD = AirGrade.GOOD
        const val MODERATE = AirGrade.MODERATE
        const val POOR = AirGrade.POOR
    }
}
