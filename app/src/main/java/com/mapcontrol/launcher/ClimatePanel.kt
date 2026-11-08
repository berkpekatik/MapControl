package com.mapcontrol.launcher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.desaysv.ivi.extra.project.carinfo.HvacID
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy
import com.desaysv.ivi.extra.project.carinfo.proxy.Constants
import com.desaysv.ivi.extra.project.carinfo.proxy.IServiceConnectListener
import com.desaysv.ivi.vdb.IVDBusNotify
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo
import com.mapcontrol.R
import kotlin.math.abs

/** Alt çubukta yukarı kaydırma ile OEM klima panelini açma eşiği (saf mantık, birim testli). */
internal object ClimatePanelGesture {
    const val OPEN_SWIPE_DP = 60f

    /** Yukarı hareket (dy > 0 yukarı) kayma payını aşıp ağırlıkla dikey mi: yakalama için. */
    fun isUpwardDrag(dy: Float, dx: Float, slopPx: Float): Boolean = dy > slopPx && dy > abs(dx)

    /** Bırakınca panel açılsın mı: dikey yukarı hareket en az [OPEN_SWIPE_DP] ve yataydan büyük. */
    fun shouldOpen(dyPx: Float, dxPx: Float, density: Float): Boolean =
        dyPx >= OPEN_SWIPE_DP * density && dyPx > abs(dxPx)
}

/**
 * OEM klima panelini açar (araçta çubuğun gizlediği OEM yukarı kaydırma jestinin yerine). Araca yazma yok: yalnızca
 * OEM HVAC servisine arayüz açma isteği (`type` = OPEN_PANEL, `page` 0 = ön klima). Not (araçta ölçüldü): OPEN_PANEL
 * statik `isHvacShow` bayrağını true yapar ama paneli ZORLA açmaz; HVAC 9 bit 0 o an 0 ise panel kapalı kalır.
 * Servis başarısız olursa OEM
 * uygulamasının ana etkinliği, o da yoksa kısa uyarı. Sonuç ([TAG]) bir kez günlüğe yazılır.
 */
internal object ClimatePanel {
    private const val TAG = "ClimatePanel"
    const val PACKAGE = "com.desaysv.svhvac"
    private const val SERVICE = "com.desaysv.svhvac.service.HvacService"
    const val TYPE_OPEN = "com.desaysv.svhvac.OPEN_PANEL"
    private const val PAGE_FRONT = 0

    /** Bilerek açma (çubukta yukarı kaydırma): koruma bunu asla kapatmaz ([ClimatePanelGuard.noteExplicitOpen]). */
    fun open(context: Context) {
        ClimatePanelGuard.noteExplicitOpen()
        val outcome = try {
            context.startService(
                Intent().setComponent(ComponentName(PACKAGE, SERVICE))
                    .putExtra("type", TYPE_OPEN)
                    .putExtra("page", PAGE_FRONT),
            )?.let { "service" } ?: fallback(context, "service not found")
        } catch (e: SecurityException) {
            fallback(context, "SecurityException")
        } catch (e: IllegalStateException) {
            fallback(context, "IllegalStateException")
        }
        Log.i(TAG, "open outcome=$outcome")
        if (outcome == "failed") Toast.makeText(context, R.string.launcher_climate_panel_failed, Toast.LENGTH_SHORT).show()
    }

