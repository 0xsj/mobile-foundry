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
    @Test fun fileDetailAdmissionRejectsUnrelatedDeletedAndUnversionedMetadata() = runBlocking {
        val account = "00000000-0000-4000-8000-000000000002"; val principal = "00000000-0000-4000-8000-000000000003"; val id = "00000000-0000-4000-8000-000000000004"
        val base = buildJsonObject { put("id", id); put("account_id", account); put("owner_id", principal); put("name", "File.png"); put("content_type", "image/png"); put("byte_size", 3); put("sha256", "a".repeat(64)); put("status", "available"); put("version", 2) }
        for (scenario in listOf("available", "pending", "wrong-id", "wrong-account", "deleted", "wrong-etag", "missing-etag")) {
            val fields = base.toMutableMap(); var etag: String? = "\"2\""
            when (scenario) {
                "pending" -> { fields["status"] = JsonPrimitive("pending_upload"); fields["version"] = JsonPrimitive(1); etag = "\"1\"" }
                "wrong-id" -> fields["id"] = JsonPrimitive(principal)
                "wrong-account" -> fields["account_id"] = JsonPrimitive(principal)
                "deleted" -> { fields["status"] = JsonPrimitive("deleted"); fields["version"] = JsonPrimitive(3); etag = "\"3\"" }
                "wrong-etag" -> etag = "\"1\""
                "missing-etag" -> etag = null
            }
            val requests = ArrayList<PreparedRequest>()
            val client = (DefaultHTTPClient.create("https://example.invalid/v1", HTTPTransport {
                requests += it; Outcome.Ok(WireResponse(200, etag?.let { value -> mapOf("etag" to value) } ?: emptyMap(), JsonObject(fields).toString().toByteArray()))
            }) as Outcome.Ok).value
            val repo = HTTPRemoteFilesRepository(client, account, principal); val result = repo.get(id)
            if (scenario in listOf("available", "pending")) assertEquals(id, (result as Outcome.Ok).value.id)
            else assertEquals("files.invalid_remote_response", (result as Outcome.Err).error.meta.code)
            for (invalid in listOf("../escape", "$id\n", "0".repeat(36))) assertTrue(repo.get(invalid) is Outcome.Err)
            assertEquals(1, requests.size); assertEquals(HTTPMethod.GET, requests.single().method)
            assertEquals("/v1/accounts/$account/files/$id", requests.single().url.encodedPath)
            assertNull(requests.single().headers["idempotency-key"]); assertNull(requests.single().headers["if-match"])
        }
    }
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
        assertEquals(requested, repo.get(requested.id).value())
        assertEquals(requested, repo.requestUpload(spec, "$key.request").value().file)
        assertEquals(1, repo.page().value().files.size)
        repo.uploadContent(requested, bytes).value()
        assertTrue(HTTPRemoteFilesRepository(drop("/complete"), account, principal).complete(requested, "$key.complete") is Outcome.Err)
        val available = repo.receipt("$key.complete").value().file
        assertEquals(available, repo.get(available.id).value())
        assertEquals(FileStatus.AVAILABLE, available.status); assertEquals(2L, available.version)
        assertEquals(available, repo.complete(requested, "$key.complete").value().file)
        assertArrayEquals(bytes, repo.download(available).value())
        val inbox = HTTPNotificationInboxRepository(bearer, account, principal)
        var notice: InboxNotification? = null
        for (attempt in 0 until 100) {
            val page = inbox.page().value()
            if (page.items.isNotEmpty()) { assertEquals(1L,page.unreadCount);assertEquals(1,page.items.size);notice=page.items.single();break }
            kotlinx.coroutines.delay(30)
        }
        val delivered = requireNotNull(notice);assertEquals(available.id,delivered.fileId);assertTrue(delivered.isUnread)
        assertTrue(HTTPNotificationInboxRepository(drop("/read"),account,principal).markRead(delivered) is Outcome.Err)
        val read = inbox.page().value().items.single();assertFalse(read.isUnread);assertNotNull(read.readAt)
        assertEquals(read.readAt,inbox.markRead(read).value().readAt)
        assertTrue(inbox.page(unreadOnly=true).value().items.isEmpty())
        assertTrue(HTTPNotificationInboxRepository(drop("/notification-preferences"),account,principal).setMediaEnabled(false) is Outcome.Err)
        assertFalse(inbox.mediaEnabled().value())
        val other = bearer.requestEntity(HTTPMethod.POST,"accounts",RequestOptions(body=buildJsonObject { put("name","Other inbox") })).value().body.jsonObject.getValue("id").jsonPrimitive.content
        val otherInbox = HTTPNotificationInboxRepository(bearer,other,principal)
        assertTrue(otherInbox.page().value().items.isEmpty());assertTrue(otherInbox.mediaEnabled().value())
        repo.delete(available, "$key.delete").value(); assertTrue(repo.page().value().files.isEmpty())
        assertEquals(FailureKind.NOT_FOUND, (repo.get(available.id) as Outcome.Err).error.kind)
        assertEquals(available, repo.complete(requested, "$key.complete").value().file)
        assertEquals(FailureKind.NOT_FOUND, (repo.download(available) as Outcome.Err).error.kind)
        raw.requestEntity(HTTPMethod.POST, "identity/sign-out", RequestOptions(body = buildJsonObject { put("refresh_token", grant.getValue("refresh_token")) }))
    }
}
