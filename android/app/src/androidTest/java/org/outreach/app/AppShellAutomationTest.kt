package org.outreach.app

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.outreach.app.testing.UiAutomationConfig
import org.outreach.testing.assertCenterXIsToTheLeft
import org.outreach.testing.assertCenterYIsAbove
import org.outreach.testing.waitForSemanticTree
import org.outreach.ui.testtags.TestTags

private fun getMainActivityFromScenarioRule(rule: ActivityScenarioRule<MainActivity>): MainActivity {
    var activity: MainActivity? = null
    rule.scenario.onActivity { activity = it }
    return activity ?: error("Activity was not set in the ActivityScenarioRule!")
}

class AppShellAutomationTest {

    /** List mode avoids loading Google Map on Home; Maps SDK has thrown off-main-thread errors under tests. */
    private val intent =
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(UiAutomationConfig.EXTRA_SKIP_STARTUP_DELAY, true)
            putExtra(UiAutomationConfig.EXTRA_HOME_VIEW_MODE, "list")
        }

    private val activityScenarioRule = ActivityScenarioRule<MainActivity>(intent)
    private val composeRule = AndroidComposeTestRule(
        activityRule = activityScenarioRule,
        activityProvider = ::getMainActivityFromScenarioRule
    )
    private val permissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

    @Before
    fun waitForComposeReady() {
        composeRule.waitForSemanticTree()
    }

    @Test
    fun appShell_navigatesAcrossBottomTabs() {
        composeRule.onNodeWithTag(TestTags.APP_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.NAV_VISITS).performClick()
        composeRule.onNodeWithTag(TestTags.CONTENT_VISITS).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.NAV_SETTINGS).performClick()
        composeRule.onNodeWithTag(TestTags.CONTENT_SETTINGS).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.SETTINGS_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.NAV_HOME).performClick()
        composeRule.onNodeWithTag(TestTags.CONTENT_HOME).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.MAP_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.MAP_SEARCH).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.MAP_ADD_PERSON).assertIsDisplayed()
    }

    @Test
    fun visitsTab_exposesTaggedVisitLoggingControls() {
        composeRule.onNodeWithTag(TestTags.NAV_VISITS).performClick()
        composeRule.onNodeWithTag(TestTags.CONTENT_VISITS).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.VISITS_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.VISITS_BRIEF).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.VISITS_NOTES).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.VISITS_SAVE).assertIsDisplayed()
    }

    @Test
    fun settingsTab_exposesTaggedSpreadsheetControls() {
        composeRule.onNodeWithTag(TestTags.NAV_SETTINGS).performClick()
        composeRule.onNodeWithTag(TestTags.SETTINGS_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.SETTINGS_SPREADSHEET_LINK_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.SETTINGS_LOAD_SPREADSHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.SETTINGS_APP_VERSION).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.SETTINGS_PICK_SPREADSHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.SETTINGS_VALIDATE).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.SETTINGS_SYNC).assertIsDisplayed()
    }

    @Test
    fun profileMenu_signedOut_showsLoginAction() {
        composeRule.onNodeWithTag(TestTags.PROFILE_BUTTON).performClick()
        composeRule.onNodeWithTag(TestTags.PROFILE_MENU_LOGIN).assertIsDisplayed()
    }
}

class AppShellForcedLoginGateIntentTest {

    private val intent =
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(UiAutomationConfig.EXTRA_FORCE_LOGIN_GATE, true)
            putExtra(UiAutomationConfig.EXTRA_SKIP_STARTUP_DELAY, true)
        }

    private val activityScenarioRule = ActivityScenarioRule<MainActivity>(intent)
    private val composeRule = AndroidComposeTestRule(
        activityRule = activityScenarioRule,
        activityProvider = ::getMainActivityFromScenarioRule
    )
    private val permissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

    @Before
    fun waitForComposeReady() {
        composeRule.waitForSemanticTree()
    }

    @Test
    fun forceLoginGateExtra_showsLoginGateImmediately() {
        composeRule.onNodeWithTag(TestTags.LOGIN_GATE).assertIsDisplayed()
    }
}

class AppShellForcedSignedInIntentTest {

