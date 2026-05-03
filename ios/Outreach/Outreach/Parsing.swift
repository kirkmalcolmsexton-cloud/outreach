import Foundation

private let fullDateRegex = try! NSRegularExpression(
    pattern: "^(\\d{1,2})/(\\d{1,2})/(\\d{2}|\\d{4})$",
    options: []
)

private let normalizationRules: [(NSRegularExpression, VisitOutcome)] = {
    let pairs: [(String, VisitOutcome)] = [
        ("^not\\s*home$", .notHome),
        ("^left\\s*mess?a?g?e?$", .leftMessage),
        ("^receptive$", .receptive),
        ("^do\\s*not\\s*visit$", .doNotVisit),
        ("^moved$", .moved),
        ("^dawat\\s*saath$", .dawatSaath)
    ]
    return pairs.compactMap { pattern, v in
        (try? NSRegularExpression(pattern: pattern, options: .caseInsensitive)).map { ($0, v) }
    }
}()

func normalizeText(_ value: String?) -> String {
    value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
}

func normalizeOptionalText(_ value: String?) -> String? {
    let t = normalizeText(value)
    return t.isEmpty ? nil : t
}

/// Extracts a Google Sheets spreadsheet id from a full URL or returns a plausible raw id token.
func extractSpreadsheetIdFromText(_ text: String) -> String? {
    let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.isEmpty { return nil }
    let pattern = "/spreadsheets/d/([a-zA-Z0-9-_]+)"
    if let re = try? NSRegularExpression(pattern: pattern, options: []),
       let m = re.firstMatch(in: trimmed, range: NSRange(trimmed.startIndex..., in: trimmed)),
       let r = Range(m.range(at: 1), in: trimmed) {
        return String(trimmed[r])
    }
    if trimmed.range(of: "^[a-zA-Z0-9-_]{20,}$", options: .regularExpression) != nil {
        return trimmed
    }
    return nil
}

/// Standard “open in browser” link for a spreadsheet id (use for Settings field so a full URL is shown after relaunch).
func canonicalGoogleSheetsEditURL(forSpreadsheetId id: String) -> String {
    let t = id.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !t.isEmpty else { return "" }
    return "https://docs.google.com/spreadsheets/d/\(t)/edit"
}

func normalizeBriefComment(_ value: String?) -> String {
    let normalized = normalizeText(value)
    if normalized.isEmpty { return VisitOutcome.other.rawValue }
    for (regex, match) in normalizationRules {
        let range = NSRange(location: 0, length: (normalized as NSString).length)
        if regex.firstMatch(in: normalized, options: [], range: range) != nil {
            return match.rawValue
        }
    }
    return normalized
}

func parseLastVisited(_ value: String?) -> String? {
    let normalized = normalizeText(value)
    if normalized.isEmpty { return nil }
    let range = NSRange(location: 0, length: (normalized as NSString).length)
    guard let m = fullDateRegex.firstMatch(in: normalized, options: [], range: range),
        m.numberOfRanges == 4,
        let month = Int((normalized as NSString).substring(with: m.range(at: 1))),
        let day = Int((normalized as NSString).substring(with: m.range(at: 2)))
    else { return nil }
    let rawYear = (normalized as NSString).substring(with: m.range(at: 3))
    let year: Int
    if rawYear.count == 2 {
        year = 2000 + (Int(rawYear) ?? 0)
    } else {
        year = Int(rawYear) ?? 0
    }
    var c = DateComponents()
    c.year = year
    c.month = month
    c.day = day
    c.calendar = Calendar(identifier: .gregorian)
    c.timeZone = TimeZone(identifier: "UTC")
    guard let d = c.date else { return nil }
    let f = ISO8601DateFormatter()
    f.formatOptions = .withFullDate
    f.timeZone = TimeZone(identifier: "UTC")
    return f.string(from: d)
}

func createHouseholdId(name: String, streetAddress: String, neighborhood: String) -> String {
    var base = "\(name)|\(streetAddress)|\(neighborhood)"
        .lowercased()
        .replacingOccurrences(of: "[^a-z0-9|]+", with: "-", options: .regularExpression)
        .replacingOccurrences(of: "-+", with: "-", options: .regularExpression)
    base = base.trimmingCharacters(in: CharacterSet(charactersIn: "-"))
    return "household_\(base.isEmpty ? "unknown" : base)"
}

func parseSpreadsheetRow(_ row: SpreadsheetRowInput, source: SourceMetadata) -> HouseholdRecord {
    let name = normalizeText(row.name)
    let streetAddress = normalizeText(row.streetAddress)
    let neighborhood = normalizeText(row.neighborhood)
    return HouseholdRecord(
        id: createHouseholdId(name: name, streetAddress: streetAddress, neighborhood: neighborhood),
        name: name,
        streetAddress: streetAddress,
        neighborhood: neighborhood,
        briefComment: normalizeBriefComment(row.briefComments),
        lastVisited: parseLastVisited(row.lastVisited),
        notes: normalizeOptionalText(row.notes),
        source: source,
        raw: RawHouseholdRow(
            briefComments: row.briefComments,
            lastVisited: row.lastVisited,
            name: row.name,
            streetAddress: row.streetAddress,
            neighborhood: row.neighborhood,
            notes: row.notes
        ),
        latitude: nil,
        longitude: nil,
        assignedTo: nil
    )
}
