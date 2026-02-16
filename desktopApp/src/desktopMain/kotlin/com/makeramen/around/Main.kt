package com.makeramen.around

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import around.shared.generated.resources.Res
import around.shared.generated.resources.ic_launcher
import com.makeramen.around.ble.BleViewModel
import com.makeramen.around.ble.PlatformContext
import com.makeramen.around.ui.ScannerScreen
import com.makeramen.around.ui.theme.AroundTheme
import org.jetbrains.compose.resources.painterResource

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Around",
        icon = painterResource(Res.drawable.ic_launcher),
    ) {
        val viewModel = remember { BleViewModel(PlatformContext()) }
        AroundTheme {
            ScannerScreen(viewModel = viewModel)
        }
    }
}
