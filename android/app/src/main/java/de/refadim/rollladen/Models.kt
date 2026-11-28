package de.refadim.rollladen

import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.util.UUID

/** Ein Rollladen = ein Shelly 2PM Gen3 (oder Gen4) im Cover-Modus. */
@Serializable
data class Shutter(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** IP-Adresse oder Hostname, z. B. 192.168.178.41 */
    val host: String = "",
    /** Optionales Geräte-Passwort (Benutzer ist immer "admin") */
    val password: String = "",
    /** Cover-Instanz; beim 2PM im Cover-Modus immer 0 */
    val coverId: Int = 0,
) {
    val hasHost: Boolean get() = host.isNotBlank()
}

fun Shutter.client() = ShellyClient(host.trim(), password)

@Serializable
enum class TriggerKind(val label: String) {
    TIME("Uhrzeit"), SUNRISE("Sonnenaufgang"), SUNSET("Sonnenuntergang");

    val isSun: Boolean get() = this != TIME
}

@Serializable
enum class ShutterAction(val label: String) {
    OPEN("Öffnen"), CLOSE("Schließen"), POSITION("Position")
}

/** Wochentage: 1 = Montag … 7 = Sonntag (wie java.time.DayOfWeek.value) */
object Days {
    val SHORT = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    val SHELLY = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    val ALL = (1..7).toSet()
    val WORK = (1..5).toSet()
    val WEEKEND = setOf(6, 7)

    fun text(days: Set<Int>): String = when (days) {
        ALL -> "Täglich"
        WORK -> "Mo–Fr"
        WEEKEND -> "Sa, So"
        emptySet<Int>() -> "Kein Tag gewählt"
        else -> days.sorted().joinToString(", ") { SHORT[it - 1] }
    }
}

/** Ein Zeitpunkt: feste Uhrzeit oder Sonnenauf-/-untergang mit Versatz. */
@Serializable
data class TimePoint(
    val trigger: TriggerKind = TriggerKind.TIME,
    val hour: Int = 7,
    val minute: Int = 0,
    /** Versatz zu Sonnenauf-/-untergang in Minuten, negativ = vorher */
    val offsetMinutes: Int = 0,
) {
    val text: String
        get() = when (trigger) {
            TriggerKind.TIME -> "%02d:%02d Uhr".format(hour, minute)
            else -> if (offsetMinutes == 0) trigger.label
            else "${trigger.label} ${if (offsetMinutes > 0) "+" else "−"}${durationText(kotlin.math.abs(offsetMinutes))}"
        }
}

@Serializable
data class ScheduleRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Neue Regel",
    val enabled: Boolean = true,
    val action: ShutterAction = ShutterAction.CLOSE,
    /** Zielposition in % (0 = geschlossen, 100 = offen) */
    val position: Int = 50,
    val trigger: TriggerKind = TriggerKind.TIME,
    val hour: Int = 20,
    val minute: Int = 0,
    /** Versatz zu Sonnenauf-/-untergang in Minuten, negativ = vorher */
    val offsetMinutes: Int = 0,
    val weekdays: Set<Int> = Days.ALL,
    val shutterIds: Set<String> = emptySet(),
)

val ScheduleRule.timePoint: TimePoint get() = TimePoint(trigger, hour, minute, offsetMinutes)

fun durationText(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}

val ScheduleRule.weekdaysText: String get() = Days.text(weekdays)

val ScheduleRule.triggerText: String get() = timePoint.text

val ScheduleRule.actionText: String
    get() = if (action == ShutterAction.POSITION) "Auf $position % fahren" else action.label

// ---------- Taster-Module (Shelly Plus i4 DC) ----------

@Serializable
enum class ButtonDir(val label: String) { UP("Auf"), DOWN("Ab") }

/** Belegung eines Eingangs: welcher Rollladen, welche Richtung. shutterId = null → nicht belegt. */
@Serializable
data class InputBinding(
    val shutterId: String? = null,
    val dir: ButtonDir = ButtonDir.UP,
)

