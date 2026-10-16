import Foundation
import FoundryData
import FoundryServices

enum LocalNotesProvider: String, CaseIterable, Identifiable {
    case device = "Device storage", memory = "Memory"
    var id: Self { self }
}
enum LocalNotesComposition {
    static func repository(_ provider: LocalNotesProvider) -> any NotesRepository {
        switch provider {
        case .device: SQLiteNotesRepository(url: URL.applicationSupportDirectory.appendingPathComponent("LocalNotes/notes.sqlite"))
        case .memory: InMemoryNotesRepository()
        }
    }
}
