import SwiftUI

/// One controlled draft with a shared native button/keyboard action. No parsing, validation, clearing or request.
public struct InlineActionField: View {
    @Environment(\.foundry) private var t
    @Environment(\.dynamicTypeSize) private var typeSize
    @Binding private var text: String
    private let title: String
    private let actionLabel: String
    private let canSubmit: Bool
    private let enabled: Bool
    private let isBusy: Bool
    private let help: String?
    private let error: String?
    private let focus: FocusState<Bool>.Binding
    private let onSubmit: () -> Void
    public init(_ title: String, text: Binding<String>, actionLabel: String, canSubmit: Bool,
                enabled: Bool = true, isBusy: Bool = false, help: String? = nil, error: String? = nil,
                focus: FocusState<Bool>.Binding, onSubmit: @escaping () -> Void) {
        self.title = title; self._text = text; self.actionLabel = actionLabel; self.canSubmit = canSubmit
        self.enabled = enabled; self.isBusy = isBusy; self.help = help; self.error = error; self.focus = focus; self.onSubmit = onSubmit
    }
    public var body: some View {
        FieldGroup(title, help: help, error: error) {
            Group {
                if typeSize.isAccessibilitySize { stacked }
                else {
                    ViewThatFits(in: .horizontal) {
                        HStack(alignment: .center, spacing: t.space.inline) {
                            field.frame(minWidth: 180)
                            action.fixedSize(horizontal: true, vertical: false)
                        }
                        stacked
                    }
                }
            }
        }
    }
    private var stacked: some View { VStack(alignment: .leading, spacing: t.space.inline) { field; action } }
    private var field: some View {
        TextField(title, text: $text).textFieldStyle(.roundedBorder).font(t.typography.body)
            .frame(minHeight: t.shape.minimumInteractive).focused(focus).submitLabel(.done)
            .disabled(!enabled || isBusy).accessibilityLabel(title).accessibilityHint(error ?? help ?? "")
            .onSubmit(submit)
    }
    private var action: some View { ActionButton(actionLabel, variant: .secondary, isBusy: isBusy, enabled: enabled && canSubmit, action: submit) }
    private func submit() { if enabled && !isBusy && canSubmit { onSubmit() } }
}
