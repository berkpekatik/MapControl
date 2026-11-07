package com.mapcontrol.launcher

import com.mapcontrol.launcher.ConsumptionSmoother.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private var clock = 0L
/** Her çağrı yeni bir okuma gibi (2,5 sn arayla) sayılır. */
private fun ConsumptionSmoother.u(l: Double?, kmh: Int?, moving: Boolean?): ConsumptionSmoother.Display {
    clock += 2500
    return update(l, kmh, moving, clock)
}

class ConsumptionSmootherTest {

    @Test fun duplicateReadingIsIgnored() {
        val c = s()
        c.update(6.0, 60, true, 0)
        c.update(6.0, 60, true, 1000)
        c.update(9.0, 60, true, 1500)
        // 6,0 bir kez sayıldı: (6+9)/2 = 7,5
        assertEquals(7.5, c.update(9.0, 60, true, 2500).l100!!, 1e-9)
    }

    @Test fun sameValueCountsAgainAfterGap() {
        val c = s()
        c.update(6.0, 60, true, 0)
        c.update(6.0, 60, true, 1999)
        c.update(12.0, 60, true, 2100)
        c.update(12.0, 60, true, 2200)
        // 6, 12 -> 9,0; 12 ancak 2 sn sonra yeniden eklenir
        assertEquals(10.0, c.update(12.0, 60, true, 4100).l100!!, 1e-9)
    }


    private fun s() = ConsumptionSmoother()

    @Test fun stoppedIsIdleAndResets() {
        val c = s()
        c.u(8.0, 60, true)
        assertEquals(Mode.IDLE, c.u(null, 0, false).mode)
        // Sıfırlandı: eski ortalama yeni değeri etkilemez.
        assertEquals(10.0, c.u(10.0, 60, true).l100!!, 1e-9)
        assertEquals(Mode.IDLE, c.u(null, 0, null).mode)
    }

    @Test fun belowTwentyIsTakeoffWithoutValue() {
        val c = s()
        val d = c.u(95.4, 13, true)
        assertEquals(Mode.TAKEOFF, d.mode)
        assertNull(d.l100)
        assertEquals(Mode.TAKEOFF, c.u(21.0, 19, true).mode)
    }

    @Test fun takeoffHysteresis() {
        val c = s()
        c.u(20.0, 15, true)
        assertEquals(Mode.TAKEOFF, c.u(10.0, 21, true).mode)
        assertEquals(Mode.VALUE, c.u(10.0, 22, true).mode)
        // Değer modunda 20-21 hâlâ değer; yalnızca 20 altı kalkış.
        assertEquals(Mode.VALUE, c.u(10.0, 20, true).mode)
        assertEquals(Mode.TAKEOFF, c.u(10.0, 19, true).mode)
    }

    @Test fun takeoffSamplesDoNotPolluteAverage() {
        val c = s()
        c.u(95.4, 13, true)
        assertEquals(8.0, c.u(8.0, 40, true).l100!!, 1e-9)
    }

    @Test fun rollingMeanOfFiveRoundedToTenth() {
        val c = s()
        listOf(6.0, 6.0, 6.0, 6.0, 6.0).forEach { c.u(it, 60, true) }
        // Pencere kayar: 6,0 x5 sonra 9,0 -> son beşin ortalaması 6,6 (fark 0,6 > 0,3, güncellenir).
        assertEquals(6.6, c.u(9.0, 60, true).l100!!, 1e-9)
        // Pencere: 6,6,6,9,9 (7,2) -> 6,6,9,9,9 (7,8) -> 6,9,9,9,9 (8,4) -> 9 x5 (9,0)
        c.u(9.0, 60, true)
        c.u(9.0, 60, true)
        assertEquals(8.4, c.u(9.0, 60, true).l100!!, 1e-9)
        assertEquals(9.0, c.u(9.0, 60, true).l100!!, 1e-9)
    }

    @Test fun deadbandKeepsShownValue() {
        val c = s()
        assertEquals(7.0, c.u(7.0, 60, true).l100!!, 1e-9)
        // Ortalama 7,1 ve 7,2 (fark <= 0,3): metin 7,0 kalır.
        assertEquals(7.0, c.u(7.2, 60, true).l100!!, 1e-9)
        // Üçüncü örnekle ortalama 7,333 -> 7,3 (fark 0,3 tam sınır): değişmez.
        assertEquals(7.0, c.u(7.6, 60, true).l100!!, 1e-9)
        // Dördüncü: ortalama 7,6 -> fark 0,6 > 0,3: güncellenir.
        assertEquals(7.6, c.u(8.6, 60, true).l100!!, 1e-9)
    }

    @Test fun capAtThirty() {
        val c = s()
        assertEquals(Mode.VALUE, c.u(30.0, 60, true).mode)
        val d = c.u(45.0, 60, true) // ortalama 37,5
        assertEquals(Mode.CAP, d.mode)
        assertEquals(30.0, d.l100!!, 1e-9)
    }

    @Test fun exactlyThirtyIsNotCapped() {
        assertEquals(Mode.VALUE, s().update(30.0, 80, true).mode)
        assertEquals(Mode.CAP, s().update(30.1, 80, true).mode)
    }

    @Test fun missingSampleKeepsShownAndNoSampleIsNone() {
        val c = s()
        assertEquals(Mode.NONE, c.u(null, 60, true).mode)
        c.u(7.0, 60, true)
        assertEquals(7.0, c.u(null, 60, true).l100!!, 1e-9)
    }

    @Test fun unknownSpeedStillSmooths() {
        assertEquals(Mode.VALUE, s().update(7.0, null, true).mode)
    }
}
