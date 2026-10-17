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
class RegistrationModelTest {
    private class Service : IdentityRegistration {
        var calls = 0; var failure: Failure? = null; var mismatch = false; var waits = false
        var continuation: Continuation<AppResult<RegisteredIdentity>>? = null
        override suspend fun register(command: RegisterIdentity): AppResult<RegisteredIdentity> {
            calls++
            if (waits) return suspendCoroutine { continuation = it }
            return failure?.let { Outcome.Err(it) } ?: Outcome.Ok(result(if (mismatch) "someone.else" else command.login, command.displayName))
        }
        fun finish() { continuation!!.resume(Outcome.Ok(result())); continuation = null }
    }
    private class Authority : SessionAuthority {
        override val id = "registration.test"
        override suspend fun signIn(command: SignIn): AppResult<SessionGrant> = Outcome.Ok(SessionGrant.make(id, Principal.make("principal", "Member")!!, Account.make("personal", "Personal")!!, "fixture-access", "fixture-refresh", 1_800_000_000_000)!!)
        override suspend fun renew(credential: StoredCredential): AppResult<SessionGrant> = Outcome.Err(SessionFailures.rejected)
        override suspend fun revoke(credential: StoredCredential): AppResult<Unit> = Outcome.Ok(Unit)
    }
    private fun TestScope.owner(vault: MemoryCredentialVault = MemoryCredentialVault()) = SessionController(this, Authority(), vault, { 1 }) { fail("Unexpected registration test failure") }
    @Test fun rejectsLocalInputAndSignedInSubmission() = runTest {
        val owner = owner(); val service = Service(); val model = RegistrationModel(this, service, owner)
        try {
            model.register("alex", "Alex", "short"); assertEquals(0, service.calls); assertEquals(FailureKind.INVALID, model.state.value.failure!!.kind)
            owner.signIn("existing", "private passphrase")!!.join()
            model.register("alex", "Alex", "private passphrase"); assertEquals(0, service.calls); assertFalse(model.canSubmit); assertNotNull(owner.lease)
        } finally { owner.shutdown() }
    }
    @Test fun successNeverSignsInOrWritesCredentials() = runTest {
        val vault = MemoryCredentialVault(); val owner = owner(vault); val service = Service(); val model = RegistrationModel(this, service, owner)
        try {
            model.register(" ALEX ", " Alex ", "private passphrase"); model.register("alex", "Alex", "private passphrase"); advanceUntilIdle()
            assertEquals(1, service.calls); assertEquals("alex", model.state.value.registered!!.login); assertEquals("alex", model.state.value.loginHint); assertFalse(model.state.value.uncertain)
            assertEquals(SessionPhase.SIGNED_OUT, owner.state.value.phase); assertNull(owner.lease); assertNull((vault.load() as Outcome.Ok).value)
            model.register("alex", "Alex", "private passphrase"); assertEquals(1, service.calls)
        } finally { owner.shutdown() }
    }
    @Test fun cancelPreservesUncertaintyAndLateReplyCannotAffectReplacement() = runTest {
        val owner = owner(); val service = Service().apply { waits = true }; val model = RegistrationModel(this, service, owner)
        try {
            model.register("alex", "Alex", "private passphrase"); runCurrent(); assertNotNull(service.continuation)
            model.cancel(); assertTrue(model.state.value.uncertain); assertEquals(RegistrationModel.interrupted, model.state.value.message)
            owner.signIn("existing", "private passphrase")!!.join(); val replacement = owner.lease!!
            service.finish(); advanceUntilIdle()
            assertTrue(owner.isCurrent(replacement)); assertNull(model.state.value.registered); assertTrue(model.state.value.uncertain); assertEquals(1, service.calls)
        } finally { owner.shutdown() }
    }
    @Test fun transportOrMismatchedReplyRequiresExplicitRecoveryWithoutReplay() = runTest {
        for (mismatch in listOf(false, true)) {
            val owner = owner(); val service = Service().apply { if (mismatch) this.mismatch = true else failure = Failure.Timeout(FailureMeta("Lost reply")) }
            val model = RegistrationModel(this, service, owner)
            try {
                model.register("alex", "Alex", "private passphrase"); advanceUntilIdle()
                assertTrue(model.state.value.uncertain); assertNull(model.state.value.registered); assertEquals(RegistrationModel.interrupted, model.state.value.message)
                model.open(); assertEquals(1, service.calls); assertTrue(model.state.value.uncertain); assertNull(owner.lease)
            } finally { owner.shutdown() }
        }
    }
    @Test fun duplicateRefusesWithoutInventingSessionSuccess() = runTest {
        val owner = owner(); val service = Service().apply { failure = Failure.Conflict(FailureMeta("That login is already registered.", "identity.login_exists")) }
        val model = RegistrationModel(this, service, owner)
        try {
            model.register("alex", "Alex", "private passphrase"); advanceUntilIdle()
            assertEquals(FailureKind.CONFLICT, model.state.value.failure!!.kind); assertFalse(model.state.value.uncertain)
            assertNull(model.state.value.registered); assertNull(owner.lease); assertEquals(1, service.calls)
        } finally { owner.shutdown() }
    }
    @Test fun checksSessionEpochBeforePublishing() = runTest {
        val owner = owner(); val service = Service().apply { waits = true }; val model = RegistrationModel(this, service, owner)
        try {
            model.register("alex", "Alex", "private passphrase"); runCurrent(); owner.signIn("existing", "private passphrase")!!.join()
            val replacement = owner.lease!!; service.finish(); advanceUntilIdle()
            assertTrue(owner.isCurrent(replacement)); assertNull(model.state.value.registered); assertEquals(1, service.calls)
        } finally { owner.shutdown() }
    }
    companion object {
        private fun result(login: String = "alex", name: String = "Alex") = RegisteredIdentity.make("00000000-0000-4000-8000-000000000001", login, name)!!
    }
}
