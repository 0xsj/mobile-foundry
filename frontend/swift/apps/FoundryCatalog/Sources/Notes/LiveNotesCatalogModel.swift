import Foundation
import FoundryData
import FoundryServices
import OSLog

@MainActor final class LiveNotesSource {
    let writer: LocalNotesStore
    let first: LiveNotesStore
    let second: LiveNotesStore
    var secondPaused = false
    init(writer: any NotesRepository, observer: any NotesObserver) {
        let report: (any Error) -> Void = { error in
            Logger(subsystem: "dev.mobilefoundry.catalog", category: "live-notes").error("Unexpected live notes error: \(String(reflecting: error), privacy: .private)")
        }
        self.writer = LocalNotesStore(repository: writer, onUnexpectedError: report)
        first = LiveNotesStore(source: observer, onUnexpectedError: report)
        second = LiveNotesStore(source: observer, onUnexpectedError: report)
    }
    func resume() { writer.refresh(); first.start(); if !secondPaused { second.start() } }
    func pause() { writer.stop(); first.pause(); second.pause() }
    func shutdown() { writer.shutdown(); first.shutdown(); second.shutdown() }
}
/// Retain source bundles above provider branches; reconstruction never makes new fixture dependencies.
@MainActor final class LiveNotesCatalogModel {
    private let url: URL
    private var sources: [LocalNotesProvider: LiveNotesSource] = [:]
    init(url: URL = URL.applicationSupportDirectory.appendingPathComponent("LiveNotes/notes.sqlite")) { self.url = url }
    func source(_ provider: LocalNotesProvider) -> LiveNotesSource {
        if let source = sources[provider] { return source }
        let source: LiveNotesSource
        switch provider {
        case .device: source = LiveNotesSource(writer: SQLiteNotesRepository(url: url), observer: SQLiteNotesRepository(url: url))
        case .memory:
            let repository = InMemoryNotesRepository(); source = LiveNotesSource(writer: repository, observer: repository)
        }
        sources[provider] = source; return source
    }
    func shutdown() { for source in sources.values { source.shutdown() } }
}
