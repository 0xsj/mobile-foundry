import FoundryUI
import SwiftUI

struct ControlValues {
    var notifications = true
    var photos = true
    var notes = false
    var format = "Original"
    var destination = "This device"
    var intensity = 0.5
    // A stable calendar label, not a backend timestamp.
    var date = Calendar.current.date(from: DateComponents(year: 2026, month: 10, day: 8))!
    var aggregate: CheckState { photos && notes ? .on : !photos && !notes ? .off : .mixed }
}

struct ControlExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: ControlValues
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            SettingsSection("Workspace preferences", footer: "Committed values stay here when you change families or preview themes.") {
                ToggleField("Activity updates", isOn: $values.notifications, help: "Keep track of changes to your workspace.")
                Divider()
                ToggleField("Managed setting", isOn: .constant(false), help: "An example of a disabled control.", enabled: false)
            }
            Card {
                Text("Include in export").font(t.typography.heading)
                Checkbox("Include everything", state: values.aggregate,
                         stateDescription: values.aggregate == .mixed ? "Some included" : values.aggregate == .on ? "All included" : "None included") {
                    let next = values.aggregate != .on
                    values.photos = next; values.notes = next
                }
                Divider()
                Checkbox("Photos", state: values.photos ? .on : .off, stateDescription: values.photos ? "Included" : "Excluded") { values.photos.toggle() }
                Checkbox("Notes", state: values.notes ? .on : .off, stateDescription: values.notes ? "Included" : "Excluded") { values.notes.toggle() }
            }
            Card {
                RadioGroup("Export format", selection: $values.format, options: ["Original", "Compact", "Print"], label: { $0 })
                Divider()
                SelectField("Destination", selection: $values.destination, options: ["This device", "Shared workspace", "Archive"], label: { $0 })
            }
            Card {
                ValueSlider("Preview intensity", value: $values.intensity, steps: 3, valueLabel: "\(Int(values.intensity * 100))%")
                Divider()
                DateField("Review date", selection: $values.date, confirmLabel: "Use date", cancelLabel: "Discard date")
                Text("Review: \(values.date.formatted(date: .abbreviated, time: .omitted))").font(t.typography.caption)
            }
            InlineAlert("Your selection", message: "\(values.format) · \(values.destination) · \(Int(values.intensity * 100))% intensity")
        }
    }
}
