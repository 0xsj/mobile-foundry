import SwiftUI

/// A padded composition surface. No implicit tap action or navigation.
public struct Card<Content: View>: View {
    @Environment(\.foundry) private var tokens
    private let role: SurfaceRole
    private let inset: CGFloat?
    private let content: Content
    public init(_ role: SurfaceRole = .content, inset: CGFloat? = nil,
                @ViewBuilder content: () -> Content) {
        self.role = role; self.inset = inset; self.content = content()
    }
    public var body: some View {
        Surface(role) {
            VStack(alignment: .leading, spacing: tokens.space.stack) { content }
                .frame(maxWidth: .infinity, alignment: .leading).padding(inset ?? tokens.space.page)
        }
    }
}
