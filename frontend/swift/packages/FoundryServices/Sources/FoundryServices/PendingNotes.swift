import Foundation
import FoundryHTTP
import FoundryKernel

/// Original deployment/principal/account partition; never contains a credential or lease epoch.
public struct PendingNotesScope: Hashable, Sendable {
    public let authorityID: String
    public let principalID: String
    public let accountID: String
    public init?(authorityID: String, principalID: String, accountID: String) {
        guard !authorityID.isEmpty, authorityID.utf8.count <= 200,
              authorityID.utf8.allSatisfy({ (65...90).contains($0) || (97...122).contains($0) || (48...57).contains($0) || [45, 46, 95].contains($0) }),
              RemoteNotesAdmission.uuid(principalID), RemoteNotesAdmission.uuid(accountID) else { return nil }
        self.authorityID = authorityID; self.principalID = principalID; self.accountID = accountID
    }
}

public enum PendingNoteRejection: String, Sendable { case versionConflict = "notes.version_conflict", notFound = "notes.not_found" }
public enum PendingNoteStatus: String, Sendable { case pending, confirmed, rejected }

/// One bounded slot per scope. A terminal record remains until a new command atomically replaces it.
public struct PendingNoteRecord: Sendable {
    public let scope: PendingNotesScope
    public let command: RemoteNoteWrite
    public let status: PendingNoteStatus
    public let receipt: NoteWriteReceipt?
    public let rejection: PendingNoteRejection?
    public init(scope: PendingNotesScope, command: RemoteNoteWrite) {
        self.scope = scope; self.command = command; status = .pending; receipt = nil; rejection = nil
    }
    private init(scope: PendingNotesScope, command: RemoteNoteWrite, status: PendingNoteStatus,
                 receipt: NoteWriteReceipt?, rejection: PendingNoteRejection?) {
        self.scope = scope; self.command = command; self.status = status; self.receipt = receipt; self.rejection = rejection
    }
    /// Swift String equality normalizes Unicode. Command identity must retain the original UTF-8 bytes.
    public func matches(_ other: RemoteNoteWrite) -> Bool {
        command.commandID == other.commandID && command.action == other.action && command.noteID == other.noteID
            && command.expectedVersion == other.expectedVersion && command.title.utf8.elementsEqual(other.title.utf8)
    }
    public func confirming(_ value: NoteWriteReceipt) -> AppResult<Self> {
        guard value.accountID == scope.accountID, value.principalID == scope.principalID, value.confirms(command), status != .rejected else { return .failure(PendingNotesFailure.conflict) }
        if let receipt, receipt != value { return .failure(PendingNotesFailure.conflict) }
        return .success(Self(scope: scope, command: command, status: .confirmed, receipt: value, rejection: nil))
    }
    public func rejecting(_ value: PendingNoteRejection) -> AppResult<Self> {
        guard status != .confirmed, rejection == nil || rejection == value else { return .failure(PendingNotesFailure.conflict) }
        return .success(Self(scope: scope, command: command, status: .rejected, receipt: nil, rejection: value))
    }
    public func encoded() throws -> Data {
        let c = command
        let receipt: JSONValue = self.receipt.map { .object([
            "command_id": .string($0.commandID), "account_id": .string($0.accountID), "principal_id": .string($0.principalID),
            "action": .string($0.action.rawValue), "note_id": .string($0.noteID), "title": .string($0.title), "version": .number(Double($0.version))
        ]) } ?? .null
        return try JSONEncoder().encode(JSONValue.object([
            "format": .string("pending-notes.v1"),
            "scope": .object(["authority_id": .string(scope.authorityID), "principal_id": .string(scope.principalID), "account_id": .string(scope.accountID)]),
            "command": .object(["command_id": .string(c.commandID), "action": .string(c.action.rawValue), "note_id": c.noteID.map(JSONValue.string) ?? .null,
                "title": .string(c.title), "expected_version": c.expectedVersion.map { .number(Double($0)) } ?? .null]),
            "status": .string(status.rawValue), "receipt": receipt, "rejection": rejection.map { .string($0.rawValue) } ?? .null
        ]))
    }
    public static func decode(_ data: Data, scope expected: PendingNotesScope) -> AppResult<Self> {
        guard data.count <= 16_384, !data.contains(0), String(data: data, encoding: .utf8) != nil,
              let json = try? JSONDecoder().decode(JSONValue.self, from: data), let root = json.object,
              Set(root.keys) == ["format", "scope", "command", "status", "receipt", "rejection"], root["format"]?.string == "pending-notes.v1",
              let s = root["scope"]?.object, Set(s.keys) == ["authority_id", "principal_id", "account_id"],
              s["authority_id"]?.string == expected.authorityID, s["principal_id"]?.string == expected.principalID, s["account_id"]?.string == expected.accountID,
              let c = root["command"]?.object, Set(c.keys) == ["command_id", "action", "note_id", "title", "expected_version"],
              let id = c["command_id"]?.string, let action = c["action"]?.string.flatMap(NoteWriteAction.init(rawValue:)), let title = c["title"]?.string,
              let rawStatus = root["status"]?.string, let status = PendingNoteStatus(rawValue: rawStatus) else { return .failure(PendingNotesFailure.invalid) }
        let answer: AppResult<RemoteNoteWrite>
        switch action {
        case .create:
            guard c["note_id"] == .null, c["expected_version"] == .null else { return .failure(PendingNotesFailure.invalid) }
            answer = RemoteNoteWrite.create(commandID: id, title: title)
        case .update, .delete:
            guard let note = c["note_id"]?.string, let version = integer(c["expected_version"]), action != .delete || title.isEmpty else { return .failure(PendingNotesFailure.invalid) }
            answer = action == .update ? RemoteNoteWrite.update(commandID: id, noteID: note, title: title, expectedVersion: version) : RemoteNoteWrite.delete(commandID: id, noteID: note, expectedVersion: version)
        }
        guard case .success(let command) = answer else { return .failure(PendingNotesFailure.invalid) }
        let pending = Self(scope: expected, command: command)
        switch status {
        case .pending:
            guard root["receipt"] == .null, root["rejection"] == .null else { return .failure(PendingNotesFailure.invalid) }; return .success(pending)
        case .rejected:
            guard root["receipt"] == .null, let reason = root["rejection"]?.string.flatMap(PendingNoteRejection.init(rawValue:)) else { return .failure(PendingNotesFailure.invalid) }
            return pending.rejecting(reason)
        case .confirmed:
            guard root["rejection"] == .null, let r = root["receipt"]?.object,
                  Set(r.keys) == ["command_id", "account_id", "principal_id", "action", "note_id", "title", "version"],
                  let commandID = r["command_id"]?.string, let account = r["account_id"]?.string, let principal = r["principal_id"]?.string,
                  let action = r["action"]?.string.flatMap(NoteWriteAction.init(rawValue:)), let note = r["note_id"]?.string, let title = r["title"]?.string,
                  let version = integer(r["version"]), let receipt = NoteWriteReceipt(commandID: commandID, accountID: account, principalID: principal,
                    action: action, noteID: note, title: title, version: version), case .success(let result) = pending.confirming(receipt) else { return .failure(PendingNotesFailure.invalid) }
            return .success(result)
        }
    }
    private static func integer(_ value: JSONValue?) -> Int64? {
        guard case .number(let n) = value, n >= 1, n <= Double(RemoteNotesAdmission.maxVersion), n.rounded(.towardZero) == n else { return nil }; return Int64(n)
    }
}

