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
    @StateObject private var routeCoordinator = MapRoutingCoordinator()

    private var selectedHouseholdForActions: HouseholdRecord? {
        selectedHouseholdId.flatMap { id in households.first { $0.id == id } }
    }

    var body: some View {
        homeScrollContent()
            .accessibilityIdentifier(UiTestTags.contentHome)
            .onChange(of: selectedHouseholdId) { _, _ in
                routeCoordinator.clearRoute()
            }
            .onChange(of: routeCoordinator.routeFitRevision) { _, _ in
                guard let region = MKCoordinateRegion(fitting: routeCoordinator.routeCoordinates) else { return }
                position = .region(region)
            }
            .alert("Routing", isPresented: routingErrorPresented) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(routeCoordinator.routingError ?? "")
            }
            .sheet(isPresented: $showAddPerson) {
                addPersonSheetView()
            }
    }

    private var routingErrorPresented: Binding<Bool> {
        Binding(
            get: { routeCoordinator.routingError != nil },
            set: { if !$0 { routeCoordinator.acknowledgeRoutingError() } }
        )
    }

    @ViewBuilder
    private func addPersonSheetView() -> some View {
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

    private func filteredHouseholdsForDisplay() -> [HouseholdRecord] {
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
        return applyOldestRecordsLimit(pass2, limit: config.mapOldestRecordsLimit)
    }

    @ViewBuilder
    private func homeScrollContent() -> some View {
        VStack(alignment: .leading, spacing: 0) {
            searchFieldRow()
            if mapHomeMode == .map {
                mapHomeLayer(
                    filtered: filteredHouseholdsForDisplay(),
                    selectedHousehold: selectedHouseholdForActions
                )
            } else {
                listHomeLayer(
                    filtered: filteredHouseholdsForDisplay(),
                    selectedHousehold: selectedHouseholdForActions
                )
            }
        }
    }

    private func searchFieldRow() -> some View {
        HStack {
            TextField("Search name or address", text: $searchQuery)
                .textFieldStyle(.roundedBorder)
                .accessibilityIdentifier(UiTestTags.mapSearch)
        }
        .padding(.horizontal)
    }

    @ViewBuilder
    private func mapHomeLayer(filtered: [HouseholdRecord], selectedHousehold: HouseholdRecord?) -> some View {
        ZStack(alignment: .bottomTrailing) {
            ZStack(alignment: .bottomLeading) {
                ZStack {
                    ZStack {
                        mapContent(
                            records: filtered,
                            position: $position,
                            routeCoordinates: routeCoordinator.routeCoordinates
                        )
                        if routeCoordinator.isCalculatingRoute {
                            ProgressView()
                                .padding(12)
                                .background(.ultraThinMaterial)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                        }
                    }
                    VStack {
                        if let summary = routeCoordinator.routeSummary {
                            routeGuidanceCard(summary)
                                .padding(.horizontal, 12)
                                .padding(.top, 8)
                        }
                        if let h = selectedHousehold {
                            householdDetailCard(h)
                                .padding(.horizontal, 12)
                                .padding(.top, 8)
                        }
                        Spacer(minLength: 0)
                    }
                }
                Text("\(filtered.count) on map after filters")
                    .font(.caption2)
                    .padding(8)
                    .background(.ultraThinMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .padding()
            }
            mapActionFabColumn(selectedHousehold: selectedHousehold)
        }
        // MKMapView tends to take over the container’s accessibility; keep a stable anchor for UI tests.
        .overlay(alignment: .topLeading) {
            Color.clear
                .contentShape(Rectangle())
                .frame(width: 44, height: 44)
                .accessibilityElement()
                .accessibilityIdentifier(UiTestTags.modeMap)
                .allowsHitTesting(false)
        }
    }

    @ViewBuilder
    private func routeGuidanceCard(_ summary: DrivingRouteSummary) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("En route")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(summary.destinationName)
                        .font(.headline)
                    Text("\(summary.etaShortFormatted) · \(summary.distanceShortFormatted)")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Text("Arrive around \(summary.projectedArrivalClockTime)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                Button {
                    routeCoordinator.repeatSpokenSummary()
                } label: {
                    Image(systemName: "speaker.wave.2.fill")
                        .frame(width: 36, height: 36)
                }
                .buttonStyle(.bordered)
                .accessibilityLabel("Repeat spoken directions")
            }
            if let next = summary.stepInstructions.first {
                Text("Next")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Text(next)
                    .font(.body)
                    .fontWeight(.semibold)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if summary.stepInstructions.count > 1 {
                DisclosureGroup {
                    ForEach(Array(summary.stepInstructions.enumerated()), id: \.offset) { pair in
                        Text("\(pair.offset + 1). \(pair.element)")
                            .font(.caption)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.vertical, 2)
                    }
                } label: {
                    Text("All steps (\(summary.stepInstructions.count))")
                        .font(.caption)
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(color: .black.opacity(0.15), radius: 4, y: 2)
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private func householdDetailCard(_ h: HouseholdRecord) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text(h.name)
                    .font(.headline)
                Spacer(minLength: 8)
                Button {
                    selectedHouseholdId = nil
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title3)
                        .symbolRenderingMode(.hierarchical)
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Dismiss")
            }
            Text(h.streetAddress)
                .font(.subheadline)
            if !h.neighborhood.isEmpty {
                Text(h.neighborhood)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Text(formatBriefComment(h.briefComment))
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Text("Last visited: \(formattedLastVisitedLabel(h.lastVisited))")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text("Tab \(h.source.sheetName)")
                .font(.caption2)
                .foregroundStyle(.tertiary)
            if let n = h.notes, !n.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Text(n)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(4)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(color: .black.opacity(0.12), radius: 4, y: 2)
        .accessibilityElement(children: .combine)
    }

    private func formattedLastVisitedLabel(_ raw: String?) -> String {
        guard let raw, let d = parseIsoDateOrNull(raw) else { return "Not visited" }
        let f = DateFormatter()
        f.dateStyle = .medium
        f.timeStyle = .none
        return f.string(from: d)
    }

    @ViewBuilder
    private func listHomeLayer(filtered: [HouseholdRecord], selectedHousehold: HouseholdRecord?) -> some View {
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

    @ViewBuilder
    private func mapActionFabColumn(selectedHousehold: HouseholdRecord?) -> some View {
        let has = selectedHousehold != nil
        let alpha: CGFloat = has ? 1.0 : 0.45
        VStack(spacing: 10) {
            Button {
                startInAppDrivingRoute(for: selectedHousehold)
            } label: {
                Image(systemName: "play.fill")
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.borderedProminent)
            .opacity(alpha)
            .accessibilityLabel("Start in-app driving route")
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
            .accessibilityLabel("Add household")
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

    private func startInAppDrivingRoute(for h: HouseholdRecord?) {
        guard let h else { return }
        if let la = h.latitude, let lo = h.longitude {
            routeCoordinator.requestDrivingRoute(
                to: CLLocationCoordinate2D(latitude: la, longitude: lo),
                destinationName: h.name
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
    private func mapContent(
        records: [HouseholdRecord],
        position: Binding<MapCameraPosition>,
        routeCoordinates: [CLLocationCoordinate2D]
    ) -> some View {
        let coordRecords = records.compactMap { h -> (HouseholdRecord, CLLocationCoordinate2D)? in
            guard let la = h.latitude, let lo = h.longitude else { return nil }
            return (h, CLLocationCoordinate2D(latitude: la, longitude: lo))
        }
        ZStack {
            Map(position: position) {
                if !routeCoordinates.isEmpty {
                    MapPolyline(coordinates: routeCoordinates)
                        .stroke(.blue, lineWidth: 5)
                }
                ForEach(coordRecords, id: \.0.id) { h, c in
                    Annotation(h.name, coordinate: c) {
                        let selected = selectedHouseholdId == h.id
                        Button {
                            if selectedHouseholdId == h.id {
                                selectedHouseholdId = nil
                            } else {
                                selectedHouseholdId = h.id
                            }
                        } label: {
                            ZStack {
                                Circle()
                                    .fill(Color.clear)
                                    .frame(width: 44, height: 44)
                                Circle()
                                    .strokeBorder(selected ? Color.primary : Color.clear, lineWidth: 2)
                                    .frame(width: 20, height: 20)
                                Circle()
                                    .fill(briefColor(h.briefComment))
                                    .frame(width: 14, height: 14)
                            }
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(Text(h.name))
                    }
                }
            }
            if coordRecords.isEmpty && routeCoordinates.isEmpty {
                Text("No coordinates for the current filter. Add addresses or adjust filters in Settings.")
                    .padding()
                    .background(.ultraThinMaterial)
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
