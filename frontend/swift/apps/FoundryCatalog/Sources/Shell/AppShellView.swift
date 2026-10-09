import FoundryGraphics
import FoundryUI
import SwiftUI

enum ShellTab: String, CaseIterable, Identifiable {
    case home, library, camera, studio, account
    var id: String { rawValue }
    var title: String {
        switch self {
        case .home: "Home"
        case .library: "Library"
        case .camera: "Camera"
        case .studio: "Studio"
        case .account: "Account"
        }
    }
    var symbol: String {
        switch self {
        case .home: "house"
        case .library: "books.vertical"
        case .camera: "camera"
        case .studio: "square.stack.3d.up"
        case .account: "person"
        }
    }
}

/// Routes and presentation lifetime belong to the app, rather than FoundryUI.
struct AppShellView: View {
    @Binding var glassTheme: Bool
    @SceneStorage("foundry.shell.selectedTab") private var selection = ShellTab.home.rawValue
    @State private var catalogPresented = false
    @State private var cameraPhoto: RasterImage?

    private var selectedTab: Binding<String> {
        Binding(get: { ShellTab(rawValue: selection)?.rawValue ?? ShellTab.home.rawValue },
                set: { if ShellTab(rawValue: $0) != nil { selection = $0 } })
    }

    var body: some View {
        AppShell(background: { Color.clear }, content: {
            TabBar(items: ShellTab.allCases.map { tab in
                TabItem(id: tab.rawValue, label: tab.title) { Image(systemName: tab.symbol) }
            }, selectedId: selectedTab) { id in
                let tab = ShellTab(rawValue: id)!
                NavigationStack {
                    if tab == .camera {
                        CameraView(photo: $cameraPhoto, isSelected: selectedTab.wrappedValue == id && !catalogPresented)
                    } else {
                        ShellPlaceholderView(tab: tab, glassTheme: $glassTheme) { catalogPresented = true }
                    }
                }
            }
        })
        // Native tab chrome owns material, safe areas and accessibility.
        .fullScreenCover(isPresented: $catalogPresented) {
            CatalogView(glassTheme: $glassTheme, onClose: { catalogPresented = false })
        }
    }
}

private struct ShellPlaceholderView: View {
    @Environment(\.foundry) private var tokens
    let tab: ShellTab
    @Binding var glassTheme: Bool
    let onOpenCatalog: () -> Void

    var body: some View {
        ZStack {
            tokens.colors.surfaceGround.color.ignoresSafeArea()
            GeometryReader { geometry in
                RadialGradient(colors: [tokens.colors.accent.color.opacity(0.12), .clear],
                    center: .bottomTrailing, startRadius: 0,
                    endRadius: max(geometry.size.width, geometry.size.height) * 0.7)
            }.ignoresSafeArea().accessibilityHidden(true)
            VStack(spacing: tokens.space.section) {
                Spacer()
                VStack(spacing: tokens.space.stack) {
                    Image(systemName: tab.symbol)
                        .font(.system(size: 48, weight: .light))
                        .foregroundStyle(tokens.colors.inkMuted.color).accessibilityHidden(true)
                    Text("Nothing here yet.").foregroundStyle(tokens.colors.inkSecondary.color)
                }
                Spacer()
                if tab == .studio {
                    Button("Open catalog", action: onOpenCatalog)
                        .buttonStyle(.borderedProminent).controlSize(.large)
                }
                if tab == .account {
                    Surface(.floating) {
                        Toggle("Glass surfaces", isOn: $glassTheme).padding(tokens.space.page)
                    }
                    Text("Compare Solid and Glass in the catalog. The tab bar follows iOS styling.")
                        .font(tokens.typography.caption).foregroundStyle(tokens.colors.inkSecondary.color)
                }
            }.padding(tokens.space.page)
        }.navigationTitle(tab.title)
    }
}

#Preview {
    FoundryTheme(style: .glass) { AppShellView(glassTheme: .constant(true)) }
}
