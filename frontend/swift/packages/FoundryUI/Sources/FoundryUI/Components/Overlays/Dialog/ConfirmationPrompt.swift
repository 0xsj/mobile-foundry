import SwiftUI

extension View {
    /// Cancel/dismiss never confirms. The caller decides whether confirmation may start an operation.
    public func confirmationPrompt(_ title: String, message: String, isPresented: Binding<Bool>,
                                   confirmLabel: String, cancelLabel: String, destructive: Bool = false,
                                   onConfirm: @escaping () -> Void) -> some View {
        alert(title, isPresented: isPresented) {
            Button(confirmLabel, role: destructive ? .destructive : nil) {
                isPresented.wrappedValue = false
                onConfirm()
            }
            Button(cancelLabel, role: .cancel) { isPresented.wrappedValue = false }
        } message: { Text(message) }
    }
}
