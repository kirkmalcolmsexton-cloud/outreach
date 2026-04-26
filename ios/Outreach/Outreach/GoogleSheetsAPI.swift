import Foundation

protocol GoogleAccessTokenProvider: Sendable {
    func getAccessToken(_ scopes: [String]) async -> String?
}

struct GoogleApiEndpoints: Sendable {
    var driveBaseUrl: String = "https://www.googleapis.com"
    var sheetsBaseUrl: String = "https://sheets.googleapis.com"
}

enum SheetsRequestResult: Sendable {
    case success([String: Any]?)
    case failure(code: Int?, message: String)
}

private extension SheetsRequestResult {
    func bodyOrThrow() throws -> [String: Any]? {
        switch self {
        case .success(let b): return b
        case .failure(let code, let m):
            throw NSError(
                domain: "Sheets",
                code: code ?? -1,
                userInfo: [NSLocalizedDescriptionKey: m]
            )
        }
    }
}

func parseSheetRowFromUpdatedRange(_ updatedRange: String) -> Int? {
    let range = updatedRange
    let bang = range.lastIndex(of: "!")
    let afterBang: Substring
    if let b = bang {
        afterBang = range[range.index(after: b)...]
    } else {
        afterBang = Substring(range)
    }
    let s = afterBang.trimmingCharacters(in: .whitespacesAndNewlines)
    guard let r = s.range(of: "^([A-Za-z]+)(\\d+)", options: .regularExpression) else { return nil }
    let m = s[r]
    let num = m.filter { $0.isNumber }
    return Int(num)
}

func sortEpochForLastVisited(_ lastVisited: String?, now: Date = Date()) -> Int {
    if let s = lastVisited, let d = parseIsoDateOrNull(s) {
        return javaEpochDay(from: d)
    }
    return javaEpochDay(from: now) - 100_000
}

private func javaEpochDay(from date: Date) -> Int {
    let cal = Calendar(identifier: .gregorian)
    let t = cal.startOfDay(for: date)
    let ref = cal.date(from: DateComponents(year: 1970, month: 1, day: 1))!
    return cal.dateComponents([.day], from: cal.startOfDay(for: ref), to: t).day ?? 0
}

protocol SheetsApi: Sendable {
    func listTabs(spreadsheetId: String) async throws -> [String]
    func getSpreadsheetTitle(spreadsheetId: String) async throws -> String?
    func fetchRows(spreadsheetId: String, tabName: String) async throws -> [SpreadsheetRowInput]
    func updateVisit(spreadsheetId: String, update: VisitUpdate) async throws
    func appendHouseholdRow(
        spreadsheetId: String,
        tabName: String,
        name: String,
        streetAddress: String,
        neighborhood: String
    ) async throws -> AppendHouseholdResult?
    func validateRequiredHeaders(spreadsheetId: String, tabName: String) async throws -> Bool
    func fetchBriefCommentPresets(spreadsheetId: String) async throws -> [String]
    func resolveSpreadsheetIdFromDriveMetadata(displayName: String, lastModifiedMillis: Int64?) async throws -> String?
}

struct StubSheetsApi: SheetsApi {
    func listTabs(spreadsheetId: String) async throws -> [String] { ["60618", "60657"] }
    func getSpreadsheetTitle(spreadsheetId: String) async throws -> String? { "Stub spreadsheet" }
    func fetchRows(spreadsheetId: String, tabName: String) async throws -> [SpreadsheetRowInput] { [] }
    func updateVisit(spreadsheetId: String, update: VisitUpdate) async throws { }
    func appendHouseholdRow(
        spreadsheetId: String, tabName: String, name: String, streetAddress: String, neighborhood: String
    ) async throws -> AppendHouseholdResult? { .init(rowNumber: 2) }
    func validateRequiredHeaders(spreadsheetId: String, tabName: String) async throws -> Bool { true }
    func fetchBriefCommentPresets(spreadsheetId: String) async throws -> [String] {
        ["Not home", "Left message", "Receptive", "Do not visit", "Moved", "Dawat saath", "Other"]
    }
    func resolveSpreadsheetIdFromDriveMetadata(displayName: String, lastModifiedMillis: Int64?) async throws -> String? { nil }
}

final class GoogleSheetsApi: SheetsApi, @unchecked Sendable {
    static let keysTabName = "keys"
    private let tokenProvider: GoogleAccessTokenProvider
    private let endpoints: GoogleApiEndpoints
    private let urlSession: URLSession

    init(
        tokenProvider: GoogleAccessTokenProvider,
        endpoints: GoogleApiEndpoints = .init(),
        urlSession: URLSession = .shared
    ) {
        self.tokenProvider = tokenProvider
        self.endpoints = endpoints
        self.urlSession = urlSession
    }

