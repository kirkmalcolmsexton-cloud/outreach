package org.outreach.app

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.rule.GrantPermissionRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.outreach.app.testing.UiAutomationConfig
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
        composeRule.onNodeWithTag(TestTags.NAV_HOME).performClick()
        composeRule.onNodeWithTag(TestTags.CONTENT_HOME).assertIsDisplayed()
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
}
