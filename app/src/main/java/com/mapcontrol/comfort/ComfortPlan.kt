package com.mapcontrol.comfort

/**
 * Konfor yazma testi (tavan perdesi, buğu giderme, direksiyon ısıtma) için SAF kod: VDBus'a, Android'e dokunmaz,
 * JVM'de sınanır. Araca yazan taraf her yazmadan önce [validate]
 * ile (modül, kimlik, değer) üçlüsünü SERT beyaz listeden geçirir. Liste dışı her şey [WriteNotAllowed] fırlatır.
 * Değerler VehicleSunroofController / ClimateAutomationEngine çağrılarıyla aynı.
 */
object ComfortPlan {

    /** VDEventCarInfo.MODULE_CAR_SETTING. */
    const val MODULE_CAR_SETTING = 327681

    /** VDEventCarInfo.MODULE_HVAC. */
    const val MODULE_HVAC = 327690

    /** VDEventCarInfo.MODULE_READONLY_INFO. */
    const val MODULE_READONLY_INFO = 327684

    /** VDEventCarInfo.MODULE_VR. */
    const val MODULE_VR = 327696

    /** CarSettingID.ID_CAR_SUNSHADE. Okuma (ölçüldü): 1 kapalı, 2 açılıyor, 3 yarıda durdu, 4 kapanıyor. */
    const val ID_SUNSHADE = 10

    /** CarSettingID.ID_CAR_REAR_WINDOW_HEAT. Yalnızca OKUNUR: hiçbir OEM uygulaması 159'a yazmıyor (arka buz çözme HVAC 80). */
    const val ID_REAR_WINDOW_HEAT = 159

    /**
     * CarSettingID.ID_CAR_STEER_WHEEL_HEAT. Anahtar gibi: OEM açarken de kapatırken de 160 ← 1 yazar, 0 hiç yazmaz
     * (SystemUI HvacModel.onCarSteerWheelHeatClick; sesli komut durumu okuyup farklıysa 1 gönderir). Okuma: 1 açık, 0 kapalı.
     */
    const val ID_STEER_WHEEL_HEAT = 160

    /** Direksiyon ısıtma anahtar değeri (aç ve kapat aynı). */
    const val STEER_WHEEL_TOGGLE = 1

    /** HvacID.ID_AC_FRONT_GLASS_HEAT. OEM: 1 aç, 2 kapat (HvacModel.onFrontHeatingClick); 0 hiç yazmaz. */
    const val ID_FRONT_GLASS_HEAT = 65

    /** HvacID.ID_AC_MAX_FRONT_DEFOREST. OEM: 1 aç, 2 kapat (HvacModel.onMaxDefrostingClick); 0 hiç yazmaz. */
    const val ID_MAX_FRONT_DEFOREST = 24

    /** HVAC 65 ve 24 için OEM kapatma değeri. */
    const val FRONT_DEFROST_OFF = 2

    /** HvacID.ID_AC_REAR_DEFROST. */
    const val ID_REAR_DEFROST = 80

    /** VrID.ID_VR_SUN_SHADE (1 aç, 2 kapat; araçta ölçüldü). Hareket sırasında komut yönü ters çevirir, durdurmaz. */
    const val ID_VR_SUN_SHADE = 124

    /** VR 124 durdurma ADAYI (araçta denenecek); yalnızca `shade_stop_vr` ile. */
    val VR_SHADE_STOP_CANDIDATES: Set<Int> = setOf(3)

    /** VrID.ID_VR_TRUNK: bagaj kapağı yazma ADAYI (DOĞRULANMADI); perde VR 124 örneğiyle 1 aç, 2 kapat denenecek. */
    const val ID_VR_TRUNK = 121

    /** CarSettingID.ID_CAR_REAR_DOOR_CONTROL. Okuma (ölçüldü): 2 durdu, 3 açılıyor, 4 kapanıyor. Yazma yedek ADAY: 3 aç, 4 kapat, 2 durdur. */
    const val ID_REAR_DOOR_CONTROL = 92

    /** ReadOnlyID.ID_PLG_POSITION: bagaj kapağı açıklığı (ölçüldü): 0 kapalı .. ~81 tam açık. */
    const val ID_PLG_POSITION = 98

    /** ReadOnlyID.ID_TRUNK_STATE (ölçüldü): 0 kapalı, 1 açık. */
    const val ID_TRUNK_STATE = 19

