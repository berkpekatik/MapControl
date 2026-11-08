package com.mapcontrol.launcher

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.mapcontrol.R
import java.util.Locale

/** Profil adı ve avatar harfi için saf (Android'siz) mantık; birim testle doğrulanır. */
object ProfileNaming {
    const val MAX_NAME = 16
    private val TR = Locale("tr")

    /** Boşlukları kırpar, [MAX_NAME] ile keser. Boş ya da varsayılana eşitse "" (= varsayılan ad kullanılır). */
    fun sanitizeName(raw: String?, defaultName: String): String {
        val t = (raw ?: "").trim().take(MAX_NAME).trim()
        return if (t.isEmpty() || t == defaultName) "" else t
    }

    /** Avatar yazısı: varsayılan adda profil numarası, özel adda ilk harf (Türkçe büyük harf: i -> İ, ı -> I). */
    fun avatarLabel(id: Int, storedName: String, defaultName: String): String {
        val n = sanitizeName(storedName, defaultName)
        if (n.isEmpty()) return id.toString()
        val first = n.substring(0, n.offsetByCodePoints(0, 1))
        return first.uppercase(TR)
    }
}

/**
 * Sürücü profilleri (1..[COUNT]). Her profilin kendi ana ekran düzeni, teması, uygulama yuvaları ve
 * navigasyon kısayolları vardır. Depolama: profil 1 mevcut tercih adlarını/anahtarlarını AYNEN kullanır
 * (taşıma yok, mevcut kullanıcı hiçbir şey kaybetmez); profil 2 ve 3 aynı adın sonuna "_p2"/"_p3" ekler.
 * Seçili profil numarası ayrı, profilden bağımsız bir tercih dosyasında tutulur.
 *
 * Araçtan otomatik seçim için bkz. [autoSelectFromVehicle] (henüz bağlı değil).
 */
object Profiles {
    const val COUNT = 3
    const val DEFAULT_ID = 1

    private const val PREFS = "launcher_profile"
    private const val KEY_CURRENT = "current"

    private const val KEY_NAME = "name_"
    private const val KEY_COLOR = "color_"

    /** Sakin, beyaz yazı okunur 6 ton: adaçayı, mavi, mor-mavi, erik, kil, kum. */
    val PALETTE: IntArray = intArrayOf(
        0xFF5F8F7B.toInt(), 0xFF5B84B1.toInt(), 0xFF7B7DB8.toInt(),
        0xFF9A6B9A.toInt(), 0xFFB5745A.toInt(), 0xFFA38A4F.toInt(),
    )

    private val listeners = LinkedHashSet<(Int) -> Unit>()
    private val appearanceListeners = LinkedHashSet<() -> Unit>()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Geçersiz/bozuk numarayı 1..[COUNT] aralığına çeker; bilinmeyen değer profil 1 olur. */
    fun sanitize(id: Int): Int = if (id in 1..COUNT) id else DEFAULT_ID

    /** [base] tercih dosyası adı ya da anahtarı için profile özel ad. Profil 1 için [base] değişmez. */
    fun key(base: String, id: Int): String {
        val p = sanitize(id)
        return if (p == DEFAULT_ID) base else "${base}_p$p"
    }

    /** Geçerli profil için [key]. */
    fun key(context: Context, base: String): String = key(base, current(context))

    fun current(context: Context): Int =
        sanitize(prefs(context).getInt(KEY_CURRENT, DEFAULT_ID))

    /** Profili değiştirir ve dinleyicilere haber verir; aynı profil seçiliyse hiçbir şey yapmaz. */
    fun select(context: Context, id: Int) {
        val p = sanitize(id)
        if (p == current(context)) return
        prefs(context).edit().putInt(KEY_CURRENT, p).apply()
        listeners.toList().forEach { it(p) }
    }

    fun addListener(l: (Int) -> Unit) { listeners.add(l) }

    fun removeListener(l: (Int) -> Unit) { listeners.remove(l) }

    fun defaultName(context: Context, id: Int): String =
        context.getString(R.string.launcher_profile_default_name, sanitize(id))

    /** Kullanıcının adı; yoksa varsayılan ("Sürücü 1"). */
    fun displayName(context: Context, id: Int): String {
        val stored = prefs(context).getString(KEY_NAME + sanitize(id), "") ?: ""
        return ProfileNaming.sanitizeName(stored, defaultName(context, id)).ifEmpty { defaultName(context, id) }
    }

    /** Avatarda gösterilen tek harf ya da numara. */
    fun avatarLabel(context: Context, id: Int): String =
        ProfileNaming.avatarLabel(sanitize(id), prefs(context).getString(KEY_NAME + sanitize(id), "") ?: "", defaultName(context, id))

