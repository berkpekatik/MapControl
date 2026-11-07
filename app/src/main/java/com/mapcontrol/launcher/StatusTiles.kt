package com.mapcontrol.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.desaysv.ivi.extra.project.carinfo.NewEnergyID
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mapcontrol.R
import com.mapcontrol.vehicle.VehicleMetricsRepository
import com.mapcontrol.vehicle.VehicleMetricsSnapshot

/**
 * Yalnızca durum gösteren araç kutucukları: veriyi [VehicleMetricsRepository] anlık görüntüsünden okur,
 * araca hiçbir komut göndermez. Sinyal gelmezse "Okunamıyor" der, tahmin yürütmez.
 */
internal abstract class MetricsTile(
    context: Context,
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
) : CenteredTile(context, iconRes, labelRes) {

    private val metrics = VehicleMetricsRepository.getInstance(context.applicationContext)
    protected var snapshot: VehicleMetricsSnapshot = current()
        private set
    private val listener = VehicleMetricsRepository.Listener {
        if (LauncherDemo.enabled) return@Listener
        snapshot = it
        bind()
    }

    private fun current() = if (LauncherDemo.enabled) LauncherDemo.vehicle() else metrics.currentSnapshot()

    override fun onVisible() {
        snapshot = current()
        bind()
        metrics.addListener(listener)
    }

    override fun onHidden() {
        metrics.removeListener(listener)
    }

    override fun release() {
        // Ortak örnek: release() çağrılmaz, yalnızca dinleyici çıkar.
        metrics.removeListener(listener)
    }

    override fun bindDetail() = bind()

    /** Değeri ve (geniş boyutta) ikinci satırı [snapshot]'tan yeniden kurar. */
    protected abstract fun bind()
}

/**
 * Sürüş modu: Eko, Normal, Spor ve arazi modları. İkon rengi aracın mod rengini alır. Dokunuş (kutucuk, şerit ve panel)
 * araçta seçilebilen sıradaki moda geçer ([DriveModes.next]; liste CarConfig 109'dan, okunamazsa Eko/Normal/Spor). Yazma
 * NewEnergy 4 ← modun değeri (okuma = yazma), [WidgetWriter] beyaz listesinden; hedef araç okuyana kadar ([CONFIRM_MS]) iyimser gösterilir.
 * Değişimden sonra açılan OEM sürüş modu penceresi [DriveModeRequests] üzerinden kapatılır ([OemDriveDialogCloser]).
 */
internal class DriveModeTile(context: Context) :
    MetricsTile(context, R.drawable.ic_mdi_car_sports, R.string.launcher_widget_drive_mode) {

    private val main = Handler(Looper.getMainLooper())
    private val io = LauncherIo.serial()

    /** İyimser hedef: araç okuyana kadar gösterilir; [CONFIRM_MS] sonra okunan moda dönülür. */
    private var pending: DriveMode? = null
    private val clearPending = Runnable { pending = null; bind() }

    init {
        WidgetWriter.attach(context)
        CarFeatures.init(context)
        view.isClickable = true
        view.isFocusable = true
        view.setOnClickListener { cycle() }
        view.pressFeedback()
    }

    override fun stripTap(part: Int) = cycle()

    private fun cycle() {
        val current = pending ?: if (snapshot.connected && snapshot.hasDriveMode()) DriveModes.fromRead(snapshot.driveMode) else null
        val to = DriveModes.next(current, DriveModes.available(CarFeatures.config109)) ?: return
        if (LauncherDemo.enabled) return
        pending = to
        bind()
        main.removeCallbacks(clearPending)
        main.postDelayed(clearPending, CONFIRM_MS)
        DriveModeRequests.mark(SystemClock.elapsedRealtime())
        io.runSafely {
            LauncherEdition.sendItemValue(CarInfoProxy.getInstance(), VDEventCarInfo.MODULE_NEW_ENERGY, NewEnergyID.ID_DRIVE_MODE, to.value)
        }
    }

    override fun onHidden() {
        super.onHidden()
        main.removeCallbacks(clearPending)
        pending = null
    }

    override fun bind() {
        val read = if (snapshot.connected && snapshot.hasDriveMode()) DriveModes.fromRead(snapshot.driveMode) else null
        // Araç hedefe geçtiyse bekleme biter.
        if (pending != null && read == pending) { pending = null; main.removeCallbacks(clearPending) }
        val shown = pending ?: read
        if (shown == null) {
            setIcon(R.drawable.ic_mdi_car_sports, LauncherTheme.TEXT_SECONDARY)
            showMissing()
            return
        }
        val mode = shown.value
        // Normal modda ikon ikincil renkte kalır; diğer modlarda tema açıksa vurgu, kapalıysa modun kendi rengi.
        val tint = when {
            mode == DriveAccent.NORMAL -> LauncherTheme.TEXT_SECONDARY
            DriveAccent.enabled(context) -> LauncherTheme.ACCENT
            else -> DriveAccent.accentFor(mode, LauncherTheme.night) ?: LauncherTheme.TEXT_SECONDARY
        }
        setIcon(R.drawable.ic_mdi_car_sports, tint)
        // 2x2'de ortada büyük mod ikonu (vurgu renginde), altında mod adı.
        setHeroIcon(R.drawable.ic_mdi_car_sports, tint)
        setValue(context.getString(shown.label), LauncherTheme.TEXT)
        showDetail(null)
    }

    private companion object {
        /** Araç yeni modu bu süre içinde okumazsa iyimser hedef bırakılır (ComfortTile.CONFIRM_MS gibi). */
        const val CONFIRM_MS = 2_500L
    }
}
