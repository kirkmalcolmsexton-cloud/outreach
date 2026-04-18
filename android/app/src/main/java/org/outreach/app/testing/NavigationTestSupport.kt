package org.outreach.app.testing

import org.outreach.feature.map.DrivingRoute
import java.util.concurrent.atomic.AtomicInteger

/** Instrumentation-only hooks for map navigation (see `docs/ui-testing.md`). */
object NavigationTestSupport {
    @Volatile
    var previewRouteByHouseholdId: Map<String, DrivingRoute>? = null

    @Volatile
    var simulatedNavigationLocations: List<Pair<Double, Double>>? = null

    private val navigationLocationUpdates = AtomicInteger(0)

    fun navigationLocationUpdateCount(): Int = navigationLocationUpdates.get()

    fun consumeNavigationLocationUpdateCount(): Int = navigationLocationUpdates.getAndSet(0)

    internal fun recordSimulatedNavigationUpdate() {
        navigationLocationUpdates.incrementAndGet()
    }

    fun reset() {
        previewRouteByHouseholdId = null
        simulatedNavigationLocations = null
        navigationLocationUpdates.set(0)
    }
}
