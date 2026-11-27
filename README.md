# Rollläden – Android-App für Shelly 2PM Gen4

Native Android-App (Kotlin, Jetpack Compose) für 12 Rollläden, die über Shelly 2PM Gen4 im WLAN angesteuert werden. Sie läuft auf Tablets und Smartphones ab Android 5.

## Funktionen
- **ALLE ÖFFNEN:** große grüne Schaltfläche, die oben immer sichtbar ist. Sie öffnet alle Rollläden gleichzeitig und meldet Fehler einzeln. Darunter liegen „Alle stoppen“ und „Alle schließen“.
- **Einzelsteuerung:** Auf, Stopp und Ab pro Rollladen, mit Status und Position in %. Auf dem Tablet stehen die Rollläden in einem mehrspaltigen Raster.
- **Wochenplan:** beliebig viele Regeln. Jede Regel hat:
  - eine Aktion: Öffnen, Schließen oder eine Position in %
  - einen Zeitpunkt: Uhrzeit, **Sonnenaufgang** oder **Sonnenuntergang**, jeweils mit Versatz (±3 h)
  - Wochentage
  - eine frei wählbare Auswahl der Rollläden
  
  So fahren Rollläden zur gleichen Zeit oder zu unterschiedlichen Zeiten, je nachdem, wie du die Regeln anlegst.
- **Einstellungen:** Standort (manuell oder per Ortung) und Zeitzone.

Der Wochenplan wird als Shelly-Zeitplan (`Schedule.Create`) **auf jedem Gerät gespeichert** und läuft dort. Das Tablet muss dafür nicht eingeschaltet sein. Beim Übertragen ersetzt die App vorhandene Zeitpläne auf den Shellys (`Schedule.DeleteAll`). Pro Gerät sind maximal 20 Zeitpläne möglich.

## APK herunterladen und installieren
1. Auf dem Tablet direkt laden: https://github.com/Refadim600/Rolladen/releases/latest/download/Rollladen.apk (alternativ unter **Actions → Android-Build → Artifacts** als ZIP).
2. Die APK öffnen. Android fragt dabei nach der Erlaubnis „Unbekannte Apps installieren“, die erteilst du einmalig.

## Voraussetzungen an den Shellys
- Jeder 2PM Gen4 ist im **Cover-Modus** und **kalibriert**.
- Jeder Shelly hat eine **feste IP** (DHCP-Reservierung im Router).
- Ein Passwort ist optional. Ist eines gesetzt, trägst du es in der App ein (Benutzer `admin`, Anmeldung per HTTP Digest SHA-256).

## Projektstruktur
- `android/`: Android-App. Der Build läuft automatisch bei jedem Push auf `main`.
- `ios/`: frühere SwiftUI-Version für iPhone/iPad. Ihr Build läuft nur noch manuell.
