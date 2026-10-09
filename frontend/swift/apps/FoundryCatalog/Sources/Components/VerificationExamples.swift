import FoundryUI
import SwiftUI

enum VerificationChannel: String, CaseIterable {
    case email = "Email", sms = "SMS"
    var destination: String { self == .email ? "j••••@example.test" : "+1 ••• ••• 0142" }
    var symbol: String { self == .email ? "envelope" : "message" }
}
enum VerificationResponse: String, CaseIterable { case matchCode = "Match demo code", unavailable = "Service unavailable" }
enum VerificationReply { case accepted, incorrect, unavailable }
enum VerificationIssue: String { case incorrect = "That code did not match. Try again.", unavailable = "The preview service is unavailable. Try again.", expired = "This code has expired. Request a new code." }
struct VerificationAttempt: Equatable {
    let id: Int; let generation: Int; let channel: VerificationChannel; let code: String
}
/// Local challenge values and identity-scoped attempts. No authentication, delivery or automatic clock.
struct VerificationValues: Equatable {
    var channel = VerificationChannel.email
    var draft = ""
    var response = VerificationResponse.matchCode
    var generation = 1
    var cooldown = 30
    var expiresIn = 120
    var enabled = true
    var verified = false
    var request: VerificationAttempt?
    var issue: VerificationIssue?
    var attempts = 0
    var resends = 0
    static let format = CodeFormat()
    var canEdit: Bool { enabled && !verified && request == nil && expiresIn > 0 }
    var canSubmit: Bool { canEdit && Self.format.isComplete(draft) }
    var canResend: Bool { enabled && !verified && request == nil && cooldown == 0 }
    var canChange: Bool { enabled && request == nil }
    var canAdvance: Bool { enabled && !verified && expiresIn > 0 }
    var error: String? { (issue ?? (expiresIn == 0 && !verified ? .expired : nil))?.rawValue }
    mutating func edit(_ input: String) {
        guard canEdit, let value = Self.format.admit(input), value != draft else { return }
        draft = value; issue = nil
    }
    mutating func begin() {
        guard canSubmit else { return }
        attempts += 1; issue = nil
        request = VerificationAttempt(id: attempts, generation: generation, channel: channel, code: draft)
    }
    mutating func finish(_ attempt: VerificationAttempt, reply: VerificationReply) {
        guard enabled && !verified && expiresIn > 0 && request == attempt && attempt.generation == generation && attempt.channel == channel else { return }
        request = nil
        switch reply {
        case .accepted: verified = true; draft = ""; issue = nil
        case .incorrect: issue = .incorrect
        case .unavailable: issue = .unavailable
        }
    }
    mutating func finishPreview() {
        guard let request else { return }
        finish(request, reply: response == .unavailable ? .unavailable : request.code == "123456" ? .accepted : .incorrect)
    }
    mutating func cancel() { guard enabled && request != nil else { return }; request = nil }
    mutating func advance(_ seconds: Int = 30) {
        guard canAdvance && seconds > 0 else { return }
        cooldown -= min(cooldown, seconds); expiresIn -= min(expiresIn, seconds)
        if expiresIn == 0 { request = nil; draft = ""; issue = .expired }
    }
    mutating func resend() {
        guard canResend else { return }
        newChallenge(); resends += 1
    }
    mutating func chooseChannel(_ value: VerificationChannel) {
        guard canChange && value != channel else { return }; channel = value; newChallenge()
    }
    mutating func chooseResponse(_ value: VerificationResponse) { guard canChange else { return }; response = value }
    mutating func setEnabled(_ value: Bool) { enabled = value; if !value { request = nil } }
    mutating func reset() {
        guard enabled else { return }; channel = .email; response = .matchCode; newChallenge()
    }
    private mutating func newChallenge() {
        generation += 1; cooldown = 30; expiresIn = 120; draft = ""; issue = nil; request = nil; verified = false
    }
}
struct VerificationExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: VerificationValues
    var body: some View {
        Card {
            SectionHeader("Verification and recovery", subtitle: "Native code entry with caller-owned challenge state.")
            NavLink("Open verification preview", subtitle: "Try code entry, resend and expiry") {
                VerificationPreview(values: $values, appearance: t.appearance, style: t.materials.style)
            }
        }
        VerificationContent(values: $values)
    }
}
struct VerificationPreview: View {
    @Binding var values: VerificationValues
    let appearance: FoundryAppearance; let style: FoundryThemeStyle
    var body: some View {
        FoundryTheme(appearance: appearance, style: style) { ScrollView { VerificationContent(values: $values).padding(20) } }
            .navigationTitle("Verification preview").navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(FoundryPreset.v1(appearance: appearance).colors.surfaceGround.color, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar).toolbarColorScheme(appearance == .dark ? .dark : .light, for: .navigationBar)
    }
}
struct VerificationContent: View {
    @Environment(\.foundry) private var t
    @FocusState private var codeFocused: Bool
    @Binding var values: VerificationValues
    private func begin() { guard values.canSubmit else { return }; codeFocused = false; values.begin() }
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            Card(.floating) {
                ToggleField("Enable verification actions", isOn: Binding(get: { values.enabled }, set: { values.setEnabled($0) }))
                SelectField("Delivery channel", selection: Binding(get: { values.channel }, set: { values.chooseChannel($0) }), options: VerificationChannel.allCases,
                            enabled: values.canChange, label: { $0.rawValue })
                SelectField("Preview response", selection: Binding(get: { values.response }, set: { values.chooseResponse($0) }), options: VerificationResponse.allCases,
                            enabled: values.canChange, label: { $0.rawValue })
                Text("Demo code: 123456. Time advances manually. No message is sent and no account is verified.").font(t.typography.caption)
                ActionButton("Advance 30 seconds", variant: .secondary, enabled: values.canAdvance) { codeFocused = false; values.advance() }
                ActionButton("Reset verification", variant: .quiet, enabled: values.enabled) { codeFocused = false; values.reset() }
                Text("Checks started: \(values.attempts) · Resend requests: \(values.resends)").font(t.typography.caption)
            }
            VerificationCard("Verify your \(values.channel == .email ? "email" : "phone")", destination: values.channel.destination,
                             accessibilityLabel: "Verification by \(values.channel.rawValue), \(values.channel.destination)", artwork: {
                Image(systemName: values.channel.symbol).font(.title).frame(width: 40, height: 40).foregroundStyle(t.colors.accent.color)
            }, content: {
                if !values.verified {
                    OneTimeCodeField("Verification code", text: Binding(get: { values.draft }, set: { values.edit($0) }),
                                     help: "Enter or paste six digits.", error: values.error, enabled: values.canEdit,
                                     canSubmit: values.canSubmit, focus: $codeFocused, onSubmit: begin)
                }
            }, status: {
                if values.verified { InlineAlert("Preview verified", message: "The local check completed.", tone: .info) }
                else {
                    Text("Code expires in \(values.expiresIn) seconds").font(t.typography.caption)
                    if values.request != nil { ProgressIndicator("Checking preview code") }
                }
            }, actions: {
                if !values.verified {
                    ActionButton("Verify code", variant: .primary, isBusy: values.request != nil, enabled: values.canSubmit, action: begin)
                    if values.request != nil {
                        ActionButton("Finish local check", variant: .secondary, enabled: values.enabled) { values.finishPreview() }
                        ActionButton("Cancel check", variant: .quiet, enabled: values.enabled) { values.cancel() }
                    }
                    ActionButton(values.cooldown == 0 ? "Resend code" : "Resend in \(values.cooldown) seconds", variant: .quiet, enabled: values.canResend) {
                        codeFocused = false; values.resend()
                    }
                }
            })
        }.onChange(of: values.canEdit) { _, enabled in if !enabled { codeFocused = false } }
    }
}
