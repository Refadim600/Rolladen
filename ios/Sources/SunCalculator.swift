import Foundation

/// Sonnenauf-/-untergang nach NOAA-Näherung (Genauigkeit ca. ±1–2 min).
/// Dient nur der Vorschau in der App – ausgeführt wird auf dem Shelly mit dessen eigener Berechnung.
enum SunCalculator {
    static func sunTimes(on day: Date, latitude: Double, longitude: Double,
                         calendar: Calendar) -> (sunrise: Date, sunset: Date)? {
        guard let dayOfYear = calendar.ordinality(of: .day, in: .year, for: day) else { return nil }
        let g = 2 * Double.pi / 365 * Double(dayOfYear - 1)   // Bruchteil des Jahres (rad), Mittag
        let eqTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g)
                               - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        let decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g)
                 + 0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        let lat = latitude * .pi / 180
        let zenith = 90.833 * .pi / 180
        let cosHA = cos(zenith) / (cos(lat) * cos(decl)) - tan(lat) * tan(decl)
        guard (-1...1).contains(cosHA) else { return nil }        // Polartag/-nacht
        let ha = acos(cosHA) * 180 / .pi

        let sunriseMin = 720 - 4 * (longitude + ha) - eqTime     // Minuten nach 0:00 UTC
        let sunsetMin = 720 - 4 * (longitude - ha) - eqTime

        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        let comps = calendar.dateComponents([.year, .month, .day], from: day)
        guard let midnightUTC = utc.date(from: comps) else { return nil }
        return (midnightUTC.addingTimeInterval(sunriseMin * 60), midnightUTC.addingTimeInterval(sunsetMin * 60))
    }

    static func calendar(for settings: AppSettings) -> Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = settings.tz
        return cal
    }

    static func today(_ settings: AppSettings) -> (sunrise: Date, sunset: Date)? {
        guard settings.hasLocation else { return nil }
        return sunTimes(on: .now, latitude: settings.latitude, longitude: settings.longitude,
                        calendar: calendar(for: settings))
    }

    /// Nächster Ausführungszeitpunkt einer Regel (für die Anzeige)
    static func nextRun(of rule: ScheduleRule, settings: AppSettings, after now: Date = .now) -> Date? {
        guard rule.enabled, !rule.weekdays.isEmpty else { return nil }
        let cal = calendar(for: settings)
        let start = cal.startOfDay(for: now)
        for offset in 0...7 {
            guard let day = cal.date(byAdding: .day, value: offset, to: start) else { continue }
            let wd = cal.component(.weekday, from: day)
            guard rule.weekdays.contains(where: { $0.calendarWeekday == wd }) else { continue }

            let candidate: Date?
            switch rule.trigger {
            case .time:
                candidate = cal.date(bySettingHour: rule.hour, minute: rule.minute, second: 0, of: day)
            case .sunrise, .sunset:
                guard settings.hasLocation,
                      let t = sunTimes(on: day, latitude: settings.latitude, longitude: settings.longitude, calendar: cal)
                else { return nil }
                candidate = (rule.trigger == .sunrise ? t.sunrise : t.sunset)
                    .addingTimeInterval(Double(rule.offsetMinutes) * 60)
            }
            if let c = candidate, c > now { return c }
        }
        return nil
    }
}
