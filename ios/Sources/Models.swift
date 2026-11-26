import Foundation

// MARK: - Rollladen (ein Shelly 2PM Gen4 im Cover-Modus)

struct Shutter: Identifiable, Codable, Hashable {
    var id = UUID()
    var name: String
    /// IP-Adresse oder Hostname, z. B. "192.168.178.41" oder "shelly2pmg4-abc123.local"
    var host: String = ""
    /// Cover-Instanz auf dem Gerät. Beim 2PM Gen4 im Cover-Modus immer 0.
    var coverID: Int = 0

    var hasHost: Bool { !host.trimmingCharacters(in: .whitespaces).isEmpty }

    /// Passwort liegt nicht im JSON, sondern im Schlüsselbund.
    var client: ShellyClient {
        ShellyClient(host: host.trimmingCharacters(in: .whitespaces),
                     password: Keychain.password(for: id))
    }
}

// MARK: - Wochentage

enum Weekday: Int, Codable, CaseIterable, Identifiable, Comparable {
    case mon = 1, tue, wed, thu, fri, sat, sun

    var id: Int { rawValue }
    var shellyCode: String { ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"][rawValue - 1] }
    var short: String { ["Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"][rawValue - 1] }
    /// Wochentag im Sinne von `Calendar` (1 = Sonntag … 7 = Samstag)
    var calendarWeekday: Int { rawValue == 7 ? 1 : rawValue + 1 }

    static let workdays: Set<Weekday> = [.mon, .tue, .wed, .thu, .fri]
    static let weekend: Set<Weekday> = [.sat, .sun]
    static let all = Set(Weekday.allCases)

    static func < (a: Weekday, b: Weekday) -> Bool { a.rawValue < b.rawValue }
}

// MARK: - Regel im Wochenplan

enum TriggerKind: String, Codable, CaseIterable, Identifiable {
    case time, sunrise, sunset
    var id: String { rawValue }
    var label: String {
        switch self {
        case .time: "Uhrzeit"
        case .sunrise: "Sonnenaufgang"
        case .sunset: "Sonnenuntergang"
        }
    }
    var isSunBased: Bool { self != .time }
}

enum ShutterAction: String, Codable, CaseIterable, Identifiable {
    case open, close, position
    var id: String { rawValue }
    var label: String {
        switch self {
        case .open: "Öffnen"
        case .close: "Schließen"
        case .position: "Position"
        }
    }
    var icon: String {
        switch self {
        case .open: "arrow.up.to.line"
        case .close: "arrow.down.to.line"
        case .position: "slider.vertical.3"
        }
    }
}

struct ScheduleRule: Identifiable, Codable, Hashable {
    var id = UUID()
    var name = "Neue Regel"
    var enabled = true

    var action: ShutterAction = .close
    /// Zielposition in % (0 = geschlossen, 100 = offen), nur bei `.position`
    var position = 50

    var trigger: TriggerKind = .time
    var hour = 20
    var minute = 0
    /// Versatz zu Sonnenauf-/-untergang in Minuten (negativ = vorher)
    var offsetMinutes = 0

    var weekdays: Set<Weekday> = Weekday.all
    var shutterIDs: Set<UUID> = []
}

extension ScheduleRule {
    var weekdaysText: String {
        switch weekdays {
        case Weekday.all: return "Täglich"
        case Weekday.workdays: return "Mo–Fr"
        case Weekday.weekend: return "Sa, So"
        case _ where weekdays.isEmpty: return "Kein Tag gewählt"
        default: return weekdays.sorted().map(\.short).joined(separator: ", ")
        }
    }

    var triggerText: String {
        switch trigger {
        case .time:
            return String(format: "%02d:%02d Uhr", hour, minute)
        case .sunrise, .sunset:
            guard offsetMinutes != 0 else { return trigger.label }
            let sign = offsetMinutes > 0 ? "+" : "−"
            return "\(trigger.label) \(sign)\(Self.durationText(abs(offsetMinutes)))"
        }
    }

    var actionText: String {
        action == .position ? "Auf \(position) % fahren" : action.label
    }

    static func durationText(_ minutes: Int) -> String {
        let h = minutes / 60, m = minutes % 60
        switch (h, m) {
        case (0, _): return "\(m) min"
        case (_, 0): return "\(h) h"
        default: return "\(h) h \(m) min"
        }
    }
}

// MARK: - Einstellungen

struct AppSettings: Codable, Hashable {
    var latitude: Double = 0
    var longitude: Double = 0
    var timeZone: String = TimeZone.current.identifier
    /// Standort und Zeitzone beim Übertragen auch auf die Shellys schreiben
    var pushLocationToDevices = true

    var hasLocation: Bool { latitude != 0 || longitude != 0 }
    var tz: TimeZone { TimeZone(identifier: timeZone) ?? .current }
}
