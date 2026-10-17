package dev.mobilefoundry.services

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID
import java.util.Base64
import java.security.SecureRandom

class GoNativeInstallationsTest {
    @Test fun realGoReadbackHandoffAndRetirement(): Unit = runBlocking {
        val base = System.getProperty("go.notes.test.base");assumeTrue("Owned Go runner supplies endpoint",base != null)
        fun <T> AppResult<T>.value(): T = (this as Outcome.Ok).value
        val raw = DefaultHTTPClient.create(base!!,OkHttpTransport()).value()
        val login = "installations-kotlin-${UUID.randomUUID()}";val password = "native integration passphrase"
        raw.requestEntity(HTTPMethod.POST,"identity/register",RequestOptions(body=buildJsonObject { put("login",login);put("display_name","Installations Kotlin");put("password",password) })).value()
        suspend fun signIn() = raw.requestEntity(HTTPMethod.POST,"identity/sign-in",RequestOptions(body=buildJsonObject { put("login",login);put("password",password) })).value().body.jsonObject
        fun bearer(grant: JsonObject) = object : HTTPClient {
            override suspend fun requestEntity(method: HTTPMethod,path: String,options: RequestOptions) = raw.requestEntity(method,path,options.copy(headers=options.headers + ("authorization" to "Bearer ${grant.getValue("access_token").jsonPrimitive.content}")))
        }
        suspend fun signOut(grant: JsonObject) { raw.requestEntity(HTTPMethod.POST,"identity/sign-out",RequestOptions(body=buildJsonObject { put("refresh_token",grant.getValue("refresh_token")) })).value() }
        val grant = signIn();var next = signIn();val a = bearer(grant);val b = bearer(next)
        val identity = InstallationIdentity.make(UUID.randomUUID().toString(),"i1." + Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) }))!!
        val route = PushRoute.make(PushProvider.FCM,"dev.example.acceptance",PushEnvironment.PRODUCTION)!!
        val target = DeliveryTarget.make(route,"native-installation-probe-${identity.id}")!!
        val repo = HTTPInstallationRegistry(a);var other = HTTPInstallationRegistry(b)
        assertNull(repo.read(identity).value())
        val dropped = HTTPInstallationRegistry(object : HTTPClient {
            override suspend fun requestEntity(method: HTTPMethod,path: String,options: RequestOptions): AppResult<Entity> {
                val result = a.requestEntity(method,path,options)
                return if (method == HTTPMethod.POST && result is Outcome.Ok) Outcome.Err(Failure.Timeout(FailureMeta("Acknowledgement deliberately dropped."))) else result
            }
        })
        val checkpoint = UUID.randomUUID().toString();assertTrue(dropped.register(identity,checkpoint,target,null) is Outcome.Err)
        val created = requireNotNull(repo.read(identity).value());assertEquals(checkpoint,created.registrationId);assertEquals(1L,created.version);assertTrue(created.currentBinding)
        val foreign = requireNotNull(other.read(identity).value());assertFalse(foreign.currentBinding)
        val rebound = other.register(identity,UUID.randomUUID().toString(),target,foreign).value();assertEquals(2L,rebound.version);assertTrue(rebound.currentBinding)
        next = raw.requestEntity(HTTPMethod.POST,"identity/renew",RequestOptions(body=buildJsonObject { put("refresh_token",next.getValue("refresh_token")) })).value().body.jsonObject
        other = HTTPInstallationRegistry(bearer(next));assertEquals(rebound,other.read(identity).value())
        assertEquals(FailureKind.FORBIDDEN,(repo.unregister(identity,created) as Outcome.Err).error.kind)
        signOut(grant);assertTrue(other.read(identity).value()!!.registered)
        val disabled = other.unregister(identity,rebound).value();assertFalse(disabled.registered);assertEquals(3L,disabled.version)
        assertEquals(disabled,other.unregister(identity,disabled).value())
        val final = other.register(identity,UUID.randomUUID().toString(),DeliveryTarget.make(route,target.token+".rotated")!!,disabled).value();assertEquals(4L,final.version)
        signOut(next)
        val observing = signIn();val observer = HTTPInstallationRegistry(bearer(observing));var retired = false
        for (attempt in 0 until 100) {
            val value = observer.read(identity).value()!!
            if (!value.registered) { assertEquals(5L,value.version);assertFalse(value.currentBinding);retired=true;break };delay(30)
        }
        assertTrue(retired);signOut(observing)
    }
}
