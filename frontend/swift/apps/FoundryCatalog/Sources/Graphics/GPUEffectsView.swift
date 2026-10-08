import FoundryGraphics
import FoundryKernel
import FoundryUI
import OSLog
import SwiftUI
import Foundation

struct GPUEffectsView: View {
    @Environment(\.foundry) private var tokens
    @Environment(\.scenePhase) private var scenePhase
    @State private var effect = EffectKind.ripple
    @State private var quality = EffectQuality.balanced
    @State private var strength = 0.65
    @State private var point = EffectPoint()
    @State private var animate = true
    @State private var reduced = false
    @State private var visible = false
    @State private var backend = "Starting GPU…"
    @State private var statistics: EffectStatistics?
    @State private var failure: Failure?
    @State private var attempt = 0
    @State private var progress = 0.5
    @State private var playhead = 0.32
    @State private var replaying = false
    @State private var replayGeneration = 0
    @State private var samples = fieldFixture(trail: false)

    private var running: Bool { visible && scenePhase == .active && animate && !reduced && !tokens.motion.reduced }
    private var ambient: Bool { running && effect != .particles && effect != .field }
    private var replayActive: Bool { running && replaying && effect == .particles }
    private struct ReplayKey: Equatable { let active: Bool; let quality: EffectQuality; let generation: Int }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: tokens.space.section) {
                Text(effect.useCase)
                    .foregroundStyle(tokens.colors.inkSecondary.color)
                if let failure {
                    Surface {
                        VStack(alignment: .leading, spacing: tokens.space.stack) {
                            Label(failure.publicInfo().meta.message, systemImage: "exclamationmark.circle")
                            Button("Try again") { self.failure = nil; backend = "Starting GPU…"; statistics = nil; attempt += 1 }
                        }.padding(tokens.space.page)
                    }
                } else {
                    GeometryReader { geometry in
                        MetalEffectSurface(settings: .init(effect: effect, quality: quality, strength: Float(strength), point: point,
                                                           progress: Float(effect == .particles ? playhead : progress), samples: samples),
                                           running: ambient, onPoint: { point = $0 }, onEvent: receive,
                                           onUnexpectedError: { error in
                            Logger(subsystem: "dev.mobilefoundry.catalog", category: "graphics").error("GPU error: \(String(reflecting: error), privacy: .private)")
                        })
                        .frame(width: geometry.size.width, height: geometry.size.height)
                        .highPriorityGesture(DragGesture(minimumDistance: 0).onChanged { value in
                            point = .init(x: Float(value.location.x / max(1, geometry.size.width)),
                                          y: Float(value.location.y / max(1, geometry.size.height)))
                        })
                    }.aspectRatio(1.2, contentMode: .fit).id(attempt)
                }
                Surface {
                    VStack(alignment: .leading, spacing: tokens.space.stack) {
                        Picker("Effect", selection: $effect) {
                            ForEach(EffectKind.allCases, id: \.self) { Text($0.title).tag($0) }
                        }.pickerStyle(.menu)
                        if effect == .liquid {
                            Text("Progress: \(Int((progress * 100).rounded()))% — supplied by the feature")
                                .font(tokens.typography.label)
                            Slider(value: $progress, in: 0...1).accessibilityLabel("Progress")
                            HStack {
                                Button("Empty") { progress = 0 }
                                Button("Half") { progress = 0.5 }
                                Button("Full") { progress = 1 }
                            }.buttonStyle(.bordered)
                        }
                        if effect == .particles {
                            Text("Playhead: \(Int((playhead * 100).rounded()))% · \(replaying ? (running ? "Playing" : "Paused") : (playhead == 1 ? "Completed" : "Static preview"))")
                                .font(tokens.typography.label)
                            Slider(value: Binding(get: { playhead }, set: { replaying = false; playhead = $0 }), in: 0...1)
                                .accessibilityLabel("Celebration playhead")
                            Button("Replay celebration") { playhead = 0; replayGeneration += 1; replaying = true }.buttonStyle(.bordered).disabled(!running)
                            Text("One 2.4-second burst. Enable animation to replay, or scrub a static frame.")
                                .font(tokens.typography.caption)
                        }
                        if effect == .field {
                            Text("Samples: \(samples.count)/\(EffectSettings.maximumFieldSamples) · illustrative density")
                                .font(tokens.typography.label)
                            HStack {
                                Button("Clusters") { samples = fieldFixture(trail: false) }
                                Button("Trail") { samples = fieldFixture(trail: true) }
                                Button("Clear data") { samples = [] }
                            }.buttonStyle(.bordered)
                            Button("Add at focus") { samples.append(.init(point: point)) }
                                .buttonStyle(.bordered).disabled(samples.count >= EffectSettings.maximumFieldSamples)
                        }
                        Picker("Quality", selection: $quality) {
                            Text("Economy").tag(EffectQuality.economy); Text("Balanced").tag(EffectQuality.balanced)
                        }.pickerStyle(.segmented)
                        Text("Strength").font(tokens.typography.label)
                        Slider(value: $strength, in: 0...1).accessibilityLabel("Effect strength")
                        Toggle("Animate", isOn: $animate)
                        Toggle("Reduce motion preview", isOn: $reduced)
                        Button("Reset focus") { point = .init() }.buttonStyle(.bordered)
                        Text(ambient || replayActive ? "Animation running" : "Animation paused").font(tokens.typography.label)
                        Text("Focus: \(point.x.formatted(.number.precision(.fractionLength(2)))), \(point.y.formatted(.number.precision(.fractionLength(2))))")
                            .font(tokens.typography.caption.monospaced())
                    }.padding(tokens.space.page)
                }
                Text(backend).font(tokens.typography.caption)
                if let statistics {
                    Text("Submitted: \(statistics.submittedFrames) · Target: \(statistics.width) × \(statistics.height)")
                        .font(tokens.typography.caption.monospaced())
                    if effect == .particles || effect == .field {
                        Text("Redraws follow input changes · Budget: \(quality.framesPerSecond)/s").font(tokens.typography.caption)
                    } else {
                        Text("Submission rate: \(statistics.submissionsPerSecond.formatted(.number.precision(.fractionLength(0))))/s · Budget: \(quality.framesPerSecond)/s")
                            .font(tokens.typography.caption.monospaced())
                    }
                }
                Text("Submission counters describe this canvas. Simulator results are not device GPU benchmarks.")
                    .font(tokens.typography.caption).foregroundStyle(tokens.colors.inkSecondary.color)
            }.frame(maxWidth: .infinity, alignment: .leading).padding(tokens.space.page)
        }
        .background(tokens.colors.surfaceGround.color)
        .navigationTitle("GPU effects").navigationBarTitleDisplayMode(.inline)
        .onAppear { visible = true }.onDisappear { visible = false }
        .onChange(of: effect) { _, _ in replaying = false }
        .task(id: ReplayKey(active: replayActive, quality: quality, generation: replayGeneration)) {
            guard replayActive else { return }
            // Feature event time drives the supplied playhead; the GPU canvas draws on change.
            var clock = EffectClock()
            let start = playhead
            let generation = replayGeneration
            while !Task.isCancelled && replaying && replayGeneration == generation && effect == .particles && playhead < 1 {
                playhead = min(1, start + clock.frame(at: ProcessInfo.processInfo.systemUptime, running: true) / 2.4)
                do { try await Task.sleep(for: .milliseconds(1000 / quality.framesPerSecond)) }
                catch { return }
            }
            if !Task.isCancelled && replayGeneration == generation { replaying = false }
        }
    }
    private func receive(_ event: EffectEvent) {
        switch event {
        case .ready(let name): backend = name
        case .statistics(let value): statistics = value
        case .failed(let value): failure = value
        }
    }
}

