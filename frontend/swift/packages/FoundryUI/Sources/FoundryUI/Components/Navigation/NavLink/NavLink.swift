import SwiftUI

/// A native navigation link. The hosting NavigationStack and destination remain caller-owned.
public struct NavLink<Destination: View, Leading: View>: View {
    private let title: String
    private let subtitle: String?
    private let destination: Destination
    private let leading: Leading

    public init(_ title: String, subtitle: String? = nil,
                @ViewBuilder destination: () -> Destination, @ViewBuilder leading: () -> Leading) {
        self.title = title; self.subtitle = subtitle
        self.destination = destination(); self.leading = leading()
    }

    public var body: some View {
        NavigationLink {
            destination
        } label: {
            ListRow(title, subtitle: subtitle, leading: { leading }, trailing: {
                Image(systemName: "chevron.forward").accessibilityHidden(true)
            })
            .contentShape(Rectangle())
        }.buttonStyle(.plain)
    }
}

extension NavLink where Leading == EmptyView {
    public init(_ title: String, subtitle: String? = nil, @ViewBuilder destination: () -> Destination) {
        self.init(title, subtitle: subtitle, destination: destination, leading: { EmptyView() })
    }
}