    /** Profil rengi; seçilmemişse profile göre ayrı varsayılan. Paletten değilse varsayılana döner. */
    fun color(context: Context, id: Int): Int {
        val p = sanitize(id)
        val c = prefs(context).getInt(KEY_COLOR + p, PALETTE[p - 1])
        return if (c in PALETTE) c else PALETTE[p - 1]
    }

    /** Ad ve rengi kaydeder (ad temizlenir) ve görünüm dinleyicilerine haber verir. */
    fun saveAppearance(context: Context, id: Int, name: String, color: Int) {
        val p = sanitize(id)
        prefs(context).edit()
            .putString(KEY_NAME + p, ProfileNaming.sanitizeName(name, defaultName(context, p)))
            .putInt(KEY_COLOR + p, if (color in PALETTE) color else PALETTE[p - 1])
            .apply()
        appearanceListeners.toList().forEach { it() }
    }

    /** Ad/renk değişince çağrılır (profil değişimi değil); başlıktaki avatarı tazelemek için. */
    fun addAppearanceListener(l: () -> Unit) { appearanceListeners.add(l) }

    fun removeAppearanceListener(l: () -> Unit) { appearanceListeners.remove(l) }

    /**
     * YER TUTUCU, henüz bağlı DEĞİL ve bir şey okumaz. Araçtaki CarSetting/HUD `HUDID.ID_HUD_PERSONAL_ID`
     * (araçta 4 okundu) sürücü profilini belirleyebilir; eşleme ve davranış araç testi gerektirir.
     * Bağlanınca [personalId] -> profil numarası eşlemesi yapılıp [select] çağrılmalıdır.
     * @return seçilecek profil numarası; şimdilik her zaman null (otomatik seçim yok).
     */
    @Suppress("UNUSED_PARAMETER")
    fun autoSelectFromVehicle(personalId: Int): Int? = null
}

/**
 * Profil seçici: büyük (≥76dp) satırlar, geçerli profil işaretli. Seçim [onSwitched] ile yeniden kurulumu tetikler.
 * Satırdaki "Düzenle" aynı panelde ad/renk düzenleyicisini açar; araç hareket halindeyken ([isMoving]) kilitli.
 */
fun showProfileSheet(activity: Activity, isMoving: () -> Boolean = { false }, onSwitched: () -> Unit): Dialog =
    showLauncherSheet(activity, R.string.launcher_profile) { d ->
        val host = this
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        lateinit var showList: () -> Unit
        fun blocked(): Boolean {
            if (!isMoving()) return false
            Toast.makeText(activity, R.string.launcher_edit_locked_driving, Toast.LENGTH_SHORT).show()
            return true
        }
        showList = {
            host.removeAllViews()
            val current = Profiles.current(activity)
            for (id in 1..Profiles.COUNT) {
                if (id > 1) host.addView(gap(activity, 12, vertical = true))
                host.addView(profileRow(activity, id, id == current,
                    onEdit = {
                        if (!blocked()) {
                            host.removeAllViews()
                            host.addView(profileEditor(activity, id, isMoving = ::blocked,
                                onDone = { showList() }), sheetFullWidth())
                        }
                    },
                    onClick = {
                        d.dismiss()
                        if (id != current) {
                            Profiles.select(activity, id)
                            onSwitched()
                        }
                    }), sheetFullWidth())
            }
        }
        showList()
    }

/** Yuvarlak simge: profil renginde, içinde ad harfi ya da numara. [selected] yalnızca satırdaki işaret içindir. */
@Suppress("UNUSED_PARAMETER")
fun profileAvatar(context: Context, id: Int, sizeDp: Int, selected: Boolean): TextView =
    text(context, LauncherTheme.BODY_SP, LauncherTheme.ON_ACCENT, bold = true).apply {
        text = Profiles.avatarLabel(context, id)
        gravity = Gravity.CENTER
        background = oval(Profiles.color(context, id))
        layoutParams = LinearLayout.LayoutParams(context.dp(sizeDp), context.dp(sizeDp))
    }

