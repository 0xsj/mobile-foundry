import FoundryUI
import SwiftUI

enum ShellDestination: String, CaseIterable {
    case overview = "Overview", activity = "Activity", settings = "Settings"
    var symbol: String { switch self { case .overview: "square.grid.2x2"; case .activity: "clock"; case .settings: "slider.horizontal.3" } }
}
enum ShellSpacing: String, CaseIterable {
    case theme = "Theme defaults", compact = "Compact", relaxed = "Relaxed"
    var value: CGFloat? { switch self { case .theme: nil; case .compact: 4; case .relaxed: 24 } }
}
/// App-owned tab identity and per-destination values outlive native tab content.
struct ShellValues: Equatable {
    var selected = ShellDestination.overview
    var spacing = ShellSpacing.theme
    var showNavigation = true
    var markers: [ShellDestination: Int] = [:]
    var selections = 0
    mutating func select(_ id: String) {
        guard let destination = ShellDestination(rawValue: id), destination != selected else { return }
        selected = destination; selections += 1
    }
    mutating func addMarker() { markers[selected, default: 0] += 1 }
}
struct ShellExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: ShellValues
    var body: some View {
        Card {
            SectionHeader("Stacks, separators and shells", subtitle: "Native layouts with caller-owned navigation and feature state.")
            NavLink("Open shell preview", subtitle: "Try tabs, spacing and retained page values") {
                ShellPreview(values: $values, appearance: t.appearance, style: t.materials.style)
            }
        }
        ShellContent(values: $values)
    }
}
struct ShellPreview: View {
    @Binding var values: ShellValues
    let appearance: FoundryAppearance
    let style: FoundryThemeStyle
    private var selection: Binding<String> { Binding(get: { values.selected.rawValue }, set: { values.select($0) }) }
    var body: some View {
        FoundryTheme(appearance: appearance, style: style) {
            AppShell(background: {
                LinearGradient(colors: [FoundryPreset.v1(appearance: appearance).colors.surfaceGround.color,
                                        FoundryPreset.v1(appearance: appearance).colors.accentTint.color],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
            }, content: {
                if values.showNavigation {
                    TabBar(items: ShellDestination.allCases.map { destination in
                        TabItem(id: destination.rawValue, label: destination.rawValue) { Image(systemName: destination.symbol) }
                    }, selectedId: selection) { id in
                        ScrollView { ShellContent(values: $values, destination: ShellDestination(rawValue: id)!).padding(20) }
                    }
                } else {
                    ScrollView { ShellContent(values: $values).padding(20) }
                }
            })
        }.navigationTitle("Shell preview").navigationBarTitleDisplayMode(.inline)
    }
}
struct ShellContent: View {
    @Environment(\.foundry) private var t
    @Binding var values: ShellValues
    var destination: ShellDestination? = nil
    private var page: ShellDestination { destination ?? values.selected }
    var body: some View {
        VerticalStack(spacing: t.space.section) {
            Card(.floating) {
                SelectField("Stack spacing", selection: $values.spacing, options: ShellSpacing.allCases, label: { $0.rawValue })
                ToggleField("Show bottom navigation", isOn: $values.showNavigation)
                SelectField("Preview destination", selection: Binding(get: { values.selected }, set: { values.select($0.rawValue) }),
                            options: ShellDestination.allCases, label: { $0.rawValue })
                Text("Tab changes: \(values.selections)").font(t.typography.caption)
            }
            Card {
                VerticalStack(spacing: values.spacing.value) {
                    SectionHeader("\(page.rawValue) workspace", subtitle: "Each destination keeps its own marker count.")
                    SectionDivider(inset: t.space.inline)
                    Text("\(page.rawValue) markers: \(values.markers[page, default: 0])").font(t.typography.body)
                    ActionButton("Add marker", variant: .secondary) { values.addMarker() }
                    HorizontalStack(spacing: values.spacing.value) {
                        Text("Draft").font(t.typography.caption)
                        SectionDivider(axis: .vertical, inset: 8)
                        Text("Local").font(t.typography.caption)
                    }.frame(height: 44)
                    Text("Stacks use theme spacing by default. The shell host owns scrolling, routes and safe areas.")
                        .font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color)
                }
            }
        }
    }
}
