package org.outreach.testing

import org.outreach.core.data.AppendHouseholdResult
import org.outreach.core.data.SheetsApi
import org.outreach.core.model.SpreadsheetRowInput
import org.outreach.core.model.VisitUpdate

class FakeSheetsApi(
    var tabsBySpreadsheetId: Map<String, List<String>> = emptyMap(),
    var titlesBySpreadsheetId: Map<String, String> = emptyMap(),
    var rowsBySpreadsheetAndTab: Map<Pair<String, String>, List<SpreadsheetRowInput>> = emptyMap(),
    var schemaValidityBySpreadsheetAndTab: Map<Pair<String, String>, Boolean> = emptyMap(),
    var briefCommentPresetsBySpreadsheetId: Map<String, List<String>> = emptyMap(),
    var resolvedSpreadsheetIdByDisplayName: Map<String, String> = emptyMap(),
    var selectableDriveSpreadsheets: List<DriveSpreadsheet> = emptyList()
) : SheetsApi {
    val visitUpdates = mutableListOf<VisitUpdate>()
    val appendRequests = mutableListOf<AppendRequest>()

    data class AppendRequest(
        val spreadsheetId: String,
        val tabName: String,
        val name: String,
        val streetAddress: String,
        val neighborhood: String
    )

    data class DriveSpreadsheet(
        val id: String,
        val displayName: String,
        val tabs: List<String>,
        val rowsByTab: Map<String, List<SpreadsheetRowInput>>,
        val title: String = displayName
    )

    override suspend fun listTabs(spreadsheetId: String): List<String> {
        return tabsBySpreadsheetId[spreadsheetId].orEmpty()
    }

    override suspend fun getSpreadsheetTitle(spreadsheetId: String): String? =
        titlesBySpreadsheetId[spreadsheetId]

    override suspend fun fetchRows(spreadsheetId: String, tabName: String): List<SpreadsheetRowInput> =
        rowsBySpreadsheetAndTab[spreadsheetId to tabName].orEmpty()

    override suspend fun updateVisit(spreadsheetId: String, update: VisitUpdate) {
        visitUpdates += update
    }

    override suspend fun appendHouseholdRow(
        spreadsheetId: String,
        tabName: String,
        name: String,
        streetAddress: String,
        neighborhood: String
    ): AppendHouseholdResult {
        appendRequests += AppendRequest(spreadsheetId, tabName, name, streetAddress, neighborhood)
        return AppendHouseholdResult(rowNumber = 2 + appendRequests.size)
    }

    override suspend fun validateRequiredHeaders(spreadsheetId: String, tabName: String): Boolean =
        schemaValidityBySpreadsheetAndTab[spreadsheetId to tabName] ?: true

    override suspend fun fetchBriefCommentPresets(spreadsheetId: String): List<String> =
        briefCommentPresetsBySpreadsheetId[spreadsheetId].orEmpty()

    override suspend fun resolveSpreadsheetIdFromDriveMetadata(
        displayName: String,
        lastModifiedMillis: Long?
    ): String? {
        val normalized = displayName.trim()
        resolvedSpreadsheetIdByDisplayName[normalized]?.let { return it }
        return selectableDriveSpreadsheets.firstOrNull {
            it.displayName.equals(normalized, ignoreCase = true)
        }?.id
    }

    fun installDriveSpreadsheet(spreadsheet: DriveSpreadsheet) {
        selectableDriveSpreadsheets = selectableDriveSpreadsheets + spreadsheet
        tabsBySpreadsheetId = tabsBySpreadsheetId + (spreadsheet.id to spreadsheet.tabs)
        titlesBySpreadsheetId = titlesBySpreadsheetId + (spreadsheet.id to spreadsheet.title)
        rowsBySpreadsheetAndTab = rowsBySpreadsheetAndTab + spreadsheet.rowsByTab.mapKeys { (tab, _) ->
            spreadsheet.id to tab
        }
        resolvedSpreadsheetIdByDisplayName =
            resolvedSpreadsheetIdByDisplayName + (spreadsheet.displayName to spreadsheet.id)
    }

    companion object {
        fun withSampleDriveSpreadsheet(): FakeSheetsApi {
            val api = FakeSheetsApi()
            api.installDriveSpreadsheet(
                DriveSpreadsheet(
                    id = "sample-drive-sheet-001",
                    displayName = "Sample Outreach Households",
                    tabs = listOf("60618"),
                    rowsByTab = mapOf(
                        "60618" to listOf(
                            SpreadsheetRowInput(
                                briefComments = "Not home",
                                lastVisited = "2026-04-10",
                                name = "Sample Person One",
                                streetAddress = "100 Sample St",
                                neighborhood = "Avondale",
                                notes = "Sample note A"
                            ),
                            SpreadsheetRowInput(
                                briefComments = "Receptive",
                                lastVisited = "2026-04-11",
                                name = "Sample Person Two",
                                streetAddress = "200 Sample St",
                                neighborhood = "Avondale",
                                notes = "Sample note B"
                            )
                        )
                    )
                )
            )
            return api
        }
    }
}
