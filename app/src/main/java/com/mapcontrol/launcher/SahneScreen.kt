package com.mapcontrol.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.mapcontrol.R

/**
 * Sahne düzeninin orta alanı (başlık ile alt çubuk arası): sol panel yuvası · sabit araç sahnesi · sağ panel yuvası,
 * altta tek cam şeritte [STRIP_SLOTS] yuva. Yuvalardaki widget'lar yalnızca ekran görünürken çalışır.
 *
 * Düzenleme modunda her yuvanın üstünde "Değiştir" örtüsü vardır; dokununca o yuvaya uyan türleri gösteren seçici açılır
 * (panel ya da şerit seçkisi [SahneForm]; ekranda olanlar soluk). "Kaldır" yuvayı boşaltır (sessiz "+").
 * Değişiklikler hemen etkin moda ait [SahneStore]'a yazılır.
 */
@SuppressLint("ViewConstructor")
class SahneScreen(
    private val activity: Activity,
    /** Açılan sayfayı etkinliğe bildirir (boşta kararları ve kapatma için); kapanınca null. */
    private val onSheet: (Dialog?) -> Unit,
    /** Düzenleme işlemi şu an yapılabilir mi (araç dururken); değilse kendi uyarısını gösterir ve false döner. */
    private val canEdit: () -> Boolean,
) : ViewGroup(activity) {

    private val parkStore = SahneStore(activity, LayoutMode.PARK)
    private val driveStore = SahneStore(activity, LayoutMode.DRIVE)
    private var mode = LayoutMode.PARK
    private val store get() = if (mode == LayoutMode.DRIVE) driveStore else parkStore

    /** Kayıtlı ve ekranda gösterilen düzen (Park ve Sürüş'te her widget çalışır; gizlenen yuva yoktur). */
    private var stored: SahneLayout = parkStore.load()
    private val shown: SahneLayout get() = stored

    private val stage = SahneStage(activity)
    private val leftPanel = GlassPanel(activity, glow = false)
    private val rightPanel = GlassPanel(activity, glow = false)
    /** Dört panelli biçimde (Tiggo 8) her yanın alt paneli; iki panelli biçimde hiç eklenmez. */
    private val quad = parkStore.quad
    private val leftLowerPanel = GlassPanel(activity, glow = false)
    private val rightLowerPanel = GlassPanel(activity, glow = false)
    private val panels: List<GlassPanel> =
        if (quad) listOf(leftPanel, rightPanel, leftLowerPanel, rightLowerPanel) else listOf(leftPanel, rightPanel)
    private val strip = StripBar()

    /** Yuvalardaki çalışan widget'lar (görünürlük ve bırakma için). */
    private val hosted = LinkedHashMap<SahneSlotRef, List<LauncherWidget>>()
    private val overlays = LinkedHashMap<SahneSlotRef, SlotOverlay>()
    private var started = false

    var editMode = false
        set(value) {
            if (field == value) return
            field = value
            stage.locked = value
            // Spor ekranı açıkken düzenleme spor küçük kadranlarında yapılır (yuva örtüleri gizli kalır).
            overlays.values.forEach { it.visibility = if (value && !sportShown) View.VISIBLE else View.GONE }
            sport?.setEditing(value && sportShown)
            refreshEmpty()
        }

    init {
        clipChildren = false
        clipToPadding = false
        addView(stage)
        panels.forEach { addView(it) }
        // Dört panelde her panel Tiggo 7 panelinin tasarım yüksekliğinde kurulur ve sığacak kadar küçültülür.
        if (quad) panels.forEach { it.naturalHeightDp = NATURAL_PANEL_H_DP }
        addView(strip)
        panels.indices.forEach { addOverlay(SahneSlotRef(SahneSlotKind.PANEL, it)) }
        (0 until STRIP_SLOTS).forEach { addOverlay(SahneSlotRef(SahneSlotKind.STRIP, it)) }
        rebuildAll()
    }

    /**
     * Etkin düzende [type] türü bir panel yuvasında mı (alt çubuk telefon düğmesi: panel ekrandaysa sayfa açılmaz). Telefon
     * için medya kartı telefon yüzündeyse de evet.
     */
    fun hasPanel(type: WidgetType): Boolean = panelIndexOf(type) != null

    private fun panelIndexOf(type: WidgetType): Int? = panels.indices.firstOrNull {
        SahneSlots.get(shown, SahneSlotRef(SahneSlotKind.PANEL, it))?.type == type ||
            (type == WidgetType.PHONE && flipAt(it)?.face == CardFace.PHONE)
    }

    private fun flipAt(index: Int): MediaPhoneFlip? = hosted[SahneSlotRef(SahneSlotKind.PANEL, index)]?.firstOrNull() as? MediaPhoneFlip

    /**
     * Verilen türü taşıyan paneli kısa bir nabızla gösterir (ör. alt çubuk telefon düğmesi, panel zaten ekrandayken):
     * 120 ms'de %97'ye küçülüp geri döner; başka kare işi yok.
     */
    fun flashPanel(type: WidgetType) {
        if (sportShown) return
        val i = panelIndexOf(type) ?: return
        if (flipAt(i)?.moving == true) return
        val v = panels[i]
        v.animate().cancel()
        v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(FLASH_MS).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(FLASH_MS).start()
        }.start()
    }

    /** Karşılama ([SahneWelcome]) için: araç sahnesi ve geri kalan orta alan (yan paneller, şerit). */
    internal fun welcomeParts(): Pair<SahneStage, List<View>> = stage to panels + strip

    // --- Yaşam döngüsü ---

    fun onVisible() {
        started = true
        stage.onVisible()
        if (sportShown) sport?.onVisible() else hosted.values.flatten().forEach { it.onVisible() }
    }

    fun onHidden() {
        started = false
        stage.onHidden()
        if (sportShown) sport?.onHidden() else hosted.values.flatten().forEach { it.onHidden() }
    }

    fun release() {
        stage.onCar = null
        sport?.release()
        stage.release()
        hosted.values.flatten().forEach { it.release() }
        hosted.clear()
    }

    // --- Spor modu ---

    /** Spor ekranı (kronograf); ilk kez spor açılınca kurulur, Normal'de hiç yoktur (Sahne aynen kalır). */
    private var sport: SportStageView? = null

    /** Spor ekranı gösteriliyor mu (orta alanın yerine). */
    var sportShown = false
        private set

    /** Spor görünümü (gerekirse kurulur); geçişten önce bitmap'i hazırlamak için. */
    internal fun sportStage(): SportStageView = sport ?: SportStageView(activity, onCarTap = { stage.openCarDetail() }).also { v ->
        sport = v
        v.visibility = View.GONE
        addView(v)
        stage.onCar = { parts, windows, sunroof, animate -> v.setCar(parts, windows, sunroof, animate) }
    }

    /**
     * Orta alanı spor ekranıyla değiştirir ([on]) ya da geri getirir. Sahne yuvalarındaki widget'lar spor açıkken durur
     * (araç sahnesi açık kapı bilgisini spor araç modülüne verdiği için çalışmaya devam eder); geri gelince aynen sürer.
     * Düzenleme modunda spor açılmaz: false (çağıran çubukları da değiştirmez).
     */
    fun showSport(on: Boolean, fadeMs: Long = 0L): Boolean {
        if (on == sportShown) return true
        if (on && editMode) return false
        sportShown = on
        val v = if (on) sportStage() else sport ?: return true
        val normalViews = listOf<View>(stage) + panels + strip
        if (fadeMs > 0L) {
            // Sakin çapraz geçiş (araç giderken): iki görünüm de geçiş boyunca ekranda, yalnız alfa canlanır.
            val incoming = if (on) listOf<View>(v) else normalViews
            val outgoing = if (on) normalViews else listOf<View>(v)
            incoming.forEach { it.animate().cancel(); it.alpha = 0f; it.visibility = View.VISIBLE; it.animate().alpha(restAlpha(it)).setDuration(fadeMs).start() }
            outgoing.forEach { o ->
                o.animate().cancel()
                o.animate().alpha(0f).setDuration(fadeMs).withEndAction {
                    o.alpha = restAlpha(o)
                    if (sportShown == on) o.visibility = if (o === v) View.GONE else View.INVISIBLE
                }.start()
            }
        } else {
            (normalViews + v).forEach { it.animate().cancel(); it.alpha = restAlpha(it) }
            val normal = if (on) View.INVISIBLE else View.VISIBLE
            normalViews.forEach { it.visibility = normal }
            v.visibility = if (on) View.VISIBLE else View.GONE
        }
        if (!on) {
            // Düzenleme sürerken spor kapandıysa düzenleme Sahne yuvalarına döner.
            v.setEditing(false)
            if (editMode) overlays.values.forEach { it.visibility = View.VISIBLE }
        }
        if (started) {
            if (on) {
                hosted.values.flatten().forEach { it.onHidden() }
                v.onVisible()
            } else {
                v.onHidden()
                hosted.values.flatten().forEach { it.onVisible() }
            }
        }
        return true
    }

    /** Görünümün dinlenme alfası: boş panel soluk ([refreshEmpty]), diğerleri tam. */
    private fun restAlpha(v: View): Float {
        val i = panels.indexOf(v)
        if (i < 0) return 1f
        return panelAlpha(i)
    }

    /** Panelin dinlenme alfası: yer kaplamayan (kapalı) boş yan 0, boş panel soluk, dolu tam. */
    private fun panelAlpha(i: Int): Float = when {
        !SahneBand.occupies(shown, i, editMode) -> 0f
        SahneSlots.get(shown, SahneSlotRef(SahneSlotKind.PANEL, i)) == null && !editMode -> EMPTY_ALPHA
        else -> 1f
    }

    /** Spor tamamen kapandıktan sonra: büyük bitmap ve görünüm bırakılır. */
    fun dropSport() {
        if (sportShown) return
        val v = sport ?: return
        stage.onCar = null
        v.release()
        removeView(v)
        sport = null
    }

    /** Park/Sürüş değişti: o modun kayıtlı düzeni kurulur. */
    fun setMode(to: LayoutMode) {
        if (to == mode) return
        mode = to
        stored = store.load()
        rebuildAll()
    }

    /** Etkin modun düzeni varsayılana döner. */
    fun resetToDefaults() {
        store.reset()
        stored = store.defaults()
        rebuildAll()
    }

    private fun rebuildAll() {
        hosted.keys.toList().forEach { dropHosted(it) }
        panels.indices.forEach { build(SahneSlotRef(SahneSlotKind.PANEL, it)) }
        strip.clearSlots()
        (0 until STRIP_SLOTS).forEach { build(SahneSlotRef(SahneSlotKind.STRIP, it)) }
        strip.syncDividers()
        refreshEmpty()
        requestLayout()
    }

    private fun dropHosted(ref: SahneSlotRef) {
        hosted.remove(ref)?.forEach { w ->
            if (started && !sportShown) w.onHidden()
            w.release()
        }
    }

    /** [ref] yuvasının widget'ını kurar ve yerine koyar (eskisi durdurulup bırakılır). */
    private fun build(ref: SahneSlotRef) {
        dropHosted(ref)
        val slot = SahneSlots.get(shown, ref)
        val widgets: List<LauncherWidget>
        when (ref.kind) {
            SahneSlotKind.PANEL -> {
                val panel = panels[ref.index]
                if (slot == null) {
                    panel.removeAllViews()
                    widgets = emptyList()
                } else {
                    val w = SahnePanelFactory.create(activity, slot, calm = mode == LayoutMode.DRIVE, phonePanel = SahneForm.hasPhonePanel(shown))
                    val flip = w as? MediaPhoneFlip
                    // Medya ışıması yalnız kartın medya yüzünde (telefon yüzü sakin cam).
                    flip?.onFaceShown = { face -> panel.setGlow(face == CardFace.MEDIA) }
                    panel.setGlow(slot.type == WidgetType.MEDIA && flip?.face != CardFace.PHONE)
                    panel.host(w.view, SahnePanelFactory.panelPaddingDp(slot.type), SahneArt.panelBackground(activity, slot.type))
                    widgets = listOf(w)
                }
            }
            SahneSlotKind.STRIP -> {
                val (view, ws) = if (slot == null) View(activity) to emptyList() else stripView(slot)
                strip.setSlot(ref.index, view, slot)
                widgets = ws
            }
        }
        if (widgets.isNotEmpty()) {
            hosted[ref] = widgets
            if (started && !sportShown) widgets.forEach { it.onVisible() }
        }
    }

    /** Şerit öğesi: APP iki uygulamalık çift; diğerleri kendi kutucuğunun [StripSource] özetiyle. */
    private fun stripView(slot: SahneSlot): Pair<View, List<LauncherWidget>> {
        if (slot.type == WidgetType.APP) {
            val pkgs = SahneSlots.appPackages(slot.extra)
            val tiles = listOf(
                AppShortcutTile(activity, pkgs[0], slot.id + "_a"),
                AppShortcutTile(activity, pkgs[1], slot.id + "_b"),
            ).onEach { it.applySpan(Span(2, 2)) }
            return AppPairStripView(activity, tiles) to tiles
        }
        val span = SahneForm.stripSpan(slot.type)
        val widget = WidgetFactory.create(activity, PlacedWidget(slot.id, slot.type, span, slot.extra)).also { it.applySpan(span) }
        val source = widget as? StripSource
        val fallback = StripState(activity.getString(slot.type.label), "", slot.type.icon)
        var anchor: View? = null
        val item = StripItemView(activity,
            onTap = { part ->
                val a = anchor
                if (source == null) widget.view.performClick() else if (a != null) source.stripTapFrom(a, part) else source.stripTap(part)
            },
            onLong = { source?.stripLongPress() ?: false },
            type = slot.type,
        )
        anchor = item
        if (source != null) source.onStripChanged = { item.bind(source.stripState() ?: fallback) }
        item.bind(source?.stripState() ?: fallback)
        return item to listOf(widget)
    }

    // --- Düzenleme ---

    private fun addOverlay(ref: SahneSlotRef) {
        val o = SlotOverlay(ref)
        o.visibility = View.GONE
        overlays[ref] = o
        addView(o)
    }

    /** Boş yuvalar: düzenlemede "+" örtüsü, normalde soluk boş cam (panel) ya da boşluk (şerit). */
    private fun refreshEmpty() {
        overlays.forEach { (ref, o) -> o.bind(SahneSlots.get(shown, ref) != null) }
        panels.forEachIndexed { i, p -> p.alpha = panelAlpha(i) }
        // Boş yan düzenlemede açılır, dışında kapanır (sahne ortada kaldığından yerinden oynamaz).
        requestLayout()
    }

    private fun place(ref: SahneSlotRef, type: WidgetType) {
        val slot = SahneSlot(SahneSlots.newId(stored, type), type,
            if (type == WidgetType.APP) SahneSlots.appExtra(DefaultApps.installedDefaults(activity, DEFAULT_APP_SLOTS)) else null)
        apply(ref, slot)
    }

    private fun apply(ref: SahneSlotRef, slot: SahneSlot?) {
        val hadPhone = SahneForm.hasPhonePanel(stored)
        stored = SahneSlots.set(stored, ref, slot)
        store.save(stored)
        build(ref)
        // Telefon paneli geldi ya da gitti: medya kartı telefon sayfasını sunup sunmayacağına göre yeniden kurulur.
        if (hadPhone != SahneForm.hasPhonePanel(stored)) panels.indices.map { SahneSlotRef(SahneSlotKind.PANEL, it) }
            .filter { it != ref && SahneSlots.get(stored, it)?.type == WidgetType.MEDIA }.forEach { build(it) }
        if (ref.kind == SahneSlotKind.STRIP) strip.syncDividers()
        refreshEmpty()
        requestLayout()
    }

    /**
     * Yuva seçici: tam ekran (alt çubuğun üstü dahil), kaydırma yok; yuvaya uyan tüm türler tek ekranda küçük kutularla
     * ([PickerGrid]: sütun/satır sayısı öğe sayısı ve ekran ölçüsünden). Yerleştirilemeyenler soluk ve nedeni yazılı.
     * Dolu yuvada başlık satırında "Şu an: X" ve "Kaldır".
     */
    private fun openPicker(ref: SahneSlotRef) {
        if (!canEdit() || activity.isFinishing || activity.isDestroyed) return
        val current = SahneSlots.get(stored, ref)
        val types = SahneForm.offered(ref.kind, quad)
        val title = if (ref.kind == SahneSlotKind.PANEL) R.string.launcher_sahne_pick_panel else R.string.launcher_sahne_pick_strip
        val header: (LinearLayout.(Dialog) -> Unit)? = if (current == null) null else { dialog ->
            addView(text(context, LauncherTheme.LABEL_SP, LauncherTheme.TEXT_SECONDARY).apply {
                text = context.getString(R.string.launcher_sahne_in_slot, context.getString(current.type.label))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = context.dp(20)
            })
            addView(text(context, LauncherTheme.BODY_SP, LauncherTheme.TEXT, medium = true).apply {
                setText(R.string.launcher_edit_remove)
                gravity = Gravity.CENTER
                background = rounded(context, LauncherTheme.CONTROL, Sahne.CONTROL_RADIUS_DP)
                setPadding(context.dp(28), 0, context.dp(28), 0)
                isClickable = true
                setOnClickListener { dialog.dismiss(); apply(ref, null) }
                pressFeedback()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(LauncherTheme.TOUCH_MIN_DP)).apply {
                marginEnd = context.dp(16)
            })
        }
        val d = showLauncherSheet(activity, title, fullScreen = true, edgeToEdge = true, header = header) { dialog ->
            val grid = PickerGridLayout(context)
            types.forEach { grid.addView(pickerTile(ref, it, current) { dialog.dismiss() }) }
            // Normalde ızgara alana sığar ve kaydırma olmaz; çok fazla seçenekte (sığmazsa) kalanlar kaydırılarak erişilir.
            addView(android.widget.ScrollView(context).apply {
                isVerticalScrollBarEnabled = true
                overScrollMode = View.OVER_SCROLL_NEVER
                isFillViewport = true
                addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }, weightLp(1f, vertical = true))
        }
        d.setOnDismissListener { onSheet(null) }
        onSheet(d)
    }

    /**
     * Seçici ızgarası: verilen alanı (kaydırıcının görünen yüksekliği) [PickerGrid.fit] ile böler, kutuları tam ölçüde
     * yerleştirir. Izgara alanın ortasında durur; az öğede (panel seçici) kutular [PickerGrid.MAX_TILE_W_DP] genişlikte
     * kalır. Sığmazsa ([PickerFit.fits] false) içerik kadar uzar ve dıştaki kaydırıcı devreye girer.
     */
    private class PickerGridLayout(context: android.content.Context) : ViewGroup(context) {
        private var fit = PickerFit(0, 0, 0, 0, true)

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val h = MeasureSpec.getSize(heightMeasureSpec)
            val density = resources.displayMetrics.density
            fit = PickerGrid.fit(childCount, (w / density).toInt(), (h / density).toInt())
            val cw = MeasureSpec.makeMeasureSpec(context.dp(fit.tileW), MeasureSpec.EXACTLY)
            val ch = MeasureSpec.makeMeasureSpec(context.dp(fit.tileH), MeasureSpec.EXACTLY)
            for (i in 0 until childCount) getChildAt(i).measure(cw, ch)
            setMeasuredDimension(w, if (fit.fits) h else maxOf(h, contentH()))
        }

        private fun contentH() = if (fit.rows == 0) 0 else fit.rows * context.dp(fit.tileH) + (fit.rows - 1) * context.dp(PickerGrid.GAP_DP)

        private fun contentW() = if (fit.cols == 0) 0 else fit.cols * context.dp(fit.tileW) + (fit.cols - 1) * context.dp(PickerGrid.GAP_DP)

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            if (fit.cols == 0) return
            val gap = context.dp(PickerGrid.GAP_DP)
            val tw = context.dp(fit.tileW)
            val th = context.dp(fit.tileH)
            // Boş alan kalmasın diye blok ortalanır (Tiggo 7'de iki satır ekranın üst köşesinde kalıyordu).
            val x0 = maxOf(0, (r - l - contentW()) / 2)
            val y0 = maxOf(0, (b - t - contentH()) / 2)
            for (i in 0 until childCount) {
                val x = x0 + (i % fit.cols) * (tw + gap)
                val y = y0 + (i / fit.cols) * (th + gap)
                getChildAt(i).layout(x, y, x + tw, y + th)
            }
        }
    }

    /**
     * Seçici kutusu: ikon ve ad tek satırda, altında küçük ikinci satır ("Zaten ekranda" / "Şu an") yalnız gerektiğinde.
     * Tek ikon dili (ince çizgili Sahne ikonları), tek başlık ölçüsü.
     */
    private fun pickerTile(ref: SahneSlotRef, type: WidgetType, current: SahneSlot?, dismiss: () -> Unit): View {
        val here = current?.type == type
        val ok = !here && SahneForm.canPlace(stored, ref, type)
        val ink = if (ok || here) LauncherTheme.TEXT else LauncherTheme.TEXT_SECONDARY
        val sub = when {
            here -> activity.getString(R.string.launcher_sahne_in_this_slot)
            !ok -> activity.getString(R.string.launcher_edit_on_screen)
            else -> ""
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(context, LauncherTheme.CONTROL, Sahne.CONTROL_RADIUS_DP, if (here) LauncherTheme.ACCENT else 0)
            setPadding(context.dp(14), 0, context.dp(12), 0)
            addView(icon(context, sahneIconFor(type.icon), PICKER_ICON_DP, ink))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(text(context, PICKER_TITLE_SP, ink, medium = true).apply {
                    setText(type.label)
                    // Tek satır: uzun ad (ör. "Uygulama kısayolu") kesilmesin diye yazı kutuya sığana dek küçülür.
                    // Otomatik küçültme sabit yükseklik ister (wrap_content'te güvenilmez): bir satırlık yükseklik verilir.
                    setAutoSizeTextTypeUniformWithConfiguration(PICKER_TITLE_MIN_SP, PICKER_TITLE_SP.toInt(), 1,
                        android.util.TypedValue.COMPLEX_UNIT_SP)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_SP, PICKER_TITLE_SP * PICKER_TITLE_LINE, resources.displayMetrics).toInt()))
                if (sub.isNotEmpty()) addView(text(context, PICKER_SUB_SP, LauncherTheme.TEXT_SECONDARY).apply {
                    text = sub
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, sheetFullWidth())
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = context.dp(10) })
            alpha = if (ok || here) 1f else DISABLED_ALPHA
            contentDescription = if (sub.isEmpty()) context.getString(type.label) else context.getString(type.label) + ", " + sub
            isClickable = true
            isFocusable = true
            setOnClickListener {
                // Yuvadaki türe dokunmak seçiciyi kapatır (değişiklik yok); yerleştirilemeyen tür hiçbir şey yapmaz.
                if (here) { dismiss(); return@setOnClickListener }
                if (!ok) return@setOnClickListener
                dismiss()
                place(ref, type)
            }
            pressFeedback()
        }
    }

    /**
     * Düzenleme örtüsü: yuvanın içeriği hafifçe karartılır, çevresinde ince düz bir buz mavisi çizgi; panelde içerik daha
     * çok karartılıp ortada "Değiştir" hapı durur (altındaki yazıyla karışmaz); şeritte sağ üstte yalnızca kalem ikonlu yuvarlak düğme. Boş yuvada ortada sessiz bir "+".
     * Dokunuş (her yerinde) seçiciyi açar; altındaki widget dokunuş almaz.
     */
    private inner class SlotOverlay(private val ref: SahneSlotRef) : FrameLayout(activity) {
        private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = context.dpF(1.5f)
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val panel = ref.kind == SahneSlotKind.PANEL
        private val chipIcon = ImageView(context).apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        private val chipText = sahneText(context, 20f, Sahne.INK, 600)
        private val chip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(chipIcon, LinearLayout.LayoutParams(context.dp(22), context.dp(22)))
            addView(chipText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(8) })
        }

        init {
            setWillNotDraw(false)
            clipChildren = false
            addView(chip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            isClickable = true
            isFocusable = true
            setOnClickListener { openPicker(ref) }
        }

        fun bind(filled: Boolean) {
            frame.color = (Sahne.ICE and 0x00FFFFFF) or 0x8C000000.toInt()
            // Panelde düğme ortada ve içeriğin üstünde durur: içerik daha çok karartılır ki "Değiştir" altındaki yazıyla karışmasın.
            fill.color = if (filled) (Sahne.GROUND and 0x00FFFFFF) or (if (panel) 0xB8000000.toInt() else 0x73000000) else 0
            val lp = chip.layoutParams as LayoutParams
            if (filled) {
                chipIcon.setImageResource(R.drawable.ic_sahne_pen)
                chip.background = GlassDrawable(context, Sahne.PILL_RADIUS_DP, shadow = 0.3f)
                contentDescription = context.getString(R.string.launcher_sahne_change)
                if (panel) {
                    chipText.setText(R.string.launcher_sahne_change)
                    chipText.visibility = View.VISIBLE
                    chip.setPadding(context.dp(18), 0, context.dp(20), 0)
                    lp.width = LayoutParams.WRAP_CONTENT
                    lp.height = context.dp(CHIP_PANEL_DP)
                } else {
                    chipText.visibility = View.GONE
                    chip.setPadding(0, 0, 0, 0)
                    lp.width = context.dp(CHIP_STRIP_DP)
                    lp.height = context.dp(CHIP_STRIP_DP)
                }
                if (panel) {
                    lp.gravity = Gravity.CENTER
                    lp.setMargins(0, 0, 0, 0)
                } else {
                    lp.gravity = Gravity.TOP or Gravity.END
                    val inset = context.dp(4)
                    lp.setMargins(0, inset, inset, 0)
                }
            } else {
                chipIcon.setImageResource(R.drawable.ic_sahne_plus)
                chipText.visibility = View.GONE
                chip.background = null
                chip.setPadding(0, 0, 0, 0)
                lp.width = LayoutParams.WRAP_CONTENT
                lp.height = LayoutParams.WRAP_CONTENT
                lp.gravity = Gravity.CENTER
                lp.setMargins(0, 0, 0, 0)
                contentDescription = context.getString(R.string.launcher_sahne_add_here)
            }
            chip.layoutParams = lp
            chipIcon.imageTintList = ColorStateList.valueOf(if (filled) Sahne.ICE else Sahne.INK_3)
            (chipIcon.layoutParams as LinearLayout.LayoutParams).apply {
                val s = context.dp(if (filled) (if (panel) 22 else 16) else 34)
                width = s; height = s
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val inset = context.dpF(1f)
            rect.set(inset, inset, width - inset, height - inset)
            val r = context.dpF(if (panel) Sahne.PANEL_RADIUS_DP else Sahne.CONTROL_RADIUS_DP)
            if (fill.color != 0) canvas.drawRoundRect(rect, r, r, fill)
            canvas.drawRoundRect(rect, r, r, frame)
        }
    }

    // --- Şerit kabı ---

    /**
     * Şerit: yuvalar yan yana (genişlikleri türlerine göre), aralarında ince çizgi. Ortak cam zemini yok: altta aracın kendi
     * alt çubuğu da bir çubuk olduğundan, alt üçte birde üst üste iki ağır şerit durmasın diye kutular doğrudan zeminde durur.
     */
    private inner class StripBar : LinearLayout(activity) {
        private val slots = arrayOfNulls<View>(STRIP_SLOTS)
        private val types = arrayOfNulls<WidgetType>(STRIP_SLOTS)

        init {
            orientation = HORIZONTAL
            setPadding(context.dp(10), 0, context.dp(10), 0)
            clipChildren = false
        }

        fun clearSlots() {
            removeAllViews()
            slots.fill(null)
            types.fill(null)
        }

        fun setSlot(index: Int, view: View, slot: SahneSlot?) {
            slots[index] = view
            types[index] = slot?.type
        }

        /** Yuvaları ve aralarındaki çizgileri yeniden dizer. */
        fun syncDividers() {
            removeAllViews()
            slots.forEachIndexed { i, v ->
                val view = v ?: View(context).also { slots[i] = it }
                (view.parent as? ViewGroup)?.removeView(view)
                if (i > 0) addView(View(context).apply { setBackgroundColor(Sahne.STRIP_HAIR) },
                    LayoutParams(context.dp(1), LayoutParams.MATCH_PARENT).apply { topMargin = context.dp(22); bottomMargin = context.dp(22) })
                addView(view, LayoutParams(0, LayoutParams.MATCH_PARENT, stripWeight(types[i])))
            }
        }

        fun slotView(index: Int): View? = slots[index]
    }

    /** Şerit yuvaları eşit genişlikte (6 eşit pay), her tür ve düzenleme kipinde aynı. */
    private fun stripWeight(@Suppress("UNUSED_PARAMETER") type: WidgetType?): Float = 1f

    // --- Yerleşim (taslak 1872 genişlik: panel 440, boşluk 16, sahne 960; şerit 92, aralık 12) ---

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val k = w / DESIGN_W
        val stripH = context.dp(STRIP_H_DP)
        val gapV = context.dp(GAP_V_DP)
        val bandH = h - stripH - gapV
        val panelW = (PANEL_W * k).toInt()
        val gapH = (GAP_H * k).toInt()
        val stageW = w - 2 * stageInset(panelW, gapH)
        val panelH = panelHeight(bandH, gapV)
        panels.forEachIndexed { i, p -> p.measure(exactly(if (SahneBand.occupies(shown, i, editMode)) panelW else 0), exactly(panelH)) }
        stage.measure(exactly(stageW), exactly(bandH + gapV))
        strip.measure(exactly(w), exactly(stripH))
        sport?.measure(exactly(w), exactly(h))
        overlays.forEach { (ref, o) ->
            if (ref.kind == SahneSlotKind.PANEL) o.measure(exactly(panelW), exactly(panelH))
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        val stripH = context.dp(STRIP_H_DP)
        val gapV = context.dp(GAP_V_DP)
        val bandH = h - stripH - gapV
        val k = w / DESIGN_W
        val panelW = (PANEL_W * k).toInt()
        val gapH = (GAP_H * k).toInt()
        val panelH = panelHeight(bandH, gapV)
        leftPanel.layout(0, 0, leftPanel.measuredWidth, panelH)
        rightPanel.layout(w - rightPanel.measuredWidth, 0, w, panelH)
        if (quad) {
            leftLowerPanel.layout(0, bandH - panelH, panelW, bandH)
            rightLowerPanel.layout(w - panelW, bandH - panelH, w, bandH)
        }
        // Sahne ekranın ortasında (araç saat ve şeritle aynı eksende); kapalı yan boş kalır.
        val stageL = stageInset(panelW, gapH)
        stage.layout(stageL, 0, stageL + stage.measuredWidth, bandH + gapV)
        strip.layout(0, h - stripH, w, h)
        sport?.layout(0, 0, w, h)
        overlays.forEach { (ref, o) ->
            if (ref.kind == SahneSlotKind.PANEL) {
                val p = panels[ref.index]
                o.layout(p.left, p.top, p.right, p.bottom)
            } else {
                val v = strip.slotView(ref.index)
                if (v == null || v.width == 0) {
                    o.measure(exactly(0), exactly(0)); o.layout(0, 0, 0, 0)
                } else {
                    val left = strip.left + v.left
                    val top = strip.top + context.dp(8)
                    val bottom = strip.bottom - context.dp(8)
                    o.measure(exactly(v.width), exactly(bottom - top))
                    o.layout(left, top, left + v.width, bottom)
                }
            }
        }
    }

    /** Düzenlemede yuva örtüleri dokunuşu alır; sahne zaten kilitli. */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false

    /** Bir yanın (0 sol, 1 sağ) bantta kapladığı genişlik: panel + aralık, kapalı (boş) yanda 0 ([SahneBand]). */
    private fun sideW(side: Int, panelW: Int, gapH: Int): Int = if (SahneBand.occupies(shown, side, editMode)) panelW + gapH else 0

    /**
     * Sahnenin iki yandaki eşit payı: dolu yanların genişi. Sahne hep ekranın ortasında kalır (araç x = w/2, saat ve şeritle
     * aynı eksen; "Kilitli" hapı araca her zamanki uzaklıkta). Tek yan kapalıyken sahne o boşluğa kaymaz; yalnız iki yan da
     * kapalıysa tam genişlik. Düzenlemeye girip çıkınca boş yuva açılıp kapansa da sahne yerinden oynamaz.
     */
    private fun stageInset(panelW: Int, gapH: Int): Int = maxOf(sideW(0, panelW, gapH), sideW(1, panelW, gapH))

    /** Panel yüksekliği: iki panelli biçimde bant boyu, dört panelli biçimde aralarında [GAP_V_DP] boşlukla yarısı. */
    private fun panelHeight(bandH: Int, gapV: Int): Int = if (quad) (bandH - gapV) / 2 else bandH

    private fun exactly(px: Int) = MeasureSpec.makeMeasureSpec(maxOf(0, px), MeasureSpec.EXACTLY)

    private companion object {
        const val DESIGN_W = 1872f
        const val FLASH_MS = 120L
        const val PANEL_W = 440f
        const val GAP_H = 16f
        const val STRIP_H_DP = 92
        const val GAP_V_DP = 16
        /** Tiggo 7'deki yan panel yüksekliği (720 dp ekranda bant boyu); dört panelli biçimde içerik bu ölçüde kurulur. */
        const val NATURAL_PANEL_H_DP = 378
        const val EMPTY_ALPHA = 0.45f
        const val DISABLED_ALPHA = 0.45f
        const val PICKER_ICON_DP = 26
        const val PICKER_TITLE_SP = 21f
        const val PICKER_TITLE_MIN_SP = 15
        const val PICKER_TITLE_LINE = 1.4f
        const val PICKER_SUB_SP = 17f
        const val CHIP_PANEL_DP = 52
        const val CHIP_STRIP_DP = 30
    }
}
