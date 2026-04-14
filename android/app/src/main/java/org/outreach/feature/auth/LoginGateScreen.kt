package org.outreach.feature.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import org.outreach.app.BuildConfig
import org.outreach.core.debug.AgentDebugLogger
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

@Composable
fun LoginGateScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val viewModel = remember { AuthViewModel(FirebaseAuth.getInstance()) }
    val session by viewModel.session.collectAsState()
    val errorMessage by viewModel.error.collectAsState()
    // #region agent log
    if (BuildConfig.DEBUG) {
        AgentDebugLogger.log(
            runId = "pre-fix",
            hypothesisId = "H4",
            location = "LoginGateScreen.kt:composable",
            message = "LoginGateScreen state snapshot",
            data = mapOf(
                "sessionState" to session,
                "hasError" to errorMessage.isNotBlank(),
                "hasCurrentUser" to (FirebaseAuth.getInstance().currentUser != null)
            )
        )
    }
    // #endregion
    LaunchedEffect(session) {
        if (session) {
            // #region agent log
            if (BuildConfig.DEBUG) {
                AgentDebugLogger.log(
                    runId = "sheet-debug",
                    hypothesisId = "S6",
                    location = "LoginGateScreen.kt:sessionEffect",
                    message = "Session true, triggering onSignedIn effect",
                    data = mapOf(
                        "sessionState" to session,
                        "hasCurrentUser" to (FirebaseAuth.getInstance().currentUser != null)
                    )
                )
            }
            // #endregion
            onSignedIn()
        }
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // #region agent log
        if (BuildConfig.DEBUG) {
            AgentDebugLogger.log(
                runId = "pre-fix",
                hypothesisId = "H11",
                location = "LoginGateScreen.kt:launcherCallback",
                message = "Activity result callback received",
                data = mapOf(
                    "resultCode" to result.resultCode,
                    "hasDataIntent" to (result.data != null)
                )
            )
        }
        // #endregion
        when (result.resultCode) {
            Activity.RESULT_OK -> viewModel.onGoogleSignInIntentResult(result.data)
            Activity.RESULT_CANCELED -> viewModel.setSignInDidNotCompleteMessage(context, result.data)
            else -> viewModel.setSignInFailedMessage("Unexpected result (${result.resultCode}).")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (session) {
            Text("Signing you in...")
            return@Column
        }
        Text("Sign in with Google SSO")
        if (errorMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMessage)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val intent = viewModel.buildGoogleSignInIntent(context)
                // #region agent log
                if (BuildConfig.DEBUG) {
                    AgentDebugLogger.log(
                        runId = "pre-fix",
                        hypothesisId = "H12",
                        location = "LoginGateScreen.kt:continueButton",
                        message = "Continue with Google tapped",
                        data = mapOf(
                            "hasSignInIntent" to (intent != null)
                        )
                    )
                }
                // #endregion
                if (intent != null) {
                    launcher.launch(intent)
                } else {
                    viewModel.setMissingWebClientIdError()
                }
            }
        ) {
            Text("Continue with Google")
        }
    }
}

