package com.mapcontrol.vehicle

import com.mapcontrol.launcher.ClimateMath
import com.mapcontrol.launcher.WidgetWritePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Launcher klima aç/kapat planı FrostNav konfor otomasyonunun yazmalarıyla birebir aynı kalmalı ve beyaz listede olmalı. */
class ClimatePowerParityTest {
    private fun frostNav(list: List<ClimateAutomationWrites.Write>): List<Pair<Int, Int>> {
        list.forEach { assertEquals(ClimateAutomationWrites.MODULE_HVAC, it.module) }
        return list.map { it.id to it.value }
    }

    @Test fun powerOnMatchesFrostNav() {
        val plan = ClimateMath.powerPlan(true)
        assertEquals(frostNav(ClimateAutomationWrites.onWrites(ClimateAutomationStore.CLIMATE, 0)), plan.first)
        assertEquals(frostNav(ClimateAutomationWrites.climateFollowUpWrites()), plan.followUp)
    }

    @Test fun powerOffMatchesFrostNav() {
        val plan = ClimateMath.powerPlan(false)
        assertEquals(frostNav(ClimateAutomationWrites.offWrites(ClimateAutomationStore.CLIMATE)), plan.first)
        assertTrue(plan.followUp.isEmpty())
    }

    @Test fun followUpDelayIsOem150ms() {
        assertEquals(150L, ClimateMath.POWER_FOLLOW_UP_MS)
    }

    @Test fun everyPlannedWriteIsWhitelisted() {
        for (on in listOf(true, false)) {
            val plan = ClimateMath.powerPlan(on)
            (plan.first + plan.followUp).forEach { (id, v) ->
                assertNotNull("id=$id v=$v", WidgetWritePolicy.find(ClimateAutomationWrites.MODULE_HVAC, id, v))
            }
        }
    }

    @Test fun zeroNeverWhitelistedForPowerAcAuto() {
        ClimateMath.powerPlan(true).followUp.forEach { (id, _) ->
            assertEquals(null, WidgetWritePolicy.find(ClimateAutomationWrites.MODULE_HVAC, id, 0))
        }
    }
}
