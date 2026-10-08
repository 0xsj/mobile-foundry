import SwiftUI
import OSLog

struct NotesCatalogView: View {
    @State private var provider: NotesProvider = .memory
    @State private var scenario: NotesScenario = .content

    var body: some View {
        List {
            Section("Source") {
                Picker("Provider", selection: $provider) {
                    ForEach(NotesProvider.allCases) { Text($0.rawValue).tag($0) }
                }
                Picker("Scenario", selection: $scenario) {
                    ForEach(NotesScenario.allCases) { Text($0.rawValue).tag($0) }
                }
                Text("Memory and HTTP use the same screen. HTTP responses are injected locally.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            NotesExample(provider: provider, scenario: scenario)
                .id("\(provider.rawValue)-\(scenario.rawValue)")
        }
        .navigationTitle("Notes service seam")
    }
}

private struct NotesExample: View {
    @State private var store: NotesStore
    @State private var generation = 0

    init(provider: NotesProvider, scenario: NotesScenario) {
        _store = State(initialValue: NotesStore(service: NotesComposition.service(provider: provider, scenario: scenario)) { error in
            Logger(subsystem: "dev.mobilefoundry.catalog", category: "notes")
                .error("Unexpected notes error: \(String(reflecting: error), privacy: .private)")
        })
    }

    var body: some View {
        NotesScreen(state: store.state, refresh: { generation += 1 }, cancel: { store.cancel() })
            .task(id: generation) { await store.load() }
            .onDisappear { store.cancel() }
    }
}
