import SwiftUI

/// Small within-screen choices; destination navigation remains app-owned.
public struct Tabs<Value: Hashable>: View {
    @Environment(\.dynamicTypeSize) private var typeSize
    @Binding private var selection: Value
    private let title: String
    private let options: [Value]
    private let label: (Value) -> String
    public init(_ title: String, selection: Binding<Value>, options: [Value], label: @escaping (Value) -> String) {
        precondition(!options.isEmpty && Set(options).count == options.count && options.contains(selection.wrappedValue))
        self.title = title; self._selection = selection; self.options = options; self.label = label
    }
    public var body: some View {
        if typeSize.isAccessibilitySize || options.count > 3 { picker.pickerStyle(.menu) }
        else { picker.pickerStyle(.segmented) }
    }
    private var picker: some View {
        Picker(title, selection: $selection) {
            ForEach(options, id: \.self) { value in Text(label(value)).tag(value) }
        }.accessibilityLabel(title)
    }
}
