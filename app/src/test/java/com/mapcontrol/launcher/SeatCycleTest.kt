package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class SeatCycleTest {

    @Test fun cycleMatchesCarButton() {
        // Araçtaki düğme gibi: kapalıdan açınca en yüksek seviye, sonra azalır.
        assertEquals(3, SeatHeatModel.nextLevel(0))
        assertEquals(2, SeatHeatModel.nextLevel(3))
        assertEquals(1, SeatHeatModel.nextLevel(2))
        assertEquals(0, SeatHeatModel.nextLevel(1))
        assertEquals(3, SeatHeatModel.nextLevel(null))
    }
}
