@testable import ProductApp
import Observation
import SwiftUI
import Testing

@Observable @MainActor private final class EntryProbe {
    var state = ProductState(glass: true)
}

@MainActor @Test func nativeProductTabsAndThemeKeepSelectedDestination() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene), previous = scene.keyWindow
    let window = UIWindow(windowScene: scene), probe = EntryProbe()
    let host = UIHostingController(rootView: ProductRootView(configuration: .init(displayName: "Custom Product"),
        state: Binding(get: { probe.state }, set: { probe.state = $0 })))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func views<T: UIView>(_ type: T.Type, in view: UIView) -> [T] {
        ((view as? T).map { [$0] } ?? []) + view.subviews.flatMap { views(type, in: $0) }
    }
    func settle(_ predicate: () -> Bool) async throws {
        for _ in 0..<100 {
            host.view.layoutIfNeeded()
            if predicate() { return }
            try await Task.sleep(for: .milliseconds(20))
        }
        Issue.record("Product entry native layout did not settle")
    }
    try await settle { !views(UITabBar.self, in: host.view).isEmpty }
    let bar = try #require(views(UITabBar.self, in: host.view).first)
    #expect(bar.items?.compactMap(\.title) == ["Today", "Library", "Activity", "Account"])
    for tab in ProductTab.allCases {
        probe.state.select(tab.rawValue)
        try await settle { bar.selectedItem?.title == tab.title }
    }
    try await settle { !views(UISwitch.self, in: host.view).isEmpty }
    let toggle = try #require(views(UISwitch.self, in: host.view).first)
    #expect(toggle.isOn)
    toggle.setOn(false, animated: false); toggle.sendActions(for: .valueChanged)
    try await settle { !probe.state.glass }
    #expect(bar.selectedItem?.title == "Account")
    probe.state.select("unknown"); #expect(probe.state.selectedTab == .account)
    probe.state.select("today")
    try await settle { bar.selectedItem?.title == "Today" }
    #expect(!probe.state.glass)
}

@Test func runtimeProductBrandingComesFromBuiltMetadata() {
    #expect(ProductConfiguration.current.displayName == Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String)
    #expect(Bundle.main.bundleIdentifier != "dev.mobilefoundry.catalog")
    #expect(Bundle.main.object(forInfoDictionaryKey: "NSCameraUsageDescription") == nil)
}
