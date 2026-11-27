package de.refadim.rollladen

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Übersetzt Regeln in Shelly-Zeitpläne. Die Zeitpläne werden AUF DEN GERÄTEN gespeichert und
 * dort ausgeführt – die Rollläden fahren also auch, wenn das Tablet aus oder außer Haus ist.
 */
object ScheduleBuilder {
    const val MAX_JOBS = 20

    /** z. B. "0 30 7 * * MON,TUE,WED,THU,FRI" oder "@sunset+15m * * SAT,SUN" */
    fun timespec(r: ScheduleRule): String = timespec(r.timePoint, r.weekdays)

    fun timespec(t: TimePoint, weekdays: Set<Int>): String {
        val days = weekdays.sorted().joinToString(",") { Days.SHELLY[it - 1] }
        return when (t.trigger) {
            TriggerKind.TIME -> "0 ${t.minute} ${t.hour} * * $days"
            TriggerKind.SUNRISE -> "@sunrise${offset(t.offsetMinutes)} * * $days"
            TriggerKind.SUNSET -> "@sunset${offset(t.offsetMinutes)} * * $days"
        }
    }

    /** "+1h30m", "-15m" oder "" (Shelly erlaubt max. ±12 h) */
    fun offset(minutes: Int): String {
        if (minutes == 0) return ""
        val h = abs(minutes) / 60
        val m = abs(minutes) % 60
        return (if (minutes > 0) "+" else "-") + (if (h > 0) "${h}h" else "") + (if (m > 0) "${m}m" else "")
    }

    /** Der Zeitplan ruft das Skript rl_auto auf, das die Taster-Sperre beachtet. */
    fun call(r: ScheduleRule, scriptId: Int): JSONObject = DeviceScripts.autoCall(scriptId, r.action, r.position)

    fun rulesFor(s: Shutter, rules: List<ScheduleRule>) =
        rules.filter { it.enabled && it.weekdays.isNotEmpty() && s.id in it.shutterIds }

    /** Ende einer Schaltregel: liegt es (bei festen Uhrzeiten) vor dem Start, ist es am Folgetag. */
    fun endDays(r: SwitchRule): Set<Int> {
        val overnight = r.start.trigger == TriggerKind.TIME && r.end.trigger == TriggerKind.TIME &&
            r.end.hour * 60 + r.end.minute <= r.start.hour * 60 + r.start.minute
        return if (overnight) r.weekdays.map { it % 7 + 1 }.toSet() else r.weekdays
    }
}

data class SyncResult(
    val name: String,
    val jobs: Int,
    val error: String?,
    val skipped: Boolean = false,
    /** Ersetzt den Standardtext bei Erfolg bzw. ergänzt einen Hinweis */
    val note: String? = null,
)

private suspend fun ShellyClient.pushLocation(settings: AppSettings) {
    if (!settings.pushLocation) return
    setLocation(
        settings.timeZone,
        settings.latitude.takeIf { settings.hasLocation },
        settings.longitude.takeIf { settings.hasLocation },
    )
}

object ScheduleSync {
    /**
     * 1. Auf jeden 2PM: Skript rl_auto + Zeitpläne (parallel).
     * 2. Auf jeden i4 DC: Eingänge auf „Taster“ und Skript rl_taster mit den Adressen der 2PM.
     */
    suspend fun run(
        shutters: List<Shutter>,
        rules: List<ScheduleRule>,
        settings: AppSettings,
        modules: List<InputModule>,
    ): List<SyncResult> = coroutineScope {
        val covers = shutters.map { s -> async { syncShutter(s, ScheduleBuilder.rulesFor(s, rules), settings) } }.awaitAll()
        val endpoints = shutters.zip(covers).mapNotNull { (s, r) ->
            r.second?.let { sid -> s.id to "http://${s.host.trim()}/script/$sid/api" }
        }.toMap()
        val inputs = modules.map { m -> async { syncModule(m, shutters, endpoints) } }.awaitAll()
        covers.map { it.first } + inputs
    }

