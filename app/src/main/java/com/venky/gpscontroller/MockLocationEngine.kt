package com.venky.gpscontroller

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import com.google.android.gms.location.LocationServices

/**
 * Feeds a fake position into Android through the official test-provider API.
 * Works only while this app is the one chosen under Developer options > Select mock location app;
 * otherwise [start] and [push] throw SecurityException.
 *
 * When preinstalled on JakshyaOS ([isSystemApp]) the OS trusts this app instead: no developer
 * setting is needed and the OS does not flag these locations as mock.
 */
@SuppressLint("MissingPermission")
class MockLocationEngine(context: Context) {
    private val lm = context.getSystemService(LocationManager::class.java)
    // Most apps read Google's fused provider rather than LocationManager, so it is mocked as well.
    private val fused = LocationServices.getFusedLocationProviderClient(context)
    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    // Play services always flags its own mock locations, which would give the game away on
    // JakshyaOS; there the fused provider is left to follow the GPS and network providers.
    private val mockFused = !isSystemApp(context)

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
        if (mockFused) runCatching { fused.setMockMode(true) }
    }

    fun push(lat: Double, lng: Double, speedMs: Float = 0f, bearingDeg: Float = 0f) {
        for (p in providers) lm.setTestProviderLocation(p, build(p, lat, lng, speedMs, bearingDeg))
        if (mockFused) runCatching { fused.setMockLocation(build("fused", lat, lng, speedMs, bearingDeg)) }
    }

    fun stop() {
        for (p in providers) runCatching { lm.removeTestProvider(p) }
        if (mockFused) runCatching { fused.setMockMode(false) }
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

/** True when the app is part of the OS image, which is how it ships on JakshyaOS. */
fun isSystemApp(context: Context): Boolean =
    context.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
