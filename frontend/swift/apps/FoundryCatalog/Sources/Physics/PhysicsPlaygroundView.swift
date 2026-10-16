import FoundryPhysics
import FoundryUI
import SwiftUI

struct PhysicsPlaygroundView: View {
    @Environment(\.foundry) private var tokens
    @Environment(\.scenePhase) private var scenePhase
    @State private var simulation = ThrowSimulation()
    @State private var target = ThrowTarget.hoop
    @State private var gravity = 9.81
    @State private var bounce = 0.65
    @State private var angle = 60.0
    @State private var speed = 9.9
    @State private var paused = false
    @State private var visible = false
    @State private var aiming = false
    @State private var dragVelocity: PhysicsVector?
    @State private var trail: [PhysicsVector] = []

    private var running: Bool { visible && scenePhase == .active && !paused && simulation.phase == .flying }
    private var controlVelocity: PhysicsVector {
        .init(x: cos(angle * .pi / 180) * speed, y: sin(angle * .pi / 180) * speed)
    }
    private var status: String {
        if simulation.scored { return target == .hoop ? "Basket!" : "On target!" }
        if paused && simulation.phase == .flying { return "Paused" }
        switch simulation.phase {
        case .ready: return "Ready to throw"
        case .flying: return "In flight"
        case .settled: return "Try another throw"
        }
    }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: tokens.space.stack) {
                SectionHeader("Make the shot", subtitle: "Drag from the ball towards the target, then release. A longer drag gives more power.")
                Picker("Target", selection: $target) {
                    Text("Hoop").tag(ThrowTarget.hoop)
                    Text("Landing zone").tag(ThrowTarget.landingZone)
                }.pickerStyle(.segmented).accessibilityIdentifier("physics-target")
                court
                scoreboard
                shotActions
                aimControls
                worldControls
                Text("2D rigid-ball physics · Gravity, rim/backboard collisions and ground friction · Local session scores")
                    .font(tokens.typography.caption).foregroundStyle(tokens.colors.inkSecondary.color)
            }.padding(tokens.space.page)
        }.background(tokens.colors.surfaceGround.color)
            .navigationTitle("Physics playground").navigationBarTitleDisplayMode(.inline)
            .onAppear { visible = true }.onDisappear { visible = false; aiming = false; dragVelocity = nil }
            .onChange(of: target) { _, _ in configure() }
            .onChange(of: gravity) { _, _ in configure() }
            .onChange(of: bounce) { _, _ in configure() }
            .task(id: running) {
                guard running else { return }
                let clock = ContinuousClock()
                var previous = clock.now
                while !Task.isCancelled {
                    do { try await Task.sleep(for: .milliseconds(16)) } catch { return }
                    guard !Task.isCancelled, running else { return }
                    let now = clock.now, duration = previous.duration(to: now).components
                    previous = now
                    simulation.advance(elapsed: Double(duration.seconds) + Double(duration.attoseconds) / 1e18)
                    trail.append(simulation.position)
                    if trail.count > 70 { trail.removeFirst(trail.count - 70) }
                }
            }
    }
    private var court: some View {
        PhysicsCourt(simulation: simulation, trajectory: simulation.trajectory(velocity: dragVelocity ?? controlVelocity), trail: trail)
            .aspectRatio(10.0 / 7.0, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 24))
            .overlay {
                GeometryReader { geometry in
                    Color.clear.contentShape(Rectangle()).gesture(aimGesture(in: geometry.size))
                }
            }
            .accessibilityElement(children: .ignore).accessibilityLabel("Physics court").accessibilityValue(status)
            .accessibilityHint("Use the angle, power and Launch ball controls below as an alternative to dragging.")
            .accessibilityIdentifier("physics-court")
    }
    private func aimGesture(in size: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 0).onChanged { value in
            guard simulation.phase == .ready, size.width > 0, size.height > 0 else { return }
            let ball = CGPoint(x: simulation.position.x / 10 * size.width, y: (7-simulation.position.y) / 7 * size.height)
            if !aiming {
                guard hypot(value.startLocation.x-ball.x, value.startLocation.y-ball.y) <= 36 else { return }
                aiming = true
            }
            dragVelocity = ThrowSimulation.aimVelocity(displacement: .init(
                x: Double(value.translation.width/size.width)*10, y: -Double(value.translation.height/size.height)*7))
        }.onEnded { _ in
            if aiming, let dragVelocity { launch(dragVelocity) }
            aiming = false; dragVelocity = nil
        }
    }
    private var scoreboard: some View {
        HStack {
            Text(status).font(tokens.typography.heading).accessibilityIdentifier("physics-status")
            Spacer()
            Text("\(simulation.scores) / \(simulation.shots)").font(tokens.typography.code)
                .accessibilityLabel("\(simulation.scores) scored out of \(simulation.shots) throws")
                .accessibilityIdentifier("physics-score")
        }
    }
    private var shotActions: some View {
        HStack {
            ActionButton("New throw", variant: .primary) { reset() }.accessibilityIdentifier("physics-reset")
            ActionButton(paused ? "Resume" : "Pause", variant: .secondary) { paused.toggle() }
                .disabled(simulation.phase != .flying).accessibilityIdentifier("physics-pause")
        }
    }
    private var aimControls: some View {
        Card(.floating) {
            Text("Aim without dragging").font(tokens.typography.label)
            Text("Angle \(Int(angle))° · Power \(speed, specifier: "%.1f") m/s").font(tokens.typography.caption)
            Slider(value: $angle, in: 20...80, step: 1).accessibilityLabel("Launch angle")
            Slider(value: $speed, in: 4...14, step: 0.1).accessibilityLabel("Launch power")
            ActionButton("Launch ball", variant: .primary) { launch(controlVelocity) }
                .disabled(simulation.phase != .ready).accessibilityIdentifier("physics-launch")
        }
    }
    private var worldControls: some View {
        Card(.floating) {
            Text("World settings").font(tokens.typography.label)
            Text("Gravity \(gravity, specifier: "%.1f") m/s²").font(tokens.typography.caption)
            Slider(value: $gravity, in: 4...20, step: 0.1).accessibilityLabel("Gravity")
            Text("Bounce \(bounce, specifier: "%.2f")").font(tokens.typography.caption)
            Slider(value: $bounce, in: 0...0.9, step: 0.05).accessibilityLabel("Bounce")
            Text("Changing the target or physics resets the ball. The landing zone scores the first ground contact.")
                .font(tokens.typography.caption).foregroundStyle(tokens.colors.inkSecondary.color)
        }
    }
    private func launch(_ velocity: PhysicsVector) {
        if simulation.launch(velocity: velocity) { paused = false; trail = [simulation.position] }
    }
    private func reset() { simulation.resetBall(); paused = false; aiming = false; dragVelocity = nil; trail = [] }
    private func configure() {
        simulation.configure(target: target, settings: .init(gravity: gravity, restitution: bounce))
        paused = false; aiming = false; dragVelocity = nil; trail = []
    }
}

