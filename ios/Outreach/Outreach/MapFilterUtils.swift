import Foundation

func parseIsoDateOrNull(_ value: String?) -> Date? {
    guard let value, !value.isEmpty else { return nil }
    let f = ISO8601DateFormatter()
    f.formatOptions = .withFullDate
    f.timeZone = TimeZone(identifier: "UTC")
    return f.date(from: value)
}

func formatBriefComment(_ value: String) -> String {
    value
        .replacingOccurrences(of: "_", with: " ")
        .split(separator: " ")
        .filter { !$0.isEmpty }
        .map { s in
            let str = String(s)
            guard let c = str.first else { return str }
            return String(c).uppercased() + str.dropFirst().lowercased()
        }
        .joined(separator: " ")
}

func householdMatchesTextSearch(_ household: HouseholdRecord, query: String) -> Bool {
    let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
    if q.isEmpty { return true }
    return household.name.range(of: q, options: .caseInsensitive) != nil
        || household.streetAddress.range(of: q, options: .caseInsensitive) != nil
}

func filterHouseholdsForMap(
    data: [HouseholdRecord],
    visibleZipTabs: Set<String>,
    briefCommentMode: String,
    selectedBriefComments: Set<String>,
    startDate: Date,
    endDate: Date
) -> [HouseholdRecord] {
    let cal = Calendar(identifier: .gregorian)
    return data.filter { household in
        let tabMatches = visibleZipTabs.isEmpty || visibleZipTabs.contains(household.source.sheetName)
        let briefCommentMatches: Bool = {
            if briefCommentMode == "pick_some" {
                return selectedBriefComments.contains(household.briefComment)
            }
            return true
        }()
        let visitationDate = parseIsoDateOrNull(household.lastVisited)
        let dateMatches: Bool = {
            guard let v = visitationDate else { return true }
            let start = cal.startOfDay(for: startDate)
            let end = cal.startOfDay(for: endDate)
            let d = cal.startOfDay(for: v)
            return d >= start && d <= end
        }()
        return tabMatches && briefCommentMatches && dateMatches
    }
}

func applyOldestRecordsLimit(_ households: [HouseholdRecord], limit: Int?) -> [HouseholdRecord] {
    guard let limit, limit > 0 else { return households }
    return households
        .sorted { a, b in
            let ad = parseIsoDateOrNull(a.lastVisited)?.timeIntervalSince1970 ?? .leastNonzeroMagnitude
            let bd = parseIsoDateOrNull(b.lastVisited)?.timeIntervalSince1970 ?? .leastNonzeroMagnitude
            if ad != bd { return ad < bd }
            return a.id < b.id
        }
        .prefix(limit)
        .map { $0 }
}
