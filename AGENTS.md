# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android UDP Transmit - A low-latency Android application for sending UDP trigger packets over a local network. Optimized for minimal delay between user input and packet transmission.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires keystore environment variables)
export KEYSTORE_PATH=path/to/keystore.jks
export KEYSTORE_PASSWORD=your_password
export KEY_ALIAS=your_key_alias
export KEY_PASSWORD=your_key_password
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.udptrigger.UdpClientTest"

# Run lint
./gradlew lint
```

## Architecture

The app follows **MVVM pattern** with Clean Architecture layers:

### `app/src/main/java/com/udptrigger/`

- **ui/** - Jetpack Compose UI layer
  - `TriggerScreen.kt` - Main composable screen with all UI sections (Config, Settings, Stats, History)
  - `TriggerViewModel.kt` - Central ViewModel managing all state, UDP operations, and business logic
  - `TriggerViewModelFactory.kt` - Factory for creating ViewModel with dependencies
  - `KeyEventHandler.kt` - Hardware keyboard event capture via `KeyEventListener` composable
  - `theme/` - Material 3 theming

- **domain/** - Business logic layer
  - `UdpClient.kt` - Low-latency UDP client using `DatagramSocket` with Nagle disabled (IPTOS_LOWDELAY), mutex-protected for thread safety, supports broadcast and listen modes
  - `NetworkMonitor.kt` - Network availability monitoring via ConnectivityManager
  - `SoundManager.kt` - Sound effect playback

- **data/** - Data persistence layer
  - `PresetsManager.kt` - Preset management (built-in + custom presets stored in SharedPreferences with kotlinx.serialization)
  - `SettingsDataStore.kt` - DataStore-based settings persistence

### Key Data Classes (in `TriggerViewModel.kt`)

- `UdpConfig` - Host, port, packet content, hex mode, timestamp options
- `TriggerState` - Main UI state including connection status, packet history, burst mode
- `BurstMode` - Configurable burst packet sending
- `PacketHistoryEntry` - Individual packet send result with timestamp

## Key Implementation Details

### Low-Latency UDP Transmission
- Address pre-resolution in `UdpClient.initialize()`
- Nagle's algorithm disabled via `trafficClass = 0x04` (IPTOS_LOWDELAY)
- Mutex-protected socket operations for thread safety
- Rate limiting with configurable minimum interval (1-5000ms)

### Hardware Keyboard Triggers
- `KeyEventListener` composable makes the root view focusable to capture all key events
- Uses `System.nanoTime()` for precise timestamp capture
- Returns `true` to consume events when connected

### Packet Format
- Default: `TRIGGER:nanoseconds_since_epoch`
- Hex mode: raw bytes with optional timestamp
- Burst mode: adds `:burstIndex` to packet

### State Management
- `TriggerViewModel` holds single `MutableStateFlow<TriggerState>`
- UI collects as `StateFlow` for reactive updates
- Settings saved to DataStore on changes
- Presets saved to SharedPreferences with JSON serialization

## Dependency Injection

ViewModel instantiation uses manual DI via `TriggerViewModelFactory`:
```kotlin
viewModel(factory = TriggerViewModelFactory(LocalContext.current))
```

`SettingsDataStore` is created inline in ViewModel init. No Hilt/Dagger used.

## Testing

Unit tests use JUnit 4 with Mockito/MockK and `runBlocking` for coroutines:
- `UdpClientTest.kt` - UDP client and config validation tests
- `TriggerViewModelTest.kt` - ViewModel state tests
- `PresetsManagerTest.kt` - Preset CRUD tests
- `NetworkMonitorTest.kt` - Network monitoring tests
