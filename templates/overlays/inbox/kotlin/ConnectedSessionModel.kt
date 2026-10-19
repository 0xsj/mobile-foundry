package dev.mobilefoundry.product.connection

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mobilefoundry.http.*
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import dev.mobilefoundry.data.SQLitePendingNotesStore
import dev.mobilefoundry.data.SQLiteNotesReadCache
import dev.mobilefoundry.session.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DirectoryState(val accounts: List<Account> = emptyList(), val nextCursor: String? = null,
    val failure: Failure? = null, val loading: Boolean = false)

/** One owner per Activity ViewModel; configuration changes do not rotate twice. */
class ConnectedViewModel(application: Application) : AndroidViewModel(application) {
    private val transport = OkHttpTransport()
    private val allowLocalHTTP = application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val connection: ConnectedSessionModel?
    val configurationFailure: String?
    init {
        when (val result = GoSessionAuthority.create(ConnectionConfiguration.baseUrl, transport, allowLocalHTTP = allowLocalHTTP)) {
            is Outcome.Err -> {
                connection = null
                configurationFailure = "Set a valid HTTPS API base in ConnectionConfiguration. Debug also supports loopback HTTP."
            }
            is Outcome.Ok -> {
                configurationFailure = null
                val authority = result.value
                connection = ConnectedSessionModel(viewModelScope, authority,
                    KeystoreCredentialVault(application, "product.${authority.id}"), authority.baseUrl, transport, allowLocalHTTP, SQLitePendingNotesStore(application), cache = SQLiteNotesReadCache(application))
                connection.restore()
            }
        }
    }
    override fun onCleared() { connection?.close() }
}

