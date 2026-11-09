package org.comdex.community.comdex

import org.junit.Assert.*
import org.junit.Test

class DeviceRulesTest {
    @Test fun roomOffCanBeRequestedWhileIdle() {
        assertNull(DeviceRules.commandError("comdex", "microphone", 0, 1, true))
        assertNull(DeviceRules.commandError("comdex", "microphone", 0, 3, true))
        assertNull(DeviceRules.commandError("comdex", "microphone", 1, 2, true))
    }
    @Test fun bondedNamesAreModelSpecificAndSeparatedByKind() {
        assertTrue(DeviceRules.matchesName("comdex", "COM-DEX 415"))
        assertTrue(DeviceRules.matchesName("comdex", "com-dex"))
        assertFalse(DeviceRules.matchesName("comdex", "DEX Headphones"))
        assertFalse(DeviceRules.matchesName("comdex", "COM-DEX Remote Mic"))
        assertFalse(DeviceRules.matchesName("comdex", "Headphones COM-DEX 415"))
        assertFalse(DeviceRules.matchesName("comdex", null))
        assertTrue(DeviceRules.matchesName("cdrm", "COM-DEX Remote Mic"))
    }
    @Test fun connectionModesMatchOriginalTransportValues() {
        assertEquals(2,DeviceRules.transport("blue")); assertEquals(1,DeviceRules.transport("green"))
    }
    @Test fun remoteMicCannotStartDuringCallOrWithoutParent() {
        assertNotNull(DeviceRules.commandError("cdrm","stream",2,2,true))
        assertNotNull(DeviceRules.commandError("cdrm","stream",2,1,false))
        assertNotNull(DeviceRules.commandError("cdrm","stream",2,null,true))
        assertNotNull(DeviceRules.commandError("cdrm","stream",2,0,true))
        assertNull(DeviceRules.commandError("cdrm","stream",2,1,true))
        assertNull(DeviceRules.commandError("cdrm","stream",1,2,true)) // pause remains available
    }
    @Test fun mediaBoostNotAvailableForCalls() {
        assertNotNull(DeviceRules.commandError("comdex","boost",2,2,true))
        assertNull(DeviceRules.commandError("comdex","boost",2,3,true))
        assertNull(DeviceRules.commandError("cdrm","gain",2,2,true))
    }
    @Test fun batteryThresholdsFromManufacturerFaq() {
        assertEquals(0,DeviceRules.batteryWarning(null)); assertEquals(0,DeviceRules.batteryWarning(255))
        assertEquals(0,DeviceRules.batteryWarning(21)); assertEquals(1,DeviceRules.batteryWarning(20))
        assertEquals(1,DeviceRules.batteryWarning(5)); assertEquals(2,DeviceRules.batteryWarning(4))
    }
}
