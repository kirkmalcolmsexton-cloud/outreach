package org.outreach.feature.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.outreach.app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.outreach.core.model.HouseholdRecord
import org.outreach.core.model.RawHouseholdRow
import org.outreach.core.model.SourceMetadata

data class MapViewportState(
    val latitude: Double,
    val longitude: Double,
    val zoom: Float,
    val tilt: Float,
    val bearing: Float
)

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    households: List<HouseholdRecord> = emptyList(),
    visibleZipTabs: Set<String> = emptySet(),
    mapBriefCommentMode: String = "include_all",
    mapBriefCommentFilter: Set<String> = emptySet(),
    mapOldestRecordsLimit: Int? = null,
    filterStartDate: LocalDate = LocalDate.now(),
    filterEndDate: LocalDate = LocalDate.now(),
    initialViewportState: MapViewportState? = null,
    onViewportStateChanged: (MapViewportState) -> Unit = {},
    selectedHouseholdId: String? = null,
    onHouseholdSelected: (HouseholdRecord) -> Unit = {},
    selectedTabs: Set<String> = emptySet(),
    onAddHousehold: (tabName: String, name: String, streetAddress: String, neighborhood: String) -> Unit =
        { _, _, _, _ -> },
    viewMode: String,
    onViewModeChange: (String) -> Unit
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
        mapBriefCommentMode,
        mapBriefCommentFilter,
        filterStartDate,
        filterEndDate
    ) {
        filterHouseholdsForMap(
            data,
            visibleZipTabs,
            mapBriefCommentMode,
            mapBriefCommentFilter,
            filterStartDate,
            filterEndDate
        )
    }
    val searchFiltered = remember(configFiltered, searchQuery) {
        configFiltered.filter { householdMatchesTextSearch(it, searchQuery) }
    }
    val filteredData = remember(searchFiltered, mapOldestRecordsLimit) {
        applyOldestRecordsLimit(searchFiltered, mapOldestRecordsLimit)
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
    val cameraPositionState = rememberCameraPositionState {
        val saved = initialViewportState
        position = if (saved != null) {
            CameraPosition(
                LatLng(saved.latitude, saved.longitude),
                saved.zoom,
                saved.tilt,
                saved.bearing
            )
        } else {
            CameraPosition.fromLatLngZoom(defaultCenter, 10f)
        }
    }
    val selectionZoom = 15f
    val haloFillColor = Color(0x403675F6)
    val haloStrokeColor = Color(0xFF6750A4)

    val scope = rememberCoroutineScope()
    val mapsApiKey = remember(context) {
        runCatching { context.getString(R.string.google_maps_key) }.getOrDefault("")
    }
    var routePoints by remember { mutableStateOf<List<LatLng>?>(null) }
    var routeTargetHousehold by remember { mutableStateOf<HouseholdRecord?>(null) }
    var routeLoading by remember { mutableStateOf(false) }
    var routeLoadError by remember { mutableStateOf<String?>(null) }
    var isNavigationActive by remember { mutableStateOf(false) }
    var activeNavigationHouseholdId by remember { mutableStateOf<String?>(null) }
    var pendingRouteHousehold by remember { mutableStateOf<HouseholdRecord?>(null) }
    var activateNavigationAfterPermission by remember { mutableStateOf(false) }
    var localSelectedHouseholdId by remember { mutableStateOf<String?>(null) }
    var hasInitializedViewport by remember { mutableStateOf(false) }
    var hasPromptedInitialLocationPermission by remember { mutableStateOf(false) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        val pending = pendingRouteHousehold
        pendingRouteHousehold = null
        val activateAfterPermission = activateNavigationAfterPermission
        activateNavigationAfterPermission = false
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
                        if (activateAfterPermission) {
                            isNavigationActive = true
                            activeNavigationHouseholdId = pending.id
                        }
                    }.onFailure { e ->
                        routeLoadError = e.message ?: "Route failed"
                        if (activateAfterPermission) {
                            isNavigationActive = false
                            activeNavigationHouseholdId = null
                        }
                    }
                } finally {
                    routeLoading = false
                }
            }
        } else if (granted && pending == null && !hasInitializedViewport && initialViewportState == null) {
            scope.launch {
                val fused = LocationServices.getFusedLocationProviderClient(context)
                val currentLocation = runCatching {
                    fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                }.getOrNull() ?: runCatching {
                    fused.lastLocation.await()
                }.getOrNull()
                if (currentLocation != null) {
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(currentLocation.latitude, currentLocation.longitude),
                            13f
                        )
                    )
                    hasInitializedViewport = true
                }
            }
        } else if (!granted) {
            routeLoadError = "Location permission is needed to show a route from your position."
        }
    }
    val requestRouteForHousehold: (HouseholdRecord, Boolean) -> Unit = { household, activateOnSuccess ->
        scope.launch {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingRouteHousehold = household
                activateNavigationAfterPermission = activateOnSuccess
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
                    if (activateOnSuccess) {
                        isNavigationActive = true
                        activeNavigationHouseholdId = household.id
                    }
                }.onFailure { e ->
                    routeLoadError = e.message ?: "Route failed"
                    if (activateOnSuccess) {
                        isNavigationActive = false
                        activeNavigationHouseholdId = null
                    }
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
            isNavigationActive = false
            activeNavigationHouseholdId = null
        }
    }

    LaunchedEffect(mapMarkers, selectedHouseholdId, hasLocationPermission) {
        if (initialViewportState != null) return@LaunchedEffect
        if (hasInitializedViewport) return@LaunchedEffect
        if (selectedHouseholdId != null) return@LaunchedEffect
        if (!hasLocationPermission && !hasPromptedInitialLocationPermission) {
            hasPromptedInitialLocationPermission = true
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return@LaunchedEffect
        }
        if (hasLocationPermission) {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            val currentLocation = runCatching {
                fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            }.getOrNull() ?: runCatching {
                fused.lastLocation.await()
            }.getOrNull()
            if (currentLocation != null) {
                cameraPositionState.move(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(currentLocation.latitude, currentLocation.longitude),
                        13f
                    )
                )
                hasInitializedViewport = true
                return@LaunchedEffect
            }
        }
        if (mapMarkers.isNotEmpty()) {
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(mapMarkers.first().second, 13f)
            )
            hasInitializedViewport = true
        } else {
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(defaultCenter, 10f)
            )
        }
    }

    LaunchedEffect(selectedHouseholdId, viewMode, mapMarkers) {
        if (viewMode != "map") return@LaunchedEffect
        val id = selectedHouseholdId ?: return@LaunchedEffect
        localSelectedHouseholdId = id
        val target = mapMarkers.firstOrNull { (h, _) -> h.id == id } ?: return@LaunchedEffect
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(target.second, selectionZoom),
            400
        )
    }
    val shouldPersistViewport = initialViewportState != null || hasInitializedViewport
    LaunchedEffect(cameraPositionState, shouldPersistViewport) {
        snapshotFlow { cameraPositionState.position }.collectLatest { pos ->
            if (!shouldPersistViewport) return@collectLatest
            onViewportStateChanged(
                MapViewportState(
                    latitude = pos.target.latitude,
                    longitude = pos.target.longitude,
                    zoom = pos.zoom,
                    tilt = pos.tilt,
                    bearing = pos.bearing
                )
            )
        }
    }
    val selectedForActionsId = selectedHouseholdId?.takeIf { it.isNotBlank() }
        ?: localSelectedHouseholdId
    val selectedHouseholdForActions = selectedForActionsId?.let { sid ->
        mapMarkers.firstOrNull { (h, _) -> h.id == sid }?.first
    }
    Box(modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(12.dp)
            .padding(end = 10.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            singleLine = true,
            label = { Text("Search name or address") }
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
                            snippet = buildString {
                                append(formatBriefComment(household.briefComment))
                                append("\n")
                                append(
                                    parseIsoDateOrNull(household.lastVisited)?.format(prettyDateFormatter)
                                        ?: "Not visited"
                                )
                            },
                            icon = markerDescriptorForBriefComment(household.briefComment),
                            zIndex = if (isSelected) 2f else 0f,
                            onClick = {
                                localSelectedHouseholdId = if (isSelected) null else household.id
                                onHouseholdSelected(household)
                                false
                            },
                            onInfoWindowClick = {
                                requestRouteForHousehold(household, false)
                            }
                        )
                    }
                }
            }
            selectedHouseholdForActions?.let { selectedHousehold ->
                if (isNavigationActive && activeNavigationHouseholdId == selectedHousehold.id) {
                    Text(
                        "Navigation active",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                                    onHouseholdSelected(household)
                                    onViewModeChange("map")
                                    requestRouteForHousehold(household, false)
                                }
                            ) {
                                Icon(
                                    Icons.Filled.Directions,
                                    contentDescription = "Show driving route on map"
                                )
                            }
                            IconButton(onClick = { shareHouseholdLocation(context, household) }) {
                                Icon(
                                    Icons.Filled.Share,
                                    contentDescription = "Share household location"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val selectedHousehold = selectedHouseholdForActions
            val hasSelectedHousehold = selectedHousehold != null
            val isActiveForSelection = selectedHousehold?.let { selected ->
                isNavigationActive && activeNavigationHouseholdId == selected.id
            } ?: false
            SmallFloatingActionButton(
                onClick = {
                    val household = selectedHousehold ?: return@SmallFloatingActionButton
                    if (isActiveForSelection) {
                        isNavigationActive = false
                        activeNavigationHouseholdId = null
                    } else {
                        val hasCurrentRoute = routeTargetHousehold?.id == household.id &&
                            !routePoints.isNullOrEmpty()
                        if (hasCurrentRoute) {
                            isNavigationActive = true
                            activeNavigationHouseholdId = household.id
                        } else {
                            requestRouteForHousehold(household, true)
                        }
                    }
                },
                modifier = Modifier.alpha(if (hasSelectedHousehold) 1f else 0.45f)
            ) {
                Icon(
                    imageVector = if (isActiveForSelection) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (isActiveForSelection) {
                        "End navigation"
                    } else {
                        "Start navigation"
                    }
                )
            }
            SmallFloatingActionButton(
                onClick = {
                    val household = selectedHousehold ?: return@SmallFloatingActionButton
                    openGoogleMapsNavigation(context, household.streetAddress)
                },
                modifier = Modifier.alpha(if (hasSelectedHousehold) 1f else 0.45f)
            ) {
                Icon(Icons.Filled.Map, contentDescription = "Open in Google Maps")
            }
            SmallFloatingActionButton(
                onClick = {
                    val household = selectedHousehold ?: return@SmallFloatingActionButton
                    shareHouseholdLocation(context, household)
                },
                modifier = Modifier.alpha(if (hasSelectedHousehold) 1f else 0.45f)
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Share household location")
            }
            SmallFloatingActionButton(onClick = { showAddPersonDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add person")
            }
        }
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
    val dest = destinationLatLng(context, household)
    if (dest == null) {
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
    address: String
) {
    val uri = Uri.parse("google.navigation:q=${Uri.encode(address)}")
    context.startActivity(
        Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
    )
}
