package de.refadim.rollladen

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

/** Hinweis „Automatik gesperrt“ mit dem Freigabe-Button (Rollläden-Seite und Ruhebildschirm) */
@Composable
fun LockBanner(vm: AppViewModel, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0B2), contentColor = Color(0xFF4E2600)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Automatik gesperrt", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Ein Taster wurde betätigt. Die Zeitpläne der Rollläden sind ausgesetzt, die Taster funktionieren weiter.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = { vm.unlockAll() }, enabled = !vm.unlocking) {
                Text(if (vm.unlocking) "Gebe frei …" else "Automatik freigeben")
            }
        }
    }
}

@Composable
private fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        value = LocalDateTime.now()
        delay(1_000)
    }
}

private val shadowStyle = TextStyle(shadow = Shadow(Color.Black, Offset(2f, 2f), 8f))

private fun tempText(t: Double?) = t?.let { "%.1f °C".format(Locale.GERMAN, it) } ?: "– °C"

/** Ruhebildschirm: Diashow und Wetter, per Wischen umschaltbar. Antippen öffnet die Steuerung. */
@Composable
fun IdleScreen(vm: AppViewModel) {
    val pager = rememberPagerState(pageCount = { 2 })
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            if (page == 0) SlideshowPage(vm) else WeatherPage(vm)
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)) {
            repeat(2) { i ->
                Box(
                    Modifier.padding(4.dp).size(9.dp).clip(CircleShape)
                        .background(if (pager.currentPage == i) Color.White else Color.White.copy(alpha = 0.4f))
                )
            }
        }
        FilledTonalButton(
            onClick = { vm.wake() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.Home, null)
            Spacer(Modifier.width(8.dp))
            Text("Steuerung")
        }
        if (vm.autoLocked) LockBanner(vm, Modifier.align(Alignment.TopCenter).padding(16.dp))
    }
}

