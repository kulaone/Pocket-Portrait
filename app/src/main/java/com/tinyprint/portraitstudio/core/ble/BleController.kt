package com.tinyprint.portraitstudio.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@SuppressLint("MissingPermission")
class BleController private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + kotlinx.coroutines.SupervisorJob())
    private var connectTimeoutJob: kotlinx.coroutines.Job? = null
    private var negotiatedMtu = 23

    private fun cancelConnectTimeout() {
        connectTimeoutJob?.cancel()
        connectTimeoutJob = null
    }

    companion object {
        private const val TAG = "BleController"

        @Volatile
        private var INSTANCE: BleController? = null

        fun getInstance(context: Context): BleController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BleController(context.applicationContext).also { INSTANCE = it }
            }
        }

        // List of candidate Service UUIDs supporting different printer hardware/firmware versions
        val SERVICE_UUIDS = listOf(
            UUID.fromString("0000ae30-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000ae00-0000-1000-8000-00805f9b34fb")
        )
        val WRITE_UUID: UUID = UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb")
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private val _discoveredPrinters = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredPrinters = _discoveredPrinters.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName = _connectedDeviceName.asStateFlow()

    // SharedFlow of raw BLE event logs to display in the terminal console
    private val _statusLogs = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val statusLogs = _statusLogs.asSharedFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var isScanning = false

    private fun logStatus(msg: String) {
        Log.d(TAG, msg)
        _statusLogs.tryEmit(msg)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: return
            
            if (name.startsWith("X6h-", ignoreCase = true) || 
                name.startsWith("X6-", ignoreCase = true) || 
                name.equals("X6", ignoreCase = true)) {
                
                val currentList = _discoveredPrinters.value
                if (currentList.none { it.address == device.address }) {
                    _discoveredPrinters.value = currentList + device
                    logStatus("Discovered printer: $name (${device.address})")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            logStatus("Scan failed with error code: $errorCode")
        }
    }

    fun startScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            logStatus("Error: BluetoothLeScanner is null (check Bluetooth adapter state)")
            return
        }
        if (isScanning) return
        _discoveredPrinters.value = emptyList()
        isScanning = true

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, settings, scanCallback)
        logStatus("Started scanning for BLE devices (filtering by name X6h/X6)")
        
        CoroutineScope(Dispatchers.Default).launch {
            delay(10000)
            if (isScanning) {
                stopScan()
            }
        }
    }

    fun stopScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        if (!isScanning) return
        isScanning = false
        scanner.stopScan(scanCallback)
        logStatus("Stopped scanning")
    }

    fun connect(address: String) {
        val adapter = bluetoothAdapter ?: run {
            logStatus("Error: Bluetooth adapter is null")
            return
        }
        val device = adapter.getRemoteDevice(address) ?: run {
            logStatus("Error: Could not resolve device address $address")
            return
        }

        synchronized(this) {
            cancelConnectTimeout()
            bluetoothGatt?.close()
            bluetoothGatt = null
            writeCharacteristic = null
            negotiatedMtu = 23
        }

        _connectionState.value = ConnectionState.CONNECTING
        _connectedDeviceName.value = device.name ?: "Unknown Printer"
        logStatus("Connecting to ${device.name} ($address)...")

        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

        connectTimeoutJob = scope.launch {
            delay(10000) // 10 seconds timeout
            if (_connectionState.value == ConnectionState.CONNECTING) {
                logStatus("Connection timeout after 10s. Disconnecting...")
                disconnect()
            }
        }
    }

    fun disconnect() {
        cancelConnectTimeout()
        if (bluetoothGatt == null) {
            logStatus("Disconnect requested but BluetoothGatt is null")
            _connectionState.value = ConnectionState.DISCONNECTED
            _connectedDeviceName.value = null
            return
        }
        logStatus("Disconnecting from printer...")
        bluetoothGatt?.disconnect()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                logStatus("GATT error state change: status=$status, newState=$newState")
                cancelConnectTimeout()
                gatt.close()
                if (gatt == bluetoothGatt) {
                    bluetoothGatt = null
                    writeCharacteristic = null
                    negotiatedMtu = 23
                }
                _connectionState.value = ConnectionState.DISCONNECTED
                _connectedDeviceName.value = null
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                logStatus("Physically connected to GATT server. Initiating service discovery...")
                CoroutineScope(Dispatchers.Main).launch {
                    delay(300) // Allow connection state to settle
                    val started = gatt.discoverServices()
                    logStatus("GATT discoverServices started: $started")
                    if (!started) {
                        logStatus("Error: discoverServices failed to start.")
                        disconnect()
                    }
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                logStatus("Physically disconnected from GATT server.")
                cancelConnectTimeout()
                gatt.close()
                if (gatt == bluetoothGatt) {
                    bluetoothGatt = null
                    writeCharacteristic = null
                    negotiatedMtu = 23
                }
                _connectionState.value = ConnectionState.DISCONNECTED
                _connectedDeviceName.value = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                logStatus("GATT Services discovered successfully.")
                // Search for any of our candidate service UUIDs
                var targetService: BluetoothGattService? = null
                for (uuid in SERVICE_UUIDS) {
                    val s = gatt.getService(uuid)
                    if (s != null) {
                        targetService = s
                        logStatus("TinyPrint Service ($uuid) found.")
                        break
                    }
                }

                if (targetService != null) {
                    val characteristic = targetService.getCharacteristic(WRITE_UUID)
                    if (characteristic != null) {
                        writeCharacteristic = characteristic
                        _connectionState.value = ConnectionState.CONNECTED
                        cancelConnectTimeout()
                        logStatus("GATT Write Characteristic resolved successfully: $WRITE_UUID")
                        
                        // Request larger MTU
                        logStatus("Requesting larger MTU...")
                        val res = gatt.requestMtu(512)
                        logStatus("Request MTU initiated: $res")
                    } else {
                        logStatus("Error: GATT Write Characteristic ($WRITE_UUID) not found.")
                        disconnect()
                    }
                } else {
                    logStatus("Error: TinyPrint GATT Service (ae30/ae00) not found.")
                    disconnect()
                }
            } else {
                logStatus("Error: Services discovery failed with status $status.")
                disconnect()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                logStatus("MTU changed to: $mtu")
                negotiatedMtu = mtu
            } else {
                logStatus("MTU negotiation failed, status=$status")
            }
        }
    }

    suspend fun sendPrintJob(buffer: ByteArray, onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val gatt = bluetoothGatt
        val char = writeCharacteristic
        if (gatt == null || char == null) {
            logStatus("Error: Print requested but gatt=$gatt, characteristic=$char (not ready)")
            return@withContext false
        }

        // Limit maximum chunk size to 128 bytes to prevent printer hardware buffer overflow,
        // but scale up from 20 bytes if MTU allows.
        val chunkSize = minOf(128, maxOf(20, negotiatedMtu - 3))
        val totalBytes = buffer.size
        var offset = 0

        logStatus("Sending job: $totalBytes bytes total ($chunkSize-byte chunks)...")

        try {
            while (offset < totalBytes) {
                if (_connectionState.value != ConnectionState.CONNECTED || bluetoothGatt == null || writeCharacteristic == null) {
                    logStatus("Error: Lost connection to printer mid-print.")
                    return@withContext false
                }

                val count = minOf(chunkSize, totalBytes - offset)
                val chunk = ByteArray(count)
                System.arraycopy(buffer, offset, chunk, 0, count)

                val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val res = gatt.writeCharacteristic(char, chunk, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                    res == android.bluetooth.BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    char.value = chunk
                    @Suppress("DEPRECATION")
                    char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(char)
                }

                if (!success) {
                    logStatus("Error: Failed to write chunk at offset $offset")
                    return@withContext false
                }

                offset += count
                val progress = (offset * 100 / totalBytes)
                withContext(Dispatchers.Main) {
                    onProgress(progress)
                }

                delay(15) // Throttling delay
            }
            logStatus("Print data transmission completed successfully.")
            return@withContext true
        } catch (e: Exception) {
            logStatus("Error: GATT write chunk exception: ${e.message}")
            return@withContext false
        }
    }
}
