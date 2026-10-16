package dev.mobilefoundry.data

import android.content.Context
import dev.mobilefoundry.services.*
import dev.mobilefoundry.session.*
import java.security.MessageDigest

object AccountNotesPartition {
    fun identifier(scope: SessionScope): String = MessageDigest.getInstance("SHA-256")
        .digest("account-notes.v1\n${scope.authorityId}\n${scope.accountId}".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
enum class AccountNotesStorage { DEVICE, MEMORY }
/** Retain one source factory. Device records have a separate namespace from guest Notes. */
class AccountNotesRepositories(context: Context, private val storage: AccountNotesStorage, private val namespace: String = "account-notes-v1") {
    private val context = context.applicationContext
    private val repositories = linkedMapOf<String, ObservableNotesRepository>()
    init { require(namespace.matches(Regex("[A-Za-z0-9._-]{1,100}"))) }
    @Synchronized fun repository(scope: SessionScope): ObservableNotesRepository = repositories.getOrPut(AccountNotesPartition.identifier(scope)) {
        when (storage) {
            AccountNotesStorage.DEVICE -> SQLiteNotesRepository(context, "$namespace-${AccountNotesPartition.identifier(scope)}.sqlite")
            AccountNotesStorage.MEMORY -> InMemoryNotesRepository()
        }
    }
    fun bind(session: SessionController, lease: SessionLease) = SessionNotesRepository(repository(lease.scope), session, lease)
}
