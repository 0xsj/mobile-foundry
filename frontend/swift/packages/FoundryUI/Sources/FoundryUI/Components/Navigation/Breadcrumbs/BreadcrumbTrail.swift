import SwiftUI

public struct BreadcrumbItem: Identifiable, Sendable {
    public let id: String
    public let label: String
    public let enabled: Bool
    public init(id: String, label: String, enabled: Bool = true) { self.id = id; self.label = label; self.enabled = enabled }
}

/// Supplied ancestors are native actions; the final item is passive current-location copy.
/// The host supplies localized current-location narration and owns all route changes.
public struct BreadcrumbTrail: View {
    @Environment(\.foundry) private var t
    private let items: [BreadcrumbItem]
    private let currentAccessibilityLabel: String
    private let onActivate: (String) -> Void
    public init(items: [BreadcrumbItem], currentAccessibilityLabel: String, onActivate: @escaping (String) -> Void) {
        precondition(Set(items.map(\.id)).count == items.count)
        self.items = items; self.currentAccessibilityLabel = currentAccessibilityLabel; self.onActivate = onActivate
    }
    public var body: some View {
        WrapLayout {
            ForEach(items) { item in
                HStack(spacing: t.space.inline) {
                    if item.id != items.first?.id { Text("/").foregroundStyle(t.colors.inkSecondary.color).accessibilityHidden(true) }
                    if item.id == items.last?.id {
                        Text(item.label).font(t.typography.label).fixedSize(horizontal: false, vertical: true)
                            .frame(minHeight: t.shape.minimumInteractive)
                            .accessibilityLabel(currentAccessibilityLabel)
                    } else {
                        ActionButton(item.label, variant: .quiet, enabled: item.enabled) { onActivate(item.id) }
                    }
                }
            }
        }
    }
}
