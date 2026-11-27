# Rollläden & Hausdisplay – Android-App für Shelly

Native Android-App (Kotlin, Jetpack Compose) für ein Wanddisplay im Eingangsbereich: 12 Rollläden über Shelly 2PM Gen3, Taster über Shelly Plus i4 DC, Schalter über Shelly 1PM Mini Gen3, dazu ein Ruhebildschirm mit Diashow, Wetterkarte und Temperaturen. Läuft auf Tablets und Smartphones ab Android 5.

## Funktionen
- **Ruhebildschirm** (per Wischen umschaltbar, Antippen öffnet die Steuerung):
  - **Diashow** mit Bildern aus einem Ordner. Die Bilder werden auf der Konfigurationsseite ausgewählt, das Intervall ist einstellbar. Uhrzeit, Datum und Temperaturen werden eingeblendet.
  - **Wetter:** Niederschlagsradar des Deutschen Wetterdienstes (kostenlos, WMS `maps.dwd.de`) auf einer OpenStreetMap-Karte rund um den Standort, dazu Innen- und Außentemperatur von DS18B20-Fühlern am Shelly Plus Add-on.
  - Nach einstellbarer Zeit ohne Bedienung kehrt die App zum Ruhebildschirm zurück.
- **ALLE ÖFFNEN:** große grüne Schaltfläche, die oben immer sichtbar ist. Darunter liegen „Alle stoppen“ und „Alle schließen“.
- **Einzelsteuerung:** Auf, Stopp und Ab pro Rollladen, mit Status und Position in %.
- **Wochenplan:** Regeln mit Aktion (Öffnen, Schließen, Position), Zeitpunkt (Uhrzeit, Sonnenaufgang, Sonnenuntergang ± Versatz), Wochentagen und frei wählbaren Rollläden.
- **Taster (Plus i4 DC):** Jeder Eingang wird einem Rollladen und einer Richtung zugeordnet (Standard: 6 Module × 4 Eingänge = 12 Rollläden × Auf/Ab). Die App zeigt die Tastendrücke live an.
- **Taster-Vorrang:** Ein Tastendruck sperrt die Automatik **aller** Rollläden und stoppt laufende Automatik-Fahrten. Danach werden nur noch Taster- und App-Befehle ausgeführt, bis am Display „Automatik freigeben“ gedrückt wird.
- **Schalter (1PM Mini Gen3):** frei benennbar (Standard: 8 Stück), manuell schaltbar. Schaltregeln mit Einschalten (Start) und optional Ausschalten (Ende), jeweils Uhrzeit oder Sonnenauf-/-untergang, plus Wochenplan.

## So funktioniert die Taster-Sperre (alles auf den Shellys, ohne Display)
Beim Übertragen spielt die App Skripte auf die Geräte:
- **2PM – Skript `rl_auto`:** Die Zeitpläne rufen `auto(...)` auf. Ist der Schlüssel `rl_lock` im KVS gesetzt, wird nicht gefahren. Das Skript stellt `http://<2pm>/script/<id>/api?up|down|lock|unlock` bereit.
- **i4 DC – Skript `rl_taster`:** Bei einem Tastendruck (`btn_down`) geht zuerst der Fahrbefehl an den zugehörigen 2PM (fährt bzw. stoppt, wenn er gerade fährt), danach `lock` an alle 2PM.
- **Freigabe:** Der Button am Display setzt `rl_lock` auf allen 2PM zurück.

Die Rollläden, die Taster und der Wochenplan funktionieren also auch, wenn das Display aus ist. Nur WLAN und Router müssen laufen.

## APK herunterladen und installieren
1. Auf dem Tablet direkt laden: https://github.com/Refadim600/Rolladen/releases/latest/download/Rollladen.apk (alternativ unter **Actions → Android-Build → Artifacts** als ZIP).
2. Die APK öffnen. Android fragt dabei nach der Erlaubnis „Unbekannte Apps installieren“, die erteilst du einmalig.

## Voraussetzungen an den Shellys
- Jeder 2PM Gen3 ist im **Cover-Modus** und **kalibriert**.
- Jeder Shelly hat eine **feste IP** (DHCP-Reservierung im Router).
- **2PM und i4 DC ohne Passwort:** Die i4 DC rufen die 2PM direkt auf und können sich nicht anmelden. Für die Live-Anzeige der Tastendrücke dürfen auch die i4 DC kein Passwort haben. Bei den 1PM Mini und beim Add-on-Shelly ist ein Passwort möglich (Benutzer `admin`, HTTP Digest SHA-256).
- Die DS18B20-Fühler werden einmalig im Webinterface des Add-on-Shellys eingerichtet. In der App unter „Fühler suchen“ ordnest du innen und außen zu.
- **Als Wanddisplay:** In Android unter Standard-Apps → Startbildschirm „Rollläden“ wählen. Dann startet die App nach dem Einschalten automatisch.

## Projektstruktur
- `android/`: Android-App. Der Build läuft automatisch bei jedem Push auf `main`.
- `ios/`: frühere SwiftUI-Version für iPhone/iPad. Ihr Build läuft nur noch manuell.
