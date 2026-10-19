import XCTest

/// The notice must come from backend delivery, never an optimistic native append.
@MainActor final class MediaUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }
    private func tap(_ node: XCUIElement, _ app: XCUIApplication) {
        for _ in 0..<20 {
            if node.exists && node.isHittable && node.frame.minY > 100 && node.frame.maxY < app.frame.maxY-90 { node.tap(); return }
            if node.exists && node.frame.minY < 100 { app.swipeDown() } else { app.swipeUp() }
        }; XCTFail("Missing media control: \(node.identifier)")
    }
    private func label(_ node: XCUIElement, _ value: String) {
        expectation(for: NSPredicate(format: "label == %@",value),evaluatedWith: node); waitForExpectations(timeout: 20)
    }
    private func enabled(_ node: XCUIElement) {
        expectation(for: NSPredicate(format: "enabled == true"),evaluatedWith: node); waitForExpectations(timeout: 20)
    }
    private func toggleMedia(_ app: XCUIApplication) {
        let node=app.switches["inbox.media"];tapLocation(node,app)
        node.coordinate(withNormalizedOffset: CGVector(dx: 0.92,dy: 0.5)).tap()
    }
    private func tapLocation(_ node: XCUIElement, _ app: XCUIApplication) {
        for _ in 0..<20 {
            if node.exists && node.isHittable && node.frame.minY > 100 && node.frame.maxY < app.frame.maxY-90 { return }
            if node.exists && node.frame.minY < 100 { app.swipeDown() } else { app.swipeUp() }
        }; XCTFail("Missing media control: \(node.identifier)")
    }
    private func upload(_ app: XCUIApplication, count: Int, loseReply: Bool = false) {
        app.buttons["Today"].tap();enabled(app.buttons["files.choose"])
        tap(app.buttons["files.choose"],app)
        let photo=app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Photo,' AND NOT label CONTAINS ', Photo,'")).firstMatch
        XCTAssertTrue(photo.waitForExistence(timeout: 20));photo.coordinate(withNormalizedOffset: CGVector(dx: 0.5,dy: 0.5)).tap()
        XCTAssertTrue(app.staticTexts["files.draft"].waitForExistence(timeout: 20));enabled(app.buttons["files.upload"])
        tap(app.buttons["files.upload"],app)
        if loseReply {
            XCTAssertTrue(app.staticTexts["files.failure"].waitForExistence(timeout: 20))
            XCTAssertTrue(app.staticTexts["files.pending"].exists)
            tap(app.buttons["files.retry"],app)
        }
        enabled(app.buttons["files.choose"]);label(app.staticTexts["files.loaded"],"Showing \(count) files.")
        XCTAssertFalse(app.staticTexts["files.pending"].exists)
    }
    private func inbox(_ app: XCUIApplication, unread: Int, loaded: Int) {
        app.buttons["Activity"].tap();enabled(app.switches["inbox.media"])
        // Delivery is asynchronous. Each retry below is an explicit UI read,
        // not a product poll or a mutation replay.
        let expected="\(unread) unread notifications"
        for _ in 0..<10 {
            if app.staticTexts["inbox.count"].label == expected && app.staticTexts["inbox.loaded"].label == "Showing \(loaded) notifications." { return }
            tap(app.buttons["inbox.refresh"],app);enabled(app.switches["inbox.media"])
        }
        label(app.staticTexts["inbox.count"],expected);label(app.staticTexts["inbox.loaded"],"Showing \(loaded) notifications.")
    }
    func testUploadsCreateScopedNoticesRespectPreferencesAndRestoreWithoutReplay() throws {
        guard ProcessInfo.processInfo.environment["CONNECTED_ACCEPTANCE"] == "1" else { throw XCTSkip("Owned API and gallery required") }
        let app=XCUIApplication();app.launch();app.buttons["Account"].tap()
        let login=app.textFields["connection.login"];XCTAssertTrue(login.waitForExistence(timeout: 20))
        tap(login,app);login.typeText("starter-ios-media")
        tap(app.secureTextFields["connection.password"],app);app.secureTextFields["connection.password"].typeText("owned starter test passphrase")
        tap(app.buttons["connection.sign-in"],app);label(app.staticTexts["connection.account"],"Selected: Personal")
        inbox(app,unread: 0,loaded: 0)
        upload(app,count: 1,loseReply: true);inbox(app,unread: 1,loaded: 1)
        tap(app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'inbox.open.'")).firstMatch,app)
        XCTAssertTrue(app.staticTexts["destination.name"].waitForExistence(timeout: 20))
        tap(app.buttons["destination.download"],app)
        XCTAssertTrue(app.staticTexts["destination.verified"].waitForExistence(timeout: 20));app.buttons["Close file"].tap()
        label(app.staticTexts["inbox.count"],"1 unread notifications")
        let read=app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'inbox.read.'")).firstMatch
        tap(read,app);XCTAssertTrue(app.staticTexts["inbox.recovery"].waitForExistence(timeout: 20))
        XCTAssertFalse(app.switches["inbox.media"].isEnabled)
        tap(app.buttons["inbox.refresh"],app);enabled(app.switches["inbox.media"]);label(app.staticTexts["inbox.count"],"0 unread notifications")
        toggleMedia(app);XCTAssertTrue(app.staticTexts["inbox.recovery"].waitForExistence(timeout: 20))
        tap(app.buttons["inbox.refresh"],app);enabled(app.switches["inbox.media"]);XCTAssertEqual(app.switches["inbox.media"].value as? String,"0")
        upload(app,count: 2);inbox(app,unread: 0,loaded: 1)
        app.terminate();app.launch();app.buttons["Account"].tap();label(app.staticTexts["connection.account"],"Selected: Personal")
        app.buttons["Today"].tap();enabled(app.buttons["files.choose"]);label(app.staticTexts["files.loaded"],"Showing 2 files.")
        inbox(app,unread: 0,loaded: 1);XCTAssertEqual(app.switches["inbox.media"].value as? String,"0")
        toggleMedia(app);enabled(app.switches["inbox.media"]);XCTAssertEqual(app.switches["inbox.media"].value as? String,"1")
        upload(app,count: 3);inbox(app,unread: 1,loaded: 2)
        app.buttons["Today"].tap();enabled(app.buttons["files.choose"])
        tap(app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'files.open.'")).firstMatch,app)
        XCTAssertTrue(app.staticTexts["files.verified"].waitForExistence(timeout: 20));app.buttons["Close preview"].tap()
        app.buttons["Account"].tap();tap(app.buttons["Use Starter workspace"],app);label(app.staticTexts["connection.account"],"Selected: Starter workspace")
        app.buttons["Today"].tap();enabled(app.buttons["files.choose"]);label(app.staticTexts["files.loaded"],"Showing 0 files.")
        inbox(app,unread: 0,loaded: 0)
        app.buttons["Account"].tap();tap(app.buttons["Use Personal"],app);label(app.staticTexts["connection.account"],"Selected: Personal")
        tap(app.buttons["connection.renew"],app);label(app.staticTexts["connection.account"],"Selected: Personal")
        inbox(app,unread: 1,loaded: 2)
        app.terminate();app.launch();app.buttons["Account"].tap();label(app.staticTexts["connection.account"],"Selected: Personal")
        app.buttons["Today"].tap();enabled(app.buttons["files.choose"]);label(app.staticTexts["files.loaded"],"Showing 3 files.")
        inbox(app,unread: 1,loaded: 2)
        tap(app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'inbox.open.'")).firstMatch,app)
        XCTAssertTrue(app.staticTexts["destination.failure"].waitForExistence(timeout: 20))
        label(app.staticTexts["destination.failure"],"This file is no longer available.")
        XCTAssertFalse(app.staticTexts["destination.name"].exists);app.buttons["Close file"].tap()
        label(app.staticTexts["inbox.count"],"1 unread notifications")
        app.buttons["Account"].tap();tap(app.buttons["connection.sign-out"],app);XCTAssertTrue(login.waitForExistence(timeout: 20))
        app.buttons["Activity"].tap();XCTAssertTrue(app.staticTexts["Sign in from Account to view your inbox."].exists)
        app.buttons["Today"].tap();XCTAssertTrue(app.staticTexts["Sign in from Account to view your files."].exists)
    }
}
