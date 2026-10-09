import FoundryUI
import SwiftUI

struct PreviewMessage: Identifiable {
    let id = UUID()
    let text: String
    let attachment: Bool
}
struct CommunicationValues {
    var draft = ""
    var attached = false
    var phase: TransferPhase = .waiting
    var enabled = true
    var typing = true
    var messages: [PreviewMessage] = []
    var inspected = 0
    var canSend: Bool {
        enabled && (!attached || phase == .complete) &&
        (!draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || attached)
    }
    mutating func send() {
        guard canSend else { return }
        messages.append(PreviewMessage(text: draft, attachment: attached))
        draft = ""; attached = false; phase = .waiting
    }
    mutating func addAttachment() {
        guard enabled, !attached else { return }
        attached = true; phase = .waiting
    }
    mutating func removeAttachment() { guard enabled else { return }; attached = false; phase = .waiting }
    mutating func transition(to next: TransferPhase) {
        guard enabled, attached else { return }
        switch (phase, next) {
        case (.waiting, .transferring), (.paused, .transferring), (.failed, .transferring),
             (.transferring, .paused), (.transferring, .failed), (.transferring, .complete): phase = next
        default: break
        }
    }
    var transferCopy: String {
        switch phase {
        case .waiting: "Waiting to start. Local preview only."
        case .transferring: "Transferring preview: 35%."
        case .paused: "Transfer paused. Your draft is still here."
        case .failed: "Transfer failed. Your draft is still here."
        case .complete: "Attachment ready. Local preview only."
        }
    }
}

struct CommunicationExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: CommunicationValues
    @State private var conversation = false
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            ToggleField("Enable communication controls", isOn: $values.enabled)
            ToggleField("Show typing preview", isOn: $values.typing)
            Card {
                ConversationRow("Design room", preview: values.messages.last?.text.isEmpty == false
                    ? values.messages.last!.text : "Jordan: Share the next study when it is ready.",
                    timestamp: "Just now", unreadLabel: "3 unread", enabled: values.enabled, onOpen: { conversation = true }) {
                    Avatar("Design room", fallback: "DR")
                }
            }
            Text("Open Design room for a conversation with a persistent composer. Everything stays in this preview.")
                .font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color)
            ConversationMessages(values: $values)
            if values.attached { TransferPreview(values: $values) }
            ConversationComposer(values: $values)
        }.navigationDestination(isPresented: $conversation) {
            ConversationPreviewView(values: $values, appearance: t.appearance, style: t.materials.style)
        }
    }
}

private struct ConversationMessages: View {
    @Binding var values: CommunicationValues
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            MessageBubble("Jordan", text: "Share the next study when it is ready. Attachments can be sent on their own, too.", metadata: "10:42 · Preview message")
            ForEach(values.messages) { message in
                MessageBubble("You", text: message.text, metadata: "Saved locally · Preview message", direction: .outgoing) {
                    if message.attachment {
                        AttachmentRow("Study brief.pdf", detail: "PDF · 240 KB · Preview file", preview: {
                            Image(systemName: "doc.text").font(.title)
                        }, actions: {
                            ActionButton("Inspect sent attachment", variant: .quiet, enabled: values.enabled) { values.inspected += 1 }
                        })
                    }
                }
            }
            if values.typing { TypingIndicator("Jordan is typing…") }
            Text("Sent previews: \(values.messages.count) · Inspected files: \(values.inspected)")
        }
    }
}
private struct TransferPreview: View {
    @Binding var values: CommunicationValues
    var body: some View {
        Card {
            TransferStatus("Attachment transfer", status: values.transferCopy, phase: values.phase, fraction: 0.35) {
                switch values.phase {
                case .waiting:
                    ActionButton("Start transfer preview", variant: .secondary, enabled: values.enabled) { values.transition(to: .transferring) }
                case .transferring:
                    ActionButton("Pause transfer preview", variant: .secondary, enabled: values.enabled) { values.transition(to: .paused) }
                    ActionButton("Fail transfer preview", variant: .quiet, enabled: values.enabled) { values.transition(to: .failed) }
                    ActionButton("Finish transfer preview", enabled: values.enabled) { values.transition(to: .complete) }
                case .paused:
                    ActionButton("Resume transfer preview", enabled: values.enabled) { values.transition(to: .transferring) }
                case .failed:
                    ActionButton("Retry transfer preview", enabled: values.enabled) { values.transition(to: .transferring) }
                case .complete: EmptyView()
                }
                if values.phase != .complete {
                    ActionButton("Cancel transfer preview", variant: .quiet, enabled: values.enabled) { values.removeAttachment() }
                }
            }
        }
    }
}
private struct ConversationComposer: View {
    @Binding var values: CommunicationValues
    @FocusState private var focused: Bool
    var body: some View {
        MessageComposer("Message draft", text: $values.draft, sendLabel: "Send preview", canSend: values.canSend,
                        enabled: values.enabled, help: "Return adds a line. Send saves locally.", focus: $focused,
                        onSend: { values.send() }, attachments: {
            if values.attached {
                AttachmentRow("Study brief.pdf", detail: "PDF · 240 KB · Preview file", preview: {
                    Image(systemName: "doc.text").font(.title)
                }, actions: {
                    IconAction("Remove draft attachment", variant: .quiet, action: { values.removeAttachment() }) { Image(systemName: "xmark") }
                })
            }
        }, actions: {
            IconAction("Add preview attachment", variant: .secondary, enabled: !values.attached,
                       action: { values.addAttachment() }) { Image(systemName: "paperclip") }
        })
    }
}
struct ConversationPreviewView: View {
    @Binding var values: CommunicationValues
    let appearance: FoundryAppearance
    let style: FoundryThemeStyle
    var body: some View {
        FoundryTheme(appearance: appearance, style: style) {
            ScrollView {
                ContentContainer {
                    VStack(alignment: .leading, spacing: 20) {
                        Text("Local conversation preview").font(.headline)
                        ConversationMessages(values: $values)
                        if values.attached { TransferPreview(values: $values) }
                    }
                }
            }.scrollDismissesKeyboard(.interactively)
                .safeAreaInset(edge: .bottom, spacing: 0) { ConversationComposer(values: $values).padding(12) }
        }.navigationTitle("Design room").navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(FoundryPreset.v1(appearance: appearance).colors.surfaceGround.color, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(appearance == .dark ? .dark : .light, for: .navigationBar)
    }
}
