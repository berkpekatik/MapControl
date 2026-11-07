package com.mapcontrol.launcher

import android.content.Context

/**
 * Sahne düzeninin modeli (saf, JVM'de sınanır). Ortadaki araç sahnesi sabittir; düzenlenebilir olan iki yan panel
 * ([left], [right]) ve alt şeridin [STRIP_SLOTS] yuvasıdır. Boş yuva null'dır (düzenlemede sessiz bir "+").
 */
data class SahneSlot(
    val id: String,
    val type: WidgetType,
    /** Türe özel ayar: APP için "paketA+paketB" (şeritte iki uygulama). */
    val extra: String? = null,
)

data class SahneLayout(
    val left: SahneSlot?,
    val right: SahneSlot?,
    val strip: List<SahneSlot?>,
    /**
     * Dört panelli biçim (Tiggo 8, 6 Kasım kararı): her yanda üst üste iki panel. [left]/[right] üst panellerdir,
     * [leftLower]/[rightLower] alttakiler. İki panelli biçimde (Tiggo 7) alt yuvalar hep null ve kayıt aynen eski biçimdir.
     */
    val quad: Boolean = false,
    val leftLower: SahneSlot? = null,
    val rightLower: SahneSlot? = null,
) {
    /** Tüm dolu yuvalar (sol, sağ, sol alt, sağ alt, şerit sırasıyla). */
    fun slots(): List<SahneSlot> = listOfNotNull(left, right, leftLower, rightLower) + strip.filterNotNull()

    /** Bu biçimdeki panel yuvası sayısı (2 ya da 4). */
    val panelCount: Int get() = if (quad) 4 else 2

    fun types(): Set<WidgetType> = slots().map { it.type }.toSet()
}

/**
 * Orta bandın yerleşimi (saf): iki panelli biçimde (Tiggo 7) boş bir yan panel, düzenleme dışında yer kaplamaz; araç
 * sahnesi o boşluğa genişler (boş soluk cam durmasın). Düzenlemede boş yuva "+" ile görünür. Dört panelde her yuva hep yerindedir.
 */
object SahneBand {
    fun occupies(layout: SahneLayout, panelIndex: Int, editing: Boolean): Boolean =
        layout.quad || editing || SahneSlots.get(layout, SahneSlotRef(SahneSlotKind.PANEL, panelIndex)) != null
}

/** Yuva türü: yan panel ya da şerit öğesi. */
enum class SahneSlotKind { PANEL, STRIP }

/** Yuvanın adresi: panel için 0 = sol (üst), 1 = sağ (üst), 2 = sol alt, 3 = sağ alt (yalnız dört panelli biçim); şerit için 0..[STRIP_SLOTS]-1. */
data class SahneSlotRef(val kind: SahneSlotKind, val index: Int)

const val STRIP_SLOTS = 6

/**
 * Hangi tür hangi yuvaya konabilir: iki seçilmiş liste (sahibin kararı). Şerit, tek dokunuşla iş gören küçük kontrollerdir;
 * panel, büyük alanı dolduran bilgi ve kontrollerdir. Listede olmayan tür Sahne seçicisinde hiç gösterilmez (araç kartı
 * sahnenin kendisidir). Her listelenen tür en az birine sığar (SahneSlotsTest).
 */
object SahneForm {
    /** Şerit (küçük) için izinli türler, seçicideki sırayla. */
    val STRIP_TYPES: List<WidgetType> = listOf(
        WidgetType.APP, WidgetType.BREAK_REMINDER, WidgetType.SCREEN_OFF, WidgetType.PHONE_REMINDER,
        WidgetType.CONSUMPTION, WidgetType.WEB_PANEL, WidgetType.EQUALIZER, WidgetType.AUTOMATION, WidgetType.CLUSTER_MIRROR,
    )

    /**
     * Panel (büyük) için izinli türler, seçicideki sırayla: yalnız bu boyuta göre çizilmiş widget'lar. Diğer türlerin eski
     * Sahne kayıtlarındaki yuvaları [SahneSlots.decode]'da yedeğe döner.
     */
    val PANEL_TYPES: List<WidgetType> = listOf(WidgetType.TRIP, WidgetType.CONSUMPTION)

    fun fitsPanel(type: WidgetType): Boolean = type in PANEL_TYPES
    fun fitsStrip(type: WidgetType): Boolean = type in STRIP_TYPES
    fun fits(type: WidgetType, kind: SahneSlotKind): Boolean = if (kind == SahneSlotKind.PANEL) fitsPanel(type) else fitsStrip(type)