    private suspend fun syncShutter(s: Shutter, jobs: List<ScheduleRule>, settings: AppSettings): Pair<SyncResult, Int?> {
        if (!s.hasHost) {
            return SyncResult(s.name, 0, if (jobs.isEmpty()) null else "Keine IP-Adresse eingetragen", jobs.isEmpty()) to null
        }
        if (jobs.size > ScheduleBuilder.MAX_JOBS) {
            return SyncResult(s.name, jobs.size, "Zu viele Regeln (max. ${ScheduleBuilder.MAX_JOBS} pro Gerät)") to null
        }
        val client = s.client()
        return try {
            client.pushLocation(settings)
            val sid = client.installScript(DeviceScripts.COVER_SCRIPT, DeviceScripts.cover(s.coverId))
            client.deleteAllSchedules()
            for (r in jobs) {
                client.createSchedule(ScheduleBuilder.timespec(r), JSONArray().put(ScheduleBuilder.call(r, sid)))
            }
            val note = if (s.password.isNotEmpty())
                "${jobs.size} Zeitpläne gespeichert. Achtung: Mit Passwort können die Taster diesen Shelly nicht ansteuern."
            else null
            SyncResult(s.name, jobs.size, null, note = note) to sid
        } catch (e: Exception) {
            SyncResult(s.name, jobs.size, e.message ?: "Unbekannter Fehler") to null
        }
    }

    private suspend fun syncModule(m: InputModule, shutters: List<Shutter>, endpoints: Map<String, String>): SyncResult {
        val name = "${m.name} (Taster)"
        val used = m.inputs.count { it.shutterId != null }
        if (!m.hasHost) {
            return SyncResult(name, 0, if (used == 0) null else "Keine IP-Adresse eingetragen", used == 0)
        }
        val missing = m.inputs.mapNotNull { b -> b.shutterId?.takeIf { it !in endpoints } }
            .mapNotNull { id -> shutters.find { it.id == id }?.name }.distinct()
        val buttonUrls = (0 until 4).map { i ->
            val b = m.inputs.getOrNull(i)
            val base = b?.shutterId?.let { endpoints[it] }
            if (b == null || base == null) "" else base + "?" + (if (b.dir == ButtonDir.UP) "up" else "down")
        }
        val lockUrls = endpoints.values.map { "$it?lock" }
        return try {
            val client = m.client()
            buttonUrls.forEachIndexed { i, u -> if (u.isNotEmpty()) client.setInputButton(i) }
            client.installScript(DeviceScripts.INPUT_SCRIPT, DeviceScripts.input(buttonUrls, lockUrls))
            val active = buttonUrls.count { it.isNotEmpty() }
            val note = if (missing.isEmpty()) "Taster-Skript installiert ($active Eingänge belegt)"
            else "Taster-Skript installiert, aber ohne: ${missing.joinToString()} (Rollladen nicht erreichbar)"
            SyncResult(name, active, null, note = note)
        } catch (e: Exception) {
            SyncResult(name, used, e.message ?: "Unbekannter Fehler")
        }
    }
}

object SwitchSync {
    suspend fun run(actors: List<SwitchActor>, rules: List<SwitchRule>, settings: AppSettings): List<SyncResult> =
        coroutineScope {
            actors.map { a ->
                async { syncOne(a, rules.filter { it.enabled && it.weekdays.isNotEmpty() && a.id in it.actorIds }, settings) }
            }.awaitAll()
        }

    private fun switchCall(id: Int, on: Boolean): JSONObject =
        JSONObject().put("method", "Switch.Set").put("params", JSONObject().put("id", id).put("on", on))

