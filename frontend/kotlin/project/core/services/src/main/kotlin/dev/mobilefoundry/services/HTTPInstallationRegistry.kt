package dev.mobilefoundry.services

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

class HTTPInstallationRegistry(private val client: HTTPClient) : InstallationRegistry {
    override suspend fun read(identity: InstallationIdentity): AppResult<InstallationRecord?> {
        val result = request(HTTPMethod.GET,"installations/${identity.id}",identity)
        if (result is Outcome.Err && result.error.kind == FailureKind.NOT_FOUND) return Outcome.Ok(null)
        return result.flatMap { e ->
            val value = decode(e)
            if (e.metadata.status != 200 || value == null || value.id != identity.id) Outcome.Err(invalid(e)) else Outcome.Ok(value)
        }
    }
    override suspend fun register(identity: InstallationIdentity, registrationId: String, target: DeliveryTarget, replacing: InstallationRecord?): AppResult<InstallationRecord> {
        if (!RemoteServiceAdmission.uuid(registrationId) || replacing != null && (replacing.id != identity.id || replacing.registrationId == registrationId || replacing.version >= 9_007_199_254_740_991L)) return Outcome.Err(InstallationFailures.invalidInput)
        val body = buildJsonObject {
            if (replacing == null) put("installation_id",identity.id)
            put("registration_id",registrationId); put("provider",target.route.provider.wire); put("application",target.route.application); put("environment",target.route.environment.wire); put("token",target.token)
        }
        return request(if (replacing == null) HTTPMethod.POST else HTTPMethod.PUT,if (replacing == null) "installations" else "installations/${identity.id}",identity,body,replacing?.version).flatMap { e ->
            val v = decode(e)
            if (e.metadata.status != (if (replacing == null) 201 else 200) || v == null || v.id != identity.id || v.registrationId != registrationId || v.version != (replacing?.version ?: 0) + 1 || v.route != target.route || !v.registered || !v.currentBinding || replacing != null && replacing.createdAt != v.createdAt) Outcome.Err(invalid(e)) else Outcome.Ok(v)
        }
    }
    override suspend fun unregister(identity: InstallationIdentity, current: InstallationRecord): AppResult<InstallationRecord> {
        if (current.id != identity.id || !current.currentBinding || current.registered && current.version >= 9_007_199_254_740_991L) return Outcome.Err(InstallationFailures.invalidInput)
        return request(HTTPMethod.DELETE,"installations/${identity.id}",identity,version=current.version).flatMap { e ->
            val v = decode(e)
            if (e.metadata.status != 200 || v == null || v.id != identity.id || v.registrationId != current.registrationId || v.version != current.version + (if (current.registered) 1 else 0) || v.route != current.route || v.registered || !v.currentBinding || v.createdAt != current.createdAt || v.expiresAt != current.expiresAt) Outcome.Err(invalid(e)) else Outcome.Ok(v)
        }
    }
    private suspend fun request(method: HTTPMethod,path: String,identity: InstallationIdentity,body: JsonElement? = null,version: Long? = null): AppResult<Entity> {
        currentCoroutineContext().ensureActive()
        val result = client.requestEntity(method,path,RequestOptions(headers=mapOf("X-Installation-Key" to identity.key),body=body,ifMatch=version?.let { "\"$it\"" }))
        currentCoroutineContext().ensureActive();return result
    }
    private fun decode(e: Entity): InstallationRecord? {
        val b = e.body as? JsonObject ?: return null
        if (b.keys != setOf("id","registration_id","version","provider","application","environment","registered","current_binding","created_at","updated_at","expires_at")) return null
        val p = b["version"] as? JsonPrimitive ?: return null
        val n = p.takeUnless { it.isString }?.doubleOrNull ?: return null
        if (!n.isFinite() || n < 1 || n > 9_007_199_254_740_991.0 || kotlin.math.floor(n) != n || e.metadata.etag != "\"${n.toLong()}\"") return null
        val provider = PushProvider.entries.find { it.wire == b["provider"].installationText() } ?: return null
        val env = PushEnvironment.entries.find { it.wire == b["environment"].installationText() } ?: return null
        val route = PushRoute.make(provider,b["application"].installationText() ?: return null,env) ?: return null
        fun boolean(name: String) = (b[name] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
        return InstallationRecord.make(b["id"].installationText() ?: return null,b["registration_id"].installationText() ?: return null,n.toLong(),route,boolean("registered") ?: return null,boolean("current_binding") ?: return null,
            RemoteServiceAdmission.timestamp(b["created_at"].installationText()) ?: return null,RemoteServiceAdmission.timestamp(b["updated_at"].installationText()) ?: return null,RemoteServiceAdmission.timestamp(b["expires_at"].installationText()) ?: return null)
    }
    private fun invalid(e: Entity) = Failure.Internal(FailureMeta("The installation response was invalid.","installations.invalid_remote_response",requestId=e.metadata.requestId,correlationId=e.metadata.correlationId))
}
