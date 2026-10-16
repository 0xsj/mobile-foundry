package dev.mobilefoundry.session

import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

typealias CredentialCheckpoint = suspend (StoredCredential) -> AppResult<Unit>

interface SessionAuthority {
    val id: String
    suspend fun signIn(command: SignIn): AppResult<SessionGrant>
    suspend fun renew(credential: StoredCredential): AppResult<SessionGrant>
    /** Multi-step authorities checkpoint rotated credentials before subsequent IO. */
    suspend fun renew(credential: StoredCredential, checkpoint: CredentialCheckpoint): AppResult<SessionGrant> {
        val answer = renew(credential)
        if (answer is Outcome.Ok) {
            val saved = checkpoint(StoredCredential.from(answer.value))
            if (saved is Outcome.Err) return saved
        }
        return answer
    }
    suspend fun revoke(credential: StoredCredential): AppResult<Unit>
}
interface AccountSelectingAuthority : SessionAuthority {
    suspend fun selectAccount(accountId: String, grant: SessionGrant): AppResult<SessionGrant>
}
data class AccountPage(val accounts: List<Account>, val nextCursor: String?)
interface AccountDirectory { suspend fun list(cursor: String? = null, limit: Int = 20): AppResult<AccountPage> }
interface CredentialVault {
    suspend fun load(): AppResult<StoredCredential?>
    suspend fun save(credential: StoredCredential): AppResult<Unit>
    suspend fun clear(): AppResult<Unit>
}
class MemoryCredentialVault(initial: StoredCredential? = null) : CredentialVault {
    private val mutex = Mutex()
    private var credential = initial
    override suspend fun load(): AppResult<StoredCredential?> = mutex.withLock { currentCoroutineContext().ensureActive(); Outcome.Ok(credential) }
    override suspend fun save(credential: StoredCredential): AppResult<Unit> = mutex.withLock { currentCoroutineContext().ensureActive(); this.credential = credential; Outcome.Ok(Unit) }
    override suspend fun clear(): AppResult<Unit> = mutex.withLock { currentCoroutineContext().ensureActive(); credential = null; Outcome.Ok(Unit) }
}
