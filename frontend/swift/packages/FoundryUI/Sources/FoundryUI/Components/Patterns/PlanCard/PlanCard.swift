import SwiftUI

/// Structured plan copy with one explicit native choice action. Price, status and feature slots stay native and independent.
public struct PlanCard<Price: View, Status: View, Features: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let subtitle: String?
    private let selected: Bool
    private let enabled: Bool
    private let actionLabel: String
    private let onSelect: () -> Void
    private let price: Price
    private let status: Status
    private let features: Features
    public init(_ title: String, subtitle: String? = nil, selected: Bool, actionLabel: String, enabled: Bool = true,
                onSelect: @escaping () -> Void, @ViewBuilder price: () -> Price,
                @ViewBuilder status: () -> Status, @ViewBuilder features: () -> Features) {
        self.title = title; self.subtitle = subtitle; self.selected = selected; self.actionLabel = actionLabel; self.enabled = enabled
        self.onSelect = onSelect; self.price = price(); self.status = status(); self.features = features()
    }
    public var body: some View {
        Card {
            SectionHeader(title, subtitle: subtitle)
            status
            price
            Divider()
            features
            ActionButton(actionLabel, variant: selected ? .secondary : .primary, enabled: enabled && !selected) {
                if enabled && !selected { onSelect() }
            }.accessibilityAddTraits(selected ? .isSelected : [])
        }.overlay(RoundedRectangle(cornerRadius: t.shape.panel)
            .stroke(selected ? t.colors.accent.color : t.colors.lineStrong.color, lineWidth: selected ? 2 : 1))
    }
}
