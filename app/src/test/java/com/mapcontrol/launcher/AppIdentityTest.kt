package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIdentityTest {

    @Test
    fun packageLikeLabelsFallBackToPrettyLastSegment() {
        val pkg = "com.desaysv.vehiclesetting"
        assertEquals("Vehiclesetting", AppIdentity.displayLabel(pkg, pkg))
        assertEquals("Vehiclesetting", AppIdentity.displayLabel(pkg, ""))
        assertEquals("Vehiclesetting", AppIdentity.displayLabel(pkg, "com.desaysv.vehiclesetti"))
        assertEquals("Some app", AppIdentity.displayLabel("com.x.some_app", "com.x.some_app"))
    }

    @Test
    fun realLabelsAreKept() {
        assertEquals("Ses kaynağı", AppIdentity.displayLabel("com.desaysv.svaudioapp", " Ses kaynağı "))
        assertEquals("Video", AppIdentity.displayLabel("com.desaysv.videoapp", "Video"))
        assertFalse(AppIdentity.looksLikePackage("Spotify", "com.spotify.music"))
        assertFalse(AppIdentity.looksLikePackage("Maps v2.0", "com.x.maps"))
        assertTrue(AppIdentity.looksLikePackage("com.x.maps", "other"))
    }

    @Test
    fun overrideWinsAndKnownPackagesAreListed() {
        assertEquals("Araç ayarları", AppIdentity.displayLabel("com.desaysv.vehiclesetting", "x", "Araç ayarları"))
        assertTrue(AppIdentity.OVERRIDES.containsKey("com.desaysv.vehiclesetting"))
        assertTrue(AppIdentity.OVERRIDES.containsKey("com.desaysv.bluetooth.phone"))
    }
}
