package de.refadim.rollladen

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val file = File(app.filesDir, "rollladen.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var shutters by mutableStateOf(listOf<Shutter>()); private set
    var rules by mutableStateOf(listOf<ScheduleRule>()); private set
    var settings by mutableStateOf(AppSettings()); private set
    /** true, solange Änderungen noch nicht auf die Shellys übertragen wurden */
    var needsSync by mutableStateOf(false); private set

    val status = mutableStateMapOf<String, Result<CoverStatus>>()
    var groupBusy by mutableStateOf(false); private set
    var banner by mutableStateOf<String?>(null)
    var syncing by mutableStateOf(false); private set
    var syncResults by mutableStateOf<List<SyncResult>?>(null)

    init {
        try {
            val snap = json.decodeFromString(Snapshot.serializer(), file.readText())
            shutters = snap.shutters
            rules = snap.rules
            settings = snap.settings
            needsSync = snap.needsSync
        } catch (e: Exception) {
            // Erststart: 12 leere Rollläden anlegen
            shutters = (1..12).map { Shutter(name = "Rollladen $it") }
        }
    }

    private fun save() {
        runCatching {
            file.writeText(json.encodeToString(Snapshot.serializer(), Snapshot(shutters, rules, settings, needsSync)))
        }
    }

    private fun changed() {
        needsSync = true
        save()
    }

    // ---- Rollläden ----

    fun shutter(id: String) = shutters.find { it.id == id }

    fun updateShutter(s: Shutter) {
        shutters = shutters.map { if (it.id == s.id) s else it }
        changed()
    }

    fun addShutter() {
        shutters = shutters + Shutter(name = "Rollladen ${shutters.size + 1}")
        changed()
    }

    fun deleteShutter(id: String) {
        shutters = shutters.filterNot { it.id == id }
        rules = rules.map { it.copy(shutterIds = it.shutterIds - id) }
        status.remove(id)
        changed()
    }

    // ---- Regeln ----

    fun rule(id: String) = rules.find { it.id == id }

    fun addRule(): ScheduleRule {
        val r = ScheduleRule(shutterIds = shutters.map { it.id }.toSet())
        rules = rules + r
        changed()
        return r
    }

    fun updateRule(r: ScheduleRule) {
        rules = rules.map { if (it.id == r.id) r else it }
        changed()
    }

    fun deleteRule(id: String) {
        rules = rules.filterNot { it.id == id }
        changed()
    }

    fun updateSettings(s: AppSettings) {
        settings = s
        changed()
    }

    // ---- Steuerung ----

    fun refreshAll() {
        viewModelScope.launch {
            shutters.filter { it.hasHost }.forEach { s -> launch { refresh(s) } }
        }
    }

    private suspend fun refresh(s: Shutter) {
        status[s.id] = runCatching { s.client().status(s.coverId) }
    }

    fun command(s: Shutter, op: suspend (ShellyClient, Int) -> Unit): Job = viewModelScope.launch {
        runCatching { op(s.client(), s.coverId) }.onFailure { banner = "${s.name}: ${it.message}" }
        delay(800)
        refresh(s)
    }

    /** Befehl gleichzeitig an alle Rollläden mit eingetragener Adresse */
    fun runOnAll(label: String, op: suspend (ShellyClient, Int) -> Unit): Job = viewModelScope.launch {
        val targets = shutters.filter { it.hasHost }
        if (targets.isEmpty()) {
            banner = "Noch keine IP-Adressen eingetragen."
            return@launch
        }
        groupBusy = true
        val failures = targets.map { s ->
            async { runCatching { op(s.client(), s.coverId) }.exceptionOrNull()?.let { "${s.name}: ${it.message}" } }
        }.awaitAll().filterNotNull()
        groupBusy = false
        banner = if (failures.isEmpty()) "$label: ${targets.size} Rollläden angesteuert."
        else "$label: ${failures.size} von ${targets.size} fehlgeschlagen\n" + failures.joinToString("\n")
        delay(1000)
        targets.forEach { launch { refresh(it) } }
    }

    fun sync() {
        viewModelScope.launch {
            syncing = true
            val res = ScheduleSync.run(shutters, rules, settings)
            syncing = false
            if (res.all { it.error == null }) {
                needsSync = false
                save()
            }
            syncResults = res
        }
    }
}
