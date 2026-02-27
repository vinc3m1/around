# Around

BLE scanner for Android. Started as a way to help my wife find her Oura ring, then turned into a general-purpose Bluetooth Low Energy device explorer.

## What it does

- Scans for nearby BLE devices and sorts them by signal strength (color-coded green → red)
- Prioritizes Oura rings at the top (identified by MAC prefix, manufacturer ID, or advertised name)
- Detects Apple devices via Continuity protocol (AirTags, AirPods, iPhones, etc.)
- Detects Google/Android ecosystem devices (Fast Pair, Find My Device Network)
- Groups devices into named, Apple, Google, and unnamed sections
- Shows RSSI, manufacturer info, and relative last-seen timestamps

## Building

```bash
./gradlew assembleDebug
./gradlew installDebug
```

Requires Android 12+ (API 31).

## Updating BLE company database

The Bluetooth SIG company identifiers are checked in at `scripts/company_identifiers.yaml` and compiled into a Kotlin map at build time. To pull the latest from the Bluetooth SIG:

```bash
./gradlew updateCompanyIdentifiers
```

## Tech

Kotlin, Jetpack Compose, Material 3, Android BLE APIs.

## License

[GPLv3](LICENSE)
