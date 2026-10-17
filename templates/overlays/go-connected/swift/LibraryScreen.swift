import FoundryServices
import FoundrySession
import SwiftUI

struct LibraryScreen: View {
    let model: ConnectedSessionModel
    @State private var deleting: VersionedNote?

    var body: some View {
        // The credential grant behind lease is deliberately observation-ignored.
        // Track the public snapshot so an eagerly created tab re-renders on sign-in.
        let snapshot = model.controller.snapshot
        if snapshot.phase != .signedIn || model.controller.lease == nil {
            ContentUnavailableView("Your library", systemImage: "books.vertical", description: Text("Sign in from Account to view your notes."))
        } else {
            NotesForm(store: model.notes, deleting: $deleting)
                .safeAreaInset(edge: .top) {
                    HStack { Text(model.liveStatus).font(.caption).accessibilityIdentifier("live.status"); Spacer()
                        Button("Reconnect") { model.reconnectLive() }.accessibilityIdentifier("live.reconnect") }.padding(.horizontal)
                }
                .onChange(of: snapshot.epoch) { _, _ in deleting = nil }
        }
    }
}

private struct NotesForm: View {
    @Bindable var store: RemoteNotesStore
    @Binding var deleting: VersionedNote?
    @FocusState private var editingTitle: Bool
    var body: some View {
        Form {
            Section("Notes") {
                if store.isBusy { ProgressView("Updating notes…") }
                if let failure = store.failure { Text(failure.meta.message).foregroundStyle(.red) }
                Button(store.recoveryLoaded ? "Refresh notes" : "Reload recovery") { store.refresh() }.disabled(store.isBusy)
                if let saved = store.cachedAtMilliseconds {
                    Text("Saved first page · \(Date(timeIntervalSince1970: Double(saved) / 1000).formatted(date: .abbreviated, time: .shortened)). Read-only until refreshed.")
                        .font(.caption).accessibilityIdentifier("notes.cached")
                }
                if let warning = store.cacheFailure { Text(warning.meta.message).font(.caption) }
                Button("Remove saved copy") { store.forgetSavedRead() }.disabled(store.isBusy).accessibilityIdentifier("notes.cache.remove")
                if store.loaded && store.notes.isEmpty { Text("No notes in this workspace.").accessibilityIdentifier("notes.empty") }
                ForEach(store.notes) { note in
                    VStack(alignment: .leading) {
                        Text(note.title)
                        HStack {
                            Button("Edit") { store.edit(note) }.accessibilityIdentifier("notes.edit.\(note.id)")
                            Button("Delete", role: .destructive) { deleting = note }.accessibilityIdentifier("notes.delete.\(note.id)")
                        }.buttonStyle(.borderless).disabled(!store.canWrite)
                    }
                }
                if let after = store.nextAfter { Button("Next page") { store.refresh(after: after) }.disabled(store.isBusy) }
            }
            if let pending = store.pending {
                Section("Unconfirmed change") {
                    Text("Saved on this device. Check its receipt or retry this exact change; recovery never sends it automatically.")
                        .accessibilityIdentifier("notes.pending.\(pending.commandID)")
                    if pending.action != .delete { Text(pending.title) }
                    Button("Check receipt") { store.recover(checkOnly: true) }.disabled(store.isBusy)
                    Button("Retry same change") { store.recover(checkOnly: false) }.disabled(store.isBusy)
                }
            }
            Section(store.editing == nil ? "New note" : "Edit note") {
                TextField("Title", text: $store.title).focused($editingTitle).disabled(!store.canWrite).accessibilityIdentifier("notes.title")
                Button(store.editing == nil ? "Create note" : "Save note") { editingTitle = false; store.save() }
                    .disabled(!store.canWrite).accessibilityIdentifier("notes.save")
                if store.editing != nil { Button("New note") { store.newDraft() }.disabled(!store.canWrite) }
                if let receipt = store.lastReceipt { Text("Confirmed \(receipt.action.rawValue), version \(receipt.version)").font(.caption) }
            }
        }
        .confirmationDialog("Delete this note?", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } })) {
            Button("Delete note", role: .destructive) { if let note = deleting { store.delete(note) }; deleting = nil }
        }
    }
}
