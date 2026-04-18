package org.outreach.app

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
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
