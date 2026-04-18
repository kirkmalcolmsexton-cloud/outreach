package org.outreach.feature.car

import com.google.android.gms.maps.model.LatLng
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicReference

/**
 * Snapshot of phone [org.outreach.feature.map.MapScreen] navigation state, mirrored for
 * Android Auto ([OutreachNavigationScreen]) so the head unit can show turn cues and a simple route line.
 */
data class OutreachCarNavSnapshot(
    val destinationLabel: String,
    val instructions: List<String>,
    /** Heuristic index into [instructions] based on route progress. */
    val primaryInstructionIndex: Int,
    val distanceText: String?,
    val durationText: String?,
    val durationSeconds: Int?,
    /** Polyline length ahead of the user in meters, when known. */
    val remainingMeters: Double?,
    /** Full route or remaining segment; same semantics as map polyline. */
    val routePoints: List<LatLng>,
    val isNavigating: Boolean
) {
    val primaryInstruction: String
        get() = instructions.getOrNull(primaryInstructionIndex)
            ?: instructions.firstOrNull()
            ?: "Follow the highlighted route."

    val nextInstruction: String?
        get() = instructions.getOrNull(primaryInstructionIndex + 1)
}

object OutreachCarNavBridge {

    private val snapshotRef = AtomicReference<OutreachCarNavSnapshot?>(null)
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun read(): OutreachCarNavSnapshot? = snapshotRef.get()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun publish(snapshot: OutreachCarNavSnapshot?) {
        snapshotRef.set(snapshot)
        listeners.forEach { it.invoke() }
    }

    /**
     * Map polyline vertex lead to a direction step index (Directions API steps are much fewer than vertices).
     */
    fun instructionIndexForRouteProgress(
        leadSegmentIndex: Int,
        polylineSize: Int,
        instructionCount: Int
    ): Int {
        if (instructionCount <= 0) return 0
        if (polylineSize < 2) return 0
        val t = leadSegmentIndex.toFloat() / (polylineSize - 1).toFloat()
        return (t * instructionCount).toInt().coerceIn(0, instructionCount - 1)
    }
}
