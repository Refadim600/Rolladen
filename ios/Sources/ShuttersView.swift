import SwiftUI

struct ShuttersView: View {
    @Environment(AppStore.self) private var store
    @State private var status: [UUID: Result<CoverStatus, Error>] = [:]
    @State private var groupBusy = false
    @State private var banner: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                OpenAllButton(busy: groupBusy) {
                    await runOnAll("Alle öffnen") { try await $0.open(id: $1) }
                }
                .padding([.horizontal, .top])

                HStack(spacing: 12) {
                    Button { Task { await runOnAll("Alle stoppen") { try await $0.stop(id: $1) } } } label: {
                        Label("Alle stoppen", systemImage: "stop.fill").frame(maxWidth: .infinity)
                    }
                    Button { Task { await runOnAll("Alle schließen") { try await $0.close(id: $1) } } } label: {
                        Label("Alle schließen", systemImage: "arrow.down.to.line").frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(.bordered)
                .controlSize(.large)
                .disabled(groupBusy)
                .padding()

                if let banner {
                    Text(banner)
                        .font(.footnote)
                        .padding(10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(.yellow.opacity(0.2), in: RoundedRectangle(cornerRadius: 10))
                        .padding(.horizontal)
                        .onTapGesture { self.banner = nil }
                }

                List {
                    ForEach(store.shutters) { shutter in
                        NavigationLink(value: shutter.id) {
                            ShutterRow(shutter: shutter, status: status[shutter.id]) {
                                await refresh(shutter)
                            }
                        }
                    }
                    .onDelete { store.deleteShutters(at: $0) }
                    .onMove { store.shutters.move(fromOffsets: $0, toOffset: $1) }
                }
                .listStyle(.insetGrouped)
                .refreshable { await refreshAll() }
            }
            .navigationTitle("Rollläden")
            .navigationDestination(for: UUID.self) { ShutterEditView(shutterID: $0) }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { EditButton() }
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        store.shutters.append(Shutter(name: "Rollladen \(store.shutters.count + 1)"))
                    } label: { Image(systemName: "plus") }
                }
            }
            .task { await refreshAll() }
        }
    }

    // MARK: Gruppenbefehle

    private func runOnAll(_ label: String, _ op: @escaping (ShellyClient, Int) async throws -> Void) async {
        let targets = store.shutters.filter(\.hasHost)
        guard !targets.isEmpty else { banner = "Noch keine IP-Adressen eingetragen."; return }
        groupBusy = true
        let failures = await withTaskGroup(of: String?.self) { group in
            for s in targets {
                let client = s.client, coverID = s.coverID, name = s.name
                group.addTask {
                    do { try await op(client, coverID); return nil }
                    catch { return "\(name): \(error.localizedDescription)" }
                }
            }
            var list: [String] = []
            for await f in group { if let f { list.append(f) } }
            return list.sorted()
        }
        groupBusy = false
        banner = failures.isEmpty
            ? "\(label): \(targets.count) Rollläden angesteuert."
            : "\(label): \(failures.count) von \(targets.count) fehlgeschlagen\n" + failures.joined(separator: "\n")
        try? await Task.sleep(for: .seconds(1))
        await refreshAll()
    }

    private func refreshAll() async {
        await withTaskGroup(of: Void.self) { group in
            for s in store.shutters where s.hasHost {
                group.addTask { await refresh(s) }
            }
        }
    }

    @MainActor
    private func refresh(_ shutter: Shutter) async {
        do { status[shutter.id] = .success(try await shutter.client.status(id: shutter.coverID)) }
        catch { status[shutter.id] = .failure(error) }
    }
}

// MARK: - Hervorgehobene Schaltfläche „Alle öffnen“

struct OpenAllButton: View {
    let busy: Bool
    let action: () async -> Void

    var body: some View {
        Button { Task { await action() } } label: {
            HStack(spacing: 14) {
                if busy { ProgressView().tint(.white) }
                else { Image(systemName: "arrow.up.to.line.compact").font(.title.bold()) }
                Text("ALLE ÖFFNEN").font(.title2.weight(.heavy))
            }
            .frame(maxWidth: .infinity, minHeight: 72)
        }
        .buttonStyle(.borderedProminent)
        .tint(.green)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .shadow(color: .green.opacity(0.4), radius: 8, y: 4)
        .disabled(busy)
        .accessibilityHint("Öffnet sofort alle Rollläden")
    }
}

