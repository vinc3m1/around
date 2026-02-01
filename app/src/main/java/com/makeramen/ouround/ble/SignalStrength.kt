package com.makeramen.ouround.ble

import androidx.compose.ui.graphics.Color

enum class SignalStrength(
    val label: String,
    val color: Color,
    val minRssi: Int,
    val maxRssi: Int
) {
    VERY_CLOSE("Very Close", Color(0xFF4CAF50), -50, 0),
    CLOSE("Close", Color(0xFF8BC34A), -70, -51),
    FAR("Far", Color(0xFFFFC107), -85, -71),
    VERY_FAR("Very Far", Color(0xFFF44336), -100, -86);

    companion object {
        fun fromRssi(rssi: Int): SignalStrength {
            return values().firstOrNull { rssi in it.minRssi..it.maxRssi }
                ?: VERY_FAR
        }
    }
}
