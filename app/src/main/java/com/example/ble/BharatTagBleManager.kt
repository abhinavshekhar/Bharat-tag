package com.example.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.example.model.BleDeviceItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class BharatTagBleManager(private val context: Context) {

    companion object {
        const val TAG = "BharatTagBLE"
        val SERVICE_UUID: UUID = UUID.fromString("19B10000-E8F2-537E-4F6C-D104768A1214")
        val COMMAND_CHARACTERISTIC_UUID: UUID = UUID.fromString("19B10001-E8F2-537E-4F6C-D104768A1214")
        const val TARGET_DEVICE_NAME = "BharatTag"
        const val SCAN_DURATION_MS = 10_000L
        const val WRITE_TIMEOUT_MS = 4_000L

        const val CMD_RING = "RING"
        const val CMD_STOP = "STOP"
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var scanTimeoutRunnable: Runnable? = null
    private var scanCountdownJob: Job? = null

    private var bluetoothGatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null

    private var writeTimeoutJob: Job? = null

    // State flows
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanRemainingSeconds = MutableStateFlow(0)
    val scanRemainingSeconds: StateFlow<Int> = _scanRemainingSeconds.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BleDeviceItem>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDeviceItem>> = _discoveredDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BleDeviceItem?>(null)
    val connectedDevice: StateFlow<BleDeviceItem?> = _connectedDevice.asStateFlow()

    private val _isCharacteristicReady = MutableStateFlow(false)
    val isCharacteristicReady: StateFlow<Boolean> = _isCharacteristicReady.asStateFlow()

    private val _isWriteInProgress = MutableStateFlow(false)
    val isWriteInProgress: StateFlow<Boolean> = _isWriteInProgress.asStateFlow()

    // Event signals
    sealed interface BleEvent {
        data class ScanStarted(val message: String) : BleEvent
        data class ScanStopped(val reason: String) : BleEvent
        data class ScanFailed(val errorCode: Int, val description: String) : BleEvent
        data class DeviceDiscovered(val device: BleDeviceItem) : BleEvent
        data class Connecting(val deviceName: String, val address: String) : BleEvent
        data class Connected(val deviceName: String, val address: String) : BleEvent
        data class ServicesDiscovered(val serviceCount: Int) : BleEvent
        data class CharacteristicReady(val characteristicUuid: String) : BleEvent
        data class Disconnected(val reason: String) : BleEvent
        data class CommandSent(val command: String) : BleEvent
        data class WriteSuccess(val command: String) : BleEvent
        data class WriteFailed(val command: String, val reason: String) : BleEvent
        data class Error(val message: String) : BleEvent
        data class BluetoothStateChanged(val isEnabled: Boolean) : BleEvent
    }

    private val _events = MutableSharedFlow<BleEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<BleEvent> = _events.asSharedFlow()

    private var bluetoothReceiverRegistered = false
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                val isEnabled = state == BluetoothAdapter.STATE_ON
                Log.d(TAG, "Bluetooth state changed: isEnabled=$isEnabled (state=$state)")
                emitEvent(BleEvent.BluetoothStateChanged(isEnabled))

                if (!isEnabled) {
                    stopScan("Bluetooth turned off")
                    disconnect()
                }
            }
        }
    }

    init {
        registerBluetoothStateReceiver()
    }

    private fun registerBluetoothStateReceiver() {
        if (!bluetoothReceiverRegistered) {
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            context.registerReceiver(bluetoothReceiver, filter)
            bluetoothReceiverRegistered = true
        }
    }

    fun unregisterReceiver() {
        if (bluetoothReceiverRegistered) {
            try {
                context.unregisterReceiver(bluetoothReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver: ${e.message}")
            }
            bluetoothReceiverRegistered = false
        }
    }

    fun isBleSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Start BLE Scan for BharatTag devices with a 10s automatic timeout.
     */
    @SuppressLint("MissingPermission")
    fun startScan(): Boolean {
        if (!isBleSupported()) {
            Log.e(TAG, "Scan failed: BLE not supported on device")
            emitEvent(BleEvent.Error("Bluetooth Low Energy is not supported on this device"))
            return false
        }

        if (!isBluetoothEnabled()) {
            Log.w(TAG, "Scan failed: Bluetooth is disabled")
            emitEvent(BleEvent.Error("Bluetooth is disabled. Please turn on Bluetooth."))
            return false
        }

        if (_isScanning.value) {
            Log.d(TAG, "Scan already in progress")
            return true
        }

        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "Scan failed: BluetoothLeScanner is null")
            emitEvent(BleEvent.Error("Cannot access BluetoothLeScanner"))
            return false
        }

        _discoveredDevices.value = emptyList()
        _isScanning.value = true
        _scanRemainingSeconds.value = (SCAN_DURATION_MS / 1000).toInt()

        Log.i(TAG, "Scan started: Scanning for BharatTag (10s duration)")
        emitEvent(BleEvent.ScanStarted("Scanning for BharatTag devices…"))

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build(),
            ScanFilter.Builder()
                .setDeviceName(TARGET_DEVICE_NAME)
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { handleScanResult(it) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with error code: $errorCode")
                _isScanning.value = false
                val reason = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
                    SCAN_FAILED_INTERNAL_ERROR -> "Internal Bluetooth stack error"
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE scan feature unsupported"
                    else -> "Scan error code $errorCode"
                }
                emitEvent(BleEvent.ScanFailed(errorCode, reason))
            }
        }

        try {
            bluetoothLeScanner?.startScan(filters, settings, scanCallback)
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException starting BLE scan: ${se.message}")
            _isScanning.value = false
            emitEvent(BleEvent.Error("Bluetooth permission denied: ${se.message}"))
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting BLE scan with filters, trying fallback: ${e.message}")
            try {
                bluetoothLeScanner?.startScan(scanCallback)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Fallback BLE scan failed: ${fallbackEx.message}")
                _isScanning.value = false
                emitEvent(BleEvent.Error("Unable to start BLE scan: ${fallbackEx.message}"))
                return false
            }
        }

        scanCountdownJob?.cancel()
        scanCountdownJob = scope.launch {
            for (sec in (SCAN_DURATION_MS / 1000).toInt() downTo 1) {
                _scanRemainingSeconds.value = sec
                delay(1000)
            }
            _scanRemainingSeconds.value = 0
        }

        scanTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        scanTimeoutRunnable = Runnable {
            Log.i(TAG, "Scan stopped: 10s timeout reached")
            stopScan("Scan completed (10 seconds timeout)")
        }
        mainHandler.postDelayed(scanTimeoutRunnable!!, SCAN_DURATION_MS)

        return true
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val scanRecord = result.scanRecord
        val advertisedName = scanRecord?.deviceName ?: device.name
        val address = device.address ?: "00:00:00:00:00:00"
        val rssi = result.rssi

        val serviceUuids = scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
        val hasServiceUuid = serviceUuids.contains(SERVICE_UUID)
        val isNamedBharatTag = advertisedName?.equals(TARGET_DEVICE_NAME, ignoreCase = true) == true

        val isRelevant = isNamedBharatTag || hasServiceUuid ||
                (advertisedName != null && advertisedName.contains("Bharat", ignoreCase = true))

        if (!isRelevant) {
            return
        }

        val item = BleDeviceItem(
            device = device,
            name = advertisedName,
            address = address,
            rssi = rssi,
            isBharatTagNamed = isNamedBharatTag,
            hasBharatTagServiceUuid = hasServiceUuid
        )

        val currentList = _discoveredDevices.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.address == address }

        if (existingIndex >= 0) {
            currentList[existingIndex] = item
        } else {
            Log.i(TAG, "Device found: ${item.displayName} [$address] (RSSI: $rssi dBm)")
            currentList.add(item)
            emitEvent(BleEvent.DeviceDiscovered(item))
        }

        currentList.sortWith(
            compareByDescending<BleDeviceItem> { it.isBharatTagNamed }
                .thenByDescending { it.hasBharatTagServiceUuid }
                .thenByDescending { it.rssi }
        )

        _discoveredDevices.value = currentList
    }

    @SuppressLint("MissingPermission")
    fun stopScan(reason: String = "User stopped scan") {
        scanTimeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
            scanTimeoutRunnable = null
        }
        scanCountdownJob?.cancel()
        _scanRemainingSeconds.value = 0

        if (_isScanning.value) {
            _isScanning.value = false
            try {
                if (bluetoothAdapter?.isEnabled == true && scanCallback != null) {
                    bluetoothLeScanner?.stopScan(scanCallback)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping BLE scanner: ${e.message}")
            }
            scanCallback = null
            Log.i(TAG, "Scan stopped: $reason")
            emitEvent(BleEvent.ScanStopped(reason))
        }
    }

    /**
     * Connect to the selected BharatTag device using BluetoothGatt.
     */
    @SuppressLint("MissingPermission")
    fun connect(item: BleDeviceItem) {
        stopScan("Connecting to device")

        disconnect()

        val device = item.device
        val address = item.address
        val name = item.displayName

        Log.i(TAG, "Connecting to BharatTag: $name [$address]")
        emitEvent(BleEvent.Connecting(name, address))

        try {
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException connecting to $address: ${se.message}")
            emitEvent(BleEvent.Error("Bluetooth permission denied for connection: ${se.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Exception connecting to $address: ${e.message}")
            emitEvent(BleEvent.Error("Connection error: ${e.message}"))
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val device = gatt?.device
            val address = device?.address ?: "Unknown"
            val name = device?.name ?: TARGET_DEVICE_NAME

            Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState for [$address]")

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.i(TAG, "Connection state: CONNECTED to $name [$address]. Discovering services…")
                        val item = BleDeviceItem(
                            device = device!!,
                            name = name,
                            address = address,
                            rssi = 0,
                            isBharatTagNamed = name.equals(TARGET_DEVICE_NAME, ignoreCase = true),
                            hasBharatTagServiceUuid = true
                        )
                        _connectedDevice.value = item
                        emitEvent(BleEvent.Connected(name, address))

                        scope.launch {
                            delay(250)
                            try {
                                val started = gatt.discoverServices()
                                Log.d(TAG, "discoverServices() triggered: $started")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error initiating service discovery: ${e.message}")
                                emitEvent(BleEvent.Error("Failed to initiate service discovery: ${e.message}"))
                            }
                        }
                    } else {
                        Log.e(TAG, "Connection state error: status=$status")
                        handleDisconnection("Connection error (status $status)")
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Connection state: DISCONNECTED (status=$status)")
                    val reason = if (status == BluetoothGatt.GATT_SUCCESS) {
                        "Device disconnected normally"
                    } else {
                        "Disconnected (status code $status)"
                    }
                    handleDisconnection(reason)
                }

                else -> {
                    Log.d(TAG, "Connection state: $newState")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                Log.e(TAG, "Services discovery failed with status $status")
                emitEvent(BleEvent.Error("Services discovery failed (status $status)"))
                return
            }

            val services = gatt.services
            Log.i(TAG, "Services discovered: found ${services.size} services")
            emitEvent(BleEvent.ServicesDiscovered(services.size))

            val bharatService = gatt.getService(SERVICE_UUID)
            if (bharatService == null) {
                Log.e(TAG, "Service UUID MISSING: $SERVICE_UUID not found on device!")
                services.forEach { s ->
                    Log.d(TAG, "  Discovered Service: ${s.uuid}")
                }
                emitEvent(BleEvent.Error("BharatTag service UUID ($SERVICE_UUID) not found on device"))
                _isCharacteristicReady.value = false
                return
            }

            Log.i(TAG, "Service UUID FOUND: $SERVICE_UUID")

            val cmdChar = bharatService.getCharacteristic(COMMAND_CHARACTERISTIC_UUID)
            if (cmdChar == null) {
                Log.e(TAG, "Characteristic UUID MISSING: $COMMAND_CHARACTERISTIC_UUID not found!")
                bharatService.characteristics.forEach { c ->
                    Log.d(TAG, "  Discovered Char: ${c.uuid} (properties=${c.properties})")
                }
                emitEvent(BleEvent.Error("Command characteristic UUID ($COMMAND_CHARACTERISTIC_UUID) not found"))
                _isCharacteristicReady.value = false
                return
            }

            val props = cmdChar.properties
            val isWritable = (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) ||
                    (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)

            if (!isWritable) {
                Log.e(TAG, "Command characteristic exists but does NOT support writing (properties=$props)")
                emitEvent(BleEvent.Error("Characteristic does not support write operations"))
                _isCharacteristicReady.value = false
                return
            }

            Log.i(TAG, "Characteristic UUID FOUND and WRITABLE: $COMMAND_CHARACTERISTIC_UUID (props=$props)")
            commandCharacteristic = cmdChar
            _isCharacteristicReady.value = true
            emitEvent(BleEvent.CharacteristicReady(COMMAND_CHARACTERISTIC_UUID.toString()))
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            writeTimeoutJob?.cancel()
            _isWriteInProgress.value = false

            val charUuid = characteristic?.uuid?.toString() ?: "unknown"
            Log.d(TAG, "onCharacteristicWrite: UUID=$charUuid, status=$status")

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Write result: SUCCESS for characteristic $charUuid")
                emitEvent(BleEvent.WriteSuccess("Command received successfully"))
            } else {
                Log.e(TAG, "Write result: FAILED with status $status")
                emitEvent(BleEvent.WriteFailed("Unknown", "Write failed with status $status"))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleDisconnection(reason: String) {
        writeTimeoutJob?.cancel()
        _isWriteInProgress.value = false
        _isCharacteristicReady.value = false
        _connectedDevice.value = null
        commandCharacteristic = null

        try {
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing BluetoothGatt: ${e.message}")
        }
        bluetoothGatt = null

        Log.i(TAG, "Disconnection completed: $reason")
        emitEvent(BleEvent.Disconnected(reason))
    }

    /**
     * Send command to BharatTag Arduino.
     * Allowed commands: "RING" and "STOP".
     */
    @SuppressLint("MissingPermission")
    fun sendCommand(command: String): Boolean {
        if (command != CMD_RING && command != CMD_STOP) {
            val errMsg = "Command rejected: '$command' is not an allowed command (only RING or STOP allowed)."
            Log.e(TAG, errMsg)
            emitEvent(BleEvent.Error(errMsg))
            return false
        }

        val gatt = bluetoothGatt
        val char = commandCharacteristic

        if (gatt == null || _connectedDevice.value == null) {
            val errMsg = "Cannot send command: Device is not connected"
            Log.e(TAG, errMsg)
            emitEvent(BleEvent.Error(errMsg))
            return false
        }

        if (char == null || !_isCharacteristicReady.value) {
            val errMsg = "Cannot send command: Command characteristic not ready"
            Log.e(TAG, errMsg)
            emitEvent(BleEvent.Error(errMsg))
            return false
        }

        if (_isWriteInProgress.value) {
            val errMsg = "Write in progress: Please wait for previous command to finish"
            Log.w(TAG, errMsg)
            emitEvent(BleEvent.Error(errMsg))
            return false
        }

        val bytes = command.toByteArray(Charsets.UTF_8)
        Log.i(TAG, "Command being sent: '$command' (${bytes.size} UTF-8 bytes)")
        emitEvent(BleEvent.CommandSent(command))

        _isWriteInProgress.value = true

        val props = char.properties
        val writeType = if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }

        val writeResult: Boolean = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val statusCode = gatt.writeCharacteristic(char, bytes, writeType)
                statusCode == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                char.value = bytes
                char.writeType = writeType
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException writing characteristic: ${se.message}")
            _isWriteInProgress.value = false
            emitEvent(BleEvent.WriteFailed(command, "Permission denied: ${se.message}"))
            false
        } catch (e: Exception) {
            Log.e(TAG, "Exception writing characteristic: ${e.message}")
            _isWriteInProgress.value = false
            emitEvent(BleEvent.WriteFailed(command, "Write exception: ${e.message}"))
            false
        }

        if (!writeResult) {
            _isWriteInProgress.value = false
            Log.e(TAG, "Write result: gatt.writeCharacteristic returned false")
            emitEvent(BleEvent.WriteFailed(command, "Bluetooth stack rejected characteristic write"))
            return false
        }

        writeTimeoutJob?.cancel()
        writeTimeoutJob = scope.launch {
            delay(WRITE_TIMEOUT_MS)
            if (_isWriteInProgress.value) {
                Log.w(TAG, "Write timeout: No callback received within ${WRITE_TIMEOUT_MS}ms")
                _isWriteInProgress.value = false
                emitEvent(BleEvent.WriteFailed(command, "Write operation timed out"))
            }
        }

        return true
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan("Disconnecting")
        writeTimeoutJob?.cancel()
        _isWriteInProgress.value = false
        _isCharacteristicReady.value = false

        val gatt = bluetoothGatt
        if (gatt != null) {
            Log.i(TAG, "Disconnecting from GATT connection…")
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing GATT: ${e.message}")
            }
            bluetoothGatt = null
        }

        _connectedDevice.value = null
        commandCharacteristic = null
        emitEvent(BleEvent.Disconnected("Disconnected by user"))
    }

    fun cleanup() {
        stopScan("Cleanup")
        disconnect()
        unregisterReceiver()
    }

    private fun emitEvent(event: BleEvent) {
        scope.launch {
            _events.emit(event)
        }
    }
}