private extension EffectKind {
    var title: String { rawValue.capitalized }
    var useCase: String {
        switch self {
        case .ripple: "Ripple · touch feedback and interactive wave fields. Drag to move the focus."
        case .orbit: "Orbit · procedural 3D hero. Drag to steer the lit sphere and ring."
        case .flow: "Flow · a quiet backdrop for music, wellness or onboarding. Drag to shift the ribbons."
        case .material: "Material · a reflective membership or collectible card. Drag to steer its tilt and light."
        case .liquid: "Liquid · uploads, timers or goals. Progress is supplied; animation only moves the surface."
        case .particles: "Particles · a finite achievement or purchase celebration. Drag to choose its origin; replay or scrub."
        case .field: "Field · density for fitness, analytics or availability. Drag to choose where to add a sample. Fixtures are illustrative."
        }
    }
}

private func fieldFixture(trail: Bool) -> [EffectFieldSample] {
    let points: [(Float, Float, Float)] = trail
        ? [(0.15, 0.75, 0.3), (0.3, 0.6, 0.5), (0.45, 0.52, 0.8), (0.6, 0.4, 1), (0.76, 0.28, 0.65), (0.85, 0.2, 0.3)]
        : [(0.3, 0.35, 1), (0.4, 0.45, 0.75), (0.72, 0.65, 1), (0.65, 0.72, 0.6)]
    return points.map { .init(point: .init(x: $0.0, y: $0.1), weight: $0.2) }
}
