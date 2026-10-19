import XCTest

@MainActor final class FilesUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }
    private func tap(_ node: XCUIElement, _ app: XCUIApplication) {
        for _ in 0..<20 {
            if node.exists && node.isHittable && node.frame.minY > 100 && node.frame.maxY < app.frame.maxY-90 { node.tap(); return }
            if node.exists && node.frame.minY < 100 { app.swipeDown() } else { app.swipeUp() }
        }; XCTFail("Missing Files control: \(node.identifier)")
    }
    private func label(_ node: XCUIElement, _ value: String) {
        expectation(for: NSPredicate(format: "label == %@",value),evaluatedWith: node); waitForExpectations(timeout: 20)
    }
    private func ready(_ app: XCUIApplication) {
        expectation(for: NSPredicate(format: "enabled == true"),evaluatedWith: app.buttons["files.choose"]); waitForExpectations(timeout: 20)
    }
    func testFilesPhotoUploadReceiptRecoveryPreviewDeleteScopeAndRelaunch() throws {
        guard ProcessInfo.processInfo.environment["CONNECTED_ACCEPTANCE"] == "1" else { throw XCTSkip("Owned API required") }
        let app=XCUIApplication();app.launch();app.buttons["Today"].tap()
        XCTAssertTrue(app.staticTexts["Sign in from Account to view your files."].waitForExistence(timeout: 15))
        app.buttons["Account"].tap();let login=app.textFields["connection.login"];XCTAssertTrue(login.waitForExistence(timeout: 15))
        tap(login,app);login.typeText("starter-ios-files")
        tap(app.secureTextFields["connection.password"],app);app.secureTextFields["connection.password"].typeText("owned starter test passphrase")
        tap(app.buttons["connection.sign-in"],app);label(app.staticTexts["connection.account"],"Selected: Personal")
        app.buttons["Today"].tap();ready(app);label(app.staticTexts["files.loaded"],"Showing 20 files.")
        tap(app.buttons["files.more"],app);label(app.staticTexts["files.loaded"],"Showing 25 files.")
        tap(app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'files.open.'")).firstMatch,app)
        XCTAssertTrue(app.staticTexts["files.verified"].waitForExistence(timeout: 20));app.buttons["Close preview"].tap()
        tap(app.buttons["files.choose"],app)
        let photo=app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Photo,' AND NOT label CONTAINS ', Photo,'")).firstMatch
        XCTAssertTrue(photo.waitForExistence(timeout: 20),app.debugDescription)
        photo.coordinate(withNormalizedOffset: CGVector(dx: 0.5,dy: 0.5)).tap()
        XCTAssertTrue(app.staticTexts["files.draft"].waitForExistence(timeout: 20))
        tap(app.buttons["files.upload"],app)
        XCTAssertTrue(app.staticTexts["files.failure"].waitForExistence(timeout: 20));XCTAssertTrue(app.staticTexts["files.pending"].exists)
        app.terminate();app.launch();app.buttons["Today"].tap();XCTAssertTrue(app.staticTexts["files.pending"].waitForExistence(timeout: 20))
        tap(app.buttons["files.retry"],app);ready(app);XCTAssertFalse(app.staticTexts["files.pending"].exists)
        tap(app.buttons["files.more"],app);label(app.staticTexts["files.loaded"],"Showing 26 files.")
        tap(app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'files.delete.'")).firstMatch,app)
        app.buttons["Confirm delete"].tap();XCTAssertTrue(app.staticTexts["files.failure"].waitForExistence(timeout: 20))
        app.terminate();app.launch();app.buttons["Today"].tap();XCTAssertTrue(app.staticTexts["files.pending"].waitForExistence(timeout: 20))
        tap(app.buttons["files.retry"],app);ready(app)
        tap(app.buttons["files.more"],app);label(app.staticTexts["files.loaded"],"Showing 25 files.")
        // Another authorized client deletes this observed file before our command.
        tap(app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'files.delete.'")).firstMatch,app)
        app.buttons["Confirm delete"].tap();XCTAssertTrue(app.staticTexts["files.failure"].waitForExistence(timeout: 20))
        tap(app.buttons["files.review"],app)
        XCTAssertTrue(app.alerts["Forget local recovery?"].waitForExistence(timeout: 10))
        app.buttons["Keep pending operation"].tap();XCTAssertTrue(app.staticTexts["files.pending"].exists)
        tap(app.buttons["files.review"],app);app.buttons["Forget local recovery"].tap();ready(app)
        XCTAssertFalse(app.staticTexts["files.pending"].exists)
        tap(app.buttons["files.more"],app);label(app.staticTexts["files.loaded"],"Showing 24 files.")
        app.buttons["Account"].tap();tap(app.buttons["Use Starter workspace"],app);label(app.staticTexts["connection.account"],"Selected: Starter workspace")
        app.buttons["Today"].tap();ready(app);XCTAssertTrue(app.staticTexts["Your account has no files."].exists)
        app.buttons["Account"].tap();tap(app.buttons["Use Personal"],app);label(app.staticTexts["connection.account"],"Selected: Personal")
        tap(app.buttons["connection.renew"],app);label(app.staticTexts["connection.account"],"Selected: Personal")
        app.terminate();app.launch();app.buttons["Account"].tap();label(app.staticTexts["connection.account"],"Selected: Personal")
        app.buttons["Today"].tap();ready(app);label(app.staticTexts["files.loaded"],"Showing 20 files.")
        app.buttons["Account"].tap();tap(app.buttons["connection.sign-out"],app);XCTAssertTrue(login.waitForExistence(timeout: 20))
        app.buttons["Today"].tap();XCTAssertTrue(app.staticTexts["Sign in from Account to view your files."].exists)
    }
    func testPendingPhotoBytesSurviveProcessExitBeforeContentIsSent() throws {
        guard ProcessInfo.processInfo.environment["CONNECTED_ACCEPTANCE"] == "1" else { throw XCTSkip("Owned API required") }
        let app=XCUIApplication();app.launch();app.buttons["Account"].tap()
        let login=app.textFields["connection.login"];XCTAssertTrue(login.waitForExistence(timeout: 15))
        tap(login,app);login.typeText("starter-ios-pending-files")
        tap(app.secureTextFields["connection.password"],app);app.secureTextFields["connection.password"].typeText("owned starter test passphrase")
        tap(app.buttons["connection.sign-in"],app);label(app.staticTexts["connection.account"],"Selected: Personal")
        app.buttons["Today"].tap();ready(app);label(app.staticTexts["files.loaded"],"Showing 0 files.")
        tap(app.buttons["files.choose"],app)
        let photo=app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Photo,' AND NOT label CONTAINS ', Photo,'")).firstMatch
        XCTAssertTrue(photo.waitForExistence(timeout: 20));photo.coordinate(withNormalizedOffset: CGVector(dx: 0.5,dy: 0.5)).tap()
        XCTAssertTrue(app.staticTexts["files.draft"].waitForExistence(timeout: 20));tap(app.buttons["files.upload"],app)
        XCTAssertTrue(app.staticTexts["files.failure"].waitForExistence(timeout: 20));XCTAssertTrue(app.staticTexts["files.pending"].exists)
        app.terminate();app.launch();app.buttons["Today"].tap()
        XCTAssertTrue(app.staticTexts["files.pending"].waitForExistence(timeout: 20));XCTAssertFalse(app.buttons["files.choose"].isEnabled)
        tap(app.buttons["files.retry"],app);ready(app);label(app.staticTexts["files.loaded"],"Showing 1 files.")
        XCTAssertFalse(app.staticTexts["files.pending"].exists)
        tap(app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'files.open.'")).firstMatch,app)
        XCTAssertTrue(app.staticTexts["files.verified"].waitForExistence(timeout: 20));app.buttons["Close preview"].tap()
        app.buttons["Account"].tap();tap(app.buttons["connection.sign-out"],app);XCTAssertTrue(login.waitForExistence(timeout: 20))
    }

}
