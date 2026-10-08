import SwiftUI

public struct SelectField<Value: Hashable>: View {
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
        Picker(title, selection: $selection) {
            ForEach(options, id: \.self) { value in Text(label(value)).tag(value) }
        }.pickerStyle(.menu).frame(minHeight: tokens.shape.minimumInteractive).disabled(!enabled)
    }
}