    /**
     * Tiggo 7'de (iki panelli biçim) gizlenen türler; kayıtlarda yerine [TIGGO7_REPLACEMENTS]'tan biri gelir
     * ([SahneSlots.forTiggo7]). Bu sürümde boştur.
     */
    val TIGGO7_HIDDEN: Set<WidgetType> = emptySet()

    /**
     * Gizlenen şerit yuvasının yerine sırayla denenen türler (düzende olmayan ilki). Anlık tüketim en sonda. Yolculuk
     * kartı artık anlık tüketimi göstermez (7 Kasım kararı), bu yüzden yolculuk paneli varken de seçilebilir.
     */
    val TIGGO7_REPLACEMENTS: List<WidgetType> = listOf(WidgetType.SCREEN_OFF, WidgetType.BREAK_REMINDER, WidgetType.PHONE_REMINDER, WidgetType.CONSUMPTION)

    /** Düzende yolculuk paneli var mı (yalnız v1 → v2 geçişi [SahneSlots.migrateTripConsumption] kullanır; çalışma anında kullanılmaz). */
    fun hasTripPanel(layout: SahneLayout): Boolean =
        listOf(layout.left, layout.right, layout.leftLower, layout.rightLower).any { it?.type == WidgetType.TRIP }

    /** Şerit yuvasına [layout]'ta yedek olarak konabilir mi: şeride sığan her tür (yolculuk paneli artık tüketimi engellemez). */
    @Suppress("UNUSED_PARAMETER")
    fun stripFallbackOk(layout: SahneLayout, type: WidgetType): Boolean = fitsStrip(type)

    /** Bu biçimde ([quad] false = Tiggo 7) gösterilebilir mi. */
    fun available(type: WidgetType, quad: Boolean): Boolean = quad || type !in TIGGO7_HIDDEN

    /** Seçicide [kind] yuvası için sunulan türler ([quad] false = Tiggo 7: gizlenenler yok). */
    fun offered(kind: SahneSlotKind, quad: Boolean = false): List<WidgetType> =
        (if (kind == SahneSlotKind.PANEL) PANEL_TYPES else STRIP_TYPES).filter { available(it, quad) }

    /** Türün panelde kendi büyük biçimi var mı (yoksa kutucuğu büyütülür). */
    fun hasLargeForm(type: WidgetType): Boolean = type.sizes.any { it.w >= 4 || it.h >= 4 }

    /** Panelde kullanılacak boyut: en büyük alanlı izinli boyut. */
    fun panelSpan(type: WidgetType): Span = type.sizes.maxByOrNull { it.w * it.h } ?: Span(2, 2)

    /** Şerit için kutucuğun kurulacağı boyut: ilk (varsayılan) boyut. */
    fun stripSpan(type: WidgetType): Span = type.sizes.first()

    /**
     * Seçicide [type] [ref] yuvasına konabilir mi: yuvaya sığmalı ve (APP hariç) düzende başka bir yuvada olmamalı ([ref]'teki
     * mevcut widget'ın yerine konabilir). Park ve Sürüş aynı kuralı kullanır: her widget sürüşte de çalışır.
     */
    fun canPlace(layout: SahneLayout, ref: SahneSlotRef, type: WidgetType): Boolean {
        if (!fits(type, ref.kind) || !available(type, layout.quad)) return false
        if (type == WidgetType.APP) return true
        val here = SahneSlots.get(layout, ref)
        return layout.slots().none { it.type == type && it !== here }
    }
}

object SahneSlots {

    fun get(layout: SahneLayout, ref: SahneSlotRef): SahneSlot? = when (ref.kind) {
        SahneSlotKind.PANEL -> when (ref.index) {
            0 -> layout.left
            1 -> layout.right
            2 -> layout.leftLower
            3 -> layout.rightLower
            else -> null
        }
        SahneSlotKind.STRIP -> layout.strip.getOrNull(ref.index)
    }

    /** [ref] yuvasına [slot] konmuş (null: boşaltılmış) yeni düzen. */
    fun set(layout: SahneLayout, ref: SahneSlotRef, slot: SahneSlot?): SahneLayout = when (ref.kind) {
        SahneSlotKind.PANEL -> when (ref.index) {
            0 -> layout.copy(left = slot)
            1 -> layout.copy(right = slot)
            2 -> if (layout.quad) layout.copy(leftLower = slot) else layout
            3 -> if (layout.quad) layout.copy(rightLower = slot) else layout
            else -> layout
        }
        SahneSlotKind.STRIP -> layout.copy(strip = layout.strip.mapIndexed { i, s -> if (i == ref.index) slot else s })
    }

