package com.mapcontrol.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.mapcontrol.R

/**
 * Sahne'nin ortası (yuva değil, sabit). Araç sahnenin tam ortasında (§10); menzil sol omzunda, kilit hapı sağ omzunda,
 * ikisi aynı yükseklikte ve aynı yazı ağırlığında (§11). Araç, omuzlar ve alttaki uyarı şeridi payı arasında sığdığı kadar
 * büyük ([StageFit.centered]); payı şerit yokken de ayrılır, araç şerit çıkınca zıplamaz.
 *
 * Uyarılar (§10, son karar): araç çiziminde görünen şey yazıyla tekrar edilmez (kapı / bagaj / kaput açılır, cam ve tavan
 * buz mavisi); düşük lastikte o tekerde ince kehribar halka. Arabanın altındaki tek şerit ([StageStrip]) yalnızca arabada
 * görünmeyenler içindir: motor suyu sıcak / akü, düşük lastik (psi ile), yakıt kapağı açık, yakıt az (dokununca benzinlik
 * araması), bakım yaklaştı, kötü hava ([MiniAlerts], bu önem sırasıyla); hiçbiri yoksa şerit hiç görünmez. Şeride ya da araca dokununca araç detayı ([CarDetailSheet]).
 *
 * Menzilin altında ince yakıt çubuğu ve yüzde (azalınca kehribar). Zemin (§11): ışık havuzu ve gölgeler, çalan şarkının kapak
 * rengini çok hafif alan ortam ışığı (şarkı başına bir kez küçük kopyadan hesaplanır, yumuşak geçiş) ve araç üstünde dijital
 * ikiz ışıkları ([CarStageView.setLights]). Görünmezken tüm hareket durur.
 *
 * Veri bugünkü widget'lardan gelir: ekrana konmayan bir [VehicleHeroCard] ([VehicleHeroCard.Summary]) ve [LockTile];
 * yolculuk verisi (bakım, hava, ışıklar, hareket) ortak [TripSource] okuyucusundan. Tasarım ölçüleri 960 genişliğe oranlanır.
 */
class SahneStage(private val activity: Activity) : ViewGroup(activity), LauncherWidget {

    private val vehicle = VehicleHeroCard(activity).apply { applySpan(Span(4, 4)) }
    private val lock = LockTile(activity)
    private val sources: List<LauncherWidget> = listOf(vehicle, lock)

    private val floor = FloorView(activity)
    private val ambient = arrayOf(AmbientView(activity), AmbientView(activity))
    /** Araç ve açılan parçaları (kapı/bagaj/kaput animasyonu); görünüm gövdenin dışına taşan açık parçaları da kapsar. */
    private val car = CarStageView(activity).apply {
        contentDescription = activity.getString(R.string.launcher_sahne_car)
        // Kapı hareketi sürerken her kare yeniden çizilir; arkadaki statik zemin bu sürede donanım katmanından tek doku
        // olarak basılır, hareket bitince katman bırakılır.
        onAnimatingChanged = { on -> floor.setLayerType(if (on) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE, null) }
        // Dokununca araç detayı. Uzun basma yok; düzenleme modunda sahne dokunuşu zaten keser ([locked]).
        isClickable = true
        isLongClickable = false
        setOnClickListener { openDetail() }
        pressFeedback()
        setFloorReflection(true)
    }
    /** Açık araç detayı sayfası (yoksa null); yeni veriyle yerinde tazelenir. */
    private var detail: CarDetailSheet? = null
    /** İlk özet animasyonsuz uygulanır (ekran açıldığında kapı zaten açıksa açık çizilir). */
    private var carSeeded = false
    private var carW = 0
    /** Araç dikey mi (burun yukarı; Ana ekran ayarı). Değişince etkinlik yeniden kurulur ([HomeScreenPrefs.signature]). */
    private val vertical = HomeScreenPrefs.carVertical(activity)
    /** Aracın gövde kutusu (sahne pikseli; açılan parçalar hariç); ölçü değişmedikçe yeniden hesaplanmaz. */
    private var carBox = StageFit.Box(0f, 0f, 0f, 0f)
    /**
     * Yerleşim çerçevesi: gövdesi tuvali dolduran bir aracın (Tiggo 7) kutusu. Sığdırma, zemin ışığı ve ortam ışığı buna göredir;
     * [carBox] (tuval kutusu) aynı merkezde modelin ölçeği ([CarGeometry.stageScale]) kadar büyüktür. Ölçeği 1 olan modellerde
     * (Tiggo 7, Jaecoo 7) ikisi aynı kutudur.
     */
    private var frameBox = carBox
    private var fitKey: List<Int> = emptyList()
    /**
     * Bagaj açıkken aracın sağa kayması (piksel): açık bagaj kapağı menzil yazısının altına girmesin diye en çok
     * [TRUNK_SHIFT_MAX_DP]; zemin ışığı, gölge, yansıma ve ortam ışığı araçla birlikte kayar. [trunkShiftNeed] o anki
     * yerleşimde gereken pay (bagaj kapalıyken de hesaplanır), [trunkShift] şu anki değer (yumuşak geçiş).
     */
    private var trunkShiftNeed = 0f
    private var trunkShift = 0f
    private var trunkOpen = false
    private var shiftAnim: ValueAnimator? = null
    /**
     * Dikey ortalama (yalnızca dikey ekran profili): şerit yokken araç sahnenin tam ortasına iner. [centerNeed] kutunun
     * (şerit payına göre yerleşmiş) en çok ineceği piksel, [centerDy] şu anki değer; şerit görününce yumuşakça yukarı çıkar.
     */
    private var centerNeed = 0f
    private var centerDy = 0f
    private var centerAnim: ValueAnimator? = null
    /** Süren ortalama geçişinin hedefi ([centerAnim] varken anlamlı). */
    private var centerTarget = 0f

