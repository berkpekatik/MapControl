package com.mapcontrol.launcher

/**
 * Yolculuk kutucuğunun "Toplam" bölümü (saf mantık, JVM'de sınanır): hangi satırların gösterileceği ve sürüş süresi seçimi.
 * Eksik sinyaller (eski yazılımda 150-153 olmayabilir) satırdan düşer; hiçbiri yoksa bölüm boştur.
 */
internal object TripTotals {
    enum class Kind { ODOMETER, FUEL, TIME }

    /**
     * Sürüş süresi: bu sürüş (151); yoksa sıfırlamadan beri (152); yoksa ID_DRIVE_TIME (29) / GRAND_TIME_AFTER_RUNNING (50);
     * hiçbiri sıfırdan büyük değilse kendi biriktirdiğimiz motor-açık süresi ([own]). Araçta 151 "0 dk" okundu (2026-11-03): sıfır
     * "veri yok" sayılır, yoksa dolu olan sonraki sinyali gölgeler. Hiçbiri yoksa null (satır düşer; "0 dk" yalan söylerdi).
     */
    fun drivingMinutes(run: Int?, reset: Int?, alt: Int? = null, own: Int? = null): Int? =
        listOf(run, reset, alt, own).firstOrNull { it != null && it > 0 }

    /** Motor-açık süresini biriktirir: tur arası [deltaMs] yalnızca motor açıkken ve makul aralıktaysa (uyku/duraklama sıçraması sayılmaz) eklenir. */
    fun accumulateMs(totalMs: Long, deltaMs: Long, engineOn: Boolean, maxStepMs: Long = 10_000L): Long =
        if (engineOn && deltaMs in 1..maxStepMs) totalMs + deltaMs else totalMs

    /** Gösterilecek satırlar sırasıyla: toplam km ([Int]), toplam yakıt L ([Double]), sürüş dakikası ([Int]). */
    fun rows(totalKm: Int?, fuelL: Double?, minutes: Int?): List<Pair<Kind, Any>> = listOfNotNull(
        totalKm?.let { Kind.ODOMETER to it },
        fuelL?.let { Kind.FUEL to it },
        minutes?.let { Kind.TIME to it },
    )
}
