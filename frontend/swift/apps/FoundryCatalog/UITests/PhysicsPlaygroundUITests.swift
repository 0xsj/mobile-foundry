import XCTest

@MainActor final class PhysicsPlaygroundUITests: XCTestCase {
    private func open() -> XCUIApplication {
        let app = XCUIApplication()
        app.launch()
        app.tabBars.buttons["Studio"].tap()
        app.buttons["Open catalog"].tap()
        let link = app.staticTexts["Physics playground"].firstMatch
        for _ in 0..<6 { if link.isHittable { break }; app.swipeUp() }
        XCTAssertTrue(link.isHittable); link.tap()
        XCTAssertTrue(app.otherElements["physics-court"].waitForExistence(timeout: 5))
        return app
    }
    func testDragLaunchScoresAndResetKeepsSessionCount() {
        let app = open(), court = app.otherElements["physics-court"]
        let start = court.coordinate(withNormalizedOffset: CGVector(dx: 0.115, dy: 1-0.65/7))
        let end = court.coordinate(withNormalizedOffset: CGVector(dx: 0.115+5/2.8/10, dy: 1-0.65/7-8.6/2.8/7))
        start.press(forDuration: 0.1, thenDragTo: end)
        let basket = app.staticTexts["physics-status"]
        expectation(for: NSPredicate(format: "label == %@", "Basket!"), evaluatedWith: basket)
        waitForExpectations(timeout: 8)
        XCTAssertEqual(app.staticTexts["physics-score"].label, "1 scored out of 1 throws")
        app.buttons["physics-reset"].tap()
        XCTAssertEqual(basket.label, "Ready to throw")
        XCTAssertEqual(app.staticTexts["physics-score"].label, "1 scored out of 1 throws")
        let screenshot = XCTAttachment(screenshot: app.screenshot()); screenshot.lifetime = .keepAlways; add(screenshot)
    }
    func testAccessibleControlsLaunchAndPause() {
        let app = open()
        let launch = app.buttons["physics-launch"]
        for _ in 0..<3 { if launch.isHittable { break }; app.swipeUp() }
        launch.tap()
        let pause = app.buttons["physics-pause"]
        pause.tap()
        XCTAssertEqual(pause.label, "Resume")
        XCTAssertEqual(app.staticTexts["physics-status"].label, "Paused")
        // Run-loop wait proves user pause survives wall time rather than merely changing its label.
        let paused = expectation(description: "Pause holds")
        DispatchQueue.main.asyncAfter(deadline: .now()+1.5) { paused.fulfill() }
        wait(for: [paused], timeout: 3)
        XCTAssertEqual(app.staticTexts["physics-status"].label, "Paused")
        XCTAssertEqual(app.staticTexts["physics-score"].label, "0 scored out of 1 throws")
        pause.tap()
        expectation(for: NSPredicate(format: "label == %@", "Basket!"), evaluatedWith: app.staticTexts["physics-status"])
        waitForExpectations(timeout: 8)
    }
}
