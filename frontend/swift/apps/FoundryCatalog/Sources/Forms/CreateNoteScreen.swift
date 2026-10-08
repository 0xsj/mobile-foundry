import FoundryKernel
import FoundryUI
import SwiftUI

struct CreateNoteScreen: View {
    @Environment(\.foundry) private var tokens
    @FocusState private var titleFocused: Bool
    let store: CreateNoteStore

    var body: some View {
        Surface(.floating) {
            VStack(alignment: .leading, spacing: tokens.space.stack) {
                Text("Create note").font(tokens.typography.heading)
                LabeledTextField("Title", text: Binding(get: { store.title }, set: { store.editTitle($0) }),
                                 help: "Required · Up to 200 characters.", error: store.titleError,
                                 enabled: store.canEdit, focus: $titleFocused,
                                 onBlur: { store.blurTitle() }, onSubmit: { submit() })
                    .textInputAutocapitalization(.sentences)
                SubmitButton("Create note", submittingTitle: "Creating note…", isSubmitting: store.mutation.isSubmitting,
                                    enabled: store.canEdit, action: { submit() })
                MutationFeedback(store.mutation, submitting: "Waiting for confirmation…") { note in
                    VStack(alignment: .leading, spacing: tokens.space.inline) {
                        Label("Note created", systemImage: "checkmark.circle")
                        Text(note.title)
                    }
                }
                if confirmationIsMissing {
                    Text("We couldn’t confirm whether the note was saved. Check your notes before submitting again.")
                        .font(tokens.typography.caption).foregroundStyle(tokens.colors.inkSecondary.color)
                }
                Button(store.mutation.value == nil ? "Clear form" : "New note") {
                    store.reset(); titleFocused = false
                }.buttonStyle(.bordered).disabled(store.mutation.isSubmitting)
            }.padding(tokens.space.page).frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var confirmationIsMissing: Bool {
        switch store.mutation.failure?.kind {
        case .timeout, .canceled, .unavailable, .internalError: true
        default: false
        }
    }

    private func submit() {
        guard store.canEdit else { return }
        let operation = store.submit()
        titleFocused = operation == nil
    }
}
