import SwiftUI

struct CatalogView: View {
    @Binding var glassTheme: Bool
    private let onClose: (() -> Void)?
    init(glassTheme: Binding<Bool> = .constant(false), onClose: (() -> Void)? = nil) {
        self._glassTheme = glassTheme
        self.onClose = onClose
    }
    var body: some View {
        NavigationStack {
            List {
                Section("Theme") {
                    Toggle("Glass surfaces", isOn: $glassTheme)
                    Text("Compare glass surfaces in Tokens and Forms and mutations.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("Foundation") {
                    NavigationLink { FormsCatalogView() } label: { Label("Forms and mutations", systemImage: "square.and.pencil") }
                    NavigationLink { TokenCatalogView() } label: { Label("Tokens", systemImage: "paintpalette") }
                    NavigationLink { HealthCatalogView() } label: { Label("HTTP health", systemImage: "network") }
                    NavigationLink { NotesCatalogView() } label: { Label("Notes service seam", systemImage: "note.text") }
                    NavigationLink { QueryCatalogView() } label: { Label("Async UI patterns", systemImage: "square.grid.2x2") }
                    Label("Identity and account", systemImage: "person.crop.circle")
                    Label("Offline and sync", systemImage: "arrow.triangle.2.circlepath")
                }

                Section("Graphics") {
                    NavigationLink { CompositorStudioView() } label: { Label("Compositor studio", systemImage: "square.3.layers.3d") }
                    NavigationLink { ImageStudioView() } label: { Label("Image studio", systemImage: "slider.horizontal.3") }
                    NavigationLink { ProductStudioView() } label: { Label("Product studio", systemImage: "lamp.desk") }
                    NavigationLink { GPUEffectsView() } label: { Label("GPU effects", systemImage: "cube.transparent") }
                }

                Section {
                    Text("Catalog examples will be added as the reusable capabilities are built.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Mobile Foundry")
            .toolbar {
                if let onClose {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Close catalog", action: onClose)
                    }
                }
            }
        }
    }
}

#Preview {
    CatalogView()
}
