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
 * boyut Sahne panelinde ([SahneForm.panelSpan]) kullanılır. Sahne'de gösterilmeyen türler (ör. sağlık, hava, fotoğraf)
 * eski Sahne kayıtlarının çözülüp yedeğe dönmesi için listede kalır ([SahneSlots.decode]).
 */
enum class WidgetType(val key: String, @StringRes val label: Int, @DrawableRes val icon: Int, val sizes: List<Span>) {
    MEDIA("media", R.string.launcher_widget_media, R.drawable.ic_mdi_music_note, listOf(Span(4, 2), Span(6, 4), Span(6, 6))),
    VEHICLE("vehicle", R.string.launcher_widget_vehicle, R.drawable.ic_mdi_car, listOf(Span(4, 4))),
    LOCK("lock", R.string.launcher_widget_lock, R.drawable.ic_mdi_lock, listOf(Span(1, 2))),
    DRIVE_MODE("drive_mode", R.string.launcher_widget_drive_mode, R.drawable.ic_mdi_car_sports, listOf(Span(2, 2))),
    CONSUMPTION("consumption", R.string.launcher_widget_consumption, R.drawable.ic_mdi_leaf, listOf(Span(2, 2))),
    TRIP("trip", R.string.launcher_widget_trip, R.drawable.ic_mdi_road_variant, listOf(Span(2, 2), Span(3, 2), Span(4, 4))),
    HEALTH("health", R.string.launcher_widget_health, R.drawable.ic_mdi_lightning_bolt, listOf(Span(2, 2), Span(4, 4))),
    AIR("air", R.string.launcher_widget_air, R.drawable.ic_mdi_air_filter, listOf(Span(2, 2), Span(4, 2))),
    APP("app", R.string.launcher_widget_app, R.drawable.ic_mdi_view_grid, listOf(Span(1, 2))),
    // Birleşik koltuk (ısıtma + soğutma tek widget'ta, taraf başına bir tane); 4×4 büyük biçim Sahne panelindedir.
    SEAT_DRIVER("seat_driver", R.string.launcher_widget_seat_driver, R.drawable.ic_mdi_car_seat_heater, listOf(Span(2, 2), Span(4, 4))),
    SEAT_PASSENGER("seat_passenger", R.string.launcher_widget_seat_passenger, R.drawable.ic_mdi_car_seat_heater, listOf(Span(2, 2), Span(4, 4))),
    AWD("awd", R.string.launcher_widget_awd, R.drawable.ic_mdi_car_cog, listOf(Span(4, 4), Span(2, 2))),
    PHOTO("photo", R.string.launcher_widget_photo, R.drawable.ic_mdi_image, listOf(Span(2, 2), Span(4, 2), Span(4, 4))),
    TRUNK("trunk", R.string.launcher_widget_trunk, R.drawable.ic_mdi_car_trunk, listOf(Span(2, 2), Span(2, 4))),
    SCREEN_OFF("screen_off", R.string.launcher_widget_screen_off, R.drawable.ic_mdi_weather_night, listOf(Span(2, 2), Span(1, 2))),
    BREAK_REMINDER("break_reminder", R.string.launcher_widget_break, R.drawable.ic_mdi_timer_sand, listOf(Span(2, 2))),
    PHONE_REMINDER("phone_reminder", R.string.launcher_widget_phone, R.drawable.ic_mdi_cellphone, listOf(Span(2, 2))),
    CLOCK("clock", R.string.launcher_widget_clock, R.drawable.ic_mdi_clock_outline, listOf(Span(2, 2), Span(4, 2))),
    REAR_SPEAKER("rear_speaker", R.string.launcher_widget_rear_speaker, R.drawable.ic_mdi_volume_high, listOf(Span(2, 2), Span(2, 1))),
    // FrostNav özellikleri ([FrostNavTile]): 4 sütunlu boyut büyük biçimdir ve Sahne panelinde kullanılır.
    WEB_PANEL("web_panel", R.string.launcher_widget_web_panel, R.drawable.ic_mdi_web, listOf(Span(2, 2), Span(4, 4))),
    EQUALIZER("equalizer", R.string.launcher_widget_equalizer, R.drawable.ic_mdi_equalizer, listOf(Span(2, 2), Span(4, 4))),
    AUTOMATION("automation", R.string.launcher_widget_automation, R.drawable.ic_mdi_restart, listOf(Span(2, 2), Span(4, 4))),
    SPLIT_SCREEN("split_screen", R.string.launcher_widget_split_screen, R.drawable.ic_mdi_view_split, listOf(Span(2, 2), Span(4, 2), Span(4, 4))),
    CLUSTER_MIRROR("cluster_mirror", R.string.launcher_widget_cluster_mirror, R.drawable.ic_mdi_speedometer, listOf(Span(2, 2), Span(4, 2), Span(4, 4))),
    // Yandex bildiriminden sonraki dönüş ve varış ([NavigationPanel]). Anahtar "nav_turn": eski, kaldırılmış "navigation"
    // kutucuğunun kayıtları bu türe dönüşmesin (o anahtar yüklemede düşer).
    NAVIGATION("nav_turn", R.string.launcher_widget_navigation, R.drawable.ic_sahne_nav, listOf(Span(4, 4))),
    // Telefon paneli ([PhonePanel]): sesli asistan, favoriler, son aramalar. Anahtar "phone_call": "phone_reminder" telefon
    // hatırlatma kutucuğunundur ([PhoneTile]).
    PHONE("phone_call", R.string.launcher_widget_phone_call, R.drawable.ic_sahne_phone, listOf(Span(4, 4))),
    // Direksiyon ısıtma ([SteerHeatTile]): yalnız şerit; yalnız CarConfig 120 izin veren araçta (Tiggo 8) sunulur.
    STEER_HEAT("steer_heat", R.string.launcher_widget_steer_heat, R.drawable.ic_mdi_steering, listOf(Span(2, 2)));

