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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import org.outreach.app.BuildConfig
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
    val hasRequiredScopes = viewModel.hasRequiredGoogleScopes(context)
    LaunchedEffect(session, hasRequiredScopes) {
        if (session && hasRequiredScopes) {
            onSignedIn()
        }
    }
    var scopeConsentLaunched by remember { mutableStateOf(false) }
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onGoogleSignInActivityResult(context, result.resultCode, result.data)
    }
    val scopeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onGoogleScopeActivityResult(context, result.resultCode, result.data)
    }
    LaunchedEffect(session, hasRequiredScopes) {
        if (session && !hasRequiredScopes && !scopeConsentLaunched) {
            scopeConsentLaunched = true
            viewModel.buildScopeConsentIntent(context)?.let { scopeLauncher.launch(it) }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (session && hasRequiredScopes) {
            Text("Signing you in...")
            return@Column
        }
        if (session && !hasRequiredScopes) {
            Text("Google Drive permission needed")
            Spacer(modifier = Modifier.height(8.dp))
            Text("Continue once to grant Drive access for picker-based sheet selection.")
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            Text("Sign in with Google SSO")
        }
        if (errorMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMessage)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                scopeConsentLaunched = false
                val intent = if (session && !hasRequiredScopes) {
                    viewModel.buildScopeConsentIntent(context)
                } else {
                    viewModel.buildGoogleSignInIntent(context)
                }
                if (intent != null) {
                    if (session && !hasRequiredScopes) {
                        scopeConsentLaunched = true
                        scopeLauncher.launch(intent)
                    } else {
                        signInLauncher.launch(intent)
                    }
                } else {
                    viewModel.setMissingWebClientIdError()
                }
            }
        ) {
            Text(
                if (session && !hasRequiredScopes) {
                    "Grant Drive & Sheets access"
                } else {
                    "Continue with Google"
                }
            )
        }
    }
}

