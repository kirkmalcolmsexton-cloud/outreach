package org.outreach.app

import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertFalse
import org.junit.Test

class AppShellTest {
    @Test
    fun launchesMainActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
            }
        }
    }
}
