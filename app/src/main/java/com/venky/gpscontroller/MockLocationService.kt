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
import android.os.SystemClock
import android.provider.Settings
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Keeps pushing the fake position so other apps keep seeing it, and moves it along the route
 * or by the joystick when either is active.
 */
class MockLocationService : Service() {
    private lateinit var engine: MockLocationEngine
    private val handler = Handler(Looper.getMainLooper())
    private var joystick: JoystickOverlay? = null
    private var lastTick = 0L
    private var bearing = 0f

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            // Capped so a stalled process does not make the position leap on resume.
            val dt = (now - lastTick).coerceIn(0L, 2000L) / 1000.0
            lastTick = now
            syncJoystick()

            var pos = MockState.target.value
            if (pos != null) {
                val maxSpeed = MockState.speedKmh.value / 3.6
                val deflection = hypot(MockState.joyX, MockState.joyY).coerceAtMost(1f)
                var speed = 0.0
                if (joystick != null && deflection > 0.1f) {
                    // The joystick overrides the route while it is held.
                    speed = maxSpeed * deflection
                    val angle = Math.toDegrees(atan2(MockState.joyX.toDouble(), -MockState.joyY.toDouble()))
                    bearing = ((angle + 360) % 360).toFloat()
                    pos = Geo.move(pos, bearing.toDouble(), speed * dt)
                } else if (MockState.routeActive.value) {
                    speed = maxSpeed
                    pos = advanceRoute(pos, speed * dt)
                    if (!MockState.routeActive.value) speed = 0.0
                }
                if (pos != MockState.target.value) MockState.target.value = pos
                try {
                    engine.push(pos.lat, pos.lng, speed.toFloat(), bearing)
                } catch (e: Exception) {
                    fail(e)
                    return
                }
            }
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    /** Moves [meters] along the route from [start], turning corners at waypoints. */
    private fun advanceRoute(start: Target, meters: Double): Target {
        var pos = start
        var left = meters
        // Bounded in case every waypoint is the same spot and looping is on.
        repeat(1000) {
            if (left <= 0) return pos
            val points = MockState.route.value
            if (points.size < 2) {
                MockState.routeActive.value = false
                return pos
            }
            if (MockState.routeNext >= points.size) {
                if (!MockState.loop.value) {
                    MockState.routeActive.value = false
                    return pos
                }
                MockState.routeNext = 0
            }
            val waypoint = points[MockState.routeNext]
            val d = Geo.distance(pos, waypoint)
            if (d <= left) {
                pos = waypoint
                left -= d
                MockState.routeNext++
            } else {
                bearing = Geo.bearing(pos, waypoint).toFloat()
                pos = Geo.move(pos, bearing.toDouble(), left)
                left = 0.0
            }
        }
        return pos
    }

    private fun syncJoystick() {
        val wanted = MockState.joystick.value && Settings.canDrawOverlays(this)
        if (wanted && joystick == null) {
            joystick = runCatching { JoystickOverlay(this).also { it.show() } }.getOrNull()
        } else if (!wanted && joystick != null) {
            removeJoystick()
        }
    }

    private fun removeJoystick() {
        joystick?.hide()
        joystick = null
        MockState.joyX = 0f
        MockState.joyY = 0f
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
        lastTick = SystemClock.elapsedRealtime()
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        removeJoystick()
        engine.stop()
        MockState.routeActive.value = false
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
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setContentTitle("Fake location is on")
            .setContentText("Other apps see the location you set.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null as Icon?, "Stop", stop).build())
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.venky.gpscontroller.STOP"
        private const val CHANNEL_ID = "mock_location"
        private const val NOTIFICATION_ID = 1
        private const val INTERVAL_MS = 500L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, MockLocationService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MockLocationService::class.java))
        }
    }
}