    /**
     * OEM panelini kapatır: `HvacService` `type` = CLOSE_HVAC_BT. Bu araç yazılımında panel OEM klima
     * uygulamasının kendisidir ve servis bu isteği şöyle işler:
     * HVAC 9 bit 0 (ön sıra) açıksa 9 ← 2, bit 2 (koku) açıksa 64 ← 2, bit 1 (PM2.5) açıksa 51 ← 0.
     * Bu yazmaları BİZ değil OEM uygulaması yapar; değerler OEM'in panelde aşağı kaydırma kapatmasıyla aynıdır
     * (9 ← 2). ECU 9=0 gönderince panel kendi animasyonuyla kapanır.
     *
     * CLOSE_PANEL KULLANILMAZ: servis statik `isHvacShow` bayrağını false yapar ve bayrak yeni bir
     * OPEN_PANEL gelene kadar öyle kalır; o sürede ECU 9=1 gönderse de (fiziksel klima düğmesi, OEM dock) panel açılmaz
     * (araç günlüğünde `isHvacShow =false`). Bazı yazılım sürümlerinde servis aynı türü
     * `ACTION_HVAC_SERVICE` yayınına çevirir, SystemUI `HvacActionReceiver` CLOSE_HVAC_BT'yi de işler; servis yoksa yayını biz göndeririz.
     */
    fun close(context: Context, hv9Before: Int?): String {
        val outcome = try {
            context.startService(
                Intent().setComponent(ComponentName(PACKAGE, SERVICE)).putExtra("type", TYPE_CLOSE_HVAC_BT).putExtra("page", PAGE_FRONT),
            )?.let { "service" } ?: closeBroadcast(context)
        } catch (e: SecurityException) {
            closeBroadcast(context)
        } catch (e: IllegalStateException) {
            closeBroadcast(context)
        }
        // Araca yazmayı OEM servisi yapar (beyaz listemizden geçmez); iz kalsın diye WidgetWrite'a da bir satır. HVAC 9
        // isteği göndermeden ÖNCE okunur (servisin hangi yazmayı yapacağını o belirler); sonrası 9 ← 2 geri okumasında görünür.
        Log.i(WidgetWritePolicy.LOG_TAG, "widget=ClimatePanelClose via=oem-service type=CLOSE_HVAC_BT outcome=$outcome hv9Before=${ClimatePanelBits.describe(hv9Before)}")
        return outcome
    }

    /** HVAC 9'u bir kez okur (eşzamanlı VDBus; yalnızca panel açık görünürken çağrılır). */
    fun readHv9(): Int? = CarSignals.readInt(VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_DISPLAY_ACTIVE)

    private fun closeBroadcast(context: Context): String = try {
        context.sendBroadcast(Intent(ACTION_HVAC_SERVICE).putExtra("type", TYPE_CLOSE_HVAC_BT).putExtra("page", PAGE_FRONT))
        "broadcast"
    } catch (e: SecurityException) {
        "failed"
    }

    /** OEM paneli açık mı: [ClimatePanelBits.SHOW_KEYS] anahtarlarından biri "true". */
    fun isShowing(context: Context): Boolean = try {
        val r = context.contentResolver
        ClimatePanelBits.anyShown { Settings.System.getString(r, it) }
    } catch (e: RuntimeException) {
        false
    }

    private const val TYPE_CLOSE_HVAC_BT = "com.desaysv.svhvac.CLOSE_HVAC_BT"
    private const val ACTION_HVAC_SERVICE = "com.desaysv.svhvac.ACTION_HVAC_SERVICE"

    private fun fallback(context: Context, reason: String): String {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return "failed"
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "activity ($reason)"
        } catch (e: ActivityNotFoundException) {
            "failed"
        } catch (e: SecurityException) {
            "failed"
        }
    }
}

/**
 * OEM klima panelinin görünürlük anahtarları ve HVAC 9 bit alanı (saf, birim testli).
 *
 * Görünürlük: bizim araçta OEM klima uygulaması `Settings.System "hvacshow"` (tamamı küçük harf) yazar; açılınca "true",
 * kapanma animasyonu bitince ve yok edilirken "false".
 * Settings anahtarları büyük/küçük harfe duyarlıdır. `hvacShow` ve `HVAC_DIALOG_IS_SHOW` bazı yazılım sürümlerinin (panel
 * SystemUI'de) anahtarlarıdır; yedek olarak sonra okunur.
 *
 * HVAC 9 düz 1/0 değil, bit alanıdır: bit 0 ön sıra klima, bit 1 PM2.5, bit 2 koku
 * (`(f(9) >> n) & 1`).
 */
internal object ClimatePanelBits {
    val SHOW_KEYS = listOf("hvacshow", "hvacShow", "HVAC_DIALOG_IS_SHOW")