class ConnectedSessionModel(private val scope: CoroutineScope, authority: SessionAuthority, vault: CredentialVault,
    private val baseUrl: String, private val transport: HTTPTransport, private val allowLocalHTTP: Boolean, journal: PendingNotesStore = MemoryPendingNotesStore(),
    private val socketTransport: LiveSocketTransport = OkHttpLiveSocketTransport(), cache: NotesReadCache = MemoryNotesReadCache()) {
    val controller = SessionController(scope, authority, vault, System::currentTimeMillis) {
        android.util.Log.e("ProductSession", "Unexpected session failure.")
    }
    val inbox = InboxStore(scope)
    val notes = RemoteNotesStore(scope, journal, cache)
    val workspaces = WorkspaceModel(scope, controller)
    val identity = (HTTPIdentityRecovery.create(baseUrl, transport, allowLocalHTTP = allowLocalHTTP) as? Outcome.Ok)?.value?.let {
        IdentitySettingsModel(scope, controller, it)
    }
    val registration = (HTTPIdentityRegistration.create(baseUrl, transport, allowLocalHTTP) as? Outcome.Ok)?.value?.let {
        RegistrationModel(scope, it, controller)
    }
    private val mutableLiveStatus = MutableStateFlow("Live updates paused.")
    val liveStatus = mutableLiveStatus.asStateFlow()
    private var liveTask: Job? = null
    private val mutableDirectory = MutableStateFlow(DirectoryState())
    val directory = mutableDirectory.asStateFlow()
    private var request: Job? = null
    private var generation = 0L
    private var boundLease: SessionLease? = null
    private var foreground = true
    private val binding = scope.launch { controller.state.collect { if (foreground) bindCurrentScope() } }

    fun bindCurrentScope(force: Boolean = false) {
        val lease = controller.lease
        if (!force && lease == boundLease) return
        detach(); boundLease = lease
        if (lease == null || !controller.isCurrent(lease)) return
        val client = client(lease)
        if (client is Outcome.Ok) {
            inbox.bind(HTTPNotificationInboxRepository(client.value, lease.scope.accountId, lease.scope.principalId), lease.scope)
            registration?.reset()
            workspaces.bind(HTTPAccountManagement(client.value, lease.scope.principalId), HTTPAccountDirectory(client.value, lease.scope.principalId), lease)
            notes.bind(HTTPRemoteNotesRepository(client.value, lease.scope.accountId, lease.scope.principalId), lease.scope)
            val service = HTTPIdentityRecovery.create(baseUrl, transport, client.value, allowLocalHTTP)
            if (service is Outcome.Ok) identity?.bind(service.value, lease)
            reconnectLive()
            loadAccounts()
        }
    }
    fun detach() {
        generation++; request?.cancel(); request = null; boundLease = null
        liveTask?.cancel(); liveTask = null; mutableLiveStatus.value = "Live updates paused."
        registration?.cancel()
        workspaces.bind(null, null, null)
        identity?.bind(null, null)
        inbox.bind(null, null)
        mutableDirectory.value = DirectoryState(); notes.bind(null, null)
    }
    fun setForeground(active: Boolean) {
        if (foreground == active) return
        foreground = active
        if (active) { controller.expireIfNeeded(); bindCurrentScope(true) } else detach()
    }
    fun restore() { detach(); controller.restore() }
    fun signIn(login: String, password: String) { detach(); controller.signIn(login, password) }
    fun renew() { detach(); controller.renew() }
    fun select(accountId: String) { if (accountId != controller.state.value.account?.id) { detach(); controller.selectAccount(accountId) } }
    fun signOut() { detach(); controller.signOut() }
    fun reconnectLive() {
        liveTask?.cancel(); liveTask = null
        val lease = controller.lease ?: return
        val feed = GoAccountLiveHints.create(baseUrl, controller, lease, socketTransport, allowLocalHTTP)
        if (feed !is Outcome.Ok) return
        val current = generation
        liveTask = scope.launch {
            for (attempt in 0..5) {
                if (current != generation || !controller.isCurrent(lease)) return@launch
                mutableLiveStatus.value = if (attempt == 0) "Connecting live updates…" else "Reconnecting live updates…"
                try {
                    feed.value.watch {
                        currentCoroutineContext().ensureActive()
                        if (current == generation && controller.isCurrent(lease)) {
                            mutableLiveStatus.value = "Live updates connected."; notes.invalidate()
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    controller.expireIfNeeded()
                    if (current != generation || !controller.isCurrent(lease)) return@launch
                    if (attempt < 5) delay((1L shl attempt) * 1000 + kotlin.random.Random.nextLong(501))
                } catch (_: CancellationException) { return@launch }
                catch (_: Exception) { break }
            }
            if (current == generation) mutableLiveStatus.value = "Live updates paused. Refresh or reconnect."
        }
    }
    fun loadAccounts(cursor: String? = null) {
        if (directory.value.loading) return
        val lease = controller.lease ?: return
        val client = client(lease)
        if (client !is Outcome.Ok) return
        val current = generation
        mutableDirectory.value = directory.value.copy(loading = true, failure = null)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = HTTPAccountDirectory(client.value, lease.scope.principalId).list(cursor, 20)
                currentCoroutineContext().ensureActive()
                if (current != generation || !controller.isCurrent(lease)) return@launch
                mutableDirectory.value = when (result) {
                    is Outcome.Ok -> DirectoryState(result.value.accounts, result.value.nextCursor)
                    is Outcome.Err -> directory.value.copy(failure = result.error.publicInfo(), loading = false)
                }
            } catch (_: CancellationException) {
            } catch (_: Exception) {
                if (current == generation && controller.isCurrent(lease)) mutableDirectory.value = DirectoryState(
                    failure = Failure.Internal(FailureMeta("Accounts could not be loaded. Try refreshing.")))
            } finally {
                if (current == generation) { mutableDirectory.value = directory.value.copy(loading = false); request = null }
            }
        }
        request = job; job.start()
    }
    private fun client(lease: SessionLease) = AuthenticatedHTTPClient.create(baseUrl, transport, controller, lease, allowLocalHTTP = allowLocalHTTP)
    fun close() { binding.cancel(); detach(); controller.shutdown() }
}
