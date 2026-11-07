package com.mapcontrol.launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.mapcontrol.R

/**
 * Uyarı şeridi (§10, karma çözüm): koyu cam, ince kehribar kenar, kehribar nokta; en çok iki madde ve "+N". Yalnızca arabada
 * görünmeyen şeyler içindir ([MiniAlerts]: motor suyu / akü, düşük lastik psi ile, yakıt kapağı, yakıt az, bakım, kötü hava; bu
 * önem sırasıyla); hiç madde yoksa şerit hiç
 * görünmez. Yükseklik [HEIGHT_DP] (sürüşte basılabilir); her madde kendi dokunma alanıdır, dokunuş [onTap]'e gider ("+N" için
 * null). Sahne'de aracın altında, mini göstergede uyarı satırında aynı görünüşle kullanılır.
 *
 * Sürüşte ([setDriving]) kenar ışığı biraz güçlenir; yanıp sönme yoktur. Görünüm statiktir: yalnızca veri değişince çizilir.
 */
internal class StageStrip(
    context: Context,
    private val onTap: (MiniAlerts.Item?) -> Unit,
) : LinearLayout(context) {

    private val dot = View(context).apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    private val labels = Array(StageNotices.MAX_VISIBLE) { label() }
    private val seps = Array(StageNotices.MAX_VISIBLE - 1) { sahneText(context, TEXT_SP, Sahne.INK_3, 400).apply { text = "·" } }
    private val more = label().apply { setTextColor(Sahne.INK_3) }
    private var items: List<MiniAlerts.Item> = emptyList()
    private var named: List<MiniAlerts.Item> = emptyList()
    private var texts: List<String> = emptyList()
    private var driving: Boolean? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(22), 0, context.dp(10), 0)
        val d = context.dp(DOT_DP + 2 * DOT_RING_DP)
        addView(dot, LayoutParams(d, d).apply { marginEnd = context.dp(4) })
        for (i in labels.indices) {
            if (i > 0) addView(seps[i - 1], LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addView(labels[i], LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }
        addView(more, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        dot.background = LayerDrawable(arrayOf(oval(Sahne.AMBER_SOFT), oval(Sahne.AMBER))).apply {
            val i = context.dp(DOT_RING_DP)
            setLayerInset(1, i, i, i, i)
        }
        setDriving(false)
        visibility = GONE
    }

    private fun label(): TextView = sahneText(context, TEXT_SP, Sahne.INK, 400).apply {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(12), 0, context.dp(12), 0)
        compoundDrawablePadding = context.dp(4)
        isClickable = true
        isFocusable = true
    }

    /** Maddeler ve metinleri ([stripText] ile aynı sırada). Aynı liste gelirse hiçbir şey yapılmaz. */
    fun bind(all: List<MiniAlerts.Item>, text: (MiniAlerts.Item) -> String) {
        val t = all.map(text)
        if (all == items && t == texts) return
        items = all
        texts = t
        if (all.isEmpty()) {
            visibility = GONE
            return
        }
        val (shown, extra) = StageNotices.visible(all)
        named = shown
        labels.forEachIndexed { i, l ->
            val item = shown.getOrNull(i)
            l.visibility = if (item == null) GONE else VISIBLE
            if (i > 0) seps[i - 1].visibility = l.visibility
            if (item == null) return@forEachIndexed
            l.text = t[i]
            l.setTextColor(Sahne.INK)
            // Yakıt az bir eylemdir (dokununca benzinlik araması): sonunda küçük ok.
            val chev = if (item.kind == MiniAlerts.Kind.FUEL_LOW) chevron() else null
            l.setCompoundDrawablesRelative(null, null, chev, null)
            l.contentDescription = t[i]
            l.setOnClickListener { onTap(item) }
        }
        more.visibility = if (extra > 0) VISIBLE else GONE
        more.text = context.getString(R.string.launcher_notice_more, extra)
        more.contentDescription = context.getString(R.string.launcher_notice_more_desc, extra)
        more.setOnClickListener { onTap(null) }
        contentDescription = (shown.indices.map { t[it] } + listOfNotNull(more.text.takeIf { extra > 0 })).joinToString(", ")
        visibility = VISIBLE
        requestLayout()
    }

    /** Sürüşte kenar ışığı biraz güçlü (yanıp sönme yok); değişmedikçe arka plan yeniden kurulmaz. */
    fun setDriving(on: Boolean) {
        if (driving == on) return
        driving = on
        background = GlassDrawable(context, Sahne.PILL_RADIUS_DP, shadow = 0f, border = if (on) EDGE_DRIVE else Sahne.AMBER_LINE)
    }

    private fun chevron() = context.getDrawable(R.drawable.ic_sahne_chev_right)?.mutate()?.apply {
        setTint(Sahne.INK_3)
        val d = context.dp(18)
        setBounds(0, 0, d, d)
    }

    private fun oval(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    companion object {
        const val HEIGHT_DP = 64
        const val TEXT_SP = 20f
        const val DOT_DP = 8
        const val DOT_RING_DP = 4
        /** Sürüşteki kenar: [Sahne.AMBER_LINE]'dan biraz güçlü (gece %62, gündüz %55 kehribar). */
        private val EDGE_DRIVE: Int get() = if (LauncherTheme.night) 0x9EFFA24A.toInt() else 0x8CB4500A.toInt()
    }
}

/**
 * Şerit maddesinin kısa metni (Sahne ve mini gösterge ortak): "Akü zayıf 11,4 V", "Arka sol 23 psi",
 * "Yakıt kapağı açık", "Bakıma 800 km", "Yakıt az" (şeritte sonunda ok: dokununca benzinlik), "Hava kalitesi kötü". Lastikte teker bilinmiyorsa yalnızca psi, psi de yoksa genel metin.
 */
internal fun stripText(context: Context, a: MiniAlerts.Item): String = when (a.kind) {
    MiniAlerts.Kind.TIRE -> {
        val bar = a.tireLow?.bar
        val wheel = a.tireLow?.wheel?.let { STRIP_WHEELS.getOrNull(it) }
        when {
            bar != null && wheel != null -> context.getString(R.string.launcher_strip_tire_wheel, context.getString(wheel), TireDecode.psi(bar))
            bar != null -> context.getString(R.string.launcher_mini_alert_tire_psi, TireDecode.psi(bar))
            else -> context.getString(R.string.launcher_mini_alert_tire)
        }
    }
    MiniAlerts.Kind.ENGINE_HOT -> context.getString(R.string.launcher_health_hot)
    MiniAlerts.Kind.BATTERY_LOW -> a.batteryV?.let { context.getString(R.string.launcher_strip_battery_low, it) }
        ?: context.getString(R.string.launcher_health_battery_low)
    MiniAlerts.Kind.BATTERY_HIGH -> a.batteryV?.let { context.getString(R.string.launcher_strip_battery_high, it) }
        ?: context.getString(R.string.launcher_health_battery_high)
    MiniAlerts.Kind.FUEL_CAP -> context.getString(R.string.launcher_strip_fuel_cap)
    MiniAlerts.Kind.FUEL_LOW -> context.getString(R.string.launcher_mini_alert_fuel)
    MiniAlerts.Kind.SERVICE_OVERDUE -> context.getString(R.string.launcher_mini_alert_service_overdue)
    MiniAlerts.Kind.SERVICE_NEAR -> a.serviceKm?.let { context.getString(R.string.launcher_mini_alert_service_km, it) }
        ?: context.getString(R.string.launcher_mini_alert_service)
    MiniAlerts.Kind.AIR_BAD -> context.getString(R.string.launcher_strip_air)
}

private val STRIP_WHEELS = listOf(R.string.launcher_tire_fl, R.string.launcher_tire_fr, R.string.launcher_tire_rl, R.string.launcher_tire_rr)

/**
 * En yakın benzin istasyonu araması, başlatıcının harita seçimine göre ([MapAppPrefs]): Google seçiliyse Google Haritalar'da
 * geo: araması; değilse Yandex. Açılmazsa genel harita (geo:); hiçbiri yoksa kısa bilgi.
 */
internal fun openNearestStation(context: Context) {
    val google = try { MapAppPrefs.current(context) == MapApp.GOOGLE_MAPS } catch (e: Exception) { false }
    val tries = if (google) listOf(NearestStation.geoUri() to NavigationSource.GOOGLE_MAPS, NearestStation.geoUri() to null)
    else listOf(NearestStation.yandexUri() to null, NearestStation.geoUri() to null)
    for ((uri, pkg) in tries) {
        try {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri))
                .apply { if (pkg != null) setPackage(pkg) }
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: android.content.ActivityNotFoundException) {
            // Sıradaki adres denenir.
        } catch (e: SecurityException) {
            // Sıradaki adres denenir.
        }
    }
    android.widget.Toast.makeText(context, R.string.launcher_notice_no_maps, android.widget.Toast.LENGTH_SHORT).show()
}
