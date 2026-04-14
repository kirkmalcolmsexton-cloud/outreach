package org.outreach.feature.visits

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.outreach.core.model.HouseholdRecord
import org.outreach.core.model.normalizeBriefComment

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

    LaunchedEffect(selectedHousehold?.id) {
        visitDate = LocalDate.now()
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
    }

    Column(modifier.padding(16.dp).verticalScroll(scroll)) {
        Text("Visit update")
        Spacer(Modifier.height(8.dp))

        if (selectedHousehold == null) {
            Text("Select someone from the Home map or list, then return here to log a visit.")
            Spacer(Modifier.height(8.dp))
        } else {
            Text("Name: ${selectedHousehold.name}")
            Text("Address: ${selectedHousehold.streetAddress}")
            if (selectedHousehold.neighborhood.isNotBlank()) {
                Text("Neighborhood: ${selectedHousehold.neighborhood}")
            }
            Text(
                "Current brief: ${
                    selectedHousehold.briefComment
                        .replace("_", " ")
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { t ->
                            t.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
                        }
                }"
            )
            Text(
                "Last visited: ${
                    selectedHousehold.lastVisited?.let { runCatching { LocalDate.parse(it).format(dateLabel) }.getOrNull() }
                        ?: "Not visited"
                }"
            )
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
