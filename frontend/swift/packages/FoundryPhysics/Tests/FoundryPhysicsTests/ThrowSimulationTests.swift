import Foundation
import Testing
@testable import FoundryPhysics

@Test func sharedShotsScoreOnceAndSettle() throws {
    struct Fixture: Decodable {
        struct Shot: Decodable { let target: String; let vx, vy: Double; let score: Int }
        let shots: [Shot]
    }
    var root = URL(fileURLWithPath: #filePath)
    for _ in 0..<7 { root.deleteLastPathComponent() }
    let rows = try JSONDecoder().decode(Fixture.self, from: Data(contentsOf: root.appendingPathComponent("contracts/fixtures/physics/shots.json"))).shots
    for row in rows {
        var world = ThrowSimulation(target: try #require(ThrowTarget(rawValue: row.target)))
        let launched = world.launch(velocity: .init(x: row.vx, y: row.vy))
        #expect(launched)
        for _ in 0..<1500 { world.advance(elapsed: ThrowSimulation.timeStep) }
        #expect(world.scores == row.score, "\(row.target): \(row.vx), \(row.vy)")
        #expect(world.shots == 1 && world.phase == .settled)
        #expect(world.position.isFinite && world.velocity == .init())
        world.resetBall()
        #expect(world.phase == .ready && world.shots == 1 && world.scores == row.score && !world.scored)
    }
}

@Test func gravityUsesWorldUnitsAndFixedSteps() {
    var world = ThrowSimulation()
    world.launch(velocity: .init(x: 2, y: 6))
    world.advance(elapsed: 0.1); world.advance(elapsed: 0.1); world.advance(elapsed: 0.05)
    let dt = ThrowSimulation.timeStep, n = 30.0
    #expect(abs(world.position.x - 1.65) < 1e-10)
    #expect(abs(world.position.y - (0.65 + 6*0.25 - 9.81*dt*dt*n*(n+1)/2)) < 1e-10)
    #expect(abs(world.velocity.y - (6 - 9.81*0.25)) < 1e-10)
    func at(_ fps: Int) -> ThrowSimulation {
        var w = ThrowSimulation(); w.launch(velocity: .init(x: 5, y: 8.6))
        for _ in 0..<(fps*2) { w.advance(elapsed: 1/Double(fps)) }
        return w
    }
    #expect((at(30).position - at(120).position).length < 1e-10)
    #expect((at(60).velocity - at(120).velocity).length < 1e-10)
    #expect(at(30).scores == 1 && at(120).scores == 1)
}

@Test func contactsReflectAndRestitutionDissipatesEnergy() {
    var floor = ThrowSimulation(settings: .init(restitution: 0.65))
    floor.launch(velocity: .init(y: -3)); floor.advance(elapsed: 0.1); floor.advance(elapsed: 0.05)
    #expect(floor.velocity.y > 0 && floor.position.y >= ThrowSimulation.ballRadius)
    var dead = ThrowSimulation(settings: .init(restitution: 0))
    dead.launch(velocity: .init(y: -3))
    for _ in 0..<60 { dead.advance(elapsed: 1/60) }
    #expect(dead.phase == .settled && dead.velocity == .init())
    var wall = ThrowSimulation(target: .landingZone)
    wall.launch(velocity: .init(x: 14))
    for _ in 0..<40 { wall.advance(elapsed: 1/60) }
    #expect(wall.velocity.x < 0 && wall.position.x <= 10-ThrowSimulation.ballRadius)
    var board = ThrowSimulation()
    board.launch(velocity: .init(x: 7, y: 8.1))
    for _ in 0..<72 { board.advance(elapsed: 1/60) }
    #expect(board.velocity.x < 0 && board.scores <= 1)
}

@Test func inputBudgetsResetAndPreviewAreSafe() {
    var world = ThrowSimulation()
    let nan = world.launch(velocity: .init(x: .nan))
    let infinity = world.launch(velocity: .init(x: .infinity))
    let zero = world.launch(velocity: .init())
    #expect(!nan && !infinity && !zero)
    #expect(ThrowSimulation.aimVelocity(displacement: .init(x: 1e308, y: 1e308)).isFinite)
    #expect(abs(ThrowSimulation.aimVelocity(displacement: .init(x: 100, y: 100)).length - 14) < 1e-10)
    let original = world
    #expect(world.trajectory(velocity: .init(x: 5.5, y: 8.1)).count > 10)
    #expect(world == original)
    world.launch(velocity: .init(x: 100, y: 100))
    #expect(abs(world.velocity.length - 14) < 1e-10)
    let duplicate = world.launch(velocity: .init(x: 1, y: 1))
    #expect(!duplicate && world.shots == 1)
    let before = world
    world.advance(elapsed: .nan); world.advance(elapsed: -1)
    #expect(world == before)
    var bounded = before; bounded.advance(elapsed: 0.1)
    world.advance(elapsed: 3600)
    #expect(world == bounded)
    world.configure(target: .landingZone, settings: .init(gravity: .nan, restitution: 5))
    #expect(world.shots == 0 && world.scores == 0 && world.phase == .ready)
    #expect(world.settings.gravity == 9.81 && world.settings.restitution == 0.9)
}

@Test func boundedShotsStayInsideTheWorld() {
    for target in ThrowTarget.allCases {
        for angle in stride(from: -160.0, through: 160, by: 20) {
            var world = ThrowSimulation(target: target)
            world.launch(velocity: .init(x: cos(angle * .pi / 180)*14, y: sin(angle * .pi / 180)*14))
            for _ in 0..<1500 {
                world.advance(elapsed: ThrowSimulation.timeStep)
                #expect(world.position.isFinite && world.velocity.isFinite)
                #expect(world.position.x >= ThrowSimulation.ballRadius - 1e-9 && world.position.x <= 10-ThrowSimulation.ballRadius+1e-9)
                #expect(world.position.y >= ThrowSimulation.ballRadius - 1e-9 && world.position.y <= 7-ThrowSimulation.ballRadius+1e-9)
            }
            #expect(world.scores <= 1 && world.phase == .settled)
        }
    }
}
