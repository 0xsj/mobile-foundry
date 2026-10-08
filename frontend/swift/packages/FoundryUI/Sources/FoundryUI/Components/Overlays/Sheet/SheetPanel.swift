import SwiftUI

/// A titled sheet body; caller content may include its own scrolling and actions.
public struct SheetPanel<Content: View>: View {
    @Environment(\.foundry) private var tokens
    private let title: String
    private let closeLabel: String
    private let onClose: () -> Void
    private let content: Content
    public init(_ title: String, closeLabel: String, onClose: @escaping () -> Void, @ViewBuilder content: () -> Content) {
        self.title = title; self.closeLabel = closeLabel; self.onClose = onClose; self.content = content()
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: tokens.space.section) {
            Text(title).font(tokens.typography.heading).accessibilityAddTraits(.isHeader)
            content
            ActionButton(closeLabel, variant: .secondary, action: onClose)
        }.frame(maxWidth: .infinity, alignment: .leading).padding(tokens.space.page)
    }
}

extension View {
    public func sheetPanel<Content: View>(_ title: String, isPresented: Binding<Bool>, closeLabel: String,
                                         onDismiss: (() -> Void)? = nil, @ViewBuilder content: @escaping () -> Content) -> some View {
        sheet(isPresented: isPresented, onDismiss: onDismiss) {
            FoundryTheme {
                SheetPanel(title, closeLabel: closeLabel, onClose: { isPresented.wrappedValue = false }, content: content)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
    }
}
