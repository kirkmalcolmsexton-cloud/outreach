package org.outreach.testing

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Rule
import org.junit.runner.RunWith
import org.outreach.app.MainActivity

@RunWith(AndroidJUnit4::class)
abstract class BaseOutreachComposeTest {
    @get:Rule
    val composeRule: AndroidComposeTestRule<*, MainActivity> =
        createAndroidComposeRule()

    @After
    fun cleanupOutreachUiTestEnvironment() {
        OutreachUiTestEnvironment.clearOverrides()
    }
}