@Composable
private fun SlideshowPage(vm: AppViewModel) {
    val images = vm.display.images
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(images) {
        index = 0
        while (images.size > 1) {
            delay(vm.display.intervalSeconds.coerceAtLeast(3) * 1000L)
            index = (index + 1) % images.size
        }
    }
    val now by rememberNow()

    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { vm.wake() } }) {
        if (images.isEmpty()) {
            Text(
                "Keine Bilder ausgewählt.\nEinstellungen → Ruhebildschirm & Wetter → Ordner wählen",
                color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        } else {
            Crossfade(
                targetState = images[index.coerceIn(0, images.lastIndex)],
                animationSpec = tween(1_200),
                label = "Diashow",
            ) { uri ->
                AsyncImage(
                    model = Uri.parse(uri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(28.dp)) {
            Text(now.format(clockFormat), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light, style = shadowStyle)
            Text(now.format(dateFormat), color = Color.White, fontSize = 22.sp, style = shadowStyle)
            if (vm.sensors.hasHost) {
                Text(
                    "Innen ${tempText(vm.indoorTemp)}  ·  Außen ${tempText(vm.outdoorTemp)}",
                    color = Color.White, fontSize = 20.sp, style = shadowStyle,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun WeatherPage(vm: AppViewModel) {
    val now by rememberNow()
    BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) { detectTapGestures { vm.wake() } }
    ) {
        val landscape = maxWidth > maxHeight
        val info: @Composable (Modifier) -> Unit = { mod ->
            Column(mod.background(MaterialTheme.colorScheme.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(now.format(clockFormat), fontSize = 56.sp, fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(now.format(dateFormat), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                TempTile("Innen", vm.indoorTemp, Modifier.fillMaxWidth())
                TempTile("Außen", vm.outdoorTemp, Modifier.fillMaxWidth())
                when {
                    !vm.sensors.hasHost -> Text(
                        "Temperaturfühler unter Einstellungen → Ruhebildschirm & Wetter eintragen.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    vm.tempError != null -> Text(
                        "Fühler: ${vm.tempError}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        val map: @Composable (Modifier) -> Unit = { mod ->
            // clipToBounds: die Karte zeichnet bei kleinen Zoomstufen sonst über ihren Bereich hinaus
            Box(mod.clipToBounds()) {
                WeatherMap(vm.settings, vm.display, Modifier.fillMaxSize())
                // Fängt Berührungen ab: die Karte bleibt fest, Wischen wechselt die Seite, Tippen öffnet die Steuerung
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { vm.wake() } })
            }
        }
        // Fester Bereich für Uhr und Temperaturen, die Karte füllt den Rest – unabhängig vom Zoom
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                map(Modifier.weight(1f).fillMaxHeight())
                info(Modifier.width(340.dp).fillMaxHeight().verticalScroll(rememberScrollState()))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                map(Modifier.weight(1f).fillMaxWidth())
                info(Modifier.fillMaxWidth().height(430.dp).verticalScroll(rememberScrollState()))
            }
        }
    }
}

@Composable
private fun TempTile(label: String, value: Double?, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Thermostat, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(tempText(value), fontSize = 44.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private val statusTime = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

/** Karte mit Grundkarte, optional Wolken (EUMETSAT) und Regenradar (DWD oder RainViewer) plus Statuszeile */
@Composable
private fun WeatherMap(settings: AppSettings, display: DisplaySettings, modifier: Modifier) {
    val context = LocalContext.current
    val mapView = remember {
        Configuration.getInstance().apply {
            setUserAgentValue(context.packageName)
            setOsmdroidBasePath(File(context.cacheDir, "osmdroid"))
            setOsmdroidTileCache(File(context.cacheDir, "osmdroid/tiles"))
        }
        MapView(context).apply {
            setMultiTouchControls(false)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setTilesScaledToDpi(true)
        }
    }
    val marker = remember { Marker(mapView).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) } }
    var status by remember { mutableStateOf("Lade Wetterdaten …") }

    // Ohne Standort: Mitte Deutschlands
    val lat = if (settings.hasLocation) settings.latitude else 51.16
    val lon = if (settings.hasLocation) settings.longitude else 10.45
    val zoom = if (display.radarSource == RadarSource.RAINVIEWER)
        display.mapZoom.coerceAtMost(WeatherSources.RAINVIEWER_MAX_ZOOM) else display.mapZoom

    LaunchedEffect(display.baseMap) {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.overlayManager.tilesOverlay.setColorFilter(WeatherSources.baseFilter(display.baseMap))
        mapView.invalidate()
    }

    LaunchedEffect(lat, lon, zoom) {
        mapView.controller.setZoom(zoom.toDouble())
        mapView.controller.setCenter(GeoPoint(lat, lon))
        mapView.overlays.remove(marker)
        if (settings.hasLocation) {
            marker.position = GeoPoint(lat, lon)
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }

    // Wetterebenen alle 5 Minuten neu laden (neuer Kachel-Cache je Zeitfenster)
    LaunchedEffect(display.radarSource, display.radarLayer, display.showClouds, display.cloudLayer, zoom, lat, lon) {
        var current = listOf<TilesOverlay>()
        while (true) {
            val stamp = System.currentTimeMillis() / 300_000
            val probeIndex = WeatherSources.tileIndexAt(lat, lon, zoom)
            val next = mutableListOf<TilesOverlay>()
            val parts = mutableListOf<String>()
            val nowText = LocalDateTime.now().format(statusTime)

            if (display.showClouds) {
                val src = WmsTileSource("EUM-$stamp-${display.cloudLayer}", WeatherSources.EUMETSAT_WMS, display.cloudLayer, stamp)
                val err = WeatherSources.probe(src.getTileURLString(probeIndex))
                parts += if (err == null) "Wolken: $nowText ✓" else "Wolken: Fehler ($err)"
                next += tilesOverlay(context, mapView, src, alpha = 0.6f)
            }
            when (display.radarSource) {
                RadarSource.OFF -> {}
                RadarSource.DWD -> {
                    val src = WmsTileSource("DWD-$stamp-${display.radarLayer}", WeatherSources.DWD_WMS, display.radarLayer, stamp)
                    val err = WeatherSources.probe(src.getTileURLString(probeIndex))
                    parts += if (err == null) "Regen (DWD): $nowText ✓" else "Regen (DWD): Fehler ($err)"
                    next += tilesOverlay(context, mapView, src, alpha = 1f)
                }
                RadarSource.RAINVIEWER -> {
                    try {
                        val frame = WeatherSources.rainViewerFrame()
                        val src = RainViewerTileSource("RV-${frame.time}", frame)
                        val err = WeatherSources.probe(src.getTileURLString(probeIndex))
                        val t = java.time.Instant.ofEpochSecond(frame.time).atZone(settings.zone).format(statusTime)
                        parts += if (err == null) "Regen (RainViewer): Stand $t ✓" else "Regen (RainViewer): Fehler ($err)"
                        next += tilesOverlay(context, mapView, src, alpha = 0.85f)
                    } catch (e: Exception) {
                        parts += "Regen (RainViewer): Fehler (${e.message ?: "nicht erreichbar"})"
                    }
                }
            }
            current.forEach {
                mapView.overlays.remove(it)
                it.onDetach(mapView)
            }
            // Wolken unten, Regen darüber, Markierung zuoberst
            next.forEachIndexed { i, o -> mapView.overlays.add(i, o) }
            current = next
            mapView.invalidate()
            status = if (parts.isEmpty()) "Keine Wetterebene gewählt" else parts.joinToString("  ·  ")
            delay(300_000)
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    val credits = buildList {
        if (display.radarSource == RadarSource.DWD) add("Radar: Deutscher Wetterdienst")
        if (display.radarSource == RadarSource.RAINVIEWER) add("Weather data by RainViewer")
        if (display.showClouds) add("Satellit: EUMETSAT")
        add(WeatherSources.baseAttribution(display.baseMap))
    }.joinToString(" · ")

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Text(
            status, fontSize = 13.sp, color = Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Text(
            credits, fontSize = 11.sp, color = Color.Black,
            modifier = Modifier.align(Alignment.BottomStart)
                .background(Color.White.copy(alpha = 0.75f)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun tilesOverlay(context: Context, mapView: MapView, src: OnlineTileSourceBase, alpha: Float): TilesOverlay {
    val provider = MapTileProviderBasic(context.applicationContext, src)
    provider.setTileRequestCompleteHandler(mapView.tileRequestCompleteHandler)
    return TilesOverlay(provider, context.applicationContext).apply {
        setLoadingBackgroundColor(android.graphics.Color.TRANSPARENT)
        setLoadingLineColor(android.graphics.Color.TRANSPARENT)
        if (alpha < 1f) setColorFilter(ColorMatrixColorFilter(ColorMatrix().apply { setScale(1f, 1f, 1f, alpha) }))
    }
}
