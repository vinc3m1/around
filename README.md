# Around

Kotlin Multiplatform BLE scanner. Started as a way to help my wife find her Oura ring, then turned into a general-purpose Bluetooth Low Energy device explorer. Targets Android, iOS, Desktop, and Web (WASM, experimental).

## What it does

- Scans for nearby BLE devices and sorts them by signal strength (color-coded green → red)
- Prioritizes Oura rings at the top (identified by MAC prefix, manufacturer ID, or advertised name)
- Detects Apple devices via Continuity protocol (AirTags, AirPods, iPhones, etc.)
- Detects Google/Android ecosystem devices (Fast Pair, Find My Device Network)
- Groups devices into named, Apple, Google, and unnamed sections
- Shows RSSI, manufacturer info, and relative last-seen timestamps

## Building

```bash
./gradlew :androidApp:assembleDebug       # Android APK (requires API 31+)
./gradlew :androidApp:installDebug        # Install to connected device
./gradlew :desktopApp:run                 # Run desktop app
./gradlew :wasmJsApp:wasmJsBrowserDevelopmentRun  # Run web app (experimental)
```

Web target requires Chrome with `chrome://flags/#enable-experimental-web-platform-features` enabled for BLE scanning via the Web Bluetooth `requestLEScan()` API. Web BLE support is experimental/WIP.

## Updating BLE company database

The Bluetooth SIG company identifiers are checked in at `scripts/company_identifiers.yaml` and compiled into a Kotlin map at build time. To pull the latest from the Bluetooth SIG:

```bash
./gradlew updateCompanyIdentifiers
```

## Tech

Kotlin Multiplatform, Compose Multiplatform, Material 3, Android BLE APIs / iOS CoreBluetooth / Web Bluetooth (experimental).

## License

[GPLv3](LICENSE)
