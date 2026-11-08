package com.mapcontrol.launcher

import android.app.Activity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mapcontrol.R

/** Bir kutucuk bu genişlikten (hücre) itibaren "geniş" yerleşime geçer. */
const val WIDE_MIN_COLS = 4

/** Bir widget'ın kurulduğu boyut (sütun × satır hücre); Sahne panelde en büyük, şeritte ilk boyutu kullanır. */
data class Span(val w: Int, val h: Int) {
    override fun toString() = "${w}×$h"
}

/**
 * Widget türleri ve her birinin desteklediği boyutlar. İlk boyut Sahne şeridinde ([SahneForm.stripSpan]), en büyük alanlı
 * boyut Sahne panelinde ([SahneForm.panelSpan]) kullanılır. Sahne'de gösterilmeyen türler (ör. sağlık, hava, saat)
 * eski Sahne kayıtlarının çözülüp yedeğe dönmesi için listede kalır ([SahneSlots.decode]).
 */
enum class WidgetType(val key: String, @StringRes val label: Int, @DrawableRes val icon: Int, val sizes: List<Span>) {
    VEHICLE("vehicle", R.string.launcher_widget_vehicle, R.drawable.ic_mdi_car, listOf(Span(4, 4))),
    CONSUMPTION("consumption", R.string.launcher_widget_consumption, R.drawable.ic_mdi_leaf, listOf(Span(2, 2))),
    TRIP("trip", R.string.launcher_widget_trip, R.drawable.ic_mdi_road_variant, listOf(Span(2, 2), Span(3, 2), Span(4, 4))),
    HEALTH("health", R.string.launcher_widget_health, R.drawable.ic_mdi_lightning_bolt, listOf(Span(2, 2), Span(4, 4))),
    AIR("air", R.string.launcher_widget_air, R.drawable.ic_mdi_air_filter, listOf(Span(2, 2), Span(4, 2))),
    APP("app", R.string.launcher_widget_app, R.drawable.ic_mdi_view_grid, listOf(Span(1, 2))),
    SCREEN_OFF("screen_off", R.string.launcher_widget_screen_off, R.drawable.ic_mdi_weather_night, listOf(Span(2, 2), Span(1, 2))),
    BREAK_REMINDER("break_reminder", R.string.launcher_widget_break, R.drawable.ic_mdi_timer_sand, listOf(Span(2, 2))),
    PHONE_REMINDER("phone_reminder", R.string.launcher_widget_phone, R.drawable.ic_mdi_cellphone, listOf(Span(2, 2))),
    CLOCK("clock", R.string.launcher_widget_clock, R.drawable.ic_mdi_clock_outline, listOf(Span(2, 2), Span(4, 2))),
    // FrostNav özellikleri ([FrostNavTile]): 4 sütunlu boyut büyük biçimdir ve Sahne panelinde kullanılır.
    WEB_PANEL("web_panel", R.string.launcher_widget_web_panel, R.drawable.ic_mdi_web, listOf(Span(2, 2), Span(4, 4))),
    EQUALIZER("equalizer", R.string.launcher_widget_equalizer, R.drawable.ic_mdi_equalizer, listOf(Span(2, 2), Span(4, 4))),
    AUTOMATION("automation", R.string.launcher_widget_automation, R.drawable.ic_mdi_restart, listOf(Span(2, 2), Span(4, 4))),
    CLUSTER_MIRROR("cluster_mirror", R.string.launcher_widget_cluster_mirror, R.drawable.ic_mdi_speedometer, listOf(Span(2, 2), Span(4, 2), Span(4, 4)));

    companion object {
        /**
         * Eski anahtarların yeni karşılıkları: "battery"/"service" -> HEALTH, "trip_totals" -> TRIP ([HealthMath.REMOVED_KEYS]).
         * Kaldırılan türler ("sunshade", "defrost") ve bilinmeyen anahtar null: kayıtlı düzende o yuva boşalır.
         */
        val MERGED_KEYS: Map<String, String> = HealthMath.REMOVED_KEYS

        fun fromKey(key: String): WidgetType? = (MERGED_KEYS[key] ?: key).let { k -> values().firstOrNull { it.key == k } }
    }
}

/** Kurulacak bir widget: kimlik, tür ve boyut. [extra] türe özel ayardır (APP için paket adı). */
data class PlacedWidget(
    val id: String,
    val type: WidgetType,
    val span: Span,
    val extra: String? = null,
)

/** Boyutu değişebilen widget: kurulduğu boyuta ([Span]) göre kendini düzenler. */
interface SizedWidget : LauncherWidget {
    fun applySpan(span: Span)
}

/** Varsayılan düzen için okunan kurulu uygulama sayısı üst sınırı ([DefaultApps.installedDefaults]). */
const val DEFAULT_APP_SLOTS = 8

/** Widget örneğini türüne göre üretir. Activity gerekir: bazı widget'lar diyalog açar. */
object WidgetFactory {
    fun create(activity: Activity, placed: PlacedWidget): SizedWidget = when (placed.type) {
        WidgetType.VEHICLE -> VehicleHeroCard(activity)
        WidgetType.CONSUMPTION -> ConsumptionTile(activity)
        WidgetType.TRIP -> TripComputerTile(activity)
        WidgetType.HEALTH -> HealthTile(activity)
        WidgetType.AIR -> AirQualityTile(activity)
        WidgetType.APP -> AppShortcutTile(activity, placed.extra, placed.id)
        WidgetType.SCREEN_OFF -> ScreenOffTile(activity)
        WidgetType.BREAK_REMINDER -> BreakTile(activity)
        WidgetType.PHONE_REMINDER -> PhoneTile(activity)
        WidgetType.CLOCK -> ClockTile(activity)
        WidgetType.WEB_PANEL -> WebPanelTile(activity)
        WidgetType.EQUALIZER -> EqualizerTile(activity)
        WidgetType.AUTOMATION -> AutomationTile(activity)
        WidgetType.CLUSTER_MIRROR -> ClusterMirrorTile(activity)
    }
}
