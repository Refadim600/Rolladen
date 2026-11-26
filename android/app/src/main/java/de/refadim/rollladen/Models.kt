package de.refadim.rollladen

import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.util.UUID

/** Ein Rollladen = ein Shelly 2PM Gen4 im Cover-Modus. */
@Serializable
data class Shutter(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** IP-Adresse oder Hostname, z. B. 192.168.178.41 */
    val host: String = "",
    /** Optionales Geräte-Passwort (Benutzer ist immer "admin") */
    val password: String = "",
    /** Cover-Instanz; beim 2PM Gen4 im Cover-Modus immer 0 */
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

fun durationText(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}

val ScheduleRule.weekdaysText: String
    get() = when (weekdays) {
        Days.ALL -> "Täglich"
        Days.WORK -> "Mo–Fr"
        Days.WEEKEND -> "Sa, So"
        emptySet<Int>() -> "Kein Tag gewählt"
        else -> weekdays.sorted().joinToString(", ") { Days.SHORT[it - 1] }
    }

val ScheduleRule.triggerText: String
    get() = when (trigger) {
        TriggerKind.TIME -> "%02d:%02d Uhr".format(hour, minute)
        else -> if (offsetMinutes == 0) trigger.label
        else "${trigger.label} ${if (offsetMinutes > 0) "+" else "−"}${durationText(kotlin.math.abs(offsetMinutes))}"
    }

val ScheduleRule.actionText: String
    get() = if (action == ShutterAction.POSITION) "Auf $position % fahren" else action.label

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

@Serializable
data class Snapshot(
    val shutters: List<Shutter>,
    val rules: List<ScheduleRule>,
    val settings: AppSettings,
    val needsSync: Boolean,
)
