package org.outreach.app.testing

import android.os.Build

object TestRuntime {
    val isInstrumentation: Boolean by lazy {
        runCatching {
            Class.forName("androidx.test.platform.app.InstrumentationRegistry")
            true
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