class AuthViewModel(
    private val auth: FirebaseAuth
) : ViewModel() {
    private val requiredScopes = arrayOf(
        Scope("https://www.googleapis.com/auth/spreadsheets"),
        Scope("https://www.googleapis.com/auth/drive.file"),
        Scope("https://www.googleapis.com/auth/drive.metadata.readonly")
    )
    private val _session = MutableStateFlow(auth.currentUser != null)
    val session: StateFlow<Boolean> = _session.asStateFlow()
    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error.asStateFlow()

    /**
     * Sign-in only (email + ID token). Sheets/Drive scopes are requested in a second step via
     * [buildScopeConsentIntent] so SignInHubActivity is less likely to hang on slow devices when
     * opening the combined account + scope UI (see Google issue 178183308).
     */
    fun buildGoogleSignInIntent(context: Context): Intent? {
        return buildGoogleSignInClientIntent(context, includeScopes = false)
    }

    /** Second-step consent for Sheets + Drive scopes after Google/Firebase account exists. */
    fun buildScopeConsentIntent(context: Context): Intent? {
        return buildGoogleSignInClientIntent(context, includeScopes = true)
    }

    private fun buildGoogleSignInClientIntent(context: Context, includeScopes: Boolean): Intent? {
        val webClientId = resolveWebClientId(context)
        if (webClientId.isBlank()) {
            return null
        }
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(webClientId)
        if (includeScopes) {
            builder.requestScopes(requiredScopes.first(), *requiredScopes.drop(1).toTypedArray())
        }
        return GoogleSignIn.getClient(context, builder.build()).signInIntent
    }

    fun hasRequiredGoogleScopes(context: Context): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(account, *requiredScopes)
    }

    fun setMissingWebClientIdError() {
        _error.value =
            "Missing OAuth Web Client ID. In Firebase: Authentication → Sign-in method → Google → copy " +
                "the Web client ID. Replace app/google-services.json with a downloaded file " +
                "that includes oauth_client entries."
    }

    fun onGoogleSignInActivityResult(context: Context, resultCode: Int, data: Intent?) {
        resolveGoogleSignInTask(context, resultCode, data) { account ->
            signInToFirebase(account)
        }
    }

    fun onGoogleScopeActivityResult(context: Context, resultCode: Int, data: Intent?) {
        resolveGoogleSignInTask(context, resultCode, data) { account ->
            if (GoogleSignIn.hasPermissions(account, *requiredScopes)) {
                _session.value = auth.currentUser != null
                _error.value = ""
            } else {
                _error.value =
                    "Drive and Sheets permissions are required. Tap Grant Drive & Sheets access to continue."
            }
        }
    }

    /**
     * Always parse the result [Intent] — Google Sign-In can return [Activity.RESULT_CANCELED] while
     * still encoding a real [ApiException] (for example DEVELOPER_ERROR) in the task.
     */
    private fun resolveGoogleSignInTask(
        context: Context,
        resultCode: Int,
        data: Intent?,
        onAccount: (GoogleSignInAccount) -> Unit,
    ) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            onAccount(task.getResult(ApiException::class.java))
        } catch (e: ApiException) {
            logAuth("GoogleSignIn.getSignedInAccountFromIntent failed", e)
            _error.value = formatGoogleSignInFailure(e)
        } catch (e: Exception) {
            logAuth("GoogleSignIn.getSignedInAccountFromIntent failed", e)
            if (resultCode == Activity.RESULT_CANCELED) {
                setSignInDidNotCompleteMessage(context, data)
            } else {
                _error.value = "Google sign-in failed: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun setSignInDidNotCompleteMessage(context: Context, data: Intent?) {
        val hasFirebaseUser = auth.currentUser != null
        val hasGoogleAccount = GoogleSignIn.getLastSignedInAccount(context) != null
        val extrasKeys = data?.extras?.keySet()?.joinToString(",").orEmpty()
        val googleSignInStatusObj = data?.extras?.get("googleSignInStatus")
        val googleSignInStatus = googleSignInStatusObj?.toString().orEmpty()
        val developerError = googleSignInStatus.contains("DEVELOPER_ERROR", ignoreCase = true)
        val packageName = context.packageName
        val signingSha1 = resolveAppSigningSha1(context)
        val debugSuffix =
            " Debug: package=$packageName, sha1=${if (signingSha1.isBlank()) "unknown" else signingSha1}, firebaseUser=$hasFirebaseUser, googleAccount=$hasGoogleAccount, extrasKeys=${if (extrasKeys.isBlank()) "none" else extrasKeys}, status=${if (googleSignInStatus.isBlank()) "none" else googleSignInStatus}."
        _error.value = if (developerError) {
            "Google sign-in did not finish (Sign-In reported DEVELOPER_ERROR). If SHA-1 is already registered " +
                "in Firebase and Google Cloud, this is often a false alarm from the combined account + scope screen " +
                "on slow devices — update the app (two-step sign-in), tap Continue again, and ensure Google Play " +
                "services is up to date. Otherwise verify Web client ID in google-services.json matches Firebase " +
                "Auth → Google → Web client ID for $packageName.$debugSuffix"
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
                " If SHA-1 is already in Firebase and Google Cloud, retry after updating the app; otherwise register " +
                    "every signing cert you ship (debug, CI upload, Play App signing) — docs/google-oauth-checklist.md."
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
            firebaseAuthMaybeApiKeyHint(msg)
                ?: "Authentication failed (${fe.errorCode}): $msg"
        } else {
            val msg = e.message?.takeIf { it.isNotBlank() } ?: e.localizedMessage.orEmpty()
            firebaseAuthMaybeApiKeyHint(msg)
                ?: "Authentication failed: ${msg.ifBlank { e.javaClass.simpleName }}"
        }
    }

    /** When Firebase rejects the packaged Android API key (google-services.json), nudge toward the right credential. */
    private fun firebaseAuthMaybeApiKeyHint(fullMessage: String): String? {
        val lower = fullMessage.lowercase()
        val apiKeyRejected =
            lower.contains("api key") &&
                (
                    lower.contains("not valid") ||
                        lower.contains("invalid") ||
                        lower.contains("expired")
                    )
        return if (!apiKeyRejected) {
            null
        } else {
            "Authentication failed (Firebase Android API key): $fullMessage — This key comes from app/google-services.json " +
                "(current_key), not MAPS_API_KEY. Renew or replace the key in Google Cloud → Credentials if expired; " +
                "download a fresh google-services.json from Firebase → Project settings → Your Android app, merge into " +
                "consolidated secrets, run android/scripts/setup-secrets.sh, and ensure API key restrictions allow " +
                "Firebase / Identity Toolkit for this package and SHA-1."
        }
    }

    private fun logAuth(message: String, e: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (e != null) Log.e(TAG, message, e) else Log.w(TAG, message)
        }
    }

    private fun signInToFirebase(account: GoogleSignInAccount) {
        val token = account.idToken
        if (token.isNullOrBlank()) {
            _error.value = "Missing Google ID token. Verify web client ID setup."
            return
        }
        val credential = GoogleAuthProvider.getCredential(token, null)
        viewModelScope.launch {
            auth.signInWithCredential(credential).addOnSuccessListener {
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
                pkgInfo.signingInfo?.apkContentsSigners
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
    }
}
