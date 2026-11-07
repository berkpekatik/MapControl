package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * HomeLauncherActivity.onRequestPermissionsResult sonucu istek koduna göre dağıtır; iki özellik aynı kodu kullanırsa sonuç
 * yanlış yere gider (Wi-Fi konum izni telefon izinlerine gidiyordu: üçü de 4711'di).
 */
class LauncherRequestCodesTest {
    @Test fun permissionRequestCodesAreUnique() {
        val codes = listOf(PhoneSource.REQ_PERMISSIONS, WifiPanel.REQ_LOCATION, PhotoTile.REQ_STORAGE, CarSpeedReader.REQ_PERMISSION)
        assertEquals(codes.size, codes.toSet().size)
    }
}
