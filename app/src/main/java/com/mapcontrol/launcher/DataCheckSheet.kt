package com.mapcontrol.launcher

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.location.Location
import android.location.LocationManager
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy
import com.mapcontrol.R
import com.mapcontrol.media.LauncherMediaController
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Veri kontrolü" paneli: araç testinde "her widget için veri geliyor mu?" sorusunu tek ekranda yanıtlar.
 * Widget'a göre gruplanmış satırlar (ad, modül#kimlik, ham değer, çözülmüş değer, durum noktası, son değişim) her
 * saniye yenilenir. YALNIZCA OKUR: araca hiçbir şey yazılmaz. Tablo [DataCheckTable]'dadır.
 * Açma: Ayarlar > Veri kontrolü, ya da debug yapıda `--ez launcher_data_check true` ([applyFrom]).
 */
class DataCheckSheet(private val activity: Activity) {

    /** Ekrandaki bir satır: tabloyla ya da GPS/medya gibi VDBus dışı kaynaklarla beslenir. */
    private class Row(val group: String, val name: String, val idLabel: String) {
        lateinit var dot: android.view.View
        lateinit var raw: TextView
        lateinit var decoded: TextView
        lateinit var age: TextView
    }

    /** Bir turun okunmuş satırı (UI iş parçacığına taşınır ve snapshot'a yazılır). */
    private class Sample(
        val row: Row,
        val rawText: String,
        val decodedText: String,
        val status: CheckStatus,
    )

    private val main = Handler(Looper.getMainLooper())
    private val io = LauncherIo.serial()
    private val tracker = ChangeTracker()
    private val rows = ArrayList<Row>()
    private val signalRows = HashMap<CheckSignal, Row>()
    private val gpsRow = Row("GPS", "Son konum", "LocationManager")
    private val accessRow = Row("Medya", "Bildirim erişimi", "enabled_notification_listeners")
    private val sessionRow = Row("Medya", "Aktif oturum", "MediaSessionManager")
    private val statusLine by lazy { text(activity, LabelSp, LauncherTheme.TEXT_SECONDARY) }

    private var dialog: Dialog? = null
    private var visible = false
    private var busy = false
    private var lastSamples: List<Sample> = emptyList()
    private var lastConnected = false

    fun show() {
        if (dialog?.isShowing == true) return
        rows.clear()
        signalRows.clear()
        dialog = showLauncherSheet(activity, R.string.launcher_data_check, SHEET_WIDTH_DP, fullScreen = true) { d -> build(d) }.apply {
            setOnDismissListener {
                visible = false
                main.removeCallbacksAndMessages(null)
                dialog = null
            }
        }
        visible = true
        main.post(tick)
    }

