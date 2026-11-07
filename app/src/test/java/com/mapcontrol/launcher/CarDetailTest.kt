package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Araç detayı verisi: bilinmeyen her şey null ("—"), lastikler psi, kapı/cam durumu ve sağlık özeti. */
class CarDetailTest {

    @Test
    fun nothingReadMeansAllUnknown() {
        val m = CarDetail.model(null, ExtraData(), TripData(), null)
        assertEquals(List(4) { null }, m.tiresPsi)
        assertEquals(List(4) { false }, m.tiresLow)
        assertEquals(List(4) { null }, m.doors)
        assertNull(m.trunk)
        assertNull(m.hood)
        assertNull(m.sunroof)
        assertEquals(List(4) { null }, m.windows)
        assertNull(m.serviceKm)
        assertNull(m.health)
        assertNull(m.pm25In)
        assertNull(m.outsideC)
        assertNull(CarDetail.openIndices(m.doors))
    }

    @Test
    fun outsideTemperatureComesFromTrip() {
        assertEquals(17.0, CarDetail.model(null, ExtraData(), TripData(outsideC = 17.0), null).outsideC!!, 1e-9)
    }

    @Test
    fun tiresInPsiPerWheelWithLowFlag() {
        val extra = ExtraData(connected = true, tires = TireReading(listOf(2.4, null, 1.6, 2.2)))
        val m = CarDetail.model(null, extra, TripData(), null)
        assertEquals(listOf(35, null, 23, 32), m.tiresPsi)
        assertEquals(listOf(false, false, true, false), m.tiresLow)
        // Tek değerli okumada tekerlek bilinmez: hepsi "—".
        assertEquals(List(4) { null }, CarDetail.psi(TireReading(listOf(2.4))))
    }

    @Test
    fun bodyStatesComeFromOpeningsAndExtraParts() {
        val o = Openings(doorFl = true, trunk = true, windows = listOf(BodyPart.WINDOW_RR))
        val extra = ExtraData(connected = true, parts = mapOf(
            BodyPart.HOOD to false, BodyPart.WINDOW_LF to false, BodyPart.WINDOW_RF to false, BodyPart.WINDOW_RR to true,
        ))
        val m = CarDetail.model(o, extra, TripData(), null)
        assertEquals(listOf<Boolean?>(true, false, false, false), m.doors)
        assertEquals(true, m.trunk)
        assertEquals(false, m.hood)
        assertEquals(false, m.sunroof)
        assertEquals(listOf<Boolean?>(false, false, null, true), m.windows) // arka sol okunamadı
        assertEquals(listOf(0), CarDetail.openIndices(m.doors))
        assertEquals(listOf(3), CarDetail.openIndices(m.windows))
        assertEquals(emptyList<Int>(), CarDetail.openIndices(listOf(false, null, false, false)))
        // Detaydaki araç görselinde renklenen camlar: yalnız açık olduğu bilinenler.
        assertEquals(listOf(BodyPart.WINDOW_RR), CarDetail.openWindows(m))
    }

    @Test
    fun healthSummaryAndService() {
        val calm = CarDetail.model(null, ExtraData(), TripData(batteryV = 14.1, coolantCode = CoolantStatus.NORMAL, serviceKm = 3978, serviceDays = 155, pm25In = 4), false)
        assertEquals(emptyList<HealthMath.Issue>(), calm.health)
        assertEquals(3978, calm.serviceKm)
        assertEquals(4, calm.pm25In)
        assertEquals(CoolantStatus.NORMAL, calm.coolantCode)
        val sick = CarDetail.model(null, ExtraData(), TripData(batteryV = 11.4, serviceKm = 800, serviceDays = 40), true)
        assertEquals(listOf(HealthMath.Issue.BATTERY_LOW, HealthMath.Issue.LOW_FUEL, HealthMath.Issue.SERVICE_SOON), sick.health)
    }

    @Test
    fun airGradeThresholds() {
        assertEquals(AirGrade.GOOD, AirGrade.of(35))
        assertEquals(AirGrade.MODERATE, AirGrade.of(36))
        assertEquals(AirGrade.MODERATE, AirGrade.of(75))
        assertEquals(AirGrade.POOR, AirGrade.of(76))
        // Sahnedeki hava notu yalnız "Kötü" derecede çıkar (uyarı rengi ile yazı aynı şeyi söyler).
        assertEquals(AirGrade.POOR, AirGrade.of(StageCorners.AIR_BAD_PM25))
        assertEquals(AirGrade.MODERATE, AirGrade.of(StageCorners.AIR_BAD_PM25 - 1))
    }
}
