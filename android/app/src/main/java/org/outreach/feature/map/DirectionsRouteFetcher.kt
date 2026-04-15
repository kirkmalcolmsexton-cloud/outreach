package org.outreach.feature.map

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
    val spokenInstructions: List<String>
)

/**
 * Fetches a driving route using the [Directions API](https://developers.google.com/maps/documentation/directions/overview).
 * Requires the same API key as Maps; enable "Directions API" for the key in Google Cloud.
 */
object DirectionsRouteFetcher {

    suspend fun fetchDrivingRouteDetails(
        origin: LatLng,
        destination: LatLng,
        apiKey: String
    ): Result<DrivingRoute> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
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
                return@withContext Result.failure(IllegalStateException("Directions HTTP $code"))
            }
            val json = JSONObject(body)
            val status = json.optString("status")
            if (status != "OK") {
                return@withContext Result.failure(IllegalStateException("Directions status: $status"))
            }
            val routes = json.optJSONArray("routes") ?: return@withContext Result.failure(
                IllegalStateException("No routes")
            )
            if (routes.length() == 0) {
                return@withContext Result.failure(IllegalStateException("Empty routes"))
            }
            val route = routes.getJSONObject(0)
            val overview = route
                .getJSONObject("overview_polyline")
                .getString("points")
            val points = PolyUtil.decode(overview)
            val spokenInstructions = extractSpokenInstructions(route)
            Result.success(DrivingRoute(points = points, spokenInstructions = spokenInstructions))
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetchDrivingRoute(
        origin: LatLng,
        destination: LatLng,
        apiKey: String
    ): Result<List<LatLng>> = fetchDrivingRouteDetails(origin, destination, apiKey).map { it.points }

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