    fun release() {
        dialog?.dismiss()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!visible) return
            if (busy) {
                main.postDelayed(this, POLL_MS)
                return
            }
            busy = true
            io.runSafely {
                val result = try {
                    readAll()
                } catch (t: Throwable) {
                    Log.w(TAG, "okuma başarısız", t)
                    null
                }
                main.post {
                    busy = false
                    if (result != null && visible) apply(result.first, result.second)
                    if (visible) main.postDelayed(this, POLL_MS)
                }
            }
        }
    }

    // ---- UI ----

    private fun LinearLayout.build(d: Dialog) {
        addView(legend(), sheetFullWidth())
        addView(statusLine, sheetFullWidth())
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        list.addView(text(activity, BodySp, LauncherTheme.ACCENT, bold = true).apply {
            setText(R.string.launcher_data_check_device)
            setPadding(0, activity.dp(14), 0, activity.dp(4))
        }, sheetFullWidth())
        list.addView(text(activity, SubSp, LauncherTheme.TEXT_SECONDARY).apply {
            typeface = Typeface.MONOSPACE
            text = LauncherInfo.text(activity)
        }, sheetFullWidth())
        val groups = DataCheckTable.groups + listOf("GPS", "Medya")
        for (group in groups) {
            list.addView(text(activity, BodySp, LauncherTheme.ACCENT, bold = true).apply {
                text = group
                setPadding(0, activity.dp(14), 0, activity.dp(4))
            }, sheetFullWidth())
            val groupRows: List<Row> = when (group) {
                "GPS" -> listOf(gpsRow)
                "Medya" -> listOf(accessRow, sessionRow)
                else -> DataCheckTable.signals.filter { it.group == group }.map { s ->
                    Row(s.group, s.name, "${s.moduleName} #${s.id}").also { signalRows[s] = it }
                }
            }
            for (r in groupRows) {
                rows += r
                list.addView(rowView(r), sheetFullWidth())
            }
        }
        val scroll = ScrollView(activity).apply { addView(list) }
        addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        addView(gap(activity, 12, vertical = true))
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(sheetButton(R.string.launcher_data_check_save, primary = true) { save() }, weightLp(1f))
        }, sheetFullWidth())
    }

    private fun legend(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, activity.dp(8), 0, activity.dp(4))
        listOf(GREEN to "veri geldi", GREY to "veri yok / servis bağlı değil", AMBER to "geçersiz (0xFF/0xFFFF)").forEach { (c, label) ->
            addView(android.view.View(activity).apply { background = oval(c) }, LinearLayout.LayoutParams(activity.dp(12), activity.dp(12)))
            addView(text(activity, LabelSp, LauncherTheme.TEXT_SECONDARY).apply {
                text = label
                setPadding(activity.dp(8), 0, activity.dp(24), 0)
            })
        }
    }

    private fun rowView(r: Row): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, activity.dp(6), 0, activity.dp(6))
        r.dot = android.view.View(activity).apply { background = oval(GREY) }
        addView(r.dot, LinearLayout.LayoutParams(activity.dp(14), activity.dp(14)).apply { rightMargin = activity.dp(14) })
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(activity, BodySp, medium = true).apply { text = r.name })
            addView(text(activity, SubSp, LauncherTheme.TEXT_SECONDARY).apply { text = r.idLabel })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2.2f))
        r.raw = text(activity, BodySp, LauncherTheme.TEXT_SECONDARY, maxLines = 2).apply { typeface = Typeface.MONOSPACE; text = "-" }
        addView(r.raw, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f))
        r.decoded = text(activity, BodySp, maxLines = 2).apply { text = "-" }
        addView(r.decoded, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2.2f))
        r.age = text(activity, SubSp, LauncherTheme.TEXT_SECONDARY).apply { gravity = Gravity.END; text = "-" }
        addView(r.age, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f))
    }

    private fun sheetButton(labelRes: Int, primary: Boolean, onClick: () -> Unit): TextView =
        text(activity, BodySp, if (primary) LauncherTheme.ON_ACCENT else LauncherTheme.TEXT, medium = true).apply {
            setText(labelRes)
            gravity = Gravity.CENTER
            background = rounded(activity, if (primary) LauncherTheme.ACCENT else LauncherTheme.CONTROL, 16f)
            minimumHeight = activity.dp(56)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            pressFeedback()
        }

    private fun apply(samples: List<Sample>, connected: Boolean) {
        lastSamples = samples
        lastConnected = connected
        val now = SystemClock.elapsedRealtime()
        statusLine.text = activity.getString(R.string.launcher_data_check_no_service)
        statusLine.visibility = if (connected) android.view.View.GONE else android.view.View.VISIBLE
        for (s in samples) {
            val key = s.row.group + "/" + s.row.name
            val changed = tracker.update(key, s.rawText + "|" + s.status, now)
            s.row.raw.text = s.rawText
            s.row.decoded.text = s.decodedText
            s.row.dot.background = oval(colorOf(s.status))
            s.row.age.text = if (s.status == CheckStatus.NO_DATA) "-" else ChangeTracker.ageText(now - changed)
        }
    }

    private fun colorOf(status: CheckStatus) = when (status) {
        CheckStatus.VALID -> GREEN
        CheckStatus.INVALID -> AMBER
        CheckStatus.NO_DATA -> GREY
    }

    // ---- Okuma (arka plan) ----

    private fun readAll(): Pair<List<Sample>, Boolean> {
        val out = ArrayList<Sample>()
        val proxy = CarInfoProxy.getInstance()
        // TripSource ile aynı: bağlı değilse bir kez bağlanmayı dene (yalnızca okuma servisi).
        if (!proxy.isServiceConnnected) proxy.init(activity.applicationContext)
        val connected = proxy.isServiceConnnected
        for ((signal, row) in signalRows) {
            val raw = if (connected) CarSignals.read(signal.module, signal.id) else null
            val decoded = if (raw == null || raw.isEmpty()) null else signal.decode(CheckClassify.combine(raw))
            val status = CheckClassify.classify(raw, decoded)
            out += Sample(row, rawText(raw), decodedText(status, decoded, raw), status)
        }
        out += gpsSample()
        out += mediaSamples()
        return out to connected
    }

    private fun rawText(raw: IntArray?): String = when {
        raw == null -> "-"
        raw.isEmpty() -> "[]"
        else -> raw.joinToString(",", "[", "]") { it.toString() }
    }

    private fun decodedText(status: CheckStatus, decoded: String?, raw: IntArray?): String = when (status) {
        CheckStatus.NO_DATA -> "veri yok"
        CheckStatus.INVALID -> decoded ?: "geçersiz (sentinel/aralık dışı)"
        CheckStatus.VALID -> decoded ?: ""
    }.let { if (status == CheckStatus.INVALID && raw != null && CheckClassify.isSentinel(raw)) "geçersiz (sentinel)" else it }

    private fun gpsSample(): Sample {
        val ctx = activity
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return Sample(gpsRow, "-", "izin yok", CheckStatus.NO_DATA)
        }
        val loc = try { lastKnownLocation(ctx) } catch (e: SecurityException) { null }
            ?: return Sample(gpsRow, "-", "konum yok", CheckStatus.NO_DATA)
        val ageS = ((System.currentTimeMillis() - loc.time) / 1000).coerceAtLeast(0)
        val text = String.format(Locale.US, "%.5f, %.5f  ±%.0f m  yaş %d sn", loc.latitude, loc.longitude, loc.accuracy, ageS)
        return Sample(gpsRow, loc.provider ?: "?", text, CheckStatus.VALID)
    }

    private fun lastKnownLocation(ctx: Context): Location? {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
    }

    private fun mediaSamples(): List<Sample> {
        val granted = LauncherMediaController.isNotificationAccessEnabled(activity)
        val access = Sample(accessRow, if (granted) "1" else "0", if (granted) "verildi" else "verilmedi",
            if (granted) CheckStatus.VALID else CheckStatus.NO_DATA)
        if (!granted) return listOf(access, Sample(sessionRow, "-", "erişim yok", CheckStatus.NO_DATA))
        // Dinleyiciyi kaydetmeden (medya kartının dinleyicisini ezmemek için) yalnızca oturum listesini okur.
        val session = try {
            val msm = activity.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val listener = ComponentName(activity.packageName, MEDIA_LISTENER_CLASS)
            msm.getActiveSessions(listener).firstOrNull()
        } catch (e: Exception) {
            null
        } ?: return listOf(access, Sample(sessionRow, "-", "aktif oturum yok", CheckStatus.NO_DATA))
        val title = session.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
        return listOf(access, Sample(sessionRow, session.packageName, title ?: "(başlık yok)", CheckStatus.VALID))
    }

    // ---- Kaydet ----

    private fun save() {
        val text = snapshotText(lastSamples, lastConnected)
        text.lines().forEach { Log.i(TAG, it) }
        io.runSafely {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = try {
                File(activity.getExternalFilesDir(null) ?: activity.filesDir, "datacheck-$stamp.txt").also { it.writeText(text) }
            } catch (e: Exception) {
                Log.w(TAG, "kaydedilemedi", e)
                null
            }
            main.post {
                val msg = if (file != null) {
                    Log.i(TAG, "SAVED ${file.absolutePath}")
                    activity.getString(R.string.launcher_data_check_saved, file.absolutePath)
                } else activity.getString(R.string.launcher_data_check_save_failed)
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun snapshotText(samples: List<Sample>, connected: Boolean): String = buildString {
        append("MapControl veri kontrolü ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append('\n')
        append("servis bağlı: ").append(connected).append('\n')
        append("\n[Cihaz]\n").append(LauncherInfo.text(activity)).append('\n')
        var group = ""
        for (s in samples) {
            if (s.row.group != group) {
                group = s.row.group
                append('\n').append('[').append(group).append("]\n")
            }
            append(String.format(Locale.US, "%-6s %-32s %-18s raw=%-14s %s%n",
                when (s.status) { CheckStatus.VALID -> "YESIL"; CheckStatus.INVALID -> "SARI"; CheckStatus.NO_DATA -> "GRI" },
                s.row.name, s.row.idLabel, s.rawText, s.decodedText))
        }
    }

    companion object {
        const val TAG = "DataCheck"
        const val EXTRA = "launcher_data_check"
        private const val POLL_MS = 1_000L
        private const val SHEET_WIDTH_DP = 1100
        /** Başlık, ipucu, gösterge, düğme ve iç boşluk için ayrılan yükseklik; kalanı kaydırılan liste. */
        private const val BodySp = 17f
        private const val LabelSp = 15f
        private const val SubSp = 13f
        private const val GREEN = 0xFF22C55E.toInt()
        private const val AMBER = 0xFFF59E0B.toInt()
        private const val GREY = 0xFF6B7280.toInt()
        private const val MEDIA_LISTENER_CLASS = "com.mapcontrol.media.LauncherMediaNotificationListener"

        /** Debug yapıda `--ez launcher_data_check true` ile paneli açar. Dönen panel etkinlik kapanırken [release] edilmeli. */
        fun applyFrom(activity: Activity, intent: Intent?): DataCheckSheet? {
            if (intent?.getBooleanExtra(EXTRA, false) != true) return null
            if (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
            return DataCheckSheet(activity).also { it.show() }
        }
    }
}