    /** [get]: anahtar → Settings değeri. Anahtarlardan biri tam "true" ise panel açık. */
    fun anyShown(get: (String) -> String?): Boolean = SHOW_KEYS.any { get(it) == "true" }

    fun frontRow(hv9: Int?): Boolean = hv9 != null && (hv9 and 1) != 0
    fun pm25(hv9: Int?): Boolean = hv9 != null && (hv9 and 2) != 0
    fun fragrance(hv9: Int?): Boolean = hv9 != null && (hv9 and 4) != 0

    /**
     * CLOSE_HVAC_BT bir şey yazar mı: yalnızca ön sıra, PM2.5 ve koku bitlerini temizler.
     * Panel arka sıralar için de açılır (2. sıra config 151, 3. sıra config 138); o durumda ya da
     * servisin modeli boşsa hiçbir şey yazılmaz. Okunamadıysa (`null`) denemeye izin verilir.
     */
    fun closeCanAct(hv9: Int?): Boolean = hv9 == null || frontRow(hv9) || pm25(hv9) || fragrance(hv9)

    /** Günlük için kısa çözüm: `null`, `0` ya da `5[front,fragrance]`. */
    fun describe(hv9: Int?): String {
        if (hv9 == null) return "null"
        val parts = listOfNotNull("front".takeIf { frontRow(hv9) }, "pm25".takeIf { pm25(hv9) }, "fragrance".takeIf { fragrance(hv9) })
        return if (parts.isEmpty()) "$hv9" else "$hv9[${parts.joinToString(",")}]"
    }
}

/**
 * "Klima değişince fabrika paneli: Göster | Gizle" (varsayılan GİZLE). Gizle iken alt çubuktaki sıcaklık/fan OEM'in kendi
 * dock'unun kimlikleriyle yazılır ve klima tuşlarına kilit ([ClimatePanelGuard]) kendiliğinden açılan paneli kapatır; Göster iken eski panel kimlikleri (10/13/12).
 */
internal object ClimatePanelPrefs {
    private const val PREFS_NAME = "launcher_settings"
    private const val KEY_SHOW = "climate_oem_panel_on_change"

    fun showOnChange(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(Profiles.key(context, KEY_SHOW), false)

    fun setShowOnChange(context: Context, show: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(Profiles.key(context, KEY_SHOW), show).apply()
    }
}

/**
 * "Klima tuşlarına kilit": klima durumu değişince OEM panelinin kendiliğinden açılmasına dair SAF karar (birim testli).
 *
 * Neden açılıyor (araçta ölçüldü): panel OEM klima uygulamasındadır ve HVAC 9'un bitlerinden biri gelince açılır
 * ([ClimatePanelBits]). Fiziksel klima tuşları, OEM alt dock'u ve bizim dock
 * yazmalarımız ECU'nun 9=1 göndermesine yol açar (7 Kasım araç testi: güç açma, AC, sıcaklık, iç/dış hava, AUTO, fan, ön
 * buz çözme açar; klima kapatma ve arka cam ısıtma açmaz). Panel açılışı değişen değerden ~0,5 sn ÖNCE de gelebilir.
 *
 * Kural: panel göründü VE (değişiklikten sonraki [CHANGE_MATCH_MS] ya da açılıştan sonraki [SHOW_BEFORE_CHANGE_MS] içinde bir
 * klima değeri değişti ya da dock yazmamız oldu) VE kullanıcı paneli bilerek açmadı → kapat. Son değişiklikten sonra [SETTLE_MS] (ve her kapatmadan sonra [RESHOW_MS]) boyunca yeniden
 * açılışlar da kapatılır; pencere başına en çok [MAX_CLOSES]. Çevresinde değişiklik olmayan açılış (OEM uygulaması, sesli
 * komut, OEM'in kendi yukarı kaydırması) olduğu gibi bırakılır. Araca hiçbir şey yazılmaz; kapatmayı OEM servisi yapar
 * ([ClimatePanel.close]).
 */
internal object ClimatePanelPolicy {
    /** Değer değişikliğinden sonra bu süre içinde gelen açılış tuşa basıştan sayılır. */
    const val CHANGE_MATCH_MS = 2_000L

