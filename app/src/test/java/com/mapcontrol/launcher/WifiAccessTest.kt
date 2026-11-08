package com.mapcontrol.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiAccessTest {
    @Test fun permissionComesFirst() {
        assertEquals(WifiAccess.Gate.NEED_PERMISSION, WifiAccess.gate(hasPermission = false, locationOn = false))
        assertEquals(WifiAccess.Gate.NEED_PERMISSION, WifiAccess.gate(hasPermission = false, locationOn = true))
    }

    @Test fun locationOffAfterPermission() {
        assertEquals(WifiAccess.Gate.LOCATION_OFF, WifiAccess.gate(hasPermission = true, locationOn = false))
        assertEquals(WifiAccess.Gate.LIST, WifiAccess.gate(hasPermission = true, locationOn = true))
    }

    @Test fun firstAskRequestsAtRuntime() {
        assertEquals(WifiAccess.PermissionAction.REQUEST, WifiAccess.permissionAction(deniedBefore = false, showRationale = false))
        assertEquals(WifiAccess.PermissionAction.REQUEST, WifiAccess.permissionAction(deniedBefore = true, showRationale = true))
    }

    @Test fun deniedForeverOpensAppSettings() {
        assertEquals(WifiAccess.PermissionAction.OPEN_APP_SETTINGS, WifiAccess.permissionAction(deniedBefore = true, showRationale = false))
    }

    @Test fun permissionResultReadsFineFirst() {
        assertEquals(WifiAccess.PermissionResult.GRANTED, WifiAccess.permissionResult(intArrayOf(0, 0)))
        assertEquals(WifiAccess.PermissionResult.DENIED, WifiAccess.permissionResult(intArrayOf(-1, -1)))
        // Android 12+ "Yaklaşık": yalnızca COARSE; Wi-Fi tarama sonuçları FINE ister.
        assertEquals(WifiAccess.PermissionResult.DENIED, WifiAccess.permissionResult(intArrayOf(-1, 0)))
    }

    @Test fun interruptedDialogIsNotADenial() {
        assertEquals(WifiAccess.PermissionResult.CANCELLED, WifiAccess.permissionResult(intArrayOf()))
    }
}
