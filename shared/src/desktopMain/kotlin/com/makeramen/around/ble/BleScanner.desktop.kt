package com.makeramen.around.ble

import com.juul.kable.ExperimentalApi
import com.juul.kable.Scanner
import com.juul.kable.logs.Logging
import com.makeramen.around.logDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn

actual class PlatformContext

actual fun createBleScanner(context: PlatformContext, scope: CoroutineScope): BleScanner {
    return KableBleScanner(scope)
}

@OptIn(ExperimentalApi::class, kotlin.uuid.ExperimentalUuidApi::class)
private class KableBleScanner(scope: CoroutineScope) : BleScanner {

    private val scanner = Scanner {
        logging { level = Logging.Level.Warnings }
    }

    override val isBluetoothEnabled: Boolean = true

    override val devices: Flow<BleDevice> = scanner.advertisements
        .map { advertisement ->
            val mfrData = buildMap {
                advertisement.manufacturerData?.let { mfr ->
                    put(mfr.code, mfr.data)
                }
            }
            BleDevice(
                name = advertisement.peripheralName,
                address = advertisement.identifier.toString(),
                rssi = advertisement.rssi,
                txPower = advertisement.txPower,
                advertisedName = advertisement.name,
                manufacturerData = mfrData,
                serviceUuids = advertisement.uuids.map { it.toString().uppercase() },
            )
        }
        .onEach { device ->
            if (device.isLikelyOura()) {
                logDebug("BleScanner") {
                    "Oura candidate: ${device.name}, addr=${device.address}, mfr=${device.manufacturerData.keys}"
                }
            }
        }
        .catch { e ->
            logDebug("BleScanner") { "Scan error: ${e.message}" }
        }
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(),
            replay = 0,
        )
}