class AuthViewModel(
    private val auth: FirebaseAuth
) : ViewModel() {
    private val _session = MutableStateFlow(auth.currentUser != null)
    val session: StateFlow<Boolean> = _session.asStateFlow()
    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error.asStateFlow()

    /** @return null if the Web client ID is missing — calling [GoogleSignInOptions.Builder.requestIdToken] with a blank id can crash Play services. */
    fun buildGoogleSignInIntent(context: Context): Intent? {
        val webClientId = resolveWebClientId(context)
        if (webClientId.isBlank()) {
            return null
        }
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(webClientId)
            .requestScopes(
                com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/spreadsheets"),
                com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.file")
            )
            .build()
        return GoogleSignIn.getClient(context, options).signInIntent
    }

    fun setMissingWebClientIdError() {
        _error.value =
            "Missing OAuth Web Client ID. In Firebase: Authentication → Sign-in method → Google → copy " +
                "the Web client ID. Paste it into res/values/strings.xml as google_web_client_id " +
                "(replace PASTE_WEB_CLIENT_ID), or replace app/google-services.json with a downloaded file " +
                "that includes oauth_client entries."
    }

    fun onGoogleSignInIntentResult(data: Intent?) {
        // #region agent log
        if (BuildConfig.DEBUG) {
            AgentDebugLogger.log(
                runId = "pre-fix",
                hypothesisId = "H13",
                location = "LoginGateScreen.kt:onGoogleSignInIntentResult",
                message = "Parsing Google sign-in intent result",
                data = mapOf(
                    "hasDataIntent" to (data != null),
                    "extrasKeys" to (data?.extras?.keySet()?.joinToString(",") ?: "none")
                )
            )
        }
        // #endregion
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        task.addOnSuccessListener { account ->
            // #region agent log
            if (BuildConfig.DEBUG) {
                AgentDebugLogger.log(
                    runId = "pre-fix",
                    hypothesisId = "H13",
                    location = "LoginGateScreen.kt:onGoogleSignInIntentResultSuccess",
                    message = "Google sign-in task succeeded",
                    data = mapOf(
                        "hasAccount" to true,
                        "hasIdToken" to !account.idToken.isNullOrBlank(),
                        "hasEmail" to !account.email.isNullOrBlank()
                    )
                )
            }
            // #endregion
            signInToFirebase(account)
        }.addOnFailureListener { e ->
            logAuth("GoogleSignIn.getSignedInAccountFromIntent failed", e)
            // #region agent log
            if (BuildConfig.DEBUG) {
                AgentDebugLogger.log(
                    runId = "pre-fix",
                    hypothesisId = "H13",
                    location = "LoginGateScreen.kt:onGoogleSignInIntentResultFailure",
                    message = "Google sign-in task failed",
                    data = mapOf(
                        "exceptionType" to e.javaClass.name,
                        "exceptionMessage" to (e.message ?: "none")
                    )
                )
            }
            // #endregion
            _error.value = formatGoogleSignInFailure(e)
        }
    }

    fun setSignInDidNotCompleteMessage(context: Context, data: Intent?) {
        val hasFirebaseUser = auth.currentUser != null
        val hasGoogleAccount = GoogleSignIn.getLastSignedInAccount(context) != null
        val extrasKeys = data?.extras?.keySet()?.joinToString(",").orEmpty()
        val googleSignInStatusObj = data?.extras?.get("googleSignInStatus")
        val googleSignInStatus = googleSignInStatusObj?.toString().orEmpty()
        val googleSignInStatusClass = googleSignInStatusObj?.javaClass?.name.orEmpty()
        val extrasSnapshot = data?.extras?.keySet()
            ?.associateWith { key -> data.extras?.get(key)?.toString().orEmpty().take(500) }
            .orEmpty()
        val developerError = googleSignInStatus.contains("DEVELOPER_ERROR", ignoreCase = true)
        val packageName = context.packageName
        val signingSha1 = resolveAppSigningSha1(context)
        val debugSuffix =
            " Debug: package=$packageName, sha1=${if (signingSha1.isBlank()) "unknown" else signingSha1}, firebaseUser=$hasFirebaseUser, googleAccount=$hasGoogleAccount, extrasKeys=${if (extrasKeys.isBlank()) "none" else extrasKeys}, status=${if (googleSignInStatus.isBlank()) "none" else googleSignInStatus}."
        // #region agent log
        if (BuildConfig.DEBUG) {
            AgentDebugLogger.log(
                runId = "pre-fix",
                hypothesisId = "H7",
                location = "LoginGateScreen.kt:setSignInDidNotCompleteMessage",
                message = "Google sign-in returned RESULT_CANCELED",
                data = mapOf(
                    "hasFirebaseUser" to hasFirebaseUser,
                    "hasGoogleAccount" to hasGoogleAccount,
                    "extrasKeys" to (if (extrasKeys.isBlank()) "none" else extrasKeys),
                    "googleSignInStatus" to (if (googleSignInStatus.isBlank()) "none" else googleSignInStatus),
                    "googleSignInStatusClass" to (if (googleSignInStatusClass.isBlank()) "none" else googleSignInStatusClass),
                    "extrasSnapshot" to extrasSnapshot.toString(),
                    "packageName" to packageName,
                    "signingSha1" to (if (signingSha1.isBlank()) "unknown" else signingSha1)
                )
            )
        }
        // #endregion
        _error.value = if (developerError) {
            "Google sign-in failed with DEVELOPER_ERROR. Fix OAuth config: ensure package name is $packageName, " +
                "add this build's SHA-1 to the Android OAuth client, and use matching Firebase/google-services.json.$debugSuffix"
        } else {
            "Sign-in did not finish (back/cancel or Google blocked the app). If you saw a Google " +
                "verification/testing message, add this Google account under Test users for the Cloud " +
                "project that owns your Web client ID.$debugSuffix"
        }
    }

    fun setSignInFailedMessage(message: String) {
        _error.value = message
    }

    private fun formatGoogleSignInFailure(e: Exception): String {
        val api = e as? ApiException ?: return "Google sign-in failed: ${e.message ?: e.javaClass.simpleName}"
        val code = api.statusCode
        if (code == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
            return "Sign-in cancelled."
        }
        val label = runCatching { GoogleSignInStatusCodes.getStatusCodeString(code) }.getOrElse { "unknown" }
        val extra = when (code) {
            com.google.android.gms.common.ConnectionResult.DEVELOPER_ERROR ->
                " Add the app’s debug/release SHA-1 to the Android OAuth client in Google Cloud Console."
            GoogleSignInStatusCodes.SIGN_IN_FAILED ->
                " If Google blocked access (verification / testing), add this Google account under Test users " +
                    "for the same Cloud project as your Web client ID, or finish verification."
            else -> ""
        }
        return "Google sign-in failed ($label, code $code).$extra"
    }

    private fun formatFirebaseAuthFailure(e: Exception): String {
        val fe = e as? FirebaseAuthException
        return if (fe != null) {
            val msg = fe.message?.takeIf { it.isNotBlank() } ?: fe.localizedMessage.orEmpty()
            "Authentication failed (${fe.errorCode}): $msg"
        } else {
            "Authentication failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun logAuth(message: String, e: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (e != null) Log.e(TAG, message, e) else Log.w(TAG, message)
        }
    }

    private fun signInToFirebase(account: GoogleSignInAccount) {
        val token = account.idToken
        // #region agent log
        if (BuildConfig.DEBUG) {
            AgentDebugLogger.log(
                runId = "pre-fix",
                hypothesisId = "H14",
                location = "LoginGateScreen.kt:signInToFirebase",
                message = "Attempting Firebase credential sign-in",
                data = mapOf(
                    "hasIdToken" to !token.isNullOrBlank()
                )
            )
        }
        // #endregion
        if (token.isNullOrBlank()) {
            _error.value = "Missing Google ID token. Verify web client ID setup."
            return
        }
        val credential = GoogleAuthProvider.getCredential(token, null)
        viewModelScope.launch {
            auth.signInWithCredential(credential).addOnSuccessListener {
                // #region agent log
                if (BuildConfig.DEBUG) {
                    AgentDebugLogger.log(
                        runId = "pre-fix",
                        hypothesisId = "H5",
                        location = "LoginGateScreen.kt:firebaseSignInSuccess",
                        message = "Firebase signInWithCredential succeeded",
                        data = mapOf(
                            "hasCurrentUserAfterSignIn" to (auth.currentUser != null)
                        )
                    )
                }
                // #endregion
                _session.value = true
                _error.value = ""
            }.addOnFailureListener { e ->
                logAuth("Firebase signInWithCredential failed", e)
                _error.value = formatFirebaseAuthFailure(e)
            }
        }
    }

    private fun resolveWebClientId(context: Context): String {
        val pkg = context.packageName
        val manual = context.resources.getIdentifier("google_web_client_id", "string", pkg)
        if (manual != 0) {
            val v = context.getString(manual).trim()
            if (v.isNotEmpty() && v != PLACEHOLDER_WEB_CLIENT_ID) {
                return v
            }
        }
        val generated = context.resources.getIdentifier("default_web_client_id", "string", pkg)
        if (generated != 0) {
            val v = context.getString(generated).trim()
            if (v.isNotEmpty()) return v
        }
        return ""
    }

    private fun resolveAppSigningSha1(context: Context): String {
        return runCatching {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pkgInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val info = pkgInfo.signingInfo
                if (info != null && info.hasMultipleSigners()) info.apkContentsSigners else info?.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                ).signatures
            }
            val cert = signatures?.firstOrNull()?.toByteArray() ?: return ""
            val digest = MessageDigest.getInstance("SHA-1").digest(cert)
            digest.joinToString(":") { b -> "%02X".format(b) }
        }.getOrDefault("")
    }

    private companion object {
        private const val TAG = "OutreachAuth"

        /** Same value as default in res/values/strings.xml — replace with your real Web client ID. */
        private const val PLACEHOLDER_WEB_CLIENT_ID = "PASTE_WEB_CLIENT_ID"
    }
}
