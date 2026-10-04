# BharatTag: Overall Codebase Architecture & Documentation

**BharatTag** is a native Android application built using **Kotlin** and **Jetpack Compose** that interfaces with a **Bluetooth Low Energy (BLE) GATT** peripheral (such as an **Arduino UNO R4 WiFi** with a Piezo buzzer) to prototype a smart item tracker similar to Apple AirTag or Tile.

---

## 📐 1. High-Level Architecture & Tech Stack

The application follows Android's recommended **Clean Architecture** and **Unidirectional Data Flow (UDF)** patterns:

```
┌─────────────────────────────────────────────────────────────┐
│                      UI Layer (Compose)                     │
│  [BharatTagScreen] -> [TrackerCard], [CommandControls], etc.│
└──────────────────────────────▲──────────────────────────────┘
                               │ StateFlow (BharatTagUiState)
                               │ Events (User Actions)
┌──────────────────────────────┴──────────────────────────────┐
│                  Presentation (ViewModel)                   │
│                    [BharatTagViewModel]                     │
└──────────────────────────────▲──────────────────────────────┘
                               │ StateFlow & SharedFlow (BleEvent)
                               │ Method Invocations
┌──────────────────────────────┴──────────────────────────────┐
│                    Data & BLE Engine                        │
│                  [BharatTagBleManager]                      │
└─────────────────────────────────────────────────────────────┘
```

### Technology Stack
- **Language**: Kotlin 2.2.10
- **UI Framework**: Jetpack Compose (Material 3) with Lifecycle `collectAsStateWithLifecycle`
- **Asynchronous & Flow**: Kotlin Coroutines, `StateFlow`, `SharedFlow`
- **BLE Engine**: Android Bluetooth Low Energy GATT APIs (`BluetoothLeScanner`, `BluetoothGatt`, `BluetoothGattCallback`)
- **Min / Target / Compile SDK**: Min SDK 24, Target SDK 36, Compile SDK 36
- **Build System**: Android Gradle Plugin (AGP) & Gradle Wrapper

---

## 📡 2. BLE GATT Specification

BharatTag operates as a **BLE Central** device connecting to a **BLE Peripheral**.

| Attribute | UUID / Value | Description |
|---|---|---|
| **Service UUID** | `19B10000-E8F2-537E-4F6C-D104768A1214` | Primary GATT Service for BharatTag |
| **Command Characteristic UUID** | `19B10001-E8F2-537E-4F6C-D104768A1214` | Writable Characteristic (Read/Write/WriteWithoutResponse) |
| **Target Device Name** | `BharatTag` | Expected peripheral advertisement name |
| **Command: "RING"** | `0x52 0x49 0x4E 0x47` | Triggers the 2000Hz buzzer sound |
| **Command: "STOP"** | `0x53 0x54 0x4F 0x50` | Immediately mutes the buzzer |

---

## 🗂️ 3. Component Breakdown

