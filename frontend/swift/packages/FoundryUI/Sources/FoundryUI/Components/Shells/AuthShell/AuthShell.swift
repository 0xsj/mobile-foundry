import SwiftUI

/// A readable, scrolling account-form layout. This shell does not implement authentication.
public struct AuthShell<Header: View, Content: View, Footer: View>: View {
    @Environment(\.foundry) private var t
    private let maximumWidth: CGFloat
    private let header: Header
    private let content: Content
    private let footer: Footer
    public init(maximumWidth: CGFloat = 480, @ViewBuilder header: () -> Header,
                @ViewBuilder content: () -> Content, @ViewBuilder footer: () -> Footer) {
        precondition(maximumWidth.isFinite && maximumWidth > 0)
        self.maximumWidth = maximumWidth; self.header = header(); self.content = content(); self.footer = footer()
    }
    public var body: some View {
        ScrollView {
            ContentContainer(maximumWidth: maximumWidth) {
                VStack(alignment: .leading, spacing: t.space.section) { header; content; footer }
            }
        }
    }
}