    /** İki uygulamalı şerit yuvasının varsayılan paketleri ("a+b"); kurulu uygulama yoksa null. */
    fun appExtra(packages: List<String>, offset: Int = 0): String? =
        packages.drop(offset).take(2).takeIf { it.isNotEmpty() }?.joinToString(APP_SEP)

    /** APP yuvasının paketleri (en çok iki). */
    fun appPackages(extra: String?): List<String?> {
        val parts = extra?.split(APP_SEP)?.map { it.trim().ifEmpty { null } } ?: emptyList()
        return listOf(parts.getOrNull(0), parts.getOrNull(1))
    }

    const val APP_SEP = "+"

    /**
     * Tiggo 7 varsayılan şeridi (Park ve Sürüş aynı): ekranı kapat · göstergeye yansıt · web paneli · ekolayzer · otomasyonlar ·
     * mola hatırlatıcı. Araca yazan kutucuklar (koltuk, bagaj, sürüş modu) sonraki parçada gelir.
     */
    fun defaultStrip(): List<SahneSlot?> = listOf(
        SahneSlot("screen_off", WidgetType.SCREEN_OFF),
        SahneSlot("cluster_mirror", WidgetType.CLUSTER_MIRROR),
        SahneSlot("web_panel", WidgetType.WEB_PANEL),
        SahneSlot("equalizer", WidgetType.EQUALIZER),
        SahneSlot("automation", WidgetType.AUTOMATION),
        SahneSlot("break_reminder", WidgetType.BREAK_REMINDER),
    )

    /**
     * Park varsayılanı: solda "Toplam" bölümlü yolculuk, sağ panel boş (araç sahnesi o yana genişler, [SahneBand]); şerit
     * [defaultStrip]. [appPackages] ve [rearSpeaker] şeridi etkilemiyor; imza çağıranlar için kaldı. Yalnız ilk kurulum ve
     * sıfırlamada kullanılır; kayıtlı düzene dokunulmaz.
     */
    @Suppress("UNUSED_PARAMETER")
    fun defaultPark(appPackages: List<String> = emptyList(), rearSpeaker: Boolean = false): SahneLayout = SahneLayout(
        left = SahneSlot("trip", WidgetType.TRIP),
        right = null,
        strip = defaultStrip(),
    )

    /** Sürüş varsayılanı: Park ile aynı. Yalnız ilk kurulum ve sıfırlamada kullanılır; kayıtlı düzene dokunulmaz. */
    @Suppress("UNUSED_PARAMETER")
    fun defaultDrive(appPackages: List<String> = emptyList(), rearSpeaker: Boolean = false): SahneLayout = SahneLayout(
        left = SahneSlot("trip", WidgetType.TRIP),
        right = null,
        strip = defaultStrip(),
    )

    fun defaults(mode: LayoutMode, appPackages: List<String> = emptyList(), rearSpeaker: Boolean = false): SahneLayout =
        if (mode == LayoutMode.DRIVE) defaultDrive(appPackages, rearSpeaker) else defaultPark(appPackages, rearSpeaker)

    /**
     * Tiggo 8 varsayılanı (Park ve Sürüş aynı panel dizilişi): sol üst yolculuk, sağ üst anlık tüketim; alt yuvalar boş
     * (düzenlemede "+"). Şerit o modun Tiggo 7 şeridi. [steerHeat] imza için kaldı.
     */
    @Suppress("UNUSED_PARAMETER")
    fun defaultsQuad(mode: LayoutMode, appPackages: List<String> = emptyList(), steerHeat: Boolean = false): SahneLayout =
        defaults(mode, appPackages).copy(
            left = SahneSlot("trip", WidgetType.TRIP),
            right = SahneSlot("consumption", WidgetType.CONSUMPTION),
            quad = true,
            leftLower = null,
            rightLower = null,
        )

    /** Yuvaların tercih ettiği türler (yükseltme ve yedek için): sol yolculuk, sağ tüketim, alt yuvalar sırayla. */
    private fun preferredPanel(index: Int): WidgetType = when (index) {
        1, 3 -> WidgetType.CONSUMPTION
        else -> WidgetType.TRIP
    }

