package dev.mobilefoundry.session

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Locale
import java.util.UUID

class GoNativeRegistrationTest {
    @Test fun explicitSignInAndDuplicateNeverChangesCredentials() = runBlocking {
        val base = System.getProperty("go.session.test.base").orEmpty()
        assumeTrue("Owned Go API required", base.isNotEmpty())
        val transport = OkHttpTransport()
        val service = (HTTPIdentityRegistration.create(base, transport, true) as Outcome.Ok).value
        val login = "kotlin-registration-${UUID.randomUUID()}"; val password = "native registration private password"
        val command = (RegisterIdentity.make(" ${login.uppercase(Locale.ROOT)} ", " Cafe\u0301 🧱 ", password) as Outcome.Ok).value
        val registered = (service.register(command) as Outcome.Ok).value; assertTrue(registered.confirms(command))
        val vault = MemoryCredentialVault()
        val owner = SessionController(this, (GoSessionAuthority.create(base, transport, allowLocalHTTP = true) as Outcome.Ok).value, vault, System::currentTimeMillis) { fail("Unexpected registration flow failure") }
        try {
            assertNull(owner.lease); assertEquals(SessionPhase.SIGNED_OUT, owner.state.value.phase); assertNull((vault.load() as Outcome.Ok).value)
            owner.signIn(command.login, password)!!.join()
            assertEquals(registered.id, owner.lease!!.scope.principalId); assertEquals("Personal", owner.state.value.account!!.name)
            val original = owner.lease!!
            val duplicate = (RegisterIdentity.make(login, "Replacement", "a different duplicate password") as Outcome.Ok).value
            val failure = (service.register(duplicate) as Outcome.Err).error
            assertEquals(FailureKind.CONFLICT, failure.kind); assertEquals("identity.login_exists", failure.meta.code); assertTrue(owner.isCurrent(original))
            owner.signOut()!!.join(); owner.signIn(login, password)!!.join()
            assertEquals(registered.id, owner.lease!!.scope.principalId); assertEquals(command.displayName, owner.state.value.principal!!.displayName)
            owner.signOut()!!.join()
        } finally { owner.shutdown() }
    }
}
