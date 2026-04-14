package org.outreach.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class AppShellTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsLoginGateInitially() {
        composeRule.onNodeWithText("Sign in with Google SSO").assertExists()
    }
}
