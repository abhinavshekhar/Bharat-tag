package com.example.viewmodel

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ble.BharatTagBleManager
import com.example.model.BharatTagUiState
import com.example.model.BleConnectionState
import com.example.model.BleDeviceItem
import com.example.model.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BharatTagViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = BharatTagBleManager(application.applicationContext)

    private val _uiState = MutableStateFlow(BharatTagUiState())
    val uiState: StateFlow<BharatTagUiState> = _uiState.asStateFlow()

    init {
        checkInitialBluetoothState()
        observeBleEvents()
        observeBleManagerStates()
    }

    private fun checkInitialBluetoothState() {
        val bleSupported = bleManager.isBleSupported()
        val btEnabled = bleManager.isBluetoothEnabled()

        _uiState.update { current ->
            val initialConnectionState = when {
                !bleSupported -> BleConnectionState.BluetoothUnavailable
                !btEnabled -> BleConnectionState.BluetoothDisabled
                else -> BleConnectionState.Idle
            }

            current.copy(
                bleSupported = bleSupported,
                bluetoothEnabled = btEnabled,
                connectionState = initialConnectionState,
                statusMessage = when {
                    !bleSupported -> "Bluetooth Low Energy is not supported on this device"
                    !btEnabled -> "Bluetooth is disabled"
                    else -> "Bluetooth is enabled"
                }
            )
        }

        addLog(
            if (!bleSupported) "BLE hardware unsupported"
            else if (!btEnabled) "Bluetooth is disabled"
            else "Bluetooth is enabled and ready"
        )
    }

    fun onPermissionsResult(allGranted: Boolean) {
        Log.i(BharatTagBleManager.TAG, "Permissions updated: allGranted=$allGranted")
        _uiState.update { it.copy(hasRequiredPermissions = allGranted) }

        if (allGranted) {
            addLog("Bluetooth permissions granted", isSuccess = true)
            if (_uiState.value.bluetoothEnabled) {
                if (_uiState.value.connectionState is BleConnectionState.PermissionRequired) {
                    _uiState.update { it.copy(connectionState = BleConnectionState.Idle, statusMessage = "Ready to scan") }
                }
            } else {
                _uiState.update { it.copy(connectionState = BleConnectionState.BluetoothDisabled, statusMessage = "Bluetooth is disabled") }
            }
        } else {
            addLog("Bluetooth permissions denied", isError = true)
            val required = getRequiredPermissionsList()
            _uiState.update {
                it.copy(
                    connectionState = BleConnectionState.PermissionRequired(required),
                    statusMessage = "Bluetooth permissions are required to scan for BharatTag"
                )
            }
        }
    }

    fun getRequiredPermissionsList(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(
                android.Manifest.permission.BLUETOOTH,
                android.Manifest.permission.BLUETOOTH_ADMIN,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    private fun observeBleManagerStates() {
        viewModelScope.launch {
            bleManager.isScanning.collect { scanning ->
                _uiState.update { it.copy(isScanning = scanning) }
            }
        }

        viewModelScope.launch {
            bleManager.scanRemainingSeconds.collect { sec ->
                _uiState.update { it.copy(scanProgressSecondsRemaining = sec) }
            }
        }

        viewModelScope.launch {
            bleManager.discoveredDevices.collect { list ->
                _uiState.update { current ->
                    // Auto-select BharatTag if none selected
                    val best = current.selectedDevice ?: list.firstOrNull { it.isRecommended }
                    current.copy(
                        discoveredDevices = list,
                        selectedDevice = best
                    )
                }
            }
        }

        viewModelScope.launch {
            bleManager.connectedDevice.collect { connected ->
                _uiState.update { it.copy(connectedDevice = connected) }
            }
        }

        viewModelScope.launch {
            bleManager.isCharacteristicReady.collect { ready ->
                _uiState.update { it.copy(isCharacteristicReady = ready) }
            }
        }

        viewModelScope.launch {
            bleManager.isWriteInProgress.collect { inProgress ->
                _uiState.update { it.copy(isWriteInProgress = inProgress) }
            }
        }
    }

    private fun observeBleEvents() {
        viewModelScope.launch {
            bleManager.events.collect { event ->
                handleBleEvent(event)
            }
        }
    }

    private fun handleBleEvent(event: BharatTagBleManager.BleEvent) {
        when (event) {
            is BharatTagBleManager.BleEvent.ScanStarted -> {
                _uiState.update {
                    it.copy(
                        connectionState = BleConnectionState.Scanning,
                        statusMessage = "Scanning for BharatTag…"
                    )
                }
                addLog("BLE Scan started (10s duration)")
            }

            is BharatTagBleManager.BleEvent.ScanStopped -> {
                _uiState.update { current ->
                    val nextState = when {
                        current.connectedDevice != null -> BleConnectionState.Connected(current.connectedDevice)
                        current.discoveredDevices.isNotEmpty() -> BleConnectionState.DeviceFound(current.discoveredDevices.first())
                        else -> BleConnectionState.Idle
                    }
                    val msg = if (current.discoveredDevices.isEmpty()) {
                        "Scan stopped. BharatTag not found."
                    } else {
                        "Scan finished. Found ${current.discoveredDevices.size} device(s)."
                    }
                    current.copy(
                        connectionState = nextState,
                        statusMessage = msg
                    )
                }
                addLog("Scan stopped: ${event.reason}")
            }

            is BharatTagBleManager.BleEvent.ScanFailed -> {
                _uiState.update {
                    it.copy(
                        connectionState = BleConnectionState.Error("Scan failed: ${event.description}"),
                        statusMessage = "Scan failed (${event.description})"
                    )
                }
                addLog("Scan failed: ${event.description} (code ${event.errorCode})", isError = true)
            }

            is BharatTagBleManager.BleEvent.DeviceDiscovered -> {
                _uiState.update { current ->
                    current.copy(
                        connectionState = BleConnectionState.DeviceFound(event.device),
                        statusMessage = "BharatTag found: ${event.device.displayName}"
                    )
                }
                addLog("Found: ${event.device.displayName} [${event.device.address}] RSSI: ${event.device.rssi} dBm", isSuccess = true)
            }

            is BharatTagBleManager.BleEvent.Connecting -> {
                val dummyDevice = _uiState.value.selectedDevice ?: BleDeviceItem(
                    device = _uiState.value.discoveredDevices.firstOrNull()?.device ?: return,
                    name = event.deviceName,
                    address = event.address,
                    rssi = 0,
                    isBharatTagNamed = true,
                    hasBharatTagServiceUuid = true
                )
                _uiState.update {
                    it.copy(
                        connectionState = BleConnectionState.Connecting(dummyDevice),
                        statusMessage = "Connecting to ${event.deviceName}…"
                    )
                }
                addLog("Connecting to ${event.deviceName} [${event.address}]…")
            }

            is BharatTagBleManager.BleEvent.Connected -> {
                val dev = _uiState.value.connectedDevice
                _uiState.update { current ->
                    val item = dev ?: current.selectedDevice ?: current.discoveredDevices.firstOrNull()
                    if (item != null) {
                        current.copy(
                            connectionState = BleConnectionState.DiscoveringServices(item),
                            statusMessage = "Connected. Discovering services…"
                        )
                    } else current
                }
                addLog("GATT Connected to ${event.deviceName}. Discovering services…", isSuccess = true)
            }

            is BharatTagBleManager.BleEvent.ServicesDiscovered -> {
                addLog("GATT Services discovered (${event.serviceCount} services found)")
            }

            is BharatTagBleManager.BleEvent.CharacteristicReady -> {
                val dev = _uiState.value.connectedDevice ?: _uiState.value.selectedDevice
                if (dev != null) {
                    _uiState.update {
                        it.copy(
                            connectionState = BleConnectionState.Connected(dev),
                            statusMessage = "Connected to BharatTag"
                        )
                    }
                    addLog("BharatTag service and writable characteristic confirmed ready!", isSuccess = true)
                }
            }

            is BharatTagBleManager.BleEvent.Disconnected -> {
                _uiState.update {
                    it.copy(
                        connectionState = BleConnectionState.Disconnected,
                        statusMessage = "Disconnected: ${event.reason}",
                        isCharacteristicReady = false,
                        isWriteInProgress = false,
                        connectedDevice = null
                    )
                }
                addLog("Disconnected: ${event.reason}", isError = true)
            }

            is BharatTagBleManager.BleEvent.CommandSent -> {
                val msg = if (event.command == BharatTagBleManager.CMD_RING) {
                    "Ring command sent"
                } else {
                    "Stop command sent"
                }
                _uiState.update {
                    it.copy(
                        lastCommandSent = event.command,
                        statusMessage = msg
                    )
                }
                addLog("Sending command: ${event.command}…")
            }

            is BharatTagBleManager.BleEvent.WriteSuccess -> {
                val lastCmd = _uiState.value.lastCommandSent
                val userFeedback = if (lastCmd == BharatTagBleManager.CMD_RING) {
                    "Ring command sent"
                } else if (lastCmd == BharatTagBleManager.CMD_STOP) {
                    "Stop command sent"
                } else {
                    "Command delivered to BharatTag"
                }
                _uiState.update {
                    it.copy(statusMessage = userFeedback)
                }
                addLog("Write confirmed: $userFeedback", isSuccess = true)
            }

            is BharatTagBleManager.BleEvent.WriteFailed -> {
                val errMsg = "Failed to send ${event.command}: ${event.reason}"
                _uiState.update {
                    it.copy(statusMessage = errMsg)
                }
                addLog(errMsg, isError = true)
            }

            is BharatTagBleManager.BleEvent.Error -> {
                _uiState.update {
                    it.copy(
                        connectionState = BleConnectionState.Error(event.message),
                        statusMessage = event.message
                    )
                }
                addLog(event.message, isError = true)
            }

            is BharatTagBleManager.BleEvent.BluetoothStateChanged -> {
                _uiState.update { current ->
                    current.copy(
                        bluetoothEnabled = event.isEnabled,
                        connectionState = if (event.isEnabled) BleConnectionState.Idle else BleConnectionState.BluetoothDisabled,
                        statusMessage = if (event.isEnabled) "Bluetooth is enabled" else "Bluetooth is disabled"
                    )
                }
                addLog(if (event.isEnabled) "Bluetooth turned ON" else "Bluetooth turned OFF")
            }
        }
    }

    fun startScan() {
        if (!_uiState.value.hasRequiredPermissions) {
            val req = getRequiredPermissionsList()
            _uiState.update {
                it.copy(
                    connectionState = BleConnectionState.PermissionRequired(req),
                    statusMessage = "Please grant Bluetooth permissions to scan"
                )
            }
            return
        }

        if (!_uiState.value.bluetoothEnabled) {
            _uiState.update {
                it.copy(
                    connectionState = BleConnectionState.BluetoothDisabled,
                    statusMessage = "Bluetooth is disabled. Please enable it in Settings."
                )
            }
            return
        }

        bleManager.startScan()
    }

    fun stopScan() {
        bleManager.stopScan("User cancelled scan")
    }

    fun selectDevice(device: BleDeviceItem) {
        _uiState.update { it.copy(selectedDevice = device) }
        addLog("Selected target: ${device.displayName} [${device.address}]")
    }

    fun connect() {
        val target = _uiState.value.selectedDevice ?: _uiState.value.discoveredDevices.firstOrNull()
        if (target == null) {
            _uiState.update { it.copy(statusMessage = "No BharatTag device selected") }
            addLog("Connect clicked but no device selected", isError = true)
            return
        }
        bleManager.connect(target)
    }

    fun disconnect() {
        bleManager.disconnect()
    }

    fun sendRingCommand() {
        bleManager.sendCommand(BharatTagBleManager.CMD_RING)
    }

    fun sendStopCommand() {
        bleManager.sendCommand(BharatTagBleManager.CMD_STOP)
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun refreshBluetoothState() {
        checkInitialBluetoothState()
    }

    private fun addLog(message: String, isError: Boolean = false, isSuccess: Boolean = false) {
        val entry = LogEntry(message = message, isError = isError, isSuccess = isSuccess)
        _uiState.update { current ->
            val updated = (listOf(entry) + current.logs).take(50)
            current.copy(logs = updated)
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.cleanup()
    }
}
