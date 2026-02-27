@file:OptIn(ExperimentalWasmJsInterop::class)

package com.makeramen.around.ble

import com.makeramen.around.logDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.shareIn
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsNumber
import kotlin.js.JsString
import kotlin.js.Promise

actual class PlatformContext

actual fun createBleScanner(context: PlatformContext, scope: CoroutineScope): BleScanner {
    return if (isWebBluetoothAvailable()) {
        WebBluetoothScanner(scope)
    } else {
        NoOpBleScanner()
    }
}

// External declarations for Web Bluetooth API

@JsFun("() => typeof navigator !== 'undefined' && navigator.bluetooth !== undefined")
private external fun isWebBluetoothAvailable(): Boolean

@JsFun("(opts) => navigator.bluetooth.requestLEScan(opts)")
private external fun requestLEScan(options: JsAny): Promise<JsAny>

@JsFun("(scan) => scan.stop()")
private external fun stopScan(scan: JsAny)

@JsFun("(cb) => navigator.bluetooth.addEventListener('advertisementreceived', cb)")
private external fun addAdvertisementListener(callback: (JsAny) -> Unit)

@JsFun("(cb) => navigator.bluetooth.removeEventListener('advertisementreceived', cb)")
private external fun removeAdvertisementListener(callback: (JsAny) -> Unit)

@JsFun("() => ({ acceptAllAdvertisements: true, keepRepeatedDevices: true })")
private external fun createScanOptions(): JsAny

// Accessors for BluetoothAdvertisingEvent properties
@JsFun("(e) => e.device ? (e.device.id || '') : ''")
private external fun getDeviceId(event: JsAny): String

@JsFun("(e) => e.device ? (e.device.name || null) : null")
private external fun getDeviceName(event: JsAny): String?

@JsFun("(e) => e.name || null")
private external fun getAdvertisedName(event: JsAny): String?

@JsFun("(e) => e.rssi !== undefined && e.rssi !== null ? e.rssi : -127")
private external fun getRssi(event: JsAny): Int

@JsFun("(e) => e.txPower !== undefined && e.txPower !== null ? e.txPower : -999")
private external fun getTxPower(event: JsAny): Int

// Manufacturer data is a BluetoothManufacturerDataMap (Map-like, keyed by company ID)
@JsFun("""(e) => {
    if (!e.manufacturerData) return [];
    const result = [];
    e.manufacturerData.forEach((value, key) => {
        const bytes = new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
        result.push({ key: key, value: Array.from(bytes) });
    });
    return result;
}""")
private external fun getManufacturerDataEntries(event: JsAny): JsArray<JsAny>

@JsFun("(entry) => entry.key")
private external fun getMfrEntryKey(entry: JsAny): Int

@JsFun("(entry) => entry.value")
private external fun getMfrEntryValue(entry: JsAny): JsArray<JsNumber>

// Helper to await a JS Promise from Kotlin/WASM
@JsFun("(promise, onSuccess, onError) => promise.then(onSuccess, onError)")
private external fun <T : JsAny?> thenCatch(promise: Promise<T>, onSuccess: (T) -> Unit, onError: (JsAny) -> Unit)

private suspend fun <T : JsAny?> Promise<T>.awaitResult(): T = suspendCoroutine { cont ->
    thenCatch(
        this,
        onSuccess = { cont.resume(it) },
        onError = { cont.resumeWithException(RuntimeException("Promise rejected")) }
    )
}

// Service UUIDs from the advertisement
@JsFun("""(e) => {
    if (!e.uuids) return [];
    return Array.from(e.uuids).map(u => u.toString().toUpperCase());
}""")
private external fun getServiceUuids(event: JsAny): JsArray<JsString>

private class WebBluetoothScanner(scope: CoroutineScope) : BleScanner {

    override val isBluetoothEnabled: Boolean
        get() = isWebBluetoothAvailable()

    override val devices: Flow<BleDevice> = createScanFlow().shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(),
        replay = 0
    )

    private fun createScanFlow(): Flow<BleDevice> = callbackFlow {
        val options = createScanOptions()

        val callback: (JsAny) -> Unit = { event ->
            val device = parseAdvertisement(event)
            if (device != null) {
                if (device.isLikelyOura()) {
                    logDebug("WebBle") { "Oura candidate: $device" }
                }
                trySend(device)
            }
        }

        logDebug("WebBle") { "Starting scan" }
        addAdvertisementListener(callback)

        var scan: JsAny? = null
        try {
            scan = requestLEScan(options).awaitResult()
        } catch (e: Throwable) {
            logDebug("WebBle") { "requestLEScan failed: ${e.message}" }
            removeAdvertisementListener(callback)
            close(e)
            return@callbackFlow
        }

        awaitClose {
            logDebug("WebBle") { "Stopping scan" }
            scan?.let { stopScan(it) }
            removeAdvertisementListener(callback)
        }
    }

    private fun parseAdvertisement(event: JsAny): BleDevice? {
        val deviceId = getDeviceId(event)
        if (deviceId.isEmpty()) return null

        val rssi = getRssi(event)
        val txPowerRaw = getTxPower(event)
        val txPower = if (txPowerRaw == -999) null else txPowerRaw

        // Parse manufacturer data
        val mfrEntries = getManufacturerDataEntries(event)
        val manufacturerData = mutableMapOf<Int, ByteArray>()
        for (i in 0 until mfrEntries.length) {
            val entry = mfrEntries[i]!!
            val key = getMfrEntryKey(entry)
            val valueArray = getMfrEntryValue(entry)
            val bytes = ByteArray(valueArray.length) { j ->
                valueArray[j]!!.toInt().toByte()
            }
            manufacturerData[key] = bytes
        }

        // Parse service UUIDs
        val uuidsArray = getServiceUuids(event)
        val serviceUuids = buildList {
            for (i in 0 until uuidsArray.length) {
                add(uuidsArray[i]!!.toString())
            }
        }

        return BleDevice(
            name = getDeviceName(event),
            address = deviceId,
            rssi = rssi,
            txPower = txPower,
            advertisedName = getAdvertisedName(event),
            manufacturerData = manufacturerData,
            serviceUuids = serviceUuids
        )
    }
}

private class NoOpBleScanner : BleScanner {
    override val isBluetoothEnabled: Boolean = false
    override val devices: Flow<BleDevice> = emptyFlow()
}
