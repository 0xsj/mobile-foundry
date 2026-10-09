import SwiftUI

/// Readable chart composition. Plot bounds, data, interactive controls and accessible summaries belong to the host.
public struct ChartPanel<Plot: View, Legend: View, Footer: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let subtitle: String?
    private let role: SurfaceRole
    private let plot: Plot
    private let legend: Legend
    private let footer: Footer
    public init(_ title: String, subtitle: String? = nil, role: SurfaceRole = .content,
                @ViewBuilder plot: () -> Plot, @ViewBuilder legend: () -> Legend, @ViewBuilder footer: () -> Footer) {
        self.title = title; self.subtitle = subtitle; self.role = role
        self.plot = plot(); self.legend = legend(); self.footer = footer()
    }
    public var body: some View {
        Card(role) {
            Text(title).font(t.typography.heading).accessibilityAddTraits(.isHeader)
            if let subtitle { Text(subtitle).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color) }
            plot
            WrapLayout { legend }
            footer
        }
    }
}
extension ChartPanel where Legend == EmptyView, Footer == EmptyView {
    public init(_ title: String, subtitle: String? = nil, role: SurfaceRole = .content, @ViewBuilder plot: () -> Plot) {
        self.init(title, subtitle: subtitle, role: role, plot: plot, legend: { EmptyView() }, footer: { EmptyView() })
    }
}