    /**
     * İki panelli bir kaydı dört panelliye çevirir (Tiggo 8'in 6 Kasım öncesi kayıtları): üst paneller ve şerit aynen kalır,
     * alt yuvalar düzende olmayan izinli panel türleriyle dolar. Zaten dört
     * panelliyse aynen döner.
     */
    fun toQuad(layout: SahneLayout): SahneLayout {
        if (layout.quad) return layout
        var out = layout.copy(quad = true, leftLower = null, rightLower = null)
        listOf(2, 3).forEach { i ->
            val used = out.types()
            val pick = (listOf(preferredPanel(i)) + SahneForm.PANEL_TYPES).firstOrNull { it !in used } ?: return@forEach
            out = set(out, SahneSlotRef(SahneSlotKind.PANEL, i), SahneSlot(newId(out, pick), pick))
        }
        return out
    }

    /**
     * Okunan düzeni temizler: yuvaya sığmayan (seçkide olmayan) türler boşalır, şerit tam [STRIP_SLOTS] yuvaya tamamlanır/kırpılır,
     * tekil türlerin ikincisi ve yinelenen kimlikler atılır (ilk kalır).
     */
    fun sanitize(layout: SahneLayout): SahneLayout {
        val seenTypes = HashSet<WidgetType>()
        val seenIds = HashSet<String>()
        fun keep(s: SahneSlot?, kind: SahneSlotKind): SahneSlot? {
            if (s == null || !SahneForm.fits(s.type, kind)) return null
            if (!seenIds.add(s.id)) return null
            if (s.type != WidgetType.APP && !seenTypes.add(s.type)) return null
            return s
        }
        fun keepPanel(s: SahneSlot?): SahneSlot? = s?.takeIf { SahneForm.available(it.type, layout.quad) }?.let { keep(it, SahneSlotKind.PANEL) }
        val left = keepPanel(layout.left)
        val right = keepPanel(layout.right)
        val leftLower = if (layout.quad) keep(layout.leftLower, SahneSlotKind.PANEL) else null
        val rightLower = if (layout.quad) keep(layout.rightLower, SahneSlotKind.PANEL) else null
        val strip = (0 until STRIP_SLOTS).map { keep(layout.strip.getOrNull(it), SahneSlotKind.STRIP) }
        return SahneLayout(left, right, strip, layout.quad, leftLower, rightLower)
    }

    // --- Kayıt biçimi ---
    // "v1|<sol>|<sağ>|<şerit1>,<şerit2>,..." ; yuva: boş = "", yoksa "tür~kimlik~ek". Paket adları ve kimlikler bu
    // ayraçları içermez (yalnızca harf, rakam, '.', '_'). Saf metin: JSON'a gerek yok, JVM'de sınanır.
    // Dört panelli biçim (Tiggo 8) sona iki alan ekler: "v1|<sol>|<sağ>|<şerit>|<sol alt>|<sağ alt>". İki panelli kayıt
    // aynen eski dört alanlı biçimdir; eski okuyucu altı alanlı kaydı çözemez ve varsayılana döner (yalnız Tiggo 8 dosyasında).
    // v2 (6 Kasım): alanlar aynı; yalnız v1 kayıtları bir kez [migrateTripConsumption]'dan geçer. v2 kayıtlarına dokunulmaz
    // (kullanıcı tüketimi bilerek geri eklemiş olabilir).
    // v3 (7 Kasım): alanlar aynı; v1/v2 kayıtlar bir kez geçirilip v3 olarak yeniden yazılır. v3 kayıtlarına dokunulmaz.

    private const val VERSION = "v3"
    private const val VERSION_2 = "v2"
    private const val VERSION_1 = "v1"

    /** Kayıt eski (v1/v2) biçimde mi: [decode] onu bir kez geçirir, çağıran v3 olarak yeniden yazar. */
    fun isLegacy(raw: String?): Boolean = raw?.startsWith("$VERSION_1|") == true || raw?.startsWith("$VERSION_2|") == true

    fun encode(layout: SahneLayout): String {
        fun slot(s: SahneSlot?): String = if (s == null) "" else listOf(s.type.key, s.id, s.extra ?: "").joinToString("~")
        val base = listOf(VERSION, slot(layout.left), slot(layout.right), layout.strip.joinToString(",") { slot(it) })
        val parts = if (layout.quad) base + listOf(slot(layout.leftLower), slot(layout.rightLower)) else base
        return parts.joinToString("|")
    }

