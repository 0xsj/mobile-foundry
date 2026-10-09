import FoundryUI
import SwiftUI

enum PlaybackScenario: String, CaseIterable { case ready = "Ready", buffering = "Buffering", failed = "Failed" }
enum PlaybackSpeed: String, CaseIterable {
    case slow = "0.75×", normal = "1×", fast = "1.5×", double = "2×"
    var multiplier: Double { switch self { case .slow: 0.75; case .normal: 1; case .fast: 1.5; case .double: 2 } }
}
struct PreviewTrack: Identifiable {
    let id: String; let title: String; let creator: String; let duration: Double; let symbol: String
    var available = true
    static let all = [
        Self(id: "coast", title: "Coastline study", creator: "Mira Chen", duration: 92, symbol: "water.waves"),
        Self(id: "orbit", title: "Orbit session", creator: "Studio sketches", duration: 146, symbol: "circle.hexagongrid"),
        Self(id: "night", title: "Night walk", creator: "Field recordings", duration: 75, symbol: "moon", available: false)
    ]
    static func find(_ id: String) -> PreviewTrack? { all.first { $0.id == id } }
}
/// Admitted local timeline values. No audio engine, automatic clock or OS media-session effects.
struct PlaybackValues: Equatable {
    var selectedID = "coast"
    var positions: [String: Double] = [:]
    var favorites: [String] = []
    var isPlaying = false
    var speed = PlaybackSpeed.normal
    var repeatTrack = false
    var scenario = PlaybackScenario.ready
    var enabled = true
    var empty = false
    var trackChanges = 0
    var seeks = 0
    var advances = 0
    var current: PreviewTrack? { empty ? nil : PreviewTrack.find(selectedID).flatMap { $0.available ? $0 : nil } }
    var position: Double { positions[selectedID, default: 0] }
    var canSelect: Bool { enabled && !empty && scenario == .ready }
    var canInteract: Bool { canSelect && current != nil }
    var canAdvance: Bool { canInteract && isPlaying }
    var stateLabel: String { switch scenario { case .ready: isPlaying ? "Playing" : "Paused"; case .buffering: "Buffering"; case .failed: "Unavailable" } }
    var timeLabel: String { "\(Self.time(position)) of \(Self.time(current?.duration ?? 0))" }
    private var available: [PreviewTrack] { PreviewTrack.all.filter(\.available) }
    func neighbor(_ step: Int) -> PreviewTrack? {
        guard canInteract && (step == -1 || step == 1), let index = available.firstIndex(where: { $0.id == selectedID }), available.indices.contains(index + step) else { return nil }
        return available[index + step]
    }
    mutating func select(_ id: String) {
        guard canSelect && PreviewTrack.find(id)?.available == true && id != selectedID else { return }
        selectedID = id; isPlaying = false; trackChanges += 1
    }
    mutating func move(_ step: Int) { if let next = neighbor(step) { select(next.id) } }
    mutating func toggle() {
        guard canInteract, let current else { return }
        if !isPlaying && position >= current.duration { positions[selectedID] = 0 }
        isPlaying.toggle()
    }
    mutating func seek(_ value: Double) {
        guard canInteract && value.isFinite, let current else { return }
        let admitted = min(current.duration, max(0, value))
        guard admitted != position else { return }
        positions[selectedID] = admitted; seeks += 1
        if admitted == current.duration { isPlaying = false }
    }
    mutating func advance(_ seconds: Double = 10) {
        guard canAdvance && seconds.isFinite && seconds > 0, let current else { return }
        let target = position + seconds * speed.multiplier
        guard target.isFinite else { return }
        if target >= current.duration {
            positions[selectedID] = repeatTrack ? target.truncatingRemainder(dividingBy: current.duration) : current.duration
            if !repeatTrack { isPlaying = false }
        } else { positions[selectedID] = target }
        advances += 1
    }
    mutating func chooseSpeed(_ value: PlaybackSpeed) { guard canInteract else { return }; speed = value }
    mutating func setRepeat(_ value: Bool) { guard canInteract else { return }; repeatTrack = value }
    mutating func chooseScenario(_ value: PlaybackScenario) { guard enabled && !empty else { return }; scenario = value; if value != .ready { isPlaying = false } }
    mutating func retry() { guard enabled && !empty && scenario == .failed else { return }; scenario = .ready }
    mutating func setEnabled(_ value: Bool) { enabled = value; if !value { isPlaying = false } }
    mutating func setEmpty(_ value: Bool) { guard enabled else { return }; empty = value; if value { isPlaying = false } }
    mutating func favorite(_ id: String) {
        guard enabled && !empty && PreviewTrack.find(id)?.available == true else { return }
        if favorites.contains(id) { favorites.removeAll { $0 == id } } else { favorites.append(id) }
    }
    mutating func resetPlayer() {
        guard enabled else { return }; selectedID = "coast"; positions = [:]; isPlaying = false; speed = .normal; repeatTrack = false; scenario = .ready; empty = false
    }
    static func time(_ value: Double) -> String { let whole = Int(value.rounded(.down)); return String(format: "%d:%02d", whole / 60, whole % 60) }
}
struct PlaybackExamples: View {
    @Environment(\.foundry) private var t
    @Binding var values: PlaybackValues
    var body: some View {
        Card {
            SectionHeader("Playback and media state", subtitle: "Independent transport controls, timeline and media identity.")
            NavLink("Open playback preview", subtitle: "Try a local timeline, queue and recovery states") {
                PlaybackPreview(values: $values, appearance: t.appearance, style: t.materials.style)
            }
        }
        PlaybackContent(values: $values)
    }
}
struct PlaybackPreview: View {
    @Binding var values: PlaybackValues
    let appearance: FoundryAppearance; let style: FoundryThemeStyle
    var body: some View {
        FoundryTheme(appearance: appearance, style: style) { ScrollView { PlaybackContent(values: $values).padding(20) } }
            .navigationTitle("Playback preview").navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(FoundryPreset.v1(appearance: appearance).colors.surfaceGround.color, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar).toolbarColorScheme(appearance == .dark ? .dark : .light, for: .navigationBar)
    }
}
struct PlaybackContent: View {
    @Environment(\.foundry) private var t
    @Binding var values: PlaybackValues
    var body: some View {
        VStack(alignment: .leading, spacing: t.space.section) {
            Card(.floating) {
                ToggleField("Enable playback actions", isOn: Binding(get: { values.enabled }, set: { values.setEnabled($0) }))
                ToggleField("Show empty queue", isOn: Binding(get: { values.empty }, set: { values.setEmpty($0) }), enabled: values.enabled)
                SelectField("Playback scenario", selection: Binding(get: { values.scenario }, set: { values.chooseScenario($0) }), options: PlaybackScenario.allCases,
                            enabled: values.enabled && !values.empty, label: { $0.rawValue })
                Text("Local timeline preview. Advance time manually; no audio is played.").font(t.typography.caption)
                ActionButton("Reset player", variant: .quiet, enabled: values.enabled) { values.resetPlayer() }
                Text("Track changes: \(values.trackChanges) · Seeks: \(values.seeks) · Time steps: \(values.advances)").font(t.typography.caption)
            }
            if let track = values.current {
                NowPlayingCard(track.title, detail: track.creator, accessibilityLabel: "\(track.title), \(track.creator)", artwork: {
                    ZStack {
                        LinearGradient(colors: [t.colors.accent.color, t.colors.accentTint.color], startPoint: .topLeading, endPoint: .bottomTrailing)
                        Image(systemName: track.symbol).resizable().scaledToFit().padding(20).foregroundStyle(t.colors.ink.color)
                    }
                }, timeline: {
                    ValueSlider("Playback position", value: Binding(get: { values.position }, set: { values.seek($0) }), in: 0...track.duration,
                                valueLabel: values.timeLabel, enabled: values.canInteract)
                    if values.scenario == .buffering { ProgressIndicator("Buffering preview") }
                    if values.scenario == .failed {
                        InlineAlert("Playback unavailable", message: "Retry restores this local timeline.", tone: .warning) {
                            ActionButton("Retry playback", variant: .secondary, enabled: values.enabled) { values.retry() }
                        }
                    }
                }, controls: {
                    PlaybackControls(isPlaying: values.isPlaying, previousLabel: "Previous track", toggleLabel: values.isPlaying ? "Pause preview" : "Play preview",
                                     nextLabel: "Next track", stateLabel: values.stateLabel,
                                     previousEnabled: values.neighbor(-1) != nil, toggleEnabled: values.canInteract, nextEnabled: values.neighbor(1) != nil,
                                     onPrevious: { values.move(-1) }, onToggle: { values.toggle() }, onNext: { values.move(1) })
                    Text(values.stateLabel).font(t.typography.caption)
                }, actions: {
                    SelectField("Playback speed", selection: Binding(get: { values.speed }, set: { values.chooseSpeed($0) }), options: PlaybackSpeed.allCases,
                                enabled: values.canInteract, label: { $0.rawValue })
                    ToggleField("Repeat current track", isOn: Binding(get: { values.repeatTrack }, set: { values.setRepeat($0) }), enabled: values.canInteract)
                    ActionButton("Advance 10 seconds", variant: .secondary, enabled: values.canAdvance) { values.advance() }
                    IconAction("\(values.favorites.contains(track.id) ? "Unfavorite" : "Favorite") \(track.title)", enabled: values.enabled && !values.empty,
                               action: { values.favorite(track.id) }) { Image(systemName: values.favorites.contains(track.id) ? "heart.fill" : "heart") }
                })
            } else {
                Card { EmptyState("Nothing queued", message: "Restore the queue or reset the player.", artwork: { Image(systemName: "music.note.list").font(.largeTitle) }, actions: { EmptyView() }) }
            }
            if !values.empty {
                Card {
                    SectionHeader("Preview queue", subtitle: "Each available track remembers its own position.")
                    ForEach(PreviewTrack.all) { track in
                        SelectionCard(selected: values.selectedID == track.id, enabled: values.canSelect && track.available, onSelect: { values.select(track.id) }) {
                            Text(track.title).font(t.typography.label)
                            Text("\(track.creator) · \(PlaybackValues.time(track.duration))\(track.available ? "" : " · Unavailable")").font(t.typography.caption)
                        }
                    }
                }
            }
        }
    }
}
