package org.outreach.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.outreach.core.model.AppConfig
import org.outreach.testing.FakeSheetsApi
import org.outreach.ui.testtags.TestTags

class SettingsScreenMockSpreadsheetSelectionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectingMockedSpreadsheet_syncUsesSampleData() = runBlocking {
        val fakeSheetsApi = FakeSheetsApi.withSampleDriveSpreadsheet()
        var pickedSpreadsheetId by mutableStateOf<String?>(null)
        var pickedSpreadsheetDisplayName by mutableStateOf<String?>(null)

        var savedConfig = AppConfig()
        var syncedConfig: AppConfig? = null
        var syncedRowsCount = 0

        composeRule.setContent {
            SettingsScreen(
                pickedSpreadsheetId = pickedSpreadsheetId,
                pickedSpreadsheetDisplayName = pickedSpreadsheetDisplayName,
                savedConfig = savedConfig,
                onUpdateConfig = { cfg -> savedConfig = cfg },
                onLoadTabs = { spreadsheetId, onResult ->
                    onResult(Result.success(runBlocking { fakeSheetsApi.listTabs(spreadsheetId) }))
                },
                onFetchSpreadsheetTitle = { spreadsheetId ->
                    fakeSheetsApi.getSpreadsheetTitle(spreadsheetId)
                },
                onPickSheetFromDrive = {
                    pickedSpreadsheetDisplayName = "Sample Outreach Households"
                    pickedSpreadsheetId = runBlocking {
                        fakeSheetsApi.resolveSpreadsheetIdFromDriveMetadata(
                            "Sample Outreach Households",
                            null
                        )
                    }
                },
                onSyncFromSpreadsheet = { cfg ->
                    syncedConfig = cfg
                    syncedRowsCount = fakeSheetsApi.fetchRows(cfg.spreadsheetId, "60618").size
                }
            )
        }

        composeRule.onNodeWithTag(TestTags.SETTINGS_PICK_SPREADSHEET).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { pickedSpreadsheetId != null }
        assertNotNull(pickedSpreadsheetId)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("60618").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("60618").performClick()
        composeRule.onNodeWithTag(TestTags.SETTINGS_SYNC).performClick()

        composeRule.runOnIdle {
            assertNotNull(syncedConfig)
            assertEquals("sample-drive-sheet-001", syncedConfig?.spreadsheetId)
            assertEquals(setOf("60618"), syncedConfig?.selectedTabs)
            assertEquals(2, syncedRowsCount)
        }
    }
}
