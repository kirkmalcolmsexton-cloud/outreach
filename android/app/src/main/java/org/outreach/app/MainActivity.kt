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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.google.firebase.auth.FirebaseAuth
import org.outreach.core.model.CollaborationEvent
import org.outreach.core.data.OutreachServiceLocator
import org.outreach.core.data.SyncWorker
import org.outreach.core.model.AppConfig
import org.outreach.core.model.HouseholdRecord
import org.outreach.core.model.VisitUpdate
import org.outreach.feature.map.parseIsoDateOrNull
import org.outreach.feature.auth.LoginGateScreen
import org.outreach.feature.collab.CollaborationScreen
import org.outreach.feature.map.MapViewportState
import org.outreach.feature.map.MapScreen
import org.outreach.feature.settings.SettingsScreen
import org.outreach.feature.visits.VisitLogScreen
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
    var loggedIn by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf("home") }
    var pickedSpreadsheetId by remember { mutableStateOf<String?>(null) }
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
    val filterStartDate = remember(savedConfig.mapDateStartIso, earliestVisitationDate) {
        val fromConfig = savedConfig.mapDateStartIso?.let { LocalDate.parse(it) }
        maxOf(fromConfig ?: earliestVisitationDate, earliestVisitationDate)
    }
    val filterEndDate = remember(savedConfig.mapDateEndIso, filterStartDate) {
        val fromConfig = savedConfig.mapDateEndIso?.let { LocalDate.parse(it) }
        maxOf(fromConfig ?: LocalDate.now(), filterStartDate)
    }
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val extracted = extractSpreadsheetIdFromUri(uri)
        if (extracted != null) {
            pickedSpreadsheetId = extracted
        } else {
            pickedSpreadsheetId = uri?.toString()
        }
    }
    val destinations = listOf("home", "visits", "collab")
    if (!loggedIn) {
        LoginGateScreen(onSignedIn = {
            loggedIn = true
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
            val repository = OutreachServiceLocator.repository ?: return@LaunchedEffect
            repository.flushPendingSync()
            repository.syncFromSheet()
        }
        when (screen) {
            "home" -> MapScreen(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                households = households,
                visibleZipTabs = savedConfig.selectedTabs,
                mapBriefCommentFilter = savedConfig.mapBriefCommentFilter,
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
                }
            )
            "settings" -> SettingsScreen(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                pickedSpreadsheetId = pickedSpreadsheetId,
                savedConfig = savedConfig,
                briefCommentOptions = briefCommentOptions,
                earliestVisitationDate = earliestVisitationDate,
                onUpdateConfig = { config ->
                    val repository = OutreachServiceLocator.repository
                    if (repository != null) {
                        coroutineScope.launch {
                            val before = repository.config.first()
                            repository.setConfig(config)
                            val sheetOrTabsChanged =
                                config.spreadsheetId != before.spreadsheetId ||
                                    config.selectedTabs != before.selectedTabs
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
                    if (repository != null && normalizedSpreadsheetId != null) {
                        coroutineScope.launch {
                            val loadedTabs = runCatching {
                                repository.availableTabs(normalizedSpreadsheetId)
                            }
                            onResult(loadedTabs)
                        }
                    } else {
                        onResult(Result.failure(IllegalArgumentException("Invalid spreadsheet id")))
                    }
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
                    val repository = OutreachServiceLocator.repository
                        ?: error("Repository not initialized")
                    repository.setConfig(config)
                    repository.flushPendingSync()
                    repository.syncFromSheet()
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
            else -> MapScreen(
                Modifier.padding(innerPadding),
                households = households,
                visibleZipTabs = savedConfig.selectedTabs,
                mapBriefCommentFilter = savedConfig.mapBriefCommentFilter,
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
                }
            )
        }
    }
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
