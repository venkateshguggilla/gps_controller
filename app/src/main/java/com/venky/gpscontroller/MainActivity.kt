package com.venky.gpscontroller

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            load(applicationContext, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            // The tile server requires a user agent; keep it generic.
            userAgentValue = "GpsController/0.1"
        }
        if (MockState.target.value == null) MockState.target.value = Prefs(this).lastTarget()
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) { GpsControllerScreen() }
        }
    }
}

@Composable
private fun GpsControllerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val store = remember { Prefs(context) }

    val running by MockState.running.collectAsStateWithLifecycle()
    val target by MockState.target.collectAsStateWithLifecycle()
    val error by MockState.error.collectAsStateWithLifecycle()

    var favorites by remember { mutableStateOf(store.favorites()) }
    var isMockApp by remember { mutableStateOf(isMockLocationApp(context)) }
    var query by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isMockApp = isMockLocationApp(context) }

    fun select(t: Target) {
        message = null
        MockState.target.value = t
        store.saveLastTarget(t)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasLocationPermission(context)) {
            MockLocationService.start(context)
        } else {
            message = "Location permission is needed to run the fake location service."
        }
    }

    fun start() {
        message = null
        MockState.error.value = null
        if (hasLocationPermission(context)) {
            MockLocationService.start(context)
        } else {
            val wanted = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                wanted += Manifest.permission.POST_NOTIFICATIONS
            }
            permissionLauncher.launch(wanted.toTypedArray())
        }
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        focus.clearFocus()
        parseLatLng(q)?.let { select(it); return }
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    Geocoder(context, Locale.getDefault()).getFromLocationName(q, 1)?.firstOrNull()
                }.getOrNull()
            }
            if (found != null) select(Target(found.latitude, found.longitude))
            else message = "No place found for \"$q\"."
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!isMockApp) SetupCard(onMessage = { message = it })

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("Search a place, or type lat, lng") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                trailingIcon = { TextButton(onClick = { search() }) { Text("Go") } },
            )

            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                MapPicker(
                    target = target,
                    onPick = { select(it) },
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    "© OpenStreetMap contributors",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.White.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp),
                )
            }

            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val t = target
                Text(
                    if (t != null) String.format(Locale.US, "%.6f, %.6f", t.lat, t.lng)
                    else "Tap the map to choose a location",
                    style = MaterialTheme.typography.titleMedium,
                )
                (error ?: message)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (running) {
                        Button(
                            onClick = { MockLocationService.stop(context) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) { Text("Stop fake location") }
                    } else {
                        Button(
                            onClick = { start() },
                            enabled = t != null && isMockApp,
                            modifier = Modifier.weight(1f),
                        ) { Text("Start fake location") }
                    }
                    OutlinedButton(onClick = { showSaveDialog = true }, enabled = t != null) {
                        Text("Save")
                    }
                }
                if (favorites.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(favorites) { fav ->
                            InputChip(
                                selected = t != null && t.lat == fav.lat && t.lng == fav.lng,
                                onClick = { select(Target(fav.lat, fav.lng)) },
                                label = { Text(fav.name) },
                                trailingIcon = {
                                    Text(
                                        "✕",
                                        modifier = Modifier
                                            .clickable {
                                                favorites = favorites - fav
                                                store.saveFavorites(favorites)
                                            }
                                            .padding(4.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    val t = target
    if (showSaveDialog && t != null) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save this location") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        favorites = favorites + Favorite(name.trim(), t.lat, t.lng)
                        store.saveFavorites(favorites)
                        showSaveDialog = false
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SetupCard(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("One-time setup needed", style = MaterialTheme.typography.titleMedium)
            Text(
                "Open Developer options, tap \"Select mock location app\" and choose GPS Controller. " +
                    "If Developer options is missing, go to Settings > About phone and tap Build number 7 times."
            )
            Button(onClick = {
                val opened = runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                }.isSuccess
                if (!opened) {
                    onMessage("Developer options is off. Tap Build number 7 times in About phone.")
                    runCatching { context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) }
                }
            }) { Text("Open Developer options") }
        }
    }
}

@Composable
private fun MapPicker(target: Target?, onPick: (Target) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val currentOnPick by rememberUpdatedState(onPick)

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 3.0
            controller.setZoom(4.0)
            controller.setCenter(GeoPoint(20.0, 78.0))
        }
    }
    val marker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setOnMarkerClickListener { _, _ -> true }
        }
    }

    DisposableEffect(mapView) {
        val taps = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                currentOnPick(Target(p.latitude, p.longitude))
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        })
        mapView.overlays.add(0, taps)
        onDispose { mapView.onDetach() }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { mapView.onResume() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { mapView.onPause() }

    LaunchedEffect(target) {
        if (target != null) {
            val point = GeoPoint(target.lat, target.lng)
            marker.position = point
            if (marker !in mapView.overlays) mapView.overlays.add(marker)
            if (mapView.zoomLevelDouble < 10.0) mapView.controller.setZoom(15.0)
            mapView.controller.animateTo(point)
            mapView.invalidate()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun isMockLocationApp(context: Context): Boolean {
    val ops = context.getSystemService(AppOpsManager::class.java)
    return runCatching {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)
}

private fun hasLocationPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Accepts "12.9716, 77.5946" or "12.9716 77.5946". */
private fun parseLatLng(text: String): Target? {
    val parts = text.split(',', ' ').filter { it.isNotBlank() }
    if (parts.size != 2) return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lng = parts[1].toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return Target(lat, lng)
}
