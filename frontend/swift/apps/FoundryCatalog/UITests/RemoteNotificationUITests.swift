import XCTest

@MainActor final class RemoteNotificationUITests: XCTestCase {
    override func setUp() { super.setUp(); continueAfterFailure = false }
    private func push(_ id: String, base: String) async throws {
        var request = URLRequest(url: URL(string: base + "/" + id)!)
        request.httpMethod = "POST"
        let (_, response) = try await URLSession.shared.data(for: request)
        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 204)
    }
    private func tapPush(_ app: XCUIApplication) {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let title = springboard.staticTexts["Mobile push preview"].firstMatch
        if !title.waitForExistence(timeout: 7) || !title.isHittable {
            let top = springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.15, dy: 0.01))
            top.press(forDuration: 0.1, thenDragTo: springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.15, dy: 0.8)))
        }
        XCTAssertTrue(title.waitForExistence(timeout: 8))
        let body = springboard.staticTexts["Open the app to continue."].firstMatch
        if body.exists && body.isHittable { body.tap() } else { title.tap() }
        if !app.wait(for: .runningForeground, timeout: 3), title.exists {
            title.swipeRight()
            let open = springboard.buttons["Open"].firstMatch
            if open.waitForExistence(timeout: 2) { open.tap() }
        }
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 8))
    }
    private func assertText(_ text: String, id: String, app: XCUIApplication) {
        let element = app.staticTexts[id]
        expectation(for: NSPredicate(format: "label == %@", text), evaluatedWith: element)
        waitForExpectations(timeout: 12)
    }
    func testSimulatorPushReceiptAndWarmColdOpens() async throws {
        guard let base = ProcessInfo.processInfo.environment["REMOTE_PREVIEW_TEST_BASE"] else {
            throw XCTSkip("make remote-notifications-ios-test supplies the simulator push driver")
        }
        let app = XCUIApplication(); app.launch()
        app.buttons["Studio"].tap(); app.buttons["Open catalog"].tap()
        app.buttons["System notifications"].firstMatch.tap()
        let allow = app.buttons["notifications.allow"]
        XCTAssertTrue(allow.waitForExistence(timeout: 8)); allow.tap()
        let systemAllow = XCUIApplication(bundleIdentifier: "com.apple.springboard").buttons["Allow"]
        if systemAllow.waitForExistence(timeout: 3) { systemAllow.tap() }
        let warm = UUID().uuidString.lowercased()
        try await push(warm, base: base)
        assertText("Received push preview: " + warm, id: "notifications.remote-received", app: app)
        XCTAssertNotEqual(app.staticTexts["notifications.opened"].label, "Opened push preview: " + warm)
        XCUIDevice.shared.press(.home); tapPush(app)
        assertText("Opened push preview: " + warm, id: "notifications.opened", app: app)
        let cold = UUID().uuidString.lowercased()
        app.terminate(); try await push(cold, base: base); tapPush(app)
        assertText("Opened push preview: " + cold, id: "notifications.opened", app: app)
        let attachment = XCTAttachment(screenshot: app.screenshot()); attachment.lifetime = .keepAlways; add(attachment)
    }
}
