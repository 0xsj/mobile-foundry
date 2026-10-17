package dev.mobilefoundry.product.connection

import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.session.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkspaceModelTest {
    private class Service : AccountManagement, AccountDirectory {
        var value = managed(); var writes = 0; var writeFailure: Failure? = null; var mismatch = false; var waits = false
        var continuation: Continuation<AppResult<ManagedAccount>>? = null
        override suspend fun read(accountId: String): AppResult<ManagedAccount> = Outcome.Ok(value)
        override suspend fun list(cursor: String?, limit: Int): AppResult<AccountPage> = Outcome.Ok(AccountPage(listOf(value.account), null))
        override suspend fun create(name: AccountName): AppResult<ManagedAccount> {
            writes++
            if (waits) return suspendCoroutine { continuation = it }
            return writeFailure?.let { Outcome.Err(it) } ?: Outcome.Ok(managed("project", if (mismatch) "foreign" else "principal", name.value))
        }
        override suspend fun rename(account: ManagedAccount, name: AccountName): AppResult<ManagedAccount> {
            writes++; writeFailure?.let { return Outcome.Err(it) }
            value = managed(name = name.value, version = account.version + 1); return Outcome.Ok(value)
        }
        fun finish() { continuation!!.resume(Outcome.Ok(managed(id = "project"))); continuation = null }
    }
    private class Authority : SessionAuthority {
        override val id = "workspace.test"
        override suspend fun signIn(command: SignIn): AppResult<SessionGrant> = Outcome.Ok(SessionGrant.make(id, Principal.make("principal", "Member")!!, Account.make("personal", "Personal")!!, "fixture-access", "fixture-refresh", 1_800_000_000_000)!!)
        override suspend fun renew(credential: StoredCredential) = signIn((SignIn.make("member", "private password") as Outcome.Ok).value)
        override suspend fun revoke(credential: StoredCredential): AppResult<Unit> = Outcome.Ok(Unit)
    }
    private suspend fun TestScope.owner(): SessionController {
        val owner = SessionController(this, Authority(), MemoryCredentialVault(), { 1 }) { fail("Unexpected workspace failure") }
        owner.signIn("member", "private password")!!.join(); return owner
    }
    private fun TestScope.model(owner: SessionController, service: Service): WorkspaceModel = WorkspaceModel(this, owner).also {
        it.bind(service, service, owner.lease); it.refresh(); advanceUntilIdle()
    }
    @Test fun admissionDoubleSubmitAndSelectionStayIndependent() = runTest {
        val owner = owner()
        try {
            val lease = owner.lease!!; val service = Service(); val model = model(owner, service)
            model.create(" \n "); assertEquals(0, service.writes); assertEquals(FailureKind.INVALID, model.state.value.failure!!.kind)
            model.create(" Project "); model.create("Queued duplicate"); advanceUntilIdle()
            assertEquals(1, service.writes); assertEquals("Project", model.state.value.created!!.account.name); assertFalse(model.canCreate)
            assertTrue(owner.isCurrent(lease)); assertEquals("personal", owner.state.value.account!!.id)
            model.create("Another queued duplicate"); assertEquals(1, service.writes)
        } finally { owner.shutdown() }
    }
    @Test fun renameConflictRequiresReadAndKeepsLease() = runTest {
        val owner = owner()
        try {
            val lease = owner.lease!!; val service = Service(); val model = model(owner, service)
            service.writeFailure = Failure.Conflict(FailureMeta("Workspace changed")); model.rename("Mine"); advanceUntilIdle()
            assertNull(model.state.value.current); assertNull(model.state.value.displayAccount); assertEquals(FailureKind.CONFLICT, model.state.value.failure!!.kind); assertFalse(model.canRename)
            model.rename("Blind retry"); assertEquals(1, service.writes)
            service.writeFailure = null; service.value = managed(name = "Someone else's rename", version = 2)
            model.refresh(); advanceUntilIdle(); assertEquals(2L, model.state.value.current!!.version)
            model.rename("Reviewed rename"); advanceUntilIdle(); assertEquals(3L, model.state.value.current!!.version); assertTrue(owner.isCurrent(lease))
            model.cancel(); assertEquals("Reviewed rename", model.state.value.displayAccount!!.name); assertFalse(model.canRename)
        } finally { owner.shutdown() }
    }
    @Test fun lostCreateNeedsReviewAndExplicitDuplicateRiskChoice() = runTest {
        val owner = owner()
        try {
            val service = Service(); val model = model(owner, service)
            service.writeFailure = Failure.Timeout(FailureMeta("Lost reply")); model.create("Project"); advanceUntilIdle()
            assertTrue(model.state.value.createUncertain); assertFalse(model.canCreate); assertEquals(1, service.writes)
            model.refresh(); advanceUntilIdle(); model.allowNewCreate(); assertTrue(model.state.value.createUncertain)
            model.review(); advanceUntilIdle(); assertTrue(model.state.value.reviewed); assertEquals(1, model.state.value.reviewAccounts.size)
            model.allowNewCreate(); assertFalse(model.state.value.createUncertain); assertTrue(model.canCreate); assertEquals(1, service.writes)
            model.create("Second uncertain create"); advanceUntilIdle(); model.allowNewCreate()
            assertTrue(model.state.value.createUncertain); assertFalse(model.state.value.reviewed); assertEquals(2, service.writes)
            model.refresh(); advanceUntilIdle(); model.review(); advanceUntilIdle(); model.allowNewCreate()
            service.writeFailure = null; model.create("Deliberately new"); advanceUntilIdle(); assertEquals(3, service.writes)
        } finally { owner.shutdown() }
    }
    @Test fun canceledCreateRetainsWarningAndLateReplyCannotPublish() = runTest {
        val owner = owner()
        try {
            val service = Service(); val model = model(owner, service)
            service.waits = true; model.create("Project"); runCurrent(); assertNotNull(service.continuation)
            model.bind(null, null, null); model.bind(service, service, owner.lease); assertTrue(model.state.value.createUncertain)
            service.finish(); advanceUntilIdle(); assertNull(model.state.value.created); assertTrue(model.state.value.createUncertain); assertEquals(1, service.writes)
        } finally { owner.shutdown() }
    }
    @Test fun mismatchedProviderReplyCannotConfirmCreation() = runTest {
        val owner = owner()
        try {
            val service = Service(); val model = model(owner, service)
            service.mismatch = true; model.create("Project"); advanceUntilIdle()
            assertNull(model.state.value.created); assertNull(model.state.value.current); assertTrue(model.state.value.createUncertain); assertEquals(FailureKind.INTERNAL, model.state.value.failure!!.kind)
        } finally { owner.shutdown() }
    }
    @Test fun oldLeaseCannotPublishWithoutCompositionRebind() = runTest {
        val owner = owner()
        try {
            val service = Service(); val model = model(owner, service)
            service.waits = true; model.create("Project"); runCurrent(); owner.renew()!!.join(); val replacement = owner.lease!!
            service.finish(); advanceUntilIdle(); assertTrue(owner.isCurrent(replacement)); assertNull(model.state.value.created); assertFalse(model.canCreate)
            model.bind(service, service, replacement); assertTrue(model.state.value.createUncertain)
        } finally { owner.shutdown() }
    }
    companion object {
        private fun managed(id: String = "personal", owner: String = "principal", name: String = "Personal", version: Long = 1) = ManagedAccount.make(id, owner, name, version, 1, 1)!!
    }
}
