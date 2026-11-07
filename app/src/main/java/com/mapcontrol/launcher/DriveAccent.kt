package com.mapcontrol.launcher

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mapcontrol.R

/**
 * Sürüş moduna göre launcher'ın vurgu rengi (salt-okur; araca hiçbir şey yazılmaz).
 * Mod değerleri [DriveModes] tablosudur (okuma = yazma, 7 Kasım ölçümü): 0 Eko, 1 Normal, 2 Spor, 3 Kar, 4 Çamur, 5 Arazi, 7 Kum.
 * Yalnızca vurgu değişir; zemin, kart ve metin renkleri aynı kalır. Normal mod varsayılan vurguyu korur.
 *
 * Renkler beyaz yazıyla okunur kalacak şekilde seçildi: gündüz >= 4.5:1, gece >= 3:1 (büyük/kalın yazı).
 */
object DriveAccent {
    // Mod değerleri; tek kaynak [DriveModes] (okuma = yazma).
    const val ECO = DriveModes.ECO
    const val NORMAL = DriveModes.NORMAL
    const val SPORT = DriveModes.SPORT
    const val SNOW = DriveModes.SNOW
    const val MUD = DriveModes.MUD
    const val SAND = DriveModes.SAND
    const val OFFROAD = DriveModes.OFFROAD

    /** Mod başına (gece, gündüz) vurgu; Normal listede yok = temanın kendi vurgusu. */
    private val ACCENTS: Map<Int, Pair<Int, Int>> = mapOf(
        ECO to (0xFF1F9D55.toInt() to 0xFF15803D.toInt()),      // sakin yeşil
        SPORT to (0xFFEA580C.toInt() to 0xFFC2410C.toInt()),    // sıcak kırmızı-turuncu
        SNOW to (0xFF0891B2.toInt() to 0xFF0E7490.toInt()),     // buz mavisi
        MUD to (0xFF9A6B43.toInt() to 0xFF7A4E2D.toInt()),      // toprak kahvesi
        SAND to (0xFFB7791F.toInt() to 0xFFA16207.toInt()),     // kum / kehribar
        OFFROAD to (0xFF6B7F23.toInt() to 0xFF556B1C.toInt()), // zeytin
    )

    /** Moda özgü vurgu; Normal ve bilinmeyen modda null (temanın varsayılanı kullanılır). */
    @JvmStatic
    fun accentFor(mode: Int, night: Boolean): Int? =
        ACCENTS[mode]?.let { if (night) it.first else it.second }

    /** Beyaz yazının [fill] üzerindeki WCAG karşıtlığı. */
    @JvmStatic
    fun contrastWithWhite(fill: Int): Double {
        fun ch(v: Int): Double { val x = v / 255.0; return if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4) }
        val l = 0.2126 * ch((fill shr 16) and 0xFF) + 0.7152 * ch((fill shr 8) and 0xFF) + 0.0722 * ch(fill and 0xFF)
        return 1.05 / (l + 0.05)
    }

    /**
     * Birincil düğme (ör. düzenleme modunda "Bitti") dolgusu: [accent] beyaz yazıyla en az 4,5:1 olana kadar
     * karartılır. Gündüz vurguları zaten yeterli, gece vurguları (açık tonlar) biraz koyulaşır; ton aynı kalır.
     */
    @JvmStatic
    fun buttonFill(accent: Int): Int {
        var c = accent
        var i = 0
        while (contrastWithWhite(c) < MIN_BUTTON_CONTRAST && i++ < 40) {
            val r = ((c shr 16) and 0xFF) * 92 / 100
            val g = ((c shr 8) and 0xFF) * 92 / 100
            val b = (c and 0xFF) * 92 / 100
            c = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return c
    }

    const val MIN_BUTTON_CONTRAST = 4.5

    /** Rozet gösterilecek mi: Normal ve bilinmeyen modda gösterilmez. */
    @JvmStatic
    fun hasBadge(mode: Int): Boolean = mode in ACCENTS

    @StringRes
    fun label(mode: Int): Int = when (mode) {
        ECO -> R.string.launcher_drive_eco
        SPORT -> R.string.launcher_drive_sport
        SNOW -> R.string.launcher_drive_snow
        MUD -> R.string.launcher_drive_mud
        SAND -> R.string.launcher_drive_sand
        OFFROAD -> R.string.launcher_drive_offroad
        else -> R.string.launcher_drive_normal
    }

    @DrawableRes
    fun icon(mode: Int): Int = when (mode) {
        ECO -> R.drawable.ic_mdi_leaf
        SPORT -> R.drawable.ic_mdi_car_sports
        SNOW -> R.drawable.ic_mdi_snowflake
        else -> R.drawable.ic_mdi_terrain
    }

    private const val PREFS_NAME = "launcher_settings"
    private const val KEY_ENABLED = "drive_mode_theme"

    /** Kullanıcı seçeneği (profil başına, varsayılan açık). */
    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(Profiles.key(context, KEY_ENABLED), true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(Profiles.key(context, KEY_ENABLED), on).apply()
    }
}

