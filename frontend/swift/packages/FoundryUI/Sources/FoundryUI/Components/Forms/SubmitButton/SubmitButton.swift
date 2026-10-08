import SwiftUI

/// One explicit action; progress/disabled state never starts asynchronous work itself.
public struct SubmitButton: View {
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
        ActionButton(submitting ? submittingTitle : title, isBusy: submitting, enabled: enabled, action: action)
    }
}
