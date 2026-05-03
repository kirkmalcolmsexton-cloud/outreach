import SwiftUI
import UIKit

/// Visit logging form aligned with Android `VisitLogScreen.kt`.
struct VisitLogView: View {
    @EnvironmentObject private var appConfig: AppConfigStore
    @Environment(\.modelContext) private var modelContext
    var records: [HouseholdRecord]
    @Binding var selectedHouseholdId: String?

    @State private var presets: [String] = []
    @State private var presetsLoading = false
    @State private var visitDate = Date()
    @State private var notes = ""
    @State private var dropdownBrief: String?
    @State private var useCustomBrief = false
    @State private var customBrief = ""
    @State private var briefMenuOpen = false
    @State private var statusMessage = ""

    private var selectedHousehold: HouseholdRecord? {
        guard let id = selectedHouseholdId else { return nil }
        return records.first { $0.id == id }
    }

    private var briefPresetsEffective: [String] {
        if !presets.isEmpty { return presets }
        return ["Not home", "Left message", "Receptive", "Do not visit", "Moved", "Dawat saath", "Other"]
    }

    private var briefToSave: String {
        if useCustomBrief { return customBrief.trimmingCharacters(in: .whitespacesAndNewlines) }
        return dropdownBrief?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    private var canSave: Bool {
        selectedHousehold != nil && !briefToSave.isEmpty
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Visit update")
                    .font(.title2.bold())
                if selectedHousehold == nil {
                    Text("Select someone from the Home map or list, then return here to log a visit.")
                        .foregroundStyle(.secondary)
                }
                if let h = selectedHousehold {
                    Group {
                        Text("Name: \(h.name)")
                        Text("Address: \(h.streetAddress)")
                        if !h.neighborhood.isEmpty {
                            Text("Neighborhood: \(h.neighborhood)")
                        }
                    }
                    .font(.subheadline)
                }

                DatePicker(
                    "Visit date",
                    selection: $visitDate,
                    displayedComponents: .date
                )
                .datePickerStyle(.compact)

                if presetsLoading {
                    Text("Loading brief comment options…").font(.caption).foregroundStyle(.secondary)
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text("Brief comment").font(.caption).foregroundStyle(.secondary)
                    Menu {
                        ForEach(briefPresetsEffective, id: \.self) { opt in
                            Button(opt) {
                                useCustomBrief = false
                                dropdownBrief = opt
                            }
                        }
                        Button("Custom…") {
                            useCustomBrief = true
                            dropdownBrief = nil
                        }
                    } label: {
                        HStack {
                            Text(displayedBriefLabel)
                                .foregroundStyle(.primary)
                            Spacer()
                            Image(systemName: "chevron.down")
                        }
                        .padding(10)
                        .background(RoundedRectangle(cornerRadius: 8).stroke(Color.secondary.opacity(0.4)))
                        .accessibilityIdentifier(UiTestTags.visitsBrief)
                    }
                }

                if useCustomBrief {
                    TextField("Custom brief comment", text: $customBrief)
                        .textFieldStyle(.roundedBorder)
                }

                notesEditorSection

                // Avoid `.disabled` / low opacity — XCTest often omits those controls from the automation snapshot.
                Button {
                    Task { await saveTapped() }
                } label: {
                    Text("Save offline + queue sync")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .accessibilityLabel("Save offline + queue sync")
                .accessibilityIdentifier(UiTestTags.visitsSave)

                if !statusMessage.isEmpty {
                    Text(statusMessage).font(.caption).foregroundStyle(.red)
                }
            }
            .padding()
            .accessibilityIdentifier(UiTestTags.visitsRoot)
        }
        .onAppear {
            syncNotesFromHousehold()
            Task { await loadPresets() }
        }
        .onChange(of: selectedHouseholdId) { _, _ in
            syncNotesFromHousehold()
        }
    }

    private var notesEditorSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text("Notes")
                    .font(.subheadline.weight(.semibold))
                Image(systemName: "square.and.pencil")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                Spacer(minLength: 0)
            }
            Text("Tap below to add or edit free-form notes for this visit.")
                .font(.caption)
                .foregroundStyle(.secondary)
            TextField("Tap to type notes…", text: $notes, axis: .vertical)
                .textFieldStyle(.plain)
                .lineLimit(4 ... 10)
                .padding(12)
                .background(Color(uiColor: .secondarySystemGroupedBackground))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(Color(uiColor: .separator), lineWidth: 1)
                )
                .accessibilityIdentifier(UiTestTags.visitsNotes)
                .accessibilityHint("Editable multi-line text field.")
        }
    }

    private var displayedBriefLabel: String {
        if useCustomBrief { return customBrief.isEmpty ? "Custom…" : customBrief }
        if let d = dropdownBrief, !d.isEmpty { return d }
        return "Choose brief comment"
    }

    private func syncNotesFromHousehold() {
        notes = selectedHousehold?.notes ?? ""
        visitDate = Date()
        customBrief = ""
        useCustomBrief = false
        dropdownBrief = nil
    }

    @MainActor
    private func loadPresets() async {
        ServiceLocator.setupIfNeeded(modelContext: modelContext, appConfig: appConfig)
        guard let repo = ServiceLocator.repository else { return }
        presetsLoading = true
        defer { presetsLoading = false }
        presets = (try? await repo.loadBriefCommentPresets()) ?? []
    }

    @MainActor
    private func saveTapped() async {
        guard let h = selectedHousehold else { return }
        let brief = briefToSave
        guard !brief.isEmpty else { return }
        ServiceLocator.setupIfNeeded(modelContext: modelContext, appConfig: appConfig)
        guard let repo = ServiceLocator.repository else { return }
        statusMessage = ""
        let f = ISO8601DateFormatter()
        f.formatOptions = .withFullDate
        let today = f.string(from: visitDate)
        do {
            try repo.saveVisitUpdate(
                VisitUpdate(
                    householdId: h.id,
                    briefComment: normalizeBriefForStorage(brief),
                    notes: notes.isEmpty ? nil : notes,
                    lastVisitedIsoDate: today,
                    sheetName: h.source.sheetName,
                    rowNumber: h.source.rowNumber
                )
            )
            try? await repo.flushPendingSync()
        } catch {
            statusMessage = error.localizedDescription
        }
    }

    private func normalizeBriefForStorage(_ displayed: String) -> String {
        normalizeBriefComment(displayed)
    }
}
