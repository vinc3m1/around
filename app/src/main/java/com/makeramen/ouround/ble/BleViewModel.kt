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
private const val CLEANUP_INTERVAL_MS = 1000L // 1 second

data class ScannerUiState(
    val isScanning: Boolean = false,
    val bluetoothEnabled: Boolean = true,
    val permissionsGranted: Boolean = false,
    val ouraDevices: List<BleDevice> = emptyList(),
    val otherDevices: List<BleDevice> = emptyList()
)

class BleViewModel(application: Application) : AndroidViewModel(application) {
    private val bleScanner = BleScanner(application, viewModelScope)

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val deviceCache = mutableMapOf<String, BleDevice>()
    private val ouraDeviceHistory = mutableMapOf<String, BleDevice>()
    private var scanJob: Job? = null

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

            // Launch cleanup task
            launch {
                while (isActive) {
                    delay(CLEANUP_INTERVAL_MS)
                    removeStaleDevices()
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

    private fun updateDeviceList() {
        val (oura, others) = deviceCache.values.partition { it.isLikelyOura() }

        // Merge current Oura devices with history
        val allOuraDevices = ouraDeviceHistory.values.toList()

        val sortedOura = allOuraDevices.sortedWith(
            compareByDescending<BleDevice> { deviceCache.containsKey(it.address) }
                .thenByDescending { it.rssi }
        )
        val sortedOthers = others.sortedByDescending { it.rssi }

        _uiState.update { it.copy(ouraDevices = sortedOura, otherDevices = sortedOthers) }
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
    }
}
