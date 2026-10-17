package dev.mobilefoundry.services

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

class GoNativeFilesTest {
    @Test fun realGoBinaryTransferAndReceiptRecovery(): Unit = runBlocking {
        val base = System.getProperty("go.notes.test.base"); assumeTrue("Owned Go runner supplies endpoint", base != null)
        fun <T> AppResult<T>.value(): T = (this as Outcome.Ok).value
        val raw = DefaultHTTPClient.create(base!!, OkHttpTransport()).value()
        val login = "files-kotlin-${UUID.randomUUID()}"; val password = "native integration passphrase"
        raw.requestEntity(HTTPMethod.POST, "identity/register", RequestOptions(body = buildJsonObject { put("login", login); put("display_name", "Files Kotlin"); put("password", password) })).value()
        val grant = raw.requestEntity(HTTPMethod.POST, "identity/sign-in", RequestOptions(body = buildJsonObject { put("login", login); put("password", password) })).value().body.jsonObject
        val token = grant.getValue("access_token").jsonPrimitive.content; val principal = grant.getValue("principal").jsonObject.getValue("id").jsonPrimitive.content
        val bearer = object : HTTPClient {
            override suspend fun requestEntity(method: HTTPMethod, path: String, options: RequestOptions) = raw.requestEntity(method, path, options.copy(headers = mapOf("authorization" to "Bearer $token")))
            override suspend fun requestBytes(method: HTTPMethod, path: String, options: RequestOptions) = raw.requestBytes(method, path, options.copy(headers = mapOf("authorization" to "Bearer $token")))
        }
        val account = bearer.requestEntity(HTTPMethod.POST, "accounts/bootstrap", RequestOptions(body = buildJsonObject { put("name", "Files") })).value().body.jsonObject.getValue("id").jsonPrimitive.content
        val repo = HTTPRemoteFilesRepository(bearer, account, principal)
        fun drop(suffix: String): HTTPClient = object : HTTPClient {
            var lost = false
            override suspend fun requestEntity(method: HTTPMethod, path: String, options: RequestOptions): AppResult<Entity> {
                val answer = bearer.requestEntity(method, path, options)
                if (path.endsWith(suffix) && !lost) { lost = true; return Outcome.Err(Failure.Unavailable(FailureMeta("Acknowledgement deliberately dropped."))) }; return answer
            }
        }
        val bytes = byteArrayOf(0, -1, 1, 13, 10, -128, 0)
        val spec = UploadSpec.prepare("opaque.bin", "application/octet-stream", bytes).value(); val key = UUID.randomUUID().toString()
        assertTrue(HTTPRemoteFilesRepository(drop("/uploads"), account, principal).requestUpload(spec, "$key.request") is Outcome.Err)
        val requested = repo.receipt("$key.request").value().file
        assertEquals(requested, repo.requestUpload(spec, "$key.request").value().file)
        assertEquals(1, repo.page().value().files.size)
        repo.uploadContent(requested, bytes).value()
        assertTrue(HTTPRemoteFilesRepository(drop("/complete"), account, principal).complete(requested, "$key.complete") is Outcome.Err)
        val available = repo.receipt("$key.complete").value().file
        assertEquals(FileStatus.AVAILABLE, available.status); assertEquals(2L, available.version)
        assertEquals(available, repo.complete(requested, "$key.complete").value().file)
        assertArrayEquals(bytes, repo.download(available).value())
        repo.delete(available, "$key.delete").value(); assertTrue(repo.page().value().files.isEmpty())
        assertEquals(available, repo.complete(requested, "$key.complete").value().file)
        assertEquals(FailureKind.NOT_FOUND, (repo.download(available) as Outcome.Err).error.kind)
        raw.requestEntity(HTTPMethod.POST, "identity/sign-out", RequestOptions(body = buildJsonObject { put("refresh_token", grant.getValue("refresh_token")) }))
    }
}
