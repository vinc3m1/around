package com.makeramen.ouround.ble

import android.os.ParcelUuid

// Oura identification constants
private const val OURA_MAC_PREFIX = "A0:38:F8"
private const val OURA_NAME_PATTERN = "Oura"
private val OURA_MANUFACTURER_IDS = setOf(
    690,  // 0x02B2 - Oura Health Oy
    772   // 0x0304 - Oura Health Ltd
)

// Apple AirTag identification constants
private const val APPLE_MANUFACTURER_ID = 76  // 0x004C
private const val FIND_MY_TYPE: Byte = 0x12

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

    fun isAirTag(): Boolean {
        val appleData = manufacturerData[APPLE_MANUFACTURER_ID] ?: return false
        // Check for FindMy type byte at start of manufacturer data
        return appleData.isNotEmpty() && appleData[0] == FIND_MY_TYPE
    }

    fun getDisplayName(): String? {
        if (isAirTag()) return "AirTag"
        return advertisedName ?: name
    }

    fun getManufacturerNames(): List<String> {
        return manufacturerData.keys.mapNotNull { CompanyDatabase.getCompanyName(it) }
    }
}
