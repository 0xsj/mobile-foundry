import FoundryUI
import SwiftUI

enum WorkspaceDestination: String, CaseIterable {
    case all = "All projects", starred = "Starred", archived = "Archived"
    var id: String { switch self { case .all: "all"; case .starred: "starred"; case .archived: "archived" } }
}
struct WorkspaceProject: Identifiable {
    let id: String; let title: String; let detail: String; let archived: Bool
    static let all = [
        Self(id: "atlas", title: "Atlas workspace", detail: "A place to collect a new idea and shape its first version.", archived: false),
        Self(id: "orbit", title: "Orbit study", detail: "A small study of motion, materials and interaction.", archived: false),
        Self(id: "field", title: "Field notes", detail: "An archived collection of observations from everyday work.", archived: true)
    ]
}
struct WorkspaceValues {
    var destination: WorkspaceDestination = .all
    var selected: String?
    var detailPresented = false
    var starred = Set(["atlas"])
    var forceSingle = false
    var projects: [WorkspaceProject] { WorkspaceProject.all.filter { project in
        switch destination { case .all: !project.archived; case .starred: starred.contains(project.id) && !project.archived; case .archived: project.archived }
    } }
    var project: WorkspaceProject? { projects.first { $0.id == selected } }
    var destinations: [RailDestination] {
        WorkspaceDestination.allCases.map { RailDestination(id: $0.id, label: $0.rawValue) } + [RailDestination(id: "shared", label: "Shared", enabled: false)]
    }
    var breadcrumbs: [BreadcrumbItem] {
        [BreadcrumbItem(id: "workspace", label: "Workspace"), BreadcrumbItem(id: "collection", label: destination.rawValue)] +
        (detailPresented ? project.map { [BreadcrumbItem(id: "project:\($0.id)", label: $0.title)] } ?? [] : [])
    }
    mutating func navigate(_ id: String) {
        guard let destination = WorkspaceDestination.allCases.first(where: { $0.id == id }) else { return }
        self.destination = destination; detailPresented = false
    }
    mutating func open(_ id: String) { guard projects.contains(where: { $0.id == id }) else { return }; selected = id; detailPresented = true }
    mutating func breadcrumb(_ id: String) {
        if id == "workspace" { destination = .all; detailPresented = false }
        else if id == "collection" { detailPresented = false }
    }
    mutating func setStarred(_ value: Bool, id: String) {
        guard WorkspaceProject.all.contains(where: { $0.id == id }) else { return }
        if value { starred.insert(id) } else { starred.remove(id) }
    }
}
struct WorkspaceExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: WorkspaceValues
    var body: some View {
        Card {
            SectionHeader("Adaptive workspaces", subtitle: "Browse projects, trace a path and keep your selection as the layout changes.")
            BreadcrumbTrail(items: values.breadcrumbs, currentAccessibilityLabel: "\(values.breadcrumbs.last!.label), current location") { values.breadcrumb($0) }
            NavLink("Open adaptive workspace", subtitle: "One pane on phones, two when there is room") {
                WorkspacePreview(values: $values, appearance: t.appearance, style: t.materials.style)
            }
        }
        Card(.floating) {
            SectionHeader("Destination rail", subtitle: "The Shared destination is unavailable in this local example.")
            DestinationRail("Projects", destinations: values.destinations, selection: values.destination.id,
                            onSelect: { values.navigate($0) }, icon: { Image(systemName: workspaceSymbol($0.id)) })
                .frame(width: 130, height: 380)
            Text("Current collection: \(values.destination.rawValue)").font(t.typography.caption)
        }
    }
}
private func workspaceSymbol(_ id: String) -> String {
    switch id { case "starred": "star"; case "archived": "archivebox"; case "shared": "person.2"; default: "square.grid.2x2" }
}
struct WorkspacePreview: View {
    @Binding var values: WorkspaceValues
    let appearance: FoundryAppearance
    let style: FoundryThemeStyle
    var body: some View {
        FoundryTheme(appearance: appearance, style: style) {
            WorkspaceContent(values: $values)
        }.navigationTitle("Adaptive workspace").navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(FoundryPreset.v1(appearance: appearance).colors.surfaceGround.color, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(appearance == .dark ? .dark : .light, for: .navigationBar)
    }
}
struct WorkspaceContent: View {
    @Environment(\.foundry) private var t
    @Binding var values: WorkspaceValues
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.inline) {
            ToggleField("Single pane preview", isOn: $values.forceSingle)
            BreadcrumbTrail(items: values.breadcrumbs, currentAccessibilityLabel: "\(values.breadcrumbs.last!.label), current location") { values.breadcrumb($0) }
            SplitPane(detailPresented: values.detailPresented, forceSingle: values.forceSingle, primaryWidth: 350, minimumDetailWidth: 320,
                      primary: { mode in
                HStack(alignment: .top, spacing: t.space.inline) {
                    if mode == .split {
                        DestinationRail("Projects", destinations: values.destinations, selection: values.destination.id,
                                        onSelect: { values.navigate($0) }, icon: { Image(systemName: workspaceSymbol($0.id)) }).frame(width: 110)
                    }
                    ScrollView {
                        VStack(alignment: .leading, spacing: t.space.inline) {
                            if mode == .single {
                                Tabs("Project collections", selection: Binding(get: { values.destination }, set: { values.navigate($0.id) }),
                                     options: WorkspaceDestination.allCases, label: { $0.rawValue })
                            }
                            SectionHeader(values.destination.rawValue, subtitle: "Choose a project to inspect.")
                            if values.projects.isEmpty { EmptyState("No projects here", message: "Star a project from All projects to include it here.") }
                            ForEach(values.projects) { project in
                                SelectionCard(selected: values.selected == project.id, onSelect: { values.open(project.id) }) {
                                    Text(project.title).font(t.typography.label)
                                    Text(project.archived ? "Archived project" : "Personal project").font(t.typography.caption)
                                }
                            }
                        }.padding(2)
                    }.accessibilityIdentifier("workspace-projects")
                }
            }, detail: { mode in
                ScrollView {
                    VStack(alignment: .leading, spacing: t.space.section) {
                        if mode == .single { ActionButton("Back to projects", variant: .secondary) { values.detailPresented = false } }
                        if let project = values.project {
                            Card {
                                SectionHeader(project.title, subtitle: project.archived ? "Archived project" : "Personal project")
                                Text(project.detail)
                                ToggleField("Star this project", isOn: Binding(get: { values.starred.contains(project.id) }, set: { values.setStarred($0, id: project.id) }))
                                KeyValueRow("Storage", value: "This device")
                                Text("Local preview. Your selection and stars stay above the pane layout.").font(t.typography.caption)
                            }
                        } else { Card { EmptyState("Choose a project", message: "Select a project from the current collection.") } }
                    }.padding(2)
                }.accessibilityIdentifier("workspace-detail")
            })
        }.padding(t.space.page)
            .background(LinearGradient(colors: [t.colors.surfaceGround.color, t.colors.accentTint.color, t.colors.surfaceGround.color], startPoint: .topLeading, endPoint: .bottomTrailing))
    }
}