    const val TRUNK_OPEN_VR = 1
    const val TRUNK_CLOSE_VR = 2
    const val TRUNK_OPEN_CS = 3
    const val TRUNK_CLOSE_CS = 4
    const val TRUNK_STOP_CS = 2

    /** Arka buğu (Hvac 80) KAPATMA adayı: 0 ve tekrar 1 kapatmadı (araçta ölçüldü); 2 denenecek. */
    const val DEFROST_OFF_CANDIDATE = 2

    const val SHADE_OPEN = 2
    const val SHADE_CLOSE = 1

    /** Perde durdurma ADAYLARI (hangisi çalışır bilinmiyor); yalnızca tek tek, açık komutla denenir. */
    val SHADE_STOP_CANDIDATES: Set<Int> = setOf(3, 6, 0)

    /** Araca yazılan tek birim. */
    data class Write(val module: Int, val id: Int, val value: Int) {
        override fun toString() = "module=$module id=$id value=$value"
    }

    /** Okunan sinyal (günlük adı + modül + kimlik). */
    data class Signal(val name: String, val module: Int, val id: Int)

    class WriteNotAllowed(val write: Write) : IllegalArgumentException("Konfor yazması izinli değil: $write")

    /** SERT beyaz liste: bunun dışındaki hiçbir (modül, kimlik, değer) araca gitmez. */
    val ALLOWED: Set<Write> = buildSet {
        add(Write(MODULE_CAR_SETTING, ID_SUNSHADE, SHADE_OPEN))
        add(Write(MODULE_CAR_SETTING, ID_SUNSHADE, SHADE_CLOSE))
        for (s in SHADE_STOP_CANDIDATES) add(Write(MODULE_CAR_SETTING, ID_SUNSHADE, s))
        add(Write(MODULE_VR, ID_VR_SUN_SHADE, 1))
        add(Write(MODULE_VR, ID_VR_SUN_SHADE, 2))
        for (s in VR_SHADE_STOP_CANDIDATES) add(Write(MODULE_VR, ID_VR_SUN_SHADE, s))
        add(Write(MODULE_VR, ID_VR_TRUNK, TRUNK_OPEN_VR))
        add(Write(MODULE_VR, ID_VR_TRUNK, TRUNK_CLOSE_VR))
        add(Write(MODULE_CAR_SETTING, ID_REAR_DOOR_CONTROL, TRUNK_OPEN_CS))
        add(Write(MODULE_CAR_SETTING, ID_REAR_DOOR_CONTROL, TRUNK_CLOSE_CS))
        add(Write(MODULE_CAR_SETTING, ID_REAR_DOOR_CONTROL, TRUNK_STOP_CS))
        // Ön cam 65 ve maks. buğu 24: yalnızca OEM değerleri (1 aç, 2 kapat); 0 yazılmaz.
        for (v in listOf(1, FRONT_DEFROST_OFF)) {
            add(Write(MODULE_HVAC, ID_FRONT_GLASS_HEAT, v))
            add(Write(MODULE_HVAC, ID_MAX_FRONT_DEFOREST, v))
        }
        // Arka buğu 80: kapatma değeri belirsiz; 0, 1 ve aday 2.
        for (v in listOf(0, 1, DEFROST_OFF_CANDIDATE)) add(Write(MODULE_HVAC, ID_REAR_DEFROST, v))
        add(Write(MODULE_CAR_SETTING, ID_STEER_WHEEL_HEAT, STEER_WHEEL_TOGGLE))
    }

    /** Listede değilse [WriteNotAllowed] fırlatır; değilse aynı yazmayı döndürür. */
    fun validate(w: Write): Write {
        if (w !in ALLOWED) throw WriteNotAllowed(w)
        return w
    }

