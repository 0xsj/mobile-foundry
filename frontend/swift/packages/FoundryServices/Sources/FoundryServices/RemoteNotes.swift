import FoundryKernel

/// Remote writes have explicit versions/command identity; local CRUD keeps its existing semantics.
public struct VersionedNote: Equatable, Identifiable, Sendable {
    public let id: String
    public let title: String
    public let version: Int64
    public init?(id: String, title: String, version: Int64) {
        guard RemoteNotesAdmission.uuid(id), RemoteNotesAdmission.title(title), (1...RemoteNotesAdmission.maxVersion).contains(version) else { return nil }
        self.id = id; self.title = title; self.version = version
    }
}
public enum NoteWriteAction: String, Sendable { case create, update, delete }
public struct RemoteNoteWrite: Equatable, Sendable {
    public let commandID: String
    public let action: NoteWriteAction
    public let noteID: String?
    public let title: String
    public let expectedVersion: Int64?
    private init(commandID: String, action: NoteWriteAction, noteID: String?, title: String, expectedVersion: Int64?) {
        self.commandID = commandID; self.action = action; self.noteID = noteID; self.title = title; self.expectedVersion = expectedVersion
    }
    /// Generate and retain the ID before dispatch; retries must reuse this exact value and input.
    public static func create(commandID: String, title: String) -> AppResult<Self> { make(commandID: commandID, action: .create, noteID: nil, title: title, expectedVersion: nil) }
    public static func update(commandID: String, noteID: String, title: String, expectedVersion: Int64) -> AppResult<Self> { make(commandID: commandID, action: .update, noteID: noteID, title: title, expectedVersion: expectedVersion) }
    public static func delete(commandID: String, noteID: String, expectedVersion: Int64) -> AppResult<Self> { make(commandID: commandID, action: .delete, noteID: noteID, title: "", expectedVersion: expectedVersion) }
    private static func make(commandID: String, action: NoteWriteAction, noteID: String?, title: String, expectedVersion: Int64?) -> AppResult<Self> {
        guard RemoteNotesAdmission.uuid(commandID), action == .create || (RemoteNotesAdmission.uuid(noteID ?? "") && (1..<RemoteNotesAdmission.maxVersion).contains(expectedVersion ?? 0)) else { return .failure(RemoteNotesAdmission.invalid) }
        if action != .delete { if case .failure(let failure) = CreateNote.make(title: title) { return .failure(failure) } }
        return .success(Self(commandID: commandID, action: action, noteID: noteID, title: title, expectedVersion: expectedVersion))
    }
}
public struct NoteWriteReceipt: Equatable, Sendable {
    public let commandID: String
    public let accountID: String
    public let principalID: String
    public let action: NoteWriteAction
    public let noteID: String
    public let title: String
    public let version: Int64
    public init?(commandID: String, accountID: String, principalID: String, action: NoteWriteAction, noteID: String, title: String, version: Int64) {
        guard [commandID, accountID, principalID, noteID].allSatisfy(RemoteNotesAdmission.uuid), (1...RemoteNotesAdmission.maxVersion).contains(version),
              action == .create ? version == 1 && RemoteNotesAdmission.title(title) : version >= 2 && (action == .delete ? title.isEmpty : RemoteNotesAdmission.title(title)) else { return nil }
        self.commandID = commandID; self.accountID = accountID; self.principalID = principalID; self.action = action; self.noteID = noteID; self.title = title; self.version = version
    }
    public func confirms(_ command: RemoteNoteWrite) -> Bool {
        commandID == command.commandID && action == command.action && title.utf8.elementsEqual(command.title.utf8)
            && (action == .create || (noteID == command.noteID && version == (command.expectedVersion ?? 0) + 1))
    }
}
public struct RemoteNotesPage: Equatable, Sendable {
    public let notes: [VersionedNote]
    public let nextAfter: String?
    public init(notes: [VersionedNote], nextAfter: String?) { self.notes = notes; self.nextAfter = nextAfter }
}
public protocol RemoteNotesReader: Sendable {
    func page(after: String?, limit: Int) async throws -> AppResult<RemoteNotesPage>
    func get(_ noteID: String) async throws -> AppResult<VersionedNote>
}
public protocol RemoteNotesWriter: Sendable {
    func execute(_ command: RemoteNoteWrite) async throws -> AppResult<NoteWriteReceipt>
    /// Not-found means no committed receipt observed yet; an in-flight write may still commit.
    func receipt(for command: RemoteNoteWrite) async throws -> AppResult<NoteWriteReceipt>
}
public protocol RemoteNotesRepository: RemoteNotesReader, RemoteNotesWriter {}
enum RemoteNotesAdmission {
    static let maxVersion: Int64 = 9_007_199_254_740_991
    static func uuid(_ value: String) -> Bool {
        let bytes = Array(value.utf8)
        guard bytes.count == 36, value != "00000000-0000-0000-0000-000000000000" else { return false }
        return bytes.enumerated().allSatisfy { index, byte in [8,13,18,23].contains(index) ? byte == 45 : (48...57).contains(byte) || (97...102).contains(byte) }
    }
    static func title(_ value: String) -> Bool { if case .success = CreateNote.make(title: value) { true } else { false } }
    static var invalid: Failure { .invalid(.init(message: "Use an admitted command, identifier and version.", code: "notes.invalid_write"), fields: [:]) }
}
