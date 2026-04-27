import MapKit
import SwiftUI
import UIKit

enum MapHomeMode: String, CaseIterable, Identifiable {
    case map
    case list
    var id: String { rawValue }
    var label: String { rawValue.prefix(1).uppercased() + rawValue.dropFirst() }

    static func defaultForLaunch() -> MapHomeMode {
        #if DEBUG
        if UiAutomationConfig.isUiTesting {
            switch UiAutomationConfig.homeViewMode {
            case .list: return .list
            case .map: return .map
            case .none: break
            }
        }
        #endif
        return .map
    }
}

struct MapTabView: View {
    @Environment(\.modelContext) private var modelContext
    @EnvironmentObject private var appConfig: AppConfigStore
    var households: [HouseholdRecord]
    var config: AppConfig
    @Binding var selectedHouseholdId: String?
    @Binding var mapHomeMode: MapHomeMode
    @State private var searchQuery = ""
    @State private var position: MapCameraPosition = .automatic
    @State private var showAddPerson = false
    @State private var addTab = ""
    @State private var addName = ""
    @State private var addStreet = ""
    @State private var addNeighborhood = ""

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
        let selectedHousehold = selectedHouseholdId.flatMap { id in households.first { $0.id == id } }

        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        TextField("Search name or address", text: $searchQuery)
                            .textFieldStyle(.roundedBorder)
                            .accessibilityIdentifier(UiTestTags.mapSearch)
                    }
                    .padding(.horizontal)
                    if mapHomeMode == .map {
                        ZStack(alignment: .bottomTrailing) {
                            ZStack(alignment: .bottomLeading) {
                                mapContent(records: filtered, position: $position)
                                Text("\(filtered.count) on map after filters")
                                    .font(.caption2)
                                    .padding(8)
                                    .background(.ultraThinMaterial)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                                    .padding()
                            }
                            mapActionFabColumn(selectedHousehold: selectedHousehold)
                        }
                        .accessibilityIdentifier(UiTestTags.modeMap)
                    } else {
                        ZStack(alignment: .bottomTrailing) {
                            List {
                                ForEach(filtered) { h in
                                    Button {
                                        selectedHouseholdId = h.id
                                    } label: {
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(h.name).font(.headline)
                                            Text(h.streetAddress + ", " + h.neighborhood)
                                                .font(.subheadline)
                                                .foregroundStyle(.secondary)
                                            Text("Tab \(h.source.sheetName) · \(formatBriefComment(h.briefComment))")
                                                .font(.caption)
                                        }
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .listStyle(.plain)
                            mapActionFabColumn(selectedHousehold: selectedHousehold)
                        }
                        .accessibilityElement(children: .contain)
                        .accessibilityIdentifier(UiTestTags.modeList)
                    }
                }
            }
        .accessibilityIdentifier(UiTestTags.contentHome)
        .sheet(isPresented: $showAddPerson) {
            NavigationStack {
                Form {
                    Section("New household") {
                        Picker("ZIP tab", selection: $addTab) {
                            ForEach(Array(config.selectedTabs).sorted(), id: \.self) { t in
                                Text(t).tag(t)
                            }
                        }
                        TextField("Name", text: $addName)
                        TextField("Street address", text: $addStreet)
                        TextField("Neighborhood", text: $addNeighborhood)
                    }
                }
                .navigationTitle("Add person")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { showAddPerson = false }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Add") {
                            Task { await confirmAddPerson() }
                        }
                    }
                }
            }
            .onAppear {
                addTab = config.selectedTabs.sorted().first ?? ""
            }
        }
    }

    @ViewBuilder
    private func mapActionFabColumn(selectedHousehold: HouseholdRecord?) -> some View {
        let has = selectedHousehold != nil
        let alpha: CGFloat = has ? 1.0 : 0.45
        VStack(spacing: 10) {
            Button {
                openDrivingDirections(for: selectedHousehold)
            } label: {
                Image(systemName: "play.fill")
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!has)
            .opacity(alpha)
            .accessibilityIdentifier(UiTestTags.mapNavFab)

            Button {
                openInAppleMaps(for: selectedHousehold)
            } label: {
                Image(systemName: "map")
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.bordered)
            .disabled(!has)
            .opacity(alpha)

            Button {
                shareHousehold(selectedHousehold)
            } label: {
                Image(systemName: "square.and.arrow.up")
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.bordered)
            .disabled(!has)
            .opacity(alpha)

            Button {
                showAddPerson = true
            } label: {
                Image(systemName: "plus")
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.borderedProminent)
            .accessibilityIdentifier(UiTestTags.mapAddPerson)
        }
        .padding(16)
    }

    @MainActor
    private func confirmAddPerson() async {
        ServiceLocator.setupIfNeeded(modelContext: modelContext, appConfig: appConfig)
        guard let repo = ServiceLocator.repository else {
            showAddPerson = false
            return
        }
        let tab = addTab.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !tab.isEmpty else { return }
        do {
            if let id = try await repo.addHousehold(
                tabName: tab,
                name: addName,
                streetAddress: addStreet,
                neighborhood: addNeighborhood
            ) {
                selectedHouseholdId = id
            }
            try? await repo.flushPendingSync()
            showAddPerson = false
            addName = ""
            addStreet = ""
            addNeighborhood = ""
        } catch {
            showAddPerson = false
        }
    }

    private func openDrivingDirections(for h: HouseholdRecord?) {
        guard let h else { return }
        if let la = h.latitude, let lo = h.longitude {
            let dest = CLLocationCoordinate2D(latitude: la, longitude: lo)
            let placemark = MKPlacemark(coordinate: dest)
            let item = MKMapItem(placemark: placemark)
            item.name = h.name
            item.openInMaps(
                launchOptions: [MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeDriving]
            )
            return
        }
        openInAppleMaps(for: h)
    }

    private func openInAppleMaps(for h: HouseholdRecord?) {
        guard let h else { return }
        let q = h.streetAddress.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        if let u = URL(string: "http://maps.apple.com/?q=\(q)") {
            UIApplication.shared.open(u)
        }
    }

    private func shareHousehold(_ h: HouseholdRecord?) {
        guard let h else { return }
        let text = "\(h.name) — \(h.streetAddress)"
        guard let root = UIApplication.outreachKeyWindow?.rootViewController?.outreachTopPresented else { return }
        let av = UIActivityViewController(activityItems: [text], applicationActivities: nil)
        av.popoverPresentationController?.sourceView = root.view
        root.present(av, animated: true)
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
