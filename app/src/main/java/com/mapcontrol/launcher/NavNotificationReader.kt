package com.mapcontrol.launcher

import android.app.Notification
import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Icon
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.RemoteViews
import com.mapcontrol.BuildConfig
import java.lang.reflect.Field

/**
 * Yandex bildiriminden [NavNotificationInput] çıkarır: extras metinleri ve özel düzenin (RemoteViews) eylem listesi.
 * Manevra oku bildirimde bitmap değil, Yandex paketindeki bir çizimin kaynak numarası olarak gelir
 * (`setImageViewResource(primaryIcon, R.drawable.notification_right_sdl)`); numara sürümden sürüme değişir, ad değişmez.
 * Bu yüzden numara Yandex'in kendi kaynaklarıyla ([Resources.getResourceEntryName]) ada çevrilir.
 *
 * RemoteViews eylem listesi (`mActions`) gizli API'dir (targetSdk 28'de gri liste, Android 11'de erişilebilir); erişilemezse
 * yalnız extras metinleri kullanılır (mesafe ve sokak yine gelir, ok [NavManeuver.UNKNOWN] kalır).
 *
 * Debug derlemede her yeni bildirim düzeni (extras anahtarları + görünüm adları) bir kez "NavSource" etiketiyle günlüğe
 * yazılır (araçta gerçek düzeni öğrenmek için); sürüm derlemesinde hiçbir metin yazılmaz.
 */
internal object NavNotificationReader {
    private const val TAG = "NavSource"
    private const val MAX_DUMPS = 12

    private val resourcesCache = HashMap<String, Resources>()
    private val dumped = HashSet<String>()

    fun read(context: Context, sbn: StatusBarNotification): NavNotificationInput {
        val pkg = sbn.packageName
        val n = sbn.notification
        val extras: Bundle? = n.extras
        val res = resources(context, pkg)
        val texts = ArrayList<Pair<String?, String>>()
        val images = ArrayList<Pair<String?, String>>()
        val actionsLog = if (BuildConfig.DEBUG) ArrayList<String>() else null
        // Aynı RemoteViews'e bir kez girilir, toplam eylem sınırlı (6 Kasım araç ANR'si: eylemlerin dış sınıf
        // bağlantısı this$0 iç içe görünüm sanılıyor, aynı ağaç katlanarak ana iş parçacığında taranıyordu).
        val walk = Walk()
        @Suppress("DEPRECATION")
        listOfNotNull(n.contentView, n.bigContentView, n.headsUpContentView).forEach { rv ->
            collect(rv, res, texts, images, actionsLog, depth = 0, walk = walk)
        }
        val smallIcon = n.smallIcon?.let { icon -> iconResName(icon, res) }
        val input = NavNotificationInput(
            packageName = pkg,
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            infoText = extras?.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString(),
            viewTexts = texts,
            images = images,
            smallIconName = smallIcon,
        )
        if (BuildConfig.DEBUG) dump(sbn, extras, input, actionsLog.orEmpty())
        return input
    }

    /**
     * Ucuz içerik imzası ([NavParseGate] için): extras metinleri, `when`, küçük simge ve RemoteViews eylemlerinin ham
     * değerleri (metin/tamsayı). Kaynak adına çevirme, dize kurma ve günlük yok; aynı sınırlar (derinlik, 400 eylem).
     */
    fun contentSignature(sbn: StatusBarNotification): Int {
        val n = sbn.notification
        val extras: Bundle? = n.extras
        var h = sbn.packageName.hashCode()
        for (k in SIGNATURE_EXTRAS) h = 31 * h + (extras?.getCharSequence(k)?.toString()?.hashCode() ?: 0)
        h = 31 * h + n.`when`.hashCode()
        h = 31 * h + (try { n.smallIcon?.let { if (it.type == Icon.TYPE_RESOURCE) it.resId else 0 } ?: 0 } catch (e: Exception) { 0 })
        val walk = Walk()
        @Suppress("DEPRECATION")
        listOfNotNull(n.contentView, n.bigContentView, n.headsUpContentView).forEach { rv ->
            h = 31 * h + rawHash(rv, 0, walk)
        }
        return h
    }

