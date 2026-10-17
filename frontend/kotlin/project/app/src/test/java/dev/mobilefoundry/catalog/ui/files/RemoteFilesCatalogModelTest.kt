package dev.mobilefoundry.catalog.ui.files
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import dev.mobilefoundry.session.SessionScope
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RemoteFilesCatalogModelTest {
    private val account = SessionScope.make("go.test", "00000000-0000-4000-8000-000000000003", "00000000-0000-4000-8000-000000000002")!!
    private class Repository(val account: SessionScope) : RemoteFilesRepository {
        val keys = mutableListOf<String>(); var bytes = byteArrayOf()
        override suspend fun page(cursor: String?, limit: Int): AppResult<FilesPage> = Outcome.Ok(FilesPage(emptyList(), null))
        override suspend fun requestUpload(spec: UploadSpec, key: String): AppResult<FileReceipt> {
            keys += key
            if (keys.size == 1) return Outcome.Err(Failure.Unavailable(FailureMeta("Acknowledgement lost")))
            val file = RemoteFile.make("00000000-0000-4000-8000-000000000004", account.accountId, account.principalId, spec, FileStatus.PENDING_UPLOAD, 1)!!
            return Outcome.Ok(FileReceipt(key, "request_upload", account.principalId, file))
        }
        override suspend fun uploadContent(file: RemoteFile, bytes: ByteArray): AppResult<Unit> { this.bytes = bytes.copyOf(); return Outcome.Ok(Unit) }
        override suspend fun complete(file: RemoteFile, key: String): AppResult<FileReceipt> = Outcome.Ok(FileReceipt(key, "complete_upload", account.principalId, RemoteFile.make(file.id, file.accountId, file.ownerId, file.spec, FileStatus.AVAILABLE, 2)!!))
        override suspend fun delete(file: RemoteFile, key: String): AppResult<FileReceipt> = Outcome.Err(Failure.Unavailable(FailureMeta("Unused")))
        override suspend fun download(file: RemoteFile): AppResult<ByteArray> = Outcome.Ok(bytes.copyOf())
        override suspend fun receipt(key: String): AppResult<FileReceipt> = Outcome.Err(Failure.NotFound(FailureMeta("Unused")))
    }
    @Test fun retryRetainsExactBytesAndKeyThroughRenewal() = runTest {
        val repo = Repository(account); val model = RemoteFilesCatalogModel(this); model.bind(repo, account); advanceUntilIdle()
        val bytes = byteArrayOf(0, -1, 1, -128); val spec = (UploadSpec.prepare("photo.png", "image/png", bytes) as Outcome.Ok).value
        model.upload(spec, bytes, model.selectionTicket); advanceUntilIdle(); assertTrue(model.state.value.pending)
        bytes[0] = 10
        model.bind(null, null); model.bind(repo, account); advanceUntilIdle(); assertTrue(model.state.value.pending)
        model.retry(); advanceUntilIdle(); assertEquals(2, repo.keys.size); assertEquals(repo.keys[0], repo.keys[1]); assertArrayEquals(byteArrayOf(0, -1, 1, -128), repo.bytes); assertFalse(model.state.value.pending)
    }
    @Test fun stalePickerResultCannotUploadToReplacementScope() = runTest {
        val repo = Repository(account); val model = RemoteFilesCatalogModel(this); model.bind(repo, account); advanceUntilIdle(); val ticket = model.selectionTicket
        val other = SessionScope.make(account.authorityId, account.principalId, "00000000-0000-4000-8000-000000000099")!!
        val replacement = Repository(other); model.bind(replacement, other); advanceUntilIdle()
        val bytes = byteArrayOf(1); val spec = (UploadSpec.prepare("photo.png", "image/png", bytes) as Outcome.Ok).value
        model.upload(spec, bytes, ticket); advanceUntilIdle(); assertTrue(repo.keys.isEmpty()); assertTrue(replacement.keys.isEmpty()); assertFalse(model.state.value.pending)
    }
}
