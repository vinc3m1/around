package com.makeramen.around.ble

import com.makeramen.around.logDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    return IosBleScanner()
}

private class IosBleScanner : BleScanner {

    private val _devices = MutableSharedFlow<BleDevice>(extraBufferCapacity = 64)
    override val devices: Flow<BleDevice> = _devices.asSharedFlow()

    private val delegate = object : NSObject(), CBCentralManagerDelegateProtocol {

        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            logDebug("BleScanner") { "Bluetooth state: ${central.state}" }
            if (central.state == CBManagerStatePoweredOn) {
                central.scanForPeripheralsWithServices(
                    serviceUUIDs = null,
                    options = mapOf<Any?, Any>(
                        CBCentralManagerScanOptionAllowDuplicatesKey to NSNumber(bool = true)
                    )
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

            val manufacturerData = parseManufacturerData(
                advertisementData[CBAdvertisementDataManufacturerDataKey] as? NSData
            )

            @Suppress("UNCHECKED_CAST")
            val serviceUuids = (advertisementData[CBAdvertisementDataServiceUUIDsKey] as? List<CBUUID>)
                ?.map { it.UUIDString }
                ?: emptyList()

            val txPower = (advertisementData[CBAdvertisementDataTxPowerLevelKey] as? NSNumber)?.intValue

            val device = BleDevice(
                name = name,
                address = address,
                displayAddress = address.substringBefore('-'),
                rssi = rssi,
                txPower = txPower,
                advertisedName = advertisedName,
                manufacturerData = manufacturerData,
                serviceUuids = serviceUuids
            )

            if (device.isLikelyOura()) {
                logDebug("BleScanner") { "Oura candidate: $device, mfr=${device.manufacturerData.keys}, uuids=${device.serviceUuids}" }
            }
            _devices.tryEmit(device)
        }
    }

    // Create CBCentralManager eagerly — this triggers the permission prompt
    private val centralManager = CBCentralManager(delegate = delegate, queue = null)

    // Only report disabled for explicitly-off states. The initial state is
    // CBManagerStateUnknown until the delegate callback fires, so treat
    // unknown/resetting as enabled to avoid blocking startScanning().
    override val isBluetoothEnabled: Boolean
        get() = centralManager.state != CBManagerStatePoweredOff &&
                centralManager.state != CBManagerStateUnsupported &&
                centralManager.state != CBManagerStateUnauthorized

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
