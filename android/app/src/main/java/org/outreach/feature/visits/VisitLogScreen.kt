package org.outreach.feature.visits

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import org.outreach.core.model.HouseholdRecord
import org.outreach.core.model.normalizeBriefComment
import org.outreach.debug.agentDebugLog
import org.outreach.feature.map.shareHouseholdLocation

private val defaultBriefPresets = listOf(
    "Not home",
    "Left message",
    "Receptive",
    "Do not visit",
    "Moved",
    "Dawat saath",
    "Other"
)

private fun effectivePresets(fromSheet: List<String>): List<String> =
    if (fromSheet.isNotEmpty()) fromSheet else defaultBriefPresets

private fun presetMatchesStored(preset: String, storedBrief: String): Boolean {
    val p = preset.trim()
    if (p.equals(storedBrief, ignoreCase = true)) return true
    if (normalizeBriefComment(p) == storedBrief) return true
    return false
}

private const val debugLogPath = "/Users/aqeel/development/cursor/workspaces/initial/.cursor/debug-e50947.log"
private const val debugSessionId = "e50947"
private const val debugEndpointPrimary = "http://127.0.0.1:7747/ingest/f7368b29-184e-4539-ae18-cb2344a4388c"
private const val debugEndpointEmulator = "http://10.0.2.2:7747/ingest/f7368b29-184e-4539-ae18-cb2344a4388c"

