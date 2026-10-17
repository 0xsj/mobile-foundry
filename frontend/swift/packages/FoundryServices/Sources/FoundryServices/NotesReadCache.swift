import Foundation
import FoundryHTTP
import FoundryKernel

/// Advisory first-page content, never authorization or a write base. The existing
/// deployment/principal/account tuple is shared with the journal, not its storage.
public struct NotesReadSnapshot: Sendable {
    public static let lifetimeMilliseconds: Int64 = 86_400_000
    public static let maxBytes = 65_536
    public let scope: PendingNotesScope
    public let notes: [VersionedNote]
    public let savedAtMilliseconds: Int64
    private let checkpoint: String?

    public init?(scope: PendingNotesScope, notes: [VersionedNote], savedAtMilliseconds: Int64, journal: PendingNoteRecord?) {
        guard notes.count <= 20, Set(notes.map(\.id)).count == notes.count,
              (1...9_007_199_254_740_991).contains(savedAtMilliseconds), journal == nil || journal?.scope == scope else { return nil }
        self.scope = scope; self.notes = notes; self.savedAtMilliseconds = savedAtMilliseconds
        checkpoint = Self.checkpoint(journal)
    }
    public func matches(_ journal: PendingNoteRecord?) -> Bool {
        (journal == nil || journal?.scope == scope) && checkpoint == Self.checkpoint(journal)
    }
    public func isUsable(at now: Int64) -> Bool {
        now >= savedAtMilliseconds && now - savedAtMilliseconds < Self.lifetimeMilliseconds
    }
    private static func checkpoint(_ record: PendingNoteRecord?) -> String? {
        record.map { "\($0.command.commandID):\($0.status.rawValue)" }
    }
    public func encoded() throws -> Data {
        try JSONEncoder().encode(JSONValue.object([
            "format": .string("notes-read-cache.v1"),
            "scope": .object(["authority_id": .string(scope.authorityID), "principal_id": .string(scope.principalID), "account_id": .string(scope.accountID)]),
            "saved_at_ms": .number(Double(savedAtMilliseconds)), "checkpoint": checkpoint.map(JSONValue.string) ?? .null,
            "notes": .array(notes.map { .object(["id": .string($0.id), "title": .string($0.title), "version": .number(Double($0.version))]) })
        ]))
    }
    public static func decode(_ bytes: Data, scope: PendingNotesScope) -> AppResult<Self> {
        guard bytes.count <= maxBytes, !bytes.contains(0), String(data: bytes, encoding: .utf8) != nil,
              let root = (try? JSONDecoder().decode(JSONValue.self, from: bytes))?.object,
              Set(root.keys) == ["format", "scope", "saved_at_ms", "checkpoint", "notes"], root["format"]?.string == "notes-read-cache.v1",
              let s = root["scope"]?.object, Set(s.keys) == ["authority_id", "principal_id", "account_id"],
              s["authority_id"]?.string == scope.authorityID, s["principal_id"]?.string == scope.principalID, s["account_id"]?.string == scope.accountID,
              let saved = integer(root["saved_at_ms"]), case .array(let rows) = root["notes"], rows.count <= 20 else { return .failure(NotesCacheFailure.invalid) }
        var notes: [VersionedNote] = []
        for row in rows {
            guard let n = row.object, Set(n.keys) == ["id", "title", "version"], let id = n["id"]?.string,
                  let title = n["title"]?.string, let version = integer(n["version"]), let note = VersionedNote(id: id, title: title, version: version) else { return .failure(NotesCacheFailure.invalid) }
            notes.append(note)
        }
        guard var snapshot = Self(scope: scope, notes: notes, savedAtMilliseconds: saved, journal: nil) else { return .failure(NotesCacheFailure.invalid) }
        if root["checkpoint"] != .null {
            guard let checkpoint = root["checkpoint"]?.string else { return .failure(NotesCacheFailure.invalid) }
            let parts = checkpoint.split(separator: ":", omittingEmptySubsequences: false)
            guard parts.count == 2, RemoteNotesAdmission.uuid(String(parts[0])), PendingNoteStatus(rawValue: String(parts[1])) != nil else { return .failure(NotesCacheFailure.invalid) }
            snapshot = Self(scope: scope, notes: notes, saved: saved, checkpoint: checkpoint)
        }
        return .success(snapshot)
    }
    private init(scope: PendingNotesScope, notes: [VersionedNote], saved: Int64, checkpoint: String) {
        self.scope = scope; self.notes = notes; savedAtMilliseconds = saved; self.checkpoint = checkpoint
    }
    private static func integer(_ value: JSONValue?) -> Int64? {
        guard case .number(let n) = value, n >= 1, n <= 9_007_199_254_740_991, n.rounded(.towardZero) == n else { return nil }; return Int64(n)
    }
}

/// No HTTP, lease, replay or authentication policy. Owners must gate every read
/// with their current session and compare the restored journal checkpoint.
public protocol NotesReadCache: Sendable {
    func load(_ scope: PendingNotesScope, nowMilliseconds: Int64) async throws -> AppResult<NotesReadSnapshot?>
    func save(_ snapshot: NotesReadSnapshot) async throws -> AppResult<Void>
    func remove(_ scope: PendingNotesScope) async throws -> AppResult<Void>
}
public enum NotesCacheFailure {
    public static var storage: Failure { .unavailable(.init(message: "Saved read cache is unavailable. Server data and pending changes are separate.", code: "notes.cache_storage")) }
    public static var invalid: Failure { .unavailable(.init(message: "Saved read cache could not be read. Stored data has been kept.", code: "notes.cache_invalid")) }
    public static var schema: Failure { .unavailable(.init(message: "Saved read cache needs a compatible app version. Stored data has been kept.", code: "notes.cache_schema")) }
}
public actor MemoryNotesReadCache: NotesReadCache {
    private var snapshots: [PendingNotesScope: NotesReadSnapshot] = [:]
    private let maxScopes: Int
    public init(maxScopes: Int = 32) { precondition((1...32).contains(maxScopes)); self.maxScopes = maxScopes }
    public func load(_ scope: PendingNotesScope, nowMilliseconds: Int64) async throws -> AppResult<NotesReadSnapshot?> {
        try Task.checkCancellation()
        guard let snapshot = snapshots[scope] else { return .success(nil) }
        guard snapshot.isUsable(at: nowMilliseconds) else { snapshots[scope] = nil; return .success(nil) }
        return .success(snapshot)
    }
    public func save(_ snapshot: NotesReadSnapshot) async throws -> AppResult<Void> {
        try Task.checkCancellation()
        guard try snapshot.encoded().count <= NotesReadSnapshot.maxBytes else { return .failure(NotesCacheFailure.invalid) }
        if let current = snapshots[snapshot.scope], current.savedAtMilliseconds > snapshot.savedAtMilliseconds { return .success(()) }
        snapshots[snapshot.scope] = snapshot
        if snapshots.count > maxScopes, let oldest = snapshots.min(by: { $0.value.savedAtMilliseconds < $1.value.savedAtMilliseconds })?.key { snapshots[oldest] = nil }
        return .success(())
    }
    public func remove(_ scope: PendingNotesScope) async throws -> AppResult<Void> {
        try Task.checkCancellation(); snapshots[scope] = nil; return .success(())
    }
}