    /**
     * Açılış değişiklikten ÖNCE geldiyse (tuşta panel değerden ~0,5 sn önce açılıyor; 7 Kasım: en çok ~0,67 sn) ancak bu
     * süre içinde gelen değişiklik tuşa basış sayılır. Daha geç değişiklik OEM'den/sesle açılmış panelin içinde kullanıcının
     * kendi ayarıdır: o panel kapatılmaz.
     */
    const val SHOW_BEFORE_CHANGE_MS = 1_000L

    /** Son değişiklikten sonra yeniden açılışların kapatıldığı süre (her yeni değişiklik uzatır). */
    const val SETTLE_MS = 2_500L

    /** Kapatmadan sonra yeniden açılış bu süre içinde gelirse yine kapatılır (7 Kasım: 0,4–1,3 sn sonra yeniden açıldı). */
    const val RESHOW_MS = 1_500L

    /**
     * Kapatma isteğinden sonra bu süre içindeki açılış sinyali aynı panelin kapanışıdır: ECU ~130 ms'de 9=0 gönderir
     * (araç günlüğünde ölçüldü), `hvacshow` ise 800 ms'lik kapanma animasyonu bitince "false" olur.
     * İki kaynağın aynı açılış için ikinci kenarı yeniden kapatma doğurmasın.
     */
    const val CLOSING_MS = 600L

    /** Kapatmadan sonra bir kez daha bakış (ECU + animasyon bitti): kaçmış yeniden açılış varsa kapatılır. */
    const val RECHECK_MS = 1_100L

    /** Bilerek açmadan sonra panelin görünmesi için tanınan süre; bu sürede görünürse kapanana kadar korunur. */
    const val EXPLICIT_GRACE_MS = 3_000L

    /** Bir pencerede en çok bu kadar kapatma (OEM ile kavgaya girmesin). */
    const val MAX_CLOSES = 6

    enum class Decision {
        /** Paneli kapat ([ClimatePanel.close]). */
        CLOSE,
        /** Açıldı ama çevresinde değişiklik yok (henüz): [SHOW_BEFORE_CHANGE_MS] içinde değişiklik gelirse kapatılır. */
        PENDING,
        /** Ayar "Göster": hiçbir şey yapılmaz. */
        KEEP_PREF_SHOW,
        /** Kullanıcı paneli bilerek açtı (alt çubukta yukarı kaydırma): asla kapatma. */
        KEEP_EXPLICIT_OPEN,
        /** Az önce kapatıldı; aynı panelin ikinci sinyali. */
        KEEP_CLOSING,
        /** Panel açık ama CLOSE_HVAC_BT'nin temizleyeceği bit yok (arka sıra ya da bilinmeyen neden). */
        UNCLOSABLE,
        /** Pencere başına kapatma sınırı doldu. */
        LIMIT,
        /** Yapılacak bir şey yok (aynı açılışın yinelenen sinyali, kapalı panel, pencere dışı). */
        NONE,
    }

    /**
     * Kilit durumu (tek iş parçacığından sürülür; zaman ms, monoton saat). Olaylar: [change] (izlenen HVAC değeri değişti ya
     * da dock yazması), [explicitOpen], [shown] (panel kapalıdan açığa geçti; HVAC 9 bit 0 ya da `hvacshow` kenarı),
     * [hidden], [recheck] (kapatmadan [RECHECK_MS] sonra). `showPref`: [ClimatePanelPrefs.showOnChange];
     * `canAct`: [ClimatePanelBits.closeCanAct] (HVAC 9 okunamadıysa `null` → true).
     */
    class Lock {
        private val never = Long.MIN_VALUE / 4
        private var lastChangeAt = never
        private var lastCloseAt = never
        private var lastShowAt = never
        private var pendingShowAt = never
        private var explicitAt = never
        private var explicitShown = false
        private var visible = false