@Serializable
data class InputModule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String = "",
    val password: String = "",
    val inputs: List<InputBinding> = List(4) { InputBinding() },
) {
    val hasHost: Boolean get() = host.isNotBlank()
}

fun InputModule.client() = ShellyClient(host.trim(), password)

// ---------- Schalt-Aktoren (Shelly 1PM Mini Gen3) ----------

@Serializable
data class SwitchActor(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String = "",
    val password: String = "",
    /** Switch-Instanz; beim 1PM Mini immer 0 */
    val switchId: Int = 0,
) {
    val hasHost: Boolean get() = host.isNotBlank()
}

fun SwitchActor.client() = ShellyClient(host.trim(), password)

/** Schaltregel: einschalten zum Start, optional ausschalten zum Ende, an gewählten Wochentagen. */
@Serializable
data class SwitchRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Neue Schaltregel",
    val enabled: Boolean = true,
    val start: TimePoint = TimePoint(TriggerKind.SUNSET),
    val hasEnd: Boolean = true,
    val end: TimePoint = TimePoint(TriggerKind.TIME, 23, 0),
    val weekdays: Set<Int> = Days.ALL,
    val actorIds: Set<String> = emptySet(),
)

// ---------- Einstellungen ----------

@Serializable
data class AppSettings(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timeZone: String = ZoneId.systemDefault().id,
    /** Standort & Zeitzone beim Übertragen auf die Shellys schreiben */
    val pushLocation: Boolean = true,
) {
    val hasLocation: Boolean get() = latitude != 0.0 || longitude != 0.0
    val zone: ZoneId get() = runCatching { ZoneId.of(timeZone) }.getOrDefault(ZoneId.systemDefault())
}

/** Shelly mit Plus Add-on und DS18B20-Fühlern */
@Serializable
data class SensorSettings(
    val host: String = "",
    val password: String = "",
    /** Komponenten-ID des Fühlers, z. B. 100 für temperature:100 */
    val indoorId: Int = 100,
    val outdoorId: Int = 101,
) {
    val hasHost: Boolean get() = host.isNotBlank()
}

@Serializable
data class DisplaySettings(
    val screensaverEnabled: Boolean = true,
    /** Per Ordnerauswahl freigegebener Ordner (content://-URI) */
    val folderUri: String = "",
    /** Ausgewählte Bilder (content://-URIs) in Anzeigereihenfolge */
    val images: List<String> = emptyList(),
    val intervalSeconds: Int = 20,
    /** Nach so vielen Sekunden ohne Bedienung zurück zum Ruhebildschirm; 0 = nie */
    val idleTimeoutSeconds: Int = 120,
    val keepScreenOn: Boolean = true,
    /** Dunkle Darstellung aller Seiten (die Wetterkarte selbst bleibt hell) */
    val darkMode: Boolean = false,
    val mapZoom: Int = 8,
    /** Ebene des DWD-WMS (maps.dwd.de) */
    val radarLayer: String = "dwd:Niederschlagsradar",
    val radarSource: RadarSource = RadarSource.DWD,
    /** Bewölkung (Meteosat-Infrarot von EUMETSAT) einblenden */
    val showClouds: Boolean = false,
    val cloudLayer: String = "msg_fes:ir108",
    val baseMap: BaseMap = BaseMap.DARK,
)

@Serializable
data class Snapshot(
    val shutters: List<Shutter>,
    val rules: List<ScheduleRule>,
    val settings: AppSettings,
    val needsSync: Boolean,
    val inputModules: List<InputModule> = emptyList(),
    val actors: List<SwitchActor> = emptyList(),
    val switchRules: List<SwitchRule> = emptyList(),
    val sensors: SensorSettings = SensorSettings(),
    val display: DisplaySettings = DisplaySettings(),
    val needsSwitchSync: Boolean = false,
    /** 1 = alte Version (nur Rollläden), 2 = mit Taster, Schaltern, Display */
    val version: Int = 1,
)
