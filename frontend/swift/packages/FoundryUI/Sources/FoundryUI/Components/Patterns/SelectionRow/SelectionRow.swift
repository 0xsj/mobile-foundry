import SwiftUI

/// One whole-row choice, distinct from opening an item. Leading artwork must be passive.
public struct SelectionRow<Leading: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let subtitle: String?
    private let selected: Bool
    private let stateDescription: String
    private let enabled: Bool
    private let onToggle: () -> Void
    private let leading: Leading
    public init(_ title: String, subtitle: String? = nil, selected: Bool, stateDescription: String,
                enabled: Bool = true, onToggle: @escaping () -> Void, @ViewBuilder leading: () -> Leading) {
        self.title = title; self.subtitle = subtitle; self.selected = selected; self.stateDescription = stateDescription
        self.enabled = enabled; self.onToggle = onToggle; self.leading = leading()
    }
    public var body: some View {
        Button(action: onToggle) {
            ListRow(title, subtitle: subtitle, leading: {
                HStack(spacing: t.space.inline) {
                    Image(systemName: selected ? "checkmark.circle.fill" : "circle").font(.title3)
                        .foregroundStyle(t.colors.accent.color)
                    leading
                }.accessibilityHidden(true)
            }, trailing: { EmptyView() }).contentShape(Rectangle())
        }.buttonStyle(.plain).foregroundStyle(t.colors.ink.color).disabled(!enabled).opacity(enabled ? 1 : 0.5)
            .accessibilityElement(children: .combine).accessibilityValue(stateDescription)
            .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
extension SelectionRow where Leading == EmptyView {
    public init(_ title: String, subtitle: String? = nil, selected: Bool, stateDescription: String,
                enabled: Bool = true, onToggle: @escaping () -> Void) {
        self.init(title, subtitle: subtitle, selected: selected, stateDescription: stateDescription,
                  enabled: enabled, onToggle: onToggle, leading: { EmptyView() })
    }
}
