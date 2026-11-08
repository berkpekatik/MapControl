package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrostNavLinkTest {

    @Test
    fun prefersFrostNavPackage() {
        val installed = setOf("com.frostnav", "com.mapcontrol")
        assertEquals("com.frostnav", FrostNavLink.choosePackage("com.mapcontrol.launcher", installed))
    }

    @Test
    fun fallsBackToMapControl() {
        assertEquals("com.mapcontrol", FrostNavLink.choosePackage("com.mapcontrol.preview", setOf("com.mapcontrol")))
    }

    @Test
    fun hiddenInsideFrostNavItself() {
        assertNull(FrostNavLink.choosePackage("com.frostnav", setOf("com.frostnav", "com.mapcontrol")))
        assertNull(FrostNavLink.choosePackage("com.mapcontrol", setOf("com.mapcontrol")))
    }

    @Test
    fun nullWhenNothingInstalled() {
        assertNull(FrostNavLink.choosePackage("com.mapcontrol.preview", setOf("com.other")))
    }
}
