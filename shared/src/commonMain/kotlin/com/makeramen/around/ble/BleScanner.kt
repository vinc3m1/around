package com.makeramen.around.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface BleScanner {
    val isBluetoothEnabled: Boolean
    val devices: Flow<BleDevice>
}

expect class PlatformContext

expect fun createBleScanner(context: PlatformContext, scope: CoroutineScope): BleScanner
