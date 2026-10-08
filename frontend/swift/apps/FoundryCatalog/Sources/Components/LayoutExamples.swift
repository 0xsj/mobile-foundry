import FoundryUI
import SwiftUI

struct LayoutValues {
    var narrow = false
    var selected = "None"
}

struct LayoutExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: LayoutValues
    var body: some View {
        ToggleField("Narrow content preview", isOn: $values.narrow)
        Text("Cards reflow to fit the available width and text size.").font(t.typography.caption)
        ContentContainer(maximumWidth: values.narrow ? 240 : 720, inset: 0) {
            AdaptiveGrid(minimumItemWidth: 140) {
                ForEach(["Atlas", "Orbit", "Field"], id: \.self) { name in
                    Card {
                        MediaFrame(ratio: 4 / 3) {
                            ZStack {
                                LinearGradient(colors: [t.colors.accentTint.color, t.colors.surfaceSunk.color],
                                               startPoint: .topLeading, endPoint: .bottomTrailing)
                                Circle().stroke(t.colors.accent.color, lineWidth: 3).padding(20)
                            }
                        }.accessibilityElement(children: .ignore).accessibilityLabel("\(name) preview")
                        Text(name).font(t.typography.label)
                        Text(name == "Orbit" ? "A longer description that grows with your preferred text size." : "A small study.")
                            .font(t.typography.caption)
                        ActionButton("Pick \(name)", variant: .secondary) { values.selected = name }
                    }
                }
            }
        }
        Text("Picked layout item: \(values.selected)").font(t.typography.caption)
    }
}
