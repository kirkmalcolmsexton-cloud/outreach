package org.outreach.feature.map

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Snap-to-route and remaining-distance helpers for in-app navigation.
 * Uses spherical geometry from Maps Android Utils.
 */
object RouteProgress {

    fun totalPathLengthMeters(points: List<LatLng>): Double {
        if (points.size < 2) return 0.0
        return SphericalUtil.computeLength(points)
    }

    data class SnapResult(val snapped: LatLng, val segmentIndex: Int)

    /**
     * Closest point on the polyline, considering only segments starting at [firstSegmentIndex]
     * so progress does not jump backward when GPS jitters.
     */
    fun snapToPolylineForward(
        points: List<LatLng>,
        here: LatLng,
        firstSegmentIndex: Int
    ): SnapResult {
        if (points.size < 2) return SnapResult(here, 0)
        val start = firstSegmentIndex.coerceIn(0, points.lastIndex - 1)
        var bestDist = Double.MAX_VALUE
        var bestSnap = points[start]
        var bestSeg = start
        for (i in start until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            val (snap, dist) = closestOnSegment(a, b, here)
            if (dist < bestDist) {
                bestDist = dist
                bestSnap = snap
                bestSeg = i
            }
        }
        return SnapResult(bestSnap, bestSeg)
    }

    private fun closestOnSegment(a: LatLng, b: LatLng, p: LatLng): Pair<LatLng, Double> {
        val samples = 40
        var best = a
        var bestD = Double.MAX_VALUE
        for (s in 0..samples) {
            val t = s / samples.toDouble()
            val lat = a.latitude + t * (b.latitude - a.latitude)
            val lng = a.longitude + t * (b.longitude - a.longitude)
            val q = LatLng(lat, lng)
            val d = SphericalUtil.computeDistanceBetween(p, q)
            if (d < bestD) {
                bestD = d
                best = q
            }
        }
        return best to bestD
    }

    /**
     * Road distance from [snap] (lying on segment [segmentIndex]) to the route end.
     */
    fun remainingPathLengthMeters(points: List<LatLng>, snap: LatLng, segmentIndex: Int): Double {
        if (points.isEmpty()) return 0.0
        if (points.size == 1) return SphericalUtil.computeDistanceBetween(snap, points[0])
        val lastIdx = points.lastIndex
        val seg = segmentIndex.coerceIn(0, lastIdx - 1)
        var len = SphericalUtil.computeDistanceBetween(snap, points[seg + 1])
        for (j in seg + 1 until lastIdx) {
            len += SphericalUtil.computeDistanceBetween(points[j], points[j + 1])
        }
        return len
    }

    /** Points to draw for the portion of the route still ahead of the user. */
    fun remainingPolylineForDraw(points: List<LatLng>, snap: LatLng, segmentIndex: Int): List<LatLng> {
        if (points.size < 2) return points
        val seg = segmentIndex.coerceIn(0, points.lastIndex - 1)
        val tail = points.subList(seg + 1, points.size)
        return listOf(snap) + tail
    }

    /**
     * Advance the first segment we search from on the next fix (never decreases).
     * Moves forward when we're close to the next vertex along the route.
     */
    fun advanceLeadSegmentIndex(
        points: List<LatLng>,
        here: LatLng,
        currentLead: Int
    ): Int {
        if (points.size < 2) return 0
        var idx = currentLead.coerceIn(0, points.lastIndex - 1)
        val advanceThresholdMeters = 35.0
        while (idx < points.lastIndex - 1) {
            val d = SphericalUtil.computeDistanceBetween(here, points[idx + 1])
            if (d < advanceThresholdMeters) {
                idx++
            } else {
                break
            }
        }
        return idx
    }

    fun estimatedRemainingSeconds(totalSeconds: Int?, totalMeters: Double, remainingMeters: Double): Int? {
        if (totalSeconds == null || totalSeconds <= 0 || totalMeters < 1.0) return null
        val ratio = (remainingMeters / totalMeters).coerceIn(0.0, 1.0)
        return min(totalSeconds, max(0, (totalSeconds * ratio).toInt()))
    }

    fun formatRemainingDistanceMi(meters: Double): String {
        val miles = meters / 1609.344
        return when {
            miles < 0.05 -> "<0.1 mi"
            miles < 10.0 -> String.format("%.1f mi", miles)
            else -> "${miles.roundToInt()} mi"
        }
    }

    fun formatRemainingDurationMinutes(seconds: Int): String {
        val mins = max(1, (seconds + 59) / 60)
        return "$mins min"
    }

    data class NavigationUiSnapshot(
        val leadSegmentIndex: Int,
        val remainingPolyline: List<LatLng>,
        val distanceText: String,
        val durationText: String?,
        val durationSeconds: Int?
    )

    /**
     * One GPS fix: update segment lead, remaining stats, and polyline ahead of the user.
     */
    fun computeNavigationUiSnapshot(
        here: LatLng,
        points: List<LatLng>,
        leadSegmentIndex: Int,
        totalPathMeters: Double,
        loadedDurationSeconds: Int?,
        loadedDurationTextFallback: String?
    ): NavigationUiSnapshot {
        if (points.size < 2) {
            return NavigationUiSnapshot(
                leadSegmentIndex = 0,
                remainingPolyline = points,
                distanceText = formatRemainingDistanceMi(0.0),
                durationText = loadedDurationTextFallback,
                durationSeconds = loadedDurationSeconds
            )
        }
        var lead = advanceLeadSegmentIndex(points, here, leadSegmentIndex)
        val snap = snapToPolylineForward(points, here, lead)
        lead = maxOf(lead, snap.segmentIndex)
        val remainingM = remainingPathLengthMeters(points, snap.snapped, snap.segmentIndex)
        val remainingSec = estimatedRemainingSeconds(loadedDurationSeconds, totalPathMeters, remainingM)
        val distText = formatRemainingDistanceMi(remainingM)
        val durText = when {
            remainingSec != null -> formatRemainingDurationMinutes(remainingSec)
            else -> loadedDurationTextFallback
        }
        val poly = remainingPolylineForDraw(points, snap.snapped, snap.segmentIndex)
        return NavigationUiSnapshot(
            leadSegmentIndex = lead,
            remainingPolyline = poly,
            distanceText = distText,
            durationText = durText,
            durationSeconds = remainingSec ?: loadedDurationSeconds
        )
    }
}
