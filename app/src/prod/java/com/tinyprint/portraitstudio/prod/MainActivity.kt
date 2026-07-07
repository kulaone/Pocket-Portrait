package com.tinyprint.portraitstudio.prod

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyprint.portraitstudio.core.ble.BleController
import com.tinyprint.portraitstudio.core.ui.SettingsScreen
import com.tinyprint.portraitstudio.core.ui.theme.*

class MainActivity : ComponentActivity() {

    private val requiredPermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PortraitStudioTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentScreen by remember { mutableStateOf("consumer") }
                    var permissionsGranted by remember { mutableStateOf(hasPermissions()) }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { results ->
                        permissionsGranted = results.values.all { it }
                        if (!permissionsGranted) {
                            Toast.makeText(this, "Permissions are required for the app to function. Please enable them in settings.", Toast.LENGTH_LONG).show()
                        }
                    }

                    LaunchedEffect(Unit) {
                        if (!permissionsGranted) {
                            permissionLauncher.launch(requiredPermissions)
                        }
                    }

                    if (permissionsGranted) {
                        when (currentScreen) {
                            "consumer" -> {
                                ConsumerScreen(
                                    onNavigateToSettings = { currentScreen = "settings" }
                                )
                            }
                            "settings" -> {
                                SettingsScreen(
                                    onBack = { currentScreen = "consumer" }
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Slate900)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = NeonViolet,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Permissions Required",
                                    fontWeight = FontWeight.Bold,
                                    color = Slate100,
                                    fontSize = 20.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "This app requires Camera and Bluetooth permissions to capture photos and print them to your thermal printer.",
                                    color = Slate400,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = { permissionLauncher.launch(requiredPermissions) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                                ) {
                                    Text("Grant Permissions", color = Slate900, fontWeight = FontWeight.Bold)
                                }
                            }
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
}
