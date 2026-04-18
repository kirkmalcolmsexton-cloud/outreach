package org.outreach.app.testing

import android.content.Intent

data class UiAutomationConfig(
    val skipStartupDelay: Boolean = false,
    val forceLoginGate: Boolean = false,
    val forcedAuthState: ForcedAuthState? = null,
    val homeViewMode: String? = null
) {
    enum class ForcedAuthState {
        SIGNED_IN,
        SIGNED_OUT
    }

    companion object {
        const val EXTRA_SKIP_STARTUP_DELAY = "outreach.ui_test.skip_startup_delay"
        const val EXTRA_FORCE_LOGIN_GATE = "outreach.ui_test.force_login_gate"
        const val EXTRA_FORCED_AUTH_STATE = "outreach.ui_test.force_auth_state"
        const val EXTRA_HOME_VIEW_MODE = "outreach.ui_test.home_view_mode"

        fun fromIntent(intent: Intent?): UiAutomationConfig {
            if (intent == null) return UiAutomationConfig()
            val forcedAuthState = when (intent.getStringExtra(EXTRA_FORCED_AUTH_STATE)?.lowercase()) {
                "signed_in" -> ForcedAuthState.SIGNED_IN
                "signed_out" -> ForcedAuthState.SIGNED_OUT
                else -> null
            }
            val rawViewMode = intent.getStringExtra(EXTRA_HOME_VIEW_MODE)?.lowercase()
            val homeViewMode = rawViewMode?.takeIf { it == "map" || it == "list" }
            return UiAutomationConfig(
                skipStartupDelay = intent.getBooleanExtra(EXTRA_SKIP_STARTUP_DELAY, false),
                forceLoginGate = intent.getBooleanExtra(EXTRA_FORCE_LOGIN_GATE, false),
                forcedAuthState = forcedAuthState,
                homeViewMode = homeViewMode
            )
        }
    }
}