    companion object {
        /**
         * Eski anahtarların yeni karşılıkları: "battery"/"service" -> HEALTH, "trip_totals" -> TRIP ([HealthMath.REMOVED_KEYS]);
         * ayrı ısıtma/soğutma koltukları -> o tarafın birleşik koltuğu. Kaldırılan türler ("sunshade", "defrost") ve bilinmeyen
         * anahtar null: kayıtlı düzende o yuva boşalır.
         */
        val MERGED_KEYS: Map<String, String> = HealthMath.REMOVED_KEYS + mapOf(
            "seat_heat_driver" to "seat_driver",
            "seat_vent_driver" to "seat_driver",
            "seat_heat_passenger" to "seat_passenger",
            "seat_vent_passenger" to "seat_passenger",
        )

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
        WidgetType.MEDIA -> MediaCard(activity)
        WidgetType.VEHICLE -> VehicleHeroCard(activity)
        WidgetType.LOCK -> LockTile(activity)
        WidgetType.DRIVE_MODE -> DriveModeTile(activity)
        WidgetType.CONSUMPTION -> ConsumptionTile(activity)
        WidgetType.TRIP -> TripComputerTile(activity)
        WidgetType.HEALTH -> HealthTile(activity)
        WidgetType.AIR -> AirQualityTile(activity)
        WidgetType.APP -> AppShortcutTile(activity, placed.extra, placed.id)
        WidgetType.SEAT_DRIVER -> SeatTile(activity, driverSeat = true)
        WidgetType.SEAT_PASSENGER -> SeatTile(activity, driverSeat = false)
        WidgetType.AWD -> AwdTile(activity)
        WidgetType.TRUNK -> TrunkTile(activity)
        WidgetType.PHOTO -> PhotoTile(activity, placed.id)
        WidgetType.SCREEN_OFF -> ScreenOffTile(activity)
        WidgetType.BREAK_REMINDER -> BreakTile(activity)
        WidgetType.PHONE_REMINDER -> PhoneTile(activity)
        WidgetType.CLOCK -> ClockTile(activity)
        WidgetType.REAR_SPEAKER -> RearSpeakerTile(activity)
        WidgetType.WEB_PANEL -> WebPanelTile(activity)
        WidgetType.EQUALIZER -> EqualizerTile(activity)
        WidgetType.AUTOMATION -> AutomationTile(activity)
        WidgetType.SPLIT_SCREEN -> SplitScreenTile(activity)
        WidgetType.CLUSTER_MIRROR -> ClusterMirrorTile(activity)
        WidgetType.NAVIGATION -> NavigationPanel(activity)
        WidgetType.PHONE -> PhonePanel(activity)
        WidgetType.STEER_HEAT -> SteerHeatTile(activity)
    }
}
