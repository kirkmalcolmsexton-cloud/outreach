import MapKit
import SwiftUI

struct MapTabView: View {
    var households: [HouseholdRecord]
    var config: AppConfig
    @Binding var selectedHouseholdId: String?
    @State private var mapViewMode: MapListMode = .map
    @State private var searchQuery = ""
    @State private var position: MapCameraPosition = .automatic

    private enum MapListMode: String, CaseIterable, Hashable {
        case map = "Map"
        case list = "List"
    }

    var body: some View {
        let (start, end) = resolvedMapDateRange(config: config, householdRecords: households)
        let pass1 = filterHouseholdsForMap(
            data: households,
            visibleZipTabs: config.selectedTabs,
            briefCommentMode: config.mapBriefCommentMode,
            selectedBriefComments: config.mapBriefCommentFilter,
            startDate: start,
            endDate: end
        )
        let pass2 = pass1.filter { householdMatchesTextSearch($0, query: searchQuery) }
        let filtered = applyOldestRecordsLimit(pass2, limit: config.mapOldestRecordsLimit)
        NavigationStack {
            VStack(alignment: .leading, spacing: 0) {
                Picker("View", selection: $mapViewMode) {
                    ForEach(MapListMode.allCases, id: \.self) { m in
                        Text(m.rawValue).tag(m)
                    }
                }
                .pickerStyle(.segmented)
                .padding([.horizontal, .top])
                HStack {
                    TextField("Search name or address", text: $searchQuery)
                        .textFieldStyle(.roundedBorder)
                }
                .padding(.horizontal)
                if mapViewMode == .map {
                    ZStack(alignment: .bottomLeading) {
                        mapContent(records: filtered, position: $position)
                        Text("\(filtered.count) on map after filters")
                            .font(.caption2)
                            .padding(8)
                            .background(.ultraThinMaterial)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                            .padding()
                    }
                } else {
                    List {
                        ForEach(filtered) { h in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(h.name).font(.headline)
                                Text(h.streetAddress + ", " + h.neighborhood)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                Text("Tab \(h.source.sheetName) · \(formatBriefComment(h.briefComment))")
                                    .font(.caption)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Home")
        }
    }

    @ViewBuilder
    private func mapContent(records: [HouseholdRecord], position: Binding<MapCameraPosition>) -> some View {
        let coordRecords = records.compactMap { h -> (HouseholdRecord, CLLocationCoordinate2D)? in
            guard let la = h.latitude, let lo = h.longitude else { return nil }
            return (h, CLLocationCoordinate2D(latitude: la, longitude: lo))
        }
        if coordRecords.isEmpty {
            ZStack {
                Map(position: position) { }
                Text("No coordinates for the current filter. Add addresses or adjust filters in Settings.")
                    .padding()
                    .background(.ultraThinMaterial)
            }
        } else {
            Map(position: position) {
                ForEach(coordRecords, id: \.0.id) { h, c in
                    Annotation(h.name, coordinate: c) {
                        Circle()
                            .fill(briefColor(h.briefComment))
                            .frame(width: 14, height: 14)
                    }
                }
            }
        }
    }

    private func briefColor(_ brief: String) -> Color {
        switch brief {
        case "not_home": return .gray
        case "receptive": return .green
        case "left_message": return .orange
        case "do_not_visit": return .red
        default: return .blue
        }
    }
}
