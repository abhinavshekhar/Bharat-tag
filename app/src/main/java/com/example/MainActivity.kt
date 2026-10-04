package com.example

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ui.BharatTagScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.BharatTagViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: BharatTagViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainContent(
                    viewModel = viewModel,
                    onOpenBluetoothSettings = { openBluetoothSettings() },
                    checkPermissions = { checkAllPermissionsGranted() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check Bluetooth state and permissions when returning from system settings
        val permissionsGranted = checkAllPermissionsGranted()
        viewModel.onPermissionsResult(permissionsGranted)
        viewModel.refreshBluetoothState()
    }

    private fun checkAllPermissionsGranted(): Boolean {
        val requiredPermissions = viewModel.getRequiredPermissionsList()
        return requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun openBluetoothSettings() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general settings
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
    }
}

@Composable
fun MainContent(
    viewModel: BharatTagViewModel,
    onOpenBluetoothSettings: () -> Unit,
    checkPermissions: () -> Boolean
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        viewModel.onPermissionsResult(allGranted)
    }

    // Auto-check and request permissions on first launch
    LaunchedEffect(Unit) {
        val alreadyGranted = checkPermissions()
        viewModel.onPermissionsResult(alreadyGranted)
        if (!alreadyGranted) {
            val needed = viewModel.getRequiredPermissionsList()
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    BharatTagScreen(
        viewModel = viewModel,
        onRequestPermissions = {
            val needed = viewModel.getRequiredPermissionsList()
            permissionLauncher.launch(needed.toTypedArray())
        },
        onOpenBluetoothSettings = onOpenBluetoothSettings,
        modifier = Modifier.fillMaxSize()
    )
}