        /** Bu kavgadaki (ardışık yeniden açılışlar) kapatma sayısı; ayrı tuş basışlarında sıfırlanır. */
        var closes = 0
            private set

        fun inWindow(t: Long): Boolean = t - lastChangeAt <= SETTLE_MS || t - lastCloseAt <= RESHOW_MS

        /** Son değişiklikten bu yana geçen süre (günlük için; hiç yoksa -1). */
        fun sinceChange(t: Long): Long = if (lastChangeAt == never) -1 else t - lastChangeAt

        private fun explicitActive(t: Long): Boolean = t - explicitAt <= EXPLICIT_GRACE_MS || explicitShown

        /** Bilerek açma. [visibleNow]: panel zaten açıksa o panel korunur. */
        fun explicitOpen(t: Long, visibleNow: Boolean) {
            explicitAt = t
            explicitShown = visibleNow
            pendingShowAt = never
        }

        /** Klima değeri değişti (tuş, OEM dock, bizim dock). [visibleNow]: panel şu an açık mı. */
        fun change(t: Long, visibleNow: Boolean, showPref: Boolean, canAct: Boolean): Decision {
            if (!inWindow(t)) closes = 0
            lastChangeAt = t
            val pendingMatch = visibleNow && t - pendingShowAt in 0..SHOW_BEFORE_CHANGE_MS
            if (!pendingMatch) return Decision.NONE
            pendingShowAt = never
            return decideClose(t, showPref, canAct)
        }

        /**
         * Panel açığa geçti. Arada [hidden] yoksa (aynı açılışın ikinci kaynaktan gelen kenarı) [Decision.NONE].
         * [fromHv9]: HVAC 9 bit 0 yükselişi; ancak düşüşünden ([hidden]) sonra gelebildiği için her zaman yeni açılıştır ve
         * [CLOSING_MS] süzgecinden geçmez. Yavaş `hvacshow` kenarı ise kapatmanın kendi animasyonuna denk gelebilir, süzülür.
         */
        fun shown(t: Long, showPref: Boolean, canAct: Boolean, fromHv9: Boolean = false): Decision {
            if (visible) return Decision.NONE
            // Ayrı bir tuş basışı (kavga değil): kapatma sınırı yeniden başlar. AUTO değerleri sürekli değiştirse de sınır dolmaz.
            if (t - lastCloseAt > RESHOW_MS) closes = 0
            visible = true
            lastShowAt = t
            if (t - explicitAt <= EXPLICIT_GRACE_MS) explicitShown = true
            return when {
                showPref -> Decision.KEEP_PREF_SHOW
                explicitActive(t) -> Decision.KEEP_EXPLICIT_OPEN
                inWindow(t) -> decideClose(t, showPref, canAct, settleSecondEdge = !fromHv9)
                else -> {
                    if (pendingShowAt == never) pendingShowAt = t
                    Decision.PENDING
                }
            }
        }

        /**
         * Panel kapandı. Bilerek açılan panelin koruması burada biter: kullanıcı onu 3 sn içinde kapatsa bile sonraki tuş
         * açılışı kapatılır. Kaydırma yapıldı ama panel henüz görünmediyse süre ([EXPLICIT_GRACE_MS]) korunur.
         */
        fun hidden(t: Long) {
            visible = false
            pendingShowAt = never
            if (explicitShown) explicitAt = never
            explicitShown = false
        }

        /** Kapatmadan sonra bakış: panel hâlâ/yeniden açıksa ve pencere sürüyorsa yeniden kapat. */
        fun recheck(t: Long, visibleNow: Boolean, showPref: Boolean, canAct: Boolean): Decision {
            if (!visibleNow || !inWindow(t)) return Decision.NONE
            visible = true
            if (showPref) return Decision.KEEP_PREF_SHOW
            if (explicitActive(t)) return Decision.KEEP_EXPLICIT_OPEN
            return decideClose(t, showPref, canAct, settleSecondEdge = false)
        }

