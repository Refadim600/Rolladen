# Rollläden – iOS/iPadOS-App für Shelly 2PM Gen4

SwiftUI-App (iOS/iPadOS 17+) zur Steuerung von 12 Rollläden über Shelly 2PM Gen4 im WLAN.

## Funktionen
- **Rollläden-Tab:** Status jedes Rollladens (offen/geschlossen/Position) mit Auf, Stopp und Ab pro Rollladen.
- **ALLE ÖFFNEN:** große grüne Schaltfläche, die immer sichtbar ist. Sie schickt `Cover.Open` gleichzeitig an alle Geräte und zeigt Fehler einzeln an. Darunter liegen „Alle stoppen“ und „Alle schließen“.
- **Wochenplan:** beliebig viele Regeln. Jede Regel hat:
  - eine Aktion: Öffnen, Schließen oder eine Position in %
  - einen Auslöser: feste Uhrzeit, **Sonnenaufgang** oder **Sonnenuntergang**, jeweils mit Versatz (±3 h in 5-min-Schritten)
  - Wochentage (mit Schnellwahl Mo–Fr, Wochenende, Täglich)
  - eine frei wählbare Auswahl der Rollläden
  
  So fahren Rollläden zur gleichen Zeit oder zu unterschiedlichen Zeiten, je nachdem, wie du die Regeln anlegst. Zu jeder Regel zeigt die App die nächste Ausführung an.
- **Einstellungen:** Standort (manuell oder per GPS) und Zeitzone.

## Architektur: Warum die Zeitpläne auf den Shellys laufen
iOS lässt Apps nicht zuverlässig zu festen Uhrzeiten im Hintergrund laufen. Deshalb übersetzt die App jede Regel in einen **Shelly-Zeitplan** (`Schedule.Create`) und schreibt ihn auf jedes betroffene Gerät. Die Shellys führen die Zeitpläne selbst aus, auch wenn iPhone oder iPad aus sind.

| Regel | Shelly-Timespec |
|---|---|
| Mo–Fr 07:00 öffnen | `0 0 7 * * MON,TUE,WED,THU,FRI` |
| Täglich 30 min nach Sonnenuntergang schließen | `@sunset+30m * * MON,…,SUN` |
| Sa/So 1 h vor Sonnenaufgang auf 30 % | `@sunrise-1h * * SAT,SUN` → `Cover.GoToPosition pos=30` |

Beim Übertragen setzt die App außerdem Standort und Zeitzone (`Sys.SetConfig` → `location`), damit der Shelly den Sonnenstand richtig berechnet.

⚠️ Beim Übertragen ruft die App `Schedule.DeleteAll` auf. **Zeitpläne, die du vorher in der Shelly-App angelegt hast, werden damit ersetzt.** Pro Gerät sind maximal 20 Zeitpläne möglich.

## Voraussetzungen an den Shellys
1. Jeder 2PM Gen4 ist im **Cover-Modus** und **kalibriert**. Das geht in der Shelly-App oder in der Weboberfläche des Geräts.
2. Jeder Shelly hat eine **feste IP** (DHCP-Reservierung im Router). Alternativ funktioniert der Hostname `shelly2pmg4-XXXXXX.local`.
3. Ein Passwort ist optional. Ist eines gesetzt, trägst du es in der App ein (Benutzer ist immer `admin`, Anmeldung per HTTP Digest SHA-256). Die App speichert das Passwort im Schlüsselbund.

## Projekt bauen
**Mit XcodeGen (empfohlen):**
```bash
brew install xcodegen
cd Rollladen
xcodegen generate
open Rollladen.xcodeproj
```
Danach unter *Signing & Capabilities* dein Team auswählen.

**Ohne XcodeGen:** In Xcode ein neues Projekt *iOS App* (SwiftUI) anlegen, dann alle Dateien aus `Sources/` hineinziehen und die vorgegebene `ContentView.swift` und `…App.swift` löschen. Unter *Info* folgende Einträge ergänzen:
- `NSLocalNetworkUsageDescription`
- `NSLocationWhenInUseUsageDescription`
- `NSAppTransportSecurity → NSAllowsLocalNetworking = YES`

## Dateien
| Datei | Inhalt |
|---|---|
| `Models.swift` | Rollladen, Regel, Wochentage, Einstellungen |
| `AppStore.swift` | Datenhaltung (JSON in Application Support) |
| `ShellyClient.swift` | RPC-Client (`POST /rpc`) inkl. Digest-Authentifizierung |
| `ScheduleSync.swift` | Regeln → Shelly-Timespecs, parallele Übertragung |
| `SunCalculator.swift` | Sonnenauf- und -untergang (NOAA), nur zur Vorschau |
| `Support.swift` | Schlüsselbund, Standortabfrage |
| `*View.swift` | Oberfläche |

## Grenzen und Erweiterungsideen
- Von unterwegs funktioniert die App nur per VPN ins Heimnetz. Alternativ ginge die Shelly Cloud API.
- Bedingungen wie „Sonnenuntergang, aber nicht vor 19:00“ beherrschen Shelly-Zeitpläne nicht. Dafür bräuchte es ein Shelly-Script oder einen Server, etwa Home Assistant.
- Geräte findet die App nicht automatisch. Eine Suche per Bonjour (`_shelly._tcp`) ließe sich ergänzen.
