package org.outreach.feature.map

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class DrivingRoute(
    val points: List<LatLng>,
    val spokenInstructions: List<String>,
    val distanceText: String?,
    val durationText: String?,
    val durationSeconds: Int?
)

/**
 * Fetches a driving route using the [Directions API](https://developers.google.com/maps/documentation/directions/overview).
 * Requires the same API key as Maps; enable "Directions API" for the key in Google Cloud.
 */
object DirectionsRouteFetcher {

    /** Logcat filter: `adb logcat -s OutreachDirections:W` */
    private const val TAG = "OutreachDirections"

    suspend fun fetchDrivingRouteDetails(
        origin: LatLng,
        destination: LatLng,
        apiKey: String
    ): Result<DrivingRoute> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            Log.w(TAG, "Directions request skipped: Maps API key is blank")
            return@withContext Result.failure(IllegalStateException("Maps API key is blank"))
        }
        val originStr = "${origin.latitude},${origin.longitude}"
        val destStr = "${destination.latitude},${destination.longitude}"
        val q = "origin=${URLEncoder.encode(originStr, "UTF-8")}" +
            "&destination=${URLEncoder.encode(destStr, "UTF-8")}" +
            "&mode=driving&key=${URLEncoder.encode(apiKey, "UTF-8")}"
        val url = URL("https://maps.googleapis.com/maps/api/directions/json?$q")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20000
            readTimeout = 20000
        }
        try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                .bufferedReader().use { it.readText() }
            if (code !in 200..299) {
                Log.w(TAG, "Directions HTTP $code (response body length=${body.length})")
                return@withContext Result.failure(IllegalStateException("Directions HTTP $code"))
            }
            val json = JSONObject(body)
            val status = json.optString("status")
            if (status != "OK") {
                val googleDetail = json.optString("error_message").trim().takeIf { it.isNotBlank() }
                val message = formatDirectionsFailure(status, googleDetail)
                Log.w(TAG, message)
                return@withContext Result.failure(IllegalStateException(message))
            }
            val routes = json.optJSONArray("routes") ?: run {
                Log.w(TAG, "Directions JSON missing routes array")
                return@withContext Result.failure(
                    IllegalStateException("No routes")
                )
            }
            if (routes.length() == 0) {
                Log.w(TAG, "Directions returned empty routes array")
                return@withContext Result.failure(IllegalStateException("Empty routes"))
            }
            val route = routes.getJSONObject(0)
            val overview = route
                .getJSONObject("overview_polyline")
                .getString("points")
            val points = PolyUtil.decode(overview)
            val summary = extractRouteSummary(route)
            val spokenInstructions = extractSpokenInstructions(route)
            Result.success(
                DrivingRoute(
                    points = points,
                    spokenInstructions = spokenInstructions,
                    distanceText = summary.distanceText,
                    durationText = summary.durationText,
                    durationSeconds = summary.durationSeconds
                )
            )
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetchDrivingRoute(
        origin: LatLng,
        destination: LatLng,
        apiKey: String
    ): Result<List<LatLng>> = fetchDrivingRouteDetails(origin, destination, apiKey).map { it.points }

    /**
     * Maps API returns [status] and optional [googleDetail] (`error_message`).
     * [REQUEST_DENIED](https://developers.google.com/maps/documentation/directions/get-directions#StatusCodes)
     * usually means Directions API is off, billing is off, or the key’s API restrictions omit Directions API.
     */
    private fun formatDirectionsFailure(status: String, googleDetail: String?): String {
        val core = buildString {
            append("Directions status: $status")
            if (!googleDetail.isNullOrBlank()) {
                append(" — ").append(googleDetail)
            }
        }
        if (status == "REQUEST_DENIED") {
            return "$core. For driving times and arrival info: Google Cloud Console → enable Directions API " +
                "for this project, ensure billing is active for Maps Platform, and under Credentials ensure " +
                "this Maps key’s API restrictions include Directions API (same key as map tiles)."
        }
        return core
    }

    private data class RouteSummary(
        val distanceText: String?,
        val durationText: String?,
        val durationSeconds: Int?
    )

    private fun extractRouteSummary(route: JSONObject): RouteSummary {
        val legs = route.optJSONArray("legs") ?: return RouteSummary(null, null, null)
        var totalDurationSeconds = 0
        var hasDuration = false
        val distancePieces = mutableListOf<String>()
        val durationPieces = mutableListOf<String>()
        for (legIndex in 0 until legs.length()) {
            val leg = legs.optJSONObject(legIndex) ?: continue
            val distanceObj = leg.optJSONObject("distance")
            val durationObj = leg.optJSONObject("duration")
            val durationValue = durationObj?.optInt("value", -1) ?: -1
            val distanceText = distanceObj?.optString("text").orEmpty().trim()
            val durationText = durationObj?.optString("text").orEmpty().trim()
            if (durationValue >= 0) {
                totalDurationSeconds += durationValue
                hasDuration = true
            }
            if (distanceText.isNotBlank()) distancePieces += distanceText
            if (durationText.isNotBlank()) durationPieces += durationText
        }
        return RouteSummary(
            distanceText = distancePieces.firstOrNull(),
            durationText = durationPieces.firstOrNull(),
            durationSeconds = if (hasDuration) totalDurationSeconds else null
        )
    }

    private fun extractSpokenInstructions(route: JSONObject): List<String> {
        val legs = route.optJSONArray("legs") ?: return emptyList()
        val instructions = mutableListOf<String>()
        for (legIndex in 0 until legs.length()) {
            val leg = legs.optJSONObject(legIndex) ?: continue
            val steps = leg.optJSONArray("steps") ?: continue
            for (stepIndex in 0 until steps.length()) {
                val step = steps.optJSONObject(stepIndex) ?: continue
                val html = step.optString("html_instructions")
                val distance = step.optJSONObject("distance")?.optString("text").orEmpty()
                val cleaned = html
                    .replace(Regex("<[^>]+>"), " ")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replace("\\s+".toRegex(), " ")
                    .trim()
                if (cleaned.isNotBlank()) {
                    val combined = if (distance.isNotBlank()) "$cleaned for $distance." else cleaned
                    instructions += combined
                }
            }
        }
        return instructions
    }
}
