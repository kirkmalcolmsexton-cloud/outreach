import Combine
import Foundation

@MainActor
final class AppConfigStore: ObservableObject {
    @Published private(set) var config: AppConfig = .init()
    private let defaults: UserDefaults
    private let keys = (
        spreadsheet: "spreadsheet_id",
        title: "spreadsheet_title",
        tabs: "selected_tabs",
        mapBrief: "map_brief_mode",
        mapBriefFilter: "map_brief_filter",
        start: "map_date_start_iso",
        end: "map_date_end_iso",
        quick: "map_quick_range",
        oldest: "map_oldest_records_limit"
    )

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        reload()
    }

    func reload() {
        let tabs = (defaults.string(forKey: keys.tabs) ?? "")
            .split(separator: ",")
            .map(String.init)
            .filter { !$0.isEmpty }
        let filterSet = (defaults.string(forKey: keys.mapBriefFilter) ?? "")
            .split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        let titleRaw = (defaults.string(forKey: keys.title) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let mapBrief = defaults.string(forKey: keys.mapBrief) ?? ""
        let quick = defaults.string(forKey: keys.quick) ?? ""
        let oldest: Int? = {
            guard let s = defaults.string(forKey: keys.oldest), let n = Int(s), n > 0 else { return nil }
            return n
        }()
        config = AppConfig(
            spreadsheetId: defaults.string(forKey: keys.spreadsheet) ?? "",
            spreadsheetTitle: titleRaw.isEmpty ? nil : titleRaw,
            selectedTabs: Set(tabs),
            mapBriefCommentMode: mapBrief.isEmpty ? "include_all" : mapBrief,
            mapBriefCommentFilter: Set(filterSet),
            mapDateStartIso: (defaults.string(forKey: keys.start) ?? "").nilIfEmpty,
            mapDateEndIso: (defaults.string(forKey: keys.end) ?? "").nilIfEmpty,
            mapQuickRange: quick.isEmpty ? "All" : quick,
            mapOldestRecordsLimit: oldest
        )
    }

    func update(_ c: AppConfig) {
        defaults.set(c.spreadsheetId, forKey: keys.spreadsheet)
        if let t = c.spreadsheetTitle?.trimmingCharacters(in: .whitespacesAndNewlines), !t.isEmpty {
            defaults.set(t, forKey: keys.title)
        } else {
            defaults.removeObject(forKey: keys.title)
        }
        defaults.set(c.selectedTabs.sorted().joined(separator: ","), forKey: keys.tabs)
        defaults.set(c.mapBriefCommentMode, forKey: keys.mapBrief)
        defaults.set(c.mapBriefCommentFilter.sorted().joined(separator: ","), forKey: keys.mapBriefFilter)
        defaults.set(c.mapDateStartIso ?? "", forKey: keys.start)
        defaults.set(c.mapDateEndIso ?? "", forKey: keys.end)
        defaults.set(c.mapQuickRange, forKey: keys.quick)
        if let o = c.mapOldestRecordsLimit, o > 0 {
            defaults.set(String(o), forKey: keys.oldest)
        } else {
            defaults.removeObject(forKey: keys.oldest)
        }
        config = c
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
