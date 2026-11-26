import SwiftUI

@main
struct RollladenApp: App {
    @State private var store = AppStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(store)
        }
    }
}

struct ContentView: View {
    var body: some View {
        TabView {
            ShuttersView()
                .tabItem { Label("Rollläden", systemImage: "blinds.horizontal.closed") }
            SchedulesView()
                .tabItem { Label("Wochenplan", systemImage: "calendar.badge.clock") }
            SettingsView()
                .tabItem { Label("Einstellungen", systemImage: "gearshape") }
        }
    }
}
