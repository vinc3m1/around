# Around

Around is a Kotlin Multiplatform Bluetooth Low Energy (BLE) scanner for finding nearby devices, originally built to find a lost Oura ring. It sorts devices by signal strength and recognizes some Oura, Apple, and Google devices from their advertisements. Android and iOS use the platform Bluetooth APIs. Desktop and experimental Web builds are also available.

## Android

- Install [Temurin JDK 17](https://adoptium.net/temurin/releases/?version=17) and point Android Studio’s Gradle JDK (or your shell’s `JAVA_HOME`) to it.
- Open the repository root in Android Studio. In SDK Manager, install [Android SDK Platform 37](https://developer.android.com/about/versions/17/setup-sdk) and Android SDK Platform-Tools. A shell build needs the SDK configured through `ANDROID_HOME` or Android Studio’s generated `local.properties`.
- Use an Android 12 (API 31) or newer phone with BLE to test scanning. Enable USB debugging for a Gradle install, turn on Bluetooth, and accept the Nearby devices permission when prompted.

From the repository root:

```sh
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug          # With a device connected
./gradlew :androidApp:connectedAndroidTest  # Instrumented tests on a device or emulator
```

The debug APK is written under `androidApp/build/outputs/apk/debug/`. It does not need a production signing key.

## iOS

1. On a Mac, install Xcode, the iOS platform support, and Temurin JDK 17. The app’s deployment target is **iOS 17.2**.
2. Open `iosApp/iosApp.xcodeproj` and select the `iosApp` scheme. To install on your iPhone, select your own Team under **Signing & Capabilities** and use a unique bundle identifier if needed.
3. Select your device and choose **Run**. Turn on Bluetooth and allow access when prompted. Use a physical iPhone to verify BLE scanning; a simulator build only verifies the build and UI.

The existing Xcode build phase calls `:shared:embedAndSignAppleFrameworkForXcode`. No separate framework build or CocoaPods setup is needed. If Xcode cannot locate Java, set `JAVA_HOME` for the build phase to your JDK 17 installation. See [Kotlin’s Xcode integration guide](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html) for troubleshooting.

## Desktop, web, and CI

```sh
./gradlew :desktopApp:run
./gradlew :shared:desktopTest
./gradlew :wasmJsApp:wasmJsBrowserDevelopmentRun
```

Web BLE scanning is experimental and depends on Chrome support for `requestLEScan()`; the project uses Chrome’s experimental web-platform features flag.

[GitHub Actions](.github/workflows/android.yml) runs `./gradlew build` and `./gradlew test` on GitHub-hosted Ubuntu for pushes to `main` and pull requests targeting it. The workflow does not run iOS/Xcode or connected Android tests, sign releases, or publish artifacts.

## Bluetooth company identifiers

The build generates a manufacturer-name lookup from `scripts/company_identifiers.yaml`. This file comes from the [Bluetooth SIG Assigned Numbers repository](https://www.bluetooth.com/specifications/assigned-numbers/) and is separate from Around’s source license. Review the [Bluetooth SIG terms](https://www.bluetooth.com/terms-of-use/third-party-service-terms/) before redistributing it or a build that embeds it.

## License

Around’s own code is licensed under [GPLv3](LICENSE). Third-party components and data remain subject to their own terms.
