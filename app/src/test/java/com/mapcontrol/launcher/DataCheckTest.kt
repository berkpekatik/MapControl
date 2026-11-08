package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataCheckTest {

    @Test fun noAnswerIsGrey() {
        assertEquals(CheckStatus.NO_DATA, CheckClassify.classify(null, null))
        assertEquals(CheckStatus.NO_DATA, CheckClassify.classify(intArrayOf(), "x"))
    }

    @Test fun validIsGreen() {
        assertEquals(CheckStatus.VALID, CheckClassify.classify(intArrayOf(64), "64 km/sa"))
        assertEquals(CheckStatus.VALID, CheckClassify.classify(intArrayOf(0), "Kapalı"))
    }

    @Test fun sentinelIsAmber() {
        assertEquals(CheckStatus.INVALID, CheckClassify.classify(intArrayOf(0xFF), "x"))
        assertEquals(CheckStatus.INVALID, CheckClassify.classify(intArrayOf(0xFFFF), "x"))
        assertEquals(CheckStatus.INVALID, CheckClassify.classify(intArrayOf(0xFF, 0xFF), "x"))
        assertEquals(CheckStatus.INVALID, CheckClassify.classify(intArrayOf(-1), "x"))
    }

    @Test fun decoderRejectionIsAmber() {
        assertEquals(CheckStatus.INVALID, CheckClassify.classify(intArrayOf(7), null))
    }

    @Test fun combineJoinsBytes() {
        assertEquals(3978, CheckClassify.combine(intArrayOf(15, 138)))
        assertEquals(694, CheckClassify.combine(intArrayOf(2, 182)))
        assertEquals(5, CheckClassify.combine(intArrayOf(5)))
    }

    private fun decode(group: String, name: String, v: Int) =
        DataCheckTable.signals.first { it.group == group && it.name == name }.decode(v)

    @Test fun decoders() {
        assertEquals("%69.4", decode("Araç kartı", "Yakıt", 694))
        assertNull(decode("Araç kartı", "Yakıt", 2000))
        assertEquals("Açık", decode("Araç kartı", "Bagaj", 1))
        assertNull(decode("Araç kartı", "Bagaj", 5))
        assertEquals("Sport", decode("Sürüş modu", "Sürüş modu", 2))
        assertEquals("12.1 L/100km", decode("Hız ve tüketim", "Anlık tüketim", 121))
        assertNull(decode("Hız ve tüketim", "Anlık tüketim", 0xFFFF))
        assertEquals("3978 km", decode("Bakım", "Servise kalan km", 3978))
        assertEquals("12.5 V", decode("Araç sağlığı", "Akü voltajı", 125))
        assertEquals("21.5 °C", decode("Klima", "Sıcaklık sürücü", 12))
        assertEquals("45 km/sa", decode("Yolculuk", "Ort. hız", 456))
        assertEquals("Kademe 2", decode("Koltuklar", "Sürücü ısıtma", 2))
        assertEquals("Açık", decode("Tavan perdesi", "Perde", 5))
    }

    @Test fun tableHasUniqueSignalsAndAllGroups() {
        val keys = DataCheckTable.signals.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(DataCheckTable.groups.containsAll(listOf("Araç kartı", "Vites", "Hız limiti", "Buğu")))
    }

    @Test fun changeTrackerKeepsTimeUntilChange() {
        val t = ChangeTracker()
        assertEquals(100L, t.update("a", "1", 100))
        assertEquals(100L, t.update("a", "1", 5000))
        assertEquals(6000L, t.update("a", "2", 6000))
    }

    @Test fun ageText() {
        assertEquals("şimdi", ChangeTracker.ageText(500))
        assertEquals("3 sn", ChangeTracker.ageText(3200))
        assertEquals("2 dk", ChangeTracker.ageText(125_000))
        assertEquals("1 sa", ChangeTracker.ageText(3_700_000))
    }

    @Test fun launcherInfoFormatsKeyValueLines() {
        assertEquals("a: 1\nb: x", LauncherInfo.format(listOf("a" to "1", "b" to "x")))
    }
}
