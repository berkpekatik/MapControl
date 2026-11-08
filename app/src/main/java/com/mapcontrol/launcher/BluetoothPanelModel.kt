package com.mapcontrol.launcher

/** Eşleşmiş bir cihazın türü (simge için): telefon, kulaklık/hoparlör, diğer. */
internal enum class BtKind { PHONE, AUDIO, OTHER }

/**
 * Panelde gösterilen eşleşmiş cihaz: [phone] arama profili (HFP), [audio] müzik profili (A2DP) şu an bağlı mı.
 * [name] boşsa satırda adres görünür ([BtListShaper.label]).
 */
internal data class BtDevice(
    val address: String,
    val name: String?,
    val kind: BtKind = BtKind.OTHER,
    val phone: Boolean = false,
    val audio: Boolean = false,
) {
    val connected: Boolean get() = phone || audio
}

/** Tek bir profil vekili üzerinden yapılan gizli `connect`/`disconnect` çağrısının sonucu. */
internal enum class BtCall { OK, DENIED, UNSUPPORTED, FAILED }

/** Bağlan/kes isteğinin panelde gösterilen sonucu. */
internal enum class BtOutcome { STARTED, NOT_PERMITTED, FAILED }

/** Başlıktaki anahtar: çalışır, yalnızca durumu gösterir (izin yok), ya da bağdaştırıcı yok. */
internal enum class BtSwitch { ACTIVE, READ_ONLY, ABSENT }

/** Panelin Bluetooth'a dokunan tek yüzü (cihazda [AndroidBluetoothBackend]). */
internal interface BluetoothBackend {
    val present: Boolean
    fun isEnabled(): Boolean
    /** Bağdaştırıcıyı açıp kapatma izni var mı (yoksa anahtar yalnızca durumu gösterir). */
    fun canToggle(): Boolean
    /** Eşleşmiş cihazlar okunabiliyor mu (izin). */
    fun canRead(): Boolean
    /** Arka planda açar/kapatır, bitince ana iş parçacığında [done]. */
    fun setEnabled(on: Boolean, done: () -> Unit)
    /** Eşleşmiş cihazlar ve bağlı profilleri. */
    fun devices(): List<BtDevice>
    /** Profil vekillerini açar (panel açıkken); bir vekil gelince ya da gidince [onChange] ana iş parçacığında. */
    fun open(onChange: () -> Unit)
    fun close()
    /** Arka planda çağrılır. */
    fun connect(address: String): BtOutcome
    fun disconnect(address: String): BtOutcome
}

/** Saf liste ve karar mantığı (JVM'de sınanır). */
internal object BtListShaper {
    /** Android profil kimlikleri (BluetoothProfile sabitleri; ikisi gizli: A2DP_SINK, HEADSET_CLIENT). */
    const val HEADSET = 1
    const val A2DP = 2
    const val A2DP_SINK = 11
    const val HEADSET_CLIENT = 16

    /** Araç ünitesinde telefon HFP istemcisi + A2DP alıcısıyla bağlanır; telefon/emülatörde kaynak profiller. Bu sırayla denenir. */
    val PROFILES = listOf(HEADSET_CLIENT, A2DP_SINK, HEADSET, A2DP)

    /** Sürüşte de aynı anda görünen en çok eşleşmiş cihaz satırı (fazlası kayar). */
    const val MAX_VISIBLE = 5

    fun isPhoneProfile(profile: Int) = profile == HEADSET_CLIENT || profile == HEADSET
    fun isAudioProfile(profile: Int) = profile == A2DP_SINK || profile == A2DP

    /** BluetoothClass.getMajorDeviceClass(): 0x0200 telefon, 0x0400 ses/görüntü. */
    fun kind(majorClass: Int): BtKind = when (majorClass) {
        0x0200 -> BtKind.PHONE
        0x0400 -> BtKind.AUDIO
        else -> BtKind.OTHER
    }

    fun label(d: BtDevice): String = d.name?.trim()?.takeIf { it.isNotEmpty() } ?: d.address

    /**
     * Bağlılar (telefon önce, sonra ada göre) ve bağlı olmayan eşleşmişler (ada göre); aynı adres bir kez
     * (bağlı profil bayrakları birleşir).
     */
    fun shape(devices: List<BtDevice>): Pair<List<BtDevice>, List<BtDevice>> {
        val byAddress = LinkedHashMap<String, BtDevice>()
        for (d in devices) {
            val key = d.address.uppercase()
            val prev = byAddress[key]
            byAddress[key] = if (prev == null) d else prev.copy(
                name = prev.name ?: d.name,
                kind = if (prev.kind == BtKind.OTHER) d.kind else prev.kind,
                phone = prev.phone || d.phone,
                audio = prev.audio || d.audio,
            )
        }
        val byName = compareBy<BtDevice> { label(it).lowercase() }
        val (on, off) = byAddress.values.partition { it.connected }
        return on.sortedWith(compareByDescending<BtDevice> { it.phone }.then(byName)) to off.sortedWith(byName)
    }

    /** Profil çağrılarından tek sonuç: biri başladıysa STARTED; hiçbiri olmadı ve biri izinle reddedildiyse NOT_PERMITTED. */
    fun outcome(calls: List<BtCall>): BtOutcome = when {
        calls.any { it == BtCall.OK } -> BtOutcome.STARTED
        calls.any { it == BtCall.DENIED } -> BtOutcome.NOT_PERMITTED
        else -> BtOutcome.FAILED
    }

    fun switchMode(present: Boolean, canToggle: Boolean): BtSwitch = when {
        !present -> BtSwitch.ABSENT
        canToggle -> BtSwitch.ACTIVE
        else -> BtSwitch.READ_ONLY
    }
}
