package org.outreach.testing

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity

/**
 * Compose UI test host that keeps the window awake. Declared in **debug** manifest so the activity
 * lives in the app-under-test APK (`org.outreach.app`), not the androidTest APK — required for
 * [androidx.compose.ui.test.junit4.createAndroidComposeRule] (same process as instrumentation target).
 */
class ComposeHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
