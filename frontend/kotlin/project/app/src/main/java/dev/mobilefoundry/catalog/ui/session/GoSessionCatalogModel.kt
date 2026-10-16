package dev.mobilefoundry.catalog.ui.session

import dev.mobilefoundry.http.HTTPTransport
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.session.*
import dev.mobilefoundry.services.HTTPRemoteNotesRepository
import dev.mobilefoundry.catalog.ui.notes.RemoteNotesCatalogModel
import dev.mobilefoundry.catalog.composition.SessionProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GoDirectoryState(val accounts: List<Account> = emptyList(), val nextCursor: String? = null, val failure: Failure? = null, val isLoading: Boolean = false)
class GoSessionCatalogModel(private val scope: CoroutineScope, authority: GoSessionAuthority, vault: CredentialVault,
    private val transport: HTTPTransport, val provider: SessionProvider, private val allowLocalHTTP: Boolean) {
    val baseUrl = authority.baseUrl
    val notes = RemoteNotesCatalogModel(scope)
    val controller = SessionController(scope, authority, vault, System::currentTimeMillis) {
        android.util.Log.e("GoSession", "Unexpected Go session failure.")
    }
    private val mutableDirectory = MutableStateFlow(GoDirectoryState())
    val directory = mutableDirectory.asStateFlow()
    private var request: Job? = null
    private var generation = 0L
    fun clearDirectory() { generation++; request?.cancel(); request = null; mutableDirectory.value = GoDirectoryState() }
    fun load(cursor: String? = null) {
        if (mutableDirectory.value.isLoading) return
        val lease = controller.lease ?: return
        generation++; val current = generation
        mutableDirectory.value = mutableDirectory.value.copy(isLoading = true, failure = null)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val client = when (val answer = AuthenticatedHTTPClient.create(baseUrl, transport, controller, lease, allowLocalHTTP = allowLocalHTTP)) {
                    is Outcome.Err -> { mutableDirectory.value = GoDirectoryState(failure = answer.error); return@launch }
                    is Outcome.Ok -> answer.value
                }
                val answer = HTTPAccountDirectory(client, lease.scope.principalId).list(cursor, 20)
                currentCoroutineContext().ensureActive()
                if (current != generation || !controller.isCurrent(lease)) return@launch
                mutableDirectory.value = when (answer) {
                    is Outcome.Ok -> GoDirectoryState(answer.value.accounts, answer.value.nextCursor)
                    is Outcome.Err -> mutableDirectory.value.copy(failure = answer.error.publicInfo(), isLoading = false)
                }
            } catch (_: CancellationException) {
            } catch (_: Exception) {
                if (current == generation && controller.isCurrent(lease)) mutableDirectory.value = GoDirectoryState(failure = Failure.Internal(FailureMeta("Account loading could not complete.")))
            } finally {
                if (current == generation) { mutableDirectory.value = mutableDirectory.value.copy(isLoading = false); request = null }
            }
        }
        request = job; job.start()
    }
    fun refreshScope() {
        clearDirectory();load()
        val lease = controller.lease
        val client = lease?.let { AuthenticatedHTTPClient.create(baseUrl,transport,controller,it,allowLocalHTTP = allowLocalHTTP) }
        if (lease != null && client is Outcome.Ok) notes.bind(HTTPRemoteNotesRepository(client.value,lease.scope.accountId,lease.scope.principalId),lease.scope) else notes.bind(null,null)
    }
    fun close() { notes.bind(null,null);clearDirectory(); controller.shutdown() }
}
