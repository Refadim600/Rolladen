import Foundation
import Observation

/// Zentrale Datenhaltung. Wird bei jeder Änderung als JSON gespeichert.
@Observable
final class AppStore {
    var shutters: [Shutter] { didSet { changed() } }
    var rules: [ScheduleRule] { didSet { changed() } }
    var settings: AppSettings { didSet { changed() } }
    /// true, solange Änderungen am Wochenplan noch nicht auf die Geräte übertragen wurden
    var needsSync: Bool { didSet { save() } }

    private struct Snapshot: Codable {
        var shutters: [Shutter]
        var rules: [ScheduleRule]
        var settings: AppSettings
        var needsSync: Bool
    }

    private static var fileURL: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("rollladen.json")
    }

    init() {
        if let data = try? Data(contentsOf: Self.fileURL),
           let snap = try? JSONDecoder().decode(Snapshot.self, from: data) {
            shutters = snap.shutters
            rules = snap.rules
            settings = snap.settings
            needsSync = snap.needsSync
        } else {
            // Erststart: 12 leere Rollläden anlegen
            shutters = (1...12).map { Shutter(name: "Rollladen \($0)") }
            rules = []
            settings = AppSettings()
            needsSync = false
        }
    }

    private func changed() {
        needsSync = true   // löst save() aus
    }

    private func save() {
        let snap = Snapshot(shutters: shutters, rules: rules, settings: settings, needsSync: needsSync)
        guard let data = try? JSONEncoder().encode(snap) else { return }
        try? data.write(to: Self.fileURL, options: [.atomic, .completeFileProtection])
    }

    // MARK: Hilfsfunktionen

    func deleteShutters(at offsets: IndexSet) {
        let ids = offsets.map { shutters[$0].id }
        shutters.remove(atOffsets: offsets)
        for i in rules.indices { rules[i].shutterIDs.subtract(ids) }
        ids.forEach { Keychain.setPassword("", for: $0) }
    }

    func shutterName(_ id: UUID) -> String {
        shutters.first { $0.id == id }?.name ?? "?"
    }
}
