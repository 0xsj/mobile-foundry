package dev.mobilefoundry.services

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class HealthStatus { OK }
data class Health(val requestId: String?, val correlationId: String?, val version: String?) { val status: HealthStatus get() = HealthStatus.OK }

class HealthService(private val http: HTTPClient) {
    suspend fun live(options: RequestOptions = RequestOptions()): AppResult<Health> = check("health/live", options)
    suspend fun ready(options: RequestOptions = RequestOptions()): AppResult<Health> = check("health/ready", options)
    private suspend fun check(path: String, options: RequestOptions): AppResult<Health> = when (val result = http.requestEntity(HTTPMethod.GET, path, options)) {
        is Outcome.Err -> result
        is Outcome.Ok -> {
            val entity = result.value
            val status = (entity.body as? JsonObject)?.get("status") as? JsonPrimitive
            if (status?.isString == true && status.content == "ok") Outcome.Ok(Health(entity.metadata.requestId, entity.metadata.correlationId, entity.metadata.etag))
            else Outcome.Err(Failure.Internal(FailureMeta("The API returned an unexpected response.", "http.invalid_response", entity.metadata.requestId, entity.metadata.correlationId)))
        }
    }
}
