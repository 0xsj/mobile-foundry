import Foundation

enum ProductShellPattern: String, CaseIterable, Identifiable {
    case daily, workspace, browse, canvas, setup
    var id: String { rawValue }
    var title: String {
        switch self { case .daily: "Daily app"; case .workspace: "Workspace"; case .browse: "Browse and collect"; case .canvas: "Canvas studio"; case .setup: "Guided setup" }
    }
    var subtitle: String {
        switch self {
        case .daily: "A personal dashboard with four independent tabs."
        case .workspace: "A list and detail workspace that expands with the window."
        case .browse: "Search a collection, open details and keep favorites."
        case .canvas: "A focused canvas with tools, an inspector and actions."
        case .setup: "A short setup flow with a draft, review and completion."
        }
    }
    var symbol: String {
        switch self { case .daily: "house"; case .workspace: "sidebar.left"; case .browse: "square.grid.2x2"; case .canvas: "square.and.pencil"; case .setup: "list.number" }
    }
}
struct ShellSampleItem: Identifiable, Equatable {
    let id: String; let title: String; let subtitle: String
    static let workspace: [Self] = [
        .init(id: "brief", title: "Project brief", subtitle: "Shape an idea into a clear next step."),
        .init(id: "research", title: "Research board", subtitle: "Collect references and early findings."),
        .init(id: "release", title: "Release checklist", subtitle: "Keep a small launch moving forward.")]
    static let collections: [Self] = [
        .init(id: "coast", title: "Coastal walks", subtitle: "A quiet collection for time outdoors."),
        .init(id: "city", title: "City notebooks", subtitle: "Small observations from everyday places."),
        .init(id: "studio", title: "Studio objects", subtitle: "Useful forms, textures and materials.")]
}
enum ProductShellAction {
    case daily(String), checkIn, workspace(String), workspaceBack, toggleDone
    case browse(String?), toggleSaved, tool(String), addLayer, export
    case focus(String), nextStep, previousStep, resetSetup
}
/// Gallery-owned fixture values. No route, network, file export or durable storage is hidden here.
struct ProductShellState: Equatable {
    static let dailyPages = ["Today", "Library", "Activity", "Account"]
    static let tools = ["Select", "Draw", "Arrange"]
    static let focuses = ["Explore", "Build", "Learn"]
    var dailyPage = "Today"
    var checkIns: [String: Int] = [:]
    var workspaceItem = "brief"
    var workspaceDetail = false
    var completedItems: Set<String> = []
    var browseItem: String?
    var savedItems: Set<String> = []
    var browseFilter = ""
    var canvasTitle = "Untitled canvas"
    var canvasTool = "Select"
    var layers = 1
    var exports = 0
    var setupName = ""
    var setupFocus = "Explore"
    var setupStep = 0
    var setupFinished = false
    mutating func apply(_ action: ProductShellAction) {
        switch action {
        case .daily(let id): if Self.dailyPages.contains(id) { dailyPage = id }
        case .checkIn: checkIns[dailyPage, default: 0] += 1
        case .workspace(let id): if ShellSampleItem.workspace.contains(where: { $0.id == id }) { workspaceItem = id; workspaceDetail = true }
        case .workspaceBack: workspaceDetail = false
        case .toggleDone: if !completedItems.insert(workspaceItem).inserted { completedItems.remove(workspaceItem) }
        case .browse(let id): if id == nil || ShellSampleItem.collections.contains(where: { $0.id == id }) { browseItem = id }
        case .toggleSaved: if let id = browseItem { if !savedItems.insert(id).inserted { savedItems.remove(id) } }
        case .tool(let id): if Self.tools.contains(id) { canvasTool = id }
        case .addLayer: if layers < 8 { layers += 1 }
        case .export: exports += 1
        case .focus(let id): if !setupFinished && Self.focuses.contains(id) { setupFocus = id }
        case .nextStep: if !setupFinished { if setupStep < 2 { setupStep += 1 } else { setupFinished = true } }
        case .previousStep: if !setupFinished && setupStep > 0 { setupStep -= 1 }
        case .resetSetup: setupStep = 0; setupFinished = false; setupName = ""; setupFocus = "Explore"
        }
    }
}
