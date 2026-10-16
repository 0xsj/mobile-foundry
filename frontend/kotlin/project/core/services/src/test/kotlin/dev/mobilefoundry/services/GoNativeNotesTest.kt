package dev.mobilefoundry.services
import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

class GoNativeNotesTest {
    @Test fun realGoWriteRecoveryAndVersions(): Unit = runBlocking {
        val base = System.getProperty("go.notes.test.base"); assumeTrue("Owned Go runner supplies endpoint",base != null)
        val transport = OkHttpTransport()
        val raw = (DefaultHTTPClient.create(base!!,transport) as Outcome.Ok).value
        fun <T> AppResult<T>.value(): T = (this as Outcome.Ok).value
        val login = "notes-kotlin-${UUID.randomUUID()}"; val password = "native integration passphrase"
        raw.requestEntity(HTTPMethod.POST,"identity/register",RequestOptions(body = buildJsonObject { put("login",login);put("display_name","Notes Kotlin");put("password",password) })).value()
        val grant = raw.requestEntity(HTTPMethod.POST,"identity/sign-in",RequestOptions(body = buildJsonObject { put("login",login);put("password",password) })).value().body.jsonObject
        val token = grant.getValue("access_token").jsonPrimitive.content; val principal = grant.getValue("principal").jsonObject.getValue("id").jsonPrimitive.content
        val bearer = object : HTTPClient {
            override suspend fun requestEntity(method: HTTPMethod,path: String,options: RequestOptions) = raw.requestEntity(method,path,options.copy(headers = mapOf("authorization" to "Bearer $token")))
        }
        val account = bearer.requestEntity(HTTPMethod.POST,"accounts/bootstrap",RequestOptions(body = buildJsonObject { put("name","Notes") })).value().body.jsonObject.getValue("id").jsonPrimitive.content
        val repo = HTTPRemoteNotesRepository(bearer,account,principal)
        var lost = false
        val lostClient = object : HTTPClient {
            override suspend fun requestEntity(method: HTTPMethod,path: String,options: RequestOptions): AppResult<Entity> {
                val result = bearer.requestEntity(method,path,options)
                if (method == HTTPMethod.POST && !lost) { lost = true; return Outcome.Err(Failure.Unavailable(FailureMeta("Acknowledgement deliberately dropped by the test."))) }; return result
            }
        }
        val create = RemoteNoteWrite.create(UUID.randomUUID().toString(),"  Café 🧱\u0000\n  ").value()
        assertTrue(HTTPRemoteNotesRepository(lostClient,account,principal).execute(create) is Outcome.Err)
        val receipt = repo.receipt(create).value(); assertEquals(receipt.noteId,repo.execute(create).value().noteId);assertEquals(1,repo.page().value().notes.size)
        val note = repo.get(receipt.noteId).value();assertEquals(create.title,note.title);assertEquals(1L,note.version)
        val update = RemoteNoteWrite.update(UUID.randomUUID().toString(),note.id,"Edited",1).value();assertEquals(2L,repo.execute(update).value().version)
        val stale = RemoteNoteWrite.update(UUID.randomUUID().toString(),note.id,"Stale",1).value();assertEquals(FailureKind.CONFLICT,(repo.execute(stale) as Outcome.Err).error.kind)
        val delete = RemoteNoteWrite.delete(UUID.randomUUID().toString(),note.id,2).value();assertEquals(3L,repo.execute(delete).value().version)
        assertEquals(receipt.noteId,repo.receipt(create).value().noteId);assertEquals(receipt.noteId,repo.execute(create).value().noteId);assertTrue(repo.page().value().notes.isEmpty())
        val other = bearer.requestEntity(HTTPMethod.POST,"accounts",RequestOptions(body = buildJsonObject { put("name","Other") })).value().body.jsonObject.getValue("id").jsonPrimitive.content
        assertEquals(FailureKind.NOT_FOUND,(HTTPRemoteNotesRepository(bearer,other,principal).receipt(create) as Outcome.Err).error.kind)
        raw.requestEntity(HTTPMethod.POST,"identity/sign-out",RequestOptions(body = buildJsonObject { put("refresh_token",grant.getValue("refresh_token")) }))
    }
}
