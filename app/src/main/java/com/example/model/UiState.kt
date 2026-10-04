package com.example.model

sealed interface BleConnectionState {
    data object BluetoothUnavailable : BleConnectionState
    data object BluetoothDisabled : BleConnectionState
    data class PermissionRequired(val permissions: List<String>) : BleConnectionState
    data object Idle : BleConnectionState
    data object Scanning : BleConnectionState
    data class DeviceFound(val device: BleDeviceItem) : BleConnectionState
    data class Connecting(val device: BleDeviceItem) : BleConnectionState
    data class DiscoveringServices(val device: BleDeviceItem) : BleConnectionState
    data class Connected(val device: BleDeviceItem) : BleConnectionState
    data object Disconnected : BleConnectionState
    data class Error(val message: String) : BleConnectionState
}

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val isError: Boolean = false,
    val isSuccess: Boolean = false
)

data class BharatTagUiState(
    val connectionState: BleConnectionState = BleConnectionState.Idle,
    val bluetoothEnabled: Boolean = false,
    val bleSupported: Boolean = true,
    val hasRequiredPermissions: Boolean = false,
    val isScanning: Boolean = false,
    val scanProgressSecondsRemaining: Int = 0,
    val discoveredDevices: List<BleDeviceItem> = emptyList(),
    val selectedDevice: BleDeviceItem? = null,
    val connectedDevice: BleDeviceItem? = null,
    val isCharacteristicReady: Boolean = false,
    val isWriteInProgress: Boolean = false,
    val lastCommandSent: String? = null,
    val statusMessage: String? = null,
    val logs: List<LogEntry> = emptyList()
) {
    val isConnected: Boolean
        get() = connectionState is BleConnectionState.Connected && isCharacteristicReady
}
