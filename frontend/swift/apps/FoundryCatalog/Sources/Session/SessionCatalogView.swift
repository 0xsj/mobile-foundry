import Foundation
import FoundryKernel
import FoundryData
import FoundrySession
import FoundryUI
import SwiftUI

struct SessionCatalogView: View {
    @State private var provider = SessionProvider.device
    var body: some View {
        SessionExample(provider: provider, onProvider: { provider = $0 }).id(provider)
            .navigationTitle("Identity and session").navigationBarTitleDisplayMode(.inline)
    }
}
private struct SessionExample: View {
    @State private var model: SessionCatalogModel
    @State private var login = "alex@example.test"
    @State private var password = ""
    @FocusState private var passwordFocused: Bool
    @FocusState private var loginFocused: Bool
    @Environment(\.foundry) private var t
    private var controller: SessionController { model.controller }
    let provider: SessionProvider
    let onProvider: (SessionProvider) -> Void
    init(provider: SessionProvider, onProvider: @escaping (SessionProvider) -> Void) {
        self.provider = provider; self.onProvider = onProvider
        _model = State(initialValue: SessionCatalogModel(vault: SessionComposition.vault(provider),
            notes: AccountNotesRepositories(storage: provider == .device ? .device : .memory, root: URL.applicationSupportDirectory.appendingPathComponent("AccountNotes/v1"))))
    }
    private var fields: [String: String] { if case .invalid(_, let fields) = controller.snapshot.failure { fields } else { [:] } }
    private func signIn() { if controller.signIn(login: login, password: password) != nil { password = ""; passwordFocused = false } }
    var body: some View {
        ScrollView {
            VerticalStack(spacing: t.space.section) {
                Card(.floating) {
                    SectionHeader("Demo authority", subtitle: "Local lifecycle example. These accounts do not authorize real data.")
                    SelectField("Credentials", selection: Binding(get: { provider }, set: { if !controller.isBusy { password = ""; passwordFocused = false; onProvider($0) } }),
                                options: SessionProvider.allCases, enabled: !controller.isBusy, label: { $0.rawValue })
                    Text(provider == .device ? "A refresh credential is saved in Keychain. Reopen this gallery to restore it." : "Credentials live in memory until this source or screen closes.").font(t.typography.caption)
                }
                Card {
                    SectionHeader("Session", subtitle: "State: \(controller.snapshot.phase.rawValue)")
                    Text(controller.snapshot.principal?.displayName ?? "No active identity")
                    if let account = controller.snapshot.account { Text(account.name) }
                    Text(controller.lease == nil ? "Account access is closed." : "Account access is active.").font(t.typography.caption)
                    if let failure = controller.snapshot.failure { Text(failure.publicInfo().meta.message).font(t.typography.caption).accessibilityIdentifier("session.failure") }
                }
                Card {
                    SectionHeader("Sign in", subtitle: "Use alex@example.test or sam@example.test with password catalog-demo.")
                    LabeledTextField("Login", text: $login, error: fields["login"], enabled: !controller.isBusy, focus: $loginFocused,
                                     submitLabel: .next, onSubmit: { passwordFocused = true })
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                    PasswordField("Password", text: $password, error: fields["password"], enabled: !controller.isBusy, focus: $passwordFocused, onSubmit: signIn)
                    ActionButton("Sign in", isBusy: controller.snapshot.phase == .signingIn, enabled: !controller.isBusy, action: signIn)
                }
                Card {
                    SectionHeader("Lifecycle controls", subtitle: "The demo clock moves only when you advance it. Access lasts 60 seconds.")
                    ActionButton("Restore saved session", variant: .secondary, enabled: !controller.isBusy) { controller.restore() }
                    ActionButton("Renew access", variant: .secondary, enabled: controller.canRenew) { controller.renew() }
                    ActionButton("Advance time 61 seconds", variant: .quiet, enabled: !controller.isBusy) { model.advanceTime() }
                    ActionButton("Reject next renewal", variant: .quiet, enabled: !controller.isBusy && controller.snapshot.phase == .signedIn) {
                        model.rejectRenewal()
                    }
                    ActionButton("Sign out", variant: .destructive, enabled: controller.snapshot.phase != .signingOut) { password = ""; passwordFocused = false; controller.signOut() }
                    Text("Sign-out closes account access and clears the credential. Saved account notes remain on this device; guest Local notes stay separate.").font(t.typography.caption)
                }
                if let lease = controller.lease { AccountAccessPanel(model: model, lease: lease).id(lease) }
            }.padding(t.space.page)
        }
        .task { await controller.restore()?.value }
        .onDisappear { password = ""; passwordFocused = false; controller.shutdown() }
    }
}
