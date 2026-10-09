import SwiftUI

/// Three independent native actions. Playing chooses only the glyph; caller supplies state meaning, admission and effects.
public struct PlaybackControls: View {
    @Environment(\.foundry) private var t
    private let isPlaying: Bool
    private let previousLabel: String
    private let toggleLabel: String
    private let nextLabel: String
    private let stateLabel: String
    private let previousEnabled: Bool
    private let toggleEnabled: Bool
    private let nextEnabled: Bool
    private let onPrevious: () -> Void
    private let onToggle: () -> Void
    private let onNext: () -> Void
    public init(isPlaying: Bool, previousLabel: String, toggleLabel: String, nextLabel: String, stateLabel: String,
                previousEnabled: Bool = true, toggleEnabled: Bool = true, nextEnabled: Bool = true,
                onPrevious: @escaping () -> Void, onToggle: @escaping () -> Void, onNext: @escaping () -> Void) {
        self.isPlaying = isPlaying; self.previousLabel = previousLabel; self.toggleLabel = toggleLabel; self.nextLabel = nextLabel
        self.stateLabel = stateLabel; self.previousEnabled = previousEnabled; self.toggleEnabled = toggleEnabled; self.nextEnabled = nextEnabled
        self.onPrevious = onPrevious; self.onToggle = onToggle; self.onNext = onNext
    }
    public var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: t.space.inline) { buttons }
            VStack(alignment: .leading, spacing: t.space.inline) { buttons }
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
    @ViewBuilder private var buttons: some View {
        IconAction(previousLabel, enabled: previousEnabled, action: onPrevious) { symbol("backward.end.fill") }
        IconAction(toggleLabel, variant: .primary, enabled: toggleEnabled, action: onToggle) {
            symbol(isPlaying ? "pause.fill" : "play.fill")
        }.accessibilityValue(stateLabel)
        IconAction(nextLabel, enabled: nextEnabled, action: onNext) { symbol("forward.end.fill") }
    }
    private func symbol(_ name: String) -> some View {
        Image(systemName: name).resizable().scaledToFit().frame(width: 24, height: 24)
    }
}
