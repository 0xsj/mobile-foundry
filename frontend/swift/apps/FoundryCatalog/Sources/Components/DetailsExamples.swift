import FoundryUI
import SwiftUI

struct DeliveryValues {
    var format = "Full size"
    var copies = 0
    var note = "Handle with care."
    var expanded = false
    var enabled = true
    var applied = 0
}

struct DetailsExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: DeliveryValues
    var body: some View {
        Card { DeliveryControls(values: $values) }
        Card { DeliveryNote(values: $values) }
        Card {
            KeyValueRow("Collection", value: "Atlas study")
            Divider()
            KeyValueRow("Destination", value: "Personal collection on this device",
                        detail: "Long detail values wrap without hiding their meaning.")
            Divider()
            NavLink("Open delivery preview", subtitle: "A detail screen with persistent actions") {
                DeliveryPreviewView(values: $values, appearance: t.appearance, style: t.materials.style)
            }
            Text("Applied previews: \(values.applied)").font(t.typography.caption)
        }
    }
}

struct DeliveryControls: View {
    @Environment(\.foundry) private var t
    @Binding var values: DeliveryValues
    var body: some View {
        Text("Delivery choices").font(t.typography.heading).accessibilityAddTraits(.isHeader)
        ViewThatFits(in: .horizontal) {
            HStack { chips }
            VStack(alignment: .leading, spacing: t.space.inline) { chips }
        }
        Text("Export size: \(values.format)").font(t.typography.caption)
        ValueStepper("Copy count", value: $values.copies, valueLabel: "\(values.copies) copies",
                     decreaseLabel: "Fewer copies", increaseLabel: "More copies", range: 0...5, step: 2, enabled: values.enabled)
        Text("Change by two, up to five copies.").font(t.typography.caption)
        ToggleField("Enable quantity control", isOn: $values.enabled)
    }
    @ViewBuilder private var chips: some View {
        ForEach(["Full size", "Compact size", "Print size"], id: \.self) { format in
            ChoiceChip(format, selected: values.format == format, enabled: format != "Print size") { values.format = format }
        }
    }
}

struct DeliveryNote: View {
    @Binding var values: DeliveryValues
    @FocusState private var focused: Bool
    var body: some View {
        DisclosureSection("Delivery note", isExpanded: $values.expanded,
                          stateDescription: values.expanded ? "Expanded" : "Collapsed", subtitle: "Optional instructions") {
            LabeledTextField("Package note", text: $values.note, focus: $focused)
            ActionButton("Done editing note", variant: .quiet) { focused = false }
        }.onChange(of: values.expanded) { _, expanded in if !expanded { focused = false } }
    }
}
