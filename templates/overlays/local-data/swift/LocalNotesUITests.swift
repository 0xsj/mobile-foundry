import XCTest

/// Actual form/confirmation actions and a fresh process in the installed container.
final class LocalNotesUITests: XCTestCase {
    @MainActor func testCreateEditCancelDeleteAndProcessRelaunch() throws {
        continueAfterFailure = false
        let app = XCUIApplication(), suffix = UUID().uuidString.prefix(8)
        let original = "Starter \(suffix)", updated = "Edited \(suffix)"
        app.launch()
        app.tabBars.buttons["Library"].tap()
        let field = app.textFields["Title"]
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        let ready = NSPredicate(format: "enabled == true")
        expectation(for: ready, evaluatedWith: field)
        waitForExpectations(timeout: 10)
        let count = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'Live collection:'")).firstMatch
        XCTAssertTrue(count.waitForExistence(timeout: 10))
        let baseline = try XCTUnwrap(Int(count.label.split(separator: " ")[2]))
        app.buttons["Create note"].tap()
        XCTAssertTrue(app.staticTexts["Error: Enter a title."].waitForExistence(timeout: 5))
        field.tap(); field.typeText(original)
        app.buttons["Create note"].tap()
        XCTAssertTrue(app.staticTexts[original].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Live collection: \(baseline + 1) notes"].waitForExistence(timeout: 10))
        reveal(app.buttons["Edit \(original)"], in: app, down: false).tap()
        reveal(field, in: app, down: true).tap()
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: original.count) + updated)
        app.buttons["Save changes"].tap()
        XCTAssertTrue(app.staticTexts[updated].waitForExistence(timeout: 10))
        reveal(app.buttons["Delete \(updated)"], in: app, down: false).tap()
        app.alerts.buttons["Keep note"].tap()
        XCTAssertTrue(app.staticTexts[updated].exists)

        app.terminate(); app.launch()
        app.tabBars.buttons["Library"].tap()
        XCTAssertTrue(app.staticTexts[updated].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Live collection: \(baseline + 1) notes"].waitForExistence(timeout: 10))
        _ = reveal(app.staticTexts[updated], in: app, down: false)
        let capture = XCTAttachment(screenshot: app.screenshot())
        capture.name = "Persisted Library after process relaunch"; capture.lifetime = .keepAlways; add(capture)
        reveal(app.buttons["Delete \(updated)"], in: app, down: false).tap()
        app.alerts.buttons["Delete note"].tap()
        XCTAssertTrue(app.staticTexts["Note deleted."].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts[updated].exists)
        XCTAssertTrue(app.staticTexts["Live collection: \(baseline) notes"].waitForExistence(timeout: 10))
    }

    @MainActor private func reveal(_ element: XCUIElement, in app: XCUIApplication, down: Bool) -> XCUIElement {
        for _ in 0..<8 {
            if element.isHittable { return element }
            if down { app.scrollViews.firstMatch.swipeDown() } else { app.scrollViews.firstMatch.swipeUp() }
        }
        XCTAssertTrue(element.isHittable)
        return element
    }
}
