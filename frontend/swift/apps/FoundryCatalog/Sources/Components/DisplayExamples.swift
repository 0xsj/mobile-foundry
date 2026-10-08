import FoundryUI
import SwiftUI

struct DisplayExamples: View {
    @Environment(\.foundry) private var t
    @Binding var artwork: Bool
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            Card {
                Text("People and workspaces").font(t.typography.heading)
                ListRow("Jordan Lee", subtitle: "Initials supplied by the caller", leading: {
                    Avatar("Jordan profile", fallback: "JL")
                }, trailing: { EmptyView() })
                ListRow("Design studio", subtitle: artwork ? "Custom artwork slot" : "Fallback while artwork is unavailable", leading: {
                    if artwork {
                        Avatar("Studio emblem", fallback: "ST", size: 64, shape: .rounded) {
                            LinearGradient(colors: [t.colors.accent.color, t.colors.info.color], startPoint: .topLeading, endPoint: .bottomTrailing)
                                .overlay { Image(systemName: "sparkle").font(.title).foregroundStyle(t.colors.surfaceGround.color) }
                        }
                    } else { Avatar("Studio emblem", fallback: "ST", size: 64, shape: .rounded) }
                }, trailing: { EmptyView() })
                ToggleField("Show custom artwork", isOn: $artwork)
            }
            StatCard("Active projects", value: "12", detail: "Illustrative workspace data", trend: "3 added this week", tone: .info)
            StatCard("Storage used", value: "2.4 GB", detail: "Of your 5 GB local budget", trend: "Review before adding large files", tone: .warning, role: .floating)
        }
    }
}
