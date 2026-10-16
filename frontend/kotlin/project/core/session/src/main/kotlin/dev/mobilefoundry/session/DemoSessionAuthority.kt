package dev.mobilefoundry.session

import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Fixture authority: generated tokens do not authorize real data or prove identity. */
class DemoSessionAuthority(private val now: () -> Long) : SessionAuthority {
    override val id = "catalog-demo.v1"
    private val mutex = Mutex()
    private val revoked = mutableSetOf<String>()
    private var rejectRenewals = false
    suspend fun rejectRenewals() = mutex.withLock { rejectRenewals = true }
    override suspend fun signIn(command: SignIn): AppResult<SessionGrant> = mutex.withLock {
        currentCoroutineContext().ensureActive()
        val person = when (command.login) { "alex@example.test" -> "alex"; "sam@example.test" -> "sam"; else -> null }
        if (person == null || command.password != "catalog-demo") Outcome.Err(Failure.Unauthenticated(FailureMeta("Use Alex or Sam with the catalog demo password.", "session.demo_credentials")))
        else grant(person)
    }
    override suspend fun renew(credential: StoredCredential): AppResult<SessionGrant> = mutex.withLock {
        currentCoroutineContext().ensureActive()
        val parts = credential.refreshToken.split('.')
        val uuid = parts.getOrNull(3)?.let { try { UUID.fromString(it) } catch (_: IllegalArgumentException) { null } }
        if (rejectRenewals || credential.scope.authorityId != id || parts.size != 4 || parts[0] != "demo" || parts[1] != "refresh" ||
            parts[2] !in listOf("alex", "sam") || uuid == null || credential.refreshToken in revoked) Outcome.Err(SessionFailures.rejected)
        else { revoked += credential.refreshToken; grant(parts[2]) }
    }
    override suspend fun revoke(credential: StoredCredential): AppResult<Unit> = mutex.withLock {
        currentCoroutineContext().ensureActive(); revoked += credential.refreshToken; Outcome.Ok(Unit)
    }
    private fun grant(person: String): AppResult<SessionGrant> {
        val time = now()
        if (time <= 0 || time > Long.MAX_VALUE - 60_000) return Outcome.Err(SessionFailures.expired)
        return Outcome.Ok(SessionGrant.make(id, Principal.make(person, if (person == "alex") "Alex" else "Sam")!!,
            Account.make("$person-personal", "${if (person == "alex") "Alex" else "Sam"}'s workspace")!!,
            "demo.access.$person.${UUID.randomUUID()}", "demo.refresh.$person.${UUID.randomUUID()}", time + 60_000)!!)
    }
}
/** Manual fixture clock, deliberately independent of wall time and recomposition. */
class ManualSessionClock(initial: Long) {
    private val value = AtomicLong(initial)
    fun now() = value.get()
    fun advance(milliseconds: Long) { require(milliseconds in 0..86_400_000); value.updateAndGet { Math.addExact(it, milliseconds) } }
}
