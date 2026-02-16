package com.makeramen.around.ble

import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BleDeviceTest {

    @BeforeTest
    fun setUp() {
        AppleDeviceCache.clear()
        GoogleDeviceCache.clear()
    }

    // ========== Oura Detection (isLikelyOura) ==========

    @Test
    fun `isLikelyOura - MAC prefix match`() {
        val device = device(address = "A0:38:F8:12:34:56")
        assertTrue(device.isLikelyOura())
    }

    @Test
    fun `isLikelyOura - MAC prefix case insensitive`() {
        val device = device(address = "a0:38:f8:12:34:56")
        assertTrue(device.isLikelyOura())
    }

    @Test
    fun `isLikelyOura - manufacturer ID 690`() {
        val device = device(manufacturerData = mapOf(690 to byteArrayOf()))
        assertTrue(device.isLikelyOura())
    }

    @Test
    fun `isLikelyOura - manufacturer ID 772`() {
        val device = device(manufacturerData = mapOf(772 to byteArrayOf()))
        assertTrue(device.isLikelyOura())
    }

    @Test
    fun `isLikelyOura - advertised name contains Oura`() {
        val device = device(advertisedName = "Oura_ABCD")
        assertTrue(device.isLikelyOura())
    }

    @Test
    fun `isLikelyOura - bonded name contains Oura`() {
        val device = device(name = "Oura Ring 3")
        assertTrue(device.isLikelyOura())
    }

    @Test
    fun `isLikelyOura - name match is case insensitive`() {
        val device = device(name = "oura ring")
        assertTrue(device.isLikelyOura())
    }

    @Test
    fun `isLikelyOura - unrelated device returns false`() {
        val device = device(
            address = "00:11:22:33:44:55",
            name = "Some Speaker",
            manufacturerData = mapOf(999 to byteArrayOf())
        )
        assertFalse(device.isLikelyOura())
    }

    @Test
    fun `isLikelyOura - no identifiers returns false`() {
        val device = device()
        assertFalse(device.isLikelyOura())
    }

    // ========== Apple Detection ==========

    @Test
    fun `isAppleDevice - Apple manufacturer ID`() {
        val device = device(manufacturerData = mapOf(76 to byteArrayOf(0x10)))
        assertTrue(device.isAppleDevice())
    }

    @Test
    fun `isAppleDevice - non-Apple manufacturer`() {
        val device = device(manufacturerData = mapOf(999 to byteArrayOf()))
        assertFalse(device.isAppleDevice())
    }

    @Test
    fun `isAppleDevice - no manufacturer data`() {
        val device = device()
        assertFalse(device.isAppleDevice())
    }

    // ========== AirTag Detection ==========

    @Test
    fun `isAirTag - Find My type with full battery status`() {
        val device = appleDevice(0x12, 0x00, 0x10)
        assertTrue(device.isAirTag())
    }

    @Test
    fun `isAirTag - Find My type with medium battery status`() {
        val device = appleDevice(0x12, 0x00, 0x50)
        assertTrue(device.isAirTag())
    }

    @Test
    fun `isAirTag - Find My type with low battery status`() {
        val device = appleDevice(0x12, 0x00, 0x90)
        assertTrue(device.isAirTag())
    }

    @Test
    fun `isAirTag - Find My type with very low battery status`() {
        val device = appleDevice(0x12, 0x00, 0xD0)
        assertTrue(device.isAirTag())
    }

    @Test
    fun `isAirTag - Find My type with non-AirTag status byte`() {
        val device = appleDevice(0x12, 0x00, 0x00)
        assertFalse(device.isAirTag())
    }

    @Test
    fun `isAirTag - non-Find My type`() {
        val device = appleDevice(0x07, 0x00, 0x10)
        assertFalse(device.isAirTag())
    }

    @Test
    fun `isAirTag - data too short`() {
        val device = device(manufacturerData = mapOf(76 to byteArrayOf(0x12)))
        assertFalse(device.isAirTag())
    }

    // ========== Apple Continuity Types (getDetectedType) ==========

    @Test
    fun `getDetectedType - iBeacon`() {
        val device = appleDevice(0x02)
        assertEquals("iBeacon", device.getDetectedType())
    }

    @Test
    fun `getDetectedType - AirDrop`() {
        val device = appleDevice(0x05)
        assertEquals("iPhone/Mac (AirDrop)", device.getDetectedType())
    }

    @Test
    fun `getDetectedType - AirPods`() {
        val device = appleDevice(0x07)
        assertEquals("AirPods", device.getDetectedType())
    }

    @Test
    fun `getDetectedType - HomeKit`() {
        val device = appleDevice(0x06)
        assertEquals("HomeKit Device", device.getDetectedType())
    }

    @Test
    fun `getDetectedType - AirPlay Target`() {
        val device = appleDevice(0x09)
        assertEquals("AirPlay Target", device.getDetectedType())
    }

    @Test
    fun `getDetectedType - Handoff`() {
        val device = appleDevice(0x0C)
        assertEquals("iPhone/Mac (Handoff)", device.getDetectedType())
    }

    @Test
    fun `getDetectedType - AirTag full battery`() {
        val device = appleDevice(0x12, 0x00, 0x10)
        assertEquals("AirTag (Full)", device.getDetectedType())
    }

    @Test
    fun `getDetectedType - AirTag medium battery`() {
        val device = appleDevice(0x12, 0x00, 0x50)
        assertEquals("AirTag (Medium)", device.getDetectedType())
    }

    @Test
    fun `getDetectedType - AirTag low battery`() {
        val device = appleDevice(0x12, 0x00, 0x90)
        assertEquals("AirTag (Low)", device.getDetectedType())
    }

    @Test
    fun `getDetectedType - AirTag very low battery`() {
        val device = appleDevice(0x12, 0x00, 0xD0)
        assertEquals("AirTag (Very Low)", device.getDetectedType())
    }

    @Test
    fun `getDetectedType - non-AirTag Find My device`() {
        val device = appleDevice(0x12, 0x00, 0x00)
        assertEquals("Find My Device", device.getDetectedType())
    }

    // ========== Nearby Info Action Codes ==========

    @Test
    fun `getDetectedType - Nearby Info idle`() {
        val device = appleDevice(0x10, 0x00, 0x03)
        assertEquals("iPhone/Mac (Idle)", device.getDetectedType())
    }

    @Test
    fun `getDetectedType - Nearby Info active`() {
        val device = appleDevice(0x10, 0x00, 0x07)
        assertEquals("iPhone/Mac (Active)", device.getDetectedType())
    }

    @Test
    fun `getDetectedType - Nearby Info driving`() {
        val device = appleDevice(0x10, 0x00, 0x0D)
        assertEquals("iPhone/Mac (Driving)", device.getDetectedType())
    }

    @Test
    fun `getDetectedType - Nearby Info on call`() {
        val device = appleDevice(0x10, 0x00, 0x0E)
        assertEquals("iPhone/Mac (On Call)", device.getDetectedType())
    }

    @Test
    fun `getDetectedType - Nearby Info action code in high nibble ignored`() {
        val device = appleDevice(0x10, 0x00, 0x73)
        assertEquals("iPhone/Mac (Idle)", device.getDetectedType())
    }

    // ========== Apple Continuity Type Enum ==========

    @Test
    fun `AppleContinuityType fromByte - known type`() {
        assertEquals(AppleContinuityType.AIRPODS, AppleContinuityType.fromByte(0x07))
    }

    @Test
    fun `AppleContinuityType fromByte - unknown type`() {
        assertNull(AppleContinuityType.fromByte(0x7F))
    }

    // ========== Cache Behavior ==========

    @Test
    fun `getDetectedType - caches detected type and uses as fallback`() {
        val device1 = appleDevice(0x07, address = "AA:BB:CC:DD:EE:FF")
        assertEquals("AirPods", device1.getDetectedType())

        val device2 = device(
            address = "AA:BB:CC:DD:EE:FF",
            manufacturerData = mapOf(76 to byteArrayOf(0x7F.toByte()))
        )
        assertEquals("Unknown [prev: AirPods]", device2.getDetectedType())
    }

    @Test
    fun `getDetectedType - no cache returns empty string for unknown`() {
        val device = device(
            manufacturerData = mapOf(76 to byteArrayOf(0x7F.toByte()))
        )
        assertEquals("", device.getDetectedType())
    }

    @Test
    fun `getDetectedType - non-Apple device returns null`() {
        val device = device()
        assertNull(device.getDetectedType())
    }

    // ========== Display Name ==========

    @Test
    fun `getDisplayName - prefers advertised name`() {
        val device = device(name = "Bonded", advertisedName = "Advertised")
        assertEquals("Advertised", device.getDisplayName())
    }

    @Test
    fun `getDisplayName - falls back to bonded name`() {
        val device = device(name = "Bonded")
        assertEquals("Bonded", device.getDisplayName())
    }

    @Test
    fun `getDisplayName - null when no name`() {
        val device = device()
        assertNull(device.getDisplayName())
    }

    // ========== Google Device Detection ==========

    @Test
    fun `isGoogleDevice - Google manufacturer ID`() {
        val device = device(manufacturerData = mapOf(224 to byteArrayOf()))
        assertTrue(device.isGoogleDevice())
    }

    @Test
    fun `isGoogleDevice - no Google identifiers returns false`() {
        val device = device()
        assertFalse(device.isGoogleDevice())
    }

    @Test
    fun `getGoogleDetectedType - Google manufacturer ID`() {
        val device = device(manufacturerData = mapOf(224 to byteArrayOf()))
        assertEquals("Google Device", device.getGoogleDetectedType())
    }

    @Test
    fun `getGoogleDetectedType - non-Google device returns null`() {
        val device = device()
        assertNull(device.getGoogleDetectedType())
    }

    // ========== Signal Strength ==========

    @Test
    fun `SignalStrength - very close at -50`() {
        assertEquals(SignalStrength.VERY_CLOSE, SignalStrength.fromRssi(-50))
    }

    @Test
    fun `SignalStrength - very close at 0`() {
        assertEquals(SignalStrength.VERY_CLOSE, SignalStrength.fromRssi(0))
    }

    @Test
    fun `SignalStrength - close at -51`() {
        assertEquals(SignalStrength.CLOSE, SignalStrength.fromRssi(-51))
    }

    @Test
    fun `SignalStrength - close at -70`() {
        assertEquals(SignalStrength.CLOSE, SignalStrength.fromRssi(-70))
    }

    @Test
    fun `SignalStrength - far at -71`() {
        assertEquals(SignalStrength.FAR, SignalStrength.fromRssi(-71))
    }

    @Test
    fun `SignalStrength - far at -85`() {
        assertEquals(SignalStrength.FAR, SignalStrength.fromRssi(-85))
    }

    @Test
    fun `SignalStrength - very far at -86`() {
        assertEquals(SignalStrength.VERY_FAR, SignalStrength.fromRssi(-86))
    }

    @Test
    fun `SignalStrength - very far at -100`() {
        assertEquals(SignalStrength.VERY_FAR, SignalStrength.fromRssi(-100))
    }

    @Test
    fun `SignalStrength - extreme low defaults to very far`() {
        assertEquals(SignalStrength.VERY_FAR, SignalStrength.fromRssi(-120))
    }

    // ========== Test Helpers ==========

    private fun device(
        name: String? = null,
        address: String = "00:11:22:33:44:55",
        rssi: Int = -60,
        advertisedName: String? = null,
        manufacturerData: Map<Int, ByteArray> = emptyMap(),
        serviceUuids: List<String> = emptyList()
    ) = BleDevice(
        name = name,
        address = address,
        rssi = rssi,
        advertisedName = advertisedName,
        manufacturerData = manufacturerData,
        serviceUuids = serviceUuids,
        lastSeen = Clock.System.now().toEpochMilliseconds()
    )

    /** Create an Apple device with the given continuity type bytes */
    private fun appleDevice(
        vararg bytes: Int,
        address: String = "00:11:22:33:44:55"
    ) = device(
        address = address,
        manufacturerData = mapOf(76 to bytes.map { it.toByte() }.toByteArray())
    )
}
