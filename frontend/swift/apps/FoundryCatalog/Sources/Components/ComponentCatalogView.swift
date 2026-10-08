import FoundryUI
import SwiftUI

struct ComponentCatalogView: View {
    @State private var dark = false
    @State private var glass = true
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Toggle("Dark preview", isOn: $dark)
                Toggle("Glass preview", isOn: $glass)
                FoundryTheme(appearance: dark ? .dark : .light, style: glass ? .glass : .solid) {
                    ComponentExamples()
                }
            }.padding(20)
        }.navigationTitle("Components").navigationBarTitleDisplayMode(.inline)
    }
}

private enum ComponentGroup: String, CaseIterable {
    case actions = "Actions", content = "Content", patterns = "Patterns", controls = "Controls", overlays = "Overlays"
    case display = "Display", feedback = "Feedback", collections = "Collections", context = "Context", layout = "Layout", details = "Details", journeys = "Journeys", activity = "Activity"
}

private struct ComponentExamples: View {
    @Environment(\.foundry) private var t
    @State private var group: ComponentGroup = .actions
    @State private var count = 0
    @State private var busy = false
    @State private var search = ""
    @State private var notifications = true
    @State private var quality = "Balanced"
    @State private var showDetails = false
    @State private var confirmDelete = false
    @State private var removed = false
    @State private var controls = ControlValues()
    @State private var overlays = OverlayValues()
    @State private var artwork = false
    @State private var feedback = FeedbackValues()
    @State private var collection = CollectionValues()
    @State private var fields = FieldGroupValues()
    @State private var context = ContextValues()
    @State private var layout = LayoutValues()
    @State private var delivery = DeliveryValues()
    @State private var journey = JourneyValues()
    @State private var activity = ActivityPreviewValues()
    private let projects = ["Atlas workspace", "Orbit study", "Field notes"]

    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            PageHeader("Everyday interfaces", subtitle: "Simple controls, useful compositions, and room for your own content.") {
                Badge("51 building blocks", tone: .info)
            }
            Tabs("Component families", selection: $group, options: ComponentGroup.allCases, label: { $0.rawValue })
            switch group {
            case .actions: actions
            case .content: content
            case .patterns: patterns
            case .controls:
                ControlExamples(values: $controls)
                FieldGroupExample(values: $fields)
            case .overlays: OverlayExamples(values: $overlays)
            case .display: DisplayExamples(artwork: $artwork)
            case .feedback: FeedbackExamples(values: $feedback)
            case .collections: CollectionExamples(values: $collection)
            case .context: ContextExamples(values: $context)
            case .layout: LayoutExamples(values: $layout)
            case .details: DetailsExamples(values: $delivery)
            case .journeys: JourneyExamples(values: $journey)
            case .activity: ActivityExamples(values: activity)
            }
        }
        .padding(t.space.page)
        .background {
            LinearGradient(colors: [t.colors.surfaceGround.color, t.colors.accentTint.color,
                                    t.colors.surfaceGround.color], startPoint: .topLeading, endPoint: .bottomTrailing)
        }
        .clipShape(RoundedRectangle(cornerRadius: t.shape.panel))
        .onChange(of: group) { _, _ in
            feedback.notice = nil; context.help = false; context.options = false
        }
        .sheetPanel("Project details", isPresented: $showDetails, closeLabel: "Close details") {
            Card { ListRow("Atlas workspace", subtitle: "Updated just now") }
                .presentationDetents([.medium, .large])
        }
        .confirmationPrompt("Remove this example item?", message: "This only changes the gallery example.",
                            isPresented: $confirmDelete, confirmLabel: "Remove item", cancelLabel: "Cancel",
                            destructive: true, onConfirm: { removed = true })
    }

    private var actions: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            Card(.floating) {
                Text("Actions").font(t.typography.heading).accessibilityAddTraits(.isHeader)
                ActionButton(variant: .primary, isBusy: busy, action: { count += 1 }) {
                    Label(busy ? "Saving changes…" : "Save changes", systemImage: "checkmark")
                        .frame(maxWidth: .infinity)
                }
                ActionButton("Preview", variant: .secondary) { count += 1 }
                ActionButton("Learn more", variant: .quiet) { count += 1 }
                ActionButton("Unavailable", enabled: false) { count += 1 }
                ActionButton("Remove example", variant: .destructive) { confirmDelete = true }
                Toggle("Show busy state", isOn: $busy)
                Text("Actions performed: \(count)").font(t.typography.caption)
                if removed { Badge("Example item removed", tone: .warning) }
            }
            Card {
                Text("Search a collection").font(t.typography.heading).accessibilityAddTraits(.isHeader)
                SearchField("Search projects", text: $search, clearLabel: "Clear search")
                let matches = projects.filter { search.isEmpty || $0.localizedCaseInsensitiveContains(search) }
                if matches.isEmpty {
                    EmptyState("No matching projects", message: "Try another name or clear your search.",
                               artwork: { Image(systemName: "magnifyingglass").font(.largeTitle) },
                               actions: { ActionButton("Reset search", variant: .secondary) { search = "" } })
                } else {
                    ForEach(matches, id: \.self) { title in ListRow(title, subtitle: "Personal project") }
                }
            }
        }
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            Card {
                Text("Status and rows").font(t.typography.heading).accessibilityAddTraits(.isHeader)
                ViewThatFits(in: .horizontal) {
                    HStack { badges }
                    VStack(alignment: .leading) { badges }
                }
                Divider()
                Button { showDetails = true } label: {
                    ListRow("Atlas workspace", subtitle: "A place for your next idea",
                            leading: { Image(systemName: "square.stack.3d.up").accessibilityHidden(true) },
                            trailing: { Image(systemName: "chevron.right").accessibilityHidden(true) })
                }.buttonStyle(.plain).accessibilityHint("Opens project details")
                Divider()
                ListRow("Storage", subtitle: "This device", leading: { EmptyView() }, trailing: { Badge("Local") })
            }
            InlineAlert("Changes stay on this device", message: "Connect an account when you are ready to share your workspace.") {
                ActionButton("Learn about accounts", variant: .quiet) { showDetails = true }
            }
            InlineAlert("Review before continuing", message: "Some changes still need your attention.", tone: .warning)
            InlineAlert("Could not complete the action", message: "Your draft is still available. Try again when you are ready.", tone: .critical)
            Card {
                ProgressIndicator("Preparing preview", fraction: 0.65)
                ProgressIndicator("Waiting for connection")
            }
            Card {
                EmptyState("Your collection starts here", message: "Add an idea, an image, or a project to make it yours.",
                           artwork: { Image(systemName: "square.stack").font(.largeTitle) },
                           actions: { ActionButton("Create something") { count += 1 } })
            }
        }
    }

    @ViewBuilder private var badges: some View {
        Badge("Draft"); Badge("Ready", tone: .info); Badge("Pending", tone: .warning); Badge("Needs attention", tone: .critical)
    }

    private var patterns: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            SettingsSection("Preferences", footer: "Settings are examples and stay in this gallery.", role: .floating) {
                Toggle("Notifications", isOn: $notifications)
                Divider()
                ListRow("Appearance", subtitle: "Follows your preview settings", leading: { EmptyView() },
                        trailing: { Badge(t.appearance == .dark ? "Dark" : "Light") })
            }
            Text("Preview quality").font(t.typography.heading).accessibilityAddTraits(.isHeader)
            ForEach(["Balanced", "Detailed"], id: \.self) { option in
                SelectionCard(selected: quality == option, onSelect: { quality = option }) {
                    Text(option).font(t.typography.label)
                    Text(option == "Balanced" ? "A lighter preview for everyday work." : "More detail when you need a closer look.")
                        .foregroundStyle(t.colors.inkSecondary.color)
                }
            }
            Text("Selected quality: \(quality)").font(t.typography.caption)
            Card(.floating) {
                PageHeader("Atlas workspace", subtitle: "A page header can carry your own actions.") {
                    ActionButton("Open details", variant: .secondary) { showDetails = true }
                }
            }
        }
    }
}

#Preview { FoundryTheme { NavigationStack { ComponentCatalogView() } } }
