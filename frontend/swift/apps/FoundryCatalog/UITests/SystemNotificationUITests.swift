import XCTest

@MainActor final class SystemNotificationUITests: XCTestCase {
    override func setUp() { super.setUp();continueAfterFailure = false }
    private func reveal(_ node: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<8 {
            if node.exists && node.isHittable && node.frame.maxY < app.frame.maxY - 40 { return }
            app.swipeUp()
        }
        XCTAssertTrue(node.isHittable)
    }
    private func post(_ app: XCUIApplication) -> String {
        let button = app.buttons["notifications.post"]; reveal(button,in:app)
        expectation(for: NSPredicate(format: "enabled == true"),evaluatedWith: button);waitForExpectations(timeout:10)
        button.tap()
        let accepted = app.staticTexts["notifications.accepted"]
        XCTAssertTrue(accepted.waitForExistence(timeout: 8))
        return String(accepted.label.suffix(36))
    }
    private func tapNotification(_ app: XCUIApplication) {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let title = springboard.staticTexts["Mobile notification preview"].firstMatch
        // SpringBoard can expose an offscreen notification whose activation point is invalid.
        // Inspect its frame before tapping; querying isHittable itself can fail in that state.
        if !title.waitForExistence(timeout: 7) || title.frame.isEmpty || !title.frame.intersects(springboard.frame) {
            let top = springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.15,dy: 0.01))
            top.press(forDuration: 0.1,thenDragTo: springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.15,dy: 0.8)))
        }
        XCTAssertTrue(title.waitForExistence(timeout: 8))
        let body = springboard.staticTexts["Tap to return to System notifications."].firstMatch
        if body.exists && !body.frame.isEmpty && body.frame.intersects(springboard.frame) {
            body.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        } else { title.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap() }
        if !app.wait(for: .runningForeground, timeout: 3), title.exists {
            title.swipeRight()
            let open = springboard.buttons["Open"].firstMatch
            if open.waitForExistence(timeout: 2) { open.tap() }
        }
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 8))
    }
    private func assertOpened(_ id: String,in app: XCUIApplication) {
        let opened = app.staticTexts["notifications.opened"]
        expectation(for:NSPredicate(format:"label == %@","Opened preview: " + id),evaluatedWith:opened);waitForExpectations(timeout:12)
    }
    func testRealLocalNotificationOpensColdAndWarmApplication() {
        let app = XCUIApplication();app.launch()
        app.buttons["Studio"].tap();app.buttons["Open catalog"].tap()
        app.buttons["System notifications"].firstMatch.tap()
        let allow = app.buttons["notifications.allow"]
        XCTAssertTrue(allow.waitForExistence(timeout: 8));allow.tap()
        let systemAllow = XCUIApplication(bundleIdentifier:"com.apple.springboard").buttons["Allow"]
        if systemAllow.waitForExistence(timeout: 3) { systemAllow.tap() }
        let warmID = post(app)
        XCUIDevice.shared.press(.home);tapNotification(app);assertOpened(warmID,in:app)
        let coldID = post(app);XCTAssertNotEqual(coldID,warmID)
        app.terminate();tapNotification(app);assertOpened(coldID,in:app)
        let clear = app.buttons["notifications.clear"];reveal(clear,in:app);clear.tap()
        let attachment = XCTAttachment(screenshot:app.screenshot());attachment.lifetime = .keepAlways;add(attachment)
    }
}
