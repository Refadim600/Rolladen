@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.refadim.rollladen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val timeoutChoices = listOf(0 to "Nie", 60 to "1 min", 120 to "2 min", 300 to "5 min", 600 to "10 min")

@Composable
fun DisplaySettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val d = vm.display

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.updateDisplay(vm.display.copy(folderUri = uri.toString(), images = emptyList()))
        }
    }

    // Alle Bilder des gewählten Ordners (URI-Strings, nach Dateiname sortiert)
    val folderImages by produceState(initialValue = emptyList<String>(), d.folderUri) {
        value = if (d.folderUri.isEmpty()) emptyList() else withContext(Dispatchers.IO) {
            runCatching {
                DocumentFile.fromTreeUri(context, Uri.parse(d.folderUri))?.listFiles()
                    ?.filter { it.isFile && it.type?.startsWith("image/") == true }
                    ?.sortedBy { it.name?.lowercase() ?: "" }
                    ?.map { it.uri.toString() }
            }.getOrNull() ?: emptyList()
        }
    }
    val selected = d.images.toSet()

    fun setSelection(newSet: Set<String>) {
        vm.updateDisplay(vm.display.copy(images = folderImages.filter { it in newSet }))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ruhebildschirm & Wetter") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
            )
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            fullWidth {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ruhebildschirm verwenden", Modifier.weight(1f))
                        Switch(d.screensaverEnabled, { vm.updateDisplay(d.copy(screensaverEnabled = it)) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bildschirm immer eingeschaltet lassen", Modifier.weight(1f))
                        Switch(d.keepScreenOn, { vm.updateDisplay(d.copy(keepScreenOn = it)) })
                    }
                    Text("Zurück zum Ruhebildschirm nach", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        timeoutChoices.forEach { (sec, label) ->
                            FilterChip(
                                selected = d.idleTimeoutSeconds == sec,
                                onClick = { vm.updateDisplay(d.copy(idleTimeoutSeconds = sec)) },
                                label = { Text(label) },
                            )
                        }
                    }
                    Text(
                        "Tipp: Unter Android-Einstellungen → Apps → Standard-Apps → Startbildschirm „Rollläden“ wählen. " +
                            "Dann startet das Display nach dem Einschalten direkt mit dem Ruhebildschirm.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    HorizontalDivider()
                    SectionTitle("Temperaturen (Shelly mit Plus Add-on)")
                    SensorSettingsBlock(vm)

                    HorizontalDivider()
                    SectionTitle("Wetterkarte")
                    Text("Kartenausschnitt (Zoomstufe)", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(6 to "Deutschland", 7 to "Region", 8 to "Umgebung", 9 to "Nah", 10 to "Sehr nah").forEach { (z, label) ->
                            FilterChip(
                                selected = d.mapZoom == z,
                                onClick = { vm.updateDisplay(d.copy(mapZoom = z)) },
                                label = { Text(label) },
                            )
                        }
                    }
                    OutlinedTextField(
                        d.radarLayer, { vm.updateDisplay(d.copy(radarLayer = it.trim())) },
                        label = { Text("DWD-Kartenebene") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Standard: dwd:Niederschlagsradar (aktueller Radarfilm des DWD, alle 5 min neu). Die Karte " +
                            "wird auf den Standort aus den Einstellungen zentriert. Daten: Deutscher Wetterdienst, kostenlos.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    HorizontalDivider()
                    SectionTitle("Diashow")
                    Text("Bildwechsel alle ${d.intervalSeconds} s", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = d.intervalSeconds.toFloat(),
                        onValueChange = { vm.updateDisplay(vm.display.copy(intervalSeconds = it.toInt())) },
                        valueRange = 5f..120f, steps = 22,
                    )
                    OutlinedButton(onClick = { folderPicker.launch(null) }) {
                        Icon(Icons.Filled.Folder, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (d.folderUri.isEmpty()) "Bilderordner wählen" else "Anderen Ordner wählen")
                    }
                    if (d.folderUri.isNotEmpty()) {
                        Text(
                            "${folderImages.size} Bilder im Ordner, ${d.images.size} ausgewählt. Antippen zum Aus-/Abwählen.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row {
                            TextButton(onClick = { setSelection(folderImages.toSet()) }) { Text("Alle auswählen") }
                            TextButton(onClick = { setSelection(emptySet()) }) { Text("Keine") }
                        }
                    }
                }
            }
            items(folderImages, key = { it }) { uri ->
                val on = uri in selected
                Box(
                    Modifier.aspectRatio(1f)
                        .border(if (on) 3.dp else 0.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { setSelection(if (on) selected - uri else selected + uri) }
                ) {
                    AsyncImage(
                        model = Uri.parse(uri), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (on) {
                        Icon(
                            Icons.Filled.CheckCircle, "Ausgewählt", tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun SensorSettingsBlock(vm: AppViewModel) {
    val s = vm.sensors
    val scope = rememberCoroutineScope()
    var indoorText by remember { mutableStateOf(s.indoorId.toString()) }
    var outdoorText by remember { mutableStateOf(s.outdoorId.toString()) }
    var found by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            s.host, { vm.updateSensors(vm.sensors.copy(host = it.trim())) },
            label = { Text("IP-Adresse des Shellys mit Add-on") }, placeholder = { Text("192.168.178.60") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            s.password, { vm.updateSensors(vm.sensors.copy(password = it)) },
            label = { Text("Passwort (falls gesetzt)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                indoorText, { t ->
                    indoorText = t
                    t.toIntOrNull()?.let { vm.updateSensors(vm.sensors.copy(indoorId = it)) }
                },
                label = { Text("Fühler innen (ID)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                outdoorText, { t ->
                    outdoorText = t
                    t.toIntOrNull()?.let { vm.updateSensors(vm.sensors.copy(outdoorId = it)) }
                },
                label = { Text("Fühler außen (ID)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                found = "Suche …"
                scope.launch {
                    found = try {
                        val list = vm.discoverSensors()
                        if (list.isEmpty()) "Keine Temperaturfühler gefunden. DS18B20 im Shelly-Webinterface unter Add-on einrichten."
                        else list.joinToString("\n") { (id, t) -> "ID $id: ${t?.let { "%.1f °C".format(it) } ?: "kein Wert"}" }
                    } catch (e: Exception) {
                        "✗ ${e.message}"
                    }
                    vm.refreshTempsNow()
                }
            }, enabled = s.hasHost) { Text("Fühler suchen") }
            Text(
                "Innen ${vm.indoorTemp?.let { "%.1f °C".format(it) } ?: "–"} · Außen ${vm.outdoorTemp?.let { "%.1f °C".format(it) } ?: "–"}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        found?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(
            "Die DS18B20 bekommen am Add-on die IDs 100, 101, … in der Reihenfolge, in der sie eingerichtet wurden. " +
                "„Fühler suchen“ zeigt die IDs mit aktuellem Wert, damit du innen und außen zuordnen kannst.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