/// Persist stage before HTTP. Every transition compares the immutable original command.
public protocol PendingNotesStore: Sendable {
    func load(_ scope: PendingNotesScope) async throws -> AppResult<PendingNoteRecord?>
    func stage(_ command: RemoteNoteWrite, scope: PendingNotesScope, replacing: String?) async throws -> AppResult<PendingNoteRecord>
    func confirm(_ command: RemoteNoteWrite, scope: PendingNotesScope, receipt: NoteWriteReceipt) async throws -> AppResult<PendingNoteRecord>
    func reject(_ command: RemoteNoteWrite, scope: PendingNotesScope, reason: PendingNoteRejection) async throws -> AppResult<PendingNoteRecord>
}

public enum PendingNotesFailure {
    public static var storage: Failure { .unavailable(.init(message: "Saved changes are unavailable. Reload recovery before trying again.", code: "notes.pending_storage")) }
    public static var invalid: Failure { .unavailable(.init(message: "Saved changes could not be read. Stored data has been kept.", code: "notes.pending_invalid")) }
    public static var schema: Failure { .unavailable(.init(message: "Saved changes need a compatible app version. Stored data has been kept.", code: "notes.pending_schema")) }
    public static var conflict: Failure { .conflict(.init(message: "The saved change has changed. Reload recovery before trying again.", code: "notes.pending_conflict")) }
    public static var capacity: Failure { .unavailable(.init(message: "Saved change storage is full. Existing changes have been kept.", code: "notes.pending_capacity")) }
}

/// Transient parity adapter; durable products explicitly select SQLite at composition.
public actor MemoryPendingNotesStore: PendingNotesStore {
    private var records: [PendingNotesScope: PendingNoteRecord] = [:]
    private let maxScopes: Int
    public init(maxScopes: Int = 256) { precondition(maxScopes > 0); self.maxScopes = maxScopes }
    public func load(_ scope: PendingNotesScope) async throws -> AppResult<PendingNoteRecord?> { try Task.checkCancellation(); return .success(records[scope]) }
    public func stage(_ command: RemoteNoteWrite, scope: PendingNotesScope, replacing: String?) async throws -> AppResult<PendingNoteRecord> {
        try Task.checkCancellation()
        if let current = records[scope], current.command.commandID == command.commandID { return current.matches(command) ? .success(current) : .failure(PendingNotesFailure.conflict) }
        guard records[scope]?.command.commandID == replacing, records[scope]?.status != .pending else { return .failure(PendingNotesFailure.conflict) }
        guard records[scope] != nil || records.count < maxScopes else { return .failure(PendingNotesFailure.capacity) }
        let record = PendingNoteRecord(scope: scope, command: command); records[scope] = record; return .success(record)
    }
    public func confirm(_ command: RemoteNoteWrite, scope: PendingNotesScope, receipt: NoteWriteReceipt) async throws -> AppResult<PendingNoteRecord> {
        try Task.checkCancellation(); guard let current = records[scope], current.matches(command) else { return .failure(PendingNotesFailure.conflict) }
        return current.confirming(receipt).map { records[scope] = $0; return $0 }
    }
    public func reject(_ command: RemoteNoteWrite, scope: PendingNotesScope, reason: PendingNoteRejection) async throws -> AppResult<PendingNoteRecord> {
        try Task.checkCancellation(); guard let current = records[scope], current.matches(command) else { return .failure(PendingNotesFailure.conflict) }
        return current.rejecting(reason).map { records[scope] = $0; return $0 }
    }
}