    val SUNSHADE = Signal("sunshade10", MODULE_CAR_SETTING, ID_SUNSHADE)
    val FRONT_GLASS_HEAT = Signal("hvac65_front_glass_heat", MODULE_HVAC, ID_FRONT_GLASS_HEAT)
    val MAX_FRONT_DEFOREST = Signal("hvac24_max_front_deforest", MODULE_HVAC, ID_MAX_FRONT_DEFOREST)
    val REAR_DEFROST = Signal("hvac80_rear_defrost", MODULE_HVAC, ID_REAR_DEFROST)
    val REAR_WINDOW_HEAT = Signal("cs159_rear_window_heat", MODULE_CAR_SETTING, ID_REAR_WINDOW_HEAT)
    val STEER_WHEEL_HEAT = Signal("cs160_steer_wheel_heat", MODULE_CAR_SETTING, ID_STEER_WHEEL_HEAT)
    val PLG_POSITION = Signal("ro98_plg_position", MODULE_READONLY_INFO, ID_PLG_POSITION)
    val TRUNK_STATE = Signal("ro19_trunk_state", MODULE_READONLY_INFO, ID_TRUNK_STATE)
    val REAR_DOOR_CONTROL = Signal("cs92_rear_door_control", MODULE_CAR_SETTING, ID_REAR_DOOR_CONTROL)

    /** Bagaj komutlarının geri okuduğu sinyaller (`trunk_info` bunları okur). */
    val TRUNK_SIGNALS: List<Signal> = listOf(PLG_POSITION, TRUNK_STATE, REAR_DOOR_CONTROL)

    /** `info` komutunun okuduğu tüm sinyaller. */
    val ALL_SIGNALS: List<Signal> =
        listOf(SUNSHADE, FRONT_GLASS_HEAT, MAX_FRONT_DEFOREST, REAR_DEFROST, REAR_WINDOW_HEAT, STEER_WHEEL_HEAT)

    /**
     * `hidden_info` / `hidden_watch` için SALT-OKUR sinyaller (gizli/belgelenmemiş kimlikler). Sürücü fiziksel tuşlara basarken
     * hangi kimliğin değiştiği görülsün diye izlenir; HİÇBİR yazma yok. Ad: modül öneki + kimlik + kısa Türkçe ad (ChangeTracker anahtarı benzersiz).
     */
    val HIDDEN_SIGNALS: List<Signal> = listOf(
        Signal("cs0_kilit_geri_bildirimi", MODULE_CAR_SETTING, 0),
        Signal("cs1_kilitte_ayna_katlama", MODULE_CAR_SETTING, 1),
        Signal("cs22_kabin_havalandırma", MODULE_CAR_SETTING, 22),
        Signal("cs23_kabin_temizleme", MODULE_CAR_SETTING, 23),
        Signal("cs41_karşılama_ışığı", MODULE_CAR_SETTING, 41),
        Signal("cs42_BT_pasif_giriş", MODULE_CAR_SETTING, 42),
        Signal("cs45_kablosuz_şarj", MODULE_CAR_SETTING, 45),
        Signal("cs47_telefon_unutma_uyarısı", MODULE_CAR_SETTING, 47),
        Signal("cs76_karşılama_kokusu", MODULE_CAR_SETTING, 76),
        Signal("cs77_hava_temizleme_oto", MODULE_CAR_SETTING, 77),
        Signal("cs78_ferahlatma", MODULE_CAR_SETTING, 78),
        Signal("cs117_ambiyans_müzik_ritmi", MODULE_CAR_SETTING, 117),
        Signal("cs161_yağmur_sensörü_hassasiyeti", MODULE_CAR_SETTING, 161),
        Signal("cs177_sürüş_modu_hafızası", MODULE_CAR_SETTING, 177),
        Signal("cs204_ayna_katlı_mı", MODULE_CAR_SETTING, 204),
        Signal("cs205_ADAS_ses_stili", MODULE_CAR_SETTING, 205),
        Signal("cs240_iç_alarm", MODULE_CAR_SETTING, 240),
        Signal("hvac69_PTC_ısıtıcı", MODULE_HVAC, 69),
        Signal("hvac70_akıllı_klima", MODULE_HVAC, 70),
        Signal("hvac97_oto_buğu_T1E24", MODULE_HVAC, 97),
        Signal("ro14_ACC", MODULE_READONLY_INFO, 14),
        Signal("ro26_vites", MODULE_READONLY_INFO, 26),
        Signal("ro74_motor", MODULE_READONLY_INFO, 74),
        // hidden_watch için ek salt-okur sinyaller (kapılar/bagaj, kablosuz şarj, ambiyans, kilit, klima durumları).
        Signal("ro15_kapı_1", MODULE_READONLY_INFO, 15),
        Signal("ro16_kapı_2", MODULE_READONLY_INFO, 16),
        Signal("ro17_kapı_3", MODULE_READONLY_INFO, 17),
        Signal("ro18_kapı_4", MODULE_READONLY_INFO, 18),
        Signal("ro19_bagaj_durumu", MODULE_READONLY_INFO, 19),
        Signal("ro47_bilinmeyen", MODULE_READONLY_INFO, 47),
        Signal("cs7_ambiyans", MODULE_CAR_SETTING, 7),
        Signal("cs44_kablosuz_şarj_durumu", MODULE_CAR_SETTING, 44),
        Signal("cs92_bagaj_kapağı", MODULE_CAR_SETTING, 92),
        Signal("cs160_direksiyon_ısıtma", MODULE_CAR_SETTING, 160),
        Signal("cs169_merkezi_kilit_durumu", MODULE_CAR_SETTING, 169),
        Signal("cs237_bilinmeyen", MODULE_CAR_SETTING, 237),
        Signal("hvac1_klima_1", MODULE_HVAC, 1),
        Signal("hvac4_klima_4", MODULE_HVAC, 4),
        Signal("hvac8_klima_8", MODULE_HVAC, 8),
        Signal("hvac9_klima_9", MODULE_HVAC, 9),
        Signal("hvac10_klima_10", MODULE_HVAC, 10),
        Signal("hvac12_fan_kademesi", MODULE_HVAC, 12),
        Signal("hvac13_klima_13", MODULE_HVAC, 13),
        Signal("hvac18_klima_gücü", MODULE_HVAC, 18),
        Signal("hvac24_maks_ön_buğu", MODULE_HVAC, 24),
        Signal("hvac65_ön_cam_ısıtma", MODULE_HVAC, 65),
        Signal("hvac80_arka_buğu", MODULE_HVAC, 80),
    )

