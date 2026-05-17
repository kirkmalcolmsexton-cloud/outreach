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
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import org.outreach.app.BuildConfig
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

data class PendingGoogleSignIn(
    val intent: Intent,
    val forScopes: Boolean,
)

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
    LaunchedEffect(Unit) {
        viewModel.pendingGoogleSignIn.collect { pending ->
            if (pending.forScopes) {
                scopeLauncher.launch(pending.intent)
            } else {
                signInLauncher.launch(pending.intent)
            }
        }
    }
    LaunchedEffect(session, hasRequiredScopes) {
        if (session && !hasRequiredScopes && !scopeConsentLaunched) {
            scopeConsentLaunched = true
            viewModel.beginGoogleSignIn(context, includeScopes = true) { intent ->
                intent?.let { scopeLauncher.launch(it) }
            }
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
                val needsScopes = session && !hasRequiredScopes
                viewModel.beginGoogleSignIn(context, includeScopes = needsScopes) { intent ->
                    if (intent == null) return@beginGoogleSignIn
                    if (needsScopes) {
                        scopeConsentLaunched = true
                        scopeLauncher.launch(intent)
                    } else {
                        signInLauncher.launch(intent)
                    }
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
    private val _pendingGoogleSignIn = MutableSharedFlow<PendingGoogleSignIn>(extraBufferCapacity = 1)
    val pendingGoogleSignIn: SharedFlow<PendingGoogleSignIn> = _pendingGoogleSignIn.asSharedFlow()
    private var developerErrorRetried = false

    /**
     * Clears cached Google Sign-In state, then returns the sign-in [Intent].
     * Stale sessions from prior app versions (different scopes/options) often surface as
     * DEVELOPER_ERROR (10) on older Play services builds.
     */
    fun beginGoogleSignIn(
        context: Context,
        includeScopes: Boolean,
        isUserInitiated: Boolean = true,
        onIntent: (Intent?) -> Unit,
    ) {
        if (isUserInitiated) {
            developerErrorRetried = false
        }
        val playServicesError = checkGooglePlayServices(context)
        if (playServicesError != null) {
            setAuthError(context, playServicesError)
            onIntent(null)
            return
        }
        val webClientId = resolveWebClientId(context)
        if (webClientId.isBlank()) {
            setMissingWebClientIdError(context)
            onIntent(null)
            return
        }
        if (!isValidWebClientId(webClientId)) {
            setAuthError(
                context,
                "Invalid OAuth Web client ID in google-services.json (expected *.apps.googleusercontent.com). " +
                    "Download a fresh file from Firebase → Project settings → Your Android app."
            )
            onIntent(null)
            return
        }
        prepareGoogleSignInIntent(context, webClientId, includeScopes, onIntent)
    }

    fun hasRequiredGoogleScopes(context: Context): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(account, *requiredScopes)
    }

    fun setMissingWebClientIdError(context: Context) {
        setAuthError(
            context,
            "Missing OAuth Web Client ID. In Firebase: Authentication → Sign-in method → Google → copy " +
                "the Web client ID. Replace app/google-services.json with a downloaded file " +
                "that includes oauth_client entries."
        )
    }

    fun onGoogleSignInActivityResult(context: Context, resultCode: Int, data: Intent?) {
        resolveGoogleSignInTask(context, resultCode, data, includeScopes = false) { account ->
            signInToFirebase(context, account)
        }
    }

    fun onGoogleScopeActivityResult(context: Context, resultCode: Int, data: Intent?) {
        resolveGoogleSignInTask(context, resultCode, data, includeScopes = true) { account ->
            if (GoogleSignIn.hasPermissions(account, *requiredScopes)) {
                _session.value = auth.currentUser != null
                _error.value = ""
            } else {
                setAuthError(
                    context,
                    "Drive and Sheets permissions are required. Tap Grant Drive & Sheets access to continue."
                )
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
        includeScopes: Boolean,
        onAccount: (GoogleSignInAccount) -> Unit,
    ) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            developerErrorRetried = false
            onAccount(task.getResult(ApiException::class.java))
        } catch (e: ApiException) {
            logAuth("GoogleSignIn.getSignedInAccountFromIntent failed", e)
            if (e.statusCode == ConnectionResult.DEVELOPER_ERROR && !developerErrorRetried) {
                developerErrorRetried = true
                retryGoogleSignInAfterDeveloperError(context, includeScopes)
                return
            }
            setAuthError(context, formatGoogleSignInFailure(context, e))
        } catch (e: Exception) {
            logAuth("GoogleSignIn.getSignedInAccountFromIntent failed", e)
            if (resultCode == Activity.RESULT_CANCELED) {
                setSignInDidNotCompleteMessage(context, data)
            } else {
                setAuthError(context, "Google sign-in failed: ${e.message ?: e.javaClass.simpleName}")
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
        setAuthError(
            context,
            if (developerError) {
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
        )
    }

    fun setSignInFailedMessage(context: Context, message: String) {
        setAuthError(context, message)
    }

    private fun formatGoogleSignInFailure(context: Context, e: Exception): String {
        val api = e as? ApiException ?: return "Google sign-in failed: ${e.message ?: e.javaClass.simpleName}"
        val code = api.statusCode
        if (code == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
            return "Sign-in cancelled"
        }
        val label = runCatching { GoogleSignInStatusCodes.getStatusCodeString(code) }.getOrElse { "unknown" }
        val webClientHint = webClientIdDebugHint(context)
        val extra = when (code) {
            com.google.android.gms.common.ConnectionResult.DEVELOPER_ERROR ->
                developerErrorGuidance(context) + webClientHint
            GoogleSignInStatusCodes.SIGN_IN_FAILED ->
                " If Google blocked access (verification / testing), add this Google account under Test users " +
                    "for the same Cloud project as your Web client ID, or finish verification."
            else -> ""
        }
        return "Google sign-in failed ($label, code $code).$extra"
    }

    private fun developerErrorGuidance(context: Context): String {
        val playServices = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo("com.google.android.gms", 0).versionName
        }.getOrNull() ?: "unknown"
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            " On Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) with Play services $playServices: " +
                "open Play Store → update Google Play services, then tap Continue again. " +
                "If it persists, verify Web client ID and every signing SHA-1 (debug, CI upload, Play App signing) — " +
                "docs/google-oauth-checklist.md."
        } else {
            " Update Google Play services ($playServices) if needed, then tap Continue again. " +
                "Verify Web client ID in google-services.json matches Firebase Auth → Google and every signing SHA-1 — " +
                "docs/google-oauth-checklist.md."
        }
    }

    private fun webClientIdDebugHint(context: Context): String {
        val id = resolveWebClientId(context)
        if (id.isBlank()) return " Web client ID: missing."
        val suffix = id.takeLast(20)
        return " Web client ID: …$suffix."
    }

    private fun retryGoogleSignInAfterDeveloperError(context: Context, includeScopes: Boolean) {
        val webClientId = resolveWebClientId(context)
        if (webClientId.isBlank()) return
        prepareGoogleSignInIntent(context, webClientId, includeScopes) { intent ->
            if (intent != null) {
                _pendingGoogleSignIn.tryEmit(PendingGoogleSignIn(intent, includeScopes))
            }
        }
    }

    /**
     * Sign out every [GoogleSignInOptions] variant we have used (legacy all-scopes + two-step flow).
     * On API 29 and below, also revoke access — older Play services often keep stale OAuth linkage.
     */
    private fun prepareGoogleSignInIntent(
        context: Context,
        webClientId: String,
        includeScopes: Boolean,
        onIntent: (Intent?) -> Unit,
    ) {
        val targetClient = GoogleSignIn.getClient(context, buildGoogleSignInOptions(webClientId, includeScopes))
        val clients = listOf(
            GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN),
            GoogleSignIn.getClient(context, buildGoogleSignInOptions(webClientId, includeScopes = false)),
            GoogleSignIn.getClient(context, buildGoogleSignInOptions(webClientId, includeScopes = true)),
        )
        val aggressiveClear = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
        if (aggressiveClear) {
            auth.signOut()
        }
        signOutGoogleSignInClients(clients, index = 0) {
            if (aggressiveClear) {
                clients.first().revokeAccess().addOnCompleteListener {
                    onIntent(targetClient.signInIntent)
                }
            } else {
                onIntent(targetClient.signInIntent)
            }
        }
    }

    private fun signOutGoogleSignInClients(
        clients: List<GoogleSignInClient>,
        index: Int,
        onComplete: () -> Unit,
    ) {
        if (index >= clients.size) {
            onComplete()
            return
        }
        clients[index].signOut().addOnCompleteListener {
            signOutGoogleSignInClients(clients, index + 1, onComplete)
        }
    }

    private fun isValidWebClientId(webClientId: String): Boolean {
        return webClientId.endsWith(".apps.googleusercontent.com") && webClientId.length > 30
    }

    private fun buildGoogleSignInOptions(webClientId: String, includeScopes: Boolean): GoogleSignInOptions {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(webClientId)
        if (includeScopes) {
            builder.requestScopes(requiredScopes.first(), *requiredScopes.drop(1).toTypedArray())
        }
        return builder.build()
    }

    private fun checkGooglePlayServices(context: Context): String? {
        val availability = GoogleApiAvailability.getInstance()
        val code = availability.isGooglePlayServicesAvailable(context)
        if (code == ConnectionResult.SUCCESS) return null
        val reason = availability.getErrorString(code)
        return if (availability.isUserResolvableError(code)) {
            "Google Play services must be updated before sign-in ($reason). Open the Play Store, update " +
                "Google Play services, then try again."
        } else {
            "Google Play services is unavailable ($reason). Google sign-in requires an up-to-date Play services app."
        }
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

    private fun signInToFirebase(context: Context, account: GoogleSignInAccount) {
        val token = account.idToken
        if (token.isNullOrBlank()) {
            setAuthError(context, "Missing Google ID token. Verify web client ID setup.")
            return
        }
        val credential = GoogleAuthProvider.getCredential(token, null)
        viewModelScope.launch {
            auth.signInWithCredential(credential).addOnSuccessListener {
                _session.value = true
                _error.value = ""
            }.addOnFailureListener { e ->
                logAuth("Firebase signInWithCredential failed", e)
                setAuthError(context, formatFirebaseAuthFailure(e))
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

    private fun setAuthError(context: Context, message: String) {
        _error.value = authError(context, message)
    }

    /** Appends device/OS and build version to every user-visible auth error. */
    private fun authError(context: Context, message: String): String {
        val debugSuffix = if (BuildConfig.DEBUG) " (debug)" else ""
        val body = message.trimEnd('.', ' ')
        return "$body. ${deviceAndOsDetails(context)}. Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})$debugSuffix."
    }

    private fun deviceAndOsDetails(context: Context): String {
        val gmsVersion = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo("com.google.android.gms", 0).versionName
        }.getOrNull() ?: "unknown"
        val manufacturer = Build.MANUFACTURER.trim().ifEmpty { "unknown" }
        val model = Build.MODEL.trim().ifEmpty { "unknown" }
        return "Device: $manufacturer $model; Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}); " +
            "Play services $gmsVersion"
    }

    private companion object {
        private const val TAG = "OutreachAuth"
    }
}
