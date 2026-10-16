import Foundation

/// Product inputs stay outside reusable UI. Build branding comes from Product.xcconfig.
struct ProductConfiguration {
    let displayName: String
    static let defaultGlass = true
    static var current: Self {
        Self(displayName: Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String ?? "My Product")
    }
}

enum ProductTab: String, CaseIterable {
    case today, library, activity, account
    var title: String {
        switch self {
        case .today: "Today"
        case .library: "Library"
        case .activity: "Activity"
        case .account: "Account"
        }
    }
    var symbol: String {
        switch self {
        case .today: "house"
        case .library: "books.vertical"
        case .activity: "clock"
        case .account: "person"
        }
    }
}

struct ProductState {
    var selectedTab = ProductTab.today
    var glass: Bool
    mutating func select(_ id: String) {
        guard let tab = ProductTab(rawValue: id) else { return }
        selectedTab = tab
    }
}
