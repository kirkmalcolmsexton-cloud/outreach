import SwiftUI
import Foundation

struct VisitsTabView: View {
    var records: [HouseholdRecord]
    @Binding var selectedHouseholdId: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VisitLogView(records: records, selectedHouseholdId: $selectedHouseholdId)
                listSection
            }
        }
        .accessibilityIdentifier(UiTestTags.contentVisits)
    }

    private var listSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Households")
                .font(.headline)
                .padding(.horizontal)
            ForEach(records) { h in
                Button {
                    selectedHouseholdId = h.id
                } label: {
                    HStack {
                        VStack(alignment: .leading) {
                            Text(h.name)
                            Text(h.streetAddress).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        if selectedHouseholdId == h.id {
                            Image(systemName: "checkmark.circle.fill").foregroundStyle(.tint)
                        }
                    }
                    .padding(12)
                    .background(RoundedRectangle(cornerRadius: 8).fill(Color(.secondarySystemBackground)))
                }
                .buttonStyle(.plain)
                .padding(.horizontal)
            }
        }
        .padding(.bottom, 24)
    }
}
