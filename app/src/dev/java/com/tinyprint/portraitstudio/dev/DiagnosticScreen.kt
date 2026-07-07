package com.tinyprint.portraitstudio.dev

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyprint.portraitstudio.core.ble.BleController
import com.tinyprint.portraitstudio.core.image.DitherEngine
import com.tinyprint.portraitstudio.core.ui.theme.NeonCyan
import com.tinyprint.portraitstudio.core.ui.theme.NeonViolet
import com.tinyprint.portraitstudio.core.ui.theme.Slate100
import com.tinyprint.portraitstudio.core.ui.theme.Slate400
import com.tinyprint.portraitstudio.core.ui.theme.Slate700
import com.tinyprint.portraitstudio.core.ui.theme.Slate800
import com.tinyprint.portraitstudio.core.ui.theme.Slate900
import com.tinyprint.portraitstudio.core.ui.theme.SuccessGreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticScreen(
    bleController: BleController,
    onNavigateToSettings: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val logs = remember { mutableStateListOf<String>() }
    val lazyListState = rememberLazyListState()

    fun log(msg: String, isError: Boolean = false) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val tag = if (isError) "[ERROR]" else "[INFO]"
        logs.add("$time $tag $msg")
    }

    val connectionState by bleController.connectionState.collectAsState()
    val connectedDeviceName by bleController.connectedDeviceName.collectAsState()
    val discoveredPrinters by bleController.discoveredPrinters.collectAsState()

    var binarizationThreshold by remember { mutableFloatStateOf(128f) }
    var contrastBoost by remember { mutableFloatStateOf(0f) }
    var ditherDetailScale by remember { mutableFloatStateOf(1.0f) }
    var isScanning by remember { mutableStateOf(false) }
    var printProgress by remember { mutableStateOf<Int?>(null) }

    // Scroll console log to bottom on new log entry
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            lazyListState.animateScrollToItem(logs.size - 1)
        }
    }

    LaunchedEffect(connectionState) {
        log("Connection State Changed: $connectionState")
        if (connectionState == BleController.ConnectionState.CONNECTED) {
            log("Resolved Characteristic write link: X6h GATT services discovered")
        }
    }

    LaunchedEffect(Unit) {
        bleController.statusLogs.collect { msg ->
            log(msg, isError = msg.contains("Error", ignoreCase = true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Diagnostic Terminal", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = Slate100)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate900,
                    titleContentColor = Slate100
                )
            )
        },
        containerColor = Slate900
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // BLE Section Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Printer Status: $connectionState" + if (connectedDeviceName != null) " ($connectedDeviceName)" else "",
                        fontWeight = FontWeight.Bold,
                        color = if (connectionState == BleController.ConnectionState.CONNECTED) SuccessGreen else Slate100,
                        fontSize = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                if (isScanning) {
                                    bleController.stopScan()
                                    isScanning = false
                                    log("Scanning stopped manually")
                                } else {
                                    bleController.startScan()
                                    isScanning = true
                                    log("Started Scanning for printers...")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isScanning) "Stop Scanning" else "Scan for Printers")
                        }
                        
                        if (connectionState != BleController.ConnectionState.DISCONNECTED) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    bleController.disconnect()
                                    log("Disconnect requested")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
                            ) {
                                Text("Disconnect")
                            }
                        }
                    }

                    // Scanned printers list
                    if (discoveredPrinters.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Discovered Printers:", fontWeight = FontWeight.Bold, color = Slate400, fontSize = 12.sp)
                        LazyColumn(modifier = Modifier.height(100.dp)) {
                            items(discoveredPrinters) { device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${device.name ?: "Unknown"} (${device.address})",
                                        color = Slate100,
                                        fontSize = 13.sp
                                    )
                                    Button(
                                        onClick = {
                                            bleController.stopScan()
                                            isScanning = false
                                            bleController.connect(device.address)
                                            log("Connecting to MAC ${device.address}...")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Connect", fontSize = 11.sp, color = Slate900)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sliders Config Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Low-Level Dither Variables", fontWeight = FontWeight.Bold, color = Slate100, fontSize = 14.sp)
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    // Binarization threshold
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Binarization Threshold: ${binarizationThreshold.toInt()}", color = Slate400, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Slider(
                            value = binarizationThreshold,
                            onValueChange = { binarizationThreshold = it },
                            valueRange = 10f..240f,
                            modifier = Modifier.width(180.dp),
                            colors = SliderDefaults.colors(thumbColor = NeonViolet, activeTrackColor = NeonViolet)
                        )
                    }

                    // Contrast Boost
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Contrast Boost: ${contrastBoost.toInt()}%", color = Slate400, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Slider(
                            value = contrastBoost,
                            onValueChange = { contrastBoost = it },
                            valueRange = -100f..100f,
                            modifier = Modifier.width(180.dp),
                            colors = SliderDefaults.colors(thumbColor = NeonViolet, activeTrackColor = NeonViolet)
                        )
                    }

                    // Dither detail scale
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Dither Detail Scale: ${"%.1f".format(ditherDetailScale)}x", color = Slate400, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Slider(
                            value = ditherDetailScale,
                            onValueChange = { ditherDetailScale = it },
                            valueRange = 0.5f..2.0f,
                            modifier = Modifier.width(180.dp),
                            colors = SliderDefaults.colors(thumbColor = NeonViolet, activeTrackColor = NeonViolet)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Items Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            log("Building darkness calibration pattern (120 rows solid black)...")
                            val testBuffer = DarknessTest.generateTestBuffer(
                                rowsCount = 120,
                                motorSpeedDivisor = 0x28, // extra slow
                                qualityLattice = 0x35,    // level 5
                                heatingEnergy = 0xFFFF    // max energy
                            )
                            log("Sending Darkness test job buffer (${testBuffer.size} bytes)...")
                            printProgress = 0
                            val success = bleController.sendPrintJob(testBuffer) { progress ->
                                printProgress = progress
                            }
                            if (success) {
                                log("Darkness grid print job completed successfully.", isError = false)
                            } else {
                                log("Darkness grid print job failed.", isError = true)
                            }
                            printProgress = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    modifier = Modifier.weight(1f),
                    enabled = connectionState == BleController.ConnectionState.CONNECTED && printProgress == null
                ) {
                    Icon(imageVector = Icons.Default.Print, contentDescription = null, tint = Slate900)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Darkness Grid", color = Slate900, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        coroutineScope.launch {
                            log("Building feed packet (80 lines)...")
                            val feedPayload = byteArrayOf(0x50.toByte(), 0x00.toByte())
                            val packet = DitherEngine.wrapPacket(0xA1, feedPayload)
                            log("Sending Paper Feed command...")
                            val success = bleController.sendPrintJob(packet) { }
                            if (success) {
                                log("Feed command executed successfully.")
                            } else {
                                log("Feed command failed.", isError = true)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                    modifier = Modifier.weight(1f),
                    enabled = connectionState == BleController.ConnectionState.CONNECTED && printProgress == null
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Slate100)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Feed 80 lines", color = Slate100, fontSize = 12.sp)
                }
            }

            if (printProgress != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Printing Progress: $printProgress%",
                    color = NeonCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Raw Hex Terminal Output
            Text("Diagnostic Hex Terminal Console", fontWeight = FontWeight.Bold, color = Slate100, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
                    .border(1.dp, Slate700, MaterialTheme.shapes.small)
                    .padding(8.dp)
            ) {
                items(logs) { line ->
                    Text(
                        text = line,
                        color = if (line.contains("[ERROR]")) Color.Red else SuccessGreen,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
