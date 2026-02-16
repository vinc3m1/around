package com.makeramen.around.ble

import kotlin.time.Clock

// Oura identification constants
private const val OURA_MAC_PREFIX = "A0:38:F8"
private const val OURA_NAME_PATTERN = "Oura"
private val OURA_MANUFACTURER_IDS = setOf(
    690,  // 0x02B2 - Oura Health Oy
    772   // 0x0304 - Oura Health Ltd
)

// Apple identification constants
private const val APPLE_MANUFACTURER_ID = 76  // 0x004C

// Google identification constants
private const val GOOGLE_MANUFACTURER_ID = 224  // 0x00E0
private const val FAST_PAIR_SERVICE_UUID = "0000FE2C-0000-1000-8000-00805F9B34FB"
private const val FMDN_SERVICE_UUID = "0000FEAA-0000-1000-8000-00805F9B34FB"

/** Cache of detected types by MAC address for Apple devices */
object AppleDeviceCache {
    private val detectedTypes = mutableMapOf<String, String>()

    fun getLastKnown(address: String): String? = detectedTypes[address]

    fun update(address: String, detectedType: String) {
        detectedTypes[address] = detectedType
    }

    fun clear() {
        detectedTypes.clear()
    }
}

/** Cache of detected types by MAC address for Google devices */
object GoogleDeviceCache {
    private val detectedTypes = mutableMapOf<String, String>()

    fun getLastKnown(address: String): String? = detectedTypes[address]

    fun update(address: String, detectedType: String) {
        detectedTypes[address] = detectedType
    }

    fun clear() {
        detectedTypes.clear()
    }
}

// Apple Nearby Info action codes (lower nibble of status byte)
private enum class NearbyActionCode(val code: Int, val label: String) {
    UNKNOWN(0x00, "Unknown"),
    DISABLED(0x01, "Disabled"),
    IDLE(0x03, "Idle"),
    AUDIO_LOCKED(0x05, "Audio (Locked)"),
    ACTIVE(0x07, "Active"),
    VIDEO(0x09, "Video"),
    WATCH_UNLOCKED(0x0A, "Watch Unlocked"),
    RECENT_INTERACTION(0x0B, "Recent"),
    DRIVING(0x0D, "Driving"),
    CALL(0x0E, "On Call");

    companion object {
        fun fromCode(code: Int): NearbyActionCode? = entries.find { it.code == code }
    }
}

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
    val advertisedName: String? = null,
    val manufacturerData: Map<Int, ByteArray> = emptyMap(),
    val serviceUuids: List<String> = emptyList(),
    val lastSeen: Long = Clock.System.now().toEpochMilliseconds()
) {
    fun isLikelyOura(): Boolean {
        if (address.startsWith(OURA_MAC_PREFIX, ignoreCase = true)) return true
        if (manufacturerData.keys.any { it in OURA_MANUFACTURER_IDS }) return true
        val deviceName = name ?: advertisedName
        if (deviceName?.contains(OURA_NAME_PATTERN, ignoreCase = true) == true) return true
        return false
    }

    fun getAppleContinuityType(): AppleContinuityType? {
        val appleData = manufacturerData[APPLE_MANUFACTURER_ID] ?: return null
        if (appleData.isEmpty()) return null
        return AppleContinuityType.fromByte(appleData[0])
    }

    fun isAppleDevice(): Boolean = manufacturerData.containsKey(APPLE_MANUFACTURER_ID)

    fun isAirTag(): Boolean {
        val appleData = manufacturerData[APPLE_MANUFACTURER_ID] ?: return false
        if (appleData.size < 3) return false
        if (appleData[0] != AppleContinuityType.FIND_MY.byte) return false
        val statusByte = appleData[2]
        return statusByte == 0x10.toByte() || statusByte == 0x50.toByte() ||
               statusByte == 0x90.toByte() || statusByte == 0xD0.toByte()
    }

    fun getDetectedType(): String? {
        if (!isAppleDevice()) return null

        val currentType = getCurrentDetectedType()

        return if (currentType != null) {
            AppleDeviceCache.update(address, currentType)
            currentType
        } else {
            val lastKnown = AppleDeviceCache.getLastKnown(address)
            if (lastKnown != null) {
                "Unknown [prev: $lastKnown]"
            } else {
                ""
            }
        }
    }

    private fun getCurrentDetectedType(): String? {
        if (isAirTag()) {
            val appleData = manufacturerData[APPLE_MANUFACTURER_ID]!!
            val batteryStatus = when (appleData[2]) {
                0x10.toByte() -> "Full"
                0x50.toByte() -> "Medium"
                0x90.toByte() -> "Low"
                0xD0.toByte() -> "Very Low"
                else -> null
            }
            return if (batteryStatus != null) "AirTag ($batteryStatus)" else "AirTag"
        }

        val continuityType = getAppleContinuityType() ?: return null

        if (continuityType == AppleContinuityType.NEARBY_INFO) {
            val appleData = manufacturerData[APPLE_MANUFACTURER_ID]!!
            if (appleData.size >= 3) {
                val actionCode = (appleData[2].toInt() and 0xFF) and 0x0F
                val actionLabel = NearbyActionCode.fromCode(actionCode)?.label
                if (actionLabel != null) {
                    return "iPhone/Mac ($actionLabel)"
                }
            }
        }

        return continuityType.label
    }

    fun getDisplayName(): String? = advertisedName ?: name

    fun getManufacturerNames(): List<String> {
        return manufacturerData.keys.mapNotNull { CompanyDatabase.getCompanyName(it) }
    }

    // ========== Google Device Detection ==========

    fun isGoogleDevice(): Boolean {
        if (manufacturerData.containsKey(GOOGLE_MANUFACTURER_ID)) return true
        if (FAST_PAIR_SERVICE_UUID in serviceUuids) return true
        if (FMDN_SERVICE_UUID in serviceUuids) return true
        return false
    }

    fun getGoogleDetectedType(): String? {
        if (!isGoogleDevice()) return null

        val currentType = getCurrentGoogleDetectedType()

        return if (currentType != null) {
            GoogleDeviceCache.update(address, currentType)
            currentType
        } else {
            val lastKnown = GoogleDeviceCache.getLastKnown(address)
            if (lastKnown != null) {
                "Unknown [prev: $lastKnown]"
            } else {
                ""
            }
        }
    }

    private fun getCurrentGoogleDetectedType(): String? {
        if (FMDN_SERVICE_UUID in serviceUuids) {
            return "Find My Tag"
        }
        if (FAST_PAIR_SERVICE_UUID in serviceUuids) {
            return "Fast Pair"
        }
        if (manufacturerData.containsKey(GOOGLE_MANUFACTURER_ID)) {
            return "Google Device"
        }
        return null
    }
}
