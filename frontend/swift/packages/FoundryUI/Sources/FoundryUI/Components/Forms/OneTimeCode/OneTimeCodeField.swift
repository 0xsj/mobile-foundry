import SwiftUI

/// Fixed-length ASCII digit input. Separators are accepted only in edits/pastes; canonical values contain digits alone.
public struct CodeFormat: Equatable, Sendable {
    public let length: Int
    public init(length: Int = 6) { precondition((1...12).contains(length)); self.length = length }
    public func admit(_ input: String) -> String? {
        var digits = ""
        for scalar in input.unicodeScalars {
            switch scalar.value {
            case 48...57: digits.unicodeScalars.append(scalar)
            case 9, 10, 13, 32, 45: continue
            default: return nil
            }
            if digits.count > length { return nil }
        }
        return digits
    }
    public func isValid(_ value: String) -> Bool { value.count <= length && value.unicodeScalars.allSatisfy { (48...57).contains($0.value) } }
    public func isComplete(_ value: String) -> Bool { isValid(value) && value.count == length }
}

/// One native editable field with an iOS one-time-code hint. No request, auto-submit, countdown or code persistence.
public struct OneTimeCodeField: View {
    @Environment(\.foundry) private var t
    @Binding private var text: String
    private let title: String
    private let format: CodeFormat
    private let help: String?
    private let error: String?
    private let enabled: Bool
    private let canSubmit: Bool
    private let focus: FocusState<Bool>.Binding
    private let onSubmit: () -> Void
    public init(_ title: String, text: Binding<String>, format: CodeFormat = CodeFormat(), help: String? = nil, error: String? = nil,
                enabled: Bool = true, canSubmit: Bool = true, focus: FocusState<Bool>.Binding, onSubmit: @escaping () -> Void = {}) {
        precondition(format.isValid(text.wrappedValue))
        self.title = title; self._text = text; self.format = format; self.help = help; self.error = error
        self.enabled = enabled; self.canSubmit = canSubmit; self.focus = focus; self.onSubmit = onSubmit
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            Text(title).font(t.typography.label).accessibilityHidden(true)
            field.textFieldStyle(.roundedBorder).font(t.typography.code)
                .frame(minHeight: t.shape.minimumInteractive).focused(focus).submitLabel(.done).disabled(!enabled)
                .autocorrectionDisabled().accessibilityLabel(title).accessibilityHint(error ?? help ?? "")
                .environment(\.layoutDirection, .leftToRight)
                .onSubmit { if enabled && canSubmit && format.isComplete(text) { onSubmit() } }
            if let error {
                Label(error, systemImage: "exclamationmark.circle").font(t.typography.caption).foregroundStyle(t.colors.crit.color)
            } else if let help { Text(help).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color) }
        }
    }
    private var admitted: Binding<String> {
        Binding(get: { text }, set: { value in
            if enabled, let next = format.admit(value), next != text { text = next }
        })
    }
    private var field: some View {
        #if os(iOS)
        TextField(title, text: admitted).textContentType(.oneTimeCode).keyboardType(.numberPad).textInputAutocapitalization(.never)
        #else
        TextField(title, text: admitted)
        #endif
    }
}
