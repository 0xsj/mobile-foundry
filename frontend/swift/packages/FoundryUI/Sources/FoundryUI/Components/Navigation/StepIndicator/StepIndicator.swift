import SwiftUI

public enum StepStatus: Sendable { case completed, current, upcoming }
public struct StepItem: Identifiable, Sendable {
    public let id: String
    public let title: String
    public let status: StepStatus
    public let stateDescription: String
    public init(id: String, title: String, status: StepStatus, stateDescription: String) {
        self.id = id; self.title = title; self.status = status; self.stateDescription = stateDescription
    }
}

/// Passive ordered progress. The caller chooses statuses, copy and navigation policy.
public struct StepIndicator: View {
    @Environment(\.foundry) private var t
    private let summary: String
    private let steps: [StepItem]
    public init(_ summary: String, steps: [StepItem]) {
        precondition(Set(steps.map(\.id)).count == steps.count)
        self.summary = summary; self.steps = steps
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: t.space.stack) {
            Text(summary).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color)
            ForEach(Array(steps.enumerated()), id: \.element.id) { index, step in
                HStack(alignment: .top, spacing: t.space.stack) {
                    Text(step.status == .completed ? "✓" : "\(index + 1)")
                        .font(t.typography.label).padding(t.space.inline)
                        .background(step.status == .current ? t.colors.accentTint.color : t.colors.surfacePanel.color, in: Circle())
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: t.space.inline) {
                        Text(step.title).font(t.typography.label)
                        Text(step.stateDescription).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color)
                    }
                }.accessibilityElement(children: .ignore)
                    .accessibilityLabel(step.title).accessibilityValue(step.stateDescription)
            }
        }
    }
}
