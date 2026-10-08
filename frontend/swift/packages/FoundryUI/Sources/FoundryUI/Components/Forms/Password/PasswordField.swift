import SwiftUI

public enum PasswordPurpose: Sendable { case current, new }

/// Native obscured input. The caller owns the draft, focus, validation and submission.
public struct PasswordField: View {
    @Environment(\.foundry) private var t
    @Binding private var text: String
    private let title: String
    private let help: String?
    private let error: String?
    private let enabled: Bool
    private let purpose: PasswordPurpose
    private let focus: FocusState<Bool>.Binding
    private let submitLabel: SubmitLabel
    private let onSubmit: () -> Void
    public init(_ title: String, text: Binding<String>, help: String? = nil, error: String? = nil,
                enabled: Bool = true, purpose: PasswordPurpose = .current,
                focus: FocusState<Bool>.Binding, submitLabel: SubmitLabel = .done,
                onSubmit: @escaping () -> Void = {}) {
        self.title = title; self._text = text; self.help = help; self.error = error
        self.enabled = enabled; self.purpose = purpose; self.focus = focus
        self.submitLabel = submitLabel; self.onSubmit = onSubmit
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            Text(title).font(t.typography.label).accessibilityHidden(true)
            nativeField.textFieldStyle(.roundedBorder).font(t.typography.body)
                .frame(minHeight: t.shape.minimumInteractive).focused(focus)
                .submitLabel(submitLabel).onSubmit(onSubmit).disabled(!enabled)
                .accessibilityLabel(title).accessibilityHint(error ?? help ?? "")
            if let error {
                Label(error, systemImage: "exclamationmark.circle").font(t.typography.caption)
                    .foregroundStyle(t.colors.crit.color).accessibilityLabel("Error: \(error)")
            } else if let help { Text(help).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color) }
        }
    }
    @ViewBuilder private var nativeField: some View {
        #if os(iOS)
        SecureField(title, text: $text).textContentType(purpose == .new ? .newPassword : .password)
            .textInputAutocapitalization(.never).autocorrectionDisabled()
        #else
        SecureField(title, text: $text)
        #endif
    }
}
