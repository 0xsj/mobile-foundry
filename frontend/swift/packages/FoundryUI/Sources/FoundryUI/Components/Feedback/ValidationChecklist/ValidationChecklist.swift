import SwiftUI

public struct ValidationItem: Identifiable, Sendable {
    public let id: String
    public let title: String
    public let satisfied: Bool
    public let stateDescription: String
    public init(id: String, title: String, satisfied: Bool, stateDescription: String) {
        self.id = id; self.title = title; self.satisfied = satisfied; self.stateDescription = stateDescription
    }
}

/// Passive supplied requirements. No validation rules or checkbox actions live here.
public struct ValidationChecklist: View {
    @Environment(\.foundry) private var t
    private let items: [ValidationItem]
    public init(_ items: [ValidationItem]) {
        precondition(Set(items.map(\.id)).count == items.count)
        self.items = items
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.stack) {
            ForEach(items) { item in
                HStack(alignment: .firstTextBaseline, spacing: t.space.inline) {
                    Image(systemName: item.satisfied ? "checkmark.circle" : "circle")
                        .foregroundStyle(item.satisfied ? t.colors.accent.color : t.colors.inkSecondary.color)
                        .accessibilityHidden(true)
                    Text(item.title)
                }.accessibilityElement(children: .ignore)
                    .accessibilityLabel(item.title).accessibilityValue(item.stateDescription)
            }
        }
    }
}
