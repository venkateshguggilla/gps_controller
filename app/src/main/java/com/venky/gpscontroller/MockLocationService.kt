package com.venky.gpscontroller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.Locale

/** Keeps pushing the chosen position once a second so other apps keep seeing it. */
class MockLocationService : Service() {
    private lateinit var engine: MockLocationEngine
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            val t = MockState.target.value
            if (t != null) {
                try {
                    engine.push(t.lat, t.lng)
                } catch (e: Exception) {
                    fail(e)
                    return
                }
            }
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        engine = MockLocationEngine(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            engine.start()
        } catch (e: Exception) {
            fail(e)
            return START_NOT_STICKY
        }
        MockState.error.value = null
        MockState.running.value = true
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        engine.stop()
        MockState.running.value = false
        super.onDestroy()
    }

    private fun fail(e: Exception) {
        MockState.error.value = if (e is SecurityException) {
            "GPS Controller is not selected as the mock location app in Developer options."
        } else {
            "Could not set the location: ${e.message ?: e.javaClass.simpleName}"
        }
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Fake location active", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val t = MockState.target.value
        val text = if (t != null) String.format(Locale.US, "%.5f, %.5f", t.lat, t.lng) else ""
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setContentTitle("Fake location is on")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null as Icon?, "Stop", stop).build())
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.venky.gpscontroller.STOP"
        private const val CHANNEL_ID = "mock_location"
        private const val NOTIFICATION_ID = 1
        private const val INTERVAL_MS = 1000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, MockLocationService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MockLocationService::class.java))
        }
    }
}
