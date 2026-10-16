package dev.mobilefoundry.session

import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

private fun grant(person: String = "alex", expiry: Long = 1_700_000_060_000) = SessionGrant.make("catalog-demo.v1",
    Principal.make(person, person)!!, Account.make("$person-personal", person)!!, "access.secret", "refresh.secret", expiry)
private class StubAuthority(var answer: AppResult<SessionGrant>) : SessionAuthority {
    override val id = "catalog-demo.v1"
    var calls = 0
    var revokeAnswer: AppResult<Unit> = Outcome.Ok(Unit)
    override suspend fun signIn(command: SignIn): AppResult<SessionGrant> { calls++; return answer }
    override suspend fun renew(credential: StoredCredential): AppResult<SessionGrant> { calls++; return answer }
    override suspend fun revoke(credential: StoredCredential) = revokeAnswer
}
private class FailingVault(val loadFails: Boolean = false, val saveFails: Boolean = false, val clearFails: Boolean = false) : CredentialVault {
    override suspend fun load(): AppResult<StoredCredential?> = if (loadFails) Outcome.Err(SessionFailures.storage) else Outcome.Ok(null)
    override suspend fun save(credential: StoredCredential): AppResult<Unit> = if (saveFails) Outcome.Err(SessionFailures.storage) else Outcome.Ok(Unit)
    override suspend fun clear(): AppResult<Unit> = if (clearFails) Outcome.Err(SessionFailures.storage) else Outcome.Ok(Unit)
}
private fun TestScope.owner(authority: SessionAuthority, vault: CredentialVault, clock: ManualSessionClock) =
    SessionController(this, authority, vault, clock::now) { throw AssertionError(it) }
private suspend fun CredentialVault.saved() = (load() as Outcome.Ok).value

