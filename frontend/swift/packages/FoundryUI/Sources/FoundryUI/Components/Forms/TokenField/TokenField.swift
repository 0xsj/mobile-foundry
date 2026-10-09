import SwiftUI

/// Caller-owned single-line draft and token slots. Admission, parsing, duplicate policy and clearing are external.
public struct TokenField<Tokens: View>: View {
    @Environment(\.foundry) private var t
    @Binding private var text: String
    private let title: String
    private let addLabel: String
    private let canAdd: Bool
    private let enabled: Bool
    private let isBusy: Bool
    private let help: String?
    private let error: String?
    private let focus: FocusState<Bool>.Binding
    private let onAdd: () -> Void
    private let tokens: Tokens
    public init(_ title: String, text: Binding<String>, addLabel: String, canAdd: Bool,
                enabled: Bool = true, isBusy: Bool = false, help: String? = nil, error: String? = nil,
                focus: FocusState<Bool>.Binding, onAdd: @escaping () -> Void, @ViewBuilder tokens: () -> Tokens) {
        self.title = title; self._text = text; self.addLabel = addLabel; self.canAdd = canAdd
        self.enabled = enabled; self.isBusy = isBusy; self.help = help; self.error = error
        self.focus = focus; self.onAdd = onAdd; self.tokens = tokens()
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            WrapLayout { tokens }
            LabeledTextField(title, text: $text, help: help, error: error, enabled: enabled && !isBusy,
                             focus: focus, onSubmit: add)
            ActionButton(addLabel, variant: .secondary, isBusy: isBusy, enabled: enabled && canAdd, action: add)
        }.disabled(!enabled || isBusy)
    }
    private func add() { if enabled && !isBusy && canAdd { onAdd() } }
}
