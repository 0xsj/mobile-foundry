import XCTest

@MainActor final class ConnectedUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }
    private func dismissStrongPasswordPrompt(_ app: XCUIApplication) {
        if app.buttons["GenerateStrongPasswordButton"].exists, app.buttons["xmark"].isHittable { app.buttons["xmark"].tap() }
    }
    private func reveal(_ node: XCUIElement, app: XCUIApplication) {
        dismissStrongPasswordPrompt(app)
        for _ in 0..<12 {
            if !node.exists { app.swipeUp(); continue }
            let frame = node.frame
            if node.isHittable && frame.minY > app.frame.minY + 100 && frame.maxY < app.frame.maxY - 90 { return }
            if frame.minY <= app.frame.minY + 100 { app.swipeDown() } else { app.swipeUp() }
        }
        if !node.isHittable {
            let screenshot = XCTAttachment(screenshot: app.screenshot()); screenshot.lifetime = .keepAlways; add(screenshot)
            XCTFail("Could not reveal \(node.identifier). Current screen: \(app.debugDescription)")
        }
    }
    private func waitForState(_ node: XCUIElement, _ predicate: String) {
        expectation(for: NSPredicate(format: predicate), evaluatedWith: node); waitForExpectations(timeout: 15)
    }
    private func tap(_ node: XCUIElement, app: XCUIApplication) { reveal(node, app: app); node.tap() }
    private func selected(_ name: String, app: XCUIApplication) {
        let label = app.staticTexts["connection.account"]
        expectation(for: NSPredicate(format: "label == %@", "Selected: \(name)"), evaluatedWith: label)
        waitForExpectations(timeout: 15)
    }
    private func replace(_ field: XCUIElement, with text: String, app: XCUIApplication) {
        tap(field, app: app)
        // Fixture names fit the field. Put the insertion point after the text;
        // tapping its middle can leave a suffix behind when deleting backwards.
        field.coordinate(withNormalizedOffset: CGVector(dx: 0.98, dy: 0.5)).tap()
        if let value = field.value as? String, value != field.placeholderValue { field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: value.count)) }
        field.typeText(text)
        if field.elementType != .secureTextField { XCTAssertEqual(field.value as? String, text) }
    }
    func testWorkspacesCreationRenameConflictAndUncertainty() throws {
        guard ProcessInfo.processInfo.environment["CONNECTED_ACCEPTANCE"] == "1" else { throw XCTSkip("Owned API required") }
        let app = XCUIApplication(); app.launch(); app.buttons["Account"].tap()
        let login = app.textFields["connection.login"]; XCTAssertTrue(login.waitForExistence(timeout: 15))
        replace(login, with: "starter-ios-workspaces", app: app)
        replace(app.secureTextFields["connection.password"], with: "owned starter test passphrase", app: app)
        tap(app.buttons["connection.sign-in"], app: app); selected("Personal", app: app)
        tap(app.buttons["workspace.open"], app: app)
        let current = app.staticTexts["workspace.current"]; XCTAssertTrue(current.waitForExistence(timeout: 15))
        replace(app.textFields["workspace.create-name"], with: "Product workspace iOS", app: app)
        tap(app.buttons["workspace.create"], app: app)
        XCTAssertTrue(app.staticTexts["workspace.created"].waitForExistence(timeout: 15))
        app.buttons["workspace.close"].tap(); selected("Personal", app: app)
        tap(app.buttons["Use Product workspace iOS"], app: app); selected("Product workspace iOS", app: app)
        app.buttons["Library"].tap(); XCTAssertTrue(app.staticTexts["notes.empty"].waitForExistence(timeout: 15))
        replace(app.textFields["notes.title"], with: "Workspace-owned iOS note", app: app); tap(app.buttons["notes.save"], app: app)
        XCTAssertTrue(app.staticTexts["Workspace-owned iOS note"].waitForExistence(timeout: 15))
        app.buttons["Account"].tap(); tap(app.buttons["workspace.open"], app: app); XCTAssertTrue(current.waitForExistence(timeout: 15))
        let rename = app.textFields["workspace.rename-name"]
        replace(rename, with: "Renamed workspace iOS", app: app); tap(app.buttons["workspace.rename"], app: app)
        waitForState(current, "label == 'Current: Renamed workspace iOS'")
        replace(rename, with: "Workspace conflict iOS", app: app); tap(app.buttons["workspace.rename"], app: app)
        XCTAssertTrue(app.staticTexts["workspace.error"].waitForExistence(timeout: 15)); XCTAssertFalse(app.buttons["workspace.rename"].isEnabled)
        tap(app.buttons["workspace.refresh"], app: app); waitForState(current, "label == 'Current: Concurrent workspace iOS'")
        replace(rename, with: "Reviewed workspace iOS", app: app); tap(app.buttons["workspace.rename"], app: app)
        waitForState(current, "label == 'Current: Reviewed workspace iOS'")
        replace(rename, with: "Unconfirmed rename iOS", app: app); tap(app.buttons["workspace.rename"], app: app)
        XCTAssertTrue(app.staticTexts["workspace.error"].waitForExistence(timeout: 15)); XCTAssertFalse(app.buttons["workspace.rename"].isEnabled)
        tap(app.buttons["workspace.refresh"], app: app); waitForState(current, "label == 'Current: Unconfirmed rename iOS'")
        replace(app.textFields["workspace.create-name"], with: "Unconfirmed workspace iOS", app: app); tap(app.buttons["workspace.create"], app: app)
        XCTAssertTrue(app.staticTexts["workspace.uncertain"].waitForExistence(timeout: 15)); XCTAssertFalse(app.buttons["workspace.create"].isEnabled)
        tap(app.buttons["workspace.refresh"], app: app); waitForState(current, "label == 'Current: Unconfirmed rename iOS'")
        XCTAssertFalse(app.buttons["workspace.create"].isEnabled)
        tap(app.buttons["workspace.review"], app: app)
        XCTAssertTrue(app.staticTexts["Unconfirmed workspace iOS"].waitForExistence(timeout: 15))
        tap(app.buttons["workspace.allow-create"], app: app)
        app.buttons["workspace.close"].tap(); selected("Unconfirmed rename iOS", app: app)
        tap(app.buttons["Use Unconfirmed workspace iOS"], app: app); selected("Unconfirmed workspace iOS", app: app)
        app.buttons["Library"].tap(); XCTAssertTrue(app.staticTexts["notes.empty"].waitForExistence(timeout: 15)); XCTAssertFalse(app.staticTexts["Workspace-owned iOS note"].exists)
        app.terminate(); app.launch(); app.buttons["Account"].tap(); selected("Unconfirmed workspace iOS", app: app)
        tap(app.buttons["Use Unconfirmed rename iOS"], app: app); selected("Unconfirmed rename iOS", app: app)
        app.buttons["Library"].tap(); XCTAssertTrue(app.staticTexts["Workspace-owned iOS note"].waitForExistence(timeout: 15))
        app.buttons["Account"].tap(); tap(app.buttons["connection.sign-out"], app: app)
        XCTAssertTrue(app.textFields["connection.login"].waitForExistence(timeout: 15))
    }
    func testSecurityFlowsAndLiveReadFromAnotherClient() async throws {
        guard ProcessInfo.processInfo.environment["CONNECTED_ACCEPTANCE"] == "1" else { throw XCTSkip("Owned API required") }
        let base = try XCTUnwrap(ProcessInfo.processInfo.environment["GO_SESSION_TEST_BASE"])
        let mailbox = try XCTUnwrap(ProcessInfo.processInfo.environment["GO_LIFECYCLE_MAIL_BASE"])
        let app = XCUIApplication(); app.launch(); app.buttons["Account"].tap()
        let login = app.textFields["connection.login"], password = app.secureTextFields["connection.password"]
        XCTAssertTrue(login.waitForExistence(timeout: 15)); tap(login, app: app); login.typeText("starter-ios-security")
        tap(password, app: app); password.typeText("owned starter test passphrase")
        tap(app.buttons["connection.sign-in"], app: app); selected("Personal", app: app)
        app.buttons["Library"].tap()
        let live = app.staticTexts["live.status"]
        for _ in 0..<150 { if live.exists && live.label == "Live updates connected." { break }; try await Task.sleep(for: .milliseconds(100)) }
        XCTAssertEqual(live.label, "Live updates connected.")
        let grant = try await lifecycleHTTP(base + "/identity/sign-in", body: ["login": "starter-ios-security", "password": "owned starter test passphrase"])
        let token = try XCTUnwrap(grant["access_token"] as? String)
        let account = try await lifecycleHTTP(base + "/accounts/bootstrap", body: ["name": "Personal"], bearer: token)
        let accountID = try XCTUnwrap(account["id"] as? String)
        _ = try await lifecycleHTTP(base + "/accounts/\(accountID)/notes", body: ["title": "Live iOS external change"], bearer: token, command: UUID().uuidString.lowercased())
        XCTAssertTrue(app.staticTexts["Live iOS external change"].waitForExistence(timeout: 15))
        app.buttons["Account"].tap(); tap(app.buttons["identity.open"], app: app)
        let email = "starter-ios-security@example.com"
        let address = app.textFields["identity.email"]
        XCTAssertTrue(address.waitForExistence(timeout: 5)); tap(address, app: app); address.typeText(email)
        tap(app.secureTextFields["identity.current-password"], app: app); app.secureTextFields["identity.current-password"].typeText("owned starter test passphrase")
        tap(app.buttons["identity.request-verification"], app: app)
        let code = try await lifecycleCode(mailbox, email: email, purpose: "verify_email")
        tap(app.textFields["identity.code"], app: app); app.textFields["identity.code"].typeText(code)
        tap(app.buttons["identity.verify"], app: app)
        XCTAssertTrue(app.staticTexts["Email verified. Refresh the verified address to confirm."].waitForExistence(timeout: 15))
        app.segmentedControls["identity.mode"].buttons["Change password"].tap()
        tap(app.secureTextFields["identity.change-current-password"], app: app); app.secureTextFields["identity.change-current-password"].typeText("owned starter test passphrase")
        tap(app.secureTextFields["identity.new-password"], app: app); _ = app.buttons["GenerateStrongPasswordButton"].waitForExistence(timeout: 1); dismissStrongPasswordPrompt(app); app.secureTextFields["identity.new-password"].typeText("new owned security passphrase")
        tap(app.buttons["identity.change"], app: app)
        XCTAssertTrue(app.buttons["identity.request-reset"].waitForExistence(timeout: 15))
        // The sheet stays open in its signed-out recovery presentation.
        tap(app.buttons["identity.request-reset"], app: app)
        let reset = try await lifecycleCode(mailbox, email: email, purpose: "reset_password")
        tap(app.textFields["identity.code"], app: app); app.textFields["identity.code"].typeText(reset)
        tap(app.secureTextFields["identity.new-password"], app: app); _ = app.buttons["GenerateStrongPasswordButton"].waitForExistence(timeout: 1); dismissStrongPasswordPrompt(app); app.secureTextFields["identity.new-password"].typeText("recovered owned security password")
        tap(app.buttons["identity.reset"], app: app)
        XCTAssertTrue(app.staticTexts["Password reset. Sign in with your new password."].waitForExistence(timeout: 15))
        app.buttons["identity.close"].tap()
        tap(login, app: app); login.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: (login.value as? String ?? "").count)); login.typeText("starter-ios-security")
        tap(password, app: app); password.typeText("recovered owned security password")
        tap(app.buttons["connection.sign-in"], app: app); selected("Personal", app: app)
        tap(app.buttons["connection.sign-out"], app: app)
        XCTAssertTrue(login.waitForExistence(timeout: 15))
    }

    func testSavedReadCacheThroughOutageAndProcessRelaunch() async throws {
        guard ProcessInfo.processInfo.environment["CONNECTED_ACCEPTANCE"] == "1" else { throw XCTSkip("Owned API required") }
        let base = try XCTUnwrap(ProcessInfo.processInfo.environment["GO_SESSION_TEST_BASE"])
        let app = XCUIApplication(); app.launch(); app.buttons["Account"].tap()
        let login = app.textFields["connection.login"], password = app.secureTextFields["connection.password"]
        XCTAssertTrue(login.waitForExistence(timeout: 15)); tap(login, app: app); login.typeText("starter-ios-cache")
        tap(password, app: app); password.typeText("owned starter test passphrase")
        tap(app.buttons["connection.sign-in"], app: app); selected("Personal", app: app)
        app.buttons["Library"].tap(); XCTAssertTrue(app.staticTexts["notes.empty"].waitForExistence(timeout: 15))
        let title = app.textFields["notes.title"]
        tap(title, app: app); title.typeText("Saved iOS first page")
        tap(app.buttons["notes.save"], app: app); XCTAssertTrue(app.staticTexts["Saved iOS first page"].waitForExistence(timeout: 15))
        waitForState(title, "enabled == true")
        let grant = try await lifecycleHTTP(base + "/identity/sign-in", body: ["login": "starter-ios-cache", "password": "owned starter test passphrase"])
        let token = try XCTUnwrap(grant["access_token"] as? String)
        let account = try await lifecycleHTTP(base + "/accounts/bootstrap", body: ["name": "Personal"], bearer: token)
        let accountID = try XCTUnwrap(account["id"] as? String)
        try await cacheOutage(base, account: accountID, blocked: true)
        tap(app.buttons["Refresh notes"], app: app)
        XCTAssertTrue(app.staticTexts["notes.cached"].waitForExistence(timeout: 15)); XCTAssertFalse(title.isEnabled)
        XCTAssertTrue(app.staticTexts["Saved iOS first page"].exists)
        app.terminate(); app.launch(); app.buttons["Account"].tap(); selected("Personal", app: app)
        app.buttons["Library"].tap()
        XCTAssertTrue(app.staticTexts["notes.cached"].waitForExistence(timeout: 15)); XCTAssertTrue(app.staticTexts["Saved iOS first page"].exists)
        XCTAssertFalse(title.isEnabled)
        app.buttons["Account"].tap(); tap(app.buttons["Use Starter workspace"], app: app); selected("Starter workspace", app: app)
        app.buttons["Library"].tap(); XCTAssertTrue(app.staticTexts["notes.empty"].waitForExistence(timeout: 15))
        XCTAssertFalse(app.staticTexts["Saved iOS first page"].exists)
        app.buttons["Account"].tap(); tap(app.buttons["Use Personal"], app: app); selected("Personal", app: app)
        app.buttons["Library"].tap(); XCTAssertTrue(app.staticTexts["notes.cached"].waitForExistence(timeout: 15))
        tap(app.buttons["notes.cache.remove"], app: app)
        waitForState(app.staticTexts["notes.cached"], "exists == false")
        XCTAssertFalse(app.staticTexts["Saved iOS first page"].exists)
        try await cacheOutage(base, account: accountID, blocked: false)
        tap(app.buttons["Refresh notes"], app: app)
        waitForState(title, "enabled == true")
        XCTAssertTrue(app.staticTexts["Saved iOS first page"].exists); XCTAssertFalse(app.staticTexts["notes.cached"].exists)
        app.buttons["Account"].tap(); tap(app.buttons["connection.sign-out"], app: app)
        XCTAssertTrue(login.waitForExistence(timeout: 15)); app.buttons["Library"].tap()
        XCTAssertTrue(app.staticTexts["Sign in from Account to view your notes."].waitForExistence(timeout: 15))
    }
    private func cacheOutage(_ base: String, account: String, blocked: Bool) async throws {
        var parts = URLComponents(string: base)!; parts.path = "/_acceptance/notes-reads"
        var request = URLRequest(url: parts.url!); request.httpMethod = "POST"; request.timeoutInterval = 5
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["account_id": account, "blocked": blocked])
        let (_, response) = try await URLSession.shared.data(for: request)
        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 204)
    }

    func testRegistrationOnboardingDuplicateAndUncertainAcknowledgement() throws {
        guard ProcessInfo.processInfo.environment["CONNECTED_ACCEPTANCE"] == "1" else { throw XCTSkip("Owned API required") }
        let app = XCUIApplication(); app.launch(); app.buttons["Account"].tap()
        let login = app.textFields["connection.login"], password = app.secureTextFields["connection.password"]
        XCTAssertTrue(login.waitForExistence(timeout: 15))
        let registeredLogin = "starter-ios-registration"
        tap(app.buttons["registration.open"], app: app)
        fillRegistration(app, login: registeredLogin.uppercased(), name: "New iOS member", password: "short")
        tap(app.buttons["registration.submit"], app: app)
        XCTAssertTrue(app.staticTexts["Check your account details."].waitForExistence(timeout: 15))
        XCTAssertTrue(app.staticTexts["registration.error.password"].exists)
        XCTAssertTrue(["", "New password"].contains(app.secureTextFields["registration.password"].value as? String ?? "missing"))
        enterRegistrationPassword(app, "owned starter test passphrase")
        tap(app.buttons["registration.submit"], app: app)
        XCTAssertTrue(app.staticTexts["Account created. Sign in to open your personal workspace."].waitForExistence(timeout: 15))
        XCTAssertFalse(app.secureTextFields["registration.password"].exists)
        tap(app.buttons["registration.continue"], app: app)
        XCTAssertTrue(login.waitForExistence(timeout: 15)); XCTAssertEqual(login.value as? String, registeredLogin)
        XCTAssertTrue(["", "Password"].contains(password.value as? String ?? "missing"))
        tap(password, app: app); password.typeText("owned starter test passphrase")
        tap(app.buttons["connection.sign-in"], app: app); selected("Personal", app: app)
        app.buttons["Library"].tap(); XCTAssertTrue(app.staticTexts["notes.empty"].waitForExistence(timeout: 15))
        let title = app.textFields["notes.title"]; tap(title, app: app); title.typeText("Registered iOS note")
        tap(app.buttons["notes.save"], app: app); XCTAssertTrue(app.staticTexts["Registered iOS note"].waitForExistence(timeout: 15)); waitForState(title, "enabled == true")
        app.terminate(); app.launch(); app.buttons["Account"].tap(); selected("Personal", app: app)
        app.buttons["Library"].tap(); XCTAssertTrue(app.staticTexts["Registered iOS note"].waitForExistence(timeout: 15))
        app.buttons["Account"].tap(); tap(app.buttons["connection.sign-out"], app: app)
        XCTAssertTrue(login.waitForExistence(timeout: 15)); tap(app.buttons["registration.open"], app: app)
        fillRegistration(app, login: registeredLogin, name: "Replacement", password: "a different duplicate password")
        tap(app.buttons["registration.submit"], app: app)
        XCTAssertTrue(app.staticTexts["That login is already registered."].waitForExistence(timeout: 15))
        tap(app.buttons["registration.continue"], app: app)
        tap(password, app: app); password.typeText("owned starter test passphrase")
        tap(app.buttons["connection.sign-in"], app: app); selected("Personal", app: app)
        tap(app.buttons["connection.sign-out"], app: app); XCTAssertTrue(login.waitForExistence(timeout: 15))
        tap(app.buttons["registration.open"], app: app)
        fillRegistration(app, login: registeredLogin + "-uncertain", name: "Unconfirmed iOS member", password: "owned starter test passphrase")
        tap(app.buttons["registration.submit"], app: app)
        let uncertain = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "Account creation was not confirmed.")).firstMatch
        XCTAssertTrue(uncertain.waitForExistence(timeout: 15))
        XCTAssertTrue(["", "New password"].contains(app.secureTextFields["registration.password"].value as? String ?? "missing"))
        tap(app.buttons["registration.continue"], app: app)
        XCTAssertEqual(login.value as? String, registeredLogin + "-uncertain")
        tap(password, app: app); password.typeText("owned starter test passphrase")
        tap(app.buttons["connection.sign-in"], app: app); selected("Personal", app: app)
        tap(app.buttons["connection.sign-out"], app: app); XCTAssertTrue(login.waitForExistence(timeout: 15))
    }
    private func fillRegistration(_ app: XCUIApplication, login: String, name: String, password: String) {
        let field = app.textFields["registration.login"]
        XCTAssertTrue(field.waitForExistence(timeout: 10)); tap(field, app: app); field.typeText(login)
        tap(app.textFields["registration.name"], app: app); app.textFields["registration.name"].typeText(name)
        enterRegistrationPassword(app, password)
    }
    private func enterRegistrationPassword(_ app: XCUIApplication, _ value: String) {
        let field = app.secureTextFields["registration.password"]
        tap(field, app: app); _ = app.buttons["GenerateStrongPasswordButton"].waitForExistence(timeout: 1)
        dismissStrongPasswordPrompt(app); field.typeText(value)
    }

    private func lifecycleHTTP(_ url: String, body: [String: String]? = nil, bearer: String? = nil, command: String? = nil) async throws -> [String: Any] {
        var request = URLRequest(url: URL(string: url)!); request.timeoutInterval = 10
        request.httpMethod = body == nil ? "GET" : "POST"
        request.httpBody = try body.map { try JSONSerialization.data(withJSONObject: $0) }
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let bearer { request.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization") }
        if let command { request.setValue(command, forHTTPHeaderField: "Idempotency-Key") }
        let (data, response) = try await URLSession.shared.data(for: request)
        XCTAssertTrue([200, 201, 202, 204, 404].contains((response as? HTTPURLResponse)?.statusCode ?? 0))
        return data.isEmpty ? [:] : try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }
    private func lifecycleCode(_ base: String, email: String, purpose: String) async throws -> String {
        var url = URLComponents(string: base + "/code")!; url.queryItems = [.init(name: "email", value: email), .init(name: "purpose", value: purpose)]
        for _ in 0..<100 {
            if let token = try await lifecycleHTTP(url.url!.absoluteString)["token"] as? String { return token }
            try await Task.sleep(for: .milliseconds(50))
        }
        throw NSError(domain: "Owned SMTP fixture", code: 1)
    }
    func testConnectedAccountsNotesAndKeychainAfterProcessRelaunch() throws {
        guard ProcessInfo.processInfo.environment["CONNECTED_ACCEPTANCE"] == "1" else {
            throw XCTSkip("make connected-test owns the required local API and users")
        }
        let app = XCUIApplication(); app.launch()
        app.buttons["Account"].tap()
        let login = app.textFields["connection.login"], password = app.secureTextFields["connection.password"]
        XCTAssertTrue(login.waitForExistence(timeout: 10)); tap(login, app: app); login.typeText("starter-ios")
        tap(password, app: app); password.typeText("owned starter test passphrase")
        tap(app.buttons["connection.sign-in"], app: app); selected("Personal", app: app)
        tap(app.buttons["Use Starter workspace"], app: app); selected("Starter workspace", app: app)
        app.buttons["Library"].tap()
        XCTAssertTrue(app.staticTexts["notes.empty"].waitForExistence(timeout: 15))
        let title = app.textFields["notes.title"]
        reveal(title, app: app)
        expectation(for: NSPredicate(format: "enabled == true"), evaluatedWith: title); waitForExpectations(timeout: 15)
        tap(title, app: app); title.typeText("Independent iOS note")
        tap(app.buttons["notes.save"], app: app)
        XCTAssertTrue(app.staticTexts["Independent iOS note"].waitForExistence(timeout: 15))
        app.buttons["Account"].tap(); tap(app.buttons["connection.renew"], app: app); selected("Starter workspace", app: app)
        app.terminate(); app.launch(); app.buttons["Account"].tap(); selected("Starter workspace", app: app)
        app.buttons["Library"].tap(); XCTAssertTrue(app.staticTexts["Independent iOS note"].waitForExistence(timeout: 15))
        let edit = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "notes.edit.")).firstMatch
        tap(edit, app: app); tap(title, app: app)
        title.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: "Independent iOS note".count)); title.typeText("Edited iOS note")
        tap(app.buttons["notes.save"], app: app); XCTAssertTrue(app.staticTexts["Edited iOS note"].waitForExistence(timeout: 15))
        app.buttons["Account"].tap(); tap(app.buttons["Use Personal"], app: app); selected("Personal", app: app)
        app.buttons["Library"].tap(); XCTAssertTrue(app.staticTexts["notes.empty"].waitForExistence(timeout: 15)); XCTAssertFalse(app.staticTexts["Edited iOS note"].exists)
        app.buttons["Account"].tap(); tap(app.buttons["Use Starter workspace"], app: app); selected("Starter workspace", app: app)
        app.buttons["Library"].tap(); XCTAssertTrue(app.staticTexts["Edited iOS note"].waitForExistence(timeout: 15))
        let delete = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "notes.delete.")).firstMatch
        tap(delete, app: app); app.buttons["Delete note"].tap()
        XCTAssertTrue(app.staticTexts["notes.empty"].waitForExistence(timeout: 15))
        // The owned HTTP proxy withholds a committed response, then refuses an unsent first attempt.
        for kind in ["committed", "unsent"] {
            reveal(title, app: app)
            expectation(for: NSPredicate(format: "enabled == true"), evaluatedWith: title); waitForExpectations(timeout: 15)
            tap(title, app: app); title.typeText("Recover \(kind) iOS")
            tap(app.buttons["notes.save"], app: app)
            let saved = app.staticTexts.matching(NSPredicate(format: "identifier BEGINSWITH %@", "notes.pending.")).firstMatch
            XCTAssertTrue(saved.waitForExistence(timeout: 15)); let original = saved.identifier
            app.terminate(); app.launch(); app.buttons["Account"].tap(); selected("Starter workspace", app: app)
            app.buttons["Library"].tap()
            XCTAssertTrue(app.staticTexts[original].waitForExistence(timeout: 15))
            tap(app.buttons["Check receipt"], app: app)
            if kind == "unsent" {
                let failure = app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "No committed receipt")).firstMatch
                XCTAssertTrue(failure.waitForExistence(timeout: 15))
                XCTAssertTrue(app.staticTexts[original].exists)
                tap(app.buttons["Retry same change"], app: app)
            }
            expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: app.staticTexts[original]); waitForExpectations(timeout: 15)
            expectation(for: NSPredicate(format: "enabled == true"), evaluatedWith: title); waitForExpectations(timeout: 15)
            let remove = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "notes.delete.")).firstMatch
            tap(remove, app: app); app.buttons["Delete note"].tap()
            XCTAssertTrue(app.staticTexts["notes.empty"].waitForExistence(timeout: 15))
        }
        app.buttons["Account"].tap(); tap(app.buttons["connection.sign-out"], app: app)
        XCTAssertTrue(app.textFields["connection.login"].waitForExistence(timeout: 15))
        app.terminate(); app.launch(); app.buttons["Library"].tap()
        XCTAssertTrue(app.staticTexts["Sign in from Account to view your notes."].waitForExistence(timeout: 10))
        let screenshot = XCTAttachment(screenshot: app.screenshot()); screenshot.lifetime = .keepAlways; add(screenshot)
    }
}