        private fun decideClose(t: Long, showPref: Boolean, canAct: Boolean, settleSecondEdge: Boolean = true): Decision = when {
            showPref -> Decision.KEEP_PREF_SHOW
            explicitActive(t) -> Decision.KEEP_EXPLICIT_OPEN
            settleSecondEdge && t - lastCloseAt < CLOSING_MS -> Decision.KEEP_CLOSING
            !canAct -> Decision.UNCLOSABLE
            closes >= MAX_CLOSES -> Decision.LIMIT
            else -> {
                closes++
                lastCloseAt = t
                pendingShowAt = never
                Decision.CLOSE
            }
        }
    }

    /**
     * İzlenen HVAC kimlikleri: tuşa basışla değişen ve paneli açtıran klima durumu. Arka cam ısıtma (80) paneli açmaz,
     * izlenmez. Panel görünürlüğü ayrıca [HvacID.ID_AC_DISPLAY_ACTIVE] (9) ile izlenir.
     */
    val CONTROL_IDS = intArrayOf(
        HvacID.ID_AC_STATE,
        HvacID.ID_AC_DUAL_MODE,
        HvacID.ID_AC_AUTO_STATE,
        HvacID.ID_AC_MODE_ADJUST,
        HvacID.ID_AC_CIRCULATION_MODE,
        HvacID.ID_AC_TEMPERATURE_DRIVER,
        HvacID.ID_AC_BLOW_SPEED_LEVEL,
        HvacID.ID_AC_TEMPERATURE_COPILOT,
        HvacID.ID_AC_WORKING,
        HvacID.ID_AC_MAX_FRONT_DEFOREST,
        HvacID.ID_AC_FRONT_GLASS_HEAT,
    )

    /** Abonelikte istenen tüm kimlikler: kontrol değerleri + HVAC 9. */
    val SUBSCRIBE_IDS: IntArray = CONTROL_IDS + HvacID.ID_AC_DISPLAY_ACTIVE

    /**
     * Değer gerçekten değişti mi: ilk görülen değer taban çizgisidir (abonelikte gelen ilk bildirim tuşa basış değildir),
     * aynı değerin yinelenen bildirimi değişiklik değildir.
     */
    fun isChange(previous: IntArray?, now: IntArray): Boolean = previous != null && !previous.contentEquals(now)
}

/**
 * Klima tuşlarına kilit (olay güdümlü; boştayken yoklama yok). Uygulama süreci yaşadığı sürece ([start], ana ekran
 * etkinliği kurulurken) çalışır; önde Google Haritalar gibi başka bir uygulama varken de.
 *
 * Sinyaller (yeni izin gerekmez):
 *  - VDBus HVAC aboneliği ([ClimatePanelPolicy.SUBSCRIBE_IDS]): klima değerlerinin değişimi ve HVAC 9 bit 0 kenarı (panel
 *    ECU'nun 9=1'iyle açılır; aynı bildirimi OEM de dinler, o yüzden en erken sinyal budur).
 * - `Settings.System` görünürlük anahtarları ([ClimatePanelBits.SHOW_KEYS]) için ContentObserver: OEM klima uygulaması panel açılınca
 *    "true" yazar; anında gelir ve VDBus aboneliği düşmüşse de çalışır.
 *  - Bizim dock yazmamız ([arm]): yazmadan önce değişiklik olarak sayılır.
 * Karar [ClimatePanelPolicy.Lock]'ta; her karar `ClimatePanel` etiketiyle tek satır günlüğe yazılır (yalnızca sayı/kimlik).
 * Tüm durum tek bir arka iş parçacığında değişir.
 */
internal object ClimatePanelGuard {
    private const val TAG = "ClimatePanel"
    private val lock = ClimatePanelPolicy.Lock()
    @Volatile private var app: Context? = null
    @Volatile private var worker: Handler? = null
    private val values = HashMap<Int, IntArray>()
    private var hv9: Int? = null
    private var settingsShown = false
    private var subscribed = false

    /** Abonelik HVAC 9'u bildirim olarak getirdi mi; getirmiyorsa taze okuma önbelleğe yazılmaz (kimse sıfırlamaz). */
    private var hv9Live = false

    private fun now() = SystemClock.elapsedRealtime()

