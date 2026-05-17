import Foundation

private func m(_ a: Date, _ b: Date) -> Date { a > b ? a : b }

func resolvedMapDateRange(
    config: AppConfig,
    householdRecords: [HouseholdRecord]
) -> (Date, Date) {
    let cal = Calendar.current
    let today = cal.startOfDay(for: Date())
    let visitDates = householdRecords.compactMap { parseIsoDateOrNull($0.lastVisited) }
    let earliestVisitation = visitDates.map { cal.startOfDay(for: $0) }.min()
        ?? cal.date(from: DateComponents(year: 1970, month: 1, day: 1))!

    func parseIso(_ value: String?) -> Date? {
        guard let value, !value.isEmpty else { return nil }
        return parseIsoDateOrNull(value)
    }

    switch config.mapQuickRange {
    case "Today":
        let start = m(today, earliestVisitation)
        let end = m(today, start)
        return (start, end)
    case "Yesterday":
        let y = cal.date(byAdding: .day, value: -1, to: today) ?? today
        let start = m(y, earliestVisitation)
        let end = m(y, start)
        return (start, end)
    default:
        let startFromConfig = parseIso(config.mapDateStartIso) ?? earliestVisitation
        let start = m(startFromConfig, earliestVisitation)
        let endFromConfig = parseIso(config.mapDateEndIso) ?? today
        let end = m(endFromConfig, start)
        return (start, end)
    }
}