// MARK: - Zeile mit Einzelsteuerung

struct ShutterRow: View {
    let shutter: Shutter
    let status: Result<CoverStatus, Error>?
    let refresh: () async -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(shutter.name).font(.headline)
                Text(subtitle).font(.caption).foregroundStyle(subtitleColor)
            }
            Spacer()
            if shutter.hasHost {
                control("chevron.up") { try await $0.open(id: shutter.coverID) }
                control("stop.fill") { try await $0.stop(id: shutter.coverID) }
                control("chevron.down") { try await $0.close(id: shutter.coverID) }
            }
        }
        .padding(.vertical, 4)
    }

    private func control(_ icon: String, _ op: @escaping (ShellyClient) async throws -> Void) -> some View {
        Button {
            Task {
                try? await op(shutter.client)
                try? await Task.sleep(for: .milliseconds(800))
                await refresh()
            }
        } label: {
            Image(systemName: icon).font(.title3).frame(width: 40, height: 36)
        }
        .buttonStyle(.bordered)
    }

    private var subtitle: String {
        guard shutter.hasHost else { return "Keine IP-Adresse – tippen zum Einrichten" }
        switch status {
        case .none: return "…"
        case .failure(let e): return e.localizedDescription
        case .success(let s):
            if let p = s.position { return "\(s.stateText) · \(p) % offen" }
            return s.stateText
        }
    }

    private var subtitleColor: Color {
        if case .failure = status { return .red }
        return .secondary
    }
}

// MARK: - Rollladen bearbeiten

struct ShutterEditView: View {
    @Environment(AppStore.self) private var store
    let shutterID: UUID
    @State private var password = ""
    @State private var testResult: String?
    @State private var targetPosition = 50.0

    var body: some View {
        @Bindable var store = store
        if let i = store.shutters.firstIndex(where: { $0.id == shutterID }) {
            let shutter = store.shutters[i]
            Form {
                Section("Bezeichnung") {
                    TextField("Name", text: $store.shutters[i].name)
                }
                Section {
                    TextField("IP-Adresse oder Hostname", text: $store.shutters[i].host)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    SecureField("Passwort (falls gesetzt)", text: $password)
                        .onChange(of: password) { _, new in Keychain.setPassword(new, for: shutterID) }
                    Stepper("Cover-ID: \(shutter.coverID)", value: $store.shutters[i].coverID, in: 0...1)
                } header: { Text("Shelly 2PM Gen4") } footer: {
                    Text("Tipp: Im Router eine feste IP für jeden Shelly vergeben. Der Shelly muss im Cover-Modus betrieben und kalibriert sein. Benutzername ist immer „admin“.")
                }
                Section {
                    Button("Verbindung testen") { Task { await test(shutter) } }
                    if let testResult { Text(testResult).font(.footnote) }
                }
                Section("Position anfahren") {
                    Slider(value: $targetPosition, in: 0...100, step: 5) { Text("Position") }
                    Button("Auf \(Int(targetPosition)) % fahren") {
                        Task { try? await shutter.client.goTo(position: Int(targetPosition), id: shutter.coverID) }
                    }
                }
            }
            .navigationTitle(shutter.name)
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { password = Keychain.password(for: shutterID) }
        } else {
            ContentUnavailableView("Rollladen gelöscht", systemImage: "trash")
        }
    }

    private func test(_ shutter: Shutter) async {
        testResult = "Verbinde …"
        do {
            let info = try await shutter.client.call("Shelly.GetDeviceInfo") as? [String: Any] ?? [:]
            let status = try await shutter.client.status(id: shutter.coverID)
            testResult = "✓ \(info["model"] ?? "Shelly") · Firmware \(info["ver"] ?? "?") · \(status.stateText)"
        } catch {
            testResult = "✗ \(error.localizedDescription)"
        }
    }
}
