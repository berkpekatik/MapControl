package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfilesTest {
    @Test fun profileOneKeepsExistingNames() {
        // Taşıma gerekmez: mevcut dosya/anahtar adları profil 1'e aittir.
        assertEquals("launcher_layout", Profiles.key("launcher_layout", 1))
        assertEquals("theme_mode", Profiles.key("theme_mode", 1))
        assertEquals("slot_0", Profiles.key("slot_0", 1))
    }

    @Test fun otherProfilesGetSuffix() {
        assertEquals("launcher_nav_p2", Profiles.key("launcher_nav", 2))
        assertEquals("theme_mode_p3", Profiles.key("theme_mode", 3))
    }

    @Test fun invalidIdFallsBackToProfileOne() {
        assertEquals(1, Profiles.sanitize(0))
        assertEquals(1, Profiles.sanitize(4))
        assertEquals(1, Profiles.sanitize(-7))
        assertEquals("launcher_apps", Profiles.key("launcher_apps", 99))
        assertEquals(3, Profiles.sanitize(3))
    }

    @Test fun autoSelectIsNotWiredYet() {
        assertNull(Profiles.autoSelectFromVehicle(4))
    }

    @Test fun nameSanitising() {
        assertEquals("Ayşe", ProfileNaming.sanitizeName("  Ayşe  ", "Sürücü 1"))
        assertEquals("", ProfileNaming.sanitizeName("   ", "Sürücü 1"))
        assertEquals("", ProfileNaming.sanitizeName(null, "Sürücü 1"))
        assertEquals("", ProfileNaming.sanitizeName("Sürücü 1", "Sürücü 1"))
        assertEquals("1234567890123456", ProfileNaming.sanitizeName("12345678901234567890", "Sürücü 1"))
        // Kesince sondaki boşluk kalmaz.
        assertEquals("abcdefghijklmno", ProfileNaming.sanitizeName("abcdefghijklmno xyz", "Sürücü 1"))
    }

    @Test fun avatarLabelUsesNumberForDefaultName() {
        assertEquals("2", ProfileNaming.avatarLabel(2, "", "Sürücü 2"))
        assertEquals("2", ProfileNaming.avatarLabel(2, "Sürücü 2", "Sürücü 2"))
        assertEquals("3", ProfileNaming.avatarLabel(3, "  ", "Sürücü 3"))
    }

    @Test fun avatarLabelHandlesTurkishCase() {
        assertEquals("İ", ProfileNaming.avatarLabel(1, "ilker", "Sürücü 1"))
        assertEquals("I", ProfileNaming.avatarLabel(1, "ısıl", "Sürücü 1"))
        assertEquals("Ş", ProfileNaming.avatarLabel(1, "şule", "Sürücü 1"))
        assertEquals("A", ProfileNaming.avatarLabel(1, " ayşe", "Sürücü 1"))
    }

    @Test fun avatarLabelKeepsSurrogatePairs() {
        assertEquals("\uD83D\uDE97", ProfileNaming.avatarLabel(1, "\uD83D\uDE97 araba", "Sürücü 1"))
    }
}
