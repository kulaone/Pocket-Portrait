package com.tinyprint.portraitstudio.dev

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tinyprint.portraitstudio.core.ble.BleController
import com.tinyprint.portraitstudio.core.ui.SettingsScreen
import com.tinyprint.portraitstudio.core.ui.theme.PortraitStudioTheme

class DiagnosticActivity : ComponentActivity() {

    private lateinit var bleController: BleController

    private val requiredPermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        bleController = BleController.getInstance(applicationContext)

        if (!hasPermissions()) {
            requestPermissions(requiredPermissions, 101)
        }

        setContent {
            PortraitStudioTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentScreen by remember { mutableStateOf("diagnostic") }

                    when (currentScreen) {
                        "diagnostic" -> {
                            DiagnosticScreen(
                                bleController = bleController,
                                onNavigateToSettings = { currentScreen = "settings" }
                            )
                        }
                        "settings" -> {
                            SettingsScreen(
                                onBack = { currentScreen = "diagnostic" }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun hasPermissions(): Boolean {
        return requiredPermissions.all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                Toast.makeText(this, "Bluetooth permissions are required for printing.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleController.disconnect()
    }
}
