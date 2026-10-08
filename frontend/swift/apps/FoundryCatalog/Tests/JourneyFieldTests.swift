import FoundryUI
import Observation
import SwiftUI
import Testing

@Observable @MainActor private final class JourneyFieldProbeValues {
    var password = "preview-only"
    var note = "One line"
    var height: CGFloat = 0
    var typeSize: DynamicTypeSize = .large
}
private struct JourneyFieldHeight: PreferenceKey {
    static let defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = max(value, nextValue()) }
}
private struct JourneyFieldProbe: View {
    @Bindable var values: JourneyFieldProbeValues
    @FocusState private var passwordFocused: Bool
    @FocusState private var noteFocused: Bool
    var body: some View {
        FoundryTheme {
            VStack {
                PasswordField("Secret", text: $values.password, focus: $passwordFocused)
                MultilineField("Note", text: $values.note, lines: 2...4, focus: $noteFocused)
                    .background { GeometryReader { geometry in
                        Color.clear.preference(key: JourneyFieldHeight.self, value: geometry.size.height)
                    } }
            }.frame(width: 300)
        }.environment(\.dynamicTypeSize, values.typeSize)
            .onPreferenceChange(JourneyFieldHeight.self) { values.height = $0 }
    }
}

@MainActor @Test func nativePasswordIsSecureAndMultilineViewportGrowsWithoutTruncatingDraft() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene)
    window.frame = CGRect(x: 0, y: 0, width: 390, height: 800)
    let values = JourneyFieldProbeValues()
    let host = UIHostingController(rootView: JourneyFieldProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func settle(_ stage: String, _ condition: () -> Bool) async throws {
        for _ in 0..<100 {
            host.view.layoutIfNeeded()
            if condition() { return }
            try await Task.sleep(for: .milliseconds(20))
        }
        Issue.record("Native input layout did not settle at \(stage), height \(values.height)")
    }
    func containsSecureInput(_ view: UIView) -> Bool {
        if let input = view as? UITextField, input.isSecureTextEntry { return true }
        return view.subviews.contains(where: containsSecureInput)
    }
    try await settle("initial secure input") { values.height > 0 && containsSecureInput(host.view) }
    #expect(containsSecureInput(host.view))
    let shortHeight = values.height
    values.note = (1...4).map { "Line \($0)" }.joined(separator: "\n")
    try await settle("four lines after \(shortHeight)") { values.height > shortHeight + 1 }
    let fullHeight = values.height
    let longNote = (1...30).map { "Line \($0)" }.joined(separator: "\n")
    values.note = longNote
    // Allow the native field to process the longer binding before checking its capped viewport.
    for _ in 0..<10 { host.view.layoutIfNeeded(); try await Task.sleep(for: .milliseconds(20)) }
    #expect(abs(values.height - fullHeight) < 2)
    #expect(values.note == longNote)
    values.typeSize = .accessibility3
    try await settle("large text after \(fullHeight)") { values.height > fullHeight + 1 }
    #expect(values.note == longNote)
}