    /**
     * Çözülemeyen kayıt null (çağıran varsayılana döner); bilinmeyen ya da kaldırılmış tür (perde, buğu) yalnızca o yuvayı
     * boşaltır.
     */
    fun decode(raw: String?): SahneLayout? {
        if (raw.isNullOrEmpty()) return null
        val parts = raw.split("|")
        if ((parts.size != 4 && parts.size != 6) || parts[0] !in listOf(VERSION, VERSION_2, VERSION_1)) return null
        val v1 = parts[0] == VERSION_1
        fun slot(s: String): SahneSlot? {
            if (s.isEmpty()) return null
            val f = s.split("~")
            val type = WidgetType.fromKey(f[0]) ?: return null
            val id = f.getOrNull(1)?.ifEmpty { null } ?: type.key
            return SahneSlot(id, type, f.getOrNull(2)?.ifEmpty { null })
        }
        val strip = parts[3].split(",").map { slot(it) }
        val quad = parts.size == 6
        val raw = SahneLayout(slot(parts[1]), slot(parts[2]), strip, quad,
            if (quad) slot(parts[4]) else null, if (quad) slot(parts[5]) else null)
        val clean = fallbackPanels(raw, sanitize(raw))
        return if (v1) migrateTripConsumption(clean) else clean
    }

    /**
     * v1 → v2 bir kerelik GEÇMİŞ geçiş (yalnız v1 kayıtlara; yeni kayıtlara uygulanmaz): o zaman yolculuk kartı sürüşte
     * canlı tüketimi öncü satırında gösteriyordu, şeritteki "Anlık tüketim" kutucuğu onu yineliyordu (7 Kasım'dan beri
     * kart göstermiyor; kayıtlar geri yazılmaz). Düzende yolculuk paneli varsa şeritteki tüketim yuvası ekranı kapata döner; ekranı kapat zaten
     * düzendeyse mola hatırlatıcıya, o da varsa aynen kalır. Yolculuk paneli ya da şeritte tüketim yoksa düzen aynen döner.
     */
    fun migrateTripConsumption(layout: SahneLayout): SahneLayout {
        if (!SahneForm.hasTripPanel(layout)) return layout
        val i = layout.strip.indexOfFirst { it?.type == WidgetType.CONSUMPTION }
        if (i < 0) return layout
        val ref = SahneSlotRef(SahneSlotKind.STRIP, i)
        val cleared = set(layout, ref, null)
        val used = cleared.types()
        val pick = listOf(WidgetType.SCREEN_OFF, WidgetType.BREAK_REMINDER).firstOrNull { it !in used } ?: return layout
        return set(cleared, ref, SahneSlot(newId(cleared, pick), pick))
    }

    /**
     * Artık panelde izinli olmayan türdeki (ör. sağlık, hava) bir yan paneli yedeğe çevirir: o tarafın varsayılanı
     * (sol yolculuk, sağ tüketim) düzende başka yerde yoksa o, yoksa kullanılmayan diğer izinli panel türü, yoksa boş.
     * Yalnız o yuvanın kaydı dolu ve türü izinsizse çalışır; bilinmeyen/boş yuvalar boş kalır.
     */
    private fun fallbackPanels(raw: SahneLayout, clean: SahneLayout): SahneLayout {
        var out = clean
        (0 until raw.panelCount).forEach { i ->
            val ref = SahneSlotRef(SahneSlotKind.PANEL, i)
            val was = get(raw, ref)
            if (was == null || SahneForm.fitsPanel(was.type) || get(out, ref) != null) return@forEach
            val preferred = preferredPanel(i)
            val used = out.types()
            val pick = (listOf(preferred) + SahneForm.PANEL_TYPES)
                .firstOrNull { it !in used && SahneForm.available(it, out.quad) } ?: return@forEach
            out = set(out, ref, SahneSlot(newId(out, pick), pick))
        }
        return out
    }

    /**
     * Tiggo 7 kaydı: gizlenen türdeki ([SahneForm.TIGGO7_HIDDEN]) şerit yuvası, düzende olmayan ilk yedek türe
     * ([SahneForm.TIGGO7_REPLACEMENTS]) döner, hiçbiri boşta değilse boşalır; panelde gizli tür olmaz. Gizli tür yoksa aynen döner.
     */
    fun forTiggo7(layout: SahneLayout): SahneLayout =
        replaceHidden(layout, SahneForm.TIGGO7_HIDDEN)

