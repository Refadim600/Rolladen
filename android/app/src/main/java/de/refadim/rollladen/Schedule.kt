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
    fun timespec(r: ScheduleRule): String {
        val days = r.weekdays.sorted().joinToString(",") { Days.SHELLY[it - 1] }
        return when (r.trigger) {
            TriggerKind.TIME -> "0 ${r.minute} ${r.hour} * * $days"
            TriggerKind.SUNRISE -> "@sunrise${offset(r.offsetMinutes)} * * $days"
            TriggerKind.SUNSET -> "@sunset${offset(r.offsetMinutes)} * * $days"
        }
    }

    /** "+1h30m", "-15m" oder "" (Shelly erlaubt max. ±12 h) */
    fun offset(minutes: Int): String {
        if (minutes == 0) return ""
        val h = abs(minutes) / 60
        val m = abs(minutes) % 60
        return (if (minutes > 0) "+" else "-") + (if (h > 0) "${h}h" else "") + (if (m > 0) "${m}m" else "")
    }

    fun call(r: ScheduleRule, coverId: Int): JSONObject {
        val params = JSONObject().put("id", coverId)
        val method = when (r.action) {
            ShutterAction.OPEN -> "Cover.Open"
            ShutterAction.CLOSE -> "Cover.Close"
            ShutterAction.POSITION -> { params.put("pos", r.position); "Cover.GoToPosition" }
        }
        return JSONObject().put("method", method).put("params", params)
    }

    fun rulesFor(s: Shutter, rules: List<ScheduleRule>) =
        rules.filter { it.enabled && it.weekdays.isNotEmpty() && s.id in it.shutterIds }
}

data class SyncResult(val name: String, val jobs: Int, val error: String?, val skipped: Boolean = false)

object ScheduleSync {
    /** Überträgt den Wochenplan parallel auf alle Geräte. Vorhandene Zeitpläne werden ersetzt. */
    suspend fun run(shutters: List<Shutter>, rules: List<ScheduleRule>, settings: AppSettings): List<SyncResult> =
        coroutineScope {
            shutters.map { s -> async { syncOne(s, ScheduleBuilder.rulesFor(s, rules), settings) } }.awaitAll()
        }

    private suspend fun syncOne(s: Shutter, jobs: List<ScheduleRule>, settings: AppSettings): SyncResult {
        if (!s.hasHost) {
            return SyncResult(s.name, 0, if (jobs.isEmpty()) null else "Keine IP-Adresse eingetragen", jobs.isEmpty())
        }
        if (jobs.size > ScheduleBuilder.MAX_JOBS) {
            return SyncResult(s.name, jobs.size, "Zu viele Regeln (max. ${ScheduleBuilder.MAX_JOBS} pro Gerät)")
        }
        val client = s.client()
        return try {
            if (settings.pushLocation) {
                client.setLocation(
                    settings.timeZone,
                    settings.latitude.takeIf { settings.hasLocation },
                    settings.longitude.takeIf { settings.hasLocation },
                )
            }
            client.deleteAllSchedules()
            for (r in jobs) {
                client.createSchedule(ScheduleBuilder.timespec(r), JSONArray().put(ScheduleBuilder.call(r, s.coverId)))
            }
            SyncResult(s.name, jobs.size, null)
        } catch (e: Exception) {
            SyncResult(s.name, jobs.size, e.message ?: "Unbekannter Fehler")
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
    fun nextRun(r: ScheduleRule, s: AppSettings, now: ZonedDateTime = ZonedDateTime.now(s.zone)): ZonedDateTime? {
        if (!r.enabled || r.weekdays.isEmpty()) return null
        for (i in 0..7) {
            val day = now.toLocalDate().plusDays(i.toLong())
            if (day.dayOfWeek.value !in r.weekdays) continue
            val candidate = when (r.trigger) {
                TriggerKind.TIME -> day.atTime(r.hour, r.minute).atZone(s.zone)
                else -> {
                    if (!s.hasLocation) return null
                    val t = sunTimes(day, s.latitude, s.longitude, s.zone) ?: return null
                    (if (r.trigger == TriggerKind.SUNRISE) t.first else t.second)
                        .plusMinutes(r.offsetMinutes.toLong())
                }
            }
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }
}
