package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.*
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.json.*

/** Physical installation ownership, independent of the selected principal/account. */
class InstallationIdentity private constructor(val id: String, val key: String) {
    override fun toString() = "InstallationIdentity(<redacted>)"
    fun encoded(): ByteArray = buildJsonObject { put("version",1);put("id",id);put("key",key) }.toString().toByteArray(Charsets.UTF_8)
    companion object {
        fun make(id: String, key: String): InstallationIdentity? {
            if (!RemoteServiceAdmission.uuid(id) || !key.matches(Regex("i1\\.[A-Za-z0-9_-]{43}"))) return null
            val bytes = try { Base64.getUrlDecoder().decode(key.drop(3)) } catch (_: IllegalArgumentException) { return null }
            if (bytes.size != 32 || Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) != key.drop(3)) return null
            return InstallationIdentity(id,key)
        }
        fun decode(bytes: ByteArray): AppResult<InstallationIdentity> {
            if (bytes.size > 1024) return Outcome.Err(InstallationFailures.invalidStorage)
            val b = try { Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject } catch (_: IllegalArgumentException) { null }
            val version = b?.get("version") as? JsonPrimitive
            val value = if (version?.isString == false && version.intOrNull == 1) make(b["id"].installationText() ?: "",b["key"].installationText() ?: "") else null
            return value?.let { Outcome.Ok(it) } ?: Outcome.Err(InstallationFailures.invalidStorage)
        }
    }
}
/** Serialize load/create; persist before returning; never reset corrupt/lost-key records. */
fun interface InstallationVault { suspend fun loadOrCreate(): AppResult<InstallationIdentity> }
enum class PushProvider(val wire: String) { APNS("apns"), FCM("fcm") }
enum class PushEnvironment(val wire: String) { SANDBOX("sandbox"), PRODUCTION("production") }
@ConsistentCopyVisibility
data class PushRoute private constructor(val provider: PushProvider, val application: String, val environment: PushEnvironment) {
    companion object { fun make(provider: PushProvider, application: String, environment: PushEnvironment): PushRoute? =
        if (application.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) && (provider != PushProvider.FCM || environment == PushEnvironment.PRODUCTION)) PushRoute(provider,application,environment) else null }
}
/** Opaque delivery address; this admission does not attest a real provider token. */
class DeliveryTarget private constructor(val route: PushRoute, val token: String) {
    override fun toString() = "DeliveryTarget(<redacted>)"
    companion object { fun make(route: PushRoute, token: String): DeliveryTarget? =
        if (token.length in 1..4096 && token.all { it.code in 33..126 }) DeliveryTarget(route,token) else null }
}
@ConsistentCopyVisibility
data class InstallationRecord private constructor(val id: String, val registrationId: String, val version: Long, val route: PushRoute,
    val registered: Boolean, val currentBinding: Boolean, val createdAt: Instant, val updatedAt: Instant, val expiresAt: Instant) {
    companion object {
        fun make(id: String, registrationId: String, version: Long, route: PushRoute, registered: Boolean, currentBinding: Boolean, createdAt: Instant, updatedAt: Instant, expiresAt: Instant): InstallationRecord? =
            if (RemoteServiceAdmission.uuid(id) && RemoteServiceAdmission.uuid(registrationId) && version in 1..9_007_199_254_740_991L &&
                listOf(createdAt,updatedAt,expiresAt).all { it.epochSecond in 0 until 253402300800L } && createdAt <= updatedAt && createdAt < expiresAt)
                InstallationRecord(id,registrationId,version,route,registered,currentBinding,createdAt,updatedAt,expiresAt) else null
    }
}
/** Bound to one exact lease. No account target, automatic renewal or replay. */
interface InstallationRegistry {
    suspend fun read(identity: InstallationIdentity): AppResult<InstallationRecord?>
    suspend fun register(identity: InstallationIdentity, registrationId: String, target: DeliveryTarget, replacing: InstallationRecord?): AppResult<InstallationRecord>
    suspend fun unregister(identity: InstallationIdentity, current: InstallationRecord): AppResult<InstallationRecord>
}
object InstallationFailures {
    val invalidInput = Failure.Invalid(FailureMeta("Check the installation target and current revision.","installations.invalid_input"),emptyMap())
    val invalidStorage = Failure.Unavailable(FailureMeta("The saved installation key could not be read. It has been kept.","installations.storage_invalid"))
    val storage = Failure.Unavailable(FailureMeta("Secure installation storage is unavailable. Unlock the device and try again.","installations.storage_unavailable"))
}
internal fun JsonElement?.installationText() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
