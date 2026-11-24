package com.venky.gpscontroller

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import com.google.android.gms.location.LocationServices

/**
 * Feeds a fake position into Android through the official test-provider API.
 * Works only while this app is the one chosen under Developer options > Select mock location app;
 * otherwise [start] and [push] throw SecurityException.
 */
@SuppressLint("MissingPermission")
class MockLocationEngine(context: Context) {
    private val lm = context.getSystemService(LocationManager::class.java)
    // Most apps read Google's fused provider rather than LocationManager, so it is mocked as well.
    private val fused = LocationServices.getFusedLocationProviderClient(context)
    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

    fun start() {
        for (p in providers) {
            // A provider left over from an earlier run makes addTestProvider fail.
            runCatching { lm.removeTestProvider(p) }
            lm.addTestProvider(
                p,
                /* requiresNetwork = */ false,
                /* requiresSatellite = */ false,
                /* requiresCell = */ false,
                /* hasMonetaryCost = */ false,
                /* supportsAltitude = */ true,
                /* supportsSpeed = */ true,
                /* supportsBearing = */ true,
                /* powerUsage = low */ 1,
                /* accuracy = fine */ 1,
            )
            lm.setTestProviderEnabled(p, true)
        }
        runCatching { fused.setMockMode(true) }
    }

    fun push(lat: Double, lng: Double, speedMs: Float = 0f, bearingDeg: Float = 0f) {
        for (p in providers) lm.setTestProviderLocation(p, build(p, lat, lng, speedMs, bearingDeg))
        runCatching { fused.setMockLocation(build("fused", lat, lng, speedMs, bearingDeg)) }
    }

    fun stop() {
        for (p in providers) runCatching { lm.removeTestProvider(p) }
        runCatching { fused.setMockMode(false) }
    }

    private fun build(provider: String, lat: Double, lng: Double, speedMs: Float, bearingDeg: Float) = Location(provider).apply {
        latitude = lat
        longitude = lng
        altitude = 0.0
        accuracy = 3f
        speed = speedMs
        bearing = bearingDeg
        verticalAccuracyMeters = 3f
        speedAccuracyMetersPerSecond = 0.1f
        bearingAccuracyDegrees = 0.1f
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }
}
