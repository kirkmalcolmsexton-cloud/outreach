import Foundation
import SwiftData
import FirebaseCore
import FirebaseFirestore

@MainActor
enum ServiceLocator {
    static var repository: OutreachRepository?
    static var collaboration: CollaborationRepository?

    static func setupIfNeeded(
        modelContext: ModelContext,
        appConfig: AppConfigStore
    ) {
        if repository != nil { return }
        let geo = GeocodingService()
        let sheets = GoogleSheetsApi(tokenProvider: GoogleTokenProvider())
        repository = OutreachRepository(
            modelContext: modelContext,
            configStore: appConfig,
            sheetsApi: sheets,
            geocoder: geo
        )
        if FirebaseApp.app() != nil {
            collaboration = CollaborationRepository()
        }
    }
}

@MainActor
final class CollaborationRepository {
    private let firestore: Firestore
    init(firestore: Firestore = Firestore.firestore()) {
        self.firestore = firestore
    }

    func publishPresence(userId: String, tabName: String) async throws {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
            firestore.collection("presence").document(userId).setData([
                "tabName": tabName,
                "updatedAt": Int64(Date().timeIntervalSince1970 * 1000)
            ]) { err in
                if let err { cont.resume(throwing: err) } else { cont.resume() }
            }
        }
    }

    func publishActivity(_ event: CollaborationEvent) async throws {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
            firestore.collection("activity").addDocument(data: [
                "userId": event.userId,
                "householdId": event.householdId as Any,
                "tabName": event.tabName as Any,
                "type": event.type,
                "epochMillis": event.epochMillis
            ]) { err in
                if let err { cont.resume(throwing: err) } else { cont.resume() }
            }
        }
    }
}

enum BackgroundSyncRunner {
    @MainActor
    static func runSync() async throws {
        guard let r = ServiceLocator.repository else { return }
        try await r.flushPendingSync()
        try await r.syncFromSheet()
    }
}
