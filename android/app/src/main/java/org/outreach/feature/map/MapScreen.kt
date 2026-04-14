package org.outreach.feature.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import org.outreach.app.BuildConfig
import org.outreach.core.debug.AgentDebugLogger
import org.outreach.core.model.HouseholdRecord
import org.outreach.core.model.RawHouseholdRow
import org.outreach.core.model.SourceMetadata

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    households: List<HouseholdRecord> = emptyList()
) {
    val context = LocalContext.current
    LaunchedEffect(households.size) {
        if (BuildConfig.DEBUG) {
            val hasMapsApiMetadata = runCatching {
                val appInfo = context.packageManager.getApplicationInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_META_DATA
                )
                val value = appInfo.metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
                value.isNotBlank()
            }.getOrDefault(false)
            // #region agent log
            AgentDebugLogger.log(
                runId = "run9",
                hypothesisId = "H30",
                location = "MapScreen.kt:MapScreen",
                message = "Rendering Home map fallback screen",
                data = mapOf(
                    "householdsCount" to households.size,
                    "hasMapsApiMetadata" to hasMapsApiMetadata
                )
            )
            // #endregion
        }
    }
    val data = remember(households) {
        if (households.isNotEmpty()) households else listOf(
            HouseholdRecord(
                id = "household_sample_1",
                name = "Sample Household",
                streetAddress = "123 Main St, Chicago, IL",
                neighborhood = "Lakeview",
                briefComment = "not_home",
                lastVisited = null,
                notes = null,
                source = SourceMetadata("60657", 2),
                raw = RawHouseholdRow()
            )
        )
    }
    val tabs = data.map { it.source.sheetName }.distinct()
    var selectedTab by remember { mutableStateOf(tabs.firstOrNull().orEmpty()) }
    Column(modifier.fillMaxSize().padding(12.dp)) {
        Text("Map pins (list fallback until key is configured)")
        LazyRow {
            items(tabs) { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    label = { Text(tab) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
        LazyColumn {
            items(data.filter { selectedTab.isBlank() || it.source.sheetName == selectedTab }) { household ->
                Card(
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .clickable {
                            val uri = Uri.parse(
                                "google.navigation:q=${Uri.encode(household.streetAddress)}"
                            )
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, uri).apply {
                                    setPackage("com.google.android.apps.maps")
                                }
                            )
                        }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(household.name)
                        Text(household.streetAddress)
                        Text("Tab: ${household.source.sheetName}")
                    }
                }
            }
        }
    }
}
