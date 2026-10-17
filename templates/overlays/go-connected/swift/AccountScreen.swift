import FoundryUI
import FoundrySession
import SwiftUI

struct AccountScreen: View {
    @Binding var glass: Bool
    let model: ConnectedSessionModel
    @State private var login = ""
    @State private var password = ""
    @State private var security = false
    @State private var registering = false
    @State private var managing = false
    @FocusState private var editingCredentials: Bool

    var body: some View {
        Form {
            Section("Your preferences") { ToggleField("Glass", isOn: $glass, help: "Use translucent surfaces.") }
            Section {
                if model.identity != nil { Button("Account security / recover access") { password = ""; security = true }.accessibilityIdentifier("identity.open") }
            }
            Section("Session") {
                if model.controller.isBusy { ProgressView("Updating your session…") }
                if let failure = model.controller.snapshot.failure { Text(failure.meta.message).foregroundStyle(.red) }
                if let account = model.controller.snapshot.account {
                    Text("Selected: \(model.workspaces.displayAccount?.name ?? model.accounts.first(where: { $0.id == account.id })?.name ?? account.name)").accessibilityIdentifier("connection.account")
                    Text(model.controller.snapshot.principal?.displayName ?? "").font(.caption)
                } else {
                    Text("Sign in to your workspace")
                    TextField("Login", text: $login).textInputAutocapitalization(.never).autocorrectionDisabled()
                        .textContentType(.username).focused($editingCredentials).accessibilityIdentifier("connection.login")
                    SecureField("Password", text: $password).textContentType(.password).focused($editingCredentials).accessibilityIdentifier("connection.password")
                    Button("Sign in") { editingCredentials = false; let secret = password; password = ""; model.signIn(login: login, password: secret) }
                        .disabled(model.controller.isBusy).accessibilityIdentifier("connection.sign-in")
                    if let registration = model.registration {
                        Button("Create account") { password = ""; registration.open(); registering = true }
                            .disabled(model.controller.snapshot.phase != .signedOut).accessibilityIdentifier("registration.open")
                        if registration.uncertain { Text(RegistrationModel.interrupted).font(.caption).accessibilityIdentifier("registration.uncertain") }
                    }
                    Button("Restore saved session") { password = ""; model.restore() }.disabled(model.controller.isBusy)
                }
                if model.controller.canRenew {
                    Button("Renew access") { model.renew() }.accessibilityIdentifier("connection.renew")
                }
                Button("Sign out", role: .destructive) { password = ""; login = ""; model.signOut() }
                    .disabled(model.controller.isBusy).accessibilityIdentifier("connection.sign-out")
            }
            if model.controller.lease != nil {
                Section("Workspaces") {
                    Button("Manage workspaces") { managing = true }.accessibilityIdentifier("workspace.open")
                    if model.isLoadingAccounts { ProgressView("Loading workspaces…") }
                    if let failure = model.directoryFailure { Text(failure.meta.message).foregroundStyle(.red) }
                    ForEach(model.accounts, id: \.id) { account in
                        Button("Use \(account.name)") { model.select(account.id) }
                            .disabled(!model.controller.canSelectAccount || account.id == model.controller.snapshot.account?.id)
                    }
                    Button("Refresh workspaces") { model.loadAccounts() }.disabled(model.isLoadingAccounts)
                    if let cursor = model.nextCursor {
                        Button("More workspaces") { model.loadAccounts(cursor: cursor) }.disabled(model.isLoadingAccounts)
                    }
                }
            }
        }
        .onChange(of: model.controller.snapshot.epoch) { _, _ in password = ""; if model.controller.lease == nil { managing = false } }
        .sheet(isPresented: $managing, onDismiss: { model.loadAccounts() }) {
            NavigationStack { WorkspaceView(model: model.workspaces, session: model.controller)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { managing = false }.accessibilityIdentifier("workspace.close") } } }
        }
        .sheet(isPresented: $registering) {
            if let registration = model.registration {
                NavigationStack { RegistrationView(model: registration, session: model.controller) { hint in
                    if !hint.isEmpty { login = hint }; password = ""; registering = false
                }.toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { registering = false }.accessibilityIdentifier("registration.close") } } }
            }
        }
        .sheet(isPresented: $security) {
            if let identity = model.identity {
                NavigationStack { IdentitySettingsView(model: identity, session: model.controller)
                    .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { security = false }.accessibilityIdentifier("identity.close") } } }
            }
        }
    }
}
