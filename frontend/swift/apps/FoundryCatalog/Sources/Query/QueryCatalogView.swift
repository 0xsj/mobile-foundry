import SwiftUI
import FoundryKernel
import FoundryQuery
import FoundryUI

private enum QueryScenario: String, CaseIterable, Identifiable {
    case idle = "Idle"
    case loading = "Loading"
    case content = "Content"
    case empty = "Empty"
    case refreshing = "Refreshing"
    case refreshingEmpty = "Refreshing empty"
    case failure = "Failure"
    case failureContent = "Failure with content"
    case failureEmpty = "Failure with empty"
    case internalFailure = "Internal failure"

    var id: Self { self }
    var state: QueryState<String> {
        let content = "Workspace is ready."
        let failure = Failure.unavailable(.init(message: "Workspace is temporarily unavailable."))
        switch self {
        case .idle: return .idle
        case .loading: return .loading(previous: nil)
        case .content: return .loaded(content)
        case .empty: return .loaded("")
        case .refreshing: return .loading(previous: content)
        case .refreshingEmpty: return .loading(previous: "")
        case .failure: return .failed(failure, previous: nil)
        case .failureContent: return .failed(failure, previous: content)
        case .failureEmpty: return .failed(failure, previous: "")
        case .internalFailure: return .failed(.internalError(.init(message: "Private diagnostic detail")), previous: nil)
        }
    }

    var starting: Self {
        guard let value = state.value else { return .loading }
        return value.isEmpty ? .refreshingEmpty : .refreshing
    }
    var restored: Self {
        guard let value = state.value else { return .idle }
        return value.isEmpty ? .empty : .content
    }
}

/// A scalar consumer with manually selected states and no service or request owner.
struct QueryCatalogView: View {
    @State private var scenario: QueryScenario = .idle

    var body: some View {
        List {
            Section("Example") {
                Picker("State", selection: $scenario) {
                    ForEach(QueryScenario.allCases) { Text($0.rawValue).tag($0) }
                }
                .accessibilityIdentifier("query-scenario")
                Text("Choose a state. Refresh or Retry starts loading; Cancel restores the last content.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            Section("Workspace") {
                QueryContent(state: scenario.state, copy: .init(
                    idle: "Ready to load workspace.", loading: "Loading workspace…",
                    refreshing: "Refreshing workspace…", empty: "No workspace content."
                ), isEmpty: { $0.isEmpty }, refresh: { scenario = scenario.starting },
                             cancel: { scenario = scenario.restored }) { Text($0) }
            }
        }
        .navigationTitle("Async UI patterns")
    }
}