    private val notify = object : IVDBusNotify.Stub() {
        override fun onVDBusNotify(event: VDEvent?) {
            if (event == null || event.id != VDEventCarInfo.MODULE_HVAC) return
            val payload = event.payload ?: return
            val id = payload.getInt(Constants.CMD_ID, -1)
            val v = payload.getIntArray(Constants.VALUE) ?: return
            if (v.isEmpty()) return
            worker?.post { onHvac(id, v) }
        }
    }

    private val connectListener = IServiceConnectListener { state -> if (state == 1) worker?.post { subscribe(force = true) } }

    /** Ana ekran kurulurken; birden çok çağrı zararsız. */
    @Synchronized
    fun start(context: Context) {
        if (worker != null) return
        val ctx = context.applicationContext
        app = ctx
        val thread = HandlerThread("ClimatePanelLock").apply { start() }
        val h = Handler(thread.looper)
        worker = h
        val observer = object : ContentObserver(h) {
            override fun onChange(selfChange: Boolean) = onSettings()
        }
        try {
            ClimatePanelBits.SHOW_KEYS.forEach { ctx.contentResolver.registerContentObserver(Settings.System.getUriFor(it), false, observer) }
        } catch (e: RuntimeException) {
            Log.w(TAG, "lock observer failed: ${e.javaClass.simpleName}")
        }
        h.post {
            settingsShown = ClimatePanel.isShowing(ctx)
            runCatching { CarInfoProxy.getInstance().regServiceConnectListener(connectListener) }
            if (!runCatching { CarInfoProxy.getInstance().isServiceConnnected }.getOrDefault(false)) {
                runCatching { CarInfoProxy.getInstance().init(ctx) }
            }
            subscribe(force = false)
        }
    }

    /** Ana ekrana dönüşte: araç uykusunda düşmüş olabilecek abonelik tazelenir. */
    fun refresh() {
        worker?.post { subscribe(force = true) }
    }

    private fun subscribe(force: Boolean) {
        val proxy = CarInfoProxy.getInstance()
        if (!runCatching { proxy.isServiceConnnected }.getOrDefault(false)) return
        if (subscribed && !force) return
        try {
            if (subscribed) proxy.unRegCallBack(VDEventCarInfo.MODULE_HVAC, notify)
            proxy.regCallBack(VDEventCarInfo.MODULE_HVAC, notify, ClimatePanelPolicy.SUBSCRIBE_IDS)
            if (!subscribed) Log.i(TAG, "lock subscribed ids=${ClimatePanelPolicy.SUBSCRIBE_IDS.size}")
            subscribed = true
            rebaseline()
        } catch (t: Throwable) {
            Log.w(TAG, "lock subscribe failed: ${t.javaClass.simpleName}")
        }
    }

    /**
     * (Yeniden) abonelikte taban çizgisi bugünkü değerlerdir: araç uykusundan kalan eski değerler ilk bildirimde tuşa basış
     * sayılmasın. Okunamayan kimlik silinir; ona gelen ilk bildirim taban olur.
     */
    private fun rebaseline() {
        ClimatePanelPolicy.CONTROL_IDS.forEach { id ->
            val v = CarSignals.read(VDEventCarInfo.MODULE_HVAC, id)
            if (v != null && v.isNotEmpty()) values[id] = v else values.remove(id)
        }
    }

    /** Bilerek açıldı (alt çubukta yukarı kaydırma): panel kapanana kadar korunur. */
    fun noteExplicitOpen() {
        val t = now()
        val h = worker ?: return
        h.post {
            lock.explicitOpen(t, visible())
            Log.i(TAG, "lock explicit-open visible=${visible()}")
        }
    }

    /** Gizle ayarında alt çubuktan yazmadan hemen ÖNCE: dock yazması değişiklik sayılır. */
    fun arm(context: Context, what: String) {
        start(context)
        val t = now()
        Log.i(TAG, "write what=$what mode=hide ids=dock lock=change")
        worker?.post { onChange(t, "dock") }
    }

    private fun visible(): Boolean = settingsShown || ClimatePanelBits.frontRow(hv9)

