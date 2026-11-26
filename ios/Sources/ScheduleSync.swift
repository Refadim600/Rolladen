import Foundation

/// Übersetzt Regeln des Wochenplans in Shelly-Zeitpläne (Schedule-Jobs).
///
/// Die Zeitpläne werden AUF DEN GERÄTEN gespeichert und dort ausgeführt – iOS erlaubt Apps
/// keine zuverlässige Ausführung zu festen Uhrzeiten im Hintergrund. So fahren die Rollläden
/// auch, wenn iPhone/iPad ausgeschaltet oder außer Haus ist.
enum ScheduleBuilder {
    static let maxJobsPerDevice = 20

    /// Shelly-Timespec, z. B. "0 30 7 * * MON,TUE,WED,THU,FRI" oder "@sunset+15m * * SAT,SUN"
    static func timespec(for rule: ScheduleRule) -> String {
        let days = rule.weekdays.sorted().map(\.shellyCode).joined(separator: ",")
        switch rule.trigger {
        case .time: return "0 \(rule.minute) \(rule.hour) * * \(days)"
        case .sunrise: return "@sunrise\(offset(rule.offsetMinutes)) * * \(days)"
        case .sunset: return "@sunset\(offset(rule.offsetMinutes)) * * \(days)"
        }
    }

    /// "+1h30m", "-15m" oder "" (Shelly erlaubt max. ±12 h)
    static func offset(_ minutes: Int) -> String {
        guard minutes != 0 else { return "" }
        let h = abs(minutes) / 60, m = abs(minutes) % 60
        return (minutes > 0 ? "+" : "-") + (h > 0 ? "\(h)h" : "") + (m > 0 ? "\(m)m" : "")
    }

    static func call(for rule: ScheduleRule, coverID: Int) -> [String: Any] {
        switch rule.action {
        case .open: ["method": "Cover.Open", "params": ["id": coverID]]
        case .close: ["method": "Cover.Close", "params": ["id": coverID]]
        case .position: ["method": "Cover.GoToPosition", "params": ["id": coverID, "pos": rule.position]]
        }
    }

    static func rules(for shutter: Shutter, in rules: [ScheduleRule]) -> [ScheduleRule] {
        rules.filter { $0.enabled && !$0.weekdays.isEmpty && $0.shutterIDs.contains(shutter.id) }
    }
}

struct SyncResult: Identifiable {
    let id = UUID()
    let shutterName: String
    let jobCount: Int
    let error: String?
    var skipped = false
}

enum ScheduleSync {
    /// Überträgt den kompletten Wochenplan parallel auf alle Geräte.
    /// Achtung: Bestehende Zeitpläne auf den Geräten werden dabei ersetzt (Schedule.DeleteAll).
    static func run(shutters: [Shutter], rules: [ScheduleRule], settings: AppSettings) async -> [SyncResult] {
        await withTaskGroup(of: (Int, SyncResult).self) { group in
            for (index, shutter) in shutters.enumerated() {
                let jobs = ScheduleBuilder.rules(for: shutter, in: rules)
                group.addTask { (index, await syncOne(shutter, jobs: jobs, settings: settings)) }
            }
            var results: [(Int, SyncResult)] = []
            for await r in group { results.append(r) }
            return results.sorted { $0.0 < $1.0 }.map(\.1)
        }
    }

    private static func syncOne(_ shutter: Shutter, jobs: [ScheduleRule], settings: AppSettings) async -> SyncResult {
        guard shutter.hasHost else {
            return SyncResult(shutterName: shutter.name, jobCount: 0,
                              error: jobs.isEmpty ? nil : "Keine IP-Adresse eingetragen",
                              skipped: jobs.isEmpty)
        }
        guard jobs.count <= ScheduleBuilder.maxJobsPerDevice else {
            return SyncResult(shutterName: shutter.name, jobCount: jobs.count,
                              error: "Zu viele Regeln (max. \(ScheduleBuilder.maxJobsPerDevice) pro Gerät)")
        }
        let client = shutter.client
        do {
            if settings.pushLocationToDevices {
                try await client.setLocation(timeZone: settings.timeZone,
                                             latitude: settings.hasLocation ? settings.latitude : nil,
                                             longitude: settings.hasLocation ? settings.longitude : nil)
            }
            try await client.deleteAllSchedules()
            for rule in jobs {
                try await client.createSchedule(timespec: ScheduleBuilder.timespec(for: rule),
                                                calls: [ScheduleBuilder.call(for: rule, coverID: shutter.coverID)])
            }
            return SyncResult(shutterName: shutter.name, jobCount: jobs.count, error: nil)
        } catch {
            return SyncResult(shutterName: shutter.name, jobCount: jobs.count, error: error.localizedDescription)
        }
    }
}
