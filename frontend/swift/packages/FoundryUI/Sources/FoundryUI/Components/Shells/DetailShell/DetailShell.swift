import SwiftUI

/// A bounded screen with fixed header/actions and a flexible body slot.
/// The caller supplies body scrolling, safe areas, navigation and keyboard policy.
public struct DetailShell<Header: View, Content: View, Actions: View>: View {
    private let header: Header
    private let content: Content
    private let actions: Actions
    public init(@ViewBuilder header: () -> Header, @ViewBuilder content: () -> Content,
                @ViewBuilder actions: () -> Actions) {
        self.header = header(); self.content = content(); self.actions = actions()
    }
    public var body: some View {
        VStack(spacing: 0) {
            header.fixedSize(horizontal: false, vertical: true)
            content.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            actions.fixedSize(horizontal: false, vertical: true)
        }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}
