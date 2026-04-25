package org.outreach.testing

import android.content.Intent
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry

/**
 * The default [createAndroidComposeRule] path uses the androidx.test
 * "getIntentForActivity" implementation, which first tries
 * [Intent.makeMainActivity] (MAIN + LAUNCHER) on the target package.
 * [ComposeHostActivity] is exported but has no MAIN/LAUNCHER intent filter, so
 * that lookup fails and the test runner falls back to the instrumentation
 * application id (`…​.test`) — producing
 * "Unable to resolve activity ... org.outreach.app.test/...ComposeHostActivity" on CI.
 * This rule uses an explicit [Intent] from the instrumentation **target** context instead.
 */
fun createComposeHostRule(): AndroidComposeTestRule<ActivityScenarioRule<ComposeHostActivity>, ComposeHostActivity> {
    val startIntent = Intent(InstrumentationRegistry.getInstrumentation().targetContext, ComposeHostActivity::class.java)
    return AndroidComposeTestRule(
        activityRule = ActivityScenarioRule<ComposeHostActivity>(startIntent),
        activityProvider = { rule ->
            var activity: ComposeHostActivity? = null
            rule.scenario.onActivity { a -> activity = a }
            return@AndroidComposeTestRule requireNotNull(activity) {
                "Activity was not set in the ActivityScenarioRule!"
            }
        }
    )
}
