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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale

private val nextRunFormat = DateTimeFormatter.ofPattern("EE dd.MM., HH:mm 'Uhr'", Locale.GERMAN)
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

@Composable
fun SchedulesScreen(vm: AppViewModel, onEdit: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wochenplan") },
                actions = { IconButton(onClick = { onEdit(vm.addRule().id) }) { Icon(Icons.Filled.Add, "Regel hinzufügen") } },
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
                    onClick = { vm.sync() }, enabled = !vm.syncing,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    if (vm.syncing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.CloudUpload, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Wochenplan auf Shellys übertragen", fontWeight = FontWeight.Bold)
                }
                if (vm.needsSync) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = Color(0xFFEF6C00), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Änderungen sind noch nicht übertragen", color = Color(0xFFEF6C00),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    "Die Zeitpläne werden auf den Shellys gespeichert und laufen dort – auch wenn das Tablet aus " +
                        "oder außer Haus ist. Vorhandene Zeitpläne auf den Geräten werden dabei ersetzt.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (vm.rules.isEmpty()) {
                item {
                    Text(
                        "Noch keine Regeln.\nTippe oben auf +, um z. B. „Abends schließen“ anzulegen.",
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    )
                }
            }
            items(vm.rules, key = { it.id }) { r ->
                RuleCard(r, vm.settings, onClick = { onEdit(r.id) }, onToggle = { vm.updateRule(r.copy(enabled = it)) })
            }
        }
    }

    vm.syncResults?.let { results -> SyncResultsDialog(results) { vm.syncResults = null } }
}

@Composable
private fun RuleCard(r: ScheduleRule, settings: AppSettings, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val icon = when (r.trigger) {
        TriggerKind.SUNRISE -> Icons.Filled.WbSunny
        TriggerKind.SUNSET -> Icons.Filled.WbTwilight
        TriggerKind.TIME -> Icons.Filled.Schedule
    }
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (r.enabled) Color(0xFFF57C00) else Color.Gray, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleMedium)
                Text("${r.actionText} · ${r.triggerText}", style = MaterialTheme.typography.bodyMedium)
                Text("${r.weekdaysText} · ${r.shutterIds.size} Rollläden",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SunCalculator.nextRun(r, settings)?.let {
                    Text("Nächste Ausführung: ${it.format(nextRunFormat)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Switch(checked = r.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun SyncResultsDialog(results: List<SyncResult>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } },
        title = { Text("Übertragung") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val shown = results.filterNot { it.skipped }
                if (shown.isEmpty()) Text("Keine Geräte mit IP-Adresse vorhanden.")
                shown.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (r.error == null) Icons.Filled.CheckCircle else Icons.Filled.Error, null,
                            tint = if (r.error == null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(r.name)
                            Text(r.error ?: "${r.jobs} Zeitplan-Einträge gespeichert",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
    )
}

@Composable
fun RuleEditScreen(vm: AppViewModel, id: String, onBack: () -> Unit) {
    val r = vm.rule(id)
    if (r == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val update: (ScheduleRule) -> Unit = { vm.updateRule(it) }
    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(r.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = { IconButton(onClick = { vm.deleteRule(id); onBack() }) { Icon(Icons.Filled.Delete, "Löschen") } },
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

            SectionTitle("Aktion")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ShutterAction.entries.forEachIndexed { i, a ->
                    SegmentedButton(
                        selected = r.action == a, onClick = { update(r.copy(action = a)) },
                        shape = SegmentedButtonDefaults.itemShape(i, ShutterAction.entries.size),
                    ) { Text(a.label) }
                }
            }
            if (r.action == ShutterAction.POSITION) {
                Text("Zielposition: ${r.position} % offen")
                Slider(
                    value = r.position.toFloat(), onValueChange = { update(r.copy(position = it.toInt())) },
                    valueRange = 0f..100f, steps = 19,
                )
            }

            SectionTitle("Zeitpunkt")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TriggerKind.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = r.trigger == t, onClick = { update(r.copy(trigger = t)) },
                        shape = SegmentedButtonDefaults.itemShape(i, TriggerKind.entries.size),
                    ) { Text(t.label, maxLines = 1) }
                }
            }
            if (r.trigger == TriggerKind.TIME) {
                OutlinedButton(onClick = { showTimePicker = true }) {
                    Icon(Icons.Filled.Schedule, null)
                    Spacer(Modifier.width(8.dp))
                    Text("%02d:%02d Uhr".format(r.hour, r.minute), style = MaterialTheme.typography.titleMedium)
                }
            } else {
                val event = r.trigger.label
                val offsetText = when {
                    r.offsetMinutes == 0 -> "Genau bei $event"
                    r.offsetMinutes > 0 -> "${durationText(r.offsetMinutes)} nach $event"
                    else -> "${durationText(-r.offsetMinutes)} vor $event"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { update(r.copy(offsetMinutes = (r.offsetMinutes - 5).coerceAtLeast(-180))) }) { Text("−5 min") }
                    Text(offsetText, Modifier.weight(1f).padding(horizontal = 8.dp), textAlign = TextAlign.Center)
                    OutlinedButton(onClick = { update(r.copy(offsetMinutes = (r.offsetMinutes + 5).coerceAtMost(180))) }) { Text("+5 min") }
                }
                val today = SunCalculator.today(vm.settings)
                if (today == null) {
                    Text("Bitte unter Einstellungen den Standort festlegen.", color = Color(0xFFEF6C00),
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    val base = if (r.trigger == TriggerKind.SUNRISE) today.first else today.second
                    Text("Heute wäre das um ${base.plusMinutes(r.offsetMinutes.toLong()).format(timeFormat)} Uhr",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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

            SectionTitle("Rollläden (${r.shutterIds.size} ausgewählt)")
            Row {
                TextButton(onClick = { update(r.copy(shutterIds = vm.shutters.map { it.id }.toSet())) }) { Text("Alle auswählen") }
                TextButton(onClick = { update(r.copy(shutterIds = emptySet())) }) { Text("Keine") }
            }
            Column {
                vm.shutters.forEach { s ->
                    val on = s.id in r.shutterIds
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { update(r.copy(shutterIds = if (on) r.shutterIds - s.id else r.shutterIds + s.id)) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(s.name)
                    }
                }
            }

            HorizontalDivider()
            Text("Shelly-Zeitausdruck", style = MaterialTheme.typography.labelMedium)
            Text(ScheduleBuilder.timespec(r), fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.height(24.dp))
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = r.hour, initialMinute = r.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    update(r.copy(hour = state.hour, minute = state.minute))
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Abbrechen") } },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}
