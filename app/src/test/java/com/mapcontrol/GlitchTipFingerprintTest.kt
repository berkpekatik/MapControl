package com.mapcontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlitchTipFingerprintTest {

    private fun at(cls: String, method: String) = StackTraceElement(cls, method, "F.kt", 1)

    @Test fun linkageErrorsGroupByTypeAndFirstOwnFrame() {
        val a = NoSuchMethodError("No static method x in /system/framework/vdbus.jar").apply {
            stackTrace = arrayOf(at("dalvik.Foo", "run"), at("com.mapcontrol.launcher.MediaPlayFormat", "apply"))
        }
        val b = NoSuchMethodError("No static method x in /system/framework/vdbus.jar!classes2.dex").apply {
            stackTrace = arrayOf(at("com.mapcontrol.launcher.MediaPlayFormat", "apply"))
        }
        val expected = listOf("linkage", "java.lang.NoSuchMethodError", "com.mapcontrol.launcher.MediaPlayFormat.apply")
        assertEquals(expected, GlitchTip.fingerprint(a))
        assertEquals(expected, GlitchTip.fingerprint(b))
    }

    @Test fun linkageErrorFoundAsCause() {
        val cause = NoClassDefFoundError("x").apply { stackTrace = arrayOf(at("com.mapcontrol.A", "b")) }
        assertEquals(listOf("linkage", "java.lang.NoClassDefFoundError", "com.mapcontrol.A.b"),
            GlitchTip.fingerprint(RuntimeException(cause)))
    }

    @Test fun otherErrorsKeepDefaultGrouping() {
        assertNull(GlitchTip.fingerprint(IllegalStateException("x")))
        assertNull(GlitchTip.fingerprint(null))
    }
}