    // --- Menzil (sol omuz): tek satır, rakam + birim + "menzil" aynı taban çizgisinde, eşit genişlikli rakamlar ---
    private val rangeNumber = sahneText(activity, RANGE_SP, Sahne.NUMERAL, 300, -0.03f).apply { includeFontPadding = false }
    private val rangeUnit = sahneText(activity, SHOULDER_SP, Sahne.INK_2, SHOULDER_WEIGHT)
    private val rangeLabel = sahneText(activity, 16f, Sahne.INK_3, 400).apply { setText(R.string.launcher_range_label) }
    private val rangeRow = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = true
        addView(rangeNumber, LinearLayout.LayoutParams(WRAP, WRAP))
        addView(rangeUnit, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(6) })
        addView(rangeLabel, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(8) })
    }
    // İnce yakıt çubuğu ve yüzde (menzilin altında; azalınca kehribar).
    private val fuelIcon = sahneIcon(R.drawable.ic_sahne_fuel, Sahne.INK_3)
    private val gauge = FuelGauge(activity)
    private val fuelText = sahneText(activity, 18f, Sahne.INK, 500)
    private val fuelRow = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(fuelIcon, LinearLayout.LayoutParams(context.dp(20), context.dp(20)))
        addView(gauge, LinearLayout.LayoutParams(context.dp(FUEL_BAR_DP), context.dp(6)).apply { marginStart = context.dp(8) })
        addView(fuelText, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(10) })
    }
    private val rangeBlock = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(rangeRow, LinearLayout.LayoutParams(WRAP, WRAP))
        addView(fuelRow, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = context.dp(6) })
    }

    // --- Kilit hapı (sağ omuz) ---
    private val lockIcon = sahneIcon(R.drawable.ic_sahne_lock, Sahne.ICE)
    private val lockText = sahneText(activity, SHOULDER_SP, Sahne.INK, SHOULDER_WEIGHT, dayBump = false)
    private val lockChip = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GlassDrawable(context, Sahne.PILL_RADIUS_DP, shadow = 0.6f)
        setPadding(context.dp(16), 0, context.dp(18), 0)
        addView(lockIcon, LinearLayout.LayoutParams(context.dp(24), context.dp(24)))
        addView(lockText, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(10) })
        isClickable = true
        isFocusable = true
        setOnClickListener { lock.stripTap(0) }
        pressFeedback()
    }
    /** Son bilinen kilit durumu (kilitlenince dörtlüler bir kez yanar); henüz okunmadıysa null. */
    private var lastLocked: Boolean? = null

    // --- Uyarı şeridi (aracın altında; yalnızca arabada görünmeyenler) ---
    private val strip = StageStrip(activity) { onStripTap(it) }
    private var openings: Openings? = null
    /** Bakım km, PM2.5, ışıklar ve hareket; ortak okuyucudan, ek yoklama yok. */
    private var trip = TripData()
    private var visibleNow = false
    private val tripListener = TripSource.Listener {
        if (it == trip) return@Listener
        trip = it
        if (!welcome) car.setLights(it.lights)
        bindPartAlerts()
        bindStrip()
        refreshDetail()
    }

    // --- Ortam ışığı: kapak rengi ---
    private val artListener: (Bitmap?) -> Unit = { onArtwork(it) }
    private var artKey: Pair<Int, Int>? = null
    private var ambientFront = 0
    private var ambientColor: Int? = null
    /** Kapaktan çıkan renk (null = kapak yok ya da renksiz) ve müzik çalıyor mu; ışık [AmbientModeColor.decide] ile seçilir. */
    private var coverColor: Int? = null
    private var mediaPlaying = false
    private val playingListener: (Boolean) -> Unit = { mediaPlaying = it; refreshAmbient() }
    private var ambientAnim: ValueAnimator? = null
    private val artIo = LauncherIo.serial()
    private val main = Handler(Looper.getMainLooper())

    // --- Veri yok ---
    /** Araç verisi gelmeden: menzil yerine sabit iskelet çubuk ve aracın altında tek mesaj. */
    private val skeleton = Skeleton(activity)
    private val waitNote = sahneText(activity, 22f, Sahne.INK_3, 400).apply {
        setText(R.string.launcher_stage_waiting)
        gravity = Gravity.CENTER
        visibility = View.GONE
    }
    private var waiting = false

    /** Düzenleme modunda sahne dokunulmaz (yalnızca yuvalar düzenlenir). */
    var locked = false

    /**
     * false: menzil omzu (ve veri beklerken yerindeki iskelet) gizlenir; araç yalnızca kilit hapını ve şerit payını gözetir.
     * Mini gösterge menzil ile yakıtı kendi üst satırında büyük gösterir (mini gösterge etkinliği); ana ekranda hep true.
     */
    internal var showRange = true
        set(value) {
            if (field == value) return
            field = value
            fitKey = emptyList()
            applyWaiting()
            requestLayout()
        }

    /** Mini gösterge: veri yokken kilit hapı da gizli (tek bekleme satırı "Araç verisi bekleniyor" kalır). */
    internal var hideLockWhileWaiting = false
        set(value) {
            if (field == value) return
            field = value
            applyWaiting()
        }

    /** Spor ekranının araç modülü için açık parçalar ve camlar (her araç özetinde; [animate] ilk özetten sonra). */
    internal var onCar: ((parts: Set<CarPart>, windows: Collection<BodyPart>, sunroof: Boolean, animate: Boolean) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(lastParts, lastWindows, lastSunroof, false)
        }
    private var lastParts: Set<CarPart> = emptySet()
    private var lastWindows: Collection<BodyPart> = emptyList()
    private var lastSunroof = false

    // --- Karşılama (§7.4, [SahneWelcome]) ---
    /** Karşılama sürerken ışıklar karşılamanındır (yolculuk verisi beklenir, sonunda gerçek ışıklar uygulanır). */
    private var welcome = false
    /** Ortam ışığının durgun alfası (karşılama sonunda dönülen): öndeki görünüm renk varsa 1, öteki 0. Karşılama sırasında
     *  [setAmbient] ön/arka görünümü değiştirebildiği için açılışta saklanan alfa değil, o anki durum kullanılır. */
    private fun ambientRest(i: Int): Float = if (ambientColor != null && i == ambientFront) 1f else 0f
    private val welcomeShoulders: Array<View> get() = arrayOf(rangeBlock, lockChip, strip, skeleton, waitNote)
    private val lightsIgnite = Runnable { car.setLights(trip.lights.copy(head = true, brake = true)) }
    private val brakeSettle = Runnable { car.setLights(trip.lights.copy(head = true)) }

    /** Karanlık: araç, zemin, ortam ışığı, omuzlar ve şerit görünmez; araç biraz küçük. */
    internal fun welcomePrepare() {
        main.removeCallbacks(lightsIgnite)
        main.removeCallbacks(brakeSettle)
        welcome = true
        car.animate().cancel()
        floor.animate().cancel()
        car.alpha = 0f
        car.scaleX = WELCOME_SCALE
        car.scaleY = WELCOME_SCALE
        floor.alpha = 0f
        ambient.forEach { v -> v.animate().cancel(); v.alpha = 0f }
        welcomeShoulders.forEach { it.animate().cancel(); it.alpha = 0f }
    }

    /**
     * Araç karanlıktan belirir (alfa + ölçek), ~0,3 sn'de farlar ve stoplar yanar (stoplar sonra park lambası parlaklığına
     * iner), zemin ışığı ve ortam ışığı açılır, sonda menzil, kilit ve şerit belirir.
     */
    internal fun welcomePlay() {
        // Katmansız: ışıklar (far huzmesi, fren havuzu) görünüm kutusunun dışına taşar, katman onları keserdi; geçişte
        // alfa her çizime ayrı uygulanır ([CarStageView.flatAlpha]).
        car.flatAlpha = true
        car.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(80).setDuration(WELCOME_CAR_MS)
            .setInterpolator(WELCOME_EASE).withEndAction { car.flatAlpha = false }.start()
        main.postDelayed(lightsIgnite, WELCOME_LIGHTS_MS)
        main.postDelayed(brakeSettle, WELCOME_BRAKE_OFF_MS)
        floor.animate().alpha(1f).setStartDelay(WELCOME_FLOOR_MS).setDuration(800).setInterpolator(WELCOME_EASE).withLayer().start()
        ambient.forEachIndexed { i, v -> v.animate().alpha(ambientRest(i)).setStartDelay(WELCOME_FLOOR_MS).setDuration(800).start() }
        welcomeShoulders.forEach { it.animate().alpha(1f).setStartDelay(1_150).setDuration(650).setInterpolator(WELCOME_EASE).withLayer().start() }
    }

    /** Oynatmadan kısa solmayla açılış (güç sinyali gelmedi). */
    internal fun welcomeReveal(ms: Long) {
        car.flatAlpha = true
        car.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(ms).withEndAction { car.flatAlpha = false }.start()
        floor.animate().alpha(1f).setStartDelay(0).setDuration(ms).start()
        ambient.forEachIndexed { i, v -> v.animate().alpha(ambientRest(i)).setStartDelay(0).setDuration(ms).start() }
        welcomeShoulders.forEach { it.animate().alpha(1f).setStartDelay(0).setDuration(ms).start() }
    }

    /** Son hal: her şey görünür, gerçek ışıklar. */
    internal fun welcomeEnd() {
        if (!welcome) return
        welcome = false
        main.removeCallbacks(lightsIgnite)
        main.removeCallbacks(brakeSettle)
        car.animate().cancel()
        car.flatAlpha = false
        floor.animate().cancel()
        car.alpha = 1f
        car.scaleX = 1f
        car.scaleY = 1f
        floor.alpha = 1f
        ambient.forEachIndexed { i, v -> v.animate().cancel(); v.alpha = ambientRest(i) }
        welcomeShoulders.forEach { it.animate().cancel(); it.alpha = 1f }
        car.setLights(trip.lights)
    }

    /** Spor ekranından araç detayı. */
    fun openCarDetail() = openDetail()

    init {
        clipChildren = false
        clipToPadding = false
        addView(floor)
        ambient.forEach { addView(it) }
        addView(car)
        addView(rangeBlock)
        addView(lockChip)
        addView(strip)
        addView(skeleton)
        addView(waitNote)
        vehicle.onSummary = { bindVehicle(it) }
        lock.onStripChanged = { bindLock(lock.stripState()) }
        bindVehicle(vehicle.summary)
        bindLock(lock.stripState())
        SahneFonts.apply(rangeNumber, 300, -0.03f)
        rangeNumber.includeFontPadding = false
        // Kilit hapı her iki yazıya da sabit genişlikte: yazı değişince hap ve araç yerinden oynamaz.
        lockChip.minimumWidth = lockChip.paddingLeft + lockChip.paddingRight + activity.dp(24 + 10) + listOf(
            R.string.launcher_locked, R.string.launcher_unlocked, R.string.launcher_lock_waiting,
        ).maxOf { lockText.paint.measureText(activity.getString(it)).toInt() + 1 }
    }

    override val view: View get() = this

    override fun onVisible() {
        sources.forEach { it.onVisible() }
        TripSource.add(activity, tripListener)
        SahneCover.listener = artListener
        SahneCover.playingListener = playingListener
        mediaPlaying = SahneCover.playing
        onArtwork(SahneCover.current)
        visibleNow = true
        car.setActive(true)
        bindStrip()
    }

    override fun onHidden() {
        sources.forEach { it.onHidden() }
        TripSource.remove(tripListener)
        if (SahneCover.listener === artListener) SahneCover.listener = null
        if (SahneCover.playingListener === playingListener) SahneCover.playingListener = null
        visibleNow = false
        car.setActive(false)
        ambientAnim?.end()
    }

    override fun release() {
        detail?.dialog?.dismiss()
        detail = null
        vehicle.onSummary = null
        TripSource.remove(tripListener)
        if (SahneCover.listener === artListener) SahneCover.listener = null
        if (SahneCover.playingListener === playingListener) SahneCover.playingListener = null
        visibleNow = false
        car.setActive(false)
        ambientAnim?.cancel()
        main.removeCallbacks(lightsIgnite)
        main.removeCallbacks(brakeSettle)
        welcome = false
        shiftAnim?.cancel()
        centerAnim?.cancel()
        centerAnim = null
        shiftAnim = null
        lock.onStripChanged = null
        artKey = null
        sources.forEach { it.release() }
    }

    override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean = locked

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean = locked

    private fun bindVehicle(s: VehicleHeroCard.Summary?) {
        val range = s?.rangeKm
        rangeNumber.setTextSmooth(range?.toString() ?: activity.getString(R.string.launcher_unavailable), animate = false)
        rangeNumber.setTextColor(if (range != null) Sahne.NUMERAL else Sahne.INK_3)
        rangeUnit.text = activity.getString(R.string.launcher_sahne_km)
        rangeUnit.visibility = if (range != null) View.VISIBLE else View.GONE
        val fuel = s?.fuel
        gauge.set(fuel, s?.lowFuel == true)
        fuelText.text = if (fuel != null) activity.getString(R.string.launcher_percent, fuel) else activity.getString(R.string.launcher_unavailable)
        fuelText.setTextColor(if (s?.lowFuel == true) Sahne.AMBER else Sahne.INK)
        rangeBlock.contentDescription = listOfNotNull(
            range?.let { activity.getString(R.string.launcher_range_value, it) + " " + activity.getString(R.string.launcher_range_label) },
            fuel?.let { activity.getString(if (s.lowFuel) R.string.launcher_fuel_low else R.string.launcher_fuel_short, it) },
        ).joinToString(", ")
        val o = s?.openings
        car.setOpen(o?.carParts() ?: s?.openParts.orEmpty(), animate = carSeeded)
        car.setGlass(o?.windows.orEmpty(), o?.sunroof == true, animate = carSeeded)
        car.setRoof(o?.roof, animate = carSeeded)
        car.setTireAlert(if (s?.tireAlert == true) s.tireLow?.wheel else null)
        lastParts = o?.carParts() ?: s?.openParts.orEmpty()
        setTrunkOpen(CarPart.TRUNK in lastParts, animate = carSeeded && visibleNow)
        lastWindows = o?.windows.orEmpty()
        lastSunroof = o?.sunroof == true
        onCar?.invoke(lastParts, lastWindows, lastSunroof, carSeeded)
        if (s != null) carSeeded = true
        openings = o
        waiting = s == null || (s.rangeKm == null && s.fuel == null && s.locked == null && o == null)
        applyWaiting()
        bindPartAlerts()
        bindStrip()
        refreshDetail()
        bindLock(lock.stripState())
        requestLayout()
    }

    private fun applyWaiting() {
        rangeBlock.visibility = if (!showRange) View.GONE else if (waiting) View.INVISIBLE else View.VISIBLE
        // Veri yokken gri iskelet çubuğu çizilmez (boş gri blok gibi görünüyordu); "Araç verisi bekleniyor" notu yeter.
        skeleton.visibility = View.GONE
        waitNote.visibility = if (waiting) View.VISIBLE else View.GONE
        lockChip.visibility = if (waiting && hideLockWhileWaiting) View.INVISIBLE else View.VISIBLE
    }

    /** Arabada yanan parçalar (§10 genel kural): yakıt kapağı açıkken kapak, motor suyu / akü uyarısında kaput. */
    private fun bindPartAlerts() {
        val s = vehicle.summary
        val health = if (waiting || s == null) emptyList() else HealthMath.issues(trip.batteryV, null, null, null, trip.batteryHighHeld)
        car.setPartAlerts(
            fuelCapOpen = !waiting && s?.fuelCapOpen == true,
            engine = health.any { it == HealthMath.Issue.ENGINE_HOT || it == HealthMath.Issue.BATTERY_LOW || it == HealthMath.Issue.BATTERY_HIGH },
        )
    }

    /** Şerit: motor suyu / akü, düşük lastik, yakıt kapağı, yakıt az, bakım, kötü hava ([MiniAlerts]); veri beklenirken gizli. Sürüşte kenar ışığı güçlü. */
    private fun bindStrip() {
        val s = vehicle.summary
        val list = if (waiting || s == null) emptyList() else MiniAlerts.build(
            tireAlert = s.tireAlert, tireLow = s.tireLow, lowFuel = s.lowFuel,
            serviceKm = trip.serviceKm, serviceDays = trip.serviceDays, pm25In = trip.pm25In,
            batteryV = trip.batteryV, batteryHighHeld = trip.batteryHighHeld, fuelCapOpen = s.fuelCapOpen,
        )
        strip.bind(list) { stripText(activity, it) }
        syncCenter(animate = carSeeded)
        strip.setDriving(if (LauncherDemo.enabled) LauncherDemo.moving == true else trip.moving == true)
    }

    /** Şerit dokunuşu: yakıt azda en yakın istasyon araması (Yandex, yoksa genel harita); diğerlerinde araç detayı. */
    private fun onStripTap(item: MiniAlerts.Item?) {
        if (locked) return
        if (item?.kind == MiniAlerts.Kind.FUEL_LOW) openNearestStation(activity) else openDetail()
    }

    /** Araç detayı: tek sayfa; düzenleme modunda ya da etkinlik kapanırken açılmaz. */
    private fun openDetail() {
        if (locked || detail != null || activity.isFinishing || activity.isDestroyed) return
        val sheet = CarDetailSheet(activity)
        detail = sheet
        refreshDetail()
        sheet.show { detail = null }
    }

    private fun refreshDetail() {
        val sheet = detail ?: return
        val s = vehicle.summary
        val extra = if (LauncherDemo.enabled) ReadOnlyDemo.extra() else ExtraSource.last
        sheet.bind(CarDetail.model(s?.openings, extra, trip, s?.lowFuel), s?.openParts.orEmpty())
    }

    /**
     * Kilit hapı: kilit dinleyicisinden; bir kapı açıksa kilitli gösterilmez (açık kapıyla "Kilitli" çelişir). Henüz okunmadıysa
     * nötr "Bekleniyor". Açıktan kilitliye geçişte dörtlüler bir kez yanar.
     */
    private fun bindLock(s: StripState?) {
        val known = s != null && s.tone != StripTone.MUTED
        val doorOpen = openings?.anyDoor == true
        val unlocked = doorOpen || s?.icon == R.drawable.ic_mdi_lock_open_variant
        lockText.text = when {
            doorOpen -> activity.getString(R.string.launcher_unlocked)
            known -> s!!.value
            else -> activity.getString(R.string.launcher_lock_waiting)
        }
        val live = known || doorOpen
        // Dörtlü kenarı yalnız gerçek kilit sinyalinden: açık kapı hapı "Açık" gösterir ama kilit durumu sayılmaz (kilitliyken
        // içeriden açılıp kapanan kapı sahte "kilitlendi" parlaması yapmasın).
        if (known) {
            val nowLocked = s!!.icon != R.drawable.ic_mdi_lock_open_variant
            if (lastLocked == false && nowLocked) car.flashHazard()
            lastLocked = nowLocked
        }
        lockIcon.setImageResource(if (unlocked) R.drawable.ic_sahne_lock_open else R.drawable.ic_sahne_lock)
        lockIcon.imageTintList = ColorStateList.valueOf(if (!live) Sahne.INK_3 else Sahne.ICE)
        lockText.setTextColor(if (!live) Sahne.INK_3 else Sahne.INK)
        lockChip.contentDescription = activity.getString(R.string.launcher_widget_lock) + ", " + lockText.text
    }

    // --- Ortam ışığı ---

    /** Yeni kapak: baskın renk arka planda küçük bir kopyadan bir kez hesaplanır (aynı kapak için yeniden hesaplanmaz). */
    private fun onArtwork(b: Bitmap?) {
        val key = b?.let { System.identityHashCode(it) to it.generationId }
        if (key == artKey) return
        artKey = key
        if (b == null || b.isRecycled) {
            coverColor = null
            refreshAmbient()
            return
        }
        artIo.runSafely {
            val c = AmbientColor.of(b)
            main.post { if (artKey == key) { coverColor = c; refreshAmbient() } }
        }
    }

    /** Kapak rengi, çalma durumu ve sürüş modundan ışığı seçer (mod değişince etkinlik yeniden kurulduğu için mod burada okunur). */
    private fun refreshAmbient() = setAmbient(AmbientModeColor.decide(coverColor, mediaPlaying, LauncherTheme.driveMode))

    /** Ortam rengini yumuşakça değiştirir: arka görünüm yeni renkte belirir, öndeki söner (yalnızca alfa canlanır). */
    private fun setAmbient(color: Int?) {
        if (color == ambientColor) return
        ambientColor = color
        ambientAnim?.cancel()
        val from = ambient[ambientFront]
        val to = ambient[1 - ambientFront]
        if (color != null) {
            to.setColor(color)
            to.alpha = 0f
            to.visibility = View.VISIBLE
            ambientFront = 1 - ambientFront
        }
        val fromStart = from.alpha
        val target = if (color != null) to else null
        if (welcome || !visibleNow || !isAttachedToWindow) {
            // Karşılama sürerken ekran karanlık: ışık yakılmaz, karşılama sonunda [ambientRest] ile durgun hale gelinir.
            from.animate().cancel()
            target?.animate()?.cancel()
            from.alpha = 0f
            from.visibility = View.GONE
            target?.alpha = if (welcome) 0f else 1f
            return
        }
        ambientAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = AMBIENT_FADE_MS
            addUpdateListener { a ->
                val f = a.animatedFraction
                from.alpha = fromStart * (1f - f)
                target?.alpha = f
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    from.alpha = 0f
                    from.visibility = View.GONE
                    target?.alpha = 1f
                    if (ambientAnim === animation) ambientAnim = null
                }
            })
            start()
        }
    }

    // --- Yerleşim (taslak 960 genişlik) ---

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        floor.measure(exactly(w), exactly(h))
        val loose = MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST)
        val looseH = MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST)
        rangeBlock.measure(loose, looseH)
        lockChip.measure(MeasureSpec.makeMeasureSpec(w / 2, MeasureSpec.AT_MOST), exactly(context.dp(LauncherTheme.TOUCH_MIN_DP)))
        fitCar(w, h)
        carW = carBox.width.toInt()
        car.measure(exactly(car.widthFor(carW)), exactly(car.heightFor(carW)))
        floor.setCar(frameBox)
        val aw = (frameBox.width * AMBIENT_RX * 2).toInt()
        val ah = (frameBox.height * AMBIENT_RY * 2).toInt()
        ambient.forEach { it.measure(exactly(aw), exactly(ah)) }
        val side = side(w)
        strip.measure(MeasureSpec.makeMeasureSpec(w - 2 * side, MeasureSpec.AT_MOST), exactly(context.dp(StageStrip.HEIGHT_DP)))
        skeleton.measure(exactly(context.dp(Skeleton.W_DP)), exactly(context.dp(Skeleton.H_DP)))
        waitNote.measure(MeasureSpec.makeMeasureSpec((w * 0.8f).toInt(), MeasureSpec.AT_MOST), looseH)
    }

    private fun side(w: Int): Int = (SIDE * w / DESIGN_W).toInt()

    private fun chipTop(w: Int): Int = (SHOULDER_TOP * w / DESIGN_W).toInt()

    /** Menzil bloğunun üstü: rakam satırı kilit hapıyla aynı orta çizgide, yakıt çubuğu altında. */
    private fun rangeTop(w: Int): Int = chipTop(w) + (lockChip.measuredHeight - rangeRow.measuredHeight) / 2

    /** Şeridin üstü (şerit görünmese de araç bu payın üstünde kalır). */
    private fun stripTop(h: Int): Int = h - context.dp(STRIP_BOTTOM_DP + StageStrip.HEIGHT_DP)

    /**
     * Aracın kutusu: yatayda sahnenin tam ortası; üst engeller menzil satırı (rakam değişince araç zıplamasın diye üç hane
     * genişliğinde sabit pay) ve kilit hapı (sabit genişlik), alt sınır şerit payı. Genişlik [CAR_W_MAX] ile [CAR_W_MIN] arası.
     */
    private fun fitCar(w: Int, h: Int) {
        val k = w / DESIGN_W
        val side = side(w)
        val rTop = rangeTop(w)
        // Sabit pay: üç haneli menzil ve "%100" (değer değişince araç zıplamasın).
        val reserve = maxOf(
            rangeRow.measuredWidth - rangeNumber.measuredWidth + rangeNumber.paint.measureText("000").toInt() + 1,
            fuelRow.measuredWidth - fuelText.measuredWidth + fuelText.paint.measureText(activity.getString(R.string.launcher_percent, 100)).toInt() + 1,
        )
        val rangeRight = if (showRange) side + maxOf(rangeBlock.measuredWidth, reserve) else side
        val cTop = chipTop(w)
        val chipLeft = w - side - lockChip.measuredWidth
        val maxBottom = stripTop(h) - context.dp(CAR_STRIP_GAP_DP)
        val key = listOf(w, h, if (showRange) 1 else 0, rTop, rangeRight, rangeBlock.measuredHeight, chipLeft, cTop + lockChip.measuredHeight, maxBottom)
        if (key == fitKey) return
        fitKey = key
        val obstacles = listOfNotNull(
            if (showRange) StageFit.Box(side.toFloat(), rTop.toFloat(), rangeRight.toFloat(), (rTop + rangeBlock.measuredHeight).toFloat()) else null,
            StageFit.Box(chipLeft.toFloat(), cTop.toFloat(), (w - side).toFloat(), (cTop + lockChip.measuredHeight).toFloat()),
        )
        if (vertical) {
            // Dikey: ortada dar ve uzun kutu (en = boy × CAR_ASPECT); araç yatay kutuda çizilip merkez etrafında döner.
            // Bagaj aşağı (şeride doğru) açılır, yana kayma yok.
            val upright = StageFit.centered(
                centerX = w / 2f, minTop = context.dpF(6f), maxBottom = maxBottom.toFloat(),
                maxW = CAR_W_MAX * k * CAR_ASPECT, minW = CAR_W_MIN * k * CAR_ASPECT * VERTICAL_MIN_SCALE,
                aspect = 1f / CAR_ASPECT, gap = context.dpF(FIT_GAP_DP), above = obstacles,
            )
            // Dikeyde gövde boyu çerçeveyi (sahne yüksekliği) aşmaz: gerçek boy oranı uygulanmaz, yalnız gövde boyu eşitlenir.
            frameBox = StageFit.lyingBody(upright, CAR_ASPECT)
            carBox = StageFit.scaled(frameBox, car.model.geometry.bodyScale)
            trunkShiftNeed = 0f
            centerNeed = 0f
            if (shiftAnim == null) applyShift(0f)
            syncCenter(false)
            return
        }
        // Uzun sahne (Tiggo 8): araç büyür, omuzlar ile şerit arasında ortalanır, bagaj kayması sahne kenarına göre.
        val tall = StageFit.isTall(w, h)
        val geo = car.model.geometry
        val trunkOverlayX = geo.parts.getValue(CarPart.TRUNK).overlay.x
        // Açık bagaj kapağının çerçevenin solundan taşması (çerçeve genişliğine oran; ölçekli tuvalde ölçeğe göre).
        val trunkFrac = StageFit.frameOverflowLeft(trunkOverlayX / CarAnimSpec.BASE_W, geo.stageScale)
        // Ölçekli modelde burun da çerçevenin sağından taşar (Tiggo 8); ölçek 1'de 0 ve hesap eskisiyle aynı.
        val noseFrac = geo.bodySize?.let { StageFit.frameOverflowRight(it.x1 / CarAnimSpec.BASE_W, geo.stageScale) } ?: 0f
        val maxW = if (tall) StageFit.tallMaxWidth(w.toFloat(), CAR_W_MAX * k, context.dpF(FIT_GAP_DP), trunkFrac + noseFrac) else CAR_W_MAX * k
        frameBox = StageFit.centered(
            centerX = w / 2f, minTop = context.dpF(6f), maxBottom = maxBottom.toFloat(),
            maxW = maxW, minW = CAR_W_MIN * k, aspect = CAR_ASPECT, gap = context.dpF(FIT_GAP_DP),
            above = obstacles,
            cornerInset = CORNER_INSET,
        ).let { box ->
            if (tall) return@let box
            // Araç kalan boşluğun ortası yerine şerit payına oturur (biraz aşağıda): omuzlarla dikey pay büyür.
            val drop = (maxBottom - box.bottom).coerceAtLeast(0f)
            box.copy(top = box.top + drop, bottom = box.bottom + drop)
        }
        carBox = StageFit.scaled(frameBox, geo.stageScale)
        // Açık bagaj kapağı gövdenin solundan taşar (base'de -79 px; bkz. CarAnimSpec TRUNK overlay); menzil bloğuyla en az
        // [FIT_GAP_DP] kalacak kadar sağa kayılır.
        val trunkLeft = carBox.left + carBox.width * trunkOverlayX / CarAnimSpec.BASE_W
        trunkShiftNeed = if (tall) StageFit.tallTrunkShift(w.toFloat(), frameBox, context.dpF(FIT_GAP_DP), trunkFrac, noseFrac)
        else (rangeRight + context.dpF(FIT_GAP_DP) - trunkLeft).coerceIn(0f, context.dpF(TRUNK_SHIFT_MAX_DP))
        if (shiftAnim == null) applyShift(if (trunkOpen) trunkShiftNeed else 0f)
        // Dikey ekranda şerit yokken araç sahnenin tam ortasında durur (kutu şerit payına göre yerleştiği için yukarıda kalır).
        centerNeed = if (VehicleProfile.active.portrait) {
            SahnePortrait.centerDrop(h.toFloat(), frameBox.top, frameBox.height, context.dpF(CENTER_BOTTOM_DP))
        } else 0f
        syncCenter(false)
    }

    private fun stripShown(): Boolean = strip.visibility == View.VISIBLE

    /** Şerit görünürken araç yukarıda (şeride yer açar), değilken ortada; geçiş bagaj kaymasıyla aynı eğriyle. */
    private fun syncCenter(animate: Boolean) {
        val target = if (stripShown()) 0f else centerNeed
        if (centerAnim != null && !animate) return
        if (target == centerDy && centerAnim == null) return
        // Aynı hedefe giden geçiş sürüyorsa dokunulmaz: aynı anda gelen ikinci çağrı (araç + yolculuk verisi) geçişi iptal edip
        // aracı son yerine sıçratırdı.
        if (centerAnim != null && target == centerTarget) return
        // Alan önce boşaltılır: cancel() bitiş dinleyicisini hemen çağırır, dinleyici de son konuma atlardı.
        centerAnim?.let { centerAnim = null; it.cancel() }
        centerTarget = target
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            applyCenter(target)
            return
        }
        val from = centerDy
        centerAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = TRUNK_SHIFT_MS
            interpolator = android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener { a -> applyCenter(from + (target - from) * a.animatedFraction) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (centerAnim === animation) {
                        centerAnim = null
                        applyCenter(if (stripShown()) 0f else centerNeed)
                    }
                }
            })
            start()
        }
    }

    private fun applyCenter(dy: Float) {
        centerDy = dy
        car.translationY = dy
        ambient.forEach { it.translationY = dy }
        floor.setShift(trunkShift, dy)
    }

    /** Bagaj açılınca / kapanınca araç ve zemini yumuşakça kayar (yalnızca konum canlanır; ilk durumda anında). */
    private fun setTrunkOpen(open: Boolean, animate: Boolean) {
        if (open == trunkOpen) return
        trunkOpen = open
        shiftAnim?.cancel()
        shiftAnim = null
        val target = if (open) trunkShiftNeed else 0f
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            applyShift(target)
            return
        }
        val from = trunkShift
        shiftAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = TRUNK_SHIFT_MS
            interpolator = android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener { a -> applyShift(from + (target - from) * a.animatedFraction) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (shiftAnim === animation) {
                        shiftAnim = null
                        applyShift(if (trunkOpen) trunkShiftNeed else 0f)
                    }
                }
            })
            start()
        }
    }

    private fun rotateAbout(v: View, cx: Float, cy: Float) {
        if (!vertical) {
            if (v.rotation != 0f) v.rotation = 0f
            return
        }
        v.pivotX = cx - v.left
        v.pivotY = cy - v.top
        v.rotation = VERTICAL_TURN
    }

    private fun applyShift(dx: Float) {
        trunkShift = dx
        car.translationX = dx
        ambient.forEach { it.translationX = dx }
        floor.setShift(dx, centerDy)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        floor.layout(0, 0, w, h)
        val acx = (carBox.left + carBox.right) / 2f
        val acy = frameBox.top + frameBox.height * AMBIENT_CY
        // Dikeyde araç, ortam ışığı ve zemin gövde kutusunun merkezi etrafında -90° döner (burun yukarı).
        val pcx = (carBox.left + carBox.right) / 2f
        val pcy = (carBox.top + carBox.bottom) / 2f
        ambient.forEach {
            val al = (acx - it.measuredWidth / 2f).toInt()
            val at = (acy - it.measuredHeight / 2f).toInt()
            it.layout(al, at, al + it.measuredWidth, at + it.measuredHeight)
            rotateAbout(it, pcx, pcy)
        }
        val carL = carBox.left.toInt() - car.carLeft(carW)
        val carT = carBox.top.toInt() - car.carTop(carW)
        car.layout(carL, carT, carL + car.measuredWidth, carT + car.measuredHeight)
        rotateAbout(car, pcx, pcy)
        floor.setTurn(if (vertical) VERTICAL_TURN else 0f, pcx, pcy)
        val side = side(w)
        val rTop = rangeTop(w)
        rangeBlock.layout(side, rTop, side + rangeBlock.measuredWidth, rTop + rangeBlock.measuredHeight)
        val cTop = chipTop(w)
        lockChip.layout(w - side - lockChip.measuredWidth, cTop, w - side, cTop + lockChip.measuredHeight)
        // Şerit aracın altında, sahnenin ortasında.
        val sTop = stripTop(h)
        val sLeft = (w - strip.measuredWidth) / 2
        strip.layout(sLeft, sTop, sLeft + strip.measuredWidth, sTop + strip.measuredHeight)
        val skTop = rTop + (rangeBlock.measuredHeight - skeleton.measuredHeight) / 2
        skeleton.layout(side, skTop, side + skeleton.measuredWidth, skTop + skeleton.measuredHeight)
        val wl = (w - waitNote.measuredWidth) / 2
        val wb = h - context.dp(STRIP_BOTTOM_DP)
        waitNote.layout(wl, wb - waitNote.measuredHeight, wl + waitNote.measuredWidth, wb)
    }

    private fun exactly(px: Int) = MeasureSpec.makeMeasureSpec(px, MeasureSpec.EXACTLY)

    private fun sahneIcon(res: Int, tint: Int) = ImageView(activity).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(tint)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Veri gelmeden menzil yerinde tek sabit iskelet çubuk (parıltı döngüsü yok): gece #1A1E25, gündüz #E6E4E0. */
    private class Skeleton(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; visibility = GONE }

        override fun onDraw(canvas: Canvas) {
            paint.color = if (LauncherTheme.night) 0xFF1A1E25.toInt() else 0xFFE6E4E0.toInt()
            val r = context.dpF(6f)
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(rect, r, r, paint)
        }

        companion object {
            const val W_DP = 180
            const val H_DP = 40
        }
    }

    /** İnce yakıt çubuğu: iz ve buz mavisi gradyan dolgu; düşük yakıtta kehribar. Yalnızca değer değişince çizilir. */
    internal class FuelGauge(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private var percent: Int? = null
        private var low = false
        private var fillW = -1f
        private var fill: android.graphics.Shader? = null

        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

        fun set(p: Int?, low: Boolean) {
            if (p == percent && low == this.low) return
            percent = p
            this.low = low
            fillW = -1f
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val h = height.toFloat()
            val r = h / 2f
            paint.shader = null
            paint.color = Sahne.TRACK
            rect.set(0f, 0f, width.toFloat(), h)
            canvas.drawRoundRect(rect, r, r, paint)
            val p = percent ?: return
            val w = maxOf(h, width * p.coerceIn(0, 100) / 100f)
            if (w != fillW) {
                fillW = w
                fill = android.graphics.LinearGradient(0f, 0f, w, 0f, Sahne.ICE_LO, Sahne.ICE, android.graphics.Shader.TileMode.CLAMP)
            }
            // Gradyan ayrı alanda saklanır: iz çizimi shader'ı sıfırladığı için her çizimde yeniden takılır
            // (yoksa ikinci çizimde dolgu düz kehribar kalıyordu).
            paint.shader = if (low) null else fill
            paint.color = Sahne.AMBER
            rect.set(0f, 0f, w, h)
            canvas.drawRoundRect(rect, r, r, paint)
        }
    }

    /**
     * Ortam ışığı katmanı: aracın altında kapak renginde çok hafif eliptik ışık. Gradyan yalnızca boyut ya da renk değişince
     * kurulur; geçişte yalnızca görünüm alfası değişir (çakışan çizim yok, ekran dışı katman açılmaz).
     */
    private class AmbientView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var color = 0
        private var builtW = -1
        private var builtH = -1
        private var builtColor = 0

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            alpha = 0f
            visibility = GONE
        }

        fun setColor(c: Int) {
            if (c == color) return
            color = c
            invalidate()
        }

        override fun hasOverlappingRendering(): Boolean = false

        override fun onDraw(canvas: Canvas) {
            val w = width
            val h = height
            if (w <= 0 || h <= 0) return
            if (w != builtW || h != builtH || color != builtColor) {
                builtW = w
                builtH = h
                builtColor = color
                val a = if (LauncherTheme.night) AMBIENT_ALPHA_NIGHT else AMBIENT_ALPHA_DAY
                paint.shader = SahneGround.ellipse(w / 2f, h / 2f, w / 2f, h / 2f, (color and 0x00FFFFFF) or (a shl 24), 1f)
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }
    }

    /**
     * Sahne zemini (bitmap değil, kodla): yukarıdan düşen ışık konisi, aracın altındaki iki katmanlı ışık havuzu, geniş yumuşak
     * gölge ve sıkı temas gölgesi; gece ön farların ve stop lambalarının zemindeki yansıması. Halka / elips çizgisi yok (§1).
     * Gradyanlar yalnızca boyut değişince kurulur; görünüm statiktir.
     */
    private class FloorView(context: Context) : View(context) {
        /** Aracın gövde kutusu: zemin ışığı ve gölgeler araçla birlikte taşınır ve büyür. */
        private var carBox: StageFit.Box? = null

        fun setCar(box: StageFit.Box) {
            if (box == carBox) return
            carBox = box
            builtW = -1
            invalidate()
        }

        /** Dikey araç: zemin ışıkları ve gölgeler aracın merkezi etrafında döner. */
        private var turn = 0f
        private var turnX = 0f
        private var turnY = 0f

        fun setTurn(deg: Float, cx: Float, cy: Float) {
            if (deg == turn && cx == turnX && cy == turnY) return
            turn = deg
            turnX = cx
            turnY = cy
            invalidate()
        }

        /** Yatay kayma (bagaj açıkken): gradyanlar yeniden kurulmaz, yalnızca tuval kaydırılır. */
        private var shift = 0f

        private var shiftY = 0f

        fun setShift(dx: Float, dy: Float = shiftY) {
            if (dx == shift && dy == shiftY) return
            shift = dx
            shiftY = dy
            invalidate()
        }

        private val cone = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pool = Paint(Paint.ANTI_ALIAS_FLAG)
        private val poolOuter = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val contact = Paint(Paint.ANTI_ALIAS_FLAG)
        private val head = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tail = Paint(Paint.ANTI_ALIAS_FLAG)
        private var builtW = -1
        private var builtH = -1

        private fun build(w: Int, h: Int) {
            builtW = w
            builtH = h
            // Taslak ölçüleri 510 birimlik aracın (sol 225, üst 146) çevresinde; hepsi aracın gerçek kutusuna oranlanır.
            val box = carBox ?: StageFit.Box(CAR_X_DESIGN * w / DESIGN_W, 0f, (CAR_X_DESIGN + CAR_W) * w / DESIGN_W, CAR_W * CAR_ASPECT * w / DESIGN_W)
            val k = box.width / CAR_W
            fun xx(v: Float) = box.left + (v - CAR_X_DESIGN) * k
            fun yy(v: Float) = box.top + (v - CAR_Y_DESIGN) * k
            val cx = xx(480f)
            cone.shader = SahneGround.ellipse(cx, yy(40f + 216f), 350f * k, 180f * k, Sahne.CONE, 1f)
            pool.shader = SahneGround.ellipse(cx, yy(270f), 400f * k, 150f * k, Sahne.FLOOR_INNER, 0.72f)
            poolOuter.shader = SahneGround.ellipse(cx, yy(270f), 560f * k, 210f * k, Sahne.FLOOR_OUTER, if (LauncherTheme.night) 0.72f else 0.74f)
            shadow.shader = SahneGround.ellipse(cx, yy(292f), 290f * k, 78f * k, Sahne.CAR_SHADOW, 1f)
            contact.shader = SahneGround.ellipse(cx, yy(270f), 250f * k, 58f * k, Sahne.CAR_CONTACT, 1f)
            // Burun sağda: farlar sağda (soğuk beyaz), stoplar solda (kırmızı); yalnızca gece.
            head.shader = if (Sahne.HEAD_GLOW == 0) null else SahneGround.ellipse(xx(760f), yy(268f), 150f * k, 70f * k, Sahne.HEAD_GLOW, 1f)
            tail.shader = if (Sahne.TAIL_GLOW == 0) null else SahneGround.ellipse(xx(212f), yy(268f), 70f * k, 56f * k, Sahne.TAIL_GLOW, 1f)
        }

        override fun onDraw(canvas: Canvas) {
            val w = width
            val h = height
            if (w <= 0 || h <= 0) return
            if (w != builtW || h != builtH) build(w, h)
            val save = canvas.save()
            canvas.translate(shift, shiftY)
            var l = -shift
            var r = w - shift
            var t = -shiftY
            var b = h - shiftY
            if (turn != 0f) {
                // Döndürülmüş tuvalde dikdörtgen tüm sahneyi örtsün (gradyan kenarları zaten saydam).
                canvas.rotate(turn, turnX, turnY)
                val span = maxOf(w, h).toFloat()
                l = turnX - span; r = turnX + span; t = turnY - span; b = turnY + span
            }
            canvas.drawRect(l, t, r, b, cone)
            canvas.drawRect(l, t, r, b, poolOuter)
            canvas.drawRect(l, t, r, b, pool)
            if (head.shader != null) canvas.drawRect(l, t, r, b, head)
            if (tail.shader != null) canvas.drawRect(l, t, r, b, tail)
            canvas.drawRect(l, t, r, b, shadow)
            canvas.drawRect(l, t, r, b, contact)
            canvas.restoreToCount(save)
        }
    }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val DESIGN_W = 960f
        /** Zemin çiziminin taslaktaki araç kutusu (sol, üst; 510 birim genişlik): ışık ve gölge bunlara göre tanımlı. */
        const val CAR_X_DESIGN = 225f
        const val CAR_Y_DESIGN = 146f
        const val CAR_W = 510f
        /** Araç genişliği sınırları (tasarım birimi): sığdığı kadar büyük, ortada (omuzlar köşeye biraz girebilir). */
        const val CAR_W_MAX = 640f
        const val CAR_W_MIN = 420f
        /** Omuzlar aracın köşesine girmez (köşe payı yok); araç ile menzil / kilit arası en az [FIT_GAP_DP] (16dp). */
        const val CORNER_INSET = 0f
        const val FIT_GAP_DP = 16f
        /** Bagaj açıkken aracın en çok sağa kayması ve geçiş süresi. */
        const val TRUNK_SHIFT_MAX_DP = 60f
        const val TRUNK_SHIFT_MS = 420L
        /** Ortalanan aracın sahne altında bırakacağı en az boşluk. */
        const val CENTER_BOTTOM_DP = 8f
        const val FUEL_BAR_DP = 84
        /** Araç ile şerit payı arası. */
        const val CAR_STRIP_GAP_DP = 10
        /** Şeridin sahne altından uzaklığı. */
        const val STRIP_BOTTOM_DP = 8
        const val CAR_ASPECT = CarAnimSpec.BASE_H / CarAnimSpec.BASE_W
        /** Dikey araç: burun yukarı (yatay çizim saat yönünün tersine döner). */
        const val VERTICAL_TURN = -90f
        /** Dikeyde alçak sahnede (Tiggo 7) araç yatay en küçük boyun bu oranına kadar küçülebilir. */
        const val VERTICAL_MIN_SCALE = 0.5f
        /** Omuzlar sahnenin dış üst köşelerinde: kenar payı ve üst kenar (tasarım birimi). */
        const val SIDE = 8f
        const val SHOULDER_TOP = 4f
        /** Omuz yazıları: "km" ve "Kilitli" aynı boy ve ağırlık. */
        const val SHOULDER_SP = 22f
        const val SHOULDER_WEIGHT = 500
        const val RANGE_SP = 46f
        /** Ortam ışığı: aracın merkezinde, gövdeye göre yarıçaplar; renk alfası; geçiş süresi. */
        const val AMBIENT_RX = 0.66f
        const val AMBIENT_RY = 0.78f
        const val AMBIENT_CY = 0.55f
        const val AMBIENT_ALPHA_NIGHT = 0x3D
        const val AMBIENT_ALPHA_DAY = 0x2E
        const val AMBIENT_FADE_MS = 1_200L
        /** Karşılama: aracın başlangıç ölçeği, belirme süresi, ışıkların yanışı, stopların inişi, zemin ışığının başlangıcı. */
        const val WELCOME_SCALE = 0.94f
        const val WELCOME_CAR_MS = 950L
        const val WELCOME_LIGHTS_MS = 330L
        const val WELCOME_BRAKE_OFF_MS = 1_050L
        const val WELCOME_FLOOR_MS = 450L
        val WELCOME_EASE = android.view.animation.DecelerateInterpolator(1.6f)
    }
}

