import Foundation

enum ProductCollection: String, CaseIterable, Identifiable {
    case collectionOne = "collection-one", collectionTwo = "collection-two", collectionThree = "collection-three"
    var id: String { rawValue }
    var title: String {
        switch self {
        case .collectionOne: "Collection one"
        case .collectionTwo: "Collection two"
        case .collectionThree: "Collection three"
        }
    }
}

/// Search and material outlive the list/detail branch. No saved collection is implied.
struct ProductState {
    static let shellTitle = "Browse"
    private(set) var selectedCollection: ProductCollection?
    var filter = ""
    var glass: Bool

    var visibleCollections: [ProductCollection] {
        let query = filter.trimmingCharacters(in: .whitespacesAndNewlines)
        return ProductCollection.allCases.filter { query.isEmpty || $0.title.localizedCaseInsensitiveContains(query) }
    }
    mutating func select(_ id: String) {
        guard let collection = ProductCollection(rawValue: id) else { return }
        selectedCollection = collection
    }
    mutating func back() { selectedCollection = nil }
}
