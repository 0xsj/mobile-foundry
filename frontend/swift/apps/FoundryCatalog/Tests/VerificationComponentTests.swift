@testable import FoundryCatalog
import FoundryUI
import Observation
import SwiftUI
import Testing

@Test func verificationAdmissionScopesResultsToAttemptAndChallenge() throws {
    var values = VerificationValues(); let initial = values
    values.begin(); values.resend(); values.advance(0); values.advance(-1); values.edit("1234567"); values.edit("12a456")
    #expect(values == initial)
    values.edit("12-34"); values.begin(); #expect(values.draft == "1234" && values.attempts == 0)
    values.edit("000000"); values.begin(); let first = try #require(values.request); let pending = values
    values.begin(); values.edit("123456"); values.resend(); values.chooseChannel(.sms); values.chooseResponse(.unavailable)
    #expect(values == pending && values.attempts == 1)
    values.finish(first, reply: .incorrect); #expect(values.issue == .incorrect && values.draft == "000000")
    values.edit("123456"); #expect(values.error == nil)
    values.begin(); let canceled = try #require(values.request); values.cancel(); values.begin()
    let newer = try #require(values.request); #expect(newer.id != canceled.id)
    values.finish(canceled, reply: .accepted); #expect(!values.verified && values.request == newer)
    values.finish(newer, reply: .unavailable); #expect(values.issue == .unavailable && values.draft == "123456")
    values.begin(); let disabledAttempt = try #require(values.request); values.setEnabled(false); let disabled = values
    values.edit(""); values.begin(); values.finish(disabledAttempt, reply: .accepted); values.resend(); values.advance(); values.chooseChannel(.sms); values.chooseResponse(.unavailable); values.reset(); values.cancel()
    #expect(values == disabled && values.request == nil)
    values.setEnabled(true); values.advance(); #expect(values.cooldown == 0 && values.expiresIn == 90)
    values.resend(); #expect(values.generation == 2 && values.resends == 1 && values.draft.isEmpty && values.error == nil)
    values.edit("123456"); values.begin(); let expiring = try #require(values.request); values.advance(Int.max)
    #expect(values.expiresIn == 0 && values.cooldown == 0 && values.request == nil && values.draft.isEmpty && values.issue == .expired)
    values.finish(expiring, reply: .accepted); #expect(!values.verified)
    values.resend(); values.chooseChannel(.sms); #expect(values.channel == .sms && values.generation == 4)
    values.edit("123456"); values.begin(); let accepted = try #require(values.request); values.finishPreview()
    #expect(values.verified && values.draft.isEmpty && values.error == nil && !values.canAdvance)
    let done = values; values.finish(accepted, reply: .incorrect); values.begin(); values.resend(); values.edit("111111"); values.advance()
    #expect(values == done)
    values.chooseChannel(.email); values.begin(); #expect(!values.verified && values.draft.isEmpty)
    values.edit("123456"); values.begin(); let old = try #require(values.request); let count = values.attempts
    values.reset(); values.finish(old, reply: .accepted)
    #expect(!values.verified && values.request == nil && values.attempts == count && values.resends == 2)
}

@Observable @MainActor private final class VerificationProbeValues {
    var textSize: DynamicTypeSize = .large
    var direction: LayoutDirection = .leftToRight
    var frames: [String: CGRect] = [:]
}
private struct VerificationFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue(), uniquingKeysWith: { _, new in new }) }
}
private extension View {
    func verificationFrame(_ id: String) -> some View {
        background { GeometryReader { proxy in Color.clear.preference(key: VerificationFrames.self, value: [id: proxy.frame(in: .named("verification-probe"))]) } }
    }
}
private struct VerificationProbe: View {
    @Bindable var values: VerificationProbeValues
    @FocusState private var focused: Bool
    var body: some View {
        FoundryTheme {
            ScrollView {
                VerificationCard("Verify the address you use for this workspace", destination: "A supplied destination with long readable copy", accessibilityLabel: "Supplied delivery identity", artwork: {
                    Image(systemName: "envelope").frame(width: 40, height: 40).verificationFrame("mark")
                }, content: {
                    OneTimeCodeField("Verification code", text: .constant("123456"), help: "Enter or paste six digits. The caller owns verification.", focus: $focused).verificationFrame("field")
                }, status: { Text("Code expires in 120 seconds").verificationFrame("status") }, actions: {
                    ActionButton("Verify code") {}.verificationFrame("action")
                }).verificationFrame("card")
            }.frame(width: 240).coordinateSpace(name: "verification-probe")
        }.environment(\.dynamicTypeSize, values.textSize).environment(\.layoutDirection, values.direction)
            .onPreferenceChange(VerificationFrames.self) { values.frames = $0 }
    }
}
@MainActor @Test func nativeCodeHintAndIndependentSlotsFitLargeTextRTLBounds() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene); window.frame = CGRect(x: 0, y: 0, width: 430, height: 3000)
    let values = VerificationProbeValues(); let host = UIHostingController(rootView: VerificationProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func settle(_ predicate: () -> Bool) async throws {
        for _ in 0..<100 { host.view.layoutIfNeeded(); if predicate() { return }; try await Task.sleep(for: .milliseconds(20)) }
        Issue.record("Verification geometry did not settle: \(values.frames)")
    }
    func codeField(_ view: UIView) -> UITextField? {
        if let field = view as? UITextField { return field }
        for child in view.subviews { if let field = codeField(child) { return field } }
        return nil
    }
    try await settle { values.frames.count == 5 && codeField(host.view) != nil }
    let input = try #require(codeField(host.view)); #expect(input.textContentType == .oneTimeCode && input.keyboardType == .numberPad)
    let normalCard = try #require(values.frames["card"]), normalField = try #require(values.frames["field"])
    values.textSize = .accessibility3
    try await settle { (values.frames["card"]?.height ?? 0) > normalCard.height + 80 && (values.frames["field"]?.height ?? 0) > normalField.height + 20 }
    for id in ["card", "field", "status", "action"] { #expect((values.frames[id]?.width ?? 1000) <= 241) }
    let action = try #require(values.frames["action"]); #expect(action.height >= 44 && action.width >= 44 && action.minX >= normalCard.minX - 1 && action.maxX <= normalCard.maxX + 1)
    let mark = try #require(values.frames["mark"]); values.direction = .rightToLeft
    try await settle { (values.frames["mark"]?.minX ?? 0) > mark.minX + 140 }
}
