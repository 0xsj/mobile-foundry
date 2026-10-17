import FoundrySession
import SwiftUI

struct IdentitySettingsView: View {
    let model: IdentitySettingsModel
    let session: SessionController
    @Environment(\.scenePhase) private var scenePhase
    @State private var email = ""
    @State private var token = ""
    @State private var verificationPassword = ""
    @State private var currentPassword = ""
    @State private var newPassword = ""
    private enum Mode: Hashable { case verification, password }
    @State private var mode = Mode.verification
    private enum Field: Hashable { case email, verificationPassword, code, currentPassword, newPassword }
    @FocusState private var editing: Field?
    private var signedIn: Bool { session.lease != nil }
    var body: some View {
        Form {
            Section {
                if model.isBusy { ProgressView("Updating account security…") }
                if let message = model.message { Text(message).accessibilityIdentifier("identity.message") }
                if let failure = model.failure { Text(failure.meta.message).foregroundStyle(.red).accessibilityIdentifier("identity.failure") }
            }
            if signedIn {
                Section {
                    Picker("Security action", selection: $mode) {
                        Text("Verify email").tag(Mode.verification)
                        Text("Change password").tag(Mode.password)
                    }.pickerStyle(.segmented).accessibilityIdentifier("identity.mode")
                }
                if mode == .verification {
                Section("Recovery email") {
                    Text(model.contact?.email ?? "No verified address loaded.")
                    Button("Refresh verified address") { model.loadContact() }
                    TextField("Email address", text: $email).textContentType(.emailAddress).keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never).autocorrectionDisabled().focused($editing, equals: .email).accessibilityIdentifier("identity.email")
                    SecureField("Current password", text: $verificationPassword).textContentType(.password).focused($editing, equals: .verificationPassword).accessibilityIdentifier("identity.current-password")
                    Button("Send verification code") { submit { model.requestVerification(email: email, password: verificationPassword) } }
                        .accessibilityIdentifier("identity.request-verification")
                    codeField("Verification code")
                    Button("Verify email") { submit { model.verify(token: token) } }.accessibilityIdentifier("identity.verify")
                    Text("Your first verified address becomes your recovery contact. Contact changes are a separate workflow.").font(.caption)
                }
                } else {
                Section("Change password") {
                    SecureField("Current password", text: $currentPassword).textContentType(.password).focused($editing, equals: .currentPassword).accessibilityIdentifier("identity.change-current-password")
                    SecureField("New password", text: $newPassword).textContentType(.newPassword).focused($editing, equals: .newPassword).accessibilityIdentifier("identity.new-password")
                    Button("Change password and sign out") { submit { model.change(currentPassword: currentPassword, newPassword: newPassword) } }
                        .accessibilityIdentifier("identity.change")
                    Text("Use at least 12 characters. All sessions are revoked. If the response is interrupted, sign in again or use recovery.").font(.caption)
                }
                }
            } else {
                Section("Recover access") {
                    TextField("Recovery email", text: $email).textContentType(.emailAddress).keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never).autocorrectionDisabled().focused($editing, equals: .email).accessibilityIdentifier("identity.email")
                    Button("Request reset code") { submit { model.requestReset(email: email) } }.accessibilityIdentifier("identity.request-reset")
                    codeField("Reset code")
                    SecureField("New password", text: $newPassword).textContentType(.newPassword).focused($editing, equals: .newPassword).accessibilityIdentifier("identity.new-password")
                    Button("Reset password") { submit { model.reset(token: token, password: newPassword) } }.accessibilityIdentifier("identity.reset")
                    Text("Use the code from your email and at least 12 password characters. Requesting a code does not confirm that an account exists.").font(.caption)
                }
            }
        }
        .disabled(model.isBusy || session.isBusy)
        .navigationTitle("Account security")
        .onChange(of: session.snapshot.epoch) { _, _ in clear() }
        .onChange(of: mode) { _, _ in editing = nil; clear() }
        .onChange(of: scenePhase) { _, phase in if phase != .active { clear() } }
        .onDisappear { clear(); model.cancel() }
    }
    private func codeField(_ label: String) -> some View {
        TextField(label, text: $token).textContentType(.oneTimeCode).privacySensitive()
            .textInputAutocapitalization(.never).autocorrectionDisabled()
            .focused($editing, equals: .code).accessibilityIdentifier("identity.code")
    }
    private func submit(_ action: () -> Void) { editing = nil; action(); clear() }
    private func clear() { editing = nil; verificationPassword = ""; currentPassword = ""; newPassword = ""; token = "" }
}
