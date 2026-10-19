import Foundation
import FoundryKernel
import FoundryServices
import SQLite3
import Testing
@testable import FoundryData

@Suite struct SQLitePendingFilesTests {
    private let scope = PendingFileScope(authorityID: "go.test", principalID: "00000000-0000-4000-8000-000000000003", accountID: "00000000-0000-4000-8000-000000000002")!
    private func intent(bytes: Data = Data([0,255,1])) throws -> PendingFileIntent {
        PendingFileIntent.upload(scope: scope, key: UUID().uuidString.lowercased(), spec: try UploadSpec.prepare(name: "Photo.png", contentType: "image/png", bytes: bytes).get(), bytes: bytes)!
    }
    private func fixture(_ body: (URL) async throws -> Void) async throws {
        let folder = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }; try await body(folder.appending(path: "journal.sqlite"))
    }
    private func sql(_ url: URL, _ statements: String...) throws {
        var db: OpaquePointer?; #expect(sqlite3_open(url.path, &db) == SQLITE_OK); defer { sqlite3_close(db) }
        for statement in statements { #expect(sqlite3_exec(db, statement, nil, nil, nil) == SQLITE_OK) }
    }
    @Test func tenMiBReopensAndExactRemovalCannotEraseNewerIntent() async throws {
        try await fixture { url in
            let bytes = Data((0..<10 * 1024 * 1024).map { UInt8($0 % 251) }), first = try intent(bytes: bytes)
            let store = SQLitePendingFilesStore(url: url); _ = try await store.stage(first).get(); _ = try await store.stage(first).get()
            let reopened = SQLitePendingFilesStore(url: url)
            #expect(try await reopened.load().get()?.matches(first) == true)
            let other = try intent(); if case .success = try await reopened.stage(other) { Issue.record("Exceeded global capacity") }
            _ = try await reopened.remove(first).get(); _ = try await reopened.remove(first).get()
            let file = RemoteFile(id: "00000000-0000-4000-8000-000000000004", accountID: scope.accountID, ownerID: scope.principalID, spec: other.spec, status: .available, version: 2)!
            let deletion = PendingFileIntent.delete(scope: scope, key: other.key, file: file)!
            _ = try await store.stage(deletion).get()
            if case .success = try await reopened.remove(first) { Issue.record("Stale removal erased a new delete") }
            #expect(try await reopened.load().get()?.matches(deletion) == true)
        }
    }
    @Test func rollbackAndCorruptionPreserveRecovery() async throws {
        try await fixture { url in
            let store = SQLitePendingFilesStore(url: url), first = try intent(); _ = try await store.stage(first).get()
            try sql(url, "CREATE TRIGGER refuse_remove BEFORE DELETE ON pending_files BEGIN SELECT RAISE(ABORT,'owned failure'); END")
            if case .success = try await store.remove(first) { Issue.record("Bypassed rollback") }
            #expect(try await store.load().get()?.matches(first) == true)
            try sql(url, "DROP TRIGGER refuse_remove", "UPDATE pending_files SET content=x'00'")
            if case .success = try await store.load() { Issue.record("Admitted changed bytes") }
            #expect(FileManager.default.fileExists(atPath: url.path))
            let corrupt = url.deletingLastPathComponent().appending(path: "corrupt.sqlite"), original = Data("not a database".utf8)
            try original.write(to: corrupt)
            if case .success = try await SQLitePendingFilesStore(url: corrupt).load() { Issue.record("Reset corrupt journal") }
            #expect(try Data(contentsOf: corrupt) == original)
        }
    }
    @Test func futureForeignAndMalformedStoresAreRefused() async throws {
        try await fixture { url in
            let store = SQLitePendingFilesStore(url: url); _ = try await store.load().get()
            try sql(url, "PRAGMA user_version=99")
            if case .success = try await store.load() { Issue.record("Admitted future schema") }
            try sql(url, "PRAGMA user_version=1", "UPDATE pending_store SET kind='pending-notes.v1'")
            if case .success = try await store.load() { Issue.record("Admitted foreign kind") }
            let foreign = url.deletingLastPathComponent().appending(path: "foreign.sqlite")
            try sql(foreign, "CREATE TABLE foreign_data(value TEXT)", "INSERT INTO foreign_data VALUES('keep')")
            if case .success = try await SQLitePendingFilesStore(url: foreign).load() { Issue.record("Reset foreign unversioned database") }
        }
    }
    @Test func independentInstancesSerializeCompetingStages() async throws {
        try await fixture { url in
            let a = SQLitePendingFilesStore(url: url), b = SQLitePendingFilesStore(url: url), one = try intent(), two = try intent()
            _ = try await a.load().get()
            async let first = a.stage(one); async let second = b.stage(two)
            let results = try await [first, second]
            #expect(results.filter { if case .success = $0 { true } else { false } }.count == 1)
            let winner = try #require(try await a.load().get()); #expect(winner.matches(one) || winner.matches(two))
        }
    }
}
