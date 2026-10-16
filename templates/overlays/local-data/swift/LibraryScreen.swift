import FoundryServices
import FoundryUI
import SwiftUI

struct LibraryScreen: View {
    @State private var store: LocalNotesStore
    @State private var reader: LiveNotesStore
    @State private var deleteTarget: Note?
    @State private var visible = false
    @FocusState private var titleFocused: Bool
    @Environment(\.foundry) private var t
    @Environment(\.scenePhase) private var scenePhase

    init(repository: any ObservableNotesRepository = NotesComposition.repository()) {
        _store = State(initialValue: LocalNotesStore(repository: repository, onUnexpectedError: NotesComposition.unexpected))
        _reader = State(initialValue: LiveNotesStore(source: repository, onUnexpectedError: NotesComposition.unexpected))
    }
    private func save() {
        if store.save() != nil { titleFocused = false }
        else if store.titleError != nil { titleFocused = true }
    }
    private func resume() { reader.start(); store.refresh() }
    private func pause() { reader.pause(); store.stop(); titleFocused = false; deleteTarget = nil }

    var body: some View {
        ScrollView {
            VerticalStack(spacing: t.space.section) {
                PageHeader("Library", subtitle: "Notes saved on this device")
                Card(.floating) {
                    SectionHeader("Local collection", subtitle: "No account or network is needed. Unsaved drafts are temporary.")
                    if let notes = reader.query.value { Text("Live collection: \(notes.count) notes").font(t.typography.caption) }
                    else { Text("Reading local collection…").font(t.typography.caption) }
                    if let failure = reader.query.failure {
                        Text(failure.publicInfo().meta.message).font(t.typography.caption)
                        ActionButton("Restart observation", variant: .quiet) { reader.restart() }
                    }
                }
                Card {
                    SectionHeader(store.editingID == nil ? "Create a note" : "Edit note", subtitle: "Save a title of up to 200 Unicode characters.")
                    LabeledTextField("Title", text: Binding(get: { store.title }, set: { store.editTitle($0) }),
                        help: "Saved records survive reopening the app.", error: store.titleError, enabled: store.canAct,
                        focus: $titleFocused, onBlur: { store.blurTitle() }, onSubmit: save)
                        .accessibilityIdentifier("note-title")
                    ActionButton(store.editingID == nil ? "Create note" : "Save changes", isBusy: store.mutation.isSubmitting,
                        enabled: store.canAct, action: save)
                    if store.editingID != nil { ActionButton("New note", variant: .quiet, enabled: store.canAct) { store.newNote() } }
                    MutationFeedback(store.mutation, submitting: "Saving local change…") { Text($0).font(t.typography.caption) }
                }
                Card {
                    SectionHeader("Saved notes", subtitle: "Edits keep their position. Delete removes only the selected note.")
                    QueryContent(state: store.query, copy: .init(idle: "Ready to load notes.", loading: "Loading saved notes…",
                        refreshing: "Reloading saved notes…", empty: "No saved notes yet."), isEmpty: { $0.isEmpty },
                        refresh: { store.refresh() }, cancel: { store.stop() }) { notes in
                        ForEach(notes) { note in
                            ListRow(note.title, leading: { EmptyView() }, trailing: {
                                VStack(alignment: .leading) {
                                    ActionButton("Edit", variant: .quiet, enabled: store.canAct) { store.edit(note); titleFocused = true }
                                        .accessibilityLabel("Edit \(note.title)")
                                    ActionButton("Delete", variant: .destructive, enabled: store.canAct) { titleFocused = false; deleteTarget = note }
                                        .accessibilityLabel("Delete \(note.title)")
                                }
                            })
                            SectionDivider()
                        }
                    }
                }
            }.padding(t.space.page)
        }
        .task { visible = true; if scenePhase == .active { resume() } }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active && visible { resume() } else { pause() }
        }
        .onDisappear { visible = false; pause() }
        .alert("Delete this note?", isPresented: Binding(get: { deleteTarget != nil }, set: { if !$0 { deleteTarget = nil } }), presenting: deleteTarget) { note in
            Button("Delete note", role: .destructive) { store.delete(note); deleteTarget = nil }
            Button("Keep note", role: .cancel) { deleteTarget = nil }
        } message: { note in Text("“\(note.title)” will be removed from this device.") }
    }
}
