@file:OptIn(ExperimentalMaterial3Api::class)

package de.refadim.rollladen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private val pressTime = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun AppViewModel.bindingText(b: InputBinding): String =
    b.shutterId?.let { id -> shutter(id)?.let { "${it.name} ${b.dir.label}" } } ?: "–"

/** Übersicht der Taster-Module (Shelly Plus i4 DC) und der letzten Tastendrücke */
@Composable
fun InputsScreen(vm: AppViewModel, onEdit: (String) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Taster (Plus i4 DC)") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = { IconButton(onClick = { onEdit(vm.addModule().id) }) { Icon(Icons.Filled.Add, "Modul hinzufügen") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Jeder Eingang wird einem Rollladen und einer Richtung zugeordnet. Beim Übertragen " +
                        "(Wochenplan → „Wochenplan & Taster auf Shellys übertragen“) bekommt jedes Modul ein Skript: " +
                        "Ein Tastendruck schickt den Befehl per WLAN direkt an den 2PM – Auf/Ab fährt, ein weiterer " +
                        "Druck während der Fahrt stoppt – und sperrt die Automatik aller Rollläden.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(vm.inputModules, key = { it.id }) { m ->
                ElevatedCard(onClick = { onEdit(m.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(m.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (m.hasHost) m.host else "Keine IP-Adresse – tippen zum Einrichten",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            m.inputs.mapIndexed { i, b -> "E${i + 1}: ${vm.bindingText(b)}" }.joinToString("   "),
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            item {
                HorizontalDivider()
                SectionTitle("Letzte Tastendrücke")
                if (vm.presses.isEmpty()) {
                    Text(
                        "Noch keine. Die App empfängt Tastendrücke live von allen Modulen mit IP-Adresse (ohne Passwort).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(vm.presses) { p ->
                Text("${p.time.format(pressTime)}  ${p.module} · Eingang ${p.input} → ${p.target}")
            }
        }
    }
}

@Composable
fun InputEditScreen(vm: AppViewModel, id: String, onBack: () -> Unit) {
    val m = vm.module(id)
    if (m == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val scope = rememberCoroutineScope()
    var testResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(m.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = { IconButton(onClick = { vm.deleteModule(id); onBack() }) { Icon(Icons.Filled.Delete, "Löschen") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(m.name, { vm.updateModule(m.copy(name = it)) },
                label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(m.host, { vm.updateModule(m.copy(host = it.trim())) },
                label = { Text("IP-Adresse oder Hostname") }, placeholder = { Text("192.168.178.70") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(m.password, { vm.updateModule(m.copy(password = it)) },
                label = { Text("Passwort (falls gesetzt)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                testResult = "Verbinde …"
                scope.launch {
                    testResult = try {
                        val info = m.client().deviceInfo()
                        "✓ ${info.optString("model", "Shelly")} · Firmware ${info.optString("ver", "?")}"
                    } catch (e: Exception) {
                        "✗ ${e.message}"
                    }
                }
            }, enabled = m.hasHost) { Text("Verbindung testen") }
            testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            HorizontalDivider()
            SectionTitle("Eingänge")
            (0 until 4).forEach { i ->
                val b = m.inputs.getOrNull(i) ?: InputBinding()
                InputBindingRow(vm, i, b) { nb ->
                    val list = (0 until 4).map { k -> if (k == i) nb else m.inputs.getOrNull(k) ?: InputBinding() }
                    vm.updateModule(m.copy(inputs = list))
                }
            }
            Text(
                "Die Eingänge werden beim Übertragen auf den Typ „Taster“ gestellt. Die 2PM dürfen kein Passwort " +
                    "haben, sonst lehnen sie die Befehle der Taster ab. Änderungen wirken erst nach dem Übertragen.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InputBindingRow(vm: AppViewModel, index: Int, b: InputBinding, onChange: (InputBinding) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Eingang ${index + 1} (SW${index + 1})", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(b.shutterId?.let { vm.shutter(it)?.name } ?: "Nicht belegt", Modifier.weight(1f))
                    Icon(Icons.Filled.ArrowDropDown, null)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(text = { Text("Nicht belegt") }, onClick = { onChange(b.copy(shutterId = null)); open = false })
                    vm.shutters.forEach { s ->
                        DropdownMenuItem(text = { Text(s.name) }, onClick = { onChange(b.copy(shutterId = s.id)); open = false })
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            SingleChoiceSegmentedButtonRow {
                ButtonDir.entries.forEachIndexed { i, d ->
                    SegmentedButton(
                        selected = b.dir == d, onClick = { onChange(b.copy(dir = d)) },
                        shape = SegmentedButtonDefaults.itemShape(i, ButtonDir.entries.size),
                        enabled = b.shutterId != null,
                    ) { Text(d.label) }
                }
            }
        }
    }
}
