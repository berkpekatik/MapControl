package com.mapcontrol.launcher

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.mapcontrol.R

/** Kabin içi PM2.5 derecesi (µg/m³): 0 iyi (<= 35), 1 orta (<= 75), 2 kötü. Hava kalitesi kutucuğu ve araç detayı aynı eşiği kullanır. */
internal object AirGrade {
    const val GOOD_MAX = 35
    const val MODERATE_MAX = 75
    const val GOOD = 0
    const val MODERATE = 1
    const val POOR = 2

    fun of(pm: Int): Int = when {
        pm <= GOOD_MAX -> GOOD
        pm <= MODERATE_MAX -> MODERATE
        else -> POOR
    }
}

/**
 * Araç detayı ekranının verisi (saf, JVM'de sınanır). Sıralar hep ön sol, ön sağ, arka sol, arka sağ. null her yerde
 * "okunamadı / bilinmiyor" demektir ve ekranda "—" görünür; uydurma değer yoktur.
 */
internal data class CarDetailModel(
    val tiresPsi: List<Int?>,
    val tiresLow: List<Boolean>,
    /** Açık (true) / kapalı (false) / bilinmiyor (null). */
    val doors: List<Boolean?>,
    val trunk: Boolean?,
    val hood: Boolean?,
    val sunroof: Boolean?,
    val windows: List<Boolean?>,
    val serviceKm: Int?,
    val serviceDays: Int?,
    /** Sağlık sorunları önem sırasıyla (boş = her şey normal); hiç veri yoksa null. */
    val health: List<HealthMath.Issue>?,
    val batteryV: Double?,
    /** Motor suyu durum kodu ([CoolantStatus]; derece değil). */
    val coolantCode: Int?,
    val pm25In: Int?,
    /** Dış sıcaklık °C (HVAC 19); okunamadıysa null. */
    val outsideC: Double? = null,
    /** Panoramik tavanın konumu ([Openings.roof]); bilinmiyorsa null. */
    val roof: RoofState? = null,
)

internal object CarDetail {

    private val WINDOWS = listOf(BodyPart.WINDOW_LF, BodyPart.WINDOW_RF, BodyPart.WINDOW_LR, BodyPart.WINDOW_RR)

    /** Açık olduğu bilinen camlar ([CarDetailModel.windows] sırası [WINDOWS]); bilinmeyen (null) açık sayılmaz. */
    fun openWindows(m: CarDetailModel): List<BodyPart> = WINDOWS.filterIndexed { i, _ -> m.windows.getOrNull(i) == true }

    /** Tekerlek başına psi; yalnızca dört tekerlekli okumada (tek değerli okumada hangi tekerlek olduğu bilinmez). */
    fun psi(t: TireReading?): List<Int?> =
        if (t == null || !t.perWheel) List(TireReading.WHEELS) { null } else t.values.map { v -> v?.let { TireDecode.psi(it) } }

    fun low(t: TireReading?): List<Boolean> =
        if (t == null || !t.perWheel) List(TireReading.WHEELS) { false } else t.values.map { TireDecode.isLow(it) }

    /**
     * [openings]: kapılar/bagaj/tavan (gövde okunamadıysa null); [extra]: camlar ve kaput (okunmayan parça haritada yok);
     * [trip]: bakım, akü, motor suyu, PM2.5; [lowFuel]: yakıt uyarısı (sağlık özetine girer).
     */
    fun model(openings: Openings?, extra: ExtraData, trip: TripData, lowFuel: Boolean?): CarDetailModel {
        val o = openings
        val hasHealth = HealthMath.hasData(trip.batteryV, trip.coolantCode, lowFuel, trip.serviceKm, trip.serviceDays)
        return CarDetailModel(
            tiresPsi = psi(extra.tires),
            tiresLow = low(extra.tires),
            doors = if (o == null) List(4) { null } else listOf(o.doorFl, o.doorFr, o.doorRl, o.doorRr),
            trunk = o?.trunk,
            hood = if (o?.hood == true) true else extra.parts[BodyPart.HOOD],
            sunroof = o?.sunroof,
            windows = WINDOWS.map { w -> if (o != null && w in o.windows) true else extra.parts[w] },
            serviceKm = trip.serviceKm,
            serviceDays = trip.serviceDays,
            health = if (!hasHealth) null else HealthMath.issues(trip.batteryV, lowFuel, trip.serviceKm, trip.serviceDays, trip.batteryHighHeld),
            batteryV = trip.batteryV,
            coolantCode = trip.coolantCode,
            pm25In = trip.pm25In,
            outsideC = trip.outsideC,
            roof = o?.roof,
        )
    }

