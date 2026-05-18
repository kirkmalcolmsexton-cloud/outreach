package org.outreach.feature.auth

import android.content.Intent
import android.os.Build
import com.google.android.gms.auth.api.signin.GoogleSignInAccount

/** Whether to run the legacy multi-client sign-out cascade before showing Google UI. */
internal fun shouldClearGoogleStateBeforeSignIn(
    sdkInt: Int,
    forceClear: Boolean,
): Boolean {
    if (forceClear) return true
    // Pre-Android 11: stale OAuth linkage is common; keep aggressive clear.
    return sdkInt < Build.VERSION_CODES.R
}

internal sealed class GoogleSignInPrepareResult {
    data class Interactive(val intent: Intent) : GoogleSignInPrepareResult()
    data class SilentAccount(val account: GoogleSignInAccount) : GoogleSignInPrepareResult()
}
