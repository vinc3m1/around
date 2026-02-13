package com.makeramen.around.ui

import android.os.ParcelUuid
import com.makeramen.around.ble.BleDevice
import com.makeramen.around.ble.ScannerUiState

object TestFixtures {

    // ========== Individual Device Factories ==========

    fun ouraRingByName(rssi: Int = -55) = BleDevice(
        name = null,
        address = "A0:38:F8:01:02:03",
        rssi = rssi,
        advertisedName = "Oura_ABCD",
        manufacturerData = mapOf(690 to byteArrayOf()),
        lastSeen = System.currentTimeMillis()
    )

    fun ouraRingByMac(rssi: Int = -72) = BleDevice(
        name = null,
        address = "A0:38:F8:04:05:06",
        rssi = rssi,
        manufacturerData = mapOf(772 to byteArrayOf()),
        lastSeen = System.currentTimeMillis()
    )

    fun airTag(rssi: Int = -65) = BleDevice(
        name = null,
        address = "11:22:33:44:55:66",
        rssi = rssi,
        manufacturerData = mapOf(76 to byteArrayOf(0x12, 0x00, 0x10)),
        lastSeen = System.currentTimeMillis()
    )

    fun iPhone(rssi: Int = -58) = BleDevice(
        name = null,
        address = "22:33:44:55:66:77",
        rssi = rssi,
        manufacturerData = mapOf(76 to byteArrayOf(0x10, 0x00, 0x07)),
        lastSeen = System.currentTimeMillis()
    )

    fun airPods(rssi: Int = -50) = BleDevice(
        name = null,
        address = "33:44:55:66:77:88",
        rssi = rssi,
        manufacturerData = mapOf(76 to byteArrayOf(0x07, 0x01)),
        lastSeen = System.currentTimeMillis()
    )

    fun googleFastPair(rssi: Int = -70) = BleDevice(
        name = null,
        address = "44:55:66:77:88:99",
        rssi = rssi,
        serviceUuids = listOf(
            ParcelUuid.fromString("0000FE2C-0000-1000-8000-00805F9B34FB")
        ),
        lastSeen = System.currentTimeMillis()
    )

    fun namedDevice(name: String = "JBL Speaker", rssi: Int = -60) = BleDevice(
        name = null,
        address = "55:66:77:88:99:AA",
        rssi = rssi,
        advertisedName = name,
        lastSeen = System.currentTimeMillis()
    )

    fun unnamedDevice(rssi: Int = -80) = BleDevice(
        name = null,
        address = "66:77:88:99:AA:BB",
        rssi = rssi,
        lastSeen = System.currentTimeMillis()
    )

    // ========== Pre-built UI State Scenarios ==========

    fun emptyScanning() = ScannerUiState(
        isScanning = true,
        permissionsGranted = true,
        bluetoothEnabled = true
    )

    fun permissionsRequired() = ScannerUiState(
        permissionsGranted = false
    )

    fun bluetoothDisabled() = ScannerUiState(
        permissionsGranted = true,
        bluetoothEnabled = false
    )

    fun ouraFound() = ScannerUiState(
        isScanning = true,
        permissionsGranted = true,
        bluetoothEnabled = true,
        ouraDevices = listOf(ouraRingByName(), ouraRingByMac())
    )

    /** Full mixed scan with all device types — good for Play Store screenshots */
    fun fullMixedScan() = ScannerUiState(
        isScanning = true,
        permissionsGranted = true,
        bluetoothEnabled = true,
        ouraDevices = listOf(
            ouraRingByName(rssi = -45),
            ouraRingByMac(rssi = -72)
        ),
        otherDevices = listOf(
            airTag(),
            iPhone(),
            airPods(),
            googleFastPair(),
            namedDevice(),
            unnamedDevice()
        )
    )
}
