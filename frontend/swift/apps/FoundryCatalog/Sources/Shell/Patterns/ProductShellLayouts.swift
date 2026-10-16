import FoundryUI
import SwiftUI

struct ProductShellLayout: View {
    let pattern: ProductShellPattern
    @Binding var values: ProductShellState
    var body: some View {
        switch pattern {
        case .daily: DailyProductShell(values: $values)
        case .workspace: WorkspaceProductShell(values: $values)
        case .browse: BrowseProductShell(values: $values)
        case .canvas: CanvasProductShell(values: $values)
        case .setup: SetupProductShell(values: $values)
        }
    }
}
private struct DailyProductShell: View {
    @Binding var values: ProductShellState
    @Environment(\.foundry) private var t
    private let symbols = ["house", "books.vertical", "clock", "person"]
    var body: some View {
        TabBar(items: ProductShellState.dailyPages.enumerated().map { index, page in
            TabItem(id: page, label: page) { Image(systemName: symbols[index]) }
        }, selectedId: Binding(get: { values.dailyPage }, set: { values.apply(.daily($0)) })) { page in
            ScrollView {
                VerticalStack(spacing: t.space.section) {
                    SectionHeader(page == "Today" ? "A little momentum" : "Your \(page.lowercased())", subtitle: "A personal space for the things that matter.")
                    Card(.floating) {
                        ShellArtwork(symbol: symbols[ProductShellState.dailyPages.firstIndex(of: page)!], caption: page).frame(height: 150)
                        Text(page == "Today" ? "Make room for a small win." : "Pick up where you left off.").font(t.typography.heading)
                        Text("\(page) check-ins: \(values.checkIns[page, default: 0])").font(t.typography.body)
                        ActionButton("Check in", variant: .primary) { values.apply(.daily(page)); values.apply(.checkIn) }
                    }
                    Card {
                        SectionHeader("At a glance")
                        ListRow("Saved for later", subtitle: "A few things worth returning to.", leading: { Image(systemName: "bookmark") }, trailing: { Text("03") })
                        SectionDivider()
                        ListRow("This week", subtitle: "Small steps add up.", leading: { Image(systemName: "chart.bar") }, trailing: { Text("07") })
                    }
                }.padding(t.space.page)
            }
        }
    }
}
struct WorkspaceProductShell: View {
    @Binding var values: ProductShellState
    @Environment(\.foundry) private var t
    var body: some View {
        // The width budget includes the primitive's token gutter: 280 + 420 = 700.
        SplitPane(detailPresented: values.workspaceDetail, primaryWidth: 280,
                  minimumDetailWidth: 420 - t.space.inline, primary: { _ in
            workspaceList.accessibilityIdentifier("workspace-list-pane")
        }, detail: { mode in
            workspaceDetail(compact: mode == .single).accessibilityIdentifier("workspace-detail-pane")
        })
    }
    private var workspaceList: some View {
        ScrollView {
            VerticalStack(spacing: t.space.section) {
                SectionHeader("Workspace", subtitle: "A clear place for work in progress.")
                Card(.floating) { Text("3 projects").font(t.typography.heading); Text("\(values.completedItems.count) completed").font(t.typography.caption) }
                ForEach(ShellSampleItem.workspace) { item in
                    Card {
                        ActionButton(item.title, variant: values.workspaceItem == item.id ? .secondary : .quiet) { values.apply(.workspace(item.id)) }
                        Text(item.subtitle).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color)
                        if values.completedItems.contains(item.id) { Label("Completed", systemImage: "checkmark.circle").font(t.typography.caption) }
                    }
                }
            }.padding(t.space.page)
        }
    }
    private func workspaceDetail(compact: Bool) -> some View {
        let item = ShellSampleItem.workspace.first { $0.id == values.workspaceItem }!
        return DetailShell(header: {
            VStack(alignment: .leading, spacing: t.space.inline) {
                if compact { ActionButton("Back to projects", variant: .quiet) { values.apply(.workspaceBack) } }
                SectionHeader(item.title, subtitle: "Project overview")
            }.padding(t.space.page)
        }, content: {
            ScrollView {
                VerticalStack(spacing: t.space.section) {
                    Card(.floating) {
                        ShellArtwork(symbol: "square.stack.3d.up", caption: "WORK IN PROGRESS").frame(height: 170)
                        Text(item.subtitle).font(t.typography.heading)
                        Text(values.completedItems.contains(item.id) ? "Project status: complete" : "Project status: in progress").font(t.typography.body)
                    }
                    Card { SectionHeader("Next step"); Text("Review the idea, choose one useful action, and keep the scope small.").font(t.typography.body) }
                }.padding(t.space.page)
            }
        }, actions: {
            Card(.floating) { ActionButton(values.completedItems.contains(item.id) ? "Reopen project" : "Complete project") { values.apply(.toggleDone) } }.padding(t.space.page)
        })
    }
}
private struct BrowseProductShell: View {
    @Binding var values: ProductShellState
    @Environment(\.foundry) private var t
    var body: some View {
        if let item = ShellSampleItem.collections.first(where: { $0.id == values.browseItem }) {
            DetailShell(header: {
                VStack(alignment: .leading, spacing: t.space.inline) {
                    ActionButton("Back to collections", variant: .quiet) { values.apply(.browse(nil)) }
                    SectionHeader(item.title, subtitle: "Collection")
                }.padding(t.space.page)
            }, content: {
                ScrollView {
                    VerticalStack(spacing: t.space.section) {
                        Card(.floating) { ShellArtwork(symbol: "square.grid.2x2", caption: item.title.uppercased()).frame(height: 240) }
                        Card { Text(item.subtitle).font(t.typography.heading); Text("A place to browse, save and return. Adapt this structure to a shop, directory or media library.").font(t.typography.body) }
                    }.padding(t.space.page)
                }
            }, actions: {
                Card(.floating) {
                    Text(values.savedItems.contains(item.id) ? "Collection saved in this preview." : "Keep this collection close.").font(t.typography.caption)
                    ActionButton(values.savedItems.contains(item.id) ? "Unsave collection" : "Save collection") { values.apply(.toggleSaved) }
                }.padding(t.space.page)
            })
        } else {
            ScrollView {
                VerticalStack(spacing: t.space.section) {
                    SectionHeader("Discover something good", subtitle: "Collections for curious days.")
                    SearchField("Find collections", text: $values.browseFilter, clearLabel: "Clear collection search")
                    Text("Saved collections: \(values.savedItems.count)").font(t.typography.caption)
                    let items = ShellSampleItem.collections.filter { values.browseFilter.isEmpty || $0.title.localizedCaseInsensitiveContains(values.browseFilter) }
                    if items.isEmpty { Text("No collections match your search.").font(t.typography.body) }
                    ForEach(items) { item in
                        Card(.floating) {
                            ShellArtwork(symbol: "bookmark", caption: item.title).frame(height: 110)
                            ActionButton("Open \(item.title)", variant: .quiet) { values.apply(.browse(item.id)) }
                            Text(item.subtitle).font(t.typography.caption)
                        }
                    }
                }.padding(t.space.page)
            }
        }
    }
}
private struct CanvasProductShell: View {
    @Binding var values: ProductShellState
    @FocusState private var titleFocused: Bool
    @Environment(\.foundry) private var t
    var body: some View {
        DetailShell(header: {
            SectionHeader("Canvas studio", subtitle: "A focused space to make something.").padding(t.space.page)
        }, content: {
            ScrollView {
                VerticalStack(spacing: t.space.section) {
                    Card(.floating) {
                        ShellCanvasArtwork(layers: values.layers, tool: values.canvasTool).frame(height: 250)
                        Text("Canvas layers: \(values.layers)").font(t.typography.caption)
                    }
                    Card {
                        LabeledTextField("Canvas title", text: $values.canvasTitle, focus: $titleFocused)
                        SelectField("Canvas tool", selection: Binding(get: { values.canvasTool }, set: { values.apply(.tool($0)) }), options: ProductShellState.tools, label: { $0 })
                        Text("Tool: \(values.canvasTool)").font(t.typography.caption)
                        Text("Preview exports: \(values.exports)").font(t.typography.caption)
                    }
                }.padding(t.space.page)
            }
        }, actions: {
            Card(.floating) {
                ActionButton("Add layer", variant: .secondary, enabled: values.layers < 8) { titleFocused = false; values.apply(.addLayer) }
                ActionButton("Preview export") { titleFocused = false; values.apply(.export) }
            }.padding(t.space.page)
        })
    }
}
private struct SetupProductShell: View {
    @Binding var values: ProductShellState
    @FocusState private var nameFocused: Bool
    @Environment(\.foundry) private var t
    private let steps = ["Welcome", "Your focus", "Review"]
    var body: some View {
        DetailShell(header: { SectionHeader("Make it yours", subtitle: "A guided beginning.").padding(t.space.page) }, content: {
            AuthShell(header: {
                StepIndicator(values.setupFinished ? "Setup complete" : "Step \(values.setupStep + 1) of 3", steps: steps.enumerated().map { index, title in
                    let status: StepStatus = values.setupFinished || index < values.setupStep ? .completed : (index == values.setupStep ? .current : .upcoming)
                    return StepItem(id: title, title: title, status: status, stateDescription: status == .completed ? "Completed" : (status == .current ? "Current" : "Upcoming"))
                })
            }, content: {
                Card(.floating) {
                    if values.setupFinished {
                        Label("You're ready to begin", systemImage: "checkmark.circle").font(t.typography.heading)
                        Text("\(values.setupName.isEmpty ? "Your space" : values.setupName) · \(values.setupFocus)").font(t.typography.body)
                    } else if values.setupStep == 0 {
                        SectionHeader("Welcome")
                        LabeledTextField("Your name", text: $values.setupName, help: "Optional in this preview.", focus: $nameFocused)
                    } else if values.setupStep == 1 {
                        SectionHeader("Choose your focus")
                        SelectField("Your focus", selection: Binding(get: { values.setupFocus }, set: { values.apply(.focus($0)) }), options: ProductShellState.focuses, label: { $0 })
                    } else {
                        SectionHeader("A good place to start")
                        Text("Name: \(values.setupName.isEmpty ? "Your space" : values.setupName)")
                        Text("Focus: \(values.setupFocus)")
                    }
                }
            }, footer: { Text("Your choices stay in this local preview.").font(t.typography.caption) })
                .padding(t.space.page)
        }, actions: {
            Card(.floating) {
                if values.setupFinished {
                    ActionButton("Start again", variant: .secondary) { nameFocused = false; values.apply(.resetSetup) }
                } else {
                    if values.setupStep > 0 { ActionButton("Previous step", variant: .quiet) { nameFocused = false; values.apply(.previousStep) } }
                    ActionButton(values.setupStep == 2 ? "Finish setup" : "Continue setup") { nameFocused = false; values.apply(.nextStep) }
                }
            }.padding(t.space.page)
        })
    }
}
private struct ShellArtwork: View {
    let symbol: String
    let caption: String
    @Environment(\.foundry) private var t
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 20).fill(LinearGradient(colors: [t.colors.accentTint.color, t.colors.surfaceGround.color], startPoint: .topLeading, endPoint: .bottomTrailing))
            VStack(spacing: t.space.stack) {
                Image(systemName: symbol).font(.system(size: 44, weight: .light)).foregroundStyle(t.colors.accent.color)
                Text(caption).font(t.typography.caption).foregroundStyle(t.colors.inkSecondary.color)
            }
        }.accessibilityHidden(true)
    }
}
private struct ShellCanvasArtwork: View {
    let layers: Int
    let tool: String
    @Environment(\.foundry) private var t
    var body: some View {
        GeometryReader { proxy in
            ZStack {
                RoundedRectangle(cornerRadius: 20).fill(t.colors.surfaceGround.color)
                ForEach(0..<layers, id: \.self) { index in
                    RoundedRectangle(cornerRadius: tool == "Draw" ? 60 : 18)
                        .fill(t.colors.accent.color.opacity(0.15 + Double(index % 3) * 0.12))
                        .frame(width: proxy.size.width * 0.5, height: 90)
                        .rotationEffect(.degrees(tool == "Arrange" ? Double(index) * 12 : -12))
                        .offset(x: CGFloat(index % 3 - 1) * 25, y: CGFloat(index % 4 - 2) * 28)
                }
            }.frame(maxWidth: .infinity, maxHeight: .infinity).clipped()
        }.accessibilityElement(children: .ignore).accessibilityLabel("Canvas preview with \(layers) layers")
    }
}
