package com.skyler.caffeinate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.provider.Settings
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class CaffeinateService : Service() {

    private var screenOffReceiver: BroadcastReceiver? = null
    private var batteryLowReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_caffeinate_on)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                deactivateAndStop(context)
            }
        }
        registerReceiver(
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            Context.RECEIVER_NOT_EXPORTED
        )

        batteryLowReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                deactivateAndStop(context)
            }
        }
        registerReceiver(
            batteryLowReceiver,
            IntentFilter(Intent.ACTION_BATTERY_LOW),
            Context.RECEIVER_NOT_EXPORTED
        )

        return START_STICKY
    }

    private fun deactivateAndStop(context: Context) {
        val originalTimeout = CaffeinateState.getOriginalTimeout(context)
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            originalTimeout
        )
        CaffeinateState.setActive(context, false)
        TileService.requestListeningState(
            context,
            ComponentName(context, CaffeinateTileService::class.java)
        )
        stopSelf()
    }

    override fun onDestroy() {
        try { screenOffReceiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        try { batteryLowReceiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        private const val CHANNEL_ID = "caffeinate_channel"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, CaffeinateService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CaffeinateService::class.java))
        }
    }
}
