import FoundrySession
import SwiftUI

struct WorkspaceView: View {
    let model: WorkspaceModel
    let session: SessionController
    @State private var rename = ""
    @State private var name = ""
    @FocusState private var editing: Bool
    var body: some View {
        Form {
            if model.isBusy { ProgressView("Updating workspaces…") }
            if let failure = model.failure { Text(failure.meta.message).foregroundStyle(.red).accessibilityIdentifier("workspace.error") }
            if let message = model.message { Text(message).accessibilityIdentifier("workspace.message") }
            Section("Current workspace") {
                if let current = model.current {
                    Text("Current: \(current.account.name)").accessibilityIdentifier("workspace.current")
                }
                Button("Refresh current workspace") { editing = false; model.refresh() }.disabled(model.isBusy).accessibilityIdentifier("workspace.refresh")
                TextField("Workspace name", text: $rename).focused($editing).accessibilityIdentifier("workspace.rename-name")
                Button("Rename workspace") { editing = false; model.rename(rename) }.disabled(!model.canRename).accessibilityIdentifier("workspace.rename")
            }
            Section("Create a workspace") {
                Text("If an earlier create was interrupted, review Workspaces before creating another. Creation leaves selection unchanged.").font(.caption)
                TextField("New workspace name", text: $name).focused($editing).accessibilityIdentifier("workspace.create-name")
                Button("Create workspace") { editing = false; model.create(name) }.disabled(!model.canCreate).accessibilityIdentifier("workspace.create")
                if let created = model.created {
                    Text("Created: \(created.account.name)").accessibilityIdentifier("workspace.created")
                    Button("Prepare another workspace") { name = ""; model.anotherCreate() }.disabled(model.isBusy)
                }
                if model.createUncertain {
                    Text(WorkspaceModel.interruptedCreate).accessibilityIdentifier("workspace.uncertain")
                    Button("Review workspaces") { editing = false; model.review() }.disabled(model.isBusy).accessibilityIdentifier("workspace.review")
                    ForEach(model.reviewAccounts, id: \.id) { Text($0.name) }
                    if let cursor = model.nextCursor { Button("Review next page") { model.review(cursor: cursor) }.disabled(model.isBusy) }
                    Text("Pages are live observations. They cannot prove whether a particular create committed.").font(.caption)
                    Button("I reviewed workspaces; allow a new create") { model.allowNewCreate() }
                        .disabled(!model.reviewed || model.isBusy).accessibilityIdentifier("workspace.allow-create")
                }
            }
        }
        .navigationTitle("Manage workspaces")
        .onAppear { model.refresh() }
        .onChange(of: model.current?.version) { _, _ in rename = model.current?.account.name ?? "" }
        .onChange(of: session.snapshot.epoch) { _, _ in rename = ""; name = "" }
        .onDisappear { rename = ""; name = ""; model.cancel() }
    }
}
