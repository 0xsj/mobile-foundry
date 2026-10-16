import XCTest

@MainActor final class GoSessionUITests: XCTestCase {
    private func reveal(_ element: XCUIElement, in app: XCUIApplication) {
        // SwiftUI reports controls behind translucent navigation chrome as hittable.
        // Admit only controls fully inside the content viewport, in either direction.
        for _ in 0..<10 {
            if !element.exists { app.swipeUp(); continue }
            let frame = element.frame
            if element.isHittable && frame.minY >= app.frame.minY + 130 && frame.maxY <= app.frame.maxY - 40 { return }
            if frame.minY < app.frame.minY + 130 { app.swipeDown() } else { app.swipeUp() }
        }
        XCTAssertTrue(element.isHittable)
    }
    private func open(_ base: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-catalog.go_api_base", base]
        app.launch()
        app.buttons["Studio"].tap(); app.buttons["Open catalog"].tap()
        let link = app.buttons["Go identity and accounts"].firstMatch
        reveal(link, in: app); link.tap()
        app.buttons["go.connect"].tap()
        return app
    }
    private func waitPhase(_ value: String, app: XCUIApplication) {
        expectation(for: NSPredicate(format: "label == %@", value), evaluatedWith: app.staticTexts["go.phase"])
        waitForExpectations(timeout: 12)
    }
    func testDeviceCredentialRestoresSelectedWorkspaceAfterProcessRelaunch() throws {
        guard let base = ProcessInfo.processInfo.environment["GO_SESSION_TEST_BASE"] else {
            throw XCTSkip("make go-native-device-test supplies an owned Go API")
        }
        let app = open(base)
        waitPhase("signedOut", app: app)
        let login = app.textFields["Login"], password = app.secureTextFields["Password"]
        reveal(login, in: app); login.tap(); login.typeText("native-ui-ios")
        reveal(password, in: app); password.tap(); password.typeText("native integration passphrase")
        let signIn = app.buttons["go.sign-in"]; reveal(signIn, in: app); signIn.tap()
        waitPhase("signedIn", app: app)
        let select = app.buttons["Use iOS workspace"]
        XCTAssertTrue(select.waitForExistence(timeout: 10)); reveal(select, in: app); select.tap()
        waitPhase("signedIn", app: app)
        XCTAssertEqual(app.staticTexts["go.selected-account"].label, "Selected: iOS workspace")
        let accountID = app.staticTexts["go.account-id"].label
        let renew = app.buttons["go.renew"]
        // Controls are above the account list after selection.
        for _ in 0..<8 { if renew.isHittable { break }; app.swipeDown() }
        renew.tap(); waitPhase("signedIn", app: app)
        XCTAssertEqual(app.staticTexts["go.account-id"].label, accountID)
        let title = app.textFields["go.notes.title"]; reveal(title,in:app)
        expectation(for:NSPredicate(format:"enabled == true"),evaluatedWith:title); waitForExpectations(timeout:10)
        title.tap(); title.typeText("iOS connected note")
        let save = app.buttons["go.notes.save"]; reveal(save,in:app); save.tap()
        XCTAssertTrue(app.staticTexts["iOS connected note"].waitForExistence(timeout:10))
        app.terminate()
        let restored = open(base)
        waitPhase("signedIn", app: restored)
        XCTAssertEqual(restored.staticTexts["go.account-id"].label, accountID)
        XCTAssertEqual(restored.staticTexts["go.selected-account"].label, "Selected: iOS workspace")
        let persisted = restored.staticTexts["iOS connected note"]; XCTAssertTrue(persisted.waitForExistence(timeout:10)); reveal(persisted,in:restored)
        let edit = restored.buttons.matching(NSPredicate(format:"identifier BEGINSWITH %@","go.notes.edit.")).firstMatch; reveal(edit,in:restored); edit.tap()
        let draft = restored.textFields["go.notes.title"]
        for _ in 0..<8 { if draft.isHittable { break };restored.swipeDown() }
        draft.tap();draft.typeText(String(repeating:XCUIKeyboardKey.delete.rawValue,count:"iOS connected note".count));draft.typeText("iOS edited note")
        let editedSave = restored.buttons["go.notes.save"];reveal(editedSave,in:restored);editedSave.tap()
        XCTAssertTrue(restored.staticTexts["iOS edited note"].waitForExistence(timeout:10))
        let delete = restored.buttons.matching(NSPredicate(format:"identifier BEGINSWITH %@","go.notes.delete.")).firstMatch;reveal(delete,in:restored);delete.tap()
        let confirmations = restored.buttons.matching(NSPredicate(format:"label == %@","Delete"))
        confirmations.element(boundBy:confirmations.count-1).tap()
        XCTAssertTrue(restored.staticTexts["Confirmed delete, version 3"].waitForExistence(timeout:10))
        let signOut = restored.buttons["go.sign-out"]
        reveal(signOut, in: restored); signOut.tap()
        waitPhase("signedOut", app: restored)
        restored.terminate()
        let signedOut = open(base)
        waitPhase("signedOut", app: signedOut)
        XCTAssertFalse(signedOut.staticTexts["go.selected-account"].exists)
        let screenshot = XCTAttachment(screenshot: signedOut.screenshot()); screenshot.lifetime = .keepAlways; add(screenshot)
    }
}
