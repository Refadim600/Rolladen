@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.refadim.rollladen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

private val nextFormat = DateTimeFormatter.ofPattern("EE dd.MM., HH:mm 'Uhr'", Locale.GERMAN)
private val hhmm = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

private val SwitchRule.summary: String
    get() = "Ein: ${start.text}" + (if (hasEnd) " · Aus: ${end.text}" else "")

/** Tab „Schalter“: Shelly 1PM Mini Gen3 schalten und Schaltregeln verwalten */
@Composable
fun SwitchesScreen(vm: AppViewModel, onEditActor: (String) -> Unit, onEditRule: (String) -> Unit) {
    LaunchedEffect(Unit) { vm.refreshSwitches() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schalter") },
                actions = {
                    IconButton(onClick = { vm.refreshSwitches() }) { Icon(Icons.Filled.Refresh, "Aktualisieren") }
                    IconButton(onClick = { onEditActor(vm.addActor().id) }) { Icon(Icons.Filled.Add, "Schalter hinzufügen") }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Button(
                    onClick = { vm.syncSwitches() }, enabled = !vm.switchSyncing,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    if (vm.switchSyncing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.CloudUpload, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Schaltzeiten auf Shellys übertragen", fontWeight = FontWeight.Bold)
                }
                if (vm.needsSwitchSync) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = Color(0xFFEF6C00), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Änderungen sind noch nicht übertragen", color = Color(0xFFEF6C00),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                vm.banner?.let { text ->
                    Card(
                        onClick = { vm.banner = null },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
                }
            }
            item { SectionTitle("Aktoren") }
            items(vm.actors, key = { it.id }) { a -> ActorCard(vm, a) { onEditActor(a.id) } }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Schaltregeln")
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onEditRule(vm.addSwitchRule().id) }) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Regel")
                    }
                }
                if (vm.switchRules.isEmpty()) {
                    Text(
                        "Noch keine Schaltregeln, z. B. „Außenlicht: Sonnenuntergang an, 23:00 aus“.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(vm.switchRules, key = { it.id }) { r ->
                ElevatedCard(onClick = { onEditRule(r.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Schedule, null, tint = if (r.enabled) Color(0xFFF57C00) else Color.Gray,
                            modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.titleMedium)
                            Text(r.summary, style = MaterialTheme.typography.bodyMedium)
                            Text("${Days.text(r.weekdays)} · ${r.actorIds.size} Schalter",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (r.enabled) {
                                SunCalculator.nextRun(r.start, r.weekdays, vm.settings)?.let {
                                    Text("Nächstes Einschalten: ${it.format(nextFormat)}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Switch(checked = r.enabled, onCheckedChange = { vm.updateSwitchRule(r.copy(enabled = it)) })
                    }
                }
            }
        }
    }

    vm.switchSyncResults?.let { results -> SyncResultsDialog(results) { vm.switchSyncResults = null } }
}

@Composable
private fun ActorCard(vm: AppViewModel, a: SwitchActor, onClick: () -> Unit) {
    val st = vm.switchStatus[a.id]
    val subtitle = when {
        !a.hasHost -> "Keine IP-Adresse – tippen zum Einrichten"
        st == null -> "…"
        st.isFailure -> st.exceptionOrNull()?.message ?: "Fehler"
        else -> st.getOrNull()!!.let { s -> (if (s.on) "an" else "aus") + (s.power?.let { " · %.0f W".format(it) } ?: "") }
    }
    val on = st?.getOrNull()?.on == true
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lightbulb, null, tint = if (on) Color(0xFFFBC02D) else Color.Gray, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = if (st?.isFailure == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (a.hasHost) Switch(checked = on, onCheckedChange = { vm.setSwitch(a, it) })
        }
    }
}

@Composable
fun ActorEditScreen(vm: AppViewModel, id: String, onBack: () -> Unit) {
    val a = vm.actor(id)
    if (a == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val scope = rememberCoroutineScope()
    var testResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(a.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = { IconButton(onClick = { vm.deleteActor(id); onBack() }) { Icon(Icons.Filled.Delete, "Löschen") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(a.name, { vm.updateActor(a.copy(name = it)) },
                label = { Text("Name, z. B. Außenlicht Eingang") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(a.host, { vm.updateActor(a.copy(host = it.trim())) },
                label = { Text("IP-Adresse oder Hostname") }, placeholder = { Text("192.168.178.80") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(a.password, { vm.updateActor(a.copy(password = it)) },
                label = { Text("Passwort (falls gesetzt)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                testResult = "Verbinde …"
                scope.launch {
                    testResult = try {
                        val info = a.client().deviceInfo()
                        val st = a.client().switchStatus(a.switchId)
                        "✓ ${info.optString("model", "Shelly")} · Firmware ${info.optString("ver", "?")} · ${if (st.on) "an" else "aus"}"
                    } catch (e: Exception) {
                        "✗ ${e.message}"
                    }
                }
            }, enabled = a.hasHost) { Text("Verbindung testen") }
            testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { vm.setSwitch(a, true) }, enabled = a.hasHost) { Text("Einschalten") }
                OutlinedButton(onClick = { vm.setSwitch(a, false) }, enabled = a.hasHost) { Text("Ausschalten") }
            }
            Text(
                "Tipp: Im Router eine feste IP vergeben. Die Schaltzeiten werden – wie bei den Rollläden – auf dem " +
                    "Shelly gespeichert und laufen dort auch bei ausgeschaltetem Display.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SwitchRuleEditScreen(vm: AppViewModel, id: String, onBack: () -> Unit) {
    val r = vm.switchRule(id)
    if (r == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val update: (SwitchRule) -> Unit = { vm.updateSwitchRule(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(r.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = { IconButton(onClick = { vm.deleteSwitchRule(id); onBack() }) { Icon(Icons.Filled.Delete, "Löschen") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(r.name, { update(r.copy(name = it)) }, label = { Text("Name") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aktiv", Modifier.weight(1f))
                Switch(checked = r.enabled, onCheckedChange = { update(r.copy(enabled = it)) })
            }

            SectionTitle("Einschalten (Start)")
            TimePointEditor(r.start, vm.settings) { update(r.copy(start = it)) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Ausschalten (Ende)")
                Spacer(Modifier.weight(1f))
                Switch(checked = r.hasEnd, onCheckedChange = { update(r.copy(hasEnd = it)) })
            }
            if (r.hasEnd) {
                TimePointEditor(r.end, vm.settings) { update(r.copy(end = it)) }
                if (ScheduleBuilder.endDays(r) != r.weekdays) {
                    Text("Das Ende liegt vor dem Start – es wird jeweils am Folgetag ausgeschaltet.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text("Ohne Ende wird nur eingeschaltet.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionTitle("Wochentage")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..7).forEach { d ->
                    val on = d in r.weekdays
                    FilterChip(
                        selected = on,
                        onClick = { update(r.copy(weekdays = if (on) r.weekdays - d else r.weekdays + d)) },
                        label = { Text(Days.SHORT[d - 1]) },
                    )
                }
            }
            Row {
                TextButton(onClick = { update(r.copy(weekdays = Days.WORK)) }) { Text("Mo–Fr") }
                TextButton(onClick = { update(r.copy(weekdays = Days.WEEKEND)) }) { Text("Wochenende") }
                TextButton(onClick = { update(r.copy(weekdays = Days.ALL)) }) { Text("Täglich") }
            }

            SectionTitle("Schalter (${r.actorIds.size} ausgewählt)")
            Column {
                vm.actors.forEach { a ->
                    val on = a.id in r.actorIds
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { update(r.copy(actorIds = if (on) r.actorIds - a.id else r.actorIds + a.id)) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(a.name)
                    }
                }
            }

            HorizontalDivider()
            Text("Shelly-Zeitausdrücke", style = MaterialTheme.typography.labelMedium)
            Text(
                "Ein:  " + ScheduleBuilder.timespec(r.start, r.weekdays) +
                    (if (r.hasEnd) "\nAus:  " + ScheduleBuilder.timespec(r.end, ScheduleBuilder.endDays(r)) else ""),
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.height(24.dp))
        }
    }
}

/** Auswahl Uhrzeit / Sonnenaufgang / Sonnenuntergang mit Versatz */
@Composable
fun TimePointEditor(t: TimePoint, settings: AppSettings, onChange: (TimePoint) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TriggerKind.entries.forEachIndexed { i, k ->
                SegmentedButton(
                    selected = t.trigger == k, onClick = { onChange(t.copy(trigger = k)) },
                    shape = SegmentedButtonDefaults.itemShape(i, TriggerKind.entries.size),
                ) { Text(k.label, maxLines = 1) }
            }
        }
        if (t.trigger == TriggerKind.TIME) {
            OutlinedButton(onClick = { showPicker = true }) {
                Icon(Icons.Filled.Schedule, null)
                Spacer(Modifier.width(8.dp))
                Text("%02d:%02d Uhr".format(t.hour, t.minute), style = MaterialTheme.typography.titleMedium)
            }
        } else {
            val event = t.trigger.label
            val offsetText = when {
                t.offsetMinutes == 0 -> "Genau bei $event"
                t.offsetMinutes > 0 -> "${durationText(t.offsetMinutes)} nach $event"
                else -> "${durationText(-t.offsetMinutes)} vor $event"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onChange(t.copy(offsetMinutes = (t.offsetMinutes - 5).coerceAtLeast(-180))) }) { Text("−5 min") }
                Text(offsetText, Modifier.weight(1f).padding(horizontal = 8.dp), textAlign = TextAlign.Center)
                OutlinedButton(onClick = { onChange(t.copy(offsetMinutes = (t.offsetMinutes + 5).coerceAtMost(180))) }) { Text("+5 min") }
            }
            val today = SunCalculator.todayAt(t, settings)
            if (today == null) {
                Text("Bitte unter Einstellungen den Standort festlegen.", color = Color(0xFFEF6C00),
                    style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Heute wäre das um ${today.format(hhmm)} Uhr",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showPicker) {
        val state = rememberTimePickerState(initialHour = t.hour, initialMinute = t.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(t.copy(hour = state.hour, minute = state.minute))
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Abbrechen") } },
            text = { TimePicker(state = state) },
        )
    }
}
