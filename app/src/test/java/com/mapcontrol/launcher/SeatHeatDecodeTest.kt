package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class SeatHeatDecodeTest {
    @Test fun `araç değeri doğrudan seviyedir`() {
        assertEquals(0, SeatHeatModel.decode(0))
        assertEquals(1, SeatHeatModel.decode(1))
        assertEquals(2, SeatHeatModel.decode(2))
        assertEquals(3, SeatHeatModel.decode(3))
    }

    @Test fun `aralık dışı değer kırpılır`() {
        assertEquals(0, SeatHeatModel.decode(-1))
        assertEquals(3, SeatHeatModel.decode(4))
    }
}
