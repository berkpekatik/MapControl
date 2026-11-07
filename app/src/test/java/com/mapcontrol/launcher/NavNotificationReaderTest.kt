package com.mapcontrol.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavNotificationReaderTest {
    @Test fun smallIntsAreNotResourceIds() {
        // 6 Kasım araç: 8 gibi değerler kaynak sanılıp saniyede onlarca "Invalid ID" yazdırıyordu.
        assertFalse(NavNotificationReader.looksLikeResId(0))
        assertFalse(NavNotificationReader.looksLikeResId(8))
        assertFalse(NavNotificationReader.looksLikeResId(120))
        assertFalse(NavNotificationReader.looksLikeResId(-1))
        assertTrue(NavNotificationReader.looksLikeResId(0x7f080123))
        assertTrue(NavNotificationReader.looksLikeResId(0x01080000))
    }
}
