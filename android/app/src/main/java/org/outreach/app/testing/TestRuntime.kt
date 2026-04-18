package org.outreach.app.testing

import android.os.Build
import org.outreach.app.BuildConfig

object TestRuntime {
    /**
     * True when AndroidJUnitRunner (or another non-default [android.app.Instrumentation]) drives this process.
     * Release builds never report true. Debug detection uses [ActivityThread]'s `mInstrumentation`, which is
     * [android.app.Instrumentation] for normal launches and a test runner subclass under connected tests.
     */
    /**
     * Not lazy: [OutreachApp] and other startup code may run before the test runner installs
     * non-default instrumentation; cache-on-first-read would stick to false forever.
     */
    val isInstrumentation: Boolean
        get() {
            if (!BuildConfig.DEBUG) return false
            return runCatching {
                val activityThread = Class.forName("android.app.ActivityThread")
                    .getMethod("currentActivityThread")
                    .invoke(null) ?: return@runCatching false
                val instrumentationField =
                    activityThread.javaClass.getDeclaredField("mInstrumentation")
                instrumentationField.isAccessible = true
                val inst = instrumentationField.get(activityThread) ?: return@runCatching false
                inst.javaClass.name != "android.app.Instrumentation"
            }.getOrDefault(false)
        }

    private val isRobolectric: Boolean by lazy {
        Build.FINGERPRINT.lowercase().contains("robolectric")
    }

    val isAutomation: Boolean
        get() = isInstrumentation || isRobolectric

    val skipStartupSideEffects: Boolean
        get() = isAutomation
}
