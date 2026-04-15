package org.outreach.feature.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.Circle
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.outreach.app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.outreach.core.model.HouseholdRecord
import org.outreach.core.model.RawHouseholdRow
import org.outreach.core.model.SourceMetadata
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    households: List<HouseholdRecord> = emptyList(),
    visibleZipTabs: Set<String> = emptySet(),
    mapBriefCommentFilter: Set<String> = emptySet(),
    filterStartDate: LocalDate = LocalDate.now(),
    filterEndDate: LocalDate = LocalDate.now(),
    selectedHouseholdId: String? = null,
    onHouseholdSelected: (HouseholdRecord) -> Unit = {},
    selectedTabs: Set<String> = emptySet(),
    onAddHousehold: (tabName: String, name: String, streetAddress: String, neighborhood: String) -> Unit =
        { _, _, _, _ -> }
) {
    var showAddPersonDialog by remember { mutableStateOf(false) }
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
    var searchQuery by remember { mutableStateOf("") }
    val configFiltered = remember(
        data,
        visibleZipTabs,
        mapBriefCommentFilter,
        filterStartDate,
        filterEndDate
    ) {
        filterHouseholdsForMap(
            data,
            visibleZipTabs,
            mapBriefCommentFilter,
            filterStartDate,
            filterEndDate
        )
    }
    val filteredData = remember(configFiltered, searchQuery) {
        configFiltered.filter { householdMatchesTextSearch(it, searchQuery) }
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
    val pageScrollState = rememberScrollState()

    var viewMode by remember { mutableStateOf("map") }
    val scope = rememberCoroutineScope()
    val mapsApiKey = remember(context) {
        runCatching { context.getString(R.string.google_maps_key) }.getOrDefault("")
    }
    var routePoints by remember { mutableStateOf<List<LatLng>?>(null) }
    var routeTargetHousehold by remember { mutableStateOf<HouseholdRecord?>(null) }
    var routeLoading by remember { mutableStateOf(false) }
    var routeLoadError by remember { mutableStateOf<String?>(null) }
    var pendingRouteHousehold by remember { mutableStateOf<HouseholdRecord?>(null) }
    var localSelectedHouseholdId by remember { mutableStateOf<String?>(null) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    LaunchedEffect(Unit) {
        // #region agent log
        agentDebugLog(
            runId = "run1",
            hypothesisId = "H5",
            location = "MapScreen.kt:composeEntry",
            message = "MapScreen composed",
            data = mapOf("householdCount" to households.size)
        )
        // #endregion
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // #region agent log
        agentDebugLog(
            runId = "run1",
            hypothesisId = "H3",
            location = "MapScreen.kt:permissionResult",
            message = "Location permission result",
            data = mapOf("granted" to granted)
        )
        // #endregion
        hasLocationPermission = granted
        val pending = pendingRouteHousehold
        pendingRouteHousehold = null
        if (granted && pending != null) {
            scope.launch {
                routeLoading = true
                routeLoadError = null
                try {
                    val result = loadDrivingRoutePreview(
                        context,
                        pending,
                        mapsApiKey,
                        cameraPositionState
                    )
                    result.onSuccess { pts ->
                        routePoints = pts
                        routeTargetHousehold = pending
                    }.onFailure { e ->
                        routeLoadError = e.message ?: "Route failed"
                    }
                } finally {
                    routeLoading = false
                }
            }
        } else if (!granted) {
            routeLoadError = "Location permission is needed to show a route from your position."
        }
    }
    val requestRouteForHousehold: (HouseholdRecord) -> Unit = { household ->
        scope.launch {
            // #region agent log
            agentDebugLog(
                runId = "run1",
                hypothesisId = "H6",
                location = "MapScreen.kt:requestRouteForHousehold",
                message = "Route requested from map action",
                data = mapOf("householdId" to household.id)
            )
            // #endregion
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingRouteHousehold = household
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                return@launch
            }
            routeLoading = true
            routeLoadError = null
            try {
                val result = loadDrivingRoutePreview(
                    context,
                    household,
                    mapsApiKey,
                    cameraPositionState
                )
                result.onSuccess { pts ->
                    routePoints = pts
                    routeTargetHousehold = household
                }.onFailure { e ->
                    routeLoadError = e.message ?: "Route failed"
                }
            } finally {
                routeLoading = false
            }
        }
    }

    LaunchedEffect(selectedHouseholdId) {
        val routeId = routeTargetHousehold?.id
        if (routePoints != null && (selectedHouseholdId == null || selectedHouseholdId != routeId)) {
            routePoints = null
            routeTargetHousehold = null
            routeLoadError = null
        }
    }

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
        // #region agent log
        agentDebugLog(
            runId = "run1",
            hypothesisId = "H7",
            location = "MapScreen.kt:selectedHouseholdEffect",
            message = "Selection effect triggered",
            data = mapOf(
                "selectedHouseholdId" to (selectedHouseholdId ?: ""),
                "viewMode" to viewMode,
                "markerCount" to mapMarkers.size
            )
        )
        // #endregion
        if (viewMode != "map") return@LaunchedEffect
        val id = selectedHouseholdId ?: return@LaunchedEffect
        localSelectedHouseholdId = id
        val target = mapMarkers.firstOrNull { (h, _) -> h.id == id } ?: return@LaunchedEffect
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(target.second, selectionZoom),
            400
        )
    }
    Box(modifier.fillMaxSize()) {
    val density = LocalDensity.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(12.dp)
            .padding(end = 10.dp)
            .verticalScroll(pageScrollState)
    ) {
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
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            singleLine = true,
            label = { Text("Search name or address") }
        )
        Text(
            if (viewMode == "map") {
                "Map pins (filters: Configure)"
            } else {
                "Address list (filters: Configure)"
            }
        )
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
                    "$filteredWithoutCoordinates missing coords",
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            GoogleMap(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(360.dp)
                    .background(Color(0xFFECEFF1)),
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                uiSettings = MapUiSettings(mapToolbarEnabled = false),
                cameraPositionState = cameraPositionState
            ) {
                val selectedId = selectedHouseholdId?.takeIf { it.isNotBlank() }
                    ?: localSelectedHouseholdId?.takeIf { localId ->
                        mapMarkers.any { (h, _) -> h.id == localId }
                    }
                val selectedPosition = selectedId?.let { sid ->
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
                routePoints?.takeIf { it.size >= 2 }?.let { pts ->
                    Polyline(
                        points = pts,
                        color = Color(0xFF1565C0),
                        width = 10f,
                        zIndex = 1f
                    )
                }
                mapMarkers.forEach { (household, position) ->
                    key(household.id) {
                        val isSelected = household.id == selectedId
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
                                agentDebugLog(
                                    runId = "run1",
                                    hypothesisId = "H7",
                                    location = "MapScreen.kt:markerOnClick",
                                    message = "Map marker clicked",
                                    data = mapOf("householdId" to household.id)
                                )
                                // #endregion
                                localSelectedHouseholdId = household.id
                                onHouseholdSelected(household)
                                false
                            },
                            onInfoWindowClick = {
                                requestRouteForHousehold(household)
                            }
                        )
                    }
                }
            }
            val selectedForActionsId = selectedHouseholdId?.takeIf { it.isNotBlank() }
                ?: localSelectedHouseholdId
            selectedForActionsId?.let { selectedId ->
                val selectedHousehold = mapMarkers.firstOrNull { (h, _) -> h.id == selectedId }?.first
                if (selectedHousehold != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        TextButton(onClick = { requestRouteForHousehold(selectedHousehold) }) {
                            Text("Show route")
                        }
                        TextButton(
                            onClick = {
                                openGoogleMapsNavigation(
                                    context,
                                    selectedHousehold.streetAddress,
                                    "selected_household_actions"
                                )
                            }
                        ) {
                            Text("Open in Google Maps")
                        }
                    }
                }
            }
            if (routeLoading) {
                Text(
                    "Loading route…",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            routeLoadError?.let { err ->
                Text(
                    err,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (routePoints != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    TextButton(
                        onClick = {
                            routePoints = null
                            routeTargetHousehold = null
                            routeLoadError = null
                        }
                    ) {
                        Text("Clear route")
                    }
                    TextButton(
                        onClick = {
                            routeTargetHousehold?.let { h ->
                                openGoogleMapsNavigation(context, h.streetAddress, "route_actions_row")
                            }
                        }
                    ) {
                        Text("Open in Google Maps")
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
                                    // #region agent log
                                    agentDebugLog(
                                        runId = "run1",
                                        hypothesisId = "H1",
                                        location = "MapScreen.kt:listDirectionsClick",
                                        message = "List directions tapped",
                                        data = mapOf("householdId" to household.id)
                                    )
                                    // #endregion
                                    onHouseholdSelected(household)
                                    viewMode = "map"
                                    requestRouteForHousehold(household)
                                }
                            ) {
                                Icon(
                                    Icons.Filled.Directions,
                                    contentDescription = "Show driving route on map"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
        if (pageScrollState.maxValue > 0 && pageScrollState.viewportSize > 0) {
            val viewportPx = pageScrollState.viewportSize.toFloat()
            val contentPx = viewportPx + pageScrollState.maxValue
            val minThumbPx = with(density) { 24.dp.toPx() }
            val thumbHeightPx = ((viewportPx / contentPx) * viewportPx).coerceAtLeast(minThumbPx)
            val maxThumbOffset = (viewportPx - thumbHeightPx).coerceAtLeast(0f)
            val thumbOffset = if (pageScrollState.maxValue == 0) 0f else {
                (pageScrollState.value.toFloat() / pageScrollState.maxValue.toFloat()) * maxThumbOffset
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 2.dp)
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(Color(0x22000000))
            ) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, thumbOffset.toInt()) }
                        .width(4.dp)
                        .height(with(density) { thumbHeightPx.toDp() })
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        FloatingActionButton(
            onClick = { showAddPersonDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            content = {
                Icon(Icons.Filled.Add, contentDescription = "Add person")
            }
        )
        if (showAddPersonDialog) {
            AddPersonDialog(
                selectedTabs = selectedTabs,
                onDismiss = { showAddPersonDialog = false },
                onConfirm = { tab, name, street, neighborhood ->
                    showAddPersonDialog = false
                    onAddHousehold(tab, name, street, neighborhood)
                }
            )
        }
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

private suspend fun loadDrivingRoutePreview(
    context: android.content.Context,
    household: HouseholdRecord,
    apiKey: String,
    cameraPositionState: CameraPositionState
): Result<List<LatLng>> {
    // #region agent log
    agentDebugLog(
        runId = "run1",
        hypothesisId = "H2",
        location = "MapScreen.kt:loadDrivingRoutePreview:start",
        message = "Route preview request started",
        data = mapOf("householdId" to household.id, "hasApiKey" to apiKey.isNotBlank())
    )
    // #endregion
    val dest = destinationLatLng(context, household)
    if (dest == null) {
        // #region agent log
        agentDebugLog(
            runId = "run1",
            hypothesisId = "H2",
            location = "MapScreen.kt:loadDrivingRoutePreview:destinationMissing",
            message = "Route destination could not be resolved",
            data = mapOf("householdId" to household.id)
        )
        // #endregion
        return Result.failure(IllegalArgumentException("Could not resolve destination address."))
    }
    val fused = LocationServices.getFusedLocationProviderClient(context)
    val loc = try {
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
    } catch (e: Exception) {
        return Result.failure(e)
    } ?: return Result.failure(IllegalStateException("Current location unavailable."))
    val origin = LatLng(loc.latitude, loc.longitude)
    val routeResult = DirectionsRouteFetcher.fetchDrivingRoute(origin, dest, apiKey)
    // #region agent log
    agentDebugLog(
        runId = "run1",
        hypothesisId = "H2",
        location = "MapScreen.kt:loadDrivingRoutePreview:result",
        message = "Route preview request completed",
        data = mapOf(
            "householdId" to household.id,
            "success" to routeResult.isSuccess,
            "points" to (routeResult.getOrNull()?.size ?: 0),
            "error" to (routeResult.exceptionOrNull()?.message ?: "")
        )
    )
    // #endregion
    routeResult.onSuccess { points -> fitCameraToRoute(cameraPositionState, points) }
    return routeResult
}

private suspend fun destinationLatLng(
    context: android.content.Context,
    household: HouseholdRecord
): LatLng? {
    val lat = household.latitude
    val lng = household.longitude
    if (lat != null && lng != null) return LatLng(lat, lng)
    return withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        @Suppress("DEPRECATION")
        val results = Geocoder(context).getFromLocationName(household.streetAddress, 1)
        if (results.isNullOrEmpty()) null
        else LatLng(results[0].latitude, results[0].longitude)
    }
}

private fun fitCameraToRoute(cameraPositionState: CameraPositionState, points: List<LatLng>) {
    if (points.size < 2) return
    val builder = LatLngBounds.Builder()
    for (p in points) builder.include(p)
    val bounds = builder.build()
    cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 120))
}

private fun openGoogleMapsNavigation(
    context: android.content.Context,
    address: String,
    source: String
) {
    // #region agent log
    agentDebugLog(
        runId = "run1",
        hypothesisId = "H1",
        location = "MapScreen.kt:openGoogleMapsNavigation",
        message = "External Google Maps launch invoked",
        data = mapOf("source" to source)
    )
    // #endregion
    val uri = Uri.parse("google.navigation:q=${Uri.encode(address)}")
    context.startActivity(
        Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
    )
}

private fun agentDebugLog(
    runId: String,
    hypothesisId: String,
    location: String,
    message: String,
    data: Map<String, Any?>
) {
    runCatching {
        val dataJson = JSONObject().apply {
            data.forEach { (k, v) -> put(k, v) }
        }
        val payload = JSONObject().apply {
            put("sessionId", "1abea7")
            put("runId", runId)
            put("hypothesisId", hypothesisId)
            put("location", location)
            put("message", message)
            put("data", dataJson)
            put("timestamp", System.currentTimeMillis())
        }.toString()
        Thread {
            runCatching {
                val endpoints = listOf(
                    "http://10.0.2.2:7747/ingest/f7368b29-184e-4539-ae18-cb2344a4388c",
                    "http://127.0.0.1:7747/ingest/f7368b29-184e-4539-ae18-cb2344a4388c"
                )
                endpoints.forEach { endpoint ->
                    runCatching {
                        // #region agent log
                        Log.d("AgentDebug", "send log to $endpoint")
                        // #endregion
                        val conn = URL(endpoint).openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.connectTimeout = 3000
                        conn.readTimeout = 3000
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.setRequestProperty("X-Debug-Session-Id", "1abea7")
                        OutputStreamWriter(conn.outputStream).use { it.write(payload) }
                        conn.inputStream.close()
                        // #region agent log
                        Log.d("AgentDebug", "log sent to $endpoint with code=${conn.responseCode}")
                        // #endregion
                        conn.disconnect()
                    }.onFailure { e ->
                        // #region agent log
                        Log.e("AgentDebug", "log send failed to $endpoint: ${e.message}")
                        // #endregion
                    }
                }
            }.onFailure { e ->
                // #region agent log
                Log.e("AgentDebug", "logger thread failed: ${e.message}")
                // #endregion
            }
        }.start()
    }
}