    private val intent =
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(UiAutomationConfig.EXTRA_FORCED_AUTH_STATE, "signed_in")
            putExtra(UiAutomationConfig.EXTRA_SKIP_STARTUP_DELAY, true)
            putExtra(UiAutomationConfig.EXTRA_HOME_VIEW_MODE, "list")
        }

    private val activityScenarioRule = ActivityScenarioRule<MainActivity>(intent)
    private val composeRule = AndroidComposeTestRule(
        activityRule = activityScenarioRule,
        activityProvider = ::getMainActivityFromScenarioRule
    )
    private val permissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

    @Before
    fun waitForComposeReady() {
        composeRule.waitForSemanticTree()
    }

    @Test
    fun forcedSignedInAndListMode_showsSignedInMenuAndListContent() {
        composeRule.onNodeWithTag(TestTags.PROFILE_BUTTON).performClick()
        composeRule.onNodeWithTag(TestTags.PROFILE_MENU_LOGOUT).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.PROFILE_MENU_SWITCH).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.MODE_LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.MAP_LIST).assertIsDisplayed()
    }

    /**
     * Screenshot / manual parity: top app title, Map/List + search, list body, then bottom nav; vertical order
     * matches Material Scaffold (top bar → home content → bottom bar).
     */
    @Test
    fun listHomeKeyChromeAndLayoutOrder() {
        composeRule.onNodeWithText("Outreach", substring = true, ignoreCase = false).assertIsDisplayed()
        composeRule.onNodeWithText("Search name or address", substring = true, ignoreCase = false)
            .assertIsDisplayed()
        composeRule.assertCenterYIsAbove(TestTags.MODE_LIST, TestTags.MAP_SEARCH)
        composeRule.assertCenterYIsAbove(TestTags.MAP_SEARCH, TestTags.MAP_LIST)
        composeRule.assertCenterYIsAbove(TestTags.MAP_LIST, TestTags.NAV_HOME)
    }

    @Test
    fun visitsTab_showsFormCopyAndVerticalOrder() {
        composeRule.onNodeWithTag(TestTags.NAV_VISITS).performClick()
        composeRule.onNodeWithText("Visit update").assertIsDisplayed()
        composeRule.onNodeWithText("Select someone from the Home map or list", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Save offline + queue sync").assertIsDisplayed()
        val titleMid = composeRule.onNodeWithText("Visit update").getUnclippedBoundsInRoot()
            .let { ((it.top + it.bottom) / 2f).value }
        val briefMid = composeRule.onNodeWithTag(TestTags.VISITS_BRIEF).getUnclippedBoundsInRoot()
            .let { ((it.top + it.bottom) / 2f).value }
        assertTrue("Visit title should be above brief field", titleMid < briefMid)
    }
}

class AppShellMockSignedInConfigurableEmailTest {

    private val intent =
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(UiAutomationConfig.EXTRA_FORCED_AUTH_STATE, "signed_in")
            putExtra(UiAutomationConfig.EXTRA_AUTH_RESOLUTION, "mock")
            putExtra(UiAutomationConfig.EXTRA_SKIP_STARTUP_DELAY, true)
            putExtra(UiAutomationConfig.EXTRA_HOME_VIEW_MODE, "list")
        }

    private val activityScenarioRule = ActivityScenarioRule<MainActivity>(intent)
    private val composeRule = AndroidComposeTestRule(
        activityRule = activityScenarioRule,
        activityProvider = ::getMainActivityFromScenarioRule
    )
    private val permissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

    @Before
    fun waitForComposeReady() {
        composeRule.waitForSemanticTree()
    }

    @Test
    fun mockSignedIn_showsConfiguredEmailInProfileMenu() {
        val args = InstrumentationRegistry.getArguments()
        val authResolution = args.getString(UiAutomationConfig.EXTRA_AUTH_RESOLUTION)?.lowercase()
        composeRule.onNodeWithTag(TestTags.PROFILE_BUTTON).performClick()

        if (authResolution == "real") {
            val configuredEmail =
                args.getString(UiAutomationConfig.EXTRA_MOCK_USER_EMAIL)?.trim()?.takeIf { it.isNotEmpty() }
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            assertNotNull(
                "Real auth requires a signed-in Firebase user on this device. " +
                    "currentUser is null (wrong password, cancelled sign-in, or signed out).",
                firebaseUser
            )
            val user = firebaseUser!!
            if (configuredEmail != null) {
                assertEquals(
                    "Signed-in email must match -PoutreachMockUserEmail when both are used with real auth.",
                    configuredEmail.lowercase(),
                    user.email?.lowercase()
                )
            }
            val profileLine = user.email ?: user.displayName ?: error("Signed-in user has no email or display name")
            composeRule.onNodeWithText(profileLine).assertIsDisplayed()
        } else {
            val expectedEmail =
                args.getString(UiAutomationConfig.EXTRA_MOCK_USER_EMAIL)?.trim()?.takeIf { it.isNotEmpty() }
                    ?: UiAutomationConfig.DEFAULT_MOCK_USER_EMAIL
            composeRule.onNodeWithText(expectedEmail).assertIsDisplayed()
        }
    }
}

/**
 * Map mode loads Google Map compose host; keep isolated so list-mode CI stays stable if Maps flakes.
 */
class AppShellMapModeForcedSignedInIntentTest {

    private val intent =
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(UiAutomationConfig.EXTRA_FORCED_AUTH_STATE, "signed_in")
            putExtra(UiAutomationConfig.EXTRA_SKIP_STARTUP_DELAY, true)
            putExtra(UiAutomationConfig.EXTRA_HOME_VIEW_MODE, "map")
        }

    private val activityScenarioRule = ActivityScenarioRule<MainActivity>(intent)
    private val composeRule = AndroidComposeTestRule(
        activityRule = activityScenarioRule,
        activityProvider = ::getMainActivityFromScenarioRule
    )
    private val permissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

    @Before
    fun waitForComposeReady() {
        composeRule.waitForSemanticTree()
    }

    @Test
    fun mapMode_showsMapChromeTags() {
        composeRule.onNodeWithTag(TestTags.APP_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.MODE_MAP).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.MAP_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.MAP_SEARCH).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.MAP_NAV_FAB).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.MAP_ADD_PERSON).assertIsDisplayed()
    }
}
