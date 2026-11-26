package de.refadim.rollladen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class ShellyException(message: String) : Exception(message)

data class CoverStatus(val state: String, val position: Int?) {
    val stateText: String
        get() = when (state) {
            "open" -> "offen"
            "closed" -> "geschlossen"
            "opening" -> "öffnet …"
            "closing" -> "schließt …"
            "stopped" -> "gestoppt"
            "calibrating" -> "kalibriert …"
            else -> state
        }
}

/**
 * Lokale RPC-Schnittstelle der Shelly Gen2+/Gen4-Geräte: POST http://<host>/rpc
 * Unterstützt die optionale Geräte-Authentifizierung (HTTP Digest, SHA-256).
 */
class ShellyClient(private val host: String, private val password: String) {

    suspend fun open(id: Int = 0) { call("Cover.Open", JSONObject().put("id", id)) }
    suspend fun close(id: Int = 0) { call("Cover.Close", JSONObject().put("id", id)) }
    suspend fun stop(id: Int = 0) { call("Cover.Stop", JSONObject().put("id", id)) }
    suspend fun goTo(position: Int, id: Int = 0) {
        call("Cover.GoToPosition", JSONObject().put("id", id).put("pos", position.coerceIn(0, 100)))
    }

    suspend fun status(id: Int = 0): CoverStatus {
        val r = call("Cover.GetStatus", JSONObject().put("id", id)) as? JSONObject ?: JSONObject()
        val pos = if (r.has("current_pos") && !r.isNull("current_pos")) r.getInt("current_pos") else null
        return CoverStatus(r.optString("state", "unbekannt"), pos)
    }

    suspend fun deviceInfo(): JSONObject = call("Shelly.GetDeviceInfo") as? JSONObject ?: JSONObject()

    suspend fun deleteAllSchedules() { call("Schedule.DeleteAll") }

    suspend fun createSchedule(timespec: String, calls: JSONArray) {
        call("Schedule.Create", JSONObject().put("enable", true).put("timespec", timespec).put("calls", calls))
    }

    suspend fun setLocation(timeZone: String, latitude: Double?, longitude: Double?) {
        val location = JSONObject().put("tz", timeZone)
        if (latitude != null && longitude != null) location.put("lat", latitude).put("lon", longitude)
        call("Sys.SetConfig", JSONObject().put("config", JSONObject().put("location", location)))
    }

    suspend fun call(method: String, params: JSONObject = JSONObject()): Any? = withContext(Dispatchers.IO) {
        if (host.isBlank()) throw ShellyException("Keine Adresse eingetragen")
        val body = JSONObject()
            .put("id", 1).put("src", "rollladen-app")
            .put("method", method).put("params", params)
            .toString()

        var resp = post(body, null)
        if (resp.code == 401) {
            if (password.isEmpty()) throw ShellyException("Gerät ist passwortgeschützt – bitte Passwort eintragen")
            val challenge = resp.wwwAuthenticate ?: throw ShellyException("Authentifizierung fehlgeschlagen")
            resp = post(body, DigestAuth.header(challenge, "admin", password, "POST", "/rpc"))
            if (resp.code == 401) throw ShellyException("Passwort falsch")
        }

        val json = try { JSONObject(resp.body) } catch (e: JSONException) { null }
        val err = json?.optJSONObject("error") ?: if (json?.has("code") == true) json else null
        if (err != null) {
            throw ShellyException("Shelly meldet: " + err.optString("message", "Code ${err.opt("code")}"))
        }
        if (resp.code !in 200..299) throw ShellyException("HTTP-Fehler ${resp.code}")
        json?.opt("result")
    }

    private class Response(val code: Int, val body: String, val wwwAuthenticate: String?)

    private fun post(body: String, authorization: String?): Response {
        val conn = try {
            URL("http://$host/rpc").openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw ShellyException("Ungültige Adresse: $host")
        }
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 4000
            conn.readTimeout = 6000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            authorization?.let { conn.setRequestProperty("Authorization", it) }
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return Response(code, text, conn.getHeaderField("WWW-Authenticate"))
        } catch (e: IOException) {
            throw ShellyException("Nicht erreichbar ($host)")
        } finally {
            conn.disconnect()
        }
    }
}

/** HTTP Digest nach RFC 7616, wie von Shelly Gen2+ verwendet (SHA-256). */
object DigestAuth {
    fun header(challenge: String, user: String, password: String, method: String, uri: String): String {
        val p = parse(challenge)
        val realm = p["realm"] ?: ""
        val nonce = p["nonce"] ?: ""
        val algorithm = p["algorithm"] ?: "MD5"
        val qopAuth = p["qop"]?.split(",")?.map { it.trim() }?.contains("auth") == true
        val cnonce = (1..16).map { "0123456789abcdef".random() }.joinToString("")
        val nc = "00000001"
        val digest = if (algorithm.uppercase().startsWith("SHA-256")) "SHA-256" else "MD5"

        fun h(s: String) = MessageDigest.getInstance(digest).digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val ha1 = h("$user:$realm:$password")
        val ha2 = h("$method:$uri")
        val response = if (qopAuth) h("$ha1:$nonce:$nc:$cnonce:auth:$ha2") else h("$ha1:$nonce:$ha2")

        val parts = mutableListOf(
            "username=\"$user\"", "realm=\"$realm\"", "nonce=\"$nonce\"",
            "uri=\"$uri\"", "algorithm=$algorithm", "response=\"$response\"",
        )
        if (qopAuth) parts += listOf("qop=auth", "nc=$nc", "cnonce=\"$cnonce\"")
        p["opaque"]?.let { parts += "opaque=\"$it\"" }
        return "Digest " + parts.joinToString(", ")
    }

    fun parse(header: String): Map<String, String> {
        val s = header.trim().let { if (it.lowercase().startsWith("digest ")) it.substring(7) else it }
        return Regex("""(\w+)=(?:"([^"]*)"|([^,\s]*))""").findAll(s).associate { m ->
            m.groupValues[1].lowercase() to (m.groups[2]?.value ?: m.groups[3]?.value ?: "")
        }
    }
}
