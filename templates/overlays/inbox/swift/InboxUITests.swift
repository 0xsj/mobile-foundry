import XCTest

@MainActor final class InboxUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }
    private func reveal(_ node: XCUIElement, app: XCUIApplication) {
        for _ in 0..<14 {
            if node.exists && node.isHittable && node.frame.minY > app.frame.minY + 100 && node.frame.maxY < app.frame.maxY - 90 { return }
            if node.exists && node.frame.minY < app.frame.minY + 100 { app.swipeDown() } else { app.swipeUp() }
        }
        XCTAssertTrue(node.isHittable, "Missing inbox control: \(node.identifier)")
    }
    private func tap(_ node: XCUIElement, app: XCUIApplication) { reveal(node, app: app); node.tap() }
    private func label(_ node: XCUIElement, _ value: String) {
        expectation(for: NSPredicate(format: "label == %@", value), evaluatedWith: node); waitForExpectations(timeout: 15)
    }
    private func ready(_ app: XCUIApplication) {
        expectation(for: NSPredicate(format: "enabled == true"), evaluatedWith: app.switches["inbox.media"])
        waitForExpectations(timeout: 15)
    }
    private func toggle(_ node: XCUIElement, app: XCUIApplication) {
        reveal(node, app: app)
        node.coordinate(withNormalizedOffset: CGVector(dx: 0.92, dy: 0.5)).tap()
    }
    func testInboxPagingUncertainWritesScopeAndRelaunch() throws {
        guard ProcessInfo.processInfo.environment["CONNECTED_ACCEPTANCE"] == "1" else { throw XCTSkip("Owned API required") }
        let app = XCUIApplication(); app.launch(); app.buttons["Activity"].tap()
        XCTAssertTrue(app.staticTexts["Sign in from Account to view your inbox."].waitForExistence(timeout: 15))
        app.buttons["Account"].tap()
        let login = app.textFields["connection.login"]; XCTAssertTrue(login.waitForExistence(timeout: 15))
        tap(login, app: app); login.typeText("starter-ios-inbox")
        tap(app.secureTextFields["connection.password"], app: app); app.secureTextFields["connection.password"].typeText("owned starter test passphrase")
        tap(app.buttons["connection.sign-in"], app: app); label(app.staticTexts["connection.account"], "Selected: Personal")
        app.buttons["Activity"].tap(); ready(app)
        label(app.staticTexts["inbox.count"], "25 unread notifications")
        label(app.staticTexts["inbox.loaded"], "Showing 20 notifications.")
        tap(app.buttons["inbox.more"], app: app); label(app.staticTexts["inbox.loaded"], "Showing 25 notifications."); ready(app)

        let read = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'inbox.read.'")).firstMatch
        tap(read, app: app)
        XCTAssertTrue(app.staticTexts["inbox.recovery"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.staticTexts["inbox.failure"].exists)
        XCTAssertFalse(app.switches["inbox.media"].isEnabled)
        label(app.staticTexts["inbox.count"], "25 unread notifications")
        tap(app.buttons["inbox.refresh"], app: app); label(app.staticTexts["inbox.count"], "24 unread notifications"); ready(app)
        toggle(app.switches["inbox.filter"], app: app); label(app.staticTexts["inbox.loaded"], "Showing 20 notifications."); ready(app)
        tap(app.buttons["inbox.more"], app: app); label(app.staticTexts["inbox.loaded"], "Showing 24 notifications."); ready(app)
        toggle(app.switches["inbox.filter"], app: app); ready(app)
        toggle(app.switches["inbox.media"], app: app)
        XCTAssertTrue(app.staticTexts["inbox.recovery"].waitForExistence(timeout: 15))
        XCTAssertFalse(app.switches["inbox.media"].isEnabled)
        tap(app.buttons["inbox.refresh"], app: app); ready(app)
        XCTAssertEqual(app.switches["inbox.media"].value as? String, "0")

        app.buttons["Account"].tap(); tap(app.buttons["Use Starter workspace"], app: app)
        label(app.staticTexts["connection.account"], "Selected: Starter workspace")
        app.buttons["Activity"].tap(); ready(app)
        label(app.staticTexts["inbox.count"], "0 unread notifications")
        XCTAssertEqual(app.switches["inbox.media"].value as? String, "1")
        XCTAssertTrue(app.staticTexts["Your account inbox is empty."].exists)
        app.buttons["Account"].tap(); tap(app.buttons["Use Personal"], app: app)
        label(app.staticTexts["connection.account"], "Selected: Personal")
        tap(app.buttons["connection.renew"], app: app); label(app.staticTexts["connection.account"], "Selected: Personal")
        app.buttons["Activity"].tap(); ready(app); label(app.staticTexts["inbox.count"], "24 unread notifications")
        XCTAssertEqual(app.switches["inbox.media"].value as? String, "0")
        app.terminate(); app.launch(); app.buttons["Account"].tap()
        label(app.staticTexts["connection.account"], "Selected: Personal")
        app.buttons["Activity"].tap(); ready(app); label(app.staticTexts["inbox.count"], "24 unread notifications")
        XCTAssertEqual(app.switches["inbox.media"].value as? String, "0")
        app.buttons["Account"].tap(); tap(app.buttons["connection.sign-out"], app: app)
        XCTAssertTrue(login.waitForExistence(timeout: 15)); app.buttons["Activity"].tap()
        XCTAssertTrue(app.staticTexts["Sign in from Account to view your inbox."].waitForExistence(timeout: 15))
        XCTAssertFalse(app.staticTexts["inbox.count"].exists)
    }
}