/**
 * Çalan şarkının kapağı (medya kartı bildirir): Sahne ortam ışığı için. Yalnızca kapak değişince [listener] çağrılır.
 * Ana iş parçacığında kullanılır.
 */
internal object SahneCover {
    var current: Bitmap? = null
        private set
    private var gen = -1
    var listener: ((Bitmap?) -> Unit)? = null

    /** Müzik çalıyor mu (medya kartı bildirir): kapaktan renk çıkmazsa ışık sürüş modu rengini yalnız çalarken taşır. */
    var playing: Boolean = false
        private set
    var playingListener: ((Boolean) -> Unit)? = null

    fun publishPlaying(p: Boolean) {
        if (p == playing) return
        playing = p
        playingListener?.invoke(p)
    }

    fun publish(b: Bitmap?) {
        val g = b?.generationId ?: -1
        if (b === current && g == gen) return
        current = b
        gen = g
        listener?.invoke(b)
    }
}

/**
 * Kapağın baskın rengi (ortam ışığı için): 24 × 24 kopyada doygun ve karanlık olmayan piksellerin ton kümesi; en ağır
 * kümenin ortalaması doygunluk ve parlaklığı sınırlanarak döner. Kapak gri / siyah-beyazsa null (ortam ışığı söner).
 */
internal object AmbientColor {
    private const val SIDE = 24
    private const val BINS = 24

