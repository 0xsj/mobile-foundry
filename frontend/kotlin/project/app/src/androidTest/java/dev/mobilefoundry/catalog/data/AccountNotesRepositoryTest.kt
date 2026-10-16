package dev.mobilefoundry.catalog.data

import androidx.test.platform.app.InstrumentationRegistry
import dev.mobilefoundry.data.*
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import dev.mobilefoundry.session.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AccountNotesRepositoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun scope(person: String = "alex", account: String = "alex-personal", authority: String = "catalog-demo.v1") = SessionScope.make(authority, person, account)!!
    private fun <T> AppResult<T>.value() = (this as Outcome.Ok).value
    @Test fun sharedAccountPartitionFixtures() {
        val rows = Json.parseToJsonElement(InstrumentationRegistry.getInstrumentation().context.assets.open("partitions.json").bufferedReader().use { it.readText() }).jsonArray
        rows.forEach { element -> val row = element.jsonObject
            assertEquals(row.getValue("identifier").jsonPrimitive.content, AccountNotesPartition.identifier(scope(row.getValue("principal").jsonPrimitive.content, row.getValue("account").jsonPrimitive.content, row.getValue("authority").jsonPrimitive.content)))
        }
    }
    @Test fun nativeSQLitePartitionsReopenAndKeepGuestNotes() = runBlocking {
        val namespace = "account-test-${UUID.randomUUID()}"; val guestName = "$namespace-guest.sqlite"
        try {
            val guest = SQLiteNotesRepository(context, guestName); val guestNote = guest.create(CreateNote.make("Guest").value()).value()
            val factory = AccountNotesRepositories(context, AccountNotesStorage.DEVICE, namespace)
            val alex = factory.repository(scope()); val sam = factory.repository(scope("sam", "sam-personal"))
            val note = alex.create(CreateNote.make("Alex only").value()).value()
            assertTrue(sam.list().value().isEmpty())
            assertTrue((sam.update(UpdateNote.make(note.id, "Wrong account").value()) as Outcome.Err).error is Failure.NotFound)
            assertTrue((sam.delete(DeleteNote.make(note.id).value()) as Outcome.Err).error is Failure.NotFound)
            listOf(scope(account = "Alex-personal"), scope(authority = "other")).forEach { assertTrue(factory.repository(it).list().value().isEmpty()) }
            assertEquals(listOf(note), factory.repository(scope("other-member")).list().value())
            val reopened = AccountNotesRepositories(context, AccountNotesStorage.DEVICE, namespace).repository(scope())
            assertEquals(listOf(note), reopened.list().value()); reopened.update(UpdateNote.make(note.id, "Edited").value()).value()
            assertEquals("Edited", alex.list().value().single().title); alex.delete(DeleteNote.make(note.id).value()).value(); assertTrue(reopened.list().value().isEmpty())
            assertEquals(listOf(guestNote), guest.list().value())
        } finally { context.databaseList().filter { it.startsWith(namespace) }.forEach { context.deleteDatabase(it) } }
    }
    @Test fun memoryFactoryRetainsAccountsOnlyWithinItsLifetime() = runBlocking {
        val factory = AccountNotesRepositories(context, AccountNotesStorage.MEMORY)
        val note = factory.repository(scope()).create(CreateNote.make("Memory Alex").value()).value()
        assertTrue(factory.repository(scope("sam", "sam-personal")).list().value().isEmpty())
        assertEquals(listOf(note), factory.repository(scope()).list().value())
        assertTrue(AccountNotesRepositories(context, AccountNotesStorage.MEMORY).repository(scope()).list().value().isEmpty())
    }
}
