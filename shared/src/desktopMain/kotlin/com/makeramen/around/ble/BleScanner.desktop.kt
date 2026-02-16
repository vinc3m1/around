package com.makeramen.around.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

actual class PlatformContext

actual fun createBleScanner(context: PlatformContext, scope: CoroutineScope): BleScanner {
    return NoOpBleScanner()
}

private class NoOpBleScanner : BleScanner {
    override val isBluetoothEnabled: Boolean = false
    override val devices: Flow<BleDevice> = emptyFlow()
}
