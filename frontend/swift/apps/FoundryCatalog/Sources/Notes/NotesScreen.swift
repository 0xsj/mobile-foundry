import SwiftUI
import FoundryQuery
import FoundryUI
import FoundryServices

/// The feature supplies domain copy, emptiness, and rows to reusable presentation.
struct NotesScreen: View {
    let state: QueryState<[Note]>
    let refresh: () -> Void
    let cancel: () -> Void

    var body: some View {
        Section("Notes") {
            QueryContent(state: state, copy: .init(
                idle: "Ready to load notes.", loading: "Loading notes…",
                refreshing: "Refreshing notes…", empty: "No notes yet."
            ), isEmpty: { $0.isEmpty }, refresh: refresh, cancel: cancel) { notes in
                ForEach(notes) { Text($0.title) }
            }
        }
    }
}
