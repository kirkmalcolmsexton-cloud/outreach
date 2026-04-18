package org.outreach.app

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.outreach.app.testing.UiAutomationConfig
import org.outreach.ui.testtags.TestTags

class AppShellAutomationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

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
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun forceLoginGateExtra_showsLoginGateImmediately() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(UiAutomationConfig.EXTRA_FORCE_LOGIN_GATE, true)
            putExtra(UiAutomationConfig.EXTRA_SKIP_STARTUP_DELAY, true)
        }
        ActivityScenario.launch<MainActivity>(intent).use {
            composeRule.onNodeWithTag(TestTags.LOGIN_GATE).assertIsDisplayed()
        }
    }
}

class AppShellForcedSignedInIntentTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun forcedSignedInAndListMode_showsSignedInMenuAndListContent() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(UiAutomationConfig.EXTRA_FORCED_AUTH_STATE, "signed_in")
            putExtra(UiAutomationConfig.EXTRA_SKIP_STARTUP_DELAY, true)
            putExtra(UiAutomationConfig.EXTRA_HOME_VIEW_MODE, "list")
        }
        ActivityScenario.launch<MainActivity>(intent).use {
            composeRule.onNodeWithTag(TestTags.PROFILE_BUTTON).performClick()
            composeRule.onNodeWithTag(TestTags.PROFILE_MENU_LOGOUT).assertIsDisplayed()
            composeRule.onNodeWithTag(TestTags.PROFILE_MENU_SWITCH).assertIsDisplayed()
            composeRule.onNodeWithTag(TestTags.MODE_LIST).assertIsDisplayed()
            composeRule.onNodeWithTag(TestTags.MAP_LIST).assertIsDisplayed()
        }
    }
}
