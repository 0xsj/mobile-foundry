package dev.mobilefoundry.session

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.serialization.json.*

/** Inject a lease-bound HTTP client. The directory owns no credentials or session state. */
class HTTPAccountDirectory(private val client: HTTPClient, private val principalId: String) : AccountDirectory {
    override suspend fun list(cursor: String?, limit: Int): AppResult<AccountPage> {
        if (!GoSessionWire.uuid(principalId) || limit !in 1..100 || cursor != null && cursor.toByteArray(Charsets.UTF_8).size !in 1..256)
            return Outcome.Err(Failure.Invalid(FailureMeta("Use an admitted account page and principal.", "session.account_page"), emptyMap()))
        val query = listOf(QueryItem("limit", limit.toString())) + (cursor?.let { listOf(QueryItem("cursor", it)) } ?: emptyList())
        return client.requestEntity(HTTPMethod.GET, "accounts", RequestOptions(query = query)).flatMap { entity ->
            val body = entity.body as? JsonObject
            val rows = body?.get("accounts") as? JsonArray
            if (entity.metadata.status != 200 || rows == null || rows.size > limit) return@flatMap Outcome.Err(GoSessionWire.failure(entity))
            val next = when (val value = body["next_cursor"]) {
                JsonNull -> null
                is JsonPrimitive -> value.takeIf { it.isString && it.content.toByteArray(Charsets.UTF_8).size in 1..256 }?.content ?: return@flatMap Outcome.Err(GoSessionWire.failure(entity))
                else -> return@flatMap Outcome.Err(GoSessionWire.failure(entity))
            }
            val accounts = mutableListOf<Account>()
            var prior = ""
            for (row in rows) {
                val account = GoSessionWire.account(row, principalId) ?: return@flatMap Outcome.Err(GoSessionWire.failure(entity))
                if (account.id <= prior) return@flatMap Outcome.Err(GoSessionWire.failure(entity))
                accounts += account; prior = account.id
            }
            if (next != null && accounts.size != limit) Outcome.Err(GoSessionWire.failure(entity)) else Outcome.Ok(AccountPage(accounts.toList(), next))
        }
    }
}
