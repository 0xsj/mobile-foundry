import FoundryUI
import SwiftUI

enum ExampleNotice { case saved, failed }
struct FeedbackValues {
    var loading = true
    var reduced = false
    var notice: ExampleNotice?
    var undos = 0
    var retries = 0
}

struct FeedbackExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: FeedbackValues
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            Card {
                ToggleField("Show loading placeholders", isOn: $values.loading)
                ToggleField("Reduce loading motion", isOn: $values.reduced)
                if values.loading {
                    Text("Loading collection…").font(t.typography.label)
                    HStack(alignment: .top, spacing: t.space.stack) {
                        Skeleton(height: 48, shape: .circle, animated: !values.reduced)
                        VStack(alignment: .leading, spacing: t.space.inline) {
                            Skeleton(height: 18, animated: !values.reduced)
                            Skeleton(height: 12, width: 140, animated: !values.reduced)
                            Skeleton(height: 12, width: 100, animated: !values.reduced)
                        }
                    }
                    Text(values.reduced || t.motion.reduced ? "Static placeholders" : "Pulsing placeholders").font(t.typography.caption)
                } else { ListRow("Collection ready", subtitle: "The host replaces placeholders with real content.") }
            }
            Card {
                Text("Transient feedback").font(t.typography.heading)
                Text("These notices stay until you dismiss or replace them. No operation runs automatically.").font(t.typography.caption)
                ActionButton("Show saved notice", variant: .secondary) { values.notice = .saved }
                ActionButton("Show retry notice", variant: .secondary) { values.notice = .failed }
                Text("Undo actions: \(values.undos) · Retry actions: \(values.retries)").font(t.typography.caption)
            }
            if let notice = values.notice {
                ToastBanner(notice == .saved ? "Example saved on this device." : "Example could not be saved.",
                            tone: notice == .saved ? .info : .warning,
                            action: ToastAction(notice == .saved ? "Undo example" : "Retry example") {
                    if notice == .saved { values.undos += 1; values.notice = nil }
                    else { values.retries += 1; values.notice = .saved }
                }, dismissLabel: "Dismiss notice", onDismiss: { values.notice = nil })
            }
        }
    }
}
