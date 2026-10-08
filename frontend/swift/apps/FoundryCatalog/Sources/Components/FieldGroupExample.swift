import FoundryUI
import SwiftUI

struct FieldGroupValues { var title = "Atlas"; var owner = "Jordan"; var validated = false }
struct FieldGroupExample: View {
    @Binding var values: FieldGroupValues
    @FocusState private var titleFocus: Bool
    @FocusState private var ownerFocus: Bool
    private var invalid: Bool { values.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || values.owner.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    var body: some View {
        Card {
            FieldGroup("Workspace naming", help: "Both names stay in this gallery.", error: values.validated && invalid ? "Enter both workspace and owner names." : nil) {
                LabeledTextField("Workspace title", text: $values.title, focus: $titleFocus)
                LabeledTextField("Owner name", text: $values.owner, focus: $ownerFocus)
            }
            ActionButton("Validate fields", variant: .secondary) {
                values.validated = true; titleFocus = false; ownerFocus = false
            }
        }
    }
}
