package com.makeramen.around.ble

import com.makeramen.around.logDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreBluetooth.*
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.darwin.NSObject
import platform.posix.memcpy

actual class PlatformContext

actual fun createBleScanner(context: PlatformContext, scope: CoroutineScope): BleScanner {
    return IosBleScanner(scope)
}

private class IosBleScanner(scope: CoroutineScope) : BleScanner {

    private var centralManager: CBCentralManager? = null

    override val isBluetoothEnabled: Boolean
        get() = centralManager?.state == CBManagerStatePoweredOn

    override val devices: Flow<BleDevice> = createScanFlow().shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(),
        replay = 0
    )

    private fun createScanFlow(): Flow<BleDevice> = callbackFlow {
        val delegate = object : NSObject(), CBCentralManagerDelegateProtocol {

            override fun centralManagerDidUpdateState(central: CBCentralManager) {
                logDebug("BleScanner") { "Bluetooth state: ${central.state}" }
                if (central.state == CBManagerStatePoweredOn) {
                    central.scanForPeripheralsWithServices(
                        serviceUUIDs = null,
                        options = mapOf(CBCentralManagerScanOptionAllowDuplicatesKey to true)
                    )
                }
            }

            override fun centralManager(
                central: CBCentralManager,
                didDiscoverPeripheral: CBPeripheral,
                advertisementData: Map<Any?, *>,
                RSSI: NSNumber
            ) {
                val peripheral = didDiscoverPeripheral
                val rssi = RSSI.intValue
                val name = peripheral.name
                val address = peripheral.identifier.UUIDString
                val advertisedName = advertisementData[CBAdvertisementDataLocalNameKey] as? String

                // Parse manufacturer data
                val manufacturerData = parseManufacturerData(
                    advertisementData[CBAdvertisementDataManufacturerDataKey] as? NSData
                )

                // Parse service UUIDs
                @Suppress("UNCHECKED_CAST")
                val serviceUuids = (advertisementData[CBAdvertisementDataServiceUUIDsKey] as? List<CBUUID>)
                    ?.map { it.UUIDString }
                    ?: emptyList()

                // Parse TX power
                val txPower = (advertisementData[CBAdvertisementDataTxPowerLevelKey] as? NSNumber)?.intValue

                val device = BleDevice(
                    name = name,
                    address = address,
                    rssi = rssi,
                    txPower = txPower,
                    advertisedName = advertisedName,
                    manufacturerData = manufacturerData,
                    serviceUuids = serviceUuids
                )

                if (device.isLikelyOura()) {
                    logDebug("BleScanner") { "Oura candidate: $device, mfr=${device.manufacturerData.keys}, uuids=${device.serviceUuids}" }
                }
                trySend(device)
            }
        }

        logDebug("BleScanner") { "Starting scan" }
        val manager = CBCentralManager(delegate = delegate, queue = null)
        centralManager = manager

        awaitClose {
            logDebug("BleScanner") { "Stopping scan" }
            manager.stopScan()
            centralManager = null
        }
    }

    /**
     * Parse iOS manufacturer data (NSData) into a map of company ID to payload.
     * iOS packs manufacturer data as: [companyId: 2 bytes little-endian] [payload: remaining bytes]
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun parseManufacturerData(data: NSData?): Map<Int, ByteArray> {
        if (data == null || data.length < 2u) return emptyMap()

        val bytes = ByteArray(data.length.toInt())
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }

        val companyId = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
        val payload = if (bytes.size > 2) bytes.copyOfRange(2, bytes.size) else byteArrayOf()

        return mapOf(companyId to payload)
    }
}
