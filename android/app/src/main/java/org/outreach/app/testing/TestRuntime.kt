package org.outreach.app.testing

import android.os.Build

object TestRuntime {
    private val isInstrumentation: Boolean by lazy {
        runCatching {
            Class.forName("androidx.test.platform.app.InstrumentationRegistry")
            true
        }.getOrDefault(false)
    }

    val skipStartupSideEffects: Boolean
        get() = isInstrumentation || Build.FINGERPRINT.lowercase().contains("robolectric")
}
