package de.refadim.rollladen

import org.json.JSONArray
import org.json.JSONObject

/**
 * Skripte, die die App auf die Shellys spielt (Variante B: die Taster-Sperre läuft auf den Geräten,
 * das Display muss dafür nicht eingeschaltet sein).
 *
 * 2PM (rl_auto):
 *  - Zeitpläne rufen auto(...) auf. Ist die Sperre (KVS "rl_lock") gesetzt, passiert nichts.
 *  - http://<2pm>/script/<id>/api?up | ?down  → Tasterbefehl (fährt bzw. stoppt, wenn er gerade fährt)
 *  - http://<2pm>/script/<id>/api?lock        → Sperre setzen und eine laufende Automatik-Fahrt stoppen
 *  - http://<2pm>/script/<id>/api?unlock      → Sperre aufheben
 *
 * i4 DC (rl_taster):
 *  - Bei btn_down an Eingang n: Tasterbefehl an den zugeordneten 2PM, danach Sperre an alle 2PM.
 */
object DeviceScripts {
    const val COVER_SCRIPT = "rl_auto"
    const val INPUT_SCRIPT = "rl_taster"
    const val LOCK_KEY = "rl_lock"

    fun cover(coverId: Int): String = COVER_TEMPLATE
        .replace("%COVER%", coverId.toString())
        .replace("%KEY%", "cover:$coverId")
        .replace("%LOCK%", LOCK_KEY)

    /** buttonUrls: 4 Einträge (leer = Eingang nicht belegt); lockUrls: Sperr-Aufruf je 2PM */
    fun input(buttonUrls: List<String>, lockUrls: List<String>): String {
        val cfg = JSONObject().put("b", JSONArray(buttonUrls)).put("l", JSONArray(lockUrls))
        return INPUT_TEMPLATE.replace("%CFG%", cfg.toString())
    }

    /** Aufruf, den ein Shelly-Zeitplan auf dem 2PM ausführt */
    fun autoCall(scriptId: Int, action: ShutterAction, position: Int): JSONObject {
        val code = when (action) {
            ShutterAction.OPEN -> "auto(\"open\",0)"
            ShutterAction.CLOSE -> "auto(\"close\",0)"
            ShutterAction.POSITION -> "auto(\"pos\",${position.coerceIn(0, 100)})"
        }
        return JSONObject().put("method", "Script.Eval")
            .put("params", JSONObject().put("id", scriptId).put("code", code))
    }

    private val COVER_TEMPLATE = """
// rl_auto - installiert von der Rollladen-App. Wird beim Uebertragen ueberschrieben.
let COVER = %COVER%;
let KEY = "%KEY%";
let LOCK = "%LOCK%";
let autoMove = false;

function auto(action, pos) {
  Shelly.call("KVS.Get", { key: LOCK }, function (res, err) {
    if (err === 0 && res && res.value === true) {
      print("Automatik gesperrt (Taster)");
      return;
    }
    autoMove = true;
    if (action === "open") {
      Shelly.call("Cover.Open", { id: COVER });
    } else if (action === "close") {
      Shelly.call("Cover.Close", { id: COVER });
    } else {
      Shelly.call("Cover.GoToPosition", { id: COVER, pos: pos });
    }
  });
}

function button(dir) {
  autoMove = false;
  let st = Shelly.getComponentStatus(KEY);
  if (st && (st.state === "opening" || st.state === "closing")) {
    Shelly.call("Cover.Stop", { id: COVER });
  } else if (dir === "up") {
    Shelly.call("Cover.Open", { id: COVER });
  } else {
    Shelly.call("Cover.Close", { id: COVER });
  }
}

function lock() {
  Shelly.call("KVS.Set", { key: LOCK, value: true });
  if (autoMove) {
    autoMove = false;
    Shelly.call("Cover.Stop", { id: COVER });
  }
}

Shelly.addStatusHandler(function (ev) {
  if (ev.component !== KEY || !ev.delta || ev.delta.state === undefined) return;
  let s = ev.delta.state;
  if (s === "open" || s === "closed" || s === "stopped") autoMove = false;
});

HTTPServer.registerEndpoint("api", function (req, res) {
  let q = req.query;
  if (q === "up" || q === "down") {
    button(q);
  } else if (q === "lock") {
    lock();
  } else if (q === "unlock") {
    Shelly.call("KVS.Set", { key: LOCK, value: false });
  }
  res.code = 200;
  res.body = "ok";
  res.send();
});
""".trimStart()

    private val INPUT_TEMPLATE = """
// rl_taster - installiert von der Rollladen-App. Wird beim Uebertragen ueberschrieben.
let CFG = %CFG%;
let prio = [];
let queue = [];
let busy = 0;

function pump() {
  while (busy < 3) {
    let u = "";
    if (prio.length > 0) {
      u = prio[0];
      prio.splice(0, 1);
    } else if (queue.length > 0) {
      u = queue[0];
      queue.splice(0, 1);
    } else {
      return;
    }
    busy++;
    Shelly.call("HTTP.GET", { url: u, timeout: 5 }, function (res, err) {
      busy--;
      if (err !== 0) print("Aufruf fehlgeschlagen");
      pump();
    });
  }
}

function queued(u) {
  for (let i = 0; i < queue.length; i++) {
    if (queue[i] === u) return true;
  }
  return false;
}

Shelly.addEventHandler(function (ev) {
  let info = ev.info;
  if (!info || info.event !== "btn_down") return;
  let n = info.id;
  if (n === undefined || n < 0 || n >= CFG.b.length) return;
  let u = CFG.b[n];
  if (u === "") return;
  prio.push(u);
  for (let i = 0; i < CFG.l.length; i++) {
    if (!queued(CFG.l[i])) queue.push(CFG.l[i]);
  }
  pump();
});
""".trimStart()
}
