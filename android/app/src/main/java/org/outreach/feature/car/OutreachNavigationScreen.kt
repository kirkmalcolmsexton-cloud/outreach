package org.outreach.feature.car

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Distance
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.model.Destination
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Trip
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.min

/**
 * Android Auto navigation surface: mirrors [OutreachCarNavBridge] from the phone map.
 */
class OutreachNavigationScreen(carContext: CarContext) : Screen(carContext) {

    private val navigationManager = carContext.getCarService(NavigationManager::class.java)
    private val surfaceCallback = RoutePolylineSurfaceCallback { OutreachCarNavBridge.read() }

    private val bridgeListener: () -> Unit = {
        invalidate()
        surfaceCallback.redraw()
    }

    private var lastReportedNavigationActive: Boolean = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                OutreachCarNavBridge.addListener(bridgeListener)
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                OutreachCarNavBridge.removeListener(bridgeListener)
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
                syncNavigationSession(false)
            }
        })
    }

    override fun onGetTemplate(): Template {
        val snapshot = OutreachCarNavBridge.read()
        syncNavigationSession(snapshot?.isNavigating == true)
        if (snapshot?.isNavigating == true) {
            pushTripMetadata(snapshot)
        }

        val routingInfo = buildNavigationInfo(snapshot)

        val mapStrip = ActionStrip.Builder()
            .addAction(Action.PAN)
            .build()

        val builder = NavigationTemplate.Builder()
            .setNavigationInfo(routingInfo)
            .setMapActionStrip(mapStrip)

        val remaining = snapshot?.remainingMeters
        val durSec = snapshot?.durationSeconds
        if (remaining != null && durSec != null && durSec > 0) {
            val arrival = ZonedDateTime.now().plusSeconds(durSec.toLong())
            val estimate = TravelEstimate.Builder(
                Distance.create(remaining, Distance.UNIT_METERS),
                arrival
            ).setRemainingTimeSeconds(durSec.toLong()).build()
            builder.setDestinationTravelEstimate(estimate)
        }

        return builder.build()
    }

    private fun buildNavigationInfo(snapshot: OutreachCarNavSnapshot?): NavigationTemplate.NavigationInfo {
        if (snapshot == null || !snapshot.isNavigating) {
            return MessageInfo.Builder("Outreach")
                .setText("Start a route in Outreach on your phone. Active navigation will appear here.")
                .build()
        }

        val cue = snapshot.primaryInstruction
        val maneuver = Maneuver.Builder(Maneuver.TYPE_STRAIGHT).build()
        val currentStep = Step.Builder(cue)
            .setManeuver(maneuver)
            .build()

        val nextStepText = snapshot.nextInstruction
        val routing = RoutingInfo.Builder().setCurrentStep(
            currentStep,
            snapshot.remainingMeters?.let { Distance.create(it, Distance.UNIT_METERS) }
                ?: Distance.create(0.0, Distance.UNIT_METERS)
        )
        if (nextStepText != null) {
            routing.setNextStep(
                Step.Builder(nextStepText)
                    .setManeuver(Maneuver.Builder(Maneuver.TYPE_STRAIGHT).build())
                    .build()
            )
        }
        return routing.build()
    }

    private fun pushTripMetadata(snapshot: OutreachCarNavSnapshot) {
        val remaining = snapshot.remainingMeters ?: return
        val dest = Destination.Builder().setName(snapshot.destinationLabel).build()
        val durSec = snapshot.durationSeconds ?: 0
        val arrival = ZonedDateTime.now().plusSeconds(max(0, durSec).toLong())
        val estimate = TravelEstimate.Builder(
            Distance.create(remaining, Distance.UNIT_METERS),
            arrival
        ).apply {
            if (durSec > 0) setRemainingTimeSeconds(durSec.toLong())
        }.build()
        val trip = Trip.Builder()
            .addDestination(dest, estimate)
            .setCurrentRoad(snapshot.primaryInstruction)
            .build()
        navigationManager.updateTrip(trip)
    }

    private fun syncNavigationSession(active: Boolean) {
        if (active && !lastReportedNavigationActive) {
            navigationManager.navigationStarted()
        } else if (!active && lastReportedNavigationActive) {
            navigationManager.navigationEnded()
        }
        lastReportedNavigationActive = active
    }
}

private class RoutePolylineSurfaceCallback(
    private val snapshotSupplier: () -> OutreachCarNavSnapshot?
) : SurfaceCallback {

    private var container: SurfaceContainer? = null

    fun redraw() {
        val c = container ?: return
        val snapshot = snapshotSupplier()
        val surface = c.surface ?: return
        val canvas = try {
            surface.lockCanvas(null)
        } catch (_: Exception) {
            null
        } ?: return
        try {
            drawRoute(canvas, snapshot, c.width, c.height)
        } finally {
            try {
                surface.unlockCanvasAndPost(canvas)
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        container = surfaceContainer
        redraw()
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        container = null
    }

    private fun drawRoute(canvas: Canvas, snapshot: OutreachCarNavSnapshot?, width: Int, height: Int) {
        canvas.drawColor(Color.rgb(38, 50, 56))
        val pts = snapshot?.routePoints ?: return
        if (pts.size < 2 || width <= 0 || height <= 0) return

        var minLat = pts[0].latitude
        var maxLat = pts[0].latitude
        var minLng = pts[0].longitude
        var maxLng = pts[0].longitude
        for (p in pts) {
            minLat = min(minLat, p.latitude)
            maxLat = max(maxLat, p.latitude)
            minLng = min(minLng, p.longitude)
            maxLng = max(maxLng, p.longitude)
        }
        val latPad = max(1e-4, (maxLat - minLat) * 0.08)
        val lngPad = max(1e-4, (maxLng - minLng) * 0.08)
        minLat -= latPad
        maxLat += latPad
        minLng -= lngPad
        maxLng += lngPad

        val path = Path()
        pts.mapIndexed { index, p ->
            val x = project(p.longitude, minLng, maxLng, width.toFloat())
            val y = projectY(p.latitude, minLat, maxLat, height.toFloat())
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(4f, width * 0.012f)
            color = Color.argb(255, 100, 181, 246)
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(path, paint)
    }

    private fun project(lng: Double, minLng: Double, maxLng: Double, w: Float): Float {
        val span = max(maxLng - minLng, 1e-9)
        return ((lng - minLng) / span * w).toFloat()
    }

    private fun projectY(lat: Double, minLat: Double, maxLat: Double, h: Float): Float {
        val span = max(maxLat - minLat, 1e-9)
        val ny = ((lat - minLat) / span).toFloat()
        return h * (1f - ny)
    }
}
