@testable import FoundryCatalog
import FoundryUI
import Observation
import SwiftUI
import Testing

@Test func sharedProductShellScenariosCoverIndependentProfilesAdmissionAndBounds() throws {
    var root = URL(fileURLWithPath: #filePath)
    for _ in 0..<6 { root.deleteLastPathComponent() }
    let scenarios = try #require(try JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("contracts/fixtures/product-shells/scenarios.json"))) as? [[String: Any]])
    for scenario in scenarios {
        var state = ProductShellState()
        for step in try #require(scenario["steps"] as? [[String: Any]]) {
            let id = step["id"] as? String
            let action: ProductShellAction
            switch try #require(step["action"] as? String) {
            case "daily": action = .daily(try #require(id))
            case "checkIn": action = .checkIn
            case "workspace": action = .workspace(try #require(id))
            case "workspaceBack": action = .workspaceBack
            case "toggleDone": action = .toggleDone
            case "browse": action = .browse(id)
            case "toggleSaved": action = .toggleSaved
            case "tool": action = .tool(try #require(id))
            case "addLayer": action = .addLayer
            case "export": action = .export
            case "focus": action = .focus(try #require(id))
            case "nextStep": action = .nextStep
            case "previousStep": action = .previousStep
            case "resetSetup": action = .resetSetup
            default: throw CocoaError(.fileReadCorruptFile)
            }
            for _ in 0..<(step["repeat"] as? Int ?? 1) { state.apply(action) }
            let snapshot: [String: Any] = ["dailyPage": state.dailyPage, "checkIns": state.checkIns,
                "workspaceItem": state.workspaceItem, "workspaceDetail": state.workspaceDetail, "completedItems": state.completedItems.sorted(),
                "browseItem": state.browseItem as Any? ?? NSNull(), "savedItems": state.savedItems.sorted(),
                "canvasTool": state.canvasTool, "layers": state.layers, "exports": state.exports,
                "setupFocus": state.setupFocus, "setupStep": state.setupStep, "setupFinished": state.setupFinished]
            for (key, expected) in try #require(step["expect"] as? [String: Any]) {
                let observed = try #require(snapshot[key] as? NSObject)
                #expect(observed.isEqual(expected), "Scenario \(scenario["name"] ?? "?"): \(step) / \(key)")
            }
        }
    }
}
@Test func explicitSetupResetClearsOnlyItsOwnDraft() {
    var state = ProductShellState()
    state.setupName = "Alex"; state.setupFocus = "Build"; state.canvasTitle = "Untouched canvas"; state.browseFilter = "city"
    state.apply(.checkIn); state.apply(.nextStep); state.apply(.nextStep); state.apply(.nextStep)
    state.apply(.resetSetup)
    #expect(state.setupName.isEmpty && state.setupFocus == "Explore" && state.setupStep == 0 && !state.setupFinished)
    #expect(state.canvasTitle == "Untouched canvas" && state.browseFilter == "city" && state.checkIns == ["Today": 1])
}

@Observable @MainActor private final class ProductShellProbe {
    var values = ProductShellState()
    var glass = true
}
@MainActor @Test func productShellsHostNativeTabsAndAdaptWorkspaceWithoutLosingSelection() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene), previous = scene.keyWindow
    let window = UIWindow(windowScene: scene), probe = ProductShellProbe()
    func screen(_ pattern: ProductShellPattern, width: CGFloat = 390, typeSize: DynamicTypeSize = .large) -> AnyView {
        AnyView(FoundryTheme(style: probe.glass ? .glass : .solid) {
            ProductShellViewport(pattern: pattern,
                values: Binding(get: { probe.values }, set: { probe.values = $0 }),
                glass: Binding(get: { probe.glass }, set: { probe.glass = $0 }), onClose: {})
                .frame(width: width, height: 760)
        }.environment(\.dynamicTypeSize, typeSize))
    }
    let host = UIHostingController(rootView: screen(.daily))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func views<T: UIView>(_ type: T.Type, in view: UIView) -> [T] {
        ((view as? T).map { [$0] } ?? []) + view.subviews.flatMap { views(type, in: $0) }
    }
    func settle(_ predicate: () -> Bool) async throws {
        for _ in 0..<100 { host.view.layoutIfNeeded(); if predicate() { return }; try await Task.sleep(for: .milliseconds(20)) }
        Issue.record("Product shell native layout did not settle")
    }
    try await settle { !views(UITabBar.self, in: host.view).isEmpty }
    let bar = try #require(views(UITabBar.self, in: host.view).first)
    #expect(bar.items?.compactMap(\.title) == ProductShellState.dailyPages)
    probe.values.apply(.daily("Activity")); probe.values.apply(.checkIn)
    try await settle { bar.selectedItem?.title == "Activity" }
    #expect(probe.values.checkIns["Activity"] == 1)
    probe.values.apply(.workspace("research")); probe.values.apply(.toggleDone)
    host.rootView = screen(.workspace)
    try await settle { views(UIScrollView.self, in: host.view).count == 1 }
    host.rootView = screen(.workspace, width: 900)
    try await settle { views(UIScrollView.self, in: host.view).count == 2 }
    host.rootView = screen(.workspace, width: 900, typeSize: .accessibility3)
    try await settle { views(UIScrollView.self, in: host.view).count == 1 }
    #expect(probe.values.workspaceItem == "research" && probe.values.completedItems == ["research"])
    // Capture the real composed previews for inspection, rather than recreating their layouts in a test.
    for pattern in ProductShellPattern.allCases {
        host.rootView = screen(pattern)
        host.view.layoutIfNeeded(); try await Task.sleep(for: .milliseconds(100))
        let toggle = try #require(views(UISwitch.self, in: host.view).first)
        let toggleFrame = toggle.convert(toggle.bounds, to: host.view)
        #expect(host.view.bounds.contains(toggleFrame), "Preview chrome must stay visible in \(pattern.rawValue): \(toggleFrame)")
        let png = try #require(UIGraphicsImageRenderer(bounds: host.view.bounds).image { _ in
            host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
        }.pngData())
        #expect(png.count > 1_000)
        let url = URL.temporaryDirectory.appendingPathComponent("product-shell-\(pattern.rawValue).png")
        try png.write(to: url); print("Product shell preview: \(url.path)")
    }
}
