package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeBindingsTest {
    @Test fun bindRunsImmediatelyAndInOrder() {
        val log = ArrayList<String>()
        val t = ThemeBindings()
        t.bind { log += "a" }
        t.bind { log += "b" }
        assertEquals(listOf("a", "b"), log)
    }

    @Test fun reapplyRerunsEveryBlockWithCurrentValues() {
        var color = 1
        val applied = ArrayList<Int>()
        val t = ThemeBindings()
        t.bind { applied += color }
        t.bind { applied += color * 10 }
        color = 2
        t.reapply()
        assertEquals(listOf(1, 10, 2, 20), applied)
    }
}
