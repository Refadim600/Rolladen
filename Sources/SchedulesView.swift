import SwiftUI

struct SchedulesView: View {
    @Environment(AppStore.self) private var store
    @State private var path: [UUID] = []
    @State private var syncing = false
    @State private var results: [SyncResult] = []
    @State private var showResults = false

    var body: some View {
        NavigationStack(path: $path) {
            List {
                Section {
                    Button { Task { await sync() } } label: {
                        HStack {
                            if syncing { ProgressView() } else { Image(systemName: "arrow.up.circle.fill") }
                            Text("Wochenplan auf Shellys übertragen").bold()
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(syncing)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())

                    if store.needsSync {
                        Label("Änderungen sind noch nicht übertragen", systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(.orange)
                            .font(.footnote)
                    }
                } footer: {
                    Text("Die Zeitpläne werden auf den Geräten gespeichert und laufen dort – auch wenn iPhone/iPad aus oder außer Haus ist. Vorhandene Zeitpläne auf den Shellys werden dabei ersetzt.")
                }

                if !store.rules.isEmpty {
                    Section("Regeln") {
                        ForEach(store.rules) { rule in
                            NavigationLink(value: rule.id) { RuleRow(rule: rule, settings: store.settings) }
                        }
                        .onDelete { store.rules.remove(atOffsets: $0) }
                        .onMove { store.rules.move(fromOffsets: $0, toOffset: $1) }
                    }
                }
            }
            .overlay {
                if store.rules.isEmpty {
                    ContentUnavailableView("Noch keine Regeln", systemImage: "calendar.badge.plus",
                                           description: Text("Tippe auf +, um z. B. „Abends schließen“ anzulegen."))
                }
            }
            .navigationTitle("Wochenplan")
            .navigationDestination(for: UUID.self) { RuleEditView(ruleID: $0) }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { EditButton() }
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        var rule = ScheduleRule()
                        rule.shutterIDs = Set(store.shutters.map(\.id))
                        store.rules.append(rule)
                        path.append(rule.id)
                    } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $showResults) { SyncResultsView(results: results) }
        }
    }

    private func sync() async {
        syncing = true
        results = await ScheduleSync.run(shutters: store.shutters, rules: store.rules, settings: store.settings)
        syncing = false
        if results.allSatisfy({ $0.error == nil }) { store.needsSync = false }
        showResults = true
    }
}

struct RuleRow: View {
    let rule: ScheduleRule
    let settings: AppSettings

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: rule.trigger.isSunBased
                  ? (rule.trigger == .sunrise ? "sunrise.fill" : "sunset.fill")
                  : rule.action.icon)
                .font(.title2)
                .foregroundStyle(rule.enabled ? Color.orange : Color.gray)
                .frame(width: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(rule.name).font(.headline)
                Text("\(rule.actionText) · \(rule.triggerText)").font(.subheadline)
                Text("\(rule.weekdaysText) · \(rule.shutterIDs.count) Rollläden")
                    .font(.caption).foregroundStyle(.secondary)
                if !rule.enabled {
                    Text("Inaktiv").font(.caption.bold()).foregroundStyle(.secondary)
                } else if let next = SunCalculator.nextRun(of: rule, settings: settings) {
                    Text("Nächste Ausführung: \(next.formatted(.dateTime.weekday(.abbreviated).day().month().hour().minute()))")
                        .font(.caption).foregroundStyle(.tint)
                }
            }
        }
        .opacity(rule.enabled ? 1 : 0.6)
    }
}

struct SyncResultsView: View {
    let results: [SyncResult]
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List(results.filter { !$0.skipped }) { r in
                HStack {
                    Image(systemName: r.error == nil ? "checkmark.circle.fill" : "xmark.octagon.fill")
                        .foregroundStyle(r.error == nil ? .green : .red)
                    VStack(alignment: .leading) {
                        Text(r.shutterName)
                        Text(r.error ?? "\(r.jobCount) Zeitplan-Einträge gespeichert")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Übertragung")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { Button("Fertig") { dismiss() } }
        }
        .presentationDetents([.medium, .large])
    }
}
