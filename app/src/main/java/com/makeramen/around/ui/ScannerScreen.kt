package com.makeramen.around.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import com.makeramen.around.R
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makeramen.around.ble.BleDevice
import com.makeramen.around.ble.BleViewModel
import com.makeramen.around.ble.CompanyDatabase
import com.makeramen.around.ble.SignalStrength

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(viewModel: BleViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // Map Oura devices with display name (default to "Oura Ring" if identified without name)
    val ouraDevices = uiState.ouraDevices.map { device ->
        device to (device.getDisplayName() ?: "Oura Ring")
    }

    // Separate Apple devices from other devices
    val appleDevices = uiState.otherDevices
        .filter { it.isAppleDevice() }
        .map { device -> device to device.getDetectedType()!! }

    // Separate Google devices from remaining
    val googleDevices = uiState.otherDevices
        .filter { !it.isAppleDevice() && it.isGoogleDevice() }
        .map { device -> device to device.getGoogleDetectedType()!! }

    // Remaining non-Apple, non-Google devices
    val otherDevices = uiState.otherDevices.filter { !it.isAppleDevice() && !it.isGoogleDevice() }

    // Split other devices into named and unnamed
    val namedDevices = otherDevices.mapNotNull { device ->
        device.getDisplayName()?.let { device to it }
    }
    val unnamedDevices = otherDevices.filter { it.getDisplayName() == null }

    // Collapsible state - collapsed by default
    var appleExpanded by rememberSaveable { mutableStateOf(false) }
    var googleExpanded by rememberSaveable { mutableStateOf(false) }
    var namedExpanded by rememberSaveable { mutableStateOf(false) }
    var unnamedExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Around") },
                navigationIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Clear device list") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = { viewModel.clearDevices() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear device list",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Status Messages
            if (!uiState.permissionsGranted) {
                item(key = "permission_error") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "Bluetooth permissions required. Please grant permissions to scan for devices.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else if (!uiState.bluetoothEnabled) {
                item(key = "bluetooth_error") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "Bluetooth is disabled. Please enable Bluetooth to scan for devices.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else if (uiState.isScanning && ouraDevices.isEmpty() && uiState.otherDevices.isEmpty()) {
                item(key = "scanning") {
                    Text(
                        text = "Scanning...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Oura Devices Section Header
            item(key = "oura_header") {
                Text(
                    text = "Oura Devices (${ouraDevices.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Oura Devices
            if (ouraDevices.isEmpty()) {
                item(key = "oura_empty") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "No Oura devices found yet",
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(ouraDevices, key = { (device, _) -> device.address }) { (device, displayName) ->
                    OuraDeviceCard(device = device, displayName = displayName, modifier = Modifier.animateItem())
                }
            }

            // Named Devices Section (collapsible)
            if (namedDevices.isNotEmpty()) {
                item(key = "named_header") {
                    CollapsibleHeader(
                        title = "Named Devices (${namedDevices.size})",
                        expanded = namedExpanded,
                        onToggle = { namedExpanded = !namedExpanded }
                    )
                }

                if (namedExpanded) {
                    items(namedDevices, key = { (device, _) -> device.address }) { (device, displayName) ->
                        CompactDeviceCard(device = device, displayName = displayName, modifier = Modifier.animateItem())
                    }
                }
            }

            // Apple Devices Section (collapsible)
            if (appleDevices.isNotEmpty()) {
                item(key = "apple_header") {
                    CollapsibleHeader(
                        title = "Apple Devices (${appleDevices.size})",
                        expanded = appleExpanded,
                        onToggle = { appleExpanded = !appleExpanded }
                    )
                }

                if (appleExpanded) {
                    items(appleDevices, key = { (device, _) -> device.address }) { (device, detectedType) ->
                        UnnamedDeviceCard(device = device, detectedType = detectedType, modifier = Modifier.animateItem())
                    }
                }
            }

            // Google Devices Section (collapsible)
            if (googleDevices.isNotEmpty()) {
                item(key = "google_header") {
                    CollapsibleHeader(
                        title = "Google Devices (${googleDevices.size})",
                        expanded = googleExpanded,
                        onToggle = { googleExpanded = !googleExpanded }
                    )
                }

                if (googleExpanded) {
                    items(googleDevices, key = { (device, _) -> device.address }) { (device, detectedType) ->
                        UnnamedDeviceCard(device = device, detectedType = detectedType, modifier = Modifier.animateItem())
                    }
                }
            }

            // Unnamed Devices Section (collapsible)
            if (unnamedDevices.isNotEmpty()) {
                item(key = "unnamed_header") {
                    CollapsibleHeader(
                        title = "Unnamed Devices (${unnamedDevices.size})",
                        expanded = unnamedExpanded,
                        onToggle = { unnamedExpanded = !unnamedExpanded }
                    )
                }

                if (unnamedExpanded) {
                    items(unnamedDevices, key = { it.address }) { device ->
                        UnnamedDeviceCard(device = device, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsibleHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "arrow_rotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            modifier = Modifier.rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatLastSeen(lastSeen: Long): String {
    val totalSeconds = (System.currentTimeMillis() - lastSeen) / 1000
    return when {
        totalSeconds < 60 -> "${totalSeconds}s ago"
        totalSeconds < 3600 -> "${totalSeconds / 60}m ${totalSeconds % 60}s ago"
        totalSeconds < 86400 -> "${totalSeconds / 3600}h ${(totalSeconds % 3600) / 60}m ago"
        else -> "${totalSeconds / 86400}d ${(totalSeconds % 86400) / 3600}h ago"
    }
}

@Composable
fun RelativeTimeText(
    timestamp: Long,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: androidx.compose.ui.graphics.Color
) {
    var text by remember { mutableStateOf(formatLastSeen(timestamp)) }

    LaunchedEffect(timestamp) {
        while (true) {
            text = formatLastSeen(timestamp)
            delay(1000)
        }
    }

    Text(text = text, modifier = modifier, fontSize = fontSize, color = color)
}

@Composable
fun OuraDeviceCard(device: BleDevice, displayName: String, modifier: Modifier = Modifier) {
    val signalStrength = SignalStrength.fromRssi(device.rssi)
    val transition = updateTransition(targetState = signalStrength, label = "signal_strength")
    val animatedColor by transition.animateColor(label = "color") { it.color }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = animatedColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Device Name
                Text(
                    text = displayName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )

                // MAC Address
                Text(
                    text = device.address,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimary
                )

                // Manufacturer IDs (if any)
                if (device.manufacturerData.isNotEmpty()) {
                    Text(
                        text = "MFR: ${device.manufacturerData.keys.joinToString { id ->
                            val name = CompanyDatabase.getCompanyName(id)
                            if (name != null) "0x${id.toString(16)} $name" else "0x${id.toString(16)}"
                        }}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                // Distance Label
                Text(
                    text = signalStrength.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            // RSSI Value and Last Seen
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${device.rssi} dBm",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                device.txPower?.let { tx ->
                    Text(
                        text = "TX: $tx dBm",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                RelativeTimeText(
                    timestamp = device.lastSeen,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun CompactDeviceCard(device: BleDevice, displayName: String, modifier: Modifier = Modifier) {
    val signalStrength = SignalStrength.fromRssi(device.rssi)
    val transition = updateTransition(targetState = signalStrength, label = "signal_strength")
    val animatedColor by transition.animateColor(label = "color") { it.color }
    val manufacturerNames = device.getManufacturerNames()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = animatedColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Device Name
                Text(
                    text = displayName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )

                // MAC Address and Manufacturer on same line or tight
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                            append(device.address)
                        }
                        if (manufacturerNames.isNotEmpty()) {
                            append(" · ${manufacturerNames.joinToString()}")
                        }
                    },
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            // RSSI Value and Last Seen
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${device.rssi}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                RelativeTimeText(
                    timestamp = device.lastSeen,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun UnnamedDeviceCard(device: BleDevice, detectedType: String? = null, modifier: Modifier = Modifier) {
    val signalStrength = SignalStrength.fromRssi(device.rssi)
    val transition = updateTransition(targetState = signalStrength, label = "signal_strength")
    val animatedColor by transition.animateColor(label = "color") { it.color }
    val manufacturerNames = device.getManufacturerNames()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = animatedColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Device info: MAC (stable) + manufacturer and detected type (marquee if overflow)
            Row(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.address,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1
                )
                val suffix = buildString {
                    if (manufacturerNames.isNotEmpty()) {
                        append(" · ${manufacturerNames.joinToString()}")
                    }
                    detectedType?.let { append(" · $it") }
                }
                if (suffix.isNotEmpty()) {
                    Text(
                        text = suffix,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }

            // Last Seen and RSSI on same row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RelativeTimeText(
                    timestamp = device.lastSeen,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = "${device.rssi}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
