package com.makeramen.ouround.ble

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

private const val STALE_DEVICE_THRESHOLD_MS = 300000L // 5 minutes
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

            // Launch periodic sort/cleanup task
            launch {
                while (isActive) {
                    delay(SORT_INTERVAL_MS)
                    removeStaleDevices()
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

    private fun removeStaleDevices() {
        val now = System.currentTimeMillis()

        deviceCache.entries.removeIf { (_, device) ->
            now - device.lastSeen > STALE_DEVICE_THRESHOLD_MS
        }
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
        // Add any new devices not yet in order lists at the end
        val currentOuraAddresses = ouraDeviceHistory.keys
        val newOura = currentOuraAddresses - ouraOrder.toSet()
        val effectiveOuraOrder = ouraOrder.filter { it in currentOuraAddresses } + newOura

        val sortedOura = effectiveOuraOrder.mapNotNull { ouraDeviceHistory[it] }

        val currentOtherAddresses = deviceCache.keys.filter { !ouraDeviceHistory.containsKey(it) }.toSet()
        val newOthers = currentOtherAddresses - otherOrder.toSet()
        val effectiveOtherOrder = otherOrder.filter { it in currentOtherAddresses } + newOthers

        val sortedOthers = effectiveOtherOrder.mapNotNull { deviceCache[it] }

        _uiState.update { it.copy(ouraDevices = sortedOura, otherDevices = sortedOthers) }
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
    }
}
