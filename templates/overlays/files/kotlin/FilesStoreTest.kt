package dev.mobilefoundry.product.connection

import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import dev.mobilefoundry.session.SessionScope
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FilesStoreTest {
    private val scope = SessionScope.make("files.test","00000000-0000-4000-8000-000000000003","00000000-0000-4000-8000-000000000002")!!
    private val other = SessionScope.make(scope.authorityId,scope.principalId,"00000000-0000-4000-8000-000000000099")!!
    private class Repository(val scope: SessionScope, val gate: CompletableDeferred<Unit>? = null, val duplicate: Boolean = false) : RemoteFilesRepository {
        var completion: FileReceipt? = null; var puts = 0; var lostComplete = true; var removed = false
        var deleteConflict = false
        var pageGate: CompletableDeferred<Unit>? = gate
        val requestKeys = mutableListOf<String>(); val deleteKeys = mutableListOf<String>()
        val bytes = byteArrayOf(0,-1,1)
        val spec = (UploadSpec.prepare("photo.png","image/png",bytes) as Outcome.Ok).value
        fun file(status: FileStatus = FileStatus.AVAILABLE) = RemoteFile.make("00000000-0000-4000-8000-000000000001",scope.accountId,scope.principalId,spec,status,if (status==FileStatus.PENDING_UPLOAD) 1 else if (status==FileStatus.DELETED) 3 else 2)!!
        override suspend fun page(cursor: String?, limit: Int): AppResult<FilesPage> {
            pageGate?.let { withContext(NonCancellable) { it.await() } }
            return Outcome.Ok(FilesPage(if (removed) emptyList() else listOf(file()),if (duplicate && cursor==null) "next" else null))
        }
        override suspend fun download(file: RemoteFile): AppResult<ByteArray> = Outcome.Ok(bytes)
        override suspend fun receipt(key: String): AppResult<FileReceipt> = completion?.let { Outcome.Ok(it) } ?: Outcome.Err(Failure.NotFound(FailureMeta("No receipt")))
        override suspend fun requestUpload(spec: UploadSpec, key: String): AppResult<FileReceipt> { requestKeys+=key; return Outcome.Ok(FileReceipt(key,"request_upload",scope.principalId,file(FileStatus.PENDING_UPLOAD))) }
        override suspend fun uploadContent(file: RemoteFile, bytes: ByteArray): AppResult<Unit> { assertArrayEquals(this.bytes,bytes); puts++; return Outcome.Ok(Unit) }
        override suspend fun complete(file: RemoteFile, key: String): AppResult<FileReceipt> {
            val receipt = FileReceipt(key,"complete_upload",scope.principalId,file()); completion=receipt
            if (lostComplete) { lostComplete=false;return Outcome.Err(Failure.Unavailable(FailureMeta("Acknowledgement lost"))) }; return Outcome.Ok(receipt)
        }
        override suspend fun delete(file: RemoteFile, key: String): AppResult<FileReceipt> {
            deleteKeys+=key
            if (deleteConflict) return Outcome.Err(Failure.Conflict(FailureMeta("Observed version changed")))
            removed=true
            if (deleteKeys.size==1) return Outcome.Err(Failure.Unavailable(FailureMeta("Cleanup incomplete")))
            return Outcome.Ok(FileReceipt(key,"delete",scope.principalId,file(FileStatus.DELETED)))
        }
    }
    private class Journal(var lostStage: Boolean=false,var removalFailures: Int=0,var refuseLoad: Boolean=false,val gate: CompletableDeferred<Unit>?=null): PendingFilesStore {
        val inner=MemoryPendingFilesStore();var staged=false
        override suspend fun load(): AppResult<PendingFileIntent?> = if (refuseLoad) Outcome.Err(PendingFilesFailure.invalid) else inner.load()
        override suspend fun stage(intent: PendingFileIntent): AppResult<Unit> {
            val result=inner.stage(intent);staged=true
            if (gate!=null) withContext(NonCancellable) { gate.await() }
            return if (lostStage) { lostStage=false;Outcome.Err(PendingFilesFailure.storage) } else result
        }
        override suspend fun remove(intent: PendingFileIntent): AppResult<Unit> = if (removalFailures>0) { removalFailures--;Outcome.Err(PendingFilesFailure.storage) } else inner.remove(intent)
    }
    @Test fun lostCompletionSettlesReceiptWithoutReuploadAndNeverAutoReplays() = runTest {
        val repo=Repository(scope);val store=FilesStore(this);store.bind(repo,scope);advanceUntilIdle()
        val bytes=repo.bytes.copyOf();store.upload(repo.spec,bytes,store.selectionTicket);bytes[0]=99;advanceUntilIdle()
        assertTrue(store.state.value.pending);assertFalse(store.canEdit);assertEquals(1,repo.puts)
        store.bind(null,null);assertFalse(store.state.value.pending);assertTrue(store.state.value.files.isEmpty())
        store.bind(Repository(other),other);advanceUntilIdle();assertTrue(store.state.value.blockedByOtherScope);assertFalse(store.canEdit)
        repo.removed=true;store.bind(repo,scope);advanceUntilIdle();assertTrue(store.state.value.pending);assertTrue(store.state.value.files.isEmpty());assertEquals(1,repo.puts)
        store.retry();advanceUntilIdle();assertFalse(store.state.value.pending);assertTrue(store.canEdit);assertEquals(1,repo.puts);assertEquals(1,repo.requestKeys.size)
    }
    @Test fun deletionRetriesOriginalCommandEvenAfterLogicalDeletion() = runTest {
        val repo=Repository(scope);val store=FilesStore(this);store.bind(repo,scope);advanceUntilIdle()
        store.delete(store.state.value.files.single());advanceUntilIdle();assertTrue(store.state.value.pending)
        store.bind(null,null);store.bind(repo,scope);advanceUntilIdle();assertTrue(store.state.value.files.isEmpty());assertTrue(store.state.value.pending);assertEquals(1,repo.deleteKeys.size)
        store.retry();advanceUntilIdle();assertEquals(2,repo.deleteKeys.size);assertEquals(1,repo.deleteKeys.toSet().size);assertFalse(store.state.value.pending)
    }
    @Test fun lateOldScopeCompletionCannotPublish() = runTest {
        val gate=CompletableDeferred<Unit>();val store=FilesStore(this);store.bind(Repository(scope,gate),scope);runCurrent()
        store.bind(Repository(other),other);runCurrent();gate.complete(Unit);advanceUntilIdle()
        assertTrue(store.state.value.files.all { it.accountId==other.accountId });store.bind(null,null)
    }
    @Test fun oldPickerTicketAndMismatchedBytesCannotStartAnIntent() = runTest {
        val repo=Repository(scope);val store=FilesStore(this);store.bind(repo,scope);advanceUntilIdle();val ticket=store.selectionTicket
        store.bind(repo,scope);advanceUntilIdle();store.upload(repo.spec,repo.bytes,ticket);store.upload(repo.spec,byteArrayOf(2),store.selectionTicket)
        assertFalse(store.state.value.pending);assertTrue(store.canEdit);assertTrue(repo.requestKeys.isEmpty())
    }
    @Test fun duplicatePagesRequireRefreshAndDetachDropsPreview() = runTest {
        val repo=Repository(scope,duplicate=true);val store=FilesStore(this);store.bind(repo,scope);advanceUntilIdle()
        store.loadMore();advanceUntilIdle();assertFalse(store.state.value.ready);assertEquals(1,store.state.value.files.size);assertFalse(store.canEdit)
        store.refresh();advanceUntilIdle();assertTrue(store.canEdit)
        store.open(store.state.value.files.single());advanceUntilIdle();assertNotNull(store.state.value.preview)
        store.bind(null,null);assertNull(store.state.value.preview);assertTrue(store.state.value.files.isEmpty())
    }
    @Test fun terminalConflictRequiresExplicitAbandonmentBeforeNewCommand() = runTest {
        val repo=Repository(scope).apply { deleteConflict=true };val store=FilesStore(this);store.bind(repo,scope);advanceUntilIdle()
        store.delete(store.state.value.files.single());advanceUntilIdle();val review=store.reviewPending()!!
        assertTrue(review.deleting);assertEquals("photo.png",review.name);assertTrue(store.state.value.pending);assertFalse(store.canEdit)
        store.retry();advanceUntilIdle();assertEquals(1,repo.deleteKeys.toSet().size)
        val ticket=store.selectionTicket;store.forgetLocalRecovery(review);assertTrue(store.state.value.pending);assertFalse(store.canEdit)
        advanceUntilIdle();assertTrue(store.canEdit);assertNull(store.state.value.failure);assertNotEquals(ticket,store.selectionTicket)
        store.delete(store.state.value.files.single());advanceUntilIdle();assertEquals(3,repo.deleteKeys.size);assertEquals(2,repo.deleteKeys.toSet().size)
    }
    @Test fun reviewCannotForgetAcrossRenewalOtherScopeOrActiveRead() = runTest {
        val repo=Repository(scope);val store=FilesStore(this);store.bind(repo,scope);advanceUntilIdle()
        store.delete(store.state.value.files.single());advanceUntilIdle();val old=store.reviewPending()!!
        store.bind(Repository(other),other);advanceUntilIdle();assertNull(store.reviewPending());store.forgetLocalRecovery(old);assertTrue(store.state.value.blockedByOtherScope)
        store.bind(repo,scope);advanceUntilIdle();store.forgetLocalRecovery(old);assertTrue(store.state.value.pending)
        val current=store.reviewPending()!!;val gate=CompletableDeferred<Unit>();repo.pageGate=gate;store.refresh();runCurrent()
        assertNull(store.reviewPending());store.forgetLocalRecovery(current);assertTrue(store.state.value.pending)
        gate.complete(Unit);advanceUntilIdle();assertTrue(store.state.value.pending)
    }
    @Test fun oldUploadReviewCannotEraseLaterDeleteAndUploadAbandonmentDoesNotReplay() = runTest {
        val repo=Repository(scope);val store=FilesStore(this);store.bind(repo,scope);advanceUntilIdle()
        store.upload(repo.spec,repo.bytes,store.selectionTicket);advanceUntilIdle();val old=store.reviewPending()!!;assertFalse(old.deleting)
        store.retry();advanceUntilIdle();store.delete(store.state.value.files.single());advanceUntilIdle()
        store.forgetLocalRecovery(old);assertTrue(store.state.value.pending)
        store.forgetLocalRecovery(store.reviewPending()!!);advanceUntilIdle()
        store.upload(repo.spec,repo.bytes,store.selectionTicket);advanceUntilIdle()
        store.forgetLocalRecovery(store.reviewPending()!!);advanceUntilIdle()
        assertFalse(store.state.value.pending);assertTrue(store.canEdit);assertEquals(1,repo.puts);assertEquals(1,repo.requestKeys.size);assertEquals(1,repo.deleteKeys.size)
    }
    @Test fun coldOwnerRestoresOriginalIntentWithoutAutomaticReplay()=runTest {
        val repo=Repository(scope);val journal=MemoryPendingFilesStore();val first=FilesStore(this,journal);first.bind(repo,scope);advanceUntilIdle()
        first.upload(repo.spec,repo.bytes,first.selectionTicket);advanceUntilIdle();first.bind(null,null)
        val restored=FilesStore(this,journal);restored.bind(Repository(other),other);advanceUntilIdle()
        assertTrue(restored.state.value.blockedByOtherScope);assertFalse(restored.canEdit);assertNull(restored.reviewPending())
        restored.bind(repo,scope);advanceUntilIdle();assertTrue(restored.state.value.pending);assertEquals(1,repo.puts)
        restored.retry();advanceUntilIdle();assertFalse(restored.state.value.pending);assertTrue(restored.canEdit);assertNull((journal.load() as Outcome.Ok).value);assertEquals(1,repo.puts)
    }
    @Test fun lostStageAndFailedRemovalCannotDispatchOrReleaseOriginalIntent()=runTest {
        val repo=Repository(scope);val journal=Journal(lostStage=true,removalFailures=2);val store=FilesStore(this,journal);store.bind(repo,scope);advanceUntilIdle()
        store.upload(repo.spec,repo.bytes,store.selectionTicket);advanceUntilIdle();assertTrue(store.state.value.pending);assertFalse(store.canEdit);assertEquals(0,repo.puts)
        store.forgetLocalRecovery(store.reviewPending()!!);advanceUntilIdle();assertTrue(store.state.value.pending)
        store.bind(repo,scope);advanceUntilIdle();assertEquals(0,repo.puts)
        store.retry();advanceUntilIdle();assertTrue(store.state.value.pending);assertEquals(1,repo.puts)
        store.retry();advanceUntilIdle();assertTrue(store.state.value.pending);assertNotNull((journal.load() as Outcome.Ok).value)
        store.retry();advanceUntilIdle();assertFalse(store.state.value.pending);assertTrue(store.canEdit);assertEquals(1,repo.puts)
    }
    @Test fun unreadableJournalBlocksChangesUntilExplicitReload()=runTest {
        val repo=Repository(scope);val journal=Journal(refuseLoad=true);val store=FilesStore(this,journal);store.bind(repo,scope);advanceUntilIdle()
        assertFalse(store.canEdit);assertFalse(store.state.value.ready);assertEquals("pending_files.invalid_store",store.state.value.failure?.meta?.code)
        store.upload(repo.spec,repo.bytes,store.selectionTicket);assertEquals(0,repo.puts)
        journal.refuseLoad=false;store.refresh();advanceUntilIdle();assertTrue(store.canEdit)
    }
    @Test fun cancelledNoncooperativeDiskStageCannotSendHTTPAfterDetach()=runTest {
        val gate=CompletableDeferred<Unit>();val repo=Repository(scope);val journal=Journal(gate=gate);val store=FilesStore(this,journal);store.bind(repo,scope);advanceUntilIdle()
        store.upload(repo.spec,repo.bytes,store.selectionTicket);runCurrent();assertTrue(journal.staged)
        store.bind(null,null);gate.complete(Unit);advanceUntilIdle();assertEquals(0,repo.puts)
        val restored=FilesStore(this,journal);restored.bind(repo,scope);advanceUntilIdle();assertTrue(restored.state.value.pending);assertFalse(restored.canEdit);assertTrue(repo.requestKeys.isEmpty())
    }

}
