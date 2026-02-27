package com.makeramen.around

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makeramen.around.ble.BleViewModel
import com.makeramen.around.ble.PlatformContext
import com.makeramen.around.ui.ScannerScreen
import com.makeramen.around.ui.theme.AroundTheme
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val body = document.body ?: return
    ComposeViewport(body) {
        val viewModel = viewModel { BleViewModel(PlatformContext()) }
        LaunchedEffect(Unit) {
            viewModel.setPermissionsGranted(true)
            viewModel.startScanning()
        }
        AroundTheme {
            ScannerScreen(viewModel = viewModel)
        }
    }
}
