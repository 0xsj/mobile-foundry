enum ProductProject: String, CaseIterable, Identifiable {
    case projectOne = "project-one", projectTwo = "project-two", projectThree = "project-three"
    var id: String { rawValue }
    var title: String {
        switch self {
        case .projectOne: "Project one"
        case .projectTwo: "Project two"
        case .projectThree: "Project three"
        }
    }
}

/// Selection outlives compact/expanded and material branches. No records are stored.
struct ProductState {
    static let shellTitle = "Workspace"
    private(set) var selectedProject: ProductProject?
    private(set) var detailPresented = false
    var glass: Bool

    mutating func select(_ id: String) {
        guard let project = ProductProject(rawValue: id) else { return }
        selectedProject = project
        detailPresented = true
    }
    mutating func back() { detailPresented = false }
}
