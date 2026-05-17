package org.outreach.feature.visits

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.outreach.core.model.HouseholdRecord
import org.outreach.core.model.RawHouseholdRow
import org.outreach.core.model.SourceMetadata
import org.outreach.testing.ComposeHostActivity
import org.outreach.testing.createComposeHostRule
import org.outreach.testing.waitForSemanticTree
import org.outreach.ui.testtags.TestTags
import org.json.JSONObject

class VisitLogScreenTest {

    @get:Rule
    val composeRule = createComposeHostRule()

    @Test
    fun saveVisit_withSelectedPreset_invokesCallback() {
        val household = loadHouseholdFromFixture()

        var callbackCount = 0
        var capturedHouseholdId: String? = null
        var capturedBriefComment: String? = null
        var capturedNotes: String? = null
        var capturedDate: String? = null

        composeRule.setContent {
            VisitLogScreen(
                selectedHousehold = household,
                briefCommentPresets = listOf("Receptive", "Not home"),
                onSaveVisit = { householdId, briefComment, notes, lastVisitedIsoDate ->
                    callbackCount += 1
                    capturedHouseholdId = householdId
                    capturedBriefComment = briefComment
                    capturedNotes = notes
                    capturedDate = lastVisitedIsoDate
                }
            )
        }

        composeRule.waitForIdle()
        composeRule.waitForSemanticTree()

        composeRule.onNodeWithTag(TestTags.VISITS_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.VISITS_NOTES).assertIsDisplayed()
        composeRule.onNodeWithText("Visit update").assertIsDisplayed()
        val titleY = composeRule.onNodeWithText("Visit update").getUnclippedBoundsInRoot().let { ((it.top + it.bottom) / 2f).value }
        val briefY = composeRule.onNodeWithTag(TestTags.VISITS_BRIEF).getUnclippedBoundsInRoot()
            .let { ((it.top + it.bottom) / 2f).value }
        assertTrue("title above brief row", titleY < briefY)
        composeRule.onNodeWithText("Save offline + queue sync").assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.VISITS_BRIEF).performClick()
        composeRule.onNodeWithText("Receptive").performClick()

        composeRule.onNodeWithTag(TestTags.VISITS_SAVE).assertIsEnabled().performClick()

        composeRule.runOnIdle {
            assertEquals(1, callbackCount)
            assertEquals("household-asset-001", capturedHouseholdId)
            assertEquals("Receptive", capturedBriefComment)
            assertEquals("From fixture file", capturedNotes)
            assertNotNull(capturedDate)
            assertTrue(capturedDate!!.isNotBlank())
        }
    }

    private fun loadHouseholdFromFixture(): HouseholdRecord {
        val context = InstrumentationRegistry.getInstrumentation().context
        val json = context.assets.open("visit_household_fixture.json").bufferedReader().use { it.readText() }
        val payload = JSONObject(json)
        return HouseholdRecord(
            id = payload.getString("id"),
            name = payload.getString("name"),
            streetAddress = payload.getString("streetAddress"),
            neighborhood = payload.getString("neighborhood"),
            briefComment = payload.optString("briefComment"),
            lastVisited = null,
            notes = payload.optString("notes"),
            source = SourceMetadata(
                sheetName = payload.getString("sheetName"),
                rowNumber = payload.getInt("rowNumber")
            ),
            raw = RawHouseholdRow()
        )
    }
}
