package org.outreach.feature.map

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
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
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
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
import org.outreach.core.model.HouseholdRecord
import org.outreach.core.model.RawHouseholdRow
import org.outreach.core.model.SourceMetadata
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    households: List<HouseholdRecord> = emptyList()
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
    val markerIconMap = remember(context) {
        mapOf(
            "left_message" to markerIconFor(context, android.R.drawable.ic_dialog_email),
            "not_home" to markerIconFor(context, android.R.drawable.ic_menu_mylocation),
            "receptive" to markerIconFor(context, android.R.drawable.checkbox_on_background),
            "do_not_visit" to markerIconFor(context, android.R.drawable.ic_delete),
            "moved" to markerIconFor(context, android.R.drawable.ic_menu_directions),
            "dawat_saath" to markerIconFor(context, android.R.drawable.star_big_on),
            "other" to markerIconFor(context, android.R.drawable.ic_menu_help)
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
                    val markerIcon = if (selectedBriefComments.isNotEmpty() && household.briefComment in selectedBriefComments) {
                        markerIconMap[household.briefComment]
                    } else {
                        null
                    }
                    Marker(
                        state = MarkerState(position = position),
                        title = household.name,
                        snippet = household.streetAddress,
                        icon = markerIcon
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
                            Text("Brief: ${formatBriefComment(household.briefComment)}")
                            Text(
                                "Last visited: ${
                                    parseIsoDateOrNull(household.lastVisited)?.format(prettyDateFormatter) ?: "Not visited"
                                }"
                            )
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

private fun markerIconFor(context: android.content.Context, drawableRes: Int): BitmapDescriptor? {
    return try {
        val drawable = ContextCompat.getDrawable(context, drawableRes) ?: return null
        BitmapDescriptorFactory.fromBitmap(drawable.toBitmapForMarker())
    } catch (_: Throwable) {
        null
    }
}

private fun Drawable.toBitmapForMarker(maxSide: Int = 128): Bitmap {
    val srcW = if (intrinsicWidth > 0) intrinsicWidth else 64
    val srcH = if (intrinsicHeight > 0) intrinsicHeight else 64
    val scale = min(min(maxSide.toFloat() / srcW, maxSide.toFloat() / srcH), 1f)
    val width = max(1, (srcW * scale).roundToInt())
    val height = max(1, (srcH * scale).roundToInt())
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}