    sealed class Step {
        data class Send(val write: Write) : Step()
        data class Wait(val ms: Long) : Step()

        /**
         * Anahtar yazması: sender önce [signal]'i okur; ilk değer [target] ise hiçbir şey göndermez, değilse [write] gider.
         * Okunamazsa gönderilmez (anahtar yanlış yöne çevrilmesin).
         */
        data class Toggle(val write: Write, val signal: Signal, val target: Int) : Step()
    }

    enum class ToggleAction { SEND, SKIP, REFUSE }

    /**
     * Anahtar kararı (saf): okunamadıysa REFUSE, zaten hedefteyse SKIP, değilse SEND.
     * OEM gibi yalnızca 1 "açık" sayılır (HvacModel.getCarSteerWheelHeat: == 1); 0, 2 vb. kapalıdır.
     */
    @JvmStatic
    fun toggleAction(current: Int?, target: Int): ToggleAction = when {
        current == null -> ToggleAction.REFUSE
        (current == 1) == (target == 1) -> ToggleAction.SKIP
        else -> ToggleAction.SEND
    }

    enum class Kind { INFO, TIMING, WRITE }

    /**
     * Çözülmüş komut. [steps] sırayla yürütülür (her [Step.Send] öncesi park kapısı sender'da denetlenir);
     * [watch] sinyalleri adımlar sırasında ve sonra [watchMs] boyunca [intervalMs] aralıkla okunup değişimleri günlüğe yazılır.
     */
    class Command(
        val name: String,
        val kind: Kind,
        val steps: List<Step> = emptyList(),
        val watch: List<Signal> = emptyList(),
        val watchMs: Long = 0,
        val intervalMs: Long = 250,
    )

    const val READBACK_MS = 3000L
    const val STOP_WATCH_MS = 6000L
    const val TRUNK_WATCH_MS = 10_000L
    const val TIMING_MS = 30_000L
    const val TIMING_INTERVAL_MS = 100L
    const val HIDDEN_WATCH_MS = 60_000L
    const val HIDDEN_WATCH_INTERVAL_MS = 300L
    const val HIDDEN_WATCH_MIN_S = 10
    const val HIDDEN_WATCH_MAX_S = 600
    const val HIDDEN_WATCH_DEFAULT_S = 60
    const val DEFAULT_AFTER_MS = 2000
    const val MAX_AFTER_MS = 10_000

    val COMMANDS: Set<String> = setOf(
        "info", "hidden_info", "hidden_watch", "shade_time", "shade_open", "shade_close", "shade_open_vr", "shade_close_vr",
        "shade_open_stop", "shade_stop", "shade_stop_vr", "defrost_front", "defrost_front_max", "defrost_rear",
        "wheel_heat",
        "trunk_info", "trunk_open_vr", "trunk_close_vr", "trunk_open_cs", "trunk_close_cs", "trunk_stop_cs", "trunk_open_stop_cs",
    )

