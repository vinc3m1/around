package com.makeramen.around

import androidx.compose.ui.window.ComposeUIViewController
import com.makeramen.around.ble.BleViewModel
import com.makeramen.around.ble.PlatformContext
import com.makeramen.around.ui.ScannerScreen
import com.makeramen.around.ui.theme.AroundTheme

fun MainViewController() = ComposeUIViewController {
    val viewModel = BleViewModel(PlatformContext())
    AroundTheme {
        ScannerScreen(viewModel = viewModel)
    }
}
