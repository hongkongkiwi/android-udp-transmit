# Android UDP Transmit

A low-latency Android application for sending UDP trigger packets over a local network. Optimized for minimal delay between user input and packet transmission.

## Features

- **Ultra-low latency UDP transmission** - Optimized for minimal delay
- **Hardware keyboard trigger** - Press any key to send UDP packets instantly
- **Timestamped packets** - Each packet includes nanoTime precision timestamp
- **Configurable destination** - Set target IP and port via UI
- **Real-time feedback** - Connection status and last trigger time display
- **Material 3 design** - Modern UI with dark mode support
- **Edge-to-edge display** - Fullscreen experience on supported devices

## Requirements

- Android 7.0 (API 24) or higher
- Local network connection to target device

## Building

### Prerequisites

- Android Studio Hedgehog or later (or command-line Gradle)
- JDK 17

### Build from command line

```bash
./gradlew assembleDebug
```

The APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

### Build release APK

For release builds, set keystore credentials as environment variables:

```bash
export KEYSTORE_PATH=path/to/keystore.jks
export KEYSTORE_PASSWORD=your_password
export KEY_ALIAS=your_key_alias
export KEY_PASSWORD=your_key_password

./gradlew assembleRelease
```

The signed APK will be at `app/build/outputs/apk/release/app-release.apk`.

## Usage

1. Launch the app
2. Enter the target IP address and port (default: 192.168.1.100:5000)
3. Tap "Connect" to establish UDP socket
4. Tap the large trigger button or press any hardware key to send packets
5. View last trigger time and timestamp in the info section

## Packet Format

The app sends UDP packets in the following format:

- Default trigger button: `TRIGGER:nanoseconds_since_epoch`
- Keyboard events: `TRIGGER:nanoseconds_since_epoch`
- Configurable packet content prefix via `UdpConfig`

## Architecture

- **MVVM pattern** with Jetpack Compose
- **Kotlin Coroutines** for async operations
- **StateFlow** for reactive state management
- **Material 3** design system

## Development

### Running tests

```bash
./gradlew test
```

### Code style

The project follows standard Android/Kotlin conventions:
- Jetpack Compose for UI
- Coroutines for async operations
- Clean architecture separation (domain/ui layers)

## License

See LICENSE file for details.
