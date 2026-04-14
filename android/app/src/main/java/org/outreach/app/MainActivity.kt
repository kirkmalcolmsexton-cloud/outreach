package org.outreach.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import org.outreach.app.BuildConfig
import org.outreach.core.model.CollaborationEvent
import org.outreach.core.debug.AgentDebugLogger
import org.outreach.core.data.OutreachServiceLocator
import org.outreach.core.data.SyncWorker
import org.outreach.core.model.AppConfig
import org.outreach.core.model.VisitUpdate
import org.outreach.feature.auth.LoginGateScreen
import org.outreach.feature.collab.CollaborationScreen
import org.outreach.feature.map.MapScreen
import org.outreach.feature.settings.SettingsScreen
import org.outreach.feature.visits.VisitLogScreen
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // #region agent log
        if (BuildConfig.DEBUG) {
            AgentDebugLogger.log(
                runId = "run5",
                hypothesisId = "H17",
                location = "MainActivity.kt:onCreate",
                message = "App started with latest debug instrumentation",
                data = mapOf(
                    "buildType" to BuildConfig.BUILD_TYPE,
                    "versionName" to BuildConfig.VERSION_NAME
                )
            )
        }
        // #endregion
        scheduleBackgroundSync()
        setContent {
            MaterialTheme {
                OutreachRoot()
            }
        }
    }

    private fun scheduleBackgroundSync() {
        val work = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "outreach-sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            work
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun OutreachRoot() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf("home") }
    var pickedSpreadsheetId by remember { mutableStateOf<String?>(null) }
    val households by (OutreachServiceLocator.repository?.households
        ?: flowOf(emptyList())).collectAsState(initial = emptyList())
    val savedConfig by (OutreachServiceLocator.repository?.config
        ?: flowOf(AppConfig(spreadsheetId = "", selectedTabs = emptySet())))
        .collectAsState(initial = AppConfig(spreadsheetId = "", selectedTabs = emptySet()))
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        inspectDriveSelection(uri, context)
        val extracted = extractSpreadsheetIdFromUri(uri)
        // #region agent log
        if (BuildConfig.DEBUG) {
            AgentDebugLogger.log(
                runId = "sheet-debug",
                hypothesisId = "S8",
                location = "MainActivity.kt:documentLauncher",
                message = "Sheet picker returned URI",
                data = mapOf(
                    "hasUri" to (uri != null),
                    "uriPrefix" to (uri?.toString()?.take(80) ?: ""),
                    "extractedIdSuffix" to (extracted?.takeLast(6) ?: "")
                )
            )
        }
        // #endregion
        if (extracted != null) {
            pickedSpreadsheetId = extracted
        } else {
            pickedSpreadsheetId = uri?.toString()
            // #region agent log
            if (BuildConfig.DEBUG) {
                AgentDebugLogger.log(
                    runId = "run1",
                    hypothesisId = "H6",
                    location = "MainActivity.kt:documentLauncher",
                    message = "Drive picker URI did not contain spreadsheet id",
                    data = mapOf(
                        "uriPrefix" to (uri?.toString()?.take(80) ?: "")
                    )
                )
            }
            // #endregion
        }
    }
    val destinations = listOf("home", "visits", "collab")
    // #region agent log
    if (BuildConfig.DEBUG) {
        AgentDebugLogger.log(
            runId = "pre-fix",
            hypothesisId = "H2",
            location = "MainActivity.kt:OutreachRoot",
            message = "OutreachRoot composed",
            data = mapOf(
                "loggedInState" to loggedIn,
                "hasCurrentUserAtCompose" to (FirebaseAuth.getInstance().currentUser != null),
                "screen" to screen
            )
        )
    }
    // #endregion
    if (!loggedIn) {
        // #region agent log
        if (BuildConfig.DEBUG) {
            AgentDebugLogger.log(
                runId = "pre-fix",
                hypothesisId = "H3",
                location = "MainActivity.kt:loginGateBranch",
                message = "Rendering LoginGateScreen branch",
                data = mapOf(
                    "loggedInState" to loggedIn,
                    "hasCurrentUserWhenBlocked" to (FirebaseAuth.getInstance().currentUser != null)
                )
            )
        }
        // #endregion
        LoginGateScreen(onSignedIn = {
            // #region agent log
            if (BuildConfig.DEBUG) {
                AgentDebugLogger.log(
                    runId = "sheet-debug",
                    hypothesisId = "S6",
                    location = "MainActivity.kt:onSignedInCallback",
                    message = "LoginGateScreen onSignedIn callback invoked",
                    data = mapOf(
                        "loggedInBefore" to loggedIn,
                        "hasCurrentUser" to (FirebaseAuth.getInstance().currentUser != null)
                    )
                )
            }
            // #endregion
            loggedIn = true
            // #region agent log
            if (BuildConfig.DEBUG) {
                AgentDebugLogger.log(
                    runId = "sheet-debug",
                    hypothesisId = "S6",
                    location = "MainActivity.kt:onSignedInCallback",
                    message = "MainActivity set loggedIn=true",
                    data = mapOf(
                        "loggedInAfter" to loggedIn
                    )
                )
            }
            // #endregion
        })
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Outreach") },
                navigationIcon = {
                    IconButton(onClick = { screen = "home" }) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                },
                actions = {
                    IconButton(onClick = { screen = "visits" }) {
                        Icon(Icons.Default.List, contentDescription = "Zip code filter")
                    }
                    IconButton(onClick = { screen = "settings" }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = screen == destination,
                        onClick = { screen = destination },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text(destination) }
                    )
                }
            }
        }
    ) { innerPadding ->
        LaunchedEffect(Unit) {
            OutreachServiceLocator.repository?.syncFromSheet()
        }
        when (screen) {
            "home" -> MapScreen(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                households = households
            )
            "settings" -> SettingsScreen(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                pickedSpreadsheetId = pickedSpreadsheetId,
                savedSpreadsheetId = savedConfig.spreadsheetId,
                savedTabs = savedConfig.selectedTabs,
                onSaveConfig = { spreadsheetId, tabs ->
                    val normalizedSpreadsheetId = normalizeSpreadsheetIdInput(spreadsheetId)
                    // #region agent log
                    if (BuildConfig.DEBUG) {
                        AgentDebugLogger.log(
                            runId = "run1",
                            hypothesisId = "H2",
                            location = "MainActivity.kt:onSaveConfig",
                            message = "Saving sheet config from settings",
                            data = mapOf(
                                "rawInputPrefix" to spreadsheetId.take(32),
                                "rawInputLength" to spreadsheetId.length,
                                "normalizedSuffix" to (normalizedSpreadsheetId?.takeLast(8) ?: "null"),
                                "normalizedLength" to (normalizedSpreadsheetId?.length ?: 0),
                                "tabsCount" to tabs.size
                            )
                        )
                    }
                    // #endregion
                    val repository = OutreachServiceLocator.repository
                    if (repository != null && normalizedSpreadsheetId != null) {
                        coroutineScope.launch {
                            repository.setConfig(AppConfig(spreadsheetId = normalizedSpreadsheetId, selectedTabs = tabs))
                            repository.syncFromSheet()
                        }
                    }
                },
                onValidateSchema = { spreadsheetId, tabs, onResult ->
                    val normalizedSpreadsheetId = normalizeSpreadsheetIdInput(spreadsheetId)
                    val repository = OutreachServiceLocator.repository
                    if (repository != null && normalizedSpreadsheetId != null) {
                        coroutineScope.launch {
                            // #region agent log
                            if (BuildConfig.DEBUG) {
                                AgentDebugLogger.log(
                                    runId = "sheet-debug",
                                    hypothesisId = "S1",
                                    location = "MainActivity.kt:onValidateSchema",
                                    message = "Validate schema tapped",
                                    data = mapOf(
                                        "spreadsheetIdSuffix" to normalizedSpreadsheetId.takeLast(6),
                                        "tabsCount" to tabs.size,
                                        "tabs" to tabs.toList()
                                    )
                                )
                            }
                            // #endregion
                            val valid = repository.isSheetSchemaValid(normalizedSpreadsheetId, tabs)
                            // #region agent log
                            if (BuildConfig.DEBUG) {
                                AgentDebugLogger.log(
                                    runId = "sheet-debug",
                                    hypothesisId = "S5",
                                    location = "MainActivity.kt:onValidateSchema",
                                    message = "Validate schema completed",
                                    data = mapOf(
                                        "isValid" to valid
                                    )
                                )
                            }
                            // #endregion
                            onResult(valid)
                        }
                    } else {
                        // #region agent log
                        if (BuildConfig.DEBUG && normalizedSpreadsheetId == null) {
                            AgentDebugLogger.log(
                                runId = "sheet-debug",
                                hypothesisId = "S7",
                                location = "MainActivity.kt:onValidateSchema",
                                message = "Rejected invalid spreadsheet id format",
                                data = mapOf(
                                    "rawSuffix" to spreadsheetId.takeLast(12),
                                    "tabsCount" to tabs.size
                                )
                            )
                        }
                        // #endregion
                        onResult(false)
                    }
                },
                onPickSheetFromDrive = {
                    documentLauncher.launch(
                        arrayOf(
                            "application/vnd.google-apps.spreadsheet",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        )
                    )
                }
            )
            "visits" -> VisitLogScreen(
                modifier = Modifier.padding(innerPadding),
                onSaveVisit = { householdId, briefComment, notes ->
                    val repository = OutreachServiceLocator.repository
                    if (repository != null) {
                        coroutineScope.launch {
                            repository.saveVisitUpdate(
                                VisitUpdate(
                                    householdId = householdId,
                                    briefComment = briefComment,
                                    notes = notes,
                                    lastVisitedIsoDate = java.time.LocalDate.now().toString()
                                )
                            )
                            repository.flushPendingSync()
                        }
                    }
                }
            )
            "collab" -> CollaborationScreen(
                modifier = Modifier.padding(innerPadding),
                onPublishPresence = {
                    val collab = OutreachServiceLocator.collaborationRepository
                    val userId = FirebaseAuth.getInstance().currentUser?.uid
                    if (userId != null) {
                        coroutineScope.launch {
                            collab?.publishPresence(userId, "active")
                            collab?.publishActivity(
                                CollaborationEvent(
                                    userId = userId,
                                    householdId = null,
                                    tabName = "active",
                                    type = "presence_update",
                                    epochMillis = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            )
            else -> MapScreen(Modifier.padding(innerPadding), households = households)
        }
    }
}

private fun inspectDriveSelection(uri: Uri?, context: android.content.Context) {
    if (!BuildConfig.DEBUG || uri == null) return
    val candidateColumns = listOf(
        "_display_name",
        "_size",
        "document_id",
        "resource_id",
        "drive_resource_id",
        "mime_type",
        "flags"
    )
    val metadata = linkedMapOf<String, String>()
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                candidateColumns.forEach { name ->
                    val idx = cursor.getColumnIndex(name)
                    if (idx >= 0) {
                        metadata[name] = runCatching { cursor.getString(idx) ?: "" }.getOrDefault("")
                    }
                }
            }
        }
    }
    // #region agent log
    AgentDebugLogger.log(
        runId = "run7",
        hypothesisId = "H20",
        location = "MainActivity.kt:inspectDriveSelection",
        message = "Captured Drive picker metadata fields",
        data = mapOf(
            "uriPrefix" to uri.toString().take(120),
            "metadata" to metadata.toString()
        )
    )
    // #endregion
}

private fun extractSpreadsheetIdFromUri(uri: Uri?): String? {
    if (uri == null) return null
    val full = uri.toString()
    val regex = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)")
    val match = regex.find(full)?.groupValues?.getOrNull(1)
    if (!match.isNullOrBlank() && isLikelySpreadsheetId(match)) return match
    val fromQuery = uri.getQueryParameter("id")?.trim()
    if (!fromQuery.isNullOrBlank() && isLikelySpreadsheetId(fromQuery)) return fromQuery
    val candidate = uri.lastPathSegment.orEmpty()
        .substringAfterLast('/')
        .substringAfterLast(':')
        .trim()
    return candidate.takeIf { isLikelySpreadsheetId(it) }
}

private fun isLikelySpreadsheetId(value: String): Boolean =
    value.matches(Regex("^[a-zA-Z0-9-_]{20,}$"))

private fun normalizeSpreadsheetIdInput(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    if (isLikelySpreadsheetId(trimmed)) return trimmed
    val fromUrl = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)").find(trimmed)?.groupValues?.getOrNull(1)
    if (!fromUrl.isNullOrBlank() && isLikelySpreadsheetId(fromUrl)) return fromUrl
    return null
}
