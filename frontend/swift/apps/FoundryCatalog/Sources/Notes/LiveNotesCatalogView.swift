import FoundryQuery
import FoundryUI
import SwiftUI

struct LiveNotesCatalogView: View {
    @State private var provider = LocalNotesProvider.device
    @State private var model = LiveNotesCatalogModel()
    var body: some View {
        VStack(spacing: 0) {
            LiveNotesExample(source: model.source(provider), provider: provider, onProvider: { provider = $0 }).id(provider)
        }
            .navigationTitle("Live notes").navigationBarTitleDisplayMode(.inline)
            .onDisappear { model.shutdown() }
    }
}
private struct LiveNotesExample: View {
    let source: LiveNotesSource
    let provider: LocalNotesProvider
    let onProvider: (LocalNotesProvider) -> Void
    @State private var secondPaused: Bool
    @FocusState private var titleFocused: Bool
    @Environment(\.foundry) private var t
    @Environment(\.scenePhase) private var scenePhase
    init(source: LiveNotesSource, provider: LocalNotesProvider, onProvider: @escaping (LocalNotesProvider) -> Void) {
        self.source = source; self.provider = provider; self.onProvider = onProvider
        _secondPaused = State(initialValue: source.secondPaused)
    }
    private var writer: LocalNotesStore { source.writer }
    var body: some View {
        ScrollView {
            VerticalStack(spacing: t.space.section) {
                Card(.floating) {
                    SectionHeader("Two live readers", subtitle: "Write once. Independent readers observe current local data.")
                    SelectField("Storage", selection: Binding(get: { provider }, set: { value in
                        guard !writer.isBusy else { return }; titleFocused = false; source.pause(); onProvider(value)
                    }), options: LocalNotesProvider.allCases, enabled: !writer.isBusy, label: { $0.rawValue })
                    Text(provider == .device ? "Separate SQLite instances coordinate writes in this process. This gallery uses its own database." :
                        "One Memory repository feeds both readers. It remains in this route until the gallery closes.").font(t.typography.caption)
                }
                Card {
                    SectionHeader("Writer", subtitle: "Use this editor while watching the readers below.")
                    LabeledTextField("Live note title", text: Binding(get: { writer.title }, set: { writer.editTitle($0) }), error: writer.titleError,
                        enabled: writer.canAct, focus: $titleFocused, onBlur: { writer.blurTitle() }, onSubmit: { save() })
                    ActionButton(writer.editingID == nil ? "Create live note" : "Save live note", isBusy: writer.mutation.isSubmitting, enabled: writer.canAct) { save() }
                    if writer.editingID != nil { ActionButton("New live note", variant: .quiet, enabled: writer.canAct) { writer.newNote() } }
                    MutationFeedback(writer.mutation, submitting: "Saving…") { Text($0).font(t.typography.caption) }
                    ForEach(writer.query.value ?? []) { note in
                        ListRow(note.title, leading: { EmptyView() }, trailing: {
                            VStack {
                                ActionButton("Edit", variant: .quiet, enabled: writer.canAct) { writer.edit(note); titleFocused = true }.accessibilityLabel("Edit live note \(note.title)")
                                ActionButton("Delete", variant: .destructive, enabled: writer.canAct) { writer.delete(note) }.accessibilityLabel("Delete live note \(note.title)")
                            }
                        })
                    }
                    if let failure = writer.query.failure { Text(failure.publicInfo().meta.message) }
                    ActionButton("Reload writer", variant: .quiet, enabled: !writer.isBusy) { writer.refresh() }
                }
                LiveNotesReaderCard(title: "First reader", store: source.first)
                Card {
                    ActionButton(secondPaused ? "Resume second reader" : "Pause second reader", variant: .secondary) {
                        secondPaused.toggle(); source.secondPaused = secondPaused
                        if secondPaused { source.second.pause() } else { source.second.start() }
                    }
                    LiveNotesReaderContent(title: "Second reader", store: source.second)
                }
            }.padding(t.space.page)
        }
        .task { if scenePhase == .active { source.resume() } }
        .onChange(of: scenePhase) { _, phase in if phase == .active { source.resume() } else { source.pause() } }
        .onDisappear { titleFocused = false; source.pause() }
    }
    private func save() { if writer.save() != nil { titleFocused = false } }
}
struct LiveNotesReaderCard: View {
    let title: String
    let store: LiveNotesStore
    var body: some View { Card { LiveNotesReaderContent(title: title, store: store) } }
}
private struct LiveNotesReaderContent: View {
    let title: String
    let store: LiveNotesStore
    @Environment(\.foundry) private var t
    var body: some View {
        SectionHeader(title, subtitle: store.isWatching ? "Watching local changes" : "Paused · last snapshot")
        QueryContent(state: store.query, copy: .init(idle: "No \(title.lowercased()) snapshot.", loading: "Loading \(title.lowercased())…", refreshing: "Resuming \(title.lowercased())…",
            empty: "\(title): 0 notes", refresh: "Restart \(title.lowercased())", cancel: "Pause \(title.lowercased())"), isEmpty: { $0.isEmpty }, refresh: { store.restart() }, cancel: { store.pause() }) { notes in
                Text("\(title): \(notes.count) notes").font(t.typography.caption)
                ForEach(notes) { note in Text("\(title): \(note.title)") }
        }
    }
}
