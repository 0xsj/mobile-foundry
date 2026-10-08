import SwiftUI

/// Unique, nonempty options including selection. The caller owns group state.
public struct RadioGroup<Value: Hashable>: View {
    @Environment(\.foundry) private var tokens
    private let title: String
    @Binding private var selection: Value
    private let options: [Value]
    private let enabled: Bool
    private let label: (Value) -> String
    public init(_ title: String, selection: Binding<Value>, options: [Value], enabled: Bool = true,
                label: @escaping (Value) -> String) {
        precondition(!options.isEmpty && Set(options).count == options.count && options.contains(selection.wrappedValue))
        self.title = title; self._selection = selection; self.options = options
        self.enabled = enabled; self.label = label
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: tokens.space.inline) {
            Text(title).font(tokens.typography.label).accessibilityAddTraits(.isHeader)
            ForEach(options, id: \.self) { value in
                Button { selection = value } label: {
                    HStack(spacing: tokens.space.stack) {
                        Image(systemName: selection == value ? "largecircle.fill.circle" : "circle")
                            .foregroundStyle(tokens.colors.accent.color).accessibilityHidden(true)
                        Text(label(value))
                    }.frame(maxWidth: .infinity, minHeight: tokens.shape.minimumInteractive, alignment: .leading)
                        .contentShape(Rectangle())
                }.buttonStyle(.plain).disabled(!enabled)
                    .accessibilityAddTraits(selection == value ? .isSelected : [])
            }
        }.opacity(enabled ? 1 : 0.5)
    }
}
