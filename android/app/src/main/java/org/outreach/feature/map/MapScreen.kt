package org.outreach.feature.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
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
    val hasMapsApiMetadata = remember {
        runCatching {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_META_DATA
            )
            val value = appInfo.metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
            value.isNotBlank()
        }.getOrDefault(false)
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
    var viewMode by remember { mutableStateOf("map") }
    var selectedTab by remember { mutableStateOf(tabs.firstOrNull().orEmpty()) }
    LaunchedEffect(tabs) {
        if (tabs.isNotEmpty() && selectedTab !in tabs) {
            selectedTab = tabs.first()
        }
    }
    val filteredData = remember(data, selectedTab) {
        data.filter { selectedTab.isBlank() || it.source.sheetName == selectedTab }
    }
    val mapMarkers = remember(filteredData) {
        filteredData.mapNotNull { household ->
            val lat = household.latitude
            val lng = household.longitude
            if (lat == null || lng == null) null else household to LatLng(lat, lng)
        }
    }
    val defaultCenter = LatLng(41.8781, -87.6298)
    val cameraPositionState = rememberCameraPositionState()
    LaunchedEffect(mapMarkers) {
        if (mapMarkers.isNotEmpty()) {
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(mapMarkers.first().second, 13f)
            )
        } else {
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(defaultCenter, 10f)
            )
        }
    }
    LaunchedEffect(viewMode, filteredData.size, mapMarkers.size, hasMapsApiMetadata) {
        if (BuildConfig.DEBUG) {
            // #region agent log
            AgentDebugLogger.log(
                runId = "run12",
                hypothesisId = "H40",
                location = "MapScreen.kt:mapRenderState",
                message = "Home map/list render state changed",
                data = mapOf(
                    "viewMode" to viewMode,
                    "filteredCount" to filteredData.size,
                    "markerCount" to mapMarkers.size,
                    "hasMapsApiMetadata" to hasMapsApiMetadata
                )
            )
            // #endregion
        }
    }
    Column(modifier.fillMaxSize().padding(12.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(bottom = 8.dp)) {
            SegmentedButton(
                selected = viewMode == "map",
                onClick = {
                    viewMode = "map"
                },
                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                    index = 0,
                    count = 2
                ),
                label = { Text("Map") }
            )
            SegmentedButton(
                selected = viewMode == "list",
                onClick = {
                    viewMode = "list"
                },
                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                    index = 1,
                    count = 2
                ),
                label = { Text("List") }
            )
        }
        Text(if (viewMode == "map") "Map pins" else "Address list")
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
        if (viewMode == "map") {
            if (!hasMapsApiMetadata) {
                Text(
                    "Map API key missing. Add MAPS_API_KEY in Gradle properties to render map tiles.",
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            if (mapMarkers.isEmpty()) {
                Text(
                    "No coordinates available for current filter. Showing map center only.",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            GoogleMap(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(360.dp)
                    .background(Color(0xFFECEFF1)),
                properties = MapProperties(isMyLocationEnabled = false),
                cameraPositionState = cameraPositionState
            ) {
                mapMarkers.forEach { (household, position) ->
                    Marker(
                        state = MarkerState(position = position),
                        title = household.name,
                        snippet = household.streetAddress
                    )
                }
            }
        } else {
            LazyColumn {
                items(filteredData) { household ->
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
}
