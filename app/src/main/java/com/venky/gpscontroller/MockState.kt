package com.venky.gpscontroller

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Target(val lat: Double, val lng: Double)

data class Favorite(val name: String, val lat: Double, val lng: Double)

/** Shared between the UI and the service. The service re-reads [target] every tick. */
object MockState {
    val running = MutableStateFlow(false)
    val target = MutableStateFlow<Target?>(null)
    val error = MutableStateFlow<String?>(null)
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
