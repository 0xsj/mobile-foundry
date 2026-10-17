package dev.mobilefoundry.product.connection
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import dev.mobilefoundry.session.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RemoteNotesStoreTest {
    private val account = SessionScope.make("go.test","00000000-0000-4000-8000-000000000003","00000000-0000-4000-8000-000000000002")!!
    private class Repository(val account: SessionScope) : RemoteNotesRepository {
        var readFailure: Failure? = null
        fun seed() { notes += VersionedNote.make("00000000-0000-4000-8000-000000000004", "Saved snapshot", 1)!! }
        var reads = 0;var executions = 0;var loseAck = true;var failAfter = false;var failRead = false
        val notes = mutableListOf<VersionedNote>();val receipts = mutableMapOf<String,NoteWriteReceipt>()
        override suspend fun page(after: String?,limit: Int): AppResult<RemoteNotesPage> { reads++; readFailure?.let { return Outcome.Err(it) }; return if (failRead) Outcome.Err(Failure.Unavailable(FailureMeta("Read failed"))) else Outcome.Ok(RemoteNotesPage(notes,null)) }
        override suspend fun get(noteId: String): AppResult<VersionedNote> = Outcome.Err(Failure.NotFound(FailureMeta("Not found")))
        override suspend fun execute(command: RemoteNoteWrite): AppResult<NoteWriteReceipt> {
            executions++;receipts[command.commandId]?.let { return Outcome.Ok(it) }
            val id = UUID.randomUUID().toString();val r = NoteWriteReceipt.make(command.commandId,account.accountId,account.principalId,NoteWriteAction.CREATE,id,command.title,1)!!
            notes += VersionedNote.make(id,command.title,1)!!;receipts[command.commandId] = r;failRead = failAfter
            return if (loseAck) Outcome.Err(Failure.Unavailable(FailureMeta("Lost acknowledgement"))) else Outcome.Ok(r)
        }
        override suspend fun receipt(command: RemoteNoteWrite): AppResult<NoteWriteReceipt> = receipts[command.commandId]?.let { Outcome.Ok(it) } ?: Outcome.Err(Failure.NotFound(FailureMeta("Not found")))
    }
    @Test fun pendingCommandSurvivesRenewalAndReceiptDoesNotReplay() = runTest {
        val repo = Repository(account);val model = RemoteNotesStore(this);model.bind(repo,account);advanceUntilIdle()
        model.setTitle("Retained");model.save();model.save();advanceUntilIdle()
        val command = model.state.value.pending!!;assertEquals(1,repo.executions);assertFalse(model.state.value.canWrite)
        model.bind(null,null);model.bind(repo,account);advanceUntilIdle();assertEquals(command.commandId,model.state.value.pending!!.commandId)
        model.recover(true);advanceUntilIdle();assertNull(model.state.value.pending);assertTrue(model.state.value.canWrite);assertEquals(1,model.state.value.notes.size);assertEquals("",model.state.value.title);assertEquals(1,repo.executions)
    }
    @Test fun confirmedWriteSurvivesReadFailureWithoutReplay() = runTest {
        val repo = Repository(account);repo.loseAck = false;repo.failAfter = true
        val model = RemoteNotesStore(this);model.bind(repo,account);advanceUntilIdle();model.setTitle("Committed");model.save();advanceUntilIdle()
        assertNull(model.state.value.pending);assertNotNull(model.state.value.receipt);assertFalse(model.state.value.canWrite)
        repo.failRead = false;model.refresh();advanceUntilIdle();assertTrue(model.state.value.canWrite);assertEquals(1,repo.executions);assertNotNull(model.state.value.receipt)
    }
    @Test fun lateAcknowledgementCannotPopulateReplacementAccount() = runTest {
        var continuation: Continuation<AppResult<NoteWriteReceipt>>? = null;var command: RemoteNoteWrite? = null
        val old = object : RemoteNotesRepository {
            override suspend fun page(after: String?,limit: Int): AppResult<RemoteNotesPage> = Outcome.Ok(RemoteNotesPage(emptyList(),null))
            override suspend fun get(noteId: String): AppResult<VersionedNote> = Outcome.Err(Failure.NotFound(FailureMeta("Not found")))
            override suspend fun receipt(command: RemoteNoteWrite): AppResult<NoteWriteReceipt> = Outcome.Err(Failure.NotFound(FailureMeta("Not found")))
            override suspend fun execute(input: RemoteNoteWrite): AppResult<NoteWriteReceipt> { command = input;return suspendCoroutine { continuation = it } }
        }
        val model = RemoteNotesStore(this);model.bind(old,account);advanceUntilIdle();model.setTitle("Old target");model.save();runCurrent();assertNotNull(continuation)
        val next = SessionScope.make("go.test",account.principalId,"00000000-0000-4000-8000-000000000099")!!;model.bind(Repository(next),next);runCurrent()
        continuation!!.resume(Outcome.Ok(NoteWriteReceipt.make(command!!.commandId,account.accountId,account.principalId,NoteWriteAction.CREATE,UUID.randomUUID().toString(),command!!.title,1)!!));advanceUntilIdle()
        assertTrue(model.state.value.notes.isEmpty());assertNull(model.state.value.receipt);assertNull(model.state.value.pending)
        model.bind(old,account);advanceUntilIdle();assertNotNull(model.state.value.pending)
    }
    private class UncertainJournal(val loseStage: Boolean = false, var failConfirm: Boolean = false) : PendingNotesStore {
        val underlying = MemoryPendingNotesStore(); var stageLost = false
        var failLoad = false
        override suspend fun load(scope: PendingNotesScope): AppResult<PendingNoteRecord?> = if (failLoad) Outcome.Err(PendingNotesFailure.invalid) else underlying.load(scope)
        override suspend fun stage(command: RemoteNoteWrite, scope: PendingNotesScope, replacing: String?): AppResult<PendingNoteRecord> {
            val result = underlying.stage(command,scope,replacing)
            if (loseStage && !stageLost) { stageLost = true; return Outcome.Err(PendingNotesFailure.storage) }; return result
        }
        override suspend fun confirm(command: RemoteNoteWrite, scope: PendingNotesScope, receipt: NoteWriteReceipt): AppResult<PendingNoteRecord> {
            if (failConfirm) { failConfirm = false; return Outcome.Err(PendingNotesFailure.storage) }
            return underlying.confirm(command,scope,receipt)
        }
        override suspend fun reject(command: RemoteNoteWrite, scope: PendingNotesScope, reason: PendingNoteRejection) = underlying.reject(command,scope,reason)
    }
    @Test fun newOwnerRestoresWithoutSendAndReceipt404KeepsIntent() = runTest {
        val journal = UncertainJournal(loseStage=true); val repo = Repository(account)
        val first = RemoteNotesStore(this,journal); first.bind(repo,account);advanceUntilIdle()
        first.setTitle("Not sent");first.save();advanceUntilIdle();assertFalse(first.state.value.recoveryLoaded);assertEquals(0,repo.executions)
        val second = RemoteNotesStore(this,journal);second.bind(repo,account);advanceUntilIdle()
        val original = second.state.value.pending!!;assertEquals("Not sent",original.title);assertEquals(0,repo.executions)
        second.recover(true);advanceUntilIdle();assertEquals(original.commandId,second.state.value.pending!!.commandId);assertEquals(0,repo.executions)
        repo.loseAck=false;second.recover(false);advanceUntilIdle();assertNull(second.state.value.pending)
        assertTrue(second.state.value.receipt!!.confirms(original));assertEquals(1,repo.executions)
    }
    @Test fun failedReceiptPersistenceRequiresReloadAndNewOwnerRecovers() = runTest {
        val journal = UncertainJournal(failConfirm=true);val repo = Repository(account);repo.loseAck=false
        val first=RemoteNotesStore(this,journal);first.bind(repo,account);advanceUntilIdle()
        first.setTitle("Remote committed");first.save();advanceUntilIdle()
        val original=first.state.value.pending!!;assertTrue(first.state.value.receipt!!.confirms(original));assertFalse(first.state.value.recoveryLoaded)
        first.save();advanceUntilIdle();assertEquals(1,repo.executions)
        val second=RemoteNotesStore(this,journal);second.bind(repo,account);advanceUntilIdle()
        assertEquals(original.commandId,second.state.value.pending!!.commandId);assertEquals(1,repo.executions)
        second.recover(true);advanceUntilIdle();assertNull(second.state.value.pending);assertEquals(1,repo.executions)
        val third=RemoteNotesStore(this,journal);third.bind(repo,account);advanceUntilIdle()
        assertTrue(third.state.value.receipt!!.confirms(original));assertTrue(third.state.value.canWrite);assertEquals(1,repo.executions)
    }

    @Test fun hintsCoalesceBehindUnconfirmedWriteWithoutReplay() = runTest {
        val repo = Repository(account); val model = RemoteNotesStore(this)
        model.bind(repo, account); advanceUntilIdle()
        model.setTitle("Unconfirmed live write"); model.save()
        repeat(100) { model.invalidate() }
        advanceUntilIdle()
        assertEquals(1, repo.executions); assertEquals(2, repo.reads)
        assertNotNull(model.state.value.pending); assertEquals(1, model.state.value.notes.size); assertFalse(model.state.value.canWrite)
    }

    @Test fun interruptedPasswordChangeClearsAccessAndLateReplyCannotCloseReplacement() = runTest {
        val authority = object : SessionAuthority {
            override val id = "presentation.test"
            override suspend fun signIn(command: SignIn): AppResult<SessionGrant> = Outcome.Ok(SessionGrant.make(id,
                Principal.make(account.principalId, "Test")!!, Account.make(account.accountId, "Personal")!!,
                "fixture-access", "fixture-refresh", 10_000)!!)
            override suspend fun renew(credential: StoredCredential): AppResult<SessionGrant> = Outcome.Err(SessionFailures.rejected)
            override suspend fun revoke(credential: StoredCredential): AppResult<Unit> = Outcome.Ok(Unit)
        }
        val vault = MemoryCredentialVault()
        val owner = SessionController(this, authority, vault, { 1L }) { fail("Unexpected failure") }
        owner.signIn("alex", "fixture")!!; runCurrent()
        var pending: Continuation<AppResult<Unit>>? = null
        val service = object : IdentityRecovery {
            override suspend fun contact(): AppResult<RecoveryContact> = Outcome.Ok(RecoveryContact(null, null))
            override suspend fun requestVerification(email: String, password: String): AppResult<Unit> = Outcome.Ok(Unit)
            override suspend fun verify(token: String): AppResult<Unit> = Outcome.Ok(Unit)
            override suspend fun requestReset(email: String): AppResult<Unit> = Outcome.Ok(Unit)
            override suspend fun reset(token: String, password: String): AppResult<Unit> = Outcome.Ok(Unit)
            override suspend fun change(currentPassword: String, newPassword: String): AppResult<Unit> = suspendCoroutine { pending = it }
        }
        val model = IdentitySettingsModel(this, owner, service); model.bind(service, owner.lease)
        model.change("private old password", "private new password"); runCurrent(); assertNotNull(pending)
        model.cancel(); assertNull(owner.lease); runCurrent(); assertNull((vault.load() as Outcome.Ok).value)
        owner.signIn("alex", "fixture"); runCurrent(); val replacement = owner.lease!!
        pending!!.resume(Outcome.Ok(Unit)); advanceUntilIdle()
        assertTrue(owner.isCurrent(replacement)); assertFalse(model.state.value.busy)
        owner.shutdown()
    }


    @Test fun savedReadSurvivesNewOwnerReadOnlyAndCanBeForgotten() = runTest {
        val repo = Repository(account).apply { seed() }; val cache = MemoryNotesReadCache()
        val first = RemoteNotesStore(this, cache = cache); first.bind(repo, account); advanceUntilIdle()
        assertTrue(first.state.value.loaded); assertNull(first.state.value.cachedAtMilliseconds)
        repo.readFailure = Failure.Unavailable(FailureMeta("Offline"))
        val reopened = RemoteNotesStore(this, cache = cache); reopened.bind(repo, account); advanceUntilIdle()
        assertEquals("Saved snapshot", reopened.state.value.notes.single().title)
        assertNotNull(reopened.state.value.cachedAtMilliseconds); assertFalse(reopened.state.value.canWrite)
        reopened.setTitle("Must not send"); reopened.save(); assertEquals(0, repo.executions)
        reopened.forgetSavedRead(); advanceUntilIdle(); assertTrue(reopened.state.value.notes.isEmpty())
        reopened.refresh(); advanceUntilIdle(); assertTrue(reopened.state.value.notes.isEmpty())
        repo.readFailure = null; reopened.refresh(); advanceUntilIdle()
        assertTrue(reopened.state.value.canWrite); assertNull(reopened.state.value.cachedAtMilliseconds)
    }
    @Test fun cacheNeverCrossesScopeOrSignedOutBoundary() = runTest {
        val cache = MemoryNotesReadCache(); val repo = Repository(account).apply { seed() }
        val model = RemoteNotesStore(this, cache = cache); model.bind(repo, account); advanceUntilIdle()
        val foreign = SessionScope.make("go.other", account.principalId, account.accountId)!!
        val offline = Repository(foreign).apply { readFailure = Failure.Timeout(FailureMeta("Offline")) }
        model.bind(offline, foreign); advanceUntilIdle(); assertTrue(model.state.value.notes.isEmpty()); assertNull(model.state.value.cachedAtMilliseconds)
        model.bind(null, null); assertTrue(model.state.value.notes.isEmpty()); assertFalse(model.state.value.canWrite)
    }
    @Test fun changedJournalPreventsPreMutationSnapshotResurrection() = runTest {
        val cache = MemoryNotesReadCache(); val journal = MemoryPendingNotesStore()
        val repo = Repository(account).apply { seed(); loseAck = false; failAfter = true }
        val first = RemoteNotesStore(this, journal, cache); first.bind(repo, account); advanceUntilIdle()
        first.setTitle("New commit"); first.save(); advanceUntilIdle()
        assertNotNull(first.state.value.receipt); assertTrue(first.state.value.notes.isEmpty()); assertNull(first.state.value.cachedAtMilliseconds)
        val reopened = RemoteNotesStore(this, journal, cache); reopened.bind(repo, account); advanceUntilIdle()
        assertNotNull(reopened.state.value.receipt); assertTrue(reopened.state.value.notes.isEmpty()); assertNull(reopened.state.value.cachedAtMilliseconds)
        assertEquals(1, repo.executions)
    }
    private class BrokenCache : NotesReadCache {
        override suspend fun load(scope: PendingNotesScope, nowMilliseconds: Long): AppResult<NotesReadSnapshot?> = error("Owned storage failure")
        override suspend fun save(snapshot: NotesReadSnapshot): AppResult<Unit> = Outcome.Err(NotesCacheFailure.storage)
        override suspend fun remove(scope: PendingNotesScope): AppResult<Unit> = Outcome.Err(NotesCacheFailure.storage)
    }
    @Test fun optionalCacheFailureCannotBlockFreshReadsOrConfirmedWrites() = runTest {
        val repo = Repository(account).apply { loseAck = false }; val model = RemoteNotesStore(this, cache = BrokenCache())
        model.bind(repo, account); advanceUntilIdle()
        assertTrue(model.state.value.canWrite); assertTrue(model.state.value.recoveryLoaded); assertNotNull(model.state.value.cacheFailure); assertNull(model.state.value.failure)
        model.setTitle("Still writable"); model.save(); advanceUntilIdle()
        assertTrue(model.state.value.canWrite); assertNull(model.state.value.pending); assertNotNull(model.state.value.receipt)
        assertEquals(1, model.state.value.notes.size); assertNotNull(model.state.value.cacheFailure)
    }
    @Test fun authorizationRefusalHidesAndRemovesSavedRead() = runTest {
        val repo = Repository(account).apply { seed() }; val cache = MemoryNotesReadCache()
        val first = RemoteNotesStore(this, cache = cache); first.bind(repo, account); advanceUntilIdle()
        repo.readFailure = Failure.Forbidden(FailureMeta("No membership"))
        val denied = RemoteNotesStore(this, cache = cache); denied.bind(repo, account); advanceUntilIdle()
        assertTrue(denied.state.value.notes.isEmpty()); assertNull(denied.state.value.cachedAtMilliseconds); assertFalse(denied.state.value.canWrite)
        repo.readFailure = Failure.Unavailable(FailureMeta("Offline"))
        val later = RemoteNotesStore(this, cache = cache); later.bind(repo, account); advanceUntilIdle()
        assertTrue(later.state.value.notes.isEmpty()); assertNull(later.state.value.cachedAtMilliseconds)
    }
    private class DelayedCache : NotesReadCache {
        var continuation: Continuation<AppResult<NotesReadSnapshot?>>? = null
        override suspend fun load(scope: PendingNotesScope, nowMilliseconds: Long): AppResult<NotesReadSnapshot?> = suspendCoroutine { continuation = it }
        override suspend fun save(snapshot: NotesReadSnapshot): AppResult<Unit> = Outcome.Ok(Unit)
        override suspend fun remove(scope: PendingNotesScope): AppResult<Unit> = Outcome.Ok(Unit)
    }
    @Test fun lateCacheReadCannotPublishAfterDetach() = runTest {
        val cache = DelayedCache(); val repo = Repository(account); val model = RemoteNotesStore(this, cache = cache)
        model.bind(repo, account); runCurrent(); assertNotNull(cache.continuation)
        model.bind(null, null)
        val original = PendingNotesScope.make(account.authorityId, account.principalId, account.accountId)!!
        cache.continuation!!.resume(Outcome.Ok(NotesReadSnapshot.make(original, listOf(VersionedNote.make(UUID.randomUUID().toString(), "Old account", 1)!!), System.currentTimeMillis(), null)!!))
        advanceUntilIdle(); assertTrue(model.state.value.notes.isEmpty()); assertNull(model.state.value.cachedAtMilliseconds); assertEquals(0, repo.reads)
    }

    private class RetainedCache : NotesReadCache {
        val underlying = MemoryNotesReadCache()
        override suspend fun load(scope: PendingNotesScope, nowMilliseconds: Long) = underlying.load(scope, nowMilliseconds)
        override suspend fun save(snapshot: NotesReadSnapshot) = underlying.save(snapshot)
        override suspend fun remove(scope: PendingNotesScope): AppResult<Unit> = Outcome.Err(NotesCacheFailure.storage)
    }
    @Test fun knownRefusalSuppressesRetainedCacheUntilFreshAuthorizationSucceeds() = runTest {
        val repo = Repository(account).apply { seed() }; val cache = RetainedCache()
        val model = RemoteNotesStore(this, cache = cache); model.bind(repo, account); advanceUntilIdle()
        repo.readFailure = Failure.Forbidden(FailureMeta("No membership")); model.refresh(); advanceUntilIdle()
        assertNotNull(model.state.value.cacheFailure); assertTrue(model.state.value.notes.isEmpty())
        repo.readFailure = Failure.Unavailable(FailureMeta("Offline")); model.refresh(); advanceUntilIdle()
        assertTrue(model.state.value.notes.isEmpty()); assertNull(model.state.value.cachedAtMilliseconds); assertFalse(model.state.value.canWrite)
        repo.readFailure = null; model.refresh(); advanceUntilIdle(); assertTrue(model.state.value.canWrite)
        repo.readFailure = Failure.Timeout(FailureMeta("Offline")); model.refresh(); advanceUntilIdle()
        assertEquals(1, model.state.value.notes.size); assertNotNull(model.state.value.cachedAtMilliseconds); assertFalse(model.state.value.canWrite)
    }
    @Test fun unreadableJournalHidesEarlierSnapshotAndBlocksReadFallback() = runTest {
        val repo = Repository(account).apply { seed() }; val cache = MemoryNotesReadCache(); val journal = UncertainJournal()
        val model = RemoteNotesStore(this, journal, cache); model.bind(repo, account); advanceUntilIdle()
        repo.readFailure = Failure.Unavailable(FailureMeta("Offline")); model.refresh(); advanceUntilIdle()
        assertNotNull(model.state.value.cachedAtMilliseconds)
        journal.failLoad = true; model.refresh(); advanceUntilIdle()
        assertTrue(model.state.value.notes.isEmpty()); assertNull(model.state.value.cachedAtMilliseconds); assertFalse(model.state.value.recoveryLoaded); assertFalse(model.state.value.canWrite)
        assertEquals(2, repo.reads)
    }
}
