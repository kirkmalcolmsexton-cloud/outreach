import SwiftUI
import Foundation

struct VisitsTabView: View {
    var records: [HouseholdRecord]
    var config: AppConfig
    @EnvironmentObject private var appConfig: AppConfigStore
    @Environment(\.modelContext) private var modelContext
    @State private var nextTarget: HouseholdRecord?
    @State private var message = ""
    @State private var isSaving = false

    var body: some View {
        let next = nextTarget ?? ServiceLocator.repository?.pickNextTarget(records) ?? records.first
        NavigationStack {
            List {
                if let n = next {
                    Section("Next suggested") {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(n.name).font(.headline)
                            Text(n.streetAddress)
                            Text("Last: \(n.lastVisited ?? "—")")
                            Button("Log visit for today (receptive)") {
                                Task { await saveVisit(n, brief: "receptive") }
                            }
                            .buttonStyle(.borderedProminent)
                        }
                    }
                }
                Section("All") {
                    ForEach(records) { h in
                        HStack {
                            VStack(alignment: .leading) {
                                Text(h.name)
                                Text(h.streetAddress).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                        }
                    }
                }
            }
            .navigationTitle("Visits")
            if !message.isEmpty { Text(message).font(.caption).foregroundStyle(.red) }
            if isSaving { ProgressView() }
        }
        .onAppear { nextTarget = ServiceLocator.repository?.pickNextTarget(records) }
    }

    @MainActor
    private func saveVisit(_ h: HouseholdRecord, brief: String) async {
        ServiceLocator.setupIfNeeded(modelContext: modelContext, appConfig: appConfig)
        guard let repo = ServiceLocator.repository else { return }
        isSaving = true
        message = ""
        defer { isSaving = false }
        let f = ISO8601DateFormatter()
        f.formatOptions = .withFullDate
        let today = f.string(from: Date())
        do {
            try repo.saveVisitUpdate(
                VisitUpdate(
                    householdId: h.id,
                    briefComment: brief,
                    notes: h.notes,
                    lastVisitedIsoDate: today,
                    sheetName: h.source.sheetName,
                    rowNumber: h.source.rowNumber
                )
            )
            try? await repo.flushPendingSync()
            let all = (try? repo.householdsAsRecords()) ?? records
            nextTarget = repo.pickNextTarget(all)
        } catch {
            message = error.localizedDescription
        }
    }
}

