package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class StageFitVerticalTest {

    @Test fun lyingBodyKeepsCenterAndSwapsAxes() {
        val aspect = CarAnimSpec.BASE_H / CarAnimSpec.BASE_W
        val upright = StageFit.Box(400f, 20f, 400f + 300f * aspect, 320f)
        val body = StageFit.lyingBody(upright, aspect)
        assertEquals(300f, body.width, 0.01f)
        assertEquals(upright.width, body.height, 0.01f)
        assertEquals((upright.left + upright.right) / 2f, (body.left + body.right) / 2f, 0.01f)
        assertEquals((upright.top + upright.bottom) / 2f, (body.top + body.bottom) / 2f, 0.01f)
    }
}