@OptIn(ExperimentalCoroutinesApi::class)
class SessionTest {
    @Test fun sharedAdmissionAndVersionedCredentials() {
        val fixture = Json.parseToJsonElement(File(System.getProperty("session.fixtures"), "admission.json").readText()).jsonObject
        fixture.getValue("commands").jsonArray.forEach { element ->
            val row = element.jsonObject; val password = row.getValue("password").jsonPrimitive.content
            when (val answer = SignIn.make(row.getValue("login").jsonPrimitive.content, password)) {
                is Outcome.Ok -> { assertTrue(row.getValue("valid").jsonPrimitive.boolean); assertEquals(row.getValue("normalizedLogin").jsonPrimitive.content, answer.value.login); assertEquals(password, answer.value.password); assertFalse(answer.value.toString().contains(password)) }
                is Outcome.Err -> { assertFalse(row.getValue("valid").jsonPrimitive.boolean); assertTrue((answer.error as Failure.Invalid).fields.containsKey(row.getValue("field").jsonPrimitive.content)) }
            }
        }
        fixture.getValue("scopes").jsonArray.forEach { element -> val row = element.jsonObject
            assertEquals(row.getValue("valid").jsonPrimitive.boolean, SessionScope.make(row.getValue("authority").jsonPrimitive.content,
                row.getValue("principal").jsonPrimitive.content, row.getValue("account").jsonPrimitive.content) != null)
        }
        fixture.getValue("credentials").jsonArray.forEach { element -> val row = element.jsonObject
            when (val answer = StoredCredential.decode(row.toString().toByteArray())) {
                is Outcome.Ok -> { assertTrue(row.getValue("valid").jsonPrimitive.boolean); assertEquals(answer.value, (StoredCredential.decode(answer.value.encoded()) as Outcome.Ok).value); assertFalse(answer.value.toString().contains(answer.value.refreshToken)) }
                is Outcome.Err -> assertFalse(row.getValue("valid").jsonPrimitive.boolean)
            }
        }
        fixture.getValue("expiries").jsonArray.forEach { element -> val row = element.jsonObject
            assertEquals(row.getValue("valid").jsonPrimitive.boolean, grant(expiry = row.getValue("value").jsonPrimitive.long) != null)
        }
        assertTrue(StoredCredential.decode("not json".toByteArray()) is Outcome.Err)
    }
    @Test fun restoreRotatesAfterPersistenceAndNewOwnerRejectsOldLease() = runTest {
        val clock = ManualSessionClock(1_700_000_000_000); val vault = MemoryCredentialVault()
        val first = owner(DemoSessionAuthority(clock::now), vault, clock)
        first.restore()!!.join(); assertEquals(SessionPhase.SIGNED_OUT, first.state.value.phase)
        first.signIn("alex@example.test", "catalog-demo")!!.join(); val old = first.lease!!; val saved = vault.saved()
        first.shutdown(); assertFalse(first.isCurrent(old)); assertNull(first.renew()); assertNull(first.restore()); assertNull(first.signOut()); assertNull(first.signIn("sam", "ok"))
        val second = owner(DemoSessionAuthority(clock::now), vault, clock); second.restore()!!.join()
        assertEquals("alex", second.state.value.principal!!.id); assertNotNull(second.lease); assertFalse(second.isCurrent(old)); assertNotEquals(saved, vault.saved())
        second.signOut()!!.join(); assertEquals(SessionPhase.SIGNED_OUT, second.state.value.phase); assertNull(vault.saved())
    }
    @Test fun expiryAndAccountReplacementClosePriorLeases() = runTest {
        val clock = ManualSessionClock(1_700_000_000_000); val controller = owner(DemoSessionAuthority(clock::now), MemoryCredentialVault(), clock)
        controller.signIn("alex@example.test", "catalog-demo")!!.join(); val old = controller.lease!!
        clock.advance(60_000); assertNull(controller.lease); assertNull(controller.accessToken(old))
        controller.expireIfNeeded(); assertEquals(SessionFailures.expired, controller.state.value.failure)
        controller.renew()!!.join(); assertNotNull(controller.lease); assertFalse(controller.isCurrent(old))
        controller.signIn("sam@example.test", "catalog-demo")!!.join(); assertEquals("sam", controller.state.value.principal!!.id); assertFalse(controller.isCurrent(old))
    }
    @Test fun invalidCommandDoesNotTouchAuthorityOrActiveScope() = runTest {
        val clock = ManualSessionClock(1_700_000_000_000); val authority = StubAuthority(Outcome.Ok(grant()!!)); val controller = owner(authority, MemoryCredentialVault(), clock)
        controller.signIn("alex", "ok")!!.join(); val old = controller.lease
        assertNull(controller.signIn(" ", "")); assertEquals(1, authority.calls); assertEquals(old, controller.lease); assertTrue(controller.state.value.failure is Failure.Invalid)
    }
    @Test fun transientRenewalKeepsSavedCredentialButRejectionClearsIt() = runTest {
        val clock = ManualSessionClock(1_700_000_000_000); val vault = MemoryCredentialVault(); val authority = StubAuthority(Outcome.Ok(grant()!!)); val controller = owner(authority, vault, clock)
        controller.signIn("alex", "ok")!!.join(); val saved = vault.saved()
        authority.answer = Outcome.Err(SessionFailures.storage); controller.renew()!!.join(); assertNull(controller.lease); assertEquals(saved, vault.saved())
        authority.answer = Outcome.Err(SessionFailures.rejected); controller.renew()!!.join(); assertNull(vault.saved()); assertEquals(SessionFailures.rejected, controller.state.value.failure)
    }
    @Test fun renewalCannotReassignStoredScopeAndIssuerMismatchAvoidsNetwork() = runTest {
        val clock = ManualSessionClock(1_700_000_000_000); val vault = MemoryCredentialVault(StoredCredential.from(grant()!!)); val authority = StubAuthority(Outcome.Ok(grant("sam")!!)); val controller = owner(authority, vault, clock)
        controller.restore()!!.join(); assertNull(controller.lease); assertEquals(SessionFailures.scopeMismatch, controller.state.value.failure); assertNull(vault.saved()); assertEquals(1, authority.calls)
        vault.save(StoredCredential.make(SessionScope.make("other", "alex", "alex-personal")!!, "token")!!)
        controller.restore()!!.join(); assertEquals(1, authority.calls); assertNull(vault.saved())
    }
    @Test fun storageFailuresNeverActivateScopeOrFalselyCompleteLogout() = runTest {
        val clock = ManualSessionClock(1_700_000_000_000); val authority = StubAuthority(Outcome.Ok(grant()!!))
        for (vault in listOf(FailingVault(loadFails = true), FailingVault(saveFails = true), FailingVault(clearFails = true))) {
            val controller = owner(authority, vault, clock)
            controller.restore()!!.join()
            if (vault.loadFails) { assertNull(controller.lease); assertEquals(SessionPhase.BLOCKED, controller.state.value.phase) }
            if (!vault.loadFails) { controller.signIn("alex", "ok")!!.join(); assertNull(controller.lease); assertEquals(SessionPhase.BLOCKED, controller.state.value.phase) }
            controller.signOut()!!.join(); assertEquals(if (vault.clearFails) SessionPhase.BLOCKED else SessionPhase.SIGNED_OUT, controller.state.value.phase)
        }
    }
    @Test fun expiredGrantIsNotSavedAndRemoteRevokeFailureStillSignsOutLocally() = runTest {
        val clock = ManualSessionClock(1_700_000_000_000); val vault = MemoryCredentialVault(); val authority = StubAuthority(Outcome.Ok(grant(expiry = clock.now())!!)); val controller = owner(authority, vault, clock)
        controller.signIn("alex", "ok")!!.join(); assertEquals(SessionFailures.expired, controller.state.value.failure); assertNull(vault.saved())
        authority.answer = Outcome.Ok(grant()!!); controller.signIn("alex", "ok")!!.join()
        authority.revokeAnswer = Outcome.Err(SessionFailures.storage); controller.signOut()!!.join()
        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase); assertEquals(SessionFailures.storage, controller.state.value.failure); assertNull(vault.saved())
    }
    @Test fun signOutWaitsForUncancelableSaveBeforeClearing() = runTest {
        val clock = ManualSessionClock(1_700_000_000_000); val vault = SaveGateVault(); val controller = owner(StubAuthority(Outcome.Ok(grant()!!)), vault, clock)
        val signIn = controller.signIn("alex", "ok")!!; vault.entered.await()
        val logout = controller.signOut()!!; assertNull(controller.lease); assertEquals(SessionPhase.SIGNING_OUT, controller.state.value.phase)
        vault.release.complete(Unit); signIn.join(); logout.join()
        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase); assertNull(vault.saved())
    }
    @Test fun cancelIgnoringOperationSettlesWithoutActivatingIdentity() = runTest {
        val clock = ManualSessionClock(1_700_000_000_000); val vault = SaveGateVault(); val controller = owner(StubAuthority(Outcome.Ok(grant()!!)), vault, clock)
        val signIn = controller.signIn("alex", "ok")!!; vault.entered.await(); signIn.cancel(); vault.release.complete(Unit); signIn.join()
        assertFalse(controller.isBusy); assertNull(controller.lease); assertTrue(controller.state.value.failure is Failure.Canceled)
        controller.signOut()!!.join(); assertNull(vault.saved())
    }
    @Test fun demoRotationRejectsPreviousTokenOnlyInThatAuthorityInstance() = runTest {
        val clock = ManualSessionClock(1_700_000_000_000); val authority = DemoSessionAuthority(clock::now)
        val grant = (authority.signIn((SignIn.make("alex@example.test", "catalog-demo") as Outcome.Ok).value) as Outcome.Ok).value
        val saved = StoredCredential.from(grant); val renewed = (authority.renew(saved) as Outcome.Ok).value
        assertNotEquals(grant.refreshToken, renewed.refreshToken); assertEquals(Outcome.Err(SessionFailures.rejected), authority.renew(saved))
        assertEquals(saved.scope, (DemoSessionAuthority(clock::now).renew(saved) as Outcome.Ok).value.scope)
    }
    @Test fun lateDefectAfterShutdownCannotPublishOrReachDiagnostics() = runTest {
        val clock = ManualSessionClock(1_700_000_000_000); val authority = LateDefectAuthority(); val vault = MemoryCredentialVault(); val controller = owner(authority, vault, clock)
        val task = controller.signIn("alex", "ok")!!; authority.entered.await(); controller.shutdown(); authority.release.complete(Unit); task.join()
        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase); assertNull(controller.state.value.failure); assertNull(vault.saved())
    }
    @Test fun cancellationSuppressesAnIgnoredLateDefectWithoutShutdown() = runTest {
        val clock = ManualSessionClock(1_700_000_000_000); val authority = LateDefectAuthority(); val controller = owner(authority, MemoryCredentialVault(), clock)
        val task = controller.signIn("alex", "ok")!!; authority.entered.await(); task.cancel(); authority.release.complete(Unit); task.join()
        assertNull(controller.lease); assertFalse(controller.isBusy); assertTrue(controller.state.value.failure is Failure.Canceled)
    }
}
private class LateDefectAuthority : SessionAuthority {
    override val id = "catalog-demo.v1"
    val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
    override suspend fun signIn(command: SignIn): AppResult<SessionGrant> = withContext(NonCancellable) { entered.complete(Unit); release.await(); error("Late fixture defect") }
    override suspend fun renew(credential: StoredCredential): AppResult<SessionGrant> = Outcome.Err(SessionFailures.rejected)
    override suspend fun revoke(credential: StoredCredential): AppResult<Unit> = Outcome.Ok(Unit)
}
private class SaveGateVault : CredentialVault {
    val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
    private var credential: StoredCredential? = null
    override suspend fun load(): AppResult<StoredCredential?> = Outcome.Ok(credential)
    override suspend fun save(credential: StoredCredential): AppResult<Unit> = withContext(NonCancellable) {
        entered.complete(Unit); release.await(); this@SaveGateVault.credential = credential; Outcome.Ok(Unit)
    }
    override suspend fun clear(): AppResult<Unit> { credential = null; return Outcome.Ok(Unit) }
}
