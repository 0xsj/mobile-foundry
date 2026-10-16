import Foundation
import FoundryData
import FoundryServices
import OSLog

/// Product-owned adapter selection. Consumers receive ports, never SQLite handles.
enum NotesComposition {
    static func repository() -> any ObservableNotesRepository {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return SQLiteNotesRepository(url: support.appendingPathComponent("LocalNotes/notes.sqlite"))
    }
    static func unexpected(_ error: any Error) {
        Logger(subsystem: Bundle.main.bundleIdentifier ?? "ProductApp", category: "local-notes")
            .error("Unexpected notes error: \(String(reflecting: error), privacy: .private)")
    }
}
