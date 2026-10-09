import FoundryUI
import SwiftUI

struct MediaStudy: Identifiable {
    let id: String
    let title: String
    let subtitle: String
    let colors: [Color]
    static let all = [
        MediaStudy(id: "orbit", title: "Orbit study", subtitle: "Soft rings and a quiet blue palette.",
                   colors: [Color(red: 0.20, green: 0.29, blue: 0.72), Color(red: 0.60, green: 0.44, blue: 0.80)]),
        MediaStudy(id: "field", title: "Field study", subtitle: "Warm light and open space.",
                   colors: [Color(red: 0.74, green: 0.29, blue: 0.25), Color(red: 0.96, green: 0.73, blue: 0.43)]),
        MediaStudy(id: "arc", title: "Arc study", subtitle: "A cool shape against deep ink.",
                   colors: [Color(red: 0.08, green: 0.39, blue: 0.44), Color(red: 0.16, green: 0.20, blue: 0.37)])
    ]
}
struct MediaValues {
    var selectedID = "orbit"
    var ratings: [String: Int] = [:]
    var favorites: Set<String> = []
    var enabled = true
    var used = 0
    var selectedIndex: Int { MediaStudy.all.firstIndex { $0.id == selectedID } ?? 0 }
}

struct MediaExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: MediaValues
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            ToggleField("Enable media controls", isOn: $values.enabled)
            MediaBrowser(values: $values)
            SelectedMediaTile(values: $values)
            NavLink("Open media preview", subtitle: "A separate screen using the same selected study.") {
                MediaPreviewView(values: $values, appearance: t.appearance, style: t.materials.style)
            }
        }
    }
}

private struct StudyArtwork: View {
    let study: MediaStudy
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                LinearGradient(colors: study.colors, startPoint: .topLeading, endPoint: .bottomTrailing)
                Circle().stroke(.white.opacity(0.65), lineWidth: 2)
                    .frame(width: geometry.size.width * 0.60).offset(x: geometry.size.width * 0.10, y: -20)
                Circle().fill(.white.opacity(0.18)).frame(width: geometry.size.width * 0.27)
                    .offset(x: -geometry.size.width * 0.22, y: 25)
            }.frame(width: geometry.size.width, height: geometry.size.height).clipped()
        }
    }
}
private struct MediaBrowser: View {
    @Environment(\.foundry) private var t
    @Binding var values: MediaValues
    private var study: MediaStudy { MediaStudy.all[values.selectedIndex] }
    var body: some View {
        Card {
            SectionHeader("Study collection", subtitle: "Swipe or use the page controls. Local preview only.")
            Carousel(MediaStudy.all, selection: $values.selectedID) { page in
                MediaOverlay {
                    StudyArtwork(study: page)
                } overlay: {
                    VStack(alignment: .leading, spacing: t.space.inline) {
                        Text(page.title).font(t.typography.heading)
                        IconAction(values.favorites.contains(page.id) ? "Remove favorite \(page.title)" : "Favorite \(page.title)",
                                   variant: .secondary, enabled: values.enabled,
                                   action: { toggleFavorite(page.id) }) {
                            Image(systemName: values.favorites.contains(page.id) ? "heart.fill" : "heart")
                        }
                    }
                }
            }.frame(height: 300).clipShape(RoundedRectangle(cornerRadius: t.shape.panel))
                .scrollDisabled(!values.enabled)
            PageIndicator(count: MediaStudy.all.count, selected: values.selectedIndex,
                          label: "Study \(values.selectedIndex + 1) of \(MediaStudy.all.count)")
            Text("Selected: \(study.title)").font(t.typography.label)
            HStack {
                IconAction("Previous study", variant: .secondary, enabled: values.enabled && values.selectedIndex > 0,
                           action: { move(-1) }) { Image(systemName: "chevron.left") }
                IconAction("Next study", variant: .secondary, enabled: values.enabled && values.selectedIndex < MediaStudy.all.count - 1,
                           action: { move(1) }) { Image(systemName: "chevron.right") }
            }
            RatingField("Rate \(study.title)", value: Binding(get: { values.ratings[study.id, default: 0] },
                        set: { values.ratings[study.id] = $0 }),
                        valueLabel: "Rating: \(values.ratings[study.id, default: 0]) of 5", enabled: values.enabled,
                        optionLabel: { "Rate \(study.title) \($0) of 5" })
            ActionButton("Clear study rating", variant: .quiet,
                         enabled: values.enabled && values.ratings[study.id, default: 0] > 0) { values.ratings[study.id] = 0 }
        }
    }
    private func move(_ step: Int) {
        let next = values.selectedIndex + step
        guard values.enabled, MediaStudy.all.indices.contains(next) else { return }
        withAnimation(t.motion.standardAnimation) { values.selectedID = MediaStudy.all[next].id }
    }
    private func toggleFavorite(_ id: String) {
        if values.favorites.contains(id) { values.favorites.remove(id) } else { values.favorites.insert(id) }
    }
}
private struct SelectedMediaTile: View {
    @Binding var values: MediaValues
    private var study: MediaStudy { MediaStudy.all[values.selectedIndex] }
    var body: some View {
        MediaTile(study.title, subtitle: study.subtitle, ratio: 16 / 9) {
            StudyArtwork(study: study).accessibilityHidden(true)
        } actions: {
            Badge(values.favorites.contains(study.id) ? "Favorite study" : "Not favorited")
            ActionButton("Use selected study", variant: .secondary, enabled: values.enabled) { values.used += 1 }
            Text("Used previews: \(values.used)")
        }
    }
}
struct MediaPreviewView: View {
    @Binding var values: MediaValues
    let appearance: FoundryAppearance
    let style: FoundryThemeStyle
    var body: some View {
        FoundryTheme(appearance: appearance, style: style) {
            ScrollView {
                ContentContainer {
                    VStack(spacing: 20) {
                        MediaBrowser(values: $values)
                        SelectedMediaTile(values: $values)
                    }
                }
            }
        }.navigationTitle("Media preview").navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(FoundryPreset.v1(appearance: appearance).colors.surfaceGround.color, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(appearance == .dark ? .dark : .light, for: .navigationBar)
    }
}
