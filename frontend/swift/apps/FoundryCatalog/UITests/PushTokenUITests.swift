import XCTest

@MainActor final class PushTokenUITests: XCTestCase {
    func testUnconfiguredProviderIsAnExplicitRecoverableUIState() {
        continueAfterFailure = false
        let app = XCUIApplication(); app.launch()
        app.buttons["Studio"].tap(); app.buttons["Open catalog"].tap()
        app.buttons["System notifications"].firstMatch.tap()
        let collect = app.buttons["push.collect"]
        for _ in 0..<8 {
            if collect.exists && collect.isHittable && collect.frame.maxY < app.frame.maxY - 40 { break }
            app.swipeUp()
        }
        XCTAssertTrue(collect.isHittable)
        XCTAssertFalse(app.staticTexts["push.failure"].exists)
        collect.tap()
        let failure = app.staticTexts["push.failure"]
        XCTAssertTrue(failure.waitForExistence(timeout: 8))
        XCTAssertTrue(failure.label.contains("Push provider setup is missing."))
        XCTAssertEqual(app.staticTexts["push.status"].label,"No collected token")
        XCTAssertFalse(app.buttons["push.register"].exists)
        let attachment = XCTAttachment(screenshot: app.screenshot()); attachment.lifetime = .keepAlways; add(attachment)
    }
}