/**
 * Sürüş modu geçişi için saf bekleme (debounce): gözlenen mod [stableMs] boyunca değişmeden kalırsa ve
 * geçişe izin varsa yeni modu döndürür (yoksa null). Gözlem uygulanan moda dönerse ya da başka moda geçerse
 * sayaç sıfırlanır. [observed] null (veri yok) uygulanan modu korur.
 */
class DriveModePolicy(private val stableMs: Long) {
    private var applied = DriveAccent.NORMAL
    private var candidate: Int? = null
    private var since = 0L

    fun reset(current: Int) {
        applied = current
        candidate = null
    }

    fun update(now: Long, observed: Int?, canSwitch: Boolean): Int? {
        if (observed == null || observed == applied) {
            candidate = null
            return null
        }
        if (candidate != observed) {
            candidate = observed
            since = now
        }
        if (now - since < stableMs || !canSwitch) return null
        applied = observed
        candidate = null
        return observed
    }
}

/**
 * "Spor modu (deneysel)": araç Spor'a geçince Sahne'nin kronograf ekranı. Varsayılan KAPALI; kapalıyken Spor da diğer
 * sürüş modları gibi orijinal temada kalır (sürüş rengi açıksa yalnız vurgu değişir). Profil başına saklanır.
 */
object SportModePrefs {
    private const val PREFS_NAME = "launcher_settings"
    private const val KEY = "sport_mode_experimental"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(Profiles.key(context, KEY), false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(Profiles.key(context, KEY), on).apply()
    }

    private const val KEY_CLOSE_OEM_DIALOG = "sport_close_oem_drive_dialog"

