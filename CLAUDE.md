# Ouround

Android app for finding Oura rings via BLE scanning.

## Build

```bash
./gradlew assembleDebug          # Debug APK
./gradlew installDebug           # Install to connected device
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests
```

## Project Structure

```
app/src/main/java/com/makeramen/ouround/
├── MainActivity.kt              # Entry point, permissions handling
├── OuroundApplication.kt        # Application class
├── ble/
│   ├── BleScanner.kt           # BLE scanning logic
│   ├── BleViewModel.kt         # UI state management
│   ├── BleDevice.kt            # Device data model
│   ├── SignalStrength.kt       # RSSI to signal strength mapping
│   └── CompanyDatabase.kt      # Bluetooth company ID lookup
└── ui/
    ├── ScannerScreen.kt        # Main Compose UI
    └── theme/                  # Material 3 theming
```

## Tech Stack

- Kotlin
- Jetpack Compose with Material 3
- Android BLE APIs
- ViewModel for state management
- Square Logcat for debug logging

## Code Style

- Use Kotlin idioms (data classes, extension functions, etc.)
- Compose UI with `@Composable` functions
- State hoisting pattern - ViewModels expose `StateFlow`
- Animations via Compose animation APIs
- No external icon libraries - use built-in Material icons or custom vector drawables

## Key Patterns

- `BleViewModel` holds `BleUiState` exposed as `StateFlow`
- Device lists sorted by RSSI (signal strength)
- Oura devices identified by advertised name prefix "Oura"
- Signal strength color-coded: green (near) → red (far)