    fun of(src: Bitmap): Int? {
        val small = try {
            Bitmap.createScaledBitmap(src, SIDE, SIDE, true)
        } catch (e: RuntimeException) {
            return null
        } catch (e: OutOfMemoryError) {
            return null
        }
        val px = IntArray(SIDE * SIDE)
        small.getPixels(px, 0, SIDE, 0, 0, SIDE, SIDE)
        if (small !== src) small.recycle()
        return dominant(px)
    }

    /** Saf hesap (JVM'de sınanır): ARGB pikseller → baskın renk ya da null. */
    fun dominant(px: IntArray): Int? {
        val weight = FloatArray(BINS)
        val sumR = FloatArray(BINS)
        val sumG = FloatArray(BINS)
        val sumB = FloatArray(BINS)
        var total = 0f
        for (c in px) {
            if ((c ushr 24) < 128) continue
            val r = (c shr 16 and 0xFF) / 255f
            val g = (c shr 8 and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val v = max
            val s = if (max <= 0f) 0f else (max - min) / max
            if (v < 0.15f || s < 0.2f) continue
            val hue = hue(r, g, b, max, min)
            val bin = ((hue / 360f) * BINS).toInt().coerceIn(0, BINS - 1)
            val wgt = s * v
            weight[bin] += wgt
            sumR[bin] += r * wgt
            sumG[bin] += g * wgt
            sumB[bin] += b * wgt
            total += wgt
        }
        if (total < px.size * 0.02f) return null
        var best = 0
        for (i in 1 until BINS) if (weight[i] > weight[best]) best = i
        val w = weight[best]
        val r = sumR[best] / w
        val g = sumG[best] / w
        val b = sumB[best] / w
        // Parlaklık 0,9'a çekilir, doygunluk 0,45..0,85 arasına sınırlanır (zeminde ne soluk ne cırtlak).
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val s = (if (max <= 0f) 0f else (max - min) / max).coerceIn(0.45f, 0.85f)
        val h = hue(r, g, b, max, min)
        return hsv(h, s, 0.9f)
    }

    private fun hue(r: Float, g: Float, b: Float, max: Float, min: Float): Float {
        val d = max - min
        if (d <= 0f) return 0f
        val h = when (max) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return if (h < 0f) h + 360f else h
    }

    private fun hsv(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1f - Math.abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when ((h / 60f).toInt() % 6) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}
