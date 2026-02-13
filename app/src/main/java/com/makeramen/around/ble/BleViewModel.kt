package com.makeramen.around.ble

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val SORT_INTERVAL_MS = 5000L // 5 seconds

data class ScannerUiState(
    val isScanning: Boolean = false,
    val bluetoothEnabled: Boolean = true,
    val permissionsGranted: Boolean = false,
    val ouraDevices: List<BleDevice> = emptyList(),
    val otherDevices: List<BleDevice> = emptyList()
)

class BleViewModel(application: Application) : AndroidViewModel(application) {
    init {
        CompanyDatabase.load(application)
    }

    private val bleScanner = BleScanner(application, viewModelScope)

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val deviceCache = mutableMapOf<String, BleDevice>()
    private val ouraDeviceHistory = mutableMapOf<String, BleDevice>()
    private var scanJob: Job? = null

    // Maintains stable ordering - only updated periodically
    private var ouraOrder: List<String> = emptyList()
    private var otherOrder: List<String> = emptyList()

    fun setPermissionsGranted(granted: Boolean) {
        _uiState.update { it.copy(permissionsGranted = granted) }
    }

    fun startScanning() {
        if (!_uiState.value.permissionsGranted) return
        if (scanJob?.isActive == true) return

        val bluetoothEnabled = bleScanner.isBluetoothEnabled
        _uiState.update { it.copy(bluetoothEnabled = bluetoothEnabled) }

        if (!bluetoothEnabled) return

        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }

            // Immediately show cached devices
            updateDeviceList()

            // Launch periodic sort/cleanup task
            launch {
                while (isActive) {
                    delay(SORT_INTERVAL_MS)
                    resortDeviceLists()
                    updateDeviceList()
                }
            }

            // Collect from shared scan flow - starts when first subscriber, stops when last unsubscribes
            bleScanner.devices
                .catch { /* Scan failed, flow will complete */ }
                .collect { device ->
                    deviceCache[device.address] = device

                    // Cache Oura devices permanently
                    if (device.isLikelyOura()) {
                        ouraDeviceHistory[device.address] = device
                    }

                    // Update data without reordering
                    updateDeviceList()
                }
        }

        scanJob?.invokeOnCompletion {
            _uiState.update { it.copy(isScanning = false) }
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        scanJob = null
    }

    fun clearDevices() {
        deviceCache.clear()
        ouraDeviceHistory.clear()
        ouraOrder = emptyList()
        otherOrder = emptyList()
        _uiState.update { it.copy(ouraDevices = emptyList(), otherDevices = emptyList()) }
    }

    private fun resortDeviceLists() {
        // Re-sort Oura devices: active first, then by RSSI
        ouraOrder = ouraDeviceHistory.values
            .sortedWith(
                compareByDescending<BleDevice> { deviceCache.containsKey(it.address) }
                    .thenByDescending { it.rssi }
            )
            .map { it.address }

        // Re-sort other devices by RSSI
        val others = deviceCache.values.filter { !it.isLikelyOura() }
        otherOrder = others.sortedByDescending { it.rssi }.map { it.address }
    }

    private fun updateDeviceList() {
        // Build lists using stable order, but with fresh data
        // Insert new devices in sorted position by RSSI
        val currentOuraAddresses = ouraDeviceHistory.keys
        val newOura = currentOuraAddresses - ouraOrder.toSet()
        val effectiveOuraOrder = ouraOrder.filter { it in currentOuraAddresses }.toMutableList()
        for (address in newOura) {
            val device = ouraDeviceHistory[address] ?: continue
            val insertIndex = effectiveOuraOrder.indexOfFirst { addr ->
                val existing = ouraDeviceHistory[addr]
                existing != null && existing.rssi < device.rssi
            }.takeIf { it >= 0 } ?: effectiveOuraOrder.size
            effectiveOuraOrder.add(insertIndex, address)
        }
        ouraOrder = effectiveOuraOrder

        val sortedOura = effectiveOuraOrder.mapNotNull { ouraDeviceHistory[it] }

        val currentOtherAddresses = deviceCache.keys.filter { !ouraDeviceHistory.containsKey(it) }.toSet()
        val newOthers = currentOtherAddresses - otherOrder.toSet()
        val effectiveOtherOrder = otherOrder.filter { it in currentOtherAddresses }.toMutableList()
        for (address in newOthers) {
            val device = deviceCache[address] ?: continue
            val insertIndex = effectiveOtherOrder.indexOfFirst { addr ->
                val existing = deviceCache[addr]
                existing != null && existing.rssi < device.rssi
            }.takeIf { it >= 0 } ?: effectiveOtherOrder.size
            effectiveOtherOrder.add(insertIndex, address)
        }
        otherOrder = effectiveOtherOrder

        val sortedOthers = effectiveOtherOrder.mapNotNull { deviceCache[it] }

        _uiState.update { it.copy(ouraDevices = sortedOura, otherDevices = sortedOthers) }
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
    }
}
