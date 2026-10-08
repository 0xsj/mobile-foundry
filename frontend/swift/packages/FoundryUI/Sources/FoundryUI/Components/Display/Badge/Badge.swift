import SwiftUI

/// Noninteractive status. Supply meaningful copy rather than relying on color.
public struct Badge: View {
    @Environment(\.foundry) private var tokens
    private let text: String
    private let tone: MessageTone
    public init(_ text: String, tone: MessageTone = .neutral) { self.text = text; self.tone = tone }
    public var body: some View {
        Text(text).font(tokens.typography.caption).foregroundStyle(tone.color(in: tokens))
            .padding(.horizontal, tokens.space.inline).padding(.vertical, tokens.space.steps[1])
            .background(tokens.colors.surfacePanel.color, in: Capsule())
            .overlay(Capsule().stroke(tone.color(in: tokens)))
    }
}
