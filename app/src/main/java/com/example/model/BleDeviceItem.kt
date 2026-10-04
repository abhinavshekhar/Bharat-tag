package com.example.model

import android.bluetooth.BluetoothDevice

data class BleDeviceItem(
    val device: BluetoothDevice,
    val name: String?,
    val address: String,
    val rssi: Int,
    val isBharatTagNamed: Boolean,
    val hasBharatTagServiceUuid: Boolean,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
) {
    val displayName: String
        get() = when {
            !name.isNullOrBlank() -> name
            isBharatTagNamed -> "BharatTag"
            else -> "Unknown BLE Device"
        }

    val isRecommended: Boolean
        get() = isBharatTagNamed || hasBharatTagServiceUuid
}
