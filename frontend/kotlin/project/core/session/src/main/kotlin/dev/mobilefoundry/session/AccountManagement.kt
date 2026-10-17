package dev.mobilefoundry.session

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.session.GoSessionWire.string
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

/** Scalar-counted, normalization-preserving names using Go's explicit space set. */
class AccountName private constructor(val value: String) {
    override fun toString() = "AccountName(<redacted>)"
    companion object {
        private val spaces = "\u0009\u000A\u000B\u000C\u000D \u0085\u00A0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u2028\u2029\u202F\u205F\u3000".toSet()
        fun make(input: String): AppResult<AccountName> {
            val value = input.trim { it in spaces }
            return if (value.codePointCount(0, value.length) in 1..80 && value.codePoints().noneMatch { it in 0xD800..0xDFFF || Character.getType(it) == Character.CONTROL.toInt() }) Outcome.Ok(AccountName(value))
            else Outcome.Err(Failure.Invalid(FailureMeta("Enter a workspace name of 1–80 characters without control characters.", "account.invalid_name"), mapOf("name" to "Use 1–80 characters without control characters.")))
        }
    }
}

/** Versioned observation; independent of session selection. */
class ManagedAccount private constructor(val account: Account, val ownerId: String, val version: Long, val createdAtMicros: Long, val updatedAtMicros: Long) {
    override fun toString() = "ManagedAccount(<redacted>)"
    companion object {
        fun make(id: String, ownerId: String, name: String, version: Long, createdAtMicros: Long, updatedAtMicros: Long): ManagedAccount? {
            val account = Account.make(id, name) ?: return null
            val admitted = AccountName.make(name)
            if (Principal.make(ownerId, "Owner") == null || admitted !is Outcome.Ok || admitted.value.value != name || version !in 1..9_007_199_254_740_991 || createdAtMicros < 0 || updatedAtMicros < createdAtMicros) return null
            return ManagedAccount(account, ownerId, version, createdAtMicros, updatedAtMicros)
        }
    }
}

/** One attempt; ownership/CAS belong to the provider. No receipt or replay contract. */
interface AccountManagement {
    suspend fun read(accountId: String): AppResult<ManagedAccount>
    suspend fun create(name: AccountName): AppResult<ManagedAccount>
    suspend fun rename(account: ManagedAccount, name: AccountName): AppResult<ManagedAccount>
}

/** Inject an exact-lease client; never renews or selects an account. */
class HTTPAccountManagement(private val client: HTTPClient, private val principalId: String) : AccountManagement {
    override suspend fun read(accountId: String): AppResult<ManagedAccount> {
        if (!GoSessionWire.uuid(accountId) || !GoSessionWire.uuid(principalId)) return Outcome.Err(SessionFailures.scopeMismatch)
        return request(HTTPMethod.GET, "accounts/$accountId", 200).flatMap { if (it.account.id == accountId) Outcome.Ok(it) else Outcome.Err(unexpected()) }
    }
    override suspend fun create(name: AccountName): AppResult<ManagedAccount> {
        if (!GoSessionWire.uuid(principalId)) return Outcome.Err(SessionFailures.scopeMismatch)
        return request(HTTPMethod.POST, "accounts", 201, RequestOptions(body = buildJsonObject { put("name", name.value) })).flatMap {
            if (it.version == 1L && it.createdAtMicros == it.updatedAtMicros && it.account.name == name.value) Outcome.Ok(it) else Outcome.Err(unexpected())
        }
    }
    override suspend fun rename(account: ManagedAccount, name: AccountName): AppResult<ManagedAccount> {
        if (!GoSessionWire.uuid(account.account.id) || account.ownerId != principalId || !GoSessionWire.uuid(principalId) || account.version >= 9_007_199_254_740_991)
            return Outcome.Err(Failure.Invalid(FailureMeta("Load a current owned workspace before renaming.", "account.rename_input"), emptyMap()))
        return request(HTTPMethod.PATCH, "accounts/${account.account.id}", 200, RequestOptions(headers = mapOf("If-Match" to "\"${account.version}\""), body = buildJsonObject { put("name", name.value) })).flatMap {
            if (it.account.id == account.account.id && it.version == account.version + 1 && it.createdAtMicros == account.createdAtMicros && it.updatedAtMicros >= account.updatedAtMicros && it.account.name == name.value) Outcome.Ok(it) else Outcome.Err(unexpected())
        }
    }
    private fun unexpected() = Failure.Internal(FailureMeta("The account operation returned an unexpected response. Refresh before another write.", "account.invalid_response"))
    private suspend fun request(method: HTTPMethod, path: String, status: Int, options: RequestOptions = RequestOptions()): AppResult<ManagedAccount> {
        currentCoroutineContext().ensureActive()
        val answer = client.requestEntity(method, path, options)
        currentCoroutineContext().ensureActive()
        return answer.flatMap { entity ->
            val b = entity.body as? JsonObject
            val id = b?.string("id"); val name = b?.string("name"); val version = GoSessionWire.version(b?.get("version"))
            val created = b?.string("created_at")?.let(GoSessionWire::micros); val updated = b?.string("updated_at")?.let(GoSessionWire::micros)
            val value = if (id != null && GoSessionWire.uuid(id) && name != null && version != null && created != null && updated != null) ManagedAccount.make(id, principalId, name, version, created, updated) else null
            if (entity.metadata.status == status && b?.keys == setOf("id", "owner_id", "name", "version", "created_at", "updated_at") && b.string("owner_id") == principalId && version != null && entity.metadata.etag == "\"$version\"" && value != null) Outcome.Ok(value)
            else Outcome.Err(GoSessionWire.failure(entity))
        }
    }
}
