package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.BleConnectionState
import com.example.ui.components.CommandControlsSection
import com.example.ui.components.DeviceListSection
import com.example.ui.components.LogConsoleSection
import com.example.ui.components.StatusBanner
import com.example.ui.components.TrackerCard
import com.example.ui.theme.CyanAccent
import com.example.viewmodel.BharatTagViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BharatTagScreen(
    viewModel: BharatTagViewModel,
    onRequestPermissions: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPermissionExplainer by remember { mutableStateOf(false) }

    // Display transient status messages (such as "Ring command sent", "Stop command sent") in snackbar
    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            if (msg.contains("command sent", ignoreCase = true) ||
                msg.contains("failed", ignoreCase = true) ||
                msg.contains("error", ignoreCase = true)
            ) {
                snackbarHostState.showSnackbar(message = msg)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "BharatTag",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 24.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CyanAccent.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "BLE",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = CyanAccent
                                    )
                                )
                            }
                        }
                        Text(
                            text = "Smart Bluetooth Item Tracker",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshBluetoothState() },
                        modifier = Modifier.testTag("refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Status",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = { showPermissionExplainer = true },
                        modifier = Modifier.testTag("info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Permissions Info",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Bluetooth and Hardware Status Banner
                StatusBanner(
                    connectionState = uiState.connectionState,
                    bluetoothEnabled = uiState.bluetoothEnabled,
                    bleSupported = uiState.bleSupported,
                    isScanning = uiState.isScanning,
                    scanRemainingSeconds = uiState.scanProgressSecondsRemaining,
                    statusMessage = uiState.statusMessage,
                    onOpenSettingsClick = onOpenBluetoothSettings,
                    onRequestPermissionsClick = onRequestPermissions
                )

                // 2. Tracker Hero Card
                TrackerCard(
                    isConnected = uiState.isConnected,
                    isCharacteristicReady = uiState.isCharacteristicReady,
                    device = uiState.connectedDevice ?: uiState.selectedDevice,
                    lastCommandSent = uiState.lastCommandSent
                )

                // 3. Buzzer Commands Section (RING, STOP, DISCONNECT)
                CommandControlsSection(
                    isConnected = uiState.isConnected,
                    isCharacteristicReady = uiState.isCharacteristicReady,
                    isWriteInProgress = uiState.isWriteInProgress,
                    onRingClick = { viewModel.sendRingCommand() },
                    onStopClick = { viewModel.sendStopCommand() },
                    onDisconnectClick = { viewModel.disconnect() }
                )

                // 4. BLE Device Scanner and List Section
                DeviceListSection(
                    isScanning = uiState.isScanning,
                    scanRemainingSeconds = uiState.scanProgressSecondsRemaining,
                    discoveredDevices = uiState.discoveredDevices,
                    selectedDevice = uiState.selectedDevice,
                    isConnected = uiState.isConnected,
                    onScanClick = { viewModel.startScan() },
                    onStopScanClick = { viewModel.stopScan() },
                    onDeviceSelect = { device -> viewModel.selectDevice(device) },
                    onConnectClick = { viewModel.connect() }
                )

                // 5. Live Activity Console / Debug Logs
                LogConsoleSection(
                    logs = uiState.logs,
                    onClearLogsClick = { viewModel.clearLogs() }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showPermissionExplainer) {
        AlertDialog(
            onDismissRequest = { showPermissionExplainer = false },
            title = {
                Text(
                    text = "Bluetooth Permissions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "BharatTag communicates directly with your Arduino UNO R4 WiFi via Bluetooth Low Energy (BLE) GATT.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Android 12+ requires Nearby Devices permissions (BLUETOOTH_SCAN and BLUETOOTH_CONNECT) to discover and connect to tags without tracking location.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Android 11 and older requires ACCESS_FINE_LOCATION because BLE beacon scanning shares radio frequencies with beacon location detection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionExplainer = false
                        onRequestPermissions()
                    }
                ) {
                    Text("Grant Permissions")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionExplainer = false }) {
                    Text("Dismiss")
                }
            }
        )
    }
}
