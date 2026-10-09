import SwiftUI

public struct AccountOption: Identifiable, Sendable {
    public let id: String
    public let title: String
    public let detail: String?
    public let enabled: Bool
    public init(id: String, title: String, detail: String? = nil, enabled: Bool = true) {
        self.id = id; self.title = title; self.detail = detail; self.enabled = enabled
    }
}

/// Native menu for supplied identities. Unknown selection displays the supplied placeholder without repairing state.
public struct AccountSwitcher: View {
    @Environment(\.foundry) private var t
    private let title: String
    private let options: [AccountOption]
    private let selection: String?
    private let placeholder: String
    private let enabled: Bool
    private let onSelect: (String) -> Void
    public init(_ title: String, options: [AccountOption], selection: String?, placeholder: String,
                enabled: Bool = true, onSelect: @escaping (String) -> Void) {
        precondition(Set(options.map(\.id)).count == options.count)
        self.title = title; self.options = options; self.selection = selection; self.placeholder = placeholder
        self.enabled = enabled; self.onSelect = onSelect
    }
    public var body: some View {
        let current = options.first { $0.id == selection }
        Menu {
            ForEach(options) { option in
                Button {
                    guard enabled && option.enabled && option.id != selection else { return }
                    onSelect(option.id)
                } label: {
                    if option.id == selection {
                        Label(option.title, systemImage: "checkmark")
                    } else { Text(option.title) }
                    if let detail = option.detail { Text(detail) }
                }.disabled(!option.enabled)
                    .accessibilityAddTraits(option.id == selection ? [.isSelected] : [])
            }
        } label: {
            HStack(spacing: t.space.inline) {
                VStack(alignment: .leading, spacing: t.space.steps[1]) {
                    Text(title).font(t.typography.caption)
                    Text(current?.title ?? placeholder).font(t.typography.label).fixedSize(horizontal: false, vertical: true)
                }
                Image(systemName: "chevron.up.chevron.down").accessibilityHidden(true)
            }
        }.buttonStyle(ActionButtonStyle(variant: .secondary))
            .disabled(!enabled || options.isEmpty).accessibilityLabel(title).accessibilityValue(current?.title ?? placeholder)
    }
}