    // MARK: - Public API (mirrors Android GoogleSheetsApi)

    func resolveSpreadsheetIdFromDriveMetadata(displayName: String, lastModifiedMillis: Int64?) async throws -> String? {
        let safeName = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "'", with: "\\'")
        guard !safeName.isEmpty else { return nil }
        let q = "name='\(safeName)' and mimeType='application/vnd.google-apps.spreadsheet' and trashed=false"
        var comp = URLComponents(string: "\(endpoints.driveBaseUrl)/drive/v3/files")!
        comp.queryItems = [
            URLQueryItem(name: "q", value: q),
            URLQueryItem(name: "fields", value: "files(id,name,modifiedTime)"),
            URLQueryItem(name: "orderBy", value: "modifiedTime desc"),
            URLQueryItem(name: "pageSize", value: "50"),
            URLQueryItem(name: "includeItemsFromAllDrives", value: "true"),
            URLQueryItem(name: "supportsAllDrives", value: "true")
        ]
        guard let path = comp.url?.absoluteString else { return nil }
        let res = try await request(method: "GET", path: path)
        guard let o = try? res.bodyOrThrow() else { return nil }
        var files: [[String: Any]] = (o["files"] as? [[String: Any]]) ?? []
        if files.isEmpty {
            let fbq = "mimeType='application/vnd.google-apps.spreadsheet' and trashed=false"
            var c2 = URLComponents(string: "\(endpoints.driveBaseUrl)/drive/v3/files")!
            c2.queryItems = [
                URLQueryItem(name: "q", value: fbq),
                URLQueryItem(name: "fields", value: "files(id,name,modifiedTime)"),
                URLQueryItem(name: "orderBy", value: "modifiedTime desc"),
                URLQueryItem(name: "pageSize", value: "200"),
                URLQueryItem(name: "includeItemsFromAllDrives", value: "true"),
                URLQueryItem(name: "supportsAllDrives", value: "true")
            ]
            if let p2 = c2.url?.absoluteString, let o2 = try? (await self.request(method: "GET", path: p2)).bodyOrThrow() {
                files = (o2["files"] as? [[String: Any]]) ?? []
            }
        }
        return pickBestSpreadsheetId(files, displayName: displayName, lastModifiedMillis: lastModifiedMillis)
    }

    private func pickBestSpreadsheetId(
        _ files: [[String: Any]],
        displayName: String,
        lastModifiedMillis: Int64?
    ) -> String? {
        let normalizedTarget = displayName.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        var bestId: String?
        var bestScore = Int.min
        var bestDiff = Int64.max
        for f in files {
            guard let id = f["id"] as? String, !id.isEmpty else { continue }
            let name = (f["name"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let nn = name.lowercased()
            let score: Int
            if nn == normalizedTarget { score = 3 }
            else if nn.contains(normalizedTarget) { score = 2 }
            else if normalizedTarget.contains(nn) { score = 1 }
            else { score = 0 }
            if score == 0 { continue }
            let modified = (f["modifiedTime"] as? String) ?? ""
            var diff: Int64 = .max
            if !modified.isEmpty, let lmm = lastModifiedMillis,
               let p = ISO8601DateFormatter().date(from: modified) {
                let mm = Int64(p.timeIntervalSince1970 * 1000)
                diff = abs(mm - lmm)
            }
            if score > bestScore || (score == bestScore && diff < bestDiff) {
                bestScore = score
                bestDiff = diff
                bestId = id
            }
        }
        if let bestId { return bestId }
        if let first = files.first, let id = first["id"] as? String, !id.isEmpty { return id }
        return nil
    }

    func getSpreadsheetTitle(spreadsheetId: String) async throws -> String? {
        let p = "\(endpoints.sheetsBaseUrl)/v4/spreadsheets/\(spreadsheetId)?fields=properties.title"
        guard let o = try await request(method: "GET", path: p).bodyOrThrow() else { return nil }
        return (o["properties"] as? [String: Any])?["title"] as? String
    }

    func listTabs(spreadsheetId: String) async throws -> [String] {
        let p = "\(endpoints.sheetsBaseUrl)/v4/spreadsheets/\(spreadsheetId)?fields=sheets.properties.title"
        guard let o = try await request(method: "GET", path: p).bodyOrThrow() else { return [] }
        let sheets = o["sheets"] as? [[String: Any]] ?? []
        return sheets.compactMap { s in
            ((s["properties"] as? [String: Any])?["title"] as? String)?
                .trimmingCharacters(in: .whitespacesAndNewlines)
        }.filter { !$0.isEmpty }
    }

    func fetchRows(spreadsheetId: String, tabName: String) async throws -> [SpreadsheetRowInput] {
        let r = "\(tabName)!A:Z"
        let enc = r.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? r
        let p = "\(endpoints.sheetsBaseUrl)/v4/spreadsheets/\(spreadsheetId)/values/\(enc)"
        guard let o = try await request(method: "GET", path: p).bodyOrThrow() else { return [] }
        let values = o["values"] as? [[Any]] ?? []
        guard values.count > 1 else { return [] }
        let headerRow = values[0]
        let headers = headerRow.map { String(describing: $0) }
        let index = indexHeaders(headers)

        func at(_ row: [Any], _ key: String) -> String? {
            guard let idx = index[key], idx < row.count else { return nil }
            return stringify(row[idx])
        }

        return (1..<values.count).compactMap { i in
            let row = values[i]
            return SpreadsheetRowInput(
                briefComments: at(row, "brief comments"),
                lastVisited: at(row, "last visited"),
                name: at(row, "name"),
                streetAddress: at(row, "street address"),
                neighborhood: at(row, "neighborhood"),
                notes: at(row, "notes")
            )
        }
    }

    func updateVisit(spreadsheetId: String, update: VisitUpdate) async throws {
        guard let sheetName = update.sheetName, let rowNumber = update.rowNumber, rowNumber >= 1 else { return }
        let headerMap = try await headerIndex(spreadsheetId: spreadsheetId, tabName: sheetName)
        guard let briefCol = headerMap["brief comments"],
              let lastVisitedCol = headerMap["last visited"],
              let notesCol = headerMap["notes"] else { return }
        let data: [[String: Any]] = [
            batchDataItem(sheet: sheetName, row: rowNumber, column: briefCol, value: update.briefComment),
            batchDataItem(sheet: sheetName, row: rowNumber, column: lastVisitedCol, value: update.lastVisitedIsoDate),
            batchDataItem(sheet: sheetName, row: rowNumber, column: notesCol, value: update.notes ?? "")
        ]
        let body: [String: Any] = [
            "valueInputOption": "USER_ENTERED",
            "data": data
        ]
        let path = "\(endpoints.sheetsBaseUrl)/v4/spreadsheets/\(spreadsheetId)/values:batchUpdate"
        _ = try await request(method: "POST", path: path, jsonBody: body).bodyOrThrow()
    }

    private func batchDataItem(sheet: String, row: Int, column: String, value: String) -> [String: Any] {
        [
            "range": "\(sheet)!\(column)\(row)",
            "values": [[value]] as [Any]
        ]
    }

    func appendHouseholdRow(
        spreadsheetId: String,
        tabName: String,
        name: String,
        streetAddress: String,
        neighborhood: String
    ) async throws -> AppendHouseholdResult? {
        let headers = try await fetchHeaderRowCells(spreadsheetId: spreadsheetId, tabName: tabName)
        guard !headers.isEmpty else { return nil }
        var row = [String](repeating: "", count: headers.count)
        for (idx, header) in headers.enumerated() {
            switch canonicalHeaderName(header) {
            case "name": row[idx] = name
            case "street address": row[idx] = streetAddress
            case "neighborhood": row[idx] = neighborhood
            case "brief comments", "last visited", "notes", .none: break
            default: break
            }
        }
        let r = "\(tabName)!A:Z"
        let enc = r.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? r
        let p =
            "\(endpoints.sheetsBaseUrl)/v4/spreadsheets/\(spreadsheetId)/values/\(enc):append" +
            "?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS"
        let body: [String: Any] = ["values": [row]]
        guard let o = try await request(method: "POST", path: p, jsonBody: body).bodyOrThrow() else { return nil }
        let updates = o["updates"] as? [String: Any] ?? [:]
        let range = (updates["updatedRange"] as? String) ?? ""
        if range.isEmpty { return nil }
        guard let rowNum = parseSheetRowFromUpdatedRange(range) else { return nil }
        return AppendHouseholdResult(rowNumber: rowNum)
    }

    func fetchBriefCommentPresets(spreadsheetId: String) async throws -> [String] {
        let r = "\(Self.keysTabName)!A:Z"
        let enc = r.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? r
        let p = "\(endpoints.sheetsBaseUrl)/v4/spreadsheets/\(spreadsheetId)/values/\(enc)"
        guard let o = try await request(method: "GET", path: p).bodyOrThrow() else { return [] }
        let values = o["values"] as? [[Any]] ?? []
        guard !values.isEmpty else { return [] }
        let headerRow = values[0].map { String(describing: $0) }
        let cIdx = headerRow.enumerated().compactMap { i, s -> (Int, String)? in
            guard let c = canonicalHeaderName(s), c == "name" else { return nil }
            return (i, c)
        }
        guard let nameIdx = cIdx.first?.0 else { return [] }
        var ordered: [String] = []
        var seen = Set<String>()
        for i in 1..<values.count {
            let row = values[i]
            guard nameIdx < row.count else { continue }
            let cell = String(describing: row[nameIdx]).trimmingCharacters(in: .whitespacesAndNewlines)
            if !cell.isEmpty, !seen.contains(cell) {
                seen.insert(cell)
                ordered.append(cell)
            }
        }
        return ordered
    }

    func validateRequiredHeaders(spreadsheetId: String, tabName: String) async throws -> Bool {
        let r = "\(tabName)!1:1"
        let enc = r.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? r
        let p = "\(endpoints.sheetsBaseUrl)/v4/spreadsheets/\(spreadsheetId)/values/\(enc)"
        guard let o = try await request(method: "GET", path: p).bodyOrThrow() else { return false }
        let values = o["values"] as? [[Any]] ?? []
        guard let first = values.first else { return false }
        let headers = first.compactMap { canonicalHeaderName(String(describing: $0)) }
        let set = Set(headers)
        let required = Set(["brief comments", "last visited", "name", "street address", "neighborhood", "notes"])
        return required.isSubset(of: set)
    }

    // MARK: - Internals

    private func fetchHeaderRowCells(spreadsheetId: String, tabName: String) async throws -> [String] {
        let r = "\(tabName)!1:1"
        let enc = r.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? r
        let p = "\(endpoints.sheetsBaseUrl)/v4/spreadsheets/\(spreadsheetId)/values/\(enc)"
        guard let o = try await request(method: "GET", path: p).bodyOrThrow() else { return [] }
        let values = o["values"] as? [[Any]] ?? []
        return values.first?.map { String(describing: $0) } ?? []
    }

    private func headerIndex(spreadsheetId: String, tabName: String) async throws -> [String: String] {
        let headers = try await fetchHeaderRowCells(spreadsheetId: spreadsheetId, tabName: tabName)
        let pairs: [(String, String)] = headers.enumerated().compactMap { i, h -> (String, String)? in
            guard let c = canonicalHeaderName(h) else { return nil }
            return (c, columnName(index1Based: i + 1))
        }
        return pairs.reduce(into: [String: String]()) { dict, pair in
            dict[pair.0] = pair.1
        }
    }

    private func indexHeaders(_ headers: [String]) -> [String: Int] {
        var m = [String: Int]()
        for (i, h) in headers.enumerated() {
            if let c = canonicalHeaderName(h) { m[c] = i }
        }
        return m
    }

    private func columnName(index1Based: Int) -> String {
        var i = index1Based
        var name = ""
        while i > 0 {
            let rem = (i - 1) % 26
            name = String(UnicodeScalar(65 + rem)!) + name
            i = (i - 1) / 26
        }
        return name
    }

    private func canonicalHeaderName(_ raw: String) -> String? {
        let normalized = raw
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
        switch normalized {
        case "brief comments", "brief comment": return "brief comments"
        case "last visited", "last visit": return "last visited"
        case "name": return "name"
        case "street address": return "street address"
        case "neighborhood": return "neighborhood"
        case "notes": return "notes"
        default: return nil
        }
    }

    private func request(method: String, path: String, jsonBody: [String: Any]? = nil) async throws -> SheetsRequestResult {
        let driveScopes: [String] = [
            "https://www.googleapis.com/auth/drive.metadata.readonly",
            "https://www.googleapis.com/auth/drive.file"
        ]
        let sheetScopes: [String] = [
            "https://www.googleapis.com/auth/spreadsheets",
            "https://www.googleapis.com/auth/drive.file"
        ]
        let useScopes = path.contains("/drive/v3/files") ? driveScopes : sheetScopes
        guard let token = await tokenProvider.getAccessToken(useScopes) else {
            return .failure(code: nil, message: "Missing Google access token")
        }
        guard let url = URL(string: path) else {
            return .failure(code: nil, message: "Invalid URL")
        }
        var req = URLRequest(url: url)
        req.httpMethod = method
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let jsonBody {
            req.httpBody = try JSONSerialization.data(withJSONObject: jsonBody, options: [])
        }
        let (data, response) = try await urlSession.data(for: req)
        let code = (response as? HTTPURLResponse)?.statusCode ?? -1
        if !(200..<300).contains(code) {
            return .failure(code: code, message: "HTTP \(code) for \(String(path.prefix(120)))")
        }
        if data.isEmpty { return .success(nil) }
        let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        return .success(obj)
    }

    private func stringify(_ any: Any) -> String? {
        if any is NSNull { return nil }
        let s = String(describing: any)
        if s == "nil" || s.isEmpty { return nil }
        return s
    }
}
