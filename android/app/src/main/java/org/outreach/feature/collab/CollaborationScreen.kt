package org.outreach.feature.collab

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.outreach.core.model.CollaborationEvent

@Composable
fun CollaborationScreen(
    modifier: Modifier = Modifier,
    onPublishPresence: () -> Unit = {}
) {
    val activity = remember {
        mutableStateListOf(
            CollaborationEvent(
                userId = "user-a",
                householdId = "household_sample_1",
                tabName = "60657",
                type = "visit_logged",
                epochMillis = System.currentTimeMillis()
            )
        )
    }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Team coordination")
        Button(onClick = onPublishPresence) {
            Text("Publish presence in selected tab")
        }
        LazyColumn {
            items(activity) { event ->
                Card(modifier = Modifier.padding(vertical = 6.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${event.userId} -> ${event.type}")
                        Text("Tab ${event.tabName} / household ${event.householdId}")
                    }
                }
            }
        }
    }
}
