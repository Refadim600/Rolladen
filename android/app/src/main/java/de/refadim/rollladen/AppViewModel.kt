package de.refadim.rollladen

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalTime

/** Ein in der App angezeigter Tastendruck */
data class InputPress(val module: String, val input: Int, val target: String, val time: LocalTime)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val file = File(app.filesDir, "rollladen.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var shutters by mutableStateOf(listOf<Shutter>()); private set
    var rules by mutableStateOf(listOf<ScheduleRule>()); private set
    var settings by mutableStateOf(AppSettings()); private set
    /** true, solange Änderungen an Rollläden/Wochenplan/Tastern noch nicht übertragen wurden */
    var needsSync by mutableStateOf(false); private set

    var inputModules by mutableStateOf(listOf<InputModule>()); private set
    var actors by mutableStateOf(listOf<SwitchActor>()); private set
    var switchRules by mutableStateOf(listOf<SwitchRule>()); private set
    var sensors by mutableStateOf(SensorSettings()); private set
    var display by mutableStateOf(DisplaySettings()); private set
    var needsSwitchSync by mutableStateOf(false); private set

    val status = mutableStateMapOf<String, Result<CoverStatus>>()
    var groupBusy by mutableStateOf(false); private set
    var banner by mutableStateOf<String?>(null)
    var syncing by mutableStateOf(false); private set
    var syncResults by mutableStateOf<List<SyncResult>?>(null)

    val switchStatus = mutableStateMapOf<String, Result<SwitchStatus>>()
    var switchSyncing by mutableStateOf(false); private set
    var switchSyncResults by mutableStateOf<List<SyncResult>?>(null)

    var indoorTemp by mutableStateOf<Double?>(null); private set
    var outdoorTemp by mutableStateOf<Double?>(null); private set
    var tempError by mutableStateOf<String?>(null); private set

    /** Taster-Sperre: mindestens ein 2PM meldet rl_lock = true */
    var autoLocked by mutableStateOf(false); private set
    var unlocking by mutableStateOf(false); private set
    var presses by mutableStateOf(listOf<InputPress>()); private set

    /** Ruhebildschirm (Diashow/Wetter) wird angezeigt */
    var idle by mutableStateOf(false)
    private var lastTouch = SystemClock.elapsedRealtime()

    private val listener = InputEventListener(viewModelScope) { m, i -> onPress(m, i) }
    private var listenerRestart: Job? = null

    init {
        var version = 0
        try {
            val snap = json.decodeFromString(Snapshot.serializer(), file.readText())
            shutters = snap.shutters
            rules = snap.rules
            settings = snap.settings
            needsSync = snap.needsSync
            inputModules = snap.inputModules
            actors = snap.actors
            switchRules = snap.switchRules
            sensors = snap.sensors
            display = snap.display
            needsSwitchSync = snap.needsSwitchSync
            version = snap.version
        } catch (e: Exception) {
            // Erststart: 12 leere Rollläden anlegen
            shutters = (1..12).map { Shutter(name = "Rollladen $it") }
        }
        if (version < 2) {
            // Neu in Version 2: 6 Taster-Module (je 2 Rollläden) und 8 Schalter vorbelegen
            if (inputModules.isEmpty()) inputModules = defaultModules(shutters)
            if (actors.isEmpty()) actors = (1..8).map { SwitchActor(name = "Schalter $it") }
            if (inputModules.isNotEmpty()) needsSync = true
            save()
        }
        idle = display.screensaverEnabled
        listener.update(inputModules)

        viewModelScope.launch { while (true) { refreshTemps(); delay(60_000) } }
        viewModelScope.launch { while (true) { refreshLock(); delay(15_000) } }
        viewModelScope.launch { while (true) { delay(5_000); checkIdle() } }
    }

    override fun onCleared() {
        listener.stop()
    }

    private fun defaultModules(sh: List<Shutter>): List<InputModule> = (0 until 6).map { k ->
        val a = sh.getOrNull(2 * k)?.id
        val b = sh.getOrNull(2 * k + 1)?.id
        InputModule(
            name = "Taster-Modul ${k + 1}",
            inputs = listOf(
                InputBinding(a, ButtonDir.UP), InputBinding(a, ButtonDir.DOWN),
                InputBinding(b, ButtonDir.UP), InputBinding(b, ButtonDir.DOWN),
            ),
        )
    }

    private fun save() {
        runCatching {
            val snap = Snapshot(
                shutters, rules, settings, needsSync,
                inputModules, actors, switchRules, sensors, display, needsSwitchSync, version = 2,
            )
            file.writeText(json.encodeToString(Snapshot.serializer(), snap))
        }
    }

    private fun changed() {
        needsSync = true
        save()
    }

    private fun changedSwitch() {
        needsSwitchSync = true
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
        inputModules = inputModules.map { m ->
            m.copy(inputs = m.inputs.map { if (it.shutterId == id) it.copy(shutterId = null) else it })
        }
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
        needsSwitchSync = true
    }

    // ---- Taster-Module ----

    fun module(id: String) = inputModules.find { it.id == id }

    fun addModule(): InputModule {
        val m = InputModule(name = "Taster-Modul ${inputModules.size + 1}")
        inputModules = inputModules + m
        changed()
        return m
    }

    fun updateModule(m: InputModule) {
        inputModules = inputModules.map { if (it.id == m.id) m else it }
        changed()
        restartListenerSoon()
    }

    fun deleteModule(id: String) {
        inputModules = inputModules.filterNot { it.id == id }
        changed()
        restartListenerSoon()
    }

    private fun restartListenerSoon() {
        listenerRestart?.cancel()
        listenerRestart = viewModelScope.launch {
            delay(2_000)
            listener.update(inputModules)
        }
    }

    private fun onPress(moduleId: String, input: Int) {
        val m = module(moduleId) ?: return
        val b = m.inputs.getOrNull(input)
        val s = b?.shutterId?.let { shutter(it) }
        val target = if (b != null && s != null) "${s.name} ${b.dir.label}" else "nicht belegt"
        presses = (listOf(InputPress(m.name, input + 1, target, LocalTime.now())) + presses).take(20)
        if (s != null) {
            autoLocked = true
            viewModelScope.launch {
                delay(3_000)
                refreshLock()
                refresh(s)
            }
        }
    }

    // ---- Automatik-Sperre ----

    private suspend fun refreshLock() {
        val targets = shutters.filter { it.hasHost }
        if (targets.isEmpty()) {
            autoLocked = false
            return
        }
        val res = coroutineScope {
            targets.map { s -> async { runCatching { s.client().kvsGetBool(DeviceScripts.LOCK_KEY) }.getOrNull() } }.awaitAll()
        }
        if (res.any { it != null }) autoLocked = res.any { it == true }
    }

    fun refreshLockNow() {
        viewModelScope.launch { refreshLock() }
    }

    /** Hebt die Taster-Sperre auf allen Rollläden auf (Button am Display) */
    fun unlockAll() {
        viewModelScope.launch {
            unlocking = true
            val targets = shutters.filter { it.hasHost }
            val failures = targets.map { s ->
                async {
                    runCatching { s.client().kvsSet(DeviceScripts.LOCK_KEY, false) }
                        .exceptionOrNull()?.let { "${s.name}: ${it.message}" }
                }
            }.awaitAll().filterNotNull()
            unlocking = false
            banner = if (failures.isEmpty()) "Automatik wieder freigegeben."
            else "Freigabe unvollständig:\n" + failures.joinToString("\n")
            refreshLock()
        }
    }

    // ---- Steuerung Rollläden ----

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
            val res = ScheduleSync.run(shutters, rules, settings, inputModules)
            syncing = false
            if (res.all { it.error == null }) {
                needsSync = false
                save()
            }
            syncResults = res
            refreshLock()
        }
    }

    // ---- Schalter (1PM Mini) ----

    fun actor(id: String) = actors.find { it.id == id }

    fun addActor(): SwitchActor {
        val a = SwitchActor(name = "Schalter ${actors.size + 1}")
        actors = actors + a
        changedSwitch()
        return a
    }

    fun updateActor(a: SwitchActor) {
        actors = actors.map { if (it.id == a.id) a else it }
        changedSwitch()
    }

    fun deleteActor(id: String) {
        actors = actors.filterNot { it.id == id }
        switchRules = switchRules.map { it.copy(actorIds = it.actorIds - id) }
        switchStatus.remove(id)
        changedSwitch()
    }

    fun switchRule(id: String) = switchRules.find { it.id == id }

    fun addSwitchRule(): SwitchRule {
        val r = SwitchRule()
        switchRules = switchRules + r
        changedSwitch()
        return r
    }

    fun updateSwitchRule(r: SwitchRule) {
        switchRules = switchRules.map { if (it.id == r.id) r else it }
        changedSwitch()
    }

    fun deleteSwitchRule(id: String) {
        switchRules = switchRules.filterNot { it.id == id }
        changedSwitch()
    }

    fun refreshSwitches() {
        viewModelScope.launch {
            actors.filter { it.hasHost }.forEach { a ->
                launch { switchStatus[a.id] = runCatching { a.client().switchStatus(a.switchId) } }
            }
        }
    }

    fun setSwitch(a: SwitchActor, on: Boolean) {
        viewModelScope.launch {
            runCatching { a.client().switchSet(on, a.switchId) }.onFailure { banner = "${a.name}: ${it.message}" }
            delay(500)
            switchStatus[a.id] = runCatching { a.client().switchStatus(a.switchId) }
        }
    }

    fun syncSwitches() {
        viewModelScope.launch {
            switchSyncing = true
            val res = SwitchSync.run(actors, switchRules, settings)
            switchSyncing = false
            if (res.all { it.error == null }) {
                needsSwitchSync = false
                save()
            }
            switchSyncResults = res
        }
    }

    // ---- Temperaturen ----

    fun updateSensors(s: SensorSettings) {
        sensors = s
        save()
    }

    fun refreshTempsNow() {
        viewModelScope.launch { refreshTemps() }
    }

    private suspend fun refreshTemps() {
        val s = sensors
        if (!s.hasHost) {
            indoorTemp = null
            outdoorTemp = null
            tempError = null
            return
        }
        val c = ShellyClient(s.host.trim(), s.password)
        val inside = runCatching { c.temperature(s.indoorId) }
        val outside = runCatching { c.temperature(s.outdoorId) }
        indoorTemp = inside.getOrNull()
        outdoorTemp = outside.getOrNull()
        tempError = (inside.exceptionOrNull() ?: outside.exceptionOrNull())?.message
    }

    /** Liest alle temperature:*-Komponenten des Add-on-Shellys (ID → °C) */
    suspend fun discoverSensors(): List<Pair<Int, Double?>> {
        val st = ShellyClient(sensors.host.trim(), sensors.password).fullStatus()
        return st.keys().asSequence()
            .filter { it.startsWith("temperature:") }
            .mapNotNull { key ->
                key.substringAfter(':').toIntOrNull()?.let { id ->
                    val o = st.optJSONObject(key)
                    id to (if (o != null && o.has("tC") && !o.isNull("tC")) o.getDouble("tC") else null)
                }
            }
            .sortedBy { it.first }
            .toList()
    }

    // ---- Display / Ruhebildschirm ----

    fun updateDisplay(d: DisplaySettings) {
        display = d
        if (!d.screensaverEnabled) idle = false
        save()
    }

    fun touch() {
        lastTouch = SystemClock.elapsedRealtime()
    }

    fun wake() {
        touch()
        idle = false
    }

    fun sleep() {
        if (display.screensaverEnabled) idle = true
    }

    private fun checkIdle() {
        val d = display
        if (!idle && d.screensaverEnabled && d.idleTimeoutSeconds > 0 &&
            SystemClock.elapsedRealtime() - lastTouch > d.idleTimeoutSeconds * 1000L
        ) idle = true
    }
}
