@testable import FoundryCatalog
import FoundryUI
import Observation
import SwiftUI
import Testing

@Test func failedTransferPreservesDraftAndOnlyReadyAttachmentCanBeSent() {
    var values = CommunicationValues()
    values.draft = "A multiline\nstudy brief"
    values.addAttachment()
    values.send()
    #expect(values.messages.isEmpty)
    values.transition(to: .transferring); values.transition(to: .failed)
    #expect(values.draft == "A multiline\nstudy brief" && values.attached)
    values.transition(to: .complete) // A failed transfer cannot skip retry.
    #expect(values.phase == .failed && !values.canSend)
    values.transition(to: .transferring); values.transition(to: .paused)
    #expect(!values.canSend)
    values.transition(to: .transferring); values.transition(to: .complete)
    values.send(); values.send()
    #expect(values.messages.count == 1)
    #expect(values.messages.first?.text == "A multiline\nstudy brief")
    #expect(values.messages.first?.attachment == true)
    #expect(values.draft.isEmpty && !values.attached)
}
@Test func disabledCommunicationAndCancellationKeepTheUnsavedDraft() {
    var values = CommunicationValues()
    values.draft = "Keep this note"
    values.addAttachment(); values.transition(to: .transferring)
    values.enabled = false
    values.transition(to: .complete); values.removeAttachment(); values.send()
    #expect(values.phase == .transferring && values.attached && values.messages.isEmpty)
    values.enabled = true; values.removeAttachment()
    #expect(values.draft == "Keep this note" && values.canSend)
    values.draft = " \n "; #expect(!values.canSend)
    values.addAttachment(); values.transition(to: .transferring); values.transition(to: .complete)
    #expect(values.canSend) // Attachment-only drafts are admitted by this app preview.
    values.send(); #expect(values.messages.count == 1 && values.messages[0].attachment)
}

@Observable @MainActor private final class CommunicationLayoutValues {
    var size: DynamicTypeSize = .large
    var frames: [String: CGRect] = [:]
    var draft = "A short preview draft"
}
private struct CommunicationFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) {
        value.merge(nextValue(), uniquingKeysWith: { _, latest in latest })
    }
}
private extension View {
    func communicationFrame(_ id: String) -> some View {
        background { GeometryReader { geometry in
            Color.clear.preference(key: CommunicationFrames.self,
                value: [id: geometry.frame(in: .named("communication-layout"))])
        } }
    }
}
private struct CommunicationLayoutProbe: View {
    @Bindable var values: CommunicationLayoutValues
    @FocusState private var focus: Bool
    var body: some View {
        FoundryTheme {
            VStack(alignment: .leading, spacing: 16) {
                ConversationRow("A room with a longer name", preview: "Preview text grows to keep the next reply readable.",
                    timestamp: "Yesterday afternoon", unreadLabel: "Three unread replies", onOpen: {}) {
                    Avatar("Room", fallback: "R")
                }.communicationFrame("row")
                MessageBubble("Jordan", text: "A longer message with supporting context that can wrap on a narrow device.", metadata: "Yesterday afternoon · Saved locally") {
                    ActionButton("Inspect message attachment", variant: .quiet) {}.communicationFrame("attachment-action")
                }.communicationFrame("bubble")
                MessageComposer("Message draft", text: $values.draft, sendLabel: "Send preview", canSend: true,
                    focus: $focus, onSend: {}, attachments: { EmptyView() }, actions: {
                        IconAction("Attach file", action: {}) { Image(systemName: "paperclip") }
                }).communicationFrame("composer")
            }.frame(width: 280).coordinateSpace(name: "communication-layout")
        }.environment(\.dynamicTypeSize, values.size)
            .onPreferenceChange(CommunicationFrames.self) { values.frames = $0 }
    }
}
@MainActor @Test func conversationCopyGrowsAndAttachmentActionsStayInsideTheBubble() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene)
    window.frame = CGRect(x: 0, y: 0, width: 390, height: 1800)
    let values = CommunicationLayoutValues()
    let host = UIHostingController(rootView: CommunicationLayoutProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    for _ in 0..<100 {
        host.view.layoutIfNeeded(); if values.frames.count == 4 { break }
        try await Task.sleep(for: .milliseconds(20))
    }
    let row = try #require(values.frames["row"])
    let bubble = try #require(values.frames["bubble"])
    values.size = .accessibility3
    for _ in 0..<100 {
        host.view.layoutIfNeeded()
        if (values.frames["row"]?.height ?? 0) > row.height + 30 && (values.frames["bubble"]?.height ?? 0) > bubble.height + 30 { break }
        try await Task.sleep(for: .milliseconds(20))
    }
    let largeRow = try #require(values.frames["row"])
    let largeBubble = try #require(values.frames["bubble"])
    let action = try #require(values.frames["attachment-action"])
    let composer = try #require(values.frames["composer"])
    #expect(largeRow.height > row.height + 30 && largeBubble.height > bubble.height + 30)
    #expect(largeBubble.insetBy(dx: -1, dy: -1).contains(action))
    #expect(action.height >= 44 && action.width <= 280)
    #expect(composer.width <= 280 && values.draft == "A short preview draft")
}
