import SwiftUI

struct RuleEditView: View {
    @Environment(AppStore.self) private var store
    let ruleID: UUID

    var body: some View {
        @Bindable var store = store
        if let i = store.rules.firstIndex(where: { $0.id == ruleID }) {
            RuleForm(rule: $store.rules[i], shutters: store.shutters, settings: store.settings)
        } else {
            ContentUnavailableView("Regel gelöscht", systemImage: "trash")
        }
    }
}

private struct RuleForm: View {
    @Binding var rule: ScheduleRule
    let shutters: [Shutter]
    let settings: AppSettings

    var body: some View {
        Form {
            Section {
                TextField("Name", text: $rule.name)
                Toggle("Aktiv", isOn: $rule.enabled)
            }

            Section("Aktion") {
                Picker("Aktion", selection: $rule.action) {
                    ForEach(ShutterAction.allCases) { Text($0.label).tag($0) }
                }
                .pickerStyle(.segmented)
                if rule.action == .position {
                    VStack(alignment: .leading) {
                        Text("Zielposition: \(rule.position) % offen")
                        Slider(value: Binding(get: { Double(rule.position) },
                                              set: { rule.position = Int($0) }),
                               in: 0...100, step: 5)
                    }
                }
            }

            Section {
                Picker("Auslöser", selection: $rule.trigger) {
                    ForEach(TriggerKind.allCases) { Text($0.label).tag($0) }
                }
                .pickerStyle(.segmented)

                if rule.trigger == .time {
                    DatePicker("Uhrzeit", selection: timeBinding, displayedComponents: .hourAndMinute)
                } else {
                    Stepper(value: $rule.offsetMinutes, in: -180...180, step: 5) { Text(offsetText) }
                    if let preview = sunPreview {
                        LabeledContent("Heute wäre das", value: preview)
                    }
                }
            } header: { Text("Zeitpunkt") } footer: {
                if rule.trigger.isSunBased && !settings.hasLocation {
                    Text("Bitte unter Einstellungen den Standort festlegen, damit Sonnenauf- und -untergang berechnet werden können.")
                        .foregroundStyle(.orange)
                }
            }

            Section("Wochentage") {
                HStack(spacing: 6) {
                    ForEach(Weekday.allCases) { day in dayButton(day) }
                }
                HStack {
                    Button("Mo–Fr") { rule.weekdays = Weekday.workdays }
                    Spacer()
                    Button("Wochenende") { rule.weekdays = Weekday.weekend }
                    Spacer()
                    Button("Täglich") { rule.weekdays = Weekday.all }
                }
                .buttonStyle(.borderless)
            }

            Section {
                HStack {
                    Button("Alle auswählen") { rule.shutterIDs = Set(shutters.map(\.id)) }
                    Spacer()
                    Button("Keine") { rule.shutterIDs = [] }
                }
                .buttonStyle(.borderless)
                ForEach(shutters) { s in
                    Button {
                        if rule.shutterIDs.contains(s.id) { rule.shutterIDs.remove(s.id) }
                        else { rule.shutterIDs.insert(s.id) }
                    } label: {
                        HStack {
                            Text(s.name).foregroundStyle(.primary)
                            Spacer()
                            if rule.shutterIDs.contains(s.id) {
                                Image(systemName: "checkmark").foregroundStyle(.tint)
                            }
                        }
                    }
                }
            } header: { Text("Rollläden (\(rule.shutterIDs.count) ausgewählt)") }

            Section("Shelly-Zeitausdruck") {
                Text(ScheduleBuilder.timespec(for: rule))
                    .font(.footnote.monospaced())
                    .foregroundStyle(.secondary)
                    .textSelection(.enabled)
            }
        }
        .navigationTitle(rule.name)
        .navigationBarTitleDisplayMode(.inline)
    }

    private func dayButton(_ day: Weekday) -> some View {
        let on = rule.weekdays.contains(day)
        return Button {
            if on { rule.weekdays.remove(day) } else { rule.weekdays.insert(day) }
        } label: {
            Text(day.short)
                .font(.subheadline.bold())
                .frame(maxWidth: .infinity, minHeight: 38)
                .background(on ? Color.accentColor : Color(.tertiarySystemFill),
                            in: RoundedRectangle(cornerRadius: 8))
                .foregroundStyle(on ? Color.white : Color.primary)
        }
        .buttonStyle(.plain)
    }

    private var timeBinding: Binding<Date> {
        Binding(
            get: { Calendar.current.date(from: DateComponents(hour: rule.hour, minute: rule.minute)) ?? .now },
            set: {
                let c = Calendar.current.dateComponents([.hour, .minute], from: $0)
                rule.hour = c.hour ?? 0
                rule.minute = c.minute ?? 0
            })
    }

    private var offsetText: String {
        let event = rule.trigger.label
        switch rule.offsetMinutes {
        case 0: return "Genau bei \(event)"
        case let m where m > 0: return "\(ScheduleRule.durationText(m)) nach \(event)"
        default: return "\(ScheduleRule.durationText(-rule.offsetMinutes)) vor \(event)"
        }
    }

    private var sunPreview: String? {
        guard let t = SunCalculator.today(settings) else { return nil }
        let base = rule.trigger == .sunrise ? t.sunrise : t.sunset
        return base.addingTimeInterval(Double(rule.offsetMinutes) * 60)
            .formatted(date: .omitted, time: .shortened) + " Uhr"
    }
}
