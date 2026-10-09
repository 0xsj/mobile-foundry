import SwiftUI

public struct RailDestination: Identifiable, Sendable {
    public let id: String
    public let label: String
    public let accessibilityLabel: String
    public let enabled: Bool
    public init(id: String, label: String, accessibilityLabel: String? = nil, enabled: Bool = true) {
        self.id = id; self.label = label; self.accessibilityLabel = accessibilityLabel ?? label; self.enabled = enabled
    }
}

/// A small, supplied destination set. The host chooses its width, route policy and compact alternative.
/// Icons are passive; labels stay visible. Unknown selection does not select a fallback destination.
public struct DestinationRail<Icon: View>: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let destinations: [RailDestination]
    private let selection: String?
    private let onSelect: (String) -> Void
    private let icon: (RailDestination) -> Icon
    public init(_ title: String, destinations: [RailDestination], selection: String?, onSelect: @escaping (String) -> Void,
                @ViewBuilder icon: @escaping (RailDestination) -> Icon) {
        precondition(Set(destinations.map(\.id)).count == destinations.count)
        self.title = title; self.destinations = destinations; self.selection = selection; self.onSelect = onSelect; self.icon = icon
    }
    public var body: some View {
        Surface(.floating) {
            ScrollView {
                VStack(spacing: t.space.inline) {
                    Text(title).font(t.typography.caption).accessibilityAddTraits(.isHeader)
                    ForEach(destinations) { destination in
                        let selected = destination.id == selection
                        Button { onSelect(destination.id) } label: {
                            VStack(spacing: t.space.inline) {
                                icon(destination).accessibilityHidden(true)
                                Text(destination.label).font(t.typography.caption).fixedSize(horizontal: false, vertical: true)
                            }
                            .frame(maxWidth: .infinity, minHeight: t.shape.minimumInteractive)
                            .padding(t.space.inline)
                            .foregroundStyle(selected ? t.colors.accent.color : t.colors.ink.color)
                            .background(selected ? t.colors.accentTint.color : .clear,
                                        in: RoundedRectangle(cornerRadius: t.shape.radii[2]))
                        }.buttonStyle(.plain).disabled(!destination.enabled)
                            .opacity(destination.enabled ? 1 : 0.5)
                            .accessibilityLabel(destination.accessibilityLabel)
                            .accessibilityAddTraits(selected ? .isSelected : [])
                    }
                }.padding(t.space.inline)
            }
        }
    }
}
