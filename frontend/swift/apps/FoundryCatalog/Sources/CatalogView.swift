import SwiftUI

struct CatalogView: View {
    var body: some View {
        NavigationStack {
            List {
                Section("Foundation") {
                    Label("Components and patterns", systemImage: "square.grid.2x2")
                    Label("Identity and account", systemImage: "person.crop.circle")
                    Label("Offline and sync", systemImage: "arrow.triangle.2.circlepath")
                }

                Section("Graphics") {
                    Label("GPU effects and 3D", systemImage: "cube.transparent")
                }

                Section {
                    Text("Catalog examples will be added as the reusable capabilities are built.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Mobile Foundry")
        }
    }
}

#Preview {
    CatalogView()
}
