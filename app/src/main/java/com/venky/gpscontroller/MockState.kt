package com.venky.gpscontroller

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Target(val lat: Double, val lng: Double)

data class Favorite(val name: String, val lat: Double, val lng: Double)

/**
 * Shared between the UI and the service; everything here is touched on the main thread only.
 * The service re-reads it every tick, so changes take effect while the fake location is running.
 */
object MockState {
    val running = MutableStateFlow(false)
    /** The position currently reported to other apps. */
    val target = MutableStateFlow<Target?>(null)
    val error = MutableStateFlow<String?>(null)

    /** Travel speed for the route, and top speed for the joystick. */
    val speedKmh = MutableStateFlow(5.0)

    val route = MutableStateFlow<List<Target>>(emptyList())
    val routeActive = MutableStateFlow(false)
    val loop = MutableStateFlow(false)
    /** Index in [route] of the waypoint being walked towards. */
    var routeNext = 0

    val joystick = MutableStateFlow(false)
    /** Joystick deflection, each -1..1; x is east, y is south. */
    var joyX = 0f
    var joyY = 0f
}

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("gps_controller", Context.MODE_PRIVATE)

    fun lastTarget(): Target? {
        if (!prefs.contains("lat")) return null
        return Target(
            Double.fromBits(prefs.getLong("lat", 0)),
            Double.fromBits(prefs.getLong("lng", 0)),
        )
    }

    fun saveLastTarget(t: Target) {
        prefs.edit().putLong("lat", t.lat.toBits()).putLong("lng", t.lng.toBits()).apply()
    }

    fun favorites(): List<Favorite> {
        val raw = prefs.getString("favorites", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Favorite(o.getString("name"), o.getDouble("lat"), o.getDouble("lng"))
            }
        }.getOrDefault(emptyList())
    }

    fun saveFavorites(list: List<Favorite>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("name", it.name).put("lat", it.lat).put("lng", it.lng))
        }
        prefs.edit().putString("favorites", arr.toString()).apply()
    }
}
