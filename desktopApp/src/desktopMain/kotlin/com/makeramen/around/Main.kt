package com.makeramen.around

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.makeramen.around.ble.BleViewModel
import com.makeramen.around.ble.PlatformContext
import com.makeramen.around.ui.ScannerScreen
import com.makeramen.around.ui.theme.AroundTheme

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Around"
    ) {
        val viewModel = remember { BleViewModel(PlatformContext()) }
        AroundTheme {
            ScannerScreen(viewModel = viewModel)
        }
    }
}
