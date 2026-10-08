import SwiftUI

/// Anchored contextual content with explicit and native outside dismissal.
/// The caller owns presentation and supplies scrolling for long content.
public struct PopoverPanel<Anchor: View, Content: View>: View {
    @Environment(\.foundry) private var t
    @Binding private var isPresented: Bool
    private let title: String
    private let closeLabel: String
    private let anchor: Anchor
    private let content: Content

    public init(_ title: String, isPresented: Binding<Bool>, closeLabel: String,
                @ViewBuilder anchor: () -> Anchor, @ViewBuilder content: () -> Content) {
        self.title = title; self._isPresented = isPresented; self.closeLabel = closeLabel
        self.anchor = anchor(); self.content = content()
    }

    public var body: some View {
        anchor.popover(isPresented: $isPresented) {
            VStack(alignment: .leading, spacing: t.space.stack) {
                Text(title).font(t.typography.heading).accessibilityAddTraits(.isHeader)
                content
                ActionButton(closeLabel, variant: .quiet) { isPresented = false }
            }
            .padding(t.space.page)
            .frame(idealWidth: 280, maxWidth: 320, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
            .font(t.typography.body).foregroundStyle(t.colors.ink.color)
            .tint(t.colors.accent.color)
            .environment(\.foundry, t)
            .environment(\.colorScheme, t.appearance == .dark ? .dark : .light)
            .presentationCompactAdaptation(.popover)
        }
    }
}
