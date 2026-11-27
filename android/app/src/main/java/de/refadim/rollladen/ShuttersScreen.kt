@file:OptIn(ExperimentalMaterial3Api::class)

package de.refadim.rollladen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val OpenGreen = Color(0xFF2E7D32)

@Composable
fun ShuttersScreen(vm: AppViewModel, onEdit: (String) -> Unit) {
    LaunchedEffect(Unit) { vm.refreshAll() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rollläden") },
                actions = {
                    IconButton(onClick = { vm.refreshAll() }) { Icon(Icons.Filled.Refresh, "Aktualisieren") }
                    IconButton(onClick = { vm.addShutter() }) { Icon(Icons.Filled.Add, "Rollladen hinzufügen") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OpenAllButton(busy = vm.groupBusy) { vm.runOnAll("Alle öffnen") { c, id -> c.open(id) } }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { vm.runOnAll("Alle stoppen") { c, id -> c.stop(id) } },
                    enabled = !vm.groupBusy, modifier = Modifier.weight(1f).height(48.dp),
                ) { Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(6.dp)); Text("Alle stoppen") }
                OutlinedButton(
                    onClick = { vm.runOnAll("Alle schließen") { c, id -> c.close(id) } },
                    enabled = !vm.groupBusy, modifier = Modifier.weight(1f).height(48.dp),
                ) { Icon(Icons.Filled.VerticalAlignBottom, null); Spacer(Modifier.width(6.dp)); Text("Alle schließen") }
            }

            vm.banner?.let { text ->
                Card(
                    onClick = { vm.banner = null },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) { Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
            }

            if (vm.autoLocked) LockBanner(vm, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 340.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(vm.shutters, key = { it.id }) { s ->
                    ShutterCard(s, vm.status[s.id], onClick = { onEdit(s.id) }, onCommand = { op -> vm.command(s, op) })
                }
            }
        }
    }
}

/** Die hervorgehobene Schaltfläche: öffnet sofort alle Rollläden. */
@Composable
fun OpenAllButton(busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = OpenGreen, contentColor = Color.White),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp).height(84.dp),
    ) {
        if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
        else Icon(Icons.Filled.VerticalAlignTop, null, Modifier.size(34.dp))
        Spacer(Modifier.width(16.dp))
        Text("ALLE ÖFFNEN", fontSize = 26.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ShutterCard(
    s: Shutter,
    status: Result<CoverStatus>?,
    onClick: () -> Unit,
    onCommand: (suspend (ShellyClient, Int) -> Unit) -> Unit,
) {
    val subtitle = when {
        !s.hasHost -> "Keine IP-Adresse – tippen zum Einrichten"
        status == null -> "…"
        status.isFailure -> status.exceptionOrNull()?.message ?: "Fehler"
        else -> status.getOrNull()!!.let { st -> st.position?.let { "${st.stateText} · $it % offen" } ?: st.stateText }
    }
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status?.isFailure == true) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (s.hasHost) {
                FilledTonalIconButton(onClick = { onCommand { c, id -> c.open(id) } }) { Icon(Icons.Filled.KeyboardArrowUp, "Öffnen") }
                FilledTonalIconButton(onClick = { onCommand { c, id -> c.stop(id) } }) { Icon(Icons.Filled.Stop, "Stopp") }
                FilledTonalIconButton(onClick = { onCommand { c, id -> c.close(id) } }) { Icon(Icons.Filled.KeyboardArrowDown, "Schließen") }
            }
        }
    }
}

@Composable
fun ShutterEditScreen(vm: AppViewModel, id: String, onBack: () -> Unit) {
    val s = vm.shutter(id)
    if (s == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val scope = rememberCoroutineScope()
    var testResult by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableFloatStateOf(50f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = { vm.deleteShutter(id); onBack() }) { Icon(Icons.Filled.Delete, "Löschen") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(s.name, { vm.updateShutter(s.copy(name = it)) },
                label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(s.host, { vm.updateShutter(s.copy(host = it.trim())) },
                label = { Text("IP-Adresse oder Hostname") }, placeholder = { Text("192.168.178.41") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(s.password, { vm.updateShutter(s.copy(password = it)) },
                label = { Text("Passwort (falls am Shelly gesetzt)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cover-ID: ${s.coverId}", Modifier.weight(1f))
                OutlinedButton(onClick = { vm.updateShutter(s.copy(coverId = (s.coverId - 1).coerceAtLeast(0))) }) { Text("−") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { vm.updateShutter(s.copy(coverId = (s.coverId + 1).coerceAtMost(1))) }) { Text("+") }
            }
            Text(
                "Tipp: Im Router für jeden Shelly eine feste IP vergeben. Der Shelly muss im Cover-Modus " +
                    "betrieben und kalibriert sein. Cover-ID ist beim 2PM Gen3/Gen4 normalerweise 0.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(onClick = {
                testResult = "Verbinde …"
                scope.launch {
                    testResult = try {
                        val info = s.client().deviceInfo()
                        val st = s.client().status(s.coverId)
                        "✓ ${info.optString("model", "Shelly")} · Firmware ${info.optString("ver", "?")} · ${st.stateText}"
                    } catch (e: Exception) {
                        "✗ ${e.message}"
                    }
                }
            }, enabled = s.hasHost) { Text("Verbindung testen") }
            testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            Text("Position anfahren: ${target.toInt()} % offen", style = MaterialTheme.typography.titleSmall)
            Slider(value = target, onValueChange = { target = it }, valueRange = 0f..100f, steps = 19)
            OutlinedButton(onClick = { vm.command(s) { c, cid -> c.goTo(target.toInt(), cid) } }, enabled = s.hasHost) {
                Text("Auf ${target.toInt()} % fahren")
            }
        }
    }
}
