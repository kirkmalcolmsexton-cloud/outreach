package org.outreach.testing

import android.os.Build
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry

/** Best-effort wake (does not require root); helps Samsung/OEM devices that dim during installs. */
private fun tryWakeScreen() {
    val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation ?: return
    if (Build.VERSION.SDK_INT < 21) return
    runCatching { uiAutomation.executeShellCommand("input keyevent 224") }
}

/**
 * Blocks until Compose has published a queryable semantics tree, or [timeoutMillis] elapses.
 * Physical devices (and some OEM builds) often need longer than emulators before the first frame
 * is queryable; without this, [androidx.compose.ui.test] can throw "No compose hierarchies found".
 */
fun ComposeTestRule.waitForSemanticTree(timeoutMillis: Long = 90_000) {
    tryWakeScreen()
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val deadline = System.currentTimeMillis() + timeoutMillis
    var iterations = 0
    while (System.currentTimeMillis() < deadline) {
        if (iterations++ % 16 == 0) {
            tryWakeScreen()
        }
        instrumentation.waitForIdleSync()
        val ready = runCatching {
            onRoot().assertExists()
            true
        }.getOrDefault(false)
        if (ready) {
            waitForIdle()
            return
        }
        Thread.sleep(48)
    }
    throw AssertionError(
        "Compose semantic tree did not appear within ${timeoutMillis}ms. " +
            "Enable Developer options → Stay awake (USB), keep the device unlocked, set ANDROID_SERIAL " +
            "when multiple devices are connected, and retry."
    )
}