    /** "Aracın sürüş modu penceresini kapat" ([OemDriveDialogCloser]); varsayılan AÇIK, profil başına. */
    fun closeOemDialog(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(Profiles.key(context, KEY_CLOSE_OEM_DIALOG), true)

    fun setCloseOemDialog(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(Profiles.key(context, KEY_CLOSE_OEM_DIALOG), on).apply()
    }
}

/**
 * OEM sürüş modu penceresini kapatma kararı (saf; JVM'de sınanır). Aracın OEM sürüş modu penceresi mod
 * değişince açılır ve `com.desaysv.systemui.ACTION_DIALOG_CLOSE` yayınını dinleyip kapanır (izinsiz, dinamik alıcı).
 * Pencere odağı aldığı için spor geçişi ([SportGate]) onun kapanmasını bekler.
 *
 * Yayın BÜTÜN OEM pencerelerini kapatır; bu yüzden yalnız gözlenen bir sürüş modu değişiminden sonra, değişim başına en çok
 * bir kez ve yalnız spor geçişi başlayacaksa: yeni mod Spor, ya da eski mod Spor ve spor ekranı açık (çıkış). İlk gözlem
 * değişim sayılmaz; veri kesintisi (null) son modu korur. Pencere önce açılabilsin diye değişimden [delayMs] sonra.
 * Pencere odağı alır: süre dolunca yalnız odak bizden gitmişse (`focusLost`) gönderilir; odak [FOCUS_WAIT_MS] içinde
 * gitmezse pencere hiç açılmamıştır (OEM bazı durumlarda göstermez) ve yayın düşer. Arka planda geçen değişimler için
 * yayın olmasın diye çağıran görünmez olunca / demo sürerken [reset] eder (dönüşteki ilk gözlem değişim sayılmaz).
 *
 * Bizim sürüş modu widget'ımızın istediği değişim ([ownRequestAt] son istek, [ownRequests] toplam istek sayısı; [OWN_WINDOW_MS]
 * içinde gözlenen değişimler, istek başına bir) her mod için
 * kapatılır: spor ekranı ya da "Spor modu (deneysel)" gerekmez, yalnız [ownAllowed] (ör. düzenleme açık değil) sorulur.
 */
class OemDriveDialogCloser(private val delayMs: Long = DELAY_MS) {
    private var last: Int? = null
    private var dueAt: Long? = null
    /** Bekleyen yayın bizim isteğimizden mi (kapı [ownAllowed]) yoksa spor geçişinden mi (kapı [allowed]). */
    private var dueOwn = false
    /**
     * Kullanılan istek sayısı: her istek en çok bir değişimi kapatır (aracın geri dönmesi gibi fazladan değişim kapanmaz).
     * Pencere ([OWN_WINDOW_MS]) dolunca artan istekler de kullanılmış sayılır (birleşen değişimlerden kalan istek sonra
     * araçtan gelen bir değişimi kapatmasın).
     */
    private var usedOwn = 0

    /** true: yayını şimdi gönder (bu değişim için bir daha true dönmez). */
    fun update(
        now: Long,
        mode: Int?,
        allowed: Boolean,
        sportShown: Boolean,
        focusLost: Boolean = true,
        ownRequestAt: Long? = null,
        ownRequests: Int = 0,
        ownAllowed: Boolean = allowed,
    ): Boolean {
        val inWindow = ownRequestAt != null && now - ownRequestAt in 0..OWN_WINDOW_MS
        if (!inWindow) usedOwn = ownRequests
        if (mode != null) {
            val prev = last
            last = mode
            if (prev != null && prev != mode) {
                val starts = mode == DriveAccent.SPORT || (prev == DriveAccent.SPORT && sportShown)
                val own = inWindow && ownRequests > usedOwn
                if (own) usedOwn++
                // Yeni değişim önceki bekleyeni geçersiz kılar (değişim başına en çok bir yayın).
                dueOwn = own && ownAllowed
                dueAt = if (dueOwn || (allowed && starts)) now + delayMs else null
            }
        }
        val at = dueAt ?: return false
        if (!(if (dueOwn) ownAllowed else allowed)) { dueAt = null; return false }
        if (now < at) return false
        if (!focusLost) {
            // Odak bizde: pencere (henüz) açık değil. Biraz bekle; açılmazsa kapatılacak bir şey yok.
            if (now >= at + FOCUS_WAIT_MS) dueAt = null
            return false
        }
        dueAt = null
        return true
    }

    /** Son modu ve bekleyeni unutur: sonraki gözlem "ilk gözlem" sayılır (görünmezken/demo sırasında geçen değişim yayın üretmez). */
    fun reset() {
        last = null
        dueAt = null
        dueOwn = false
    }

    companion object {
        /** Gecikme: mod değişiminden ~300 ms sonra (pencere açılmış olsun). */
        const val DELAY_MS = 300L
        /** Süre dolduktan sonra pencerenin odağı almasını bekleme payı. */
        const val FOCUS_WAIT_MS = 1_500L
        /** Widget isteğinden sonra aracın modu bu süre içinde değişirse değişim "bizim" sayılır. */
        const val OWN_WINDOW_MS = 5_000L
    }
}
