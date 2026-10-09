@testable import FoundryCatalog
import FoundryUI
import Observation
import SwiftUI
import Testing

@Test func workspaceIdentitySurvivesHiddenCollectionsAndCompactBack() {
    var values = WorkspaceValues()
    values.open("orbit"); values.setStarred(true, id: "orbit")
    #expect(values.breadcrumbs.last?.id == "project:orbit")
    values.detailPresented = false
    #expect(values.selected == "orbit" && values.starred.contains("orbit"))
    #expect(values.breadcrumbs.last?.id == "collection")
    values.navigate("archived")
    #expect(values.selected == "orbit" && values.project == nil && !values.detailPresented)
    values.open("orbit"); values.navigate("shared")
    #expect(values.destination == .archived && values.selected == "orbit")
    values.navigate("starred")
    #expect(values.project?.id == "orbit")
    values.open("orbit"); values.setStarred(false, id: "orbit")
    #expect(values.project == nil && values.selected == "orbit" && values.detailPresented)
    values.breadcrumb("workspace")
    #expect(values.destination == .all && values.project?.id == "orbit" && !values.detailPresented)
}
@Observable @MainActor private final class WorkspaceProbeValues {
    var width: CGFloat = 820
    var textSize: DynamicTypeSize = .large
    var direction: LayoutDirection = .leftToRight
    var detailPresented = false
    var frames: [String: CGRect] = [:]
}
private struct WorkspaceFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue(), uniquingKeysWith: { _, new in new }) }
}
private extension View {
    func workspaceFrame(_ id: String) -> some View {
        background { GeometryReader { proxy in Color.clear.preference(key: WorkspaceFrames.self, value: [id: proxy.frame(in: .named("workspace-probe"))]) } }
    }
}
private struct WorkspaceProbe: View {
    @Bindable var values: WorkspaceProbeValues
    var body: some View {
        FoundryTheme {
            SplitPane(detailPresented: values.detailPresented, primary: { mode in
                Text("Projects").frame(maxWidth: .infinity, maxHeight: .infinity).workspaceFrame("primary")
            }, detail: { mode in
                Text("Project detail").frame(maxWidth: .infinity, maxHeight: .infinity).workspaceFrame("detail")
            }).frame(width: values.width, height: 420).workspaceFrame("viewport")
                .coordinateSpace(name: "workspace-probe")
        }.environment(\.dynamicTypeSize, values.textSize).environment(\.layoutDirection, values.direction)
            .onPreferenceChange(WorkspaceFrames.self) { values.frames = $0 }
    }
}
@MainActor @Test func panesRespondToLocalWidthTextSizeAndLogicalDirection() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene); window.frame = CGRect(x: 0, y: 0, width: 1000, height: 800)
    let values = WorkspaceProbeValues()
    let host = UIHostingController(rootView: WorkspaceProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func settle(_ predicate: () -> Bool) async throws {
        for _ in 0..<100 { host.view.layoutIfNeeded(); if predicate() { return }; try await Task.sleep(for: .milliseconds(20)) }
        Issue.record("Workspace geometry did not settle: \(values.frames)")
    }
    try await settle { values.frames.count == 3 }
    let primary = try #require(values.frames["primary"]), detail = try #require(values.frames["detail"])
    #expect(abs(primary.width - 320) < 1 && detail.width >= 320 && primary.maxX <= detail.minX)
    values.direction = .rightToLeft
    try await settle { (values.frames["primary"]?.minX ?? 0) > (values.frames["detail"]?.maxX ?? 1000) }
    values.width = 280
    try await settle { values.frames["detail"] == nil && abs((values.frames["primary"]?.width ?? 0) - 280) < 1 }
    values.detailPresented = true
    try await settle { values.frames["primary"] == nil && values.frames["detail"] != nil }
    values.width = 820; values.textSize = .accessibility3
    try await settle { values.frames["primary"] == nil && abs((values.frames["detail"]?.width ?? 0) - 820) < 1 }
}
