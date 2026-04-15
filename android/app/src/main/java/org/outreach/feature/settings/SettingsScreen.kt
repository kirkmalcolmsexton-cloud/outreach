package org.outreach.feature.settings

import android.app.DatePickerDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import org.outreach.core.model.AppConfig
import org.outreach.debug.agentDebugLog
import org.outreach.feature.map.formatBriefComment

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    pickedSpreadsheetId: String? = null,
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
    var lastAutoFilledSpreadsheetId by remember { mutableStateOf<String?>(null) }
    var lastLoadedSpreadsheetId by remember { mutableStateOf<String?>(null) }
    /** Title for the ID currently in the field when it differs from [savedConfig] (not persisted until Save). */
    var pendingSpreadsheetTitle by remember { mutableStateOf<String?>(null) }

    var selectedBriefComments by remember { mutableStateOf(savedConfig.mapBriefCommentFilter) }
    var startDate by remember {
        mutableStateOf(
            savedConfig.mapDateStartIso?.let { LocalDate.parse(it) } ?: earliestVisitationDate
        )
    }
    var endDate by remember {
        mutableStateOf(
            savedConfig.mapDateEndIso?.let { LocalDate.parse(it) } ?: LocalDate.now()
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
            "7D" to 7L,
            "30D" to 30L,
            "90D" to 90L,
            "All" to null
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
        onUpdateConfig(
            savedConfig.copy(
                mapBriefCommentFilter = selectedBriefComments,
                mapDateStartIso = startDate.toString(),
                mapDateEndIso = endDate.toString(),
                mapQuickRange = selectedQuickRange,
                mapOldestRecordsLimit = parseOldestRecordsLimitOrNull(oldestRecordsLimitInput)
            )
        )
    }

    fun persistFullConfig(selectedTabsOverride: Set<String>) {
        onUpdateConfig(
            AppConfig(
                spreadsheetId = resolvedSpreadsheetIdForPersist(),
                spreadsheetTitle = resolvedSpreadsheetTitleForPersist(),
                selectedTabs = selectedTabsOverride,
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
        val canApplySaved =
            spreadsheetId.isBlank() || spreadsheetId == lastAutoFilledSpreadsheetId
        // #region agent log
        agentDebugLog(
            hypothesisId = "D",
            location = "SettingsScreen.LaunchedEffect(savedConfig.sheet)",
            message = "saved spreadsheet effect",
            data = JSONObject().apply {
                put("savedSpreadsheetId", savedConfig.spreadsheetId)
                put("fieldSpreadsheetId", spreadsheetId)
                put("lastAutoFilledSpreadsheetId", lastAutoFilledSpreadsheetId ?: "")
                put("canApplySaved", canApplySaved)
            }
        )
        // #endregion
        if (canApplySaved) {
            spreadsheetId = savedConfig.spreadsheetId
            selectedTabs = savedConfig.selectedTabs
            lastAutoFilledSpreadsheetId = savedConfig.spreadsheetId
        }
    }
    LaunchedEffect(savedConfig.selectedTabs) {
        selectedTabs = savedConfig.selectedTabs
    }

    LaunchedEffect(
        savedConfig.mapBriefCommentFilter,
        savedConfig.mapDateStartIso,
        savedConfig.mapDateEndIso,
        savedConfig.mapQuickRange,
        savedConfig.mapOldestRecordsLimit,
        earliestVisitationDate
    ) {
        selectedBriefComments = savedConfig.mapBriefCommentFilter
        startDate = savedConfig.mapDateStartIso?.let { LocalDate.parse(it) } ?: earliestVisitationDate
        endDate = savedConfig.mapDateEndIso?.let { LocalDate.parse(it) } ?: LocalDate.now()
        selectedQuickRange = savedConfig.mapQuickRange
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
        val currentNorm = normalizeSpreadsheetIdInput(spreadsheetId)
        val shouldApplyPickedValue =
            spreadsheetId.isBlank() ||
                spreadsheetId == lastAutoFilledSpreadsheetId ||
                (pickedNorm != null && pickedNorm != currentNorm) ||
                picked.startsWith("content://")
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
            spreadsheetId = picked
            lastAutoFilledSpreadsheetId = picked
            status = if (picked.startsWith("content://")) {
                "Drive returned a document URI. Paste a Google Sheets URL or raw sheet ID."
            } else {
                "Spreadsheet selected from Drive"
            }
        }
    }
    val normalizedSpreadsheetId = remember(spreadsheetId) {
        normalizeSpreadsheetIdInput(spreadsheetId)
    }
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
                    availableZipTabs = zipTabs
                    selectedTabs = selectedTabs.filterTo(mutableSetOf()) { it in zipTabs }
                    if (zipTabs.isEmpty()) {
                        status = "No ZIP-named tabs found. Expected 12345 or 12345-6789."
                    }
                    lastLoadedSpreadsheetId = normalized
                    isLoadingTabs = false
                    scope.launch {
                        val title = runCatching { onFetchSpreadsheetTitle(normalized) }.getOrNull()
                            ?.takeIf { it.isNotBlank() } ?: return@launch
                        val fieldNorm = normalizeSpreadsheetIdInput(spreadsheetId)
                        if (fieldNorm == savedConfig.spreadsheetId) {
                            onUpdateConfig(savedConfig.copy(spreadsheetTitle = title))
                        } else if (fieldNorm == normalized) {
                            pendingSpreadsheetTitle = title
                        }
                    }
                }
                .onFailure {
                    availableZipTabs = emptyList()
                    status = "Unable to load tabs. Check spreadsheet access and try again."
                    isLoadingTabs = false
                }
        }
    }
    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Configuration")
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = spreadsheetId,
            onValueChange = { spreadsheetId = it },
            label = { Text("Spreadsheet ID or URL") }
        )
        val displaySpreadsheetTitle = when (normalizedSpreadsheetId) {
            savedConfig.spreadsheetId -> savedConfig.spreadsheetTitle
            else -> pendingSpreadsheetTitle
        }
        if (!displaySpreadsheetTitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                displaySpreadsheetTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            normalizedSpreadsheetId == null -> Text("Enter a valid spreadsheet ID to load ZIP tabs.")
            isLoadingTabs -> Text("Loading tabs...")
            availableZipTabs.isEmpty() -> Text("No matching ZIP tabs available.")
            else -> {
                LazyRow {
                    items(availableZipTabs.sorted()) { tab ->
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
                            label = { Text(tab) },
                            modifier = Modifier.padding(end = 8.dp)
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
            LazyRow {
                items(briefCommentOptions) { briefComment ->
                    val selected = briefComment in selectedBriefComments
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedBriefComments = if (selected) {
                                selectedBriefComments - briefComment
                            } else {
                                selectedBriefComments + briefComment
                            }
                            persistMapFiltersOnly()
                        },
                        label = { Text(formatBriefComment(briefComment)) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
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
        Text("Last visited date range")
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow {
            items(quickRangeOptions) { (label, days) ->
                val selected = selectedQuickRange == label
                FilterChip(
                    selected = selected,
                    onClick = {
                        selectedQuickRange = label
                        if (days == null) {
                            startDate = earliestVisitationDate
                            endDate = LocalDate.now()
                        } else {
                            endDate = LocalDate.now()
                            val rangeStart = endDate.minusDays(days)
                            startDate = if (rangeStart.isBefore(earliestVisitationDate)) {
                                earliestVisitationDate
                            } else {
                                rangeStart
                            }
                        }
                        persistMapFiltersOnly()
                    },
                    label = { Text(label) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
        Row(modifier = Modifier.padding(top = 8.dp)) {
            FilterChip(
                selected = false,
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val picked = LocalDate.of(year, month + 1, dayOfMonth)
                            startDate = picked
                            if (picked.isAfter(endDate)) {
                                endDate = picked
                            }
                            selectedQuickRange = "All"
                            persistMapFiltersOnly()
                        },
                        startDate.year,
                        startDate.monthValue - 1,
                        startDate.dayOfMonth
                    ).show()
                },
                label = { Text("Start: ${startDate.format(prettyDateFormatter)}") },
                modifier = Modifier.padding(end = 8.dp)
            )
            FilterChip(
                selected = false,
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val picked = LocalDate.of(year, month + 1, dayOfMonth)
                            endDate = picked
                            if (picked.isBefore(startDate)) {
                                startDate = picked
                            }
                            selectedQuickRange = "All"
                            persistMapFiltersOnly()
                        },
                        endDate.year,
                        endDate.monthValue - 1,
                        endDate.dayOfMonth
                    ).show()
                },
                label = { Text("End: ${endDate.format(prettyDateFormatter)}") },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val normalized = normalizeSpreadsheetIdInput(spreadsheetId)
                if (normalized == null) {
                    status = "Could not save: paste a Google Sheets URL (contains /spreadsheets/d/...) or raw Sheet ID."
                } else {
                    onUpdateConfig(
                        AppConfig(
                            spreadsheetId = normalized,
                            spreadsheetTitle = resolvedSpreadsheetTitleForPersist(),
                            selectedTabs = selectedTabs,
                            mapBriefCommentFilter = selectedBriefComments,
                            mapDateStartIso = startDate.toString(),
                            mapDateEndIso = endDate.toString(),
                            mapQuickRange = selectedQuickRange,
                            mapOldestRecordsLimit = parseOldestRecordsLimitOrNull(oldestRecordsLimitInput)
                        )
                    )
                    lastAutoFilledSpreadsheetId = normalized
                    status = "Saved config"
                }
            }
        ) {
            Text("Save Sheet Configuration")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            enabled = !isSyncing,
            onClick = {
                val normalized = normalizeSpreadsheetIdInput(spreadsheetId)
                if (normalized == null) {
                    status =
                        "Could not sync: paste a Google Sheets URL (contains /spreadsheets/d/...) or raw Sheet ID."
                    return@Button
                }
                if (selectedTabs.isEmpty()) {
                    status = "Select at least one ZIP tab to sync."
                    return@Button
                }
                scope.launch {
                    isSyncing = true
                    try {
                        onSyncFromSpreadsheet(
                            AppConfig(
                                spreadsheetId = normalized,
                                spreadsheetTitle = resolvedSpreadsheetTitleForPersist(),
                                selectedTabs = selectedTabs,
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
        ) {
            Text(if (isSyncing) "Syncing…" else "Sync from spreadsheet")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val normalized = normalizeSpreadsheetIdInput(spreadsheetId)
                if (normalized == null) {
                    status = "Cannot validate URI from Drive picker. Paste a Google Sheets URL or raw Sheet ID."
                    return@Button
                }
                status = "Validating schema..."
                onValidateSchema(normalized, selectedTabs) { valid ->
                    status = if (valid) "Schema is valid" else "Schema invalid or inaccessible"
                }
            }
        ) {
            Text("Validate Sheet Schema")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onPickSheetFromDrive) {
            Text("Select Sheet from Drive")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(status)
    }
}

private fun normalizeSpreadsheetIdInput(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    if (trimmed.matches(Regex("^[a-zA-Z0-9-_]{20,}$"))) return trimmed
    val fromUrl = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)").find(trimmed)?.groupValues?.getOrNull(1)
    return fromUrl?.takeIf { it.matches(Regex("^[a-zA-Z0-9-_]{20,}$")) }
}
