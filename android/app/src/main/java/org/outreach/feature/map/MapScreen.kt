package org.outreach.feature.map

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.Circle
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import org.outreach.core.model.HouseholdRecord
import org.outreach.core.model.RawHouseholdRow
import org.outreach.core.model.SourceMetadata
import kotlin.concurrent.thread
import java.net.HttpURLConnection
import java.net.URL

// #region agent log
private fun mapPinNavDebug(
    hypothesisId: String,
    location: String,
    message: String,
    data: Map<String, Any?> = emptyMap(),
    runId: String = "nav-from-pin-pre"
) {
    val payload = JSONObject().apply {
        put("sessionId", "1abea7")
        put("runId", runId)
        put("hypothesisId", hypothesisId)
        put("location", location)
        put("message", message)
        put("timestamp", System.currentTimeMillis())
        put("data", JSONObject(data))
    }
    android.util.Log.d("MapPinNavDbg", payload.toString())
    thread(start = true) {
        for (host in listOf("127.0.0.1", "10.0.2.2")) {
            runCatching {
                val conn = (URL("http://$host:7747/ingest/f7368b29-184e-4539-ae18-cb2344a4388c").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 2000
                    readTimeout = 2000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Debug-Session-Id", "1abea7")
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                conn.responseCode
                conn.disconnect()
            }
        }
    }
}
// #endregion

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    households: List<HouseholdRecord> = emptyList(),
    selectedHouseholdId: String? = null,
    onHouseholdSelected: (HouseholdRecord) -> Unit = {}
) {
    val prettyDateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
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
    val allTabsLabel = "All"
    val tabs = data.map { it.source.sheetName }.distinct()
    val briefCommentOptions = remember(data) { data.map { it.briefComment }.distinct().sorted() }
    val visitationDates = remember(data) {
        data.mapNotNull { parseIsoDateOrNull(it.lastVisited) }
    }
    val earliestVisitationDate = remember(visitationDates) {
        visitationDates.minOrNull() ?: LocalDate.now()
    }
    val quickRangeOptions = remember {
        listOf(
            "7D" to 7L,
            "30D" to 30L,
            "90D" to 90L,
            "All" to null
        )
    }
    var viewMode by remember { mutableStateOf("map") }
    var selectedTab by remember { mutableStateOf(allTabsLabel) }
    var selectedBriefComments by remember { mutableStateOf(setOf<String>()) }
    var startDate by remember { mutableStateOf(earliestVisitationDate) }
    var endDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedQuickRange by remember { mutableStateOf("All") }
    LaunchedEffect(tabs) {
        val validTab = selectedTab == allTabsLabel || selectedTab in tabs
        if (!validTab) {
            selectedTab = allTabsLabel
        }
    }
    LaunchedEffect(earliestVisitationDate) {
        if (startDate.isBefore(earliestVisitationDate)) {
            startDate = earliestVisitationDate
        }
    }
    val filteredData = remember(data, selectedTab, selectedBriefComments, startDate, endDate) {
        data.filter { household ->
            val tabMatches = selectedTab == allTabsLabel || household.source.sheetName == selectedTab
            val briefCommentMatches = selectedBriefComments.isEmpty() || household.briefComment in selectedBriefComments
            val visitationDate = parseIsoDateOrNull(household.lastVisited)
            val dateMatches = visitationDate == null || (!visitationDate.isBefore(startDate) && !visitationDate.isAfter(endDate))
            tabMatches && briefCommentMatches && dateMatches
        }
    }
    val mapMarkers = remember(filteredData) {
        filteredData.mapNotNull { household ->
            val lat = household.latitude
            val lng = household.longitude
            if (lat == null || lng == null) null else household to LatLng(lat, lng)
        }
    }
    val filteredWithoutCoordinates = remember(filteredData) {
        filteredData.count { it.latitude == null || it.longitude == null }
    }
    val defaultCenter = LatLng(41.8781, -87.6298)
    val cameraPositionState = rememberCameraPositionState()
    val selectionZoom = 15f
    val haloFillColor = Color(0x403675F6)
    val haloStrokeColor = Color(0xFF6750A4)

    LaunchedEffect(mapMarkers, selectedHouseholdId) {
        if (selectedHouseholdId != null) return@LaunchedEffect
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

    LaunchedEffect(selectedHouseholdId, viewMode, mapMarkers) {
        if (viewMode != "map") return@LaunchedEffect
        val id = selectedHouseholdId ?: return@LaunchedEffect
        val target = mapMarkers.firstOrNull { (h, _) -> h.id == id } ?: return@LaunchedEffect
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(target.second, selectionZoom),
            400
        )
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
            items(listOf(allTabsLabel) + tabs) { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    label = { Text(tab) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
        LazyRow(modifier = Modifier.padding(top = 8.dp)) {
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
                    },
                    label = { Text(formatBriefComment(briefComment)) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
        LazyRow(modifier = Modifier.padding(top = 8.dp)) {
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
                    },
                    label = { Text(label) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
        LazyRow(modifier = Modifier.padding(top = 8.dp)) {
            item {
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
                            },
                            startDate.year,
                            startDate.monthValue - 1,
                            startDate.dayOfMonth
                        ).show()
                    },
                    label = { Text("Start: ${startDate.format(prettyDateFormatter)}") },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            item {
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
            if (filteredWithoutCoordinates > 0) {
                Text(
                    "${filteredWithoutCoordinates} filtered address(es) have no coordinates yet (geocoding may still be in progress or failed).",
                    modifier = Modifier.padding(top = 4.dp)
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
                val selectedPosition = selectedHouseholdId?.let { sid ->
                    mapMarkers.firstOrNull { (h, _) -> h.id == sid }?.second
                }
                selectedPosition?.let { center ->
                    Circle(
                        center = center,
                        radius = 80.0,
                        fillColor = haloFillColor,
                        strokeColor = haloStrokeColor,
                        strokeWidth = 4f,
                        zIndex = 0.5f
                    )
                }
                mapMarkers.forEach { (household, position) ->
                    key(household.id) {
                        val isSelected = household.id == selectedHouseholdId
                        val markerState = remember { MarkerState(position = position) }
                            .apply { this.position = position }
                        Marker(
                            state = markerState,
                            title = household.name,
                            snippet = household.streetAddress,
                            icon = markerDescriptorForBriefComment(household.briefComment),
                            zIndex = if (isSelected) 2f else 0f,
                            onClick = {
                                // #region agent log
                                mapPinNavDebug(
                                    hypothesisId = "H1_H2",
                                    location = "MapScreen.kt:Marker.onClick",
                                    message = "marker_clicked",
                                    data = mapOf(
                                        "householdIdSuffix" to household.id.takeLast(8),
                                        "returnsConsumed" to false
                                    ),
                                    runId = "nav-from-pin-post"
                                )
                                // #endregion
                                onHouseholdSelected(household)
                                false
                            },
                            onInfoWindowClick = {
                                // #region agent log
                                mapPinNavDebug(
                                    hypothesisId = "H_fix_nav",
                                    location = "MapScreen.kt:Marker.onInfoWindowClick",
                                    message = "info_window_open_navigation",
                                    data = mapOf("householdIdSuffix" to household.id.takeLast(8)),
                                    runId = "nav-from-pin-post"
                                )
                                // #endregion
                                val uri = Uri.parse(
                                    "google.navigation:q=${Uri.encode(household.streetAddress)}"
                                )
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, uri).apply {
                                        setPackage("com.google.android.apps.maps")
                                    }
                                )
                            }
                        )
                    }
                }
            }
        } else {
            LazyColumn {
                items(filteredData) { household ->
                    val isSelected = household.id == selectedHouseholdId
                    Card(
                        modifier = Modifier.padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable { onHouseholdSelected(household) }
                            ) {
                                Text(household.name)
                                Text(household.streetAddress)
                                Text("Brief: ${formatBriefComment(household.briefComment)}")
                                Text(
                                    "Last visited: ${
                                        parseIsoDateOrNull(household.lastVisited)?.format(prettyDateFormatter) ?: "Not visited"
                                    }"
                                )
                            }
                            IconButton(
                                onClick = {
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
                                Icon(
                                    Icons.Filled.Directions,
                                    contentDescription = "Open directions in Google Maps"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseIsoDateOrNull(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(value, DateTimeFormatter.ISO_DATE) }.getOrNull()
}

private fun formatBriefComment(value: String): String {
    return value
        .replace("_", " ")
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
}

/** Hue (0–360) per canonical [VisitOutcome] key; unknown/raw [briefComment] uses [OTHER_BRIEF_COMMENT_MARKER_HUE]. */
private val briefCommentMarkerHue = mapOf(
    "left_message" to 210f,
    "not_home" to 30f,
    "receptive" to 120f,
    "do_not_visit" to 0f,
    "moved" to 280f,
    "dawat_saath" to 55f,
    "other" to 200f
)

private const val OTHER_BRIEF_COMMENT_MARKER_HUE = 200f

private fun markerDescriptorForBriefComment(briefComment: String): BitmapDescriptor {
    val hue = briefCommentMarkerHue[briefComment] ?: OTHER_BRIEF_COMMENT_MARKER_HUE
    return BitmapDescriptorFactory.defaultMarker(hue)
}