private fun profileRow(activity: Activity, id: Int, selected: Boolean, onEdit: () -> Unit, onClick: () -> Unit): LinearLayout =
    LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(activity, LauncherTheme.CONTROL, 16f)
        minimumHeight = activity.dp(ROW_DP)
        val p = activity.dp(24)
        setPadding(p, activity.dp(8), activity.dp(8), activity.dp(8))
        addView(profileAvatar(activity, id, 48, selected))
        addView(gap(activity, 20))
        addView(text(activity, LauncherTheme.BODY_SP, medium = true).apply { text = Profiles.displayName(activity, id) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (selected) {
            addView(icon(activity, R.drawable.ic_mdi_check, 32, LauncherTheme.ACCENT))
            addView(gap(activity, 12))
        }
        addView(text(activity, LauncherTheme.LABEL_SP, LauncherTheme.TEXT_SECONDARY, medium = true).apply {
            setText(R.string.launcher_edit)
            gravity = Gravity.CENTER
            minWidth = activity.dp(EDIT_MIN_DP)
            minimumHeight = activity.dp(EDIT_MIN_DP)
            isClickable = true
            isFocusable = true
            contentDescription = activity.getString(R.string.launcher_edit) + ": " + Profiles.displayName(activity, id)
            setOnClickListener { onEdit() }
            pressFeedback()
        })
        contentDescription = Profiles.displayName(activity, id)
        isSelected = selected
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        pressFeedback()
    }

/** Satır içi düzenleyici: ad alanı (en çok [ProfileNaming.MAX_NAME] karakter), 6 renk, Kaydet/Vazgeç. */
private fun profileEditor(activity: Activity, id: Int, isMoving: () -> Boolean, onDone: () -> Unit): LinearLayout {
    var chosen = Profiles.color(activity, id)
    val input = EditText(activity).apply {
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, LauncherTheme.BODY_SP)
        setTextColor(LauncherTheme.TEXT)
        setHintTextColor(LauncherTheme.TEXT_SECONDARY)
        hint = Profiles.defaultName(activity, id)
        setText(Profiles.displayName(activity, id))
        setSelection(text.length)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        imeOptions = EditorInfo.IME_ACTION_DONE
        setSingleLine(true)
        filters = arrayOf(InputFilter.LengthFilter(ProfileNaming.MAX_NAME))
        background = rounded(activity, LauncherTheme.CONTROL, 16f)
        val pad = activity.dp(24)
        setPadding(pad, activity.dp(12), pad, activity.dp(12))
        minimumHeight = activity.dp(ROW_DP)
        contentDescription = activity.getString(R.string.launcher_profile_name)
    }
    val swatches = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
    fun drawSwatches() {
        swatches.removeAllViews()
        Profiles.PALETTE.forEachIndexed { i, c ->
            val cell = FrameLayout(activity).apply {
                isClickable = true
                isFocusable = true
                contentDescription = activity.getString(R.string.launcher_profile_color, i + 1)
                isSelected = c == chosen
                setOnClickListener { chosen = c; drawSwatches() }
                addView(View(activity).apply { background = oval(c) },
                    FrameLayout.LayoutParams(activity.dp(52), activity.dp(52), Gravity.CENTER))
                if (c == chosen) addView(icon(activity, R.drawable.ic_mdi_check, 28, LauncherTheme.ON_ACCENT),
                    FrameLayout.LayoutParams(activity.dp(28), activity.dp(28), Gravity.CENTER))
            }
            swatches.addView(cell, LinearLayout.LayoutParams(0, activity.dp(EDIT_MIN_DP), 1f))
        }
    }
    drawSwatches()

    fun button(label: Int, primary: Boolean, onClick: () -> Unit) =
        text(activity, LauncherTheme.BODY_SP, if (primary) LauncherTheme.ON_ACCENT else LauncherTheme.TEXT, medium = true).apply {
            setText(label)
            gravity = Gravity.CENTER
            background = rounded(activity, if (primary) LauncherTheme.ACCENT else LauncherTheme.CONTROL, 16f)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            pressFeedback()
        }

    return LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(text(activity, LauncherTheme.LABEL_SP, LauncherTheme.TEXT_SECONDARY).apply { setText(R.string.launcher_profile_name) })
        addView(gap(activity, 8, vertical = true))
        addView(input, sheetFullWidth())
        addView(gap(activity, 20, vertical = true))
        addView(swatches, sheetFullWidth())
        addView(gap(activity, 24, vertical = true))
        addView(LinearLayout(activity).apply {
            addView(button(R.string.launcher_profile_cancel, false) { onDone() },
                LinearLayout.LayoutParams(0, activity.dp(ROW_DP), 1f))
            addView(gap(activity, 12))
            addView(button(R.string.launcher_profile_save, true) {
                // Kaydederken sürüş başlamış olabilir; yine kilitle.
                if (!isMoving()) {
                    Profiles.saveAppearance(activity, id, input.text.toString(), chosen)
                    onDone()
                }
            }, LinearLayout.LayoutParams(0, activity.dp(ROW_DP), 1f))
        }, sheetFullWidth())
    }
}

private const val ROW_DP = 80
private const val EDIT_MIN_DP = 76
