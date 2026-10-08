import SwiftUI

/// Native single-line input. The feature owns validation, focus and submit policy.
public struct FoundryTextField: View {
    @Environment(\.foundry) private var tokens
    @Binding private var text: String
    private let label: String
    private let help: String?
    private let error: String?
    private let enabled: Bool
    private let focus: FocusState<Bool>.Binding
    private let submitLabel: SubmitLabel
    private let onBlur: () -> Void
    private let onSubmit: () -> Void

    public init(_ label: String, text: Binding<String>, help: String? = nil, error: String? = nil,
                enabled: Bool = true, focus: FocusState<Bool>.Binding, submitLabel: SubmitLabel = .done,
                onBlur: @escaping () -> Void = {}, onSubmit: @escaping () -> Void = {}) {
        self.label = label; self._text = text; self.help = help; self.error = error
        self.enabled = enabled; self.focus = focus; self.submitLabel = submitLabel
        self.onBlur = onBlur; self.onSubmit = onSubmit
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: tokens.space.inline) {
            Text(label).font(tokens.typography.label).accessibilityHidden(true)
            TextField(label, text: $text)
                .textFieldStyle(.roundedBorder).font(tokens.typography.body)
                .frame(minHeight: tokens.shape.minimumInteractive)
                .focused(focus).submitLabel(submitLabel).disabled(!enabled)
                .accessibilityLabel(label).accessibilityHint(error ?? help ?? "")
                .onSubmit(onSubmit)
                .onChange(of: focus.wrappedValue) { old, new in if old && !new { onBlur() } }
            if let error {
                Label(error, systemImage: "exclamationmark.circle")
                    .font(tokens.typography.caption).foregroundStyle(tokens.colors.crit.color)
                    .accessibilityLabel("Error: \(error)")
            } else if let help {
                Text(help).font(tokens.typography.caption).foregroundStyle(tokens.colors.inkSecondary.color)
            }
        }
    }
}
