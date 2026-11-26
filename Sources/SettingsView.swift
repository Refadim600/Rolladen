import SwiftUI

struct SettingsView: View {
    @Environment(AppStore.self) private var store
    @State private var locator = LocationProvider()
    @State private var locating = false
    @State private var locationError: String?

    var body: some View {
        @Bindable var store = store
        NavigationStack {
            Form {
                Section {
                    LabeledContent("Breitengrad") {
                        TextField("z. B. 50,94", value: $store.settings.latitude,
                                  format: .number.precision(.fractionLength(0...5)))
                            .multilineTextAlignment(.trailing)
                            .keyboardType(.numbersAndPunctuation)
                    }
                    LabeledContent("Längengrad") {
                        TextField("z. B. 6,96", value: $store.settings.longitude,
                                  format: .number.precision(.fractionLength(0...5)))
                            .multilineTextAlignment(.trailing)
                            .keyboardType(.numbersAndPunctuation)
                    }
                    Button { Task { await locate() } } label: {
                        HStack {
                            if locating { ProgressView() }
                            Label("Aktuellen Standort übernehmen", systemImage: "location.fill")
                        }
                    }
                    .disabled(locating)
                    if let locationError {
                        Text(locationError).font(.footnote).foregroundStyle(.red)
                    }
                } header: { Text("Standort für Sonnenauf-/-untergang") } footer: {
                    if let t = SunCalculator.today(store.settings) {
                        Text("Heute: Sonnenaufgang \(t.sunrise.formatted(date: .omitted, time: .shortened)), Sonnenuntergang \(t.sunset.formatted(date: .omitted, time: .shortened)) Uhr")
                    }
                }

                Section {
                    LabeledContent("Zeitzone", value: store.settings.timeZone)
                    Button("Zeitzone dieses Geräts übernehmen") {
                        store.settings.timeZone = TimeZone.current.identifier
                    }
                    Toggle("Standort & Zeitzone an Shellys senden", isOn: $store.settings.pushLocationToDevices)
                } footer: {
                    Text("Die Shellys berechnen Sonnenauf- und -untergang selbst. Dafür brauchen sie Standort und Zeitzone – beides wird beim Übertragen des Wochenplans gesetzt.")
                }

                Section("So funktioniert’s") {
                    Text("• Die App steuert die Shellys direkt im WLAN über deren lokale RPC-Schnittstelle – ohne Cloud.\n• Der Wochenplan wird als Zeitplan auf jedem Shelly gespeichert und dort ausgeführt.\n• Von unterwegs funktioniert die Steuerung nur per VPN ins Heimnetz (z. B. FRITZ!Box-VPN).")
                        .font(.footnote)
                }
            }
            .navigationTitle("Einstellungen")
        }
    }

    private func locate() async {
        locating = true
        locationError = nil
        defer { locating = false }
        do {
            let loc = try await locator.currentLocation()
            store.settings.latitude = (loc.coordinate.latitude * 10_000).rounded() / 10_000
            store.settings.longitude = (loc.coordinate.longitude * 10_000).rounded() / 10_000
            store.settings.timeZone = TimeZone.current.identifier
        } catch {
            locationError = error.localizedDescription
        }
    }
}
