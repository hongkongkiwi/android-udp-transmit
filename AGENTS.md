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
  - `TriggerScreen.kt` - Main screen with Config, Settings, Stats, History, Macros, and Multi-Target sections
  - `TriggerViewModel.kt` - Central ViewModel managing all state, UDP/TCP/HTTP operations, and business logic
  - `TriggerViewModelFactory.kt` - Factory for creating ViewModel with dependencies
  - `KeyEventHandler.kt` - Hardware keyboard event capture via `KeyEventListener` composable
  - `GestureTrigger.kt` - Touch gesture trigger support
  - `OnboardingTutorial.kt` - First-run tutorial overlay
  - `theme/` - Material 3 theming

- **domain/** - Business logic layer
  - `UdpClient.kt` - Low-latency UDP client using `DatagramSocket` with Nagle disabled (IPTOS_LOWDELAY), mutex-protected, supports broadcast and listen modes
  - `UdpServer.kt` - UDP server mode for receiving packets
  - `TcpClient.kt` - TCP client for alternative protocol support
  - `HttpClient.kt` - HTTP client for REST API triggers
  - `NetworkMonitor.kt` - Network availability monitoring via ConnectivityManager
  - `NetworkScanner.kt` - Local network scanner for discovering devices
  - `UdpDiscoveryService.kt` - Broadcast discovery service
  - `SoundManager.kt` - Sound effect playback
  - `WakeLockManager.kt` - Wake lock management for background operation
  - `PacketAnalyzer.kt` - Packet analysis and diagnostics

- **data/** - Data persistence layer
  - `PresetsManager.kt` - Preset management (built-in + custom presets stored in SharedPreferences with kotlinx.serialization)
  - `SettingsDataStore.kt` - DataStore-based settings persistence
  - `MultiTargetConfig.kt` - Multi-target broadcast configuration
  - `MacroManager.kt` - Macro recording and playback (keyboard/mouse/gesture automation)
  - `AutomationManager.kt` - Conditional automation rules
  - `PacketTemplateManager.kt` - Packet template management
  - `PacketActionRule.kt` - Packet response action rules
  - `ConfigShareCode.kt` - Configuration sharing via QR/URL codes
  - `QuickHostsManager.kt` - Quick hosts list management
  - `ScriptEngine.kt` - Script execution engine for advanced automation
  - `UsageStatistics.kt` - Usage metrics tracking
  - `SettingsBackupManager.kt` - Settings backup/restore with encryption
  - `DataManager.kt` - Central data management coordination

- **service/** - Background services
  - `UdpForegroundService.kt` - Foreground service for persistent background operation
  - `TaskerReceiver.kt` - Tasker integration receiver

- **receiver/** - Broadcast receivers
  - `UdpIntentReceiver.kt` - Intent-based trigger receiver
  - `ShortcutReceiver.kt` - Quick shortcut launcher receiver

- **widget/** - App widgets
  - `TriggerGlanceWidget.kt` - Glance-based home screen widget
  - `WidgetTriggerReceiver.kt` - Widget trigger handler

- **util/** - Utility classes
  - `QrCodeManager.kt` - QR code generation
  - `QrCodeScanner.kt` - QR code scanning (CameraX + ML Kit)
  - `BatteryOptimization.kt` - Battery optimization bypass
  - `VpnManager.kt` - VPN connection detection
  - `NotificationChannelManager.kt` - Notification channel setup
  - `LogViewer.kt` - In-app log viewer
  - `ConnectionDiagnostics.kt` - Network diagnostics
  - `CrashReporter.kt` - Crash reporting

### Key Data Classes (in `TriggerViewModel.kt`)

- `UdpConfig` - Host, port, packet content, hex mode, timestamp options
- `TriggerState` - Main UI state including connection status, packet history, burst mode
- `BurstMode` - Configurable burst packet sending
- `PacketHistoryEntry` - Individual packet send result with timestamp
- `MultiTargetState` - Multi-target broadcast configuration
- `MacroState` - Macro recording/playback state
- `AutomationRule` - Conditional automation rule definition

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

### Foreground Service
- `UdpForegroundService` keeps app alive in background for persistent triggers
- Requires `FOREGROUND_SERVICE_SPECIAL_USE` permission on Android 14+
- Notification with action buttons for quick operations

### Packet Format
- Default: `TRIGGER:nanoseconds_since_epoch`
- Hex mode: raw bytes with optional timestamp
- Burst mode: adds `:burstIndex` to packet
- Templates support dynamic content via placeholders

### State Management
- `TriggerViewModel` holds single `MutableStateFlow<TriggerState>`
- UI collects as `StateFlow` for reactive updates
- Settings saved to DataStore on changes
- Presets saved to SharedPreferences with JSON serialization

### QR Code Features
- QR code generation for config sharing (uses ZXing)
- QR code scanning for config import (uses CameraX + ML Kit)
- Share codes support short codes and full URL modes

### Widget Support
- Glance App Widget for home screen trigger
- Configuration activity for widget setup
- Tapping widget sends configured trigger

## Dependency Injection

ViewModel instantiation uses manual DI via `TriggerViewModelFactory`:
```kotlin
viewModel(factory = TriggerViewModelFactory(LocalContext.current))
```

`SettingsDataStore` is created inline in ViewModel init. No Hilt/Dagger used.

## Key Dependencies

- **Jetpack Compose** with Material 3 - UI framework
- **Kotlin Coroutines** - Async operations
- **DataStore Preferences** - Settings persistence
- **Kotlinx Serialization** - JSON encoding
- **ZXing** - QR code generation
- **ML Kit + CameraX** - QR code scanning
- **Glance AppWidget** - Home screen widget

## Testing

Unit tests use JUnit 4 with Mockito/MockK and `runBlocking` for coroutines:
- `UdpClientTest.kt` - UDP client and config validation tests
- `TriggerViewModelTest.kt` - ViewModel state tests
- `PresetsManagerTest.kt` - Preset CRUD tests
- `NetworkMonitorTest.kt` - Network monitoring tests
- `MultiTargetConfigTest.kt` - Multi-target configuration tests
- `AutomationManagerTest.kt` - Automation rules tests
