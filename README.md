# BharatTag: Smart Bluetooth Item Tracker

**BharatTag** is a native Android application built with Kotlin and Jetpack Compose that interfaces with an **Arduino UNO R4 WiFi** over Bluetooth Low Energy (BLE) GATT to prototype an AirTag-like item finder.

> 📖 **Developer Documentation**: For complete code architecture, component hierarchy, BLE state machine diagrams, and file reference, see [CODE_OVERVIEW.md](CODE_OVERVIEW.md).

---

## 🛠️ Hardware Requirements & Wiring

- **Arduino UNO R4 WiFi**
- **Piezo Buzzer** (Active or Passive):
  - **Positive (+) pin**: Connected to Arduino Digital Pin **D8**
  - **Negative (-) / GND pin**: Connected to Arduino **GND**
- **USB-C Cable** for programming the Arduino
- **Physical Android Smartphone** running Android 8.0 (API 26) through Android 15/16 (API 34/35/36)

---

## ⚡ Complete Arduino UNO R4 WiFi Sketch

Open the **Arduino IDE**, install the official `ArduinoBLE` library via **Tools > Manage Libraries...**, paste the following sketch, and upload it to your board:

```cpp
#include <ArduinoBLE.h>

// Hardware Pin Configuration
const int BUZZER_PIN = 8;
const unsigned int BUZZER_FREQUENCY_HZ = 2000;
const unsigned long RING_DURATION_MS = 3000; // Auto-stop after 3 seconds

// BharatTag BLE GATT UUIDs
const char* BLE_SERVICE_UUID = "19B10000-E8F2-537E-4F6C-D104768A1214";
const char* BLE_COMMAND_CHAR_UUID = "19B10001-E8F2-537E-4F6C-D104768A1214";

// Define GATT Service and Writable String Characteristic (up to 32 bytes)
BLEService bharatTagService(BLE_SERVICE_UUID);
BLEStringCharacteristic commandCharacteristic(
  BLE_COMMAND_CHAR_UUID,
  BLERead | BLEWrite | BLEWriteWithoutResponse,
  32
);

// State tracking variables
bool isRinging = false;
unsigned long ringStartTime = 0;

void setup() {
  Serial.begin(9600);
  pinMode(BUZZER_PIN, OUTPUT);
  noTone(BUZZER_PIN);

  // Initialize BLE Hardware
  if (!BLE.begin()) {
    Serial.println("Starting BLE module failed!");
    while (1);
  }

  // Set advertised local name and service
  BLE.setLocalName("BharatTag");
  BLE.setDeviceName("BharatTag");
  BLE.setAdvertisedService(bharatTagService);

  // Add characteristic to service, then service to BLE stack
  bharatTagService.addCharacteristic(commandCharacteristic);
  BLE.addService(bharatTagService);

  // Set initial characteristic value
  commandCharacteristic.writeValue("READY");

  // Begin advertising
  BLE.advertise();

  Serial.println("======================================");
  Serial.println(" BharatTag Arduino UNO R4 WiFi Online ");
  Serial.println(" Advertised Name : BharatTag          ");
  Serial.println(" Service UUID    : 19B10000-E8F2-537E-4F6C-D104768A1214");
  Serial.println(" Command UUID    : 19B10001-E8F2-537E-4F6C-D104768A1214");
  Serial.println(" Buzzer Pin      : D8 (2000 Hz)       ");
  Serial.println(" Waiting for Android App connection...");
  Serial.println("======================================");
}

void loop() {
  // Listen for BLE central connections
  BLEDevice central = BLE.central();

  if (central) {
    Serial.print("Connected to central phone MAC: ");
    Serial.println(central.address());

    while (central.connected()) {
      // Check if Android app wrote a command
      if (commandCharacteristic.written()) {
        String command = commandCharacteristic.value();
        command.trim();

        if (command == "RING") {
          Serial.println("Received command: RING");
          Serial.println("Ringing BharatTag");
          startRinging();
        } else if (command == "STOP") {
          Serial.println("Received command: STOP");
          Serial.println("Buzzer stopped");
          stopRinging();
        } else {
          Serial.print("Received unrecognized command: ");
          Serial.println(command);
        }
      }

      // Non-blocking auto-timeout using millis()
      if (isRinging && (millis() - ringStartTime >= RING_DURATION_MS)) {
        Serial.println("Ring duration timeout reached (3s). Stopping buzzer.");
        stopRinging();
      }
    }

    // Connection ended
    stopRinging();
    Serial.print("Disconnected from central: ");
    Serial.println(central.address());
    Serial.println("Resuming BLE advertising...");
  }

  // Handle auto-timeout even if connection dropped while ringing
  if (isRinging && (millis() - ringStartTime >= RING_DURATION_MS)) {
    stopRinging();
  }
}

void startRinging() {
  tone(BUZZER_PIN, BUZZER_FREQUENCY_HZ);
  isRinging = true;
  ringStartTime = millis();
}

void stopRinging() {
  noTone(BUZZER_PIN);
  isRinging = false;
}
```

