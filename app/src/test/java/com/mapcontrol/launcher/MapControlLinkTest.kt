package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapControlLinkTest {

    @Test
    fun prefersMapControlPackage() {
        val installed = setOf("com.mapcontrol", "com.mapcontrol")
        assertEquals("com.mapcontrol", MapControlLink.choosePackage("com.mapcontrol.launcher", installed))
    }

    @Test
    fun fallsBackToMapControl() {
        assertEquals("com.mapcontrol", MapControlLink.choosePackage("com.mapcontrol.preview", setOf("com.mapcontrol")))
    }

    @Test
    fun hiddenInsideMapControlItself() {
        assertNull(MapControlLink.choosePackage("com.mapcontrol", setOf("com.mapcontrol", "com.mapcontrol")))
        assertNull(MapControlLink.choosePackage("com.mapcontrol", setOf("com.mapcontrol")))
    }

    @Test
    fun nullWhenNothingInstalled() {
        assertNull(MapControlLink.choosePackage("com.mapcontrol.preview", setOf("com.other")))
    }
}
