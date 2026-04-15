package org.outreach.app

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDate
import org.outreach.core.data.OutreachServiceLocator
import org.outreach.core.data.RepositorySyncCoordinator
import org.outreach.core.data.SyncWorker
import org.outreach.core.model.AppConfig
import org.outreach.core.model.HouseholdRecord
import org.outreach.core.model.VisitUpdate
import org.outreach.feature.map.parseIsoDateOrNull
import org.outreach.feature.auth.LoginGateScreen
import org.outreach.feature.map.MapViewportState
import org.outreach.feature.map.MapScreen
import org.outreach.feature.settings.SettingsScreen
import org.outreach.feature.visits.VisitLogScreen
import org.json.JSONObject
import org.outreach.debug.agentDebugLog
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var showStartupScreen by remember { mutableStateOf(true) }
    var showLoginGate by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf("home") }
    var mapViewMode by remember { mutableStateOf("map") }
    var pickedSpreadsheetId by remember { mutableStateOf<String?>(null) }
    var pickedSpreadsheetDisplayName by remember { mutableStateOf<String?>(null) }
    var mapViewportState by remember { mutableStateOf<MapViewportState?>(null) }
    val households by (OutreachServiceLocator.repository?.households
        ?: flowOf(emptyList())).collectAsState(initial = emptyList())
    val savedConfig by (OutreachServiceLocator.repository?.config
        ?: flowOf(AppConfig()))
        .collectAsState(initial = AppConfig())
    var selectedHouseholdId by remember { mutableStateOf<String?>(null) }
    val selectedHousehold: HouseholdRecord? =
        remember(households, selectedHouseholdId) {
            selectedHouseholdId?.let { id -> households.firstOrNull { it.id == id } }
        }
    val onHouseholdSelected: (HouseholdRecord) -> Unit = { household ->
        selectedHouseholdId =
            if (selectedHouseholdId == household.id) null else household.id
    }
    var briefCommentPresets by remember { mutableStateOf<List<String>>(emptyList()) }
    var presetsLoading by remember { mutableStateOf(false) }
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var syncStatusMessage by remember { mutableStateOf<String?>(null) }
    val syncCoordinator = remember {
        RepositorySyncCoordinator { OutreachServiceLocator.repository }
    }
    LaunchedEffect(Unit) {
        showStartupScreen = false
    }
    LaunchedEffect(Unit) {
        // #region agent log
        agentDebugLog(
            hypothesisId = "NET",
            location = "OutreachRoot.LaunchedEffect(Unit)",
            message = "app runtime probe",
            data = JSONObject().apply {
                put("showLoginGateInitial", showLoginGate)
            }
        )
        // #endregion
    }
    LaunchedEffect(savedConfig.spreadsheetId) {
        val repository = OutreachServiceLocator.repository ?: return@LaunchedEffect
        presetsLoading = true
        briefCommentPresets = runCatching { repository.loadBriefCommentPresets() }.getOrDefault(emptyList())
        presetsLoading = false
    }
    val briefCommentOptions = remember(households) {
        households.map { it.briefComment }.distinct().sorted()
    }
    val visitationDates = remember(households) {
        households.mapNotNull { parseIsoDateOrNull(it.lastVisited) }
    }
    val earliestVisitationDate = remember(visitationDates) {
        visitationDates.minOrNull() ?: LocalDate.now()
    }
    val resolvedDateRange = remember(
        savedConfig.mapQuickRange,
        savedConfig.mapDateStartIso,
        savedConfig.mapDateEndIso,
        earliestVisitationDate
    ) {
        fun parseIsoLocalDateOrNull(value: String?): LocalDate? =
            value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        val today = LocalDate.now()
        when (savedConfig.mapQuickRange) {
            "Today" -> {
                val start = maxOf(today, earliestVisitationDate)
                start to maxOf(today, start)
            }
            "Yesterday" -> {
                val yesterday = today.minusDays(1)
                val start = maxOf(yesterday, earliestVisitationDate)
                start to maxOf(yesterday, start)
            }
            else -> {
                val startFromConfig = parseIsoLocalDateOrNull(savedConfig.mapDateStartIso)
                val start = maxOf(startFromConfig ?: earliestVisitationDate, earliestVisitationDate)
                val endFromConfig = parseIsoLocalDateOrNull(savedConfig.mapDateEndIso)
                start to maxOf(endFromConfig ?: today, start)
            }
        }
    }
    val filterStartDate = resolvedDateRange.first
    val filterEndDate = resolvedDateRange.second
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val extracted = extractSpreadsheetIdFromUri(context, uri)
        // #region agent log
        agentDebugLog(
            hypothesisId = "B",
            location = "MainActivity.documentLauncher",
            message = "drive pick result",
            data = JSONObject().apply {
                put("extracted", extracted ?: JSONObject.NULL)
                put("uriScheme", uri?.scheme ?: "")
            }
        )
        // #endregion
        if (extracted != null) {
            pickedSpreadsheetDisplayName = drivePickerMetadata(context, uri ?: return@rememberLauncherForActivityResult)?.first
            pickedSpreadsheetId = extracted
        } else if (uri != null) {
            coroutineScope.launch {
                val metadata = drivePickerMetadata(context, uri)
                val resolved = metadata?.let { (displayName, lastModifiedMillis) ->
                    OutreachServiceLocator.repository
                        ?.resolveSpreadsheetIdFromDriveMetadata(displayName, lastModifiedMillis)
                }
                // #region agent log
                agentDebugLog(
                    hypothesisId = "O",
                    location = "MainActivity.documentLauncher",
                    message = "drive metadata id resolution",
                    data = JSONObject().apply {
                        put("displayName", metadata?.first ?: JSONObject.NULL)
                        put("lastModifiedMillis", metadata?.second ?: JSONObject.NULL)
                        put("resolvedId", resolved ?: JSONObject.NULL)
                    }
                )
                // #endregion
                pickedSpreadsheetDisplayName = metadata?.first
                pickedSpreadsheetId = resolved ?: uri.toString()
            }
        } else {
            pickedSpreadsheetDisplayName = null
            pickedSpreadsheetId = null
        }
    }
    val destinations = listOf("home", "visits", "settings")
    if (showStartupScreen) {
        StartupScreen()
        return
    }
    if (showLoginGate) {
        LoginGateScreen(onSignedIn = {
            // #region agent log
            agentDebugLog(
                hypothesisId = "U",
                location = "MainActivity.profileMenu",
                message = "login completed from profile menu",
                data = JSONObject()
            )
            // #endregion
            showLoginGate = false
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
                    if (screen == "home") {
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = mapViewMode == "map",
                                onClick = { mapViewMode = "map" },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                label = { Text("Map") }
                            )
                            SegmentedButton(
                                selected = mapViewMode == "list",
                                onClick = { mapViewMode = "list" },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                label = { Text("List") }
                            )
                        }
                    }
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    IconButton(onClick = { profileMenuExpanded = true }) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = "Profile"
                        )
                    }
                    DropdownMenu(
                        expanded = profileMenuExpanded,
                        onDismissRequest = { profileMenuExpanded = false }
                    ) {
                        val userLine = currentUser?.email
                            ?: currentUser?.displayName
                            ?: "Not logged in"
                        DropdownMenuItem(
                            text = { Text(userLine) },
                            onClick = { profileMenuExpanded = false }
                        )
                        if (currentUser == null) {
                            DropdownMenuItem(
                                text = { Text("Login") },
                                onClick = {
                                    profileMenuExpanded = false
                                    // #region agent log
                                    agentDebugLog(
                                        hypothesisId = "U",
                                        location = "MainActivity.profileMenu",
                                        message = "login selected from profile menu",
                                        data = JSONObject()
                                    )
                                    // #endregion
                                    showLoginGate = true
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Switch profile") },
                                onClick = {
                                    profileMenuExpanded = false
                                    // #region agent log
                                    agentDebugLog(
                                        hypothesisId = "U",
                                        location = "MainActivity.profileMenu",
                                        message = "switch profile selected",
                                        data = JSONObject().apply {
                                            put("email", currentUser.email ?: JSONObject.NULL)
                                        }
                                    )
                                    // #endregion
                                    signOutCurrentSession(context)
                                    showLoginGate = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Logout") },
                                onClick = {
                                    profileMenuExpanded = false
                                    // #region agent log
                                    agentDebugLog(
                                        hypothesisId = "U",
                                        location = "MainActivity.profileMenu",
                                        message = "logout selected",
                                        data = JSONObject().apply {
                                            put("email", currentUser.email ?: JSONObject.NULL)
                                        }
                                    )
                                    // #endregion
                                    signOutCurrentSession(context)
                                    showLoginGate = false
                                }
                            )
                        }
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
                        icon = {
                            Icon(
                                when (destination) {
                                    "home" -> Icons.Default.Home
                                    "visits" -> Icons.AutoMirrored.Filled.List
                                    else -> Icons.Default.Settings
                                },
                                contentDescription = when (destination) {
                                    "home" -> "Home"
                                    "visits" -> "Visits"
                                    else -> "Settings"
                                }
                            )
                        },
                        label = {
                            Text(
                                when (destination) {
                                    "home" -> "Home"
                                    "visits" -> "Visits"
                                    else -> "Settings"
                                }
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        LaunchedEffect(Unit) {
            syncCoordinator.sync().onFailure { throwable ->
                syncStatusMessage = "Initial sync failed: ${throwable.message ?: "unknown error"}"
            }
        }
        syncStatusMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(innerPadding).padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        when (screen) {
            "home" -> MapScreen(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                households = households,
                visibleZipTabs = savedConfig.selectedTabs,
                mapBriefCommentMode = savedConfig.mapBriefCommentMode,
                mapBriefCommentFilter = savedConfig.mapBriefCommentFilter,
                mapOldestRecordsLimit = savedConfig.mapOldestRecordsLimit,
                filterStartDate = filterStartDate,
                filterEndDate = filterEndDate,
                initialViewportState = mapViewportState,
                onViewportStateChanged = { mapViewportState = it },
                selectedHouseholdId = selectedHouseholdId,
                onHouseholdSelected = onHouseholdSelected,
                selectedTabs = savedConfig.selectedTabs,
                onAddHousehold = { tabName, name, streetAddress, neighborhood ->
                    coroutineScope.launch {
                        val repository = OutreachServiceLocator.repository ?: return@launch
                        val id = repository.addHousehold(tabName, name, streetAddress, neighborhood)
                            ?: return@launch
                        repository.flushPendingSync()
                        selectedHouseholdId = id
                    }
                },
                viewMode = mapViewMode,
                onViewModeChange = { mapViewMode = it }
            )
            "settings" -> SettingsScreen(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                pickedSpreadsheetId = pickedSpreadsheetId,
                pickedSpreadsheetDisplayName = pickedSpreadsheetDisplayName,
                savedConfig = savedConfig,
                briefCommentOptions = briefCommentOptions,
                earliestVisitationDate = earliestVisitationDate,
                onUpdateConfig = { config ->
                    val repository = OutreachServiceLocator.repository
                    if (repository != null) {
                        coroutineScope.launch {
                            val before = repository.config.first()
                            // #region agent log
                            val sheetOrTabsChanged =
                                config.spreadsheetId != before.spreadsheetId ||
                                    config.selectedTabs != before.selectedTabs
                            agentDebugLog(
                                hypothesisId = "E",
                                location = "MainActivity.onUpdateConfig",
                                message = "config update",
                                data = JSONObject().apply {
                                    put("beforeSpreadsheetId", before.spreadsheetId)
                                    put("afterSpreadsheetId", config.spreadsheetId)
                                    put("sheetOrTabsChanged", sheetOrTabsChanged)
                                }
                            )
                            // #endregion
                            repository.setConfig(config)
                            if (sheetOrTabsChanged &&
                                config.spreadsheetId.isNotBlank() &&
                                config.selectedTabs.isNotEmpty()
                            ) {
                                repository.syncFromSheet()
                            }
                        }
                    }
                },
                onValidateSchema = { spreadsheetId, tabs, onResult ->
                    val normalizedSpreadsheetId = normalizeSpreadsheetIdInput(spreadsheetId)
                    val repository = OutreachServiceLocator.repository
                    if (repository != null && normalizedSpreadsheetId != null) {
                        coroutineScope.launch {
                            val valid = repository.isSheetSchemaValid(normalizedSpreadsheetId, tabs)
                            onResult(valid)
                        }
                    } else {
                        onResult(false)
                    }
                },
                onLoadTabs = { spreadsheetId, onResult ->
                    val normalizedSpreadsheetId = normalizeSpreadsheetIdInput(spreadsheetId)
                    val repository = OutreachServiceLocator.repository
                    // #region agent log
                    agentDebugLog(
                        hypothesisId = "J",
                        location = "MainActivity.onLoadTabs",
                        message = "load tabs request",
                        data = JSONObject().apply {
                            put("inputSpreadsheetId", spreadsheetId)
                            put("normalizedSpreadsheetId", normalizedSpreadsheetId ?: JSONObject.NULL)
                            put("repositoryPresent", repository != null)
                        }
                    )
                    // #endregion
                    if (repository != null && normalizedSpreadsheetId != null) {
                        coroutineScope.launch {
                            val loadedTabs = runCatching {
                                repository.availableTabs(normalizedSpreadsheetId)
                            }
                            // #region agent log
                            agentDebugLog(
                                hypothesisId = "J",
                                location = "MainActivity.onLoadTabs",
                                message = "load tabs result",
                                data = JSONObject().apply {
                                    put("normalizedSpreadsheetId", normalizedSpreadsheetId)
                                    put("success", loadedTabs.isSuccess)
                                    put(
                                        "error",
                                        loadedTabs.exceptionOrNull()?.message ?: JSONObject.NULL
                                    )
                                    put(
                                        "tabsSample",
                                        loadedTabs.getOrNull()?.take(10)?.joinToString("|") ?: ""
                                    )
                                }
                            )
                            // #endregion
                            onResult(loadedTabs)
                        }
                    } else {
                        onResult(Result.failure(IllegalArgumentException("Invalid spreadsheet id")))
                    }
                },
                onFetchSpreadsheetTitle = { spreadsheetId ->
                    OutreachServiceLocator.repository?.fetchSpreadsheetTitle(spreadsheetId)
                },
                onPickSheetFromDrive = {
                    documentLauncher.launch(
                        arrayOf(
                            "application/vnd.google-apps.spreadsheet",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        )
                    )
                },
                onSyncFromSpreadsheet = { config ->
                    // #region agent log
                    agentDebugLog(
                        hypothesisId = "E",
                        location = "MainActivity.onSyncFromSpreadsheet",
                        message = "manual sync",
                        data = JSONObject().apply {
                            put("spreadsheetId", config.spreadsheetId)
                            put("selectedTabsCount", config.selectedTabs.size)
                        }
                    )
                    // #endregion
                    syncCoordinator.sync(config).onFailure { throwable ->
                        syncStatusMessage = "Sync failed: ${throwable.message ?: "unknown error"}"
                        throw throwable
                    }.onSuccess {
                        syncStatusMessage = null
                    }
                }
            )
            "visits" -> VisitLogScreen(
                modifier = Modifier.padding(innerPadding),
                selectedHousehold = selectedHousehold,
                briefCommentPresets = briefCommentPresets,
                presetsLoading = presetsLoading,
                onSaveVisit = { householdId, briefComment, notes, lastVisitedIsoDate ->
                    val repository = OutreachServiceLocator.repository
                    if (repository != null) {
                        coroutineScope.launch {
                            repository.saveVisitUpdate(
                                VisitUpdate(
                                    householdId = householdId,
                                    briefComment = briefComment,
                                    notes = notes,
                                    lastVisitedIsoDate = lastVisitedIsoDate
                                )
                            )
                            repository.flushPendingSync()
                        }
                    }
                }
            )
            else -> MapScreen(
                Modifier.padding(innerPadding),
                households = households,
                visibleZipTabs = savedConfig.selectedTabs,
                mapBriefCommentMode = savedConfig.mapBriefCommentMode,
                mapBriefCommentFilter = savedConfig.mapBriefCommentFilter,
                mapOldestRecordsLimit = savedConfig.mapOldestRecordsLimit,
                filterStartDate = filterStartDate,
                filterEndDate = filterEndDate,
                initialViewportState = mapViewportState,
                onViewportStateChanged = { mapViewportState = it },
                selectedHouseholdId = selectedHouseholdId,
                onHouseholdSelected = onHouseholdSelected,
                selectedTabs = savedConfig.selectedTabs,
                onAddHousehold = { tabName, name, streetAddress, neighborhood ->
                    coroutineScope.launch {
                        val repository = OutreachServiceLocator.repository ?: return@launch
                        val id = repository.addHousehold(tabName, name, streetAddress, neighborhood)
                            ?: return@launch
                        repository.flushPendingSync()
                        selectedHouseholdId = id
                    }
                },
                viewMode = mapViewMode,
                onViewModeChange = { mapViewMode = it }
            )
        }
    }
}

private fun signOutCurrentSession(context: android.content.Context) {
    runCatching { FirebaseAuth.getInstance().signOut() }
    val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
    runCatching {
        GoogleSignIn.getClient(context, signInOptions).signOut()
    }
}

private fun extractSpreadsheetIdFromUri(context: android.content.Context, uri: Uri?): String? {
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
    if (isLikelySpreadsheetId(candidate)) return candidate
    val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
    val metadataPairs = mutableListOf<Pair<String, String>>()
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                for (i in 0 until cursor.columnCount) {
                    val key = cursor.getColumnName(i).orEmpty()
                    val value = cursor.getString(i).orEmpty()
                    if (key.isNotBlank() && value.isNotBlank()) metadataPairs += key to value
                }
            }
        }
    }
    val metadataCandidates = buildList {
        if (!documentId.isNullOrBlank()) add(documentId)
        addAll(metadataPairs.map { it.second })
    }
    val fromMetadata = metadataCandidates
        .asSequence()
        .mapNotNull { normalizeSpreadsheetIdInput(it) }
        .firstOrNull()
    // #region agent log
    agentDebugLog(
        hypothesisId = "N",
        location = "MainActivity.extractSpreadsheetIdFromUri",
        message = "drive uri metadata probe",
        data = JSONObject().apply {
            put("uriScheme", uri.scheme ?: "")
            put("documentId", documentId ?: JSONObject.NULL)
            put(
                "metadataSample",
                metadataPairs.take(8).joinToString("|") { "${it.first}=${it.second.take(80)}" }
            )
            put("fromMetadata", fromMetadata ?: JSONObject.NULL)
        }
    )
    // #endregion
    if (!fromMetadata.isNullOrBlank()) return fromMetadata
    // #region agent log
    agentDebugLog(
        hypothesisId = "K",
        location = "MainActivity.extractSpreadsheetIdFromUri",
        message = "uri extraction candidates",
        data = JSONObject().apply {
            put("uriScheme", uri.scheme ?: "")
            put("matchedFromSheetsPath", match ?: JSONObject.NULL)
            put("matchedFromQueryId", fromQuery ?: JSONObject.NULL)
            put("matchedFromLastSegment", candidate.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("matchedFromEncodedDoc", JSONObject.NULL)
        }
    )
    // #endregion
    return null
}

private fun drivePickerMetadata(context: android.content.Context, uri: Uri): Pair<String, Long?>? {
    var displayName: String? = null
    var lastModifiedMillis: Long? = null
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                for (i in 0 until cursor.columnCount) {
                    val key = cursor.getColumnName(i).orEmpty()
                    val value = cursor.getString(i).orEmpty()
                    if (displayName == null &&
                        (key == "_display_name" || key.equals("display_name", ignoreCase = true))
                    ) {
                        displayName = value.takeIf { it.isNotBlank() }
                    }
                    if (lastModifiedMillis == null &&
                        (key == "last_modified" || key.equals("lastModified", ignoreCase = true))
                    ) {
                        lastModifiedMillis = value.toLongOrNull()
                    }
                }
            }
        }
    }
    val name = displayName?.trim().orEmpty()
    if (name.isBlank()) return null
    return name to lastModifiedMillis
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

@Composable
private fun StartupScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher),
            contentDescription = "Startup branding",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
