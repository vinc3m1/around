package com.makeramen.ouround.ble

import android.os.ParcelUuid

// Oura identification constants
private const val OURA_MAC_PREFIX = "A0:38:F8"
private const val OURA_NAME_PATTERN = "Oura"
private val OURA_MANUFACTURER_IDS = setOf(
    690,  // 0x02B2 - Oura Health Oy
    772   // 0x0304 - Oura Health Ltd
)

// Apple identification constants
private const val APPLE_MANUFACTURER_ID = 76  // 0x004C

// Apple Continuity protocol type bytes
enum class AppleContinuityType(val byte: Byte, val label: String) {
    IBEACON(0x02, "iBeacon"),
    AIRPRINT(0x03, "AirPrint"),
    AIRDROP(0x05, "iPhone/Mac (AirDrop)"),
    HOMEKIT(0x06, "HomeKit Device"),
    AIRPODS(0x07, "AirPods"),
    HEY_SIRI(0x08, "iPhone/Mac (Hey Siri)"),
    AIRPLAY(0x09, "AirPlay Target"),
    AIRPLAY_SOURCE(0x0A, "AirPlay Source"),
    WATCH(0x0B, "Apple Watch"),
    HANDOFF(0x0C, "iPhone/Mac (Handoff)"),
    WIFI_SETUP(0x0D, "iPhone/Mac (WiFi Setup)"),
    HOTSPOT(0x0E, "iPhone/Mac (Hotspot)"),
    NEARBY_ACTION(0x0F, "iPhone/Mac (Nearby Action)"),
    NEARBY_INFO(0x10, "iPhone/Mac (Nearby)"),
    FIND_MY(0x12, "Find My Device");

    companion object {
        fun fromByte(byte: Byte): AppleContinuityType? = entries.find { it.byte == byte }
    }
}

data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val txPower: Int? = null,
    val advertisedName: String? = null, // From ScanRecord.getDeviceName()
    val manufacturerData: Map<Int, ByteArray> = emptyMap(), // Manufacturer ID -> data
    val serviceUuids: List<ParcelUuid> = emptyList(),
    val lastSeen: Long = System.currentTimeMillis()
) {
    fun isLikelyOura(): Boolean {
        // Check MAC address prefix (works for non-bonded devices)
        if (address.startsWith(OURA_MAC_PREFIX, ignoreCase = true)) return true

        // Check manufacturer IDs
        if (manufacturerData.keys.any { it in OURA_MANUFACTURER_IDS }) return true

        // Check device name (from BluetoothDevice or ScanRecord)
        val deviceName = name ?: advertisedName
        if (deviceName?.contains(OURA_NAME_PATTERN, ignoreCase = true) == true) return true

        return false
    }

    /** Returns the Apple continuity type if this is an Apple device, null otherwise */
    fun getAppleContinuityType(): AppleContinuityType? {
        val appleData = manufacturerData[APPLE_MANUFACTURER_ID] ?: return null
        if (appleData.isEmpty()) return null
        return AppleContinuityType.fromByte(appleData[0])
    }

    /** Returns true if this is any Apple device (has Apple manufacturer data with known continuity type) */
    fun isAppleDevice(): Boolean = getAppleContinuityType() != null

    /** Returns true if this is specifically an AirTag (Find My with AirTag status bytes) */
    fun isAirTag(): Boolean {
        val appleData = manufacturerData[APPLE_MANUFACTURER_ID] ?: return false
        if (appleData.size < 3) return false
        if (appleData[0] != AppleContinuityType.FIND_MY.byte) return false
        // AirTags have specific status bytes, other Find My devices don't
        val statusByte = appleData[2]
        return statusByte == 0x10.toByte() || statusByte == 0x50.toByte() ||
               statusByte == 0x90.toByte() || statusByte == 0xD0.toByte()
    }

    /** Returns a detected/inferred device type label based on protocol analysis (not broadcast name) */
    fun getDetectedType(): String? {
        if (isAirTag()) return "AirTag"
        return getAppleContinuityType()?.label
    }

    /** Returns the broadcast display name (advertised or bonded name), not inferred */
    fun getDisplayName(): String? = advertisedName ?: name

    fun getManufacturerNames(): List<String> {
        return manufacturerData.keys.mapNotNull { CompanyDatabase.getCompanyName(it) }
    }
}
