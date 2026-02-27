package com.makeramen.around

import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.window.ComposeUIViewController
import com.makeramen.around.ble.BleViewModel
import com.makeramen.around.ble.PlatformContext
import com.makeramen.around.ui.ScannerScreen
import com.makeramen.around.ui.theme.AroundTheme
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerAuthorizationNotDetermined

fun MainViewController() = ComposeUIViewController {
    val viewModel = viewModel { BleViewModel(PlatformContext()) }

    LaunchedEffect(Unit) {
        val auth = CBCentralManager.authorization
        if (auth == CBManagerAuthorizationAllowedAlways) {
            viewModel.setPermissionsGranted(true)
            viewModel.startScanning()
        } else if (auth == CBManagerAuthorizationNotDetermined) {
            // CBCentralManager init triggers the system permission prompt.
            // The scanner's callbackFlow creates it when we start scanning.
            // On iOS, set permissions granted and start — the prompt will appear
            // and centralManagerDidUpdateState will handle the actual BLE state.
            viewModel.setPermissionsGranted(true)
            viewModel.startScanning()
        }
    }

    AroundTheme {
        ScannerScreen(viewModel = viewModel)
    }
}
