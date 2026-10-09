@testable import FoundryCatalog
import FoundryUI
import Observation
import SwiftUI
import Testing

@Test func timelineAdmissionPreservesTrackPositionsAndSeparatesTransportFromMetadata() {
    var values = PlaybackValues(); let initial = values
    values.select("missing"); values.select("night"); values.move(-1); values.move(3); values.seek(.nan); values.seek(.infinity); values.advance()
    #expect(values == initial && values.neighbor(-1) == nil)
    values.toggle(); values.advance(); values.chooseSpeed(.fast); values.advance()
    #expect(values.position == 25 && values.isPlaying && values.advances == 2)
    values.seek(45); values.select("orbit")
    #expect(values.position == 0 && !values.isPlaying && values.neighbor(1) == nil)
    values.toggle(); values.advance(); values.move(-1)
    #expect(values.selectedID == "coast" && values.position == 45 && values.positions["orbit"] == 15 && !values.isPlaying)
    values.seek(1000); #expect(values.position == 92 && !values.isPlaying)
    values.toggle(); #expect(values.position == 0 && values.isPlaying)
    values.setRepeat(true); values.seek(90); values.advance()
    #expect(values.position == 13 && values.isPlaying && values.timeLabel == "0:13 of 1:32")
    let looped = values; values.advance(.greatestFiniteMagnitude); values.advance(-10); values.advance(0)
    #expect(values == looped)
    values.chooseScenario(.buffering); let buffering = values
    values.toggle(); values.seek(20); values.advance(); values.move(1); values.select("orbit"); values.chooseSpeed(.normal); values.setRepeat(false)
    #expect(values == buffering && !values.isPlaying)
    values.favorite("coast"); values.favorite("missing"); values.favorite("night")
    #expect(values.favorites == ["coast"])
    values.chooseScenario(.failed); values.retry()
    #expect(values.scenario == .ready && values.position == 13 && !values.isPlaying && values.repeatTrack)
    values.chooseSpeed(.slow); values.toggle(); values.advance()
    #expect(values.position == 20.5 && values.timeLabel == "0:20 of 1:32")
    values.setEnabled(false); let disabled = values
    values.select("orbit"); values.toggle(); values.seek(0); values.advance(); values.move(1); values.favorite("coast")
    values.chooseSpeed(.double); values.setRepeat(false); values.chooseScenario(.failed); values.retry(); values.setEmpty(true); values.resetPlayer()
    #expect(values == disabled && !values.isPlaying)
    values.setEnabled(true); values.setEmpty(true); let empty = values
    values.select("orbit"); values.toggle(); values.seek(0); values.advance(); values.move(1); values.favorite("coast")
    values.chooseSpeed(.double); values.setRepeat(false); values.chooseScenario(.failed); values.retry()
    #expect(values == empty && values.current == nil)
    values.setEmpty(false); #expect(values.position == 20.5 && !values.isPlaying && values.favorites == ["coast"])
    let count = values.advances; values.resetPlayer()
    #expect(values.positions.isEmpty && values.selectedID == "coast" && values.speed == .normal && !values.repeatTrack)
    #expect(values.favorites == ["coast"] && values.advances == count && values.trackChanges == 2)
    var ended = PlaybackValues(); ended.seek(90); ended.toggle(); ended.advance(); ended.advance()
    #expect(ended.position == 92 && !ended.isPlaying && ended.advances == 1)
    ended.toggle(); #expect(ended.position == 0 && ended.isPlaying)
}
@Observable @MainActor private final class PlaybackProbeValues {
    var textSize: DynamicTypeSize = .large
    var direction: LayoutDirection = .leftToRight
    var frames: [String: CGRect] = [:]
}
private struct PlaybackFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue(), uniquingKeysWith: { _, new in new }) }
}
private extension View {
    func playbackFrame(_ id: String) -> some View {
        background { GeometryReader { proxy in Color.clear.preference(key: PlaybackFrames.self, value: [id: proxy.frame(in: .named("playback-probe"))]) } }
    }
}
private struct PlaybackProbe: View {
    @Bindable var values: PlaybackProbeValues
    var body: some View {
        FoundryTheme {
            ScrollView {
                NowPlayingCard("A long recording title for a quiet afternoon", detail: "A supplied creator and collection", accessibilityLabel: "Supplied track identity", artworkSize: 64,
                               artwork: { HStack { Image(systemName: "music.note").frame(width: 24, height: 24).playbackFrame("mark"); Spacer() } }, timeline: {
                    ValueSlider("Playback position", value: .constant(45), in: 0...92, valueLabel: "0:45 of 1:32").playbackFrame("timeline")
                }, controls: {
                    PlaybackControls(isPlaying: false, previousLabel: "Previous track", toggleLabel: "Play preview", nextLabel: "Next track", stateLabel: "Paused",
                                     onPrevious: {}, onToggle: {}, onNext: {}).playbackFrame("controls")
                }, actions: { ActionButton("Favorite preview", variant: .quiet) {}.playbackFrame("favorite") }).playbackFrame("card")
            }.frame(width: 240).coordinateSpace(name: "playback-probe")
        }.environment(\.dynamicTypeSize, values.textSize).environment(\.layoutDirection, values.direction)
            .onPreferenceChange(PlaybackFrames.self) { values.frames = $0 }
    }
}
@MainActor @Test func nowPlayingIdentityAndTimelineGrowInsideNarrowRTLCardBounds() async throws {
    let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
    let previous = scene.keyWindow
    let window = UIWindow(windowScene: scene); window.frame = CGRect(x: 0, y: 0, width: 430, height: 3000)
    let values = PlaybackProbeValues(); let host = UIHostingController(rootView: PlaybackProbe(values: values))
    window.rootViewController = host; window.makeKeyAndVisible()
    defer { window.isHidden = true; window.rootViewController = nil; previous?.makeKey() }
    func settle(_ predicate: () -> Bool) async throws {
        for _ in 0..<100 { host.view.layoutIfNeeded(); if predicate() { return }; try await Task.sleep(for: .milliseconds(20)) }
        Issue.record("Playback geometry did not settle: \(values.frames)")
    }
    try await settle { values.frames.count == 5 }
    let normalCard = try #require(values.frames["card"]), normalTimeline = try #require(values.frames["timeline"])
    values.textSize = .accessibility3
    try await settle { (values.frames["card"]?.height ?? 0) > normalCard.height + 80 && (values.frames["timeline"]?.height ?? 0) > normalTimeline.height + 40 }
    for id in ["card", "timeline", "controls", "favorite"] { #expect((values.frames[id]?.width ?? 1000) <= 241) }
    let favorite = try #require(values.frames["favorite"])
    #expect(favorite.height >= 44 && favorite.width >= 44 && favorite.minX >= normalCard.minX - 1 && favorite.maxX <= normalCard.maxX + 1)
    let mark = try #require(values.frames["mark"]); values.direction = .rightToLeft
    try await settle { (values.frames["mark"]?.minX ?? 0) > mark.minX + 150 }
}