    private suspend fun syncOne(a: SwitchActor, rules: List<SwitchRule>, settings: AppSettings): SyncResult {
        val jobs: Int = rules.count() + rules.count { it.hasEnd }
        if (!a.hasHost) return SyncResult(a.name, 0, if (jobs == 0) null else "Keine IP-Adresse eingetragen", jobs == 0)
        if (jobs > ScheduleBuilder.MAX_JOBS) {
            return SyncResult(a.name, jobs, "Zu viele Zeitpunkte (max. ${ScheduleBuilder.MAX_JOBS} pro Gerät)")
        }
        val client = a.client()
        return try {
            client.pushLocation(settings)
            client.deleteAllSchedules()
            for (r in rules) {
                client.createSchedule(ScheduleBuilder.timespec(r.start, r.weekdays), JSONArray().put(switchCall(a.switchId, true)))
                if (r.hasEnd) {
                    client.createSchedule(
                        ScheduleBuilder.timespec(r.end, ScheduleBuilder.endDays(r)),
                        JSONArray().put(switchCall(a.switchId, false)),
                    )
                }
            }
            SyncResult(a.name, jobs, null)
        } catch (e: Exception) {
            SyncResult(a.name, jobs, e.message ?: "Unbekannter Fehler")
        }
    }
}

/**
 * Sonnenauf-/-untergang nach NOAA-Näherung (±1–2 min).
 * Nur für die Vorschau – ausgeführt wird mit der Berechnung des Shelly.
 */
object SunCalculator {
    fun sunTimes(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): Pair<ZonedDateTime, ZonedDateTime>? {
        val g = 2 * PI / 365 * (date.dayOfYear - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) -
            0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
            0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val latRad = Math.toRadians(lat)
        val cosHA = cos(Math.toRadians(90.833)) / (cos(latRad) * cos(decl)) - tan(latRad) * tan(decl)
        if (cosHA < -1 || cosHA > 1) return null   // Polartag/-nacht
        val ha = Math.toDegrees(acos(cosHA))
        val riseMin = 720 - 4 * (lon + ha) - eqTime
        val setMin = 720 - 4 * (lon - ha) - eqTime
        val midnight = date.atStartOfDay(ZoneOffset.UTC)
        return midnight.plusSeconds((riseMin * 60).toLong()).withZoneSameInstant(zone) to
            midnight.plusSeconds((setMin * 60).toLong()).withZoneSameInstant(zone)
    }

    fun today(s: AppSettings) =
        if (!s.hasLocation) null else sunTimes(LocalDate.now(s.zone), s.latitude, s.longitude, s.zone)

    /** Nächster Ausführungszeitpunkt einer Regel (Anzeige) */
    fun nextRun(r: ScheduleRule, s: AppSettings, now: ZonedDateTime = ZonedDateTime.now(s.zone)): ZonedDateTime? =
        if (!r.enabled) null else nextRun(r.timePoint, r.weekdays, s, now)

    fun nextRun(t: TimePoint, weekdays: Set<Int>, s: AppSettings, now: ZonedDateTime = ZonedDateTime.now(s.zone)): ZonedDateTime? {
        if (weekdays.isEmpty()) return null
        for (i in 0..7) {
            val day = now.toLocalDate().plusDays(i.toLong())
            if (day.dayOfWeek.value !in weekdays) continue
            val candidate = when (t.trigger) {
                TriggerKind.TIME -> day.atTime(t.hour, t.minute).atZone(s.zone)
                else -> {
                    if (!s.hasLocation) return null
                    val st = sunTimes(day, s.latitude, s.longitude, s.zone) ?: return null
                    (if (t.trigger == TriggerKind.SUNRISE) st.first else st.second)
                        .plusMinutes(t.offsetMinutes.toLong())
                }
            }
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }

    /** Uhrzeit eines Zeitpunkts heute (Vorschau) */
    fun todayAt(t: TimePoint, s: AppSettings): ZonedDateTime? = when (t.trigger) {
        TriggerKind.TIME -> java.time.LocalDate.now(s.zone).atTime(t.hour, t.minute).atZone(s.zone)
        else -> today(s)?.let { (if (t.trigger == TriggerKind.SUNRISE) it.first else it.second).plusMinutes(t.offsetMinutes.toLong()) }
    }
}
