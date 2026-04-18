package org.outreach.feature.map

import android.Manifest
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.google.android.gms.maps.model.LatLng
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.outreach.app.testing.NavigationTestSupport
import org.outreach.core.model.HouseholdRecord
import org.outreach.core.model.RawHouseholdRow
import org.outreach.core.model.SourceMetadata
import org.outreach.testing.ComposeHostActivity
import org.outreach.testing.OutreachUiTestEnvironment
import org.outreach.testing.waitForSemanticTree
import org.outreach.ui.testtags.TestTags

@RunWith(AndroidJUnit4::class)
class MapNavigationFlowTest {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComposeHostActivity>()

    private val householdId = "nav_test_household"
    private val destLat = 41.88
    private val destLng = -87.63

    @Before
    fun setup() {
        NavigationTestSupport.reset()
        val origin = LatLng(41.87, -87.64)
        val dest = LatLng(destLat, destLng)
        NavigationTestSupport.previewRouteByHouseholdId = mapOf(
            householdId to DrivingRoute(
                points = listOf(origin, dest),
                spokenInstructions = listOf("Head toward destination"),
                distanceText = "1 mi",
                durationText = "5 min",
                durationSeconds = 300
            )
        )
        NavigationTestSupport.simulatedNavigationLocations = listOf(
            origin.latitude to origin.longitude,
            Pair((origin.latitude + dest.latitude) / 2.0, (origin.longitude + dest.longitude) / 2.0),
            dest.latitude to dest.longitude
        )
    }

    @After
    fun tearDown() {
        OutreachUiTestEnvironment.clearOverrides()
    }

    @Test
    fun list_selectHousehold_startNavigation_simulatedMovementUpdates() {
        val household = HouseholdRecord(
            id = householdId,
            name = "Nav Test Household",
            streetAddress = "200 Test St, Chicago, IL",
            neighborhood = "Test",
            briefComment = "not_home",
            lastVisited = null,
            notes = null,
            source = SourceMetadata("60657", 2),
            raw = RawHouseholdRow(),
            latitude = destLat,
            longitude = destLng
        )
        composeRule.setContent {
            MaterialTheme {
                var selectedHouseholdId by remember { mutableStateOf<String?>(null) }
                MapScreen(
                    households = listOf(household),
                    selectedHouseholdId = selectedHouseholdId,
                    onHouseholdSelected = { h ->
                        selectedHouseholdId =
                            if (selectedHouseholdId == h.id) null else h.id
                    },
                    viewMode = "list",
                    onViewModeChange = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.waitForSemanticTree()

        val rowTag = TestTags.MAP_LIST_ROW_PREFIX + householdId
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasTestTag(rowTag), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(rowTag, useUnmergedTree = true).performClick()

        composeRule.onNodeWithTag(TestTags.MAP_NAV_FAB).performClick()
        composeRule.waitForIdle()

        // List mode does not show the map-only "Navigation active" label; wait for the Stop FAB icon.
        // Tag + description often live on different semantics nodes (merged vs unmerged); match description only.
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodes(hasContentDescription("End navigation"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasContentDescription("End navigation")).assertIsDisplayed()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            NavigationTestSupport.navigationLocationUpdateCount() >= 2
        }
    }
}
