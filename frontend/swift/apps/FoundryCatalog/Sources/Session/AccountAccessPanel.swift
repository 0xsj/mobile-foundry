import FoundryServices
import FoundrySession
import FoundryUI
import OSLog
import SwiftUI

/// Inline so navigating a child does not accidentally shut down the gallery's session owner.
struct AccountAccessPanel: View {
    @State private var store: LocalNotesStore
    @State private var probe: AuthenticatedProbeStore
    @State private var reader: LiveNotesStore
    @State private var response = ProbeResponse.success
    @State private var deleteTarget: Note?
    @FocusState private var titleFocused: Bool
    @Environment(\.foundry) private var t
    @Environment(\.scenePhase) private var scenePhase
    let lease: SessionLease
    init(model: SessionCatalogModel, lease: SessionLease) {
        self.lease = lease
        _store = State(initialValue: LocalNotesStore(repository: model.notes.bind(session: model.controller, lease: lease)) { error in
            Logger(subsystem: "dev.mobilefoundry.catalog", category: "account-notes").error("Unexpected note error: \(String(reflecting: error), privacy: .private)")
        })
        _probe = State(initialValue: AuthenticatedProbeStore(session: model.controller, lease: lease))
        _reader = State(initialValue: LiveNotesStore(source: model.notes.repository(for: lease.scope), admitted: { model.controller.isCurrent(lease) }) { error in
            Logger(subsystem: "dev.mobilefoundry.catalog", category: "account-observation").error("Unexpected observation error: \(String(reflecting: error), privacy: .private)")
        })
    }
    var body: some View {
        Card {
            SectionHeader("Account notes", subtitle: lease.scope.accountID)
            Text("Saved notes belong to this account on this device. Signing out keeps them; switching accounts discards the editor draft.").font(t.typography.caption)
            LabeledTextField("Account note title", text: Binding(get: { store.title }, set: { store.editTitle($0) }), error: store.titleError,
                enabled: store.canAct, focus: $titleFocused, onBlur: { store.blurTitle() }, onSubmit: { save() })
            ActionButton(store.editingID == nil ? "Create account note" : "Save account note", isBusy: store.mutation.isSubmitting, enabled: store.canAct, action: save)
            if store.editingID != nil { ActionButton("New account note", variant: .quiet, enabled: store.canAct) { store.newNote() } }
            MutationFeedback(store.mutation, submitting: "Saving account change…") { Text($0).font(t.typography.caption) }
            QueryContent(state: store.query, copy: .init(idle: "Ready to load this account.", loading: "Loading account notes…", refreshing: "Reloading account notes…", empty: "No notes in this account."), isEmpty: { $0.isEmpty }, refresh: { store.refresh() }, cancel: { store.stop() }) { notes in
                ForEach(notes, id: \.id) { note in
                    ListRow(note.title, leading: { EmptyView() }, trailing: {
                        VStack {
                            ActionButton("Edit", variant: .quiet, enabled: store.canAct) { store.edit(note); titleFocused = true }
                            ActionButton("Delete", variant: .destructive, enabled: store.canAct) { titleFocused = false; deleteTarget = note }
                        }
                    })
                }
            }
        }
        Card {
            SectionHeader("Authenticated HTTP", subtitle: "Local response fixture. No server connection.")
            SelectField("Probe response", selection: $response, options: ProbeResponse.allCases, enabled: !probe.isBusy, label: { $0.rawValue })
            ActionButton("Send authenticated probe", isBusy: probe.isBusy, enabled: !probe.isBusy) { probe.run(response) }
            if let report = probe.report { Text(report).font(t.typography.caption) }
            Text("Unauthorized returns one failure. Use the lifecycle controls to renew explicitly.").font(t.typography.caption)
        }
        .task { await store.refresh()?.value }
        .onDisappear { titleFocused = false; deleteTarget = nil; store.shutdown(); probe.shutdown(); reader.shutdown() }
        .alert("Delete this account note?", isPresented: Binding(get: { deleteTarget != nil }, set: { if !$0 { deleteTarget = nil } }), presenting: deleteTarget) { note in
            Button("Delete note", role: .destructive) { store.delete(note); deleteTarget = nil }
            Button("Keep note", role: .cancel) { deleteTarget = nil }
        } message: { note in Text("“\(note.title)” will be removed from this account on this device.") }
        LiveNotesReaderCard(title: "Account live reader", store: reader)
            .task { if scenePhase == .active { reader.start() } }
            .onChange(of: scenePhase) { _, phase in if phase == .active { reader.start() } else { reader.pause() } }
            .onDisappear { reader.shutdown() }
    }
    private func save() { if store.save() != nil { titleFocused = false } }
}
