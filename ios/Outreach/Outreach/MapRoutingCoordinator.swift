import AVFoundation
import Combine
import CoreLocation
import Foundation
import MapKit

/// Turn-by-turn text and arrival stats derived from `MKRoute` for in-app UI (not persisted).
struct DrivingRouteSummary: Equatable {
    var destinationName: String
    var expectedTravelTime: TimeInterval
    var distanceMeters: CLLocationDistance
    /// Non-empty instruction strings from `MKRoute.Step`, in order.
    var stepInstructions: [String]

    var etaShortFormatted: String {
        let f = DateComponentsFormatter()
        f.allowedUnits = expectedTravelTime >= 3600 ? [.hour, .minute] : [.minute]
        f.unitsStyle = .abbreviated
        return f.string(from: expectedTravelTime)
            ?? "\(max(1, Int(expectedTravelTime / 60))) min"
    }

    var distanceShortFormatted: String {
        let m = Measurement(value: distanceMeters, unit: UnitLength.meters)
        let fmt = MeasurementFormatter()
        fmt.unitOptions = .naturalScale
        fmt.numberFormatter.maximumFractionDigits = 1
        return fmt.string(from: m)
    }

    /// Local clock time if you depart now (MapKit ETA does not include live traffic).
    var projectedArrivalClockTime: String {
        let arrive = Date().addingTimeInterval(expectedTravelTime)
        let f = DateFormatter()
        f.timeStyle = .short
        f.dateStyle = .none
        return f.string(from: arrive)
    }
}

/// Fetches the user location and draws a driving route in-app via `MKDirections` (no handoff to Apple Maps).
@MainActor
final class MapRoutingCoordinator: ObservableObject {
    @Published private(set) var routeCoordinates: [CLLocationCoordinate2D] = []
    /// Bumps when `routeCoordinates` changes so SwiftUI can observe route updates without comparing coordinate arrays.
    @Published private(set) var routeFitRevision: Int = 0
    @Published private(set) var routeSummary: DrivingRouteSummary?
    @Published private(set) var routingError: String?
    @Published private(set) var isCalculatingRoute = false

    private let locationManager = CLLocationManager()
    private let bridge = LocationBridge()
    private let speech = RouteNavigationSpeech()
    private var pendingDestination: CLLocationCoordinate2D?
    private var pendingDestinationName: String = ""

    init() {
        bridge.owner = self
        locationManager.delegate = bridge
        locationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func clearRoute() {
        speech.stop()
        routeCoordinates = []
        routeSummary = nil
        routeFitRevision += 1
        routingError = nil
        pendingDestination = nil
        pendingDestinationName = ""
    }

    /// Replays the spoken navigation summary (same as when the route first appears).
    func repeatSpokenSummary() {
        guard let summary = routeSummary else { return }
        speech.announce(summary: summary)
    }

    func acknowledgeRoutingError() {
        routingError = nil
    }

    func requestDrivingRoute(to destination: CLLocationCoordinate2D, destinationName: String) {
        routingError = nil
        routeCoordinates = []
        routeSummary = nil
        speech.stop()
        isCalculatingRoute = true
        pendingDestination = destination
        pendingDestinationName = destinationName

        switch locationManager.authorizationStatus {
        case .notDetermined:
            locationManager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse, .authorizedAlways:
            locationManager.requestLocation()
        case .denied, .restricted:
            finishWithError("Turn on Location in Settings to see routes in Outreach.")
        @unknown default:
            finishWithError("Location is not available.")
        }
    }

    private func finishWithError(_ message: String) {
        isCalculatingRoute = false
        routingError = message
        pendingDestination = nil
    }

    fileprivate func handleAuthorizationChange() {
        guard pendingDestination != nil else { return }
        switch locationManager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            locationManager.requestLocation()
        case .denied, .restricted:
            finishWithError("Turn on Location in Settings to see routes in Outreach.")
        default:
            break
        }
    }

    fileprivate func handleLocations(_ locations: [CLLocation]) {
        guard let dest = pendingDestination else { return }
        guard let coord = locations.last?.coordinate else {
            finishWithError("Could not get your location.")
            return
        }
        pendingDestination = nil
        Task {
            await calculateRoute(from: coord, to: dest)
        }
    }

    fileprivate func handleLocationFailure() {
        guard pendingDestination != nil else { return }
        finishWithError("Could not get your location.")
    }

