import FoundryUI
import SwiftUI

struct ContextValues {
    var help = false
    var options = false
    var choices = 0
}

struct ContextExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: ContextValues
    var body: some View {
        Card {
            Text("Context and navigation").font(t.typography.heading).accessibilityAddTraits(.isHeader)
            HelpTooltip("About previews", message: "Previews stay on this device until you choose to share them.",
                        isPresented: $values.help, closeLabel: "Close preview help")
            PopoverPanel("Storage options", isPresented: $values.options, closeLabel: "Close storage options") {
                ActionButton("Show storage options", variant: .secondary) { values.options = true }
            } content: {
                Text("Keep a local copy for quick access.")
                ActionButton("Choose local storage") { values.choices += 1; values.options = false }
            }
            Text("Storage choices: \(values.choices)").font(t.typography.caption)
            Divider()
            NavLink("Storage details", subtitle: "Open a separate example screen") { ComponentDetailView() }
        }
    }
}

struct ComponentDetailView: View {
    @Environment(\.foundry) private var t
    var body: some View {
        ScrollView {
            ContentContainer {
                Card {
                    Text("Local storage").font(t.typography.heading).accessibilityAddTraits(.isHeader)
                    Text("This is a placeholder destination. Your app supplies the route and its content.")
                }
            }
        }.navigationTitle("Storage details").navigationBarTitleDisplayMode(.inline)
    }
}
