package de.refadim.rollladen

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Hört per WebSocket (ws://<i4>/rpc) auf die Tastendrücke der Plus i4 DC.
 * Die Rollläden fahren unabhängig davon – das hier dient nur der Anzeige in der App.
 * Funktioniert nur ohne Geräte-Passwort.
 */
class InputEventListener(
    private val scope: CoroutineScope,
    private val onPress: (moduleId: String, input: Int) -> Unit,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private val jobs = mutableListOf<Job>()
    private val src = "rl-display-" + (1000..9999).random()

    fun update(modules: List<InputModule>) {
        stop()
        modules.filter { it.hasHost && it.password.isEmpty() }.forEach { m ->
            jobs += scope.launch { connectLoop(m.id, m.host.trim()) }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    private suspend fun connectLoop(moduleId: String, host: String) {
        while (currentCoroutineContext().isActive) {
            val closed = CompletableDeferred<Unit>()
            val ws = http.newWebSocket(
                Request.Builder().url("ws://$host/rpc").build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        // Eine erste Anfrage meldet uns als Empfänger für Benachrichtigungen an
                        webSocket.send(
                            JSONObject().put("id", 1).put("src", src).put("method", "Shelly.GetDeviceInfo").toString()
                        )
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        parse(text).forEach { input -> scope.launch { onPress(moduleId, input) } }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        closed.complete(Unit)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        closed.complete(Unit)
                    }
                },
            )
            try {
                closed.await()
            } finally {
                ws.cancel()
            }
            delay(10_000)
        }
    }

    private fun parse(text: String): List<Int> = try {
        val j = JSONObject(text)
        if (j.optString("method") != "NotifyEvent") emptyList()
        else {
            val events = j.optJSONObject("params")?.optJSONArray("events")
            val out = mutableListOf<Int>()
            if (events != null) {
                for (i in 0 until events.length()) {
                    val e = events.optJSONObject(i) ?: continue
                    val comp = e.optString("component")
                    if (e.optString("event") == "btn_down" && comp.startsWith("input:")) {
                        val id = if (e.has("id")) e.optInt("id", -1) else comp.substringAfter(':').toIntOrNull() ?: -1
                        if (id >= 0) out += id
                    }
                }
            }
            out
        }
    } catch (e: Exception) {
        emptyList()
    }
}