### 📱 A. Main Activity & Entry Point
- **[MainActivity.kt](file:///C:/Users/abhin/OneDrive/Documents/bharattag/app/src/main/java/com/example/MainActivity.kt)**
  - Configures `enableEdgeToEdge()` layout.
  - Manages runtime permissions using `ActivityResultContracts.RequestMultiplePermissions()`.
  - Re-evaluates permissions and Bluetooth hardware state inside `onResume()`.
  - Provides system fallback navigation to `Settings.ACTION_BLUETOOTH_SETTINGS`.

---

### ⚙️ B. ViewModel & State Management
- **[BharatTagViewModel.kt](file:///C:/Users/abhin/OneDrive/Documents/bharattag/app/src/main/java/com/example/viewmodel/BharatTagViewModel.kt)**
  - Acts as the single source of truth for `BharatTagUiState`.
  - Observes scanning states, device discovery streams, connection states, and command transmission statuses from `BharatTagBleManager`.
  - Maintains a real-time diagnostic log stream (`List<LogEntry>`).
  - Evaluates system version to supply appropriate required permissions (`BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` for Android 12+, versus legacy permissions for Android 11 and below).

---

### 📡 C. Core BLE Engine
- **[BharatTagBleManager.kt](file:///C:/Users/abhin/OneDrive/Documents/bharattag/app/src/main/java/com/example/ble/BharatTagBleManager.kt)**
  - Wraps low-level Android `BluetoothManager`, `BluetoothLeScanner`, and `BluetoothGatt`.
  - **Scanning**: Executes a 10-second timed BLE scan with countdown progress. Filters or identifies devices matching `BharatTag` by name or Service UUID.
  - **GATT Connection Lifecycle**: Handles `connectGatt()`, `discoverServices()`, MTU negotiation, characteristic lookup, and write confirmation callback (`onCharacteristicWrite`).
  - **Timeout Protection**: Implements a 4-second safety write timeout job to prevent infinite progress lock if a peripheral drops packet.
  - **Broadcast Receiver**: Listens for system `BluetoothAdapter.ACTION_STATE_CHANGED` events to handle Bluetooth being toggled on/off while app is running.

---

### 📦 D. Data Models
- **[BleDeviceItem.kt](file:///C:/Users/abhin/OneDrive/Documents/bharattag/app/src/main/java/com/example/model/BleDeviceItem.kt)**
  - Immutable representation of a discovered BLE peripheral (`device`, `name`, `address`, `rssi`, `isBharatTagNamed`, `hasBharatTagServiceUuid`, `lastSeenTimestamp`).
- **[UiState.kt](file:///C:/Users/abhin/OneDrive/Documents/bharattag/app/src/main/java/com/example/model/UiState.kt)**
  - `BleConnectionState`: Sealed interface representing connection states (`BluetoothUnavailable`, `BluetoothDisabled`, `PermissionRequired`, `Idle`, `Scanning`, `Connecting`, `DiscoveringServices`, `Connected`, `Disconnected`, `Error`).
  - `BharatTagUiState`: Data class combining current connection state, lists of discovered devices, write statuses, and diagnostic logs.
  - `LogEntry`: Log item with timestamp, message, and error/success flag.

---

### 🎨 E. UI Components (Jetpack Compose)
- **[BharatTagScreen.kt](file:///C:/Users/abhin/OneDrive/Documents/bharattag/app/src/main/java/com/example/ui/BharatTagScreen.kt)**: Root composable layout housing Scaffold, TopAppBar, SnackbarHost, and scrollable column sections.
- **[TrackerCard.kt](file:///C:/Users/abhin/OneDrive/Documents/bharattag/app/src/main/java/com/example/ui/components/TrackerCard.kt)**: Hero card featuring an animated infinite pulse effect when connected, displaying device MAC address, RSSI, and active state badge.
- **[CommandControlsSection.kt](file:///C:/Users/abhin/OneDrive/Documents/bharattag/app/src/main/java/com/example/ui/components/CommandControlsSection.kt)**: Interactive control panel providing **Ring (Buzzer ON)**, **Stop (Buzzer OFF)**, and **Disconnect** buttons with write-in-progress indicators.
- **[DeviceListSection.kt](file:///C:/Users/abhin/OneDrive/Documents/bharattag/app/src/main/java/com/example/ui/components/DeviceListSection.kt)**: BLE device discovery list with scan countdown timer, RSSI indicator, recommendation badges, device selection radio buttons, and Connect action.
- **[StatusBanner.kt](file:///C:/Users/abhin/OneDrive/Documents/bharattag/app/src/main/java/com/example/ui/components/StatusBanner.kt)**: Color-animated status banner reflecting live BLE system state with direct action buttons (Enable Bluetooth / Grant Permissions).
- **[LogConsoleSection.kt](file:///C:/Users/abhin/OneDrive/Documents/bharattag/app/src/main/java/com/example/ui/components/LogConsoleSection.kt)**: Terminal-style expandable diagnostic log viewer with clear logs capability.
- **Theme**: Custom Material 3 color palette in [Color.kt](file:///C:/Users/abhin/OneDrive/Documents/bharattag/app/src/main/java/com/example/ui/theme/Color.kt) (`CyanAccent`, `RingOrange`, `StopRed`, `SuccessGreen`) and dark/light configuration in [Theme.kt](file:///C:/Users/abhin/OneDrive/Documents/bharattag/app/src/main/java/com/example/ui/theme/Theme.kt).

---

## 🔐 4. Permission Model & Security

The app handles Android's dual permission model automatically:

| Android Version | Required Permissions |
|---|---|
| **Android 12+ (API 31+)** | `android.permission.BLUETOOTH_SCAN` (`neverForLocation`), `android.permission.BLUETOOTH_CONNECT` |
| **Android 11 & older (API 24–30)** | `android.permission.BLUETOOTH`, `android.permission.BLUETOOTH_ADMIN`, `android.permission.ACCESS_FINE_LOCATION` |

---

## ⚡ 5. Hardware Integration (Arduino UNO R4 WiFi Sketch)

Connect a **Piezo Buzzer** positive lead to Arduino Pin **D8** and negative lead to **GND**.

```cpp
#include <ArduinoBLE.h>

const int BUZZER_PIN = 8;
const char* BLE_SERVICE_UUID = "19B10000-E8F2-537E-4F6C-D104768A1214";
const char* BLE_COMMAND_CHAR_UUID = "19B10001-E8F2-537E-4F6C-D104768A1214";

BLEService bharatTagService(BLE_SERVICE_UUID);
BLEStringCharacteristic commandCharacteristic(BLE_COMMAND_CHAR_UUID, BLERead | BLEWrite | BLEWriteWithoutResponse, 32);

void setup() {
  pinMode(BUZZER_PIN, OUTPUT);
  noTone(BUZZER_PIN);
  BLE.begin();
  BLE.setLocalName("BharatTag");
  BLE.setAdvertisedService(bharatTagService);
  bharatTagService.addCharacteristic(commandCharacteristic);
  BLE.addService(bharatTagService);
  BLE.advertise();
}

void loop() {
  BLEDevice central = BLE.central();
  if (central) {
    while (central.connected()) {
      if (commandCharacteristic.written()) {
        String cmd = commandCharacteristic.value();
        if (cmd == "RING") { tone(BUZZER_PIN, 2000); }
        else if (cmd == "STOP") { noTone(BUZZER_PIN); }
      }
    }
  }
}
```

---

## 🛠️ 6. Build & APK Generation

To compile and produce the Android Debug APK:

```powershell
./gradlew app:assembleDebug
```

### Output File Path
- **Debug APK Location**: [app-debug.apk](file:///C:/Users/abhin/OneDrive/Documents/bharattag/app/build/outputs/apk/debug/app-debug.apk)
