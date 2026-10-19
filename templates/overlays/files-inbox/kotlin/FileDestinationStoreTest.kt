package dev.mobilefoundry.product.connection

import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import dev.mobilefoundry.session.SessionScope
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FileDestinationStoreTest {
    private val account = SessionScope.make("go.test", "00000000-0000-4000-8000-000000000003", "00000000-0000-4000-8000-000000000002")!!
    private fun notice(accountId: String = account.accountId, principal: String = account.principalId) = InboxNotification.make(
        "00000000-0000-4000-8000-000000000001", accountId, principal, "00000000-0000-4000-8000-000000000004", Instant.ofEpochSecond(1000), Instant.ofEpochSecond(1001), null)!!
    private fun file(id: String = notice().fileId, accountId: String = account.accountId, status: FileStatus = FileStatus.AVAILABLE): RemoteFile {
        val spec = (UploadSpec.prepare("Actual.png", "image/png", byteArrayOf(1, 2, 3)) as Outcome.Ok).value
        return RemoteFile.make(id, accountId, account.principalId, spec, status, if (status == FileStatus.PENDING_UPLOAD) 1 else if (status == FileStatus.AVAILABLE) 2 else 3)!!
    }
    private class Reader(val response: AppResult<RemoteFile>, val payload: ByteArray = byteArrayOf(1, 2, 3),
        val readGate: CompletableDeferred<Unit>? = null, val downloadGate: CompletableDeferred<Unit>? = null) : RemoteFileDetailReader {
        var reads = 0; var downloads = 0
        override suspend fun get(id: String): AppResult<RemoteFile> { reads++; readGate?.let { withContext(NonCancellable) { it.await() } }; return response }
        override suspend fun download(file: RemoteFile): AppResult<ByteArray> { downloads++; downloadGate?.let { withContext(NonCancellable) { it.await() } }; return Outcome.Ok(payload) }
    }
    @Test fun scopedOpenReadsMetadataBeforeExplicitVerifiedDownload() = runTest {
        val reader = Reader(Outcome.Ok(file())); val model = FileDestinationStore(this)
        model.bind(reader, account); assertEquals(0, reader.reads)
        model.open(notice(accountId = "00000000-0000-4000-8000-000000000099")); assertFalse(model.state.value.presented)
        model.open(notice(principal = "00000000-0000-4000-8000-000000000099")); assertFalse(model.state.value.presented)
        model.open(notice()); advanceUntilIdle()
        assertEquals("Actual.png", model.state.value.file?.spec?.name); assertNull(model.state.value.bytes); assertNull(model.state.value.failure)
        assertEquals(1, reader.reads); assertEquals(0, reader.downloads)
        model.download(); advanceUntilIdle(); assertArrayEquals(byteArrayOf(1, 2, 3), model.state.value.bytes)
        model.close(); assertFalse(model.state.value.presented); assertNull(model.state.value.file); assertNull(model.state.value.bytes)
    }
    @Test fun sameScopeRenewalAndReplacementRejectNoncooperativeMetadata() = runTest {
        val gate = CompletableDeferred<Unit>(); val old = Reader(Outcome.Ok(file()), readGate = gate); val model = FileDestinationStore(this)
        model.bind(old, account); model.open(notice()); runCurrent(); assertEquals(1, old.reads)
        val replacement = Reader(Outcome.Ok(file(status = FileStatus.PENDING_UPLOAD)))
        model.bind(replacement, account); assertFalse(model.state.value.presented)
        model.open(notice()); runCurrent(); gate.complete(Unit); advanceUntilIdle()
        assertEquals(FileStatus.PENDING_UPLOAD, model.state.value.file?.status); assertFalse(model.state.value.busy)
        model.download(); assertEquals(0, replacement.downloads)
        model.bind(null, null); assertFalse(model.state.value.presented); assertNull(model.state.value.file)
    }
    @Test fun missingDeniedAndMismatchedMetadataRemainDistinctFailures() = runTest {
        val model = FileDestinationStore(this)
        for (error in listOf(Failure.NotFound(FailureMeta("Missing", "files.not_found")), Failure.Forbidden(FailureMeta("Denied")))) {
            model.bind(Reader(Outcome.Err(error)), account); model.open(notice()); advanceUntilIdle()
            assertEquals(error.kind, model.state.value.failure?.kind); assertNull(model.state.value.file); assertNull(model.state.value.bytes)
        }
        for (value in listOf(file(id = "00000000-0000-4000-8000-000000000099"), file(accountId = "00000000-0000-4000-8000-000000000099"), file(status = FileStatus.DELETED))) {
            model.bind(Reader(Outcome.Ok(value)), account); model.open(notice()); advanceUntilIdle()
            assertNull(model.state.value.file); assertEquals("files.invalid_remote_response", model.state.value.failure?.meta?.code)
        }
    }
    @Test fun closedDownloadCannotRepublishAndCorruptBytesNeverPreview() = runTest {
        val gate = CompletableDeferred<Unit>(); val reader = Reader(Outcome.Ok(file()), downloadGate = gate); val model = FileDestinationStore(this)
        model.bind(reader, account); model.open(notice()); advanceUntilIdle(); model.download(); runCurrent(); assertEquals(1, reader.downloads)
        model.close(); gate.complete(Unit); advanceUntilIdle()
        assertFalse(model.state.value.presented); assertNull(model.state.value.bytes); assertNull(model.state.value.file)
        val corrupt = Reader(Outcome.Ok(file()), payload = byteArrayOf(9))
        model.bind(corrupt, account); model.open(notice()); advanceUntilIdle(); model.download(); advanceUntilIdle()
        assertNull(model.state.value.bytes); assertEquals("files.invalid_remote_response", model.state.value.failure?.meta?.code)
    }
}
