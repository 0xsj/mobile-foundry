import SwiftUI

/// Native multiline draft. The caller owns send eligibility, focus, attachments and post-send clearing.
public struct MessageComposer<Attachments: View, Actions: View>: View {
    @Environment(\.foundry) private var t
    @Binding private var text: String
    private let title: String
    private let sendLabel: String
    private let canSend: Bool
    private let isSending: Bool
    private let enabled: Bool
    private let help: String?
    private let focus: FocusState<Bool>.Binding
    private let onSend: () -> Void
    private let attachments: Attachments
    private let actions: Actions
    public init(_ title: String, text: Binding<String>, sendLabel: String, canSend: Bool, isSending: Bool = false,
                enabled: Bool = true, help: String? = nil, focus: FocusState<Bool>.Binding,
                onSend: @escaping () -> Void, @ViewBuilder attachments: () -> Attachments, @ViewBuilder actions: () -> Actions) {
        self.title = title; self._text = text; self.sendLabel = sendLabel; self.canSend = canSend
        self.isSending = isSending; self.enabled = enabled; self.help = help; self.focus = focus
        self.onSend = onSend; self.attachments = attachments(); self.actions = actions()
    }
    public var body: some View {
        Surface(.floating) {
            VStack(alignment: .leading, spacing: t.space.inline) {
                attachments
                MultilineField(title, text: $text, help: help, enabled: enabled && !isSending, lines: 1...5, focus: focus)
                ViewThatFits(in: .horizontal) {
                    HStack { actions; Spacer(minLength: t.space.inline); send }
                    VStack(alignment: .leading, spacing: t.space.inline) { actions; send }
                }
            }.padding(t.space.stack)
        }.disabled(!enabled || isSending)
    }
    private var send: some View {
        ActionButton(sendLabel, isBusy: isSending, enabled: enabled && canSend, action: onSend)
    }
}
extension MessageComposer where Attachments == EmptyView, Actions == EmptyView {
    public init(_ title: String, text: Binding<String>, sendLabel: String, canSend: Bool, isSending: Bool = false,
                enabled: Bool = true, help: String? = nil, focus: FocusState<Bool>.Binding, onSend: @escaping () -> Void) {
        self.init(title, text: text, sendLabel: sendLabel, canSend: canSend, isSending: isSending, enabled: enabled,
                  help: help, focus: focus, onSend: onSend, attachments: { EmptyView() }, actions: { EmptyView() })
    }
}
