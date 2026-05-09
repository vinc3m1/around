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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.time.Clock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makeramen.around.ble.BleDevice
import com.makeramen.around.ble.BleViewModel
import com.makeramen.around.ble.ScannerUiState
import com.makeramen.around.ble.SignalStrength
import org.jetbrains.compose.resources.painterResource
import around.shared.generated.resources.Res
import around.shared.generated.resources.ic_launcher_foreground
import androidx.compose.ui.tooling.preview.Preview
import com.makeramen.around.ui.theme.AroundTheme

@Composable
fun ScannerScreen(viewModel: BleViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    ScannerScreenContent(uiState = uiState, onClearDevices = viewModel::clearDevices)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreenContent(
    uiState: ScannerUiState,
    onClearDevices: () -> Unit,
    initialExpanded: Boolean = false
) {
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
    var appleExpanded by rememberSaveable { mutableStateOf(initialExpanded) }
    var googleExpanded by rememberSaveable { mutableStateOf(initialExpanded) }
    var namedExpanded by rememberSaveable { mutableStateOf(initialExpanded) }
    var unnamedExpanded by rememberSaveable { mutableStateOf(initialExpanded) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Around") },
                navigationIcon = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_launcher_foreground),
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
                    val refreshRotation = remember { Animatable(0f) }
                    val scope = rememberCoroutineScope()
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                        tooltip = { PlainTooltip { Text("Clear device list") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = {
                            onClearDevices()
                            scope.launch {
                                refreshRotation.snapTo(0f)
                                refreshRotation.animateTo(
                                    targetValue = 360f,
                                    animationSpec = tween(durationMillis = 500)
                                )
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Clear device list",
                                modifier = Modifier.rotate(refreshRotation.value),
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
    val totalSeconds = (Clock.System.now().toEpochMilliseconds() - lastSeen) / 1000L
    return when {
        totalSeconds < 60L -> "${totalSeconds}s ago"
        totalSeconds < 3600L -> "${totalSeconds / 60L}m ${totalSeconds % 60L}s ago"
        totalSeconds < 86400L -> "${totalSeconds / 3600L}h ${(totalSeconds % 3600L) / 60L}m ago"
        else -> "${totalSeconds / 86400L}d ${(totalSeconds % 86400L) / 3600L}h ago"
    }
}

private const val STALE_GRACE_SECONDS = 5.0
private const val STALE_MAX_SECONDS = 1800.0 // 30 minutes = fully stale
private const val STALE_LOG_DIVISOR = 10.0

private fun staleFraction(lastSeenMs: Long, nowMs: Long): Float {
    val elapsedSeconds = (nowMs - lastSeenMs) / 1000.0
    if (elapsedSeconds <= STALE_GRACE_SECONDS) return 0f
    val adjusted = elapsedSeconds - STALE_GRACE_SECONDS
    val maxAdjusted = STALE_MAX_SECONDS - STALE_GRACE_SECONDS
    return (ln(1.0 + adjusted / STALE_LOG_DIVISOR) / ln(1.0 + maxAdjusted / STALE_LOG_DIVISOR))
        .coerceIn(0.0, 1.0).toFloat()
}

private const val STALE_TICK_MS = 5000L

@Composable
private fun rememberStaleNow(): Long {
    val now by produceState(Clock.System.now().toEpochMilliseconds()) {
        while (true) {
            delay(STALE_TICK_MS)
            value = Clock.System.now().toEpochMilliseconds()
        }
    }
    return now
}

@Composable
fun RelativeTimeText(
    timestamp: Long,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: androidx.compose.ui.graphics.Color
) {
    val text by produceState(formatLastSeen(timestamp), timestamp) {
        while (true) {
            value = formatLastSeen(timestamp)
            delay(1000)
        }
    }
    Text(text = text, modifier = modifier, fontSize = fontSize, color = color)
}

@Composable
fun OuraDeviceCard(device: BleDevice, displayName: String, modifier: Modifier = Modifier) {
    val now = rememberStaleNow()
    val stale = staleFraction(device.lastSeen, now)

    val signalStrength = SignalStrength.fromRssi(device.rssi)
    val transition = updateTransition(targetState = signalStrength, label = "signal_strength")
    val animatedColor by transition.animateColor(label = "color") { it.color }

    val cardColor = lerp(animatedColor, MaterialTheme.colorScheme.surfaceVariant, stale)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
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
                    text = device.displayAddress,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimary
                )

                // Manufacturer name (if known)
                device.getManufacturerName()?.let { name ->
                    Text(
                        text = name,
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
    val now = rememberStaleNow()
    val stale = staleFraction(device.lastSeen, now)

    val signalStrength = SignalStrength.fromRssi(device.rssi)
    val transition = updateTransition(targetState = signalStrength, label = "signal_strength")
    val animatedColor by transition.animateColor(label = "color") { it.color }
    val manufacturerName = device.getManufacturerName()

    val cardColor = lerp(animatedColor, MaterialTheme.colorScheme.surfaceVariant, stale)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
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
                            append(device.displayAddress)
                        }
                        manufacturerName?.let { append(" · $it") }
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
    val now = rememberStaleNow()
    val stale = staleFraction(device.lastSeen, now)

    val signalStrength = SignalStrength.fromRssi(device.rssi)
    val transition = updateTransition(targetState = signalStrength, label = "signal_strength")
    val animatedColor by transition.animateColor(label = "color") { it.color }
    val manufacturerName = device.getManufacturerName()

    val cardColor = lerp(animatedColor, MaterialTheme.colorScheme.surfaceVariant, stale)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
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
                    text = device.displayAddress,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1
                )
                val suffix = buildString {
                    manufacturerName?.let { append(" · $it") }
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

@Preview(heightDp = 1200)
@Composable
fun ScannerScreenPreview() {
    val now = Clock.System.now().toEpochMilliseconds()
    AroundTheme {
        ScannerScreenContent(
            uiState = ScannerUiState(
                isScanning = true,
                permissionsGranted = true,
                bluetoothEnabled = true,
                ouraDevices = listOf(
                    // Very Close, fresh, with txPower
                    BleDevice(
                        name = "Oura Ring 4",
                        address = "A0:38:F8:01:23:45",
                        rssi = -35,
                        txPower = -21,
                        manufacturerData = mapOf(690 to byteArrayOf()),
                        lastSeen = now
                    ),
                    // Close, 30s stale
                    BleDevice(
                        name = "Oura Ring 3",
                        address = "A0:38:F8:67:89:AB",
                        rssi = -60,
                        manufacturerData = mapOf(772 to byteArrayOf()),
                        lastSeen = now - 30_000
                    ),
                    // Far, 10min stale, no name (falls back to "Oura Ring")
                    BleDevice(
                        name = null,
                        address = "A0:38:F8:CD:EF:01",
                        rssi = -80,
                        manufacturerData = mapOf(690 to byteArrayOf()),
                        lastSeen = now - 600_000
                    ),
                    // Very Far, 20min stale
                    BleDevice(
                        advertisedName = "Oura Ring Gen2",
                        name = null,
                        address = "A0:38:F8:23:45:67",
                        rssi = -95,
                        manufacturerData = mapOf(690 to byteArrayOf()),
                        lastSeen = now - 1_200_000
                    ),
                ),
                otherDevices = listOf(
                    // Apple: AirTag (Full battery), Close, fresh
                    BleDevice(
                        name = null,
                        address = "AA:BB:CC:11:22:33",
                        rssi = -55,
                        manufacturerData = mapOf(76 to byteArrayOf(0x12, 0x19, 0x10)),
                        lastSeen = now
                    ),
                    // Apple: iPhone/Mac (Active), Far, 45s stale
                    BleDevice(
                        name = null,
                        address = "AA:BB:CC:44:55:66",
                        rssi = -75,
                        manufacturerData = mapOf(76 to byteArrayOf(0x10, 0x00, 0x07)),
                        lastSeen = now - 45_000
                    ),
                    // Apple: AirPods, Very Far, 5min stale
                    BleDevice(
                        name = null,
                        address = "AA:BB:CC:77:88:99",
                        rssi = -92,
                        manufacturerData = mapOf(76 to byteArrayOf(0x07)),
                        lastSeen = now - 300_000
                    ),
                    // Google: Fast Pair, Close, 10s stale
                    BleDevice(
                        name = null,
                        address = "DD:EE:FF:11:22:33",
                        rssi = -58,
                        serviceUuids = listOf("0000FE2C-0000-1000-8000-00805F9B34FB"),
                        manufacturerData = mapOf(224 to byteArrayOf()),
                        lastSeen = now - 10_000
                    ),
                    // Google: Find My Tag, Far, 2min stale
                    BleDevice(
                        name = null,
                        address = "DD:EE:FF:44:55:66",
                        rssi = -82,
                        serviceUuids = listOf("0000FEAA-0000-1000-8000-00805F9B34FB"),
                        lastSeen = now - 120_000
                    ),
                    // Named: Very Close, fresh
                    BleDevice(
                        name = "JBL Flip 6",
                        address = "11:22:33:44:55:66",
                        rssi = -40,
                        lastSeen = now - 5_000
                    ),
                    // Named: Close, 1min stale, with manufacturer
                    BleDevice(
                        name = "Galaxy Watch5",
                        address = "22:33:44:55:66:77",
                        rssi = -65,
                        manufacturerData = mapOf(117 to byteArrayOf()),
                        lastSeen = now - 60_000
                    ),
                    // Named: Very Far, 15min stale
                    BleDevice(
                        name = "Fitbit Sense",
                        address = "33:44:55:66:77:88",
                        rssi = -95,
                        lastSeen = now - 900_000
                    ),
                    // Unnamed: Far, 20s stale
                    BleDevice(
                        name = null,
                        address = "44:55:66:77:88:99",
                        rssi = -78,
                        lastSeen = now - 20_000
                    ),
                    // Unnamed: Very Far, 20min stale, with manufacturer data
                    BleDevice(
                        name = null,
                        address = "55:66:77:88:99:AA",
                        rssi = -98,
                        manufacturerData = mapOf(301 to byteArrayOf()),
                        lastSeen = now - 1_200_000
                    ),
                )
            ),
            onClearDevices = {},
            initialExpanded = true
        )
    }
}
