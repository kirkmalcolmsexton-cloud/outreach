package org.outreach.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.outreach.app.BuildConfig
import org.outreach.core.debug.AgentDebugLogger

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    pickedSpreadsheetId: String? = null,
    savedSpreadsheetId: String = "",
    savedTabs: Set<String> = emptySet(),
    onSaveConfig: (spreadsheetId: String, tabs: Set<String>) -> Unit = { _, _ -> },
    onValidateSchema: (spreadsheetId: String, tabs: Set<String>, onResult: (Boolean) -> Unit) -> Unit = { _, _, _ -> },
    onLoadTabs: (spreadsheetId: String, onResult: (Result<List<String>>) -> Unit) -> Unit = { _, onResult -> onResult(Result.success(emptyList())) },
    onPickSheetFromDrive: () -> Unit = {}
) {
    val zipTabPattern = remember { Regex("^\\d{5}(-\\d{4})?$") }
    var spreadsheetId by remember { mutableStateOf("") }
    var selectedTabs by remember { mutableStateOf(savedTabs) }
    var availableZipTabs by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingTabs by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Not validated") }
    var lastAutoFilledSpreadsheetId by remember { mutableStateOf<String?>(null) }
    var lastLoadedSpreadsheetId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(savedSpreadsheetId, savedTabs) {
        if (savedSpreadsheetId.isBlank()) return@LaunchedEffect
        val canApplySaved =
            spreadsheetId.isBlank() || spreadsheetId == lastAutoFilledSpreadsheetId
        if (canApplySaved) {
            spreadsheetId = savedSpreadsheetId
            selectedTabs = savedTabs
            // #region agent log
            if (BuildConfig.DEBUG) {
                AgentDebugLogger.log(
                    runId = "run8",
                    hypothesisId = "H21",
                    location = "SettingsScreen.kt:LaunchedEffect(savedSpreadsheetId)",
                    message = "Loaded saved configuration into settings fields",
                    data = mapOf(
                        "savedSpreadsheetPrefix" to savedSpreadsheetId.take(64),
                        "savedTabsCount" to savedTabs.size
                    )
                )
            }
            // #endregion
        }
    }
    LaunchedEffect(savedTabs) {
        selectedTabs = savedTabs
    }
    LaunchedEffect(pickedSpreadsheetId) {
        // #region agent log
        if (BuildConfig.DEBUG) {
            AgentDebugLogger.log(
                runId = "run2",
                hypothesisId = "H7",
                location = "SettingsScreen.kt:LaunchedEffect(pickedSpreadsheetId)",
                message = "Settings received picker result",
                data = mapOf(
                    "pickedIsNull" to (pickedSpreadsheetId == null),
                    "pickedPrefix" to (pickedSpreadsheetId?.take(64) ?: ""),
                    "pickedLooksLikeContentUri" to (pickedSpreadsheetId?.startsWith("content://") ?: false)
                )
            )
        }
        // #endregion
        if (!pickedSpreadsheetId.isNullOrBlank()) {
            val shouldApplyPickedValue =
                spreadsheetId.isBlank() || spreadsheetId == lastAutoFilledSpreadsheetId
            if (shouldApplyPickedValue) {
                spreadsheetId = pickedSpreadsheetId
                lastAutoFilledSpreadsheetId = pickedSpreadsheetId
                status = if (pickedSpreadsheetId.startsWith("content://")) {
                    "Drive returned a document URI. Paste a Google Sheets URL or raw sheet ID."
                } else {
                    "Spreadsheet selected from Drive"
                }
            } else {
                // #region agent log
                if (BuildConfig.DEBUG) {
                    AgentDebugLogger.log(
                        runId = "run6",
                        hypothesisId = "H18",
                        location = "SettingsScreen.kt:LaunchedEffect(pickedSpreadsheetId)",
                        message = "Skipped auto-fill to preserve manual spreadsheet edit",
                        data = mapOf(
                            "currentPrefix" to spreadsheetId.take(64),
                            "pickedPrefix" to pickedSpreadsheetId.take(64)
                        )
                    )
                }
                // #endregion
            }
        }
    }
    val normalizedSpreadsheetId = remember(spreadsheetId) {
        normalizeSpreadsheetIdInput(spreadsheetId)
    }
    LaunchedEffect(normalizedSpreadsheetId) {
        val normalized = normalizedSpreadsheetId
        if (normalized == null) {
            availableZipTabs = emptyList()
            isLoadingTabs = false
            return@LaunchedEffect
        }
        if (normalized == lastLoadedSpreadsheetId) return@LaunchedEffect
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
                }
                .onFailure {
                    availableZipTabs = emptyList()
                    status = "Unable to load tabs. Check spreadsheet access and try again."
                    isLoadingTabs = false
                }
        }
    }
    Column(modifier.padding(16.dp)) {
        Text("Configuration")
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = spreadsheetId,
            onValueChange = {
                spreadsheetId = it
                if (BuildConfig.DEBUG && (it.startsWith("http") || it.contains("/spreadsheets/d/"))) {
                    // #region agent log
                    AgentDebugLogger.log(
                        runId = "run6",
                        hypothesisId = "H19",
                        location = "SettingsScreen.kt:spreadsheetIdOnValueChange",
                        message = "Spreadsheet input changed to URL-like value",
                        data = mapOf(
                            "inputPrefix" to it.take(64),
                            "inputLength" to it.length
                        )
                    )
                    // #endregion
                }
            },
            label = { Text("Spreadsheet ID") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("ZIP tabs from spreadsheet")
        Spacer(modifier = Modifier.height(4.dp))
        when {
            normalizedSpreadsheetId == null -> Text("Enter a valid spreadsheet ID to load tabs.")
            isLoadingTabs -> Text("Loading tabs...")
            availableZipTabs.isEmpty() -> Text("No matching ZIP tabs available.")
            else -> {
                availableZipTabs.forEach { tab ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = tab in selectedTabs,
                            onCheckedChange = { checked ->
                                selectedTabs = if (checked) {
                                    selectedTabs + tab
                                } else {
                                    selectedTabs - tab
                                }
                            }
                        )
                        Text(tab, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val normalized = normalizeSpreadsheetIdInput(spreadsheetId)
                // #region agent log
                if (BuildConfig.DEBUG) {
                    AgentDebugLogger.log(
                        runId = "run2",
                        hypothesisId = "H8",
                        location = "SettingsScreen.kt:SaveSheetConfiguration",
                        message = "User tapped Save Sheet Configuration",
                        data = mapOf(
                            "spreadsheetInputPrefix" to spreadsheetId.take(64),
                            "spreadsheetInputLength" to spreadsheetId.length,
                            "tabsCount" to selectedTabs.size
                        )
                    )
                }
                // #endregion
                if (normalized == null) {
                    // #region agent log
                    if (BuildConfig.DEBUG) {
                        AgentDebugLogger.log(
                            runId = "run4",
                            hypothesisId = "H15",
                            location = "SettingsScreen.kt:SaveSheetConfiguration",
                            message = "Rejected save due to invalid spreadsheet identifier",
                            data = mapOf(
                                "looksLikeContentUri" to spreadsheetId.trim().startsWith("content://"),
                                "inputSuffix" to spreadsheetId.takeLast(18)
                            )
                        )
                    }
                    // #endregion
                    status = "Could not save: paste a Google Sheets URL (contains /spreadsheets/d/...) or raw Sheet ID."
                } else {
                    onSaveConfig(normalized, selectedTabs)
                    lastAutoFilledSpreadsheetId = normalized
                    status = "Saved config"
                }
            }
        ) {
            Text("Save Sheet Configuration")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val normalized = normalizeSpreadsheetIdInput(spreadsheetId)
                // #region agent log
                if (BuildConfig.DEBUG) {
                    AgentDebugLogger.log(
                        runId = "run2",
                        hypothesisId = "H9",
                        location = "SettingsScreen.kt:ValidateSheetSchema",
                        message = "User tapped Validate Sheet Schema",
                        data = mapOf(
                            "spreadsheetInputPrefix" to spreadsheetId.take(64),
                            "spreadsheetInputLength" to spreadsheetId.length,
                            "tabsCount" to selectedTabs.size
                        )
                    )
                }
                // #endregion
                if (normalized == null) {
                    // #region agent log
                    if (BuildConfig.DEBUG) {
                        AgentDebugLogger.log(
                            runId = "run4",
                            hypothesisId = "H16",
                            location = "SettingsScreen.kt:ValidateSheetSchema",
                            message = "Blocked validation due to invalid spreadsheet identifier",
                            data = mapOf(
                                "looksLikeContentUri" to spreadsheetId.trim().startsWith("content://"),
                                "inputSuffix" to spreadsheetId.takeLast(18)
                            )
                        )
                    }
                    // #endregion
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
