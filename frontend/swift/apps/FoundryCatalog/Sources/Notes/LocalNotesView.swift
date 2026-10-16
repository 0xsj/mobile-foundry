import FoundryServices
import FoundryUI
import OSLog
import SwiftUI

struct LocalNotesView: View {
    @State private var provider = LocalNotesProvider.device
    var body: some View {
        LocalNotesExample(provider: provider, onProvider: { provider = $0 }).id(provider)
            .navigationTitle("Local notes").navigationBarTitleDisplayMode(.inline)
    }
}
private struct LocalNotesExample: View {
    @State private var store: LocalNotesStore
    @Environment(\.foundry) private var t
    @FocusState private var titleFocused: Bool
    @State private var deleteTarget: Note?
    let provider: LocalNotesProvider
    let onProvider: (LocalNotesProvider) -> Void
    init(provider: LocalNotesProvider, onProvider: @escaping (LocalNotesProvider) -> Void) {
        self.provider = provider; self.onProvider = onProvider
        _store = State(initialValue: LocalNotesStore(repository: LocalNotesComposition.repository(provider)) { error in
            Logger(subsystem: "dev.mobilefoundry.catalog", category: "local-notes")
                .error("Unexpected local notes error: \(String(reflecting: error), privacy: .private)")
        })
    }
    private func save() {
        if store.save() != nil { titleFocused = false } else if store.titleError != nil { titleFocused = true }
    }
    var body: some View {
        ScrollView {
            VerticalStack(spacing: t.space.section) {
                Card(.floating) {
                    SelectField("Storage", selection: Binding(get: { provider }, set: {
                        guard !store.isBusy else { return }; titleFocused = false; deleteTarget = nil; onProvider($0)
                    }), options: LocalNotesProvider.allCases, enabled: !store.isBusy, label: { $0.rawValue })
                    Text(provider == .device ? "Saved notes stay on this device after you reopen the app. No account or network is needed." :
                         "Memory uses the same feature. These notes are discarded when this source is replaced or the screen closes.")
                        .font(t.typography.caption)
                }
                Card {
                    SectionHeader(store.editingID == nil ? "Create a note" : "Edit note", subtitle: "Save a title of up to 200 Unicode characters.")
                    LabeledTextField("Title", text: Binding(get: { store.title }, set: { store.editTitle($0) }),
                                     help: "Unsaved drafts are temporary.", error: store.titleError, enabled: store.canAct,
                                     focus: $titleFocused, onBlur: { store.blurTitle() }, onSubmit: save)
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
        .task { await store.refresh()?.value }
        .onDisappear { store.stop(); deleteTarget = nil }
        .alert("Delete this note?", isPresented: Binding(get: { deleteTarget != nil }, set: { if !$0 { deleteTarget = nil } }), presenting: deleteTarget) { note in
            Button("Delete note", role: .destructive) { store.delete(note); deleteTarget = nil }
            Button("Keep note", role: .cancel) { deleteTarget = nil }
        } message: { note in Text("“\(note.title)” will be removed from \(provider == .device ? "this device" : "memory").") }
    }
}
