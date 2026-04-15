package org.outreach.core.model

enum class VisitOutcome {
    NOT_HOME,
    LEFT_MESSAGE,
    RECEPTIVE,
    DO_NOT_VISIT,
    MOVED,
    DAWAT_SAATH,
    OTHER
}

data class SourceMetadata(
    val sheetName: String,
    val rowNumber: Int
)

data class RawHouseholdRow(
    val briefComments: String? = null,
    val lastVisited: String? = null,
    val name: String? = null,
    val streetAddress: String? = null,
    val neighborhood: String? = null,
    val notes: String? = null
)

data class HouseholdRecord(
    val id: String,
    val name: String,
    val streetAddress: String,
    val neighborhood: String,
    val briefComment: String,
    val lastVisited: String?,
    val notes: String?,
    val source: SourceMetadata,
    val raw: RawHouseholdRow,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val assignedTo: String? = null
)

data class SpreadsheetRowInput(
    val briefComments: String? = null,
    val lastVisited: String? = null,
    val name: String? = null,
    val streetAddress: String? = null,
    val neighborhood: String? = null,
    val notes: String? = null
)

data class AppConfig(
    val spreadsheetId: String = "",
    /** Document title from Google Sheets API; helps confirm which file is selected. */
    val spreadsheetTitle: String? = null,
    /** ZIP tab names to sync from the sheet and show on the map (multi-select). */
    val selectedTabs: Set<String> = emptySet(),
    val mapBriefCommentFilter: Set<String> = emptySet(),
    /** When null, the map uses the earliest last-visited date from loaded data. */
    val mapDateStartIso: String? = null,
    /** When null, the map uses today. */
    val mapDateEndIso: String? = null,
    val mapQuickRange: String = "All",
    /** When null, map/list show all records after other filters. */
    val mapOldestRecordsLimit: Int? = null
)

data class VisitUpdate(
    val householdId: String,
    val briefComment: String,
    val notes: String?,
    val lastVisitedIsoDate: String,
    val sheetName: String? = null,
    val rowNumber: Int? = null
)

data class CollaborationEvent(
    val userId: String,
    val householdId: String?,
    val tabName: String?,
    val type: String,
    val epochMillis: Long
)
