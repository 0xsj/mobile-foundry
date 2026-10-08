import FoundryUI
import OSLog
import SwiftUI

struct FormsCatalogView: View {
    @Environment(\.foundry) private var tokens
    @State private var provider: NotesProvider = .memory
    @State private var scenario: CreateNoteScenario = .success
    @State private var store = CreateNoteStore(creator: CreateNoteComposition.creator(provider: .memory, scenario: .success)) { error in
        Logger(subsystem: "dev.mobilefoundry.catalog", category: "create-note")
            .error("Unexpected create-note error: \(String(reflecting: error), privacy: .private)")
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: tokens.space.section) {
                FoundrySurface(.floating) {
                    VStack(alignment: .leading, spacing: tokens.space.stack) {
                        Picker("Provider", selection: Binding(get: { provider }, set: { value in
                            if store.use(CreateNoteComposition.creator(provider: value, scenario: scenario)) { provider = value }
                        })) { ForEach(NotesProvider.allCases) { Text($0.rawValue).tag($0) } }
                        Picker("Scenario", selection: Binding(get: { scenario }, set: { value in
                            if store.use(CreateNoteComposition.creator(provider: provider, scenario: value)) { scenario = value }
                        })) { ForEach(CreateNoteScenario.allCases) { Text($0.rawValue).tag($0) } }
                        Text("One form, two providers. HTTP responses are injected locally.")
                            .font(tokens.typography.caption).foregroundStyle(tokens.colors.inkSecondary.color)
                    }.frame(maxWidth: .infinity, alignment: .leading)
                        .padding(tokens.space.page).disabled(store.mutation.isSubmitting)
                }
                CreateNoteScreen(store: store)
            }.frame(maxWidth: .infinity, alignment: .leading).padding(tokens.space.page)
        }
        .background { backdrop.ignoresSafeArea() }
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle("Forms and mutations").navigationBarTitleDisplayMode(.inline)
        .onDisappear { store.stop() }
    }

    private var backdrop: some View {
        GeometryReader { geometry in
            ZStack {
                tokens.colors.surfaceGround.color
                if tokens.materials.style == .glass {
                    LinearGradient(colors: [tokens.colors.accent.color.opacity(0.24), .clear,
                                            tokens.colors.info.color.opacity(0.18)],
                                   startPoint: .topLeading, endPoint: .bottomTrailing)
                    RoundedRectangle(cornerRadius: tokens.shape.panel)
                        .fill(tokens.colors.accent.color.opacity(0.22))
                        .frame(width: geometry.size.width * 0.75, height: geometry.size.height * 0.55)
                        .rotationEffect(.degrees(-24))
                        .offset(x: -geometry.size.width * 0.28, y: geometry.size.height * 0.12)
                }
            }.frame(width: geometry.size.width, height: geometry.size.height).clipped()
        }.accessibilityHidden(true).allowsHitTesting(false)
    }
}
