import SwiftUI

/// Text and search execution belong to the caller; this control owns no debounce or request.
public struct SearchField: View {
    @Environment(\.foundry) private var tokens
    @Binding private var text: String
    private let title: String
    private let clearLabel: String
    private let onSubmit: () -> Void
    public init(_ title: String, text: Binding<String>, clearLabel: String,
                onSubmit: @escaping () -> Void = {}) {
        self.title = title; self._text = text; self.clearLabel = clearLabel; self.onSubmit = onSubmit
    }
    public var body: some View {
        HStack(spacing: tokens.space.inline) {
            Image(systemName: "magnifyingglass").accessibilityHidden(true)
            TextField(title, text: $text).accessibilityLabel(title)
                .submitLabel(.search).onSubmit(onSubmit)
            if !text.isEmpty {
                Button { text = "" } label: {
                    Image(systemName: "xmark.circle").frame(minWidth: tokens.shape.minimumInteractive,
                                                          minHeight: tokens.shape.minimumInteractive)
                }.buttonStyle(.plain).accessibilityLabel(clearLabel)
            }
        }
        .padding(.leading, tokens.space.stack).padding(.trailing, tokens.space.inline)
        .frame(minHeight: tokens.shape.minimumInteractive)
        .background(tokens.colors.surfaceSunk.color, in: RoundedRectangle(cornerRadius: tokens.shape.radii[2]))
    }
}
