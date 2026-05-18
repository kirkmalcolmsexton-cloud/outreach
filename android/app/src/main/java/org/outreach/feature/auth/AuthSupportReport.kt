package org.outreach.feature.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import org.outreach.app.BuildConfig

/** Non-PII fields for tester/support copy-paste (release-safe). */
internal data class GoogleSignInAttemptContext(
    val resultCode: Int,
    val data: Intent?,
    val includeScopes: Boolean,
    val gmsStatusCode: Int? = null,
)

internal fun buildGoogleSignInSupportReport(
    context: Context,
    signingSha1: String,
    attempt: GoogleSignInAttemptContext,
): String {
    val extrasKeys = attempt.data?.extras?.keySet()?.joinToString(",") ?: ""
    val googleSignInStatus = attempt.data?.extras?.get("googleSignInStatus")?.toString().orEmpty()
    val flow = if (attempt.includeScopes) "scopes" else "account"
    val activityResult = when (attempt.resultCode) {
        Activity.RESULT_OK -> "OK"
        Activity.RESULT_CANCELED -> "CANCELED"
        else -> attempt.resultCode.toString()
    }
    val gmsCode = attempt.gmsStatusCode?.toString() ?: "n/a"
    val webClientSuffix = run {
        val generated = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (generated == 0) "missing"
        else context.getString(generated).trim().takeLast(12).ifEmpty { "missing" }
    }
    return buildString {
        appendLine("Outreach auth report")
        appendLine("flow=$flow")
        appendLine("gmsStatusCode=$gmsCode")
        appendLine("activityResult=$activityResult")
        appendLine("package=${context.packageName}")
        appendLine("sha1=${signingSha1.ifBlank { "unknown" }}")
        appendLine("webClientSuffix=$webClientSuffix")
        appendLine("build=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("debugBuild=${BuildConfig.DEBUG}")
        appendLine("extrasKeys=${extrasKeys.ifBlank { "none" }}")
        appendLine("googleSignInStatus=${googleSignInStatus.ifBlank { "none" }}")
    }.trimEnd()
}

internal fun signInCancelledUserMessage(report: String): String =
    "Sign-in cancelled. If you closed the Google screen with Back or dismissed it, tap Continue and finish " +
        "account selection. If you did not cancel, your Google account may need to be added as a Test user " +
        "(OAuth consent in Testing), or Play App Signing SHA-1 may be missing in Firebase — see " +
        "docs/google-oauth-checklist.md. Report:\n$report"
