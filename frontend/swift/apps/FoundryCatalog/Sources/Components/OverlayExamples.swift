import FoundryUI
import SwiftUI

struct OverlayValues {
    var sheet = false
    var confirm = false
    var copies = 0
    var lastAction = "No action yet"
}

struct OverlayExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: OverlayValues
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            Card(.floating) {
                PageHeader("Native presentations", subtitle: "Tap outside, dismiss, or confirm. Only explicit actions change the example.")
                ActionButton("Show workspace sheet", variant: .secondary) { values.sheet = true }
                ActionButton("Reset copies", variant: .destructive) { values.confirm = true }
                ActionMenu("Workspace actions", actions: [
                    MenuAction(id: "duplicate", title: "Duplicate workspace") { values.copies += 1; values.lastAction = "Workspace duplicated" },
                    MenuAction(id: "share", title: "Share workspace", enabled: false) {},
                    MenuAction(id: "reset", title: "Reset copies…", destructive: true) { values.confirm = true }
                ])
            }
            Card {
                ListRow("Copies created", subtitle: "\(values.copies)")
                Text("Last action: \(values.lastAction)").font(t.typography.caption)
            }
        }
        .sheetPanel("Workspace sheet", isPresented: $values.sheet, closeLabel: "Close workspace sheet") {
            Card { ListRow("Atlas workspace", subtitle: "Custom content in a native presentation.") }
            Text("Closing this sheet does not change your selection.").font(t.typography.body)
        }
        .confirmationPrompt("Reset workspace copies?", message: "Clear the example copy count. Your real projects are not affected.",
                            isPresented: $values.confirm, confirmLabel: "Confirm reset", cancelLabel: "Keep copies",
                            destructive: true) {
            values.copies = 0; values.lastAction = "Copies reset"
        }
    }
}
