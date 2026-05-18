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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import org.outreach.app.BuildConfig
import org.outreach.debug.AgentDebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
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
    val supportReport by viewModel.supportReport.collectAsState()
    val clipboard = LocalClipboardManager.current
    val signInBusy by viewModel.isSignInInProgress.collectAsState()
    val hasRequiredScopes by viewModel.googleScopesGranted.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.refreshGoogleScopeState(context)
    }
    LaunchedEffect(session, hasRequiredScopes) {
        if (session && hasRequiredScopes) {
            onSignedIn()
        }
    }
    var scopeConsentLaunched by remember { mutableStateOf(false) }
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // #region agent log
        AgentDebugLog.emit(
            hypothesisId = "D",
            location = "LoginGateScreen:signInLauncher",
            message = "sign-in activity result",
            data = mapOf(
                "resultCode" to result.resultCode,
                "dataNull" to (result.data == null),
            ),
        )
        // #endregion
        viewModel.onGoogleSignInActivityResult(context, result.resultCode, result.data)
    }
    val scopeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // #region agent log
        AgentDebugLog.emit(
            hypothesisId = "E",
            location = "LoginGateScreen:scopeLauncher",
            message = "scope activity result",
            data = mapOf(
                "resultCode" to result.resultCode,
                "dataNull" to (result.data == null),
            ),
        )
        // #endregion
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
            if (supportReport.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(supportReport)) }
                ) {
                    Text("Copy report for support")
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            enabled = !signInBusy,
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
    private val _supportReport = MutableStateFlow("")
    val supportReport: StateFlow<String> = _supportReport.asStateFlow()
    private val _pendingGoogleSignIn = MutableSharedFlow<PendingGoogleSignIn>(extraBufferCapacity = 1)
    val pendingGoogleSignIn: SharedFlow<PendingGoogleSignIn> = _pendingGoogleSignIn.asSharedFlow()
    private val _signInInProgress = MutableStateFlow(false)
    val isSignInInProgress: StateFlow<Boolean> = _signInInProgress.asStateFlow()
    private val _googleScopesGranted = MutableStateFlow(false)
    val googleScopesGranted: StateFlow<Boolean> = _googleScopesGranted.asStateFlow()
    private val authMutex = Mutex()
    private var authGeneration = 0
    private var developerErrorRetried = false

    fun refreshGoogleScopeState(context: Context) {
        _googleScopesGranted.value = hasRequiredGoogleScopes(context, requiredScopes)
    }

    /**
     * Starts Google sign-in. Prep runs under a mutex with a generation token so stale GMS callbacks
     * cannot launch UI after a newer attempt. Sign-in stays "in progress" until Firebase finishes.
     */
    fun beginGoogleSignIn(
        context: Context,
        includeScopes: Boolean,
        isUserInitiated: Boolean = true,
        forceClearState: Boolean = false,
        onIntent: (Intent?) -> Unit,
    ) {
        if (_signInInProgress.value) {
            return
        }
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
        val generation = startAuthOperation()
        val clearState = shouldClearGoogleStateBeforeSignIn(Build.VERSION.SDK_INT, forceClearState)
        // #region agent log
        AgentDebugLog.emit(
            hypothesisId = "B",
            location = "AuthViewModel:beginGoogleSignIn",
            message = "starting sign-in prep",
            data = mapOf(
                "generation" to generation,
                "includeScopes" to includeScopes,
                "clearState" to clearState,
                "webClientIdSuffix" to webClientId.takeLast(12),
            ),
        )
        // #endregion
        viewModelScope.launch {
            try {
                val result = authMutex.withLock {
                    if (isStaleAuthOperation(generation)) return@launch
                    prepareGoogleSignInResult(context, webClientId, includeScopes, clearState)
                }
                if (isStaleAuthOperation(generation)) return@launch
                dispatchPrepareResult(context, webClientId, includeScopes, generation, result, onIntent)
            } catch (e: Exception) {
                logAuth("prepareGoogleSignIn failed", e)
                if (!isStaleAuthOperation(generation)) {
                    setAuthError(context, "Google sign-in could not start: ${e.message ?: e.javaClass.simpleName}")
                    withContext(Dispatchers.Main) { onIntent(null) }
                }
            }
        }
    }

    private suspend fun dispatchPrepareResult(
        context: Context,
        webClientId: String,
        includeScopes: Boolean,
        generation: Int,
        result: GoogleSignInPrepareResult,
        onIntent: (Intent?) -> Unit,
    ) {
        when (result) {
            is GoogleSignInPrepareResult.SilentAccount -> {
                if (includeScopes) {
                    if (GoogleSignIn.hasPermissions(result.account, *requiredScopes)) {
                        _session.value = auth.currentUser != null
                        _error.value = ""
                        _supportReport.value = ""
                        refreshGoogleScopeState(context)
                        finishAuthOperation()
                    } else if (!isStaleAuthOperation(generation)) {
                        val intent = GoogleSignIn.getClient(
                            context,
                            buildGoogleSignInOptions(webClientId, includeScopes = true),
                        ).signInIntent
                        withContext(Dispatchers.Main) { onIntent(intent) }
                    }
                } else {
                    signInToFirebase(context, result.account, generation)
                }
            }
            is GoogleSignInPrepareResult.Interactive -> {
                withContext(Dispatchers.Main) { onIntent(result.intent) }
            }
        }
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
                _supportReport.value = ""
                refreshGoogleScopeState(context)
                finishAuthOperation()
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
        // #region agent log
        val extrasKeys = data?.extras?.keySet()?.joinToString(",") ?: ""
        AgentDebugLog.emit(
            hypothesisId = "D",
            location = "AuthViewModel:resolveGoogleSignInTask:entry",
            message = "resolving sign-in result",
            data = mapOf(
                "resultCode" to resultCode,
                "includeScopes" to includeScopes,
                "dataNull" to (data == null),
                "extrasKeyCount" to extrasKeys.split(',').filter { it.isNotEmpty() }.size,
            ),
        )
        // #endregion
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            developerErrorRetried = false
            val account = task.getResult(ApiException::class.java)
            // #region agent log
            AgentDebugLog.emit(
                hypothesisId = "A",
                location = "AuthViewModel:resolveGoogleSignInTask:success",
                message = "Google account obtained",
                data = mapOf(
                    "includeScopes" to includeScopes,
                    "hasIdToken" to !account.idToken.isNullOrBlank(),
                    "hasEmail" to !account.email.isNullOrBlank(),
                ),
            )
            // #endregion
            onAccount(account)
        } catch (e: ApiException) {
            // #region agent log
            AgentDebugLog.emit(
                hypothesisId = "A",
                location = "AuthViewModel:resolveGoogleSignInTask:ApiException",
                message = "Google sign-in ApiException",
                data = mapOf(
                    "statusCode" to e.statusCode,
                    "resultCode" to resultCode,
                    "includeScopes" to includeScopes,
                    "isSignInCancelled" to (e.statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED),
                    "isDeveloperError" to (e.statusCode == ConnectionResult.DEVELOPER_ERROR),
                ),
            )
            // #endregion
            logAuth("GoogleSignIn.getSignedInAccountFromIntent failed", e)
            if (e.statusCode == ConnectionResult.DEVELOPER_ERROR && !developerErrorRetried) {
                developerErrorRetried = true
                retryGoogleSignInAfterDeveloperError(context, includeScopes)
                return
            }
            val attempt = GoogleSignInAttemptContext(resultCode, data, includeScopes, e.statusCode)
            val report = buildGoogleSignInSupportReport(context, resolveAppSigningSha1(context), attempt)
            logAuth("Google sign-in failed: $report", e)
            setAuthError(
                context,
                formatGoogleSignInFailure(context, e, attempt, report),
                supportReport = report,
            )
            finishAuthOperation()
        } catch (e: Exception) {
            logAuth("GoogleSignIn.getSignedInAccountFromIntent failed", e)
            if (resultCode == Activity.RESULT_CANCELED) {
                setSignInDidNotCompleteMessage(context, data, resultCode, includeScopes)
            } else {
                setAuthError(context, "Google sign-in failed: ${e.message ?: e.javaClass.simpleName}")
            }
            finishAuthOperation()
        }
    }

    private fun startAuthOperation(): Int {
        authGeneration += 1
        _signInInProgress.value = true
        return authGeneration
    }

    private fun isStaleAuthOperation(generation: Int): Boolean = generation != authGeneration

    private fun finishAuthOperation() {
        _signInInProgress.value = false
    }

    private suspend fun prepareGoogleSignInResult(
        context: Context,
        webClientId: String,
        includeScopes: Boolean,
        clearState: Boolean,
    ): GoogleSignInPrepareResult {
        val client = GoogleSignIn.getClient(context, buildGoogleSignInOptions(webClientId, includeScopes))
        if (clearState) {
            clearStaleGoogleSignInState(
                context = context,
                webClientId = webClientId,
                firebaseAuth = auth,
                aggressiveClear = Build.VERSION.SDK_INT < Build.VERSION_CODES.R,
            )
            return GoogleSignInPrepareResult.Interactive(client.signInIntent)
        }
        if (includeScopes) {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null && GoogleSignIn.hasPermissions(account, *requiredScopes)) {
                return GoogleSignInPrepareResult.SilentAccount(account)
            }
            return GoogleSignInPrepareResult.Interactive(client.signInIntent)
        }
        val silentAccount = silentGoogleSignInAccount(client)
        return if (silentAccount != null) {
            GoogleSignInPrepareResult.SilentAccount(silentAccount)
        } else {
            GoogleSignInPrepareResult.Interactive(client.signInIntent)
        }
    }

    fun setSignInDidNotCompleteMessage(
        context: Context,
        data: Intent?,
        resultCode: Int = Activity.RESULT_CANCELED,
        includeScopes: Boolean = false,
    ) {
        val hasFirebaseUser = auth.currentUser != null
        val hasGoogleAccount = GoogleSignIn.getLastSignedInAccount(context) != null
        val extrasKeys = data?.extras?.keySet()?.joinToString(",").orEmpty()
        val googleSignInStatusObj = data?.extras?.get("googleSignInStatus")
        val googleSignInStatus = googleSignInStatusObj?.toString().orEmpty()
        val developerError = googleSignInStatus.contains("DEVELOPER_ERROR", ignoreCase = true)
        val packageName = context.packageName
        val signingSha1 = resolveAppSigningSha1(context)
        val attempt = GoogleSignInAttemptContext(resultCode, data, includeScopes, gmsStatusCode = null)
        val report = buildGoogleSignInSupportReport(context, signingSha1, attempt) +
            "\nfirebaseUser=$hasFirebaseUser\ngoogleAccountCached=$hasGoogleAccount"
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
            },
            supportReport = report,
        )
    }

    fun setSignInFailedMessage(context: Context, message: String) {
        setAuthError(context, message)
    }

    private fun formatGoogleSignInFailure(
        context: Context,
        e: Exception,
        attempt: GoogleSignInAttemptContext,
        report: String,
    ): String {
        val api = e as? ApiException ?: return "Google sign-in failed: ${e.message ?: e.javaClass.simpleName}"
        val code = api.statusCode
        if (code == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
            return signInCancelledUserMessage(report)
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
        if (webClientId.isBlank()) {
            finishAuthOperation()
            return
        }
        val generation = authGeneration
        viewModelScope.launch {
            try {
                val result = authMutex.withLock {
                    if (isStaleAuthOperation(generation)) return@launch
                    prepareGoogleSignInResult(context, webClientId, includeScopes, clearState = true)
                }
                if (isStaleAuthOperation(generation)) return@launch
                val intent = (result as? GoogleSignInPrepareResult.Interactive)?.intent
                if (intent != null) {
                    _pendingGoogleSignIn.emit(PendingGoogleSignIn(intent, includeScopes))
                } else {
                    finishAuthOperation()
                }
            } catch (e: Exception) {
                logAuth("retryGoogleSignInAfterDeveloperError failed", e)
                if (!isStaleAuthOperation(generation)) {
                    setAuthError(context, "Google sign-in retry failed: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    private fun isValidWebClientId(webClientId: String): Boolean {
        return webClientId.endsWith(".apps.googleusercontent.com") && webClientId.length > 30
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

    private fun signInToFirebase(
        context: Context,
        account: GoogleSignInAccount,
        generation: Int = authGeneration,
    ) {
        val token = account.idToken
        if (token.isNullOrBlank()) {
            setAuthError(context, "Missing Google ID token. Verify web client ID setup.")
            return
        }
        val credential = GoogleAuthProvider.getCredential(token, null)
        viewModelScope.launch {
            try {
                auth.signInWithCredential(credential).await()
                if (isStaleAuthOperation(generation)) return@launch
                _session.value = true
                _error.value = ""
                _supportReport.value = ""
                refreshGoogleScopeState(context)
            } catch (e: Exception) {
                logAuth("Firebase signInWithCredential failed", e)
                if (!isStaleAuthOperation(generation)) {
                    setAuthError(context, formatFirebaseAuthFailure(e))
                }
            } finally {
                if (!isStaleAuthOperation(generation)) {
                    finishAuthOperation()
                }
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

    private fun setAuthError(context: Context, message: String, supportReport: String = "") {
        finishAuthOperation()
        _error.value = authError(context, message)
        _supportReport.value = if (supportReport.isBlank()) {
            ""
        } else {
            authError(context, message) + "\n\n" + supportReport
        }
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
