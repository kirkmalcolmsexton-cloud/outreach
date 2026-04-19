package org.outreach.feature.settings

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.outreach.app.BuildConfig
import org.outreach.testing.ComposeHostActivity
import org.outreach.testing.waitForSemanticTree
import org.outreach.ui.testtags.TestTags

class SettingsScreenVersionUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeHostActivity>()

    @Test
    fun settingsScreen_showsAppVersionFromBuildConfig() {
        composeRule.setContent {
            SettingsScreen()
        }
        composeRule.waitForIdle()
        composeRule.waitForSemanticTree()

        val expected = buildString {
            append("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            if (BuildConfig.DEBUG) append(" (debug)")
        }

        composeRule
            .onNodeWithTag(TestTags.SETTINGS_APP_VERSION, useUnmergedTree = true)
            .performScrollTo()
            .assertTextEquals(expected)
    }
}
