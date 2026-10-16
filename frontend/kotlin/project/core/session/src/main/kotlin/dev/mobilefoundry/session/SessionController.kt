package dev.mobilefoundry.session

import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Call from one UI executor (normally Main). The caller owns and closes this scope. */
class SessionController(private val scope: CoroutineScope, private val authority: SessionAuthority,
    private val vault: CredentialVault, private val now: () -> Long, private val onUnexpectedError: (Throwable) -> Unit) {
    private val mutableState = MutableStateFlow(SessionSnapshot())
    val state: StateFlow<SessionSnapshot> = mutableState.asStateFlow()
    private var grant: SessionGrant? = null
    private var credential: StoredCredential? = null
    private var persistedCredential: StoredCredential? = null
    private var generation = 0L
    private var epoch = 0L
    private var request: Job? = null
    private var closed = false
    private val ownerId = UUID.randomUUID()
    val isBusy get() = state.value.phase.isBusy
    val canRenew get() = !closed && !isBusy && credential != null
    val canSelectAccount get() = !closed && !isBusy && lease != null && authority is AccountSelectingAuthority
    val lease: SessionLease? get() = grant?.takeIf { it.expiresAtMillis > now() }?.let { SessionLease(it.scope, epoch, ownerId) }
    fun isCurrent(lease: SessionLease) = this.lease == lease
    fun accessToken(lease: SessionLease): String? = if (isCurrent(lease)) grant?.accessToken else null
    fun expireIfNeeded() { if (grant != null && lease == null) { invalidate(); block(SessionFailures.expired) } }

    fun restore(): Job? {
        if (closed || isBusy) return null
        return start(SessionPhase.RESTORING) { current ->
            val pending = credential
            if (pending != null && pending != persistedCredential) { renew(pending, current); return@start }
            val loaded = vault.load(); if (!admit(current)) return@start
            when (loaded) {
                is Outcome.Err -> block(loaded.error)
                is Outcome.Ok -> { val saved = loaded.value; credential = saved; persistedCredential = saved; if (saved == null) mutableState.value = SessionSnapshot(epoch = epoch) else renew(saved, current) }
            }
        }
    }
    fun signIn(login: String, password: String): Job? {
        if (closed || isBusy) return null
        val admitted = SignIn.make(login, password)
        if (admitted is Outcome.Err) { mutableState.value = state.value.copy(failure = admitted.error); return null }
        val command = (admitted as Outcome.Ok).value
        return start(SessionPhase.SIGNING_IN) { current ->
            val cleared = vault.clear(); if (!admit(current)) return@start
            if (cleared is Outcome.Err) { block(cleared.error); return@start }
            credential = null
            persistedCredential = null
            val answer = authority.signIn(command); if (!admit(current)) return@start
            when (answer) { is Outcome.Err -> block(answer.error); is Outcome.Ok -> activate(answer.value, null, current) }
        }
    }
    fun renew(): Job? {
        val saved = credential ?: return null
        if (closed || isBusy) return null
        return start(SessionPhase.RENEWING) { current -> renew(saved, current) }
    }
    private suspend fun renew(saved: StoredCredential, current: Long) {
        if (saved.scope.authorityId != authority.id) { refuseAndClear(SessionFailures.scopeMismatch, current); return }
        if (persistedCredential != saved) {
            val written = vault.save(saved); if (!admit(current)) return
            if (written is Outcome.Err) { block(written.error); return }
            persistedCredential = saved
        }
        val answer = authority.renew(saved) { rotated -> checkpoint(rotated, saved.scope, current) }; if (!admit(current)) return
        when (answer) {
            is Outcome.Ok -> activate(answer.value, saved.scope, current)
            is Outcome.Err -> if (answer.error.kind in listOf(FailureKind.UNAUTHENTICATED, FailureKind.FORBIDDEN)) refuseAndClear(answer.error, current) else block(answer.error)
        }
    }
    private suspend fun checkpoint(rotated: StoredCredential, expected: SessionScope, current: Long): AppResult<Unit> {
        if (!admit(current)) return Outcome.Err(SessionFailures.scopeChanged)
        if (rotated.scope != expected || rotated.scope.authorityId != authority.id) return Outcome.Err(SessionFailures.scopeMismatch)
        credential = rotated // Retain the known current family even if durable saving fails.
        val answer = vault.save(rotated)
        if (!admit(current)) return Outcome.Err(SessionFailures.scopeChanged)
        if (answer is Outcome.Ok) persistedCredential = rotated
        return answer
    }
    fun selectAccount(accountId: String): Job? {
        val prior = grant ?: return null
        if (!canSelectAccount || accountId == prior.account.id) return null
        val selecting = authority as? AccountSelectingAuthority ?: return null
        val expected = SessionScope.make(authority.id, prior.principal.id, accountId) ?: return null
        return start(SessionPhase.SELECTING_ACCOUNT) { current ->
            val answer = selecting.selectAccount(accountId, prior); if (!admit(current)) return@start
            when (answer) {
                is Outcome.Ok -> activate(answer.value, expected, current)
                is Outcome.Err -> block(answer.error)
            }
        }
    }
    private suspend fun activate(value: SessionGrant, expected: SessionScope?, current: Long) {
        if (value.scope.authorityId != authority.id || expected != null && expected != value.scope) { refuseAndClear(SessionFailures.scopeMismatch, current); return }
        if (value.expiresAtMillis <= now()) { block(SessionFailures.expired); return }
        val saved = StoredCredential.from(value)
        val written = if (persistedCredential == saved) Outcome.Ok(Unit) else vault.save(saved); if (!admit(current)) return
        if (written is Outcome.Err) { block(written.error); return }
        persistedCredential = saved
        credential = saved
        if (value.expiresAtMillis <= now()) { block(SessionFailures.expired); return }
        grant = value; mutableState.value = SessionSnapshot(SessionPhase.SIGNED_IN, value.principal, value.account, epoch = epoch)
    }
    private suspend fun refuseAndClear(failure: Failure, current: Long) {
        val cleared = vault.clear(); if (!admit(current)) return
        if (cleared is Outcome.Err) block(cleared.error) else { credential = null; persistedCredential = null; block(failure) }
    }
    fun signOut(): Job? {
        if (closed || state.value.phase == SessionPhase.SIGNING_OUT) return null
        val prior = request; val previous = credential
        prior?.cancel()
        return start(SessionPhase.SIGNING_OUT) { current ->
            prior?.join(); if (!admit(current)) return@start
            val cleared = vault.clear(); if (!admit(current)) return@start
            if (cleared is Outcome.Err) { block(cleared.error); return@start }
            credential = null
            persistedCredential = null
            val failure = try { previous?.let { (authority.revoke(it) as? Outcome.Err)?.error?.publicInfo() } }
                catch (canceled: CancellationException) { throw canceled }
                catch (error: Exception) { if (!admit(current)) return@start; onUnexpectedError(error); Failure.Internal(FailureMeta("An unexpected error occurred.")) }
            if (admit(current)) mutableState.value = SessionSnapshot(failure = failure, epoch = epoch)
        }
    }
    fun shutdown() { closed = true; generation++; request?.cancel(); request = null; invalidate(); mutableState.value = SessionSnapshot(epoch = epoch) }
    private fun invalidate() { epoch++; grant = null }
    private fun block(failure: Failure) { grant = null; mutableState.value = SessionSnapshot(SessionPhase.BLOCKED, failure = failure.publicInfo(), epoch = epoch) }
    private suspend fun admit(current: Long) = current == generation && currentCoroutineContext().isActive
    private fun start(phase: SessionPhase, operation: suspend (Long) -> Unit): Job {
        generation++; val current = generation; invalidate(); mutableState.value = SessionSnapshot(phase, epoch = epoch)
        // Assign before running: Unconfined/immediate executors may complete inline.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try { operation(current) }
            catch (canceled: CancellationException) { if (current == generation) canceled() }
            catch (error: Exception) {
                if (current == generation) {
                    if (!currentCoroutineContext().isActive) canceled()
                    else { onUnexpectedError(error); if (current == generation) block(Failure.Internal(FailureMeta("An unexpected error occurred."))) }
                }
            }
            finally { if (current == generation) { if (!currentCoroutineContext().isActive && state.value.phase.isBusy) canceled(); request = null } }
        }
        request = job; job.start(); return job
    }
    private fun canceled() = block(Failure.Canceled(FailureMeta("Session action stopped. Restore or sign in again.", "session.canceled")))
}
