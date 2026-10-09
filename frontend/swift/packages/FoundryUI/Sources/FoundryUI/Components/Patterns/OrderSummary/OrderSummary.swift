import SwiftUI

/// Caller-formatted order composition. Lines, totals, disclosures and admission stay in the feature.
public struct OrderSummary<Lines: View, Footer: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let subtitle: String?
    private let totalTitle: String
    private let totalValue: String
    private let totalDetail: String?
    private let lines: Lines
    private let footer: Footer
    public init(_ title: String, subtitle: String? = nil, totalTitle: String, totalValue: String, totalDetail: String? = nil,
                @ViewBuilder lines: () -> Lines, @ViewBuilder footer: () -> Footer) {
        self.title = title; self.subtitle = subtitle; self.totalTitle = totalTitle; self.totalValue = totalValue
        self.totalDetail = totalDetail; self.lines = lines(); self.footer = footer()
    }
    public var body: some View {
        Card {
            SectionHeader(title, subtitle: subtitle)
            lines
            Divider().overlay(t.colors.line.color).accessibilityHidden(true)
            KeyValueRow(totalTitle, value: totalValue, detail: totalDetail)
            footer
        }
    }
}