    private fun showPref(): Boolean = app?.let { runCatching { ClimatePanelPrefs.showOnChange(it) }.getOrDefault(false) } ?: false

    private fun onHvac(id: Int, v: IntArray) {
        if (id == HvacID.ID_AC_DISPLAY_ACTIVE) {
            hv9Live = true
            val wasFront = ClimatePanelBits.frontRow(hv9)
            hv9 = if (v.size == 1) v[0] else v.fold(0) { acc, b -> (acc shl 8) or (b and 0xFF) }
            val front = ClimatePanelBits.frontRow(hv9)
            // HVAC 9 bit 0 düşüşü panelin kapandığıdır; `hvacshow` 800 ms'lik animasyon boyunca "true" kalsa da. Böylece
            // kapatmadan hemen sonraki yeniden açılış (bit 0 yeniden yükselir) yeni açılış olarak hemen kapatılır.
            if (front && !wasFront) onShown("hv9") else if (wasFront && !front) lock.hidden(now())
            return
        }
        if (id !in ClimatePanelPolicy.CONTROL_IDS) return
        val previous = values[id]
        values[id] = v
        if (ClimatePanelPolicy.isChange(previous, v)) onChange(now(), "hvac$id")
    }

    private fun onSettings() {
        val ctx = app ?: return
        val wasVisible = visible()
        val was = settingsShown
        settingsShown = ClimatePanel.isShowing(ctx)
        if (settingsShown && !was) onShown("settings") else if (wasVisible && !visible()) lock.hidden(now())
    }

    private fun onChange(t: Long, src: String) {
        val d = lock.change(t, visible(), showPref(), ClimatePanelBits.closeCanAct(hv9))
        if (d != ClimatePanelPolicy.Decision.NONE) act(d, "change", src, t)
    }

    private fun onShown(src: String) {
        val t = now()
        val d = lock.shown(t, showPref(), ClimatePanelBits.closeCanAct(hv9), fromHv9 = src == "hv9")
        if (d != ClimatePanelPolicy.Decision.NONE) act(d, "show", src, t)
    }

    /**
     * Kapatmadan sonra taze okuma (bildirim gecikmiş ya da kaçmış olabilir). Okunan durum bildirimlerle aynı kenar
     * mantığından geçer: kapanış kaçmışsa [ClimatePanelPolicy.Lock.hidden] yine çağrılır. HVAC 9 bildirimi hiç gelmiyorsa
     * taze okuma yalnızca bu bakışın kararında kullanılır, önbelleğe yazılmaz (yoksa kimse 0'a döndürmez).
     */
    private fun recheck() {
        val t = now()
        val ctx = app ?: return
        val wasVisible = visible()
        val fresh = ClimatePanel.readHv9()
        if (fresh != null && hv9Live) hv9 = fresh
        settingsShown = ClimatePanel.isShowing(ctx)
        val nowVisible = visible()
        if (wasVisible && !nowVisible) lock.hidden(t)
        val showing = if (fresh != null) ClimatePanelBits.frontRow(fresh) else nowVisible
        if (!wasVisible && nowVisible) {
            onShown("read")
            return
        }
        val d = lock.recheck(t, showing, showPref(), ClimatePanelBits.closeCanAct(fresh ?: hv9))
        if (d != ClimatePanelPolicy.Decision.NONE) act(d, "recheck", "read", t)
    }

    private fun act(d: ClimatePanelPolicy.Decision, on: String, src: String, t: Long) {
        val ctx = app ?: return
        val outcome = if (d == ClimatePanelPolicy.Decision.CLOSE) {
            worker?.postDelayed({ recheck() }, ClimatePanelPolicy.RECHECK_MS)
            ClimatePanel.close(ctx, hv9)
        } else {
            "-"
        }
        Log.i(TAG, "lock decision=${d.name.lowercase()} on=$on src=$src n=${lock.closes} dtChange=${lock.sinceChange(t)} hv9=${ClimatePanelBits.describe(hv9)} outcome=$outcome")
    }
}
