package com.tinyprint.portraitstudio.core.ble

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PrintService : Service() {

    companion object {
        private const val CHANNEL_ID = "print_service_channel"
        private const val NOTIFICATION_ID = 1001

        private val _printProgress = MutableStateFlow<Int?>(null)
        val printProgress = _printProgress.asStateFlow()

        private val _printSuccess = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
        val printSuccess = _printSuccess.asSharedFlow()
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val data = intent?.getByteArrayExtra("extra_print_data")
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val initialNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Printing Portrait")
            .setContentText("Preparing transmission...")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, 0, false)
            .setOngoing(true)
            .build()

        startForeground(
            NOTIFICATION_ID,
            initialNotification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )

        val bleController = BleController.getInstance(this)
        _printProgress.value = 0

        serviceScope.launch {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val success = bleController.sendPrintJob(data) { progress ->
                _printProgress.value = progress

                val updatedNotification = NotificationCompat.Builder(this@PrintService, CHANNEL_ID)
                    .setContentTitle("Printing Portrait")
                    .setContentText("Sending data: $progress%")
                    .setSmallIcon(android.R.drawable.stat_sys_upload)
                    .setProgress(100, progress, false)
                    .setOngoing(true)
                    .build()
                notificationManager.notify(NOTIFICATION_ID, updatedNotification)
            }

            _printProgress.value = null
            _printSuccess.emit(success)

            val finalNotification = NotificationCompat.Builder(this@PrintService, CHANNEL_ID)
                .setContentTitle(if (success) "Print Completed" else "Print Failed")
                .setContentText(if (success) "Portrait printed successfully." else "An error occurred during print.")
                .setSmallIcon(if (success) android.R.drawable.stat_sys_upload_done else android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
                .build()

            stopForeground(STOP_FOREGROUND_REMOVE)

            notificationManager.notify(NOTIFICATION_ID + 1, finalNotification)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Printing Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows printing progress notifications"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
