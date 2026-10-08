import FoundryUI
import Observation
import SwiftUI
import Testing

@Observable @MainActor private final class ShellProbeValues {
    var scrollToBottom = false
    var typeSize: DynamicTypeSize = .large
    var frames: [String: CGRect] = [:]
}
private struct ShellFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) {
        value.merge(nextValue(), uniquingKeysWith: { _, new in new })
    }
}
private struct ShellFrameProbe: ViewModifier {
    let name: String
    func body(content: Content) -> some View {
        content.background {
            GeometryReader { geometry in
                Color.clear.preference(key: ShellFrames.self, value: [name: geometry.frame(in: .named("shell"))])
            }
        }
    }
}
private struct ShellProbe: View {
    let values: ShellProbeValues
    var body: some View {
        FoundryTheme {
            DetailShell {
                Text("Detail heading").frame(height: 70).modifier(ShellFrameProbe(name: "header"))
            } content: {
                ScrollViewReader { proxy in
                    ScrollView {
                        VStack(spacing: 16) {
                            ForEach(0..<20) { index in
                                KeyValueRow("Record \(index)", value: "A long readable value for this item")
                                    .id(index).modifier(ShellFrameProbe(name: "row \(index)"))
                            }
                        }
                    }.onChange(of: values.scrollToBottom) { _, bottom in
                        if bottom { proxy.scrollTo(19, anchor: .bottom) }
                    }
                }.modifier(ShellFrameProbe(name: "body"))
            } actions: {
                ActionBar(summary: "Review before continuing") { ActionButton("Continue") {} }
                    .modifier(ShellFrameProbe(name: "footer"))
            }.frame(width: 300, height: 500).modifier(ShellFrameProbe(name: "viewport"))
        }.environment(\.dynamicTypeSize, values.typeSize)
            .coordinateSpace(name: "shell").onPreferenceChange(ShellFrames.self) { values.frames = $0 }
    }
}

@MainActor @Test func nativeDetailShellReservesActionsWhileBodyScrollsAndTextGrows() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene)
    window.frame = CGRect(x: 0, y: 0, width: 390, height: 800)
    let values = ShellProbeValues()
    let host = UIHostingController(rootView: ShellProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func settle(_ stage: String, _ condition: () -> Bool) async throws {
        for _ in 0..<100 {
            host.view.layoutIfNeeded()
            if condition() { return }
            try await Task.sleep(for: .milliseconds(20))
        }
        Issue.record("Detail shell did not settle at \(stage): \(values.frames)")
    }
    try await settle("initial") { values.frames.count == 24 }
    let footer = try #require(values.frames["footer"])
    let row = try #require(values.frames["row 0"])
    values.scrollToBottom = true
    try await settle("scroll") {
        guard let last = values.frames["row 19"], let body = values.frames["body"] else { return false }
        return abs(last.maxY - body.maxY) < 1
    }
    #expect(abs(try #require(values.frames["footer"]).minY - footer.minY) < 1)
    #expect(try #require(values.frames["header"]).maxY <= (values.frames["body"]?.minY ?? 0) + 1)
    #expect(try #require(values.frames["body"]).maxY <= (values.frames["footer"]?.minY ?? 0) + 1)
    values.typeSize = .accessibility3
    try await settle("large text") { (values.frames["row 0"]?.height ?? 0) > row.height }
    #expect(try #require(values.frames["body"]).height > 0)
    #expect(try #require(values.frames["body"]).maxY <= (values.frames["footer"]?.minY ?? 0) + 1)
    #expect(try #require(values.frames["footer"]).maxY <= (values.frames["viewport"]?.maxY ?? 0) + 1)
}