    /** Tiggo 8 kaydı: bu sürümde gizlenen tür yok, düzen aynen döner. */
    fun forTiggo8(layout: SahneLayout): SahneLayout = layout

    /** [hidden] türdeki şerit yuvaları, düzende olmayan ilk yedek türe ([SahneForm.TIGGO7_REPLACEMENTS]) döner; yoksa boşalır. */
    fun replaceHidden(layout: SahneLayout, hidden: Set<WidgetType>): SahneLayout {
        if (layout.slots().none { it.type in hidden }) return layout
        var out = layout
        layout.strip.forEachIndexed { i, s ->
            if (s == null || s.type !in hidden) return@forEachIndexed
            val ref = SahneSlotRef(SahneSlotKind.STRIP, i)
            val cleared = set(out, ref, null)
            val used = cleared.types()
            val pick = SahneForm.TIGGO7_REPLACEMENTS.firstOrNull { it !in used && SahneForm.stripFallbackOk(cleared, it) }
            out = if (pick == null) cleared else set(cleared, ref, SahneSlot(newId(cleared, pick), pick))
        }
        return out
    }

    /** Yeni yuva kimliği: tür anahtarı, gerekirse sıra eki (APP birden çok olabilir). */
    fun newId(layout: SahneLayout, type: WidgetType): String {
        val used = layout.slots().map { it.id }.toSet()
        if (type.key !in used) return type.key
        var i = 2
        while ("${type.key}_$i" in used) i++
        return "${type.key}_$i"
    }
}

/**
 * Sahne düzeninin deposu: profil ve araç ekranı başına ayrı dosya, Park ve Sürüş için ayrı anahtar.
 */
class SahneStore(
    context: Context,
    private val mode: LayoutMode,
    vehicle: VehicleProfile = VehicleProfile.compute(context),
) {
    init {
        // Yapılandırma kararları (direksiyon ısıtma, sürüş modları): kayıtlı değer hemen, araçtan okuma arka planda.
        CarFeatures.init(context)
    }

    private val appPackages = DefaultApps.installedDefaults(context, DEFAULT_APP_SLOTS)
    private val prefs = context.applicationContext.getSharedPreferences(prefsName(Profiles.current(context), vehicle), Context.MODE_PRIVATE)
    private val key = key(mode)

    /** Tiggo 8: her yanda üst üste iki panel (dört panelli biçim); Tiggo 7 iki panelli kalır. */
    val quad: Boolean = vehicle == VehicleProfile.TIGGO8

    fun load(): SahneLayout {
        val raw = prefs.getString(key, null)
        val decoded = SahneSlots.decode(raw)
        // v1/v2 kaydı bir kez geçirilir ve v3 olarak yazılır: sonradan bilerek eklenen tüketim bir daha kaldırılmaz.
        if (decoded != null && SahneSlots.isLegacy(raw)) save(decoded)
        return forVehicle(decoded ?: defaults())
    }

    fun save(layout: SahneLayout) = prefs.edit().putString(key, SahneSlots.encode(layout)).apply()

    /** İlk kurulum / sıfırlama düzeni (kayıt yoksa); kayıtlı düzen hiç değiştirilmez. */
    fun defaults(): SahneLayout =
        if (quad) SahneSlots.defaultsQuad(mode, appPackages, steerHeat = CarFeatures.steerHeat == true) else SahneSlots.defaults(mode, appPackages)

    /** Kayıt bu aracın biçiminde değilse çevrilir: Tiggo 8'in eski iki panelli kaydı dört panelliye yükselir. */
    private fun forVehicle(layout: SahneLayout): SahneLayout = when {
        quad -> SahneSlots.forTiggo8(SahneSlots.toQuad(layout))
        layout.quad -> SahneSlots.forTiggo7(SahneSlots.sanitize(layout.copy(quad = false, leftLower = null, rightLower = null)))
        else -> SahneSlots.forTiggo7(layout)
    }

    fun reset() = prefs.edit().remove(key).apply()

    companion object {
        const val PREFS = "launcher_sahne"
        const val PARK = "sahne_park_v1"
        const val DRIVE = "sahne_drive_v1"

        fun key(mode: LayoutMode): String = if (mode == LayoutMode.DRIVE) DRIVE else PARK

        fun prefsName(profile: Int, vehicle: VehicleProfile = VehicleProfile.TIGGO7): String =
            Profiles.key(PREFS, profile) + if (vehicle == VehicleProfile.TIGGO8) "_t8" else ""
    }
}
