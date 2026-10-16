import XCTest

final class BrowseUITests: XCTestCase {
    @MainActor func testSearchDetailBackClearAndMaterial() {
        let app = XCUIApplication()
        app.launch()
        let search = app.textFields["Find collections"]
        XCTAssertTrue(search.waitForExistence(timeout: 8))
        search.tap(); search.typeText("TWO")
        XCTAssertTrue(app.buttons["collection-two"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["collection-one"].exists)
        app.buttons["collection-two"].tap()
        XCTAssertTrue(app.staticTexts["Your collection starts here."].waitForExistence(timeout: 5))
        XCTAssertFalse(search.exists)
        let toggle = app.switches["Glass"]
        toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5)).tap()
        XCTAssertEqual(toggle.value as? String, "0")
        XCTAssertTrue(app.staticTexts["Collection two"].exists)
        app.buttons["browse-back"].tap()
        XCTAssertTrue(search.waitForExistence(timeout: 5))
        XCTAssertEqual(search.value as? String, "TWO")
        app.buttons["Clear search"].tap()
        XCTAssertTrue(app.buttons["collection-one"].exists)
        search.tap(); search.typeText("absent")
        XCTAssertTrue(app.staticTexts["No collections match your search."].waitForExistence(timeout: 5))
        app.buttons["Clear search"].tap()
        app.buttons["collection-three"].tap()
        XCTAssertTrue(app.staticTexts["Collection three"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.switches["Glass"].value as? String, "0")
        let capture = XCTAttachment(screenshot: app.screenshot())
        capture.name = "Browse navigation"; capture.lifetime = .keepAlways
        add(capture)
    }
}