private struct PhysicsCourt: View {
    let simulation: ThrowSimulation
    let trajectory: [PhysicsVector]
    let trail: [PhysicsVector]
    var body: some View {
        Canvas { context, size in
            let unit = size.width / 10
            func point(_ p: PhysicsVector) -> CGPoint { .init(x: p.x * unit, y: size.height - p.y * unit) }
            func line(_ a: PhysicsVector, _ b: PhysicsVector, color: Color, width: Double = 1) {
                var path = Path(); path.move(to: point(a)); path.addLine(to: point(b))
                context.stroke(path, with: .color(color), lineWidth: width)
            }
            func circle(_ p: PhysicsVector, radius: Double, color: Color) {
                let center = point(p), r = radius * unit
                context.fill(Path(ellipseIn: CGRect(x: center.x-r, y: center.y-r, width: r*2, height: r*2)), with: .color(color))
            }
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .linearGradient(
                Gradient(colors: [Color(red: 0.06, green: 0.10, blue: 0.20), Color(red: 0.12, green: 0.19, blue: 0.30)]),
                startPoint: .zero, endPoint: CGPoint(x: size.width, y: size.height)))
            for x in 1..<10 { line(.init(x: Double(x)), .init(x: Double(x), y: 7), color: .white.opacity(0.045)) }
            for y in 1..<7 { line(.init(y: Double(y)), .init(x: 10, y: Double(y)), color: .white.opacity(0.045)) }
            line(.init(y: 0.03), .init(x: 10, y: 0.03), color: .white.opacity(0.4), width: 2)
            if simulation.target == .hoop {
                let c = ThrowSimulation.hoopCenter, half = ThrowSimulation.hoopHalfWidth
                let net = Color.white.opacity(0.55)
                for i in 0...4 {
                    let x = c.x-half + Double(i)/4 * half*2
                    line(.init(x: x, y: c.y), .init(x: c.x+(x-c.x)*0.6, y: c.y-0.85), color: net)
                }
                line(.init(x: c.x-half*0.6, y: c.y-0.85), .init(x: c.x+half*0.6, y: c.y-0.85), color: net)
                line(.init(x: c.x-half*0.85, y: c.y-0.35), .init(x: c.x+half*0.85, y: c.y-0.35), color: net)
                line(.init(x: c.x+half+0.35, y: c.y-0.15), .init(x: c.x+half+0.35, y: c.y+1.3), color: .white, width: 0.14*unit)
                // Side-on rim: only the two ends collide; the gap remains open to the ball.
                for x in [c.x-half, c.x+half] { circle(.init(x: x, y: c.y), radius: ThrowSimulation.rimRadius, color: .orange) }
            } else {
                let left = point(.init(x: ThrowSimulation.zoneMin, y: 0.2))
                context.fill(Path(CGRect(x: left.x, y: left.y, width: (ThrowSimulation.zoneMax-ThrowSimulation.zoneMin)*unit, height: 0.2*unit)), with: .color(.mint))
                line(.init(x: 7.5, y: 0.45), .init(x: 7.5, y: 1), color: .mint.opacity(0.7), width: 2)
            }
            for (index, p) in trail.enumerated() { circle(p, radius: 0.045, color: .orange.opacity(Double(index+1)/Double(max(1,trail.count))*0.4)) }
            if simulation.phase == .ready {
                for p in trajectory { circle(p, radius: 0.035, color: .white.opacity(0.45)) }
                circle(simulation.position, radius: 0.45, color: .orange.opacity(0.15))
            }
            circle(simulation.position, radius: ThrowSimulation.ballRadius, color: .orange)
            let p = simulation.position, r = ThrowSimulation.ballRadius * 0.9
            line(.init(x: p.x-r, y: p.y), .init(x: p.x+r, y: p.y), color: .black.opacity(0.55))
            line(.init(x: p.x, y: p.y-r), .init(x: p.x, y: p.y+r), color: .black.opacity(0.55))
            context.draw(Text(simulation.target == .hoop ? "THE COURT" : "LAND IT").font(.system(size: 11, weight: .bold, design: .monospaced)).foregroundStyle(.white.opacity(0.6)), at: CGPoint(x: 18, y: 20), anchor: .leading)
        }
    }
}
