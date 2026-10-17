import FoundrySession
import SwiftUI

struct RegistrationView: View {
    let model: RegistrationModel
    let session: SessionController
    let onSignIn: (String) -> Void
    @Environment(\.scenePhase) private var scenePhase
    @State private var login = ""
    @State private var name = ""
    @State private var password = ""
    private enum Field: Hashable { case login, name, password }
    @FocusState private var editing: Field?
    var body: some View {
        Form {
            Section {
                if model.isBusy { ProgressView("Creating your account…") }
                if let message = model.message { Text(message).accessibilityIdentifier("registration.message") }
                if let failure = model.failure {
                    Text(failure.meta.message).foregroundStyle(.red).accessibilityIdentifier("registration.failure")
                    if case .invalid(_, let fields) = failure {
                        ForEach(fields.keys.sorted(), id: \.self) { field in
                            Text(fields[field]!).font(.caption).foregroundStyle(.red).accessibilityIdentifier("registration.error.\(field)")
                        }
                    }
                }
            }
            if model.registered == nil {
                Section("Your account") {
                    TextField("Login", text: $login).textContentType(.username).textInputAutocapitalization(.never).autocorrectionDisabled()
                        .focused($editing, equals: .login).accessibilityIdentifier("registration.login")
                    TextField("Display name", text: $name).textContentType(.name).focused($editing, equals: .name).accessibilityIdentifier("registration.name")
                    SecureField("New password", text: $password).textContentType(.newPassword).focused($editing, equals: .password).accessibilityIdentifier("registration.password")
                    Text("Use at least 12 password characters. Your login and recovery email are separate; add a verified recovery address after signing in.").font(.caption)
                    Button("Create account") {
                        editing = nil; let secret = password; password = ""
                        model.register(login: login, displayName: name, password: secret)
                    }.disabled(!model.canSubmit).accessibilityIdentifier("registration.submit")
                }.disabled(model.isBusy || session.snapshot.phase != .signedOut)
            }
            Section {
                Button(model.registered == nil ? "Back to sign in" : "Continue to sign in") { editing = nil; password = ""; onSignIn(model.loginHint) }
                    .accessibilityIdentifier("registration.continue")
            }
        }
        .navigationTitle("Create account")
        .onChange(of: scenePhase) { _, phase in if phase != .active { password = ""; editing = nil; model.cancel() } }
        .onChange(of: session.snapshot.epoch) { _, _ in password = ""; editing = nil; model.cancel() }
        .onDisappear { password = ""; editing = nil; model.cancel() }
    }
}
