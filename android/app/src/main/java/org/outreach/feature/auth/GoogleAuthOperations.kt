package org.outreach.feature.auth

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/** Runs the legacy sign-out / revoke sequence; callers must hold the auth mutex. */
internal suspend fun clearStaleGoogleSignInState(
    context: Context,
    webClientId: String,
    firebaseAuth: FirebaseAuth,
    aggressiveClear: Boolean,
) {
    val clients = listOf(
        GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN),
        GoogleSignIn.getClient(context, buildGoogleSignInOptions(webClientId, includeScopes = false)),
        GoogleSignIn.getClient(context, buildGoogleSignInOptions(webClientId, includeScopes = true)),
    )
    if (aggressiveClear) {
        firebaseAuth.signOut()
    }
    for (client in clients) {
        runCatching { client.signOut().await() }
    }
    if (aggressiveClear) {
        runCatching { clients.first().revokeAccess().await() }
    }
}

internal suspend fun silentGoogleSignInAccount(
    client: GoogleSignInClient,
): GoogleSignInAccount? {
    return runCatching {
        val account = client.silentSignIn().await()
        if (account.idToken.isNullOrBlank()) null else account
    }.getOrNull()
}

internal fun buildGoogleSignInOptions(webClientId: String, includeScopes: Boolean): GoogleSignInOptions {
    val requiredScopes = arrayOf(
        Scope("https://www.googleapis.com/auth/spreadsheets"),
        Scope("https://www.googleapis.com/auth/drive.file"),
        Scope("https://www.googleapis.com/auth/drive.metadata.readonly"),
    )
    val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestIdToken(webClientId)
    if (includeScopes) {
        builder.requestScopes(requiredScopes.first(), *requiredScopes.drop(1).toTypedArray())
    }
    return builder.build()
}

internal fun hasRequiredGoogleScopes(
    context: Context,
    requiredScopes: Array<Scope>,
): Boolean {
    val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
    return GoogleSignIn.hasPermissions(account, *requiredScopes)
}
