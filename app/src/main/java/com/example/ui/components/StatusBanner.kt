package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BleConnectionState
import com.example.ui.theme.StopRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber

@Composable
fun StatusBanner(
    connectionState: BleConnectionState,
    bluetoothEnabled: Boolean,
    bleSupported: Boolean,
    isScanning: Boolean,
    scanRemainingSeconds: Int,
    statusMessage: String?,
    onOpenSettingsClick: () -> Unit,
    onRequestPermissionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !bleSupported || !bluetoothEnabled -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
            connectionState is BleConnectionState.Connected -> SuccessGreen.copy(alpha = 0.15f)
            connectionState is BleConnectionState.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
            connectionState is BleConnectionState.Connecting || connectionState is BleConnectionState.DiscoveringServices ->
                WarningAmber.copy(alpha = 0.15f)
            isScanning -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "statusBg"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("status_banner"),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status icon / indicator
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !bluetoothEnabled || !bleSupported -> StopRed.copy(alpha = 0.2f)
                                connectionState is BleConnectionState.Connected -> SuccessGreen.copy(alpha = 0.2f)
                                connectionState is BleConnectionState.Connecting || connectionState is BleConnectionState.DiscoveringServices ->
                                    WarningAmber.copy(alpha = 0.2f)
                                isScanning -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        !bleSupported -> {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Unsupported",
                                tint = StopRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        !bluetoothEnabled -> {
                            Icon(
                                imageVector = Icons.Default.BluetoothDisabled,
                                contentDescription = "Bluetooth Disabled",
                                tint = StopRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        isScanning -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        connectionState is BleConnectionState.Connecting || connectionState is BleConnectionState.DiscoveringServices -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = WarningAmber
                            )
                        }
                        connectionState is BleConnectionState.Connected -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Connected",
                                tint = SuccessGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        connectionState is BleConnectionState.Error -> {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = StopRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = "Bluetooth",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    val titleText = when {
                        !bleSupported -> "BLE Unsupported"
                        !bluetoothEnabled -> "Bluetooth is disabled"
                        connectionState is BleConnectionState.PermissionRequired -> "Permissions Required"
                        isScanning -> "Scanning... (${scanRemainingSeconds}s)"
                        connectionState is BleConnectionState.Connecting -> "Connecting to BharatTag…"
                        connectionState is BleConnectionState.DiscoveringServices -> "Discovering GATT services…"
                        connectionState is BleConnectionState.Connected -> "Connected"
                        connectionState is BleConnectionState.DeviceFound -> "BharatTag found"
                        connectionState is BleConnectionState.Disconnected -> "Disconnected"
                        connectionState is BleConnectionState.Error -> "Connection failed"
                        else -> "Bluetooth is enabled"
                    }

                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = when {
                            !bluetoothEnabled || !bleSupported || connectionState is BleConnectionState.Error -> StopRed
                            connectionState is BleConnectionState.Connected -> SuccessGreen
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )

                    if (!statusMessage.isNullOrBlank()) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Action button if Bluetooth is disabled
            AnimatedVisibility(visible = !bluetoothEnabled && bleSupported) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onOpenSettingsClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("enable_bluetooth_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Bluetooth Settings")
                    }
                }
            }

            // Action button if Permission is required
            AnimatedVisibility(visible = connectionState is BleConnectionState.PermissionRequired) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onRequestPermissionsClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("grant_permissions_button")
                    ) {
                        Text("Grant Permissions")
                    }
                }
            }
        }
    }
}
