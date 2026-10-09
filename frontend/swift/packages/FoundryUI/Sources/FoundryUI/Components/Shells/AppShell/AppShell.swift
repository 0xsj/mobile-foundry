import SwiftUI

/// Bounded passive background, flexible content and independent bottom navigation slot.
/// The host owns routes, scrolling, safe areas, keyboard policy and feature/resource lifetime.
public struct AppShell<Background: View, Content: View, Navigation: View>: View {
    private let background: Background
    private let content: Content
    private let navigation: Navigation
    public init(@ViewBuilder background: () -> Background, @ViewBuilder content: () -> Content,
                @ViewBuilder navigation: () -> Navigation) {
        self.background = background(); self.content = content(); self.navigation = navigation()
    }
    public init(@ViewBuilder background: () -> Background, @ViewBuilder content: () -> Content) where Navigation == EmptyView {
        self.init(background: background, content: content, navigation: { EmptyView() })
    }
    public var body: some View {
        ZStack {
            background.frame(maxWidth: .infinity, maxHeight: .infinity).accessibilityHidden(true)
            VStack(spacing: 0) {
                content.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                navigation.fixedSize(horizontal: false, vertical: true)
            }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
    }
}