    private val SIGNATURE_EXTRAS = arrayOf(
        Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT,
        Notification.EXTRA_BIG_TEXT, Notification.EXTRA_INFO_TEXT,
    )

    private fun rawHash(rv: RemoteViews, depth: Int, walk: Walk): Int {
        if (depth > 4 || !walk.seen.add(rv)) return 0
        val actions = try {
            field(RemoteViews::class.java, "mActions")?.get(rv) as? List<*>
        } catch (e: Exception) {
            null
        } ?: return 0
        var h = actions.size
        for (action in actions) {
            if (action == null) continue
            if (++walk.actions > MAX_ACTIONS) return h
            try {
                val cls = action.javaClass
                h = 31 * h + ((field(cls, "viewId")?.get(action) as? Int) ?: 0)
                h = 31 * h + when (val v = field(cls, "value")?.get(action)) {
                    is CharSequence -> v.toString().hashCode()
                    is Int -> v
                    is Icon -> try { if (v.type == Icon.TYPE_RESOURCE) v.resId else 1 } catch (e: Exception) { 1 }
                    null -> 0
                    else -> v.javaClass.hashCode()
                }
                nestedFields(cls).forEach { f ->
                    (f.get(action) as? RemoteViews)?.let { if (it !== rv) h = 31 * h + rawHash(it, depth + 1, walk) }
                }
            } catch (e: Exception) {
            }
        }
        return h
    }

    /** Yandex'in kaynakları (paket başına bir kez yüklenir). */
    private fun resources(context: Context, pkg: String): Resources? = resourcesCache[pkg] ?: try {
        context.packageManager.getResourcesForApplication(pkg).also { resourcesCache[pkg] = it }
    } catch (e: Exception) {
        null
    }

    /** Uygulama güncellenince kaynak tablosu değişir: önbelleği bırak. */
    fun forget(pkg: String) {
        resourcesCache.remove(pkg)
    }

