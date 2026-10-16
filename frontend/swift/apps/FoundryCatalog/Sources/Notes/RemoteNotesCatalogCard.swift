import FoundryServices
import FoundryUI
import SwiftUI

struct RemoteNotesCatalogCard: View {
    @Bindable var model: RemoteNotesCatalogModel
    @State private var deleting: VersionedNote?
    @FocusState private var focused: Bool
    var body: some View {
        Card {
            SectionHeader("Remote notes",subtitle:"Versioned writes in the selected account. Showing the first 20 notes.")
            ActionButton("Refresh notes",variant:.secondary,isBusy:model.isBusy,enabled:!model.isBusy) { model.refresh() }
            LabeledTextField("Title",text:$model.title,enabled:model.canWrite,focus:$focused).accessibilityIdentifier("go.notes.title")
            ActionButton(model.editing == nil ? "Create remote note" : "Save remote note",enabled:model.canWrite) { focused = false; model.save() }.accessibilityIdentifier("go.notes.save")
            if model.editing != nil { ActionButton("New draft",variant:.quiet,enabled:model.canWrite) { model.newDraft() } }
            if let command = model.pending {
                Text("Unconfirmed command: \(command.commandID)").font(.caption).textSelection(.enabled)
                ActionButton("Check write receipt",variant:.secondary,enabled:!model.isBusy) { model.recover(checkOnly:true) }
                ActionButton("Retry original write",variant:.quiet,enabled:!model.isBusy) { model.recover(checkOnly:false) }
            }
            if let receipt = model.lastReceipt { Text("Confirmed \(receipt.action.rawValue), version \(receipt.version)").font(.caption) }
            if let failure = model.failure { Text(failure.meta.message).font(.caption) }
            ForEach(model.notes) { note in
                Text(note.title)
                Text("Version \(note.version)").font(.caption)
                HStack {
                    ActionButton("Edit",variant:.quiet,enabled:model.canWrite) { model.edit(note) }.accessibilityIdentifier("go.notes.edit.\(note.id)")
                    ActionButton("Delete",variant:.destructive,enabled:model.canWrite) { deleting = note }.accessibilityIdentifier("go.notes.delete.\(note.id)")
                }
            }
            if model.hasMore { Text("More notes exist. The repository supports paged reads.").font(.caption) }
        }
        .confirmationDialog("Delete this remote note?",isPresented:Binding(get:{deleting != nil},set:{if !$0 {deleting = nil}}),titleVisibility:.visible) {
            if let note = deleting { Button("Delete",role:.destructive) { model.delete(note); deleting = nil } }
        }
    }
}
