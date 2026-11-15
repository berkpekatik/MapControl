package com.mapcontrol.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPlayFormatTest {

    @Test fun autoFollowsDetection() {
        assertTrue(MediaPlayFormat.resolve(MediaPlayFormat.AUTO, true))
        assertFalse(MediaPlayFormat.resolve(MediaPlayFormat.AUTO, false))
    }

    @Test fun autoFallsBackToShortWhenDetectionFails() {
        assertFalse(MediaPlayFormat.resolve(MediaPlayFormat.AUTO, null))
    }

    /**
     * VDMediaPlay kopyası bazı araçlarda /system/framework/vdbus.jar ile gölgelenir; kopyaya eklenen statik üye orada
     * yoktur (1.5.0: setWithActions → NoSuchMethodError, launcher çökme döngüsü). Biçim bayrağı [MediaPlayWire]'da durur.
     */
    @Test fun vdMediaPlayCopyHasNoOwnStaticMembers() {
        val cls = com.desaysv.ivi.vdb.event.id.media.bean.VDMediaPlay::class.java
        // SDK'nın her bean'de taşıdığı standart üçlü; bunun dışı bizim eklememizdir.
        val sdk = setOf("getValue", "createPayload", "createEvent")
        val statics = cls.declaredMethods.filter {
            java.lang.reflect.Modifier.isStatic(it.modifiers) && !it.isSynthetic && it.name !in sdk
        }
        assertTrue("statik metot: ${statics.map { it.name }}", statics.isEmpty())
        val fields = cls.declaredFields.filter {
            java.lang.reflect.Modifier.isStatic(it.modifiers) && !it.isSynthetic && it.name != "CREATOR"
        }
        assertTrue("statik alan: ${fields.map { it.name }}", fields.isEmpty())
    }

    @Test fun manualChoiceOverridesDetection() {
        assertFalse(MediaPlayFormat.resolve(MediaPlayFormat.SHORT, true))
        assertTrue(MediaPlayFormat.resolve(MediaPlayFormat.LONG, false))
        assertTrue(MediaPlayFormat.resolve(MediaPlayFormat.LONG, null))
    }
}