    private func calculateRoute(from: CLLocationCoordinate2D, to: CLLocationCoordinate2D) async {
        defer { isCalculatingRoute = false }

        let request = MKDirections.Request()
        request.source = MKMapItem(placemark: MKPlacemark(coordinate: from))
        request.destination = MKMapItem(placemark: MKPlacemark(coordinate: to))
        request.transportType = .automobile

        do {
            let response = try await MKDirections(request: request).calculate()
            guard let route = response.routes.first else {
                routingError = "No driving route found."
                return
            }
            let steps = route.steps.compactMap { step -> String? in
                let t = step.instructions.trimmingCharacters(in: .whitespacesAndNewlines)
                return t.isEmpty ? nil : t
            }
            let name = pendingDestinationName.isEmpty ? "Destination" : pendingDestinationName
            routeSummary = DrivingRouteSummary(
                destinationName: name,
                expectedTravelTime: route.expectedTravelTime,
                distanceMeters: route.distance,
                stepInstructions: steps
            )
            routeCoordinates = Self.points(for: route.polyline)
            routeFitRevision += 1
            routingError = nil
            if let summary = routeSummary {
                speech.announce(summary: summary)
            }
        } catch {
            routingError = "Could not compute a route."
        }
    }

    private static func points(for polyline: MKPolyline) -> [CLLocationCoordinate2D] {
        let n = polyline.pointCount
        guard n > 0 else { return [] }
        var coords = [CLLocationCoordinate2D](repeating: kCLLocationCoordinate2DInvalid, count: n)
        polyline.getCoordinates(&coords, range: NSRange(location: 0, length: n))
        return coords
    }
}

// MARK: - Spoken prompts (not full Apple Maps turn-by-turn; uses system text-to-speech)

private final class RouteNavigationSpeech {
    private let synthesizer = AVSpeechSynthesizer()

    func stop() {
        synthesizer.stopSpeaking(at: .immediate)
    }

    func announce(summary: DrivingRouteSummary) {
        stop()
        activateSessionForSpeech()
        let eta = Self.formatETA(summary.expectedTravelTime)
        let dist = Self.formatDistance(summary.distanceMeters)
        let arriveClock = summary.projectedArrivalClockTime
        let first = summary.stepInstructions.first ?? "Follow the highlighted route on the map."
        let text =
            "Starting navigation to \(summary.destinationName). Driving time about \(eta), \(dist). Arrive around \(arriveClock). \(first)"
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: Locale.current.identifier)
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate * 0.92
        synthesizer.speak(utterance)
    }

    private func activateSessionForSpeech() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
            try session.setActive(true)
        } catch {
            // Speaking may still work with default session.
        }
    }

    private static func formatETA(_ seconds: TimeInterval) -> String {
        let f = DateComponentsFormatter()
        f.allowedUnits = seconds >= 3600 ? [.hour, .minute] : [.minute]
        f.unitsStyle = .abbreviated
        return f.string(from: seconds) ?? "\(Int(seconds / 60)) min"
    }

    private static func formatDistance(_ meters: CLLocationDistance) -> String {
        let m = Measurement(value: meters, unit: UnitLength.meters)
        let f = MeasurementFormatter()
        f.unitOptions = .naturalScale
        f.numberFormatter.maximumFractionDigits = 1
        return f.string(from: m)
    }
}

private final class LocationBridge: NSObject, CLLocationManagerDelegate {
    weak var owner: MapRoutingCoordinator?

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in
            owner?.handleAuthorizationChange()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        Task { @MainActor in
            owner?.handleLocations(locations)
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in
            owner?.handleLocationFailure()
        }
    }
}

extension MKCoordinateRegion {
    /// Builds a region that contains all coordinates (e.g. to frame a route).
    init?(fitting coordinates: [CLLocationCoordinate2D], paddingFactor: Double = 1.35) {
        guard let first = coordinates.first else { return nil }
        var minLat = first.latitude
        var maxLat = first.latitude
        var minLon = first.longitude
        var maxLon = first.longitude
        for c in coordinates.dropFirst() {
            minLat = min(minLat, c.latitude)
            maxLat = max(maxLat, c.latitude)
            minLon = min(minLon, c.longitude)
            maxLon = max(maxLon, c.longitude)
        }
        let latMid = (minLat + maxLat) / 2
        let lonMid = (minLon + maxLon) / 2
        let latDelta = max((maxLat - minLat) * paddingFactor, 0.02)
        let lonDelta = max((maxLon - minLon) * paddingFactor, 0.02)
        self.init(
            center: CLLocationCoordinate2D(latitude: latMid, longitude: lonMid),
            span: MKCoordinateSpan(latitudeDelta: latDelta, longitudeDelta: lonDelta)
        )
    }
}