---

## 📱 Step-by-Step Build & Installation Guide

### 1. Opening the Project
- Open **Android Studio** (or Google AI Studio).
- Select **Open an Existing Project** and choose the root directory of this repository (`BharatTag`).

### 2. Syncing Gradle
- Android Studio will automatically invoke Gradle Sync.
- If prompted, click **Sync Project with Gradle Files**.

### 3. Building the App
- In Android Studio, select **Build > Build Bundle(s) / APK(s) > Build APK(s)**, or run:
  ```bash
  gradle assembleDebug
  ```

### 4. Installing on a Physical Android Phone
1. Connect your physical Android phone to your computer via USB.
2. Enable **Developer Options** and **USB Debugging**:
   - Go to **Settings > About Phone**.
   - Tap **Build Number** 7 times until you see "You are now a developer!".
   - Go to **Settings > System > Developer Options** and enable **USB Debugging**.
3. Accept the RSA fingerprint prompt on your phone screen.
4. In Android Studio, select your phone from the device dropdown and click **Run (Shift + F10)**.

---

## 🔍 How to Test BharatTag End-to-End

1. **Power the Arduino**: Plug the Arduino UNO R4 into power or your PC. Verify the green power LED is ON.
2. **Launch the App**: Open **BharatTag** on your phone.
3. **Grant Permissions**:
   - On Android 12+, approve the **Nearby Devices** prompt.
   - On Android 11 or older, approve the **Location** prompt (required for BLE scans).
4. **Enable Bluetooth**: If your Bluetooth is switched off, tap the **Open Bluetooth Settings** button on the banner to turn it on.
5. **Scan for BharatTag**:
   - Tap **Scan for BharatTag**.
   - The app scans for up to 10 seconds with a real-time progress indicator.
6. **Select & Connect**:
   - When `BharatTag` appears in the device list with signal strength (RSSI), select it and tap **Connect**.
   - The app connects to the GATT server and discovers the service and writable characteristic.
   - The status badge changes to **CONNECTED & READY** (bright green).
7. **Press RING**:
   - Tap the large orange **RING** button.
   - The phone writes `"RING"` as UTF-8 bytes to characteristic `19B10001-E8F2-537E-4F6C-D104768A1214`.
   - The Arduino buzzer sounds a **2000 Hz** tone.
   - A snackbar displays **Ring command sent**.
8. **Press STOP**:
   - Tap the red **STOP** button.
   - The phone writes `"STOP"`.
   - The buzzer cuts off immediately.
   - A snackbar displays **Stop command sent**.
9. **Automatic Timeout**:
   - If you press **RING** and do not press **STOP**, the Arduino will automatically stop buzzing after exactly 3 seconds via non-blocking `millis()`.
10. **Disconnect**:
    - Tap **Disconnect** to close the GATT connection.

---

## 📊 Debugging with Android Logcat

Filter logs in Android Studio Logcat using the tag:

```text
package:mine tag:BharatTagBLE
```

Logged events include:
- `Permission state`: Shows granted/denied permission status.
- `Scan started`: Scan initiated with 10s timeout.
- `Device found`: Discovered device name, address, and RSSI.
- `Connection state`: CONNECTED or DISCONNECTED.
- `Services discovered`: Number of discovered GATT services.
- `Service UUID FOUND`: Confirmation of `19B10000-E8F2-537E-4F6C-D104768A1214`.
- `Characteristic UUID FOUND and WRITABLE`: Confirmation of `19B10001-E8F2-537E-4F6C-D104768A1214`.
- `Command being sent`: "RING" or "STOP".
- `Write result`: SUCCESS or error status.

---

## 🛠️ Troubleshooting Connection Problems

1. **Bluetooth is disabled**:
   - Tap "Open Bluetooth Settings" in the app or turn Bluetooth ON from your phone's quick settings drawer.
2. **BharatTag not found during scan**:
   - Ensure the Arduino sketch is uploaded and running.
   - Ensure the Arduino is not already connected to another device (BLE peripherals can typically connect to only one central at a time).
   - Check the Serial Monitor in Arduino IDE (9600 baud) to ensure it says `BharatTag Arduino UNO R4 WiFi Online`.
   - Bring your phone within 5 meters of the Arduino.
3. **Connection fails or disconnects immediately**:
   - In rare cases, Android's Bluetooth cache may hold old GATT attributes. Toggle Bluetooth OFF and ON on your phone to refresh the stack.
4. **Characteristic not found**:
   - Verify that your Arduino sketch uses the exact UUIDs:
     - Service: `19B10000-E8F2-537E-4F6C-D104768A1214`
     - Characteristic: `19B10001-E8F2-537E-4F6C-D104768A1214`
5. **Buzzer does not sound**:
   - Check wiring: Buzzer (+) to pin D8, Buzzer (-) to GND.
   - Verify with a multimeter or an active piezo buzzer if using a 5V/3.3V buzzer.