    /** Parça listesinin özeti: hepsi bilinmiyorsa null ("—"); açık olanların sırası (boş = hepsi kapalı). */
    fun openIndices(states: List<Boolean?>): List<Int>? =
        if (states.all { it == null }) null else states.indices.filter { states[it] == true }
}

/**
 * Arabaya dokununca açılan detay ekranı (launcher sayfası, Sahne yazısı ve renkleri): solda üstten araç ve her tekerleğin
 * yanında lastik basıncı (psi), sağda kapı / bagaj / kaput / cam / tavan durumu, bakıma kalan, araç sağlığı özeti ve hava
 * kalitesi. Açıkken gelen yeni veriyle [bind] yeniden çağrılır. Yalnızca okur; araca bir şey göndermez.
 */
internal class CarDetailSheet(private val activity: Activity) {

    private val tireMap = TireMap(activity)
    /** İki sütun (araç ekranı 720 px: tek sütunda alt satırlar taşıyordu): solda gövde, sağda bakım / sağlık / hava. */
    private val bodyRows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val stateRows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private var rows = bodyRows
    var dialog: Dialog? = null
        private set

    fun show(onDismiss: () -> Unit): Dialog {
        val d = showLauncherSheet(activity, R.string.launcher_detail_title, SHEET_WIDTH_DP) {
            if (VehicleProfile.active.portrait) {
                // Dikey ekran (Jaecoo): üç sütun yan yana sığmaz; araç ve basınçlar üstte ortada, altında iki sütun.
                addView(tireMap, LinearLayout.LayoutParams(context.dp(PORTRAIT_TIRE_W_DP), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                })
                addView(View(context), LinearLayout.LayoutParams(1, context.dp(24)))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(bodyRows, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(View(context).apply { setBackgroundColor(Sahne.STRIP_HAIR) },
                        LinearLayout.LayoutParams(context.dp(1), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                            marginStart = context.dp(24); marginEnd = context.dp(24)
                        })
                    addView(stateRows, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f))
                }, sheetFullWidth())
                return@showLauncherSheet
            }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(tireMap, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(View(context), LinearLayout.LayoutParams(context.dp(28), 1))
                addView(bodyRows, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(View(context).apply { setBackgroundColor(Sahne.STRIP_HAIR) },
                    LinearLayout.LayoutParams(context.dp(1), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                        marginStart = context.dp(24); marginEnd = context.dp(24)
                    })
                addView(stateRows, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f))
            }, sheetFullWidth())
        }
        d.setOnDismissListener { dialog = null; onDismiss() }
        dialog = d
        return d
    }

    private var bound: Pair<CarDetailModel, Set<CarPart>>? = null

    fun bind(m: CarDetailModel, carParts: Set<CarPart>) {
        if (bound == m to carParts) return
        bound = m to carParts
        tireMap.bind(m, carParts)
        bodyRows.removeAllViews()
        stateRows.removeAllViews()
        rows = bodyRows
        val dash = activity.getString(R.string.launcher_unavailable)
        fun s(id: Int) = activity.getString(id)
        fun openClosed(v: Boolean?) = when (v) { null -> dash; true -> s(R.string.launcher_detail_open); false -> s(R.string.launcher_detail_closed) }
        fun list(states: List<Boolean?>, names: List<Int>): Pair<String, Boolean> {
            val open = CarDetail.openIndices(states) ?: return dash to false
            if (open.isEmpty()) return s(R.string.launcher_detail_closed) to false
            val locale = activity.resources.configuration.locales[0]
            val joined = open.mapIndexed { i, x -> s(names[x]).let { if (i == 0) it else it.lowercase(locale) } }.joinToString(", ")
            return activity.getString(R.string.launcher_part_open, joined) to true
        }
        val (doors, doorsOpen) = list(m.doors, DOOR_NAMES)
        row(s(R.string.launcher_detail_doors), doors, doorsOpen)
        row(s(R.string.launcher_trunk), openClosed(m.trunk), m.trunk == true)
        row(s(R.string.launcher_body_hood), openClosed(m.hood), m.hood == true)
        val (windows, windowsOpen) = list(m.windows, WINDOW_NAMES)
        row(s(R.string.launcher_detail_windows), windows, windowsOpen)
        row(s(R.string.launcher_detail_sunroof), openClosed(m.sunroof), m.sunroof == true)
        row(s(R.string.launcher_detail_outside), m.outsideC?.let { activity.getString(R.string.launcher_temp_c, Math.round(it).toInt()) } ?: dash, false)
        rows = stateRows
        val number = java.text.NumberFormat.getIntegerInstance(java.util.Locale.getDefault())
        val service = listOfNotNull(
            m.serviceKm?.let { activity.getString(R.string.launcher_km_value, number.format(it)) },
            m.serviceDays?.let { activity.getString(R.string.launcher_days_value, it) },
        ).joinToString(" · ").ifEmpty { dash }
        row(s(R.string.launcher_health_row_service), service, StageCorners.serviceNear(m.serviceKm, m.serviceDays))
        val health = when {
            m.health == null -> dash
            m.health.isEmpty() -> s(R.string.launcher_health_all_ok)
            else -> listOfNotNull(s(issueLabel(m.health.first())), (m.health.size - 1).takeIf { it > 0 }?.let { "+$it" }).joinToString(" · ")
        }
        row(s(R.string.launcher_widget_health), health, !m.health.isNullOrEmpty())
        row(s(R.string.launcher_health_row_battery),
            m.batteryV?.let { activity.getString(R.string.launcher_health_volt_only, String.format(java.util.Locale.getDefault(), "%,.1f", it)) } ?: dash,
            m.health?.any { it == HealthMath.Issue.BATTERY_LOW || it == HealthMath.Issue.BATTERY_HIGH } == true)
        // Motor suyu: yalnız durum ("Normal"); kod derece değil, diğer kodlar doğrulanana dek "—" ([CoolantStatus]).
        row(s(R.string.launcher_health_row_engine), if (CoolantStatus.isNormal(m.coolantCode)) s(R.string.launcher_coolant_normal) else dash, false)
        val air = m.pm25In?.let { pm ->
            val grade = s(when (AirGrade.of(pm)) {
                AirGrade.GOOD -> R.string.launcher_air_good
                AirGrade.MODERATE -> R.string.launcher_air_moderate
                else -> R.string.launcher_air_poor
            })
            "$grade · " + activity.getString(R.string.launcher_air_pm, pm)
        } ?: dash
        row(s(R.string.launcher_widget_air), air, StageCorners.airShown(m.pm25In))
    }

    private fun issueLabel(i: HealthMath.Issue): Int = when (i) {
        HealthMath.Issue.ENGINE_HOT -> R.string.launcher_health_hot
        HealthMath.Issue.BATTERY_LOW -> R.string.launcher_health_battery_low
        HealthMath.Issue.BATTERY_HIGH -> R.string.launcher_health_battery_high
        HealthMath.Issue.SERVICE_OVERDUE -> R.string.launcher_health_service_overdue
        HealthMath.Issue.LOW_FUEL -> R.string.launcher_health_low_fuel
        HealthMath.Issue.SERVICE_SOON -> R.string.launcher_health_service_soon
    }

    /** Satır: solda soluk etiket, sağda değer (uyarıda turuncu); en az dokunma yüksekliğinin yarısı kadar. */
    private fun row(label: String, value: String, alert: Boolean) {
        rows.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = activity.dp(ROW_H_DP)
            addView(sahneText(activity, 20f, Sahne.INK_3, 400).apply { text = label },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(sahneText(activity, 22f, if (alert) Sahne.AMBER else Sahne.INK, 500).apply {
                text = value
                gravity = Gravity.END
                maxLines = 2
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f))
            contentDescription = "$label, $value"
        }, sheetFullWidth())
    }

    /**
     * Üstten araç ve tekerlek başına basınç. Burun sağda, aracın sol yanı üstte: ön sol sağ üstte, arka sol sol üstte, ön sağ
     * sağ altta, arka sağ sol altta. Düşük basınç turuncu; okunamayan "—".
     */
    private class TireMap(context: Context) : ViewGroup(context) {
        private val car = CarStageView(context).apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        private val labels = List(TireReading.WHEELS) { TireLabel(context) }

        init {
            clipChildren = false
            addView(car)
            labels.forEach { addView(it) }
        }

        fun bind(m: CarDetailModel, parts: Set<CarPart>) {
            car.setOpen(parts, animate = false)
            car.setGlass(CarDetail.openWindows(m), m.sunroof == true, animate = false)
            car.setRoof(m.roof, animate = false)
            labels.forEachIndexed { i, l -> l.bind(context.getString(TIRE_NAMES[i]), m.tiresPsi[i], m.tiresLow[i]) }
        }

        private fun carW(w: Int) = (w * CAR_FRACTION).toInt()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val cw = carW(w)
            car.measure(MeasureSpec.makeMeasureSpec(CarStageView.widthFor(cw), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(CarStageView.heightFor(cw), MeasureSpec.EXACTLY))
            val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            labels.forEach { it.measure(free, free) }
            val labelH = labels.maxOf { it.measuredHeight }
            val carH = (cw * CarAnimSpec.BASE_H / CarAnimSpec.BASE_W).toInt()
            setMeasuredDimension(w, carH + 2 * (labelH + context.dp(LABEL_GAP_DP)))
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val w = r - l
            val h = b - t
            val cw = carW(w)
            val ch = (cw * CarAnimSpec.BASE_H / CarAnimSpec.BASE_W).toInt()
            val bodyLeft = (w - cw) / 2
            val bodyTop = (h - ch) / 2
            val cl = bodyLeft - CarStageView.carLeft(cw)
            val ct = bodyTop - CarStageView.carTop(cw)
            car.layout(cl, ct, cl + car.measuredWidth, ct + car.measuredHeight)
            val gap = context.dp(LABEL_GAP_DP)
            labels.forEachIndexed { i, label ->
                val front = i == 0 || i == 1
                val top = i == 0 || i == 2
                val cx = bodyLeft + (cw * if (front) FRONT_AXLE else REAR_AXLE).toInt()
                val x = (cx - label.measuredWidth / 2).coerceIn(0, maxOf(0, w - label.measuredWidth))
                val y = if (top) bodyTop - gap - label.measuredHeight else bodyTop + ch + gap
                label.layout(x, y, x + label.measuredWidth, y + label.measuredHeight)
            }
        }

        private class TireLabel(context: Context) : LinearLayout(context) {
            private val value: TextView = sahneText(context, 28f, Sahne.NUMERAL, 400)
            private val name: TextView = sahneText(context, 16f, Sahne.INK_3, 400)

            init {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(value, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
                addView(name, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            }

            fun bind(wheel: String, psi: Int?, low: Boolean) {
                value.text = psi?.let { context.getString(R.string.launcher_sahne_psi, it) } ?: context.getString(R.string.launcher_unavailable)
                value.setTextColor(if (low) Sahne.AMBER else if (psi != null) Sahne.NUMERAL else Sahne.INK_3)
                name.text = wheel
                contentDescription = "$wheel, ${value.text}"
            }
        }
    }

    private companion object {
        const val SHEET_WIDTH_DP = 1440
        /** Dikey ekranda üstteki araç + basınç alanının genişliği. */
        const val PORTRAIT_TIRE_W_DP = 560
        const val ROW_H_DP = 44
        const val CAR_FRACTION = 0.76f
        const val LABEL_GAP_DP = 10
        /** Aks konumları gövde genişliğine oranla (burun sağda). */
        const val FRONT_AXLE = 0.76f
        const val REAR_AXLE = 0.24f
        val TIRE_NAMES = listOf(R.string.launcher_tire_fl, R.string.launcher_tire_fr, R.string.launcher_tire_rl, R.string.launcher_tire_rr)
        val DOOR_NAMES = listOf(R.string.launcher_door_lf, R.string.launcher_door_rf, R.string.launcher_door_lr, R.string.launcher_door_rr)
        val WINDOW_NAMES = listOf(R.string.launcher_door_lf, R.string.launcher_door_rf, R.string.launcher_door_lr, R.string.launcher_door_rr)
    }
}
