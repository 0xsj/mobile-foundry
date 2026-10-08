import FoundryGraphics
import FoundryKernel
import FoundryUI
import OSLog
import SwiftUI

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

    private var running: Bool { visible && scenePhase == .active && animate && !reduced && !tokens.motion.reduced }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: tokens.space.section) {
                Text("Drag the canvas to steer waves or rotate the orbit.")
                    .foregroundStyle(tokens.colors.inkSecondary.color)
                if let failure {
                    FoundrySurface {
                        VStack(alignment: .leading, spacing: tokens.space.stack) {
                            Label(failure.publicInfo().meta.message, systemImage: "exclamationmark.circle")
                            Button("Try again") { self.failure = nil; backend = "Starting GPU…"; statistics = nil; attempt += 1 }
                        }.padding(tokens.space.page)
                    }
                } else {
                    GeometryReader { geometry in
                        MetalEffectSurface(settings: .init(effect: effect, quality: quality, strength: Float(strength), point: point),
                                           running: running, onPoint: { point = $0 }, onEvent: receive,
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
                FoundrySurface {
                    VStack(alignment: .leading, spacing: tokens.space.stack) {
                        Picker("Effect", selection: $effect) {
                            Text("Ripple").tag(EffectKind.ripple); Text("Orbit").tag(EffectKind.orbit)
                        }.pickerStyle(.segmented)
                        Picker("Quality", selection: $quality) {
                            Text("Economy").tag(EffectQuality.economy); Text("Balanced").tag(EffectQuality.balanced)
                        }.pickerStyle(.segmented)
                        Text("Strength").font(tokens.typography.label)
                        Slider(value: $strength, in: 0...1).accessibilityLabel("Effect strength")
                        Toggle("Animate", isOn: $animate)
                        Toggle("Reduce motion preview", isOn: $reduced)
                        Button("Reset focus") { point = .init() }.buttonStyle(.bordered)
                        Text(running ? "Animation running" : "Animation paused").font(tokens.typography.label)
                        Text("Focus: \(point.x.formatted(.number.precision(.fractionLength(2)))), \(point.y.formatted(.number.precision(.fractionLength(2))))")
                            .font(tokens.typography.caption.monospaced())
                    }.padding(tokens.space.page)
                }
                Text(backend).font(tokens.typography.caption)
                if let statistics {
                    Text("Submitted: \(statistics.submittedFrames) · Target: \(statistics.width) × \(statistics.height)")
                        .font(tokens.typography.caption.monospaced())
                    Text("Submission rate: \(statistics.submissionsPerSecond.formatted(.number.precision(.fractionLength(0))))/s · Budget: \(quality.framesPerSecond)/s")
                        .font(tokens.typography.caption.monospaced())
                }
                Text("Submission counters describe this canvas. Simulator results are not device GPU benchmarks.")
                    .font(tokens.typography.caption).foregroundStyle(tokens.colors.inkSecondary.color)
            }.frame(maxWidth: .infinity, alignment: .leading).padding(tokens.space.page)
        }
        .background(tokens.colors.surfaceGround.color)
        .navigationTitle("GPU effects").navigationBarTitleDisplayMode(.inline)
        .onAppear { visible = true }.onDisappear { visible = false }
    }
    private func receive(_ event: EffectEvent) {
        switch event {
        case .ready(let name): backend = name
        case .statistics(let value): statistics = value
        case .failed(let value): failure = value
        }
    }
}
