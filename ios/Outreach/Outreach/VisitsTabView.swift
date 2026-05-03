import SwiftUI

/// Visit logging — aligned with Android `VisitLogScreen.kt`: selection happens on Home; no household list here.
struct VisitsTabView: View {
    var records: [HouseholdRecord]
    @Binding var selectedHouseholdId: String?

    var body: some View {
        VisitLogView(records: records, selectedHouseholdId: $selectedHouseholdId)
            .accessibilityIdentifier(UiTestTags.contentVisits)
    }
}
