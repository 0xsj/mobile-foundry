import SwiftUI

public enum PaneMode: Sendable { case single, split }

/// Requires a bounded viewport, not an unbounded vertical ScrollView.
/// Slots own scrolling and state. Layout changes can recreate slots; keep feature values above this view.
/// Primary stays at the logical leading edge. This does not create a navigation stack or own Back.
public struct SplitPane<Primary: View, Detail: View>: View {
    @Environment(\.foundry) private var t
    @Environment(\.dynamicTypeSize) private var textSize
    private let detailPresented: Bool
    private let forceSingle: Bool
    private let primaryWidth: CGFloat
    private let minimumDetailWidth: CGFloat
    private let primary: (PaneMode) -> Primary
    private let detail: (PaneMode) -> Detail
    public init(detailPresented: Bool, forceSingle: Bool = false, primaryWidth: CGFloat = 320, minimumDetailWidth: CGFloat = 320,
                @ViewBuilder primary: @escaping (PaneMode) -> Primary, @ViewBuilder detail: @escaping (PaneMode) -> Detail) {
        precondition(primaryWidth.isFinite && primaryWidth > 0 && minimumDetailWidth.isFinite && minimumDetailWidth > 0)
        self.detailPresented = detailPresented; self.forceSingle = forceSingle
        self.primaryWidth = primaryWidth; self.minimumDetailWidth = minimumDetailWidth; self.primary = primary; self.detail = detail
    }
    public var body: some View {
        GeometryReader { geometry in
            let split = !forceSingle && !textSize.isAccessibilitySize && geometry.size.width >= primaryWidth + minimumDetailWidth + t.space.inline
            if split {
                HStack(alignment: .top, spacing: t.space.inline) {
                    primary(.split).frame(width: primaryWidth).frame(maxHeight: .infinity, alignment: .topLeading)
                    detail(.split).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                }
            } else if detailPresented {
                detail(.single).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            } else {
                primary(.single).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
    }
}
