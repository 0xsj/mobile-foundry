@testable import FoundryCatalog

import FoundryUI
import Observation
import SwiftUI
import Testing

@MainActor @Test func activityPagesRetainRowsOnFailureAndFinishOnlyAfterExplicitRequests() async {
    let values = ActivityPreviewValues(pause: {})
    values.expanded.insert(1); values.failNextPage = true
    values.requestPage(); values.requestPage()
    #expect(values.pageRequest == 1)
    await values.completePage()
    #expect(values.phase == .failed && values.records.map(\.id) == [1, 2, 3])
    #expect(values.expanded == [1] && !values.failNextPage)
    values.requestPage(); await values.completePage()
    #expect(values.records.map(\.id) == Array(1...6))
    values.requestPage(); await values.completePage()
    #expect(values.phase == .exhausted && values.records.count == 9)
    values.requestPage(); #expect(values.pageRequest == 3)
    values.expanded.insert(7)
    await values.refresh()
    #expect(values.records.count == 3 && values.refreshes == 1 && values.phase == .idle)
    #expect(values.expanded == [1])
}

@MainActor @Test func activityCancelledRefreshAndPageCannotApplyLateResults() async {
    let values = ActivityPreviewValues(pause: { try await Task.sleep(for: .seconds(10)) })
    let refresh = Task { await values.refresh() }
    while !values.refreshing { await Task.yield() }
    values.requestPage(); #expect(values.pageRequest == 0)
    refresh.cancel(); await refresh.value
    #expect(!values.refreshing && values.refreshes == 0 && values.records.count == 3)
    values.requestPage()
    let page = Task { await values.completePage() }
    page.cancel(); await page.value
    #expect(values.phase == .idle && values.records.count == 3)
}

@MainActor @Test func activityLeavingDestinationInvalidatesAnUncancelledCompletion() async {
    let gate = ActivityPauseGate()
    let values = ActivityPreviewValues(pause: { await gate.wait() })
    values.requestPage()
    let page = Task { await values.completePage() }
    while !(await gate.waiting) { await Task.yield() }
    values.cancelPending()
    await gate.release(); await page.value
    #expect(values.records.count == 3 && values.phase == .idle)
}
private actor ActivityPauseGate {
    private var continuation: CheckedContinuation<Void, Never>?
    var waiting: Bool { continuation != nil }
    func wait() async { await withCheckedContinuation { continuation = $0 } }
    func release() { continuation?.resume(); continuation = nil }
}

@Observable @MainActor private final class ActivityTextValues {
    var expanded = false
    var height: CGFloat = 0
    var size: DynamicTypeSize = .large
}
private struct ActivityTextHeight: PreferenceKey {
    static let defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = max(value, nextValue()) }
}
private struct ActivityTextProbe: View {
    @Bindable var values: ActivityTextValues
    var body: some View {
        FoundryTheme {
            ExpandableText(ActivityRecord(id: 1).note, expanded: $values.expanded, moreLabel: "Read update", lessLabel: "Collapse update")
                .frame(width: 280)
                .background { GeometryReader { Color.clear.preference(key: ActivityTextHeight.self, value: $0.size.height) } }
        }.environment(\.dynamicTypeSize, values.size)
            .onPreferenceChange(ActivityTextHeight.self) { values.height = $0 }
    }
}
@MainActor @Test func activityDisclosureExpandsAndAdaptsToAccessibilityText() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene)
    window.frame = CGRect(x: 0, y: 0, width: 390, height: 1000)
    let values = ActivityTextValues()
    let host = UIHostingController(rootView: ActivityTextProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func settle(_ condition: () -> Bool) async throws {
        for _ in 0..<100 {
            host.view.layoutIfNeeded()
            if condition() { return }
            try await Task.sleep(for: .milliseconds(20))
        }
        Issue.record("Activity disclosure layout did not settle")
    }
    try await settle { values.height > 0 }
    let collapsed = values.height
    values.expanded = true
    try await settle { values.height > collapsed + 20 }
    let expanded = values.height
    values.expanded = false
    try await settle { abs(values.height - collapsed) < 2 }
    values.size = .accessibility3
    try await settle { values.height > collapsed + 20 }
    values.expanded = true
    try await settle { values.height > expanded + 20 }
}

@MainActor @Test func nativeRefreshControlAwaitsTheHostsWork() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene)
    window.frame = CGRect(x: 0, y: 0, width: 390, height: 800)
    let gate = ActivityPauseGate()
    let values = ActivityPreviewValues(pause: { await gate.wait() })
    let host = UIHostingController(rootView: FoundryTheme {
        RefreshContainer(onRefresh: { await values.refresh() }) { List { Text("Updates") } }
    })
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func control(in view: UIView) -> UIRefreshControl? {
        if let scroll = view as? UIScrollView, let control = scroll.refreshControl { return control }
        for child in view.subviews { if let control = control(in: child) { return control } }
        return nil
    }
    for _ in 0..<100 {
        host.view.layoutIfNeeded()
        if control(in: host.view) != nil { break }
        try await Task.sleep(for: .milliseconds(20))
    }
    let refresh = try #require(control(in: host.view))
    refresh.beginRefreshing(); refresh.sendActions(for: .valueChanged)
    for _ in 0..<100 {
        if values.refreshing { break }
        try await Task.sleep(for: .milliseconds(20))
    }
    #expect(values.refreshing && values.refreshes == 0)
    #expect(refresh.isRefreshing)
    await gate.release()
    for _ in 0..<100 {
        if !values.refreshing && !refresh.isRefreshing { break }
        try await Task.sleep(for: .milliseconds(20))
    }
    #expect(values.refreshes == 1 && !values.refreshing && !refresh.isRefreshing)
}
