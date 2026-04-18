package org.outreach.app.testing

import android.content.Intent
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.outreach.app.BuildConfig

data class UiAutomationConfig(
    val skipStartupDelay: Boolean = false,
    val forceLoginGate: Boolean = false,
    val forcedAuthState: ForcedAuthState? = null,
    val homeViewMode: String? = null,
    /** When [AuthResolution.MOCK], `force_auth_state` drives UI without OAuth. When [AuthResolution.REAL], Firebase session only. */
    val authResolution: AuthResolution = AuthResolution.MOCK,
    /** Display line when mock mode is signed in; ignored for REAL. */
    val mockSignedInEmail: String = DEFAULT_MOCK_USER_EMAIL
) {
    enum class ForcedAuthState {
        SIGNED_IN,
        SIGNED_OUT
    }

    enum class AuthResolution {
        /** Use `force_auth_state` extras and [mockSignedInEmail] for signed-in display; no real Google session required. */
        MOCK,

        /** Ignore `force_auth_state` for UI and sign-out behavior; match real `FirebaseAuth` session (sign in on device for “signed in” UI). */
        REAL
    }

    /** When [AuthResolution.REAL], forced auth extras are ignored so UI matches Firebase. */
    val effectiveForcedAuthState: ForcedAuthState?
        get() = when (authResolution) {
            AuthResolution.REAL -> null
            AuthResolution.MOCK -> forcedAuthState
        }

    companion object {
        const val EXTRA_SKIP_STARTUP_DELAY = "outreach.ui_test.skip_startup_delay"
        const val EXTRA_FORCE_LOGIN_GATE = "outreach.ui_test.force_login_gate"
        const val EXTRA_FORCED_AUTH_STATE = "outreach.ui_test.force_auth_state"
        const val EXTRA_HOME_VIEW_MODE = "outreach.ui_test.home_view_mode"
        const val EXTRA_AUTH_RESOLUTION = "outreach.ui_test.auth_resolution"
        const val EXTRA_MOCK_USER_EMAIL = "outreach.ui_test.mock_user_email"

        /** Default profile line when mock signed-in and no `mock_user_email` extra is set (backward compatible). */
        const val DEFAULT_MOCK_USER_EMAIL = "ui-test@outreach.dev"

        fun fromIntent(intent: Intent?): UiAutomationConfig {
            return parseIntentExtras(intent).mergeInstrumentationRunnerArgumentsIfPresent()
        }

        private fun parseIntentExtras(intent: Intent?): UiAutomationConfig {
            if (intent == null) return UiAutomationConfig()
            val forcedAuthState = parseForcedAuthState(intent.getStringExtra(EXTRA_FORCED_AUTH_STATE))
            val rawViewMode = intent.getStringExtra(EXTRA_HOME_VIEW_MODE)?.lowercase()
            val homeViewMode = rawViewMode?.takeIf { it == "map" || it == "list" }
            val authResolution = when (intent.getStringExtra(EXTRA_AUTH_RESOLUTION)?.lowercase()) {
                "real" -> AuthResolution.REAL
                else -> AuthResolution.MOCK
            }
            val mockEmailRaw = intent.getStringExtra(EXTRA_MOCK_USER_EMAIL)?.trim().orEmpty()
            val mockSignedInEmail = mockEmailRaw.ifEmpty { DEFAULT_MOCK_USER_EMAIL }
            return UiAutomationConfig(
                skipStartupDelay = intent.getBooleanExtra(EXTRA_SKIP_STARTUP_DELAY, false),
                forceLoginGate = intent.getBooleanExtra(EXTRA_FORCE_LOGIN_GATE, false),
                forcedAuthState = forcedAuthState,
                homeViewMode = homeViewMode,
                authResolution = authResolution,
                mockSignedInEmail = mockSignedInEmail
            )
        }

        private fun parseForcedAuthState(raw: String?): ForcedAuthState? = when (raw?.lowercase()) {
            "signed_in" -> ForcedAuthState.SIGNED_IN
            "signed_out" -> ForcedAuthState.SIGNED_OUT
            else -> null
        }

        /**
         * Gradle / CLI: `-Pandroid.testInstrumentationRunnerArguments.<key>=<value>` merges over activity intent extras.
         */
        private fun UiAutomationConfig.mergeInstrumentationRunnerArgumentsIfPresent(): UiAutomationConfig {
            val bundle = instrumentationArgumentsBundle() ?: return this
            if (bundle.keySet().isEmpty()) return this
            var next = this
            if (bundle.containsKey(EXTRA_MOCK_USER_EMAIL)) {
                val v = bundle.getString(EXTRA_MOCK_USER_EMAIL)?.trim().orEmpty()
                if (v.isNotEmpty()) next = next.copy(mockSignedInEmail = v)
            }
            if (bundle.containsKey(EXTRA_AUTH_RESOLUTION)) {
                next = next.copy(
                    authResolution = when (bundle.getString(EXTRA_AUTH_RESOLUTION)?.lowercase()) {
                        "real" -> AuthResolution.REAL
                        else -> AuthResolution.MOCK
                    }
                )
            }
            if (bundle.containsKey(EXTRA_FORCED_AUTH_STATE)) {
                next = next.copy(forcedAuthState = parseForcedAuthState(bundle.getString(EXTRA_FORCED_AUTH_STATE)))
            }
            if (bundle.containsKey(EXTRA_HOME_VIEW_MODE)) {
                val raw = bundle.getString(EXTRA_HOME_VIEW_MODE)?.lowercase()
                val vm = raw?.takeIf { it == "map" || it == "list" }
                if (vm != null) next = next.copy(homeViewMode = vm)
            }
            if (bundle.containsKey(EXTRA_SKIP_STARTUP_DELAY)) {
                next = next.copy(skipStartupDelay = bundle.readBoolish(EXTRA_SKIP_STARTUP_DELAY))
            }
            if (bundle.containsKey(EXTRA_FORCE_LOGIN_GATE)) {
                next = next.copy(forceLoginGate = bundle.readBoolish(EXTRA_FORCE_LOGIN_GATE))
            }
            return next
        }

        private fun Bundle.readBoolish(key: String): Boolean {
            val s = getString(key)?.lowercase()
            if (s != null) return s == "true" || s == "1"
            return getBoolean(key, false)
        }

        /** Same Bundle as `InstrumentationRegistry.getArguments()` in tests; skipped when not instrumented. */
        private fun instrumentationArgumentsBundle(): Bundle? {
            if (!BuildConfig.DEBUG || !TestRuntime.isInstrumentation) return null
            return runCatching { InstrumentationRegistry.getArguments() }.getOrNull()
        }
    }
}
