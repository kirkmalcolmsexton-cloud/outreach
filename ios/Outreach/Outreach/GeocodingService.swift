import CoreLocation
import Foundation

actor GeocodingService {
    private let geocoder = CLGeocoder()

    func geocodeAddress(_ address: String) async -> (Double, Double)? {
        let a = address.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !a.isEmpty else { return nil }
        return await withCheckedContinuation { cont in
            geocoder.geocodeAddressString(a) { placemarks, _ in
                guard let p = placemarks?.first,
                      let l = p.location
                else {
                    cont.resume(returning: nil)
                    return
                }
                cont.resume(returning: (l.coordinate.latitude, l.coordinate.longitude))
            }
        }
    }
}