    /** hidden_watch süresi: --ei watch_s (yoksa 60 sn), 10..600 sn aralığına sıkıştırılır. */
    @JvmStatic
    fun hiddenWatchMs(watchS: Int?): Long =
        (watchS ?: HIDDEN_WATCH_DEFAULT_S).coerceIn(HIDDEN_WATCH_MIN_S, HIDDEN_WATCH_MAX_S) * 1000L

    private fun send(module: Int, id: Int, value: Int) = Step.Send(validate(Write(module, id, value)))

    /**
     * `--es c <ad> [--ei v 0|1 (defrost_front/defrost_front_max: 0 ya da 2 = kapat → 2; defrost_rear: 0|1|2)] [--ei after_ms N (shade_open_stop, trunk_open_stop_cs)] [--ei stop 3|6|0] [--ei watch_s 10..600 (hidden_watch; varsayılan 60, aralık dışı sıkıştırılır)]` ekstralarını komuta çevirir.
     * Bilinmeyen ad, eksik/geçersiz v, geçersiz stop veya after_ms için [IllegalArgumentException].
     */
    fun parse(c: String?, v: Int? = null, afterMs: Int? = null, stop: Int? = null, watchS: Int? = null): Command {
        require(c != null && c in COMMANDS) { "geçersiz komut: $c" }
        fun onOff(): Int {
            require(v == 0 || v == 1) { "--ei v 0|1 gerekli (geldi: $v)" }
            return v!!
        }
        fun stopValue(): Int {
            val s = stop ?: 3
            require(s in SHADE_STOP_CANDIDATES) { "--ei stop yalnızca 3, 6 veya 0 olabilir (geldi: $s)" }
            return s
        }
        // Hvac 65 ve 24: v hedef durum; 1 → 1, 0 ya da 2 → OEM kapatma değeri 2 (araca 0 gitmez).
        fun frontDefrost(): Int {
            require(v == 0 || v == 1 || v == FRONT_DEFROST_OFF) { "--ei v 0|1|2 gerekli (geldi: $v)" }
            return if (v == 1) 1 else FRONT_DEFROST_OFF
        }
        // Yalnızca Hvac 80 için: 0/1 ya da kapatma adayı 2.
        fun onOffOrTwo(): Int {
            require(v == 0 || v == 1 || v == DEFROST_OFF_CANDIDATE) { "--ei v 0|1|2 gerekli (geldi: $v)" }
            return v!!
        }
        fun vrStopValue(): Int {
            val s = stop ?: 3
            require(s in VR_SHADE_STOP_CANDIDATES) { "--ei stop yalnızca 3 olabilir (geldi: $s)" }
            return s
        }
        val cs = MODULE_CAR_SETTING
        val hv = MODULE_HVAC
        return when (c) {
            "info" -> Command(c, Kind.INFO)
            // Salt-okur: gizli kimlikler bir kez okunur / 60 sn boyunca 300 ms aralıkla yalnızca değişimler yazılır.
            "hidden_info" -> Command(c, Kind.INFO, watch = HIDDEN_SIGNALS)
            "hidden_watch" -> Command(c, Kind.TIMING, watch = HIDDEN_SIGNALS, watchMs = hiddenWatchMs(watchS), intervalMs = HIDDEN_WATCH_INTERVAL_MS)
            "shade_time" -> Command(c, Kind.TIMING, watch = listOf(SUNSHADE), watchMs = TIMING_MS, intervalMs = TIMING_INTERVAL_MS)
            "shade_open" -> writeCmd(c, listOf(send(cs, ID_SUNSHADE, SHADE_OPEN)), listOf(SUNSHADE))
            "shade_close" -> writeCmd(c, listOf(send(cs, ID_SUNSHADE, SHADE_CLOSE)), listOf(SUNSHADE))
            "shade_open_vr" -> writeCmd(c, listOf(send(MODULE_VR, ID_VR_SUN_SHADE, 1)), listOf(SUNSHADE))
            "shade_close_vr" -> writeCmd(c, listOf(send(MODULE_VR, ID_VR_SUN_SHADE, 2)), listOf(SUNSHADE))
            "shade_stop" -> writeCmd(c, listOf(send(cs, ID_SUNSHADE, stopValue())), listOf(SUNSHADE), STOP_WATCH_MS)
            "shade_stop_vr" -> writeCmd(c, listOf(send(MODULE_VR, ID_VR_SUN_SHADE, vrStopValue())), listOf(SUNSHADE), STOP_WATCH_MS)
            "shade_open_stop" -> {
                val after = afterMs ?: DEFAULT_AFTER_MS
                require(after in 0..MAX_AFTER_MS) { "--ei after_ms 0..$MAX_AFTER_MS olmalı (geldi: $after)" }
                writeCmd(
                    c,
                    listOf(send(cs, ID_SUNSHADE, SHADE_OPEN), Step.Wait(after.toLong()), send(cs, ID_SUNSHADE, stopValue())),
                    listOf(SUNSHADE), STOP_WATCH_MS,
                )
            }
            "trunk_info" -> Command(c, Kind.INFO, watch = TRUNK_SIGNALS)
            "trunk_open_vr" -> trunkCmd(c, listOf(send(MODULE_VR, ID_VR_TRUNK, TRUNK_OPEN_VR)))
            "trunk_close_vr" -> trunkCmd(c, listOf(send(MODULE_VR, ID_VR_TRUNK, TRUNK_CLOSE_VR)))
            "trunk_open_cs" -> trunkCmd(c, listOf(send(cs, ID_REAR_DOOR_CONTROL, TRUNK_OPEN_CS)))
            "trunk_close_cs" -> trunkCmd(c, listOf(send(cs, ID_REAR_DOOR_CONTROL, TRUNK_CLOSE_CS)))
            "trunk_stop_cs" -> trunkCmd(c, listOf(send(cs, ID_REAR_DOOR_CONTROL, TRUNK_STOP_CS)))
            "trunk_open_stop_cs" -> {
                val after = afterMs ?: DEFAULT_AFTER_MS
                require(after in 0..MAX_AFTER_MS) { "--ei after_ms 0..$MAX_AFTER_MS olmalı (geldi: $after)" }
                trunkCmd(
                    c,
                    listOf(send(cs, ID_REAR_DOOR_CONTROL, TRUNK_OPEN_CS), Step.Wait(after.toLong()), send(cs, ID_REAR_DOOR_CONTROL, TRUNK_STOP_CS)),
                )
            }
            "defrost_front" -> writeCmd(c, listOf(send(hv, ID_FRONT_GLASS_HEAT, frontDefrost())), listOf(FRONT_GLASS_HEAT))
            "defrost_front_max" -> writeCmd(c, listOf(send(hv, ID_MAX_FRONT_DEFOREST, frontDefrost())), listOf(MAX_FRONT_DEFOREST))
            "defrost_rear" -> writeCmd(c, listOf(send(hv, ID_REAR_DEFROST, onOffOrTwo())), listOf(REAR_DEFROST))
            // v = hedef durum (1 açık, 0 kapalı); araca her iki durumda da yalnızca 160 ← 1 gider (OEM gibi).
            "wheel_heat" -> writeCmd(
                c,
                listOf(Step.Toggle(validate(Write(cs, ID_STEER_WHEEL_HEAT, STEER_WHEEL_TOGGLE)), STEER_WHEEL_HEAT, onOff())),
                listOf(STEER_WHEEL_HEAT),
            )
            else -> throw IllegalArgumentException("geçersiz komut: $c")
        }
    }

    /** Bagaj yazması: PLG 98, TRUNK 19 ve CS 92 geçişleri [TRUNK_WATCH_MS] boyunca izlenir. */
    private fun trunkCmd(name: String, steps: List<Step>) = Command(name, Kind.WRITE, steps, TRUNK_SIGNALS, TRUNK_WATCH_MS)

    private fun writeCmd(name: String, steps: List<Step>, watch: List<Signal>, watchMs: Long = READBACK_MS) =
        Command(name, Kind.WRITE, steps, watch, watchMs)

    /** Değişim izleyici (saf): aynı anahtarın değeri değiştiyse (ya da ilk kez görüldüyse) true. */
    class ChangeTracker {
        private val last = HashMap<String, String>()
        fun observe(key: String, value: String): Boolean {
            val prev = last.put(key, value)
            return prev != value
        }
    }
}
