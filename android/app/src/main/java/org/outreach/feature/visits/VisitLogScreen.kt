package org.outreach.feature.visits

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VisitLogScreen(
    modifier: Modifier = Modifier,
    onSaveVisit: (householdId: String, briefComment: String, notes: String?) -> Unit = { _, _, _ -> }
) {
    var householdId by remember { mutableStateOf("") }
    var briefComment by remember { mutableStateOf("left_message") }
    var notes by remember { mutableStateOf("") }
    Column(modifier.padding(16.dp)) {
        Text("Visit update")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = householdId,
            onValueChange = { householdId = it },
            label = { Text("Household ID") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = briefComment,
            onValueChange = { briefComment = it },
            label = { Text("Brief Comment") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") }
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                onSaveVisit(
                    householdId.trim(),
                    briefComment.trim(),
                    notes.trim().ifBlank { null }
                )
            }
        ) {
            Text("Save offline + queue sync")
        }
    }
}
