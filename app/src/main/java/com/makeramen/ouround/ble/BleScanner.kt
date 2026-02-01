package com.makeramen.ouround.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import logcat.logcat
import android.util.SparseArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn

class BleScanner(context: Context, scope: CoroutineScope) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    val devices: Flow<BleDevice> = createScanFlow().shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(),
        replay = 0
    )

    @SuppressLint("MissingPermission")
    private fun createScanFlow(): Flow<BleDevice> = callbackFlow {
        if (bluetoothLeScanner == null) {
            close()
            return@callbackFlow
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setLegacy(false) // Also receive BLE 5.0 extended advertisements
            .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED) // Scan on all PHYs (1M, 2M, Coded)
            .build()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val scanRecord = result.scanRecord
                val txPower = result.txPower.takeIf { it != ScanResult.TX_POWER_NOT_PRESENT }

                val device = BleDevice(
                    name = result.device.name,
                    address = result.device.address,
                    rssi = result.rssi,
                    txPower = txPower,
                    advertisedName = scanRecord?.deviceName,
                    manufacturerData = scanRecord?.manufacturerSpecificData?.toMap() ?: emptyMap(),
                    serviceUuids = scanRecord?.serviceUuids ?: emptyList()
                )

                if (device.isLikelyOura()) {
                    logcat { "Oura candidate: $device, mfr=${device.manufacturerData.keys}, uuids=${device.serviceUuids}" }
                }
                trySend(device)
            }

            override fun onScanFailed(errorCode: Int) {
                close(Exception("Scan failed with error code: $errorCode"))
            }
        }

        logcat { "Starting scan" }
        try {
            bluetoothLeScanner.startScan(null, settings, scanCallback)
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        }

        awaitClose {
            logcat { "Stopping scan" }
            try {
                bluetoothLeScanner.stopScan(scanCallback)
            } catch (e: SecurityException) {
                // Ignore
            }
        }
    }

    private fun SparseArray<ByteArray>.toMap(): Map<Int, ByteArray> {
        val map = mutableMapOf<Int, ByteArray>()
        for (i in 0 until size()) {
            map[keyAt(i)] = valueAt(i)
        }
        return map
    }
}
