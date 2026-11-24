package com.venky.gpscontroller

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Great-circle maths on a spherical Earth; accurate enough for moving a fake position. */
object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distance(a: Target, b: Target): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Compass bearing from [a] to [b] in degrees, 0 = north, 90 = east. */
    fun bearing(a: Target, b: Target): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    fun move(from: Target, bearingDeg: Double, meters: Double): Target {
        val d = meters / EARTH_RADIUS_M
        val brg = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(from.lat)
        val lng1 = Math.toRadians(from.lng)
        val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(brg))
        val lng2 = lng1 + atan2(sin(brg) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
        val lng = (Math.toDegrees(lng2) + 540) % 360 - 180
        return Target(Math.toDegrees(lat2).coerceIn(-89.9, 89.9), lng)
    }

    fun length(points: List<Target>): Double =
        points.zipWithNext { a, b -> distance(a, b) }.sum()
}
