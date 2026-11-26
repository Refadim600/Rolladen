import Foundation
import CryptoKit

struct ShellyError: LocalizedError {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}

struct CoverStatus {
    var state: String
    /// 0 = geschlossen, 100 = offen; nur vorhanden, wenn der Rollladen kalibriert ist
    var position: Int?

    var stateText: String {
        switch state {
        case "open": "offen"
        case "closed": "geschlossen"
        case "opening": "öffnet …"
        case "closing": "schließt …"
        case "stopped": "gestoppt"
        case "calibrating": "kalibriert …"
        default: state
        }
    }
    var isMoving: Bool { state == "opening" || state == "closing" }
}

/// Spricht die lokale RPC-Schnittstelle (Gen2+/Gen4) eines Shelly an: POST http://<host>/rpc
/// Unterstützt die optionale Geräte-Authentifizierung (HTTP Digest, SHA-256).
struct ShellyClient {
    let host: String
    let password: String

    private static let session: URLSession = {
        let cfg = URLSessionConfiguration.ephemeral
        cfg.timeoutIntervalForRequest = 5
        cfg.timeoutIntervalForResource = 10
        cfg.waitsForConnectivity = false
        return URLSession(configuration: cfg)
    }()

    // MARK: Rollladen-Befehle

    func open(id: Int = 0) async throws { _ = try await call("Cover.Open", ["id": id]) }
    func close(id: Int = 0) async throws { _ = try await call("Cover.Close", ["id": id]) }
    func stop(id: Int = 0) async throws { _ = try await call("Cover.Stop", ["id": id]) }
    func goTo(position: Int, id: Int = 0) async throws {
        _ = try await call("Cover.GoToPosition", ["id": id, "pos": max(0, min(100, position))])
    }

    func status(id: Int = 0) async throws -> CoverStatus {
        let r = try await call("Cover.GetStatus", ["id": id]) as? [String: Any] ?? [:]
        return CoverStatus(state: r["state"] as? String ?? "unbekannt",
                           position: r["current_pos"] as? Int)
    }

    // MARK: Zeitpläne & Konfiguration

    func deleteAllSchedules() async throws { _ = try await call("Schedule.DeleteAll") }

    func createSchedule(timespec: String, calls: [[String: Any]]) async throws {
        _ = try await call("Schedule.Create", ["enable": true, "timespec": timespec, "calls": calls])
    }

    func setLocation(timeZone: String, latitude: Double?, longitude: Double?) async throws {
        var location: [String: Any] = ["tz": timeZone]
        if let latitude, let longitude {
            location["lat"] = latitude
            location["lon"] = longitude
        }
        _ = try await call("Sys.SetConfig", ["config": ["location": location]])
    }

    // MARK: RPC-Kern

    @discardableResult
    func call(_ method: String, _ params: [String: Any] = [:]) async throws -> Any? {
        guard !host.isEmpty, let url = URL(string: "http://\(host)/rpc") else {
            throw ShellyError("Keine gültige Adresse eingetragen")
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "id": 1, "src": "rollladen-app", "method": method, "params": params,
        ])

        var (data, response) = try await send(request)

        if let http = response as? HTTPURLResponse, http.statusCode == 401 {
            guard !password.isEmpty else { throw ShellyError("Gerät ist passwortgeschützt – bitte Passwort eintragen") }
            guard let challenge = http.value(forHTTPHeaderField: "WWW-Authenticate") else {
                throw ShellyError("Authentifizierung fehlgeschlagen")
            }
            request.setValue(DigestAuth.header(challenge: challenge, user: "admin", password: password,
                                               method: "POST", uri: "/rpc"),
                             forHTTPHeaderField: "Authorization")
            (data, response) = try await send(request)
            if (response as? HTTPURLResponse)?.statusCode == 401 { throw ShellyError("Passwort falsch") }
        }

        let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        if let err = (json?["error"] as? [String: Any]) ?? (json?["code"] != nil ? json : nil) {
            let msg = (err["message"] as? String) ?? "Code \(String(describing: err["code"] ?? "?"))"
            throw ShellyError("Shelly meldet: \(msg)")
        }
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw ShellyError("HTTP-Fehler \((response as? HTTPURLResponse)?.statusCode ?? -1)")
        }
        return json?["result"]
    }

    private func send(_ request: URLRequest) async throws -> (Data, URLResponse) {
        do {
            return try await Self.session.data(for: request)
        } catch let error as URLError where error.code == .timedOut || error.code == .cannotConnectToHost
                                       || error.code == .cannotFindHost || error.code == .notConnectedToInternet {
            throw ShellyError("Nicht erreichbar (\(host))")
        }
    }
}

// MARK: - HTTP Digest (RFC 7616), wie von Shelly Gen2+ verwendet

enum DigestAuth {
    static func header(challenge: String, user: String, password: String, method: String, uri: String) -> String {
        let p = parse(challenge)
        let realm = p["realm"] ?? ""
        let nonce = p["nonce"] ?? ""
        let algorithm = p["algorithm"] ?? "MD5"
        let qop = p["qop"].map { $0.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) } }
        let useAuthQop = qop?.contains("auth") ?? false
        let cnonce = (0..<16).map { _ in String(format: "%x", Int.random(in: 0..<16)) }.joined()
        let nc = "00000001"

        let h: (String) -> String = algorithm.uppercased().hasPrefix("SHA-256") ? sha256 : md5
        let ha1 = h("\(user):\(realm):\(password)")
        let ha2 = h("\(method):\(uri)")
        let response = useAuthQop ? h("\(ha1):\(nonce):\(nc):\(cnonce):auth:\(ha2)") : h("\(ha1):\(nonce):\(ha2)")

        var parts = [
            "username=\"\(user)\"", "realm=\"\(realm)\"", "nonce=\"\(nonce)\"",
            "uri=\"\(uri)\"", "algorithm=\(algorithm)", "response=\"\(response)\"",
        ]
        if useAuthQop { parts += ["qop=auth", "nc=\(nc)", "cnonce=\"\(cnonce)\""] }
        if let opaque = p["opaque"] { parts.append("opaque=\"\(opaque)\"") }
        return "Digest " + parts.joined(separator: ", ")
    }

    static func parse(_ header: String) -> [String: String] {
        var s = header.trimmingCharacters(in: .whitespaces)
        if s.lowercased().hasPrefix("digest ") { s = String(s.dropFirst(7)) }
        let regex = try! NSRegularExpression(pattern: #"(\w+)=(?:"([^"]*)"|([^,\s]*))"#)
        var result: [String: String] = [:]
        for m in regex.matches(in: s, range: NSRange(s.startIndex..., in: s)) {
            guard let k = Range(m.range(at: 1), in: s) else { continue }
            let value = Range(m.range(at: 2), in: s) ?? Range(m.range(at: 3), in: s)
            result[s[k].lowercased()] = value.map { String(s[$0]) } ?? ""
        }
        return result
    }

    private static func sha256(_ s: String) -> String { hex(SHA256.hash(data: Data(s.utf8))) }
    private static func md5(_ s: String) -> String { hex(Insecure.MD5.hash(data: Data(s.utf8))) }
    private static func hex<D: Sequence>(_ d: D) -> String where D.Element == UInt8 {
        d.map { String(format: "%02x", $0) }.joined()
    }
}