private fun escapeJson(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

private fun appendDebugLog(
    runId: String,
    hypothesisId: String,
    location: String,
    message: String,
    data: Map<String, Any?>
) {
    runCatching {
        val dataJson = data.entries.joinToString(",") { (k, v) ->
            val serialized = when (v) {
                null -> "null"
                is Number, is Boolean -> v.toString()
                else -> "\"${escapeJson(v.toString())}\""
            }
            "\"${escapeJson(k)}\":$serialized"
        }
        val line =
            "{\"sessionId\":\"$debugSessionId\",\"id\":\"${UUID.randomUUID()}\",\"timestamp\":${System.currentTimeMillis()},\"runId\":\"${escapeJson(runId)}\",\"hypothesisId\":\"${escapeJson(hypothesisId)}\",\"location\":\"${escapeJson(location)}\",\"message\":\"${escapeJson(message)}\",\"data\":{$dataJson}}\n"
        runCatching { File(debugLogPath).appendText(line) }
        Thread {
            postDebugLine(debugEndpointPrimary, line)
            postDebugLine(debugEndpointEmulator, line)
        }.start()
    }
}

private fun postDebugLine(endpoint: String, line: String) {
    runCatching {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 1000
            readTimeout = 1000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Debug-Session-Id", debugSessionId)
        }
        connection.outputStream.use { it.write(line.toByteArray()) }
        runCatching { connection.inputStream.close() }
        connection.disconnect()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitLogScreen(
    modifier: Modifier = Modifier,
    selectedHousehold: HouseholdRecord?,
    briefCommentPresets: List<String>,
    presetsLoading: Boolean = false,
    onSaveVisit: (householdId: String, briefComment: String, notes: String?, lastVisitedIsoDate: String) -> Unit =
        { _, _, _, _ -> }
) {
    val debugRunId = remember { "initial-${System.currentTimeMillis()}" }
    val composeCounter = remember { AtomicInteger(0) }
    val dateLabel = remember {
        DateTimeFormatter.ofPattern("MMM d, yyyy")
    }
    val context = LocalContext.current
    val scroll = rememberScrollState()
    var visitDate by remember { mutableStateOf(LocalDate.now()) }
    var notes by remember { mutableStateOf("") }
    var useCustomBrief by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf<String?>(null) }
    var customBrief by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    SideEffect {
        val composeCount = composeCounter.incrementAndGet()
        // #region agent log
        agentDebugLog(
            hypothesisId = "H5",
            location = "VisitLogScreen.SideEffect",
            message = "visits recomposition snapshot",
            data = JSONObject().apply {
                put("composeCount", composeCount)
                put("selectedHouseholdPresent", selectedHousehold != null)
                put("usesSelectionContainerForDetails", true)
            },
            runId = debugRunId
        )
        // #endregion
        // #region agent log
        appendDebugLog(
            runId = debugRunId,
            hypothesisId = "H4",
            location = "VisitLogScreen.kt:SideEffect",
            message = "Visits screen recomposed",
            data = mapOf(
                "composeCount" to composeCount,
                "hasSelectedHousehold" to (selectedHousehold != null),
                "useCustomBrief" to useCustomBrief,
                "dropdownExpanded" to dropdownExpanded
            )
        )
        // #endregion
    }

    LaunchedEffect(selectedHousehold?.id) {
        visitDate = LocalDate.now()
        // #region agent log
        appendDebugLog(
            runId = debugRunId,
            hypothesisId = "H2",
            location = "VisitLogScreen.kt:LaunchedEffect(selectedHousehold?.id)",
            message = "Selected household changed",
            data = mapOf(
                "selectedHouseholdPresent" to (selectedHousehold != null),
                "visitDate" to visitDate.toString()
            )
        )
        // #endregion
    }

    LaunchedEffect(selectedHousehold?.id, briefCommentPresets) {
        val h = selectedHousehold ?: return@LaunchedEffect
        notes = h.notes.orEmpty()
        val presets = effectivePresets(briefCommentPresets)
        val match = presets.firstOrNull { presetMatchesStored(it, h.briefComment) }
        if (match != null) {
            useCustomBrief = false
            selectedPreset = match
            customBrief = ""
        } else {
            useCustomBrief = true
            selectedPreset = null
            customBrief = h.briefComment
        }
        // #region agent log
        appendDebugLog(
            runId = debugRunId,
            hypothesisId = "H3",
            location = "VisitLogScreen.kt:LaunchedEffect(selectedHousehold?.id, briefCommentPresets)",
            message = "Derived brief state from selected household",
            data = mapOf(
                "matchedPreset" to (match != null),
                "briefLength" to h.briefComment.length,
                "hasNeighborhood" to h.neighborhood.isNotBlank(),
                "notesLength" to notes.length
            )
        )
        // #endregion
    }

    Column(modifier.padding(16.dp).verticalScroll(scroll)) {
        Text("Visit update")
        Spacer(Modifier.height(8.dp))

        if (selectedHousehold == null) {
            // #region agent log
            appendDebugLog(
                runId = debugRunId,
                hypothesisId = "H1",
                location = "VisitLogScreen.kt:selectedHousehold==null",
                message = "Rendering empty-state selection container",
                data = mapOf("composeCount" to composeCounter.get())
            )
            // #endregion
            SelectionContainer {
                Text("Select someone from the Home map or list, then return here to log a visit.")
            }
            Spacer(Modifier.height(8.dp))
        } else {
            val formattedBrief =
                selectedHousehold.briefComment
                    .replace("_", " ")
                    .split(" ")
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { t ->
                        t.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
                    }
            // #region agent log
            appendDebugLog(
                runId = debugRunId,
                hypothesisId = "H1",
                location = "VisitLogScreen.kt:selectedHousehold!=null",
                message = "Rendering selected-household selection container",
                data = mapOf(
                    "composeCount" to composeCounter.get(),
                    "nameLength" to selectedHousehold.name.length,
                    "addressLength" to selectedHousehold.streetAddress.length,
                    "formattedBriefLength" to formattedBrief.length,
                    "formattedBriefHasNewline" to formattedBrief.contains("\n")
                )
            )
            // #endregion
            SelectionContainer {
                Column {
                    Text("Name: ${selectedHousehold.name}")
                    Text("Address: ${selectedHousehold.streetAddress}")
                    if (selectedHousehold.neighborhood.isNotBlank()) {
                        Text("Neighborhood: ${selectedHousehold.neighborhood}")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Current brief: ${
                            formattedBrief
                        }"
                    )
                    Text(
                        "Last visited: ${
                            selectedHousehold.lastVisited?.let { runCatching { LocalDate.parse(it).format(dateLabel) }.getOrNull() }
                                ?: "Not visited"
                        }"
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { shareHouseholdLocation(context, selectedHousehold) }
            ) {
                Text("Share location")
            }
            Spacer(Modifier.height(12.dp))
        }

        FilterChip(
            selected = false,
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        visitDate = LocalDate.of(year, month + 1, dayOfMonth)
                    },
                    visitDate.year,
                    visitDate.monthValue - 1,
                    visitDate.dayOfMonth
                ).show()
            },
            label = { Text("Visit date: ${visitDate.format(dateLabel)}") }
        )
        Spacer(Modifier.height(12.dp))

        if (presetsLoading) {
            Text("Loading brief comment options…")
            Spacer(Modifier.height(8.dp))
        }

        val presets = effectivePresets(briefCommentPresets)
        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { dropdownExpanded = !dropdownExpanded }
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                readOnly = true,
                value = when {
                    useCustomBrief -> "Custom…"
                    selectedPreset != null -> selectedPreset!!
                    else -> ""
                },
                onValueChange = {},
                label = { Text("Brief comment") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false }
            ) {
                presets.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            selectedPreset = option
                            useCustomBrief = false
                            customBrief = ""
                            dropdownExpanded = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Custom…") },
                    onClick = {
                        useCustomBrief = true
                        selectedPreset = null
                        dropdownExpanded = false
                    }
                )
            }
        }

        if (useCustomBrief) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = customBrief,
                onValueChange = { customBrief = it },
                label = { Text("Custom brief comment") }
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") }
        )
        Spacer(Modifier.height(12.dp))

        val briefToSave = when {
            useCustomBrief -> customBrief.trim()
            selectedPreset != null -> selectedPreset!!.trim()
            else -> ""
        }
        val canSave = selectedHousehold != null && briefToSave.isNotBlank()

        Button(
            enabled = canSave,
            onClick = {
                val id = selectedHousehold?.id ?: return@Button
                onSaveVisit(
                    id,
                    briefToSave,
                    notes.trim().ifBlank { null },
                    visitDate.toString()
                )
            }
        ) {
            Text("Save offline + queue sync")
        }
    }
}
