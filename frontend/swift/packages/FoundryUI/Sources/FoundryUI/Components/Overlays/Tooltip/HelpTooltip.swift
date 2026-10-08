import SwiftUI

/// Tap-accessible short help, kept open until dismissed. No hover/long-press requirement or timer.
public struct HelpTooltip: View {
    @Binding private var isPresented: Bool
    private let label: String
    private let message: String
    private let closeLabel: String
    public init(_ label: String, message: String, isPresented: Binding<Bool>, closeLabel: String) {
        self.label = label; self.message = message
        self._isPresented = isPresented; self.closeLabel = closeLabel
    }
    public var body: some View {
        PopoverPanel(label, isPresented: $isPresented, closeLabel: closeLabel) {
            ActionButton(variant: .quiet, action: { isPresented = true }) {
                Label(label, systemImage: "questionmark.circle")
            }
        } content: { Text(message) }
    }
}
