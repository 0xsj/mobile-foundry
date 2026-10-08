import SwiftUI

/// One explicit action; progress/disabled state never starts asynchronous work itself.
public struct FoundrySubmitButton: View {
    @Environment(\.foundry) private var tokens
    private let title: String
    private let submittingTitle: String
    private let submitting: Bool
    private let enabled: Bool
    private let action: () -> Void

    public init(_ title: String, submittingTitle: String, isSubmitting: Bool, enabled: Bool = true,
                action: @escaping () -> Void) {
        self.title = title; self.submittingTitle = submittingTitle; self.submitting = isSubmitting
        self.enabled = enabled; self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: tokens.space.inline) {
                if submitting { ProgressView().tint(tokens.colors.fillInk.color).accessibilityHidden(true) }
                Text(submitting ? submittingTitle : title)
            }.frame(minHeight: tokens.shape.minimumInteractive).contentShape(Rectangle())
        }
        .buttonStyle(.borderedProminent).tint(tokens.colors.fill.color)
        .foregroundStyle(tokens.colors.fillInk.color).disabled(submitting || !enabled)
    }
}
