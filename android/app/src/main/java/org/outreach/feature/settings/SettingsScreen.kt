package org.outreach.feature.settings

import android.app.DatePickerDialog
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import org.outreach.core.model.AppConfig
import org.outreach.debug.agentDebugLog
import org.outreach.feature.map.formatBriefComment
import org.outreach.ui.testtags.TestTags

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SettingsScreen(
    modifier: Modifier = Modifier,
    pickedSpreadsheetId: String? = null,
    pickedSpreadsheetDisplayName: String? = null,
    savedConfig: AppConfig = AppConfig(),
    briefCommentOptions: List<String> = emptyList(),
    earliestVisitationDate: LocalDate = LocalDate.now(),
    onUpdateConfig: (AppConfig) -> Unit = {},
    onValidateSchema: (spreadsheetId: String, tabs: Set<String>, onResult: (Boolean) -> Unit) -> Unit = { _, _, _ -> },
    onLoadTabs: (spreadsheetId: String, onResult: (Result<List<String>>) -> Unit) -> Unit = { _, onResult -> onResult(Result.success(emptyList())) },
    onFetchSpreadsheetTitle: suspend (spreadsheetId: String) -> String? = { null },
    onPickSheetFromDrive: () -> Unit = {},
    onSyncFromSpreadsheet: suspend (AppConfig) -> Unit = {}
) {
    fun parseIsoLocalDateOrNull(value: String?): LocalDate? =
        value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prettyDateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    val zipTabPattern = remember { Regex("^\\d{5}(-\\d{4})?$") }
    var spreadsheetId by remember { mutableStateOf("") }
    var selectedTabs by remember { mutableStateOf(savedConfig.selectedTabs) }
    var availableZipTabs by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingTabs by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Not validated") }
    var isSyncing by remember { mutableStateOf(false) }
    var validationStatus by remember { mutableStateOf("Not validated") }
    var lastAutoFilledSpreadsheetId by remember { mutableStateOf<String?>(null) }
    var lastLoadedSpreadsheetId by remember { mutableStateOf<String?>(null) }
    /** Title for the ID currently in the field when it differs from [savedConfig] (not persisted until Save). */
    var pendingSpreadsheetTitle by remember { mutableStateOf<String?>(null) }
    var pendingDisplayNameFromPicker by remember { mutableStateOf<String?>(null) }

    var briefCommentMode by remember { mutableStateOf(savedConfig.mapBriefCommentMode) }
    var selectedBriefComments by remember { mutableStateOf(savedConfig.mapBriefCommentFilter) }
    var startDate by remember {
        mutableStateOf(
            parseIsoLocalDateOrNull(savedConfig.mapDateStartIso) ?: earliestVisitationDate
        )
    }
    var endDate by remember {
        mutableStateOf(
            parseIsoLocalDateOrNull(savedConfig.mapDateEndIso) ?: LocalDate.now()
        )
    }
    var selectedQuickRange by remember { mutableStateOf(savedConfig.mapQuickRange) }
    var oldestRecordsLimitInput by remember {
        mutableStateOf(savedConfig.mapOldestRecordsLimit?.toString().orEmpty())
    }

    fun parseOldestRecordsLimitOrNull(value: String): Int? =
        value.trim().toIntOrNull()?.takeIf { it > 0 }

    val quickRangeOptions = remember {
        listOf(
            "Today",
            "Yesterday",
            "All",
            "Custom"
        )
    }

    fun resolvedSpreadsheetIdForPersist(): String =
        normalizeSpreadsheetIdInput(spreadsheetId) ?: savedConfig.spreadsheetId

    fun resolvedSpreadsheetTitleForPersist(): String? =
        when (normalizeSpreadsheetIdInput(spreadsheetId)) {
            savedConfig.spreadsheetId -> savedConfig.spreadsheetTitle?.takeIf { it.isNotBlank() }
            else -> pendingSpreadsheetTitle?.takeIf { it.isNotBlank() }
        }

    fun persistMapFiltersOnly() {
        // #region agent log
        agentDebugLog(
            hypothesisId = "X1",
            location = "SettingsScreen.persistMapFiltersOnly",
            message = "persist map-only config",
            data = JSONObject().apply {
                put("briefCount", selectedBriefComments.size)
                put("briefMode", briefCommentMode)
                put("quickRange", selectedQuickRange)
                put("startDate", startDate.toString())
                put("endDate", endDate.toString())
                put("oldestLimitRaw", oldestRecordsLimitInput)
            }
        )
        // #endregion
        onUpdateConfig(
            savedConfig.copy(
                mapBriefCommentMode = briefCommentMode,
                mapBriefCommentFilter = selectedBriefComments,
                mapDateStartIso = startDate.toString(),
                mapDateEndIso = endDate.toString(),
                mapQuickRange = selectedQuickRange,
                mapOldestRecordsLimit = parseOldestRecordsLimitOrNull(oldestRecordsLimitInput)
            )
        )
    }

    fun persistFullConfig(selectedTabsOverride: Set<String>) {
        // #region agent log
        agentDebugLog(
            hypothesisId = "X2",
            location = "SettingsScreen.persistFullConfig",
            message = "persist full config",
            data = JSONObject().apply {
                put("resolvedSpreadsheetId", resolvedSpreadsheetIdForPersist())
                put("selectedTabsCount", selectedTabsOverride.size)
                put("pendingTitle", resolvedSpreadsheetTitleForPersist() ?: JSONObject.NULL)
            }
        )
        // #endregion
        onUpdateConfig(
            AppConfig(
                spreadsheetId = resolvedSpreadsheetIdForPersist(),
                spreadsheetTitle = resolvedSpreadsheetTitleForPersist(),
                selectedTabs = selectedTabsOverride,
                mapBriefCommentMode = briefCommentMode,
                mapBriefCommentFilter = selectedBriefComments,
                mapDateStartIso = startDate.toString(),
                mapDateEndIso = endDate.toString(),
                mapQuickRange = selectedQuickRange,
                mapOldestRecordsLimit = parseOldestRecordsLimitOrNull(oldestRecordsLimitInput)
            )
        )
    }

    LaunchedEffect(savedConfig.spreadsheetId, savedConfig.selectedTabs) {
        if (savedConfig.spreadsheetId.isBlank()) return@LaunchedEffect
        val savedLooksLikeDocToken = looksLikeDriveDocToken(savedConfig.spreadsheetId)
        val canApplySaved =
            (spreadsheetId.isBlank() || spreadsheetId == lastAutoFilledSpreadsheetId) &&
                !savedLooksLikeDocToken
        // #region agent log
        agentDebugLog(
            hypothesisId = "D",
            location = "SettingsScreen.LaunchedEffect(savedConfig.sheet)",
            message = "saved spreadsheet effect",
            data = JSONObject().apply {
                put("savedSpreadsheetId", savedConfig.spreadsheetId)
                put("fieldSpreadsheetId", spreadsheetId)
                put("lastAutoFilledSpreadsheetId", lastAutoFilledSpreadsheetId ?: "")
                put("savedLooksLikeDocToken", savedLooksLikeDocToken)
                put("canApplySaved", canApplySaved)
            }
        )
        // #endregion
        if (canApplySaved) {
            spreadsheetId = savedConfig.spreadsheetId
            selectedTabs = savedConfig.selectedTabs
            lastAutoFilledSpreadsheetId = savedConfig.spreadsheetId
        } else if (savedLooksLikeDocToken && spreadsheetId.isBlank()) {
            // #region agent log
            agentDebugLog(
                hypothesisId = "M",
                location = "SettingsScreen.LaunchedEffect(savedConfig.sheet)",
                message = "skip autofill for suspicious saved id",
                data = JSONObject().apply {
                    put("savedSpreadsheetId", savedConfig.spreadsheetId)
                }
            )
            // #endregion
            status = "Saved spreadsheet ID looks invalid. Paste a Google Sheets URL or raw sheet ID."
        }
    }
    LaunchedEffect(savedConfig.selectedTabs) {
        selectedTabs = savedConfig.selectedTabs
    }

    LaunchedEffect(
        savedConfig.mapBriefCommentFilter,
        savedConfig.mapBriefCommentMode,
        savedConfig.mapDateStartIso,
        savedConfig.mapDateEndIso,
        savedConfig.mapQuickRange,
        savedConfig.mapOldestRecordsLimit,
        earliestVisitationDate
    ) {
        briefCommentMode = savedConfig.mapBriefCommentMode
        selectedBriefComments = savedConfig.mapBriefCommentFilter
        selectedQuickRange = savedConfig.mapQuickRange
        when (savedConfig.mapQuickRange) {
            "Today" -> {
                val today = LocalDate.now()
                startDate = maxOf(today, earliestVisitationDate)
                endDate = maxOf(today, startDate)
            }
            "Yesterday" -> {
                val yesterday = LocalDate.now().minusDays(1)
                startDate = maxOf(yesterday, earliestVisitationDate)
                endDate = maxOf(yesterday, startDate)
            }
            else -> {
                startDate = parseIsoLocalDateOrNull(savedConfig.mapDateStartIso) ?: earliestVisitationDate
                endDate = parseIsoLocalDateOrNull(savedConfig.mapDateEndIso) ?: LocalDate.now()
            }
        }
        oldestRecordsLimitInput = savedConfig.mapOldestRecordsLimit?.toString().orEmpty()
    }

    LaunchedEffect(earliestVisitationDate) {
        if (startDate.isBefore(earliestVisitationDate)) {
            startDate = earliestVisitationDate
        }
    }

    LaunchedEffect(pickedSpreadsheetId) {
        val picked = pickedSpreadsheetId ?: return@LaunchedEffect
        if (picked.isBlank()) return@LaunchedEffect
        val pickedNorm = normalizeSpreadsheetIdInput(picked)
        if (!pickedSpreadsheetDisplayName.isNullOrBlank()) {
            pendingDisplayNameFromPicker = pickedSpreadsheetDisplayName
            // #region agent log
            agentDebugLog(
                hypothesisId = "P",
                location = "SettingsScreen.LaunchedEffect(pickedSpreadsheetId)",
                message = "picked display name applied",
                data = JSONObject().apply {
                    put("pickedNorm", pickedNorm ?: JSONObject.NULL)
                    put("pickedSpreadsheetDisplayName", pickedSpreadsheetDisplayName)
                }
            )
            // #endregion
        }
        val currentNorm = normalizeSpreadsheetIdInput(spreadsheetId)
        if (picked.startsWith("content://") && pickedNorm == null) {
            // #region agent log
            agentDebugLog(
                hypothesisId = "L",
                location = "SettingsScreen.LaunchedEffect(pickedSpreadsheetId)",
                message = "ignore unresolved content uri",
                data = JSONObject().apply {
                    put("picked", picked)
                    put("fieldBefore", spreadsheetId)
                    put("currentNorm", currentNorm ?: JSONObject.NULL)
                }
            )
            // #endregion
            status =
                "Drive selection could not resolve a Sheet ID. Enable Google Drive API for the app's project or paste a Google Sheets URL/raw sheet ID."
            return@LaunchedEffect
        }
        val shouldApplyPickedValue =
            spreadsheetId.isBlank() ||
                spreadsheetId == lastAutoFilledSpreadsheetId ||
                (pickedNorm != null && pickedNorm != currentNorm)
        // #region agent log
        agentDebugLog(
            hypothesisId = "B",
            location = "SettingsScreen.LaunchedEffect(pickedSpreadsheetId)",
            message = "drive pick vs field",
            data = JSONObject().apply {
                put("picked", picked)
                put("pickedNorm", pickedNorm ?: JSONObject.NULL)
                put("currentNorm", currentNorm ?: JSONObject.NULL)
                put("shouldApplyPickedValue", shouldApplyPickedValue)
                put("lastAutoFilled", lastAutoFilledSpreadsheetId ?: "")
            }
        )
        // #endregion
        if (shouldApplyPickedValue) {
            val nextSpreadsheetId = pickedNorm ?: picked
            spreadsheetId = nextSpreadsheetId
            lastAutoFilledSpreadsheetId = nextSpreadsheetId
            if (normalizeSpreadsheetIdInput(nextSpreadsheetId) != null) {
                persistFullConfig(selectedTabs)
            }
        }
    }
    val normalizedSpreadsheetId = remember(spreadsheetId) {
        normalizeSpreadsheetIdInput(spreadsheetId)
    }
    // #region agent log
    LaunchedEffect(normalizedSpreadsheetId, savedConfig.spreadsheetId, savedConfig.spreadsheetTitle) {
        agentDebugLog(
            hypothesisId = "F",
            location = "SettingsScreen.displaySpreadsheetTitle",
            message = "display title inputs",
            data = JSONObject().apply {
                put("normalizedSpreadsheetId", normalizedSpreadsheetId ?: JSONObject.NULL)
                put("savedSpreadsheetId", savedConfig.spreadsheetId)
                put("savedSpreadsheetTitle", savedConfig.spreadsheetTitle ?: JSONObject.NULL)
                put("pendingSpreadsheetTitle", pendingSpreadsheetTitle ?: JSONObject.NULL)
            }
        )
    }
    // #endregion
    LaunchedEffect(normalizedSpreadsheetId) {
        pendingSpreadsheetTitle = null
        val normalized = normalizedSpreadsheetId
        if (normalized == null) {
            availableZipTabs = emptyList()
            isLoadingTabs = false
            return@LaunchedEffect
        }
        if (normalized == lastLoadedSpreadsheetId) {
            // #region agent log
            agentDebugLog(
                hypothesisId = "C",
                location = "SettingsScreen.LaunchedEffect(normalizedSpreadsheetId)",
                message = "skip tab load (same as lastLoaded)",
                data = JSONObject().apply {
                    put("normalized", normalized)
                    put("lastLoadedSpreadsheetId", lastLoadedSpreadsheetId ?: "")
                }
            )
            // #endregion
            return@LaunchedEffect
        }
        // #region agent log
        agentDebugLog(
            hypothesisId = "C",
            location = "SettingsScreen.LaunchedEffect(normalizedSpreadsheetId)",
            message = "will load tabs",
            data = JSONObject().apply {
                put("normalized", normalized)
                put("lastLoadedSpreadsheetId", lastLoadedSpreadsheetId ?: "")
            }
        )
        // #endregion
        isLoadingTabs = true
        onLoadTabs(normalized) { result ->
            result
                .onSuccess { tabs ->
                    val zipTabs = tabs
                        .map { it.trim() }
                        .filter { it.matches(zipTabPattern) }
                        .sorted()
                    // #region agent log
                    agentDebugLog(
                        hypothesisId = "I",
                        location = "SettingsScreen.onLoadTabs.onSuccess",
                        message = "tabs loaded and filtered",
                        data = JSONObject().apply {
                            put("normalizedSpreadsheetId", normalized)
                            put("rawTabsCount", tabs.size)
                            put("zipTabsCount", zipTabs.size)
                            put("rawTabsSample", tabs.take(10).joinToString("|"))
                            put("zipTabsSample", zipTabs.take(10).joinToString("|"))
                        }
                    )
                    // #endregion
                    availableZipTabs = zipTabs
                    selectedTabs = selectedTabs.filterTo(mutableSetOf()) { it in zipTabs }
                    if (zipTabs.isEmpty()) {
                        status = "No ZIP-named tabs found. Expected 12345 or 12345-6789."
                    }
                    lastLoadedSpreadsheetId = normalized
                    isLoadingTabs = false
                    scope.launch {
                        val titleResult = runCatching { onFetchSpreadsheetTitle(normalized) }
                        val title = titleResult.getOrNull()?.takeIf { it.isNotBlank() }
                        // #region agent log
                        agentDebugLog(
                            hypothesisId = "G",
                            location = "SettingsScreen.fetchSpreadsheetTitle",
                            message = "title fetch finished",
                            data = JSONObject().apply {
                                put("normalized", normalized)
                                put("title", title ?: JSONObject.NULL)
                                put("titleFetchSucceeded", titleResult.isSuccess)
                                put(
                                    "titleFetchError",
                                    titleResult.exceptionOrNull()?.message ?: JSONObject.NULL
                                )
                            }
                        )
                        // #endregion
                        if (title == null) return@launch
                        val fieldNorm = normalizeSpreadsheetIdInput(spreadsheetId)
                        // #region agent log
                        agentDebugLog(
                            hypothesisId = "H",
                            location = "SettingsScreen.titleRouting",
                            message = "title target branch",
                            data = JSONObject().apply {
                                put("fieldNorm", fieldNorm ?: JSONObject.NULL)
                                put("savedSpreadsheetId", savedConfig.spreadsheetId)
                                put("normalized", normalized)
                            }
                        )
                        // #endregion
                        if (fieldNorm == savedConfig.spreadsheetId) {
                            onUpdateConfig(savedConfig.copy(spreadsheetTitle = title))
                        } else if (fieldNorm == normalized) {
                            pendingSpreadsheetTitle = title
                            persistFullConfig(selectedTabs)
                        }
                    }
                }
                .onFailure {
                    // #region agent log
                    agentDebugLog(
                        hypothesisId = "I",
                        location = "SettingsScreen.onLoadTabs.onFailure",
                        message = "tabs load failed",
                        data = JSONObject().apply {
                            put("normalizedSpreadsheetId", normalized)
                        }
                    )
                    // #endregion
                    availableZipTabs = emptyList()
                    status = "Unable to load tabs. Check spreadsheet access and try again."
                    isLoadingTabs = false
                }
        }
    }
    Column(
        modifier = modifier
            .testTag(TestTags.SETTINGS_ROOT)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        val displaySpreadsheetTitle = when (normalizedSpreadsheetId) {
            savedConfig.spreadsheetId -> savedConfig.spreadsheetTitle
            else -> pendingSpreadsheetTitle
        }
        val displaySpreadsheetName = displaySpreadsheetFileName(pendingDisplayNameFromPicker)
            ?: displaySpreadsheetFileName(displaySpreadsheetTitle)
        // #region agent log
        LaunchedEffect(displaySpreadsheetTitle, status, normalizedSpreadsheetId, savedConfig.spreadsheetId) {
            agentDebugLog(
                hypothesisId = "X3",
                location = "SettingsScreen.displayHeaderState",
                message = "spreadsheet display state",
                data = JSONObject().apply {
                    put("displaySpreadsheetTitle", displaySpreadsheetTitle ?: JSONObject.NULL)
                    put("status", status)
                    put("normalizedSpreadsheetId", normalizedSpreadsheetId ?: JSONObject.NULL)
                    put("savedSpreadsheetId", savedConfig.spreadsheetId)
                }
            )
        }
        // #endregion
        if (displaySpreadsheetName != null || normalizedSpreadsheetId != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    displaySpreadsheetName ?: "Untitled spreadsheet",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    validationStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!normalizedSpreadsheetId.isNullOrBlank()) {
                Text(
                    "ID: $normalizedSpreadsheetId",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onPickSheetFromDrive,
                modifier = Modifier.testTag(TestTags.SETTINGS_PICK_SPREADSHEET),
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Select spreadsheet"
                )
            }
            IconButton(
                onClick = {
                    val normalized = normalizeSpreadsheetIdInput(spreadsheetId)
                    if (normalized == null) {
                        status = "Cannot validate: select a spreadsheet first."
                        return@IconButton
                    }
                    validationStatus = "Validating..."
                    status = "Validating schema..."
                    onValidateSchema(normalized, selectedTabs) { valid ->
                        validationStatus = if (valid) "Valid" else "Invalid"
                        status = if (valid) "Validated" else "Schema invalid or inaccessible"
                    }
                },
                modifier = Modifier.testTag(TestTags.SETTINGS_VALIDATE),
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Validate spreadsheet schema"
                )
            }
            IconButton(
                enabled = !isSyncing,
                onClick = {
                    val normalized = normalizeSpreadsheetIdInput(spreadsheetId)
                    if (normalized == null) {
                        status = "Could not sync: select a spreadsheet first."
                        return@IconButton
                    }
                    if (selectedTabs.isEmpty()) {
                        status = "Select at least one ZIP tab to sync."
                        return@IconButton
                    }
                    scope.launch {
                        validationStatus = "Not validated"
                        isSyncing = true
                        try {
                            onSyncFromSpreadsheet(
                                AppConfig(
                                    spreadsheetId = normalized,
                                    spreadsheetTitle = resolvedSpreadsheetTitleForPersist(),
                                    selectedTabs = selectedTabs,
                                    mapBriefCommentMode = briefCommentMode,
                                    mapBriefCommentFilter = selectedBriefComments,
                                    mapDateStartIso = startDate.toString(),
                                    mapDateEndIso = endDate.toString(),
                                    mapQuickRange = selectedQuickRange,
                                    mapOldestRecordsLimit = parseOldestRecordsLimitOrNull(oldestRecordsLimitInput)
                                )
                            )
                            lastAutoFilledSpreadsheetId = normalized
                            status = "Synced from spreadsheet"
                        } catch (e: Exception) {
                            status = "Sync failed: ${e.message ?: e::class.simpleName}"
                        } finally {
                            isSyncing = false
                        }
                    }
                }
                ,
                modifier = Modifier.testTag(TestTags.SETTINGS_SYNC)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = if (isSyncing) "Syncing spreadsheet" else "Sync spreadsheet"
                )
            }
            IconButton(
                onClick = {
                    val normalized = normalizeSpreadsheetIdInput(spreadsheetId)
                    if (normalized == null) {
                        status = "Could not share: select a spreadsheet first."
                        return@IconButton
                    }
                    val fullUrl = "https://docs.google.com/spreadsheets/d/$normalized"
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, fullUrl)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share spreadsheet link"))
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share spreadsheet link"
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("ZIP codes")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Choose which ZIP tabs to sync and show on the map (multi-select).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        when {
            normalizedSpreadsheetId == null -> Text("Select a spreadsheet to load ZIP tabs.")
            isLoadingTabs -> Text("Loading tabs...")
            availableZipTabs.isEmpty() -> Text("No matching ZIP tabs available.")
            else -> {
                FlowRow(
                    modifier = Modifier.testTag(TestTags.SETTINGS_ZIP_SECTION),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableZipTabs.sorted().forEach { tab ->
                        val selected = tab in selectedTabs
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val newTabs = if (selected) {
                                    selectedTabs - tab
                                } else {
                                    selectedTabs + tab
                                }
                                selectedTabs = newTabs
                                persistFullConfig(newTabs)
                            },
                            label = { Text(tab) }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Home map filters")
        Spacer(modifier = Modifier.height(8.dp))
        Text("Brief comment filter")
        Spacer(modifier = Modifier.height(4.dp))
        if (briefCommentOptions.isEmpty()) {
            Text("No brief comments in loaded data yet. Sync households, then pick filters here.")
        } else {
            Text(
                "Inclusive filter mode",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = briefCommentMode == "include_all",
                    onClick = {
                        briefCommentMode = "include_all"
                        persistMapFiltersOnly()
                    },
                    label = { Text("Include all") }
                )
                FilterChip(
                    selected = briefCommentMode == "pick_some",
                    onClick = {
                        briefCommentMode = "pick_some"
                        persistMapFiltersOnly()
                    },
                    label = { Text("Pick some") }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                briefCommentOptions.forEach { briefComment ->
                    val selected = briefComment in selectedBriefComments
                    FilterChip(
                        selected = selected,
                        enabled = briefCommentMode == "pick_some",
                        onClick = {
                            selectedBriefComments = if (selected) {
                                selectedBriefComments - briefComment
                            } else {
                                selectedBriefComments + briefComment
                            }
                            persistMapFiltersOnly()
                        },
                        label = { Text(formatBriefComment(briefComment)) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Last visited date range")
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quickRangeOptions.forEach { label ->
                val selected = selectedQuickRange == label
                FilterChip(
                    selected = selected,
                    onClick = {
                        // #region agent log
                        agentDebugLog(
                            hypothesisId = "X4",
                            location = "SettingsScreen.quickRange.onClick",
                            message = "quick range selected",
                            data = JSONObject().apply {
                                put("label", label)
                                put("previousQuickRange", selectedQuickRange)
                            }
                        )
                        // #endregion
                        selectedQuickRange = label
                        when (label) {
                            "Today" -> {
                                val today = LocalDate.now()
                                startDate = maxOf(today, earliestVisitationDate)
                                endDate = maxOf(today, startDate)
                            }
                            "Yesterday" -> {
                                val yesterday = LocalDate.now().minusDays(1)
                                startDate = maxOf(yesterday, earliestVisitationDate)
                                endDate = maxOf(yesterday, startDate)
                            }
                            "All" -> {
                                startDate = earliestVisitationDate
                                endDate = LocalDate.now()
                            }
                            "Custom" -> Unit
                            else -> Unit
                        }
                        persistMapFiltersOnly()
                    },
                    label = { Text(label) }
                )
            }
        }
        val customEndDateEnabled = selectedQuickRange == "Custom"
        // #region agent log
        LaunchedEffect(customEndDateEnabled, selectedQuickRange, startDate, endDate) {
            agentDebugLog(
                hypothesisId = "X5",
                location = "SettingsScreen.customDateControls",
                message = "custom date controls state",
                data = JSONObject().apply {
                    put("customRangeEnabled", customEndDateEnabled)
                    put("selectedQuickRange", selectedQuickRange)
                    put("startDate", startDate.toString())
                    put("endDate", endDate.toString())
                }
            )
        }
        // #endregion
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
        ) {
            FilterChip(
                selected = false,
                enabled = true,
                onClick = {
                    // #region agent log
                    agentDebugLog(
                        hypothesisId = "X5",
                        location = "SettingsScreen.startDateChip.onClick",
                        message = "start date chip pressed",
                        data = JSONObject().apply {
                            put("customRangeEnabled", true)
                        }
                    )
                    // #endregion
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val picked = LocalDate.of(year, month + 1, dayOfMonth)
                            startDate = picked
                            if (picked.isAfter(endDate)) {
                                endDate = picked
                            }
                            persistMapFiltersOnly()
                        },
                        startDate.year,
                        startDate.monthValue - 1,
                        startDate.dayOfMonth
                    ).show()
                },
                label = { Text("Start: ${startDate.format(prettyDateFormatter)}") },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            )
            FilterChip(
                selected = false,
                enabled = customEndDateEnabled,
                onClick = {
                    // #region agent log
                    agentDebugLog(
                        hypothesisId = "X5",
                        location = "SettingsScreen.endDateChip.onClick",
                        message = "end date chip pressed",
                        data = JSONObject().apply {
                            put("customRangeEnabled", customEndDateEnabled)
                        }
                    )
                    // #endregion
                    if (!customEndDateEnabled) return@FilterChip
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val picked = LocalDate.of(year, month + 1, dayOfMonth)
                            endDate = picked
                            if (picked.isBefore(startDate)) {
                                startDate = picked
                            }
                            persistMapFiltersOnly()
                        },
                        endDate.year,
                        endDate.monthValue - 1,
                        endDate.dayOfMonth
                    ).show()
                },
                label = { Text("End: ${endDate.format(prettyDateFormatter)}") },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Oldest N records")
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = oldestRecordsLimitInput,
            onValueChange = { input ->
                val digitsOnly = input.filter(Char::isDigit)
                oldestRecordsLimitInput = digitsOnly
                persistMapFiltersOnly()
            },
            singleLine = true,
            label = { Text("Leave blank for all") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        val statusText = when (status) {
            "Schema is valid", "Validated", "Not validated" -> null
            else -> status
        }
        if (!statusText.isNullOrBlank()) {
            Text(statusText)
        }
    }
}

private fun normalizeSpreadsheetIdInput(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    if (trimmed.matches(Regex("^[a-zA-Z0-9-_]{20,}$"))) return trimmed
    val fromUrl = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)").find(trimmed)?.groupValues?.getOrNull(1)
    return fromUrl?.takeIf { it.matches(Regex("^[a-zA-Z0-9-_]{20,}$")) }
}

private fun looksLikeDriveDocToken(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.matches(Regex("^[a-zA-Z0-9-_]{56,}$")) && !trimmed.startsWith("1")
}

private fun displaySpreadsheetFileName(value: String?): String? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    return trimmed.substringAfterLast('/').substringAfterLast('\\').trim().ifBlank { null }
}
