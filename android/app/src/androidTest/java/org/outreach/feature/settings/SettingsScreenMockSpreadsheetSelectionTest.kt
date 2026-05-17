package org.outreach.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.outreach.core.model.AppConfig
import org.outreach.testing.ComposeHostActivity
import org.outreach.testing.createComposeHostRule
import org.outreach.testing.FakeSheetsApi
import org.outreach.testing.assertCenterXIsToTheLeft
import org.outreach.testing.waitForSemanticTree
import org.outreach.ui.testtags.TestTags

class SettingsScreenMockSpreadsheetSelectionTest {
    @get:Rule
    val composeRule = createComposeHostRule()

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

        composeRule.waitForIdle()
        composeRule.waitForSemanticTree()

        composeRule.onNodeWithTag(TestTags.SETTINGS_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.SETTINGS_VALIDATE).assertIsDisplayed()
        composeRule.assertCenterXIsToTheLeft(
            TestTags.SETTINGS_PICK_SPREADSHEET,
            TestTags.SETTINGS_VALIDATE
        )

        composeRule.onNodeWithTag(TestTags.SETTINGS_PICK_SPREADSHEET).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { pickedSpreadsheetId != null }
        assertNotNull(pickedSpreadsheetId)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("60618").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TestTags.SETTINGS_ZIP_SECTION).assertIsDisplayed()
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