    /** Tek okumada gezilen RemoteViews'ler (kimlik) ve sayılan eylemler. */
    private class Walk {
        val seen: MutableSet<RemoteViews> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap())
        var actions = 0
    }

    private const val MAX_ACTIONS = 400

    /** Kaynak numarası gibi görünen değer mi (paket kimliği 0x01..0x7f): küçük tamsayılar (8, 120…) sorulmaz. */
    internal fun looksLikeResId(id: Int): Boolean = (id ushr 24) in 0x01..0x7f

    private fun name(res: Resources?, id: Int): String? {
        if (res == null || !looksLikeResId(id)) return null
        return try {
            res.getResourceEntryName(id)
        } catch (e: Resources.NotFoundException) {
            null
        }
    }

    private fun iconResName(icon: Icon, res: Resources?): String? = try {
        if (icon.type == Icon.TYPE_RESOURCE) name(res, icon.resId) else null
    } catch (e: Exception) {
        null
    }

    /** RemoteViews eylemlerini dolaşır: metin ("setText") ve çizim ("setImageResource", "setImageIcon"); iç içe görünümlere de iner. */
    private fun collect(
        rv: RemoteViews,
        res: Resources?,
        texts: MutableList<Pair<String?, String>>,
        images: MutableList<Pair<String?, String>>,
        log: MutableList<String>?,
        depth: Int,
        walk: Walk,
    ) {
        if (depth > 4 || !walk.seen.add(rv)) return
        val actions = try {
            field(RemoteViews::class.java, "mActions")?.get(rv) as? List<*>
        } catch (e: Exception) {
            null
        } ?: return
        for (action in actions) {
            if (action == null) continue
            if (++walk.actions > MAX_ACTIONS) return
            try {
                val cls = action.javaClass
                val viewId = (field(cls, "viewId")?.get(action) as? Int) ?: 0
                val viewName = name(res, viewId)
                val method = field(cls, "methodName")?.get(action) as? String
                val value = field(cls, "value")?.get(action)
                when {
                    method == "setText" && value is CharSequence -> texts += viewName to value.toString()
                    method == "setImageResource" && value is Int -> name(res, value)?.let { images += viewName to it }
                    method == "setImageIcon" && value is Icon -> iconResName(value, res)?.let { images += viewName to it }
                }
                log?.add("${cls.simpleName} view=$viewName method=$method value=${describe(value, res)}")
                // İç içe RemoteViews (ViewGroupActionAdd.mNestedViews vb.).
                // Dış sınıf bağlantısı (this$0, sentetik) üst RemoteViews'in kendisidir: atlanır.
                nestedFields(cls).forEach { f ->
                    (f.get(action) as? RemoteViews)?.let { if (it !== rv) collect(it, res, texts, images, log, depth + 1, walk) }
                }
            } catch (e: Exception) {
                // Bir eylem okunamazsa diğerlerine devam.
            }
        }
    }

    private fun describe(value: Any?, res: Resources?): String = when (value) {
        null -> "null"
        is CharSequence -> "\"$value\""
        is Int -> name(res, value)?.let { "@$it" } ?: value.toString()
        else -> value.javaClass.simpleName
    }

    private val fieldCache = HashMap<Pair<Class<*>, String>, Field?>()

    private fun field(cls: Class<*>, name: String): Field? = fieldCache.getOrPut(cls to name) {
        var c: Class<*>? = cls
        var found: Field? = null
        while (c != null && found == null) {
            found = try {
                c.getDeclaredField(name).apply { isAccessible = true }
            } catch (e: NoSuchFieldException) {
                null
            } catch (e: SecurityException) {
                null
            }
            c = c.superclass
        }
        found
    }

    private val nestedCache = HashMap<Class<*>, List<Field>>()

    /** Eylem sınıfının iç içe RemoteViews alanları (sınıf başına bir kez; sentetik dış bağlantı hariç). */
    private fun nestedFields(cls: Class<*>): List<Field> = nestedCache.getOrPut(cls) {
        allFields(cls).filter { !it.isSynthetic && it.name != "this\$0" && RemoteViews::class.java.isAssignableFrom(it.type) }
    }

    private fun allFields(cls: Class<*>): List<Field> {
        val out = ArrayList<Field>()
        var c: Class<*>? = cls
        while (c != null && c != Any::class.java) {
            c.declaredFields.forEach { f ->
                try {
                    f.isAccessible = true
                    out += f
                } catch (e: Exception) {
                }
            }
            c = c.superclass
        }
        return out
    }

    /** Debug: yeni bir düzen (paket + extras anahtarları + görünüm adları) ilk görüldüğünde tam dökümü yazar. */
    private fun dump(sbn: StatusBarNotification, extras: Bundle?, input: NavNotificationInput, actions: List<String>) {
        val keys = extras?.keySet()?.sorted().orEmpty()
        val signature = sbn.packageName + "|" + keys.joinToString(",") + "|" +
            (input.viewTexts.map { it.first } + input.images.map { it.first }).joinToString(",")
        if (dumped.size >= MAX_DUMPS || !dumped.add(signature)) return
        Log.d(TAG, "pkg=${sbn.packageName} id=${sbn.id} tag=${sbn.tag} ongoing=${sbn.isOngoing} category=${sbn.notification.category}")
        keys.forEach { k ->
            @Suppress("DEPRECATION") val v = extras?.get(k)
            Log.d(TAG, "  extra $k = ${if (v is CharSequence) "\"$v\"" else v?.javaClass?.simpleName}")
        }
        Log.d(TAG, "  smallIcon=${input.smallIconName}")
        actions.forEach { Log.d(TAG, "  action $it") }
        Log.d(TAG, "  parsed=${NavParse.parse(input)}")
    }
}
