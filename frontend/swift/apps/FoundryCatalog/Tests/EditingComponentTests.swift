@testable import FoundryCatalog
import FoundryUI
import Observation
import SwiftUI
import Testing

@Test func libraryFilteringRemovalAndUndoPreserveIdentityAndSelectionIntent() {
    var values = EditingValues()
    values.toggle("orbit"); values.toggle("field")
    values.search = "Orbit"
    #expect(values.visible.map(\.id) == ["orbit"] && values.hiddenSelected == 1)
    values.selectVisible()
    #expect(values.selected == ["field"])
    values.selectVisible(); values.archive(values.selected)
    values.remove(values.selected)
    #expect(values.visible.isEmpty && values.selected.isEmpty)
    values.toggle("orbit") // A stale row callback cannot select a removed record.
    #expect(values.selected.isEmpty)
    values.undo(); values.undo()
    #expect(values.selected == ["orbit", "field"] && values.archived == ["orbit", "field"])
    values.search = ""
    #expect(values.visible.map(\.id) == LibraryPreviewItem.all.map(\.id))
    values.remove(["orbit"]); values.remove(["field"]); values.undo()
    #expect(values.removed == ["orbit"] && values.selected == ["field"])
}
@Test func tokenAdmissionAndDisabledCommandsKeepDraftAndUndoValues() {
    var values = EditingValues()
    values.tagDraft = " sketch "
    values.addTag(); #expect(values.tags.count == 2 && values.tagDraft == " sketch ")
    values.tagDraft = " Light "; values.addTag()
    #expect(values.tags.last == "Light" && values.tagDraft.isEmpty)
    values.tagDraft = "Unfinished"; values.removeTag("Review")
    #expect(values.tagDraft == "Unfinished")
    values.toggle("orbit"); values.remove(["orbit"]); values.enabled = false
    values.addTag(); values.removeTag("Sketch"); values.undo(); values.archive(["field"]); values.toggle("field")
    #expect(values.tags.contains("Sketch") && values.tagDraft == "Unfinished")
    #expect(values.removed == ["orbit"] && values.undoIDs == ["orbit"] && values.archived.isEmpty && values.selected.isEmpty)
    values.enabled = true; values.undo()
    #expect(values.selected == ["orbit"])
    for tag in ["A", "B", "C", "D"] { values.tagDraft = tag; values.addTag() }
    values.tagDraft = "Too many"; values.addTag()
    #expect(values.tags.count == 6 && values.tagDraft == "Too many" && !values.canAdd)
}

@Observable @MainActor private final class WrapProbeValues {
    var width: CGFloat = 400
    var direction: LayoutDirection = .leftToRight
    var frames: [Int: CGRect] = [:]
}
private struct WrapProbeFrames: PreferenceKey {
    static let defaultValue: [Int: CGRect] = [:]
    static func reduce(value: inout [Int: CGRect], nextValue: () -> [Int: CGRect]) { value.merge(nextValue(), uniquingKeysWith: { _, new in new }) }
}
private struct WrapProbe: View {
    @Bindable var values: WrapProbeValues
    private let sizes = [CGSize(width: 90, height: 44), CGSize(width: 140, height: 60), CGSize(width: 80, height: 44)]
    var body: some View {
        FoundryTheme {
            WrapLayout(horizontalSpacing: 8, verticalSpacing: 10) {
                ForEach(sizes.indices, id: \.self) { id in
                    Color.blue.frame(width: sizes[id].width, height: sizes[id].height)
                        .background { GeometryReader { geometry in
                            Color.clear.preference(key: WrapProbeFrames.self, value: [id: geometry.frame(in: .named("wrap-probe"))])
                        } }
                }
            }.frame(width: values.width).coordinateSpace(name: "wrap-probe")
        }.environment(\.layoutDirection, values.direction)
            .onPreferenceChange(WrapProbeFrames.self) { values.frames = $0 }
    }
}
@MainActor @Test func intrinsicWrappingReflowsRowsAndMirrorsNativePlacement() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene)
    window.frame = CGRect(x: 0, y: 0, width: 430, height: 1000)
    let values = WrapProbeValues()
    let host = UIHostingController(rootView: WrapProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func settle(_ predicate: () -> Bool) async throws {
        for _ in 0..<100 {
            host.view.layoutIfNeeded(); if predicate() { return }
            try await Task.sleep(for: .milliseconds(20))
        }
        Issue.record("Native wrap did not settle: \(values.frames)")
    }
    try await settle { values.frames.count == 3 }
    #expect(values.frames[0]?.minY == values.frames[2]?.minY)
    values.width = 220
    try await settle { (values.frames[1]?.minY ?? 0) > (values.frames[0]?.maxY ?? 0) }
    let frames = values.frames
    #expect(abs((frames[1]?.minY ?? 0) - (frames[0]?.maxY ?? 0) - 10) < 1)
    #expect(abs((frames[2]?.minY ?? 0) - (frames[1]?.maxY ?? 0) - 10) < 1)
    values.direction = .rightToLeft
    try await settle { (values.frames[0]?.minX ?? 0) > 100 }
    #expect(abs((values.frames[0]?.maxX ?? 0) - 220) < 1)
    #expect(abs((values.frames[1]?.maxX ?? 0) - 220) < 1)
}
