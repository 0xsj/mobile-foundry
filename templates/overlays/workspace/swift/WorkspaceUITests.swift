import XCTest

final class WorkspaceUITests: XCTestCase {
    @MainActor func testWorkspaceNavigationMaterialAndWidthChange() {
        let app = XCUIApplication()
        app.launch()
        let one = app.buttons["project-one"], two = app.buttons["project-two"]
        XCTAssertTrue(one.waitForExistence(timeout: 8))
        two.tap()
        XCTAssertTrue(app.staticTexts["Your project starts here."].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Project two"].firstMatch.exists)
        let toggle = app.switches["Glass"]
        XCTAssertEqual(toggle.value as? String, "1")
        // SwiftUI exposes the whole labeled row; hit the native switch at its trailing edge.
        toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5)).tap()
        XCTAssertTrue(toggle.waitForExistence(timeout: 3))
        XCTAssertEqual(toggle.value as? String, "0")
        XCTAssertTrue(app.staticTexts["Project two"].firstMatch.exists)
        let back = app.buttons["workspace-back"]
        if back.exists {
            back.tap()
            XCTAssertTrue(one.waitForExistence(timeout: 5))
            two.tap()
            XCTAssertTrue(app.staticTexts["Project two"].firstMatch.waitForExistence(timeout: 5))
        } else {
            XCTAssertTrue(one.exists) // Expanded list and detail are visible together.
            one.tap()
            XCTAssertTrue(app.staticTexts["Project one"].firstMatch.waitForExistence(timeout: 5))
        }
        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(app.staticTexts["Your project starts here."].waitForExistence(timeout: 5))
        XCUIDevice.shared.orientation = .portrait
        XCTAssertTrue(app.staticTexts["Your project starts here."].waitForExistence(timeout: 5))
        XCTAssertEqual(app.switches["Glass"].value as? String, "0")
        let capture = XCTAttachment(screenshot: app.screenshot())
        capture.name = "Workspace navigation"; capture.lifetime = .keepAlways
        add(capture)
    }
}
