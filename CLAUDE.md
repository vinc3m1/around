# Around

Kotlin Multiplatform app for finding Oura rings via BLE scanning. Targets Android, iOS, and Desktop.

## Build

```bash
./gradlew :androidApp:assembleDebug    # Android Debug APK
./gradlew :androidApp:installDebug     # Install to connected device
./gradlew :shared:desktopTest          # Run unit tests (JVM)
./gradlew :desktopApp:run              # Run desktop app
./gradlew :androidApp:connectedAndroidTest  # Run instrumented tests
./gradlew :shared:linkDebugFrameworkIosArm64       # Build iOS framework (device)
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64  # Build iOS framework (simulator)
```

iOS app is built via Xcode — open `iosApp/iosApp.xcodeproj`.

## Project Structure

```
shared/src/
├── commonMain/kotlin/com/makeramen/around/
│   ├── Platform.kt              # expect: logging
│   ├── ble/
│   │   ├── BleScanner.kt       # BLE scanner interface + expect factory
│   │   ├── BleViewModel.kt     # UI state management (KMP ViewModel)
│   │   ├── BleDevice.kt        # Device data model + detection logic
│   │   ├── SignalStrength.kt   # RSSI to signal strength mapping
│   │   └── CompanyDatabase.kt  # Bluetooth company ID lookup
│   └── ui/
│       ├── ScannerScreen.kt    # Main Compose UI
│       └── theme/              # Material 3 theming (expect/actual for dynamic colors)
├── commonMain/composeResources/ # Shared resources (icon, company_ids.json)
├── androidMain/                 # Android BLE scanner, dynamic theme
├── desktopMain/                 # Desktop stubs (no BLE)
└── iosMain/                     # iOS stubs + MainViewController

androidApp/                      # Android app shell (permissions, Activity)
desktopApp/                      # Desktop JVM entry point
iosApp/                          # iOS SwiftUI entry point
```

## Tech Stack

- Kotlin 2.3.10 + Compose Multiplatform 1.10.1 + AGP 9.0.1
- Material 3 via Compose Multiplatform
- Android BLE APIs / iOS CoreBluetooth (platform-specific via expect/actual)
- KMP ViewModel for state management
- kotlinx.serialization for JSON parsing
- kotlin.time.Clock (stdlib) for cross-platform time

## Code Style

- Use Kotlin idioms (data classes, extension functions, etc.)
- Compose UI with `@Composable` functions
- State hoisting pattern - ViewModels expose `StateFlow`
- Animations via Compose animation APIs
- No external icon libraries - use built-in Material icons or custom vector drawables
- Platform abstractions via expect/actual pattern

## Key Patterns

- `BleViewModel` holds `ScannerUiState` exposed as `StateFlow`
- Device lists sorted by RSSI (signal strength)
- Oura devices identified by MAC prefix, manufacturer IDs, or name
- Signal strength color-coded: green (near) → red (far)
- BLE scanning abstracted via `BleScanner` interface with platform implementations
- `PlatformContext` expect/actual for dependency injection (Android Context / empty on others)
