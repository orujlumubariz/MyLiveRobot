package com.mylive.robot

import android.content.pm.ApplicationInfo
import com.mylive.robot.ui.DebugGate
import com.mylive.robot.ui.DebugState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugGateTest {
    @Test fun debuggableFlagEnablesThePanel() {
        assertTrue(DebugGate.isDebuggable(ApplicationInfo.FLAG_DEBUGGABLE))
        assertTrue(DebugGate.isDebuggable(ApplicationInfo.FLAG_DEBUGGABLE or ApplicationInfo.FLAG_HAS_CODE))
    }

    @Test fun releaseBuildsHaveNoPanel() {
        assertFalse(DebugGate.isDebuggable(0))
        assertFalse(DebugGate.isDebuggable(ApplicationInfo.FLAG_HAS_CODE or ApplicationInfo.FLAG_ALLOW_BACKUP))
    }

    @Test fun debugStateStartsOffWithSaneDefaults() {
        val s = DebugState()
        assertFalse(s.fakeBatteryOn)
        assertFalse(s.fakeTimeOn)
        assertFalse(s.charging)
        assertEquals(80, s.batteryPercent)
        assertEquals(12 * 60, s.minuteOfDay)
    }
}
