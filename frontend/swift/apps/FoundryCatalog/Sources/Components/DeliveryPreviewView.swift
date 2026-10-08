import FoundryUI
import SwiftUI

struct DeliveryPreviewView: View {
    @Binding var values: DeliveryValues
    let appearance: FoundryAppearance
    let style: FoundryThemeStyle
    var body: some View {
        FoundryTheme(appearance: appearance, style: style) { DeliveryPreviewContent(values: $values) }
            .navigationTitle("Delivery preview").navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(FoundryPreset.v1(appearance: appearance).colors.surfaceGround.color, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(appearance == .dark ? .dark : .light, for: .navigationBar)
    }
}

private struct DeliveryPreviewContent: View {
    @Environment(\.foundry) private var t
    @Binding var values: DeliveryValues
    var body: some View {
        DetailShell {
            ContentContainer { PageHeader("Atlas delivery", subtitle: "Review a local preview.") }
        } content: {
            ScrollView {
                ContentContainer {
                    VStack(alignment: .leading, spacing: t.space.section) {
                        Card {
                            KeyValueRow("Collection", value: "Atlas study")
                            Divider()
                            KeyValueRow("Destination", value: "Personal collection on this device")
                            Divider()
                            KeyValueRow("Availability", value: "Local preview only", detail: "These controls change this preview.")
                        }
                        Card { DeliveryControls(values: $values) }
                        Card { DeliveryNote(values: $values) }
                    }
                }
            }
        } actions: {
            ContentContainer {
                ActionBar(summary: "\(values.copies) copies · \(values.format)") {
                    ActionButton(enabled: values.copies > 0, action: { values.applied += 1 }) {
                        Text("Apply preview").frame(maxWidth: .infinity)
                    }
                    ActionButton("Reset delivery", variant: .quiet) { values = DeliveryValues(applied: values.applied) }
                    Text("Applied previews: \(values.applied)").font(t.typography.caption)
                }
            }
        }
    }
}
