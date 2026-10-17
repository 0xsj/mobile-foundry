package dev.mobilefoundry.session

import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

class GoNativeAccountTest {
    @Test fun creationCASOwnershipAndExplicitSelection() = runBlocking {
        val base = System.getProperty("go.session.test.base", "").orEmpty(); assumeTrue("Owned API required", base.isNotEmpty())
        val transport = OkHttpTransport()
        val registration = (HTTPIdentityRegistration.create(base, transport, true) as Outcome.Ok).value
        val login = "kotlin-workspace-${UUID.randomUUID()}"; val password = "owned workspace passphrase"
        assertTrue(registration.register((RegisterIdentity.make(login, "Workspace owner", password) as Outcome.Ok).value) is Outcome.Ok)
        val authority = (GoSessionAuthority.create(base, transport, allowLocalHTTP = true) as Outcome.Ok).value
        val owner = SessionController(this, authority, MemoryCredentialVault(), System::currentTimeMillis) { fail("Unexpected workspace failure") }
        val other = SessionController(this, authority, MemoryCredentialVault(), System::currentTimeMillis) { fail("Unexpected foreign owner failure") }
        try {
            owner.signIn(login, password)!!.join(); val lease = owner.lease!!
            val service = HTTPAccountManagement((AuthenticatedHTTPClient.create(base, transport, owner, lease, allowLocalHTTP = true) as Outcome.Ok).value, lease.scope.principalId)
            val personal = (service.read(lease.scope.accountId) as Outcome.Ok).value
            val name = (AccountName.make(" Native project 🧱 ") as Outcome.Ok).value
            val project = (service.create(name) as Outcome.Ok).value
            assertTrue(owner.isCurrent(lease)); assertEquals(personal.account.id, owner.state.value.account!!.id); assertNotEquals(personal.account.id, project.account.id)
            assertEquals(2L, (service.rename(project, (AccountName.make("Renamed project") as Outcome.Ok).value) as Outcome.Ok).value.version)
            assertEquals(FailureKind.CONFLICT, (service.rename(project, name) as Outcome.Err).error.kind)
            assertEquals(2L, (service.read(project.account.id) as Outcome.Ok).value.version)
            val otherLogin = "kotlin-other-${UUID.randomUUID()}"
            assertTrue(registration.register((RegisterIdentity.make(otherLogin, "Other owner", password) as Outcome.Ok).value) is Outcome.Ok)
            other.signIn(otherLogin, password)!!.join(); val otherLease = other.lease!!
            val foreign = HTTPAccountManagement((AuthenticatedHTTPClient.create(base, transport, other, otherLease, allowLocalHTTP = true) as Outcome.Ok).value, otherLease.scope.principalId)
            assertEquals(FailureKind.FORBIDDEN, (foreign.read(project.account.id) as Outcome.Err).error.kind)
            owner.selectAccount(project.account.id)!!.join(); assertEquals("Renamed project", owner.state.value.account!!.name); assertFalse(owner.isCurrent(lease))
            assertEquals(FailureKind.CANCELED, (service.create(name) as Outcome.Err).error.kind)
            other.signOut()!!.join(); owner.signOut()!!.join()
        } finally { other.shutdown(); owner.shutdown() }
    }
}
